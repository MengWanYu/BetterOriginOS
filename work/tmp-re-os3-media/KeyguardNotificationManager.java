package com.vivo.systemui.statusbar.notification;

import android.animation.ObjectAnimator;
import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.service.notification.StatusBarNotification;
import android.util.ArrayMap;
import android.util.FtFeature;
import com.android.keyguard.KeyguardUpdateMonitor;
import com.android.keyguard.vivo.ChildModeManager;
import com.android.systemui.Dependency;
import com.android.systemui.R;
import com.android.systemui.statusbar.notification.NotificationFilter;
import com.android.systemui.statusbar.notification.collection.GroupEntry;
import com.android.systemui.statusbar.notification.collection.ListEntry;
import com.android.systemui.statusbar.notification.collection.NotifCollection;
import com.android.systemui.statusbar.notification.collection.NotifPipeline;
import com.android.systemui.statusbar.notification.collection.NotificationEntry;
import com.android.systemui.statusbar.notification.collection.ShadeListBuilder;
import com.android.systemui.statusbar.notification.collection.legacy.NotificationGroupManagerLegacy;
import com.android.systemui.statusbar.notification.collection.listbuilder.pluggable.NotifFilter;
import com.android.systemui.statusbar.notification.collection.notifcollection.NotifCollectionListener;
import com.android.systemui.statusbar.notification.collection.render.GroupMembershipManager;
import com.android.systemui.statusbar.notification.row.ExpandableNotificationRow;
import com.android.systemui.statusbar.notification.stack.NotificationStackScrollLayout;
import com.android.systemui.statusbar.phone.CentralSurfaces;
import com.android.systemui.statusbar.phone.StatusBarKeyguardViewManager;
import com.vivo.common.config.VivoCustomizedConfig;
import com.vivo.common.monitor.VivoSystemUIUpdateMonitorCallback;
import com.vivo.common.trackevent.misc.NotificationExposureHelper;
import com.vivo.common.utils.ApplicationUtils;
import com.vivo.common.utils.LogUtils;
import com.vivo.common.utils.SystemPropertiesFacade;
import com.vivo.keyguard.plugin.KeyguardPlugin;
import com.vivo.keyguard.presenter.theme.KeyguardThemeUtils;
import com.vivo.plugin.PluginManager;
import com.vivo.systemui.export.utils.MultiUsersUtils;
import com.vivo.systemui.statusbar.motomode.MotoModeManager;
import com.vivo.systemui.statusbar.utils.CarModeManager;
import com.vivo.systemui.statusbar.utils.StateUtils;
import com.vivo.systemui.statusbar.utils.VivoConstants;
import com.vivo.systemui.statusbar.utils.VivoDeviceUtils;
import com.vivo.systemui.statusbar.utils.VivoNotificationUtils;
import com.vivo.systemui.statusbar.utils.VivoWhiteList;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;

/* JADX INFO: loaded from: classes3.dex */
public class KeyguardNotificationManager implements VivoSystemUIUpdateMonitorCallback, NotifCollectionListener {
    private static final String ALLOWED_TAG = "allowedOnKeyguard";
    private static final int MAX_ITEM = 4;
    private static final String SETTING_KEYGUARD_MULTIUSER_HAS_SWITCH = "persist.vivo.keyguard.multiuser.has.switch";
    public static final int STATE_KEYGUARD = 1;
    public static final int STATE_NORMAL = 0;
    private static final String TAG = "KeyguardNotificationManager";
    public static boolean sCurClassInit = false;
    private Context mContext;
    private NotificationGroupManagerLegacy mGroupManager;
    private GroupMembershipManager mGroupMembershipManager;
    private IKeyguardMusicWidgetCallback mMusicWidgetCallback;
    private int mMusicWidgetHeight;
    private NotifCollection mNotifCollection;
    private NotifPipeline mNotifPipeline;
    private int mNotificationMarginTop;
    private NotificationOpsManager mNotificationOpsManager;
    private NotificationScreenUtils mNotificationScreenUtils;
    private ShadeListBuilder mShadeListBuilder;
    private ObjectAnimator mStackAlphaAnimation;
    private CentralSurfaces mStatusBar;
    private StatusBarKeyguardViewManager mStatusBarKeyguardViewManager;
    private boolean mStayingKeyguard;
    private int mTopCurrentPadding;
    private int mWidgetViewHeight;
    private int singleHeight;
    private HashSet<Callback> mCallbacks = new HashSet<>();
    private int mKeyguardNotificationCount = 0;
    private int mState = 0;
    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private ArrayMap<String, StatusBarNotification> mKeyguardNotifications = null;
    private final boolean mIsSupportFocusMode = FtFeature.isFeatureSupport("vivo.opt.notification.focusmode");
    private NotifFilter mNotifFilter = new NotifFilter(TAG) { // from class: com.vivo.systemui.statusbar.notification.KeyguardNotificationManager.1
        @Override // com.android.systemui.statusbar.notification.collection.listbuilder.pluggable.NotifFilter
        public boolean shouldFilterOut(NotificationEntry notificationEntry, long j) {
            if (!StateUtils.getInstance().isKeyguardShowingWithoutShade() || KeyguardNotificationManager.this.mGroupMembershipManager.isGroupSummary(notificationEntry)) {
                return false;
            }
            if (!KeyguardNotificationManager.this.allowedOnKeyguard(notificationEntry)) {
                LogUtils.d(KeyguardNotificationManager.ALLOWED_TAG, " notif filter not allow on keyguard and key = " + notificationEntry.getKey());
                return true;
            }
            if (notificationEntry.getBelongToKeyguard()) {
                return false;
            }
            LogUtils.d(KeyguardNotificationManager.ALLOWED_TAG, " notif filter not belong to keyguard and key = " + notificationEntry.getKey());
            return true;
        }
    };
    private boolean mIsWidgetVisible = false;

