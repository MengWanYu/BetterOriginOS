package com.betteroriginos.vivouidemo.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RenderEffect;
import android.graphics.RenderNode;
import android.graphics.Shader;
import android.os.Build;
import android.view.View;
import android.widget.FrameLayout;

import com.betteroriginos.vivouidemo.R;

/**
 * A frosted-glass surface inspired by com.originui.core.blur.VBlurLinearLayout.
 * The real OriginUI container asks the vivo framework for a blur material; this demo
 * reproduces the optical result with a RenderNode of the content underneath and a
 * translucent material tint. API 31+ uses a real GPU blur; older devices fall back
 * to the material tint.
 */
public class BlurBar extends FrameLayout {
    private View blurSource;
    private RenderNode renderNode;
    private final Paint tintPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dividerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path clipPath = new Path();
    private float blurRadiusDp = 32f;
    private float tintAlpha = 0.91f;
    private int tintColor;
    private int dividerColor;
    private boolean realBlur = true;
    private boolean dividerVisible = true;
    private boolean dividerTop = false;
    private float cornerRadiusDp = 0f;
    private boolean backdropEnabled = true;

    public BlurBar(Context context) {
        super(context);
        setWillNotDraw(false);
        tintColor = VivoTheme.color(context, R.color.vivo_bar_tint);
        dividerColor = VivoTheme.color(context, R.color.vivo_bar_divider);
        tintPaint.setColor(tintColor);
        dividerPaint.setColor(dividerColor);
        setClipChildren(false);
        setClipToPadding(false);
        setBackgroundColor(Color.TRANSPARENT);
        setClickable(true);
    }

    public void setBlurSource(View source) {
        blurSource = source;
        invalidate();
    }

    public void setBlurRadiusDp(float dp) {
        blurRadiusDp = Math.max(0f, dp);
        invalidate();
    }

    public float getBlurRadiusDp() {
        return blurRadiusDp;
    }

    public void setTintAlpha(float alpha) {
        tintAlpha = Math.max(0f, Math.min(1f, alpha));
        invalidate();
    }

    public float getTintAlpha() {
        return tintAlpha;
    }

    public void setRealBlur(boolean real) {
        realBlur = real;
        invalidate();
    }

    public boolean isRealBlur() {
        return realBlur;
    }

    public void setBackdropEnabled(boolean enabled) {
        backdropEnabled = enabled;
        invalidate();
    }

    public void setCornerRadiusDp(float radiusDp) {
        cornerRadiusDp = radiusDp;
        invalidate();
    }

    public void setDividerVisible(boolean visible) {
        dividerVisible = visible;
        invalidate();
    }

    public void setDividerTop(boolean top) {
        dividerTop = top;
        invalidate();
    }

    public void setDividerColor(int color) {
        dividerColor = color;
        dividerPaint.setColor(color);
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (backdropEnabled) {
            drawBackdrop(canvas);
        }
    }

    private void drawBackdrop(Canvas canvas) {
        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0) {
            return;
        }
        canvas.save();
        if (cornerRadiusDp > 0f) {
            clipPath.reset();
            float r = VivoTheme.dp(getContext(), cornerRadiusDp);
            android.graphics.RectF rf = new android.graphics.RectF(0, 0, w, h);
            clipPath.addRoundRect(rf, r, r, Path.Direction.CW);
            canvas.clipPath(clipPath);
        } else {
            canvas.clipRect(0, 0, w, h);
        }

        boolean blurred = false;
        if (realBlur && Build.VERSION.SDK_INT >= 31 && blurSource != null
                && blurRadiusDp > 0f && canvas.isHardwareAccelerated()) {
            blurred = recordAndDrawBlur(canvas, w, h);
        }
        if (!blurred) {
            canvas.drawColor(VivoTheme.alpha(tintColor, Math.min(1f, tintAlpha + 0.06f)));
        } else {
            canvas.drawColor(VivoTheme.alpha(tintColor, tintAlpha));
        }
        canvas.restore();
    }

    private boolean recordAndDrawBlur(Canvas canvas, int w, int h) {
        View source = blurSource;
        if (source.getWidth() <= 0 || source.getHeight() <= 0) {
            return false;
        }
        boolean recording = false;
        try {
            if (renderNode == null) {
                renderNode = new RenderNode("vivoBlur");
            }
            renderNode.setPosition(0, 0, w, h);
            android.graphics.RecordingCanvas rc = renderNode.beginRecording(w, h);
            recording = true;
            rc.translate(-getLeft(), -getTop());
            rc.clipRect(getLeft(), getTop(), getLeft() + w, getTop() + h);
            source.draw(rc);
            renderNode.endRecording();
            recording = false;
            float radius = VivoTheme.dp(getContext(), blurRadiusDp);
            renderNode.setRenderEffect(RenderEffect.createBlurEffect(
                    radius, radius, Shader.TileMode.CLAMP));
            canvas.drawRenderNode(renderNode);
            return true;
        } catch (Throwable t) {
            if (recording) {
                try {
                    renderNode.endRecording();
                } catch (Throwable ignored) {
                }
            }
            return false;
        }
    }

    protected void drawHairline(Canvas canvas) {
        if (!dividerVisible) {
            return;
        }
        float y = dividerTop ? 0f : getHeight() - 1f;
        canvas.drawRect(0, y, getWidth(), y + 1f, dividerPaint);
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        super.dispatchDraw(canvas);
        drawHairline(canvas);
    }
}
