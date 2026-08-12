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

package com.carassistant.service;

import android.Manifest;
import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.content.ContentResolver;
import android.content.Context;
import android.content.pm.PackageManager;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.os.Build;
import android.provider.Settings;

import androidx.core.content.ContextCompat;

/**
 * 系统快捷开关辅助类
 * 提供 WiFi/蓝牙/手电筒/旋转锁定的状态查询与切换。
 *
 * 注意：
 * - WiFi 开关在 Android 10+ 需要系统权限，普通应用只能跳转设置页；这里尽力尝试，失败时返回当前状态
 * - 蓝牙开关 Android 6+ 需要 BLUETOOTH_CONNECT 权限（Android 12+）
 * - 手电筒使用 CameraManager.setTorchMode，Android 6+ 支持
 * - 旋转锁定通过 Settings.System.ACCELEROMETER_ROTATION 控制
 */
final class SystemToggleHelper {

    private SystemToggleHelper() {}

    /** WiFi 是否开启 */
    static boolean isWifiOn(Context ctx) {
        try {
            android.net.wifi.WifiManager wm = (android.net.wifi.WifiManager)
                    ctx.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            return wm != null && wm.isWifiEnabled();
        } catch (Exception e) {
            return false;
        }
    }

    /** 切换 WiFi 开关（Android 10+ 普通应用无法直接切换，失败时仅返回当前状态） */
    static boolean toggleWifi(Context ctx) {
        try {
            android.net.wifi.WifiManager wm = (android.net.wifi.WifiManager)
                    ctx.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            if (wm == null) return false;
            boolean target = !wm.isWifiEnabled();
            // Android 10+ setWifiEnabled 已废弃且对非系统应用失效，但仍尝试调用
            try { wm.setWifiEnabled(target); } catch (Exception ignored) {}
            return isWifiOn(ctx);
        } catch (Exception e) {
            return isWifiOn(ctx);
        }
    }

    /** 蓝牙是否开启 */
    @SuppressLint("MissingPermission")
    static boolean isBluetoothOn(Context ctx) {
        try {
            if (!hasBluetoothPermission(ctx)) return false;
            BluetoothAdapter ba = getBluetoothAdapter(ctx);
            return ba != null && ba.isEnabled();
        } catch (Exception e) {
            return false;
        }
    }

    /** 切换蓝牙开关 */
    @SuppressLint("MissingPermission")
    static boolean toggleBluetooth(Context ctx) {
        try {
            if (!hasBluetoothPermission(ctx)) return false;
            BluetoothAdapter ba = getBluetoothAdapter(ctx);
            if (ba == null) return false;
            if (ba.isEnabled()) {
                try { ba.disable(); } catch (Exception ignored) {}
            } else {
                try { ba.enable(); } catch (Exception ignored) {}
            }
            return ba.isEnabled();
        } catch (Exception e) {
            return isBluetoothOn(ctx);
        }
    }

    private static boolean hasBluetoothPermission(Context ctx) {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.S
                || ContextCompat.checkSelfPermission(ctx, Manifest.permission.BLUETOOTH_CONNECT)
                == PackageManager.PERMISSION_GRANTED;
    }

    private static BluetoothAdapter getBluetoothAdapter(Context ctx) {
        BluetoothManager manager = (BluetoothManager) ctx.getSystemService(Context.BLUETOOTH_SERVICE);
        return manager != null ? manager.getAdapter() : null;
    }

    /** 手电筒状态（CameraManager 不提供直接查询，用内部标志位） */
    private static volatile boolean sTorchOn = false;

    static boolean isTorchOn(Context ctx) {
        return sTorchOn;
    }

    /** 切换手电筒 */
    static boolean toggleTorch(Context ctx) {
        try {
            CameraManager cm = (CameraManager) ctx.getSystemService(Context.CAMERA_SERVICE);
            if (cm == null) return false;
            // 查找带闪光灯的后置摄像头
            String targetCam = null;
            for (String id : cm.getCameraIdList()) {
                CameraCharacteristics c = cm.getCameraCharacteristics(id);
                Integer facing = c.get(CameraCharacteristics.LENS_FACING);
                Boolean flash = c.get(CameraCharacteristics.FLASH_INFO_AVAILABLE);
                if (facing != null && facing == CameraCharacteristics.LENS_FACING_BACK
                        && flash != null && flash) {
                    targetCam = id;
                    break;
                }
            }
            if (targetCam == null) return false;
            final String camId = targetCam;
            final boolean target = !sTorchOn;
            cm.setTorchMode(camId, target);
            sTorchOn = target;
            return sTorchOn;
        } catch (Exception e) {
            android.util.Log.e("ToggleHelper", "toggleTorch failed", e);
            return sTorchOn;
        }
    }

    /** 自动旋转是否开启 */
    static boolean isRotationOn(Context ctx) {
        try {
            return Settings.System.getInt(ctx.getContentResolver(),
                    Settings.System.ACCELEROMETER_ROTATION, 0) == 1;
        } catch (Exception e) {
            return false;
        }
    }

    /** 切换自动旋转 */
    static boolean toggleRotation(Context ctx) {
        try {
            ContentResolver cr = ctx.getContentResolver();
            boolean target = !isRotationOn(ctx);
            Settings.System.putInt(cr, Settings.System.ACCELEROMETER_ROTATION, target ? 1 : 0);
            return isRotationOn(ctx);
        } catch (Exception e) {
            android.util.Log.e("ToggleHelper", "toggleRotation failed (need WRITE_SETTINGS permission)", e);
            return isRotationOn(ctx);
        }
    }
}
