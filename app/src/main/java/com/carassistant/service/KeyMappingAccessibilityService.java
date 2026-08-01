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

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.graphics.Path;
import android.os.Build;
import android.util.Log;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityEvent;

import androidx.annotation.Nullable;

import com.carassistant.util.AppAutoStartManager;
import com.carassistant.util.KeyActionExecutor;
import com.carassistant.util.KeyMappingUtil;
import com.carassistant.util.KeyTriggerDetector;

/**
 * 车机助手无障碍服务
 *
 * 用途：
 * 1. 全局拦截物理按键（任意界面下生效，不仅限应用内）
 * 2. 提供手势模拟 API（返回键、最近任务、手势滑动等）
 *
 * 启用方式：用户在「设置 → 无障碍」中开启本服务，或点击应用内的权限引导卡。
 *
 * 安全说明：
 * - 不读取窗口内容（canRetrieveWindowContent=false）
 * - 不上传任何数据
 * - 仅用于按键映射和系统手势模拟
 */
public class KeyMappingAccessibilityService extends AccessibilityService {

    private static final String TAG = "KeyMapAccessibility";

    /** 单例引用：供 KeyActionExecutor 调用手势模拟 API */
    @Nullable
    private static volatile KeyMappingAccessibilityService sInstance;

    private KeyTriggerDetector keyDetector;

    @Override
    public void onServiceConnected() {
        super.onServiceConnected();
        sInstance = this;
        keyDetector = new KeyTriggerDetector(this);
        // 显式请求按键过滤能力（1:1 对齐红旗方控 GlobalKeyAccessibilityService）
        // 某些设备上仅靠 XML 的 canRequestFilterKeyEvents 不生效，必须在代码中显式设置
        try {
            android.accessibilityservice.AccessibilityServiceInfo info = getServiceInfo();
            if (info != null) {
                info.flags |= android.accessibilityservice.AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS;
                setServiceInfo(info);
            }
        } catch (Exception e) {
            Log.w(TAG, "设置 FLAG_REQUEST_FILTER_KEY_EVENTS 失败", e);
        }
        Log.i(TAG, "无障碍服务已连接，按键映射全局生效");
        // 兜底：开机自启调度（三重保险之一，防 BootReceiver 未触发）
        AppAutoStartManager.scheduleFromBoot(this);
    }

    @Override
    public boolean onKeyEvent(KeyEvent event) {
        if (keyDetector == null) return false;
        int action = event.getAction();
        if (action == KeyEvent.ACTION_DOWN) {
            if (keyDetector.onKeyDown(event.getKeyCode(), event)) {
                return true;
            }
        } else if (action == KeyEvent.ACTION_UP) {
            keyDetector.onKeyUp(event.getKeyCode(), event);
            // 不消费 ACTION_UP，让其他系统组件也能收到（如音量键调音）
            // 仅当 KeyMapping 命中 ACTION_DOWN 时返回 true
        }
        return false;
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        // 不需要处理无障碍事件
    }

    @Override
    public void onInterrupt() {
        // 不处理
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        sInstance = null;
        if (keyDetector != null) {
            keyDetector.cleanup();
            keyDetector = null;
        }
        Log.i(TAG, "无障碍服务已断开");
    }

    // ============ 手势模拟 API（供 KeyActionExecutor 调用） ============

    /** 模拟返回键 */
    public boolean performGlobalBack() {
        try {
            return performGlobalAction(GLOBAL_ACTION_BACK);
        } catch (Exception e) {
            Log.e(TAG, "performGlobalBack failed", e);
            return false;
        }
    }

    /** 模拟最近任务键 */
    public boolean performRecentTasks() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                return performGlobalAction(GLOBAL_ACTION_RECENTS);
            }
        } catch (Exception e) {
            Log.e(TAG, "performRecentTasks failed", e);
        }
        return false;
    }

    /** 模拟 Home 键 */
    public boolean performHome() {
        try {
            return performGlobalAction(GLOBAL_ACTION_HOME);
        } catch (Exception e) {
            Log.e(TAG, "performHome failed", e);
            return false;
        }
    }

    /** 打开通知栏 */
    public boolean expandNotifications() {
        try {
            return performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS);
        } catch (Exception e) {
            Log.e(TAG, "expandNotifications failed", e);
            return false;
        }
    }

    /** 打开快捷设置面板 */
    public boolean expandQuickSettings() {
        try {
            return performGlobalAction(GLOBAL_ACTION_QUICK_SETTINGS);
        } catch (Exception e) {
            Log.e(TAG, "expandQuickSettings failed", e);
            return false;
        }
    }

    /** 锁屏（Android 9+） */
    public boolean lockScreen() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                return performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN);
            }
        } catch (Exception e) {
            Log.e(TAG, "lockScreen failed", e);
        }
        return false;
    }

    /** 截屏（Android 11+） */
    public boolean takeScreenshot() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                AccessibilityService.TakeScreenshotCallback cb =
                        new AccessibilityService.TakeScreenshotCallback() {
                    @Override
                    public void onSuccess(
                            android.accessibilityservice.AccessibilityService.ScreenshotResult result) {
                        // 截屏成功，结果由系统自动回收，这里仅触发动作
                        if (result != null) {
                            try { result.getHardwareBuffer().close(); } catch (Exception ignored) {}
                        }
                    }
                    @Override
                    public void onFailure(int errorCode) {
                        Log.e(TAG, "takeScreenshot failed, code=" + errorCode);
                    }
                };
                takeScreenshot(0, getMainExecutor(), cb);
                return true;
            }
        } catch (Exception e) {
            Log.e(TAG, "takeScreenshot failed", e);
        }
        return false;
    }

    /**
     * 执行手势（点击/滑动）
     * @param path 手势路径
     * @param startTime 起始时间（毫秒，从当前算起）
     * @param duration 持续时间（毫秒）
     */
    public boolean dispatchGesture(Path path, long startTime, long duration) {
        try {
            GestureDescription.StrokeDescription stroke =
                    new GestureDescription.StrokeDescription(path, startTime, duration);
            GestureDescription.Builder builder = new GestureDescription.Builder();
            builder.addStroke(stroke);
            return dispatchGesture(builder.build(), null, null);
        } catch (Exception e) {
            Log.e(TAG, "dispatchGesture failed", e);
            return false;
        }
    }

    /** 获取单例（可能为 null，表示服务未开启） */
    @Nullable
    public static KeyMappingAccessibilityService getInstance() {
        return sInstance;
    }

    /** 服务是否已连接（即用户已在系统设置中开启） */
    public static boolean isConnected() {
        return sInstance != null;
    }
}
