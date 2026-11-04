package com.android.systemui.media;

import android.app.PendingIntent;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.Icon;
import android.media.session.MediaSession;
import com.android.systemui.pip.phone.PipMenuActivityController;
import com.android.systemui.statusbar.phone.NavigationBarInflaterView;
import com.vivo.assistant.vcorentsdk.utils.Constants;
import java.util.List;
import kotlin.Metadata;
import kotlin.jvm.internal.DefaultConstructorMarker;
import kotlin.jvm.internal.Intrinsics;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/* JADX INFO: compiled from: MediaData.kt */
/* JADX INFO: loaded from: classes2.dex */
@Metadata(bv = {1, 0, 3}, d1 = {"\u0000Z\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0000\n\u0002\u0010\b\n\u0000\n\u0002\u0010\u000b\n\u0002\b\u0002\n\u0002\u0010\u000e\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\r\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010 \n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\bA\b\u0086\b\u0018\u00002\u00020\u0001BÅ\u0001\u0012\u0006\u0010\u0002\u001a\u00020\u0003\u0012\b\b\u0002\u0010\u0004\u001a\u00020\u0005\u0012\u0006\u0010\u0006\u001a\u00020\u0003\u0012\b\u0010\u0007\u001a\u0004\u0018\u00010\b\u0012\b\u0010\t\u001a\u0004\u0018\u00010\n\u0012\b\u0010\u000b\u001a\u0004\u0018\u00010\f\u0012\b\u0010\r\u001a\u0004\u0018\u00010\f\u0012\b\u0010\u000e\u001a\u0004\u0018\u00010\u000f\u0012\f\u0010\u0010\u001a\b\u0012\u0004\u0012\u00020\u00120\u0011\u0012\f\u0010\u0013\u001a\b\u0012\u0004\u0012\u00020\u00030\u0011\u0012\u0006\u0010\u0014\u001a\u00020\b\u0012\b\u0010\u0015\u001a\u0004\u0018\u00010\u0016\u0012\b\u0010\u0017\u001a\u0004\u0018\u00010\u0018\u0012\b\u0010\u0019\u001a\u0004\u0018\u00010\u001a\u0012\u0006\u0010\u001b\u001a\u00020\u0005\u0012\b\u0010\u001c\u001a\u0004\u0018\u00010\u001d\u0012\b\b\u0002\u0010\u001e\u001a\u00020\u0005\u0012\n\b\u0002\u0010\u001f\u001a\u0004\u0018\u00010\b\u0012\b\b\u0002\u0010 \u001a\u00020\u0005¢\u0006\u0002\u0010!J\t\u0010F\u001a\u00020\u0003HÆ\u0003J\u000f\u0010G\u001a\b\u0012\u0004\u0012\u00020\u00030\u0011HÆ\u0003J\t\u0010H\u001a\u00020\bHÆ\u0003J\u000b\u0010I\u001a\u0004\u0018\u00010\u0016HÆ\u0003J\u000b\u0010J\u001a\u0004\u0018\u00010\u0018HÆ\u0003J\u000b\u0010K\u001a\u0004\u0018\u00010\u001aHÆ\u0003J\t\u0010L\u001a\u00020\u0005HÆ\u0003J\u000b\u0010M\u001a\u0004\u0018\u00010\u001dHÆ\u0003J\t\u0010N\u001a\u00020\u0005HÆ\u0003J\u000b\u0010O\u001a\u0004\u0018\u00010\bHÆ\u0003J\t\u0010P\u001a\u00020\u0005HÆ\u0003J\t\u0010Q\u001a\u00020\u0005HÆ\u0003J\t\u0010R\u001a\u00020\u0003HÆ\u0003J\u000b\u0010S\u001a\u0004\u0018\u00010\bHÆ\u0003J\u000b\u0010T\u001a\u0004\u0018\u00010\nHÆ\u0003J\u000b\u0010U\u001a\u0004\u0018\u00010\fHÆ\u0003J\u000b\u0010V\u001a\u0004\u0018\u00010\fHÆ\u0003J\u000b\u0010W\u001a\u0004\u0018\u00010\u000fHÆ\u0003J\u000f\u0010X\u001a\b\u0012\u0004\u0012\u00020\u00120\u0011HÆ\u0003Jç\u0001\u0010Y\u001a\u00020\u00002\b\b\u0002\u0010\u0002\u001a\u00020\u00032\b\b\u0002\u0010\u0004\u001a\u00020\u00052\b\b\u0002\u0010\u0006\u001a\u00020\u00032\n\b\u0002\u0010\u0007\u001a\u0004\u0018\u00010\b2\n\b\u0002\u0010\t\u001a\u0004\u0018\u00010\n2\n\b\u0002\u0010\u000b\u001a\u0004\u0018\u00010\f2\n\b\u0002\u0010\r\u001a\u0004\u0018\u00010\f2\n\b\u0002\u0010\u000e\u001a\u0004\u0018\u00010\u000f2\u000e\b\u0002\u0010\u0010\u001a\b\u0012\u0004\u0012\u00020\u00120\u00112\u000e\b\u0002\u0010\u0013\u001a\b\u0012\u0004\u0012\u00020\u00030\u00112\b\b\u0002\u0010\u0014\u001a\u00020\b2\n\b\u0002\u0010\u0015\u001a\u0004\u0018\u00010\u00162\n\b\u0002\u0010\u0017\u001a\u0004\u0018\u00010\u00182\n\b\u0002\u0010\u0019\u001a\u0004\u0018\u00010\u001a2\b\b\u0002\u0010\u001b\u001a\u00020\u00052\n\b\u0002\u0010\u001c\u001a\u0004\u0018\u00010\u001d2\b\b\u0002\u0010\u001e\u001a\u00020\u00052\n\b\u0002\u0010\u001f\u001a\u0004\u0018\u00010\b2\b\b\u0002\u0010 \u001a\u00020\u0005HÆ\u0001J\u0013\u0010Z\u001a\u00020\u00052\b\u0010[\u001a\u0004\u0018\u00010\u0001HÖ\u0003J\t\u0010\\\u001a\u00020\u0003HÖ\u0001J\t\u0010]\u001a\u00020\bHÖ\u0001R\u0017\u0010\u0010\u001a\b\u0012\u0004\u0012\u00020\u00120\u0011¢\u0006\b\n\u0000\u001a\u0004\b\"\u0010#R\u0017\u0010\u0013\u001a\b\u0012\u0004\u0012\u00020\u00030\u0011¢\u0006\b\n\u0000\u001a\u0004\b$\u0010#R\u001a\u0010\u001b\u001a\u00020\u0005X\u0086\u000e¢\u0006\u000e\n\u0000\u001a\u0004\b%\u0010&\"\u0004\b'\u0010(R\u0013\u0010\u0007\u001a\u0004\u0018\u00010\b¢\u0006\b\n\u0000\u001a\u0004\b)\u0010*R\u0013\u0010\t\u001a\u0004\u0018\u00010\n¢\u0006\b\n\u0000\u001a\u0004\b+\u0010,R\u0013\u0010\u000b\u001a\u0004\u0018\u00010\f¢\u0006\b\n\u0000\u001a\u0004\b-\u0010.R\u0013\u0010\u000e\u001a\u0004\u0018\u00010\u000f¢\u0006\b\n\u0000\u001a\u0004\b/\u00100R\u0011\u0010\u0006\u001a\u00020\u0003¢\u0006\b\n\u0000\u001a\u0004\b1\u00102R\u0013\u0010\u0017\u001a\u0004\u0018\u00010\u0018¢\u0006\b\n\u0000\u001a\u0004\b3\u00104R\u0013\u0010\u0019\u001a\u0004\u0018\u00010\u001a¢\u0006\b\n\u0000\u001a\u0004\b5\u00106R\u001a\u0010 \u001a\u00020\u0005X\u0086\u000e¢\u0006\u000e\n\u0000\u001a\u0004\b7\u0010&\"\u0004\b8\u0010(R\u0011\u0010\u0004\u001a\u00020\u0005¢\u0006\b\n\u0000\u001a\u0004\b9\u0010&R\u0013\u0010\u001f\u001a\u0004\u0018\u00010\b¢\u0006\b\n\u0000\u001a\u0004\b:\u0010*R\u0011\u0010\u0014\u001a\u00020\b¢\u0006\b\n\u0000\u001a\u0004\b;\u0010*R\u001c\u0010\u001c\u001a\u0004\u0018\u00010\u001dX\u0086\u000e¢\u0006\u000e\n\u0000\u001a\u0004\b<\u0010=\"\u0004\b>\u0010?R\u001a\u0010\u001e\u001a\u00020\u0005X\u0086\u000e¢\u0006\u000e\n\u0000\u001a\u0004\b@\u0010&\"\u0004\bA\u0010(R\u0013\u0010\r\u001a\u0004\u0018\u00010\f¢\u0006\b\n\u0000\u001a\u0004\bB\u0010.R\u0013\u0010\u0015\u001a\u0004\u0018\u00010\u0016¢\u0006\b\n\u0000\u001a\u0004\bC\u0010DR\u0011\u0010\u0002\u001a\u00020\u0003¢\u0006\b\n\u0000\u001a\u0004\bE\u00102¨\u0006^"}, d2 = {"Lcom/android/systemui/media/MediaData;", "", "userId", "", "initialized", "", "backgroundColor", "app", "", "appIcon", "Landroid/graphics/drawable/Drawable;", "artist", "", "song", "artwork", "Landroid/graphics/drawable/Icon;", PipMenuActivityController.EXTRA_ACTIONS, "", "Lcom/android/systemui/media/MediaAction;", "actionsToShowInCompact", Constants.KEY_PACKAGE_NAME, "token", "Landroid/media/session/MediaSession$Token;", "clickIntent", "Landroid/app/PendingIntent;", "device", "Lcom/android/systemui/media/MediaDeviceData;", "active", "resumeAction", "Ljava/lang/Runnable;", "resumption", "notificationKey", "hasCheckedForResume", "(IZILjava/lang/String;Landroid/graphics/drawable/Drawable;Ljava/lang/CharSequence;Ljava/lang/CharSequence;Landroid/graphics/drawable/Icon;Ljava/util/List;Ljava/util/List;Ljava/lang/String;Landroid/media/session/MediaSession$Token;Landroid/app/PendingIntent;Lcom/android/systemui/media/MediaDeviceData;ZLjava/lang/Runnable;ZLjava/lang/String;Z)V", "getActions", "()Ljava/util/List;", "getActionsToShowInCompact", "getActive", "()Z", "setActive", "(Z)V", "getApp", "()Ljava/lang/String;", "getAppIcon", "()Landroid/graphics/drawable/Drawable;", "getArtist", "()Ljava/lang/CharSequence;", "getArtwork", "()Landroid/graphics/drawable/Icon;", "getBackgroundColor", "()I", "getClickIntent", "()Landroid/app/PendingIntent;", "getDevice", "()Lcom/android/systemui/media/MediaDeviceData;", "getHasCheckedForResume", "setHasCheckedForResume", "getInitialized", "getNotificationKey", "getPackageName", "getResumeAction", "()Ljava/lang/Runnable;", "setResumeAction", "(Ljava/lang/Runnable;)V", "getResumption", "setResumption", "getSong", "getToken", "()Landroid/media/session/MediaSession$Token;", "getUserId", "component1", "component10", "component11", "component12", "component13", "component14", "component15", "component16", "component17", "component18", "component19", "component2", "component3", "component4", "component5", "component6", "component7", "component8", "component9", "copy", "equals", "other", "hashCode", "toString", "app_xxhdpiRatio_20_9Rom_13_0DemesticAndroid_30Release"}, k = 1, mv = {1, 1, 16})
public final /* data */ class MediaData {

    @NotNull
    private final List<MediaAction> actions;

    @NotNull
    private final List<Integer> actionsToShowInCompact;
    private boolean active;

    @Nullable
    private final String app;

    @Nullable
    private final Drawable appIcon;

    @Nullable
    private final CharSequence artist;

    @Nullable
    private final Icon artwork;
    private final int backgroundColor;

    @Nullable
    private final PendingIntent clickIntent;

    @Nullable
    private final MediaDeviceData device;
    private boolean hasCheckedForResume;
    private final boolean initialized;

    @Nullable
    private final String notificationKey;

    @NotNull
    private final String packageName;

    @Nullable
    private Runnable resumeAction;
    private boolean resumption;

    @Nullable
    private final CharSequence song;

    @Nullable
    private final MediaSession.Token token;
    private final int userId;

    /* JADX WARN: Multi-variable type inference failed */
    public static /* synthetic */ MediaData copy$default(MediaData mediaData, int i, boolean z, int i2, String str, Drawable drawable, CharSequence charSequence, CharSequence charSequence2, Icon icon, List list, List list2, String str2, MediaSession.Token token, PendingIntent pendingIntent, MediaDeviceData mediaDeviceData, boolean z2, Runnable runnable, boolean z3, String str3, boolean z4, int i3, Object obj) {
        boolean z5;
        Runnable runnable2;
        Runnable runnable3;
        boolean z6;
        boolean z7;
        String str4;
        int i4 = (i3 & 1) != 0 ? mediaData.userId : i;
        boolean z8 = (i3 & 2) != 0 ? mediaData.initialized : z;
        int i5 = (i3 & 4) != 0 ? mediaData.backgroundColor : i2;
        String str5 = (i3 & 8) != 0 ? mediaData.app : str;
        Drawable drawable2 = (i3 & 16) != 0 ? mediaData.appIcon : drawable;
        CharSequence charSequence3 = (i3 & 32) != 0 ? mediaData.artist : charSequence;
        CharSequence charSequence4 = (i3 & 64) != 0 ? mediaData.song : charSequence2;
        Icon icon2 = (i3 & 128) != 0 ? mediaData.artwork : icon;
        List list3 = (i3 & 256) != 0 ? mediaData.actions : list;
        List list4 = (i3 & 512) != 0 ? mediaData.actionsToShowInCompact : list2;
        String str6 = (i3 & 1024) != 0 ? mediaData.packageName : str2;
        MediaSession.Token token2 = (i3 & 2048) != 0 ? mediaData.token : token;
        PendingIntent pendingIntent2 = (i3 & 4096) != 0 ? mediaData.clickIntent : pendingIntent;
        MediaDeviceData mediaDeviceData2 = (i3 & 8192) != 0 ? mediaData.device : mediaDeviceData;
        boolean z9 = (i3 & 16384) != 0 ? mediaData.active : z2;
        if ((i3 & 32768) != 0) {
            z5 = z9;
            runnable2 = mediaData.resumeAction;
        } else {
            z5 = z9;
            runnable2 = runnable;
        }
        if ((i3 & 65536) != 0) {
            runnable3 = runnable2;
            z6 = mediaData.resumption;
        } else {
            runnable3 = runnable2;
            z6 = z3;
        }
        if ((i3 & 131072) != 0) {
            z7 = z6;
            str4 = mediaData.notificationKey;
        } else {
            z7 = z6;
            str4 = str3;
        }
        return mediaData.copy(i4, z8, i5, str5, drawable2, charSequence3, charSequence4, icon2, list3, list4, str6, token2, pendingIntent2, mediaDeviceData2, z5, runnable3, z7, str4, (i3 & 262144) != 0 ? mediaData.hasCheckedForResume : z4);
    }

    /* JADX INFO: renamed from: component1, reason: from getter */
    public final int getUserId() {
        return this.userId;
    }

    @NotNull
    public final List<Integer> component10() {
        return this.actionsToShowInCompact;
    }

    @NotNull
    /* JADX INFO: renamed from: component11, reason: from getter */
    public final String getPackageName() {
        return this.packageName;
    }

    @Nullable
    /* JADX INFO: renamed from: component12, reason: from getter */
    public final MediaSession.Token getToken() {
        return this.token;
    }

    @Nullable
    /* JADX INFO: renamed from: component13, reason: from getter */
    public final PendingIntent getClickIntent() {
        return this.clickIntent;
    }

    @Nullable
    /* JADX INFO: renamed from: component14, reason: from getter */
    public final MediaDeviceData getDevice() {
        return this.device;
    }

    /* JADX INFO: renamed from: component15, reason: from getter */
    public final boolean getActive() {
        return this.active;
    }

    @Nullable
    /* JADX INFO: renamed from: component16, reason: from getter */
    public final Runnable getResumeAction() {
        return this.resumeAction;
    }

    /* JADX INFO: renamed from: component17, reason: from getter */
    public final boolean getResumption() {
        return this.resumption;
    }

    @Nullable
    /* JADX INFO: renamed from: component18, reason: from getter */
    public final String getNotificationKey() {
        return this.notificationKey;
    }

    /* JADX INFO: renamed from: component19, reason: from getter */
    public final boolean getHasCheckedForResume() {
        return this.hasCheckedForResume;
    }

    /* JADX INFO: renamed from: component2, reason: from getter */
    public final boolean getInitialized() {
        return this.initialized;
    }

    /* JADX INFO: renamed from: component3, reason: from getter */
    public final int getBackgroundColor() {
        return this.backgroundColor;
    }

    @Nullable
    /* JADX INFO: renamed from: component4, reason: from getter */
    public final String getApp() {
        return this.app;
    }

    @Nullable
    /* JADX INFO: renamed from: component5, reason: from getter */
    public final Drawable getAppIcon() {
        return this.appIcon;
    }

    @Nullable
    /* JADX INFO: renamed from: component6, reason: from getter */
    public final CharSequence getArtist() {
        return this.artist;
    }

    @Nullable
    /* JADX INFO: renamed from: component7, reason: from getter */
    public final CharSequence getSong() {
        return this.song;
    }

    @Nullable
    /* JADX INFO: renamed from: component8, reason: from getter */
    public final Icon getArtwork() {
        return this.artwork;
    }

    @NotNull
    public final List<MediaAction> component9() {
        return this.actions;
    }

    @NotNull
    public final MediaData copy(int userId, boolean initialized, int backgroundColor, @Nullable String app, @Nullable Drawable appIcon, @Nullable CharSequence artist, @Nullable CharSequence song, @Nullable Icon artwork, @NotNull List<MediaAction> actions, @NotNull List<Integer> actionsToShowInCompact, @NotNull String packageName, @Nullable MediaSession.Token token, @Nullable PendingIntent clickIntent, @Nullable MediaDeviceData device, boolean active, @Nullable Runnable resumeAction, boolean resumption, @Nullable String notificationKey, boolean hasCheckedForResume) {
        Intrinsics.checkParameterIsNotNull(actions, "actions");
        Intrinsics.checkParameterIsNotNull(actionsToShowInCompact, "actionsToShowInCompact");
        Intrinsics.checkParameterIsNotNull(packageName, "packageName");
        return new MediaData(userId, initialized, backgroundColor, app, appIcon, artist, song, artwork, actions, actionsToShowInCompact, packageName, token, clickIntent, device, active, resumeAction, resumption, notificationKey, hasCheckedForResume);
    }

    public boolean equals(@Nullable Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof MediaData)) {
            return false;
        }
        MediaData mediaData = (MediaData) other;
        return this.userId == mediaData.userId && this.initialized == mediaData.initialized && this.backgroundColor == mediaData.backgroundColor && Intrinsics.areEqual(this.app, mediaData.app) && Intrinsics.areEqual(this.appIcon, mediaData.appIcon) && Intrinsics.areEqual(this.artist, mediaData.artist) && Intrinsics.areEqual(this.song, mediaData.song) && Intrinsics.areEqual(this.artwork, mediaData.artwork) && Intrinsics.areEqual(this.actions, mediaData.actions) && Intrinsics.areEqual(this.actionsToShowInCompact, mediaData.actionsToShowInCompact) && Intrinsics.areEqual(this.packageName, mediaData.packageName) && Intrinsics.areEqual(this.token, mediaData.token) && Intrinsics.areEqual(this.clickIntent, mediaData.clickIntent) && Intrinsics.areEqual(this.device, mediaData.device) && this.active == mediaData.active && Intrinsics.areEqual(this.resumeAction, mediaData.resumeAction) && this.resumption == mediaData.resumption && Intrinsics.areEqual(this.notificationKey, mediaData.notificationKey) && this.hasCheckedForResume == mediaData.hasCheckedForResume;
    }

    /* JADX WARN: Multi-variable type inference failed */
    /* JADX WARN: Type inference failed for: r1v1, types: [int] */
    /* JADX WARN: Type inference failed for: r1v38, types: [int] */
    /* JADX WARN: Type inference failed for: r1v43, types: [int] */
    /* JADX WARN: Type inference failed for: r1v45 */
    /* JADX WARN: Type inference failed for: r1v47 */
    /* JADX WARN: Type inference failed for: r1v59 */
    /* JADX WARN: Type inference failed for: r1v60 */
    /* JADX WARN: Type inference failed for: r1v61 */
    /* JADX WARN: Type inference failed for: r1v62 */
    /* JADX WARN: Type inference failed for: r4v2, types: [int] */
    /* JADX WARN: Type inference failed for: r4v3 */
    /* JADX WARN: Type inference failed for: r4v4 */
    public int hashCode() {
        int iHashCode = Integer.hashCode(this.userId) * 31;
        boolean z = this.initialized;
        ?? r1 = z;
        if (z) {
            r1 = 1;
        }
        int iHashCode2 = (((iHashCode + r1) * 31) + Integer.hashCode(this.backgroundColor)) * 31;
        String str = this.app;
        int iHashCode3 = (iHashCode2 + (str != null ? str.hashCode() : 0)) * 31;
        Drawable drawable = this.appIcon;
        int iHashCode4 = (iHashCode3 + (drawable != null ? drawable.hashCode() : 0)) * 31;
        CharSequence charSequence = this.artist;
        int iHashCode5 = (iHashCode4 + (charSequence != null ? charSequence.hashCode() : 0)) * 31;
        CharSequence charSequence2 = this.song;
        int iHashCode6 = (iHashCode5 + (charSequence2 != null ? charSequence2.hashCode() : 0)) * 31;
        Icon icon = this.artwork;
        int iHashCode7 = (iHashCode6 + (icon != null ? icon.hashCode() : 0)) * 31;
        List<MediaAction> list = this.actions;
        int iHashCode8 = (iHashCode7 + (list != null ? list.hashCode() : 0)) * 31;
        List<Integer> list2 = this.actionsToShowInCompact;
        int iHashCode9 = (iHashCode8 + (list2 != null ? list2.hashCode() : 0)) * 31;
        String str2 = this.packageName;
        int iHashCode10 = (iHashCode9 + (str2 != null ? str2.hashCode() : 0)) * 31;
        MediaSession.Token token = this.token;
        int iHashCode11 = (iHashCode10 + (token != null ? token.hashCode() : 0)) * 31;
        PendingIntent pendingIntent = this.clickIntent;
        int iHashCode12 = (iHashCode11 + (pendingIntent != null ? pendingIntent.hashCode() : 0)) * 31;
        MediaDeviceData mediaDeviceData = this.device;
        int iHashCode13 = (iHashCode12 + (mediaDeviceData != null ? mediaDeviceData.hashCode() : 0)) * 31;
        boolean z2 = this.active;
        ?? r12 = z2;
        if (z2) {
            r12 = 1;
        }
        int i = (iHashCode13 + r12) * 31;
        Runnable runnable = this.resumeAction;
        int iHashCode14 = (i + (runnable != null ? runnable.hashCode() : 0)) * 31;
        boolean z3 = this.resumption;
        ?? r13 = z3;
        if (z3) {
            r13 = 1;
        }
        int i2 = (iHashCode14 + r13) * 31;
        String str3 = this.notificationKey;
        int iHashCode15 = (i2 + (str3 != null ? str3.hashCode() : 0)) * 31;
        boolean z4 = this.hasCheckedForResume;
        ?? r4 = z4;
        if (z4) {
            r4 = 1;
        }
        return iHashCode15 + r4;
    }

    @NotNull
    public String toString() {
        return "MediaData(userId=" + this.userId + ", initialized=" + this.initialized + ", backgroundColor=" + this.backgroundColor + ", app=" + this.app + ", appIcon=" + this.appIcon + ", artist=" + this.artist + ", song=" + this.song + ", artwork=" + this.artwork + ", actions=" + this.actions + ", actionsToShowInCompact=" + this.actionsToShowInCompact + ", packageName=" + this.packageName + ", token=" + this.token + ", clickIntent=" + this.clickIntent + ", device=" + this.device + ", active=" + this.active + ", resumeAction=" + this.resumeAction + ", resumption=" + this.resumption + ", notificationKey=" + this.notificationKey + ", hasCheckedForResume=" + this.hasCheckedForResume + NavigationBarInflaterView.KEY_CODE_END;
    }

    public MediaData(int i, boolean z, int i2, @Nullable String str, @Nullable Drawable drawable, @Nullable CharSequence charSequence, @Nullable CharSequence charSequence2, @Nullable Icon icon, @NotNull List<MediaAction> actions, @NotNull List<Integer> actionsToShowInCompact, @NotNull String packageName, @Nullable MediaSession.Token token, @Nullable PendingIntent pendingIntent, @Nullable MediaDeviceData mediaDeviceData, boolean z2, @Nullable Runnable runnable, boolean z3, @Nullable String str2, boolean z4) {
        Intrinsics.checkParameterIsNotNull(actions, "actions");
        Intrinsics.checkParameterIsNotNull(actionsToShowInCompact, "actionsToShowInCompact");
        Intrinsics.checkParameterIsNotNull(packageName, "packageName");
        this.userId = i;
        this.initialized = z;
        this.backgroundColor = i2;
        this.app = str;
        this.appIcon = drawable;
        this.artist = charSequence;
        this.song = charSequence2;
        this.artwork = icon;
        this.actions = actions;
        this.actionsToShowInCompact = actionsToShowInCompact;
        this.packageName = packageName;
        this.token = token;
        this.clickIntent = pendingIntent;
        this.device = mediaDeviceData;
        this.active = z2;
        this.resumeAction = runnable;
        this.resumption = z3;
        this.notificationKey = str2;
        this.hasCheckedForResume = z4;
    }

    public final int getUserId() {
        return this.userId;
    }

    public final boolean getInitialized() {
        return this.initialized;
    }

    public final int getBackgroundColor() {
        return this.backgroundColor;
    }

    @Nullable
    public final String getApp() {
        return this.app;
    }

    @Nullable
    public final Drawable getAppIcon() {
        return this.appIcon;
    }

    @Nullable
    public final CharSequence getArtist() {
        return this.artist;
    }

    @Nullable
    public final CharSequence getSong() {
        return this.song;
    }

    @Nullable
    public final Icon getArtwork() {
        return this.artwork;
    }

    @NotNull
    public final List<MediaAction> getActions() {
        return this.actions;
    }

    @NotNull
    public final List<Integer> getActionsToShowInCompact() {
        return this.actionsToShowInCompact;
    }

    @NotNull
    public final String getPackageName() {
        return this.packageName;
    }

    @Nullable
    public final MediaSession.Token getToken() {
        return this.token;
    }

    @Nullable
    public final PendingIntent getClickIntent() {
        return this.clickIntent;
    }

    @Nullable
    public final MediaDeviceData getDevice() {
        return this.device;
    }

    public final boolean getActive() {
        return this.active;
    }

    public final void setActive(boolean z) {
        this.active = z;
    }

    @Nullable
    public final Runnable getResumeAction() {
        return this.resumeAction;
    }

    public final void setResumeAction(@Nullable Runnable runnable) {
        this.resumeAction = runnable;
    }

    public final boolean getResumption() {
        return this.resumption;
    }

    public final void setResumption(boolean z) {
        this.resumption = z;
    }

    public /* synthetic */ MediaData(int i, boolean z, int i2, String str, Drawable drawable, CharSequence charSequence, CharSequence charSequence2, Icon icon, List list, List list2, String str2, MediaSession.Token token, PendingIntent pendingIntent, MediaDeviceData mediaDeviceData, boolean z2, Runnable runnable, boolean z3, String str3, boolean z4, int i3, DefaultConstructorMarker defaultConstructorMarker) {
        this(i, (i3 & 2) != 0 ? false : z, i2, str, drawable, charSequence, charSequence2, icon, list, list2, str2, token, pendingIntent, mediaDeviceData, z2, runnable, (i3 & 65536) != 0 ? false : z3, (i3 & 131072) != 0 ? (String) null : str3, (i3 & 262144) != 0 ? false : z4);
    }

    @Nullable
    public final String getNotificationKey() {
        return this.notificationKey;
    }

    public final boolean getHasCheckedForResume() {
        return this.hasCheckedForResume;
    }

    public final void setHasCheckedForResume(boolean z) {
        this.hasCheckedForResume = z;
    }
}
