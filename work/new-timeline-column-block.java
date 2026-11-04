    /**
     * header 文本列对位：挂在 framework 自己的摆位出口，在**同一趟布局**里把文本子节点整体平移到
     * COLUMN_DP（66dp），A13/A15 共用这一处。
     *
     * 为什么必须挂在这里（前三轮都在跟这条规则赛跑，别再退回补推）：
     * `android.view.NotificationTopLineView.onLayout` 是**按子节点顺序从左往右流式排**、
     * GONE 跳过（真机布局源：framework-res 的 notification_template_header.xml，A13/A15 同构）。
     * vivo 的组子行把 `app_name_text` 收掉了，那一行只剩 `time` 一个可见子节点，
     * 于是时间落到行首（实测 +126），正好压进 42dp 大图标占的 +48..+174。
     * 这不是"谁把时间写歪了"，是布局规则的必然结果 —— 所以既不能用 translationX 补
     * （vivo 每趟重排都写回 0），也不能靠延时重推（永远在跟动画抢时机）。
     */
    private static void installTopLineColumnHook(ClassLoader cl) {
        try {
            Class<?> cls = XposedHelpers.findClassIfExists(
                    "android.view.NotificationTopLineView", cl);
            if (cls == null) {
                Log.i(TAG, "notify_row: top-line column hook skipped=no-class");
                return;
            }
            int hits = XposedBridge.hookAllMethods(cls, "onLayout", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    alignLineIntoColumn((View) param.thisObject);
                }
            }).size();
            Log.i(TAG, "notify_row: top-line column hook hits=" + hits);
        } catch (Throwable t) {
            Log.w(TAG, "notify_row: top-line column hook failed", t);
        }
    }

    /**
     * 只搬文本列这几个子节点。`mFeedbackIcon` 不参与：vivo 用它在 top_line **自己的坐标系**里
     * 算触摸热区（HeaderTouchListener.isInside），把它挪走等于把热区挪出图标。
     */
    private static final String[] HEADER_TEXT_IDS = {
            "app_name_text", "time", "time_divider", "header_text", "header_text_divider",
            "secondary_header_text", "secondary_header_text_divider", "title"};
    private static volatile int[] sHeaderTextIds;
    private static int sColShiftWitness;
    private static final java.util.Set<String> sColSkipWitness =
            java.util.Collections.synchronizedSet(new java.util.HashSet<String>());

    /** 布局期把 header 文本列推到 COLUMN_DP；够不着就不动，并记下不动的原因。 */
    private static void alignLineIntoColumn(View line) {
        try {
            if (!(line instanceof ViewGroup) || line.getWidth() <= 0) {
                return;
            }
            View row = rowAncestor(line);
            View cv = contentViewOf(line);
            if (row == null || cv == null) {
                return;
            }
            boolean taken;
            synchronized (LIGHT_POLISHED) {
                taken = LIGHT_POLISHED.containsKey(cv);
            }
            if (!taken) {
                synchronized (RESTYLED) {
                    taken = RESTYLED.containsKey(cv);
                }
            }
            if (!taken) {
                // 没被我们接管的行（媒体行等）保持原生排版
                return;
            }
            // 只用布局坐标逐级累加 getLeft，不用 getLocationOnScreen：
            // 展开动画期间祖先自己带 translationX/Y，那份位移会被算进时间自己的位移。
            int leftInRow = 0;
            View cur = line;
            for (int i = 0; i < 8 && cur != null && cur != row; i++) {
                leftInRow += cur.getLeft();
                ViewParent p = cur.getParent();
                cur = p instanceof View ? (View) p : null;
            }
            if (cur != row) {
                return;
            }
            final int want = NotifyRowSpec.dp(row.getContext(), NotifyRowSpec.COLUMN_DP);
            final int d = want - leftInRow;
            if (d <= 2) {
                return;   // 已经在目标列或更右：原生就合规，不动
            }
            ViewGroup g = (ViewGroup) line;
            int maxRight = 0;
            for (int i = 0; i < g.getChildCount(); i++) {
                View c = g.getChildAt(i);
                if (c != null && c.getVisibility() != View.GONE && isHeaderText(c)) {
                    maxRight = Math.max(maxRight, c.getRight());
                }
            }
            int room = g.getWidth() - g.getPaddingEnd()
                    - NotifyRowSpec.dp(row.getContext(), 8) - maxRight;
            if (maxRight <= 0 || room < d) {
                // 平移会把文本推出行的右边界（被箭头压住或画到卡片外）=> 宁可不动，但要留证据
                if (sColSkipWitness.size() < 6) {
                    String k = "clamp line=" + leftInRow + " d=" + d + " room=" + room
                            + " pkg=" + (sbnOf(row) == null ? "-" : sbnOf(row).getPackageName());
                    if (sColSkipWitness.add(k)) {
                        Log.i(TAG, "notify_row.col skip=" + k);
                    }
                }
                return;
            }
            int moved = 0;
            for (int i = 0; i < g.getChildCount(); i++) {
                View c = g.getChildAt(i);
                if (c == null || c.getVisibility() == View.GONE || !isHeaderText(c)) {
                    continue;
                }
                c.layout(c.getLeft() + d, c.getTop(), c.getRight() + d, c.getBottom());
                moved++;
            }
            // 不看 sVerifyEnabled：开关一旦为 false，修复和诊断同时失明（上一轮就是这么瞎的）。封顶 12 条。
            if (sColShiftWitness < 12) {
                sColShiftWitness++;
                Log.i(TAG, "notify_row.col layout line=" + leftInRow + " want=" + want
                        + " d=" + d + " kids=" + moved + " rowH=" + row.getHeight()
                        + " pkg=" + (sbnOf(row) == null ? "-" : sbnOf(row).getPackageName()));
            }
        } catch (Throwable t) {
            Log.w(TAG, "notify_row.col layout failed", t);
        }
    }

    /** 这个子节点属不属于"文本列"（id 只解析一次，onLayout 每趟都会调，别在这里查资源名）。 */
    private static boolean isHeaderText(View c) {
        int[] ids = sHeaderTextIds;
        if (ids == null) {
            android.content.res.Resources res = c.getResources();
            int[] built = new int[HEADER_TEXT_IDS.length];
            int n = 0;
            for (String name : HEADER_TEXT_IDS) {
                int id = res.getIdentifier(name, "id", "android");
                if (id != 0) {
                    built[n++] = id;
                }
            }
            ids = java.util.Arrays.copyOf(built, n);
            sHeaderTextIds = ids;
        }
        int id = c.getId();
        if (id == 0) {
            return false;
        }
        for (int v : ids) {
            if (v == id) {
                return true;
            }
        }
        return false;
    }

