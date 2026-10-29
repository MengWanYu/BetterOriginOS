import re

p = r"C:/Users/Administrator/Desktop/iqooz8-os5-mod/module/src/com/iqoo/os5/mediaprobe/MainHook.java"
s = open(p, encoding="utf-8").read()


def rep(old, new):
    global s
    assert old in s, "MISSING: " + old[:100]
    s = s.replace(old, new, 1)


# import
rep("import de.robv.android.xposed.XposedHelpers;",
    "import de.robv.android.xposed.XSharedPreferences;\nimport de.robv.android.xposed.XposedHelpers;")

# style provider + restyle passes style
rep("""    private static void restyle(View root) {""",
    """    private static String selectedStyle() {
        try {
            XSharedPreferences p = new XSharedPreferences("com.iqoo.os5.mediaprobe", "settings");
            p.reload();
            String s = p.getString("style", "os5");
            return "material".equals(s) ? "material" : "os5";
        } catch (Throwable t) {
            Log.w(TAG, "read style failed", t);
            return "os5";
        }
    }

    private static void restyle(View root) {""")

rep("""        Log.i(TAG, "restyling " + root.getClass().getName()
                + " title=" + title.getText() + " actions=" + actions.getChildCount());
        MediaCard card = MediaCard.build(ctx, root, actions, title, artist, art, sourceIcon, headerId);""",
    """        String style = selectedStyle();
        Log.i(TAG, "restyling style=" + style + " " + root.getClass().getName()
                + " title=" + title.getText() + " actions=" + actions.getChildCount());
        MediaCard card = MediaCard.build(ctx, root, actions, title, artist, art, sourceIcon,
                headerId, style);""")

# fields/constructor/build
rep("        final ImageView sourceView;",
    "        final ImageView sourceView;\n        final String style;")

rep("""        MediaCard(View root, LinearLayout card, ImageView art, ImageView artView, SeekBar bar,
                  SeekDrawable seekDrawable, TextView elapsed, TextView total, TextView title,
                  TextView artist, ImageButton playBtn, ImageView sourceView) {""",
    """        MediaCard(View root, LinearLayout card, ImageView art, ImageView artView, SeekBar bar,
                  SeekDrawable seekDrawable, TextView elapsed, TextView total, TextView title,
                  TextView artist, ImageButton playBtn, ImageView sourceView, String style) {""")
rep("            this.sourceView = sourceView;",
    "            this.sourceView = sourceView;\n            this.style = style;")

rep("""        static MediaCard build(Context ctx, View root, ViewGroup actions, TextView title,
                               TextView artist, ImageView art, ImageView sourceIcon, int headerId) {
            ensureWidgetService(ctx);
            boolean night = isNight(ctx);
            int primary = night ? 0xFFFFFFFF : 0xFF241A16;
            int secondary = night ? 0xB3FFFFFF : 0x8C322319;
            int accent = night ? 0xFFF07A64 : 0xFFE0503A;""",
    """        static MediaCard build(Context ctx, View root, ViewGroup actions, TextView title,
                               TextView artist, ImageView art, ImageView sourceIcon, int headerId,
                               String style) {
            ensureWidgetService(ctx);
            boolean m3 = "material".equals(style);
            boolean night = isNight(ctx);
            int primary = night ? 0xFFFFFFFF : (m3 ? 0xFF2A1A15 : 0xFF241A16);
            int secondary = night ? 0xB3FFFFFF : (m3 ? 0x8C5A4038 : 0x8C322319);
            int accent = m3 ? 0xFFA93A27 : (night ? 0xFFF07A64 : 0xFFE0503A);""")

# initial card background
rep("""            GradientDrawable bg = new GradientDrawable(
                    GradientDrawable.Orientation.TOP_BOTTOM,
                    night ? new int[]{0xFF2A211C, 0xFF211915}
                            : new int[]{0xFFFDF6EC, 0xFFF8EDE1});
            bg.setCornerRadius(dp(ctx, 24));""",
    """            GradientDrawable bg = new GradientDrawable(
                    GradientDrawable.Orientation.TOP_BOTTOM,
                    m3
                            ? (night ? new int[]{0xFF2A211C, 0xFF211915}
                                     : new int[]{0xFFF7DED4, 0xFFFBEADE})
                            : (night ? new int[]{0xFF2A211C, 0xFF211915}
                                     : new int[]{0xFFFDF6EC, 0xFFF8EDE1}));
            bg.setCornerRadius(dp(ctx, m3 ? 28 : 24));""")

# art styling/size
rep("            artBg.setCornerRadius(dp(ctx, 8));",
    "            artBg.setCornerRadius(dp(ctx, m3 ? 16 : 8));")
rep("            top.addView(artView, new LinearLayout.LayoutParams(dp(ctx, 60), dp(ctx, 60)));",
    "            top.addView(artView, new LinearLayout.LayoutParams(\n"
    "                    dp(ctx, m3 ? 64 : 60), dp(ctx, m3 ? 64 : 60)));")

# title size
rep("            title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17);",
    "            title.setTextSize(TypedValue.COMPLEX_UNIT_SP, m3 ? 16 : 17);")

