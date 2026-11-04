package com.android.systemui.media;

import android.app.Notification;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ImageDecoder;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.Icon;
import android.media.MediaDescription;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSession;
import android.net.Uri;
import android.os.UserHandle;
import android.service.notification.StatusBarNotification;
import android.support.v4.media.MediaMetadataCompat;
import android.text.TextUtils;
import android.util.Log;
import androidx.core.app.NotificationCompat;
import androidx.palette.graphics.Palette;
import com.android.internal.graphics.ColorUtils;
import com.android.settingslib.SliceBroadcastRelay;
import com.android.systemui.Dumpable;
import com.android.systemui.R;
import com.android.systemui.broadcast.BroadcastDispatcher;
import com.android.systemui.dagger.qualifiers.Background;
import com.android.systemui.dagger.qualifiers.Main;
import com.android.systemui.dump.DumpManager;
import com.android.systemui.statusbar.notification.MediaNotificationProcessor;
import com.android.systemui.statusbar.notification.row.HybridGroupManager;
import com.android.systemui.util.Assert;
import com.android.systemui.util.Utils;
import com.vivo.assistant.vcorentsdk.utils.Constants;
import com.vivo.common.utils.LogUtils;
import java.io.FileDescriptor;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executor;
import javax.inject.Inject;
import javax.inject.Singleton;
import kotlin.Metadata;
import kotlin.Unit;
import kotlin.collections.ArraysKt;
import kotlin.collections.CollectionsKt;
import kotlin.jvm.functions.Function2;
import kotlin.jvm.internal.Intrinsics;
import kotlin.jvm.internal.Ref;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/* JADX INFO: compiled from: MediaDataManager.kt */
/* JADX INFO: loaded from: classes2.dex */
@Singleton
@Metadata(bv = {1, 0, 3}, d1 = {"\u0000Ç\u0001\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0010\u000b\n\u0002\b\u0003\n\u0002\b\u0003\n\u0002\u0010#\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\u0010\u000e\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0010\u0002\n\u0000\n\u0002\u0010\b\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u0011\n\u0002\b\u0004\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u0012*\u0001\u0017\b\u0007\u0018\u00002\u00020\u0001:\u0001YBK\b\u0017\u0012\u0006\u0010\u0002\u001a\u00020\u0003\u0012\b\b\u0001\u0010\u0004\u001a\u00020\u0005\u0012\b\b\u0001\u0010\u0006\u001a\u00020\u0005\u0012\u0006\u0010\u0007\u001a\u00020\b\u0012\u0006\u0010\t\u001a\u00020\n\u0012\u0006\u0010\u000b\u001a\u00020\f\u0012\u0006\u0010\r\u001a\u00020\u000e\u0012\u0006\u0010\u000f\u001a\u00020\u0010¢\u0006\u0002\u0010\u0011BY\u0012\u0006\u0010\u0002\u001a\u00020\u0003\u0012\b\b\u0001\u0010\u0004\u001a\u00020\u0005\u0012\b\b\u0001\u0010\u0006\u001a\u00020\u0005\u0012\u0006\u0010\u0007\u001a\u00020\b\u0012\u0006\u0010\u000b\u001a\u00020\f\u0012\u0006\u0010\t\u001a\u00020\n\u0012\u0006\u0010\r\u001a\u00020\u000e\u0012\u0006\u0010\u000f\u001a\u00020\u0010\u0012\u0006\u0010\u0012\u001a\u00020\u0013\u0012\u0006\u0010\u0014\u001a\u00020\u0013¢\u0006\u0002\u0010\u0015J\u000e\u0010!\u001a\u00020\u00132\u0006\u0010\"\u001a\u00020\u001bJ>\u0010#\u001a\u00020$2\u0006\u0010%\u001a\u00020&2\u0006\u0010'\u001a\u00020(2\u0006\u0010)\u001a\u00020*2\u0006\u0010+\u001a\u00020,2\u0006\u0010-\u001a\u00020\u001e2\u0006\u0010.\u001a\u00020/2\u0006\u00100\u001a\u00020\u001eJ\u0012\u00101\u001a\u00020&2\b\u00102\u001a\u0004\u0018\u000103H\u0002J\u0006\u00104\u001a\u00020$J-\u00105\u001a\u00020$2\u0006\u00106\u001a\u0002072\u0006\u00108\u001a\u0002092\u000e\u0010:\u001a\n\u0012\u0006\b\u0001\u0012\u00020\u001e0;H\u0016¢\u0006\u0002\u0010<J\u001a\u0010=\u001a\u0004\u0018\u00010\u001e2\u0006\u0010>\u001a\u00020\u001e2\u0006\u00100\u001a\u00020\u001eH\u0002J\u0010\u0010?\u001a\u00020@2\u0006\u0010)\u001a\u00020*H\u0002J\u0012\u0010A\u001a\u0004\u0018\u0001032\u0006\u0010B\u001a\u00020CH\u0002J\u0012\u0010A\u001a\u0004\u0018\u0001032\u0006\u0010D\u001a\u00020EH\u0002J\"\u0010F\u001a\u00020$2\u0006\u0010>\u001a\u00020\u001e2\u0006\u0010G\u001a\u00020H2\b\u0010I\u001a\u0004\u0018\u00010\u001eH\u0002J\"\u0010J\u001a\u00020$2\u0006\u0010>\u001a\u00020\u001e2\u0006\u0010G\u001a\u00020H2\b\u0010I\u001a\u0004\u0018\u00010\u001eH\u0002J@\u0010K\u001a\u00020$2\u0006\u0010%\u001a\u00020&2\u0006\u0010'\u001a\u00020(2\u0006\u0010L\u001a\u00020*2\u0006\u0010+\u001a\u00020,2\u0006\u0010-\u001a\u00020\u001e2\u0006\u0010.\u001a\u00020/2\u0006\u00100\u001a\u00020\u001eH\u0002J \u0010M\u001a\u00020$2\u0006\u0010>\u001a\u00020\u001e2\b\u0010I\u001a\u0004\u0018\u00010\u001e2\u0006\u0010N\u001a\u00020\u001fJ\u0016\u0010O\u001a\u00020$2\u0006\u0010>\u001a\u00020\u001e2\u0006\u0010G\u001a\u00020HJ\u000e\u0010P\u001a\u00020$2\u0006\u0010>\u001a\u00020\u001eJ\u0010\u0010Q\u001a\u00020$2\u0006\u00100\u001a\u00020\u001eH\u0002J\u000e\u0010R\u001a\u00020\u00132\u0006\u0010\"\u001a\u00020\u001bJ\u000e\u0010S\u001a\u00020$2\u0006\u0010T\u001a\u00020\u0013J\u0018\u0010U\u001a\u00020$2\u0006\u0010>\u001a\u00020\u001e2\b\u0010)\u001a\u0004\u0018\u00010*J\u001d\u0010V\u001a\u00020$2\u0006\u0010+\u001a\u00020\u001e2\u0006\u0010W\u001a\u00020\u0013H\u0000¢\u0006\u0002\bXR\u0010\u0010\u0016\u001a\u00020\u0017X\u0082\u0004¢\u0006\u0004\n\u0002\u0010\u0018R\u000e\u0010\u0004\u001a\u00020\u0005X\u0082\u0004¢\u0006\u0002\n\u0000R\u000e\u0010\u000b\u001a\u00020\fX\u0082\u0004¢\u0006\u0002\n\u0000R\u000e\u0010\u0002\u001a\u00020\u0003X\u0082\u0004¢\u0006\u0002\n\u0000R\u000e\u0010\u0006\u001a\u00020\u0005X\u0082\u0004¢\u0006\u0002\n\u0000R\u0014\u0010\u0019\u001a\b\u0012\u0004\u0012\u00020\u001b0\u001aX\u0082\u0004¢\u0006\u0002\n\u0000R\u000e\u0010\u0007\u001a\u00020\bX\u0082\u0004¢\u0006\u0002\n\u0000R*\u0010\u001c\u001a\u001e\u0012\u0004\u0012\u00020\u001e\u0012\u0004\u0012\u00020\u001f0\u001dj\u000e\u0012\u0004\u0012\u00020\u001e\u0012\u0004\u0012\u00020\u001f` X\u0082\u0004¢\u0006\u0002\n\u0000R\u000e\u0010\u0012\u001a\u00020\u0013X\u0082\u000e¢\u0006\u0002\n\u0000R\u000e\u0010\u0014\u001a\u00020\u0013X\u0082\u0004¢\u0006\u0002\n\u0000¨\u0006Z"}, d2 = {"Lcom/android/systemui/media/MediaDataManager;", "Lcom/android/systemui/Dumpable;", "context", "Landroid/content/Context;", "backgroundExecutor", "Ljava/util/concurrent/Executor;", "foregroundExecutor", "mediaControllerFactory", "Lcom/android/systemui/media/MediaControllerFactory;", "dumpManager", "Lcom/android/systemui/dump/DumpManager;", "broadcastDispatcher", "Lcom/android/systemui/broadcast/BroadcastDispatcher;", "mediaTimeoutListener", "Lcom/android/systemui/media/MediaTimeoutListener;", "mediaResumeListener", "Lcom/android/systemui/media/MediaResumeListener;", "(Landroid/content/Context;Ljava/util/concurrent/Executor;Ljava/util/concurrent/Executor;Lcom/android/systemui/media/MediaControllerFactory;Lcom/android/systemui/dump/DumpManager;Lcom/android/systemui/broadcast/BroadcastDispatcher;Lcom/android/systemui/media/MediaTimeoutListener;Lcom/android/systemui/media/MediaResumeListener;)V", "useMediaResumption", "", "useQsMediaPlayer", "(Landroid/content/Context;Ljava/util/concurrent/Executor;Ljava/util/concurrent/Executor;Lcom/android/systemui/media/MediaControllerFactory;Lcom/android/systemui/broadcast/BroadcastDispatcher;Lcom/android/systemui/dump/DumpManager;Lcom/android/systemui/media/MediaTimeoutListener;Lcom/android/systemui/media/MediaResumeListener;ZZ)V", "appChangeReceiver", "com/android/systemui/media/MediaDataManager$appChangeReceiver$1", "Lcom/android/systemui/media/MediaDataManager$appChangeReceiver$1;", "listeners", "", "Lcom/android/systemui/media/MediaDataManager$Listener;", "mediaEntries", "Ljava/util/LinkedHashMap;", "", "Lcom/android/systemui/media/MediaData;", "Lkotlin/collections/LinkedHashMap;", "addListener", "listener", "addResumptionControls", "", "userId", "", "desc", "Landroid/media/MediaDescription;", "action", "Ljava/lang/Runnable;", "token", "Landroid/media/session/MediaSession$Token;", "appName", "appIntent", "Landroid/app/PendingIntent;", Constants.KEY_PACKAGE_NAME, "computeBackgroundColor", "artworkBitmap", "Landroid/graphics/Bitmap;", "destroy", "dump", "fd", "Ljava/io/FileDescriptor;", "pw", "Ljava/io/PrintWriter;", "args", "", "(Ljava/io/FileDescriptor;Ljava/io/PrintWriter;[Ljava/lang/String;)V", "findExistingEntry", "key", "getResumeMediaAction", "Lcom/android/systemui/media/MediaAction;", "loadBitmapFromUri", "metadata", "Landroid/media/MediaMetadata;", SliceBroadcastRelay.EXTRA_URI, "Landroid/net/Uri;", "loadMediaData", "sbn", "Landroid/service/notification/StatusBarNotification;", "oldKey", "loadMediaDataInBg", "loadMediaDataInBgForResumption", "resumeAction", "onMediaDataLoaded", "data", "onNotificationAdded", "onNotificationRemoved", "removeAllForPackage", "removeListener", "setMediaResumptionEnabled", "isEnabled", "setResumeAction", "setTimedOut", "timedOut", "setTimedOut$app_xxhdpiRatio_20_9Rom_13_0DemesticAndroid_30Release", "Listener", "app_xxhdpiRatio_20_9Rom_13_0DemesticAndroid_30Release"}, k = 1, mv = {1, 1, 16})
public final class MediaDataManager implements Dumpable {
    private final MediaDataManager$appChangeReceiver$1 appChangeReceiver;
    private final Executor backgroundExecutor;
    private final BroadcastDispatcher broadcastDispatcher;
    private final Context context;
    private final Executor foregroundExecutor;
    private final Set<Listener> listeners;
    private final MediaControllerFactory mediaControllerFactory;
    private final LinkedHashMap<String, MediaData> mediaEntries;
    private boolean useMediaResumption;
    private final boolean useQsMediaPlayer;

