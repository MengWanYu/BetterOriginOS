import io

p = r"C:/Users/Administrator/Desktop/iqooz8-os5-mod/module/src/com/iqoo/os5/mediaprobe/MainHook.java"
s = open(p, encoding="utf-8").read()


def rep(old, new):
    global s
    assert old in s, "MISSING: " + old[:90]
    s = s.replace(old, new, 1)


# import MediaDescription
rep("import android.media.MediaMetadata;",
    "import android.media.MediaDescription;\nimport android.media.MediaMetadata;")

# fields
rep("        final ImageButton playBtn;",
    "        final ImageButton playBtn;\n        final ImageView sourceView;")
rep("        int lastArtHash;",
    "        int lastArtHash;\n        String lastTrackKey = \"\";")

# constructor signature + body
rep("""        MediaCard(View root, LinearLayout card, ImageView art, SeekBar bar, TextView elapsed,
                  TextView total, TextView title, TextView artist, ImageButton playBtn) {""",
    """        MediaCard(View root, LinearLayout card, ImageView art, SeekBar bar, TextView elapsed,
                  TextView total, TextView title, TextView artist, ImageButton playBtn,
                  ImageView sourceView) {""")
rep("            this.playBtn = playBtn;",
    "            this.playBtn = playBtn;\n            this.sourceView = sourceView;")

# build source slot: always create a view, use header icon as initial, app icon later
rep("""            FrameLayout sourceSlot = new FrameLayout(ctx);
            LinearLayout.LayoutParams sl = new LinearLayout.LayoutParams(0, dp(ctx, 44), 1f);
            controls.addView(sourceSlot, sl);
            if (sourceIcon != null && sourceIcon.getDrawable() != null) {
                ImageView sourceCopy = new ImageView(ctx);
                sourceCopy.setImageDrawable(sourceIcon.getDrawable());
                sourceCopy.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
                FrameLayout.LayoutParams flp = new FrameLayout.LayoutParams(
                        dp(ctx, 30), dp(ctx, 30), Gravity.START | Gravity.CENTER_VERTICAL);
                sourceSlot.addView(sourceCopy, flp);
            }""",
    """            FrameLayout sourceSlot = new FrameLayout(ctx);
            LinearLayout.LayoutParams sl = new LinearLayout.LayoutParams(0, dp(ctx, 44), 1f);
            controls.addView(sourceSlot, sl);
            ImageView sourceView = new ImageView(ctx);
            sourceView.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
            GradientDrawable ph = new GradientDrawable();
            ph.setShape(GradientDrawable.OVAL);
            ph.setColor(night ? 0x1FFFFFFF : 0x14000000);
            sourceView.setBackground(ph);
            sourceView.setClipToOutline(true);
            if (sourceIcon != null && sourceIcon.getDrawable() != null) {
                sourceView.setImageDrawable(sourceIcon.getDrawable());
            }
            FrameLayout.LayoutParams flp = new FrameLayout.LayoutParams(
                    dp(ctx, 30), dp(ctx, 30), Gravity.START | Gravity.CENTER_VERTICAL);
            sourceSlot.addView(sourceView, flp);""")

# constructor call
rep("            MediaCard mc = new MediaCard(root, card, art, bar, elapsed, total, title, artist, play);",
    "            MediaCard mc = new MediaCard(root, card, art, bar, elapsed, total, title, artist, play, sourceView);")

# bind session -> set app icon
rep("""                controller = chosen;
                chosen.registerCallback(callback, handler);""",
    """                controller = chosen;
                updateSourceIcon();
                chosen.registerCallback(callback, handler);""")

# replace artHash(Bitmap) with content hash
rep("""        private static int artHash(Bitmap b) {
            if (b == null || b.isRecycled()) {
                return 0;
            }
            return b.getGenerationId() * 31 + b.getWidth() * 17 + b.getHeight();
        }""",
    """        private static int artHash(Bitmap b) {
            if (b == null || b.isRecycled()) {
                return 0;
            }
            int h = b.getWidth() * 31 + b.getHeight();
            try {
                int w = b.getWidth();
                int hh = b.getHeight();
                for (int y = 0; y < 6; y++) {
                    for (int x = 0; x < 6; x++) {
                        int px = b.getPixel(Math.min(w - 1, (x * 2 + 1) * w / 12),
                                Math.min(hh - 1, (y * 2 + 1) * hh / 12));
                        h = h * 31 + px;
                    }
                }
            } catch (Throwable t) {
                h = h * 31 + b.getGenerationId();
            }
            return h;
        }""")

# transparency
rep("""                int top = night ? 0x99000000 : 0xC8FFFFFF;
                int bottom = night ? 0xE6000000 : 0xF0FFFFFF;""",
    """                int top = night ? 0x8C000000 : 0xB8FFFFFF;
                int bottom = night ? 0xCC000000 : 0xE6FFFFFF;
                paint.setAlpha(night ? 235 : 240);""")

# replace refreshColors with track-key de-dupe
rep("""        void refreshColors(boolean animate) {
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
        }""",
    """        static String trackKey(MediaMetadata md) {
            if (md == null) {
                return "";
            }
            MediaDescription d = md.getDescription();
            if (d == null) {
                return "";
            }
            return str(d.getTitle()) + "|" + str(d.getSubtitle()) + "|"
                    + md.getLong(MediaMetadata.METADATA_KEY_DURATION);
        }

        void updateSourceIcon() {
            if (controller == null) {
                return;
            }
            try {
                Drawable d = root.getContext().getPackageManager()
                        .getApplicationIcon(controller.getPackageName());
                if (d != null) {
                    sourceView.setImageDrawable(d);
                }
            } catch (Throwable t) {
                Log.w(TAG, "app icon failed", t);
            }
        }

        void refreshColors(boolean animate) {
            try {
                MediaMetadata md = controller != null ? controller.getMetadata() : null;
                String key = trackKey(md);
                if (!key.isEmpty() && key.equals(lastTrackKey)) {
                    return;
                }
                Bitmap src = metadataArt(md);
                if (src == null) {
                    src = viewArtBitmap(art);
                }
                if (src == null && curBg != null) {
                    lastTrackKey = key;
                    return;
                }
                Log.i(TAG, "track changed, retinting: " + key);
                applyPalette(animate, src);
                lastTrackKey = key;
            } catch (Throwable t) {
                Log.w(TAG, "refreshColors failed", t);
            }
        }""")

open(p, "w", encoding="utf-8").write(s)
print("patched v0.7")
