package com.android.systemui.statusbar.notification;

import com.android.systemui.Flags;
import java.util.Iterator;
import java.util.Map;
import kotlin.collections.CollectionsKt___CollectionsKt;
import kotlin.collections.IntIterator;
import kotlin.ranges.IntRange;

/* JADX INFO: compiled from: Roundable.kt */
/* JADX INFO: loaded from: classes5.dex */
public interface Roundable {
    int getClipHeight();

    RoundableState getRoundableState();

    default float getTopRoundness() {
        return getRoundableState().getTopRoundness$app_xxhdpiRatio_20_9Iqoo_rom_15_0DemesticAndroid_35Release();
    }

    default float getBottomRoundness() {
        return getRoundableState().getBottomRoundness$app_xxhdpiRatio_20_9Iqoo_rom_15_0DemesticAndroid_35Release();
    }

    default float getBottomCornerRadius() {
        if (Flags.notificationsImprovedHunAnimation()) {
            return getRoundableState().getBottomCornerRadius$app_xxhdpiRatio_20_9Iqoo_rom_15_0DemesticAndroid_35Release();
        }
        return getMaxRadius() * getBottomRoundness();
    }

    default float getTopCornerRadius() {
        if (Flags.notificationsImprovedHunAnimation()) {
            return getRoundableState().getTopCornerRadius$app_xxhdpiRatio_20_9Iqoo_rom_15_0DemesticAndroid_35Release();
        }
        return getMaxRadius() * getTopRoundness();
    }

    default float getMaxRadius() {
        return getRoundableState().getMaxRadius$app_xxhdpiRatio_20_9Iqoo_rom_15_0DemesticAndroid_35Release();
    }

    default float[] getUpdatedRadii() {
        float[] radiiBuffer$app_xxhdpiRatio_20_9Iqoo_rom_15_0DemesticAndroid_35Release = getRoundableState().getRadiiBuffer$app_xxhdpiRatio_20_9Iqoo_rom_15_0DemesticAndroid_35Release();
        updateRadii(getTopCornerRadius(), getBottomCornerRadius(), radiiBuffer$app_xxhdpiRatio_20_9Iqoo_rom_15_0DemesticAndroid_35Release);
        return radiiBuffer$app_xxhdpiRatio_20_9Iqoo_rom_15_0DemesticAndroid_35Release;
    }

    default boolean requestTopRoundness(float f, SourceType sourceType, boolean z) {
        Map<SourceType, Float> topRoundnessMap$app_xxhdpiRatio_20_9Iqoo_rom_15_0DemesticAndroid_35Release = getRoundableState().getTopRoundnessMap$app_xxhdpiRatio_20_9Iqoo_rom_15_0DemesticAndroid_35Release();
        Float fM10339maxOrNull = CollectionsKt___CollectionsKt.m10339maxOrNull((Iterable) topRoundnessMap$app_xxhdpiRatio_20_9Iqoo_rom_15_0DemesticAndroid_35Release.values());
        float fFloatValue = fM10339maxOrNull != null ? fM10339maxOrNull.floatValue() : 0.0f;
        if (f == 0.0f) {
            topRoundnessMap$app_xxhdpiRatio_20_9Iqoo_rom_15_0DemesticAndroid_35Release.remove(sourceType);
        } else {
            topRoundnessMap$app_xxhdpiRatio_20_9Iqoo_rom_15_0DemesticAndroid_35Release.put(sourceType, Float.valueOf(f));
        }
        Float fM10339maxOrNull2 = CollectionsKt___CollectionsKt.m10339maxOrNull((Iterable) topRoundnessMap$app_xxhdpiRatio_20_9Iqoo_rom_15_0DemesticAndroid_35Release.values());
        float fFloatValue2 = fM10339maxOrNull2 != null ? fM10339maxOrNull2.floatValue() : 0.0f;
        if (fFloatValue == fFloatValue2) {
            return false;
        }
        getRoundableState().setTopRoundness$app_xxhdpiRatio_20_9Iqoo_rom_15_0DemesticAndroid_35Release(fFloatValue2, (getRoundableState().isTopAnimating$app_xxhdpiRatio_20_9Iqoo_rom_15_0DemesticAndroid_35Release() && (Math.abs(fFloatValue2 - fFloatValue) > 0.5f ? 1 : (Math.abs(fFloatValue2 - fFloatValue) == 0.5f ? 0 : -1)) > 0) || z);
        return true;
    }

