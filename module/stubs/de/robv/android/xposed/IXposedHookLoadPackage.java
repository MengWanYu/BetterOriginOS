package de.robv.android.xposed;

import de.robv.android.xposed.callbacks.XC_LoadPackage;

/** 编译期桩：运行时由 LSPosed 提供实现 */
public interface IXposedHookLoadPackage {
    void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam);
}
