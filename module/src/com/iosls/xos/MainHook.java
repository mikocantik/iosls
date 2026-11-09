package com.iosls.xos;

import android.util.Log;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public final class MainHook implements IXposedHookLoadPackage {

    public static final String TAG = "iOSLS";
    private static final String SYSTEMUI = "com.android.systemui";

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) {
        if (!SYSTEMUI.equals(lpparam.packageName)) {
            return;
        }
        log("loaded package=" + lpparam.packageName + " process=" + lpparam.processName);
        if (lpparam.processName != null && lpparam.processName.contains(":")) {
            log("secondary process, skipping UI hooks");
            return;
        }
        try {
            PowerMenuHook.install(lpparam.classLoader);
        } catch (Throwable t) {
            log(t);
        }
        try {
            NotifCenterHook.install(lpparam.classLoader);
        } catch (Throwable t) {
            log(t);
        }
    }

    public static void log(String message) {
        XposedBridge.log(TAG + ": " + message);
    }

    public static void log(Throwable t) {
        XposedBridge.log(TAG + ": " + Log.getStackTraceString(t));
    }
}
