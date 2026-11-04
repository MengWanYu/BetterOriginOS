package com.android.systemui.statusbar;

import android.content.Context;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.util.IndentingPrintWriter;
import android.util.MathUtils;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.animation.Interpolator;
import android.view.animation.PathInterpolator;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.TextView;
import androidx.core.view.AccessibilityDelegateCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;
import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.policy.SystemBarUtils;
import com.android.keyguard.BouncerPanelExpansionCalculator;
import com.android.systemui.Dependency;
import com.android.systemui.animation.ShadeInterpolation;
import com.android.systemui.res.R;
import com.android.systemui.statusbar.notification.ColorUpdateLogger;
import com.android.systemui.statusbar.notification.NotificationUtils;
import com.android.systemui.statusbar.notification.SourceType;
import com.android.systemui.statusbar.notification.collection.coordinator.VivoKeyguardCoordinator;
import com.android.systemui.statusbar.notification.row.ActivatableNotificationView;
import com.android.systemui.statusbar.notification.row.ExpandableNotificationRow;
import com.android.systemui.statusbar.notification.row.ExpandableView;
import com.android.systemui.statusbar.notification.shared.NotificationIconContainerRefactor;
import com.android.systemui.statusbar.notification.stack.AmbientState;
import com.android.systemui.statusbar.notification.stack.AnimationProperties;
import com.android.systemui.statusbar.notification.stack.ExpandableViewState;
import com.android.systemui.statusbar.notification.stack.NotificationRoundnessManager;
import com.android.systemui.statusbar.notification.stack.NotificationStackScrollLayout;
import com.android.systemui.statusbar.notification.stack.NotificationStackScrollLayoutController;
import com.android.systemui.statusbar.notification.stack.StackScrollAlgorithm;
import com.android.systemui.statusbar.notification.stack.ViewState;
import com.android.systemui.statusbar.phone.CentralSurfaces;
import com.android.systemui.statusbar.phone.NotificationIconContainer;
import com.android.systemui.util.ColorUtilKt;
import com.android.systemui.util.DumpUtilsKt;
import com.vivo.common.trackevent.misc.NotificationExposureHelper;
import com.vivo.common.trackevent.statusbar.VivoStatusBarEventTrackerImpl;
import com.vivo.common.utils.LogUtils;
import com.vivo.systemui.statusbar.notification.KeyguardFullShadeList;
import com.vivo.systemui.statusbar.notification.NotificationIconRedrawHelper;
import com.vivo.systemui.statusbar.phone.VivoNotificationIconContainer;
import com.vivo.systemui.statusbar.utils.FoldedStateMonitorUtils;
import com.vivo.systemui.statusbar.utils.FontSizeLimitUtils;
import com.vivo.systemui.statusbar.utils.VivoDeviceUtils;
import java.io.PrintWriter;

/* JADX INFO: loaded from: classes5.dex */
public class NotificationShelf extends ActivatableNotificationView {
    private static final String TAG = "NotificationShelf";
    private float mActualWidth;
    private AmbientState mAmbientState;
    private boolean mAnimationsEnabled;
    private boolean mCanInteract;
    private boolean mCanModifyColorOfNotifications;
    private final Rect mClipRect;
    private float mCornerAnimationDistance;
    private TextView mDismissAllNotification;
    private boolean mEnableNotificationClipping;
    private int mGapBetweenIconAndDismiss;
    private boolean mHasItemsInStableShelf;
    private boolean mHideBackground;
    private NotificationStackScrollLayout mHostLayout;
    private NotificationStackScrollLayoutController mHostLayoutController;
    private int mIndexOfFirstViewInShelf;
    private boolean mInteractive;
    private boolean mLastHideState;
    private int mMaxIconsOnLockscreen;
    private int mNotGoneIndex;
    private int mPaddingBetweenElements;
    private NotificationRoundnessManager mRoundnessManager;
    private int mScrollFastThreshold;
    private boolean mShelfDisappearAnimatorRun;
    private VivoNotificationIconContainer mShelfIcons;
    private boolean mShowNotificationShelf;
    private int mStatusBarHeight;
    private static final int TAG_CONTINUOUS_CLIPPING = R.id.continuous_clipping_tag;
    private static final Interpolator ICON_ALPHA_INTERPOLATOR = new PathInterpolator(0.6f, 0.0f, 0.6f, 0.0f);
    private static final SourceType BASE_VALUE = SourceType.from("BaseValue");
    private static final SourceType SHELF_SCROLL = SourceType.from("ShelfScroll");

    @Override // com.android.systemui.statusbar.notification.row.ExpandableView
    public boolean hasNoContentHeight() {
        return true;
    }

    @Override // com.android.systemui.statusbar.notification.row.ExpandableView, android.view.View
    public boolean hasOverlappingRendering() {
        return false;
    }

    @VisibleForTesting
    public boolean isXInView(float f, float f2, float f3, float f4) {
        return f3 - f2 <= f && f < f4 + f2;
    }

    @VisibleForTesting
    public boolean isYInView(float f, float f2, float f3, float f4) {
        return f3 - f2 <= f && f < f4 + f2;
    }

    @Override // com.android.systemui.statusbar.notification.row.ExpandableView
    public boolean needsClippingToShelf() {
        return false;
    }

    public void setDismissVisible(boolean z) {
        this.mDismissAllNotification.setVisibility(8);
        int dimensionPixelOffset = getResources().getDimensionPixelOffset(R.dimen.shelf_icon_container_padding);
        if (z) {
            setHideBackground(false);
            dimensionPixelOffset = this.mGapBetweenIconAndDismiss + this.mDismissAllNotification.getWidth();
        }
        VivoNotificationIconContainer vivoNotificationIconContainer = this.mShelfIcons;
        vivoNotificationIconContainer.setPadding(vivoNotificationIconContainer.getPaddingStart(), 0, dimensionPixelOffset, 0);
    }

    public void setClearNotificationListener(View.OnClickListener onClickListener) {
        TextView textView = this.mDismissAllNotification;
        if (textView != null) {
            textView.setOnClickListener(onClickListener);
        }
    }

