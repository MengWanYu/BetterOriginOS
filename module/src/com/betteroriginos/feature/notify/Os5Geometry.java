package com.betteroriginos.feature.notify;

import android.graphics.Path;
import android.graphics.RectF;

/**
 * OS5 通知几何里的纯计算部分。单独成类的理由：
 * 这些函数不碰 Android 运行时状态，因此可以在桌面 JVM 上直接执行来验收
 * （`work/g2_path_selftest.py` 跑的就是这里的字节码），而 Os5Mirror 其余部分
 * 需要 Handler/Xposed，类初始化在离线环境下会失败。
 */
public final class Os5Geometry {

    private Os5Geometry() {
    }

    /** vivo 公式里的 25 度常量 */
    private static final double COS25 = Math.cos(0.4363323129985824d);
    private static final double SIN25 = Math.sin(0.4363323129985824d);

    /**
     * 用"目标基线"换算原生已算好的上下半径：半径 = 系数 * 基线，所以按比例缩放，系数保持不变。
     * 返回 {top, bottom, 需要写回的新基线（0=不用写）}。current<=0.5（没读到基线）或差异在半个像素内都不动。
     */
    public static float[] rescaleRadii(float current, float target, float top, float bottom) {
        if (current <= 0.5f || target <= 0.5f || Math.abs(current - target) <= 0.5f) {
            return new float[]{top, bottom, 0f};
        }
        float k = target / current;
        return new float[]{top * k, bottom * k, target};
    }

    /**
     * vivo G2CornerUtil.getNormalCornerPath 的等价实现（G2 画不出来时的回落分支）。
     * 照抄反编译：先把半径夹到 min(w,h)/2，再逐角 arcTo(90°)。
     */
    public static void normalCornerPath(Path p, float left, float top, float right, float bottom,
                                        float radius, boolean leftTop, boolean rightTop,
                                        boolean rightBottom, boolean leftBottom) {
        // 参考先把宽高截断成 int 再判夹取（G2CornerUtil.java:87-102）：
        // 尺寸带小数时（59.9px 这类）不截断会让钳出来的半径差最多 0.5px
        int w = (int) (right - left);
        int h = (int) (bottom - top);
        if (w < 0 || h < 0) {
            return;
        }
        if (radius * 2f > w || radius * 2f > h) {
            radius = Math.min(w, h) / 2f;
        }
        float topStart = top + radius;
        p.moveTo(left, topStart);
        if (leftTop) {
            float d = radius * 2f;
            p.arcTo(new RectF(left, top, left + d, d + top), 180f, 90f);
        } else {
            p.lineTo(left, top);
            p.lineTo(left + radius, top);
        }
        float rightStop = right - radius;
        p.lineTo(rightStop, top);
        if (rightTop) {
            float d = radius * 2f;
            p.arcTo(new RectF(right - d, top, right, d + top), 270f, 90f);
        } else {
            p.lineTo(right, top);
            p.lineTo(right, topStart);
        }
        float bottomStop = bottom - radius;
        p.lineTo(right, bottomStop);
        if (rightBottom) {
            float d = radius * 2f;
            p.arcTo(new RectF(right - d, bottom - d, right, bottom), 0f, 90f);
        } else {
            p.lineTo(right, bottom);
            p.lineTo(rightStop, bottom);
        }
        p.lineTo(left + radius, bottom);
        if (leftBottom) {
            float d = radius * 2f;
            p.arcTo(new RectF(left, bottom - d, d + left, bottom), 90f, 90f);
        } else {
            p.lineTo(left, bottom);
            p.lineTo(left, bottomStop);
        }
        p.lineTo(left, topStart);
        p.close();
    }

