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
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.carassistant.R;
import com.carassistant.adapter.AppAdapter;
import com.carassistant.util.AppUtil;
import com.google.android.material.tabs.TabLayout;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class AppFragment extends Fragment {

    private TabLayout tabs;
    private RecyclerView rv;
    private TextView tvEmpty, tvCount;
    private EditText etSearch;
    private ImageView ivClear;
    private SwipeRefreshLayout refresh;
    private AppAdapter adapter;
    private int currentFilter = 0; // 0 all, 1 user, 2 system
    /** 后台加载的全部应用（用于搜索过滤） */
    private List<AppUtil.AppInfo> allApps = new ArrayList<>();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_app, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        tabs = view.findViewById(R.id.tabs_app);
        rv = view.findViewById(R.id.rv_app);
        tvEmpty = view.findViewById(R.id.tv_app_empty);
        tvCount = view.findViewById(R.id.tv_app_count);
        refresh = view.findViewById(R.id.refresh_app);
        etSearch = view.findViewById(R.id.et_app_search);
        ivClear = view.findViewById(R.id.iv_app_search_clear);

        tabs.addTab(tabs.newTab().setText(R.string.app_all));
        tabs.addTab(tabs.newTab().setText(R.string.app_user));
        tabs.addTab(tabs.newTab().setText(R.string.app_system));
        tabs.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override public void onTabSelected(TabLayout.Tab tab) {
                currentFilter = tab.getPosition();
                // 切换 Tab 时清空搜索，避免过滤结果错乱
                if (etSearch != null && etSearch.getText().length() > 0) {
                    etSearch.setText("");
                } else {
                    loadApps();
                }
            }
            @Override public void onTabUnselected(TabLayout.Tab tab) {}
            @Override public void onTabReselected(TabLayout.Tab tab) {}
        });

        // 搜索框：实时过滤当前列表
        etSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) {
                applySearchFilter(s.toString().trim());
            }
        });
        ivClear.setOnClickListener(v -> etSearch.setText(""));

        rv.setLayoutManager(new LinearLayoutManager(requireContext()));
        adapter = new AppAdapter();
        adapter.setListener(new AppAdapter.OnAppActionListener() {
            @Override public void onOpen(AppUtil.AppInfo info) {
                launchApp(info);
            }
            @Override public void onUninstall(AppUtil.AppInfo info) {
                confirmUninstall(info);
            }
            @Override public void onDetail(AppUtil.AppInfo info) {
                try {
                    Intent it = AppUtil.buildAppDetailIntent(info.packageName);
                    it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(it);
                } catch (Exception ignored) {}
            }
        });
        rv.setAdapter(adapter);

        refresh.setOnRefreshListener(this::loadApps);

        loadApps();
    }

    private void loadApps() {
        new LoadTask(this, currentFilter).execute();
    }

    /**
     * 启动应用：优先使用缓存的 launchIntent，失败则重新查询并尝试，最后兜底跳详情页
     * 设计要点：
     * - launchIntent 可能在加载后被系统冻结/卸载失效，需重新查询
     * - 失败时给用户明确反馈，而不是静默跳转系统设置
     * - 仅在确实无法启动时才跳转应用详情页
     */
    private void launchApp(AppUtil.AppInfo info) {
        Context ctx = requireContext();
        // 1. 优先使用缓存的 launchIntent
        Intent launch = info.launchIntent;
        // 2. 缓存为空或失败后，重新通过 PackageManager 查询
        if (launch == null) {
            launch = ctx.getPackageManager().getLaunchIntentForPackage(info.packageName);
        }
        if (launch != null) {
            launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            try {
                startActivity(launch);
                return;
            } catch (Exception e) {
                android.util.Log.w("AppFragment", "startActivity failed for " + info.packageName, e);
                // 继续走 fallback
            }
        }
        // 3. 兜底：检查应用是否被禁用（pm disabled 会导致 getLaunchIntentForPackage 返回 null）
        try {
            android.content.pm.ApplicationInfo ai = ctx.getPackageManager()
                    .getApplicationInfo(info.packageName, 0);
            boolean enabled = ai.enabled;
            if (!enabled) {
                android.widget.Toast.makeText(ctx,
                        getString(R.string.app_disabled_cannot_open, info.name),
                        android.widget.Toast.LENGTH_LONG).show();
                // 跳转到应用详情页，方便用户启用
                Intent detail = AppUtil.buildAppDetailIntent(info.packageName);
                detail.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                try { startActivity(detail); } catch (Exception ignored) {}
                return;
            }
        } catch (Exception ignored) {}
        // 4. 最终兜底：跳详情页 + 提示
        android.widget.Toast.makeText(ctx,
                getString(R.string.app_open_failed, info.name),
                android.widget.Toast.LENGTH_LONG).show();
        try {
            Intent detail = AppUtil.buildAppDetailIntent(info.packageName);
            detail.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(detail);
        } catch (Exception ignored) {}
    }

    /**
     * 在当前已加载列表上应用搜索过滤。
     * - 空关键字：还原原始列表
     * - 非空：按应用名/包名模糊匹配
     */
    private void applySearchFilter(String query) {
        if (ivClear != null) {
            ivClear.setVisibility(query.isEmpty() ? View.GONE : View.VISIBLE);
        }
        if (allApps.isEmpty()) return;

        if (query.isEmpty()) {
            adapter.setData(allApps);
            tvCount.setText("共 " + allApps.size() + " 个应用");
            tvEmpty.setVisibility(allApps.isEmpty() ? View.VISIBLE : View.GONE);
            rv.setVisibility(allApps.isEmpty() ? View.GONE : View.VISIBLE);
            return;
        }

        String q = query.toLowerCase(Locale.getDefault());
        List<AppUtil.AppInfo> filtered = new ArrayList<>();
        for (AppUtil.AppInfo info : allApps) {
            if (info.name != null && info.name.toLowerCase(Locale.getDefault()).contains(q)) {
                filtered.add(info);
            } else if (info.packageName != null
                    && info.packageName.toLowerCase(Locale.getDefault()).contains(q)) {
                filtered.add(info);
            }
        }
        adapter.setData(filtered);
        tvCount.setText("匹配 " + filtered.size() + " 个应用");
        if (filtered.isEmpty()) {
            tvEmpty.setText(R.string.app_search_no_match);
            tvEmpty.setVisibility(View.VISIBLE);
            rv.setVisibility(View.GONE);
        } else {
            tvEmpty.setText(R.string.empty_list);
            tvEmpty.setVisibility(View.GONE);
            rv.setVisibility(View.VISIBLE);
        }
    }

    /** 卸载二次确认：使用自定义 ConfirmDialog */
    private void confirmUninstall(AppUtil.AppInfo info) {
        android.util.Log.d("AppFragment", "confirmUninstall called for: " + info.name
                + " pkg=" + info.packageName);
        if (info.packageName == null || info.packageName.isEmpty()) {
            android.widget.Toast.makeText(requireContext(),
                    "包名为空，无法卸载", android.widget.Toast.LENGTH_SHORT).show();
            return;
        }
        ConfirmDialog.show(requireActivity(),
                R.drawable.ic_panel_close, ConfirmDialog.TYPE_DANGER,
                getString(R.string.app_uninstall),
                getString(R.string.app_uninstall_confirm, info.name),
                getString(R.string.app_uninstall),
                () -> {
                    android.util.Log.d("AppFragment", "uninstall onConfirm triggered, pkg=" + info.packageName);
                    try {
                        Intent it = AppUtil.buildUninstallIntent(info.packageName);
                        it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        // 提示用户即将跳转到系统卸载页面
                        if (isAdded()) {
                            android.widget.Toast.makeText(requireContext(),
                                    "正在跳转到系统卸载页面...", android.widget.Toast.LENGTH_SHORT).show();
                        }
                        requireActivity().startActivity(it);
                        android.util.Log.d("AppFragment", "uninstall intent started");
                    } catch (Exception e) {
                        android.util.Log.e("AppFragment", "uninstall failed", e);
                        if (isAdded()) {
                            android.widget.Toast.makeText(requireContext(),
                                    R.string.launch_fail, android.widget.Toast.LENGTH_SHORT).show();
                        }
                    }
                });
    }

    private static class LoadTask extends AsyncTask<Void, Void, List<AppUtil.AppInfo>> {
        private final WeakReference<AppFragment> ref;
        private final int filter;
        private final Context appContext;  // 在主线程获取，后台线程安全使用

        LoadTask(AppFragment f, int filter) {
            this.ref = new WeakReference<>(f);
            this.filter = filter;
            // 在主线程获取 Application Context，避免后台线程调用 requireContext()
            this.appContext = f.requireContext().getApplicationContext();
        }

        @Override
        protected void onPreExecute() {
            AppFragment f = ref.get();
            if (f != null && f.isAdded()) f.refresh.setRefreshing(true);
        }

        @Override
        protected List<AppUtil.AppInfo> doInBackground(Void... voids) {
            if (appContext == null) return null;
            return AppUtil.getInstalledApps(appContext, filter);
        }

        @Override
        protected void onPostExecute(List<AppUtil.AppInfo> data) {
            AppFragment f = ref.get();
            if (f == null || !f.isAdded() || data == null) return;
            f.refresh.setRefreshing(false);
            f.allApps = data;
            // 若搜索框有内容，沿用关键字过滤；否则直接展示原始列表
            String q = f.etSearch != null ? f.etSearch.getText().toString().trim() : "";
            if (!q.isEmpty()) {
                f.applySearchFilter(q);
            } else {
                f.adapter.setData(data);
                f.tvCount.setText("共 " + data.size() + " 个应用");
                f.tvEmpty.setVisibility(data.isEmpty() ? View.VISIBLE : View.GONE);
                f.rv.setVisibility(data.isEmpty() ? View.GONE : View.VISIBLE);
            }
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        // 卸载返回后刷新
        if (adapter != null && adapter.getItemCount() > 0) {
            loadApps();
        }
    }
}
