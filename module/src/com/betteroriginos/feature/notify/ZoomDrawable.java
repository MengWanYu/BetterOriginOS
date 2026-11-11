package com.betteroriginos.feature.notify;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;

/** 自适应图标自带安全区留白；等比放大后裁边绘制，观感接近 iOS 满幅圆角图标 */
public final class ZoomDrawable extends Drawable {
    private final Drawable inner;
    private final float zoom;

    public ZoomDrawable(Drawable inner, float zoom) {
        this.inner = inner;
        this.zoom = zoom;
    }

    @Override
    public void draw(Canvas canvas) {
        Rect bounds = getBounds();
        if (bounds.isEmpty()) {
            return;
        }
        int centerX = bounds.centerX();
        int centerY = bounds.centerY();
        int halfW = Math.round(bounds.width() * zoom / 2f);
        int halfH = Math.round(bounds.height() * zoom / 2f);
        canvas.save();
        canvas.clipRect(bounds);
        inner.setBounds(centerX - halfW, centerY - halfH,
                centerX + halfW, centerY + halfH);
        inner.draw(canvas);
        canvas.restore();
    }

    @Override
    public void setAlpha(int alpha) {
        inner.setAlpha(alpha);
    }

    @Override
    public void setColorFilter(ColorFilter colorFilter) {
        inner.setColorFilter(colorFilter);
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }

    @Override
    public int getIntrinsicWidth() {
        return inner.getIntrinsicWidth();
    }

    @Override
    public int getIntrinsicHeight() {
        return inner.getIntrinsicHeight();
    }
}