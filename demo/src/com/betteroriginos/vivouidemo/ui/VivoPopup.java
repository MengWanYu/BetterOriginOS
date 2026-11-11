package com.betteroriginos.vivouidemo.ui;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.TextView;

import com.betteroriginos.vivouidemo.R;

public final class VivoPopup {
    public interface OnItemClickListener {
        void onItemClick(int index);
    }

    private VivoPopup() {
    }

    public static void show(View anchor, String[] items, String[] icons, final OnItemClickListener listener) {
        Context c = anchor.getContext();
        LinearLayout content = new LinearLayout(c);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(VivoTheme.dpi(c, 6), VivoTheme.dpi(c, 6),
                VivoTheme.dpi(c, 6), VivoTheme.dpi(c, 6));
        content.setBackground(VivoTheme.rounded(c, VivoTheme.color(c, R.color.vivo_card), 14f));
        content.setElevation(VivoTheme.dp(c, 14));

        final PopupWindow popup = new PopupWindow(content,
                VivoTheme.dpi(c, 224), ViewGroup.LayoutParams.WRAP_CONTENT, true);
        popup.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        popup.setOutsideTouchable(true);
        popup.setElevation(VivoTheme.dp(c, 14));

        for (int i = 0; i < items.length; i++) {
            final int index = i;
            LinearLayout row = new LinearLayout(c);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(VivoTheme.dpi(c, 14), 0, VivoTheme.dpi(c, 14), 0);
            row.setClickable(true);
            row.setBackground(VivoTheme.iconButtonBackground(c));

            if (icons != null && i < icons.length && icons[i] != null) {
                android.widget.ImageView icon = new android.widget.ImageView(c);
                icon.setImageDrawable(VivoIcons.of(icons[i],
                        VivoTheme.color(c, R.color.vivo_text_primary), 20));
                LinearLayout.LayoutParams iconLp = new LinearLayout.LayoutParams(
                        VivoTheme.dpi(c, 20), VivoTheme.dpi(c, 20));
                iconLp.rightMargin = VivoTheme.dpi(c, 12);
                row.addView(icon, iconLp);
            }

            TextView text = new TextView(c);
            VivoTheme.text(text, 15f, VivoTheme.color(c, R.color.vivo_text_primary), VivoTheme.W_REGULAR);
            text.setText(items[i]);
            row.addView(text, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

            row.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    popup.dismiss();
                    if (listener != null) {
                        listener.onItemClick(index);
                    }
                }
            });
            content.addView(row, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, VivoTheme.dpi(c, 48)));

            if (i < items.length - 1) {
                View divider = new View(c);
                divider.setBackgroundColor(VivoTheme.color(c, R.color.vivo_divider));
                LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, 1);
                dlp.leftMargin = VivoTheme.dpi(c, 14);
                content.addView(divider, dlp);
            }
        }

        popup.showAsDropDown(anchor, 0, VivoTheme.dpi(c, 6), Gravity.END);
        content.setAlpha(0f);
        content.setScaleX(0.96f);
        content.setScaleY(0.96f);
        content.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(150)
                .setInterpolator(new android.view.animation.PathInterpolator(0.2f, 0f, 0f, 1f))
                .start();
    }
}