    /* JADX INFO: compiled from: MediaDataManager.kt */
    @Metadata(bv = {1, 0, 3}, d1 = {"\u0000 \n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0000\n\u0002\u0010\u0002\n\u0000\n\u0002\u0010\u000e\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\bf\u0018\u00002\u00020\u0001J\"\u0010\u0002\u001a\u00020\u00032\u0006\u0010\u0004\u001a\u00020\u00052\b\u0010\u0006\u001a\u0004\u0018\u00010\u00052\u0006\u0010\u0007\u001a\u00020\bH\u0016J\u0010\u0010\t\u001a\u00020\u00032\u0006\u0010\u0004\u001a\u00020\u0005H\u0016¨\u0006\n"}, d2 = {"Lcom/android/systemui/media/MediaDataManager$Listener;", "", "onMediaDataLoaded", "", "key", "", "oldKey", "data", "Lcom/android/systemui/media/MediaData;", "onMediaDataRemoved", "app_xxhdpiRatio_20_9Rom_13_0DemesticAndroid_30Release"}, k = 1, mv = {1, 1, 16})
    public interface Listener {

        /* JADX INFO: compiled from: MediaDataManager.kt */
        @Metadata(bv = {1, 0, 3}, k = 3, mv = {1, 1, 16})
        public static final class DefaultImpls {
            public static void onMediaDataLoaded(Listener listener, @NotNull String key, @Nullable String str, @NotNull MediaData data) {
                Intrinsics.checkParameterIsNotNull(key, "key");
                Intrinsics.checkParameterIsNotNull(data, "data");
            }

