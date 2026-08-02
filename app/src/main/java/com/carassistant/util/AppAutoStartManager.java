/*
 * Copyright (C) 2026 CarAssistant Project. All rights reserved.
 *
 * 版权所有 (C) 2026 车机助手项目
 * 保留所有权利
 *
 * 本源代码受著作权法保护，未经著作权人书面许可，不得以任何形式复制、修改、
 * 分发、出售或逆向工程。违反者将承担法律责任。
 *
 * Source code protected by copyright law. Unauthorized copying, modification,
 * distribution, sale, or reverse engineering without written permission is
 * prohibited and subject to legal action.
 */

package com.carassistant.util;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.SystemClock;
import android.provider.Settings;
import android.util.Log;

import com.carassistant.receiver.AppAutoStartReceiver;

import java.util.List;

/**
 * 开机自启应用管理器
 *
 * 移植自「侧边栏_方控_开启启动三方应用三合一」APK 的 AppAutoStartManager，
 * 适配本项目包名与数据结构（复用 PrefsUtil 的 boot_app_packages 列表）。
 *
 * 核心机制：
 * - 开机后通过 AlarmManager 闹钟链顺序启动各应用，避免开机系统繁忙导致 startActivity 丢失
 * - 使用 boot_count 全局设置项作为去重令牌，保证同一次开机只调度一次
 * - 三重兜底：BootReceiver / KeyMappingAccessibilityService / TargetMediaSessionService
 *   均会调用 scheduleFromBoot()，靠 boot_count 幂等
 *
 * 启动参数存储在 car_assistant_prefs：
 * - boot_autostart_enabled：总开关（默认 true）
 * - boot_autostart_delay：首次启动延迟秒数（默认 5）
 * - boot_autostart_interval：相邻应用间隔秒数（默认 3）
 * - boot_autostart_return_home：启动完成后是否回桌面（默认 true）
 * - boot_autostart_last_token：上次调度的 boot_count，用于去重
 *
 * 应用列表复用 PrefsUtil.getBootApps()（key=boot_app_packages，JSON 数组），
 * 保证与 AutostartActivity 已有数据兼容。
 */
public final class AppAutoStartManager {

    private static final String TAG = "AppAutoStartManager";

    private static final String PREF_NAME = "car_assistant_prefs";
    private static final String KEY_ENABLED = "boot_autostart_enabled";
    private static final String KEY_DELAY = "boot_autostart_delay";
    private static final String KEY_INTERVAL = "boot_autostart_interval";
    private static final String KEY_RETURN_HOME = "boot_autostart_return_home";
    private static final String KEY_LAST_TOKEN = "boot_autostart_last_token";

    /** AlarmManager 请求码基址，避免与其他 PendingIntent 冲突 */
    private static final int REQUEST_BASE = 17300;
    /** 闹钟 extra：当前启动的应用索引 */
    static final String EXTRA_INDEX = "index";
    /** 闹钟 extra：是否为收尾回桌面环节 */
    static final String EXTRA_RETURN_HOME = "return_home";

    private AppAutoStartManager() {}

