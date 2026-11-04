package com.originui.core.utils;

import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;

/* JADX INFO: loaded from: classes2.dex */
public class VRoundedCornerDrawable extends Drawable {
    public Rect drawBounds;
    public boolean leftBottomCorner;
    public boolean leftTopCorner;
    public boolean mDrawG2RoundRectRadii;
    public int mHeight;
    public int mHideStrokeFlag;
    public boolean mIsThinStroke;
    public float mRadius;
    public float mSolidAlphaPlus;
    public ColorStateList mSolidColor;
    public Paint mSolidPaint;
    public G2PathCache mSolidPathCache;
    public float mStrokeAlphaPlus;
    public ColorStateList mStrokeColor;
    public Paint mStrokePaint;
    public G2PathCache mStrokePathCache;
    public int mStrokeWidth;
    public int mWidth;
    public Rect paddingRect;
    public boolean rightBottomCorner;
    public boolean rightTopCorner;

    @Override // android.graphics.drawable.Drawable
    public int getOpacity() {
        return -3;
    }

    public VRoundedCornerDrawable() {
        this(0, 0, 0, 0);
    }

    public VRoundedCornerDrawable(int i, int i2) {
        this(i, i2, 0, 0);
    }

    public VRoundedCornerDrawable(int i, int i2, int i3, int i4) {
        this.mHideStrokeFlag = 0;
        this.mRadius = 10.0f;
        this.leftTopCorner = true;
        this.rightTopCorner = true;
        this.rightBottomCorner = true;
        this.leftBottomCorner = true;
        this.mSolidAlphaPlus = 1.0f;
        this.mStrokeAlphaPlus = 1.0f;
        this.mStrokeWidth = 0;
        this.mSolidPathCache = new G2PathCache();
        this.mStrokePathCache = new G2PathCache();
        this.paddingRect = new Rect();
        this.mWidth = -1;
        this.mHeight = -1;
        this.drawBounds = new Rect();
        this.mIsThinStroke = false;
        this.mDrawG2RoundRectRadii = false;
        this.mRadius = i;
        this.mSolidColor = ColorStateList.valueOf(i2);
        this.mStrokeColor = ColorStateList.valueOf(i3);
        this.mStrokeWidth = i4;
        init();
    }

    @Override // android.graphics.drawable.Drawable
    public boolean onStateChange(int[] iArr) {
        boolean z;
        ColorStateList colorStateList;
        int curStateColorForPaint;
        ColorStateList colorStateList2;
        int curStateColorForPaint2;
        if (this.mSolidPaint == null || (colorStateList2 = this.mSolidColor) == null || this.mSolidPaint.getColor() == (curStateColorForPaint2 = getCurStateColorForPaint(colorStateList2, iArr, this.mSolidAlphaPlus))) {
            z = false;
        } else {
            this.mSolidPaint.setColor(curStateColorForPaint2);
            z = true;
        }
        if (this.mStrokePaint != null && (colorStateList = this.mStrokeColor) != null && this.mStrokePaint.getColor() != (curStateColorForPaint = getCurStateColorForPaint(colorStateList, iArr, this.mStrokeAlphaPlus))) {
            this.mStrokePaint.setColor(curStateColorForPaint);
        } else if (!z) {
            return false;
        }
        invalidateSelf();
        return true;
    }

    public final int getCurStateColorForPaint(ColorStateList colorStateList, int[] iArr, float f) {
        if (colorStateList == null) {
            return 0;
        }
        return VViewUtils.colorPlusAlpha(colorStateList.getColorForState(iArr, 0), f);
    }

    public final void refreshPaintState() {
        this.mSolidPaint.setColor(getCurStateColorForPaint(this.mSolidColor, getState(), this.mSolidAlphaPlus));
        this.mStrokePaint.setColor(getCurStateColorForPaint(this.mStrokeColor, getState(), this.mStrokeAlphaPlus));
        checkThinStroke();
    }

    public final void checkThinStroke() {
        int i = this.mStrokeWidth;
        this.mIsThinStroke = i == 1;
        this.mStrokePaint.setStrokeWidth(i);
    }

