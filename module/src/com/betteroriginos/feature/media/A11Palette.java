package com.betteroriginos.feature.media;

import android.graphics.Bitmap;
import android.graphics.Color;

import java.util.ArrayList;
import java.util.List;

/**
 * Android 11（OriginOS Ocean）媒体通知取色的等价实现。
 * 阈值与判定顺序逐条对照 out/LOG-1102-A11-MEDIACARD-RE.md §3（证据为
 * `framework/base` 的 MediaNotificationProcessor / MediaDataManager 反编译结果）。
 *
 * 为什么不用 androidx.palette：模块不引 androidx，且需要的是"同算法同阈值"而不是任意调色板库，
 * 这里按 AOSP PNGQ 的思路做 5bit/通道直方图量化 + 每桶均值与计数，
 * 结果与 AOSP 的 dominant/vibrant/muted 选法一致。
 */
public final class A11Palette {

    private A11Palette() {
    }

    // ---- MediaNotificationProcessor 常量（同名字同值） ----
    static final float BLACK_MAX_LIGHTNESS = 0.08f;
    static final float WHITE_MIN_LIGHTNESS = 0.90f;
    /** MediaNotificationProcessor:17 —— 群体占图比例下限，population/22500 > 0.002 才算"有这一块"（≈45 像素） */
    static final double MINIMUM_IMAGE_FRACTION = 0.002d;
    /** Palette.Builder 的默认量化色数（Palette.java:379 mMaxColors = 16），MediaStyle 没改它 */
    static final int MAX_COLORS = 16;
    static final float MIN_SATURATION_WHEN_DECIDING = 0.19f;
    static final float POPULATION_FRACTION_FOR_DOMINANT = 0.01f;
    static final float POPULATION_FRACTION_FOR_WHITE_OR_BLACK = 2.5f;
    static final float POPULATION_FRACTION_FOR_MORE_VIBRANT = 1.0f;
    static final int RESIZE_BITMAP_AREA = 22500; // 150*150

    // ---- MediaDataManager.computeBackgroundColor ----
    static final float CARD_LIGHTNESS = 0.25f;
    static final float CARD_SATURATION_SCALE = 0.8f;
    /** 0xFF444444 只出现在 MediaDataManager 的 resumption 路径（:339）；
     *  展开卡无封面时应把白色送进 HSL 变换得到 0xFF404040，见 computeCardBackground(Swatch)。 */
    static final int RESUMPTION_FALLBACK = 0xFF444444;


    public static final class Swatch {
        public final int rgb;
        public final int population;
        public final float[] hsl = new float[3];

        Swatch(int rgb, int population) {
            this.rgb = rgb;
            this.population = population;
            float[] hslOut = rgbToHsl(rgb);
            this.hsl[0] = hslOut[0];
            this.hsl[1] = hslOut[1];
            this.hsl[2] = hslOut[2];
        }

        public float hue() {
            return hsl[0];
        }

        public float saturation() {
            return hsl[1];
        }

        public float lightness() {
            return hsl[2];
        }
    }

    /**
     * androidx.palette.graphics 的 Target 打分模型（不是 support-v7 的 sat*population 老写法）。
     * 常数逐条抄自 work/y7s-a11/tmp/Target.java：
     * 饱和度默认 [0,0.5,1]、明度默认 [0,0.5,1]；
     * vibrant 族把 sat 下界抬到 0.35、目标设 1.0；muted 族把 sat 目标压到 0.3、上界 0.4；
     * 明度三档 light=[0.55,0.74,1]、normal=[0.3,0.5,0.7]、dark=[0,0.26,0.45]；
     * 权重 sat 0.24 / light 0.52 / population 0.24（相加正好 1，normalizeWeights 后不变）。
     * 打分用的 hsl 是 0..1 标度（ColorUtils.RGBToHSL），本类的 Swatch.lightness() 是 0..100，
     * 所以这里要除以 100 再用 —— 标度混用是这类移植最容易犯也最难发现的错。
     */
    static final class Target {
        final String name;
        final float satMin;
        final float satTarget;
        final float satMax;
        final float lightMin;
        final float lightTarget;
        final float lightMax;

        Target(String name, float sMin, float sT, float sMax, float lMin, float lT, float lMax) {
            this.name = name;
            this.satMin = sMin;
            this.satTarget = sT;
            this.satMax = sMax;
            this.lightMin = lMin;
            this.lightTarget = lT;
            this.lightMax = lMax;
        }
    }

    static final Target LIGHT_VIBRANT = new Target("lightVibrant", 0.35f, 1f, 1f, 0.55f, 0.74f, 1f);
    static final Target VIBRANT = new Target("vibrant", 0.35f, 1f, 1f, 0.30f, 0.50f, 0.70f);
    static final Target DARK_VIBRANT = new Target("darkVibrant", 0.35f, 1f, 1f, 0.00f, 0.26f, 0.45f);
    static final Target LIGHT_MUTED = new Target("lightMuted", 0.00f, 0.3f, 0.4f, 0.55f, 0.74f, 1f);
    static final Target MUTED = new Target("muted", 0.00f, 0.3f, 0.4f, 0.30f, 0.50f, 0.70f);
    static final Target DARK_MUTED = new Target("darkMuted", 0.00f, 0.3f, 0.4f, 0.00f, 0.26f, 0.45f);

    /** Builder 构造时加入 target 的顺序（Palette.java:391-396），互斥选取依赖这个顺序 */
    static final Target[] TARGET_ORDER = {LIGHT_VIBRANT, VIBRANT, DARK_VIBRANT,
            LIGHT_MUTED, MUTED, DARK_MUTED};

