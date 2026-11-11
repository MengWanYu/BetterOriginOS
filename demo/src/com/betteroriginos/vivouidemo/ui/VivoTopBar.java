package com.betteroriginos.vivouidemo.ui;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.betteroriginos.vivouidemo.R;

public class VivoTopBar extends BlurBar {
    public static final float LARGE_TITLE_HEIGHT = 96f;
    public static final float COMPACT_HEIGHT = 56f;
    public static final float SEARCH_HEIGHT = 56f;

    private final boolean large;
    private float largeHeightDp = LARGE_TITLE_HEIGHT;
    private int statusInset;
    private TextView titleView;
    private TextView subtitleView;
    private LinearLayout actionBox;
    private ImageView backView;
    private EditText searchInput;
    private LinearLayout searchRow;
    private boolean searchEnabled;
    private int actionCount;
    private float contentHeightDp;

    public VivoTopBar(Context context, boolean large) {
        super(context);
        this.large = large;
        setBlurRadiusDp(34f);
        setTintAlpha(0.92f);
        setPadding(0, 0, 0, 0);
        build();
    }

    private void build() {
        if (large) {
            buildLarge();
        } else {
            buildCompact();
        }
    }

    private void buildLarge() {
        LinearLayout column = new LinearLayout(getContext());
        column.setOrientation(LinearLayout.VERTICAL);
        column.setClipChildren(false);
        column.setClipToPadding(false);

        FrameLayout titleArea = new FrameLayout(getContext());
        column.addView(titleArea, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, VivoTheme.dpi(getContext(), largeHeightDp)));

        LinearLayout titleBlock = new LinearLayout(getContext());
        titleBlock.setOrientation(LinearLayout.VERTICAL);
        titleBlock.setGravity(Gravity.CENTER_VERTICAL);
        FrameLayout.LayoutParams blockLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        blockLp.gravity = Gravity.START | Gravity.CENTER_VERTICAL;
        blockLp.leftMargin = VivoTheme.dpi(getContext(), 24);
        titleArea.addView(titleBlock, blockLp);

