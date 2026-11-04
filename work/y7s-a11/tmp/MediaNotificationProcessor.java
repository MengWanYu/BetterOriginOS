package com.android.systemui.statusbar.notification;

import android.app.Notification;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.Icon;
import androidx.annotation.VisibleForTesting;
import androidx.palette.graphics.Palette;
import com.android.internal.util.ContrastColorUtil;
import com.android.systemui.R;

/* JADX INFO: loaded from: classes2.dex */
public class MediaNotificationProcessor {
    private static final float BLACK_MAX_LIGHTNESS = 0.08f;
    private static final double MINIMUM_IMAGE_FRACTION = 0.002d;
    private static final float MIN_SATURATION_WHEN_DECIDING = 0.19f;
    private static final float POPULATION_FRACTION_FOR_DOMINANT = 0.01f;
    private static final float POPULATION_FRACTION_FOR_MORE_VIBRANT = 1.0f;
    private static final float POPULATION_FRACTION_FOR_WHITE_OR_BLACK = 2.5f;
    private static final int RESIZE_BITMAP_AREA = 22500;
    private static final float WHITE_MIN_LIGHTNESS = 0.9f;
    private final Palette.Filter mBlackWhiteFilter;
    private final ImageGradientColorizer mColorizer;
    private final Context mContext;
    private final Context mPackageContext;

    static /* synthetic */ boolean lambda$new$0(int i, float[] fArr) {
        return !isWhiteOrBlack(fArr);
    }

    public MediaNotificationProcessor(Context context, Context context2) {
        this(context, context2, new ImageGradientColorizer());
    }

    @VisibleForTesting
    MediaNotificationProcessor(Context context, Context context2, ImageGradientColorizer imageGradientColorizer) {
        this.mBlackWhiteFilter = new Palette.Filter() { // from class: com.android.systemui.statusbar.notification.-$$Lambda$MediaNotificationProcessor$oWRwwE503YseXSqqQUwqkZxEskY
            @Override // androidx.palette.graphics.Palette.Filter
            public final boolean isAllowed(int i, float[] fArr) {
                return MediaNotificationProcessor.lambda$new$0(i, fArr);
            }
        };
        this.mContext = context;
        this.mPackageContext = context2;
        this.mColorizer = imageGradientColorizer;
    }

    public void processNotification(Notification notification, Notification.Builder builder) {
        int color;
        Icon largeIcon = notification.getLargeIcon();
        if (largeIcon != null) {
            builder.setRebuildStyledRemoteViews(true);
            Drawable drawableLoadDrawable = largeIcon.loadDrawable(this.mPackageContext);
            if (notification.isColorizedMedia()) {
                int intrinsicWidth = drawableLoadDrawable.getIntrinsicWidth();
                int intrinsicHeight = drawableLoadDrawable.getIntrinsicHeight();
                if (intrinsicWidth * intrinsicHeight > RESIZE_BITMAP_AREA) {
                    double dSqrt = Math.sqrt(22500.0f / r4);
                    intrinsicWidth = (int) (((double) intrinsicWidth) * dSqrt);
                    intrinsicHeight = (int) (dSqrt * ((double) intrinsicHeight));
                }
                Bitmap bitmapCreateBitmap = Bitmap.createBitmap(intrinsicWidth, intrinsicHeight, Bitmap.Config.ARGB_8888);
                Canvas canvas = new Canvas(bitmapCreateBitmap);
                drawableLoadDrawable.setBounds(0, 0, intrinsicWidth, intrinsicHeight);
                drawableLoadDrawable.draw(canvas);
                Palette.Builder builderGenerateArtworkPaletteBuilder = generateArtworkPaletteBuilder(bitmapCreateBitmap);
                Palette.Swatch swatchFindBackgroundSwatch = findBackgroundSwatch(builderGenerateArtworkPaletteBuilder.generate());
                color = swatchFindBackgroundSwatch.getRgb();
                builderGenerateArtworkPaletteBuilder.setRegion((int) (bitmapCreateBitmap.getWidth() * 0.4f), 0, bitmapCreateBitmap.getWidth(), bitmapCreateBitmap.getHeight());
                if (!isWhiteOrBlack(swatchFindBackgroundSwatch.getHsl())) {
                    final float f = swatchFindBackgroundSwatch.getHsl()[0];
                    builderGenerateArtworkPaletteBuilder.addFilter(new Palette.Filter() { // from class: com.android.systemui.statusbar.notification.-$$Lambda$MediaNotificationProcessor$jNuRDwOMbOj8fwROH917lxaryoM
                        @Override // androidx.palette.graphics.Palette.Filter
                        public final boolean isAllowed(int i, float[] fArr) {
                            return MediaNotificationProcessor.lambda$processNotification$1(f, i, fArr);
                        }
                    });
                }
                builderGenerateArtworkPaletteBuilder.addFilter(this.mBlackWhiteFilter);
                builder.setColorPalette(color, selectForegroundColor(color, builderGenerateArtworkPaletteBuilder.generate()));
            } else {
                color = this.mContext.getColor(R.color.notification_material_background_color);
            }
            builder.setLargeIcon(Icon.createWithBitmap(this.mColorizer.colorize(drawableLoadDrawable, color, this.mContext.getResources().getConfiguration().getLayoutDirection() == 1)));
        }
    }

