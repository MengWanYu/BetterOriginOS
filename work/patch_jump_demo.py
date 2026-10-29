import io

p = 'module/src/com/betteroriginos/debug/NotifyTest.java'
s = io.open(p, encoding='utf-8').read()

# constants
anchor = "    private static final int ID_ACT_BIG = 4252;\n"
assert anchor in s
s = s.replace(anchor, anchor + """    private static final String PLATFORM_JUMP = "betteroriginos_debug_jump";
    private static final int ID_JUMP_BASE = 4260;
""", 1)

# add postJump before postActions javadoc
anchor2 = """    /**
     * 带动作按钮的样本：OS3(A13) 的动作带只在展开态，是"保留展开箭头 + 行高含动作带 +
     * 时间右移让位"这条链路的唯一可控样本（真实样本只有 dynsystem/互传 两条）。
     */
"""
assert anchor2 in s
post_jump = '''    /**
     * 跳转回归样本：同一 groupKey 下 3 条通知，各自 contentIntent 携带不同的
     * debug_open 标记 + autoCancel，另带动作按钮。点哪条胶囊就必须跳到哪条对应的标记，
     * 且该条自动消失——用来验证"堆叠展开后单条通知的跳转/自动清除"不再走组摘要语义。
     */
    public static void postJump(Context ctx) {
        try {
            NotificationManager nm = nm(ctx);
            if (nm == null) {
                return;
            }
            String group = PLATFORM_JUMP;
            for (int i = 1; i <= 3; i++) {
                final String tag = "jump" + i;
                Intent open = new Intent(ctx, MainActivity.class)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        .putExtra("debug_open", tag);
                PendingIntent pi = PendingIntent.getActivity(ctx, 100 + i, open,
                        PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
                Notification.Action act = new Notification.Action.Builder(
                        Icon.createWithResource(ctx, R.drawable.ic_notify),
                        "动作" + i, pi).build();
                Notification n = new Notification.Builder(ctx, CHANNEL)
                        .setSmallIcon(R.drawable.ic_notify)
                        .setContentTitle("跳转测试 " + i)
                        .setContentText("点我应跳 debug_open=" + tag + " 并自动消失")
                        .setStyle(new Notification.BigTextStyle()
                                .bigText("跳转测试 " + i + "：点击胶囊应打开主界面并打印 "
                                        + "debug open=" + tag + "，随后本条通知自动消失。"))
                        .setGroup(group)
                        .setContentIntent(pi)
                        .setAutoCancel(true)
                        .setShowWhen(true)
                        .addAction(act)
                        .build();
                nm.notify(PLATFORM_JUMP, ID_JUMP_BASE + i, n);
            }
            Notification summary = new Notification.Builder(ctx, CHANNEL)
                    .setSmallIcon(R.drawable.ic_notify)
                    .setContentTitle("跳转测试组")
                    .setGroup(group)
                    .setGroupSummary(true)
                    .setAutoCancel(true)
                    .setContentIntent(contentIntent(ctx))
                    .build();
            nm.notify(PLATFORM_JUMP, ID_JUMP_BASE, summary);
            Log.i(TAG, "debug jump posted ids=" + ID_JUMP_BASE + ".." + (ID_JUMP_BASE + 3));
        } catch (Throwable t) {
            Log.w(TAG, "debug jump post failed", t);
        }
    }

'''
s = s.replace(anchor2, post_jump + anchor2, 1)
io.open(p, 'w', encoding='utf-8').write(s)
print('NotifyTest patched')

# MainActivity: log debug_open + dispatch postjump
p2 = 'module/src/com/betteroriginos/ui/MainActivity.java'
m = io.open(p2, encoding='utf-8').read()
old = '''    private void handleDebugIntent(Intent intent) {
        String debugNotify = intent == null ? null
                : intent.getStringExtra("debug_notify");
        android.util.Log.i("BetterOriginOS", "debug intent cmd=" + debugNotify
                + " instance=" + Integer.toHexString(System.identityHashCode(this)));
'''
new = '''    private void handleDebugIntent(Intent intent) {
        String debugNotify = intent == null ? null
                : intent.getStringExtra("debug_notify");
        String debugOpen = intent == null ? null : intent.getStringExtra("debug_open");
        if (debugOpen != null) {
            android.util.Log.i("BetterOriginOS", "debug open=" + debugOpen
                    + " instance=" + Integer.toHexString(System.identityHashCode(this)));
        }
        android.util.Log.i("BetterOriginOS", "debug intent cmd=" + debugNotify
                + " instance=" + Integer.toHexString(System.identityHashCode(this)));
'''
assert old in m
m = m.replace(old, new, 1)
old2 = '''        } else if ("postswap".equals(debugNotify)) {'''
new2 = '''        } else if ("postjump".equals(debugNotify)) {
            moveTaskToBack(true);
            new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(
                    () -> com.betteroriginos.debug.NotifyTest.postJump(this), 800);
        } else if ("postswap".equals(debugNotify)) {'''
assert old2 in m
m = m.replace(old2, new2, 1)
io.open(p2, 'w', encoding='utf-8').write(m)
print('MainActivity patched')
