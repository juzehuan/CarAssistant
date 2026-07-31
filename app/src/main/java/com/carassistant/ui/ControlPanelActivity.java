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

import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.carassistant.R;
import com.carassistant.util.ControlPanelUtil;

/**
 * 车机控制面板：音量/亮度/蓝牙/WiFi/热点/夜间模式
 *
 * 用户反馈改进：每次操作均给出 Toast 提示与按钮状态变化，避免"按了没反应"的感受。
 */
public class ControlPanelActivity extends AppCompatActivity {

    private static final int REQ_WRITE_SETTINGS = 0x201;

    private SeekBar sbVolume, sbBrightness;
    private TextView tvVolumeValue, tvBrightnessValue;
    private LinearLayout btnWifi, btnBluetooth, btnHotspot, btnDark, btnAirplane, btnLocation;
    private ImageView ivWifi, ivBluetooth, ivHotspot, ivDark;
    private TextView tvWifi, tvBluetooth, tvHotspot, tvDark;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_control_panel);

        findViewById(R.id.btn_back).setOnClickListener(v -> finish());

        sbVolume = findViewById(R.id.sb_volume);
        sbBrightness = findViewById(R.id.sb_brightness);
        tvVolumeValue = findViewById(R.id.tv_volume_value);
        tvBrightnessValue = findViewById(R.id.tv_brightness_value);

        btnWifi = findViewById(R.id.btn_wifi);
        btnBluetooth = findViewById(R.id.btn_bluetooth);
        btnHotspot = findViewById(R.id.btn_hotspot);
        btnDark = findViewById(R.id.btn_dark);
        btnAirplane = findViewById(R.id.btn_airplane);
        btnLocation = findViewById(R.id.btn_location);

        ivWifi = findViewById(R.id.iv_wifi);
        ivBluetooth = findViewById(R.id.iv_bluetooth);
        ivHotspot = findViewById(R.id.iv_hotspot);
        ivDark = findViewById(R.id.iv_dark);

        tvWifi = findViewById(R.id.tv_wifi);
        tvBluetooth = findViewById(R.id.tv_bluetooth);
        tvHotspot = findViewById(R.id.tv_hotspot);
        tvDark = findViewById(R.id.tv_dark);

        setupVolume();
        setupBrightness();
        setupToggles();
    }

    private void setupVolume() {
        int max = ControlPanelUtil.getMaxVolume(this);
        int current = ControlPanelUtil.getCurrentVolume(this);
        sbVolume.setMax(max);
        sbVolume.setProgress(current);
        tvVolumeValue.setText(current + " / " + max);
        sbVolume.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar sb, int progress, boolean fromUser) {
                tvVolumeValue.setText(progress + " / " + max);
                if (fromUser) ControlPanelUtil.setVolume(ControlPanelActivity.this, progress);
            }
            @Override public void onStartTrackingTouch(SeekBar sb) {}
            @Override public void onStopTrackingTouch(SeekBar sb) {
                Toast.makeText(ControlPanelActivity.this,
                        R.string.control_volume_changed, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void setupBrightness() {
        int max = ControlPanelUtil.getMaxBrightness();
        int current = ControlPanelUtil.getBrightness(this);
        sbBrightness.setMax(max);
        sbBrightness.setProgress(current);
        tvBrightnessValue.setText(current + " / " + max);
        sbBrightness.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar sb, int progress, boolean fromUser) {
                tvBrightnessValue.setText(progress + " / " + max);
            }
            @Override public void onStartTrackingTouch(SeekBar sb) {}
            @Override public void onStopTrackingTouch(SeekBar sb) {
                if (!ControlPanelUtil.canWriteSettings(ControlPanelActivity.this)) {
                    new androidx.appcompat.app.AlertDialog.Builder(ControlPanelActivity.this)
                            .setTitle(R.string.permission_grant)
                            .setMessage(R.string.control_brightness_permission)
                            .setPositiveButton(R.string.ok, (d, w) ->
                                    ControlPanelUtil.requestWriteSettings(ControlPanelActivity.this, REQ_WRITE_SETTINGS))
                            .setNegativeButton(R.string.cancel, null)
                            .show();
                    return;
                }
                ControlPanelUtil.setBrightness(ControlPanelActivity.this, sb.getProgress());
                Toast.makeText(ControlPanelActivity.this,
                        R.string.control_brightness_changed, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void setupToggles() {
        btnWifi.setOnClickListener(v -> {
            boolean wasOn = ControlPanelUtil.isWifiEnabled(this);
            ControlPanelUtil.toggleWifi(this);
            // Android 10+ 会跳转到设置页
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                Toast.makeText(this, R.string.control_open_settings, Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this,
                        wasOn ? R.string.control_wifi_off : R.string.control_wifi_on,
                        Toast.LENGTH_SHORT).show();
            }
            btnWifi.postDelayed(this::refreshToggleStates, 800);
        });
        btnBluetooth.setOnClickListener(v -> {
            boolean wasOn = ControlPanelUtil.isBluetoothEnabled(this);
            ControlPanelUtil.toggleBluetooth(this);
            Toast.makeText(this,
                    wasOn ? R.string.control_bluetooth_off : R.string.control_bluetooth_on,
                    Toast.LENGTH_SHORT).show();
            btnBluetooth.postDelayed(this::refreshToggleStates, 1500);
        });
        btnHotspot.setOnClickListener(v -> {
            ControlPanelUtil.toggleHotspot(this);
            // 热点通常需要跳转系统设置（反射调用在大多数设备上失效）
            Toast.makeText(this, R.string.control_open_settings, Toast.LENGTH_SHORT).show();
            btnHotspot.postDelayed(this::refreshToggleStates, 1000);
        });
        btnDark.setOnClickListener(v -> {
            boolean wasOn = ControlPanelUtil.isDarkMode(this);
            ControlPanelUtil.toggleDarkMode(this);
            Toast.makeText(this,
                    wasOn ? R.string.control_dark_off : R.string.control_dark_on,
                    Toast.LENGTH_SHORT).show();
            btnDark.postDelayed(this::refreshToggleStates, 500);
        });
        btnAirplane.setOnClickListener(v -> {
            ControlPanelUtil.openSettings(this, android.provider.Settings.ACTION_AIRPLANE_MODE_SETTINGS);
            Toast.makeText(this, R.string.control_open_settings, Toast.LENGTH_SHORT).show();
        });
        btnLocation.setOnClickListener(v -> {
            ControlPanelUtil.openSettings(this, android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS);
            Toast.makeText(this, R.string.control_open_settings, Toast.LENGTH_SHORT).show();
        });

        refreshToggleStates();
    }

    private void refreshToggleStates() {
        try {
            boolean wifiOn = ControlPanelUtil.isWifiEnabled(this);
            applyToggleStyle(btnWifi, ivWifi, tvWifi, wifiOn, getString(R.string.control_wifi));
        } catch (Exception ignored) {}

        try {
            boolean btOn = ControlPanelUtil.isBluetoothEnabled(this);
            applyToggleStyle(btnBluetooth, ivBluetooth, tvBluetooth, btOn, getString(R.string.control_bluetooth));
        } catch (Exception ignored) {}

        try {
            boolean darkOn = ControlPanelUtil.isDarkMode(this);
            applyToggleStyle(btnDark, ivDark, tvDark, darkOn, getString(R.string.control_dark));
        } catch (Exception ignored) {}

        try {
            boolean hotOn = ControlPanelUtil.isHotspotEnabled(this);
            applyToggleStyle(btnHotspot, ivHotspot, tvHotspot, hotOn, getString(R.string.control_hotspot));
        } catch (Exception ignored) {}
    }

    /**
     * 应用开关样式：开启=主色背景+主色描边，关闭=灰底细描边
     * 同时刷新图标和文字颜色
     */
    private void applyToggleStyle(LinearLayout btn, ImageView iv, TextView tv, boolean on, String label) {
        int iconColor = on ? ContextCompat.getColor(this, R.color.brand_primary)
                           : ContextCompat.getColor(this, R.color.text_hint);
        int textColor = on ? ContextCompat.getColor(this, R.color.brand_primary)
                           : ContextCompat.getColor(this, R.color.text_secondary);
        iv.setColorFilter(iconColor);
        tv.setTextColor(textColor);
        tv.setText(label);
        // 切换背景：开启用 active，关闭用普通
        btn.setBackgroundResource(on ? R.drawable.bg_toggle_item_active
                                     : R.drawable.bg_toggle_item);
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshToggleStates();
    }
}
