package com.betteroriginos.vivouidemo.ui;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;

public final class VivoIcons {

    private VivoIcons() {
    }

    public static Drawable of(String name, int color) {
        return new IconDrawable(name, color);
    }

    public static Drawable of(String name, int color, int sizeDp) {
        return new IconDrawable(name, color, sizeDp);
    }

    public static class IconDrawable extends Drawable {
        private final String name;
        private final int color;
        private final int sizePx;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path path = new Path();
        private final RectF rect = new RectF();

        public IconDrawable(String name, int color) {
            this(name, color, 24);
        }

        public IconDrawable(String name, int color, int sizeDp) {
            this.name = name;
            this.color = color;
            this.sizePx = sizeDp;
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setStrokeJoin(Paint.Join.ROUND);
        }

        @Override
        public void draw(Canvas canvas) {
            Rect b = getBounds();
            float scale = Math.min(b.width(), b.height()) / 24f;
            canvas.save();
            canvas.translate(b.left + (b.width() - 24 * scale) / 2f,
                    b.top + (b.height() - 24 * scale) / 2f);
            canvas.scale(scale, scale);
            drawIcon(canvas, name, color, paint, path, rect);
            canvas.restore();
        }

        @Override
        public void setAlpha(int alpha) {
            paint.setAlpha(alpha);
            invalidateSelf();
        }

        @Override
        public void setColorFilter(ColorFilter colorFilter) {
            paint.setColorFilter(colorFilter);
            invalidateSelf();
        }

        @Override
        public int getOpacity() {
            return PixelFormat.TRANSLUCENT;
        }

        @Override
        public int getIntrinsicWidth() {
            return sizePx;
        }

        @Override
        public int getIntrinsicHeight() {
            return sizePx;
        }
    }

    private static void stroke(Paint p, float width, int color) {
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(width);
        p.setColor(color);
    }

    private static void fill(Paint p, int color) {
        p.setStyle(Paint.Style.FILL);
        p.setColor(color);
    }

    private static void circle(Canvas c, Paint p, float cx, float cy, float r) {
        c.drawCircle(cx, cy, r, p);
    }

    private static void line(Canvas c, Paint p, float x1, float y1, float x2, float y2) {
        c.drawLine(x1, y1, x2, y2, p);
    }

    private static void roundRect(Canvas c, Paint p, float l, float t, float r, float b, float rad) {
        RectF rf = new RectF(l, t, r, b);
        c.drawRoundRect(rf, rad, rad, p);
    }

    private static void check(Canvas c, Paint p, float left, float top, float scale) {
        Path path = new Path();
        path.moveTo(left, top + 3.4f * scale);
        path.lineTo(left + 3.2f * scale, top + 6.6f * scale);
        path.lineTo(left + 9.0f * scale, top);
        c.drawPath(path, p);
    }

