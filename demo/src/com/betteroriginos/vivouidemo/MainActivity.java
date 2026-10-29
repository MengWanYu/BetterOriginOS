package com.betteroriginos.vivouidemo;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Shader;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.betteroriginos.vivouidemo.ui.VivoIcons;
import com.betteroriginos.vivouidemo.ui.VivoScaffold;
import com.betteroriginos.vivouidemo.ui.VivoTheme;
import com.betteroriginos.vivouidemo.ui.VivoTopBar;
import com.betteroriginos.vivouidemo.ui.VivoUi;

public class MainActivity extends Activity {
    private VivoScaffold scaffold;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        VivoTheme.applyWindow(this);
        scaffold = new VivoScaffold(this);
        final VivoTopBar top = new VivoTopBar(this, true);
        top.setTitle(getString(R.string.lab_title));
        top.setSubtitle(getString(R.string.lab_subtitle));
        top.addAction(VivoTheme.night(this) ? "sun" : "moon", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                VivoTheme.toggleNight(MainActivity.this);
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
        body.addView(new HeroView(this), heroParams());

        addSection(body, getString(R.string.hub_apps), 18);
        LinearLayout apps = VivoUi.group(this);
        View settingsRow = VivoUi.sceneRow(this, "gear",
                VivoTheme.color(this, R.color.vivo_accent),
                getString(R.string.hub_settings_title),
                getString(R.string.hub_settings_summary), new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        startActivity(new Intent(MainActivity.this, SettingsActivity.class));
                    }
                });
        View clockRow = VivoUi.sceneRow(this, "clock",
                VivoTheme.color(this, R.color.vivo_orange),
                getString(R.string.hub_clock_title),
                getString(R.string.hub_clock_summary), new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        startActivity(new Intent(MainActivity.this, ClockActivity.class));
                    }
                });
        apps.addView(settingsRow);
        apps.addView(clockRow);
        VivoUi.addGroup(body, apps, 0, 8);

        addSection(body, getString(R.string.hub_components), 16);
        LinearLayout tools = VivoUi.group(this);
        tools.addView(VivoUi.sceneRow(this, "palette",
                VivoTheme.color(this, R.color.vivo_purple),
                getString(R.string.hub_controls_title),
                getString(R.string.hub_controls_summary), new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        startActivity(new Intent(MainActivity.this, ComponentsActivity.class));
                    }
                }));
        tools.addView(VivoUi.sceneRow(this, "layers",
                VivoTheme.color(this, R.color.vivo_accent),
                getString(R.string.hub_overlays_title),
                getString(R.string.hub_overlays_summary), new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        Intent intent = new Intent(MainActivity.this, ComponentsActivity.class);
                        intent.putExtra("section", "overlays");
                        startActivity(intent);
                    }
                }));
        tools.addView(VivoUi.sceneRow(this, "blur",
                VivoTheme.color(this, R.color.vivo_orange),
                getString(R.string.hub_blur_title),
                getString(R.string.hub_blur_summary), new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        startActivity(new Intent(MainActivity.this, BlurLabActivity.class));
                    }
                }));
        tools.addView(VivoUi.sceneRow(this, "layers",
                VivoTheme.color(this, R.color.vivo_purple),
                "OriginUI 验收页", "复刻库控件 + 原生模糊（真机 gate ✓）", new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        startActivity(new Intent(MainActivity.this, ReplicaActivity.class));
                    }
                }));
        VivoUi.addGroup(body, tools, 0, 16);

        TextView footer = VivoUi.headerText(this, getString(R.string.about_source),
                11f, VivoTheme.color(this, R.color.vivo_text_tertiary), VivoTheme.W_REGULAR);
        footer.setGravity(Gravity.CENTER);
        footer.setPadding(0, VivoTheme.dpi(this, 12), 0, VivoTheme.dpi(this, 20));
        body.addView(footer, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    private LinearLayout.LayoutParams heroParams() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, VivoTheme.dpi(this, 172));
        lp.leftMargin = VivoTheme.dpi(this, VivoUi.PAGE_MARGIN);
        lp.rightMargin = VivoTheme.dpi(this, VivoUi.PAGE_MARGIN);
        lp.topMargin = VivoTheme.dpi(this, 4);
        return lp;
    }

    private void addSection(LinearLayout body, String text, float topDp) {
        TextView label = VivoUi.sectionLabel(this, text);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.leftMargin = VivoTheme.dpi(this, 24);
        lp.rightMargin = VivoTheme.dpi(this, 24);
        lp.topMargin = VivoTheme.dpi(this, topDp);
        lp.bottomMargin = VivoTheme.dpi(this, 6);
        body.addView(label, lp);
    }

    private static class HeroView extends FrameLayout {
        private final Paint blobPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint ringPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

        HeroView(Activity activity) {
            super(activity);
            GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                    new int[]{0xFF0F77FF, 0xFF5B6CFF, 0xFFB45CFF});
            bg.setCornerRadius(VivoTheme.dp(activity, 18));
            setBackground(bg);
            setClipToOutline(true);

            TextView chip = new TextView(activity);
            VivoTheme.text(chip, 10f, 0xE6FFFFFF, VivoTheme.W_SEMI);
            chip.setText(activity.getString(R.string.hero_label));
            chip.setLetterSpacing(0.14f);
            chip.setPadding(VivoTheme.dpi(activity, 10), VivoTheme.dpi(activity, 6),
                    VivoTheme.dpi(activity, 10), VivoTheme.dpi(activity, 6));
            GradientDrawable chipBg = new GradientDrawable();
            chipBg.setColor(0x33FFFFFF);
            chipBg.setCornerRadius(VivoTheme.dp(activity, 12));
            chip.setBackground(chipBg);
            FrameLayout.LayoutParams chipLp = new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            chipLp.gravity = Gravity.START | Gravity.TOP;
            chipLp.leftMargin = VivoTheme.dpi(activity, 20);
            chipLp.topMargin = VivoTheme.dpi(activity, 18);
            addView(chip, chipLp);

            LinearLayout textBlock = new LinearLayout(activity);
            textBlock.setOrientation(LinearLayout.VERTICAL);
            TextView title = new TextView(activity);
            VivoTheme.text(title, 32f, VivoTheme.color(activity, R.color.vivo_white), VivoTheme.W_BOLD);
            title.setText("OriginOS 5");
            title.setLetterSpacing(-0.01f);
            textBlock.addView(title);
            TextView sub = new TextView(activity);
            VivoTheme.text(sub, 13f, 0xBFFFFFFF, VivoTheme.W_REGULAR);
            sub.setText(activity.getString(R.string.blur_hero_sub));
            LinearLayout.LayoutParams subLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            subLp.topMargin = VivoTheme.dpi(activity, 6);
            textBlock.addView(sub, subLp);
            FrameLayout.LayoutParams textLp = new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            textLp.gravity = Gravity.START | Gravity.BOTTOM;
            textLp.leftMargin = VivoTheme.dpi(activity, 22);
            textLp.bottomMargin = VivoTheme.dpi(activity, 22);
            addView(textBlock, textLp);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            int w = getWidth();
            int h = getHeight();
            blobPaint.setShader(new LinearGradient(w * 0.62f, 0, w, h,
                    new int[]{0x33FFFFFF, 0x00FFFFFF}, null, Shader.TileMode.CLAMP));
            canvas.drawCircle(w * 0.86f, h * 0.22f, h * 0.52f, blobPaint);
            blobPaint.setShader(null);
            blobPaint.setColor(0x26FFCC88);
            canvas.drawCircle(w * 0.12f, h * 0.92f, h * 0.34f, blobPaint);
            ringPaint.setStyle(Paint.Style.STROKE);
            ringPaint.setStrokeWidth(VivoTheme.dp(getContext(), 1.2f));
            ringPaint.setColor(0x33FFFFFF);
            canvas.drawCircle(w * 0.78f, h * 0.32f, h * 0.36f, ringPaint);
            ringPaint.setColor(0x1FFFFFFF);
            canvas.drawCircle(w * 0.78f, h * 0.32f, h * 0.48f, ringPaint);
        }
    }
}
