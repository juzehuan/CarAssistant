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

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.carassistant.MainActivity;
import com.carassistant.R;
import com.carassistant.util.PermissionUtil;
import com.carassistant.util.PrefsUtil;

/**
 * 首次启动权限引导页
 *
 * 启动时机：
 * - MainActivity.onCreate 检查 {@link PrefsUtil#isPermissionGuideDone}
 * - 未完成时跳转到本页并 finish MainActivity
 *
 * 权限分类：
 * 1. 必需权限（运行时权限）：进入应用前必须授予
 *    - POST_NOTIFICATIONS（Android 13+）
 *    - BLUETOOTH_CONNECT（Android 12+）
 *    - CAMERA
 *    - READ/WRITE_EXTERNAL_STORAGE（Android 10 及以下）
 *
 * 2. 重要权限（特殊权限）：跳转系统设置，可跳过但提示影响
 *    - 悬浮窗 / 所有文件访问 / 使用情况访问 / 修改系统设置 / 通知访问
 *
 * 进入按钮策略：
 * - 必需权限全部授予时可点击进入应用
 * - 跳过特殊权限时显示影响提示对话框
 */
public class PermissionActivity extends AppCompatActivity {

    private static final int REQ_RUNTIME = 0x1001;

    // 视图：运行时权限状态徽章
    private TextView tvRuntimeStatus;
    // 视图：特殊权限状态徽章
    private TextView tvOverlayStatus, tvStorageStatus, tvUsageStatus;
    private TextView tvWriteSettingsStatus, tvListenerStatus;
    // 视图：行容器（点击跳转设置）
    private View rowOverlay, rowStorage, rowUsage, rowWriteSettings, rowListener;
    // 视图：底部按钮
    private Button btnRequestRuntime, btnEnterApp, btnSkipSpecial;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_permission);

        bindViews();
        setupListeners();
        refreshAllStatus();
    }

    private void bindViews() {
        tvRuntimeStatus = findViewById(R.id.tv_runtime_status);
        tvOverlayStatus = findViewById(R.id.tv_overlay_status);
        tvStorageStatus = findViewById(R.id.tv_storage_status);
        tvUsageStatus = findViewById(R.id.tv_usage_status);
        tvWriteSettingsStatus = findViewById(R.id.tv_write_settings_status);
        tvListenerStatus = findViewById(R.id.tv_listener_status);

        rowOverlay = findViewById(R.id.row_overlay);
        rowStorage = findViewById(R.id.row_storage);
        rowUsage = findViewById(R.id.row_usage);
        rowWriteSettings = findViewById(R.id.row_write_settings);
        rowListener = findViewById(R.id.row_listener);

        btnRequestRuntime = findViewById(R.id.btn_request_runtime);
        btnEnterApp = findViewById(R.id.btn_enter_app);
        btnSkipSpecial = findViewById(R.id.btn_skip_special);
    }

    private void setupListeners() {
        // 一键申请运行时权限
        btnRequestRuntime.setOnClickListener(v -> {
            PermissionUtil.requestAllRuntimePermissions(this, REQ_RUNTIME);
            btnRequestRuntime.setText(R.string.permission_runtime_requesting);
        });

        // 各特殊权限行点击跳转对应系统设置
        rowOverlay.setOnClickListener(v -> {
            PermissionUtil.requestOverlayPermission(this, 0x2001);
        });
        rowStorage.setOnClickListener(v -> {
            if (!PermissionUtil.hasStorageAccess(this)) {
                PermissionUtil.requestAllFilesAccess(this);
            }
        });
        rowUsage.setOnClickListener(v -> {
            if (!PermissionUtil.hasUsageStatsAccess(this)) {
                PermissionUtil.requestUsageStatsAccess(this);
            }
        });
        rowWriteSettings.setOnClickListener(v -> {
            if (!PermissionUtil.canWriteSettings(this)) {
                PermissionUtil.requestWriteSettings(this);
            }
        });
        rowListener.setOnClickListener(v -> {
            if (!PermissionUtil.isNotificationListenerEnabled(this)) {
                PermissionUtil.requestNotificationListenerAccess(this);
            }
        });

        // 进入应用
        btnEnterApp.setOnClickListener(v -> {
            if (!PermissionUtil.hasAllRuntimePermissions(this)) {
                Toast.makeText(this, R.string.permission_runtime_required_toast,
                        Toast.LENGTH_LONG).show();
                PermissionUtil.requestAllRuntimePermissions(this, REQ_RUNTIME);
                return;
            }
            enterApp();
        });

        // 跳过特殊权限
        btnSkipSpecial.setOnClickListener(v -> {
            if (!PermissionUtil.hasAllRuntimePermissions(this)) {
                Toast.makeText(this, R.string.permission_runtime_required_toast,
                        Toast.LENGTH_LONG).show();
                PermissionUtil.requestAllRuntimePermissions(this, REQ_RUNTIME);
                return;
            }
            new AlertDialog.Builder(this)
                    .setTitle(R.string.permission_skip_special)
                    .setMessage(R.string.permission_skip_confirm)
                    .setPositiveButton(R.string.permission_skip_yes,
                            (d, w) -> enterApp())
                    .setNegativeButton(R.string.permission_skip_no, null)
                    .show();
        });
    }

    /** 进入主界面，标记引导完成 */
    private void enterApp() {
        PrefsUtil.setPermissionGuideDone(this, true);
        startActivity(new Intent(this, MainActivity.class));
        finish();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshAllStatus();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_RUNTIME) {
            refreshAllStatus();
            // 恢复按钮文案
            btnRequestRuntime.setText(R.string.permission_action_grant_runtime);
        }
    }

    /** 刷新所有权限状态徽章与按钮可用性 */
    private void refreshAllStatus() {
        // 运行时权限
        boolean runtimeOk = PermissionUtil.hasAllRuntimePermissions(this);
        updateStatusBadge(tvRuntimeStatus, runtimeOk);
        btnRequestRuntime.setVisibility(runtimeOk ? View.GONE : View.VISIBLE);
        btnRequestRuntime.setText(runtimeOk
                ? R.string.permission_runtime_done
                : R.string.permission_action_grant_runtime);

        // 特殊权限
        updateStatusBadge(tvOverlayStatus, PermissionUtil.canDrawOverlays(this));
        updateStatusBadge(tvStorageStatus, PermissionUtil.hasStorageAccess(this));
        updateStatusBadge(tvUsageStatus, PermissionUtil.hasUsageStatsAccess(this));
        updateStatusBadge(tvWriteSettingsStatus, PermissionUtil.canWriteSettings(this));
        updateStatusBadge(tvListenerStatus, PermissionUtil.isNotificationListenerEnabled(this));

        // 进入按钮可用性
        btnEnterApp.setEnabled(runtimeOk);
        btnEnterApp.setAlpha(runtimeOk ? 1.0f : 0.5f);
    }

    /** 更新状态徽章：已授予显示绿色，未授予显示灰色 */
    private void updateStatusBadge(TextView tv, boolean granted) {
        if (granted) {
            tv.setText(R.string.permission_status_granted);
            tv.setBackgroundResource(R.drawable.bg_perm_status_granted);
            tv.setTextColor(ContextCompat.getColor(this, R.color.success));
        } else {
            tv.setText(R.string.permission_status_not_granted);
            tv.setBackgroundResource(R.drawable.bg_status_disabled);
            tv.setTextColor(ContextCompat.getColor(this, R.color.text_hint));
        }
    }
}
