import io

p = 'module/src/com/betteroriginos/feature/notify/IosNotificationRow.java'
s = io.open(p, encoding='utf-8').read()

# fields
anchor = """    private int childGapPx;
    private boolean expandedLogged;
"""
assert anchor in s
s = s.replace(anchor, """    private int childGapPx;
    private boolean expandedLogged;
    /** 正面卡（组内最新一条）的动作按钮：展开态挂在第一枚胶囊底部 */
    private LinearLayout frontActions;
""", 1)

# setFrontActions API + builder, insert before setStack
anchor2 = """    /**
     * 折叠堆叠（对齐 OS6 实测）：正面卡占满除露边带以外的区域，背后每层卡片下移 14dp、
"""
assert anchor2 in s
api = '''    /** 正面卡的动作按钮（与列表胶囊同一套样式） */
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
        row.setVisibility(row.getChildCount() == 0 ? GONE : VISIBLE);
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

'''
s = s.replace(anchor2, api + anchor2, 1)

# clearStack: hide front actions
old = """        rowContainer.setAlpha(1f);
        rowContainer.setVisibility(VISIBLE);
        if (childList != null) {
            childList.setAlpha(1f);
            childList.setVisibility(GONE);
        }
        setClipChildren(false);"""
assert old in s
s = s.replace(old, """        rowContainer.setAlpha(1f);
        rowContainer.setVisibility(VISIBLE);
        if (childList != null) {
            childList.setAlpha(1f);
            childList.setVisibility(GONE);
        }
        if (frontActions != null) {
            frontActions.setVisibility(GONE);
        }
        setClipChildren(false);""", 1)

# animatingHeight: include front actions
old2 = """        int rowH = measureSelf(rowContainer, innerW);
        // 展开态 = 正面卡（第一枚胶囊）+ 8dp + 其余胶囊；折叠态 = 正面卡 + 露边
        int collapsed = topPad + rowH + baseBottom + stackPeekPx;
        int expanded = rowH + childGapPx + measureSelf(childList, innerW) + baseBottom;"""
assert old2 in s
s = s.replace(old2, """        int rowH = measureSelf(rowContainer, innerW);
        int frontActs = frontActionsHeight(innerW, true);
        // 展开态 = 正面卡 + 正面动作 + 8dp + 其余胶囊；折叠态 = 正面卡 + 露边
        int collapsed = topPad + rowH + baseBottom + stackPeekPx;
        int expanded = rowH + frontActs + childGapPx
                + measureSelf(childList, innerW) + baseBottom;""", 1)

# applyExpansion: manage front actions visibility/topMargin and list top
old3 = """        LayoutParams llp = (LayoutParams) list.getLayoutParams();
        int wantTop = open > 0.004f && rowH > 0 ? rowH + childGapPx : 0;
        if (llp != null && llp.topMargin != wantTop) {
            llp.topMargin = wantTop;
            list.setLayoutParams(llp);
        }"""
assert old3 in s
s = s.replace(old3, """        // 正面卡动作按钮：展开态可见，挂在正面卡内容下方，列表整体再往下让
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
        }""", 1)

# metrics log: front height includes actions, add frontActH
old4 = """            Log.i("BetterOriginOS", "notify_row.stack expanded count=" + (n + 1)
                    + " frontVis=" + rowContainer.getVisibility()
                    + " frontH=" + rowContainer.getHeight()"""
assert old4 in s
s = s.replace(old4, """            int frontActsH = frontActionsHeight(innerW, true);
            Log.i("BetterOriginOS", "notify_row.stack expanded count=" + (n + 1)
                    + " frontVis=" + rowContainer.getVisibility()
                    + " frontH=" + (rowContainer.getHeight() + frontActsH)
                    + " frontActH=" + frontActsH""", 1)

# onDraw: front capsule bottom includes actions
old5 = """            if (rowContainer.getHeight() > 0) {
                int frontTop = rowContainer.getTop();
                stackRect.set(0, frontTop, width, frontTop + rowContainer.getHeight());
                canvas.drawRoundRect(stackRect, cardRadius, cardRadius, capsulePaint);
            }"""
assert old5 in s
s = s.replace(old5, """            if (rowContainer.getHeight() > 0) {
                int frontTop = rowContainer.getTop();
                int frontBottom = frontTop + rowContainer.getHeight();
                if (frontActions != null && frontActions.getVisibility() == VISIBLE) {
                    frontBottom = Math.max(frontBottom,
                            frontActions.getTop() + frontActions.getHeight());
                }
                stackRect.set(0, frontTop, width, frontBottom);
                canvas.drawRoundRect(stackRect, cardRadius, cardRadius, capsulePaint);
            }""", 1)

io.open(p, 'w', encoding='utf-8').write(s)
print('IosNotificationRow front actions patched')

# NotifyRowFeature: pass front actions
p2 = 'module/src/com/betteroriginos/feature/notify/NotifyRowFeature.java'
f = io.open(p2, encoding='utf-8').read()
old6 = """            overlay.setStackChildren(kids);
            overlay.setStackExpanded(STACK_USER_EXPANDED.containsKey(rowView));"""
assert old6 in f
f = f.replace(old6, """            overlay.setStackChildren(kids);
            overlay.setFrontActions(stackActions(front));
            overlay.setStackExpanded(STACK_USER_EXPANDED.containsKey(rowView));""", 1)
io.open(p2, 'w', encoding='utf-8').write(f)
print('NotifyRowFeature front actions patched')
