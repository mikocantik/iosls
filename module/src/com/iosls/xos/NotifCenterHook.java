package com.iosls.xos;

import android.app.KeyguardManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import java.lang.ref.WeakReference;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

public final class NotifCenterHook {

    private static final String NSSL = "com.android.systemui.statusbar.notification.stack.NotificationStackScrollLayout";
    private static final String TR_NSSL = "com.android.systemui.statusbar.notification.stack.TrNotificationStackScrollLayout";

    private static final int[] STACK_LOC = new int[2];
    private static final int[] ROOT_LOC = new int[2];
    private static final int[] ROW_LOC = new int[2];

    private static WeakReference<ViewGroup> sStack = new WeakReference<>(null);
    private static TextView sPill;
    private static ViewGroup sRoot;
    private static boolean sRevealed;
    private static boolean sDumpedChildren;
    private static boolean sRefreshPosted;
    private static boolean sScreenReceiverRegistered;

    private NotifCenterHook() {
    }

    public static void install(ClassLoader classLoader) {
        Class<?> nsslClass = XposedHelpers.findClass(NSSL, classLoader);
        XposedBridge.hookAllConstructors(nsslClass, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                if (!(param.thisObject instanceof ViewGroup)) {
                    return;
                }
                final ViewGroup stack = (ViewGroup) param.thisObject;
                sStack = new WeakReference<>(stack);
                MainHook.log("stack created: " + stack.getClass().getName());
                stack.post(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            ensureAttached(stack, 0);
                        } catch (Throwable t) {
                            MainHook.log(t);
                        }
                    }
                });
            }
        });

        XC_MethodHook refreshHook = new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                if (param.thisObject instanceof ViewGroup) {
                    refreshSoon((ViewGroup) param.thisObject);
                }
            }
        };
        XposedBridge.hookAllMethods(nsslClass, "updateChildren", refreshHook);
        XposedBridge.hookAllMethods(nsslClass, "onLayout", refreshHook);
        try {
            Class<?> trClass = XposedHelpers.findClass(TR_NSSL, classLoader);
            XposedBridge.hookAllMethods(trClass, "updateChildren", refreshHook);
            XposedBridge.hookAllMethods(trClass, "onLayout", refreshHook);
        } catch (Throwable ignored) {
        }
        MainHook.log("notification center hooks installed");
    }

    private static void ensureAttached(final ViewGroup stack, final int attempt) {
        if (sPill != null && sPill.isAttachedToWindow()) {
            return;
        }
        if (stack.isAttachedToWindow()) {
            try {
                attach(stack);
            } catch (Throwable t) {
                MainHook.log(t);
            }
            return;
        }
        if (attempt < 120) {
            stack.postDelayed(new Runnable() {
                @Override
                public void run() {
                    try {
                        ensureAttached(stack, attempt + 1);
                    } catch (Throwable t) {
                        MainHook.log(t);
                    }
                }
            }, 500);
        }
    }

    private static void attach(final ViewGroup stack) {
        if (sPill != null && sPill.isAttachedToWindow() && sRoot != null) {
            refresh(stack);
            return;
        }
        View rootView = stack.getRootView();
        if (!(rootView instanceof ViewGroup) || rootView == stack) {
            MainHook.log("attach deferred, root=" + rootView);
            return;
        }
        final ViewGroup root = (ViewGroup) rootView;
        Context context = stack.getContext();

        TextView pill = new TextView(context);
        GradientDrawable background = new GradientDrawable();
        background.setColor(0x4DFFFFFF);
        background.setCornerRadius(dp(context, 24));
        pill.setBackground(background);
        pill.setTextColor(0xFFFFFFFF);
        pill.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f);
        pill.setGravity(Gravity.CENTER);
        pill.setSingleLine(true);
        pill.setPadding(dp(context, 18), dp(context, 10), dp(context, 18), dp(context, 10));
        pill.setVisibility(View.GONE);
        pill.setOnTouchListener(new PillTouchListener());

        root.addView(pill);
        sPill = pill;
        sRoot = root;

        stack.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
            @Override
            public void onLayoutChange(View v, int left, int top, int right, int bottom,
                                       int oldLeft, int oldTop, int oldRight, int oldBottom) {
                try {
                    refresh(stack);
                } catch (Throwable t) {
                    MainHook.log(t);
                }
            }
        });

        if (!sScreenReceiverRegistered) {
            try {
                IntentFilter filter = new IntentFilter(Intent.ACTION_SCREEN_OFF);
                context.registerReceiver(new BroadcastReceiver() {
                    @Override
                    public void onReceive(Context ctx, Intent intent) {
                        sRevealed = false;
                        refresh(stack);
                    }
                }, filter);
                sScreenReceiverRegistered = true;
            } catch (Throwable t) {
                MainHook.log(t);
            }
        }

        MainHook.log("pill attached to " + root.getClass().getName());
        refresh(stack);
    }

    private static void refreshSoon(final ViewGroup stack) {
        if (sRefreshPosted) {
            return;
        }
        sRefreshPosted = true;
        stack.post(new Runnable() {
            @Override
            public void run() {
                sRefreshPosted = false;
                try {
                    refresh(stack);
                } catch (Throwable t) {
                    MainHook.log(t);
                }
            }
        });
    }

    private static void refresh(ViewGroup stack) {
        TextView pill = sPill;
        if (pill == null || !pill.isAttachedToWindow()) {
            ensureAttached(stack, 0);
            return;
        }
        int count = countRows(stack);
        boolean locked = isKeyguardLocked(stack.getContext());

        if (!sDumpedChildren) {
            sDumpedChildren = true;
            StringBuilder dump = new StringBuilder();
            for (int i = 0; i < stack.getChildCount() && i < 12; i++) {
                dump.append(i).append(':').append(stack.getChildAt(i).getClass().getSimpleName()).append(' ');
            }
            MainHook.log("stack children=" + stack.getChildCount() + " rows=" + count
                    + " locked=" + locked + " [" + dump + "]");
        }

        if (count == 0 || !locked) {
            sRevealed = false;
            restoreRows(stack);
            if (pill.getVisibility() != View.GONE) {
                pill.setVisibility(View.GONE);
            }
            return;
        }
        if (sRevealed) {
            restoreRows(stack);
            if (pill.getVisibility() != View.GONE) {
                pill.setVisibility(View.GONE);
            }
            return;
        }

        hideRows(stack);
        positionPill(stack);
        String text = count == 1 ? "1 notification" : count + " notifications";
        if (!text.contentEquals(pill.getText())) {
            pill.setText(text);
        }
        if (pill.getVisibility() != View.VISIBLE) {
            pill.setVisibility(View.VISIBLE);
        }
    }

    private static void reveal() {
        sRevealed = true;
        ViewGroup stack = sStack.get();
        if (stack != null) {
            restoreRows(stack);
        }
        if (sPill != null) {
            sPill.setVisibility(View.GONE);
        }
        MainHook.log("notifications revealed");
    }

    private static int countRows(ViewGroup stack) {
        int count = 0;
        for (int i = 0; i < stack.getChildCount(); i++) {
            View child = stack.getChildAt(i);
            if (child.getVisibility() == View.GONE) {
                continue;
            }
            String name = child.getClass().getName();
            if (name.endsWith("ExpandableNotificationRow")) {
                count++;
            }
        }
        return count;
    }

    private static View firstRow(ViewGroup stack) {
        for (int i = 0; i < stack.getChildCount(); i++) {
            View child = stack.getChildAt(i);
            if (child.getVisibility() == View.GONE) {
                continue;
            }
            if (child.getClass().getName().endsWith("ExpandableNotificationRow")) {
                return child;
            }
        }
        return null;
    }

    private static void hideRows(ViewGroup stack) {
        for (int i = 0; i < stack.getChildCount(); i++) {
            View child = stack.getChildAt(i);
            if (child.getClass().getName().endsWith("ExpandableNotificationRow") && child.getAlpha() != 0f) {
                child.setAlpha(0f);
            }
        }
    }

    private static void restoreRows(ViewGroup stack) {
        for (int i = 0; i < stack.getChildCount(); i++) {
            View child = stack.getChildAt(i);
            if (child.getClass().getName().endsWith("ExpandableNotificationRow") && child.getAlpha() != 1f) {
                child.setAlpha(1f);
            }
        }
    }

    private static void positionPill(ViewGroup stack) {
        TextView pill = sPill;
        ViewGroup root = sRoot;
        if (pill == null || root == null) {
            return;
        }
        stack.getLocationInWindow(STACK_LOC);
        root.getLocationInWindow(ROOT_LOC);
        int x = STACK_LOC[0];
        int y = STACK_LOC[1];
        int width = stack.getWidth();

        View row = firstRow(stack);
        if (row != null && row.getWidth() > 0) {
            row.getLocationInWindow(ROW_LOC);
            x = ROW_LOC[0];
            y = ROW_LOC[1];
            width = row.getWidth();
        }

        ViewGroup.LayoutParams params = pill.getLayoutParams();
        if (params != null && params.width != width) {
            params.width = width;
            pill.setLayoutParams(params);
        }
        pill.setTranslationX(x - ROOT_LOC[0]);
        pill.setTranslationY(y - ROOT_LOC[1]);
    }

    private static boolean isKeyguardLocked(Context context) {
        try {
            KeyguardManager manager = (KeyguardManager) context.getSystemService(Context.KEYGUARD_SERVICE);
            return manager != null && manager.isKeyguardLocked();
        } catch (Throwable t) {
            return false;
        }
    }

    private static int dp(Context context, int value) {
        return (int) (value * context.getResources().getDisplayMetrics().density + 0.5f);
    }

    private static final class PillTouchListener implements View.OnTouchListener {

        private float downY;

        @Override
        public boolean onTouch(View view, MotionEvent event) {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    downY = event.getRawY();
                    return true;
                case MotionEvent.ACTION_MOVE:
                    if (!sRevealed && downY - event.getRawY() > dp(view.getContext(), 8)) {
                        reveal();
                    }
                    return true;
                case MotionEvent.ACTION_UP:
                    if (!sRevealed) {
                        reveal();
                    }
                    return true;
                default:
                    return false;
            }
        }
    }
}
