package com.betteroriginos.feature.media;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Shader;

/**
 * A11 的封面"浮雕+渐变"后处理，逐指令照搬
 * {@code com.android.systemui.statusbar.notification.ImageGradientColorizer.colorize}
 * （字节码：{@code work/tmp-re-a11/a11-code-classes2.txt} 里偏移 3280d0-328374；
 * 说明见 out/LOG-1103-A11-MEDIACARD-REVERSE.md §4a）。
 *
 * A11 每有一张 largeIcon 就会用它替换掉原封面（**着色与否都会**，LOG §3-4 最后一条），
 * 观感是"左边是取色浮雕，向右逐渐露出真封面"。数值全部来自 {@link A11NotifSpec}，
 * 这样离线自检能在桌面 JVM 上比常量表，不需要 Android。
 */
final class A11ArtRelief {

    private A11ArtRelief() {
    }

    /**
     * @param src   封面位图（原图，任意长宽）
     * @param color 卡片底色（A11 传进来的就是它）
     * @param rtl   布局方向是否为 RTL
     * @return 边长 = min(w,h) 的正方形位图；入参非法时返回 null（调用方保留原图）
     */
    static Bitmap colorize(Bitmap src, int color, boolean rtl) {
        if (src == null || src.getWidth() <= 0 || src.getHeight() <= 0) {
            return null;
        }
        int[] crop = A11NotifSpec.squareCrop(src.getWidth(), src.getHeight());
        int side = crop[2];
        if (side <= 0) {
            return null;
        }
        Bitmap target = Bitmap.createBitmap(side, side, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(target);
        Bitmap layer = Bitmap.createBitmap(side, side, Bitmap.Config.ARGB_8888);
        Canvas layerCanvas = new Canvas(layer);
        try {
            // 1) 原图居中裁方后画进 layer（A11 直接把 drawable 画到偏移后的 bounds）
            layerCanvas.save();
            layerCanvas.translate(-crop[0], -crop[1]);
            src.prepareToDraw();
            layerCanvas.drawBitmap(src, 0f, 0f, null);
            layerCanvas.restore();

            // 2) 遮罩渐变（第一段 stops）以 DST_IN 削掉左半边透明度。
            //    RTL 时 A11 是**先翻转画布**再画渐变，并且不还原 —— 这里保持同样顺序。
            Paint mask = new Paint(Paint.FILTER_BITMAP_FLAG);
            mask.setShader(new LinearGradient(0f, 0f, side, 0f,
                    new int[]{A11NotifSpec.GRAD_TRANSPARENT, A11NotifSpec.GRAD_HALF_WHITE,
                            A11NotifSpec.GRAD_BLACK},
                    A11NotifSpec.GRAD_STOPS_FIRST, Shader.TileMode.CLAMP));
            mask.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_IN));
            if (rtl) {
                layerCanvas.translate(side, 0f);
                layerCanvas.scale(-1f, 1f);
            }
            layerCanvas.drawPaint(mask);

            // 3) 浮雕层：颜色矩阵 + alpha 127，画进目标位图
            float[] m = A11NotifSpec.reliefMatrix(color);
            Paint relief = new Paint(Paint.FILTER_BITMAP_FLAG);
            relief.setColorFilter(new ColorMatrixColorFilter(new ColorMatrix(m)));
            relief.setAlpha(A11NotifSpec.RELIEF_LAYER_ALPHA);
            canvas.drawBitmap(layer, 0f, 0f, relief);

            // 4) 第二段 stops 再削一次（同一支 mask，xfermode 仍是 DST_IN），
            //    然后把**未染色**的图层原样叠上去 ⇒ 右侧逐渐露出真封面
            mask.setShader(new LinearGradient(0f, 0f, side, 0f,
                    new int[]{A11NotifSpec.GRAD_TRANSPARENT, A11NotifSpec.GRAD_HALF_WHITE,
                            A11NotifSpec.GRAD_BLACK},
                    A11NotifSpec.GRAD_STOPS_SECOND, Shader.TileMode.CLAMP));
            layerCanvas.drawPaint(mask);
            canvas.drawBitmap(layer, 0f, 0f, null);
            return target;
        } catch (Throwable t) {
            return null;
        } finally {
            // layer 只是中间层，两种出口都要释放；target 是返回值，不能碰
            try {
                layer.recycle();
            } catch (Throwable ignored) {
            }
        }
    }
}
