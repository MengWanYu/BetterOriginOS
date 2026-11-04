package com.android.systemui.statusbar.notification.row;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Outline;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.os.SystemProperties;
import android.util.AttributeSet;
import android.util.IndentingPrintWriter;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.widget.FrameLayout;
import com.android.systemui.Dependency;
import com.android.systemui.res.R;
import com.android.systemui.statusbar.notification.RoundableState;
import com.android.systemui.statusbar.notification.shared.NotificationsImprovedHunAnimation;
import com.android.systemui.statusbar.notification.stack.NotificationChildrenContainer;
import com.android.systemui.statusbar.phone.CentralSurfaces;
import com.android.systemui.util.DumpUtilsKt;
import com.originui.core.utils.G2CornerUtil;
import com.vivo.common.utils.SystemPropertiesFacade;
import com.vivo.systemui.statusbar.notification.row.VivoNotificationDivider;
import com.vivo.systemui.statusbar.utils.FoldedStateMonitorUtils;
import com.vivo.systemui.statusbar.utils.VivoColorAndCornerPicker;
import java.io.PrintWriter;

/* JADX INFO: loaded from: classes5.dex */
public abstract class ExpandableOutlineView extends ExpandableView {
    private static final boolean DEBUG_USE_G2_CORNER;
    public static final boolean DEBUG_USE_G2_CORNER_BG;
    private static final Path EMPTY_PATH = new Path();
    private boolean mAlwaysRoundBothCorners;
    private CentralSurfaces mCentralSurfaces;
    private final Path mClipPath;
    private boolean mCustomOutline;
    protected boolean mDismissUsingRowTranslationX;
    protected VivoNotificationDivider mGroupDivider;
    private Rect mOutlinRect;
    private float mOutlineAlpha;
    private final Rect mOutlineRect;
    private final ViewOutlineProvider mProvider;
    private RoundableState mRoundableState;
    protected int mRowDividerPadding;
    private final Rect mTempRect;
    private float[] mTmpCornerRadii;
    private Path mTmpPath;

    public boolean childNeedsClipping(View view) {
        return false;
    }

    public Path getCustomClipPath(View view) {
        return null;
    }

    static {
        boolean z = SystemProperties.getBoolean("vivo.debug.noti.g2", true) && SystemPropertiesFacade.SUPPORT_G2;
        DEBUG_USE_G2_CORNER = z;
        DEBUG_USE_G2_CORNER_BG = SystemProperties.getBoolean("vivo.debug.noti.g2.bg", true) && z;
    }

    public void setGroupDivider(VivoNotificationDivider vivoNotificationDivider) {
        this.mGroupDivider = vivoNotificationDivider;
        if (vivoNotificationDivider != null) {
            vivoNotificationDivider.setSidePadding(this.mRowDividerPadding);
        }
    }

    @Override // com.android.systemui.statusbar.notification.row.ExpandableView, com.android.systemui.statusbar.notification.Roundable
    public RoundableState getRoundableState() {
        return this.mRoundableState;
    }

    @Override // com.android.systemui.statusbar.notification.row.ExpandableView, com.android.systemui.statusbar.notification.Roundable
    public int getClipHeight() {
        if (this.mCustomOutline) {
            return this.mOutlineRect.height();
        }
        return super.getClipHeight();
    }

    public Path getClipPath(boolean z) {
        return getClipPath(z, null);
    }

    public Path getClipPath(boolean z, Rect rect) {
        int iMax;
        int i;
        int width;
        int iMax2;
        float g2Radius;
        float g2Radius2;
        float maxRadius = this.mAlwaysRoundBothCorners ? getMaxRadius() : getTopCornerRadius();
        if (!this.mCustomOutline) {
            int translation = (this.mDismissUsingRowTranslationX || z) ? 0 : (int) getTranslation();
            int i2 = (int) (this.mExtraWidthForClipping / 2.0f);
            iMax = Math.max(translation, 0) - i2;
            i = this.mClipTopAmount;
            width = getWidth() + i2 + Math.min(translation, 0);
            if (!isChildInGroup() && getContext().getResources().getConfiguration().orientation == 1 && ((FoldedStateMonitorUtils) Dependency.get(FoldedStateMonitorUtils.class)).isFoldState()) {
                iMax = translation - i2;
                width = getWidth() + i2 + translation;
            }
            iMax2 = Math.max(this.mMinimumHeightForClipping, Math.max(getActualHeight() - this.mClipBottomAmount, (int) (i + maxRadius)));
        } else {
            Rect rect2 = this.mOutlineRect;
            iMax = rect2.left;
            i = rect2.top;
            width = rect2.right;
            iMax2 = rect2.bottom;
        }
        int i3 = iMax2;
        int i4 = iMax;
        int i5 = i;
        int i6 = width;
        int i7 = i3 - i5;
        if (i7 == 0) {
            return EMPTY_PATH;
        }
        float maxRadius2 = this.mAlwaysRoundBothCorners ? getMaxRadius() : getBottomCornerRadius();
        if (!NotificationsImprovedHunAnimation.isEnabled()) {
            float f = maxRadius + maxRadius2;
            float f2 = i7;
            if (f > f2) {
                float f3 = f - f2;
                float topRoundness = getTopRoundness();
                float bottomRoundness = getBottomRoundness();
                float f4 = f3 * topRoundness;
                float f5 = topRoundness + bottomRoundness;
                maxRadius -= f4 / f5;
                maxRadius2 -= (f3 * bottomRoundness) / f5;
            }
        }
        if (DEBUG_USE_G2_CORNER) {
            g2Radius = G2CornerUtil.getG2Radius(maxRadius);
            g2Radius2 = G2CornerUtil.getG2Radius(maxRadius2);
        } else {
            g2Radius = maxRadius;
            g2Radius2 = maxRadius2;
        }
        getRoundedRectPath(i4, i5, i6, i3, g2Radius, g2Radius2, this.mTmpPath);
        if (rect != null) {
            rect.set(i4, i5, i6, i3);
        }
        return this.mTmpPath;
    }

