package com.android.systemui.statusbar.phone;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.util.AttributeSet;
import android.view.View;
import com.android.systemui.Dependency;
import com.android.systemui.R;
import com.vivo.systemui.statusbar.utils.StartActAfterCollapseHelper;

/* JADX INFO: loaded from: classes2.dex */
public class NotificationPanelView extends PanelView {
    static final String COUNTER_PANEL_OPEN = "panel_open";
    static final String COUNTER_PANEL_OPEN_QS = "panel_open_qs";
    private static final boolean DEBUG = false;
    public static final int FLING_EXPAND = 0;
    private final Paint mAlphaPaint;
    private int mCurrentPanelAlpha;
    private boolean mDozing;
    private RtlChangeListener mRtlChangeListener;

    interface RtlChangeListener {
        void onRtlPropertielsChanged(int i);
    }

    @Override // android.widget.FrameLayout, android.view.ViewGroup
    public boolean shouldDelayChildPressedState() {
        return true;
    }

    public NotificationPanelView(Context context, AttributeSet attributeSet) {
        super(context, attributeSet);
        this.mAlphaPaint = new Paint();
        setWillNotDraw(true);
        this.mAlphaPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.MULTIPLY));
        setBackgroundColor(0);
    }

    @Override // android.view.View
    public void onRtlPropertiesChanged(int i) {
        RtlChangeListener rtlChangeListener = this.mRtlChangeListener;
        if (rtlChangeListener != null) {
            rtlChangeListener.onRtlPropertielsChanged(i);
        }
    }

    @Override // android.view.ViewGroup, android.view.View
    protected void dispatchDraw(Canvas canvas) {
        super.dispatchDraw(canvas);
        if (this.mCurrentPanelAlpha != 255) {
            canvas.setNightMode(0);
            canvas.drawRect(0.0f, 0.0f, canvas.getWidth(), canvas.getHeight(), this.mAlphaPaint);
        }
    }

    float getCurrentPanelAlpha() {
        return this.mCurrentPanelAlpha;
    }

    void setPanelAlphaInternal(float f) {
        int i = (int) f;
        this.mCurrentPanelAlpha = i;
        this.mAlphaPaint.setARGB(i, 255, 255, 255);
        invalidate();
    }

    public void setDozing(boolean z) {
        this.mDozing = z;
    }

    @Override // android.view.View
    public boolean hasOverlappingRendering() {
        return !this.mDozing;
    }

    void setRtlChangeListener(RtlChangeListener rtlChangeListener) {
        this.mRtlChangeListener = rtlChangeListener;
    }

    public TapAgainView getTapAgainView() {
        return (TapAgainView) findViewById(R.id.shade_falsing_tap_again);
    }

    @Override // android.view.View
    protected void onVisibilityChanged(View view, int i) {
        super.onVisibilityChanged(view, i);
        if (view != this || i == 0) {
            return;
        }
        ((CentralSurfaces) Dependency.get(CentralSurfaces.class)).getPanelController().resetHandleSpringAnimationState();
        StartActAfterCollapseHelper.getInstance().runPendingTaskAndReset();
        StartActAfterCollapseHelper.getInstance().setCollapseFromRecentState(false);
    }
}
