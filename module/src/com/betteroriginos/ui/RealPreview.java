package com.betteroriginos.ui;

import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Context;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.provider.Settings;

import com.betteroriginos.core.SuCommander;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 真实媒体卡预览加载器：请求 Hook 侧离屏渲染真实卡片，
 * 轮询完成标记后经 su 读取 PNG。同一样式只加载一次（内存缓存）。
 */
public final class RealPreview {
    private static final String TAG = "BetterOriginOS";

    /** bitmap 为 null 表示请求失败/超时 */
    public interface Callback {
        void onResult(Bitmap bitmap);
    }

    private static final String REQ_KEY = "betteroriginos_preview_req";
    private static final String DONE_KEY = "betteroriginos_preview_done";
    private static final String[] HOOK_DIRS = {
            "/data/user_de/0/com.android.systemui/files/preview",
            "/data/user/0/com.android.systemui/files/preview",
    };
    private static final ExecutorService EXEC = Executors.newSingleThreadExecutor();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private static Context appCtx;
    private static String loadedStyle;
    private static Bitmap loaded;

    private RealPreview() {
    }

    public static void init(Context ctx) {
        if (appCtx == null) {
            appCtx = ctx.getApplicationContext();
        }
    }

    public static boolean hasCached(String style) {
        return style != null && style.equals(loadedStyle) && loaded != null;
    }

    public static Bitmap cached() {
        return loaded;
    }

    public static void fetch(final String style, final Callback cb) {
        if (appCtx == null || style == null) {
            return;
        }
        if (hasCached(style)) {
            MAIN.post(new Runnable() {
                @Override
                public void run() {
                    cb.onResult(loaded);
                }
            });
            return;
        }
        EXEC.execute(new Runnable() {
            @Override
            public void run() {
                Bitmap bmp = null;
                try {
                    String nonce = System.currentTimeMillis() + ":" + style;
                    SuCommander.runSu("settings put global " + REQ_KEY + " " + nonce);
                    boolean ready = false;
                    for (int i = 0; i < 20; i++) {
                        Thread.sleep(250);
                        String done = Settings.Global.getString(
                                appCtx.getContentResolver(), DONE_KEY);
                        if (nonce.equals(done)) {
                            ready = true;
                            break;
                        }
                    }
                    if (ready) {
                        Thread.sleep(150);
                        byte[] data = readPreview(style);
                        if (data != null) {
                            bmp = BitmapFactory.decodeByteArray(data, 0, data.length);
                        }
                    }
                } catch (Throwable t) {
                    android.util.Log.e(TAG, "RealPreview fetch failed", t);
                }
                if (bmp != null) {
                    loaded = bmp;
                    loadedStyle = style;
                }
                final Bitmap result = bmp;
                MAIN.post(new Runnable() {
                    @Override
                    public void run() {
                        cb.onResult(result);
                    }
                });
            }
        });
    }

    /** 优先从 MediaStore（Pictures/BetterOriginOS，Hook 发布）读取；su 读文件作为兜底 */
    private static byte[] readPreview(String style) {
        try {
            String name = "card_" + style + ".png";
            Uri collection = MediaStore.Images.Media.getContentUri("external");
            Bundle q = new Bundle();
            q.putString(ContentResolver.QUERY_ARG_SQL_SELECTION,
                    MediaStore.Images.Media.RELATIVE_PATH + "=? AND "
                            + MediaStore.Images.Media.DISPLAY_NAME + "=?");
            q.putStringArray(ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS,
                    new String[]{"Pictures/BetterOriginOS/", name});
            q.putString(ContentResolver.QUERY_ARG_SQL_SORT_ORDER,
                    MediaStore.Images.Media.DATE_ADDED + " DESC");
            q.putInt(ContentResolver.QUERY_ARG_LIMIT, 1);
            Cursor c = appCtx.getContentResolver().query(collection,
                    new String[]{MediaStore.Images.Media._ID}, q, null);
            if (c != null) {
                if (c.moveToFirst()) {
                    Uri uri = ContentUris.withAppendedId(collection, c.getLong(0));
                    java.io.InputStream in = appCtx.getContentResolver().openInputStream(uri);
                    java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
                    byte[] buf = new byte[8192];
                    int n;
                    while ((n = in.read(buf)) > 0) {
                        out.write(buf, 0, n);
                    }
                    in.close();
                    c.close();
                    return out.toByteArray();
                }
                c.close();
            }
            return null;
        } catch (Throwable ignored) {
        }
        for (String base : HOOK_DIRS) {
            byte[] data = SuCommander.catFile(base + "/card_" + style + ".png");
            if (data != null) {
                return data;
            }
        }
        return null;
    }
}
