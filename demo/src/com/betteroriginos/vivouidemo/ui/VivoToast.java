package com.betteroriginos.vivouidemo.ui;

import android.app.Activity;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;

import com.betteroriginos.vivouidemo.R;

public final class VivoToast {
    private VivoToast() {
    }

    public static void show(final Activity activity, CharSequence message) {
        if (activity == null || activity.isFinishing()) {
            return;
        }
        View decor = activity.findViewById(android.R.id.content);
        if (!(decor instanceof ViewGroup)) {
            return;
        }
        final ViewGroup root = (ViewGroup) decor;
        View old = root.findViewWithTag("vivo_toast");
        if (old != null) {
            root.removeView(old);
        }

        final TextView toast = new TextView(activity);
        VivoTheme.text(toast, 14f, VivoTheme.color(activity, R.color.vivo_white), VivoTheme.W_MEDIUM);
        toast.setText(message);
        toast.setGravity(Gravity.CENTER);
        toast.setPadding(VivoTheme.dpi(activity, 18), VivoTheme.dpi(activity, 11),
                VivoTheme.dpi(activity, 18), VivoTheme.dpi(activity, 11));
        toast.setBackground(VivoTheme.rounded(activity,
                VivoTheme.color(activity, R.color.vivo_toast_bg), 22f));
        toast.setTag("vivo_toast");
        toast.setElevation(VivoTheme.dp(activity, 10f));
        toast.setAlpha(0f);
        toast.setTranslationY(VivoTheme.dp(activity, 10f));

        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
        lp.bottomMargin = VivoTheme.dpi(activity, 110);
        root.addView(toast, lp);

        toast.animate().alpha(1f).translationY(0f).setDuration(180).start();
        toast.postDelayed(new Runnable() {
            @Override
            public void run() {
                toast.animate().alpha(0f).translationY(VivoTheme.dp(activity, 8f))
                        .setDuration(160).withEndAction(new Runnable() {
                            @Override
                            public void run() {
                                if (toast.getParent() instanceof ViewGroup) {
                                    ((ViewGroup) toast.getParent()).removeView(toast);
                                }
                            }
                        }).start();
            }
        }, 1800);
    }
}