    public void getRoundedRectPath(int i, int i2, int i3, int i4, float f, float f2, Path path) {
        path.reset();
        float[] fArr = this.mTmpCornerRadii;
        fArr[0] = f;
        fArr[1] = f;
        fArr[2] = f;
        fArr[3] = f;
        fArr[4] = f2;
        fArr[5] = f2;
        fArr[6] = f2;
        fArr[7] = f2;
        path.addRoundRect(i, i2, i3, i4, fArr, Path.Direction.CW);
    }

    public ExpandableOutlineView(Context context, AttributeSet attributeSet) {
        super(context, attributeSet);
        this.mOutlineRect = new Rect();
        this.mOutlineAlpha = -1.0f;
        this.mTmpPath = new Path();
        this.mClipPath = new Path();
        this.mTempRect = new Rect();
        this.mOutlinRect = new Rect();
        this.mDismissUsingRowTranslationX = true;
        this.mTmpCornerRadii = new float[8];
        ViewOutlineProvider viewOutlineProvider = new ViewOutlineProvider() { // from class: com.android.systemui.statusbar.notification.row.ExpandableOutlineView.1
            @Override // android.view.ViewOutlineProvider
            public void getOutline(View view, Outline outline) {
                if (!ExpandableOutlineView.this.mCustomOutline && !ExpandableOutlineView.this.hasRoundedCorner() && !ExpandableOutlineView.this.mAlwaysRoundBothCorners) {
                    ExpandableOutlineView expandableOutlineView = ExpandableOutlineView.this;
                    int translation = !expandableOutlineView.mDismissUsingRowTranslationX ? (int) expandableOutlineView.getTranslation() : 0;
                    int iMax = Math.max(translation, 0);
                    ExpandableOutlineView expandableOutlineView2 = ExpandableOutlineView.this;
                    int i = expandableOutlineView2.mClipTopAmount;
                    outline.setRect(iMax, i, expandableOutlineView2.getWidth() + Math.min(translation, 0), Math.max(ExpandableOutlineView.this.getActualHeight() - ExpandableOutlineView.this.mClipBottomAmount, i));
                } else {
                    ExpandableOutlineView expandableOutlineView3 = ExpandableOutlineView.this;
                    Path clipPath = expandableOutlineView3.getClipPath(false, expandableOutlineView3.mOutlinRect);
                    if (clipPath != null) {
                        if ((ExpandableOutlineView.this.mTmpCornerRadii[0] > 0.0f || ExpandableOutlineView.this.mTmpCornerRadii[4] > 0.0f) && !clipPath.isEmpty() && ExpandableOutlineView.DEBUG_USE_G2_CORNER) {
                            if (ExpandableOutlineView.this.mTmpCornerRadii[0] != ExpandableOutlineView.this.mTmpCornerRadii[4]) {
                                if (ExpandableOutlineView.this.mTmpCornerRadii[0] == 0.0f) {
                                    ExpandableOutlineView.this.mOutlinRect.top = (int) (ExpandableOutlineView.this.mOutlinRect.top - ExpandableOutlineView.this.mTmpCornerRadii[4]);
                                } else {
                                    ExpandableOutlineView.this.mOutlinRect.bottom = (int) (ExpandableOutlineView.this.mOutlinRect.bottom - ExpandableOutlineView.this.mTmpCornerRadii[0]);
                                }
                            }
                            G2CornerUtil.setOutLineG2RoundRect(outline, ExpandableOutlineView.this.mOutlinRect.left, ExpandableOutlineView.this.mOutlinRect.top, ExpandableOutlineView.this.mOutlinRect.right, ExpandableOutlineView.this.mOutlinRect.bottom, G2CornerUtil.getG2Radius(Math.max(ExpandableOutlineView.this.mTmpCornerRadii[0], ExpandableOutlineView.this.mTmpCornerRadii[4])));
                        } else {
                            outline.setPath(clipPath);
                        }
                    }
                }
                outline.setAlpha(ExpandableOutlineView.this.mOutlineAlpha);
            }
        };
        this.mProvider = viewOutlineProvider;
        setOutlineProvider(viewOutlineProvider);
        initDimens();
    }

