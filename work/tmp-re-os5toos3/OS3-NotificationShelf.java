package com.android.systemui.statusbar;

import android.content.Context;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.util.MathUtils;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.animation.Interpolator;
import android.view.animation.PathInterpolator;
import android.widget.Button;
import android.widget.TextView;
import androidx.core.view.AccessibilityDelegateCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;
import com.android.internal.policy.SystemBarUtils;
import com.android.systemui.Dependency;
import com.android.systemui.R;
import com.android.systemui.animation.ShadeInterpolation;
import com.android.systemui.plugins.statusbar.StatusBarStateController;
import com.android.systemui.statusbar.notification.NotificationUtils;
import com.android.systemui.statusbar.notification.row.ActivatableNotificationView;
import com.android.systemui.statusbar.notification.row.ExpandableNotificationRow;
import com.android.systemui.statusbar.notification.row.ExpandableView;
import com.android.systemui.statusbar.notification.stack.AmbientState;
import com.android.systemui.statusbar.notification.stack.AnimationProperties;
import com.android.systemui.statusbar.notification.stack.ExpandableViewState;
import com.android.systemui.statusbar.notification.stack.NotificationStackScrollLayoutController;
import com.android.systemui.statusbar.notification.stack.StackScrollAlgorithm;
import com.android.systemui.statusbar.notification.stack.ViewState;
import com.android.systemui.statusbar.phone.CentralSurfaces;
import com.android.systemui.statusbar.phone.NotificationIconContainer;
import com.vivo.common.trackevent.misc.NotificationExposureHelper;
import com.vivo.common.utils.LogUtils;
import com.vivo.systemui.statusbar.notification.stack.VivoCustomStackSection;
import com.vivo.systemui.statusbar.phone.VivoNotificationIconContainer;
import com.vivo.systemui.statusbar.utils.FontSizeLimitUtils;

/* JADX INFO: loaded from: classes2.dex */
public class NotificationShelf extends ActivatableNotificationView implements View.OnLayoutChangeListener, StatusBarStateController.StateListener {
    private static final Interpolator ICON_ALPHA_INTERPOLATOR = new PathInterpolator(0.6f, 0.0f, 0.6f, 0.0f);
    private static final String TAG = "NotificationShelf";
    private static final int TAG_CONTINUOUS_CLIPPING = 2131427790;
    private float mActualWidth;
    private AmbientState mAmbientState;
    private boolean mAnimationsEnabled;
    private Rect mClipRect;
    private VivoNotificationIconContainer mCollapsedIcons;
    private NotificationShelfController mController;
    private float mCornerAnimationDistance;
    private TextView mDismissAllNotification;
    private float mFirstElementRoundness;
    private int mGapBetweenIconAndDismiss;
    private boolean mHasItemsInStableShelf;
    private boolean mHideBackground;
    private NotificationStackScrollLayoutController mHostLayoutController;
    private int mIndexOfFirstViewInShelf;
    private boolean mInteractive;
    private boolean mLastHideState;
    private int mNotGoneIndex;
    private int mPaddingBetweenElements;
    private int mScrollFastThreshold;
    private VivoNotificationIconContainer mShelfIcons;
    private boolean mShowNotificationShelf;
    private int mStatusBarHeight;
    private int mStatusBarState;
    private int[] mTmp;

    @Override // com.android.systemui.statusbar.notification.row.ExpandableView
    public boolean hasNoContentHeight() {
        return true;
    }

    @Override // com.android.systemui.statusbar.notification.row.ExpandableView, android.view.View
    public boolean hasOverlappingRendering() {
        return false;
    }

    public boolean isXInView(float f, float f2, float f3, float f4) {
        return f3 - f2 <= f && f < f4 + f2;
    }

    public boolean isYInView(float f, float f2, float f3, float f4) {
        return f3 - f2 <= f && f < f4 + f2;
    }

    @Override // com.android.systemui.statusbar.notification.row.ExpandableView
    public boolean needsClippingToShelf() {
        return false;
    }

    public void setDismissVisible(boolean z) {
        this.mDismissAllNotification.setVisibility(z ? 0 : 8);
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
        this.mTmp = new int[2];
        this.mAnimationsEnabled = true;
        this.mClipRect = new Rect();
        this.mIndexOfFirstViewInShelf = -1;
        this.mActualWidth = -1.0f;
    }

