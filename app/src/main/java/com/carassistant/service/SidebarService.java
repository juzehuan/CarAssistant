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
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.provider.Settings;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.carassistant.R;
import com.carassistant.adapter.FloatAppAdapter;
import com.carassistant.util.PermissionUtil;
import com.carassistant.util.PrefsUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 侧边栏服务（替代悬浮球）
 *
 * 工作原理：
 * - 在屏幕左/右边缘各放置一条 8dp 宽的透明热区
 * - 用户从边缘向内滑动超过阈值时，从对应方向滑出垂直侧边栏面板
 * - 面板包含：系统快捷开关(WiFi/蓝牙/亮度/手电筒/旋转) + 快捷应用网格 + 导航按钮(返回/主页/最近任务/锁屏)
 * - 点击面板外遮罩或关闭按钮收起面板
 *
 * 兼容性：
 * - 前台服务 startForeground 双重回退（Android 8+/14+）
 * - 所有 inflate/updateViewLayout 均 try-catch 保护
 * - 边缘热区使用 FLAG_NOT_FOCUSABLE | FLAG_WATCH_OUTSIDE_TOUCH
 * - 不使用 ?attr 主题属性（Service 上下文兼容）
 */
public class SidebarService extends Service {

    public static final String ACTION_REFRESH = "com.carassistant.action.REFRESH_SIDEBAR";

    private static volatile boolean sRunning = false;

    private WindowManager windowManager;
    private View leftEdge, rightEdge;
    private View sidebarPanel, dimOverlay;
    private WindowManager.LayoutParams edgeLeftParams, edgeRightParams;
    private WindowManager.LayoutParams panelParams, overlayParams;

    private FloatAppAdapter panelAdapter;
    private RecyclerView rvApps;
    private TextView tvAppsEmpty;
    private ProgressBar pbLoading;

    private final ExecutorService ioPool = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    /** 滑入触发阈值（dp） */
    private static final float SWIPE_THRESHOLD_DP = 14f;
    /** 边缘热区宽度（dp） */
    private static final float EDGE_WIDTH_DP = 16f;