    public NotificationShelf(Context context, AttributeSet attributeSet) {
        super(context, attributeSet);
        this.mAnimationsEnabled = true;
        this.mClipRect = new Rect();
        this.mIndexOfFirstViewInShelf = -1;
        this.mActualWidth = -1.0f;
    }

    @VisibleForTesting
    public NotificationShelf(Context context, AttributeSet attributeSet, boolean z) {
        super(context, attributeSet);
        this.mAnimationsEnabled = true;
        this.mClipRect = new Rect();
        this.mIndexOfFirstViewInShelf = -1;
        this.mActualWidth = -1.0f;
        this.mShowNotificationShelf = z;
    }

    @Override // com.android.systemui.statusbar.notification.row.ActivatableNotificationView, android.view.View
    @VisibleForTesting
    public void onFinishInflate() {
        super.onFinishInflate();
        VivoNotificationIconContainer vivoNotificationIconContainer = (VivoNotificationIconContainer) findViewById(R.id.content);
        this.mShelfIcons = vivoNotificationIconContainer;
        vivoNotificationIconContainer.setClipChildren(false);
        this.mShelfIcons.setClipToPadding(false);
        setClipToActualHeight(false);
        setClipChildren(false);
        setClipToPadding(false);
        this.mShelfIcons.setIsStaticLayout(false);
        SourceType sourceType = BASE_VALUE;
        requestRoundness(1.0f, 1.0f, sourceType, false);
        requestTopRoundness(1.0f, sourceType, false);
        TextView textView = (TextView) findViewById(R.id.clear_all_notification);
        this.mDismissAllNotification = textView;
        textView.setText(getResources().getString(R.string.vivo_clear_all_notifications_text));
        this.mDismissAllNotification.setTypeface(Typeface.DEFAULT_BOLD);
        updateResources();
        shouldLimitFontSize(getContext().getResources().getConfiguration());
        setOnClickListener(new View.OnClickListener() { // from class: com.android.systemui.statusbar.NotificationShelf$$ExternalSyntheticLambda0
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                this.f$0.lambda$onFinishInflate$0(view);
            }
        });
        ViewCompat.setAccessibilityDelegate(this.mDismissAllNotification, new AccessibilityDelegateCompat() { // from class: com.android.systemui.statusbar.NotificationShelf.1
            @Override // androidx.core.view.AccessibilityDelegateCompat
            public void onInitializeAccessibilityNodeInfo(View view, AccessibilityNodeInfoCompat accessibilityNodeInfoCompat) {
                super.onInitializeAccessibilityNodeInfo(view, accessibilityNodeInfoCompat);
                accessibilityNodeInfoCompat.setClassName(Button.class.getName());
            }
        });
        this.mBackgroundNormal.setParentTag("[StackScroller:Shelf]");
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$onFinishInflate$0(View view) {
        KeyguardFullShadeList keyguardFullShadeList = ((VivoKeyguardCoordinator) Dependency.get(VivoKeyguardCoordinator.class)).getKeyguardFullShadeList();
        if (keyguardFullShadeList == null || keyguardFullShadeList.getCurrentState() == 2) {
            return;
        }
        ((CentralSurfaces) Dependency.get(CentralSurfaces.class)).userActivity();
        keyguardFullShadeList.enterFullShadeState(true, "click shelf");
        VivoStatusBarEventTrackerImpl.trackNotificatioEnterFullShadeState(((FrameLayout) this).mContext, "1");
    }

    public void bind(AmbientState ambientState, NotificationStackScrollLayout notificationStackScrollLayout, NotificationRoundnessManager notificationRoundnessManager) {
        this.mAmbientState = ambientState;
        this.mHostLayout = notificationStackScrollLayout;
        this.mHostLayoutController = notificationStackScrollLayout.getController();
        this.mRoundnessManager = notificationRoundnessManager;
    }

    private void updateResources() {
        Resources resources = getResources();
        this.mStatusBarHeight = SystemBarUtils.getStatusBarHeight(((FrameLayout) this).mContext);
        this.mPaddingBetweenElements = resources.getDimensionPixelSize(R.dimen.vivo_notification_paddding_between_elements);
        this.mMaxIconsOnLockscreen = resources.getInteger(R.integer.max_notif_icons_on_lockscreen);
        ViewGroup.LayoutParams layoutParams = getLayoutParams();
        int dimensionPixelOffset = resources.getDimensionPixelOffset(R.dimen.notification_shelf_height);
        if (dimensionPixelOffset != layoutParams.height) {
            layoutParams.height = dimensionPixelOffset;
            setLayoutParams(layoutParams);
        }
        int dimensionPixelOffset2 = resources.getDimensionPixelOffset(R.dimen.shelf_icon_container_padding);
        this.mShelfIcons.setPadding(dimensionPixelOffset2, 0, dimensionPixelOffset2, 0);
        this.mScrollFastThreshold = resources.getDimensionPixelOffset(R.dimen.scroll_fast_threshold);
        this.mShowNotificationShelf = resources.getBoolean(R.bool.config_showNotificationShelf);
        this.mCornerAnimationDistance = resources.getDimensionPixelSize(R.dimen.notification_corner_animation_distance);
        this.mEnableNotificationClipping = resources.getBoolean(R.bool.notification_enable_clipping);
        this.mGapBetweenIconAndDismiss = resources.getDimensionPixelOffset(R.dimen.vivo_shelf_gap_between_icons_dismiss);
        if (NotificationIconContainerRefactor.isEnabled()) {
            this.mShelfIcons.setOverrideIconColor(true);
        } else {
            this.mShelfIcons.setInNotificationIconShelf(true);
        }
        if (this.mShowNotificationShelf) {
            return;
        }
        setVisibility(8);
    }