    @Override // com.android.systemui.statusbar.notification.row.ActivatableNotificationView, com.android.systemui.statusbar.notification.row.ExpandableOutlineView, android.view.View
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
        setBottomRoundness(1.0f, false);
        setTopRoundness(0.0f, false);
        TextView textView = (TextView) findViewById(R.id.clear_all_notification);
        this.mDismissAllNotification = textView;
        textView.setText(getResources().getString(R.string.vivo_clear_all_notifications_text));
        this.mDismissAllNotification.setTypeface(Typeface.DEFAULT_BOLD);
        setFirstInSection(true);
        initDimens();
        shouldLimitFontSize(getContext().getResources().getConfiguration());
        ViewCompat.setAccessibilityDelegate(this.mDismissAllNotification, new AccessibilityDelegateCompat() { // from class: com.android.systemui.statusbar.NotificationShelf.1
            @Override // androidx.core.view.AccessibilityDelegateCompat
            public void onInitializeAccessibilityNodeInfo(View view, AccessibilityNodeInfoCompat accessibilityNodeInfoCompat) {
                super.onInitializeAccessibilityNodeInfo(view, accessibilityNodeInfoCompat);
                accessibilityNodeInfoCompat.setClassName(Button.class.getName());
            }
        });
    }

    public void bind(AmbientState ambientState, NotificationStackScrollLayoutController notificationStackScrollLayoutController) {
        this.mAmbientState = ambientState;
        this.mHostLayoutController = notificationStackScrollLayoutController;
    }

    private void initDimens() {
        Resources resources = getResources();
        this.mStatusBarHeight = SystemBarUtils.getStatusBarHeight(this.mContext);
        this.mPaddingBetweenElements = 0;
        ViewGroup.LayoutParams layoutParams = getLayoutParams();
        layoutParams.height = resources.getDimensionPixelOffset(R.dimen.notification_shelf_height);
        setLayoutParams(layoutParams);
        int dimensionPixelOffset = resources.getDimensionPixelOffset(R.dimen.shelf_icon_container_padding);
        this.mShelfIcons.setPadding(dimensionPixelOffset, 0, dimensionPixelOffset, 0);
        this.mScrollFastThreshold = resources.getDimensionPixelOffset(R.dimen.scroll_fast_threshold);
        this.mShowNotificationShelf = resources.getBoolean(R.bool.config_showNotificationShelf);
        this.mCornerAnimationDistance = resources.getDimensionPixelSize(R.dimen.notification_corner_animation_distance);
        this.mGapBetweenIconAndDismiss = resources.getDimensionPixelOffset(R.dimen.vivo_shelf_gap_between_icons_dismiss);
        this.mShelfIcons.setInNotificationIconShelf(true);
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
    protected void onConfigurationChanged(Configuration configuration) {
        super.onConfigurationChanged(configuration);
        initDimens();
        this.mDismissAllNotification.setTextSize(0, getResources().getDimensionPixelSize(R.dimen.vivo_shelf_dismiss_notification_text_size));
        this.mDismissAllNotification.setText(getResources().getString(R.string.vivo_clear_all_notifications_text));
        shouldLimitFontSize(configuration);
        this.mRowDivider.setDividerColor(getResources().getColor(R.color.notification_divider_color));
        setDismissVisible(this.mDismissAllNotification.getVisibility() == 0);
    }

    @Override // com.android.systemui.statusbar.notification.row.ActivatableNotificationView
    protected View getContentView() {
        return this.mShelfIcons;
    }

    public NotificationIconContainer getShelfIcons() {
        return this.mShelfIcons;
    }

    @Override // com.android.systemui.statusbar.notification.row.ExpandableView
    public ExpandableViewState createExpandableViewState() {
        return new ShelfState();
    }

    public void updateState(StackScrollAlgorithm.StackScrollAlgorithmState stackScrollAlgorithmState, AmbientState ambientState) {
        ExpandableView lastVisibleBackgroundChild = ambientState.getLastVisibleBackgroundChild();
        ShelfState shelfState = (ShelfState) getViewState();
        boolean z = false;
        if (this.mShowNotificationShelf && lastVisibleBackgroundChild != null) {
            ExpandableViewState viewState = lastVisibleBackgroundChild.getViewState();
            if (viewState == null) {
                LogUtils.w(TAG, "updateState: lastViewState is null!");
                return;
            }
            shelfState.copyFrom(viewState);
            shelfState.height = getIntrinsicHeight();
            shelfState.zTranslation = ambientState.getBaseZHeight();
            shelfState.clipTopAmount = 0;
            if (ambientState.isExpansionChanging() && !ambientState.isOnKeyguard()) {
                shelfState.alpha = ShadeInterpolation.getContentAlpha(ambientState.getExpansionFraction());
            } else {
                shelfState.alpha = 1.0f - ambientState.getHideAmount();
            }
            shelfState.belowSpeedBump = this.mHostLayoutController.getSpeedBumpIndex() == 0;
            shelfState.hideSensitive = false;
            shelfState.xTranslation = getTranslationX();
            shelfState.hasItemsInStableShelf = viewState.inShelf;
            shelfState.firstViewInShelf = stackScrollAlgorithmState.firstViewInShelf;
            if (this.mNotGoneIndex != -1) {
                shelfState.notGoneIndex = Math.min(shelfState.notGoneIndex, this.mNotGoneIndex);
            }
            if (!this.mAmbientState.isShadeExpanded() || (!this.mHostLayoutController.shouldShowClearAll() && stackScrollAlgorithmState.firstViewInShelf == null)) {
                z = true;
            }
            shelfState.hidden = z;
            int iIndexOf = stackScrollAlgorithmState.visibleChildren.indexOf(stackScrollAlgorithmState.firstViewInShelf);
            if (this.mAmbientState.isExpansionChanging() && stackScrollAlgorithmState.firstViewInShelf != null && iIndexOf > 0 && stackScrollAlgorithmState.visibleChildren.get(iIndexOf - 1).getViewState().hidden) {
                shelfState.hidden = true;
            }
            shelfState.yTranslation = MathUtils.min(viewState.yTranslation + viewState.height, (ambientState.getStackY() + ambientState.getStackHeight()) - shelfState.height);
            return;
        }
        shelfState.hidden = true;
        shelfState.location = 64;
        shelfState.hasItemsInStableShelf = false;
    }

    public void updateActualWidth(float f, float f2) {
        float width;
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

    /* JADX WARN: Removed duplicated region for block: B:102:0x01d5  */
    /* JADX WARN: Removed duplicated region for block: B:122:0x028c  */
    /* JADX WARN: Removed duplicated region for block: B:133:0x02b1  */
    /* JADX WARN: Removed duplicated region for block: B:136:0x02ba  */
    /* JADX WARN: Removed duplicated region for block: B:146:? A[RETURN, SYNTHETIC] */
    /* JADX WARN: Removed duplicated region for block: B:92:0x019c  */
    /* JADX WARN: Removed duplicated region for block: B:95:0x01a7  */
    /*
        Code decompiled incorrectly, please refer to instructions dump.
        To view partially-correct add '--show-bad-code' argument
    */
    public void updateAppearance() {
        /*
            Method dump skipped, instruction units count: 701
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

    private void updateCornerRoundnessOnScroll(ActivatableNotificationView activatableNotificationView, float f, float f2) {
        if ((f >= f2 || this.mHostLayoutController.isViewAffectedBySwipe(activatableNotificationView) || (!this.mAmbientState.isOnKeyguard() && !this.mAmbientState.isShadeExpanded() && (activatableNotificationView instanceof ExpandableNotificationRow) && ((ExpandableNotificationRow) activatableNotificationView).isHeadsUp()) || (this.mAmbientState.isShadeExpanded() && activatableNotificationView == this.mAmbientState.getTrackedHeadsUpRow()) || activatableNotificationView.isAboveShelf() || this.mAmbientState.isPulsing() || this.mAmbientState.isDozing()) ? false : true) {
            float dimension = getResources().getDimension(R.dimen.notification_corner_radius_small) / getResources().getDimension(R.dimen.notification_corner_radius);
            float actualHeight = activatableNotificationView.getActualHeight() + f;
            float expansionFraction = this.mCornerAnimationDistance * this.mAmbientState.getExpansionFraction();
            float f3 = f2 - expansionFraction;
            if (actualHeight >= f3) {
                float fSaturate = MathUtils.saturate((actualHeight - f3) / expansionFraction);
                if (activatableNotificationView.isLastInSection()) {
                    fSaturate = 1.0f;
                }
                activatableNotificationView.setBottomRoundness(fSaturate, false);
            } else if (actualHeight < f3) {
                activatableNotificationView.setBottomRoundness(activatableNotificationView.isLastInSection() ? 1.0f : dimension, false);
            }
            if (f >= f3) {
                activatableNotificationView.setTopRoundness(activatableNotificationView.isFirstInSection() ? 1.0f : MathUtils.saturate((f - f3) / expansionFraction), false);
            } else if (f < f3) {
                if (activatableNotificationView.isFirstInSection()) {
                    dimension = 1.0f;
                }
                activatableNotificationView.setTopRoundness(dimension, false);
            }
        }
    }

    private void clipTransientViews() {
        for (int i = 0; i < this.mHostLayoutController.getTransientViewCount(); i++) {
            View transientView = this.mHostLayoutController.getTransientView(i);
            if (transientView instanceof ExpandableView) {
                updateNotificationClipHeight((ExpandableView) transientView, getTranslationY(), -1);
            }
        }
    }

    private void setFirstElementRoundness(float f) {
        if (this.mFirstElementRoundness != f) {
            this.mFirstElementRoundness = f;
        }
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
        boolean z2 = shelfIcon.getTag(R.id.continuous_clipping_tag) != null;
        if (!z || z2) {
            return;
        }
        final ViewTreeObserver viewTreeObserver = shelfIcon.getViewTreeObserver();
        final ViewTreeObserver.OnPreDrawListener onPreDrawListener = new ViewTreeObserver.OnPreDrawListener() { // from class: com.android.systemui.statusbar.NotificationShelf.2
            @Override // android.view.ViewTreeObserver.OnPreDrawListener
            public boolean onPreDraw() {
                if (ViewState.isAnimatingY(shelfIcon)) {
                    NotificationShelf.this.updateIconClipAmount(expandableNotificationRow);
                    return true;
                }
                if (viewTreeObserver.isAlive()) {
                    viewTreeObserver.removeOnPreDrawListener(this);
                }
                shelfIcon.setTag(R.id.continuous_clipping_tag, null);
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
                    shelfIcon.setTag(R.id.continuous_clipping_tag, null);
                }
            }
        });
        shelfIcon.setTag(R.id.continuous_clipping_tag, onPreDrawListener);
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
            if (translationY > f && !zShowingPulsing && !(expandableView instanceof VivoCustomStackSection)) {
                int i2 = (int) (translationY - f);
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
    /* JADX WARN: Removed duplicated region for block: B:35:0x00ad  */
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
            if (r1 == r2) goto Lad
            r2 = r11
            if (r2 < r1) goto Lad
        L58:
            r2 = r9
            r8 = r2
            goto Lae
        L5b:
            int r6 = (r6 > r16 ? 1 : (r6 == r16 ? 0 : -1))
            if (r6 < 0) goto Lad
            com.android.systemui.statusbar.notification.stack.AmbientState r6 = r0.mAmbientState
            boolean r6 = r6.isUnlockHintRunning()
            if (r6 == 0) goto L6d
            boolean r6 = r12.isInShelf()
            if (r6 == 0) goto Lad
        L6d:
            com.android.systemui.statusbar.notification.stack.AmbientState r6 = r0.mAmbientState
            boolean r6 = r6.isShadeExpanded()
            if (r6 != 0) goto L81
            boolean r6 = r12.isPinned()
            if (r6 != 0) goto Lad
            boolean r6 = r12.isHeadsUpAnimatingAway()
            if (r6 != 0) goto Lad
        L81:
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
            if (r15 == 0) goto La1
            float r4 = r4 - r1
            float r6 = r6 / r4
            goto La5
        La1:
            float r1 = r16 - r4
            float r6 = r1 / r5
        La5:
            float r1 = android.util.MathUtils.constrain(r6, r8, r9)
            float r9 = r9 - r1
            r8 = r2
            r2 = r9
            goto Lae
        Lad:
            r2 = r8
        Lae:
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
        float f2 = (f > 0.5f ? 1 : (f == 0.5f ? 0 : -1)) > 0 || isTargetClipped(expandableView) ? 1.0f : 0.0f;
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
        if (z && (iconState = getIconState((shelfIcon = (expandableNotificationRow = (ExpandableNotificationRow) expandableView).getShelfIcon()))) != null) {
            iconState.alpha = ICON_ALPHA_INTERPOLATOR.getInterpolation(f);
            iconState.hidden = (expandableNotificationRow.isDrawingAppearAnimation() && !expandableNotificationRow.isInShelf()) || (z && expandableNotificationRow.isLowPriority() && this.mShelfIcons.hasMaxNumDot()) || ((f == 0.0f && !iconState.isAnimating(shelfIcon)) || expandableNotificationRow.isAboveShelf() || expandableNotificationRow.showingPulsing() || expandableNotificationRow.getTranslationZ() > ((float) this.mAmbientState.getBaseZHeight()));
            if (iconState.hidden) {
                f = 0.0f;
            }
            iconState.iconAppearAmount = f;
            iconState.xTranslation = this.mShelfIcons.getActualPaddingStart();
            if (expandableNotificationRow.isInShelf() && !expandableNotificationRow.isTransformingIntoShelf()) {
                iconState.iconAppearAmount = 1.0f;
                iconState.alpha = 1.0f;
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
    }

    private NotificationIconContainer.IconState getIconState(StatusBarIconView statusBarIconView) {
        VivoNotificationIconContainer vivoNotificationIconContainer = this.mShelfIcons;
        if (vivoNotificationIconContainer == null) {
            return null;
        }
        return vivoNotificationIconContainer.getIconState(statusBarIconView);
    }

    private float getFullyClosedTranslation() {
        return (-(getIntrinsicHeight() - this.mStatusBarHeight)) / 2;
    }

    private void setHideBackground(boolean z) {
        if (this.mHideBackground != z) {
            this.mHideBackground = z;
            updateOutline();
        }
        setLastVisibleRowBottomRoundness(z);
    }

    private void setLastVisibleRowBottomRoundness(boolean z) {
        ExpandableView lastVisibleBackgroundChild = this.mAmbientState.getLastVisibleBackgroundChild();
        if ((lastVisibleBackgroundChild instanceof ExpandableNotificationRow) && lastVisibleBackgroundChild.getVisibility() == 0) {
            ExpandableNotificationRow expandableNotificationRow = (ExpandableNotificationRow) lastVisibleBackgroundChild;
            expandableNotificationRow.setBottomRoundness((z || expandableNotificationRow.isHeadsUpState()) ? 1.0f : 0.0f, false);
        }
    }

    @Override // com.android.systemui.statusbar.notification.row.ExpandableOutlineView
    protected boolean needsOutline() {
        return !this.mHideBackground && super.needsOutline();
    }

    @Override // com.android.systemui.statusbar.notification.row.ActivatableNotificationView, com.android.systemui.statusbar.notification.row.ExpandableView, android.widget.FrameLayout, android.view.ViewGroup, android.view.View
    protected void onLayout(boolean z, int i, int i2, int i3, int i4) {
        super.onLayout(z, i, i2, i3, i4);
        updateRelativeOffset();
        int i5 = getResources().getDisplayMetrics().heightPixels;
        this.mClipRect.set(0, -i5, getWidth(), i5);
        VivoNotificationIconContainer vivoNotificationIconContainer = this.mShelfIcons;
        if (vivoNotificationIconContainer != null) {
            vivoNotificationIconContainer.setClipBounds(this.mClipRect);
        }
        this.mRowDivider.layout(0, 0, getWidth(), this.mRowDivider.getHeight());
    }

    private void updateRelativeOffset() {
        VivoNotificationIconContainer vivoNotificationIconContainer = this.mCollapsedIcons;
        if (vivoNotificationIconContainer != null) {
            vivoNotificationIconContainer.getLocationOnScreen(this.mTmp);
        }
        getLocationOnScreen(this.mTmp);
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

    public boolean hasItemsInStableShelf() {
        return this.mHasItemsInStableShelf;
    }

    public void setCollapsedIcons(VivoNotificationIconContainer vivoNotificationIconContainer) {
        this.mCollapsedIcons = vivoNotificationIconContainer;
        vivoNotificationIconContainer.addOnLayoutChangeListener(this);
    }

    @Override // com.android.systemui.plugins.statusbar.StatusBarStateController.StateListener
    public void onStateChanged(int i) {
        this.mStatusBarState = i;
        updateInteractiveness();
    }

    private void updateInteractiveness() {
        boolean z = this.mStatusBarState == 1 && this.mHasItemsInStableShelf;
        this.mInteractive = z;
        setClickable(z);
        setFocusable(this.mInteractive);
    }

    @Override // com.android.systemui.statusbar.notification.row.ActivatableNotificationView
    protected boolean isInteractive() {
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

    @Override // android.view.View.OnLayoutChangeListener
    public void onLayoutChange(View view, int i, int i2, int i3, int i4, int i5, int i6, int i7, int i8) {
        updateRelativeOffset();
    }

    public void setController(NotificationShelfController notificationShelfController) {
        this.mController = notificationShelfController;
    }

    public void setIndexOfFirstViewInShelf(ExpandableView expandableView) {
        this.mIndexOfFirstViewInShelf = this.mHostLayoutController.indexOfChild(expandableView);
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
            animate().alpha(0.0f).scaleX(z ? 0.0f : 1.0f).scaleY(z ? 0.0f : 1.0f).setDuration(250L).setInterpolator(new PathInterpolator(0.33f, 0.0f, 0.67f, 1.0f)).withEndAction(new Runnable() { // from class: com.android.systemui.statusbar.-$$Lambda$NotificationShelf$1qu-1mABKcOyWYgiUtd97ofRw14
                @Override // java.lang.Runnable
                public final void run() {
                    this.f$0.lambda$animateShelfDisappear$0$NotificationShelf();
                }
            }).start();
        }
    }

    public /* synthetic */ void lambda$animateShelfDisappear$0$NotificationShelf() {
        setVisibility(4);
    }
}
