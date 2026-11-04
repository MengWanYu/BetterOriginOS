package com.android.systemui.media;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ValueAnimator;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Rect;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.util.MathUtils;
import androidx.annotation.Keep;
import com.android.internal.graphics.ColorUtils;
import com.android.systemui.Interpolators;
import com.android.systemui.R;
import kotlin.Metadata;
import kotlin.TypeCastException;
import kotlin.jvm.internal.Intrinsics;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.xmlpull.v1.XmlPullParser;
import vivo.app.epm.Switch;

/* JADX INFO: compiled from: LightSourceDrawable.kt */
/* JADX INFO: loaded from: classes2.dex */
@Keep
@Metadata(bv = {1, 0, 3}, d1 = {"\u0000\u0082\u0001\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0010\u000b\n\u0002\b\u0003\n\u0002\u0010\b\n\u0002\b\u0006\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u0015\n\u0000\n\u0002\u0010\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0018\u0002\n\u0002\b\u0005\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\b\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0010\u0007\n\u0002\b\u0003\n\u0002\u0018\u0002\n\u0000\b\u0007\u0018\u00002\u00020\u0001B\u0005¢\u0006\u0002\u0010\u0002J\u0014\u0010\u0017\u001a\u00020\u00182\n\u0010\u0019\u001a\u00060\u001aR\u00020\u001bH\u0016J\b\u0010\u001c\u001a\u00020\u0004H\u0016J\u0010\u0010\u001d\u001a\u00020\u00182\u0006\u0010\u001e\u001a\u00020\u001fH\u0016J\b\u0010 \u001a\u00020!H\u0016J\b\u0010\"\u001a\u00020\bH\u0016J\u0010\u0010#\u001a\u00020\u00182\u0006\u0010$\u001a\u00020%H\u0016J\b\u0010&\u001a\u00020\u0004H\u0016J\b\u0010'\u001a\u00020\u0018H\u0002J.\u0010(\u001a\u00020\u00182\u0006\u0010)\u001a\u00020\u001b2\u0006\u0010*\u001a\u00020+2\u0006\u0010,\u001a\u00020-2\f\u0010.\u001a\b\u0018\u00010\u001aR\u00020\u001bH\u0016J\b\u0010/\u001a\u00020\u0004H\u0016J\b\u00100\u001a\u00020\u0004H\u0016J\u0012\u00101\u001a\u00020\u00042\b\u00102\u001a\u0004\u0018\u00010\u0016H\u0014J\u0010\u00103\u001a\u00020\u00182\u0006\u0010\u0003\u001a\u00020\bH\u0016J\u0012\u00104\u001a\u00020\u00182\b\u00105\u001a\u0004\u0018\u000106H\u0016J\u0018\u00107\u001a\u00020\u00182\u0006\u00108\u001a\u0002092\u0006\u0010:\u001a\u000209H\u0016J\u0010\u0010;\u001a\u00020\u00182\u0006\u0010<\u001a\u00020=H\u0002R\u001e\u0010\u0005\u001a\u00020\u00042\u0006\u0010\u0003\u001a\u00020\u0004@BX\u0082\u000e¢\u0006\b\n\u0000\"\u0004\b\u0006\u0010\u0007R$\u0010\t\u001a\u00020\b2\u0006\u0010\u0003\u001a\u00020\b@FX\u0086\u000e¢\u0006\u000e\n\u0000\u001a\u0004\b\n\u0010\u000b\"\u0004\b\f\u0010\rR\u000e\u0010\u000e\u001a\u00020\u000fX\u0082\u000e¢\u0006\u0002\n\u0000R\u000e\u0010\u0010\u001a\u00020\u0004X\u0082\u000e¢\u0006\u0002\n\u0000R\u0010\u0010\u0011\u001a\u0004\u0018\u00010\u0012X\u0082\u000e¢\u0006\u0002\n\u0000R\u000e\u0010\u0013\u001a\u00020\u0014X\u0082\u0004¢\u0006\u0002\n\u0000R\u0010\u0010\u0015\u001a\u0004\u0018\u00010\u0016X\u0082\u000e¢\u0006\u0002\n\u0000¨\u0006>"}, d2 = {"Lcom/android/systemui/media/LightSourceDrawable;", "Landroid/graphics/drawable/Drawable;", "()V", Switch.SWITCH_ATTR_VALUE, "", "active", "setActive", "(Z)V", "", "highlightColor", "getHighlightColor", "()I", "setHighlightColor", "(I)V", "paint", "Landroid/graphics/Paint;", "pressed", "rippleAnimation", "Landroid/animation/Animator;", "rippleData", "Lcom/android/systemui/media/RippleData;", "themeAttrs", "", "applyTheme", "", "t", "Landroid/content/res/Resources$Theme;", "Landroid/content/res/Resources;", "canApplyTheme", "draw", "canvas", "Landroid/graphics/Canvas;", "getDirtyBounds", "Landroid/graphics/Rect;", "getOpacity", "getOutline", "outline", "Landroid/graphics/Outline;", "hasFocusStateSpecified", "illuminate", "inflate", "r", "parser", "Lorg/xmlpull/v1/XmlPullParser;", "attrs", "Landroid/util/AttributeSet;", "theme", "isProjected", "isStateful", "onStateChange", "stateSet", "setAlpha", "setColorFilter", "p0", "Landroid/graphics/ColorFilter;", "setHotspot", "x", "", "y", "updateStateFromTypedArray", "a", "Landroid/content/res/TypedArray;", "app_xxhdpiRatio_20_9Rom_13_0DemesticAndroid_30Release"}, k = 1, mv = {1, 1, 16})
public final class LightSourceDrawable extends Drawable {
    private boolean active;
    private boolean pressed;
    private Animator rippleAnimation;
    private int[] themeAttrs;
    private final RippleData rippleData = new RippleData(0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f);
    private Paint paint = new Paint();
    private int highlightColor = -1;