    private void shouldLimitFontSize(Configuration configuration) {
        int curFontLevel;
        StringBuilder sb = new StringBuilder();
        sb.append(" configuration = ");
        sb.append(configuration == null);
        sb.append(" mTextView = ");
        sb.append(this.mDismissAllNotification == null);
        LogUtils.d(TAG, sb.toString());
        if (configuration == null || this.mDismissAllNotification == null || (curFontLevel = FontSizeLimitUtils.getCurFontLevel(configuration)) != 7) {
            return;
        }
        float textSize = (this.mDismissAllNotification.getTextSize() / FontSizeLimitUtils.getSysLevel()[curFontLevel - 1]) * FontSizeLimitUtils.getSysLevel()[5];
        this.mDismissAllNotification.setTextSize(0, textSize);
        LogUtils.d(TAG, "need limt font size, current sys level=" + curFontLevel + ", current size=" + this.mDismissAllNotification.getTextSize() + ", limit size=" + textSize + " fontScale = " + configuration.fontScale);
    }

    @Override // com.android.systemui.statusbar.notification.row.ExpandableView, android.view.View
    public void onConfigurationChanged(Configuration configuration) {
        super.onConfigurationChanged(configuration);
        updateResources();
        this.mDismissAllNotification.setTextColor(getResources().getColor(R.color.vivo_dismiss_notification_text_color));
        this.mDismissAllNotification.setTextSize(0, getResources().getDimensionPixelSize(R.dimen.vivo_shelf_dismiss_notification_text_size));
        this.mDismissAllNotification.setText(getResources().getString(R.string.vivo_clear_all_notifications_text));
        this.mDismissAllNotification.setTypeface(Typeface.DEFAULT_BOLD);
        shouldLimitFontSize(configuration);
        setDismissVisible(this.mDismissAllNotification.getVisibility() == 0);
    }

    @Override // com.android.systemui.statusbar.notification.row.ActivatableNotificationView
    public View getContentView() {
        return this.mShelfIcons;
    }

    public NotificationIconContainer getShelfIcons() {
        return this.mShelfIcons;
    }

    @Override // com.android.systemui.statusbar.notification.row.ExpandableView
    public ExpandableViewState createExpandableViewState() {
        return new ShelfState();
    }

    @Override // android.view.View
    public String toString() {
        return super.toString() + " (hideBackground=" + this.mHideBackground + " notGoneIndex=" + this.mNotGoneIndex + " hasItemsInStableShelf=" + this.mHasItemsInStableShelf + " interactive=" + this.mInteractive + " animationsEnabled=" + this.mAnimationsEnabled + " showNotificationShelf=" + this.mShowNotificationShelf + " indexOfFirstViewInShelf=" + this.mIndexOfFirstViewInShelf + ')';
    }

    @Override // com.android.systemui.statusbar.notification.row.ExpandableView
    public String getTraceDes() {
        return toString();
    }

    public void updateState(StackScrollAlgorithm.StackScrollAlgorithmState stackScrollAlgorithmState, AmbientState ambientState) {
        ExpandableView vivoLastChildWithoutClipBackground;
        ExpandableViewState viewState;
        ExpandableView lastVisibleBackgroundChild = ambientState.getLastVisibleBackgroundChild();
        ShelfState shelfState = (ShelfState) getViewState();
        if (this.mShowNotificationShelf && lastVisibleBackgroundChild != null) {
            ExpandableViewState viewState2 = lastVisibleBackgroundChild.getViewState();
            if (viewState2 == null) {
                LogUtils.w(TAG, "updateState: lastViewState is null!");
                return;
            }
            shelfState.copyFrom(viewState2);
            shelfState.height = getIntrinsicHeight();
            shelfState.setZTranslation(ambientState.getBaseZHeight());
            shelfState.clipTopAmount = 0;
            if (ambientState.isExpansionChanging() && !ambientState.isOnKeyguard()) {
                float expansionFraction = ambientState.getExpansionFraction();
                if (ambientState.isBouncerInTransit()) {
                    shelfState.setAlpha(BouncerPanelExpansionCalculator.aboutToShowBouncerProgress(expansionFraction));
                } else if (ambientState.isSmallScreen()) {
                    shelfState.setAlpha(ShadeInterpolation.getContentAlpha(expansionFraction));
                } else {
                    shelfState.setAlpha(ambientState.getLargeScreenShadeInterpolator().getNotificationContentAlpha(expansionFraction));
                }
            } else {
                shelfState.setAlpha(1.0f - ambientState.getHideAmount());
            }
            if (!NotificationIconContainerRefactor.isEnabled()) {
                shelfState.belowSpeedBump = getSpeedBumpIndex() == 0;
            }
            shelfState.hideSensitive = false;
            shelfState.setXTranslation(getTranslationX());
            shelfState.hasItemsInStableShelf = viewState2.inShelf;
            shelfState.firstViewInShelf = stackScrollAlgorithmState.firstViewInShelf;
            int i = this.mNotGoneIndex;
            if (i != -1) {
                shelfState.notGoneIndex = Math.min(shelfState.notGoneIndex, i);
            }
            shelfState.hidden = !this.mAmbientState.isShadeExpanded() || (!this.mHostLayoutController.shouldShowClearAll() && stackScrollAlgorithmState.firstViewInShelf == null) || ((CentralSurfaces) Dependency.get(CentralSurfaces.class)).getPanelController().isQSFullyExpanded();
            int iIndexOf = stackScrollAlgorithmState.visibleChildren.indexOf(stackScrollAlgorithmState.firstViewInShelf);
            if (this.mAmbientState.isExpansionChanging() && stackScrollAlgorithmState.firstViewInShelf != null && iIndexOf > 0 && stackScrollAlgorithmState.visibleChildren.get(iIndexOf - 1).getViewState().hidden) {
                shelfState.hidden = true;
            }
        } else {
            shelfState.hidden = true;
            shelfState.location = 64;
            shelfState.hasItemsInStableShelf = false;
        }
        float stackY = ambientState.getStackY() + ambientState.getStackHeight();
        if (shelfState.hidden) {
            shelfState.setYTranslation(this.mPaddingBetweenElements + stackY);
        } else {
            shelfState.setYTranslation(stackY - shelfState.height);
        }
        if (lastVisibleBackgroundChild != null) {
            ExpandableViewState viewState3 = lastVisibleBackgroundChild.getViewState();
            if (viewState3 == null) {
                LogUtils.d(TAG, "last viewState is null so return");
                return;
            }
            shelfState.mYTranslation = MathUtils.min(viewState3.mYTranslation + viewState3.height + this.mPaddingBetweenElements, stackY - shelfState.height);
            if (((FoldedStateMonitorUtils) Dependency.get(FoldedStateMonitorUtils.class)).isFoldable() && !((FoldedStateMonitorUtils) Dependency.get(FoldedStateMonitorUtils.class)).isFoldState() && VivoDeviceUtils.isFoldKeyguardOS2(((FrameLayout) this).mContext)) {
                return;
            }
            KeyguardFullShadeList keyguardFullShadeList = ((VivoKeyguardCoordinator) Dependency.get(VivoKeyguardCoordinator.class)).getKeyguardFullShadeList();
            if (keyguardFullShadeList != null && keyguardFullShadeList.getCurrentState() == 1 && ambientState.isOnKeyguard() && !this.mHostLayoutController.userChangedNotificationExpansion() && (vivoLastChildWithoutClipBackground = this.mHostLayoutController.getVivoLastChildWithoutClipBackground()) != null && (viewState = vivoLastChildWithoutClipBackground.getViewState()) != null) {
                shelfState.mYTranslation = viewState.mYTranslation + viewState.height + this.mPaddingBetweenElements;
            }
            if (LogUtils.isDebugOpen()) {
                int size = stackScrollAlgorithmState.visibleChildren.size();
                int i2 = 0;
                for (int i3 = 0; i3 < size; i3++) {
                    if (!stackScrollAlgorithmState.visibleChildren.get(i3).getViewState().inShelf) {
                        i2++;
                    }
                }
                if (i2 == 0 && size > 0 && ambientState.isShadeExpanded()) {
                    LogUtils.w(TAG, "all notification in shelf! shelfY = " + shelfState.getYTranslation() + ", stackY = " + ambientState.getStackY() + ", stackH = " + ambientState.getStackHeight() + "lastViewY = " + viewState3.mYTranslation);
                }
            }
        }
    }