    public interface Callback {
        default void onKeyguardMusicVisibleChanged(boolean z) {
        }

        default void onKeyguardNotificationAppear(boolean z) {
        }

        default void onKeyguardNotificationClick(Runnable runnable) {
        }

        default void onKeyguardNotificationDisappear(boolean z) {
        }

        default void onMissedCallNotificationChanged(boolean z) {
        }

        default void onNotificationTopPadingChanged(int i) {
        }

        default void onUnreadSmsNotificationChanged(boolean z) {
        }
    }

    public interface IKeyguardMusicWidgetCallback {
        void onMultiUserContainerVisibleChange();

        void onStepWeatherTimeWidgetVisibleChange(boolean z);
    }

    public static int getMaxKeyguardNotifications() {
        return 4;
    }

    public KeyguardNotificationManager(Context context, NotifPipeline notifPipeline, NotifCollection notifCollection, ShadeListBuilder shadeListBuilder) {
        this.mContext = context.getApplicationContext();
        this.mNotifPipeline = notifPipeline;
        notifPipeline.addFinalizeFilter(this.mNotifFilter);
        this.mNotifCollection = notifCollection;
        this.mShadeListBuilder = shadeListBuilder;
        this.mTopCurrentPadding = context.getResources().getDimensionPixelSize(R.dimen.vivo_keyguard_notification_music_margin_top);
        KeyguardPlugin keyguardPlugin = (KeyguardPlugin) PluginManager.INSTANCE.getPlugin(KeyguardPlugin.class);
        if (keyguardPlugin == null) {
            this.mMusicWidgetHeight = context.getResources().getDimensionPixelSize(R.dimen.vivo_keyguard_music_widget_container_top_margin);
        } else {
            this.mMusicWidgetHeight = keyguardPlugin.getWidgetHeight() + context.getResources().getDimensionPixelSize(R.dimen.vivo_keyguard_music_widget_container_top_margin);
        }
        this.mNotificationMarginTop = this.mContext.getResources().getDimensionPixelSize(R.dimen.vivo_keyguard_notification_top_margin_online_theme);
        this.mNotificationOpsManager = (NotificationOpsManager) Dependency.get(NotificationOpsManager.class);
        this.mGroupManager = (NotificationGroupManagerLegacy) Dependency.get(NotificationGroupManagerLegacy.class);
        this.mGroupMembershipManager = (GroupMembershipManager) Dependency.get(GroupMembershipManager.class);
        this.mStatusBar = (CentralSurfaces) Dependency.get(CentralSurfaces.class);
        this.singleHeight = this.mContext.getResources().getDimensionPixelSize(R.dimen.vivo_keyguard_multiple_user_item_height);
        boolean zIsPackageInstalled = ApplicationUtils.isPackageInstalled(this.mContext, "com.vivo.agent");
        LogUtils.i(TAG, "Jovi --> mJoviInstalled= " + zIsPackageInstalled);
        onJoviInstalledChanged(zIsPackageInstalled);
        sCurClassInit = true;
    }

    private void notifyCallAndSms(StatusBarNotification statusBarNotification, boolean z) {
        if (isMissedCall(statusBarNotification)) {
            notifyMissedCallNotificationChanged(z);
        } else if (isUnreadSms(statusBarNotification)) {
            notifyUnreadSmsNotificationChanged(z);
        }
    }

    @Override // com.android.systemui.statusbar.notification.collection.notifcollection.NotifCollectionListener
    public void onEntryUpdated(NotificationEntry notificationEntry, boolean z) {
        if (getState() != 1) {
            LogUtils.i(TAG, "onNotificationUpdate -- not in KeyguardState, return!");
            return;
        }
        boolean zAllowedOnKeyguard = allowedOnKeyguard(notificationEntry);
        LogUtils.i(TAG, "onNotificationUpdate, shown = " + zAllowedOnKeyguard + ", pkg = " + notificationEntry.getSbn().getPackageName() + ", id = " + notificationEntry.getSbn().getId() + ", getState() = " + getState() + ", isAppLocked = " + isAppLocked(notificationEntry.getSbn()) + ", mKeyguardNotificationCount = " + this.mKeyguardNotificationCount + ", belongToKeyguard = " + notificationEntry.getBelongToKeyguard());
        if (zAllowedOnKeyguard) {
            notifyCallAndSms(notificationEntry.getSbn(), true);
            if (!VivoNotificationUtils.considerAsOngoing(notificationEntry.getSbn())) {
                notifyScreenOn(notificationEntry);
            }
            addKeyguardNotification(notificationEntry.getSbn());
            addKeyguardNotificationData(notificationEntry);
            return;
        }
        removeKeyguardNotification(notificationEntry.getSbn());
        removeKeyguardNotificationData(notificationEntry);
    }

