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
import android.app.ActivityManager;
import android.app.SearchManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.hardware.camera2.CameraManager;
import android.media.AudioManager;
import android.net.Uri;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.AlarmClock;
import android.provider.MediaStore;
import android.provider.Settings;
import android.widget.Toast;

import com.carassistant.MainActivity;
import com.carassistant.R;
import com.carassistant.SettingsActivity;
import com.carassistant.service.KeyMappingAccessibilityService;
import com.carassistant.ui.DeviceInfoActivity;
import com.carassistant.ui.FileFragment;

import java.lang.reflect.Method;

/**
 * 按键动作执行器
 *
 * 将映射的动作类型转换为实际系统操作。
 * 提取为独立工具类，便于在 MainActivity / SidebarService 等场景复用。
 *
 * 所有操作均带 try-catch，避免因权限不足或系统不支持导致崩溃。
 */
public final class KeyActionExecutor {

    private KeyActionExecutor() {}

    /** 执行映射动作 */
    public static void execute(Context ctx, KeyMappingUtil.KeyMapping mapping) {
        if (mapping == null || !mapping.enabled) return;
        switch (mapping.actionType) {
            // ============ 应用启动类 ============
            case KeyMappingUtil.ACTION_OPEN_APP:
                launchApp(ctx, mapping.actionData);
                break;
            case KeyMappingUtil.ACTION_OPEN_ACTIVITY:
                launchActivity(ctx, mapping.actionData);
                break;

            // ============ 音量类 ============
            case KeyMappingUtil.ACTION_VOLUME_UP:
                adjustVolume(ctx, AudioManager.ADJUST_RAISE);
                break;
            case KeyMappingUtil.ACTION_VOLUME_DOWN:
                adjustVolume(ctx, AudioManager.ADJUST_LOWER);
                break;
            case KeyMappingUtil.ACTION_VOLUME_MUTE:
                adjustVolume(ctx, AudioManager.ADJUST_TOGGLE_MUTE);
                break;

            // ============ 媒体控制类 ============
            // 若映射指定了 targetPackage，优先通过 TargetMediaSessionService 定向派发到该应用；
            // 失败或未指定则回退到 AudioManager 全局派发（由系统路由到当前播放器）
            case KeyMappingUtil.ACTION_MEDIA_PLAY_PAUSE:
                dispatchMediaKey(ctx, android.view.KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, mapping.targetPackage);
                break;
            case KeyMappingUtil.ACTION_MEDIA_NEXT:
                dispatchMediaKey(ctx, android.view.KeyEvent.KEYCODE_MEDIA_NEXT, mapping.targetPackage);
                break;
            case KeyMappingUtil.ACTION_MEDIA_PREVIOUS:
                dispatchMediaKey(ctx, android.view.KeyEvent.KEYCODE_MEDIA_PREVIOUS, mapping.targetPackage);
                break;
            case KeyMappingUtil.ACTION_MEDIA_STOP:
                dispatchMediaKey(ctx, android.view.KeyEvent.KEYCODE_MEDIA_STOP, mapping.targetPackage);
                break;

            // ============ 开关类 ============
            case KeyMappingUtil.ACTION_TOGGLE_WIFI:
                ControlPanelUtil.toggleWifi(ctx);
                break;
            case KeyMappingUtil.ACTION_TOGGLE_BLUETOOTH:
                ControlPanelUtil.toggleBluetooth(ctx);
                break;
            case KeyMappingUtil.ACTION_TOGGLE_AIRPLANE:
                toggleAirplane(ctx);
                break;
            case KeyMappingUtil.ACTION_TOGGLE_TORCH:
                toggleTorch(ctx);
                break;
            case KeyMappingUtil.ACTION_TOGGLE_AUTO_BRIGHTNESS:
                toggleAutoBrightness(ctx);
                break;

            // ============ 屏幕亮度类 ============
            case KeyMappingUtil.ACTION_BRIGHTNESS_UP:
                adjustBrightness(ctx, 25);
                break;
            case KeyMappingUtil.ACTION_BRIGHTNESS_DOWN:
                adjustBrightness(ctx, -25);
                break;
            case KeyMappingUtil.ACTION_BRIGHTNESS_MAX:
                setBrightness(ctx, 255);
                break;

            // ============ 系统操作类 ============
            case KeyMappingUtil.ACTION_LOCK_SCREEN:
                lockScreen(ctx);
                break;
            case KeyMappingUtil.ACTION_SCREENSHOT:
                takeScreenshot(ctx);
                break;
            case KeyMappingUtil.ACTION_CLEAN_MEMORY:
                cleanMemory(ctx);
                break;
            case KeyMappingUtil.ACTION_BACK:
                simulateBack(ctx);
                break;
            case KeyMappingUtil.ACTION_RECENT_TASKS:
                simulateRecentTasks(ctx);
                break;
            case KeyMappingUtil.ACTION_POWER_DIALOG:
                showPowerDialog(ctx);
                break;

            // ============ 车机助手内跳转 ============
            case KeyMappingUtil.ACTION_OPEN_CONTROL_PANEL:
                // 控制面板已下架，提示用户
                if (ctx instanceof android.app.Activity) {
                    ((android.app.Activity) ctx).runOnUiThread(() ->
                            Toast.makeText(ctx, "控制面板已下架", Toast.LENGTH_SHORT).show());
                }
                break;
            case KeyMappingUtil.ACTION_BACK_HOME:
                if (ctx instanceof MainActivity) {
                    ((MainActivity) ctx).runOnUiThread(() ->
                            Toast.makeText(ctx, R.string.home_feature_home, Toast.LENGTH_SHORT).show());
                }
                openLauncher(ctx);
                break;
            case KeyMappingUtil.ACTION_OPEN_SETTINGS:
                startActivitySafe(ctx, new Intent(ctx, SettingsActivity.class));
                break;
            case KeyMappingUtil.ACTION_OPEN_FILE_MANAGER:
                try {
                    Intent it = new Intent(ctx, MainActivity.class);
                    it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                    it.putExtra("target_tab", "file");
                    ctx.startActivity(it);
                } catch (Exception e) {
                    toast(ctx, ctx.getString(R.string.launch_fail));
                }
                break;
            case KeyMappingUtil.ACTION_OPEN_KEY_MAPPING:
                startActivitySafe(ctx, new Intent(ctx, com.carassistant.ui.KeyMappingActivity.class));
                break;
            case KeyMappingUtil.ACTION_OPEN_DEVICE_INFO:
                startActivitySafe(ctx, new Intent(ctx, DeviceInfoActivity.class));
                break;

            // ============ 系统设置子页类 ============
            case KeyMappingUtil.ACTION_SETTINGS_WIFI:
                startActivitySafe(ctx, new Intent(Settings.ACTION_WIFI_SETTINGS));
                break;
            case KeyMappingUtil.ACTION_SETTINGS_BLUETOOTH:
                startActivitySafe(ctx, new Intent(Settings.ACTION_BLUETOOTH_SETTINGS));
                break;
            case KeyMappingUtil.ACTION_SETTINGS_DISPLAY:
                startActivitySafe(ctx, new Intent(Settings.ACTION_DISPLAY_SETTINGS));
                break;
            case KeyMappingUtil.ACTION_SETTINGS_SOUND:
                startActivitySafe(ctx, new Intent(Settings.ACTION_SOUND_SETTINGS));
                break;
            case KeyMappingUtil.ACTION_SETTINGS_APPS:
                startActivitySafe(ctx, new Intent(Settings.ACTION_MANAGE_APPLICATIONS_SETTINGS));
                break;
            case KeyMappingUtil.ACTION_SETTINGS_LOCATION:
                startActivitySafe(ctx, new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS));
                break;
            case KeyMappingUtil.ACTION_SETTINGS_DATE_TIME:
                startActivitySafe(ctx, new Intent(Settings.ACTION_DATE_SETTINGS));
                break;
            case KeyMappingUtil.ACTION_SETTINGS_LANGUAGE:
                startActivitySafe(ctx, new Intent(Settings.ACTION_LOCALE_SETTINGS));
                break;
            case KeyMappingUtil.ACTION_SETTINGS_ABOUT:
                startActivitySafe(ctx, new Intent(Settings.ACTION_DEVICE_INFO_SETTINGS));
                break;
            case KeyMappingUtil.ACTION_SETTINGS_BATTERY:
                try {
                    Intent bat = new Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS);
                    bat.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    ctx.startActivity(bat);
                } catch (Exception e) {
                    // 部分系统不支持，回退到应用详情页
                    Intent fallback = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                    fallback.setData(Uri.parse("package:" + ctx.getPackageName()));
                    fallback.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    try { ctx.startActivity(fallback); } catch (Exception ignored) {}
                }
                break;

