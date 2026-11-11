package com.betteroriginos.feature.camera;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;

/**
 * 快门键的 OS5 原子设计环前景：外环描边 + 中心实心盘。
 *
 * 挂在 {@code ShutterButton.setForeground()} 上，**不参与布局**（前景不改尺寸、不改热区），
 * 尺寸按 {@link CameraShutterSpec#sizesPx} 从自己的 bounds 现算，所以相机给这颗按钮多大
 * 就按多大画，缩放/换屏/字体档位都不会把环挤歪。
 *
 * 第一次 draw 会打一条读数：那是"屏幕上真的按这套几何画出来了"的唯一硬证据
 * （施加调用本身只证明我们写了，不证明画过）。
 */
public final class ShutterRingDrawable extends Drawable {

    private final float density;
    private final Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint disc = new Paint(Paint.ANTI_ALIAS_FLAG);
    private int ringPx;
    private int discPx;
    private int outerPx;
    private int drawnWith = -1;
    private int drawCount;

    public ShutterRingDrawable(float density) {
        this.density = density <= 0f ? 1f : density;
        ring.setStyle(Paint.Style.STROKE);
        ring.setColor(CameraShutterSpec.COLOR_RING);
        disc.setStyle(Paint.Style.FILL);
        disc.setColor(CameraShutterSpec.COLOR_FILL);
    }

    @Override
    protected void onBoundsChange(Rect bounds) {
        int[] s = CameraShutterSpec.sizesPx(Math.min(bounds.width(), bounds.height()), density);
        outerPx = s[0];
        discPx = s[1];
        ringPx = s[2];
        ring.setStrokeWidth(ringPx);
    }

    @Override
    public void draw(Canvas canvas) {
        Rect b = getBounds();
        float cx = b.left + b.width() / 2f;
        float cy = b.top + b.height() / 2f;
        if (outerPx <= 0 || ringPx <= 0) {
            return;
        }
        // 描边圆心要往里收半个线宽，否则环的外沿会超出外接框被裁
        float ringR = outerPx / 2f - ringPx / 2f;
        canvas.drawCircle(cx, cy, ringR, ring);
        canvas.drawCircle(cx, cy, discPx / 2f, disc);
        drawnWith = b.width();
        drawCount++;
    }

    @Override
    public void setAlpha(int alpha) {
        ring.setAlpha(alpha);
        disc.setAlpha(alpha);
    }

    @Override
    public void setColorFilter(ColorFilter cf) {
        ring.setColorFilter(cf);
        disc.setColorFilter(cf);
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }

    /** 见证用：读回**上一次真正画出来**的三段尺寸与当时的框宽（施加调用只证明我们写了，这几个值证明画过）。 */
    public int lastOuterPx() {
        return outerPx;
    }

    public int lastInnerPx() {
        return discPx;
    }

    public int lastRingPx() {
        return ringPx;
    }

    public int lastBoundsWidth() {
        return drawnWith;
    }

    public int drawCount() {
        return drawCount;
    }
}