    @Override // com.android.systemui.statusbar.notification.collection.notifcollection.NotifCollectionListener
    public void onEntryRemoved(NotificationEntry notificationEntry, int i) {
        StatusBarNotification sbn = notificationEntry.getSbn();
        boolean zShouldShowOnKeyguard = shouldShowOnKeyguard(notificationEntry);
        LogUtils.i(TAG, "onNotificationRemove, shown = " + zShouldShowOnKeyguard + ", pkg = " + sbn.getPackageName() + ", id = " + sbn.getId() + ", isAppLocked = " + isAppLocked(sbn) + ", mKeyguardNotificationCount = " + this.mKeyguardNotificationCount);
        removeKeyguardNotification(sbn);
        removeKeyguardNotificationData(notificationEntry);
        if (zShouldShowOnKeyguard) {
            notifyCallAndSms(sbn, false);
            NotificationScreenUtils notificationScreenUtils = this.mNotificationScreenUtils;
            if (notificationScreenUtils != null) {
                notificationScreenUtils.onNotificationRemove(sbn);
            }
        }
    }

    @Override // com.android.systemui.statusbar.notification.collection.notifcollection.NotifCollectionListener
    public void onEntryAdded(NotificationEntry notificationEntry) {
        if (getState() != 1) {
            LogUtils.i(TAG, "onNotificationAdd -- not in KeyguardState, return!");
            return;
        }
        boolean zAllowedOnKeyguard = allowedOnKeyguard(notificationEntry);
        LogUtils.i(TAG, "onNotificationAdd, show = " + zAllowedOnKeyguard + ", pkg = " + notificationEntry.getSbn().getPackageName() + ", id = " + notificationEntry.getSbn().getId() + ", isAppLocked = " + isAppLocked(notificationEntry.getSbn()) + ", mKeyguardNotificationCount = " + this.mKeyguardNotificationCount + ", isKeyguardShowingWithoutShade() = " + StateUtils.getInstance().isKeyguardShowingWithoutShade() + ", getState() = " + getState());
        NotificationStackScrollLayout notificationStackScrollLayout = (NotificationStackScrollLayout) this.mStatusBar.getNotificationScrollLayout();
        if (notificationStackScrollLayout != null) {
            LogUtils.d(TAG, "stackY = " + notificationStackScrollLayout.getTranslationY() + "; stackAmbientY = " + notificationStackScrollLayout.getStackTranslation() + "; stackAppearEnd = " + notificationStackScrollLayout.getAppearEndPosition() + "; stackTopPadding = " + notificationStackScrollLayout.getTopPadding() + "; keyguardTopPadding = " + getTopPadding());
        }
        if (zAllowedOnKeyguard) {
            notifyCallAndSms(notificationEntry.getSbn(), true);
            notifyScreenOn(notificationEntry);
            addKeyguardNotification(notificationEntry.getSbn());
            addKeyguardNotificationData(notificationEntry);
            NotificationExposureHelper.onKeyguardNotiAdd();
            return;
        }
        removeKeyguardNotification(notificationEntry.getSbn());
        removeKeyguardNotificationData(notificationEntry);
    }

    private boolean alwaysShowedOnKeyguard(NotificationEntry notificationEntry) {
        if (shouldMutexAndHide(notificationEntry)) {
            return false;
        }
        StatusBarNotification sbn = notificationEntry.getSbn();
        if (VivoWhiteList.SHOW_CTS_NOTIFICATION_ON_KEYGUARD.contains(sbn.getPackageName())) {
            return true;
        }
        boolean zIsKeyguardNotificationEnabled = this.mNotificationOpsManager.isKeyguardNotificationEnabled(sbn.getPackageName(), sbn.getUid());
        if (VivoNotificationUtils.isCarModeNotification(notificationEntry.getSbn()) && allowedOnKeyguard(notificationEntry)) {
            return true;
        }
        return zIsKeyguardNotificationEnabled && notificationEntry.getRow() != null && notificationEntry.getRow().isMediaNotification() && !VivoConstants.BBK_MUSIC_PKG.equals(notificationEntry.getSbn().getPackageName());
    }

    private void addKeyguardNotificationData(NotificationEntry notificationEntry) {
        if (!this.mGroupManager.isGroupSummary(notificationEntry.getSbn())) {
            updateKeyguardNotificationCount(notificationEntry, true);
            notificationEntry.setBelongToKeyguard(true);
            NotificationEntry logicalGroupSummary = this.mGroupManager.getLogicalGroupSummary(notificationEntry);
            if (logicalGroupSummary != null) {
                updateKeyguardNotificationCount(logicalGroupSummary, true);
                logicalGroupSummary.setBelongToKeyguard(true);
                return;
            }
            return;
        }
        ArrayList<NotificationEntry> logicalChildren = this.mGroupManager.getLogicalChildren(notificationEntry.getSbn());
        if (logicalChildren != null && logicalChildren.size() > 0) {
            Iterator<NotificationEntry> it = logicalChildren.iterator();
            while (it.hasNext()) {
                if (it.next().getBelongToKeyguard()) {
                    updateKeyguardNotificationCount(notificationEntry, true);
                    notificationEntry.setBelongToKeyguard(true);
                }
            }
            return;
        }
        updateKeyguardNotificationCount(notificationEntry, true);
        notificationEntry.setBelongToKeyguard(true);
    }

