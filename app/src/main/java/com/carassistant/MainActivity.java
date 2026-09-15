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
 * distribution, sale or reverse engineering without written permission is
 * prohibited and subject to legal action.
 */

package com.carassistant;

import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;

import com.carassistant.ui.AppFragment;
import com.carassistant.ui.CleanFragment;
import com.carassistant.ui.FileFragment;
import com.carassistant.ui.HomeFragment;
import com.carassistant.ui.PermissionActivity;
import com.carassistant.util.PermissionUtil;
import com.carassistant.util.PrefsUtil;
import com.google.android.material.bottomnavigation.BottomNavigationView;

/**
 * 主活动，承载底部导航四个 Tab
 */
public class MainActivity extends AppCompatActivity {

    /** 通过 Intent 启动并直接跳转到指定底部 Tab */
    public static final String EXTRA_NAV_ID = "com.carassistant.extra.NAV_ID";

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
            int navId = getIntent().getIntExtra(EXTRA_NAV_ID, 0);
            if (navId != 0) {
                bottomNav.setSelectedItemId(navId);
            } else {
                getSupportFragmentManager().beginTransaction()
                        .replace(R.id.main_container, new HomeFragment())
                        .commit();
            }
        }

        // 请求通知权限（用于性能监控悬浮窗服务的前台通知）
        if (!PermissionUtil.hasNotificationPermission(this)) {
            PermissionUtil.requestNotificationPermission(this, 1001);
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        int navId = intent.getIntExtra(EXTRA_NAV_ID, 0);
        if (navId != 0 && bottomNav != null) {
            bottomNav.setSelectedItemId(navId);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
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
}
