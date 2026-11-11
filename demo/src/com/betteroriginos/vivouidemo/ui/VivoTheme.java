package com.betteroriginos.vivouidemo.ui;

import android.app.Activity;
import android.app.UiModeManager;
import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.os.Build;
import android.util.SparseArray;
import android.view.View;
import android.view.Window;
import android.view.WindowInsetsController;
import android.widget.TextView;

import com.betteroriginos.vivouidemo.R;

public final class VivoTheme {
    public static final int W_REGULAR = 400;
    public static final int W_MEDIUM = 500;
    public static final int W_SEMI = 600;
    public static final int W_BOLD = 700;

    private static final SparseArray<Typeface> TYPEFACES = new SparseArray<>();

    private VivoTheme() {
    }

    public static float dp(Context c, float v) {
        return v * c.getResources().getDisplayMetrics().density;
    }

    public static int dpi(Context c, float v) {
        return Math.round(dp(c, v));
    }

    public static int color(Context c, int res) {
        return c.getColor(res);
    }

    public static boolean night(Context c) {
        int mode = c.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        return mode == Configuration.UI_MODE_NIGHT_YES;
    }

    public static Typeface typeface(int weight) {
        Typeface tf = TYPEFACES.get(weight);
        if (tf == null) {
            Typeface base = Typeface.create("sans-serif", Typeface.NORMAL);
            tf = Typeface.create(base, weight, false);
            TYPEFACES.put(weight, tf);
        }
        return tf;
    }

    public static void text(TextView v, float sp, int color, int weight) {
        v.setTextSize(sp);
        v.setTextColor(color);
        v.setTypeface(typeface(weight));
        v.setIncludeFontPadding(false);
    }

    public static void applyWindow(Activity activity) {
        Window window = activity.getWindow();
        window.setStatusBarColor(Color.TRANSPARENT);
        window.setNavigationBarColor(Color.TRANSPARENT);
        window.setNavigationBarDividerColor(Color.TRANSPARENT);
        if (Build.VERSION.SDK_INT >= 30) {
            window.setDecorFitsSystemWindows(false);
        }
        int flags = View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION;
        boolean night = night(activity);
        if (!night) {
            flags |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
        }
        window.getDecorView().setSystemUiVisibility(flags);
        if (Build.VERSION.SDK_INT >= 30) {
            WindowInsetsController controller = window.getInsetsController();
            if (controller != null) {
                int mask = WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                        | WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS;
                controller.setSystemBarsAppearance(night ? 0 : mask, mask);
            }
        }
    }

    public static void toggleNight(Activity activity) {
        if (Build.VERSION.SDK_INT < 31) {
            return;
        }
        UiModeManager manager = (UiModeManager) activity.getSystemService(Context.UI_MODE_SERVICE);
        if (manager == null) {
            return;
        }
        boolean night = night(activity);
        try {
            manager.setApplicationNightMode(night
                    ? UiModeManager.MODE_NIGHT_NO : UiModeManager.MODE_NIGHT_YES);
            activity.recreate();
        } catch (Throwable ignored) {
        }
    }

    public static GradientDrawable rounded(Context c, int fillColor, float radiusDp) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(fillColor);
        d.setCornerRadius(dp(c, radiusDp));
        return d;
    }

    /** cornerRadiiDp: top-left, top-right, bottom-right, bottom-left. */
    public static GradientDrawable roundedRadii(Context c, int fillColor, float[] cornerRadiiDp) {
        float[] radii = new float[]{
                dp(c, cornerRadiiDp[0]), dp(c, cornerRadiiDp[0]),
                dp(c, cornerRadiiDp[1]), dp(c, cornerRadiiDp[1]),
                dp(c, cornerRadiiDp[2]), dp(c, cornerRadiiDp[2]),
                dp(c, cornerRadiiDp[3]), dp(c, cornerRadiiDp[3])
        };
        GradientDrawable d = new GradientDrawable();
        d.setColor(fillColor);
        d.setCornerRadii(radii);
        return d;
    }

    public static Drawable cardBackground(Context c) {
        return rounded(c, color(c, R.color.vivo_card), 12f);
    }

    public static Drawable rowBackground(Context c, boolean top, boolean bottom) {
        float r = 12f;
        float[] radii = new float[]{top ? r : 0, top ? r : 0, bottom ? r : 0, bottom ? r : 0};
        GradientDrawable content = roundedRadii(c, color(c, R.color.vivo_card), radii);
        GradientDrawable mask = roundedRadii(c, Color.WHITE, radii);
        return new RippleDrawable(ColorStateList.valueOf(color(c, R.color.vivo_ripple)), content, mask);
    }

    public static Drawable iconButtonBackground(Context c) {
        GradientDrawable content = new GradientDrawable();
        content.setColor(Color.TRANSPARENT);
        content.setCornerRadius(dp(c, 22));
        GradientDrawable mask = new GradientDrawable();
        mask.setColor(Color.WHITE);
        mask.setCornerRadius(dp(c, 22));
        return new RippleDrawable(ColorStateList.valueOf(color(c, R.color.vivo_ripple)), content, mask);
    }

    public static int alpha(int color, float alpha) {
        int a = Math.round(Color.alpha(color) * Math.max(0f, Math.min(1f, alpha)));
        return Color.argb(a, Color.red(color), Color.green(color), Color.blue(color));
    }

    public static int blend(int from, int to, float t) {
        t = Math.max(0f, Math.min(1f, t));
        int a = Math.round(Color.alpha(from) + (Color.alpha(to) - Color.alpha(from)) * t);
        int r = Math.round(Color.red(from) + (Color.red(to) - Color.red(from)) * t);
        int g = Math.round(Color.green(from) + (Color.green(to) - Color.green(from)) * t);
        int b = Math.round(Color.blue(from) + (Color.blue(to) - Color.blue(from)) * t);
        return Color.argb(a, r, g, b);
    }

    public static void setMargins(View v, int left, int top, int right, int bottom) {
        if (v.getLayoutParams() instanceof android.view.ViewGroup.MarginLayoutParams) {
            android.view.ViewGroup.MarginLayoutParams lp =
                    (android.view.ViewGroup.MarginLayoutParams) v.getLayoutParams();
            lp.setMargins(left, top, right, bottom);
            v.setLayoutParams(lp);
        }
    }

    public static Drawable transparent() {
        return new ColorDrawable(Color.TRANSPARENT);
    }
}