    /**
     * vivo G2CornerUtil.getG2RoundConerPath 的等价实现（逐条对照反编译结果）。
     *
     * 三处容易抄错、且错了只会表现为"圆角看着不太对"的地方：
     * 1) 退普通圆角的门限是 **2.2×半径**（不是 2.6），vivo 在 2.2~2.6 之间仍然画 G2；
     * 2) 常数是**两套**：min(w,h) > 2.6r 用 (-0.013r, 0.61r, 1.3r)，
     *    否则用 (-0.008r, 0.4r, 1.1r)；
     * 3) 第一个控制点偏移是**负值**（代码里以 "点 - cNeg" 出现，等价于往里侧让）。
     * 这三条由 work/g2_path_selftest.py 在桌面 JVM 上逐指令比对锁住。
     */
    public static void g2Path(Path p, float left, float top, float right, float bottom, float radius,
                       boolean leftTop, boolean rightTop, boolean rightBottom, boolean leftBottom) {
        p.reset();
        float w = right - left;
        float h = bottom - top;
        if (w < 0f || h < 0f) {
            return;
        }
        if (radius <= 0f) {
            p.addRect(left, top, right, bottom, Path.Direction.CW);
            return;
        }
        if (radius * 2.2f > w || radius * 2.2f > h) {
            normalCornerPath(p, left, top, right, bottom, radius, leftTop, rightTop, rightBottom,
                    leftBottom);
            return;
        }
        double d = radius;
        float a = (float) (d - (COS25 * d));        // f15
        float b25 = (float) (d - (SIN25 * d));      // f16
        float cNeg;                                 // f17 / f20
        float cMid;                                 // f18 / f21
        float nOff;                                 // f19 / f22
        if (Math.min(w, h) > 2.6f * radius) {
            cNeg = -0.013f * radius;
            cMid = 0.61f * radius;
            nOff = 1.3f * radius;
        } else {
            cNeg = -0.008f * radius;
            cMid = 0.4f * radius;
            nOff = 1.1f * radius;
        }
        float arc = radius * 2f;
        float topStart = top + nOff;                 // f23 / f7 / f9 / f11
        p.moveTo(left, topStart);
        if (leftTop) {
            float x1 = left + a;
            float y1 = top + b25;
            p.cubicTo(left, topStart - cMid, x1 - cNeg, y1, x1, y1);
            p.arcTo(new RectF(left, top, left + arc, arc + top), 205f, 40f);
            float x2 = left + nOff;
            p.cubicTo(left + b25, (top + a) - cNeg, x2 - cMid, top, x2, top);
        } else {
            p.lineTo(left, top);
            p.lineTo(left + nOff, top);
        }
        float topStop = right - nOff;                // f28
        p.lineTo(topStop, top);
        if (rightTop) {
            float x3 = right - b25;
            float y2 = top + a;
            p.cubicTo(topStop + cMid, top, x3, y2 - cNeg, x3, y2);
            p.arcTo(new RectF(right - arc, top, right, arc + top), 295f, 40f);
            p.cubicTo((right - a) + cNeg, top + b25, right, topStart - cMid, right, topStart);
        } else {
            p.lineTo(right, top);
            p.lineTo(right, topStart);
        }
        float rightStop = bottom - nOff;             // f34 / f10
        p.lineTo(right, rightStop);
        if (rightBottom) {
            float x4 = right - a;
            float y3 = bottom - b25;
            p.cubicTo(right, rightStop + cMid, x4 + cNeg, y3, x4, y3);
            p.arcTo(new RectF(right - arc, bottom - arc, right, bottom), 25f, 40f);
            p.cubicTo(right - b25, (bottom - a) + cNeg, topStop + cMid, bottom, topStop, bottom);
        } else {
            p.lineTo(right, bottom);
            p.lineTo(right - nOff, bottom);
        }
        float bottomStart = left + nOff;             // f39
        p.lineTo(bottomStart, bottom);
        if (leftBottom) {
            float x5 = left + b25;
            float y4 = bottom - a;
            p.cubicTo(bottomStart - cMid, bottom, x5, y4 + cNeg, x5, y4);
            p.arcTo(new RectF(left, bottom - arc, arc + left, bottom), 115f, 40f);
            p.cubicTo((left + a) - cNeg, bottom - b25, left, rightStop + cMid, left, rightStop);
        } else {
            p.lineTo(left, bottom);
            p.lineTo(left, rightStop);
        }
        p.lineTo(left, topStart);
        p.close();
    }
}
