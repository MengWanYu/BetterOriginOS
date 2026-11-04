package com.android.systemui.statusbar.notification.stack;

import android.content.res.Resources;
import android.util.MathUtils;
import android.view.View;
import com.android.systemui.Dependency;
import com.android.systemui.R;
import com.android.systemui.dagger.SysUISingleton;
import com.android.systemui.statusbar.NotificationShelf;
import com.android.systemui.statusbar.notification.NotificationSectionsFeatureManager;
import com.android.systemui.statusbar.notification.row.ExpandableNotificationRow;
import com.android.systemui.statusbar.notification.row.ExpandableView;
import com.android.systemui.statusbar.policy.BatteryController;
import com.vivo.common.monitor.VivoSystemUIUpdateMonitor;
import com.vivo.systemui.statusbar.utils.VivoDeviceUtils;
import java.util.HashSet;
import javax.inject.Inject;

/* JADX INFO: loaded from: classes2.dex */
@SysUISingleton
public class NotificationRoundnessManager {
    private HashSet<ExpandableView> mAnimatedChildren;
    private float mAppearFraction;
    private boolean mExpanded;
    private final ExpandableView[] mFirstInSectionViews;
    private boolean mHasShelf;
    private boolean mIsClearAllInProgress;
    private final ExpandableView[] mLastInSectionViews;
    private NotificationListContainer mListContainer;
    private boolean mRoundForPulsingViews;
    private Runnable mRoundingChangedCallback;
    private final ExpandableView[] mTmpFirstInSectionViews;
    private final ExpandableView[] mTmpLastInSectionViews;
    private ExpandableNotificationRow mTrackedHeadsUp;
    private ExpandableView mSwipedView = null;
    private ExpandableView mViewBeforeSwipedView = null;
    private ExpandableView mViewAfterSwipedView = null;
    private boolean ENABLE_SWIPE_ROUNDNESS = false;

    public void setNotiListContainer(NotificationListContainer notificationListContainer) {
        this.mListContainer = notificationListContainer;
    }

    @Inject
    NotificationRoundnessManager(NotificationSectionsFeatureManager notificationSectionsFeatureManager) {
        int numberOfBuckets = notificationSectionsFeatureManager.getNumberOfBuckets();
        this.mFirstInSectionViews = new ExpandableView[numberOfBuckets];
        this.mLastInSectionViews = new ExpandableView[numberOfBuckets];
        this.mTmpFirstInSectionViews = new ExpandableView[numberOfBuckets];
        this.mTmpLastInSectionViews = new ExpandableView[numberOfBuckets];
    }

    public void updateView(ExpandableView expandableView, boolean z) {
        if (updateViewWithoutCallback(expandableView, z)) {
            this.mRoundingChangedCallback.run();
        }
    }

    public boolean isViewAffectedBySwipe(ExpandableView expandableView) {
        return expandableView != null && (expandableView == this.mSwipedView || expandableView == this.mViewBeforeSwipedView || expandableView == this.mViewAfterSwipedView);
    }

    boolean updateViewWithoutCallback(ExpandableView expandableView, boolean z) {
        if (expandableView == null || expandableView == this.mViewBeforeSwipedView || expandableView == this.mViewAfterSwipedView) {
            return false;
        }
        if (((BatteryController) Dependency.get(BatteryController.class)).isVivoPowerSave() || VivoSystemUIUpdateMonitor.getInstance(expandableView.getContext()).getSpsMode()) {
            z = false;
        }
        float roundnessFraction = getRoundnessFraction(expandableView, true);
        float roundnessFraction2 = getRoundnessFraction(expandableView, false);
        boolean topRoundness = expandableView.setTopRoundness(roundnessFraction, z);
        boolean bottomRoundness = expandableView.setBottomRoundness(roundnessFraction2, z);
        boolean zIsFirstInStack = isFirstInStack(expandableView);
        boolean zIsLastInStack = isLastInStack(expandableView);
        boolean z2 = (zIsFirstInStack == expandableView.isFirstInStack() && zIsLastInStack == expandableView.isLastInStack()) ? false : true;
        expandableView.setFirstInStack(zIsFirstInStack);
        expandableView.setLastInStack(zIsLastInStack);
        return z2 || topRoundness || bottomRoundness;
    }

    private boolean isFirstInSection(ExpandableView expandableView) {
        int i = 0;
        while (true) {
            ExpandableView[] expandableViewArr = this.mFirstInSectionViews;
            if (i >= expandableViewArr.length) {
                return false;
            }
            if (expandableView == expandableViewArr[i]) {
                return true;
            }
            i++;
        }
    }

    private boolean isLastInSection(ExpandableView expandableView) {
        for (int length = this.mLastInSectionViews.length - 1; length >= 0; length--) {
            if (expandableView == this.mLastInSectionViews[length]) {
                return true;
            }
        }
        return false;
    }

