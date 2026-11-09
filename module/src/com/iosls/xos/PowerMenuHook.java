package com.iosls.xos;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Looper;
import android.os.Process;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.TextView;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.List;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

public final class PowerMenuHook {

    private static final String BASE = "com.android.systemui.globalactions.GlobalActionsDialogLite";
    private static final String TR = "com.transsion.systemui.globalactions.TrGlobalActionsDialogLite";
    private static final String LABEL = "Restart SystemUI";

    private static boolean sInstalled;

    private PowerMenuHook() {
    }

    public static void install(ClassLoader classLoader) {
        if (sInstalled) {
            return;
        }
        hookClass(classLoader, BASE);
        hookClass(classLoader, TR);
        sInstalled = true;
    }

    private static void hookClass(ClassLoader classLoader, String className) {
        try {
            Class<?> clazz = XposedHelpers.findClass(className, classLoader);
            XposedBridge.hookAllMethods(clazz, "createActionItems", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    try {
                        inject(param.thisObject);
                    } catch (Throwable t) {
                        MainHook.log(t);
                    }
                }
            });
            MainHook.log("power menu hooked: " + className);
        } catch (Throwable t) {
            MainHook.log("power menu class not found: " + className);
        }
    }

    private static void inject(Object dialog) throws Exception {
        Class<?> actionClass = Class.forName(BASE + "$Action", false, dialog.getClass().getClassLoader());
        if (!actionClass.isInterface()) {
            MainHook.log("Action is not an interface, skipping power menu entry");
            return;
        }
        List items = null;
        try {
            Object value = XposedHelpers.getObjectField(dialog, "mItems");
            if (value instanceof List) {
                items = (List) value;
            }
        } catch (Throwable ignored) {
        }
        if (items == null) {
            try {
                Object value = XposedHelpers.getObjectField(dialog, "mOverflowItems");
                if (value instanceof List) {
                    items = (List) value;
                }
            } catch (Throwable ignored) {
            }
        }
        if (items == null) {
            MainHook.log("no action list found on " + dialog.getClass().getName());
            return;
        }
        for (Object existing : items) {
            if (LABEL.equals(String.valueOf(existing))) {
                return;
            }
        }
        Object action = Proxy.newProxyInstance(
                actionClass.getClassLoader(),
                new Class<?>[]{actionClass},
                new RestartHandler());
        items.add(action);
        MainHook.log("Restart SystemUI injected, total actions=" + items.size());
    }

    private static final class RestartHandler implements InvocationHandler {

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) {
            String name = method.getName();
            if ("toString".equals(name)) {
                return LABEL;
            }
            if ("hashCode".equals(name)) {
                return System.identityHashCode(proxy);
            }
            if ("equals".equals(name)) {
                return args != null && args.length > 0 && proxy == args[0];
            }
            if ("getMessage".equals(name)) {
                return LABEL;
            }
            if ("getMessageResId".equals(name)) {
                return 0;
            }
            if ("getIcon".equals(name)) {
                return icon(args);
            }
            if ("isEnabled".equals(name) || "shouldShow".equals(name) || "showBeforeProvisioning".equals(name)) {
                return Boolean.TRUE;
            }
            if ("create".equals(name)) {
                return createRow(args);
            }
            if ("onPress".equals(name)) {
                restartSystemUi();
                return null;
            }
            if ("onLongPress".equals(name)) {
                return Boolean.FALSE;
            }
            return defaultValue(method.getReturnType());
        }
    }

    private static View createRow(Object[] args) {
        if (args == null || args.length == 0 || !(args[0] instanceof Context)) {
            return null;
        }
        Context context = (Context) args[0];
        TextView view = new TextView(context);
        view.setText(LABEL);
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f);
        view.setTextColor(0xFFFFFFFF);
        view.setGravity(Gravity.CENTER_VERTICAL);
        view.setSingleLine(true);
        int padding = dp(context, 16);
        view.setPadding(padding, padding, padding, padding);
        view.setMinHeight(dp(context, 64));
        return view;
    }

    private static Drawable icon(Object[] args) {
        if (args == null || args.length == 0 || !(args[0] instanceof Context)) {
            return null;
        }
        try {
            return ((Context) args[0]).getDrawable(android.R.drawable.ic_popup_sync);
        } catch (Throwable t) {
            return null;
        }
    }

    private static void restartSystemUi() {
        MainHook.log("Restart SystemUI pressed");
        new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
            @Override
            public void run() {
                try {
                    Process.killProcess(Process.myPid());
                } catch (Throwable t) {
                    MainHook.log(t);
                }
            }
        }, 700);
    }

    private static Object defaultValue(Class<?> type) {
        if (type == boolean.class) {
            return Boolean.FALSE;
        }
        if (type == int.class) {
            return 0;
        }
        if (type == long.class) {
            return 0L;
        }
        if (type == float.class) {
            return 0f;
        }
        if (type == double.class) {
            return 0d;
        }
        if (type == short.class) {
            return (short) 0;
        }
        if (type == byte.class) {
            return (byte) 0;
        }
        if (type == char.class) {
            return (char) 0;
        }
        return null;
    }

    private static int dp(Context context, int value) {
        return (int) (value * context.getResources().getDisplayMetrics().density + 0.5f);
    }
}