    private static final float W_SAT = 0.24f;
    private static final float W_LIGHT = 0.52f;
    private static final float W_POP = 0.24f;

    public static final class Palette {
        final List<Swatch> swatches;
        final int maxPopulation;
        private final Swatch dominantSwatch;
        private final Swatch lightVibrantSwatch;
        private final Swatch vibrantSwatch;
        private final Swatch darkVibrantSwatch;
        private final Swatch lightMutedSwatch;
        private final Swatch mutedSwatch;
        private final Swatch darkMutedSwatch;

        Palette(List<Swatch> swatches, int maxPopulation) {
            this.swatches = swatches;
            this.maxPopulation = maxPopulation;
            this.dominantSwatch = findDominant(swatches);
            java.util.Set<Integer> used = new java.util.HashSet<>();
            Swatch[] picks = new Swatch[TARGET_ORDER.length];
            for (int i = 0; i < TARGET_ORDER.length; i++) {
                Swatch pick = selectForTarget(TARGET_ORDER[i], used);
                picks[i] = pick;
                if (pick != null) {
                    used.add(Integer.valueOf(pick.rgb));
                }
            }
            this.lightVibrantSwatch = picks[0];
            this.vibrantSwatch = picks[1];
            this.darkVibrantSwatch = picks[2];
            this.lightMutedSwatch = picks[3];
            this.mutedSwatch = picks[4];
            this.darkMutedSwatch = picks[5];
        }

        private static Swatch findDominant(List<Swatch> in) {
            Swatch found = null;
            int population = Integer.MIN_VALUE;
            for (Swatch s : in) {
                if (s.population > population) {
                    population = s.population;
                    found = s;
                }
            }
            return found;
        }

        private Swatch selectForTarget(Target t, java.util.Set<Integer> used) {
            Swatch found = null;
            float bestScore = 0f;
            for (Swatch s : swatches) {
                if (used.contains(Integer.valueOf(s.rgb))) {
                    continue;
                }
                float sat = s.saturation();
                float light = s.lightness() / 100f;
                if (sat < t.satMin || sat > t.satMax || light < t.lightMin || light > t.lightMax) {
                    continue;
                }
                float popBase = dominantSwatch != null ? dominantSwatch.population : 1f;
                float score = W_SAT * (1f - Math.abs(sat - t.satTarget))
                        + W_LIGHT * (1f - Math.abs(light - t.lightTarget))
                        + W_POP * (s.population / popBase);
                if (found == null || score > bestScore) {
                    bestScore = score;
                    found = s;
                }
            }
            return found;
        }

        /** AOSP 语义：出现次数最多的那一块 */
        public Swatch dominant() {
            return dominantSwatch;
        }

        public Swatch vibrant() {
            return vibrantSwatch;
        }

        public Swatch lightVibrant() {
            return lightVibrantSwatch;
        }

        public Swatch darkVibrant() {
            return darkVibrantSwatch;
        }

        public Swatch muted() {
            return mutedSwatch;
        }

        public Swatch lightMuted() {
            return lightMutedSwatch;
        }

        public Swatch darkMuted() {
            return darkMutedSwatch;
        }

        /** AOSP Palette.getPopulationForPercent(percent) */
        public int populationForPercent(float percent) {
            return Math.round(percent * maxPopulation);
        }

        public List<Swatch> all() {
            return swatches;
        }
    }

        /**
     * A11 取色的第一步：把整张图按面积 22500 缩放，取整用 (int) 强转（向下）。
     * 证据 MediaNotificationProcessor.java:57-63。之后 androidx Palette 自己的
     * scaleBitmapDown 不会再缩（面积已 <= 22500，判定是严格大于），
     * 所以**后面所有坐标（region、量化输入）都在这个"工作系"里**。
     * 返回 {工作宽, 工作高}。
     */
    public static int[] workingSize(int srcW, int srcH) {
        if (srcW <= 0 || srcH <= 0) {
            return new int[]{Math.max(0, srcW), Math.max(0, srcH)};
        }
        if ((float) srcW * srcH <= RESIZE_BITMAP_AREA) {
            return new int[]{srcW, srcH};
        }
        double ratio = Math.sqrt(RESIZE_BITMAP_AREA / (srcW * (double) srcH));
        int ww = (int) (srcW * ratio);
        int hh = (int) (srcH * ratio);
        if (ww < 1 || hh < 1) {
            // 极端长宽比会把某一边取整成 0；Drawable.draw 不会产出这种位图，退回不缩放
            return new int[]{srcW, srcH};
        }
        return new int[]{ww, hh};
    }

    /**
     * 取色区域：背景用左半幅、前景用右 60%，坐标一律在**工作系**里
     * （MNP:177 `setRegion(0, 0, bitmap.getWidth()/2, bitmap.getHeight())`，
     *  MNP:71 `setRegion((int)(bitmap.width * 0.4f), 0, width, height)`，
     *  两处用的都已经是缩放后的 bitmap）。
     * 返回 {背景区 l,t,r,b, 前景区 l,t,r,b}。
     */
    public static int[] collapsedRegions(int srcW, int srcH) {
        int[] ws = workingSize(srcW, srcH);
        int w = ws[0];
        int h = ws[1];
        return new int[]{0, 0, w / 2, h, (int) (0.4f * w), 0, w, h};
    }

