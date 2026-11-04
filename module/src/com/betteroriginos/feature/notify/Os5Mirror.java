package com.betteroriginos.feature.notify;

import android.content.Context;
import android.graphics.BlurMaskFilter;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.Log;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import com.betteroriginos.R;
import com.betteroriginos.core.OsProfile;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

/**
 * 把 OriginOS 5（A15）的通知观感反向移植到 OriginOS 3（A13）。
 * 数值与形状逐条来自 out/LOG-1102-OS5-TO-OS3-ADAPT.md §六 的证据表；
 * 每条都注明了它在 A13 上的落点（同一个类、同一个方法签名）。
 */
public final class Os5Mirror {

    private Os5Mirror() {
    }

    private static final String TAG = "BetterOriginOS";

    // ---- OS5 规格（dp/ms/色值均为 A15 侧真值） ----
    static final float RADIUS_SHARP_DP = 12f;     // level 0 -> notification_shadow_radius_sharp
    static final float RADIUS_BASE_DP = 18f;      // level 1 -> notification_shadow_radius
    static final float RADIUS_ROUND_DP = 24f;     // level 2/3 -> _smooth / _mellow
    /** A15 的条目间距 dimen（vivo_notification_paddding_between_elements，vivo 原始拼写就是三个 d）：
     *  NSSL.initView 与 NotificationShelf 都用它，所以列表间距与收纳栏间距是同一个值。 */
    static final int GAP_BETWEEN_DP = 7;
    static final float CLEAR_SIZE_DP = 46f;       // vivo_notification_clear_all_size
    static final float CLEAR_PAD_DP = 2f;         // vivo_notification_clear_all_padding
    static final float CLEAR_MARGIN_DP = 40f;     // 无导航栏
    static final float CLEAR_MARGIN_NAV_DP = 62f; // 有导航栏

    /** A15 有、A13 无（三个 dex 全 0）：系统自带就别再补挂一枚。 */
    static final String NATIVE_CLEAR_ALL_BUTTON =
            "com.vivo.systemui.statusbar.ui.NotificationClearAllImageView";
    static final int CLEAR_SHADOW = 0x1AEEEEEE;   // color/vivo_notification_clear_all_shadow
    static final float CLEAR_BLUR = 45f;          // BlurMaskFilter(45f, OUTER)
    static final long PRESS_MS = 200L;            // 按下缩放 1.0->0.9
    static final long APPEAR_MS = 150L;           // 出现/消失 alpha
    /**
     * OS5 的 NotificationClearAllImageView 四处动画（按下/抬起的缩放、alpha 出入）
     * 用的是同一条 PathInterpolator(0.25,0.45,0.3,1.0)（OS5-NotificationClearAllImageView.java:69/106/142/162）。
     * 只设时长不设它，观感就是"线性缩放"，OS5 那种先快后缓的收尾没了。
     */
    static final android.view.animation.Interpolator OS5_EASE =
            new android.view.animation.PathInterpolator(0.25f, 0.45f, 0.3f, 1.0f);

    private static volatile boolean sEnabled;
    private static volatile ClassLoader sLoader;
    /** 系统是否自带浮动清除钮（装钩子时按类存在性判一次，缓存复用）。 */
    private static volatile Boolean sNativeClearAll;
    private static int sLevel = -1;   // -1 = 还没读到过
    private static int sAppliedRadius;
    private static int sAppliedPath;
    private static int sShelfMirrors;
    private static int sClearAttaches;
    private static int sBgClips;
    private static boolean sDismissAllowed;
    /**
     * 系统正在跑 NotificationShelf.onConfigurationChanged 的标记。
     * 那方法末尾会 `setDismissVisible(内联按钮.getVisibility()==0)`，
     * 而我们把内联按钮按成 GONE ⇒ 这句会传来一个"假 false"。
     * 期间不采信参数，配置变更结束后用 sDismissAllowed 重算一次。
     */
    private static boolean sInConfigChange;
    private static java.lang.ref.WeakReference<View> sShelfRef;
    private static boolean sLandingChecked;
    private static android.os.Handler sHandler;

    /** 惰性创建：静态初始化里碰 Looper 会让类在离线字节码验证环境下加载失败 */
    private static android.os.Handler handler() {
        if (sHandler == null) {
            sHandler = new android.os.Handler(android.os.Looper.getMainLooper());
        }
        return sHandler;
    }

    /** 度量日志的开关联到 Settings.Global，避免 install 时快照到旧值 */
    private static boolean verify(Context ctx) {
        try {
            return ctx != null && NotifyRowFeature.verifyEnabled(ctx);
        } catch (Throwable t) {
            return false;
        }
    }

    /** @return 装上的钩子组数；每组的实际命中方法数单独记日志（0 命中=静默失败，必须可见） */
    public static int install(ClassLoader cl) {
        int installed = 0;
        // 必须用 SystemUI 自己的 ClassLoader：Context 的实现类（ContextImpl / ContextThemeWrapper）
        // 是 framework 侧加载的，`context.getClass().getClassLoader()` 拿到的是 BootClassLoader，
        // 看不见 vivo 的 VivoColorAndCornerPicker/Dependency —— 之前档位因此永远读不到，
        // 圆角退成兜底 18dp，而 verify 关着的真机用户连"日志那次视图 loader 探测"都没有。
        sLoader = cl;
        installed += hookOutlineRadius(cl);
        installed += hookG2Path(cl);
        installed += hookShelf(cl);
        installed += hookBackgroundShape(cl);
        Log.i(TAG, "os5_mirror: install groups=" + installed + " " + sHookHits);
        return installed;
    }

    private static final StringBuilder sHookHits = new StringBuilder();

    private static int record(String name, int n) {
        if (sHookHits.length() > 0) {
            sHookHits.append(' ');
        }
        sHookHits.append(name).append('=').append(n);
        if (n == 0) {
            Log.w(TAG, "os5_mirror: hook " + name + " matched ZERO methods"
                    + " (该系统的类里没有这个方法声明，钩子静默失效)");
        }
        return n;
    }

    /** 由 notify_row 的样式开关驱动；关闭时形状与半径都回到系统自身行为。 */
    /** 只读见证用：当前是否在用 A15 档位半径；不参与任何行为判断。 */
    static boolean enabled() {
        return sEnabled;
    }

    public static void setEnabled(boolean enabled) {
        if (sEnabled != enabled) {
            sEnabled = enabled;
            Log.i(TAG, "os5_mirror: enabled=" + enabled + " a13=" + OsProfile.isA13OrLower());
        }
        // 只要当前是开着的就确保自检在排（scheduleLandingCheck 自己有 sLandingChecked 去重）。
        // 之前只在"跳变"时排：A13 真机上出现过 enabled=true 已记录、但 2 分钟内
        // 一条 landing 都没有的情况，靠跳变排不出第二次机会，就再也无法区分
        // "没排上"和"排上了但没跑"。
        if (enabled) {
            scheduleLandingCheck();
        }
    }

    /**
     * 延后自检：钩子装上 != 有落点。开启后每 5s 看一次，
     * 一旦几何真的被改过就报 reachable 并收工；连续 LOOK_TICKS 次没有任何改动才报 no-entry。
     * 一次"开启"只出一个结论，避免刷屏；下次再开会继续自检。
     * post 里的异常会带走 SystemUI 主线程，所以整体包 try。
     */
    private static final int LOOK_TICKS = 24;          // 最多观察 2 分钟
    private static final long LOOK_INTERVAL_MS = 5000L;

