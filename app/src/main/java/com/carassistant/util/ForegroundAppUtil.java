/*
 * Copyright (C) 2026 CarAssistant Project. All rights reserved.
 *
 * 版权所有 (C) 2026 车机助手项目
 * 保留所有权利
 *
 * 本源代码受著作权法保护，未经著作权人书面许可，不得以任何形式复制、修改、
 * 分发、出售或逆向工程。违反者将承担法律责任。
 */

package com.carassistant.util;

import android.app.ActivityManager;
import android.app.usage.UsageStats;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.os.Build;
import android.text.TextUtils;

import java.lang.reflect.Method;
import java.util.List;

/**
 * 前台应用检测工具。
 *
 * 用于按键映射的「仅前台应用生效」约束条件：
 * 当某条映射设置了 constraintPackage，仅当该包名当前处于前台时才触发。
 *
 * 检测策略（按可靠性降序）：
 * 1. UsageStatsManager（Android 21+）：需要 PACKAGE_USAGE_STATS 权限，
 *    在部分车机 ROM 上需用户手动授予「使用情况访问」权限。
 * 2. ActivityManager.getRunningTasks：Android 5.0 后仅返回调用者自身，
 *    但在许多定制车机 ROM（拥有特权）上仍可用，这里通过反射兜底调用。
 */
public final class ForegroundAppUtil {

    private ForegroundAppUtil() {}

    /** 获取当前前台应用包名；失败时返回空串 */
    public static String getForegroundPackage(Context ctx) {
        if (ctx == null) return "";
        String pkg = getForegroundByUsageStats(ctx);
        if (!TextUtils.isEmpty(pkg)) return pkg;
        pkg = getForegroundByRunningTasks(ctx);
        if (!TextUtils.isEmpty(pkg)) return pkg;
        return "";
    }

    private static String getForegroundByUsageStats(Context ctx) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) return "";
        try {
            UsageStatsManager usm = (UsageStatsManager) ctx.getSystemService(Context.USAGE_STATS_SERVICE);
            if (usm == null) return "";
            long now = System.currentTimeMillis();
            long begin = now - 5000;
            List<UsageStats> stats = usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, begin, now);
            if (stats == null || stats.isEmpty()) return "";
            String top = "";
            long lastTime = 0;
            for (UsageStats s : stats) {
                if (s.getLastTimeUsed() > lastTime) {
                    lastTime = s.getLastTimeUsed();
                    top = s.getPackageName();
                }
            }
            return top;
        } catch (Exception e) {
            return "";
        }
    }

    private static String getForegroundByRunningTasks(Context ctx) {
        try {
            ActivityManager am = (ActivityManager) ctx.getSystemService(Context.ACTIVITY_SERVICE);
            if (am == null) return "";
            Method m = ActivityManager.class.getMethod("getRunningTasks", int.class);
            //noinspection unchecked
            List<Object> list = (List<Object>) m.invoke(am, 1);
            if (list == null || list.isEmpty()) return "";
            Object info = list.get(0);
            // RunningTaskInfo.topActivity 是公开字段，不是方法；旧实现会始终抛异常。
            Object topActivity = info.getClass().getField("topActivity").get(info);
            if (topActivity == null) return "";
            Object pkg = topActivity.getClass().getMethod("getPackageName").invoke(topActivity);
            return pkg == null ? "" : pkg.toString();
        } catch (Exception e) {
            return "";
        }
    }
}
