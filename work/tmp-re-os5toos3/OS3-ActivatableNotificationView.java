package com.android.systemui.statusbar.notification.row;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Point;
import android.util.AttributeSet;
import android.util.MathUtils;
import android.view.MotionEvent;
import android.view.View;
import android.view.accessibility.AccessibilityManager;
import android.view.animation.Interpolator;
import android.view.animation.PathInterpolator;
import com.android.internal.jank.InteractionJankMonitor;
import com.android.systemui.Gefingerpoken;
import com.android.systemui.R;
import com.android.systemui.animation.Interpolators;
import com.android.systemui.statusbar.notification.NotificationUtils;
import com.vivo.systemui.statusbar.notification.row.HoverModeHelper;

/* JADX INFO: loaded from: classes2.dex */
public abstract class ActivatableNotificationView extends ExpandableOutlineView {
    private static final float ALPHA_APPEAR_END_FRACTION = 1.0f;
    private static final float ALPHA_APPEAR_START_FRACTION = 0.4f;
    private static final float HORIZONTAL_ANIMATION_END = 0.2f;
    private static final float HORIZONTAL_ANIMATION_START = 1.0f;
    private static final float HORIZONTAL_COLLAPSED_REST_PARTIAL = 0.05f;
    protected static final int NO_COLOR = 0;
    private static final float VERTICAL_ANIMATION_START = 1.0f;
    private AccessibilityManager mAccessibilityManager;
    private boolean mActivated;
    private float mAnimationTranslationY;
    private float mAppearAnimationFraction;
    private float mAppearAnimationTranslation;
    private ValueAnimator mAppearAnimator;
    private ValueAnimator mBackgroundColorAnimator;
    NotificationBackgroundView mBackgroundNormal;
    int mBgTint;
    private Interpolator mCurrentAppearInterpolator;
    private int mCurrentBackgroundTint;
    private boolean mDismissed;
    private boolean mDrawingAppearAnimation;
    protected boolean mHeadsUpSimple;
    private boolean mIsAppearing;
    private boolean mIsBelowSpeedBump;
    private boolean mIsHeadsUpAnimation;
    private long mLastActionUpTime;
    private boolean mLastHideBgState;
    private float mNormalBackgroundVisibilityAmount;
    private int mNormalColor;
    private int mNormalRippleColor;
    private OnActivatedListener mOnActivatedListener;
    private float mOverrideAmount;
    private int mOverrideTint;
    private boolean mRefocusOnDismiss;
    private boolean mShadowHidden;
    private final Interpolator mSlowOutFastInInterpolator;
    private final Interpolator mSlowOutLinearInInterpolator;
    private int mStartTint;
    protected Point mTargetPoint;
    private int mTargetTint;
    private int mTintedRippleColor;
    private Gefingerpoken mTouchHandler;
    private static final Interpolator ACTIVATE_INVERSE_INTERPOLATOR = new PathInterpolator(0.6f, 0.0f, 0.5f, 1.0f);
    private static final Interpolator ACTIVATE_INVERSE_ALPHA_INTERPOLATOR = new PathInterpolator(0.0f, 0.0f, 0.5f, 1.0f);

    public interface OnActivatedListener {
        void onActivated(ActivatableNotificationView activatableNotificationView);

        void onActivationReset(ActivatableNotificationView activatableNotificationView);
    }

    protected boolean disallowSingleClick(MotionEvent motionEvent) {
        return false;
    }

    protected abstract View getContentView();

    protected boolean handleSlideBack() {
        return false;
    }

    public boolean hideBackground() {
        return false;
    }

    public boolean isHeadsUp() {
        return false;
    }

    public boolean isHeadsUpFullscreenSimpleWhenInflate() {
        return false;
    }

    protected boolean isInteractive() {
        return true;
    }

    protected void onAppearAnimationFinished(boolean z) {
    }

    protected void onBelowSpeedBumpChanged() {
    }

