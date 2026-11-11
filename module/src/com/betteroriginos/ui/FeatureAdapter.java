package com.betteroriginos.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.graphics.Bitmap;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.LinearLayout;
import com.originui.replica.OriginSwitch;
import android.widget.TextView;

import com.betteroriginos.R;
import com.betteroriginos.core.Feature;
import com.betteroriginos.core.FeatureOption;
import com.betteroriginos.core.Prefs;
import com.betteroriginos.core.SuCommander;
import com.betteroriginos.feature.notify.NotifyRowPreview;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** 应用详情列表：可折叠分类头 + 每个可预览功能自己的实时预览 + 功能卡，支持文本过滤 */
public class FeatureAdapter extends BaseAdapter {
    private static final int TYPE_SECTION = 0;
    private static final int TYPE_PREVIEW = 1;
    private static final int TYPE_FEATURE = 2;

    private final AppDetailActivity act;
    private final LayoutInflater inflater;
    private final Map<Integer, List<Feature>> groups = new LinkedHashMap<>();
    private final List<Object> items = new ArrayList<>();
    private final Set<Integer> collapsed = new HashSet<>();
    private final Map<String, View> previewRows = new HashMap<>();
    private String filter = "";

    private static final class PreviewItem {
        final Feature feature;

        PreviewItem(Feature feature) {
            this.feature = feature;
        }
    }

    FeatureAdapter(AppDetailActivity act, List<Feature> features) {
        this.act = act;
        this.inflater = LayoutInflater.from(act);
        RealPreview.init(act);
        for (Feature f : features) {
            List<Feature> list = groups.get(f.categoryRes());
            if (list == null) {
                list = new ArrayList<>();
                groups.put(f.categoryRes(), list);
            }
            list.add(f);
        }
        rebuild();
    }

    /** 搜索过滤：命中功能标题/说明/选项文案；搜索时强制展开并隐藏预览 */
    void setFilter(String text) {
        filter = text == null ? "" : text.trim();
        rebuild();
        notifyDataSetChanged();
    }

    private void rebuild() {
        items.clear();
        boolean searching = !filter.isEmpty();
        for (Map.Entry<Integer, List<Feature>> e : groups.entrySet()) {
            List<Feature> visible = searching ? matched(e.getValue()) : e.getValue();
            if (visible.isEmpty()) {
                continue;
            }
            Integer key = e.getKey();
            items.add(key);
            boolean hidden = !searching && collapsed.contains(key);
            if (!hidden) {
                for (Feature feature : visible) {
                    if (!searching && feature.previewKind() != null) {
                        items.add(new PreviewItem(feature));
                    }
                    items.add(feature);
                }
            }
        }
    }

    private List<Feature> matched(List<Feature> list) {
        String f = filter.toLowerCase(Locale.getDefault());
        List<Feature> out = new ArrayList<>();
        for (Feature feat : list) {
            if (matches(feat, f)) {
                out.add(feat);
            }
        }
        return out;
    }

