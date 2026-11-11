package com.betteroriginos.feature.camera;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;

import com.betteroriginos.R;
import com.betteroriginos.core.Feature;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

/**
 * 相机快门按钮样式（用户 11-05 选定 B 款：OS5 原子设计环，只改静态外观）。
 *
 * 落点（逆向 + 真机取证，材料在 work/camera-re/）：快门是
 * {@code com.android.camera.ui.commonui.ShutterButton}（资源条目名 {@code shutter_button}，
 * 屏幕实测 210x210px=70dp），类链 {@code ShutterButton → ui.base.DualPointButton →
 * RotateImageView → TwoStateImageView → ImageView}；外观来自布局里 {@code android:src}
 * 那张 animated-selector：灰外环 64dp/4dp + 黄内环 52dp/2dp，中心透明。
 *
 * 三条被真机教出来的约束：
 * 1. **只挂 setImage* 一条都不命中**：{@code android:src} 是 ImageView 构造函数里
 *    {@code obtainStyledAttributes} 直接取用并 {@code updateDrawable} 的，不走
 *    {@code setImageResource/setImageDrawable}（真机：hooks=2、按钮在屏、零日志）。
 * 2. **不用 {@code setForeground}**：构造期挂上去的前景实测没被画出来
 *    （见证 {@code fg=not-drawn}，屏幕上那颗实例也没有任何 draw 记录）——
 *    相机会自己动这颗视图的前景/轮廓（{@code setOutlineProvider(oval)}）。
 *    所以改成**在快门自己的 {@code onDraw} 之后画我们的环**：同一个绘制趟、同一个画布，
 *    视图级按下缩放 1→0.9 自然带着我们的环一起缩，热区与点击链路完全不碰。
 * 3. **不换 src 里的 Drawable**：相机代码有
 *    {@code (AnimatedVectorDrawable) imageView.getDrawable()} 的强转
 *    （{@code BaseBottomBar.processSVGAnim} / {@code updateButtonIcon} 前置检查），
 *    换成我们自己的 Drawable 会在语音快门/加载态那条路上 ClassCastException。
 *    原图只压 {@code setImageAlpha(0)}（AVD 仍在、录像切换动画仍在跑）。
 *
 * 只接管**拍照态**（图标条目名以 {@code ic_capture_camera} 开头，或从没走过设图调用 ——
 * 布局默认就是拍照那颗）；判到录像/loading/语音快门就还原，不能把"正在录像"的红盘反馈抹掉。
 */
public final class CameraShutterFeature extends Feature {
    private static final String TAG = "BetterOriginOS";
    private static final String SHUTTER_ID_NAME = "shutter_button";
    private static final String PHOTO_ICON_PREFIX = "ic_capture_camera";

    /** 设图调用可能出现在类链任何一层（含 ImageView 自己），逐层挂，命中数自报 */
    private static final String[] ICON_CHAIN = {
            "com.android.camera.ui.commonui.ShutterButton",
            "com.android.camera.ui.base.DualPointButton",
            "com.android.camera.ui.base.RotateImageView",
            "com.android.camera.ui.base.TwoStateImageView",
            "android.widget.ImageView",
    };
    /**
     * onDraw 的真实声明处不好猜：链上任何一层覆写且**不调 super**，挂在别层上就一条不命中
     * （真机：只挂 RotateImageView 时 ENTER 零条，而 ShutterButton 构造钩子能命中，
     * 说明类对象没错、错在方法所在层）。所以整条链都挂，命中数逐层自报。
     */
    private static final String[] DRAW_CHAIN = {
            "com.android.camera.ui.commonui.ShutterButton",
            "com.android.camera.ui.base.DualPointButton",
            "com.android.camera.ui.base.RotateImageView",
            "com.android.camera.ui.base.TwoStateImageView",
            "android.widget.ImageView",
    };

