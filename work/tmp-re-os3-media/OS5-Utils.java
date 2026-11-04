package com.android.systemui.util;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.provider.Settings;
import android.view.DisplayCutout;
import com.android.internal.R;
import com.android.internal.policy.SystemBarUtils;
import com.android.systemui.settings.DisplayTracker;
import com.android.systemui.shared.system.QuickStepContract;
import java.util.List;
import java.util.function.Consumer;

/* JADX INFO: loaded from: classes5.dex */
public class Utils {
    private static Boolean sUseQsMediaPlayer;

    public static boolean useQsMediaPlayer(Context context) {
        return false;
    }

    @Deprecated
    public static <T> void safeForeach(List<T> list, Consumer<T> consumer) {
        for (int size = list.size() - 1; size >= 0; size--) {
            T t = list.get(size);
            if (t != null) {
                consumer.accept(t);
            }
        }
    }

    public static boolean isHeadlessRemoteDisplayProvider(PackageManager packageManager, String str) {
        if (packageManager.checkPermission("android.permission.REMOTE_DISPLAY_PROVIDER", str) != 0) {
            return false;
        }
        Intent intent = new Intent("android.intent.action.MAIN");
        intent.addCategory("android.intent.category.LAUNCHER");
        intent.setPackage(str);
        return packageManager.queryIntentActivities(intent, 0).isEmpty();
    }

    public static boolean isGesturalModeOnDefaultDisplay(Context context, DisplayTracker displayTracker, int i) {
        return context.getDisplayId() == displayTracker.getDefaultDisplayId() && QuickStepContract.isGesturalMode(i);
    }

    public static boolean isDeviceFoldable(Context context) {
        return context.getResources().getIntArray(R.array.config_foldedDeviceStates).length != 0;
    }

    public static boolean useMediaResumption(Context context) {
        return useQsMediaPlayer(context) && Settings.Secure.getInt(context.getContentResolver(), "qs_media_resumption", 1) > 0;
    }

    public static boolean useCollapsedMediaInLandscape(Resources resources) {
        return resources.getBoolean(com.android.systemui.res.R.bool.config_quickSettingsMediaLandscapeCollapsed);
    }

    @Deprecated
    public static int getStatusBarHeaderHeightKeyguard(Context context) {
        int statusBarHeight = SystemBarUtils.getStatusBarHeight(context);
        DisplayCutout cutout = context.getDisplay().getCutout();
        return Math.max(statusBarHeight, context.getResources().getDimensionPixelSize(com.android.systemui.res.R.dimen.status_bar_header_height_keyguard) + (cutout == null ? 0 : cutout.getWaterfallInsets().top));
    }
}
