/*
 * Copyright (C) 2026 CarAssistant Project. All rights reserved.
 *
 * 版权所有 (C) 2026 车机助手项目
 * 保留所有权利
 *
 * 本源代码受著作权法保护，未经著作权人书面许可，不得以任何形式复制、修改、
 * 分发、出售或逆向工程。违反者将承担法律责任。
 */

package com.carassistant.ui;

/**
 * 按键录制结果回调（顶层接口，避免与 KeyCaptureDialog 嵌套类型解析混淆）。
 */
public interface KeyCaptureListener {
    void onSingleKeyCaptured(int keyCode);
    void onComboCaptured(int key1, int key2);
    void onSequenceCaptured(int[] keys);
    void onAxisCaptured(int axisKeyCode);
}