# constructor call
rep("""            MediaCard mc = new MediaCard(root, card, art, artView, bar, seekDrawable, elapsed,
                    total, title, artist, play, sourceView);""",
    """            MediaCard mc = new MediaCard(root, card, art, artView, bar, seekDrawable, elapsed,
                    total, title, artist, play, sourceView, style);""")

# dominant hue helpers
rep("        static Bitmap softBitmap(Bitmap src) {",
    """        static float dominantHue(Bitmap bmp) {
            if (bmp == null || bmp.isRecycled()) {
                return 20f;
            }
            Bitmap small = null;
            try {
                small = Bitmap.createScaledBitmap(bmp, 24, 24, false);
                int[] px = new int[24 * 24];
                small.getPixels(px, 0, 24, 0, 0, 24, 24);
                float[] score = new float[36];
                float[] hsv = new float[3];
                for (int c : px) {
                    if (Color.alpha(c) < 100) {
                        continue;
                    }
                    Color.colorToHSV(c, hsv);
                    if (hsv[1] < 0.18f || hsv[2] < 0.18f) {
                        continue;
                    }
                    int bucket = Math.min(35, (int) (hsv[0] / 10f));
                    score[bucket] += hsv[1] * hsv[2];
                }
                int best = -1;
                float bestScore = 0.6f;
                for (int i = 0; i < 36; i++) {
                    if (score[i] > bestScore) {
                        bestScore = score[i];
                        best = i;
                    }
                }
                return best < 0 ? 20f : best * 10f + 5f;
            } catch (Throwable t) {
                return 20f;
            } finally {
                if (small != null && small != bmp) {
                    try {
                        small.recycle();
                    } catch (Throwable ignored) {
                    }
                }
            }
        }

        static int hsv(float h, float s, float v) {
            return Color.HSVToColor(new float[]{h, s, v});
        }

        static Bitmap softBitmap(Bitmap src) {""")

# applyPalette style branch
old_palette = """                Bitmap soft = softBitmap(src);
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
                seekDrawable.setColors(trackColor, barFill);
                bar.setThumb(thumb(ctx, barFill));
                if (playBtn != null) {
                    playBtn.setColorFilter(night ? 0xFFF07A64 : 0xFFE0503A);
                }
                lastArtHash = h;"""

new_palette = """                boolean m3 = "material".equals(style);
                Drawable newBg;
                int accent;
                if (m3) {
                    float hue = dominantHue(src);
                    int bg1 = hsv(hue, night ? 0.18f : 0.22f, night ? 0.20f : 0.99f);
                    int bg2 = hsv(hue, night ? 0.24f : 0.30f, night ? 0.16f : 0.955f);
                    accent = hsv(hue, night ? 0.55f : 0.62f, night ? 0.82f : 0.55f);
                    GradientDrawable g = new GradientDrawable(
                            GradientDrawable.Orientation.TOP_BOTTOM, new int[]{bg1, bg2});
                    g.setCornerRadius(dp(ctx, 28));
                    newBg = g;
                } else {
                    Bitmap soft = softBitmap(src);
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
                    accent = night ? 0xFFF07A64 : 0xFFE0503A;
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
                if (m3) {
                    int barFill = accent;
                    int trackColor = Color.argb(night ? 0x33 : 0x2E,
                            Color.red(accent), Color.green(accent), Color.blue(accent));
                    seekDrawable.setColors(trackColor, barFill);
                    bar.setThumb(thumb(ctx, barFill));
                    if (playBtn != null) {
                        GradientDrawable circle = new GradientDrawable();
                        circle.setShape(GradientDrawable.OVAL);
                        circle.setColor(accent);
                        playBtn.setBackground(circle);
                        playBtn.setColorFilter(0xFFFFFFFF);
                        int pad = dp(ctx, 10);
                        playBtn.setPadding(pad, pad, pad, pad);
                    }
                    int primary = night ? 0xFFFFFFFF : 0xFF2A1A15;
                    int secondary = night ? 0xB3FFFFFF : 0x8C5A4038;
                    title.setTextColor(primary);
                    artist.setTextColor(secondary);
                    elapsed.setTextColor(secondary);
                    total.setTextColor(secondary);
                } else {
                    int barFill = night ? 0xFFE6E6E6 : 0xFF4A4A4A;
                    int trackColor = night ? 0x33FFFFFF : 0x1F000000;
                    seekDrawable.setColors(trackColor, barFill);
                    bar.setThumb(thumb(ctx, barFill));
                    if (playBtn != null) {
                        playBtn.setBackground(null);
                        playBtn.setColorFilter(night ? 0xFFF07A64 : 0xFFE0503A);
                        int pad = Math.round(dp(ctx, 44) * 0.16f);
                        playBtn.setPadding(pad, pad, pad, pad);
                    }
                    int primary = night ? 0xFFFFFFFF : 0xFF241A16;
                    int secondary = night ? 0xB3FFFFFF : 0x8C322319;
                    title.setTextColor(primary);
                    artist.setTextColor(secondary);
                    elapsed.setTextColor(secondary);
                    total.setTextColor(secondary);
                }
                lastArtHash = h;"""

assert old_palette in s
s = s.replace(old_palette, new_palette, 1)

open(p, "w", encoding="utf-8").write(s)
print("patched multistyle")