            public static void onMediaDataRemoved(Listener listener, @NotNull String key) {
                Intrinsics.checkParameterIsNotNull(key, "key");
            }
        }

        void onMediaDataLoaded(@NotNull String key, @Nullable String oldKey, @NotNull MediaData data);

        void onMediaDataRemoved(@NotNull String key);
    }

    /* JADX WARN: Type inference failed for: r2v4, types: [com.android.systemui.media.MediaDataManager$appChangeReceiver$1] */
    public MediaDataManager(@NotNull Context context, @Background @NotNull Executor backgroundExecutor, @Main @NotNull Executor foregroundExecutor, @NotNull MediaControllerFactory mediaControllerFactory, @NotNull BroadcastDispatcher broadcastDispatcher, @NotNull DumpManager dumpManager, @NotNull MediaTimeoutListener mediaTimeoutListener, @NotNull MediaResumeListener mediaResumeListener, boolean z, boolean z2) {
        Intrinsics.checkParameterIsNotNull(context, "context");
        Intrinsics.checkParameterIsNotNull(backgroundExecutor, "backgroundExecutor");
        Intrinsics.checkParameterIsNotNull(foregroundExecutor, "foregroundExecutor");
        Intrinsics.checkParameterIsNotNull(mediaControllerFactory, "mediaControllerFactory");
        Intrinsics.checkParameterIsNotNull(broadcastDispatcher, "broadcastDispatcher");
        Intrinsics.checkParameterIsNotNull(dumpManager, "dumpManager");
        Intrinsics.checkParameterIsNotNull(mediaTimeoutListener, "mediaTimeoutListener");
        Intrinsics.checkParameterIsNotNull(mediaResumeListener, "mediaResumeListener");
        this.context = context;
        this.backgroundExecutor = backgroundExecutor;
        this.foregroundExecutor = foregroundExecutor;
        this.mediaControllerFactory = mediaControllerFactory;
        this.broadcastDispatcher = broadcastDispatcher;
        this.useMediaResumption = z;
        this.useQsMediaPlayer = z2;
        this.listeners = new LinkedHashSet();
        this.mediaEntries = new LinkedHashMap<>();
        this.appChangeReceiver = new BroadcastReceiver() { // from class: com.android.systemui.media.MediaDataManager$appChangeReceiver$1
            @Override // android.content.BroadcastReceiver
            public void onReceive(@NotNull Context context2, @NotNull Intent intent) {
                String[] stringArrayExtra;
                String encodedSchemeSpecificPart;
                Intrinsics.checkParameterIsNotNull(context2, "context");
                Intrinsics.checkParameterIsNotNull(intent, "intent");
                String action = intent.getAction();
                if (action == null) {
                    return;
                }
                int iHashCode = action.hashCode();
                if (iHashCode == -1001645458) {
                    if (!action.equals("android.intent.action.PACKAGES_SUSPENDED") || (stringArrayExtra = intent.getStringArrayExtra("android.intent.extra.changed_package_list")) == null) {
                        return;
                    }
                    for (String it : stringArrayExtra) {
                        MediaDataManager mediaDataManager = this.this$0;
                        Intrinsics.checkExpressionValueIsNotNull(it, "it");
                        mediaDataManager.removeAllForPackage(it);
                    }
                    return;
                }
                if (iHashCode != -757780528) {
                    if (iHashCode != 525384130 || !action.equals("android.intent.action.PACKAGE_REMOVED")) {
                        return;
                    }
                } else if (!action.equals("android.intent.action.PACKAGE_RESTARTED")) {
                    return;
                }
                Uri data = intent.getData();
                if (data == null || (encodedSchemeSpecificPart = data.getEncodedSchemeSpecificPart()) == null) {
                    return;
                }
                this.this$0.removeAllForPackage(encodedSchemeSpecificPart);
            }
        };
        dumpManager.registerDumpable("MediaDataManager", this);
        mediaTimeoutListener.setTimeoutCallback(new Function2<String, Boolean, Unit>() { // from class: com.android.systemui.media.MediaDataManager.1
            {
                super(2);
            }

            @Override // kotlin.jvm.functions.Function2
            public /* bridge */ /* synthetic */ Unit invoke(String str, Boolean bool) {
                invoke(str, bool.booleanValue());
                return Unit.INSTANCE;
            }

            public final void invoke(@NotNull String token, boolean z3) {
                Intrinsics.checkParameterIsNotNull(token, "token");
                MediaDataManager.this.setTimedOut$app_xxhdpiRatio_20_9Rom_13_0DemesticAndroid_30Release(token, z3);
            }
        });
        addListener(mediaTimeoutListener);
        mediaResumeListener.setManager(this);
        addListener(mediaResumeListener);
        IntentFilter intentFilter = new IntentFilter("android.intent.action.PACKAGES_SUSPENDED");
        BroadcastDispatcher broadcastDispatcher2 = this.broadcastDispatcher;
        MediaDataManager$appChangeReceiver$1 mediaDataManager$appChangeReceiver$1 = this.appChangeReceiver;
        UserHandle userHandle = UserHandle.ALL;
        Intrinsics.checkExpressionValueIsNotNull(userHandle, "UserHandle.ALL");
        broadcastDispatcher2.registerReceiver(mediaDataManager$appChangeReceiver$1, intentFilter, null, userHandle);
        IntentFilter intentFilter2 = new IntentFilter();
        intentFilter2.addAction("android.intent.action.PACKAGE_REMOVED");
        intentFilter2.addAction("android.intent.action.PACKAGE_RESTARTED");
        intentFilter2.addDataScheme("package");
        this.context.registerReceiver(this.appChangeReceiver, intentFilter2);
    }

