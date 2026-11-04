package com.android.systemui.statusbar.phone;

import android.app.Fragment;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Canvas;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewOverlay;
import android.view.WindowInsets;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.constraintlayout.widget.ConstraintSet;
import com.android.systemui.R;
import com.android.systemui.fragments.FragmentHostManager;
import com.android.systemui.plugins.qs.QS;
import com.android.systemui.statusbar.notification.AboveShelfObserver;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.function.Consumer;
import java.util.function.ToIntFunction;

/* JADX INFO: loaded from: classes2.dex */
public class NotificationsQuickSettingsContainer extends ConstraintLayout implements FragmentHostManager.FragmentListener, AboveShelfObserver.HasViewAboveShelfChangedListener {
    private Consumer<Configuration> mConfigurationChangedListener;
    private ArrayList<View> mDrawingOrderedChildren;
    private final Comparator<View> mIndexComparator;
    private Consumer<WindowInsets> mInsetsChangedListener;
    private View mKeyguardStatusBar;
    private ArrayList<View> mLayoutDrawingOrder;
    private View mQSContainer;
    private Consumer<QS> mQSFragmentAttachedListener;
    private View mQSScrollView;
    private QS mQs;
    private View mQsFrame;
    private View mStackScroller;

    static /* synthetic */ void lambda$new$0(WindowInsets windowInsets) {
    }

    static /* synthetic */ void lambda$new$1(QS qs) {
    }

    static /* synthetic */ void lambda$removeOnInsetsChangedListener$2(WindowInsets windowInsets) {
    }

    static /* synthetic */ void lambda$removeQSFragmentAttachedListener$3(QS qs) {
    }

    @Override // android.view.View
    public WindowInsets onApplyWindowInsets(WindowInsets windowInsets) {
        return windowInsets;
    }

    @Override // android.view.ViewGroup, android.view.View
    public /* bridge */ /* synthetic */ ViewOverlay getOverlay() {
        return super.getOverlay();
    }

    public NotificationsQuickSettingsContainer(Context context, AttributeSet attributeSet) {
        super(context, attributeSet);
        this.mDrawingOrderedChildren = new ArrayList<>();
        this.mLayoutDrawingOrder = new ArrayList<>();
        this.mIndexComparator = Comparator.comparingInt(new ToIntFunction() { // from class: com.android.systemui.statusbar.phone.-$$Lambda$NotificationsQuickSettingsContainer$rYOLYKY9UUHboooVhy4ZToEslhI
            @Override // java.util.function.ToIntFunction
            public final int applyAsInt(Object obj) {
                return this.f$0.indexOfChild((View) obj);
            }
        });
        this.mInsetsChangedListener = new Consumer() { // from class: com.android.systemui.statusbar.phone.-$$Lambda$NotificationsQuickSettingsContainer$lCMwCbx3RVs9TumXni8LhGn-2S8
            @Override // java.util.function.Consumer
            public final void accept(Object obj) {
                NotificationsQuickSettingsContainer.lambda$new$0((WindowInsets) obj);
            }
        };
        this.mQSFragmentAttachedListener = new Consumer() { // from class: com.android.systemui.statusbar.phone.-$$Lambda$NotificationsQuickSettingsContainer$uUPDmfJ-fEHtFGpibsahg6QSzhg
            @Override // java.util.function.Consumer
            public final void accept(Object obj) {
                NotificationsQuickSettingsContainer.lambda$new$1((QS) obj);
            }
        };
    }

    @Override // android.view.View
    protected void onFinishInflate() {
        super.onFinishInflate();
        this.mQsFrame = findViewById(R.id.qs_frame);
        this.mStackScroller = findViewById(R.id.notification_stack_scroller);
        this.mKeyguardStatusBar = findViewById(R.id.keyguard_header);
    }

    /* JADX WARN: Multi-variable type inference failed */
    @Override // com.android.systemui.fragments.FragmentHostManager.FragmentListener
    public void onFragmentViewCreated(String str, Fragment fragment) {
        QS qs = (QS) fragment;
        this.mQs = qs;
        this.mQSFragmentAttachedListener.accept(qs);
        this.mQSScrollView = this.mQs.getView().findViewById(R.id.expanded_qs_scroll_view);
        this.mQSContainer = this.mQs.getView().findViewById(R.id.quick_settings_container);
    }

