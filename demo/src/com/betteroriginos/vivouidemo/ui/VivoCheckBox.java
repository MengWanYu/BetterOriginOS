package com.betteroriginos.vivouidemo.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.view.HapticFeedbackConstants;
import android.view.View;

import com.betteroriginos.vivouidemo.R;

public class VivoCheckBox extends View {
    public interface OnCheckedChangeListener {
        void onCheckedChanged(VivoCheckBox view, boolean checked);
    }

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private boolean checked;
    private final boolean radio;
    private OnCheckedChangeListener listener;

    public VivoCheckBox(Context context) {
        this(context, false);
    }

    public VivoCheckBox(Context context, boolean radio) {
        super(context);
        this.radio = radio;
        setClickable(true);
        setFocusable(true);
    }

    public boolean isChecked() {
        return checked;
    }

    public void setChecked(boolean value) {
        if (checked == value) {
            return;
        }
        checked = value;
        animate().cancel();
        setScaleX(0.86f);
        setScaleY(0.86f);
        animate().scaleX(1f).scaleY(1f).setDuration(160)
                .setInterpolator(new android.view.animation.PathInterpolator(0.2f, 0f, 0f, 1f))
                .start();
        invalidate();
        if (listener != null) {
            listener.onCheckedChanged(this, checked);
        }
    }

    public void setOnCheckedChangeListener(OnCheckedChangeListener l) {
        listener = l;
    }

    @Override
    public boolean performClick() {
        super.performClick();
        setChecked(!checked);
        performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
        return true;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        setMeasuredDimension(VivoTheme.dpi(getContext(), 28), VivoTheme.dpi(getContext(), 28));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float cx = getWidth() / 2f;
        float cy = getHeight() / 2f;
        float radius = VivoTheme.dp(getContext(), 10);
        int accent = VivoTheme.color(getContext(), R.color.vivo_accent);
        int outline = VivoTheme.color(getContext(), R.color.vivo_text_tertiary);

        if (radio) {
            if (checked) {
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(VivoTheme.dp(getContext(), 1.8f));
                paint.setColor(accent);
                canvas.drawCircle(cx, cy, radius, paint);
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(accent);
                canvas.drawCircle(cx, cy, VivoTheme.dp(getContext(), 5.4f), paint);
            } else {
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(VivoTheme.dp(getContext(), 1.7f));
                paint.setColor(outline);
                canvas.drawCircle(cx, cy, radius, paint);
            }
            return;
        }

        if (checked) {
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(accent);
            canvas.drawCircle(cx, cy, radius, paint);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(VivoTheme.dp(getContext(), 2.0f));
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setStrokeJoin(Paint.Join.ROUND);
            paint.setColor(VivoTheme.color(getContext(), R.color.vivo_white));
            path.reset();
            path.moveTo(cx - VivoTheme.dp(getContext(), 5.0f), cy);
            path.lineTo(cx - VivoTheme.dp(getContext(), 1.4f), cy + VivoTheme.dp(getContext(), 3.6f));
            path.lineTo(cx + VivoTheme.dp(getContext(), 5.4f), cy - VivoTheme.dp(getContext(), 3.4f));
            canvas.drawPath(path, paint);
        } else {
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(VivoTheme.dp(getContext(), 1.7f));
            paint.setColor(outline);
            canvas.drawCircle(cx, cy, radius, paint);
        }
    }

    @Override
    public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);
        setAlpha(enabled ? 1f : 0.4f);
    }
}
