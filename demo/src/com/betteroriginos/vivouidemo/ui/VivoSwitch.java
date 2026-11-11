package com.betteroriginos.vivouidemo.ui;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.view.animation.PathInterpolator;

import com.betteroriginos.vivouidemo.R;

/**
 * OS5 switch: a pill track with a hollow-looking circular thumb.
 * Off: light track + white thumb wrapped by a grey ring.
 * On: vivo blue track + white thumb.
 */
public class VivoSwitch extends View {
    public interface OnCheckedChangeListener {
        void onCheckedChanged(VivoSwitch view, boolean checked);
    }

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private boolean checked;
    private float fraction;
    private ValueAnimator animator;
    private OnCheckedChangeListener listener;

    public VivoSwitch(Context context) {
        super(context);
        setClickable(true);
        setFocusable(true);
        setContentDescription(checked ? "已开启" : "已关闭");
    }

    public boolean isChecked() {
        return checked;
    }

    public void setChecked(boolean value) {
        setChecked(value, true);
    }

    public void setChecked(boolean value, boolean animate) {
        if (checked == value) {
            return;
        }
        checked = value;
        setContentDescription(checked ? "已开启" : "已关闭");
        if (animator != null) {
            animator.cancel();
        }
        if (animate) {
            animator = ValueAnimator.ofFloat(fraction, checked ? 1f : 0f);
            animator.setDuration(240);
            animator.setInterpolator(new PathInterpolator(0.22f, 0f, 0f, 1f));
            animator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                @Override
                public void onAnimationUpdate(ValueAnimator animation) {
                    fraction = (float) animation.getAnimatedValue();
                    invalidate();
                }
            });
            animator.start();
        } else {
            fraction = checked ? 1f : 0f;
            invalidate();
        }
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
        setMeasuredDimension(VivoTheme.dpi(getContext(), 48), VivoTheme.dpi(getContext(), 28));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        Context c = getContext();
        float trackW = VivoTheme.dp(c, 40);
        float trackH = VivoTheme.dp(c, 20);
        float left = (getWidth() - trackW) / 2f;
        float top = (getHeight() - trackH) / 2f;
        rect.set(left, top, left + trackW, top + trackH);

        int offTrack = VivoTheme.color(c, R.color.vivo_switch_track_off);
        int onTrack = VivoTheme.color(c, R.color.vivo_switch_track_on);
        int offRing = VivoTheme.color(c, R.color.vivo_switch_ring_off);
        int onRing = VivoTheme.color(c, R.color.vivo_accent);

        paint.setStyle(Paint.Style.FILL);
        paint.setColor(VivoTheme.blend(offTrack, onTrack, fraction));
        canvas.drawRoundRect(rect, trackH / 2f, trackH / 2f, paint);

        float thumbR = VivoTheme.dp(c, 8.35f);
        float ringW = VivoTheme.dp(c, 3.3f);
        float cx = left + trackH / 2f + (trackW - trackH) * fraction;
        float cy = top + trackH / 2f;

        paint.setStyle(Paint.Style.FILL);
        paint.setColor(VivoTheme.color(c, R.color.vivo_white));
        canvas.drawCircle(cx, cy, thumbR + ringW / 2f, paint);

        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(ringW);
        paint.setColor(VivoTheme.blend(offRing, onRing, fraction));
        canvas.drawCircle(cx, cy, thumbR, paint);

        if (!isEnabled()) {
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(VivoTheme.alpha(VivoTheme.color(c, R.color.vivo_card), 0.55f));
            canvas.drawRoundRect(rect, trackH / 2f, trackH / 2f, paint);
        }
    }

    @Override
    public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);
        setAlpha(enabled ? 1f : 0.4f);
    }
}
