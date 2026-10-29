package com.betteroriginos.feature.notify;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.text.TextUtils;

/** 设置页内的通知条实时预览：用与 Hook 侧同一组规格离屏绘制，改参数即可见 */
public final class NotifyRowPreview {
    private static Bitmap sCache;
    private static int sCacheWidth;
    private static boolean sCacheNight;

    private NotifyRowPreview() {
    }

    public static synchronized Bitmap render(Context ctx) {
        int width = ctx.getResources().getDisplayMetrics().widthPixels
                - NotifyRowSpec.dp(ctx, NotifyRowSpec.PREVIEW_PAGE_MARGIN_DP) * 2;
        boolean night = NotifyRowSpec.night(ctx);
        if (sCache != null && sCacheWidth == width && sCacheNight == night) {
            return sCache;
        }

        Sample a = new Sample();
        a.pkg = "com.android.settings";
        a.app = "设置";
        a.time = "刚刚";
        a.title = "软件更新";
        a.body = "发现新版本 15.0.5.241，建议在 Wi-Fi 环境下更新。";
        Sample b = new Sample();
        b.pkg = "com.android.bbkmusic";
        b.app = "音乐";
        b.time = "2 分钟前";
        b.title = "每日推荐";
        b.body = "为你推荐 30 首好歌，点击查看今日歌单。";
        Sample d = new Sample();
        d.pkg = "com.android.shell";
        d.app = "Shell";
        d.time = "刚刚";
        d.title = "小明";
        d.body = "你好，在吗？";

        int cardGap = NotifyRowSpec.dp(ctx, NotifyRowSpec.CARD_GAP_DP);
        int cardA = cardHeight(ctx, a, width);
        int cardB = cardHeight(ctx, b, width);
        int cardD = cardHeight(ctx, d, width);
        int height = cardA + cardGap + cardB + cardGap + cardD;
        Bitmap bmp = Bitmap.createBitmap(width, Math.max(1, height), Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bmp);
        drawCard(c, ctx, a, 0, width, night);
        drawCard(c, ctx, b, cardA + cardGap, width, night);
        drawCard(c, ctx, d, cardA + cardGap + cardB + cardGap, width, night);
        sCache = bmp;
        sCacheWidth = width;
        sCacheNight = night;
        return bmp;
    }

    private static int cardHeight(Context ctx, Sample s, int width) {
        int measured = measureCard(ctx, s, width);
        return measured;
    }

    private static int measureCard(Context ctx, Sample s, int width) {
        TextPaint titlePaint = titlePaint(ctx);
        TextPaint bodyPaint = bodyPaint(ctx);
        int textWidth = width - NotifyRowSpec.dp(ctx, NotifyRowSpec.COLUMN_DP)
                - NotifyRowSpec.dp(ctx, 16);
        int titleH = layout(s.title, titlePaint, textWidth, NotifyRowSpec.TITLE_MAX_LINES).getHeight();
        int bodyH = layout(s.body, bodyPaint, textWidth, NotifyRowSpec.BODY_MAX_LINES).getHeight();
        int bottom = NotifyRowSpec.dp(ctx, NotifyRowSpec.TITLE_TOP_DP) + titleH
                + NotifyRowSpec.dp(ctx, NotifyRowSpec.TITLE_BODY_GAP_DP) + bodyH
                + NotifyRowSpec.dp(ctx, NotifyRowSpec.CARD_PAD_BOTTOM_DP);
        int iconBottom = NotifyRowSpec.dp(ctx, NotifyRowSpec.APP_LINE_TOP_DP
                + NotifyRowSpec.ICON_DP + 12);
        return Math.max(bottom, iconBottom);
    }

