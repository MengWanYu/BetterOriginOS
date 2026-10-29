package com.betteroriginos.feature.notify;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.text.format.DateUtils;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/** iOS 风格通知行（View 版）：用于 messaging/conversation 等无法原地改造的模板，也用于 app 内预览 */
public final class IosNotificationRow extends FrameLayout {
    /** 校验期逐帧打点：证明过渡期间"目标高、卡高、行高"三者同源，而不是靠肉眼看动画 */
    public static volatile boolean traceFrames;

    /** OS6 条数徽标：深色圆 + 白字（日夜一致） */
    private static final int BADGE_FILL = 0xE6000000;
    private static final int BADGE_TEXT = 0xFFFFFFFF;

    public static final String TAG_ROOT = "betteroriginos_ios_row";
    public static final String TAG_ICON = "betteroriginos_ios_row_icon";
    public static final String TAG_APP = "betteroriginos_ios_row_app";
    public static final String TAG_TIME = "betteroriginos_ios_row_time";
    public static final String TAG_TITLE = "betteroriginos_ios_row_title";
    public static final String TAG_BODY = "betteroriginos_ios_row_body";

    private final ImageView icon;
    private final TextView app;
    private final TextView time;
    private final TextView title;
    private final TextView body;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private long postTime;
    private boolean ticker;
    private float appliedFontScale = -1f;

    /** 折叠堆叠（OS6 实测）：露边在卡片下方，每层下移 14dp、左右各内缩 13dp，条数徽标压在图标右上角 */
    private final Paint stackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stackTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF stackRect = new RectF();
    private LinearLayout rowContainer;
    private int stackBars;
    private int stackPeekPx;
    private int stackCardColor;
    private int stackTotal;
    private boolean paintCard;

    /** 展开态由卡片自己渲染子行列表：AOSP 侧永不展开，杜绝"半展开"混合态 */
    private LinearLayout childList;
    private boolean stackExpanded;
    private final java.util.List<TextView[]> childViews = new java.util.ArrayList<>();
    private java.util.List<StackChild> stackChildren = java.util.Collections.emptyList();
    /** 展开进度：0=折叠，1=展开；只在过渡中取中间值，行高据此插值 */
    private float expandT;
    private android.animation.ValueAnimator animator;
    /** 展开态每条子通知各自一枚胶囊；胶囊底由卡面统一画，保证与折叠卡同宽 */
    private final Paint capsulePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private int childGapPx;
    private boolean expandedLogged;
    /** 正面卡（组内最新一条）的动作按钮：展开态挂在第一枚胶囊底部 */
    private LinearLayout frontActions;
    /** 列表最后一行额外高度：透明留白，让最后一条能完整滚出屏幕底边 */
    private int bottomExtraPx;

    /** 组内一条子通知的呈现数据 */
    public static final class StackChild {
        public Drawable icon;
        public CharSequence title;
        public CharSequence body;
        public CharSequence time;
        /** 点击整条胶囊：走该子通知自己的 contentIntent（不是组摘要的） */
        public View.OnClickListener click;
        public View.OnLongClickListener longPress;
        /** 展开态左下角动作按钮（iOS 蓝色文字按钮） */
        public java.util.List<Action> actions;
    }

    /** 单条子通知的动作按钮：文案 + 点击时发送该动作的 PendingIntent */
    public static final class Action {
        public CharSequence title;
        public Runnable run;
    }

