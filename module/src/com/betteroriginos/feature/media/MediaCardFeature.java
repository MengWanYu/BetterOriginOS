package com.betteroriginos.feature.media;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.database.ContentObserver;
import android.net.Uri;
import android.content.ContentValues;
import android.database.Cursor;
import android.os.Bundle;
import android.provider.MediaStore;
import android.provider.Settings;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.TransitionDrawable;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ClipDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.media.MediaDescription;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Parcel;
import android.os.SystemClock;
import android.text.TextUtils;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.WeakHashMap;

import com.betteroriginos.R;
import com.betteroriginos.core.Feature;
import com.betteroriginos.core.FeatureOption;
import com.betteroriginos.core.OsProfile;
import com.betteroriginos.core.Prefs;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

public final class MediaCardFeature extends Feature {
    private static final String TAG = "BetterOriginOS";
    private static final Map<View, MediaCard> CARDS = new WeakHashMap<>();
    private static final String WIDGET_PKG = "com.vivo.musicwidgetmix";
    private static final String PREVIEW_REQ_KEY = "betteroriginos_preview_req";
    private static final String PREVIEW_DONE_KEY = "betteroriginos_preview_done";
    private static final String PREVIEW_DIR = "preview";
    private static volatile CharSequence sLastTitle;
    private static volatile CharSequence sLastArtist;
    private static volatile Drawable sLastArt;
    private static volatile String sLastSourcePkg;
    private static boolean sObserverRegistered;
    private static boolean sRendering;

    static {
        Prefs.registerLegacyFeatureKey("media_card", "enabled");
        Prefs.registerLegacyOptionKey("media_card", "style", "style");
    }
    private static final String WIDGET_SVC = "com.vivo.musicwidgetmix.service.MusicWidgetMixService";
    private static final String WIDGET_DESC = "com.vivo.musicwidgetmix.IMusicWidgetMixService";
    private static final int TX_SEEK_POS = 15;
    private static volatile IBinder sWidgetBinder;
    private static boolean sWidgetBinding;