        titleView = new TextView(getContext());
        VivoTheme.text(titleView, 24f, VivoTheme.color(getContext(), R.color.vivo_text_primary), VivoTheme.W_BOLD);
        titleView.setSingleLine(true);
        titleView.setLetterSpacing(0.005f);
        titleBlock.addView(titleView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        subtitleView = new TextView(getContext());
        VivoTheme.text(subtitleView, 12f, VivoTheme.color(getContext(), R.color.vivo_text_secondary), VivoTheme.W_REGULAR);
        subtitleView.setSingleLine(true);
        subtitleView.setVisibility(View.GONE);
        LinearLayout.LayoutParams subLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        subLp.topMargin = VivoTheme.dpi(getContext(), 3);
        titleBlock.addView(subtitleView, subLp);

        actionBox = new LinearLayout(getContext());
        actionBox.setOrientation(LinearLayout.HORIZONTAL);
        actionBox.setGravity(Gravity.CENTER_VERTICAL);
        FrameLayout.LayoutParams actionLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        actionLp.gravity = Gravity.END | Gravity.CENTER_VERTICAL;
        actionLp.rightMargin = VivoTheme.dpi(getContext(), 8);
        titleArea.addView(actionBox, actionLp);

        contentHeightDp = largeHeightDp;

        addView(column, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    private void buildCompact() {
        LinearLayout row = new LinearLayout(getContext());
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(VivoTheme.dpi(getContext(), 4), 0, VivoTheme.dpi(getContext(), 8), 0);

        backView = new ImageView(getContext());
        backView.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        backView.setImageDrawable(VivoIcons.of("back",
                VivoTheme.color(getContext(), R.color.vivo_text_primary), 24));
        backView.setPadding(VivoTheme.dpi(getContext(), 10), VivoTheme.dpi(getContext(), 10),
                VivoTheme.dpi(getContext(), 10), VivoTheme.dpi(getContext(), 10));
        backView.setBackground(VivoTheme.iconButtonBackground(getContext()));
        backView.setVisibility(View.GONE);
        row.addView(backView, new LinearLayout.LayoutParams(
                VivoTheme.dpi(getContext(), 44), VivoTheme.dpi(getContext(), 44)));

        titleView = new TextView(getContext());
        VivoTheme.text(titleView, 20f, VivoTheme.color(getContext(), R.color.vivo_text_primary), VivoTheme.W_SEMI);
        titleView.setSingleLine(true);
        titleView.setEllipsize(android.text.TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        titleLp.leftMargin = VivoTheme.dpi(getContext(), 4);
        row.addView(titleView, titleLp);

        actionBox = new LinearLayout(getContext());
        actionBox.setOrientation(LinearLayout.HORIZONTAL);
        actionBox.setGravity(Gravity.CENTER_VERTICAL);
        row.addView(actionBox, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        addView(row, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, VivoTheme.dpi(getContext(), COMPACT_HEIGHT)));
        contentHeightDp = COMPACT_HEIGHT;
    }

    public void setTitle(CharSequence title) {
        titleView.setText(title);
    }

    public void setSubtitle(CharSequence subtitle) {
        if (subtitleView == null) {
            return;
        }
        subtitleView.setText(subtitle);
        subtitleView.setVisibility(subtitle == null || subtitle.length() == 0 ? View.GONE : View.VISIBLE);
    }

    public void setLargeHeightDp(float dp) {
        if (!large) {
            return;
        }
        largeHeightDp = dp;
        contentHeightDp = dp + (searchEnabled ? SEARCH_HEIGHT : 0);
        View column = getChildAt(0);
        if (column instanceof LinearLayout) {
            View titleArea = ((LinearLayout) column).getChildAt(0);
            ViewGroup.LayoutParams lp = titleArea.getLayoutParams();
            lp.height = VivoTheme.dpi(getContext(), dp);
            titleArea.setLayoutParams(lp);
        }
        requestLayout();
    }

    public void setBackVisible(boolean visible, OnClickListener listener) {
        if (backView == null) {
            return;
        }
        backView.setVisibility(visible ? View.VISIBLE : View.GONE);
        backView.setOnClickListener(listener);
    }

    public ImageView addAction(String iconName, OnClickListener listener) {
        ImageView view = new ImageView(getContext());
        view.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        view.setImageDrawable(VivoIcons.of(iconName,
                VivoTheme.color(getContext(), R.color.vivo_text_primary), 24));
        view.setPadding(VivoTheme.dpi(getContext(), 10), VivoTheme.dpi(getContext(), 10),
                VivoTheme.dpi(getContext(), 10), VivoTheme.dpi(getContext(), 10));
        view.setBackground(VivoTheme.iconButtonBackground(getContext()));
        view.setOnClickListener(listener);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                VivoTheme.dpi(getContext(), 44), VivoTheme.dpi(getContext(), 44));
        lp.leftMargin = actionCount == 0 ? 0 : VivoTheme.dpi(getContext(), 2);
        actionBox.addView(view, lp);
        actionCount++;
        return view;
    }

    public EditText enableSearch(String hint) {
        if (!large || searchRow != null) {
            return searchInput;
        }
        searchRow = new LinearLayout(getContext());
        searchRow.setOrientation(LinearLayout.HORIZONTAL);
        searchRow.setGravity(Gravity.CENTER_VERTICAL);
        searchRow.setPadding(VivoTheme.dpi(getContext(), 24), 0, VivoTheme.dpi(getContext(), 24),
                VivoTheme.dpi(getContext(), 6));

        ImageView icon = new ImageView(getContext());
        icon.setImageDrawable(VivoIcons.of("search",
                VivoTheme.alpha(VivoTheme.color(getContext(), R.color.vivo_text_primary), 0.55f), 22));
        searchRow.addView(icon, new LinearLayout.LayoutParams(
                VivoTheme.dpi(getContext(), 22), VivoTheme.dpi(getContext(), 22)));

        searchInput = new EditText(getContext());
        searchInput.setSingleLine(true);
        searchInput.setBackgroundColor(Color.TRANSPARENT);
        searchInput.setHint(hint);
        searchInput.setTextSize(16f);
        searchInput.setTextColor(VivoTheme.color(getContext(), R.color.vivo_text_primary));
        searchInput.setHintTextColor(VivoTheme.alpha(VivoTheme.color(getContext(), R.color.vivo_text_primary), 0.42f));
        searchInput.setTypeface(VivoTheme.typeface(VivoTheme.W_REGULAR));
        searchInput.setIncludeFontPadding(false);
        searchInput.setPadding(0, 0, 0, 0);
        searchInput.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        LinearLayout.LayoutParams inputLp = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.MATCH_PARENT, 1f);
        inputLp.leftMargin = VivoTheme.dpi(getContext(), 14);
        searchRow.addView(searchInput, inputLp);
        searchRow.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                searchInput.requestFocus();
                android.view.inputmethod.InputMethodManager imm =
                        (android.view.inputmethod.InputMethodManager) getContext()
                                .getSystemService(Context.INPUT_METHOD_SERVICE);
                if (imm != null) {
                    imm.showSoftInput(searchInput, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT);
                }
            }
        });

        LinearLayout column = (LinearLayout) getChildAt(0);
        column.addView(searchRow, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, VivoTheme.dpi(getContext(), SEARCH_HEIGHT)));
        searchEnabled = true;
        contentHeightDp = largeHeightDp + SEARCH_HEIGHT;
        requestLayout();
        return searchInput;
    }

    public EditText getSearchInput() {
        return searchInput;
    }

    public void addSearchTextWatcher(TextWatcher watcher) {
        if (searchInput != null) {
            searchInput.addTextChangedListener(watcher);
        }
    }

    public void clearSearch() {
        if (searchInput != null) {
            searchInput.setText("");
        }
    }

    public int getTotalHeight() {
        return statusInset + VivoTheme.dpi(getContext(), contentHeightDp);
    }

    public void setStatusInset(int px) {
        if (statusInset == px) {
            return;
        }
        statusInset = px;
        setPadding(0, px, 0, 0);
        requestLayout();
    }

    public int getStatusInset() {
        return statusInset;
    }

    public boolean isLarge() {
        return large;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
    }
}
