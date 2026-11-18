package com.betteroriginos.feature.updater;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.Log;
import android.widget.Toast;

import com.betteroriginos.R;
import com.betteroriginos.core.Feature;
import com.betteroriginos.core.FeatureOption;
import com.betteroriginos.core.Prefs;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

/**
 * 系统升级防自动重启（OriginOS 4/5 的 com.bbk.updater）。
 *
 * 逆向结论（详见 out/LOG-1112-UPDATER-REVERSE.md）：
 * - 下载完成后 PopDialogUtils.showRebootInduceDialog(context, ..., z5, z6) 建重启对话框；
 *   z5=true 时走进度倒计时（PopDialogUtils.countdownDialog，10 秒）。
 * - 倒计时结束发 DialogEvent(13)，BaseUpdateStrategy 收到后调 n0.a.C(ctx, UPDATE_NOW_COUNT)；
 *   正按钮“立即重启”发 DialogEvent(10) -> n0.a.C(ctx, UPDATE_NOW_DIALOG)。
 * - n0.a.C/D 最终在线程里 PowerManager.reboot("reboot-ab-update")。
 * - 另外还有 CommonUtils.shutdown（关机）与 RecoverySystem.installPackage（进 recovery）两条旁路。
 *
 * 本 Feature 只拦“重启相关”，不拦下载/安装：
 * 1. showRebootInduceDialog 强制 z5=false，不再倒计时；
 * 2. countdownDialog / AB_REBOOT 的 countdown 直接跳过（兜底）；
 * 3. n0.a.D 与 PowerManager.reboot 拦截，按钮点了也不会重启；
 * 4. CommonUtils.shutdown / RecoverySystem.installPackage 拦截，避免关机/进 recovery；
 * 5. UpdateTipsService 的夜间倒计时与 UpdateManager 的定时重启闹钟一并静音。
 */
public final class UpdaterGuardFeature extends Feature {
    private static final String TAG = "BetterOriginOS";
    private static final String FEATURE_ID = "updater_guard";
    private static final String UPDATER = "com.bbk.updater";
    private static final String COTA = "com.vivo.cota";

    private static volatile Boolean sEnabled;
    private static final Set<String> WITNESS = Collections.synchronizedSet(new HashSet<String>());
    private static volatile int sInstalledHooks;
    private static final java.util.concurrent.atomic.AtomicInteger BLOCKS =
            new java.util.concurrent.atomic.AtomicInteger();
    private static volatile boolean sHeartbeatStarted;

    @Override
    public String id() {
        return FEATURE_ID;
    }

    @Override
    public int titleRes() {
        return R.string.feature_updater_guard_title;
    }

    @Override
    public int summaryRes() {
        return R.string.feature_updater_guard_summary;
    }

    @Override
    public int categoryRes() {
        return R.string.category_system;
    }

    /** 防掉 root 的保险，默认开；用户可在卡片里关掉。 */
    @Override
    public boolean defaultEnabled() {
        return true;
    }

    @Override
    public int supportNoteRes(Context ctx) {
        return R.string.note_updater_guard;
    }

    @Override
    public int actionLabelRes() {
        return R.string.feature_updater_center;
    }

    @Override
    public void onAction(Context ctx) {
        com.betteroriginos.ui.UpdaterActivity.start(ctx);
    }

    @Override
    public String[] scopes() {
        return new String[]{UPDATER, COTA};
    }

    private static final String OPT_DOWNLOAD_ONLY = "download_only";
    private static final String OPT_CUSTOM_UI = "custom_ui";
    private static final String OPT_EXTERNAL_INSTALL = "external_install";
    private static final String OPT_STATUS_NOTI = "status_notification";
    private static final String CHANNEL_ID = "betteroriginos_updater";
    private static final int NOTI_ID = 0x424f5441;
    private static final String STATE_CHANNEL_ID = "betteroriginos_upgrade_state";
    private static final int STATE_NOTI_ID = 0x424f5453;

    @Override
    protected List<FeatureOption> buildOptions() {
        List<FeatureOption> list = new ArrayList<FeatureOption>();
        list.add(FeatureOption.toggle(
                OPT_DOWNLOAD_ONLY,
                R.string.option_updater_download_only_title,
                R.string.option_updater_download_only_summary,
                true));
        list.add(FeatureOption.toggle(
                OPT_CUSTOM_UI,
                R.string.option_updater_custom_ui_title,
                R.string.option_updater_custom_ui_summary,
                true));
        list.add(FeatureOption.toggle(
                OPT_EXTERNAL_INSTALL,
                R.string.option_updater_external_install_title,
                R.string.option_updater_external_install_summary,
                true));
        list.add(FeatureOption.toggle(
                OPT_STATUS_NOTI,
                R.string.option_updater_status_noti_title,
                R.string.option_updater_status_noti_summary,
                true));
        return list;
    }

    @Override
    public void install(ClassLoader cl) {
        installUpdater(cl);
    }

    @Override
    public void install(ClassLoader cl, String packageName) {
        if (COTA.equals(packageName)) {
            int n = hookPowerGuard();
            Log.i(TAG, "updater guard installed pkg=" + packageName + " power=" + n);
            return;
        }
        if (UPDATER.equals(packageName)) {
            installUpdater(cl);
        }
    }

