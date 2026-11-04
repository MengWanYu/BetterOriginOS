package com.android.systemui.statusbar;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Icon;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSession;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.os.AsyncTask;
import android.os.Trace;
import android.provider.DeviceConfig;
import android.text.TextUtils;
import android.util.ArraySet;
import android.widget.ImageView;
import androidx.core.app.NotificationCompat;
import com.android.internal.statusbar.NotificationVisibility;
import com.android.systemui.Dependency;
import com.android.systemui.Dumpable;
import com.android.systemui.Interpolators;
import com.android.systemui.colorextraction.SysuiColorExtractor;
import com.android.systemui.dagger.qualifiers.Main;
import com.android.systemui.media.MediaDataManager;
import com.android.systemui.plugins.statusbar.StatusBarStateController;
import com.android.systemui.statusbar.notification.NotificationEntryListener;
import com.android.systemui.statusbar.notification.NotificationEntryManager;
import com.android.systemui.statusbar.notification.collection.NotificationEntry;
import com.android.systemui.statusbar.phone.BiometricUnlockController;
import com.android.systemui.statusbar.phone.KeyguardBypassController;
import com.android.systemui.statusbar.phone.LockscreenWallpaper;
import com.android.systemui.statusbar.phone.NotificationShadeWindowController;
import com.android.systemui.statusbar.phone.ScrimController;
import com.android.systemui.statusbar.phone.ScrimState;
import com.android.systemui.statusbar.phone.StatusBar;
import com.android.systemui.statusbar.policy.KeyguardStateController;
import com.android.systemui.util.DeviceConfigProxy;
import com.android.systemui.util.concurrency.DelayableExecutor;
import com.vivo.common.utils.LogUtils;
import dagger.Lazy;
import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

/* JADX INFO: loaded from: classes2.dex */
public class NotificationMediaManager implements Dumpable {
    public static final boolean DEBUG_MEDIA = false;
    private static final HashSet<Integer> PAUSED_MEDIA_STATES = new HashSet<>();
    private static final String TAG = "NotificationMediaManager";
    private BackDropView mBackdrop;
    private ImageView mBackdropBack;
    private ImageView mBackdropFront;
    private BiometricUnlockController mBiometricUnlockController;
    private final Context mContext;
    private final NotificationEntryManager mEntryManager;
    private final KeyguardBypassController mKeyguardBypassController;
    private LockscreenWallpaper mLockscreenWallpaper;
    private final DelayableExecutor mMainExecutor;
    private final MediaArtworkProcessor mMediaArtworkProcessor;
    private MediaController mMediaController;
    private final MediaDataManager mMediaDataManager;
    private MediaMetadata mMediaMetadata;
    private String mMediaNotificationKey;
    private final MediaSessionManager mMediaSessionManager;
    private Lazy<NotificationShadeWindowController> mNotificationShadeWindowController;
    protected NotificationPresenter mPresenter;
    private ScrimController mScrimController;
    private boolean mShowCompactMediaSeekbar;
    private final Lazy<StatusBar> mStatusBarLazy;
    private final StatusBarStateController mStatusBarStateController = (StatusBarStateController) Dependency.get(StatusBarStateController.class);
    private final SysuiColorExtractor mColorExtractor = (SysuiColorExtractor) Dependency.get(SysuiColorExtractor.class);
    private final KeyguardStateController mKeyguardStateController = (KeyguardStateController) Dependency.get(KeyguardStateController.class);
    private final Set<AsyncTask<?, ?, ?>> mProcessArtworkTasks = new ArraySet();
    private List<String> mAllMediaNotificationKey = new ArrayList();
    private final DeviceConfig.OnPropertiesChangedListener mPropertiesChangedListener = new DeviceConfig.OnPropertiesChangedListener() { // from class: com.android.systemui.statusbar.NotificationMediaManager.1
        public void onPropertiesChanged(DeviceConfig.Properties properties) {
            for (String str : properties.getKeyset()) {
                if ("compact_media_notification_seekbar_enabled".equals(str)) {
                    String string = properties.getString(str, (String) null);
                    NotificationMediaManager.this.mShowCompactMediaSeekbar = "true".equals(string);
                }
            }
        }
    };
    private final MediaController.Callback mMediaListener = new MediaController.Callback() { // from class: com.android.systemui.statusbar.NotificationMediaManager.2
        @Override // android.media.session.MediaController.Callback
        public void onPlaybackStateChanged(PlaybackState playbackState) {
            super.onPlaybackStateChanged(playbackState);
            if (playbackState != null) {
                if (!NotificationMediaManager.this.isPlaybackActive(playbackState.getState())) {
                    NotificationMediaManager.this.clearCurrentMediaNotification();
                }
                NotificationMediaManager.this.findAndUpdateMediaNotifications();
            }
        }

        @Override // android.media.session.MediaController.Callback
        public void onMetadataChanged(MediaMetadata mediaMetadata) {
            super.onMetadataChanged(mediaMetadata);
            NotificationMediaManager.this.mMediaArtworkProcessor.clearCache();
            NotificationMediaManager.this.mMediaMetadata = mediaMetadata;
            NotificationMediaManager.this.dispatchUpdateMediaMetaData(true, true);
        }
    };
    protected final Runnable mHideBackdropFront = new Runnable() { // from class: com.android.systemui.statusbar.NotificationMediaManager.4
        @Override // java.lang.Runnable
        public void run() {
            NotificationMediaManager.this.mBackdropFront.setVisibility(4);
            NotificationMediaManager.this.mBackdropFront.animate().cancel();
            NotificationMediaManager.this.mBackdropFront.setImageDrawable(null);
        }
    };
    private final ArrayList<MediaListener> mMediaListeners = new ArrayList<>();

