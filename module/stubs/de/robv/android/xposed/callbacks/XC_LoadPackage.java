package de.robv.android.xposed.callbacks;

import de.robv.android.xposed.XC_MethodHook;

/** 编译期桩：运行时由 LSPosed 提供实现 */
public abstract class XC_LoadPackage extends XC_MethodHook {
    public static final class LoadPackageParam {
        public String packageName;
        public String processName;
        public ClassLoader classLoader;
        public boolean isFirstApplication;
    }
}
