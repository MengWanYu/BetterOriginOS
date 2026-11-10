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

    /** 本机设置里没有“桌面过渡动画”入口，档位放到卡片里；写入系统的 enter_exit_app_animation。 */
    @Override
    protected List<FeatureOption> buildOptions() {
        List<FeatureOption> list = new ArrayList<FeatureOption>();
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
        hookConfig(cl);
    }

    /**
     * 主锚点（实测必需）：预配置在 Application 创建早期就解析完了，晚于注入的 XML 钩子见不到；
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