    @Override // android.graphics.drawable.Drawable
    public int getOpacity() {
        return -2;
    }

    @Override // android.graphics.drawable.Drawable
    public void getOutline(@NotNull Outline outline) {
        Intrinsics.checkParameterIsNotNull(outline, "outline");
    }

    @Override // android.graphics.drawable.Drawable
    public boolean hasFocusStateSpecified() {
        return true;
    }

    @Override // android.graphics.drawable.Drawable
    public boolean isProjected() {
        return true;
    }

    @Override // android.graphics.drawable.Drawable
    public boolean isStateful() {
        return true;
    }

    public final int getHighlightColor() {
        return this.highlightColor;
    }

    public final void setHighlightColor(int i) {
        if (this.highlightColor == i) {
            return;
        }
        this.highlightColor = i;
        invalidateSelf();
    }

    private final void setActive(boolean z) {
        if (z == this.active) {
            return;
        }
        this.active = z;
        if (z) {
            Animator animator = this.rippleAnimation;
            if (animator != null) {
                animator.cancel();
            }
            this.rippleData.setAlpha(1.0f);
            this.rippleData.setProgress(0.05f);
        } else {
            Animator animator2 = this.rippleAnimation;
            if (animator2 != null) {
                animator2.cancel();
            }
            ValueAnimator valueAnimatorOfFloat = ValueAnimator.ofFloat(this.rippleData.getAlpha(), 0.0f);
            valueAnimatorOfFloat.setDuration(200L);
            valueAnimatorOfFloat.setInterpolator(Interpolators.LINEAR_OUT_SLOW_IN);
            valueAnimatorOfFloat.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() { // from class: com.android.systemui.media.LightSourceDrawable$active$$inlined$apply$lambda$1
                @Override // android.animation.ValueAnimator.AnimatorUpdateListener
                public final void onAnimationUpdate(ValueAnimator it) {
                    RippleData rippleData = this.this$0.rippleData;
                    Intrinsics.checkExpressionValueIsNotNull(it, "it");
                    Object animatedValue = it.getAnimatedValue();
                    if (animatedValue == null) {
                        throw new TypeCastException("null cannot be cast to non-null type kotlin.Float");
                    }
                    rippleData.setAlpha(((Float) animatedValue).floatValue());
                    this.this$0.invalidateSelf();
                }
            });
            valueAnimatorOfFloat.addListener(new AnimatorListenerAdapter() { // from class: com.android.systemui.media.LightSourceDrawable$active$$inlined$apply$lambda$2
                private boolean cancelled;

                public final boolean getCancelled() {
                    return this.cancelled;
                }

                public final void setCancelled(boolean z2) {
                    this.cancelled = z2;
                }

                @Override // android.animation.AnimatorListenerAdapter, android.animation.Animator.AnimatorListener
                public void onAnimationCancel(@Nullable Animator animator3) {
                    this.cancelled = true;
                }

                @Override // android.animation.AnimatorListenerAdapter, android.animation.Animator.AnimatorListener
                public void onAnimationEnd(@Nullable Animator animator3) {
                    if (this.cancelled) {
                        return;
                    }
                    this.this$0.rippleData.setProgress(0.0f);
                    this.this$0.rippleData.setAlpha(0.0f);
                    this.this$0.rippleAnimation = (Animator) null;
                    this.this$0.invalidateSelf();
                }
            });
            valueAnimatorOfFloat.start();
            this.rippleAnimation = valueAnimatorOfFloat;
        }
        invalidateSelf();
    }

    @Override // android.graphics.drawable.Drawable
    public void draw(@NotNull Canvas canvas) {
        Intrinsics.checkParameterIsNotNull(canvas, "canvas");
        float fLerp = MathUtils.lerp(this.rippleData.getMinSize(), this.rippleData.getMaxSize(), this.rippleData.getProgress());
        this.paint.setShader(new RadialGradient(this.rippleData.getX(), this.rippleData.getY(), fLerp, new int[]{ColorUtils.setAlphaComponent(this.highlightColor, (int) (this.rippleData.getAlpha() * 255)), 0}, LightSourceDrawableKt.GRADIENT_STOPS, Shader.TileMode.CLAMP));
        canvas.drawCircle(this.rippleData.getX(), this.rippleData.getY(), fLerp, this.paint);
    }

    @Override // android.graphics.drawable.Drawable
    public void inflate(@NotNull Resources r, @NotNull XmlPullParser parser, @NotNull AttributeSet attrs, @Nullable Resources.Theme theme) {
        Intrinsics.checkParameterIsNotNull(r, "r");
        Intrinsics.checkParameterIsNotNull(parser, "parser");
        Intrinsics.checkParameterIsNotNull(attrs, "attrs");
        TypedArray a = Drawable.obtainAttributes(r, theme, attrs, R.styleable.IlluminationDrawable);
        this.themeAttrs = a.extractThemeAttrs();
        Intrinsics.checkExpressionValueIsNotNull(a, "a");
        updateStateFromTypedArray(a);
        a.recycle();
    }

    private final void updateStateFromTypedArray(TypedArray a) {
        if (a.hasValue(3)) {
            this.rippleData.setMinSize(a.getDimension(3, 0.0f));
        }
        if (a.hasValue(2)) {
            this.rippleData.setMaxSize(a.getDimension(2, 0.0f));
        }
        if (a.hasValue(1)) {
            this.rippleData.setHighlight(a.getInteger(1, 0) / 100.0f);
        }
    }

    /* JADX WARN: Removed duplicated region for block: B:8:0x000c  */
    @Override // android.graphics.drawable.Drawable
    /*
        Code decompiled incorrectly, please refer to instructions dump.
        To view partially-correct add '--show-bad-code' argument
    */
    public boolean canApplyTheme() {
        /*
            r1 = this;
            int[] r0 = r1.themeAttrs
            if (r0 == 0) goto Lc
            if (r0 != 0) goto L9
            kotlin.jvm.internal.Intrinsics.throwNpe()
        L9:
            int r0 = r0.length
            if (r0 > 0) goto L12
        Lc:
            boolean r1 = super.canApplyTheme()
            if (r1 == 0) goto L14
        L12:
            r1 = 1
            goto L15
        L14:
            r1 = 0
        L15:
            return r1
        */
        throw new UnsupportedOperationException("Method not decompiled: com.android.systemui.media.LightSourceDrawable.canApplyTheme():boolean");
    }

    @Override // android.graphics.drawable.Drawable
    public void applyTheme(@NotNull Resources.Theme t) {
        Intrinsics.checkParameterIsNotNull(t, "t");
        super.applyTheme(t);
        int[] iArr = this.themeAttrs;
        if (iArr != null) {
            TypedArray a = t.resolveAttributes(iArr, R.styleable.IlluminationDrawable);
            Intrinsics.checkExpressionValueIsNotNull(a, "a");
            updateStateFromTypedArray(a);
            a.recycle();
        }
    }

    @Override // android.graphics.drawable.Drawable
    public void setColorFilter(@Nullable ColorFilter p0) {
        throw new UnsupportedOperationException("Color filters are not supported");
    }

    @Override // android.graphics.drawable.Drawable
    public void setAlpha(int value) {
        throw new UnsupportedOperationException("Alpha is not supported");
    }

    private final void illuminate() {
        this.rippleData.setAlpha(1.0f);
        invalidateSelf();
        Animator animator = this.rippleAnimation;
        if (animator != null) {
            animator.cancel();
        }
        AnimatorSet animatorSet = new AnimatorSet();
        ValueAnimator valueAnimatorOfFloat = ValueAnimator.ofFloat(1.0f, 0.0f);
        valueAnimatorOfFloat.setStartDelay(133L);
        valueAnimatorOfFloat.setDuration(800 - valueAnimatorOfFloat.getStartDelay());
        valueAnimatorOfFloat.setInterpolator(Interpolators.LINEAR_OUT_SLOW_IN);
        valueAnimatorOfFloat.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() { // from class: com.android.systemui.media.LightSourceDrawable$illuminate$$inlined$apply$lambda$1
            @Override // android.animation.ValueAnimator.AnimatorUpdateListener
            public final void onAnimationUpdate(ValueAnimator it) {
                RippleData rippleData = this.this$0.rippleData;
                Intrinsics.checkExpressionValueIsNotNull(it, "it");
                Object animatedValue = it.getAnimatedValue();
                if (animatedValue == null) {
                    throw new TypeCastException("null cannot be cast to non-null type kotlin.Float");
                }
                rippleData.setAlpha(((Float) animatedValue).floatValue());
                this.this$0.invalidateSelf();
            }
        });
        ValueAnimator valueAnimatorOfFloat2 = ValueAnimator.ofFloat(this.rippleData.getProgress(), 1.0f);
        valueAnimatorOfFloat2.setDuration(800L);
        valueAnimatorOfFloat2.setInterpolator(Interpolators.LINEAR_OUT_SLOW_IN);
        valueAnimatorOfFloat2.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() { // from class: com.android.systemui.media.LightSourceDrawable$illuminate$$inlined$apply$lambda$2
            @Override // android.animation.ValueAnimator.AnimatorUpdateListener
            public final void onAnimationUpdate(ValueAnimator it) {
                RippleData rippleData = this.this$0.rippleData;
                Intrinsics.checkExpressionValueIsNotNull(it, "it");
                Object animatedValue = it.getAnimatedValue();
                if (animatedValue == null) {
                    throw new TypeCastException("null cannot be cast to non-null type kotlin.Float");
                }
                rippleData.setProgress(((Float) animatedValue).floatValue());
                this.this$0.invalidateSelf();
            }
        });
        animatorSet.playTogether(valueAnimatorOfFloat, valueAnimatorOfFloat2);
        animatorSet.addListener(new AnimatorListenerAdapter() { // from class: com.android.systemui.media.LightSourceDrawable$illuminate$$inlined$apply$lambda$3
            @Override // android.animation.AnimatorListenerAdapter, android.animation.Animator.AnimatorListener
            public void onAnimationEnd(@Nullable Animator animation) {
                this.this$0.rippleData.setProgress(0.0f);
                this.this$0.rippleAnimation = (Animator) null;
                this.this$0.invalidateSelf();
            }
        });
        animatorSet.start();
        this.rippleAnimation = animatorSet;
    }

    @Override // android.graphics.drawable.Drawable
    public void setHotspot(float x, float y) {
        this.rippleData.setX(x);
        this.rippleData.setY(y);
        if (this.active) {
            invalidateSelf();
        }
    }

    @Override // android.graphics.drawable.Drawable
    @NotNull
    public Rect getDirtyBounds() {
        float fLerp = MathUtils.lerp(this.rippleData.getMinSize(), this.rippleData.getMaxSize(), this.rippleData.getProgress());
        Rect rect = new Rect((int) (this.rippleData.getX() - fLerp), (int) (this.rippleData.getY() - fLerp), (int) (this.rippleData.getX() + fLerp), (int) (this.rippleData.getY() + fLerp));
        rect.union(super.getDirtyBounds());
        return rect;
    }

    @Override // android.graphics.drawable.Drawable
    protected boolean onStateChange(@Nullable int[] stateSet) {
        boolean zOnStateChange = super.onStateChange(stateSet);
        if (stateSet == null) {
            return zOnStateChange;
        }
        boolean z = this.pressed;
        boolean z2 = false;
        this.pressed = false;
        boolean z3 = false;
        boolean z4 = false;
        boolean z5 = false;
        for (int i : stateSet) {
            if (i == com.android.internal.R.attr.state_enabled) {
                z3 = true;
            } else if (i == com.android.internal.R.attr.state_focused) {
                z4 = true;
            } else if (i == com.android.internal.R.attr.state_pressed) {
                this.pressed = true;
            } else if (i == com.android.internal.R.attr.state_hovered) {
                z5 = true;
            }
        }
        if (z3 && (this.pressed || z4 || z5)) {
            z2 = true;
        }
        setActive(z2);
        if (z && !this.pressed) {
            illuminate();
        }
        return zOnStateChange;
    }
}
