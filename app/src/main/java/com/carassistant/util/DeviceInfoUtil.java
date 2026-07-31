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

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Environment;
import android.os.StatFs;
import android.util.DisplayMetrics;
import android.view.View;
import android.view.WindowManager;

import androidx.core.content.FileProvider;

import java.io.File;
import java.io.FileOutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * 设备信息工具类
 */
public final class DeviceInfoUtil {

    private DeviceInfoUtil() {}

    public static class DeviceInfo {
        public String model, brand, manufacturer, androidVersion, sdkVersion;
        public String cpuAbi, cpuCores;
        public String memoryTotal, storageTotal, storageAvailable;
        public String resolution, density;
        public String securityPatch;
        public String batteryLevel, batteryStatus, batteryTemp;
    }

    public static DeviceInfo gather(Context ctx) {
        DeviceInfo info = new DeviceInfo();
        info.model = Build.MODEL;
        info.brand = Build.BRAND;
        info.manufacturer = Build.MANUFACTURER;
        info.androidVersion = Build.VERSION.RELEASE;
        info.sdkVersion = String.valueOf(Build.VERSION.SDK_INT);
        info.cpuAbi = Build.SUPPORTED_ABIS.length > 0 ? Build.SUPPORTED_ABIS[0] : "unknown";
        info.cpuCores = String.valueOf(Runtime.getRuntime().availableProcessors());
        info.securityPatch = Build.VERSION.SECURITY_PATCH == null ? "unknown" : Build.VERSION.SECURITY_PATCH;

        // 内存
        long memTotal = MemoryUtil.getTotalMemory(ctx);
        info.memoryTotal = FormatUtil.formatSize(memTotal);

        // 存储
        try {
            File ext = android.os.Environment.getDataDirectory();
            StatFs stat = new StatFs(ext.getPath());
            long total = stat.getTotalBytes();
            long avail = stat.getAvailableBytes();
            info.storageTotal = FormatUtil.formatSize(total);
            info.storageAvailable = FormatUtil.formatSize(avail);
        } catch (Exception e) {
            info.storageTotal = info.storageAvailable = "unknown";
        }

        // 屏幕
        DisplayMetrics dm = new DisplayMetrics();
        WindowManager wm = (WindowManager) ctx.getSystemService(Context.WINDOW_SERVICE);
        if (wm != null) {
            wm.getDefaultDisplay().getRealMetrics(dm);
            info.resolution = dm.widthPixels + " x " + dm.heightPixels;
            info.density = dm.densityDpi + " dpi";
        }

        // 电量
        IntentFilter filter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        Intent battery = ctx.registerReceiver(null, filter);
        if (battery != null) {
            int level = battery.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
            int scale = battery.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
            info.batteryLevel = (level >= 0 && scale > 0) ? (level * 100 / scale + "%") : "unknown";
            int status = battery.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
            info.batteryStatus = formatBatteryStatus(status);
            int temp = battery.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1);
            info.batteryTemp = (temp >= 0) ? (temp / 10.0 + "°C") : "unknown";
        } else {
            info.batteryLevel = info.batteryStatus = info.batteryTemp = "unknown";
        }

        return info;
    }

    private static String formatBatteryStatus(int status) {
        if (status == BatteryManager.BATTERY_STATUS_CHARGING) return "充电中";
        if (status == BatteryManager.BATTERY_STATUS_DISCHARGING) return "使用中";
        if (status == BatteryManager.BATTERY_STATUS_FULL) return "已充满";
        if (status == BatteryManager.BATTERY_STATUS_NOT_CHARGING) return "未充电";
        return "未知";
    }

    /**
     * 截屏：截取当前Activity根View
     * 注意：这是应用内截屏，不是系统级截屏。系统级截屏需要 MediaProjection API
     *
     * 兼容策略：
     * - Android 10+ (Scoped Storage)：保存到应用专属外部目录，无需写权限
     * - Android 9 及以下：保存到公共 Pictures/Screenshots，需要 WRITE_EXTERNAL_STORAGE
     * - 使用 MediaScannerConnection.scanFile() 替代废弃的 ACTION_MEDIA_SCANNER_SCAN_FILE 广播
     */
    public static String takeScreenshot(Activity activity) {
        try {
            View rootView = activity.getWindow().getDecorView().getRootView();
            Bitmap bitmap = Bitmap.createBitmap(rootView.getWidth(), rootView.getHeight(),
                    Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bitmap);
            rootView.draw(canvas);

            // Android 10+ 用应用专属目录避免 Scoped Storage 写入限制
            File dir;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                dir = new File(activity.getExternalFilesDir(Environment.DIRECTORY_PICTURES),
                        "Screenshots");
            } else {
                dir = new File(Environment.getExternalStoragePublicDirectory(
                        Environment.DIRECTORY_PICTURES), "Screenshots");
            }
            if (!dir.exists() && !dir.mkdirs()) {
                // 创建失败时回退到应用专属缓存目录
                dir = new File(activity.getExternalFilesDir(null), "Screenshots");
                if (!dir.exists()) dir.mkdirs();
            }
            String fileName = "Screenshot_" + new SimpleDateFormat("yyyyMMdd_HHmmss",
                    Locale.getDefault()).format(new Date()) + ".png";
            File file = new File(dir, fileName);
            FileOutputStream fos = new FileOutputStream(file);
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, fos);
            fos.flush();
            fos.close();

            // 用 MediaScannerConnection 替代废弃的 ACTION_MEDIA_SCANNER_SCAN_FILE 广播
            // 仅对公共目录文件扫描有效；应用专属目录文件不需要扫描
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                MediaScannerConnection.scanFile(activity,
                        new String[]{file.getAbsolutePath()},
                        new String[]{"image/png"},
                        null);
            }

            return file.getAbsolutePath();
        } catch (Exception e) {
            return null;
        }
    }
}
