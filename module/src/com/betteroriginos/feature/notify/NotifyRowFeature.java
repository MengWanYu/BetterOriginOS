package com.betteroriginos.feature.notify;

import android.app.Notification;
import android.app.Person;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.ContentObserver;
import android.os.Bundle;
import android.os.Parcelable;
import android.text.format.DateUtils;
import android.graphics.Outline;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.service.notification.StatusBarNotification;
import android.text.TextUtils;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.ViewOutlineProvider;
import android.view.ViewTreeObserver;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.betteroriginos.R;
import com.betteroriginos.core.Feature;
import com.betteroriginos.core.OsProfile;
import com.betteroriginos.core.Prefs;

import java.util.Iterator;
import java.util.List;
import java.util.WeakHashMap;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

/**
 * 通知条 iOS 化（收起态，原地改造）。
 * 不动模板与绑定链，只调整 header / 内容列的布局关系；展开态、点击链、动画保持原生。
 */
public final class NotifyRowFeature extends Feature {
    private static final String TAG = "BetterOriginOS";
    private static final String DEBUG_KEY = "betteroriginos_notify_debug";
    private static final String VERIFY_KEY = "betteroriginos_notify_verify";
    private static final String REVERIFY_KEY = "betteroriginos_notify_reverify";
    private static final String EXPAND_TEST_KEY = "betteroriginos_notify_expand_test";
    private static final String STACK_TEST_KEY = "betteroriginos_notify_stack_test";
    private static final String SCROLL_TEST_KEY = "betteroriginos_notify_scroll_test";
    private static final String TAG_ICON = "betteroriginos_notify_icon";
    private static final String TAG_LIGHT_BIG = "betteroriginos_notify_light_big";
    /**
     * header 时间的接管方式自报。push-only=只在事后推 translationX（col 布局钩子没装），
     * column-hook=装了布局期对位；验证器用它决定"col measure"那套断言该不该跑。
     * 必须按**实际装没装上**自报（sColumnHook），别写死：写死过一次，装机后类不存在也照样报
     * column-hook，门禁据此跑完整套 col 断言然后全绿 —— 那轮结论是假的。
     * 只靠 install 那一行不够：SystemUI 启动那一秒 vivo 自己刷几百行，logd 的每进程
     * 250/s 会把它整条冲掉（11-04 真机两次实测），所以补扫也按低频重发同一行。
     */
    private static String headerTimeMode() {
        return "notify row feature installed header-time="
                + (sColumnHook ? "column-hook" : "push-only");
    }
    private static long sModeWitnessAt;
    private static final int LIGHT_TITLE_RAISE_DP = 8;
    private static final int LIGHT_ICON_DP = 42;
    private static final int LIGHT_ICON_CORNER_DP = 11;
    private static final WeakHashMap<View, View> LIGHT_NATIVE_ICON = new WeakHashMap<>();
    /** 按住原生 header 小图标时被我们摘下来的那张 drawable（放开时要装回去） */
    private static final WeakHashMap<View, android.graphics.drawable.Drawable>
            LIGHT_ICON_DRAWABLE = new WeakHashMap<>();
    private static final WeakHashMap<View, int[]> LIGHT_ICON_LP = new WeakHashMap<>();
    private static final WeakHashMap<View, ViewTreeObserver> LIGHT_ICON_GUARD =
            new WeakHashMap<>();
    private static final WeakHashMap<View, int[]> LIGHT_COLUMN_BOX = new WeakHashMap<>();
    private static final WeakHashMap<View, Integer> LIGHT_COLUMN_HEADER =
            new WeakHashMap<>();
    private static final WeakHashMap<View, Integer> LIGHT_COLUMN_MARGIN =
            new WeakHashMap<>();
    private static final WeakHashMap<View, ViewTreeObserver> LIGHT_MORPH_GUARD =
            new WeakHashMap<>();
    private static final WeakHashMap<View, ViewTreeObserver> STACK_GUARD_VTO =
            new WeakHashMap<>();
    private static final int COLLAPSED_MAX_DP = 200;
    private static final WeakHashMap<View, Boolean> RESTYLED = new WeakHashMap<>();
    private static final WeakHashMap<View, Boolean> RESTYLED_ROWS = new WeakHashMap<>();
    private static final WeakHashMap<View, Boolean> DUMPED = new WeakHashMap<>();
    private static final WeakHashMap<View, Boolean> GUARDED = new WeakHashMap<>();
    /**
     * overlay 守卫要单独记账：GUARDED 是"装过就算"的布尔标记，
     * 而行回收后 ViewTreeObserver 会换，旧监听已失效却仍被标记为已装
     * -> 压 alpha / 淡出永久不再执行（实测"展开重叠"因此时有时无）。
     */
    private static final WeakHashMap<View, ViewTreeObserver.OnPreDrawListener> OVERLAY_GUARDS =
            new WeakHashMap<>();
    private static final WeakHashMap<View, ViewTreeObserver> OVERLAY_GUARD_VTO =
            new WeakHashMap<>();
    private static final WeakHashMap<View, Boolean> VERIFIED = new WeakHashMap<>();
    private static final WeakHashMap<View, Boolean> VERIFY_INFO = new WeakHashMap<>();
    private static final WeakHashMap<View, Boolean> AUTO_EXPANDED_ROWS = new WeakHashMap<>();
    private static final WeakHashMap<View, IosNotificationRow> OVERLAYS = new WeakHashMap<>();
    private static final WeakHashMap<View, Boolean> OVERLAY_ROOTS = new WeakHashMap<>();
    private static final WeakHashMap<View, Boolean> HEADSUP_ROOTS = new WeakHashMap<>();
    /** iOS 折叠堆叠：组摘要行 -> 自绘堆叠卡（露边 + 条数徽标） */
    private static final WeakHashMap<View, IosNotificationRow> STACK_OVERLAYS = new WeakHashMap<>();
    private static final WeakHashMap<View, View[]> STACK_VERIFY_INFO = new WeakHashMap<>();
    private static final WeakHashMap<View, Boolean> STACK_ROWS = new WeakHashMap<>();
    /** 组摘要行 -> 它的堆叠卡：折叠期间用卡片高度顶掉原生"子容器固有高度"的排版步长 */
    private static final WeakHashMap<View, IosNotificationRow> STACK_CARDS = new WeakHashMap<>();
    private static final WeakHashMap<View, ViewTreeObserver.OnPreDrawListener> STACK_GUARDS =
            new WeakHashMap<>();
    private static final WeakHashMap<View, Boolean> STACK_NIGHT = new WeakHashMap<>();
    private static final WeakHashMap<View, Integer> STACK_ROOT_PAD = new WeakHashMap<>();
    private static final WeakHashMap<View, ViewGroup> STACK_CONTAINERS = new WeakHashMap<>();
    private static final WeakHashMap<View, Boolean> STACK_USER_EXPANDED = new WeakHashMap<>();
    /** 组内子行数快照 + 去抖：组新增/减少通知时摘要行不会重新绑定，卡片必须自己重算 */
    private static final WeakHashMap<View, Integer> STACK_SEEN_KIDS = new WeakHashMap<>();
    private static final WeakHashMap<View, Long> STACK_DIRTY_AT = new WeakHashMap<>();
    private static final WeakHashMap<View, Long> STACK_SYNC_AT = new WeakHashMap<>();
    /** 被我们退出测量的行内大件，记住原可见性以便回收时还原 */
    private static final WeakHashMap<View, Integer> STACK_EXTRA_VIS = new WeakHashMap<>();
    /** GroupExpansionManager 按 entry 记账，收起动作也从那里来，需要反查回行 */
    private static final WeakHashMap<Object, View> STACK_ENTRIES = new WeakHashMap<>();
    /** 我们自己压折叠/展开时不要记成“用户操作” */
    private static volatile boolean sCollapsing;
    private static volatile boolean sExpanding;
    /** 每帧 pre-draw 只回放缓存目标，避免整棵通知视图树反复遍历 */
    private static final WeakHashMap<View, View[]> GUARD_TARGETS = new WeakHashMap<>();
    private static final WeakHashMap<View, View[]> COLOR_TARGETS = new WeakHashMap<>();
    /** 待验证 overlay：行首次真正布局可见时出验证日志，避免滚动/惰性装载漏检 */
    private static final WeakHashMap<View, Boolean> OVERLAY_VERIFY_PENDING = new WeakHashMap<>();
    private static boolean sReverifyRegistered;
    /** 关闭 OS5 分组显示：isGroup=false + 从渲染列表移除有子项的组摘要 */
    private static volatile boolean sUngroupEnabled;
    /** iOS 折叠堆叠模式（group_mode=ios_stack）：同 app 多条折叠成一叠，点开原生展开 */
    private static volatile boolean sStackMode;
    /**
     * OS6 堆叠样式的总开关：置 false 时选项不出现、设置项被忽略，只保留"每条独立卡"分离模式。
     * 展开态由卡片自绘（AOSP 侧永不展开），行高唯一真源是 IosNotificationRow#contentHeight。
     */
    private static final boolean STACK_BUILD_ENABLED = true;
    private static volatile boolean sVerifyEnabled;
    private static long sLastWitnessAt;
    private static long sLastRoundnessDumpAt;
    /** 展开交接：overlay 当前淡入/淡出的目标值，与已打日志的状态 */
    private static final long OVERLAY_FADE_MS = 160;
    private static final WeakHashMap<View, Float> OVERLAY_FADE_TO = new WeakHashMap<>();
    private static final WeakHashMap<View, Boolean> HANDOFF_LOGGED = new WeakHashMap<>();
    /** 展开重影守卫：contentView -> 已挂（与 GUARDED 分开，避免和 overlay 守卫互斥） */
    private static final WeakHashMap<View, Boolean> EXPANDED_GHOST = new WeakHashMap<>();
    /** 展开子视图的 header 小图标已按掉的实例（每个展开子视图只做一次） */
    private static final WeakHashMap<View, Boolean> GHOST_ICONS_HIDDEN = new WeakHashMap<>();
    /** 展开子视图里要常驻按掉的图标（由 contentView 的活守卫逐帧补写，绕开 VTO 换实例的坑） */
    private static final WeakHashMap<View, View[]> GHOST_TARGETS = new WeakHashMap<>();
    /** 展开/收起两个子视图当前淡变目标（按子视图记，避免每帧重启动画） */
    private static final WeakHashMap<View, Float> CHILD_FADE_TO = new WeakHashMap<>();
    /** 行展开态记忆：只在翻转那一帧启动一次位移动画 */
    private static final WeakHashMap<View, Boolean> ROW_OPEN = new WeakHashMap<>();
    /** 飞行中的那枚图标（每行一个，盖在 contentView 上） */
    private static final WeakHashMap<View, ImageView> FLIGHT = new WeakHashMap<>();
    private static final long MORPH_MS = 240;
    /** 逐帧补写次数（只用于日志，证明"动画写回 alpha"这条确实会发生） */
    private static int sGhostReapplied;
    /** 展开态收起侧 alpha 纠偏次数（A13 展开动画会写回 1） */
    private static int sConZeroed;
    private static int sGroupHidden;
    private static ClassLoader sClassLoader;

    @Override
    public String id() {
        return "notify_row";
    }

    @Override
    public int titleRes() {
        return R.string.feature_notify_title;
    }

    @Override
    public int summaryRes() {
        return R.string.feature_notify_summary;
    }

    @Override
    public int categoryRes() {
        return R.string.category_notify;
    }

    @Override
    public int iconRes() {
        return R.drawable.ic_notify;
    }

    @Override
    public int supportNoteRes(Context ctx) {
        return OsProfile.isA13OrLower() ? R.string.note_notify_actions_a13 : 0;
    }

    @Override
    public boolean defaultEnabled() {
        return false;
    }

    @Override
    public String previewKind() {
        return "notify";
    }

    @Override
    protected java.util.List<com.betteroriginos.core.FeatureOption> buildOptions() {
        java.util.List<com.betteroriginos.core.FeatureOption> options =
                new java.util.ArrayList<>();
        // OS3 专属：把 OS5 的通知几何（连续角半径、收纳栏、浮动一键清除）移植过来
        if (OsProfile.isA13OrLower()) {
            options.add(com.betteroriginos.core.FeatureOption.choice(
                    "sep",
                    R.string.option_sep_title,
                    R.string.option_sep_summary,
                    "off",
                    new String[]{"off", "os5"},
                    new int[]{R.string.sep_off, R.string.sep_os5},
                    null));
        }
        return options;
    }

    @Override
    public void install(ClassLoader cl) {
        sClassLoader = cl;
        ensureGroupRoot();
        try {
            Class<?> contentView = XposedHelpers.findClass(
                    "com.android.systemui.statusbar.notification.row.NotificationContentView", cl);
            XposedBridge.hookAllMethods(contentView, "setContractedChild", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    Object child = param.args != null && param.args.length > 0 ? param.args[0] : null;
                    if (child instanceof View && param.thisObject instanceof View) {
                        scheduleRestyle((View) param.thisObject, (View) child, 0);
                    }
                }
            });
            XposedBridge.hookAllMethods(contentView, "setExpandedChild", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    Object child = param.args != null && param.args.length > 0 ? param.args[0] : null;
                    if (child instanceof View && param.thisObject instanceof View) {
                        scheduleRestyle((View) param.thisObject, (View) child, 1);
                    }
                }
            });
            XposedBridge.hookAllMethods(contentView, "setHeadsUpChild", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    Object child = param.args != null && param.args.length > 0 ? param.args[0] : null;
                    if (child instanceof View && param.thisObject instanceof View) {
                        scheduleRestyle((View) param.thisObject, (View) child, 2);
                    }
                }
            });
            XposedBridge.hookAllMethods(contentView, "calculateVisibleType", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    if (!RESTYLED.containsKey(param.thisObject)) {
                        return;
                    }
                    if (needsExpandForActions((View) param.thisObject)) {
                        // 压成 COLLAPSED 会让原生不摆 action_list -> 收起态的按钮消失
                        return;
                    }
                    try {
                        if (!XposedHelpers.getBooleanField(param.thisObject, "mIsHeadsUp")) {
                            param.setResult(0);
                        }
                    } catch (Throwable t) {
                        param.setResult(0);
                    }
                }
            });
            XposedBridge.hookAllMethods(contentView, "onMeasure", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    try {
                        View cv = (View) param.thisObject;
                        IosNotificationRow stackOverlay = STACK_OVERLAYS.get(cv);
                        if (stackOverlay != null) {
                            int w = cv.getMeasuredWidth();
                            View contracted = currentContracted(cv);
                            int h = contracted == null ? 0 : contracted.getMeasuredHeight();
                            if (h > 0 && w > 0) {
                                stackOverlay.measure(View.MeasureSpec.makeMeasureSpec(
                                                w, View.MeasureSpec.EXACTLY),
                                        View.MeasureSpec.makeMeasureSpec(
                                                h, View.MeasureSpec.EXACTLY));
                                XposedHelpers.callMethod(cv, "setMeasuredDimension", w, h);
                            }
                            return;
                        }
                        IosNotificationRow overlay = OVERLAYS.get(cv);
                        if (overlay != null) {
                            int w = cv.getMeasuredWidth();
                            overlay.measure(View.MeasureSpec.makeMeasureSpec(
                                            w, View.MeasureSpec.EXACTLY),
                                    View.MeasureSpec.makeMeasureSpec(
                                            0, View.MeasureSpec.UNSPECIFIED));
                            int oh = overlay.getMeasuredHeight();
                            View contracted = currentContracted(cv);
                            if (contracted != null) {
                                contracted.measure(View.MeasureSpec.makeMeasureSpec(
                                                w, View.MeasureSpec.EXACTLY),
                                        View.MeasureSpec.makeMeasureSpec(
                                                oh, View.MeasureSpec.EXACTLY));
                            }
                            XposedHelpers.callMethod(cv, "setMeasuredDimension", w, oh);
                            return;
                        }
                        if (!RESTYLED.containsKey(param.thisObject)) {
                            return;
                        }
                        if (needsExpandForActions(cv)) {
                            // 动作带在行内、模板之外：按收起子 View 高度钳会把展开后的按钮切掉
                            return;
                        }
                        View contracted = currentContracted(cv);
                        if (contracted == null
                                || XposedHelpers.getIntField(cv, "mVisibleType") != 0) {
                            return;
                        }
                        int h = contracted.getMeasuredHeight();
                        if (h > 0 && h != cv.getMeasuredHeight()) {
                            XposedHelpers.callMethod(cv, "setMeasuredDimension",
                                    cv.getMeasuredWidth(), h);
                        }
                    } catch (Throwable ignored) {
                    }
                }
            });
            XposedBridge.hookAllMethods(contentView, "setHeights", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    if (!RESTYLED.containsKey(param.thisObject) || param.args.length < 1
                            || !(param.args[0] instanceof Integer)) {
                        return;
                    }
                    View v = (View) param.thisObject;
                    int min = NotifyRowSpec.dp(v.getContext(), COLLAPSED_MAX_DP);
                    if ((Integer) param.args[0] < min) {
                        param.args[0] = min;
                    }
                }
            });
            XC_MethodHook hideIcons = new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    try {
                        Object v = XposedHelpers.getObjectField(param.thisObject, "mView");
                        if (v instanceof View && ((View) v).findViewWithTag(TAG_ICON) != null) {
                            hideSmallIcons((View) v);
                            disableHeaderTouch((View) v);
                        }
                    } catch (Throwable ignored) {
                    }
                }
            };
            String[] wrappers = {
                    "NotificationHeaderViewWrapper",
                    "NotificationTemplateViewWrapper",
                    "NotificationBigTextTemplateViewWrapper",
                    "NotificationBigPictureTemplateViewWrapper",
                    "NotificationMessagingTemplateViewWrapper",
                    "NotificationCustomViewWrapper",
                    "NotificationDecoratedCustomViewWrapper",
            };
            for (String name : wrappers) {
                try {
                    Class<?> c = XposedHelpers.findClass(
                            "com.android.systemui.statusbar.notification.row.wrapper." + name, cl);
                    XposedBridge.hookAllMethods(c, "onContentUpdated", hideIcons);
                } catch (Throwable ignored) {
                }
            }
            XC_MethodHook overlayHook = new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    try {
                        Object rootObj = XposedHelpers.getObjectField(param.thisObject, "mView");
                        Object rowObj = XposedHelpers.getObjectField(param.thisObject, "mRow");
                        if (!(rootObj instanceof View) || !(rowObj instanceof View)) {
                            return;
                        }
                        View root = (View) rootObj;
                        ViewParent p = root.getParent();
                        if (!(p instanceof ViewGroup)) {
                            return;
                        }
                        View contentView = (View) p;
                        if (currentContracted(contentView) != root) {
                            return;
                        }
                        applyMessagingOverlay(contentView, root, (View) rowObj);
                    } catch (Throwable t) {
                        Log.w(TAG, "notify_row: overlay hook failed", t);
                    }
                }
            };
            String[] msgWrappers = {
                    "NotificationMessagingTemplateViewWrapper",
                    "NotificationCompactMessagingTemplateViewWrapper",
                    "NotificationConversationTemplateViewWrapper",
            };
            for (String name : msgWrappers) {
                try {
                    Class<?> c = XposedHelpers.findClass(
                            "com.android.systemui.statusbar.notification.row.wrapper." + name, cl);
                    XposedBridge.hookAllMethods(c, "onContentUpdated", overlayHook);
                } catch (Throwable ignored) {
                }
            }
            installUngroupHooks(cl);
            installSafetyHooks(cl);
            installStackMetricsHooks(cl);
            if (OsProfile.isA13OrLower()) {
                installRoundnessHooks(cl);
            }
            installVivoIconGuardHooks(cl);
            installWitnessHooks(cl);
            installShelfCornerHooks(cl);
            installTopLineColumnHook(cl);
            installSelectLayoutTimeHook(cl);
            if (OsProfile.isA13OrLower()) {
                int mirrored = Os5Mirror.install(cl);
                Log.i(TAG, "notify_row: os5 mirror hooks installed=" + mirrored);
            }
            installEarlyInit(cl);
            initUngroupFlag(cl);
            // header 时间的接管方式，门禁据此决定 L10/L11（col measure 那套）该不该跑。
            // 挂在"installed"这一行而不是单开一行：单开的那一行在真机上被 logd 限流整条丢过。
            sModeWitnessAt = android.os.SystemClock.uptimeMillis();
            Log.i(TAG, headerTimeMode());
        } catch (Throwable t) {
            Log.e(TAG, "notify row feature install failed", t);
        }
    }

    /** 逐帧快照的预算与上一帧状态（只在调试键开启时才有非空开销） */
    private static int sTraceBudget;
    private static int sTraceFrame;
    private static long sTraceCheckAt;
    private static final java.util.HashMap<String, String> sTracePrev = new java.util.HashMap<>();

    /** 收集子树里所有某个类名的视图（一行可能有收起/展开两份，各带一颗 CachingIconView）。 */
    private static void collectClass(View v, String simpleName,
                                     java.util.List<View> out, int depth) {
        if (v == null || depth > 8 || out.size() > 4) {
            return;
        }
        if (v.getClass().getSimpleName().equals(simpleName)) {
            out.add(v);
        }
        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) {
                collectClass(g.getChildAt(i), simpleName, out, depth + 1);
            }
        }
    }

    /** 从某个视图往下找我们那枚带标记的大图标（找不到返回 null，不抛）。 */
    private static View findTag(View v) {
        return v instanceof ViewGroup ? ((ViewGroup) v).findViewWithTag(TAG_LIGHT_BIG) : null;
    }

    /**
     * 行内的**视觉**左偏移：逐级累加 `getLeft() + translationX`，够不到 row 返回 -1。
     * 不用 getLocationOnScreen —— 展开动画期祖先自己带位移，会把祖先的位移算成时间的位移；
     * 也不能只累加 getLeft —— push-only 那套就是用 translationX 把时间推过去的。
     */
    private static int rowVisualLeft(View row, View v) {
        int x = 0;
        View cur = v;
        for (int i = 0; i < 12 && cur != null && cur != row; i++) {
            x += cur.getLeft() + Math.round(cur.getTranslationX());
            ViewParent p = cur.getParent();
            cur = p instanceof View ? (View) p : null;
        }
        return cur == row ? x : -1;
    }

    /** 一棵子树里**所有**指定 id 后缀的视图（一行同时挂着收起/展开两份 header，只取第一份会量到隐藏的）。 */
    private static void collectIdSuffix(View v, String suffix,
                                        java.util.List<View> out, int depth) {
        if (v == null || depth > 12 || out.size() > 4) {
            return;
        }
        if (v.getId() != 0 && viewIdName(v).endsWith(":id/" + suffix)) {
            out.add(v);
        }
        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) {
                collectIdSuffix(g.getChildAt(i), suffix, out, depth + 1);
            }
        }
    }

    /** 每行一帧的只读快照：大图标在不在/alpha、原生小图标可见性与 alpha、行高、当前显示哪一份根。 */
    private static void traceFrame(ViewGroup list) {
        Context ctx = list.getContext();
        long now = android.os.SystemClock.uptimeMillis();
        if (sTraceBudget <= 0) {
            // 预算耗尽后每秒才看一次开关，别每帧读 settings
            if (now - sTraceCheckAt < 1000) {
                return;
            }
            sTraceCheckAt = now;
            if (traceRequested(ctx)) {
                sTraceBudget = 240;          // ≈4 秒 @60fps
                sTraceFrame = 0;
                sTracePrev.clear();
                Log.i(TAG, "notify_row.trace arm frames=" + sTraceBudget);
            } else {
                return;
            }
        }
        sTraceBudget--;
        sTraceFrame++;
        StringBuilder sum = new StringBuilder();
        for (int i = 0; i < list.getChildCount(); i++) {
            View child = list.getChildAt(i);
            if (child == null || !child.getClass().getSimpleName()
                    .contains("ExpandableNotificationRow")) {
                continue;
            }
            String id = Integer.toHexString(System.identityHashCode(child));
            try {
                // 对照三种查找入口，看到底哪一层能找到我们那枚大图标：
                // ①行本身 ②行自己的 contentView 的可见根 ③那份收起态根。
                // polish 用的是 ②/③，所以 ① 找不到不代表图标不在屏上 —— 先把这点弄清楚再谈闪现
                View cv = contentViewIn(child);
                View shownRoot = cv == null ? null : visibleRootOf(cv);
                View conRoot = cv == null ? null : currentContracted(cv);
                View big = findTag(child);
                String where = "row";
                if (big == null) {
                    big = shownRoot == null ? null : shownRoot.findViewWithTag(TAG_LIGHT_BIG);
                    where = "shown";
                }
                if (big == null) {
                    big = conRoot == null ? null : conRoot.findViewWithTag(TAG_LIGHT_BIG);
                    where = "contracted";
                }
                if (big == null) {
                    where = "none";
                }
                View nat = big == null ? null : LIGHT_NATIVE_ICON.get(
                        "row".equals(where) ? child : ("shown".equals(where) ? shownRoot : conRoot));
                if (nat == null && big != null && !sTracePrev.isEmpty()) {
                    nat = findClass(child, "CachingIconView");
                }
                // 一行里可能同时存在**两颗** CachingIconView（收起/展开两份根各一颗）。
                // 到底是哪颗在被画，决定我们该按哪颗 —— 所以两份状态都记下来。
                java.util.ArrayList<View> all = new java.util.ArrayList<>(2);
                collectClass(child, "CachingIconView", all, 0);
                StringBuilder icons = new StringBuilder();
                for (int k = 0; k < all.size() && k < 3; k++) {
                    View v = all.get(k);
                    icons.append(k == 0 ? "" : ",")
                            .append(Integer.toHexString(System.identityHashCode(v)))
                            .append(':').append(v.getVisibility())
                            .append(':').append(String.format(
                                    java.util.Locale.US, "%.2f", v.getAlpha()))
                            .append(":s").append(String.format(
                                    java.util.Locale.US, "%.2f", v.getScaleX()))
                            // d=有没有 drawable：没有东西可画时，vivo 再怎么抬 alpha 也闪不出来
                            .append(":d").append(v instanceof ImageView
                                    && ((ImageView) v).getDrawable() != null ? "1" : "0")
                            // da = drawable 自己的 alpha：0 就是"有图但画不出来"，
                            // 这一项为 0 时 vivo 抬视图属性也不会闪
                            .append(":da").append(v instanceof ImageView
                                    && ((ImageView) v).getDrawable() != null
                                    ? String.valueOf(((ImageView) v).getDrawable().getAlpha())
                                    : "-")
                            .append(v == nat ? "*" : "");
                }
                // 时间的逐帧落点：header 时间在过渡帧会掉进图标列，这是 1Hz 度量量不到的
                // 那一类（16ms 级）。一行有收起/展开两份 header，两份都量，谁在屏上谁算。
                java.util.ArrayList<View> times = new java.util.ArrayList<>(3);
                collectIdSuffix(child, "time", times, 0);
                StringBuilder tms = new StringBuilder();
                for (int k = 0; k < times.size() && k < 3; k++) {
                    View t = times.get(k);
                    int tx = rowVisualLeft(child, t);
                    tms.append(k == 0 ? "" : ",")
                            .append(Integer.toHexString(System.identityHashCode(t)))
                            .append(":L").append(tx < 0 ? "-" : String.valueOf(tx))
                            .append(":W").append(t.getWidth())
                            .append(":a").append(String.format(
                                    java.util.Locale.US, "%.2f", t.getAlpha()))
                            .append(":v").append(t.getVisibility())
                            .append(":s").append(t.isShown() ? "1" : "0");
                }
                // 大图标占的横向区间：门禁判"时间不许压在图标上"，比的是这两段真实几何
                int bigL = big == null ? -1 : rowVisualLeft(child, big);
                String bigGeom = big == null ? "-"
                        : "L" + bigL + ":W" + big.getWidth() + ":s"
                        + (big.isShown() ? "1" : "0") + ":v" + big.getVisibility()
                        + ":d" + (big instanceof ImageView
                                && ((ImageView) big).getDrawable() != null ? "1" : "0")
                        + ":a" + String.format(
                        java.util.Locale.US, "%.2f", big.getAlpha());
                java.util.ArrayList<View> ivl = new java.util.ArrayList<>(3);
                collectIconViews(child, ivl, 0);
                StringBuilder ivs = new StringBuilder();
                for (int k = 0; k < ivl.size() && k < 3; k++) {
                    View iv = ivl.get(k);
                    String nm = viewIdName(iv);
                    int sl = nm.lastIndexOf('/');
                    ivs.append(k == 0 ? "" : ",")
                            .append(iv.getClass().getSimpleName()).append('/')
                            .append(sl >= 0 ? nm.substring(sl + 1) : "-")
                            .append(':').append(iv.getVisibility())
                            .append(':').append(String.format(
                                    java.util.Locale.US, "%.2f", iv.getAlpha()))
                            .append(":da").append(iv instanceof ImageView
                                    && ((ImageView) iv).getDrawable() != null
                                    ? String.valueOf(((ImageView) iv).getDrawable().getAlpha())
                                    : "-");
                }
                String state = (big == null ? "-" : (where + ":A" + String.format(
                        java.util.Locale.US, "%.2f", big.getAlpha())))
                        + "/" + (nat == null ? "-" : (nat.getVisibility()
                        + ":" + String.format(java.util.Locale.US, "%.2f", nat.getAlpha())))
                        + "/icons=" + icons
                        + "/big=" + bigGeom
                        + "/ivs=" + ivs
                        + "/time=" + tms
                        + "/h" + child.getHeight()
                        + "/v" + child.getVisibility()
                        + "/ty" + (int) child.getTranslationY();
                if (state.equals(sTracePrev.get(id))) {
                    continue;      // 与上一帧一致 ⇒ 不打，否则 240 帧会把别的见证挤出缓冲区
                }
                sTracePrev.put(id, state);
                // 逐帧路径里**不做反射取包名**（那是热路径）；id 与包名的对账用 1Hz 那批 witness
                sum.append("\n  f=").append(sTraceFrame).append(" id=").append(id)
                        .append(" big/nat=").append(state);
            } catch (Throwable ignored) {
            }
        }
        if (sum.length() > 0) {
            Log.i(TAG, "notify_row.trace" + sum);
        }
        if (sTraceBudget == 0) {
            Log.i(TAG, "notify_row.trace done frames=" + sTraceFrame
                    + " rows=" + sTracePrev.size());
            sTracePrev.clear();
        }
    }

    private static boolean traceRequested(Context ctx) {
        try {
            return android.provider.Settings.Global.getInt(
                    ctx.getContentResolver(), "betteroriginos_trace_flicker", 0) == 1;
        } catch (Throwable t) {
            return false;
        }
    }

    /**
     * 现场见证：列表级单趟遍历，记录屏幕上**当前这一帧真实存在**的行实例与呈现状态。
     * 逐行度量用的是绑定时捕获的视图引用，行一旦换实例，那份快照就是陈旧的 ——
     * 之前"收起后精确回到基线"的假绿正源于此。真值只能从屏上取，校验器再用 rowId 对账。
     */
    private static void installWitnessHooks(ClassLoader cl) {
        try {
            Class<?> nsnl = XposedHelpers.findClass(
                    "com.android.systemui.statusbar.notification.stack.NotificationStackScrollLayout", cl);
            XposedBridge.hookAllMethods(nsnl, "dispatchDraw", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    try {
                        if (!(param.thisObject instanceof ViewGroup)) {
                            return;
                        }
                        // 亚秒级的"闪"用 1Hz 的度量采样是量不到的（本轮就是这么把假设
                        // 一条条证伪后卡住的）。这个调试键开启一次**逐帧**只读快照：
                        // 只在状态相对上一帧变了的那一行打一条，预算用完自动停。
                        traceFrame((ViewGroup) param.thisObject);
                        long now = android.os.SystemClock.uptimeMillis();
                        if (now - sLastWitnessAt < 1000) {
                            return;
                        }
                        sLastWitnessAt = now;
                        ViewGroup list = (ViewGroup) param.thisObject;
                        healLightRows(list);
                        if (!sVerifyEnabled) {
                            return;
                        }
                        // 圆角快照：只靠切 sep 触发的话，验收脚本在稳态面板里取不到读数
                        if (now - sLastRoundnessDumpAt > 15000) {
                            sLastRoundnessDumpAt = now;
                            dumpRoundness(list, "live");
                        }
                        StringBuilder tree = new StringBuilder();
                        for (int i = 0; i < list.getChildCount(); i++) {
                            View child = list.getChildAt(i);
                            tree.append(' ').append(i).append(':')
                                    .append(child.getClass().getName())
                                    .append("(t").append(child.getTop())
                                    .append(",h").append(child.getHeight())
                                    .append(",ty").append((int) child.getTranslationY())
                                    .append(",sy").append(String.format(
                                            java.util.Locale.US, "%.2f", child.getScaleY()))
                                    .append(",a").append(String.format(
                                            java.util.Locale.US, "%.2f", child.getAlpha()))
                                    .append(",v").append(child.getVisibility()).append(')');
                            if (child.getClass().getSimpleName()
                                    .contains("ExpandableNotificationRow")) {
                                logWitness(child);
                            }
                        }
                        Log.i(TAG, "notify_row.witness.list n=" + list.getChildCount()
                                + " h=" + list.getHeight() + " scroll=" + list.getScrollY()
                                + " sy=" + String.format(java.util.Locale.US, "%.2f",
                                        list.getScaleY())
                                + " kids=" + tree);
                    } catch (Throwable t) {
                        Log.w(TAG, "notify_row: witness/heal failed", t);
                    }
                }
            });
        } catch (Throwable t) {
            Log.w(TAG, "notify_row: witness hook failed", t);
        }
    }

    /**
     * 自愈补扫：A13 的部分行（vivo 分组摘要、系统服务行）不走
     * NotificationContentView.setContractedChild/setExpandedChild，restyle 从未被调用，
     * 于是一直没有 42dp 大图标。列表每帧只做一次（1s 节流），未处理的行 post 出去补一次；
     * 已处理的行由 RESTYLED_ROWS 记住，不会重复。
     */
    private static void healLightRows(ViewGroup list) {
        try {
            // 接管方式按低频重发（补扫本身 1s 一趟）：门禁拿不到这一行就只能判红，
            // 而它只在进程启动那一拍打一次时会被 logd 的每进程限流整条冲掉。
            long now = android.os.SystemClock.uptimeMillis();
            if (now - sModeWitnessAt > 5000) {
                sModeWitnessAt = now;
                Log.i(TAG, headerTimeMode());
            }
            // 布局期对位的实测落点也按低频重发：一次切歌/重排会连打几十条，logd 的每进程
            // 限流会把整批一起冲掉，门禁于是读不到 col measure 只能报"钩子没跑"（分不清）。
            if (now - sColBeatAt > 5000) {
                sColBeatAt = now;
                String beat = sColBeat;
                Log.i(TAG, "notify_row.col calls=" + sColHookCalls);
                if (beat != null) {
                    Log.i(TAG, "notify_row." + beat);
                }
            }
            for (int i = 0; i < list.getChildCount(); i++) {
                View child = list.getChildAt(i);
                if (child == null || !child.getClass().getSimpleName()
                        .contains("ExpandableNotificationRow")) {
                    continue;
                }
                healLightRow(child);
                // 组子行要单独遍历：它们既可能漏绑，也不走圆角钩子
                maintainNestedRows(child, 0);
            }
        } catch (Throwable ignored) {
        }
    }

    /**
     * 组子行既不走 contentView 绑定（父行展开时系统不重新调 setContractedChild），
     * 圆角也由父行 applyChildrenRoundness 直接下发（不经过 getRoundnessFraction），
     * 所以补扫必须自己遍历到它们：漏绑的补一次 polish，文本列做一次收敛。
     */
    private static void maintainNestedRows(View v, int depth) {
        if (depth > 3 || !(v instanceof ViewGroup)) {
            return;
        }
        ViewGroup g = (ViewGroup) v;
        for (int i = 0; i < g.getChildCount(); i++) {
            View c = g.getChildAt(i);
            if (c == null) {
                continue;
            }
            if (c != v && c.getClass().getName().endsWith(".ExpandableNotificationRow")
                    && !bool(c, "isSummaryWithChildren")) {
                healLightRow(c);
                maybeConvergeHeader(c);
            }
            maintainNestedRows(c, depth + 1);
        }
    }

    private static final WeakHashMap<View, Integer> HEAL_TRIES = new WeakHashMap<>();

    private static void healLightRow(final View row) {
        try {
            if (!row.isShown() || bool(row, "isRemoved")) {
                return;
            }
            Context ctx = row.getContext();
            if (!isEnabledStatic(ctx) || !isLightStyle(ctx)) {
                return;
            }
            final View cv = contentViewIn(row);
            if (bool(row, "isSummaryWithChildren")) {
                // 分组摘要保持原生小图标：42dp 塞进 44dp 的 header 会变成"大头"，
                // 用户明确否决。要放大图标的是展开后的子行（走下面的常规 polish）。
                return;
            }
            if (cv == null) {
                lightSkip("heal-nocv", row, null);
                return;
            }
            // 只认 light 自己的完成标记：RESTYLED 是 iOS 卡那套布局钩子的开关，
            // 在 light 路径里永远不会被写，所以"补扫每秒重跑 polish"就是这么来的。
            if (LIGHT_POLISHED.containsKey(cv) || RESTYLED.containsKey(cv)) {
                // 标记说的是"这一行已经按大图标排好"。vivo 收起再展开子行时会换一份可见子树
                // （或把图标连同旧根一起摘掉），标记留在 cv 上就成了谎话，而补扫只认标记 ——
                // 现象正是用户报的"展开时大图标离奇消失并且再也回不来"（真机见证
                // notify_row.heal skip reason=marked-empty h=255 vis=0，稳定 8s 后仍未恢复）。
                View shown = visibleRootOf(cv);
                if (shown != null && shown.findViewWithTag(TAG_LIGHT_BIG) != null) {
                    // 标记与屏幕上那一份一致：大图标在。但 A13 一行有**收起/展开两份根**，
                    // 而 LIGHT_POLISHED 的键是 contentView —— 只要其中一份排好就算"这行做完了"，
                    // 另一份的原生小图标从来没被按住。展开动画把可见的那一份换过去时，
                    // 用户看到的就是"某条通知的小图标闪一下"（切歌引发整列表重排时尤其明显）。
                    // 所以两份都查、都按：幂等写，已经是 0 的那份一个字节都不动。
                    pressStrayNativeIcon(shown, "shown");
                    View other = (shown == currentContracted(cv))
                            ? currentExpanded(cv) : currentContracted(cv);
                    if (other != null) {
                        pressStrayNativeIcon(other, "twin");
                    }
                    return;
                }
                witnessMarkedButEmpty(row, cv, shown);
                if (!rearmStalePolish(cv, shown)) {
                    return;      // 这个根已经给过一次重排机会；还没有图标就是"这行不该有图标"，
                                 // 继续清标记就变成每秒重跑的打环
                }
            }
            synchronized (HEAL_TRIES) {
                Integer n = HEAL_TRIES.get(row);
                int tries = n == null ? 0 : n.intValue();
                if (tries >= 6) {
                    // 上限放宽到 6：子行可能在"还没长高"时先来几轮，不能被 3 次用完就永久放弃
                    lightSkip("heal-tried", row, "tries=" + tries);
                    return;
                }
                HEAL_TRIES.put(row, tries + 1);
                // 每行只记一次：补扫本身每秒都跑，逐次打会把别的见证挤出缓冲区
                if (isVerify(ctx) && tries == 0) {
                    Log.i(TAG, "notify_row.heal try=" + (tries + 1)
                            + " pkg=" + (sbnOf(row) == null ? "-" : sbnOf(row).getPackageName())
                            + " child=" + (bool(row, "isChildInGroup") ? 1 : 0)
                            + " vis=" + row.getVisibility());
                }
            }
            View root = visibleRootOf(cv);
            if (root == null) {
                return;
            }
            final View target = root;
            row.post(safe(() -> {
                try {
                    if (!LIGHT_POLISHED.containsKey(cv) && !RESTYLED.containsKey(cv)) {
                        restyle(cv, target);
                    }
                } catch (Throwable ignored) {
                }
            }));
        } catch (Throwable ignored) {
        }
    }

    private static void logWitness(View row) {
        StringBuilder sb = new StringBuilder();
        sb.append(" id=").append(Integer.toHexString(System.identityHashCode(row)));
        StatusBarNotification sbn = sbnOf(row);
        sb.append(" pkg=").append(sbn == null ? "-" : sbn.getPackageName());
        sb.append(" sum=").append(bool(row, "isSummaryWithChildren") ? 1 : 0);
        sb.append(" exp=").append(bool(row, "isGroupExpanded") ? 1 : 0);
        sb.append(" child=").append(bool(row, "isChildInGroup") ? 1 : 0);
        sb.append(" ty=").append((int) row.getTranslationY());
        sb.append(" h=").append(row.getHeight());
        sb.append(" vis=").append(row.getVisibility());
        try {
            View bg = findClass(row, "NotificationBackgroundView");
            if (bg != null) {
                int[] rl = new int[2];
                int[] bl = new int[2];
                row.getLocationOnScreen(rl);
                bg.getLocationOnScreen(bl);
                sb.append(" rowY=").append(rl[1])
                        .append(" bgY=").append(bl[1])
                        .append(" bgH=").append(bg.getHeight())
                        .append(" bgVis=").append(bg.getVisibility())
                        .append(" bgA=").append(bg.getAlpha());
            }
        } catch (Throwable ignored) {
        }
        IosNotificationRow card = STACK_CARDS.get(row);
        sb.append(" card=").append(card == null ? 0 : 1);
        sb.append(" cardH=").append(card == null ? -1 : card.getHeight());
        sb.append(" cardVis=").append(card == null ? -1 : card.getVisibility());
        if (card != null) {
            sb.append(" rowMH=").append(row.getMeasuredHeight())
                    .append(" dims=").append(card.dumpDims())
                    .append(" ch=").append(card.contentHeight(card.getWidth() > 0
                            ? card.getWidth() : row.getWidth()));
        }
        try {
            Object container = XposedHelpers.callMethod(row, "getChildrenContainer");
            if (container instanceof ViewGroup) {
                ViewGroup g = (ViewGroup) container;
                sb.append(" contV=").append(g.getVisibility())
                        .append(" contH=").append(g.getHeight())
                        .append(" contN=").append(g.getChildCount());
            }
        } catch (Throwable ignored) {
        }
        // 收起态动作区现场状态（OS3 的容器是 android:id/actions_container）：
        // 逐个按钮打 vis/height/alpha，才能分清"原生没摆"还是"被我们压透明"
        View acts = findActionContainer(row, 0);
        if (acts != null) {
            sb.append(" act=").append(acts.getVisibility()).append('x').append(acts.getHeight())
                    .append('/').append(acts instanceof ViewGroup
                            ? ((ViewGroup) acts).getChildCount() : -1);
            if (acts instanceof ViewGroup) {
                ViewGroup ag = (ViewGroup) acts;
                for (int i = 0; i < ag.getChildCount(); i++) {
                    View b = ag.getChildAt(i);
                    sb.append(" b").append(i).append(':').append(b.getClass().getSimpleName())
                            .append("(v").append(b.getVisibility())
                            .append(",h").append(b.getHeight())
                            .append(",a").append((int) (b.getAlpha() * 100)).append(')');
                }
            }
        }
        // 行高明显大于卡片时，把行内每个子 View 的尺寸摊开：直接看空白被谁占着
        if (card != null && row instanceof ViewGroup && row.getHeight() > card.getHeight() + 8) {
            ViewGroup g = (ViewGroup) row;
            sb.append(" <<");
            for (int i = 0; i < g.getChildCount(); i++) {
                View c = g.getChildAt(i);
                sb.append(' ').append(i).append(':').append(c.getClass().getSimpleName())
                        .append("(v").append(c.getVisibility())
                        .append(",h").append(c.getHeight())
                        .append(",mh").append(c.getMeasuredHeight())
                        .append(",ty").append((int) c.getTranslationY()).append(')');
            }
            sb.append(">>");
        }
        Log.i(TAG, "notify_row.witness" + sb);
    }

    /** 收起/展开两套模板都改造，避免点箭头后掉回 OS5 样式 */
    private static void scheduleRestyle(final View contentView, final View root,
                                        final int mode) {
        Log.i(TAG, "notify_row: schedule mode=" + mode + " tag=" + root.getTag()
                + " cls=" + root.getClass().getSimpleName());
        contentView.post(() -> {
            try {
                View current = mode == 1 ? currentExpanded(contentView)
                        : mode == 2 ? currentHeadsUp(contentView)
                        : currentContracted(contentView);
                if (current != root) {
                    Log.i(TAG, "notify_row: restyle skipped (child changed) mode=" + mode);
                    return;
                }
                restyle(contentView, root, mode == 2);
            } catch (Throwable t) {
                Log.e(TAG, "notify_row restyle failed", t);
            }
        });
    }

    /**
     * 关闭 OS5 的分组"应用类别卡"：让 isGroup() 恒为 false（媒体除外），
     * 并从渲染列表移除 group summary。这样应用自定义分组和系统 ranker_group 的子项都
     * 各自成为独立卡片（iOS 堆叠），也不会留下空的组摘要行。
     */
    private static void installUngroupHooks(ClassLoader cl) {
        try {
            Class<?> sbn = XposedHelpers.findClass(
                    "android.service.notification.StatusBarNotification", cl);
            XposedBridge.hookAllMethods(sbn, "isGroup", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    if (sUngroupEnabled && param.thisObject instanceof StatusBarNotification
                            && shouldUngroup((StatusBarNotification) param.thisObject)) {
                        param.setResult(false);
                    }
                }
            });
            // 只改 isGroup，不动 getGroupKey：返回 null 会让 GroupMembershipManager /
            // HeadsUpCoordinator 在批量移除（一键清除）时拿空 key 查表直接 NPE 崩溃循环
            // （OS5 实测崩在 HeadsUpCoordinator.onBeforeFinalizeFilter；OS3/A13 同一个方法
            //   (Ljava/util/List;)V 也在，两边都不能碰。拆组只需要 isGroup=false）
        } catch (Throwable t) {
            Log.w(TAG, "notify_row: hook isGroup failed", t);
        }
        try {
            Class<?> builder = XposedHelpers.findClass(
                    "com.android.systemui.statusbar.notification.collection.ShadeListBuilder", cl);
            XposedBridge.hookAllMethods(builder, "applyFilters", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    if (!sVerifyEnabled || !Boolean.TRUE.equals(param.getResult())
                            || param.args == null || param.args.length < 3
                            || !(param.args[2] instanceof List)) {
                        return;
                    }
                    try {
                        Object entry = param.args[0];
                        String key = String.valueOf(
                                XposedHelpers.callMethod(entry, "getKey"));
                        for (Object filter : (List<?>) param.args[2]) {
                            Object filtered = XposedHelpers.callMethod(filter,
                                    "shouldFilterOut", entry, param.args[1]);
                            if (Boolean.TRUE.equals(filtered)) {
                                String name;
                                try {
                                    name = String.valueOf(
                                            XposedHelpers.callMethod(filter, "getName"));
                                } catch (Throwable ignored) {
                                    name = filter.getClass().getName();
                                }
                                Log.i(TAG, "notify_row.filter name=" + name
                                        + " cls=" + filter.getClass().getName()
                                        + " key=" + key);
                                break;
                            }
                        }
                    } catch (Throwable ignored) {
                    }
                }
            });
            XposedBridge.hookAllMethods(builder, "filterNotifs", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    if (!sUngroupEnabled || param.args == null || param.args.length < 2) {
                        return;
                    }
                    filterGroupSummaries(param.args[1]);
                    flattenGroups(param.args[1]);
                    if (sVerifyEnabled) {
                        logStack(param.args[1]);
                    }
                }
            });
        } catch (Throwable t) {
            Log.w(TAG, "notify_row: hook filterNotifs failed", t);
        }
    }

    @SuppressWarnings("unchecked")
    private static void filterGroupSummaries(Object listObject) {
        if (!(listObject instanceof List)) {
            return;
        }
        try {
            List<Object> list = (List<Object>) listObject;
            Iterator<Object> it = list.iterator();
            while (it.hasNext()) {
                StatusBarNotification sbn = sbnOfEntry(it.next());
                if (sbn == null || !isGroupSummary(sbn) || isMediaSbn(sbn)) {
                    continue;
                }
                // 分组摘要不再需要：子项靠 isGroup()=false + flattenGroups 拆平成独立卡
                it.remove();
            }
        } catch (Throwable t) {
            Log.w(TAG, "notify_row: filter group summary failed", t);
        }
    }

    private static volatile Object sGroupRootEntry;

    /** 把遗留的 GroupEntry 扁平化：summary 有子项则丢弃，子项全部提升为顶层卡片 */
    private static void ensureGroupRoot() {
        if (sGroupRootEntry != null) {
            return;
        }
        try {
            Class<?> groupEntry = XposedHelpers.findClass(
                    "com.android.systemui.statusbar.notification.collection.GroupEntry",
                    sClassLoader);
            sGroupRootEntry = XposedHelpers.getStaticObjectField(groupEntry, "ROOT_ENTRY");
        } catch (Throwable t) {
            Log.w(TAG, "notify_row: resolve ROOT_ENTRY failed", t);
        }
    }

    @SuppressWarnings("unchecked")
    private static void flattenGroups(Object listObject) {
        if (!(listObject instanceof List)) {
            return;
        }
        try {
            ensureGroupRoot();
            if (sGroupRootEntry == null) {
                return;
            }
            List<Object> list = (List<Object>) listObject;
            List<Object> out = new java.util.ArrayList<>();
            for (Object o : list) {
                if (o == null || !o.getClass().getName().contains("GroupEntry")) {
                    if (o != null) {
                        try {
                            Object parent = XposedHelpers.callMethod(o, "getParent");
                            if (parent != null && parent != sGroupRootEntry) {
                                XposedHelpers.callMethod(o, "setParent", sGroupRootEntry);
                            }
                        } catch (Throwable ignored) {
                        }
                        out.add(o);
                    }
                    continue;
                }
                Object children = null;
                Object summary = null;
                try {
                    children = XposedHelpers.callMethod(o, "getRawChildren");
                } catch (Throwable ignored) {
                }
                if (!(children instanceof List) || ((List<?>) children).isEmpty()) {
                    try {
                        children = XposedHelpers.callMethod(o, "getChildren");
                    } catch (Throwable ignored) {
                    }
                }
                try {
                    summary = XposedHelpers.callMethod(o, "getSummary");
                } catch (Throwable ignored) {
                }
                StatusBarNotification summarySbn = summary == null ? null
                        : sbnOfEntry(summary);
                if (summarySbn != null && isMediaSbn(summarySbn)) {
                    out.add(o);
                    continue;
                }
                boolean addedChild = false;
                if (children instanceof List) {
                    for (Object child : (List<?>) children) {
                        if (child == null) {
                            continue;
                        }
                        try {
                            XposedHelpers.callMethod(child, "setParent", sGroupRootEntry);
                        } catch (Throwable ignored) {
                        }
                        out.add(child);
                        addedChild = true;
                    }
                }
                if (!addedChild && summary != null) {
                    try {
                        XposedHelpers.callMethod(summary, "setParent", sGroupRootEntry);
                    } catch (Throwable ignored) {
                    }
                    out.add(summary);
                }
            }
            list.clear();
            list.addAll(out);
        } catch (Throwable t) {
            Log.w(TAG, "notify_row: flatten groups failed", t);
        }
    }

    private static void logStack(Object listObject) {
        if (!(listObject instanceof List)) {
            return;
        }
        try {
            int count = 0;
            int summaries = 0;
            int groupEntries = 0;
            for (Object entry : (List<?>) listObject) {
                if (entry.getClass().getSimpleName().contains("GroupEntry")) {
                    groupEntries++;
                    Object children = XposedHelpers.callMethod(entry, "getRawChildren");
                    if (children instanceof List) {
                        count += ((List<?>) children).size();
                    }
                    continue;
                }
                StatusBarNotification sbn = sbnOfEntry(entry);
                if (sbn != null) {
                    count++;
                    if (isGroupSummary(sbn)) {
                        summaries++;
                    }
                }
            }
            Log.i(TAG, "notify_row.stack count=" + count + " summaries=" + summaries
                    + " groupEntries=" + groupEntries);
        } catch (Throwable ignored) {
        }
    }

    private static StatusBarNotification sbnOfEntry(Object entry) {
        try {
            Object sbn = XposedHelpers.callMethod(entry, "getSbn");
            return sbn instanceof StatusBarNotification ? (StatusBarNotification) sbn : null;
        } catch (Throwable t) {
            return null;
        }
    }

    private static boolean isGroupSummary(StatusBarNotification sbn) {
        Notification n = sbn.getNotification();
        return n != null && (n.flags & Notification.FLAG_GROUP_SUMMARY) != 0;
    }

    /** 媒体通知/媒体组件交给 MediaCardFeature，不参与拆组 */
    private static boolean isMediaSbn(StatusBarNotification sbn) {
        if (sbn == null) {
            return false;
        }
        String pkg = sbn.getPackageName();
        if ("com.vivo.musicwidgetmix".equals(pkg) || "com.android.bbkmusic".equals(pkg)) {
            return true;
        }
        Notification n = sbn.getNotification();
        if (n == null) {
            return false;
        }
        if (Notification.CATEGORY_TRANSPORT.equals(n.category)) {
            return true;
        }
        return n.extras != null
                && n.extras.getParcelable(Notification.EXTRA_MEDIA_SESSION) != null;
    }

    private static boolean shouldUngroup(StatusBarNotification sbn) {
        return sbn != null && !isMediaSbn(sbn);
    }

    /** 时间右移后，若仍有分组逻辑走到 sanitizeTopLine，跳过而不是 NPE；同时禁止 OS5 展开 */
    private static void installSafetyHooks(ClassLoader cl) {
        try {
            Class<?> grouping = XposedHelpers.findClass(
                    "com.android.systemui.statusbar.NotificationGroupingUtil", cl);
            XposedBridge.hookAllMethods(grouping, "sanitizeTopLine", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    if (param.args == null || param.args.length < 1
                            || !(param.args[0] instanceof ViewGroup)) {
                        return;
                    }
                    ViewGroup topLine = (ViewGroup) param.args[0];
                    try {
                        int timeId = topLine.getResources().getIdentifier(
                                "time", "id", "android");
                        if (timeId != 0 && topLine.findViewById(timeId) == null) {
                            param.setResult(null);
                            return;
                        }
                    } catch (Throwable ignored) {
                    }
                }
            });
        } catch (Throwable t) {
            Log.w(TAG, "notify_row: hook sanitizeTopLine failed", t);
        }
        try {
            Class<?> row = XposedHelpers.findClass(
                    "com.android.systemui.statusbar.notification.row.ExpandableNotificationRow", cl);
            // 组的展开状态有三个入口（isGroupExpanded 读的是 mGroupExpanded），必须一起拦
            XC_MethodHook rejectExpand = new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    if (param.args == null || param.args.length < 1
                            || !Boolean.TRUE.equals(param.args[0])) {
                        return;
                    }
                    if (STACK_ROWS.containsKey(param.thisObject)) {
                        // 堆叠行的展开完全由卡片自绘：借组展开会牵动高度动画/子容器/背景尺寸，
                        // 产生半展开混合态，所以 AOSP 侧任何展开都吃掉
                        if (sVerifyEnabled) {
                            Log.i(TAG, "notify_row: stack expand rejected "
                                    + param.method.getName() + " args=" + param.args.length
                                    + " marked=" + STACK_USER_EXPANDED.containsKey(param.thisObject));
                        }
                        param.args[0] = false;
                        return;
                    }
                    // 普通 iOS 行禁用展开（展开态会掉回 OS5 模板）。
                    // 例外：OS3 的动作按钮只在展开态（收起时 actions_container 是 INVISIBLE），
                    // 这类行禁展开等于把按钮彻底藏掉。
                    if (RESTYLED_ROWS.containsKey(param.thisObject)
                            && !needsExpandForActions((View) param.thisObject)) {
                        param.args[0] = false;
                    }
                }
            };
            for (String method : new String[]{"setUserExpanded", "setChildrenExpanded"}) {
                XposedBridge.hookAllMethods(row, method, rejectExpand);
            }
            // 组收起时淡出子行的大图标。必须挂在这个入口——它是"系统动手之前"，那一刻图标还挂在
            // 树上。前两版押在子行自己的布局回调/preDraw 与摘要行布局上，真机收起时全是 0 条触发：
            // 子行连同图标被提前拆掉，摘要行则根本不重新布局，所以消失一定是硬切。
            XC_MethodHook collapseFade = new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    if (param.args == null || param.args.length < 1
                            || !(param.thisObject instanceof ViewGroup)) {
                        return;
                    }
                    View self = (View) param.thisObject;
                    boolean open = Boolean.TRUE.equals(param.args[0]);
                    // 只有"确实带子项的摘要行"才处理：系统对每个无子项的行也会调这个方法，
                    // 不加这道判断就会把子行自己的图标淡掉，再被补扫 polish 回来，形成打环。
                    if (!bool(self, "isSummaryWithChildren") || childCountOf(self) <= 0) {
                        return;
                    }
                    if (open) {
                        // 展开：这一路是组开合唯一确定会执行的入口，时间对位也挂在这里。
                        // vivo 会在动画过程中重新布局 header 把我们写的位移抹掉，所以按它的节奏
                        // 再补推三次（150/400/800ms），而不是只推一次然后指望收敛循环。
                        pushTimesIn((ViewGroup) self, 0, "group-open");
                        for (int i = 1; i <= 3; i++) {
                            final ViewGroup g = (ViewGroup) self;
                            final int step = i;
                            self.postDelayed(safe(() -> pushTimesIn(g, 0, "group-open+" + (step * 250))),
                                    step * 250L);
                        }
                        return;
                    }
                    java.util.List<View> icons = new java.util.ArrayList<>(4);
                    collectLightIcons((ViewGroup) param.thisObject, icons, 0);
                    if (sVerifyEnabled && sCollapseWitnesses < 8) {
                        sCollapseWitnesses++;
                        // icons=0 也要打：那说明子行此刻已被移走，得换更早的入口
                        Log.i(TAG, "notify_row.light.collapse via=setChildrenExpanded row="
                                + Integer.toHexString(System.identityHashCode(param.thisObject))
                                + " icons=" + icons.size());
                    }
                    for (View icon : icons) {
                        View host = icon.getParent() instanceof View
                                ? (View) icon.getParent() : null;
                        fadeLightIcon(host, icon, 0f, "children-collapse");
                    }
                }
            };
            Log.i(TAG, "notify_row: children-collapse hook hits="
                    + XposedBridge.hookAllMethods(row, "setChildrenExpanded", collapseFade).size());
            // 组里再展开**单条**通知时，vivo 会把这行的 header 换成另一份布局（文字行变 wrap 宽），
            // 我们按"行右端"算的位移就没了 —— 用户看到时间从右边跑回左边、压住图标。
            // 这里在展开动作之后按 0/150/300/600ms 重推（布局是异步的，一次不够）。
            XC_MethodHook rowExpandTime = new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    if (param.args == null || param.args.length < 1
                            || !Boolean.TRUE.equals(param.args[0])
                            || !(param.thisObject instanceof ViewGroup)) {
                        return;
                    }
                    final ViewGroup self = (ViewGroup) param.thisObject;
                    // 先手：赶在系统这次布局/动画之前把两份 header 的时间都摆好，
                    // 否则会先露一帧"左对齐"再被补推闪回右边（用户报的正是这一下）。
                    pushTimesIn(self, 0, "row-expand");
                    for (int i = 1; i <= 4; i++) {
                        final int step = i;
                        self.postDelayed(safe(() -> pushTimesIn(self, 0, "row-expand+" + (step * 150))),
                                step * 150L);
                    }
                }
            };
            int reHits = 0;
            for (String m : new String[]{"setUserExpanded", "setExpanded"}) {
                reHits += XposedBridge.hookAllMethods(row, m, rowExpandTime).size();
            }
            Log.i(TAG, "notify_row: row-expand time hook hits=" + reHits);
            /**
             * 折叠的堆叠行必须自己定高：StackScrollAlgorithm 用 measureChildExactly 按它缓存的
             * 每行高度反向钉住行高（行内大件全 GONE 也仍是展开时的高度），
             * 于是收起后卡下面留一大片空白。这里在测量出口直接把行高压成卡片高度。
             */
            XposedBridge.hookAllMethods(row, "onMeasure", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    View v = (View) param.thisObject;
                    IosNotificationRow card = STACK_CARDS.get(v);
                    if (card == null) {
                        Integer extra = LAST_ROW_EXTRA.get(v);
                        if (extra != null && extra > 0) {
                            try {
                                XposedHelpers.callMethod(v, "setMeasuredDimension",
                                        v.getMeasuredWidth(), v.getMeasuredHeight() + extra);
                            } catch (Throwable ignored) {
                            }
                        }
                        return;
                    }
                    int cardH = card.contentHeight(
                            card.getMeasuredWidth() > 0 ? card.getMeasuredWidth()
                                    : v.getMeasuredWidth());
                    if (cardH <= 0 || v.getMeasuredHeight() == cardH) {
                        return;
                    }
                    try {
                        XposedHelpers.callMethod(v, "setMeasuredDimension",
                                v.getMeasuredWidth(), cardH);
                    } catch (Throwable ignored) {
                    }
                }
            });
            /**
             * 排版步长取 getIntrinsicHeight()，摘要行的原生实现直接返回子容器高度（折叠也照算），
             * 折叠卡下面因此留出一大段空洞。折叠期间改报卡片自身高度。
             */
            XposedBridge.hookAllMethods(row, "getIntrinsicHeight", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    if (param.args != null && param.args.length > 0) {
                        return;
                    }
                    IosNotificationRow card = STACK_CARDS.get(param.thisObject);
                    if (card == null) {
                        return;
                    }
                    int h = card.contentHeight(card.getWidth() > 0
                            ? card.getWidth() : ((View) param.thisObject).getWidth());
                    if (h > 0) {
                        param.setResult(h);
                    }
                }
            });
        } catch (Throwable t) {
            Log.w(TAG, "notify_row: hook group expand guards failed", t);
        }
    }

    /** 非堆叠的最后一行：在 onMeasure 出口补透明留白高度（历史实验保留，当前不标记） */
    private static final WeakHashMap<View, Integer> LAST_ROW_EXTRA = new WeakHashMap<>();

    /**
     * 原生收纳栏顶角是直角，根因在字节码里：ActivatableNotificationView.applyRoundnessAndInvalidate()
     * 用 getTopCornerRadius()/getBottomCornerRadius() 调 applyBackgroundRoundness(top,bottom)，
     * shelf 的 top 恒为 0 → NotificationBackgroundView.setRadius(0, r) → vivo 的
     * VRoundedCornerDrawable.setCorner(false,false,true,true)（updateBackgroundRadii 里按
     * mCornerRadii[0]/[4] 是否 >0 传布尔）。这里在 setRadius 源头把 shelf 背景的 top 对齐
     * bottom：四角半径同一来源，日夜/滚动/重生动画后的重设都一致，不依赖裁剪或截图调参。
     */
    private static final WeakHashMap<View, Boolean> SHELF_RADIUS_LOGGED = new WeakHashMap<>();
    private static final WeakHashMap<View, Boolean> SHELF_FORCED = new WeakHashMap<>();
    private static final java.util.concurrent.atomic.AtomicBoolean ROUNDNESS_LIGHT_LOGGED =
            new java.util.concurrent.atomic.AtomicBoolean();
    private static final java.util.concurrent.atomic.AtomicBoolean LIGHT_METRICS_LOGGED =
            new java.util.concurrent.atomic.AtomicBoolean();
    private static final WeakHashMap<Object, Boolean> SHELF_DRAWABLE_LOGGED = new WeakHashMap<>();
    private static final WeakHashMap<View, Boolean> SHELF_ENTER_LOGGED = new WeakHashMap<>();
    private static final WeakHashMap<View, Boolean> NSS_SWEEP_LOGGED = new WeakHashMap<>();
    private static final WeakHashMap<View, String> SHELF_DRAW_SIG = new WeakHashMap<>();
    private static final WeakHashMap<View, Boolean> SHELF_DRAW_SEEN = new WeakHashMap<>();
    private static final WeakHashMap<View, String> SHELF_LAST_STATE = new WeakHashMap<>();

    private static void installShelfCornerHooks(ClassLoader cl) {
        try {
            Class<?> bg = XposedHelpers.findClass(
                    "com.android.systemui.statusbar.notification.row.NotificationBackgroundView", cl);
            XposedBridge.hookAllMethods(bg, "setRadius", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    if (!(param.thisObject instanceof View) || param.args == null
                            || param.args.length != 2
                            || !(param.args[0] instanceof Float)
                            || !(param.args[1] instanceof Float)) {
                        return;
                    }
                    View v = (View) param.thisObject;
                    if (!isShelfBackground(v)) {
                        return;
                    }
                    float top = (Float) param.args[0];
                    float bottom = (Float) param.args[1];
                    if (bottom <= 0f || top == bottom) {
                        return;
                    }
                    param.args[0] = bottom;
                    if (sVerifyEnabled && SHELF_RADIUS_LOGGED.put(v, Boolean.TRUE) == null) {
                        Log.i(TAG, "notify_row: shelf radius top " + top + " -> " + bottom
                                + " bg=" + v.getClass().getSimpleName());
                    }
                }
            });
            // 绘制必经点：shelf 每次上屏都会走背景的 onDraw。排版/绑定钩子可能因
            // 类加载时序漏掉（冷启时 shelf 早于钩子 inflate），这里按签名只在半径或
            // drawable 变化时校正一次，不逐帧反射、不触发无效重绘。
            XposedBridge.hookAllMethods(bg, "onDraw", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    if (param.thisObject instanceof View) {
                        enforceShelfCornersOnDraw((View) param.thisObject);
                    }
                }
            });
            Class<?> shelf = XposedHelpers.findClass(
                    "com.android.systemui.statusbar.NotificationShelf", cl);
            XC_MethodHook force = new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    if (param.thisObject instanceof View) {
                        forceShelfRoundness((View) param.thisObject);
                    }
                }
            };
            // shelf 在 SystemUI 启动时就 inflate 好，onFinishInflate 里的 setRadius(0,r)
            // 早于钩子装载；之后没人再调 setRadius。所以对现有实例在每次排版时校正一次。
            XposedBridge.hookAllMethods(shelf, "onFinishInflate", force);
            XposedBridge.hookAllMethods(shelf, "onLayout", force);
            XposedBridge.hookAllMethods(shelf, "bind", force);
            // 兜底：shelf 可能早于本功能钩子 inflate，直接在每个 NSS 排版后点名校正
            Class<?> nss = XposedHelpers.findClass(
                    "com.android.systemui.statusbar.notification.stack"
                            + ".NotificationStackScrollLayout", cl);
            XposedBridge.hookAllMethods(nss, "onLayout", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    if (!(param.thisObject instanceof ViewGroup)) {
                        return;
                    }
                    ViewGroup list = (ViewGroup) param.thisObject;
                    int shelves = 0;
                    for (int i = 0; i < list.getChildCount(); i++) {
                        View child = list.getChildAt(i);
                        if ("NotificationShelf".equals(child.getClass().getSimpleName())) {
                            shelves++;
                            forceShelfRoundness(child);
                        }
                    }
                    if (sVerifyEnabled && NSS_SWEEP_LOGGED.put(list, Boolean.TRUE) == null) {
                        Log.i(TAG, "notify_row: nss sweep children=" + list.getChildCount()
                                + " shelves=" + shelves);
                    }
                }
            });
            Log.i(TAG, "notify_row: shelf corner hook installed");
        } catch (Throwable t) {
            Log.w(TAG, "notify_row: shelf corner hook failed", t);
        }
    }

    /**
     * 直接把 shelf 背景的 mCornerRadii 顶部半径补成底部半径，再让 vivo 的
     * VRoundedCornerDrawable 重新读一次（updateBackgroundRadii）。这是绘制源头，
     * 不依赖 outline 裁剪，日夜、滚动、换主题后由 onLayout 再校正。
     */
    private static void forceShelfRoundness(View shelf) {
        try {
            if (sVerifyEnabled && SHELF_ENTER_LOGGED.put(shelf, Boolean.TRUE) == null) {
                Log.i(TAG, "notify_row: shelf force enter cls=" + shelf.getClass().getName());
            }
            Object bgObj = XposedHelpers.getObjectField(shelf, "mBackgroundNormal");
            if (!(bgObj instanceof View)) {
                return;
            }
            View bg = (View) bgObj;
            float[] radii = (float[]) XposedHelpers.getObjectField(bg, "mCornerRadii");
            if (radii == null || radii.length != 8) {
                return;
            }
            float top = Math.max(radii[0], radii[2]);
            float bottom = Math.max(radii[4], radii[6]);
            if (sVerifyEnabled) {
                String state = top + "/" + bottom;
                if (!state.equals(SHELF_LAST_STATE.put(shelf, state))) {
                    Log.i(TAG, "notify_row: shelf radii state tl=" + radii[0] + " tr=" + radii[2]
                            + " bl=" + radii[4] + " br=" + radii[6]);
                }
            }
            if (bottom <= 0f) {
                return;
            }
            if (top < bottom - 0.5f) {
                boolean dontModify = false;
                try {
                    dontModify = XposedHelpers.getBooleanField(bg, "mDontModifyCorners");
                } catch (Throwable ignored) {
                }
                if (dontModify) {
                    XposedHelpers.setObjectField(bg, "mDontModifyCorners", Boolean.FALSE);
                }
                XposedHelpers.callMethod(bg, "setRadius", bottom, bottom);
                // setRadius 只改字段，不保证宿主重绘：通知行静止时会出现"值对了但画面没跟上"。
                // 与 forceShelfDrawableCorners 里对 drawable 的 invalidateSelf 配合，两边都推一次。
                bg.invalidate();
                if (dontModify) {
                    XposedHelpers.setObjectField(bg, "mDontModifyCorners", Boolean.TRUE);
                }
                if (sVerifyEnabled && SHELF_FORCED.put(bg, Boolean.TRUE) == null) {
                    Log.i(TAG, "notify_row: shelf force radii top=" + top + " -> " + bottom
                            + " dontModify=" + dontModify);
                }
            }
            // mDontModifyCorners 为 true 时 updateBackgroundRadii 会早退，drawable 仍保留
            // 旧的角布尔值；直接按同一半径把真实 drawable 的四角补齐（绘制源头）。
            forceShelfDrawableCorners(bg, bottom);
        } catch (Throwable t) {
            if (sVerifyEnabled) {
                Log.w(TAG, "notify_row: shelf force failed", t);
            }
        }
    }

    private static void forceShelfDrawableCorners(View bg, float radius) {
        Object d = XposedHelpers.getObjectField(bg, "mBackground");
        if (!(d instanceof android.graphics.drawable.Drawable)) {
            return;
        }
        android.graphics.drawable.Drawable dr = (android.graphics.drawable.Drawable) d;
        if (dr instanceof android.graphics.drawable.LayerDrawable) {
            android.graphics.drawable.LayerDrawable ld =
                    (android.graphics.drawable.LayerDrawable) dr;
            for (int i = 0; i < ld.getNumberOfLayers(); i++) {
                android.graphics.drawable.Drawable layer = ld.getDrawable(i);
                applyCornersToDrawable(layer, radius);
            }
        } else {
            applyCornersToDrawable(dr, radius);
        }
    }

    private static void applyCornersToDrawable(android.graphics.drawable.Drawable layer, float radius) {
        if (layer == null) {
            return;
        }
        String cls = layer.getClass().getName();
        try {
            if ("com.originui.core.utils.VRoundedCornerDrawable".equals(cls)) {
                XposedHelpers.callMethod(layer, "setRadius", radius);
                XposedHelpers.callMethod(layer, "setCorner",
                        Boolean.TRUE, Boolean.TRUE, Boolean.TRUE, Boolean.TRUE);
                XposedHelpers.callMethod(layer, "invalidateSelf");
                if (sVerifyEnabled && SHELF_DRAWABLE_LOGGED.put(layer, Boolean.TRUE) == null) {
                    Log.i(TAG, "notify_row: shelf drawable corners all rounded r=" + radius);
                }
            } else if (layer instanceof GradientDrawable) {
                ((GradientDrawable) layer).setCornerRadii(new float[]{
                        radius, radius, radius, radius,
                        radius, radius, radius, radius});
                // GradientDrawable 的 setter 不会自己失效，宿主也不一定再画一次；
                // 不 invalidate 就是"改了但看不到"（与下面统一收尾）
                layer.invalidateSelf();
                if (sVerifyEnabled && SHELF_DRAWABLE_LOGGED.put(layer, Boolean.TRUE) == null) {
                    Log.i(TAG, "notify_row: shelf gd corners rounded r=" + radius);
                }
            } else if (sVerifyEnabled && SHELF_DRAWABLE_LOGGED.put(layer, Boolean.TRUE) == null) {
                // 落到这里的 drawable 我们**没动**：没有这条日志，机型换个背景 drawable 就会静默失效
                Log.i(TAG, "notify_row: shelf drawable unhandled cls=" + cls + " r=" + radius);
            }
        } catch (Throwable t) {
            if (sVerifyEnabled) {
                Log.w(TAG, "notify_row: shelf drawable corner failed cls=" + cls, t);
            }
        }
    }

    /** onDraw 入口的确定性校正：只在 mCornerRadii/drawable 签名变化时执行一次 */
    private static void enforceShelfCornersOnDraw(View bg) {
        if (!isShelfBackground(bg)) {
            return;
        }
        try {
            if (sVerifyEnabled && SHELF_DRAW_SEEN.put(bg, Boolean.TRUE) == null) {
                Log.i(TAG, "notify_row: shelf draw seen parent="
                        + (bg.getParent() == null ? "null"
                        : bg.getParent().getClass().getSimpleName()));
            }
            float[] radii = (float[]) XposedHelpers.getObjectField(bg, "mCornerRadii");
            if (radii == null || radii.length != 8) {
                return;
            }
            float top = Math.max(radii[0], radii[2]);
            float bottom = Math.max(radii[4], radii[6]);
            if (bottom <= 0f) {
                return;
            }
            Object drawable = XposedHelpers.getObjectField(bg, "mBackground");
            String sig = top + "/" + bottom + "/" + System.identityHashCode(drawable);
            if (sig.equals(SHELF_DRAW_SIG.put(bg, sig))) {
                return;
            }
            if (top < bottom - 0.5f) {
                radii[0] = bottom;
                radii[1] = bottom;
                radii[2] = bottom;
                radii[3] = bottom;
                boolean dontModify = false;
                try {
                    dontModify = XposedHelpers.getBooleanField(bg, "mDontModifyCorners");
                } catch (Throwable ignored) {
                }
                if (dontModify) {
                    XposedHelpers.setObjectField(bg, "mDontModifyCorners", Boolean.FALSE);
                }
                try {
                    XposedHelpers.callMethod(bg, "updateBackgroundRadii");
                } finally {
                    if (dontModify) {
                        XposedHelpers.setObjectField(bg, "mDontModifyCorners", Boolean.TRUE);
                    }
                }
                Log.i(TAG, "notify_row: shelf draw fix top=" + top + " -> " + bottom);
            }
            forceShelfDrawableCorners(bg, bottom);
        } catch (Throwable t) {
            if (sVerifyEnabled) {
                Log.w(TAG, "notify_row: shelf draw fix failed", t);
            }
        }
    }

    private static boolean isShelfBackground(View v) {
        ViewParent p = v.getParent();
        for (int i = 0; i < 3 && p instanceof View; i++) {
            if (((View) p).getClass().getSimpleName().equals("NotificationShelf")) {
                return true;
            }
            p = ((View) p).getParent();
        }
        return false;
    }

    /** 必须在第一批通知 pipeline 之前就位，否则会先建出 GroupEntry，后续删 summary 也拆不干净 */
    private static void installEarlyInit(ClassLoader cl) {
        XC_MethodHook init = new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                if (param.thisObject instanceof Context) {
                    initFeature((Context) param.thisObject);
                }
            }
        };
        try {
            Class<?> sysuiApp = XposedHelpers.findClass(
                    "com.android.systemui.SystemUIApplication", cl);
            XposedBridge.hookAllMethods(sysuiApp, "onCreate", init);
        } catch (Throwable t) {
            Log.w(TAG, "notify_row: early init hook failed", t);
        }
        try {
            Class<?> app = XposedHelpers.findClass("android.app.Application", cl);
            XposedBridge.hookAllMethods(app, "onCreate", init);
        } catch (Throwable ignored) {
        }
    }

    private static void initFeature(Context ctx) {
        boolean enabled = Prefs.featureEnabled(ctx, "notify_row", false);
        boolean light = enabled && isLightStyle(ctx);
        boolean stack = enabled && !light && isStackMode(ctx);
        boolean verify = isVerify(ctx);
        sStackMode = stack;
        sUngroupEnabled = enabled && !light && !stack;
        sVerifyEnabled = verify;
        IosNotificationRow.traceFrames = verify;
        registerReverify(ctx);
        registerSepObserver(ctx);
        if (light) {
            Log.i(TAG, "notify_row: light style, native list metrics/spacing");
        }
        Log.i(TAG, "notify_row: init enabled=" + enabled + " stack=" + stack
                + " verify=" + verify);
    }

    private static void initUngroupFlag(final ClassLoader cl) {
        final Handler handler = new Handler(Looper.getMainLooper());
        handler.postDelayed(new Runnable() {
            private int attempts;

            @Override
            public void run() {
                try {
                    Object app = XposedHelpers.callStaticMethod(
                            XposedHelpers.findClass("android.app.ActivityThread", cl),
                            "currentApplication");
                    if (app instanceof Context) {
                        initFeature((Context) app);
                        return;
                    }
                } catch (Throwable ignored) {
                }
                if (++attempts < 20) {
                    handler.postDelayed(this, 500);
                }
            }
        }, 800);
    }

    /** iOS 堆叠参数：16dp 侧边距 / 8dp 间距 / 20dp 圆角（可在功能选项回 OS5） */
    private static int sMetricsApplied;

    /** OS3 light 的 OS5 复刻：off / os5（列表间距由 Os5Mirror 决定，关闭时还原原生值） */
    private static final WeakHashMap<View, Boolean> LIGHT_STACKS = new WeakHashMap<>();
    private static final WeakHashMap<View, Integer> LIGHT_ORIG_GAP = new WeakHashMap<>();
    private static final WeakHashMap<Object, Float> LIGHT_ORIG_ALGO_GAP = new WeakHashMap<>();
    private static volatile String sSepMode;
    private static boolean sSepObserver;

    /** 字段存在才写：A13/A15 的堆叠容器字段名不同，缺字段是正常情况而不是错误 */
    private static void setIntIfPresent(Object target, String field, int value) {
        try {
            XposedHelpers.setIntField(target, field, value);
            sMetricsApplied++;
        } catch (Throwable ignored) {
        }
    }

    private static void setFloatIfPresent(Object target, String field, float value) {
        try {
            XposedHelpers.setFloatField(target, field, value);
            sMetricsApplied++;
        } catch (Throwable ignored) {
        }
    }

    private static void installStackMetricsHooks(ClassLoader cl) {
        try {
            Class<?> stack = XposedHelpers.findClass(
                    "com.android.systemui.statusbar.notification.stack.NotificationStackScrollLayout", cl);
            XC_MethodHook apply = new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    applyStackMetrics((View) param.thisObject);
                }
            };
            XposedBridge.hookAllConstructors(stack, apply);
            XposedBridge.hookAllMethods(stack, "updateSidePadding", apply);
            // vivo 在配置变化（字号/夜间/语言）时会 reinitView() 重读 OS5 资源覆盖参数
            XposedBridge.hookAllMethods(stack, "reinitView", apply);
            XposedBridge.hookAllMethods(stack, "updateCornerRadius", apply);
            XposedBridge.hookAllMethods(stack, "onSystemFilletChanged", apply);
        } catch (Throwable t) {
            Log.w(TAG, "notify_row: stack metrics hook failed", t);
        }
        try {
            Class<?> algo = XposedHelpers.findClass(
                    "com.android.systemui.statusbar.notification.stack.StackScrollAlgorithm", cl);
            XposedBridge.hookAllMethods(algo, "initView", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    try {
                        if (param.args == null || param.args.length < 1
                                || !(param.args[0] instanceof Context)) {
                            return;
                        }
                        Context ctx = (Context) param.args[0];
                        if (!Prefs.featureEnabled(ctx, "notify_row", false)
                                || isLightStyle(ctx)
                                || !"ios".equals(Prefs.option(ctx, "notify_row",
                                "stack_metrics", "ios"))) {
                            return;
                        }
                        XposedHelpers.setFloatField(param.thisObject,
                                "mPaddingBetweenElements",
                                (float) NotifyRowSpec.dp(ctx, 8));
                    } catch (Throwable ignored) {
                    }
                }
            });
        } catch (Throwable t) {
            Log.w(TAG, "notify_row: algorithm metrics hook failed", t);
        }
    }


    private static String sepMode(Context ctx) {
        try {
            return "os5".equals(Prefs.option(ctx, "notify_row", "sep", "off"))
                    ? "os5" : "off";
        } catch (Throwable ignored) {
            return "off";
        }
    }

    /** OS3 的 OS5 复刻：列表间距写 A15 的 7dp，几何（连续角/收纳栏/浮动清除）由 Os5Mirror 负责。 */
    private static void applyLightSeparation(View stack, Context ctx) {
        try {
            if (!OsProfile.isA13OrLower()) {
                return;
            }
            String mode = sepMode(ctx);
            sSepMode = mode;
            LIGHT_STACKS.put(stack, Boolean.TRUE);
            boolean split = "os5".equals(mode) && isLightStyle(ctx);
            Os5Mirror.setEnabled(split);
            if (isDebug(ctx)) {
                Log.i(TAG, "notify_row: os5 mirror enter mode=" + mode + " on=" + split);
            }

            int orig;
            Integer stored = LIGHT_ORIG_GAP.get(stack);
            if (stored == null) {
                try {
                    orig = XposedHelpers.getIntField(stack, "mPaddingBetweenElements");
                } catch (Throwable t) {
                    orig = 0;
                }
                LIGHT_ORIG_GAP.put(stack, orig);
            } else {
                orig = stored;
            }
            int gap = split ? NotifyRowSpec.dp(ctx, Os5Mirror.GAP_BETWEEN_DP) : orig;
            try {
                XposedHelpers.setIntField(stack, "mPaddingBetweenElements", gap);
            } catch (Throwable ignored) {
            }
            Object algo = null;
            try {
                algo = XposedHelpers.getObjectField(stack, "mStackScrollAlgorithm");
            } catch (Throwable ignored) {
            }
            if (algo != null) {
                Float origF = LIGHT_ORIG_ALGO_GAP.get(algo);
                if (origF == null) {
                    float f = 0f;
                    try {
                        f = XposedHelpers.getFloatField(algo, "mPaddingBetweenElements");
                    } catch (Throwable ignored) {
                    }
                    LIGHT_ORIG_ALGO_GAP.put(algo, f);
                    origF = f;
                }
                try {
                    XposedHelpers.setFloatField(algo, "mPaddingBetweenElements",
                            split ? (float) NotifyRowSpec.dp(ctx, Os5Mirror.GAP_BETWEEN_DP) : origF);
                } catch (Throwable ignored) {
                }
            }

            if (isDebug(ctx)) {
                Log.i(TAG, "notify_row: os5 mirror mode=" + mode + " gap=" + gap);
            }
            stack.requestLayout();
            stack.invalidate();
            refreshCardRoundness();
            if (isVerify(ctx)) {
                final View probe = stack;
                probe.postDelayed(safe(() -> dumpRoundness(probe, "sep")), 1500);
            }
        } catch (Throwable t) {
            try {
                if (isDebug(stack.getContext())) {
                    Log.w(TAG, "notify_row: split failed", t);
                }
            } catch (Throwable ignored) {
            }
        }
    }

    /**
     * 把 header 的应用名/时间实际左缘收敛到 66dp 列：header 里还有图标槽，
     * 单次 padding 增量常被系统布局吃掉或过冲，所以量一次改一次，最多 5 轮。
     * 独立行、组摘要、组子行共用这套口径（alignLightColumn 的公式只作初值）。
     */
    private static final WeakHashMap<View, long[]> HEADER_TEXT_STATE = new WeakHashMap<>();
    private static final WeakHashMap<View, Boolean> HEADER_CONVERGING = new WeakHashMap<>();
    private static final WeakHashMap<View, Long> HEADER_LAST_TRY = new WeakHashMap<>();
    private static final java.util.Set<String> sColLogOnce =
            java.util.Collections.synchronizedSet(new java.util.HashSet<String>());

    /** 常态收敛入口：由圆角钩子（每轮布局都会调）触发，同一 header 只允许一条链在跑。 */
    private static void maybeConvergeHeader(final View row) {
        try {
            if (row == null || !row.isShown()) {
                return;
            }
            View hdr = findViewByIdSuffixShown(row, "notification_header");
            if (hdr == null || hdr.getHeight() <= 0) {
                lightSkip("col-nohdr", row, "hdr=" + (hdr == null ? "null" : "h0"));
                return;
            }
            long now = android.os.SystemClock.uptimeMillis();
            synchronized (HEADER_CONVERGING) {
                Long last = HEADER_LAST_TRY.get(hdr);
                if (last != null && now - last < 400) {
                    return;
                }
                HEADER_LAST_TRY.put(hdr, now);
            }
            synchronized (HEADER_CONVERGING) {
                if (Boolean.TRUE.equals(HEADER_CONVERGING.get(hdr))) {
                    return;
                }
                HEADER_CONVERGING.put(hdr, Boolean.TRUE);
            }
            convergeHeaderColumn(row, hdr, 0);
        } catch (Throwable ignored) {
        }
    }

    /**
     * 把 header 的时间推到文字行右端（OS5 口径：时间在右上角，右边缘停在应用名那条线的末尾、
     * 箭头左侧）。门槛是"这一行已按展开态布局"（≥70dp）：紧凑子行是原生小行，不动。
     *
     * 全程用**布局坐标**（parent.getWidth() 与 child.getRight()），不碰 getLocationOnScreen：
     * 上一版用屏幕坐标，展开动画期间祖先本身带 translationY/X，那份位移被当成时间自己的位移算进
     * 增量，于是"先跳到右边、动画一结束再推出去被卡片裁掉"（用户看到的就是一秒多后消失）。
     */
    /** 把一枚 time 推到它所在 header 的右内缩处（有箭头则停在箭头左缘）——布局坐标，纯几何。 */
    /**
     * 把时间钉在**箭头左缘**这条稳定锚上（组摘要行专用，11-05 凌晨定案）。
     *
     * 真机逐帧读数（`betteroriginos_trace_flicker`）：组摘要行 `id=6abb4b2` 的时间
     * f=139 L=674 → f=142 L=578 → f=149 L=674 —— 左移 96px 再闪回，就是用户报的动作。
     * 摘举行**没有** 42dp 大图标（L7 策略），我们前面五版全部把它当"不归我们接管"跳过，
     * 所以屡修不好：改的根本不是这一行。
     *
     * 为什么它自己会动：时间是流式排的最后一个子节点，位置 = 线的左 + 线的宽 − 自身宽；
     * 而 `notification_top_line` 是 `layout_width=wrap` + `toStartOf=expand_button`，
     * 展开动画里线的宽度跟着内容变 ⇒ 拿"线的右端"当目标就等于追一个会跑的目标。
     * `expand_button` 是 `alignParentEnd`，它的左缘全程不动 ⇒ 这才是稳定锚。
     *
     * @return -1 无法摆位 / 0 本来就在锚上 / 1 钉回锚上
     */
    private static int anchorTimeToMaxRight(View time) {
        try {
            View header = time;
            for (int i = 0; i < 6 && header != null; i++) {
                if (header.getId() != 0 && idSuffix(header).equals("notification_header")) {
                    break;
                }
                header = header.getParent() instanceof View ? (View) header.getParent() : null;
            }
            if (header == null || header.getWidth() <= 0 || time.getWidth() <= 0) {
                return -1;
            }
            int offX = 0;
            View cur = time;
            for (int i = 0; i < 6 && cur != null && cur != header; i++) {
                offX += cur.getLeft();
                cur = cur.getParent() instanceof View ? (View) cur.getParent() : null;
            }
            if (cur != header) {
                return -1;
            }
            long now = android.os.SystemClock.uptimeMillis();
            int naturalRight = offX + time.getWidth();
            long[] st = TIME_MAX_RIGHT.get(time);
            if (st == null || now - st[1] > TIME_ANCHOR_KEEP_MS || naturalRight >= st[0]) {
                st = new long[]{naturalRight, now, 0};
            } else {
                st[1] = now;
            }
            TIME_MAX_RIGHT.put(time, st);
            int tx = (int) (st[0] - naturalRight);
            if (tx <= 0) {
                if (Math.round(time.getTranslationX()) != 0) {
                    time.setTranslationX(0f);
                }
                return 0;
            }
            if (tx > TIME_ANCHOR_MAX_PX) {
                return -1;   // 差太多说明算错，宁可不补，也不把时间推到看不见的位置
            }
            time.setTranslationX(tx);
            return 1;
        } catch (Throwable t) {
            return -1;
        }
    }

    /**
     * 时间的锚：它自己最近出现过的**最右自然位置**（不含我们写的位移），1500ms 内有效。
     *
     * 为什么不钉常量、也不钉"箭头左缘"（11-05 凌晨定案）：组摘要行静止时原生就在最右
     *（真机 L=674、宽 178），展开动画里线宽一变化把它临时推到 L=578（左移 96px）再回来 ——
     * 用户报的"往左跑、到左边又闪回右"就是这 96px。上一版拿 `header_button_container` 左缘
     * 当锚，跨度确实归零了，但把稳态钉成 578：**用位置偏左换了不滑，是观感回归**。
     * 钉"历史最右自然位置"同时满足：① 静止时自然右缘==最大值 ⇒ 位移 0，不改原生外观；
     * ② 动画中自然位置左移 ⇒ 补一个正位移把右缘顶回去 ⇒ 不动；
     * ③ 文本真变短（"9 小时前"→"现在"）时右缘保持、左缘右移，正是右对齐时间的原生行为。
     * 只写**绝对** translationX ⇒ 幂等、一个主人；补位超过 300px 直接放弃（宁可不修）。
     */
    private static final WeakHashMap<View, long[]> TIME_MAX_RIGHT = new WeakHashMap<>();
    private static final int TIME_ANCHOR_MAX_PX = 300;
    private static final long TIME_ANCHOR_KEEP_MS = 1500L;

    /**
     * 是否由我们把 header 时间搬到行右端。
     *
     * **false 是当前选择**（11-05 晨，用户对照实验定案）：A13 原生的时间戳一直在左上槽、
     * 展开全程不动；我们把它往右搬，而 vivo 每帧按原生把它摆回去 ⇒ 同一属性上逐帧对拉，
     * 观感就是"从右滑到左、到左又闪回右"。十版都在加固这个搬迁，所以十版都没变化。
     * 时间不该被钉——该让位的是我们自己加的视图（图标是我们的，摆它没人争抢）。
     */
    private static final boolean RELOCATE_HEADER_TIME = false;

    private static int pushTimeRight(View time, String why) {
        if (!RELOCATE_HEADER_TIME) {
            return 0;
        }
        try {
            if (time == null || time.getWidth() <= 0) {
                return -1;
            }
            View header = time;
            for (int i = 0; i < 6 && header != null; i++) {
                if (header.getId() != 0 && idSuffix(header).equals("notification_header")) {
                    break;
                }
                header = header.getParent() instanceof View ? (View) header.getParent() : null;
            }
            if (header == null || header.getWidth() <= 0) {
                return -1;
            }
            // 时间在 header 坐标系里的布局位置（逐级累加 getLeft，不含 translationX）
            int offX = 0;
            View cur = time;
            for (int i = 0; i < 6 && cur != null && cur != header; i++) {
                offX += cur.getLeft();
                cur = cur.getParent() instanceof View ? (View) cur.getParent() : null;
            }
            if (cur != header) {
                return -1;
            }
            // 右边界：优先"文字行的右端"（OS5 就是停在箭头左侧）；行在展开态会变成 wrap 宽度，
            // 那时它等于时间自身的右缘，用它当目标就等于不推 —— 这正是"再展开单条、时间又回左边"。
            View line = findViewByIdSuffixShown(header, "notification_top_line");
            int lineRight = -1;
            if (line != null && line.getWidth() > 0) {
                int lineOff = 0;
                View c2 = line;
                for (int i = 0; i < 6 && c2 != null && c2 != header; i++) {
                    lineOff += c2.getLeft();
                    c2 = c2.getParent() instanceof View ? (View) c2.getParent() : null;
                }
                if (c2 == header) {
                    lineRight = lineOff + line.getWidth();
                }
            }
            int target = lineRight;
            if (target <= 0 || target - offX - time.getWidth() < 8) {
                // 行没给出有效右边界（wrap）：退到箭头左缘，没有箭头就用 header 右内缩 16dp
                View chevron = findViewByIdSuffixShown(header, "header_button_container");
                if (chevron != null && chevron.getWidth() > 0) {
                    int off = 0;
                    View c3 = chevron;
                    for (int i = 0; i < 6 && c3 != null && c3 != header; i++) {
                        off += c3.getLeft();
                        c3 = c3.getParent() instanceof View ? (View) c3.getParent() : null;
                    }
                    target = c3 == header ? off : -1;
                }
                if (target <= 0) {
                    target = header.getWidth() - NotifyRowSpec.dp(time.getContext(), 16);
                }
            }
            // 右缘 = 在 header 系里的布局左偏移 + 自身宽度。
            // 原来这里写成 offX + time.getRight()，而 getRight() = getLeft() + width
            // ⇒ 左偏移被算了两遍。而 time.getLeft() 正是"随兄弟可见性每帧变化"的那个量
            //（top_line 流式排、GONE 跳过），于是每帧的目标都在抖 ——
            // 用户看到的"时间从右滑到左、到左又闪回右"就是这个自算出来的抖动目标，
            // 不是 vivo 在抢写。（11-04 深夜自审 + 读数定案）
            int d = target - (offX + time.getWidth());
            if (Math.abs(d) <= 2) {
                return 0;
            }
            // 直接写绝对位移：time 是一枚纯文本，没有系统自己的位移要保留。
            // 位移会让它离开"文字行"的边界（展开态的行会缩成 wrap 宽度），必须把沿途容器的
            // 裁剪关掉，否则它在右边根本画不出来：真机表现就是"右边看不到、dump 里连节点都没有，
            // 等系统重排又回到左边压住图标"。只关到 header 这一层，不动行外的东西。
            View clipStop = time;
            for (int i = 0; i < 5 && clipStop != null && clipStop != header; i++) {
                View p = clipStop.getParent() instanceof View ? (View) clipStop.getParent() : null;
                if (p instanceof ViewGroup) {
                    ((ViewGroup) p).setClipChildren(false);
                }
                clipStop = p;
            }
            time.setTranslationX(d);
            hookTimeRelayout(header, time);
            timeWitness("push", "d=" + d + " target=" + target + " why=" + why, header);
            // 回读校验（比的是真实视图状态，不是我这行算式）：还差得远就报 2
            int off2 = 0;
            View c4 = time;
            for (int i = 0; i < 6 && c4 != null && c4 != header; i++) {
                off2 += c4.getLeft();
                c4 = c4.getParent() instanceof View ? (View) c4.getParent() : null;
            }
            int right = off2 + time.getWidth() + Math.round(time.getTranslationX());
            return Math.abs(target - right) <= 3 ? 1 : 2;
        } catch (Throwable t) {
            timeWitness("throw", String.valueOf(t), time);
        }
        return -1;
    }

    /**
     * vivo 会在每次重新布局 header 时把 time 的 translationX 写回 0（真机："推了但还是留在左边"），
     * 只靠几次延时补推落不住。挂在它所在那一行的布局回调上：行一重排就再推一次。
     */
    private static final WeakHashMap<View, View> TIME_RELAYOUT = new WeakHashMap<>();

    private static void hookTimeRelayout(final View line, final View time) {
        synchronized (TIME_RELAYOUT) {
            if (TIME_RELAYOUT.get(line) == time) {
                return;
            }
            TIME_RELAYOUT.put(line, time);
        }
        line.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
            @Override
            public void onLayoutChange(View v, int left, int top, int right, int bottom,
                                       int oldLeft, int oldTop, int oldRight, int oldBottom) {
                pushTimeRight(time, "relayout");
            }
        });
    }

    /** 一棵子树里所有 header 的时间（组开合时一次性重推，vivo 会把布局重置回去）。 */
    private static void pushTimesIn(ViewGroup g, int depth, String why) {
        if (g == null || depth > 8) {
            return;
        }
        for (int i = 0; i < g.getChildCount(); i++) {
            View c = g.getChildAt(i);
            if (c == null) {
                continue;
            }
            if (c.getId() != 0 && idSuffix(c).equals("time")) {
                // 不再按"行高≥70dp"过滤：展开过渡里先露出来的是紧凑那份 header，它的时间也在
                // 左边；只推最终那份就会出现"先回左边、动画末尾再被闪回右边"（用户报的现象）。
                // 两份都推，谁先可见谁就已经在右边。
                pushTimeRight(c, why);
            } else if (c instanceof ViewGroup) {
                pushTimesIn((ViewGroup) c, depth + 1, why);
            }
        }
    }

    private static void alignHeaderTime(final View row, final View header) {
        try {
            View time = findViewByIdSuffixShown(header, "time");
            if (time == null) {
                return;
            }
            if (row.getHeight() < NotifyRowSpec.dp(row.getContext(), 70)) {
                return;   // 紧凑子行：那本来就是原生小行，不动原生排版
            }
            pushTimeRight(time, "converge");
        } catch (Throwable t) {
            timeWitness("throw", String.valueOf(t), header);
        }
    }

    private static String idSuffix(View v) {
        Object id = null;
        try {
            id = v.getResources() == null ? null : v.getResources().getResourceName(v.getId());
        } catch (Throwable ignored) {
        }
        String s = id == null ? v.getClass().getSimpleName() : id.toString();
        int i = s.lastIndexOf('/');
        return i >= 0 ? s.substring(i + 1) : s;
    }

    /**
     * 时间对位的见证：按 (阶段 + header) 去重，整轮封顶 8 条。
     * 上一轮"用户说时间跳了却一条 notify_row.time 都没有"，分不清是没跑到还是跑到没日志，
     * 所以早退分支也要各留一条。
     */
    private static final java.util.Set<String> sTimeWitness =
            java.util.Collections.synchronizedSet(new java.util.HashSet<String>());
    private static int sTimeWitnessLines;

    private static void timeWitness(String tag, String body, View header) {
        // 不看 sVerifyEnabled：上一轮"用户说时间没动，而日志一条都没有"，
        // 开关本身就成了嫌疑——它一旦为 false，诊断和修复就同时失明。封顶 12 条不会刷屏。
        if (sTimeWitnessLines >= 12) {
            return;
        }
        if (!sTimeWitness.add(tag + "|" + System.identityHashCode(header))) {
            return;
        }
        sTimeWitnessLines++;
        Log.i(TAG, "notify_row.time " + tag + " " + body + " hdr="
                + Integer.toHexString(System.identityHashCode(header)));
    }

    private static void convergeHeaderColumn(final View row, final View header, final int pass) {
        if (pass >= 5 || header == null || row == null) {
            synchronized (HEADER_CONVERGING) {
                HEADER_CONVERGING.remove(header);
            }
            return;
        }
        boolean aligned = false;
        try {
            // 时间先走：vivo 组子行的 header 里只有时间、没有应用名（实测 x=+126，压在
            // 42dp 大图标的右半边），而下面 appName==null 会直接退出——旧代码因此从未维护过时间。
            alignHeaderTime(row, header);
            // 只认这份 header 子树里的应用名：行里可能同时存在收起/展开两份 header，
            // 跨子树取值会把 padding 加到另一份上（实测出现过 x=99 配 hdrPad=159 的矛盾读数）
            View appName = findViewByIdSuffixShown(header, "app_name_text");
            if (appName == null || !appName.isShown() || appName.getWidth() <= 0) {
                stopConverge(header);
                return;
            }
            // 口径与本文件的其它摆位一致：**逐级累加 getLeft**（不含 translationX、不用屏幕坐标）。
            // 原来这里用 getLocationOnScreen 相减，过渡帧祖先正带着位移 ⇒ 同一个真实位置会量出
            // 不同的 x，`d` 跟着抖，`shiftHeaderText` 的 `+=` 就把线的左缘加偏
            //（真机读数 +126/+132/+565 那种"别名落点"就是这么来的）。
            final int x = rowVisualLeft(row, appName);
            if (x < 0) {
                stopConverge(header);
                return;
            }
            int want = NotifyRowSpec.dp(row.getContext(), NotifyRowSpec.COLUMN_DP);
            final int d = want - x;
            long now = android.os.SystemClock.uptimeMillis();
            if (Math.abs(d) <= 2) {
                aligned = true;
            } else {
                synchronized (HEADER_TEXT_STATE) {
                    long[] st = HEADER_TEXT_STATE.get(appName);
                    boolean sameAsLast = st != null && st[0] == x;
                    // 位置没变且刚改过：布局还没跟上，重复加会过冲；
                    // 位置没变但已过 250ms：说明被 vivo 布局重置回去了，要再补一次。
                    boolean stale = st == null || now - st[1] > 250;
                    if (!sameAsLast || stale) {
                        HEADER_TEXT_STATE.put(appName, new long[]{x, now});
                        shiftHeaderText(row, header, appName, d);
                        if (isVerify(row.getContext())
                                && sColLogOnce.add(Integer.toHexString(
                                        System.identityHashCode(header)))) {
                            // 每个 header 只打一次：这条是诊断，刷多了会把启动期的
                            // g2/shelfgap/补挂见证冲出 logcat 窗口，门禁反而取不到证据
                            Log.i(TAG, "notify_row.col x=" + x + " want=" + want
                                    + " d=" + d + " same=" + (sameAsLast ? 1 : 0)
                                    + " pkg=" + (sbnOf(row) == null ? "-"
                                            : sbnOf(row).getPackageName()));
                        }
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        if (aligned) {
            stopConverge(header);
            return;
        }
        header.postDelayed(safe(() -> convergeHeaderColumn(row, header, pass + 1)), 100);
    }

    /**
     * 把文本整体右移到目标列：子行的应用名由 `notification_top_line` 自己的左边距定位
     * （实测 header padding 改了它不动），独立行/摘要则跟着 header padding 走。
     * 先试真正承载文本的祖先，都不行才退回 header。
     */
    private static boolean shiftHeaderText(View row, View header, View appName, int delta) {
        View cur = appName;
        for (int up = 0; up < 4 && cur != null; up++) {
            ViewGroup.LayoutParams lp = cur.getLayoutParams();
            if (lp instanceof ViewGroup.MarginLayoutParams
                    && viewIdName(cur).endsWith(":id/notification_top_line")) {
                ViewGroup.MarginLayoutParams mlp = (ViewGroup.MarginLayoutParams) lp;
                mlp.leftMargin += delta;
                cur.setLayoutParams(mlp);
                return true;
            }
            ViewParent p = cur.getParent();
            cur = p instanceof View ? (View) p : null;
        }
        header.setPaddingRelative(header.getPaddingStart() + delta,
                header.getPaddingTop(), header.getPaddingEnd(), header.getPaddingBottom());
        // 写回缓存，免得下一次 polish 用公式初值把它回退
        LIGHT_COLUMN_HEADER.put(header, header.getPaddingStart());
        return true;
    }

    private static void stopConverge(View header) {
        synchronized (HEADER_CONVERGING) {
            HEADER_CONVERGING.remove(header);
        }
    }

    // ==================== 布局期文本列对位（header 时间不再有机会落到图标列） ====================
    // 这一版 11-03 装过、当天因"大图标消失"整体回退；那个回归的根因已在 11-04 修掉
    // （LIGHT_POLISHED 的键是 contentView，而 A13 一行有两棵根 ⇒ 标记过期 → rearmStalePolish；
    // 原生小图标改成压 drawable alpha，不再摘 drawable），所以重上它的前提现在才成立。

    /** 钩子实际装上与否：模块据此自报 header-time=column-hook / push-only，门禁决定 L10/L11 跑不跑。 */
    private static volatile boolean sColumnHook;
    /** onLayout 进过钩子的次数：区分"钩子装了没跑"（类不对/被子类覆盖）与"跑了但证据被 logd 冲掉"。 */
    private static volatile int sColHookCalls;
    /** 最近一次实测落点的整串参数，由补扫低频重发（beat 行），别只靠一次性那条。 */
    private static volatile String sColBeat;
    private static long sColBeatAt;

    /**
     * header 文本列对位：挂在 framework 自己的摆位出口，在**同一趟布局**里把文本子节点整体平移到
     * COLUMN_DP（66dp），A13/A15 共用这一处。
     *
     * 为什么必须挂在这里（前三轮都在跟这条规则赛跑，别再退回补推）：
     * `android.view.NotificationTopLineView.onLayout` 是**按子节点顺序从左往右流式排**、
     * GONE 跳过（真机布局源：framework-res 的 notification_template_header.xml，A13/A15 同构）。
     * vivo 的组子行把 `app_name_text` 收掉了，那一行只剩 `time` 一个可见子节点，
     * 于是时间落到行首（实测 +126），正好压进 42dp 大图标占的 +48..+174。
     * 这不是"谁把时间写歪了"，是布局规则的必然结果 —— 所以既不能用 translationX 补
     * （vivo 每趟重排都写回 0），也不能靠延时重推（永远在跟动画抢时机）。
     */
    private static void installTopLineColumnHook(ClassLoader cl) {
        try {
            Class<?> cls = XposedHelpers.findClassIfExists(
                    "android.view.NotificationTopLineView", cl);
            if (cls == null) {
                Log.i(TAG, "notify_row: top-line column hook skipped=no-class");
                return;
            }
            int hits = XposedBridge.hookAllMethods(cls, "onLayout", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    alignLineIntoColumn((View) param.thisObject);
                }
            }).size();
            sColumnHook = hits > 0;
            Log.i(TAG, "notify_row: top-line column hook hits=" + hits);
        } catch (Throwable t) {
            Log.w(TAG, "notify_row: top-line column hook failed", t);
        }
    }

    /**
     * 紧跟在可见性翻转之后重摆时间 —— "加入那一帧"，而不是再加一个事后写者。
     *
     * RE 的帧序结论：`NotificationContentView.onLayout` 是
     * FrameLayout.onLayout(排完所有子) → updateClipping → **selectLayout**，
     * 也就是**翻可见性发生在布局期钩子之后**。于是布局期摆好的是"当时可见的那一枚"，
     * 刚被 selectLayout 翻出来的另一枚沿用它当隐藏副本时的旧位置 ——
     * 用户看到的正是"滑到左边、下一帧才被闪回右边"。
     * 这里在 selectLayout 之后把这一行里所有**在屏上的**时间交给同一个主人
     * （`pushTimeRight` 写绝对位移 ⇒ 幂等，重复调用只会收敛到同一目标）。
     */
    private static void installSelectLayoutTimeHook(ClassLoader cl) {
        try {
            Class<?> ncv = XposedHelpers.findClassIfExists(
                    "com.android.systemui.statusbar.notification.row.NotificationContentView", cl);
            if (ncv == null) {
                Log.i(TAG, "notify_row: select-layout hook skipped=no-class");
                return;
            }
            int hits = XposedBridge.hookAllMethods(ncv, "selectLayout", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    try {
                        if (!(param.thisObject instanceof View)) {
                            return;
                        }
                        View row = rowAncestor((View) param.thisObject);
                        if (row == null) {
                            return;
                        }
                        // 媒体行由 MediaCardFeature 独占自绘，不参与
                        if (isMediaRow(row, null)) {
                            return;
                        }
                        java.util.ArrayList<View> times = new java.util.ArrayList<>(3);
                        collectIdSuffix(row, "time", times, 0);
                        for (int i = 0; i < times.size(); i++) {
                            View t = times.get(i);
                            if (t == null || !t.isShown() || t.getWidth() <= 0) {
                                continue;
                            }
                            // 被接管的行（有 42dp 图标）沿用自己的目标；
                            // 组摘要行**没有**图标，过去被整段跳过，正是"屡提屡没改好"的那一行
                            pushTimeRight(t, "selayout");
                        }
                    } catch (Throwable ignored) {
                    }
                }
            }).size();
            Log.i(TAG, "notify_row: select-layout hook hits=" + hits);
        } catch (Throwable t) {
            Log.w(TAG, "notify_row: select-layout hook failed", t);
        }
    }

    /**
     * 只搬文本列这几个子节点。`mFeedbackIcon` 不参与：vivo 用它在 top_line **自己的坐标系**里
     * 算触摸热区（HeaderTouchListener.isInside），把它挪走等于把热区挪出图标。
     *
     * **`time` 与 `time_divider` 不在这个名单里**（11-04 深夜改的）：把时间搬进"对齐到 66dp 列"
     * 这一圈，等于给它多加了一个主人 —— 同一枚 time 当时有三个写者（这里的 `c.layout()`、
     * `pushTimeRight` 的绝对 translationX、`freezeTimeLeft` 的增量 translationX），目标还各不相同，
     * 于是每帧左右互推：用户看到的"从右滑到左、再闪回右"就是我们自己在打。
     * 时间只允许由**一个**主人摆位（见 `positionTime` → `pushTimeRight`）。
     */
    private static final String[] HEADER_TEXT_IDS = {
            "app_name_text", "header_text", "header_text_divider",
            "secondary_header_text", "secondary_header_text_divider", "title"};
    private static volatile int[] sHeaderTextIds;
    private static volatile int sTimeId;
    private static int sColShiftWitness;
    private static int sColMeasureWitness;
    private static final java.util.Set<String> sColSkipWitness =
            java.util.Collections.synchronizedSet(new java.util.HashSet<String>());
    private static final java.util.Set<String> sColReasonWitness =
            java.util.Collections.synchronizedSet(new java.util.HashSet<String>());
    private static final java.util.Set<String> sColMeasureKeys =
            java.util.Collections.synchronizedSet(new java.util.HashSet<String>());

    /**
     * 早退原因见证（入口级）：只打过一次的原因不重复打。
     * 上一轮的教训是"见证只打在动作分支"，于是"钩子没跑"与"跑了但判成不用动"在日志里长得一样。
     */
    private static void colReason(String why, View line, View row, String extra) {
        if (sColReasonWitness.size() >= 8) {
            return;
        }
        String pkg = "-";
        try {
            if (row != null && sbnOf(row) != null) {
                pkg = sbnOf(row).getPackageName();
            }
        } catch (Throwable ignored) {
        }
        String k = why + "|" + pkg;
        if (sColReasonWitness.add(k)) {
            Log.i(TAG, "notify_row.col skip why=" + why + " pkg=" + pkg
                    + " lineId=" + Integer.toHexString(System.identityHashCode(line))
                    + (extra == null ? "" : " " + extra));
        }
    }

    /** contentViewOf 走不通时，把走到的最后一层报出来（no-cv 只有带上链路才分得清"没挂上"与"链路不同"）。 */
    private static String contentViewTrace(View v) {
        StringBuilder sb = new StringBuilder();
        View cur = v;
        for (int i = 0; i < 8 && cur != null; i++) {
            sb.append(cur.getClass().getSimpleName());
            ViewParent p = cur.getParent();
            cur = p instanceof View ? (View) p : null;
            if (cur != null) {
                sb.append('<');
            }
        }
        return "hop=" + sb;
    }

    /**
     * 实测见证：不管这一趟有没有动手，都把"时间在行里的实际左偏移"记一帧。
     * 验收器拿它和 66dp 常量比 —— 断言的是视图真实落点，不是我这行算式里的 want。
     */
    /** top_line 里那枚可见的时间子节点（id 只解析一次，onLayout 每趟都调）。 */
    private static View findTimeChild(ViewGroup line) {
        if (line == null) {
            return null;
        }
        int timeId = sTimeId;
        if (timeId == 0) {
            timeId = line.getResources().getIdentifier("time", "id", "android");
            sTimeId = timeId;
        }
        if (timeId == 0) {
            return null;
        }
        // vivo 的行里时间**不是 top_line 的直接子节点**（真机 `col measure … time=none` 就是这么来的：
        // 只扫直接子节点 ⇒ 我们两版钳位都没作用到屏幕上那枚时间）。
        java.util.ArrayList<View> found = new java.util.ArrayList<>(2);
        collectIdSuffix(line, "time", found, 0);
        for (int i = 0; i < found.size(); i++) {
            View c = found.get(i);
            if (c != null && c.getVisibility() != View.GONE) {
                return c;
            }
        }
        return found.isEmpty() ? null : found.get(0);
    }

    /**
     * 诊断：这一趟 top_line 里"谁可见、多宽、线自己在哪"。
     * 现在的症状是**线本身在 410/565/568 之间滑**（`col measure line=+…`）——线是右对齐的，
     * 所以任何兄弟节点的可见性或宽度一翻，线的左端就跟着动；要治就得点名是哪一枚在翻。
     * 按内容去重 + 最短间隔 120ms（这条只在 notify_verify=1 时打，别把 logd 挤爆）。
     */
    private static String sLineSnapPrev;
    private static long sLineSnapAt;
    private static int sLineSnapLines;

    private static void lineSnapshotWitness(ViewGroup line, int leftInRow) {
        // 不挂在 sVerifyEnabled 上：那个开关是绑定期快照的，钩子里读到的常是旧值
        //（第一次就这么丢掉整批快照读数）。改用硬上限 40 条 + 120ms 节流自保。
        if (sLineSnapLines >= 40 || line.getChildCount() <= 0) {
            return;
        }
        long now = android.os.SystemClock.uptimeMillis();
        if (now - sLineSnapAt < 120) {
            return;
        }
        StringBuilder sb = new StringBuilder();
        sb.append("line=+").append(leftInRow)
                .append(" w").append(line.getWidth()).append(" kids=");
        for (int i = 0; i < line.getChildCount() && i < 8; i++) {
            View c = line.getChildAt(i);
            if (c == null) {
                continue;
            }
            String id = viewIdName(c);
            int slash = id.lastIndexOf('/');
            sb.append(slash >= 0 ? id.substring(slash + 1) : c.getClass().getSimpleName())
                    .append(':').append(c.getVisibility())
                    .append(':').append(c.getWidth())
                    .append('+');
        }
        String snap = sb.toString();
        String key = Integer.toHexString(System.identityHashCode(line)) + "|" + snap;
        if (key.equals(sLineSnapPrev)) {
            return;
        }
        sLineSnapPrev = key;
        sLineSnapAt = now;
        sLineSnapLines++;
        Log.i(TAG, "notify_row.col snap " + snap + " lineId="
                + Integer.toHexString(System.identityHashCode(line)));
    }

    /**
     * 时间的**唯一**摆位入口（11-04 深夜定案）。
     *
     * 之前是三个主人在同一枚 time 上打：`c.layout()`（曾被 HEADER_TEXT_IDS 里的 "time" 带进
     * "搬到 66dp 列"那圈）、`pushTimeRight`（绝对 translationX，目标=行右缘）、
     * `freezeTimeLeft`/pre-draw（增量 translationX，目标=它自己学的稳态）——
     * 目标与口径都不同，每帧互相覆盖，用户看到的"从右滑到左、到左又闪回右"是我们自己在打。
     * 现在只留 `pushTimeRight`：它写**绝对**位移，天然幂等；调用点从"事后补推若干次"改成
     * **每趟布局都调**（真机证明展开期间 onLayout 确实每帧重跑：连点时 calls 23→99）。
     *
     * @return pushTimeRight 的结果码（-1=这趟没有可摆的时间）
     */
    private static int positionTime(View row, View line) {
        View time = findTimeChild(line instanceof ViewGroup ? (ViewGroup) line : null);
        if (time == null) {
            return -1;
        }
        return pushTimeRight(time, "layout");
    }



    private static void colMeasure(View line, View row, int leftInRow, int want, int d, int moved,
                                   int clamp) {
        if (sColMeasureWitness >= 24) {
            return;
        }
        View time = findTimeChild(line instanceof ViewGroup ? (ViewGroup) line : null);
        String body;
        if (time == null || time.getWidth() <= 0) {
            body = "time=none";
        } else {
            // 量**视觉**左位（含各级祖先的 translationX）：稳态下它和布局坐标一致，
            // 而过渡帧只有这个数代表屏幕上真正画出来的位置。
            int vis = rowVisualLeft(row, time);
            int off = vis < 0 ? leftInRow + time.getLeft() : vis;
            body = "time=+" + off;
            // 同一个落点只记一次（2px 一档），换档说明布局又动过
            String key = Integer.toHexString(System.identityHashCode(row)) + "/" + (off / 2);
            if (!sColMeasureKeys.add(key)) {
                return;
            }
        }
        sColMeasureWitness++;
        String witness = "notify_row.col measure row="
                + Integer.toHexString(System.identityHashCode(row))
                + " lineId=" + Integer.toHexString(System.identityHashCode(line))
                + " line=+" + leftInRow + " " + body + " want=+" + want + " d=" + d
                + " moved=" + moved + " clamp=" + clamp + " rowH=" + row.getHeight()
                + " pkg=" + (sbnOf(row) == null ? "-" : sbnOf(row).getPackageName());
        // 整串留一份给补扫重发：一次性那条会被 logd 每进程限流整批冲掉，
        // 门禁读不到就只能报"钩子没跑"，而真凶是日志通道。
        sColBeat = witness.substring("notify_row.".length());
        Log.i(TAG, witness);
    }

    /**
     * 这一行里有没有我们那枚 42dp 大图标 —— 用屏幕上看得见的事实把关，不用 LIGHT_POLISHED
     * 这类名义标志（它的键是 contentView，vivo 组子行命不中，整段对位就被跳过了）。
     * 不做缓存：正缓存会在我们主动摘掉图标（放开这一行）之后继续误判"该管"。
     */
    private static boolean hasBigIconIn(View row) {
        return findTag(row) != null;
    }

    /** 布局期把 header 文本列推到 COLUMN_DP；够不着就不动，并记下不动的原因。 */
    private static void alignLineIntoColumn(View line) {
        try {
            sColHookCalls++;
            if (!(line instanceof ViewGroup)) {
                colReason("not-group", line, null, null);
                return;
            }
            if (line.getWidth() <= 0) {
                return;   // 这一趟还没量过，下一趟自然有值
            }
            View row = rowAncestor(line);
            if (row == null) {
                colReason("no-row", line, null, contentViewTrace(line));
                return;
            }
            View cv = contentViewOf(line);
            boolean taken = false;
            if (cv != null) {
                synchronized (LIGHT_POLISHED) {
                    taken = LIGHT_POLISHED.containsKey(cv);
                }
                if (!taken) {
                    synchronized (RESTYLED) {
                        taken = RESTYLED.containsKey(cv);
                    }
                }
            }
            if (!taken) {
                // 名义标志命不中 ≠ "这行不该管"。vivo 组子行就是这样被整段漏掉的（真机读数
                // `col skip why=not-taken pkg=com.vivo.daemonService`），而视觉钳位在 return 之后
                // 才做 ⇒ 时间照样压进 42dp 图标里闪。把关换成屏幕上看得见的事实：
                // **这一行里到底有没有我们那枚大图标**；没有才按原生放行（媒体行、vivo 摊开的
                // 组摘要头都不带它），有就必须守住"时间不落进图标列"这条几何。
                if (!hasBigIconIn(row)) {
                    if (cv == null) {
                        colReason(isGroupedSummaryHeader(line) ? "summary-header" : "no-cv",
                                line, row, contentViewTrace(line));
                    } else {
                        colReason("not-taken", line, row, null);
                    }
                    return;
                }
                int want = NotifyRowSpec.dp(row.getContext(), NotifyRowSpec.COLUMN_DP);
                int clamped = positionTime(row, line);
                if (clamped != 0) {
                    // 只在这里打一条：这些行走的是"没有标志但有图标"的兜底路径，
                    // 出问题时要能看出是钳住了(1)还是钳了还不到位(2)。
                    colReason("clamp-only", line, row, "clamp=" + clamped + " want=+" + want);
                }
                return;
            }
            // 只用布局坐标逐级累加 getLeft，不用 getLocationOnScreen：
            // 展开动画期间祖先自己带 translationX/Y，那份位移会被算进时间自己的位移。
            int leftInRow = 0;
            View cur = line;
            for (int i = 0; i < 8 && cur != null && cur != row; i++) {
                leftInRow += cur.getLeft();
                ViewParent p = cur.getParent();
                cur = p instanceof View ? (View) p : null;
            }
            if (cur != row) {
                colReason("chain", line, row, contentViewTrace(line));
                return;
            }
            final int want = NotifyRowSpec.dp(row.getContext(), NotifyRowSpec.COLUMN_DP);
            final int d = want - leftInRow;
            // 先留一张"这趟线里有什么"的快照：症状已经缩到"线本身在滑"，要治就得知道是谁翻的
            lineSnapshotWitness((ViewGroup) line, leftInRow);
            int moved = 0;
            if (d > 2) {
                ViewGroup g = (ViewGroup) line;
                int maxRight = 0;
                for (int i = 0; i < g.getChildCount(); i++) {
                    View c = g.getChildAt(i);
                    if (c != null && c.getVisibility() != View.GONE && isHeaderText(c)) {
                        maxRight = Math.max(maxRight, c.getRight());
                    }
                }
                int room = g.getWidth() - g.getPaddingEnd()
                        - NotifyRowSpec.dp(row.getContext(), 8) - maxRight;
                if (maxRight <= 0 || room < d) {
                    // 平移会把文本推出行的右边界（被箭头压住或画到卡片外）=> 宁可不动，但要留证据
                    if (sColSkipWitness.size() < 6) {
                        String k = leftInRow + "|" + room;
                        if (sColSkipWitness.add(k)) {
                            Log.i(TAG, "notify_row.col clamp d=" + d + " room=" + room
                                    + " lineId=" + Integer.toHexString(System.identityHashCode(line))
                                    + " pkg=" + (sbnOf(row) == null ? "-"
                                    : sbnOf(row).getPackageName()));
                        }
                    }
                } else {
                    for (int i = 0; i < g.getChildCount(); i++) {
                        View c = g.getChildAt(i);
                        if (c == null || c.getVisibility() == View.GONE || !isHeaderText(c)) {
                            continue;
                        }
                        c.layout(c.getLeft() + d, c.getTop(), c.getRight() + d, c.getBottom());
                        moved++;
                    }
                    // 不看 sVerifyEnabled：开关一旦为 false，修复和诊断同时失明（上一轮就是这么瞎的）。
                    if (sColShiftWitness < 12) {
                        sColShiftWitness++;
                        Log.i(TAG, "notify_row.col layout line=+" + leftInRow + " want=+" + want
                                + " d=" + d + " kids=" + moved + " rowH=" + row.getHeight()
                                + " pkg=" + (sbnOf(row) == null ? "-"
                                : sbnOf(row).getPackageName()));
                    }
                }
            }
            // 每帧钳位与上面的 layout 平移是两条独立的事：d<=2（按布局坐标已经够右）时上面整段
            // 会跳过，但祖先带 translationX 时**视觉**落点照样可能压进图标列 —— 过渡帧就是这种。
            int clamp = positionTime(row, line);
            colMeasure(line, row, leftInRow, want, d, moved, clamp);
        } catch (Throwable t) {
            Log.w(TAG, "notify_row.col layout failed", t);
        }
    }

    /** 这枚 header 是不是 vivo 分组卡的摘要头（直接挂在子行容器下面、没有内容视图那一种）。 */
    private static boolean isGroupedSummaryHeader(View line) {
        View cur = line;
        for (int i = 0; i < 4 && cur != null; i++) {
            String cn = cur.getClass().getSimpleName();
            ViewParent p = cur.getParent();
            if (cn.contains("NotificationHeaderView") && p instanceof View
                    && p.getClass().getSimpleName().contains("NotificationChildrenContainer")) {
                return true;
            }
            cur = p instanceof View ? (View) p : null;
        }
        return false;
    }

    /** 这个子节点属不属于"文本列"（id 只解析一次，onLayout 每趟都会调，别在这里查资源名）。 */
    private static boolean isHeaderText(View c) {
        int[] ids = sHeaderTextIds;
        if (ids == null) {
            android.content.res.Resources res = c.getResources();
            int[] built = new int[HEADER_TEXT_IDS.length];
            int n = 0;
            for (String name : HEADER_TEXT_IDS) {
                int id = res.getIdentifier(name, "id", "android");
                if (id != 0) {
                    built[n++] = id;
                }
            }
            ids = java.util.Arrays.copyOf(built, n);
            sHeaderTextIds = ids;
        }
        int id = c.getId();
        if (id == 0) {
            return false;
        }
        for (int v : ids) {
            if (v == id) {
                return true;
            }
        }
        return false;
    }

    /**
     * 圆角见证：逐行只读打印角色、当前 top/bottom 系数、半径来源与背景四角。
     * A13 的行挂在 VivoCustomStackSection 下，必须递归；只用于验收，不写任何状态。
     */
    private static void dumpRoundness(View stack, String phase) {
        try {
            if (!(stack instanceof ViewGroup) || !isVerify(stack.getContext())) {
                return;
            }
            ViewGroup g = (ViewGroup) stack;
            Log.i(TAG, "notify_row.roundness phase=" + phase
                    + " stack=" + g.getClass().getSimpleName()
                    + " n=" + g.getChildCount()
                    + " mirror=" + (Os5Mirror.enabled() ? 1 : 0));
            dumpRoundnessInto(g, phase, "", 0);
        } catch (Throwable t) {
            Log.w(TAG, "notify_row.roundness dump failed", t);
        }
    }

    private static void dumpRoundnessInto(ViewGroup g, String phase, String path, int depth) {
        if (depth > 4) {
            return;
        }
        for (int i = 0; i < g.getChildCount(); i++) {
            View c = g.getChildAt(i);
            if (c == null) {
                continue;
            }
            String cn = c.getClass().getName();
            boolean shelf = cn.endsWith(".NotificationShelf");
            if (shelf || cn.endsWith(".ExpandableNotificationRow")) {
                logRoundnessRow(c, phase, path + i, shelf);
            }
            if (c instanceof ViewGroup) {
                dumpRoundnessInto((ViewGroup) c, phase, path + i + ">", depth + 1);
            }
        }
    }

    private static void logRoundnessRow(View c, String phase, String path, boolean shelf) {
        float top = -1f;
        float bot = -1f;
        float outline = -1f;
        try {
            Object v = XposedHelpers.callMethod(c, "getCurrentTopRoundness");
            if (v instanceof Float) {
                top = ((Float) v).floatValue();
            }
            v = XposedHelpers.callMethod(c, "getCurrentBottomRoundness");
            if (v instanceof Float) {
                bot = ((Float) v).floatValue();
            }
        } catch (Throwable ignored) {
        }
        try {
            outline = XposedHelpers.getFloatField(c, "mOutlineRadius");
        } catch (Throwable ignored) {
        }
        String radii = "none";
        try {
            Object bg = XposedHelpers.getObjectField(c, "mBackgroundNormal");
            Object arr = bg == null ? null
                    : XposedHelpers.getObjectField(bg, "mCornerRadii");
            if (arr instanceof float[]) {
                float[] a = (float[]) arr;
                StringBuilder sb = new StringBuilder("[");
                for (int k = 0; k < a.length; k++) {
                    if (k > 0) {
                        sb.append(',');
                    }
                    sb.append(String.format(java.util.Locale.US, "%.0f", a[k]));
                }
                radii = sb.append(']').toString();
            }
        } catch (Throwable ignored) {
        }
        String pkg = "-";
        try {
            StatusBarNotification s = sbnOf(c);
            if (s != null) {
                pkg = s.getPackageName();
            }
        } catch (Throwable ignored) {
        }
        Log.i(TAG, "notify_row.roundness phase=" + phase
                + " path=" + path
                + " shelf=" + (shelf ? 1 : 0)
                + " sum=" + (bool(c, "isSummaryWithChildren") ? 1 : 0)
                + " child=" + (bool(c, "isChildInGroup") ? 1 : 0)
                + " pkg=" + pkg
                + " top=" + f2(top) + " bot=" + f2(bot)
                + " R=" + f2(outline)
                + " radii=" + radii
                + " mirror=" + (Os5Mirror.enabled() ? 1 : 0));
    }

    private static String f2(float v) {
        return String.format(java.util.Locale.US, "%.2f", v);
    }

    private static void registerSepObserver(final Context ctx) {
        if (sSepObserver) {
            return;
        }
        sSepObserver = true;
        try {
            sSepMode = sepMode(ctx);
            ContentObserver observer = new ContentObserver(
                    new Handler(Looper.getMainLooper())) {
                @Override
                public void onChange(boolean self) {
                    sSepMode = sepMode(ctx);
                    java.util.List<View> stacks;
                    synchronized (LIGHT_STACKS) {
                        stacks = new java.util.ArrayList<>(LIGHT_STACKS.keySet());
                    }
                    for (View st : stacks) {
                        if (st != null) {
                            applyLightSeparation(st, st.getContext());
                            st.invalidate();
                        }
                    }
                }
            };
            ctx.getContentResolver().registerContentObserver(
                    Settings.Global.getUriFor(Prefs.optionKey("notify_row", "sep")),
                    false, observer);
        } catch (Throwable t) {
            sSepObserver = false;
        }
    }

    /**
     * OS3(A13) 的行圆角只认 row.setTopRoundness/setBottomRoundness（像素），
     * NSSL 的 mCornerRadius 不会传到行背景；不设就是直角。iOS 样式每张卡四角都圆，
     * 所以上下 roundness 给同一个值。
     */
    /**
     * 这一行是否"只有展开才够得着动作按钮"。
     * A13/vivo OS3 的动作带（`android:id/actions_container`）收起态是 INVISIBLE，按钮只在展开态，
     * 所以这类行必须保留原生展开箭头、允许展开、不参与收起态高度钳制。
     * OS5(A15) 收起态就内联显示按钮，箭头照旧隐藏、高度照旧钳制 —— 已验证行为零改动，
     * 因此这里直接按版本关门（顺带省掉 OS5 每轮 measure 的子树扫描）。
     */
    private static boolean needsExpandForActions(View anyRowChild) {
        return OsProfile.isA13OrLower() && hasInlineActions(anyRowChild);
    }

    /** 收起态是否内联了动作按钮：A13/vivo 的动作容器既可能不叫 android:id/action_list，也可能挂在行上而不是模板里 */
    private static boolean hasInlineActions(View contentView) {
        try {
            // 传进来的可能是 contentView，也可能是模板根/箭头这类行内任意节点：
            // rowOf 只认 contentView，所以先按父链上行找行，否则动作区（模板的兄弟节点）会被判成"没有"
            View row = rowAncestor(contentView);
            if (row == null) {
                row = rowOf(contentView);
            }
            boolean cacheable = row != null;
            View scope = cacheable ? row : contentView;
            if (cacheable) {
                synchronized (ACTION_ROWS) {
                    Boolean cached = ACTION_ROWS.get(scope);
                    if (cached != null) {
                        return cached;
                    }
                }
            }
            boolean has = false;
            // 只走结构判定：按 android:id/actions_container 直取在这台机上量不到（通知视图的
            // context 是发通知的 App 的 resources），而见证里同一棵子树的 DFS 每帧都能命中
            View hit = findActionContainer(scope, 0);
            has = hit != null;
            if (cacheable && has) {
                // 只缓存"有动作"：首轮扫描可能赶在动作区 inflate 之前，负结果缓存下来就是永久假阴性
                synchronized (ACTION_ROWS) {
                    ACTION_ROWS.put(scope, Boolean.TRUE);
                }
            }
            if (hit != null && sActionLogOnce.add(hit.getClass().getName() + "#" + viewIdName(hit))) {
                Log.i(TAG, "notify_row: inline action " + hit.getClass().getSimpleName()
                        + " id=" + viewIdName(hit) + " vis=" + hit.getVisibility()
                        + " h=" + hit.getHeight()
                        + " kids=" + (hit instanceof ViewGroup
                                ? ((ViewGroup) hit).getChildCount() : -1));
            }
            return has;
        } catch (Throwable t) {
            return false;
        }
    }

    private static final WeakHashMap<View, Boolean> ACTION_ROWS = new WeakHashMap<>();

    /** 动作区信号：Button 本体、AOSP/vivo 动作容器的 id 名或类名；展开箭头不算动作按钮 */
    private static boolean isActionNode(View v, String idName) {
        String simple = v.getClass().getSimpleName();
        if (idName.contains("expand_button") || simple.contains("ExpandButton")) {
            return false;
        }
        return v instanceof android.widget.Button
                || idName.contains(":id/action")
                || idName.endsWith(":id/button1") || idName.endsWith(":id/button2")
                || idName.endsWith(":id/button3")
                || simple.contains("ActionList") || simple.contains("ActionButton")
                || simple.contains("ButtonsRow") || simple.contains("ActionContainer");
    }

    private static View findActionContainer(View v, int depth) {
        if (v == null || depth > 14) {
            return null;
        }
        if (isActionNode(v, viewIdName(v))) {
            return v;
        }
        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) {
                View hit = findActionContainer(g.getChildAt(i), depth + 1);
                if (hit != null) {
                    return hit;
                }
            }
        }
        return null;
    }

    private static String viewIdName(View v) {
        try {
            if (v.getId() == 0) {
                return "";
            }
            String n = v.getResources().getResourceName(v.getId());
            return n == null ? "" : n;
        } catch (Throwable t) {
            return "";
        }
    }

    /** 按资源 id 名后缀 DFS 查找（vivo 的 header 不一定是 NotificationHeaderView 类）。 */
    private static View findViewByIdSuffix(View root, String suffix) {
        return findViewByIdSuffix(root, suffix, 0);
    }

    private static View findViewByIdSuffix(View v, String suffix, int depth) {
        if (v == null || depth > 12) {
            return null;
        }
        if (viewIdName(v).endsWith(":id/" + suffix)) {
            return v;
        }
        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) {
                View hit = findViewByIdSuffix(g.getChildAt(i), suffix, depth + 1);
                if (hit != null) {
                    return hit;
                }
            }
        }
        return null;
    }

    /** 摘要行可能同时有收起/展开两份 header：优先返回真正可见的那一份。 */
    private static View findViewByIdSuffixShown(View v, String suffix) {
        View first = null;
        try {
            if (v == null) {
                return null;
            }
            if (viewIdName(v).endsWith(":id/" + suffix)) {
                if (v.isShown() && v.getHeight() > 0) {
                    return v;
                }
                first = v;
            }
            if (v instanceof ViewGroup) {
                ViewGroup g = (ViewGroup) v;
                for (int i = 0; i < g.getChildCount(); i++) {
                    View hit = findViewByIdSuffixShown(g.getChildAt(i), suffix);
                    if (hit != null && hit.isShown() && hit.getHeight() > 0) {
                        return hit;
                    }
                    if (first == null && hit != null) {
                        first = hit;
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        return first;
    }

    /** 未测量的 TextView 拿不到 Layout，日志取值不能直接点下去 */
    private static int lineCount(TextView tv) {
        android.text.Layout l = tv == null ? null : tv.getLayout();
        return l == null ? -1 : l.getLineCount();
    }

    private static final java.util.Set<String> sActionLogOnce =
            java.util.Collections.synchronizedSet(new java.util.HashSet<String>());

    /**
     * OS3(A13) 的 NotificationRoundnessManager 把**整个列表**当成一张连续卡：
     * 只有 firstInStack 的 top 是圆的，最后一行因 shelf 存在 bottom 恒为 0，其余行取
     * notification_corner_radius_small/corner（本机 = 0 ⇒ 直角）。所以 OS3 原生观感是
     * "整列一张方卡 + 分隔线"，与 OS5 的"每个应用一张圆角卡"根本不同。
     *
     * 这里在 getRoundnessFraction 上按**卡片角色**给系数（只在 sep=os5 时接管）：
     * - 独立行：top=1，bottom=1（自己一张卡）
     * - 组摘要：top=1；组展开有可见子行时 bottom=0，否则 bottom=1
     * - 组子行：top=0；末个可见子行 bottom=1，其余 0
     * - shelf：top=1，bottom=1（OS5 收纳栏自身是一张独立圆角卡）
     * 原生已判 1.0 的情形（滑动/清除中/首行/无栏末行）保持不动，避免打断动画。
     */
    /** 首次钩到 getRoundnessFraction 时记下的管理器实例，用于开关切换后强制重算。 */
    private static volatile Object sRoundnessMgr;

    /** 让系统按当前开关重算所有行的圆角（切 sep 后旧值不会自己刷新）。 */
    private static void refreshCardRoundness() {
        Object m = sRoundnessMgr;
        if (m == null) {
            return;
        }
        try {
            XposedHelpers.callMethod(m, "updateNotificationRoundness");
        } catch (Throwable t) {
            Log.w(TAG, "notify_row: roundness refresh failed", t);
        }
    }

    private static void installRoundnessHooks(ClassLoader cl) {
        try {
            final Class<?> mgr = XposedHelpers.findClass(
                    "com.android.systemui.statusbar.notification.stack.NotificationRoundnessManager",
                    cl);
            XC_MethodHook card = new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    try {
                        sRoundnessMgr = param.thisObject;
                        if (!Os5Mirror.enabled() || param.args == null || param.args.length < 2
                                || !(param.args[0] instanceof View)
                                || !(param.args[1] instanceof Boolean)) {
                            return;
                        }
                        // 独立行/子行的 header 文本列会被 vivo 布局重置，借这个每轮必调的钩子常态维护。
                        // 组摘要不参与：它保持原生 16dp 小图标与原生 header 排版。
                        final View v = (View) param.args[0];
                        if (v.getClass().getName().endsWith(".ExpandableNotificationRow")
                                && !bool(v, "isSummaryWithChildren")) {
                            v.post(safe(() -> maybeConvergeHeader(v)));
                        }
                        Object r = param.getResult();
                        float nativeV = r instanceof Float ? ((Float) r).floatValue() : -1f;
                        if (nativeV >= 0.999f) {
                            return;
                        }
                        // 原生给的是 0~1 之间的分数 ⇒ 这是 NotificationShelf
                        // .updateCornerRoundnessOnScroll 正在做的滚动过渡，不能被卡片值打断，
                        // 否则"从收纳栏划回来"会看到圆角突然丢失。
                        if (nativeV > 0.001f && nativeV < 0.999f) {
                            return;
                        }
                        final View rv = (View) param.args[0];
                        // 收纳栏上方/正在滑动的行由 shelf 自己裁决（同上一条理由）
                        if (bool(rv, "isAboveShelf") || bool(rv, "isHeadsUp")
                                || swipeAffected(rv)) {
                            return;
                        }
                        float want = cardRoundness((View) param.args[0],
                                ((Boolean) param.args[1]).booleanValue());
                        if (want >= 0f) {
                            param.setResult(want);
                        }
                    } catch (Throwable ignored) {
                    }
                }
            };
            int hits = XposedBridge.hookAllMethods(mgr, "getRoundnessFraction", card).size();
            Log.i(TAG, "notify_row: card roundness hook hits=" + hits);
            if (hits == 0) {
                Log.w(TAG, "notify_row: card roundness hook missed (getRoundnessFraction)");
            }
        } catch (Throwable t) {
            Log.w(TAG, "notify_row: roundness hook failed", t);
        }
    }

    /** 该行是否正在被滑动（原生 `NotificationRoundnessManager.isViewAffectedBySwipe`）。 */
    private static boolean swipeAffected(View v) {
        Object mgr = sRoundnessMgr;
        if (mgr == null) {
            return false;
        }
        try {
            return Boolean.TRUE.equals(XposedHelpers.callMethod(mgr, "isViewAffectedBySwipe", v));
        } catch (Throwable ignored) {
            return false;
        }
    }

    /** @return 该视图作为"卡片"应有的系数；<0 表示不接管（非通知行/非 shelf） */
    private static float cardRoundness(View v, boolean top) {
        String cn = v.getClass().getName();
        if (cn.endsWith(".NotificationShelf")) {
            return 1f;
        }
        if (!cn.endsWith(".ExpandableNotificationRow")) {
            return -1f;
        }
        if (bool(v, "isChildInGroup") || inChildrenContainer(v)) {
            if (top) {
                return 0f;
            }
            return isLastVisibleGroupChild(v) ? 1f : 0f;
        }
        if (bool(v, "isSummaryWithChildren")) {
            if (top) {
                return 1f;
            }
            return bool(v, "isGroupExpanded") && hasVisibleGroupChild(v) ? 0f : 1f;
        }
        return 1f;
    }

    private static ViewGroup groupParent(View row) {
        ViewParent p = row.getParent();
        return p instanceof ViewGroup ? (ViewGroup) p : null;
    }

    private static boolean hasVisibleGroupChild(View summaryOrChild) {
        ViewGroup g = groupParent(summaryOrChild);
        if (g == null) {
            return false;
        }
        for (int i = 0; i < g.getChildCount(); i++) {
            View c = g.getChildAt(i);
            if (c != null && c.getClass().getName().endsWith(".ExpandableNotificationRow")
                    && c.getVisibility() == View.VISIBLE && !bool(c, "isRemoved")) {
                return true;
            }
        }
        return false;
    }

    private static boolean isLastVisibleGroupChild(View row) {
        ViewGroup g = groupParent(row);
        if (g == null) {
            return false;
        }
        View last = null;
        for (int i = 0; i < g.getChildCount(); i++) {
            View c = g.getChildAt(i);
            if (c != null && c.getClass().getName().endsWith(".ExpandableNotificationRow")
                    && c.getVisibility() == View.VISIBLE && !bool(c, "isRemoved")) {
                last = c;
            }
        }
        return last == row;
    }

    private static void applyStackMetrics(View stack) {
        try {
            Context ctx = stack.getContext();
            if (!Prefs.featureEnabled(ctx, "notify_row", false)) {
                return;
            }
            if (isLightStyle(ctx)) {
                applyLightSeparation(stack, ctx);
                if (LIGHT_METRICS_LOGGED.compareAndSet(false, true)) {
                    Log.i(TAG, "notify_row: light keeps native list metrics sep=" + sSepMode);
                }
                return;
            }
            if (!"ios".equals(Prefs.option(ctx, "notify_row", "stack_metrics", "ios"))) {
                return;
            }
            int side = NotifyRowSpec.dp(ctx, 16);
            int overlapDp = 0;
            try {
                overlapDp = Integer.parseInt(Prefs.option(ctx, "notify_row",
                        "list_overlap", "0"));
            } catch (Throwable ignored) {
            }
            overlapDp = Math.max(0, Math.min(12, overlapDp));
            // 覆盖式列表原型：负间距让上一张卡压住下一张（0=保持 8dp 间隙）
            int gap = overlapDp > 0 ? -NotifyRowSpec.dp(ctx, overlapDp)
                    : NotifyRowSpec.dp(ctx, 8);
            int corner = NotifyRowSpec.dp(ctx, 20);
            // 字段名两套系统不一样，且缺任何一个都不能让整段中止：
            // A13 的 NotificationChildrenContainer 没有 mSidePaddings，
            // 之前第一个 setIntField 就 NoSuchFieldError 抛出，后面的间距/圆角全没生效
            sMetricsApplied = 0;
            setIntIfPresent(stack, "mSidePaddings", side);
            setIntIfPresent(stack, "mMinimumPaddings", side);
            setIntIfPresent(stack, "mPaddingBetweenElements", gap);
            setIntIfPresent(stack, "mCornerRadius", corner);
            // A13 的子项间距叫 mChildPadding；侧边距与圆角由我们自绘的卡片负责
            setIntIfPresent(stack, "mChildPadding", gap);
            Object algo = null;
            try {
                algo = XposedHelpers.getObjectField(stack, "mStackScrollAlgorithm");
            } catch (Throwable ignored) {
            }
            if (algo != null) {
                setFloatIfPresent(algo, "mPaddingBetweenElements", (float) gap);
            }
            if (isDebug(ctx)) {
                Log.i(TAG, "notify_row: stack metrics side=" + side + " gap=" + gap
                        + " corner=" + corner + " cls="
                        + stack.getClass().getSimpleName()
                        + " applied=" + sMetricsApplied + "/5");
            }
            stack.requestLayout();
            if (overlapDp > 0) {
                installOverlapZ(stack);
            }
        } catch (Throwable t) {
            Log.w(TAG, "notify_row: apply stack metrics failed", t);
        }
    }

    /** 已装 Z 序守卫的列表容器 */
    private static final WeakHashMap<View, Boolean> OVERLAP_Z_GUARDS = new WeakHashMap<>();

    /**
     * 覆盖式列表：负间距把行叠起来后，AOSP 默认"后加的行画在上层"——正好和 iOS 相反
     * （iOS 是上一条压住下一条）。用 translationZ 反转绘制层级，不动 child 顺序
     * （child 顺序被 NSS 的排版/命中测试依赖）。
     */
    private static void installOverlapZ(final View stack) {
        synchronized (OVERLAP_Z_GUARDS) {
            if (OVERLAP_Z_GUARDS.containsKey(stack)) {
                return;
            }
            OVERLAP_Z_GUARDS.put(stack, Boolean.TRUE);
        }
        stack.getViewTreeObserver().addOnPreDrawListener(preDrawGuard(() -> {
            if (!(stack instanceof ViewGroup)) {
                return;
            }
            ViewGroup g = (ViewGroup) stack;
            int n = g.getChildCount();
            for (int i = 0; i < n; i++) {
                View c = g.getChildAt(i);
                String cn = c.getClass().getSimpleName();
                if (cn.contains("ExpandableNotificationRow") || cn.contains("Section")) {
                    float want = (n - i) * 2f;
                    if (c.getTranslationZ() != want) {
                        c.setTranslationZ(want);
                    }
                }
            }
        }));
        Log.i(TAG, "notify_row: overlap z guard installed on "
                + stack.getClass().getSimpleName());
    }

    /**
 * 堆叠模式保留 GroupEntry，vivo 的状态栏图标控制器（updateVivoIconsForLayout）遇到组项
 * 会对 null 字符串调 equals 直接把 SystemUI 崩掉；这里把原调用包进 try/catch，
 * 正常路径照常执行，踩空时只跳过这一次图标布局。
 */
    private static void installVivoIconGuardHooks(ClassLoader cl) {
        XC_MethodHook wrap = new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                try {
                    XposedBridge.invokeOriginalMethod(param.method, param.thisObject,
                            param.args);
                } catch (Throwable t) {
                    Log.w(TAG, "notify_row: icon layout swallowed in "
                            + param.thisObject.getClass().getSimpleName()
                            + "." + param.method.getName());
                }
                param.setResult(null);
            }
        };
        // OS5(A15) 是 *Legacy*Impl；OS3(A13) 没有 Legacy 层，类名不同、方法名一致
        String[] classes = {
                "com.vivo.systemui.statusbar.phone.VivoLegacyNotificationIconAreaControllerImpl",
                "com.android.systemui.statusbar.phone.LegacyNotificationIconAreaControllerImpl",
                "com.vivo.systemui.statusbar.phone.VivoNotificationIconAreaController",
                "com.android.systemui.statusbar.phone.NotificationIconAreaController",
        };
        String[] methods = {"updateVivoIconsForLayout", "updateIconsForLayout"};
        int hooked = 0;
        for (String name : classes) {
            try {
                Class<?> ctl = XposedHelpers.findClass(name, cl);
                for (String method : methods) {
                    if (XposedBridge.hookAllMethods(ctl, method, wrap).size() > 0) {
                        hooked++;
                    }
                }
            } catch (Throwable ignored) {
            }
        }
        Log.i(TAG, "notify_row: icon layout guards installed=" + hooked);
    }

    private static void registerReverify(Context ctx) {
        if (sReverifyRegistered) {
            return;
        }
        sReverifyRegistered = true;
        try {
            ctx.getContentResolver().registerContentObserver(
                    Settings.Global.getUriFor(REVERIFY_KEY), false,
                    new ContentObserver(new Handler(Looper.getMainLooper())) {
                        @Override
                        public void onChange(boolean selfChange) {
                            // ContentObserver 回调跑在主线程，不在 Xposed 保护范围内；
                            // reverify 要遍历整棵通知树，撞上一次异常就是 SystemUI 重启
                            try {
                                triggerReverify();
                            } catch (Throwable t) {
                                Log.w(TAG, "notify_row: reverify failed", t);
                            }
                        }
                    });
        } catch (Throwable t) {
            Log.w(TAG, "notify_row: reverify observer failed", t);
        }
        try {
            ctx.getContentResolver().registerContentObserver(
                    Settings.Global.getUriFor(EXPAND_TEST_KEY), false,
                    new ContentObserver(new Handler(Looper.getMainLooper())) {
                        @Override
                        public void onChange(boolean selfChange) {
                            try {
                                debugExpandFirstRow();
                            } catch (Throwable t) {
                                Log.w(TAG, "notify_row: debug expand failed", t);
                            }
                        }
                    });
        } catch (Throwable t) {
            Log.w(TAG, "notify_row: expand observer failed", t);
        }
        try {
            ctx.getContentResolver().registerContentObserver(
                    Settings.Global.getUriFor(STACK_TEST_KEY), false,
                    new ContentObserver(new Handler(Looper.getMainLooper())) {
                        @Override
                        public void onChange(boolean selfChange) {
                            try {
                                int want = 0;
                                try {
                                    want = Settings.Global.getInt(ctx.getContentResolver(),
                                            STACK_TEST_KEY, 0);
                                } catch (Throwable ignored) {
                                }
                                debugApplyStack(want != 0);
                            } catch (Throwable t) {
                                Log.w(TAG, "notify_row: debug stack failed", t);
                            }
                        }
                    });
        } catch (Throwable t) {
            Log.w(TAG, "notify_row: stack observer failed", t);
        }
        try {
            ctx.getContentResolver().registerContentObserver(
                    Settings.Global.getUriFor(SCROLL_TEST_KEY), false,
                    new ContentObserver(new Handler(Looper.getMainLooper())) {
                        @Override
                        public void onChange(boolean selfChange) {
                            try {
                                debugScrollBottom();
                            } catch (Throwable t) {
                                Log.w(TAG, "notify_row: debug scroll failed", t);
                            }
                        }
                    });
        } catch (Throwable t) {
            Log.w(TAG, "notify_row: scroll observer failed", t);
        }
    }

    /** 调试把手：把通知列表直接滚到底（确定性验证底部留白，不依赖 adb 手势） */
    private static void debugScrollBottom() {
        View nss = null;
        synchronized (STACK_CARDS) {
            for (View row : STACK_CARDS.keySet()) {
                if (row == null) continue;
                View p = row.getParent() instanceof View ? (View) row.getParent() : null;
                for (int i = 0; i < 4 && p != null; i++) {
                    if (p.getClass().getName().contains("NotificationStackScrollLayout")) {
                        nss = p;
                        break;
                    }
                    p = p.getParent() instanceof View ? (View) p.getParent() : null;
                }
                if (nss != null) break;
            }
        }
        if (!(nss instanceof ViewGroup)) {
            Log.i(TAG, "notify_row: debug scroll none");
            return;
        }
        ViewGroup g = (ViewGroup) nss;
        int max = 0;
        for (int i = 0; i < g.getChildCount(); i++) {
            View c = g.getChildAt(i);
            if (c.getVisibility() == View.GONE) continue;
            // NSS 用 translationY 定位子行，getBottom() 不含位移
            int bottom = Math.round(c.getTop() + c.getTranslationY() + c.getHeight());
            max = Math.max(max, bottom);
        }
        int scroll = Math.max(0, max - nss.getHeight());
        nss.scrollTo(0, scroll);
        StringBuilder shelfInfo = new StringBuilder();
        try {
            Object o = XposedHelpers.getObjectField(nss, "mShelf");
            if (o instanceof View) {
                View shelf = (View) o;
                ViewGroup.LayoutParams lp = shelf.getLayoutParams();
                shelfInfo.append(" shelfVis=").append(shelf.getVisibility())
                        .append(" shelfAlpha=").append(shelf.getAlpha())
                        .append(" shelfH=").append(shelf.getHeight())
                        .append(" shelfMeasured=").append(shelf.getMeasuredHeight())
                        .append(" shelfLp=").append(lp == null ? -1 : lp.height)
                        .append(" shelfTop=").append(shelf.getTop());
            } else {
                shelfInfo.append(" shelf=?");
            }
        } catch (Throwable t) {
            shelfInfo.append(" shelfErr=").append(t);
        }
        Log.i(TAG, "notify_row: debug scroll to=" + scroll
                + " content=" + max + " h=" + nss.getHeight() + shelfInfo);
    }

    /** 调试把手：展开/收起第一条可见的堆叠卡（门禁用它覆盖堆叠展开动效） */
    /** 调试把手：1=展开最上面那一叠（已展开则不动），0=收起任何已展开的叠 */
    private static void debugApplyStack(boolean expand) {
        List<View> rows;
        synchronized (STACK_CARDS) {
            rows = new java.util.ArrayList<>(STACK_CARDS.keySet());
        }
        View expanded = null;
        View top = null;
        int topY = Integer.MAX_VALUE;
        int[] loc = new int[2];
        for (View row : rows) {
            if (row == null || !row.isShown()) {
                continue;
            }
            IosNotificationRow card = STACK_CARDS.get(row);
            if (card == null) {
                continue;
            }
            if (card.isStackExpanded()) {
                expanded = row;
                break;
            }
            row.getLocationOnScreen(loc);
            if (loc[1] < topY) {
                topY = loc[1];
                top = row;
            }
        }
        View target = expanded != null ? expanded : (expand ? top : null);
        if (target == null) {
            Log.i(TAG, "notify_row: debug stack none want=" + (expand ? 1 : 0)
                    + " rows=" + rows.size()
                    + (expand && expanded != null ? " already" : ""));
            return;
        }
        IosNotificationRow card = STACK_CARDS.get(target);
        Log.i(TAG, "notify_row: debug stack toggle pkg="
                + (sbnOf(target) == null ? "?" : sbnOf(target).getPackageName())
                + " wasExpanded=" + (card.isStackExpanded() ? 1 : 0)
                + " want=" + (expand ? 1 : 0)
                + " cardH=" + card.getHeight() + " count=" + childCountOf(target));
        toggleStack(target);
    }

    /**
     * 调试把手：把第一条可展开的非组行真正展开，并把收起/展开两侧的
     * 高度、alpha、visibility 一起打出来 —— 展开态"新旧图标重叠"能不能看见，
     * 取决于这两侧是否同时可见，日志比截图盲猜可靠。
     */
    private static void debugExpandFirstRow() {
        List<View> roots;
        // 只遍历"有自绘卡"的行：重叠发生在我们的卡与原生展开内容之间，
        // 挑一条没有卡的行展开看不到任何事（实测第一版白跑了三轮）
        synchronized (OVERLAYS) {
            roots = new java.util.ArrayList<>(OVERLAYS.keySet());
        }
        // 但重影也会发生在没有自绘卡的普通行上（A13 收起子视图不被置不可见），
        // 所以把手也要覆盖挂过重影守卫的行，否则门禁永远测不到那条路径
        synchronized (EXPANDED_GHOST) {
            for (View cv : EXPANDED_GHOST.keySet()) {
                if (!roots.contains(cv)) {
                    roots.add(cv);
                }
            }
        }
        int notShown = 0, noRow = 0, summary = 0, noContracted = 0;
        StringBuilder cls = new StringBuilder();
        for (View root : roots) {
            // rowOf() 在这里会返回 NotificationContentView（它自己就是"行"的近亲），
            // 而 setUserExpanded 只存在于 ExpandableNotificationRow 上，
            // 所以按类名向上找到真正的行
            View row = null;
            View p2 = root;
            for (int i = 0; i < 8 && p2 != null; i++) {
                if (p2.getClass().getSimpleName().contains("ExpandableNotificationRow")) {
                    row = p2;
                    break;
                }
                if (!(p2.getParent() instanceof View)) {
                    break;
                }
                p2 = (View) p2.getParent();
            }
            if (row == null) {
                noRow++;
                continue;
            }
            StatusBarNotification sbn = sbnOf(row);
            // 判可见要看"行"，不能看内容子视图：VERIFY_INFO 同时收着收起态与展开态两套
            // 内容视图，未展开的那套永远 isShown()=false（实测 24 个候选里 20 个这样被误拒）
            if (!row.isShown()) {
                notShown++;
                continue;
            }
            // 调试把手不需要 sbn（A13 上有 8 个可见行 sbnOf 取不到，另记一笔待查）

            if (cls.length() < 200) {
                cls.append(root.getClass().getSimpleName()).append('/')
                        .append(root.getParent() == null ? "-"
                                : root.getParent().getClass().getSimpleName()).append(' ');
            }
            View content = root.getParent() instanceof View ? (View) root.getParent() : null;
            View contracted = content == null ? null : currentContracted(content);
            // 展开子 View 在 A13 上是懒加载的：展开前 currentExpanded 可能为 null，
            // 不能因此跳过（否则这个调试把手永远找不到行）
            if (contracted == null) {
                noContracted++;
                continue;
            }
            View expanded = currentExpanded(content);
            try {
                XposedHelpers.callMethod(row, "setUserExpanded", true);
                Log.i(TAG, "notify_row: debug expand pkg="
                        + (sbn == null ? "?" : sbn.getPackageName())
                        + " via=cv cH=" + contracted.getHeight()
                        + " eH=" + (expanded == null ? -1 : expanded.getHeight())
                        + " cAlpha=" + contracted.getAlpha()
                        + " eAlpha=" + (expanded == null ? -1 : expanded.getAlpha())
                        + " cVis=" + contracted.getVisibility()
                        + " eVis=" + (expanded == null ? -1 : expanded.getVisibility())
                        + " rowH=" + row.getHeight());
                return;
            } catch (Throwable t) {
                Log.w(TAG, "notify_row: debug expand call failed", t);
                return;
            }
        }
        // 兜底：上面的 root 集合可能是被回收/不在屏的实例（实测 15 个 notShown）。
        // 直接从"已改造的行"里挑一条真正挂在通知栈上的行。
        List<View> rows;
        synchronized (RESTYLED_ROWS) {
            rows = new java.util.ArrayList<>(RESTYLED_ROWS.keySet());
        }
        // 第二次触发时优先收起已展开的行：门禁要能覆盖展开/收起两个方向
        for (View row : rows) {
            if (row == null || !row.isShown() || !bool(row, "isExpanded")
                    || STACK_ROWS.containsKey(row)) {
                continue;
            }
            View content = contentViewIn(row);
            View contracted = content == null ? null : currentContracted(content);
            if (content == null || contracted == null) {
                continue;
            }
            try {
                XposedHelpers.callMethod(row, "setUserExpanded", false);
                View expanded = currentExpanded(content);
                Log.i(TAG, "notify_row: debug collapse pkg="
                        + (sbnOf(row) == null ? "?" : sbnOf(row).getPackageName())
                        + " via=row cH=" + contracted.getHeight()
                        + " eH=" + (expanded == null ? -1 : expanded.getHeight())
                        + " cAlpha=" + contracted.getAlpha()
                        + " eAlpha=" + (expanded == null ? -1 : expanded.getAlpha())
                        + " rowH=" + row.getHeight());
                return;
            } catch (Throwable t) {
                Log.w(TAG, "notify_row: debug collapse call failed", t);
            }
        }
        for (View row : rows) {
            if (row == null || !row.isShown() || STACK_ROWS.containsKey(row)) {
                continue;
            }
            View content = contentViewIn(row);
            View contracted = content == null ? null : currentContracted(content);
            if (content == null || contracted == null) {
                continue;
            }
            View expanded = currentExpanded(content);
            try {
                XposedHelpers.callMethod(row, "setUserExpanded", true);
                Log.i(TAG, "notify_row: debug expand pkg="
                        + (sbnOf(row) == null ? "?" : sbnOf(row).getPackageName())
                        + " via=row cH=" + contracted.getHeight()
                        + " eH=" + (expanded == null ? -1 : expanded.getHeight())
                        + " cAlpha=" + contracted.getAlpha()
                        + " eAlpha=" + (expanded == null ? -1 : expanded.getAlpha())
                        + " cVis=" + contracted.getVisibility()
                        + " eVis=" + (expanded == null ? -1 : expanded.getVisibility())
                        + " rowH=" + row.getHeight());
                return;
            } catch (Throwable t) {
                Log.w(TAG, "notify_row: debug expand call failed", t);
            }
        }
        Log.i(TAG, "notify_row: debug expand none roots=" + roots.size()
                + " notShown=" + notShown + " noRow=" + noRow + " summary=" + summary
                + " noContracted=" + noContracted + " pairs=" + cls);
    }

    /** 行内真正承载收起/展开子视图的 NotificationContentView */
    private static View contentViewIn(View v) {
        if (v.getClass().getName().contains("NotificationContentView")) {
            return v;
        }
        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) {
                View hit = contentViewIn(g.getChildAt(i));
                if (hit != null) {
                    return hit;
                }
            }
        }
        return null;
    }

    private static void triggerReverify() {
        List<View> roots;
        synchronized (VERIFY_INFO) {
            roots = new java.util.ArrayList<>(VERIFY_INFO.keySet());
        }
        for (View root : roots) {
            if (root == null || !root.isShown()) {
                continue;
            }
            synchronized (VERIFIED) {
                VERIFIED.remove(root);
            }
            View content = root.getParent() instanceof View ? (View) root.getParent() : null;
            View row = content == null ? null : rowOf(content);
            StatusBarNotification sbn = row == null ? null : sbnOf(row);
            if (sbn == null) {
                continue;
            }
            hideSmallIcons(root);
            scheduleVerify(root, row, sbn, String.valueOf(root.getTag()), false,
                    isGroupSummary(sbn), currentExpanded(content) == root,
                    HEADSUP_ROOTS.containsKey(root), 0);
        }
        List<View> overlayViews;
        synchronized (OVERLAYS) {
            overlayViews = new java.util.ArrayList<>(OVERLAYS.keySet());
        }
        for (View contentView : overlayViews) {
            IosNotificationRow overlay = OVERLAYS.get(contentView);
            if (overlay == null || !overlay.isShown()) {
                continue;
            }
            View root = currentContracted(contentView);
            View row = rowOf(contentView);
            StatusBarNotification sbn = row == null ? null : sbnOf(row);
            if (root == null || sbn == null) {
                continue;
            }
            guardOverlay(contentView, root, overlay);
            scheduleOverlayVerify(contentView, overlay, root, row, sbn, 0);
        }
        List<View> stackViews;
        synchronized (STACK_OVERLAYS) {
            stackViews = new java.util.ArrayList<>(STACK_OVERLAYS.keySet());
        }
        for (View contentView : stackViews) {
            IosNotificationRow overlay = STACK_OVERLAYS.get(contentView);
            View[] info = STACK_VERIFY_INFO.get(contentView);
            if (overlay == null || info == null || !overlay.isShown()) {
                continue;
            }
            View root = info[0];
            View row = info[1];
            StatusBarNotification front = stackFrontOf(row);
            if (front == null) {
                continue;
            }
            guardStack(contentView, row, root, overlay,
                    findClass(row, "NotificationBackgroundView"));
            scheduleStackVerify(contentView, overlay, root, row, front,
                    frontCountOf(row), 0);
        }
        Log.i(TAG, "notify_row: reverify roots=" + roots.size()
                + " overlays=" + overlayViews.size()
                + " stacks=" + stackViews.size());
    }

    private static StatusBarNotification stackFrontOf(View rowView) {
        return newestChildSbn(rowView);
    }

    private static int frontCountOf(View rowView) {
        return childCountOf(rowView);
    }

    /** 摘掉原生优化的大图标，并还原被隐藏的原生 header 小图标 */
    private static void clearLightBigIcon(View root, String why) {
        View nativeIcon = LIGHT_NATIVE_ICON.remove(root);
        if (nativeIcon != null) {
            nativeIcon.setAlpha(1f);
            // 按住期间压过 scale/可见性，必须一起恢复：否则大图标被摘掉后原生小图标永不回来
            suppressNativeIcon(nativeIcon, false);
            int[] old = LIGHT_ICON_LP.remove(nativeIcon);
            if (old != null) {
                ViewGroup.LayoutParams lp = nativeIcon.getLayoutParams();
                if (lp != null) {
                    lp.width = old[0];
                    lp.height = old[1];
                    nativeIcon.setLayoutParams(lp);
                }
            }
        }
        boolean removed = false;
        if (root instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) root;
            for (int i = g.getChildCount() - 1; i >= 0; i--) {
                View c = g.getChildAt(i);
                if (TAG_LIGHT_BIG.equals(c.getTag())) {
                    // 标签一起清掉：正在淡出的那枚如果被这轮复用，它的 endAction 就不该
                    // 再来摘除/清标记（否则会把刚 polish 完的行标成"没打磨"）。
                    c.setTag(null);
                    g.removeViewAt(i);
                    removed = true;
                }
            }
        }
        if (sVerifyEnabled && removed) {
            // "图标瞬间消失"到底是哪条路径摘的，只有这条日志能回答；硬删都是观感缺陷的候选
            Log.i(TAG, "notify_row.light.icon removed why=" + why + " row="
                    + Integer.toHexString(System.identityHashCode(root)));
        }
    }

    /** 淡出结束后收尾：真的摘掉图标，并让补扫有权在它再次长高时重新 polish。 */
    private static void retireLightIcon(View root) {
        clearLightBigIcon(root, "fade-out");
        View cv = lightContentViewOf(root);
        if (cv == null) {
            return;
        }
        synchronized (LIGHT_POLISHED) {
            LIGHT_POLISHED.remove(cv);
        }
        View row = rowOf(cv);
        if (row != null) {
            synchronized (HEAL_TRIES) {
                HEAL_TRIES.remove(row);
            }
        }
    }

    /** 从图标宿主往上找那一行自己的 NotificationContentView */
    private static View lightContentViewOf(View v) {
        View cur = v;
        for (int i = 0; i < 6 && cur != null; i++) {
            if (cur.getClass().getName().endsWith(".NotificationContentView")) {
                return cur;
            }
            ViewParent p = cur.getParent();
            cur = p instanceof View ? (View) p : null;
        }
        return null;
    }

    /**
     * OS5 light：原生收起行的标题/正文从 12dp 起排，会被左侧 42dp 大图标压住；
     * 按 OS5 内容列（66dp = 16 间距 + 42 图标 + 8 间隙）右移，header 的应用名/时间也对齐到同一列。
     * 行会被复用，最终内边距记在 view 上，重复调用不会累加。
     */
    private static void alignLightColumn(Context ctx, View root, TextView anchor) {
        try {
            View box = null;
            if (anchor != null) {
                // 只给文字列加偏移；展开视图里 header 与文字列可能共用一个父容器，
                // 加到父容器会把应用名一起推走
                box = androidView(root, "notification_main_column");
                if (box == null) {
                    View topContent = directChildOf(root, anchor);
                    box = topContent == null ? anchor
                            : directChildOf(topContent, anchor);
                    if (box == null) {
                        box = anchor;
                    }
                }
                int[] pad = LIGHT_COLUMN_BOX.get(box);
                if (pad == null) {
                    int target = NotifyRowSpec.dp(ctx, NotifyRowSpec.COLUMN_DP);
                    int endTarget = NotifyRowSpec.dp(ctx, 16);
                    pad = new int[]{
                            box.getPaddingStart()
                                    + Math.max(0, target - startOffset(root, anchor)),
                            box.getPaddingEnd()
                                    + Math.max(0, endTarget - endOffset(root, anchor))};
                    LIGHT_COLUMN_BOX.put(box, pad);
                }
                box.setPaddingRelative(pad[0], box.getPaddingTop(),
                        pad[1], box.getPaddingBottom());
                // 收起态标题上提必须走布局：translation 会把标题顶进 header 被盖住。
                // 找到真正带 topMargin 的文字容器（headerless column/row），减 8dp；
                // header 同步压到 34dp，二者不会重叠。
                View marginView = findTopMarginContainer(box,
                        NotifyRowSpec.dp(ctx, LIGHT_TITLE_RAISE_DP));
                if (marginView != null) {
                    ViewGroup.LayoutParams blp = marginView.getLayoutParams();
                    if (blp instanceof ViewGroup.MarginLayoutParams) {
                        ViewGroup.MarginLayoutParams mlp = (ViewGroup.MarginLayoutParams) blp;
                        Integer origTop = LIGHT_COLUMN_MARGIN.get(marginView);
                        if (origTop == null) {
                            origTop = mlp.topMargin;
                            LIGHT_COLUMN_MARGIN.put(marginView, origTop);
                        }
                        mlp.topMargin = Math.max(0,
                                origTop - NotifyRowSpec.dp(ctx, LIGHT_TITLE_RAISE_DP));
                        marginView.setLayoutParams(mlp);
                    }
                }
            }

            View header = findClass(root, "NotificationHeaderView");
            if (header != null) {
                Integer padStart = LIGHT_COLUMN_HEADER.get(header);
                if (padStart == null) {
                    int iconW = 0;
                    int iconEnd = 0;
                    View nativeIcon = LIGHT_NATIVE_ICON.get(root);
                    if (nativeIcon != null) {
                        iconW = NotifyRowSpec.dp(ctx, 16);
                        ViewGroup.LayoutParams lp = nativeIcon.getLayoutParams();
                        if (lp != null && lp.width > 0) {
                            iconW = lp.width;
                        }
                        if (lp instanceof ViewGroup.MarginLayoutParams) {
                            iconEnd = ((ViewGroup.MarginLayoutParams) lp).getMarginEnd();
                        }
                    }
                    padStart = Math.max(header.getPaddingStart(),
                            NotifyRowSpec.dp(ctx, NotifyRowSpec.COLUMN_DP) - iconW - iconEnd);
                    LIGHT_COLUMN_HEADER.put(header, padStart);
                }
                header.setPaddingRelative(padStart, header.getPaddingTop(),
                        header.getPaddingEnd(), header.getPaddingBottom());
                ViewGroup.LayoutParams hlp = header.getLayoutParams();
                if (hlp != null) {
                    int h = NotifyRowSpec.dp(ctx, NotifyRowSpec.HEADER_HEIGHT_DP);
                    if (hlp.height != h) {
                        hlp.height = h;
                        header.setLayoutParams(hlp);
                    }
                }
                if (sVerifyEnabled) {
                    Log.i(TAG, "notify_row.light.geo hdrH=" + header.getHeight()
                            + " hdrLp=" + lpInfo(header) + " boxLp=" + lpInfo(box));
                }
            }
        } catch (Throwable ignored) {
        }
    }

    /** 从文字列往上找真正负责"距 header 多远"的容器（topMargin 足够大的那一层） */
    private static View findTopMarginContainer(View start, int need) {
        View cur = start;
        for (int i = 0; i < 4 && cur != null; i++) {
            ViewGroup.LayoutParams lp = cur.getLayoutParams();
            if (lp instanceof ViewGroup.MarginLayoutParams
                    && ((ViewGroup.MarginLayoutParams) lp).topMargin >= need) {
                return cur;
            }
            ViewParent p = cur.getParent();
            cur = p instanceof View ? (View) p : null;
        }
        return null;
    }

    private static String lpInfo(View v) {
        ViewGroup.LayoutParams lp = v == null ? null : v.getLayoutParams();
        if (lp == null) {
            return "null";
        }
        String s = lp.width + "x" + lp.height;
        if (lp instanceof ViewGroup.MarginLayoutParams) {
            ViewGroup.MarginLayoutParams m = (ViewGroup.MarginLayoutParams) lp;
            s += " m=" + m.leftMargin + "," + m.topMargin + ","
                    + m.rightMargin + "," + m.bottomMargin;
        }
        return s;
    }

    /** 组子行才做 alpha 过渡：普通行绑定时硬切，滚动列表逐行淡入反而更花。 */
    private static final WeakHashMap<View, Boolean> LIGHT_ICON_CHILD = new WeakHashMap<>();
    /** 图标的淡出/淡入目标：布局回调每帧都会走 fadeLightIcon，靠它避免每帧重启动画。 */
    private static final WeakHashMap<View, Float> LIGHT_ICON_WANT = new WeakHashMap<>();
    private static final android.view.animation.Interpolator ICON_FADE_INTERP =
            new android.view.animation.PathInterpolator(0.25f, 0.1f, 0.25f, 1f);

    /**
     * 大图标 alpha 过渡。target=0 时**动画真正结束才摘除**：收起过程中用户又展开的话，
     * 后一次 animate() 取消前一次，endAction 不执行（框架保证），图标就从当前半透明
     * 顺势淡回来，不会出现"消失一帧再冒出来"。
     */
    private static void fadeLightIcon(final View root, final View icon, final float target,
                                      final String why) {
        if (root == null || icon == null) {
            return;
        }
        float from = icon.getAlpha();
        if (Math.abs(from - target) < 0.01f) {
            return;      // 已经在目标上：不重启动画，也不重新排 endAction
        }
        synchronized (LIGHT_ICON_WANT) {
            Float want = LIGHT_ICON_WANT.get(icon);
            // 已经在往这个方向淡了：布局回调每帧都会走这里，每帧重启就等于淡不完
            if (want != null && Math.abs(want.floatValue() - target) < 0.01f) {
                return;
            }
            LIGHT_ICON_WANT.put(icon, Float.valueOf(target));
        }
        if (sVerifyEnabled) {
            Log.i(TAG, "notify_row.light.icon fade=" + (target > from ? "in" : "out")
                    + " from=" + String.format(java.util.Locale.US, "%.2f", from)
                    + " to=" + String.format(java.util.Locale.US, "%.2f", target)
                    + " ms=" + NotifyRowSpec.ICON_FADE_MS + " why=" + why
                    + " h=" + root.getHeight()
                    + " row=" + Integer.toHexString(System.identityHashCode(root)));
            sampleFade(icon, why);
        }
        if (target <= 0.5f) {
            // 淡出时把原生小图标同时淡进来：只留大图标独自消失，尾巴上会空一帧再"蹦"出小图标
            View nat = LIGHT_NATIVE_ICON.get(root);
            if (nat != null && nat.getAlpha() > 0.01f) {
                nat.setAlpha(0f);
                // 按住期间它是 scale=0 + INVISIBLE：不先放开，alpha 动画就动画在画不出来的视图上，
                // 大图标淡完之后那一格会一直空着
                suppressNativeIcon(nat, false);
                nat.animate().alpha(1f).setDuration(NotifyRowSpec.ICON_FADE_MS)
                        .setInterpolator(ICON_FADE_INTERP).start();
            }
        }
        icon.animate().alpha(target).setDuration(NotifyRowSpec.ICON_FADE_MS)
                .setInterpolator(ICON_FADE_INTERP)
                .withEndAction(new Runnable() {
                    @Override
                    public void run() {
                        synchronized (LIGHT_ICON_WANT) {
                            LIGHT_ICON_WANT.remove(icon);
                        }
                        // 标签已被摘走 = 这枚被后一次 polish 复用了，别去动那一行的状态
                        if (target > 0.5f || !TAG_LIGHT_BIG.equals(icon.getTag())) {
                            return;
                        }
                        retireLightIcon(root);
                    }
                })
                .start();
    }

    /** 过渡是否真在爬：60/120/180ms 三个 alpha 读数就能证伪"其实还是硬切"。 */
    private static void sampleFade(final View icon, final String why) {
        for (int i = 1; i <= 3; i++) {
            final int step = i;
            icon.postDelayed(safe(() -> Log.i(TAG, "notify_row.light.icon ramp t="
                    + (step * 60) + "ms alpha="
                    + String.format(java.util.Locale.US, "%.2f", icon.getAlpha())
                    + " why=" + why)), step * 60L);
        }
    }

    /** 宿主是否已回到紧凑模板（真机实测：收起 57px，展开 200~300px） */
    private static boolean isCompactHost(View host) {
        return host != null && host.getHeight() > 0
                && host.getHeight() < NotifyRowSpec.dp(host.getContext(), 70);
    }

    private static boolean isChildIcon(View icon) {
        if (icon == null) {
            return false;
        }
        synchronized (LIGHT_ICON_CHILD) {
            return Boolean.TRUE.equals(LIGHT_ICON_CHILD.get(icon));
        }
    }

    /**
     * 这枚图标是否正在淡出（或已淡完还没被摘走）。对位与"这一轮该建图标"的判定都要认它，
     * 否则收起动画里会出现：图标正在淡、却又被重新 align/重新 polish 成 alpha=1。
     */
    private static boolean isRetiringIcon(View icon) {
        if (icon == null) {
            return false;
        }
        synchronized (LIGHT_ICON_WANT) {
            Float w = LIGHT_ICON_WANT.get(icon);
            return w != null && w.floatValue() <= 0.5f;
        }
    }

    /** 这一轮不该有图标：有就淡出（动画结束才真摘），没有就只还原被我们按住的原生小图标。 */
    private static void dropLightIcon(View root, View existing, String why) {
        if (existing != null) {
            fadeLightIcon(root, existing, 0f, why);
        } else {
            clearLightBigIcon(root, why);
        }
    }

    /** 收起入口的见证配额：每翻一次打一行，封顶 8 条；取样本身不许把别的证据挤出缓冲区。 */
    private static int sCollapseWitnesses;

    /** 收集一棵子树里我们自加的大图标：组收起时要把它们一次性淡掉。 */
    private static void collectLightIcons(ViewGroup g, java.util.List<View> out, int depth) {
        if (g == null || depth > 6) {
            return;
        }
        for (int i = 0; i < g.getChildCount(); i++) {
            View c = g.getChildAt(i);
            if (c == null) {
                continue;
            }
            if (TAG_LIGHT_BIG.equals(c.getTag())) {
                out.add(c);
            } else if (c instanceof ViewGroup) {
                collectLightIcons((ViewGroup) c, out, depth + 1);
            }
        }
    }

    /**
     * OS5 light：42dp 大图标纵向**居中于卡片**（8dp 内缩），文字块只作为"布局已就绪"的判据。
     *
     * 历史口径换过两次：纯按文字块居中在 A13 上低 30px（卡片顶上还有 34dp header），
     * 于是改成"文字块中心与卡片中心的中点"，结果在组子行上稳定低 24px ——
     * 那是公式自己的落点，不是没算对，补校多少轮都出不了验收线（L6 ≤20px）。
     */
    private static void alignLightIcon(final View root, final View icon, final int size) {
        alignLightIcon(root, icon, size, 0);
    }

    /**
     * 对齐输入的对数配额（每容器 3 条）：只打一次看不到收敛过程，
     * 每次 pass 都打又会把别的见证从日志里挤掉 —— L6 停在 +24px 时，
     * 必须看得出"最后一次写的是哪个 want"。
     */
    private static final WeakHashMap<View, Integer> ALIGN_LOGGED = new WeakHashMap<>();

    private static synchronized boolean alignLogOnce(View v) {
        if (v == null) {
            return false;
        }
        if (ALIGN_LOGGED.size() > 64) {
            ALIGN_LOGGED.clear();
        }
        int n = ALIGN_LOGGED.get(v) == null ? 0 : ALIGN_LOGGED.get(v);
        if (n >= 3) {
            return false;
        }
        ALIGN_LOGGED.put(v, n + 1);
        return true;
    }

    /**
     * pass 用来做"布局落定后再校一次"：polish 发生时行高常常还没长到最终值
     * （真机见过 root 只有 138px 而行是 255px），那一刻的钳位会把图标压在顶部，
     * 于是用户看到"这一条的图标偏上/偏下"。补两次（250ms / 700ms）即可覆盖重排。
     */
    private static void alignLightIcon(final View root, final View icon, final int size,
            final int pass) {
        try {
            TextView title = text(root, "title");
            TextView body = firstText(root, "text", "big_text", "always");
            View topV = title != null ? title : body;
            View bottomV = body != null && body != title ? body : title;
            if (topV == null || bottomV == null
                    || topV.getHeight() <= 0 || bottomV.getHeight() <= 0) {
                if (sVerifyEnabled && alignLogOnce(root)) {
                    // 分不清"算错"还是"压根没算"时，这条就是那半个答案
                    Log.i(TAG, "notify_row.align pass=" + pass + " no-text"
                            + " title=" + (title == null ? "null" : title.getHeight())
                            + " body=" + (body == null ? "null" : body.getHeight())
                            + " h=" + root.getHeight());
                }
                return;
            }
            View parent = icon.getParent() instanceof View ? (View) icon.getParent() : root;
            int[] tl = new int[2];
            int[] bl = new int[2];
            int[] pl = new int[2];
            topV.getLocationOnScreen(tl);
            bottomV.getLocationOnScreen(bl);
            parent.getLocationOnScreen(pl);
            // 参照物 = 图标自己的宿主（status_bar_latest_event_content）。
            // 真机取证否掉了"往上找 ExpandableNotificationRow"那版：组子行的祖先链上
            // 会先撞到一个 297px 的孪生行（同一行的另一份拷贝/过渡态），而 dump 里可见的行
            // 是 255px —— 按 297 的中点摆，图标就稳定低 (297-255)/2 ≈ 21px（L6 的那 20.5px）。
            // 宿主与可见行在四行取证里逐条同界（1107..1362 == 1107..1362），所以以宿主为准。
            int cardTop = pl[1];
            int cardH = parent.getHeight();
            if (cardH <= 0) {
                cardH = root.getHeight();
            }
            // 全程用**屏幕坐标**算，最后才换算成父容器里的 translationY。
            // 旧写法把 root（内容 FrameLayout）当卡片：A13 上它常比行矮一截，
            // `max = root.h - size` 的钳位就把图标钉在 root 顶部（真机 138px 那帧留下 -52px），
            // 而验收 L6 量的是**行**的中心 —— 两边不同源，怎么补校都收敛不回去。
            // 目标 = **卡片中心**（用户原话就是"大图标没居中"），8dp 内缩保证不越界；
            // 文字块只用来判断布局是否就绪。旧口径取"文字块中心与卡片中心的中点"，
            // 在组子行上稳定低 24px —— 那是公式本身的位置，补校再多轮也收敛不到验收线内。
            int textCenter = (tl[1] + bl[1] + bottomV.getHeight()) / 2;
            int cardCenter = cardTop + cardH / 2;
            int inset = NotifyRowSpec.dp(icon.getContext(), 8);
            int minCenter = cardTop + inset + size / 2;
            int maxCenter = cardTop + Math.max(0, cardH - inset) - size / 2;
            int center = maxCenter < minCenter ? cardCenter
                    : Math.max(minCenter, Math.min(cardCenter, maxCenter));
            int want = center - size / 2;
            int min = Math.max(cardTop, pl[1]);
            int maxTop = Math.min(cardTop + Math.max(0, cardH - size),
                    pl[1] + Math.max(0, parent.getHeight() - size));
            if (maxTop < min) {
                maxTop = min;
            }
            boolean grew = want > maxTop;      // 卡片还没长到位：等布局落定再校，别现在就钳死
            want = Math.max(min, Math.min(want, maxTop));
            int baseTop = pl[1] + icon.getTop();
            int drift = (want + size / 2) - (cardTop + cardH / 2);
            if (sVerifyEnabled && alignLogOnce(root)) {
                // 分不清"算错"还是"压根没算"时，这条就是那半个答案：输入量与钳位前后一起打。
                Log.i(TAG, "notify_row.align pass=" + pass + " textC=" + textCenter
                        + " card=[" + cardTop + "+" + cardH + "] rootH=" + root.getHeight()
                        + " parentH=" + parent.getHeight() + " want=" + want
                        + " base=" + baseTop + " ty=" + icon.getTranslationY()
                        + " drift=" + drift + " grew=" + grew);
            }
            // 已经在这个位置上就**不写**：布局监听会让这里每轮都被调用，
            // 无条件 setTranslationY 正是"点展开时所有大图标抖一下"的源头之一。
            if (Math.abs(baseTop + icon.getTranslationY() - want) > 1f) {
                icon.setTranslationY(want - baseTop);
            }
            // 收敛循环：**不管这一帧有没有钳位**都再校几次，直到落在 ±12px 内。
            // 只在 grew（被钳）时才补校是上一版的失效方式：A13 上 pass=0 那帧卡片只有 255px、
            // 297px 是之后才长出来的，没钳位不等于位置对，于是图标永久停在 +25px（验收 L6 红）。
            if (pass < 4 && (grew || Math.abs(drift) > 12)) {
                final int next = pass + 1;
                icon.postDelayed(safe(() -> alignLightIcon(root, icon, size, next)),
                        next <= 2 ? 250 : 700);
            }
        } catch (Throwable ignored) {
        }
    }

    /**
     * 把纵向对位挂到宿主的布局回调上。
     *
     * translationY 是"按当时宿主的几何算出来的偏移"，而图标本身是 CENTER_VERTICAL 布局的：
     * 宿主随后被 vivo 重新布局（长高或整体位移）时，布局位置自己变了，我们写的偏移却没变 ——
     * 真机上图标就停在"低 20px"那种固定增量上，补校轮数再多也只是在旧增量上再猜一次。
     * 写入本身有判等跳过（见 alignLightIcon），所以每轮布局都回调也不会抖。
     */
    private static final WeakHashMap<View, View> ALIGN_LAYOUT_HOOK = new WeakHashMap<>();

    private static void hookAlignOnLayout(View host, final View root, final View icon,
                                          final int size) {
        if (host == null) {
            return;
        }
        synchronized (ALIGN_LAYOUT_HOOK) {
            if (ALIGN_LAYOUT_HOOK.get(host) == icon) {
                return;          // 同一宿主 + 同一图标只挂一次
            }
            ALIGN_LAYOUT_HOOK.put(host, icon);
        }
        host.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
            @Override
            public void onLayoutChange(View v, int left, int top, int right, int bottom,
                                       int oldLeft, int oldTop, int oldRight, int oldBottom) {
                // 尺寸**或位置**变了都要重算：vivo 会把内容框整体下移而不改它的高度，
                // 只听高度变化就漏掉这一类，图标照样停在"低 20px"。
                // 真正的写入由 alignLightIcon 自己判等跳过，所以这里不需要再节流。
                if (isChildIcon(icon)) {
                    if (isCompactHost(host)) {
                        // 组收起：宿主回到紧凑高度就先把图标淡出去。
                        // 等下一次 bind 再删，用户看到的就是"字压上去后再瞬间消失"。
                        fadeLightIcon(host, icon, 0f, "child-collapse");
                    } else if (isRetiringIcon(icon)) {
                        // 淡出还没走完用户又展开了：顺势淡回来（动画被取消，摘除不会执行）
                        fadeLightIcon(host, icon, 1f, "child-reopen");
                    }
                }
                if (!isRetiringIcon(icon)) {
                    alignLightIcon(root, icon, size);
                }
            }
        });
    }

    /** 按住"露出来的原生小图标"，并把这个状态读回来当证据（每行只报一次，别淹掉别的见证）。 */
    private static final java.util.Set<String> STRAY_NATIVE_SEEN =
            java.util.Collections.synchronizedSet(new java.util.HashSet<String>());

    /**
     * 按住/放开原生 header 小图标。
     *
     * 为什么不是"压 alpha"：逐帧实测（`notify_row.trace`）显示 vivo 每次重排都把这颗图标的
     * visibility 与 alpha 一起写回 `0:1.00`，我们下一帧再按成 `4:0.00` —— 两边逐帧对写，
     * 中间那一帧就是用户看到的"别的通知小图标闪一下"。alpha/可见性都是**它的领地**，
     * 而 drawable 自己的 alpha 它不碰：压成 0 之后视图再怎么被抬回可见也画不出东西，
     * 且 drawable 还在 ⇒ 图标的 measure 尺寸不变（摘成 null 会让 header 的列数学塌掉，
     * 真机门禁当场 L3 红）。
     */
    /** 取栈里第一个非本类、非 JDK 的帧，只给诊断用（栈展开比字符串裁剪便宜不了多少，别常开）。 */
    private static String callerName() {
        try {
            StackTraceElement[] st = Thread.currentThread().getStackTrace();
            for (StackTraceElement e : st) {
                String c = e.getClassName();
                if (c.contains("NotifyRowFeature") || c.startsWith("java.")
                        || c.startsWith("android.os.") || c.startsWith("dalvik.")) {
                    continue;
                }
                int dot = c.lastIndexOf('.');
                return c.substring(dot + 1) + "." + e.getMethodName() + ":" + e.getLineNumber();
            }
        } catch (Throwable ignored) {
        }
        return "?";
    }


    /**
     * 顶替物是否**真的在显示**，而不是只存在。
     * 真机逐帧读数（11-05 上午）：`row=2a04976 h=332` 我们的 42dp 图标 `big=L48:W126:s0`
     * （在但不显示），而原生那颗 `da0`（被我们压掉）⇒ 图标位空白。
     * 根因在 polish 里有一条 `groupChild && recycled==null → bigIcon.setAlpha(0f)`：
     * 淡入没跑，图标就是自己抹掉的。所以"有没有顶替"必须看 isShown + alpha，
     * 只看 findTag 会把"我们把它藏了、又把原生也藏了"当成合法状态。
     */
    /** 逐帧诊断用：一棵子树里所有"图标类节点"（与 collectHideTargets 的收法一致）。 */
    private static void collectIconViews(View v, java.util.List<View> out, int depth) {
        if (v == null || depth > 10 || out.size() > 5) {
            return;
        }
        if (isIconView(v)) {
            out.add(v);
        }
        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) {
                collectIconViews(g.getChildAt(i), out, depth + 1);
            }
        }
    }

    /**
     * 这一棵子树里有没有**我们自己的顶替大图标**（归属判据，不是"此刻可见"判据）。
     *
     * 不许读 alpha / isShown：展开/收起动画里 vivo 会把子树 alpha 一起写回、`isShown()` 跟着翻
     * （逐帧实测同一行 `big=…:s0`→`:s1`→`:s0`）。上一版把判据写成"真的在显示才压"，
     * 于是过渡那几帧判成"没有顶替"、把原生小图标放回去 ⇒ 用户看到的
     * "展开或收起通知时小图标闪一下"。
     *
     * 判据**按子树**而不是按行（11-05 试过行级判据）：一份行的两份 header 拷贝里大图标常常只在
     * 其中一份，按行判会把另一份的原生图标也压掉 ⇒ 真机读数里 `big=-` 的摘要行变成空白图标，
     * 是一种观感换成另一种观感。两份拷贝共用同一个 Drawable 的互打由
     * {@link #privateDrawable} 从通道上断开，不靠放宽判据。
     *
     * 唯一保留的硬条件是"顶替物真的有图"：drawable 为空时那枚 42dp 壳会露出空白圆角边框
     * （`android` 包取不到应用图标那类），此时让它自己 GONE 并放回原生。
     */
    private static boolean bigIconOwned(View root) {
        View big = root == null ? null : findTag(root);
        if (big == null) {
            return false;
        }
        if (big instanceof ImageView) {
            ImageView iv = (ImageView) big;
            if (iv.getDrawable() == null) {
                // 关键一步：没有前景图的顶替图标必须**自己消失**。它带的是圆角背景，
                // 留着就是一层空壳糊在原生图标上 —— 用户看到的"空白圆角矩形"就是这个壳
                //（Android 系统 这类 `android` 包取不到应用图标）。有图时再自动回来。
                if (iv.getVisibility() != View.GONE) {
                    iv.setVisibility(View.GONE);
                }
                return false;
            }
            if (iv.getVisibility() == View.GONE) {
                iv.setVisibility(View.VISIBLE);
            }
        }
        return true;
    }

    /**
     * 原生小图标的唯一压制入口。判据（该不该压）由调用方按**子树归属**决定
     * （见 {@link #bigIconOwned}），这里只负责"写哪个通道"和"已经是目标值就别写"。
     */
    private static void suppressNativeIcon(View ni, boolean on) {
        if (ni == null) {
            return;
        }
        // 为什么不是"压视图 alpha/可见性"：逐帧实测（notify_row.trace）证明 vivo 每次重排会把
        // 这四项一起写回可见 ⇒ 按任何单一视图属性都是逐帧对写，中间那一帧就是"闪一下"。
        // 真正不争抢的只有 **drawable 自己的 alpha**：vivo 的图标更新才会换 drawable，
        // 换的那一帧我们在守卫里再压一次即可（见 guardLightNativeIcon）。
        // （更正 11-05：这里原先记的是"我们自己的放开路径一条都没跑过"，那是被旧见证的
        //   `scaleX==0` 条件骗了——放开一直在跑，只是那条见证抓不到。放开判据见下方 RELEASE。）
        int wasDrawable = -1;
        if (ni instanceof ImageView) {
            ImageView iv = (ImageView) ni;
            android.graphics.drawable.Drawable d = iv.getDrawable();
            if (d != null) {
                wasDrawable = d.getAlpha();
                // 不能把 drawable 摘成 null：那颗图标是 wrap_content，没有 drawable 就 measure 成 0，
                // header 的列数学跟着塌 ⇒ 真机门禁当场 L3 红（标题左偏移 60px 而不是 66dp 列）。
                // 所以改压 **drawable 自己的 alpha**：尺寸、布局、可见性全不动。
                // 而且已经是目标值就别再写：这条跑在每帧 pre-draw 上，`mutate()` + setAlpha
                // 每帧都做一次就是一台永不停下的重绘机（窗口永不 idle、dump 取不到样本）。
                if (wasDrawable != (on ? 0 : 255)) {
                    d.mutate().setAlpha(on ? 0 : 255);
                }
            }
        }
        // 视图 alpha/可见性只是"顺手维持"：真机证明 vivo 每次重排会把它们抬回可见，
        // 所以不再依赖它们（连 scale 也一样守不住，已经去掉），真正挡画的是上面的 drawable alpha。
        if (on) {
            if (ni.getAlpha() != 0f) {
                ni.setAlpha(0f);
            }
            if (ni.getVisibility() == View.VISIBLE) {
                ni.setVisibility(View.INVISIBLE);
            }
        } else {
            if (ni.getVisibility() != View.VISIBLE) {
                ni.setVisibility(View.VISIBLE);
            }
        }
        // 放开见证：只在"我们确实要把一枚**被压成 0** 的图标放回可见"那一拍打。
        // 判据别写成 `!= 255`：vivo 自己的图标 alpha 本来就可能不是 255，那样这条会退化成
        // "每次调用都打一条"（真机一度 502 条），把"我们在恢复"和"图标本来就不是全实"混成一谈。
        // 判"图标忽有忽无"时这条是分诊第一现场：出现 ⇒ 我们自己在放；不出现 ⇒ 是系统抬的。
        if (!on && sVerifyEnabled && wasDrawable == 0) {
            Log.i(TAG, "notify_row.light.native RELEASE id="
                    + Integer.toHexString(System.identityHashCode(ni))
                    + " wasDa=" + wasDrawable
                    + " from=" + callerName());
        }
    }

    private static void pressStrayNativeIcon(View root, String where) {
        View ni = LIGHT_NATIVE_ICON.get(root);
        if (ni == null) {
            // 登记丢了（或这一行的一份孪生根从来没按过）：先找回来再按，并把逐帧守卫补挂上，
            // 否则 vivo 下一次写回 alpha=1 之后就再也没人压回去 —— 那就是持续露出，不是闪一帧
            ni = findClass(root, "CachingIconView");
            if (ni == null) {
                return;
            }
            LIGHT_NATIVE_ICON.put(root, ni);
            guardLightNativeIcon(root);
        }
        // 只有"这一行有我们那枚 42dp 大图标顶替它"才允许压。
        // 组摘要行按策略保留原生小图标、不放大图标（L7）—— 一旦这里无差别压掉，
        // drawable alpha=0 又没有顶替物，就是真机报的"图标变成空白边框"
        //（Android 系统 / vivo服务 都是组摘要行）；而且下面 guardLightNativeIcon 的
        // 每帧 pre-draw 会把它反复压回去，永不恢复。没有顶替就反过来恢复可见。
        if (!bigIconOwned(root)) {
            suppressNativeIcon(ni, false);
            return;
        }
        if (ni.getAlpha() == 0f && ni.getVisibility() != View.VISIBLE) {
            return;
        }
        float was = ni.getAlpha();
        int wasVis = ni.getVisibility();
        suppressNativeIcon(ni, true);
        if (!sVerifyEnabled) {
            return;
        }
        String key = "stray|" + Integer.toHexString(System.identityHashCode(root));
        synchronized (STRAY_NATIVE_SEEN) {
            if (STRAY_NATIVE_SEEN.size() > 64 || !STRAY_NATIVE_SEEN.add(key)) {
                return;
            }
        }
        Log.i(TAG, "notify_row.light.native stray alpha="
                + String.format(java.util.Locale.US, "%.2f", was)
                + " where=" + where + " vis=" + wasVis + " row="
                + Integer.toHexString(System.identityHashCode(root)));
    }

    /** 原生图标隐藏的逐帧兜底：原生更新可能把 small icon alpha 写回 1 */
    private static void guardLightNativeIcon(final View root) {
        final ViewTreeObserver vto = root.getViewTreeObserver();
        synchronized (LIGHT_ICON_GUARD) {
            ViewTreeObserver old = LIGHT_ICON_GUARD.get(root);
            if (old == vto && vto.isAlive()) {
                return;
            }
            LIGHT_ICON_GUARD.put(root, vto);
        }
        vto.addOnPreDrawListener(preDrawGuard(() -> {
            // vivo 每次重排都把这颗图标的 visibility/alpha 写回可见（逐帧见证 0:1.00），
            // 我们同通道按回去就成了逐帧对写 —— 中间那一帧就是用户看到的"小图标闪一下"。
            // 所以压制改走 scale（它从不碰那条），alpha/可见性只是顺手维持。
            View ni = LIGHT_NATIVE_ICON.get(root);
            if (ni == null) {
                return;
            }
            // 同一条纪律：只有我们放了大图标顶替的行才压；否则恢复，绝不留下空白边框
            if (!bigIconOwned(root)) {
                suppressNativeIcon(ni, false);
                return;
            }
            suppressNativeIcon(ni, true);
        }));
    }

    /** light 的展开/收起图标飞行：同一枚 38dp 图标从收起位连续移动到展开位 */
    private static void guardLightMorph(final View contentView) {
        final ViewTreeObserver vto = contentView.getViewTreeObserver();
        synchronized (LIGHT_MORPH_GUARD) {
            if (LIGHT_MORPH_GUARD.get(contentView) == vto && vto.isAlive()) {
                return;
            }
            LIGHT_MORPH_GUARD.put(contentView, vto);
        }
        vto.addOnPreDrawListener(preDrawGuard(() -> {
            ImageView flying;
            synchronized (FLIGHT) {
                flying = FLIGHT.get(contentView);
            }
            // 展开动画会重新排列 contentView 的子视图，飞行图标必须逐帧置顶
            if (flying != null && flying.getVisibility() == View.VISIBLE
                    && contentView instanceof ViewGroup) {
                ViewGroup cv = (ViewGroup) contentView;
                if (cv.indexOfChild(flying) != cv.getChildCount() - 1) {
                    cv.bringChildToFront(flying);
                }
            }
            View row = rowOf(contentView);
            if (row == null) {
                return;
            }
            boolean open = bool(row, "isExpanded");
            Boolean prev;
            synchronized (ROW_OPEN) {
                prev = ROW_OPEN.get(contentView);
                ROW_OPEN.put(contentView, open);
            }
            if (prev == null || prev.booleanValue() == open) {
                return;
            }
            View contracted = currentContracted(contentView);
            View expanded = currentExpanded(contentView);
            if (sVerifyEnabled) {
                Log.i(TAG, "notify_row.light.morph flip open=" + open
                        + " con=" + (contracted != null)
                        + " exp=" + (expanded != null)
                        + " conIcon=" + (contracted != null && iconOf(contracted) != null)
                        + " expIcon=" + (expanded != null && iconOf(expanded) != null));
            }
            if (contracted != null && expanded != null) {
                morphIcon(contentView, contracted, expanded, open, null);
            }
        }));
    }

    /** light polish 的早退原因见证：只打一次/（原因+包+行），避免绑定重放刷屏。 */
    private static void lightSkip(String reason, View row, String extra) {
        if (!sVerifyEnabled) {
            return;
        }
        String pkg = "-";
        try {
            StatusBarNotification s = sbnOf(row);
            if (s != null) {
                pkg = s.getPackageName();
            }
        } catch (Throwable ignored) {
        }
        String key = reason + "|" + pkg + "|"
                + (row == null ? 0 : System.identityHashCode(row));
        if (!sLightSkips.add(key)) {
            return;
        }
        Log.i(TAG, "notify_row.light.skip reason=" + reason + " pkg=" + pkg
                + (extra == null ? "" : " " + extra));
    }

    private static final java.util.Set<String> sLightSkips =
            java.util.Collections.synchronizedSet(new java.util.HashSet<String>());

    /** witnessMarkedButEmpty 的去重：每个 (cv,可见根) 只打一次，补扫每秒都走这条不能刷屏。 */
    private static final java.util.Set<String> MARKED_EMPTY_SEEN =
            java.util.Collections.synchronizedSet(new java.util.HashSet<String>());

    /**
     * 这一行的哪一份子视图正被显示。收起/展开在 A13 上是**两棵子树**，补扫和对位都必须
     * 画在可见的那一棵上（画到隐藏那份 = 没写，过渡结束才闪回来）。
     */
    private static View visibleRootOf(View cv) {
        View root = currentContracted(cv);
        try {
            if (XposedHelpers.getIntField(cv, "mVisibleType") == 1) {
                View exp = currentExpanded(cv);
                if (exp != null) {
                    root = exp;
                }
            }
        } catch (Throwable ignored) {
        }
        return root;
    }

    /**
     * 标记过期（cv 上写着"排好了"，可见根上却没有图标）时清掉标记，让这一轮补扫重排一次。
     * 次数由 HEAL_TRIES（每行 ≤6）兜住，而不是"每个 (cv,根) 只清一次"：真机上收起再展开时，
     * 第一趟重排常落在动画中途（行还没长够高，lightPolish 自己判 group-collapsed 早退），
     * 只给一次机会就等于把这种行永久留在没有图标的状态上。
     */
    private static boolean rearmStalePolish(View cv, View shown) {
        if (shown == null) {
            return false;
        }
        synchronized (LIGHT_POLISHED) {
            LIGHT_POLISHED.remove(cv);
        }
        if (sVerifyEnabled) {
            Log.i(TAG, "notify_row.heal rearm cv=" + Integer.toHexString(System.identityHashCode(cv))
                    + " root=" + Integer.toHexString(System.identityHashCode(shown)));
        }
        return true;
    }

    /**
     * "去重标记说这行排好了，但可见的那一份根上找不到我们的图标"的见证（每个 cv|root 只打一次）。
     * 没有它，"收起再展开后图标再也不回来"就分不清是①补扫没走到、②走到但被标记挡住、
     * ③ polish 跑了但判成不该有图标。
     */
    private static void witnessMarkedButEmpty(View row, View cv, View root) {
        if (!isVerify(row.getContext())) {
            return;
        }
        if (root != null && root.findViewWithTag(TAG_LIGHT_BIG) != null) {
            return;          // 标记说的是实话，这一行确实排好了
        }
        String key = Integer.toHexString(System.identityHashCode(cv)) + "|"
                + Integer.toHexString(System.identityHashCode(root));
        synchronized (MARKED_EMPTY_SEEN) {
            if (MARKED_EMPTY_SEEN.size() > 64 || !MARKED_EMPTY_SEEN.add(key)) {
                return;
            }
        }
        Log.i(TAG, "notify_row.heal skip reason=marked-empty cv="
                + Integer.toHexString(System.identityHashCode(cv))
                + " root=" + Integer.toHexString(System.identityHashCode(root))
                + " h=" + row.getHeight() + " vis=" + row.getVisibility()
                + " pkg=" + (sbnOf(row) == null ? "-" : sbnOf(row).getPackageName()));
    }

    /**
     * light polish 的完成标记（补扫用它判断"这行还要不要再打磨"）。
     * 不能复用 RESTYLED / RESTYLED_ROWS：那两个键是 iOS 卡那套布局钩子的开关
     * （calculateVisibleType 强制收起、onMeasure 钳高、setHeights 钳高、rejectExpand 拒绝展开），
     * light 样式写上就会把这些行为一起带进来。
     */
    private static final WeakHashMap<View, Boolean> LIGHT_POLISHED = new WeakHashMap<>();

    private static void applyLightPolish(View contentView, View root, boolean headsup) {
        // 判定"不该有大图标"的早退分支（组收起/摘要/heads-up/样式不合）以前一律硬删，
        // 用户看到的正是"展开时图标闪现、收起时字压上去后再瞬间消失"。
        if (!lightPolish(contentView, root, headsup)) {
            dropLightIcon(root, root instanceof ViewGroup
                    ? ((ViewGroup) root).findViewWithTag(TAG_LIGHT_BIG) : null, "gate");
        }
    }

    /** @return true = 这一轮已经把这行按大图标排好（含复用原有那枚） */
    private static boolean lightPolish(View contentView, View root, boolean headsup) {
        try {
            // 先记下已存在的大图标：polish 必须幂等。销毁再新建会把 translationY 打回 0，
            // 下一帧 post 才对齐 —— 这就是"点展开时所有大图标抖一下"的直接原因。
            final ImageView recycled = root.findViewWithTag(TAG_LIGHT_BIG) instanceof ImageView
                    ? (ImageView) root.findViewWithTag(TAG_LIGHT_BIG) : null;
            Context ctx = root.getContext();
            sVerifyEnabled = isVerify(ctx);
            // OS5 的排版是唯一目标：42dp 大图标进内容列、标题上提、组展开子行同样走大图标。
            // 不按版本关门——A13 的差异（header 槽位窄、主列从 header 下方开始）由
            // alignLightColumn/alignLightIcon 逐项校正；关掉整段等于放弃目标观感。
            if (!(root instanceof FrameLayout) || !(contentView instanceof ViewGroup)) {
                lightSkip("not-frame", null, root.getClass().getSimpleName());
                return false;
            }
            View rowView = rowOf(contentView);
            if (rowView == null || headsup) {
                lightSkip(headsup ? "headsup" : "no-row", rowView, null);
                return false;
            }
            // 分组摘要保持原生：42dp 图标塞进 44dp 的摘要 header 会成"大头"（用户否决）。
            if (bool(rowView, "isSummaryWithChildren")) {
                lightSkip("summary", rowView, null);
                return false;
            }
            boolean groupChild = bool(rowView, "isChildInGroup")
                    || inChildrenContainer(rowView);
            // 子行要不要建大图标，不能用"组是否展开"判：vivo OS3 把组默认摊开显示，
            // 容器仍报 mChildrenExpanded=false / isGroupExpanded=false / isExpanded=false
            // （真机 group-collapsed 见证里逐条实测到）。真正的信号是子行自己有没有按完整高度布局：
            // 收起态紧凑模板 57px(19dp)，展开后 200~300px。
            if (groupChild && !groupExpanded(rowView)
                    && rowView.getHeight() < NotifyRowSpec.dp(ctx, 70)) {
                lightSkip("group-collapsed", rowView, groupDbg(rowView)
                        + " h=" + rowView.getHeight());
                return false;
            }
            Object tag = root.getTag();
            String style = tag == null ? "" : tag.toString();
            if (!"base".equals(style) && !"big".equals(style) && !"bigText".equals(style)
                    && !"inbox".equals(style) && !"bigPicture".equals(style)) {
                lightSkip("style", rowView, "tag=" + style);
                return false;
            }
            Object wrapper = null;
            try {
                wrapper = XposedHelpers.getObjectField(contentView,
                        currentExpanded(contentView) == root
                                ? "mExpandedWrapper" : "mContractedWrapper");
            } catch (Throwable ignored) {
            }
            String wrapperName = wrapper == null ? "" : wrapper.getClass().getName();
            if (wrapperName.contains("CustomViewWrapper") || wrapperName.contains("Media")
                    || wrapperName.contains("ConversationTemplate")
                    || wrapperName.contains("CallTemplate")) {
                lightSkip("wrapper", rowView, "cls=" + wrapperName);
                return false;
            }
            StatusBarNotification sbn = sbnOf(rowView);
            if (sbn == null || sbn.getNotification() == null) {
                lightSkip("sbn", rowView, null);
                return false;
            }
            Notification n = sbn.getNotification();
            if (Notification.CATEGORY_TRANSPORT.equals(n.category)
                    || (n.extras != null
                    && n.extras.getParcelable(Notification.EXTRA_MEDIA_SESSION) != null)
                    || androidView(root, "media_actions") != null) {
                lightSkip("media", rowView, null);
                return false;
            }
            TextView title = text(root, "title");
            TextView body = firstText(root, "text", "big_text", "always");
            CharSequence titleText = title == null ? null : title.getText();
            CharSequence bodyText = body != null && body != title ? body.getText() : null;
            // OS5 的自动归组摘要是系统生成的空正文行，header 仍要套大图标排版，所以不按空文本跳过
            // 展开/收起时让同一枚大图标连续飞过去，而不是两个子视图各画一枚硬切
            ensureFlight(contentView);
            guardLightMorph(contentView);
            int big = 0;
            final View nativeIcon = findClass(root, "CachingIconView");
            // OS5 展开视图可能没有 CachingIconView，大图标照样要建
            int size = NotifyRowSpec.dp(ctx, LIGHT_ICON_DP);
            // 复用上一枚实例：translationY 是视图属性，跨 detach/attach 保留，
            // 所以重绑/重排时不会再出现"先回原位再跳过来"的一帧
            ImageView bigIcon = recycled != null ? recycled : new ImageView(ctx);
            bigIcon.setTag(TAG_LIGHT_BIG);
            bigIcon.setScaleType(ImageView.ScaleType.FIT_CENTER);
            bigIcon.setClipToOutline(true);
            final int corner = NotifyRowSpec.dp(ctx, LIGHT_ICON_CORNER_DP);
            bigIcon.setOutlineProvider(new ViewOutlineProvider() {
                @Override
                public void getOutline(View view, Outline outline) {
                    outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), corner);
                }
            });
            Drawable appDrawable = appIcon(ctx, sbn.getPackageName());
            if (appDrawable == null && nativeIcon instanceof ImageView) {
                appDrawable = ((ImageView) nativeIcon).getDrawable();
            }
            if (appDrawable != null || bigIcon.getDrawable() == null) {
                bigIcon.setImageDrawable(appDrawable);
            }
            bigIcon.setClickable(true);
            bigIcon.setFocusable(false);
            bigIcon.setImportantForAccessibility(
                    View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            final View rowRef = rowView;
            bigIcon.setOnClickListener(v -> rowRef.performClick());
            bigIcon.setOnLongClickListener(v -> longClickRow(rowRef));
            // OS5：42dp 应用图标放左侧 16dp；纵向对齐在布局后按标题→正文文字块居中
            FrameLayout.LayoutParams flp = new FrameLayout.LayoutParams(size, size);
            flp.gravity = Gravity.CENTER_VERTICAL | Gravity.START;
            flp.setMarginStart(NotifyRowSpec.dp(ctx, NotifyRowSpec.GUTTER_DP));
            if (bigIcon.getParent() instanceof ViewGroup) {
                ((ViewGroup) bigIcon.getParent()).removeView(bigIcon);
            }
            ((ViewGroup) root).addView(bigIcon, flp);
            // 组子行是被展开动画"揭示"出来的：新建那枚从 0 淡进，回收来的那枚从当前值淡回 1，
            // 而不是在动画中段硬闪一下（用户报的"闪现"就是这个）。普通行不淡入：
            // 滚动列表里逐行淡反而更花。
            synchronized (LIGHT_ICON_CHILD) {
                LIGHT_ICON_CHILD.put(bigIcon, Boolean.valueOf(groupChild));
            }
            if (groupChild) {
                if (recycled == null) {
                    bigIcon.setAlpha(0f);
                }
                fadeLightIcon(root, bigIcon, 1f, "child-expand");
            }
            if (nativeIcon != null) {
                suppressNativeIcon(nativeIcon, true);
                LIGHT_NATIVE_ICON.put(root, nativeIcon);
                guardLightNativeIcon(root);
            }
            final View rootRef = root;
            final View iconRef = bigIcon;
            iconRef.post(() -> {
                try {
                    alignLightIcon(rootRef, iconRef, size);
                    // 宿主（= 可见卡片）自己重新布局时重算一次：图标是 CENTER_VERTICAL 布局的，
                    // 宿主长高/位移后我们写的偏移量就过期了，而这一轮不写就不会抖。
                    hookAlignOnLayout(iconRef.getParent() instanceof View
                            ? (View) iconRef.getParent() : rootRef, rootRef, iconRef, size);
                    iconRef.bringToFront();
                    // header 文本列：公式给的初值常与真机差 10~30px，再收敛一次
                    // 从整行找（vivo 有的 header 挂在 contentView 之外），行的左缘就是列的基准
                    View hdr = findViewByIdSuffixShown(rowView, "notification_header");
                    if (hdr == null) {
                        hdr = findViewByIdSuffixShown(rootRef, "notification_header");
                    }
                    if (hdr != null) {
                        convergeHeaderColumn(rowView, hdr, 0);
                    }
                } catch (Throwable ignored) {
                }
            });
            big = 1;
            if (title != null) {
                title.setTextSize(TypedValue.COMPLEX_UNIT_SP, NotifyRowSpec.TITLE_TEXT_SP);
                title.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
            }
            alignLightColumn(ctx, root, title != null ? title : body);
            // 图标/标题变化会改行高：必须通知列表重排，否则 StackScrollAlgorithm 仍按旧高度
            // 定位，表现为行重叠或大间隔
            contentView.requestLayout();
            refreshRowLayout(rowView);
            if (sVerifyEnabled) {
                final View probeRoot = root;
                final View probeRow = rowView;
                final String probePkg = sbn.getPackageName();
                rowView.postDelayed(() -> {
                    try {
                        View hdr = findClass(probeRoot, "NotificationHeaderView");
                        View ic = findClass(probeRoot, "CachingIconView");
                        int[] il = new int[2];
                        int[] hl = new int[2];
                        int[] rl = new int[2];
                        if (ic != null) ic.getLocationOnScreen(il);
                        if (hdr != null) hdr.getLocationOnScreen(hl);
                        probeRow.getLocationOnScreen(rl);
                        String pcls = "null";
                        int ph = -1, plpW = -1, plpH = -1, pclip = -1;
                        if (ic != null && ic.getParent() instanceof View) {
                            View pv = (View) ic.getParent();
                            pcls = pv.getClass().getSimpleName();
                            ph = pv.getHeight();
                            if (pv.getLayoutParams() != null) {
                                plpW = pv.getLayoutParams().width;
                                plpH = pv.getLayoutParams().height;
                            }
                            if (pv instanceof ViewGroup) {
                                pclip = ((ViewGroup) pv).getClipChildren() ? 1 : 0;
                            }
                        }
                        int ilpW = -1, ilpH = -1;
                        if (ic != null && ic.getLayoutParams() != null) {
                            ilpW = ic.getLayoutParams().width;
                            ilpH = ic.getLayoutParams().height;
                        }
                        Log.i(TAG, "notify_row.light.icon pkg=" + probePkg
                                + " iconW=" + (ic == null ? -1 : ic.getWidth())
                                + " iconH=" + (ic == null ? -1 : ic.getHeight())
                                + " ilp=" + ilpW + "x" + ilpH
                                + " parent=" + pcls + "/" + ph
                                + " plp=" + plpW + "x" + plpH + " pclip=" + pclip
                                + " iconX=" + (ic == null ? -1 : il[0] - rl[0])
                                + " iconY=" + (ic == null ? -1 : il[1] - rl[1])
                                + " icScale=" + (ic == null ? -1f : ic.getScaleX())
                                + " icTY=" + (ic == null ? -1f : ic.getTranslationY())
                                + " hdrH=" + (hdr == null ? -1 : hdr.getHeight())
                                + " hdrY=" + (hdr == null ? -1 : hl[1] - rl[1])
                                + " rowH=" + probeRow.getHeight()
                                + " clipB=" + XposedHelpers.getIntField(probeRow,
                                        "mClipBottomAmount"));
                    } catch (Throwable ignored) {
                    }
                }, 500);
            }
            LIGHT_POLISHED.put(contentView, Boolean.TRUE);
            // 故意不写 RESTYLED_ROWS：它是 iOS 时代"rejectExpand 拒绝用户展开"的开关，
            // light 样式必须能正常展开（真机否决过一次同类回归）。
            Log.i(TAG, "notify_row: restyled light pkg=" + sbn.getPackageName()
                    + " bigIcon=" + big + " geo=os5-column"
                    // row= 让"同一次交互里同一行被 polish 几次"可数：抖动（用户报的第 1 个症状）
                    // 的指纹就是重复 polish；没有行身份时 3 行同包日志分不清是 3 行还是 1 行×3。
                    + " row=" + Integer.toHexString(System.identityHashCode(contentView))
                    + " titleLen=" + (titleText == null ? 0 : titleText.length())
                    + " bodyLen=" + (bodyText == null ? 0 : bodyText.length()));
            return true;
        } catch (Throwable t) {
            Log.w(TAG, "notify_row: light polish failed", t);
            return false;
        }
    }

    private static void restyle(View contentView, View root) {
        restyle(contentView, root, false);
    }

    private static void restyle(View contentView, View root, boolean headsup) {
        Context ctx = root.getContext();
        if (!isEnabledStatic(ctx)) {
            dropOverlay(contentView);
            dropStackOverlay(contentView, rowOf(contentView));
            RESTYLED.remove(contentView);
            return;
        }
        if (isLightStyle(ctx)) {
            applyLightPolish(contentView, root, headsup);
            return;
        }
        clearLightBigIcon(root, "not-light-style");
        sStackMode = isStackMode(ctx);
        sUngroupEnabled = !sStackMode;
        sVerifyEnabled = isVerify(ctx);
        // 重影守卫要覆盖所有行（含下面被 supported/wrapper 过滤掉的），必须在过滤之前挂；
        // 它自己按 isExpanded + 展开子视图可见来判断，收起态一律还原 alpha=1
        guardExpandedGhost(contentView, rowOf(contentView));
        Object tag = root.getTag();
        String style = tag == null ? "" : tag.toString();
        // 行被回收复用：新绑定不是聊天模板时移除旧 overlay，避免残留 iOS 行叠加显示
        if (!"messaging".equals(style) && !"conversation".equals(style)) {
            dropOverlay(contentView);
        }
        boolean supported = "base".equals(style) || "big".equals(style)
                || "bigText".equals(style) || "inbox".equals(style)
                || "bigPicture".equals(style);
        if (!supported || !(root instanceof FrameLayout)
                || !(contentView instanceof ViewGroup)) {
            if (isDebug(ctx)) {
                Log.i(TAG, "notify_row: skip unsupported style=" + style
                        + " root=" + root.getClass().getSimpleName());
            }
            RESTYLED.remove(contentView);
            return;
        }

        View rowView = rowOf(contentView);
        if (rowView == null) {
            RESTYLED.remove(contentView);
            return;
        }
        if (bool(rowView, "isSummaryWithChildren")) {
            if (sStackMode) {
                applyStackOverlay(contentView, root, rowView);
            } else {
                RESTYLED.remove(contentView);
                root.postDelayed(safe(() -> autoExpandGroup(rowView, 0)), 150);
            }
            return;
        }
        if (sStackMode && STACK_ROWS.containsKey(rowView) && !headsup) {
            // 展开/收起切换的一瞬间 isSummaryWithChildren 会短暂变 false，
            // 此时拆卡会注销守卫，之后没人压住原生内容 -> 组头+子行直接露出来
            scheduleStackRetry(contentView, root);
            return;
        }
        dropStackOverlay(contentView, rowView);
        boolean groupChild = bool(rowView, "isChildInGroup") || inChildrenContainer(rowView);

        Object wrapper = XposedHelpers.getObjectField(contentView, "mContractedWrapper");
        String wrapperName = wrapper == null ? "" : wrapper.getClass().getName();
        if (wrapperName.contains("CustomViewWrapper") || wrapperName.contains("Media")
                || wrapperName.contains("ConversationTemplate") || wrapperName.contains("CallTemplate")) {
            if (isDebug(ctx)) {
                Log.i(TAG, "notify_row: skip wrapper=" + wrapperName
                        + " style=" + style);
            }
            RESTYLED.remove(contentView);
            return;
        }

        StatusBarNotification sbn = sbnOf(rowView);
        if (sbn == null) {
            RESTYLED.remove(contentView);
            return;
        }
        Notification n = sbn.getNotification();
        if (n == null || Notification.CATEGORY_TRANSPORT.equals(n.category)) {
            RESTYLED.remove(contentView);
            return;
        }
        if (n.extras != null && n.extras.getParcelable(Notification.EXTRA_MEDIA_SESSION) != null) {
            RESTYLED.remove(contentView);
            return;
        }
        if (androidView(root, "media_actions") != null) {
            RESTYLED.remove(contentView);
            return;
        }

        TextView title = text(root, "title");
        if (title == null) {
            Log.i(TAG, "notify_row: skip no title style=" + style);
            RESTYLED.remove(contentView);
            return;
        }
        View header = findClass(root, "NotificationHeaderView");
        if (!groupChild && header == null) {
            Log.i(TAG, "notify_row: skip no header style=" + style);
            RESTYLED.remove(contentView);
            return;
        }
        boolean night = NotifyRowSpec.night(ctx);
        int primary = night ? NotifyRowSpec.DARK_PRIMARY : NotifyRowSpec.LIGHT_PRIMARY;
        int secondary = night ? NotifyRowSpec.DARK_SECONDARY : NotifyRowSpec.LIGHT_SECONDARY;

        View topContent = directChildOf(root, title);
        if (topContent == null) {
            RESTYLED.remove(contentView);
            return;
        }
        int start = startOffset(root, title);
        int target = NotifyRowSpec.dp(ctx, NotifyRowSpec.COLUMN_DP);
        int end = endOffset(root, title);
        int endTarget = NotifyRowSpec.dp(ctx, 16);
        if (start < target || end < endTarget) {
            View box = directChildOf(topContent, title);
            if (box == null) {
                box = title;
            }
            box.setPaddingRelative(box.getPaddingStart() + Math.max(0, target - start),
                    box.getPaddingTop(),
                    box.getPaddingEnd() + Math.max(0, endTarget - end),
                    box.getPaddingBottom());
        }

        if (header != null) {
            // header 压成一行；小图标占位保留但彻底隐形（组内子行同样处理）
            View smallIcon = findClass(header, "CachingIconView");
            int small = 0;
            int smallEnd = 0;
            if (smallIcon != null) {
                small = smallIcon.getWidth();
                ViewGroup.LayoutParams slp = smallIcon.getLayoutParams();
                if (small <= 0 && slp != null && slp.width > 0) {
                    small = slp.width;
                }
                if (small <= 0) {
                    small = NotifyRowSpec.dp(ctx, 16);
                }
                if (slp instanceof ViewGroup.MarginLayoutParams) {
                    smallEnd = ((ViewGroup.MarginLayoutParams) slp).getMarginEnd();
                }
            }
            int headerPad = Math.max(0,
                    NotifyRowSpec.dp(ctx, NotifyRowSpec.COLUMN_DP) - small - smallEnd);
            header.setPaddingRelative(headerPad, header.getPaddingTop(),
                    header.getPaddingEnd(), header.getPaddingBottom());
            ViewGroup.LayoutParams hlp = header.getLayoutParams();
            if (hlp != null) {
                hlp.height = NotifyRowSpec.dp(ctx, NotifyRowSpec.HEADER_HEIGHT_DP);
                header.setLayoutParams(hlp);
            }
            TextView appName = text(header, "app_name_text");
            if (appName != null) {
                appName.setTextSize(TypedValue.COMPLEX_UNIT_SP, NotifyRowSpec.APP_TEXT_SP);
                appName.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
                appName.setTextColor(secondary);
                appName.setSingleLine(true);
                appName.setEllipsize(TextUtils.TruncateAt.END);
            }
            ViewGroup.LayoutParams clp = topContent.getLayoutParams();
            if (clp instanceof ViewGroup.MarginLayoutParams) {
                ViewGroup.MarginLayoutParams mlp = (ViewGroup.MarginLayoutParams) clp;
                mlp.topMargin = NotifyRowSpec.dp(ctx, NotifyRowSpec.HEADER_HEIGHT_DP);
                topContent.setLayoutParams(mlp);
            }
        }
        boolean summaryOnly = (n.flags & Notification.FLAG_GROUP_SUMMARY) != 0;
        if (!summaryOnly) {
            // 分组逻辑会管理 divider；独立卡片（含堆叠展开后的子行）隐藏 "/" 分隔
            hide(androidView(root, "time_divider"));
        }

        TextView time = text(root, "time");
        if (time != null) {
            time.setTextSize(TypedValue.COMPLEX_UNIT_SP, NotifyRowSpec.APP_TEXT_SP);
            time.setTextColor(secondary);
            if (!summaryOnly && header != null) {
                // 保留展开箭头的行：时间照旧右对齐，但给箭头让出 30dp（与堆叠卡同一套预留）
                moveTimeRight(header, time, ctx,
                        needsExpandForActions(contentView) ? 46 : 16);
            }
        }

        styleTexts(ctx, root, title, primary, secondary);
        hideSmallIcons(root);
        disableHeaderTouch(root);
        guardSmallIcons(root);

        // 38dp 大图标：垂直居中，左侧 16dp；组内子行同样一个条目一个大图标
        ImageView icon = iconOf(root);
        if (icon == null) {
            icon = new ImageView(ctx);
            icon.setTag(TAG_ICON);
            icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
            icon.setClipToOutline(true);
            GradientDrawable shape = new GradientDrawable();
            shape.setCornerRadius(NotifyRowSpec.dp(ctx, NotifyRowSpec.ICON_CORNER_DP));
            shape.setColor(0);
            icon.setBackground(shape);
            icon.setClickable(true);
            icon.setFocusable(false);
            icon.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            final View row = rowView;
            icon.setOnClickListener(v -> row.performClick());
            icon.setOnLongClickListener(v -> longClickRow(row));
            ((FrameLayout) root).addView(icon);
        }
        centerIcon(ctx, root, icon);
        Drawable appIcon = appIcon(ctx, sbn.getPackageName());
        if (appIcon == null) {
            View original = findClass(root, "CachingIconView");
            if (original instanceof ImageView) {
                appIcon = ((ImageView) original).getDrawable();
            }
        }
        icon.setImageDrawable(appIcon);
        icon.setContentDescription(null);
        // 行复用/旧 morph 可能把这枚图标按成 alpha 0；重新绑定时必须还原
        icon.setAlpha(1f);
        // 飞行图标预建：preDraw 里 addView 会丢一帧，正是"图标闪现"的来源
        ensureFlight(contentView);

        RESTYLED.put(contentView, Boolean.TRUE);
        RESTYLED_ROWS.put(rowView, Boolean.TRUE);
        synchronized (ACTION_ROWS) {
            ACTION_ROWS.remove(rowView);   // 行会被回收复用，动作区判定跟着这次绑定重算
        }
        if (rowView.getParent() instanceof ViewGroup) {
            applyStackMetrics((View) rowView.getParent());
        }
        try {
            if (!bool(rowView, "isHeadsUp") && bool(rowView, "isExpanded")
                    && !needsExpandForActions(contentView)) {
                XposedHelpers.callMethod(rowView, "setUserExpanded", false);
            }
        } catch (Throwable ignored) {
        }
        contentView.requestLayout();
        try {
            XposedHelpers.callMethod(rowView, "updateLimits");
        } catch (Throwable ignored) {
        }
        root.postDelayed(safe(() -> hideSmallIcons(root)), 300);
        if (isDebug(ctx)) {
            dumpOnce(root);
        }
        if (headsup) {
            HEADSUP_ROOTS.put(root, Boolean.TRUE);
        }
        if (sVerifyEnabled) {
            VERIFY_INFO.put(root, Boolean.TRUE);
            scheduleVerify(root, rowView, sbn, style, groupChild, summaryOnly,
                    currentExpanded(contentView) == root, headsup, 0);
        }
        Log.i(TAG, "notify_row: restyled pkg=" + sbn.getPackageName() + " style=" + style
                + (groupChild ? " groupChild" : " single") + (headsup ? " headsup" : "")
                + " bigIcon=" + (icon.getDrawable() != null ? 1 : 0));
    }

    /** light = 原生优化：保留 AOSP/vivo 原卡片与分组，只做 38dp 大图标 + 标题排版 */
    private static boolean isLightStyle(Context ctx) {
        // iOS 卡样式已移除，所有版本统一走原生优化
        return true;
    }

    private static boolean isStackMode(Context ctx) {
        if (!STACK_BUILD_ENABLED) {
            return false;
        }
        try {
            return "ios_stack".equals(Prefs.option(ctx, "notify_row", "group_mode",
                    "ios_stack"));
        } catch (Throwable t) {
            return false;
        }
    }

    /** 组摘要行 -> iOS 折叠堆叠卡：正面=最新一条，顶部露出背后卡片边 + 条数徽标 */
    private static void applyStackOverlay(View contentView, View root, View rowView) {
        try {
            Context ctx = root.getContext();
            if (!(contentView instanceof ViewGroup)) {
                return;
            }
            int childCount = childCountOf(rowView);
            StatusBarNotification front = newestChildSbn(rowView);
            if (childCount <= 0 || front == null || front.getNotification() == null) {
                if (bool(rowView, "isSummaryWithChildren")) {
                    scheduleStackRetry(contentView, root);   // 子项还没挂上，别拆已有卡片
                    return;
                }
                dropStackOverlay(contentView, rowView);
                return;
            }
            if (rowView instanceof ViewGroup) {
                ((ViewGroup) rowView).setClipChildren(false);
                ((ViewGroup) rowView).setClipToPadding(false);
            }
            ((ViewGroup) contentView).setClipChildren(false);
            ((ViewGroup) contentView).setClipToPadding(false);
            Notification n = front.getNotification();
            IosNotificationRow overlay = STACK_OVERLAYS.get(contentView);
            if (overlay == null) {
                overlay = new IosNotificationRow(ctx);
                ((ViewGroup) contentView).addView(overlay,
                        new FrameLayout.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.WRAP_CONTENT));
                STACK_OVERLAYS.put(contentView, overlay);
            }
            int cardColor = sampleCardColor(rowView, ctx);
            final View row = rowView;
            overlay.bind(appIcon(ctx, front.getPackageName()),
                    appLabel(ctx, front.getPackageName()),
                    relativeTime(front.getPostTime()),
                    notificationTitle(n),
                    notificationText(n),
                    NotifyRowSpec.night(ctx),
                    v -> openStackCard(row),
                    v -> longClickRow(row));
            java.util.List<IosNotificationRow.StackChild> kids =
                    new java.util.ArrayList<>();
            for (StatusBarNotification child : childSbns(rowView)) {
                Notification cn = child.getNotification();
                IosNotificationRow.StackChild item = new IosNotificationRow.StackChild();
                item.icon = appIcon(ctx, child.getPackageName());
                item.title = notificationTitle(cn);
                item.body = notificationText(cn);
                item.time = relativeTime(child.getPostTime());
                final StatusBarNotification childRef = child;
                item.click = v -> openStackChild(row, childRef);
                item.longPress = v -> {
                    longClickStackChild(row, childRef);
                    return true;
                };
                item.actions = stackActions(child);
                kids.add(item);
            }
            overlay.setStackChildren(kids);
            overlay.setFrontActions(stackActions(front));
            overlay.setStackExpanded(STACK_USER_EXPANDED.containsKey(rowView));
            int bars = Math.min(Math.max(0, childCount - 1), NotifyRowSpec.STACK_BARS);
            overlay.setStack(bars, childCount, cardColor);
            overlay.setPostTime(front.getPostTime());
            // 卡体点击 = 打开应用；展开/收起只走右上角箭头；长按仍是原生菜单
            overlay.setOnClickListener(v -> openStackCard(row));
            overlay.setOnExpandToggle(v -> toggleStack(row));
            overlay.setOnLongClickListener(v -> longClickRow(row));
            // 用系统原生展开箭头的外观（自绘的细线箭头和系统其它箭头不一致）
            try {
                View arrow = findClass(rowView, "NotificationExpandButton");
                if (arrow == null) {
                    arrow = findClass(rowView, "ExpandButton");
                }
                if (arrow instanceof ImageView) {
                    overlay.useNativeChevron(((ImageView) arrow).getDrawable());
                }
            } catch (Throwable ignored) {
            }
            // 行高要容得下露边带：给原生收起态根视图加等量底部内边距（其内容已被隐藏）
            Integer originalBottom = STACK_ROOT_PAD.get(root);
            if (originalBottom == null) {
                originalBottom = root.getPaddingBottom();
                STACK_ROOT_PAD.put(root, originalBottom);
            }
            int peek = bars * NotifyRowSpec.dp(ctx, NotifyRowSpec.STACK_PEEK_LAYER_DP);
            root.setPadding(root.getPaddingLeft(), root.getPaddingTop(),
                    root.getPaddingRight(), originalBottom + peek);
            root.setAlpha(0f);
            View bg = findClass(rowView, "NotificationBackgroundView");
            if (bg != null) {
                bg.setAlpha(0f);
            }
            // OS5 折叠组显示的是子容器里的“组头”，堆叠卡必须让位给 NotificationContentView
            collapseGroup(rowView);
            contentView.setVisibility(View.VISIBLE);
            guardStack(contentView, rowView, root, overlay, bg);
            STACK_ROWS.put(rowView, Boolean.TRUE);
            STACK_CARDS.put(rowView, overlay);
            Object entry = safeGetEntry(rowView);
            if (entry != null) {
                STACK_ENTRIES.put(entry, rowView);
            }
            RESTYLED.put(contentView, Boolean.TRUE);
            contentView.requestLayout();
            try {
                XposedHelpers.callMethod(rowView, "updateLimits");
            } catch (Throwable ignored) {
            }
            if (sVerifyEnabled) {
                STACK_VERIFY_INFO.put(contentView, new View[]{root, rowView});
                scheduleStackVerify(contentView, overlay, root, rowView, front,
                        childCount, 0);
            }
            Log.i(TAG, "notify_row: stack pkg=" + front.getPackageName()
                    + " count=" + childCount + " bars=" + bars
                    + " peekPx=" + peek
                    + " cardColor=0x" + Integer.toHexString(cardColor));
        } catch (Throwable t) {
            Log.w(TAG, "notify_row: stack overlay failed", t);
        }
    }

    private static void dropStackOverlay(View contentView, View rowView) {
        IosNotificationRow overlay = STACK_OVERLAYS.remove(contentView);
        STACK_VERIFY_INFO.remove(contentView);
        STACK_NIGHT.remove(contentView);
        ViewGroup children = STACK_CONTAINERS.remove(contentView);
        if (children != null) {
            children.setAlpha(1f);
        }
        if (rowView != null) {
            // 行回收给普通通知时必须摘掉堆叠标记，否则 setUserExpanded 会永久拒绝它的展开
            STACK_ROWS.remove(rowView);
            STACK_CARDS.remove(rowView);
            STACK_USER_EXPANDED.remove(rowView);
            restoreStackedViews(rowView);
        }
        ViewTreeObserver.OnPreDrawListener guard = STACK_GUARDS.remove(contentView);
        ViewTreeObserver guardVto = STACK_GUARD_VTO.remove(contentView);
        if (guard != null && guardVto != null && guardVto.isAlive()) {
            try {
                guardVto.removeOnPreDrawListener(guard);
            } catch (Throwable ignored) {
            }
        }
        View root = currentContracted(contentView);
        if (root != null) {
            Integer originalBottom = STACK_ROOT_PAD.remove(root);
            if (originalBottom != null) {
                root.setPadding(root.getPaddingLeft(), root.getPaddingTop(),
                        root.getPaddingRight(), originalBottom);
            }
            root.setAlpha(1f);
            root.setVisibility(View.VISIBLE);
        }
        if (overlay == null) {
            return;
        }
        ViewParent parent = overlay.getParent();
        if (parent instanceof ViewGroup) {
            ((ViewGroup) parent).removeView(overlay);
        }
        Log.i(TAG, "notify_row: stack overlay dropped");
    }

    /** alpha 不是 rebind 会重置的属性：堆叠卡撤掉后要自己把被压透明的原生件恢复 */
    private static void restoreStackedViews(View rowView) {
        View bg = findClass(rowView, "NotificationBackgroundView");
        if (bg != null) {
            bg.setAlpha(1f);
        }
        if (rowView instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) rowView;
            for (int i = 0; i < g.getChildCount(); i++) {
                View child = g.getChildAt(i);
                String cn = child.getClass().getSimpleName();
                Integer extra = STACK_EXTRA_VIS.remove(child);
                if (extra != null && child.getVisibility() != extra) {
                    child.setVisibility(extra);
                }
                if (cn.contains("ExpandButton") || "Button".equals(cn)) {
                    child.setAlpha(1f);
                }
            }
        }
    }

    /** OS5 上没有 GroupEntry.getRawChildren 的入口，改从 UI 侧子容器取（同一数据源） */
    private static ViewGroup childrenContainerOf(View rowView) {
        try {
            Object container = XposedHelpers.callMethod(rowView, "getChildrenContainer");
            return container instanceof ViewGroup ? (ViewGroup) container : null;
        } catch (Throwable t) {
            return null;
        }
    }

    private static int childCountOf(View rowView) {
        ViewGroup container = childrenContainerOf(rowView);
        if (container == null) {
            return 0;
        }
        try {
            Object count = XposedHelpers.callMethod(container, "getNotificationChildCount");
            if (count instanceof Integer) {
                return (Integer) count;
            }
        } catch (Throwable ignored) {
        }
        return container.getChildCount();
    }

    /** 组内子通知（新->旧）：走容器的 mAttachedChildren，折叠/子容器 GONE 时依然有效 */
    private static java.util.List<StatusBarNotification> childSbns(View rowView) {
        java.util.List<StatusBarNotification> out = new java.util.ArrayList<>();
        ViewGroup container = childrenContainerOf(rowView);
        if (container == null) {
            return out;
        }
        try {
            Object attached = XposedHelpers.callMethod(container, "getAttachedChildren");
            if (attached instanceof java.util.List) {
                for (Object o : (java.util.List<?>) attached) {
                    if (!(o instanceof View)) {
                        continue;
                    }
                    StatusBarNotification s2 = sbnOf((View) o);
                    if (s2 != null && s2.getNotification() != null) {
                        out.add(s2);
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        if (out.isEmpty()) {
            for (int i = 0; i < container.getChildCount(); i++) {
                StatusBarNotification s2 = sbnOf(container.getChildAt(i));
                if (s2 != null && s2.getNotification() != null) {
                    out.add(s2);
                }
            }
        }
        java.util.Collections.sort(out, (x, y) -> Long.compare(y.getPostTime(), x.getPostTime()));
        return out;
    }

    private static StatusBarNotification newestChildSbn(View rowView) {
        ViewGroup container = childrenContainerOf(rowView);
        if (container == null) {
            return null;
        }
        StatusBarNotification front = null;
        for (int i = 0; i < container.getChildCount(); i++) {
            View child = container.getChildAt(i);
            StatusBarNotification sbn = child == null ? null : sbnOf(child);
            if (sbn != null && (front == null || sbn.getPostTime() > front.getPostTime())) {
                front = sbn;
            }
        }
        return front;
    }

    /**
     * Xposed 只兜 before/afterHookedMethod 里的异常；pre-draw 监听与 post 出去的延后任务
     * 直接跑在主线程上，一次异常就是 SystemUI 重启。批量移除通知（一键清除）时视图树正在
     * 拆/换实例，是最容易撞上的时刻，所以统一包一层。
     */
    private static Runnable safe(Runnable body) {
        return () -> {
            try {
                body.run();
            } catch (Throwable t) {
                Log.w(TAG, "notify_row: deferred task failed", t);
            }
        };
    }

    /**
     * 原生 NotificationChildrenContainer 不一定挂在行的直接子级：它可能被包在别的容器里，
     * 却是按 90px 紧凑子行自绘的。堆叠展开态必须整棵 GONE，否则它会在胶囊上方重画一遍子通知
     * （实测 3~4 条文字和图标叠在第二枚胶囊里）。
     */
    private static void hideNativeChildContainers(View v, int depth) {
        if (depth > 6 || !(v instanceof ViewGroup)) {
            return;
        }
        ViewGroup g = (ViewGroup) v;
        for (int i = 0; i < g.getChildCount(); i++) {
            View child = g.getChildAt(i);
            String cn = child.getClass().getSimpleName();
            if (cn.contains("ChildrenContainer") || cn.contains("NotificationChildContainer")) {
                if (child.getVisibility() != View.GONE) {
                    child.setVisibility(View.GONE);
                    if (sVerifyEnabled && sGroupHidden++ < 4) {
                        Log.i(TAG, "notify_row: native child container GONE n="
                                + sGroupHidden + " cls=" + cn + " depth=" + depth);
                    }
                }
                if (child.getAlpha() != 0f) {
                    child.setAlpha(0f);
                }
                continue;
            }
            hideNativeChildContainers(child, depth + 1);
        }
    }

    private static ViewTreeObserver.OnPreDrawListener preDrawGuard(Runnable body) {
        return () -> {
            try {
                body.run();
            } catch (Throwable t) {
                Log.w(TAG, "notify_row: pre-draw guard failed", t);
            }
            return true;
        };
    }

    private static void guardStack(final View contentView, final View rowView,
                                   final View root, final IosNotificationRow overlay,
                                   final View bg) {
        final ViewTreeObserver vto = contentView.getViewTreeObserver();
        synchronized (STACK_GUARDS) {
            ViewTreeObserver.OnPreDrawListener bound = STACK_GUARDS.get(contentView);
            ViewTreeObserver boundVto = STACK_GUARD_VTO.get(contentView);
            if (bound != null && boundVto == vto && vto.isAlive()) {
                return;
            }
            // VTO 会随 attach 合并/换实例：按旧实例摘监听会失败，留下"孤儿守卫"继续把
            // 复用行压成 alpha 0（空白卡）。先按记录的真实 VTO 摘干净再重挂。
            if (bound != null && boundVto != null && boundVto.isAlive()) {
                boundVto.removeOnPreDrawListener(bound);
            }
            STACK_GUARDS.remove(contentView);
            STACK_GUARD_VTO.remove(contentView);
        }
        ViewTreeObserver.OnPreDrawListener guard = new ViewTreeObserver.OnPreDrawListener() {
                    @Override
                    public boolean onPreDraw() {
                      try {
                        if (!STACK_OVERLAYS.containsKey(contentView)
                                || overlay.getParent() == null) {
                            // 堆叠卡已被摘掉：不能继续压透明/隐藏子容器，否则复用行会空白
                            return true;
                        }
                        // 每帧都从活视图树解析：行会被重新绑定甚至换实例，捕获引用会指向废弃实例，
                        // 之前"以为收起/以为展开"的假象都来自这里
                        View rowView = rowOf(contentView);
                        View root = currentContracted(contentView);
                        if (rowView == null || root == null) {
                            return true;
                        }
                        // 覆盖层自绘了正面卡 + 各条胶囊，原生收起内容必须整棵隐形：
                        // 只藏背景的话，胶囊间隙会露出原生文字，和胶囊内容叠在一起
                        if (root.getAlpha() != 0f) {
                            root.setAlpha(0f);
                        }
                        boolean expanded = STACK_USER_EXPANDED.containsKey(rowView);
                        if (bool(rowView, "isGroupExpanded")) {
                            syncGroupExpansion(rowView, false);   // AOSP 侧只允许折叠
                        }
                        if (overlay.isStackExpanded() != expanded) {
                            overlay.setStackExpanded(expanded);
                            refreshRowLayout(rowView);
                        }
                        ViewGroup group = STACK_CONTAINERS.get(contentView);
                        if (group == null || group.getParent() != rowView) {
                            group = childrenContainerOf(rowView);
                            if (group != null) {
                                STACK_CONTAINERS.put(contentView, group);
                            }
                        }
                        if (group != null) {
                            // 折叠：子容器退出测量，否则行高仍被子行撑开（卡下方一大片空白）
                            if (group.getVisibility() != View.GONE) {
                                group.setVisibility(View.GONE);
                                group.requestLayout();
                                refreshRowLayout(rowView);
                            }
                            if (group.getAlpha() != 1f) {
                                group.setAlpha(1f);
                            }
                        }
                        refreshStackIfGroupChanged(contentView, root, rowView, group, overlay);
                        if (bg != null && bg.getAlpha() != 0f && bg.isAttachedToWindow()) {
                            bg.setAlpha(0f);
                        }
                        // 背景与 vivo 长按菜单容器仍按展开态高度参与测量（INVISIBLE 也占位，
                        // 只有 GONE 才退出测量），行因此停在展开高度 -> 卡下面一大片空白
                        hideRowExtras(rowView, false);
                        hideNativeChildContainers(rowView, 0);
                        if (rowView instanceof ViewGroup) {
                            ViewGroup rowGroup = (ViewGroup) rowView;
                            for (int i = 0; i < rowGroup.getChildCount(); i++) {
                                View child = rowGroup.getChildAt(i);
                                String cn = child.getClass().getSimpleName();
                                if (cn.contains("ExpandButton") || "Button".equals(cn)) {
                                    // 收起入口是"再点一次卡片"，原生箭头让位避免两套状态打架
                                    if (child.getAlpha() != 0f) {
                                        child.setAlpha(0f);
                                    }
                                    if (child.isClickable()) {
                                        child.setClickable(false);
                                    }
                                }
                                // 原生组子容器会画在覆盖层之上（实测展开态 3 条子行叠在胶囊上），
                                // 每帧强制退出：只认 GONE，alpha 0 仍参与绘制与测量
                                if (cn.contains("ChildrenContainer")
                                        || cn.contains("NotificationChildContainer")) {
                                    if (child.getVisibility() != View.GONE) {
                                        child.setVisibility(View.GONE);
                                        if (sVerifyEnabled && sGroupHidden++ < 4) {
                                            Log.i(TAG, "notify_row: child container hidden n="
                                                    + sGroupHidden + " cls=" + cn);
                                        }
                                    }
                                    if (child.getAlpha() != 0f) {
                                        child.setAlpha(0f);
                                    }
                                }
                            }
                        }
                        root.setAlpha(0f);
                        Integer originalBottom = STACK_ROOT_PAD.get(root);
                        if (originalBottom != null) {
                            int wantPad = originalBottom
                                    + (overlay.isStackExpanded() ? 0 : overlay.stackPeekPx());
                            if (root.getPaddingBottom() != wantPad) {
                                root.setPadding(root.getPaddingLeft(), root.getPaddingTop(),
                                        root.getPaddingRight(), wantPad);
                            }
                        }
                        if (overlay.getVisibility() != View.VISIBLE) {
                            overlay.setVisibility(View.VISIBLE);
                        }
                        if (contentView.getVisibility() != View.VISIBLE) {
                            contentView.setVisibility(View.VISIBLE);
                        }
                        boolean night = NotifyRowSpec.night(contentView.getContext());
                        Boolean lastNight = STACK_NIGHT.get(contentView);
                        if (lastNight == null || lastNight != night) {
                            STACK_NIGHT.put(contentView, night);
                            if (overlay.isStack()) {
                                overlay.applyTheme(night);
                                overlay.applyTextScale();
                                // 卡面是采样色，日夜切换必须重取，否则夜间仍是日间灰
                                overlay.setStack(overlay.stackBars(), overlay.stackTotal(),
                                        sampleCardColor(rowView, contentView.getContext()));
                            }
                        }
                        return true;
                      } catch (Throwable t) {
                        Log.w(TAG, "notify_row: stack guard failed", t);
                        return true;
                      }
                    }
                };
        STACK_GUARDS.put(contentView, guard);
        STACK_GUARD_VTO.put(contentView, vto);
        vto.addOnPreDrawListener(guard);
    }

    /**
     * 我们绕过原生动画改了子容器可见性/行内边距，StackScrollAlgorithm 的每行高度缓存会留在旧值
     * （表现为折叠卡下面一大块空洞，甚至相邻行重叠），必须显式让行与列表重新测量排版。
     */
    private static void refreshRowLayout(View rowView) {
        try {
            XposedHelpers.callMethod(rowView, "adjustHeightForChildren");
        } catch (Throwable ignored) {
        }
        // 行高被 AOSP 缓存（mHeight/HeightCacheUpdater），只 requestLayout 不会回落，
        // 收起后行会一直占着展开时的高度 —— 必须显式通知高度变化
        try {
            XposedHelpers.callMethod(rowView, "notifyHeightChanged", false);
        } catch (Throwable ignored) {
        }
        rowView.requestLayout();
        if (rowView.getParent() instanceof View) {
            ((View) rowView.getParent()).requestLayout();
        }
    }

    /**
     * 组里新增/撤销通知时，摘要行往往不会重新绑定（只有子行变化），卡片就会停在旧条数、
     * 旧正面通知上。子容器挂了多少 View 是免费信号，但它比通知数慢一拍，所以再用
     * 500ms 节流比对真实条数，不一致才重算。
     */
    private static void refreshStackIfGroupChanged(final View contentView, final View root,
                                                   View rowView, ViewGroup container,
                                                   IosNotificationRow overlay) {
        long now = android.os.SystemClock.uptimeMillis();
        boolean kidsChanged = false;
        if (container != null) {
            Integer seen = STACK_SEEN_KIDS.put(contentView, container.getChildCount());
            kidsChanged = seen != null && seen != container.getChildCount();
        }
        Long last = STACK_DIRTY_AT.get(contentView);
        if (!kidsChanged && last != null && now - last < 500) {
            return;
        }
        STACK_DIRTY_AT.put(contentView, now);
        int total = childCountOf(rowView);
        if (total == overlay.stackTotal()) {
            return;
        }
        if (total <= 0) {
            // 组已经被清空/摘掉：卡片会在下次绑定时回收，别再空转重排
            STACK_SEEN_KIDS.remove(contentView);
            return;
        }
        Log.i(TAG, "notify_row: stack total " + overlay.stackTotal() + "->" + total + ", restyling");
        contentView.post(safe(() -> restyle(contentView, root)));
    }

    /** 延后重算堆叠卡（去抖 300ms），用于子项/摘要状态短暂不一致的时刻 */
    private static void scheduleStackRetry(final View contentView, final View root) {
        long now = android.os.SystemClock.uptimeMillis();
        Long last = STACK_DIRTY_AT.get(contentView);
        if (last != null && now - last < 300) {
            return;
        }
        STACK_DIRTY_AT.put(contentView, now);
        contentView.post(safe(() -> restyle(contentView, root)));
    }

    private static Object safeGetEntry(View rowView) {
        try {
            return XposedHelpers.callMethod(rowView, "getEntry");
        } catch (Throwable ignored) {
            return null;
        }
    }

    /** 从任意子 View 往上找到当前真正挂在列表里的行实例 */
    private static View rowAncestor(View v) {
        ViewParent p = v == null ? null : v.getParent();
        while (p != null) {
            if (p.getClass().getSimpleName().contains("ExpandableNotificationRow")) {
                return (View) p;
            }
            p = p.getParent();
        }
        return null;
    }

    /** 系统在我们之外展开/折叠了组：按我们的目标同步回去，250ms 节流避免来回抖 */
    private static void syncGroupExpansion(View rowView, boolean want) {
        long now = android.os.SystemClock.uptimeMillis();
        Long last = STACK_SYNC_AT.get(rowView);
        if (last != null && now - last < 250) {
            return;
        }
        STACK_SYNC_AT.put(rowView, now);
        Log.i(TAG, "notify_row: sync group expansion ->" + (want ? "expanded" : "collapsed"));
        applyGroupExpansion(rowView, want);
    }

    /**
     * 堆叠行折叠期间把"只为展开态存在"的行内大件退出测量：
     * NotificationBackgroundView / VivoMenuContainerView 会按展开高度参与 FrameLayout 测量，
     * 压 alpha 或置 INVISIBLE 都没用（INVISIBLE 仍占位），必须 GONE。
     */
    private static void hideRowExtras(View rowView, boolean expanded) {
        if (!(rowView instanceof ViewGroup)) {
            return;
        }
        ViewGroup g = (ViewGroup) rowView;
        for (int i = 0; i < g.getChildCount(); i++) {
            View child = g.getChildAt(i);
            String cn = child.getClass().getSimpleName();
            if (!cn.contains("NotificationBackgroundView") && !cn.contains("MenuContainer")) {
                continue;
            }
            Integer original = STACK_EXTRA_VIS.get(child);
            if (original == null) {
                original = child.getVisibility();
                STACK_EXTRA_VIS.put(child, original);
            }
            int want = expanded ? original : View.GONE;
            if (child.getVisibility() != want) {
                child.setVisibility(want);
                child.requestLayout();
            }
        }
    }

    private static void collapseGroup(View rowView) {
        STACK_USER_EXPANDED.remove(rowView);
        applyGroupExpansion(rowView, false);
    }

    /**
     * OS5 里组的展开状态记在 GroupExpansionManager(entry) 上：单参数 setUserExpanded(z) 只会写
     * mUserExpanded，**不会折叠子行**（源码 setUserExpanded(z, z2) 只有 z2=true 才写 manager）。
     * 之前所有"压折叠"都打在无效状态上，收起后子容器照旧可见 —— 这就是退回原生的真因。
     */
    private static void applyGroupExpansion(View rowView, boolean expanded) {
        if (expanded) {
            STACK_USER_EXPANDED.put(rowView, Boolean.TRUE);
        } else {
            STACK_USER_EXPANDED.remove(rowView);
        }
        try {
            Object mgr = XposedHelpers.getObjectField(rowView, "mGroupExpansionManager");
            Object entry = XposedHelpers.callMethod(rowView, "getEntry");
            if (mgr != null && entry != null) {
                XposedHelpers.callMethod(mgr, "setGroupExpanded", entry, expanded);
            }
        } catch (Throwable t) {
            Log.w(TAG, "notify_row: group expansion failed", t);
        }
        try {
            XposedHelpers.callMethod(rowView, "setUserExpanded", expanded, true);
        } catch (Throwable ignored) {
        }
        try {
            XposedHelpers.callMethod(rowView, "setChildrenExpanded", expanded, false);
        } catch (Throwable ignored) {
        }
    }


    /**
     * 堆叠卡卡体点击 = 打开应用。不能走 row.performClick()：组摘要行那个点击的语义是"展开组"，
     * 而堆叠行我们一律拒绝 AOSP 展开，结果就是点了没反应。这里直接发最新一条的 contentIntent。
     */
    private static void openStackCard(View rowView) {
        StatusBarNotification front = newestChildSbn(rowView);
        if (front != null) {
            openStackChild(rowView, front);
            return;
        }
        Log.i(TAG, "notify_row: stack card has no child, use row click");
        try {
            rowView.performClick();
        } catch (Throwable ignored) {
        }
    }

    /**
     * 打开堆叠组里的某一条子通知。优先走该子通知原生行的点击回调：
     * contentIntent / autoCancel / 收起通知中心都是系统原有语义；找不到原生行时退回
     * send PendingIntent + 按 FLAG_AUTO_CANCEL 移除，避免"跳转了但通知还挂着"。
     */
    private static void openStackChild(View rowView, StatusBarNotification sbn) {
        if (sbn == null) {
            return;
        }
        View child = findChildRow(rowView, sbn);
        if (child != null) {
            try {
                if (child.performClick()) {
                    Log.i(TAG, "notify_row: stack child click via=row key=" + sbn.getKey());
                    return;
                }
            } catch (Throwable t) {
                Log.w(TAG, "notify_row: stack child row click failed", t);
            }
        }
        PendingIntent pi = sbn.getNotification() == null
                ? null : sbn.getNotification().contentIntent;
        if (pi == null) {
            Log.i(TAG, "notify_row: stack child no contentIntent key=" + sbn.getKey());
            return;
        }
        try {
            pi.send();
            dismissIfAutoCancel(sbn);
            closeShade(rowView.getContext());
            Log.i(TAG, "notify_row: stack child click via=intent key=" + sbn.getKey());
        } catch (Throwable t) {
            Log.w(TAG, "notify_row: stack child intent failed", t);
        }
    }

    private static void longClickStackChild(View rowView, StatusBarNotification sbn) {
        View child = findChildRow(rowView, sbn);
        longClickRow(child != null ? child : rowView);
        Log.i(TAG, "notify_row: stack child long-press key=" + sbn.getKey()
                + " via=" + (child != null ? "row" : "summary"));
    }

    /** 组子容器里的原生行（容器 GONE 时 getAttachedChildren 依然有效） */
    private static View findChildRow(View rowView, StatusBarNotification sbn) {
        ViewGroup container = childrenContainerOf(rowView);
        if (container == null || sbn == null) {
            return null;
        }
        try {
            Object attached = XposedHelpers.callMethod(container, "getAttachedChildren");
            if (attached instanceof java.util.List) {
                for (Object o : (java.util.List<?>) attached) {
                    if (!(o instanceof View)) {
                        continue;
                    }
                    StatusBarNotification s2 = sbnOf((View) o);
                    if (s2 != null && sbn.getKey().equals(s2.getKey())) {
                        return (View) o;
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        for (int i = 0; i < container.getChildCount(); i++) {
            View v = container.getChildAt(i);
            StatusBarNotification s2 = sbnOf(v);
            if (s2 != null && sbn.getKey().equals(s2.getKey())) {
                return v;
            }
        }
        return null;
    }

    /** 系统层移除该条通知（只在 FLAG_AUTO_CANCEL 时）；原生行点击路径走不到时的兜底 */
    private static void dismissIfAutoCancel(StatusBarNotification sbn) {
        Notification n = sbn.getNotification();
        if (n == null || (n.flags & Notification.FLAG_AUTO_CANCEL) == 0) {
            return;
        }
        try {
            Object svc = XposedHelpers.callStaticMethod(
                    android.app.NotificationManager.class, "getService");
            if (svc == null) {
                return;
            }
            try {
                XposedHelpers.callMethod(svc, "cancelNotificationWithTag",
                        sbn.getPackageName(), sbn.getTag(), sbn.getId(), sbn.getUserId());
            } catch (Throwable t) {
                XposedHelpers.callMethod(svc, "cancelNotificationWithTag",
                        sbn.getPackageName(), sbn.getTag(), sbn.getId(), sbn.getUserId(), 0);
            }
            Log.i(TAG, "notify_row: stack child auto-cancel key=" + sbn.getKey());
        } catch (Throwable t) {
            Log.w(TAG, "notify_row: stack child dismiss failed", t);
        }
    }

    /** 子通知的动作按钮（展开态显示在胶囊左下角）：只认有 title + actionIntent 的 */
    private static java.util.List<IosNotificationRow.Action> stackActions(
            StatusBarNotification sbn) {
        java.util.List<IosNotificationRow.Action> out =
                new java.util.ArrayList<>();
        Notification n = sbn == null ? null : sbn.getNotification();
        Notification.Action[] acts = n == null ? null : n.actions;
        if (acts == null) {
            return out;
        }
        for (Notification.Action a : acts) {
            if (a == null || a.actionIntent == null || TextUtils.isEmpty(a.title)) {
                continue;
            }
            final PendingIntent pi = a.actionIntent;
            final String title = a.title.toString();
            IosNotificationRow.Action item =
                    new IosNotificationRow.Action();
            item.title = a.title;
            item.run = () -> {
                try {
                    pi.send();
                    Log.i(TAG, "notify_row: stack action sent title=" + title);
                } catch (Throwable t) {
                    Log.w(TAG, "notify_row: stack action failed title=" + title, t);
                }
            };
            out.add(item);
        }
        return out;
    }

    private static void closeShade(Context ctx) {
        try {
            ctx.sendBroadcast(new Intent("android.intent.action.CLOSE_SYSTEM_DIALOGS"));
        } catch (Throwable ignored) {
        }
    }

    /** 点击堆叠卡右上角箭头：折叠 <-> 展开。展开只改卡片自己的呈现，不动 AOSP 的组状态 */
    private static void toggleStack(View rowView) {
        IosNotificationRow card = STACK_CARDS.get(rowView);
        if (card == null) {
            return;
        }
        boolean expand = !card.isStackExpanded();
        if (expand) {
            STACK_USER_EXPANDED.put(rowView, Boolean.TRUE);
        } else {
            STACK_USER_EXPANDED.remove(rowView);
        }
        card.animateStackExpanded(expand);
        refreshRowLayout(rowView);
        // 动画结束后必须再刷新一次行高缓存：只在开始时通知的话，NSS 的高度缓存/可滚动范围
        // 会停在展开前的值——底部内容露不出来，上滑还会被当成"关闭面板"（实测复现）。
        rowView.postDelayed(safe(() -> refreshRowLayout(rowView)),
                NotifyRowSpec.STACK_ANIM_MS + 120);
        Log.i(TAG, "notify_row: stack " + (expand ? "expanded" : "collapsed")
                + " count=" + childCountOf(rowView)
                + " cardH=" + card.getHeight());
    }

    /** 采样 OS5 卡片材质色作为堆叠卡面/露边颜色，取不到时用兜底色 */
    private static int sampleCardColor(View rowView, Context ctx) {
        try {
            View bg = findClass(rowView, "NotificationBackgroundView");
            if (bg != null) {
                Drawable d = bg.getBackground();
                if (d instanceof GradientDrawable) {
                    android.content.res.ColorStateList cs = ((GradientDrawable) d).getColor();
                    if (cs != null) {
                        return cs.getDefaultColor();
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        return NotifyRowSpec.night(ctx)
                ? NotifyRowSpec.DARK_CARD_FALLBACK : NotifyRowSpec.LIGHT_CARD_FALLBACK;
    }

    private static CharSequence notificationTitle(Notification n) {
        if (n.extras == null) {
            return null;
        }
        CharSequence title = n.extras.getCharSequence(Notification.EXTRA_TITLE);
        return TextUtils.isEmpty(title)
                ? n.extras.getCharSequence(Notification.EXTRA_TEXT) : title;
    }

    private static CharSequence notificationText(Notification n) {
        if (n.extras == null) {
            return null;
        }
        CharSequence text = n.extras.getCharSequence(Notification.EXTRA_TEXT);
        if (TextUtils.isEmpty(text)) {
            text = n.extras.getCharSequence(Notification.EXTRA_BIG_TEXT);
        }
        return text;
    }

    /** 行复用为非聊天通知/功能关闭时，移除曾经叠加的 iOS 聊天行 */
    private static void dropOverlay(View contentView) {
        IosNotificationRow overlay = OVERLAYS.remove(contentView);
        // overlay 活着时把原生根压成了 alpha 0；摘卡必须还原，否则行复用后只剩白底（空白卡）
        View overlayRoot = currentContracted(contentView);
        if (overlayRoot == null) {
            for (View r : new java.util.ArrayList<>(OVERLAY_ROOTS.keySet())) {
                if (r != null && contentViewOf(r) == contentView) {
                    overlayRoot = r;
                    break;
                }
            }
        }
        if (overlayRoot != null && OVERLAY_ROOTS.remove(overlayRoot) != null) {
            overlayRoot.setAlpha(1f);
            overlayRoot.setVisibility(View.VISIBLE);
            overlayRoot.setImportantForAccessibility(
                    View.IMPORTANT_FOR_ACCESSIBILITY_AUTO);
        }
        OVERLAY_VERIFY_PENDING.remove(contentView);
        synchronized (OVERLAY_FADE_TO) {
            OVERLAY_FADE_TO.remove(contentView);
            HANDOFF_LOGGED.remove(contentView);
        }
        ViewTreeObserver.OnPreDrawListener guard;
        ViewTreeObserver bound;
        synchronized (OVERLAY_GUARDS) {
            guard = OVERLAY_GUARDS.remove(contentView);
            bound = OVERLAY_GUARD_VTO.remove(contentView);
        }
        GUARDED.remove(contentView);
        if (guard != null && bound != null && bound.isAlive()) {
            bound.removeOnPreDrawListener(guard);
        }
        if (overlay == null) {
            return;
        }
        ViewParent parent = overlay.getParent();
        if (parent instanceof ViewGroup) {
            ((ViewGroup) parent).removeView(overlay);
        }
        Log.i(TAG, "notify_row: overlay dropped (row rebound)");
    }

    /** messaging/conversation：保留原模板绑定，盖一层 iOS 行 */
    private static void applyMessagingOverlay(View contentView, View root, View rowView) {
        try {
            Context ctx = root.getContext();
            if (!isEnabledStatic(ctx)) {
                return;
            }
            if (isLightStyle(ctx)) {
                // 原生优化不盖自绘消息卡
                dropOverlay(contentView);
                return;
            }
            StatusBarNotification sbn = sbnOf(rowView);
            if (sbn == null || sbn.getNotification() == null
                    || !(contentView instanceof ViewGroup)) {
                dropOverlay(contentView);
                return;
            }
            Notification n = sbn.getNotification();
            // 堆叠模式必须保留分组（组摘要就是堆叠卡的挂载点），只有分离模式才拆组
            if (!sStackMode) {
                sUngroupEnabled = true;
            }
            IosNotificationRow overlay = OVERLAYS.get(contentView);
            if (overlay == null) {
                overlay = new IosNotificationRow(ctx);
                ((ViewGroup) contentView).addView(overlay,
                        new FrameLayout.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.WRAP_CONTENT));
                OVERLAYS.put(contentView, overlay);
                // 飞行图标后加会被卡片盖住：卡片建好后把它顶到最上层
                ImageView fly = ensureFlight(contentView);
                if (fly != null) {
                    fly.bringToFront();
                }
            }
            final View row = rowView;
            CharSequence convTitle = conversationTitle(n);
            CharSequence convMessage = latestMessage(n);
            if (TextUtils.isEmpty(convTitle) && TextUtils.isEmpty(convMessage)) {
                // 没有会话标题/正文：不接管，保留原生渲染，避免自绘空卡
                dropOverlay(contentView);
                return;
            }
            overlay.bind(appIcon(ctx, sbn.getPackageName()),
                    appLabel(ctx, sbn.getPackageName()),
                    relativeTime(sbn.getPostTime()),
                    convTitle,
                    convMessage,
                    NotifyRowSpec.night(ctx),
                    v -> row.performClick(),
                    v -> longClickRow(row));
            // 整行可点：与原生行一致，点文本区也打开应用；长按转发到系统原生菜单
            overlay.setOnClickListener(v -> row.performClick());
            overlay.setOnLongClickListener(v -> longClickRow(row));
            overlay.setPostTime(sbn.getPostTime());
            root.setAlpha(0f);
            root.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            OVERLAY_ROOTS.put(root, Boolean.TRUE);
            RESTYLED.put(contentView, Boolean.TRUE);
            RESTYLED_ROWS.put(rowView, Boolean.TRUE);
            guardOverlay(contentView, root, overlay);
            contentView.requestLayout();
            try {
                XposedHelpers.callMethod(rowView, "updateLimits");
            } catch (Throwable ignored) {
            }
            if (sVerifyEnabled) {
                scheduleOverlayVerify(contentView, overlay, root, rowView, sbn, 0);
            }
            String wrapperName = "";
            try {
                Object wrapper = XposedHelpers.getObjectField(contentView,
                        "mContractedWrapper");
                if (wrapper != null) {
                    wrapperName = wrapper.getClass().getSimpleName();
                }
            } catch (Throwable ignored) {
            }
            Log.i(TAG, "notify_row: overlay pkg=" + sbn.getPackageName()
                    + " style=messaging wrapper=" + wrapperName);
        } catch (Throwable t) {
            Log.w(TAG, "notify_row: messaging overlay failed", t);
        }
    }

    /** 守卫是否挂在"当前这个" ViewTreeObserver 上：只判断键在不在会把失效监听算成已挂 */
    private static int overlayGuardAttached(View cv) {
        synchronized (OVERLAY_GUARDS) {
            ViewTreeObserver.OnPreDrawListener l = OVERLAY_GUARDS.get(cv);
            ViewTreeObserver v = OVERLAY_GUARD_VTO.get(cv);
            return l != null && v != null && v == cv.getViewTreeObserver()
                    && v.isAlive() ? 1 : 0;
        }
    }

    private static void guardOverlay(final View contentView, final View root,
                                     final IosNotificationRow overlay) {
        ViewTreeObserver vto = contentView.getViewTreeObserver();
        boolean sameGuard;
        synchronized (OVERLAY_GUARDS) {
            ViewTreeObserver.OnPreDrawListener boundListener = OVERLAY_GUARDS.get(contentView);
            ViewTreeObserver boundObserver = OVERLAY_GUARD_VTO.get(contentView);
            sameGuard = boundListener != null && boundObserver == vto && vto.isAlive();
            if (!sameGuard) {
                // 旧的 ViewTreeObserver 已经换了：先把失效监听摘掉再重挂
                GUARDED.remove(contentView);
                if (boundListener != null && boundObserver != null && boundObserver.isAlive()) {
                    boundObserver.removeOnPreDrawListener(boundListener);
                }
                OVERLAY_GUARDS.remove(contentView);
                OVERLAY_GUARD_VTO.remove(contentView);
            }
        }
        if (sameGuard) {
            return;
        }
        GUARDED.put(contentView, Boolean.TRUE);
        ViewTreeObserver liveVto = contentView.getViewTreeObserver();
        ViewTreeObserver.OnPreDrawListener guard = preDrawGuard(() -> {
                        if (!OVERLAYS.containsKey(contentView) || overlay.getParent() == null) {
                            // 卡片已被摘掉（行回收或一键清除）：继续压 alpha 会让复用后的整行透明
                            return;
                        }
                        View row = rowOf(contentView);
                        // 展开态交还原生：我们的卡只取代"收起态"。A13 展开时可能画展开子视图
                        // （和新卡两份图标=重影），也可能只把收起子视图撑高（旧卡下面一条空白），
                        // 两种都要同时满足"原生可见 + 自绘卡不可见"，所以交还是原子动作
                        boolean nativeOwns = expansionHandoff(row, contentView);
                        root.setAlpha(nativeOwns ? 1f : 0f);
                        fadeOverlay(contentView, overlay, nativeOwns ? 0f : 1f);
                        if (sVerifyEnabled && setHandoffLogged(contentView, nativeOwns)) {
                            View expandedChild = currentExpanded(contentView);
                            Log.i(TAG, "notify_row: expand handoff=" + nativeOwns
                                    + " cvH=" + Math.max(contentView.getHeight(),
                                            contentView.getMeasuredHeight())
                                    + " cH=" + (root.getHeight() + "/" + root.getMeasuredHeight())
                                    + " eH=" + (expandedChild == null ? "null"
                                            : expandedChild.getHeight() + "/"
                                                    + expandedChild.getMeasuredHeight())
                                    + " eVis=" + (expandedChild == null ? -1
                                            : expandedChild.getVisibility())
                                    + " rowH=" + (row == null ? -1 : row.getHeight()));
                        }
                        if (!nativeOwns && overlay.getVisibility() != View.VISIBLE) {
                            overlay.setVisibility(View.VISIBLE);
                        }
                        overlay.applyTheme(NotifyRowSpec.night(contentView.getContext()));
                        overlay.applyTextScale();
                        if (OVERLAY_VERIFY_PENDING.containsKey(contentView)
                                && overlay.getWidth() > 0 && overlay.isShown()
                                && settledTree(overlay)) {
                            StatusBarNotification sbn = row == null ? null : sbnOf(row);
                            if (row != null && sbn != null && settledTree(row)) {
                                OVERLAY_VERIFY_PENDING.remove(contentView);
                                verifyOverlay(contentView, overlay, root, row, sbn);
                            }
                        }
                });
        liveVto.addOnPreDrawListener(guard);
        synchronized (OVERLAY_GUARDS) {
            OVERLAY_GUARDS.put(contentView, guard);
            OVERLAY_GUARD_VTO.put(contentView, liveVto);
        }
    }

    /**
     * 展开态是否该把这一行交还原生绘制。
     * 只用 isExpanded() 不够：vivo 的 mCustomAlwaysExpand 会让它恒真，那样整个功能等于关掉，
     * 所以要再加一条"几何上真的撑开了"的判据（容器高度超过收起态的自然高度，或展开子视图可见）。
     */
    /**
     * 没有自绘卡的行也会重影：A13 展开时收起子视图不被系统置为不可见，
     * 于是收起态的大图标和展开态 header 的小图标同时画出来
     * （实测 互传 行两个图标错位并存）。
     * 只在"展开子视图确实在画"时抹掉收起子视图，否则会出现整行空白——A13 还有另一种形态：
     * 行被撑到展开高度但展开子视图仍是 INVISIBLE，内容由撑大的收起子视图承载。
     */
    private static void guardExpandedGhost(final View contentView, final View rowView) {
        synchronized (EXPANDED_GHOST) {
            if (EXPANDED_GHOST.containsKey(contentView)) {
                return;
            }
            EXPANDED_GHOST.put(contentView, Boolean.TRUE);
        }
        contentView.getViewTreeObserver().addOnPreDrawListener(preDrawGuard(() -> {
            View row = rowOf(contentView);
            View contracted = currentContracted(contentView);
            if (row == null || contracted == null) {
                return;
            }
            View expanded = currentExpanded(contentView);
            // 媒体行归 MediaCardFeature 管：对它跑 hideSmallIcons 会把原生展开态的封面副本露出来
            if (expanded != null && isMediaRow(row, contracted)) {
                return;
            }
            // alpha 话语权：overlay 行归 guardOverlay、堆叠行归 guardStack，这里都不能碰。
            // 之前只排除了 overlay 行，结果把堆叠卡下面"已被压住的原生组头"又放回 alpha=1，
            // 门禁当场报"3 stack cards on screen but 0 verified"。
            IosNotificationRow overlayCard;
            boolean stacked;
            synchronized (STACK_OVERLAYS) {
                overlayCard = OVERLAYS.get(contentView);
                stacked = STACK_OVERLAYS.get(contentView) != null;
            }
            // 堆叠卡自绘展开，原生 isExpanded 不跟随，别对它做交互动画
            if (!stacked) {
                // 对称驱动两个子视图：A13/vivo 展开和收回两个方向都不会把"另一个"子视图置为
                // 不可见，只压收起侧会在收回后留下重叠（收起大图标 + 展开 header 小图标）。
                boolean isOpen = bool(row, "isExpanded");
                // overlay 行的 alpha 由 guardOverlay 独占；它的大图标飞行仍要走 morph
                if (overlayCard == null) {
                    setChildFade(contentView, "con", contracted, isOpen ? 0f : 1f);
                    if (expanded != null) {
                        setChildFade(contentView, "exp", expanded, isOpen ? 1f : 0f);
                    }
                }
                Boolean prev;
                synchronized (ROW_OPEN) {
                    prev = ROW_OPEN.get(contentView);
                    ROW_OPEN.put(contentView, isOpen);
                }
                // 只在状态真的翻转那一帧做一次位移，逐帧调用会把动画一直打断
                if (prev != null && prev.booleanValue() != isOpen && expanded != null) {
                    morphIcon(contentView, contracted, expanded, isOpen, overlayCard);
                }
            }
            // 去重必须在"绑定时"做一次：实测展开动画会把子视图的可见性/alpha 写回，
            // 等展开那一刻才动手的版本（ghost-9）两枚图标依旧并存。
            if (expanded != null && !GHOST_ICONS_HIDDEN.containsKey(expanded)) {
                GHOST_ICONS_HIDDEN.put(expanded, Boolean.TRUE);
                hideSmallIcons(expanded);
                guardSmallIcons(expanded);
                View[] hiddenTargets;
                synchronized (GUARD_TARGETS) {
                    hiddenTargets = GUARD_TARGETS.get(expanded);
                }
                // 只缓存"要按掉的图标"，展开箭头必须留在系统手里：
                // 连箭头一起常驻压透明会把唯一的展开入口吃掉
                if (hiddenTargets != null) {
                    List<View> keep = new java.util.ArrayList<>();
                    for (View v : hiddenTargets) {
                        if (v != null && !v.getClass().getSimpleName().contains("ExpandButton")) {
                            keep.add(v);
                        }
                    }
                    GHOST_TARGETS.put(expanded, keep.toArray(new View[0]));
                }
                Log.i(TAG, "notify_row: ghost dedup cls="
                        + expanded.getClass().getSimpleName()
                        + " targets=" + (hiddenTargets == null ? -1 : hiddenTargets.length));
            }
            // 展开动画会把这些视图的 alpha 一路写回 1，而挂在展开子视图自己 VTO 上的
            // 逐帧守卫在 attach 之后就已经不是当前观察器了（和 overlay 那次同一个坑）。
            // 所以从 contentView 的活守卫里补一次：漂移了才写，开销是几个 float 比较。
            View[] ghost = expanded == null ? null : GHOST_TARGETS.get(expanded);
            ImageView flying;
            synchronized (FLIGHT) {
                flying = FLIGHT.get(contentView);
            }
            // 小图标一律常驻隐藏，飞行期间也不例外：现在飞行的是 38dp 大图标，
            // 展开动画会把 header 小图标的 alpha 写回 1（实测 mid 采样 smallAlpha=1.0），
            // 一旦跳过补写，用户就会看到小图标在过渡中闪现——这正是用户报的问题。
            if (ghost != null) {
                for (View v : ghost) {
                    if (v != null && v.getAlpha() != 0f) {
                        v.setAlpha(0f);
                        if (sVerifyEnabled && sGhostReapplied++ < 4) {
                            Log.i(TAG, "notify_row: ghost reapply n=" + sGhostReapplied
                                    + " cls=" + v.getClass().getSimpleName());
                        }
                    }
                }
            }
            // 飞行中两侧图标由 flight 代班，收/展两侧的 alpha 都别碰
            boolean flyingNow = flying != null && flying.getVisibility() == View.VISIBLE;
            if (flyingNow && contentView instanceof ViewGroup) {
                // AOSP 展开动画会把自己的子视图重新置于上层，一次性 bringToFront 会被覆盖；
                // 飞行期间必须每帧检查并置顶（只在真的不在最上层时才重排，避免每帧 requestLayout）
                ViewGroup cv = (ViewGroup) contentView;
                if (cv.indexOfChild(flying) != cv.getChildCount() - 1) {
                    cv.bringChildToFront(flying);
                }
            }
            boolean openNow = bool(row, "isExpanded");
            if (!stacked && !flyingNow) {
                if (!openNow) {
                    // 收起态大图标必须始终可见：morph 的源图标被按掉过，行复用后也可能残留 0
                    ImageView conIcon = overlayCard != null
                            ? overlayCard.iconView() : iconOf(contracted);
                    if (conIcon != null && conIcon.getAlpha() != 1f) {
                        conIcon.setAlpha(1f);
                    }
                } else if (expanded != null && expanded.getVisibility() == View.VISIBLE
                        && expanded.getAlpha() > 0.01f) {
                    // A13 的展开动画会把收起子视图的 alpha 写回 1（一次性淡变挡不住，交接 §4.2）：
                    // 展开子视图真的在画之后，收起侧必须逐帧压回 0，否则收起大图标一直叠在
                    // 展开内容上 = 两枚大图标。只认"展开子视图 VISIBLE 且 alpha>0"这一种形态，
                    // A13 另一种"行被撑高但内容仍由收起子视图承载"的形态不能碰（会整行空白）。
                    if (overlayCard == null && contracted.getAlpha() != 0f) {
                        contracted.setAlpha(0f);
                        if (sVerifyEnabled && sConZeroed++ < 4) {
                            Log.i(TAG, "notify_row: con alpha re-zero n=" + sConZeroed);
                        }
                    }
                    // 展开态只留这一枚 38dp 大图标（小图标在 ghost 去重里保持常驻隐藏）
                    ImageView expIcon = iconOf(expanded);
                    if (expIcon != null && expIcon.getAlpha() != 1f) {
                        expIcon.setAlpha(1f);
                    }
                }
            }
        }));
    }

    /** 播放中通知（媒体卡）由 MediaCardFeature 独占自绘，notify_row 不能碰它的子视图 */
    private static boolean isMediaRow(View row, View contracted) {
        try {
            StatusBarNotification sbn = row == null ? null : sbnOf(row);
            if (sbn != null && sbn.getNotification() != null
                    && Notification.CATEGORY_TRANSPORT.equals(sbn.getNotification().category)) {
                return true;
            }
        } catch (Throwable ignored) {
        }
        return contracted != null && contracted.getClass().getName().contains("Media");
    }

    private static boolean expansionHandoff(View row, View contentView) {
        if (row == null || contentView == null || !bool(row, "isExpanded")) {
            return false;
        }
        View expanded = currentExpanded(contentView);
        if (expanded != null && expanded.getVisibility() == View.VISIBLE
                && expanded.getAlpha() > 0f) {
            return true;
        }
        View contracted = currentContracted(contentView);
        if (contracted == null) {
            return false;
        }
        int cvH = Math.max(contentView.getHeight(), contentView.getMeasuredHeight());
        // 取 height 与 measuredHeight 的较小值当"收起态自然高度"：实测 A13 会把收起子视图
        // 的 layout 高度一起撑到容器高度，只看 getHeight 永远相等、判不出展开
        int cH = Math.min(contracted.getHeight(), contracted.getMeasuredHeight());
        return cH > 0 && cvH > cH + 4;
    }

    /** 从行内任意视图向上找 NotificationContentView（收起/展开两个子视图的共同父） */
    private static View contentViewOf(View any) {
        View v = any;
        for (int i = 0; i < 8 && v != null; i++) {
            if (v.getClass().getName().contains("NotificationContentView")) {
                return v;
            }
            ViewParent p = v.getParent();
            v = p instanceof View ? (View) p : null;
        }
        return null;
    }

    /** 图标守卫用的版本：只给"我们的卡已经淡出"的行还原原生图标 */
    private static boolean nativeOwnsIcons(View anyRowChild) {
        View cv = contentViewOf(anyRowChild);
        if (cv == null) {
            return false;
        }
        View row = rowOf(cv);
        return row != null && expansionHandoff(row, cv);
    }

    /** 目标值没变就不重启动画，否则每帧 animate() 会把淡入淡出永远停在起点 */
    private static void fadeOverlay(View contentView, View overlay, float to) {
        Float last;
        synchronized (OVERLAY_FADE_TO) {
            last = OVERLAY_FADE_TO.get(contentView);
            if (last != null && Math.abs(last - to) < 0.01f) {
                return;
            }
            OVERLAY_FADE_TO.put(contentView, to);
        }
        try {
            if (to <= 0f) {
                // 卡淡出后不能继续吃掉点击：展开态要点的是原生行本身
                overlay.setClickable(false);
                overlay.setLongClickable(false);
                overlay.animate().alpha(0f).setDuration(OVERLAY_FADE_MS).start();
            } else {
                overlay.setClickable(true);
                overlay.setLongClickable(true);
                overlay.animate().alpha(1f).setDuration(OVERLAY_FADE_MS).start();
            }
        } catch (Throwable t) {
            overlay.setAlpha(to);
        }
    }

    /** 每个 contentView 各自记交接状态，只在翻转时打一行，避免逐帧刷屏 */
    private static boolean setHandoffLogged(View contentView, boolean now) {
        Boolean prev;
        synchronized (HANDOFF_LOGGED) {
            prev = HANDOFF_LOGGED.get(contentView);
            if (prev != null && prev.booleanValue() == now) {
                return false;
            }
            HANDOFF_LOGGED.put(contentView, now);
        }
        return true;
    }

    /** 目标值没变就不重启动画，否则每帧 animate() 会把淡入淡出永远停在起点 */
    private static void setChildFade(View contentView, String which, View child, float to) {
        Float last;
        synchronized (CHILD_FADE_TO) {
            last = CHILD_FADE_TO.get(child);
            if (last != null && Math.abs(last - to) < 0.01f) {
                return;
            }
            CHILD_FADE_TO.put(child, to);
        }
        if (sVerifyEnabled) {
            Log.i(TAG, "notify_row: fade " + which + " to=" + to
                    + " vis=" + child.getVisibility() + " h=" + child.getHeight());
        }
        try {
            child.animate().alpha(to).setDuration(OVERLAY_FADE_MS).start();
        } catch (Throwable t) {
            child.setAlpha(to);
        }
    }

    /**
     * 图标变化的正确形态是"同一枚图标走过去"：展开/收回时，收起态的大图标和展开态 header 的
     * 小图标如果只是对淡，眼睛看到的是闪烁而不是变化。这里在 contentView 上盖一枚飞行图标，
     * 过渡期间按住两侧原生图标，只让这一枚从旧矩形连续平移+缩放到新矩形，落定后交还目标图标。
     * 任一侧几何拿不到就什么都不做（保留原有的淡变，不会更差）。
     */
    private static void morphIcon(final View contentView, final View contracted,
                                  final View expanded, final boolean open,
                                  IosNotificationRow card) {
        // 源必须是用户当前看到的那枚：overlay 卡图标 / restyle 自加的 38dp 图标。
        // 旧版对两侧都取 firstIconView()，拿到的是被 ghost 压成 alpha=0 的 header 小图标，
        // 于是 from==to（原地不动）——大图标照旧淡出，小图标在被系统写回 alpha 时闪现。
        final View fromIcon = visibleIcon(open ? contracted : expanded, open ? card : null);
        final View toIcon = visibleIcon(open ? expanded : contracted, open ? null : card);
        if (!(contentView instanceof ViewGroup) || fromIcon == null || toIcon == null
                || fromIcon == toIcon) {
            if (sVerifyEnabled) {
                Log.i(TAG, "notify_row: icon morph skip open=" + (open ? 1 : 0)
                        + " from=" + (fromIcon == null ? "null" : "1")
                        + " to=" + (toIcon == null ? "null" : "1")
                        + (fromIcon != null && fromIcon == toIcon ? " same" : ""));
            }
            return;
        }
        Drawable d = iconDrawable(fromIcon);
        if (d == null) {
            d = iconDrawable(toIcon);
        }
        if (d == null) {
            return;
        }
        int[] from = rectIn(fromIcon, contentView);
        int[] to = rectIn(toIcon, contentView);
        if (from == null || to == null || from[2] <= 0 || to[2] <= 0) {
            if (sVerifyEnabled) {
                Log.i(TAG, "notify_row: icon morph skip open=" + (open ? 1 : 0)
                        + " geometry from=" + (from == null ? "null" : "ok")
                        + " to=" + (to == null ? "null" : "ok"));
            }
            return;
        }
        // 飞行图标必须是**绑定时就建好**的：在 preDraw 里 addView 会让这一帧不画新 child，
        // 而源图标已被按成 alpha 0 —— 用户看到的就是"图标消失一帧再出现"（闪现）。
        final ImageView flight = ensureFlight(contentView);
        if (flight == null) {
            return;
        }
        // 展开子视图是首次展开时才挂进 contentView 的，会盖在预建的 flight 之上 ——
        // 不置顶的话开场那段飞行图标被整块盖住，看起来就是"图标消失再出现"（闪现）。
        flight.bringToFront();
        ViewGroup.LayoutParams lp = flight.getLayoutParams();
        if (lp != null && (lp.width != from[2] || lp.height != from[3])) {
            // 只有尺寸真的变了才改 LP（会触发一次 layout）；常规 38->38 是纯位移
            lp.width = from[2];
            lp.height = from[3];
            flight.setLayoutParams(lp);
        }
        if (flight.getWidth() != from[2] || flight.getHeight() != from[3]) {
            layoutFlight(flight, from[2], from[3]);
        }
        flight.setImageDrawable(d);
        flight.setTranslationX(from[0]);
        flight.setTranslationY(from[1]);
        flight.setScaleX(1f);
        flight.setScaleY(1f);
        flight.setAlpha(1f);
        flight.setVisibility(View.VISIBLE);
        // 飞行途中两侧原生图标都不许露脸，否则又变成两枚并存
        hideIconForMorph(fromIcon, true);
        hideIconForMorph(toIcon, true);
        float sx = to[2] / (float) from[2];
        float sy = to[3] / (float) from[3];
        final ImageView flyer = flight;
        final int[] dst = to;
        try {
            flyer.animate().translationX(to[0]).translationY(to[1])
                    .scaleX(sx).scaleY(sy).setDuration(MORPH_MS)
                    .setInterpolator(NotifyRowSpec.spring())
                    .withEndAction(new Runnable() {
                        @Override
                        public void run() {
                            flyer.setVisibility(View.INVISIBLE);
                            // 只交还"落点"那一枚（展开态=展开子视图的 38dp 大图标，
                            // 收起态=收起子视图的大图标）。源图标留在 alpha 0：
                            // A13 两个子视图会同时画，放回源图标就是两枚大图标并存。
                            // 收起态的还原由守卫在落定后兜底。
                            hideIconForMorph(toIcon, false);
                            hideIconForMorph(fromIcon, true);
                            // 落定这一帧同步把展开态小图标压回 0：系统动画刚写过 alpha 1，
                            // 等下一帧 preDraw 才补就是一次肉眼可见的闪现
                            View small = firstIconView(expanded);
                            if (small != null) {
                                small.setAlpha(0f);
                            }
                            View[] gt = GHOST_TARGETS.get(expanded);
                            if (gt != null) {
                                for (View v : gt) {
                                    if (v != null) {
                                        v.setAlpha(0f);
                                    }
                                }
                            }
                            if (sVerifyEnabled) {
                                Log.i(TAG, "notify_row: icon morph landed open=" + (open ? 1 : 0)
                                        + " dest=" + dst[0] + ',' + dst[1] + 'x' + dst[2]
                                        + " srcAlpha=" + fromIcon.getAlpha()
                                        + " destAlpha=" + toIcon.getAlpha()
                                        + " smallAlpha=" + (small == null ? -1 : small.getAlpha()));
                            }
                        }
                    })
                    .start();
            if (sVerifyEnabled) {
                // 中段采样：证明这是一枚图标在中间位置连续飞行，且两枚原生图标+小图标都不可见
                flyer.postDelayed(safe(() -> {
                    View small = firstIconView(expanded);
                    String extra = "";
                    if (contentView instanceof ViewGroup) {
                        ViewGroup cv = (ViewGroup) contentView;
                        extra = " idx=" + cv.indexOfChild(flyer) + "/" + cv.getChildCount()
                                + " pA=" + cv.getAlpha() + " pVis=" + cv.getVisibility()
                                + " wh=" + flyer.getWidth() + "x" + flyer.getHeight()
                                + " shown=" + (flyer.isShown() ? 1 : 0)
                                + " d=" + (flyer.getDrawable() != null ? 1 : 0);
                    }
                    Log.i(TAG, "notify_row: icon morph mid open=" + (open ? 1 : 0)
                            + " vis=" + flyer.getVisibility()
                            + " x=" + Math.round(flyer.getTranslationX())
                            + " y=" + Math.round(flyer.getTranslationY())
                            + " s=" + flyer.getScaleX()
                            + " srcAlpha=" + fromIcon.getAlpha()
                            + " dstAlpha=" + toIcon.getAlpha()
                            + " smallAlpha=" + (small == null ? -1 : small.getAlpha())
                            + extra);
                }), MORPH_MS / 2);
            }
        } catch (Throwable t) {
            flight.setVisibility(View.INVISIBLE);
            hideIconForMorph(fromIcon, false);
            hideIconForMorph(toIcon, false);
        }
        if (sVerifyEnabled) {
            boolean moved = Math.abs(from[0] - to[0]) > 1 || Math.abs(from[1] - to[1]) > 1
                    || Math.abs(from[2] - to[2]) > 1;
            Log.i(TAG, "notify_row: icon morph open=" + (open ? 1 : 0)
                    + " from=" + from[0] + ',' + from[1] + 'x' + from[2]
                    + " to=" + to[0] + ',' + to[1] + 'x' + to[2]
                    + " moved=" + (moved ? 1 : 0) + " sx=" + sx
                    + " srcKind=" + iconKind(fromIcon, open ? contracted : expanded,
                            open ? card : null)
                    + " dstKind=" + iconKind(toIcon, open ? expanded : contracted,
                            open ? null : card));
        }
    }

    /**
     * 某一侧的 38dp 应用大图标：overlay 卡图标，或 restyle 自加进该子视图的图标。
     * **不退化到 header 小图标**——展开态的小图标是刻意隐藏的（用户明确不要），
     * 拿它当飞行落点等于把隐形图标当目标，旧版就是这么飞了个原地不动的动画。
     * 翻转帧展开子视图可能还是 INVISIBLE，所以不能用 isShown()。
     */
    private static View visibleIcon(View child, IosNotificationRow card) {
        if (child == null) {
            return card == null ? null : card.iconView();
        }
        if (card != null) {
            ImageView icon = card.iconView();
            if (icon != null && icon.getWidth() > 0 && icon.getHeight() > 0) {
                return icon;
            }
        }
        ImageView own = iconOf(child);
        return own != null && own.getWidth() > 0 && own.getHeight() > 0 ? own : null;
    }

    private static void layoutFlight(View f, int w, int h) {
        if (f == null || w <= 0 || h <= 0) {
            return;
        }
        f.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY));
        f.layout(0, 0, w, h);
    }

    /** 飞行图标视图：绑定时就挂好（不可见），morph 只用 translation/scale，避免首帧丢 draw */
    private static ImageView ensureFlight(View contentView) {
        if (!(contentView instanceof ViewGroup)) {
            return null;
        }
        synchronized (FLIGHT) {
            ImageView f = FLIGHT.get(contentView);
            if (f != null) {
                return f;
            }
            Context ctx = contentView.getContext();
            f = new ImageView(ctx);
            f.setScaleType(ImageView.ScaleType.FIT_CENTER);
            f.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            f.setClipToOutline(true);
            GradientDrawable bg = new GradientDrawable();
            bg.setCornerRadius(NotifyRowSpec.dp(ctx, NotifyRowSpec.ICON_CORNER_DP));
            bg.setColor(0);
            f.setBackground(bg);
            f.setVisibility(View.INVISIBLE);
            int size = NotifyRowSpec.dp(ctx, NotifyRowSpec.ICON_DP);
            ((ViewGroup) contentView).addView(f,
                    new android.widget.FrameLayout.LayoutParams(size, size));
            // NotificationContentView.onMeasure/onLayout 只认它自己的三个子视图，
            // 额外加的 child 永远不会被测量（实测 wh=0x0，飞行图标根本没画出来）。
            // 这里自己量一次、摆一次；之后 parent 不会覆盖它。
            layoutFlight(f, size, size);
            FLIGHT.put(contentView, f);
            return f;
        }
    }

    private static String iconKind(View v, View child, IosNotificationRow card) {
        if (v == null) {
            return "null";
        }
        if (card != null && v == card.iconView()) {
            return "card";
        }
        if (child != null && v == iconOf(child)) {
            return "own";
        }
        return "small";
    }

    /** 子树里第一枚"应用图标"（AOSP 用 CachingIconView；避开动作区，那里也有 :id/icon） */
    private static View firstIconView(View root) {
        if (root == null) {
            return null;
        }
        String simple = root.getClass().getSimpleName();
        if (simple.equals("CachingIconView")) {
            return root;
        }
        if (root instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) root;
            for (int i = 0; i < g.getChildCount(); i++) {
                View hit = firstIconView(g.getChildAt(i));
                if (hit != null) {
                    return hit;
                }
            }
        }
        return null;
    }

    private static Drawable iconDrawable(View iconView) {
        try {
            return iconView instanceof ImageView
                    ? ((ImageView) iconView).getDrawable() : null;
        } catch (Throwable t) {
            return null;
        }
    }

    /** 视图在 contentView 坐标系里的矩形；没布局好就返回 null */
    private static int[] rectIn(View v, View parent) {
        if (v == null || v.getWidth() <= 0 || v.getHeight() <= 0) {
            return null;
        }
        int[] a = new int[2];
        int[] b = new int[2];
        v.getLocationInWindow(a);
        parent.getLocationInWindow(b);
        return new int[]{a[0] - b[0], a[1] - b[1], v.getWidth(), v.getHeight()};
    }

    private static void hideIconForMorph(View icon, boolean hidden) {
        try {
            if (icon != null) {
                icon.setAlpha(hidden ? 0f : 1f);
            }
        } catch (Throwable ignored) {
        }
    }

    /** 走原生长按入口（弹出通知菜单/设置），performLongClick 在部分行上没有绑定 */
    private static boolean longClickRow(View row) {
        try {
            int x = Math.max(1, row.getWidth() / 2);
            int y = Math.max(1, row.getHeight() / 2);
            XposedHelpers.callMethod(row, "doLongClickCallback", x, y);
            return true;
        } catch (Throwable t) {
            try {
                return row.performLongClick();
            } catch (Throwable ignored) {
                return false;
            }
        }
    }

    private static String appLabel(Context ctx, String pkg) {
        try {
            PackageManager pm = ctx.getPackageManager();
            return pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString();
        } catch (Throwable t) {
            return pkg;
        }
    }

    private static CharSequence conversationTitle(Notification n) {
        if (n.extras == null) {
            return null;
        }
        CharSequence title = n.extras.getCharSequence("android.conversationTitle");
        if (TextUtils.isEmpty(title)) {
            title = n.extras.getCharSequence(Notification.EXTRA_TITLE);
        }
        return title;
    }

    private static CharSequence latestMessage(Notification n) {
        if (n.extras == null) {
            return null;
        }
        CharSequence fallback = n.extras.getCharSequence(Notification.EXTRA_TEXT);
        Parcelable[] messages = n.extras.getParcelableArray("android.messages");
        if (messages == null) {
            return fallback;
        }
        String self = n.extras.getString("android.selfDisplayName");
        String lastAny = null;
        String lastIncoming = null;
        String lastSender = null;
        for (Parcelable p : messages) {
            if (!(p instanceof Bundle)) {
                continue;
            }
            Bundle bundle = (Bundle) p;
            CharSequence text = bundle.getCharSequence("text");
            if (TextUtils.isEmpty(text)) {
                continue;
            }
            String sender = personName(bundle.getParcelable("sender"));
            lastAny = text.toString();
            if (self == null || sender == null || !self.equals(sender)) {
                lastIncoming = text.toString();
                lastSender = sender;
            }
        }
        String text = lastIncoming != null ? lastIncoming : lastAny;
        if (text == null) {
            return fallback;
        }
        CharSequence convTitle = conversationTitle(n);
        if (lastSender != null && (convTitle == null
                || !convTitle.toString().equals(lastSender))) {
            return lastSender + "：" + text;
        }
        return text;
    }

    private static String personName(Parcelable person) {
        if (person instanceof Person) {
            CharSequence name = ((Person) person).getName();
            return name == null ? null : name.toString();
        }
        return person == null ? null : person.toString();
    }

    private static CharSequence relativeTime(long when) {
        return DateUtils.getRelativeTimeSpanString(when, System.currentTimeMillis(),
                DateUtils.MINUTE_IN_MILLIS, DateUtils.FORMAT_ABBREV_RELATIVE);
    }

    private static void scheduleStackVerify(final View contentView,
                                            final IosNotificationRow overlay,
                                            final View root, final View row,
                                            final StatusBarNotification front,
                                            final int count, final int attempt) {
        overlay.postDelayed(safe(() -> {
            if (overlay.getWidth() <= 0 || !overlay.isShown()
                    || !settledTree(overlay) || !settledTree(row)) {
                if (attempt < 8) {
                    scheduleStackVerify(contentView, overlay, root, row, front,
                            count, attempt + 1);
                    return;
                }
                // 静默放弃等于"没看"：必须留一条证据，否则校验器会以为一切正常
                Log.w(TAG, "notify_row.verify.stack.skipped pkg=" + front.getPackageName()
                        + " count=" + count + " w=" + overlay.getWidth()
                        + " shown=" + (overlay.isShown() ? 1 : 0)
                        + " settledOverlay=" + (settledTree(overlay) ? 1 : 0)
                        + " settledRow=" + (settledTree(row) ? 1 : 0));
                return;
            }
            verifyStack(overlay, root, row, front, count);
        }), 600 + attempt * 500L);
    }

    private static void verifyStack(IosNotificationRow overlay, View root, View row,
                                    StatusBarNotification front, int count) {
        ImageView icon = overlay.iconView();
        TextView app = overlay.appView();
        TextView time = overlay.timeView();
        TextView title = overlay.titleView();
        TextView body = overlay.bodyView();
        int w = overlay.getWidth();
        int h = overlay.getHeight();
        int cardBottom = overlay.cardBottom();
        int[] il = loc(icon, overlay);
        int[] al = loc(app, overlay);
        int[] tl = loc(title, overlay);
        int[] bl = loc(body, overlay);
        int[] tm = loc(time, overlay);
        boolean rtl = overlay.getLayoutDirection() == View.LAYOUT_DIRECTION_RTL;
        int rowH = row.getHeight();
        int contractedH = rowH;
        View contracted = currentContracted((View) overlay.getParent());
        if (contracted != null) {
            contractedH = contracted.getMeasuredHeight();
        }
        boolean expanded = bool(row, "isGroupExpanded");
        int[] rl = new int[2];
        row.getLocationOnScreen(rl);
        ViewGroup cont = childrenContainerOf(row);
        int contVis = cont == null ? -1 : cont.getVisibility();
        int contH = cont == null ? 0 : cont.getHeight();
        int contKids = cont == null ? 0 : cont.getChildCount();
        if (row.getParent() instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) row.getParent();
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < g.getChildCount(); i++) {
                View c = g.getChildAt(i);
                sb.append(' ').append(i).append(':').append(c.getClass().getSimpleName())
                        .append("@ty").append((int) c.getTranslationY())
                        .append("xh").append(c.getHeight())
                        .append("/mh").append(c.getMeasuredHeight())
                        .append("/v").append(c.getVisibility())
                        .append("/a").append((int) (c.getAlpha() * 100));
                if (c instanceof ViewGroup) {
                    int kids = ((ViewGroup) c).getChildCount();
                    sb.append("/kids").append(kids);
                    for (int j = 0; j < kids; j++) {
                        View gc = ((ViewGroup) c).getChildAt(j);
                        String gcn = gc.getClass().getSimpleName();
                        if (gcn.contains("ChildrenContainer")) {
                            sb.append("{").append(gcn).append(" v").append(gc.getVisibility())
                                    .append(" h").append(gc.getHeight())
                                    .append(" n").append(((ViewGroup) gc).getChildCount())
                                    .append('}');
                        }
                    }
                }
                StatusBarNotification cSbn = sbnOf(c);
                sb.append('/').append(cSbn == null ? "-" : cSbn.getPackageName())
                        .append('/').append(cSbn == null ? "-" : String.valueOf(cSbn.getId()))
                        .append(cSbn != null && bool(c, "isChildInGroup") ? "/child" : "")
                        .append(cSbn != null && bool(c, "isSummaryWithChildren") ? "/summary" : "")
                        .append(bool(c, "isUserExpanded") ? "/userExp" : "")
                        .append(bool(c, "isGroupExpanded") ? "/grpExp" : "");
            }
            Log.i(TAG, "notify_row.stack.layout" + sb);
        }
        Log.i(TAG, "notify_row.verify.stack pkg=" + front.getPackageName()
                + " rowId=" + Integer.toHexString(System.identityHashCode(row))
                + " gkey=" + (front.getGroupKey() == null ? "-" : front.getGroupKey())
                + " count=" + count + " bars=" + overlay.stackBars()
                + " stack=1 overlayW=" + w + " overlayH=" + h
                + " rowH=" + rowH + " contractedH=" + contractedH
                + " cardExpanded=" + (overlay.isStackExpanded() ? 1 : 0)
                + " kids=" + overlay.stackChildCount()
                + " rcH=" + overlay.rowContentHeight()
                + " clH=" + overlay.childListHeight()
                + " padT=" + overlay.getPaddingTop()
                + " padB=" + overlay.getPaddingBottom()
                + " rowY=" + rl[1] + " contVis=" + contVis + " contH=" + contH
                + " contKids=" + contKids + " overlayVis=" + overlay.getVisibility()
                + " peekPx=" + overlay.stackPeekPx()
                + " cardColor=0x" + Integer.toHexString(overlay.stackCardColor())
                + " night=" + (NotifyRowSpec.night(overlay.getContext()) ? 1 : 0)
                + " density=" + overlay.getResources().getDisplayMetrics().density
                + " fontScale=" + overlay.getResources().getConfiguration().fontScale
                + " groupExpanded=" + (expanded ? 1 : 0)
                + " rtl=" + (rtl ? 1 : 0)
                + " iconW=" + icon.getWidth() + " iconH=" + icon.getHeight()
                + " iconX=" + il[0] + " iconStart=" + (rtl ? w - il[0] - icon.getWidth() : il[0])
                + " cardBottom=" + cardBottom
                + " iconCenterDelta=" + (il[1] + icon.getHeight() / 2 - cardBottom / 2)
                + " iconDrawable=" + (icon.getDrawable() == null ? 0 : 1)
                + " appX=" + al[0] + " appStart=" + (rtl ? w - al[0] - app.getWidth() : al[0])
                + " appTS=" + Math.round(app.getTextSize()) + " appColor=0x" + Integer.toHexString(app.getCurrentTextColor())
                + " badgeCx=" + overlay.badgeCenterX() + " badgeCy=" + overlay.badgeCenterY()
                + " badgeR=" + overlay.badgeRadius() + " badgeTotal=" + overlay.stackTotal()
                + " badgeRightGap=" + (il[0] + icon.getWidth() - overlay.badgeCenterX())
                + " titleX=" + tl[0] + " titleStart=" + (rtl ? w - tl[0] - title.getWidth() : tl[0])
                + " titleTS=" + Math.round(title.getTextSize()) + " titleColor=0x" + Integer.toHexString(title.getCurrentTextColor())
                + " titleEndGap=" + (rtl ? tl[0] : w - tl[0] - title.getWidth())
                + " titleMax=" + title.getMaxLines() + " titleLines=" + lineCount(title)
                + " bodyX=" + bl[0] + " bodyStart=" + (rtl ? w - bl[0] - body.getWidth() : bl[0])
                + " bodyTS=" + Math.round(body.getTextSize()) + " bodyColor=0x" + Integer.toHexString(body.getCurrentTextColor())
                + " bodyEndGap=" + (rtl ? bl[0] : w - bl[0] - body.getWidth())
                + " bodyMax=" + body.getMaxLines() + " bodyLines=" + lineCount(body)
                + " timeEndGap=" + (rtl ? tm[0] : w - tm[0] - time.getWidth())
                + " timeTS=" + Math.round(time.getTextSize())
                + " timeColor=0x" + Integer.toHexString(time.getCurrentTextColor())
                + " timeVis=" + time.getVisibility()
                + " nAppTimeGap=" + (rtl
                        ? al[0] - (tm[0] + time.getWidth())
                        : tm[0] - (al[0] + app.getWidth())));
    }

    private static void scheduleOverlayVerify(final View contentView,
                                              final IosNotificationRow overlay,
                                              final View root,
                                              final View row,
                                              final StatusBarNotification sbn,
                                              final int attempt) {
        OVERLAY_VERIFY_PENDING.put(contentView, Boolean.TRUE);
        overlay.postDelayed(safe(() -> {
            if (overlay.getWidth() <= 0 || !overlay.isShown()
                    || !settledTree(overlay) || (row != null && !settledTree(row))) {
                if (attempt < 8) {
                    scheduleOverlayVerify(contentView, overlay, root, row, sbn,
                            attempt + 1);
                } else {
                    OVERLAY_VERIFY_PENDING.remove(contentView);
                }
                return;
            }
            OVERLAY_VERIFY_PENDING.remove(contentView);
            verifyOverlay(contentView, overlay, root, row, sbn);
        }), 600 + attempt * 500L);
    }

    private static void verifyOverlay(View contentView, IosNotificationRow overlay,
                                      View root, View row, StatusBarNotification sbn) {
        ImageView icon = overlay.iconView();
        TextView app = overlay.appView();
        TextView time = overlay.timeView();
        TextView title = overlay.titleView();
        TextView body = overlay.bodyView();
        int w = overlay.getWidth();
        int h = overlay.getHeight();
        int[] il = loc(icon, overlay);
        int[] al = loc(app, overlay);
        int[] tl = loc(title, overlay);
        int[] bl = loc(body, overlay);
        int[] tm = loc(time, overlay);
        int iconCenterDelta = il[1] + icon.getHeight() / 2 - h / 2;
        int timeEndGap = w - (tm[0] + time.getWidth());
        int titleEndGap = w - (tl[0] + title.getWidth());
        int bodyEndGap = w - (bl[0] + body.getWidth());
        int appW = app.getWidth();
        int appTimeGap = tm[0] - (al[0] + appW);
        boolean rtl = overlay.getLayoutDirection() == View.LAYOUT_DIRECTION_RTL;
        int iconStart = rtl ? w - (il[0] + icon.getWidth()) : il[0];
        int appStart = rtl ? w - (al[0] + appW) : al[0];
        int titleStart = rtl ? w - (tl[0] + title.getWidth()) : tl[0];
        int bodyStart = rtl ? w - (bl[0] + body.getWidth()) : bl[0];
        int nTimeEndGap = rtl ? tm[0] : timeEndGap;
        int nTitleEndGap = rtl ? tl[0] : titleEndGap;
        int nBodyEndGap = rtl ? bl[0] : bodyEndGap;
        int nAppTimeGap = rtl ? al[0] - (tm[0] + time.getWidth()) : appTimeGap;
        int stackRows = 0;
        int cardLeft = -1;
        int cardRight = -1;
        int gapAbove = -1;
        int stackSide = -1;
        int stackGap = -1;
        int stackCorner = -1;
        int stackChildContainers = 0;
        if (row.getParent() instanceof ViewGroup) {
            ViewGroup stack = (ViewGroup) row.getParent();
            cardLeft = row.getLeft();
            cardRight = stack.getWidth() - row.getRight();
            try {
                stackSide = XposedHelpers.getIntField(stack, "mSidePaddings");
                stackGap = XposedHelpers.getIntField(stack, "mPaddingBetweenElements");
                stackCorner = XposedHelpers.getIntField(stack, "mCornerRadius");
            } catch (Throwable ignored) {
            }
            int idx = stack.indexOfChild(row);
            for (int i = 0; i < stack.getChildCount(); i++) {
                View child = stack.getChildAt(i);
                if (child.getClass().getSimpleName()
                        .equals("ExpandableNotificationRow")) {
                    stackRows++;
                }
                if (child.getClass().getSimpleName().contains("ChildrenContainer")
                        && child.isShown()) {
                    stackChildContainers++;
                }
            }
            for (int i = idx - 1; i >= 0; i--) {
                View c = stack.getChildAt(i);
                if (c.getClass().getSimpleName().equals("ExpandableNotificationRow")
                        && c.getVisibility() == View.VISIBLE) {
                    gapAbove = Math.round(row.getY() - (c.getY() + c.getHeight()));
                    break;
                }
            }
        }
        Log.i(TAG, "notify_row.verify.overlay pkg=" + sbn.getPackageName()
                + " style=messaging overlay=1 shown=" + (overlay.isShown() ? 1 : 0)
                + " guard=" + overlayGuardAttached(contentView)
                + " nativeAlpha=" + (root == null ? -1f : root.getAlpha())
                + " nativeVis=" + (root == null ? -1 : root.getVisibility())
                + " handoff=" + (contentView == null ? 0
                        : (expansionHandoff(row, contentView) ? 1 : 0))
                + " overlayAlpha=" + overlay.getAlpha()
                + " stackMode=" + (sStackMode ? 1 : 0)
                + " night=" + (NotifyRowSpec.night(overlay.getContext()) ? 1 : 0)
                + " density=" + overlay.getResources().getDisplayMetrics().density
                + " fontScale=" + overlay.getResources().getConfiguration().fontScale
                + " rootW=" + w + " overlayH=" + h + " rowH=" + row.getHeight()
                + " contentH=" + (contentView == null ? -1 : contentView.getHeight())
                + " cardLeft=" + cardLeft + " cardRight=" + cardRight
                + " gapAbove=" + gapAbove + " stackRows=" + stackRows
                + " stackChildContainers=" + stackChildContainers
                + " stackSide=" + stackSide + " stackGap=" + stackGap
                + " stackCorner=" + stackCorner
                + " groupChild=" + (bool(row, "isChildInGroup") ? 1 : 0)
                + " small=0 expand=0"
                + " origAlpha=" + root.getAlpha()
                + " overlayVis=" + overlay.getVisibility()
                + " rtl=" + (rtl ? 1 : 0)
                + " iconW=" + icon.getWidth() + " iconH=" + icon.getHeight()
                + " iconX=" + il[0] + " iconStart=" + iconStart
                + " iconCenterDelta=" + iconCenterDelta
                + " iconDrawable=" + (icon.getDrawable() != null ? 1 : 0)
                + " appX=" + al[0] + " appStart=" + appStart
                + " appTS=" + Math.round(app.getTextSize())
                + " appColor=0x" + Integer.toHexString(app.getCurrentTextColor())
                + " appVis=" + app.getVisibility()
                + " titleX=" + tl[0] + " titleStart=" + titleStart
                + " titleTS=" + Math.round(title.getTextSize())
                + " titleColor=0x" + Integer.toHexString(title.getCurrentTextColor())
                + " titleEndGap=" + nTitleEndGap + " titleVis=" + title.getVisibility()
                + " titleMax=" + title.getMaxLines()
                + " bodyX=" + bl[0] + " bodyStart=" + bodyStart
                + " bodyTS=" + Math.round(body.getTextSize())
                + " bodyColor=0x" + Integer.toHexString(body.getCurrentTextColor())
                + " bodyEndGap=" + nBodyEndGap + " bodyVis=" + body.getVisibility()
                + " bodyMax=" + body.getMaxLines()
                + " timeEndGap=" + nTimeEndGap + " nAppTimeGap=" + nAppTimeGap
                + " timeTS=" + Math.round(time.getTextSize())
                + " timeColor=0x" + Integer.toHexString(time.getCurrentTextColor())
                + " timeVis=" + time.getVisibility()
                + " titleLines=" + title.getLineCount()
                + " bodyLines=" + body.getLineCount());
    }

    private static void moveTimeRight(View header, TextView time, Context ctx, int endDp) {
        if (!(header instanceof ViewGroup)) {
            return;
        }
        ViewGroup group = (ViewGroup) header;
        int endPx = NotifyRowSpec.dp(ctx, endDp);
        if (time.getParent() == group) {
            // 行会被回收复用：同一实例可能从"无动作"变成"有动作"，边距要跟着改
            if (time.getLayoutParams() instanceof RelativeLayout.LayoutParams) {
                RelativeLayout.LayoutParams tlp =
                        (RelativeLayout.LayoutParams) time.getLayoutParams();
                if (tlp.getMarginEnd() != endPx) {
                    tlp.setMarginEnd(endPx);
                    time.setLayoutParams(tlp);
                }
            }
        } else {
            ViewParent p = time.getParent();
            if (p instanceof ViewGroup) {
                ((ViewGroup) p).removeView(time);
            }
            RelativeLayout.LayoutParams lp = new RelativeLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.addRule(RelativeLayout.ALIGN_PARENT_END);
            lp.addRule(RelativeLayout.CENTER_VERTICAL);
            lp.setMarginEnd(endPx);
            group.addView(time, lp);
        }
        View topLine = findClass(header, "NotificationTopLineView");
        if (topLine != null && topLine.getLayoutParams() instanceof RelativeLayout.LayoutParams) {
            RelativeLayout.LayoutParams tlp =
                    (RelativeLayout.LayoutParams) topLine.getLayoutParams();
            if (time.getId() != 0) {
                tlp.addRule(RelativeLayout.START_OF, time.getId());
            }
            tlp.setMarginEnd(NotifyRowSpec.dp(ctx, 8));
            topLine.setLayoutParams(tlp);
        }
    }

    private static void styleTexts(Context ctx, View root, TextView title, int primary,
                                   int secondary) {
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, NotifyRowSpec.TITLE_TEXT_SP);
        title.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        title.setTextColor(primary);
        title.setSingleLine(false);
        title.setMaxLines(NotifyRowSpec.TITLE_MAX_LINES);
        title.setEllipsize(TextUtils.TruncateAt.END);
        title.setHorizontallyScrolling(false);
        ViewGroup.LayoutParams tlp = title.getLayoutParams();
        if (tlp != null) {
            tlp.width = ViewGroup.LayoutParams.MATCH_PARENT;
            title.setLayoutParams(tlp);
        }
        TextView body = firstText(root, "text", "big_text", "always");
        if (body != null && body != title) {
            body.setTextSize(TypedValue.COMPLEX_UNIT_SP, NotifyRowSpec.BODY_TEXT_SP);
            body.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
            // iOS 正文为 label 色（与标题同深浅），不是灰字
            body.setTextColor(primary);
            body.setSingleLine(false);
            body.setMaxLines(NotifyRowSpec.BODY_MAX_LINES);
            body.setEllipsize(TextUtils.TruncateAt.END);
            body.setHorizontallyScrolling(false);
            ViewGroup.LayoutParams blp = body.getLayoutParams();
            if (blp instanceof ViewGroup.MarginLayoutParams) {
                ((ViewGroup.MarginLayoutParams) blp).topMargin =
                        NotifyRowSpec.dp(ctx, NotifyRowSpec.TITLE_BODY_GAP_DP);
                body.setLayoutParams(blp);
            }
        }
    }

    private static void centerIcon(Context ctx, View root, ImageView icon) {
        ViewGroup.LayoutParams lp = icon.getLayoutParams();
        FrameLayout.LayoutParams flp;
        if (lp instanceof FrameLayout.LayoutParams) {
            flp = (FrameLayout.LayoutParams) lp;
        } else {
            flp = new FrameLayout.LayoutParams(
                    NotifyRowSpec.dp(ctx, NotifyRowSpec.ICON_DP),
                    NotifyRowSpec.dp(ctx, NotifyRowSpec.ICON_DP));
        }
        flp.width = NotifyRowSpec.dp(ctx, NotifyRowSpec.ICON_DP);
        flp.height = NotifyRowSpec.dp(ctx, NotifyRowSpec.ICON_DP);
        flp.gravity = Gravity.CENTER_VERTICAL | Gravity.START;
        flp.setMarginStart(NotifyRowSpec.dp(ctx, NotifyRowSpec.GUTTER_DP));
        flp.topMargin = 0;
        icon.setLayoutParams(flp);
    }

    /** 小图标和 OS5 展开箭头都隐藏；展开态排版与收起态不一致，iOS 卡片不提供展开箭头 */

    /** 把被隐藏名单压掉的图标放回可见（alpha/可见性都恢复；vivo 自己会重绘内容）。 */

    /**
     * 是否"应用图标"这一类节点。必须与 `collectHideTargets` 的收法一致：
     * 它既按类名 `CachingIconView` 收，也按 id `:id/icon` / `:id/left_icon` 收
     *（真机：组摘要行那颗其实是普通 `ImageView`+`android:id/icon`，
     * 上一版过滤只认 CachingIconView ⇒ 漏掉，图标仍被隐藏名单压掉）。
     */
    private static boolean isIconView(View v) {
        if (v == null) {
            return false;
        }
        if ("CachingIconView".equals(v.getClass().getSimpleName())) {
            return true;
        }
        try {
            if (v.getId() == 0) {
                return false;
            }
            String name = v.getResources().getResourceName(v.getId());
            return name != null && (name.endsWith(":id/icon") || name.endsWith(":id/left_icon"));
        } catch (Throwable t) {
            return false;
        }
    }

    private static void showIconAgain(View icon) {
        try {
            if (icon.getAlpha() < 1f) {
                icon.setAlpha(1f);
            }
            if (icon.getVisibility() == View.INVISIBLE) {
                icon.setVisibility(View.VISIBLE);
            }
            if (icon instanceof ImageView && ((ImageView) icon).getDrawable() != null
                    && ((ImageView) icon).getDrawable().getAlpha() != 255) {
                ((ImageView) icon).getDrawable().mutate().setAlpha(255);
            }
        } catch (Throwable ignored) {
        }
    }

    private static void hideSmallIcons(View root) {
        // 带内联动作的行是例外：箭头是唯一能把动作按钮够出来的入口
        boolean keepExpand = needsExpandForActions(root);
        List<View> targets = new java.util.ArrayList<>();
        collectHideTargets(root, targets, 0, keepExpand);
        // 没有"真的在显示的顶替图标"，就不许藏原生图标 —— 组摘要行按 L7 保留原生小图标、
        // 不放大图标，一旦进了这份名单就是真机报的"圆角矩形空框"（图标被每帧 GUARD_TARGETS
        // 重新压掉，只剩壳）。这里既不进名单，还要把之前压掉的恢复回来。
        if (!bigIconOwned(root)) {
            java.util.Iterator<View> it = targets.iterator();
            while (it.hasNext()) {
                View t = it.next();
                if (t == null) {
                    continue;
                }
                if (isIconView(t)) {
                    it.remove();
                    showIconAgain(t);
                }
            }
        }
        if (keepExpand) {
            // 早一轮（行还没 attach 时）可能已把箭头压过透明，alpha 不会被原生还原
            restoreExpandButton(root, 0);
        }
        GUARD_TARGETS.put(root, targets.toArray(new View[0]));
        enforceHidden(targets);
        TextView title = text(root, "title");
        TextView body = firstText(root, "text", "big_text", "always");
        View header = findClass(root, "NotificationHeaderView");
        TextView app = header == null ? null : text(header, "app_name_text");
        TextView time = text(root, "time");
        COLOR_TARGETS.put(root, new View[]{title, body, app, time});
        enforceColors(root.getContext(), title, body, app, time);
    }

    /** OS5 会在夜间/更新时把文字色改回自己的 token；按 iOS 规格持续压住 */
    private static void enforceColors(Context ctx, TextView title, TextView body,
                                      TextView app, TextView time) {
        try {
            boolean night = NotifyRowSpec.night(ctx);
            int primary = night ? NotifyRowSpec.DARK_PRIMARY : NotifyRowSpec.LIGHT_PRIMARY;
            int secondary = night ? NotifyRowSpec.DARK_SECONDARY : NotifyRowSpec.LIGHT_SECONDARY;
            if (title != null && title.getCurrentTextColor() != primary) {
                title.setTextColor(primary);
            }
            if (body != null && body != title && body.getCurrentTextColor() != primary) {
                body.setTextColor(primary);
            }
            if (app != null && app.getCurrentTextColor() != secondary) {
                app.setTextColor(secondary);
            }
            if (time != null && time.getCurrentTextColor() != secondary) {
                time.setTextColor(secondary);
            }
        } catch (Throwable ignored) {
        }
    }

    private static void enforceHidden(List<View> targets) {
        for (View v : targets) {
            if (v != null) {
                applyHidden(v);
            }
        }
    }

    /** 只用 alpha/clickable/enabled，不切 visibility，避免每帧 requestLayout 吞掉手势 */
    private static void applyHidden(View v) {
        String simple = v.getClass().getSimpleName();
        boolean expand = simple.contains("ExpandButton");
        if (v.getAlpha() != 0f) {
            v.setAlpha(0f);
        }
        if (v.getImportantForAccessibility() != View.IMPORTANT_FOR_ACCESSIBILITY_NO) {
            v.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        }
        if (expand) {
            if (v.isClickable()) {
                v.setClickable(false);
            }
            if (v.isEnabled()) {
                v.setEnabled(false);
            }
        } else if ("android.widget.Button".equals(v.getClass().getName()) && v.isClickable()) {
            v.setClickable(false);
        }
    }

    /** 摘掉 OS5 header 的展开触摸监听；点击/长按/滑动交给整行，避免吞事件 */
    private static void disableHeaderTouch(View root) {
        View header = findClass(root, "NotificationHeaderView");
        if (header != null) {
            header.setOnTouchListener(null);
            header.setOnClickListener(null);
            header.setClickable(false);
            header.setFocusable(false);
        }
    }

    /** 分组逻辑会在绑定后再次写入小图标；每帧绘制前压住（只遍历 header，开销很小） */
    private static void guardSmallIcons(final View headerOrRoot) {
        synchronized (GUARDED) {
            if (GUARDED.containsKey(headerOrRoot)) {
                return;
            }
            GUARDED.put(headerOrRoot, Boolean.TRUE);
        }
        headerOrRoot.getViewTreeObserver().addOnPreDrawListener(
                new ViewTreeObserver.OnPreDrawListener() {
                    @Override
                    public boolean onPreDraw() {
                      try {
                        View[] targets = GUARD_TARGETS.get(headerOrRoot);
                        if (targets == null) {
                            hideSmallIcons(headerOrRoot);
                            return true;
                        }
                        // 交还原生绘制时必须把原生图标放回来，否则展开态只剩文字没有图标
                        boolean giveBack = nativeOwnsIcons(headerOrRoot);
                        for (View v : targets) {
                            if (v == null) {
                                continue;
                            }
                            if (giveBack) {
                                v.setVisibility(View.VISIBLE);
                            } else {
                                applyHidden(v);
                            }
                        }
                        View[] colors = COLOR_TARGETS.get(headerOrRoot);
                        if (colors != null) {
                            enforceColors(headerOrRoot.getContext(),
                                    (TextView) colors[0], (TextView) colors[1],
                                    (TextView) colors[2], (TextView) colors[3]);
                        }
                        return true;
                      } catch (Throwable t) {
                        Log.w(TAG, "notify_row: icon guard failed", t);
                        return true;
                      }
                    }
                });
    }

    /** 按同一套命中规则反向还原展开箭头（只还原我们自己改过的三个量） */
    private static void restoreExpandButton(View v, int depth) {
        if (v == null || depth > 12) {
            return;
        }
        String name = viewIdName(v);
        if (v.getClass().getSimpleName().contains("ExpandButton") || name.contains("expand_button")) {
            if (v.getAlpha() == 0f) {
                v.setAlpha(1f);
            }
            if (v.getImportantForAccessibility() != View.IMPORTANT_FOR_ACCESSIBILITY_AUTO) {
                v.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_AUTO);
            }
            if (!v.isClickable()) {
                v.setClickable(true);
            }
            if (!v.isEnabled()) {
                v.setEnabled(true);
            }
            return;
        }
        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) {
                restoreExpandButton(g.getChildAt(i), depth + 1);
            }
        }
    }

    private static void collectHideTargets(View v, List<View> out, int depth, boolean keepExpand) {
        if (depth > 12) {
            return;
        }
        String simple = v.getClass().getSimpleName();
        if (simple.equals("CachingIconView")
                || (simple.contains("ExpandButton") && !keepExpand)) {
            out.add(v);
            return;
        }
        String selfName = "";
        try {
            if (v.getId() != 0) {
                selfName = v.getResources().getResourceName(v.getId());
            }
        } catch (Throwable ignored) {
        }
        // 动作区整棵子树都不碰：vivo OS3 的收起态按钮内部带 :id/icon，
        // 一按命中规则藏下去，按钮就"整片消失"了。展开箭头自己也是 Button，仍按上面的规则处理。
        if (isActionNode(v, selfName)) {
            return;
        }
        if (v instanceof TextView) {
            // OS5 把小组件图标以 compound drawable 塞在 app 名/时间 TextView 里
            TextView tv = (TextView) v;
            Drawable[] ds = tv.getCompoundDrawablesRelative();
            if (ds[0] != null || ds[1] != null || ds[2] != null || ds[3] != null) {
                tv.setCompoundDrawablesRelative(null, null, null, null);
                tv.setCompoundDrawables(null, null, null, null);
            }
        }
        String name = "";
        try {
            if (v.getId() != 0) {
                name = v.getResources().getResourceName(v.getId());
            }
        } catch (Throwable ignored) {
        }
        if (name.endsWith(":id/left_icon") || name.endsWith(":id/icon")
                || name.endsWith(":id/time_divider")
                || (name.contains("expand_button") && !keepExpand)) {
            out.add(v);
        }
        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) {
                collectHideTargets(g.getChildAt(i), out, depth + 1, keepExpand);
            }
        }
    }

    /** 同应用多条：默认展开一次，让每条子通知都以大图标行显示；用户手动收起后不再强制 */
    private static void autoExpandGroup(final View row, final int attempt) {
        // 堆叠模式下自动展开会把组撑开（首帧 Prefs 还没就绪时模式判定会落到分离分支）
        if (sStackMode || STACK_ROWS.containsKey(row)) {
            return;
        }
        try {
            if (!bool(row, "isSummaryWithChildren")) {
                return;
            }
            Object entry = XposedHelpers.callMethod(row, "getEntry");
            if (entry == null) {
                return;
            }
            final String key = String.valueOf(XposedHelpers.callMethod(entry, "getKey"));
            if (attempt == 0) {
                synchronized (AUTO_EXPANDED_ROWS) {
                    if (AUTO_EXPANDED_ROWS.containsKey(row)) {
                        return;
                    }
                    AUTO_EXPANDED_ROWS.put(row, Boolean.TRUE);
                }
                if (Boolean.TRUE.equals(
                        XposedHelpers.callMethod(row, "hasUserChangedGroupExpansion"))) {
                    return;
                }
            }
            XposedHelpers.callMethod(row, "setUserExpanded", true, true);
            XposedHelpers.callMethod(row, "setChildrenExpanded", true, false);
            boolean expanded = Boolean.TRUE.equals(XposedHelpers.callMethod(row, "isGroupExpanded"));
            Log.i(TAG, "notify_row: auto expand key=" + key + " attempt=" + attempt
                    + " expanded=" + expanded);
            if (!expanded) {
                if (attempt < 4) {
                    row.postDelayed(safe(() -> autoExpandGroup(row, attempt + 1)), 300);
                } else {
                    clickGroupExpand(row);
                }
            }
        } catch (Throwable t) {
            Log.w(TAG, "notify_row: auto expand failed", t);
        }
    }

    /** manager 状态没落到 UI 时，直接点组头的展开按钮（原生交互路径） */
    private static void clickGroupExpand(View row) {
        try {
            Object container = XposedHelpers.callMethod(row, "getChildrenContainer");
            if (container instanceof View) {
                View expand = findClass((View) container, "NotificationExpandButton");
                if (expand != null) {
                    expand.performClick();
                    Log.i(TAG, "notify_row: group expand clicked");
                }
            }
        } catch (Throwable t) {
            Log.w(TAG, "notify_row: group expand click failed", t);
        }
    }

    private static View currentContracted(View contentView) {
        try {
            Object v = XposedHelpers.getObjectField(contentView, "mContractedChild");
            return v instanceof View ? (View) v : null;
        } catch (Throwable t) {
            return null;
        }
    }

    private static View currentExpanded(View contentView) {
        try {
            Object v = XposedHelpers.getObjectField(contentView, "mExpandedChild");
            return v instanceof View ? (View) v : null;
        } catch (Throwable t) {
            return null;
        }
    }

    private static View currentHeadsUp(View contentView) {
        try {
            Object v = XposedHelpers.getObjectField(contentView, "mHeadsUpChild");
            return v instanceof View ? (View) v : null;
        } catch (Throwable t) {
            return null;
        }
    }

    private static boolean inChildrenContainer(View row) {
        ViewParent p = row.getParent();
        int depth = 0;
        while (p instanceof View && depth++ < 8) {
            String name = p.getClass().getName();
            if (name.contains("ChildrenContainer") || name.contains("NotificationChildContainer")) {
                return true;
            }
            p = ((View) p).getParent();
        }
        return false;
    }

    /**
     * 子行所在的组是否处于展开态。
     * A13 的权威信号是 `NotificationChildrenContainer.mChildrenExpanded`；
     * 摘要行的 `isExpanded()` 是"这条通知自身被点开（大图/长文）"，**不等于组展开**——
     * 用它当第一判据会把"组展开但通知未展开"的子行全部判成收起（vivo 服务那组就是这个情况，
     * 真机日志 `light.skip reason=group-collapsed`），而会话类通知自身常是展开态，于是"时有时无"。
     * 所以顺序改成：容器字段 → isGroupExpanded → isExpanded，并打出实际走了哪条。
     */
    private static boolean groupExpanded(View row) {
        try {
            ViewParent p = row.getParent();
            for (int i = 0; i < 8 && p instanceof View; i++) {
                String name = p.getClass().getName();
                if (name.contains("ChildrenContainer")
                        || name.contains("NotificationChildContainer")) {
                    try {
                        boolean exp = XposedHelpers.getBooleanField(p, "mChildrenExpanded");
                        logGroupSource(p, "mChildrenExpanded");
                        return exp;
                    } catch (Throwable ignored) {
                    }
                    Object summary = null;
                    try {
                        summary = XposedHelpers.callMethod(p, "getContainingNotification");
                    } catch (Throwable ignored) {
                    }
                    if (summary instanceof View) {
                        if (bool((View) summary, "isGroupExpanded")) {
                            logGroupSource(p, "isGroupExpanded");
                            return true;
                        }
                        if (bool((View) summary, "isExpanded")) {
                            logGroupSource(p, "isExpanded");
                            return true;
                        }
                    }
                    return false;
                }
                p = ((View) p).getParent();
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    /** 组展开判据的取证串：容器类名 + 三个候选信号的真实值 */
    private static String groupDbg(View row) {
        StringBuilder sb = new StringBuilder();
        try {
            ViewParent p = row.getParent();
            for (int i = 0; i < 8 && p instanceof View; i++) {
                String name = p.getClass().getName();
                if (name.contains("ChildrenContainer")
                        || name.contains("NotificationChildContainer")) {
                    sb.append("cont=").append(name.substring(name.lastIndexOf('.') + 1));
                    try {
                        sb.append(" mce=").append(XposedHelpers.getBooleanField(
                                p, "mChildrenExpanded"));
                    } catch (Throwable t) {
                        sb.append(" mce=ERR");
                    }
                    Object sum = null;
                    try {
                        sum = XposedHelpers.callMethod(p, "getContainingNotification");
                    } catch (Throwable ignored) {
                    }
                    if (sum instanceof View) {
                        sb.append(" sumGexp=").append(bool((View) sum, "isGroupExpanded"))
                          .append(" sumExp=").append(bool((View) sum, "isExpanded"))
                          .append(" sumChildInGrp=").append(bool((View) sum, "isChildInGroup"));
                    } else {
                        sb.append(" sum=null");
                    }
                    return sb.toString();
                }
                p = ((View) p).getParent();
            }
            sb.append("no-container");
        } catch (Throwable ignored) {
        }
        return sb.toString();
    }

    private static final java.util.Set<String> sGroupSrcLogged =
            java.util.Collections.synchronizedSet(new java.util.HashSet<String>());

    private static void logGroupSource(Object container, String src) {
        if (!sVerifyEnabled) {
            return;
        }
        if (sGroupSrcLogged.add(Integer.toHexString(System.identityHashCode(container))
                + "->" + src)) {
            Log.i(TAG, "notify_row.group.expanded src=" + src);
        }
    }

    private static ImageView iconOf(View root) {
        View v = root.findViewWithTag(TAG_ICON);
        if (!(v instanceof ImageView)) {
            v = root.findViewWithTag(TAG_LIGHT_BIG);
        }
        return v instanceof ImageView ? (ImageView) v : null;
    }

    /** 滑动删除/按下态会给行加缩放，缩放期间测量值失真；translation 是 NSS 正常堆叠定位，不能判定 */
    private static boolean settled(View v) {
        return v.getScaleX() == 1f && v.getScaleY() == 1f;
    }

    /** 展开/收起动画会给行与容器加缩放；沿父链一起确认静止，避免抓到瞬态布局值 */
    private static boolean settledTree(View v) {
        View cur = v;
        for (int i = 0; i < 5 && cur != null; i++) {
            if (!settled(cur)) {
                return false;
            }
            ViewParent parent = cur.getParent();
            cur = parent instanceof View ? (View) parent : null;
        }
        return true;
    }

    private static Drawable appIcon(Context ctx, String pkg) {
        try {
            Drawable d = ctx.getPackageManager().getApplicationIcon(pkg);
            if (d instanceof android.graphics.drawable.AdaptiveIconDrawable) {
                // 自适应图标自带安全区留白，裁边放大到接近 iOS 满幅观感
                return new ZoomDrawable(d, NotifyRowSpec.ICON_ZOOM);
            }
            return d;
        } catch (Throwable t) {
            return null;
        }
    }

    private static void hide(View v) {
        if (v != null) {
            v.setVisibility(View.GONE);
        }
    }

    private static boolean isEnabledStatic(Context ctx) {
        try {
            return com.betteroriginos.core.Prefs.featureEnabled(ctx, "notify_row", false);
        } catch (Throwable t) {
            return false;
        }
    }

    private static boolean isDebug(Context ctx) {
        try {
            return Settings.Global.getInt(ctx.getContentResolver(), DEBUG_KEY, 0) != 0;
        } catch (Throwable t) {
            return false;
        }
    }

    private static boolean isVerify(Context ctx) {
        try {
            return Settings.Global.getInt(ctx.getContentResolver(), VERIFY_KEY, 0) != 0;
        } catch (Throwable t) {
            return false;
        }
    }

    /** 供同包移植类即时读取，避免在 install 时快照到旧的开关值 */
    static boolean verifyEnabled(Context ctx) {
        return isVerify(ctx);
    }

    /** 机读校验：不依赖截图，直接断言视图的尺寸/坐标/可见性/字号/颜色/重叠 */
    /** 通知本身带不带标题：不带标题的通知"标题不显示"是正确行为，不能算缺陷 */
    private static boolean notifHasTitle(StatusBarNotification sbn) {
        try {
            Notification n = sbn == null ? null : sbn.getNotification();
            CharSequence t = (n == null || n.extras == null)
                    ? null : n.extras.getCharSequence(Notification.EXTRA_TITLE);
            return t != null && t.length() > 0;
        } catch (Throwable t) {
            return true;   // 判不出来就按"应该有标题"处理：宁可误报，不可漏报
        }
    }

    private static void scheduleVerify(final View root, final View row,
                                       final StatusBarNotification sbn, final String style,
                                       final boolean groupChild, final boolean summaryOnly,
                                       final boolean expanded, final boolean headsup,
                                       final int attempt) {
        root.postDelayed(() -> {
            try {
                // 手势/滚动的缩放与位移会污染测量值，等行真正静止再断言
                if (root.getWidth() <= 0 || root.getHeight() <= 0 || !root.isShown()
                        || !settledTree(root) || (row != null && !settledTree(row))) {
                    if (attempt < 14) {
                        // 刚滚动过的列表还在补间，8 次（≈4.6s）经常赶不上；给到 ≈10s
                        scheduleVerify(root, row, sbn, style, groupChild, summaryOnly,
                                expanded, headsup, attempt + 1);
                    } else {
                        // 放弃必须留终态记录：否则"这一行根本没被测量"会被当成通过
                        Log.w(TAG, "notify_row.verify.skipped pkg=" + sbn.getPackageName()
                                + " style=" + style
                                + " w=" + root.getWidth() + " h=" + root.getHeight()
                                + " shown=" + (root.isShown() ? 1 : 0)
                                + " settledRoot=" + (settledTree(root) ? 1 : 0)
                                + " settledRow=" + (row != null && settledTree(row) ? 1 : 0));
                    }
                    return;
                }
                synchronized (VERIFIED) {
                    if (VERIFIED.containsKey(root)) {
                        return;
                    }
                    VERIFIED.put(root, Boolean.TRUE);
                }
                verifyRow(root, row, sbn, style, summaryOnly, expanded, headsup);
            } catch (Throwable t) {
                Log.w(TAG, "notify_row.verify failed", t);
            }
        }, 600 + attempt * 500L);
    }

    private static void verifyRow(View root, View row, StatusBarNotification sbn, String style,
                                  boolean summaryOnly, boolean expanded, boolean headsup) {
        // 与 pre-draw 守卫相同的一套强制，确保断言的是最终绘制状态
        hideSmallIcons(root);
        Context ctx = root.getContext();
        View header = findClass(root, "NotificationHeaderView");
        TextView title = text(root, "title");
        TextView body = firstText(root, "text", "big_text", "always");
        TextView app = header == null ? null : text(header, "app_name_text");
        if (app == null) {
            app = text(root, "app_name_text");
        }
        TextView time = text(root, "time");
        ImageView icon = iconOf(root);
        View rightIcon = androidView(root, "right_icon");

        int[] r = loc(root, root);
        int rootW = root.getWidth();
        int rootH = root.getHeight();
        int[] il = icon == null ? null : loc(icon, root);
        int iconW = icon == null ? 0 : icon.getWidth();
        int iconH = icon == null ? 0 : icon.getHeight();
        int iconL = il == null ? -1 : il[0];
        int iconT = il == null ? -1 : il[1];
        int iconCenterDelta = il == null ? 9999 : (iconT + iconH / 2) - rootH / 2;
        int[] tl = title == null ? null : loc(title, root);
        int[] bl = body == null ? null : loc(body, root);
        int[] al = app == null ? null : loc(app, root);
        int[] tm = time == null ? null : loc(time, root);
        int[] ri = rightIcon == null || rightIcon.getVisibility() != View.VISIBLE
                ? null : loc(rightIcon, root);
        int titleX = tl == null ? -1 : tl[0];
        int titleY = tl == null ? -1 : tl[1];
        int bodyX = bl == null ? -1 : bl[0];
        int bodyY = bl == null ? -1 : bl[1];
        int appX = al == null ? -1 : al[0];
        int timeX = tm == null ? -1 : tm[0];
        int timeW = time == null ? 0 : time.getWidth();
        int titleW = title == null ? 0 : title.getWidth();
        int titleR = titleX < 0 ? -1 : titleX + titleW;
        int appW = app == null ? 0 : app.getWidth();
        int titleLines = title == null ? -1 : title.getLineCount();
        int bodyLines = body == null ? -1 : body.getLineCount();
        int timeGapRight = tm == null ? -1 : rootW - (timeX + timeW);
        int iconTitleGap = (il == null || tl == null) ? 9999
                : titleX - (iconL + iconW);
        int appTimeGap = (al == null || tm == null) ? 9999
                : timeX - (appX + appW);
        int bodyW = body == null ? 0 : body.getWidth();
        int bodyR = bodyX < 0 ? -1 : bodyX + bodyW;
        int titleGapRight = titleR < 0 ? -1 : rootW - titleR;
        int bodyGapRight = bodyR < 0 ? -1 : rootW - bodyR;
        boolean rtl = root.getLayoutDirection() == View.LAYOUT_DIRECTION_RTL;
        int iconStart = il == null ? -1 : (rtl ? rootW - (iconL + iconW) : iconL);
        int titleStart = tl == null ? -1 : (rtl ? rootW - titleR : titleX);
        int bodyStart = bl == null ? -1 : (rtl ? rootW - bodyR : bodyX);
        int appStart = al == null ? -1
                : (rtl ? rootW - (appX + appW) : appX);
        int timeEndGap = tm == null ? -1
                : (rtl ? timeX : rootW - (timeX + timeW));
        int normalizedIconTitleGap = (il == null || tl == null) ? 9999
                : (rtl ? iconL - titleR : titleX - (iconL + iconW));
        int normalizedAppTimeGap = (al == null || tm == null) ? 9999
                : (rtl ? appX - (timeX + timeW) : timeX - (appX + appW));
        int titleEndGap = tl == null ? -1 : (rtl ? titleX : rootW - titleR);
        int bodyEndGap = bl == null ? -1 : (rtl ? bodyX : rootW - bodyR);
        View timeDivider = androidView(root, "time_divider");
        int dividerVis = timeDivider == null ? -1 : timeDivider.getVisibility();
        float dividerAlpha = timeDivider == null ? -1f : timeDivider.getAlpha();
        int rowCanDismiss = -1;
        int entryClearable = -1;
        int notifFlags = -1;
        String entryParent = "null";
        int entryParentRoot = -1;
        try {
            Object can = XposedHelpers.callMethod(row, "canViewBeDismissed");
            rowCanDismiss = Boolean.TRUE.equals(can) ? 1 : 0;
            Object entry = XposedHelpers.callMethod(row, "getEntry");
            if (entry != null) {
                Object parent = XposedHelpers.callMethod(entry, "getParent");
                entryParent = parent == null ? "null" : parent.getClass().getSimpleName();
                entryParentRoot = parent != null && parent == sGroupRootEntry ? 1 : 0;
                Object clear = XposedHelpers.callMethod(entry, "isClearable");
                entryClearable = Boolean.TRUE.equals(clear) ? 1 : 0;
            }
            Notification nn = sbn.getNotification();
            notifFlags = nn == null ? -1 : nn.flags;
        } catch (Throwable ignored) {
        }
        int small = countSmall(root);
        int expand = countExpand(root);
        String timeParent = time == null || !(time.getParent() instanceof View) ? "null"
                : ((View) time.getParent()).getClass().getSimpleName();
        String rowParent = row.getParent() == null ? "null"
                : row.getParent().getClass().getSimpleName();
        boolean groupChild = bool(row, "isChildInGroup");
        boolean night = NotifyRowSpec.night(ctx);
        View contentView = root.getParent() instanceof View ? (View) root.getParent() : null;
        View contracted = contentView == null ? null : currentContracted(contentView);
        View expandedChild = contentView == null ? null : currentExpanded(contentView);
        int contentH = contentView == null ? -1 : contentView.getHeight();
        int contractedH = contracted == null ? -1 : contracted.getHeight();
        int expandedH = expandedChild == null ? -1 : expandedChild.getHeight();
        int visibleType = -1;
        try {
            visibleType = contentView == null ? -1
                    : XposedHelpers.getIntField(contentView, "mVisibleType");
        } catch (Throwable ignored) {
        }
        int rowIntrinsic = -1;
        int rowExtra = -1;
        try {
            rowIntrinsic = (Integer) XposedHelpers.callMethod(row, "getIntrinsicHeight");
            rowExtra = (Integer) XposedHelpers.callMethod(row, "getExtraHeight");
        } catch (Throwable ignored) {
        }
        int stackRows = 0;
        int stackChildContainers = 0;
        int stackW = -1;
        int cardLeft = -1;
        int cardRight = -1;
        int gapAbove = -1;
        int gapBelow = -1;
        int stackSide = -1;
        int stackGap = -1;
        int stackCorner = -1;
        if (row.getParent() instanceof ViewGroup) {
            ViewGroup stack = (ViewGroup) row.getParent();
            stackW = stack.getWidth();
            try {
                stackSide = XposedHelpers.getIntField(stack, "mSidePaddings");
                stackGap = XposedHelpers.getIntField(stack, "mPaddingBetweenElements");
                stackCorner = XposedHelpers.getIntField(stack, "mCornerRadius");
            } catch (Throwable ignored) {
            }
            cardLeft = row.getLeft();
            cardRight = stackW - row.getRight();
            int idx = stack.indexOfChild(row);
            for (int i = idx - 1; i >= 0; i--) {
                View c = stack.getChildAt(i);
                if (c.getClass().getSimpleName().equals("ExpandableNotificationRow")
                        && c.getVisibility() == View.VISIBLE) {
                    gapAbove = Math.round(row.getY() - (c.getY() + c.getHeight()));
                    break;
                }
            }
            for (int i = idx + 1; i < stack.getChildCount(); i++) {
                View c = stack.getChildAt(i);
                if (c.getClass().getSimpleName().equals("ExpandableNotificationRow")
                        && c.getVisibility() == View.VISIBLE) {
                    gapBelow = Math.round(c.getY() - (row.getY() + row.getHeight()));
                    break;
                }
            }
            for (int i = 0; i < stack.getChildCount(); i++) {
                View child = stack.getChildAt(i);
                String cn = child.getClass().getSimpleName();
                if (cn.equals("ExpandableNotificationRow")) {
                    stackRows++;
                }
                if (cn.contains("ChildrenContainer")
                        || "notification_children_container".equals(
                        simpleResName(child))) {
                    if (child.isShown()) {
                        stackChildContainers++;
                    }
                }
            }
        }

        // 量这一行的资源：root 已 attach 到 SystemUI 窗口，它的配置才是实际渲染用的配置
        // （ctx 是发通知 App 的 createPackageContext，字号改过后它可能还是旧值）
        Context dens = root != null ? root.getContext() : ctx;
        Log.i(TAG, "notify_row.verify pkg=" + sbn.getPackageName()
                + " shown=" + (root.isShown() ? 1 : 0)
                + " rowCanDismiss=" + rowCanDismiss
                + " entryClearable=" + entryClearable
                + " notifFlags=" + notifFlags
                + " entryParent=" + entryParent
                + " entryParentRoot=" + entryParentRoot
                + " rowClickable=" + (row.isClickable() ? 1 : 0)
                + " rowLongClickable=" + (row.isLongClickable() ? 1 : 0)
                + " rootTag=" + root.getTag()
                + " rootClass=" + root.getClass().getSimpleName()
                + " rootAlpha=" + root.getAlpha()
                + " guard=" + (GUARDED.containsKey(root) ? 1 : 0)
                + " contentH=" + contentH + " contractedH=" + contractedH
                + " expandedH=" + expandedH + " visibleType=" + visibleType
                + " rowIntrinsic=" + rowIntrinsic + " rowExtra=" + rowExtra
                + " stackRows=" + stackRows + " stackChildContainers=" + stackChildContainers
                + " stackW=" + stackW + " cardLeft=" + cardLeft
                + " cardRight=" + cardRight + " gapAbove=" + gapAbove
                + " gapBelow=" + gapBelow + " stackSide=" + stackSide
                + " stackGap=" + stackGap + " stackCorner=" + stackCorner
                + " style=" + style
                + " expanded=" + (expanded ? 1 : 0)
                + " summaryOnly=" + (summaryOnly ? 1 : 0)
                + " headsup=" + (headsup ? 1 : 0)
                + " stackMode=" + (sStackMode ? 1 : 0)
                + " groupChild=" + (groupChild ? 1 : 0)
                + " night=" + (night ? 1 : 0)
                // 字号/密度要按**真正渲染这一行的那套资源**报：ctx 是发通知的 App 的
                // createPackageContext，它的 Configuration 可能是改字号之前的旧值
                // （实测 fontScale 记 1 而 titleTS 已经是 1.3 倍），校验器按这个值缩放期望会算错
                + " density=" + dens.getResources().getDisplayMetrics().density
                + " fontScale=" + dens.getResources().getConfiguration().fontScale
                + " rootW=" + rootW + " rootH=" + rootH
                + " rowW=" + row.getWidth() + " rowH=" + row.getHeight()
                + " rowParent=" + rowParent
                + " headerH=" + (header == null ? -1 : header.getHeight())
                + " headerPadStart=" + (header == null ? -1 : header.getPaddingStart())
                + " headerVis=" + (header == null ? -1 : header.getVisibility())
                + " iconVis=" + (icon == null ? -1 : icon.getVisibility())
                + " iconW=" + iconW + " iconH=" + iconH
                + " iconX=" + iconL + " iconTop=" + iconT
                + " iconCenterDelta=" + iconCenterDelta
                + " iconDrawable=" + (icon != null && icon.getDrawable() != null ? 1 : 0)
                + " iconTitleGap=" + iconTitleGap
                + " rtl=" + (rtl ? 1 : 0)
                + " iconStart=" + iconStart + " titleStart=" + titleStart
                + " bodyStart=" + bodyStart + " appStart=" + appStart
                + " timeEndGap=" + timeEndGap
                + " nIconTitleGap=" + normalizedIconTitleGap
                + " nAppTimeGap=" + normalizedAppTimeGap
                + " titleEndGap=" + titleEndGap
                + " bodyEndGap=" + bodyEndGap
                + " appX=" + appX + " appW=" + appW
                + " appTS=" + (app == null ? -1 : Math.round(app.getTextSize()))
                + " appColor=" + color(app) + " appVis=" + vis(app)
                + " timeX=" + timeX + " timeW=" + timeW
                + " timeGapRight=" + timeGapRight
                + " timeTS=" + (time == null ? -1 : Math.round(time.getTextSize()))
                + " timeColor=" + color(time) + " timeVis=" + vis(time)
                + " timeParent=" + timeParent
                + " appTimeGap=" + appTimeGap
                + " titleX=" + titleX + " titleY=" + titleY
                + " titleW=" + titleW + " titleR=" + titleR
                + " titleGapRight=" + titleGapRight
                + " titleTS=" + (title == null ? -1 : Math.round(title.getTextSize()))
                + " titleColor=" + color(title) + " titleMax=" + maxLines(title)
                + " titleLines=" + titleLines + " titleVis=" + vis(title)
                + " hasTitle=" + (notifHasTitle(sbn) ? 1 : 0)
                + " bodyX=" + bodyX + " bodyY=" + bodyY
                + " bodyR=" + bodyR + " bodyGapRight=" + bodyGapRight
                + " dividerVis=" + dividerVis + " dividerAlpha=" + dividerAlpha
                + " bodyTS=" + (body == null ? -1 : Math.round(body.getTextSize()))
                + " bodyColor=" + color(body) + " bodyMax=" + maxLines(body)
                + " bodyLines=" + bodyLines + " bodyVis=" + vis(body)
                + " small=" + small + " expand=" + expand
                + " actVis=" + actField(row == null ? root : row, true)
                + " actH=" + actField(row == null ? root : row, false)
                + " rightIconVis=" + (rightIcon == null ? -1 : rightIcon.getVisibility())
                + " rightIconX=" + (ri == null ? -1 : ri[0])
                + " rightIconW=" + (rightIcon == null ? 0 : rightIcon.getWidth()));
    }

    /** 动作区现场值：wantVis 给 visibility（无动作区 -1），否则给高度（无动作区 0） */
    private static int actField(View anyRowChild, boolean wantVis) {
        try {
            View row = rowAncestor(anyRowChild);
            View acts = findActionContainer(row != null ? row : anyRowChild, 0);
            if (acts == null) {
                return wantVis ? -1 : 0;
            }
            if (wantVis) {
                return acts.getVisibility();
            }
            // verify 发生在绑定后立刻，此时可能只 measure 没 layout
            return acts.getHeight() > 0 ? acts.getHeight() : acts.getMeasuredHeight();
        } catch (Throwable t) {
            return wantVis ? -1 : 0;
        }
    }

    private static int[] loc(View v, View root) {
        int[] a = new int[2];
        int[] b = new int[2];
        v.getLocationOnScreen(a);
        root.getLocationOnScreen(b);
        return new int[]{a[0] - b[0], a[1] - b[1]};
    }

    private static int vis(View v) {
        return v == null ? -1 : v.getVisibility();
    }

    private static int maxLines(TextView v) {
        return v == null ? -1 : v.getMaxLines();
    }

    private static String color(TextView v) {
        return v == null ? "null" : "0x" + Integer.toHexString(v.getCurrentTextColor());
    }

    private static boolean visible(View v) {
        return v != null && v.isShown() && v.getAlpha() > 0.01f
                && v.getWidth() > 0 && v.getHeight() > 0;
    }

    private static int countSmall(View root) {
        return countSmallInner(root);
    }

    private static int countSmallInner(View v) {
        int count = 0;
        if (v.getTag() == TAG_ICON) {
            return 0;
        }
        String simple = v.getClass().getSimpleName();
        if (simple.equals("CachingIconView") && visible(v)) {
            count++;
        }
        if (v instanceof TextView) {
            Drawable[] ds = ((TextView) v).getCompoundDrawablesRelative();
            if ((ds[0] != null || ds[1] != null || ds[2] != null || ds[3] != null)
                    && visible(v)) {
                count++;
            }
        }
        String name = resName(v);
        if ((name.endsWith(":id/left_icon") || name.endsWith(":id/icon"))
                && visible(v)) {
            count++;
        }
        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) {
                count += countSmallInner(g.getChildAt(i));
            }
        }
        return count;
    }

    private static int countExpand(View root) {
        return countExpandInner(root);
    }

    private static int countExpandInner(View v) {
        int count = 0;
        String simple = v.getClass().getSimpleName();
        String shortName = simpleResName(v);
        if ((simple.contains("ExpandButton") || "expand_button".equals(shortName))
                && visible(v)) {
            count++;
        }
        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) {
                count += countExpandInner(g.getChildAt(i));
            }
        }
        return count;
    }

    private static String simpleResName(View v) {
        String name = resName(v);
        int slash = name.lastIndexOf('/');
        return slash < 0 ? name : name.substring(slash + 1);
    }

    private static String resName(View v) {
        try {
            if (v.getId() != 0) {
                return v.getResources().getResourceName(v.getId());
            }
        } catch (Throwable ignored) {
        }
        return "";
    }

    private static void dumpOnce(final View root) {
        synchronized (DUMPED) {
            if (DUMPED.containsKey(root)) {
                return;
            }
            DUMPED.put(root, Boolean.TRUE);
        }
        root.postDelayed(safe(() -> dump(root, 0)), 400);
    }

    private static void dump(View v, int depth) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < depth; i++) {
            sb.append("  ");
        }
        String id = "";
        try {
            if (v.getId() != 0) {
                id = v.getResources().getResourceName(v.getId());
            }
        } catch (Throwable ignored) {
        }
        sb.append(v.getClass().getSimpleName()).append(" id=").append(id)
                .append(" ").append(v.getWidth()).append("x").append(v.getHeight())
                .append(" @").append(v.getLeft()).append(',').append(v.getTop())
                .append(" pad=").append(v.getPaddingStart()).append('/')
                .append(v.getPaddingTop()).append('/').append(v.getPaddingEnd()).append('/')
                .append(v.getPaddingBottom());
        ViewGroup.LayoutParams lp = v.getLayoutParams();
        if (lp instanceof ViewGroup.MarginLayoutParams) {
            ViewGroup.MarginLayoutParams m = (ViewGroup.MarginLayoutParams) lp;
            sb.append(" mar=").append(m.getMarginStart()).append('/').append(m.topMargin)
                    .append('/').append(m.getMarginEnd()).append('/').append(m.bottomMargin);
        }
        if (v.getVisibility() != View.VISIBLE) {
            sb.append(" vis=").append(v.getVisibility());
        }
        if (v instanceof TextView) {
            TextView t = (TextView) v;
            sb.append(" text=").append(t.getText()).append(" ts=").append(t.getTextSize());
            Drawable[] cds = t.getCompoundDrawablesRelative();
            if (cds[0] != null || cds[1] != null || cds[2] != null || cds[3] != null) {
                sb.append(" cd=1");
            }
        }
        if (v instanceof ImageView) {
            sb.append(" drawable=").append(((ImageView) v).getDrawable() != null);
        }
        Log.i(TAG, "notify_row.tree " + sb);
        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            int n = Math.min(g.getChildCount(), 16);
            for (int i = 0; i < n; i++) {
                dump(g.getChildAt(i), depth + 1);
            }
        }
    }

    private static StatusBarNotification sbnOf(View row) {
        try {
            Object entry = XposedHelpers.callMethod(row, "getEntry");
            if (entry == null) {
                return null;
            }
            Object sbn = XposedHelpers.callMethod(entry, "getSbn");
            return sbn instanceof StatusBarNotification ? (StatusBarNotification) sbn : null;
        } catch (Throwable t) {
            return null;
        }
    }

    private static boolean bool(View v, String method) {
        try {
            Object r = XposedHelpers.callMethod(v, method);
            return Boolean.TRUE.equals(r);
        } catch (Throwable t) {
            return false;
        }
    }

    private static View rowOf(View contentView) {
        try {
            Object row = XposedHelpers.getObjectField(contentView, "mContainingNotification");
            if (row instanceof View) {
                return (View) row;
            }
        } catch (Throwable ignored) {
        }
        ViewParent p = contentView.getParent();
        return p instanceof View ? (View) p : null;
    }

    private static View findClass(View v, String simpleName) {
        if (v.getClass().getSimpleName().equals(simpleName)) {
            return v;
        }
        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) {
                View found = findClass(g.getChildAt(i), simpleName);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private static int id(Context ctx, String name) {
        try {
            return ctx.getResources().getIdentifier(name, "id", "android");
        } catch (Throwable t) {
            return 0;
        }
    }

    private static View androidView(View root, String name) {
        int id = id(root.getContext(), name);
        return id == 0 ? null : root.findViewById(id);
    }

    private static TextView text(View root, String name) {
        View v = androidView(root, name);
        return v instanceof TextView ? (TextView) v : null;
    }

    private static TextView firstText(View root, String... names) {
        for (String n : names) {
            TextView t = text(root, n);
            if (t != null) {
                return t;
            }
        }
        return null;
    }

    private static View directChildOf(View root, View descendant) {
        View v = descendant;
        while (v != null) {
            ViewParent p = v.getParent();
            if (p == root) {
                return v;
            }
            if (!(p instanceof View)) {
                return null;
            }
            v = (View) p;
        }
        return null;
    }

    private static int startOffset(View root, View target) {
        int sum = root.getPaddingStart();
        View v = target;
        while (v != null && v != root) {
            ViewParent p = v.getParent();
            if (!(p instanceof ViewGroup)) {
                break;
            }
            ViewGroup parent = (ViewGroup) p;
            sum += parent.getPaddingStart();
            ViewGroup.LayoutParams lp = v.getLayoutParams();
            if (lp instanceof ViewGroup.MarginLayoutParams) {
                sum += ((ViewGroup.MarginLayoutParams) lp).getMarginStart();
            }
            v = parent;
        }
        return sum;
    }

    private static int endOffset(View root, View target) {
        int sum = root.getPaddingEnd();
        View v = target;
        while (v != null && v != root) {
            ViewParent p = v.getParent();
            if (!(p instanceof ViewGroup)) {
                break;
            }
            ViewGroup parent = (ViewGroup) p;
            sum += parent.getPaddingEnd();
            ViewGroup.LayoutParams lp = v.getLayoutParams();
            if (lp instanceof ViewGroup.MarginLayoutParams) {
                sum += ((ViewGroup.MarginLayoutParams) lp).getMarginEnd();
            }
            v = parent;
        }
        return sum;
    }

}
