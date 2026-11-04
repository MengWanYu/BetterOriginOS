package androidx.palette.graphics;

import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Rect;
import android.os.AsyncTask;
import android.util.Log;
import android.util.SparseBooleanArray;
import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.Px;
import androidx.collection.ArrayMap;
import androidx.core.graphics.ColorUtils;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/* JADX INFO: loaded from: classes.dex */
public final class Palette {
    static final int DEFAULT_CALCULATE_NUMBER_COLORS = 16;
    static final Filter DEFAULT_FILTER = new Filter() { // from class: androidx.palette.graphics.Palette.1
        private static final float BLACK_MAX_LIGHTNESS = 0.05f;
        private static final float WHITE_MIN_LIGHTNESS = 0.95f;

        @Override // androidx.palette.graphics.Palette.Filter
        public boolean isAllowed(int i, float[] fArr) {
            return (isWhite(fArr) || isBlack(fArr) || isNearRedILine(fArr)) ? false : true;
        }

        private boolean isBlack(float[] fArr) {
            return fArr[2] <= BLACK_MAX_LIGHTNESS;
        }

        private boolean isWhite(float[] fArr) {
            return fArr[2] >= WHITE_MIN_LIGHTNESS;
        }

        private boolean isNearRedILine(float[] fArr) {
            return fArr[0] >= 10.0f && fArr[0] <= 37.0f && fArr[1] <= 0.82f;
        }
    };
    static final int DEFAULT_RESIZE_BITMAP_AREA = 12544;
    static final String LOG_TAG = "Palette";
    static final boolean LOG_TIMINGS = false;
    static final float MIN_CONTRAST_BODY_TEXT = 4.5f;
    static final float MIN_CONTRAST_TITLE_TEXT = 3.0f;
    private final List<Swatch> mSwatches;
    private final List<Target> mTargets;
    private final SparseBooleanArray mUsedColors = new SparseBooleanArray();
    private final Map<Target, Swatch> mSelectedSwatches = new ArrayMap();

    @Nullable
    private final Swatch mDominantSwatch = findDominantSwatch();

    public interface Filter {
        boolean isAllowed(@ColorInt int i, @NonNull float[] fArr);
    }

    public interface PaletteAsyncListener {
        void onGenerated(@Nullable Palette palette);
    }

    @NonNull
    public static Builder from(@NonNull Bitmap bitmap) {
        return new Builder(bitmap);
    }

    @NonNull
    public static Palette from(@NonNull List<Swatch> list) {
        return new Builder(list).generate();
    }

    @NonNull
    @Deprecated
    public static Palette generate(@NonNull Bitmap bitmap) {
        return from(bitmap).generate();
    }

    @NonNull
    @Deprecated
    public static Palette generate(@NonNull Bitmap bitmap, int i) {
        return from(bitmap).maximumColorCount(i).generate();
    }

    @NonNull
    @Deprecated
    public static AsyncTask<Bitmap, Void, Palette> generateAsync(@NonNull Bitmap bitmap, @NonNull PaletteAsyncListener paletteAsyncListener) {
        return from(bitmap).generate(paletteAsyncListener);
    }

    @NonNull
    @Deprecated
    public static AsyncTask<Bitmap, Void, Palette> generateAsync(@NonNull Bitmap bitmap, int i, @NonNull PaletteAsyncListener paletteAsyncListener) {
        return from(bitmap).maximumColorCount(i).generate(paletteAsyncListener);
    }

    Palette(List<Swatch> list, List<Target> list2) {
        this.mSwatches = list;
        this.mTargets = list2;
    }

    @NonNull
    public List<Swatch> getSwatches() {
        return Collections.unmodifiableList(this.mSwatches);
    }

    @NonNull
    public List<Target> getTargets() {
        return Collections.unmodifiableList(this.mTargets);
    }

    @Nullable
    public Swatch getVibrantSwatch() {
        return getSwatchForTarget(Target.VIBRANT);
    }

    @Nullable
    public Swatch getLightVibrantSwatch() {
        return getSwatchForTarget(Target.LIGHT_VIBRANT);
    }

    @Nullable
    public Swatch getDarkVibrantSwatch() {
        return getSwatchForTarget(Target.DARK_VIBRANT);
    }

