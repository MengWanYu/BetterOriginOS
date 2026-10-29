import io

p = r"C:/Users/Administrator/Desktop/iqooz8-os5-mod/module/src/com/iqoo/os5/mediaprobe/MainHook.java"
s = open(p, encoding="utf-8").read()


def rep(old, new):
    global s
    assert old in s, "MISSING: " + old[:90]
    s = s.replace(old, new, 1)


# fields
rep("        final SeekBar bar;",
    "        final SeekBar bar;\n        final SeekDrawable seekDrawable;\n        long pendingSeekMs = -1;\n        long suppressUntil;")

# constructor signature/body
rep("""        MediaCard(View root, LinearLayout card, ImageView art, ImageView artView, SeekBar bar,
                  TextView elapsed, TextView total, TextView title, TextView artist,
                  ImageButton playBtn, ImageView sourceView) {""",
    """        MediaCard(View root, LinearLayout card, ImageView art, ImageView artView, SeekBar bar,
                  SeekDrawable seekDrawable, TextView elapsed, TextView total, TextView title,
                  TextView artist, ImageButton playBtn, ImageView sourceView) {""")
rep("            this.bar = bar;",
    "            this.bar = bar;\n            this.seekDrawable = seekDrawable;")

# create custom seek drawable in build
rep("""            SeekBar bar = new SeekBar(ctx);
            bar.setMax(1);
            bar.setProgress(0);
            bar.setSplitTrack(false);
            bar.setPadding(0, dp(ctx, 6), 0, dp(ctx, 6));
            bar.setBackground(null);""",
    """            SeekBar bar = new SeekBar(ctx);
            bar.setMax(1);
            bar.setProgress(0);
            bar.setSplitTrack(false);
            bar.setPadding(0, dp(ctx, 6), 0, dp(ctx, 6));
            bar.setBackground(null);
            SeekDrawable seekDrawable = new SeekDrawable(
                    night ? 0x33FFFFFF : 0x1F000000,
                    night ? 0xFFE6E6E6 : 0xFF4A4A4A,
                    ctx.getResources().getDisplayMetrics().density);
            bar.setProgressDrawable(seekDrawable);""")

# constructor call
rep("            MediaCard mc = new MediaCard(root, card, art, artView, bar, elapsed, total,\n                    title, artist, play, sourceView);",
    "            MediaCard mc = new MediaCard(root, card, art, artView, bar, seekDrawable, elapsed,\n                    total, title, artist, play, sourceView);")

# clear ancestor backgrounds (white corners)
rep("            hideOld(ctx, root);",
    "            hideOld(ctx, root);\n            clearAncestorBackgrounds(root);")

# applyPalette: use custom drawable colors
rep("""                int barFill = night ? 0xFFE6E6E6 : 0xFF4A4A4A;
                int trackColor = night ? 0x33FFFFFF : 0x1F000000;
                bar.setProgressDrawable(track(ctx, barFill, trackColor));
                bar.setThumb(thumb(ctx, barFill));""",
    """                int barFill = night ? 0xFFE6E6E6 : 0xFF4A4A4A;
                int trackColor = night ? 0x33FFFFFF : 0x1F000000;
                seekDrawable.setColors(trackColor, barFill);
                bar.setThumb(thumb(ctx, barFill));""")

# seek release -> hold position while session catches up
rep("""                public void onStopTrackingTouch(SeekBar seekBar) {
                    mc.dragging = false;
                    if (mc.controller != null) {
                        try {
                            mc.controller.getTransportControls().seekTo(seekBar.getProgress());
                        } catch (Throwable t) {
                            Log.w(TAG, "seek failed", t);
                        }
                    }
                }""",
    """                public void onStopTrackingTouch(SeekBar seekBar) {
                    mc.dragging = false;
                    long target = seekBar.getProgress();
                    mc.pendingSeekMs = target;
                    mc.suppressUntil = SystemClock.elapsedRealtime() + 2000;
                    if (mc.controller != null) {
                        try {
                            mc.controller.getTransportControls().seekTo(target);
                        } catch (Throwable t) {
                            Log.w(TAG, "seek failed", t);
                        }
                    }
                }""")

