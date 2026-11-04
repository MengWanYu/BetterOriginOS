package com.vivo.systemui.statusbar.utils;

import android.content.Context;
import android.os.Handler;
import com.android.systemui.res.R;
import com.vivo.common.proxy.ThemeIconManagerProxy;
import com.vivo.common.utils.LogUtils;
import java.util.Iterator;
import java.util.concurrent.CopyOnWriteArrayList;
import vivo.app.themeicon.SystemColorListener;
import vivo.app.themeicon.SystemFilletListener;

/* JADX INFO: loaded from: classes6.dex */
public class VivoColorAndCornerPicker {
    public static final String CORNER_STATE = "vivo_qs_center_corner_state";
    public static final int DEFAULT_FILLET = 45;
    public static final int FILLET_LEVEL_DEFAULT = 1;
    public static final int FILLET_LEVEL_DEFAULT_15DP = 15;
    public static final int FILLET_LEVEL_MELLOW = 3;
    public static final int FILLET_LEVEL_MELLOW_30DP = 30;
    public static final int FILLET_LEVEL_SHARP = 0;
    public static final int FILLET_LEVEL_SHARP_5DP = 5;
    public static final int FILLET_LEVEL_SMOOTH = 2;
    public static final int FILLET_LEVEL_SMOOTH_21DP = 21;
    public static final int FORTITUDE_FILLET = 15;
    public static final int MELLOW_FILLET = 90;
    public static final int SMOOTH_FILLET = 63;
    private static String TAG = "VivoColorAndCornerPicker";
    public static final String TILE_COlOR = "vivo_qs_center_tile_color";
    private Handler mCallbackHandler;
    private int mColorPrimary;
    private int mColorSecondary;
    private Context mContext;
    private int mFillet;
    private int mFilletLevel;
    private int mSystemColorMode;
    private ThemeIconManagerProxy mThemeIconProxy;
    private CopyOnWriteArrayList<CallBacks> mCallBacksList = new CopyOnWriteArrayList<>();
    private Runnable mSystemFilletChange = new Runnable() { // from class: com.vivo.systemui.statusbar.utils.VivoColorAndCornerPicker$$ExternalSyntheticLambda0
        @Override // java.lang.Runnable
        public final void run() {
            this.f$0.lambda$new$0();
        }
    };
    private Runnable mSystemColorChange = new Runnable() { // from class: com.vivo.systemui.statusbar.utils.VivoColorAndCornerPicker$$ExternalSyntheticLambda1
        @Override // java.lang.Runnable
        public final void run() {
            this.f$0.lambda$new$1();
        }
    };

    public interface CallBacks {
        default void onSystemColorChanged(int i, int i2, int i3) {
        }

        default void onSystemFilletChanged(int i, int i2) {
        }
    }

    public boolean supportColorAndCornerPicker() {
        return true;
    }

    public void unregisterSystemColorChangeListener() {
    }

    public void unregisterSystemFilletListener() {
    }

    public VivoColorAndCornerPicker(Context context) {
        this.mFillet = 15;
        this.mFilletLevel = 1;
        this.mColorPrimary = -13531649;
        this.mColorSecondary = -10947791;
        this.mSystemColorMode = -1;
        this.mContext = context;
        try {
            this.mThemeIconProxy = ThemeIconManagerProxy.getInstance();
            this.mCallbackHandler = new Handler(context.getMainLooper());
            this.mColorPrimary = this.mThemeIconProxy.getSystemPrimaryColor();
            this.mColorSecondary = this.mThemeIconProxy.getSystemSecondaryColor();
            this.mSystemColorMode = this.mThemeIconProxy.getSystemColorMode();
            this.mFillet = this.mThemeIconProxy.getSystemFillet();
            this.mFilletLevel = this.mThemeIconProxy.getSystemFilletLevel();
        } catch (Throwable th) {
            LogUtils.e(TAG, "VivoColorAndCornerPicker error:" + th);
        }
        LogUtils.d(TAG, "mColorPrimary : " + Integer.toHexString(this.mColorPrimary) + ", mColorSecondary : " + Integer.toHexString(this.mColorSecondary) + ", mFillet : " + this.mFillet + ", mFilletLevel : " + this.mFilletLevel);
        registerSystemColorChangeListener();
        registerSystemFilletListener();
    }

    public int getFillet() {
        return this.mFillet;
    }

    public int getSystemColorMode() {
        return this.mSystemColorMode;
    }

    public int getFilletLevel() {
        return this.mFilletLevel;
    }

    public int getColorPrimary() {
        return this.mColorPrimary;
    }

    public int getColorSecondary() {
        return this.mColorSecondary;
    }