    default boolean requestTopRoundness(float f, SourceType sourceType) {
        return requestTopRoundness(f, sourceType, getRoundableState().getTargetView$app_xxhdpiRatio_20_9Iqoo_rom_15_0DemesticAndroid_35Release().isShown());
    }

    default boolean requestBottomRoundness(float f, SourceType sourceType, boolean z) {
        Map<SourceType, Float> bottomRoundnessMap$app_xxhdpiRatio_20_9Iqoo_rom_15_0DemesticAndroid_35Release = getRoundableState().getBottomRoundnessMap$app_xxhdpiRatio_20_9Iqoo_rom_15_0DemesticAndroid_35Release();
        Float fM10339maxOrNull = CollectionsKt___CollectionsKt.m10339maxOrNull((Iterable) bottomRoundnessMap$app_xxhdpiRatio_20_9Iqoo_rom_15_0DemesticAndroid_35Release.values());
        float fFloatValue = fM10339maxOrNull != null ? fM10339maxOrNull.floatValue() : 0.0f;
        if (f == 0.0f) {
            bottomRoundnessMap$app_xxhdpiRatio_20_9Iqoo_rom_15_0DemesticAndroid_35Release.remove(sourceType);
        } else {
            bottomRoundnessMap$app_xxhdpiRatio_20_9Iqoo_rom_15_0DemesticAndroid_35Release.put(sourceType, Float.valueOf(f));
        }
        Float fM10339maxOrNull2 = CollectionsKt___CollectionsKt.m10339maxOrNull((Iterable) bottomRoundnessMap$app_xxhdpiRatio_20_9Iqoo_rom_15_0DemesticAndroid_35Release.values());
        float fFloatValue2 = fM10339maxOrNull2 != null ? fM10339maxOrNull2.floatValue() : 0.0f;
        if (fFloatValue == fFloatValue2) {
            return false;
        }
        getRoundableState().setBottomRoundness$app_xxhdpiRatio_20_9Iqoo_rom_15_0DemesticAndroid_35Release(fFloatValue2, (getRoundableState().isBottomAnimating$app_xxhdpiRatio_20_9Iqoo_rom_15_0DemesticAndroid_35Release() && (Math.abs(fFloatValue2 - fFloatValue) > 0.5f ? 1 : (Math.abs(fFloatValue2 - fFloatValue) == 0.5f ? 0 : -1)) > 0) || z);
        return true;
    }

    default boolean requestBottomRoundness(float f, SourceType sourceType) {
        return requestBottomRoundness(f, sourceType, getRoundableState().getTargetView$app_xxhdpiRatio_20_9Iqoo_rom_15_0DemesticAndroid_35Release().isShown());
    }

    default boolean requestRoundness(float f, float f2, SourceType sourceType, boolean z) {
        return requestTopRoundness(f, sourceType, z) || requestBottomRoundness(f2, sourceType, z);
    }

    default boolean requestRoundness(float f, float f2, SourceType sourceType) {
        return requestRoundness(f, f2, sourceType, getRoundableState().getTargetView$app_xxhdpiRatio_20_9Iqoo_rom_15_0DemesticAndroid_35Release().isShown());
    }

    default void requestRoundnessReset(SourceType sourceType, boolean z) {
        requestRoundness(0.0f, 0.0f, sourceType, z);
    }

    default void requestRoundnessReset(SourceType sourceType) {
        requestRoundnessReset(sourceType, getRoundableState().getTargetView$app_xxhdpiRatio_20_9Iqoo_rom_15_0DemesticAndroid_35Release().isShown());
    }

    default void applyRoundnessAndInvalidate() {
        getRoundableState().getTargetView$app_xxhdpiRatio_20_9Iqoo_rom_15_0DemesticAndroid_35Release().invalidate();
    }

    default boolean hasRoundedCorner() {
        return (getTopRoundness() == 0.0f && getBottomRoundness() == 0.0f) ? false : true;
    }

    default void updateRadii(float f, float f2, float[] fArr) {
        if (fArr.length != 8) {
            throw new IllegalStateException(("Unexpected radiiBuffer size " + fArr.length).toString());
        }
        if (fArr[0] == f && fArr[4] == f2) {
            return;
        }
        Iterator it = new IntRange(0, 3).iterator();
        while (it.hasNext()) {
            fArr[((IntIterator) it).nextInt()] = f;
        }
        Iterator it2 = new IntRange(4, 7).iterator();
        while (it2.hasNext()) {
            fArr[((IntIterator) it2).nextInt()] = f2;
        }
    }
}
