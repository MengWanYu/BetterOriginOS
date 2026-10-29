package com.originui.replica;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;

/**
 * OriginOS 5 滑杆（originui seekbar rom15_0 复刻，绘制 1:1）。
 *
 * 逆向来源：settings-dec/res/drawable/originui_seekbar_thumb_normal_light_enable_rom13_5.xml
 * （thumb vector 18dp：外圆 = track_gradient_endColor #0F77FF、内心 9dp = thumb_color #FFFFFF，
 * 夜间内心黑）+ originui_seekbar_progress_horizontal（轨道 4dp，vigour_seekbar_minHeight）+
 * 未播放色 #0F000000（夜间 #33FFFFFF）+ 拖动数值气泡
 * （originui_abs_seekbar_new_style_toast_*：≥50×22dp、padding 10/4、背景 vseekbar_toast_color #0F77FF）。
 *
 * 结构（区别于 Material 滑杆）：thumb 是"蓝色圆盘+白心"（无阴影），直径 18dp 远大于 4dp 轨道。
 */
public class OriginSlider extends View {
    public interface OnProgressChangedListener {
        void onProgressChanged(OriginSlider view, float progress);
    }

    private static final float TRACK_H = 4f;      // vigour_seekbar_minHeight
    private static final float THUMB_D = 18f;     // thumb vector 尺寸
    private static final float CORE_D = 9f;       // 白心直径（viewport 4/8）
    private static final float TOAST_MIN_W = 50f;
    private static final float TOAST_MIN_H = 22f;

    private final Paint mTrackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mThumbPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mCorePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF mRect = new RectF();

    private float mMin = 0f, mMax = 100f, mProgress = 0f;
    private OnProgressChangedListener mListener;

    // 夜间缓存
    private boolean mNight;
    private int mActiveColor, mInactiveColor, mThumbColor, mCoreColor;

    // 拖动气泡（VPopupTextView 语义）
    private TextView mToast;
    private CharSequence mToastSuffix = "";

    public OriginSlider(Context context) {
        this(context, null);
    }

    public OriginSlider(Context context, AttributeSet attrs) {
        super(context, attrs);
        refreshColors();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        refreshColors();
    }

    @Override
    protected void onConfigurationChanged(android.content.res.Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        refreshColors();
        invalidate();
    }

    private void refreshColors() {
        mNight = OriginTheme.night(getContext());
        mActiveColor = 0xFF0F77FF;                    // track_gradient_endColor_rom15_0
        mInactiveColor = mNight ? 0x33FFFFFF : 0x0F000000;
        mThumbColor = 0xFF0F77FF;                     // thumb 外盘
        mCoreColor = mNight ? 0xFF000000 : 0xFFFFFFFF; // thumb 心
    }

    public void setRange(float min, float max) {
        mMin = min;
        mMax = max;
        invalidate();
    }

    public void setProgress(float value) {
        float p = (value - mMin) / (mMax - mMin);
        if (p != mProgress) {
            mProgress = Math.max(0f, Math.min(1f, p));
            invalidate();
        }
    }

    public float getProgressValue() {
        return mMin + mProgress * (mMax - mMin);
    }

    public void setListener(OnProgressChangedListener l) {
        mListener = l;
    }

