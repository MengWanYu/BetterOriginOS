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
import android.view.MotionEvent;
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

    /** 选项监听要用实例方法重建卡片；install 时记下唯一实例（每进程一个 Feature 实例） */
    private static volatile MediaCardFeature sSelf;

    /** 不可拖动时吞掉触摸。A11 本体靠 `setEnabled(false)`；这里两者一起做，避免手势穿到下层。 */
    private static final android.view.View.OnTouchListener CONSUME_TOUCH = (v, event) -> true;

    /**
     * A11 标题的字族实例。两态共用同一个对象，`titleMedium=` 见证的同一性比较才成立；
     * 每次 Typeface.create 可能返回不同实例，见证就会假红（内部版 android.jar 没有
     * Typeface.getFamily()，只能按实例身份比）。
     * 设备真值（LOG-1103 §20-1）：line1 = sans-serif-medium 族**并且**布局又叠了 textStyle=BOLD，
     * 所以生效的是 medium+BOLD 合成那一个实例，同样只创建一次。
     */
    private static android.graphics.Typeface sA11MediumBold;

    private static android.graphics.Typeface a11MediumBoldTitle() {
        if (sA11MediumBold == null) {
            sA11MediumBold = android.graphics.Typeface.create(
                    "sans-serif-medium", android.graphics.Typeface.BOLD);
        }
        return sA11MediumBold;
    }

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
    /** 我们自己的进度条 id（不与系统/RemoteViews 的 id 冲突）。 */
    private static final int ID_A11_SEEK = 0x0B0A11;

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
            // 不借系统 id 之后，横向手势由这里拿住（按下禁止父级拦截、抬起/取消放开）。
            int act = event.getActionMasked();
            if (act == android.view.MotionEvent.ACTION_DOWN && getParent() != null) {
                getParent().requestDisallowInterceptTouchEvent(true);
            } else if ((act == android.view.MotionEvent.ACTION_UP
                    || act == android.view.MotionEvent.ACTION_CANCEL) && getParent() != null) {
                getParent().requestDisallowInterceptTouchEvent(false);
            }
            return super.onTouchEvent(event);
        }
    }


    /** 给卡片里**所有**动作按钮上同一前景色。原来只给播放键设了 colorFilter，
     *  切歌/其它按钮还留着系统默认白图标，深色封面上就"只有暂停键看得清"（真机反馈）。 */
    private static void tintActionButtons(View v, int color) {
        if (v == null) {
            return;
        }
        if (v instanceof ImageButton) {
            ((ImageButton) v).setColorFilter(color);
            return;
        }
        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) {
                tintActionButtons(g.getChildAt(i), color);
            }
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
                new String[]{"os5", "material", "a11"},
                new int[]{R.string.style_os5_title, R.string.style_m3_title,
                        R.string.style_a11_title},
                new int[]{R.drawable.preview_os5, R.drawable.preview_m3,
                        R.drawable.preview_a11}));
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
        sSelf = this;
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
                            // A11 的收起态是"着色行"、展开态才是"深色卡"，两条取色链不同，
                            // 所以必须记住这次是哪个 setter 触发的
                            String method = param.method == null ? "" : param.method.getName();
                            restyle((View) child, method.contains("Expanded"));
                        } catch (Throwable t) {
                            Log.e(TAG, "restyle failed", t);
                        }
                    }
                }
            };
            int n1 = XposedBridge.hookAllMethods(c, "setExpandedChild", hook).size();
            int n2 = XposedBridge.hookAllMethods(c, "setContractedChild", hook).size();
            // 展开/收起翻转时重算已有的卡。A13 真机读数说明绑定路径接不到展开：
            // `expandNotification()` 之后 `isExpanded` 已翻成 true，
            // 但 `setExpandedChild` 没有再跑 ⇒ `a11 art state=expanded` 见证 0 条，
            // 而 A11 的收起态是"着色行"、展开态是"深色卡"，两态排版与取色链都不同。
            // 信号用系统自己的 `ExpandableNotificationRow.onExpansionChanged(userChanged, 旧状态)`
            // （OS3 jadx:740）—— **新状态不在参数里**，要回读 `isExpanded()`。
            int n3 = installExpansionHook(cl);
            installSweepHook(cl);
            OsProfile.report();
            Log.i(TAG, "media card feature installed expandedHooks=" + n1
                    + " contractedHooks=" + n2 + " expansionHooks=" + n3);
        } catch (Throwable t) {
            Log.e(TAG, "media card feature install failed", t);
        }
    }

    private static int sBindCount;
    private static int sExpansionRestyles;

    /** 挂 `ExpandableNotificationRow.onExpansionChanged`；返回命中方法数。 */
    private int installExpansionHook(ClassLoader cl) {
        try {
            Class<?> rowCls = XposedHelpers.findClass(
                    "com.android.systemui.statusbar.notification.row.ExpandableNotificationRow", cl);
            return XposedBridge.hookAllMethods(rowCls, "onExpansionChanged", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    if (!(param.thisObject instanceof View)) {
                        return;
                    }
                    View row = (View) param.thisObject;
                    boolean now;
                    try {
                        now = Boolean.TRUE.equals(XposedHelpers.callMethod(row, "isExpanded"));
                    } catch (Throwable t) {
                        return;
                    }
                    try {
                        restyleOnExpansion(row, now);
                    } catch (Throwable t) {
                        Log.w(TAG, "media expansion restyle failed", t);
                    }
                }
            }).size();
        } catch (Throwable t) {
            Log.i(TAG, "media expansion hook class missing: " + t.getMessage());
            return 0;
        }
    }

    /** 把挂在这行里的卡切到新状态（setState 自己会跳过非 a11 与状态未变的卡）。 */
    private void restyleOnExpansion(View row, boolean expanded) {
        java.util.List<MediaCard> hits = new java.util.ArrayList<>();
        synchronized (CARDS) {
            for (java.util.Map.Entry<View, MediaCard> e : CARDS.entrySet()) {
                View root = e.getKey();
                if (root != null && (root == row || isDescendantOf(root, row))) {
                    hits.add(e.getValue());
                }
            }
        }
        for (MediaCard c : hits) {
            c.setState(Boolean.valueOf(expanded));
        }
        // 见证：展开这条路真的走到了卡片，而不是只把调用发出去了
        if (!hits.isEmpty() && ++sExpansionRestyles <= 6) {
            Log.i(TAG, "media expansion restyle cards=" + hits.size()
                    + " expanded=" + (expanded ? 1 : 0));
        }
    }

    private static boolean isDescendantOf(View v, View ancestor) {
        for (View p = v.getParent() instanceof View ? (View) v.getParent() : null;
                p != null; p = p.getParent() instanceof View ? (View) p.getParent() : null) {
            if (p == ancestor) {
                return true;
            }
        }
        return false;
    }

    private static long sLastSweepAt;
    private static boolean sTreeDumped;
    private static long sLastTreeDumpAt;

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
                        // 先清孤儿：重绑会换掉整棵 child 子树，旧 root 已不在窗口，
                        // 留着只会被 refreshColors 盲刷（OS4 真机：bbkmusic 每首歌换一次通知）
                        java.util.List<View> orphans = new java.util.ArrayList<>();
                        synchronized (CARDS) {
                            for (View k : CARDS.keySet()) {
                                if (!k.isAttachedToWindow()) {
                                    orphans.add(k);
                                }
                            }
                        }
                        for (View k : orphans) {
                            MediaCard mc;
                            synchronized (CARDS) {
                                mc = CARDS.remove(k);
                            }
                            if (mc != null) {
                                mc.release();
                            }
                        }
                        ViewGroup list = (ViewGroup) param.thisObject;
                        java.util.List<View> found = new java.util.ArrayList<>();
                        for (int i = 0; i < list.getChildCount(); i++) {
                            collectMediaViews(list.getChildAt(i), found, 0);
                        }
                        if (found.isEmpty() && list.getChildCount() > 0) {
                            // 行存在却匹配不上：把首行的类树摊出来。旧版"每进程一次"在
                            // OS4 上不够用——静默区行关面板后会被 vivo 换成另一套容器，
                            // 卡片随面板关闭被孤儿清理，重开面板时若新容器不匹配就永远
                            // 没人补挂（真机 01:17-01:20 序列）。按 15s 限频反复摊。
                            long now2 = android.os.SystemClock.uptimeMillis();
                            if (now2 - sLastTreeDumpAt > 15000) {
                                sLastTreeDumpAt = now2;
                                StringBuilder sb = new StringBuilder();
                                dumpTree(list.getChildAt(0), sb, 0);
                                Log.i(TAG, "media sweep found nothing; row0 tree:" + sb);
                            }
                        }
                        for (View v : found) {
                            boolean known;
                            synchronized (CARDS) {
                                known = CARDS.containsKey(v);
                            }
                            // 只挡 known；瞬态 skip（如"动作按钮还没投完"）必须允许重试——
                            // 曾把 empty-actions 的根永久拉黑（SKIP_LOGGED），剥行重建后
                            // 卡片从此补挂不上，可见行只剩原生新内容盖在旧卡上（真机 06:42 序列）。
                            if (known) {
                                continue;
                            }
                            Log.i(TAG, "media sweep attach cls=" + v.getClass().getName());
                            restyle(v, null);
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

    /** 只认媒体模板根：类名（OS5/OS3）或探测组合（OS4 展开态）命中即可，卡片要加在它里面 */
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
        if (!(v instanceof ViewGroup)) {
            return;
        }
        ViewGroup g = (ViewGroup) v;
        int before = out.size();
        for (int i = 0; i < g.getChildCount(); i++) {
            collectMediaViews(g.getChildAt(i), out, depth + 1);
        }
        // OS4：展开态模板根没有类名/媒体 id 可认——先递归保证取最内层，
        // 子树里没有任何更内层的媒体根、而自己能同时探测出 actions+title 才算模板根
        if (out.size() == before && discoverMedia(g.getContext(), g).complete()) {
            out.add(g);
        }
    }
    private static final java.util.Set<View> SKIP_LOGGED =
            java.util.Collections.newSetFromMap(new java.util.WeakHashMap<View, Boolean>());

    /**
     * 每个视图只报一次早退原因，但一定要报：
     * "媒体卡没出现"过去完全没有线索，只能靠猜（实测踩过 20 分钟）。
     * 以前只报类名含 "Media" 的根——OS4（Funtouch 14.1）媒体模板根是普通
     * FrameLayout/LinearLayout，那条过滤把真正的媒体行 skip 吞成了静默，
     * 移植期间一无所见。改成全量（每根实例一条，WeakHashMap 去重兜住量）。
     */
    private static void skipOnce(String why, View root) {
        synchronized (SKIP_LOGGED) {
            if (!SKIP_LOGGED.add(root)) {
                return;
            }
        }
        Log.i(TAG, "media restyle skip " + why + " cls=" + root.getClass().getName()
                + " pkg=" + root.getContext().getPackageName());
    }

    /**
     * 补扫（{@link #installSweepHook}）路径拿不到"这次绑定出自哪个 setter"，expanded 只能是 null，
     * 于是会把**收起子视图**建成展开结构（11-05 加紧凑排布后这条会变成观感缺陷，以前两态结构相同所以看不出来）。
     * 这里改用系统自己的公开判据：{@code NotificationContentView.getContractedChild()/getExpandedChild()}
     * （OS5 两者都在，work/systemui/out-src/NotificationContentView.java:379-384）。
     * 读不到就返回 null，沿用旧的"按展开"，并把原因留在 {@code restyling ... src=} 见证里。
     */
    private static Boolean childRoleOf(View root) {
        ViewParent p = root.getParent();
        if (!(p instanceof View)) {
            return null;
        }
        try {
            if (root == XposedHelpers.callMethod(p, "getContractedChild")) {
                return Boolean.FALSE;
            }
            if (root == XposedHelpers.callMethod(p, "getExpandedChild")) {
                return Boolean.TRUE;
            }
        } catch (Throwable t) {
            skipOnce("child role getter missing: " + t.getClass().getSimpleName(), root);
        }
        return null;
    }

    /** restyle 与补扫共用的媒体视图探测结果：complete() 才值得建卡 */
    private static final class MediaViews {
        ViewGroup actions;
        TextView title;
        TextView artist;
        ImageView art;
        ImageView sourceIcon;
        int headerId;
        /** 识别路径，进见证：id=老模板 / fallback=展开态 MediaStyle 模板 */
        String how = "none";

        boolean complete() {
            return actions != null && title != null;
        }
    }

    /**
     * 媒体模板探测（OS4 适配）：OS5/OS3 的模板（collapsed=notification_media_content 族）
     * 靠 media_actions/title id 就能认；OS4（Funtouch 14.1）的**展开态**是 framework
     * MediaStyle 族（action0..4、无 media_actions、artwork 无 id）——逐级兜底：
     * actions = media_actions → action0 的父容器；art = right_icon → 子树里最大的
     * 方形 ImageView（排除小图标）。任何一级命中老 id 就走老路径，零回归。
     */
    private static MediaViews discoverMedia(Context ctx, View root) {
        MediaViews mv = new MediaViews();
        int actionsId = id(ctx, "media_actions");
        int titleId = id(ctx, "title");
        int textId = id(ctx, "text");
        int artId = id(ctx, "right_icon");
        mv.headerId = id(ctx, "notification_header");
        int iconId = id(ctx, "icon");
        if (titleId != 0) {
            mv.title = root.findViewById(titleId);
        }
        if (textId != 0) {
            mv.artist = root.findViewById(textId);
        }
        if (actionsId != 0) {
            mv.actions = root.findViewById(actionsId);
        }
        if (mv.actions != null) {
            mv.how = "id";
        }
        if (mv.actions == null) {
            // 展开态 MediaStyle：动作按钮挂在 action0..4，取 action0 的父容器
            int a0 = id(ctx, "action0");
            View v = a0 == 0 ? null : root.findViewById(a0);
            if (v != null && v.getParent() instanceof ViewGroup) {
                mv.actions = (ViewGroup) v.getParent();
                mv.how = "action0";
            }
        }
        if (artId != 0) {
            mv.art = root.findViewById(artId);
        }
        if (mv.art == null) {
            mv.art = largestArtView(root);
        }
        if (iconId != 0) {
            mv.sourceIcon = root.findViewById(iconId);
        }
        return mv;
    }

    /** 展开态模板的封面没有 id：子树里"最大的、有内容的、宽≥高的 ImageView"就是它 */
    private static ImageView largestArtView(View root) {
        if (!(root instanceof ViewGroup)) {
            return null;
        }
        ImageView best = null;
        long bestArea = 0;
        java.util.Deque<View> stack = new java.util.ArrayDeque<>();
        stack.push(root);
        while (!stack.isEmpty()) {
            View v = stack.pop();
            if (v instanceof ImageView && v.getWidth() > 0 && v.getHeight() > 0) {
                ImageView iv = (ImageView) v;
                long area = (long) v.getWidth() * v.getHeight();
                if (area > bestArea && v.getWidth() >= v.getHeight()
                        && iv.getDrawable() != null) {
                    best = iv;
                    bestArea = area;
                }
            }
            if (v instanceof ViewGroup) {
                ViewGroup g = (ViewGroup) v;
                for (int i = 0; i < g.getChildCount(); i++) {
                    stack.push(g.getChildAt(i));
                }
            }
        }
        return best;
    }

    /** @param expanded 收起态/展开态（A11 两条取色链不同）；null 表示沿用已有状态 */
    private void restyle(View root, Boolean expanded) {
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
        MediaViews mv = discoverMedia(ctx, root);
        ViewGroup actions = mv.actions;
        TextView title = mv.title;
        TextView artist = mv.artist;
        ImageView art = mv.art;
        ImageView sourceIcon = mv.sourceIcon;
        int headerId = mv.headerId;
        if (!mv.complete()) {
            skipOnce("discover=" + mv.how
                    + " actions=" + (actions == null ? "null" : "ok")
                    + " title=" + (title == null ? "null" : "ok"), root);
            return;
        }
        if (actions.getChildCount() == 0 && expanded == null) {
            // 补扫路径：动作按钮还没被 RemoteViews 装上就建卡 = 一张没有按钮的废卡
            //（真机 00:46 实测 actions=0 也建了）。等 setter 路径来建。
            skipOnce("sweep empty-actions", root);
            return;
        }
        Boolean role = expanded != null ? expanded : childRoleOf(root);
        String style = option(ctx, "style", "os5");
        synchronized (CARDS) {
            MediaCard existing = CARDS.get(root);
            if (existing != null) {
                boolean detached = existing.card.getParent() == null;
                boolean styleChanged = !existing.style.equals(style);
                if (!detached && !styleChanged) {
                    existing.setState(role);
                    existing.refreshColors(true);
                    return;
                }
                // 卡视图被摘走（孤儿）或样式换了：拆掉重建。destroy 会把 title/artist/
                // 按钮归还回模板原位，下面的探测/建卡流程照常工作。
                boolean alive = existing.destroy();
                CARDS.remove(root);
                if (!alive) {
                    return;
                }
                Log.i(TAG, "media card rebuild root=" + root.getClass().getName()
                        + " style=" + style + (styleChanged ? " (style changed)" : " (orphan)"));
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
        Log.i(TAG, "restyling style=" + style + " " + root.getClass().getName()
                + " title=" + title.getText() + " actions=" + actions.getChildCount()
                + " role=" + (role == null ? "unknown" : role.booleanValue() ? "expanded" : "contracted")
                + " src=" + (expanded != null ? "setter" : "getter"));
        MediaCard card = MediaCard.build(ctx, root, actions, title, artist, art, sourceIcon,
                headerId, style, !Boolean.FALSE.equals(role));
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
                                    if ("os5".equals(requested) || "material".equals(requested)
                                            || "a11".equals(requested)) {
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
            // 调试用：门禁必须看到**展开态**的 A11 卡（16/14/14sp 那一套），
            // 而收起态只是它的默认外观。系统自己的展开入口是
            // `ExpandableNotificationRow.expandNotification()`（A13 jadx:1327，
            // 内部就是 `mExpandClickListener.onClick(this)`，与用户点一下等价），
            // 走它比盲 tap 可靠 —— tap 在这两台机上都会把播放器点开或把面板甩掉。
            sysui.getContentResolver().registerContentObserver(
                    Settings.Global.getUriFor(EXPAND_DEBUG_KEY), false,
                    new ContentObserver(new Handler(Looper.getMainLooper())) {
                        @Override
                        public void onChange(boolean selfChange) {
                            try {
                                if (!"1".equals(Settings.Global.getString(
                                        sysui.getContentResolver(), EXPAND_DEBUG_KEY))) {
                                    return;
                                }
                                debugExpandMediaRow();
                            } catch (Throwable t) {
                                Log.w(TAG, "media_card: debug expand failed", t);
                            }
                        }
                    });
            // 样式选项即时生效：OS4 上重绑稀疏（bbkmusic 只在换歌时更新通知），
            // 等"下次换歌"用户要等一分钟以上才能看到新样式。监听选项键，
            // 拆掉旧卡按新样式重建（destroy 会把 title/按钮还回模板，重建无损）。
            sysui.getContentResolver().registerContentObserver(
                    Settings.Global.getUriFor(Prefs.optionKey("media_card", "style")), false,
                    new ContentObserver(new Handler(Looper.getMainLooper())) {
                        @Override
                        public void onChange(boolean selfChange) {
                            try {
                                MediaCardFeature self = sSelf;
                                if (self != null) {
                                    self.forceRestyleAll("style option changed");
                                }
                            } catch (Throwable t) {
                                Log.w(TAG, "media_card: style switch failed", t);
                            }
                        }
                    });
        } catch (Throwable t) {
            Log.e(TAG, "preview observer register failed", t);
        }
    }

    /** 拆掉全部旧卡并按当前选项重建；root 已离开窗口的直接丢弃 */
    void forceRestyleAll(String why) {
        java.util.List<View> roots;
        synchronized (CARDS) {
            roots = new java.util.ArrayList<>(CARDS.keySet());
        }
        int rebuilt = 0, dropped = 0;
        for (View root : roots) {
            MediaCard mc;
            synchronized (CARDS) {
                mc = CARDS.remove(root);
            }
            boolean alive = mc != null && mc.destroy();
            if (alive && root instanceof ViewGroup && root.isAttachedToWindow()) {
                restyle(root, null);
                rebuilt++;
            } else {
                dropped++;
            }
        }
        Log.i(TAG, "force restyle all " + why + " rebuilt=" + rebuilt + " dropped=" + dropped);
    }

    private static final String EXPAND_DEBUG_KEY = "betteroriginos_notify_expand_test";

    /** 展开挂着媒体卡的行；只为产出展开态见证，不参与任何产品行为。 */
    private static void debugExpandMediaRow() {
        java.util.List<View> keys;
        synchronized (CARDS) {
            keys = new java.util.ArrayList<>(CARDS.keySet());
        }
        int expanded = 0, missed = 0;
        StringBuilder after = new StringBuilder();
        // **每一张卡都展开**，不是"挑第一张就 return"：CARDS 是 WeakHashMap，
        // 迭代顺序不定，两台卡并存时（真机实测 cards=2）早退会随机命中非媒体行，
        // 现象是同一份门禁两次跑出不同的结果。
        for (View root : keys) {
            View row = root;
            while (row != null && !row.getClass().getName()
                    .contains("ExpandableNotificationRow")) {
                row = row.getParent() instanceof View ? (View) row.getParent() : null;
            }
            if (row == null) {
                missed++;
                continue;
            }
            try {
                XposedHelpers.callMethod(row, "expandNotification");
                // 回读系统的判断结果：`expanded=2` 只代表"调用发出去了"，
                // 真机上出现过调用后行仍是收起态（于是没有展开态见证）。
                // 不回读就只能猜是调用没生效还是绑定没重跑。
                Object now;
                try {
                    now = XposedHelpers.callMethod(row, "isExpanded");
                } catch (Throwable t) {
                    now = "?";
                }
                after.append(' ').append(now);
                expanded++;
            } catch (Throwable t) {
                Log.w(TAG, "media_card: debug expand call failed", t);
            }
        }
        Log.i(TAG, "media_card: debug expand cards=" + keys.size()
                + " called=" + expanded + " isExpandedAfter=" + after.toString().trim()
                + " noRow=" + missed);
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
            MediaCard.build(ctx, root, actions, title, text, art, null, 0, style, true);

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

    /**
     * A11 通知卡**没有圆角**：framework-res 与 SystemUI 的资源表里
     * `dimen/notification_corner_radius` 命中数都是 0（A11 的行背景还是 9-patch，
     * 圆角是 A12 才引进的），卡内封面的 right_icon 也没有任何裁剪属性（LOG-1103 §12-2）。
     * 以前这里返回 2dp，那是 QS 播放器 Theme.DeviceDefault.dialogCornerRadius
     * （Y7s fw_res 0x010500a6=2.000000dp）串进来的；宿主值仍打进日志做对照，
     * 断言绝不能读它——照宿主取值等于把 A11 卡"顺手美化"掉。
     */
    private static int a11ArtRadiusPx(Context ctx) {
        return dp(ctx, A11NotifSpec.ART_RADIUS_DP);
    }

    /**
     * 见证日志用的封面尺寸与取色网格。网格必须与 A11 一致（先缩整图再换算 region），
     * 否则量化直方图的像素基数就不同，胜出的 swatch 可能整个换人。
     * region 定义复用 A11Palette.collapsedRegions，避免日志和实际取色分叉。
     */
    private static String artSize(Bitmap art) {
        return art == null ? "0x0" : art.getWidth() + "x" + art.getHeight();
    }

    private static int artGrid(Bitmap art, int regionIndex) {
        if (art == null) {
            return 0;
        }
        return A11Palette.gridPixels(art.getWidth(), art.getHeight(), regionIndex);
    }

    /** 宿主主题自己的 dialogCornerRadius，仅用于日志对照 */
    private static int hostDialogCornerRadiusPx(Context ctx) {
        try {
            android.util.TypedValue tv = new android.util.TypedValue();
            if (ctx.getTheme() != null
                    && ctx.getTheme().resolveAttribute(android.R.attr.dialogCornerRadius, tv,
                            true)
                    && tv.type == android.util.TypedValue.TYPE_DIMENSION) {
                return Math.round(
                        tv.getDimension(ctx.getResources().getDisplayMetrics()));
            }
        } catch (Throwable ignored) {
        }
        return -1;
    }

    private static boolean isNight(Context ctx) {
        // 通知视图的 context 是发起 app 的 createPackageContext（OS4 上 bbkmusic 带
        // 浅色 uiMode），而卡片贴在 SystemUI 的深色面板里——明暗必须问面板（SystemUI
        // 上下文），否则 os5/material 出"暖黑底 + 深棕字"（真机 01:38 实测 title 不可见）。
        Context sysui = sysuiContext(ctx);
        Configuration cfg = (sysui != null ? sysui : ctx).getResources().getConfiguration();
        return (cfg.uiMode & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
    }

    static final class MediaCard {
        final View root;
        /** a11 时是 FrameLayout（A11 的根），其它样式是竖向 LinearLayout（LOG-1103 §20-3） */
        final ViewGroup card;
        /** 原生封面视图；OS4 上建卡那一拍可能还没内容，rediscoverArtIfNeeded 会重指 */
        ImageView art;
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
        /** A11 收起态=着色行（36dp right_icon、Palette 明暗可翻转）；展开态=深色原生卡 */
        boolean expanded = true;
        final Handler handler = new Handler(Looper.getMainLooper());
        MediaController controller;
        boolean dragging;
        boolean tickerRunning;
        boolean canSeek;
        boolean lastNoDur;
        /**
         * 取色去重键。必须带上 expanded 与 night：
         * a11 两链取色本就不同，样式也可能在卡片存活期间被切换。
         */
        String lastArtKey = "";
        String lastTrackKey = "";
        int lastDrawableHash;
        /** a11 卡片的实际底色，syncArt 用它跑 A11 的"浮雕+渐变"封面后处理 */
        int a11CardBg;
        /** 上一次做浮雕用的底色。底色换人时右边的淡出就接不上卡片，必须重做（见 syncArt）。 */
        int a11ReliefBg = Integer.MIN_VALUE;
        /** 唯一的原始封面（未经 ImageGradientColorizer 处理的那份），只做一次方形化取图 */
        Bitmap a11RawArt;
        /** 最近一次 artSource() 实际取到的那一份来自哪里（largeIcon/largeIconDrawn/metadata/none） */
        String artFrom = "none";
        /**
         * a11 的竖向内容列（对应 A11 的 notification_media_content）。
         * 封面正方形的边长取**它**的测量高度，不是整张 card 的高 —— A11 的封面
         * 与内容列同顶同高，card 根是 FrameLayout（LOG-1103 §2/§15-9）。
         */
        ViewGroup a11Column;
        /** a11 卡顶那一行的 app 名（§20-1：12sp、副文同色），随取色链一起换色 */
        TextView a11HeaderName;
        /** a11 的进度条容器（条 + 下方两枚时间），显隐按 A11 的三判据切（§15-7） */
        ViewGroup a11SeekWrap;
        /**
         * 收起态（media_q 模板）的横向 main_column；展开态是 null。
         * "这张卡是不是紧凑结构"就以它为准，seek 的判据也读它 —— A11 收起模板里那颗
         * 进度条 ViewStub 的根不是 MediaNotificationView，从来没人 inflate 它。
         */
        LinearLayout a11MainRow;
        /** 动作带本体：见证按它**实际的父级**报 btnPos，不写死自报字符串（§4.3 那条坑） */
        LinearLayout a11Controls;
        long lastDurationMs;
        /** 条是否该显示（含「有时长」）。reclaimBar 必须尊重它，否则会与系统可见性写入互打。 */
        boolean barShouldShow = true;
        /**
         * 建卡那一拍的规格见证原文。logd 有 **每进程 250 行/秒** 的限流，SystemUI 启动/转屏
         * 那一秒 vivo 自己就刷几百行，我们夹在中间的 restyling + a11 card spec 会被整段丢掉
         * （11-04 早上真机复现：media bind 与 preview observer registered 都在，中间两行没了）。
         * 所以这条见证按低频（>4s）在几何刷新里补发一次，门禁不再赌"恰好没被限流"。
         */
        String a11SpecWitness;
        long a11SpecWitnessAt;

        /**
         * A11 进度条的显隐判据，逐条来自 `NotificationMediaTemplateViewWrapper.resolveViews`
         * （LOG-1103 §15-7）：没有会话 / 时长&lt;=0 / **收起态**（
         * `compact_media_notification_seekbar_enabled` 默认 false）⇒ 整个 seek 容器 GONE。
         * 注意"不可拖"不是藏条，而是滑块透明 + setEnabled(false)，那条走 seek 见证。
         */
        void applyA11SeekVisibility() {
            if (a11SeekWrap == null) {
                return;
            }
            // 行与条的显隐分开判（11-05，切歌闪条的治本）：
            // 原来判据含「时长>0」，切歌空窗期时长未知 ⇒ 整行 GONE ⇒ 行按零宽重测 ⇒
            // 回来时 weight=1 的条拿不到剩余宽度、两枚时间贴到左边（用户看到的现象）。
            // A11 原生只在 onContentUpdated 里判可见性、metadata 为 null 时沿用上一首，
            // 所以它从不参与每拍刷新。这里：行只看「有会话 && 展开」；
            // 条没有时长时只置 INVISIBLE —— 继续占位（weight 不失效），
            // 于是两枚时间的位置与行宽永远不变，切歌那一刻什么都不动。
            // 紧凑结构（收起态 media_q）永远不出条：原生那颗进度条在收起模板里只是
            // 一个从没被 inflate 的 ViewStub（根是裸 FrameLayout，不是 MediaNotificationView）
            boolean sessionOk = controller != null && expanded && a11MainRow == null;
            int wrapWant = sessionOk ? View.VISIBLE : View.GONE;
            barShouldShow = sessionOk && lastDurationMs > 0;
            if (bar != null) {
                int barWant = barShouldShow ? View.VISIBLE : View.INVISIBLE;
                if (bar.getVisibility() != barWant) {
                    bar.setVisibility(barWant);
                    Log.i(TAG, "a11 seek bar vis=" + barWant + " dur=" + lastDurationMs
                            + " sessionOk=" + (sessionOk ? 1 : 0));
                }
            }
            int want = wrapWant;
            if (a11SeekWrap.getVisibility() == want) {
                return;
            }
            a11SeekWrap.setVisibility(want);
            Log.i(TAG, "a11 seek vis=" + want + " expanded=" + (expanded ? 1 : 0)
                    + " session=" + (controller != null ? 1 : 0)
                    + " dur=" + lastDurationMs + " bar=" + (barShouldShow ? 1 : 0));
            // 容器进出会改变内容列高度 ⇒ 封面正方形要跟着重量一次
            card.post(() -> {
                try {
                    applyA11ArtGeometry();
                } catch (Throwable ignored) {
                }
            });
        }
        View rowBgView;
        Drawable curBg;

        final Runnable ticker = new Runnable() {
            @Override
            public void run() {
                // A11 这个定时器只管时间/进度（`PROGRESS_UPDATE_INTERVAL_MS` = 1000ms）。
                // 以前这里是 300ms 且顺带 syncArt()+refreshColors()，三个后果：
                // ①窗口永远不空闲 ⇒ `uiautomator dump` 报 "could not get idle state"，取证整轮作废；
                // ②切歌时反复拿"会话里那份还没换过来的 metadata"重取色，卡片底色就停在上一首；
                // ③每秒三次无谓重画。封面与取色改回**事件驱动**（bind / onMetadataChanged）。
                updateProgress();
                watchArtArrival();
                if (tickerRunning) {
                    handler.postDelayed(this, A11NotifSpec.PROGRESS_UPDATE_INTERVAL_MS);
                }
            }
        };

        final MediaController.Callback callback = new MediaController.Callback() {
            @Override
            public void onPlaybackStateChanged(PlaybackState state) {
                updateSeekable();
                // 暂停就把 1s 定时器摘掉，恢复播放再挂上（观感上没差别，窗口却终于能空闲）
                tickIfPlaying();
                if (pendingSeekMs >= 0 && state != null
                        && Math.abs(state.getPosition() - pendingSeekMs) < 1500) {
                    pendingSeekMs = -1;
                }
                updateProgress();
            }

            @Override
            public void onMetadataChanged(MediaMetadata metadata) {
                // 与 watchArtArrival 同一理由：先按新 metadata 取色，再画封面（旧底色画一张
                // 会逼出第二次淡入，过渡被重启就成了硬跳）
                refreshColors(true);
                syncArt();
                // 把回调自带的那份交给 updateProgress（A11 原生就是这么做的）：
                // 这里再轮询一次 controller.getMetadata() 会在切歌空窗期拿到 null，
                // 于是时长被当成 0、整条 seek 容器藏掉、两枚时间挤到左边。
                updateProgress(metadata);
            }

            @Override
            public void onSessionDestroyed() {
                release();
            }
        };

        MediaCard(View root, ViewGroup card, ImageView art, ImageView artView, SeekBar bar,
                  SeekDrawable seekDrawable, TextView elapsed, TextView total, TextView title,
                  TextView artist, ImageButton playBtn, ImageView sourceView, String style,
                  boolean expanded, ViewGroup origActions, List<ImageButton> orderedButtons) {
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
            this.expanded = expanded;
            this.origActions = origActions;
            this.orderedButtons = orderedButtons;
        }

        /** 建卡时按钮原本所在的容器：样式重建要把按钮"还"回去，重扫才找得到 */
        final ViewGroup origActions;
        /** 建卡时按通知顺序拿到的按钮（reparent 会打乱容器，重建还原靠它） */
        final List<ImageButton> orderedButtons;

        /**
         * 拆卡：样式切换（或孤儿清理）后要在同一个 root 上重建。
         * 把 reparent 走的 title/artist/按钮归还回模板原位（title/artist 置 GONE，
         * 下一次 build 会重新搬并置回可见），移除我们的卡视图，释放定时器与回调。
         * 返回 root 是否还被挂在窗口上（false 时调用方不必再重建）。
         */
        boolean destroy() {
            release();
            try {
                if (origActions != null) {
                    for (ImageButton b : orderedButtons) {
                        reparent(b);
                        origActions.addView(b);
                    }
                }
                restoreBack(title);
                restoreBack(artist);
                ViewParent cp = card.getParent();
                if (cp instanceof ViewGroup) {
                    ((ViewGroup) cp).removeView(card);
                }
            } catch (Throwable t) {
                Log.w(TAG, "media card destroy failed", t);
            }
            return root.isAttachedToWindow();
        }

        private static void restoreBack(TextView v) {
            if (v == null) {
                return;
            }
            reparent(v);
            v.setVisibility(View.GONE);
        }

        /** 状态变化只影响 A11 的几何与取色链，其它样式行为保持不变 */
        /**
         * A11 **通知栏**媒体卡的封面几何（LOG-1103 §2 / §12-2/3）：
         * 封面是正方形，边长 = 内容块（notification_media_content）的测量高度；
         * scaleType=CENTER_INSIDE + adjustViewBounds，**没有圆角、没有底板**
         * （A11 的 right_icon 在布局里没有任何裁剪/圆角属性，卡片圆角是 A12 才引入的）。
         * 排版也只有一套：MediaStyle 模板的 line1=14sp sans-serif-medium、副文=12**dp**（不是 sp）。
         * 原来"展开 52dp+2dp 圆角+底板 / 收起 36dp"和"展开 16/14/14sp"两套，都是 QS 播放器的
         * media_view.xml 常量串进来的（§10）。
         */
        void applyA11ArtGeometry() {
            try {
                Context ctx = card.getContext();
                // 先按 A11 的判据把进度条容器摆好，再量内容列高度（顺序反了封面会大一档，§15-7）
                applyA11SeekVisibility();
                // 封面正方形的边长 = 内容列（A11 的 notification_media_content）的测量高度（§2）
                int contentH = a11Column != null ? a11Column.getMeasuredHeight()
                        : card.getMeasuredHeight() - card.getPaddingTop() - card.getPaddingBottom();
                int size = A11NotifSpec.artSidePx(contentH);
                android.view.ViewGroup.LayoutParams lp = artView.getLayoutParams();
                if (lp != null) {
                    // 量不到就先保留现有尺寸，布局完成后再补一次（见下方的 post）
                    if (size > 0) {
                        lp.width = size;
                        lp.height = size;
                        artView.setLayoutParams(lp);
                    } else {
                        size = lp.height;
                    }
                }
                if (a11MainRow != null && size > 0
                        && a11MainRow.getLayoutParams()
                        instanceof android.view.ViewGroup.MarginLayoutParams) {
                    // 收起态给封面让出来的右边距：和 A11 的 onMeasure 一样**按实测封面边长**重算
                    // （artSide + notification_content_margin_end=16dp，A11-MediaNotificationView.java:74-80），
                    // 不是一次性按常量钉死；否则字号一大就把按钮压在封面下面。
                    android.view.ViewGroup.MarginLayoutParams mlp =
                            (android.view.ViewGroup.MarginLayoutParams) a11MainRow.getLayoutParams();
                    int wantEnd = A11NotifSpec.mainColumnMarginEndPx(size,
                            dp(ctx, A11NotifSpec.CONTENT_MARGIN_END_DP));
                    if (mlp.getMarginEnd() != wantEnd) {
                        mlp.setMarginEnd(wantEnd);
                        a11MainRow.setLayoutParams(mlp);
                    }
                }
                // §15-4：两份模板的 right_icon 都是 scaleType=6=**centerCrop**（枚举表核对过，
                // 不是 centerInside）；A11 的封面没有圆角、没有底板
                artView.setScaleType(ImageView.ScaleType.CENTER_CROP);
                artView.setBackground(null);
                artView.setClipToOutline(false);
                title.setTypeface(a11MediumBoldTitle());
                title.setTextSize(TypedValue.COMPLEX_UNIT_SP, A11NotifSpec.TITLE_SP);
                // 设备（vigour）副文 = 12**sp**；AOSP 那一支才是 14dp（§15-5/§20-1）
                artist.setTextSize(TypedValue.COMPLEX_UNIT_SP, A11NotifSpec.ARTIST_SP);
                // 两枚时间按 QS 播放器 = 14.0**sp**（跟 fontScale；通知卡那一版才是 14dp）
                elapsed.setTextSize(TypedValue.COMPLEX_UNIT_SP, A11NotifSpec.PLAYER_SEEK_TIME_SP);
                total.setTextSize(TypedValue.COMPLEX_UNIT_SP, A11NotifSpec.PLAYER_SEEK_TIME_SP);
                Log.i(TAG, "a11 art state=" + (expanded ? "expanded" : "collapsed")
                        + " size=" + size + " contentH=" + contentH
                        + " scaleType=" + artView.getScaleType()
                        + " clip=" + artView.getClipToOutline()
                        + " titlePx=" + Math.round(title.getTextSize())
                        + " subtextPx=" + Math.round(artist.getTextSize())
                        + " timePx=" + Math.round(elapsed.getTextSize())
                        // titleMedium 是"状态没清"的见证：两态都必须带 medium 族
                        + " titleMedium=" + (title.getTypeface() == a11MediumBoldTitle() ? 1 : 0)
                        // 收起态整条 seek 容器必须 GONE（A11 默认没有紧凑进度条，§15-7）
                        + " seekVis=" + (a11SeekWrap == null ? -1 : a11SeekWrap.getVisibility())
                        // 触控带读回视图真实 padding：16dp=播放器那一版；24dp=又拿通知卡那一版喂进来了
                        + " padTbPx=" + bar.getPaddingTop()
                        // 进度条该不该在，判据是"展开 + 有时长 + 有会话"：只报 dur 的话，
                        // 会话被拆掉那一帧会被门禁当成"该露却没露"（其实是该藏）
                        + " seekDur=" + lastDurationMs
                        + " seekSession=" + (controller != null ? 1 : 0)
                        // 收起态排布的三项证据，全部**回读视图实际状态**：
                        // compact 以横向 main_column 是否存在为准，btnPos 以动作带真正的父级为准，
                        // mainEndPx/btnMarginPx 读真正的 MarginLayoutParams —— 避免"自报常量"
                        + " compact=" + (a11MainRow != null ? 1 : 0)
                        + " btnPos=" + (a11Controls != null && a11Controls.getParent() == a11MainRow
                                ? "beside" : "below")
                        + " mainEndPx=" + endMarginOf(a11MainRow)
                        + " btnMarginPx=" + startEndMarginOf(a11Controls));
                if (a11SpecWitness != null) {
                    long now = android.os.SystemClock.uptimeMillis();
                    if (now - a11SpecWitnessAt > 4000) {
                        a11SpecWitnessAt = now;
                        Log.i(TAG, a11SpecWitness);
                    }
                }
                if (contentH <= 0) {
                    // 卡片还没量过：布局完成后再走一次，否则封面尺寸会停在旧值。
                    // post 里的异常不在 Xposed 的保护范围内，必须自己兜住（会带走主线程）。
                    card.post(() -> {
                        try {
                            applyA11ArtGeometry();
                        } catch (Throwable ignored) {
                        }
                    });
                }
            } catch (Throwable t) {
                Log.w(TAG, "a11 art geometry failed", t);
            }
        }

        /** 见证用：读回真实的 end margin；没有这一层（展开态）就是 -1。 */
        private static int endMarginOf(View v) {
            if (v == null || !(v.getLayoutParams()
                    instanceof android.view.ViewGroup.MarginLayoutParams)) {
                return -1;
            }
            return ((android.view.ViewGroup.MarginLayoutParams) v.getLayoutParams()).getMarginEnd();
        }

        /** 见证用：动作带真实的 start/end 外边距（收起 0/0、展开 14dp/20dp），比回读常量硬。 */
        private static String startEndMarginOf(View v) {
            if (v == null || !(v.getLayoutParams()
                    instanceof android.view.ViewGroup.MarginLayoutParams)) {
                return "-";
            }
            android.view.ViewGroup.MarginLayoutParams lp =
                    (android.view.ViewGroup.MarginLayoutParams) v.getLayoutParams();
            return lp.getMarginStart() + "/" + lp.getMarginEnd();
        }

        void setState(Boolean next) {
            if (next == null || !"a11".equals(style) || next.booleanValue() == expanded) {
                return;
            }
            expanded = next.booleanValue();
            applyA11ArtGeometry();
            // a11 的取色链两态不同（收起=着色行 + WCAG 校正，展开=恒深卡 + 白色阶），
            // 只换几何不换色 = 收起的彩色贴在展开的深色卡上
            applyPalette(false, null);
        }

        static MediaCard build(Context ctx, View root, ViewGroup actions, TextView title,
                               TextView artist, ImageView art, ImageView sourceIcon, int headerId,
                               String style, boolean expanded) {
            ensureWidgetService(ctx);
            boolean m3 = "material".equals(style);
            boolean a11 = "a11".equals(style);
            // 收起态是**另一份布局**（media_q，main_column 横向、动作带在文字右侧），
            // 不是"展开态去掉进度条"。证据见 A11NotifSpec 的"收起态模板"一节。
            boolean compact = a11 && !expanded;
            boolean night = isNight(ctx);
            int primary = night ? 0xFFFFFFFF : (m3 ? 0xFF2A1A15 : 0xFF241A16);
            int secondary = night ? 0xB3FFFFFF : (m3 ? 0x8C5A4038 : 0x8C322319);
            int accent = m3 ? 0xFFA93A27 : (night ? 0xFFF07A64 : 0xFFE0503A);

            root.setBackground(null);
            root.setPadding(0, 0, 0, 0);
            ((ViewGroup) root).setClipChildren(false);
            ((ViewGroup) root).setClipToPadding(false);
            TextView appName = null;
            View header = headerId == 0 ? null : root.findViewById(headerId);
            if (a11) {
                // A11 的卡顶有"app 名 + 时间"那一行（§20-1）：把 app_name_text 摘出来复用。
                // OS4 展开态模板整块无 id（headerId=0）：直接全树找 app_name_text。
                // 此刻必须**先把无 id 的原生头部容器藏掉**——buildA11HeaderRow 会把 app 名
                // 搬进我们的卡，之后全树唯一的那颗 app_name_text 就在我们的卡里，
                // 再按它找"原生头部"只会爬到自己（真机 01:08 实测：一爬正好是本卡这个
                // root 直接子级 → 整卡被 GONE，行里只剩空壳）。
                int anId = id(ctx, "app_name_text");
                View v = anId == 0 ? null : root.findViewById(anId);
                if (v instanceof TextView) {
                    appName = (TextView) v;
                    View p = (View) v.getParent();
                    while (p != null && p != root
                            && p.getParent() instanceof View && p.getParent() != root) {
                        p = (View) p.getParent();
                    }
                    if (p != null && p.getParent() == root) {
                        p.setVisibility(View.GONE);
                        Log.i(TAG, "a11 native header hidden cls=" + p.getClass().getName());
                    }
                }
                Log.i(TAG, "a11 header appName id=" + anId
                        + " found=" + (appName != null)
                        + " text=" + (appName == null ? "-" : appName.getText()));
            }
            if (header != null) {
                header.setVisibility(View.GONE);
            }

            // a11 走 A11 模板的骨架（LOG-1103 §20-3）：根 = FrameLayout，封面 TOP|END 正方形压在右上，
            // 竖向内容列自上而下 = 头部行 / 文字列 / 动作带 / 进度条 + 时间行。
            // 其它样式仍是"卡片即竖向三行"。column = "行往哪儿放"：非 a11 时就是 card 本身。
            ViewGroup card;
            if (a11) {
                card = new FrameLayout(ctx);
            } else {
                LinearLayout vertical = new LinearLayout(ctx);
                vertical.setOrientation(LinearLayout.VERTICAL);
                card = vertical;
            }
            ViewGroup column;
            if (a11) {
                column = new LinearLayout(ctx);
                ((LinearLayout) column).setOrientation(LinearLayout.VERTICAL);
            } else {
                column = card;
            }
            card.setPadding(a11 ? 0 : dp(ctx, 16), a11 ? 0 : dp(ctx, 15),
                    a11 ? 0 : dp(ctx, 16), a11 ? 0 : dp(ctx, 13));
            GradientDrawable bg = new GradientDrawable(
                    GradientDrawable.Orientation.TOP_BOTTOM,
                    m3
                            ? (night ? new int[]{0xFF2A211C, 0xFF211915}
                                     : new int[]{0xFFF7DED4, 0xFFFBEADE})
                            : (night ? new int[]{0xFF2A211C, 0xFF211915}
                                     : new int[]{0xFFFDF6EC, 0xFFF8EDE1}));
            bg.setCornerRadius(a11 ? a11ArtRadiusPx(ctx) : dp(ctx, m3 ? 28 : 24));
            card.setBackground(bg);

            // 封面。A11 的 right_icon：scaleType=6=**centerCrop**（§15-4，枚举表核对过），
            // 没有圆角、没有底板、没有阴影；位置在卡片的 TOP|END（§15-3），见下面的装配分支。
            ImageView artView = new ImageView(ctx);
            artView.setScaleType(ImageView.ScaleType.CENTER_CROP);
            if (a11) {
                artView.setBackground(null);
                artView.setClipToOutline(false);
            } else {
                GradientDrawable artBg = new GradientDrawable();
                artBg.setColor(night ? 0x33FFFFFF : 0x14000000);
                artBg.setCornerRadius(dp(ctx, m3 ? 16 : 8));
                artView.setBackground(artBg);
                artView.setClipToOutline(true);
                artView.setElevation(dp(ctx, 2));
            }
            if (art != null) {
                if (art.getDrawable() != null) {
                    artView.setImageDrawable(art.getDrawable());
                }
                art.setVisibility(View.GONE);
                // 换歌那一拍 RemoteViews 会把原生封面设回可见 ⇒ 小封面闪现/常驻
                //（用户报 os5"右边小封面闪现"；a11 有守卫、os5/material 以前没有）。
                // 守卫与样式无关，全部挂上。
                guardArtHidden(art);
            }
            LinearLayout texts = new LinearLayout(ctx);
            texts.setOrientation(LinearLayout.VERTICAL);
            // 标题/歌手改用**自己的 TextView**（复制文案/字号/颜色），原生那颗原地 GONE。
            // 以前是 reparent 原视图——OS4 真机有外部写手（模板 wrapper 一族）会对
            // android:id/title 反复写 INVISIBLE/alpha=0，搬进我们卡也照写不误，
            // 自建的视图没人动。重绑时 RemoteViews 更新的是留在模板里的原生视图，
            // 我们这份文字由会话元数据同步（syncTextFromMetadata）。
            TextView titleView = new TextView(ctx);
            titleView.setText(str(title.getText()));
            titleView.setTextSize(TypedValue.COMPLEX_UNIT_SP,
                    a11 ? A11NotifSpec.TITLE_SP : (m3 ? 16 : 17));
            if (a11) {
                // 设备真值（§20-1）：父样式给 sans-serif-medium 族，布局又叠了 textStyle=BOLD
                titleView.setTypeface(a11MediumBoldTitle());
            } else {
                titleView.setTypeface(null, android.graphics.Typeface.BOLD);
            }
            titleView.setTextColor(primary);
            titleView.setSingleLine(true);
            titleView.setEllipsize(TextUtils.TruncateAt.END);
            titleView.setPadding(0, 0, 0, 0);
            title.setVisibility(View.GONE);
            title = titleView;
            texts.addView(title, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            if (artist != null) {
                TextView artistView = new TextView(ctx);
                artistView.setText(str(artist.getText()));
                // 副文：设备 vigour_notification_text_size = 12**sp**（AOSP 那一支才是 14dp）
                artistView.setTextSize(TypedValue.COMPLEX_UNIT_SP,
                        a11 ? A11NotifSpec.ARTIST_SP : 12.5f);
                artistView.setTextColor(secondary);
                artistView.setSingleLine(true);
                artistView.setEllipsize(TextUtils.TruncateAt.END);
                artistView.setPadding(0, 0, 0, 0);
                artist.setVisibility(View.GONE);
                artist = artistView;
                LinearLayout.LayoutParams al = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                al.topMargin = a11 ? dp(ctx, A11NotifSpec.ARTIST_MARGIN_TOP_DP) : dp(ctx, 4);
                texts.addView(artist, al);
            }
            // 收起态那一行横向 main_column（media_q 模板），展开态为 null（文字直接进竖向列）
            LinearLayout a11MainRow = compact ? new LinearLayout(ctx) : null;
            if (compact) {
                a11MainRow.setOrientation(LinearLayout.HORIZONTAL);
                a11MainRow.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
                a11MainRow.setMinimumHeight(dp(ctx, A11NotifSpec.MIN_CONTENT_HEIGHT_DP));
                // 文字列：weight=1、填满列高、下方内缩 14dp（模板里 layout_gravity=0x70=FILL_VERTICAL）
                texts.setPadding(0, 0, 0, dp(ctx, A11NotifSpec.DEVICE_TEXT_COLUMN_PADDING_BOTTOM_DP));
                LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(
                        0, ViewGroup.LayoutParams.MATCH_PARENT, 1f);
                tlp.gravity = Gravity.FILL_VERTICAL;
                a11MainRow.addView(texts, tlp);
                LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                rlp.topMargin = dp(ctx, A11NotifSpec.DEVICE_COLUMN_MARGIN_TOP_DP);
                rlp.leftMargin = dp(ctx, A11NotifSpec.DEVICE_COLUMN_MARGIN_SIDE_DP);
                // 给封面让出来的右边距：走 MediaNotificationView 那条运行时式子
                // （artSide + notification_content_margin_end=16dp），artSide = 内容块高 = 42+61dp
                rlp.rightMargin = A11NotifSpec.mainColumnMarginEndPx(
                        dp(ctx, A11NotifSpec.DEVICE_COLUMN_MARGIN_TOP_DP
                                + A11NotifSpec.MIN_CONTENT_HEIGHT_DP),
                        dp(ctx, A11NotifSpec.CONTENT_MARGIN_END_DP));
                column.addView(a11MainRow, rlp);
            } else if (a11) {
                // 文字列 = notification_main_column：marginTop 42dp、左右 20dp、
                // 最小高 61dp（由 setContentMinHeight 写进来，§15-10/§20-1）
                LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                tlp.topMargin = dp(ctx, A11NotifSpec.DEVICE_COLUMN_MARGIN_TOP_DP);
                tlp.leftMargin = dp(ctx, A11NotifSpec.DEVICE_COLUMN_MARGIN_SIDE_DP);
                tlp.rightMargin = dp(ctx, A11NotifSpec.DEVICE_COLUMN_MARGIN_SIDE_DP);
                texts.setMinimumHeight(dp(ctx, A11NotifSpec.MIN_CONTENT_HEIGHT_DP));
                column.addView(texts, tlp);
            } else {
                LinearLayout top = new LinearLayout(ctx);
                top.setOrientation(LinearLayout.HORIZONTAL);
                top.setGravity(Gravity.CENTER_VERTICAL);
                LinearLayout.LayoutParams textsLp = new LinearLayout.LayoutParams(
                        0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
                textsLp.leftMargin = dp(ctx, 12);
                texts.setLayoutParams(textsLp);
                top.addView(artView, new LinearLayout.LayoutParams(
                        dp(ctx, m3 ? 64 : 60), dp(ctx, m3 ? 64 : 60)));
                top.addView(texts);
                column.addView(top, new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            }

            // 进度条 + 两枚时间。A11 的排布不是"时间夹在条两侧"，
            // 而是条独占一行（满宽，会穿过封面区，与原生一致），时间在条**下方**左右贴边（§15-6/§20-2）。
            // 进度条这一排按**用户指定的 A11 QS 播放器**画（media_view.xml:23-40/116-128）：
            // 两枚时间在条左右两端同一行，条吃剩余宽、paddingTB 16dp、maxHeight 3dp、thumb 常驻。
            // （通知卡那一版是"条满宽 + 时间在条下方左右贴边 + 不可拖时 thumb 透明"，
            //  常数还留在 A11NotifSpec 里，但产品不用那一版。）
            TextView elapsed = new TextView(ctx);
            if (a11) {
                // QS 这里是 14.0**sp**（跟 fontScale）
                elapsed.setTextSize(TypedValue.COMPLEX_UNIT_SP, A11NotifSpec.PLAYER_SEEK_TIME_SP);
            } else {
                elapsed.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10.5f);
            }
            elapsed.setTextColor(secondary);
            elapsed.setText("0:00");
            elapsed.setGravity(Gravity.END);
            elapsed.setMinWidth(dp(ctx, 28));
            elapsed.setFontFeatureSettings("tnum");
            LinearLayout seekRow = new LinearLayout(ctx);
            seekRow.setOrientation(LinearLayout.HORIZONTAL);
            seekRow.setGravity(Gravity.CENTER_VERTICAL);
            // a11 时这一排挂进内容列的动作带之后（见下面 column.addView(seekWrap)），
            // 并且要能被"收起态/无时长就没有条"这条 A11 判据整体摘掉（§15-7）
            LinearLayout seekWrap = a11 ? seekRow : null;
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
            // 不再借系统自己的 android.R.id.progress（11-05）：借了 id 就等于把可见性
            // 交给 NotificationHeaderViewWrapper —— 它每趟 selectLayout 会按自己的状态把
            // 「它的」条置 4/8，GONE 还会连带 weight 一起失效。手势改由 DragSeekBar 自己
            // 在 ACTION_DOWN 里 requestDisallowInterceptTouchEvent 拿住，不再依赖 id 白名单。
            bar.setId(ID_A11_SEEK);
            bar.setMax(1);
            bar.setProgress(0);
            bar.setSplitTrack(false);
            if (a11) {
                // QS：paddingTop/Bottom 各 16dp（触控带 32dp + 轨道），不靠负 margin 收
                bar.setPadding(dp(ctx, 4), dp(ctx, A11NotifSpec.PLAYER_SEEK_PADDING_TB_DP),
                        dp(ctx, 4), dp(ctx, A11NotifSpec.PLAYER_SEEK_PADDING_TB_DP));
                bar.setMinimumHeight(dp(ctx, 20));
            } else {
                bar.setPadding(dp(ctx, 6), dp(ctx, 6), dp(ctx, 6), dp(ctx, 6));
            }
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
            LinearLayout.LayoutParams bl;
            if (a11) {
                // 条在两枚时间中间吃剩余宽（QS 的 layout_width=0dp + weight）
                bl = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
                bl.leftMargin = dp(ctx, 4);
                bl.rightMargin = dp(ctx, 4);
            } else {
                bl = new LinearLayout.LayoutParams(0, dp(ctx, 24), 1f);
                bl.leftMargin = dp(ctx, 2);
                bl.rightMargin = dp(ctx, 2);
            }
            seekRow.addView(bar, bl);

            TextView total = new TextView(ctx);
            if (a11) {
                total.setTextSize(TypedValue.COMPLEX_UNIT_SP, A11NotifSpec.PLAYER_SEEK_TIME_SP);
            } else {
                total.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10.5f);
            }
            total.setTextColor(secondary);
            total.setText("0:00");
            total.setGravity(Gravity.START);
            total.setMinWidth(dp(ctx, 28));
            total.setFontFeatureSettings("tnum");
            seekRow.addView(total, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            if (!a11) {
                LinearLayout.LayoutParams seekRowLp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                seekRowLp.topMargin = dp(ctx, 11);
                column.addView(seekRow, seekRowLp);
            }

            // 现场几何取证：条在部分机器上塌成 0 宽（两侧时间贴在一起），
            // 绑定阶段 getLocationOnScreen 还是 0，所以等布局完成后再量一次
            final TextView titleW = title;
            final TextView artistW = artist;
            bar.post(() -> {
                try {
                    // OS4 上 title/artist 曾被外部写手置 INVISIBLE/alpha=0（改自建
                    // TextView 前的老账）；自建视图后这条见证核对 children/颜色/几何。
                    if (titleW.getVisibility() != View.VISIBLE) {
                        titleW.setVisibility(View.VISIBLE);
                        Log.i(TAG, "card title restored at layout");
                    }
                    if (artistW != null && artistW.getVisibility() != View.VISIBLE) {
                        artistW.setVisibility(View.VISIBLE);
                        Log.i(TAG, "card artist restored at layout");
                    }
                    Log.i(TAG, "card texts: children=" + texts.getChildCount()
                            + " titleVis=" + titleW.getVisibility()
                            + " titleText=" + str(titleW.getText())
                            + " titleWH=" + titleW.getWidth() + "x" + titleW.getHeight()
                            + " textsWH=" + texts.getWidth() + "x" + texts.getHeight()
                            + " color=" + Integer.toHexString(titleW.getCurrentTextColor())
                            + " alpha=" + titleW.getAlpha()
                            + " ty=" + titleW.getTranslationY()
                            + " xy=" + titleW.getX() + "," + titleW.getY()
                            + " scroll=" + titleW.getScrollX() + "," + titleW.getScrollY());
                    View row = bar.getParent() instanceof View ? (View) bar.getParent() : null;
                    Log.i(TAG, "card bar geom: bar=" + bar.getWidth() + 'x' + bar.getHeight()
                            + " row=" + (row == null ? "null" : row.getWidth() + 'x' + row.getHeight())
                            + " rowW=" + (row == null ? -1 : row.getMeasuredWidth())
                            + " card=" + card.getWidth()
                            + " el=" + elapsed.getWidth() + " tot=" + total.getWidth()
                            + " weight=" + ((LinearLayout.LayoutParams) bar.getLayoutParams()).weight
                            + " count=" + (row instanceof ViewGroup
                                    ? ((ViewGroup) row).getChildCount() : -1));
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
            controls.setGravity(a11 ? Gravity.TOP : Gravity.CENTER_VERTICAL);
            if (a11) {
                // 两份 A11 模板都写了 layoutDirection=0，而 attr 枚举里 **0=ltr**（不是 inherit）
                controls.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
            }
            ImageView sourceView = new ImageView(ctx);
            sourceView.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
            if (!a11) {
                GradientDrawable ph = new GradientDrawable();
                ph.setShape(GradientDrawable.OVAL);
                ph.setColor(night ? 0x1FFFFFFF : 0x14000000);
                sourceView.setBackground(ph);
                sourceView.setClipToOutline(true);
            }
            if (sourceIcon != null && sourceIcon.getDrawable() != null) {
                sourceView.setImageDrawable(sourceIcon.getDrawable());
            }
            int sourceDp = a11 ? A11NotifSpec.HEADER_ICON_DP : 30;
            if (!a11) {
                // 非 a11：应用图标留在动作带左侧的权重槽里（原排版）
                FrameLayout sourceSlot = new FrameLayout(ctx);
                controls.addView(sourceSlot, new LinearLayout.LayoutParams(0, dp(ctx, 44), 1f));
                FrameLayout.LayoutParams flp = new FrameLayout.LayoutParams(
                        dp(ctx, sourceDp), dp(ctx, sourceDp), Gravity.START | Gravity.CENTER_VERTICAL);
                sourceSlot.addView(sourceView, flp);
            }

            ImageButton prev = null;
            ImageButton play = null;
            ImageButton next = null;
            ImageButton heart = null;
            List<ImageButton> others = new ArrayList<>();
            List<ImageButton> ordered = new ArrayList<>();
            int n = actions.getChildCount();
            for (int i = 0; i < n; i++) {
                View child = actions.getChildAt(i);
                if (!(child instanceof ImageButton)) {
                    continue;
                }
                ImageButton b = (ImageButton) child;
                // A11 的动作带按 notification 的 action 顺序原样排（不重排成 prev-play-next），
                // 所以先留一份快照：下面 reparent 会改动 actions 的子项
                ordered.add(b);
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

            // 动作带。设备真值（§20-1，vigour 那颗）：**36×40dp**、padding 上下 8dp / 左右 6dp、
            // 左右各 2dp 外边距、scaleType=centerInside(7)、背景 @android:color/transparent
            //（所以原生这一支没有水波纹层）。颜色 = 正文主色（§5b/§15-8：着色时
            // getActionColor(p) == getPrimaryTextColor(p)）⇒ 原生不存在"按钮压暗到看不清"。
            // AOSP 那一支才是 48dp 方形 + 8dp 内缩 + borderless（有 ripple），记在 A11NotifSpec 里备用。
            int ctrlTint = primary;
            if (compact) {
                for (ImageButton b : ordered) {
                    addA11Control(ctx, controls, b, ctrlTint);
                }
                // media_q 的模板里这颗**没有写任何左右外边距**（14/20 只属于展开那份 big_media_q），
                // 它是 main_column 的第二个子，贴顶排在文字列右边
                LinearLayout.LayoutParams alp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                alp.gravity = Gravity.TOP;
                alp.leftMargin = dp(ctx, A11NotifSpec.DEVICE_COMPACT_ACTIONS_MARGIN_DP);
                alp.rightMargin = dp(ctx, A11NotifSpec.DEVICE_COMPACT_ACTIONS_MARGIN_DP);
                a11MainRow.addView(controls, alp);
            } else if (a11) {
                for (ImageButton b : ordered) {
                    addA11Control(ctx, controls, b, ctrlTint);
                }
                LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                clp.leftMargin = dp(ctx, A11NotifSpec.DEVICE_ACTIONS_MARGIN_START_DP);
                clp.rightMargin = dp(ctx, A11NotifSpec.DEVICE_ACTIONS_MARGIN_END_DP);
                column.addView(controls, clp);
            } else {
                addControl(ctx, controls, prev, 40, ctrlTint);
                addControl(ctx, controls, play, 44, accent);
                addControl(ctx, controls, next, 40, ctrlTint);

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
                column.addView(controls, controlsLp);
            }

            if (a11) {
                // A11 竖向列的最后一件：进度条 + 时间行（动作带之后、列末尾，§15-9）。
                // 左右内缩必须与文字列同一档（20dp）：列本身没有 padding，谁忘加就贴着卡片边，
                // 于是"左边那枚时间溢出去"（11-04 真机实测 elapsed 落在 x=60，与行左边齐平）。
                LinearLayout.LayoutParams seekLp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                seekLp.leftMargin = dp(ctx, A11NotifSpec.DEVICE_COLUMN_MARGIN_SIDE_DP);
                seekLp.rightMargin = dp(ctx, A11NotifSpec.DEVICE_COLUMN_MARGIN_SIDE_DP);
                column.addView(seekWrap, seekLp);
                card.addView(column, new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
                // 头部行（44dp，图标 16dp + app 名 12sp）与封面（TOP|END 正方形）都挂在根 FrameLayout 上，
                // 这样"文字列 marginTop 42dp 是为了让开绝对定位的头部行"这条 A11 的排布逻辑才成立
                card.addView(buildA11HeaderRow(ctx, sourceView, appName, secondary),
                        new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                                dp(ctx, A11NotifSpec.HEADER_HEIGHT_DP),
                                Gravity.START | Gravity.TOP));
                int initSide = dp(ctx, A11NotifSpec.DEVICE_COLUMN_MARGIN_TOP_DP
                        + A11NotifSpec.MIN_CONTENT_HEIGHT_DP);
                card.addView(artView, new FrameLayout.LayoutParams(initSide, initSide,
                        Gravity.END | Gravity.TOP));
            }

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
                    total, title, artist, play, sourceView, style, expanded, actions, ordered);
            if ("a11".equals(style)) {
                mc.a11Column = column;
                mc.a11HeaderName = appName;
                mc.a11SeekWrap = seekWrap;
                mc.a11MainRow = a11MainRow;
                mc.a11Controls = controls;
                mc.applyA11ArtGeometry();
                // 规格见证：门禁据此断言尺寸（dp 换算后的 px），不依赖任何截图。
                // 数值全部来自 LOG-1103 §20-1 的**设备分支**（IS_TIER_VOS=false ⇒ vigour 模板）；
                // AOSP 那一支（48dp 方按钮 / 列边距 46-16 / minHeight 39）记在 A11NotifSpec 里备查。
                mc.a11SpecWitness = "a11 card spec artSide=contentHeight btnPad="
                        + dp(ctx, A11NotifSpec.DEVICE_BUTTON_PADDING_TB_DP)
                        + " btnEnd=" + dp(ctx, A11NotifSpec.DEVICE_BUTTON_MARGIN_DP)
                        + " btn=" + dp(ctx, A11NotifSpec.DEVICE_BUTTON_W_DP) + "x"
                        + dp(ctx, A11NotifSpec.DEVICE_BUTTON_H_DP)
                        + " src=" + dp(ctx, sourceDp)
                        + " titlePx=" + Math.round(title.getTextSize())
                        + " artistPx=" + Math.round(artist == null ? 0 : artist.getTextSize())
                        + " timePx=" + Math.round(elapsed.getTextSize())
                        + " trackDp=" + A11NotifSpec.SEEK_TRACK_DP
                        + " colTop=" + dp(ctx, A11NotifSpec.DEVICE_COLUMN_MARGIN_TOP_DP)
                        + " colSide=" + dp(ctx, A11NotifSpec.DEVICE_COLUMN_MARGIN_SIDE_DP)
                        + " colMinH=" + dp(ctx, A11NotifSpec.MIN_CONTENT_HEIGHT_DP)
                        + " headH=" + dp(ctx, A11NotifSpec.HEADER_HEIGHT_DP)
                        + " actMargin=" + dp(ctx, A11NotifSpec.DEVICE_ACTIONS_MARGIN_START_DP)
                        + "/" + dp(ctx, A11NotifSpec.DEVICE_ACTIONS_MARGIN_END_DP)
                        + " density=" + ctx.getResources().getDisplayMetrics().density
                        + " fontScale=" + ctx.getResources().getConfiguration().fontScale
                        + " radius=" + a11ArtRadiusPx(ctx)
                        + " hostCorner=" + hostDialogCornerRadiusPx(ctx);
                Log.i(TAG, mc.a11SpecWitness);
            }
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
                        // 暂停态已经没有秒级定时器了（tickIfPlaying 只在播放/拖动时挂着），
                        // 所以这一下必须自己刷一次，否则时间与条停在拖动手势的残值上
                        mc.updateProgress();
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
            addControl(ctx, parent, b, sizeDp, tint, 0, 0);
        }

        /** padDp/endDp 给 0 表示沿用原来的"16% 内缩"；A11 要显式给 8dp/2dp（LOG-1103 §5a）。 */
        private static void addControl(Context ctx, LinearLayout parent, ImageButton b,
                                       int sizeDp, int tint, int padDp, int endDp) {
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
            int pad = padDp > 0 ? dp(ctx, padDp) : Math.round(dp(ctx, sizeDp) * 0.16f);
            b.setPadding(pad, pad, pad, pad);
            if (tint != 0) {
                b.setColorFilter(tint);
            }
            LinearLayout.LayoutParams blp =
                    new LinearLayout.LayoutParams(0, dp(ctx, sizeDp), 1f);
            if (endDp > 0) {
                blp.rightMargin = dp(ctx, endDp);
            }
            parent.addView(b, blp);
        }

        /**
         * 设备分支的动作按钮（LOG-1103 §20-1，`vigour_notification_material_media_action_q`）：
         * 36×40dp、padding 上下 8dp / 左右 6dp、左右各 2dp 外边距、scaleType=centerInside(7)、
         * 背景 `@android:color/transparent` ⇒ 这一支原生没有水波纹层。
         * 图标色 = 正文主色：`bindMediaActionButton` 在着色时取 `getPrimaryTextColor(p)`（§5b/§15-8），
         * 也就是与标题同一支颜色 —— "按钮看不清"在原生逻辑里不该发生。
         */
        private static void addA11Control(Context ctx, LinearLayout parent, ImageButton b, int tint) {
            if (b == null) {
                return;
            }
            reparent(b);
            b.setBackground(null);
            b.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
            int ptb = dp(ctx, A11NotifSpec.DEVICE_BUTTON_PADDING_TB_DP);
            int plr = dp(ctx, A11NotifSpec.DEVICE_BUTTON_PADDING_LR_DP);
            b.setPadding(plr, ptb, plr, ptb);
            if (tint != 0) {
                b.setColorFilter(tint);
            }
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    dp(ctx, A11NotifSpec.DEVICE_BUTTON_W_DP), dp(ctx, A11NotifSpec.DEVICE_BUTTON_H_DP));
            lp.leftMargin = dp(ctx, A11NotifSpec.DEVICE_BUTTON_MARGIN_DP);
            lp.rightMargin = dp(ctx, A11NotifSpec.DEVICE_BUTTON_MARGIN_DP);
            parent.addView(b, lp);
        }

        /**
         * A11 卡顶那一行（§20-1）：高 44dp、padding 上 16 / 下 12 / 左右 20，
         * 内容 = 16dp 应用小图标（marginEnd 8dp）+ app 名（12sp）。
         * 媒体通知 `showWhen=false`（§1 的 i 音乐真机样本），所以 A11 这一行也只有名字，没有时间。
         */
        private static LinearLayout buildA11HeaderRow(Context ctx, ImageView icon, TextView appName,
                                                      int nameColor) {
            LinearLayout row = new LinearLayout(ctx);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(ctx, A11NotifSpec.HEADER_PADDING_SIDE_DP),
                    dp(ctx, A11NotifSpec.HEADER_PADDING_TOP_DP),
                    dp(ctx, A11NotifSpec.HEADER_PADDING_SIDE_DP),
                    dp(ctx, A11NotifSpec.HEADER_PADDING_BOTTOM_DP));
            if (icon != null) {
                reparent(icon);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                        dp(ctx, A11NotifSpec.HEADER_ICON_DP), dp(ctx, A11NotifSpec.HEADER_ICON_DP));
                lp.rightMargin = dp(ctx, 8);
                row.addView(icon, lp);
            }
            if (appName != null) {
                reparent(appName);
                appName.setTextSize(TypedValue.COMPLEX_UNIT_SP, A11NotifSpec.HEADER_TEXT_SP);
                appName.setTextColor(nameColor);
                appName.setSingleLine(true);
                appName.setEllipsize(TextUtils.TruncateAt.END);
                row.addView(appName, new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            }
            return row;
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
            hideOldExpanded(ctx, root);
        }

        /**
         * OS4 展开态（framework MediaStyle 族）的原生件没有上面那些 id：
         * 原生进度行（SeekBar + 两枚时间）——不是我们的条就整行 GONE，否则它画在
         * 我们的卡上面（z 序：我们的卡在 root index 0，原生内容在后加入的在上面）。
         * 头部行不在这里处理：a11 建 build 时 app 名会被搬进我们的卡，那之后再按
         * app_name_text 找"原生头部"只会爬到自己（真机 01:08 实测整卡被藏），
         * 所以原生头部的隐藏在 build 摘 app 名之前做。
         */
        private static void hideOldExpanded(Context ctx, View root) {
            int mine = ID_A11_SEEK;
            java.util.Deque<View> stack = new java.util.ArrayDeque<>();
            stack.push(root);
            while (!stack.isEmpty()) {
                View v = stack.pop();
                if (v instanceof SeekBar && v.getId() != mine
                        && v.getParent() instanceof ViewGroup) {
                    ((ViewGroup) v.getParent()).setVisibility(View.GONE);
                    continue;
                }
                if (v instanceof ViewGroup) {
                    ViewGroup g = (ViewGroup) v;
                    for (int i = 0; i < g.getChildCount(); i++) {
                        stack.push(g.getChildAt(i));
                    }
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

        /** 已经挂过"原生封面自愈隐藏"守卫的那一颗 right_icon */
        private static final WeakHashMap<View, Boolean> ART_HIDE_GUARD = new WeakHashMap<>();

        /**
         * 换歌那一拍 RemoteViews 会把 `android:id/right_icon` 重新设回可见（A11 的封面位本来就是它），
         * 于是用户看到**右边闪一下小图标版的封面**。建卡时按一次 GONE 不够，必须挂在这颗视图自己的
         * 布局回调上自愈回来：只在"确实又露出来"时写一次，不做每帧轮询（高频写正是本项目抖动的老根）。
         */
        private static void guardArtHidden(final ImageView art) {
            if (art == null) {
                return;
            }
            synchronized (ART_HIDE_GUARD) {
                if (Boolean.TRUE.equals(ART_HIDE_GUARD.get(art))) {
                    return;
                }
                ART_HIDE_GUARD.put(art, Boolean.TRUE);
            }
            art.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
                @Override
                public void onLayoutChange(View v, int left, int top, int right, int bottom,
                                           int oldLeft, int oldTop, int oldRight, int oldBottom) {
                    try {
                        if (v.getVisibility() == View.GONE) {
                            return;
                        }
                        v.setVisibility(View.GONE);
                        Log.i(TAG, "a11 art native reshown rehidden vis=" + v.getVisibility()
                                + " w=" + v.getWidth() + " h=" + v.getHeight());
                    } catch (Throwable ignored) {
                    }
                }
            });
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

        /**
         * 把任意 Drawable 按它自己的尺寸画进位图。A11 就是这么干的（`Icon.toBitmap()` /
         * MediaNotificationProcessor 拿到的是 bitmap），因为 vivo 挂在 `android:id/art` 上的
         * 常常**不是 BitmapDrawable**（Icon 包装 / 异步加载的那一类），只认 BitmapDrawable 就会
         * 得到"没有封面"⇒ 卡片退回浅色底、文字用黑色，而屏幕上那张浮雕还是深色的上一首歌。
         */
        static Bitmap drawableToBitmap(Drawable d) {
            if (d == null || d instanceof BitmapDrawable) {
                return null;      // BitmapDrawable 走 viewArtBitmap 那条零拷贝路径
            }
            int w = d.getIntrinsicWidth();
            int h = d.getIntrinsicHeight();
            if (w <= 0 || h <= 0) {
                return null;
            }
            // 取色只需要小图；按 256 上限缩，省内存也省每帧的像素遍历
            float scale = Math.min(1f, 256f / Math.max(w, h));
            int bw = Math.max(1, Math.round(w * scale));
            int bh = Math.max(1, Math.round(h * scale));
            try {
                Bitmap bmp = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888);
                Canvas c = new Canvas(bmp);
                d.setBounds(0, 0, bw, bh);
                d.draw(c);
                return bmp;
            } catch (Throwable t) {
                return null;
            }
        }

        /**
         * 取色与封面的**唯一输入源**，优先级照 A11：通知自带的 largeIcon → 会话 metadata。
         * A11 里 `MediaNotificationProcessor` 处理的就是 sbn 自己的那张图，它在切歌那一拍
         * 已经是新一首；而 `controller.getMetadata()` 常常还停在上一首 —— 先读 metadata
         * 就是用户报的"左边取色是上一首歌"。两处都拿不到就返回 null（**不要**拿旧图顶着）。
         */
        /** 新封面已到、但还没换进 ImageView 时的那张图。切歌瞬间取色必须用它：
         *  否则 artSource() 读到的是上一首的封面 ⇒ 按钮/文字着色滞后一档，
         *  320ms 淡入期间就是"变淡、短暂看不清"（真机反馈）。 */
        Bitmap pendingArtSrc;

        Bitmap artSource() {
            Bitmap pending = pendingArtSrc;
            if (pending != null && !pending.isRecycled()) {
                pendingArtSrc = null;
                artFrom = "pending";
                return pending;
            }
            Bitmap fromView = viewArtBitmap(art);
            if (fromView != null) {
                artFrom = "largeIcon";
                return fromView;
            }
            Bitmap raster = art == null ? null : drawableToBitmap(art.getDrawable());
            if (raster != null) {
                artFrom = "largeIconDrawn";
                return raster;
            }
            Bitmap meta = controller == null ? null : metadataArt(controller.getMetadata());
            artFrom = meta != null ? "metadata" : "none";
            return meta;
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

        /**
         * os5 背景的磨砂底图。以前是 20×20 + 3×3 均值——拉伸到整卡只剩一片糊泥，
         * 用户报"os5 背景模糊不正常，好像糊了"。改成 96px 长边 + 三轮 box blur
         * （可分离滑动窗，O(n) 每轮）：低频色块结构保留，拉伸后是"磨砂取色底"
         * 而不是"放大马赛克"。走 ArtBgDrawable 原有的拉伸+渐变纱，其余链路不动。
         */
        static Bitmap softBitmap(Bitmap src) {
            if (src == null || src.isRecycled() || src.getWidth() < 2 || src.getHeight() < 2) {
                return null;
            }
            Bitmap small = null;
            try {
                float scale = Math.min(1f, 96f / Math.max(src.getWidth(), src.getHeight()));
                int w = Math.max(2, Math.round(src.getWidth() * scale));
                int h = Math.max(2, Math.round(src.getHeight() * scale));
                // 必须**自建可变位图**：createScaledBitmap 同尺寸时返回 src 本身，
                // 而 src（通知 largeIcon/Icon 解出来的那颗）是不可变的——
                // setPixels 直接 IllegalStateException（真机 01:38 栈）。
                small = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
                Canvas draw = new Canvas(small);
                draw.drawBitmap(src, null,
                        new RectF(0, 0, w, h), new Paint(Paint.FILTER_BITMAP_FLAG));
                int[] px = new int[w * h];
                small.getPixels(px, 0, w, 0, 0, w, h);
                int r = Math.max(2, Math.min(w, h) / 12);
                for (int pass = 0; pass < 3; pass++) {
                    boxBlur(px, w, h, r, true);
                    boxBlur(px, w, h, r, false);
                }
                small.setPixels(px, 0, w, 0, 0, w, h);
                Log.i(TAG, "os5 bg blur src=" + src.getWidth() + "x" + src.getHeight()
                        + " out=" + w + "x" + h + " r=" + r);
                return small;
            } catch (Throwable t) {
                Log.w(TAG, "softBitmap failed", t);
                if (small != null) {
                    try {
                        small.recycle();
                    } catch (Throwable ignored) {
                    }
                }
                return null;
            }
        }

        /** 可分离 box blur（滑动窗求和，边缘 clamp）。horizontal=false 时跑纵向。 */
        private static void boxBlur(int[] px, int w, int h, int r, boolean horizontal) {
            int n = horizontal ? w : h;
            int m = horizontal ? h : w;
            int win = r * 2 + 1;
            int[] out = new int[px.length];
            for (int j = 0; j < m; j++) {
                long sa = 0, sr = 0, sg = 0, sb = 0;
                for (int k = -r; k <= r; k++) {
                    int i = Math.min(n - 1, Math.max(0, k));
                    int c = px[horizontal ? j * w + i : i * w + j];
                    sa += Color.alpha(c);
                    sr += Color.red(c);
                    sg += Color.green(c);
                    sb += Color.blue(c);
                }
                for (int i = 0; i < n; i++) {
                    out[horizontal ? j * w + i : i * w + j] = Color.argb(
                            (int) (sa / win), (int) (sr / win), (int) (sg / win), (int) (sb / win));
                    int add = Math.min(n - 1, i + r + 1);
                    int sub = Math.max(0, i - r);
                    int ca = px[horizontal ? j * w + add : add * w + j];
                    int cs = px[horizontal ? j * w + sub : sub * w + j];
                    sa += Color.alpha(ca) - Color.alpha(cs);
                    sr += Color.red(ca) - Color.red(cs);
                    sg += Color.green(ca) - Color.green(cs);
                    sb += Color.blue(ca) - Color.blue(cs);
                }
            }
            System.arraycopy(out, 0, px, 0, px.length);
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
                Bitmap src = bmp != null ? bmp : artSource();
                int h = artHash(src);
                String artKey = h + "/" + style + "/" + (expanded ? "e" : "c")
                        + (night ? "n" : "l");
                if (h != 0 && artKey.equals(lastArtKey) && curBg != null) {
                    return;
                }
                boolean m3 = "material".equals(style);
                boolean a11 = "a11".equals(style);
                Drawable newBg;
                int accent;
                // 初值只是"分支没跑到时不该拿别的样式的色"的兜底：用 A11 通知卡自己的两档，
                // 不再引 QS 播放器那套固定白色阶（A11MediaSpec.TEXT_*，见 LOG-1103 §10）
                int a11Primary = night ? A11NotifSpec.PRIMARY_ON_DARK : A11NotifSpec.PRIMARY_ON_LIGHT;
                int a11Secondary = night
                        ? A11NotifSpec.SECONDARY_ON_DARK : A11NotifSpec.SECONDARY_ON_LIGHT;
                if (a11) {
                    // A11 的着色判定（Notification.isColorizedMedia 的原文）：
                    //   colorized = extras.get("android.colorized");  return (colorized==null||colorized) && hasMediaSession()
                    // 也就是说 MediaStyle + 有会话的媒体行**默认就是着色的**，底色来自封面。
                    // 收起/展开走**同一条链**：原来"展开态=恒深卡"是把 OS5/A15 的
                    // MediaDataManager.computeBackgroundColor（sat×0.8、L 锁 0.25）错搬进 A11 样式，
                    // A11 里没有这条规则（证据：LOG-1103 §3-4/§9/§12）。
                    boolean hasSession = controller != null;
                    boolean colorized = hasSession;
                    // 已知缺口（记进 LOG-1103 §13）：A11 的完整判据还要看
                    // extras["android.colorized"] 有没有被 app 显式关掉；本类拿不到 sbn
                    // （卡片是从视图树 + MediaController 建的），而实际媒体 app 基本不显式关，
                    // 所以这里按"有会话即着色"。要补就在 MediaCard 构造里把 sbn 传进来。
                    // 底色 = 左半幅 background swatch（A11Palette.colorizedPair 已实现同一条链）
                    int[] pair = src == null ? null : A11Palette.colorizedPair(src);
                    int base = A11NotifSpec.cardBackground(
                            colorized && pair != null, pair == null ? 0 : pair[0], night);
                    int fg = pair != null ? pair[1] : A11NotifSpec.primaryTextColor(base, night);
                    int primary = A11NotifSpec.primaryTextColor(base, night);
                    int secondary = A11NotifSpec.secondaryTextColor(base, night);
                    if (colorized && base != 0) {
                        // 着色时 A11 只调 alpha 把文字拉到 4.5:1（ensureColors 的第一支，LOG §9-4）
                        primary = A11NotifSpec.findAlphaToMeetContrast(primary, base, 4.5d);
                        secondary = A11NotifSpec.findAlphaToMeetContrast(secondary, base, 4.5d);
                    }
                    a11Primary = primary;
                    a11Secondary = secondary;
                    a11CardBg = base;
                    accent = fg;
                    GradientDrawable g = new GradientDrawable();
                    // A11 的行背景还是 9-patch：framework-res 与 SystemUI 资源表里
                    // dimen/notification_corner_radius 命中数都是 0（圆角是 A12 才加的）⇒ 半径 0
                    g.setCornerRadius(0f);
                    g.setColor(base);
                    newBg = g;
                    Log.i(TAG, "a11 palette state=" + (expanded ? "expanded" : "collapsed")
                            + " bg=" + Integer.toHexString(base)
                            + " fg=" + Integer.toHexString(fg)
                            + " primary=" + Integer.toHexString(a11Primary)
                            + " secondary=" + Integer.toHexString(a11Secondary)
                            + " colorized=" + (colorized ? 1 : 0)
                            + " session=" + (hasSession ? 1 : 0)
                            + " contrast=" + String.format(java.util.Locale.US, "%.2f",
                                    A11Palette.contrast(a11Primary, base))
                            + " artwork=" + (src != null)
                            + " art=" + artSize(src)
                            // 取色输入到底是哪一份图：用户报"切歌后左边还是上一首"，
                            // 来源字段就是那条能证伪它的证据
                            + " srcFrom=" + (bmp != null ? "argb" : artFrom)
                            // srcHash = 真正被采样那张图的指纹；metaTitle = 会话此刻的曲名。
                            // 三个一起看才能分清"图旧"还是"事件旧"：曲名已经新一首而
                            // srcHash 还是上一首那个值，就是**采到的图本身滞后**（用户报的现象）
                            + " srcHash=" + Integer.toHexString(h)
                            + " metaTitle=" + metaTitleForLog()
                            + " gridBg=" + artGrid(src, 0)
                            + " gridFg=" + artGrid(src, 1)
                            // 曲名放**行尾**：歌名里一定有空格，中间字段会把正则截断
                            + " track=" + (title.getText() == null ? "-"
                                    : title.getText().toString()));
                } else if (m3) {
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
                    // TransitionDrawable 只在被绘制时推进：闲置面板没有后续绘制，
                    // 过渡冻结在第 0 帧（旧底/黑），用户看到的就是"切歌后左边黑"。
                    // 过渡结束后补一次 invalidate，画到终态（真机 06:5x 帧对照证实）。
                    card.postInvalidateDelayed(340);
                } else {
                    card.setBackground(newBg);
                }
                curBg = newBg;
                if (a11) {
                    // §15-7 `updateSeekBarTint` 原文：thumb / progress / track = **同一个** originalIconColor
                    // 的 255 / 192 / 128 三档 alpha；§15-8 又证明着色时 originalIconColor == 正文主色。
                    // 两枚时间也取这支颜色，total 额外叠一层用卡片底色做的阴影（原生只有这一处阴影）。
                    int seekBase = a11Primary;
                    // alpha 与 QS 播放器的 media_seekbar_progress(#c0ffffff)/media_disabled(#80ffffff)
                    // 完全同值，只是基色换成取色链算出的主文字色：卡片底色可能很浅，白阶会看不见
                    seekDrawable.setColors(A11NotifSpec.seekTrackColor(seekBase),
                            A11NotifSpec.seekProgressColor(seekBase));
                    // 3dp 来自 notification_material_media_seekbar.xml 的 android:maxHeight（§15-6）。
                    // 以前记成"借 QS 的 qs_media_enabled_seekbar_height"：数值一样、出处不同，现已更正。
                    seekDrawable.setTrackThicknessDp(A11NotifSpec.SEEK_TRACK_DP);
                    // A11 的已读部分与轨道同厚（不是 iOS 那款 10dp 粗胶囊）
                    seekDrawable.uniformFill = true;
                    // 滑块要看得见（A11 样式带来的实心圆）；不可拖时靠 alpha=0 让它消失
                    bar.setThumb(thumb(ctx, A11NotifSpec.seekThumbColor(seekBase), true));
                    total.setShadowLayer(A11NotifSpec.SEEK_TIME_SHADOW_RADIUS,
                            A11NotifSpec.SEEK_TIME_SHADOW_DX, A11NotifSpec.SEEK_TIME_SHADOW_DY, a11CardBg);
                    elapsed.setShadowLayer(0f, 0f, 0f, 0);
                    // A11：着色时动作按钮 = getPrimaryTextColor(p)，与标题同色（§5b/§15-8）。
                    // 必须作用于**全部**按钮：只给播放键着色会让切歌键在深色封面上消失（真机反馈）。
                    if (playBtn != null) {
                        playBtn.setBackground(null);
                    }
                    tintActionButtons(card, A11NotifSpec.actionIconTint(
                            true, a11Primary, a11Primary));
                    title.setTextColor(a11Primary);
                    artist.setTextColor(a11Secondary);
                    elapsed.setTextColor(seekBase);
                    total.setTextColor(seekBase);
                    if (a11HeaderName != null) {
                        a11HeaderName.setTextColor(a11Secondary);
                    }
                    // 刚换过 thumb，把"可不可拖"的状态按 A11 的判据补回去（§15-7 setScrubberVisible）
                    reclaimBar();
                    // 见证读的是**施加之后的真实画笔/视图状态**，不是实现里再算一遍同一个表达式：
                    // 断言才有信息量（prog=primary@192、track=primary@128、thumb 随 canSeek 开合、
                    // total 带底色阴影而 elapsed 不带）
                    Drawable applied = bar.getThumb();
                    // 只读 alpha 会被"颜色全透明的占位"骗过去（之前就是这么漏的）：
                    // 现在把 drawable 自己的颜色和内在尺寸一起读回来。
                    // 用 if 而不是三元式：本机 android.jar 里 getColor() 的返回类型与
                    // null / 0 配起来都会撞上"三元式类型不兼容"（两次编译都栽在这儿）。
                    int thumbColor = 0;
                    if (applied instanceof GradientDrawable) {
                        // GradientDrawable.getColor() 返回的是 ColorStateList（可空），
                        // 取默认色即可：透明占位的那一颗默认色 alpha=0
                        android.content.res.ColorStateList csl =
                                ((GradientDrawable) applied).getColor();
                        thumbColor = csl == null ? 0 : csl.getDefaultColor();
                    }
                    Log.i(TAG, "a11 seek prog=" + Integer.toHexString(seekDrawable.fillPaint.getColor())
                            + " track=" + Integer.toHexString(seekDrawable.trackPaint.getColor())
                            + " base=" + Integer.toHexString(a11Primary)
                            + " thumbAlpha=" + (applied == null ? -1 : applied.getAlpha())
                            + " thumbColor=" + Integer.toHexString(thumbColor)
                            + " thumbPx=" + (applied == null ? -1 : applied.getIntrinsicWidth())
                            + " uniformFill=" + (seekDrawable.uniformFill ? 1 : 0)
                            + " barEnabled=" + (bar.isEnabled() ? 1 : 0)
                            + " thicknessDp=" + seekDrawable.trackThicknessDp
                            + " totalShadow=" + (total.getShadowRadius() > 0f ? 1 : 0)
                            + " totalShadowColor="
                            + Integer.toHexString(total.getShadowColor())
                            // 两枚时间**读回自己画笔上的色**：用户报"右边那枚在黑底上没反色、看不清"，
                            // 只看实现里赋的变量证明不了屏幕上那一份是什么色
                            + " elapsedColor=" + Integer.toHexString(elapsed.getCurrentTextColor())
                            + " totalColor=" + Integer.toHexString(total.getCurrentTextColor())
                            + " elapsedShadow=" + (elapsed.getShadowRadius() > 0f ? 1 : 0));
                    // 底色变了就必须重做封面浮雕（右缘淡出的终点 = 卡片底色），
                    // 否则"换歌 / 切深浅色"之后那条边会露出硬界（11-04 早上的反馈）。
                    syncArt();
                } else if (m3) {
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
                lastArtKey = artKey;
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

        /** 我们上一次真正贴到封面上的那张（不是 artView 当前值，那可能还是过渡包装） */
        Drawable lastAppliedArt;

        /**
         * 封面换图的过渡。A11 原生是硬切，用户 11-04 明确要淡入淡出：
         * 手上还有一张就交叉淡（旧图→新图），空窗后第一张到达就 alpha 0→1 淡入，
         * 两种都不再"啪"地蹦出来。时长走 `A11NotifSpec.ART_FADE_MS`（产品取向，非逆向值）。
         */
        void applyArtSwap(Drawable prev, Drawable next, boolean animated) {
            try {
                if (!animated) {
                    // 同一张图只是按新底色重染左边那层色调：直接替换。再淡一次会把正在跑的
                    // 过渡重启，用户看到的就是"旧封面突然消失"那种硬跳（11-04 午后的实测）
                    artView.animate().cancel();
                    artView.setAlpha(1f);
                    artView.setImageDrawable(next);
                    Log.i(TAG, "a11 art fade mode=retint");
                    return;
                }
                if (prev != null) {
                    TransitionDrawable td =
                            new TransitionDrawable(new Drawable[]{prev, next});
                    // 与卡片背景那次过渡同一套调用（startFade 在本机 android.jar 里没有）
                    td.setCrossFadeEnabled(true);
                    artView.setImageDrawable(td);
                    td.startTransition(A11NotifSpec.ART_FADE_MS);
                    // 同卡片背景：闲置面板下过渡冻结在第 0 帧，补一次终帧绘制
                    artView.postInvalidateDelayed(A11NotifSpec.ART_FADE_MS + 20);
                    Log.i(TAG, "a11 art fade mode=cross ms=" + A11NotifSpec.ART_FADE_MS);
                    return;
                }
                artView.setAlpha(0f);
                artView.setImageDrawable(next);
                artView.animate().alpha(1f).setDuration(A11NotifSpec.ART_FADE_MS).start();
                Log.i(TAG, "a11 art fade mode=in ms=" + A11NotifSpec.ART_FADE_MS);
            } catch (Throwable t) {
                // 过渡失败也必须把图画上，宁可硬切不要空白
                artView.setAlpha(1f);
                artView.setImageDrawable(next);
            }
        }

        void syncArt() {
            hideRowBackground();
            try {
                if (art != null) {
                    art.setVisibility(View.GONE);
                }
                // 与取色同一个来源解析（通知自带 largeIcon 优先，metadata 兜底）
                Bitmap pick = artSource();
                int h = artHash(pick);
                // 缓存键含底色：A11 的"封面右缘淡出进底色"要求浮雕底色 == 卡片底色，
                // 只按封面 hash 去重会让"换歌/换深浅色后底色变了"的那一轮仍然拿着旧底色，
                // 右边淡出的终点和卡片对不上 ⇒ 用户看到的就是一条硬边（11-04 早上就是这个反馈）。
                int reliefBg = a11CardBg == 0 ? A11NotifSpec.CARD_BG_LIGHT : a11CardBg;
                if (h == 0) {
                    // 这一帧**没有**封面来源。以前会拿留存的 `a11RawArt` 继续染一张贴上去，
                    // 于是切歌空窗期屏幕上就是上一首歌（用户报的那条）。现在清空，
                    // 等 bind / metadata / 封面图换了再画。
                    if (lastDrawableHash != 0) {
                        artView.setImageDrawable(null);
                        lastAppliedArt = null;
                        lastDrawableHash = 0;
                        Log.i(TAG, "a11 art cleared why=no-source " + artFrom);
                    }
                    return;
                }
                if (h == lastDrawableHash && reliefBg == a11ReliefBg) {
                    return;
                }
                // 只有**封面真的换了**才做淡入淡出；同一张图因底色变化重染时直接替换 ——
                // 那种情况下画面差异只是左边那层色调，重启一次淡入反而看得更跳
                boolean animated = h != lastDrawableHash;
                Drawable prevArt = lastAppliedArt;
                Drawable d = new BitmapDrawable(root.getResources(), pick);
                if ("a11".equals(style)) {
                    // A11 每有一张 largeIcon 就用 ImageGradientColorizer 换掉它（着色与否都会，
                    // LOG-1103 §3-4 最后一条）：左边是底色浮雕，向右逐渐露出真封面。
                    Bitmap relief = A11ArtRelief.colorize(pick, reliefBg,
                            root.getLayoutDirection() == View.LAYOUT_DIRECTION_RTL);
                    if (relief != null) {
                        d = new BitmapDrawable(root.getResources(), relief);
                        Log.i(TAG, "a11 art relief side=" + relief.getWidth() + "x"
                                + relief.getHeight() + " src="
                                + pick.getWidth() + "x" + pick.getHeight()
                                + " hash=" + Integer.toHexString(h)
                                + " color=" + Integer.toHexString(reliefBg)
                                + " from=" + artFrom
                                + " rtl=" + (root.getLayoutDirection()
                                        == View.LAYOUT_DIRECTION_RTL ? 1 : 0));
                    }
                }
                a11RawArt = pick;
                applyArtSwap(animated ? prevArt : null, d, animated);
                lastAppliedArt = d;
                lastDrawableHash = h;
                a11ReliefBg = reliefBg;
                if (art != null) {
                    // 挂在这里而不是只挂在建卡那一次：换歌的重新应用发生在绑定之后（全样式）
                    guardArtHidden(art);
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
            if ("a11".equals(style)) {
                // A11 的 setScrubberVisible（§15-7）：不可 seek 只把 **thumb 调透明** + setEnabled(false)，
                // 轨道和两枚时间照常满强度显示。原来把整条 bar 压到 0.85 是自创的"变暗"，原生没有。
                // 播放器样式：thumb 常驻可见（通知卡那一版才会把它调透明），
                // 不可拖只 setEnabled(false) + 吞掉手势，条与时间满强度
                bar.setAlpha(1f);
                bar.setOnTouchListener(seekable ? null : CONSUME_TOUCH);
            } else if (seekable) {
                bar.setOnTouchListener(null);
                bar.setAlpha(1f);
            } else {
                bar.setOnTouchListener(CONSUME_TOUCH);
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
                // 卡上的 title/artist 是从模板摘走的**原视图**：OS4 上通知重绑不走
                // setChild（RemoteViews 重投对已摘走的视图无效），文字必须自己跟会话
                // 元数据走，否则换歌后卡上停在上一首（真机 01:25-01:26 实测）。
                syncTextFromMetadata(md);
                // 建卡那一拍封面视图可能还没内容（RemoteViews 未投完）→ art=null，
                // bbkmusic 元数据又没有封面位图 ⇒ artSource 永远 null ⇒ a11 卡
                // 永久黑底、永不取色（用户报"奢香夫人/读心术左边黑"）。重探一次。
                rediscoverArtIfNeeded();
                if (!key.isEmpty() && key.equals(lastTrackKey)) {
                    return;
                }
                Bitmap src = artSource();
                if (src == null && curBg != null) {
                    // 没有封面就不动色，但**别把这一首歌的 key 记成"已处理"**：
                    // 记了就等于"后来封面到了也不再取色"，用户看到的正是停在上一首的底色
                    return;
                }
                Log.i(TAG, "track changed, retinting: " + key
                        + " src=" + (src == null ? "null" : src.getWidth() + "x" + src.getHeight())
                        + " artFrom=" + artFrom
                        + " artView=" + (art == null ? "null" : art.getClass().getSimpleName()
                                + ":" + (art.getDrawable() == null ? "empty" : "ok")));
                applyPalette(animate, src);
                lastTrackKey = key;
                // 底色刚更新：补一次 syncArt，浮雕（用 a11CardBg 当左缘色）与底色
                // 永远成对，不依赖任何调用方记得"先取色后画封面"的顺序。
                // syncArt 自身有 hash+bg 双条件幂等门，同图同底色时零成本。
                syncArt();
            } catch (Throwable t) {
                Log.w(TAG, "refreshColors failed", t);
            }
        }

        /**
         * title/artist 的可见性/alpha 自愈：OS4 真机有外部写手把摘进我们卡的 title
         * 设成 INVISIBLE（1s bar.post 见证抓到 titleVis=4、尺寸正常 432x46），
         * 后来又抓到 alpha=0.0（color=ffffffff、vis=0、xy 全对，就是不画）——
         * 都不是我们任何路径写的（我们只对 art 视图做 alpha 淡入）。
         * ticker 每拍幂等修复（写前判等，不做重绘机）。
         */
        void repairTextVisibility() {
            if (title != null) {
                if (title.getVisibility() != View.VISIBLE) {
                    title.setVisibility(View.VISIBLE);
                    Log.i(TAG, "card title restored vis");
                }
                if (title.getAlpha() != 1f) {
                    title.setAlpha(1f);
                    Log.i(TAG, "card title restored alpha");
                }
            }
            if (artist != null) {
                if (artist.getVisibility() != View.VISIBLE) {
                    artist.setVisibility(View.VISIBLE);
                    Log.i(TAG, "card artist restored vis");
                }
                if (artist.getAlpha() != 1f) {
                    artist.setAlpha(1f);
                    Log.i(TAG, "card artist restored alpha");
                }
            }
        }

        /**
         * art 引用为空（或没内容）时重探一次：建卡那一拍 RemoteViews 往往还没把
         * largeIcon 投进模板，discoverMedia 的 right_icon/largestArtView 都拿不到
         * drawable ⇒ art=null。此后 bbkmusic 的元数据里没有封面位图，artSource()
         * 永远 null —— a11 卡黑底不取色（用户报"切到奢香夫人/读心术左边黑"）。
         * 探到就接管：隐藏原生视图 + 挂防闪守卫，之后 watchArtArrival/retint 正常走。
         */
        void rediscoverArtIfNeeded() {
            if (art != null && art.getDrawable() != null) {
                return;
            }
            try {
                View found = null;
                int artId = id(root.getContext(), "right_icon");
                if (artId != 0) {
                    View v = root.findViewById(artId);
                    if (v instanceof ImageView && ((ImageView) v).getDrawable() != null) {
                        found = v;
                    }
                }
                if (found == null) {
                    View v = largestArtView(root);
                    if (v instanceof ImageView && ((ImageView) v).getDrawable() != null) {
                        found = v;
                    }
                }
                if (found instanceof ImageView) {
                    ImageView prev = art;
                    art = (ImageView) found;
                    art.setVisibility(View.GONE);
                    guardArtHidden(art);
                    lastArtDrawableSeen = null;
                    lastArtHashSeen = 0;
                    Log.i(TAG, "art rediscovered cls=" + art.getClass().getName()
                            + " prev=" + (prev == null ? "null" : prev.getClass().getSimpleName()));
                }
            } catch (Throwable t) {
                Log.w(TAG, "rediscover art failed", t);
            }
        }

        /** 标题/歌手跟随会话元数据；空串不写（避免把卡面抹白），同值不写（避免无谓重排） */
        private void syncTextFromMetadata(MediaMetadata md) {
            if (md == null || title == null) {
                return;
            }
            try {
                CharSequence t = md.getDescription().getTitle();
                if (t != null && t.length() > 0 && !t.toString().equals(str(title.getText()))) {
                    title.setText(t.toString());
                }
                if (artist != null) {
                    CharSequence a = md.getDescription().getSubtitle();
                    if (a != null && a.length() > 0 && !a.toString().equals(str(artist.getText()))) {
                        artist.setText(a.toString());
                    }
                }
            } catch (Throwable t) {
                Log.w(TAG, "sync text failed", t);
            }
        }

        static final class SeekDrawable extends Drawable {
            final Paint trackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            final float density;
            float trackThicknessDp = 4f;
            /**
             * 进度部分是否与轨道同厚。A11 的两个媒体模板（framework 的
             * `notification_material_media_seekbar` 与设备那支 `vigour_media_seekbar`）都只写
             * `android:maxHeight=3dp`，形状由 progress drawable 自己出 ⇒ 已读部分是**细线**；
             * 而 iOS/OS5 那一款是 10dp 粗胶囊。默认 false 保持老样，a11 显式打开。
             */
            boolean uniformFill;
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

            void setTrackThicknessDp(float v) {
                trackThicknessDp = v;
                invalidateSelf();
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
                float trackH = Math.min(density * trackThicknessDp, b.height());
                // A11：已读部分与轨道同厚（两端圆头自然接成一条圆头细线）；
                // iOS/OS5：已读部分是 10dp 粗胶囊，余轨是细线 —— 这个差别就是用户说的"不像 A11"。
                float fillH = uniformFill ? trackH : Math.min(density * 10f, b.height());
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

        /**
         * 进度条滑块。
         *
         * `visible=false` 是 iOS/OS5 那一款：留一颗 10dp 的**全透明**占位（只撑触摸热区，看不见）。
         * a11 要走 `visible=true`：A11 那两个媒体模板都没写 `android:thumb`，滑块来自样式，
         * 是看得见的实心圆 —— 之前一律用透明占位，所以 A11 样式下"没有滑块"，
         * 而见证只读 drawable 的 alpha（透明色也报 255）完全看不出来，这次一并补上。
         */
        private static Drawable thumb(Context ctx, int accent) {
            return thumb(ctx, accent, false);
        }

        private static int lastThumbSizePx;

        private static Drawable thumb(Context ctx, int accent, boolean visible) {
            GradientDrawable d = new GradientDrawable();
            d.setShape(GradientDrawable.OVAL);
            // 颜色带上 accent 的 alpha：可拖时 255、不可拖时 A11 也是靠 alpha 让它消失
            d.setColor(visible ? accent : 0x00000000);
            int size = dp(ctx, 10);
            lastThumbSizePx = visible ? size : 0;
            d.setSize(size, size);
            return d;
        }

        // ---- media session ----

        void onAttached() {
            if (controller == null) {
                bindSession();
            }
            updateProgress();
            tickIfPlaying();
            // 暂停状态下没有秒级定时器，所以绑定这一拍也要比对一次封面身份，
            // 否则"暂停时切歌"这一类就没人重取色
            watchArtArrival();
        }

        /**
         * 只有**正在播放**才挂那个 1s 定时器（A11 也是这么做的：暂停的卡没有会动的东西）。
         * 拖动中例外——拖完要等 `suppressUntil` 过去才回到会话位置，所以期间得继续走。
         */
        void tickIfPlaying() {
            try {
                PlaybackState st = controller == null ? null : controller.getPlaybackState();
                boolean playing = st != null
                        && st.getState() == PlaybackState.STATE_PLAYING;
                if (playing || dragging) {
                    startTicker();
                } else {
                    tickerRunning = false;
                }
            } catch (Throwable ignored) {
            }
        }

        /** 会话服务比 SystemUI 晚起来（重启后建卡那一拍 getActiveSessions 为空），
         *  卡片会从此永远黑底/白钮（真机 00:56 复现）。拿到控制器前每 2.5s 重试一次，
         *  最多 8 次；root 离开窗口即停。 */
        int sessionRetries;

        void scheduleSessionRetry() {
            if (sessionRetries >= 8) {
                return;
            }
            sessionRetries++;
            handler.postDelayed(() -> {
                try {
                    if (controller == null && root.isAttachedToWindow()) {
                        bindSession();
                    }
                } catch (Throwable ignored) {
                }
            }, sessionRetries == 1 ? 1200 : 2500);
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
                    scheduleSessionRetry();
                    return;
                }
                sessionRetries = 0;
                Log.i(TAG, "session=" + chosen.getPackageName() + " state="
                        + (chosen.getPlaybackState() == null ? -1 : chosen.getPlaybackState().getState()));
                controller = chosen;
                updateSourceIcon();
                // 启动竞态自愈：建卡那一拍会话服务还没起（palette 黑底兜底、duration=0），
                // 现在控制器才绑上，而元数据没变就不会再有回调 —— 作废 trackKey 强制重取色。
                // 顺序必须是**先取色后画封面**（与 watchArtArrival 同序）：
                // 反过来浮雕会拿建卡时的黑底画一张（a11CardBg 尚未更新），
                // 而封面指纹不变就没人再触发 syncArt —— 黑左底陪伴整首歌（真机 07:00 栈）。
                lastTrackKey = "";
                refreshColors(false);
                syncArt();
                chosen.registerCallback(callback, handler);
                updateSeekable();
                updateProgress();
            } catch (Throwable t) {
                Log.w(TAG, "bindSession failed", t);
            }
        }

        /** 会话此刻的曲名（只给见证用）：和 srcHash 一起看才知道"图旧"还是"事件旧"。 */
        String metaTitleForLog() {
            try {
                MediaMetadata md = controller == null ? null : controller.getMetadata();
                CharSequence t = md == null ? null : md.getDescription().getTitle();
                return t == null ? "-" : t.toString();
            } catch (Throwable e) {
                return "err";
            }
        }

        /** 上一拍看到的封面 Drawable 身份；变了才重取色（比像素便宜，也不会把窗口闹得永不空闲）。 */
        Drawable lastArtDrawableSeen;
        /** 上一拍那张封面的指纹（零拷贝那条才算）：Drawable 实例被复用但内容换了也抓得住。 */
        int lastArtHashSeen;

        /**
         * 通知把 largeIcon 换成新一首时，`art` 里的 Drawable 实例会变，而 bind / metadata 两个事件
         * 都可能早于它到达（vivo 的会话 metadata 经常慢一拍）。这一路只做身份比较：
         * 没变就一个调用都不发，变了才 syncArt()+refreshColors()。
         */
        void watchArtArrival() {
            try {
                Drawable d = art == null ? null : art.getDrawable();
                // 只走零拷贝那条（BitmapDrawable）算指纹：非位图的那一类每秒栅格化太贵，
                // 交给 Drawable 身份比较兜底
                int h = artHash(viewArtBitmap(art));
                if (d == lastArtDrawableSeen && h == lastArtHashSeen) {
                    return;
                }
                boolean known = lastArtDrawableSeen != null || d == null;
                lastArtDrawableSeen = d;
                lastArtHashSeen = h;
                Log.i(TAG, "art drawable changed now=" + (d == null ? "null"
                        : d.getClass().getSimpleName()) + " hash=" + Integer.toHexString(h));
                if (known) {
                    // 换歌的真实顺序是：曲目事件先到、新一首的封面图**后**到（真机见证：
                    // 取色时 srcHash=1a13df86，随后浮雕却拿到 hash=35f4616a）。
                    // 此时 trackKey 已经是新一首，refreshColors 会在入口直接早退，
                    // 于是左半底色永久停在上一首那张图的取色结果——正是用户报的现象。
                    lastTrackKey = "";
                }
                // 顺序必须是**先取色、后画封面**：A11 的浮雕要用卡片底色做右缘淡出的终点，
                // 反过来就会先拿旧底色画一张、320ms 淡入跑到一半又被新底色那张重启
                // （真机见证：同一次切歌里 relief color=ffffffff → 24ms 后 color=ff103808，
                // 两条 a11 art fade 相隔 24ms）——用户看到的"还是生硬切换"就是这个重启。
                // 先取色后画封面：把**新**封面交给取色（pendingArtSrc），
                // 否则 refreshColors 里 artSource() 读到的还是上一首，按钮着色慢一档。
                if (d instanceof android.graphics.drawable.BitmapDrawable) {
                    pendingArtSrc = ((android.graphics.drawable.BitmapDrawable) d).getBitmap();
                }
                refreshColors(false);
                syncArt();
            } catch (Throwable ignored) {
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
                boolean a11 = "a11".equals(style);
                float want = (a11 || canSeek) ? 1f : 0.85f;
                // 可见性不再在这里碰（11-05）：换了自己的 id 之后没有任何外部写入者
                // 会改这根条，`reclaimBar` 只负责 alpha/可拖状态；可见性由
                // applyA11SeekVisibility 一处决定。
                if (Math.abs(bar.getAlpha() - want) > 0.01f) {
                    bar.setAlpha(want);
                }
                if (a11) {
                    // 按 A11 的判据把"可不可拖"这一对**一起**纠正（§15-7 setScrubberVisible）：
                    // thumb 255⟺enabled、thumb 0⟺disabled。分开纠正会让见证抓到中间态（自相矛盾）。
                    Drawable th = bar.getThumb();
                    // thumb 常驻 255（播放器样式），不再随 canSeek 消失
                    if (th != null && th.getAlpha() != A11NotifSpec.SEEK_THUMB_ALPHA) {
                        th.setAlpha(A11NotifSpec.SEEK_THUMB_ALPHA);
                    }
                    if (bar.isEnabled() != canSeek) {
                        bar.setEnabled(canSeek);
                    }
                    bar.setOnTouchListener(canSeek ? null : CONSUME_TOUCH);
                }
            } catch (Throwable ignored) {
            }
        }

        void updateProgress() {
            updateProgress(null);
        }

        /**
         * @param known 来自 `onMetadataChanged` 回调自带的那份 metadata。
         *                  A11 原生就是用它（`NotificationMediaTemplateViewWrapper` 的 mMediaCallback：
         *                  `mMediaMetadata = mediaMetadata; updateDuration();`），
         *                  而我们原来在这里**重新轮询** `controller.getMetadata()` ——
         *                  切歌那一拍曲名先到、新 metadata 还没落到会话上，轮询拿到 null ⇒
         *                  时长被当成 0 ⇒ 整条 seek 容器 GONE ⇒ 列按零宽重测 ⇒
         *                  恢复时 `weight=1` 的条拿不到剩余宽度、两枚时间贴合到左（用户报的"切歌时进度条闪一下"）。
         */
        void updateProgress(MediaMetadata known) {
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
                MediaMetadata md = known != null ? known : controller.getMetadata();
                PlaybackState st = controller.getPlaybackState();
                // 时长 sticky：A11 的 `updateDuration()` 在 metadata==null 时**直接 return**，
                // 也就是沿用上一首的 max/总时长，而不是当成 0。原来这里 `md==null → 0`
                // 把"暂时没拿到"错当成"这首歌没有时长"，于是切歌空窗期整条进度条被藏掉。
                long duration = md == null ? lastDurationMs
                        : md.getLong(MediaMetadata.METADATA_KEY_DURATION);
                long position = st == null ? 0 : st.getPosition();
                // A11 的"时长未知就没有进度条"这条判据（§15-7）；只在值变了才会走日志/post
                lastDurationMs = duration;
                applyA11SeekVisibility();
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
                repairTextVisibility();
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
        // A11 原文：`DateUtils.formatElapsedTime(ms / 1000)`（NotificationMediaTemplateViewWrapper
        // .millisecondsToTimeString，LOG-1103 §15-7）。自己拼 "%d:%02d" 过了一小时会写成 "62:05"，
        // 而系统写 "1:02:05" —— 复刻就别自创格式。
        return android.text.format.DateUtils.formatElapsedTime(ms / 1000);
    }
}