    public void onTap() {
    }

    protected void resetAllContentAlphas() {
    }

    protected void setTapTip(boolean z) {
    }

    public ActivatableNotificationView(Context context, AttributeSet attributeSet) {
        super(context, attributeSet);
        this.mBgTint = 0;
        this.mAppearAnimationFraction = -1.0f;
        this.mHeadsUpSimple = false;
        this.mSlowOutFastInInterpolator = new PathInterpolator(0.8f, 0.0f, 0.6f, 1.0f);
        this.mSlowOutLinearInInterpolator = new PathInterpolator(0.8f, 0.0f, 1.0f, 1.0f);
        setClipChildren(false);
        setClipToPadding(false);
        updateColors();
    }

    private void updateColors() {
        this.mNormalColor = this.mContext.getColor(R.color.vivo_notification_background_color);
        this.mTintedRippleColor = this.mContext.getColor(R.color.notification_ripple_tinted_color);
        this.mNormalRippleColor = this.mContext.getColor(R.color.notification_ripple_untinted_color);
    }

    public void updateBackgroundColors() {
        updateColors();
        initBackground();
        updateBackgroundTint();
    }

    public void setBackgroundWidth(int i) {
        NotificationBackgroundView notificationBackgroundView = this.mBackgroundNormal;
        if (notificationBackgroundView == null) {
            return;
        }
        notificationBackgroundView.setActualWidth(i);
    }

    @Override // com.android.systemui.statusbar.notification.row.ExpandableOutlineView, android.view.View
    protected void onFinishInflate() {
        super.onFinishInflate();
        this.mBackgroundNormal = (NotificationBackgroundView) findViewById(R.id.backgroundNormal);
        initBackground();
        updateBackgroundTint();
        updateOutlineAlpha();
    }

    protected void initBackground() {
        this.mBackgroundNormal.setCustomBackground(R.drawable.notification_material_bg);
    }

    protected void updateBackground() {
        updateHeadsUpRowBackground();
    }

    @Override // android.view.ViewGroup
    public boolean onInterceptTouchEvent(MotionEvent motionEvent) {
        Gefingerpoken gefingerpoken = this.mTouchHandler;
        if (gefingerpoken == null || !gefingerpoken.onInterceptTouchEvent(motionEvent)) {
            return super.onInterceptTouchEvent(motionEvent);
        }
        return true;
    }

    void setLastActionUpTime(long j) {
        this.mLastActionUpTime = j;
    }

    public long getAndResetLastActionUpTime() {
        long j = this.mLastActionUpTime;
        this.mLastActionUpTime = 0L;
        return j;
    }

    @Override // android.view.ViewGroup, android.view.View
    protected void drawableStateChanged() {
        super.drawableStateChanged();
        this.mBackgroundNormal.setState(getDrawableState());
    }

    void setRippleAllowed(boolean z) {
        this.mBackgroundNormal.setPressedAllowed(z);
    }

    void makeActive() {
        setTapTip(true);
        this.mActivated = true;
        OnActivatedListener onActivatedListener = this.mOnActivatedListener;
        if (onActivatedListener != null) {
            onActivatedListener.onActivated(this);
        }
    }

    public boolean isActive() {
        return this.mActivated;
    }

    public void makeInactive(boolean z) {
        setTapTip(false);
        if (this.mActivated) {
            this.mActivated = false;
        }
        OnActivatedListener onActivatedListener = this.mOnActivatedListener;
        if (onActivatedListener != null) {
            onActivatedListener.onActivationReset(this);
        }
    }

    private void updateOutlineAlpha() {
        setOutlineAlpha((0.3f * this.mNormalBackgroundVisibilityAmount) + 0.7f);
    }

    private void setNormalBackgroundVisibilityAmount(float f) {
        this.mNormalBackgroundVisibilityAmount = f;
        updateOutlineAlpha();
    }