    private void removeKeyguardNotificationData(NotificationEntry notificationEntry) {
        ArrayList<NotificationEntry> logicalChildren;
        if (notificationEntry.getBelongToKeyguard()) {
            updateKeyguardNotificationCount(notificationEntry, false);
            notificationEntry.setBelongToKeyguard(false);
            if (this.mGroupManager.isGroupSummary(notificationEntry)) {
                ArrayList<NotificationEntry> logicalChildren2 = this.mGroupManager.getLogicalChildren(notificationEntry.getSbn());
                if (logicalChildren2 == null) {
                    return;
                }
                for (NotificationEntry notificationEntry2 : logicalChildren2) {
                    if (notificationEntry2.getBelongToKeyguard()) {
                        updateKeyguardNotificationCount(notificationEntry2, false);
                        notificationEntry2.setBelongToKeyguard(false);
                    }
                }
                return;
            }
            NotificationEntry logicalGroupSummary = this.mGroupManager.getLogicalGroupSummary(notificationEntry);
            if (logicalGroupSummary == null || (logicalChildren = this.mGroupManager.getLogicalChildren(logicalGroupSummary.getSbn())) == null) {
                return;
            }
            Iterator<NotificationEntry> it = logicalChildren.iterator();
            while (it.hasNext()) {
                if (it.next().getBelongToKeyguard()) {
                    LogUtils.d(TAG, "Still has keyguard notification in this group, keep the summary in KeyguardNotificationData.");
                    return;
                }
            }
            updateKeyguardNotificationCount(logicalGroupSummary, false);
            logicalGroupSummary.setBelongToKeyguard(false);
        }
    }

    private void clearkeyguardNotificationData() {
        Iterator<NotificationEntry> it = this.mNotifCollection.getAllNotifs().iterator();
        while (it.hasNext()) {
            it.next().setBelongToKeyguard(false);
        }
        this.mKeyguardNotificationCount = 0;
    }

    private void updateKeyguardNotificationCount(NotificationEntry notificationEntry, boolean z) {
        if (z) {
            if (notificationEntry.getBelongToKeyguard()) {
                return;
            }
            int i = this.mKeyguardNotificationCount + 1;
            this.mKeyguardNotificationCount = i;
            if (i == 1) {
                notifyKeyguardNotificationAppear(false);
                return;
            }
            return;
        }
        if (notificationEntry.getBelongToKeyguard()) {
            int i2 = this.mKeyguardNotificationCount - 1;
            this.mKeyguardNotificationCount = i2;
            if (i2 == 0) {
                notifyKeyguardNotificationDisappear(false);
            }
        }
    }

    private void notifyScreenOn(NotificationEntry notificationEntry) {
        NotificationScreenUtils notificationScreenUtils;
        if (notificationEntry == null || notificationEntry.getSbn() == null || notificationEntry.getSbn().getNotification() == null || VivoNotificationUtils.isAutoBundleSummary(notificationEntry.getSbn()) || (notificationEntry.getSbn().getNotification().flags & 64) != 0) {
            return;
        }
        if ((ApplicationUtils.getCurrentUser() == notificationEntry.getSbn().getUserId() || (ApplicationUtils.getCurrentUser() == 0 && MultiUsersUtils.getDualUserId(this.mContext) == notificationEntry.getSbn().getUserId())) && (notificationScreenUtils = this.mNotificationScreenUtils) != null) {
            notificationScreenUtils.notiyfScreenOn(notificationEntry);
        }
    }

    public void setKeyguardCallback(StatusBarKeyguardViewManager statusBarKeyguardViewManager) {
        this.mStatusBarKeyguardViewManager = statusBarKeyguardViewManager;
    }

    public boolean isKeyguardSecure() {
        StatusBarKeyguardViewManager statusBarKeyguardViewManager = this.mStatusBarKeyguardViewManager;
        if (statusBarKeyguardViewManager != null) {
            return statusBarKeyguardViewManager.isSecure();
        }
        return false;
    }

    public boolean isKeyguardSecureForSystemUI() {
        StatusBarKeyguardViewManager statusBarKeyguardViewManager = this.mStatusBarKeyguardViewManager;
        if (statusBarKeyguardViewManager != null) {
            return statusBarKeyguardViewManager.isCurrentSecure();
        }
        return false;
    }

    public boolean isKeyguardOccluded() {
        StatusBarKeyguardViewManager statusBarKeyguardViewManager = this.mStatusBarKeyguardViewManager;
        if (statusBarKeyguardViewManager != null) {
            return statusBarKeyguardViewManager.isOccluded();
        }
        return false;
    }

    public void dismissKeyguard() {
        if (this.mStatusBarKeyguardViewManager != null) {
            LogUtils.i(TAG, "dismissKeyguard");
            this.mStatusBarKeyguardViewManager.dismiss();
        }
    }

    public int getState() {
        return this.mState;
    }

    public void enterKeyguardState() {
        enterKeyguardState(false);
    }

    private List<NotificationEntry> getVisibleNotifications() {
        List<ListEntry> vivoShadeList = this.mShadeListBuilder.getVivoShadeList();
        ArrayList arrayList = new ArrayList(vivoShadeList.size());
        for (ListEntry listEntry : vivoShadeList) {
            if (listEntry instanceof GroupEntry) {
                arrayList.add(((GroupEntry) listEntry).getSummary());
            } else if (listEntry instanceof NotificationEntry) {
                arrayList.add((NotificationEntry) listEntry);
            }
        }
        return arrayList;
    }

