package com.betteroriginos.debug;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Person;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ShortcutInfo;
import android.content.pm.ShortcutManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Shader;
import android.graphics.drawable.Icon;
import android.media.MediaMetadata;
import android.media.session.MediaSession;
import android.media.session.PlaybackState;
import android.os.Bundle;
import android.util.Log;

import com.betteroriginos.R;
import com.betteroriginos.ui.MainActivity;

import java.util.Collections;

/** 调试用高优先级通知：验证 heads-up / 长按 / 点击 / 滑动 / 分组 / 聊天 overlay */
public final class NotifyTest {
    private static final String TAG = "BetterOriginOS";
    private static final String CHANNEL = "betteroriginos_debug";
    private static final String PLATFORM = "betteroriginos_debug";
    private static final String PLATFORM_MSG = "betteroriginos_debug_msg";
    private static final String PLATFORM_GROUP = "betteroriginos_debug_group";
    private static final String PLATFORM_SWAP = "betteroriginos_debug_swap";
    private static final String PLATFORM_CONV = "betteroriginos_debug_conv";
    private static final String PLATFORM_LONG = "betteroriginos_debug_long";
    private static final String PLATFORM_MEDIA = "betteroriginos_debug_media";
    private static final String PLATFORM_ACT = "betteroriginos_debug_act";
    private static final String SHORTCUT_ID = "betteroriginos_debug_chat";
    private static final int ID = 4242;
    private static final int ID_MSG = 4243;
    private static final int ID_GROUP_SUMMARY = 4244;
    private static final int ID_GROUP_A = 4245;
    private static final int ID_GROUP_B = 4246;
    private static final int ID_SWAP = 4247;
    private static final int ID_CONV = 4248;
    private static final int ID_LONG = 4249;
    private static final int ID_MEDIA = 4250;
    private static final int ID_ACT = 4251;
    private static final int ID_ACT_BIG = 4252;
    private static final String PLATFORM_JUMP = "betteroriginos_debug_jump";
    private static final int ID_JUMP_BASE = 4260;

    private NotifyTest() {
    }

    /** SDK 桩缺 pushDynamicShortcuts，用反射按方法名查找（真机 API 35 存在） */
    private static void pushShortcut(Context ctx, ShortcutInfo info) {
        try {
            ShortcutManager sm =
                    (ShortcutManager) ctx.getSystemService(Context.SHORTCUT_SERVICE);
            if (sm == null) {
                return;
            }
            for (java.lang.reflect.Method m : sm.getClass().getMethods()) {
                if (m.getParameterTypes().length != 1
                        || !m.getName().startsWith("pushDynamicShortcut")) {
                    continue;
                }
                // 该 ROM 只有单数 pushDynamicShortcut(ShortcutInfo)
                Object arg = "pushDynamicShortcut".equals(m.getName())
                        ? info : Collections.singletonList(info);
                m.invoke(sm, arg);
                Log.i(TAG, "debug shortcut pushed via " + m.getName()
                        + " id=" + SHORTCUT_ID);
                return;
            }
            StringBuilder names = new StringBuilder();
            for (java.lang.reflect.Method m : sm.getClass().getMethods()) {
                if (m.getName().contains("push")) {
                    names.append(m.getName()).append(',');
                }
            }
            Log.w(TAG, "debug shortcut method missing, push*=" + names);
        } catch (java.lang.reflect.InvocationTargetException e) {
            Log.w(TAG, "debug shortcut push failed: " + e.getCause(), e);
        } catch (Throwable t) {
            Log.w(TAG, "debug shortcut push failed", t);
        }
    }

    private static NotificationManager nm(Context ctx) {
        NotificationManager nm =
                (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) {
            NotificationChannel channel = new NotificationChannel(CHANNEL,
                    "BetterOriginOS debug", NotificationManager.IMPORTANCE_HIGH);
            channel.setDescription("Heads-up / long-press / click / group test");
            nm.createNotificationChannel(channel);
        }
        return nm;
    }

