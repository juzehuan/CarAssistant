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
import android.util.Log;

import com.carassistant.service.SidebarService;
import com.carassistant.util.AppAutoStartManager;
import com.carassistant.util.PermissionUtil;

/**
 * 开机自启动接收器
 * 1. 启动车机助手侧边栏服务（已授予悬浮窗权限时）
 * 2. 调度用户配置的开机自启应用（通过 AlarmManager 闹钟链顺序启动，避免开机繁忙丢失）
 *
 * 同时监听 MY_PACKAGE_REPLACED 以便应用升级后重新拉起侧边栏，
 * 但应用升级时不重新触发三方应用启动调度（避免每次更新都弹一堆应用）。
 */
public class BootReceiver extends BroadcastReceiver {

    private static final String TAG = "BootReceiver";

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
        startSidebarService(appCtx);

        // 2. 调度开机自启应用（应用升级时跳过，避免每次更新都弹出应用）
        if (!Intent.ACTION_MY_PACKAGE_REPLACED.equals(action)) {
            Log.i(TAG, "scheduling boot auto-start");
            AppAutoStartManager.scheduleFromBoot(appCtx);
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
