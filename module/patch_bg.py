import io

p = r"C:/Users/Administrator/Desktop/iqooz8-os5-mod/module/src/com/iqoo/os5/mediaprobe/MainHook.java"
s = open(p, encoding="utf-8").read()


def rep(old, new):
    global s
    assert old in s, "MISSING: " + old[:80]
    s = s.replace(old, new, 1)


# imports
rep("import android.content.Context;",
    "import android.content.Context;\n"
    "import android.graphics.Canvas;\n"
    "import android.graphics.ColorFilter;\n"
    "import android.graphics.LinearGradient;\n"
    "import android.graphics.Paint;\n"
    "import android.graphics.Path;\n"
    "import android.graphics.PixelFormat;\n"
    "import android.graphics.Rect;\n"
    "import android.graphics.RectF;\n"
    "import android.graphics.Shader;\n"
    "import android.graphics.drawable.TransitionDrawable;")

# field
rep("        int lastArtHash;",
    "        int lastArtHash;\n        Drawable curBg;")

# ticker faster + color refresh
rep("""                updateProgress();
                if (tickerRunning) {
                    handler.postDelayed(this, 500);
                }""",
    """                updateProgress();
                refreshColors(true);
                if (tickerRunning) {
                    handler.postDelayed(this, 300);
                }""")

rep("""            public void onMetadataChanged(MediaMetadata metadata) {
                refreshColors();
                updateProgress();
            }""",
    """            public void onMetadataChanged(MediaMetadata metadata) {
                refreshColors(true);
                updateProgress();
            }""")

rep("                existing.refreshColors();",
    "                existing.refreshColors(true);")

rep("            mc.applyPalette();",
    "            mc.applyPalette(false, null);")

# replace palette..refreshColors block
start = s.index("        static int[] palette(Context ctx, ImageView art, boolean night) {")
end = s.index("        private static Drawable track", start)

