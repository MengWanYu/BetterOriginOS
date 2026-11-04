package android.view;

import android.content.Context;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Outline;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.os.FtBuild;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RemoteViews;
import com.android.internal.R;
import com.android.internal.widget.CachingIconView;
import com.android.internal.widget.NotificationExpandButton;
import java.util.ArrayList;

/* JADX INFO: loaded from: classes3.dex */
@RemoteViews.RemoteView
public class NotificationHeaderView extends ViewGroup {
    private static boolean IS_TIER_VOS = false;
    public static final int NO_COLOR = 1;
    private boolean mAcceptAllTouches;
    private View mAppName;
    private View mAppOps;
    private View.OnClickListener mAppOpsListener;
    private Drawable mBackground;
    private final int mChildMinWidth;
    private ImageView mCloseButton;
    private final int mContentEndMargin;
    private boolean mEntireHeaderClickable;
    private NotificationExpandButton mExpandButton;
    private View.OnClickListener mExpandClickListener;
    private boolean mExpandOnlyOnButton;
    private boolean mExpanded;
    private final int mGravity;
    private View mHeaderText;
    private int mHeaderTextMarginEnd;
    private CachingIconView mIcon;
    private View mNotificationCountText;
    private View mProfileBadge;
    ViewOutlineProvider mProvider;
    private View mSecondaryHeaderText;
    private boolean mShowExpandButtonAtEnd;
    private boolean mShowWorkBadgeAtEnd;
    private int mTotalWidth;
    private HeaderTouchListener mTouchListener;
    private LinearLayout mTransferChip;

    static {
        IS_TIER_VOS = FtBuild.getTierLevel() != 0 && FtBuild.getOsName().equalsIgnoreCase("vos");
    }

    public NotificationHeaderView(Context context) {
        this(context, null);
    }

    public NotificationHeaderView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public NotificationHeaderView(Context context, AttributeSet attrs, int defStyleAttr) {
        this(context, attrs, defStyleAttr, 0);
    }

    public NotificationHeaderView(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        this.mTouchListener = new HeaderTouchListener();
        this.mProvider = new ViewOutlineProvider() { // from class: android.view.NotificationHeaderView.1
            @Override // android.view.ViewOutlineProvider
            public void getOutline(View view, Outline outline) {
                if (NotificationHeaderView.this.mBackground != null) {
                    outline.setRect(0, 0, NotificationHeaderView.this.getWidth(), NotificationHeaderView.this.getHeight());
                    outline.setAlpha(1.0f);
                }
            }
        };
        Resources res = getResources();
        this.mChildMinWidth = res.getDimensionPixelSize(R.dimen.notification_header_shrink_min_width);
        this.mContentEndMargin = res.getDimensionPixelSize(R.dimen.notification_content_margin_end);
        this.mEntireHeaderClickable = res.getBoolean(R.bool.config_notificationHeaderClickableForExpand);
        int[] attrIds = {16842927};
        TypedArray ta = context.obtainStyledAttributes(attrs, attrIds, defStyleAttr, defStyleRes);
        this.mGravity = ta.getInt(0, 0);
        ta.recycle();
    }

    @Override // android.view.View
    protected void onFinishInflate() {
        super.onFinishInflate();
        this.mAppName = findViewById(R.id.app_name_text);
        this.mHeaderText = findViewById(R.id.header_text);
        this.mSecondaryHeaderText = findViewById(R.id.header_text_secondary);
        if (!IS_TIER_VOS) {
            this.mNotificationCountText = findViewById(51183754);
            ImageView imageView = (ImageView) findViewById(51183753);
            this.mCloseButton = imageView;
            imageView.setImageDrawable(getContext().getDrawable(50464828));
        }
        this.mTransferChip = (LinearLayout) findViewById(R.id.media_seamless);
        this.mExpandButton = (NotificationExpandButton) findViewById(R.id.expand_button);
        this.mIcon = (CachingIconView) findViewById(16908294);
        this.mProfileBadge = findViewById(R.id.profile_badge);
        this.mAppOps = findViewById(R.id.app_ops);
    }

