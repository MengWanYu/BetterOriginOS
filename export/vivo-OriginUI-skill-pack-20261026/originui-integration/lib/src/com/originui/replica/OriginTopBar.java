package com.originui.replica;

import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/**
 * OriginOS 模糊顶栏（TYPE_TOOLBAR 材质）。
 *
 * 交互逆向：
 * - 顶栏为 VBlurLinearLayout（TYPE_TOOLBAR），列表滚动时 blurAlpha 随滚动距离线性渐入
 *   （VBlurUtils.getScrollBlurAlpha，critical=16dp）→ attach(ScrollView/ListVIew)。
 * - 大标题（28sp medium）在页面顶部时可见，滚动后顶栏保留模糊+发丝线。
 */
public class OriginTopBar extends OriginBlurLayout {
    private final TextView mTitleView;
    private final LinearLayout mActions;
    private static final float SCROLL_ALPHA_CRITICAL_DP = 16f;

    public OriginTopBar(Context context) {
        super(context);
        setOrientation(HORIZONTAL);
        setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
        int padH = OriginTheme.dpi(context, 20);
        setPadding(padH, 0, padH, 0);
        setMinimumHeight(OriginTheme.dpi(context, 56));
        setElevation(0);

        mTitleView = new TextView(context);
        OriginTheme.text(mTitleView, 17f, OriginTheme.textPrimary(context), OriginTheme.W_MEDIUM);
        addView(mTitleView, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        mActions = new LinearLayout(context);
        mActions.setOrientation(HORIZONTAL);
        mActions.setGravity(Gravity.CENTER_VERTICAL);
        addView(mActions, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        setBlurType(OriginBlur.TOOLBAR);
        setBlurAlpha(0f);
        setDividerBottom(true);
    }

    public void setTitle(CharSequence title) {
        mTitleView.setText(title);
    }

    public View addAction(CharSequence label, View.OnClickListener listener) {
        TextView tv = new TextView(getContext());
        OriginTheme.text(tv, 14f, OriginTheme.accent(getContext()), OriginTheme.W_MEDIUM);
        tv.setText(label);
        tv.setPadding(OriginTheme.dpi(getContext(), 10), OriginTheme.dpi(getContext(), 8),
                0, OriginTheme.dpi(getContext(), 8));
        tv.setOnClickListener(listener);
        mActions.addView(tv);
        return tv;
    }

    /** 与 ScrollView 联动：滚动 <16dp 时模糊与发丝线渐入。 */
    public void attach(final ScrollView scroll) {
        scroll.getViewTreeObserver().addOnScrollChangedListener(
                new android.view.ViewTreeObserver.OnScrollChangedListener() {
                    @Override
                    public void onScrollChanged() {
                        float dist = Math.max(0, scroll.getScrollY());
                        float alpha = OriginBlur.scrollAlpha(dist,
                                OriginTheme.dp(getContext(), SCROLL_ALPHA_CRITICAL_DP));
                        setBlurAlpha(alpha);
                    }
                });
    }

    public void attach(final AbsListView list) {
        list.setOnScrollListener(new AbsListView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(AbsListView view, int scrollState) {
            }

            @Override
            public void onScroll(AbsListView view, int firstVisibleItem, int visibleItemCount,
                    int totalItemCount) {
                View first = list.getChildAt(0);
                int dist = first == null ? 0 : -first.getTop() + list.getFirstVisiblePosition() * 0;
                float alpha = OriginBlur.scrollAlpha(Math.max(0, dist),
                        OriginTheme.dp(getContext(), SCROLL_ALPHA_CRITICAL_DP));
                setBlurAlpha(alpha);
            }
        });
    }
}
