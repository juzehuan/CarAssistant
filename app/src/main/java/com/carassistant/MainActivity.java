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

package com.carassistant;

import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;

import com.carassistant.service.SidebarService;
import com.carassistant.ui.AppFragment;
import com.carassistant.ui.CleanFragment;
import com.carassistant.ui.ControlPanelActivity;
import com.carassistant.ui.FileFragment;
import com.carassistant.ui.HomeFragment;
import com.carassistant.ui.PermissionActivity;
import com.carassistant.util.KeyActionExecutor;
import com.carassistant.util.KeyMappingUtil;
import com.carassistant.util.KeyTriggerDetector;
import com.carassistant.util.MemoryUtil;
import com.carassistant.util.PermissionUtil;
import com.carassistant.util.PrefsUtil;
import com.google.android.material.bottomnavigation.BottomNavigationView;

/**
 * 主活动，承载底部导航四个 Tab
 */
public class MainActivity extends AppCompatActivity {

    private BottomNavigationView bottomNav;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // 首次启动权限引导：未完成时跳转到 PermissionActivity 并 finish 本页
        if (!PrefsUtil.isPermissionGuideDone(this)) {
            startActivity(new Intent(this, PermissionActivity.class));
            finish();
            return;
        }
        setContentView(R.layout.activity_main);

        bottomNav = findViewById(R.id.bottom_nav);
        bottomNav.setOnNavigationItemSelectedListener(item -> {
            Fragment target = null;
            int id = item.getItemId();
            if (id == R.id.nav_home) target = new HomeFragment();
            else if (id == R.id.nav_clean) target = new CleanFragment();
            else if (id == R.id.nav_app) target = new AppFragment();
            else if (id == R.id.nav_file) target = new FileFragment();
            if (target != null) {
                getSupportFragmentManager().beginTransaction()
                        .replace(R.id.main_container, target)
                        .commit();
                return true;
            }
            return false;
        });

        if (savedInstanceState == null) {
            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.main_container, new HomeFragment())
                    .commit();
        }

        // 请求通知权限（用于悬浮球服务的前台通知）
        if (!PermissionUtil.hasNotificationPermission(this)) {
            PermissionUtil.requestNotificationPermission(this, 1001);
        }

        // 初始化按键触发检测器（支持单击/双击/长按/组合键）
        keyDetector = new KeyTriggerDetector(this);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == R.id.menu_settings) {
            startActivity(new Intent(this, SettingsActivity.class));
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 0x101) {
            // 悬浮窗权限回调
            if (PermissionUtil.canDrawOverlays(this)) {
                startSidebarService();
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }

    /** 开启侧边栏服务（替代悬浮球）：任何异常都安全降级，不让应用崩溃 */
    public void startSidebarService() {
        try {
            Intent intent = new Intent(this, SidebarService.class);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent);
            } else {
                startService(intent);
            }
        } catch (Exception e) {
            android.util.Log.e("MainActivity", "startSidebarService failed", e);
            Toast.makeText(this, R.string.float_start_failed, Toast.LENGTH_SHORT).show();
            // 同步开关状态：服务启动失败，下次 onResume 会自动回滚开关
        }
    }

    /** 关闭侧边栏服务 */
    public void stopSidebarService() {
        try {
            stopService(new Intent(this, SidebarService.class));
        } catch (Exception ignored) {}
    }

    /** 通知侧边栏刷新（设置变更后调用） */
    public void notifySidebarRefresh() {
        try {
            sendBroadcast(new Intent(SidebarService.ACTION_REFRESH));
        } catch (Exception ignored) {}
    }

    @SuppressWarnings("deprecation")
    @Override
    public void onBackPressed() {
        Fragment current = getSupportFragmentManager().findFragmentById(R.id.main_container);
        if (current instanceof FileFragment) {
            if (((FileFragment) current).onBackPressed()) return;
        }
        super.onBackPressed();
    }

    private KeyTriggerDetector keyDetector;

    /**
     * 按键映射拦截：检查是否有配置该按键的映射，有则执行对应动作
     * 支持单击/双击/长按/组合键
     */
    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyDetector != null && keyDetector.onKeyDown(keyCode, event)) {
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        if (keyDetector != null) {
            keyDetector.onKeyUp(keyCode, event);
        }
        return super.onKeyUp(keyCode, event);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (keyDetector != null) {
            keyDetector.cleanup();
            keyDetector = null;
        }
    }
}