            // ============ 常用系统应用类 ============
            case KeyMappingUtil.ACTION_OPEN_CAMERA:
                startActivitySafe(ctx, new Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA));
                break;
            case KeyMappingUtil.ACTION_OPEN_CLOCK:
                // 闹钟 Intent（厂商实现可能不同，回退到包名启动）
                try {
                    Intent clock = new Intent(AlarmClock.ACTION_SHOW_ALARMS);
                    clock.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    ctx.startActivity(clock);
                } catch (Exception e) {
                    launchPackage(ctx, "com.android.deskclock");
                }
                break;
            case KeyMappingUtil.ACTION_OPEN_CALENDAR:
                launchPackage(ctx, "com.android.calendar");
                break;
            case KeyMappingUtil.ACTION_OPEN_CALCULATOR:
                launchPackage(ctx, "com.android.calculator2");
                break;
            case KeyMappingUtil.ACTION_OPEN_BROWSER:
                try {
                    Intent browser = new Intent(Intent.ACTION_VIEW, Uri.parse("https://www.baidu.com"));
                    browser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    ctx.startActivity(browser);
                } catch (Exception e) {
                    launchPackage(ctx, "com.android.browser");
                }
                break;
            case KeyMappingUtil.ACTION_OPEN_DIALER:
                try {
                    Intent dial = new Intent(Intent.ACTION_DIAL);
                    dial.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    ctx.startActivity(dial);
                } catch (Exception e) {
                    launchPackage(ctx, "com.android.dialer");
                }
                break;
            case KeyMappingUtil.ACTION_OPEN_CONTACTS:
                try {
                    Intent contacts = new Intent(Intent.ACTION_VIEW);
                    contacts.setType(android.provider.ContactsContract.Contacts.CONTENT_TYPE);
                    contacts.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    ctx.startActivity(contacts);
                } catch (Exception e) {
                    launchPackage(ctx, "com.android.contacts");
                }
                break;
            case KeyMappingUtil.ACTION_OPEN_GALLERY:
                try {
                    Intent gallery = new Intent(Intent.ACTION_VIEW);
                    gallery.setType("image/*");
                    gallery.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    ctx.startActivity(gallery);
                } catch (Exception e) {
                    launchPackage(ctx, "com.android.gallery3d");
                }
                break;

            // ============ 高级动作类 ============
            case KeyMappingUtil.ACTION_OPEN_URL:
                openUrl(ctx, mapping.actionData);
                break;
            case KeyMappingUtil.ACTION_SEND_BROADCAST:
                sendBroadcastAction(ctx, mapping.actionData);
                break;
            case KeyMappingUtil.ACTION_SET_VOLUME:
                setVolumePercent(ctx, mapping.actionData);
                break;
            case KeyMappingUtil.ACTION_EXPAND_NOTIFICATIONS:
                expandStatusBar(ctx, false);
                break;
            case KeyMappingUtil.ACTION_EXPAND_QUICK_SETTINGS:
                expandStatusBar(ctx, true);
                break;

            // ============ 系统设置子页类 ============
            case KeyMappingUtil.ACTION_TOGGLE_INPUT_METHOD:
                toggleInputMethod(ctx);
                break;
            case KeyMappingUtil.ACTION_TOGGLE_ORIENTATION:
                toggleOrientation(ctx);
                break;
            case KeyMappingUtil.ACTION_KILL_CURRENT_APP:
                killCurrentApp(ctx);
                break;
            case KeyMappingUtil.ACTION_OPEN_VOICE_ASSISTANT:
                openVoiceAssistant(ctx);
                break;
            case KeyMappingUtil.ACTION_OPEN_SEARCH:
                try {
                    Intent search = new Intent(SearchManager.INTENT_ACTION_GLOBAL_SEARCH);
                    search.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    ctx.startActivity(search);
                } catch (Exception e) {
                    toast(ctx, "未找到搜索应用");
                }
                break;
        }
    }

    // ============ 内部实现 ============

    private static void launchApp(Context ctx, String pkg) {
        if (pkg == null || pkg.isEmpty()) return;
        Intent it = ctx.getPackageManager().getLaunchIntentForPackage(pkg);
        if (it != null) {
            it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            try { ctx.startActivity(it); }
            catch (Exception e) { toast(ctx, ctx.getString(R.string.launch_fail)); }
        } else {
            toast(ctx, ctx.getString(R.string.app_not_installed));
        }
    }

    private static void launchActivity(Context ctx, String data) {
        // actionData 格式：pkg/cls 或 cls
        if (data == null || data.isEmpty()) return;
        try {
            Intent it = new Intent();
            if (data.contains("/")) {
                String[] parts = data.split("/", 2);
                it.setComponent(new ComponentName(parts[0], parts[1]));
            } else {
                it.setClassName(ctx, data);
            }
            it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            ctx.startActivity(it);
        } catch (Exception e) {
            toast(ctx, ctx.getString(R.string.launch_fail));
        }
    }

    private static void adjustVolume(Context ctx, int direction) {
        try {
            AudioManager am = (AudioManager) ctx.getSystemService(Context.AUDIO_SERVICE);
            am.adjustStreamVolume(AudioManager.STREAM_MUSIC, direction, AudioManager.FLAG_SHOW_UI);
        } catch (Exception ignored) {}
    }

    /**
     * 派发媒体按键事件。
     *
     * 派发优先级（支持多选目标应用）：
     * 1. 若 targetPackage 非空（可含逗号分隔的多个包名）且系统 Android 5.0+：
     *    a. 多个候选包名 → 调用 {@link com.carassistant.service.TargetMediaSessionService#selectTargetPackage}
     *       智能选择最合适的目标（优先正在播放的绑定应用，未绑定应用播放时旁路让系统处理）
     *    b. 单个候选包名 → 直接定向派发
     *    c. 选中的目标通过 dispatchToPackage 派发到其 MediaController
     *    d. 旁路（selectTargetPackage 返回 null）或派发失败 → 回退全局派发
     * 2. 未指定 targetPackage 或 Android 5.0 以下：AudioManager.dispatchMediaKeyEvent
     *    （由系统路由到当前活跃播放器）
     * 3. AudioManager 失败再回退到 ACTION_MEDIA_BUTTON 广播
     *
     * @param ctx           上下文
     * @param keyCode       媒体键码
     * @param targetPackage 定向目标应用包名（逗号分隔多个，空串表示由系统路由）
     */
    private static void dispatchMediaKey(Context ctx, int keyCode, String targetPackage) {
        // 优先：定向派发到指定应用的 MediaController
        java.util.List<String> targets = KeyMappingUtil.parseTargetPackages(targetPackage);
        if (!targets.isEmpty() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            try {
                String selected;
                if (targets.size() == 1) {
                    // 单目标：直接定向派发
                    selected = targets.get(0);
                } else {
                    // 多目标：智能选择最合适的候选应用
                    // - 未绑定的应用正在播放 → 旁路（返回 null，让系统处理原按键）
                    // - 优先派发到正在播放的绑定应用
                    // - 否则返回第一个绑定应用
                    selected = com.carassistant.service.TargetMediaSessionService
                            .selectTargetPackage(ctx, targets);
                }
                if (selected != null
                        && com.carassistant.service.TargetMediaSessionService.dispatchToPackage(
                                selected, keyCode)) {
                    return; // 定向派发成功
                }
                // 已指定目标应用，但定向派发失败或被旁路：
                // 不再回退 AudioManager 全局派发，否则会把按键误派发给其它正在播放的
                // 音乐 App（即"只绑了 A 却也控制了 B"的 bug）。
                // 按键已被无障碍服务消费，此处直接结束，对未绑定的应用无副作用。
                return;
            } catch (Exception ignored) {
                // 定向派发异常：同样不回退全局，避免误控其它应用
                return;
            }
        }
        // 未指定目标应用（targetPackage 为空）：由系统路由到当前活跃播放器
        // 回退1：AudioManager 全局派发
        try {
            AudioManager am = (AudioManager) ctx.getSystemService(Context.AUDIO_SERVICE);
            android.view.KeyEvent down = new android.view.KeyEvent(
                    android.view.KeyEvent.ACTION_DOWN, keyCode);
            android.view.KeyEvent up = new android.view.KeyEvent(
                    android.view.KeyEvent.ACTION_UP, keyCode);
            am.dispatchMediaKeyEvent(down);
            am.dispatchMediaKeyEvent(up);
        } catch (Exception ignored) {
            // 回退2：ACTION_MEDIA_BUTTON 广播
            try {
                Intent i = new Intent(Intent.ACTION_MEDIA_BUTTON);
                i.putExtra(Intent.EXTRA_KEY_EVENT, new android.view.KeyEvent(
                        android.view.KeyEvent.ACTION_DOWN, keyCode));
                ctx.sendBroadcast(i);
            } catch (Exception ignored2) {}
        }
    }

    private static void toggleAirplane(Context ctx) {
        try {
            // Android 4.2+ 无法直接切换飞行模式，引导用户到设置
            boolean enabled = Settings.Global.getInt(ctx.getContentResolver(),
                    Settings.Global.AIRPLANE_MODE_ON, 0) == 1;
            toast(ctx, enabled ? "飞行模式已开启" : "飞行模式已关闭");
            // 直接打开飞行模式设置页
            Intent it = new Intent(Settings.ACTION_AIRPLANE_MODE_SETTINGS);
            it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            ctx.startActivity(it);
        } catch (Exception e) {
            toast(ctx, "无法切换飞行模式");
        }
    }

    private static boolean torchState = false;
    private static void toggleTorch(Context ctx) {
        try {
            CameraManager cm = (CameraManager) ctx.getSystemService(Context.CAMERA_SERVICE);
            if (cm == null) return;
            String[] ids = cm.getCameraIdList();
            if (ids.length == 0) return;
            // 优先找后置闪光灯
            String id = null;
            for (String cid : ids) {
                Boolean f = cm.getCameraCharacteristics(cid).get(
                        android.hardware.camera2.CameraCharacteristics.FLASH_INFO_AVAILABLE);
                if (f != null && f) { id = cid; break; }
            }
            if (id == null) id = ids[0];
            torchState = !torchState;
            cm.setTorchMode(id, torchState);
            toast(ctx, torchState ? "手电筒已开启" : "手电筒已关闭");
        } catch (Exception e) {
            toast(ctx, "无法切换手电筒");
        }
    }

    private static void toggleAutoBrightness(Context ctx) {
        try {
            boolean enabled = Settings.System.getInt(ctx.getContentResolver(),
                    Settings.System.SCREEN_BRIGHTNESS_MODE,
                    Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL)
                    == Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC;
            Settings.System.putInt(ctx.getContentResolver(),
                    Settings.System.SCREEN_BRIGHTNESS_MODE,
                    enabled ? Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL
                            : Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC);
            toast(ctx, enabled ? "已关闭自动亮度" : "已开启自动亮度");
        } catch (Exception e) {
            toast(ctx, "需要写入系统设置权限");
        }
    }

    /** 调整屏幕亮度（增量） */
    private static void adjustBrightness(Context ctx, int delta) {
        try {
            int cur = Settings.System.getInt(ctx.getContentResolver(),
                    Settings.System.SCREEN_BRIGHTNESS);
            int next = Math.max(0, Math.min(255, cur + delta));
            Settings.System.putInt(ctx.getContentResolver(),
                    Settings.System.SCREEN_BRIGHTNESS, next);
            toast(ctx, "亮度：" + (next * 100 / 255) + "%");
        } catch (Exception e) {
            toast(ctx, "需要写入系统设置权限");
        }
    }

    private static void setBrightness(Context ctx, int value) {
        try {
            Settings.System.putInt(ctx.getContentResolver(),
                    Settings.System.SCREEN_BRIGHTNESS, value);
            toast(ctx, "亮度已调至最大");
        } catch (Exception e) {
            toast(ctx, "需要写入系统设置权限");
        }
    }

    /** 锁屏：优先使用无障碍服务，回退到设备管理员 */
    private static void lockScreen(Context ctx) {
        // 优先：无障碍服务 GLOBAL_ACTION_LOCK_SCREEN（Android 9+）
        KeyMappingAccessibilityService acc = KeyMappingAccessibilityService.getInstance();
        if (acc != null && acc.lockScreen()) {
            return;
        }
        // 回退：设备管理员
        try {
            Class<?> clazz = Class.forName("android.app.admin.DevicePolicyManager");
            Object dpm = ctx.getSystemService(Context.DEVICE_POLICY_SERVICE);
            Method m = clazz.getMethod("lockNow");
            m.invoke(dpm);
        } catch (Exception e) {
            toast(ctx, "锁屏需要开启无障碍服务或设备管理员权限");
        }
    }

    /** 截屏：通过无障碍服务 API（Android 11+） */
    private static void takeScreenshot(Context ctx) {
        KeyMappingAccessibilityService acc = KeyMappingAccessibilityService.getInstance();
        if (acc != null && acc.takeScreenshot()) {
            return;
        }
        toast(ctx, "截屏需要开启无障碍服务（Android 11+）");
    }

    private static void cleanMemory(Context ctx) {
        try {
            ActivityManager am = (ActivityManager) ctx.getSystemService(Context.ACTIVITY_SERVICE);
            am.killBackgroundProcesses(ctx.getPackageName());
            toast(ctx, ctx.getString(R.string.cleaned));
        } catch (Exception ignored) {}
    }

    /** 模拟返回键：优先使用无障碍 GLOBAL_ACTION_BACK */
    private static void simulateBack(Context ctx) {
        KeyMappingAccessibilityService acc = KeyMappingAccessibilityService.getInstance();
        if (acc != null && acc.performGlobalBack()) {
            return;
        }
        // 回退：Activity.onBackPressed（仅应用内有效）
        try {
            if (ctx instanceof Activity) {
                ((Activity) ctx).onBackPressed();
            } else {
                android.app.Instrumentation inst = new android.app.Instrumentation();
                inst.sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK);
            }
        } catch (Exception ignored) {
            toast(ctx, "返回键需要开启无障碍服务");
        }
    }

    /** 模拟最近任务键：优先使用无障碍 GLOBAL_ACTION_RECENTS */
    private static void simulateRecentTasks(Context ctx) {
        KeyMappingAccessibilityService acc = KeyMappingAccessibilityService.getInstance();
        if (acc != null && acc.performRecentTasks()) {
            return;
        }
        // 回退：反射调用 StatusBarService
        try {
            Class<?> sm = Class.forName("android.os.ServiceManager");
            Object statusbar = sm.getMethod("getService", String.class).invoke(null, "statusbar");
            Class<?> stub = Class.forName("com.android.internal.statusbar.IStatusBarService$Stub");
            Method asInterface = stub.getMethod("asInterface", android.os.IBinder.class);
            Object proxy = asInterface.invoke(null, statusbar);
            if (proxy != null) {
                Method toggle = proxy.getClass().getMethod("toggleRecentApps");
                toggle.invoke(proxy);
                return;
            }
        } catch (Exception ignored) {}
        toast(ctx, "最近任务键需要开启无障碍服务");
    }

    /** 显示电源对话框 */
    private static void showPowerDialog(Context ctx) {
        try {
            android.app.AlertDialog.Builder b = new android.app.AlertDialog.Builder(ctx);
            b.setTitle("电源");
            String[] items = {"关机", "重启", "取消"};
            b.setItems(items, (d, w) -> {
                try {
                    if (w == 0) {
                        Runtime.getRuntime().exec(new String[]{"su", "-c", "reboot -p"});
                    } else if (w == 1) {
                        Runtime.getRuntime().exec(new String[]{"su", "-c", "reboot"});
                    }
                } catch (Exception e) {
                    toast(ctx, "需要 root 权限");
                }
            });
            b.show();
        } catch (Exception ignored) {}
    }

    /** 打开 Launcher（回到桌面） */
    private static void openLauncher(Context ctx) {
        try {
            Intent home = new Intent(Intent.ACTION_MAIN);
            home.addCategory(Intent.CATEGORY_HOME);
            home.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            ctx.startActivity(home);
        } catch (Exception ignored) {}
    }

    private static void startActivitySafe(Context ctx, Intent it) {
        try {
            it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            ctx.startActivity(it);
        } catch (Exception e) {
            toast(ctx, ctx.getString(R.string.launch_fail));
        }
    }

    /** 通过包名启动应用（启动失败有提示） */
    private static void launchPackage(Context ctx, String pkg) {
        if (pkg == null || pkg.isEmpty()) return;
        Intent it = ctx.getPackageManager().getLaunchIntentForPackage(pkg);
        if (it != null) {
            it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            try { ctx.startActivity(it); }
            catch (Exception e) { toast(ctx, ctx.getString(R.string.launch_fail)); }
        } else {
            toast(ctx, "应用未安装：" + pkg);
        }
    }

    /** 打开 URL（默认浏览器） */
    private static void openUrl(Context ctx, String url) {
        if (url == null || url.trim().isEmpty()) {
            toast(ctx, "URL 为空");
            return;
        }
        String u = url.trim();
        if (!u.startsWith("http://") && !u.startsWith("https://") && !u.startsWith("file://")) {
            u = "https://" + u;
        }
        try {
            Intent it = new Intent(Intent.ACTION_VIEW, Uri.parse(u));
            it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            ctx.startActivity(it);
        } catch (Exception e) {
            toast(ctx, "无法打开网址");
        }
    }

    /** 发送广播 */
    private static void sendBroadcastAction(Context ctx, String action) {
        if (action == null || action.trim().isEmpty()) {
            toast(ctx, "广播 action 为空");
            return;
        }
        try {
            ctx.sendBroadcast(new Intent(action.trim()));
            toast(ctx, "已发送广播：" + action.trim());
        } catch (Exception e) {
            toast(ctx, "发送广播失败");
        }
    }

    /** 设置音量百分比（0-100） */
    private static void setVolumePercent(Context ctx, String data) {
        try {
            int percent = Integer.parseInt(data.trim());
            percent = Math.max(0, Math.min(100, percent));
            AudioManager am = (AudioManager) ctx.getSystemService(Context.AUDIO_SERVICE);
            int max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
            int target = max * percent / 100;
            am.setStreamVolume(AudioManager.STREAM_MUSIC, target, AudioManager.FLAG_SHOW_UI);
            toast(ctx, "音量已设为 " + percent + "%");
        } catch (Exception e) {
            toast(ctx, "音量设置失败");
        }
    }

    /** 展开通知栏 / 快捷设置：优先使用无障碍 GLOBAL_ACTION_NOTIFICATIONS */
    private static void expandStatusBar(Context ctx, boolean quickSettings) {
        // 优先：无障碍服务
        KeyMappingAccessibilityService acc = KeyMappingAccessibilityService.getInstance();
        if (acc != null) {
            boolean ok = quickSettings ? acc.expandQuickSettings() : acc.expandNotifications();
            if (ok) return;
        }
        // 回退：反射调用 StatusBarService
        try {
            Class<?> sm = Class.forName("android.os.ServiceManager");
            Object statusbar = sm.getMethod("getService", String.class).invoke(null, "statusbar");
            Class<?> stub = Class.forName("com.android.internal.statusbar.IStatusBarService$Stub");
            Method asInterface = stub.getMethod("asInterface", android.os.IBinder.class);
            Object proxy = asInterface.invoke(null, statusbar);
            if (proxy != null) {
                String methodName = quickSettings ? "expandSettingsPanel" : "expandNotificationsPanel";
                Method m = proxy.getClass().getMethod(methodName);
                m.invoke(proxy);
                return;
            }
        } catch (Exception ignored) {}
        // 再回退：广播 Action（部分系统支持）
        try {
            Intent it = new Intent(quickSettings
                    ? "com.android.internal.statusbar.EXPAND_QUICK_SETTINGS"
                    : "com.android.internal.statusbar.EXPAND_NOTIFICATIONS");
            ctx.sendBroadcast(it);
        } catch (Exception ignored) {
            toast(ctx, "展开通知栏需要开启无障碍服务");
        }
    }

    /** 切换输入法（系统提供了 ACTION_SHOW_INPUT_METHOD_PICKER） */
    private static void toggleInputMethod(Context ctx) {
        try {
            android.view.inputmethod.InputMethodManager imm =
                    (android.view.inputmethod.InputMethodManager)
                            ctx.getSystemService(Context.INPUT_METHOD_SERVICE);
            imm.showInputMethodPicker();
        } catch (Exception e) {
            toast(ctx, "无法切换输入法");
        }
    }

    /** 切换屏幕方向锁定（自动旋转） */
    private static void toggleOrientation(Context ctx) {
        try {
            boolean enabled = Settings.System.getInt(ctx.getContentResolver(),
                    Settings.System.ACCELEROMETER_ROTATION, 0) == 1;
            Settings.System.putInt(ctx.getContentResolver(),
                    Settings.System.ACCELEROMETER_ROTATION, enabled ? 0 : 1);
            toast(ctx, enabled ? "已锁定屏幕方向" : "已开启自动旋转");
        } catch (Exception e) {
            toast(ctx, "需要写入系统设置权限");
        }
    }

    /** 结束当前前台应用 */
    private static void killCurrentApp(Context ctx) {
        try {
            ActivityManager am = (ActivityManager) ctx.getSystemService(Context.ACTIVITY_SERVICE);
            // Android 5.0+ 只能获取自己应用的进程
            // 通过 getRunningTasks 获取当前前台应用（需要特殊权限）
            // 简化处理：killBackgroundProcesses 当前包名
            String pkg = null;
            try {
                java.lang.reflect.Method m = ActivityManager.class.getMethod("getRunningTasks", int.class);
                @SuppressWarnings("unchecked")
                java.util.List<ActivityManager.RunningTaskInfo> tasks =
                        (java.util.List<ActivityManager.RunningTaskInfo>) m.invoke(am, 1);
                if (tasks != null && !tasks.isEmpty()) {
                    pkg = tasks.get(0).topActivity.getPackageName();
                }
            } catch (Exception ignored) {}
            if (pkg != null && !pkg.equals(ctx.getPackageName())) {
                am.killBackgroundProcesses(pkg);
                toast(ctx, "已结束应用：" + pkg);
            } else {
                toast(ctx, "无法结束当前应用");
            }
        } catch (Exception e) {
            toast(ctx, "结束应用失败");
        }
    }

    /** 打开语音助手 */
    private static void openVoiceAssistant(Context ctx) {
        try {
            Intent it = new Intent("android.intent.action.VOICE_COMMAND");
            it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            ctx.startActivity(it);
        } catch (Exception e) {
            try {
                Intent it = new Intent("android.intent.action.ASSIST");
                it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                ctx.startActivity(it);
            } catch (Exception e2) {
                toast(ctx, "未找到语音助手");
            }
        }
    }

    private static void toast(Context ctx, String msg) {
        if (ctx == null || msg == null) return;
        new Handler(Looper.getMainLooper()).post(() ->
                Toast.makeText(ctx, msg, Toast.LENGTH_SHORT).show());
    }
}
