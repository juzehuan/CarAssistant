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

import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.CompoundButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.carassistant.R;
import com.carassistant.service.MonitorService;
import com.carassistant.util.MonitorUtil;
import com.carassistant.util.PermissionUtil;

/**
 * 性能监控页：CPU/内存/温度/帧率/电量实时显示 + 悬浮监控开关
 */
public class MonitorActivity extends AppCompatActivity {

    private TextView tvCpu, tvMemory, tvTemp, tvFps, tvBattery, tvFreq;
    private TextView tvPeakCpu, tvPeakTemp;
    private CompoundButton swFloat;

    private Handler handler;
    private Runnable runnable;
    private float peakCpu = 0, peakTemp = 0;
    private float maxFreq = -1;  // 缓存最大频率，避免每次扫描 sysfs
    private boolean firstSample = true;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_monitor);

        findViewById(R.id.btn_back).setOnClickListener(v -> finish());
        tvCpu = findViewById(R.id.tv_cpu);
        tvMemory = findViewById(R.id.tv_memory);
        tvTemp = findViewById(R.id.tv_temp);
        tvFps = findViewById(R.id.tv_fps);
        tvBattery = findViewById(R.id.tv_battery);
        tvFreq = findViewById(R.id.tv_freq);
        tvPeakCpu = findViewById(R.id.tv_peak_cpu);
        tvPeakTemp = findViewById(R.id.tv_peak_temp);
        swFloat = findViewById(R.id.sw_float_monitor);

        swFloat.setChecked(MonitorService.isRunning());
        swFloat.setOnCheckedChangeListener((b, checked) -> {
            if (checked) ensureOverlayAndStart();
            else stopService(new Intent(this, MonitorService.class));
        });

        handler = new Handler(Looper.getMainLooper());
        runnable = new Runnable() {
            @Override
            public void run() {
                updateStats();
                handler.postDelayed(this, 2000);
            }
        };

        // 刷新率（静态值）
        float fps = MonitorUtil.getRefreshRate(this);
        if (fps > 0) tvFps.setText(fps + " Hz");
    }

    private void updateStats() {
        float cpu = MonitorUtil.getCpuUsage();
        float memory = MonitorUtil.getMemoryUsage(this);
        float temp = MonitorUtil.getCpuTemp();
        if (temp < 0) temp = MonitorUtil.getBatteryTemp(this);
        int battery = MonitorUtil.getBatteryLevel(this);
        float freq = MonitorUtil.getCpuFreq();
        // 最大频率只取一次
        if (maxFreq < 0 && freq >= 0) maxFreq = MonitorUtil.getCpuMaxFreq();

        tvCpu.setText(String.format("%.0f%%", cpu));
        tvMemory.setText(String.format("%.0f%%", memory));
        tvTemp.setText(temp >= 0 ? String.format("%.1f°C", temp) : "N/A");
        if (battery >= 0) tvBattery.setText(battery + "%");
        // 频率：有最大频率时显示"当前 / 最大"，否则只显示当前
        if (freq >= 0) {
            String freqText = maxFreq > 0
                    ? String.format("%.0f / %.0f MHz", freq, maxFreq)
                    : String.format("%.0f MHz", freq);
            tvFreq.setText(freqText);
        } else {
            tvFreq.setText("N/A");
        }

        // 峰值（首次采样跳过，因 /proc/stat 第一次返回 0）
        if (!firstSample) {
            if (cpu > peakCpu) {
                peakCpu = cpu;
                tvPeakCpu.setText(String.format("%.0f%%", peakCpu));
            }
            if (temp > peakTemp) {
                peakTemp = temp;
                tvPeakTemp.setText(String.format("%.1f°C", peakTemp));
            }
        }
        firstSample = false;
    }

    private void ensureOverlayAndStart() {
        if (PermissionUtil.canDrawOverlays(this)) {
            Intent intent = new Intent(this, MonitorService.class);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent);
            } else {
                startService(intent);
            }
            Toast.makeText(this, R.string.monitor_float_on, Toast.LENGTH_SHORT).show();
        } else {
            new AlertDialog.Builder(this)
                    .setTitle(R.string.float_permission_title)
                    .setMessage(R.string.float_permission_msg)
                    .setPositiveButton(R.string.float_grant, (d, w) -> {
                        PermissionUtil.requestOverlayPermission(this, 0x101);
                        swFloat.setChecked(false);
                    })
                    .setNegativeButton(R.string.float_cancel, (d, w) -> swFloat.setChecked(false))
                    .setCancelable(false)
                    .show();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        firstSample = true;
        MonitorUtil.resetCpuBaseline();
        handler.post(runnable);
        if (swFloat != null) swFloat.setChecked(MonitorService.isRunning());
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (handler != null && runnable != null) handler.removeCallbacks(runnable);
    }
}