    /** 该取色区在工作系里的像素数（= 喂给量化器的像素个数），装机日志核对用 */
    public static int gridPixels(int srcW, int srcH, int regionIndex) {
        int[] ws = workingSize(srcW, srcH);
        int[] reg = collapsedRegions(srcW, srcH);
        int i = regionIndex * 4;
        int l = clampi(reg[i], 0, Math.max(0, ws[0] - 1));
        int t = clampi(reg[i + 1], 0, Math.max(0, ws[1] - 1));
        int r = clampi(reg[i + 2], l + 1, Math.max(1, ws[0]));
        int b = clampi(reg[i + 3], t + 1, Math.max(1, ws[1]));
        return (r - l) * (b - t);
    }

    private static int clampi(int v, int lo, int hi) {
        return Math.max(lo, Math.min(v, hi));
    }

    /**
     * 在工作系里取 region 做量化：region 必须是 {@link #collapsedRegions} 那套坐标。
     * 像素按最近邻从源图映射回来 —— A11 那一步是 `Canvas.draw(Drawable)` 的平滑缩放，
     * 我们是同网格不同采样（个别像素值可差 1~2 级），已在 out/LOG-1102-A11-MEDIACARD-RE.md §14 记为近似。
     */
    public static Palette generate(Bitmap src, int regionLeft, int regionTop, int regionRight,
                                   int regionBottom, SwatchFilter filter) {
        if (src == null || src.getWidth() <= 0 || src.getHeight() <= 0) {
            return new Palette(new ArrayList<Swatch>(), 0);
        }
        int sw0 = src.getWidth();
        int sh0 = src.getHeight();
        int[] ws = workingSize(sw0, sh0);
        int ww = ws[0];
        int hh = ws[1];
        int l = clampi(regionLeft, 0, Math.max(0, ww - 1));
        int t = clampi(regionTop, 0, Math.max(0, hh - 1));
        int r = clampi(regionRight, l + 1, ww);
        int b = clampi(regionBottom, t + 1, hh);
        double ratioX = ww / (double) sw0;
        double ratioY = hh / (double) sh0;
        int sw = r - l;
        int sh = b - t;
        int[] pixels = new int[sw * sh];
        for (int y = 0; y < sh; y++) {
            int sy = Math.min(sh0 - 1, (int) ((t + y) / ratioY));
            for (int x = 0; x < sw; x++) {
                int sx = Math.min(sw0 - 1, (int) ((l + x) / ratioX));
                pixels[y * sw + x] = src.getPixel(sx, sy);
            }
        }
        List<Swatch> out = quantize(pixels, filter);
        // AOSP 在这一步**没有**"按占比预筛 swatch"：量化结果直接进 Palette，
        // 占比判定只发生在 selectForegroundColorForSwatches 内部的 hasEnoughPopulation。
        return new Palette(out, maxPopulation(out));
    }

    private static int maxPopulation(List<Swatch> in) {
        int max = 0;
        for (Swatch s : in) {
            max = Math.max(max, s.population);
        }
        return max;
    }

    /**
     * androidx.palette.graphics.ColorCutQuantizer 的等价实现（逐条照
     * work/y7s-a11/tmp/ColorCutQuantizer.java:58-318 转写），不是"直方图取前 N"的简化版。
     *
     * 关键语义（每一处都是与旧实现的实质差别）：
     * 1) 先把像素压成 5bit/通道（红占 10..14、绿 5..9、蓝 0..4）的 32768 桶直方图，**不看 alpha**；
     * 2) 过滤作用在**桶的代表色**上（`approximateToRgb888(桶号)` 的 HSL），整桶要么留要么丢 ——
     *    旧实现是按像素过滤，桶内混色时结果完全不同；
     * 3) 非空桶数 <= maxColors（Palette 默认 16）时，一个桶就是一个 Swatch，不做任何合并；
     * 4) 超过才走 median-cut：优先队列按**体积**取最大的盒子，沿最长颜色维切一刀
     *    （切点 = 该维排序后累计群体首次 >= 总群体一半处），切到 16 个盒子；
     * 5) 盒子的平均色是按桶群体数加权后**四舍五入**，再放大回 8bit；生成后**再过一次滤**。
     *
     * splitBoxes 的循环条件是 `size < max && (box = poll()) != null && box.canSplit()`：
     * 切不动的那个盒子**不会被放回队列**（androidx 就是这个行为，会丢整块群体、
     * 结果盒子数可能少于 maxColors）。我们照抄它，不做"更正确"的版本。
     */
    static List<Swatch> quantizeToSwatches(int[] pixels, int maxColors, SwatchFilter filter) {
        int[] histogram = new int[32768];
        for (int px : pixels) {
            histogram[quantizeFromRgb888(px)]++;
        }
        int n = 0;
        for (int i = 0; i < histogram.length; i++) {
            if (histogram[i] > 0 && shouldIgnore(approximateToRgb888(i), filter)) {
                histogram[i] = 0;
            }
            if (histogram[i] > 0) {
                n++;
            }
        }
        int[] colors = new int[n];
        int k = 0;
        for (int i = 0; i < histogram.length; i++) {
            if (histogram[i] > 0) {
                colors[k++] = i;
            }
        }
        List<Swatch> out = new ArrayList<>();
        if (n <= maxColors) {
            for (int color : colors) {
                out.add(new Swatch(approximateToRgb888(color), histogram[color]));
            }
            return out;
        }
        java.util.PriorityQueue<Vbox> queue = new java.util.PriorityQueue<>(
                maxColors, (a, b) -> b.volume() - a.volume());
        queue.offer(new Vbox(0, n - 1, colors, histogram));
        while (queue.size() < maxColors) {
            Vbox best = queue.poll();
            if (best == null) {
                break;
            }
            if (!best.canSplit()) {
                // androidx ColorCutQuantizer.splitBoxes 把 canSplit() 写在 while 的 && 链里：
                // poll 出来的盒子切不动时**不放回队列**，那个盒子的群体就此消失，
                // 最终盒子数可能 < maxColors。这里照抄——目标是一比一还原 A11 取出的那批色，
                // 不是"我们认为应该取出的那批"。（20:30 前这版把它放回队列，属偏离。）
                break;
            }
            Vbox split = best.splitBox(colors, histogram);
            queue.offer(split);
            queue.offer(best);
        }
        for (Vbox box : queue) {
            Swatch avg = box.averageColor(colors, histogram);
            if (!shouldIgnore(avg.rgb, filter)) {
                out.add(avg);
            }
        }
        return out;
    }

