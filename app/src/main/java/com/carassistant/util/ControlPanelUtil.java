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
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.AudioManager;
import android.net.Uri;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.provider.Settings;
import android.view.Window;
import android.view.WindowManager;

import androidx.core.content.ContextCompat;

import java.lang.reflect.Method;

/**
 * 车机控制面板工具类：音量/亮度/蓝牙/WiFi/热点/夜间模式
 */
public final class ControlPanelUtil {

    private ControlPanelUtil() {}

    // ============ 音量 ============
    public static int getCurrentVolume(Context ctx) {
        AudioManager am = (AudioManager) ctx.getSystemService(Context.AUDIO_SERVICE);
        return am.getStreamVolume(AudioManager.STREAM_MUSIC);
    }

    public static int getMaxVolume(Context ctx) {
        AudioManager am = (AudioManager) ctx.getSystemService(Context.AUDIO_SERVICE);
        return am.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
    }

    public static void setVolume(Context ctx, int volume) {
        AudioManager am = (AudioManager) ctx.getSystemService(Context.AUDIO_SERVICE);
        am.setStreamVolume(AudioManager.STREAM_MUSIC, volume, AudioManager.FLAG_SHOW_UI);
    }

    // ============ 屏幕亮度 ============
    public static int getBrightness(Context ctx) {
        try {
            return Settings.System.getInt(ctx.getContentResolver(),
                    Settings.System.SCREEN_BRIGHTNESS);
        } catch (Exception e) {
            return 128;
        }
    }

    public static int getMaxBrightness() { return 255; }

    public static void setBrightness(Context ctx, int brightness) {
        try {
            // 先关闭自动亮度
            Settings.System.putInt(ctx.getContentResolver(),
                    Settings.System.SCREEN_BRIGHTNESS_MODE,
                    Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL);
            // 写入亮度
            Settings.System.putInt(ctx.getContentResolver(),
                    Settings.System.SCREEN_BRIGHTNESS, brightness);
        } catch (Exception e) {
            // 权限不足：直接改当前窗口亮度作为兜底
            try {
                Activity act = (Activity) ctx;
                Window window = act.getWindow();
                WindowManager.LayoutParams lp = window.getAttributes();
                lp.screenBrightness = brightness / 255f;
                window.setAttributes(lp);
            } catch (Exception ignored) {}
        }
    }

