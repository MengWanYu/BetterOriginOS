package com.vivo.systemui.statusbar.notification.stack;

import android.animation.AnimatorListenerAdapter;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.os.Bundle;
import android.util.AttributeSet;
import android.view.View;
import com.android.systemui.Dependency;
import com.android.systemui.R;
import com.android.systemui.plugins.PluginListener;
import com.android.systemui.plugins.vivo.VivoPanelStackSection;
import com.android.systemui.statusbar.notification.row.StackScrollerDecorView;
import com.vivo.common.utils.LogUtils;
import com.vivo.systemui.statusbar.notification.stack.superx.SuperXServer;
import com.vivo.systemui.vivoqscenter.animation.GridSpringDisplayable;
import java.util.Map;

/* JADX INFO: loaded from: classes3.dex */
public class VivoCustomStackSection extends StackScrollerDecorView implements PluginListener<VivoPanelStackSection>, GridSpringDisplayable {
    private boolean DEBUG;
    private VivoSectionContainer mContainer;
    private Paint mDebugPaint;
    private float mParentSpringTransY;
    private float mRealTransY;
    private Rect mRectForClip;
    private int mSpringLocationX;
    private int mSpringLocationY;
    private float mSpringTransY;
    private String mTag;
    VivoPanelStackSection plugin;

    @Override // com.android.systemui.statusbar.notification.row.StackScrollerDecorView
    protected View findSecondaryView() {
        return null;
    }

    @Override // com.vivo.systemui.vivoqscenter.animation.GridSpringDisplayable
    public int getSizeHeight() {
        return 1;
    }

    @Override // com.vivo.systemui.vivoqscenter.animation.GridSpringDisplayable
    public int getSizeWidth() {
        return 1;
    }

    @Override // com.android.systemui.statusbar.notification.row.StackScrollerDecorView, com.android.systemui.statusbar.notification.row.ExpandableView
    public boolean needsClippingToShelf() {
        return true;
    }

    @Override // com.android.systemui.plugins.PluginListener
    public void onPluginDisconnected(VivoPanelStackSection vivoPanelStackSection) {
    }

    @Override // com.android.systemui.statusbar.notification.row.StackScrollerDecorView, com.android.systemui.statusbar.notification.row.ExpandableView
    public long performRemoveAnimation(long j, long j2, float f, boolean z, float f2, Runnable runnable, AnimatorListenerAdapter animatorListenerAdapter) {
        return 0L;
    }

    @Override // com.vivo.systemui.vivoqscenter.animation.GridSpringDisplayable
    public void setTranslationHorizontal(float f) {
    }

    public VivoCustomStackSection(Context context, AttributeSet attributeSet) {
        super(context, attributeSet);
        this.mTag = "VivoCustomStackSection";
        this.DEBUG = false;
        this.mRectForClip = new Rect();
        setWillNotDraw(!this.DEBUG);
        if (this.DEBUG) {
            Paint paint = new Paint();
            this.mDebugPaint = paint;
            paint.setColor(-65536);
            this.mDebugPaint.setStrokeWidth(2.0f);
            this.mDebugPaint.setStyle(Paint.Style.STROKE);
            this.mDebugPaint.setTextSize(25.0f);
        }
    }

