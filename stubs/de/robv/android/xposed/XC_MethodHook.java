package de.robv.android.xposed;

import java.lang.reflect.Member;

public abstract class XC_MethodHook {

    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
    }

    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
    }

    public static class MethodHookParam {
        public Object thisObject;
        public Object[] args = new Object[0];
        public Member method;
        private Object result;
        private Throwable throwable;

        public Object getResult() {
            return result;
        }

        public void setResult(Object value) {
            result = value;
            throwable = null;
        }

        public Throwable getThrowable() {
            return throwable;
        }

        public boolean hasThrowable() {
            return throwable != null;
        }

        public void setThrowable(Throwable t) {
            throwable = t;
        }
    }

    public static class Unhook {
        public void unhook() {
        }
    }
}
