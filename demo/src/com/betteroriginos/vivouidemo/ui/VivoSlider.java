package com.betteroriginos.vivouidemo.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.View;

import com.betteroriginos.vivouidemo.R;

public class VivoSlider extends View {
    public interface OnProgressChangedListener {
        void onProgressChanged(VivoSlider slider, float progress);
    }

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private float min;
    private float max = 100f;
    private float progress = 50f;
    private boolean dragging;
    private OnProgressChangedListener listener;

    public VivoSlider(Context context) {
        super(context);
        setFocusable(true);
        setClickable(true);
    }

    public void setRange(float min, float max) {
        this.min = min;
        this.max = max;
        invalidate();
    }

    public void setProgress(float p) {
        progress = Math.max(min, Math.min(max, p));
        invalidate();
    }

    public float getProgress() {
        return progress;
    }

    public void setOnProgressChangedListener(OnProgressChangedListener l) {
        listener = l;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int w = MeasureSpec.getSize(widthMeasureSpec);
        setMeasuredDimension(w, VivoTheme.dpi(getContext(), 44));
    }

    private float trackLeft() {
        return VivoTheme.dp(getContext(), 10);
    }

    private float trackRight() {
        return getWidth() - VivoTheme.dp(getContext(), 10);
    }

    private float ratio() {
        return max == min ? 0f : (progress - min) / (max - min);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float left = trackLeft();
        float right = trackRight();
        float cy = getHeight() / 2f;
        float trackH = VivoTheme.dp(getContext(), 4);
        float radius = trackH / 2f;
        float cx = left + (right - left) * ratio();

        paint.setStyle(Paint.Style.FILL);
        paint.setColor(VivoTheme.color(getContext(), R.color.vivo_track));
        rect.set(left, cy - radius, right, cy + radius);
        canvas.drawRoundRect(rect, radius, radius, paint);

        paint.setColor(VivoTheme.color(getContext(), R.color.vivo_accent));
        rect.set(left, cy - radius, Math.max(left + radius, cx), cy + radius);
        canvas.drawRoundRect(rect, radius, radius, paint);

        float outer = VivoTheme.dp(getContext(), 10);
        paint.setColor(VivoTheme.color(getContext(), R.color.vivo_white));
        canvas.drawCircle(cx, cy, outer, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(VivoTheme.dp(getContext(), 5));
        paint.setColor(VivoTheme.color(getContext(), R.color.vivo_accent));
        canvas.drawCircle(cx, cy, VivoTheme.dp(getContext(), 7.5f), paint);
    }

    private void updateFromTouch(float x) {
        float left = trackLeft();
        float right = trackRight();
        float t = (x - left) / (right - left);
        t = Math.max(0f, Math.min(1f, t));
        float value = min + (max - min) * t;
        if (value != progress) {
            progress = value;
            invalidate();
            if (listener != null) {
                listener.onProgressChanged(this, progress);
            }
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (!isEnabled()) {
            return false;
        }
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                dragging = true;
                getParent().requestDisallowInterceptTouchEvent(true);
                updateFromTouch(event.getX());
                return true;
            case MotionEvent.ACTION_MOVE:
                if (dragging) {
                    updateFromTouch(event.getX());
                }
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (dragging) {
                    updateFromTouch(event.getX());
                    performClick();
                }
                dragging = false;
                getParent().requestDisallowInterceptTouchEvent(false);
                return true;
            default:
                return super.onTouchEvent(event);
        }
    }

    @Override
    public boolean performClick() {
        super.performClick();
        return true;
    }

    @Override
    public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);
        setAlpha(enabled ? 1f : 0.4f);
    }
}
