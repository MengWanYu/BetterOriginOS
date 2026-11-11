package com.betteroriginos.vivouidemo.ui;

import android.content.Context;
import android.graphics.Color;
import android.view.Gravity;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Space;
import android.widget.TextView;

import com.betteroriginos.vivouidemo.R;

public final class VivoUi {
    public static final int PAGE_MARGIN = 20;
    public static final int CARD_RADIUS = 12;

    private VivoUi() {
    }

    public static int dp(Context c, float v) {
        return VivoTheme.dpi(c, v);
    }

    public static Space space(Context c, float heightDp) {
        Space space = new Space(c);
        space.setLayoutParams(new LinearLayout.LayoutParams(1, dp(c, heightDp)));
        return space;
    }

    public static TextView sectionLabel(Context c, CharSequence text) {
        TextView tv = new TextView(c);
        VivoTheme.text(tv, 13f, VivoTheme.color(c, R.color.vivo_text_secondary), VivoTheme.W_MEDIUM);
        tv.setText(text);
        tv.setPadding(0, 0, 0, 0);
        return tv;
    }

    public static LinearLayout card(Context c) {
        return card(c, 0);
    }

    public static LinearLayout card(Context c, float paddingDp) {
        LinearLayout layout = new LinearLayout(c);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setBackground(VivoTheme.rounded(c, VivoTheme.color(c, R.color.vivo_card), CARD_RADIUS));
        layout.setClipChildren(false);
        layout.setClipToPadding(false);
        int p = dp(c, paddingDp);
        layout.setPadding(p, p, p, p);
        return layout;
    }

    public static LinearLayout group(Context c) {
        LinearLayout layout = new LinearLayout(c);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setClipChildren(false);
        layout.setClipToPadding(false);
        return layout;
    }