    /** 每颗快门视图当前判定的图标条目名（没有就走"布局默认=拍照态"） */
    private static final java.util.WeakHashMap<View, String> LAST_NAME = new java.util.WeakHashMap<>();
    /** 每颗快门视图自己的环 drawable（按 density 造一次，bounds 每次绘制现算） */
    private static final java.util.WeakHashMap<View, ShutterRingDrawable> RINGS =
            new java.util.WeakHashMap<>();
    /** id → 条目名（种类很少；超过上限就停止缓存，不做无界增长） */
    private static final java.util.Map<Integer, String> NAME_CACHE =
            new java.util.concurrent.ConcurrentHashMap<>();
    /** 见证去重：onDraw 是每帧路径，一条只能打一次 */
    private static final java.util.Set<String> WITNESS_SEEN =
            java.util.Collections.synchronizedSet(new java.util.HashSet<String>());
    private static int sPainted;
    private static int sHidden;
    /** 绘制见证的按视图节流：相机进程日志很吵，一次性见证会被 logd 挤掉（10s 一条） */
    private static final java.util.WeakHashMap<View, Long> DRAW_LOG_AT =
            new java.util.WeakHashMap<>();

    @Override
    public String id() {
        return "camera_shutter";
    }

    @Override
    public int titleRes() {
        return R.string.feature_camera_title;
    }

    @Override
    public int summaryRes() {
        return R.string.feature_camera_summary;
    }

    @Override
    public int categoryRes() {
        return R.string.category_camera;
    }

    /** 接管的是第三方 App 的界面，默认关，由用户在功能卡里自己打开。 */
    @Override
    public boolean defaultEnabled() {
        return false;
    }

    @Override
    public String[] scopes() {
        return new String[]{"com.android.camera"};
    }

    @Override
    public void install(ClassLoader cl) {
        StringBuilder sb = new StringBuilder();
        int total = 0;
        for (String name : ICON_CHAIN) {
            Class<?> c = find(cl, name);
            if (c == null) {
                sb.append(' ').append(shortName(name)).append("=missing");
                continue;
            }
            int n = XposedBridge.hookAllMethods(c, "setImageResource", iconHook()).size()
                    + XposedBridge.hookAllMethods(c, "setImageDrawable", iconHook()).size();
            total += n;
            sb.append(' ').append(shortName(name)).append('=').append(n);
        }
        for (String name : DRAW_CHAIN) {
            Class<?> c = find(cl, name);
            if (c == null) {
                continue;
            }
            int n = XposedBridge.hookAllMethods(c, "onDraw", drawHook()).size();
            total += n;
            sb.append(' ').append(shortName(name)).append(".onDraw=").append(n);
        }
        // 自报"实际挂上了什么"：挂不上与挂了不命中是两种完全不同的病
        Log.i(TAG, "camera shutter feature installed hooks=" + total + " by" + sb);
    }

    private static Class<?> find(ClassLoader cl, String name) {
        try {
            return XposedHelpers.findClass(name, cl);
        } catch (Throwable t) {
            return null;
        }
    }

    private static String shortName(String fqn) {
        int i = fqn.lastIndexOf('.');
        return i < 0 ? fqn : fqn.substring(i + 1);
    }

