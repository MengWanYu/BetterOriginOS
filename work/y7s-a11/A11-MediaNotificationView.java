package com.android.internal.widget;

import android.content.Context;
import android.os.FtBuild;
import android.util.AttributeSet;
import android.view.NotificationHeaderView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.RemoteViews;
import com.android.internal.R;
import java.util.ArrayList;

/* JADX INFO: loaded from: classes4.dex */
@RemoteViews.RemoteView
public class MediaNotificationView extends FrameLayout {
    private static boolean IS_TIER_VOS;
    private View mActions;
    private NotificationHeaderView mHeader;
    private int mImagePushIn;
    private ArrayList<VisibilityChangeListener> mListeners;
    private View mMainColumn;
    private View mMediaContent;
    private final int mNotificationContentImageMarginEnd;
    private final int mNotificationContentMarginEnd;
    private ImageView mRightIcon;

    public interface VisibilityChangeListener {
        void onAggregatedVisibilityChanged(boolean z);
    }

    public MediaNotificationView(Context context) {
        this(context, null);
    }

    public MediaNotificationView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public MediaNotificationView(Context context, AttributeSet attrs, int defStyleAttr) {
        this(context, attrs, defStyleAttr, 0);
    }

    @Override // android.widget.FrameLayout, android.view.View
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        boolean hasIcon = this.mRightIcon.getVisibility() != 8;
        if (!hasIcon) {
            resetHeaderIndention();
        }
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        int mode = View.MeasureSpec.getMode(widthMeasureSpec);
        boolean reMeasure = false;
        this.mImagePushIn = 0;
        if (hasIcon && mode != 0) {
            int size = View.MeasureSpec.getSize(widthMeasureSpec);
            int size2 = size - this.mActions.getMeasuredWidth();
            ViewGroup.MarginLayoutParams layoutParams = (ViewGroup.MarginLayoutParams) this.mRightIcon.getLayoutParams();
            int imageEndMargin = layoutParams.getMarginEnd();
            int size3 = size2 - imageEndMargin;
            int fullHeight = this.mMediaContent.getMeasuredHeight();
            if (size3 > fullHeight) {
                size3 = fullHeight;
            } else if (size3 < fullHeight) {
                size3 = Math.max(0, size3);
                this.mImagePushIn = fullHeight - size3;
            }
            if (layoutParams.width != fullHeight || layoutParams.height != fullHeight) {
                layoutParams.width = fullHeight;
                layoutParams.height = fullHeight;
                this.mRightIcon.setLayoutParams(layoutParams);
                reMeasure = true;
            }
            ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) this.mMainColumn.getLayoutParams();
            int marginEnd = size3 + imageEndMargin + this.mNotificationContentMarginEnd;
            if (marginEnd != params.getMarginEnd()) {
                params.setMarginEnd(marginEnd);
                this.mMainColumn.setLayoutParams(params);
                reMeasure = true;
            }
            int headerTextMarginEnd = size3 + imageEndMargin;
            if (headerTextMarginEnd != this.mHeader.getHeaderTextMarginEnd()) {
                this.mHeader.setHeaderTextMarginEnd(headerTextMarginEnd);
                reMeasure = true;
            }
            ViewGroup.MarginLayoutParams params2 = (ViewGroup.MarginLayoutParams) this.mHeader.getLayoutParams();
            if (params2.getMarginEnd() != imageEndMargin) {
                params2.setMarginEnd(imageEndMargin);
                this.mHeader.setLayoutParams(params2);
                reMeasure = true;
            }
            if (this.mHeader.getPaddingEnd() != this.mNotificationContentImageMarginEnd && IS_TIER_VOS) {
                NotificationHeaderView notificationHeaderView = this.mHeader;
                notificationHeaderView.setPaddingRelative(notificationHeaderView.getPaddingStart(), this.mHeader.getPaddingTop(), this.mNotificationContentImageMarginEnd, this.mHeader.getPaddingBottom());
                reMeasure = true;
            }
        }
        if (reMeasure) {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        }
    }

    static {
        IS_TIER_VOS = FtBuild.getTierLevel() != 0 && FtBuild.getOsName().equalsIgnoreCase("vos");
    }

    @Override // android.widget.FrameLayout, android.view.ViewGroup, android.view.View
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        if (this.mImagePushIn > 0) {
            if (!IS_TIER_VOS) {
                ImageView imageView = this.mRightIcon;
                imageView.layout(imageView.getLeft(), this.mRightIcon.getTop(), this.mRightIcon.getRight(), this.mRightIcon.getBottom());
            } else {
                if (getLayoutDirection() == 1) {
                    this.mImagePushIn *= -1;
                }
                ImageView imageView2 = this.mRightIcon;
                imageView2.layout(imageView2.getLeft() + this.mImagePushIn, this.mRightIcon.getTop(), this.mRightIcon.getRight() + this.mImagePushIn, this.mRightIcon.getBottom());
            }
        }
    }

    private void resetHeaderIndention() {
        if (this.mHeader.getPaddingEnd() != this.mNotificationContentMarginEnd) {
            NotificationHeaderView notificationHeaderView = this.mHeader;
            notificationHeaderView.setPaddingRelative(notificationHeaderView.getPaddingStart(), this.mHeader.getPaddingTop(), this.mNotificationContentMarginEnd, this.mHeader.getPaddingBottom());
        }
        ViewGroup.MarginLayoutParams headerParams = (ViewGroup.MarginLayoutParams) this.mHeader.getLayoutParams();
        headerParams.setMarginEnd(0);
        if (headerParams.getMarginEnd() != 0) {
            headerParams.setMarginEnd(0);
            this.mHeader.setLayoutParams(headerParams);
        }
    }

    public MediaNotificationView(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        this.mNotificationContentMarginEnd = context.getResources().getDimensionPixelSize(R.dimen.notification_content_margin_end);
        this.mNotificationContentImageMarginEnd = context.getResources().getDimensionPixelSize(R.dimen.notification_content_image_margin_end);
    }

    @Override // android.view.View
    protected void onFinishInflate() {
        super.onFinishInflate();
        this.mRightIcon = (ImageView) findViewById(R.id.right_icon);
        this.mActions = findViewById(R.id.media_actions);
        this.mHeader = (NotificationHeaderView) findViewById(R.id.notification_header);
        this.mMainColumn = findViewById(R.id.notification_main_column);
        this.mMediaContent = findViewById(R.id.notification_media_content);
    }

    @Override // android.view.View
    public void onVisibilityAggregated(boolean isVisible) {
        super.onVisibilityAggregated(isVisible);
        if (this.mListeners != null) {
            for (int i = 0; i < this.mListeners.size(); i++) {
                this.mListeners.get(i).onAggregatedVisibilityChanged(isVisible);
            }
        }
    }

    public void addVisibilityListener(VisibilityChangeListener listener) {
        if (this.mListeners == null) {
            this.mListeners = new ArrayList<>();
        }
        if (!this.mListeners.contains(listener)) {
            this.mListeners.add(listener);
        }
    }

    public void removeVisibilityListener(VisibilityChangeListener listener) {
        ArrayList<VisibilityChangeListener> arrayList = this.mListeners;
        if (arrayList != null) {
            arrayList.remove(listener);
        }
    }
}