    private static PendingIntent contentIntent(Context ctx) {
        return PendingIntent.getActivity(ctx, 0,
                new Intent(ctx, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }

    public static void post(Context ctx) {
        try {
            NotificationManager nm = nm(ctx);
            if (nm == null) {
                return;
            }
            Notification n = new Notification.Builder(ctx, CHANNEL)
                    .setSmallIcon(R.drawable.ic_notify)
                    .setContentTitle("BetterOriginOS 测试")
                    .setContentText("点击打开模块，长按查看通知菜单")
                    .setStyle(new Notification.BigTextStyle()
                            .bigText("点击打开模块，长按查看通知菜单；"
                                    + "这条通知同时用于验证 heads-up、滑动删除和聊天 overlay。"))
                    .setContentIntent(contentIntent(ctx))
                    .setAutoCancel(true)
                    .setShowWhen(true)
                    .build();
            nm.notify(PLATFORM, ID, n);
            Log.i(TAG, "debug notify posted id=" + ID);
        } catch (Throwable t) {
            Log.w(TAG, "debug notify post failed", t);
        }
    }

    /** MessagingStyle：验证 NotificationMessagingTemplateViewWrapper 的 iOS overlay */
    public static void postMessaging(Context ctx) {
        try {
            NotificationManager nm = nm(ctx);
            if (nm == null) {
                return;
            }
            long now = System.currentTimeMillis();
            Person me = new Person.Builder().setName("我").build();
            Person xiaoming = new Person.Builder().setName("小明").build();
            Notification.MessagingStyle style = new Notification.MessagingStyle(me)
                    .setConversationTitle("小明")
                    .addMessage("你好，在吗？", now - 60_000L, xiaoming)
                    .addMessage("在的，怎么了", now - 30_000L, me)
                    .addMessage("晚上一起吃饭？", now - 5_000L, xiaoming);
            Bundle extras = new Bundle();
            extras.putCharSequence("android.conversationTitle", "小明");
            Notification n = new Notification.Builder(ctx, CHANNEL)
                    .setSmallIcon(R.drawable.ic_notify)
                    .setContentTitle("小明")
                    .setContentText("晚上一起吃饭？")
                    .setStyle(style)
                    .addExtras(extras)
                    .setCategory(Notification.CATEGORY_MESSAGE)
                    .setContentIntent(contentIntent(ctx))
                    .setAutoCancel(true)
                    .setShowWhen(true)
                    .setWhen(now)
                    .build();
            nm.notify(PLATFORM_MSG, ID_MSG, n);
            Log.i(TAG, "debug messaging posted id=" + ID_MSG);
        } catch (Throwable t) {
            Log.w(TAG, "debug messaging post failed", t);
        }
    }

    /** SDK 桩隐藏 setShortcutInfo，反射写入（会话识别需要 shortcutInfo 非空） */
    private static void setShortcutInfo(Notification.Builder builder, ShortcutInfo info) {
        try {
            java.lang.reflect.Method m = Notification.Builder.class.getMethod(
                    "setShortcutInfo", ShortcutInfo.class);
            m.invoke(builder, info);
            Log.i(TAG, "debug shortcutInfo set");
        } catch (Throwable t) {
            StringBuilder names = new StringBuilder();
            for (Class<?> c = Notification.Builder.class; c != null; c = c.getSuperclass()) {
                for (java.lang.reflect.Method m : c.getDeclaredMethods()) {
                    if (m.getName().contains("Shortcut")) {
                        names.append(m.getName()).append('/')
                                .append(m.getParameterCount()).append(',');
                    }
                }
            }
            Log.w(TAG, "debug shortcutInfo set failed: " + t
                    + " methods=" + names);
        }
    }

    /** 真实 Conversation：发布动态快捷方式 + MessagingStyle，命中原生会话模板 wrapper */
    public static void postConversation(Context ctx) {
        try {
            NotificationManager nm = nm(ctx);
            if (nm == null) {
                return;
            }
            Person xiaoming = new Person.Builder().setName("小明").build();
            ShortcutInfo info = new ShortcutInfo.Builder(ctx, SHORTCUT_ID)
                    .setShortLabel("小明")
                    .setLongLabel("和小明的聊天")
                    .setPerson(xiaoming)
                    .setLongLived(true)
                    .setIntent(new Intent(ctx, MainActivity.class)
                            .setAction(Intent.ACTION_VIEW))
                    .setIcon(Icon.createWithResource(ctx, R.drawable.ic_notify))
                    .build();
            pushShortcut(ctx, info);
            long now = System.currentTimeMillis();
            Person me = new Person.Builder().setName("我").build();
            Notification.MessagingStyle style = new Notification.MessagingStyle(me)
                    .setConversationTitle("小明")
                    .addMessage("快捷方式会话：在吗？", now - 40_000L, xiaoming)
                    .addMessage("在的", now - 20_000L, me)
                    .addMessage("周末见", now - 3_000L, xiaoming);
            Notification.Builder builder = new Notification.Builder(ctx, CHANNEL)
                    .setSmallIcon(R.drawable.ic_notify)
                    .setContentTitle("小明")
                    .setContentText("周末见")
                    .setStyle(style)
                    .setShortcutId(SHORTCUT_ID)
                    .setCategory(Notification.CATEGORY_MESSAGE)
                    .setContentIntent(contentIntent(ctx))
                    .setAutoCancel(true)
                    .setShowWhen(true)
                    .setWhen(now);
            setShortcutInfo(builder, info);
            Notification n = builder.build();
            nm.notify(PLATFORM_CONV, ID_CONV, n);
            Log.i(TAG, "debug conversation posted id=" + ID_CONV);
        } catch (Throwable t) {
            Log.w(TAG, "debug conversation post failed", t);
        }
    }

    /** 超长标题 + 超长正文：验证 2 行标题/4 行正文截断与卡片高度自适应 */
    public static void postLong(Context ctx) {
        try {
            NotificationManager nm = nm(ctx);
            if (nm == null) {
                return;
            }
            String title = "这是一条非常长的通知标题，用来验证两行标题截断，以及卡片高度是否会随着文本换行正确增长";
            StringBuilder sb = new StringBuilder();
            for (int i = 1; i <= 8; i++) {
                sb.append("第").append(i)
                        .append("行正文内容用于验证最多四行截断与省略号是否正常显示。");
            }
            Notification n = new Notification.Builder(ctx, CHANNEL)
                    .setSmallIcon(R.drawable.ic_notify)
                    .setContentTitle(title)
                    .setContentText(sb.toString())
                    .setStyle(new Notification.BigTextStyle().bigText(sb.toString()))
                    .setContentIntent(contentIntent(ctx))
                    .setAutoCancel(true)
                    .setShowWhen(true)
                    .build();
            nm.notify(PLATFORM_LONG, ID_LONG, n);
            Log.i(TAG, "debug long posted id=" + ID_LONG);
        } catch (Throwable t) {
            Log.w(TAG, "debug long post failed", t);
        }
    }

    /**
     * 收纳栏取证样本：一次投满 BULK_N 条普通通知，把列表撑到溢出，
     * 制造"被挤进收纳栏的行"——症状②（划回来丢圆角）与症状③（滚动时收纳栏闪烁）
     * 在没有收纳栏的那一屏根本测不到，缺这一步就会把"没测到"写成"没问题"。
     */
    public static final int BULK_N = 14;
    private static final String PLATFORM_BULK = "betteroriginos_debug_bulk";
    private static final int ID_BULK_BASE = 4280;

    public static void postBulk(Context ctx) {
        try {
            NotificationManager nm = nm(ctx);
            if (nm == null) {
                return;
            }
            for (int i = 1; i <= BULK_N; i++) {
                nm.notify(PLATFORM_BULK, ID_BULK_BASE + i, new Notification.Builder(ctx, CHANNEL)
                        .setSmallIcon(R.drawable.ic_notify)
                        .setContentTitle("收纳栏取证 " + i)
                        .setContentText("第 " + i + " 条：划进收纳栏再划回来，圆角与间距必须还在")
                        .setAutoCancel(true)
                        .setShowWhen(true)
                        .build());
            }
            Log.i(TAG, "debug bulk posted n=" + BULK_N);
        } catch (Throwable t) {
            Log.w(TAG, "debug bulk post failed", t);
        }
    }

    /**
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

    /**
     * 带动作按钮的样本：OS3(A13) 的动作带只在展开态，是"保留展开箭头 + 行高含动作带 +
     * 时间右移让位"这条链路的唯一可控样本（真实样本只有 dynsystem/互传 两条）。
     */
    public static void postActions(Context ctx) {
        try {
            NotificationManager nm = nm(ctx);
            if (nm == null) {
                return;
            }
            PendingIntent pi = contentIntent(ctx);
            Notification.Action reply = new Notification.Action.Builder(
                    Icon.createWithResource(ctx, R.drawable.ic_notify), "回复", pi).build();
            Notification.Action archive = new Notification.Action.Builder(
                    Icon.createWithResource(ctx, R.drawable.ic_notify), "归档", pi).build();
            Notification.Action mute = new Notification.Action.Builder(
                    Icon.createWithResource(ctx, R.drawable.ic_notify), "静音", pi).build();
            nm.notify(PLATFORM_ACT, ID_ACT, new Notification.Builder(ctx, CHANNEL)
                    .setSmallIcon(R.drawable.ic_notify)
                    .setContentTitle("动作按钮取证（base）")
                    .setContentText("展开后应看到两个动作按钮，收起时右侧仍有展开箭头")
                    .addAction(reply).addAction(archive)
                    .setContentIntent(pi)
                    .setAutoCancel(true)
                    .setShowWhen(true)
                    .build());
            nm.notify(PLATFORM_ACT, ID_ACT_BIG, new Notification.Builder(ctx, CHANNEL)
                    .setSmallIcon(R.drawable.ic_notify)
                    .setContentTitle("动作按钮取证（bigText）")
                    .setContentText("三条动作 + 长正文，验证展开后行高与动作带不溢出卡片")
                    .setStyle(new Notification.BigTextStyle().bigText(
                            "这条通知带三条动作，用来验证 A13 上展开态的行高来源"
                                    + "与收起态箭头、时间右移的让位是否互相打架。"))
                    .addAction(reply).addAction(archive).addAction(mute)
                    .setContentIntent(pi)
                    .setAutoCancel(true)
                    .setShowWhen(true)
                    .build());
            Log.i(TAG, "debug actions posted ids=" + ID_ACT + "," + ID_ACT_BIG);
        } catch (Throwable t) {
            Log.w(TAG, "debug actions post failed", t);
        }
    }

    /** 同一 key 先 MessagingStyle 再换成普通样式：验证行复用时 overlay 被清理、不残留 */    public static void postSwap(Context ctx) {
        try {
            final NotificationManager nm = nm(ctx);
            if (nm == null) {
                return;
            }
            long now = System.currentTimeMillis();
            Person me = new Person.Builder().setName("我").build();
            Person xiaoming = new Person.Builder().setName("小明").build();
            Notification.MessagingStyle style = new Notification.MessagingStyle(me)
                    .setConversationTitle("小明")
                    .addMessage("这条马上会被普通样式替换", now - 5_000L, xiaoming);
            Notification msg = new Notification.Builder(ctx, CHANNEL)
                    .setSmallIcon(R.drawable.ic_notify)
                    .setContentTitle("小明")
                    .setContentText("这条马上会被普通样式替换")
                    .setStyle(style)
                    .setCategory(Notification.CATEGORY_MESSAGE)
                    .setContentIntent(contentIntent(ctx))
                    .setShowWhen(true)
                    .build();
            nm.notify(PLATFORM_SWAP, ID_SWAP, msg);
            Log.i(TAG, "debug swap: messaging posted id=" + ID_SWAP);
            new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
                try {
                    Notification base = new Notification.Builder(ctx, CHANNEL)
                            .setSmallIcon(R.drawable.ic_notify)
                            .setContentTitle("样式已切换")
                            .setContentText("同一 key 从聊天模板换成普通模板")
                            .setStyle(new Notification.BigTextStyle()
                                    .bigText("同一 key 从聊天模板换成普通模板，"
                                            + "用于验证 overlay 清理。"))
                            .setContentIntent(contentIntent(ctx))
                            .setShowWhen(true)
                            .build();
                    nm.notify(PLATFORM_SWAP, ID_SWAP, base);
                    Log.i(TAG, "debug swap: base posted id=" + ID_SWAP);
                } catch (Throwable t) {
                    Log.w(TAG, "debug swap base failed", t);
                }
            }, 2000);
        } catch (Throwable t) {
            Log.w(TAG, "debug swap failed", t);
        }
    }

