import io

p = 'module/src/com/betteroriginos/feature/notify/NotifyRowFeature.java'
s = io.open(p, encoding='utf-8').read()

old = '''    private static void openStackCard(View rowView) {
        StatusBarNotification front = newestChildSbn(rowView);
        PendingIntent pi = front == null || front.getNotification() == null
                ? null : front.getNotification().contentIntent;
        if (pi == null) {
            Log.i(TAG, "notify_row: stack card has no contentIntent, use row click");
            rowView.performClick();
            return;
        }
        try {
            pi.send();
            closeShade(rowView.getContext());
            Log.i(TAG, "notify_row: stack card opened app pkg=" + front.getPackageName());
        } catch (Throwable t) {
            Log.w(TAG, "notify_row: stack contentIntent send failed, fall back to row click", t);
            try {
                rowView.performClick();
            } catch (Throwable ignored) {
            }
        }
    }
'''

new = '''    private static void openStackCard(View rowView) {
        StatusBarNotification front = newestChildSbn(rowView);
        if (front != null) {
            openStackChild(rowView, front);
            return;
        }
        Log.i(TAG, "notify_row: stack card has no child, use row click");
        try {
            rowView.performClick();
        } catch (Throwable ignored) {
        }
    }

    /**
     * 打开堆叠组里的某一条子通知。优先走该子通知原生行的点击回调：
     * contentIntent / autoCancel / 收起通知中心都是系统原有语义；找不到原生行时退回
     * send PendingIntent + 按 FLAG_AUTO_CANCEL 移除，避免"跳转了但通知还挂着"。
     */
    private static void openStackChild(View rowView, StatusBarNotification sbn) {
        if (sbn == null) {
            return;
        }
        View child = findChildRow(rowView, sbn);
        if (child != null) {
            try {
                if (child.performClick()) {
                    Log.i(TAG, "notify_row: stack child click via=row key=" + sbn.getKey());
                    return;
                }
            } catch (Throwable t) {
                Log.w(TAG, "notify_row: stack child row click failed", t);
            }
        }
        PendingIntent pi = sbn.getNotification() == null
                ? null : sbn.getNotification().contentIntent;
        if (pi == null) {
            Log.i(TAG, "notify_row: stack child no contentIntent key=" + sbn.getKey());
            return;
        }
        try {
            pi.send();
            dismissIfAutoCancel(sbn);
            closeShade(rowView.getContext());
            Log.i(TAG, "notify_row: stack child click via=intent key=" + sbn.getKey());
        } catch (Throwable t) {
            Log.w(TAG, "notify_row: stack child intent failed", t);
        }
    }

    private static void longClickStackChild(View rowView, StatusBarNotification sbn) {
        View child = findChildRow(rowView, sbn);
        longClickRow(child != null ? child : rowView);
        Log.i(TAG, "notify_row: stack child long-press key=" + sbn.getKey()
                + " via=" + (child != null ? "row" : "summary"));
    }

    /** 组子容器里的原生行（容器 GONE 时 getAttachedChildren 依然有效） */
    private static View findChildRow(View rowView, StatusBarNotification sbn) {
        ViewGroup container = childrenContainerOf(rowView);
        if (container == null || sbn == null) {
            return null;
        }
        try {
            Object attached = XposedHelpers.callMethod(container, "getAttachedChildren");
            if (attached instanceof java.util.List) {
                for (Object o : (java.util.List<?>) attached) {
                    if (!(o instanceof View)) {
                        continue;
                    }
                    StatusBarNotification s2 = sbnOf((View) o);
                    if (s2 != null && sbn.getKey().equals(s2.getKey())) {
                        return (View) o;
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        for (int i = 0; i < container.getChildCount(); i++) {
            View v = container.getChildAt(i);
            StatusBarNotification s2 = sbnOf(v);
            if (s2 != null && sbn.getKey().equals(s2.getKey())) {
                return v;
            }
        }
        return null;
    }

    /** 系统层移除该条通知（只在 FLAG_AUTO_CANCEL 时）；原生行点击路径走不到时的兜底 */
    private static void dismissIfAutoCancel(StatusBarNotification sbn) {
        Notification n = sbn.getNotification();
        if (n == null || (n.flags & Notification.FLAG_AUTO_CANCEL) == 0) {
            return;
        }
        try {
            Object svc = XposedHelpers.callStaticMethod(
                    android.app.NotificationManager.class, "getService");
            if (svc == null) {
                return;
            }
            try {
                XposedHelpers.callMethod(svc, "cancelNotificationWithTag",
                        sbn.getPackageName(), sbn.getTag(), sbn.getId(), sbn.getUserId());
            } catch (Throwable t) {
                XposedHelpers.callMethod(svc, "cancelNotificationWithTag",
                        sbn.getPackageName(), sbn.getTag(), sbn.getId(), sbn.getUserId(), 0);
            }
            Log.i(TAG, "notify_row: stack child auto-cancel key=" + sbn.getKey());
        } catch (Throwable t) {
            Log.w(TAG, "notify_row: stack child dismiss failed", t);
        }
    }

    /** 子通知的动作按钮（展开态显示在胶囊左下角）：只认有 title + actionIntent 的 */
    private static java.util.List<IosNotificationRow.StackChild.Action> stackActions(
            StatusBarNotification sbn) {
        java.util.List<IosNotificationRow.StackChild.Action> out =
                new java.util.ArrayList<>();
        Notification n = sbn == null ? null : sbn.getNotification();
        Notification.Action[] acts = n == null ? null : n.actions;
        if (acts == null) {
            return out;
        }
        for (Notification.Action a : acts) {
            if (a == null || a.actionIntent == null || TextUtils.isEmpty(a.title)) {
                continue;
            }
            final PendingIntent pi = a.actionIntent;
            final String title = a.title.toString();
            IosNotificationRow.StackChild.Action item =
                    new IosNotificationRow.StackChild.Action();
            item.title = a.title;
            item.run = () -> {
                try {
                    pi.send();
                    Log.i(TAG, "notify_row: stack action sent title=" + title);
                } catch (Throwable t) {
                    Log.w(TAG, "notify_row: stack action failed title=" + title, t);
                }
            };
            out.add(item);
        }
        return out;
    }
'''

assert old in s, 'openStackCard block not found'
s = s.replace(old, new, 1)

old2 = '''                IosNotificationRow.StackChild item = new IosNotificationRow.StackChild();
                item.icon = appIcon(ctx, child.getPackageName());
                item.title = notificationTitle(cn);
                item.body = notificationText(cn);
                item.time = relativeTime(child.getPostTime());
                kids.add(item);'''
new2 = '''                IosNotificationRow.StackChild item = new IosNotificationRow.StackChild();
                item.icon = appIcon(ctx, child.getPackageName());
                item.title = notificationTitle(cn);
                item.body = notificationText(cn);
                item.time = relativeTime(child.getPostTime());
                final StatusBarNotification childRef = child;
                item.click = v -> openStackChild(row, childRef);
                item.longPress = v -> {
                    longClickStackChild(row, childRef);
                    return true;
                };
                item.actions = stackActions(child);
                kids.add(item);'''
assert old2 in s, 'kid build block not found'
s = s.replace(old2, new2, 1)

io.open(p, 'w', encoding='utf-8').write(s)
print('patched NotifyRowFeature')