    void setViewsAffectedBySwipe(ExpandableView expandableView, ExpandableView expandableView2, ExpandableView expandableView3) {
        if (this.ENABLE_SWIPE_ROUNDNESS) {
            ExpandableView expandableView4 = this.mViewBeforeSwipedView;
            this.mViewBeforeSwipedView = expandableView;
            if (expandableView4 != null) {
                expandableView4.setBottomRoundness(getRoundnessFraction(expandableView4, false), true);
            }
            if (expandableView != null) {
                expandableView.setBottomRoundness(1.0f, true);
            }
            ExpandableView expandableView5 = this.mSwipedView;
            this.mSwipedView = expandableView2;
            if (expandableView5 != null) {
                float roundnessFraction = getRoundnessFraction(expandableView5, false);
                expandableView5.setTopRoundness(getRoundnessFraction(expandableView5, true), true);
                expandableView5.setBottomRoundness(roundnessFraction, true);
            }
            if (expandableView2 != null) {
                expandableView2.setTopRoundness(1.0f, true);
                expandableView2.setBottomRoundness(1.0f, true);
            }
            ExpandableView expandableView6 = this.mViewAfterSwipedView;
            this.mViewAfterSwipedView = expandableView3;
            if (expandableView6 != null) {
                expandableView6.setTopRoundness(getRoundnessFraction(expandableView6, true), true);
            }
            if (expandableView3 != null) {
                expandableView3.setTopRoundness(1.0f, true);
            }
        }
    }

    private boolean isFirstInStack(ExpandableView expandableView) {
        NotificationListContainer notificationListContainer = this.mListContainer;
        if (notificationListContainer != null) {
            int containerChildCount = notificationListContainer.getContainerChildCount();
            for (int i = 0; i < containerChildCount; i++) {
                View containerChildAt = this.mListContainer.getContainerChildAt(i);
                if (containerChildAt instanceof ExpandableNotificationRow) {
                    return expandableView == containerChildAt;
                }
            }
        }
        return false;
    }

    private boolean isLastInStack(ExpandableView expandableView) {
        NotificationListContainer notificationListContainer = this.mListContainer;
        if (notificationListContainer != null) {
            for (int containerChildCount = notificationListContainer.getContainerChildCount() - 1; containerChildCount >= 0; containerChildCount--) {
                View containerChildAt = this.mListContainer.getContainerChildAt(containerChildCount);
                if (containerChildAt instanceof ExpandableNotificationRow) {
                    return expandableView == containerChildAt;
                }
            }
        }
        return false;
    }

    void setClearAllInProgress(boolean z) {
        this.mIsClearAllInProgress = z;
    }

    private float getRoundnessFraction(ExpandableView expandableView, boolean z) {
        if (expandableView == null || VivoDeviceUtils.isFoldKeyguardOS2(expandableView.getContext())) {
            return 0.0f;
        }
        if (expandableView == this.mViewBeforeSwipedView || expandableView == this.mSwipedView || expandableView == this.mViewAfterSwipedView || (((expandableView instanceof ExpandableNotificationRow) && ((ExpandableNotificationRow) expandableView).canViewBeCleared() && this.mIsClearAllInProgress) || expandableView.isPinned() || (expandableView.isHeadsUpAnimatingAway() && !this.mExpanded))) {
            return 1.0f;
        }
        if (isFirstInStack(expandableView) && z) {
            return 1.0f;
        }
        if (isLastInStack(expandableView) && !z) {
            return this.mHasShelf ? 0.0f : 1.0f;
        }
        if (expandableView instanceof NotificationShelf) {
            return z ? 0.0f : 1.0f;
        }
        if (expandableView == this.mTrackedHeadsUp) {
            return MathUtils.saturate(1.0f - this.mAppearFraction);
        }
        if (expandableView.showingPulsing() && this.mRoundForPulsingViews) {
            return 1.0f;
        }
        Resources resources = expandableView.getResources();
        return resources.getDimension(R.dimen.notification_corner_radius_small) / resources.getDimension(R.dimen.notification_corner_radius);
    }

    public void setExpanded(float f, float f2) {
        this.mExpanded = f != 0.0f;
        this.mAppearFraction = f2;
        ExpandableNotificationRow expandableNotificationRow = this.mTrackedHeadsUp;
        if (expandableNotificationRow != null) {
            updateView(expandableNotificationRow, false);
        }
    }