    @Override // com.android.systemui.statusbar.notification.row.ExpandableView
    public void setBelowSpeedBump(boolean z) {
        super.setBelowSpeedBump(z);
        if (z != this.mIsBelowSpeedBump) {
            this.mIsBelowSpeedBump = z;
            updateBackgroundTint();
            onBelowSpeedBumpChanged();
        }
    }

    public boolean isBelowSpeedBump() {
        return this.mIsBelowSpeedBump;
    }

    protected void setTintColor(int i) {
        setTintColor(i, false);
    }

    void setTintColor(int i, boolean z) {
        if (i != this.mBgTint) {
            this.mBgTint = i;
            updateBackgroundTint(z);
        }
    }

    @Override // com.android.systemui.statusbar.notification.row.ExpandableOutlineView, com.android.systemui.statusbar.notification.row.ExpandableView
    public void setDistanceToTopRoundness(float f) {
        super.setDistanceToTopRoundness(f);
        this.mBackgroundNormal.setDistanceToTopRoundness(f);
    }

    public void setOverrideTintColor(int i, float f) {
        this.mOverrideTint = i;
        this.mOverrideAmount = f;
        setBackgroundTintColor(calculateBgColor());
    }

    protected void updateBackgroundTint() {
        updateBackgroundTint(false);
    }