    /** 是否有修改系统设置权限 */
    public static boolean canWriteSettings(Context ctx) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return Settings.System.canWrite(ctx);
        }
        return true;
    }

    /** 跳转到"可修改系统设置"授权页 */
    public static void requestWriteSettings(Activity act, int requestCode) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Intent intent = new Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS);
            intent.setData(Uri.parse("package:" + act.getPackageName()));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            act.startActivityForResult(intent, requestCode);
        }
    }

    // ============ WiFi ============
    public static boolean isWifiEnabled(Context ctx) {
        WifiManager wm = (WifiManager) ctx.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        return wm != null && wm.isWifiEnabled();
    }

    /** 切换 WiFi（Android 10+ 系统应用才能直接开关，普通应用跳设置页） */
    public static void toggleWifi(Context ctx) {
        // Android 10+ 普通应用无法直接开关 WiFi，直接跳设置页
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            openSettings(ctx, Settings.ACTION_WIFI_SETTINGS);
            return;
        }
        WifiManager wm = (WifiManager) ctx.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        if (wm == null) return;
        try {
            wm.setWifiEnabled(!wm.isWifiEnabled());
        } catch (Exception e) {
            openSettings(ctx, Settings.ACTION_WIFI_SETTINGS);
        }
    }

    // ============ 蓝牙 ============
    /** 获取 BluetoothAdapter（兼容 Android 12+ 废弃的 getDefaultAdapter） */
    private static BluetoothAdapter getBluetoothAdapter(Context ctx) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
                BluetoothManager bm = (BluetoothManager) ctx.getSystemService(Context.BLUETOOTH_SERVICE);
                return bm == null ? null : bm.getAdapter();
            } else {
                return BluetoothAdapter.getDefaultAdapter();
            }
        } catch (Exception e) {
            return null;
        }
    }

    /** Android 12+ 检查 BLUETOOTH_CONNECT 运行时权限 */
    private static boolean hasBluetoothConnectPermission(Context ctx) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true;
        return ContextCompat.checkSelfPermission(ctx,
                android.Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED;
    }

    public static boolean isBluetoothEnabled(Context ctx) {
        try {
            if (!hasBluetoothConnectPermission(ctx)) return false;
            BluetoothAdapter adapter = getBluetoothAdapter(ctx);
            return adapter != null && adapter.isEnabled();
        } catch (Exception e) {
            return false;
        }
    }

    public static void toggleBluetooth(Context ctx) {
        try {
            if (!hasBluetoothConnectPermission(ctx)) {
                openSettings(ctx, Settings.ACTION_BLUETOOTH_SETTINGS);
                return;
            }
            BluetoothAdapter adapter = getBluetoothAdapter(ctx);
            if (adapter == null) return;
            if (adapter.isEnabled()) adapter.disable();
            else adapter.enable();
        } catch (Exception e) {
            openSettings(ctx, Settings.ACTION_BLUETOOTH_SETTINGS);
        }
    }

    // ============ 个人热点（反射调用，可能因厂商不同有差异） ============
    public static boolean isHotspotEnabled(Context ctx) {
        try {
            WifiManager wm = (WifiManager) ctx.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            if (wm == null) return false;
            Method method = wm.getClass().getDeclaredMethod("isWifiApEnabled");
            method.setAccessible(true);
            return (boolean) method.invoke(wm);
        } catch (Exception e) {
            return false;
        }
    }

    public static void toggleHotspot(Context ctx) {
        try {
            WifiManager wm = (WifiManager) ctx.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            Method method = wm.getClass().getDeclaredMethod("isWifiApEnabled");
            method.setAccessible(true);
            boolean enabled = (boolean) method.invoke(wm);
            Method setMethod = wm.getClass().getMethod("setWifiApEnabled",
                    android.net.wifi.WifiConfiguration.class, boolean.class);
            setMethod.invoke(wm, null, !enabled);
        } catch (Exception e) {
            // 兜底：跳转到热点设置页
            Intent intent = new Intent("android.settings.TETHER_SETTINGS");
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            try {
                ctx.startActivity(intent);
            } catch (Exception ignored) {}
        }
    }

    // ============ 夜间模式（深色模式） ============
    public static boolean isDarkMode(Context ctx) {
        try {
            android.app.UiModeManager umm = (android.app.UiModeManager)
                    ctx.getSystemService(Context.UI_MODE_SERVICE);
            if (umm != null) {
                return umm.getNightMode() == android.app.UiModeManager.MODE_NIGHT_YES;
            }
        } catch (Exception ignored) {}
        int mode = ctx.getResources().getConfiguration().uiMode
                & android.content.res.Configuration.UI_MODE_NIGHT_MASK;
        return mode == android.content.res.Configuration.UI_MODE_NIGHT_YES;
    }

    public static void toggleDarkMode(Context ctx) {
        try {
            android.app.UiModeManager umm = (android.app.UiModeManager)
                    ctx.getSystemService(Context.UI_MODE_SERVICE);
            if (umm != null) {
                int current = umm.getNightMode();
                int target = (current == android.app.UiModeManager.MODE_NIGHT_YES)
                        ? android.app.UiModeManager.MODE_NIGHT_NO
                        : android.app.UiModeManager.MODE_NIGHT_YES;
                umm.setNightMode(target);
                return;
            }
        } catch (Exception ignored) {}
        // 兜底：跳转到显示设置
        openSettings(ctx, Settings.ACTION_DISPLAY_SETTINGS);
    }

    // ============ 系统设置快捷入口 ============
    public static void openSettings(Context ctx, String action) {
        Intent intent = new Intent(action);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            ctx.startActivity(intent);
        } catch (Exception ignored) {}
    }
}
