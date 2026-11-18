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
        show(act, act.getString(titleRes), act.getString(messageRes),
                act.getString(confirmTextRes), onConfirm);
    }

    /** 动态文案版本（按应用/包名拼说明）。 */
    public static void show(Activity act, CharSequence title, CharSequence message,
                            CharSequence confirmText, final Runnable onConfirm) {
        OriginOverlays.OriginDialog dialog = new OriginOverlays.OriginDialog(act);
        dialog.build(title, message,
                act.getString(R.string.dialog_cancel), null,
                confirmText, v -> {
                    if (onConfirm != null) {
                        onConfirm.run();
                    }
                });
        dialog.show();
    }
}