    @Override // android.view.ViewGroup
    public boolean drawChild(Canvas canvas, View view, long j) {
        Path customClipPath;
        canvas.save();
        Path path = null;
        if (childNeedsClipping(view)) {
            customClipPath = getCustomClipPath(view);
            if (customClipPath == null) {
                customClipPath = getClipPath(false);
            }
            if (this.mDismissUsingRowTranslationX && (view instanceof NotificationChildrenContainer)) {
                path = customClipPath;
                customClipPath = null;
            }
        } else {
            customClipPath = null;
        }
        if (view instanceof NotificationChildrenContainer) {
            ((NotificationChildrenContainer) view).setChildClipPath(path);
        }
        if (customClipPath != null) {
            float[] fArr = this.mTmpCornerRadii;
            if ((fArr[0] > 0.0f || fArr[4] > 0.0f) && !customClipPath.isEmpty() && DEBUG_USE_G2_CORNER) {
                G2CornerUtil.canvasClipG2Path(canvas, customClipPath);
            } else {
                canvas.clipPath(customClipPath);
            }
        }
        boolean zDrawChild = super.drawChild(canvas, view, j);
        canvas.restore();
        return zDrawChild;
    }

    @Override // com.android.systemui.statusbar.notification.row.ExpandableView
    public void setExtraWidthForClipping(float f) {
        super.setExtraWidthForClipping(f);
        invalidate();
        if (this instanceof ExpandableNotificationRow) {
            ExpandableNotificationRow expandableNotificationRow = (ExpandableNotificationRow) this;
            if (expandableNotificationRow.isDragFreefromShowing()) {
                expandableNotificationRow.mBackgroundNormal.invalidate();
            }
        }
    }

    @Override // com.android.systemui.statusbar.notification.row.ExpandableView
    public void setMinimumHeightForClipping(int i) {
        super.setMinimumHeightForClipping(i);
        invalidate();
    }

    public boolean isClippingNeeded() {
        return this.mAlwaysRoundBothCorners || this.mCustomOutline || ((getTranslation() > 0.0f ? 1 : (getTranslation() == 0.0f ? 0 : -1)) != 0 && !this.mDismissUsingRowTranslationX) || this.mExtraWidthForClipping != 0.0f;
    }

    private void initDimens() {
        float dimension = getResources().getDimension(R.dimen.notification_shadow_radius);
        if (((VivoColorAndCornerPicker) Dependency.get(VivoColorAndCornerPicker.class)).supportColorAndCornerPicker()) {
            dimension = VivoColorAndCornerPicker.getNotificationDefFillet(((FrameLayout) this).mContext, ((VivoColorAndCornerPicker) Dependency.get(VivoColorAndCornerPicker.class)).getFilletLevel());
        }
        int dimensionPixelSize = getResources().getDimensionPixelSize(R.dimen.vivo_notification_divider_padding);
        this.mRowDividerPadding = dimensionPixelSize;
        VivoNotificationDivider vivoNotificationDivider = this.mGroupDivider;
        if (vivoNotificationDivider != null) {
            vivoNotificationDivider.setSidePadding(dimensionPixelSize);
        }
        RoundableState roundableState = this.mRoundableState;
        if (roundableState == null) {
            this.mRoundableState = new RoundableState(this, this, dimension);
        } else {
            roundableState.setMaxRadius(dimension);
        }
        setClipToOutline(this.mAlwaysRoundBothCorners);
    }

    public void applyRoundnessAndInvalidate() {
        invalidateOutline();
        super.applyRoundnessAndInvalidate();
    }

    public void onDensityOrFontScaleChanged() {
        initDimens();
        applyRoundnessAndInvalidate();
    }

    public void onSystemFilletChanged(int i, int i2) {
        if (getMaxRadius() != i) {
            if (i == -1) {
                i = getResources().getDimensionPixelSize(R.dimen.notification_shadow_radius);
            }
            this.mRoundableState.setMaxRadius(i);
            applyRoundnessAndInvalidate();
        }
    }

