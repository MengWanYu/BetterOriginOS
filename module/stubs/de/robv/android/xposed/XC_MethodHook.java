package de.robv.android.xposed;

import java.lang.reflect.Member;

/** 编译期桩：运行时由 LSPosed 提供实现 */
public abstract class XC_MethodHook {
    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
    }

    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
    }

    public static final class MethodHookParam {
        public Member method;
        public Object[] args;
        public Object thisObject;
        private Object result = null;
        private Throwable throwable = null;

        public Object getResult() {
            return result;
        }

        public void setResult(Object result) {
            this.result = result;
            this.throwable = null;
        }

        public Throwable getThrowable() {
            return throwable;
        }

        public void setThrowable(Throwable throwable) {
            this.throwable = throwable;
            this.result = null;
        }
    }

    public static final class Unhook {
        public void unhook() {
            throw new UnsupportedOperationException("stub");
        }

        public Member getHookedMethod() {
            throw new UnsupportedOperationException("stub");
        }
    }
}
