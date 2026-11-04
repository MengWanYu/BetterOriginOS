package com.android.systemui.media;

import android.content.Context;
import android.provider.Settings;
import android.service.notification.StatusBarNotification;
import android.support.v4.media.MediaMetadataCompat;
import com.android.internal.logging.InstanceId;
import com.android.systemui.util.Utils;
import java.util.List;
import kotlin.Metadata;
import kotlin.collections.CollectionsKt;
import kotlin.jvm.internal.Intrinsics;

/* JADX INFO: compiled from: MediaDataManager.kt */
/* JADX INFO: loaded from: classes2.dex */
@Metadata(d1 = {"\u00002\n\u0000\n\u0002\u0010\u0011\n\u0002\u0010\u000e\n\u0002\b\u0002\n\u0002\u0010\u000b\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0006\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0000\u001a\u0010\u0010\u0010\u001a\u00020\u00052\u0006\u0010\u0011\u001a\u00020\u0012H\u0002\u001a\u000e\u0010\u0013\u001a\u00020\u00052\u0006\u0010\u0014\u001a\u00020\u0015\"\u0016\u0010\u0000\u001a\b\u0012\u0004\u0012\u00020\u00020\u0001X\u0082\u0004¢\u0006\u0004\n\u0002\u0010\u0003\"\u000e\u0010\u0004\u001a\u00020\u0005X\u0082T¢\u0006\u0002\n\u0000\"\u001c\u0010\u0006\u001a\u00020\u00078\u0000X\u0081\u0004¢\u0006\u000e\n\u0000\u0012\u0004\b\b\u0010\t\u001a\u0004\b\n\u0010\u000b\"\u000e\u0010\f\u001a\u00020\u0002X\u0082T¢\u0006\u0002\n\u0000\"\u000e\u0010\r\u001a\u00020\u000eX\u0082\u0004¢\u0006\u0002\n\u0000\"\u000e\u0010\u000f\u001a\u00020\u0002X\u0082T¢\u0006\u0002\n\u0000¨\u0006\u0016"}, d2 = {"ART_URIS", "", "", "[Ljava/lang/String;", "DEBUG", "", "EMPTY_SMARTSPACE_MEDIA_DATA", "Lcom/android/systemui/media/SmartspaceMediaData;", "getEMPTY_SMARTSPACE_MEDIA_DATA$annotations", "()V", "getEMPTY_SMARTSPACE_MEDIA_DATA", "()Lcom/android/systemui/media/SmartspaceMediaData;", "EXTRAS_SMARTSPACE_DISMISS_INTENT_KEY", "LOADING", "Lcom/android/systemui/media/MediaData;", "TAG", "allowMediaRecommendations", "context", "Landroid/content/Context;", "isMediaNotification", "sbn", "Landroid/service/notification/StatusBarNotification;", "app_xxhdpiRatio_20_9Rom_13_5DemesticAndroid_33Release"}, k = 2, mv = {1, 6, 0}, xi = 48)
public final class MediaDataManagerKt {
    private static final String[] ART_URIS = {MediaMetadataCompat.METADATA_KEY_ALBUM_ART_URI, MediaMetadataCompat.METADATA_KEY_ART_URI, MediaMetadataCompat.METADATA_KEY_DISPLAY_ICON_URI};
    private static final boolean DEBUG = true;
    private static final SmartspaceMediaData EMPTY_SMARTSPACE_MEDIA_DATA;
    private static final String EXTRAS_SMARTSPACE_DISMISS_INTENT_KEY = "dismiss_intent";
    private static final MediaData LOADING;
    private static final String TAG = "MediaDataManager";

    public static /* synthetic */ void getEMPTY_SMARTSPACE_MEDIA_DATA$annotations() {
    }

    static {
        List listEmptyList = CollectionsKt.emptyList();
        List listEmptyList2 = CollectionsKt.emptyList();
        InstanceId instanceIdFakeInstanceId = InstanceId.fakeInstanceId(-1);
        Intrinsics.checkNotNullExpressionValue(instanceIdFakeInstanceId, "fakeInstanceId(-1)");
        LOADING = new MediaData(-1, false, null, null, null, null, null, listEmptyList, listEmptyList2, null, "INVALID", null, null, null, true, null, 0, false, null, false, null, false, 0L, instanceIdFakeInstanceId, -1, 8323584, null);
        List listEmptyList3 = CollectionsKt.emptyList();
        InstanceId instanceIdFakeInstanceId2 = InstanceId.fakeInstanceId(-1);
        Intrinsics.checkNotNullExpressionValue(instanceIdFakeInstanceId2, "fakeInstanceId(-1)");
        EMPTY_SMARTSPACE_MEDIA_DATA = new SmartspaceMediaData("INVALID", false, "INVALID", null, listEmptyList3, null, 0L, instanceIdFakeInstanceId2);
    }

    public static final SmartspaceMediaData getEMPTY_SMARTSPACE_MEDIA_DATA() {
        return EMPTY_SMARTSPACE_MEDIA_DATA;
    }

    public static final boolean isMediaNotification(StatusBarNotification sbn) {
        Intrinsics.checkNotNullParameter(sbn, "sbn");
        return sbn.getNotification().isMediaNotification();
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static final boolean allowMediaRecommendations(Context context) {
        return Utils.useQsMediaPlayer(context) && Settings.Secure.getInt(context.getContentResolver(), "qs_media_recommend", 1) > 0;
    }
}