    private int getSpeedBumpIndex() {
        NotificationIconContainerRefactor.assertInLegacyMode();
        return this.mHostLayout.getSpeedBumpIndex();
    }

    @VisibleForTesting
    public void updateActualWidth(float f, float f2) {
        float width;
        NotificationIconContainerRefactor.assertInLegacyMode();
        if (this.mAmbientState.isOnKeyguard()) {
            width = MathUtils.lerp(f2, getWidth(), f);
        } else {
            width = getWidth();
        }
        int i = (int) width;
        setBackgroundWidth(i);
        VivoNotificationIconContainer vivoNotificationIconContainer = this.mShelfIcons;
        if (vivoNotificationIconContainer != null) {
            vivoNotificationIconContainer.setActualLayoutWidth(i);
        }
        this.mActualWidth = width;
    }

    private void setActualWidth(float f) {
        if (NotificationIconContainerRefactor.isUnexpectedlyInLegacyMode()) {
            return;
        }
        int i = (int) f;
        setBackgroundWidth(i);
        VivoNotificationIconContainer vivoNotificationIconContainer = this.mShelfIcons;
        if (vivoNotificationIconContainer != null) {
            vivoNotificationIconContainer.setActualLayoutWidth(i);
        }
        this.mActualWidth = f;
    }

    @Override // com.android.systemui.statusbar.notification.row.ExpandableView
    public void getBoundsOnScreen(Rect rect, boolean z) {
        super.getBoundsOnScreen(rect, z);
        int actualWidth = getActualWidth();
        if (isLayoutRtl()) {
            rect.left = rect.right - actualWidth;
        } else {
            rect.right = rect.left + actualWidth;
        }
    }

    public int getActualWidth() {
        float f = this.mActualWidth;
        return f > -1.0f ? (int) f : getWidth();
    }

    @Override // com.android.systemui.statusbar.notification.row.ExpandableView
    public boolean pointInView(float f, float f2, float f3) {
        float width = getWidth();
        float actualWidth = getActualWidth();
        float f4 = isLayoutRtl() ? width - actualWidth : 0.0f;
        if (!isLayoutRtl()) {
            width = actualWidth;
        }
        return isXInView(f, f3, f4, width) && isYInView(f2, f3, (float) this.mClipTopAmount, (float) getActualHeight());
    }

    @Override // com.android.systemui.statusbar.notification.row.ActivatableNotificationView
    public void updateBackgroundColors() {
        super.updateBackgroundColors();
        ColorUpdateLogger colorUpdateLogger = ColorUpdateLogger.getInstance();
        if (colorUpdateLogger != null) {
            colorUpdateLogger.logEvent("Shelf.updateBackgroundColors()", "normalBgColor=" + ColorUtilKt.hexColorString(Integer.valueOf(getNormalBgColor())) + " background=" + this.mBackgroundNormal.toDumpString());
        }
    }

    /* JADX WARN: Removed duplicated region for block: B:86:0x0189  */
    /* JADX WARN: Removed duplicated region for block: B:89:0x0193  */
    /*
        Code decompiled incorrectly, please refer to instructions dump.
        To view partially-correct add '--show-bad-code' argument
    */
    public void updateAppearance() {
        /*
            Method dump skipped, instruction units count: 588
            To view this dump add '--comments-level debug' option
        */
        throw new UnsupportedOperationException("Method not decompiled: com.android.systemui.statusbar.NotificationShelf.updateAppearance():void");
    }

    private void changeShelfVisibility(boolean z) {
        if ((z && this.mDismissAllNotification.getVisibility() == 8) || ((CentralSurfaces) Dependency.get(CentralSurfaces.class)).isFullyCollapsed() || ((CentralSurfaces) Dependency.get(CentralSurfaces.class)).getPanelController().isQSFullyExpanded()) {
            setVisibility(4);
        } else {
            setVisibility(0);
        }
    }