    @Nullable
    public Swatch getMutedSwatch() {
        return getSwatchForTarget(Target.MUTED);
    }

    @Nullable
    public Swatch getLightMutedSwatch() {
        return getSwatchForTarget(Target.LIGHT_MUTED);
    }

    @Nullable
    public Swatch getDarkMutedSwatch() {
        return getSwatchForTarget(Target.DARK_MUTED);
    }

    @ColorInt
    public int getVibrantColor(@ColorInt int i) {
        return getColorForTarget(Target.VIBRANT, i);
    }

    @ColorInt
    public int getLightVibrantColor(@ColorInt int i) {
        return getColorForTarget(Target.LIGHT_VIBRANT, i);
    }

    @ColorInt
    public int getDarkVibrantColor(@ColorInt int i) {
        return getColorForTarget(Target.DARK_VIBRANT, i);
    }

    @ColorInt
    public int getMutedColor(@ColorInt int i) {
        return getColorForTarget(Target.MUTED, i);
    }

    @ColorInt
    public int getLightMutedColor(@ColorInt int i) {
        return getColorForTarget(Target.LIGHT_MUTED, i);
    }

    @ColorInt
    public int getDarkMutedColor(@ColorInt int i) {
        return getColorForTarget(Target.DARK_MUTED, i);
    }

    @Nullable
    public Swatch getSwatchForTarget(@NonNull Target target) {
        return this.mSelectedSwatches.get(target);
    }

    @ColorInt
    public int getColorForTarget(@NonNull Target target, @ColorInt int i) {
        Swatch swatchForTarget = getSwatchForTarget(target);
        return swatchForTarget != null ? swatchForTarget.getRgb() : i;
    }

    @Nullable
    public Swatch getDominantSwatch() {
        return this.mDominantSwatch;
    }

    @ColorInt
    public int getDominantColor(@ColorInt int i) {
        Swatch swatch = this.mDominantSwatch;
        return swatch != null ? swatch.getRgb() : i;
    }

    void generate() {
        int size = this.mTargets.size();
        for (int i = 0; i < size; i++) {
            Target target = this.mTargets.get(i);
            target.normalizeWeights();
            this.mSelectedSwatches.put(target, generateScoredTarget(target));
        }
        this.mUsedColors.clear();
    }

    @Nullable
    private Swatch generateScoredTarget(Target target) {
        Swatch maxScoredSwatchForTarget = getMaxScoredSwatchForTarget(target);
        if (maxScoredSwatchForTarget != null && target.isExclusive()) {
            this.mUsedColors.append(maxScoredSwatchForTarget.getRgb(), true);
        }
        return maxScoredSwatchForTarget;
    }

    @Nullable
    private Swatch getMaxScoredSwatchForTarget(Target target) {
        int size = this.mSwatches.size();
        float f = 0.0f;
        Swatch swatch = null;
        for (int i = 0; i < size; i++) {
            Swatch swatch2 = this.mSwatches.get(i);
            if (shouldBeScoredForTarget(swatch2, target)) {
                float fGenerateScore = generateScore(swatch2, target);
                if (swatch == null || fGenerateScore > f) {
                    swatch = swatch2;
                    f = fGenerateScore;
                }
            }
        }
        return swatch;
    }

    private boolean shouldBeScoredForTarget(Swatch swatch, Target target) {
        float[] hsl = swatch.getHsl();
        return hsl[1] >= target.getMinimumSaturation() && hsl[1] <= target.getMaximumSaturation() && hsl[2] >= target.getMinimumLightness() && hsl[2] <= target.getMaximumLightness() && !this.mUsedColors.get(swatch.getRgb());
    }

    private float generateScore(Swatch swatch, Target target) {
        float[] hsl = swatch.getHsl();
        return (target.getSaturationWeight() > 0.0f ? (1.0f - Math.abs(hsl[1] - target.getTargetSaturation())) * target.getSaturationWeight() : 0.0f) + (target.getLightnessWeight() > 0.0f ? target.getLightnessWeight() * (1.0f - Math.abs(hsl[2] - target.getTargetLightness())) : 0.0f) + (target.getPopulationWeight() > 0.0f ? target.getPopulationWeight() * (swatch.getPopulation() / (this.mDominantSwatch != null ? r6.getPopulation() : 1)) : 0.0f);
    }

