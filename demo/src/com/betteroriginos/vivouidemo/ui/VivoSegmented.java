package com.betteroriginos.vivouidemo.ui;

import android.content.Context;
import android.graphics.Color;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.betteroriginos.vivouidemo.R;

public class VivoSegmented extends LinearLayout {
    public interface OnSelectedListener {
        void onSelected(int index);
    }

    private final LinearLayout container;
    private String[] options = new String[0];
    private int selectedIndex;
    private OnSelectedListener listener;

    public VivoSegmented(Context context) {
        super(context);
        setOrientation(VERTICAL);
        container = new LinearLayout(context);
        container.setOrientation(HORIZONTAL);
        container.setPadding(VivoTheme.dpi(context, 3), VivoTheme.dpi(context, 3),
                VivoTheme.dpi(context, 3), VivoTheme.dpi(context, 3));
        container.setBackground(VivoTheme.rounded(context,
                VivoTheme.color(context, R.color.vivo_segment_bg), 18f));
        addView(container, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                VivoTheme.dpi(context, 36)));
    }

    public void setOptions(String[] values) {
        options = values;
        rebuild();
    }

    private void rebuild() {
        container.removeAllViews();
        for (int i = 0; i < options.length; i++) {
            final int index = i;
            TextView tv = new TextView(getContext());
            tv.setGravity(Gravity.CENTER);
            tv.setSingleLine(true);
            tv.setText(options[i]);
            tv.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    setSelectedIndex(index, true);
                }
            });
            container.addView(tv, new LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));
        }
        styleOptions();
    }

    public void setSelectedIndex(int index, boolean notify) {
        if (index < 0 || index >= options.length) {
            return;
        }
        selectedIndex = index;
        styleOptions();
        if (notify && listener != null) {
            listener.onSelected(index);
        }
    }

    public int getSelectedIndex() {
        return selectedIndex;
    }

    public void setOnSelectedListener(OnSelectedListener l) {
        listener = l;
    }

    private void styleOptions() {
        Context c = getContext();
        for (int i = 0; i < container.getChildCount(); i++) {
            TextView tv = (TextView) container.getChildAt(i);
            boolean selected = i == selectedIndex;
            tv.setTextSize(13f);
            tv.setTypeface(VivoTheme.typeface(selected ? VivoTheme.W_SEMI : VivoTheme.W_MEDIUM));
            tv.setTextColor(selected
                    ? VivoTheme.color(c, R.color.vivo_text_primary)
                    : VivoTheme.color(c, R.color.vivo_text_secondary));
            if (selected) {
                tv.setBackground(VivoTheme.rounded(c,
                        VivoTheme.color(c, R.color.vivo_segment_selected), 15f));
                tv.setElevation(VivoTheme.dp(c, 1.5f));
            } else {
                tv.setBackgroundColor(Color.TRANSPARENT);
                tv.setElevation(0f);
            }
        }
    }
}
