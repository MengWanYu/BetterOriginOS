package com.betteroriginos.vivouidemo.ui;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Build;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.betteroriginos.vivouidemo.R;

public final class VivoSheet {
    public interface OnActionClickListener {
        void onActionClick(int index);
    }

    private VivoSheet() {
    }

    public static void show(final Activity activity, String title, final String[] actions,
                            String[] icons, final int[] iconColors,
                            final OnActionClickListener listener) {
        final Dialog dialog = new Dialog(activity);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        Window window = dialog.getWindow();
        if (window == null) {
            return;
        }
        window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        window.addFlags(WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN);
        window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);

        final FrameLayout root = new FrameLayout(activity);
        root.setBackgroundColor(VivoTheme.color(activity, R.color.vivo_scrim));
        final LinearLayout bottom = new LinearLayout(activity);
        bottom.setOrientation(LinearLayout.VERTICAL);
        bottom.setPadding(VivoTheme.dpi(activity, 12), 0, VivoTheme.dpi(activity, 12),
                VivoTheme.dpi(activity, 12));
        final FrameLayout.LayoutParams bottomLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        bottomLp.gravity = Gravity.BOTTOM;
        root.addView(bottom, bottomLp);

        LinearLayout card = new LinearLayout(activity);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(VivoTheme.roundedRadii(activity,
                VivoTheme.color(activity, R.color.vivo_card),
                new float[]{20, 20, 0, 0}));
        card.setClipChildren(false);
        card.setElevation(VivoTheme.dp(activity, 16f));

        View handle = new View(activity);
        handle.setBackground(VivoTheme.rounded(activity,
                VivoTheme.color(activity, R.color.vivo_handle), 2f));
        LinearLayout.LayoutParams handleLp = new LinearLayout.LayoutParams(
                VivoTheme.dpi(activity, 36), VivoTheme.dpi(activity, 4));
        handleLp.gravity = Gravity.CENTER_HORIZONTAL;
        handleLp.topMargin = VivoTheme.dpi(activity, 8);
        card.addView(handle, handleLp);

        if (title != null) {
            TextView titleView = new TextView(activity);
            VivoTheme.text(titleView, 16f, VivoTheme.color(activity, R.color.vivo_text_secondary),
                    VivoTheme.W_MEDIUM);
            titleView.setText(title);
            titleView.setGravity(Gravity.CENTER);
            titleView.setPadding(0, VivoTheme.dpi(activity, 12), 0, VivoTheme.dpi(activity, 6));
            card.addView(titleView, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        }

        for (int i = 0; i < actions.length; i++) {
            final int index = i;
            LinearLayout row = new LinearLayout(activity);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(VivoTheme.dpi(activity, 20), 0, VivoTheme.dpi(activity, 20), 0);
            row.setClickable(true);
            row.setBackground(VivoTheme.iconButtonBackground(activity));

            if (icons != null && i < icons.length && icons[i] != null) {
                android.widget.ImageView icon = new android.widget.ImageView(activity);
                int color = iconColors != null && i < iconColors.length
                        ? iconColors[i] : VivoTheme.color(activity, R.color.vivo_text_primary);
                icon.setImageDrawable(VivoIcons.of(icons[i], color, 24));
                LinearLayout.LayoutParams iconLp = new LinearLayout.LayoutParams(
                        VivoTheme.dpi(activity, 24), VivoTheme.dpi(activity, 24));
                iconLp.rightMargin = VivoTheme.dpi(activity, 16);
                row.addView(icon, iconLp);
            }

            TextView text = new TextView(activity);
            VivoTheme.text(text, 16f, i == actions.length - 1 && "删除闹钟".contentEquals(actions[i])
                            ? VivoTheme.color(activity, R.color.vivo_red)
                            : VivoTheme.color(activity, R.color.vivo_text_primary),
                    VivoTheme.W_REGULAR);
            text.setText(actions[i]);
            row.addView(text, new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            row.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    dismissAnimated(activity, dialog, root, bottom);
                    if (listener != null) {
                        listener.onActionClick(index);
                    }
                }
            });
            card.addView(row, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, VivoTheme.dpi(activity, 56)));

            if (i < actions.length - 1) {
                View divider = new View(activity);
                divider.setBackgroundColor(VivoTheme.color(activity, R.color.vivo_divider));
                LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, 1);
                dlp.leftMargin = VivoTheme.dpi(activity, 20);
                card.addView(divider, dlp);
            }
        }
        bottom.addView(card, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView cancel = new TextView(activity);
        VivoTheme.text(cancel, 15f, VivoTheme.color(activity, R.color.vivo_text_primary), VivoTheme.W_MEDIUM);
        cancel.setText("取消");
        cancel.setGravity(Gravity.CENTER);
        cancel.setClickable(true);
        cancel.setBackground(VivoTheme.rounded(activity,
                VivoTheme.color(activity, R.color.vivo_card), 16f));
        cancel.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dismissAnimated(activity, dialog, root, bottom);
            }
        });
        LinearLayout.LayoutParams cancelLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, VivoTheme.dpi(activity, 52));
        cancelLp.topMargin = VivoTheme.dpi(activity, 8);
        bottom.addView(cancel, cancelLp);

        root.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dismissAnimated(activity, dialog, root, bottom);
            }
        });
        bottom.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // swallow
            }
        });

        bottom.setOnApplyWindowInsetsListener(new View.OnApplyWindowInsetsListener() {
            @Override
            public WindowInsets onApplyWindowInsets(View v, WindowInsets insets) {
                int nav = 0;
                if (Build.VERSION.SDK_INT >= 30) {
                    nav = insets.getInsets(WindowInsets.Type.navigationBars()).bottom;
                } else {
                    nav = insets.getSystemWindowInsetBottom();
                }
                v.setPadding(VivoTheme.dpi(activity, 12), 0, VivoTheme.dpi(activity, 12),
                        VivoTheme.dpi(activity, 12) + nav);
                return insets;
            }
        });

        dialog.setContentView(root);
        window = dialog.getWindow();
        if (window != null) {
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
            window.setGravity(Gravity.BOTTOM);
        }
        dialog.show();
        window = dialog.getWindow();
        if (window != null) {
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
            window.setGravity(Gravity.BOTTOM);
        }
        bottom.requestApplyInsets();

        root.setAlpha(0f);
        bottom.post(new Runnable() {
            @Override
            public void run() {
                bottom.setTranslationY(bottom.getHeight());
                root.animate().alpha(1f).setDuration(200).start();
                bottom.animate().translationY(0f).setDuration(280)
                        .setInterpolator(new android.view.animation.PathInterpolator(0.2f, 0f, 0f, 1f))
                        .start();
            }
        });
    }

    private static void dismissAnimated(final Activity activity, final Dialog dialog,
                                        final View root, final View bottom) {
        if (dialog == null || !dialog.isShowing()) {
            return;
        }
        root.animate().alpha(0f).setDuration(160).start();
        bottom.animate().translationY(bottom.getHeight()).setDuration(200).start();
        root.postDelayed(new Runnable() {
            @Override
            public void run() {
                try {
                    dialog.dismiss();
                } catch (Throwable ignored) {
                }
            }
        }, 210);
    }
}