    private static final ServiceConnection WIDGET_CONN = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            sWidgetBinder = service;
            Log.i(TAG, "widget service connected");
            synchronized (CARDS) {
                for (MediaCard c : CARDS.values()) {
                    c.updateSeekable();
                }
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            sWidgetBinder = null;
            Log.i(TAG, "widget service disconnected");
        }
    };

    private static void ensureWidgetService(Context ctx) {
        synchronized (MediaCardFeature.class) {
            if (sWidgetBinder != null || sWidgetBinding) {
                return;
            }
            sWidgetBinding = true;
            try {
                Intent i = new Intent();
                i.setComponent(new ComponentName(WIDGET_PKG, WIDGET_SVC));
                boolean ok = ctx.bindService(i, WIDGET_CONN, Context.BIND_AUTO_CREATE);
                Log.i(TAG, "bind widget service ok=" + ok);
                if (!ok) {
                    sWidgetBinding = false;
                }
            } catch (Throwable t) {
                sWidgetBinding = false;
                Log.w(TAG, "bind widget service failed", t);
            }
        }
    }

    /**
     * 通知行的 NotifTouchInterceptor 会把横向手势当成"滑动删除"，
     * 我们的进度条没有 AOSP 忽略名单里的 id，所以按下时必须主动请求父级不拦截。
     */
    private static final class DragSeekBar extends SeekBar {
        DragSeekBar(Context c) {
            super(c);
        }

        private int mOnDraws;

        /**
         * 不要覆写 setVisibility：RemoteViews 按 id 找到这条（借了 android.R.id.progress）
         * 反射调 setVisibility 时会检查方法上的 @RemotableViewMethod，覆写会把注解丢掉，
         * 抛 "can't use method with RemoteViews: setVisibility(int)"，系统判定发通知的
         * 音乐 App 发了 BadForegroundServiceNotification 并把它杀掉（真机 crash 6 次）。
         * 原生隐藏由 reclaimBar() 在 ticker 里纠正（300ms 内）。
         */

        @Override
        protected void onDraw(android.graphics.Canvas c) {
            if (mOnDraws++ < 3) {
                android.graphics.drawable.Drawable d = getProgressDrawable();
                Log.i("BetterOriginOS", "seek view onDraw #" + mOnDraws
                        + " w=" + getWidth() + " h=" + getHeight()
                        + " shown=" + isShown() + " vis=" + getVisibility()
                        + " alpha=" + getAlpha() + " willNotDraw=" + willNotDraw()
                        + " dBounds=" + (d == null ? "null" : d.getBounds().toShortString())
                        + " level=" + (d == null ? -1 : d.getLevel()));
            }
            super.onDraw(c);
        }

        @Override
        public boolean onTouchEvent(android.view.MotionEvent event) {
            if (event.getActionMasked() == android.view.MotionEvent.ACTION_DOWN) {
                Log.i("BetterOriginOS", "card seek bar DOWN enabled=" + isEnabled()
                        + " w=" + getWidth() + " h=" + getHeight());
            }
            if (event.getActionMasked() == android.view.MotionEvent.ACTION_DOWN
                    && getParent() != null) {
                getParent().requestDisallowInterceptTouchEvent(true);
            }
            return super.onTouchEvent(event);
        }
    }

    private static boolean widgetSeekAvailable() {
        return sWidgetBinder != null;
    }

    /** 供 QS 磁贴进度条复用：vivo 音乐小组件 binder 是否就绪 */
    static boolean widgetReady() {
        return sWidgetBinder != null;
    }

    static boolean seekViaWidget(long position) {
        IBinder b = sWidgetBinder;
        if (b == null) {
            Log.i(TAG, "widget seek: binder not ready");
            return false;
        }
        Parcel data = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        try {
            data.writeInterfaceToken(WIDGET_DESC);
            data.writeLong(position);
            boolean ok = b.transact(TX_SEEK_POS, data, reply, 0);
            reply.readException();
            Log.i(TAG, "widget seek pos=" + position + " ok=" + ok);
            return ok;
        } catch (Throwable t) {
            Log.w(TAG, "widget seek failed", t);
            return false;
        } finally {
            data.recycle();
            reply.recycle();
        }
    }

    @Override
    public String id() {
        return "media_card";
    }

    @Override
    public int titleRes() {
        return R.string.feature_media_title;
    }

    @Override
    public int summaryRes() {
        return R.string.feature_media_summary;
    }

    @Override
    public int categoryRes() {
        return R.string.category_media;
    }

    @Override
    public int iconRes() {
        return R.drawable.ic_music;
    }

    @Override
    public String previewKind() {
        return "media";
    }

    @Override
    protected List<FeatureOption> buildOptions() {
        java.util.List<FeatureOption> list = new java.util.ArrayList<>();
        list.add(FeatureOption.choice(
                "style",
                R.string.option_style_title,
                R.string.option_style_summary,
                "os5",
                new String[]{"os5", "material"},
                new int[]{R.string.style_os5_title, R.string.style_m3_title},
                new int[]{R.drawable.preview_os5, R.drawable.preview_m3}));
        list.add(FeatureOption.toggle("qs_seek", R.string.option_qs_seek_title,
                R.string.option_qs_seek_summary, false));
        return list;
    }

    private static final String PLUGIN_PKG = "com.vivo.systemuiplugin";

    @Override
    public String[] scopes() {
        return new String[]{"com.android.systemui", PLUGIN_PKG};
    }

    @Override
    public void install(ClassLoader cl, String packageName) {
        if (PLUGIN_PKG.equals(packageName)) {
            installQsTile(cl);
            return;
        }
        install(cl);
    }

    /** OS3：播放器是插件里的 QS 磁贴，类只在插件 ClassLoader 里可见 */
    private void installQsTile(ClassLoader cl) {
        if (!OsProfile.isQsTilePlayer()) {
            return;
        }
        try {
            QsPlayerTile.install(XposedHelpers.findClass(QsPlayerTile.TILE_CLASS, cl));
        } catch (Throwable t) {
            Log.i(TAG, "qs player tile not present: " + t.getMessage());
        }
    }

    @Override
    public void install(ClassLoader cl) {
        try {
            Class<?> c = XposedHelpers.findClass(
                    "com.android.systemui.statusbar.notification.row.NotificationContentView", cl);
            XC_MethodHook hook = new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    Object child = param.args != null && param.args.length > 0 ? param.args[0] : null;
                    if (child instanceof View) {
                        sBindCount++;
                        if (sBindCount <= 6 || (sBindCount & 31) == 0) {
                            CharSequence t = null;
                            try {
                                View tv = ((View) child).findViewById(
                                        android.R.id.text1);
                                if (tv instanceof android.widget.TextView) {
                                    t = ((android.widget.TextView) tv).getText();
                                }
                            } catch (Throwable ignored) {
                            }
                            Log.i(TAG, "media bind n=" + sBindCount + " cls="
                                    + child.getClass().getName()
                                    + " pkg=" + ((View) child).getContext().getPackageName()
                                    + " tv=" + t);
                        }
                        try {
                            restyle((View) child);
                        } catch (Throwable t) {
                            Log.e(TAG, "restyle failed", t);
                        }
                    }
                }
            };
            int n1 = XposedBridge.hookAllMethods(c, "setExpandedChild", hook).size();
            int n2 = XposedBridge.hookAllMethods(c, "setContractedChild", hook).size();
            installSweepHook(cl);
            OsProfile.report();
            Log.i(TAG, "media card feature installed expandedHooks=" + n1 + " contractedHooks=" + n2);
        } catch (Throwable t) {
            Log.e(TAG, "media card feature install failed", t);
        }
    }

    private static int sBindCount;
    private static long sLastSweepAt;
    private static boolean sTreeDumped;

    /** 找不到媒体视图时把子树摊出来：类名与深度都只能从现场拿 */
    private static void dumpTree(View v, StringBuilder sb, int depth) {
        if (v == null || depth > 7 || sb.length() > 1400) {
            return;
        }
        sb.append(' ').append(depth).append(':').append(v.getClass().getSimpleName());
        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) {
                dumpTree(g.getChildAt(i), sb, depth + 1);
            }
        }
    }

    /**
     * SystemUI 重启后，已存在的媒体通知行不会再走 setContractedChild
     * （hookAllMethods 只匹配类自己声明的方法，vivo 也不会重发通知），
     * 于是卡片永远挂不上、露出原生那张没有进度条的卡。这里在列表绘制时（1s 节流）补扫。
     */
    private void installSweepHook(ClassLoader cl) {
        XC_MethodHook sweep = new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    if (!(param.thisObject instanceof ViewGroup)) {
                        return;
                    }
                    long now = android.os.SystemClock.uptimeMillis();
                    if (now - sLastSweepAt < 1000) {
                        return;
                    }
                    sLastSweepAt = now;
                    try {
                        ViewGroup list = (ViewGroup) param.thisObject;
                        java.util.List<View> found = new java.util.ArrayList<>();
                        for (int i = 0; i < list.getChildCount(); i++) {
                            collectMediaViews(list.getChildAt(i), found, 0);
                        }
                        if (found.isEmpty() && !sTreeDumped && list.getChildCount() > 0) {
                            sTreeDumped = true;
                            StringBuilder sb = new StringBuilder();
                            dumpTree(list.getChildAt(0), sb, 0);
                            Log.i(TAG, "media sweep found nothing; row0 tree:" + sb);
                        }
                        for (View v : found) {
                            boolean known;
                            boolean alreadySkipped;
                            synchronized (CARDS) {
                                known = CARDS.containsKey(v);
                            }
                            synchronized (SKIP_LOGGED) {
                                alreadySkipped = SKIP_LOGGED.contains(v);
                            }
                            if (known || alreadySkipped) {
                                continue;
                            }
                            Log.i(TAG, "media sweep attach cls=" + v.getClass().getName());
                            restyle(v);
                        }
                    } catch (Throwable t) {
                        Log.w(TAG, "media sweep failed", t);
                    }
                }
        };
        // A13 在 notification.stack，A15 在 notification.row：两个都试，命中即止
        String[] names = {
                "com.android.systemui.statusbar.notification.stack"
                        + ".NotificationStackScrollLayout",
                "com.android.systemui.statusbar.notification.row"
                        + ".NotificationStackScrollLayout",
        };
        for (String name : names) {
            try {
                int n = XposedBridge.hookAllMethods(
                        XposedHelpers.findClass(name, cl), "dispatchDraw", sweep).size();
                Log.i(TAG, "media sweep hook=" + n + " on " + name);
                if (n > 0) {
                    return;
                }
            } catch (Throwable ignored) {
            }
        }
        Log.w(TAG, "media sweep: no list class matched");
    }

    /** 只认 MediaNotificationView 本身：卡片要加在它里面，别的容器不行 */
    private static void collectMediaViews(View v, java.util.List<View> out, int depth) {
        if (v == null || depth > 7) {
            return;
        }
        // 只认 MediaNotificationView：卡片必须加在它里面。
        // MediaContainerView 是 A13 列表里的外层容器，它自己找不到 media_actions（实测），
        // 传进去只会白跑一次
        if (v.getClass().getName().contains("MediaNotificationView")) {
            out.add(v);
            return;
        }
        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) {
                collectMediaViews(g.getChildAt(i), out, depth + 1);
            }
        }
    }
    private static final java.util.Set<View> SKIP_LOGGED =
            java.util.Collections.newSetFromMap(new java.util.WeakHashMap<View, Boolean>());

    /**
     * 每个视图只报一次早退原因（避免刷屏），但一定要报：
     * "媒体卡没出现"过去完全没有线索，只能靠猜（实测踩过 20 分钟）。
     */
    private static void skipOnce(String why, View root) {
        synchronized (SKIP_LOGGED) {
            if (!SKIP_LOGGED.add(root)) {
                return;
            }
        }
        // 只有媒体视图才值得记：普通通知行没有 media_actions 是正常情况，别刷屏
        if (root.getClass().getName().contains("Media")) {
            Log.i(TAG, "media restyle skip " + why + " cls=" + root.getClass().getName());
        }
    }

    private void restyle(View root) {
        if (!(root instanceof ViewGroup)) {
            return;
        }
        Context ctx = root.getContext();
        // 任意通知绑定即注册预览监听（不依赖媒体通知，未播放音乐时也能响应渲染请求）
        registerPreviewObserver(ctx);
        if (!isEnabled(ctx)) {
            skipOnce("disabled", root);
            return;
        }
        int actionsId = id(ctx, "media_actions");
        int titleId = id(ctx, "title");
        int textId = id(ctx, "text");
        int artId = id(ctx, "right_icon");
        int headerId = id(ctx, "notification_header");
        int iconId = id(ctx, "icon");
        if (actionsId == 0 || titleId == 0) {
            skipOnce("no-ids actionsId=" + actionsId + " titleId=" + titleId, root);
            return;
        }
        ViewGroup actions = root.findViewById(actionsId);
        TextView title = root.findViewById(titleId);
        TextView artist = root.findViewById(textId);
        ImageView art = root.findViewById(artId);
        ImageView sourceIcon = iconId == 0 ? null : (ImageView) root.findViewById(iconId);
        if (actions == null || title == null) {
            skipOnce("actions=" + (actions == null ? "null" : "ok")
                    + " title=" + (title == null ? "null" : "ok"), root);
            return;
        }
        synchronized (CARDS) {
            MediaCard existing = CARDS.get(root);
            if (existing != null) {
                existing.refreshColors(true);
                return;
            }
        }
        sLastTitle = title.getText();
        sLastArtist = artist == null ? null : artist.getText();
        if (art != null && art.getDrawable() != null) {
            sLastArt = art.getDrawable();
        }
        if (sourceIcon != null && sourceIcon.getDrawable() != null) {
            sLastSourcePkg = sourceIcon.getContentDescription() == null
                    ? null : sourceIcon.getContentDescription().toString();
        }
        String style = option(ctx, "style", "os5");
        Log.i(TAG, "restyling style=" + style + " " + root.getClass().getName()
                + " title=" + title.getText() + " actions=" + actions.getChildCount());
        MediaCard card = MediaCard.build(ctx, root, actions, title, artist, art, sourceIcon,
                headerId, style);
        synchronized (CARDS) {
            CARDS.put(root, card);
        }
        registerPreviewObserver(ctx);
        card.handler.postDelayed(() -> captureLiveCard(ctx, card, style), 500);
    }

    // ---- 真实卡片预览渲染（设置 App 通过 betteroriginos_preview_req 触发） ----

    /** 通知视图的 context 是发起通知的应用（createPackageContext），存储/系统服务必须用 SystemUI 本体上下文 */
    private static Context sysuiContext(Context fallback) {
        try {
            Object app = XposedHelpers.callStaticMethod(
                    XposedHelpers.findClass("android.app.ActivityThread", null),
                    "currentApplication");
            if (app instanceof Context) {
                return (Context) app;
            }
        } catch (Throwable ignored) {
        }
        return fallback;
    }

    private static void registerPreviewObserver(Context ctx) {
        if (sObserverRegistered) {
            return;
        }
        sObserverRegistered = true;
        final Context sysui = sysuiContext(ctx);
        try {
            sysui.getContentResolver().registerContentObserver(
                    Settings.Global.getUriFor(PREVIEW_REQ_KEY), false,
                    new ContentObserver(new Handler(Looper.getMainLooper())) {
                        @Override
                        public void onChange(boolean selfChange) {
                            try {
                                String req = Settings.Global.getString(
                                        sysui.getContentResolver(), PREVIEW_REQ_KEY);
                                if (req == null || req.isEmpty()) {
                                    return;
                                }
                                // 协议：req = nonce[:style]；指定样式避免与当前配置竞态；
                                // done 回显完整请求串（App 按完整串轮询）
                                String style = Prefs.option(sysui, "media_card",
                                        "style", "os5");
                                int sep = req.indexOf(':');
                                if (sep > 0) {
                                    String requested = req.substring(sep + 1);
                                    if ("os5".equals(requested) || "material".equals(requested)) {
                                        style = requested;
                                    }
                                }
                                renderPreview(sysui, style, req);
                            } catch (Throwable t) {
                                Log.e(TAG, "preview render failed", t);
                            }
                        }
                    });
            Log.i(TAG, "preview observer registered");
        } catch (Throwable t) {
            Log.e(TAG, "preview observer register failed", t);
        }
    }

    /** 离屏合成模板 + 复用产线 MediaCard.build 渲染指定样式，输出真实观感 PNG */
    private static void renderPreview(final Context ctx, final String style, final String nonce) {
        if (sRendering) {
            return;
        }
        sRendering = true;
        try {
            FrameLayout root = new FrameLayout(ctx);
            LinearLayout actions = new LinearLayout(ctx);
            actions.setOrientation(LinearLayout.HORIZONTAL);
            actions.addView(actionButton(ctx, "收藏", android.R.drawable.btn_star_big_off));
            actions.addView(actionButton(ctx, "上一首", android.R.drawable.ic_media_previous));
            actions.addView(actionButton(ctx, "播放", android.R.drawable.ic_media_pause));
            actions.addView(actionButton(ctx, "下一首", android.R.drawable.ic_media_next));
            TextView title = new TextView(ctx);
            title.setText(sLastTitle == null ? "示例歌曲" : sLastTitle.toString());
            TextView text = new TextView(ctx);
            text.setText(sLastArtist == null ? "示例歌手 · 示例专辑" : sLastArtist.toString());
            ImageView art = new ImageView(ctx);
            Drawable artDrawable = sLastArt;
            if (artDrawable != null) {
                art.setImageDrawable(artDrawable);
            }
            MediaCard.build(ctx, root, actions, title, text, art, null, 0, style);

            int w = ctx.getResources().getDisplayMetrics().widthPixels - dp(ctx, 40);
            int wSpec = View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY);
            root.measure(wSpec, View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
            int h = Math.max(root.getMeasuredHeight(), dp(ctx, 120));
            root.layout(0, 0, w, h);
            Bitmap bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
            root.draw(new Canvas(bmp));
            savePng(ctx, bmp, style, nonce);
        } catch (Throwable t) {
            Log.e(TAG, "renderPreview failed", t);
        } finally {
            sRendering = false;
        }
    }

    /** 抓取真实在屏卡片（真实封面/标题/取色）；布局未完成时退避重试 */
    private static void captureLiveCard(Context ctx, MediaCard card, String style) {
        captureWithRetry(ctx, card, style, 0);
    }

    private static void captureWithRetry(Context ctx, MediaCard card, String style, int attempt) {
        try {
            View v = card.card;
            if (v != null && v.getWidth() > 0 && v.getHeight() > 0) {
                Bitmap bmp = Bitmap.createBitmap(v.getWidth(), v.getHeight(),
                        Bitmap.Config.ARGB_8888);
                v.draw(new Canvas(bmp));
                savePng(ctx, bmp, style, null);
                return;
            }
        } catch (Throwable t) {
            Log.e(TAG, "captureLiveCard failed", t);
            return;
        }
        if (attempt < 10) {
            card.handler.postDelayed(() -> captureWithRetry(ctx, card, style, attempt + 1), 400);
        }
    }

    /** 发布到 MediaStore（Pictures/BetterOriginOS）：App 无需 su 即可读取 */
    private static void publishToMediaStore(Context ctx, Bitmap bmp, String style)
            throws Throwable {
        ContentResolver cr = ctx.getContentResolver();
        String name = "card_" + style + ".png";
        Uri collection = MediaStore.Images.Media.getContentUri("external");
        Bundle q = new Bundle();
        q.putString(ContentResolver.QUERY_ARG_SQL_SELECTION,
                MediaStore.Images.Media.RELATIVE_PATH + "=? AND "
                        + MediaStore.Images.Media.DISPLAY_NAME + "=?");
        q.putStringArray(ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS,
                new String[]{"Pictures/BetterOriginOS/", name});
        Cursor c = cr.query(collection, new String[]{MediaStore.Images.Media._ID}, q, null);
        if (c != null) {
            while (c.moveToNext()) {
                cr.delete(ContentUris.withAppendedId(collection, c.getLong(0)), null, null);
            }
            c.close();
        }
        ContentValues v = new ContentValues();
        v.put(MediaStore.Images.Media.DISPLAY_NAME, name);
        v.put(MediaStore.Images.Media.MIME_TYPE, "image/png");
        v.put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/BetterOriginOS");
        v.put(MediaStore.Images.Media.IS_PENDING, 1);
        Uri uri = cr.insert(collection, v);
        if (uri == null) {
            throw new IllegalStateException("MediaStore insert failed");
        }
        java.io.OutputStream os = cr.openOutputStream(uri);
        bmp.compress(Bitmap.CompressFormat.PNG, 100, os);
        os.close();
        ContentValues done = new ContentValues();
        done.put(MediaStore.Images.Media.IS_PENDING, 0);
        cr.update(uri, done, null, null);
    }

    private static ImageButton actionButton(Context ctx, String desc, int resId) {
        ImageButton b = new ImageButton(ctx);
        b.setContentDescription(desc);
        b.setImageResource(resId);
        return b;
    }

    private static void savePng(Context ctx, Bitmap bmp, String style, String nonce) {
        try {
            Context sysui = sysuiContext(ctx);
            File dir = new File(sysui.getFilesDir(), PREVIEW_DIR);
            if (!dir.exists() && !dir.mkdirs()) {
                throw new FileNotFoundException(dir.getAbsolutePath());
            }
            File out = new File(dir, "card_" + style + ".png");
            FileOutputStream fos = new FileOutputStream(out);
            bmp.compress(Bitmap.CompressFormat.PNG, 100, fos);
            fos.close();
            if (nonce != null) {
                try {
                    publishToMediaStore(sysui, bmp, style);
                } catch (Throwable t) {
                    Log.w(TAG, "preview media publish failed", t);
                }
                try {
                    Settings.Global.putString(sysui.getContentResolver(),
                            PREVIEW_DONE_KEY, nonce);
                } catch (Throwable t) {
                    Log.w(TAG, "preview done put failed", t);
                }
            }
            Log.i(TAG, "preview saved style=" + style + " size=" + bmp.getWidth() + "x"
                    + bmp.getHeight());
        } catch (Throwable t) {
            Log.e(TAG, "savePng failed", t);
        }
    }

    private static int id(Context ctx, String name) {
        return ctx.getResources().getIdentifier(name, "id", "android");
    }

    private static int dp(Context ctx, float v) {
        return Math.round(v * ctx.getResources().getDisplayMetrics().density);
    }

    private static boolean isNight(Context ctx) {
        return (ctx.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
                == Configuration.UI_MODE_NIGHT_YES;
    }

    static final class MediaCard {
        final View root;
        final LinearLayout card;
        final ImageView art;
        final ImageView artView;
        final SeekBar bar;
        final SeekDrawable seekDrawable;
        long pendingSeekMs = -1;
        long suppressUntil;
        final TextView elapsed;
        final TextView total;
        final TextView title;
        final TextView artist;
        final ImageButton playBtn;
        final ImageView sourceView;
        final String style;
        final Handler handler = new Handler(Looper.getMainLooper());
        MediaController controller;
        boolean dragging;
        boolean tickerRunning;
        boolean canSeek;
        boolean lastNoDur;
        int lastArtHash;
        String lastTrackKey = "";
        int lastDrawableHash;
        View rowBgView;
        Drawable curBg;

        final Runnable ticker = new Runnable() {
            @Override
            public void run() {
                updateProgress();
                syncArt();
                refreshColors(true);
                if (tickerRunning) {
                    handler.postDelayed(this, 300);
                }
            }
        };

        final MediaController.Callback callback = new MediaController.Callback() {
            @Override
            public void onPlaybackStateChanged(PlaybackState state) {
                updateSeekable();
                if (pendingSeekMs >= 0 && state != null
                        && Math.abs(state.getPosition() - pendingSeekMs) < 1500) {
                    pendingSeekMs = -1;
                }
                updateProgress();
            }

            @Override
            public void onMetadataChanged(MediaMetadata metadata) {
                syncArt();
                refreshColors(true);
                updateProgress();
            }

            @Override
            public void onSessionDestroyed() {
                release();
            }
        };

        MediaCard(View root, LinearLayout card, ImageView art, ImageView artView, SeekBar bar,
                  SeekDrawable seekDrawable, TextView elapsed, TextView total, TextView title,
                  TextView artist, ImageButton playBtn, ImageView sourceView, String style) {
            this.root = root;
            this.card = card;
            this.art = art;
            this.artView = artView;
            this.bar = bar;
            this.seekDrawable = seekDrawable;
            this.elapsed = elapsed;
            this.total = total;
            this.title = title;
            this.artist = artist;
            this.playBtn = playBtn;
            this.sourceView = sourceView;
            this.style = style;
        }

        static MediaCard build(Context ctx, View root, ViewGroup actions, TextView title,
                               TextView artist, ImageView art, ImageView sourceIcon, int headerId,
                               String style) {
            ensureWidgetService(ctx);
            boolean m3 = "material".equals(style);
            boolean night = isNight(ctx);
            int primary = night ? 0xFFFFFFFF : (m3 ? 0xFF2A1A15 : 0xFF241A16);
            int secondary = night ? 0xB3FFFFFF : (m3 ? 0x8C5A4038 : 0x8C322319);
            int accent = m3 ? 0xFFA93A27 : (night ? 0xFFF07A64 : 0xFFE0503A);

            root.setBackground(null);
            root.setPadding(0, 0, 0, 0);
            ((ViewGroup) root).setClipChildren(false);
            ((ViewGroup) root).setClipToPadding(false);
            if (headerId != 0) {
                View header = root.findViewById(headerId);
                if (header != null) {
                    header.setVisibility(View.GONE);
                }
            }

            LinearLayout card = new LinearLayout(ctx);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setPadding(dp(ctx, 16), dp(ctx, 15), dp(ctx, 16), dp(ctx, 13));
            GradientDrawable bg = new GradientDrawable(
                    GradientDrawable.Orientation.TOP_BOTTOM,
                    m3
                            ? (night ? new int[]{0xFF2A211C, 0xFF211915}
                                     : new int[]{0xFFF7DED4, 0xFFFBEADE})
                            : (night ? new int[]{0xFF2A211C, 0xFF211915}
                                     : new int[]{0xFFFDF6EC, 0xFFF8EDE1}));
            bg.setCornerRadius(dp(ctx, m3 ? 28 : 24));
            card.setBackground(bg);

            // row 1: art + title/artist
            LinearLayout top = new LinearLayout(ctx);
            top.setOrientation(LinearLayout.HORIZONTAL);
            top.setGravity(Gravity.CENTER_VERTICAL);
            ImageView artView = new ImageView(ctx);
            artView.setScaleType(ImageView.ScaleType.CENTER_CROP);
            GradientDrawable artBg = new GradientDrawable();
            artBg.setColor(night ? 0x33FFFFFF : 0x14000000);
            artBg.setCornerRadius(dp(ctx, m3 ? 16 : 8));
            artView.setBackground(artBg);
            artView.setClipToOutline(true);
            artView.setElevation(dp(ctx, 2));
            if (art != null) {
                if (art.getDrawable() != null) {
                    artView.setImageDrawable(art.getDrawable());
                }
                art.setVisibility(View.GONE);
            }
            top.addView(artView, new LinearLayout.LayoutParams(
                    dp(ctx, m3 ? 64 : 60), dp(ctx, m3 ? 64 : 60)));
            LinearLayout texts = new LinearLayout(ctx);
            texts.setOrientation(LinearLayout.VERTICAL);
            LinearLayout.LayoutParams textsLp = new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            textsLp.leftMargin = dp(ctx, 12);
            texts.setLayoutParams(textsLp);
            reparent(title);
            CharSequence titleCs = title.getText();
            title.setText(titleCs == null ? "" : titleCs.toString());
            title.setTextSize(TypedValue.COMPLEX_UNIT_SP, m3 ? 16 : 17);
            title.setTypeface(null, android.graphics.Typeface.BOLD);
            title.setTextColor(primary);
            title.setSingleLine(true);
            title.setEllipsize(TextUtils.TruncateAt.END);
            title.setPadding(0, 0, 0, 0);
            texts.addView(title, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            if (artist != null) {
                reparent(artist);
                CharSequence artistCs = artist.getText();
                artist.setText(artistCs == null ? "" : artistCs.toString());
                artist.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f);
                artist.setTextColor(secondary);
                artist.setSingleLine(true);
                artist.setEllipsize(TextUtils.TruncateAt.END);
                artist.setPadding(0, 0, 0, 0);
                LinearLayout.LayoutParams al = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                al.topMargin = dp(ctx, 4);
                texts.addView(artist, al);
            }
            top.addView(texts);
            card.addView(top, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

            // row 2: elapsed / seek / total
            LinearLayout seekRow = new LinearLayout(ctx);
            seekRow.setOrientation(LinearLayout.HORIZONTAL);
            seekRow.setGravity(Gravity.CENTER_VERTICAL);
            TextView elapsed = new TextView(ctx);
            elapsed.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10.5f);
            elapsed.setTextColor(secondary);
            elapsed.setText("0:00");
            elapsed.setGravity(Gravity.END);
            elapsed.setMinWidth(dp(ctx, 28));
            elapsed.setFontFeatureSettings("tnum");
            seekRow.addView(elapsed, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

            SeekBar bar = new DragSeekBar(ctx);
            // 通知行的 NotifTouchInterceptor 只放行"忽略名单"里的 id；AOSP 自己的媒体进度条
            // 用的就是 android.R.id.progress，借同一个 id 才拿得到横向手势。
            // 代价：NotificationHeaderViewWrapper 把它当成自己那份进度条，每次 selectLayout
            // 会按布局状态改它可见性（实测每轮 1~3 次）——由 MediaCard.reclaimBar() 自愈。
            // 注意不能覆写 setVisibility（会破坏 RemoteViews 的 @RemotableViewMethod 校验，
            // 导致发通知的音乐 App 被判 BadForegroundServiceNotification 直接闪退）。
            // 换用 systemui 私有 progress_bar id 可以躲开这次改写，但那个 id 是否在滑动忽略
            // 名单里未经验证，换错就吃掉刚修好的拖动手势，所以保持借 android.R.id.progress。
            bar.setId(android.R.id.progress);
            bar.setMax(1);
            bar.setProgress(0);
            bar.setSplitTrack(false);
            bar.setPadding(dp(ctx, 6), dp(ctx, 6), dp(ctx, 6), dp(ctx, 6));
            bar.setBackground(null);
            SeekDrawable seekDrawable = new SeekDrawable(
                    night ? 0x33FFFFFF : 0x1F000000,
                    night ? 0xFFE6E6E6 : 0xFF4A4A4A,
                    ctx.getResources().getDisplayMetrics().density);
            bar.setProgressDrawable(seekDrawable);
            Log.i(TAG, "card attach check: rootAttached=" + root.isAttachedToWindow()
                    + " root=" + root.getClass().getSimpleName()
                    + " rootParent=" + (root.getParent() == null ? "null"
                            : root.getParent().getClass().getSimpleName()));
            Log.i(TAG, "seek view: indeterminate=" + bar.isIndeterminate()
                    + " max=" + bar.getMax() + " progress=" + bar.getProgress()
                    + " progDrawable=" + (bar.getProgressDrawable() == null ? "null"
                            : bar.getProgressDrawable().getClass().getSimpleName())
                    + " enabled=" + bar.isEnabled());
            LinearLayout.LayoutParams bl = new LinearLayout.LayoutParams(0, dp(ctx, 24), 1f);
            bl.leftMargin = dp(ctx, 2);
            bl.rightMargin = dp(ctx, 2);
            seekRow.addView(bar, bl);

            TextView total = new TextView(ctx);
            total.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10.5f);
            total.setTextColor(secondary);
            total.setText("0:00");
            total.setGravity(Gravity.START);
            total.setMinWidth(dp(ctx, 28));
            total.setFontFeatureSettings("tnum");
            seekRow.addView(total, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

            LinearLayout.LayoutParams seekRowLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            seekRowLp.topMargin = dp(ctx, 11);
            card.addView(seekRow, seekRowLp);

            // 现场几何取证：条在部分机器上塌成 0 宽（两侧时间贴在一起），
            // 绑定阶段 getLocationOnScreen 还是 0，所以等布局完成后再量一次
            bar.post(() -> {
                try {
                    Log.i(TAG, "card bar geom: bar=" + bar.getWidth() + 'x' + bar.getHeight()
                            + " row=" + seekRow.getWidth() + 'x' + seekRow.getHeight()
                            + " rowW=" + seekRow.getMeasuredWidth()
                            + " card=" + card.getWidth()
                            + " el=" + elapsed.getWidth() + " tot=" + total.getWidth()
                            + " weight=" + ((LinearLayout.LayoutParams) bar.getLayoutParams()).weight
                            + " count=" + seekRow.getChildCount());
                    StringBuilder chain = new StringBuilder();
                    android.view.View p2 = bar;
                    for (int i = 0; i < 9 && p2 != null; i++) {
                        chain.append(' ').append(p2.getClass().getSimpleName())
                                .append("(v").append(p2.getVisibility())
                                .append(",a").append(String.format(
                                        java.util.Locale.US, "%.2f", p2.getAlpha()))
                                .append(",w").append(p2.getWidth())
                                .append(",h").append(p2.getHeight()).append(')');
                        if (!(p2.getParent() instanceof android.view.View)) {
                            break;
                        }
                        p2 = (android.view.View) p2.getParent();
                    }
                    Log.i(TAG, "card bar ancestry:" + chain);
                } catch (Throwable ignored) {
                }
            });

            // row 3: source / prev / play / next / heart
            LinearLayout controls = new LinearLayout(ctx);
            controls.setOrientation(LinearLayout.HORIZONTAL);
            controls.setGravity(Gravity.CENTER_VERTICAL);

            FrameLayout sourceSlot = new FrameLayout(ctx);
            LinearLayout.LayoutParams sl = new LinearLayout.LayoutParams(0, dp(ctx, 44), 1f);
            controls.addView(sourceSlot, sl);
            ImageView sourceView = new ImageView(ctx);
            sourceView.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
            GradientDrawable ph = new GradientDrawable();
            ph.setShape(GradientDrawable.OVAL);
            ph.setColor(night ? 0x1FFFFFFF : 0x14000000);
            sourceView.setBackground(ph);
            sourceView.setClipToOutline(true);
            if (sourceIcon != null && sourceIcon.getDrawable() != null) {
                sourceView.setImageDrawable(sourceIcon.getDrawable());
            }
            FrameLayout.LayoutParams flp = new FrameLayout.LayoutParams(
                    dp(ctx, 30), dp(ctx, 30), Gravity.START | Gravity.CENTER_VERTICAL);
            sourceSlot.addView(sourceView, flp);

            ImageButton prev = null;
            ImageButton play = null;
            ImageButton next = null;
            ImageButton heart = null;
            List<ImageButton> others = new ArrayList<>();
            int n = actions.getChildCount();
            for (int i = 0; i < n; i++) {
                View child = actions.getChildAt(i);
                if (!(child instanceof ImageButton)) {
                    continue;
                }
                ImageButton b = (ImageButton) child;
                String desc = b.getContentDescription() == null ? "" : b.getContentDescription().toString();
                Log.i(TAG, "action[" + i + "] desc=" + desc);
                if (isPrev(desc)) {
                    prev = b;
                } else if (isNext(desc)) {
                    next = b;
                } else if (isPlay(desc)) {
                    play = b;
                } else if (isHeart(desc)) {
                    heart = b;
                } else if (!isLyric(desc)) {
                    others.add(b);
                }
            }
            if (prev == null && play == null && next == null && n >= 4) {
                View a0 = actions.getChildAt(0);
                View a1 = actions.getChildAt(1);
                View a2 = actions.getChildAt(2);
                View a3 = actions.getChildAt(3);
                if (a1 instanceof ImageButton) {
                    prev = (ImageButton) a1;
                }
                if (a2 instanceof ImageButton) {
                    play = (ImageButton) a2;
                }
                if (a3 instanceof ImageButton) {
                    next = (ImageButton) a3;
                }
                if (heart == null && n >= 5 && actions.getChildAt(4) instanceof ImageButton) {
                    heart = (ImageButton) actions.getChildAt(4);
                }
                if (heart == null && a0 instanceof ImageButton) {
                    String d0 = String.valueOf(((ImageButton) a0).getContentDescription());
                    if (!isLyric(d0)) {
                        heart = (ImageButton) a0;
                    }
                }
            }
            if (heart == null && !others.isEmpty()) {
                heart = others.get(0);
            }

            addControl(ctx, controls, prev, 40, primary);
            addControl(ctx, controls, play, 44, accent);
            addControl(ctx, controls, next, 40, primary);

            FrameLayout heartSlot = new FrameLayout(ctx);
            LinearLayout.LayoutParams hl = new LinearLayout.LayoutParams(0, dp(ctx, 44), 1f);
            controls.addView(heartSlot, hl);
            if (heart != null) {
                reparent(heart);
                heart.setBackground(null);
                heart.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
                heart.setMinimumWidth(dp(ctx, 40));
                heart.setMinimumHeight(dp(ctx, 40));
                int hp = Math.round(dp(ctx, 40) * 0.16f);
                heart.setPadding(hp, hp, hp, hp);
                if (night) {
                    heart.setColorFilter(primary);
                }
                FrameLayout.LayoutParams hflp = new FrameLayout.LayoutParams(
                        dp(ctx, 40), dp(ctx, 40), Gravity.END | Gravity.CENTER_VERTICAL);
                heartSlot.addView(heart, hflp);
            }

            LinearLayout.LayoutParams controlsLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            controlsLp.topMargin = dp(ctx, 7);
            card.addView(controls, controlsLp);

            ViewGroup parent = (ViewGroup) root;
            if (parent instanceof LinearLayout) {
                parent.addView(card, 0, new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            } else {
                parent.addView(card, 0, new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            }

            hideOld(ctx, root);
            clearAncestorBackgrounds(root);

            MediaCard mc = new MediaCard(root, card, art, artView, bar, seekDrawable, elapsed,
                    total, title, artist, play, sourceView, style);
            mc.applyPalette(false, null);
            mc.syncArt();
            bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                }

                @Override
                public void onStartTrackingTouch(SeekBar seekBar) {
                    mc.dragging = true;
                }

                @Override
                public void onStopTrackingTouch(SeekBar seekBar) {
                    mc.dragging = false;
                    if (!mc.canSeek) {
                        mc.updateProgress();
                        return;
                    }
                    long target = seekBar.getProgress();
                    boolean done = false;
                    try {
                        PlaybackState st = mc.controller != null
                                ? mc.controller.getPlaybackState() : null;
                        boolean nativeSeek = st != null
                                && (st.getActions() & PlaybackState.ACTION_SEEK_TO) != 0;
                        if (nativeSeek && mc.controller != null) {
                            mc.controller.getTransportControls().seekTo(target);
                            done = true;
                        }
                    } catch (Throwable t) {
                        Log.w(TAG, "native seek failed", t);
                    }
                    if (!done) {
                        done = MediaCardFeature.seekViaWidget(target);
                    }
                    if (done) {
                        mc.pendingSeekMs = target;
                        mc.suppressUntil = SystemClock.elapsedRealtime() + 2000;
                    } else {
                        mc.pendingSeekMs = -1;
                        mc.updateProgress();
                    }
                }
            });
            root.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
                @Override
                public void onViewAttachedToWindow(View v) {
                    mc.onAttached();
                }

                @Override
                public void onViewDetachedFromWindow(View v) {
                    mc.release();
                }
            });
            if (root.isAttachedToWindow()) {
                mc.onAttached();
            }
            Log.i(TAG, "atomic card built: prev=" + (prev != null) + " play=" + (play != null)
                    + " next=" + (next != null) + " heart=" + (heart != null)
                    + " source=" + (sourceIcon != null));
            return mc;
        }

        private static void addControl(Context ctx, LinearLayout parent, ImageButton b,
                                       int sizeDp, int tint) {
            if (b == null) {
                View v = new View(ctx);
                parent.addView(v, new LinearLayout.LayoutParams(0, dp(ctx, sizeDp), 1f));
                return;
            }
            reparent(b);
            b.setBackground(null);
            b.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
            b.setMinimumWidth(dp(ctx, sizeDp));
            b.setMinimumHeight(dp(ctx, sizeDp));
            int pad = Math.round(dp(ctx, sizeDp) * 0.16f);
            b.setPadding(pad, pad, pad, pad);
            if (tint != 0) {
                b.setColorFilter(tint);
            }
            parent.addView(b, new LinearLayout.LayoutParams(0, dp(ctx, sizeDp), 1f));
        }

        private static void clearAncestorBackgrounds(View root) {
            try {
                ViewParent p = root.getParent();
                int level = 0;
                while (p instanceof View && level < 4) {
                    View v = (View) p;
                    Log.i(TAG, "ancestor[" + level + "] " + v.getClass().getName()
                            + " bg=" + v.getBackground());
                    if (v.getBackground() != null) {
                        v.setBackground(null);
                    }
                    if (level == 1 && v instanceof ViewGroup) {
                        ViewGroup row = (ViewGroup) v;
                        for (int i = 0; i < row.getChildCount(); i++) {
                            View c = row.getChildAt(i);
                            String cn = c.getClass().getName();
                            Log.i(TAG, "rowChild[" + i + "]=" + cn + " bg=" + c.getBackground()
                                    + " vis=" + c.getVisibility() + " alpha=" + c.getAlpha());
                            if (c.getBackground() != null) {
                                c.setBackground(null);
                            }
                            if (cn.contains("NotificationBackgroundView")) {
                                c.setAlpha(0f);
                                c.setVisibility(View.INVISIBLE);
                            }
                            if (c instanceof ViewGroup) {
                                ViewGroup g = (ViewGroup) c;
                                for (int j = 0; j < g.getChildCount(); j++) {
                                    View cc = g.getChildAt(j);
                                    Log.i(TAG, "  rowChild[" + i + "][" + j + "]="
                                            + cc.getClass().getName() + " bg=" + cc.getBackground()
                                            + " vis=" + cc.getVisibility());
                                    if (cc.getBackground() != null) {
                                        cc.setBackground(null);
                                    }
                                }
                            }
                        }
                    }
                    p = v.getParent();
                    level++;
                }
            } catch (Throwable t) {
                Log.w(TAG, "clearAncestorBackgrounds failed", t);
            }
        }

        private static void hideOld(Context ctx, View root) {
            String[] names = {"notification_media_content", "notification_headerless_view_row",
                    "notification_headerless_view_column", "notification_main_column", "media_actions"};
            for (String name : names) {
                int id = id(ctx, name);
                if (id == 0) {
                    continue;
                }
                View v = root.findViewById(id);
                if (v != null) {
                    v.setVisibility(View.GONE);
                }
            }
        }

        private static void reparent(View v) {
            ViewParent p = v.getParent();
            if (p instanceof ViewGroup) {
                ((ViewGroup) p).removeView(v);
            }
        }

        private static boolean isPrev(String s) {
            return s.contains("上一首") || s.toLowerCase(Locale.ROOT).contains("previous")
                    || s.toLowerCase(Locale.ROOT).contains("prev");
        }

        private static boolean isNext(String s) {
            return s.contains("下一首") || s.toLowerCase(Locale.ROOT).contains("next");
        }

        private static boolean isPlay(String s) {
            return s.contains("暂停") || s.contains("播放") || s.toLowerCase(Locale.ROOT).contains("pause")
                    || s.toLowerCase(Locale.ROOT).contains("play");
        }

        private static boolean isHeart(String s) {
            return s.contains("收藏") || s.contains("喜欢") || s.toLowerCase(Locale.ROOT).contains("favorite")
                    || s.toLowerCase(Locale.ROOT).contains("like");
        }

        private static boolean isLyric(String s) {
            return s.contains("歌词") || s.toLowerCase(Locale.ROOT).contains("lyric");
        }

        // ---- dynamic color from album art ----

        static Bitmap metadataArt(MediaMetadata md) {
            if (md == null) {
                return null;
            }
            Bitmap b = md.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART);
            if (b == null) {
                b = md.getBitmap(MediaMetadata.METADATA_KEY_ART);
            }
            if (b == null) {
                b = md.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON);
            }
            return b;
        }

        static Bitmap viewArtBitmap(ImageView art) {
            if (art == null) {
                return null;
            }
            Drawable d = art.getDrawable();
            if (!(d instanceof BitmapDrawable)) {
                return null;
            }
            return ((BitmapDrawable) d).getBitmap();
        }

        private static int artHash(Bitmap b) {
            if (b == null || b.isRecycled()) {
                return 0;
            }
            int h = b.getWidth() * 31 + b.getHeight();
            try {
                int w = b.getWidth();
                int hh = b.getHeight();
                for (int y = 0; y < 6; y++) {
                    for (int x = 0; x < 6; x++) {
                        int px = b.getPixel(Math.min(w - 1, (x * 2 + 1) * w / 12),
                                Math.min(hh - 1, (y * 2 + 1) * hh / 12));
                        h = h * 31 + px;
                    }
                }
            } catch (Throwable t) {
                h = h * 31 + b.getGenerationId();
            }
            return h;
        }

        private static int artHash(ImageView art) {
            return artHash(viewArtBitmap(art));
        }

        static float dominantHue(Bitmap bmp) {
            if (bmp == null || bmp.isRecycled()) {
                return 20f;
            }
            Bitmap small = null;
            try {
                small = Bitmap.createScaledBitmap(bmp, 24, 24, false);
                int[] px = new int[24 * 24];
                small.getPixels(px, 0, 24, 0, 0, 24, 24);
                float[] score = new float[36];
                float[] hsv = new float[3];
                for (int c : px) {
                    if (Color.alpha(c) < 100) {
                        continue;
                    }
                    Color.colorToHSV(c, hsv);
                    if (hsv[1] < 0.18f || hsv[2] < 0.18f) {
                        continue;
                    }
                    int bucket = Math.min(35, (int) (hsv[0] / 10f));
                    score[bucket] += hsv[1] * hsv[2];
                }
                int best = -1;
                float bestScore = 0.6f;
                for (int i = 0; i < 36; i++) {
                    if (score[i] > bestScore) {
                        bestScore = score[i];
                        best = i;
                    }
                }
                return best < 0 ? 20f : best * 10f + 5f;
            } catch (Throwable t) {
                return 20f;
            } finally {
                if (small != null && small != bmp) {
                    try {
                        small.recycle();
                    } catch (Throwable ignored) {
                    }
                }
            }
        }

        static int hsv(float h, float s, float v) {
            return Color.HSVToColor(new float[]{h, s, v});
        }

        static Bitmap softBitmap(Bitmap src) {
            if (src == null || src.isRecycled() || src.getWidth() < 2 || src.getHeight() < 2) {
                return null;
            }
            Bitmap small = null;
            try {
                int size = 20;
                small = Bitmap.createScaledBitmap(src, size, size, true);
                int[] px = new int[size * size];
                small.getPixels(px, 0, size, 0, 0, size, size);
                int[] out = new int[px.length];
                for (int y = 0; y < size; y++) {
                    for (int x = 0; x < size; x++) {
                        int a = 0, r = 0, g = 0, b = 0, n = 0;
                        for (int dy = -1; dy <= 1; dy++) {
                            for (int dx = -1; dx <= 1; dx++) {
                                int yy = y + dy, xx = x + dx;
                                if (yy < 0 || yy >= size || xx < 0 || xx >= size) {
                                    continue;
                                }
                                int c = px[yy * size + xx];
                                a += Color.alpha(c);
                                r += Color.red(c);
                                g += Color.green(c);
                                b += Color.blue(c);
                                n++;
                            }
                        }
                        out[y * size + x] = Color.argb(a / n, r / n, g / n, b / n);
                    }
                }
                return Bitmap.createBitmap(out, size, size, Bitmap.Config.ARGB_8888);
            } catch (Throwable t) {
                Log.w(TAG, "softBitmap failed", t);
                return null;
            } finally {
                if (small != null && small != src) {
                    try {
                        small.recycle();
                    } catch (Throwable ignored) {
                    }
                }
            }
        }

        static final class ArtBgDrawable extends Drawable {
            final Bitmap bmp;
            final int corner;
            final boolean night;
            final Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG);
            final Paint veil = new Paint(Paint.ANTI_ALIAS_FLAG);
            final Path path = new Path();
            Shader shader;

            ArtBgDrawable(Bitmap bmp, int corner, boolean night) {
                this.bmp = bmp;
                this.corner = corner;
                this.night = night;
            }

            @Override
            protected void onBoundsChange(Rect bounds) {
                int top = night ? 0x8C000000 : 0xB8FFFFFF;
                int bottom = night ? 0xCC000000 : 0xE6FFFFFF;
                paint.setAlpha(night ? 235 : 240);
                shader = new LinearGradient(0, bounds.top, 0, bounds.bottom,
                        top, bottom, Shader.TileMode.CLAMP);
                veil.setShader(shader);
            }

            @Override
            public void draw(Canvas canvas) {
                if (bmp == null || bmp.isRecycled()) {
                    return;
                }
                Rect b = getBounds();
                int save = canvas.save();
                path.reset();
                float c = Math.min(corner, Math.min(b.width(), b.height()) / 2f);
                path.addRoundRect(new RectF(b), c, c, Path.Direction.CW);
                canvas.clipPath(path);
                canvas.drawBitmap(bmp, null, b, paint);
                canvas.drawRect(b, veil);
                canvas.restoreToCount(save);
            }

            @Override
            public void setAlpha(int alpha) {
                paint.setAlpha(alpha);
                veil.setAlpha(alpha);
            }

            @Override
            public void setColorFilter(ColorFilter cf) {
                paint.setColorFilter(cf);
            }

            @Override
            public int getOpacity() {
                return PixelFormat.TRANSLUCENT;
            }
        }

        void applyPalette(boolean animate, Bitmap bmp) {
            try {
                Context ctx = card.getContext();
                boolean night = isNight(ctx);
                Bitmap src = bmp;
                if (src == null && controller != null) {
                    src = metadataArt(controller.getMetadata());
                }
                if (src == null) {
                    src = viewArtBitmap(art);
                }
                int h = artHash(src);
                if (h != 0 && h == lastArtHash && curBg != null) {
                    return;
                }
                boolean m3 = "material".equals(style);
                Drawable newBg;
                int accent;
                if (m3) {
                    float hue = dominantHue(src);
                    int bg1 = hsv(hue, night ? 0.18f : 0.22f, night ? 0.20f : 0.99f);
                    int bg2 = hsv(hue, night ? 0.24f : 0.30f, night ? 0.16f : 0.955f);
                    accent = hsv(hue, night ? 0.55f : 0.62f, night ? 0.82f : 0.55f);
                    GradientDrawable g = new GradientDrawable(
                            GradientDrawable.Orientation.TOP_BOTTOM, new int[]{bg1, bg2});
                    g.setCornerRadius(dp(ctx, 28));
                    newBg = g;
                } else {
                    Bitmap soft = softBitmap(src);
                    if (soft != null) {
                        newBg = new ArtBgDrawable(soft, dp(ctx, 24), night);
                    } else {
                        GradientDrawable g = new GradientDrawable(
                                GradientDrawable.Orientation.TOP_BOTTOM,
                                night ? new int[]{0xFF2A211C, 0xFF211915}
                                        : new int[]{0xFFFDF6EC, 0xFFF8EDE1});
                        g.setCornerRadius(dp(ctx, 24));
                        newBg = g;
                    }
                    accent = night ? 0xFFF07A64 : 0xFFE0503A;
                }
                if (animate && curBg != null && card.isAttachedToWindow()) {
                    TransitionDrawable td = new TransitionDrawable(new Drawable[]{curBg, newBg});
                    td.setCrossFadeEnabled(true);
                    card.setBackground(td);
                    td.startTransition(320);
                } else {
                    card.setBackground(newBg);
                }
                curBg = newBg;
                if (m3) {
                    int barFill = accent;
                    int trackColor = Color.argb(night ? 0x33 : 0x2E,
                            Color.red(accent), Color.green(accent), Color.blue(accent));
                    seekDrawable.setColors(trackColor, barFill);
                    bar.setThumb(thumb(ctx, barFill));
                    if (playBtn != null) {
                        GradientDrawable circle = new GradientDrawable();
                        circle.setShape(GradientDrawable.OVAL);
                        circle.setColor(accent);
                        playBtn.setBackground(circle);
                        playBtn.setColorFilter(0xFFFFFFFF);
                        int pad = dp(ctx, 10);
                        playBtn.setPadding(pad, pad, pad, pad);
                    }
                    int primary = night ? 0xFFFFFFFF : 0xFF2A1A15;
                    int secondary = night ? 0xB3FFFFFF : 0x8C5A4038;
                    title.setTextColor(primary);
                    artist.setTextColor(secondary);
                    elapsed.setTextColor(secondary);
                    total.setTextColor(secondary);
                } else {
                    int barFill = night ? 0xFFE6E6E6 : 0xFF4A4A4A;
                    int trackColor = night ? 0x33FFFFFF : 0x1F000000;
                    seekDrawable.setColors(trackColor, barFill);
                    bar.setThumb(thumb(ctx, barFill));
                    if (playBtn != null) {
                        playBtn.setBackground(null);
                        playBtn.setColorFilter(night ? 0xFFF07A64 : 0xFFE0503A);
                        int pad = Math.round(dp(ctx, 44) * 0.16f);
                        playBtn.setPadding(pad, pad, pad, pad);
                    }
                    int primary = night ? 0xFFFFFFFF : 0xFF241A16;
                    int secondary = night ? 0xB3FFFFFF : 0x8C322319;
                    title.setTextColor(primary);
                    artist.setTextColor(secondary);
                    elapsed.setTextColor(secondary);
                    total.setTextColor(secondary);
                }
                lastArtHash = h;
            } catch (Throwable t) {
                Log.w(TAG, "applyPalette failed", t);
            }
        }

        static String trackKey(MediaMetadata md) {
            if (md == null) {
                return "";
            }
            MediaDescription d = md.getDescription();
            if (d == null) {
                return "";
            }
            return str(d.getTitle()) + "|" + str(d.getSubtitle()) + "|"
                    + md.getLong(MediaMetadata.METADATA_KEY_DURATION);
        }

        void hideRowBackground() {
            try {
                if (rowBgView == null) {
                    ViewParent p = root.getParent();
                    if (p instanceof View) {
                        p = ((View) p).getParent();
                    }
                    if (p instanceof ViewGroup) {
                        ViewGroup row = (ViewGroup) p;
                        for (int i = 0; i < row.getChildCount(); i++) {
                            View c = row.getChildAt(i);
                            if (c.getClass().getName().contains("NotificationBackgroundView")) {
                                rowBgView = c;
                                break;
                            }
                        }
                    }
                }
                if (rowBgView != null
                        && (rowBgView.getAlpha() != 0f || rowBgView.getVisibility() == View.VISIBLE)) {
                    rowBgView.setAlpha(0f);
                    rowBgView.setVisibility(View.INVISIBLE);
                }
            } catch (Throwable t) {
                Log.w(TAG, "hideRowBackground failed", t);
            }
        }

        void syncArt() {
            hideRowBackground();
            try {
                if (art != null) {
                    art.setVisibility(View.GONE);
                }
                Bitmap meta = controller != null ? metadataArt(controller.getMetadata()) : null;
                int h;
                Drawable d;
                if (meta != null) {
                    h = artHash(meta);
                    d = null;
                } else if (art != null) {
                    d = art.getDrawable();
                    h = artHash(viewArtBitmap(art));
                } else {
                    return;
                }
                if (h == 0 || h == lastDrawableHash) {
                    return;
                }
                if (meta != null) {
                    d = new BitmapDrawable(root.getResources(), meta);
                }
                if (d != null) {
                    artView.setImageDrawable(d);
                    lastDrawableHash = h;
                }
            } catch (Throwable t) {
                Log.w(TAG, "syncArt failed", t);
            }
        }

        void updateSeekable() {
            // 通知被清掉（一键清除）时会话同时销毁：这里读状态会抛 DeadObjectException，
            // 而 MediaController 回调跑在主线程、不在 Xposed 的异常保护范围内 -> SystemUI 重启
            try {
                updateSeekableInner();
            } catch (Throwable t) {
                canSeek = false;
                Log.w(TAG, "updateSeekable failed", t);
            }
        }

        private void updateSeekableInner() {
            boolean nativeSeek = false;
            long actions = 0;
            if (controller != null) {
                PlaybackState st = controller.getPlaybackState();
                if (st != null) {
                    actions = st.getActions();
                    nativeSeek = (actions & PlaybackState.ACTION_SEEK_TO) != 0;
                }
            }
            boolean seekable = nativeSeek || widgetSeekAvailable();
            if (seekable == canSeek && bar.isEnabled() == seekable) {
                return;
            }
            canSeek = seekable;
            int[] bl = new int[2];
            bar.getLocationOnScreen(bl);
            Log.i(TAG, "seekable=" + seekable + " native=" + nativeSeek
                    + " widget=" + widgetSeekAvailable() + " actions=" + actions
                    + " barAt=" + bl[0] + ',' + bl[1] + 'x' + bar.getWidth() + 'x' + bar.getHeight());
            bar.setEnabled(seekable);
            if (seekable) {
                bar.setOnTouchListener(null);
                bar.setAlpha(1f);
            } else {
                bar.setOnTouchListener((v, event) -> true);
                bar.setAlpha(0.85f);
            }
        }

        void updateSourceIcon() {
            if (controller == null) {
                return;
            }
            try {
                Drawable d = root.getContext().getPackageManager()
                        .getApplicationIcon(controller.getPackageName());
                if (d != null) {
                    sourceView.setImageDrawable(d);
                }
            } catch (Throwable t) {
                Log.w(TAG, "app icon failed", t);
            }
        }

        void refreshColors(boolean animate) {
            try {
                MediaMetadata md = controller != null ? controller.getMetadata() : null;
                String key = trackKey(md);
                if (!key.isEmpty() && key.equals(lastTrackKey)) {
                    return;
                }
                Bitmap src = metadataArt(md);
                if (src == null) {
                    src = viewArtBitmap(art);
                }
                if (src == null && curBg != null) {
                    lastTrackKey = key;
                    return;
                }
                Log.i(TAG, "track changed, retinting: " + key);
                applyPalette(animate, src);
                lastTrackKey = key;
            } catch (Throwable t) {
                Log.w(TAG, "refreshColors failed", t);
            }
        }

        static final class SeekDrawable extends Drawable {
            final Paint trackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            final float density;
            int baseTrack;
            int baseFill;
            int mDraws;

            SeekDrawable(int trackColor, int fillColor, float density) {
                this.density = density;
                baseTrack = trackColor;
                baseFill = fillColor;
                applyColors();
            }

            /** 画笔颜色永远由基色推导，任何外部改动都会在下次刷新时被纠正回来 */
            void applyColors() {
                trackPaint.setColor(baseTrack);
                fillPaint.setColor(baseFill);
            }

            void setColors(int trackColor, int fillColor) {
                baseTrack = trackColor;
                baseFill = fillColor;
                applyColors();
                invalidateSelf();
            }

            @Override
            public void draw(Canvas canvas) {
                Rect b = getBounds();
                if (mDraws++ < 3) {
                    Log.i(TAG, "seek draw #" + mDraws + " bounds=" + b.toShortString()
                            + " w=" + b.width() + " h=" + b.height()
                            + " level=" + getLevel()
                            + " track=#" + Integer.toHexString(trackPaint.getColor())
                            + " fill=#" + Integer.toHexString(fillPaint.getColor()));
                }
                if (b.width() <= 0 || b.height() <= 0) {
                    return;
                }
                float trackH = Math.min(density * 4f, b.height());
                float fillH = Math.min(density * 10f, b.height());
                float cy = b.centerY();
                RectF track = new RectF(b.left, cy - trackH / 2f, b.right, cy + trackH / 2f);
                canvas.drawRoundRect(track, trackH / 2f, trackH / 2f, trackPaint);
                float level = getLevel() / 10000f;
                if (level > 0f) {
                    RectF fill = new RectF(b.left, cy - fillH / 2f,
                            b.left + b.width() * Math.min(1f, level), cy + fillH / 2f);
                    canvas.drawRoundRect(fill, fillH / 2f, fillH / 2f, fillPaint);
                } else {
                    canvas.drawCircle(b.left, cy, fillH / 2f, fillPaint);
                }
            }

            @Override
            protected boolean onLevelChange(int level) {
                // 每次进度刷新都按"基色"重算画笔：框架的 setAlpha 早晚会来，
                // 重算一次就不会被谁永久压成不可见
                applyColors();
                invalidateSelf();
                return true;
            }

            /**
             * 故意不覆盖画笔 alpha：AbsSeekBar.drawableStateChanged 会按 enabled
             * 调 progressDrawable.setAlpha()，而我们的 setColors 只在换曲时才重跑，
             * 于是条会被粘成不可见（OS3 上会话不支持 seek 时条恒为 disabled，必踩）。
             * 禁用态的"变淡"已经由 bar.setAlpha(0.85f) 这个视图级 alpha 表达了。
             */
            @Override
            public void setAlpha(int alpha) {
            }

            @Override
            public void setColorFilter(ColorFilter cf) {
                trackPaint.setColorFilter(cf);
                fillPaint.setColorFilter(cf);
            }

            @Override
            public int getOpacity() {
                return PixelFormat.TRANSLUCENT;
            }
        }

        private static Drawable track(Context ctx, int accent, int trackColor) {
            GradientDrawable bg = new GradientDrawable();
            bg.setCornerRadius(dp(ctx, 2));
            bg.setColor(trackColor);
            GradientDrawable fg = new GradientDrawable();
            fg.setCornerRadius(dp(ctx, 2));
            fg.setColor(accent);
            ClipDrawable clip = new ClipDrawable(fg, Gravity.LEFT, ClipDrawable.HORIZONTAL);
            LayerDrawable ld = new LayerDrawable(new Drawable[]{bg, clip});
            ld.setId(0, android.R.id.background);
            ld.setId(1, android.R.id.progress);
            return ld;
        }

        private static Drawable thumb(Context ctx, int accent) {
            GradientDrawable d = new GradientDrawable();
            d.setShape(GradientDrawable.OVAL);
            d.setColor(0x00000000);
            d.setSize(dp(ctx, 10), dp(ctx, 10));
            return d;
        }

        // ---- media session ----

        void onAttached() {
            if (controller == null) {
                bindSession();
            }
            startTicker();
        }

        void bindSession() {
            if (controller != null) {
                return;
            }
            Log.i(TAG, "bindSession enter root=" + root.getClass().getSimpleName()
                    + " attached=" + root.isAttachedToWindow());
            try {
                // 必须用 SystemUI 自己的 context：通知视图的 context 是发通知 App 的
                // createPackageContext，A13 的 MediaSessionService 会拒绝
                // （IllegalArgumentException: packageName does not belong to the calling uid）
                MediaSessionManager msm = (MediaSessionManager) sysuiContext(root.getContext())
                        .getSystemService(Context.MEDIA_SESSION_SERVICE);
                if (msm == null) {
                    Log.w(TAG, "no MediaSessionManager");
                    return;
                }
                List<MediaController> list = msm.getActiveSessions(null);
                MediaController chosen = null;
                String wanted = title.getText() == null ? "" : title.getText().toString();
                for (MediaController c : list) {
                    MediaMetadata md = c.getMetadata();
                    String t = md == null ? "" : str(md.getDescription().getTitle());
                    if (!wanted.isEmpty() && t.contains(wanted)) {
                        chosen = c;
                        break;
                    }
                }
                if (chosen == null) {
                    for (MediaController c : list) {
                        PlaybackState st = c.getPlaybackState();
                        if (st != null && st.getState() == PlaybackState.STATE_PLAYING) {
                            chosen = c;
                            break;
                        }
                    }
                }
                if (chosen == null && !list.isEmpty()) {
                    chosen = list.get(0);
                }
                if (chosen == null) {
                    Log.i(TAG, "no media session found");
                    return;
                }
                Log.i(TAG, "session=" + chosen.getPackageName() + " state="
                        + (chosen.getPlaybackState() == null ? -1 : chosen.getPlaybackState().getState()));
                controller = chosen;
                updateSourceIcon();
                syncArt();
                chosen.registerCallback(callback, handler);
                updateSeekable();
                updateProgress();
            } catch (Throwable t) {
                Log.w(TAG, "bindSession failed", t);
            }
        }

        void startTicker() {
            if (tickerRunning) {
                return;
            }
            tickerRunning = true;
            handler.post(ticker);
        }

        /**
         * MediaNotificationView 自己会按 android.R.id.progress 找"它的"进度条并显隐，
         * 而我们借同一个 id 过 NotifTouchInterceptor 的手势白名单，于是它会把我们自绘的条
         * 设成 INVISIBLE + alpha 0（实测祖先链 DragSeekBar(v4,a0.00)）。
         * id 不能改（OS5 的拖动靠它），所以每次刷新都把自己的可见性/alpha 纠正回来。
         */
        void reclaimBar() {
            try {
                float want = canSeek ? 1f : 0.85f;
                if (bar.getVisibility() != View.VISIBLE) {
                    Log.i(TAG, "card bar reclaimed visibility was " + bar.getVisibility());
                    bar.setVisibility(View.VISIBLE);
                }
                if (Math.abs(bar.getAlpha() - want) > 0.01f) {
                    bar.setAlpha(want);
                }
            } catch (Throwable ignored) {
            }
        }

        void updateProgress() {
            if (controller == null) {
                return;
            }
            try {
                reclaimBar();
                long now = SystemClock.elapsedRealtime();
                if (!canSeek) {
                    pendingSeekMs = -1;
                }
                if (pendingSeekMs >= 0 && now < suppressUntil) {
                    if (!dragging) {
                        bar.setProgress((int) pendingSeekMs);
                        elapsed.setText(fmt(pendingSeekMs));
                    }
                    return;
                }
                pendingSeekMs = -1;
                MediaMetadata md = controller.getMetadata();
                PlaybackState st = controller.getPlaybackState();
                long duration = md == null ? 0 : md.getLong(MediaMetadata.METADATA_KEY_DURATION);
                long position = st == null ? 0 : st.getPosition();
                if (st != null && st.getState() == PlaybackState.STATE_PLAYING) {
                    position += (long) ((SystemClock.elapsedRealtime() - st.getLastPositionUpdateTime())
                            * st.getPlaybackSpeed());
                }
                boolean noDur = duration <= 0;
                if (noDur != lastNoDur) {
                    lastNoDur = noDur;
                    Log.i(TAG, "card progress source: dur=" + duration + " pos=" + position
                            + " mdNull=" + (md == null) + " stNull=" + (st == null)
                            + " canSeek=" + canSeek + " barW=" + bar.getWidth());
                }
                if (duration <= 0) {
                    bar.setMax(1);
                    bar.setProgress(0);
                } else {
                    bar.setMax((int) duration);
                    if (!dragging) {
                        bar.setProgress((int) Math.max(0, Math.min(position, duration)));
                    }
                }
                elapsed.setText(fmt(position));
                total.setText(fmt(duration));
            } catch (Throwable t) {
                Log.w(TAG, "updateProgress failed", t);
            }
        }

        void release() {
            tickerRunning = false;
            handler.removeCallbacksAndMessages(null);
            if (controller != null) {
                try {
                    controller.unregisterCallback(callback);
                } catch (Throwable ignored) {
                }
                controller = null;
            }
        }
    }

    private static String str(CharSequence cs) {
        return cs == null ? "" : cs.toString();
    }

    private static String fmt(long ms) {
        if (ms < 0) {
            ms = 0;
        }
        long sec = ms / 1000;
        return String.format(Locale.US, "%d:%02d", sec / 60, sec % 60);
    }
}
