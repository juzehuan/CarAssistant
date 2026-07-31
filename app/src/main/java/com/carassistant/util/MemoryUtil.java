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

import android.app.ActivityManager;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Debug;
import android.text.TextUtils;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 内存工具：读取总/可用内存，运行中的进程信息
 *
 * 清理策略：
 * - 非 root：调用 ActivityManager.killBackgroundProcesses（系统限制严格，效果有限）
 * - root：使用 am force-stop 强制结束第三方后台进程（真正释放内存）
 *
 * 安全约束：
 * - 永不结束自身应用、系统应用、白名单应用
 * - force-stop 通过 ShellUtil.execRoot 单条批量执行，避免多次 fork su
 */
public final class MemoryUtil {

    private MemoryUtil() {}

    public static long getTotalMemory(Context ctx) {
        ActivityManager am = (ActivityManager) ctx.getSystemService(Context.ACTIVITY_SERVICE);
        ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
        if (am != null) am.getMemoryInfo(mi);
        return mi.totalMem;
    }

    public static long getAvailableMemory(Context ctx) {
        ActivityManager am = (ActivityManager) ctx.getSystemService(Context.ACTIVITY_SERVICE);
        ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
        if (am != null) am.getMemoryInfo(mi);
        return mi.availMem;
    }

    public static long getUsedMemory(Context ctx) {
        return getTotalMemory(ctx) - getAvailableMemory(ctx);
    }

    /** 已用内存占比（0-100） */
    public static int getUsedPercent(Context ctx) {
        long total = getTotalMemory(ctx);
        if (total <= 0) return 0;
        long used = getUsedMemory(ctx);
        return (int) (used * 100 / total);
    }

    /** 获取运行中进程数 */
    public static int getRunningProcessCount(Context ctx) {
        ActivityManager am = (ActivityManager) ctx.getSystemService(Context.ACTIVITY_SERVICE);
        if (am == null) return 0;
        // Android 5.0+ getRunningAppProcesses 仅返回自身进程，但作为参考指标仍可用
        List<ActivityManager.RunningAppProcessInfo> list = am.getRunningAppProcesses();
        return list == null ? 0 : list.size();
    }

    /**
     * 结束后台进程（仅对自身应用之外的其他应用生效）
     * 注意：Android 8+ 系统限制较严，实际释放效果与设备厂商策略相关
     */
    public static int killBackgroundProcesses(Context ctx, List<String> packages) {
        ActivityManager am = (ActivityManager) ctx.getSystemService(Context.ACTIVITY_SERVICE);
        if (am == null) return 0;
        int count = 0;
        String myPkg = ctx.getPackageName();
        for (String pkg : packages) {
            if (pkg == null || pkg.equals(myPkg)) continue;
            try {
                am.killBackgroundProcesses(pkg);
                count++;
            } catch (Exception ignored) {}
        }
        return count;
    }

    /**
     * Root 模式强制结束后台应用（am force-stop）
     * 单条 su 命令批量执行，避免多次 fork su 进程
     *
     * @param packages 待结束的包名列表（调用方需已过滤系统应用/自身/白名单）
     * @return 实际执行 force-stop 的应用数（命令执行成功即计入）
     */
    public static int forceStopPackages(List<String> packages) {
        if (packages == null || packages.isEmpty()) return 0;
        // 拼接单条命令：am force-stop pkg1 ; am force-stop pkg2 ; ...
        StringBuilder cmd = new StringBuilder();
        int count = 0;
        for (String pkg : packages) {
            if (TextUtils.isEmpty(pkg)) continue;
            if (cmd.length() > 0) cmd.append(" ; ");
            cmd.append("am force-stop ").append(pkg);
            count++;
        }
        if (count == 0) return 0;
        ShellUtil.Result r = ShellUtil.execRoot(cmd.toString());
        return r.success() ? count : 0;
    }

