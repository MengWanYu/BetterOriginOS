package com.betteroriginos.vivouidemo;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.betteroriginos.vivouidemo.ui.VivoScaffold;
import com.betteroriginos.vivouidemo.ui.VivoTheme;
import com.betteroriginos.vivouidemo.ui.VivoToast;
import com.betteroriginos.vivouidemo.ui.VivoTopBar;
import com.betteroriginos.vivouidemo.ui.VivoUi;

import java.util.ArrayList;
import java.util.List;

public class SettingsActivity extends Activity {
    private VivoScaffold scaffold;
    private VivoTopBar topBar;
    private final List<GroupRef> groups = new ArrayList<>();
    private TextView emptyView;

    private static class RowRef {
        View row;
        String title;
        int originalIndex;

        RowRef(View row, String title, int originalIndex) {
            this.row = row;
            this.title = title;
            this.originalIndex = originalIndex;
        }
    }

    private static class GroupRef {
        LinearLayout group;
        final List<RowRef> rows = new ArrayList<>();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        VivoTheme.applyWindow(this);
        scaffold = new VivoScaffold(this);
        topBar = new VivoTopBar(this, true);
        topBar.setTitle(getString(R.string.settings_title));
        EditText search = topBar.enableSearch(getString(R.string.settings_search_hint));
        scaffold.setTopBar(topBar);
        setContentView(scaffold);
        scaffold.requestApplyInsets();
        buildBody();
        search.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                applyFilter(s == null ? "" : s.toString().trim());
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });
        scaffold.invalidateBars();
    }

    private void buildBody() {
        LinearLayout body = scaffold.getBody();
        int blue = VivoTheme.color(this, R.color.vivo_accent);
        int green = VivoTheme.color(this, R.color.vivo_green);
        int orange = VivoTheme.color(this, R.color.vivo_orange);
        int red = VivoTheme.color(this, R.color.vivo_red);
        int purple = VivoTheme.color(this, R.color.vivo_purple);
        int gray = VivoTheme.color(this, R.color.vivo_icon_gray);

        GroupRef connectivity = newGroup(body, 8);
        addRow(connectivity, "wifi", blue, getString(R.string.row_wifi), null,
                getString(R.string.row_wifi_value));
        addRow(connectivity, "bluetooth", blue, getString(R.string.row_bt), null,
                getString(R.string.row_bt_value));
        addRow(connectivity, "cellular", green, getString(R.string.row_mobile), null, null);
        addRow(connectivity, "hotspot", orange, getString(R.string.row_hotspot), null,
                getString(R.string.row_hotspot_value));
        addRow(connectivity, "link", blue, getString(R.string.row_more), null, null);
        finishGroup(body, connectivity, 8);

        GroupRef personal = newGroup(body, 0);
        addRow(personal, "notification", gray, getString(R.string.row_notification), null, null);
        addRow(personal, "sound", red, getString(R.string.row_sound), null, null);
        addRow(personal, "brightness", orange, getString(R.string.row_display), null, null);
        addRow(personal, "wallpaper", blue, getString(R.string.row_wallpaper), null, null);
        finishGroup(body, personal, 8);

        GroupRef apps = newGroup(body, 0);
        addRow(apps, "apps", blue, getString(R.string.row_apps), null, null);
        addRow(apps, "fingerprint", purple, getString(R.string.row_fingerprint), null, null);
        addRow(apps, "shield", green, getString(R.string.row_security), null, null);
        addRow(apps, "privacy", blue, getString(R.string.row_privacy), null, null);
        addRow(apps, "storage", orange, getString(R.string.row_storage), null, null);
        addRow(apps, "battery", green, getString(R.string.row_battery), null, null);
        finishGroup(body, apps, 8);

        GroupRef system = newGroup(body, 0);
        addRow(system, "navigation", blue, getString(R.string.row_navigation), null, null);
        addRow(system, "shortcut", purple, getString(R.string.row_shortcuts), null, null);
        addRow(system, "screenshot", red, getString(R.string.row_screenshot), null, null);
        finishGroup(body, system, 8);

        emptyView = new TextView(this);
        VivoTheme.text(emptyView, 15f, VivoTheme.color(this, R.color.vivo_text_secondary),
                VivoTheme.W_REGULAR);
        emptyView.setText(getString(R.string.settings_empty));
        emptyView.setGravity(android.view.Gravity.CENTER);
        emptyView.setVisibility(View.GONE);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = VivoTheme.dpi(this, 96);
        body.addView(emptyView, lp);
    }

    private GroupRef newGroup(LinearLayout body, float topDp) {
        GroupRef ref = new GroupRef();
        ref.group = VivoUi.group(this);
        groups.add(ref);
        return ref;
    }

    private void finishGroup(LinearLayout body, GroupRef ref, float bottomDp) {
        VivoUi.addGroup(body, ref.group, 0, bottomDp);
    }

    private void addRow(GroupRef group, String icon, int color, String title, String summary,
                        String value) {
        final int index = group.rows.size();
        View row = VivoUi.row(this, icon, color, title, summary, value, true, null);
        row.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openRow(title);
            }
        });
        group.group.addView(row);
        group.rows.add(new RowRef(row, title, index));
    }

    private void openRow(String title) {
        if (getString(R.string.row_display).equals(title)) {
            startActivity(new Intent(this, SettingsDetailActivity.class));
        } else {
            VivoToast.show(this, getString(R.string.demo_row_toast, title));
        }
    }

    private void applyFilter(String query) {
        String q = query.toLowerCase();
        boolean anyVisible = false;
        for (GroupRef group : groups) {
            List<RowRef> visible = new ArrayList<>();
            for (RowRef ref : group.rows) {
                boolean match = q.isEmpty() || ref.title.toLowerCase().contains(q);
                ref.row.setVisibility(match ? View.VISIBLE : View.GONE);
                if (match) {
                    visible.add(ref);
                }
            }
            group.group.setVisibility(visible.isEmpty() ? View.GONE : View.VISIBLE);
            for (int i = 0; i < visible.size(); i++) {
                RowRef ref = visible.get(i);
                boolean top = i == 0;
                boolean bottom = i == visible.size() - 1;
                ref.row.setBackground(VivoTheme.rowBackground(this, top, bottom));
                View divider = ref.row.findViewWithTag("row_divider");
                if (divider != null) {
                    divider.setVisibility(bottom ? View.GONE : View.VISIBLE);
                }
            }
            if (!visible.isEmpty()) {
                anyVisible = true;
            }
        }
        emptyView.setVisibility(anyVisible ? View.GONE : View.VISIBLE);
        scaffold.invalidateBars();
    }
}
