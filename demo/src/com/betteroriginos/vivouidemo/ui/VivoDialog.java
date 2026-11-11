package com.betteroriginos.vivouidemo.ui;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.betteroriginos.vivouidemo.R;

public final class VivoDialog {
    private VivoDialog() {
    }

    public static void confirm(final Activity activity, String title, String message,
                               String confirmText, String cancelText, final Runnable onConfirm) {
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

        final LinearLayout card = new LinearLayout(activity);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(VivoTheme.rounded(activity,
                VivoTheme.color(activity, R.color.vivo_card), 18f));
        card.setClipChildren(false);
        card.setElevation(VivoTheme.dp(activity, 18f));

        TextView titleView = new TextView(activity);
        VivoTheme.text(titleView, 17f, VivoTheme.color(activity, R.color.vivo_text_primary), VivoTheme.W_SEMI);
        titleView.setText(title);
        titleView.setGravity(Gravity.CENTER);
        titleView.setPadding(VivoTheme.dpi(activity, 24), VivoTheme.dpi(activity, 24),
                VivoTheme.dpi(activity, 24), 0);
        card.addView(titleView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView messageView = new TextView(activity);
        VivoTheme.text(messageView, 14f, VivoTheme.color(activity, R.color.vivo_text_secondary), VivoTheme.W_REGULAR);
        messageView.setText(message);
        messageView.setGravity(Gravity.CENTER);
        messageView.setLineSpacing(VivoTheme.dp(activity, 2.5f), 1f);
        messageView.setPadding(VivoTheme.dpi(activity, 26), VivoTheme.dpi(activity, 10),
                VivoTheme.dpi(activity, 26), VivoTheme.dpi(activity, 24));
        card.addView(messageView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        View divider = new View(activity);
        divider.setBackgroundColor(VivoTheme.color(activity, R.color.vivo_divider));
        card.addView(divider, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 1));

        LinearLayout buttons = new LinearLayout(activity);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        buttons.setGravity(Gravity.CENTER_VERTICAL);

        TextView cancel = new TextView(activity);
        cancel.setText(cancelText);
        cancel.setGravity(Gravity.CENTER);
        VivoTheme.text(cancel, 16f, VivoTheme.color(activity, R.color.vivo_text_secondary), VivoTheme.W_MEDIUM);
        cancel.setBackground(VivoTheme.iconButtonBackground(activity));
        cancel.setClickable(true);
        cancel.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dismissAnimated(dialog, root, card);
            }
        });
        buttons.addView(cancel, new LinearLayout.LayoutParams(0, VivoTheme.dpi(activity, 52), 1f));

        View vDivider = new View(activity);
        vDivider.setBackgroundColor(VivoTheme.color(activity, R.color.vivo_divider));
        buttons.addView(vDivider, new LinearLayout.LayoutParams(1, VivoTheme.dpi(activity, 22)));

        TextView confirm = new TextView(activity);
        confirm.setText(confirmText);
        confirm.setGravity(Gravity.CENTER);
        VivoTheme.text(confirm, 16f, VivoTheme.color(activity, R.color.vivo_accent), VivoTheme.W_SEMI);
        confirm.setBackground(VivoTheme.iconButtonBackground(activity));
        confirm.setClickable(true);
        confirm.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dismissAnimated(dialog, root, card);
                if (onConfirm != null) {
                    onConfirm.run();
                }
            }
        });
        buttons.addView(confirm, new LinearLayout.LayoutParams(0, VivoTheme.dpi(activity, 52), 1f));
        card.addView(buttons, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, VivoTheme.dpi(activity, 52)));

        int width = Math.min(activity.getResources().getDisplayMetrics().widthPixels
                - VivoTheme.dpi(activity, 56), VivoTheme.dpi(activity, 320));
        FrameLayout.LayoutParams cardLp = new FrameLayout.LayoutParams(
                width, ViewGroup.LayoutParams.WRAP_CONTENT);
        cardLp.gravity = Gravity.CENTER;
        root.addView(card, cardLp);

        root.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dismissAnimated(dialog, root, card);
            }
        });
        card.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // swallow
            }
        });

        dialog.setContentView(root);
        window = dialog.getWindow();
        if (window != null) {
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
            window.setGravity(Gravity.CENTER);
        }
        dialog.show();
        window = dialog.getWindow();
        if (window != null) {
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        }
        root.setAlpha(0f);
        card.setScaleX(0.92f);
        card.setScaleY(0.92f);
        root.animate().alpha(1f).setDuration(170).start();
        card.animate().scaleX(1f).scaleY(1f).setDuration(220)
                .setInterpolator(new android.view.animation.PathInterpolator(0.2f, 0f, 0f, 1f))
                .start();
    }

    private static void dismissAnimated(final Dialog dialog, final View root, final View card) {
        if (dialog == null || !dialog.isShowing()) {
            return;
        }
        root.animate().alpha(0f).setDuration(140).start();
        card.animate().scaleX(0.94f).scaleY(0.94f).setDuration(150).start();
        root.postDelayed(new Runnable() {
            @Override
            public void run() {
                try {
                    dialog.dismiss();
                } catch (Throwable ignored) {
                }
            }
        }, 160);
    }
}
