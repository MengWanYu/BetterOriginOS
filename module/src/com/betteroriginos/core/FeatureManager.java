package com.betteroriginos.core;

import android.content.Context;
import android.util.Log;

import com.betteroriginos.feature.camera.CameraShutterFeature;
import com.betteroriginos.feature.desktop.DesktopMotionFeature;
import com.betteroriginos.feature.media.MediaCardFeature;
import com.betteroriginos.feature.notify.NotifyRowFeature;
import com.betteroriginos.feature.fileman.FileManagerRenameFeature;
import com.betteroriginos.feature.picker.PickerAlbumsFeature;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class FeatureManager {
    public static final String TAG = "BetterOriginOS";

    private static final List<Feature> FEATURES = Collections.unmodifiableList(
            Arrays.<Feature>asList(
                    new NotifyRowFeature(),
                    new MediaCardFeature(),
                    new PickerAlbumsFeature(),
                    new FileManagerRenameFeature(),
                    new CameraShutterFeature(),
                    new DesktopMotionFeature()
            ));

    private FeatureManager() {
    }

    public static List<Feature> all() {
        return FEATURES;
    }

    public static Feature byId(String id) {
        for (Feature f : FEATURES) {
            if (f.id().equals(id)) {
                return f;
            }
        }
        return null;
    }

    /** 已装过的包名（每进程一份）。Vector 会重复派发 handleLoadPackage（真机实测 8 秒内
     *  systemui×2 + magazine×2，且每次派发的 classloader 身份都不同），不挡就是同一批
     *  hook 每次全量叠加：notify 绑定路径一次回调跑 N 遍。同进程同包重复派发一律跳过——
     *  同一个 LoadedApk 的类就那一份，重装只会叠 hook，不会带来新类。 */
    private static final Set<String> INSTALLED = new java.util.HashSet<>();

    /**
     * 跨"模块类加载器重建"的防重入：Vector 会整体重建模块 classloader（真机 01:01 实测，
     * 广播之后静态集合随新实例全部归零，同进程再装 = 同一批类再叠一层 hook、卡片视图也会
     * 重复添加）。标记落在宿主进程自己的 SharedPreferences，键带进程启动时刻——
     * 同进程重载命中跳过；进程重启/新进程键不同，照常安装。
     * 标记存不了就按"没装过"走：宁可偶尔叠一层 hook，也不能不装。
     */
    private static boolean alreadyInstalledInProcess(Context app, String packageName) {
        try {
            android.content.SharedPreferences sp = app.getSharedPreferences(
                    "betteroriginos_install_guard", 0);
            String key = "installed_" + android.os.Process.getStartElapsedRealtime();
            java.util.Set<String> pkgs = sp.getStringSet(key, null);
            if (pkgs != null && pkgs.contains(packageName)) {
                return true;
            }
            java.util.Set<String> next = new java.util.HashSet<>(
                    pkgs == null ? Collections.<String>emptySet() : pkgs);
            next.add(packageName);
            sp.edit().clear().putStringSet(key, next).apply();
            return false;
        } catch (Throwable t) {
            return false;
        }
    }

    public static synchronized void installAll(ClassLoader classLoader, String packageName) {
        if (!INSTALLED.add(packageName)) {
            Log.i(TAG, "skip duplicate install " + packageName + " (static guard)");
            return;
        }
        try {
            Object app = Class.forName("android.app.ActivityThread")
                    .getMethod("currentApplication").invoke(null);
            if (app instanceof Context
                    && alreadyInstalledInProcess((Context) app, packageName)) {
                Log.i(TAG, "skip duplicate install " + packageName + " (process guard)");
                return;
            }
        } catch (Throwable ignored) {
        }
        for (Feature f : FEATURES) {
            if (!targets(f.scopes(), packageName)) {
                continue;
            }
            try {
                f.install(classLoader, packageName);
                Log.i(TAG, "installed feature " + f.id() + " for " + packageName);
            } catch (Throwable t) {
                Log.e(TAG, "install " + f.id() + " failed", t);
            }
        }
    }

    public static List<String> appPackages() {
        Set<String> set = new LinkedHashSet<>();
        for (Feature f : FEATURES) {
            String[] scopes = f.scopes();
            if (scopes != null && scopes.length > 0) {
                set.add(scopes[0]);
            }
        }
        return new ArrayList<>(set);
    }

    public static List<Feature> forPackage(String packageName) {
        List<Feature> list = new ArrayList<>();
        for (Feature f : FEATURES) {
            String[] scopes = f.scopes();
            if (scopes != null && scopes.length > 0 && scopes[0].equals(packageName)) {
                list.add(f);
            }
        }
        return list;
    }

    private static boolean targets(String[] scopes, String packageName) {
        if (scopes == null) {
            return false;
        }
        for (String s : scopes) {
            if (s.equals(packageName)) {
                return true;
            }
        }
        return false;
    }
}
