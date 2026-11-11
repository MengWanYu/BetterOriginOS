package com.betteroriginos.core;

import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Looper;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * su 写入队列：同 key 防抖合并、执行结果回调、待生效变更计数。
 * 设置 App 与 SystemUI Hook 进程隔离；Hook 侧不使用本类。
 */
public final class SuCommander {
    public interface Result {
        void onResult(boolean ok);
    }

    public interface PendingListener {
        void onPendingChanged(int count);
    }

    private static final SuCommander INSTANCE = new SuCommander();
    private static final long DEBOUNCE_MS = 250;
    private static final long TIMEOUT_S = 8;

    public static SuCommander get() {
        return INSTANCE;
    }

    private final ThreadPoolExecutor exec = new ThreadPoolExecutor(1, 1, 30,
            TimeUnit.SECONDS, new LinkedBlockingQueue<Runnable>());
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Map<String, Runnable> queued = new HashMap<>();
    private final AtomicInteger pending = new AtomicInteger();
    private final List<PendingListener> listeners =
            new CopyOnWriteArrayList<PendingListener>();

    private SuCommander() {
    }

    public void addPendingListener(PendingListener l) {
        if (l != null && !listeners.contains(l)) {
            listeners.add(l);
            l.onPendingChanged(pending.get());
        }
    }

    public void removePendingListener(PendingListener l) {
        listeners.remove(l);
    }

    public int pendingCount() {
        return pending.get();
    }

    public void write(String key, String value) {
        write(key, value, null);
    }

    public void write(final String key, final String value, final Result result) {
        Runnable old = queued.remove(key);
        if (old != null) {
            main.removeCallbacks(old);
        }
        Runnable r = new Runnable() {
            @Override
            public void run() {
                exec.execute(new Runnable() {
                    @Override
                    public void run() {
                        final boolean ok = runSu("settings put global " + key + " " + value);
                        main.post(new Runnable() {
                            @Override
                            public void run() {
                                if (ok) {
                                    notifyPending(pending.incrementAndGet());
                                }
                                if (result != null) {
                                    result.onResult(ok);
                                }
                            }
                        });
                    }
                });
            }
        };
        queued.put(key, r);
        main.postDelayed(r, DEBOUNCE_MS);
    }

    /** 与 write 相同的串行/去抖语义，但落到 settings 的 system 命名空间。 */
    public void writeSystem(final String key, final String value, final Result result) {
        final String queueKey = "system:" + key;
        Runnable old = queued.remove(queueKey);
        if (old != null) {
            main.removeCallbacks(old);
        }
        Runnable r = new Runnable() {
            @Override
            public void run() {
                exec.execute(new Runnable() {
                    @Override
                    public void run() {
                        final boolean ok = runSu("settings put system " + key + " " + value);
                        main.post(new Runnable() {
                            @Override
                            public void run() {
                                if (ok) {
                                    notifyPending(pending.incrementAndGet());
                                }
                                if (result != null) {
                                    result.onResult(ok);
                                }
                            }
                        });
                    }
                });
            }
        };
        queued.put(queueKey, r);
        main.postDelayed(r, DEBOUNCE_MS);
    }

    public void restartSystemUi(final Result result) {
        exec.execute(new Runnable() {
            @Override
            public void run() {
                final boolean ok = runSu("killall com.android.systemui");
                main.post(new Runnable() {
                    @Override
                    public void run() {
                        if (ok) {
                            pending.set(0);
                            notifyPending(0);
                        }
                        if (result != null) {
                            result.onResult(ok);
                        }
                    }
                });
            }
        });
    }

    private void notifyPending(int count) {
        for (PendingListener l : listeners) {
            l.onPendingChanged(count);
        }
    }

    /** 阻塞式 su 写入（供后台线程的预览请求等使用） */
    public static boolean runSu(String command) {
        try {
            Process p = new ProcessBuilder("su", "-c", command).start();
            if (!p.waitFor(TIMEOUT_S, TimeUnit.SECONDS)) {
                p.destroy();
                return false;
            }
            return p.exitValue() == 0;
        } catch (Throwable t) {
            return false;
        }
    }

    /** 阻塞式 su 读文件（预览 PNG 等）；App 侧 stdout 是管道非 TTY，二进制不会被污染。
     *  必须边读边等：先 waitFor 会因管道写满（输出 > 64KB 缓冲）死锁。 */
    public static byte[] catFile(String path) {
        try {
            Process p = new ProcessBuilder("su", "-c", "cat " + path).start();
            long deadline = System.currentTimeMillis() + TIMEOUT_S * 1000;
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            java.io.InputStream in = p.getInputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) {
                out.write(buf, 0, n);
                if (System.currentTimeMillis() > deadline) {
                    break;
                }
            }
            p.waitFor(2, TimeUnit.SECONDS);
            p.destroy();
            return out.toByteArray();
        } catch (Throwable t) {
            return null;
        }
    }
}