    /* JADX WARN: 'this' call moved to the top of the method (can break code semantics) */
    @Inject
    public MediaDataManager(@NotNull Context context, @Background @NotNull Executor backgroundExecutor, @Main @NotNull Executor foregroundExecutor, @NotNull MediaControllerFactory mediaControllerFactory, @NotNull DumpManager dumpManager, @NotNull BroadcastDispatcher broadcastDispatcher, @NotNull MediaTimeoutListener mediaTimeoutListener, @NotNull MediaResumeListener mediaResumeListener) {
        this(context, backgroundExecutor, foregroundExecutor, mediaControllerFactory, broadcastDispatcher, dumpManager, mediaTimeoutListener, mediaResumeListener, Utils.useMediaResumption(context), Utils.useQsMediaPlayer(context));
        Intrinsics.checkParameterIsNotNull(context, "context");
        Intrinsics.checkParameterIsNotNull(backgroundExecutor, "backgroundExecutor");
        Intrinsics.checkParameterIsNotNull(foregroundExecutor, "foregroundExecutor");
        Intrinsics.checkParameterIsNotNull(mediaControllerFactory, "mediaControllerFactory");
        Intrinsics.checkParameterIsNotNull(dumpManager, "dumpManager");
        Intrinsics.checkParameterIsNotNull(broadcastDispatcher, "broadcastDispatcher");
        Intrinsics.checkParameterIsNotNull(mediaTimeoutListener, "mediaTimeoutListener");
        Intrinsics.checkParameterIsNotNull(mediaResumeListener, "mediaResumeListener");
    }

    public final void destroy() {
        this.context.unregisterReceiver(this.appChangeReceiver);
    }

