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

package com.carassistant.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.carassistant.service.SidebarService;
import com.carassistant.util.AppAutoStartManager;
import com.carassistant.util.PermissionUtil;
import com.carassistant.util.PrefsUtil;

import java.util.List;

/**
 * 开机自启动接收器
 * 1. 启动车机助手侧边栏服务（已授予悬浮窗权限时）
 * 2. 启动用户配置的开机自启应用
 *    - 主方案：goAsync() + Handler 链式启动（可靠，不依赖 AlarmManager）
 *    - 备选方案：AlarmManager 闹钟链（作为兜底）
 *
 * 同时监听 MY_PACKAGE_REPLACED 以便应用升级后重新拉起侧边栏，
 * 但应用升级时不重新触发三方应用启动调度（避免每次更新都弹一堆应用）。
 */
public class BootReceiver extends BroadcastReceiver {

    private static final String TAG = "BootReceiver";
    /** goAsync 最大执行时间（毫秒），不超过系统 ANR 阈值 */
    private static final long GO_ASYNC_TIMEOUT_MS = 9000;

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null) return;
        String action = intent.getAction();
        Log.i(TAG, "onReceive action=" + action);
        if (!Intent.ACTION_BOOT_COMPLETED.equals(action)
                && !"android.intent.action.QUICKBOOT_POWERON".equals(action)
                && !"com.htc.intent.action.QUICKBOOT_POWERON".equals(action)
                && !Intent.ACTION_MY_PACKAGE_REPLACED.equals(action)) return;

        Context appCtx = context.getApplicationContext();

        // 1. 启动侧边栏服务
        Log.i(TAG, "before startSidebarService");
        try {
            startSidebarService(appCtx);
        } catch (Throwable t) {
            Log.e(TAG, "startSidebarService error", t);
        }
        Log.i(TAG, "after startSidebarService");

        // 2. 启动开机自启应用（应用升级时跳过，避免每次更新都弹出应用）
        if (!Intent.ACTION_MY_PACKAGE_REPLACED.equals(action)) {
            Log.i(TAG, "starting boot auto-start");
            // 主方案：goAsync + Handler 直接链式启动
            startBootAppsDirectly(appCtx);
            // 备选方案：AlarmManager 闹钟链作为兜底
            AppAutoStartManager.scheduleFromBoot(appCtx);
        }
    }

    /**
     * 使用 goAsync() + Handler 链式启动开机自启应用。
     * 不依赖 AlarmManager，避免 Android 12+ 精确闹钟权限问题和 Doze 限制。
     * goAsync() 允许 BroadcastReceiver 在后台处理，最多 10 秒。
     */
    private void startBootAppsDirectly(Context ctx) {
        if (!AppAutoStartManager.isEnabled(ctx)) {
            Log.i(TAG, "auto-start disabled, skip");
            return;
        }
        List<String> pkgs = PrefsUtil.getBootApps(ctx);
        if (pkgs.isEmpty()) {
            Log.i(TAG, "no boot apps, skip");
            return;
        }

        final int delay = AppAutoStartManager.getDelay(ctx);
        final int interval = AppAutoStartManager.getInterval(ctx);
        final boolean returnHome = AppAutoStartManager.isReturnHome(ctx);

        // 检查总时间是否超过 goAsync 限制
        long totalTime = (delay + pkgs.size() * interval) * 1000L;
        if (totalTime > GO_ASYNC_TIMEOUT_MS) {
            Log.w(TAG, "total time " + totalTime + "ms exceeds limit, use AlarmManager only");
            return;
        }

        final PendingResult pendingResult = goAsync();
        final Handler handler = new Handler(Looper.getMainLooper());
        Log.i(TAG, "startBootAppsDirectly: " + pkgs.size() + " apps, delay=" + delay + "s interval=" + interval + "s");

        for (int i = 0; i < pkgs.size(); i++) {
            final int index = i;
            final String pkg = pkgs.get(i);
            long delayMs = (delay + i * interval) * 1000L;
            handler.postDelayed(() -> {
                try {
                    Intent launch = ctx.getPackageManager().getLaunchIntentForPackage(pkg);
                    if (launch != null) {
                        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        ctx.startActivity(launch);
                        Log.i(TAG, "launched [" + index + "] " + pkg);
                    } else {
                        Log.w(TAG, "no launch intent for " + pkg);
                    }
                } catch (Exception e) {
                    Log.w(TAG, "launch failed: " + pkg, e);
                }
                // 最后一个应用启动后
                if (index == pkgs.size() - 1) {
                    if (returnHome) {
                        handler.postDelayed(() -> {
                            try {
                                Intent home = new Intent(Intent.ACTION_MAIN)
                                        .addCategory(Intent.CATEGORY_HOME)
                                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                                ctx.startActivity(home);
                                Log.i(TAG, "return home ok");
                            } catch (Exception e) {
                                Log.w(TAG, "return home failed", e);
                            }
                            pendingResult.finish();
                        }, Math.max(1000, interval * 1000L));
                    } else {
                        pendingResult.finish();
                    }
                }
            }, delayMs);
        }
    }

    /** 启动侧边栏服务（需悬浮窗权限） */
    private void startSidebarService(Context ctx) {
        try {
            if (PermissionUtil.canDrawOverlays(ctx)) {
                Intent service = new Intent(ctx, SidebarService.class);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    ctx.startForegroundService(service);
                } else {
                    ctx.startService(service);
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "Failed to start sidebar service", e);
        }
    }
}