    /** 应用自定义 groupKey：验证拆组后子项各自独立成卡、摘要卡消失 */
    public static void postGroup(Context ctx) {
        try {
            NotificationManager nm = nm(ctx);
            if (nm == null) {
                return;
            }
            String group = "betteroriginos_debug_chat";
            Notification summary = new Notification.Builder(ctx, CHANNEL)
                    .setSmallIcon(R.drawable.ic_notify)
                    .setContentTitle("BetterOriginOS")
                    .setContentText("两条分组测试")
                    .setGroup(group)
                    .setGroupSummary(true)
                    .setAutoCancel(true)
                    .build();
            Notification a = new Notification.Builder(ctx, CHANNEL)
                    .setSmallIcon(R.drawable.ic_notify)
                    .setContentTitle("分组测试 A")
                    .setContentText("同一 groupKey 的子通知 A")
                    .setGroup(group)
                    .setContentIntent(contentIntent(ctx))
                    .setAutoCancel(true)
                    .setShowWhen(true)
                    .build();
            Notification b = new Notification.Builder(ctx, CHANNEL)
                    .setSmallIcon(R.drawable.ic_notify)
                    .setContentTitle("分组测试 B")
                    .setContentText("同一 groupKey 的子通知 B")
                    .setGroup(group)
                    .setContentIntent(contentIntent(ctx))
                    .setAutoCancel(true)
                    .setShowWhen(true)
                    .build();
            nm.notify(PLATFORM_GROUP, ID_GROUP_SUMMARY, summary);
            nm.notify(PLATFORM_GROUP, ID_GROUP_A, a);
            nm.notify(PLATFORM_GROUP, ID_GROUP_B, b);
            Log.i(TAG, "debug group posted ids=" + ID_GROUP_SUMMARY
                    + "," + ID_GROUP_A + "," + ID_GROUP_B);
        } catch (Throwable t) {
            Log.w(TAG, "debug group post failed", t);
        }
    }

