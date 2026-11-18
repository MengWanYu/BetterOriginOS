package com.betteroriginos.feature.installer;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.betteroriginos.R;
import com.betteroriginos.core.Feature;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

/**
 * 系统安装器（com.android.packageinstaller）净化。
 *
 * vivo 在 AOSP 安装器上叠了“安全守护”流程：adb 安装会弹出 PackageInterceptActivity
 * 要求点“继续安装”，普通安装也会先走联网检测/推荐。这里：
 * 1. adb 安装的拦截页直接放行（AdbInstaller.c() = setPermissionsResult + finish）；
 * 2. 关闭身份验证（S0.M0.k0()=true，等价 persist.vivo.install.account.verify=0）；
 * 3. 关闭安装推荐（S0.M0.E0()=true）。
 *
 * 类名/字段名来自 12.4.1.10.0（work/pkginstaller/src）；升级后缺失会打日志并回退原生。
 */
public final class InstallerGuardFeature extends Feature {
    private static final String TAG = "BetterOriginOS";
    private static final String FEATURE_ID = "installer_guard";
    private static final String INSTALLER = "com.android.packageinstaller";

    private static volatile Boolean sEnabled;
    private static volatile int sHooks;
    /** 已由我们放行的 adb 会话；后续同一会话的 false 一律忽略。 */
    private static volatile int sGrantedSession = -1;
    private static volatile long sGrantedAt;

    @Override
    public String id() {
        return FEATURE_ID;
    }

    @Override
    public int titleRes() {
        return R.string.feature_installer_title;
    }

    @Override
    public int summaryRes() {
        return R.string.feature_installer_summary;
    }

    @Override
    public int categoryRes() {
        return R.string.category_system;
    }

    @Override
    public boolean defaultEnabled() {
        return true;
    }

    @Override
    public int supportNoteRes(Context ctx) {
        return R.string.note_installer;
    }

    @Override
    public String[] scopes() {
        return new String[]{INSTALLER};
    }

    @Override
    public void install(ClassLoader cl) {
        install(cl, INSTALLER);
    }

    @Override
    public void install(ClassLoader cl, String packageName) {
        if (!INSTALLER.equals(packageName)) {
            return;
        }
        StringBuilder sb = new StringBuilder();
        int hooks = 0;
        hooks += hookFlag(cl, "S0.M0", "k0", "noVerify", sb);
        hooks += hookFlag(cl, "S0.M0", "E0", "noRecommend", sb);
        hooks += hookAdbContinue(cl, sb);
        hooks += hookAdbReject(cl, sb);
        hooks += hookLateAbort(cl, sb);
        hooks += hookRouteToAosp(cl, sb);
        sHooks = hooks;
        Log.i(TAG, "installer guard installed hooks=" + hooks + " by" + sb);
        probe("hooks", hooks + " by" + sb);
        hookResultWriters(cl, sb);
    }

    private static void probe(String key, String value) {
        try {
            Context ctx = currentApp();
            if (ctx != null) {
                ctx.getSharedPreferences("betteroriginos_installer", 0).edit()
                        .putString(key, value).commit();
            }
        } catch (Throwable ignored) {
        }
    }