    private static void drawIcon(Canvas c, String name, int color, Paint p, Path path, RectF rect) {
        float s = 1.9f;
        switch (name == null ? "" : name) {
            case "search":
                stroke(p, s + 0.1f, color);
                circle(c, p, 10.6f, 10.6f, 6.2f);
                line(c, p, 15.2f, 15.2f, 20.2f, 20.2f);
                break;
            case "plus":
                stroke(p, 2.0f, color);
                line(c, p, 12, 4.2f, 12, 19.8f);
                line(c, p, 4.2f, 12, 19.8f, 12);
                break;
            case "minus":
                stroke(p, 2.0f, color);
                line(c, p, 4.2f, 12, 19.8f, 12);
                break;
            case "close":
                stroke(p, 2.0f, color);
                line(c, p, 5.5f, 5.5f, 18.5f, 18.5f);
                line(c, p, 18.5f, 5.5f, 5.5f, 18.5f);
                break;
            case "check":
                stroke(p, 2.2f, color);
                path.reset();
                path.moveTo(4.5f, 12.4f);
                path.lineTo(9.5f, 17.2f);
                path.lineTo(19.6f, 6.8f);
                c.drawPath(path, p);
                break;
            case "back":
                stroke(p, 2.1f, color);
                path.reset();
                path.moveTo(14.8f, 4.6f);
                path.lineTo(7.4f, 12f);
                path.lineTo(14.8f, 19.4f);
                c.drawPath(path, p);
                break;
            case "chevron":
                stroke(p, 1.9f, color);
                path.reset();
                path.moveTo(9.4f, 5.2f);
                path.lineTo(16.2f, 12f);
                path.lineTo(9.4f, 18.8f);
                c.drawPath(path, p);
                break;
            case "chevron_down":
                stroke(p, 1.9f, color);
                path.reset();
                path.moveTo(5.2f, 9.4f);
                path.lineTo(12f, 16.2f);
                path.lineTo(18.8f, 9.4f);
                c.drawPath(path, p);
                break;
            case "more":
                fill(p, color);
                circle(c, p, 5.5f, 12, 1.7f);
                circle(c, p, 12, 12, 1.7f);
                circle(c, p, 18.5f, 12, 1.7f);
                break;
            case "wifi":
                stroke(p, 1.9f, color);
                path.reset();
                path.addArc(new RectF(2.6f, 4.2f, 21.4f, 23.0f), 218, 104);
                c.drawPath(path, p);
                path.reset();
                path.addArc(new RectF(6.4f, 8.0f, 17.6f, 19.2f), 218, 104);
                c.drawPath(path, p);
                path.reset();
                path.addArc(new RectF(9.9f, 11.5f, 14.1f, 15.7f), 218, 104);
                c.drawPath(path, p);
                fill(p, color);
                circle(c, p, 12, 18.6f, 1.55f);
                break;
            case "bluetooth":
                stroke(p, 1.9f, color);
                path.reset();
                path.moveTo(8.0f, 7.2f);
                path.lineTo(16.0f, 12.0f);
                path.lineTo(12.0f, 15.1f);
                path.lineTo(16.0f, 18.0f);
                path.lineTo(8.0f, 22.0f);
                path.moveTo(12.0f, 3.0f);
                path.lineTo(12.0f, 15.1f);
                path.moveTo(12.0f, 8.2f);
                path.lineTo(16.0f, 12.0f);
                path.moveTo(12.0f, 15.1f);
                path.lineTo(8.0f, 18.9f);
                c.drawPath(path, p);
                break;
            case "cellular":
                fill(p, VivoTheme.alpha(color, 0.35f));
                roundRect(c, p, 3.4f, 16.3f, 6.4f, 20.4f, 1.5f);
                fill(p, VivoTheme.alpha(color, 0.55f));
                roundRect(c, p, 8.0f, 12.6f, 11.0f, 20.4f, 1.5f);
                fill(p, VivoTheme.alpha(color, 0.78f));
                roundRect(c, p, 12.6f, 9.0f, 15.6f, 20.4f, 1.5f);
                fill(p, color);
                roundRect(c, p, 17.2f, 4.6f, 20.2f, 20.4f, 1.5f);
                break;
            case "hotspot":
                stroke(p, 1.9f, color);
                circle(c, p, 12, 17.4f, 2.0f);
                path.reset();
                path.addArc(new RectF(6.8f, 8.2f, 17.2f, 20.4f), 200, 140);
                c.drawPath(path, p);
                path.reset();
                path.addArc(new RectF(3.4f, 4.6f, 20.6f, 24.0f), 202, 136);
                c.drawPath(path, p);
                break;
            case "link":
                c.save();
                c.rotate(-42, 12, 12);
                stroke(p, 2.0f, color);
                roundRect(c, p, 2.5f, 8.0f, 14.0f, 15.8f, 3.9f);
                roundRect(c, p, 10.0f, 8.2f, 21.5f, 16.0f, 3.9f);
                c.restore();
                break;
            case "notification":
                stroke(p, 1.8f, color);
                roundRect(c, p, 3.4f, 4.6f, 20.6f, 18.4f, 4.0f);
                fill(p, color);
                circle(c, p, 17.2f, 6.4f, 2.1f);
                stroke(p, 1.7f, color);
                line(c, p, 7.2f, 9.2f, 15.4f, 9.2f);
                line(c, p, 7.2f, 13.0f, 13.0f, 13.0f);
                break;
            case "sound":
                fill(p, color);
                path.reset();
                path.moveTo(3.6f, 9.4f);
                path.lineTo(7.1f, 9.4f);
                path.lineTo(11.4f, 5.4f);
                path.lineTo(11.4f, 18.6f);
                path.lineTo(7.1f, 14.6f);
                path.lineTo(3.6f, 14.6f);
                path.close();
                c.drawPath(path, p);
                stroke(p, 1.8f, color);
                path.reset();
                path.addArc(new RectF(9.8f, 7.0f, 17.8f, 17.0f), -58, 116);
                c.drawPath(path, p);
                path.reset();
                path.addArc(new RectF(10.6f, 3.8f, 22.0f, 20.2f), -54, 108);
                c.drawPath(path, p);
                break;
            case "brightness":
                stroke(p, 1.8f, color);
                circle(c, p, 12, 12, 4.4f);
                for (int i = 0; i < 8; i++) {
                    double a = Math.PI * i / 4.0;
                    line(c, p, (float) (12 + Math.cos(a) * 7.4), (float) (12 + Math.sin(a) * 7.4),
                            (float) (12 + Math.cos(a) * 10.0), (float) (12 + Math.sin(a) * 10.0));
                }
                fill(p, color);
                circle(c, p, 12, 12, 2.2f);
                break;
            case "wallpaper":
                stroke(p, 1.8f, color);
                roundRect(c, p, 3.2f, 4.4f, 20.8f, 19.6f, 3.2f);
                fill(p, color);
                circle(c, p, 8.0f, 9.2f, 1.7f);
                path.reset();
                path.moveTo(4.6f, 17.2f);
                path.lineTo(9.6f, 11.8f);
                path.lineTo(13.0f, 15.3f);
                path.lineTo(16.1f, 12.2f);
                path.lineTo(19.4f, 16.4f);
                path.lineTo(19.4f, 18.4f);
                path.lineTo(4.6f, 18.4f);
                path.close();
                c.drawPath(path, p);
                break;
            case "apps":
                fill(p, VivoTheme.alpha(color, 0.55f));
                roundRect(c, p, 3.6f, 3.6f, 10.4f, 10.4f, 2.4f);
                fill(p, color);
                roundRect(c, p, 13.6f, 3.6f, 20.4f, 10.4f, 2.4f);
                roundRect(c, p, 3.6f, 13.6f, 10.4f, 20.4f, 2.4f);
                fill(p, VivoTheme.alpha(color, 0.75f));
                roundRect(c, p, 13.6f, 13.6f, 20.4f, 20.4f, 2.4f);
                break;
            case "fingerprint":
                stroke(p, 1.7f, color);
                path.reset();
                path.addArc(new RectF(7.0f, 7.8f, 17.0f, 17.4f), 160, 220);
                c.drawPath(path, p);
                path.reset();
                path.addArc(new RectF(4.4f, 5.2f, 19.6f, 20.0f), 152, 236);
                c.drawPath(path, p);
                path.reset();
                path.addArc(new RectF(9.4f, 10.3f, 14.6f, 16.0f), 130, 280);
                c.drawPath(path, p);
                line(c, p, 12, 13.5f, 12, 17.2f);
                break;
            case "shield":
                fill(p, color);
                path.reset();
                path.moveTo(12, 2.6f);
                path.lineTo(20.2f, 5.8f);
                path.lineTo(20.2f, 11.4f);
                path.cubicTo(20.2f, 16.4f, 16.9f, 20.0f, 12, 21.6f);
                path.cubicTo(7.1f, 20.0f, 3.8f, 16.4f, 3.8f, 11.4f);
                path.lineTo(3.8f, 5.8f);
                path.close();
                c.drawPath(path, p);
                stroke(p, 2.0f, Color.WHITE);
                path.reset();
                path.moveTo(8.2f, 11.7f);
                path.lineTo(10.9f, 14.4f);
                path.lineTo(16.0f, 9.1f);
                c.drawPath(path, p);
                break;
            case "privacy":
                stroke(p, 1.8f, color);
                path.reset();
                path.moveTo(12, 2.8f);
                path.lineTo(20.0f, 7.5f);
                path.lineTo(20.0f, 16.5f);
                path.lineTo(12, 21.2f);
                path.lineTo(4.0f, 16.5f);
                path.lineTo(4.0f, 7.5f);
                path.close();
                c.drawPath(path, p);
                line(c, p, 4.4f, 7.8f, 12, 12.2f);
                line(c, p, 19.6f, 7.8f, 12, 12.2f);
                line(c, p, 12, 12.2f, 12, 20.6f);
                break;
            case "storage":
                stroke(p, 1.8f, color);
                circle(c, p, 12, 12, 8.2f);
                fill(p, color);
                path.reset();
                path.moveTo(12, 12);
                path.lineTo(12, 3.8f);
                path.arcTo(new RectF(3.8f, 3.8f, 20.2f, 20.2f), -90, 88);
                path.close();
                c.drawPath(path, p);
                fill(p, Color.WHITE);
                circle(c, p, 12, 12, 3.2f);
                break;
            case "battery":
                stroke(p, 1.8f, color);
                roundRect(c, p, 2.8f, 6.6f, 18.4f, 17.4f, 3.2f);
                fill(p, color);
                roundRect(c, p, 5.2f, 9.0f, 13.4f, 15.0f, 1.6f);
                fill(p, color);
                roundRect(c, p, 19.4f, 10.0f, 21.4f, 14.0f, 1.0f);
                break;
            case "navigation":
                stroke(p, 1.8f, color);
                roundRect(c, p, 3.2f, 5.0f, 20.8f, 19.0f, 4.0f);
                fill(p, color);
                roundRect(c, p, 8.2f, 16.0f, 15.8f, 17.2f, 0.6f);
                break;
            case "shortcut":
                fill(p, color);
                path.reset();
                path.moveTo(13.2f, 2.4f);
                path.lineTo(5.0f, 13.4f);
                path.lineTo(10.8f, 13.4f);
                path.lineTo(9.6f, 21.6f);
                path.lineTo(19.0f, 10.0f);
                path.lineTo(12.8f, 10.0f);
                path.close();
                c.drawPath(path, p);
                break;
            case "screenshot":
                stroke(p, 2.0f, color);
                // four crop corners
                path.reset();
                path.moveTo(3.2f, 8.0f); path.lineTo(3.2f, 3.4f); path.lineTo(8.0f, 3.4f);
                path.moveTo(16.0f, 3.4f); path.lineTo(20.8f, 3.4f); path.lineTo(20.8f, 8.0f);
                path.moveTo(20.8f, 16.0f); path.lineTo(20.8f, 20.6f); path.lineTo(16.0f, 20.6f);
                path.moveTo(8.0f, 20.6f); path.lineTo(3.2f, 20.6f); path.lineTo(3.2f, 16.0f);
                c.drawPath(path, p);
                stroke(p, 1.6f, color);
                circle(c, p, 12, 12, 3.2f);
                break;
            case "clock":
                stroke(p, 1.8f, color);
                circle(c, p, 12, 12, 8.6f);
                line(c, p, 12, 7.2f, 12, 12.4f);
                line(c, p, 12, 12.4f, 15.6f, 14.4f);
                fill(p, color);
                circle(c, p, 12, 12.2f, 1.35f);
                break;
            case "world":
                stroke(p, 1.8f, color);
                circle(c, p, 12, 12, 8.6f);
                path.reset();
                path.addOval(new RectF(7.0f, 3.4f, 17.0f, 20.6f), Path.Direction.CW);
                c.drawPath(path, p);
                line(c, p, 3.6f, 12, 20.4f, 12);
                break;
            case "stopwatch":
                stroke(p, 1.8f, color);
                circle(c, p, 12, 13.2f, 7.8f);
                line(c, p, 12, 13.2f, 12, 9.4f);
                line(c, p, 12, 13.2f, 15.0f, 15.2f);
                line(c, p, 9.8f, 1.9f, 14.2f, 1.9f);
                line(c, p, 12, 2.2f, 12, 4.6f);
                break;
            case "timer":
                stroke(p, 1.8f, color);
                path.reset();
                path.moveTo(6.6f, 3.4f);
                path.lineTo(17.4f, 3.4f);
                path.lineTo(17.4f, 7.4f);
                path.lineTo(12, 12.0f);
                path.lineTo(17.4f, 16.6f);
                path.lineTo(17.4f, 20.6f);
                path.lineTo(6.6f, 20.6f);
                path.lineTo(6.6f, 16.6f);
                path.lineTo(12, 12.0f);
                path.lineTo(6.6f, 7.4f);
                path.close();
                c.drawPath(path, p);
                break;
            case "gear":
                stroke(p, 2.1f, color);
                for (int i = 0; i < 8; i++) {
                    double a = Math.PI * i / 4.0 + Math.PI / 8.0;
                    line(c, p, (float) (12 + Math.cos(a) * 6.6), (float) (12 + Math.sin(a) * 6.6),
                            (float) (12 + Math.cos(a) * 9.2), (float) (12 + Math.sin(a) * 9.2));
                }
                circle(c, p, 12, 12, 5.0f);
                circle(c, p, 12, 12, 1.8f);
                break;
            case "theme":
            case "moon":
                fill(p, color);
                path.reset();
                path.addCircle(12, 12, 8.4f, Path.Direction.CW);
                Path cut = new Path();
                cut.addCircle(15.4f, 8.8f, 7.4f, Path.Direction.CW);
                path.op(cut, Path.Op.DIFFERENCE);
                c.drawPath(path, p);
                break;
            case "sun":
                stroke(p, 1.8f, color);
                circle(c, p, 12, 12, 4.0f);
                for (int i = 0; i < 8; i++) {
                    double a = Math.PI * i / 4.0;
                    line(c, p, (float) (12 + Math.cos(a) * 6.8), (float) (12 + Math.sin(a) * 6.8),
                            (float) (12 + Math.cos(a) * 9.2), (float) (12 + Math.sin(a) * 9.2));
                }
                break;
            case "sun_small":
                stroke(p, 1.7f, color);
                circle(c, p, 12, 12, 3.4f);
                for (int i = 0; i < 8; i++) {
                    double a = Math.PI * i / 4.0;
                    line(c, p, (float) (12 + Math.cos(a) * 5.6), (float) (12 + Math.sin(a) * 5.6),
                            (float) (12 + Math.cos(a) * 7.6), (float) (12 + Math.sin(a) * 7.6));
                }
                break;
            case "sun_large":
                stroke(p, 1.8f, color);
                circle(c, p, 12, 12, 4.8f);
                for (int i = 0; i < 8; i++) {
                    double a = Math.PI * i / 4.0;
                    line(c, p, (float) (12 + Math.cos(a) * 7.8), (float) (12 + Math.sin(a) * 7.8),
                            (float) (12 + Math.cos(a) * 10.4), (float) (12 + Math.sin(a) * 10.4));
                }
                break;
            case "blur":
                fill(p, VivoTheme.alpha(color, 0.22f));
                circle(c, p, 8.8f, 12.2f, 6.6f);
                fill(p, VivoTheme.alpha(color, 0.38f));
                circle(c, p, 12.4f, 11.4f, 6.2f);
                fill(p, VivoTheme.alpha(color, 0.62f));
                circle(c, p, 15.6f, 12.2f, 5.4f);
                stroke(p, 1.2f, VivoTheme.alpha(color, 0.9f));
                circle(c, p, 12, 12.2f, 8.8f);
                break;
            case "layers":
                stroke(p, 1.7f, VivoTheme.alpha(color, 0.45f));
                roundRect(c, p, 4.4f, 3.6f, 19.6f, 9.6f, 2.0f);
                stroke(p, 1.7f, VivoTheme.alpha(color, 0.7f));
                roundRect(c, p, 4.4f, 8.8f, 19.6f, 14.8f, 2.0f);
                fill(p, VivoTheme.alpha(color, 0.18f));
                roundRect(c, p, 4.4f, 14.0f, 19.6f, 20.4f, 2.0f);
                stroke(p, 1.7f, color);
                roundRect(c, p, 4.4f, 14.0f, 19.6f, 20.4f, 2.0f);
                break;
            case "palette":
                stroke(p, 1.7f, color);
                roundRect(c, p, 3.4f, 3.4f, 11.0f, 11.0f, 2.4f);
                roundRect(c, p, 13.0f, 3.4f, 20.6f, 11.0f, 2.4f);
                roundRect(c, p, 3.4f, 13.0f, 11.0f, 20.6f, 2.4f);
                fill(p, color);
                circle(c, p, 16.8f, 16.8f, 3.8f);
                break;
            case "bell":
                fill(p, color);
                path.reset();
                path.moveTo(5.2f, 17.2f);
                path.cubicTo(6.4f, 15.6f, 6.8f, 14.4f, 6.8f, 10.8f);
                path.cubicTo(6.8f, 7.6f, 9.0f, 4.6f, 12.0f, 4.6f);
                path.cubicTo(15.0f, 4.6f, 17.2f, 7.6f, 17.2f, 10.8f);
                path.cubicTo(17.2f, 14.4f, 17.6f, 15.6f, 18.8f, 17.2f);
                path.lineTo(5.2f, 17.2f);
                path.close();
                c.drawPath(path, p);
                stroke(p, 1.8f, color);
                path.reset();
                path.addArc(new RectF(9.6f, 17.4f, 14.4f, 21.2f), 0, 180);
                c.drawPath(path, p);
                break;
            case "edit":
                stroke(p, 1.8f, color);
                path.reset();
                path.moveTo(4.2f, 19.6f);
                path.lineTo(5.2f, 14.8f);
                path.lineTo(15.4f, 4.6f);
                path.lineTo(19.2f, 8.4f);
                path.lineTo(9.0f, 18.6f);
                path.close();
                c.drawPath(path, p);
                line(c, p, 13.6f, 6.4f, 17.4f, 10.2f);
                break;
            case "copy":
                stroke(p, 1.7f, color);
                roundRect(c, p, 8.2f, 3.4f, 20.4f, 15.6f, 3.0f);
                roundRect(c, p, 3.6f, 8.0f, 15.8f, 20.4f, 3.0f);
                break;
            case "trash":
                stroke(p, 1.8f, color);
                line(c, p, 4.4f, 6.6f, 19.6f, 6.6f);
                line(c, p, 9.2f, 3.6f, 14.8f, 3.6f);
                path.reset();
                path.moveTo(6.4f, 7.2f);
                path.lineTo(7.4f, 20.2f);
                path.lineTo(16.6f, 20.2f);
                path.lineTo(17.6f, 7.2f);
                c.drawPath(path, p);
                line(c, p, 10.2f, 10.0f, 10.6f, 17.2f);
                line(c, p, 13.8f, 10.0f, 13.4f, 17.2f);
                break;
            case "waveform":
                stroke(p, 1.6f, color);
                circle(c, p, 12, 12, 8.8f);
                stroke(p, 1.9f, color);
                line(c, p, 7.4f, 10.4f, 7.4f, 13.6f);
                line(c, p, 10.2f, 8.4f, 10.2f, 15.6f);
                line(c, p, 13.8f, 8.4f, 13.8f, 15.6f);
                line(c, p, 16.6f, 10.4f, 16.6f, 13.6f);
                break;
            case "bed":
                stroke(p, 1.8f, color);
                line(c, p, 3.0f, 8.0f, 3.0f, 18.6f);
                line(c, p, 3.0f, 13.2f, 21.0f, 13.2f);
                line(c, p, 3.0f, 18.6f, 21.0f, 18.6f);
                line(c, p, 21.0f, 18.6f, 21.0f, 15.4f);
                path.reset();
                path.moveTo(5.0f, 13.2f);
                path.lineTo(5.0f, 10.4f);
                path.cubicTo(5.0f, 9.8f, 5.5f, 9.4f, 6.2f, 9.4f);
                path.lineTo(11.6f, 9.4f);
                path.lineTo(11.6f, 13.2f);
                path.close();
                c.drawPath(path, p);
                break;
            case "display":
                stroke(p, 1.8f, color);
                roundRect(c, p, 2.8f, 4.2f, 21.2f, 17.0f, 3.2f);
                line(c, p, 9.0f, 20.2f, 15.0f, 20.2f);
                line(c, p, 12.0f, 17.2f, 12.0f, 20.2f);
                break;
            case "font":
                fill(p, color);
                p.setTextAlign(Paint.Align.CENTER);
                p.setTextSize(17.5f);
                p.setTypeface(VivoTheme.typeface(VivoTheme.W_BOLD));
                c.drawText("A", 11.4f, 19.4f, p);
                p.setTextAlign(Paint.Align.LEFT);
                stroke(p, 1.6f, color);
                line(c, p, 4.8f, 19.4f, 10.2f, 19.4f);
                break;
            case "widget":
                stroke(p, 1.7f, color);
                roundRect(c, p, 3.4f, 3.4f, 12.0f, 12.0f, 3.0f);
                roundRect(c, p, 14.2f, 3.4f, 20.6f, 9.8f, 2.4f);
                roundRect(c, p, 3.4f, 14.2f, 9.8f, 20.6f, 2.4f);
                fill(p, color);
                roundRect(c, p, 14.2f, 14.2f, 20.6f, 20.6f, 2.4f);
                break;
            case "eye_off":
                stroke(p, 1.7f, color);
                path.reset();
                path.moveTo(3.2f, 12.0f);
                path.cubicTo(5.8f, 7.6f, 8.8f, 5.6f, 12.0f, 5.6f);
                path.cubicTo(15.2f, 5.6f, 18.2f, 7.6f, 20.8f, 12.0f);
                path.cubicTo(18.2f, 16.4f, 15.2f, 18.4f, 12.0f, 18.4f);
                path.cubicTo(8.8f, 18.4f, 5.8f, 16.4f, 3.2f, 12.0f);
                c.drawPath(path, p);
                circle(c, p, 12, 12, 3.0f);
                line(c, p, 4.4f, 4.4f, 19.6f, 19.6f);
                break;
            case "refresh":
                stroke(p, 1.9f, color);
                path.reset();
                path.addArc(new RectF(4.0f, 4.0f, 20.0f, 20.0f), 40, 280);
                c.drawPath(path, p);
                path.reset();
                path.moveTo(17.0f, 2.6f);
                path.lineTo(20.6f, 6.2f);
                path.lineTo(16.2f, 7.4f);
                c.drawPath(path, p);
                break;
            case "info":
                stroke(p, 1.8f, color);
                circle(c, p, 12, 12, 8.6f);
                fill(p, color);
                circle(c, p, 12, 7.7f, 1.3f);
                stroke(p, 2.0f, color);
                line(c, p, 12, 10.6f, 12, 17.0f);
                break;
            case "help":
                stroke(p, 1.8f, color);
                circle(c, p, 12, 12, 8.6f);
                stroke(p, 1.9f, color);
                path.reset();
                path.moveTo(9.2f, 9.2f);
                path.cubicTo(9.4f, 6.8f, 14.8f, 6.6f, 14.8f, 10.0f);
                path.cubicTo(14.8f, 12.0f, 12.0f, 12.0f, 12.0f, 14.2f);
                c.drawPath(path, p);
                fill(p, color);
                circle(c, p, 12, 17.2f, 1.25f);
                break;
            case "dot":
                fill(p, color);
                circle(c, p, 12, 12, 4.2f);
                break;
            case "radio":
                stroke(p, 1.9f, color);
                circle(c, p, 12, 12, 8.2f);
                break;
            case "checkbox":
                stroke(p, 1.9f, color);
                roundRect(c, p, 3.6f, 3.6f, 20.4f, 20.4f, 5.6f);
                break;
            case "refresh_arrow":
                stroke(p, 1.9f, color);
                path.reset();
                path.addArc(new RectF(4.0f, 4.0f, 20.0f, 20.0f), -50, 280);
                c.drawPath(path, p);
                path.reset();
                path.moveTo(17.4f, 2.8f);
                path.lineTo(21.0f, 6.4f);
                path.lineTo(16.6f, 7.8f);
                c.drawPath(path, p);
                break;
            case "cloud":
                stroke(p, 1.8f, color);
                path.reset();
                path.moveTo(7.0f, 18.6f);
                path.cubicTo(4.0f, 18.6f, 2.8f, 15.2f, 5.2f, 13.2f);
                path.cubicTo(5.0f, 9.4f, 9.2f, 7.4f, 11.6f, 9.8f);
                path.cubicTo(13.4f, 6.8f, 18.4f, 7.6f, 18.6f, 11.2f);
                path.cubicTo(21.8f, 11.8f, 22.2f, 16.4f, 19.4f, 18.6f);
                path.lineTo(7.0f, 18.6f);
                c.drawPath(path, p);
                break;
            case "star":
                fill(p, color);
                path.reset();
                for (int i = 0; i < 5; i++) {
                    double a = -Math.PI / 2 + i * 2 * Math.PI / 5;
                    double b = a + Math.PI / 5;
                    float x1 = (float) (12 + Math.cos(a) * 9.2);
                    float y1 = (float) (12 + Math.sin(a) * 9.2);
                    float x2 = (float) (12 + Math.cos(b) * 3.9);
                    float y2 = (float) (12 + Math.sin(b) * 3.9);
                    if (i == 0) path.moveTo(x1, y1); else path.lineTo(x1, y1);
                    path.lineTo(x2, y2);
                }
                path.close();
                c.drawPath(path, p);
                break;
            default:
                stroke(p, 1.8f, color);
                circle(c, p, 12, 12, 8.4f);
                break;
        }
    }
}
