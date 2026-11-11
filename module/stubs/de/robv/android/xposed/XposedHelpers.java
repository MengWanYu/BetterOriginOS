package de.robv.android.xposed;

/** 编译期桩：运行时由 LSPosed 提供实现 */
public final class XposedHelpers {
    private XposedHelpers() {
    }

    public static Class<?> findClass(String className, ClassLoader classLoader) {
        throw new UnsupportedOperationException("stub");
    }

    public static Class<?> findClassIfExists(String className, ClassLoader classLoader) {
        throw new UnsupportedOperationException("stub");
    }

    public static Object newInstance(Class<?> clazz, Object... args) {
        throw new UnsupportedOperationException("stub");
    }

    public static XC_MethodHook.Unhook findAndHookMethod(Class<?> clazz, String methodName,
                                                         Object... parameterTypesAndCallback) {
        throw new UnsupportedOperationException("stub");
    }

    public static Object callStaticMethod(Class<?> clazz, String methodName, Object... args) {
        throw new UnsupportedOperationException("stub");
    }

    public static Object callMethod(Object obj, String methodName, Object... args) {
        throw new UnsupportedOperationException("stub");
    }

    public static Object getObjectField(Object obj, String fieldName) {
        throw new UnsupportedOperationException("stub");
    }

    public static int getIntField(Object obj, String fieldName) {
        throw new UnsupportedOperationException("stub");
    }

    public static boolean getBooleanField(Object obj, String fieldName) {
        throw new UnsupportedOperationException("stub");
    }

    public static float getFloatField(Object obj, String fieldName) {
        throw new UnsupportedOperationException("stub");
    }

    public static void setIntField(Object obj, String fieldName, int value) {
        throw new UnsupportedOperationException("stub");
    }

    public static void setBooleanField(Object obj, String fieldName, boolean value) {
        throw new UnsupportedOperationException("stub");
    }

    public static void setFloatField(Object obj, String fieldName, float value) {
        throw new UnsupportedOperationException("stub");
    }

    public static void setObjectField(Object obj, String fieldName, Object value) {
        throw new UnsupportedOperationException("stub");
    }

    public static Object getStaticObjectField(Class<?> clazz, String fieldName) {
        throw new UnsupportedOperationException("stub");
    }

    public static void setStaticObjectField(Class<?> clazz, String fieldName, Object value) {
        throw new UnsupportedOperationException("stub");
    }
}
