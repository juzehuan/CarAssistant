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
 * distribution, sale or reverse engineering without written permission is
 * prohibited and subject to legal action.
 */

package com.carassistant.util;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;

import androidx.core.app.NotificationManagerCompat;

import com.carassistant.service.MonitorService;

/**
 * 退出应用。
 *
 * 顺序很重要，原因是「性能监控」跑的是前台服务：
 * 如果只结束进程而不先 stopService，系统会认为服务仍应存活并把它重新拉起，
 * 结果就是"退出了但悬浮窗又冒出来"。所以必须先停服务、再关页面、最后才结束进程。
 */
public final class AppExit {

    /** 停服务是异步的，留一点时间让它真正走完 onDestroy，再结束进程 */
    private static final long KILL_DELAY_MS = 250L;

    private AppExit() {
    }

    /**
     * 彻底退出应用：停止后台服务 → 清除通知 → 关闭所有页面 → 结束进程。
     *
     * @param activity 调用方的 Activity（需要在其中关闭任务栈）
     */
    public static void exit(final Activity activity) {
        if (activity == null) return;
        Context app = activity.getApplicationContext();

        // 1) 停止悬浮监控服务（前台服务，必须先停，否则会被系统重新拉起）
        try {
            app.stopService(new Intent(app, MonitorService.class));
        } catch (Exception e) {
            android.util.Log.e("AppExit", "stop MonitorService failed", e);
        }

        // 2) 清除所有通知（前台服务通知、U 盘提醒等）
        try {
            NotificationManagerCompat.from(app).cancelAll();
        } catch (Exception e) {
            android.util.Log.e("AppExit", "cancel notifications failed", e);
        }

        // 3) 关闭本任务栈内的全部页面（含已经压栈的设置页、详情页等）
        try {
            activity.finishAndRemoveTask();
        } catch (Exception e) {
            android.util.Log.e("AppExit", "finishAndRemoveTask failed", e);
            try {
                activity.finishAffinity();
            } catch (Exception ignored) {
            }
        }

        // 4) 结束进程，确保不残留内存占用（等上一步的服务停止真正生效）
        new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
            @Override
            public void run() {
                android.os.Process.killProcess(android.os.Process.myPid());
                System.exit(0);
            }
        }, KILL_DELAY_MS);
    }
}
