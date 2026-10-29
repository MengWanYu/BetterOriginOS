package com.betteroriginos.vivouidemo;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * 真机 gate 测试：验证普通 App 能否通过反射调用 vivo 框架的
 * View.setMaterial(int style, int contentType) 获得原生 OriginOS 模糊。
 *
 * 布局：彩色滚动作背景；顶部一条 toolbar 材质条(210/252)，中间一张悬浮卡(206/255)，
 * 底部一条导航材质条(204/254)。每个条上写明反射调用结果。
 */
public class MaterialGateActivity extends Activity {
    private static final String TAG = "MaterialGate";
    private static final boolean DARK = false;

    private final List<String> report = new ArrayList<String>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // ---- 1. 能力探测 ----
        boolean hasSetMaterial = false, hasAlpha = false, hasCorner = false;
        try {
            View.class.getMethod("setMaterial", int.class, int.class);
            hasSetMaterial = true;
        } catch (Throwable t) {
            logLine("setMaterial not found: " + t);
        }
        try {
            View.class.getMethod("setMaterialAlpha", float.class);
            hasAlpha = true;
        } catch (Throwable ignored) {
        }
        try {
            View.class.getMethod("setMaterialCornerRadius", float.class, float.class, float.class, float.class);
            hasCorner = true;
        } catch (Throwable ignored) {
        }
        int spaceMode = -1;
        try {
            Method m = android.content.res.Configuration.class.getDeclaredMethod("getSpaceSystemMode");
            m.setAccessible(true);
            spaceMode = (Integer) m.invoke(getResources().getConfiguration());
        } catch (Throwable t) {
            logLine("getSpaceSystemMode fail: " + t);
        }
        boolean ftBlur = false;
        try {
            Class<?> ft = Class.forName("android.util.FtFeature");
            Method isSup = ft.getDeclaredMethod("isFeatureSupport", String.class);
            isSup.setAccessible(true);
            ftBlur = (Boolean) isSup.invoke(null, "vivo.software.spacesystem");
        } catch (Throwable t) {
            logLine("FtFeature fail: " + t);
        }
        logLine("View.setMaterial=" + hasSetMaterial + " setMaterialAlpha=" + hasAlpha
                + " setMaterialCornerRadius=" + hasCorner);
        logLine("FtFeature(spacesystem)=" + ftBlur + " spaceMode=" + spaceMode);

        // ---- 2. 布局 ----
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(DARK ? Color.BLACK : 0xFFF2F2F5);

        ScrollView scroll = new ScrollView(this);
        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(body, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // 彩色内容：让模糊有东西可糊
        for (int i = 0; i < 14; i++) {
            GradientDrawable gd = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                    new int[]{0xFF0F77FF, 0xFF7A6FF0, 0xFFFF8A3D, 0xFF16B364});
            gd.setCornerRadius(dp(16));
            TextView tv = new TextView(this);
            tv.setText("内容块 " + (i + 1));
            tv.setTextColor(Color.WHITE);
            tv.setTextSize(20);
            tv.setGravity(Gravity.CENTER);
            tv.setBackground(gd);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(120));
            lp.leftMargin = lp.rightMargin = dp(20);
            lp.topMargin = i == 0 ? dp(140) : dp(16);
            lp.bottomMargin = i == 13 ? dp(160) : 0;
            body.addView(tv, lp);
        }

        root.addView(scroll, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        // ---- 3. 顶栏材质条 (TYPE_TOOLBAR=2 → light 210 / dark 252) ----
        LinearLayout topBar = new LinearLayout(this);
        topBar.setOrientation(LinearLayout.VERTICAL);
        topBar.setGravity(Gravity.CENTER_VERTICAL);
        TextView topTitle = new TextView(this);
        topTitle.setText("顶部 Toolbar 材质 (style 210, contentType 1)");
        topTitle.setTextSize(13);
        topTitle.setTextColor(DARK ? 0xFFF2F2F2 : 0xDE000000);
        topBar.addView(topTitle, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        TextView topStatus = new TextView(this);
        topStatus.setTextSize(11);
        topStatus.setTextColor(0x8A000000);
        topBar.addView(topStatus, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        topBar.setPadding(dp(20), dp(56), dp(20), dp(14));
        root.addView(topBar, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.TOP));

        // ---- 4. 底栏材质条 (TYPE_SHEET_DIALOG=4 → 204/254) ----
        LinearLayout bottomBar = new LinearLayout(this);
        bottomBar.setOrientation(LinearLayout.VERTICAL);
        bottomBar.setGravity(Gravity.CENTER);
        TextView botStatus = new TextView(this);
        botStatus.setText("底部导航材质 (style 204) 初始化…");
        botStatus.setTextSize(12);
        botStatus.setTextColor(DARK ? 0xFFF2F2F2 : 0xDE000000);
        botStatus.setGravity(Gravity.CENTER);
        bottomBar.addView(botStatus, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        bottomBar.setPadding(dp(20), dp(16), dp(20), dp(48));
        root.addView(bottomBar, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM));

        // ---- 5. 圆角悬浮卡 (TYPE_FLOAT_MENU=1 → 207/257) ----
        TextView card = new TextView(this);
        card.setText("圆角悬浮卡\nstyle 207 + corner 24dp");
        card.setTextSize(13);
        card.setTextColor(DARK ? 0xFFF2F2F2 : 0xDE000000);
        card.setGravity(Gravity.CENTER);
        card.setPadding(dp(16), dp(16), dp(16), dp(16));
        FrameLayout.LayoutParams cardLp = new FrameLayout.LayoutParams(dp(220), dp(220),
                Gravity.CENTER);
        root.addView(card, cardLp);

        setContentView(root);

        // ---- 6. 应用材质 ----
        boolean topOk = applyMaterial(topBar, DARK ? 252 : 210, 1);
        boolean botOk = applyMaterial(bottomBar, DARK ? 254 : 204, 1);
        boolean cardOk = applyMaterial(card, DARK ? 257 : 207, 1);
        if (cardOk) {
            try {
                Method cr = View.class.getMethod("setMaterialCornerRadius",
                        float.class, float.class, float.class, float.class);
                cr.invoke(card, dp(24), dp(24), dp(24), dp(24));
            } catch (Throwable ignored) {
            }
        }
        topStatus.setText("反射结果: " + (topOk ? "成功 ✓ (原生模糊)" : "失败 ✗"));
        botStatus.setText("底部导航材质 (style 204): " + (botOk ? "成功 ✓" : "失败 ✗"));
        card.setText("圆角悬浮卡 style207\n" + (cardOk ? "成功 ✓" : "失败 ✗"));

        logLine("apply top=" + topOk + " bottom=" + botOk + " card=" + cardOk);
        for (String line : report) {
            Log.i(TAG, line);
        }
    }

    private boolean applyMaterial(View view, int style, int contentType) {
        try {
            Method m = View.class.getMethod("setMaterial", int.class, int.class);
            Object r = m.invoke(view, style, contentType);
            return !(r instanceof Boolean) || (Boolean) r;
        } catch (Throwable t) {
            logLine("applyMaterial(" + style + ") fail: " + t);
            return false;
        }
    }

    private void logLine(String s) {
        report.add(s);
    }

    private int dp(float v) {
        return Math.round(getResources().getDisplayMetrics().density * v);
    }
}
