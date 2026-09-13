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

import android.app.AlertDialog;
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
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.carassistant.adapter.PickAppAdapter;
import com.carassistant.adapter.SimpleAppAdapter;
import com.carassistant.util.AppUtil;
import com.carassistant.util.PrefsUtil;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 设置页：
 * - 内存清理白名单管理
 * - 关于
 */
public class SettingsActivity extends AppCompatActivity {

    private RecyclerView rvWhitelist;
    private TextView tvWhitelistEmpty, tvVersion;
    private SimpleAppAdapter whitelistAdapter;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        findViewById(R.id.btn_back).setOnClickListener(v -> finish());

        rvWhitelist = findViewById(R.id.rv_whitelist);
        tvWhitelistEmpty = findViewById(R.id.tv_whitelist_empty);
        tvVersion = findViewById(R.id.tv_version);

        rvWhitelist.setLayoutManager(new LinearLayoutManager(this));
        rvWhitelist.setNestedScrollingEnabled(false);

        whitelistAdapter = new SimpleAppAdapter();
        rvWhitelist.setAdapter(whitelistAdapter);

        whitelistAdapter.setListener((position, info) -> {
            PrefsUtil.removeWhitelist(this, info.packageName);
            refreshWhitelist();
        });

        findViewById(R.id.btn_pick_whitelist).setOnClickListener(v -> showPickDialog());

        // 版本号
        try {
            PackageInfo pi = getPackageManager().getPackageInfo(getPackageName(), 0);
            tvVersion.setText(getString(R.string.settings_about_version, pi.versionName));
        } catch (PackageManager.NameNotFoundException e) {
            tvVersion.setText(getString(R.string.settings_about_version, "1.0"));
        }

        refreshWhitelist();
    }

    private void refreshWhitelist() {
        Set<String> pkgSet = PrefsUtil.getWhitelist(this);
        if (pkgSet.isEmpty()) {
            whitelistAdapter.setData(Collections.emptyList());
            tvWhitelistEmpty.setVisibility(View.VISIBLE);
            rvWhitelist.setVisibility(View.GONE);
            return;
        }
        new LoadAppsTask(this, new ArrayList<>(pkgSet)).execute();
    }

    private void showPickDialog() {
        new LoadAllAppsTask(this).execute();
    }

    /** 加载指定的应用列表（用于已选应用展示） */
    private static class LoadAppsTask extends AsyncTask<Void, Void, List<AppUtil.AppInfo>> {
        private final WeakReference<SettingsActivity> ref;
        private final List<String> pkgList;

        LoadAppsTask(SettingsActivity act, List<String> pkgList) {
            this.ref = new WeakReference<>(act);
            this.pkgList = pkgList;
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
            act.whitelistAdapter.setData(data);
            boolean empty = data.isEmpty();
            act.tvWhitelistEmpty.setVisibility(empty ? View.VISIBLE : View.GONE);
            act.rvWhitelist.setVisibility(empty ? View.GONE : View.VISIBLE);
        }
    }

    /** 加载全部用户应用并弹窗选择（内存清理白名单仅显示用户应用，系统应用通常不会被结束） */
    private static class LoadAllAppsTask extends AsyncTask<Void, Void, List<AppUtil.AppInfo>> {
        private final WeakReference<SettingsActivity> ref;

        LoadAllAppsTask(SettingsActivity act) {
            this.ref = new WeakReference<>(act);
        }

        @Override
        protected List<AppUtil.AppInfo> doInBackground(Void... voids) {
            SettingsActivity act = ref.get();
            if (act == null) return Collections.emptyList();
            return AppUtil.getInstalledApps(act, 1);
        }

        @Override
        protected void onPostExecute(List<AppUtil.AppInfo> data) {
            SettingsActivity act = ref.get();
            if (act == null || act.isFinishing()) return;
            act.showPickDialogInternal(data);
        }
    }

    private void showPickDialogInternal(List<AppUtil.AppInfo> allApps) {
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_pick_app, null);
        RecyclerView rv = view.findViewById(R.id.rv_pick);
        rv.setLayoutManager(new LinearLayoutManager(this));

        // 标题区：自定义渐变标题，覆盖 AlertDialog 默认标题
        TextView tvTitle = view.findViewById(R.id.tv_pick_title);
        tvTitle.setText(getString(R.string.settings_whitelist));

        final PickAppAdapter adapter = new PickAppAdapter();
        adapter.setData(allApps);
        adapter.setSelected(PrefsUtil.getWhitelist(this));
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
            PrefsUtil.setWhitelist(this, selected);
            refreshWhitelist();
            dialog.dismiss();
        });
    }
}