    public void enterKeyguardState(boolean z) {
        LogUtils.i(TAG, "enterKeyguardState, doNotChangeKeyguardBackground = " + z + ", mState = " + this.mState + ", StateUtils.getStatusBarState() = " + StateUtils.getInstance().getStatusBarState() + "; mKeyguardNotificationCount = " + this.mKeyguardNotificationCount);
        if (2 == StateUtils.getInstance().getStatusBarState()) {
            z = true;
        }
        if (this.mState != 1) {
            this.mState = 1;
            notifyKeyguardNotificationDisappear(z);
            for (NotificationEntry notificationEntry : getVisibleNotifications()) {
                if (alwaysShowedOnKeyguard(notificationEntry)) {
                    addKeyguardNotificationData(notificationEntry);
                }
                boolean zShouldShowOnKeyguard = shouldShowOnKeyguard(notificationEntry);
                StringBuilder sb = new StringBuilder();
                sb.append("belongToKeyguard = ");
                sb.append(notificationEntry.getBelongToKeyguard());
                sb.append(" , show = ");
                sb.append(zShouldShowOnKeyguard);
                sb.append(", pkg = ");
                sb.append(notificationEntry.getSbn().getPackageName());
                sb.append(", id = ");
                sb.append(notificationEntry.getSbn().getId());
                sb.append(", media = ");
                sb.append(notificationEntry.getRow() != null && notificationEntry.getRow().isMediaNotification());
                sb.append("; mKeyguardNotificationCount = ");
                sb.append(this.mKeyguardNotificationCount);
                LogUtils.d(TAG, sb.toString());
                if (zShouldShowOnKeyguard) {
                    notifyCallAndSms(notificationEntry.getSbn(), true);
                }
            }
            if (this.mKeyguardNotificationCount > 0) {
                notifyKeyguardNotificationAppear(z);
            }
            LogUtils.d(TAG, "enterKeyguardState, registerObserver");
            this.mNotifPipeline.addCollectionListener(this);
            requestNotificationEdgeUpdate();
        }
    }

    public void exitKeyguardState(boolean z) {
        LogUtils.i(TAG, "exitKeyguardState, doNotChangeKeyguardBackground = " + z + ", mState = " + this.mState);
        if (this.mState != 0) {
            this.mState = 0;
            recoverStackIfWasHide(false);
            if (!this.mStatusBar.isKeyguardViewShowing()) {
                clearKeyguardNotifications();
                clearkeyguardNotificationData();
                LogUtils.d(TAG, "exitKeyguardState, unregisterObserver");
                this.mNotifPipeline.removeCollectionListener(this);
            }
            notifyKeyguardNotificationDisappear(z);
            requestNotificationEdgeUpdate();
            NotificationScreenUtils notificationScreenUtils = this.mNotificationScreenUtils;
            if (notificationScreenUtils != null) {
                notificationScreenUtils.onKeyguardExit();
            }
            NotificationExposureHelper.onKeyguardStateChanged(this.mContext, true, new ArrayList(this.mNotifCollection.getAllNotifs()));
        }
    }

    public boolean shouldShowOnKeyguard(NotificationEntry notificationEntry) {
        return notificationEntry.getBelongToKeyguard() && allowedOnKeyguard(notificationEntry);
    }

    public boolean allowedOnKeyguard(NotificationEntry notificationEntry) {
        boolean z;
        boolean z2;
        List<NotificationEntry> attachedNotifChildren;
        if (notificationEntry == null) {
            LogUtils.d(ALLOWED_TAG, "return FALSE because of null entry!");
            return false;
        }
        if (VivoWhiteList.SHOW_CTS_NOTIFICATION_ON_KEYGUARD.contains(notificationEntry.getSbn().getPackageName())) {
            return true;
        }
        StatusBarNotification sbn = notificationEntry.getSbn();
        if (this.mIsSupportFocusMode && notificationEntry.shouldSuppressPeek()) {
            LogUtils.d(TAG, "not allowed on keyguard: suppressed by DND: " + sbn.getKey());
            return false;
        }
        if (!this.mNotificationOpsManager.isKeyguardNotificationEnabled(sbn.getPackageName(), sbn.getUid())) {
            LogUtils.d(ALLOWED_TAG, "return FALSE because of isKeyguardNotiEnable false!! key = " + notificationEntry.getKey());
            return false;
        }
        if (notificationEntry.isSummaryWithChildren() && (attachedNotifChildren = notificationEntry.getAttachedNotifChildren()) != null && attachedNotifChildren.size() != 0) {
            Iterator<NotificationEntry> it = attachedNotifChildren.iterator();
            while (it.hasNext()) {
                if (allowedOnKeyguard(it.next())) {
                    LogUtils.d(ALLOWED_TAG, "return TRUE because of Summay has visible child,key:" + notificationEntry.getKey());
                    return true;
                }
            }
        }
        if (shouldMutexAndHide(notificationEntry)) {
            LogUtils.d(ALLOWED_TAG, "return FALSE because of mutex hide:" + notificationEntry);
            return false;
        }
        if (((NotificationFilter) Dependency.get(NotificationFilter.class)).shouldFilterOut(notificationEntry)) {
            LogUtils.d(ALLOWED_TAG, " return False because of filter out! key = " + notificationEntry.getKey());
            return false;
        }
        boolean zIsChildrenMod = ChildModeManager.getInstance(this.mContext).isChildrenMod();
        Bundle bundle = sbn.getNotification().extras;
        if (bundle != null) {
            z2 = bundle.getBoolean(VivoCustomizedConfig.NotificationKeys.CUSTOM_FORCE_SHOW_NOTI_ON_KEYGUARD, false);
            z = bundle.getBoolean(VivoCustomizedConfig.NotificationKeys.CUSTOM_ONGOING_NOTI_ON_KEYGUARD, false);
        } else {
            z = false;
            z2 = false;
        }
        if (("com.vivo.agent".equals(sbn.getPackageName()) && 100 == sbn.getId()) || VivoNotificationUtils.isCarModeNotification(sbn) || VivoNotificationUtils.isFBENotification(sbn)) {
            z2 = true;
        }
        if (ApplicationUtils.isSystemApp(sbn.getPackageName()) && z2) {
            LogUtils.d(ALLOWED_TAG, "return TRUE because of forceShow!! key = " + notificationEntry.getKey());
            return true;
        }
        boolean zIsCarModeNotDisturbOn = CarModeManager.getInstance(this.mContext).isCarModeNotDisturbOn();
        boolean zShouldKeyguardIgnoreNotification = MotoModeManager.getInstance(this.mContext).shouldKeyguardIgnoreNotification();
        boolean z3 = sbn.getPackageName().equals(VivoConstants.BBK_MUSIC_PKG) && (sbn.getId() == 10001 || sbn.getId() == 10002 || sbn.getNotification().isGroupSummary());
        if (zIsChildrenMod || zIsCarModeNotDisturbOn || zShouldKeyguardIgnoreNotification || z3 || "android".equals(sbn.getPackageName()) || sbn.getNotification().internalPriority < 0) {
            LogUtils.d(ALLOWED_TAG, "return FALSE because of special notification!!  key = " + notificationEntry.getKey());
            return false;
        }
        if (notificationEntry.isIsMedia()) {
            LogUtils.d(ALLOWED_TAG, " return TRUE because of mediaNotification. key = " + notificationEntry.getKey());
            return true;
        }
        int classifyImportance = -1;
        try {
            classifyImportance = sbn.getClassifyImportance();
        } catch (Exception e) {
            LogUtils.e(TAG, "isKeyguardNotificationEnabled, getClassifyImportance: ", e);
        }
        if (classifyImportance == 1 || classifyImportance == 2) {
            LogUtils.d(ALLOWED_TAG, "return FALSE because of isLowPriority by Smart Notifications!!  key = " + notificationEntry.getKey());
            return false;
        }
        boolean z4 = notificationEntry.getImportance() < 3;
        boolean zConsiderAsOngoing = VivoNotificationUtils.considerAsOngoing(sbn);
        boolean z5 = z && ApplicationUtils.isSystemApp(sbn.getPackageName());
        LogUtils.d(ALLOWED_TAG, " isOngoing = " + zConsiderAsOngoing + " isLowPriority = " + z4 + " isExport = " + SystemPropertiesFacade.isExport() + "  customShowOngoing = " + z5 + "  GroupSummary = " + this.mGroupManager.isGroupSummary(notificationEntry) + " entry key = " + notificationEntry.getKey());
        if ((!zConsiderAsOngoing && !z4) || ((zConsiderAsOngoing && SystemPropertiesFacade.isExport()) || (zConsiderAsOngoing && (z5 || this.mGroupManager.isGroupSummary(notificationEntry))))) {
            return true;
        }
        if (z4) {
        }
        return false;
    }

