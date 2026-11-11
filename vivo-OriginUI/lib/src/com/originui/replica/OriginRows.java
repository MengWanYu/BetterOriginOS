package com.originui.replica;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * OriginOS 列表行/分组卡工厂（OS5 设置 App 结构，DESIGN-SPEC §3.3）：
 * 行级圆角分组（top/mid/bot）+ 组内发丝线（左内缩 70dp）+ 组间 8dp 间隔。
 */
public final class OriginRows {

    /** 一行：标题 + 副标题 + 右侧控件（开关/值/chevron），整行 ripple。 */
    public static LinearLayout row(Context c, String title, String summary,
            View trailing, int position, View.OnClickListener onClick) {
        LinearLayout row = new LinearLayout(c);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        int padH = OriginTheme.dpi(c, OriginTheme.ROW_PAD_H);
        int padV = OriginTheme.dpi(c, OriginTheme.ROW_PAD_V);
        row.setMinimumHeight(OriginTheme.dpi(c, 55));
        row.setPadding(padH, padV, padH, padV);
        row.setBackground(OriginTheme.ripple(c,
                OriginTheme.rowBg(c, OriginTheme.bgCard(c), position), OriginTheme.RADIUS_CARD));

        LinearLayout texts = new LinearLayout(c);
        texts.setOrientation(LinearLayout.VERTICAL);
        TextView t = new TextView(c);
        OriginTheme.text(t, OriginTheme.TEXT_ROW_TITLE, OriginTheme.textPrimary(c), OriginTheme.W_MEDIUM);
        t.setText(title);
        texts.addView(t);
        if (summary != null) {
            TextView s = new TextView(c);
            OriginTheme.text(s, OriginTheme.TEXT_ROW_SUMMARY, OriginTheme.textSecondary(c), OriginTheme.W_REGULAR);
            s.setText(summary);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.topMargin = OriginTheme.dpi(c, 2);
            texts.addView(s, lp);
        }
        row.addView(texts, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        if (trailing != null) {
            LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            tlp.gravity = Gravity.CENTER_VERTICAL;
            row.addView(trailing, tlp);
        }
        if (onClick != null) {
            row.setOnClickListener(onClick);
        }
        return row;
    }

    /** 分隔线（逆向：左内缩 15dp、右侧到边，色 #D9D9D9/15% 白，高 1px）。 */
    public static View divider(Context c) {
        View v = new View(c);
        v.setBackgroundColor(OriginTheme.divider(c));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, OriginTheme.dpi(c, 1)));
        lp.leftMargin = OriginTheme.dpi(c, 15);
        v.setLayoutParams(lp);
        return v;
    }

    /** 组间 8dp 间隔行。 */
    public static View spacer(Context c, float dp) {
        View v = new View(c);
        v.setLayoutParams(new ViewGroup.LayoutParams(1, OriginTheme.dpi(c, dp)));
        return v;
    }

    /** 分类小节标签（section_label 色，12sp）。 */
    public static TextView sectionLabel(Context c, String text) {
        TextView tv = new TextView(c);
        OriginTheme.text(tv, 12f, OriginTheme.sectionLabel(c), OriginTheme.W_REGULAR);
        tv.setText(text);
        tv.setPadding(OriginTheme.dpi(c, 6), 0, 0, OriginTheme.dpi(c, 8));
        return tv;
    }

    /** 大标题（28sp medium，OS5 设置页首屏）。 */
    public static TextView pageTitle(Context c, String text) {
        TextView tv = new TextView(c);
        OriginTheme.text(tv, OriginTheme.TEXT_PAGE_TITLE, OriginTheme.textPrimary(c), OriginTheme.W_MEDIUM);
        tv.setText(text);
        return tv;
    }

    /** 胶囊主按钮（accent 底 + 白字 15sp medium，44dp 高）。 */
    public static TextView pillButton(Context c, String text, View.OnClickListener onClick) {
        TextView tv = new TextView(c);
        OriginTheme.text(tv, 15f, 0xFFFFFFFF, OriginTheme.W_MEDIUM);
        tv.setText(text);
        tv.setGravity(Gravity.CENTER);
        tv.setBackground(OriginTheme.ripple(c,
                OriginTheme.roundedRect(c, OriginTheme.accent(c), OriginTheme.RADIUS_PILL),
                OriginTheme.RADIUS_PILL));
        tv.setOnClickListener(onClick);
        return tv;
    }

    private OriginRows() {
    }
}
