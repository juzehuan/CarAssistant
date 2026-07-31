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

package com.carassistant.ui;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.carassistant.R;

/**
 * 通用确认弹窗：图标 + 标题 + 消息 + 取消/确认按钮
 * 替代默认 AlertDialog，提供更精致的视觉效果。
 *
 * 容错策略：自定义 Dialog 失败时自动回退到原生 AlertDialog，确保功能可用。
 */
public class ConfirmDialog {

    public static final int TYPE_DANGER = 0;   // 红色图标（卸载/删除）
    public static final int TYPE_PRIMARY = 1;  // 蓝色图标（安装/信息）
    public static final int TYPE_SUCCESS = 2;  // 绿色图标（成功/确认）
    public static final int TYPE_WARN = 3;     // 橙色图标（警告/清理）

    public interface OnConfirmListener {
        void onConfirm();
    }

    /**
     * 显示确认弹窗
     * @param context 上下文（必须是 Activity）
     * @param iconRes 图标资源（如 R.drawable.ic_panel_close）
     * @param type 图标颜色类型 TYPE_DANGER / TYPE_PRIMARY / TYPE_SUCCESS / TYPE_WARN
     * @param title 标题文本
     * @param message 消息文本
     * @param confirmText 确认按钮文本
     * @param listener 确认回调
     * @return Dialog 实例
     */
    public static Dialog show(Context context, int iconRes, int type,
                              String title, String message,
                              String confirmText, OnConfirmListener listener) {
        // 优先使用自定义 Dialog
        try {
            return showCustomDialog(context, iconRes, type, title, message, confirmText, listener);
        } catch (Exception e) {
            android.util.Log.e("ConfirmDialog", "custom dialog failed, fallback to AlertDialog", e);
            return showAlertDialog(context, title, message, confirmText, listener);
        }
    }

    private static Dialog showCustomDialog(Context context, int iconRes, int type,
                                           String title, String message,
                                           String confirmText, OnConfirmListener listener) {
        Dialog dialog = new Dialog(context);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog_confirm);
        dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        dialog.getWindow().setLayout(
                (int) (getScreenWidth(context) * 0.82),
                WindowManager.LayoutParams.WRAP_CONTENT);
        dialog.setCanceledOnTouchOutside(true);

        ImageView ivIconBg = dialog.findViewById(R.id.iv_dialog_icon_bg);
        ImageView ivIcon = dialog.findViewById(R.id.iv_dialog_icon);
        TextView tvTitle = dialog.findViewById(R.id.tv_dialog_title);
        TextView tvMessage = dialog.findViewById(R.id.tv_dialog_message);
        Button btnCancel = dialog.findViewById(R.id.btn_dialog_cancel);
        Button btnConfirm = dialog.findViewById(R.id.btn_dialog_confirm);

        // 图标颜色背景
        int bgRes;
        switch (type) {
            case TYPE_PRIMARY:  bgRes = R.drawable.bg_dialog_icon_primary; break;
            case TYPE_SUCCESS:  bgRes = R.drawable.bg_dialog_icon_success; break;
            case TYPE_WARN:     bgRes = R.drawable.bg_dialog_icon_warn; break;
            default:            bgRes = R.drawable.bg_dialog_icon_danger; break;
        }
        if (ivIconBg != null) ivIconBg.setImageResource(bgRes);
        if (ivIcon != null) ivIcon.setImageResource(iconRes);

        if (tvTitle != null) tvTitle.setText(title);
        if (tvMessage != null) tvMessage.setText(message);
        if (btnConfirm != null) btnConfirm.setText(confirmText);

        if (btnCancel != null) {
            btnCancel.setOnClickListener(v -> dialog.dismiss());
        }
        if (btnConfirm != null) {
            btnConfirm.setOnClickListener(v -> {
                dialog.dismiss();
                if (listener != null) {
                    try {
                        listener.onConfirm();
                    } catch (Exception e) {
                        android.util.Log.e("ConfirmDialog", "onConfirm failed", e);
                    }
                }
            });
        }

        dialog.show();
        return dialog;
    }

    /** 备用方案：使用原生 AlertDialog（更兼容） */
    private static Dialog showAlertDialog(Context context, String title, String message,
                                          String confirmText, OnConfirmListener listener) {
        AlertDialog.Builder b = new AlertDialog.Builder(context);
        b.setTitle(title);
        b.setMessage(message);
        b.setCancelable(true);
        b.setPositiveButton(confirmText, (d, w) -> {
            if (listener != null) {
                try {
                    listener.onConfirm();
                } catch (Exception e) {
                    android.util.Log.e("ConfirmDialog", "onConfirm(fallback) failed", e);
                }
            }
        });
        b.setNegativeButton(R.string.cancel, null);
        AlertDialog dialog = b.create();
        dialog.show();
        return dialog;
    }

    private static int getScreenWidth(Context context) {
        return context.getResources().getDisplayMetrics().widthPixels;
    }
}
