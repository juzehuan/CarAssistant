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

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.carassistant.adapter.PickAppAdapter;
import com.carassistant.adapter.SimpleAppAdapter;
import com.carassistant.util.AppUtil;
import com.carassistant.util.PrefsUtil;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 设置页：
 * - 悬浮球快捷应用管理
 * - 内存清理白名单管理
 * - 关于
 */
public class SettingsActivity extends AppCompatActivity {

    private RecyclerView rvFloatApps, rvWhitelist;
    private TextView tvFloatEmpty, tvWhitelistEmpty, tvVersion;
    private SimpleAppAdapter floatAdapter, whitelistAdapter;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        findViewById(R.id.btn_back).setOnClickListener(v -> finish());

        rvFloatApps = findViewById(R.id.rv_float_apps);
        tvFloatEmpty = findViewById(R.id.tv_float_empty);
        rvWhitelist = findViewById(R.id.rv_whitelist);
        tvWhitelistEmpty = findViewById(R.id.tv_whitelist_empty);
        tvVersion = findViewById(R.id.tv_version);

        rvFloatApps.setLayoutManager(new LinearLayoutManager(this));
        rvWhitelist.setLayoutManager(new LinearLayoutManager(this));
        rvFloatApps.setNestedScrollingEnabled(false);
        rvWhitelist.setNestedScrollingEnabled(false);

        floatAdapter = new SimpleAppAdapter();
        whitelistAdapter = new SimpleAppAdapter();
        rvFloatApps.setAdapter(floatAdapter);
        rvWhitelist.setAdapter(whitelistAdapter);

        floatAdapter.setListener((position, info) -> {
            PrefsUtil.removeFloatApp(this, info.packageName);
            refreshFloatApps();
        });
        whitelistAdapter.setListener((position, info) -> {
            PrefsUtil.removeWhitelist(this, info.packageName);
            refreshWhitelist();
        });

        findViewById(R.id.btn_pick_float).setOnClickListener(v -> showPickDialog(true));
        findViewById(R.id.btn_pick_whitelist).setOnClickListener(v -> showPickDialog(false));

        // 悬浮球快捷按钮开关
        setupQuickActionSwitches();

        // 版本号
        try {
            PackageInfo pi = getPackageManager().getPackageInfo(getPackageName(), 0);
            tvVersion.setText(getString(R.string.settings_about_version, pi.versionName));
        } catch (PackageManager.NameNotFoundException e) {
            tvVersion.setText(getString(R.string.settings_about_version, "1.0"));
        }