    private float findNextChildTop(int i) {
        if (i >= this.mHostLayoutController.getChildCount() - 1) {
            return -1.0f;
        }
        while (true) {
            i++;
            if (i >= this.mHostLayoutController.getChildCount()) {
                return -1.0f;
            }
            ExpandableView childAt = this.mHostLayoutController.getChildAt(i);
            if ((childAt instanceof ExpandableNotificationRow) && childAt.getVisibility() == 0) {
                return childAt.getTranslationY();
            }
        }
    }

    private ExpandableView getHostLayoutChildAt(int i) {
        return (ExpandableView) this.mHostLayout.getChildAt(i);
    }

    private int getHostLayoutChildCount() {
        return this.mHostLayout.getChildCount();
    }

    private boolean canModifyColorOfNotifications() {
        return this.mCanModifyColorOfNotifications && this.mAmbientState.isShadeExpanded();
    }

    private void updateCornerRoundnessOnScroll(ActivatableNotificationView activatableNotificationView, float f, float f2) {
        boolean z = !this.mAmbientState.isOnKeyguard() && !this.mAmbientState.isShadeExpanded() && (activatableNotificationView instanceof ExpandableNotificationRow) && activatableNotificationView.isHeadsUp();
        boolean z2 = this.mAmbientState.isShadeExpanded() && activatableNotificationView == this.mAmbientState.getTrackedHeadsUpRow();
        if (f >= f2 || isViewAffectedBySwipe(activatableNotificationView) || z || z2 || activatableNotificationView.isAboveShelf() || this.mAmbientState.isPulsing() || this.mAmbientState.isDozing()) {
            return;
        }
        float actualHeight = activatableNotificationView.getActualHeight() + f;
        float expansionFraction = this.mCornerAnimationDistance * this.mAmbientState.getExpansionFraction();
        float f3 = f2 - expansionFraction;
        float fSaturate = f >= f3 ? MathUtils.saturate((f - f3) / expansionFraction) : 0.0f;
        SourceType sourceType = SHELF_SCROLL;
        activatableNotificationView.requestTopRoundness(fSaturate, sourceType, false);
        activatableNotificationView.requestBottomRoundness(actualHeight >= f3 ? MathUtils.saturate((actualHeight - f3) / expansionFraction) : 0.0f, sourceType, false);
    }

    private boolean isViewAffectedBySwipe(ExpandableView expandableView) {
        return this.mRoundnessManager.isViewAffectedBySwipe(expandableView);
    }

    private void clipTransientViews() {
        for (int i = 0; i < getHostLayoutTransientViewCount(); i++) {
            View hostLayoutTransientView = getHostLayoutTransientView(i);
            if (hostLayoutTransientView instanceof ExpandableView) {
                updateNotificationClipHeight((ExpandableView) hostLayoutTransientView, getTranslationY(), -1);
            }
        }
    }

    private View getHostLayoutTransientView(int i) {
        return this.mHostLayout.getTransientView(i);
    }

    private int getHostLayoutTransientViewCount() {
        return this.mHostLayout.getTransientViewCount();
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void updateIconClipAmount(ExpandableNotificationRow expandableNotificationRow) {
        float translationY = expandableNotificationRow.getTranslationY();
        if (getClipTopAmount() != 0) {
            translationY = Math.max(translationY, getTranslationY() + getClipTopAmount());
        }
        StatusBarIconView shelfIcon = expandableNotificationRow.getEntry().getIcons().getShelfIcon();
        float translationY2 = getTranslationY() + shelfIcon.getTop() + shelfIcon.getTranslationY();
        if (translationY2 < translationY && !this.mAmbientState.isFullyHidden()) {
            int i = (int) (translationY - translationY2);
            shelfIcon.setClipBounds(new Rect(0, i, shelfIcon.getWidth(), Math.max(i, shelfIcon.getHeight())));
        } else {
            shelfIcon.setClipBounds(null);
        }
    }

    private void updateContinuousClipping(final ExpandableNotificationRow expandableNotificationRow) {
        final StatusBarIconView shelfIcon = expandableNotificationRow.getEntry().getIcons().getShelfIcon();
        boolean z = ViewState.isAnimatingY(shelfIcon) && !this.mAmbientState.isDozing();
        int i = TAG_CONTINUOUS_CLIPPING;
        boolean z2 = shelfIcon.getTag(i) != null;
        if (!z || z2) {
            return;
        }
        final ViewTreeObserver viewTreeObserver = shelfIcon.getViewTreeObserver();
        final ViewTreeObserver.OnPreDrawListener onPreDrawListener = new ViewTreeObserver.OnPreDrawListener() { // from class: com.android.systemui.statusbar.NotificationShelf.2
            @Override // android.view.ViewTreeObserver.OnPreDrawListener
            public boolean onPreDraw() {
                if (!ViewState.isAnimatingY(shelfIcon)) {
                    if (viewTreeObserver.isAlive()) {
                        viewTreeObserver.removeOnPreDrawListener(this);
                    }
                    shelfIcon.setTag(NotificationShelf.TAG_CONTINUOUS_CLIPPING, null);
                    return true;
                }
                NotificationShelf.this.updateIconClipAmount(expandableNotificationRow);
                return true;
            }
        };
        viewTreeObserver.addOnPreDrawListener(onPreDrawListener);
        shelfIcon.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() { // from class: com.android.systemui.statusbar.NotificationShelf.3
            @Override // android.view.View.OnAttachStateChangeListener
            public void onViewAttachedToWindow(View view) {
            }

            @Override // android.view.View.OnAttachStateChangeListener
            public void onViewDetachedFromWindow(View view) {
                if (view == shelfIcon) {
                    if (viewTreeObserver.isAlive()) {
                        viewTreeObserver.removeOnPreDrawListener(onPreDrawListener);
                    }
                    shelfIcon.setTag(NotificationShelf.TAG_CONTINUOUS_CLIPPING, null);
                }
            }
        });
        shelfIcon.setTag(i, onPreDrawListener);
    }

