package com.vivo.systemui.statusbar.ui;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.BlurMaskFilter;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PointF;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.animation.PathInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import com.android.systemui.Dependency;
import com.android.systemui.res.R;
import com.android.systemui.statusbar.phone.CentralSurfaces;
import com.vivo.systemui.navigation.NavigationConfiguration;
import com.vivo.systemui.statusbar.network.VivoFlowDisplayView;
import com.vivo.systemui.statusbar.utils.DisplayUtils;
import com.vivo.systemui.statusbar.utils.FoldedStateMonitorUtils;

/* JADX INFO: loaded from: classes3.dex */
public class NotificationClearAllImageView extends ImageView {
    private float mAnimatorAlpha;
    private ValueAnimator mClearAllpressAnimator;
    private ValueAnimator mClearAllpressAppear;
    private ValueAnimator mClearAllpressDisAnimator;
    private int mIconPadding;
    private int mIconSize;
    private Paint mPaint;
    private float mParentAlpha;

    public NotificationClearAllImageView(Context context) {
        super(context);
        this.mParentAlpha = 1.0f;
        this.mAnimatorAlpha = 1.0f;
    }

    public NotificationClearAllImageView(Context context, AttributeSet attributeSet) {
        super(context, attributeSet);
        this.mParentAlpha = 1.0f;
        this.mAnimatorAlpha = 1.0f;
    }

    public NotificationClearAllImageView(Context context, AttributeSet attributeSet, int i) {
        super(context, attributeSet, i);
        this.mParentAlpha = 1.0f;
        this.mAnimatorAlpha = 1.0f;
    }

    public NotificationClearAllImageView(Context context, AttributeSet attributeSet, int i, int i2) {
        super(context, attributeSet, i, i2);
        this.mParentAlpha = 1.0f;
        this.mAnimatorAlpha = 1.0f;
    }