    static /* synthetic */ boolean lambda$processNotification$1(float f, int i, float[] fArr) {
        float fAbs = Math.abs(fArr[0] - f);
        return fAbs > 10.0f && fAbs < 350.0f;
    }

    public static int selectForegroundColor(int i, Palette palette) {
        if (ContrastColorUtil.isColorLight(i)) {
            return selectForegroundColorForSwatches(palette.getDarkVibrantSwatch(), palette.getVibrantSwatch(), palette.getDarkMutedSwatch(), palette.getMutedSwatch(), palette.getDominantSwatch(), -16777216);
        }
        return selectForegroundColorForSwatches(palette.getLightVibrantSwatch(), palette.getVibrantSwatch(), palette.getLightMutedSwatch(), palette.getMutedSwatch(), palette.getDominantSwatch(), -1);
    }

    private static int selectForegroundColorForSwatches(Palette.Swatch swatch, Palette.Swatch swatch2, Palette.Swatch swatch3, Palette.Swatch swatch4, Palette.Swatch swatch5, int i) {
        Palette.Swatch swatchSelectVibrantCandidate = selectVibrantCandidate(swatch, swatch2);
        if (swatchSelectVibrantCandidate == null) {
            swatchSelectVibrantCandidate = selectMutedCandidate(swatch4, swatch3);
        }
        if (swatchSelectVibrantCandidate == null) {
            return hasEnoughPopulation(swatch5) ? swatch5.getRgb() : i;
        }
        if (swatch5 == swatchSelectVibrantCandidate) {
            return swatchSelectVibrantCandidate.getRgb();
        }
        if (swatchSelectVibrantCandidate.getPopulation() / swatch5.getPopulation() < POPULATION_FRACTION_FOR_DOMINANT && swatch5.getHsl()[1] > MIN_SATURATION_WHEN_DECIDING) {
            return swatch5.getRgb();
        }
        return swatchSelectVibrantCandidate.getRgb();
    }

    private static Palette.Swatch selectMutedCandidate(Palette.Swatch swatch, Palette.Swatch swatch2) {
        boolean zHasEnoughPopulation = hasEnoughPopulation(swatch);
        boolean zHasEnoughPopulation2 = hasEnoughPopulation(swatch2);
        if (zHasEnoughPopulation && zHasEnoughPopulation2) {
            return swatch.getHsl()[1] * (((float) swatch.getPopulation()) / ((float) swatch2.getPopulation())) > swatch2.getHsl()[1] ? swatch : swatch2;
        }
        if (zHasEnoughPopulation) {
            return swatch;
        }
        if (zHasEnoughPopulation2) {
            return swatch2;
        }
        return null;
    }

    private static Palette.Swatch selectVibrantCandidate(Palette.Swatch swatch, Palette.Swatch swatch2) {
        boolean zHasEnoughPopulation = hasEnoughPopulation(swatch);
        boolean zHasEnoughPopulation2 = hasEnoughPopulation(swatch2);
        if (zHasEnoughPopulation && zHasEnoughPopulation2) {
            return ((float) swatch.getPopulation()) / ((float) swatch2.getPopulation()) < 1.0f ? swatch2 : swatch;
        }
        if (zHasEnoughPopulation) {
            return swatch;
        }
        if (zHasEnoughPopulation2) {
            return swatch2;
        }
        return null;
    }

    private static boolean hasEnoughPopulation(Palette.Swatch swatch) {
        return swatch != null && ((double) (((float) swatch.getPopulation()) / 22500.0f)) > MINIMUM_IMAGE_FRACTION;
    }

    public static Palette.Swatch findBackgroundSwatch(Bitmap bitmap) {
        return findBackgroundSwatch(generateArtworkPaletteBuilder(bitmap).generate());
    }

    public static Palette.Swatch findBackgroundSwatch(Palette palette) {
        Palette.Swatch dominantSwatch = palette.getDominantSwatch();
        if (dominantSwatch == null) {
            return new Palette.Swatch(-1, 100);
        }
        if (!isWhiteOrBlack(dominantSwatch.getHsl())) {
            return dominantSwatch;
        }
        float population = -1.0f;
        Palette.Swatch swatch = null;
        for (Palette.Swatch swatch2 : palette.getSwatches()) {
            if (swatch2 != dominantSwatch && swatch2.getPopulation() > population && !isWhiteOrBlack(swatch2.getHsl())) {
                population = swatch2.getPopulation();
                swatch = swatch2;
            }
        }
        return (swatch != null && ((float) dominantSwatch.getPopulation()) / population <= POPULATION_FRACTION_FOR_WHITE_OR_BLACK) ? swatch : dominantSwatch;
    }

    public static Palette.Builder generateArtworkPaletteBuilder(Bitmap bitmap) {
        return Palette.from(bitmap).setRegion(0, 0, bitmap.getWidth() / 2, bitmap.getHeight()).clearFilters().resizeBitmapArea(RESIZE_BITMAP_AREA);
    }

    private static boolean isWhiteOrBlack(float[] fArr) {
        return isBlack(fArr) || isWhite(fArr);
    }

    private static boolean isBlack(float[] fArr) {
        return fArr[2] <= BLACK_MAX_LIGHTNESS;
    }

    private static boolean isWhite(float[] fArr) {
        return fArr[2] >= 0.9f;
    }
}
