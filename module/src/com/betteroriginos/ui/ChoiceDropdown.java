package com.betteroriginos.ui;

import android.app.Activity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.ListPopupWindow;
import android.widget.TextView;

import com.betteroriginos.R;
import com.betteroriginos.core.FeatureOption;

/** 行内下拉选择：锚定在选项行下方，菜单项带预览图与勾选态 */
public final class ChoiceDropdown {
    public interface PickListener {
        void onPick(String value);
    }

    private ChoiceDropdown() {
    }

    public static void show(Activity act, View anchor, final FeatureOption opt,
                            String current, final PickListener listener) {
        if (opt.values == null || opt.labelRes == null) {
            return;
        }
        final int currentIndex = opt.indexOf(current);
        final LayoutInflater inflater = LayoutInflater.from(act);

        ListPopupWindow popup = new ListPopupWindow(act);
        popup.setAnchorView(anchor);
        popup.setBackgroundDrawable(act.getDrawable(R.drawable.bg_menu));
        popup.setModal(true);
        popup.setAdapter(new BaseAdapter() {
            @Override
            public int getCount() {
                return opt.values.length;
            }

            @Override
            public Object getItem(int position) {
                return opt.values[position];
            }

            @Override
            public long getItemId(int position) {
                return position;
            }

            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                View row = convertView;
                if (row == null) {
                    row = inflater.inflate(R.layout.item_choice, parent, false);
                }
                ImageView icon = row.findViewById(R.id.choice_icon);
                if (opt.iconRes != null && position < opt.iconRes.length
                        && opt.iconRes[position] != 0) {
                    icon.setImageResource(opt.iconRes[position]);
                    icon.setVisibility(View.VISIBLE);
                } else {
                    icon.setVisibility(View.GONE);
                }
                ((TextView) row.findViewById(R.id.choice_label)).setText(opt.labelRes[position]);
                row.findViewById(R.id.choice_check).setVisibility(
                        position == currentIndex ? View.VISIBLE : View.INVISIBLE);
                return row;
            }
        });
        popup.setOnItemClickListener((parent, view, position, id) -> {
            popup.dismiss();
            listener.onPick(opt.values[position]);
        });
        popup.setContentWidth(Math.max(anchor.getWidth(), dp(act, 260)));
        popup.setVerticalOffset(dp(act, 6));
        popup.show();
    }

    private static int dp(Activity act, int value) {
        return Math.round(value * act.getResources().getDisplayMetrics().density);
    }
}