    @Nullable
    private Swatch findDominantSwatch() {
        int size = this.mSwatches.size();
        int population = Integer.MIN_VALUE;
        Swatch swatch = null;
        for (int i = 0; i < size; i++) {
            Swatch swatch2 = this.mSwatches.get(i);
            if (swatch2.getPopulation() > population) {
                population = swatch2.getPopulation();
                swatch = swatch2;
            }
        }
        return swatch;
    }

    public static final class Swatch {
        private final int mBlue;
        private int mBodyTextColor;
        private boolean mGeneratedTextColors;
        private final int mGreen;

        @Nullable
        private float[] mHsl;
        private final int mPopulation;
        private final int mRed;
        private final int mRgb;
        private int mTitleTextColor;

        public Swatch(@ColorInt int i, int i2) {
            this.mRed = Color.red(i);
            this.mGreen = Color.green(i);
            this.mBlue = Color.blue(i);
            this.mRgb = i;
            this.mPopulation = i2;
        }

        @ColorInt
        public int getRgb() {
            return this.mRgb;
        }

        @NonNull
        public float[] getHsl() {
            if (this.mHsl == null) {
                this.mHsl = new float[3];
            }
            ColorUtils.RGBToHSL(this.mRed, this.mGreen, this.mBlue, this.mHsl);
            return this.mHsl;
        }

        public int getPopulation() {
            return this.mPopulation;
        }

        @ColorInt
        public int getTitleTextColor() {
            ensureTextColorsGenerated();
            return this.mTitleTextColor;
        }

        @ColorInt
        public int getBodyTextColor() {
            ensureTextColorsGenerated();
            return this.mBodyTextColor;
        }

        private void ensureTextColorsGenerated() {
            int alphaComponent;
            int alphaComponent2;
            if (this.mGeneratedTextColors) {
                return;
            }
            int iCalculateMinimumAlpha = ColorUtils.calculateMinimumAlpha(-1, this.mRgb, Palette.MIN_CONTRAST_BODY_TEXT);
            int iCalculateMinimumAlpha2 = ColorUtils.calculateMinimumAlpha(-1, this.mRgb, 3.0f);
            if (iCalculateMinimumAlpha != -1 && iCalculateMinimumAlpha2 != -1) {
                this.mBodyTextColor = ColorUtils.setAlphaComponent(-1, iCalculateMinimumAlpha);
                this.mTitleTextColor = ColorUtils.setAlphaComponent(-1, iCalculateMinimumAlpha2);
                this.mGeneratedTextColors = true;
                return;
            }
            int iCalculateMinimumAlpha3 = ColorUtils.calculateMinimumAlpha(-16777216, this.mRgb, Palette.MIN_CONTRAST_BODY_TEXT);
            int iCalculateMinimumAlpha4 = ColorUtils.calculateMinimumAlpha(-16777216, this.mRgb, 3.0f);
            if (iCalculateMinimumAlpha3 != -1 && iCalculateMinimumAlpha4 != -1) {
                this.mBodyTextColor = ColorUtils.setAlphaComponent(-16777216, iCalculateMinimumAlpha3);
                this.mTitleTextColor = ColorUtils.setAlphaComponent(-16777216, iCalculateMinimumAlpha4);
                this.mGeneratedTextColors = true;
                return;
            }
            if (iCalculateMinimumAlpha != -1) {
                alphaComponent = ColorUtils.setAlphaComponent(-1, iCalculateMinimumAlpha);
            } else {
                alphaComponent = ColorUtils.setAlphaComponent(-16777216, iCalculateMinimumAlpha3);
            }
            this.mBodyTextColor = alphaComponent;
            if (iCalculateMinimumAlpha2 != -1) {
                alphaComponent2 = ColorUtils.setAlphaComponent(-1, iCalculateMinimumAlpha2);
            } else {
                alphaComponent2 = ColorUtils.setAlphaComponent(-16777216, iCalculateMinimumAlpha4);
            }
            this.mTitleTextColor = alphaComponent2;
            this.mGeneratedTextColors = true;
        }

