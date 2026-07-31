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

import android.Manifest;
import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.Settings;
import android.text.TextUtils;
import android.app.AppOpsManager;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.carassistant.service.TargetMediaSessionService;

/**
 * 权限工具：统一处理存储、悬浮窗、通知、无障碍、蓝牙、相机、写入设置、使用情况等权限
 */
public final class PermissionUtil {

    private PermissionUtil() {}

    /** Android 13+ 通知权限 */
    public static boolean hasNotificationPermission(Context ctx) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true;
        return ContextCompat.checkSelfPermission(ctx,
                Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED;
    }

    public static void requestNotificationPermission(Activity act, int code) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ActivityCompat.requestPermissions(act,
                    new String[]{Manifest.permission.POST_NOTIFICATIONS}, code);
        }
    }

    /** 悬浮窗权限 */
    public static boolean canDrawOverlays(Context ctx) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return Settings.canDrawOverlays(ctx);
        }
        return true;
    }

    public static void requestOverlayPermission(Activity act, int code) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !canDrawOverlays(act)) {
            Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + act.getPackageName()));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            act.startActivityForResult(intent, code);
        }
    }

    /**
     * 无障碍权限：检查本应用的无障碍服务是否已开启
     * - 通过系统 Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES 检查
     * - 服务组件名以 "pkg/cls" 形式出现在该列表中即视为已启用
     */
    public static boolean isAccessibilityEnabled(Context ctx) {
        try {
            final String expected = ctx.getPackageName() + "/"
                    + "com.carassistant.service.KeyMappingAccessibilityService";
            String enabledServices = Settings.Secure.getString(ctx.getContentResolver(),
                    Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
            if (TextUtils.isEmpty(enabledServices)) return false;
            // 列表格式：pkg1/cls1:pkg2/cls2
            String[] items = enabledServices.split(":");
            for (String it : items) {
                if (it.equalsIgnoreCase(expected) || it.toLowerCase().contains(expected.toLowerCase())) {
                    return true;
                }
            }
            // 部分系统使用 ComponentName.flattenToString() 格式（pkg/cls），上面已覆盖
            return false;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 跳转到系统无障碍设置页（用户需手动开启本应用的服务）
     */
    public static void requestAccessibilityPermission(Context ctx) {
        try {
            Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            ctx.startActivity(intent);
        } catch (Exception e) {
            // 极少数系统不支持直接跳转，回退到通用设置
            try {
                Intent fallback = new Intent(Settings.ACTION_SETTINGS);
                fallback.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                ctx.startActivity(fallback);
            } catch (Exception ignored) {}
        }
    }

    /**
     * 是否拥有"所有文件访问权限"
     * - Android 11+ 检查 MANAGE_EXTERNAL_STORAGE
     * - Android 10 及以下使用传统 READ/WRITE_EXTERNAL_STORAGE
     */
    public static boolean hasStorageAccess(Context ctx) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return Environment.isExternalStorageManager();
        }
        int r = ContextCompat.checkSelfPermission(ctx, Manifest.permission.READ_EXTERNAL_STORAGE);
        int w = ContextCompat.checkSelfPermission(ctx, Manifest.permission.WRITE_EXTERNAL_STORAGE);
        return r == PackageManager.PERMISSION_GRANTED && w == PackageManager.PERMISSION_GRANTED;
    }

    public static void requestLegacyStorage(Activity act, int code) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            ActivityCompat.requestPermissions(act,
                    new String[]{
                            Manifest.permission.READ_EXTERNAL_STORAGE,
                            Manifest.permission.WRITE_EXTERNAL_STORAGE
                    }, code);
        }
    }

    public static void requestAllFilesAccess(Context ctx) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !Environment.isExternalStorageManager()) {
            Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
            intent.setData(Uri.parse("package:" + ctx.getPackageName()));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            ctx.startActivity(intent);
        }
    }

    // ============ 蓝牙（Android 12+ BLUETOOTH_CONNECT） ============

    public static boolean hasBluetoothConnect(Context ctx) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true;
        return ContextCompat.checkSelfPermission(ctx,
                Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED;
    }

    // ============ 相机（手电筒） ============

    public static boolean hasCamera(Context ctx) {
        return ContextCompat.checkSelfPermission(ctx,
                Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED;
    }

    // ============ 修改系统设置（WRITE_SETTINGS） ============

    public static boolean canWriteSettings(Context ctx) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return Settings.System.canWrite(ctx);
        }
        return true;
    }

    public static void requestWriteSettings(Context ctx) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !canWriteSettings(ctx)) {
            Intent intent = new Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS);
            intent.setData(Uri.parse("package:" + ctx.getPackageName()));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            ctx.startActivity(intent);
        }
    }

    // ============ 使用情况访问（PACKAGE_USAGE_STATS） ============

    public static boolean hasUsageStatsAccess(Context ctx) {
        try {
            AppOpsManager appOps = (AppOpsManager) ctx.getSystemService(Context.APP_OPS_SERVICE);
            if (appOps == null) return false;
            int mode;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                mode = appOps.unsafeCheckOpNoThrow(
                        AppOpsManager.OPSTR_GET_USAGE_STATS,
                        android.os.Process.myUid(),
                        ctx.getPackageName());
            } else {
                mode = appOps.checkOpNoThrow(
                        AppOpsManager.OPSTR_GET_USAGE_STATS,
                        android.os.Process.myUid(),
                        ctx.getPackageName());
            }
            return mode == AppOpsManager.MODE_ALLOWED;
        } catch (Exception e) {
            return false;
        }
    }

    public static void requestUsageStatsAccess(Context ctx) {
        try {
            Intent intent = new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            ctx.startActivity(intent);
        } catch (Exception ignored) {}
    }

    // ============ 通知访问权限（NotificationListenerService） ============

    /**
     * 检查本应用的 TargetMediaSessionService 是否已被用户授权为通知监听器
     * 用于方控媒体键定向派发 + 音乐伴侣获取媒体会话
     */
    public static boolean isNotificationListenerEnabled(Context ctx) {
        try {
            ComponentName cn = new ComponentName(ctx, TargetMediaSessionService.class);
            String flat = Settings.Secure.getString(ctx.getContentResolver(),
                    "enabled_notification_listeners");
            return flat != null && flat.contains(cn.flattenToString());
        } catch (Exception e) {
            return false;
        }
    }

    public static void requestNotificationListenerAccess(Context ctx) {
        try {
            Intent intent = new Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS");
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            ctx.startActivity(intent);
        } catch (Exception ignored) {}
    }

    // ============ 一键申请所有运行时权限 ============

    /**
     * 一次性申请所有运行时权限（不包含特殊权限）
     * - 通知（Android 13+）
     * - 蓝牙连接（Android 12+）
     * - 相机
     * - 存储权限（Android 10 及以下）
     */
    public static void requestAllRuntimePermissions(Activity act, int code) {
        java.util.List<String> perms = new java.util.ArrayList<>();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            perms.add(Manifest.permission.POST_NOTIFICATIONS);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            perms.add(Manifest.permission.BLUETOOTH_CONNECT);
        }
        perms.add(Manifest.permission.CAMERA);
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            perms.add(Manifest.permission.READ_EXTERNAL_STORAGE);
            perms.add(Manifest.permission.WRITE_EXTERNAL_STORAGE);
        }
        if (perms.isEmpty()) return;
        ActivityCompat.requestPermissions(act, perms.toArray(new String[0]), code);
    }

    /**
     * 检查所有运行时权限是否已授予
     */
    public static boolean hasAllRuntimePermissions(Context ctx) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && !hasNotificationPermission(ctx)) return false;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                && !hasBluetoothConnect(ctx)) return false;
        if (!hasCamera(ctx)) return false;
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R && !hasStorageAccess(ctx)) return false;
        return true;
    }
}