    private static boolean shouldIgnore(int rgb, SwatchFilter filter) {
        return filter != null && !filter.accept(rgb);
    }

    /** 5bit/通道的桶号（红在高位），等价 ColorCutQuantizer.quantizeFromRgb888 */
    static int quantizeFromRgb888(int rgb) {
        return (modifyWordWidth((rgb >> 16) & 0xFF, 8, 5) << 10)
                | (modifyWordWidth((rgb >> 8) & 0xFF, 8, 5) << 5)
                | modifyWordWidth(rgb & 0xFF, 8, 5);
    }

    static int approximateToRgb888(int quantized) {
        return 0xFF000000
                | (modifyWordWidth((quantized >> 10) & 0x1F, 5, 8) << 16)
                | (modifyWordWidth((quantized >> 5) & 0x1F, 5, 8) << 8)
                | modifyWordWidth(quantized & 0x1F, 5, 8);
    }

    private static int modifyWordWidth(int value, int currentWidth, int targetWidth) {
        return (targetWidth > currentWidth
                ? value << (targetWidth - currentWidth)
                : value >> (currentWidth - targetWidth)) & ((1 << targetWidth) - 1);
    }

    /** median-cut 的盒子：只管一段已排序的桶下标区间，边界与群体按需重算 */
    private static final class Vbox {
        private static final int RED = -3;
        private static final int GREEN = -2;
        private static final int BLUE = -1;

        private int lower;
        private int upper;
        private int minRed, maxRed, minGreen, maxGreen, minBlue, maxBlue;
        private int population;

        Vbox(int lower, int upper, int[] colors, int[] histogram) {
            this.lower = lower;
            this.upper = upper;
            fitBox(colors, histogram);
        }

        void fitBox(int[] colors, int[] histogram) {
            int minR = Integer.MAX_VALUE, maxR = Integer.MIN_VALUE;
            int minG = Integer.MAX_VALUE, maxG = Integer.MIN_VALUE;
            int minB = Integer.MAX_VALUE, maxB = Integer.MIN_VALUE;
            int pop = 0;
            for (int i = lower; i <= upper; i++) {
                int color = colors[i];
                pop += histogram[color];
                int r = (color >> 10) & 0x1F;
                int g = (color >> 5) & 0x1F;
                int b = color & 0x1F;
                if (r > maxR) {
                    maxR = r;
                }
                if (r < minR) {
                    minR = r;
                }
                if (g > maxG) {
                    maxG = g;
                }
                if (g < minG) {
                    minG = g;
                }
                if (b > maxB) {
                    maxB = b;
                }
                if (b < minB) {
                    minB = b;
                }
            }
            minRed = minR;
            maxRed = maxR;
            minGreen = minG;
            maxGreen = maxG;
            minBlue = minB;
            maxBlue = maxB;
            population = pop;
        }

        int volume() {
            return (maxRed - minRed + 1) * (maxGreen - minGreen + 1) * (maxBlue - minBlue + 1);
        }

        boolean canSplit() {
            return (upper + 1) - lower > 1;
        }

        int longestDimension() {
            int r = maxRed - minRed;
            int g = maxGreen - minGreen;
            int b = maxBlue - minBlue;
            if (r < g || r < b) {
                return (g < r || g < b) ? BLUE : GREEN;
            }
            return RED;
        }

        /** 把待排序的通道挪到高位，排完再挪回来（等价 modifySignificantOctet + Arrays.sort） */
        int findSplitPoint(int[] colors, int[] histogram) {
            int dimension = longestDimension();
            applySignificantOctet(colors, dimension, lower, upper);
            java.util.Arrays.sort(colors, lower, upper + 1);
            applySignificantOctet(colors, dimension, lower, upper);
            int half = population / 2;
            int running = 0;
            for (int i = lower; i <= upper; i++) {
                running += histogram[colors[i]];
                if (running >= half) {
                    return Math.min(upper - 1, i);
                }
            }
            return lower;
        }

        static void applySignificantOctet(int[] colors, int dimension, int from, int to) {
            for (int i = from; i <= to; i++) {
                int c = colors[i];
                int r = (c >> 10) & 0x1F;
                int g = (c >> 5) & 0x1F;
                int b = c & 0x1F;
                if (dimension == GREEN) {
                    colors[i] = b | (g << 10) | (r << 5);
                } else if (dimension == BLUE) {
                    colors[i] = r | (b << 10) | (g << 5);
                }
                // RED：本来就是 r<<10 | g<<5 | b，不用动
            }
        }

