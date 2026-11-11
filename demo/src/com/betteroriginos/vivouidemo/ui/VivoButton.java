package com.betteroriginos.vivouidemo.ui;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.ViewGroup;
import android.widget.TextView;

import com.betteroriginos.vivouidemo.R;

public class VivoButton extends TextView {
    public static final int PRIMARY = 0;
    public static final int SECONDARY = 1;
    public static final int STROKE = 2;
    public static final int TEXT = 3;
    public static final int DANGER = 4;

    public static final int SIZE_XL = 0;
    public static final int SIZE_M = 1;
    public static final int SIZE_S = 2;

    private int style = PRIMARY;
    private int size = SIZE_XL;
    private boolean pressedState;
    private int fillColor;
    private int strokeColor;
    private float strokeWidthDp;
    private float radiusDp;
    private int textColor;

    public VivoButton(Context context) {
        super(context);
        setGravity(Gravity.CENTER);
        setSingleLine(true);
        setEllipsize(android.text.TextUtils.TruncateAt.END);
        setIncludeFontPadding(false);
        setClickable(true);
        setFocusable(true);
        applySize();
        applyStyle();
    }

    public void setStyle(int style) {
        this.style = style;
        applyStyle();
    }

    public void setSize(int size) {
        this.size = size;
        applySize();
        applyStyle();
    }

    private void applySize() {
        Context c = getContext();
        int h;
        float sp;
        float pad;
        if (size == SIZE_XL) {
            h = 46;
            sp = 16f;
            pad = 16f;
            radiusDp = 23f;
            setMinWidth(VivoTheme.dpi(c, 96));
        } else if (size == SIZE_M) {
            h = 32;
            sp = 15f;
            pad = 12f;
            radiusDp = 16f;
            setMinWidth(VivoTheme.dpi(c, 64));
        } else {
            h = 28;
            sp = 12f;
            pad = 12f;
            radiusDp = 14f;
            setMinWidth(VivoTheme.dpi(c, 48));
        }
        setMinHeight(VivoTheme.dpi(c, h));
        setMaxHeight(VivoTheme.dpi(c, h));
        setTextSize(sp);
        setPadding(VivoTheme.dpi(c, pad), 0, VivoTheme.dpi(c, pad), 0);
        setGravity(Gravity.CENTER);
        setTypeface(VivoTheme.typeface(style == PRIMARY || style == STROKE || style == DANGER
                ? VivoTheme.W_BOLD : VivoTheme.W_SEMI));
        ViewGroup.LayoutParams lp = getLayoutParams();
        if (lp != null) {
            lp.height = VivoTheme.dpi(c, h);
            setLayoutParams(lp);
        }
    }

    private void applyStyle() {
        Context c = getContext();
        fillColor = 0;
        strokeColor = 0;
        strokeWidthDp = 0;
        switch (style) {
            case SECONDARY:
                fillColor = VivoTheme.color(c, R.color.vivo_button_gray);
                textColor = VivoTheme.color(c, R.color.vivo_button_gray_text);
                break;
            case STROKE:
                strokeColor = VivoTheme.color(c, R.color.vivo_accent);
                strokeWidthDp = 2f;
                textColor = VivoTheme.color(c, R.color.vivo_accent);
                break;
            case TEXT:
                textColor = VivoTheme.color(c, R.color.vivo_accent);
                break;
            case DANGER:
                strokeColor = VivoTheme.color(c, R.color.vivo_red);
                strokeWidthDp = 2f;
                textColor = VivoTheme.color(c, R.color.vivo_red);
                break;
            case PRIMARY:
            default:
                fillColor = VivoTheme.color(c, R.color.vivo_accent);
                textColor = VivoTheme.color(c, R.color.vivo_white);
                break;
        }
        setTextColor(textColor);
        setTypeface(VivoTheme.typeface(style == PRIMARY || style == STROKE || style == DANGER
                ? VivoTheme.W_BOLD : VivoTheme.W_SEMI));
        updateBackground();
    }

    private void updateBackground() {
        Context c = getContext();
        GradientDrawable d = new GradientDrawable();
        d.setCornerRadius(VivoTheme.dp(c, radiusDp));
        if (fillColor != 0) {
            int color = fillColor;
            if (pressedState && isEnabled()) {
                if (style == PRIMARY) {
                    color = VivoTheme.color(c, R.color.vivo_accent_pressed);
                } else if (style == SECONDARY) {
                    color = VivoTheme.color(c, R.color.vivo_button_gray_pressed);
                }
            }
            d.setColor(color);
        } else {
            if (pressedState) {
                d.setColor(VivoTheme.color(c, R.color.vivo_accent_soft));
            } else {
                d.setColor(android.graphics.Color.TRANSPARENT);
            }
        }
        if (strokeWidthDp > 0f) {
            d.setStroke(VivoTheme.dpi(c, strokeWidthDp), strokeColor);
        }
        setBackground(d);
    }

    @Override
    public void setPressed(boolean pressed) {
        boolean changed = pressed != pressedState;
        pressedState = pressed;
        super.setPressed(pressed);
        if (changed) {
            updateBackground();
            if (isEnabled()) {
                animate().cancel();
                if (pressed) {
                    animate().scaleX(0.96f).scaleY(0.96f).setDuration(90).start();
                } else {
                    animate().scaleX(1f).scaleY(1f).setDuration(150).start();
                }
            }
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        boolean result = super.onTouchEvent(event);
        if (event.getActionMasked() == MotionEvent.ACTION_CANCEL) {
            animate().scaleX(1f).scaleY(1f).setDuration(150).start();
        }
        return result;
    }

    @Override
    public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);
        setAlpha(enabled ? 1f : 0.4f);
        if (!enabled) {
            pressedState = false;
            updateBackground();
            animate().cancel();
            setScaleX(1f);
            setScaleY(1f);
        }
    }
}