    private static void scheduleLandingCheck() {
        if (sLandingChecked) {
            return;
        }
        sLandingChecked = true;
        // 这条见证是为了把"没排上"和"排上了但没跑"分开：A13 上出现过
        // enabled=true 已记录、2 分钟内却没有 landing 结论，两种成因在日志里长一样。
        Log.i(TAG, "os5_mirror: landing check scheduled ticks=" + LOOK_TICKS);
        try {
            handler().postDelayed(new Runnable() {
                private int ticks;

                @Override
                public void run() {
                    try {
                        if (ticks == 0) {
                            // 一次性见证：把"这条链根本没跑"和"跑了但判据没命中"分开。
                            // A13 真机两轮（17:58/18:14）都是 scheduled 有、verdict 无，
                            // 光看日志分不清断在 postDelayed 之前还是在判据里。
                            Log.i(TAG, "os5_mirror landing tick#1 radius=" + sAppliedRadius
                                    + " g2=" + sAppliedPath + " shelf=" + sShelfMirrors
                                    + " clear=" + sClearAttaches + " enabled=" + sEnabled);
                        }
                        boolean hit = sAppliedPath > 0 && sShelfMirrors > 0;
                        if (hit) {
                            verdict("reachable");
                            sLandingChecked = false;
                            return;
                        }
                        if (!sEnabled) {
                            // 静默退出是 A13 上"只有 scheduled、没有任何 verdict"的真凶：
                            // sep 在重启期间会被读到一次 false，这条链就永久消失了，而
                            // sLandingChecked 还留着 true，下次 enabled=true 也排不出第二次。
                            // 现在：报一行中断原因，并把去重标记放回，让下次跳变能重排。
                            sLandingChecked = false;
                            Log.i(TAG, "os5_mirror landing interrupted ticks=" + ticks
                                    + " radius=" + sAppliedRadius + " g2=" + sAppliedPath
                                    + " shelf=" + sShelfMirrors + " clear=" + sClearAttaches);
                            return;
                        }
                        if (++ticks >= LOOK_TICKS) {
                            verdict("no-entry");
                            sLandingChecked = false;
                            return;
                        }
                        handler().postDelayed(this, LOOK_INTERVAL_MS);
                    } catch (Throwable t) {
                        Log.w(TAG, "os5_mirror: landing check failed", t);
                    }
                }

                private void verdict(String state) {
                    Log.i(TAG, "os5_mirror landing " + state
                            + " radius=" + sAppliedRadius + " g2=" + sAppliedPath
                            + " shelf=" + sShelfMirrors + " clear=" + sClearAttaches);
                }
            }, LOOK_INTERVAL_MS);
        } catch (Throwable t) {
            sLandingChecked = false;
            // 这里以前静默：排不出自检和自检跑了但没结论，在日志里长得一模一样。
            Log.w(TAG, "os5_mirror: landing check schedule failed", t);
        }
    }

    // ---------------------------------------------------------------- 半径档位