        Vbox splitBox(int[] colors, int[] histogram) {
            int split = findSplitPoint(colors, histogram);
            Vbox next = new Vbox(split + 1, upper, colors, histogram);
            upper = split;
            fitBox(colors, histogram);
            return next;
        }

        Swatch averageColor(int[] colors, int[] histogram) {
            long sumR = 0, sumG = 0, sumB = 0;
            int count = 0;
            for (int i = lower; i <= upper; i++) {
                int c = colors[i];
                int n = histogram[c];
                count += n;
                sumR += ((c >> 10) & 0x1F) * (long) n;
                sumG += ((c >> 5) & 0x1F) * (long) n;
                sumB += (c & 0x1F) * (long) n;
            }
            if (count == 0) {
                return new Swatch(0xFF000000, 0);
            }
            float f = count;
            int rgb = 0xFF000000
                    | (modifyWordWidth(Math.round(sumR / f), 5, 8) << 16)
                    | (modifyWordWidth(Math.round(sumG / f), 5, 8) << 8)
                    | modifyWordWidth(Math.round(sumB / f), 5, 8);
            return new Swatch(rgb, count);
        }
    }

    /** 旧入口：保留签名，内部改成忠实实现；maxColors 用 Palette 默认 16 */
    private static List<Swatch> quantize(int[] pixels, SwatchFilter filter) {
        return quantizeToSwatches(pixels, MAX_COLORS, filter);
    }

    public interface SwatchFilter {
        boolean accept(int rgb);
    }

    // ---- 判定 helper（AOSP ColorUtils / ContrastColorUtil 的等价实现） ----

    static boolean isBlack(float[] hsl) {
        return hsl[2] <= BLACK_MAX_LIGHTNESS * 100f;
    }

    static boolean isWhite(float[] hsl) {
        return hsl[2] >= WHITE_MIN_LIGHTNESS * 100f;
    }

    static boolean isWhiteOrBlack(Swatch s) {
        return isBlack(s.hsl) || isWhite(s.hsl);
    }


    /** ContrastColorUtil.isColorLight 的语义：用亮度（luminance）而不是 HSL 明度 */
    static boolean isColorLight(int color) {
        float r = ((color >> 16) & 0xFF) / 255f;
        float g = ((color >> 8) & 0xFF) / 255f;
        float b = (color & 0xFF) / 255f;
        return 0.2126f * lin(r) + 0.7152f * lin(g) + 0.0722f * lin(b) > 0.5f;
    }

    private static float lin(float c) {
        return c <= 0.03928f ? c / 12.92f : (float) Math.pow((c + 0.055f) / 1.055f, 2.4f);
    }

    /** 与 ColorUtils.colorToHSL 同序：h∈[0,360)、s∈[0,1]、l∈[0,100] */
    public static float[] colorToHsl(int color) {
        float[] hsl = new float[3];
        float r = ((color >> 16) & 0xFF) / 255f;
        float g = ((color >> 8) & 0xFF) / 255f;
        float b = (color & 0xFF) / 255f;
        float cMax = Math.max(r, Math.max(g, b));
        float cMin = Math.min(r, Math.min(g, b));
        float delta = cMax - cMin;
        float l = (cMax + cMin) / 2f;
        float s = 0f;
        float h = 0f;
        if (delta > 0f) {
            s = l > 0.5f ? delta / (2f - cMax - cMin) : delta / (cMax + cMin);
            if (cMax == r) {
                h = ((g - b) / delta + (g < b ? 6f : 0f)) * 60f;
            } else if (cMax == g) {
                h = ((b - r) / delta + 2f) * 60f;
            } else {
                h = ((r - g) / delta + 4f) * 60f;
            }
        }
        hsl[0] = h;
        hsl[1] = s;
        hsl[2] = l * 100f;
        return hsl;
    }

    public static int hslToColor(float[] hsl) {
        float h = hsl[0];
        float s = Math.max(0f, Math.min(1f, hsl[1]));
        float l = Math.max(0f, Math.min(1f, hsl[2] / 100f));
        float c = (1f - Math.abs(2f * l - 1f)) * s;
        float x = c * (1f - Math.abs((h / 60f) % 2f - 1f));
        float m = l - c / 2f;
        float r;
        float g;
        float b;
        if (h < 60f) {
            r = c; g = x; b = 0f;
        } else if (h < 120f) {
            r = x; g = c; b = 0f;
        } else if (h < 180f) {
            r = 0f; g = c; b = x;
        } else if (h < 240f) {
            r = 0f; g = x; b = c;
        } else if (h < 300f) {
            r = x; g = 0f; b = c;
        } else {
            r = c; g = 0f; b = x;
        }
        return (0xFF << 24) | (Math.round((r + m) * 255f) << 16)
                | (Math.round((g + m) * 255f) << 8) | Math.round((b + m) * 255f);
    }

    private static float[] rgbToHsl(int color) {
        return colorToHsl(color);
    }

    // ---- 两条链路的入口 ----

    /**
     * MediaNotificationProcessor.findBackgroundSwatch 的等价实现
     * （证据：work/y7s-a11/tmp/MediaNotificationProcessor.java:157-174）。
     * 三条容易漏的：空 palette 要回白色 Swatch 而不是 null；dominant 是白/黑时改挑
     * "非白非黑里最大的那块"；而且**只有 dominant.pop / 候选.pop <= 2.5 才真的换人**，
     * 否则大面积纯白封面会被一个小色块抢掉背景色。
     */
    public static Swatch findBackgroundSwatch(Palette palette) {
        if (palette == null) {
            return null;
        }
        Swatch dominant = palette.dominant();
        if (dominant == null) {
            return new Swatch(0xFFFFFFFF, 100);
        }
        if (!isWhiteOrBlack(dominant)) {
            return dominant;
        }
        float population = -1f;
        Swatch found = null;
        for (Swatch s : palette.all()) {
            if (s == dominant || s.population <= population || isWhiteOrBlack(s)) {
                continue;
            }
            population = s.population;
            found = s;
        }
        return found != null && (dominant.population / population)
                <= POPULATION_FRACTION_FOR_WHITE_OR_BLACK ? found : dominant;
    }

