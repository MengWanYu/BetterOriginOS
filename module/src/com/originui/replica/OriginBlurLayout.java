package com.originui.replica;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;

import java.util.HashMap;
import java.util.Map;

/**
 * com.originui.core.blur.VBlurLinearLayout 的 1:1 复刻（docs/01-blur.md §5）。
 *
 * 行为对齐原实现：
 * - onAttachedToWindow 记录子 View 背景 → setMaterial；成功：清自身背景 + 清子 View 背景
 *   （setBackgroundTintList 透明）；失败：恢复原背景（模糊不可用时显示原色，OriginUI 同款回退）。
 * - 发丝线 divider（默认底部），alpha 跟随 blurAlpha（VBlurLinearLayout.setDividerAlpha 语义）。
 * - 夜间配置变化 → clearMaterial → 换 dark 材质重打（VBlurLinearLayout.onConfigurationChanged）。
 * - setBlurAlpha 同步 divider 与子 View tag。
 */
public class OriginBlurLayout extends LinearLayout {
    private Context mContext;
    private boolean mBlurEnabled;
    private int mOriginType = OriginBlur.TOOLBAR;
    private float mBlurAlpha = 1f;
    private boolean mApplied;
    private final Map<View, Drawable> mChildBackgrounds = new HashMap<View, Drawable>();
    private int mLastUiMode = -1;

    // divider（rom15 token）
    private int mDividerColor;
    private int mDividerHeight;
    private final Paint mDividerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private boolean mDividerBottom = true;
    private float mDividerAlpha = 0f;

    public OriginBlurLayout(Context context) {
        this(context, null);
    }

    public OriginBlurLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
        mContext = context;
        mBlurEnabled = OriginBlur.isSupported(context);
        mLastUiMode = context.getResources().getConfiguration().uiMode;
        initDivider();
    }

    private void initDivider() {
        mDividerColor = OriginTheme.divider(mContext);
        mDividerHeight = OriginTheme.dpi(mContext, 1);
        mDividerPaint.setStrokeWidth(mDividerHeight);
        mDividerPaint.setStyle(Paint.Style.FILL);
        mDividerPaint.setColor(mDividerColor);
        mDividerPaint.setAlpha(0);
    }

    /** 设置 OriginUI 语义材质类型（默认 TOOLBAR）。 */
    public void setBlurType(int originType) {
        mOriginType = originType;
        applyBlur();
    }

    public void setBlurEnabled(boolean enabled) {
        mBlurEnabled = enabled;
        applyBlur();
    }

    public void setBlurAlpha(float alpha) {
        mBlurAlpha = alpha;
        setDividerAlpha(alpha);
        if (mApplied) {
            OriginBlur.setAlpha(this, alpha);
        }
    }

    public void setDividerBottom(boolean bottom) {
        mDividerBottom = bottom;
        invalidate();
    }

    public void setDividerColor(int color) {
        mDividerColor = color;
        mDividerPaint.setColor(color);
        invalidate();
    }

    public void setDividerAlpha(float alpha) {
        float clamped = Math.min(1f, Math.max(alpha, 0f));
        if (clamped == mDividerAlpha) return;
        mDividerAlpha = clamped;
        mDividerPaint.setAlpha((int) (clamped * Color.alpha(mDividerColor)));
        invalidate();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        mChildBackgrounds.clear();
        for (int i = 0; i < getChildCount(); i++) {
            View child = getChildAt(i);
            mChildBackgrounds.put(child, child.getBackground());
        }
        applyBlur();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        mChildBackgrounds.clear();
    }

    @Override
    protected void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        int uiMode = newConfig.uiMode;
        if (uiMode != mLastUiMode) {
            mLastUiMode = uiMode;
            mDividerColor = OriginTheme.divider(mContext);
            mDividerPaint.setColor(mDividerColor);
            mDividerPaint.setAlpha((int) (mDividerAlpha * Color.alpha(mDividerColor)));
            OriginBlur.clear(this);
            applyBlur();
        }
    }

    private void applyBlur() {
        if (!mBlurEnabled || !OriginBlur.isSupported(mContext)) {
            restoreChildren();
            return;
        }
        boolean ok = OriginBlur.apply(this, mOriginType, OriginTheme.night(mContext));
        mApplied = ok;
        if (ok) {
            // 模糊成功：清自身背景（模糊层即背景），清子 View 背景避免盖住模糊
            setBackground(new ColorDrawable(Color.TRANSPARENT));
            OriginBlur.setAlpha(this, mBlurAlpha);
            for (int i = 0; i < getChildCount(); i++) {
                View child = getChildAt(i);
                if (child.getBackground() instanceof ColorDrawable
                        && ((ColorDrawable) child.getBackground()).getColor() != Color.TRANSPARENT) {
                    mChildBackgrounds.put(child, child.getBackground());
                }
                child.setBackground(null);
            }
        } else {
            restoreChildren();
        }
    }

    private void restoreChildren() {
        mApplied = false;
        for (Map.Entry<View, Drawable> e : mChildBackgrounds.entrySet()) {
            View v = e.getKey();
            if (v.getParent() == this) {
                v.setBackground(e.getValue());
            }
        }
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        super.dispatchDraw(canvas);
        if (mDividerAlpha > 0f && mDividerHeight > 0) {
            float y = mDividerBottom ? getHeight() - mDividerHeight : 0f;
            canvas.drawRect(0, y, getWidth(), y + mDividerHeight, mDividerPaint);
        }
    }
}
