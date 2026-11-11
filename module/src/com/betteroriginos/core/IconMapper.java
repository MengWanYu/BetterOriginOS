package com.betteroriginos.core;

import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;

public final class IconMapper {
    private IconMapper() {
    }

    /** 应用图标：取目标 APK 本身图标，无则回退系统默认 */
    public static Drawable forPackage(Context ctx, String pkg) {
        PackageManager pm = ctx.getPackageManager();
        Drawable icon = null;
        try {
            icon = pm.getApplicationIcon(pkg);
        } catch (Throwable ignored) {
        }
        if (icon == null) {
            icon = pm.getDefaultActivityIcon();
        }
        return icon;
    }

    public static String labelOf(Context ctx, String pkg) {
        PackageManager pm = ctx.getPackageManager();
        try {
            return String.valueOf(pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)));
        } catch (Throwable t) {
            return pkg;
        }
    }
}
