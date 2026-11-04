package com.android.systemui.media;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.PlayerBase;
import android.net.Uri;
import android.os.Looper;
import android.os.PowerManager;
import android.os.SystemClock;
import android.util.Log;
import com.android.internal.annotations.GuardedBy;
import com.vivo.common.utils.LogUtils;
import com.vivo.common.utils.SystemPropertiesFacade;
import java.lang.Thread;
import java.util.LinkedList;

/* JADX INFO: loaded from: classes2.dex */
public class NotificationPlayer implements MediaPlayer.OnCompletionListener, MediaPlayer.OnErrorListener {
    public static final boolean ADJUST_PLAYER_FOR_MTK = useMtkNotificationPlayerForDevices();
    private static final boolean DEBUG = false;
    private static final int PLAY = 1;
    private static final int STOP = 2;

    @GuardedBy({"mQueueAudioFocusLock"})
    private AudioManager mAudioManagerWithAudioFocus;

    @GuardedBy({"mCompletionHandlingLock"})
    private CreationAndCompletionThread mCompletionThread;

    @GuardedBy({"mCompletionHandlingLock"})
    private Looper mLooper;

    @GuardedBy({"mPlayerLock"})
    private MediaPlayer mPlayer;
    private String mTag;

    @GuardedBy({"mCmdQueue"})
    private CmdThread mThread;

    @GuardedBy({"mCmdQueue"})
    private PowerManager.WakeLock mWakeLock;
    private boolean mLockAcquired = false;
    private final LinkedList<Command> mCmdQueue = new LinkedList<>();
    private final Object mCompletionHandlingLock = new Object();
    private final Object mPlayerLock = new Object();
    private final Object mQueueAudioFocusLock = new Object();
    private int mNotificationRampTimeMs = 0;
    private int mState = 2;

    private static final class Command {
        AudioAttributes attributes;
        int code;
        Context context;
        boolean looping;
        long requestTime;
        Uri uri;

        private Command() {
        }

        public String toString() {
            return "{ code=" + this.code + " looping=" + this.looping + " attributes=" + this.attributes + " uri=" + this.uri + " }";
        }
    }

    private final class CreationAndCompletionThread extends Thread {
        public Command mCmd;

        public CreationAndCompletionThread(Command command) {
            this.mCmd = command;
        }