    /**
     * 页面结束/ViewModel 收尾时会异步再写一次 setPermissionsResult(false)
     * （栈：S0.P0.h0 <- T0.g.u）。只屏蔽“我们已放行的那一个会话”的 false，
     * 用户主动取消的其它会话不受影响。
     */
    private static int hookLateAbort(ClassLoader cl, StringBuilder sb) {
        Class<?> p0 = find(cl, "S0.P0");
        if (p0 == null) {
            sb.append(" lateAbort=missing");
            return 0;
        }
        int n = XposedBridge.hookAllMethods(p0, "h0", new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                try {
                    Object[] a = param.args;
                    if (!enabled() || a == null || a.length < 3
                            || !Boolean.FALSE.equals(a[2])) {
                        return;
                    }
                    int session = sGrantedSession;
                    boolean fresh = android.os.SystemClock.elapsedRealtime() - sGrantedAt < 60000L;
                    if (fresh && session >= 0 && a[1] instanceof Integer
                            && (Integer) a[1] == session) {
                        probe("lateAbort", "suppressed " + session);
                        param.setResult(null);
                    }
                } catch (Throwable ignored) {
                }
            }
        }).size();
        sb.append(" lateAbort=").append(n);
        return n;
    }

    /** 谁在往会话里写 false：整个安装器进程里所有 setPermissionsResult(false) 都留栈。 */
    private static void hookResultWriters(ClassLoader cl, StringBuilder sb) {
        try {
            Class<?> pi = Class.forName("android.content.pm.PackageInstaller");
            int n = XposedBridge.hookAllMethods(pi, "setPermissionsResult", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    try {
                        Object[] a = param.args;
                        boolean ok = a != null && a.length >= 2 && Boolean.TRUE.equals(a[1]);
                        if (!ok) {
                            probe("rejectStack", stack());
                        }
                    } catch (Throwable ignored) {
                    }
                }
            }).size();
            sb.append(" resultWriters=").append(n);
        } catch (Throwable t) {
            sb.append(" resultWriters=err");
        }
    }

    private static String stack() {
        StringBuilder sb = new StringBuilder();
        StackTraceElement[] st = Thread.currentThread().getStackTrace();
        for (int i = 3; i < st.length && sb.length() < 900; i++) {
            sb.append(st[i].getClassName()).append('#')
                    .append(st[i].getMethodName()).append('\n');
        }
        return sb.toString();
    }

    /** 两个开关都是 static boolean：返回“关闭”的那一值。 */
    private static int hookFlag(ClassLoader cl, String className, String method,
                                String label, StringBuilder sb) {
        Class<?> cls = find(cl, className);
        if (cls == null) {
            sb.append(' ').append(label).append("=missing");
            return 0;
        }
        int n = XposedBridge.hookAllMethods(cls, method, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                if (!enabled()) {
                    return;
                }
                param.setResult(Boolean.TRUE);
            }
        }).size();
        sb.append(' ').append(label).append('=').append(n);
        return n;
    }

    /** adb 安装的“继续安装”页：活动创建完成后直接 setPermissionsResult(true) 并结束。 */
    private static int hookAdbContinue(ClassLoader cl, StringBuilder sb) {
        Class<?> act = find(cl, "com.android.packageinstaller.PackageInterceptActivity");
        if (act == null) {
            sb.append(" adbContinue=missing");
            return 0;
        }
        int n = XposedBridge.hookAllMethods(act, "onCreate", new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                try {
                    if (!(param.thisObject instanceof Activity)) {
                        return;
                    }
                    Activity activity = (Activity) param.thisObject;
                    Intent intent = activity.getIntent();
                    Object installer = findAdbInstaller(activity);
                    try {
                        activity.getSharedPreferences("betteroriginos_installer", 0).edit()
                                .putString("last", "act=" + (intent == null ? null : intent.getAction())
                                        + " extras=" + (intent == null ? null : String.valueOf(intent.getExtras()))
                                        + " adbField=" + (installer != null)
                                        + " enabled=" + enabled())
                                .commit();
                    } catch (Throwable ignored) {
                    }
                    if (!enabled() || intent == null
                            || !"adb".equals(intent.getStringExtra("install_app_from_adb"))) {
                        return;
                    }
                    if (installer == null) {
                        Log.i(TAG, "installer guard adb continue: no installer");
                        return;
                    }
                    sGrantedSession = intent.getIntExtra(
                            "android.content.pm.extra.SESSION_ID", -1);
                    sGrantedAt = android.os.SystemClock.elapsedRealtime();
                    XposedHelpers.callMethod(installer, "c");
                    Log.i(TAG, "installer guard adb continue -> permissions granted");
                } catch (Throwable t) {
                    Log.w(TAG, "installer guard adb continue failed", t);
                }
            }
        }).size();
        // onResume 不一定由子类声明；钩框架 Activity.onResume，只认这个 Activity。
        try {
            Class<?> activityCls = Class.forName("android.app.Activity");
            n += XposedBridge.hookAllMethods(activityCls, "onResume", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    try {
                        Object self = param.thisObject;
                        if (!enabled() || !(self instanceof Activity)
                                || !self.getClass().getName().endsWith("PackageInterceptActivity")) {
                            return;
                        }
                        Activity activity = (Activity) self;
                        Intent intent = activity.getIntent();
                        boolean fromAdb = intent != null
                                && "adb".equals(intent.getStringExtra("install_app_from_adb"));
                        Object installer = findAdbInstaller(activity);
                        activity.getSharedPreferences("betteroriginos_installer", 0).edit()
                                .putString("resume", "adb=" + fromAdb + " installer=" + (installer != null))
                                .commit();
                        if (fromAdb) {
                            if (installer != null) {
                                XposedHelpers.callMethod(installer, "c");
                            }
                        } else {
                            // 普通安装：等底部面板出来后自动点“继续安装”。
                            autoContinue(activity);
                        }
                    } catch (Throwable t) {
                        Log.w(TAG, "installer guard adb resume failed", t);
                    }
                }
            }).size();
        } catch (Throwable t) {
            Log.w(TAG, "installer guard resume hook failed", t);
        }
        sb.append(" adbContinue=").append(n);
        return n;
    }

    /**
     * adb 会话的拒绝源：AdbInstaller.d() 在页面 onStop 时会 setPermissionsResult(false)。
     * 我们的 c() 随后又发 true，于是“包装上了但 adb 报 User rejected”。
     * 直接让 d() 不再执行（启用本功能时），只保留 c() 的放行。
     */
    private static int hookAdbReject(ClassLoader cl, StringBuilder sb) {
        Class<?> cls = find(cl, "com.android.packageinstaller.vivo.utils.AdbInstaller");
        if (cls == null) {
            sb.append(" adbReject=missing");
            return 0;
        }
        int n = XposedBridge.hookAllMethods(cls, "d", new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                if (enabled()) {
                    probe("dHit", "skipped");
                    param.setResult(null);
                }
            }
        }).size();
        sb.append(" adbReject=").append(n);
        return n;
    }

    /**
     * 普通安装的拦截页直接换成 AOSP 的 PackageInstallerActivity：
     * 不弹 vivo 的“安全守护/未备案/继续安装”页，也不做联网检测。
     * adb 安装仍走 intercept + AdbInstaller 自动放行（原生对 adb 本来就没有 UI）。
     */
    private static int hookRouteToAosp(ClassLoader cl, StringBuilder sb) {
        final Class<?> google = find(cl,
                "com.google.packageinstaller.androidq.PackageInstallerActivity");
        final Class<?> vivo = find(cl, "com.android.packageinstaller.PackageInstallerActivity");
        final Class<?> target = google != null ? google : vivo;
        if (target == null) {
            sb.append(" route=missing");
            return 0;
        }
        Class<?> activityCls;
        try {
            activityCls = Class.forName("android.app.Activity");
        } catch (Throwable t) {
            sb.append(" route=missing");
            return 0;
        }
        XC_MethodHook hook = new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                try {
                    if (!enabled()) {
                        return;
                    }
                    Object[] a = param.args;
                    if (a == null || !(a[0] instanceof Intent)) {
                        return;
                    }
                    Intent intent = (Intent) a[0];
                    android.content.ComponentName cn = intent.getComponent();
                    if (cn == null || !cn.getClassName().endsWith("PackageInterceptActivity")) {
                        return;
                    }
                    // 已带 session id 的是 adb/系统已建会话的确认流程，必须留给 intercept 的
                    // AdbInstaller 去 setPermissionsResult(true)；改成 AOSP 页会被判成拒绝。
                    if (intent.getIntExtra("android.content.pm.extra.SESSION_ID", -1) >= 0
                            || "adb".equals(intent.getStringExtra("install_app_from_adb"))) {
                        return;
                    }
                    intent.setComponent(new android.content.ComponentName(
                            cn.getPackageName(), target.getName()));
                } catch (Throwable t) {
                    Log.w(TAG, "installer guard route failed", t);
                }
            }
        };
        int n = XposedBridge.hookAllMethods(activityCls, "startActivity", hook).size();
        n += XposedBridge.hookAllMethods(activityCls, "startActivityForResult", hook).size();
        sb.append(" route=").append(n).append('(').append(target.getSimpleName()).append(')');
        return n;
    }

    /** AdbInstaller 构造完就相当于“adb 安装流程已就绪”，直接放行；非 adb 时 c() 自己会返回。 */
    private static int hookAdbInstallerCtor(ClassLoader cl, StringBuilder sb) {
        Class<?> cls = find(cl, "com.android.packageinstaller.vivo.utils.AdbInstaller");
        if (cls == null) {
            sb.append(" adbCtor=missing");
            return 0;
        }
        int n = XposedBridge.hookAllConstructors(cls, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                try {
                    if (!enabled() || param.thisObject == null) {
                        return;
                    }
                    XposedHelpers.callMethod(param.thisObject, "c");
                    Log.i(TAG, "installer guard adb ctor continue");
                } catch (Throwable t) {
                    Log.w(TAG, "installer guard adb ctor continue failed", t);
                }
            }
        }).size();
        sb.append(" adbCtor=").append(n);
        return n;
    }

    private static void autoContinue(final Activity activity) {
        final Handler h = new Handler(Looper.getMainLooper());
        h.postDelayed(new Runnable() {
            private int tries;

            @Override
            public void run() {
                tries++;
                View button = findBottomContinue(activity);
                if (button == null) {
                    button = findContinueButton(activity.getWindow().getDecorView());
                }
                if (button != null) {
                    button.performClick();
                    Log.i(TAG, "installer guard auto continue clicked");
                    return;
                }
                if (tries < 12) {
                    h.postDelayed(this, 400L);
                }
            }
        }, 400L);
    }

    /** 优先用 BottomView.getContinueButton()：VButton 的文案不在 TextView.getText() 里。 */
    private static View findBottomContinue(Activity activity) {
        try {
            View bottom = findByClassSuffix(activity.getWindow().getDecorView(), "BottomView");
            if (bottom == null) {
                return null;
            }
            Object button = XposedHelpers.callMethod(bottom, "getContinueButton");
            if (button instanceof View) {
                View v = (View) button;
                if (v.isShown() && v.isEnabled()) {
                    return v;
                }
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private static View findByClassSuffix(View v, String suffix) {
        if (v.getClass().getName().endsWith(suffix)) {
            return v;
        }
        if (v instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) v;
            for (int i = 0; i < group.getChildCount(); i++) {
                View found = findByClassSuffix(group.getChildAt(i), suffix);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private static View findContinueButton(View v) {
        if (v instanceof TextView) {
            CharSequence text = ((TextView) v).getText();
            if (text != null && text.toString().contains("继续安装") && v.isShown()) {
                // 文案节点自己不一定是可点的，往上找第一个 clickable 的容器。
                View target = v;
                for (int i = 0; i < 6 && target != null; i++) {
                    if (target.isClickable()) {
                        return target;
                    }
                    target = target.getParent() instanceof View ? (View) target.getParent() : null;
                }
                return v;
            }
        }
        if (v instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) v;
            for (int i = 0; i < group.getChildCount(); i++) {
                View found = findContinueButton(group.getChildAt(i));
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private static Object findAdbInstaller(Activity activity) {
        Class<?> c = activity.getClass();
        while (c != null) {
            for (java.lang.reflect.Field f : c.getDeclaredFields()) {
                if (f.getType() != null
                        && f.getType().getName().endsWith("AdbInstaller")) {
                    try {
                        f.setAccessible(true);
                        return f.get(activity);
                    } catch (Throwable ignored) {
                    }
                }
            }
            c = c.getSuperclass();
        }
        return null;
    }

    private static boolean enabled() {
        Boolean cached = sEnabled;
        if (cached != null) {
            return cached.booleanValue();
        }
        Context ctx = currentApp();
        if (ctx == null) {
            return true;
        }
        boolean on = com.betteroriginos.core.Prefs.featureEnabled(ctx, FEATURE_ID, true);
        sEnabled = Boolean.valueOf(on);
        Log.i(TAG, "installer guard enabled=" + (on ? 1 : 0));
        return on;
    }

    private static Context currentApp() {
        try {
            Object app = Class.forName("android.app.ActivityThread")
                    .getMethod("currentApplication").invoke(null);
            return app instanceof Context ? (Context) app : null;
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
}
