package de.robv.android.xposed;

/** 编译期桩：运行时由 LSPosed 提供实现 */
public final class XSharedPreferences {
    public XSharedPreferences(String packageName) {
        throw new UnsupportedOperationException("stub");
    }

    public XSharedPreferences(String packageName, String prefFileName) {
        throw new UnsupportedOperationException("stub");
    }

    public void reload() {
        throw new UnsupportedOperationException("stub");
    }

    public String getString(String key, String defaultValue) {
        throw new UnsupportedOperationException("stub");
    }

    public boolean getBoolean(String key, boolean defaultValue) {
        throw new UnsupportedOperationException("stub");
    }
}
