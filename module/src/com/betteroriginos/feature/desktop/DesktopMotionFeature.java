package com.betteroriginos.feature.desktop;

import android.content.Context;
import android.util.Log;

import com.betteroriginos.R;
import com.betteroriginos.core.Feature;
import com.betteroriginos.core.FeatureOption;
import com.betteroriginos.core.Prefs;
import com.betteroriginos.core.SuCommander;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

/**
 * 桌面动效增强（OriginOS 4 适配，用户 11-10 选定）。
 *
 * 这台机的“桌面过渡动画”设置已经是最高档（舒适=3），但机型预配置
 * /oem/etc/launcher/attributes_preconfig.xml 把两个开关写死为 false：
 * - registeranimation=false        → 打开/退出应用动画总开关（LauncherConfigManager.L / w0()）
 * - wallpaperTargetAnimation=false → 壁纸缩放（LauncherConfigManager.M / y0()）
 * 结果桌面走旧的低档链路，新链路（图标锚点/远程动画/壁纸缩放）不启用。
 *
 * 落点选在 **XML 属性解析层**而不是混淆后的桌面类名：
 * 桌面用 XmlPullParserFactory.newPullParser()（libcore 的 com.android.org.kxml2.io.KXmlParser）
 * 解析预配置，属性读取统一走 XmlPullParser.getAttributeValue(String,String)。
 * 只覆盖 registeranimation / wallpaperTargetAnimation 两个属性名，其余一律放行，
 * 这样不依赖 r1.a / w0() / y0() 这些会随 ROM 版本变化的混淆名。
 *
 * 纪律：
 * 1. 每个进程只在第一次真正改写时打一条见证（属性解析在启动期高频调用，不能刷屏）；
 * 2. 运行期按功能开关判断，关掉即完全放行（钩子仍在，但零写入）；
 * 3. animationGrade / 动态模糊 / two-level 这一轮不碰，单变量验证。
 */
public final class DesktopMotionFeature extends Feature {
    private static final String TAG = "BetterOriginOS";
    private static final String FEATURE_ID = "desktop_motion";

    /** 预配置里两个开关的属性名（XML 里的契约，跨版本稳定） */
    private static final String ATTR_REGISTER = "registeranimation";
    private static final String ATTR_WALLPAPER = "wallpaperTargetAnimation";

    /** libcore kxml2 的两种包名；实际命中哪一个打进日志 */
    private static final String[] PARSERS = {
            "com.android.org.kxml2.io.KXmlParser",
            "org.kxml2.io.KXmlParser",
    };

    /** 每属性只打第一条，后续只计数 */
    private static final Map<String, Integer> FORCED = new ConcurrentHashMap<String, Integer>();
    private static final Set<String> WITNESS_SEEN =
            Collections.synchronizedSet(new HashSet<String>());
    private static Boolean sEnabled;

    @Override
    public String id() {
        return FEATURE_ID;
    }

    @Override
    public int titleRes() {
        return R.string.feature_desktop_motion_title;
    }

    @Override
    public int summaryRes() {
        return R.string.feature_desktop_motion_summary;
    }

    @Override
    public int categoryRes() {
        return R.string.category_desktop;
    }

    /** 改的是桌面进程的 OEM 预配置，默认关；开启后需重启桌面进程。 */
    @Override
    public boolean defaultEnabled() {
        return false;
    }

    @Override
    public int supportNoteRes(Context ctx) {
        return R.string.note_desktop_motion_reboot;
    }

    @Override
    public String[] scopes() {
        return new String[]{"com.bbk.launcher2", "com.android.settings"};
    }

    @Override
    public void install(ClassLoader cl, String packageName) {
        if ("com.android.settings".equals(packageName)) {
            installSettings(cl);
            return;
        }
        install(cl);
    }

    private static final String OPT_LEVEL = "level";
    private static final String OPT_NEW_ANIM = "new_anim";
    private static final String OPT_REGISTER = "register_anim";
    private static final String OPT_WALLPAPER = "wallpaper_anim";

