package com.betteroriginos.feature.camera;

/**
 * 相机快门按钮的"OS5 原子设计环"规格（用户 11-05 选定 B 款，只改静态外观）。
 *
 * 一条硬约束：**不改按钮的物理尺寸**。相机 UI 里快门键的位置/大小参与布局与点击热区，
 * 换样式只许在原生那颗的既有外接矩形里画，所以这里给的是**比例**而不是绝对 dp：
 * 原生外径 {@code nativeOuterPx} 由现场读回，环与内圆按下面的比例分配。
 * 若原生外径明显小于设计基准（{@link #BASE_OUTER_DP}），就按基准上限画，避免在小屏/
 * 小按钮上把环挤成一条线。
 *
 * 本类不做任何 Android 静态初始化，纯算术，桌面 JVM 可直接跑离线自检
 * （约定同 {@code Os5Geometry}：数值与绘制分离，数值能被独立验证）。
 */
public final class CameraShutterSpec {

    private CameraShutterSpec() {
    }

    /** 设计基准：OS5 那颗快门的视觉外径（dp）。现场值小于它时按现场值，大于它时按它封顶。 */
    public static final float BASE_OUTER_DP = 74f;

    /** 外环厚度占外径的比例（3dp / 74dp）。 */
    public static final float RING_FRACTION = 3f / 74f;

    /** 环与内圆之间的空隙占外径的比例（6dp / 74dp）。 */
    public static final float GAP_FRACTION = 6f / 74f;

    /** 内实心圆直径 = 外径 - 2×环厚 - 2×空隙（74-6-12 = 56dp 那一档）。 */
    public static final float INNER_FRACTION = 1f - 2f * RING_FRACTION - 2f * GAP_FRACTION;

    /** 常态颜色：不透明白（OS5 环在深色预览底上就是纯白，不做半透明）。 */
    public static final int COLOR_RING = 0xFFFFFFFF;
    public static final int COLOR_FILL = 0xFFFFFFFF;

    /** 按下态颜色：同一支颜色，只降不透明度（用户明确不要"形状/环厚随按压变化"，这里只跟随原生缩放动画）。 */
    public static final int COLOR_PRESSED_ALPHA = 0x8CFFFFFF;

    /**
     * 把原生外接矩形（px）换算成三段直径：{外径, 内圆直径, 环厚}。
     * 外径取 {@code min(nativeOuterPx, BASE_OUTER_DP×density)} —— 不放大按钮，只在其内部重排。
     */
    public static int[] sizesPx(int nativeOuterPx, float density) {
        if (nativeOuterPx <= 0 || density <= 0f) {
            return new int[]{0, 0, 0};
        }
        int base = Math.round(BASE_OUTER_DP * density);
        int outer = Math.min(nativeOuterPx, base);
        int ring = Math.max(1, Math.round(outer * RING_FRACTION));
        int inner = Math.max(1, Math.round(outer * INNER_FRACTION));
        // 内圆不能因为取整而顶到环上：留出的空隙至少 1px
        int gap = (outer - 2 * ring - inner) / 2;
        if (gap < 1) {
            inner = Math.max(1, outer - 2 * ring - 2);
        }
        return new int[]{outer, inner, ring};
    }

    /** 见证行：验收要的是"环厚/内圆/外径"三个真实施加值，不是常量回读。 */
    public static String describe(int outerPx, int innerPx, int ringPx, int nativeOuterPx) {
        return "outer=" + outerPx + " inner=" + innerPx + " ring=" + ringPx
                + " nativeOuter=" + nativeOuterPx;
    }
}
