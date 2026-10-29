package com.betteroriginos.vivouidemo;

import android.app.Activity;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
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

import com.betteroriginos.vivouidemo.ui.VivoBottomBar;
import com.betteroriginos.vivouidemo.ui.VivoIcons;
import com.betteroriginos.vivouidemo.ui.VivoScaffold;
import com.betteroriginos.vivouidemo.ui.VivoSlider;
import com.betteroriginos.vivouidemo.ui.VivoSwitch;
import com.betteroriginos.vivouidemo.ui.VivoTheme;
import com.betteroriginos.vivouidemo.ui.VivoToast;
import com.betteroriginos.vivouidemo.ui.VivoTopBar;
import com.betteroriginos.vivouidemo.ui.VivoUi;

public class BlurLabActivity extends Activity {
    private VivoScaffold scaffold;
    private VivoTopBar topBar;
    private VivoBottomBar bottomBar;
    private VivoSlider radiusSlider;
    private VivoSlider tintSlider;
    private VivoSwitch realSwitch;
    private VivoSwitch bottomSwitch;
    private TextView radiusValue;
    private TextView tintValue;
    private boolean initialScrollDone;

    private float radiusDp = 34f;
    private int tintPercent = 86;
    private boolean realBlur = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        VivoTheme.applyWindow(this);
        scaffold = new VivoScaffold(this);