block = '''        static Bitmap metadataArt(MediaMetadata md) {
            if (md == null) {
                return null;
            }
            Bitmap b = md.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART);
            if (b == null) {
                b = md.getBitmap(MediaMetadata.METADATA_KEY_ART);
            }
            if (b == null) {
                b = md.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON);
            }
            return b;
        }

        static Bitmap viewArtBitmap(ImageView art) {
            if (art == null) {
                return null;
            }
            Drawable d = art.getDrawable();
            if (!(d instanceof BitmapDrawable)) {
                return null;
            }
            return ((BitmapDrawable) d).getBitmap();
        }

        private static int artHash(Bitmap b) {
            if (b == null || b.isRecycled()) {
                return 0;
            }
            return b.getGenerationId() * 31 + b.getWidth() * 17 + b.getHeight();
        }

        private static int artHash(ImageView art) {
            return artHash(viewArtBitmap(art));
        }

        static Bitmap softBitmap(Bitmap src) {
            if (src == null || src.isRecycled() || src.getWidth() < 2 || src.getHeight() < 2) {
                return null;
            }
            Bitmap small = null;
            try {
                int size = 20;
                small = Bitmap.createScaledBitmap(src, size, size, true);
                int[] px = new int[size * size];
                small.getPixels(px, 0, size, 0, 0, size, size);
                int[] out = new int[px.length];
                for (int y = 0; y < size; y++) {
                    for (int x = 0; x < size; x++) {
                        int a = 0, r = 0, g = 0, b = 0, n = 0;
                        for (int dy = -1; dy <= 1; dy++) {
                            for (int dx = -1; dx <= 1; dx++) {
                                int yy = y + dy, xx = x + dx;
                                if (yy < 0 || yy >= size || xx < 0 || xx >= size) {
                                    continue;
                                }
                                int c = px[yy * size + xx];
                                a += Color.alpha(c);
                                r += Color.red(c);
                                g += Color.green(c);
                                b += Color.blue(c);
                                n++;
                            }
                        }
                        out[y * size + x] = Color.argb(a / n, r / n, g / n, b / n);
                    }
                }
                return Bitmap.createBitmap(out, size, size, Bitmap.Config.ARGB_8888);
            } catch (Throwable t) {
                Log.w(TAG, "softBitmap failed", t);
                return null;
            } finally {
                if (small != null && small != src) {
                    try {
                        small.recycle();
                    } catch (Throwable ignored) {
                    }
                }
            }
        }

        static final class ArtBgDrawable extends Drawable {
            final Bitmap bmp;
            final int corner;
            final boolean night;
            final Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG);
            final Paint veil = new Paint(Paint.ANTI_ALIAS_FLAG);
            final Path path = new Path();
            Shader shader;

            ArtBgDrawable(Bitmap bmp, int corner, boolean night) {
                this.bmp = bmp;
                this.corner = corner;
                this.night = night;
            }

            @Override
            protected void onBoundsChange(Rect bounds) {
                int top = night ? 0x99000000 : 0xC8FFFFFF;
                int bottom = night ? 0xE6000000 : 0xF0FFFFFF;
                shader = new LinearGradient(0, bounds.top, 0, bounds.bottom,
                        top, bottom, Shader.TileMode.CLAMP);
                veil.setShader(shader);
            }

            @Override
            public void draw(Canvas canvas) {
                if (bmp == null || bmp.isRecycled()) {
                    return;
                }
                Rect b = getBounds();
                int save = canvas.save();
                path.reset();
                float c = Math.min(corner, Math.min(b.width(), b.height()) / 2f);
                path.addRoundRect(new RectF(b), c, c, Path.Direction.CW);
                canvas.clipPath(path);
                canvas.drawBitmap(bmp, null, b, paint);
                canvas.drawRect(b, veil);
                canvas.restoreToCount(save);
            }

            @Override
            public void setAlpha(int alpha) {
                paint.setAlpha(alpha);
                veil.setAlpha(alpha);
            }

            @Override
            public void setColorFilter(ColorFilter cf) {
                paint.setColorFilter(cf);
            }

            @Override
            public int getOpacity() {
                return PixelFormat.TRANSLUCENT;
            }
        }

        void applyPalette(boolean animate, Bitmap bmp) {
            try {
                Context ctx = card.getContext();
                boolean night = isNight(ctx);
                Bitmap src = bmp;
                if (src == null && controller != null) {
                    src = metadataArt(controller.getMetadata());
                }
                if (src == null) {
                    src = viewArtBitmap(art);
                }
                int h = artHash(src);
                if (h != 0 && h == lastArtHash && curBg != null) {
                    return;
                }
                Bitmap soft = softBitmap(src);
                Drawable newBg;
                if (soft != null) {
                    newBg = new ArtBgDrawable(soft, dp(ctx, 24), night);
                } else {
                    GradientDrawable g = new GradientDrawable(
                            GradientDrawable.Orientation.TOP_BOTTOM,
                            night ? new int[]{0xFF2A211C, 0xFF211915}
                                    : new int[]{0xFFFDF6EC, 0xFFF8EDE1});
                    g.setCornerRadius(dp(ctx, 24));
                    newBg = g;
                }
                if (animate && curBg != null && card.isAttachedToWindow()) {
                    TransitionDrawable td = new TransitionDrawable(new Drawable[]{curBg, newBg});
                    td.setCrossFadeEnabled(true);
                    card.setBackground(td);
                    td.startTransition(320);
                } else {
                    card.setBackground(newBg);
                }
                curBg = newBg;
                int barFill = night ? 0xFFE6E6E6 : 0xFF4A4A4A;
                int trackColor = night ? 0x33FFFFFF : 0x1F000000;
                bar.setProgressDrawable(track(ctx, barFill, trackColor));
                bar.setThumb(thumb(ctx, barFill));
                if (playBtn != null) {
                    playBtn.setColorFilter(night ? 0xFFF07A64 : 0xFFE0503A);
                }
                lastArtHash = h;
            } catch (Throwable t) {
                Log.w(TAG, "applyPalette failed", t);
            }
        }

        void refreshColors(boolean animate) {
            try {
                Bitmap src = controller != null ? metadataArt(controller.getMetadata()) : null;
                int h = src != null ? artHash(src) : artHash(art);
                if (h != 0 && h != lastArtHash) {
                    Log.i(TAG, "album art changed, retinting");
                    lastArtHash = h;
                    applyPalette(animate, src);
                }
            } catch (Throwable t) {
                Log.w(TAG, "refreshColors failed", t);
            }
        }

'''
s = s[:start] + block + s[end:]

open(p, "w", encoding="utf-8").write(s)
print("patched blurred art background")
