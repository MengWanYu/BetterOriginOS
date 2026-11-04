package com.vivo.systemui.statusbar.notification.stack;

import android.view.LayoutInflater;
import android.view.View;
import com.android.systemui.dagger.SysUISingleton;
import com.android.systemui.statusbar.notification.collection.render.NodeController;
import javax.inject.Inject;
import kotlin.Metadata;
import kotlin.jvm.internal.Intrinsics;

/* JADX INFO: compiled from: VivoCustomSectionController.kt */
/* JADX INFO: loaded from: classes3.dex */
@SysUISingleton
@Metadata(d1 = {"\u00006\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u0004\n\u0002\u0010\u000e\n\u0002\b\u0003\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0010\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\b\u0007\u0018\u00002\u00020\u0001B\u000f\b\u0007\u0012\u0006\u0010\u0002\u001a\u00020\u0003¢\u0006\u0002\u0010\u0004J\u000e\u0010\u0012\u001a\u00020\u00132\u0006\u0010\u0014\u001a\u00020\u0015R\"\u0010\u0007\u001a\u0004\u0018\u00010\u00062\b\u0010\u0005\u001a\u0004\u0018\u00010\u0006@BX\u0086\u000e¢\u0006\b\n\u0000\u001a\u0004\b\b\u0010\tR\u000e\u0010\u0002\u001a\u00020\u0003X\u0082\u0004¢\u0006\u0002\n\u0000R\u0014\u0010\n\u001a\u00020\u000bX\u0096D¢\u0006\b\n\u0000\u001a\u0004\b\f\u0010\rR\u0014\u0010\u000e\u001a\u00020\u000f8VX\u0096\u0004¢\u0006\u0006\u001a\u0004\b\u0010\u0010\u0011¨\u0006\u0016"}, d2 = {"Lcom/vivo/systemui/statusbar/notification/stack/VivoCustomSectionController;", "Lcom/android/systemui/statusbar/notification/collection/render/NodeController;", "layoutInflater", "Landroid/view/LayoutInflater;", "(Landroid/view/LayoutInflater;)V", "<set-?>", "Lcom/vivo/systemui/statusbar/notification/stack/VivoCustomStackSection;", "customStackSection", "getCustomStackSection", "()Lcom/vivo/systemui/statusbar/notification/stack/VivoCustomStackSection;", "nodeLabel", "", "getNodeLabel", "()Ljava/lang/String;", "view", "Landroid/view/View;", "getView", "()Landroid/view/View;", "reinflateView", "", "parent", "Landroid/view/ViewGroup;", "app_xxhdpiRatio_20_9Rom_13_5DemesticAndroid_33Release"}, k = 1, mv = {1, 6, 0}, xi = 48)
public final class VivoCustomSectionController implements NodeController {
    private VivoCustomStackSection customStackSection;
    private final LayoutInflater layoutInflater;
    private final String nodeLabel;

    @Inject
    public VivoCustomSectionController(LayoutInflater layoutInflater) {
        Intrinsics.checkNotNullParameter(layoutInflater, "layoutInflater");
        this.layoutInflater = layoutInflater;
        this.nodeLabel = "VivoCustomSection";
    }

    @Override // com.android.systemui.statusbar.notification.collection.render.NodeController
    public void addChildAt(NodeController nodeController, int i) {
        NodeController.DefaultImpls.addChildAt(this, nodeController, i);
    }

    @Override // com.android.systemui.statusbar.notification.collection.render.NodeController
    public View getChildAt(int i) {
        return NodeController.DefaultImpls.getChildAt(this, i);
    }

    @Override // com.android.systemui.statusbar.notification.collection.render.NodeController
    public int getChildCount() {
        return NodeController.DefaultImpls.getChildCount(this);
    }

    @Override // com.android.systemui.statusbar.notification.collection.render.NodeController
    public void moveChildTo(NodeController nodeController, int i) {
        NodeController.DefaultImpls.moveChildTo(this, nodeController, i);
    }

