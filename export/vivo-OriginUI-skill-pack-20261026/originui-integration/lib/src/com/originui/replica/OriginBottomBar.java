package com.originui.replica;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * OriginOS 模糊底栏（TYPE_SHEET_DIALOG 材质，i 管家/文件管理底部导航语义）。
 *
 * 逆向：应用侧底栏类直接 extends VBlurLinearLayout（如文件管理
 * LiteRecentFilesBottomTabBar），材质 TYPE_SHEET_DIALOG(4)→light 204/dark 254；
 * 顶部发丝线（VBlurLinearLayout.setDividerBottom 默认 true，底栏场景翻转）。
 * tab：图标 24dp + 标签 10sp，选中 accent / 未选 text_secondary。
 */
public class OriginBottomBar extends OriginBlurLayout {
    public interface OnItemSelectedListener {
        void onItemSelected(int index);
    }

    private final LinearLayout mTabs;
    private int mSelected = -1;
    private OnItemSelectedListener mListener;
    private int mItemCount;

    public OriginBottomBar(Context context) {
        super(context);
        setOrientation(VERTICAL);
        setDividerBottom(false);
        setBlurType(OriginBlur.SHEET_DIALOG);
        setBlurAlpha(1f);
        setMinimumHeight(OriginTheme.dpi(context, 64));

        mTabs = new LinearLayout(context);
        mTabs.setOrientation(HORIZONTAL);
        addView(mTabs, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, OriginTheme.dpi(context, 64)));
        setPadding(0, OriginTheme.dpi(context, 6), 0, OriginTheme.dpi(context, 2));
    }

    /** tab 定义：label + 图标绘制器（用文字符号或 App 自供 icon）。 */
    public void setItems(CharSequence[] labels, IconFactory factory,
            OnItemSelectedListener listener) {
        mTabs.removeAllViews();
        mItemCount = labels.length;
        mListener = listener;
        for (int i = 0; i < labels.length; i++) {
            final int index = i;
            LinearLayout tab = new LinearLayout(getContext());
            tab.setOrientation(VERTICAL);
            tab.setGravity(Gravity.CENTER);
            View icon = factory.createIcon(i, 24);
            tab.addView(icon, new LinearLayout.LayoutParams(
                    OriginTheme.dpi(getContext(), 24), OriginTheme.dpi(getContext(), 24)));
            TextView label = new TextView(getContext());
            OriginTheme.text(label, 10f, 0, OriginTheme.W_MEDIUM);
            label.setText(labels[i]);
            label.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            llp.topMargin = OriginTheme.dpi(getContext(), 2);
            tab.addView(label, llp);
            tab.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    select(index, true);
                }
            });
            mTabs.addView(tab, new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));
        }
        if (labels.length > 0) {
            select(0, false);
        }
    }

    public interface IconFactory {
        View createIcon(int index, int sizeDp);
    }

    public void select(int index, boolean notify) {
        if (index == mSelected || index < 0 || index >= mItemCount) {
            return;
        }
        mSelected = index;
        for (int i = 0; i < mTabs.getChildCount(); i++) {
            LinearLayout tab = (LinearLayout) mTabs.getChildAt(i);
            boolean on = i == index;
            TextView label = (TextView) tab.getChildAt(1);
            label.setTextColor(on ? OriginTheme.accent(getContext())
                    : OriginTheme.textSecondary(getContext()));
            View icon = tab.getChildAt(0);
            icon.setAlpha(on ? 1f : 0.62f);
        }
        if (notify && mListener != null) {
            mListener.onItemSelected(index);
        }
    }
}
