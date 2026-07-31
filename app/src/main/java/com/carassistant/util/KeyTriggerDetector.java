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
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.KeyEvent;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * 按键触发检测器
 *
 * 在 Activity 的 onKeyDown / onKeyLongPress 中调用本类方法，
 * 自动识别单击 / 双击 / 长按 / 组合键，并派发对应的映射动作。
 *
 * 使用：
 *   Activity onKeyDown:
 *     return keyDetector.onKeyDown(keyCode, event);
 *   Activity onKeyLongPress:
 *     return keyDetector.onKeyLongPress(keyCode);
 */
public class KeyTriggerDetector {

    private final Context ctx;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    /** 每个按键的上次按下时间（用于双击检测） */
    private final Map<Integer, Long> lastTapTime = new HashMap<>();
    /** 每个按键的长按检测 Runnable */
    private final Map<Integer, Runnable> longPressRunnables = new HashMap<>();
    /** 当前按下的按键集合（用于组合键检测） */
    private final Set<Integer> pressedKeys = new HashSet<>();
    /** 双击回调待执行 Runnable（用于在第二次按下时取消第一次的延迟执行） */
    private final Map<Integer, Runnable> pendingTapRunnables = new HashMap<>();

    public KeyTriggerDetector(Context ctx) {
        this.ctx = ctx.getApplicationContext();
    }

    /**
     * 在 Activity.onKeyDown 中调用
     * @return true 表示已消费事件
     */
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (event.getRepeatCount() == 0) {
            // 首次按下
            pressedKeys.add(keyCode);

            // 1. 检查组合键映射（当前按下键 + 任意其他已按下键）
            for (int other : pressedKeys) {
                if (other != keyCode) {
                    KeyMappingUtil.KeyMapping combo = KeyMappingUtil.getComboMapping(ctx, keyCode, other);
                    if (combo != null && combo.enabled) {
                        // 命中组合键，取消当前键的单击/长按检测
                        cancelPending(keyCode);
                        KeyActionExecutor.execute(ctx, combo);
                        return true;
                    }
                }
            }

            // 2. 检查长按映射：若存在长按映射，启动延迟检测
            KeyMappingUtil.KeyMapping longPressMapping = KeyMappingUtil.getMapping(ctx, keyCode,
                    KeyMappingUtil.TRIGGER_LONG_PRESS);
            if (longPressMapping != null && longPressMapping.enabled) {
                Runnable r = () -> {
                    if (pressedKeys.contains(keyCode)) {
                        cancelPending(keyCode);
                        KeyActionExecutor.execute(ctx, longPressMapping);
                        // 标记已触发，避免松开时再触发单击
                        lastTapTime.put(keyCode, -1L);
                    }
                };
                longPressRunnables.put(keyCode, r);
                mainHandler.postDelayed(r, KeyMappingUtil.LONG_PRESS_TIMEOUT);
            }

            // 3. 检查双击映射：若存在双击映射，本次按下暂不触发单击
            KeyMappingUtil.KeyMapping doubleTapMapping = KeyMappingUtil.getMapping(ctx, keyCode,
                    KeyMappingUtil.TRIGGER_DOUBLE_TAP);
            if (doubleTapMapping != null && doubleTapMapping.enabled) {
                long now = SystemClock.uptimeMillis();
                Long last = lastTapTime.get(keyCode);
                if (last != null && last > 0 && now - last < KeyMappingUtil.DOUBLE_TAP_TIMEOUT) {
                    // 双击命中
                    cancelPending(keyCode);
                    lastTapTime.put(keyCode, 0L);
                    KeyActionExecutor.execute(ctx, doubleTapMapping);
                    return true;
                } else {
                    // 第一次按下：延迟触发单击（若双击未命中）
                    lastTapTime.put(keyCode, now);
                    KeyMappingUtil.KeyMapping tapMapping = KeyMappingUtil.getMapping(ctx, keyCode,
                            KeyMappingUtil.TRIGGER_TAP);
                    if (tapMapping != null && tapMapping.enabled) {
                        Runnable pending = () -> {
                            pendingTapRunnables.remove(keyCode);
                            KeyActionExecutor.execute(ctx, tapMapping);
                        };
                        pendingTapRunnables.put(keyCode, pending);
                        mainHandler.postDelayed(pending, KeyMappingUtil.DOUBLE_TAP_TIMEOUT);
                    }
                    return true; // 消费事件，等待后续判断
                }
            }

            // 4. 仅单击映射：直接触发
            KeyMappingUtil.KeyMapping tapMapping = KeyMappingUtil.getMapping(ctx, keyCode,
                    KeyMappingUtil.TRIGGER_TAP);
            if (tapMapping != null && tapMapping.enabled) {
                cancelPending(keyCode);
                KeyActionExecutor.execute(ctx, tapMapping);
                return true;
            }
        }
        return false;
    }

    /** 在 Activity.onKeyUp 中调用 */
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        pressedKeys.remove(keyCode);
        // 取消长按检测
        Runnable r = longPressRunnables.remove(keyCode);
        if (r != null) mainHandler.removeCallbacks(r);
        return false;
    }

    /** 取消某按键所有待执行动作 */
    private void cancelPending(int keyCode) {
        Runnable lr = longPressRunnables.remove(keyCode);
        if (lr != null) mainHandler.removeCallbacks(lr);
        Runnable pr = pendingTapRunnables.remove(keyCode);
        if (pr != null) mainHandler.removeCallbacks(pr);
    }

    /** 清理所有状态（Activity 销毁时调用） */
    public void cleanup() {
        for (Runnable r : longPressRunnables.values()) mainHandler.removeCallbacks(r);
        for (Runnable r : pendingTapRunnables.values()) mainHandler.removeCallbacks(r);
        longPressRunnables.clear();
        pendingTapRunnables.clear();
        lastTapTime.clear();
        pressedKeys.clear();
    }
}