    public interface MediaListener {
        default void onPrimaryMetadataOrStateChanged(MediaMetadata mediaMetadata, int i) {
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public boolean isPlaybackActive(int i) {
        return (i == 1 || i == 7 || i == 0) ? false : true;
    }

    static {
        PAUSED_MEDIA_STATES.add(0);
        PAUSED_MEDIA_STATES.add(1);
        PAUSED_MEDIA_STATES.add(2);
        PAUSED_MEDIA_STATES.add(7);
        PAUSED_MEDIA_STATES.add(8);
    }

    public NotificationMediaManager(Context context, Lazy<StatusBar> lazy, Lazy<NotificationShadeWindowController> lazy2, NotificationEntryManager notificationEntryManager, MediaArtworkProcessor mediaArtworkProcessor, KeyguardBypassController keyguardBypassController, @Main DelayableExecutor delayableExecutor, DeviceConfigProxy deviceConfigProxy, final MediaDataManager mediaDataManager) {
        this.mContext = context;
        this.mMediaArtworkProcessor = mediaArtworkProcessor;
        this.mKeyguardBypassController = keyguardBypassController;
        this.mMediaSessionManager = (MediaSessionManager) this.mContext.getSystemService("media_session");
        this.mStatusBarLazy = lazy;
        this.mNotificationShadeWindowController = lazy2;
        this.mEntryManager = notificationEntryManager;
        this.mMainExecutor = delayableExecutor;
        this.mMediaDataManager = mediaDataManager;
        notificationEntryManager.addNotificationEntryListener(new NotificationEntryListener() { // from class: com.android.systemui.statusbar.NotificationMediaManager.3
            @Override // com.android.systemui.statusbar.notification.NotificationEntryListener
            public void onPendingEntryAdded(NotificationEntry notificationEntry) {
                mediaDataManager.onNotificationAdded(notificationEntry.getKey(), notificationEntry.getSbn());
            }

            @Override // com.android.systemui.statusbar.notification.NotificationEntryListener
            public void onPreEntryUpdated(NotificationEntry notificationEntry) {
                mediaDataManager.onNotificationAdded(notificationEntry.getKey(), notificationEntry.getSbn());
            }

            @Override // com.android.systemui.statusbar.notification.NotificationEntryListener
            public void onEntryInflated(NotificationEntry notificationEntry) {
                NotificationMediaManager.this.findAndUpdateMediaNotifications();
            }

            @Override // com.android.systemui.statusbar.notification.NotificationEntryListener
            public void onEntryReinflated(NotificationEntry notificationEntry) {
                NotificationMediaManager.this.findAndUpdateMediaNotifications();
            }

            @Override // com.android.systemui.statusbar.notification.NotificationEntryListener
            public void onEntryRemoved(NotificationEntry notificationEntry, NotificationVisibility notificationVisibility, boolean z, int i) {
                NotificationMediaManager.this.onNotificationRemoved(notificationEntry.getKey());
                mediaDataManager.onNotificationRemoved(notificationEntry.getKey());
            }
        });
        this.mShowCompactMediaSeekbar = "true".equals(DeviceConfig.getProperty("systemui", "compact_media_notification_seekbar_enabled"));
        deviceConfigProxy.addOnPropertiesChangedListener("systemui", this.mContext.getMainExecutor(), this.mPropertiesChangedListener);
    }

    public static boolean isPlayingState(int i) {
        return !PAUSED_MEDIA_STATES.contains(Integer.valueOf(i));
    }

    public void setUpWithPresenter(NotificationPresenter notificationPresenter) {
        this.mPresenter = notificationPresenter;
    }

    public void onNotificationRemoved(String str) {
        removeMediaNotificationKey(str);
        if (str.equals(this.mMediaNotificationKey)) {
            clearCurrentMediaNotification();
            dispatchUpdateMediaMetaData(true, true);
        }
    }

    public String getMediaNotificationKey() {
        return this.mMediaNotificationKey;
    }

    private void removeMediaNotificationKey(String str) {
        if (TextUtils.isEmpty(str) || !this.mAllMediaNotificationKey.contains(str)) {
            return;
        }
        this.mAllMediaNotificationKey.remove(str);
    }

    public void clearAllMediaNotificationKey() {
        this.mAllMediaNotificationKey.clear();
    }

    public List<String> getAllMediaNotificationKey() {
        return this.mAllMediaNotificationKey;
    }

    public MediaMetadata getMediaMetadata() {
        return this.mMediaMetadata;
    }

    public boolean getShowCompactMediaSeekbar() {
        return this.mShowCompactMediaSeekbar;
    }

    public Icon getMediaIcon() {
        if (this.mMediaNotificationKey == null) {
            return null;
        }
        synchronized (this.mEntryManager) {
            NotificationEntry activeNotificationUnfiltered = this.mEntryManager.getActiveNotificationUnfiltered(this.mMediaNotificationKey);
            if (activeNotificationUnfiltered != null && activeNotificationUnfiltered.getIcons().getShelfIcon() != null) {
                return activeNotificationUnfiltered.getIcons().getShelfIcon().getSourceIcon();
            }
            return null;
        }
    }

    public void addCallback(MediaListener mediaListener) {
        this.mMediaListeners.add(mediaListener);
        mediaListener.onPrimaryMetadataOrStateChanged(this.mMediaMetadata, getMediaControllerPlaybackState(this.mMediaController));
    }

    public void removeCallback(MediaListener mediaListener) {
        this.mMediaListeners.remove(mediaListener);
    }

    public void findAndUpdateMediaNotifications() {
        NotificationEntry next;
        MediaController mediaController;
        boolean z;
        NotificationEntry next2;
        boolean z2;
        MediaSession.Token token;
        synchronized (this.mEntryManager) {
            Collection<NotificationEntry> allNotifs = this.mEntryManager.getAllNotifs();
            Iterator<NotificationEntry> it = allNotifs.iterator();
            while (true) {
                if (!it.hasNext()) {
                    next = null;
                    mediaController = null;
                    break;
                }
                next = it.next();
                if (next.isMediaNotification() && (token = (MediaSession.Token) next.getSbn().getNotification().extras.getParcelable(NotificationCompat.EXTRA_MEDIA_SESSION)) != null) {
                    mediaController = new MediaController(this.mContext, token);
                    if (3 == getMediaControllerPlaybackState(mediaController)) {
                        break;
                    }
                }
            }
            z = false;
            if (next == null && this.mMediaSessionManager != null) {
                Iterator it2 = this.mMediaSessionManager.getActiveSessionsForUser(null, -1).iterator();
                while (true) {
                    if (!it2.hasNext()) {
                        break;
                    }
                    MediaController mediaController2 = (MediaController) it2.next();
                    String packageName = mediaController2.getPackageName();
                    Iterator<NotificationEntry> it3 = allNotifs.iterator();
                    while (it3.hasNext()) {
                        next2 = it3.next();
                        if (next2.getSbn().getPackageName().equals(packageName) && next2.getRow() != null && !next2.getRow().isSummaryWithChildren() && (next2.getRow().isMediaRow() || next2.getRow().isCustomNotification())) {
                            z2 = true;
                            mediaController = mediaController2;
                            break;
                        }
                    }
                    next2 = next;
                    z2 = false;
                    if (z2) {
                        next = next2;
                        break;
                    }
                    next = next2;
                }
            }
            if (mediaController != null && !sameSessions(this.mMediaController, mediaController)) {
                clearCurrentMediaNotificationSession();
                this.mMediaController = mediaController;
                this.mMediaController.registerCallback(this.mMediaListener);
                this.mMediaMetadata = this.mMediaController.getMetadata();
                z = true;
            }
            if (next != null && !next.getSbn().getKey().equals(this.mMediaNotificationKey)) {
                this.mMediaNotificationKey = next.getSbn().getKey();
                if (!this.mAllMediaNotificationKey.contains(this.mMediaNotificationKey)) {
                    this.mAllMediaNotificationKey.add(this.mMediaNotificationKey);
                }
                LogUtils.d(TAG, "currenKey:" + this.mMediaNotificationKey + ",allKeys:" + this.mAllMediaNotificationKey.toString());
            }
        }
        if (z) {
            this.mEntryManager.updateNotifications("NotificationMediaManager - metaDataChanged");
        }
        dispatchUpdateMediaMetaData(z, true);
    }

    public void clearCurrentMediaNotification() {
        LogUtils.d(TAG, "clearCurrentMediaNotification()");
        removeMediaNotificationKey(this.mMediaNotificationKey);
        this.mMediaNotificationKey = null;
        clearCurrentMediaNotificationSession();
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void dispatchUpdateMediaMetaData(boolean z, boolean z2) {
        NotificationPresenter notificationPresenter = this.mPresenter;
        if (notificationPresenter != null) {
            notificationPresenter.updateMediaMetaData(z, z2);
        }
        int mediaControllerPlaybackState = getMediaControllerPlaybackState(this.mMediaController);
        ArrayList arrayList = new ArrayList(this.mMediaListeners);
        for (int i = 0; i < arrayList.size(); i++) {
            ((MediaListener) arrayList.get(i)).onPrimaryMetadataOrStateChanged(this.mMediaMetadata, mediaControllerPlaybackState);
        }
    }

    @Override // com.android.systemui.Dumpable
    public void dump(FileDescriptor fileDescriptor, PrintWriter printWriter, String[] strArr) {
        printWriter.print("    mMediaSessionManager=");
        printWriter.println(this.mMediaSessionManager);
        printWriter.print("    mMediaNotificationKey=");
        printWriter.println(this.mMediaNotificationKey);
        printWriter.print("    mMediaController=");
        printWriter.print(this.mMediaController);
        if (this.mMediaController != null) {
            printWriter.print(" state=" + this.mMediaController.getPlaybackState());
        }
        printWriter.println();
        printWriter.print("    mMediaMetadata=");
        printWriter.print(this.mMediaMetadata);
        if (this.mMediaMetadata != null) {
            printWriter.print(" title=" + ((Object) this.mMediaMetadata.getText("android.media.metadata.TITLE")));
        }
        printWriter.println();
    }

    private boolean sameSessions(MediaController mediaController, MediaController mediaController2) {
        if (mediaController == mediaController2) {
            return true;
        }
        if (mediaController == null) {
            return false;
        }
        return mediaController.controlsSameSession(mediaController2);
    }

    private int getMediaControllerPlaybackState(MediaController mediaController) {
        PlaybackState playbackState;
        if (mediaController == null || (playbackState = mediaController.getPlaybackState()) == null) {
            return 0;
        }
        return playbackState.getState();
    }

    private void clearCurrentMediaNotificationSession() {
        this.mMediaArtworkProcessor.clearCache();
        this.mMediaMetadata = null;
        MediaController mediaController = this.mMediaController;
        if (mediaController != null) {
            mediaController.unregisterCallback(this.mMediaListener);
        }
        this.mMediaController = null;
    }

    public void updateMediaMetaData(boolean z, boolean z2) {
        Trace.beginSection("StatusBar#updateMediaMetaData");
        Trace.endSection();
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void finishUpdateMediaMetaData(boolean z, boolean z2, Bitmap bitmap) {
        BiometricUnlockController biometricUnlockController;
        BitmapDrawable bitmapDrawable = bitmap != null ? new BitmapDrawable(this.mBackdropBack.getResources(), bitmap) : null;
        boolean z3 = bitmapDrawable != null;
        NotificationShadeWindowController notificationShadeWindowController = this.mNotificationShadeWindowController.get();
        boolean zIsOccluded = this.mStatusBarLazy.get().isOccluded();
        boolean z4 = bitmapDrawable != null;
        this.mColorExtractor.setHasMediaArtwork(z3);
        ScrimController scrimController = this.mScrimController;
        if (scrimController != null) {
            scrimController.setHasBackdrop(z4);
        }
        if (z4 && this.mStatusBarStateController.getState() != 0 && (biometricUnlockController = this.mBiometricUnlockController) != null && biometricUnlockController.getMode() != 2 && !zIsOccluded) {
            if (this.mBackdrop.getVisibility() != 0) {
                this.mBackdrop.setVisibility(0);
                if (z2) {
                    this.mBackdrop.setAlpha(0.0f);
                    this.mBackdrop.animate().alpha(1.0f);
                } else {
                    this.mBackdrop.animate().cancel();
                    this.mBackdrop.setAlpha(1.0f);
                }
                if (notificationShadeWindowController != null) {
                    notificationShadeWindowController.setBackdropShowing(true);
                }
                z = true;
            }
            if (z) {
                if (this.mBackdropBack.getDrawable() != null) {
                    this.mBackdropFront.setImageDrawable(this.mBackdropBack.getDrawable().getConstantState().newDrawable(this.mBackdropFront.getResources()).mutate());
                    this.mBackdropFront.setAlpha(1.0f);
                    this.mBackdropFront.setVisibility(0);
                } else {
                    this.mBackdropFront.setVisibility(4);
                }
                this.mBackdropBack.setImageDrawable(bitmapDrawable);
                if (this.mBackdropFront.getVisibility() == 0) {
                    this.mBackdropFront.animate().setDuration(250L).alpha(0.0f).withEndAction(this.mHideBackdropFront);
                    return;
                }
                return;
            }
            return;
        }
        if (this.mBackdrop.getVisibility() != 8) {
            boolean z5 = this.mStatusBarStateController.isDozing() && !ScrimState.AOD.getAnimateChange();
            boolean zIsBypassFadingAnimation = this.mKeyguardStateController.isBypassFadingAnimation();
            BiometricUnlockController biometricUnlockController2 = this.mBiometricUnlockController;
            if ((((biometricUnlockController2 != null && biometricUnlockController2.getMode() == 2) || z5) && !zIsBypassFadingAnimation) || zIsOccluded) {
                this.mBackdrop.setVisibility(8);
                this.mBackdropBack.setImageDrawable(null);
                if (notificationShadeWindowController != null) {
                    notificationShadeWindowController.setBackdropShowing(false);
                    return;
                }
                return;
            }
            if (notificationShadeWindowController != null) {
                notificationShadeWindowController.setBackdropShowing(false);
            }
            this.mBackdrop.animate().alpha(0.0f).setInterpolator(Interpolators.ACCELERATE_DECELERATE).setDuration(300L).setStartDelay(0L).withEndAction(new Runnable() { // from class: com.android.systemui.statusbar.-$$Lambda$NotificationMediaManager$5ApBYxWBRgBH6AkWUHgwLiCFqEk
                @Override // java.lang.Runnable
                public final void run() {
                    this.f$0.lambda$finishUpdateMediaMetaData$0$NotificationMediaManager();
                }
            });
            if (this.mKeyguardStateController.isKeyguardFadingAway()) {
                this.mBackdrop.animate().setDuration(this.mKeyguardStateController.getShortenedFadingAwayDuration()).setStartDelay(this.mKeyguardStateController.getKeyguardFadingAwayDelay()).setInterpolator(Interpolators.LINEAR).start();
            }
        }
    }

    public /* synthetic */ void lambda$finishUpdateMediaMetaData$0$NotificationMediaManager() {
        this.mBackdrop.setVisibility(8);
        this.mBackdropFront.animate().cancel();
        this.mBackdropBack.setImageDrawable(null);
        this.mMainExecutor.execute(this.mHideBackdropFront);
    }

    public void setup(BackDropView backDropView, ImageView imageView, ImageView imageView2, ScrimController scrimController, LockscreenWallpaper lockscreenWallpaper) {
        this.mBackdrop = backDropView;
        this.mBackdropFront = imageView;
        this.mBackdropBack = imageView2;
        this.mScrimController = scrimController;
        this.mLockscreenWallpaper = lockscreenWallpaper;
    }

    public void setBiometricUnlockController(BiometricUnlockController biometricUnlockController) {
        this.mBiometricUnlockController = biometricUnlockController;
    }

    /* JADX INFO: Access modifiers changed from: private */
    public Bitmap processArtwork(Bitmap bitmap) {
        return this.mMediaArtworkProcessor.processArtwork(this.mContext, bitmap);
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void removeTask(AsyncTask<?, ?, ?> asyncTask) {
        this.mProcessArtworkTasks.remove(asyncTask);
    }

    private static final class ProcessArtworkTask extends AsyncTask<Bitmap, Void, Bitmap> {
        private final boolean mAllowEnterAnimation;
        private final WeakReference<NotificationMediaManager> mManagerRef;
        private final boolean mMetaDataChanged;

        ProcessArtworkTask(NotificationMediaManager notificationMediaManager, boolean z, boolean z2) {
            this.mManagerRef = new WeakReference<>(notificationMediaManager);
            this.mMetaDataChanged = z;
            this.mAllowEnterAnimation = z2;
        }

        /* JADX INFO: Access modifiers changed from: protected */
        @Override // android.os.AsyncTask
        public Bitmap doInBackground(Bitmap... bitmapArr) {
            NotificationMediaManager notificationMediaManager = this.mManagerRef.get();
            if (notificationMediaManager == null || bitmapArr.length == 0 || isCancelled()) {
                return null;
            }
            return notificationMediaManager.processArtwork(bitmapArr[0]);
        }

        /* JADX INFO: Access modifiers changed from: protected */
        @Override // android.os.AsyncTask
        public void onPostExecute(Bitmap bitmap) {
            NotificationMediaManager notificationMediaManager = this.mManagerRef.get();
            if (notificationMediaManager == null || isCancelled()) {
                return;
            }
            notificationMediaManager.removeTask(this);
            notificationMediaManager.finishUpdateMediaMetaData(this.mMetaDataChanged, this.mAllowEnterAnimation, bitmap);
        }

        /* JADX INFO: Access modifiers changed from: protected */
        @Override // android.os.AsyncTask
        public void onCancelled(Bitmap bitmap) {
            if (bitmap != null) {
                bitmap.recycle();
            }
            NotificationMediaManager notificationMediaManager = this.mManagerRef.get();
            if (notificationMediaManager != null) {
                notificationMediaManager.removeTask(this);
            }
        }
    }
}