    @Override // com.android.systemui.statusbar.notification.collection.render.NodeController
    public boolean offerToKeepInParentForAnimation() {
        return NodeController.DefaultImpls.offerToKeepInParentForAnimation(this);
    }

    @Override // com.android.systemui.statusbar.notification.collection.render.NodeController
    public void onViewAdded() {
        NodeController.DefaultImpls.onViewAdded(this);
    }

    @Override // com.android.systemui.statusbar.notification.collection.render.NodeController
    public void onViewMoved() {
        NodeController.DefaultImpls.onViewMoved(this);
    }

    @Override // com.android.systemui.statusbar.notification.collection.render.NodeController
    public void onViewRemoved() {
        NodeController.DefaultImpls.onViewRemoved(this);
    }

    @Override // com.android.systemui.statusbar.notification.collection.render.NodeController
    public void removeChild(NodeController nodeController, boolean z) {
        NodeController.DefaultImpls.removeChild(this, nodeController, z);
    }

    @Override // com.android.systemui.statusbar.notification.collection.render.NodeController
    public boolean removeFromParentIfKeptForAnimation() {
        return NodeController.DefaultImpls.removeFromParentIfKeptForAnimation(this);
    }

    @Override // com.android.systemui.statusbar.notification.collection.render.NodeController
    public void resetKeepInParentForAnimation() {
        NodeController.DefaultImpls.resetKeepInParentForAnimation(this);
    }

    @Override // com.android.systemui.statusbar.notification.collection.render.NodeController
    public String getNodeLabel() {
        return this.nodeLabel;
    }

    public final VivoCustomStackSection getCustomStackSection() {
        return this.customStackSection;
    }

    /* JADX WARN: Removed duplicated region for block: B:10:0x0020  */
    /*
        Code decompiled incorrectly, please refer to instructions dump.
        To view partially-correct add '--show-bad-code' argument
    */
    public final void reinflateView(android.view.ViewGroup r6) {
        /*
            r5 = this;
            java.lang.String r0 = "parent"
            kotlin.jvm.internal.Intrinsics.checkNotNullParameter(r6, r0)
            com.vivo.systemui.statusbar.notification.stack.VivoCustomStackSection r0 = r5.customStackSection
            if (r0 == 0) goto La
            return
        La:
            r1 = -1
            if (r0 == 0) goto L20
            r0.removeFromTransientContainer()
            android.view.ViewParent r2 = r0.getParent()
            if (r2 != r6) goto L20
            android.view.View r0 = (android.view.View) r0
            int r2 = r6.indexOfChild(r0)
            r6.removeView(r0)
            goto L21
        L20:
            r2 = r1
        L21:
            android.view.LayoutInflater r0 = r5.layoutInflater
            r3 = 2131624556(0x7f0e026c, float:1.8876295E38)
            r4 = 0
            android.view.View r0 = r0.inflate(r3, r6, r4)
            if (r0 == 0) goto L3a
            com.vivo.systemui.statusbar.notification.stack.VivoCustomStackSection r0 = (com.vivo.systemui.statusbar.notification.stack.VivoCustomStackSection) r0
            if (r2 == r1) goto L37
            r1 = r0
            android.view.View r1 = (android.view.View) r1
            r6.addView(r1, r2)
        L37:
            r5.customStackSection = r0
            return
        L3a:
            java.lang.NullPointerException r5 = new java.lang.NullPointerException
            java.lang.String r6 = "null cannot be cast to non-null type com.vivo.systemui.statusbar.notification.stack.VivoCustomStackSection"
            r5.<init>(r6)
            throw r5
        */
        throw new UnsupportedOperationException("Method not decompiled: com.vivo.systemui.statusbar.notification.stack.VivoCustomSectionController.reinflateView(android.view.ViewGroup):void");
    }

    @Override // com.android.systemui.statusbar.notification.collection.render.NodeController
    public View getView() {
        VivoCustomStackSection vivoCustomStackSection = this.customStackSection;
        Intrinsics.checkNotNull(vivoCustomStackSection);
        return vivoCustomStackSection;
    }
}
