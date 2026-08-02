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

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.provider.Settings;
import android.view.KeyEvent;
import android.view.inputmethod.InputMethodInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.carassistant.R;
import com.carassistant.service.KeyMappingAccessibilityService;
import com.carassistant.service.KeyMappingInputMethod;
import com.carassistant.util.KeyMappingUtil;

import java.util.ArrayList;
import java.util.List;

/**
 * 按键录制对话框（参考开源 Key Mapper 的交互）。
 *
 * 支持四种录制模式：
 * - 单键/组合：默认模式，按下物理按键（或同时按两键形成组合键）
 * - 序列：依次按下若干键组成序列（如 音量+ → 音量- → 播放）
 * - 模拟轴：通过已启用的「车机助手按键映射输入法」捕获手柄/方向盘的模拟轴事件
 * - 手动输入：直接输入 keyCode
 */
public class KeyCaptureDialog extends AlertDialog {

    private static final int MODE_SINGLE_COMBO = 0;
    private static final int MODE_SEQUENCE = 1;
    private static final int MODE_AXIS = 2;

    private final Activity activity;
    private final KeyCaptureListener listener;
    private final boolean searchKeyCode;

    private int mode = MODE_SINGLE_COMBO;
    private int comboKey1 = -1;   // 组合键第一键
    private int comboKey2 = -1;   // 组合键第二键
    private int singleKey = -1;   // 已捕获的单键
    private final List<Integer> seqList = new ArrayList<>();
    private int axisKeyCode = 0;  // 已捕获的模拟轴合成键码

    private TextView tvMsg;
    private Button btnSingle, btnSeq, btnAxis;

    public KeyCaptureDialog(Activity activity, KeyCaptureListener listener, boolean searchKeyCode) {
        super(activity);
        this.activity = activity;
        this.listener = listener;
        this.searchKeyCode = searchKeyCode;

        // 进入录制模式：让无障碍服务转发按键但不执行
        KeyMappingAccessibilityService.setCaptureMode(true);

        // 自定义内容必须在 show() 之前设置（AlertDialog 内容在 show() 内部的 onCreate 阶段构建，
        // 若在 onStart() 里 setView/setButton 则不会刷新，仅剩遮罩）
        tvMsg = new TextView(activity);
        tvMsg.setPadding(40, 24, 40, 24);
        tvMsg.setTextSize(15);

        btnSingle = new Button(activity);
        btnSingle.setText(R.string.keymap_btn_single_combo);
        btnAxis = new Button(activity);
        btnAxis.setText(R.string.keymap_btn_axis);
        btnSeq = new Button(activity);
        btnSeq.setText(R.string.keymap_btn_sequence);

        LinearLayout modeRow = new LinearLayout(activity);
        modeRow.setOrientation(LinearLayout.HORIZONTAL);
        modeRow.setPadding(40, 8, 40, 8);
        modeRow.addView(btnSingle);
        modeRow.addView(btnSeq);
        modeRow.addView(btnAxis);

        LinearLayout root = new LinearLayout(activity);
        root.setOrientation(LinearLayout.VERTICAL);
        root.addView(tvMsg);
        root.addView(modeRow);

        ScrollView scroll = new ScrollView(activity);
        scroll.addView(root);

        setView(scroll);
        setButton(BUTTON_POSITIVE, activity.getString(android.R.string.ok), (d, which) -> onConfirm());
        setButton(BUTTON_NEUTRAL, "手动输入", (d, which) -> showManualInput());
        setButton(BUTTON_NEGATIVE, "取消", (d, which) -> {/* no-op */});

        btnSingle.setOnClickListener(v -> setMode(MODE_SINGLE_COMBO));
        btnSeq.setOnClickListener(v -> setMode(MODE_SEQUENCE));
        btnAxis.setOnClickListener(v -> {
            if (!isKeyMapperImeEnabled()) {
                Toast.makeText(activity, R.string.keymap_ime_tip, Toast.LENGTH_LONG).show();
                try {
                    activity.startActivity(new Intent(Settings.ACTION_INPUT_METHOD_SETTINGS));
                } catch (Exception ignored) {}
            }
            setMode(MODE_AXIS);
        });

        // 关闭时退出录制模式并清理轴监听（覆盖确定/取消/手动输入/外部返回键所有关闭路径）
        setOnDismissListener(d -> {
            KeyMappingAccessibilityService.setCaptureMode(false);
            KeyMappingInputMethod.setAxisCaptureListener(null);
        });

        setMode(MODE_SINGLE_COMBO);
    }

    private void setMode(int newMode) {
        this.mode = newMode;
        comboKey1 = -1;
        comboKey2 = -1;
        singleKey = -1;
        seqList.clear();
        axisKeyCode = 0;
        if (newMode == MODE_AXIS) {
            // 注册轴捕获监听：录制模式下由输入法把轴事件转发到这里
            KeyMappingInputMethod.setAxisCaptureListener(code -> {
                axisKeyCode = code;
                updateMsg();
            });
        } else {
            KeyMappingInputMethod.setAxisCaptureListener(null);
        }
        updateMsg();
        updateButtonStates();
    }

    private void updateButtonStates() {
        btnSingle.setAlpha(mode == MODE_SINGLE_COMBO ? 1f : 0.5f);
        btnSeq.setAlpha(mode == MODE_SEQUENCE ? 1f : 0.5f);
        btnAxis.setAlpha(mode == MODE_AXIS ? 1f : 0.5f);
    }

