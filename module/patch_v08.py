import io

p = r"C:/Users/Administrator/Desktop/iqooz8-os5-mod/module/src/com/iqoo/os5/mediaprobe/MainHook.java"
s = open(p, encoding="utf-8").read()


def rep(old, new):
    global s
    assert old in s, "MISSING: " + old[:90]
    s = s.replace(old, new, 1)


# fields
rep("        final ImageView art;",
    "        final ImageView art;\n        final ImageView artView;")
rep("        String lastTrackKey = \"\";",
    "        String lastTrackKey = \"\";\n        int lastDrawableHash;")

# constructor signature/body
rep("""        MediaCard(View root, LinearLayout card, ImageView art, SeekBar bar, TextView elapsed,
                  TextView total, TextView title, TextView artist, ImageButton playBtn,
                  ImageView sourceView) {""",
    """        MediaCard(View root, LinearLayout card, ImageView art, ImageView artView, SeekBar bar,
                  TextView elapsed, TextView total, TextView title, TextView artist,
                  ImageButton playBtn, ImageView sourceView) {""")
rep("            this.art = art;",
    "            this.art = art;\n            this.artView = artView;")

# build: replace reparented original art with our own fixed-size copy
rep("""            if (art != null && art.getDrawable() != null) {
                reparent(art);
                art.setLayoutParams(new LinearLayout.LayoutParams(dp(ctx, 60), dp(ctx, 60)));
                art.setScaleType(ImageView.ScaleType.CENTER_CROP);
                GradientDrawable artBg = new GradientDrawable();
                artBg.setColor(night ? 0x33FFFFFF : 0x14000000);
                artBg.setCornerRadius(dp(ctx, 8));
                art.setBackground(artBg);
                art.setClipToOutline(true);
                art.setElevation(dp(ctx, 2));
                top.addView(art);
            } else if (art != null) {
                art.setVisibility(View.GONE);
            }""",
    """            ImageView artView = new ImageView(ctx);
            artView.setScaleType(ImageView.ScaleType.CENTER_CROP);
            GradientDrawable artBg = new GradientDrawable();
            artBg.setColor(night ? 0x33FFFFFF : 0x14000000);
            artBg.setCornerRadius(dp(ctx, 8));
            artView.setBackground(artBg);
            artView.setClipToOutline(true);
            artView.setElevation(dp(ctx, 2));
            if (art != null) {
                if (art.getDrawable() != null) {
                    artView.setImageDrawable(art.getDrawable());
                }
                art.setVisibility(View.GONE);
            }
            top.addView(artView, new LinearLayout.LayoutParams(dp(ctx, 60), dp(ctx, 60)));""")

# constructor call
rep("            MediaCard mc = new MediaCard(root, card, art, bar, elapsed, total, title, artist, play, sourceView);",
    "            MediaCard mc = new MediaCard(root, card, art, artView, bar, elapsed, total,\n                    title, artist, play, sourceView);")

# initial art sync
rep("            mc.applyPalette(false, null);",
    "            mc.applyPalette(false, null);\n            mc.syncArt();")

# ticker + metadata
rep("""                updateProgress();
                refreshColors(true);""",
    """                updateProgress();
                syncArt();
                refreshColors(true);""")
rep("""            public void onMetadataChanged(MediaMetadata metadata) {
                refreshColors(true);""",
    """            public void onMetadataChanged(MediaMetadata metadata) {
                syncArt();
                refreshColors(true);""")

# bind session: sync art after session known
rep("""                updateSourceIcon();
                chosen.registerCallback(callback, handler);""",
    """                updateSourceIcon();
                syncArt();
                chosen.registerCallback(callback, handler);""")

# add syncArt before updateSourceIcon
rep("""        void updateSourceIcon() {""",
    """        void syncArt() {
            try {
                if (art != null) {
                    art.setVisibility(View.GONE);
                }
                Bitmap meta = controller != null ? metadataArt(controller.getMetadata()) : null;
                int h;
                Drawable d;
                if (meta != null) {
                    h = artHash(meta);
                    d = null;
                } else if (art != null) {
                    d = art.getDrawable();
                    h = artHash(viewArtBitmap(art));
                } else {
                    return;
                }
                if (h == 0 || h == lastDrawableHash) {
                    return;
                }
                if (meta != null) {
                    d = new BitmapDrawable(root.getResources(), meta);
                }
                if (d != null) {
                    artView.setImageDrawable(d);
                    lastDrawableHash = h;
                }
            } catch (Throwable t) {
                Log.w(TAG, "syncArt failed", t);
            }
        }

        void updateSourceIcon() {""")

open(p, "w", encoding="utf-8").write(s)
print("patched v0.8 art copy")