    private static void drawCard(Canvas c, Context ctx, Sample s, int top, int width,
                                 boolean night) {
        float radius = NotifyRowSpec.dp(ctx, NotifyRowSpec.CARD_RADIUS_DP);
        RectF rect = new RectF(0, top, width, top + measureCard(ctx, s, width));
        Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        fill.setShader(new LinearGradient(0, rect.top, 0, rect.bottom,
                night ? 0xFF2C2C2E : 0xFFFDFDFE,
                night ? 0xFF232325 : 0xFFF4F5F7, Shader.TileMode.CLAMP));
        c.drawRoundRect(rect, radius, radius, fill);
        Paint border = new Paint(Paint.ANTI_ALIAS_FLAG);
        border.setStyle(Paint.Style.STROKE);
        border.setStrokeWidth(Math.max(1f, NotifyRowSpec.dp(ctx, 0.5f)));
        border.setColor(night ? 0x1FFFFFFF : 0x14000000);
        c.drawRoundRect(rect, radius, radius, border);

        int iconLeft = NotifyRowSpec.dp(ctx, NotifyRowSpec.GUTTER_DP);
        int iconTop = top + NotifyRowSpec.dp(ctx, NotifyRowSpec.APP_LINE_TOP_DP);
        int iconSize = NotifyRowSpec.dp(ctx, NotifyRowSpec.ICON_DP);
        drawIcon(c, ctx, s, iconLeft, iconTop, iconSize);

        int column = NotifyRowSpec.dp(ctx, NotifyRowSpec.COLUMN_DP);
        int rightPad = NotifyRowSpec.dp(ctx, 16);
        int appLineTop = top + NotifyRowSpec.dp(ctx, NotifyRowSpec.APP_LINE_TOP_DP);
        int appLineH = NotifyRowSpec.dp(ctx, 18);
        int secondary = night ? NotifyRowSpec.DARK_SECONDARY : NotifyRowSpec.LIGHT_SECONDARY;
        int primary = night ? NotifyRowSpec.DARK_PRIMARY : NotifyRowSpec.LIGHT_PRIMARY;

        TextPaint appPaint = appPaint(ctx);
        appPaint.setColor(secondary);
        c.drawText(s.app, column, baseline(appPaint, appLineTop, appLineH), appPaint);

        TextPaint timePaint = appPaint(ctx);
        timePaint.setColor(secondary);
        float arrowRight = width - rightPad;
        float arrowLeft = arrowRight - NotifyRowSpec.dp(ctx, 8);
        drawArrow(c, arrowLeft, arrowRight, appLineTop + appLineH / 2f, ctx, secondary);
        float timeRight = arrowLeft - NotifyRowSpec.dp(ctx, 8);
        c.drawText(s.time, timeRight - timePaint.measureText(s.time),
                baseline(timePaint, appLineTop, appLineH), timePaint);

        int titleTop = top + NotifyRowSpec.dp(ctx, NotifyRowSpec.TITLE_TOP_DP);
        int textWidth = width - column - rightPad;
        TextPaint titlePaint = titlePaint(ctx);
        titlePaint.setColor(primary);
        StaticLayout title = layout(s.title, titlePaint, textWidth,
                NotifyRowSpec.TITLE_MAX_LINES);
        c.save();
        c.translate(column, titleTop);
        title.draw(c);
        c.restore();

        int bodyTop = titleTop + title.getHeight()
                + NotifyRowSpec.dp(ctx, NotifyRowSpec.TITLE_BODY_GAP_DP);
        TextPaint bodyPaint = bodyPaint(ctx);
        bodyPaint.setColor(primary);
        StaticLayout body = layout(s.body, bodyPaint, textWidth,
                NotifyRowSpec.BODY_MAX_LINES);
        c.save();
        c.translate(column, bodyTop);
        body.draw(c);
        c.restore();
    }

    private static void drawIcon(Canvas c, Context ctx, Sample s, int left, int top, int size) {
        Drawable d = null;
        try {
            d = ctx.getPackageManager().getApplicationIcon(s.pkg);
        } catch (Throwable ignored) {
        }
        if (d == null) {
            try {
                d = ctx.getPackageManager().getApplicationIcon("com.android.settings");
            } catch (Throwable ignored) {
            }
        }
        RectF r = new RectF(left, top, left + size, top + size);
        float corner = NotifyRowSpec.dp(ctx, NotifyRowSpec.ICON_CORNER_DP);
        Path clip = new Path();
        clip.addRoundRect(r, corner, corner, Path.Direction.CW);
        int save = c.save();
        c.clipPath(clip);
        if (d != null) {
            d.setBounds((int) r.left, (int) r.top, (int) r.right, (int) r.bottom);
            d.draw(c);
        } else {
            Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
            p.setColor(0xFF0F77FF);
            c.drawRect(r, p);
        }
        c.restoreToCount(save);
    }

    private static void drawArrow(Canvas c, float left, float right, float cy, Context ctx,
                                  int color) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeCap(Paint.Cap.ROUND);
        p.setStrokeJoin(Paint.Join.ROUND);
        p.setStrokeWidth(NotifyRowSpec.dp(ctx, 1.6f));
        p.setColor((color & 0x00FFFFFF) | 0x55000000);
        float midX = (left + right) / 2f;
        float half = NotifyRowSpec.dp(ctx, 2.6f);
        Path path = new Path();
        path.moveTo(left, cy - half);
        path.lineTo(midX, cy + half);
        path.lineTo(right, cy - half);
        c.drawPath(path, p);
    }

    private static float baseline(TextPaint p, int top, int height) {
        Paint.FontMetrics fm = p.getFontMetrics();
        return top + (height - (fm.descent - fm.ascent)) / 2f - fm.ascent;
    }

    private static TextPaint appPaint(Context ctx) {
        TextPaint p = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        p.setTextSize(NotifyRowSpec.sp(ctx, NotifyRowSpec.APP_TEXT_SP));
        p.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
        return p;
    }

    private static TextPaint titlePaint(Context ctx) {
        TextPaint p = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        p.setTextSize(NotifyRowSpec.sp(ctx, NotifyRowSpec.TITLE_TEXT_SP));
        p.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        return p;
    }

    private static TextPaint bodyPaint(Context ctx) {
        TextPaint p = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        p.setTextSize(NotifyRowSpec.sp(ctx, NotifyRowSpec.BODY_TEXT_SP));
        p.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
        return p;
    }

    private static StaticLayout layout(CharSequence text, TextPaint paint, int width, int maxLines) {
        return StaticLayout.Builder.obtain(text, 0, text.length(), paint, Math.max(1, width))
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .setIncludePad(false)
                .setLineSpacing(0f, 1f)
                .setMaxLines(maxLines)
                .setEllipsize(TextUtils.TruncateAt.END)
                .build();
    }

    private static final class Sample {
        String pkg;
        String app;
        String time;
        String title;
        String body;
    }
}
