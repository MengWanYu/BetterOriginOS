package com.android.systemui.plugins.vivo;

import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import com.android.systemui.plugins.Plugin;
import com.android.systemui.plugins.annotations.ProvidesInterface;
import java.util.Map;

/* JADX INFO: loaded from: classes2.dex */
@ProvidesInterface(action = VivoPanelStackSection.ACTION, version = 1)
public interface VivoPanelStackSection extends Plugin {
    public static final String ACTION = "com.android.systemui.action.PLUGIN_VIVO_STACK_SECTION";
    public static final int CLICK_NOTI = 1;
    public static final int EXIT_PANEL = 3;
    public static final int SCREEN_OFF = 2;
    public static final int UNKNOWN = 0;
    public static final int VERSION = 1;

    @ProvidesInterface(version = 1)
    public interface IVivoSectionCallback {
        public static final int VERSION = 1;

        void notifyHeightChanged(boolean z);
    }

    boolean allowEmptyShade();

    int getIntrinsicHeight();

    String getSectionTag();

    boolean handleMotionEvent();

    View loadContent(IVivoSectionCallback iVivoSectionCallback, ViewGroup viewGroup);

    void onQuerySuperXData(String str);

    void onSuperXSettingsChanged(String str, String str2);

    void processSuperXData(Map<String, String> map, byte[] bArr, Bundle bundle);

    void reinflateView();

    void reportVisibleToUser(boolean z, int i);

    boolean shouldClipToActualHeight();
}