    private int updateNotificationClipHeight(ExpandableView expandableView, float f, int i) {
        float translationY = expandableView.getTranslationY() + expandableView.getActualHeight();
        boolean zShowingPulsing = true;
        boolean z = (expandableView.isPinned() || expandableView.isHeadsUpAnimatingAway()) && !this.mAmbientState.isDozingAndNotPulsing(expandableView);
        if (!this.mAmbientState.isPulseExpanding()) {
            zShowingPulsing = expandableView.showingPulsing();
        } else if (i != 0) {
            zShowingPulsing = false;
        }
        if (!z) {
            if (translationY > f && !zShowingPulsing) {
                int i2 = this.mEnableNotificationClipping ? (int) (translationY - f) : 0;
                if (expandableView instanceof ExpandableNotificationRow) {
                    NotificationExposureHelper.onNotificationExposedByScroll((ExpandableNotificationRow) expandableView, expandableView.getIntrinsicHeight(), expandableView.getClipBottomAmount(), i2, false);
                }
                expandableView.setClipBottomAmount(i2);
            } else {
                if (expandableView instanceof ExpandableNotificationRow) {
                    NotificationExposureHelper.onNotificationExposedByScroll((ExpandableNotificationRow) expandableView, expandableView.getIntrinsicHeight(), expandableView.getClipBottomAmount(), 0, false);
                }
                expandableView.setClipBottomAmount(0);
            }
        }
        if (zShowingPulsing) {
            return (int) (translationY - getTranslationY());
        }
        return 0;
    }

    @Override // com.android.systemui.statusbar.notification.row.ActivatableNotificationView, com.android.systemui.statusbar.notification.row.ExpandableView
    public void setFakeShadowIntensity(float f, float f2, int i, int i2) {
        if (!this.mHasItemsInStableShelf) {
            f = 0.0f;
        }
        super.setFakeShadowIntensity(f, f2, i, i2);
    }

    /* JADX WARN: Removed duplicated region for block: B:13:0x0058  */
    /* JADX WARN: Removed duplicated region for block: B:31:0x009f  */
    @com.android.internal.annotations.VisibleForTesting
    /*
        Code decompiled incorrectly, please refer to instructions dump.
        To view partially-correct add '--show-bad-code' argument
    */
    public float getAmountInShelf(int r11, com.android.systemui.statusbar.notification.row.ExpandableView r12, boolean r13, boolean r14, boolean r15, float r16) {
        /*
            r10 = this;
            r0 = r10
            float r1 = r12.getTranslationY()
            int r2 = r12.getActualHeight()
            int r3 = r0.mPaddingBetweenElements
            int r2 = r2 + r3
            r3 = r12
            float r4 = r10.calculateIconTransformationStart(r12)
            float r5 = (float) r2
            float r5 = r5 + r1
            float r5 = r5 - r4
            int r6 = r10.getIntrinsicHeight()
            float r6 = (float) r6
            float r5 = java.lang.Math.min(r5, r6)
            if (r15 == 0) goto L3a
            int r6 = r12.getMinHeight()
            int r7 = r10.getIntrinsicHeight()
            int r6 = r6 - r7
            int r2 = java.lang.Math.min(r2, r6)
            int r6 = r12.getMinHeight()
            int r7 = r10.getIntrinsicHeight()
            int r6 = r6 - r7
            float r6 = (float) r6
            float r5 = java.lang.Math.min(r5, r6)
        L3a:
            float r2 = (float) r2
            float r6 = r1 + r2
            com.android.systemui.statusbar.notification.stack.AmbientState r7 = r0.mAmbientState
            boolean r7 = r7.isExpansionChanging()
            r8 = 0
            r9 = 1065353216(0x3f800000, float:1.0)
            if (r7 == 0) goto L5b
            com.android.systemui.statusbar.notification.stack.AmbientState r7 = r0.mAmbientState
            boolean r7 = r7.isOnKeyguard()
            if (r7 != 0) goto L5b
            int r1 = r0.mIndexOfFirstViewInShelf
            r2 = -1
            if (r1 == r2) goto L9f
            r2 = r11
            if (r2 < r1) goto L9f
        L58:
            r2 = r9
            r8 = r2
            goto La0
        L5b:
            int r6 = (r6 > r16 ? 1 : (r6 == r16 ? 0 : -1))
            if (r6 < 0) goto L9f
            com.android.systemui.statusbar.notification.stack.AmbientState r6 = r0.mAmbientState
            boolean r6 = r6.isShadeExpanded()
            if (r6 != 0) goto L73
            boolean r6 = r12.isPinned()
            if (r6 != 0) goto L9f
            boolean r6 = r12.isHeadsUpAnimatingAway()
            if (r6 != 0) goto L9f
        L73:
            int r6 = (r1 > r16 ? 1 : (r1 == r16 ? 0 : -1))
            if (r6 >= 0) goto L58
            float r6 = r1 - r16
            float r6 = java.lang.Math.abs(r6)
            r7 = 981668463(0x3a83126f, float:0.001)
            int r6 = (r6 > r7 ? 1 : (r6 == r7 ? 0 : -1))
            if (r6 <= 0) goto L58
            float r6 = r16 - r1
            float r2 = r6 / r2
            float r2 = java.lang.Math.min(r9, r2)
            float r2 = r9 - r2
            if (r15 == 0) goto L93
            float r4 = r4 - r1
            float r6 = r6 / r4
            goto L97
        L93:
            float r1 = r16 - r4
            float r6 = r1 / r5
        L97:
            float r1 = android.util.MathUtils.constrain(r6, r8, r9)
            float r9 = r9 - r1
            r8 = r2
            r2 = r9
            goto La0
        L9f:
            r2 = r8
        La0:
            r0 = r10
            r1 = r12
            r3 = r13
            r4 = r14
            r5 = r15
            r0.updateIconPositioning(r1, r2, r3, r4, r5)
            return r8
        */
        throw new UnsupportedOperationException("Method not decompiled: com.android.systemui.statusbar.NotificationShelf.getAmountInShelf(int, com.android.systemui.statusbar.notification.row.ExpandableView, boolean, boolean, boolean, float):float");
    }

