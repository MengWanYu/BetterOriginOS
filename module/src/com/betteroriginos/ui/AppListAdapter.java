package com.betteroriginos.ui;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import com.betteroriginos.R;
import com.betteroriginos.core.Feature;
import com.betteroriginos.core.FeatureManager;
import com.betteroriginos.core.IconMapper;
import com.betteroriginos.core.Prefs;

import java.util.ArrayList;
import java.util.List;

/** 首页应用列表：每个作用域应用一张分组卡，卡间以透明间隔行分隔（ListView 无 item margin） */
public class AppListAdapter extends BaseAdapter {
    private static final int TYPE_APP = 0;
    private static final int TYPE_SPACER = 1;

    private static class Row {
        String pkg;
        boolean first;
        boolean last;
        boolean spacer;
    }

    private final Context ctx;
    private final LayoutInflater inflater;
    private final List<Row> rows = new ArrayList<>();

    AppListAdapter(Context ctx) {
        this.ctx = ctx;
        this.inflater = LayoutInflater.from(ctx);
        List<String> packages = FeatureManager.appPackages();
        for (int i = 0; i < packages.size(); i++) {
            Row r = new Row();
            r.pkg = packages.get(i);
            r.first = true;
            r.last = true;
            rows.add(r);
            if (i < packages.size() - 1) {
                Row gap = new Row();
                gap.spacer = true;
                rows.add(gap);
            }
        }
    }

    @Override
    public int getCount() {
        return rows.size();
    }

    @Override
    public Object getItem(int position) {
        return rows.get(position);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public int getViewTypeCount() {
        return 2;
    }

    @Override
    public int getItemViewType(int position) {
        return rows.get(position).spacer ? TYPE_SPACER : TYPE_APP;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        Row row = rows.get(position);
        if (row.spacer) {
            if (convertView == null) {
                convertView = new View(ctx);
                convertView.setLayoutParams(new ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, dp(8)));
            }
            return convertView;
        }

        View v = convertView;
        if (v == null) {
            v = inflater.inflate(R.layout.item_app, parent, false);
        }
        v.setBackgroundResource(!row.first && row.last ? R.drawable.bg_item_bot
                : row.first && !row.last ? R.drawable.bg_item_top
                : row.first ? R.drawable.bg_card : R.drawable.bg_item_mid);

        ImageView icon = v.findViewById(R.id.app_icon);
        icon.setImageDrawable(IconMapper.forPackage(ctx, row.pkg));
        ((TextView) v.findViewById(R.id.app_name)).setText(IconMapper.labelOf(ctx, row.pkg));
        ((TextView) v.findViewById(R.id.app_package)).setText(row.pkg);

        List<Feature> features = FeatureManager.forPackage(row.pkg);
        int enabled = 0;
        for (Feature f : features) {
            if (Prefs.featureEnabled(ctx, f.id(), f.defaultEnabled())) {
                enabled++;
            }
        }
        ((TextView) v.findViewById(R.id.app_counts)).setText(
                ctx.getString(R.string.app_features_format, features.size(), enabled));
        v.findViewById(R.id.app_divider).setVisibility(row.last ? View.GONE : View.VISIBLE);

        v.findViewById(R.id.app_row_content)
                .setOnClickListener(view -> AppDetailActivity.start(ctx, row.pkg));
        return v;
    }

    private int dp(int value) {
        return Math.round(value * ctx.getResources().getDisplayMetrics().density);
    }
}
