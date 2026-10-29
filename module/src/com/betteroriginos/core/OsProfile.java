package com.betteroriginos.core;

import android.os.Build;
import android.util.Log;

/**
 * 系统画像：只做"这一版 SystemUI 长什么样"的判定，不承载业务逻辑。
 * 主判据用 SDK_INT（公开、零风险），OriginOS 名字仅用于日志与人话展示。
 */
public final class OsProfile {
    /** OriginOS 5 / Android 15（iQOO Z8）：媒体卡是通知列表里的一行 */
    public static final int SHADE_MEDIA_IN_LIST = 5;
    /** OriginOS 3 / Android 13（V1913A）：媒体通知被从列表收走，播放器是 QS 磁贴 */
    public static final int SHADE_MEDIA_AS_QS_TILE = 3;

    private static volatile int sShadeKind;
    private static volatile String sLabel;

    private OsProfile() {
    }

    /** 媒体 UI 落在哪：通知列表里的一行，还是 QS 磁贴 */
    public static int mediaShadeKind() {
        int k = sShadeKind;
        if (k == 0) {
            k = Build.VERSION.SDK_INT >= 34 ? SHADE_MEDIA_IN_LIST : SHADE_MEDIA_AS_QS_TILE;
            sShadeKind = k;
        }
        return k;
    }

    public static boolean isQsTilePlayer() {
        return mediaShadeKind() == SHADE_MEDIA_AS_QS_TILE;
    }

    /** A13 及更早的行圆角管线（setTopRoundness 每轮布局重算），与媒体落点无关 */
    public static boolean isA13OrLower() {
        return Build.VERSION.SDK_INT <= 33;
    }

    /** "OriginOS 3 / Android 13 (sdk 33)" 这类可诊断字符串，只用于日志 */
    public static String label() {
        String l = sLabel;
        if (l == null) {
            l = "OriginOS " + osNameVersion() + " / Android " + Build.VERSION.RELEASE
                    + " (sdk " + Build.VERSION.SDK_INT + ")";
            sLabel = l;
        }
        return l;
    }

    /** "OriginOS 3" / "OriginOS 5"：设置页对外只说这一句，别写死版本 */
    public static String originName() {
        return "OriginOS " + osNameVersion();
    }

    private static String osNameVersion() {
        String v = prop("ro.vivo.os.build.display.id", "");
        int cut = v.lastIndexOf(' ');
        if (cut >= 0 && cut + 1 < v.length()) {
            v = v.substring(cut + 1);
        }
        return v.isEmpty() ? "?" : v;
    }

    public static String prop(String key, String def) {
        try {
            Object o = Class.forName("android.os.SystemProperties")
                    .getMethod("get", String.class, String.class)
                    .invoke(null, key, def);
            return o == null ? def : o.toString();
        } catch (Throwable t) {
            return def;
        }
    }

    /** 一次性把画像与关键 hook 目标命中情况打进日志，冒烟时先核对这行 */
    public static void report() {
        Log.i(FeatureManager.TAG, "os profile: " + label()
                + " mediaShade=" + (isQsTilePlayer() ? "qs_tile" : "notification_row"));
    }
}