    public void addCallBack(CallBacks callBacks) {
        if (!this.mCallBacksList.contains(callBacks)) {
            this.mCallBacksList.add(callBacks);
        }
        callBacks.onSystemColorChanged(this.mSystemColorMode, this.mColorPrimary, this.mColorSecondary);
        callBacks.onSystemFilletChanged(getFillet(), this.mFilletLevel);
    }

    public void removeCallBack(CallBacks callBacks) {
        if (this.mCallBacksList.contains(callBacks)) {
            this.mCallBacksList.remove(callBacks);
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$new$0() {
        Iterator<CallBacks> it = this.mCallBacksList.iterator();
        while (it.hasNext()) {
            it.next().onSystemFilletChanged(getFillet(), this.mFilletLevel);
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$new$1() {
        Iterator<CallBacks> it = this.mCallBacksList.iterator();
        while (it.hasNext()) {
            it.next().onSystemColorChanged(this.mSystemColorMode, this.mColorPrimary, this.mColorSecondary);
        }
    }

    public void notifySystemFilletChanged() {
        this.mCallbackHandler.removeCallbacks(this.mSystemFilletChange);
        this.mCallbackHandler.post(this.mSystemFilletChange);
    }

    public void notifySystemColorChanged() {
        this.mCallbackHandler.removeCallbacks(this.mSystemColorChange);
        this.mCallbackHandler.post(this.mSystemColorChange);
    }

    public void registerSystemColorChangeListener() {
        try {
            this.mThemeIconProxy.registerSystemColorChangeListener(new SystemColorListener() { // from class: com.vivo.systemui.statusbar.utils.VivoColorAndCornerPicker.1
                public void onSystemColorChanged(int i, int i2, int i3) {
                    LogUtils.d(VivoColorAndCornerPicker.TAG, "onSystemColorChanged, mode:" + Integer.toHexString(i) + ",primary:" + Integer.toHexString(i2) + ",secondary:" + Integer.toHexString(i3));
                    if (i2 == VivoColorAndCornerPicker.this.mColorPrimary && i3 == VivoColorAndCornerPicker.this.mColorSecondary && i == VivoColorAndCornerPicker.this.mSystemColorMode) {
                        return;
                    }
                    VivoColorAndCornerPicker vivoColorAndCornerPicker = VivoColorAndCornerPicker.this;
                    if (i == 0) {
                        i2 = vivoColorAndCornerPicker.mContext.getColor(R.color.vivo_qs_tile_active_color_default);
                    }
                    vivoColorAndCornerPicker.mColorPrimary = i2;
                    VivoColorAndCornerPicker.this.mColorSecondary = i3;
                    VivoColorAndCornerPicker.this.mSystemColorMode = i;
                    VivoColorAndCornerPicker.this.notifySystemColorChanged();
                }
            });
        } catch (Throwable th) {
            LogUtils.e(TAG, "registerSystemColorChangeListener error:" + th);
        }
    }

    public void registerSystemFilletListener() {
        try {
            this.mThemeIconProxy.registerSystemFilletChangeListener(new SystemFilletListener() { // from class: com.vivo.systemui.statusbar.utils.VivoColorAndCornerPicker.2
                public void onSystemFilletChanged(int i, int i2) {
                    LogUtils.i(VivoColorAndCornerPicker.TAG, "onSystemFilletChanged,level:" + i + ",fillet:" + i2);
                    if (VivoColorAndCornerPicker.this.mFilletLevel == i && VivoColorAndCornerPicker.this.mFillet == i2) {
                        return;
                    }
                    VivoColorAndCornerPicker.this.mFilletLevel = i;
                    VivoColorAndCornerPicker.this.mFillet = i2;
                    VivoColorAndCornerPicker.this.notifySystemFilletChanged();
                }
            });
        } catch (Throwable th) {
            LogUtils.e(TAG, "registerSystemFilletListener error:" + th);
        }
    }

    public static int getNotificationDefFillet(Context context, int i) {
        int dimensionPixelSize = context.getResources().getDimensionPixelSize(R.dimen.notification_shadow_radius);
        if (i == 0) {
            return context.getResources().getDimensionPixelSize(R.dimen.notification_shadow_radius_sharp);
        }
        if (i == 1) {
            return context.getResources().getDimensionPixelSize(R.dimen.notification_shadow_radius);
        }
        if (i != 2) {
            return i != 3 ? dimensionPixelSize : context.getResources().getDimensionPixelSize(R.dimen.notification_shadow_radius_mellow);
        }
        return context.getResources().getDimensionPixelSize(R.dimen.notification_shadow_radius_smooth);
    }
}
