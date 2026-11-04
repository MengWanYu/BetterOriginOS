package com.android.systemui.statusbar.phone;

import android.content.Context;
import android.content.res.Configuration;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOverlay;
import android.widget.FrameLayout;
import com.android.systemui.statusbar.phone.PanelViewController;

/* JADX INFO: loaded from: classes2.dex */
public abstract class PanelView extends FrameLayout {
    public static final boolean DEBUG = false;
    public static final String TAG = PanelView.class.getSimpleName();
    protected CentralSurfaces mCentralSurfaces;
    protected HeadsUpManagerPhone mHeadsUpManager;
    protected KeyguardBottomAreaView mKeyguardBottomArea;
    private OnConfigurationChangedListener mOnConfigurationChangedListener;
    private PanelViewController.TouchHandler mTouchHandler;

    interface OnConfigurationChangedListener {
        void onConfigurationChanged(Configuration configuration);
    }

    @Override // android.widget.FrameLayout, android.view.ViewGroup
    protected /* bridge */ /* synthetic */ ViewGroup.LayoutParams generateDefaultLayoutParams() {
        return super.generateDefaultLayoutParams();
    }

    @Override // android.widget.FrameLayout, android.view.ViewGroup
    public /* bridge */ /* synthetic */ ViewGroup.LayoutParams generateLayoutParams(AttributeSet attributeSet) {
        return super.generateLayoutParams(attributeSet);
    }

    @Override // android.view.ViewGroup, android.view.View
    public /* bridge */ /* synthetic */ ViewOverlay getOverlay() {
        return super.getOverlay();
    }

    public PanelView(Context context) {
        super(context);
    }

    public PanelView(Context context, AttributeSet attributeSet) {
        super(context, attributeSet);
    }

    public PanelView(Context context, AttributeSet attributeSet, int i) {
        super(context, attributeSet, i);
    }

    public PanelView(Context context, AttributeSet attributeSet, int i, int i2) {
        super(context, attributeSet, i, i2);
    }

    public void setOnTouchListener(PanelViewController.TouchHandler touchHandler) {
        super.setOnTouchListener((View.OnTouchListener) touchHandler);
        this.mTouchHandler = touchHandler;
    }

    public void setOnConfigurationChangedListener(OnConfigurationChangedListener onConfigurationChangedListener) {
        this.mOnConfigurationChangedListener = onConfigurationChangedListener;
    }

    @Override // android.view.ViewGroup, android.view.View
    public boolean dispatchTouchEvent(MotionEvent motionEvent) {
        this.mTouchHandler.dispatchTouchEvent(motionEvent);
        return super.dispatchTouchEvent(motionEvent);
    }

    @Override // android.view.ViewGroup
    public boolean onInterceptTouchEvent(MotionEvent motionEvent) {
        return this.mTouchHandler.onInterceptTouchEvent(motionEvent);
    }

    @Override // android.view.ViewGroup, android.view.View
    public void dispatchConfigurationChanged(Configuration configuration) {
        super.dispatchConfigurationChanged(configuration);
        this.mOnConfigurationChangedListener.onConfigurationChanged(configuration);
    }
}
