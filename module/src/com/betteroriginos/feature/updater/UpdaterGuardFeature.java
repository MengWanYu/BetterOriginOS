package com.betteroriginos.feature.updater;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
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
    public String[] scopes() {
        return new String[]{UPDATER, COTA};
    }

    private static final String OPT_DOWNLOAD_ONLY = "download_only";
    private static final String CHANNEL_ID = "betteroriginos_updater";
    private static final int NOTI_ID = 0x424f5441;

    @Override
    protected List<FeatureOption> buildOptions() {
        List<FeatureOption> list = new ArrayList<FeatureOption>();
        list.add(FeatureOption.toggle(
                OPT_DOWNLOAD_ONLY,
                R.string.option_updater_download_only_title,
                R.string.option_updater_download_only_summary,
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
                        param.setResult(null);
                    }
                }
            }).size();
            hooks += n;
            sb.append(" n0.a.D=").append(n);
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

        // 6) 只下载不安装：目录改到 Download、拦 startInstall、完成后通知路径。
        hooks += hookDownloadOnly(cl, common, sb);
        // 7) 安装包保留：拦住 updater 的“成功后清理”，避免递归删掉整个 Download。
        hooks += hookKeepPackage(cl, sb);

        Log.i(TAG, "updater guard installed pkg=" + UPDATER + " hooks=" + hooks + " by" + sb);
        guardEnabled();   // 安装期自报一次 enabled=0/1（取不到 Context 时首个拦截点会再报）
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
            Class<?> udm = Class.forName("com.bbk.updater.remote.download.UpdateDownloadManager");
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
        once(what, "updater guard blocked " + what);
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
