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

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import com.carassistant.R;
import com.carassistant.util.MonitorUtil;

/**
 * 性能监控悬浮窗服务：在桌面/任意应用上方显示 CPU/内存/温度
 */
public class MonitorService extends Service {

    private static final String CHANNEL_ID = "monitor_channel";
    private static final int NOTI_ID = 0x200;
    private static final int OVERHEAT_THRESHOLD = 55;  // °C

    private WindowManager windowManager;
    private View floatView;
    private WindowManager.LayoutParams params;
    private Handler handler;
    private Runnable runnable;

    private TextView tvCpu, tvMemory, tvTemp, tvBattery, tvFreq;
    private float peakCpu = 0, peakTemp = 0;
    private boolean overheatNotified = false;

    private static boolean running = false;

    public static boolean isRunning() { return running; }

    @Override
    public void onCreate() {
        super.onCreate();
        running = true;
        windowManager = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
        handler = new Handler(Looper.getMainLooper());
        createNotificationChannel();
        // 双重回退策略，避免 Android 14+ 因 type 不匹配导致崩溃
        boolean foregroundOk = false;
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(NOTI_ID, buildNotification(),
                        android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
            } else {
                startForeground(NOTI_ID, buildNotification());
            }
            foregroundOk = true;
        } catch (Exception e) {
            android.util.Log.e("MonitorService", "startForeground with type failed, retry without type", e);
            try {
                startForeground(NOTI_ID, buildNotification());
                foregroundOk = true;
            } catch (Exception e2) {
                android.util.Log.e("MonitorService", "startForeground fallback failed", e2);
            }
        }
        if (!foregroundOk) {
            running = false;
            stopSelf();
            return;
        }
        showFloatWindow();
    }

    private void createNotificationChannel() {
        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel ch = new NotificationChannel(CHANNEL_ID,
                    getString(R.string.monitor_title), NotificationManager.IMPORTANCE_LOW);
            ch.setDescription(getString(R.string.monitor_float));
            nm.createNotificationChannel(ch);
        }
    }

    private Notification buildNotification() {
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle(getString(R.string.monitor_float))
                .setContentText(getString(R.string.monitor_float_on))
                .setSmallIcon(R.drawable.ic_feature_monitor)
                .setOngoing(true)
                .build();
    }

    private int overlayType() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;
    }

    private void showFloatWindow() {
        floatView = LayoutInflater.from(this).inflate(R.layout.view_monitor_float, null, false);
        tvCpu = floatView.findViewById(R.id.tv_monitor_cpu);
        tvMemory = floatView.findViewById(R.id.tv_monitor_memory);
        tvTemp = floatView.findViewById(R.id.tv_monitor_temp);
        tvBattery = floatView.findViewById(R.id.tv_monitor_battery);
        tvFreq = floatView.findViewById(R.id.tv_monitor_freq);

        params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                overlayType(),
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.TOP | Gravity.START;
        params.x = 10;
        params.y = 100;

        // 拖动
        floatView.setOnTouchListener(new View.OnTouchListener() {
            int initialX, initialY;
            float touchX, touchY;
            boolean moved;
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        initialX = params.x;
                        initialY = params.y;
                        touchX = event.getRawX();
                        touchY = event.getRawY();
                        moved = false;
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        int dx = (int) (event.getRawX() - touchX);
                        int dy = (int) (event.getRawY() - touchY);
                        if (Math.abs(dx) > 4 || Math.abs(dy) > 4) moved = true;
                        params.x = initialX + dx;
                        params.y = initialY + dy;
                        windowManager.updateViewLayout(floatView, params);
                        return true;
                }
                return false;
            }
        });

        try {
            windowManager.addView(floatView, params);
        } catch (Exception e) {
            Toast.makeText(this, "悬浮窗权限不足", Toast.LENGTH_SHORT).show();
            stopSelf();
            return;
        }

        runnable = new Runnable() {
            @Override
            public void run() {
                updateStats();
                handler.postDelayed(this, 2000);
            }
        };
        handler.post(runnable);
    }

    private void updateStats() {
        float cpu = MonitorUtil.getCpuUsage();
        float memory = MonitorUtil.getMemoryUsage(this);
        float temp = MonitorUtil.getCpuTemp();
        if (temp < 0) temp = MonitorUtil.getBatteryTemp(this);
        int battery = MonitorUtil.getBatteryLevel(this);
        float freq = MonitorUtil.getCpuFreq();

        tvCpu.setText(String.format("CPU %.0f%%", cpu));
        tvMemory.setText(String.format("MEM %.0f%%", memory));
        tvTemp.setText(temp >= 0 ? String.format("%.1f°C", temp) : "温度 N/A");
        tvBattery.setText(battery >= 0 ? battery + "%" : "");
        if (tvFreq != null) {
            tvFreq.setText(freq >= 0 ? String.format("FREQ %.0f MHz", freq) : "FREQ N/A");
        }

        // 峰值
        if (cpu > peakCpu) peakCpu = cpu;
        if (temp > peakTemp) peakTemp = temp;

        // 过热告警
        if (temp >= OVERHEAT_THRESHOLD && !overheatNotified) {
            overheatNotified = true;
            Toast.makeText(this, getString(R.string.monitor_overheat_msg, temp), Toast.LENGTH_LONG).show();
        } else if (temp < OVERHEAT_THRESHOLD - 5) {
            overheatNotified = false;
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        running = false;
        if (handler != null && runnable != null) handler.removeCallbacks(runnable);
        if (floatView != null) {
            try { windowManager.removeView(floatView); } catch (Exception ignored) {}
            floatView = null;
        }
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) { return null; }
}