    private static SharedPreferences sp(Context ctx) {
        return ctx.getApplicationContext().getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    // ============ 参数读写 ============

    public static boolean isEnabled(Context ctx) {
        return sp(ctx).getBoolean(KEY_ENABLED, true);
    }

    public static void setEnabled(Context ctx, boolean enabled) {
        sp(ctx).edit().putBoolean(KEY_ENABLED, enabled).apply();
    }

    public static int getDelay(Context ctx) {
        return sp(ctx).getInt(KEY_DELAY, 5);
    }

    public static void setDelay(Context ctx, int seconds) {
        sp(ctx).edit().putInt(KEY_DELAY, clamp(seconds)).apply();
    }

    public static int getInterval(Context ctx) {
        return sp(ctx).getInt(KEY_INTERVAL, 3);
    }

    public static void setInterval(Context ctx, int seconds) {
        sp(ctx).edit().putInt(KEY_INTERVAL, clamp(seconds)).apply();
    }

    public static boolean isReturnHome(Context ctx) {
        return sp(ctx).getBoolean(KEY_RETURN_HOME, true);
    }

    public static void setReturnHome(Context ctx, boolean on) {
        sp(ctx).edit().putBoolean(KEY_RETURN_HOME, on).apply();
    }

    private static int clamp(int v) {
        return Math.max(0, Math.min(300, v));
    }

    // ============ 开机调度 ============

    /**
     * 开机后调度顺序启动链。
     * 幂等：同一次开机（boot_count 相同）只调度一次。
     * 应在 BootReceiver、AccessibilityService.onServiceConnected、
     * NotificationListenerService.onListenerConnected 三处调用以提高可靠性。
     */
    public static synchronized void scheduleFromBoot(Context ctx) {
        if (ctx == null) return;
        Context appCtx = ctx.getApplicationContext();
        SharedPreferences sp = sp(appCtx);
        if (!sp.getBoolean(KEY_ENABLED, true)) {
            Log.i(TAG, "auto-start disabled by user, skip");
            return;
        }
        List<String> pkgs = PrefsUtil.getBootApps(appCtx);
        if (pkgs.isEmpty()) {
            Log.i(TAG, "no boot apps, skip");
            return;
        }
        long token = getBootToken(appCtx);
        if (sp.getLong(KEY_LAST_TOKEN, Long.MIN_VALUE) == token) {
            Log.i(TAG, "already scheduled for this boot, skip");
            return;
        }
        sp.edit().putLong(KEY_LAST_TOKEN, token).commit();
        int delay = sp.getInt(KEY_DELAY, 5);
        Log.i(TAG, "scheduling boot auto-start: " + pkgs.size() + " apps, delay=" + delay + "s");
        schedule(appCtx, 0, false, delay);
    }

    /**
     * 获取开机去重令牌。
     * Android 6.0+ 使用系统 boot_count 全局设置项（每次开机递增）；
     * 低版本用「当前时间 - 启动后流逝时间」估算的开机时刻（按分钟取整）。
     */
    private static long getBootToken(Context ctx) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            try {
                return Settings.Global.getInt(ctx.getContentResolver(), "boot_count");
            } catch (Settings.SettingNotFoundException e) {
                // 部分系统可能没有该项，回退到估算
            }
        }
        return ((System.currentTimeMillis() - SystemClock.elapsedRealtime()) + 30000) / 60000;
    }

    /**
     * 排一个闹钟：延迟 delaySec 秒后启动第 index 个应用（或回桌面）。
     * 使用 setExactAndAllowWhileIdle（Android 6+）提高 Doze 模式下的可靠性
     */
    private static void schedule(Context ctx, int index, boolean returnHome, int delaySec) {
        Intent intent = new Intent(ctx, AppAutoStartReceiver.class);
        intent.putExtra(EXTRA_INDEX, index);
        intent.putExtra(EXTRA_RETURN_HOME, returnHome);
        // +300ms 小偏移，避免边界抖动；延迟 clamp 到 0-300 秒
        long triggerAt = SystemClock.elapsedRealtime()
                + clamp(delaySec) * 1000L + 300;
        int requestCode = index + REQUEST_BASE + (returnHome ? 1000 : 0);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            flags |= PendingIntent.FLAG_IMMUTABLE;
        }
        PendingIntent pi = PendingIntent.getBroadcast(ctx, requestCode, intent, flags);
        AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
        if (am != null) {
            try {
                // Android 6+ 使用 setExactAndAllowWhileIdle 突破 Doze 模式
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    am.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAt, pi);
                } else {
                    am.set(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAt, pi);
                }
                Log.i(TAG, "scheduled: index=" + index + " returnHome=" + returnHome
                        + " delaySec=" + delaySec + " triggerAt(elapsed)=" + triggerAt);
            } catch (SecurityException se) {
                // Android 12+ 可能因未授予 SCHEDULE_EXACT_ALARM 而拒绝
                // fallback: setAndAllowWhileIdle 不需要精确闹钟权限，仍可在 Doze 下触发
                Log.w(TAG, "setExactAndAllowWhileIdle denied, fallback to setAndAllowWhileIdle", se);
                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        am.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAt, pi);
                    } else {
                        am.set(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAt, pi);
                    }
                    Log.i(TAG, "fallback scheduled: index=" + index + " delaySec=" + delaySec);
                } catch (Exception e) {
                    Log.e(TAG, "set alarm failed", e);
                }
            } catch (Exception e) {
                Log.e(TAG, "schedule alarm failed", e);
            }
        } else {
            Log.w(TAG, "AlarmManager is null, cannot schedule");
        }
    }

    /**
     * 闹钟触发时的处理：启动第 index 个应用，并排下一个闹钟。
     * 由 AppAutoStartReceiver.onReceive 调用。
     */
    public static void handleAlarm(Context ctx, Intent intent) {
        Log.d(TAG, "handleAlarm: intent=" + intent + " index=" + intent.getIntExtra(EXTRA_INDEX, -1)
                + " returnHome=" + intent.getBooleanExtra(EXTRA_RETURN_HOME, false));
        SharedPreferences sp = sp(ctx);
        if (!sp.getBoolean(KEY_ENABLED, true)) {
            Log.i(TAG, "auto-start disabled, abort chain");
            return;
        }
        // 收尾环节：返回桌面
        if (intent.getBooleanExtra(EXTRA_RETURN_HOME, false)) {
            Intent home = new Intent(Intent.ACTION_MAIN)
                    .addCategory(Intent.CATEGORY_HOME)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            try {
                ctx.startActivity(home);
                Log.i(TAG, "return home ok");
            } catch (Exception e) {
                Log.w(TAG, "return home failed", e);
            }
            return;
        }
        List<String> pkgs = PrefsUtil.getBootApps(ctx);
        int index = intent.getIntExtra(EXTRA_INDEX, 0);
        Log.d(TAG, "handleAlarm: pkgs.size=" + pkgs.size() + " index=" + index);
        if (index < 0 || index >= pkgs.size()) {
            Log.w(TAG, "index out of range: index=" + index + " size=" + pkgs.size());
            return;
        }
        String pkg = pkgs.get(index);
        Intent launch = ctx.getPackageManager().getLaunchIntentForPackage(pkg);
        Log.d(TAG, "handleAlarm: pkg=" + pkg + " launchIntent=" + launch);
        if (launch != null) {
            launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            try {
                ctx.startActivity(launch);
                Log.i(TAG, "launched [" + index + "] " + pkg);
            } catch (Exception e) {
                Log.w(TAG, "launch failed: " + pkg, e);
            }
        } else {
            Log.w(TAG, "no launch intent for " + pkg);
        }
        int interval = sp.getInt(KEY_INTERVAL, 3);
        int next = index + 1;
        if (next >= pkgs.size()) {
            // 已是最后一个：若需要回桌面，排一个收尾闹钟
            if (sp.getBoolean(KEY_RETURN_HOME, true)) {
                schedule(ctx, next, true, Math.max(1, interval));
            }
            return;
        }
        schedule(ctx, next, false, interval);
    }

    /**
     * 立即顺序启动所有开机自启应用（供 AutostartActivity 的「全部启动」按钮调用）。
     * 与开机调度的区别：不走 AlarmManager，直接启动第一个应用，后续通过 Handler.postDelayed 链式启动。
     * 这样用户点击「全部启动」后能立即看到效果，避免闹钟延迟。
     */
    public static void startChainNow(Context ctx) {
        if (ctx == null) return;
        final Context appCtx = ctx.getApplicationContext();
        final List<String> pkgs = PrefsUtil.getBootApps(appCtx);
        if (pkgs.isEmpty()) {
            Log.i(TAG, "startChainNow: no boot apps");
            return;
        }
        final boolean returnHome = isReturnHome(appCtx);
        final int interval = getInterval(appCtx);
        final android.os.Handler handler = new android.os.Handler(android.os.Looper.getMainLooper());
        Log.i(TAG, "startChainNow: " + pkgs.size() + " apps, interval=" + interval + "s");
        // 立即启动第一个，后续按 interval 秒间隔启动
        for (int i = 0; i < pkgs.size(); i++) {
            final int index = i;
            final String pkg = pkgs.get(i);
            long delayMs = i * interval * 1000L;
            handler.postDelayed(() -> {
                launchSingle(appCtx, pkg, index);
                // 最后一个：若需要回桌面
                if (index == pkgs.size() - 1 && returnHome) {
                    handler.postDelayed(() -> returnHome(appCtx), Math.max(1000, interval * 1000L));
                }
            }, delayMs);
        }
    }

    /** 启动单个应用 */
    private static void launchSingle(Context ctx, String pkg, int index) {
        Intent launch = ctx.getPackageManager().getLaunchIntentForPackage(pkg);
        if (launch != null) {
            launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            try {
                ctx.startActivity(launch);
                Log.i(TAG, "startChainNow launched [" + index + "] " + pkg);
            } catch (Exception e) {
                Log.w(TAG, "startChainNow launch failed: " + pkg, e);
            }
        } else {
            Log.w(TAG, "startChainNow no launch intent: " + pkg);
        }
    }

    /** 返回桌面 */
    private static void returnHome(Context ctx) {
        Intent home = new Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_HOME)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            ctx.startActivity(home);
            Log.i(TAG, "returnHome ok");
        } catch (Exception e) {
            Log.w(TAG, "returnHome failed", e);
        }
    }
}
