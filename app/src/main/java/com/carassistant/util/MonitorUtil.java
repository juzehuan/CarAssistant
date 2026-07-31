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

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;
import android.os.Process;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;

/**
 * 性能监控工具类：CPU/内存/温度/帧率/电量
 *
 * 兼容策略：
 * - CPU 使用率：优先 /proc/stat；不可读时回退到 Process.getElapsedCpuTime()（应用级 CPU）
 * - CPU 频率：扫描 /sys/devices/system/cpu/cpuN/cpufreq/scaling_cur_freq
 * - 温度：thermal_zone（不依赖 type 文件）→ hwmon → 电池温度兜底
 * - 所有读取都做 try-catch，避免 SELinux 限制导致崩溃
 */
public final class MonitorUtil {

    private MonitorUtil() {}

    // CPU 使用率状态：用于两次采样计算差值
    private static long lastTotal = 0, lastIdle = 0;
    private static boolean procStatAvailable = true;

    /** CPU 使用率（百分比），基于 /proc/stat 计算 */
    public static float getCpuUsage() {
        if (!procStatAvailable) {
            return getCpuUsageByProcess();
        }
        try {
            String line = readFirstLine("/proc/stat");
            if (line == null || !line.startsWith("cpu ")) {
                procStatAvailable = false;
                return getCpuUsageByProcess();
            }
            String[] parts = line.split("\\s+");
            // user nice system idle iowait irq softirq steal
            long idle = Long.parseLong(parts[4]);
            long total = 0;
            for (int i = 1; i < parts.length; i++) {
                try {
                    total += Long.parseLong(parts[i]);
                } catch (NumberFormatException ignored) {
                    break;
                }
            }
            float usage = 0;
            if (lastTotal > 0 && total > lastTotal) {
                long totalDiff = total - lastTotal;
                long idleDiff = idle - lastIdle;
                usage = (float) (totalDiff - idleDiff) * 100 / totalDiff;
            }
            lastTotal = total;
            lastIdle = idle;
            return Math.max(0, Math.min(100, usage));
        } catch (Exception e) {
            procStatAvailable = false;
            return getCpuUsageByProcess();
        }
    }

    /**
     * 应用级 CPU 使用率兜底方案
     * 基于 Process.getElapsedCpuTime()（应用自身 CPU 时间）+ 系统墙钟时间估算
     * 注意：这不是系统级 CPU 使用率，但能反映本应用的 CPU 活动
     */
    private static long lastProcCpuTime = 0;
    private static long lastWallTime = 0;
    private static int cpuCores = 0;

    private static float getCpuUsageByProcess() {
        try {
            long procCpu = Process.getElapsedCpuTime();  // ms
            long wall = System.currentTimeMillis();
            if (cpuCores <= 0) {
                cpuCores = Runtime.getRuntime().availableProcessors();
                if (cpuCores <= 0) cpuCores = 1;
            }
            float usage = 0;
            if (lastProcCpuTime > 0 && wall > lastWallTime) {
                long procDiff = procCpu - lastProcCpuTime;
                long wallDiff = wall - lastWallTime;
                // 应用 CPU 占比 = 进程CPU时间 / (墙钟时间 * 核心数)
                usage = (float) procDiff * 100 / (wallDiff * cpuCores);
            }
            lastProcCpuTime = procCpu;
            lastWallTime = wall;
            return Math.max(0, Math.min(100, usage));
        } catch (Exception e) {
            return 0;
        }
    }

    /** 重置 CPU 采样基线（Activity onResume 时调用） */
    public static void resetCpuBaseline() {
        lastTotal = 0;
        lastIdle = 0;
        lastProcCpuTime = 0;
        lastWallTime = 0;
    }

    /** 内存使用率（百分比） */
    public static float getMemoryUsage(Context ctx) {
        long total = MemoryUtil.getTotalMemory(ctx);
        long avail = MemoryUtil.getAvailableMemory(ctx);
        if (total <= 0) return 0;
        return (float) (total - avail) * 100 / total;
    }