    private boolean isAppLocked(StatusBarNotification statusBarNotification) {
        if (statusBarNotification == null) {
            return false;
        }
        return this.mNotificationOpsManager.isAppLockedByIManager(statusBarNotification.getPackageName(), this.mNotificationOpsManager.checkDual(statusBarNotification.getUid()));
    }

    private boolean isKeyguardNotificationEnabled(StatusBarNotification statusBarNotification) {
        if (statusBarNotification == null) {
            return false;
        }
        return this.mNotificationOpsManager.isKeyguardNotificationEnabled(statusBarNotification.getPackageName(), statusBarNotification.getUid());
    }

    private boolean isKeyguardNotificationDetailEnabled(StatusBarNotification statusBarNotification) {
        if (statusBarNotification == null) {
            return false;
        }
        return this.mNotificationOpsManager.isKeyguardNotificationDetailsEnabled(statusBarNotification.getPackageName(), statusBarNotification.getUid());
    }

    public void exitKeyguardState() {
        exitKeyguardState(false);
    }

    private void notifyKeyguardNotificationAppear(boolean z) {
        Iterator<Callback> it = this.mCallbacks.iterator();
        while (it.hasNext()) {
            it.next().onKeyguardNotificationAppear(z);
        }
    }

    private void notifyKeyguardNotificationDisappear(boolean z) {
        Iterator<Callback> it = this.mCallbacks.iterator();
        while (it.hasNext()) {
            it.next().onKeyguardNotificationDisappear(z);
        }
    }

    private void notifyMissedCallNotificationChanged(boolean z) {
        Iterator<Callback> it = this.mCallbacks.iterator();
        while (it.hasNext()) {
            it.next().onMissedCallNotificationChanged(z);
        }
    }

    private void notifyUnreadSmsNotificationChanged(boolean z) {
        Iterator<Callback> it = this.mCallbacks.iterator();
        while (it.hasNext()) {
            it.next().onUnreadSmsNotificationChanged(z);
        }
    }

    private boolean isMissedCall(StatusBarNotification statusBarNotification) {
        if ("com.android.phone".equals(statusBarNotification.getPackageName())) {
            return statusBarNotification.getId() == 10001 || isSmartAlert(statusBarNotification);
        }
        return false;
    }

    private boolean isUnreadSms(StatusBarNotification statusBarNotification) {
        if (isSmsPkg(statusBarNotification.getPackageName())) {
            return statusBarNotification.getId() == 10001 || isSmartAlert(statusBarNotification);
        }
        return false;
    }

    public static boolean isSmsPkg(String str) {
        return VivoConstants.MMS_SERVICE_PACKAGE.equals(str) || VivoConstants.MMS_PACKAGE.equals(str);
    }

    private boolean isSmartAlert(StatusBarNotification statusBarNotification) {
        Bundle bundle = statusBarNotification.getNotification().extras;
        return bundle != null && bundle.getBoolean("smart_alert", false);
    }