    public final void onNotificationAdded(@NotNull String key, @NotNull StatusBarNotification sbn) {
        Intrinsics.checkParameterIsNotNull(key, "key");
        Intrinsics.checkParameterIsNotNull(sbn, "sbn");
        if (this.useQsMediaPlayer && MediaDataManagerKt.isMediaNotification(sbn)) {
            Assert.isMainThread();
            String packageName = sbn.getPackageName();
            Intrinsics.checkExpressionValueIsNotNull(packageName, "sbn.packageName");
            String strFindExistingEntry = findExistingEntry(key, packageName);
            if (strFindExistingEntry == null) {
                MediaData mediaData = MediaDataManagerKt.LOADING;
                String packageName2 = sbn.getPackageName();
                Intrinsics.checkExpressionValueIsNotNull(packageName2, "sbn.packageName");
                this.mediaEntries.put(key, MediaData.copy$default(mediaData, 0, false, 0, null, null, null, null, null, null, null, packageName2, null, null, null, false, null, false, null, false, 523263, null));
            } else if (!Intrinsics.areEqual(strFindExistingEntry, key)) {
                MediaData mediaDataRemove = this.mediaEntries.remove(strFindExistingEntry);
                if (mediaDataRemove == null) {
                    Intrinsics.throwNpe();
                }
                Intrinsics.checkExpressionValueIsNotNull(mediaDataRemove, "mediaEntries.remove(oldKey)!!");
                this.mediaEntries.put(key, mediaDataRemove);
            }
            loadMediaData(key, sbn, strFindExistingEntry);
            return;
        }
        onNotificationRemoved(key);
    }

    /* JADX INFO: Access modifiers changed from: private */
    public final void removeAllForPackage(String packageName) {
        Assert.isMainThread();
        Set set = CollectionsKt.toSet(this.listeners);
        LinkedHashMap<String, MediaData> linkedHashMap = this.mediaEntries;
        LinkedHashMap linkedHashMap2 = new LinkedHashMap();
        for (Map.Entry<String, MediaData> entry : linkedHashMap.entrySet()) {
            if (Intrinsics.areEqual(entry.getValue().getPackageName(), packageName)) {
                linkedHashMap2.put(entry.getKey(), entry.getValue());
            }
        }
        for (Map.Entry entry2 : linkedHashMap2.entrySet()) {
            this.mediaEntries.remove(entry2.getKey());
            Iterator it = set.iterator();
            while (it.hasNext()) {
                ((Listener) it.next()).onMediaDataRemoved((String) entry2.getKey());
            }
        }
    }

    public final void setResumeAction(@NotNull String key, @Nullable Runnable action) {
        Intrinsics.checkParameterIsNotNull(key, "key");
        MediaData mediaData = this.mediaEntries.get(key);
        if (mediaData != null) {
            mediaData.setResumeAction(action);
            mediaData.setHasCheckedForResume(true);
        }
    }

    public final void addResumptionControls(final int userId, @NotNull final MediaDescription desc, @NotNull final Runnable action, @NotNull final MediaSession.Token token, @NotNull final String appName, @NotNull final PendingIntent appIntent, @NotNull final String packageName) {
        Intrinsics.checkParameterIsNotNull(desc, "desc");
        Intrinsics.checkParameterIsNotNull(action, "action");
        Intrinsics.checkParameterIsNotNull(token, "token");
        Intrinsics.checkParameterIsNotNull(appName, "appName");
        Intrinsics.checkParameterIsNotNull(appIntent, "appIntent");
        Intrinsics.checkParameterIsNotNull(packageName, "packageName");
        if (!this.mediaEntries.containsKey(packageName)) {
            this.mediaEntries.put(packageName, MediaData.copy$default(MediaDataManagerKt.LOADING, 0, false, 0, null, null, null, null, null, null, null, packageName, null, null, null, false, action, false, null, true, 228351, null));
        }
        this.backgroundExecutor.execute(new Runnable() { // from class: com.android.systemui.media.MediaDataManager.addResumptionControls.1
            @Override // java.lang.Runnable
            public final void run() {
                MediaDataManager.this.loadMediaDataInBgForResumption(userId, desc, action, token, appName, appIntent, packageName);
            }
        });
    }

    private final String findExistingEntry(String key, String packageName) {
        if (this.mediaEntries.containsKey(key)) {
            return key;
        }
        if (this.mediaEntries.containsKey(packageName)) {
            return packageName;
        }
        return null;
    }

    private final void loadMediaData(final String key, final StatusBarNotification sbn, final String oldKey) {
        this.backgroundExecutor.execute(new Runnable() { // from class: com.android.systemui.media.MediaDataManager.loadMediaData.1
            @Override // java.lang.Runnable
            public final void run() {
                MediaDataManager.this.loadMediaDataInBg(key, sbn, oldKey);
            }
        });
    }

    public final boolean addListener(@NotNull Listener listener) {
        Intrinsics.checkParameterIsNotNull(listener, "listener");
        return this.listeners.add(listener);
    }

    public final boolean removeListener(@NotNull Listener listener) {
        Intrinsics.checkParameterIsNotNull(listener, "listener");
        return this.listeners.remove(listener);
    }

    public final void setTimedOut$app_xxhdpiRatio_20_9Rom_13_0DemesticAndroid_30Release(@NotNull String token, boolean timedOut) {
        Intrinsics.checkParameterIsNotNull(token, "token");
        MediaData it = this.mediaEntries.get(token);
        if (it == null || it.getActive() == (!timedOut)) {
            return;
        }
        it.setActive(!timedOut);
        Intrinsics.checkExpressionValueIsNotNull(it, "it");
        onMediaDataLoaded(token, token, it);
    }