    public final void init() {
        Paint paint = new Paint(1);
        this.mSolidPaint = paint;
        paint.setStyle(Paint.Style.FILL);
        Paint paint2 = new Paint(1);
        this.mStrokePaint = paint2;
        paint2.setAntiAlias(true);
        this.mStrokePaint.setStyle(Paint.Style.STROKE);
        refreshPaintState();
    }

    public void setDrawG2RoundRectRadii(boolean z) {
        this.mDrawG2RoundRectRadii = z;
    }

    @Override // android.graphics.drawable.Drawable
    public void draw(Canvas canvas) {
        VReflectionUtils.setCanvasNightMode(canvas, 0);
        Rect bounds = getBounds();
        Rect rect = this.drawBounds;
        int i = bounds.left;
        Rect rect2 = this.paddingRect;
        rect.set(i + rect2.left, bounds.top + rect2.top, bounds.right - rect2.right, bounds.bottom - rect2.bottom);
        int iSave = canvas.save();
        if (this.mSolidPaint.getColor() != 0) {
            if (!this.mDrawG2RoundRectRadii) {
                G2PathCache g2PathCache = this.mSolidPathCache;
                float f = this.mRadius;
                Rect rect3 = this.drawBounds;
                canvas.drawPath(g2PathCache.getG2RoundConerPath(f, rect3.left, rect3.top, rect3.right, rect3.bottom, this.leftTopCorner, this.rightTopCorner, this.rightBottomCorner, this.leftBottomCorner), this.mSolidPaint);
            } else {
                Rect rect4 = this.drawBounds;
                G2CornerUtil.canvasDrawG2RoundRectRadii(canvas, rect4.left, rect4.top, rect4.right, rect4.bottom, G2CornerUtil.getRoundRectRadiiCW(this.mRadius, this.leftTopCorner, this.rightTopCorner, this.rightBottomCorner, this.leftBottomCorner), this.mSolidPaint);
            }
            canvas.restoreToCount(iSave);
            iSave = canvas.save();
        }
        Paint paint = this.mStrokePaint;
        if (paint != null && paint.getStrokeWidth() > 0.0f) {
            if (isHideStrokeNo()) {
                if (this.mIsThinStroke) {
                    if (!this.mDrawG2RoundRectRadii) {
                        G2PathCache g2PathCache2 = this.mStrokePathCache;
                        float f2 = this.mRadius;
                        Rect rect5 = this.drawBounds;
                        canvas.drawPath(g2PathCache2.getG2RoundConerPath(f2, rect5.left, rect5.top, rect5.right, rect5.bottom, this.leftTopCorner, this.rightTopCorner, this.rightBottomCorner, this.leftBottomCorner), this.mStrokePaint);
                    } else {
                        Rect rect6 = this.drawBounds;
                        G2CornerUtil.canvasDrawG2RoundRectRadii(canvas, rect6.left, rect6.top, rect6.right, rect6.bottom, G2CornerUtil.getRoundRectRadiiCW(this.mRadius, this.leftTopCorner, this.rightTopCorner, this.rightBottomCorner, this.leftBottomCorner), this.mStrokePaint);
                    }
                } else {
                    float strokeWidth = this.mStrokePaint.getStrokeWidth() / 2.0f;
                    int i2 = (int) strokeWidth;
                    new Rect(this.drawBounds).inset(i2, i2);
                    if (!this.mDrawG2RoundRectRadii) {
                        canvas.drawPath(this.mStrokePathCache.getG2RoundConerPath(this.mRadius, r2.left, r2.top, r2.right, r2.bottom, this.leftTopCorner, this.rightTopCorner, this.rightBottomCorner, this.leftBottomCorner), this.mStrokePaint);
                    } else {
                        G2CornerUtil.canvasDrawG2RoundRectRadii(canvas, r2.left, r2.top, r2.right, r2.bottom, G2CornerUtil.getRoundRectRadiiCW(this.mRadius, this.leftTopCorner, this.rightTopCorner, this.rightBottomCorner, this.leftBottomCorner), this.mStrokePaint);
                    }
                }
            } else {
                float f3 = this.mStrokeWidth / 2.0f;
                if (!isHideStrokeLeft()) {
                    int i3 = this.drawBounds.left;
                    canvas.drawLine(i3 + f3, r2.top, i3 + f3, r2.bottom, this.mStrokePaint);
                }
                if (!isHideStrokeTop()) {
                    Rect rect7 = this.drawBounds;
                    float f4 = rect7.left;
                    int i4 = rect7.top;
                    canvas.drawLine(f4, i4 + f3, rect7.right, i4 + f3, this.mStrokePaint);
                }
                if (!isHideStrokeRight()) {
                    int i5 = this.drawBounds.right;
                    canvas.drawLine(i5 - f3, r2.top, i5 - f3, r2.bottom, this.mStrokePaint);
                }
                if (!isHideStrokeBottom()) {
                    Rect rect8 = this.drawBounds;
                    float f5 = rect8.left;
                    int i6 = rect8.bottom;
                    canvas.drawLine(f5, i6 - f3, rect8.right, i6 - f3, this.mStrokePaint);
                }
            }
        }
        canvas.restoreToCount(iSave);
    }

