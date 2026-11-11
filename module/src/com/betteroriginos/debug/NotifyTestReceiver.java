package com.betteroriginos.debug;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

/** 调试入口：adb broadcast 投递/取消测试通知 */
public final class NotifyTestReceiver extends BroadcastReceiver {
    private static final String TAG = "BetterOriginOS";

    @Override
    public void onReceive(Context context, Intent intent) {
        // 清单里声明了 android:permission=DUMP，只有 shell/root/system 能触发
        String cmd = intent == null ? null : intent.getStringExtra("cmd");
        Log.i(TAG, "debug receiver cmd=" + cmd);
        if ("cancel".equals(cmd)) {
            NotifyTest.cancel(context);
        } else if ("cancelmedia".equals(cmd)) {
            NotifyTest.cancelMedia(context);
        } else if ("postact".equals(cmd)) {
            NotifyTest.postActions(context);
        } else if ("postmedia".equals(cmd)) {
            NotifyTest.postMedia(context);
        } else if ("postall".equals(cmd)) {
            // 广播触发而不是 Activity：Activity 会把设置页顶到前台，挡住 heads-up 取证
            NotifyTest.post(context);
            NotifyTest.postMessaging(context);
            NotifyTest.postGroup(context);
            NotifyTest.postConversation(context);
            NotifyTest.postLong(context);
            NotifyTest.postActions(context);
        } else if ("postmsg".equals(cmd)) {
            NotifyTest.postMessaging(context);
        } else if ("postgroup".equals(cmd)) {
            NotifyTest.postGroup(context);
        } else if ("postconv".equals(cmd)) {
            NotifyTest.postConversation(context);
        } else if ("postlong".equals(cmd)) {
            NotifyTest.postLong(context);
        } else {
            NotifyTest.post(context);
        }
    }
}
