package com.betteroriginos.vivouidemo;

import android.app.Activity;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Shader;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.betteroriginos.vivouidemo.ui.VivoButton;
import com.betteroriginos.vivouidemo.ui.VivoDialog;
import com.betteroriginos.vivouidemo.ui.VivoIcons;
import com.betteroriginos.vivouidemo.ui.VivoScaffold;
import com.betteroriginos.vivouidemo.ui.VivoSegmented;
import com.betteroriginos.vivouidemo.ui.VivoSlider;
import com.betteroriginos.vivouidemo.ui.VivoSwitch;
import com.betteroriginos.vivouidemo.ui.VivoTheme;
import com.betteroriginos.vivouidemo.ui.VivoToast;
import com.betteroriginos.vivouidemo.ui.VivoTopBar;
import com.betteroriginos.vivouidemo.ui.VivoUi;

public class SettingsDetailActivity extends Activity {
    private VivoScaffold scaffold;
    private PreviewCard preview;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        VivoTheme.applyWindow(this);
        scaffold = new VivoScaffold(this);
        VivoTopBar top = new VivoTopBar(this, false);
        top.setTitle(getString(R.string.row_display));
        top.setBackVisible(true, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });
        top.addAction("help", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                VivoDialog.confirm(SettingsDetailActivity.this,
                        getString(R.string.row_display),
                        "这是 OriginOS 5 设置页的代码级复刻演示。滑块、开关与深色模式均可交互。",
                        "知道了", "关闭", null);
            }
        });
        scaffold.setTopBar(top);
        setContentView(scaffold);
        scaffold.requestApplyInsets();
        buildBody();
        scaffold.invalidateBars();
    }

    private void buildBody() {
        LinearLayout body = scaffold.getBody();
        int blue = VivoTheme.color(this, R.color.vivo_accent);
        int green = VivoTheme.color(this, R.color.vivo_green);
        int orange = VivoTheme.color(this, R.color.vivo_orange);
        int purple = VivoTheme.color(this, R.color.vivo_purple);

        section(body, getString(R.string.detail_brightness), 12, 8);

        LinearLayout brightnessGroup = VivoUi.group(this);
        final VivoSlider slider = new VivoSlider(this);
        slider.setProgress(64f);
        LinearLayout brightnessRow = new LinearLayout(this);
        brightnessRow.setOrientation(LinearLayout.VERTICAL);
        LinearLayout sliderLine = new LinearLayout(this);
        sliderLine.setOrientation(LinearLayout.HORIZONTAL);
        sliderLine.setGravity(Gravity.CENTER_VERTICAL);
        sliderLine.setPadding(VivoTheme.dpi(this, 16), 0, VivoTheme.dpi(this, 16), 0);

        ImageView smallSun = new ImageView(this);
        smallSun.setImageDrawable(VivoIcons.of("sun_small",
                VivoTheme.color(this, R.color.vivo_text_secondary), 22));
        sliderLine.addView(smallSun, new LinearLayout.LayoutParams(
                VivoTheme.dpi(this, 22), VivoTheme.dpi(this, 22)));

        LinearLayout.LayoutParams sliderLp = new LinearLayout.LayoutParams(
                0, VivoTheme.dpi(this, 44), 1f);
        sliderLp.leftMargin = VivoTheme.dpi(this, 14);
        sliderLp.rightMargin = VivoTheme.dpi(this, 14);
        sliderLine.addView(slider, sliderLp);

        ImageView largeSun = new ImageView(this);
        largeSun.setImageDrawable(VivoIcons.of("sun_large",
                VivoTheme.color(this, R.color.vivo_text_secondary), 24));
        sliderLine.addView(largeSun, new LinearLayout.LayoutParams(
                VivoTheme.dpi(this, 24), VivoTheme.dpi(this, 24)));

        brightnessRow.addView(sliderLine, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, VivoTheme.dpi(this, 72)));
        View divider = new View(this);
        divider.setTag("row_divider");
        divider.setBackgroundColor(VivoTheme.color(this, R.color.vivo_divider));
        LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 1);
        dlp.leftMargin = VivoTheme.dpi(this, 16);
        brightnessRow.addView(divider, dlp);
        brightnessGroup.addView(brightnessRow);

        final VivoSwitch auto = new VivoSwitch(this);
        View autoRow = VivoUi.switchRow(this, null, 0,
                getString(R.string.detail_brightness_auto), null, auto);
        brightnessGroup.addView(autoRow);
        VivoUi.addGroup(body, brightnessGroup, 0, 8);

        preview = new PreviewCard(this);
        LinearLayout.LayoutParams previewLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, VivoTheme.dpi(this, 104));
        previewLp.leftMargin = VivoTheme.dpi(this, VivoUi.PAGE_MARGIN);
        previewLp.rightMargin = VivoTheme.dpi(this, VivoUi.PAGE_MARGIN);
        previewLp.topMargin = VivoTheme.dpi(this, 2);
        body.addView(preview, previewLp);
        preview.setLevel(slider.getProgress());

        slider.setOnProgressChangedListener(new VivoSlider.OnProgressChangedListener() {
            @Override
            public void onProgressChanged(VivoSlider s, float progress) {
                preview.setLevel(progress);
            }
        });
        auto.setOnCheckedChangeListener(new VivoSwitch.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(VivoSwitch view, boolean checked) {
                slider.setEnabled(!checked);
                preview.setAuto(checked);
                scaffold.invalidateBars();
            }
        });

        section(body, getString(R.string.detail_display), 18, 8);
        LinearLayout displayGroup = VivoUi.group(this);
        displayGroup.addView(row("sun", orange, getString(R.string.detail_eye_care), null, true, "护眼模式"));
        final boolean night = VivoTheme.night(this);
        View darkRow = row("moon", purple, getString(R.string.detail_dark_mode),
                getString(night ? R.string.detail_opened : R.string.detail_closed), true, "深色模式");
        darkRow.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                VivoTheme.toggleNight(SettingsDetailActivity.this);
            }
        });
        displayGroup.addView(darkRow);
        displayGroup.addView(row("font", blue, getString(R.string.detail_font_style),
                getString(R.string.detail_font_style_value), true, "字体样式"));
        displayGroup.addView(row("font", blue, getString(R.string.detail_font_size),
                null, true, "字体大小与粗细"));
        displayGroup.addView(row("display", blue, getString(R.string.detail_display_size),
                getString(R.string.detail_display_size_value), true, "显示大小"));
        VivoUi.addGroup(body, displayGroup, 0, 8);

        section(body, getString(R.string.detail_screen), 18, 8);
        LinearLayout refreshCard = VivoUi.card(this, 16);
        TextView refreshTitle = VivoUi.headerText(this, getString(R.string.detail_refresh),
                15f, VivoTheme.color(this, R.color.vivo_text_primary), VivoTheme.W_MEDIUM);
        refreshCard.addView(refreshTitle);
        TextView refreshDesc = new TextView(this);
        VivoTheme.text(refreshDesc, 12f, VivoTheme.color(this, R.color.vivo_text_secondary),
                VivoTheme.W_REGULAR);
        refreshDesc.setText(getString(R.string.detail_refresh_desc));
        LinearLayout.LayoutParams descLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        descLp.topMargin = VivoTheme.dpi(this, 3);
        refreshCard.addView(refreshDesc, descLp);
        final VivoSegmented segmented = new VivoSegmented(this);
        segmented.setOptions(new String[]{getString(R.string.detail_refresh_smart),
                getString(R.string.detail_refresh_60), getString(R.string.detail_refresh_120)});
        segmented.setSelectedIndex(0, false);
        LinearLayout.LayoutParams segLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, VivoTheme.dpi(this, 36));
        segLp.topMargin = VivoTheme.dpi(this, 12);
        refreshCard.addView(segmented, segLp);
        segmented.setOnSelectedListener(new VivoSegmented.OnSelectedListener() {
            @Override
            public void onSelected(int index) {
                VivoToast.show(SettingsDetailActivity.this,
                        "屏幕刷新率：" + new String[]{"智能切换", "60 Hz", "120 Hz"}[index]);
            }
        });
        VivoUi.addToBody(body, refreshCard, 0, 0);

        VivoButton done = new VivoButton(this);
        done.setStyle(VivoButton.PRIMARY);
        done.setSize(VivoButton.SIZE_XL);
        done.setText(getString(R.string.detail_done));
        done.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });
        VivoUi.addToBody(body, done, 24, 32);
    }

    private View row(String icon, int color, String title, String value, boolean chevron,
                     final String label) {
        View v = VivoUi.row(this, icon, color, title, null, value, chevron, null);
        v.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (!"深色模式".equals(label)) {
                    VivoToast.show(SettingsDetailActivity.this,
                            getString(R.string.demo_row_toast, label));
                }
            }
        });
        return v;
    }

    private void section(LinearLayout body, String text, float topDp, float bottomDp) {
        TextView label = VivoUi.sectionLabel(this, text);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.leftMargin = VivoTheme.dpi(this, 24);
        lp.rightMargin = VivoTheme.dpi(this, 24);
        lp.topMargin = VivoTheme.dpi(this, topDp);
        lp.bottomMargin = VivoTheme.dpi(this, bottomDp);
        body.addView(label, lp);
    }

    private static class PreviewCard extends FrameLayout {
        private final View overlay;
        private final TextView valueView;
        private final TextView stateView;
        private float level = 64f;
        private boolean auto;

        PreviewCard(Activity activity) {
            super(activity);
            GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                    new int[]{0xFF0F77FF, 0xFF6D5BFF, 0xFFFF9A62});
            bg.setCornerRadius(VivoTheme.dp(activity, 12));
            setBackground(bg);
            setClipToOutline(true);

            overlay = new View(activity);
            overlay.setBackgroundColor(Color.BLACK);
            addView(overlay, new LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

            LinearLayout textBlock = new LinearLayout(activity);
            textBlock.setOrientation(LinearLayout.VERTICAL);
            TextView title = new TextView(activity);
            VivoTheme.text(title, 12f, 0xCCFFFFFF, VivoTheme.W_MEDIUM);
            title.setText(activity.getString(R.string.detail_preview));
            textBlock.addView(title);
            valueView = new TextView(activity);
            VivoTheme.text(valueView, 24f, Color.WHITE, VivoTheme.W_SEMI);
            valueView.setFontFeatureSettings("tnum");
            LinearLayout.LayoutParams valueLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            valueLp.topMargin = VivoTheme.dpi(activity, 2);
            textBlock.addView(valueView, valueLp);
            FrameLayout.LayoutParams textLp = new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            textLp.gravity = Gravity.START | Gravity.BOTTOM;
            textLp.leftMargin = VivoTheme.dpi(activity, 16);
            textLp.bottomMargin = VivoTheme.dpi(activity, 12);
            addView(textBlock, textLp);

            stateView = new TextView(activity);
            VivoTheme.text(stateView, 11f, 0xCCFFFFFF, VivoTheme.W_REGULAR);
            stateView.setPadding(VivoTheme.dpi(activity, 9), VivoTheme.dpi(activity, 5),
                    VivoTheme.dpi(activity, 9), VivoTheme.dpi(activity, 5));
            GradientDrawable chip = new GradientDrawable();
            chip.setColor(0x33FFFFFF);
            chip.setCornerRadius(VivoTheme.dp(activity, 10));
            stateView.setBackground(chip);
            FrameLayout.LayoutParams stateLp = new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            stateLp.gravity = Gravity.END | Gravity.TOP;
            stateLp.rightMargin = VivoTheme.dpi(activity, 14);
            stateLp.topMargin = VivoTheme.dpi(activity, 12);
            addView(stateView, stateLp);
            setLevel(level);
        }

        void setLevel(float level) {
            this.level = level;
            overlay.setAlpha((1f - level / 100f) * 0.5f);
            valueView.setText(((int) level) + "%");
            stateView.setText(auto ? "自动" : "手动");
        }

        void setAuto(boolean auto) {
            this.auto = auto;
            stateView.setText(auto ? "自动" : "手动");
        }
    }
}