    public void updateRoundedChildren(NotificationSection[] notificationSectionArr) {
        for (int i = 0; i < notificationSectionArr.length; i++) {
            ExpandableView[] expandableViewArr = this.mTmpFirstInSectionViews;
            ExpandableView[] expandableViewArr2 = this.mFirstInSectionViews;
            expandableViewArr[i] = expandableViewArr2[i];
            this.mTmpLastInSectionViews[i] = this.mLastInSectionViews[i];
            expandableViewArr2[i] = notificationSectionArr[i].getFirstVisibleChild();
            this.mLastInSectionViews[i] = notificationSectionArr[i].getLastVisibleChild();
        }
        if (handleAddedNewViews(notificationSectionArr, this.mTmpLastInSectionViews, false) || (((handleRemovedOldViews(notificationSectionArr, this.mTmpFirstInSectionViews, true) | false) | handleRemovedOldViews(notificationSectionArr, this.mTmpLastInSectionViews, false)) | handleAddedNewViews(notificationSectionArr, this.mTmpFirstInSectionViews, true))) {
            this.mRoundingChangedCallback.run();
        }
    }

    public void updateNotificationRoundness() {
        int containerChildCount = this.mListContainer.getContainerChildCount();
        boolean zUpdateViewWithoutCallback = false;
        this.mHasShelf = false;
        for (int i = containerChildCount - 1; i >= 0; i--) {
            View containerChildAt = this.mListContainer.getContainerChildAt(i);
            if (containerChildAt instanceof ExpandableView) {
                if ((containerChildAt instanceof NotificationShelf) && containerChildAt.getVisibility() == 0) {
                    this.mHasShelf = true;
                }
                ExpandableView expandableView = (ExpandableView) containerChildAt;
                if (!expandableView.isRemoved()) {
                    zUpdateViewWithoutCallback |= updateViewWithoutCallback(expandableView, expandableView.isShown());
                }
            }
        }
        if (zUpdateViewWithoutCallback) {
            this.mRoundingChangedCallback.run();
        }
    }

    private boolean handleRemovedOldViews(NotificationSection[] notificationSectionArr, ExpandableView[] expandableViewArr, boolean z) {
        boolean z2;
        boolean z3;
        ExpandableView lastVisibleChild;
        boolean z4 = false;
        for (ExpandableView expandableView : expandableViewArr) {
            if (expandableView != null) {
                int length = notificationSectionArr.length;
                int i = 0;
                while (true) {
                    if (i >= length) {
                        z2 = false;
                        break;
                    }
                    NotificationSection notificationSection = notificationSectionArr[i];
                    if (z) {
                        lastVisibleChild = notificationSection.getFirstVisibleChild();
                    } else {
                        lastVisibleChild = notificationSection.getLastVisibleChild();
                    }
                    if (lastVisibleChild != expandableView) {
                        i++;
                    } else if (expandableView.isFirstInStack() == isFirstInStack(expandableView) && expandableView.isLastInStack() == isLastInStack(expandableView)) {
                        z3 = false;
                        z2 = true;
                    } else {
                        z2 = true;
                    }
                }
                z3 = z2;
                if (!z2 || z3) {
                    if (!expandableView.isRemoved()) {
                        updateViewWithoutCallback(expandableView, expandableView.isShown());
                    }
                    z4 = true;
                }
            }
        }
        return z4;
    }

    private boolean handleAddedNewViews(NotificationSection[] notificationSectionArr, ExpandableView[] expandableViewArr, boolean z) {
        boolean z2;
        boolean z3 = false;
        for (NotificationSection notificationSection : notificationSectionArr) {
            ExpandableView firstVisibleChild = z ? notificationSection.getFirstVisibleChild() : notificationSection.getLastVisibleChild();
            if (firstVisibleChild != null) {
                int length = expandableViewArr.length;
                int i = 0;
                while (true) {
                    if (i >= length) {
                        z2 = false;
                        break;
                    }
                    if (expandableViewArr[i] == firstVisibleChild) {
                        z2 = true;
                        break;
                    }
                    i++;
                }
                if (!z2) {
                    updateViewWithoutCallback(firstVisibleChild, firstVisibleChild.isShown() && !this.mAnimatedChildren.contains(firstVisibleChild));
                    z3 = true;
                }
            }
        }
        return z3;
    }

    public void setAnimatedChildren(HashSet<ExpandableView> hashSet) {
        this.mAnimatedChildren = hashSet;
    }

    public void setOnRoundingChangedCallback(Runnable runnable) {
        this.mRoundingChangedCallback = runnable;
    }

    public void setTrackingHeadsUp(ExpandableNotificationRow expandableNotificationRow) {
        ExpandableNotificationRow expandableNotificationRow2 = this.mTrackedHeadsUp;
        this.mTrackedHeadsUp = expandableNotificationRow;
        if (expandableNotificationRow2 != null) {
            updateView(expandableNotificationRow2, true);
        }
    }

    public void setShouldRoundPulsingViews(boolean z) {
        this.mRoundForPulsingViews = z;
    }
}