    /** A13 与 A15 都走 ExpandableOutlineView.initDimens()，只是取值来源不同：这里换成 A15 的档位表。
     *  onSystemFilletChanged 会被主题回调重新写回原生 fillet，所以两处都要拦。 */
    private static int hookOutlineRadius(ClassLoader cl) {
        try {
            Class<?> outline = XposedHelpers.findClass(
                    "com.android.systemui.statusbar.notification.row.ExpandableOutlineView", cl);
            XC_MethodHook apply = new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    applyRadius(param.thisObject);
                }
            };
            record("initDimens", XposedBridge.hookAllMethods(outline, "initDimens", apply).size());
            // 构造函数里那次 initDimens 只能写字段（见 applyRadius 的注释），
            // 背景视图在 onFinishInflate 才出现 —— 这里补一次，圆角才真的画出来。
            // 钩 ActivatableNotificationView 自己声明的那一条（OS3 jadx:472 赋 mBackgroundNormal），
            // 子类的 override 都会 super.onFinishInflate() 走到它；
            // hookAllMethods 只匹配**该类声明**的方法，所以必须按这个类钩而不是基类。
            // 独立 try：这个类万一名义上被挪走，也不能把后面的 fillet 钩子一起带走。
            try {
                Class<?> activatable = XposedHelpers.findClass(
                        "com.android.systemui.statusbar.notification.row.ActivatableNotificationView",
                        cl);
                record("onFinishInflate",
                        XposedBridge.hookAllMethods(activatable, "onFinishInflate", apply).size());
            } catch (Throwable t) {
                record("onFinishInflate", 0);
                Log.w(TAG, "os5_mirror: inflate 补挂失败（圆角只写字段，不会应用到背景）", t);
            }
            XC_MethodHook fillet = new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    sLevel = -1;          // 档位可能变了，下次重读
                    applyRadius(param.thisObject);
                }
            };
            record("fillet", XposedBridge.hookAllMethods(outline, "onSystemFilletChanged", fillet).size());
            return 1;
        } catch (Throwable t) {
            Log.w(TAG, "os5_mirror: radius hook failed", t);
            return 0;
        }
    }

    private static void applyRadius(Object view) {
        if (!sEnabled || !(view instanceof View)) {
            return;
        }
        try {
            Context vc = ((View) view).getContext();
            float px = radiusPx(vc);
            XposedHelpers.setFloatField(view, "mOutlineRadius", px);
            // applyRoundness 会走到 ActivatableNotificationView.applyBackgroundRoundness
            // （`mBackgroundNormal.setRadius(f, f2)`，OS3 jadx:86-88），而 mBackgroundNormal
            // 要等 onFinishInflate 里 findViewById 才有值（:472-474）；initDimens 是**构造函数**
            // 里调的（ExpandableOutlineView.<init>:103）—— 那一刻它还是 null，一调就 NPE。
            // 真机第一次跑就是这样：22 条 "radius write failed"，档位见证一条都没出。
            // 所以背景没就绪时只写半径字段（G2 路径与 bind 都读它），延后到 inflate 完成再补。
            if (backgroundReady(view)) {
                XposedHelpers.callMethod(view, "applyRoundness");
            } else {
                sRadiusDeferred++;
            }
            sAppliedRadius++;
            if (verify(vc) && sAppliedRadius <= 3) {
                Log.i(TAG, "os5_mirror: radius level=" + level() + " px=" + px
                        + " cls=" + view.getClass().getSimpleName()
                        + " deferred=" + sRadiusDeferred);
            }
        } catch (Throwable t) {
            Log.w(TAG, "os5_mirror: radius write failed", t);
        }
    }

    /** 延后过几次（背景未就绪）—— 只在第一次挂载日志里当见证用，不是行为开关。 */
    private static int sRadiusDeferred;

    /**
     * 背景视图是否已经存在。
     * 没有 `mBackgroundNormal` 这个字段的类（基类 ExpandableOutlineView 的 applyRoundness
     * 只是 invalidateOutline + invalidate）返回 true：它本来就安全。
     */
    private static boolean backgroundReady(Object view) {
        try {
            return XposedHelpers.getObjectField(view, "mBackgroundNormal") != null;
        } catch (Throwable t) {
            return true;
        }
    }

    /**
     * 档位由 A13 自己的 VivoColorAndCornerPicker.getFilletLevel() 提供（与 A15 同一份语义）。
     * 只用 install() 记下的 SystemUI ClassLoader（见 sLoader 的注释）：
     * 传 Context 进来取 loader 是这条链之前失效的原因。
     */
    private static int level() {
        if (sLevel >= 0) {
            return sLevel;
        }
        ClassLoader cl = sLoader != null ? sLoader : Os5Mirror.class.getClassLoader();
        try {
            Class<?> picker = XposedHelpers.findClass(
                    "com.vivo.systemui.statusbar.utils.VivoColorAndCornerPicker", cl);
            Object inst = null;
            for (String dep : new String[]{"com.android.systemui.Dependency",
                    "com.android.systemui.dependencies.Dependency"}) {
                try {
                    inst = XposedHelpers.callStaticMethod(
                            XposedHelpers.findClass(dep, cl),
                            "get", picker);
                    break;
                } catch (Throwable ignored) {
                }
            }
            if (inst != null) {
                Object v = XposedHelpers.callMethod(inst, "getFilletLevel");
                if (v instanceof Integer) {
                    // 只有真读到才缓存：启动早期 Dependency 可能还没装好，
                    // 把兜底值缓存住就等于永远停在 18dp，主题换档也不会再跟
                    sLevel = ((Integer) v).intValue();
                }
            }
        } catch (Throwable ignored) {
        }
        return sLevel >= 0 ? sLevel : 1;
    }

    private static float radiusPx(Context ctx) {
        int l = level();
        float dp;
        if (l == 0) {
            dp = RADIUS_SHARP_DP;
        } else if (l == 2 || l == 3) {
            dp = RADIUS_ROUND_DP;
        } else {
            dp = RADIUS_BASE_DP;
        }
        return NotifyRowSpec.dp(ctx, dp);
    }

    // ---------------------------------------------------------------- 连续角形状

    /** A13 的行轮廓是自己 addRoundRect 画出来的；把它换成 vivo 的 G2 路径，几何与 A15 默认分支一致。 */
    private static int hookG2Path(ClassLoader cl) {
        try {
            Class<?> outline = XposedHelpers.findClass(
                    "com.android.systemui.statusbar.notification.row.ExpandableOutlineView", cl);
            XC_MethodHook g2 = new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    Object[] a = param.args;
                    if (!sEnabled || a == null || a.length < 7 || !(a[6] instanceof Path)) {
                        return;
                    }
                    try {
                        View self = (View) param.thisObject;
                        Context vc = self.getContext();
                        float top = ((Float) a[4]).floatValue();
                        float bottom = ((Float) a[5]).floatValue();
                        // 原生半径 = 系数 * mOutlineRadius。选项可能在行构造之后才打开，
                        // 而 initDimens 只在构造与主题/字号变化时再跑 —— 这里按当前目标值自我纠正，
                        // 半径就永远不会停留在旧基线上（系数保持原生值不变）。
                        float current = 0f;
                        try {
                            current = XposedHelpers.getFloatField(self, "mOutlineRadius");
                        } catch (Throwable ignored) {
                        }
                        float[] fixed = Os5Geometry.rescaleRadii(current, radiusPx(vc), top, bottom);
                        if (fixed[2] > 0f) {
                            XposedHelpers.setFloatField(self, "mOutlineRadius", fixed[2]);
                        }
                        top = fixed[0];
                        bottom = fixed[1];
                        Path p = (Path) a[6];
                        p.reset();
                        Os5Geometry.g2Path(p, ((Integer) a[0]).intValue(), ((Integer) a[1]).intValue(),
                                ((Integer) a[2]).intValue(), ((Integer) a[3]).intValue(),
                                Math.max(top, bottom), top > 0f, top > 0f, bottom > 0f,
                                bottom > 0f);
                        sAppliedPath++;
                        if (verify(vc) && sAppliedPath <= 3) {
                            Log.i(TAG, "os5_mirror: g2 path l=" + a[0] + " t=" + a[1]
                                    + " r=" + a[2] + " b=" + a[3] + " top=" + top
                                    + " bottom=" + bottom);
                        }
                    } catch (Throwable ignored) {
                    }
                }
            };
            record("getRoundedRectPath", XposedBridge.hookAllMethods(
                    outline, "getRoundedRectPath", g2).size());
            return 1;
        } catch (Throwable t) {
            Log.w(TAG, "os5_mirror: g2 hook failed", t);
            return 0;
        }
    }

    /**
     * A13 的卡片可见圆角来自 mBackground（GradientDrawable）自己的 radii，
     * 只改 ExpandableOutlineView 的裁剪路径不够 —— 底色仍会是直角矩形。
     * 这里在背景 View 绘制前按同一份 mCornerRadii 把画布裁成 G2 形状：
     * 不替换 drawable，免得打断它的动画/状态（verifyDrawable 比较的就是原对象）。
     */
    private static int hookBackgroundShape(ClassLoader cl) {
        try {
            Class<?> bg = XposedHelpers.findClass(
                    "com.android.systemui.statusbar.notification.row.NotificationBackgroundView", cl);
            XC_MethodHook clip = new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    clipToG2((android.graphics.Canvas) param.args[0], (View) param.thisObject);
                }
            };
            return record("bg.onDraw", XposedBridge.hookAllMethods(bg, "onDraw", clip).size());
        } catch (Throwable t) {
            Log.w(TAG, "os5_mirror: background shape hook failed", t);
            return 0;
        }
    }

    private static void clipToG2(android.graphics.Canvas canvas, View bg) {
        if (!sEnabled || canvas == null || bg == null) {
            return;
        }
        try {
            Object radiiObj = XposedHelpers.getObjectField(bg, "mCornerRadii");
            if (!(radiiObj instanceof float[])) {
                skipClip("no-radii-field", 0f);
                return;
            }
            float[] radii = (float[]) radiiObj;
            float r = 0f;
            for (float v : radii) {
                r = Math.max(r, v);
            }
            if (r <= 0.5f) {
                // 系统自己没给这枚背景圆角（mCornerRadii 由
                // ActivatableNotificationView.applyRoundness -> applyBackgroundRoundness
                //   (getCurrentBackgroundRadiusTop(), ...) 写入，而那是
                //   mCurrentTopRoundness * mOutlineRadius，OS3 jadx:237-238/308-310）。
                // 半径为 0 时无角可复刻，是原生行为而不是我们的缺陷 —— 但必须看得见。
                skipClip("radii-zero", r);
                return;
            }
            Object d = XposedHelpers.getObjectField(bg, "mBackground");
            android.graphics.Rect stale = d instanceof android.graphics.drawable.Drawable
                    ? ((android.graphics.drawable.Drawable) d).getBounds() : null;
            // before-hook 里 drawable 的 bounds 还是**上一帧**的（setBounds 发生在 onDraw 内部，
            // OS3-NotificationBackgroundView.java:65-92），所以展开/收起、RTL、宽度过渡那几帧
            // 会裁到旧矩形上。这里按系统自己那套公式先算出本帧矩形；算不出来才退回旧 bounds。
            android.graphics.Rect b = currentBackgroundRect(bg);
            if (b == null) {
                b = stale;
            }
            if (b == null || b.width() <= 0 || b.height() <= 0) {
                skipClip("no-rect", r);
                return;
            }
            android.graphics.Path path = new android.graphics.Path();
            Os5Geometry.g2Path(path, b.left, b.top, b.right, b.bottom, r,
                    radii[0] > 0f, radii[2] > 0f, radii[4] > 0f, radii[6] > 0f);
            canvas.clipPath(path);
            sBgClips++;
            if (verify(bg.getContext()) && sBgClips <= 3) {
                // bounds 打**四个数字**而不是 b.toShortString()：A13 真机上它输出
                // `[0,0][960,279]`，不是我按 AOSP 印象写的 `(0, 0 - 960, 279)`，
                // 验证器的正则因此整条解析不到（"有 1 行但只解析出 0 条"）。
                // 别把断言建在平台格式化字符串上。
                Log.i(TAG, "os5_mirror: bg clip g2 r=" + r + " bounds="
                        + b.left + "," + b.top + "," + b.right + "," + b.bottom
                        + " lt=" + (radii[0] > 0f) + " rt=" + (radii[2] > 0f)
                        + " rb=" + (radii[4] > 0f) + " lb=" + (radii[6] > 0f)
                        // stale=1：这一帧用的还是上一帧的矩形（算不出来才退回），
                        // 稳态下应该是 0；一直是 1 说明几何字段名漂了
                        + " stale=" + (b.equals(stale) ? 0 : 1));
            }
        } catch (Throwable t) {
            // 背景绘制是每帧路径，异常必须留在这里，不能带走主线程
            skipClip("throw:" + t.getClass().getSimpleName(), 0f);
        }
    }

    /** 背景裁剪没发生的次数与见过的最大半径：只作见证，不参与任何行为判断。 */
    private static int sBgSkips;
    private static float sBgSkipMaxR;

    /**
     * 为什么这一帧没做 G2 裁剪。没有这条见证时，"字段名漂了""系统本来就没给圆角"
     * "矩形算不出来"三种情况在日志里长得一模一样（都是没有 bg clip 行），
     * A13 真机第一次跑就是这样卡住的。最多打 3 条，避免每帧刷。
     */
    private static void skipClip(String reason, float r) {
        sBgSkips++;
        sBgSkipMaxR = Math.max(sBgSkipMaxR, r);
        if (sBgSkips <= 3) {
            Log.i(TAG, "os5_mirror: bg clip skipped reason=" + reason
                    + " n=" + sBgSkips + " maxR=" + sBgSkipMaxR);
        }
    }

    /** 复刻 A13 `NotificationBackgroundView.draw(Canvas, Drawable)` 本帧要用的矩形。 */
    private static android.graphics.Rect currentBackgroundRect(View bg) {
        try {
            int top = XposedHelpers.getIntField(bg, "mBackgroundTop");
            int height = ((Integer) XposedHelpers.callMethod(bg, "getActualHeight")).intValue();
            boolean bottomRounded = XposedHelpers.getBooleanField(bg, "mBottomIsRounded");
            boolean bottomClips = XposedHelpers.getBooleanField(bg, "mBottomAmountClips");
            boolean animating = XposedHelpers.getBooleanField(bg, "mExpandAnimationRunning");
            if (bottomRounded && bottomClips && !animating) {
                height -= XposedHelpers.getIntField(bg, "mClipBottomAmount");
            }
            int w = bg.getWidth();
            int aw = ((Integer) XposedHelpers.callMethod(bg, "getActualWidth")).intValue();
            int left;
            int right;
            if (animating) {
                left = (int) ((w - aw) / 2.0f);
                right = left + aw;
            } else if (bg.getLayoutDirection() == View.LAYOUT_DIRECTION_RTL) {
                left = w - aw;
                right = w;
            } else {
                left = 0;
                right = aw;
            }
            if (XposedHelpers.getBooleanField(bg, "mTopAmountRounded")) {
                int delta = (int) (XposedHelpers.getIntField(bg, "mClipTopAmount")
                        - XposedHelpers.getFloatField(bg, "mDistanceToTopRoundness"));
                top += delta;
                if (delta >= 0) {
                    height += delta;
                }
            }
            if (right <= left || height <= top) {
                return null;
            }
            return new android.graphics.Rect(left, top, right, height);
        } catch (Throwable t) {
            return null;
        }
    }

    // ---------------------------------------------------------------- 收纳栏

    private static int hookShelf(ClassLoader cl) {
        int n = 0;
        try {
            Class<?> shelf = XposedHelpers.findClass(
                    "com.android.systemui.statusbar.NotificationShelf", cl);
            // 判"系统有没有自己的浮动清除钮"要在装钩子时就做，不要等 shelf 事件：
            // 否则在 OS5 上整段不干活时日志里一条证据都没有，验收器只能猜。
            sNativeClearAll = Boolean.valueOf(XposedHelpers.findClassIfExists(
                    NATIVE_CLEAR_ALL_BUTTON, cl) != null);
            Log.i(TAG, "os5_mirror: clear-all native=" + sNativeClearAll);
            XC_MethodHook round = new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    mirrorShelf((View) param.thisObject);
                }
            };
            n += record("shelf.onFinishInflate", XposedBridge.hookAllMethods(
                    shelf, "onFinishInflate", round).size());
            n += record("shelf.bind", XposedBridge.hookAllMethods(shelf, "bind", round).size());
            // 间距：A13 的 shelf 位移就是这里算出来的（见 applyShelfGap 的注释）。
            n += record("shelf.updateAppearance", XposedBridge.hookAllMethods(
                    shelf, "updateAppearance", new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            applyShelfGap((View) param.thisObject);
                        }
                    }).size());
            // 收纳栏可见性是系统自己算的（updateAppearance -> changeShelfVisibility，
            // 清除后 animateShelfDisappear 结束也会置 INVISIBLE）。浮钮只被 setDismissVisible
            // 驱动的话，一键清除把栏清空后如果系统没再回调那个方法，钮会留在空栏上
            // （OS5 的钮可见性由 NSSL.setClearAllButtonVisibility 自己重算，A13 没这个方法）。
            // 这里读的是系统刚写下的 shelf 可见性，不是我们写的，所以不构成 §8 那种自噬回路。
            n += record("shelf.changeShelfVisibility", XposedBridge.hookAllMethods(
                    shelf, "changeShelfVisibility", new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            ClearAll.reEvaluate((View) param.thisObject);
                        }
                    }).size());
            // 转屏/改字号会走 onConfigurationChanged -> initDimens()：它把 mPaddingBetweenElements
            // 复位成 0、重设 shelf 高度/图标内边距/分隔线色（OS3-NotificationShelf.java:158-175），
            // 所以我们补的 7dp 间距与顶部圆角都必须在这之后重下一遍。
            // 更要紧的是：同一方法末尾有
            //     setDismissVisible(mDismissAllNotification.getVisibility() == 0)
            // 而我们把那个内联文字按钮按成了 GONE —— 这句会**误读成"系统不允许清除"**，
            // 把浮动按钮一并永久藏掉。所以这期间把参数当不可信，事后用我们自己记住的状态重算。
            n += record("shelf.onConfigurationChanged", XposedBridge.hookAllMethods(
                    shelf, "onConfigurationChanged", new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            sInConfigChange = true;
                        }

                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            sInConfigChange = false;
                            View shelfView = (View) param.thisObject;
                            mirrorShelf(shelfView);
                            ClearAll.onShelfDismissVisible(shelfView, clearAllShouldShow(shelfView));
                        }
                    }).size());
            int d = XposedBridge.hookAllMethods(shelf, "setDismissVisible", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    if (sInConfigChange) {
                        return;     // 自造的 false，不代表系统意图
                    }
                    boolean show = param.args != null && param.args.length > 0
                            && Boolean.TRUE.equals(param.args[0]);
                    sDismissAllowed = show;
                    sShelfRef = new java.lang.ref.WeakReference<>((View) param.thisObject);
                    ClearAll.onShelfDismissVisible((View) param.thisObject,
                            clearAllShouldShow((View) param.thisObject));
                }
            }).size();
            // 先把 shelf 自己那条注册记下来，再去找 NSSL：
            // 之前 record("shelf.setDismissVisible") 排在 NSSL 的 findClass 之后且共用一个 try，
            // 那个类一旦被混淆/挪包就会抛错，连带把已经装好的注册计数一起吞掉
            // （日志里只剩"hook failed"，看不出到底哪几组还在）。
            record("shelf.setDismissVisible", d);
            n += d;
            // 撤掉一处我自己没验证的"修复"：曾想钩 changeShelfVisibility(z) 把入参改成
            // `z && !sDismissAllowed`。但它的唯一调用点在 updateAppearance() 里，而那个方法
            // jadx 直接跳过（701 指令单位，"Method not decompiled"）⇒ z 的语义我并没有读到，
            // 而且 sDismissAllowed 在复刻刚开启时可能还没被捕获过（默认 false）→ 判据退化，
            // 真机出现过滑完收纳栏整条不在、切一次 sep 才回来的卡死。宁可不改。
            try {
                // 钩哪些方法由 OS5 的重算点决定，不是猜的：A15 `setIsExpanded` 只在状态真的翻转时
                // 调 `setClearAllButtonVisibility()`（A15 jadx:4228-4233），`setQsFullScreen`
                // 无条件调（:4828-4844）。所以 A13 侧对应挂这两个，并且**照抄"值没变就不重算"**：
                // setExpandedHeight 每帧都会调 setIsExpanded(f>0)，不门控就是在拖拽的每一帧
                // 走一遍父链 + 两三次反射调用（全在主线程）。
                // 更正一条我先前写下的假命题：A15 的 NSSL **没有** `onExpandedChanged`
                // （A15/A13 两份 jadx 全文检索各 0 命中，我原来那句"A15 有、A13 没有"是把
                // 别的类的名字安到了这里）；旧代码先试它、命中 0 再退到 setIsExpanded，
                // 结果"正确"但绕了一圈，还让 os3-probe 报告里长期挂着一条误导性的 ⚠️。
                // 注意：类名必须保持**一整条字面量**。拆成两段拼接后，
                // work/os3-probe.py 就扫不到它 —— 检查器瞎了比红更糟。
                Class<?> nssl = XposedHelpers.findClass(
                        "com.android.systemui.statusbar.notification.stack.NotificationStackScrollLayout",
                        cl);
                int e = XposedBridge.hookAllMethods(nssl, "setIsExpanded",
                        gatedRecheck(sLastExpanded)).size();
                int e2 = XposedBridge.hookAllMethods(nssl, "setQsFullScreen",
                        gatedRecheck(sLastQs)).size();
                record("nssl.setIsExpanded", e);
                record("nssl.setQsFullScreen", e2);
                n += e + e2;
            } catch (Throwable t) {
                record("nssl.expandedHook", 0);
                Log.w(TAG, "os5_mirror: NSSL 展开态钩子没装上（浮动按钮不会随展开重算）", t);
            }
        } catch (Throwable t) {
            Log.w(TAG, "os5_mirror: shelf hook failed", t);
        }
        return n;
    }

    /** 命中的展开状态来源，打进日志用（别让它靠猜） */
    static String sShadeSource = "untried";
    /** 命中的 QS 全屏来源，同上 */
    static String sQsSource = "untried";

    /** 从 shelf（或行内任意节点）往上找 NSSL；找不到返回 null。 */
    private static View findNssl(View from) {
        View v = from;
        while (v != null && !v.getClass().getName()
                .contains("NotificationStackScrollLayout")) {
            v = v.getParent() instanceof View ? (View) v.getParent() : null;
        }
        return v;
    }

    /**
     * 面板是否已展开 —— 用**原生那个值**，不是我们自己造的阈值。
     * OS5 的浮动钮条件（A15 `setClearAllButtonVisibility`，OS3-… 对照见 §11.2）读的是
     * `this.mIsExpanded`，而 A15:2004 / A13:4508 的 `setExpandedHeight` 都在同一行
     * `setIsExpanded(f > 0.0f)` 里写它 —— 两版语义一致：有展开高度即算展开。
     * 我先前用"展开分数 > 0.5"是**凭空加的阈值**（而且 A15/A13 的 NSSL 都没有
     * `getExpansionFraction()`，那个同名方法在 AmbientState 上），所以顺序反过来：
     * 先读 `isExpanded()`，读不到才退到分数，两者都读不到才放行。
     * 放行意味着"收起态也可能亮着浮动清除按钮"，所以到底走了哪条路要能看见。
     */
    private static boolean isShadeExpanded(View from) {
        View v = findNssl(from);
        if (v == null) {
            sShadeSource = "nssl-missing";
            return true;
        }
        try {
            Object b = XposedHelpers.callMethod(v, "isExpanded");
            if (b instanceof Boolean) {
                sShadeSource = "isExpanded";
                return (Boolean) b;
            }
        } catch (Throwable ignored) {
        }
        try {
            Object f = XposedHelpers.callMethod(v, "getExpansionFraction");
            if (f instanceof Float) {
                sShadeSource = "fraction";
                return ((Float) f).floatValue() > 0.5f;
            }
        } catch (Throwable ignored) {
        }
        sShadeSource = "unavailable";
        return true;
    }

    /**
     * QS 是否铺满（下拉到顶打开快捷面板）。OS5 的浮动钮条件里有一条
     * `!isQsFullScreen()`（A15:6113），我之前整条都没查 —— QS 全屏时浮动清除钮会
     * 压在快捷面板上，这是 OS5 明确不画的形态。
     * A13 有同名方法（`NotificationStackScrollLayout.isQsFullScreen()`，
     * OS3 jadx:3491 读 `mQsFullScreen`，写点 :4828），所以照读；读不到就放行，
     * 并把来源记进日志（放行 = QS 态下也可能多一枚钮，看得见才谈修）。
     */
    private static boolean isQsFullScreen(View from) {
        View v = findNssl(from);
        if (v == null) {
            sQsSource = "nssl-missing";
            return false;
        }
        try {
            Object b = XposedHelpers.callMethod(v, "isQsFullScreen");
            if (b instanceof Boolean) {
                sQsSource = "isQsFullScreen";
                return (Boolean) b;
            }
        } catch (Throwable ignored) {
        }
        sQsSource = "unavailable";
        return false;
    }

    /**
     * 浮动清除钮该不该显示 —— 逐条对齐 OS5 的两处原生判据：
     * `shouldShowClearAll()`（有可清除通知 && 非"锁屏且面板没拉下"）
     * && `mIsExpanded` && `!isQsFullScreen()` && `!isKeyguardShowing()`。
     * 其中"有可清除通知"我们跟着系统写 shelf 的那个布尔（`sDismissAllowed`，
     * 它就是 A13 `shouldShowClearAll()` 的结果），锁屏用公开 API `KeyguardManager`
     * 而不是反射 vivo 的 StateUtils —— 反射失败就等于闸门形同虚设。
     * **四项都要真读，不许用 && 短路**：短路会让后面几项的来源永远停在 `untried`，
     * 而挂载日志就在这一刻打出去 —— 见证当场变成假的（A13 真机第一次跑就是这样：
     * `shade=isExpanded qs=untried`，因为收起态下 `mIsExpanded` 是 false）。
     */
    private static boolean clearAllShouldShow(View shelf) {
        boolean expanded = isShadeExpanded(shelf);
        boolean qsFull = isQsFullScreen(shelf);
        boolean locked = isKeyguardLocked(shelf);
        return expanded && !qsFull && !locked && sDismissAllowed;
    }

    /** 上一次看到的展开/QS 值（对应 A15 里被 z2 门控掉的那一类重复重算）。 */
    private static final boolean[] sLastExpanded = {false};
    private static final boolean[] sLastQs = {false};

    /**
     * 只有布尔参数真的翻转才重算一次。`setExpandedHeight` 每帧都会用同一个值调
     * `setIsExpanded`，直接挂钩子等于在拖拽的每一帧走父链 + 反射；A15 自己也是
     * 靠 `z2 = (z != mIsExpanded)` 把这段筛掉的（A15 jadx:4231）。
     * 记账在调用**之前**，shelf 引用还没建立时也不让缓存和真实状态脱节。
     */
    private static XC_MethodHook gatedRecheck(final boolean[] last) {
        return new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                if (param.args == null || param.args.length < 1
                        || !(param.args[0] instanceof Boolean)) {
                    return;
                }
                boolean now = ((Boolean) param.args[0]).booleanValue();
                if (now == last[0]) {
                    return;
                }
                last[0] = now;
                View shelf = sShelfRef == null ? null : sShelfRef.get();
                if (shelf == null) {
                    return;
                }
                ClearAll.onShelfDismissVisible(shelf, clearAllShouldShow(shelf));
            }
        };
    }

    /**
     * 锁屏时不要出现浮动清除按钮（OS5 自己也是这么规定的）。
     * 用公开 API 问 KeyguardManager，避免去反射 vivo 的 KeyguardStateController ——
     * 那些类名/方法在两版差异很大，反射失败就等于闸门形同虚设。
     */
    private static boolean isKeyguardLocked(View from) {
        try {
            android.app.KeyguardManager km = (android.app.KeyguardManager) from.getContext()
                    .getSystemService(android.content.Context.KEYGUARD_SERVICE);
            return km != null && km.isKeyguardLocked();
        } catch (Throwable ignored) {
            return false;
        }
    }

    /**
     * A13 的 shelf 与 OS5 差两处，但**只有一处能移植**：
     *
     * 1. 顶部圆角：A13 的 `onFinishInflate` 自己写 `setTopRoundness(0.0f,false)`、
     *    OS5 写 1.0f ⇒ 这里跟着补 1.0f，签名是 `setTopRoundness(FF)Z`（第二参是"立刻生效"），
     *    与原生调用形状一致。**读回来当见证**：mTopRoundness 是同步写的目标值，
     *    日志里 `readback=` 取的就是它，字段被改名/方法没生效都会红。
     * 2. 7dp 间距：**A13 没有这个通道**。`mPaddingBetweenElements` 在 A15 参与
     *    shelf 的 Y 位置（OS5-NotificationShelf.java:341/351/357 `setYTranslation(pad + stackY)`），
     *    而 A13 只在图标数算（OS3-NotificationShelf.java:484）与"行是否已在栏内"的判定
     *    （:611）里读它，shelf 的位置计算完全不看它（A13 全 dex 里 `setYTranslation` 出现 0 次）。
     *    之前我们照 OS5 写 7dp，实际是"没补上间距，反倒把那两个判定的阈值挪了 21px"。
     *    ⇒ 改成在 A13 真正算 shelf 位移的那一行之后加一次（见 applyShelfGap）。
     */
    private static void mirrorShelf(View shelf) {
        if (!sEnabled) {
            return;
        }
        float readback = -1f;
        try {
            XposedHelpers.callMethod(shelf, "setTopRoundness", 1.0f, false);
            sShelfMirrors++;
        } catch (Throwable t) {
            Log.w(TAG, "os5_mirror: shelf top roundness write failed", t);
        }
        try {
            Object v = XposedHelpers.getObjectField(shelf, "mTopRoundness");
            if (v instanceof Float) {
                readback = ((Float) v).floatValue();
            }
        } catch (Throwable t) {
            Log.w(TAG, "os5_mirror: shelf roundness readback failed", t);
        }
        if (verify(shelf.getContext())) {
            Log.i(TAG, "os5_mirror: shelf topRoundness=1.0 readback="
                    + String.format(java.util.Locale.US, "%.3f", readback));
        }
    }

    /**
     * 7dp 间距在 A13 上的正确落点。
     *
     * A15 在 `updateAppearance` 里把 `mPaddingBetweenElements` 加进 shelf 的 Y
     * （OS5-NotificationShelf.java:341/351/357）；A13 的同一段是
     * `OS3-NotificationShelf.java:254  shelfState.yTranslation = min(viewState.yTranslation +
     * viewState.height, stackY + stackHeight - shelfState.height)` —— 少加那一项，
     * 而且 A13 字段叫 `yTranslation`、全 dex 没有 `setYTranslation`。
     * ⇒ 只能在这条公式算完之后补一次；写 `mPaddingBetweenElements` 是无效写入。
     *
     * 幂等：254 只在"栏里有可见背景子项"时重算，不算时字段保持我们上次写的值，
     * 直接 += 会每帧累加。所以记住每个 state 上次写出的值，命中就先减回基线再加。
     */
    private static final java.util.WeakHashMap<Object, Float> SHELF_Y_APPLIED =
            new java.util.WeakHashMap<>();
    private static int sShelfGapApplied;

    private static void applyShelfGap(View shelf) {
        if (!sEnabled) {
            return;
        }
        int gapPx = NotifyRowSpec.dp(shelf.getContext(), GAP_BETWEEN_DP);
        try {
            Object state = XposedHelpers.callMethod(shelf, "getViewState");
            if (state == null) {
                return;
            }
            float y = ((Float) XposedHelpers.getObjectField(state, "yTranslation")).floatValue();
            Float prev = SHELF_Y_APPLIED.get(state);
            float base = (prev != null && Math.abs(y - prev.floatValue()) < 0.5f)
                    ? y - gapPx : y;
            float next = base + gapPx;
            XposedHelpers.setFloatField(state, "yTranslation", next);
            SHELF_Y_APPLIED.put(state, Float.valueOf(next));
            sShelfGapApplied++;
            if (verify(shelf.getContext()) && sShelfGapApplied <= 6) {
                // 带上 base：验证器断言 `yTo == base + want` 这条算术，
                // 而不是"必须抓到第一次真实施加"——幂等命中同样能证明间距落在 shelf 位移上。
                Log.i(TAG, "os5_mirror: shelf gap yFrom=" + y + " yTo=" + next
                        + " base=" + base + " want=" + gapPx);
            }
        } catch (Throwable t) {
            Log.w(TAG, "os5_mirror: shelf gap apply failed", t);
        }
    }

    // ---------------------------------------------------------------- 浮动一键清除

    /** OS5 的清除按钮是面板底部居中的 46dp 圆形自绘 ImageView；A13 只有 shelf 内联文字，这里复刻观感、复用原生点击链。 */
    static final class ClearAll extends ImageView {

        private final Paint mShadow = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final int mPad;
        private boolean mShown;
        private boolean mNav = true;   // 上次用来决定底边距的"导航条可见"信号

        ClearAll(Context ctx) {
            super(ctx);
            mPad = NotifyRowSpec.dp(ctx, CLEAR_PAD_DP);
            setPadding(mPad, mPad, mPad, mPad);
            setLayerType(LAYER_TYPE_SOFTWARE, null);
            mShadow.setColor(CLEAR_SHADOW);
            mShadow.setStyle(Paint.Style.FILL);
            mShadow.setMaskFilter(new BlurMaskFilter(CLEAR_BLUR, BlurMaskFilter.Blur.OUTER));
            setImageDrawable(moduleIcon(ctx));
            setContentDescription("清除全部通知");
            setClickable(true);
            setFocusable(true);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            float cx = getWidth() / 2f;
            float cy = getHeight() / 2f;
            canvas.drawCircle(cx, cy, cx - mPad, mShadow);
            super.onDraw(canvas);
        }

        @Override
        protected void onSizeChanged(int w, int h, int oldw, int oldh) {
            super.onSizeChanged(w, h, oldw, oldh);
            try {
                logBounds(this);
            } catch (Throwable t) {
                Log.w(TAG, "os5_mirror: clear-all bounds log failed", t);
            }
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            int a = event.getActionMasked();
            if (a == MotionEvent.ACTION_DOWN) {
                animate().scaleX(0.9f).scaleY(0.9f).setDuration(PRESS_MS)
                        .setInterpolator(OS5_EASE).start();
            } else if (a == MotionEvent.ACTION_UP || a == MotionEvent.ACTION_CANCEL) {
                animate().scaleX(1f).scaleY(1f).setDuration(PRESS_MS)
                        .setInterpolator(OS5_EASE).start();
            }
            return super.onTouchEvent(event);
        }

        /** shelf 的可见性变化即 OS5 的显隐条件；同时把内联文字按钮按 OS5 一样藏起来。 */
        static void onShelfDismissVisible(View shelf, boolean show) {
            if (!sEnabled) {
                return;
            }
            try {
                if (nativeClearAllPresent(shelf)) {
                    return;
                }
                hideInline(shelf);
                ClearAll btn = ensureAttached(shelf);
                if (btn == null) {
                    return;
                }
                int target = show ? VISIBLE : INVISIBLE;
                if (btn.getVisibility() != target) {
                    if (show) {
                        // OS5：先VISIBLE再淡入，否则 alpha 从 0 起看不出来
                        btn.setVisibility(VISIBLE);
                        btn.animate().alpha(1f).setDuration(APPEAR_MS)
                                .setInterpolator(OS5_EASE).start();
                    } else {
                        // OS5：淡出结束后才置 INVISIBLE；原来同帧就藏起来，动画等于不存在
                        btn.animate().alpha(0f).setDuration(APPEAR_MS)
                                .setInterpolator(OS5_EASE)
                                .withEndAction(new Runnable() {
                                    @Override
                                    public void run() {
                                        // withEndAction 在动画被"重新显示"打断时也会跑，
                                        // 所以要按最新状态再判一次，否则会出现"已显示但被置灰"
                                        if (!btn.mShown) {
                                            btn.setVisibility(INVISIBLE);
                                        }
                                    }
                                }).start();
                    }
                }
                btn.mShown = show;
                logBounds(btn);
            } catch (Throwable t) {
                Log.w(TAG, "os5_mirror: clear-all visibility failed", t);
            }
        }

        /**
         * OS5 的浮动圆钮是系统自己的
         * `com.vivo.systemui.statusbar.ui.NotificationClearAllImageView`
         * （A15 三个 dex 命中 6 次、由 status_bar_expanded.xml 直接 inflate；A13 全 dex 命中 0），
         * 所以只有"系统没提供"时才补一枚，否则 OS5 上会出现两枚重叠的按钮。
         * 用类存在性而不是版本号判断：ROM 哪天补上这个类，我们自动让位。
         */

        private static boolean nativeClearAllPresent(View shelf) {
            Boolean cached = sNativeClearAll;
            if (cached != null) {
                return cached;
            }
            boolean found;
            try {
                XposedHelpers.findClass(NATIVE_CLEAR_ALL_BUTTON, shelf.getClass().getClassLoader());
                found = true;
            } catch (Throwable t) {
                found = false;
            }
            sNativeClearAll = found;
            Log.i(TAG, "os5_mirror: clear-all native=" + found);
            return found;
        }

        /**
         * 坐标只能来自日志：脚本按这里的中心 tap，没点到就没有
         * `clear-all tap` 见证行，验收器会红——不会把"静默空转"当成"按钮能用"。
         * onSizeChanged 与显隐变化两处都要覆盖，但**几何没变就不重复打**：
         * shelf 的 updateAppearance 每帧都会走到这里，真机一次滑动就刷出 322 条相同坐标，
         * 把同一缓冲区里 `g2 path / shelf gap / clear-all attached` 的见证挤了出去，
         * 门禁因此报"没有日志"（见证淹掉见证，比没见证更难查）。
         */
        private static String sLastBoundsLine;

        private static void logBounds(ClearAll btn) {
            if (btn.getWidth() == 0 || !btn.isAttachedToWindow() || !verify(btn.getContext())) {
                return;
            }
            int[] loc = new int[2];
            btn.getLocationOnScreen(loc);
            String line = "x=" + loc[0] + " y=" + loc[1]
                    + " w=" + btn.getWidth() + " h=" + btn.getHeight();
            if (line.equals(sLastBoundsLine)) {
                return;
            }
            sLastBoundsLine = line;
            Log.i(TAG, "os5_mirror: clear-all at " + line);
        }

        /**
         * 内联文字按钮在 A13 上不是装饰，它是收纳栏可见性判据的输入：
         * `changeShelfVisibility(z)` 用 `(z && 内联.getVisibility()==8) || 面板折叠 || QS全展开`。
         * OS5 那边 `setDismissVisible` 无条件写 8（OS5-NotificationShelf.java:119-128），
         * 我们一开始照抄成"恒 GONE"，结果在 A13 上把这条判据简化成了 `z`，
         * 而 z 每帧都在变 ⇒ 收纳栏滑动时闪。现在的做法见 hideInline()：
         * 只在系统要显示它时降级成 INVISIBLE（视觉消失、但不污染 `==8`），
         * 系统自己判 GONE 时不干预，清空后收起栏的行为照旧。
         */
        /** 系统改了收纳栏可见性时跟着重算浮钮；读的是系统的决定，不写回任何系统状态。 */
        static void reEvaluate(View shelf) {
            if (!sEnabled) {
                return;
            }
            // 比 clearAllShouldShow 多一项：一键清除后系统把整条 shelf 收成 INVISIBLE，
            // 这时栏已经没了，钮不能留在空栏上（OS5 靠 NSSL 自己重算，A13 没那个方法，见 §9）。
            boolean show = clearAllShouldShow(shelf) && shelf.getVisibility() == VISIBLE;
            logVisFlip(shelf, show);
            onShelfDismissVisible(shelf, show);
        }

        /**
         * 收纳栏闪烁的取证：只在"判据或可见性真的变了"时打一行。
         * 每帧无脑打会把别的见证挤出 logcat 缓冲区（真机刷出过 322 条同内容日志）。
         */
        private static String sLastVisState;

        private static void logVisFlip(View shelf, boolean show) {
            if (!verify(shelf.getContext())) {
                return;
            }
            String st = "shelfVis=" + shelf.getVisibility() + " want=" + (show ? 1 : 0)
                    + " dismiss=" + (sDismissAllowed ? 1 : 0)
                    + " shade=" + sShadeSource + " qs=" + sQsSource;
            if (st.equals(sLastVisState)) {
                return;
            }
            sLastVisState = st;
            Log.i(TAG, "os5_mirror: shelf/button " + st);
        }

        /**
         * 隐藏内联"一键清除"文字（OS5 观感）但**不喂给承重墙一个假的 8**：
         * A13 的 `changeShelfVisibility(z)` 判据是 `(z && 内联.getVisibility()==8) || 面板折叠 || QS全展开`。
         * 我们若把它恒按成 GONE(8)，第一条就退化成 `z` —— 而 z 在滑动的每一帧都在翻，
         * 真机实测 `shelfVis` 0↔4 每帧一次（11 条翻转里 10 条是 0↔4），就是收纳栏闪烁的成因。
         * 规则：系统本来要显示它（VISIBLE）时，我们降级成 INVISIBLE(4)：视觉上看不见，
         * 但 `==8` 不成立 ⇒ 判据回到原生语义；系统自己判 GONE 时不去改它，
         * 所以"清空后该收起还收起"仍然成立。
         */
        private static void hideInline(View shelf) {
            Object tv = XposedHelpers.getObjectField(shelf, "mDismissAllNotification");
            if (!(tv instanceof View)) {
                return;
            }
            View v = (View) tv;
            if (v.getVisibility() == VISIBLE) {
                v.setVisibility(INVISIBLE);
            }
        }

        /** 在 shelf 的宿主上挂（或复用）那枚浮动圆钮；每次调用都按当前 shelf 重绑点击转接。 */
        private static ClearAll ensureAttached(View shelf) {
            ViewGroup host = hostFor(shelf);
            if (host == null) {
                return null;
            }
            for (int i = 0; i < host.getChildCount(); i++) {
                View c = host.getChildAt(i);
                if (c instanceof ClearAll) {
                    hookClick((ClearAll) c, shelf);
                    refreshMargin((ClearAll) c, host);
                    return (ClearAll) c;
                }
            }
            Context ctx = shelf.getContext();
            final ClearAll btn = new ClearAll(ctx);
            btn.setAlpha(0f);
            btn.setVisibility(INVISIBLE);
            FrameLayout.LayoutParams wanted = new FrameLayout.LayoutParams(
                    NotifyRowSpec.dp(ctx, CLEAR_SIZE_DP), NotifyRowSpec.dp(ctx, CLEAR_SIZE_DP),
                    Gravity.CENTER_HORIZONTAL | Gravity.BOTTOM);
            boolean nav = navBarVisible(ctx, host);
            btn.mNav = nav;
            wanted.bottomMargin = NotifyRowSpec.dp(ctx, nav ? CLEAR_MARGIN_NAV_DP : CLEAR_MARGIN_DP);
            // 宿主已保证是 FrameLayout（见 hostFor），gravity 与 bottomMargin 都会按其原生语义生效
            host.addView(btn, wanted);
            sClearAttaches++;
            hookClick(btn, shelf);
            if (verify(ctx)) {
                Log.i(TAG, "os5_mirror: clear-all attached size=" + wanted.width
                        + " marginBottom=" + wanted.bottomMargin + " nav=" + nav
                        + " navSrc=" + sNavSource + " navVis=" + (sNavVisible ? 1 : 0)
                        + " navH=" + sNavInsetPx
                        + " shade=" + sShadeSource + " qs=" + sQsSource
                        + " host=" + host.getClass().getSimpleName());
            }
            return btn;
        }

        /**
         * OS5 不是一次性决定边距：`NSSL.setClearAllButtonLocation` 在 `onConfigurationChanged`
         * 里会重新算（A15 dexdump 调用点 2ad99e），导航条由隐到显（或反过来）时 40dp/62dp 会换。
         * 我们跟着重算：只在信号真的变了才写回 LayoutParams，避免每次 shelf 事件都触发一次 requestLayout。
         */
        private static void refreshMargin(ClearAll btn, ViewGroup host) {
            boolean nav = navBarVisible(btn.getContext(), host);
            if (nav == btn.mNav) {
                return;
            }
            btn.mNav = nav;
            if (btn.getLayoutParams() instanceof FrameLayout.LayoutParams) {
                FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) btn.getLayoutParams();
                lp.bottomMargin =
                        NotifyRowSpec.dp(btn.getContext(), nav ? CLEAR_MARGIN_NAV_DP : CLEAR_MARGIN_DP);
                btn.setLayoutParams(lp);
                Log.i(TAG, "os5_mirror: clear-all margin re-armed nav=" + nav
                        + " navSrc=" + sNavSource + " navH=" + sNavInsetPx
                        + " marginBottom=" + lp.bottomMargin);
            }
        }

        /**
         * 点击只转接，不补动画。A13 原生这一条链与 OS5 逐参数相同：
         * NSSL.setShelfController -> setShelfListener -> shelf.setClearNotificationListener
         * -> lambda: onDismiss() + clearNotifications(0, z) + （mClearAllInProgress 时）
         * updateShelfWhenDismissAll(z) -> animateShelfDisappear(z)
         * = alpha 1->0 + scale(z?0:1)，250ms，PathInterpolator(.33,0,.67,1)，结束 INVISIBLE。
         * 我们自己再播一遍是错的：z = !hasUnClearableNotifications，还剩 ongoing 通知时
         * 系统故意让栏留着，无门控的补播会把栏抹掉。
         *
         * inlineListener=0 表示监听压根没挂（它只在 setShelfController 里挂一次），
         * 这一击就是静默空转——必须打进日志，不能等装机当天靠肉眼发现。
         * inlineVis 应为 8(GONE)：OS5 的 setDismissVisible 无条件写 8，而
         * changeShelfVisibility(z) 用 (z && 内联==8) 决定整栏隐藏，写 8 才是 OS5 行为。
         */
        private static void hookClick(final ClearAll btn, final View shelf) {
            btn.setOnClickListener(v -> {
                try {
                    Object tv = XposedHelpers.getObjectField(shelf, "mDismissAllNotification");
                    if (verify(v.getContext())) {
                        Log.i(TAG, "os5_mirror: clear-all tap inlineListener="
                                + (tv instanceof TextView
                                        && ((TextView) tv).hasOnClickListeners() ? 1 : 0)
                                + " inlineVis="
                                + (tv instanceof View ? ((View) tv).getVisibility() : -1)
                                + " shelfVis=" + shelf.getVisibility());
                    }
                    if (tv instanceof TextView) {
                        ((TextView) tv).performClick();
                    }
                } catch (Throwable t) {
                    Log.w(TAG, "os5_mirror: clear-all click failed", t);
                }
            });
        }

        /**
         * 宿主必须照 OS5 的布局来选。OS5 的原生钮是 `status_bar_expanded.xml` 里
         * **NotificationPanelView 的直接子级**（`layout_gravity=0x51`=BOTTOM|CENTER_HORIZONTAL、
         * `layout_marginBottom=40dp`、`alpha=0`、wrap_content，aapt2 dump 实测），
         * 而 A13 的这条链是 shelf → NSSL → `NotificationsQuickSettingsContainer`，
         * 后者 **extends ConstraintLayout**（A13 反编译 :23）：
         * 给它塞 `FrameLayout.LayoutParams` 会被 `generateLayoutParams` 换成 Constraint 参数、
         * gravity 被丢，未约束的子视图落到左上角 —— 按钮就不是"底部居中"而是"顶部靠左"。
         * 所以往上找**第一个 FrameLayout**：A13 上正是 `NotificationPanelView`
         * （`PanelView extends FrameLayout`），与 OS5 同一层、同一个 gravity 语义。
         */
        private static ViewGroup hostFor(View shelf) {
            View root = shelf;
            while (root != null && root.getParent() instanceof View) {
                View parent = (View) root.getParent();
                String name = parent.getClass().getSimpleName();
                // 只排除"会跟着列表滚动/被当列表项测量"的容器：NSSL 与 shelf 本身
                // （A13 上它们是 ViewGroup/ExpandableNotificationRow，不是 FrameLayout，
                // 但别的 ROM/后续版本改继承关系时这条就是保险）
                boolean listLike = name.contains("StackScrollLayout") || name.contains("Shelf");
                if (parent instanceof FrameLayout && !listLike) {
                    return (ViewGroup) parent;
                }
                root = parent;
            }
            return null;
        }

        private static String sNavSource = "unset";   // insets = 读到窗口信号；config = 兜底
        private static boolean sNavVisible = true;
        private static int sNavInsetPx = -1;        // 导航条留出的高度（-1 = 没读到）

        /**
         * OS5 底边距 40dp / 62dp 的分支，判据是「导航条此刻可不可见」，不是「三键还是手势」：
         * A15 的 `NSSL.setClearAllButtonLocation` 把 `mCentralSurfaces` 传进去（dexdump 调用点 2ae284），
         * 所以走 `CentralSurfaces.isNavigationBarVisible()` 那一支
         * （OS5-NotificationClearAllImageView.java:207-238；同一处还会把 46dp 尺寸**覆写**回
         * LayoutParams，所以 XML 里的 wrap_content 不是最终值）。
         * 全面屏手势下 hint bar 仍可见 ⇒ OS5 用的也是 62dp。
         * 曾试过 `config_navBarInteractionMode`：实测 A15 与 A11 的 framework-res 里它都是 0，
         * 既不是系统真正读的信号，也可能与真实可见性分叉 ⇒ 不用。
         * 还没 attach 拿不到 rootWindowInsets 才退回
         * `config_showNavigationBar`，并把来源打进日志（`navSrc=`）。
         */
        private static boolean navBarVisible(Context ctx, View host) {
            try {
                WindowInsets ri = host.getRootWindowInsets();
                if (ri != null) {
                    // isVisible() 才是 OS5 那句 isNavigationBarVisible() 的等价物：
                    // "造这些 inset 的窗口此刻在不在"。getInsets().bottom>0 量的是"留没留出高度"，
                    // 全面屏手势下 hint bar 可见但 inset 常为 0 ⇒ 会选错 40dp。
                    // 两个值都打出来（navVis / navH），门禁能看见"可见但 0 高"这种正是要 62dp 的情形。
                    sNavVisible = ri.isVisible(WindowInsets.Type.navigationBars());
                    android.graphics.Insets nb =
                            ri.getInsets(WindowInsets.Type.navigationBars());
                    sNavInsetPx = nb == null ? -1 : nb.bottom;
                    sNavSource = "insets";
                    return sNavVisible;
                }
            } catch (Throwable t) {
                Log.w(TAG, "os5_mirror: nav bar insets unreadable, fall back to config", t);
            }
            sNavVisible = hasNavigationBar(ctx);
            sNavInsetPx = -1;
            sNavSource = "config";
            return sNavVisible;
        }

        private static boolean hasNavigationBar(Context ctx) {
            try {
                int id = ctx.getResources().getIdentifier("config_showNavigationBar", "bool",
                        "android");
                return id != 0 && ctx.getResources().getBoolean(id);
            } catch (Throwable t) {
                return false;
            }
        }

        private static android.graphics.drawable.Drawable moduleIcon(Context ctx) {
            try {
                Context mod = ctx.createPackageContext("com.betteroriginos",
                        Context.CONTEXT_IGNORE_SECURITY);
                int id = mod.getResources().getIdentifier("bo_ic_clear_all_os5", "drawable",
                        "com.betteroriginos");
                return id == 0 ? null : mod.getDrawable(id);
            } catch (Throwable t) {
                Log.w(TAG, "os5_mirror: module icon unavailable", t);
                return null;
            }
        }
    }
}