    public void setTopPadding(int i) {
        this.mTopCurrentPadding = i;
        NotificationStackScrollLayout notificationStackScrollLayout = (NotificationStackScrollLayout) this.mStatusBar.getNotificationScrollLayout();
        if (notificationStackScrollLayout == null || !StateUtils.getInstance().isKeyguardShowingWithoutShade()) {
            return;
        }
        notificationStackScrollLayout.updateTopPadding(0.0f, false);
    }

    public int getTopPadding() {
        return VivoDeviceUtils.isFoldKeyguardOS2(this.mContext) ? this.mStatusBar.getOriginKeyguardBottomViewTop() : this.mTopCurrentPadding;
    }

    private void recoverStackIfWasHide(boolean z) {
        NotificationStackScrollLayout notificationStackScrollLayout = (NotificationStackScrollLayout) this.mStatusBar.getNotificationScrollLayout();
        if (notificationStackScrollLayout != null) {
            if (z) {
                ObjectAnimator objectAnimator = this.mStackAlphaAnimation;
                if (objectAnimator != null && objectAnimator.isRunning()) {
                    this.mStackAlphaAnimation.cancel();
                }
                ObjectAnimator objectAnimatorOfFloat = ObjectAnimator.ofFloat(notificationStackScrollLayout, "alpha", 1.0f);
                this.mStackAlphaAnimation = objectAnimatorOfFloat;
                objectAnimatorOfFloat.setDuration(300L);
                this.mStackAlphaAnimation.start();
            } else {
                notificationStackScrollLayout.setAlpha(1.0f);
            }
            if (notificationStackScrollLayout.getVisibility() != 0) {
                notificationStackScrollLayout.setVisibility(0);
                notificationStackScrollLayout.setAlpha(1.0f);
            }
        }
    }

    private void requestNotificationEdgeUpdate() {
        if (this.mStatusBar.getPanelController() != null) {
            this.mStatusBar.getPanelController().updateScrollerTopPadding();
        }
    }

    public int getKeyguardNotificationCount() {
        return this.mKeyguardNotificationCount;
    }

    public void onKeyguardNotificationClick(Runnable runnable) {
        if (this.mCallbacks.size() != 0) {
            Iterator<Callback> it = this.mCallbacks.iterator();
            while (it.hasNext()) {
                it.next().onKeyguardNotificationClick(runnable);
            }
        }
    }

    public void registerCallback(Callback callback) {
        this.mCallbacks.add(callback);
    }

    public void unregisterCallback(Callback callback) {
        this.mCallbacks.remove(callback);
    }

    public void onWidgetHeightChange(int i) {
        this.mNotificationMarginTop = i;
        setTopPadding(i);
        notifyTopPaddingChanged(i - this.mMusicWidgetHeight);
    }

    public void setNotificationScreenUtils(NotificationScreenUtils notificationScreenUtils) {
        LogUtils.i(TAG, "setNotificationScreenUtils " + notificationScreenUtils);
        this.mNotificationScreenUtils = notificationScreenUtils;
    }

    public boolean isSuppressedSummary(NotificationEntry notificationEntry) {
        return this.mGroupManager.isSummaryOfSuppressedGroup(notificationEntry.getSbn()) && !notificationEntry.getRow().isRemoved();
    }

    public boolean isKeyguardNotification(NotificationEntry notificationEntry) {
        return isInKeyguardNotificationData(notificationEntry.getKey());
    }

    private boolean isInKeyguardNotificationData(String str) {
        List<NotificationEntry> visibleNotifications = getVisibleNotifications();
        if (visibleNotifications == null || visibleNotifications.size() <= 0) {
            return false;
        }
        Iterator<NotificationEntry> it = visibleNotifications.iterator();
        while (it.hasNext()) {
            if (str.equals(it.next().getKey())) {
                return true;
            }
        }
        return false;
    }

    public ArrayList<StatusBarNotification> getKeyguardNotifications() {
        StringBuilder sb = new StringBuilder();
        sb.append("Jovi --> getKeyguardNotifications: mKeyguardNotifications.size() = ");
        ArrayMap<String, StatusBarNotification> arrayMap = this.mKeyguardNotifications;
        sb.append(arrayMap == null ? -1 : arrayMap.size());
        sb.append("  mCurClassInit = ");
        sb.append(sCurClassInit);
        LogUtils.d(TAG, sb.toString());
        if (this.mKeyguardNotifications == null || !sCurClassInit) {
            return null;
        }
        return new ArrayList<>(this.mKeyguardNotifications.values());
    }

    public ArrayList<StatusBarNotification> getStatusBarNotifications() {
        LogUtils.d(TAG, " getStatusBarNotifications  mCurClassInit = " + sCurClassInit);
        if (!sCurClassInit) {
            return null;
        }
        List<NotificationEntry> visibleNotifications = getVisibleNotifications();
        ArrayList<StatusBarNotification> arrayList = new ArrayList<>(visibleNotifications.size());
        Iterator<NotificationEntry> it = visibleNotifications.iterator();
        while (it.hasNext()) {
            arrayList.add(it.next().getSbn());
        }
        return arrayList;
    }

    @Override // com.vivo.common.monitor.VivoSystemUIUpdateMonitorCallback
    public void onPackageAdded(String str, boolean z) {
        if ("com.vivo.agent".equals(str)) {
            LogUtils.d(TAG, "Jovi --> onPackageAdded: com.vivo.agent");
            onJoviInstalledChanged(true);
        }
    }

    @Override // com.vivo.common.monitor.VivoSystemUIUpdateMonitorCallback
    public void onPackageRemoved(String str, boolean z) {
        if ("com.vivo.agent".equals(str)) {
            LogUtils.d(TAG, "Jovi --> onPackageRemoved: com.vivo.agent");
            onJoviInstalledChanged(false);
        }
    }