    /** 电池温度（摄氏度） */
    public static float getBatteryTemp(Context ctx) {
        try {
            IntentFilter filter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
            Intent battery = ctx.registerReceiver(null, filter);
            if (battery != null) {
                int temp = battery.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1);
                if (temp >= 0) return temp / 10.0f;
            }
        } catch (Exception ignored) {}
        return -1;
    }

    /** 电量百分比 */
    public static int getBatteryLevel(Context ctx) {
        try {
            IntentFilter filter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
            Intent battery = ctx.registerReceiver(null, filter);
            if (battery != null) {
                int level = battery.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
                int scale = battery.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
                if (level >= 0 && scale > 0) return level * 100 / scale;
            }
        } catch (Exception ignored) {}
        return -1;
    }

    /** 屏幕刷新率（Hz） */
    public static float getRefreshRate(Context ctx) {
        try {
            android.view.WindowManager wm = (android.view.WindowManager)
                    ctx.getSystemService(Context.WINDOW_SERVICE);
            if (wm != null) {
                return wm.getDefaultDisplay().getRefreshRate();
            }
        } catch (Exception ignored) {}
        return -1;
    }

    /**
     * 读取 CPU/SoC 温度
     * 策略：
     * 1. thermal_zone0-15：优先匹配 type 含 cpu/soc 关键字
     * 2. 退化：任意 thermal_zone 的 temp（值在 10-150°C 范围内视为有效）
     * 3. hwmon：扫描 temp1-4_input
     * 4. 兜底：返回 -1（调用方可回退到电池温度）
     */
    public static float getCpuTemp() {
        // 1. 按 type 过滤的 thermal_zone
        for (int i = 0; i < 16; i++) {
            String base = "/sys/class/thermal/thermal_zone" + i;
            String type = readFirstLine(base + "/type");
            if (type == null) continue;
            String t = type.toLowerCase();
            if (t.contains("cpu") || t.contains("soc") || t.contains("x86")
                    || t.contains("apc") || t.contains("tsens") || t.contains("skin")
                    || t.contains("panel") || t.contains("gpu")) {
                float v = readTempValue(base + "/temp");
                if (v >= 10 && v <= 150) return v;
            }
        }
        // 2. 退化：任意 thermal_zone（不依赖 type）
        for (int i = 0; i < 16; i++) {
            float v = readTempValue("/sys/class/thermal/thermal_zone" + i + "/temp");
            if (v >= 10 && v <= 150) return v;
        }
        // 3. hwmon 兜底
        for (int i = 0; i < 8; i++) {
            for (int j = 1; j <= 8; j++) {
                float v = readTempValue("/sys/class/hwmon/hwmon" + i + "/temp" + j + "_input");
                if (v >= 10 && v <= 150) return v;
            }
        }
        return -1;
    }

    /**
     * 获取温度诊断信息：返回所有可读的温度源
     * 用于排查"温度获取不到"问题
     */
    public static String getTempDiagnostics() {
        StringBuilder sb = new StringBuilder();
        sb.append("=== thermal_zone ===\n");
        for (int i = 0; i < 16; i++) {
            String base = "/sys/class/thermal/thermal_zone" + i;
            String type = readFirstLine(base + "/type");
            String temp = readFirstLine(base + "/temp");
            if (type != null || temp != null) {
                sb.append("zone").append(i)
                        .append(" type=").append(type != null ? type : "null")
                        .append(" temp=").append(temp != null ? temp : "null")
                        .append("\n");
            }
        }
        sb.append("=== hwmon ===\n");
        for (int i = 0; i < 4; i++) {
            String name = readFirstLine("/sys/class/hwmon/hwmon" + i + "/name");
            if (name != null) {
                sb.append("hwmon").append(i).append(" name=").append(name).append("\n");
                for (int j = 1; j <= 4; j++) {
                    String v = readFirstLine("/sys/class/hwmon/hwmon" + i + "/temp" + j + "_input");
                    if (v != null) sb.append("  temp").append(j).append("=").append(v).append("\n");
                }
            }
        }
        if (sb.length() < 30) {
            sb.append("所有 sysfs 温度路径均不可读（SELinux 限制）\n");
        }
        return sb.toString();
    }

    /** CPU 使用率诊断信息 */
    public static String getCpuDiagnostics() {
        StringBuilder sb = new StringBuilder();
        // /proc/stat 可读性
        String stat = readFirstLine("/proc/stat");
        sb.append("/proc/stat: ").append(stat != null ? "可读" : "不可读").append("\n");
        if (stat != null) {
            sb.append("  内容: ").append(stat.length() > 60 ? stat.substring(0, 60) + "..." : stat).append("\n");
        }
        sb.append("采样方式: ").append(procStatAvailable ? "/proc/stat" : "Process.getElapsedCpuTime()").append("\n");
        sb.append("CPU核心数: ").append(Runtime.getRuntime().availableProcessors()).append("\n");
        // cpufreq 目录可读性
        for (int i = 0; i < 4; i++) {
            String path = "/sys/devices/system/cpu/cpu" + i + "/cpufreq/scaling_cur_freq";
            String v = readFirstLine(path);
            if (v != null) {
                sb.append("cpu").append(i).append(" freq: ").append(v).append("\n");
            } else {
                File f = new File("/sys/devices/system/cpu/cpu" + i + "/cpufreq");
                sb.append("cpu").append(i).append(" cpufreq目录: ").append(f.exists() ? "存在但读不到" : "不存在").append("\n");
            }
        }
        return sb.toString();
    }

    private static float readTempValue(String path) {
        try {
            String line = readFirstLine(path);
            if (line == null) return -1;
            float t = Float.parseFloat(line.trim());
            // thermal_zone/hwmon 单位通常为毫摄氏度
            return t > 1000 ? t / 1000 : t;
        } catch (Exception e) {
            return -1;
        }
    }

    private static String readFirstLine(String path) {
        BufferedReader br = null;
        try {
            br = new BufferedReader(new FileReader(path));
            return br.readLine();
        } catch (Exception e) {
            return null;
        } finally {
            if (br != null) try { br.close(); } catch (Exception ignored) {}
        }
    }

    /**
     * 读取 CPU 当前频率（MHz）
     * 路径：/sys/devices/system/cpu/cpuN/cpufreq/scaling_cur_freq（单位 kHz）
     */
    public static float getCpuFreq() {
        for (int i = 0; i < 8; i++) {
            String path = "/sys/devices/system/cpu/cpu" + i + "/cpufreq/scaling_cur_freq";
            String line = readFirstLine(path);
            if (line != null) {
                try {
                    long khz = Long.parseLong(line.trim());
                    if (khz > 0) return khz / 1000f;  // kHz -> MHz
                } catch (NumberFormatException ignored) {}
            }
        }
        return -1;
    }

    /** 读取 CPU 最大频率（MHz） */
    public static float getCpuMaxFreq() {
        for (int i = 0; i < 8; i++) {
            String path = "/sys/devices/system/cpu/cpu" + i + "/cpufreq/cpuinfo_max_freq";
            String line = readFirstLine(path);
            if (line != null) {
                try {
                    long khz = Long.parseLong(line.trim());
                    if (khz > 0) return khz / 1000f;
                } catch (NumberFormatException ignored) {}
            }
        }
        return -1;
    }
}
