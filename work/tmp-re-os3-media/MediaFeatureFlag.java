package com.android.systemui.media;

import android.content.Context;
import com.android.systemui.util.Utils;
import javax.inject.Inject;
import kotlin.Metadata;
import kotlin.jvm.internal.Intrinsics;

/* JADX INFO: compiled from: MediaFeatureFlag.kt */
/* JADX INFO: loaded from: classes2.dex */
@Metadata(d1 = {"\u0000\u001a\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0010\u000b\n\u0002\b\u0003\u0018\u00002\u00020\u0001B\u000f\b\u0007\u0012\u0006\u0010\u0002\u001a\u00020\u0003¢\u0006\u0002\u0010\u0004R\u000e\u0010\u0002\u001a\u00020\u0003X\u0082\u0004¢\u0006\u0002\n\u0000R\u0011\u0010\u0005\u001a\u00020\u00068F¢\u0006\u0006\u001a\u0004\b\u0007\u0010\b¨\u0006\t"}, d2 = {"Lcom/android/systemui/media/MediaFeatureFlag;", "", "context", "Landroid/content/Context;", "(Landroid/content/Context;)V", "enabled", "", "getEnabled", "()Z", "app_xxhdpiRatio_20_9Rom_13_5DemesticAndroid_33Release"}, k = 1, mv = {1, 6, 0}, xi = 48)
public final class MediaFeatureFlag {
    private final Context context;

    @Inject
    public MediaFeatureFlag(Context context) {
        Intrinsics.checkNotNullParameter(context, "context");
        this.context = context;
    }

    public final boolean getEnabled() {
        return Utils.useQsMediaPlayer(this.context);
    }
}