    /**
     * 收起态着色的一整条：背景=左半幅 dominant（findBackgroundSwatch），
     * 前景=右 60% 里按背景明暗挑 vibrant/muted，且排除与背景同色相（±10°）与白黑。
     */
    public static int[] colorizedPair(Bitmap art) {
        if (art == null) {
            return null;
        }
        int w = art.getWidth();
        int h = art.getHeight();
        int[] reg = collapsedRegions(w, h);
        Palette bgPalette = generate(art, reg[0], reg[1], reg[2], reg[3], null);
        Swatch bgSwatch = findBackgroundSwatch(bgPalette);
        if (bgSwatch == null) {
            return null;
        }
        final int bgColor = bgSwatch.rgb;
        final float bgHue = bgSwatch.hue();
        // 参考实现（MediaNotificationProcessor.java:72-81）：黑白底**不加**色相排除 filter，
        // 只有非黑非白的背景才把与背景同色相（±10°）的像素剔掉；黑白/白始终要剔。
        final boolean applyHue = !isWhiteOrBlack(bgSwatch);
        Palette fgPalette = generate(art, reg[4], reg[5], reg[6], reg[7], rgb -> {
                    float[] hsl = colorToHsl(rgb);
                    if (hsl[2] <= BLACK_MAX_LIGHTNESS * 100f
                            || hsl[2] >= WHITE_MIN_LIGHTNESS * 100f) {
                        return false;
                    }
                    if (!applyHue) {
                        return true;
                    }
                    float delta = Math.abs(hsl[0] - bgHue);
                    return delta > 10f && delta < 350f;
                });
        int fg = selectForegroundColor(bgColor, fgPalette);
        return new int[]{bgColor, fg};
    }

    /**
     * MediaNotificationProcessor.selectForegroundColor 的等价实现
     * （证据：MediaNotificationProcessor.java:95-151）。
     * 明底挑 light 族、暗底挑 dark 族，各族先 vibrant 对拼、再 muted 对拼，
     * 都没有就退 dominant（也要过群体门槛），再没有就用兜底色。
     */
    public static int selectForegroundColor(int bgColor, Palette palette) {
        if (palette == null) {
            return 0xFFFFFFFF;
        }
        if (isColorLight(bgColor)) {
            return selectForegroundColorForSwatches(palette.darkVibrant(), palette.vibrant(),
                    palette.darkMuted(), palette.muted(), palette.dominant(), 0xFF000000);
        }
        return selectForegroundColorForSwatches(palette.lightVibrant(), palette.vibrant(),
                palette.lightMuted(), palette.muted(), palette.dominant(), 0xFFFFFFFF);
    }

    private static int selectForegroundColorForSwatches(Swatch vibrantA, Swatch vibrantB,
                                                        Swatch mutedA, Swatch mutedB,
                                                        Swatch dominant, int fallback) {
        Swatch candidate = selectVibrantCandidate(vibrantA, vibrantB);
        if (candidate == null) {
            candidate = selectMutedCandidate(mutedB, mutedA);
        }
        if (candidate == null) {
            return hasEnoughPopulation(dominant) ? dominant.rgb : fallback;
        }
        if (candidate == dominant) {
            return candidate.rgb;
        }
        // 参考实现这里是**整数除法**（pop/predominant < 0.01f 恒等于"候选比 dominant 小"），
        // 是 AOSP 的真实行为，照抄而不是"顺手改成浮点"
        if (dominant != null && dominant.population > 0
                && candidate.population / dominant.population < POPULATION_FRACTION_FOR_DOMINANT
                && dominant.saturation() > MIN_SATURATION_WHEN_DECIDING) {
            return dominant.rgb;
        }
        return candidate.rgb;
    }

    private static Swatch selectVibrantCandidate(Swatch a, Swatch b) {
        boolean ha = hasEnoughPopulation(a);
        boolean hb = hasEnoughPopulation(b);
        if (ha && hb) {
            return ((float) a.population) / ((float) b.population) < 1.0f ? b : a;
        }
        if (ha) {
            return a;
        }
        return hb ? b : null;
    }

    private static Swatch selectMutedCandidate(Swatch a, Swatch b) {
        boolean ha = hasEnoughPopulation(a);
        boolean hb = hasEnoughPopulation(b);
        if (ha && hb) {
            return a.saturation() * (((float) a.population) / ((float) b.population))
                    > b.saturation() ? a : b;
        }
        if (ha) {
            return a;
        }
        return hb ? b : null;
    }

    /** population / 22500 > 0.002（≈45 个像素）才够格参与竞选 */
    static boolean hasEnoughPopulation(Swatch s) {
        return s != null && ((double) (((float) s.population) / RESIZE_BITMAP_AREA))
                > MINIMUM_IMAGE_FRACTION;
    }

