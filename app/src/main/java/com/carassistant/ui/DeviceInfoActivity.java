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

import android.os.AsyncTask;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.carassistant.R;
import com.carassistant.util.DeviceInfoUtil;

import java.lang.ref.WeakReference;

/**
 * 设备信息页：系统/硬件/电量信息 + 一键截屏
 */
public class DeviceInfoActivity extends AppCompatActivity {

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_device_info);

        findViewById(R.id.btn_back).setOnClickListener(v -> finish());
        findViewById(R.id.btn_screenshot).setOnClickListener(v -> {
            String path = DeviceInfoUtil.takeScreenshot(this);
            if (path != null) {
                Toast.makeText(this, getString(R.string.device_screenshot_success, path),
                        Toast.LENGTH_LONG).show();
            } else {
                Toast.makeText(this, R.string.device_screenshot_failed, Toast.LENGTH_SHORT).show();
            }
        });

        new LoadTask(this).execute();
    }

    private static class LoadTask extends AsyncTask<Void, Void, DeviceInfoUtil.DeviceInfo> {
        private final WeakReference<DeviceInfoActivity> ref;

        LoadTask(DeviceInfoActivity act) { this.ref = new WeakReference<>(act); }

        @Override
        protected DeviceInfoUtil.DeviceInfo doInBackground(Void... voids) {
            DeviceInfoActivity act = ref.get();
            if (act == null) return null;
            return DeviceInfoUtil.gather(act);
        }

        @Override
        protected void onPostExecute(DeviceInfoUtil.DeviceInfo info) {
            DeviceInfoActivity act = ref.get();
            if (act == null || info == null) return;
            act.bindRow(R.id.row_model, R.string.device_model, info.model);
            act.bindRow(R.id.row_brand, R.string.device_brand, info.brand + " / " + info.manufacturer);
            act.bindRow(R.id.row_android, R.string.device_android, info.androidVersion);
            act.bindRow(R.id.row_sdk, R.string.device_sdk, info.sdkVersion);
            act.bindRow(R.id.row_security, R.string.device_security_patch, info.securityPatch);
            act.bindRow(R.id.row_cpu, R.string.device_cpu, info.cpuAbi);
            act.bindRow(R.id.row_cores, R.string.device_cores, info.cpuCores + " 核");
            act.bindRow(R.id.row_memory, R.string.device_memory, info.memoryTotal);
            act.bindRow(R.id.row_storage, R.string.device_storage,
                    info.storageAvailable + " / " + info.storageTotal);
            act.bindRow(R.id.row_resolution, R.string.device_resolution, info.resolution);
            act.bindRow(R.id.row_dpi, R.string.device_dpi, info.density);
            act.bindRow(R.id.row_battery_level, R.string.device_battery_level, info.batteryLevel);
            act.bindRow(R.id.row_battery_status, R.string.device_battery_status, info.batteryStatus);
            act.bindRow(R.id.row_battery_temp, R.string.device_battery_temp, info.batteryTemp);
        }
    }

    private void bindRow(int rowId, int labelRes, String value) {
        View row = findViewById(rowId);
        if (row == null) return;
        TextView tvLabel = row.findViewById(R.id.tv_label);
        TextView tvValue = row.findViewById(R.id.tv_value);
        if (tvLabel != null) tvLabel.setText(labelRes);
        if (tvValue != null) tvValue.setText(value == null ? "unknown" : value);
    }
}