    /* JADX INFO: Access modifiers changed from: private */
    public final void loadMediaDataInBgForResumption(final int userId, final MediaDescription desc, final Runnable resumeAction, final MediaSession.Token token, final String appName, final PendingIntent appIntent, final String packageName) {
        if (TextUtils.isEmpty(desc.getTitle())) {
            Log.e("MediaDataManager", "Description incomplete");
            this.mediaEntries.remove(packageName);
            return;
        }
        Log.d("MediaDataManager", "adding track for " + userId + " from browser: " + desc);
        Bitmap iconBitmap = desc.getIconBitmap();
        if (iconBitmap == null && desc.getIconUri() != null) {
            Uri iconUri = desc.getIconUri();
            if (iconUri == null) {
                Intrinsics.throwNpe();
            }
            iconBitmap = loadBitmapFromUri(iconUri);
        }
        final Icon iconCreateWithBitmap = iconBitmap != null ? Icon.createWithBitmap(iconBitmap) : null;
        final int iComputeBackgroundColor = iconBitmap != null ? computeBackgroundColor(iconBitmap) : -12303292;
        final MediaAction resumeMediaAction = getResumeMediaAction(resumeAction);
        this.foregroundExecutor.execute(new Runnable() { // from class: com.android.systemui.media.MediaDataManager.loadMediaDataInBgForResumption.1
            @Override // java.lang.Runnable
            public final void run() {
                MediaDataManager mediaDataManager = MediaDataManager.this;
                String str = packageName;
                int i = userId;
                int i2 = iComputeBackgroundColor;
                String str2 = appName;
                CharSequence subtitle = desc.getSubtitle();
                CharSequence title = desc.getTitle();
                Icon icon = iconCreateWithBitmap;
                List listListOf = CollectionsKt.listOf(resumeMediaAction);
                List listListOf2 = CollectionsKt.listOf(0);
                String str3 = packageName;
                mediaDataManager.onMediaDataLoaded(str, null, new MediaData(i, true, i2, str2, null, subtitle, title, icon, listListOf, listListOf2, str3, token, appIntent, null, false, resumeAction, true, str3, true));
            }
        });
    }

