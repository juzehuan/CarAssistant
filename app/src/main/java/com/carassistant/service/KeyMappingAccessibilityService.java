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

    /**
     * 按键录制模式标志位。
     * 当 KeyMappingActivity 的 KeyCaptureDialog 显示时置为 true，
     * 本服务不消费任何按键事件，让按键透传到录制对话框。
     * 这样用户为已配置单击的按键再录制双击/长按时，按键不会被已有映射拦截。
     */
    private static volatile boolean sCaptureMode = false;

    /** 进入/退出按键录制模式（由 KeyCaptureDialog 调用） */
    public static void setCaptureMode(boolean enabled) {
        sCaptureMode = enabled;
    }

    public static boolean isCaptureMode() {
        return sCaptureMode;
    }

    /**
     * 供输入法（KeyMappingInputMethod）转发模拟轴合成键码，接入全局触发检测。
     */
    public static void feedAxisEvent(int syntheticKeyCode) {
        if (sInstance != null && sInstance.keyDetector != null) {
            sInstance.keyDetector.onAxisTrigger(syntheticKeyCode);
        }
    }

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
        // 录制模式：不消费任何按键，让事件透传到 KeyCaptureDialog
        if (sCaptureMode) return false;
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

    /**
     * 模拟"清除全部最近任务"：非 Root 设备下最有效的内存释放方式。
     *
     * 适配策略（覆盖主流国产 ROM + 原生 Android）：
     * 1. 打开最近任务界面，等待 UI 渲染
     * 2. 点击右下角「清除全部」按钮（vivo/MIUI/EMUI/MagicUI 等国产 ROM 通用位置）
     * 3. 回退：多次向上滑动清除卡片（原生 Android / Pixel 系列）
     * 4. 按 Home 键退出
     *
     * 经实测：vivo 车机（BBK Launcher）的「清除全部」按钮位于屏幕 (W*0.62, H*0.90)，
     * 不接受向上滑动清除手势，必须点击按钮才有效。
     *
     * @param callback 完成回调（success=true 表示已执行清理动作）
     */
    public void cleanRecentTasks(final CleanCallback callback) {
        new Thread(() -> {
            try {
                // 1. 打开最近任务
                performGlobalAction(GLOBAL_ACTION_RECENTS);
                sleep(800);

                // 2. 获取屏幕尺寸，计算「清除全部」按钮坐标
                android.graphics.Point screen = getScreenSize();

                // 3. 点击右下角「清除全部」按钮（国产 ROM 通用位置）
                //    vivo BBK Launcher 实测：(W*0.62, H*0.90)
                //    MIUI/EMUI 略有差异，多点击几个候选位置提高命中率
                clickClearAllButton(screen);

                // 等待系统清除动画
                sleep(800);

                // 4. 回退方案：向上滑动清除（原生 Android / 未命中按钮时）
                //    仅点击 2 次，避免在已清空的界面上无效滑动
                for (int i = 0; i < 2; i++) {
                    swipeUpToClearTask(screen);
                    sleep(300);
                }

                // 5. 退回桌面
                performGlobalAction(GLOBAL_ACTION_HOME);
                sleep(300);
                performGlobalAction(GLOBAL_ACTION_HOME);

                if (callback != null) {
                    callback.onDone(true);
                }
            } catch (Exception e) {
                Log.e(TAG, "cleanRecentTasks failed", e);
                try { performGlobalAction(GLOBAL_ACTION_HOME); } catch (Exception ignored) {}
                if (callback != null) {
                    callback.onDone(false);
                }
            }
        }).start();
    }

    /**
     * 点击右下角「清除全部」按钮。
     * 适配多 ROM：
     * - vivo BBK Launcher：屏幕 (W*0.62, H*0.90)
     * - MIUI：屏幕 (W*0.50, H*0.92)
     * - EMUI/MagicUI：屏幕 (W*0.85, H*0.92)
     * - 原生 Android 12+：屏幕 (W*0.50, H*0.92)（底部居中 Clear all）
     *
     * 多点几次提高命中率，点击无效时由后续向上滑动兜底。
     */
    private void clickClearAllButton(android.graphics.Point screen) {
        if (screen.x <= 0 || screen.y <= 0) {
            screen.x = 1920;
            screen.y = 1080;
        }
        // 候选坐标：覆盖主流 ROM 的「清除全部」按钮位置
        float[][] candidates = {
                {0.62f, 0.90f},  // vivo BBK Launcher（实测）
                {0.50f, 0.92f},  // 原生 Android 12+ / MIUI
                {0.85f, 0.92f},  // EMUI/MagicUI 右下角
                {0.50f, 0.85f},  // 部分 ROM 底部居中
        };
        for (float[] c : candidates) {
            int x = (int) (screen.x * c[0]);
            int y = (int) (screen.y * c[1]);
            tapAt(x, y);
            sleep(150);
        }
    }

    /** 在指定坐标执行点击手势 */
    private void tapAt(int x, int y) {
        try {
            Path path = new Path();
            path.moveTo(x, y);
            GestureDescription.StrokeDescription stroke =
                    new GestureDescription.StrokeDescription(path, 0, 60);
            GestureDescription.Builder builder = new GestureDescription.Builder();
            builder.addStroke(stroke);
            dispatchGesture(builder.build(), null, null);
        } catch (Exception e) {
            Log.w(TAG, "tapAt failed", e);
        }
    }

    /** 获取屏幕真实尺寸 */
    private android.graphics.Point getScreenSize() {
        android.graphics.Point screen = new android.graphics.Point();
        try {
            android.view.WindowManager wm = (android.view.WindowManager)
                    getSystemService(android.content.Context.WINDOW_SERVICE);
            if (wm != null) {
                android.view.Display display = wm.getDefaultDisplay();
                if (display != null) display.getRealSize(screen);
            }
        } catch (Exception e) {
            Log.w(TAG, "getScreenSize failed", e);
        }
        if (screen.x <= 0 || screen.y <= 0) {
            screen.x = 1920;
            screen.y = 1080;
        }
        return screen;
    }

    /** 向上快速滑动，清除当前最近任务卡片（原生 Android 兜底） */
    private void swipeUpToClearTask(android.graphics.Point screen) {
        try {
            if (screen.x <= 0 || screen.y <= 0) {
                screen.x = 1920;
                screen.y = 1080;
            }
            int startX = screen.x / 2;
            int startY = (int) (screen.y * 0.75);
            int endY = (int) (screen.y * 0.10);
            Path path = new Path();
            path.moveTo(startX, startY);
            path.lineTo(startX, endY);
            GestureDescription.StrokeDescription stroke =
                    new GestureDescription.StrokeDescription(path, 0, 250);
            GestureDescription.Builder builder = new GestureDescription.Builder();
            builder.addStroke(stroke);
            dispatchGesture(builder.build(), null, null);
        } catch (Exception e) {
            Log.w(TAG, "swipeUpToClearTask failed", e);
        }
    }

    /** 清理回调 */
    public interface CleanCallback {
        void onDone(boolean success);
    }

    private static void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException ignored) {}
    }
}