    @Override // com.android.systemui.statusbar.notification.AboveShelfObserver.HasViewAboveShelfChangedListener
    public void onHasViewsAboveShelfChanged(boolean z) {
        invalidate();
    }

    @Override // android.view.View
    protected void onConfigurationChanged(Configuration configuration) {
        super.onConfigurationChanged(configuration);
        Consumer<Configuration> consumer = this.mConfigurationChangedListener;
        if (consumer != null) {
            consumer.accept(configuration);
        }
    }

    public void setConfigurationChangedListener(Consumer<Configuration> consumer) {
        this.mConfigurationChangedListener = consumer;
    }

    public void setNotificationsMarginBottom(int i) {
        ConstraintLayout.LayoutParams layoutParams = (ConstraintLayout.LayoutParams) this.mStackScroller.getLayoutParams();
        layoutParams.bottomMargin = i;
        this.mStackScroller.setLayoutParams(layoutParams);
    }

    public void setQSContainerPaddingBottom(int i) {
        View view = this.mQSContainer;
        if (view != null) {
            view.setPadding(view.getPaddingLeft(), this.mQSContainer.getPaddingTop(), this.mQSContainer.getPaddingRight(), i);
        }
    }

    public void setInsetsChangedListener(Consumer<WindowInsets> consumer) {
        this.mInsetsChangedListener = consumer;
    }

    public void removeOnInsetsChangedListener() {
        this.mInsetsChangedListener = new Consumer() { // from class: com.android.systemui.statusbar.phone.-$$Lambda$NotificationsQuickSettingsContainer$0F1rIfTbath6n--Nz_M8gvXP7NU
            @Override // java.util.function.Consumer
            public final void accept(Object obj) {
                NotificationsQuickSettingsContainer.lambda$removeOnInsetsChangedListener$2((WindowInsets) obj);
            }
        };
    }

    public void setQSFragmentAttachedListener(Consumer<QS> consumer) {
        this.mQSFragmentAttachedListener = consumer;
        QS qs = this.mQs;
        if (qs != null) {
            consumer.accept(qs);
        }
    }

    public void removeQSFragmentAttachedListener() {
        this.mQSFragmentAttachedListener = new Consumer() { // from class: com.android.systemui.statusbar.phone.-$$Lambda$NotificationsQuickSettingsContainer$F_IgNAJ9mymI1pd9lhso8G24Fwo
            @Override // java.util.function.Consumer
            public final void accept(Object obj) {
                NotificationsQuickSettingsContainer.lambda$removeQSFragmentAttachedListener$3((QS) obj);
            }
        };
    }

    @Override // android.view.ViewGroup, android.view.View
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        FragmentHostManager.get(this).addTagListener(QS.TAG, this);
    }

    @Override // android.view.ViewGroup, android.view.View
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        FragmentHostManager.get(this).removeTagListener(QS.TAG, this);
    }

    @Override // androidx.constraintlayout.widget.ConstraintLayout, android.view.ViewGroup, android.view.View
    protected void dispatchDraw(Canvas canvas) {
        this.mDrawingOrderedChildren.clear();
        this.mLayoutDrawingOrder.clear();
        if (this.mKeyguardStatusBar.getVisibility() == 0) {
            this.mDrawingOrderedChildren.add(this.mKeyguardStatusBar);
            this.mLayoutDrawingOrder.add(this.mKeyguardStatusBar);
        }
        if (this.mQsFrame.getVisibility() == 0) {
            this.mDrawingOrderedChildren.add(this.mQsFrame);
            this.mLayoutDrawingOrder.add(this.mQsFrame);
        }
        if (this.mStackScroller.getVisibility() == 0) {
            this.mDrawingOrderedChildren.add(this.mStackScroller);
            this.mLayoutDrawingOrder.add(this.mStackScroller);
        }
        this.mLayoutDrawingOrder.sort(this.mIndexComparator);
        super.dispatchDraw(canvas);
    }

    @Override // android.view.ViewGroup
    protected boolean drawChild(Canvas canvas, View view, long j) {
        int iIndexOf = this.mLayoutDrawingOrder.indexOf(view);
        if (iIndexOf >= 0) {
            return super.drawChild(canvas, this.mDrawingOrderedChildren.get(iIndexOf), j);
        }
        return super.drawChild(canvas, view, j);
    }

    public void applyConstraints(ConstraintSet constraintSet) {
        constraintSet.applyTo(this);
    }
}
