package com.originui.replica;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.Interpolator;
import android.view.animation.OvershootInterpolator;
import android.view.animation.PathInterpolator;

/**
 * OriginOS 5 开关（com.originui.widget.components.switches.VMoveBoolButton 复刻，
 * 绘制/几何/颜色/动画 1:1 逆向自 rom15_0 资源、d0() 绘制与 o0()/performClick/ACTION_MOVE/h0()）。
 *
 * 视觉结构（三层，progress J0 0..1）：
 *  1. 填充蓝条：高 13dp，宽 = max(J0,0.5)×38dp，色 light #330F77FF / night #4D2785FF，
 *     alpha = J0<0.5 ? J0×2 : 1（k0：仅缩放 alpha 通道）。
 *  2. 描边灰条（OFF 态轨道）：高 17.5dp、线宽 2.5dp、空心，左端 = 4 + min(J0,0.5)×38dp
 *     随进度向右滑出，alpha = 1-J0，色 light #5CCCCCCC / night #75464646。
 *  3. thumb：半径 8.5→10dp 随进度生长，中心 x = 12.5→32dp；
 *     填充 light 透明→80%白（night 黑→80%黑），叠加 3dp 描边环
 *     light #CCCCCC→#0F77FF（night #464646→#2785FF）。OFF 态 = 空心圆环，
 *     ON 态 = 白(黑)底+蓝环。thumb/环颜色为通道级 lerp（Z()）。
 *
 * 动画架构（源码级）：
 * - 双 ValueAnimator：mAnimOn(C0)=0→1 / mAnimOff(B0)=1→0，duration D0=250，
 *   默认插值器 u0=PathInterpolator(0.2,0.4,0.2,1)（源码 line 2790/2792）。
 * - 点击切换（performClick L17-L24）：目标开→C0.start()、关→B0.start()，不换插值器。
 * - 拖动跨 0.6/0.4 阈值（ACTION_MOVE L86/L88）：setInterpolator(Overshoot 1.8) +
 *   setCurrentPlayTime(J0×250) 从当前位置续播（源码不恢复默认插值器，同样保留）。
 * - onCheckedChanged 在动画结束时触发（h0() endOfAnimation 语义），非点击瞬间。
 * - 触感：源码 H0() vibratorPro(113,-1,-1) 受系统 haptic_feedback_enabled 控制（此处系统 API 回退）；
 *   音效 playSoundEffect(0)。
 */
public class OriginSwitch extends View {
    public interface OnCheckedChangeListener {
        void onCheckedChanged(OriginSwitch view, boolean isChecked);
    }

    private static final float FLIP_ON = 0.6f;
    private static final float FLIP_OFF = 0.4f;

    // ---- rom15_0 几何（dp）----
    private static final float PAD_LEFT = 4f;
    private static final float TRAVEL = 38f;        // A0：行程基准
    private static final float TRACK_ON_H = 13f;    // 填充蓝条高
    private static final float TRACK_OFF_H = 17.5f; // 描边条高
    private static final float TRACK_OFF_STROKE = 2.5f;
    private static final float RING_STROKE = 3f;    // thumb 环线宽
    private static final float THUMB_R_OFF = 8.5f;  // X0
    private static final float THUMB_R_ON = 10f;    // Y0

    // ---- 动画（源码 D0=250、u0、v0）----
    private static final long D0 = 250;
    private final Interpolator mU0 = new PathInterpolator(0.2f, 0.4f, 0.2f, 1f);
    private final OvershootInterpolator mOvershoot = new OvershootInterpolator(1.8f);

    private final Paint mPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF mRect = new RectF();

    private ValueAnimator mAnimOn;   // C0：0→1
    private ValueAnimator mAnimOff;  // B0：1→0
    private boolean mSuppressEndCallback;
    private float mProgress;         // J0
    private boolean mChecked;
    private boolean mDragging;
    private float mDragStartX;
    private float mDragProgressStart;

    private OnCheckedChangeListener mListener;

    // 夜间缓存
    private boolean mNight;
    private int mTrackOffColor, mTrackOnColor, mRingOffColor, mRingOnColor,
            mThumbOffColor, mThumbOnColor;

    public OriginSwitch(Context context) {
        this(context, null);
    }

    public OriginSwitch(Context context, AttributeSet attrs) {
        super(context, attrs);
        refreshColors();
        setClickable(true);
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
        if (mNight) {
            mTrackOffColor = 0x75464646;
            mTrackOnColor = 0x4D2785FF;
            mRingOffColor = 0xFF464646;
            mRingOnColor = 0xFF2785FF;
            mThumbOffColor = 0xFF000000;
            mThumbOnColor = 0xCC000000;
        } else {
            mTrackOffColor = 0x5CCCCCCC;
            mTrackOnColor = 0x330F77FF;
            mRingOffColor = 0xFFCCCCCC;
            mRingOnColor = 0xFF0F77FF;
            mThumbOffColor = 0x00FFFFFF;
            mThumbOnColor = 0xCCFFFFFF;
        }
    }

    public void setOnCheckedChangeListener(OnCheckedChangeListener l) {
        mListener = l;
    }