    private void updateBackgroundTint(boolean z) {
        ValueAnimator valueAnimator = this.mBackgroundColorAnimator;
        if (valueAnimator != null) {
            valueAnimator.cancel();
        }
        this.mBackgroundNormal.setRippleColor(getRippleColor());
        int iCalculateBgColor = calculateBgColor();
        if (!z) {
            setBackgroundTintColor(iCalculateBgColor);
            return;
        }
        int i = this.mCurrentBackgroundTint;
        if (iCalculateBgColor != i) {
            this.mStartTint = i;
            this.mTargetTint = iCalculateBgColor;
            ValueAnimator valueAnimatorOfFloat = ValueAnimator.ofFloat(0.0f, 1.0f);
            this.mBackgroundColorAnimator = valueAnimatorOfFloat;
            valueAnimatorOfFloat.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() { // from class: com.android.systemui.statusbar.notification.row.-$$Lambda$ActivatableNotificationView$g1kb8VlKmUAgKQynf4ni8BbvTO4
                @Override // android.animation.ValueAnimator.AnimatorUpdateListener
                public final void onAnimationUpdate(ValueAnimator valueAnimator2) {
                    this.f$0.lambda$updateBackgroundTint$0$ActivatableNotificationView(valueAnimator2);
                }
            });
            this.mBackgroundColorAnimator.setDuration(360L);
            this.mBackgroundColorAnimator.setInterpolator(Interpolators.LINEAR);
            this.mBackgroundColorAnimator.addListener(new AnimatorListenerAdapter() { // from class: com.android.systemui.statusbar.notification.row.ActivatableNotificationView.1
                @Override // android.animation.AnimatorListenerAdapter, android.animation.Animator.AnimatorListener
                public void onAnimationEnd(Animator animator) {
                    ActivatableNotificationView.this.mBackgroundColorAnimator = null;
                }
            });
            this.mBackgroundColorAnimator.start();
        }
    }

    public /* synthetic */ void lambda$updateBackgroundTint$0$ActivatableNotificationView(ValueAnimator valueAnimator) {
        setBackgroundTintColor(NotificationUtils.interpolateColors(this.mStartTint, this.mTargetTint, valueAnimator.getAnimatedFraction()));
    }

    protected void setBackgroundTintColor(int i) {
        if (i != this.mCurrentBackgroundTint) {
            this.mCurrentBackgroundTint = i;
            if (i == this.mNormalColor) {
                i = 0;
            }
            this.mBackgroundNormal.setTint(i);
        }
    }

    protected void updateBackgroundClipping() {
        this.mBackgroundNormal.setBottomAmountClips(!isChildInGroup());
    }

    @Override // com.android.systemui.statusbar.notification.row.ExpandableView, android.widget.FrameLayout, android.view.ViewGroup, android.view.View
    protected void onLayout(boolean z, int i, int i2, int i3, int i4) {
        super.onLayout(z, i, i2, i3, i4);
        setPivotX(getWidth() / 2);
    }

    @Override // com.android.systemui.statusbar.notification.row.ExpandableOutlineView, com.android.systemui.statusbar.notification.row.ExpandableView
    public void setActualHeight(int i, boolean z) {
        super.setActualHeight(i, z);
        setPivotY(i / 2);
        this.mBackgroundNormal.setActualHeight(i);
    }

    @Override // com.android.systemui.statusbar.notification.row.ExpandableOutlineView, com.android.systemui.statusbar.notification.row.ExpandableView
    public void setClipTopAmount(int i) {
        super.setClipTopAmount(i);
        this.mBackgroundNormal.setClipTopAmount(i);
    }

    @Override // com.android.systemui.statusbar.notification.row.ExpandableOutlineView, com.android.systemui.statusbar.notification.row.ExpandableView
    public void setClipBottomAmount(int i) {
        super.setClipBottomAmount(i);
        this.mBackgroundNormal.setClipBottomAmount(i);
    }

    @Override // com.android.systemui.statusbar.notification.row.ExpandableView
    public long performRemoveAnimation(long j, long j2, float f, boolean z, float f2, Runnable runnable, AnimatorListenerAdapter animatorListenerAdapter) {
        enableAppearDrawing(true);
        this.mIsHeadsUpAnimation = z;
        if (this.mDrawingAppearAnimation) {
            startAppearAnimation(false, f, j2, j, runnable, animatorListenerAdapter);
            return 0L;
        }
        if (runnable == null) {
            return 0L;
        }
        runnable.run();
        return 0L;
    }

    @Override // com.android.systemui.statusbar.notification.row.ExpandableView
    public void performAddAnimation(long j, long j2, boolean z, Runnable runnable) {
        enableAppearDrawing(true);
        this.mIsHeadsUpAnimation = z;
        if (this.mDrawingAppearAnimation) {
            startAppearAnimation(true, 0.0f, j, j2, null, null);
        }
    }

    private void startAppearAnimation(final boolean z, float f, long j, long j2, final Runnable runnable, AnimatorListenerAdapter animatorListenerAdapter) {
        this.mAnimationTranslationY = f * getActualHeight();
        cancelAppearAnimation();
        float f2 = 0.0f;
        if (this.mAppearAnimationFraction == -1.0f) {
            if (z) {
                this.mAppearAnimationFraction = 0.0f;
                this.mAppearAnimationTranslation = this.mAnimationTranslationY;
            } else {
                this.mAppearAnimationFraction = 1.0f;
                this.mAppearAnimationTranslation = 0.0f;
            }
        }
        this.mIsAppearing = z;
        if (z) {
            this.mCurrentAppearInterpolator = Interpolators.FAST_OUT_SLOW_IN;
            if (this.mIsHeadsUpAnimation) {
                this.mCurrentAppearInterpolator = Interpolators.HEADS_UP_APPEAR;
            }
            f2 = 1.0f;
        } else {
            this.mCurrentAppearInterpolator = this.mSlowOutFastInInterpolator;
            if (this.mIsHeadsUpAnimation) {
                this.mCurrentAppearInterpolator = Interpolators.HEADS_UP_ALPHA_OUT;
            }
        }
        ValueAnimator valueAnimatorOfFloat = ValueAnimator.ofFloat(this.mAppearAnimationFraction, f2);
        this.mAppearAnimator = valueAnimatorOfFloat;
        valueAnimatorOfFloat.setInterpolator(Interpolators.LINEAR);
        this.mAppearAnimator.setDuration((long) (j2 * Math.abs(this.mAppearAnimationFraction - f2)));
        this.mAppearAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() { // from class: com.android.systemui.statusbar.notification.row.-$$Lambda$ActivatableNotificationView$tzTZb5iJRhe9TsCDPX-mkYNj4J4
            @Override // android.animation.ValueAnimator.AnimatorUpdateListener
            public final void onAnimationUpdate(ValueAnimator valueAnimator) {
                this.f$0.lambda$startAppearAnimation$1$ActivatableNotificationView(valueAnimator);
            }
        });
        if (animatorListenerAdapter != null) {
            this.mAppearAnimator.addListener(animatorListenerAdapter);
        }
        if (j > 0) {
            updateAppearAnimationAlpha();
            updateAppearRect();
            this.mAppearAnimator.setStartDelay(j);
        }
        if (this.mIsHeadsUpAnimation) {
            setContentAlpha(1.0f);
        }
        this.mAppearAnimator.addListener(new AnimatorListenerAdapter() { // from class: com.android.systemui.statusbar.notification.row.ActivatableNotificationView.2
            private boolean mWasCancelled;

            @Override // android.animation.AnimatorListenerAdapter, android.animation.Animator.AnimatorListener
            public void onAnimationEnd(Animator animator) {
                Runnable runnable2 = runnable;
                if (runnable2 != null) {
                    runnable2.run();
                }
                if (!this.mWasCancelled) {
                    ActivatableNotificationView.this.enableAppearDrawing(false);
                    ActivatableNotificationView.this.onAppearAnimationFinished(z);
                    InteractionJankMonitor.getInstance().end(ActivatableNotificationView.this.getCujType(z));
                } else {
                    InteractionJankMonitor.getInstance().cancel(ActivatableNotificationView.this.getCujType(z));
                }
                ActivatableNotificationView.this.setAlpha(1.0f);
                ActivatableNotificationView.this.setScaleX(1.0f);
                ActivatableNotificationView.this.setScaleY(1.0f);
            }

            @Override // android.animation.AnimatorListenerAdapter, android.animation.Animator.AnimatorListener
            public void onAnimationStart(Animator animator) {
                this.mWasCancelled = false;
                InteractionJankMonitor.getInstance().begin(InteractionJankMonitor.Configuration.Builder.withView(ActivatableNotificationView.this.getCujType(z), ActivatableNotificationView.this));
            }

            @Override // android.animation.AnimatorListenerAdapter, android.animation.Animator.AnimatorListener
            public void onAnimationCancel(Animator animator) {
                this.mWasCancelled = true;
            }
        });
        this.mAppearAnimator.start();
    }

    public /* synthetic */ void lambda$startAppearAnimation$1$ActivatableNotificationView(ValueAnimator valueAnimator) {
        this.mAppearAnimationFraction = ((Float) valueAnimator.getAnimatedValue()).floatValue();
        updateAppearAnimationAlpha();
        updateAppearRect();
        invalidate();
    }

    /* JADX INFO: Access modifiers changed from: private */
    public int getCujType(boolean z) {
        return this.mIsHeadsUpAnimation ? z ? 12 : 13 : z ? 14 : 15;
    }

    private void cancelAppearAnimation() {
        ValueAnimator valueAnimator = this.mAppearAnimator;
        if (valueAnimator != null) {
            valueAnimator.cancel();
            this.mAppearAnimator = null;
        }
    }

    public void cancelAppearDrawing() {
        cancelAppearAnimation();
        enableAppearDrawing(false);
    }

    private void updateAppearRect() {
        float interpolation = this.mCurrentAppearInterpolator.getInterpolation(this.mAppearAnimationFraction);
        this.mAppearAnimationTranslation = (1.0f - interpolation) * this.mAnimationTranslationY;
        float actualHeight = getActualHeight();
        float f = interpolation * actualHeight;
        float fMax = HoverModeHelper.getInstance(this.mContext).isInHoverMode() && (this.mIsAppearing || ((this instanceof ExpandableNotificationRow) && ((ExpandableNotificationRow) this).isUserReleaseHeadsUp())) ? Math.max((((HoverModeHelper.getInstance(this.mContext).getHeadsUpAppearTop() - HoverModeHelper.getInstance(this.mContext).getHeadsUpInset()) - r1) * getInterpolatedAppearAnimationFraction()) + actualHeight, 0.0f) : 0.0f;
        if (!this.mIsHeadsUpAnimation && !this.mIsAppearing) {
            fMax = this.mAppearAnimationTranslation;
        }
        float width = getWidth();
        if (!this.mIsHeadsUpAnimation && !this.mIsAppearing) {
            actualHeight = this.mAppearAnimationTranslation + f;
        }
        setOutlineRect(0.0f, fMax, width, actualHeight);
    }

    private float getInterpolatedAppearAnimationFraction() {
        float f = this.mAppearAnimationFraction;
        if (f >= 0.0f) {
            return this.mCurrentAppearInterpolator.getInterpolation(f);
        }
        return 1.0f;
    }

    private void updateAppearAnimationAlpha() {
        if (this.mIsHeadsUpAnimation || this.mIsAppearing) {
            float interpolatedAppearAnimationFraction = getInterpolatedAppearAnimationFraction();
            setAlpha(interpolatedAppearAnimationFraction);
            if (this.mIsHeadsUpAnimation) {
                return;
            }
            setScaleX(interpolatedAppearAnimationFraction);
            setScaleY(interpolatedAppearAnimationFraction);
            return;
        }
        setContentAlpha(Interpolators.ALPHA_IN.getInterpolation((MathUtils.constrain(this.mAppearAnimationFraction, ALPHA_APPEAR_START_FRACTION, 1.0f) - ALPHA_APPEAR_START_FRACTION) / 0.6f));
    }

    private void setContentAlpha(float f) {
        View contentView = getContentView();
        if (contentView.hasOverlappingRendering()) {
            contentView.setLayerType((f == 0.0f || f == 1.0f) ? 0 : 2, null);
        }
        contentView.setAlpha(f);
        if (f == 1.0f) {
            resetAllContentAlphas();
        }
    }

    @Override // com.android.systemui.statusbar.notification.row.ExpandableOutlineView
    protected void applyRoundness() {
        super.applyRoundness();
        applyBackgroundRoundness(getCurrentBackgroundRadiusTop(), getCurrentBackgroundRadiusBottom());
    }

    private void applyBackgroundRoundness(float f, float f2) {
        this.mBackgroundNormal.setRadius(f, f2);
    }

    @Override // com.android.systemui.statusbar.notification.row.ExpandableOutlineView
    protected void setBackgroundTop(int i) {
        this.mBackgroundNormal.setBackgroundTop(i);
    }

    public int calculateBgColor() {
        return calculateBgColor(true, true);
    }

    @Override // com.android.systemui.statusbar.notification.row.ExpandableOutlineView
    protected boolean childNeedsClipping(View view) {
        if (view instanceof NotificationBackgroundView) {
            return true;
        }
        return super.childNeedsClipping(view);
    }

    private int calculateBgColor(boolean z, boolean z2) {
        int i;
        if (!z2 || this.mOverrideTint == 0) {
            return (!z || (i = this.mBgTint) == 0) ? this.mNormalColor : i;
        }
        return NotificationUtils.interpolateColors(calculateBgColor(z, false), this.mOverrideTint, this.mOverrideAmount);
    }

    private int getRippleColor() {
        if (this.mBgTint != 0) {
            return this.mTintedRippleColor;
        }
        return this.mNormalRippleColor;
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void enableAppearDrawing(boolean z) {
        if (z != this.mDrawingAppearAnimation) {
            this.mDrawingAppearAnimation = z;
            if (!z) {
                setContentAlpha(1.0f);
                this.mAppearAnimationFraction = -1.0f;
                setOutlineRect(null);
            }
            invalidate();
        }
    }

    public boolean isDrawingAppearAnimation() {
        return this.mDrawingAppearAnimation;
    }

    @Override // android.view.ViewGroup, android.view.View
    protected void dispatchDraw(Canvas canvas) {
        if (this.mDrawingAppearAnimation) {
            canvas.save();
            canvas.translate(0.0f, this.mAppearAnimationTranslation);
        }
        super.dispatchDraw(canvas);
        if (this.mDrawingAppearAnimation) {
            canvas.restore();
        }
    }

    public void setOnActivatedListener(OnActivatedListener onActivatedListener) {
        this.mOnActivatedListener = onActivatedListener;
    }

    @Override // com.android.systemui.statusbar.notification.row.ExpandableView
    public void setFakeShadowIntensity(float f, float f2, int i, int i2) {
        this.mShadowHidden = f == 0.0f;
    }

    public int getBackgroundColorWithoutTint() {
        return calculateBgColor(false, false);
    }

    public int getCurrentBackgroundTint() {
        return this.mCurrentBackgroundTint;
    }

    @Override // com.android.systemui.statusbar.notification.row.ExpandableView
    public int getHeadsUpHeightWithoutHeader() {
        return getHeight();
    }

    public void dismiss(boolean z) {
        this.mDismissed = true;
        this.mRefocusOnDismiss = z;
    }

    public void unDismiss() {
        this.mDismissed = false;
    }

    public boolean isDismissed() {
        return this.mDismissed;
    }

    public boolean shouldRefocusOnDismiss() {
        return this.mRefocusOnDismiss || isAccessibilityFocused();
    }

    void setTouchHandler(Gefingerpoken gefingerpoken) {
        this.mTouchHandler = gefingerpoken;
    }

    public void setAccessibilityManager(AccessibilityManager accessibilityManager) {
        this.mAccessibilityManager = accessibilityManager;
    }

    public void setHeadsUpSimple(boolean z) {
        if (this.mHeadsUpSimple != z && isHeadsUp()) {
            this.mHeadsUpSimple = z;
            updateHeadsUpRowBackground();
        } else {
            this.mHeadsUpSimple = z;
        }
    }

    public boolean isHeadsUpSimple() {
        return this.mHeadsUpSimple;
    }

    public void updateHeadsUpRowBackground() {
        if (isHeadsUp()) {
            setTranslationZ(99.0f);
            boolean z = this instanceof ExpandableNotificationRow;
            int i = R.drawable.vivo_heads_up_notification_simple_bg;
            if (z) {
                ExpandableNotificationRow expandableNotificationRow = (ExpandableNotificationRow) this;
                NotificationBackgroundView notificationBackgroundView = this.mBackgroundNormal;
                if (!this.mHeadsUpSimple || expandableNotificationRow.shouldShowPublic()) {
                    i = R.drawable.vivo_heads_up_notification_bg;
                }
                notificationBackgroundView.setCustomBackground(i);
            } else {
                NotificationBackgroundView notificationBackgroundView2 = this.mBackgroundNormal;
                if (!this.mHeadsUpSimple) {
                    i = R.drawable.vivo_heads_up_notification_bg;
                }
                notificationBackgroundView2.setCustomBackground(i);
            }
        } else {
            setTranslationZ(0.0f);
            this.mBackgroundNormal.setCustomBackground(R.drawable.notification_material_bg);
        }
        this.mBackgroundNormal.setAlpha(1.0f);
        this.mBackgroundNormal.setVisibility(hideBackground() ? 4 : 0);
        this.mLastHideBgState = hideBackground();
    }

    @Override // android.view.ViewGroup, android.view.View
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (this.mLastHideBgState != hideBackground()) {
            updateHeadsUpRowBackground();
        }
    }

    public View getBackgroundNormal() {
        return this.mBackgroundNormal;
    }
}
