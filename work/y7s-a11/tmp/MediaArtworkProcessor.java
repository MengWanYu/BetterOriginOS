package com.android.systemui.statusbar;

import android.graphics.Bitmap;
import android.graphics.Point;
import javax.inject.Inject;
import javax.inject.Singleton;
import kotlin.Metadata;

/* JADX INFO: compiled from: MediaArtworkProcessor.kt */
/* JADX INFO: loaded from: classes2.dex */
@Singleton
@Metadata(bv = {1, 0, 3}, d1 = {"\u0000(\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\b\u0007\u0018\u00002\u00020\u0001B\u0007\b\u0007¢\u0006\u0002\u0010\u0002J\u0006\u0010\u0007\u001a\u00020\bJ\u0018\u0010\t\u001a\u0004\u0018\u00010\u00042\u0006\u0010\n\u001a\u00020\u000b2\u0006\u0010\f\u001a\u00020\u0004R\u0010\u0010\u0003\u001a\u0004\u0018\u00010\u0004X\u0082\u000e¢\u0006\u0002\n\u0000R\u000e\u0010\u0005\u001a\u00020\u0006X\u0082\u0004¢\u0006\u0002\n\u0000¨\u0006\r"}, d2 = {"Lcom/android/systemui/statusbar/MediaArtworkProcessor;", "", "()V", "mArtworkCache", "Landroid/graphics/Bitmap;", "mTmpSize", "Landroid/graphics/Point;", "clearCache", "", "processArtwork", "context", "Landroid/content/Context;", "artwork", "app_xxhdpiRatio_20_9Rom_13_0DemesticAndroid_30Release"}, k = 1, mv = {1, 1, 16})
public final class MediaArtworkProcessor {
    private Bitmap mArtworkCache;
    private final Point mTmpSize = new Point();

    @Inject
    public MediaArtworkProcessor() {
    }

    /* JADX WARN: Removed duplicated region for block: B:36:0x00ed  */
    /* JADX WARN: Removed duplicated region for block: B:38:0x00f2  */
    /* JADX WARN: Removed duplicated region for block: B:41:0x00fa  */
    /* JADX WARN: Removed duplicated region for block: B:45:0x0101  */
    /* JADX WARN: Removed duplicated region for block: B:47:0x0106  */
    /* JADX WARN: Removed duplicated region for block: B:50:0x010e  */
    @org.jetbrains.annotations.Nullable
    /*
        Code decompiled incorrectly, please refer to instructions dump.
        To view partially-correct add '--show-bad-code' argument
    */
    public final android.graphics.Bitmap processArtwork(@org.jetbrains.annotations.NotNull android.content.Context r11, @org.jetbrains.annotations.NotNull android.graphics.Bitmap r12) throws java.lang.Throwable {
        /*
            Method dump skipped, instruction units count: 274
            To view this dump add '--comments-level debug' option
        */
        throw new UnsupportedOperationException("Method not decompiled: com.android.systemui.statusbar.MediaArtworkProcessor.processArtwork(android.content.Context, android.graphics.Bitmap):android.graphics.Bitmap");
    }

    public final void clearCache() {
        Bitmap bitmap = this.mArtworkCache;
        if (bitmap != null) {
            bitmap.recycle();
        }
        this.mArtworkCache = (Bitmap) null;
    }
}
