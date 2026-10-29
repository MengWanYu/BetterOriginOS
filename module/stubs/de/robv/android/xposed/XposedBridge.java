package de.robv.android.xposed;

import java.util.Set;

/** 编译期桩：运行时由 LSPosed 提供实现 */
public final class XposedBridge {
    private XposedBridge() {
    }

    public static Set<XC_MethodHook.Unhook> hookAllMethods(Class<?> hookClass,
                                                           String methodName,
                                                           XC_MethodHook callback) {
        throw new UnsupportedOperationException("stub");
    }

    public static Set<XC_MethodHook.Unhook> hookAllConstructors(Class<?> hookClass,
                                                                XC_MethodHook callback) {
        throw new UnsupportedOperationException("stub");
    }

    /** 运行时由 LSPosed 提供：直接调原方法（用于把易崩的厂商实现包进 try/catch） */
    public static Object invokeOriginalMethod(java.lang.reflect.Member method,
                                              Object thisObject,
                                              Object[] args) throws Throwable {
        throw new UnsupportedOperationException("stub");
    }

    public static void log(String text) {
        throw new UnsupportedOperationException("stub");
    }

    public static void log(Throwable t) {
        throw new UnsupportedOperationException("stub");
    }
}
