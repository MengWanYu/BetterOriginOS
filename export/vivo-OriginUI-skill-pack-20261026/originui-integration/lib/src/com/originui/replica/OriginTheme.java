package com.originui.replica;

import android.app.Activity;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.graphics.drawable.ColorDrawable;
import android.view.View;
import android.widget.TextView;

/**
 * OriginOS 5 设计 token（逆向自本机 com.android.settings 的 vivo_card_style_* 资源
 * 与真机取色，详见 vivo-OriginUI/docs 与 project out/DESIGN-SPEC-1025.md）。
 * 无资源依赖：全部常量内联，任何 App 可直接拷贝本目录接入。
 */
public final class OriginTheme {
    // ---- 颜色 ----
    public static int bgPage(Context c) { return night(c) ? 0xFF000000 : 0xFFF2F2F5; }
    public static int bgCard(Context c) { return night(c) ? 0xFF202020 : 0xFFFFFFFF; }
    public static int textPrimary(Context c) { return night(c) ? 0xFFF2F2F2 : 0xDE000000; }
    public static int textSecondary(Context c) { return night(c) ? 0x99FFFFFF : 0x61000000; }
    public static int textTertiary(Context c) { return night(c) ? 0x61FFFFFF : 0x42000000; }
    public static int sectionLabel(Context c) { return night(c) ? 0x80FFFFFF : 0x78000000; }
    public static int divider(Context c) { return night(c) ? 0x26FFFFFF : 0xFFD9D9D9; }
    public static int accent(Context c) { return night(c) ? 0xFF2785FF : 0xFF0F77FF; }
    public static int accentSoft(Context c) { return night(c) ? 0x332785FF : 0x1A0F77FF; }
    public static int trackOff(Context c) { return night(c) ? 0xFF3A3C40 : 0xFFE3E5EA; }
    public static int scrim(Context c) { return night(c) ? 0x66000000 : 0x52000000; }

    // ---- 圆角 / 间距（dp）----
    public static final float RADIUS_CARD = 12f;
    public static final float RADIUS_DIALOG = 16f;
    public static final float RADIUS_PILL = 22f;
    public static final float PAGE_MARGIN = 22f;
    public static final float CARD_GAP = 18f;
    public static final float ROW_PAD_H = 16f;
    public static final float ROW_PAD_V = 14f;
    public static final float BAR_HEIGHT = 64f;
    public static final float PILL_HEIGHT = 44f;

    // ---- 字号（sp）----
    public static final float TEXT_ROW_TITLE = 16f;
    public static final float TEXT_ROW_SUMMARY = 13f;
    public static final float TEXT_PAGE_TITLE = 28f;

    public static boolean night(Context c) {
        int m = c.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        return m == Configuration.UI_MODE_NIGHT_YES;
    }

    public static float dp(Context c, float v) { return v * c.getResources().getDisplayMetrics().density; }
    public static int dpi(Context c, float v) { return Math.round(dp(c, v)); }

    public static void text(TextView tv, float sp, int color, int weight) {
        tv.setTextSize(sp);
        tv.setTextColor(color);
        tv.setTypeface(Typeface.create("sans-serif", weight));
    }
    public static final int W_REGULAR = Typeface.NORMAL;
    public static final int W_MEDIUM = Typeface.NORMAL;
    public static final int W_SEMI = Typeface.BOLD;
    public static final int W_BOLD = Typeface.BOLD;

    // ---- 形状 ----
    public static GradientDrawable roundedRect(Context c, int color, float radiusDp) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(c, radiusDp));
        return d;
    }

    /** 行级圆角：组内 top/mid/bot。 */
    public static GradientDrawable rowBg(Context c, int color, int position) {
        float r = dp(c, RADIUS_CARD);
        float[] radii;
        if (position == POSITION_TOP) {
            radii = new float[]{r, r, r, r, 0, 0, 0, 0};
        } else if (position == POSITION_BOTTOM) {
            radii = new float[]{0, 0, 0, 0, r, r, r, r};
        } else if (position == POSITION_SINGLE) {
            radii = new float[]{r, r, r, r, r, r, r, r};
        } else {
            radii = new float[8];
        }
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadii(radii);
        return d;
    }
    public static final int POSITION_TOP = 0, POSITION_MID = 1, POSITION_BOTTOM = 2, POSITION_SINGLE = 3;

    public static Drawable ripple(Context c, Drawable content, float radiusDp) {
        GradientDrawable mask = new GradientDrawable();
        mask.setColor(Color.WHITE);
        mask.setCornerRadius(dp(c, radiusDp));
        return new RippleDrawable(android.content.res.ColorStateList.valueOf(0x33000000), content, mask);
    }

    public static void applyWindow(Activity a) {
        int page = bgPage(a);
        a.getWindow().setBackgroundDrawable(new ColorDrawable(page));
        a.getWindow().setStatusBarColor(page);
        a.getWindow().setNavigationBarColor(page);
        View decor = a.getWindow().getDecorView();
        int flags = decor.getSystemUiVisibility();
        if (!night(a)) {
            flags |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
        } else {
            flags &= ~(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        }
        decor.setSystemUiVisibility(flags);
    }

    private OriginTheme() {}
}