    public boolean isChecked() {
        return mChecked;
    }

    // ---- 动画核心 ----

    /** o0()：构造双动画器（C0=0→1 / B0=1→0）；结束回调走 h0() 语义（动画结束才通知）。 */
    private void setupAnimators() {
        mAnimOn = ValueAnimator.ofFloat(0f, 1f);
        mAnimOn.setDuration(D0);
        mAnimOn.setInterpolator(mU0);
        mAnimOn.addUpdateListener(mUpdate);
        mAnimOn.addListener(mEndListener);
        mAnimOff = ValueAnimator.ofFloat(1f, 0f);
        mAnimOff.setDuration(D0);
        mAnimOff.setInterpolator(mU0);
        mAnimOff.addUpdateListener(mUpdate);
        mAnimOff.addListener(mEndListener);
    }

    private final ValueAnimator.AnimatorUpdateListener mUpdate =
            new ValueAnimator.AnimatorUpdateListener() {
        @Override
        public void onAnimationUpdate(ValueAnimator animation) {
            mProgress = (Float) animation.getAnimatedValue();
            invalidate();
        }
    };

    private final Animator.AnimatorListener mEndListener = new AnimatorListenerAdapter() {
        @Override
        public void onAnimationEnd(Animator animation) {
            fireEndCallback();
        }
    };

    /** h0()：动画结束才回调 onCheckedChanged；因切换/取消引发的中途 end 不回调。 */
    private void fireEndCallback() {
        if (mSuppressEndCallback) {
            return;
        }
        if (mListener != null) {
            mListener.onCheckedChanged(this, mChecked);
        }
    }

    /** H0()：触感（源码 vibratorPro(113) 的系统 API 回退），受系统触感开关控制。 */
    private void haptic() {
        try {
            if (android.provider.Settings.System.getInt(
                    getContext().getContentResolver(), "haptic_feedback_enabled", 1) == 0) {
                return;
            }
        } catch (Throwable ignored) {
        }
        performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
    }

    /** performClick L17-L24：点击切换——C0/B0 直接 start，不换插值器。 */
    private void toggle() {
        final boolean newState = !mChecked;
        mChecked = newState;
        playSoundEffect(0);
        haptic();
        if (mAnimOn == null) {
            setupAnimators();
        }
        if (newState) {
            mAnimOn.start();
        } else {
            mAnimOff.start();
        }
    }

    /** ACTION_MOVE L86/L88：拖动跨阈值——换 Overshoot(1.8) 并 setCurrentPlayTime 续播。 */
    private void dragFlipTo(boolean on) {
        if (mAnimOn == null) {
            setupAnimators();
        }
        mSuppressEndCallback = true;
        try {
            mAnimOn.cancel();
            mAnimOff.cancel();
        } finally {
            mSuppressEndCallback = false;
        }
        mChecked = on;
        if (on) {
            mAnimOn.setInterpolator(mOvershoot);
            mAnimOn.start();
            mAnimOn.setCurrentPlayTime((long) (clamp01(mProgress) * D0));
        } else {
            mAnimOff.setInterpolator(mOvershoot);
            mAnimOff.start();
            mAnimOff.setCurrentPlayTime((long) ((1f - clamp01(mProgress)) * D0));
        }
    }

    private boolean animRunning() {
        return (mAnimOn != null && mAnimOn.isRunning())
                || (mAnimOff != null && mAnimOff.isRunning());
    }

    private static float clamp01(float v) {
        return v < 0f ? 0f : v > 1f ? 1f : v;
    }

    private void cancelAnims() {
        if (mAnimOn == null) {
            return;
        }
        mSuppressEndCallback = true;
        try {
            mAnimOn.cancel();
            mAnimOff.cancel();
        } finally {
            mSuppressEndCallback = false;
        }
    }

