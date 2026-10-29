package com.betteroriginos.vivouidemo;

import android.app.Activity;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.betteroriginos.vivouidemo.ui.VivoButton;
import com.betteroriginos.vivouidemo.ui.VivoCheckBox;
import com.betteroriginos.vivouidemo.ui.VivoDialog;
import com.betteroriginos.vivouidemo.ui.VivoIcons;
import com.betteroriginos.vivouidemo.ui.VivoPopup;
import com.betteroriginos.vivouidemo.ui.VivoScaffold;
import com.betteroriginos.vivouidemo.ui.VivoSegmented;
import com.betteroriginos.vivouidemo.ui.VivoSheet;
import com.betteroriginos.vivouidemo.ui.VivoSlider;
import com.betteroriginos.vivouidemo.ui.VivoSwitch;
import com.betteroriginos.vivouidemo.ui.VivoTheme;
import com.betteroriginos.vivouidemo.ui.VivoToast;
import com.betteroriginos.vivouidemo.ui.VivoTopBar;
import com.betteroriginos.vivouidemo.ui.VivoUi;

public class ComponentsActivity extends Activity {
    private VivoScaffold scaffold;
    private LinearLayout body;
    private View overlaysCard;
    private TextView sliderValue;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        VivoTheme.applyWindow(this);
        scaffold = new VivoScaffold(this);
        body = scaffold.getBody();
        VivoTopBar top = new VivoTopBar(this, true);
        top.setTitle(getString(R.string.comp_title));
        top.setSubtitle(getString(R.string.comp_subtitle));
        top.addAction(VivoTheme.night(this) ? "sun" : "moon", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                VivoTheme.toggleNight(ComponentsActivity.this);
            }
        });
        scaffold.setTopBar(top);
        setContentView(scaffold);
        scaffold.requestApplyInsets();
        buildBody();
        scaffold.invalidateBars();

        if ("overlays".equals(getIntent().getStringExtra("section")) && overlaysCard != null) {
            scaffold.getScroll().post(new Runnable() {
                @Override
                public void run() {
                    int y = overlaysCard.getTop();
                    scaffold.getScroll().scrollTo(0, Math.max(0, y));
                    scaffold.invalidateBars();
                }
            });
        }
    }

    private void buildBody() {
        int accent = VivoTheme.color(this, R.color.vivo_accent);
        int orange = VivoTheme.color(this, R.color.vivo_orange);
        int purple = VivoTheme.color(this, R.color.vivo_purple);
        int gray = VivoTheme.color(this, R.color.vivo_icon_gray);
        int green = VivoTheme.color(this, R.color.vivo_green);

        section(getString(R.string.comp_buttons), 12);
        LinearLayout buttonCard = VivoUi.card(this, 16);
        addStackButton(buttonCard, VivoButton.PRIMARY, VivoButton.SIZE_XL,
                getString(R.string.comp_primary), 0);
        addStackButton(buttonCard, VivoButton.SECONDARY, VivoButton.SIZE_XL,
                getString(R.string.comp_secondary), 12);
        addStackButton(buttonCard, VivoButton.STROKE, VivoButton.SIZE_XL,
                getString(R.string.comp_stroke), 12);
        addStackButton(buttonCard, VivoButton.TEXT, VivoButton.SIZE_XL,
                getString(R.string.comp_text), 12);

        LinearLayout midRow = new LinearLayout(this);
        midRow.setOrientation(LinearLayout.HORIZONTAL);
        midRow.setGravity(Gravity.CENTER_VERTICAL);
        VivoButton mid = baseButton(VivoButton.PRIMARY, VivoButton.SIZE_M, "中号按钮");
        VivoButton small = baseButton(VivoButton.SECONDARY, VivoButton.SIZE_S, getString(R.string.comp_small));
        LinearLayout.LayoutParams midLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        midLp.topMargin = VivoTheme.dpi(this, 12);
        midLp.rightMargin = VivoTheme.dpi(this, 6);
        midRow.addView(mid, midLp);
        LinearLayout.LayoutParams smallLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        smallLp.topMargin = VivoTheme.dpi(this, 12);
        smallLp.leftMargin = VivoTheme.dpi(this, 6);
        midRow.addView(small, smallLp);
        buttonCard.addView(midRow);

        VivoButton disabled = baseButton(VivoButton.PRIMARY, VivoButton.SIZE_XL,
                getString(R.string.comp_disabled));
        disabled.setEnabled(false);
        LinearLayout.LayoutParams disabledLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        disabledLp.topMargin = VivoTheme.dpi(this, 12);
        buttonCard.addView(disabled, disabledLp);
        VivoUi.addToBody(body, buttonCard, 0, 8);

        section(getString(R.string.comp_switches), 12);
        LinearLayout switchGroup = VivoUi.group(this);
        switchGroup.addView(switchRow("sun", orange, getString(R.string.comp_switch_on), true, true));
        switchGroup.addView(switchRow("moon", purple, getString(R.string.comp_switch_off), false, true));
        switchGroup.addView(switchRow("bell", gray, getString(R.string.comp_switch_disabled), false, false));
        VivoUi.addGroup(body, switchGroup, 0, 8);

        section(getString(R.string.comp_slider), 12);
        LinearLayout sliderCard = VivoUi.card(this, 16);
        LinearLayout sliderTitleRow = new LinearLayout(this);
        sliderTitleRow.setOrientation(LinearLayout.HORIZONTAL);
        sliderTitleRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView sliderTitle = VivoUi.headerText(this, getString(R.string.comp_slider_label),
                15f, VivoTheme.color(this, R.color.vivo_text_primary), VivoTheme.W_MEDIUM);
        sliderTitleRow.addView(sliderTitle, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        sliderValue = new TextView(this);
        VivoTheme.text(sliderValue, 13f, accent, VivoTheme.W_MEDIUM);
        sliderTitleRow.addView(sliderValue);
        sliderCard.addView(sliderTitleRow);

        LinearLayout sliderLine = new LinearLayout(this);
        sliderLine.setOrientation(LinearLayout.HORIZONTAL);
        sliderLine.setGravity(Gravity.CENTER_VERTICAL);
        ImageView smallSun = new ImageView(this);
        smallSun.setImageDrawable(VivoIcons.of("sun_small", gray, 20));
        sliderLine.addView(smallSun, new LinearLayout.LayoutParams(
                VivoTheme.dpi(this, 20), VivoTheme.dpi(this, 20)));
        final VivoSlider slider = new VivoSlider(this);
        slider.setProgress(70f);
        LinearLayout.LayoutParams sliderLp = new LinearLayout.LayoutParams(
                0, VivoTheme.dpi(this, 44), 1f);
        sliderLp.leftMargin = VivoTheme.dpi(this, 12);
        sliderLp.rightMargin = VivoTheme.dpi(this, 12);
        sliderLine.addView(slider, sliderLp);
        ImageView largeSun = new ImageView(this);
        largeSun.setImageDrawable(VivoIcons.of("sun_large", gray, 24));
        sliderLine.addView(largeSun, new LinearLayout.LayoutParams(
                VivoTheme.dpi(this, 24), VivoTheme.dpi(this, 24)));
        LinearLayout.LayoutParams lineLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, VivoTheme.dpi(this, 56));
        lineLp.topMargin = VivoTheme.dpi(this, 8);
        sliderCard.addView(sliderLine, lineLp);
        updateSliderLabel(70);
        slider.setOnProgressChangedListener(new VivoSlider.OnProgressChangedListener() {
            @Override
            public void onProgressChanged(VivoSlider s, float progress) {
                updateSliderLabel((int) progress);
            }
        });
        VivoUi.addToBody(body, sliderCard, 0, 8);

        section(getString(R.string.comp_selection), 12);
        LinearLayout selectionGroup = VivoUi.group(this);
        final VivoCheckBox cb1 = new VivoCheckBox(this);
        cb1.setChecked(true);
        cb1.setClickable(false);
        cb1.setFocusable(false);
        View cbRow1 = VivoUi.row(this, "clock", accent,
                getString(R.string.comp_checkbox), null, null, false, cb1);
        cbRow1.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                cb1.setChecked(!cb1.isChecked());
            }
        });
        selectionGroup.addView(cbRow1);

        final VivoCheckBox cb2 = new VivoCheckBox(this);
        cb2.setClickable(false);
        cb2.setFocusable(false);
        View cbRow2 = VivoUi.row(this, "bell", orange,
                getString(R.string.comp_checkbox_2), null, null, false, cb2);
        cbRow2.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                cb2.setChecked(!cb2.isChecked());
            }
        });
        selectionGroup.addView(cbRow2);

        final VivoCheckBox r1 = new VivoCheckBox(this, true);
        final VivoCheckBox r2 = new VivoCheckBox(this, true);
        r1.setChecked(true);
        r1.setClickable(false);
        r1.setFocusable(false);
        r2.setClickable(false);
        r2.setFocusable(false);
        View radioRow1 = VivoUi.row(this, "refresh", purple,
                getString(R.string.detail_refresh_smart), null, null, false, r1);
        radioRow1.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                r1.setChecked(true);
                r2.setChecked(false);
            }
        });
        selectionGroup.addView(radioRow1);
        View radioRow2 = VivoUi.row(this, "refresh", gray,
                getString(R.string.detail_refresh_120), null, null, false, r2);
        radioRow2.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                r2.setChecked(true);
                r1.setChecked(false);
            }
        });
        selectionGroup.addView(radioRow2);
        VivoUi.addGroup(body, selectionGroup, 0, 8);

        LinearLayout segmentCard = VivoUi.card(this, 16);
        TextView segmentTitle = VivoUi.headerText(this, getString(R.string.comp_segment_label),
                15f, VivoTheme.color(this, R.color.vivo_text_primary), VivoTheme.W_MEDIUM);
        segmentCard.addView(segmentTitle);
        final VivoSegmented segmented = new VivoSegmented(this);
        segmented.setOptions(new String[]{getString(R.string.detail_refresh_smart),
                getString(R.string.detail_refresh_60), getString(R.string.detail_refresh_120)});
        segmented.setSelectedIndex(0, false);
        LinearLayout.LayoutParams segLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, VivoTheme.dpi(this, 36));
        segLp.topMargin = VivoTheme.dpi(this, 12);
        segmentCard.addView(segmented, segLp);
        segmented.setOnSelectedListener(new VivoSegmented.OnSelectedListener() {
            @Override
            public void onSelected(int index) {
                VivoToast.show(ComponentsActivity.this,
                        "刷新率：" + new String[]{"智能切换", "60 Hz", "120 Hz"}[index]);
            }
        });
        VivoUi.addToBody(body, segmentCard, 0, 8);

        section(getString(R.string.comp_rows), 12);
        LinearLayout listGroup = VivoUi.group(this);
        final VivoSwitch syncSwitch = new VivoSwitch(this);
        syncSwitch.setChecked(true, false);
        syncSwitch.setOnCheckedChangeListener(new VivoSwitch.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(VivoSwitch view, boolean checked) {
                VivoToast.show(ComponentsActivity.this, checked ? "自动同步已开启" : "自动同步已关闭");
            }
        });
        listGroup.addView(VivoUi.switchRow(this, "cloud", green,
                getString(R.string.comp_row_switch), null, syncSwitch));
        View cacheRow = VivoUi.row(this, "storage", orange,
                getString(R.string.comp_row_value), null, getString(R.string.comp_row_value_summary), true, null);
        cacheRow.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                VivoToast.show(ComponentsActivity.this, "演示：缓存已清除");
            }
        });
        listGroup.addView(cacheRow);
        View updateRow = VivoUi.row(this, "refresh", accent,
                getString(R.string.comp_row_chevron), getString(R.string.comp_row_chevron_summary), null, true, null);
        updateRow.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                VivoToast.show(ComponentsActivity.this, "演示：已是最新版本");
            }
        });
        listGroup.addView(updateRow);
        VivoUi.addGroup(body, listGroup, 0, 8);

        section(getString(R.string.comp_overlays), 12);
        LinearLayout overlayCard = VivoUi.card(this, 16);
        TextView overlayTitle = VivoUi.headerText(this, getString(R.string.comp_overlays),
                15f, VivoTheme.color(this, R.color.vivo_text_primary), VivoTheme.W_MEDIUM);
        overlayCard.addView(overlayTitle);
        TextView note = new TextView(this);
        VivoTheme.text(note, 12f, VivoTheme.color(this, R.color.vivo_text_secondary),
                VivoTheme.W_REGULAR);
        note.setText(getString(R.string.comp_overlay_note));
        LinearLayout.LayoutParams noteLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        noteLp.topMargin = VivoTheme.dpi(this, 3);
        overlayCard.addView(note, noteLp);

        LinearLayout row1 = new LinearLayout(this);
        row1.setOrientation(LinearLayout.HORIZONTAL);
        VivoButton dialogButton = baseButton(VivoButton.STROKE, VivoButton.SIZE_M,
                getString(R.string.comp_dialog));
        dialogButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                VivoDialog.confirm(ComponentsActivity.this, getString(R.string.dialog_title),
                        getString(R.string.dialog_message), getString(R.string.dialog_confirm),
                        getString(R.string.dialog_cancel), new Runnable() {
                            @Override
                            public void run() {
                                VivoToast.show(ComponentsActivity.this, "已删除");
                            }
                        });
            }
        });
        LinearLayout.LayoutParams b1 = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        b1.topMargin = VivoTheme.dpi(this, 14);
        b1.rightMargin = VivoTheme.dpi(this, 6);
        row1.addView(dialogButton, b1);

        VivoButton sheetButton = baseButton(VivoButton.SECONDARY, VivoButton.SIZE_M,
                getString(R.string.comp_sheet));
        sheetButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                VivoSheet.show(ComponentsActivity.this, getString(R.string.sheet_title),
                        new String[]{getString(R.string.sheet_edit), getString(R.string.sheet_copy),
                                getString(R.string.sheet_delete)},
                        new String[]{"edit", "copy", "trash"},
                        new int[]{VivoTheme.color(ComponentsActivity.this, R.color.vivo_accent),
                                VivoTheme.color(ComponentsActivity.this, R.color.vivo_accent),
                                VivoTheme.color(ComponentsActivity.this, R.color.vivo_red)},
                        new VivoSheet.OnActionClickListener() {
                            @Override
                            public void onActionClick(int index) {
                                VivoToast.show(ComponentsActivity.this, "已选择：" + index);
                            }
                        });
            }
        });
        LinearLayout.LayoutParams b2 = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        b2.topMargin = VivoTheme.dpi(this, 14);
        b2.leftMargin = VivoTheme.dpi(this, 6);
        row1.addView(sheetButton, b2);
        overlayCard.addView(row1);

        LinearLayout row2 = new LinearLayout(this);
        row2.setOrientation(LinearLayout.HORIZONTAL);
        final VivoButton popupButton = baseButton(VivoButton.SECONDARY, VivoButton.SIZE_M,
                getString(R.string.comp_popup));
        popupButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                VivoPopup.show(popupButton, new String[]{"编辑闹钟", "复制到明天", "删除闹钟"},
                        new String[]{"edit", "copy", "trash"},
                        new VivoPopup.OnItemClickListener() {
                            @Override
                            public void onItemClick(int index) {
                                VivoToast.show(ComponentsActivity.this, "菜单项：" + index);
                            }
                        });
            }
        });
        LinearLayout.LayoutParams b3 = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        b3.topMargin = VivoTheme.dpi(this, 10);
        b3.rightMargin = VivoTheme.dpi(this, 6);
        row2.addView(popupButton, b3);

        VivoButton toastButton = baseButton(VivoButton.SECONDARY, VivoButton.SIZE_M,
                getString(R.string.comp_toast));
        toastButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                VivoToast.show(ComponentsActivity.this, getString(R.string.toast_demo));
            }
        });
        LinearLayout.LayoutParams b4 = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        b4.topMargin = VivoTheme.dpi(this, 10);
        b4.leftMargin = VivoTheme.dpi(this, 6);
        row2.addView(toastButton, b4);
        overlayCard.addView(row2);

        overlaysCard = overlayCard;
        VivoUi.addToBody(body, overlayCard, 0, 8);

        TextView footer = VivoUi.headerText(this, getString(R.string.comp_footer),
                11f, VivoTheme.color(this, R.color.vivo_text_tertiary), VivoTheme.W_REGULAR);
        footer.setGravity(Gravity.CENTER);
        footer.setPadding(0, VivoTheme.dpi(this, 18), 0, VivoTheme.dpi(this, 20));
        body.addView(footer, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    private VivoButton baseButton(int style, int size, String text) {
        VivoButton button = new VivoButton(this);
        button.setSize(size);
        button.setStyle(style);
        button.setText(text);
        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                VivoToast.show(ComponentsActivity.this, "按钮已点击");
            }
        });
        return button;
    }

    private void addStackButton(LinearLayout card, int style, int size, String text, float topDp) {
        VivoButton button = baseButton(style, size, text);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = VivoTheme.dpi(this, topDp);
        card.addView(button, lp);
    }

    private View switchRow(String icon, int color, String title, boolean checked, boolean enabled) {
        final VivoSwitch toggle = new VivoSwitch(this);
        toggle.setChecked(checked, false);
        toggle.setEnabled(enabled);
        toggle.setOnCheckedChangeListener(new VivoSwitch.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(VivoSwitch view, boolean isChecked) {
                VivoToast.show(ComponentsActivity.this, isChecked ? "已开启" : "已关闭");
            }
        });
        View row = VivoUi.switchRow(this, icon, color, title, null, toggle);
        row.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (toggle.isEnabled()) {
                    toggle.setChecked(!toggle.isChecked());
                }
            }
        });
        return row;
    }

    private void updateSliderLabel(int value) {
        if (sliderValue != null) {
            sliderValue.setText(getString(R.string.comp_slider_value, value));
        }
    }

    private void section(String title, float topDp) {
        TextView label = VivoUi.sectionLabel(this, title);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.leftMargin = VivoTheme.dpi(this, 24);
        lp.rightMargin = VivoTheme.dpi(this, 24);
        lp.topMargin = VivoTheme.dpi(this, topDp);
        lp.bottomMargin = VivoTheme.dpi(this, 6);
        body.addView(label, lp);
    }
}