        topBar = new VivoTopBar(this, true);
        topBar.setTitle(getString(R.string.blur_title));
        topBar.setSubtitle(getString(R.string.blur_subtitle));
        topBar.addAction("refresh", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                resetParams();
            }
        });
        scaffold.setTopBar(topBar);

        bottomBar = new VivoBottomBar(this);
        bottomBar.setItems(new String[]{
                        getString(R.string.blur_preset_light),
                        getString(R.string.blur_preset_standard),
                        getString(R.string.blur_preset_strong),
                        getString(R.string.blur_preset_off)},
                new String[]{"blur", "blur", "blur", "eye_off"});
        bottomBar.setOnItemSelectedListener(new VivoBottomBar.OnItemSelectedListener() {
            @Override
            public void onItemSelected(int index) {
                float[] radii = {16f, 34f, 56f, 0f};
                setBlurRadius(radii[index]);
                setRealBlur(index != 3);
                syncControls();
            }
        });
        bottomBar.select(1, false);
        scaffold.setBottomBar(bottomBar);

        setContentView(scaffold);
        scaffold.requestApplyInsets();
        buildBody();
        applyParams();
        scaffold.invalidateBars();

        scaffold.getScroll().post(new Runnable() {
            @Override
            public void run() {
                if (!initialScrollDone) {
                    initialScrollDone = true;
                    scaffold.getScroll().scrollTo(0, VivoTheme.dpi(BlurLabActivity.this, 84));
                    scaffold.invalidateBars();
                }
            }
        });
    }

    private void buildBody() {
        LinearLayout body = scaffold.getBody();
        int accent = VivoTheme.color(this, R.color.vivo_accent);

        HeroCard hero = new HeroCard(this);
        LinearLayout.LayoutParams heroLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, VivoTheme.dpi(this, 196));
        heroLp.leftMargin = VivoTheme.dpi(this, VivoUi.PAGE_MARGIN);
        heroLp.rightMargin = VivoTheme.dpi(this, VivoUi.PAGE_MARGIN);
        heroLp.topMargin = VivoTheme.dpi(this, 2);
        body.addView(hero, heroLp);

        TextView hint = new TextView(this);
        VivoTheme.text(hint, 13f, VivoTheme.color(this, R.color.vivo_text_secondary),
                VivoTheme.W_REGULAR);
        hint.setText(getString(R.string.blur_hint));
        hint.setLineSpacing(VivoTheme.dp(this, 2), 1f);
        LinearLayout.LayoutParams hintLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        hintLp.leftMargin = VivoTheme.dpi(this, 24);
        hintLp.rightMargin = VivoTheme.dpi(this, 24);
        hintLp.topMargin = VivoTheme.dpi(this, 12);
        body.addView(hint, hintLp);

        LinearLayout controls = VivoUi.card(this, 16);
        TextView title = VivoUi.headerText(this, getString(R.string.blur_control_title),
                15f, VivoTheme.color(this, R.color.vivo_text_primary), VivoTheme.W_MEDIUM);
        controls.addView(title);

        controls.addView(sliderBlock(getString(R.string.blur_radius),
                radiusDp, 0, 60, true));
        controls.addView(sliderBlock(getString(R.string.blur_tint),
                tintPercent, 40, 100, false));

        View realRow = switchBlock("blur", accent, getString(R.string.blur_real), true, true);
        controls.addView(realRow);
        View bottomRow = switchBlock("layers", accent, getString(R.string.blur_bottom_bar), true, false);
        controls.addView(bottomRow);
        VivoUi.addToBody(body, controls, 16, 8);

        section(getString(R.string.blur_content_title));
        LinearLayout grid1 = new LinearLayout(this);
        grid1.setOrientation(LinearLayout.HORIZONTAL);
        ColorCard card1 = new ColorCard(this, getString(R.string.blur_card_1), "notification",
                new int[]{0xFF0F77FF, 0xFF5B6CFF}, "bell");
        ColorCard card2 = new ColorCard(this, getString(R.string.blur_card_2), "wallpaper",
                new int[]{0xFFFF8A3D, 0xFFFF5F6D}, "apps");
        LinearLayout.LayoutParams c1 = new LinearLayout.LayoutParams(
                0, VivoTheme.dpi(this, 116), 1f);
        c1.rightMargin = VivoTheme.dpi(this, 5);
        grid1.addView(card1, c1);
        LinearLayout.LayoutParams c2 = new LinearLayout.LayoutParams(
                0, VivoTheme.dpi(this, 116), 1f);
        c2.leftMargin = VivoTheme.dpi(this, 5);
        grid1.addView(card2, c2);
        VivoUi.addToBody(body, grid1, 0, 0);

        LinearLayout grid2 = new LinearLayout(this);
        grid2.setOrientation(LinearLayout.HORIZONTAL);
        ColorCard card3 = new ColorCard(this, getString(R.string.blur_card_3), "widget",
                new int[]{0xFF16B364, 0xFF0E9F6E}, "layers");
        ColorCard card4 = new ColorCard(this, getString(R.string.blur_card_4), "blur",
                new int[]{0xFF7A6FF0, 0xFFB45CFF}, "blur");
        LinearLayout.LayoutParams c3 = new LinearLayout.LayoutParams(
                0, VivoTheme.dpi(this, 116), 1f);
        c3.rightMargin = VivoTheme.dpi(this, 5);
        c3.topMargin = VivoTheme.dpi(this, 10);
        grid2.addView(card3, c3);
        LinearLayout.LayoutParams c4 = new LinearLayout.LayoutParams(
                0, VivoTheme.dpi(this, 116), 1f);
        c4.leftMargin = VivoTheme.dpi(this, 5);
        c4.topMargin = VivoTheme.dpi(this, 10);
        grid2.addView(card4, c4);
        VivoUi.addToBody(body, grid2, 0, 8);

        LinearLayout list = VivoUi.group(this);
        list.addView(VivoUi.row(this, "notification", accent, "通知材质", "实时模糊示例", null, true, null));
        list.addView(VivoUi.row(this, "layers", VivoTheme.color(this, R.color.vivo_purple),
                "卡片叠层", "白色 88% 材质 + 发丝线", null, true, null));
        list.addView(VivoUi.row(this, "blur", VivoTheme.color(this, R.color.vivo_orange),
                "顶栏渐变", "内容穿过时实时更新", null, true, null));
        VivoUi.addGroup(body, list, 0, 8);

        TextView footer = VivoUi.headerText(this, getString(R.string.about_footer),
                11f, VivoTheme.color(this, R.color.vivo_text_tertiary), VivoTheme.W_REGULAR);
        footer.setGravity(Gravity.CENTER);
        footer.setPadding(0, VivoTheme.dpi(this, 18), 0, VivoTheme.dpi(this, 22));
        body.addView(footer, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    private View sliderBlock(String label, float value, float min, float max, boolean isRadius) {
        LinearLayout block = new LinearLayout(this);
        block.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams blockLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        blockLp.topMargin = VivoTheme.dpi(this, 14);
        block.setLayoutParams(blockLp);

        LinearLayout titleRow = new LinearLayout(this);
        titleRow.setOrientation(LinearLayout.HORIZONTAL);
        titleRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = new TextView(this);
        VivoTheme.text(title, 14f, VivoTheme.color(this, R.color.vivo_text_primary),
                VivoTheme.W_MEDIUM);
        title.setText(label);
        titleRow.addView(title, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView valueView = new TextView(this);
        VivoTheme.text(valueView, 13f, VivoTheme.color(this, R.color.vivo_text_secondary),
                VivoTheme.W_REGULAR);
        valueView.setFontFeatureSettings("tnum");
        titleRow.addView(valueView);
        block.addView(titleRow);

        final VivoSlider slider = new VivoSlider(this);
        slider.setRange(min, max);
        slider.setProgress(value);
        LinearLayout.LayoutParams sliderLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, VivoTheme.dpi(this, 44));
        block.addView(slider, sliderLp);

        if (isRadius) {
            radiusSlider = slider;
            radiusValue = valueView;
            slider.setOnProgressChangedListener(new VivoSlider.OnProgressChangedListener() {
                @Override
                public void onProgressChanged(VivoSlider s, float progress) {
                    setBlurRadius(progress);
                    syncControls();
                }
            });
        } else {
            tintSlider = slider;
            tintValue = valueView;
            slider.setOnProgressChangedListener(new VivoSlider.OnProgressChangedListener() {
                @Override
                public void onProgressChanged(VivoSlider s, float progress) {
                    tintPercent = Math.round(progress);
                    applyParams();
                    syncControls();
                }
            });
        }
        updateSliderLabels();
        return block;
    }

    private View switchBlock(String icon, int color, String title, boolean checked, boolean isReal) {
        LinearLayout block = new LinearLayout(this);
        block.setOrientation(LinearLayout.HORIZONTAL);
        block.setGravity(Gravity.CENTER_VERTICAL);
        block.setPadding(0, VivoTheme.dpi(this, 4), 0, 0);
        ImageView iv = new ImageView(this);
        iv.setImageDrawable(VivoIcons.of(icon, color, 22));
        LinearLayout.LayoutParams iconLp = new LinearLayout.LayoutParams(
                VivoTheme.dpi(this, 22), VivoTheme.dpi(this, 22));
        iconLp.rightMargin = VivoTheme.dpi(this, 10);
        block.addView(iv, iconLp);
        TextView label = new TextView(this);
        VivoTheme.text(label, 14f, VivoTheme.color(this, R.color.vivo_text_primary),
                VivoTheme.W_REGULAR);
        label.setText(title);
        block.addView(label, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        final VivoSwitch toggle = new VivoSwitch(this);
        toggle.setChecked(checked, false);
        block.addView(toggle, new LinearLayout.LayoutParams(
                VivoTheme.dpi(this, 48), VivoTheme.dpi(this, 28)));
        block.setMinimumHeight(VivoTheme.dpi(this, 44));
        if (isReal) {
            realSwitch = toggle;
            toggle.setOnCheckedChangeListener(new VivoSwitch.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(VivoSwitch view, boolean isChecked) {
                    setRealBlur(isChecked);
                    if (isChecked) {
                        if (radiusDp <= 0f) {
                            setBlurRadius(16f);
                        }
                        int index = radiusDp <= 24f ? 0 : radiusDp <= 44f ? 1 : 2;
                        bottomBar.select(index, false);
                    } else {
                        bottomBar.select(3, false);
                    }
                }
            });
        } else {
            bottomSwitch = toggle;
            toggle.setOnCheckedChangeListener(new VivoSwitch.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(VivoSwitch view, boolean isChecked) {
                    bottomBar.setVisibility(isChecked ? View.VISIBLE : View.GONE);
                    scaffold.refreshPadding();
                    scaffold.invalidateBars();
                }
            });
        }
        return block;
    }

    private void section(String text) {
        TextView label = VivoUi.sectionLabel(this, text);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.leftMargin = VivoTheme.dpi(this, 24);
        lp.rightMargin = VivoTheme.dpi(this, 24);
        lp.topMargin = VivoTheme.dpi(this, 18);
        lp.bottomMargin = VivoTheme.dpi(this, 8);
        scaffold.getBody().addView(label, lp);
    }

    private void setBlurRadius(float radius) {
        radiusDp = Math.max(0f, Math.min(60f, radius));
        applyParams();
    }

    private void setRealBlur(boolean real) {
        realBlur = real;
        applyParams();
    }

    private void applyParams() {
        topBar.setBlurRadiusDp(radiusDp);
        topBar.setTintAlpha(tintPercent / 100f);
        topBar.setRealBlur(realBlur);
        bottomBar.setBlurRadiusDp(radiusDp);
        bottomBar.setTintAlpha(tintPercent / 100f);
        bottomBar.setRealBlur(realBlur);
        updateSliderLabels();
    }

    private void syncControls() {
        if (radiusSlider != null) {
            radiusSlider.setProgress(radiusDp);
        }
        if (tintSlider != null) {
            tintSlider.setProgress(tintPercent);
        }
        if (realSwitch != null) {
            realSwitch.setChecked(realBlur, false);
        }
        updateSliderLabels();
    }

    private void updateSliderLabels() {
        if (radiusValue != null) {
            radiusValue.setText(getString(R.string.blur_radius_value, Math.round(radiusDp)));
        }
        if (tintValue != null) {
            tintValue.setText(getString(R.string.blur_tint_value, tintPercent));
        }
    }

    private void resetParams() {
        radiusDp = 34f;
        tintPercent = 86;
        realBlur = true;
        bottomBar.setVisibility(View.VISIBLE);
        if (bottomSwitch != null) {
            bottomSwitch.setChecked(true, false);
        }
        bottomBar.select(1, false);
        applyParams();
        syncControls();
        scaffold.refreshPadding();
        scaffold.invalidateBars();
        VivoToast.show(this, "已恢复默认材质");
    }

    private static class HeroCard extends FrameLayout {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

        HeroCard(Activity activity) {
            super(activity);
            GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                    new int[]{0xFF062B6B, 0xFF0F77FF, 0xFF7A6FF0, 0xFFFF9A62});
            bg.setCornerRadius(VivoTheme.dp(activity, 18));
            setBackground(bg);
            setClipToOutline(true);

            TextView title = new TextView(activity);
            VivoTheme.text(title, 34f, 0xFFFFFFFF, VivoTheme.W_BOLD);
            title.setText(activity.getString(R.string.blur_hero_title));
            title.setLetterSpacing(-0.015f);
            FrameLayout.LayoutParams tLp = new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            tLp.gravity = Gravity.START | Gravity.BOTTOM;
            tLp.leftMargin = VivoTheme.dpi(activity, 22);
            tLp.bottomMargin = VivoTheme.dpi(activity, 38);
            addView(title, tLp);

            TextView sub = new TextView(activity);
            VivoTheme.text(sub, 13f, 0xCCFFFFFF, VivoTheme.W_REGULAR);
            sub.setText(activity.getString(R.string.blur_hero_sub));
            FrameLayout.LayoutParams sLp = new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            sLp.gravity = Gravity.START | Gravity.BOTTOM;
            sLp.leftMargin = VivoTheme.dpi(activity, 23);
            sLp.bottomMargin = VivoTheme.dpi(activity, 18);
            addView(sub, sLp);

            ImageView icon = new ImageView(activity);
            icon.setImageDrawable(VivoIcons.of("blur", 0xE6FFFFFF, 26));
            FrameLayout.LayoutParams iLp = new FrameLayout.LayoutParams(
                    VivoTheme.dpi(activity, 26), VivoTheme.dpi(activity, 26));
            iLp.gravity = Gravity.END | Gravity.TOP;
            iLp.rightMargin = VivoTheme.dpi(activity, 18);
            iLp.topMargin = VivoTheme.dpi(activity, 18);
            addView(icon, iLp);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            int w = getWidth();
            int h = getHeight();
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(VivoTheme.dp(getContext(), 1.2f));
            paint.setColor(0x33FFFFFF);
            canvas.drawCircle(w * 0.82f, h * 0.3f, h * 0.45f, paint);
            paint.setColor(0x22FFFFFF);
            canvas.drawCircle(w * 0.82f, h * 0.3f, h * 0.62f, paint);
            paint.setStyle(Paint.Style.FILL);
            paint.setShader(new LinearGradient(w * 0.45f, h, w, 0,
                    new int[]{0x40FFFFFF, 0x00FFFFFF}, null, Shader.TileMode.CLAMP));
            canvas.drawCircle(w * 0.16f, h * 0.2f, h * 0.32f, paint);
            paint.setShader(null);
        }
    }

    private static class ColorCard extends FrameLayout {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

        ColorCard(Activity activity, String label, String iconName, int[] colors, String icon) {
            super(activity);
            GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.TL_BR, colors);
            bg.setCornerRadius(VivoTheme.dp(activity, 14));
            setBackground(bg);
            setClipToOutline(true);

            TextView title = new TextView(activity);
            VivoTheme.text(title, 15f, 0xFFFFFFFF, VivoTheme.W_SEMI);
            title.setText(label);
            FrameLayout.LayoutParams tLp = new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            tLp.gravity = Gravity.START | Gravity.BOTTOM;
            tLp.leftMargin = VivoTheme.dpi(activity, 14);
            tLp.bottomMargin = VivoTheme.dpi(activity, 12);
            addView(title, tLp);

            ImageView iv = new ImageView(activity);
            iv.setImageDrawable(VivoIcons.of(icon, 0xCCFFFFFF, 20));
            FrameLayout.LayoutParams iLp = new FrameLayout.LayoutParams(
                    VivoTheme.dpi(activity, 20), VivoTheme.dpi(activity, 20));
            iLp.gravity = Gravity.END | Gravity.TOP;
            iLp.rightMargin = VivoTheme.dpi(activity, 12);
            iLp.topMargin = VivoTheme.dpi(activity, 12);
            addView(iv, iLp);

            setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    VivoToast.show(activity, "材质卡片");
                }
            });
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(VivoTheme.dp(getContext(), 1f));
            paint.setColor(0x30FFFFFF);
            canvas.drawCircle(getWidth() * 0.85f, getHeight() * 0.2f, getHeight() * 0.46f, paint);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(0x22FFFFFF);
            canvas.drawCircle(getWidth() * 0.15f, getHeight() * 0.88f, getHeight() * 0.26f, paint);
        }
    }
}
