import io

p = r"C:/Users/Administrator/Desktop/iqooz8-os5-mod/module/src/com/iqoo/os5/mediaprobe/MainHook.java"
s = open(p, encoding="utf-8").read()


def rep(old, new):
    global s
    assert old in s, "MISSING: " + old[:90]
    s = s.replace(old, new, 1)


# imports
rep("import android.content.Context;",
    "import android.content.ComponentName;\n"
    "import android.content.Context;\n"
    "import android.content.Intent;\n"
    "import android.content.ServiceConnection;")
rep("import android.os.Looper;",
    "import android.os.IBinder;\n"
    "import android.os.Looper;\n"
    "import android.os.Parcel;")

# static service bridge fields/methods
rep("""    private static final String TAG = "OS5MOD";
    private static final Map<View, MediaCard> CARDS = new WeakHashMap<>();""",
    """    private static final String TAG = "OS5MOD";
    private static final Map<View, MediaCard> CARDS = new WeakHashMap<>();
    private static final String WIDGET_PKG = "com.vivo.musicwidgetmix";
    private static final String WIDGET_SVC = "com.vivo.musicwidgetmix.service.MusicWidgetMixService";
    private static final String WIDGET_DESC = "com.vivo.musicwidgetmix.IMusicWidgetMixService";
    private static final int TX_SEEK_POS = 15;
    private static volatile IBinder sWidgetBinder;
    private static boolean sWidgetBinding;

    private static final ServiceConnection WIDGET_CONN = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            sWidgetBinder = service;
            Log.i(TAG, "widget service connected");
            synchronized (CARDS) {
                for (MediaCard c : CARDS.values()) {
                    c.updateSeekable();
                }
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            sWidgetBinder = null;
            Log.i(TAG, "widget service disconnected");
        }
    };

    private static void ensureWidgetService(Context ctx) {
        synchronized (MainHook.class) {
            if (sWidgetBinder != null || sWidgetBinding) {
                return;
            }
            sWidgetBinding = true;
            try {
                Intent i = new Intent();
                i.setComponent(new ComponentName(WIDGET_PKG, WIDGET_SVC));
                boolean ok = ctx.bindService(i, WIDGET_CONN, Context.BIND_AUTO_CREATE);
                Log.i(TAG, "bind widget service ok=" + ok);
                if (!ok) {
                    sWidgetBinding = false;
                }
            } catch (Throwable t) {
                sWidgetBinding = false;
                Log.w(TAG, "bind widget service failed", t);
            }
        }
    }

    private static boolean widgetSeekAvailable() {
        return sWidgetBinder != null;
    }

    private static boolean seekViaWidget(long position) {
        IBinder b = sWidgetBinder;
        if (b == null) {
            Log.i(TAG, "widget seek: binder not ready");
            return false;
        }
        Parcel data = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        try {
            data.writeInterfaceToken(WIDGET_DESC);
            data.writeLong(position);
            boolean ok = b.transact(TX_SEEK_POS, data, reply, 0);
            reply.readException();
            Log.i(TAG, "widget seek pos=" + position + " ok=" + ok);
            return ok;
        } catch (Throwable t) {
            Log.w(TAG, "widget seek failed", t);
            return false;
        } finally {
            data.recycle();
            reply.recycle();
        }
    }""")

# bind service when a card is built
rep("""            boolean night = isNight(ctx);
            int primary = night ? 0xFFFFFFFF : 0xFF241A16;""",
    """            ensureWidgetService(ctx);
            boolean night = isNight(ctx);
            int primary = night ? 0xFFFFFFFF : 0xFF241A16;""")

# updateSeekable includes widget-bridge capability
rep("""        void updateSeekable() {
            boolean seekable = false;
            long actions = 0;
            if (controller != null) {
                PlaybackState st = controller.getPlaybackState();
                if (st != null) {
                    actions = st.getActions();
                    seekable = (actions & PlaybackState.ACTION_SEEK_TO) != 0;
                }
            }
            if (seekable == canSeek && bar.isEnabled() == seekable) {
                return;
            }
            canSeek = seekable;
            Log.i(TAG, "seekable=" + seekable + " actions=" + actions);""",
    """        void updateSeekable() {
            boolean nativeSeek = false;
            long actions = 0;
            if (controller != null) {
                PlaybackState st = controller.getPlaybackState();
                if (st != null) {
                    actions = st.getActions();
                    nativeSeek = (actions & PlaybackState.ACTION_SEEK_TO) != 0;
                }
            }
            boolean seekable = nativeSeek || widgetSeekAvailable();
            if (seekable == canSeek && bar.isEnabled() == seekable) {
                return;
            }
            canSeek = seekable;
            Log.i(TAG, "seekable=" + seekable + " native=" + nativeSeek
                    + " widget=" + widgetSeekAvailable() + " actions=" + actions);""")

# seek release: standard session first, vivo widget bridge as fallback
rep("""                public void onStopTrackingTouch(SeekBar seekBar) {
                    mc.dragging = false;
                    if (!mc.canSeek) {
                        mc.updateProgress();
                        return;
                    }
                    long target = seekBar.getProgress();
                    mc.pendingSeekMs = target;
                    mc.suppressUntil = SystemClock.elapsedRealtime() + 2000;
                    if (mc.controller != null) {
                        try {
                            mc.controller.getTransportControls().seekTo(target);
                        } catch (Throwable t) {
                            Log.w(TAG, "seek failed", t);
                        }
                    }
                }""",
    """                public void onStopTrackingTouch(SeekBar seekBar) {
                    mc.dragging = false;
                    if (!mc.canSeek) {
                        mc.updateProgress();
                        return;
                    }
                    long target = seekBar.getProgress();
                    boolean done = false;
                    try {
                        PlaybackState st = mc.controller != null
                                ? mc.controller.getPlaybackState() : null;
                        boolean nativeSeek = st != null
                                && (st.getActions() & PlaybackState.ACTION_SEEK_TO) != 0;
                        if (nativeSeek && mc.controller != null) {
                            mc.controller.getTransportControls().seekTo(target);
                            done = true;
                        }
                    } catch (Throwable t) {
                        Log.w(TAG, "native seek failed", t);
                    }
                    if (!done) {
                        done = MainHook.seekViaWidget(target);
                    }
                    if (done) {
                        mc.pendingSeekMs = target;
                        mc.suppressUntil = SystemClock.elapsedRealtime() + 2000;
                    } else {
                        mc.pendingSeekMs = -1;
                        mc.updateProgress();
                    }
                }""")

open(p, "w", encoding="utf-8").write(s)
print("patched vivo widget seek bridge")
