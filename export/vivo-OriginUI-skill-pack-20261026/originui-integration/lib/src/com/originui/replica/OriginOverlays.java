package com.originui.replica;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.LinearLayout;
import android.widget.ListAdapter;
import android.widget.ListPopupWindow;
import android.widget.TextView;

/**
 * OriginOS 弹层族复刻：
 * - OriginDialog：居中圆角卡 16dp（VCustomRoundRectLayout 语义），标题 16sp bold /
 *   说明 13sp secondary / 右下文字按钮（取消 secondary / 确认 accent bold）。
 * - OriginSheet：底部弹层（TYPE_SHEET_DIALOG 模糊材质 + 顶部把手 VSheetHandleBar）。
 * - ListPopup：行内下拉（originui VListPopupWindow：圆角卡 + 勾选态）。
 */
public final class OriginOverlays {

    // ---------------- Dialog ----------------
    public static class OriginDialog extends Dialog {
        private final LinearLayout mBody;
        private final Context mContext;

        public OriginDialog(Context context) {
            super(context);
            mContext = context;
            requestWindowFeature(Window.FEATURE_NO_TITLE);
            mBody = new LinearLayout(context);
            mBody.setOrientation(LinearLayout.VERTICAL);
            GradientDrawable bg = OriginTheme.roundedRect(context,
                    OriginTheme.bgCard(context), OriginTheme.RADIUS_DIALOG);
            mBody.setBackground(bg);
            int pad = OriginTheme.dpi(context, 20);
            mBody.setPadding(pad, pad, pad, OriginTheme.dpi(context, 10));
            setContentView(mBody, new ViewGroup.LayoutParams(
                    OriginTheme.dpi(context, 300), ViewGroup.LayoutParams.WRAP_CONTENT));
            getWindow().setBackgroundDrawableResource(android.R.color.transparent);
            getWindow().setDimAmount(OriginTheme.night(context) ? 0.4f : 0.32f);
        }

        public void build(CharSequence title, CharSequence message,
                CharSequence negative, View.OnClickListener negListener,
                CharSequence positive, View.OnClickListener posListener) {
            TextView tv = new TextView(mContext);
            OriginTheme.text(tv, 16f, OriginTheme.textPrimary(mContext), OriginTheme.W_BOLD);
            tv.setText(title);
            mBody.addView(tv);

            if (message != null && message.length() > 0) {
                TextView msg = new TextView(mContext);
                OriginTheme.text(msg, 13f, OriginTheme.textSecondary(mContext), OriginTheme.W_REGULAR);
                msg.setText(message);
                msg.setLineSpacing(OriginTheme.dp(mContext, 2), 1f);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                lp.topMargin = OriginTheme.dpi(mContext, 10);
                mBody.addView(msg, lp);
            }

            LinearLayout buttons = new LinearLayout(mContext);
            buttons.setOrientation(LinearLayout.HORIZONTAL);
            buttons.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
            LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            blp.topMargin = OriginTheme.dpi(mContext, 18);
            mBody.addView(buttons, blp);

            if (negative != null) {
                buttons.addView(textButton(negative, OriginTheme.textSecondary(mContext),
                        OriginTheme.W_REGULAR, negListener));
            }
            if (positive != null) {
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                lp.leftMargin = OriginTheme.dpi(mContext, 20);
                View b = textButton(positive, OriginTheme.accent(mContext),
                        OriginTheme.W_BOLD, posListener);
                b.setLayoutParams(lp);
                buttons.addView(b);
            }
        }

        private TextView textButton(CharSequence label, int color, int weight,
                final View.OnClickListener listener) {
            TextView tv = new TextView(mContext);
            OriginTheme.text(tv, 14f, color, weight);
            tv.setText(label);
            tv.setPadding(OriginTheme.dpi(mContext, 12), OriginTheme.dpi(mContext, 10),
                    OriginTheme.dpi(mContext, 12), OriginTheme.dpi(mContext, 10));
            tv.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    dismiss();
                    if (listener != null) {
                        listener.onClick(v);
                    }
                }
            });
            return tv;
        }
    }

    // ---------------- Bottom Sheet（模糊） ----------------
    public static class OriginSheet extends Dialog {
        private final LinearLayout mBody;

        public OriginSheet(final Activity activity) {
            super(activity);
            requestWindowFeature(Window.FEATURE_NO_TITLE);
            Context c = activity;
            mBody = new LinearLayout(c);
            mBody.setOrientation(LinearLayout.VERTICAL);

            // 把手（VSheetHandleBar：居中 32x4dp 圆角）
            View handle = new View(c);
            GradientDrawable hgd = OriginTheme.roundedRect(c, OriginTheme.divider(c), 2f);
            handle.setBackground(hgd);
            LinearLayout.LayoutParams hlp = new LinearLayout.LayoutParams(
                    OriginTheme.dpi(c, 32), OriginTheme.dpi(c, 4));
            hlp.gravity = Gravity.CENTER_HORIZONTAL;
            hlp.topMargin = OriginTheme.dpi(c, 10);
            hlp.bottomMargin = OriginTheme.dpi(c, 6);
            mBody.addView(handle, hlp);

            // 模糊容器：sheet 场景下模糊是窗口级的，App 内近似用 blur layout 失败则纯色
            final OriginBlurLayout blur = new OriginBlurLayout(c);
            blur.setBlurType(OriginBlur.SHEET_DIALOG);
            blur.setOrientation(LinearLayout.VERTICAL);
            int pad = OriginTheme.dpi(c, 20);
            blur.setPadding(pad, pad, pad, pad);
            int pageBg = OriginTheme.night(c) ? 0xE6252525 : 0xE6F7F7FA;
            blur.setBackground(OriginTheme.roundedRect(c, pageBg, 24f));
            blur.addView(mBody, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

            setContentView(blur, new ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            getWindow().setBackgroundDrawableResource(android.R.color.transparent);
            getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            getWindow().setGravity(Gravity.BOTTOM);
            getWindow().setWindowAnimations(-1);
        }

        public LinearLayout body() {
            return mBody;
        }
    }

    // ---------------- 行内下拉 ----------------
    public static class OriginListPopup extends ListPopupWindow {
        public OriginListPopup(Context context, View anchor, ListAdapter adapter,
                final OnItemClickListener listener) {
            super(context);
            setContentWidth(OriginTheme.dpi(context, 260));
            setAnchorView(anchor);
            setAdapter(adapter);
            setModal(true);
            GradientDrawable bg = OriginTheme.roundedRect(context,
                    OriginTheme.bgCard(context), OriginTheme.RADIUS_CARD);
            setBackgroundDrawable(bg);
            setVerticalOffset(OriginTheme.dpi(context, 6));
            setOnItemClickListener(new android.widget.AdapterView.OnItemClickListener() {
                @Override
                public void onItemClick(android.widget.AdapterView<?> parent, View view,
                        int position, long id) {
                    dismiss();
                    if (listener != null) {
                        listener.onItemClick(parent, view, position, id);
                    }
                }
            });
        }

        public interface OnItemClickListener {
            void onItemClick(android.widget.AdapterView<?> parent, View view, int position, long id);
        }
    }

    private OriginOverlays() {
    }
}