        @NonNull
        public String toString() {
            return getClass().getSimpleName() + " [RGB: #" + Integer.toHexString(getRgb()) + "] [HSL: " + Arrays.toString(getHsl()) + "] [Population: " + this.mPopulation + "] [Title Text: #" + Integer.toHexString(getTitleTextColor()) + "] [Body Text: #" + Integer.toHexString(getBodyTextColor()) + ']';
        }

        public boolean equals(@Nullable Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj == null || getClass() != obj.getClass()) {
                return false;
            }
            Swatch swatch = (Swatch) obj;
            return this.mPopulation == swatch.mPopulation && this.mRgb == swatch.mRgb;
        }

        public int hashCode() {
            return (this.mRgb * 31) + this.mPopulation;
        }
    }

    public static final class Builder {

        @Nullable
        private final Bitmap mBitmap;

        @Nullable
        private Rect mRegion;

        @Nullable
        private final List<Swatch> mSwatches;
        private final List<Target> mTargets = new ArrayList();
        private int mMaxColors = 16;
        private int mResizeArea = Palette.DEFAULT_RESIZE_BITMAP_AREA;
        private int mResizeMaxDimension = -1;
        private final List<Filter> mFilters = new ArrayList();

        public Builder(@NonNull Bitmap bitmap) {
            if (bitmap == null || bitmap.isRecycled()) {
                throw new IllegalArgumentException("Bitmap is not valid");
            }
            this.mFilters.add(Palette.DEFAULT_FILTER);
            this.mBitmap = bitmap;
            this.mSwatches = null;
            this.mTargets.add(Target.LIGHT_VIBRANT);
            this.mTargets.add(Target.VIBRANT);
            this.mTargets.add(Target.DARK_VIBRANT);
            this.mTargets.add(Target.LIGHT_MUTED);
            this.mTargets.add(Target.MUTED);
            this.mTargets.add(Target.DARK_MUTED);
        }

        public Builder(@NonNull List<Swatch> list) {
            if (list == null || list.isEmpty()) {
                throw new IllegalArgumentException("List of Swatches is not valid");
            }
            this.mFilters.add(Palette.DEFAULT_FILTER);
            this.mSwatches = list;
            this.mBitmap = null;
        }

        @NonNull
        public Builder maximumColorCount(int i) {
            this.mMaxColors = i;
            return this;
        }

        @NonNull
        @Deprecated
        public Builder resizeBitmapSize(int i) {
            this.mResizeMaxDimension = i;
            this.mResizeArea = -1;
            return this;
        }

        @NonNull
        public Builder resizeBitmapArea(int i) {
            this.mResizeArea = i;
            this.mResizeMaxDimension = -1;
            return this;
        }

        @NonNull
        public Builder clearFilters() {
            this.mFilters.clear();
            return this;
        }

        @NonNull
        public Builder addFilter(@NonNull Filter filter) {
            if (filter != null) {
                this.mFilters.add(filter);
            }
            return this;
        }

        @NonNull
        public Builder setRegion(@Px int i, @Px int i2, @Px int i3, @Px int i4) {
            if (this.mBitmap != null) {
                if (this.mRegion == null) {
                    this.mRegion = new Rect();
                }
                this.mRegion.set(0, 0, this.mBitmap.getWidth(), this.mBitmap.getHeight());
                if (!this.mRegion.intersect(i, i2, i3, i4)) {
                    throw new IllegalArgumentException("The given region must intersect with the Bitmap's dimensions.");
                }
            }
            return this;
        }

        @NonNull
        public Builder clearRegion() {
            this.mRegion = null;
            return this;
        }

        @NonNull
        public Builder addTarget(@NonNull Target target) {
            if (!this.mTargets.contains(target)) {
                this.mTargets.add(target);
            }
            return this;
        }

        @NonNull
        public Builder clearTargets() {
            List<Target> list = this.mTargets;
            if (list != null) {
                list.clear();
            }
            return this;
        }

        @NonNull
        public Palette generate() {
            List<Swatch> quantizedColors;
            Filter[] filterArr;
            Bitmap bitmap = this.mBitmap;
            if (bitmap != null) {
                Bitmap bitmapScaleBitmapDown = scaleBitmapDown(bitmap);
                Rect rect = this.mRegion;
                if (bitmapScaleBitmapDown != this.mBitmap && rect != null) {
                    double width = ((double) bitmapScaleBitmapDown.getWidth()) / ((double) this.mBitmap.getWidth());
                    rect.left = (int) Math.floor(((double) rect.left) * width);
                    rect.top = (int) Math.floor(((double) rect.top) * width);
                    rect.right = Math.min((int) Math.ceil(((double) rect.right) * width), bitmapScaleBitmapDown.getWidth());
                    rect.bottom = Math.min((int) Math.ceil(((double) rect.bottom) * width), bitmapScaleBitmapDown.getHeight());
                }
                int[] pixelsFromBitmap = getPixelsFromBitmap(bitmapScaleBitmapDown);
                int i = this.mMaxColors;
                if (this.mFilters.isEmpty()) {
                    filterArr = null;
                } else {
                    List<Filter> list = this.mFilters;
                    filterArr = (Filter[]) list.toArray(new Filter[list.size()]);
                }
                ColorCutQuantizer colorCutQuantizer = new ColorCutQuantizer(pixelsFromBitmap, i, filterArr);
                if (bitmapScaleBitmapDown != this.mBitmap) {
                    bitmapScaleBitmapDown.recycle();
                }
                quantizedColors = colorCutQuantizer.getQuantizedColors();
            } else {
                quantizedColors = this.mSwatches;
                if (quantizedColors == null) {
                    throw new AssertionError();
                }
            }
            Palette palette = new Palette(quantizedColors, this.mTargets);
            palette.generate();
            return palette;
        }

        /* JADX WARN: Type inference failed for: r0v0, types: [androidx.palette.graphics.Palette$Builder$1] */
        @NonNull
        public AsyncTask<Bitmap, Void, Palette> generate(@NonNull final PaletteAsyncListener paletteAsyncListener) {
            if (paletteAsyncListener == null) {
                throw new IllegalArgumentException("listener can not be null");
            }
            return new AsyncTask<Bitmap, Void, Palette>() { // from class: androidx.palette.graphics.Palette.Builder.1
                /* JADX INFO: Access modifiers changed from: protected */
                @Override // android.os.AsyncTask
                @Nullable
                public Palette doInBackground(Bitmap... bitmapArr) {
                    try {
                        return Builder.this.generate();
                    } catch (Exception e) {
                        Log.e(Palette.LOG_TAG, "Exception thrown during async generate", e);
                        return null;
                    }
                }

                /* JADX INFO: Access modifiers changed from: protected */
                @Override // android.os.AsyncTask
                public void onPostExecute(@Nullable Palette palette) {
                    paletteAsyncListener.onGenerated(palette);
                }
            }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, this.mBitmap);
        }

        private int[] getPixelsFromBitmap(Bitmap bitmap) {
            int width = bitmap.getWidth();
            int height = bitmap.getHeight();
            int[] iArr = new int[width * height];
            bitmap.getPixels(iArr, 0, width, 0, 0, width, height);
            Rect rect = this.mRegion;
            if (rect == null) {
                return iArr;
            }
            int iWidth = rect.width();
            int iHeight = this.mRegion.height();
            int[] iArr2 = new int[iWidth * iHeight];
            for (int i = 0; i < iHeight; i++) {
                System.arraycopy(iArr, ((this.mRegion.top + i) * width) + this.mRegion.left, iArr2, i * iWidth, iWidth);
            }
            return iArr2;
        }

        private Bitmap scaleBitmapDown(Bitmap bitmap) {
            int iMax;
            int i;
            double dSqrt = -1.0d;
            if (this.mResizeArea > 0) {
                int width = bitmap.getWidth() * bitmap.getHeight();
                int i2 = this.mResizeArea;
                if (width > i2) {
                    dSqrt = Math.sqrt(((double) i2) / ((double) width));
                }
            } else if (this.mResizeMaxDimension > 0 && (iMax = Math.max(bitmap.getWidth(), bitmap.getHeight())) > (i = this.mResizeMaxDimension)) {
                dSqrt = ((double) i) / ((double) iMax);
            }
            return dSqrt <= 0.0d ? bitmap : Bitmap.createScaledBitmap(bitmap, (int) Math.ceil(((double) bitmap.getWidth()) * dSqrt), (int) Math.ceil(((double) bitmap.getHeight()) * dSqrt), false);
        }
    }
}