    /** 每个预配置项各自一个开关；档位额外写入系统 enter_exit_app_animation。 */
    @Override
    protected List<FeatureOption> buildOptions() {
        List<FeatureOption> list = new ArrayList<FeatureOption>();
        list.add(FeatureOption.toggle(
                OPT_NEW_ANIM,
                R.string.option_desktop_new_anim_title,
                R.string.option_desktop_new_anim_summary,
                true));
        list.add(FeatureOption.toggle(
                OPT_REGISTER,
                R.string.option_desktop_register_title,
                R.string.option_desktop_register_summary,
                true));
        list.add(FeatureOption.toggle(
                OPT_WALLPAPER,
                R.string.option_desktop_wallpaper_title,
                R.string.option_desktop_wallpaper_summary,
                true));
        list.add(FeatureOption.choice(
                OPT_LEVEL,
                R.string.option_desktop_level_title,
                R.string.option_desktop_level_summary,
                "3",
                new String[]{"0", "1", "2", "3"},
                new int[]{R.string.level_none, R.string.level_efficient,
                        R.string.level_medium, R.string.level_comfort},
                null));
        return list;
    }

    @Override
    public void onOptionChanged(String key, String value) {
        if (OPT_LEVEL.equals(key)) {
            SuCommander.get().writeSystem("enter_exit_app_animation", value, null);
        }
    }

    @Override
    public void install(ClassLoader cl) {
        StringBuilder sb = new StringBuilder();
        int total = 0;
        for (String name : PARSERS) {
            Class<?> c = find(cl, name);
            if (c == null) {
                sb.append(' ').append(shortName(name)).append("=missing");
                continue;
            }
            int n = XposedBridge.hookAllMethods(c, "getAttributeValue", attrHook()).size();
            total += n;
            sb.append(' ').append(shortName(name)).append('=').append(n);
        }
        // 自报“实际挂上了什么”：挂不上与挂了不命中是两种病
        Log.i(TAG, "desktop motion installed hooks=" + total + " by" + sb);
        String os5 = installLauncherOs5(cl);
        Log.i(TAG, "desktop motion os5" + os5);
        String os6 = installLauncherOs6(cl);
        Log.i(TAG, "desktop motion os6" + os6);
        hookConfig(cl);
    }