    @Override // android.view.View
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (this.DEBUG) {
            float f = 0;
            canvas.drawLine(0.0f, f, getWidth(), f, this.mDebugPaint);
            float intrinsicHeight = getIntrinsicHeight();
            canvas.drawLine(0.0f, intrinsicHeight, getWidth(), intrinsicHeight, this.mDebugPaint);
        }
    }

    @Override // com.android.systemui.statusbar.notification.row.StackScrollerDecorView, android.view.View
    protected void onFinishInflate() {
        super.onFinishInflate();
        this.mContainer = (VivoSectionContainer) findViewById(R.id.section_container);
        ((VivoCustomSectionManager) Dependency.get(VivoCustomSectionManager.class)).put(this);
        setVisible(true, false);
        SuperXServer.getInstance(this.mContext).addSuperXProcessCallback(new SuperXServer.SuperXProcessCallback() { // from class: com.vivo.systemui.statusbar.notification.stack.VivoCustomStackSection.1
            @Override // com.vivo.systemui.statusbar.notification.stack.superx.SuperXServer.SuperXProcessCallback
            public void onProcess(Map<String, String> map, byte[] bArr, Bundle bundle) {
                if (VivoCustomStackSection.this.plugin != null) {
                    VivoCustomStackSection.this.plugin.processSuperXData(map, bArr, bundle);
                }
            }

            @Override // com.vivo.systemui.statusbar.notification.stack.superx.SuperXServer.SuperXProcessCallback
            public void onQueryData(String str) {
                if (VivoCustomStackSection.this.plugin != null) {
                    VivoCustomStackSection.this.plugin.onQuerySuperXData(str);
                }
            }

            @Override // com.vivo.systemui.statusbar.notification.stack.superx.SuperXServer.SuperXProcessCallback
            public void onSuperXSettingsChanged(String str, String str2) {
                if (VivoCustomStackSection.this.plugin != null) {
                    VivoCustomStackSection.this.plugin.onSuperXSettingsChanged(str, str2);
                }
            }
        });
        LogUtils.d(this.mTag, "load complete");
    }

    public String getSectionTag() {
        return this.mContainer.getSectionTag();
    }

    public void reportVisibleToUser(boolean z, int i) {
        this.mContainer.reportVisibleToUser(z, i);
    }

    @Override // com.android.systemui.statusbar.notification.row.StackScrollerDecorView
    protected View findContentView() {
        return findViewById(R.id.custom_section);
    }

    @Override // com.android.systemui.statusbar.notification.row.ExpandableView
    protected boolean shouldClipToActualHeight() {
        return this.mContainer.shouldClipToActualHeight();
    }

    @Override // com.android.systemui.statusbar.notification.row.ExpandableView
    protected void updateClipping() {
        if (shouldClipToActualHeight()) {
            super.updateClipping();
        } else if (this.mClipTopAmount > 0) {
            this.mRectForClip.set(0, this.mClipTopAmount, getWidth(), Math.max(getHeight(), this.mClipTopAmount));
            setClipBounds(this.mRectForClip);
        } else {
            setClipBounds(null);
        }
    }

    @Override // com.android.systemui.statusbar.notification.row.ExpandableView
    public int getIntrinsicHeight() {
        return this.mContainer.getIntrinsicHeight();
    }

    public boolean handleEvent() {
        return this.mContainer.handleMotionEvent();
    }

    public boolean allowEmptyShade() {
        return this.mContainer.allowEmptyShade();
    }

    public void setSpringLocation(int i, int i2) {
        this.mSpringLocationX = i;
        this.mSpringLocationY = i2;
    }

    public void setParentSpringTransY(float f) {
        this.mParentSpringTransY = f;
        setTranslationY(this.mRealTransY);
    }

    @Override // android.view.View
    public void setTranslationY(float f) {
        this.mRealTransY = f;
        super.setTranslationY((f + this.mSpringTransY) - this.mParentSpringTransY);
    }

    @Override // android.view.View
    public float getTranslationY() {
        return (super.getTranslationY() - this.mSpringTransY) + this.mParentSpringTransY;
    }

    @Override // com.android.systemui.plugins.PluginListener
    public void onPluginConnected(VivoPanelStackSection vivoPanelStackSection, Context context) {
        this.plugin = vivoPanelStackSection;
        this.mContainer.setPluginView(vivoPanelStackSection, new VivoPanelStackSection.IVivoSectionCallback() { // from class: com.vivo.systemui.statusbar.notification.stack.VivoCustomStackSection.2
            @Override // com.android.systemui.plugins.vivo.VivoPanelStackSection.IVivoSectionCallback
            public void notifyHeightChanged(boolean z) {
                VivoCustomStackSection.this.notifyHeightChanged(z);
                ((VivoCustomSectionManager) Dependency.get(VivoCustomSectionManager.class)).dispatchSectionHeightChanged(VivoCustomStackSection.this.getSectionTag());
            }
        });
    }

    @Override // com.vivo.systemui.vivoqscenter.animation.GridSpringDisplayable
    public int getLocationX() {
        return this.mSpringLocationX;
    }

    @Override // com.vivo.systemui.vivoqscenter.animation.GridSpringDisplayable
    public int getLocationY() {
        return this.mSpringLocationY;
    }

    @Override // com.vivo.systemui.vivoqscenter.animation.GridSpringDisplayable
    public void setTranslationVertical(float f) {
        this.mSpringTransY = f;
        setTranslationY(this.mRealTransY);
    }
}
