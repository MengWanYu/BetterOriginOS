package com.betteroriginos.vivouidemo.ui;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.betteroriginos.vivouidemo.R;

public class VivoBottomBar extends BlurBar {
    public static final float BAR_HEIGHT = 66f;

    public interface OnItemSelectedListener {
        void onItemSelected(int index);
    }

    private static class Item {
        LinearLayout root;
        ImageView icon;
        TextView label;
        String iconName;
        int iconRes;
    }

    private final LinearLayout row;
    private final Item[] items;
    private int selectedIndex = 0;
    private int navigationInset;
    private OnItemSelectedListener listener;
    private boolean notifySelection = true;

    public VivoBottomBar(Context context) {
        super(context);
        setBlurRadiusDp(30f);
        setTintAlpha(0.93f);
        setDividerTop(true);

        row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        addView(row, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, VivoTheme.dpi(context, BAR_HEIGHT)));
        items = new Item[4];
    }

    public void setItems(String[] labels, String[] iconNames) {
        clearItems();
        for (int i = 0; i < labels.length && i < 4; i++) {
            addItem(i, labels[i], iconNames[i], 0);
        }
        updateSelection(false);
    }

    public void setItemsWithResources(String[] labels, int[] iconRes) {
        clearItems();
        for (int i = 0; i < labels.length && i < 4; i++) {
            addItem(i, labels[i], null, iconRes[i]);
        }
        updateSelection(false);
    }

    private void clearItems() {
        row.removeAllViews();
        for (int i = 0; i < items.length; i++) {
            items[i] = null;
        }
    }

    private void addItem(final int index, String label, String iconName, int iconRes) {
        Item item = new Item();
        item.iconName = iconName;
        item.iconRes = iconRes;
        item.root = new LinearLayout(getContext());
        item.root.setOrientation(LinearLayout.VERTICAL);
        item.root.setGravity(Gravity.CENTER);
        item.root.setClickable(true);
        item.root.setFocusable(true);

        GradientDrawable mask = new GradientDrawable();
        mask.setColor(Color.WHITE);
        mask.setCornerRadius(VivoTheme.dp(getContext(), 12));
        GradientDrawable content = new GradientDrawable();
        content.setColor(Color.TRANSPARENT);
        content.setCornerRadius(VivoTheme.dp(getContext(), 12));
        item.root.setBackground(new RippleDrawable(
                ColorStateList.valueOf(VivoTheme.color(getContext(), R.color.vivo_ripple)),
                content, mask));

        item.icon = new ImageView(getContext());
        item.icon.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        LinearLayout.LayoutParams iconLp = new LinearLayout.LayoutParams(
                VivoTheme.dpi(getContext(), 24), VivoTheme.dpi(getContext(), 24));
        item.root.addView(item.icon, iconLp);

        item.label = new TextView(getContext());
        VivoTheme.text(item.label, 11f, VivoTheme.color(getContext(), R.color.vivo_nav_unselected),
                VivoTheme.W_REGULAR);
        item.label.setSingleLine(true);
        item.label.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams labelLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        labelLp.topMargin = VivoTheme.dpi(getContext(), 2);
        item.root.addView(item.label, labelLp);

        item.root.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                select(index, true);
            }
        });
        item.root.setContentDescription(label);

        row.addView(item.root, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.MATCH_PARENT, 1f));
        items[index] = item;
    }

    public void select(int index, boolean notify) {
        if (index < 0 || index >= items.length || items[index] == null) {
            return;
        }
        if (selectedIndex == index && notify) {
            // reselect still animates the icon, like the OS5 navigation bar
        }
        selectedIndex = index;
        updateSelection(notify);
    }

    public int getSelectedIndex() {
        return selectedIndex;
    }

    public void setOnItemSelectedListener(OnItemSelectedListener l) {
        listener = l;
    }

    private void updateSelection(boolean notify) {
        int accent = VivoTheme.color(getContext(), R.color.vivo_accent);
        int normal = VivoTheme.color(getContext(), R.color.vivo_nav_unselected);
        for (int i = 0; i < items.length; i++) {
            Item item = items[i];
            if (item == null) {
                continue;
            }
            boolean selected = i == selectedIndex;
            int color = selected ? accent : normal;
            item.label.setTextColor(color);
            item.label.setTypeface(VivoTheme.typeface(selected ? VivoTheme.W_MEDIUM : VivoTheme.W_REGULAR));
            if (item.iconRes != 0) {
                item.icon.setImageResource(item.iconRes);
                item.icon.setColorFilter(color, PorterDuff.Mode.SRC_IN);
            } else {
                item.icon.setImageDrawable(VivoIcons.of(item.iconName == null ? "dot" : item.iconName, color, 24));
            }
            if (selected) {
                item.icon.animate().cancel();
                item.icon.setScaleX(1f);
                item.icon.setScaleY(1f);
                item.icon.animate().scaleX(1.08f).scaleY(1.08f).setDuration(110)
                        .withEndAction(new Runnable() {
                            @Override
                            public void run() {
                                item.icon.animate().scaleX(1f).scaleY(1f).setDuration(130).start();
                            }
                        }).start();
            } else {
                item.icon.animate().cancel();
                item.icon.setScaleX(1f);
                item.icon.setScaleY(1f);
            }
        }
        if (notify && listener != null) {
            listener.onItemSelected(selectedIndex);
        }
    }

    public void setNavigationInset(int px) {
        if (navigationInset == px) {
            return;
        }
        navigationInset = px;
        setPadding(0, 0, 0, px);
        requestLayout();
    }

    public int getTotalHeight() {
        return VivoTheme.dpi(getContext(), BAR_HEIGHT) + navigationInset;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
    }
}
