package com.android.systemui.media.controls.util;

import android.content.Context;
import com.android.systemui.util.Utils;

/* JADX INFO: compiled from: MediaFeatureFlag.kt */
/* JADX INFO: loaded from: classes4.dex */
public final class MediaFeatureFlag {
    public static final int $stable = 8;
    private final Context context;

    public MediaFeatureFlag(Context context) {
        this.context = context;
    }

    public final boolean getEnabled() {
        return Utils.useQsMediaPlayer(this.context);
    }
}