    @Override // android.graphics.drawable.Drawable
    public void setAlpha(int i) {
        float f = i;
        setStrokeAlphaPlus(f);
        setSolidAlphaPlus(f);
    }

    @Override // android.graphics.drawable.Drawable
    public void setColorFilter(ColorFilter colorFilter) {
        this.mSolidPaint.setColorFilter(colorFilter);
        this.mStrokePaint.setColorFilter(colorFilter);
    }

    @Override // android.graphics.drawable.Drawable
    public int getIntrinsicWidth() {
        int i = this.mWidth;
        return i < 0 ? super.getIntrinsicWidth() : i;
    }

    @Override // android.graphics.drawable.Drawable
    public int getIntrinsicHeight() {
        int i = this.mHeight;
        return i < 0 ? super.getIntrinsicHeight() : i;
    }

    public void setCorner(boolean z, boolean z2, boolean z3, boolean z4) {
        this.leftTopCorner = z;
        this.rightTopCorner = z2;
        this.rightBottomCorner = z3;
        this.leftBottomCorner = z4;
        invalidateSelf();
    }

    public void setRadius(float f) {
        this.mRadius = f;
        invalidateSelf();
    }

    public void setColor(int i) {
        setColor(ColorStateList.valueOf(i));
    }

    public void setColor(ColorStateList colorStateList) {
        this.mSolidColor = colorStateList;
        refreshPaintState();
        invalidateSelf();
    }

    public void setStrokeAlphaPlus(float f) {
        if (this.mStrokeAlphaPlus == f) {
            return;
        }
        this.mStrokeAlphaPlus = f;
        refreshPaintState();
        invalidateSelf();
    }

    public void setSolidAlphaPlus(float f) {
        if (this.mSolidAlphaPlus == f) {
            return;
        }
        this.mSolidAlphaPlus = f;
        refreshPaintState();
        invalidateSelf();
    }

    public boolean isHideStrokeNo() {
        return this.mHideStrokeFlag == 0;
    }

    public boolean isHideStrokeLeft() {
        return (this.mHideStrokeFlag & 8) != 0;
    }

    public boolean isHideStrokeTop() {
        return (this.mHideStrokeFlag & 4) != 0;
    }

    public boolean isHideStrokeRight() {
        return (this.mHideStrokeFlag & 2) != 0;
    }

    public boolean isHideStrokeBottom() {
        return (this.mHideStrokeFlag & 1) != 0;
    }

    @Override // android.graphics.drawable.Drawable
    public boolean isStateful() {
        ColorStateList colorStateList;
        ColorStateList colorStateList2;
        return super.isStateful() || ((colorStateList = this.mSolidColor) != null && colorStateList.isStateful()) || ((colorStateList2 = this.mStrokeColor) != null && colorStateList2.isStateful());
    }

    @Override // android.graphics.drawable.Drawable
    public int getChangingConfigurations() {
        int changingConfigurations = super.getChangingConfigurations();
        ColorStateList colorStateList = this.mSolidColor;
        int changingConfigurations2 = changingConfigurations | (colorStateList != null ? colorStateList.getChangingConfigurations() : 0);
        ColorStateList colorStateList2 = this.mStrokeColor;
        return changingConfigurations2 | (colorStateList2 != null ? colorStateList2.getChangingConfigurations() : 0);
    }
}