    public static void addToBody(LinearLayout body, View view, float topDp, float bottomDp) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.leftMargin = dp(body.getContext(), PAGE_MARGIN);
        lp.rightMargin = dp(body.getContext(), PAGE_MARGIN);
        lp.topMargin = dp(body.getContext(), topDp);
        lp.bottomMargin = dp(body.getContext(), bottomDp);
        body.addView(view, lp);
    }

    public static void addFullWidth(LinearLayout body, View view, float topDp, float bottomDp) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(body.getContext(), topDp);
        lp.bottomMargin = dp(body.getContext(), bottomDp);
        body.addView(view, lp);
    }

    public static void addGroup(LinearLayout body, LinearLayout group, float topDp, float bottomDp) {
        int count = group.getChildCount();
        for (int i = 0; i < count; i++) {
            View row = group.getChildAt(i);
            row.setBackground(VivoTheme.rowBackground(body.getContext(), i == 0, i == count - 1));
            if (i == count - 1) {
                View divider = row.findViewWithTag("row_divider");
                if (divider != null) {
                    divider.setVisibility(View.GONE);
                }
            }
        }
        addToBody(body, group, topDp, bottomDp);
    }

    public static View row(Context c, String icon, int iconColor, CharSequence title,
                           CharSequence summary, CharSequence value, boolean chevron, View trailing) {
        final LinearLayout row = new LinearLayout(c);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setClickable(true);
        row.setFocusable(true);

        LinearLayout content = new LinearLayout(c);
        content.setOrientation(LinearLayout.HORIZONTAL);
        content.setGravity(Gravity.CENTER_VERTICAL);
        content.setPadding(dp(c, 12), 0, dp(c, 16), 0);
        int height = summary == null || summary.length() == 0 ? 56 : 64;
        row.addView(content, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(c, height)));

        if (icon != null) {
            ImageView iv = new ImageView(c);
            iv.setImageDrawable(VivoIcons.of(icon, iconColor, 24));
            LinearLayout.LayoutParams iconLp = new LinearLayout.LayoutParams(dp(c, 26), dp(c, 26));
            iconLp.rightMargin = dp(c, 10);
            content.addView(iv, iconLp);
        } else {
            content.setPadding(dp(c, 16), 0, dp(c, 16), 0);
        }

        LinearLayout texts = new LinearLayout(c);
        texts.setOrientation(LinearLayout.VERTICAL);
        texts.setGravity(Gravity.CENTER_VERTICAL);
        content.addView(texts, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView titleView = new TextView(c);
        VivoTheme.text(titleView, 15f, VivoTheme.color(c, R.color.vivo_text_primary), VivoTheme.W_SEMI);
        titleView.setText(title);
        titleView.setSingleLine(true);
        titleView.setEllipsize(android.text.TextUtils.TruncateAt.END);
        texts.addView(titleView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        if (summary != null && summary.length() > 0) {
            TextView summaryView = new TextView(c);
            VivoTheme.text(summaryView, 12f, VivoTheme.color(c, R.color.vivo_text_secondary), VivoTheme.W_REGULAR);
            summaryView.setText(summary);
            summaryView.setSingleLine(true);
            summaryView.setEllipsize(android.text.TextUtils.TruncateAt.END);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.topMargin = dp(c, 2);
            texts.addView(summaryView, lp);
        }

        if (value != null && value.length() > 0) {
            TextView valueView = new TextView(c);
            VivoTheme.text(valueView, 14f, VivoTheme.color(c, R.color.vivo_text_value), VivoTheme.W_REGULAR);
            valueView.setText(value);
            valueView.setSingleLine(true);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.leftMargin = dp(c, 8);
            content.addView(valueView, lp);
        }

        if (trailing != null) {
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.leftMargin = dp(c, 12);
            content.addView(trailing, lp);
        }

        if (chevron) {
            ImageView arrow = new ImageView(c);
            arrow.setImageDrawable(VivoIcons.of("chevron",
                    VivoTheme.color(c, R.color.vivo_text_tertiary), 16));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(c, 16), dp(c, 16));
            lp.leftMargin = dp(c, 6);
            content.addView(arrow, lp);
        }

        View divider = new View(c);
        divider.setTag("row_divider");
        divider.setBackgroundColor(VivoTheme.color(c, R.color.vivo_divider));
        LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 1);
        dlp.leftMargin = dp(c, icon == null ? 16 : 48);
        row.addView(divider, dlp);
        return row;
    }

    public static View switchRow(Context c, String icon, int iconColor, CharSequence title,
                                 CharSequence summary, VivoSwitch toggle) {
        return row(c, icon, iconColor, title, summary, null, false, toggle);
    }

    public static View sceneRow(Context c, String icon, int iconColor, CharSequence title,
                                CharSequence summary, OnClickListener click) {
        LinearLayout row = (LinearLayout) row(c, icon, iconColor, title, summary, null, true, null);
        View content = row.getChildAt(0);
        ((LinearLayout.LayoutParams) content.getLayoutParams()).height = dp(c, 72);
        row.setOnClickListener(click);
        return row;
    }

    public static TextView bodyText(Context c, CharSequence text) {
        TextView tv = new TextView(c);
        VivoTheme.text(tv, 14f, VivoTheme.color(c, R.color.vivo_text_secondary), VivoTheme.W_REGULAR);
        tv.setText(text);
        tv.setLineSpacing(dp(c, 2), 1f);
        return tv;
    }

    public static TextView headerText(Context c, CharSequence text, float sp, int color, int weight) {
        TextView tv = new TextView(c);
        VivoTheme.text(tv, sp, color, weight);
        tv.setText(text);
        return tv;
    }

    public static void addCentered(LinearLayout body, View v, float topDp, float bottomDp) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.gravity = Gravity.CENTER_HORIZONTAL;
        lp.topMargin = dp(body.getContext(), topDp);
        lp.bottomMargin = dp(body.getContext(), bottomDp);
        body.addView(v, lp);
    }

    public static FrameLayout iconBox(Context c, String icon, int color, float sizeDp) {
        FrameLayout box = new FrameLayout(c);
        ImageView iv = new ImageView(c);
        iv.setImageDrawable(VivoIcons.of(icon, color, Math.round(sizeDp)));
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(dp(c, sizeDp), dp(c, sizeDp));
        lp.gravity = Gravity.CENTER;
        box.addView(iv, lp);
        return box;
    }
}