    @Override // android.view.View
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int givenWidth = View.MeasureSpec.getSize(widthMeasureSpec);
        int givenHeight = View.MeasureSpec.getSize(heightMeasureSpec);
        int wrapContentWidthSpec = View.MeasureSpec.makeMeasureSpec(givenWidth, Integer.MIN_VALUE);
        int wrapContentHeightSpec = View.MeasureSpec.makeMeasureSpec(givenHeight, Integer.MIN_VALUE);
        int totalWidth = getPaddingStart();
        int iconWidth = getPaddingEnd();
        if (!IS_TIER_VOS) {
            iconWidth = 0;
        }
        for (int i = 0; i < getChildCount(); i++) {
            View child = getChildAt(i);
            if (child.getVisibility() != 8) {
                ViewGroup.MarginLayoutParams lp = (ViewGroup.MarginLayoutParams) child.getLayoutParams();
                int childWidthSpec = getChildMeasureSpec(wrapContentWidthSpec, lp.leftMargin + lp.rightMargin, lp.width);
                int childHeightSpec = getChildMeasureSpec(wrapContentHeightSpec, lp.topMargin + lp.bottomMargin, lp.height);
                child.measure(childWidthSpec, childHeightSpec);
                if ((child == this.mExpandButton && this.mShowExpandButtonAtEnd) || child == this.mProfileBadge || child == this.mAppOps || child == this.mTransferChip || (!IS_TIER_VOS && child == this.mCloseButton)) {
                    iconWidth += lp.leftMargin + lp.rightMargin + child.getMeasuredWidth();
                } else {
                    totalWidth += lp.leftMargin + lp.rightMargin + child.getMeasuredWidth();
                }
            }
        }
        if (!IS_TIER_VOS) {
            totalWidth += getPaddingEnd();
        }
        int endMargin = Math.max(this.mHeaderTextMarginEnd, iconWidth);
        if (totalWidth > givenWidth - endMargin) {
            int overFlow = (totalWidth - givenWidth) + endMargin;
            shrinkViewForOverflow(wrapContentHeightSpec, shrinkViewForOverflow(wrapContentHeightSpec, shrinkViewForOverflow(wrapContentHeightSpec, overFlow, this.mAppName, this.mChildMinWidth), this.mHeaderText, 0), this.mSecondaryHeaderText, 0);
        }
        if (IS_TIER_VOS) {
            totalWidth += getPaddingEnd();
        }
        this.mTotalWidth = Math.min(totalWidth + getPaddingEnd(), givenWidth);
        setMeasuredDimension(givenWidth, givenHeight);
    }

    private int shrinkViewForOverflow(int heightSpec, int overFlow, View targetView, int minimumWidth) {
        int oldWidth = targetView.getMeasuredWidth();
        if (overFlow > 0 && targetView.getVisibility() != 8 && oldWidth > minimumWidth) {
            int newSize = Math.max(minimumWidth, oldWidth - overFlow);
            int childWidthSpec = View.MeasureSpec.makeMeasureSpec(newSize, Integer.MIN_VALUE);
            targetView.measure(childWidthSpec, heightSpec);
            return overFlow - (oldWidth - newSize);
        }
        return overFlow;
    }

    @Override // android.view.ViewGroup, android.view.View
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        View view;
        ImageView imageView;
        int layoutLeft;
        int layoutRight;
        int left = getPaddingStart();
        int end = getMeasuredWidth();
        if (!IS_TIER_VOS) {
            end = getMeasuredWidth() - getPaddingEnd();
        }
        boolean centerAligned = (this.mGravity & 1) != 0;
        if (centerAligned) {
            left += (getMeasuredWidth() / 2) - (this.mTotalWidth / 2);
        }
        int childCount = getChildCount();
        int ownHeight = (getMeasuredHeight() - getPaddingTop()) - getPaddingBottom();
        for (int i = 0; i < childCount; i++) {
            View child = getChildAt(i);
            if (child.getVisibility() != 8) {
                int childHeight = child.getMeasuredHeight();
                ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) child.getLayoutParams();
                int top = (int) (getPaddingTop() + ((ownHeight - childHeight) / 2.0f));
                int bottom = top + childHeight;
                if ((child == this.mExpandButton && this.mShowExpandButtonAtEnd) || child == this.mProfileBadge || (((view = this.mNotificationCountText) != null && child == view) || child == this.mAppOps || child == this.mTransferChip || ((imageView = this.mCloseButton) != null && child == imageView))) {
                    if (end == getMeasuredWidth()) {
                        if (!IS_TIER_VOS) {
                            layoutRight = end;
                        } else {
                            int layoutRight2 = this.mContentEndMargin;
                            layoutRight = end - layoutRight2;
                        }
                    } else {
                        int layoutRight3 = params.getMarginEnd();
                        layoutRight = end - layoutRight3;
                    }
                    layoutLeft = layoutRight - child.getMeasuredWidth();
                    end = layoutLeft - params.getMarginStart();
                } else {
                    int left2 = left + params.getMarginStart();
                    int right = child.getMeasuredWidth() + left2;
                    layoutLeft = left2;
                    layoutRight = right;
                    left = right + params.getMarginEnd();
                }
                if (getLayoutDirection() == 1) {
                    int ltrLeft = layoutLeft;
                    layoutLeft = getWidth() - layoutRight;
                    layoutRight = getWidth() - ltrLeft;
                }
                child.layout(layoutLeft, top, layoutRight, bottom);
            }
        }
        updateTouchListener();
    }

    public View getHeaderTextCountView() {
        return this.mNotificationCountText;
    }

    @Override // android.view.ViewGroup
    public ViewGroup.LayoutParams generateLayoutParams(AttributeSet attrs) {
        return new ViewGroup.MarginLayoutParams(getContext(), attrs);
    }

    public void setHeaderBackgroundDrawable(Drawable drawable) {
        if (drawable != null) {
            setWillNotDraw(false);
            this.mBackground = drawable;
            drawable.setCallback(this);
            setOutlineProvider(this.mProvider);
        } else {
            setWillNotDraw(true);
            this.mBackground = null;
            setOutlineProvider(null);
        }
        invalidate();
    }

    @Override // android.view.View
    protected void onDraw(Canvas canvas) {
        Drawable drawable = this.mBackground;
        if (drawable != null) {
            drawable.setBounds(0, 0, getWidth(), getHeight());
            this.mBackground.draw(canvas);
        }
    }

    @Override // android.view.View
    protected boolean verifyDrawable(Drawable who) {
        return super.verifyDrawable(who) || who == this.mBackground;
    }

    @Override // android.view.ViewGroup, android.view.View
    protected void drawableStateChanged() {
        Drawable drawable = this.mBackground;
        if (drawable != null && drawable.isStateful()) {
            this.mBackground.setState(getDrawableState());
        }
    }

    private void updateTouchListener() {
        if (this.mExpandClickListener == null && this.mAppOpsListener == null) {
            setOnTouchListener(null);
        } else {
            setOnTouchListener(this.mTouchListener);
            this.mTouchListener.bindTouchRects();
        }
    }

    public void setAppOpsOnClickListener(View.OnClickListener l) {
        this.mAppOpsListener = l;
        updateTouchListener();
    }

    @Override // android.view.View
    public void setOnClickListener(View.OnClickListener l) {
        this.mExpandClickListener = l;
        this.mExpandButton.setOnClickListener(l);
        updateTouchListener();
    }

    public int getOriginalIconColor() {
        return this.mIcon.getOriginalIconColor();
    }

    public int getOriginalNotificationColor() {
        return this.mExpandButton.getOriginalNotificationColor();
    }

    @RemotableViewMethod
    public void setExpanded(boolean expanded) {
        this.mExpanded = expanded;
        updateExpandButton();
    }

    private void updateExpandButton() {
        int drawableId;
        int contentDescriptionId;
        if (this.mExpanded) {
            if (!IS_TIER_VOS) {
                drawableId = 50464829;
            } else {
                drawableId = R.drawable.ic_collapse_notification;
            }
            contentDescriptionId = R.string.expand_button_content_description_expanded;
        } else {
            if (!IS_TIER_VOS) {
                drawableId = 50464833;
            } else {
                drawableId = R.drawable.ic_expand_notification;
            }
            contentDescriptionId = R.string.expand_button_content_description_collapsed;
        }
        this.mExpandButton.setImageDrawable(getContext().getDrawable(drawableId));
        this.mExpandButton.setColorFilter(getOriginalNotificationColor());
        this.mExpandButton.setContentDescription(this.mContext.getText(contentDescriptionId));
    }

    public void setShowWorkBadgeAtEnd(boolean showWorkBadgeAtEnd) {
        if (showWorkBadgeAtEnd != this.mShowWorkBadgeAtEnd) {
            setClipToPadding(!showWorkBadgeAtEnd);
            this.mShowWorkBadgeAtEnd = showWorkBadgeAtEnd;
        }
    }

    public void setShowExpandButtonAtEnd(boolean showExpandButtonAtEnd) {
        if (showExpandButtonAtEnd != this.mShowExpandButtonAtEnd) {
            setClipToPadding(!showExpandButtonAtEnd);
            this.mShowExpandButtonAtEnd = showExpandButtonAtEnd;
        }
    }

    public View getWorkProfileIcon() {
        return this.mProfileBadge;
    }

    public CachingIconView getIcon() {
        return this.mIcon;
    }

    @RemotableViewMethod
    public void setHeaderTextMarginEnd(int headerTextMarginEnd) {
        if (this.mHeaderTextMarginEnd != headerTextMarginEnd) {
            this.mHeaderTextMarginEnd = headerTextMarginEnd;
            requestLayout();
        }
    }

    public int getHeaderTextMarginEnd() {
        return this.mHeaderTextMarginEnd;
    }

    public class HeaderTouchListener implements View.OnTouchListener {
        private Rect mAppOpsRect;
        private float mDownX;
        private float mDownY;
        private Rect mExpandButtonRect;
        private final ArrayList<Rect> mTouchRects = new ArrayList<>();
        private int mTouchSlop;
        private boolean mTrackGesture;

        public HeaderTouchListener() {
        }

        public void bindTouchRects() {
            this.mTouchRects.clear();
            addRectAroundView(NotificationHeaderView.this.mIcon);
            this.mExpandButtonRect = addRectAroundView(NotificationHeaderView.this.mExpandButton);
            Rect rectAddRectAroundView = addRectAroundView(NotificationHeaderView.this.mAppOps);
            this.mAppOpsRect = rectAddRectAroundView;
            NotificationHeaderView notificationHeaderView = NotificationHeaderView.this;
            notificationHeaderView.setTouchDelegate(new TouchDelegate(rectAddRectAroundView, notificationHeaderView.mAppOps));
            addWidthRect();
            this.mTouchSlop = ViewConfiguration.get(NotificationHeaderView.this.getContext()).getScaledTouchSlop();
        }

        private void addWidthRect() {
            Rect r = new Rect();
            r.top = 0;
            r.bottom = (int) (NotificationHeaderView.this.getResources().getDisplayMetrics().density * 32.0f);
            r.left = 0;
            r.right = NotificationHeaderView.this.getWidth();
            this.mTouchRects.add(r);
        }

        private Rect addRectAroundView(View view) {
            Rect r = getRectAroundView(view);
            this.mTouchRects.add(r);
            return r;
        }

        private Rect getRectAroundView(View view) {
            float size = NotificationHeaderView.this.getResources().getDisplayMetrics().density * 48.0f;
            float width = Math.max(size, view.getWidth());
            float height = Math.max(size, view.getHeight());
            Rect r = new Rect();
            if (view.getVisibility() == 8) {
                view = NotificationHeaderView.this.getFirstChildNotGone();
                r.left = (int) (view.getLeft() - (width / 2.0f));
            } else {
                r.left = (int) (((view.getLeft() + view.getRight()) / 2.0f) - (width / 2.0f));
            }
            r.top = (int) (((view.getTop() + view.getBottom()) / 2.0f) - (height / 2.0f));
            r.bottom = (int) (r.top + height);
            r.right = (int) (r.left + width);
            return r;
        }

        @Override // android.view.View.OnTouchListener
        public boolean onTouch(View v, MotionEvent event) {
            float x = event.getX();
            float y = event.getY();
            int actionMasked = event.getActionMasked() & 255;
            if (actionMasked == 0) {
                this.mTrackGesture = false;
                if (isInside(x, y)) {
                    this.mDownX = x;
                    this.mDownY = y;
                    this.mTrackGesture = true;
                    return true;
                }
            } else if (actionMasked != 1) {
                if (actionMasked == 2 && this.mTrackGesture && (Math.abs(this.mDownX - x) > this.mTouchSlop || Math.abs(this.mDownY - y) > this.mTouchSlop)) {
                    this.mTrackGesture = false;
                }
            } else if (this.mTrackGesture) {
                if (!NotificationHeaderView.this.mAppOps.isVisibleToUser() || (!this.mAppOpsRect.contains((int) x, (int) y) && !this.mAppOpsRect.contains((int) this.mDownX, (int) this.mDownY))) {
                    NotificationHeaderView.this.mExpandButton.performClick();
                } else {
                    NotificationHeaderView.this.mAppOps.performClick();
                    return true;
                }
            }
            return this.mTrackGesture;
        }

        /* JADX INFO: Access modifiers changed from: private */
        public boolean isInside(float x, float y) {
            if (NotificationHeaderView.this.mAcceptAllTouches) {
                return true;
            }
            if (NotificationHeaderView.this.mExpandOnlyOnButton) {
                return this.mExpandButtonRect.contains((int) x, (int) y);
            }
            for (int i = 0; i < this.mTouchRects.size(); i++) {
                Rect r = this.mTouchRects.get(i);
                if (r.contains((int) x, (int) y)) {
                    return true;
                }
            }
            return false;
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public View getFirstChildNotGone() {
        for (int i = 0; i < getChildCount(); i++) {
            View child = getChildAt(i);
            if (child.getVisibility() != 8) {
                return child;
            }
        }
        return this;
    }

    public ImageView getExpandButton() {
        return this.mExpandButton;
    }

    @Override // android.view.View
    public boolean hasOverlappingRendering() {
        return false;
    }

    public boolean isInTouchRect(float x, float y) {
        if (this.mExpandClickListener != null) {
            return this.mTouchListener.isInside(x, y);
        }
        return false;
    }

    @RemotableViewMethod
    public void setAcceptAllTouches(boolean acceptAllTouches) {
        this.mAcceptAllTouches = this.mEntireHeaderClickable || acceptAllTouches;
    }

    @RemotableViewMethod
    public void setExpandOnlyOnButton(boolean expandOnlyOnButton) {
        this.mExpandOnlyOnButton = expandOnlyOnButton;
    }
}