    /** 气泡后缀（如 "°"、"%"），拖动时显示在 thumb 上方。 */
    public void setToastSuffix(CharSequence suffix) {
        mToastSuffix = suffix;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int h = resolveSize(OriginTheme.dpi(getContext(), 48), heightMeasureSpec);
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), h);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float scale = OriginTheme.dp(getContext(), 1f);
        float cy = getHeight() / 2f;
        float trackH = TRACK_H * scale;
        float sidePad = THUMB_D * scale / 2f;
        float width = getWidth() - sidePad * 2;

        // 轨道：已播放 accent / 未播放 6% 黑（夜 20% 白）
        mRect.set(sidePad, cy - trackH / 2, sidePad + width, cy + trackH / 2);
        mTrackPaint.setStyle(Paint.Style.FILL);
        mTrackPaint.setColor(mInactiveColor);
        canvas.drawRoundRect(mRect, trackH / 2, trackH / 2, mTrackPaint);
        mRect.set(sidePad, cy - trackH / 2, sidePad + width * mProgress, cy + trackH / 2);
        mTrackPaint.setColor(mActiveColor);
        canvas.drawRoundRect(mRect, trackH / 2, trackH / 2, mTrackPaint);

        // thumb：18dp 蓝盘 + 9dp 白心（无阴影，vector 1:1）
        float cx = sidePad + width * mProgress;
        float thumbR = THUMB_D * scale / 2f;
        mThumbPaint.setColor(mThumbColor);
        mThumbPaint.setAlpha(isEnabled() ? 255 : 102);
        canvas.drawCircle(cx, cy, thumbR, mThumbPaint);
        mCorePaint.setColor(mCoreColor);
        mCorePaint.setAlpha(mThumbPaint.getAlpha());
        canvas.drawCircle(cx, cy, CORE_D * scale / 2f, mCorePaint);
    }

    // ---- 拖动气泡 ----

    private void ensureToast() {
        if (mToast != null) {
            return;
        }
        ViewGroup root = (ViewGroup) getRootView();
        FrameLayout flip = new FrameLayout(getContext());
        flip.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        mToast = new TextView(getContext());
        mToast.setGravity(Gravity.CENTER);
        mToast.setTextColor(0xFFFFFFFF);
        mToast.setTextSize(12);
        mToast.setTypeface(android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL));
        mToast.setBackground(OriginTheme.roundedRect(getContext(), 0xFF0F77FF,
                TOAST_MIN_H / 2f));
        mToast.setPadding(OriginTheme.dpi(getContext(), 10), OriginTheme.dpi(getContext(), 4),
                OriginTheme.dpi(getContext(), 10), OriginTheme.dpi(getContext(), 4));
        flip.addView(mToast, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(flip);
        mToast.setTag(flip);
    }

    private void updateToast() {
        if (mToast == null) {
            return;
        }
        mToast.setText(String.valueOf(Math.round(getProgressValue())) + mToastSuffix);
        mToast.measure(MeasureSpec.UNSPECIFIED, MeasureSpec.UNSPECIFIED);
        int tw = Math.max(mToast.getMeasuredWidth(), OriginTheme.dpi(getContext(), TOAST_MIN_W));
        int th = Math.max(mToast.getMeasuredHeight(), OriginTheme.dpi(getContext(), TOAST_MIN_H));
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) mToast.getLayoutParams();
        lp.width = tw;
        lp.height = th;
        mToast.setLayoutParams(lp);
        // 定位：thumb 上方 12dp，水平居中并 clamp 到屏幕内
        int[] origin = new int[2];
        getLocationOnScreen(origin);
        int[] rootOrigin = new int[2];
        ((View) mToast.getTag()).getLocationOnScreen(rootOrigin);
        float scale = OriginTheme.dp(getContext(), 1f);
        float sidePad = THUMB_D * scale / 2f;
        float width = getWidth() - sidePad * 2;
        float cx = origin[0] + sidePad + width * mProgress;
        int left = (int) (cx - rootOrigin[0] - tw / 2f);
        int top = (int) (origin[1] - rootOrigin[1] - th - OriginTheme.dp(getContext(), 12f));
        left = Math.max(origin[0] > 0 ? 8 : 8, Math.min(left,
                ((View) mToast.getTag()).getWidth() - tw - 8));
        mToast.setX(left);
        mToast.setY(top);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (!isEnabled()) {
            return false;
        }
        float sidePad = OriginTheme.dp(getContext(), THUMB_D / 2f);
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                ensureToast();
                updateProgress(event.getX(), sidePad);
                updateToast();
                mToast.setVisibility(VISIBLE);
                mToast.animate().alpha(1f).setDuration(120).start();
                performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
                getParent().requestDisallowInterceptTouchEvent(true);
                return true;
            case MotionEvent.ACTION_MOVE:
                updateProgress(event.getX(), sidePad);
                updateToast();
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                getParent().requestDisallowInterceptTouchEvent(false);
                if (mToast != null) {
                    mToast.animate().alpha(0f).setDuration(150)
                            .withEndAction(new Runnable() {
                                @Override
                                public void run() {
                                    if (mToast != null) {
                                        mToast.setVisibility(INVISIBLE);
                                    }
                                }
                            }).start();
                }
                performClick();
                return true;
            default:
                return super.onTouchEvent(event);
        }
    }

    private void updateProgress(float x, float sidePad) {
        float width = getWidth() - sidePad * 2;
        if (width <= 0) return;
        mProgress = Math.max(0f, Math.min(1f, (x - sidePad) / width));
        invalidate();
        if (mListener != null) {
            mListener.onProgressChanged(this, getProgressValue());
        }
    }

    @Override
    public boolean performClick() {
        performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
        return super.performClick();
    }
}
