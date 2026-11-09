package de.robv.android.xposed;

public final class XposedHelpers {

    private XposedHelpers() {
    }

    public static Class<?> findClass(String className, ClassLoader classLoader) {
        return null;
    }

    public static XC_MethodHook.Unhook findAndHookMethod(Class<?> clazz, String methodName, Object... parameterTypesAndCallback) {
        return null;
    }

    public static Object getObjectField(Object obj, String fieldName) {
        return null;
    }

    public static void setObjectField(Object obj, String fieldName, Object value) {
    }
}