    private XC_MethodHook iconHook() {
        return new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                try {
                    Object self = param.thisObject;
                    if (!(self instanceof View)) {
                        return;
                    }
                    View v = (View) self;
                    if (!isShutter(v)) {
                        return;
                    }
                    String name = argName(v, param.args);
                    if (name == null) {
                        return;   // 判不出来就保留上一次结论，别把状态抹成"未知"
                    }
                    synchronized (LAST_NAME) {
                        LAST_NAME.put(v, name);
                    }
                    witnessMode(v, name);
                } catch (Throwable t) {
                    Log.w(TAG, "camera shutter icon hook failed", t);
                }
            }
        };
    }

    private XC_MethodHook drawHook() {
        return new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                try {
                    Object self = param.thisObject;
                    if (!(self instanceof View) || param.args == null
                            || param.args.length == 0 || !(param.args[0] instanceof Canvas)) {
                        return;
                    }
                    View v = (View) self;
                    if (!isShutter(v)) {
                        return;
                    }
                    Context ctx = v.getContext();
                    String name;
                    synchronized (LAST_NAME) {
                        name = LAST_NAME.get(v);
                    }
                    // 布局默认那颗就是拍照态；只有明确判到非拍照态才交还原生。
                    // OS4 相机切模式不走 setImage*（图标 hooks 全链 0 命中），LAST_NAME
                    // 恒空，只靠"布局默认=拍照态"会把录像态也接管 —— 快门的无障碍
                    // 描述随模式/状态变（真机：录像态="开始录制，按钮…"），拿它兜底否决。
                    boolean photo = (name == null || name.startsWith(PHOTO_ICON_PREFIX))
                            && !recordingByDesc(v);
                    boolean paint = photo && isEnabled(ctx);
                    if (v instanceof ImageView) {
                        int want = paint ? 0 : 255;
                        if (((ImageView) v).getDrawable() != null) {
                            ((ImageView) v).setImageAlpha(want);
                        }
                        if (!paint) {
                            sHidden++;
                            return;
                        }
                    }
                    Canvas canvas = (Canvas) param.args[0];
                    ShutterRingDrawable ring = ringFor(v);
                    int w = v.getWidth();
                    int h = v.getHeight();
                    ring.setBounds(0, 0, w, h);
                    ring.draw(canvas);
                    sPainted++;
                    witnessDraw(v, name, w, h, ring);
                } catch (Throwable t) {
                    Log.w(TAG, "camera shutter draw hook failed", t);
                }
            }
        };
    }

    private static ShutterRingDrawable ringFor(View v) {
        ShutterRingDrawable ring;
        synchronized (RINGS) {
            ring = RINGS.get(v);
        }
        if (ring == null) {
            ring = new ShutterRingDrawable(
                    v.getContext().getResources().getDisplayMetrics().density);
            synchronized (RINGS) {
                RINGS.put(v, ring);
            }
        }
        return ring;
    }

    private static String argName(View v, Object[] args) {
        if (args == null || args.length == 0 || !(args[0] instanceof Integer)) {
            return null;
        }
        String name = entryName(v.getResources(), ((Integer) args[0]).intValue());
        return name == null || name.isEmpty() ? null : name;
    }

    /** 提到录制/录像（开始录制/停止录制/录像中，含英文 Record）都算录像链路，还原生 */
    private static boolean recordingByDesc(View v) {
        CharSequence d = v.getContentDescription();
        if (d == null) {
            return false;
        }
        String s = d.toString();
        return s.contains("录制") || s.contains("录像") || s.contains("Record");
    }

    /**
     * 热路径（每帧 onDraw + 每次设图都会进来）：判据要便宜、不抛、不分配。
     * 认按钮用**资源条目名**而不是类名或 0x id —— 类名会随相机升级重构，id 会重排，
     * 条目名是布局里的契约。
     */
    private static boolean isShutter(View v) {
        int id = v.getId();
        return id != 0 && SHUTTER_ID_NAME.equals(entryName(v.getResources(), id));
    }

    private static String entryName(Resources res, int id) {
        if (id == 0 || res == null) {
            return "";
        }
        Integer key = Integer.valueOf(id);
        String hit = NAME_CACHE.get(key);
        if (hit != null) {
            return hit;
        }
        String name;
        try {
            name = res.getResourceEntryName(id);
        } catch (Throwable t) {
            name = "";
        }
        if (NAME_CACHE.size() < 256) {
            NAME_CACHE.put(key, name);
        }
        return name;
    }

    private static void witnessMode(View v, String name) {
        String key = "mode|" + System.identityHashCode(v) + "|" + name;
        if (!once(key)) {
            return;
        }
        Log.i(TAG, "camera shutter mode icon=" + name
                + " photo=" + (name.startsWith(PHOTO_ICON_PREFIX) ? 1 : 0)
                + " cls=" + v.getClass().getName());
    }

    private static void witnessDraw(View v, String name, int w, int h,
                                    ShutterRingDrawable ring) {
        long now = android.os.SystemClock.uptimeMillis();
        synchronized (DRAW_LOG_AT) {
            Long at = DRAW_LOG_AT.get(v);
            if (at != null && now - at.longValue() < 10000) {
                return;
            }
            DRAW_LOG_AT.put(v, Long.valueOf(now));
        }
        Log.i(TAG, "camera shutter drawn "
                + CameraShutterSpec.describe(ring.lastOuterPx(), ring.lastInnerPx(),
                        ring.lastRingPx(), w)
                + " icon=" + (name == null ? "layout-default" : name)
                + " desc=" + v.getContentDescription()
                + " id=" + Integer.toHexString(System.identityHashCode(v))
                + " painted=" + sPainted + " hidden=" + sHidden);
    }

    private static boolean once(String key) {
        synchronized (WITNESS_SEEN) {
            return WITNESS_SEEN.size() <= 64 && WITNESS_SEEN.add(key);
        }
    }
}