        @Override // java.lang.Thread, java.lang.Runnable
        public void run() {
            MediaPlayer mediaPlayer;
            MediaPlayer mediaPlayer2;
            Looper.prepare();
            NotificationPlayer.this.mLooper = Looper.myLooper();
            synchronized (this) {
                AudioManager audioManager = (AudioManager) this.mCmd.context.getSystemService("audio");
                try {
                    mediaPlayer = new MediaPlayer();
                } catch (Exception e) {
                    e = e;
                    mediaPlayer = null;
                }
                try {
                    if (this.mCmd.attributes == null) {
                        this.mCmd.attributes = new AudioAttributes.Builder().setUsage(5).setContentType(4).build();
                    }
                    mediaPlayer.setAudioAttributes(this.mCmd.attributes);
                    mediaPlayer.setDataSource(this.mCmd.context, this.mCmd.uri);
                    mediaPlayer.setLooping(this.mCmd.looping);
                    mediaPlayer.setOnCompletionListener(NotificationPlayer.this);
                    mediaPlayer.setOnErrorListener(NotificationPlayer.this);
                    mediaPlayer.prepare();
                    if (this.mCmd.uri != null && this.mCmd.uri.getEncodedPath() != null && this.mCmd.uri.getEncodedPath().length() > 0 && !audioManager.isMusicActiveRemotely()) {
                        synchronized (NotificationPlayer.this.mQueueAudioFocusLock) {
                            if (NotificationPlayer.this.mAudioManagerWithAudioFocus == null) {
                                int i = this.mCmd.looping ? 1 : 3;
                                LogUtils.d(NotificationPlayer.this.mTag, "AudioFocus:" + i + " for uri:" + this.mCmd.uri);
                                NotificationPlayer.this.mNotificationRampTimeMs = audioManager.getFocusRampTimeMs(i, this.mCmd.attributes);
                                audioManager.requestAudioFocus(null, this.mCmd.attributes, i, 0);
                                NotificationPlayer.this.mAudioManagerWithAudioFocus = audioManager;
                            }
                        }
                    }
                    try {
                        Thread.sleep(NotificationPlayer.this.mNotificationRampTimeMs);
                    } catch (InterruptedException e2) {
                        Log.e(NotificationPlayer.this.mTag, "Exception while sleeping to sync notification playback with ducking", e2);
                    }
                    mediaPlayer.start();
                } catch (Exception e3) {
                    e = e3;
                    if (mediaPlayer != null) {
                        mediaPlayer.release();
                        mediaPlayer = null;
                    }
                    if (NotificationPlayer.ADJUST_PLAYER_FOR_MTK) {
                        synchronized (NotificationPlayer.this.mCmdQueue) {
                            if (NotificationPlayer.this.mCmdQueue.size() == 0) {
                                NotificationPlayer.this.mThread = null;
                                NotificationPlayer.this.releaseWakeLock();
                            }
                        }
                    }
                    Log.w(NotificationPlayer.this.mTag, "error loading sound for " + this.mCmd.uri, e);
                    NotificationPlayer.this.abandonAudioFocusAfterError();
                }
                synchronized (NotificationPlayer.this.mPlayerLock) {
                    mediaPlayer2 = NotificationPlayer.this.mPlayer;
                    NotificationPlayer.this.mPlayer = mediaPlayer;
                }
                if (mediaPlayer2 != null) {
                    mediaPlayer2.release();
                }
                notify();
            }
            Looper.loop();
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void abandonAudioFocusAfterError() {
        synchronized (this.mQueueAudioFocusLock) {
            if (this.mAudioManagerWithAudioFocus != null) {
                this.mAudioManagerWithAudioFocus.abandonAudioFocus(null);
                this.mAudioManagerWithAudioFocus = null;
            }
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void startSound(Command command) {
        try {
            synchronized (this.mCompletionHandlingLock) {
                if (this.mLooper != null && this.mLooper.getThread().getState() != Thread.State.TERMINATED) {
                    this.mLooper.quit();
                }
                this.mCompletionThread = new CreationAndCompletionThread(command);
                synchronized (this.mCompletionThread) {
                    this.mCompletionThread.start();
                    this.mCompletionThread.wait();
                }
            }
            long jUptimeMillis = SystemClock.uptimeMillis() - command.requestTime;
            if (jUptimeMillis > 1000) {
                Log.w(this.mTag, "Notification sound delayed by " + jUptimeMillis + "msecs");
            }
        } catch (Exception e) {
            if (ADJUST_PLAYER_FOR_MTK) {
                synchronized (this.mCmdQueue) {
                    if (this.mCmdQueue.size() == 0) {
                        this.mThread = null;
                        releaseWakeLock();
                    }
                }
            }
            Log.w(this.mTag, "error loading sound for " + command.uri, e);
        }
    }

    private final class CmdThread extends Thread {
        CmdThread() {
            super("NotificationPlayer-" + NotificationPlayer.this.mTag);
        }

        @Override // java.lang.Thread, java.lang.Runnable
        public void run() {
            Command command;
            MediaPlayer mediaPlayer;
            while (true) {
                synchronized (NotificationPlayer.this.mCmdQueue) {
                    command = (Command) NotificationPlayer.this.mCmdQueue.removeFirst();
                }
                int i = command.code;
                if (i == 1) {
                    NotificationPlayer.this.startSound(command);
                    if (NotificationPlayer.ADJUST_PLAYER_FOR_MTK) {
                        synchronized (NotificationPlayer.this.mCmdQueue) {
                            if (NotificationPlayer.this.mCmdQueue.size() == 0) {
                                NotificationPlayer.this.mThread = null;
                                return;
                            }
                        }
                    }
                } else if (i == 2) {
                    synchronized (NotificationPlayer.this.mPlayerLock) {
                        mediaPlayer = NotificationPlayer.this.mPlayer;
                        NotificationPlayer.this.mPlayer = null;
                    }
                    if (mediaPlayer == null) {
                        Log.w(NotificationPlayer.this.mTag, "STOP command without a player");
                    } else {
                        long jUptimeMillis = SystemClock.uptimeMillis() - command.requestTime;
                        if (jUptimeMillis > 1000) {
                            Log.w(NotificationPlayer.this.mTag, "Notification stop delayed by " + jUptimeMillis + "msecs");
                        }
                        try {
                            mediaPlayer.stop();
                        } catch (Exception unused) {
                        }
                        mediaPlayer.release();
                        synchronized (NotificationPlayer.this.mQueueAudioFocusLock) {
                            if (NotificationPlayer.this.mAudioManagerWithAudioFocus != null) {
                                NotificationPlayer.this.mAudioManagerWithAudioFocus.abandonAudioFocus(null);
                                NotificationPlayer.this.mAudioManagerWithAudioFocus = null;
                            }
                        }
                        synchronized (NotificationPlayer.this.mCompletionHandlingLock) {
                            if (NotificationPlayer.this.mLooper != null && NotificationPlayer.this.mLooper.getThread().getState() != Thread.State.TERMINATED) {
                                NotificationPlayer.this.mLooper.quit();
                            }
                        }
                    }
                    if (NotificationPlayer.ADJUST_PLAYER_FOR_MTK) {
                        synchronized (NotificationPlayer.this.mCmdQueue) {
                            if (NotificationPlayer.this.mCmdQueue.size() == 0) {
                                NotificationPlayer.this.mThread = null;
                                NotificationPlayer.this.releaseWakeLock();
                                return;
                            }
                        }
                    }
                }
                if (!NotificationPlayer.ADJUST_PLAYER_FOR_MTK) {
                    synchronized (NotificationPlayer.this.mCmdQueue) {
                        if (NotificationPlayer.this.mCmdQueue.size() == 0) {
                            NotificationPlayer.this.mThread = null;
                            NotificationPlayer.this.releaseWakeLock();
                            return;
                        }
                    }
                }
            }
        }
    }

    @Override // android.media.MediaPlayer.OnCompletionListener
    public void onCompletion(MediaPlayer mediaPlayer) {
        synchronized (this.mQueueAudioFocusLock) {
            if (this.mAudioManagerWithAudioFocus != null) {
                this.mAudioManagerWithAudioFocus.abandonAudioFocus(null);
                this.mAudioManagerWithAudioFocus = null;
            }
        }
        synchronized (this.mCmdQueue) {
            synchronized (this.mCompletionHandlingLock) {
                if (this.mCmdQueue.size() == 0) {
                    if (this.mLooper != null) {
                        this.mLooper.quit();
                    }
                    this.mCompletionThread = null;
                    if (ADJUST_PLAYER_FOR_MTK) {
                        releaseWakeLock();
                    }
                }
            }
        }
        synchronized (this.mPlayerLock) {
            if (mediaPlayer == this.mPlayer) {
                this.mPlayer = null;
            }
        }
        if (mediaPlayer != null) {
            mediaPlayer.release();
        }
    }

    @Override // android.media.MediaPlayer.OnErrorListener
    public boolean onError(MediaPlayer mediaPlayer, int i, int i2) {
        Log.e(this.mTag, "error " + i + " (extra=" + i2 + ") playing notification");
        onCompletion(mediaPlayer);
        return true;
    }

    public NotificationPlayer(String str) {
        if (str != null) {
            this.mTag = str;
        } else {
            this.mTag = "NotificationPlayer";
        }
    }

    @Deprecated
    public void play(Context context, Uri uri, boolean z, int i) {
        if (uri != null) {
            LogUtils.d(this.mTag, "play1 uri=" + uri.toString());
        }
        PlayerBase.deprecateStreamTypeForPlayback(i, "NotificationPlayer", "play");
        Command command = new Command();
        command.requestTime = SystemClock.uptimeMillis();
        command.code = 1;
        command.context = context;
        command.uri = uri;
        command.looping = z;
        command.attributes = new AudioAttributes.Builder().setInternalLegacyStreamType(i).build();
        synchronized (this.mCmdQueue) {
            enqueueLocked(command);
            this.mState = 1;
        }
    }

    public void play(Context context, Uri uri, boolean z, AudioAttributes audioAttributes) {
        if (uri != null) {
            LogUtils.d(this.mTag, "play2 uri=" + uri.toString());
        }
        Command command = new Command();
        command.requestTime = SystemClock.uptimeMillis();
        command.code = 1;
        command.context = context;
        command.uri = uri;
        command.looping = z;
        command.attributes = audioAttributes;
        synchronized (this.mCmdQueue) {
            enqueueLocked(command);
            this.mState = 1;
        }
    }

    public void stop() {
        synchronized (this.mCmdQueue) {
            if (this.mState != 2) {
                Command command = new Command();
                command.requestTime = SystemClock.uptimeMillis();
                command.code = 2;
                enqueueLocked(command);
                this.mState = 2;
            }
        }
    }

    @GuardedBy({"mCmdQueue"})
    private void enqueueLocked(Command command) {
        this.mCmdQueue.add(command);
        if (this.mThread == null) {
            acquireWakeLock();
            this.mThread = new CmdThread();
            this.mThread.start();
        }
    }

    public void setUsesWakeLock(Context context) {
        synchronized (this.mCmdQueue) {
            if (this.mWakeLock != null || this.mThread != null) {
                throw new RuntimeException("assertion failed mWakeLock=" + this.mWakeLock + " mThread=" + this.mThread);
            }
            this.mWakeLock = ((PowerManager) context.getSystemService("power")).newWakeLock(1, this.mTag);
        }
    }

    @GuardedBy({"mCmdQueue"})
    private void acquireWakeLock() {
        PowerManager.WakeLock wakeLock = this.mWakeLock;
        if (wakeLock == null || this.mLockAcquired) {
            return;
        }
        this.mLockAcquired = true;
        wakeLock.acquire();
    }

    /* JADX INFO: Access modifiers changed from: private */
    @GuardedBy({"mCmdQueue"})
    public void releaseWakeLock() {
        PowerManager.WakeLock wakeLock = this.mWakeLock;
        if (wakeLock == null || !this.mLockAcquired) {
            return;
        }
        wakeLock.release();
        this.mLockAcquired = false;
    }

    private static boolean useMtkNotificationPlayerForDevices() {
        return SystemPropertiesFacade.PRODUCT_MODEL.contains("PD2156");
    }
}