    private float calculateIconTransformationStart(ExpandableView expandableView) {
        if (expandableView.getShelfTransformationTarget() == null) {
            return expandableView.getTranslationY();
        }
        return (expandableView.getTranslationY() + expandableView.getRelativeTopPadding(r1)) - expandableView.getShelfIcon().getTop();
    }

    private void updateIconPositioning(ExpandableView expandableView, float f, boolean z, boolean z2, boolean z3) {
        StatusBarIconView shelfIcon = expandableView.getShelfIcon();
        NotificationIconContainer.IconState iconState = getIconState(shelfIcon);
        if (iconState == null) {
            return;
        }
        float f2 = (f > 0.5f || isTargetClipped(expandableView)) ? 1.0f : 0.0f;
        if (f == f2) {
            iconState.noAnimations = (z || z2) && !z3;
        }
        if (!z3 && (z || (z2 && !ViewState.isAnimatingY(shelfIcon)))) {
            iconState.cancelAnimations(shelfIcon);
            iconState.noAnimations = true;
        }
        if (this.mAmbientState.isHiddenAtAll() && !expandableView.isInShelf()) {
            f = this.mAmbientState.isFullyHidden() ? 1.0f : 0.0f;
        } else {
            iconState.needsCannedAnimation = iconState.clampedAppearAmount != f2;
        }
        iconState.clampedAppearAmount = f2;
        setIconTransformationAmount(expandableView, f);
    }

    private boolean isTargetClipped(ExpandableView expandableView) {
        View shelfTransformationTarget = expandableView.getShelfTransformationTarget();
        return shelfTransformationTarget != null && ((expandableView.getTranslationY() + expandableView.getContentTranslation()) + ((float) expandableView.getRelativeTopPadding(shelfTransformationTarget))) + ((float) shelfTransformationTarget.getHeight()) >= getTranslationY() - ((float) this.mPaddingBetweenElements);
    }

    private void setIconTransformationAmount(ExpandableView expandableView, float f) {
        ExpandableNotificationRow expandableNotificationRow;
        StatusBarIconView shelfIcon;
        NotificationIconContainer.IconState iconState;
        boolean z = expandableView instanceof ExpandableNotificationRow;
        if (!z || (iconState = getIconState((shelfIcon = (expandableNotificationRow = (ExpandableNotificationRow) expandableView).getShelfIcon()))) == null) {
            return;
        }
        iconState.setAlpha(ICON_ALPHA_INTERPOLATOR.getInterpolation(f));
        boolean z2 = (expandableNotificationRow.isDrawingAppearAnimation() && !expandableNotificationRow.isInShelf()) || (z && expandableNotificationRow.isMinimized() && this.mShelfIcons.areIconsOverflowing()) || ((f == 0.0f && !iconState.isAnimating(shelfIcon)) || expandableNotificationRow.isAboveShelf() || expandableNotificationRow.showingPulsing() || expandableNotificationRow.getTranslationZ() > ((float) this.mAmbientState.getBaseZHeight()));
        iconState.hidden = z2;
        if (z2) {
            f = 0.0f;
        }
        iconState.iconAppearAmount = f;
        iconState.setXTranslation(this.mShelfIcons.getActualPaddingStart());
        if (expandableNotificationRow.isInShelf() && !expandableNotificationRow.isTransformingIntoShelf()) {
            iconState.iconAppearAmount = 1.0f;
            iconState.setAlpha(1.0f);
            iconState.hidden = false;
        }
        int contrastedStaticDrawableColor = shelfIcon.getContrastedStaticDrawableColor(getBackgroundColorWithoutTint());
        if (expandableNotificationRow.isShowingIcon() && contrastedStaticDrawableColor != 0) {
            contrastedStaticDrawableColor = NotificationUtils.interpolateColors(expandableNotificationRow.getOriginalIconColor(), contrastedStaticDrawableColor, iconState.iconAppearAmount);
        }
        if (shelfIcon.getNeedColored()) {
            iconState.iconColor = contrastedStaticDrawableColor;
        } else {
            iconState.iconColor = 0;
        }
    }

    private NotificationIconContainer.IconState getIconState(StatusBarIconView statusBarIconView) {
        VivoNotificationIconContainer vivoNotificationIconContainer = this.mShelfIcons;
        if (vivoNotificationIconContainer == null) {
            return null;
        }
        return vivoNotificationIconContainer.getIconState(statusBarIconView);
    }

    private void setHideBackground(boolean z) {
        if (this.mHideBackground != z) {
            this.mHideBackground = z;
            updateOutline();
        }
    }

    @Override // com.android.systemui.statusbar.notification.row.ExpandableOutlineView
    public boolean needsOutline() {
        return !this.mHideBackground && super.needsOutline();
    }

    @Override // com.android.systemui.statusbar.notification.row.ActivatableNotificationView, com.android.systemui.statusbar.notification.row.ExpandableView, android.widget.FrameLayout, android.view.ViewGroup, android.view.View
    public void onLayout(boolean z, int i, int i2, int i3, int i4) {
        super.onLayout(z, i, i2, i3, i4);
        int i5 = getResources().getDisplayMetrics().heightPixels;
        this.mClipRect.set(0, -i5, getWidth(), i5);
        VivoNotificationIconContainer vivoNotificationIconContainer = this.mShelfIcons;
        if (vivoNotificationIconContainer != null) {
            vivoNotificationIconContainer.setClipBounds(this.mClipRect);
        }
    }