    public void startClearAllButtonDisnimator() {
        if (getVisibility() == 0) {
            ValueAnimator valueAnimator = this.mClearAllpressAppear;
            if (valueAnimator != null && valueAnimator.isRunning()) {
                this.mClearAllpressAppear.cancel();
            }
            if (this.mClearAllpressDisAnimator == null) {
                ValueAnimator valueAnimatorOfFloat = ValueAnimator.ofFloat(1.0f, 0.0f);
                this.mClearAllpressDisAnimator = valueAnimatorOfFloat;
                valueAnimatorOfFloat.setDuration(150L).setInterpolator(new PathInterpolator(0.25f, 0.45f, 0.3f, 1.0f));
                this.mClearAllpressDisAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() { // from class: com.vivo.systemui.statusbar.ui.NotificationClearAllImageView$$ExternalSyntheticLambda3
                    @Override // android.animation.ValueAnimator.AnimatorUpdateListener
                    public final void onAnimationUpdate(ValueAnimator valueAnimator2) {
                        this.f$0.lambda$startClearAllButtonDisnimator$0(valueAnimator2);
                    }
                });
                this.mClearAllpressDisAnimator.addListener(new AnimatorListenerAdapter() { // from class: com.vivo.systemui.statusbar.ui.NotificationClearAllImageView.1
                    @Override // android.animation.AnimatorListenerAdapter, android.animation.Animator.AnimatorListener
                    public void onAnimationEnd(Animator animator) {
                        super.onAnimationEnd(animator);
                        NotificationClearAllImageView.this.setScaleX(1.0f);
                        NotificationClearAllImageView.this.setScaleY(1.0f);
                    }
                });
            }
            this.mClearAllpressDisAnimator.start();
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$startClearAllButtonDisnimator$0(ValueAnimator valueAnimator) {
        float fFloatValue = ((Float) valueAnimator.getAnimatedValue()).floatValue();
        this.mAnimatorAlpha = fFloatValue;
        setAlpha(fFloatValue * this.mParentAlpha);
    }

    public void startClearAllButtonAppear() {
        ValueAnimator valueAnimator = this.mClearAllpressDisAnimator;
        if (valueAnimator != null && valueAnimator.isRunning()) {
            this.mClearAllpressDisAnimator.cancel();
        }
        ValueAnimator valueAnimator2 = this.mClearAllpressAppear;
        if (valueAnimator2 == null || !valueAnimator2.isRunning()) {
            if (this.mClearAllpressAppear == null) {
                ValueAnimator valueAnimatorOfFloat = ValueAnimator.ofFloat(0.0f, 1.0f);
                this.mClearAllpressAppear = valueAnimatorOfFloat;
                valueAnimatorOfFloat.setDuration(150L).setInterpolator(new PathInterpolator(0.25f, 0.45f, 0.3f, 1.0f));
                this.mClearAllpressAppear.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() { // from class: com.vivo.systemui.statusbar.ui.NotificationClearAllImageView$$ExternalSyntheticLambda0
                    @Override // android.animation.ValueAnimator.AnimatorUpdateListener
                    public final void onAnimationUpdate(ValueAnimator valueAnimator3) {
                        this.f$0.lambda$startClearAllButtonAppear$1(valueAnimator3);
                    }
                });
            }
            this.mClearAllpressAppear.start();
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$startClearAllButtonAppear$1(ValueAnimator valueAnimator) {
        float fFloatValue = ((Float) valueAnimator.getAnimatedValue()).floatValue();
        this.mAnimatorAlpha = fFloatValue;
        setAlpha(fFloatValue * this.mParentAlpha);
    }

    public void setAlphaForParent(float f) {
        this.mParentAlpha = f;
        setAlpha(this.mAnimatorAlpha * f);
    }

    public float getAnimatorAlpha() {
        return this.mAnimatorAlpha;
    }

    @Override // android.view.View
    public boolean onTouchEvent(MotionEvent motionEvent) {
        int action = motionEvent.getAction();
        if (action == 0) {
            ValueAnimator valueAnimator = this.mClearAllpressAnimator;
            if (valueAnimator == null) {
                ValueAnimator valueAnimatorOfFloat = ValueAnimator.ofFloat(1.0f, 0.0f);
                this.mClearAllpressAnimator = valueAnimatorOfFloat;
                valueAnimatorOfFloat.setDuration(200L).setInterpolator(new PathInterpolator(0.25f, 0.45f, 0.3f, 1.0f));
            } else {
                if (valueAnimator.isRunning()) {
                    this.mClearAllpressAnimator.cancel();
                }
                setScaleX(1.0f);
                setScaleY(1.0f);
            }
            this.mClearAllpressAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() { // from class: com.vivo.systemui.statusbar.ui.NotificationClearAllImageView$$ExternalSyntheticLambda1
                @Override // android.animation.ValueAnimator.AnimatorUpdateListener
                public final void onAnimationUpdate(ValueAnimator valueAnimator2) {
                    this.f$0.lambda$onTouchEvent$2(valueAnimator2);
                }
            });
            this.mClearAllpressAnimator.start();
        } else if ((action == 1 || action == 3) && !pointIsInsideOfViewBounds(convertPointFromScreenSpaceToViewSpace(motionEvent.getRawX(), motionEvent.getRawY()))) {
            ValueAnimator valueAnimator2 = this.mClearAllpressAnimator;
            if (valueAnimator2 == null) {
                ValueAnimator valueAnimatorOfFloat2 = ValueAnimator.ofFloat(0.0f, 1.0f);
                this.mClearAllpressAnimator = valueAnimatorOfFloat2;
                valueAnimatorOfFloat2.setDuration(200L).setInterpolator(new PathInterpolator(0.25f, 0.45f, 0.3f, 1.0f));
            } else if (valueAnimator2.isRunning()) {
                this.mClearAllpressAnimator.cancel();
            }
            final float scaleX = getScaleX();
            final float scaleY = getScaleY();
            this.mClearAllpressAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() { // from class: com.vivo.systemui.statusbar.ui.NotificationClearAllImageView$$ExternalSyntheticLambda2
                @Override // android.animation.ValueAnimator.AnimatorUpdateListener
                public final void onAnimationUpdate(ValueAnimator valueAnimator3) {
                    this.f$0.lambda$onTouchEvent$3(scaleX, scaleY, valueAnimator3);
                }
            });
            this.mClearAllpressAnimator.start();
        }
        return super.onTouchEvent(motionEvent);
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$onTouchEvent$2(ValueAnimator valueAnimator) {
        setScaleX((float) (1.0d - (((double) valueAnimator.getAnimatedFraction()) * 0.1d)));
        setScaleY((float) (1.0d - (((double) valueAnimator.getAnimatedFraction()) * 0.1d)));
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$onTouchEvent$3(float f, float f2, ValueAnimator valueAnimator) {
        setScaleX(f + (valueAnimator.getAnimatedFraction() * (1.0f - f)));
        setScaleY(f2 + (valueAnimator.getAnimatedFraction() * (1.0f - f2)));
    }

    public PointF convertPointFromScreenSpaceToViewSpace(float f, float f2) {
        getLocationInWindow(new int[2]);
        return new PointF(f - r0[0], f2 - r0[1]);
    }

    public boolean pointIsInsideOfViewBounds(PointF pointF) {
        float f = pointF.x;
        if (f >= 0.0f && f <= getWidth()) {
            float f2 = pointF.y;
            if (f2 >= 0.0f && f2 <= getHeight()) {
                return true;
            }
        }
        return false;
    }

    public void setClearAllButtonLocation(CentralSurfaces centralSurfaces, VivoFlowDisplayView vivoFlowDisplayView, Configuration configuration) {
        boolean zIsUsingNavigationBar;
        int dimensionPixelSize;
        int dimensionPixelSize2;
        int currRotation;
        FrameLayout.LayoutParams layoutParams = (FrameLayout.LayoutParams) getLayoutParams();
        boolean z = configuration != null ? configuration.orientation == 2 : (currRotation = DisplayUtils.getInstance(((ImageView) this).mContext).getCurrRotation()) == 1 || currRotation == 3;
        if (centralSurfaces != null) {
            zIsUsingNavigationBar = centralSurfaces.isNavigationBarVisible();
        } else {
            zIsUsingNavigationBar = NavigationConfiguration.getInstance(((ImageView) this).mContext).isUsingNavigationBar();
        }
        if (z && ((FoldedStateMonitorUtils) Dependency.get(FoldedStateMonitorUtils.class)).isFoldState()) {
            layoutParams.gravity = 21;
            if (zIsUsingNavigationBar) {
                dimensionPixelSize2 = getResources().getDimensionPixelSize(R.dimen.vivo_notification_clear_all_margin_right_with_navigationbar);
            } else {
                dimensionPixelSize2 = getResources().getDimensionPixelSize(R.dimen.vivo_notification_clear_all_margin_right);
            }
            layoutParams.rightMargin = dimensionPixelSize2;
            layoutParams.bottomMargin = 0;
        } else {
            boolean z2 = vivoFlowDisplayView != null && vivoFlowDisplayView.getVisibility() == 0;
            layoutParams.gravity = 81;
            if (!zIsUsingNavigationBar) {
                dimensionPixelSize = getResources().getDimensionPixelSize(R.dimen.vivo_notification_clear_all_margin_bottom);
            } else if (z2) {
                dimensionPixelSize = getResources().getDimensionPixelSize(R.dimen.vivo_notification_clear_all_margin_bottom_with_navigationbar_with_flowview);
            } else {
                dimensionPixelSize = getResources().getDimensionPixelSize(R.dimen.vivo_notification_clear_all_margin_bottom_with_navigationbar);
            }
            layoutParams.bottomMargin = dimensionPixelSize;
            layoutParams.rightMargin = 0;
        }
        Context localContext = ((FoldedStateMonitorUtils) Dependency.get(FoldedStateMonitorUtils.class)).getLocalContext();
        this.mIconSize = localContext.getResources().getDimensionPixelSize(R.dimen.vivo_notification_clear_all_size);
        this.mIconPadding = localContext.getResources().getDimensionPixelSize(R.dimen.vivo_notification_clear_all_padding);
        int i = this.mIconSize;
        layoutParams.width = i;
        layoutParams.height = i;
        setLayoutParams(layoutParams);
        int i2 = this.mIconPadding;
        setPadding(i2, i2, i2, i2);
    }

    @Override // android.widget.ImageView, android.view.View
    public void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        canvas.save();
        if (this.mPaint == null) {
            Paint paint = new Paint();
            this.mPaint = paint;
            paint.setColor(getResources().getColor(R.color.vivo_notification_clear_all_shadow));
            this.mPaint.setMaskFilter(new BlurMaskFilter(45.0f, BlurMaskFilter.Blur.OUTER));
        }
        int i = this.mIconSize;
        canvas.drawCircle(i / 2, i / 2, (i / 2) - this.mIconPadding, this.mPaint);
        canvas.restore();
    }
}