    public void setChecked(boolean checked, boolean animate) {
        if (mChecked == checked) {
            return;
        }
        mChecked = checked;
        if (animate) {
            if (mAnimOn == null) {
                setupAnimators();
            }
            if (checked) {
                mAnimOn.start();
            } else {
                mAnimOff.start();
            }
        } else {
            cancelAnims();
            mProgress = checked ? 1f : 0f;
            invalidate();
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int w = resolveSize(OriginTheme.dpi(getContext(), 48), widthMeasureSpec);
        int h = resolveSize(OriginTheme.dpi(getContext(), 28), heightMeasureSpec);
        setMeasuredDimension(w, h);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float scale = OriginTheme.dp(getContext(), 1f);
        float cy = getHeight() / 2f;
        float p = clamp01(mProgress);
        float dim = isEnabled() ? 1f : 0.4f;

        // 1. 填充蓝条：宽 = max(J0,0.5)×TRAVEL，alpha = J0<0.5 ? J0×2 : 1（k0）
        float fillAlpha = p < 0.5f ? p * 2f : 1f;
        if (fillAlpha > 0f) {
            float fillH = TRACK_ON_H * scale;
            float r = fillH / 2f;
            mPaint.setStyle(Paint.Style.FILL);
            mPaint.setColor(mTrackOnColor);
            mPaint.setAlpha(Math.round(Color.alpha(mTrackOnColor) * fillAlpha * dim));
            float right = PAD_LEFT * scale + Math.max(p, 0.5f) * TRAVEL * scale;
            mRect.set(PAD_LEFT * scale, cy - r, right, cy + r);
            canvas.drawRoundRect(mRect, r, r, mPaint);
        }

        // 2. 描边灰条（OFF 轨道）：左端随进度右移滑出，alpha = 1-J0（k0）
        float strokeAlpha = 1f - p;
        if (strokeAlpha > 0f) {
            float trackH = TRACK_OFF_H * scale;
            float r = trackH / 2f;
            mPaint.setStyle(Paint.Style.STROKE);
            mPaint.setStrokeWidth(TRACK_OFF_STROKE * scale);
            mPaint.setColor(mTrackOffColor);
            mPaint.setAlpha(Math.round(Color.alpha(mTrackOffColor) * strokeAlpha * dim));
            float left = PAD_LEFT * scale + Math.min(p, 0.5f) * TRAVEL * scale;
            mRect.set(left, cy - r, (PAD_LEFT + 40f) * scale, cy + r);
            canvas.drawRoundRect(mRect, r, r, mPaint);
        }

        // 3. thumb：半径 8.5→10dp 生长，中心 12.5→32dp；填充 + 3dp 环（Z 通道 lerp）
        float thumbR = (THUMB_R_OFF + (THUMB_R_ON - THUMB_R_OFF) * p) * scale;
        float thumbX = (PAD_LEFT + THUMB_R_OFF) * scale
                + p * (TRAVEL - THUMB_R_OFF - THUMB_R_ON) * scale;
        int fill = lerp(mThumbOffColor, mThumbOnColor, p);
        mPaint.setStyle(Paint.Style.FILL);
        mPaint.setColor(fill);
        mPaint.setAlpha(Math.round(Color.alpha(fill) * dim));
        canvas.drawCircle(thumbX, cy, thumbR, mPaint);

        int ring = lerp(mRingOffColor, mRingOnColor, p);
        mPaint.setStyle(Paint.Style.STROKE);
        mPaint.setStrokeWidth(RING_STROKE * scale);
        mPaint.setColor(ring);
        mPaint.setAlpha(Math.round(Color.alpha(ring) * dim));
        canvas.drawCircle(thumbX, cy, thumbR, mPaint);
    }

    private static int lerp(int a, int b, float t) {
        float inv = 1 - t;
        int al = Math.round(((a >>> 24) & 0xFF) * inv + ((b >>> 24) & 0xFF) * t);
        int r = Math.round(((a >> 16) & 0xFF) * inv + ((b >> 16) & 0xFF) * t);
        int g = Math.round(((a >> 8) & 0xFF) * inv + ((b >> 8) & 0xFF) * t);
        int bl = Math.round((a & 0xFF) * inv + (b & 0xFF) * t);
        return (al << 24) | (r << 16) | (g << 8) | bl;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (!isEnabled()) {
            return false;
        }
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                mDragging = false;
                mDragStartX = event.getX();
                mDragProgressStart = clamp01(mProgress);
                getParent().requestDisallowInterceptTouchEvent(true);
                return true;
            case MotionEvent.ACTION_MOVE: {
                float dx = event.getX() - mDragStartX;
                if (!mDragging && Math.abs(dx) > OriginTheme.dp(getContext(), 4f)) {
                    mDragging = true;
                }
                if (mDragging) {
                    float travel = (TRAVEL - THUMB_R_OFF - THUMB_R_ON + THUMB_R_OFF * 2)
                            * OriginTheme.dp(getContext(), 1f);
                    if (travel <= 0) travel = 1;
                    float k = clamp01(mDragProgressStart + dx / travel);
                    if (k >= FLIP_ON && !mChecked) {
                        dragFlipTo(true);            // 跨 0.6：Overshoot 续播接管
                    } else if (k <= FLIP_OFF && mChecked) {
                        dragFlipTo(false);           // 跨 0.4
                    } else if (!animRunning()) {
                        mProgress = k;               // 未接管：thumb 跟手
                        invalidate();
                    } else if (k > FLIP_OFF && k < FLIP_ON) {
                        cancelAnims();               // 拖回中间区：交还跟手
                        mProgress = k;
                        invalidate();
                    }
                }
                return true;
            }
            case MotionEvent.ACTION_UP:
                getParent().requestDisallowInterceptTouchEvent(false);
                if (mDragging) {
                    // 阈值处已翻转；松手按当前进度回到最近端（Overshoot 续播）
                    boolean target = mProgress >= FLIP_ON ? true
                            : mProgress <= FLIP_OFF ? false : mChecked;
                    mDragging = false;
                    playSoundEffect(0);
                    dragFlipTo(target);
                } else {
                    toggle();
                }
                return true;
            case MotionEvent.ACTION_CANCEL:
                getParent().requestDisallowInterceptTouchEvent(false);
                mDragging = false;
                dragFlipTo(mChecked);
                return true;
            default:
                return super.onTouchEvent(event);
        }
    }
}
