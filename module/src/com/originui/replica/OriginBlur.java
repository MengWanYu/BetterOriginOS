package com.originui.replica;

import android.content.Context;
import android.graphics.RectF;
import android.view.View;

import java.lang.reflect.Method;

/**
 * OriginOS 原生模糊（View.setMaterial）反射封装。
 *
 * 原理（docs/01-blur.md）：vivo framework 在 android.view.View 上加了 setMaterial 系列
 * 公共方法（VivoViewImpl），渲染走 RenderNode.setBackdropBlurEffect 的 SKIA Kawase 原生模糊。
 * 本机已验证普通 App 直接调用即可生效（evidence/gate-material-1.png）。
 *
 * 非vivo设备/失败时 apply() 返回 false，调用方应保留原背景（OriginUI 同款行为）。
 */
public final class OriginBlur {
    /** OriginUI 语义类型（VBlurUtils.TYPE_*），apply/OriginBlurLayout 用。 */
    public static final int IMMERSION_MENU = 0;   // 沉浸菜单
    public static final int FLOAT_MENU = 1;       // 悬浮菜单/悬浮卡
    public static final int TOOLBAR = 2;          // 顶栏
    public static final int POPUP_WINDOW = 3;     // 弹窗
    public static final int SHEET_DIALOG = 4;     // 底部弹层/底栏
    public static final int SHEET_TOOLBAR = 5;    // 弹层顶栏
    public static final int WINDOW = 6;           // 窗口模糊（糊壁纸）
    public static final int TOAST = 7;
    public static final int FLOAT_NOTIFICATION = 9;
    public static final int SNACKBAR = 10;
    public static final int PROMPT = 11;
    public static final int BANNERTIP = 12;
    public static final int SHEET_DIALOG_2 = 13;
    public static final int M1 = 14, M2 = 15, M3 = 16, M4 = 17;
    public static final int BANNERTIP_2 = 18;
    public static final int TOOLBAR_2 = 19;

    /** [type] → {light, dark}（VBlurUtils.initMaterialMap / initDarkMaterialMap 还原）。 */
    static final int[][] MATERIAL = {
            {206, 256}, // IMMERSION_MENU
            {207, 257}, // FLOAT_MENU
            {202, 252}, // TOOLBAR
            {208, 258}, // POPUP_WINDOW
            {204, 254}, // SHEET_DIALOG
            {210, 260}, // SHEET_TOOLBAR
            {201, 251}, // WINDOW
            {209, 259}, // TOAST
            {205, 255}, // FLOAT_NOTIFICATION
            {211, 261}, // SNACKBAR
            {203, 253}, // PROMPT
            {212, 262}, // BANNERTIP
            {213, 263}, // SHEET_DIALOG_2
            {1, 51},    // M1
            {2, 52},    // M2
            {3, 53},    // M3
            {4, 54},    // M4
            {214, 264}, // BANNERTIP_2
            {215, 265}, // TOOLBAR_2
    };

    /** contentType：1=模糊视图下层内容（顶栏/底栏/卡片，最常用），0=模糊窗口外（壁纸）。 */
    public static final int CONTENT_VIEW = 1;
    public static final int CONTENT_WINDOW = 0;

    private static Boolean sMethodOk;
    private static Method mSetMaterial, mSetAlpha, mSetCorner, mSetCornerOne, mClear, mSetFps;

    public static boolean isSupported(Context c) {
        if (sMethodOk == null) {
            try {
                Class<?> ft = Class.forName("android.util.FtFeature");
                Method m = ft.getDeclaredMethod("isFeatureSupport", String.class);
                m.setAccessible(true);
                sMethodOk = (Boolean) m.invoke(null, "vivo.software.spacesystem");
            } catch (Throwable t) {
                sMethodOk = Boolean.FALSE;
            }
        }
        return sMethodOk && hasMethod();
    }

    private static boolean hasMethod() {
        try {
            View.class.getMethod("setMaterial", int.class, int.class);
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    /** 应用材质（OriginUI 语义类型）。失败返回 false，调用方保持原背景。 */
    public static boolean apply(View v, int originType, boolean night) {
        if (v == null || originType < 0 || originType >= MATERIAL.length) return false;
        try {
            if (mSetMaterial == null) {
                mSetMaterial = View.class.getMethod("setMaterial", int.class, int.class);
            }
            Object r = mSetMaterial.invoke(v, MATERIAL[originType][night ? 1 : 0], CONTENT_VIEW);
            boolean ok = !(r instanceof Boolean) || (Boolean) r;
            if (ok) {
                setAlpha(v, 1f);
            }
            return ok;
        } catch (Throwable t) {
            return false;
        }
    }

    public static void setAlpha(View v, float alpha) {
        try {
            if (mSetAlpha == null) mSetAlpha = View.class.getMethod("setMaterialAlpha", float.class);
            mSetAlpha.invoke(v, alpha);
        } catch (Throwable ignored) {
        }
    }

    /** 模糊区域圆角（4 角同值）。 */
    public static void setCornerRadius(View v, float radiusPx) {
        try {
            if (mSetCorner == null) {
                mSetCorner = View.class.getMethod("setMaterialCornerRadius",
                        float.class, float.class, float.class, float.class);
                mSetCornerOne = View.class.getMethod("setMaterialCornerRadius", float.class);
            }
            try {
                mSetCorner.invoke(v, radiusPx, radiusPx, radiusPx, radiusPx);
            } catch (Throwable e) {
                mSetCornerOne.invoke(v, radiusPx);
            }
        } catch (Throwable ignored) {
        }
    }

    public static void setFps(View v, int fps) {
        try {
            if (mSetFps == null) mSetFps = View.class.getMethod("setMaterialFPS", int.class);
            mSetFps.invoke(v, fps);
        } catch (Throwable ignored) {
        }
    }

    public static void clear(View v) {
        try {
            if (mClear == null) mClear = View.class.getMethod("clearMaterial");
            mClear.invoke(v);
        } catch (Throwable ignored) {
        }
    }

    /** 滚动渐显系数（VBlurUtils.getScrollBlurAlpha）：dist < critical 时线性，否则 1。 */
    public static float scrollAlpha(float distPx, float criticalPx) {
        return distPx < criticalPx ? distPx / criticalPx : 1f;
    }

    /** 滚动渐显便捷调用：dist 为列表滚动距离（px）。 */
    public static void applyScrollAlpha(View blurView, float distPx, float criticalDp, Context c) {
        setAlpha(blurView, scrollAlpha(distPx, OriginTheme.dp(c, criticalDp)));
    }

    private OriginBlur() {}
}
