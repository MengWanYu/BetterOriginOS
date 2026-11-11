package com.betteroriginos.core;

import android.content.Context;
import android.provider.Settings;

import java.util.HashMap;
import java.util.Map;

import de.robv.android.xposed.XSharedPreferences;

public final class Prefs {
    private static final String PKG = "com.betteroriginos";
    private static final String FILE = "settings";
    private static final String PREFIX = "betteroriginos_";

    /** featureKey -> legacy global key；由各 Feature 自行注册，Prefs 不感知具体功能 */
    private static final Map<String, String> LEGACY_FEATURE = new HashMap<>();
    /** optionKey -> legacy global key */
    private static final Map<String, String> LEGACY_OPTION = new HashMap<>();

    private Prefs() {
    }

    public static String featureKey(String featureId) {
        return PREFIX + "feature_" + featureId;
    }

    public static String optionKey(String featureId, String optionKey) {
        return PREFIX + featureId + "_" + optionKey;
    }

    public static void registerLegacyFeatureKey(String featureId, String legacyKey) {
        LEGACY_FEATURE.put(featureKey(featureId), PREFIX + legacyKey);
    }

    public static void registerLegacyOptionKey(String featureId, String optionKey, String legacyKey) {
        LEGACY_OPTION.put(optionKey(featureId, optionKey), PREFIX + legacyKey);
    }

    public static boolean featureEnabled(Context ctx, String featureId, boolean defaultValue) {
        String value = global(ctx, featureKey(featureId));
        if (value == null) {
            String legacy = LEGACY_FEATURE.get(featureKey(featureId));
            if (legacy != null) {
                value = global(ctx, legacy);
            }
        }
        if (value == null) {
            value = local(featureKey(featureId));
        }
        if (value == null) {
            return defaultValue;
        }
        return "1".equals(value) || "true".equals(value);
    }

    public static String option(Context ctx, String featureId, String optionKey,
                                String defaultValue) {
        String key = optionKey(featureId, optionKey);
        String value = global(ctx, key);
        if (value == null) {
            String legacy = LEGACY_OPTION.get(key);
            if (legacy != null) {
                value = global(ctx, legacy);
            }
        }
        if (value == null) {
            value = local(key);
        }
        return value == null ? defaultValue : value;
    }

    /**
     * Settings.Global 读缓存（进程级，TTL 2s）。每次读是一次 binder 往返（system_server），
     * 而热路径每秒都会问：媒体卡补扫的重试、col 对位的行检查、ticker 的可见性修复…
     * 特性开关从设置 App 写入后最多 2 秒生效——体感即时。
     */
    private static final java.util.HashMap<String, Object[]> GLOBAL_CACHE =
            new java.util.HashMap<>();
    private static final long GLOBAL_TTL_MS = 2000;

    private static String global(Context ctx, String key) {
        long now = android.os.SystemClock.uptimeMillis();
        synchronized (GLOBAL_CACHE) {
            Object[] hit = GLOBAL_CACHE.get(key);
            if (hit != null && now - (Long) hit[1] < GLOBAL_TTL_MS) {
                return (String) hit[0];
            }
        }
        String value = null;
        try {
            value = Settings.Global.getString(ctx.getContentResolver(), key);
        } catch (Throwable t) {
            return null;
        }
        synchronized (GLOBAL_CACHE) {
            GLOBAL_CACHE.put(key, new Object[]{value, Long.valueOf(now)});
        }
        return value;
    }

    private static String local(String key) {
        try {
            XSharedPreferences p = new XSharedPreferences(PKG, FILE);
            p.reload();
            return p.getString(key, null);
        } catch (Throwable t) {
            return null;
        }
    }
}