    @Override
    public void onCreate() {
        super.onCreate();
        try {
            windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
            startForegroundSafe();
            addEdges();
            registerRefreshReceiver();
            sRunning = true;
        } catch (Exception e) {
            android.util.Log.e("SidebarService", "onCreate failed", e);
            sRunning = false;
            stopSelf();
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        try {
            unregisterRefreshReceiver();
        } catch (Exception ignored) {}
        hidePanel();
        removeEdges();
        try { ioPool.shutdownNow(); } catch (Exception ignored) {}
        sRunning = false;
        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) { return null; }

    public static boolean isRunning() { return sRunning; }

    // ============ 前台服务 ============

    private void startForegroundSafe() {
        String channelId = "car_sidebar_channel";
        try {
            NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && nm != null) {
                NotificationChannel ch = new NotificationChannel(
                        channelId, "侧边栏服务", NotificationManager.IMPORTANCE_LOW);
                nm.createNotificationChannel(ch);
            }
            Notification n = new NotificationCompat.Builder(this, channelId)
                    .setContentTitle(getString(R.string.sidebar_running_title))
                    .setContentText(getString(R.string.sidebar_running_text))
                    .setSmallIcon(R.drawable.ic_float_logo)
                    .setOngoing(true)
                    .build();
            // 双重回退：Android 14+ 要求 foregroundServiceType，低版本忽略
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    startForeground(1, n, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
                } else {
                    startForeground(1, n);
                }
            } catch (Exception e) {
                android.util.Log.w("SidebarService", "startForeground(typed) failed, fallback", e);
                startForeground(1, n);
            }
        } catch (Exception e) {
            android.util.Log.e("SidebarService", "startForegroundSafe failed", e);
        }
    }

    // ============ 边缘热区 ============

    private int edgeType() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;
    }

    private int dp(float dp) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp,
                getResources().getDisplayMetrics());
    }

    /** 添加左/右边缘热区条 */
    private void addEdges() {
        int w = dp(EDGE_WIDTH_DP);
        int h = WindowManager.LayoutParams.MATCH_PARENT;

        leftEdge = new View(this);
        leftEdge.setBackgroundColor(0x00000000);
        leftEdge.setOnTouchListener((v, e) -> handleEdgeTouch(e, true));

        edgeLeftParams = new WindowManager.LayoutParams(
                w, h, edgeType(),
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
                PixelFormat.TRANSLUCENT);
        edgeLeftParams.gravity = Gravity.START | Gravity.TOP;

        rightEdge = new View(this);
        rightEdge.setBackgroundColor(0x00000000);
        rightEdge.setOnTouchListener((v, e) -> handleEdgeTouch(e, false));

        edgeRightParams = new WindowManager.LayoutParams(
                w, h, edgeType(),
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
                PixelFormat.TRANSLUCENT);
        edgeRightParams.gravity = Gravity.END | Gravity.TOP;

        try { windowManager.addView(leftEdge, edgeLeftParams); } catch (Exception e) {
            android.util.Log.e("SidebarService", "add left edge failed", e);
        }
        try { windowManager.addView(rightEdge, edgeRightParams); } catch (Exception e) {
            android.util.Log.e("SidebarService", "add right edge failed", e);
        }
    }

    private void removeEdges() {
        if (leftEdge != null) { try { windowManager.removeView(leftEdge); } catch (Exception ignored) {} leftEdge = null; }
        if (rightEdge != null) { try { windowManager.removeView(rightEdge); } catch (Exception ignored) {} rightEdge = null; }
    }

    /** 处理边缘触摸：从边缘向内滑动超过阈值时展开面板 */
    private boolean handleEdgeTouch(MotionEvent e, boolean fromLeft) {
        switch (e.getAction()) {
            case MotionEvent.ACTION_DOWN:
                return true;
            case MotionEvent.ACTION_MOVE: {
                float dx = fromLeft ? e.getX() : -e.getX();
                if (dx > dp(SWIPE_THRESHOLD_DP)) {
                    if (sidebarPanel == null) showPanel(fromLeft);
                    return true;
                }
                return true;
            }
            case MotionEvent.ACTION_UP:
                return true;
        }
        return false;
    }

    // ============ 侧边栏面板 ============

    /** 显示侧边栏面板 */
    private void showPanel(boolean fromLeft) {
        try {
            sidebarPanel = android.view.LayoutInflater.from(this)
                    .inflate(R.layout.view_sidebar_panel, null);
            int panelW = dp(240);
            panelParams = new WindowManager.LayoutParams(
                    panelW, WindowManager.LayoutParams.MATCH_PARENT, edgeType(),
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                            | WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
                            | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                    PixelFormat.TRANSLUCENT);
            panelParams.gravity = (fromLeft ? Gravity.START : Gravity.END) | Gravity.TOP;

            // 根据方向设置圆角背景
            View root = sidebarPanel.findViewById(R.id.sidebar_root);
            root.setBackgroundResource(fromLeft
                    ? R.drawable.bg_sidebar_panel_left
                    : R.drawable.bg_sidebar_panel_right);

            // 关闭按钮
            View btnClose = sidebarPanel.findViewById(R.id.iv_sidebar_close);
            if (btnClose != null) btnClose.setOnClickListener(v -> hidePanel());

            // 系统快捷开关
            setupToggles();

            // 导航快捷按钮
            setupNavActions();

            // 快捷应用 RecyclerView
            rvApps = sidebarPanel.findViewById(R.id.rv_sidebar_apps);
            tvAppsEmpty = sidebarPanel.findViewById(R.id.tv_sidebar_apps_empty);
            pbLoading = sidebarPanel.findViewById(R.id.pb_sidebar_loading);
            rvApps.setLayoutManager(new GridLayoutManager(this, 3));
            panelAdapter = new FloatAppAdapter(getPackageManager(), 24);
            panelAdapter.setListener(entry -> {
                if (entry.launchIntent != null) {
                    try {
                        entry.launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        startActivity(entry.launchIntent);
                    } catch (Exception ex) {
                        android.util.Log.e("SidebarService", "launch app failed", ex);
                        Toast.makeText(this, R.string.launch_fail, Toast.LENGTH_SHORT).show();
                    }
                }
                hidePanel();
            });
            rvApps.setAdapter(panelAdapter);

            // 先显示遮罩，再显示面板
            addDimOverlay(fromLeft);
            windowManager.addView(sidebarPanel, panelParams);

            // 入场动画
            sidebarPanel.setTranslationX(fromLeft ? -panelW : panelW);
            sidebarPanel.animate().translationX(0).setDuration(180).start();

            // 异步加载应用
            loadAppsAsync();
        } catch (Exception e) {
            android.util.Log.e("SidebarService", "showPanel failed", e);
            hidePanel();
        }
    }

    /** 添加半透明遮罩，点击关闭面板 */
    private void addDimOverlay(boolean fromLeft) {
        dimOverlay = new View(this);
        dimOverlay.setBackgroundColor(0x44000000);
        overlayParams = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT, edgeType(),
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT);
        dimOverlay.setOnTouchListener((v, e) -> {
            if (e.getAction() == MotionEvent.ACTION_DOWN) {
                hidePanel();
                return true;
            }
            return false;
        });
        try { windowManager.addView(dimOverlay, overlayParams); } catch (Exception e) {
            android.util.Log.e("SidebarService", "addDimOverlay failed", e);
        }
    }

    /** 隐藏面板 */
    private void hidePanel() {
        if (dimOverlay != null) {
            try { windowManager.removeView(dimOverlay); } catch (Exception ignored) {}
            dimOverlay = null;
        }
        if (sidebarPanel != null) {
            try { windowManager.removeView(sidebarPanel); } catch (Exception ignored) {}
            sidebarPanel = null;
            panelAdapter = null;
            rvApps = null;
        }
    }

    // ============ 系统快捷开关 ============

    private void setupToggles() {
        bindToggle(R.id.toggle_wifi, R.id.iv_toggle_wifi,
                SystemToggleHelper.isWifiOn(this),
                v -> {
                    boolean on = SystemToggleHelper.toggleWifi(this);
                    updateToggleBg(v.findViewById(R.id.iv_toggle_wifi), on);
                    Toast.makeText(this, on ? R.string.quick_on : R.string.quick_off,
                            Toast.LENGTH_SHORT).show();
                });
        bindToggle(R.id.toggle_bluetooth, R.id.iv_toggle_bluetooth,
                SystemToggleHelper.isBluetoothOn(this),
                v -> {
                    boolean on = SystemToggleHelper.toggleBluetooth(this);
                    updateToggleBg(v.findViewById(R.id.iv_toggle_bluetooth), on);
                    Toast.makeText(this, on ? R.string.quick_on : R.string.quick_off,
                            Toast.LENGTH_SHORT).show();
                });
        // 亮度：点击打开系统亮度设置（车机无法直接切换自动/手动）
        bindToggle(R.id.toggle_brightness, 0, true, v -> {
            try {
                Intent it = new Intent(Settings.ACTION_DISPLAY_SETTINGS);
                it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(it);
                hidePanel();
            } catch (Exception e) {
                Toast.makeText(this, R.string.launch_fail, Toast.LENGTH_SHORT).show();
            }
        });
        bindToggle(R.id.toggle_torch, R.id.iv_toggle_torch,
                SystemToggleHelper.isTorchOn(this),
                v -> {
                    boolean on = SystemToggleHelper.toggleTorch(this);
                    updateToggleBg(v.findViewById(R.id.iv_toggle_torch), on);
                    Toast.makeText(this, on ? R.string.quick_on : R.string.quick_off,
                            Toast.LENGTH_SHORT).show();
                });
        bindToggle(R.id.toggle_rotation, R.id.iv_toggle_rotation,
                SystemToggleHelper.isRotationOn(this),
                v -> {
                    boolean on = SystemToggleHelper.toggleRotation(this);
                    updateToggleBg(v.findViewById(R.id.iv_toggle_rotation), on);
                    Toast.makeText(this, on ? R.string.quick_on : R.string.quick_off,
                            Toast.LENGTH_SHORT).show();
                });
    }

    private void bindToggle(int containerId, int iconId, boolean on, View.OnClickListener l) {
        View v = sidebarPanel.findViewById(containerId);
        if (v == null) return;
        v.setOnClickListener(l);
        if (iconId != 0) updateToggleBg((ImageView) v.findViewById(iconId), on);
    }

    private void updateToggleBg(ImageView iv, boolean on) {
        if (iv == null) return;
        iv.setBackgroundResource(on ? R.drawable.bg_quick_toggle_on : R.drawable.bg_quick_toggle_off);
        // 关闭态图标变暗
        iv.setAlpha(on ? 1.0f : 0.5f);
    }

    // ============ 导航快捷按钮 ============

    private void setupNavActions() {
        boolean master = PrefsUtil.isFloatQuickMasterOn(this);
        if (!master) {
            View nav = sidebarPanel.findViewById(R.id.ll_sidebar_nav);
            if (nav != null) nav.setVisibility(View.GONE);
            return;
        }
        setNavVisible(R.id.nav_back, PrefsUtil.isFloatQuickBackOn(this), v -> performBack());
        setNavVisible(R.id.nav_home, PrefsUtil.isFloatQuickHomeOn(this), v -> performHome());
        setNavVisible(R.id.nav_recents, PrefsUtil.isFloatQuickRecentsOn(this), v -> performRecents());
        setNavVisible(R.id.nav_lock, PrefsUtil.isFloatQuickLockOn(this), v -> performLock());
    }

    private void setNavVisible(int viewId, boolean visible, View.OnClickListener l) {
        View v = sidebarPanel.findViewById(viewId);
        if (v == null) return;
        v.setVisibility(visible ? View.VISIBLE : View.GONE);
        if (visible) v.setOnClickListener(l);
    }

    private void performBack() {
        KeyMappingAccessibilityService svc = KeyMappingAccessibilityService.getInstance();
        if (svc != null && svc.performGlobalBack()) { hidePanel(); return; }
        Toast.makeText(this, R.string.quick_accessibility_required, Toast.LENGTH_SHORT).show();
    }

    private void performHome() {
        KeyMappingAccessibilityService svc = KeyMappingAccessibilityService.getInstance();
        if (svc != null && svc.performHome()) { hidePanel(); return; }
        try {
            Intent home = new Intent(Intent.ACTION_MAIN);
            home.addCategory(Intent.CATEGORY_HOME);
            home.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(home);
            hidePanel();
        } catch (Exception e) {
            Toast.makeText(this, R.string.launch_fail, Toast.LENGTH_SHORT).show();
        }
    }

    private void performRecents() {
        KeyMappingAccessibilityService svc = KeyMappingAccessibilityService.getInstance();
        if (svc != null && svc.performRecentTasks()) { hidePanel(); return; }
        Toast.makeText(this, R.string.quick_accessibility_required, Toast.LENGTH_SHORT).show();
    }

    private void performLock() {
        KeyMappingAccessibilityService svc = KeyMappingAccessibilityService.getInstance();
        if (svc != null && svc.lockScreen()) { hidePanel(); return; }
        Toast.makeText(this, R.string.quick_accessibility_required, Toast.LENGTH_SHORT).show();
    }

    // ============ 快捷应用加载 ============

    private void loadAppsAsync() {
        if (pbLoading != null) pbLoading.setVisibility(View.VISIBLE);
        if (rvApps != null) rvApps.setVisibility(View.GONE);
        if (tvAppsEmpty != null) tvAppsEmpty.setVisibility(View.GONE);

        ioPool.execute(() -> {
            List<FloatAppAdapter.AppEntry> result = new ArrayList<>();
            try {
                List<String> pkgs = PrefsUtil.getFloatApps(this);
                PackageManager pm = getPackageManager();
                if (pkgs != null) {
                    for (String pkg : pkgs) {
                        try {
                            Intent launch = pm.getLaunchIntentForPackage(pkg);
                            if (launch == null) continue;
                            android.content.pm.ApplicationInfo ai = pm.getApplicationInfo(pkg, 0);
                            FloatAppAdapter.AppEntry en = new FloatAppAdapter.AppEntry();
                            en.packageName = pkg;
                            en.label = pm.getApplicationLabel(ai).toString();
                            en.icon = pm.getApplicationIcon(ai);
                            en.launchIntent = launch;
                            result.add(en);
                        } catch (Exception ignored) {}
                    }
                }
            } catch (Exception e) {
                android.util.Log.e("SidebarService", "loadAppsAsync failed", e);
            }
            mainHandler.post(() -> {
                if (sidebarPanel == null) return; // 面板已关闭
                if (pbLoading != null) pbLoading.setVisibility(View.GONE);
                if (panelAdapter != null) panelAdapter.setData(result);
                boolean empty = result.isEmpty();
                if (rvApps != null) rvApps.setVisibility(empty ? View.GONE : View.VISIBLE);
                if (tvAppsEmpty != null) tvAppsEmpty.setVisibility(empty ? View.VISIBLE : View.GONE);
            });
        });
    }

    // ============ 刷新广播 ============

    private BroadcastReceiver refreshReceiver;

    private void registerRefreshReceiver() {
        refreshReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (ACTION_REFRESH.equals(intent.getAction()) && sidebarPanel != null) {
                    setupNavActions();
                    if (panelAdapter != null) loadAppsAsync();
                }
            }
        };
        IntentFilter f = new IntentFilter(ACTION_REFRESH);
        try { registerReceiver(refreshReceiver, f); } catch (Exception ignored) {}
    }

    private void unregisterRefreshReceiver() {
        if (refreshReceiver != null) {
            try { unregisterReceiver(refreshReceiver); } catch (Exception ignored) {}
            refreshReceiver = null;
        }
    }
}