# updateProgress: respect pending seek
rep("""        void updateProgress() {
            if (controller == null) {
                return;
            }
            try {
                MediaMetadata md = controller.getMetadata();""",
    """        void updateProgress() {
            if (controller == null) {
                return;
            }
            try {
                long now = SystemClock.elapsedRealtime();
                if (pendingSeekMs >= 0 && now < suppressUntil) {
                    if (!dragging) {
                        bar.setProgress((int) pendingSeekMs);
                        elapsed.setText(fmt(pendingSeekMs));
                    }
                    return;
                }
                pendingSeekMs = -1;
                MediaMetadata md = controller.getMetadata();""")

# clear pending when the session reports a position near target
rep("""            public void onPlaybackStateChanged(PlaybackState state) {
                updateProgress();
            }""",
    """            public void onPlaybackStateChanged(PlaybackState state) {
                if (pendingSeekMs >= 0 && state != null
                        && Math.abs(state.getPosition() - pendingSeekMs) < 1500) {
                    pendingSeekMs = -1;
                }
                updateProgress();
            }""")

# helper + custom drawable
rep("""        private static void hideOld(Context ctx, View root) {""",
    """        private static void clearAncestorBackgrounds(View root) {
            try {
                ViewParent p = root.getParent();
                int level = 0;
                while (p instanceof View && level < 4) {
                    View v = (View) p;
                    Log.i(TAG, "ancestor[" + level + "] " + v.getClass().getName()
                            + " bg=" + v.getBackground());
                    if (v.getBackground() != null) {
                        v.setBackground(null);
                    }
                    p = v.getParent();
                    level++;
                }
            } catch (Throwable t) {
                Log.w(TAG, "clearAncestorBackgrounds failed", t);
            }
        }

        private static void hideOld(Context ctx, View root) {""")

# add SeekDrawable class before track()
rep("""        private static Drawable track(Context ctx, int accent, int trackColor) {""",
    """        static final class SeekDrawable extends Drawable {
            final Paint trackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            final float density;

            SeekDrawable(int trackColor, int fillColor, float density) {
                this.density = density;
                trackPaint.setColor(trackColor);
                fillPaint.setColor(fillColor);
            }

            void setColors(int trackColor, int fillColor) {
                trackPaint.setColor(trackColor);
                fillPaint.setColor(fillColor);
                invalidateSelf();
            }

            @Override
            public void draw(Canvas canvas) {
                Rect b = getBounds();
                if (b.width() <= 0 || b.height() <= 0) {
                    return;
                }
                float h = Math.min(density * 4f, b.height());
                float top = b.centerY() - h / 2f;
                float rad = h / 2f;
                RectF track = new RectF(b.left, top, b.right, top + h);
                canvas.drawRoundRect(track, rad, rad, trackPaint);
                float level = getLevel() / 10000f;
                if (level > 0f) {
                    RectF fill = new RectF(b.left, top,
                            b.left + b.width() * Math.min(1f, level), top + h);
                    canvas.drawRoundRect(fill, rad, rad, fillPaint);
                }
            }

            @Override
            protected boolean onLevelChange(int level) {
                invalidateSelf();
                return true;
            }

            @Override
            public void setAlpha(int alpha) {
                trackPaint.setAlpha(alpha);
                fillPaint.setAlpha(alpha);
            }

            @Override
            public void setColorFilter(ColorFilter cf) {
                trackPaint.setColorFilter(cf);
                fillPaint.setColorFilter(cf);
            }

            @Override
            public int getOpacity() {
                return PixelFormat.TRANSLUCENT;
            }
        }

        private static Drawable track(Context ctx, int accent, int trackColor) {""")

open(p, "w", encoding="utf-8").write(s)
print("patched v0.9 seek + corners")