    /**
     * OS5（桌面 15.0.x）适配。OS5 已没有 attributes_preconfig.xml，改成
     * /system/etc/launcher/preconfig.xml，且锁点换成：
     * - &lt;animation useNewAnim="false"&gt;      → 新过渡链路（图标锚点/远程动画）总开关
     * - &lt;wallpaperTargetAnimation&gt; 缺省      → 壁纸缩放（MTK 平台不在自动放行名单）
     * - &lt;animationGrade&gt; 缺省 1               → 动画档位
     * 解析在 k1.b.L0(XmlPullParser) 里，属性统一过 m1.d.a(parser, ns, name, default)；
     * 对外读取是 k1.b.A0()（register）/ C0()（wallpaper）/ i()（grade）。
     * 同一份代码在 15.0.1.690 上类名/短名一致；缺失只自报不中断。
     */
    private static String installLauncherOs5(ClassLoader cl) {
        StringBuilder sb = new StringBuilder();
        Class<?> cfg = find(cl, "k1.b");
        if (cfg == null) {
            return " config=missing";
        }
        Class<?> xmlu = find(cl, "m1.d");
        if (xmlu != null) {
            int n = XposedBridge.hookAllMethods(xmlu, "a", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    try {
                        Object[] a = param.args;
                        if (a == null || a.length < 3 || !(a[2] instanceof String)
                                || !enabled()) {
                            return;
                        }
                        String name = (String) a[2];
                        String option = null;
                        if ("useNewAnim".equals(name)) {
                            option = OPT_NEW_ANIM;
                        } else if (ATTR_REGISTER.equals(name)) {
                            option = OPT_REGISTER;
                        } else if (ATTR_WALLPAPER.equals(name)) {
                            option = OPT_WALLPAPER;
                        }
                        if (option != null && optOn(option)) {
                            param.setResult(Boolean.TRUE);
                            once("os5|" + name, "desktop motion os5 attr " + name + "=true");
                            probe("attr_" + name, "true");
                        }
                    } catch (Throwable t) {
                        Log.w(TAG, "desktop motion os5 attr hook failed", t);
                    }
                }
            }).size();
            sb.append(" attrs=").append(n);
        } else {
            sb.append(" xmlutil=missing");
        }

        int getters = 0;
        getters += hookBoolGetter(cfg, "A0", OPT_REGISTER);
        getters += hookBoolGetter(cfg, "C0", OPT_WALLPAPER);
        // animationGrade 不能强改：ILauncherInterface.supportLauncherScale() 依赖 k1.b.i()==1；
        // 强写成用户档位会直接把壁纸缩放链路关掉。档位只写系统 enter_exit_app_animation。
        sb.append(" getters=").append(getters);

        // 解析方法名会随版本变（680 是 L0，690 已不是）：统一钩“参数里带 XmlPullParser”的
        // 实例方法，解析完就把三个字段补成解锁值，不依赖混淆名。
        int parsed = 0;
        try {
            Class<?> parserCls = Class.forName("org.xmlpull.v1.XmlPullParser");
            for (java.lang.reflect.Method m : cfg.getDeclaredMethods()) {
                Class<?>[] pt = m.getParameterTypes();
                boolean hasParser = false;
                for (Class<?> p0 : pt) {
                    if (p0 == parserCls) {
                        hasParser = true;
                        break;
                    }
                }
                if (!hasParser) {
                    continue;
                }
                try {
                    XposedBridge.hookMethod(m, new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        if (!enabled() || param.thisObject == null) {
                            return;
                        }
                        Object cfgObj = param.thisObject;
                        boolean i = optOn(OPT_REGISTER) && setTrue(cfgObj, "I");
                        boolean j = optOn(OPT_WALLPAPER) && setTrue(cfgObj, "J");
                        int k = -1;
                        try {
                            k = XposedHelpers.getIntField(cfgObj, "K");
                        } catch (Throwable ignored) {
                        }
                        once("os5|fields", "desktop motion os5 fields I=" + i + " J=" + j + " K=" + k);
                        probe("fields", "I=" + i + ",J=" + j + ",K=" + k
                                + " wallOn=" + optOn(OPT_WALLPAPER)
                                + " raw=" + rawOption(OPT_WALLPAPER));
                    }
                    });
                    parsed++;
                } catch (Throwable ignored) {
                }
            }
        } catch (Throwable t) {
            Log.w(TAG, "desktop motion os5 parse hook failed", t);
        }
        sb.append(" parsed=").append(parsed);

        // 壁纸缩放总门：LauncherEnvironmentManager.f3() = (u1()==0)
        // （u1 是 sConfigWPScaleOpt，本机非 0，所以 open/close 的壁纸目标动画整段被跳过）
        Class<?> env = find(cl, "com.bbk.launcher2.environment.LauncherEnvironmentManager");
        int wpGate = 0;
        if (env != null) {
            wpGate = XposedBridge.hookAllMethods(env, "f3", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    if (param.method instanceof java.lang.reflect.Method
                            && ((java.lang.reflect.Method) param.method).getReturnType() == boolean.class
                            && enabled() && optOn(OPT_WALLPAPER)) {
                        param.setResult(Boolean.TRUE);
                        probe("gate_f3", "true");
                    }
                }
            }).size();
        }
        sb.append(" wpGate=").append(wpGate);

        // 观测：缩放链路的三道运行时判定（不改值，只看真实结果）
        probeGetter(cl, "com.android.quickstep.VivoLauncherHelper$4", "supportLauncherScale", "supportLauncherScale");
        probeGetter(cl, "com.android.quickstep.VivoLauncherHelper$4", "supportWallpaperScale", "supportWallpaperScale");
        try {
            Class<?> ap = find(cl, "com.android.quickstep.gesture.animparam.AnimParamProvider");
            if (ap != null) {
                XposedBridge.hookAllMethods(ap, "getRectWallpaperStartScale", new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        if (param.getResult() instanceof Float) {
                            probe("wpStart", String.valueOf(param.getResult()));
                        }
                    }
                });
            }
        } catch (Throwable ignored) {
        }

        int mirror = XposedBridge.hookAllMethods(cfg, "U0", new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                mirrorAnimationSettings();
            }
        }).size();
        sb.append(" mirror=").append(mirror);

        // 壁纸缩放的属性可能整段缺失（解析器不会去问它）：在平台判定函数后面直接补字段。
        int plat = XposedBridge.hookAllMethods(cfg, "e", new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                if (enabled() && optOn(OPT_WALLPAPER) && param.thisObject != null) {
                    boolean j = setTrue(param.thisObject, "J");
                    probe("platform", "J=" + j);
                }
            }
        }).size();
        sb.append(" platform=").append(plat);
        return sb.toString();
    }

    private static int probeGetter(ClassLoader cl, String className, String method, String key) {
        Class<?> c = find(cl, className);
        if (c == null) {
            return 0;
        }
        try {
            return XposedBridge.hookAllMethods(c, method, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    if (param.getResult() instanceof Boolean) {
                        probe(key, String.valueOf(param.getResult()));
                    }
                }
            }).size();
        } catch (Throwable t) {
            return 0;
        }
    }

    private static int hookBoolGetter(Class<?> c, final String name, final String option) {
        try {
            return XposedBridge.hookAllMethods(c, name, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    if (param.method instanceof java.lang.reflect.Method
                            && ((java.lang.reflect.Method) param.method).getReturnType() == boolean.class
                            && enabled() && optOn(option)) {
                        param.setResult(Boolean.TRUE);
                        once("os5|" + name, "desktop motion os5 " + name + "()=true");
                        probe("getter_" + name, "true");
                    }
                }
            }).size();
        } catch (Throwable t) {
            return 0;
        }
    }

    private static volatile String sProbe;

    /** 诊断：桌面进程日志读不到，把 OS5 判定结果写进桌面自己的 SharedPreferences。 */
    private static void probe(String key, String value) {
        try {
            Context ctx = currentApp();
            if (ctx == null) {
                ctx = systemContext();
            }
            if (ctx != null) {
                ctx.getSharedPreferences("betteroriginos_desktop_probe", 0).edit()
                        .putString(key, value).commit();
            }
        } catch (Throwable ignored) {
        }
    }

    private static boolean setInt(Object cfg, String field, int value) {
        try {
            XposedHelpers.setIntField(cfg, field, value);
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    /** 用户选的档位（0..3），默认 3=舒适。 */
    private static int levelOption() {
        Context ctx = currentApp();
        if (ctx == null) {
            ctx = systemContext();
        }
        if (ctx == null) {
            return 3;
        }
        try {
            return Integer.parseInt(Prefs.option(ctx, FEATURE_ID, OPT_LEVEL, "3"));
        } catch (Throwable t) {
            return 3;
        }
    }

    /** 桌面会把“动画窗口支持”镜像回系统 settings；解析后补一次，保证设置入口和桌面一致。 */
    private static void mirrorAnimationSettings() {
        if (!enabled()) {
            return;
        }
        Context ctx = currentApp();
        if (ctx == null) {
            ctx = systemContext();
        }
        if (ctx == null) {
            return;
        }
        try {
            if (optOn(OPT_REGISTER)) {
                android.provider.Settings.System.putInt(ctx.getContentResolver(),
                        "support_app_animation_window", 1);
                android.provider.Settings.Secure.putInt(ctx.getContentResolver(),
                        "vivo_register_animation", 1);
            }
            once("os5|mirror", "desktop motion os5 settings mirrored");
            probe("mirror", "ok");
        } catch (Throwable t) {
            Log.w(TAG, "desktop motion os5 mirror failed", t);
        }
    }

    /**
     * OS6（桌面 16.0.x）：配置类换成 h2.a，解析助手换成 q1.a；
     * registeranimation→字段 A / getter C()，wallpaperTargetAnimation→字段 C / getter E()，
     * useNewAnim→字段 f2067h（只有解析入口，没有 getter），animationGrade→字段 E。
     * 壁纸缩放仍由 VivoLauncherHelper$4 的 supportLauncherScale/supportWallpaperScale 判定。
     */
    private static String installLauncherOs6(ClassLoader cl) {
        StringBuilder sb = new StringBuilder();
        Class<?> cfg = find(cl, "h2.a");
        if (cfg == null) {
            return " config=missing";
        }
        Class<?> xmlu = find(cl, "q1.a");
        int attrs = 0;
        if (xmlu != null) {
            attrs = XposedBridge.hookAllMethods(xmlu, "a", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    try {
                        Object[] a = param.args;
                        if (a == null || a.length < 3 || !(a[2] instanceof String) || !enabled()) {
                            return;
                        }
                        String name = (String) a[2];
                        String option = null;
                        if ("useNewAnim".equals(name)) {
                            option = OPT_NEW_ANIM;
                        } else if (ATTR_REGISTER.equals(name)) {
                            option = OPT_REGISTER;
                        } else if (ATTR_WALLPAPER.equals(name)) {
                            option = OPT_WALLPAPER;
                        }
                        if (option != null && optOn(option)) {
                            param.setResult(Boolean.TRUE);
                            probe("os6_attr_" + name, "true");
                        }
                    } catch (Throwable t) {
                        Log.w(TAG, "desktop motion os6 attr hook failed", t);
                    }
                }
            }).size();
        }
        sb.append(" attrs=").append(attrs);

        int getters = 0;
        getters += hookBoolGetter(cfg, "C", OPT_REGISTER);
        getters += hookBoolGetter(cfg, "E", OPT_WALLPAPER);
        Class<?> vlh = find(cl, "com.android.quickstep.VivoLauncherHelper$4");
        if (vlh != null) {
            getters += hookBoolGetter(vlh, "supportLauncherScale", OPT_WALLPAPER);
            getters += hookBoolGetter(vlh, "supportWallpaperScale", OPT_WALLPAPER);
        }
        sb.append(" getters=").append(getters);

        int parsed = 0;
        try {
            Class<?> parserCls = Class.forName("org.xmlpull.v1.XmlPullParser");
            for (java.lang.reflect.Method m : cfg.getDeclaredMethods()) {
                boolean hasParser = false;
                for (Class<?> p0 : m.getParameterTypes()) {
                    if (p0 == parserCls) {
                        hasParser = true;
                        break;
                    }
                }
                if (!hasParser) {
                    continue;
                }
                try {
                    XposedBridge.hookMethod(m, new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            if (!enabled() || param.thisObject == null) {
                                return;
                            }
                            boolean a = optOn(OPT_REGISTER) && setTrue(param.thisObject, "A");
                            boolean c = optOn(OPT_WALLPAPER) && setTrue(param.thisObject, "C");
                            probe("os6_fields", "A=" + a + ",C=" + c);
                        }
                    });
                    parsed++;
                } catch (Throwable ignored) {
                }
            }
        } catch (Throwable t) {
            Log.w(TAG, "desktop motion os6 parse hook failed", t);
        }
        sb.append(" parsed=").append(parsed);
        return sb.toString();
    }

    /**
     * 预配置在 Application 创建早期就解析完了，晚于注入的 XML 钩子见不到；
     * 但配置对象之后会被反复取用。hook 真实的 LauncherEnvironmentManager.F1()，拿到
     * LauncherConfigManager 实例后把 registeranimation/wallpaperTargetAnimation 两个布尔字段
     * （本 build：L / M）置 true，并钩住 w0()/y0() 让后续读取一律为 true。
     * 字段名/方法名都是混淆产物，逐个独立 try，失败只自报不中断。
     */
    private static void hookConfig(ClassLoader cl) {
        Class<?> env = find(cl, "com.bbk.launcher2.environment.LauncherEnvironmentManager");
        if (env == null) {
            Log.i(TAG, "desktop motion config hook missing LauncherEnvironmentManager");
            return;
        }
        int n = XposedBridge.hookAllMethods(env, "F1", new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                Object cfg = param.getResult();
                if (cfg != null) {
                    patchConfig(cfg);
                }
            }
        }).size();
        Log.i(TAG, "desktop motion config hook F1=" + n);
    }

    private static final Object PATCH_LOCK = new Object();
    private static boolean sConfigPatched;

    private static void patchConfig(Object cfg) {
        if (sConfigPatched) {
            return;
        }
        synchronized (PATCH_LOCK) {
            if (sConfigPatched) {
                return;
            }
            if (!enabled()) {
                return;   // 开关关着或还没有 context：下一拍再判
            }
            Class<?> c = cfg.getClass();
            int fields = 0;
            if (setTrue(cfg, "L")) {
                fields++;
            }
            if (setTrue(cfg, "M")) {
                fields++;
            }
            int methods = 0;
            methods += hookTrue(c, "w0");
            methods += hookTrue(c, "y0");
            sConfigPatched = fields > 0 || methods > 0;
            Log.i(TAG, "desktop motion config patched class=" + c.getName()
                    + " fields=" + fields + "/2 methods=" + methods);
            if (!sConfigPatched) {
                Log.w(TAG, "desktop motion config patch failed: no known field/method");
            }
        }
    }

    private static boolean setTrue(Object cfg, String field) {
        try {
            XposedHelpers.setBooleanField(cfg, field, true);
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    private static int hookTrue(final Class<?> c, final String name) {
        try {
            return XposedBridge.hookAllMethods(c, name, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    param.setResult(Boolean.TRUE);
                    once("true|" + name, "desktop motion forced " + name + "=true class="
                            + c.getName());
                }
            }).size();
        } catch (Throwable t) {
            return 0;
        }
    }

    /**
     * 系统设置侧：把“桌面过渡动画”这一行放回「设置 → 动态效果 → 界面动态效果」。
     * 原生 EnhanceDynamicSettings.isItemEnabled() 对 order=100 要求
     * Settings.System.support_app_animation_window==1；而本机预配置 registeranimation=false，
     * 桌面启动时会把该镜像写回 0 ⇒ 入口永远不显示。这里仅在模块功能开启时放行 order=100。
     */
    private static void installSettings(ClassLoader cl) {
        Class<?> c = find(cl, "com.vivo.settings.EnhanceDynamicSettings");
        if (c == null) {
            Log.i(TAG, "desktop motion settings entry missing EnhanceDynamicSettings");
            return;
        }
        int n = XposedBridge.hookAllMethods(c, "isItemEnabled", new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                try {
                    Object[] args = param.args;
                    if (args == null || args.length != 2
                            || !(args[0] instanceof Context) || !(args[1] instanceof Integer)) {
                        return;
                    }
                    if (((Integer) args[1]).intValue() != 100) {
                        return;
                    }
                    boolean on = Prefs.featureEnabled((Context) args[0], FEATURE_ID, false);
                    once("settings-entry", "desktop motion settings entry enabled=" + (on ? 1 : 0));
                    if (on) {
                        param.setResult(Boolean.TRUE);
                    }
                } catch (Throwable t) {
                    Log.w(TAG, "desktop motion settings entry hook failed", t);
                }
            }
        }).size();
        Log.i(TAG, "desktop motion settings entry hooks=" + n);
    }

    /** 实机预配置状态：让用户看到哪些项是开的、哪些被 vivo 预置关掉。 */
    @Override
    public CharSequence noteText(Context ctx) {
        if (ctx == null) {
            return null;
        }
        PreState st = preState(ctx);
        boolean on = Prefs.featureEnabled(ctx, FEATURE_ID, false);
        return ctx.getString(R.string.desktop_pre_note_format, st.src,
                switchLabel(ctx, st.useNew),
                switchLabel(ctx, st.register),
                switchLabel(ctx, st.wallpaper),
                st.grade == null ? ctx.getString(R.string.desktop_pre_unset_grade) : st.grade,
                st.aw, st.vr, st.ee,
                ctx.getString(on ? R.string.desktop_pre_module_on
                        : R.string.desktop_pre_module_off));
    }

    /** 每个开关/档位下方的动态状态：本机预配置 → 本模块是否解锁。 */
    @Override
    public CharSequence optionNote(Context ctx, String optionKey) {
        if (ctx == null || optionKey == null) {
            return null;
        }
        PreState st = preState(ctx);
        String pre;
        String mirror = null;
        if (OPT_NEW_ANIM.equals(optionKey)) {
            pre = switchLabel(ctx, st.useNew);
        } else if (OPT_REGISTER.equals(optionKey)) {
            pre = switchLabel(ctx, st.register);
            mirror = "animation_window=" + st.aw + " · register=" + st.vr;
        } else if (OPT_WALLPAPER.equals(optionKey)) {
            pre = switchLabel(ctx, st.wallpaper);
        } else if (OPT_LEVEL.equals(optionKey)) {
            pre = st.grade == null ? ctx.getString(R.string.desktop_pre_unset_grade) : st.grade;
            mirror = "enter_exit=" + st.ee;
        } else {
            return null;
        }
        String mod = OPT_LEVEL.equals(optionKey)
                ? String.valueOf(levelOption())
                : ctx.getString(optOn(optionKey)
                ? R.string.desktop_opt_module_on : R.string.desktop_opt_module_off);
        String text = ctx.getString(R.string.desktop_opt_note_format, pre, mod);
        if (mirror != null) {
            text += ctx.getString(R.string.desktop_opt_note_mirror, mirror);
        }
        return text;
    }

    private static final class PreState {
        String src;
        String useNew;
        String register;
        String wallpaper;
        String grade;
        String aw;
        String vr;
        String ee;
    }

    private static volatile PreState sPreState;
    private static volatile long sPreAt;

    private static PreState preState(Context ctx) {
        long now = android.os.SystemClock.elapsedRealtime();
        PreState st = sPreState;
        if (st != null && now - sPreAt < 10000L) {
            return st;
        }
        st = new PreState();
        String text = null;
        java.io.File os5 = new java.io.File("/system/etc/launcher/preconfig.xml");
        if (os5.canRead()) {
            text = readText(os5);
            st.src = "preconfig.xml";
        }
        if (text == null) {
            java.io.File os4 = new java.io.File("/oem/etc/launcher/attributes_preconfig.xml");
            if (os4.canRead()) {
                text = readText(os4);
                st.src = "attributes_preconfig.xml";
            }
        }
        boolean readable = text != null && text.length() > 0;
        st.useNew = readable ? attr(text, "useNewAnim") : null;
        st.register = readable ? attr(text, "registeranimation") : null;
        st.wallpaper = readable ? attr(text, "wallpaperTargetAnimation") : null;
        st.grade = readable ? attr(text, "animationGrade") : null;
        if (st.src == null) {
            st.src = ctx.getString(R.string.desktop_pre_unreadable);
        }
        st.aw = intLabel(ctx, android.provider.Settings.System.getInt(
                ctx.getContentResolver(), "support_app_animation_window", Integer.MIN_VALUE));
        st.vr = intLabel(ctx, android.provider.Settings.Secure.getInt(
                ctx.getContentResolver(), "vivo_register_animation", Integer.MIN_VALUE));
        st.ee = intLabel(ctx, android.provider.Settings.System.getInt(
                ctx.getContentResolver(), "enter_exit_app_animation", Integer.MIN_VALUE));
        sPreState = st;
        sPreAt = now;
        return st;
    }

    private static String rawOption(String key) {
        Context ctx = currentApp();
        String src = "app";
        if (ctx == null) {
            ctx = systemContext();
            src = "sys";
        }
        if (ctx == null) {
            return "noctx";
        }
        try {
            return src + "=" + android.provider.Settings.Global.getString(
                    ctx.getContentResolver(),
                    Prefs.optionKey(FEATURE_ID, key));
        } catch (Throwable t) {
            return src + "=err:" + t.getClass().getSimpleName();
        }
    }

    private static boolean optOn(String key) {
        Context ctx = currentApp();
        if (ctx == null) {
            ctx = systemContext();
        }
        if (ctx == null) {
            return true;
        }
        return !"0".equals(Prefs.option(ctx, FEATURE_ID, key, "1"));
    }

    private static String switchLabel(Context ctx, String value) {
        if (value == null || value.length() == 0) {
            return ctx.getString(R.string.desktop_pre_unset);
        }
        return "true".equalsIgnoreCase(value)
                ? ctx.getString(R.string.desktop_pre_on)
                : ctx.getString(R.string.desktop_pre_off);
    }

    private static String intLabel(Context ctx, int value) {
        return value == Integer.MIN_VALUE
                ? ctx.getString(R.string.desktop_pre_unset_value)
                : String.valueOf(value);
    }

    private static String readText(java.io.File f) {
        try (java.io.FileInputStream in = new java.io.FileInputStream(f)) {
            byte[] buf = new byte[Math.min((int) f.length(), 256 * 1024)];
            int n = in.read(buf);
            return n <= 0 ? null : new String(buf, 0, n, "UTF-8");
        } catch (Throwable t) {
            return null;
        }
    }

    private static String attr(String text, String name) {
        try {
            java.util.regex.Matcher m = java.util.regex.Pattern
                    .compile(name + "\\s*=\\s*\"([^\"]*)\"").matcher(text);
            return m.find() ? m.group(1) : null;
        } catch (Throwable t) {
            return null;
        }
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

    private XC_MethodHook attrHook() {
        return new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                try {
                    Object[] args = param.args;
                    if (args == null || args.length != 2
                            || !(args[0] instanceof String) || !(args[1] instanceof String)) {
                        return;
                    }
                    String name = (String) args[1];
                    String forced = forcedValue(name);
                    if (forced == null) {
                        return;
                    }
                    boolean on = enabled();
                    once("seen|" + name, "desktop motion attr name=" + name
                            + " enabled=" + (on ? 1 : 0) + " parser=" + param.thisObject.getClass().getName());
                    if (!on) {
                        return;
                    }
                    param.setResult(forced);
                    witness(name, forced);
                } catch (Throwable t) {
                    Log.w(TAG, "desktop motion attr hook failed", t);
                }
            }
        };
    }

    /** 只认预配置里的两个属性；其余返回 null（完全放行）。 */
    private static String forcedValue(String name) {
        if (ATTR_REGISTER.equals(name) || ATTR_WALLPAPER.equals(name)) {
            return "true";
        }
        return null;
    }

    /**
     * 配置在桌面进程启动期解析，早于任何 Activity；用 ActivityThread.currentApplication()
     * 拿本体 Context（AndroidAppHelper 在该环境不可靠）。拿不到就这一拍不写、下次再判。
     */
    private static boolean enabled() {
        if (sEnabled != null) {
            return sEnabled.booleanValue();
        }
        Context ctx = currentApp();
        String src = "app";
        if (ctx == null) {
            ctx = systemContext();
            src = "system";
        }
        if (ctx == null) {
            once("enabled-none", "desktop motion enabled=unknown (no context)");
            return false;
        }
        boolean on = Prefs.featureEnabled(ctx, FEATURE_ID, false);
        sEnabled = Boolean.valueOf(on);
        once("enabled", "desktop motion enabled=" + (on ? 1 : 0) + " ctx=" + src);
        return on;
    }

    private static Context currentApp() {
        try {
            Class<?> at = Class.forName("android.app.ActivityThread");
            Object app = at.getMethod("currentApplication").invoke(null);
            return app instanceof Context ? (Context) app : null;
        } catch (Throwable t) {
            return null;
        }
    }

    /** 极早期（ContentProvider/Application 构造期）没有 initial application，用系统上下文读全局开关。 */
    private static Context systemContext() {
        try {
            Class<?> at = Class.forName("android.app.ActivityThread");
            Object thread = at.getMethod("currentActivityThread").invoke(null);
            if (thread == null) {
                return null;
            }
            Object ctx = at.getMethod("getSystemContext").invoke(thread);
            return ctx instanceof Context ? (Context) ctx : null;
        } catch (Throwable t) {
            return null;
        }
    }

    private static void witness(String name, String value) {
        Integer n = FORCED.get(name);
        int count = n == null ? 1 : n.intValue() + 1;
        FORCED.put(name, Integer.valueOf(count));
        if (count == 1) {
            Log.i(TAG, "desktop motion xml name=" + name + " -> " + value + " (n=" + count + ")");
        }
    }

    private static void once(String key, String line) {
        if (WITNESS_SEEN.size() <= 16 && WITNESS_SEEN.add(key)) {
            Log.i(TAG, line);
        }
    }
}