    private boolean matches(Feature feat, String f) {
        if (contains(act.getString(feat.titleRes()), f)
                || contains(act.getString(feat.summaryRes()), f)
                || contains(act.getString(feat.categoryRes()), f)) {
            return true;
        }
        for (FeatureOption o : feat.options()) {
            if (contains(act.getString(o.titleRes), f)
                    || contains(act.getString(o.summaryRes), f)) {
                return true;
            }
            if (o.labelRes != null) {
                for (int label : o.labelRes) {
                    if (contains(act.getString(label), f)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static boolean contains(String text, String filterLower) {
        return text != null && text.toLowerCase(Locale.getDefault()).contains(filterLower);
    }

    @Override
    public int getCount() {
        return items.size();
    }

    @Override
    public Object getItem(int position) {
        return items.get(position);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public int getViewTypeCount() {
        return 3;
    }

    @Override
    public int getItemViewType(int position) {
        Object item = items.get(position);
        if (item instanceof Integer) {
            return TYPE_SECTION;
        }
        return item instanceof PreviewItem ? TYPE_PREVIEW : TYPE_FEATURE;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        Object item = items.get(position);
        if (item instanceof Integer) {
            return bindSection((Integer) item, convertView, parent);
        }
        if (item instanceof PreviewItem) {
            return bindPreview(convertView, parent, (PreviewItem) item);
        }
        return bindFeature(convertView, parent, (Feature) item);
    }

    private View bindSection(final Integer key, View convertView, ViewGroup parent) {
        View row = convertView;
        if (row == null) {
            row = inflater.inflate(R.layout.item_section, parent, false);
        }
        int stringRes = key;
        ((TextView) row.findViewById(R.id.section_title)).setText(stringRes);
        boolean isCollapsed = collapsed.contains(key);
        row.findViewById(R.id.section_arrow).setRotation(isCollapsed ? 0f : 90f);
        row.setOnClickListener(v -> {
            if (!filter.isEmpty()) {
                return;
            }
            if (!collapsed.remove(key)) {
                collapsed.add(key);
            }
            rebuild();
            notifyDataSetChanged();
        });
        return row;
    }

    private View bindPreview(View convertView, ViewGroup parent, PreviewItem item) {
        View row = convertView;
        if (row == null) {
            row = inflater.inflate(R.layout.item_preview, parent, false);
        }
        final ImageView image = row.findViewById(R.id.preview_image);
        final View loading = row.findViewById(R.id.preview_loading);
        final Feature feature = item.feature;
        boolean enabled = Prefs.featureEnabled(act, feature.id(), feature.defaultEnabled());
        row.setAlpha(enabled ? 1f : 0.45f);
        previewRows.put(feature.id(), row);
        if ("notify".equals(feature.previewKind())) {
            image.setTag("notify");
            image.setImageBitmap(NotifyRowPreview.render(act));
            image.setVisibility(View.VISIBLE);
            loading.setVisibility(View.GONE);
            return row;
        }

        FeatureOption option = previewOption(feature);
        final String style = option == null ? ""
                : Prefs.option(act, feature.id(), option.key, option.defaultValue);
        image.setTag(style);
        if (RealPreview.hasCached(style)) {
            showPreview(image, loading, RealPreview.cached());
        } else {
            image.setVisibility(View.GONE);
            loading.setVisibility(View.VISIBLE);
            RealPreview.fetch(style, new RealPreview.Callback() {
                @Override
                public void onResult(Bitmap bitmap) {
                    if (!style.equals(image.getTag())) {
                        return;
                    }
                    if (bitmap != null) {
                        showPreview(image, loading, bitmap);
                    } else {
                        loading.setVisibility(View.GONE);
                    }
                }
            });
        }
        return row;
    }

    private static FeatureOption previewOption(Feature feature) {
        for (FeatureOption option : feature.options()) {
            if (option.type == FeatureOption.Type.CHOICE && option.iconRes != null
                    && option.iconRes.length > 0) {
                return option;
            }
        }
        return null;
    }

    private static void showPreview(ImageView image, View loading, Bitmap bitmap) {
        image.setImageBitmap(bitmap);
        image.setVisibility(View.VISIBLE);
        if (loading != null) {
            loading.setVisibility(View.GONE);
        }
    }

    private View bindFeature(View convertView, ViewGroup parent, final Feature feature) {
        View card = convertView;
        if (card == null) {
            card = inflater.inflate(R.layout.item_feature, parent, false);
        }
        ImageView icon = card.findViewById(R.id.feature_icon);
        if (feature.iconRes() != 0) {
            icon.setImageResource(feature.iconRes());
        } else {
            icon.setVisibility(View.GONE);
        }
        ((TextView) card.findViewById(R.id.feature_title)).setText(feature.titleRes());
        ((TextView) card.findViewById(R.id.feature_summary)).setText(feature.summaryRes());
        TextView note = card.findViewById(R.id.feature_note);
        int noteRes = feature.supportNoteRes(act);
        if (noteRes != 0) {
            note.setText(noteRes);
            note.setVisibility(View.VISIBLE);
        } else {
            note.setVisibility(View.GONE);
        }

        View options = card.findViewById(R.id.feature_options);
        View divider = card.findViewById(R.id.feature_divider);
        OriginSwitch featureSwitch = card.findViewById(R.id.feature_switch);
        final View cardView = card;
        final boolean enabled = Prefs.featureEnabled(act, feature.id(), feature.defaultEnabled());
        featureSwitch.setOnCheckedChangeListener(null);
        featureSwitch.setChecked(enabled, false);
        featureSwitch.setOnCheckedChangeListener((button, checked) -> {
            SuCommander.get().write(Prefs.featureKey(feature.id()), checked ? "1" : "0",
                    ok -> act.onWriteResult(ok));
            setOptionsEnabled(cardView, checked);
            View preview = previewRows.get(feature.id());
            if (preview != null) {
                preview.setAlpha(checked ? 1f : 0.45f);
            }
        });
        // 开关只由 Switch 控件本身切换，整行点击不触发（避免无提示误触）

        List<FeatureOption> opts = feature.options();
        if (opts.isEmpty()) {
            return card;
        }
        divider.setVisibility(View.VISIBLE);
        options.setVisibility(View.VISIBLE);
        ((LinearLayout) options).removeAllViews();
        setOptionsEnabled(card, enabled);
        for (FeatureOption opt : opts) {
            if (opt.type == FeatureOption.Type.SWITCH) {
                bindSwitchOption((LinearLayout) options, feature, opt);
            } else {
                bindChoiceOption((LinearLayout) options, feature, opt);
            }
        }
        return card;
    }

    private void setOptionsEnabled(View card, boolean enabled) {
        View options = card.findViewById(R.id.feature_options);
        options.setAlpha(enabled ? 1f : 0.45f);
        options.setEnabled(enabled);
        for (int i = 0; i < ((LinearLayout) options).getChildCount(); i++) {
            View child = ((LinearLayout) options).getChildAt(i);
            child.setEnabled(enabled);
            child.setClickable(enabled);
        }
    }

    private void bindChoiceOption(final LinearLayout parent, final Feature feature,
                                  final FeatureOption opt) {
        View row = inflater.inflate(R.layout.item_option_choice, parent, false);
        ((TextView) row.findViewById(R.id.option_title)).setText(opt.titleRes);
        ((TextView) row.findViewById(R.id.option_summary)).setText(opt.summaryRes);
        String value = Prefs.option(act, feature.id(), opt.key, opt.defaultValue);
        updateChoiceRow(row, opt, value);
        row.setOnClickListener(v -> ChoiceDropdown.show(act, row, opt, value,
                picked -> {
                    SuCommander.get().write(Prefs.optionKey(feature.id(), opt.key), picked,
                            ok -> act.onWriteResult(ok));
                    feature.onOptionChanged(opt.key, picked);
                    updateChoiceRow(row, opt, picked);
                    if (feature.previewKind() != null && opt == previewOption(feature)) {
                        notifyDataSetChanged();
                    }
                }));
        parent.addView(row);
    }

    private void bindSwitchOption(LinearLayout parent, final Feature feature,
                                  final FeatureOption opt) {
        View row = inflater.inflate(R.layout.item_option_switch, parent, false);
        ((TextView) row.findViewById(R.id.option_title)).setText(opt.titleRes);
        ((TextView) row.findViewById(R.id.option_summary)).setText(opt.summaryRes);
        OriginSwitch sw = row.findViewById(R.id.option_switch);
        sw.setChecked("1".equals(Prefs.option(act, feature.id(), opt.key, opt.defaultValue)), false);
        sw.setOnCheckedChangeListener((button, checked) ->
                SuCommander.get().write(Prefs.optionKey(feature.id(), opt.key),
                        checked ? "1" : "0", ok -> act.onWriteResult(ok)));
        parent.addView(row);
    }

    private void updateChoiceRow(View row, FeatureOption opt, String value) {
        int index = opt.indexOf(value);
        if (index < 0) {
            index = 0;
        }
        ((TextView) row.findViewById(R.id.option_value)).setText(opt.labelRes[index]);
        row.setTag(value);
    }
}
