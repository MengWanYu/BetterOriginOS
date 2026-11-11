package com.betteroriginos.feature.media;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import java.lang.reflect.Field;
import java.util.List;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;

import com.betteroriginos.core.FeatureManager;
import com.betteroriginos.core.Prefs;

/**
 * OriginOS 3 的"系统界面播放器"是 QS 网格里的 ControlCenterMediaPlayerView 磁贴
 * （媒体通知被 vivo 从通知列表收走，OS5 那套改通知行的逻辑在这里没有落点）。
 *
 * 只往磁贴底部加一条可拖动进度条，音源绑定/其余渲染保持原生。磁贴本身是 ConstraintLayout，
 * 不同插件里 androidx 可能被改名，所以子 View 的 LayoutParams 由磁贴自己的
 * generateLayoutParams 生成，再用反射摆约束。
 */
public final class QsPlayerTile {
    private static final String TAG = FeatureManager.TAG;
    public static final String TILE_CLASS =
            "com.vivo.systemuiplugin.systemui.qs.music.ControlCenterMediaPlayerView";
    private static final int KEY_TAG = View.generateViewId();
    private static final int KEY_BAR = View.generateViewId();
    private static final int BAR_HEIGHT_DP = 10;
    private static final int BAR_INSET_DP = 12;
    private static final int BAR_THICK_DP = 3;
    private static final long TICK_MS = 500;

    private static volatile boolean sInstalled;

    private QsPlayerTile() {
    }