    /* JADX INFO: Access modifiers changed from: private */
    /* JADX WARN: Type inference failed for: r0v25, types: [T, java.lang.CharSequence] */
    /* JADX WARN: Type inference failed for: r0v7, types: [T, java.lang.CharSequence] */
    /* JADX WARN: Type inference failed for: r2v22, types: [T, java.lang.CharSequence] */
    /* JADX WARN: Type inference failed for: r2v25, types: [T, java.lang.CharSequence] */
    /* JADX WARN: Type inference failed for: r2v7, types: [T, java.lang.CharSequence] */
    public final void loadMediaDataInBg(final String key, final StatusBarNotification sbn, final String oldKey) {
        Icon iconCreateWithBitmap;
        ArrayList arrayList;
        Notification.Action[] actionArr;
        Notification notification;
        final MediaSession.Token token = (MediaSession.Token) sbn.getNotification().extras.getParcelable(NotificationCompat.EXTRA_MEDIA_SESSION);
        MediaController mediaControllerCreate = this.mediaControllerFactory.create(token);
        Intrinsics.checkExpressionValueIsNotNull(mediaControllerCreate, "mediaControllerFactory.create(token)");
        MediaMetadata metadata = mediaControllerCreate.getMetadata();
        if (metadata == null) {
            return;
        }
        Notification notification2 = sbn.getNotification();
        Intrinsics.checkExpressionValueIsNotNull(notification2, "sbn.notification");
        Bitmap bitmap = metadata.getBitmap(MediaMetadataCompat.METADATA_KEY_ART);
        if (bitmap == null) {
            bitmap = metadata.getBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART);
        }
        if (bitmap == null) {
            bitmap = loadBitmapFromUri(metadata);
        }
        if (bitmap == null) {
            iconCreateWithBitmap = notification2.getLargeIcon();
        } else {
            iconCreateWithBitmap = Icon.createWithBitmap(bitmap);
        }
        final Icon icon = iconCreateWithBitmap;
        int i = 0;
        if (icon != null && bitmap == null) {
            if (icon.getType() == 1 || icon.getType() == 5) {
                bitmap = icon.getBitmap();
            } else {
                Drawable drawableLoadDrawable = icon.loadDrawable(this.context);
                Intrinsics.checkExpressionValueIsNotNull(drawableLoadDrawable, "artWorkIcon.loadDrawable(context)");
                Bitmap bitmapCreateBitmap = Bitmap.createBitmap(drawableLoadDrawable.getIntrinsicWidth(), drawableLoadDrawable.getIntrinsicHeight(), Bitmap.Config.ARGB_8888);
                Canvas canvas = new Canvas(bitmapCreateBitmap);
                drawableLoadDrawable.setBounds(0, 0, drawableLoadDrawable.getIntrinsicWidth(), drawableLoadDrawable.getIntrinsicHeight());
                drawableLoadDrawable.draw(canvas);
                bitmap = bitmapCreateBitmap;
            }
        }
        final int iComputeBackgroundColor = computeBackgroundColor(bitmap);
        final String strLoadHeaderAppName = Notification.Builder.recoverBuilder(this.context, notification2).loadHeaderAppName();
        Notification notification3 = sbn.getNotification();
        Intrinsics.checkExpressionValueIsNotNull(notification3, "sbn.notification");
        final Drawable drawableLoadDrawable2 = notification3.getSmallIcon().loadDrawable(this.context);
        Intrinsics.checkExpressionValueIsNotNull(drawableLoadDrawable2, "sbn.notification.smallIcon.loadDrawable(context)");
        final Ref.ObjectRef objectRef = new Ref.ObjectRef();
        objectRef.element = metadata.getString(MediaMetadataCompat.METADATA_KEY_DISPLAY_TITLE);
        if (((CharSequence) objectRef.element) == null) {
            objectRef.element = metadata.getString("android.media.metadata.TITLE");
        }
        if (((CharSequence) objectRef.element) == null) {
            objectRef.element = HybridGroupManager.resolveTitle(notification2);
        }
        final Ref.ObjectRef objectRef2 = new Ref.ObjectRef();
        objectRef2.element = metadata.getString("android.media.metadata.ARTIST");
        if (((CharSequence) objectRef2.element) == null) {
            objectRef2.element = HybridGroupManager.resolveText(notification2);
        }
        final ArrayList arrayList2 = new ArrayList();
        Notification.Action[] actionArr2 = notification2.actions;
        int[] intArray = notification2.extras.getIntArray(NotificationCompat.EXTRA_COMPACT_ACTIONS);
        if (intArray == null || (arrayList = ArraysKt.toMutableList(intArray)) == null) {
            arrayList = new ArrayList();
        }
        final List list = arrayList;
        Context packageContext = sbn.getPackageContext(this.context);
        Intrinsics.checkExpressionValueIsNotNull(packageContext, "sbn.getPackageContext(context)");
        if (actionArr2 != null) {
            int length = actionArr2.length;
            while (i < length) {
                int i2 = length;
                final Notification.Action action = actionArr2[i];
                if (action.getIcon() == null) {
                    actionArr = actionArr2;
                    Log.i("MediaDataManager", "No icon for action " + i + ' ' + action.title);
                    list.remove(Integer.valueOf(i));
                    notification = notification2;
                } else {
                    actionArr = actionArr2;
                    notification = notification2;
                    arrayList2.add(new MediaAction(action.getIcon().loadDrawable(packageContext), action.actionIntent != null ? new Runnable() { // from class: com.android.systemui.media.MediaDataManager$loadMediaDataInBg$runnable$1
                        @Override // java.lang.Runnable
                        public final void run() {
                            try {
                                action.actionIntent.send();
                            } catch (PendingIntent.CanceledException e) {
                                Log.d("MediaDataManager", "Intent canceled", e);
                            }
                        }
                    } : null, action.title));
                }
                i++;
                length = i2;
                actionArr2 = actionArr;
                notification2 = notification;
            }
        }
        final Notification notification4 = notification2;
        this.foregroundExecutor.execute(new Runnable() { // from class: com.android.systemui.media.MediaDataManager.loadMediaDataInBg.1
            @Override // java.lang.Runnable
            public final void run() {
                MediaData mediaData = (MediaData) MediaDataManager.this.mediaEntries.get(key);
                Runnable resumeAction = mediaData != null ? mediaData.getResumeAction() : null;
                MediaData mediaData2 = (MediaData) MediaDataManager.this.mediaEntries.get(key);
                boolean z = mediaData2 != null && mediaData2.getHasCheckedForResume();
                MediaData mediaData3 = (MediaData) MediaDataManager.this.mediaEntries.get(key);
                boolean active = mediaData3 != null ? mediaData3.getActive() : true;
                MediaDataManager mediaDataManager = MediaDataManager.this;
                String str = key;
                String str2 = oldKey;
                int normalizedUserId = sbn.getNormalizedUserId();
                int i3 = iComputeBackgroundColor;
                String str3 = strLoadHeaderAppName;
                Drawable drawable = drawableLoadDrawable2;
                CharSequence charSequence = (CharSequence) objectRef2.element;
                CharSequence charSequence2 = (CharSequence) objectRef.element;
                Icon icon2 = icon;
                List list2 = arrayList2;
                List list3 = list;
                String packageName = sbn.getPackageName();
                Intrinsics.checkExpressionValueIsNotNull(packageName, "sbn.packageName");
                mediaDataManager.onMediaDataLoaded(str, str2, new MediaData(normalizedUserId, true, i3, str3, drawable, charSequence, charSequence2, icon2, list2, list3, packageName, token, notification4.contentIntent, null, active, resumeAction, false, key, z, 65536, null));
            }
        });
    }

    private final Bitmap loadBitmapFromUri(MediaMetadata metadata) {
        for (String str : MediaDataManagerKt.ART_URIS) {
            String string = metadata.getString(str);
            if (!TextUtils.isEmpty(string)) {
                Uri uri = Uri.parse(string);
                Intrinsics.checkExpressionValueIsNotNull(uri, "Uri.parse(uriString)");
                Bitmap bitmapLoadBitmapFromUri = loadBitmapFromUri(uri);
                if (bitmapLoadBitmapFromUri != null) {
                    Log.d("MediaDataManager", "loaded art from " + str);
                    return bitmapLoadBitmapFromUri;
                }
            }
        }
        return null;
    }

    private final Bitmap loadBitmapFromUri(Uri uri) {
        if (uri.getScheme() == null) {
            return null;
        }
        if (!uri.getScheme().equals("content") && !uri.getScheme().equals("android.resource") && !uri.getScheme().equals("file")) {
            return null;
        }
        try {
            return ImageDecoder.decodeBitmap(ImageDecoder.createSource(this.context.getContentResolver(), uri), new ImageDecoder.OnHeaderDecodedListener() { // from class: com.android.systemui.media.MediaDataManager.loadBitmapFromUri.1
                @Override // android.graphics.ImageDecoder.OnHeaderDecodedListener
                public final void onHeaderDecoded(ImageDecoder decoder, ImageDecoder.ImageInfo imageInfo, ImageDecoder.Source source) {
                    Intrinsics.checkExpressionValueIsNotNull(decoder, "decoder");
                    decoder.setMutableRequired(true);
                }
            });
        } catch (IOException e) {
            LogUtils.w("MediaDataManager", "loadBitmapFromUri: fail by ", e);
            return null;
        } catch (UnsupportedOperationException e2) {
            LogUtils.w("MediaDataManager", "loadBitmapFromUri: fail by ", e2);
            return null;
        }
    }

    private final int computeBackgroundColor(Bitmap artworkBitmap) {
        int rgb;
        if (artworkBitmap != null) {
            Palette paletteGenerate = MediaNotificationProcessor.generateArtworkPaletteBuilder(artworkBitmap).generate();
            Intrinsics.checkExpressionValueIsNotNull(paletteGenerate, "MediaNotificationProcess…              .generate()");
            Palette.Swatch swatch = MediaNotificationProcessor.findBackgroundSwatch(paletteGenerate);
            Intrinsics.checkExpressionValueIsNotNull(swatch, "swatch");
            rgb = swatch.getRgb();
        } else {
            rgb = -1;
        }
        float[] fArr = {0.0f, 0.0f, 0.0f};
        ColorUtils.colorToHSL(rgb, fArr);
        float f = fArr[2];
        if (f < 0.05f || f > 0.95f) {
            fArr[1] = 0.0f;
        }
        fArr[1] = fArr[1] * 0.8f;
        fArr[2] = 0.25f;
        return ColorUtils.HSLToColor(fArr);
    }

    private final MediaAction getResumeMediaAction(Runnable action) {
        return new MediaAction(this.context.getDrawable(R.drawable.lb_ic_play), action, this.context.getString(R.string.controls_media_resume));
    }

    public final void onMediaDataLoaded(@NotNull String key, @Nullable String oldKey, @NotNull MediaData data) {
        Intrinsics.checkParameterIsNotNull(key, "key");
        Intrinsics.checkParameterIsNotNull(data, "data");
        Assert.isMainThread();
        if (this.mediaEntries.containsKey(key)) {
            this.mediaEntries.put(key, data);
            Iterator it = CollectionsKt.toSet(this.listeners).iterator();
            while (it.hasNext()) {
                ((Listener) it.next()).onMediaDataLoaded(key, oldKey, data);
            }
        }
    }

    public final void onNotificationRemoved(@NotNull String key) {
        Intrinsics.checkParameterIsNotNull(key, "key");
        Assert.isMainThread();
        MediaData mediaDataRemove = this.mediaEntries.remove(key);
        if (this.useMediaResumption) {
            if ((mediaDataRemove != null ? mediaDataRemove.getResumeAction() : null) != null) {
                Log.d("MediaDataManager", "Not removing " + key + " because resumable");
                Runnable resumeAction = mediaDataRemove.getResumeAction();
                if (resumeAction == null) {
                    Intrinsics.throwNpe();
                }
                MediaData mediaDataCopy$default = MediaData.copy$default(mediaDataRemove, 0, false, 0, null, null, null, null, null, CollectionsKt.listOf(getResumeMediaAction(resumeAction)), CollectionsKt.listOf(0), null, null, null, null, false, null, true, null, false, 439551, null);
                String packageName = mediaDataRemove != null ? mediaDataRemove.getPackageName() : null;
                boolean z = this.mediaEntries.put(packageName, mediaDataCopy$default) == null;
                Set set = CollectionsKt.toSet(this.listeners);
                if (!z) {
                    Set set2 = set;
                    Iterator it = set2.iterator();
                    while (it.hasNext()) {
                        ((Listener) it.next()).onMediaDataRemoved(key);
                    }
                    Iterator it2 = set2.iterator();
                    while (it2.hasNext()) {
                        ((Listener) it2.next()).onMediaDataLoaded(packageName, packageName, mediaDataCopy$default);
                    }
                    return;
                }
                Iterator it3 = set.iterator();
                while (it3.hasNext()) {
                    ((Listener) it3.next()).onMediaDataLoaded(packageName, key, mediaDataCopy$default);
                }
                return;
            }
        }
        if (mediaDataRemove != null) {
            Iterator it4 = CollectionsKt.toSet(this.listeners).iterator();
            while (it4.hasNext()) {
                ((Listener) it4.next()).onMediaDataRemoved(key);
            }
        }
    }

    public final void setMediaResumptionEnabled(boolean isEnabled) {
        if (this.useMediaResumption == isEnabled) {
            return;
        }
        this.useMediaResumption = isEnabled;
        if (this.useMediaResumption) {
            return;
        }
        Set set = CollectionsKt.toSet(this.listeners);
        LinkedHashMap<String, MediaData> linkedHashMap = this.mediaEntries;
        LinkedHashMap linkedHashMap2 = new LinkedHashMap();
        for (Map.Entry<String, MediaData> entry : linkedHashMap.entrySet()) {
            if (!entry.getValue().getActive()) {
                linkedHashMap2.put(entry.getKey(), entry.getValue());
            }
        }
        for (Map.Entry entry2 : linkedHashMap2.entrySet()) {
            this.mediaEntries.remove(entry2.getKey());
            Iterator it = set.iterator();
            while (it.hasNext()) {
                ((Listener) it.next()).onMediaDataRemoved((String) entry2.getKey());
            }
        }
    }

    @Override // com.android.systemui.Dumpable
    public void dump(@NotNull FileDescriptor fd, @NotNull PrintWriter pw, @NotNull String[] args) {
        Intrinsics.checkParameterIsNotNull(fd, "fd");
        Intrinsics.checkParameterIsNotNull(pw, "pw");
        Intrinsics.checkParameterIsNotNull(args, "args");
        pw.println("listeners: " + this.listeners);
        pw.println("mediaEntries: " + this.mediaEntries);
        pw.println("useMediaResumption: " + this.useMediaResumption);
    }
}
