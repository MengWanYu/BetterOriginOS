package com.betteroriginos.vivouidemo.ui;

import android.content.Context;
import android.os.Build;
import android.view.View;
import android.view.WindowInsets;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;

import com.betteroriginos.vivouidemo.R;

public class VivoScaffold extends FrameLayout {
    private final ScrollView scroll;
    private final LinearLayout body;
    private VivoTopBar topBar;
    private VivoBottomBar bottomBar;
    private int lastImeBottom;

    public VivoScaffold(Context context) {
        super(context);
        setBackgroundColor(VivoTheme.color(context, R.color.vivo_bg));
        setClipChildren(false);
        setClipToPadding(false);

        scroll = new ScrollView(context);
        scroll.setFillViewport(true);
        scroll.setClipChildren(false);
        scroll.setClipToPadding(false);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        scroll.setVerticalFadingEdgeEnabled(false);

        body = new LinearLayout(context);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setClipChildren(false);
        body.setClipToPadding(false);
        scroll.addView(body, new ScrollView.LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        addView(scroll, new FrameLayout.LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));

        scroll.setOnScrollChangeListener(new OnScrollChangeListener() {
            @Override
            public void onScrollChange(View v, int scrollX, int scrollY, int oldScrollX, int oldScrollY) {
                invalidateBars();
            }
        });

        setOnApplyWindowInsetsListener(new OnApplyWindowInsetsListener() {
            @Override
            public WindowInsets onApplyWindowInsets(View v, WindowInsets insets) {
                applyInsets(insets);
                return insets;
            }
        });
    }

    public ScrollView getScroll() {
        return scroll;
    }

    public LinearLayout getBody() {
        return body;
    }

    public void setTopBar(VivoTopBar bar) {
        if (topBar != null) {
            removeView(topBar);
        }
        topBar = bar;
        addView(bar, new FrameLayout.LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT,
                android.view.Gravity.TOP));
        bar.setBlurSource(scroll);
        bar.bringToFront();
        requestApplyInsets();
        requestLayout();
    }

    public void setBottomBar(VivoBottomBar bar) {
        if (bottomBar != null) {
            removeView(bottomBar);
        }
        bottomBar = bar;
        addView(bar, new FrameLayout.LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT,
                android.view.Gravity.BOTTOM));
        bar.setBlurSource(scroll);
        bar.bringToFront();
        requestApplyInsets();
        requestLayout();
    }

    public VivoTopBar getTopBar() {
        return topBar;
    }

    public VivoBottomBar getBottomBar() {
        return bottomBar;
    }

    private void applyInsets(WindowInsets insets) {
        int top;
        int bottom;
        int ime = 0;
        if (Build.VERSION.SDK_INT >= 30) {
            android.graphics.Insets bars = insets.getInsets(WindowInsets.Type.systemBars());
            top = bars.top;
            bottom = bars.bottom;
            ime = insets.getInsets(WindowInsets.Type.ime()).bottom;
        } else {
            top = insets.getSystemWindowInsetTop();
            bottom = insets.getSystemWindowInsetBottom();
        }
        lastImeBottom = ime;
        if (topBar != null) {
            topBar.setStatusInset(top);
        }
        if (bottomBar != null) {
            bottomBar.setNavigationInset(bottom);
        }
        updateScrollPadding();
        post(new Runnable() {
            @Override
            public void run() {
                invalidateBars();
            }
        });
    }

    private void updateScrollPadding() {
        int top = topBar == null ? 0 : topBar.getTotalHeight();
        int bottom = bottomBar == null || bottomBar.getVisibility() != View.VISIBLE
                ? 0 : bottomBar.getTotalHeight();
        if (lastImeBottom > bottom) {
            bottom = lastImeBottom;
        }
        scroll.setPadding(0, top + VivoTheme.dpi(getContext(), 8),
                0, bottom + VivoTheme.dpi(getContext(), 16));
    }

    public void invalidateBars() {
        if (topBar != null) {
            topBar.invalidate();
        }
        if (bottomBar != null) {
            bottomBar.invalidate();
        }
    }

    public void scrollToTop() {
        scroll.scrollTo(0, 0);
    }

    public void refreshPadding() {
        updateScrollPadding();
        requestLayout();
    }

    public int getTopBarHeight() {
        return topBar == null ? 0 : topBar.getTotalHeight();
    }

    public int getBottomBarHeight() {
        return bottomBar == null ? 0 : bottomBar.getTotalHeight();
    }
}
