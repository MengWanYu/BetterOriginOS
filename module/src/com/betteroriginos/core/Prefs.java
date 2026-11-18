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

    /** 模块级调试开关（设置 App 信息页），打开后各进程输出低频诊断日志。 */
    public static final String DEBUG_KEY = PREFIX + "debug";

    /**
     * 升级器运行期通道：App 写入 "<seq>:<verb>"，updater 进程 2s 轮询执行；
     * updater 把状态发布到 STATE 键，App 直接读。两端都走 Settings.Global，
     * 不重启 updater 进程即可生效（命令用 ':' 分隔，写入走 su shell 不能含 '|'）。
     */
    public static final String UPDATER_CMD_KEY = PREFIX + "updater_cmd";
    public static final String UPDATER_STATE_KEY = PREFIX + "updater_state";

    private Prefs() {
    }

    public static boolean debugEnabled(Context ctx) {
        String value = global(ctx, DEBUG_KEY);
        if (value == null) {
            value = local(DEBUG_KEY);
        }
        return "1".equals(value) || "true".equals(value);
    }

    /**
     * 设置 App 专用：绕开进程内 2s 缓存直接读，写入（su settings put）完成后立刻回读
     * 才能看到新值。Hook 进程仍用带缓存版本（热路径每秒读）。
     */
    public static boolean debugEnabledNow(Context ctx) {
        String value = globalNow(ctx, DEBUG_KEY);
        if (value == null) {
            value = local(DEBUG_KEY);
        }
        return "1".equals(value) || "true".equals(value);
    }

    public static boolean featureEnabledNow(Context ctx, String featureId, boolean defaultValue) {
        String value = globalNow(ctx, featureKey(featureId));
        if (value == null) {
            String legacy = LEGACY_FEATURE.get(featureKey(featureId));
            if (legacy != null) {
                value = globalNow(ctx, legacy);
            }
        }
        if (value == null) {
            value = local(featureKey(featureId));
        }
        return value == null ? defaultValue : ("1".equals(value) || "true".equals(value));
    }

    public static String optionNow(Context ctx, String featureId, String optionKey,
                                   String defaultValue) {
        String key = optionKey(featureId, optionKey);
        String value = globalNow(ctx, key);
        if (value == null) {
            String legacy = LEGACY_OPTION.get(key);
            if (legacy != null) {
                value = globalNow(ctx, legacy);
            }
        }
        if (value == null) {
            value = local(key);
        }
        return value == null ? defaultValue : value;
    }

    private static String globalNow(Context ctx, String key) {
        try {
            return Settings.Global.getString(ctx.getContentResolver(), key);
        } catch (Throwable t) {
            return null;
        }
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

    public static boolean featureEnabled(Context ctx, String featureId, boolean defaultValue) {        String value = global(ctx, featureKey(featureId));
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