    private void updateMsg() {
        switch (mode) {
            case MODE_SEQUENCE:
                tvMsg.setText(R.string.keymap_sequence_hint + "\n\n" + currentSequenceText());
                break;
            case MODE_AXIS:
                tvMsg.setText(R.string.keymap_axis_hint + "\n\n" + currentAxisText());
                break;
            default:
                tvMsg.setText("请按下物理按键（方向盘键/音量键等）\n"
                        + "同时按住两键可录制组合键。\n\n" + currentSingleText());
        }
    }

    private String currentSingleText() {
        if (comboKey1 != -1 && comboKey2 != -1) {
            return "已捕获组合键：" + KeyMappingUtil.getKeyLabel(comboKey1) + " + "
                    + KeyMappingUtil.getKeyLabel(comboKey2);
        } else if (singleKey != -1) {
            return "已捕获按键：" + KeyMappingUtil.getKeyLabel(singleKey);
        }
        return "（尚未捕获）";
    }

    private String currentSequenceText() {
        if (seqList.isEmpty()) return "（尚未捕获）";
        StringBuilder sb = new StringBuilder("当前序列：");
        for (int i = 0; i < seqList.size(); i++) {
            if (i > 0) sb.append(" → ");
            sb.append(KeyMappingUtil.getKeyLabel(seqList.get(i)));
        }
        return sb.toString();
    }

    private String currentAxisText() {
        return axisKeyCode != 0
                ? "已捕获：" + KeyMappingUtil.getAxisLabel(axisKeyCode)
                : "（尚未捕获，请拨动手柄/方向盘模拟轴）";
    }

    @Override
    public boolean dispatchKeyEvent(android.view.KeyEvent event) {
        if (mode == MODE_AXIS) {
            // 模拟轴模式下忽略数字按键（轴事件由输入法捕获）
            return false;
        }
        if (event.getAction() == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0) {
            int keyCode = event.getKeyCode();
            if (keyCode == KeyEvent.KEYCODE_BACK || keyCode == KeyEvent.KEYCODE_HOME
                    || keyCode == KeyEvent.KEYCODE_APP_SWITCH) {
                return false;
            }
            if (mode == MODE_SEQUENCE) {
                // 序列：依次记录按键（避免与上一键重复，防止抖动）
                if (seqList.isEmpty() || seqList.get(seqList.size() - 1) != keyCode) {
                    seqList.add(keyCode);
                    updateMsg();
                }
                return true;
            }
            // 单键/组合模式
            if (comboKey1 == -1) {
                comboKey1 = keyCode;
                singleKey = keyCode;
                updateMsg();
            } else if (comboKey2 == -1 && keyCode != comboKey1) {
                comboKey2 = keyCode;
                updateMsg();
                // 组合键已捕获，直接完成
                listener.onComboCaptured(comboKey1, comboKey2);
                dismiss();
            } else {
                // 已捕获单键，再次按下则更新单键
                singleKey = keyCode;
                comboKey1 = keyCode;
                comboKey2 = -1;
                updateMsg();
            }
            return true;
        }
        return super.dispatchKeyEvent(event);
    }

    private void onConfirm() {
        if (mode == MODE_SEQUENCE) {
            if (seqList.size() >= 2) {
                int[] keys = new int[seqList.size()];
                for (int i = 0; i < seqList.size(); i++) keys[i] = seqList.get(i);
                listener.onSequenceCaptured(keys);
                dismiss();
            } else {
                Toast.makeText(activity, "序列至少需要 2 个键", Toast.LENGTH_SHORT).show();
            }
        } else if (mode == MODE_AXIS) {
            if (axisKeyCode != 0) {
                listener.onAxisCaptured(axisKeyCode);
                dismiss();
            } else {
                Toast.makeText(activity, "请先拨动模拟轴", Toast.LENGTH_SHORT).show();
            }
        } else {
            if (comboKey1 != -1 && comboKey2 != -1) {
                listener.onComboCaptured(comboKey1, comboKey2);
                dismiss();
            } else if (singleKey != -1) {
                listener.onSingleKeyCaptured(singleKey);
                dismiss();
            } else {
                Toast.makeText(activity, "请先按下按键", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void showManualInput() {
        EditText et = new EditText(activity);
        et.setHint("例如 24 表示 KEYCODE_VOLUME_UP");
        et.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        new AlertDialog.Builder(activity)
                .setTitle("手动输入 keyCode")
                .setView(et)
                .setPositiveButton(android.R.string.ok, (d, w) -> {
                    try {
                        int code = Integer.parseInt(et.getText().toString().trim());
                        listener.onSingleKeyCaptured(code);
                        dismiss();
                    } catch (Exception e) {
                        Toast.makeText(activity, "请输入有效数字", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private boolean isKeyMapperImeEnabled() {
        try {
            InputMethodManager imm = (InputMethodManager) activity.getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm == null) return false;
            String ourId = activity.getPackageName() + "/.service.KeyMappingInputMethod";
            List<InputMethodInfo> list = imm.getEnabledInputMethodList();
            for (InputMethodInfo info : list) {
                if (info.getId().equals(ourId)) return true;
            }
        } catch (Exception ignored) {}
        return false;
    }
}
