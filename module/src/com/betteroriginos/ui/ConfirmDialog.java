package com.betteroriginos.ui;

import android.app.Activity;

import com.betteroriginos.R;
import com.originui.replica.OriginOverlays;

/** OS5 风格居中确认对话框（OriginUI 复刻库 OriginDialog 实现）：圆角卡片 + 文字按钮 */
public final class ConfirmDialog {
    private ConfirmDialog() {
    }

    public static void show(Activity act, int titleRes, int messageRes,
                            int confirmTextRes, final Runnable onConfirm) {
        OriginOverlays.OriginDialog dialog = new OriginOverlays.OriginDialog(act);
        dialog.build(act.getString(titleRes), act.getString(messageRes),
                act.getString(R.string.dialog_cancel), null,
                act.getString(confirmTextRes), v -> {
                    if (onConfirm != null) {
                        onConfirm.run();
                    }
                });
        dialog.show();
    }
}