    /**
     * Root 模式获取正在运行的第三方应用包名列表（通过 ps 命令）
     * 非 root 时回退到 ActivityManager.getRunningAppProcesses（仅返回自身进程）
     *
     * @param ctx 上下文
     * @return 正在运行的第三方应用包名集合
     */
    public static Set<String> getRunningThirdPartyPackages(Context ctx) {
        Set<String> result = new HashSet<>();
        // 收集已安装第三方应用包名集合（用于过滤 ps 输出）
        Set<String> installedThirdParty = new HashSet<>();
        PackageManager pm = ctx.getPackageManager();
        List<ApplicationInfo> apps = pm.getInstalledApplications(0);
        String myPkg = ctx.getPackageName();
        for (ApplicationInfo ai : apps) {
            if ((ai.flags & ApplicationInfo.FLAG_SYSTEM) == 0
                    && !myPkg.equals(ai.packageName)) {
                installedThirdParty.add(ai.packageName);
            }
        }
        if (installedThirdParty.isEmpty()) return result;

        // 通过 ps 扫描正在运行的进程
        // 使用 ps -A（Android 8+）或 ps（旧版本）输出格式：USER PID PPID ... NAME
        ShellUtil.Result r = ShellUtil.execRoot("ps -A 2>/dev/null || ps");
        if (r.success() && !TextUtils.isEmpty(r.stdout)) {
            String[] lines = r.stdout.split("\n");
            for (String line : lines) {
                line = line.trim();
                if (line.isEmpty()) continue;
                // ps 输出最后一列为进程名，可能是包名或包名:子进程
                String[] parts = line.split("\\s+");
                if (parts.length < 1) continue;
                String name = parts[parts.length - 1];
                // 处理 子进程名（如 com.foo:remote）
                int colon = name.indexOf(':');
                if (colon > 0) name = name.substring(0, colon);
                if (installedThirdParty.contains(name)) {
                    result.add(name);
                }
            }
        }
        return result;
    }

    /** 获取可被清理的后台应用包名列表 */
    public static List<String> getKillablePackages(Context ctx) {
        List<String> result = new ArrayList<>();
        PackageManager pm = ctx.getPackageManager();
        List<ApplicationInfo> apps = pm.getInstalledApplications(PackageManager.GET_META_DATA);
        String myPkg = ctx.getPackageName();
        for (ApplicationInfo ai : apps) {
            if ((ai.flags & ApplicationInfo.FLAG_SYSTEM) != 0) continue;
            String pkg = ai.packageName;
            if (pkg.equals(myPkg)) continue;
            result.add(pkg);
        }
        return result;
    }

    /**
     * 获取可被清理的后台应用包名列表（过滤白名单）
     * @param whitelist 白名单包名集合，集合内应用不被清理
     */
    public static List<String> getKillablePackages(Context ctx, Set<String> whitelist) {
        List<String> result = new ArrayList<>();
        PackageManager pm = ctx.getPackageManager();
        List<ApplicationInfo> apps = pm.getInstalledApplications(PackageManager.GET_META_DATA);
        String myPkg = ctx.getPackageName();
        for (ApplicationInfo ai : apps) {
            if ((ai.flags & ApplicationInfo.FLAG_SYSTEM) != 0) continue;
            String pkg = ai.packageName;
            if (pkg.equals(myPkg)) continue;
            if (whitelist != null && whitelist.contains(pkg)) continue;
            result.add(pkg);
        }
        return result;
    }

    /**
     * Root 模式：获取正在运行的第三方应用包名列表（已过滤白名单和自身）
     * 仅清理"正在运行"的应用，避免无差别 force-stop 全部已安装应用
     *
     * @param ctx 上下文
     * @param whitelist 白名单包名集合
     * @return 正在运行且可被清理的第三方应用包名列表
     */
    public static List<String> getRunningKillablePackages(Context ctx, Set<String> whitelist) {
        Set<String> running = getRunningThirdPartyPackages(ctx);
        List<String> result = new ArrayList<>();
        String myPkg = ctx.getPackageName();
        for (String pkg : running) {
            if (pkg.equals(myPkg)) continue;
            if (whitelist != null && whitelist.contains(pkg)) continue;
            result.add(pkg);
        }
        return result;
    }
}