    /** 由 MediaCardFeature 在插件 ClassLoader 里探测到磁贴类后调用（本模块的 loader 看不到插件类） */
    public static synchronized void install(Class<?> tileClass) {
        if (sInstalled || tileClass == null) {
            return;
        }
        sInstalled = true;
        try {
            XposedBridge.hookAllMethods(tileClass, "onFinishInflate", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    try {
                        attach((View) param.thisObject);
                    } catch (Throwable t) {
                        Log.e(TAG, "qs tile attach failed", t);
                    }
                }
            });
            Log.i(TAG, "qs player tile installed on " + tileClass.getName());
        } catch (Throwable t) {
            Log.e(TAG, "qs player tile install failed", t);
        }
    }

    private static void attach(final View tile) {
        if (!(tile instanceof ViewGroup) || tile.getTag(KEY_TAG) != null) {
            return;
        }
        tile.setTag(KEY_TAG, Boolean.TRUE);
        tile.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
            @Override
            public void onViewAttachedToWindow(View v) {
                sync(v);
            }

            @Override
            public void onViewDetachedFromWindow(View v) {
                Object holder = v.getTag(KEY_BAR);
                if (holder instanceof Bar) {
                    ((Bar) holder).stop();
                }
            }
        });
        if (tile.isAttachedToWindow()) {
            sync(tile);
        }
    }

    /** 开关可能在使用中变化，每次 attach 与每次 tick 都按当前配置对齐 */
    private static void sync(View tile) {
        Bar bar = tile.getTag(KEY_BAR) instanceof Bar ? (Bar) tile.getTag(KEY_BAR) : null;
        if (!seekEnabled(tile.getContext())) {
            if (bar != null) {
                bar.stop();
            }
            return;
        }
        if (bar == null) {
            bar = new Bar((ViewGroup) tile);
            tile.setTag(KEY_BAR, bar);
            try {
                bar.mount();
            } catch (Throwable t) {
                Log.e(TAG, "qs bar mount failed", t);
            }
        }
        bar.start();
    }

    static boolean seekEnabled(Context ctx) {
        try {
            return "1".equals(Prefs.option(ctx, "media_card", "qs_seek", "0"));
        } catch (Throwable t) {
            return false;
        }
    }

    private static final class Bar implements Runnable {
        private final ViewGroup tile;
        private final Context ctx;
        private final float density;
        private final Handler handler = new Handler(Looper.getMainLooper());
        private final Paint track = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint active = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF rect = new RectF();
        private View view;
        private MediaController controller;
        private long durationMs = -1;
        private long pendingSeekMs = -1;
        private long suppressUntil;
        private boolean dragging;
        private boolean running;
        private boolean seekable;

        Bar(ViewGroup tile) {
            this.tile = tile;
            this.ctx = tile.getContext();
            this.density = ctx.getResources().getDisplayMetrics().density;
            track.setColor(0x33808080);
            boolean night = (ctx.getResources().getConfiguration().uiMode
                    & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
            active.setColor(night ? 0xFFE8E8E8 : 0xFF2B2B2B);
        }

        void mount() {
            final int h = dp(BAR_HEIGHT_DP);
            view = new View(ctx) {
                @Override
                protected void onDraw(Canvas canvas) {
                    float thick = dp(BAR_THICK_DP);
                    rect.set(dp(BAR_INSET_DP), (h - thick) / 2f,
                            getWidth() - dp(BAR_INSET_DP), (h + thick) / 2f);
                    if (rect.right <= rect.left) {
                        return;
                    }
                    canvas.drawRoundRect(rect, thick / 2f, thick / 2f, track);
                    float dur = durationMs;
                    if (dur > 0) {
                        float frac = Math.min(1f, Math.max(0f, positionMs() / dur));
                        canvas.drawRoundRect(rect.left, rect.top,
                                rect.left + (rect.right - rect.left) * frac,
                                rect.bottom, thick / 2f, thick / 2f, active);
                    }
                }
            };
            ((ViewGroup) tile).addView(view,
                    (ViewGroup.LayoutParams) bottomConstraints(tile,
                            new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, h)));
            liftButtons(h);
            view.setOnTouchListener(new View.OnTouchListener() {
                @Override
                public boolean onTouch(View v, MotionEvent e) {
                    return touch(e);
                }
            });
            start();
        }

        void start() {
            if (!running) {
                running = true;
                handler.post(this);
            }
        }

        void stop() {
            running = false;
            handler.removeCallbacks(this);
            if (controller != null) {
                try {
                    controller.unregisterCallback(callback);
                } catch (Throwable ignored) {
                }
                controller = null;
            }
        }

        @Override
        public void run() {
            if (!running) {
                return;
            }
            try {
                if (controller == null) {
                    bind();
                }
                if (view != null && view.isShown()) {
                    view.invalidate();
                }
            } catch (Throwable t) {
                Log.w(TAG, "qs bar tick failed", t);
            }
            handler.postDelayed(this, TICK_MS);
        }

        private final MediaController.Callback callback = new MediaController.Callback() {
            @Override
            public void onPlaybackStateChanged(PlaybackState state) {
                durationMs = -1;
            }

            @Override
            public void onMetadataChanged(MediaMetadata metadata) {
                durationMs = -1;
            }
        };

        private void bind() {
            MediaController chosen = pickSession();
            if (chosen == null) {
                return;
            }
            controller = chosen;
            try {
                chosen.registerCallback(callback, handler);
            } catch (Throwable ignored) {
            }
            PlaybackState st = chosen.getPlaybackState();
            seekable = st != null && (st.getActions() & PlaybackState.ACTION_SEEK_TO) != 0;
            MediaMetadata md = chosen.getMetadata();
            durationMs = md == null ? -1 : md.getLong(MediaMetadata.METADATA_KEY_DURATION);
            Log.i(TAG, "qs bar bound pkg=" + chosen.getPackageName() + " seekable=" + seekable
                    + " widgetBinder=" + MediaCardFeature.widgetReady() + " dur=" + durationMs);
        }

        /** 磁贴显示的曲目来自 vivo 自己的服务，标题能对上就优先对上，否则取正在播放的 */
        private MediaController pickSession() {
            try {
                MediaSessionManager msm = (MediaSessionManager)
                        ctx.getSystemService(Context.MEDIA_SESSION_SERVICE);
                if (msm == null) {
                    return null;
                }
                List<MediaController> list = msm.getActiveSessions(null);
                String wanted = tileTitle();
                MediaController playing = null;
                for (MediaController c : list) {
                    MediaMetadata md = c.getMetadata();
                    String t = md == null || md.getDescription().getTitle() == null
                            ? "" : md.getDescription().getTitle().toString();
                    if (!wanted.isEmpty() && t.contains(wanted)) {
                        return c;
                    }
                    PlaybackState st = c.getPlaybackState();
                    if (playing == null && st != null
                            && st.getState() == PlaybackState.STATE_PLAYING) {
                        playing = c;
                    }
                }
                if (playing != null) {
                    return playing;
                }
                return list.isEmpty() ? null : list.get(0);
            } catch (Throwable t) {
                Log.w(TAG, "qs bar pickSession failed", t);
                return null;
            }
        }

        private String tileTitle() {
            try {
                TextView name = publicField("musicName");
                if (name != null) {
                    CharSequence text = name.getText();
                    return text == null ? "" : text.toString();
                }
            } catch (Throwable ignored) {
            }
            return "";
        }

        @SuppressWarnings("unchecked")
        private <T> T publicField(String name) {
            try {
                Field f = tile.getClass().getField(name);
                return (T) f.get(tile);
            } catch (Throwable t) {
                return null;
            }
        }

        private long positionMs() {
            if (pendingSeekMs >= 0 && SystemClock.elapsedRealtime() < suppressUntil) {
                return pendingSeekMs;
            }
            PlaybackState st = controller == null ? null : controller.getPlaybackState();
            if (st == null) {
                return 0;
            }
            long pos = st.getPosition();
            if (st.getState() == PlaybackState.STATE_PLAYING) {
                pos += (long) ((SystemClock.elapsedRealtime() - st.getLastPositionUpdateTime())
                        * st.getPlaybackSpeed());
            }
            return pos;
        }

        private long durationOrRefresh() {
            if (durationMs > 0) {
                return durationMs;
            }
            MediaMetadata md = controller == null ? null : controller.getMetadata();
            durationMs = md == null ? 0 : md.getLong(MediaMetadata.METADATA_KEY_DURATION);
            return durationMs;
        }

        private boolean touch(MotionEvent e) {
            long dur = durationOrRefresh();
            if (dur <= 0 || view == null) {
                return false;
            }
            int inset = dp(BAR_INSET_DP);
            float span = view.getWidth() - inset * 2f;
            if (span <= 0f) {
                return false;
            }
            float x = Math.max(inset, Math.min(inset + span, e.getX()));
            long target = (long) ((x - inset) / span * dur);
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    dragging = true;
                    pendingSeekMs = target;
                    suppressUntil = SystemClock.elapsedRealtime() + 1500;
                    view.invalidate();
                    return true;
                case MotionEvent.ACTION_MOVE:
                    if (dragging) {
                        pendingSeekMs = target;
                        view.invalidate();
                    }
                    return dragging;
                case MotionEvent.ACTION_UP:
                    dragging = false;
                    boolean ok = seek(target);
                    pendingSeekMs = -1;
                    view.invalidate();
                    return ok;
                case MotionEvent.ACTION_CANCEL:
                    dragging = false;
                    pendingSeekMs = -1;
                    view.invalidate();
                    return true;
                default:
                    return false;
            }
        }

        private boolean seek(long ms) {
            boolean ok = false;
            if (seekable && controller != null) {
                try {
                    controller.getTransportControls().seekTo(ms);
                    ok = true;
                } catch (Throwable t) {
                    Log.w(TAG, "qs seek via session failed", t);
                }
            }
            if (!ok) {
                ok = MediaCardFeature.seekViaWidget(ms);
            }
            suppressUntil = SystemClock.elapsedRealtime() + 1200;
            Log.i(TAG, "qs seek ms=" + ms + " ok=" + ok);
            return ok;
        }

        /** 按钮行整体上移，腾出进度条位置，避免和播放/上下曲抢点击 */
        private void liftButtons(int by) {
            for (String name : new String[]{"prevSongParent", "playSongParent", "nextSongParent"}) {
                View v = publicField(name);
                if (v == null) {
                    continue;
                }
                ViewGroup.LayoutParams p = v.getLayoutParams();
                if (p instanceof ViewGroup.MarginLayoutParams
                        && ((ViewGroup.MarginLayoutParams) p).bottomMargin < by) {
                    ((ViewGroup.MarginLayoutParams) p).bottomMargin = by;
                    v.setLayoutParams(p);
                }
            }
        }

        private int dp(int v) {
            return Math.round(v * density);
        }
    }

    /** 用磁贴自己的 generateLayoutParams 得到真实的参数类型，再摆"底部满宽"约束 */
    private static Object bottomConstraints(ViewGroup owner, ViewGroup.LayoutParams raw) {
        Object lp = invokeGenerateLayoutParams(owner, raw);
        if (lp == null) {
            return raw;
        }
        setIntField(lp, "leftToLeft", 0);
        setIntField(lp, "rightToRight", 0);
        setIntField(lp, "bottomToBottom", 0);
        setIntField(lp, "topToTop", -1);
        setIntField(lp, "topToBottom", -1);
        return lp;
    }

    /** ViewGroup.generateLayoutParams 是 protected，只能反射调 */
    private static Object invokeGenerateLayoutParams(ViewGroup owner, ViewGroup.LayoutParams raw) {
        try {
            java.lang.reflect.Method m =
                    ViewGroup.class.getDeclaredMethod("generateLayoutParams",
                            ViewGroup.LayoutParams.class);
            m.setAccessible(true);
            return m.invoke(owner, raw);
        } catch (Throwable t) {
            Log.w(TAG, "qs bar generateLayoutParams failed", t);
            return null;
        }
    }

    private static void setIntField(Object target, String name, int value) {
        try {
            target.getClass().getField(name).setInt(target, value);
        } catch (Throwable ignored) {
        }
    }
}
