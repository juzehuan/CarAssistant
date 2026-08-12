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

import java.util.ArrayList;
import java.util.List;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;
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
            // 媒体控制统一控制「当前正在播放的应用」：始终由系统路由，不再定向到指定应用。
            case KeyMappingUtil.ACTION_MEDIA_PLAY_PAUSE:
                dispatchMediaKey(ctx, android.view.KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, "");
                break;
            case KeyMappingUtil.ACTION_MEDIA_NEXT:
                dispatchMediaKey(ctx, android.view.KeyEvent.KEYCODE_MEDIA_NEXT, "");
                break;
            case KeyMappingUtil.ACTION_MEDIA_PREVIOUS:
                dispatchMediaKey(ctx, android.view.KeyEvent.KEYCODE_MEDIA_PREVIOUS, "");
                break;
            case KeyMappingUtil.ACTION_MEDIA_STOP:
                dispatchMediaKey(ctx, android.view.KeyEvent.KEYCODE_MEDIA_STOP, "");
                break;
            case KeyMappingUtil.ACTION_MEDIA_FAST_FORWARD:
                dispatchMediaKey(ctx, android.view.KeyEvent.KEYCODE_MEDIA_FAST_FORWARD, "");
                break;
            case KeyMappingUtil.ACTION_MEDIA_REWIND:
                dispatchMediaKey(ctx, android.view.KeyEvent.KEYCODE_MEDIA_REWIND, "");
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
                    it.putExtra(MainActivity.EXTRA_NAV_ID, R.id.nav_file);
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

    /**
     * 调整音乐音量（自诊断版）。
     *
     * 策略：优先通过当前媒体会话（MediaController.adjustVolume）调整，等同用户与播放器交互，
     * 不受 Android 12+ 对后台第三方应用 adjustStreamVolume(STREAM_MUSIC) 的静默限制；
     * 若媒体会话方式未真正改变 STREAM_MUSIC 音量，再回退 AudioManager。
     * 每一步都写入 /sdcard/carassist_vol.log，便于事后排查"为什么没效果"。
     */
    private static void adjustVolume(Context ctx, int direction) {
        AudioManager am = (AudioManager) ctx.getSystemService(Context.AUDIO_SERVICE);
        int before = am.getStreamVolume(AudioManager.STREAM_MUSIC);
        boolean changed = false;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            List<android.media.session.MediaController> sessions = listVolumeSessions(ctx);
            android.media.session.MediaController target = null;
            for (android.media.session.MediaController mc : sessions) {
                if (isSessionPlaying(mc)) { target = mc; break; }
            }
            if (target == null && !sessions.isEmpty()) target = sessions.get(0);
            logVol("adjustVolume dir=" + direction + " sessions=" + describeSessions(sessions)
                    + " target=" + (target == null ? "none" : target.getPackageName()));
            if (target != null) {
                try {
                    // 会话自身的 adjustVolume 既覆盖本地音量会话（会改变系统 STREAM_MUSIC），
                    // 也覆盖远程音量会话（如 QQ音乐，改变的是会话自身音量，即用户实际听到的音量）。
                    // 两种情况都应视为已生效，不要再回退到被 Android 12+ 后台限制的 AudioManager。
                    target.adjustVolume(direction, 0);
                    SystemClock.sleep(80);
                    changed = true;
                    logVol("session adjust done, currentVolume=" + currentSessionVolume(target)
                            + " streamVol=" + am.getStreamVolume(AudioManager.STREAM_MUSIC));
                } catch (Exception e) {
                    logVol("session adjust failed: " + e);
                }
            }
        }
        if (!changed) {
            try {
                am.adjustStreamVolume(AudioManager.STREAM_MUSIC, direction,
                        AudioManager.FLAG_SHOW_UI | AudioManager.FLAG_ALLOW_RINGER_MODES);
                logVol("AudioManager fallback before=" + before
                        + " after=" + am.getStreamVolume(AudioManager.STREAM_MUSIC));
            } catch (Exception e) {
                logVol("AudioManager fallback failed: " + e);
            }
        } else {
            logVol("session adjusted OK, before=" + before
                    + " after=" + am.getStreamVolume(AudioManager.STREAM_MUSIC));
        }
    }

    /** 实时枚举本应用通过通知访问权限可见的媒体会话（不依赖服务内部缓存） */
    private static List<android.media.session.MediaController> listVolumeSessions(Context ctx) {
        try {
            android.media.session.MediaSessionManager msm = (android.media.session.MediaSessionManager)
                    ctx.getSystemService(Context.MEDIA_SESSION_SERVICE);
            android.content.ComponentName cn = new android.content.ComponentName(ctx,
                    com.carassistant.service.TargetMediaSessionService.class);
            return msm.getActiveSessions(cn);
        } catch (Exception e) {
            logVol("listVolumeSessions failed: " + e);
            return new ArrayList<>();
        }
    }

    /** 判定会话是否可视为"播放中"（含暂停，遵循 App 既有语义） */
    private static boolean isSessionPlaying(android.media.session.MediaController mc) {
        android.media.session.PlaybackState st = mc.getPlaybackState();
        if (st == null) return false;
        int s = st.getState();
        return s == android.media.session.PlaybackState.STATE_PLAYING
                || s == android.media.session.PlaybackState.STATE_BUFFERING
                || s == android.media.session.PlaybackState.STATE_PAUSED
                || s == android.media.session.PlaybackState.STATE_FAST_FORWARDING
                || s == android.media.session.PlaybackState.STATE_REWINDING
                || s == 9 || s == 10 || s == 11;
    }

    private static String describeSessions(List<android.media.session.MediaController> list) {
        StringBuilder sb = new StringBuilder();
        for (android.media.session.MediaController mc : list) {
            android.media.session.PlaybackState ps = mc.getPlaybackState();
            sb.append(mc.getPackageName()).append('(')
              .append(ps == null ? "null" : ps.getState()).append("),");
        }
        return sb.toString();
    }

    /** 读取会话当前音量（用于诊断日志，验证 adjustVolume/setVolumeTo 是否真正改变了会话音量） */
    private static int currentSessionVolume(android.media.session.MediaController mc) {
        try {
            android.media.session.MediaController.PlaybackInfo pi = mc.getPlaybackInfo();
            return pi == null ? -1 : pi.getCurrentVolume();
        } catch (Exception e) {
            return -1;
        }
    }

    /** 音量调试日志（仅输出到 logcat，便于必要时通过 adb logcat 排查） */
    private static void logVol(String msg) {
        Log.d("KeyAction", msg);
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
            } catch (Exception ignored) {
                // 定向派发异常，继续走下方回退判断
            }
            // 到这里说明定向派发失败/被旁路：
            // - 若有其他【未绑定】应用正在播放，则不回退 AudioManager 全局派发，
            //   避免把按键误派发给其它音乐 App（即"只绑了 A 却也控了 B"的 bug）；
            // - 否则回退全局派发，保证对目标应用（或系统当前播放器）的按键有效。
            //   （无通知权限导致 sActiveSessions 为空时 isOtherAppPlaying 恒为 false，
            //    从而回退全局，恢复"媒体控制有效"的可用性。）
            if (com.carassistant.service.TargetMediaSessionService.isOtherAppPlaying(targets)) {
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
            int before = am.getStreamVolume(AudioManager.STREAM_MUSIC);
            boolean changed = false;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                List<android.media.session.MediaController> sessions = listVolumeSessions(ctx);
                android.media.session.MediaController ctrl = null;
                for (android.media.session.MediaController mc : sessions) {
                    if (isSessionPlaying(mc)) { ctrl = mc; break; }
                }
                if (ctrl == null && !sessions.isEmpty()) ctrl = sessions.get(0);
                logVol("setVolumePercent " + percent + "% sessions=" + describeSessions(sessions)
                        + " target=" + (ctrl == null ? "none" : ctrl.getPackageName()));
                if (ctrl != null) {
                    try {
                        android.media.session.MediaController.PlaybackInfo info = ctrl.getPlaybackInfo();
                        if (info != null && info.getMaxVolume() > 0) {
                            int vol = Math.max(0, Math.min(info.getMaxVolume(),
                                    Math.round(info.getMaxVolume() * percent / 100f)));
                            ctrl.setVolumeTo(vol, 0);
                            SystemClock.sleep(80);
                            changed = true;
                            logVol("setVolume session done, currentVolume=" + currentSessionVolume(ctrl)
                                    + " streamVol=" + am.getStreamVolume(AudioManager.STREAM_MUSIC));
                        }
                    } catch (Exception e) {
                        logVol("setVolume session failed: " + e);
                    }
                }
            }
            if (!changed) {
                int volTarget = max * percent / 100;
                am.setStreamVolume(AudioManager.STREAM_MUSIC, volTarget, AudioManager.FLAG_SHOW_UI);
                logVol("setVolume AudioManager fallback target=" + volTarget);
            } else {
                logVol("setVolume session OK, before=" + before
                        + " after=" + am.getStreamVolume(AudioManager.STREAM_MUSIC));
            }
            toast(ctx, "音量已设为 " + percent + "%");
        } catch (Exception e) {
            Log.e("KeyAction", "setVolumePercent failed", e);
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