    public int getNotGoneIndex() {
        return this.mNotGoneIndex;
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void setHasItemsInStableShelf(boolean z) {
        if (this.mHasItemsInStableShelf != z) {
            this.mHasItemsInStableShelf = z;
            updateInteractiveness();
        }
    }

    private void updateInteractiveness() {
        boolean z = this.mCanInteract && this.mHasItemsInStableShelf;
        this.mInteractive = z;
        setClickable(z);
        setFocusable(this.mInteractive);
    }

    @Override // com.android.systemui.statusbar.notification.row.ActivatableNotificationView
    public boolean isInteractive() {
        return this.mInteractive;
    }

    public void setAnimationsEnabled(boolean z) {
        this.mAnimationsEnabled = z;
        if (z) {
            return;
        }
        this.mShelfIcons.setAnimationsEnabled(false);
    }

    @Override // android.view.View
    public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo accessibilityNodeInfo) {
        super.onInitializeAccessibilityNodeInfo(accessibilityNodeInfo);
        if (this.mInteractive) {
            accessibilityNodeInfo.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_EXPAND);
            accessibilityNodeInfo.addAction(new AccessibilityNodeInfo.AccessibilityAction(16, getContext().getString(R.string.accessibility_overflow_action)));
        }
    }

    public void setCanModifyColorOfNotifications(boolean z) {
        this.mCanModifyColorOfNotifications = z;
    }

    public void setCanInteract(boolean z) {
        this.mCanInteract = z;
        updateInteractiveness();
    }

    public void setIndexOfFirstViewInShelf(ExpandableView expandableView) {
        this.mIndexOfFirstViewInShelf = getIndexOfViewInHostLayout(expandableView);
    }

    private int getIndexOfViewInHostLayout(ExpandableView expandableView) {
        return this.mHostLayout.indexOfChild(expandableView);
    }

    public void requestRoundnessResetFor(ExpandableView expandableView) {
        expandableView.requestRoundnessReset(SHELF_SCROLL);
    }

    @Override // com.android.systemui.statusbar.notification.row.ActivatableNotificationView, com.android.systemui.statusbar.notification.row.ExpandableOutlineView, com.android.systemui.statusbar.notification.row.ExpandableView, com.android.systemui.Dumpable
    public void dump(PrintWriter printWriter, String[] strArr) {
        final IndentingPrintWriter indentingPrintWriterAsIndenting = DumpUtilsKt.asIndenting(printWriter);
        super.dump(indentingPrintWriterAsIndenting, strArr);
        indentingPrintWriterAsIndenting.println("fullAodAmount: " + getFullAodAmount() + ", transitionAlpha: " + getTransitionAlpha());
        if (ExpandableView.DUMP_VERBOSE) {
            DumpUtilsKt.withIncreasedIndent(indentingPrintWriterAsIndenting, new Runnable() { // from class: com.android.systemui.statusbar.NotificationShelf$$ExternalSyntheticLambda1
                @Override // java.lang.Runnable
                public final void run() {
                    this.f$0.lambda$dump$1(indentingPrintWriterAsIndenting);
                }
            });
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$dump$1(IndentingPrintWriter indentingPrintWriter) {
        indentingPrintWriter.println("mActualWidth: " + this.mActualWidth);
        indentingPrintWriter.println("mStatusBarHeight: " + this.mStatusBarHeight);
    }

    public class ShelfState extends ExpandableViewState {
        private ExpandableView firstViewInShelf;
        private boolean hasItemsInStableShelf;

        public ShelfState() {
        }

        @Override // com.android.systemui.statusbar.notification.stack.ExpandableViewState, com.android.systemui.statusbar.notification.stack.ViewState
        public void applyToView(View view) {
            if (NotificationShelf.this.mShowNotificationShelf) {
                super.applyToView(view);
                NotificationShelf.this.setIndexOfFirstViewInShelf(this.firstViewInShelf);
                NotificationShelf.this.updateAppearance();
                NotificationShelf.this.setHasItemsInStableShelf(this.hasItemsInStableShelf);
                NotificationShelf.this.mShelfIcons.setAnimationsEnabled(NotificationShelf.this.mAnimationsEnabled);
            }
        }

        @Override // com.android.systemui.statusbar.notification.stack.ExpandableViewState, com.android.systemui.statusbar.notification.stack.ViewState
        public void animateTo(View view, AnimationProperties animationProperties) {
            if (NotificationShelf.this.mShowNotificationShelf) {
                super.animateTo(view, animationProperties);
                NotificationShelf.this.setIndexOfFirstViewInShelf(this.firstViewInShelf);
                NotificationShelf.this.updateAppearance();
                NotificationShelf.this.setHasItemsInStableShelf(this.hasItemsInStableShelf);
                NotificationShelf.this.mShelfIcons.setAnimationsEnabled(NotificationShelf.this.mAnimationsEnabled);
            }
        }
    }

    public void updateShelfWhenDismissAll(boolean z) {
        if (this.mLastHideState || z) {
            animateShelfDisappear(z);
        }
    }

    private void animateShelfDisappear(boolean z) {
        animate().cancel();
        if (getVisibility() == 0) {
            animate().alpha(0.0f).scaleX(z ? 0.0f : 1.0f).scaleY(z ? 0.0f : 1.0f).setDuration(250L).setInterpolator(new PathInterpolator(0.33f, 0.0f, 0.67f, 1.0f)).withEndAction(new Runnable() { // from class: com.android.systemui.statusbar.NotificationShelf.5
                @Override // java.lang.Runnable
                public void run() {
                    NotificationShelf.this.setVisibility(4);
                    NotificationShelf.this.mShelfDisappearAnimatorRun = false;
                }
            }).withStartAction(new Runnable() { // from class: com.android.systemui.statusbar.NotificationShelf.4
                @Override // java.lang.Runnable
                public void run() {
                    NotificationShelf.this.mShelfDisappearAnimatorRun = true;
                }
            }).start();
        }
    }

    public void setOnKeyguard(boolean z) {
        this.mBackgroundNormal.setBackgroundType(z ? 3 : 0);
        updateBackground();
    }

    @Override // com.android.systemui.statusbar.notification.row.ActivatableNotificationView
    public boolean hideBackground() {
        return super.hideBackground() || VivoDeviceUtils.isFoldKeyguardOS2(((FrameLayout) this).mContext);
    }

    @Override // com.android.systemui.statusbar.notification.row.ActivatableNotificationView
    public void setFullAodAmount(float f, boolean z) {
        super.setFullAodAmount(f, z);
        NotificationIconRedrawHelper.getInstance(getContext()).aodChange(z);
    }
}