        refreshFloatApps();
        refreshWhitelist();
    }

    /** 初始化悬浮球快捷按钮开关：总开关 + 4 个子开关 */
    private void setupQuickActionSwitches() {
        SwitchCompat swMaster = findViewById(R.id.sw_quick_master);
        SwitchCompat swBack = findViewById(R.id.sw_quick_back);
        SwitchCompat swHome = findViewById(R.id.sw_quick_home);
        SwitchCompat swRecents = findViewById(R.id.sw_quick_recents);
        SwitchCompat swLock = findViewById(R.id.sw_quick_lock);
        final View llOptions = findViewById(R.id.ll_quick_options);

        // 初始状态
        boolean master = PrefsUtil.isFloatQuickMasterOn(this);
        swMaster.setChecked(master);
        applyMasterState(master, llOptions, swBack, swHome, swRecents, swLock);
        swBack.setChecked(PrefsUtil.isFloatQuickBackOn(this));
        swHome.setChecked(PrefsUtil.isFloatQuickHomeOn(this));
        swRecents.setChecked(PrefsUtil.isFloatQuickRecentsOn(this));
        swLock.setChecked(PrefsUtil.isFloatQuickLockOn(this));

        // 总开关：关闭后子开关区域变灰，但保留子开关状态
        swMaster.setOnCheckedChangeListener((button, checked) -> {
            PrefsUtil.setFloatQuickMaster(this, checked);
            applyMasterState(checked, llOptions, swBack, swHome, swRecents, swLock);
            sendRefreshFloatBroadcast();
        });
        // 子开关：变更后立即持久化并通知悬浮球刷新
        swBack.setOnCheckedChangeListener((button, checked) -> {
            if (swBack.isEnabled()) {
                PrefsUtil.setFloatQuickBack(this, checked);
                sendRefreshFloatBroadcast();
            }
        });
        swHome.setOnCheckedChangeListener((button, checked) -> {
            if (swHome.isEnabled()) {
                PrefsUtil.setFloatQuickHome(this, checked);
                sendRefreshFloatBroadcast();
            }
        });
        swRecents.setOnCheckedChangeListener((button, checked) -> {
            if (swRecents.isEnabled()) {
                PrefsUtil.setFloatQuickRecents(this, checked);
                sendRefreshFloatBroadcast();
            }
        });
        swLock.setOnCheckedChangeListener((button, checked) -> {
            if (swLock.isEnabled()) {
                PrefsUtil.setFloatQuickLock(this, checked);
                sendRefreshFloatBroadcast();
            }
        });
    }

    /** 总开关关闭时禁用所有子开关并降低视觉权重 */
    private void applyMasterState(boolean master, View llOptions,
                                  SwitchCompat swBack, SwitchCompat swHome,
                                  SwitchCompat swRecents, SwitchCompat swLock) {
        if (llOptions != null) llOptions.setAlpha(master ? 1.0f : 0.4f);
        swBack.setEnabled(master);
        swHome.setEnabled(master);
        swRecents.setEnabled(master);
        swLock.setEnabled(master);
    }

    private void refreshFloatApps() {
        List<String> pkgList = PrefsUtil.getFloatApps(this);
        if (pkgList.isEmpty()) {
            floatAdapter.setData(Collections.emptyList());
            tvFloatEmpty.setVisibility(View.VISIBLE);
            rvFloatApps.setVisibility(View.GONE);
            return;
        }
        new LoadAppsTask(this, pkgList, true).execute();
    }

    private void refreshWhitelist() {
        Set<String> pkgSet = PrefsUtil.getWhitelist(this);
        if (pkgSet.isEmpty()) {
            whitelistAdapter.setData(Collections.emptyList());
            tvWhitelistEmpty.setVisibility(View.VISIBLE);
            rvWhitelist.setVisibility(View.GONE);
            return;
        }
        new LoadAppsTask(this, new ArrayList<>(pkgSet), false).execute();
    }

    private void showPickDialog(boolean forFloat) {
        new LoadAllAppsTask(this, forFloat).execute();
    }

    /** 加载指定的应用列表（用于已选应用展示） */
    private static class LoadAppsTask extends AsyncTask<Void, Void, List<AppUtil.AppInfo>> {
        private final WeakReference<SettingsActivity> ref;
        private final List<String> pkgList;
        private final boolean forFloat;

        LoadAppsTask(SettingsActivity act, List<String> pkgList, boolean forFloat) {
            this.ref = new WeakReference<>(act);
            this.pkgList = pkgList;
            this.forFloat = forFloat;
        }

        @Override
        protected List<AppUtil.AppInfo> doInBackground(Void... voids) {
            SettingsActivity act = ref.get();
            if (act == null) return Collections.emptyList();
            PackageManager pm = act.getPackageManager();
            List<AppUtil.AppInfo> result = new ArrayList<>();
            for (String pkg : pkgList) {
                try {
                    android.content.pm.ApplicationInfo ai = pm.getApplicationInfo(pkg, 0);
                    AppUtil.AppInfo info = new AppUtil.AppInfo();
                    info.packageName = pkg;
                    info.name = pm.getApplicationLabel(ai).toString();
                    info.icon = pm.getApplicationIcon(ai);
                    info.launchIntent = pm.getLaunchIntentForPackage(pkg);
                    result.add(info);
                } catch (PackageManager.NameNotFoundException ignored) {
                    // 应用可能已卸载
                }
            }
            return result;
        }

        @Override
        protected void onPostExecute(List<AppUtil.AppInfo> data) {
            SettingsActivity act = ref.get();
            if (act == null || act.isFinishing()) return;
            if (forFloat) {
                act.floatAdapter.setData(data);
                boolean empty = data.isEmpty();
                act.tvFloatEmpty.setVisibility(empty ? View.VISIBLE : View.GONE);
                act.rvFloatApps.setVisibility(empty ? View.GONE : View.VISIBLE);
            } else {
                act.whitelistAdapter.setData(data);
                boolean empty = data.isEmpty();
                act.tvWhitelistEmpty.setVisibility(empty ? View.VISIBLE : View.GONE);
                act.rvWhitelist.setVisibility(empty ? View.GONE : View.VISIBLE);
            }
        }
    }

    /** 加载全部已安装应用并弹窗选择 */
    private static class LoadAllAppsTask extends AsyncTask<Void, Void, List<AppUtil.AppInfo>> {
        private final WeakReference<SettingsActivity> ref;
        private final boolean forFloat;

        LoadAllAppsTask(SettingsActivity act, boolean forFloat) {
            this.ref = new WeakReference<>(act);
            this.forFloat = forFloat;
        }

        @Override
        protected List<AppUtil.AppInfo> doInBackground(Void... voids) {
            SettingsActivity act = ref.get();
            if (act == null) return Collections.emptyList();
            // 侧边栏快捷应用允许选择系统应用（如设置、相机等常用系统应用）
            // 内存清理白名单仅显示用户应用（系统应用通常不会被结束）
            int filter = forFloat ? 0 : 1;
            List<AppUtil.AppInfo> all = AppUtil.getInstalledApps(act, filter);
            if (!forFloat) return all;
            // 侧边栏模式：过滤掉无启动入口的应用（很多系统应用是纯服务）
            List<AppUtil.AppInfo> result = new ArrayList<>();
            for (AppUtil.AppInfo info : all) {
                if (info.launchIntent != null) result.add(info);
            }
            return result;
        }

        @Override
        protected void onPostExecute(List<AppUtil.AppInfo> data) {
            SettingsActivity act = ref.get();
            if (act == null || act.isFinishing()) return;
            act.showPickDialogInternal(data, forFloat);
        }
    }

    private void showPickDialogInternal(List<AppUtil.AppInfo> allApps, boolean forFloat) {
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_pick_app, null);
        RecyclerView rv = view.findViewById(R.id.rv_pick);
        rv.setLayoutManager(new LinearLayoutManager(this));

        // 标题区：自定义渐变标题，覆盖 AlertDialog 默认标题
        TextView tvTitle = view.findViewById(R.id.tv_pick_title);
        TextView tvSubtitle = view.findViewById(R.id.tv_pick_subtitle);
        String title = forFloat ? getString(R.string.settings_float_apps)
                : getString(R.string.settings_whitelist);
        tvTitle.setText(title);
        // subtitle 用默认文案，不需要改

        final PickAppAdapter adapter = new PickAppAdapter();
        adapter.setData(allApps);
        if (forFloat) {
            adapter.setSelected(new HashSet<>(PrefsUtil.getFloatApps(this)));
        } else {
            adapter.setSelected(PrefsUtil.getWhitelist(this));
        }
        rv.setAdapter(adapter);

        // 搜索框
        final EditText etSearch = view.findViewById(R.id.et_pick_search);
        final ImageView ivClear = view.findViewById(R.id.iv_pick_clear);
        etSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) {
                String q = s.toString().trim().toLowerCase(Locale.getDefault());
                ivClear.setVisibility(q.isEmpty() ? View.GONE : View.VISIBLE);
                List<AppUtil.AppInfo> filtered = new ArrayList<>();
                for (AppUtil.AppInfo info : allApps) {
                    if (info.name != null && info.name.toLowerCase(Locale.getDefault()).contains(q)) {
                        filtered.add(info);
                    } else if (info.packageName != null && info.packageName.toLowerCase(Locale.getDefault()).contains(q)) {
                        filtered.add(info);
                    }
                }
                adapter.setData(filtered);
            }
        });
        ivClear.setOnClickListener(v -> etSearch.setText(""));

        // 不使用 AlertDialog 默认标题/按钮，使用布局内自定义按钮
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(view)
                .create();
        dialog.show();

        // 自定义按钮回调
        view.findViewById(R.id.btn_pick_cancel).setOnClickListener(v -> dialog.dismiss());
        view.findViewById(R.id.btn_pick_confirm).setOnClickListener(v -> {
            Set<String> selected = adapter.getSelected();
            if (forFloat) {
                PrefsUtil.setFloatApps(this, new ArrayList<>(selected));
                refreshFloatApps();
                sendRefreshFloatBroadcast();
            } else {
                PrefsUtil.setWhitelist(this, selected);
                refreshWhitelist();
            }
            dialog.dismiss();
        });
    }

    /** 通知侧边栏服务刷新（应用列表与快捷按钮） */
    private void sendRefreshFloatBroadcast() {
        Intent intent = new Intent(com.carassistant.service.SidebarService.ACTION_REFRESH);
        intent.setPackage(getPackageName());
        sendBroadcast(intent);
    }
}