    private static void installUpdater(ClassLoader cl) {
        sAppLoader = cl;
        int hooks = 0;
        StringBuilder sb = new StringBuilder();

        // 1) 直接重启点：n0.a.D(Context, UpdateMethod, boolean) -> PowerManager.reboot
        //    n0.a 是混淆名，只在当前桌面/升级器版本有效；找不到就靠下一层 PowerManager.
        Class<?> rebootHelper = find(cl, "n0.a");
        if (rebootHelper == null) {
            sb.append(" n0.a=missing");
        } else {
            int n = XposedBridge.hookAllMethods(rebootHelper, "D", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    if (guardEnabled()) {
                        block("n0.a.D");
                        Object[] a = param.args;
                        if (a != null && a.length > 0 && a[0] instanceof Context) {
                            toast((Context) a[0], "已拦截重启，升级包与系统都保持不变");
                        }
                        param.setResult(null);
                    }
                }
            }).size();
            hooks += n;
            sb.append(" n0.a.D=").append(n);
        }
        // OS6(4.9.29.x)：重启入口搬到 l0.a.F(Context, UpdateMethod, boolean)
        Class<?> reboot6 = find(cl, "l0.a");
        if (reboot6 == null) {
            sb.append(" l0.a=missing");
        } else {
            int n6 = XposedBridge.hookAllMethods(reboot6, "F", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    Object[] a = param.args;
                    if (guardEnabled() && a != null && a.length >= 1 && a[0] instanceof Context) {
                        block("l0.a.F");
                        toast((Context) a[0], "已拦截重启，升级包与系统都保持不变");
                        param.setResult(null);
                    }
                }
            }).size();
            hooks += n6;
            sb.append(" l0.a.F=").append(n6);
        }

        hooks += hookPowerGuard();

        // 2) 重启对话框：强制 z5=false，去掉 10 秒倒计时。
        Class<?> dialogs = find(cl, "com.bbk.updater.utils.PopDialogUtils");
        if (dialogs == null) {
            sb.append(" PopDialogUtils=missing");
        } else {
            int n = XposedBridge.hookAllMethods(dialogs, "showRebootInduceDialog",
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            if (!guardEnabled()) {
                                return;
                            }
                            Object[] a = param.args;
                            if (a != null && a.length >= 5 && a[4] instanceof Boolean) {
                                a[4] = Boolean.FALSE;
                                once("dialog", "updater guard countdown=off dialog");
                            }
                        }
                    }).size();
            hooks += n;
            sb.append(" rebootDialog=").append(n);

            int n2 = XposedBridge.hookAllMethods(dialogs, "countdownDialog",
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            if (guardEnabled()) {
                                block("countdownDialog");
                                param.setResult(null);
                            }
                        }
                    }).size();
            hooks += n2;
            sb.append(" countdownDialog=").append(n2);

            int n3 = XposedBridge.hookAllMethods(dialogs, "countdown",
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            if (!guardEnabled()) {
                                return;
                            }
                            Object[] a = param.args;
                            if (a != null && a.length >= 5 && a[4] != null
                                    && a[4].toString().contains("AB_REBOOT")) {
                                block("countdown(ab)");
                                param.setResult(null);
                            }
                        }
                    }).size();
            hooks += n3;
            sb.append(" countdown=").append(n3);

            // 2b) “发现新版本/下载完成”的安装诱导对话框：只下载模式下不再劝装。
            int n4 = XposedBridge.hookAllMethods(dialogs, "showInstallInduceDialog",
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            try {
                                Object[] a = param.args;
                                if (a == null || a.length == 0 || !(a[0] instanceof Context)) {
                                    return;
                                }
                                if (guardEnabled() && downloadOnly((Context) a[0])) {
                                    block("installInduce");
                                    param.setResult(null);
                                }
                            } catch (Throwable t) {
                                Log.w(TAG, "updater guard install induce hook failed", t);
                            }
                        }
                    }).size();
            hooks += n4;
            sb.append(" installInduce=").append(n4);
        }

        // 3) 关机 / 进 recovery 两条旁路。
        Class<?> common = find(cl, "com.bbk.updater.utils.CommonUtils");
        if (common != null) {
            int n = XposedBridge.hookAllMethods(common, "shutdown", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    if (guardEnabled()) {
                        block("shutdown");
                        param.setResult(null);
                    }
                }
            }).size();
            hooks += n;
            sb.append(" shutdown=").append(n);
        } else {
            sb.append(" CommonUtils=missing");
        }

        // 4) 通知里“安装于夜间”的倒计时：冻结 tick、屏蔽 finish，不再自动进夜间安装。
        Class<?> tipsTimer = find(cl, "com.bbk.updater.remote.UpdateTipsService$c");
        if (tipsTimer != null) {
            int n = XposedBridge.hookAllMethods(tipsTimer, "onTick", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    if (guardEnabled()) {
                        once("night-tick", "updater guard night countdown frozen");
                        param.setResult(null);
                    }
                }
            }).size();
            n += XposedBridge.hookAllMethods(tipsTimer, "onFinish", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    if (guardEnabled()) {
                        once("night-finish", "updater guard night auto-install blocked");
                        param.setResult(null);
                    }
                }
            }).size();
            hooks += n;
            sb.append(" nightTimer=").append(n);
        }

        // 5) 装完后定时弹“重启倒计时”的 Alarm（有调用则拦）。
        Class<?> updateManager = find(cl, "com.bbk.updater.remote.abinstall.UpdateManager");
        if (updateManager != null) {
            int n = XposedBridge.hookAllMethods(updateManager, "setRebootInduceAlarmClock",
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            if (guardEnabled()) {
                                block("rebootAlarm");
                                param.setResult(null);
                            }
                        }
                    }).size();
            hooks += n;
            sb.append(" rebootAlarm=").append(n);
        }

        // 5b) 定制仅下载界面：vivo 自带 VGC 定制模式，一处开关改全链路文案与流程。
        hooks += hookCustomUi(cl, sb);
        // 5c) 外部安装模式：不把 updater 注册成 UpdateEngine 回调客户端。
        hooks += hookExternalBind(cl, sb);
        // 5d) 版本伪装：只在“获取最新版本全量包”那一次请求期间生效。
        hooks += hookVersionSpoof(cl, sb);
        // 5e) 直链回退：服务器拒绝给“当前版本”的包时，用上次解析出的签名直链交给原生下载器。
        hooks += hookDirectUrl(cl, sb);

        // 6) 只下载不安装：目录改到 Download、拦 startInstall、完成后通知路径。
        hooks += hookDownloadOnly(cl, common, sb);
        // 7) 安装包保留：拦住 updater 的“成功后清理”，避免递归删掉整个 Download。
        hooks += hookKeepPackage(cl, sb);

        sInstalledHooks = hooks;
        Log.i(TAG, "updater guard installed pkg=" + UPDATER + " hooks=" + hooks + " by" + sb);
        guardEnabled();   // 安装期自报一次 enabled=0/1（取不到 Context 时首个拦截点会再报）
        startHeartbeat();
        startCommandLoop();
    }

    /** 调试模式下每 60 秒一条低频心跳；开关关闭后静默，不需要重启进程。 */
    private static void startHeartbeat() {
        if (sHeartbeatStarted) {
            return;
        }
        sHeartbeatStarted = true;
        final Handler h = new Handler(Looper.getMainLooper());
        h.postDelayed(new Runnable() {
            @Override
            public void run() {
                try {
                    Context ctx = currentApp();
                    if (ctx == null) {
                        ctx = systemContext();
                    }
                    if (ctx != null && Prefs.debugEnabled(ctx)) {
                        Log.i(TAG, "updater guard alive pid=" + android.os.Process.myPid()
                                + " hooks=" + sInstalledHooks
                                + " enabled=" + (guardEnabled() ? 1 : 0)
                                + " blocks=" + BLOCKS.get());
                    }
                } catch (Throwable ignored) {
                }
                h.postDelayed(this, 60000L);
            }
        }, 10000L);
    }

    /**
     * vivo 自带的“定制（VGC）只下载”模式：v0.a.M() 为 true 时，主按钮变「仅下载」、
     * 通知按钮变「仅下载/后台下载」、下载完成不自动进安装（UpdatePresenter 走 TO_INSTALL）。
     * 直接借用这一处开关，比自己改写升级页控件稳，也和 vivo 全链路文案一致。
     */
    private static int hookCustomUi(ClassLoader cl, StringBuilder sb) {
        Class<?> vgc = android.os.Build.VERSION.SDK_INT >= 36 ? null : find(cl, "v0.a");
        int n = 0;
        if (vgc == null) {
            sb.append(" customUi=skip");
        } else {
            n = XposedBridge.hookAllMethods(vgc, "M", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    if (!(param.method instanceof java.lang.reflect.Method)
                            || ((java.lang.reflect.Method) param.method).getReturnType() != boolean.class) {
                        return;
                    }
                    if (guardEnabled() && optionOn(OPT_CUSTOM_UI)) {
                        param.setResult(Boolean.TRUE);
                        once("customUi", "updater guard custom-ui download-only");
                    }
                }
            }).size();
            sb.append(" customUi=").append(n);
        }
        // OS6(4.9.29.x)：定制模式在 u0.a.F(int)（返回 customtype，3 = 定制仅下载）
        Class<?> vgc6 = find(cl, "u0.a");
        if (vgc6 != null) {
            int n6 = XposedBridge.hookAllMethods(vgc6, "F", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    Object[] a = param.args;
                    if (guardEnabled() && optionOn(OPT_CUSTOM_UI)
                            && a != null && a.length == 1 && a[0] instanceof Integer) {
                        param.setResult(Integer.valueOf(3));
                        once("customUi6", "updater guard custom-ui(os6) download-only");
                    }
                }
            }).size();
            n += n6;
            sb.append(" customUi6=").append(n6);
        }
        return n;
    }

    /**
     * 外部安装模式：本进程不把 updater 注册成 UpdateEngine 的回调客户端。
     * update_engine 只有在有人 bind 时才持有客户端 binder；不绑，updater 进程被杀
     * 就不会以“客户端死亡”为由取消别人（native client / 第三方工具）发起的 apply。
     * applyPayload 本身是独立 binder 调用，不依赖回调注册。
     */
    private static int hookExternalBind(ClassLoader cl, StringBuilder sb) {
        int n = 0;
        try {
            Class<?> engine = Class.forName("android.os.UpdateEngine", false, cl);
            n = XposedBridge.hookAllMethods(engine, "bind", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    if (guardEnabled() && optionOn(OPT_EXTERNAL_INSTALL)) {
                        once("extBind", "updater guard external-install engine-callback unbind");
                        param.setResult(null);
                    }
                }
            }).size();
        } catch (Throwable t) {
            Log.w(TAG, "updater guard external install hook failed", t);
        }
        sb.append(" extBind=").append(n);
        return n;
    }

    /** 模块类的默认 ClassLoader 看不到宿主类，查宿主类一律用安装时拿到的 app loader。 */
    private static volatile ClassLoader sAppLoader;

    private static Class<?> load(String name) {
        ClassLoader cl = sAppLoader;
        Class<?> c = cl == null ? null : XposedHelpers.findClassIfExists(name, cl);
        if (c == null) {
            try {
                c = Class.forName(name);
            } catch (Throwable ignored) {
            }
        }
        return c;
    }

    private static volatile boolean sCommandLoop;
    private static volatile long sLastCmdSeq = -1;
    private static volatile String sLastCmdRaw;
    private static volatile long sLastPublishAt;
    private static volatile String sLastStateLine;
    private static volatile String sSpoofSoft;
    private static volatile String sSpoofFull;
    private static volatile String sSpoofCompose;
    private static volatile String sLastFetch = "";
    private static volatile long sLastFetchTs;
    private static volatile String sDirectUrl;
    private static final java.util.concurrent.atomic.AtomicBoolean FETCHING =
            new java.util.concurrent.atomic.AtomicBoolean();

    /**
     * 直链回退：原生下载流程只认 /pk/redir 形式的 downloadURL。
     * 服务器不给“当前版本”的包时，把上次解析出的 sysuptxdl 签名直链伪装成 redir 结果，
     * 后面的下载/续传/进度全部沿用原生下载服务。
     */
    private static int hookDirectUrl(ClassLoader cl, StringBuilder sb) {
        Class<?> hu = find(cl, "com.bbk.updater.utils.HttpUtils");
        Class<?> su = find(cl, "com.bbk.updater.utils.SecurityUtils");
        int n = 0;
        if (hu != null) {
            n += XposedBridge.hookAllMethods(hu, "getUrlFromGet", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    Object[] a = param.args;
                    if (sDirectUrl != null && a != null && a.length == 1
                            && a[0] instanceof String && ((String) a[0]).startsWith("direct:")) {
                        param.setResult(((String) a[0]).substring(7));
                    }
                }
            }).size();
            n += XposedBridge.hookAllMethods(hu, "getResponse", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    Object[] a = param.args;
                    if (sDirectUrl != null && a != null && a.length >= 2 && a[1] instanceof String
                            && sDirectUrl.equals(a[1])) {
                        param.setResult("DIRECTDL");
                    }
                }
            }).size();
        }
        if (su != null) {
            n += XposedBridge.hookAllMethods(su, "decryptResponse", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    Object[] a = param.args;
                    String url = sDirectUrl;
                    if (url != null && a != null && a.length == 2 && "DIRECTDL".equals(a[1])) {
                        param.setResult("{\"data\":\"" + url + "\",\"retcode\":200}");
                    }
                }
            }).size();
        }
        sb.append(" directUrl=").append(n);
        return n;
    }

    /**
     * 版本伪装：正在检查更新时把“当前版本”报低一版（上次成功升级的版本），
     * 服务器才会在“已经最新”时仍然返回当前版本的完整包。
     * 只在 fetch 的那一次请求窗口里非空，其余时刻完全不干预。
     */
    private static int hookVersionSpoof(ClassLoader cl, StringBuilder sb) {
        Class<?> vu = find(cl, "com.bbk.updater.utils.VersionUtils");
        if (vu == null) {
            sb.append(" spoof=missing");
            return 0;
        }
        int n = hookSpoof(vu, "getSoftVersion", 0);
        n += hookSpoof(vu, "getFullVersion", 1);
        n += hookSpoof(vu, "composeVersionString", 2);
        sb.append(" spoof=").append(n);
        return n;
    }

    private static int hookSpoof(Class<?> target, String method, final int which) {
        return XposedBridge.hookAllMethods(target, method, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                String value = which == 0 ? sSpoofSoft : which == 1 ? sSpoofFull : sSpoofCompose;
                if (value != null) {
                    param.setResult(value);
                    once("spoofHit" + which, "updater guard spoof hit=" + which);
                }
            }
        }).size();
    }

    /** 诊断：不看已有文件，直接用保存的直链走一遍原生下载（验证 shim 与任务链路）。 */
    private static void directDownloadProbe(final Context ctx) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    Class<?> pu = load("com.bbk.updater.utils.PrefsUtils");
                    String url = str(XposedHelpers.callStaticMethod(pu, "getString", ctx,
                            "current_download_real_url", ""));
                    long len = headLength(url);
                    String name = url.substring(0, url.indexOf('?') > 0 ? url.indexOf('?') : url.length());
                    name = name.substring(name.lastIndexOf('/') + 1);
                    Class<?> vu = load("com.bbk.updater.utils.VersionUtils");
                    String soft = str(XposedHelpers.callStaticMethod(vu, "getSoftVersion"));
                    Object info = XposedHelpers.newInstance(load("com.bbk.updater.bean.UpdateInfo"));
                    call(info, "setVersion", soft);
                    call(info, "setFileName", name);
                    call(info, "setFileLength", Long.valueOf(len));
                    call(info, "setDownloadURL", "direct:" + url);
                    call(info, "setPackageType", "OTA_FOTA");
                    call(info, "setTimeStamp", "0");
                    call(info, "setActivePackage", Boolean.TRUE);
                    sDirectUrl = url;
                    startNativeDownload(ctx, info);
                    Log.i(TAG, "updater guard dlprobe started name=" + name + " size=" + len);
                } catch (Throwable t) {
                    Log.w(TAG, "updater guard dlprobe failed", t);
                }
            }
        }, "updater-dlprobe").start();
    }

    private static void pauseDownload(Context ctx) {
        try {
            Object mgr = XposedHelpers.callStaticMethod(load("g0.a"), "E", ctx);
            call(mgr, "q0", "ota_pacakge", Boolean.TRUE);
            Log.i(TAG, "updater guard dl pause sent");
        } catch (Throwable t) {
            Log.w(TAG, "updater guard dl pause failed", t);
        }
    }

    private static void cleanDownload(Context ctx) {
        try {
            Object mgr = XposedHelpers.callStaticMethod(load("g0.a"), "E", ctx);
            call(mgr, "p", "ota_pacakge");
            File dir = new File(downloadDir());
            File[] files = dir.listFiles();
            int removed = 0;
            if (files != null) {
                for (File f : files) {
                    String n = f.getName();
                    if (f.isFile() && (n.endsWith(".zip") || n.endsWith(".temp") || n.endsWith(".tmp"))) {
                        // 只删本次测试产生的（大小与服务器包一致或明显是临时文件）。
                        if (n.contains("202610262028094ad48fade6c32367fe1dbdc1f86c02f7")
                                && f.length() < 8053346114L) {
                            if (f.delete()) {
                                removed++;
                            }
                        }
                    }
                }
            }
            Log.i(TAG, "updater guard dl clean removed=" + removed);
        } catch (Throwable t) {
            Log.w(TAG, "updater guard dl clean failed", t);
        }
    }

    /** 诊断：升级器上次解析出的真实下载直链是否还有效（HEAD/Range 探测）。 */
    private static void testStoredUrl(final Context ctx) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                java.net.HttpURLConnection c = null;
                try {
                    String url = str(XposedHelpers.callStaticMethod(
                            load("com.bbk.updater.utils.PrefsUtils"),
                            "getString", ctx, "current_download_real_url", ""));
                    if (url.length() == 0) {
                        Log.i(TAG, "updater guard urltest no-url");
                        return;
                    }
                    c = (java.net.HttpURLConnection) new java.net.URL(url).openConnection();
                    c.setRequestMethod("GET");
                    c.setRequestProperty("Range", "bytes=0-0");
                    c.setConnectTimeout(10000);
                    c.setReadTimeout(10000);
                    int code = c.getResponseCode();
                    Log.i(TAG, "updater guard urltest code=" + code
                            + " range=" + c.getHeaderField("Content-Range")
                            + " len=" + c.getHeaderField("Content-Length"));
                } catch (Throwable t) {
                    Log.w(TAG, "updater guard urltest failed", t);
                } finally {
                    if (c != null) {
                        c.disconnect();
                    }
                }
            }
        }, "updater-urltest").start();
    }

    /** 一次“获取最新版本全量包”：伪装版本 → 走升级器自己的检查请求 → 保存结果 → 启动下载。 */
    private static void fetchFullPackage(final Context ctx, final boolean startDownload) {
        if (!FETCHING.compareAndSet(false, true)) {
            return;
        }
        new Thread(new Runnable() {
            @Override
            public void run() {
                String result;
                try {
                    result = doFetch(ctx, startDownload);
                } catch (Throwable t) {
                    result = "error:" + t.getClass().getSimpleName();
                    Log.w(TAG, "updater guard fetch failed", t);
                } finally {
                    sSpoofSoft = null;
                    sSpoofFull = null;
                    sSpoofCompose = null;
                    FETCHING.set(false);
                }
                sLastFetch = result;
                sLastFetchTs = System.currentTimeMillis();
                Log.i(TAG, "updater guard fetch " + result);
                publishState(ctx);
            }
        }, "updater-fetch").start();
    }

    private static String doFetch(Context ctx, boolean startDownload) {
        Class<?> vu = load("com.bbk.updater.utils.VersionUtils");
        Class<?> pu = load("com.bbk.updater.utils.PrefsUtils");
        String soft = str(XposedHelpers.callStaticMethod(vu, "getSoftVersion"));
        String full = str(XposedHelpers.callStaticMethod(vu, "getFullVersion"));
        String compose = str(XposedHelpers.callStaticMethod(vu, "composeVersionString"));
        String prev = str(XposedHelpers.callStaticMethod(pu, "getString", ctx,
                "updater_success_version", ""));
        if (prev.length() == 0) {
            prev = str(XposedHelpers.callStaticMethod(pu, "getString", ctx,
                    "updating_version", ""));
        }
        Log.i(TAG, "updater guard fetch local soft=" + soft + " prev=" + prev
                + " full=" + full + " compose=" + compose);
        boolean canSpoof = soft.length() > 0 && prev.length() > 0 && !prev.equals(soft);
        if (canSpoof) {
            sSpoofSoft = prev;
            sSpoofFull = full.replace(soft, prev);
            sSpoofCompose = compose.replace(soft, prev);
        }

        Class<?> hu = load("com.bbk.updater.utils.HttpUtils");
        String url = str(XposedHelpers.callStaticMethod(hu, "getUpdateRequestUrl", ctx));
        Class<?> trig = load("com.vivo.updaterbaseframe.utils.BaseFrameUtils$CheckTrigeType");
        Object manul = Enum.valueOf((Class) trig, "MANUL");
        // isFull 一定带上：即便平时只给差分包，也要完整包。
        String params = str(XposedHelpers.callStaticMethod(hu, "getUpdateRequestParams",
                ctx, Boolean.FALSE, Boolean.TRUE, manul, Boolean.TRUE, Boolean.FALSE));
        String resp = str(XposedHelpers.callStaticMethod(hu, "getResponse", ctx, url, params, "POST"));
        String connectErr = str(XposedHelpers.getStaticObjectField(hu, "ERROR_CONNECT_TO_SERVER"));
        if (resp.length() == 0 || resp.equals(connectErr)) {
            return "net-error";
        }
        Class<?> su = load("com.bbk.updater.utils.SecurityUtils");
        String plain = str(XposedHelpers.callStaticMethod(su, "decryptResponse", ctx, resp));
        if (plain.length() == 0) {
            plain = resp;
        }
        Class<?> cu = load("com.bbk.updater.utils.CheckResultUtils");
        Object check = XposedHelpers.callStaticMethod(cu, "getCheckResultInfoFromJson", ctx, plain);
        if (check == null) {
            return "parse-fail";
        }
        int ret = toInt(call(check, "getRetcode"), -1);
        Log.i(TAG, "updater guard fetch ret=" + ret + " msg=" + str(call(check, "getMessage")));
        Object fota = call(check, "getFotaUpdateInfo");
        if (fota == null) {
            return directFallback(ctx, soft, startDownload, "no-package:ret=" + ret);
        }
        String ver = str(call(fota, "getVersion"));
        String type = str(call(fota, "getPackageType"));
        long len = toLong(call(fota, "getFileLength"), 0L);
        String dl = str(call(fota, "getDownloadURL"));
        Log.i(TAG, "updater guard fetch result ret=" + ret + " version=" + ver
                + " type=" + type + " size=" + len + " url=" + (dl.length() > 0 ? 1 : 0));
        if (dl.length() == 0) {
            return "no-url:ret=" + ret;
        }
        if (!startDownload) {
            return "probe-ok:" + ver + ":" + type + ":" + len;
        }
        String fileName = str(call(fota, "getFileName"));
        File target = new File(downloadDir(), fileName);
        if (fileName.length() > 0 && target.isFile() && target.length() == len) {
            return "already:" + fileName;
        }
        try {
            android.os.StatFs stat = new android.os.StatFs(Environment.getExternalStorageDirectory().getPath());
            long free = stat.getAvailableBytes();
            if (len > 0 && free < len + (long) (1024 * 1024 * 1024)) {
                return "no-space:free=" + free;
            }
        } catch (Throwable ignored) {
        }
        try {
            XposedHelpers.callStaticMethod(pu, "putUpdateInfo", ctx, fota);
        } catch (Throwable t) {
            Log.w(TAG, "updater guard fetch save failed", t);
        }
        startNativeDownload(ctx, fota);
        return "ok:" + ver + ":" + len;
    }

    /**
     * 已经最新、服务器只回“无更新”时的回退：用升级器上次解析保存的签名直链，
     * 现造一个 UpdateInfo（文件名/长度来自 URL 与 Range 探测）交给原生下载器。
     */
    private static String directFallback(Context ctx, String soft, boolean startDownload, String why) {
        try {
            Class<?> pu = load("com.bbk.updater.utils.PrefsUtils");
            String url = str(XposedHelpers.callStaticMethod(pu, "getString", ctx,
                    "current_download_real_url", ""));
            Log.i(TAG, "updater guard fetch fallback " + why
                    + " direct=" + (url.length() > 0 ? 1 : 0));
            if (!url.startsWith("https://")) {
                return why;
            }
            long len = headLength(url);
            if (len <= 0) {
                return "no-url-length:" + why;
            }
            String name = url.substring(0, url.indexOf('?') > 0 ? url.indexOf('?') : url.length());
            name = name.substring(name.lastIndexOf('/') + 1);
            if (name.length() == 0) {
                return "no-file-name:" + why;
            }
            if (!startDownload) {
                return "probe-direct:" + soft + ":" + len;
            }
            File target = new File(downloadDir(), name);
            if (target.isFile() && target.length() == len) {
                return "already:" + name;
            }
            // 用户可能把包改过名：同目录下同字节数的 zip 视为同一份，不重复下 8GB。
            File[] existing = new File(downloadDir()).listFiles();
            if (existing != null) {
                for (File f : existing) {
                    if (f != null && f.isFile() && f.length() == len
                            && f.getName().toLowerCase().endsWith(".zip")) {
                        return "already:" + f.getName();
                    }
                }
            }
            try {
                android.os.StatFs stat = new android.os.StatFs(
                        Environment.getExternalStorageDirectory().getPath());
                if (stat.getAvailableBytes() < len + (long) (1024 * 1024 * 1024)) {
                    return "no-space";
                }
            } catch (Throwable ignored) {
            }
            Object info = XposedHelpers.newInstance(load("com.bbk.updater.bean.UpdateInfo"));
            call(info, "setVersion", soft);
            call(info, "setFileName", name);
            call(info, "setFileLength", Long.valueOf(len));
            call(info, "setDownloadURL", "direct:" + url);
            call(info, "setPackageType", "OTA_FOTA");
            call(info, "setTimeStamp", "0");
            call(info, "setActivePackage", Boolean.TRUE);
            try {
                XposedHelpers.callStaticMethod(pu, "putUpdateInfo", ctx, info);
            } catch (Throwable t) {
                Log.w(TAG, "updater guard fetch save failed", t);
            }
            sDirectUrl = url;
            startNativeDownload(ctx, info);
            final String clear = url;
            new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
                @Override
                public void run() {
                    if (clear.equals(sDirectUrl)) {
                        sDirectUrl = null;
                    }
                }
            }, 600000L);
            Log.i(TAG, "updater guard fetch direct-ok name=" + name + " size=" + len);
            return "direct-ok:" + soft + ":" + len;
        } catch (Throwable t) {
            Log.w(TAG, "updater guard fetch fallback failed", t);
            return "error:" + t.getClass().getSimpleName();
        }
    }

    private static long headLength(String url) {
        java.net.HttpURLConnection c = null;
        try {
            c = (java.net.HttpURLConnection) new java.net.URL(url).openConnection();
            c.setRequestMethod("GET");
            c.setRequestProperty("Range", "bytes=0-0");
            c.setConnectTimeout(10000);
            c.setReadTimeout(10000);
            if (c.getResponseCode() / 100 != 2) {
                return -1;
            }
            String range = c.getHeaderField("Content-Range");
            if (range != null && range.indexOf('/') > 0) {
                return toLong(range.substring(range.indexOf('/') + 1), -1);
            }
            return toLong(c.getHeaderField("Content-Length"), -1);
        } catch (Throwable t) {
            return -1;
        } finally {
            if (c != null) {
                c.disconnect();
            }
        }
    }

    /** 交给升级器自己的下载服务：续传、进度、通知都由它负责。 */
    private static void startNativeDownload(Context ctx, Object info) {
        boolean wifi = false;
        try {
            Class<?> commonUtils = load("com.bbk.updater.utils.CommonUtils");
            Object v = XposedHelpers.callStaticMethod(commonUtils, "isNetworkWifi", ctx);
            wifi = v instanceof Boolean && (Boolean) v;
        } catch (Throwable ignored) {
        }
        Object task = XposedHelpers.newInstance(load("h3.a"));
        call(task, "i", info);
        call(task, "n", "ota_pacakge");
        call(task, "m", Boolean.TRUE);
        call(task, "j", Integer.valueOf(wifi ? 2 : 0));
        call(task, "k", "NORMAL_MANUAL_DOWNLOAD");
        java.util.ArrayList<Object> list = new java.util.ArrayList<Object>();
        list.add(task);
        Object mgr = XposedHelpers.callStaticMethod(load("g0.a"), "E", ctx);
        call(mgr, "u", list);
    }

    /** 运行期命令通道：2s 轮询 Settings.Global，不重启 updater 就能查状态/取消安装。 */
    private static void startCommandLoop() {
        if (sCommandLoop) {
            return;
        }
        sCommandLoop = true;
        final Handler h = new Handler(Looper.getMainLooper());
        h.postDelayed(new Runnable() {
            @Override
            public void run() {
                try {
                    commandTick();
                } catch (Throwable t) {
                    Log.w(TAG, "updater guard cmd tick failed", t);
                }
                h.postDelayed(this, 2000L);
            }
        }, 2500L);
    }

    private static void commandTick() {
        final Context ctx = appCtx();
        if (ctx == null) {
            return;
        }
        String raw = null;
        try {
            raw = Settings.Global.getString(ctx.getContentResolver(), Prefs.UPDATER_CMD_KEY);
        } catch (Throwable ignored) {
        }
        long seq = -1;
        String verb = null;
        if (raw != null) {
            int i = raw.indexOf(':');
            if (i > 0) {
                seq = toLong(raw.substring(0, i), -1);
                verb = raw.substring(i + 1).trim();
            }
        }
        if (seq > 0 && verb != null && verb.length() > 0) {
            if (sLastCmdRaw == null) {
                // 进程刚起：当前值只作基线，绝不回放（旧 cancel 可能打断新安装）。
                sLastCmdRaw = raw;
                sLastCmdSeq = seq;
            } else if (!raw.equals(sLastCmdRaw)) {
                // 按“值变了”去重而不是比大小：adb 手写过大的 seq、改过时钟都不会卡住通道。
                sLastCmdRaw = raw;
                sLastCmdSeq = seq;
                if ("cancel".equals(verb)) {
                    cancelInstall(ctx);
                } else if (isMainProcess()) {
                    // 只有主进程执行 fetch/probe：:remote 也装着模块，两边同时拉包会重复下载。
                    if ("probe".equals(verb)) {
                        probeCustomUi();
                    } else if ("fetch".equals(verb)) {
                        fetchFullPackage(ctx, true);
                    } else if ("fetchprobe".equals(verb)) {
                        fetchFullPackage(ctx, false);
                    } else if ("urltest".equals(verb)) {
                        testStoredUrl(ctx);
                    } else if ("dlprobe".equals(verb)) {
                        directDownloadProbe(ctx);
                    } else if ("pause".equals(verb)) {
                        pauseDownload(ctx);
                    } else if ("cleandl".equals(verb)) {
                        cleanDownload(ctx);
                    }
                }
                publishState(ctx);
                return;
            }
        }
        long now = android.os.SystemClock.elapsedRealtime();
        if (now - sLastPublishAt < 3000) {
            return;
        }
        publishState(ctx);
    }

    /** 直接调 update_engine 的 cancel()：对 updater 自己的会话和第三方 native apply 都有效。 */
    private static void cancelInstall(Context ctx) {
        try {
            Class<?> um = load("com.bbk.updater.remote.abinstall.UpdateManager");
            Object mgr = XposedHelpers.callStaticMethod(um, "getInstance", ctx);
            Object engine = callNoArg(mgr, "getUpdateEngine");
            if (engine == null) {
                Log.i(TAG, "updater guard cancel: engine null");
                return;
            }
            call(engine, "cancel");
            BLOCKS.incrementAndGet();
            Log.i(TAG, "updater guard cancel sent engine=" + engineInfo(mgr, "update_status"));
        } catch (Throwable t) {
            Log.w(TAG, "updater guard cancel failed", t);
        }
    }

    /** 自检：直接问宿主“定制只下载模式现在生效吗”，避免只凭装钩子成功就宣称生效。 */
    private static void probeCustomUi() {
        try {
            Class<?> vgc = android.os.Build.VERSION.SDK_INT >= 36 ? null : load("v0.a");
            if (vgc != null) {
                Object instance = XposedHelpers.callStaticMethod(vgc, "A");
                Object value = XposedHelpers.callMethod(instance, "M");
                Log.i(TAG, "updater guard probe customUi=" + value
                        + " wanted=" + optionOn(OPT_CUSTOM_UI));
                return;
            }
            Class<?> vgc6 = load("u0.a");
            Object instance6 = XposedHelpers.callStaticMethod(vgc6, "B");
            Object value6 = XposedHelpers.callMethod(instance6, "F", Integer.valueOf(-1));
            Log.i(TAG, "updater guard probe customUi(os6)=" + value6
                    + " wanted=" + optionOn(OPT_CUSTOM_UI));
        } catch (Throwable t) {
            Log.w(TAG, "updater guard probe failed", t);
        }
    }

    private static void publishState(Context ctx) {
        if (!isMainProcess()) {
            // :remote 也装着模块；两边都发布会让状态在“有 fetch 结果 / 空”之间来回跳。
            return;
        }
        sLastPublishAt = android.os.SystemClock.elapsedRealtime();
        int engine = -1;
        int progress = -1;
        int updState = -1;
        int result = -99;
        int dlp = -1;
        int dls = -1;
        String caller = "";
        String ver = "";
        String name = null;
        String path = null;
        long size = 0;
        Object mgr = null;
        try {
            Class<?> um = load("com.bbk.updater.remote.abinstall.UpdateManager");
            mgr = XposedHelpers.callStaticMethod(um, "getInstance", ctx);
            Object eng = callNoArg(mgr, "getUpdateEngine");
            if (eng != null) {
                engine = toInt(call(mgr, "getUpdateEngineStatus", eng), -1);
                updState = toInt(call(mgr, "getUpdateEngineState", eng), -1);
                result = toInt(call(mgr, "getUpdateEngineResult", eng), -99);
                caller = str(call(mgr, "getUpdateEngineCaller", eng));
                float p = toFloat(callNoArg(mgr, "getUpdateEngineProgress"), -1f);
                progress = p < 0 ? -1 : (int) Math.round(p <= 1.001f ? p * 100f : p);
                if (progress > 100) {
                    progress = 100;
                }
            }
            Object info = callNoArg(mgr, "getAbUpdateInfo");
            if (info != null) {
                ver = firstNonEmpty(str(call(info, "getFotaVersion")), str(call(info, "getVgcVersion")));
            }
        } catch (Throwable t) {
            once("stateErr", "updater guard state read failed " + t.getMessage());
        }
        try {
            Class<?> udm = load("com.bbk.updater.remote.download.UpdateDownloadManager");
            Object u = XposedHelpers.callStaticMethod(udm, "getInstance", ctx);
            Object di = call(u, "getDownloadInfo", "ota_pacakge");
            if (di != null) {
                dlp = toInt(call(di, "getProgress"), -1);
                dls = toInt(call(di, "getStatus"), -1);
                name = str(call(di, "getFileName"));
                size = toLong(call(di, "getTotalBytes"), 0L);
                path = str(call(di, "getFullFilePath"));
            }
        } catch (Throwable ignored) {
        }
        if (path == null || path.length() == 0 || !new java.io.File(path).isFile()) {
            File f = newestPackage();
            if (f != null) {
                name = f.getName();
                path = f.getAbsolutePath();
                size = f.length();
            }
        }
        StringBuilder s = new StringBuilder(200);
        s.append("seq=").append(sLastCmdSeq)
                .append(";pid=").append(android.os.Process.myPid())
                .append(";proc=").append(clean(processName()))
                .append(";ts=").append(System.currentTimeMillis())
                .append(";engine=").append(engine)
                .append(";progress=").append(progress)
                .append(";upd=").append(updState)
                .append(";result=").append(result)
                .append(";caller=").append(clean(caller))
                .append(";dlp=").append(dlp)
                .append(";dls=").append(dls)
                .append(";ver=").append(clean(ver))
                .append(";name=").append(clean(name))
                .append(";path=").append(clean(path))
                .append(";size=").append(size)
                .append(";fetch=").append(clean(sLastFetch))
                .append(";fetchts=").append(sLastFetchTs)
                .append(";build=").append(clean(Build.DISPLAY))
                .append(";mode=").append(optionOn(OPT_EXTERNAL_INSTALL) ? 1 : 0)
                .append(";ui=").append(optionOn(OPT_CUSTOM_UI) ? 1 : 0);
        String line = s.toString();
        try {
            Settings.Global.putString(ctx.getContentResolver(), Prefs.UPDATER_STATE_KEY, line);
        } catch (Throwable t) {
            once("stateWrite", "updater guard state write failed " + t.getMessage());
        }
        if (!line.equals(sLastStateLine)) {
            sLastStateLine = line;
            if (engine > 0 || dlp > 0) {
                Log.i(TAG, "updater guard state " + line);
            }
        }
        notifyStatus(ctx, engine, progress);
    }

    private static File newestPackage() {
        File dir = new File(downloadDir());
        File[] files = dir.listFiles();
        if (files == null) {
            return null;
        }
        File best = null;
        for (File f : files) {
            if (f == null || !f.isFile() || !f.getName().toLowerCase().endsWith(".zip")) {
                continue;
            }
            if (best == null || f.lastModified() > best.lastModified()) {
                best = f;
            }
        }
        return best;
    }

    private static void notifyStatus(Context ctx, int engine, int progress) {
        try {
            NotificationManager nm = (NotificationManager)
                    ctx.getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm == null) {
                return;
            }
            boolean live = engine >= 3 && engine <= 6;
            if (!live || !optionOn(OPT_STATUS_NOTI) || !nm.areNotificationsEnabled()) {
                nm.cancel(STATE_NOTI_ID);
                return;
            }
            NotificationChannel ch = new NotificationChannel(STATE_CHANNEL_ID,
                    "系统升级状态", NotificationManager.IMPORTANCE_LOW);
            ch.setShowBadge(false);
            nm.createNotificationChannel(ch);
            boolean ready = engine == 6;
            int p = Math.max(0, Math.min(100, progress < 0 ? 0 : progress));
            Notification n = new Notification.Builder(ctx, STATE_CHANNEL_ID)
                    .setSmallIcon(android.R.drawable.stat_sys_download)
                    .setContentTitle(ready ? "升级包已应用" : "正在应用系统更新")
                    .setContentText(ready ? "重启后生效；本模块已拦截自动重启" : p + "%")
                    .setProgress(100, p, !ready)
                    .setOngoing(!ready)
                    .setOnlyAlertOnce(true)
                    .build();
            nm.notify(STATE_NOTI_ID, n);
        } catch (Throwable ignored) {
        }
    }

    private static Object call(Object target, String method, Object... args) {
        if (target == null) {
            return null;
        }
        try {
            return XposedHelpers.callMethod(target, method, args);
        } catch (Throwable t) {
            return null;
        }
    }

    /** 部分字段只有无参重载作用在引擎字段上，单独走一次。 */
    private static Object callNoArg(Object target, String method) {
        if (target == null) {
            return null;
        }
        try {
            return XposedHelpers.callMethod(target, method, new Object[0]);
        } catch (Throwable t) {
            return null;
        }
    }

    private static String engineInfo(Object mgr, String key) {
        try {
            return String.valueOf(XposedHelpers.callMethod(mgr, "getUpdateEngineInfo", key));
        } catch (Throwable t) {
            return "?";
        }
    }

    private static String processName() {
        try {
            Object name = Class.forName("android.app.Application")
                    .getMethod("getProcessName").invoke(null);
            return name == null ? "" : name.toString();
        } catch (Throwable t) {
            return "";
        }
    }

    private static boolean isMainProcess() {
        String name = processName();
        return name.length() == 0 || UPDATER.equals(name);
    }

    private static String clean(String v) {
        if (v == null) {
            return "";
        }
        return v.replace(';', ',').replace('\n', ' ').replace('\r', ' ');
    }

    private static String firstNonEmpty(String a, String b) {
        return a != null && a.length() > 0 ? a : (b == null ? "" : b);
    }

    private static String str(Object v) {
        return v == null ? "" : v.toString();
    }

    private static int toInt(Object v, int def) {
        try {
            return v == null ? def : Integer.parseInt(v.toString().trim());
        } catch (Throwable t) {
            return def;
        }
    }

    private static long toLong(Object v, long def) {
        try {
            return v == null ? def : Long.parseLong(v.toString().trim());
        } catch (Throwable t) {
            return def;
        }
    }

    private static long toLong(String v, long def) {
        try {
            return Long.parseLong(v.trim());
        } catch (Throwable t) {
            return def;
        }
    }

    private static float toFloat(Object v, float def) {
        try {
            return v == null ? def : Float.parseFloat(v.toString().trim());
        } catch (Throwable t) {
            return def;
        }
    }

    private static Context appCtx() {
        Context ctx = currentApp();
        return ctx != null ? ctx : systemContext();
    }

    private static boolean optionOn(String key) {
        Context ctx = appCtx();
        if (ctx == null) {
            return true;
        }
        try {
            return !"0".equals(Prefs.option(ctx, FEATURE_ID, key, "1"));
        } catch (Throwable t) {
            return true;
        }
    }

    private static int hookDownloadOnly(ClassLoader cl, Class<?> common, StringBuilder sb) {
        int hooks = 0;
        if (common != null) {
            int n = XposedBridge.hookAllMethods(common, "getUpdatePackageDirectory",
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            try {
                                Object[] a = param.args;
                                if (a == null || a.length < 2
                                        || !(a[0] instanceof Context) || !(a[1] instanceof String)) {
                                    return;
                                }
                                Context ctx = (Context) a[0];
                                String task = (String) a[1];
                                if (!guardEnabled() || !downloadOnly(ctx)) {
                                    return;
                                }
                                if (!"ota_pacakge".equals(task) && !"vgc_package".equals(task)) {
                                    return;
                                }
                                File dir = new File(downloadDir());
                                if (!dir.exists()) {
                                    dir.mkdirs();
                                }
                                param.setResult(dir.getAbsolutePath());
                                once("downloadDir", "updater guard download dir=" + dir.getAbsolutePath());
                            } catch (Throwable t) {
                                Log.w(TAG, "updater guard download dir hook failed", t);
                            }
                        }
                    }).size();
            hooks += n;
            sb.append(" downloadDir=").append(n);

            int n2 = XposedBridge.hookAllMethods(common, "startInstall",
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            try {
                                Object[] a = param.args;
                                if (a == null || a.length == 0 || !(a[0] instanceof Context)) {
                                    return;
                                }
                                Context ctx = (Context) a[0];
                                if (guardEnabled() && downloadOnly(ctx)) {
                                    block("startInstall");
                                    toast(ctx, "已拦截自动安装：升级包保留在 Download");
                                    param.setResult(null);
                                }
                            } catch (Throwable t) {
                                Log.w(TAG, "updater guard startInstall hook failed", t);
                            }
                        }
                    }).size();
            hooks += n2;
            sb.append(" startInstall=").append(n2);
        } else {
            sb.append(" CommonUtils=missing(download)");
        }

        Class<?> receiver = find(cl, "com.bbk.updater.receiver.DownloadReceiver");
        if (receiver != null) {
            int n3 = XposedBridge.hookAllMethods(receiver, "onReceive", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    try {
                        Object[] a = param.args;
                        if (a == null || a.length < 2
                                || !(a[0] instanceof Context) || !(a[1] instanceof Intent)) {
                            return;
                        }
                        Context ctx = (Context) a[0];
                        Intent intent = (Intent) a[1];
                        if (!guardEnabled() || !downloadOnly(ctx)) {
                            return;
                        }
                        if (!"com.vivo.updater.download.status.change".equals(intent.getAction())) {
                            return;
                        }
                        String task = intent.getStringExtra("task");
                        if (!"ota_pacakge".equals(task) && !"vgc_package".equals(task)) {
                            return;
                        }
                        notifyDownloadedLater(ctx, task);
                    } catch (Throwable t) {
                        Log.w(TAG, "updater guard download notify hook failed", t);
                    }
                }
            }).size();
            hooks += n3;
            sb.append(" downloadNotify=").append(n3);
        } else {
            sb.append(" DownloadReceiver=missing");
        }
        return hooks;
    }

    private static volatile Boolean sDownloadOnly;

    private static boolean downloadOnly(Context ctx) {
        boolean on = "1".equals(Prefs.option(ctx, FEATURE_ID, OPT_DOWNLOAD_ONLY, "1"));
        sDownloadOnly = Boolean.valueOf(on);
        return on;
    }

    private static boolean downloadOnlyCached() {
        Boolean v = sDownloadOnly;
        return v == null || v.booleanValue();
    }

    private static boolean protectedPath(String path) {
        if (path == null) {
            return false;
        }
        String dir = downloadDir();
        return path.equals(dir) || path.startsWith(dir + File.separator);
    }

    /**
     * 只下载模式下必须保住安装包：updater 的 checkIfNeedToDeletePackageAfterUpdate()
     * 会对 getUpdatePackageDirectory()（已被我们改成 Download）调 IOUtils.deleteFile，
     * 而它是递归删除目录的 —— 不拦会把整个 Download 清空。
     */
    private static int hookKeepPackage(ClassLoader cl, StringBuilder sb) {
        int hooks = 0;

        Class<?> strategy = find(cl, "com.bbk.updater.download.strategy.DownloadStrategy");
        if (strategy != null) {
            int n = XposedBridge.hookAllMethods(strategy, "checkIfNeedToDeletePackageAfterUpdate",
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            if (guardEnabled() && downloadOnlyCached()) {
                                block("deleteCleanup");
                                param.setResult(null);
                            }
                        }
                    }).size();
            n += XposedBridge.hookAllMethods(strategy, "clearDownloadPackage",
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            if (guardEnabled() && downloadOnlyCached()) {
                                block("clearDownload");
                                param.setResult(null);
                            }
                        }
                    }).size();
            hooks += n;
            sb.append(" keepCleanup=").append(n);
        } else {
            sb.append(" DownloadStrategy=missing");
        }

        Class<?> io = find(cl, "com.bbk.updater.utils.IOUtils");
        if (io != null) {
            XC_MethodHook hook = new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    if (!guardEnabled() || !downloadOnlyCached()) {
                        return;
                    }
                    Object[] a = param.args;
                    if (a == null || a.length == 0) {
                        return;
                    }
                    Object f = a[0];
                    String path = f instanceof File ? ((File) f).getAbsolutePath()
                            : f instanceof String ? (String) f : null;
                    if (protectedPath(path)) {
                        block("deleteFile");
                        param.setResult(null);
                    }
                }
            };
            int n = XposedBridge.hookAllMethods(io, "deleteFile", hook).size();
            n += XposedBridge.hookAllMethods(io, "deleteFileImpl", hook).size();
            hooks += n;
            sb.append(" keepIo=").append(n);
        } else {
            sb.append(" IOUtils=missing");
        }

        Class<?> manager = find(cl, "g0.a");
        if (manager != null) {
            int n = XposedBridge.hookAllMethods(manager, "n", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    if (!guardEnabled() || !downloadOnlyCached()) {
                        return;
                    }
                    Object[] a = param.args;
                    if (a == null || a.length < 2 || !(a[1] instanceof String)) {
                        return;
                    }
                    String path = downloadDir() + File.separator + a[1];
                    if (protectedPath(path)) {
                        block("deleteFileDirect");
                        param.setResult(null);
                    }
                }
            }).size();
            XC_MethodHook kill = new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    if (!guardEnabled() || !downloadOnlyCached()) {
                        return;
                    }
                    Object[] a = param.args;
                    if (a != null && a.length > 0 && a[0] instanceof String) {
                        String task = (String) a[0];
                        if ("ota_pacakge".equals(task) || "vgc_package".equals(task)) {
                            block("deleteDir");
                            param.setResult(null);
                        }
                    }
                }
            };
            n += XposedBridge.hookAllMethods(manager, "h", kill).size();
            n += XposedBridge.hookAllMethods(manager, "m", kill).size();
            hooks += n;
            sb.append(" keepManager=").append(n);
        } else {
            sb.append(" g0a=missing");
        }
        return hooks;
    }

    private static String downloadDir() {
        try {
            File dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
            return dir.getAbsolutePath();
        } catch (Throwable t) {
            return "/storage/emulated/0/Download";
        }
    }

    private static final Set<String> NOTIFIED = Collections.synchronizedSet(new HashSet<String>());

    private static void notifyDownloadedLater(final Context ctx, final String task) {
        final Context app = ctx.getApplicationContext();
        new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
            @Override
            public void run() {
                try {
                    String path = downloadedPath(app, task);
                    if (path != null) {
                        notifyDownloaded(app, path);
                    }
                } catch (Throwable t) {
                    Log.w(TAG, "updater guard notify path failed", t);
                }
            }
        }, 1500L);
    }

    private static String downloadedPath(Context ctx, String task) {
        String name = null;
        try {
            Class<?> udm = load("com.bbk.updater.remote.download.UpdateDownloadManager");
            Object mgr = XposedHelpers.callStaticMethod(udm, "getInstance", ctx);
            Object info = XposedHelpers.callMethod(mgr, "getDownloadInfo", task);
            if (info != null) {
                name = (String) XposedHelpers.callMethod(info, "getFileName");
            }
        } catch (Throwable ignored) {
        }
        File dir = new File(downloadDir());
        if (name != null && name.length() > 0) {
            File f = new File(dir, name);
            if (f.isFile() && f.length() > 0) {
                return f.getAbsolutePath();
            }
        }
        File[] files = dir.listFiles();
        if (files == null) {
            return null;
        }
        File best = null;
        for (File f : files) {
            if (f == null || !f.isFile() || !f.getName().toLowerCase().endsWith(".zip")) {
                continue;
            }
            if (best == null || f.lastModified() > best.lastModified()) {
                best = f;
            }
        }
        return best != null && best.length() > 0 ? best.getAbsolutePath() : null;
    }

    private static void notifyDownloaded(Context ctx, String path) {
        if (!NOTIFIED.add(path)) {
            return;
        }
        Log.i(TAG, "updater guard download done path=" + path);
        try {
            NotificationManager nm = (NotificationManager)
                    ctx.getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm == null) {
                return;
            }
            if (!nm.areNotificationsEnabled()) {
                Toast.makeText(ctx, "升级包已下载：" + path, Toast.LENGTH_LONG).show();
                return;
            }
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID, "系统升级下载", NotificationManager.IMPORTANCE_DEFAULT);
            nm.createNotificationChannel(channel);
            Notification n = new Notification.Builder(ctx, CHANNEL_ID)
                    .setSmallIcon(android.R.drawable.stat_sys_download)
                    .setContentTitle("升级包已下载")
                    .setContentText(path)
                    .setStyle(new Notification.BigTextStyle().bigText(path))
                    .setAutoCancel(true)
                    .build();
            nm.notify(NOTI_ID, n);
        } catch (Throwable t) {
            Log.w(TAG, "updater guard notification failed", t);
            try {
                Toast.makeText(ctx, "升级包已下载：" + path, Toast.LENGTH_LONG).show();
            } catch (Throwable ignored) {
            }
        }
    }

    /** 两个包共用的最底层护栏：PowerManager.reboot + RecoverySystem.installPackage。 */
    private static int hookPowerGuard() {
        int n = 0;
        try {
            Class<?> pm = Class.forName("android.os.PowerManager");
            n += XposedBridge.hookAllMethods(pm, "reboot", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    if (guardEnabled()) {
                        block("PowerManager.reboot");
                        toast(appCtx(), "已拦截重启，升级包与系统都保持不变");
                        param.setResult(null);
                    }
                }
            }).size();
        } catch (Throwable t) {
            Log.w(TAG, "updater guard PowerManager hook failed", t);
        }
        try {
            Class<?> rs = Class.forName("android.os.RecoverySystem");
            n += XposedBridge.hookAllMethods(rs, "installPackage", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    if (guardEnabled()) {
                        block("RecoverySystem.installPackage");
                        param.setResult(null);
                    }
                }
            }).size();
        } catch (Throwable t) {
            Log.w(TAG, "updater guard RecoverySystem hook failed", t);
        }
        return n;
    }

    private static void block(String what) {
        BLOCKS.incrementAndGet();
        once(what, "updater guard blocked " + what);
    }

    private static volatile long sLastToastAt;

    /** 拦截类动作给一次可见反馈（8 秒节流）：否则用户只看到“点了没反应”。 */
    private static void toast(final Context ctx, final String message) {
        if (ctx == null) {
            return;
        }
        long now = android.os.SystemClock.elapsedRealtime();
        if (now - sLastToastAt < 8000) {
            return;
        }
        sLastToastAt = now;
        new Handler(Looper.getMainLooper()).post(new Runnable() {
            @Override
            public void run() {
                try {
                    Toast.makeText(ctx.getApplicationContext(), message, Toast.LENGTH_SHORT).show();
                } catch (Throwable ignored) {
                }
            }
        });
    }

    private static void once(String key, String line) {
        if (WITNESS.size() <= 32 && WITNESS.add(key)) {
            Log.i(TAG, line);
        }
    }

    private static boolean guardEnabled() {
        Boolean cached = sEnabled;
        if (cached != null) {
            return cached.booleanValue();
        }
        Context ctx = currentApp();
        if (ctx == null) {
            ctx = systemContext();
        }
        if (ctx == null) {
            return true;   // 取不到 Context 时按默认开（这是防掉 root 的保险）
        }
        boolean on = Prefs.featureEnabled(ctx, FEATURE_ID, true);
        sEnabled = Boolean.valueOf(on);
        once("enabled", "updater guard enabled=" + (on ? 1 : 0));
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

    private static Class<?> find(ClassLoader cl, String name) {
        try {
            return XposedHelpers.findClass(name, cl);
        } catch (Throwable t) {
            return null;
        }
    }
}