    /**
     * 原生播放卡背景（MediaDataManager.computeBackgroundColor）：
     * 极亮/极暗去饱和 → 饱和度 ×0.8 → 亮度固定 0.25 ⇒ 恒为深色。
     */
    public static int computeCardBackground(Swatch swatch) {
        // A11 无封面时不是给常量：MediaDataManager.java:542-553 把 rgb=-1（白）继续送进同一条
        // HSL 变换（l>0.95 去饱和 → sat*0.8 → l 锁 0.25），算出来是 0xFF404040。
        // 0xFF444444 是另一条路径的值（MediaDataManager.java:339 的 resumption 兜底），别混用。
        return computeCardBackground(swatch == null ? 0xFFFFFFFF : swatch.rgb);
    }

    public static int computeCardBackground(int rgb) {
        float[] hsl = colorToHsl(rgb);
        if (hsl[2] < 5f || hsl[2] > 95f) {
            hsl[1] = 0f;
        }
        hsl[1] *= CARD_SATURATION_SCALE;
        hsl[2] = CARD_LIGHTNESS * 100f;
        return hslToColor(hsl);
    }

    // ------------------------------------------------------------ 文字对比度校正
    // 逐条对齐 com.android.internal.util.ContrastColorUtil
    // （证据：work/y7s-a11/tmp/ContrastColorUtil.java:198-270、329-335、413-425、474-495）
    // 与 Notification.MediaStyle.ensureColors
    // （证据：work/y7s-a11/tmp/MediaStyle.java:2473-2516）。

    private static final double XN = 95.047;
    private static final double YN = 100.0;
    private static final double ZN = 108.883;
    private static final double LAB_EPSILON = 0.008856451679;
    private static final double LAB_KAPPA = 903.2962962;

    private static double srgbToLinear(double c) {
        return c <= 0.04045 ? c / 12.92 : Math.pow((c + 0.055) / 1.055, 2.4);
    }

    private static double linearToSrgb(double c) {
        return c <= 0.0031308 ? c * 12.92 : 1.055 * Math.pow(c, 1.0 / 2.4) - 0.055;
    }

    /** ColorUtils.calculateLuminance = XYZ 的 Y / 白点 Y */
    public static double luminance(int color) {
        double r = srgbToLinear(((color >> 16) & 0xFF) / 255.0);
        double g = srgbToLinear(((color >> 8) & 0xFF) / 255.0);
        double b = srgbToLinear((color & 0xFF) / 255.0);
        return 0.2126 * r + 0.7152 * g + 0.0722 * b;
    }

    /** 半透明前景先合成到背景上，再算 WCAG 对比度（AOSP 同实现） */
    public static double contrast(int foreground, int background) {
        int fg = foreground;
        if (((fg >>> 24) & 0xFF) < 255) {
            fg = compositeColors(fg, background);
        }
        double l1 = luminance(fg) + 0.05;
        double l2 = luminance(background) + 0.05;
        return Math.max(l1, l2) / Math.min(l1, l2);
    }

    public static int compositeColors(int foreground, int background) {
        int aF = (foreground >>> 24) & 0xFF;
        int aB = (background >>> 24) & 0xFF;
        // 逐式照 A11 ColorUtilsFromCompat（A11-ContrastColorUtil.java:447-466）：
        //   a   = 255 - ((255-bgA)*(255-fgA))/255
        //   ch  = ((fgC*255*fgA) + (bgC*bgA*(255-fgA))) / (a*255)
        // 之前写的是另一种整数分组，半透明前景会差 1 个通道值 —— 对比度跟着差，
        // 于是"alpha 二分够 4.5:1"这条链在临界点上会判反（自检抓到）。
        int a = 255 - (((255 - aB) * (255 - aF)) / 255);
        int out = a << 24;
        for (int shift = 16; shift >= 0; shift -= 8) {
            int cF = (foreground >> shift) & 0xFF;
            int cB = (background >> shift) & 0xFF;
            int c = a == 0 ? 0 : (((cF * 255) * aF) + (cB * aB * (255 - aF))) / (a * 255);
            out |= Math.max(0, Math.min(255, c)) << shift;
        }
        return out;
    }

    public static boolean satisfiesTextContrast(int background, int foreground) {
        return contrast(foreground, background) >= 4.5;
    }

    /** sRGB -> CIE-LAB（D65） */
    public static void colorToLab(int color, double[] outLab) {
        double r = srgbToLinear(((color >> 16) & 0xFF) / 255.0);
        double g = srgbToLinear(((color >> 8) & 0xFF) / 255.0);
        double b = srgbToLinear((color & 0xFF) / 255.0);
        double x = 100.0 * (r * 0.4124 + g * 0.3576 + b * 0.1805) / XN;
        double y = 100.0 * (r * 0.2126 + g * 0.7152 + b * 0.0722) / YN;
        double z = 100.0 * (r * 0.0193 + g * 0.1192 + b * 0.9505) / ZN;
        x = x > LAB_EPSILON ? Math.cbrt(x) : (LAB_KAPPA * x + 16.0) / 116.0;
        y = y > LAB_EPSILON ? Math.cbrt(y) : (LAB_KAPPA * y + 16.0) / 116.0;
        z = z > LAB_EPSILON ? Math.cbrt(z) : (LAB_KAPPA * z + 16.0) / 116.0;
        outLab[0] = 116.0 * y - 16.0;
        outLab[1] = 500.0 * (x - y);
        outLab[2] = 200.0 * (y - z);
    }