    /** iOS 通知动作的强调蓝（日夜一致） */
    private static final int ACTION_BLUE = 0xFF0A84FF;

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            if (!ticker) {
                return;
            }
            time.setText(relative(postTime));
            handler.postDelayed(this, 60000);
        }
    };

    public IosNotificationRow(Context ctx) {
        super(ctx);
        setTag(TAG_ROOT);
        setClipChildren(false);
        setClipToPadding(false);
        setPadding(NotifyRowSpec.dp(ctx, NotifyRowSpec.GUTTER_DP),
                NotifyRowSpec.dp(ctx, 8),
                NotifyRowSpec.dp(ctx, 16),
                NotifyRowSpec.dp(ctx, 8));

        LinearLayout row = new LinearLayout(ctx);
        rowContainer = row;
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);

        icon = new ImageView(ctx);
        icon.setTag(TAG_ICON);
        icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
        icon.setClipToOutline(true);
        GradientDrawable shape = new GradientDrawable();
        shape.setCornerRadius(NotifyRowSpec.dp(ctx, NotifyRowSpec.ICON_CORNER_DP));
        shape.setColor(0);
        icon.setBackground(shape);
        icon.setClickable(true);
        icon.setFocusable(false);
        icon.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(
                NotifyRowSpec.dp(ctx, NotifyRowSpec.ICON_DP),
                NotifyRowSpec.dp(ctx, NotifyRowSpec.ICON_DP));
        ilp.setMarginEnd(NotifyRowSpec.dp(ctx, NotifyRowSpec.ICON_GAP_DP));
        row.addView(icon, ilp);

        LinearLayout texts = new LinearLayout(ctx);
        texts.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(0,
                LayoutParams.WRAP_CONTENT, 1f);
        row.addView(texts, tlp);

        LinearLayout meta = new LinearLayout(ctx);
        meta.setOrientation(LinearLayout.HORIZONTAL);
        meta.setGravity(Gravity.CENTER_VERTICAL);
        meta.setMinimumHeight(NotifyRowSpec.dp(ctx, 18));
        app = new TextView(ctx);
        app.setTag(TAG_APP);
        app.setTextSize(TypedValue.COMPLEX_UNIT_SP, NotifyRowSpec.APP_TEXT_SP);
        app.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        app.setSingleLine(true);
        app.setEllipsize(TextUtils.TruncateAt.END);
        meta.addView(app, new LinearLayout.LayoutParams(0,
                LayoutParams.WRAP_CONTENT, 1f));
        time = new TextView(ctx);
        time.setTag(TAG_TIME);
        time.setTextSize(TypedValue.COMPLEX_UNIT_SP, NotifyRowSpec.APP_TEXT_SP);
        time.setSingleLine(true);
        time.setGravity(Gravity.END);
        LinearLayout.LayoutParams timeLp = new LinearLayout.LayoutParams(
                LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        timeLp.setMarginStart(NotifyRowSpec.dp(ctx, 8));
        meta.addView(time, timeLp);
        texts.addView(meta, new LinearLayout.LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

        title = new TextView(ctx);
        title.setTag(TAG_TITLE);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, NotifyRowSpec.TITLE_TEXT_SP);
        title.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        title.setSingleLine(false);
        title.setMaxLines(NotifyRowSpec.TITLE_MAX_LINES);
        title.setEllipsize(TextUtils.TruncateAt.END);
        title.setHorizontallyScrolling(false);
        texts.addView(title, new LinearLayout.LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

        body = new TextView(ctx);
        body.setTag(TAG_BODY);
        body.setTextSize(TypedValue.COMPLEX_UNIT_SP, NotifyRowSpec.BODY_TEXT_SP);
        body.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
        body.setSingleLine(false);
        body.setMaxLines(NotifyRowSpec.BODY_MAX_LINES);
        body.setEllipsize(TextUtils.TruncateAt.END);
        body.setHorizontallyScrolling(false);
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        blp.topMargin = NotifyRowSpec.dp(ctx, NotifyRowSpec.TITLE_BODY_GAP_DP);
        texts.addView(body, blp);

        addView(row, new FrameLayout.LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        applyTheme(NotifyRowSpec.night(ctx));
    }

    public void bind(Drawable iconDrawable, CharSequence appName, CharSequence timeText,
                     CharSequence titleText, CharSequence bodyText, boolean night,
                     View.OnClickListener iconClick, View.OnLongClickListener iconLongClick) {
        icon.setImageDrawable(iconDrawable);
        icon.setContentDescription(null);
        icon.setAlpha(1f);
        icon.setOnClickListener(iconClick);
        icon.setOnLongClickListener(iconLongClick);
        app.setText(appName == null ? "" : appName.toString());
        time.setText(timeText == null ? "" : timeText.toString());
        title.setText(titleText == null ? "" : titleText.toString());
        body.setText(bodyText == null ? "" : bodyText.toString());
        title.setVisibility(titleText == null || titleText.length() == 0
                ? View.GONE : View.VISIBLE);
        body.setVisibility(bodyText == null || bodyText.length() == 0
                ? View.GONE : View.VISIBLE);
        applyTheme(night);
    }

    public void applyTheme(boolean night) {
        int primary = night ? NotifyRowSpec.DARK_PRIMARY : NotifyRowSpec.LIGHT_PRIMARY;
        int secondary = night ? NotifyRowSpec.DARK_SECONDARY : NotifyRowSpec.LIGHT_SECONDARY;
        if (app.getCurrentTextColor() != secondary) {
            app.setTextColor(secondary);
        }
        if (time.getCurrentTextColor() != secondary) {
            time.setTextColor(secondary);
        }
        if (title.getCurrentTextColor() != primary) {
            title.setTextColor(primary);
        }
        if (body.getCurrentTextColor() != primary) {
            body.setTextColor(primary);
        }
        if (!childViews.isEmpty()) {
            applyChildTheme(night);
        }
        if (chevronDrawable != null) {
            chevronDrawable.setColor(night ? NotifyRowSpec.DARK_SECONDARY
                    : NotifyRowSpec.LIGHT_SECONDARY);
        }
    }

    public void applyTextScale() {
        float scale = getResources().getConfiguration().fontScale;
        if (scale == appliedFontScale) {
            return;
        }
        appliedFontScale = scale;
        app.setTextSize(TypedValue.COMPLEX_UNIT_SP, NotifyRowSpec.APP_TEXT_SP);
        time.setTextSize(TypedValue.COMPLEX_UNIT_SP, NotifyRowSpec.APP_TEXT_SP);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, NotifyRowSpec.TITLE_TEXT_SP);
        body.setTextSize(TypedValue.COMPLEX_UNIT_SP, NotifyRowSpec.BODY_TEXT_SP);
    }

    /** 正面卡的动作按钮（与列表胶囊同一套样式） */
    public void setFrontActions(java.util.List<Action> actions) {
        if ((actions == null || actions.isEmpty()) && frontActions == null) {
            return;
        }
        LinearLayout row = ensureFrontActions();
        row.removeAllViews();
        if (actions != null) {
            for (final Action a : actions) {
                if (a == null || TextUtils.isEmpty(a.title)) {
                    continue;
                }
                TextView ab = new TextView(getContext());
                ab.setText(a.title);
                ab.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f);
                ab.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
                ab.setTextColor(ACTION_BLUE);
                ab.setGravity(Gravity.CENTER);
                ab.setPadding(NotifyRowSpec.dp(getContext(), 10),
                        NotifyRowSpec.dp(getContext(), 8),
                        NotifyRowSpec.dp(getContext(), 10),
                        NotifyRowSpec.dp(getContext(), 8));
                ab.setClickable(true);
                if (a.run != null) {
                    ab.setOnClickListener(v -> a.run.run());
                }
                row.addView(ab, new LinearLayout.LayoutParams(
                        0, LayoutParams.WRAP_CONTENT, 1f));
            }
        }
        // 收起态必须 GONE：setStackExpanded(false) 在状态未变时会提前返回，
        // 不在这里按当前展开进度决定显隐，动作行会露在卡顶（实测）
        row.setVisibility(row.getChildCount() == 0 || expandT <= 0.004f ? GONE : VISIBLE);
        requestLayout();
    }

    private LinearLayout ensureFrontActions() {
        if (frontActions != null) {
            return frontActions;
        }
        Context ctx = getContext();
        LinearLayout v = new LinearLayout(ctx);
        v.setOrientation(LinearLayout.HORIZONTAL);
        v.setPadding(0, 0, 0, NotifyRowSpec.dp(ctx, 4));
        v.setVisibility(GONE);
        addView(v, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        frontActions = v;
        return v;
    }

    private int frontActionsHeight(int innerW, boolean expanded) {
        if (frontActions == null || frontActions.getVisibility() == GONE
                || frontActions.getChildCount() == 0) {
            return 0;
        }
        return expanded ? measureSelf(frontActions, innerW) : 0;
    }

    /**
     * 折叠堆叠（对齐 OS6 实测）：正面卡占满除露边带以外的区域，背后每层卡片下移 14dp、
     * 左右各内缩 13dp，只露出其底部圆角边；条数徽标压在图标右上角。
     *
     * @param bars  露边层数（OS6 最多 2）
     * @param total 组内总条数
     */
    public void setStack(int bars, int total, int cardColor) {
        Context ctx = getContext();
        stackBars = Math.max(0, Math.min(bars, NotifyRowSpec.STACK_BARS));
        stackTotal = total;
        stackPeekPx = stackBars * NotifyRowSpec.dp(ctx, NotifyRowSpec.STACK_PEEK_LAYER_DP);
        stackCardColor = cardColor;
        paintCard = true;
        int baseBottom = NotifyRowSpec.dp(ctx, 8);
        setPadding(getPaddingLeft(), getPaddingTop(),
                getPaddingRight(), baseBottom + stackPeekPx);
        // OS6 收起态是定高单行卡：多行内容会被行高裁掉，这里按单行显示并在卡区内垂直居中
        title.setMaxLines(1);
        body.setMaxLines(1);
        LayoutParams lp = (LayoutParams) rowContainer.getLayoutParams();
        lp.gravity = Gravity.CENTER_VERTICAL;
        rowContainer.setLayoutParams(lp);
        ensureChevron();
        // 箭头占住右上角，时间要给它让位（否则叠在"8分钟前"上）
        ViewGroup.LayoutParams tlp = time.getLayoutParams();
        if (tlp instanceof LinearLayout.LayoutParams) {
            int want = NotifyRowSpec.dp(ctx, NotifyRowSpec.CHEVRON_RESERVE_DP);
            if (((LinearLayout.LayoutParams) tlp).getMarginEnd() != want) {
                ((LinearLayout.LayoutParams) tlp).setMarginEnd(want);
                time.setLayoutParams(tlp);
            }
        }
        setWillNotDraw(false);
        invalidate();
    }

    /** 右上角展开/收起箭头：卡体点击留给"打开应用"，切换只走这个箭头 */
    private ImageView chevron;
    private Chevron chevronDrawable;
    private Drawable nativeChevron;
    private boolean chevronUp;

    public void setOnExpandToggle(View.OnClickListener listener) {
        ensureChevron().setOnClickListener(listener);
    }

    /** 用行里原生展开按钮的 drawable：与系统其它箭头完全同款，比自己画的好看 */
    public void useNativeChevron(Drawable d) {
        if (d == null) {
            return;
        }
        ImageView v = (ImageView) ensureChevron();
        nativeChevron = d.mutate();
        chevronDrawable = null;
        v.setImageDrawable(nativeChevron);
        applyChevronDirection(false);
    }

    private View ensureChevron() {
        if (chevron != null) {
            return chevron;
        }
        Context ctx = getContext();
        chevronDrawable = new Chevron(NotifyRowSpec.secondary(ctx),
                NotifyRowSpec.dp(ctx, 2.4f));
        ImageView v = new ImageView(ctx);
        v.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        v.setImageDrawable(chevronDrawable);
        v.setClickable(true);
        v.setFocusable(false);
        v.setContentDescription("expand");
        chevron = v;
        int size = NotifyRowSpec.dp(ctx, 30);
        LayoutParams lp = new LayoutParams(size, size, Gravity.END | Gravity.TOP);
        lp.topMargin = NotifyRowSpec.dp(ctx, 2);
        addView(v, lp);
        return v;
    }

    private void applyChevronDirection(boolean animate) {
        if (chevron == null) {
            return;
        }
        if (nativeChevron != null) {
            float target = chevronUp ? 180f : 0f;
            if (animate) {
                chevron.animate().rotation(target)
                        .setDuration(220)
                        .setInterpolator(NotifyRowSpec.spring())
                        .start();
            } else {
                chevron.animate().cancel();
                chevron.setRotation(target);
            }
        } else if (chevronDrawable != null) {
            chevronDrawable.setUp(chevronUp);
        }
    }

    /** 细线箭头，方向随展开态翻转；不依赖任何图片资源 */
    private static final class Chevron extends android.graphics.drawable.Drawable {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final android.graphics.Path path = new android.graphics.Path();
        private boolean up;

        Chevron(int color, float stroke) {
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setStrokeJoin(Paint.Join.ROUND);
            paint.setStrokeWidth(stroke);
            paint.setColor(color);
        }

        void setUp(boolean value) {
            if (up != value) {
                up = value;
                invalidateSelf();
            }
        }

        void setColor(int color) {
            paint.setColor(color);
            invalidateSelf();
        }

        @Override
        public void draw(Canvas canvas) {
            int w = getBounds().width();
            int h = getBounds().height();
            float cx = w / 2f;
            float cy = h / 2f;
            float arm = Math.min(w, h) * 0.24f;
            float dip = up ? -arm * 0.62f : arm * 0.62f;
            path.reset();
            path.moveTo(cx - arm, cy - dip);
            path.lineTo(cx, cy + dip);
            path.lineTo(cx + arm, cy - dip);
            canvas.drawPath(path, paint);
        }

        @Override public void setAlpha(int alpha) { paint.setAlpha(alpha); }
        @Override public void setColorFilter(android.graphics.ColorFilter f) { paint.setColorFilter(f); }
        @Override public int getOpacity() { return android.graphics.PixelFormat.TRANSLUCENT; }
    }

    public void clearStack() {
        if (!paintCard && stackPeekPx == 0) {
            return;
        }
        stackBars = 0;
        stackTotal = 0;
        stackPeekPx = 0;
        paintCard = false;
        // 卡片可能被回收成非堆叠行：展开态与动画必须一起归零
        if (animator != null) {
            animator.cancel();
            animator = null;
        }
        expandT = 0f;
        stackExpanded = false;
        icon.setAlpha(1f);
        rowContainer.setAlpha(1f);
        rowContainer.setVisibility(VISIBLE);
        if (childList != null) {
            childList.setAlpha(1f);
            childList.setVisibility(GONE);
        }
        if (frontActions != null) {
            frontActions.setVisibility(GONE);
        }
        setClipChildren(false);
        setPadding(getPaddingLeft(), NotifyRowSpec.dp(getContext(), 8), getPaddingRight(),
                NotifyRowSpec.dp(getContext(), 8));
        title.setMaxLines(NotifyRowSpec.TITLE_MAX_LINES);
        body.setMaxLines(NotifyRowSpec.BODY_MAX_LINES);
        LayoutParams lp = (LayoutParams) rowContainer.getLayoutParams();
        lp.gravity = Gravity.TOP | Gravity.START;
        rowContainer.setLayoutParams(lp);
        setWillNotDraw(true);
        invalidate();
    }

    public boolean isStack() {
        return paintCard;
    }

    /**
     * 卡片内容自身高度：用 UNSPECIFIED 重新量一遍，不依赖父级给的尺寸。
     * StackScrollAlgorithm 会按 AOSP 缓存的行高反向把行 EXACTLY 钉死，若只读
     * getMeasuredHeight()，展开态永远被压在旧行高里（长不高 -> 展开看不见）。
     *
     * 这里刻意不做缓存：影响量高结果的量太多（文本内容、maxLines、字体缩放、露边、子行数），
     * 少算一项就会留下"行高与卡高不一致"的空洞，而重 measuring 只是一次十来个 View 的计算。
     */
    public int contentHeight(int widthPx) {
        if (widthPx <= 0) {
            return 0;
        }
        if (expandT > 0f && expandT < 1f) {
            int h = animatingHeight(widthPx);
            h += bottomExtraPx;
            // 过渡中行高就是这个插值，卡片自己也必须量成同一个值：否则父级按上一次真实
            // measure 的结果摆放，卡面会比行矮一截（实测停在 168），动画期间露出底色
            setMeasuredDimension(widthPx, h);
            return h;
        }
        measure(MeasureSpec.makeMeasureSpec(widthPx, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
        return getMeasuredHeight() + bottomExtraPx;
    }

    /** 最后一行透明留白（px）：由 NotifyRowFeature 按"是否列表最后一行"设置 */
    public void setBottomExtra(int px) {
        if (bottomExtraPx != px) {
            bottomExtraPx = px;
            requestLayout();
        }
    }

    public int bottomExtra() {
        return bottomExtraPx;
    }

    /** 过渡中的高度：两端各量一次再按进度插值（GONE 的那一侧不参与，所以必须分别量） */
    private int animatingHeight(int widthPx) {
        int innerW = widthPx - getPaddingLeft() - getPaddingRight();
        int baseBottom = NotifyRowSpec.dp(getContext(), 8);
        int topPad = NotifyRowSpec.dp(getContext(), 8);
        int rowH = measureSelf(rowContainer, innerW);
        int frontActs = frontActionsHeight(innerW, true);
        // 展开态 = 正面卡 + 正面动作 + 8dp + 其余胶囊；折叠态 = 正面卡 + 露边
        int collapsed = topPad + rowH + baseBottom + stackPeekPx;
        int expanded = rowH + frontActs + childGapPx
                + measureSelf(childList, innerW) + baseBottom;
        return Math.round(collapsed + (expanded - collapsed) * expandT);
    }

    private int measureSelf(View v, int innerW) {
        if (v == null || innerW <= 0) {
            return 0;
        }
        v.measure(MeasureSpec.makeMeasureSpec(innerW, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
        return v.getMeasuredHeight();
    }

    /** 喂入组内子通知（新->旧），供展开态自绘 */
    public void setStackChildren(java.util.List<StackChild> children) {
        stackChildren = children == null ? java.util.Collections.<StackChild>emptyList() : children;
        rebuildChildList();
    }

    public int stackChildCount() {
        return stackChildren.size();
    }

    public boolean isStackExpanded() {
        return stackExpanded;
    }

    /** 折叠 <-> 展开（立即到位，用于状态纠正而非用户手势） */
    public void setStackExpanded(boolean expanded) {
        if (stackExpanded == expanded && childList != null
                && (expanded ? expandT == 1f : expandT == 0f)) {
            return;
        }
        if (animator != null) {
            animator.cancel();
        }
        chevronUp = expanded;
        applyChevronDirection(false);
        applyExpansion(expanded, expanded ? 1f : 0f);
    }

    /**
     * 带过渡的展开/收起。高度仍走 contentHeight 这个唯一真源：过渡中它按同一进度插值，
     * 所以行与卡逐帧同源，不会重现"行比卡高"的空洞。
     */
    public void animateStackExpanded(boolean expanded) {
        ensureChildList();
        chevronUp = expanded;
        applyChevronDirection(true);
        float to = expanded ? 1f : 0f;
        stackExpanded = expanded;      // 目标态立刻生效，守卫据此判断，不会中途把动画纠正回去
        if (animator != null) {
            animator.cancel();
        }
        if (Math.abs(expandT - to) < 0.001f) {
            applyExpansion(expanded, to);
            return;
        }
        animator = android.animation.ValueAnimator.ofFloat(expandT, to);
        animator.setDuration(NotifyRowSpec.STACK_ANIM_MS);
        animator.setInterpolator(NotifyRowSpec.spring());
        final boolean target = expanded;
        animator.addUpdateListener(a -> applyExpansion(target,
                (float) a.getAnimatedValue()));
        animator.start();
    }

    /** @param open 0=完全折叠，1=完全展开 */
    private void applyExpansion(boolean expanded, float open) {
        expandT = open;
        Context ctx = getContext();
        int baseBottom = NotifyRowSpec.dp(ctx, 8);
        boolean tweening = open > 0.004f && open < 0.996f;
        // 正面卡 = 第一枚胶囊：全程原样显示，文字和图标都不换，只有背景/高度/圆角在变。
        // 旧版让正卡淡出、首条子行淡入，两套文字在过渡里必然叠影。
        rowContainer.setAlpha(1f);
        rowContainer.setVisibility(VISIBLE);
        LinearLayout list = ensureChildList();
        list.setAlpha(clamp01((open - 0.2f) * 2.5f));
        list.setVisibility(open < 0.005f ? GONE : VISIBLE);
        LayoutParams rlp = (LayoutParams) rowContainer.getLayoutParams();
        // 展开态（含 open==1 的稳态）必须保持 TOP：终点回落 CENTER_VERTICAL 会把正面卡
        // 垂直居中到整张展开卡里，直接压到下面几枚胶囊上（实测叠字就是这么来的）
        int wantGravity = open > 0.004f ? Gravity.TOP : Gravity.CENTER_VERTICAL;
        if (rlp.gravity != wantGravity) {
            rlp.gravity = wantGravity;
            rowContainer.setLayoutParams(rlp);
        }
        // 展开态首条胶囊贴卡顶，顶部内边距归零；折叠态恢复 8dp
        int topPad = open > 0.004f ? 0 : NotifyRowSpec.dp(ctx, 8);
        setPadding(getPaddingLeft(), topPad, getPaddingRight(),
                baseBottom + Math.round(stackPeekPx * (1f - open)));
        // 其余胶囊挂在正面卡下面，保持 8dp 间隙
        int width = getWidth();
        int innerW = width - getPaddingLeft() - getPaddingRight();
        int rowH = innerW > 0 ? measureSelf(rowContainer, innerW) : 0;
        // 正面卡动作按钮：展开态可见，挂在正面卡内容下方，列表整体再往下让
        if (frontActions != null) {
            boolean showActs = open > 0.004f && frontActions.getChildCount() > 0;
            frontActions.setVisibility(showActs ? VISIBLE : GONE);
            if (showActs) {
                LayoutParams falp = (LayoutParams) frontActions.getLayoutParams();
                int wantActTop = topPad + rowH;
                if (falp != null && falp.topMargin != wantActTop) {
                    falp.topMargin = wantActTop;
                    frontActions.setLayoutParams(falp);
                }
            }
        }
        int frontActs = frontActionsHeight(innerW, open > 0.004f);
        LayoutParams llp = (LayoutParams) list.getLayoutParams();
        int wantTop = open > 0.004f && rowH > 0 ? rowH + frontActs + childGapPx : 0;
        if (llp != null && llp.topMargin != wantTop) {
            llp.topMargin = wantTop;
            list.setLayoutParams(llp);
        }
        // 过渡中卡比子列表矮：裁掉溢出，别让子行画到卡外面。静止两端不需要裁
        setClipChildren(tweening);
        if (traceFrames && open >= 0.996f && !expandedLogged && childList != null) {
            expandedLogged = true;
            int n = childList.getChildCount();
            int lastBottom = -1, gapMin = -1;
            for (int i = 0; i < n; i++) {
                View it = childList.getChildAt(i);
                if (i > 0) {
                    View prev = childList.getChildAt(i - 1);
                    int gap = it.getTop() - (prev.getTop() + prev.getHeight());
                    if (gapMin < 0 || gap < gapMin) {
                        gapMin = gap;
                    }
                }
                lastBottom = it.getTop() + it.getHeight();
            }
            StringBuilder items = new StringBuilder();
            int[] sc = new int[2];
            int[] sc2 = new int[2];
            childList.getLocationOnScreen(sc2);
            for (int i = 0; i < n; i++) {
                View it = childList.getChildAt(i);
                it.getLocationOnScreen(sc);
                items.append(i == 0 ? "" : " ").append(it.getTop()).append('x')
                        .append(it.getHeight()).append('v').append(it.getVisibility())
                        .append("@").append(sc[1]);
            }
            int frontActsH = frontActionsHeight(innerW, true);
            Log.i("BetterOriginOS", "notify_row.stack expanded count=" + (n + 1)
                    + " frontVis=" + rowContainer.getVisibility()
                    + " frontH=" + (rowContainer.getHeight() + frontActsH)
                    + " frontActH=" + frontActsH
                    + " listTop=" + childList.getTop() + " listH=" + childList.getHeight()
                    + " lastBottom=" + lastBottom
                    + " gap=" + gapMin + " cardH=" + getHeight()
                    + " target=" + contentHeight(getWidth() > 0 ? getWidth() : 984)
                    + " listScreenTop=" + sc2[1] + " items=[" + items + "]");
        }
        if (open <= 0.004f) {
            expandedLogged = false;
        }
        if (traceFrames) {
            int w = getWidth() > 0 ? getWidth() : 984;
            View parent = getParent() instanceof View ? (View) getParent() : null;
            View row = parent != null && parent.getParent() instanceof View
                    ? (View) parent.getParent() : null;
            Log.i("BetterOriginOS", "notify_row.stack.frame open="
                    + String.format(java.util.Locale.US, "%.3f", open)
                    + " target=" + contentHeight(w) + " cardH=" + getHeight()
                    + " rowH=" + (row == null ? -1 : row.getHeight()));
        }
        requestLayout();
        invalidate();
    }

    private static float clamp01(float v) {
        return v < 0f ? 0f : (v > 1f ? 1f : v);
    }

    private LinearLayout ensureChildList() {
        if (childList != null) {
            return childList;
        }
        Context ctx = getContext();
        childList = new LinearLayout(ctx);
        childList.setOrientation(LinearLayout.VERTICAL);
        childList.setPadding(NotifyRowSpec.dp(ctx, NotifyRowSpec.GUTTER_DP),
                NotifyRowSpec.dp(ctx, 8),
                NotifyRowSpec.dp(ctx, 16), NotifyRowSpec.dp(ctx, 8));
        addView(childList, new LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        childList.setVisibility(GONE);
        return childList;
    }

    private void rebuildChildList() {
        LinearLayout list = ensureChildList();
        Context ctx = list.getContext();
        childViews.clear();
        list.removeAllViews();
        int n = stackChildren.size();
        int iconSize = NotifyRowSpec.dp(ctx, 30);
        childGapPx = NotifyRowSpec.dp(ctx, 8);
        // 胶囊底由卡面 onDraw 统一画（与折叠卡同宽）；列表只负责内容排布
        list.setPadding(0, 0, 0, 0);
        // 第一条就是正面卡本身（rowContainer 已绑定它的内容）：展开时它原地变成
        // 第一枚胶囊，文字/图标完全不动，也就不存在交叉淡入的重影。
        int i = n > 1 ? 1 : n;
        for (; i < n; i++) {
            StackChild c = stackChildren.get(i);
            // 胶囊 = 垂直容器：第一行是图标/标题/时间，下面（展开态）挂动作按钮
            LinearLayout item = new LinearLayout(ctx);
            item.setOrientation(LinearLayout.VERTICAL);
            item.setPadding(0, NotifyRowSpec.dp(ctx, 10), 0, NotifyRowSpec.dp(ctx, 10));
            if (c.click != null) {
                item.setOnClickListener(c.click);
                item.setClickable(true);
            }
            if (c.longPress != null) {
                item.setOnLongClickListener(c.longPress);
            }
            LinearLayout head = new LinearLayout(ctx);
            head.setOrientation(LinearLayout.HORIZONTAL);
            head.setGravity(Gravity.CENTER_VERTICAL);
            item.addView(head, new LinearLayout.LayoutParams(
                    LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

            ImageView iv = new ImageView(ctx);
            iv.setImageDrawable(c.icon);
            iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
            iv.setClipToOutline(true);
            GradientDrawable iconBg = new GradientDrawable();
            iconBg.setCornerRadius(NotifyRowSpec.dp(ctx, 8));
            iconBg.setColor(0);
            iv.setBackground(iconBg);
            iv.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(iconSize, iconSize);
            ilp.setMarginEnd(NotifyRowSpec.dp(ctx, NotifyRowSpec.ICON_GAP_DP));
            head.addView(iv, ilp);

            LinearLayout col = new LinearLayout(ctx);
            col.setOrientation(LinearLayout.VERTICAL);
            col.setLayoutParams(new LinearLayout.LayoutParams(
                    0, LayoutParams.WRAP_CONTENT, 1f));

            TextView ti = new TextView(ctx);
            ti.setText(c.title == null ? "" : c.title.toString());
            ti.setTextSize(TypedValue.COMPLEX_UNIT_SP, NotifyRowSpec.TITLE_TEXT_SP);
            ti.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
            // 展开组里的胶囊本身就是"展开态"：标题/正文允许换行，替代"再点开看全文"
            ti.setMaxLines(2);
            ti.setEllipsize(TextUtils.TruncateAt.END);
            col.addView(ti);

            TextView bo = new TextView(ctx);
            bo.setText(c.body == null ? "" : c.body.toString());
            bo.setTextSize(TypedValue.COMPLEX_UNIT_SP, NotifyRowSpec.BODY_TEXT_SP);
            bo.setMaxLines(3);
            bo.setEllipsize(TextUtils.TruncateAt.END);
            LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(
                    LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
            blp.topMargin = NotifyRowSpec.dp(ctx, 2);
            col.addView(bo, blp);
            head.addView(col);

            TextView tm = new TextView(ctx);
            tm.setText(c.time == null ? "" : c.time.toString());
            tm.setTextSize(TypedValue.COMPLEX_UNIT_SP, NotifyRowSpec.APP_TEXT_SP);
            tm.setSingleLine(true);
            LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(
                    LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
            tlp.setMarginStart(NotifyRowSpec.dp(ctx, 8));
            head.addView(tm, tlp);

            if (c.actions != null && !c.actions.isEmpty()) {
                LinearLayout acts = new LinearLayout(ctx);
                acts.setOrientation(LinearLayout.HORIZONTAL);
                LinearLayout.LayoutParams alp = new LinearLayout.LayoutParams(
                        LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
                alp.topMargin = NotifyRowSpec.dp(ctx, 2);
                item.addView(acts, alp);
                for (Action a : c.actions) {
                    if (a == null || TextUtils.isEmpty(a.title)) {
                        continue;
                    }
                    TextView ab = new TextView(ctx);
                    ab.setText(a.title);
                    ab.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f);
                    ab.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
                    ab.setTextColor(ACTION_BLUE);
                    ab.setGravity(Gravity.CENTER);
                    ab.setPadding(NotifyRowSpec.dp(ctx, 10), NotifyRowSpec.dp(ctx, 8),
                            NotifyRowSpec.dp(ctx, 10), NotifyRowSpec.dp(ctx, 8));
                    ab.setClickable(true);
                    if (a.run != null) {
                        ab.setOnClickListener(v -> a.run.run());
                    }
                    acts.addView(ab, new LinearLayout.LayoutParams(
                            0, LayoutParams.WRAP_CONTENT, 1f));
                }
            }

            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
            // 列表整体已经挂在正面卡下方 8dp 处；列表内部相邻胶囊再留 8dp
            lp.topMargin = list.getChildCount() == 0 ? 0 : childGapPx;
            list.addView(item, lp);
            childViews.add(new TextView[] { ti, bo, tm });
        }
        applyChildTheme(NotifyRowSpec.night(ctx));
    }

    private void applyChildTheme(boolean night) {
        int primary = night ? NotifyRowSpec.DARK_PRIMARY : NotifyRowSpec.LIGHT_PRIMARY;
        int secondary = night ? NotifyRowSpec.DARK_SECONDARY : NotifyRowSpec.LIGHT_SECONDARY;
        for (TextView[] trio : childViews) {
            trio[0].setTextColor(primary);
            trio[1].setTextColor(primary);
            trio[2].setTextColor(secondary);
        }
    }

    /** 诊断用：把决定卡高的每个量摊开，避免外面靠猜 */
    public String dumpDims() {
        StringBuilder sb = new StringBuilder();
        sb.append("h").append(getHeight()).append("/mh").append(getMeasuredHeight())
                .append(" pad").append(getPaddingTop()).append('+').append(getPaddingBottom())
                .append(" peek").append(stackPeekPx)
                .append(" rc").append(rowContainer == null ? "-"
                        : rowContainer.getVisibility() + "-" + rowContainer.getHeight()
                                + "-" + rowContainer.getMeasuredHeight())
                .append(" cl").append(childList == null ? "-"
                        : childList.getVisibility() + "-" + childList.getHeight()
                                + "-" + childList.getMeasuredHeight())
                .append(" exp").append(stackExpanded ? 1 : 0)
                .append(" kids").append(stackChildren.size());
        return sb.toString();
    }

    /** 诊断/校验用：卡内正面内容与其上下内边距，用来断言"内容装得进卡" */
    public int rowContentHeight() {
        return rowContainer == null ? -1 : rowContainer.getHeight();
    }

    public int childListHeight() {
        return childList == null ? -1 : childList.getHeight();
    }

    public int cardBottom() {
        return getHeight() - stackPeekPx;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (!paintCard) {
            return;
        }
        Context ctx = getContext();
        int width = getWidth();
        float open = clamp01(expandT);
        int peekNow = Math.round(stackPeekPx * (1f - open));
        int cardBottom = getHeight() - peekNow - bottomExtraPx;
        float cardRadius = NotifyRowSpec.dp(ctx, NotifyRowSpec.CARD_RADIUS_DP);
        int mainAlpha = Math.round(255 * (1f - open));
        // 露边层必须画在 cardBottom 以下，**不能**放进下面的外层裁剪里——上一版把外层
        // clipRect 套到全函数，露边被整块裁掉，收起态看起来就只剩单条通知了。
        if (mainAlpha > 0 && peekNow > 0) {
            stackPaint.setColor(stackCardColor);
            stackPaint.setAlpha(mainAlpha);
            int layerShift = NotifyRowSpec.dp(ctx, NotifyRowSpec.STACK_PEEK_LAYER_DP);
            int insetUnit = NotifyRowSpec.dp(ctx, NotifyRowSpec.STACK_BAR_INSET_DP);
            int save = canvas.save();
            canvas.clipRect(0, cardBottom, width, getHeight());
            for (int i = stackBars; i >= 1; i--) {
                int inset = insetUnit * i;
                stackRect.set(inset, (long) i * layerShift,
                        width - inset, (long) i * layerShift + cardBottom);
                canvas.drawRoundRect(stackRect, cardRadius, cardRadius, stackPaint);
            }
            canvas.restoreToCount(save);
        }
        int clip = canvas.save();
        canvas.clipRect(0, 0, width, cardBottom);
        // 展开态：每条子通知一枚独立胶囊（独立圆角 + 8dp 间隙），而不是一张大卡连在一起。
        // 胶囊与折叠卡同宽，文字图标仍内缩 16dp；随展开进度淡入。
        if (open > 0.001f && childList != null && childList.getVisibility() != GONE) {
            int alpha = Math.round(255 * clamp01(open * 2f));
            capsulePaint.setColor(stackCardColor);
            capsulePaint.setAlpha(alpha);
            // 第一枚胶囊就是正面卡：它在展开过程中原地不动，只是从"大卡"变成"胶囊"
            if (rowContainer.getHeight() > 0) {
                int frontTop = rowContainer.getTop();
                int frontBottom = frontTop + rowContainer.getHeight();
                if (frontActions != null && frontActions.getVisibility() == VISIBLE) {
                    frontBottom = Math.max(frontBottom,
                            frontActions.getTop() + frontActions.getHeight());
                }
                stackRect.set(0, frontTop, width, frontBottom);
                canvas.drawRoundRect(stackRect, cardRadius, cardRadius, capsulePaint);
            }
            int listTop = childList.getTop();
            for (int i = 0; i < childList.getChildCount(); i++) {
                View item = childList.getChildAt(i);
                if (item.getVisibility() != VISIBLE || item.getHeight() <= 0) {
                    continue;
                }
                int top = listTop + item.getTop();
                stackRect.set(0, top, width, top + item.getHeight());
                canvas.drawRoundRect(stackRect, cardRadius, cardRadius, capsulePaint);
            }
        }
        // 折叠/过渡中的正面大卡：按展开进度退场，避免与胶囊叠画成"连体"
        if (mainAlpha > 0) {
            stackPaint.setColor(stackCardColor);
            stackPaint.setAlpha(mainAlpha);
            stackRect.set(0, 0, width, cardBottom);
            canvas.drawRoundRect(stackRect, cardRadius, cardRadius, stackPaint);
        }
        canvas.restoreToCount(clip);
        if (open < 0.01f && stackTotal > 1 && icon != null && icon.getWidth() > 0) {
            float radius = badgeRadius();
            float cx = badgeCenterX();
            float cy = badgeCenterY();
            stackPaint.setColor(BADGE_FILL);
            stackPaint.setAlpha(255);
            canvas.drawCircle(cx, cy, radius, stackPaint);
            stackTextPaint.setAntiAlias(true);
            stackTextPaint.setColor(BADGE_TEXT);
            stackTextPaint.setTextAlign(Paint.Align.CENTER);
            stackTextPaint.setTextSize(NotifyRowSpec.sp(ctx, 10f));
            Paint.FontMetrics fm = stackTextPaint.getFontMetrics();
            canvas.drawText(String.valueOf(stackTotal), cx,
                    cy - (fm.ascent + fm.descent) / 2f, stackTextPaint);
        }
    }

    /** 条数徽标（OS6：压在图标右上角的深色圆 + 白字），自绘所以只暴露几何 */
    public int badgeCenterX() {
        if (icon == null || rowContainer == null) {
            return 0;
        }
        return rowContainer.getLeft() + icon.getLeft() + icon.getWidth()
                - NotifyRowSpec.dp(getContext(), 3);
    }

    public int badgeCenterY() {
        if (icon == null || rowContainer == null) {
            return 0;
        }
        return rowContainer.getTop() + icon.getTop()
                + NotifyRowSpec.dp(getContext(), 3);
    }

    public int badgeRadius() {
        return NotifyRowSpec.dp(getContext(), 9);
    }

    public int stackTotal() {
        return stackTotal;
    }

    public int stackBars() {
        return stackBars;
    }

    public int stackPeekPx() {
        return stackPeekPx;
    }

    public int stackCardColor() {
        return stackCardColor;
    }

    public void setPostTime(long when) {
        postTime = when;
        time.setText(relative(when));
        startTicker();
    }

    private static CharSequence relative(long when) {
        return DateUtils.getRelativeTimeSpanString(when, System.currentTimeMillis(),
                DateUtils.MINUTE_IN_MILLIS, DateUtils.FORMAT_ABBREV_RELATIVE);
    }

    private void startTicker() {
        if (ticker || !isAttachedToWindow()) {
            return;
        }
        ticker = true;
        handler.postDelayed(tick, 60000);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        startTicker();
    }

    @Override
    protected void onDetachedFromWindow() {
        ticker = false;
        handler.removeCallbacks(tick);
        if (animator != null) {
            animator.cancel();
            // 停在半途会被复用带下去，脱离窗口时按目标态收尾
            applyExpansion(stackExpanded, stackExpanded ? 1f : 0f);
        }
        super.onDetachedFromWindow();
    }

    public ImageView iconView() {
        return icon;
    }

    public TextView appView() {
        return app;
    }

    public TextView timeView() {
        return time;
    }

    public TextView titleView() {
        return title;
    }

    public TextView bodyView() {
        return body;
    }
}
