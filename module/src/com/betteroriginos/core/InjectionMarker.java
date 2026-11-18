package com.betteroriginos.core;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * 注入标记：每个 scope 进程加载模块后，在自己的数据目录里写 "<pid>:<fingerprint>"。
 * 用 App 私有 SharedPreferences 而不是 Settings.Global——除 system uid 外很多宿主
 * 进程没有 WRITE_SECURE_SETTINGS，写 settings 会静默失败。
 * 设置 App 用 root 读回这个文件，和 ps 里的 pid 对比，就能区分
 * “进程里真的有模块”与“LSPosed 显示已启用但没注入”（OTA 后常见）。
 */
public final class InjectionMarker {
    public static final String FILE = "betteroriginos_inject";
    public static final String KEY = "mark";

    private InjectionMarker() {
    }

    public static void mark(Context ctx, String packageName) {
        try {
            SharedPreferences sp = ctx.getSharedPreferences(FILE, 0);
            String value = android.os.Process.myPid() + ":" + android.os.Build.FINGERPRINT;
            if (!value.equals(sp.getString(KEY, null))) {
                sp.edit().putString(KEY, value).commit();
            }
        } catch (Throwable ignored) {
        }
    }
}