    @Override // com.android.systemui.statusbar.notification.row.ExpandableView
    public void setActualHeight(int i, boolean z) {
        int actualHeight = getActualHeight();
        super.setActualHeight(i, z);
        if (actualHeight != i) {
            applyRoundnessAndInvalidate();
        }
    }

    @Override // com.android.systemui.statusbar.notification.row.ExpandableView
    public void setClipTopAmount(int i) {
        int clipTopAmount = getClipTopAmount();
        super.setClipTopAmount(i);
        if (clipTopAmount != i) {
            applyRoundnessAndInvalidate();
        }
    }

    @Override // com.android.systemui.statusbar.notification.row.ExpandableView
    public void setClipBottomAmount(int i) {
        int clipBottomAmount = getClipBottomAmount();
        super.setClipBottomAmount(i);
        if (clipBottomAmount != i) {
            applyRoundnessAndInvalidate();
        }
    }

    public void setOutlineAlpha(float f) {
        if (f != this.mOutlineAlpha) {
            this.mOutlineAlpha = f;
            applyRoundnessAndInvalidate();
        }
    }

    @Override // com.android.systemui.statusbar.notification.row.ExpandableView
    public float getOutlineAlpha() {
        return this.mOutlineAlpha;
    }

    public void setOutlineRect(RectF rectF) {
        if (rectF != null) {
            setOutlineRect(rectF.left, rectF.top, rectF.right, rectF.bottom);
        } else {
            this.mCustomOutline = false;
            applyRoundnessAndInvalidate();
        }
    }

    public void setDismissUsingRowTranslationX(boolean z) {
        this.mDismissUsingRowTranslationX = z;
    }

    @Override // com.android.systemui.statusbar.notification.row.ExpandableView
    public int getOutlineTranslation() {
        if (this.mCustomOutline) {
            return this.mOutlineRect.left;
        }
        if (this.mDismissUsingRowTranslationX) {
            return 0;
        }
        return (int) getTranslation();
    }

    public void updateOutline() {
        if (this.mCustomOutline) {
            return;
        }
        setOutlineProvider(needsOutline() ? this.mProvider : null);
    }

    public boolean needsOutline() {
        if (isChildInGroup()) {
            return isGroupExpanded() && !isGroupExpansionChanging();
        }
        if (isSummaryWithChildren()) {
            return !isGroupExpanded() || isGroupExpansionChanging();
        }
        return true;
    }

    public boolean isOutlineShowing() {
        return getOutlineProvider() != null;
    }

    public void setOutlineRect(float f, float f2, float f3, float f4) {
        this.mCustomOutline = true;
        this.mOutlineRect.set((int) f, (int) f2, (int) f3, (int) f4);
        this.mOutlineRect.bottom = (int) Math.max(f2, r6.bottom);
        this.mOutlineRect.right = (int) Math.max(f, r5.right);
        applyRoundnessAndInvalidate();
    }

    public boolean isOutlineRectChanged() {
        if (this.mOutlineRect.equals(this.mTempRect)) {
            return false;
        }
        this.mTempRect.set(this.mOutlineRect);
        return true;
    }

    @Override // com.android.systemui.statusbar.notification.row.ExpandableView, com.android.systemui.Dumpable
    public void dump(PrintWriter printWriter, String[] strArr) {
        final IndentingPrintWriter indentingPrintWriterAsIndenting = DumpUtilsKt.asIndenting(printWriter);
        super.dump(indentingPrintWriterAsIndenting, strArr);
        DumpUtilsKt.withIncreasedIndent(indentingPrintWriterAsIndenting, new Runnable() { // from class: com.android.systemui.statusbar.notification.row.ExpandableOutlineView$$ExternalSyntheticLambda0
            @Override // java.lang.Runnable
            public final void run() {
                this.f$0.lambda$dump$0(indentingPrintWriterAsIndenting);
            }
        });
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$dump$0(IndentingPrintWriter indentingPrintWriter) {
        indentingPrintWriter.println(getRoundableState().debugString());
        if (ExpandableView.DUMP_VERBOSE) {
            indentingPrintWriter.println("mCustomOutline: " + this.mCustomOutline + " mOutlineRect: " + this.mOutlineRect);
            StringBuilder sb = new StringBuilder();
            sb.append("mOutlineAlpha: ");
            sb.append(this.mOutlineAlpha);
            indentingPrintWriter.println(sb.toString());
            indentingPrintWriter.println("mAlwaysRoundBothCorners: " + this.mAlwaysRoundBothCorners);
        }
    }

    public Rect getOutLineRect() {
        if (this.mCustomOutline) {
            return this.mOutlineRect;
        }
        return null;
    }
}
