package com.betteroriginos.feature.notify;

import android.content.Context;
import android.content.res.Configuration;
import android.util.TypedValue;

/** 通知条 iOS 化规格：Hook 侧与应用内预览共用同一组数值，保证所见即所得 */
public final class NotifyRowSpec {
    public static final int ICON_DP = 38;
    public static final int ICON_CORNER_DP = 10;
    public static final int GUTTER_DP = 16;
    public static final int ICON_GAP_DP = 12;
    public static final int COLUMN_DP = GUTTER_DP + ICON_DP + ICON_GAP_DP;
    public static final int HEADER_HEIGHT_DP = 34;
    public static final int APP_LINE_TOP_DP = 8;
    public static final int TITLE_TOP_DP = HEADER_HEIGHT_DP;

    public static final float APP_TEXT_SP = 12f;
    public static final float TITLE_TEXT_SP = 15f;
    public static final float BODY_TEXT_SP = 14f;
    public static final int TITLE_MAX_LINES = 2;
    public static final int BODY_MAX_LINES = 4;
    public static final int TITLE_BODY_GAP_DP = 2;
    public static final int CARD_PAD_BOTTOM_DP = 14;

    public static final int CARD_RADIUS_DP = 20;
    public static final int CARD_GAP_DP = 8;
    public static final int PREVIEW_PAGE_MARGIN_DP = 22;

    /** OS6 折叠堆叠实测：露边在卡片下方，每层下移 14dp(42px)、左右各内缩 13dp(39px)，最多 2 层 */
    public static final int STACK_BARS = 2;
    public static final int STACK_PEEK_LAYER_DP = 14;
    public static final int STACK_BAR_INSET_DP = 13;
    /** 堆叠卡右上角箭头给时间让出的宽度 */
    public static final int CHEVRON_RESERVE_DP = 30;
    /** 展开/收起过渡时长（iOS 的分组展开约 0.28s，太短会显得生硬） */
    public static final long STACK_ANIM_MS = 280;

    /**
     * 组展开/收起时 42dp 大图标的淡入淡出时长。
     * vivo 自己那段展开动画约 0.25s，图标比它略短才不会尾端还在淡、行已经停住。
     */
    public static final long ICON_FADE_MS = 170;

    /**
     * iOS 式轻微回弹曲线：贝塞尔控制点让进度先过冲 ~8% 再回落到 1，
     * 视觉上是"弹一下再停住"，而不是线性/纯减速那种机械感。
     */
    public static android.view.animation.Interpolator spring() {
        return new android.view.animation.PathInterpolator(0.18f, 1.30f, 0.30f, 1f);
    }

    /** 自适应图标裁边放大倍率（iOS 应用图标无明显安全区留白） */
    public static final float ICON_ZOOM = 1.28f;

    public static final int LIGHT_PRIMARY = 0xDE000000;
    public static final int LIGHT_SECONDARY = 0x61000000;
    public static final int DARK_PRIMARY = 0xFFFFFFFF;
    public static final int DARK_SECONDARY = 0xB3FFFFFF;
    /** 取不到 OS5 卡片材质时的堆叠卡兜底色 */
    public static final int LIGHT_CARD_FALLBACK = 0xF2F6F6F6;
    public static final int DARK_CARD_FALLBACK = 0xF21C1C1E;

    private NotifyRowSpec() {
    }

    public static int dp(Context ctx, float v) {
        return Math.round(v * ctx.getResources().getDisplayMetrics().density);
    }

    public static int sp(Context ctx, float v) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, v,
                ctx.getResources().getDisplayMetrics()));
    }

    public static boolean night(Context ctx) {
        return (ctx.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
                == Configuration.UI_MODE_NIGHT_YES;
    }

    public static int primary(Context ctx) {
        return night(ctx) ? DARK_PRIMARY : LIGHT_PRIMARY;
    }

    public static int secondary(Context ctx) {
        return night(ctx) ? DARK_SECONDARY : LIGHT_SECONDARY;
    }
}