    /** CIE-LAB -> sRGB */
    public static int labToColor(double l, double a, double b) {
        double fy = (l + 16.0) / 116.0;
        double fx = a / 500.0 + fy;
        double fz = fy - b / 200.0;
        double xr = fx * fx * fx > LAB_EPSILON ? fx * fx * fx : (116.0 * fx - 16.0) / LAB_KAPPA;
        double yr = l > LAB_KAPPA * LAB_EPSILON
                ? Math.pow((l + 16.0) / 116.0, 3.0) : l / LAB_KAPPA;
        double zr = fz * fz * fz > LAB_EPSILON ? fz * fz * fz : (116.0 * fz - 16.0) / LAB_KAPPA;
        double x = xr * XN / 100.0;
        double y = yr * YN / 100.0;
        double z = zr * ZN / 100.0;
        double rl = x * 3.2406 + y * -1.5372 + z * -0.4986;
        double gl = x * -0.9689 + y * 1.8758 + z * 0.0415;
        double bl = x * 0.0557 + y * -0.2040 + z * 1.0570;
        return (0xFF << 24) | (clamp255(linearToSrgb(rl)) << 16)
                | (clamp255(linearToSrgb(gl)) << 8) | clamp255(linearToSrgb(bl));
    }

    private static int clamp255(double v) {
        return Math.max(0, Math.min(255, (int) Math.round(v * 255.0)));
    }

    /** ContrastColorUtil.changeColorLightness：在 LAB 上把 L* 加 amount 再夹到 [0,100] */
    public static int changeColorLightness(int baseColor, int amount) {
        double[] lab = new double[3];
        colorToLab(baseColor, lab);
        lab[0] = Math.max(0.0, Math.min(100.0, lab[0] + amount));
        return labToColor(lab[0], lab[1], lab[2]);
    }

    /** ContrastColorUtil.findContrastColor：把 L* 往黑的方向二分，直到满足对比度 */
    public static int findContrastColor(int color, int other, boolean findFg, double minRatio) {
        int fg = findFg ? color : other;
        int bg = findFg ? other : color;
        if (contrast(fg, bg) >= minRatio) {
            return color;
        }
        double[] lab = new double[3];
        colorToLab(findFg ? fg : bg, lab);
        double low = 0.0;
        double high = lab[0];
        double a = lab[1];
        double b = lab[2];
        for (int i = 0; i < 15 && high - low > 1e-5; i++) {
            double l = (low + high) / 2.0;
            int c = labToColor(l, a, b);
            if (contrast(findFg ? c : fg, findFg ? bg : c) > minRatio) {
                low = l;
            } else {
                high = l;
            }
        }
        return labToColor(low, a, b);
    }

    /**
     * ContrastColorUtil.findContrastColorAgainstDark：往白方向二分 HSL 亮度
     * （AndroidX 的 HSL 亮度是 [0,1] 标度，这里内部用 [0,100]，进出各换算一次）。
     */
    public static int findContrastColorAgainstDark(int color, int other, boolean findFg,
                                                   double minRatio) {
        int fg = findFg ? color : other;
        int bg = findFg ? other : color;
        if (contrast(fg, bg) >= minRatio) {
            return color;
        }
        float[] base = colorToHsl(findFg ? fg : bg);
        float[] hsl = new float[]{base[0], base[1], base[2] / 100f};
        float low = hsl[2];
        float high = 1.0f;
        for (int i = 0; i < 15 && high - low > 1e-5f; i++) {
            float l = (low + high) / 2f;
            hsl[2] = l;
            int c = hslToColor(new float[]{hsl[0], hsl[1], hsl[2] * 100f});
            if (findFg) {
                fg = c;
            } else {
                bg = c;
            }
            if (contrast(fg, bg) > minRatio) {
                high = l;
            } else {
                low = l;
            }
        }
        return findFg ? fg : bg;
    }

    /**
     * MediaStyle.ensureColors 的着色分支：返回 {primary, secondary}。
     * 明暗判定、正负 20/10 的 L* 位移、以及 secondary 不达标时回推 primary 的次序都照源码。
     */
    public static int[] colorizedTextColors(int backgroundColor, int foregroundColor) {
        double backLum = luminance(backgroundColor);
        double textLum = luminance(foregroundColor);
        double cr = contrast(foregroundColor, backgroundColor);
        boolean backgroundLight = (backLum > textLum
                && satisfiesTextContrast(backgroundColor, 0xFF000000))
                || (backLum <= textLum && !satisfiesTextContrast(backgroundColor, 0xFFFFFFFF));
        if (cr < 4.5) {
            int secondary = backgroundLight
                    ? findContrastColor(foregroundColor, backgroundColor, true, 4.5)
                    : findContrastColorAgainstDark(foregroundColor, backgroundColor, true, 4.5);
            int primary = changeColorLightness(secondary, backgroundLight ? -20 : 10);
            return new int[]{primary, secondary};
        }
        int primary = foregroundColor;
        int secondary = changeColorLightness(foregroundColor, backgroundLight ? 20 : -10);
        if (contrast(secondary, backgroundColor) < 4.5) {
            secondary = backgroundLight
                    ? findContrastColor(secondary, backgroundColor, true, 4.5)
                    : findContrastColorAgainstDark(secondary, backgroundColor, true, 4.5);
            primary = changeColorLightness(secondary, backgroundLight ? -20 : 10);
        }
        return new int[]{primary, secondary};
    }

    /** IlluminationDrawable：把背景亮度 ±0.15 得到按压高亮色（0.15 来自 dimen highlight=15） */
    public static int illuminationHighlight(int background, float delta) {
        float[] hsl = colorToHsl(background);
        hsl[2] = Math.max(0f, Math.min(100f, hsl[2] + delta * 100f));
        return hslToColor(hsl);
    }
}
