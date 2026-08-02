/*
 * Copyright (C) 2026 CarAssistant Project. All rights reserved.
 *
 * 版权所有 (C) 2026 车机助手项目
 * 保留所有权利
 *
 * 本源代码受著作权法保护，未经著作权人书面许可，不得以任何形式复制、修改、
 * 分发、出售或逆向工程。违反者将承担法律责任。
 */

package com.carassistant.service;

import android.inputmethodservice.InputMethodService;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;

import com.carassistant.util.KeyMappingUtil;

import java.util.HashMap;
import java.util.Map;

/**
 * 按键映射输入法（IME）。
 *
 * 用途：全局捕获「模拟轴事件」（手柄/方向盘的 MotionEvent，如 AXIS_HAT_X/Y、
 * AXIS_X/Y/Z、AXIS_THROTTLE/BRAKE/GAS、AXIS_LTRIGGER/RTRIGGER 及通用轴等），
 * 并将其转换为合成键码后派发到按键映射检测器，从而让模拟轴也能作为可映射的触发源。
 *
 * 设计要点：
 * - 本输入法无软键盘 UI（onCreateInputView 返回 null），仅用于事件捕获，不干扰文字输入。
 * - 数字按键（KeyEvent）仍由无障碍服务处理，此处不拦截，避免重复。
 * - 仅在「录制模式」时将轴事件转发给录制对话框；否则转发给无障碍服务内的全局检测器。
 * - 轴事件采用边缘检测（方向变化才触发），避免长按时持续重复触发。
 *
 * 注意：用户需在系统「语言与输入法 → 当前输入法」中启用本输入法，模拟轴捕获才会生效。
 */
public class KeyMappingInputMethod extends InputMethodService {

    /** 录制模式下，将捕获到的轴合成键码回调给录制对话框 */
    public interface AxisCaptureListener {
        void onAxisCaptured(int syntheticKeyCode);
    }

    private static volatile AxisCaptureListener sCaptureListener;

    public static void setAxisCaptureListener(AxisCaptureListener l) {
        sCaptureListener = l;
    }

    /** 记录每个轴的上一次方向，用于边缘检测 */
    private final Map<Integer, Integer> lastAxisDir = new HashMap<>();

    @Override
    public View onCreateInputView() {
        // 无软键盘 UI，仅用于捕获轴事件
        return null;
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        // 数字按键交由无障碍服务处理，这里直接放行
        return false;
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        return false;
    }

    @Override
    public boolean onGenericMotionEvent(MotionEvent event) {
        if (event == null) return false;
        int source = event.getSource();
        boolean fromGamepad = (source & InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK
                || (source & InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD
                || (source & InputDevice.SOURCE_DPAD) == InputDevice.SOURCE_DPAD;
        if (fromGamepad) {
            int code = detectAxisCode(event);
            if (code != 0) {
                if (KeyMappingAccessibilityService.isCaptureMode()) {
                    AxisCaptureListener l = sCaptureListener;
                    if (l != null) l.onAxisCaptured(code);
                } else {
                    KeyMappingAccessibilityService.feedAxisEvent(code);
                }
            }
        }
        return false; // 不消费事件，继续传递给应用
    }

    /**
     * 从 MotionEvent 中检测发生方向变化的轴，并返回对应的合成键码。
     * 边缘检测：仅当某轴方向相对上一次发生变化（且非 0）才返回该合成键码。
     */
    private int detectAxisCode(MotionEvent event) {
        // 固定轴列表（含通用轴 32..47，即 AXIS_GENERIC_1..16）
        int[] axes = {
                MotionEvent.AXIS_HAT_X, MotionEvent.AXIS_HAT_Y,
                MotionEvent.AXIS_X, MotionEvent.AXIS_Y, MotionEvent.AXIS_Z,
                MotionEvent.AXIS_RX, MotionEvent.AXIS_RY, MotionEvent.AXIS_RZ,
                MotionEvent.AXIS_THROTTLE, MotionEvent.AXIS_BRAKE,
                MotionEvent.AXIS_GAS, MotionEvent.AXIS_LTRIGGER, MotionEvent.AXIS_RTRIGGER
        };
        int[] allAxes = new int[axes.length + 16];
        System.arraycopy(axes, 0, allAxes, 0, axes.length);
        for (int i = 0; i < 16; i++) {
            allAxes[axes.length + i] = 32 + i; // AXIS_GENERIC_1 = 32
        }

        for (int axis : allAxes) {
            float v = event.getAxisValue(axis);
            int dir;
            if (v > 0.5f) dir = 1;
            else if (v < -0.5f) dir = -1;
            else dir = 0;
            Integer last = lastAxisDir.get(axis);
            if (last == null) last = 0;
            if (dir != 0 && dir != last) {
                lastAxisDir.put(axis, dir);
                return KeyMappingUtil.encodeAxisKey(axis, dir > 0);
            } else if (dir == 0 && last != 0) {
                lastAxisDir.put(axis, 0);
            }
        }
        return 0;
    }
}