    private static MediaSession sMediaSession;

    /** MediaStyle 通知 + 自建 MediaSession：取证/验证通知中心媒体卡，不依赖第三方音乐 App */
    public static void postMedia(Context ctx) {
        try {
            NotificationManager nm = nm(ctx);
            if (nm == null) {
                return;
            }
            if (sMediaSession == null) {
                sMediaSession = new MediaSession(ctx, "betteroriginos_debug_media");
                sMediaSession.setCallback(new MediaSession.Callback() {
                    @Override
                    public void onSeekTo(long pos) {
                        // 调试会话必须真的响应 seek，否则"拖动有没有生效"根本观测不到
                        Log.i(TAG, "debug media onSeekTo pos=" + pos);
                        if (sMediaSession != null) {
                            sMediaSession.setPlaybackState(new PlaybackState.Builder()
                                    .setActions(PlaybackState.ACTION_PLAY | PlaybackState.ACTION_PAUSE
                                            | PlaybackState.ACTION_PLAY_PAUSE
                                            | PlaybackState.ACTION_SKIP_TO_NEXT
                                            | PlaybackState.ACTION_SKIP_TO_PREVIOUS
                                            | PlaybackState.ACTION_SEEK_TO)
                                    .setState(PlaybackState.STATE_PLAYING, pos, 1f)
                                    .build());
                        }
                    }
                });
            }
            sMediaSession.setActive(true);
            Bitmap art = Bitmap.createBitmap(630, 630, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(art);
            android.graphics.Paint paint = new android.graphics.Paint();
            paint.setShader(new LinearGradient(0, 0, 630, 630,
                    new int[]{0xFF2B5C8A, 0xFF7A3E6B, 0xFFB0463C}, null,
                    Shader.TileMode.CLAMP));
            canvas.drawRect(0, 0, 630, 630, paint);

            sMediaSession.setMetadata(new MediaMetadata.Builder()
                    .putString(MediaMetadata.METADATA_KEY_TITLE, "BetterOriginOS 播放器取证")
                    .putString(MediaMetadata.METADATA_KEY_ARTIST, "AuroraXL")
                    .putString(MediaMetadata.METADATA_KEY_ALBUM, "OS3 adapt")
                    .putBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART, art)
                    .putLong(MediaMetadata.METADATA_KEY_DURATION, 214000)
                    .build());
            sMediaSession.setPlaybackState(new PlaybackState.Builder()
                    .setActions(PlaybackState.ACTION_PLAY | PlaybackState.ACTION_PAUSE
                            | PlaybackState.ACTION_PLAY_PAUSE | PlaybackState.ACTION_SKIP_TO_NEXT
                            | PlaybackState.ACTION_SKIP_TO_PREVIOUS
                            | PlaybackState.ACTION_SEEK_TO)
                    .setState(PlaybackState.STATE_PLAYING, 63000, 1f)
                    .build());

            PendingIntent pi = PendingIntent.getBroadcast(ctx, 0,
                    new Intent("com.betteroriginos.DEBUG_MEDIA").setPackage(ctx.getPackageName()),
                    PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
            // 编译用的 android.jar 与 vivo framework 都只有 getSessionToken（无公开 getMediaSessionToken），
            // 按"无参 + 返回 MediaSession$Token"探测方法名，避免写死
            MediaSession.Token token = null;
            for (java.lang.reflect.Method m : MediaSession.class.getMethods()) {
                if (m.getParameterTypes().length != 0
                        || !"android.media.session.MediaSession$Token"
                                .equals(m.getReturnType().getName())) {
                    continue;
                }
                try {
                    token = (MediaSession.Token) m.invoke(sMediaSession);
                    Log.i(TAG, "media token via " + m.getName());
                    break;
                } catch (Throwable ignored) {
                }
            }
            if (token == null) {
                Log.w(TAG, "media session token unavailable");
                return;
            }
            Notification n = new Notification.Builder(ctx, CHANNEL)
                    .setSmallIcon(R.drawable.ic_music)
                    .setContentTitle("BetterOriginOS 播放器取证")
                    .setContentText("AuroraXL · OS3 adapt")
                    .setStyle(new Notification.MediaStyle().setMediaSession(token))
                    .addAction(new Notification.Action.Builder(
                            Icon.createWithResource(ctx, R.drawable.ic_music), "上一首", pi).build())
                    .addAction(new Notification.Action.Builder(
                            Icon.createWithResource(ctx, R.drawable.ic_music), "播放/暂停", pi).build())
                    .addAction(new Notification.Action.Builder(
                            Icon.createWithResource(ctx, R.drawable.ic_music), "下一首", pi).build())
                    .setOngoing(true)
                    .setShowWhen(false)
                    .build();
            nm.notify(PLATFORM_MEDIA, ID_MEDIA, n);
            Log.i(TAG, "debug media notification posted id=" + ID_MEDIA);
        } catch (Throwable t) {
            Log.w(TAG, "debug media post failed", t);
        }
    }

    public static void cancelMedia(Context ctx) {
        try {
            NotificationManager nm = nm(ctx);
            if (nm != null) {
                nm.cancel(PLATFORM_MEDIA, ID_MEDIA);
            }
            if (sMediaSession != null) {
                sMediaSession.release();
                sMediaSession = null;
            }
        } catch (Throwable ignored) {
        }
    }

    public static void cancel(Context ctx) {
        try {
            NotificationManager nm = nm(ctx);
            if (nm == null) {
                return;
            }
            nm.cancel(PLATFORM, ID);
            nm.cancel(PLATFORM_MSG, ID_MSG);
            nm.cancel(PLATFORM_GROUP, ID_GROUP_SUMMARY);
            nm.cancel(PLATFORM_GROUP, ID_GROUP_A);
            nm.cancel(PLATFORM_GROUP, ID_GROUP_B);
            nm.cancel(PLATFORM_SWAP, ID_SWAP);
            nm.cancel(PLATFORM_CONV, ID_CONV);
            nm.cancel(PLATFORM_LONG, ID_LONG);
            nm.cancel(PLATFORM_MEDIA, ID_MEDIA);
            nm.cancel(PLATFORM_ACT, ID_ACT);
            nm.cancel(PLATFORM_ACT, ID_ACT_BIG);
            try {
                ShortcutManager sm =
                        (ShortcutManager) ctx.getSystemService(Context.SHORTCUT_SERVICE);
                if (sm != null) {
                    sm.removeDynamicShortcuts(Collections.singletonList(SHORTCUT_ID));
                }
            } catch (Throwable ignored) {
            }
            Log.i(TAG, "debug notify canceled");
        } catch (Throwable t) {
            Log.w(TAG, "debug notify cancel failed", t);
        }
    }
}