    public boolean isJoviCallbackEnabled() {
        return this.mKeyguardNotifications != null;
    }

    private void onJoviInstalledChanged(boolean z) {
        clearKeyguardNotifications();
        if (z) {
            if (this.mKeyguardNotifications == null) {
                this.mKeyguardNotifications = new ArrayMap<>();
                return;
            }
            return;
        }
        this.mKeyguardNotifications = null;
    }

    private void addKeyguardNotification(StatusBarNotification statusBarNotification) {
        if (this.mKeyguardNotifications == null || statusBarNotification == null) {
            return;
        }
        LogUtils.d(TAG, "Jovi --> addKeyguardNotification  key: " + statusBarNotification.getKey() + " | mKeyguardNotifications.size: " + this.mKeyguardNotifications.size());
        this.mKeyguardNotifications.put(statusBarNotification.getKey(), statusBarNotification);
    }

    public void removeKeyguardNotification(StatusBarNotification statusBarNotification) {
        if (this.mKeyguardNotifications == null || statusBarNotification == null) {
            return;
        }
        LogUtils.d(TAG, "Jovi --> removeKeyguardNotification  key: " + statusBarNotification.getKey() + " | mKeyguardNotifications.size: " + this.mKeyguardNotifications.size());
        this.mKeyguardNotifications.remove(statusBarNotification.getKey());
    }

    public void clearKeyguardNotifications() {
        LogUtils.d(TAG, "Jovi --> clearKeyguardNotifications");
        ArrayMap<String, StatusBarNotification> arrayMap = this.mKeyguardNotifications;
        if (arrayMap != null) {
            arrayMap.clear();
        }
    }

    public boolean isStayingKeyguard() {
        return this.mStayingKeyguard;
    }

    public void setStayingKeyguard(boolean z) {
        this.mStayingKeyguard = z;
        if (z) {
            this.mNotifPipeline.getInternalNotifUpdater("keyguardNotificationManager").reBuildList("faceUnlockSuccess");
        }
    }

    public void onMusicPanelVisibleChanged(boolean z) {
        LogUtils.d(TAG, "onMusicPanelVisibleChanged -- musicRowVisible = " + z + " ; mMusicWidgetHeight: " + this.mMusicWidgetHeight);
        this.mMusicWidgetHeight = z ? this.mMusicWidgetHeight : 0;
        setTopPadding(this.mNotificationMarginTop);
        notifyKeyguardMusicShowing(z);
        notifyTopPaddingChanged(this.mNotificationMarginTop - this.mMusicWidgetHeight);
    }

    private boolean shouldMutexAndHide(NotificationEntry notificationEntry) {
        ArrayList<String> playingPackage = ((KeyguardPlugin) PluginManager.INSTANCE.getPlugin(KeyguardPlugin.class)).getPlayingPackage();
        if (playingPackage == null) {
            return false;
        }
        for (int i = 0; i < playingPackage.size(); i++) {
            if (notificationEntry.getSbn().getPackageName().equals(playingPackage.get(i))) {
                ExpandableNotificationRow row = notificationEntry.getRow();
                if (row == null) {
                    return false;
                }
                if (row.isMediaNotification()) {
                    return true;
                }
                if (row.isCustomNotification() && VivoNotificationUtils.considerAsOngoing(notificationEntry.getSbn())) {
                    return true;
                }
            }
        }
        return false;
    }

    public void onWidgetVisibleChange(boolean z, int i) {
        this.mIsWidgetVisible = z;
        this.mWidgetViewHeight = i;
        IKeyguardMusicWidgetCallback iKeyguardMusicWidgetCallback = this.mMusicWidgetCallback;
        if (iKeyguardMusicWidgetCallback != null) {
            iKeyguardMusicWidgetCallback.onStepWeatherTimeWidgetVisibleChange(z);
        }
    }

    public int getWidgetViewHeight() {
        if (this.mIsWidgetVisible) {
            return this.mWidgetViewHeight;
        }
        return 0;
    }

    public void onMultiUserContainerSizeChanged(int i) {
        if (((KeyguardUpdateMonitor) Dependency.get(KeyguardUpdateMonitor.class)).isLargeAreaFinger() || !KeyguardThemeUtils.isDefaultThemeId()) {
            setTopPadding(this.mNotificationMarginTop);
        } else {
            setTopPadding((this.mNotificationMarginTop + i) - this.singleHeight);
        }
        notifyTopPaddingChanged(this.mNotificationMarginTop - this.mMusicWidgetHeight);
        if (this.mMusicWidgetCallback == null || ((KeyguardUpdateMonitor) Dependency.get(KeyguardUpdateMonitor.class)).isLargeAreaFinger()) {
            return;
        }
        this.mMusicWidgetCallback.onMultiUserContainerVisibleChange();
    }

    private void notifyKeyguardMusicShowing(boolean z) {
        Iterator<Callback> it = this.mCallbacks.iterator();
        while (it.hasNext()) {
            it.next().onKeyguardMusicVisibleChanged(z);
        }
    }

    private void notifyTopPaddingChanged(int i) {
        Iterator<Callback> it = this.mCallbacks.iterator();
        while (it.hasNext()) {
            it.next().onNotificationTopPadingChanged(i);
        }
    }

    public void setKeyguardMusicWidgetCallback(IKeyguardMusicWidgetCallback iKeyguardMusicWidgetCallback) {
        this.mMusicWidgetCallback = iKeyguardMusicWidgetCallback;
    }
}
