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

import android.app.ProgressDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.AsyncTask;
import android.util.Log;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.carassistant.R;
import com.carassistant.adapter.PickAppAdapter;
import com.carassistant.util.AppAutoStartManager;
import com.carassistant.util.AppUtil;
import com.carassistant.util.AutostartUtil;
import com.carassistant.util.PrefsUtil;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class AutostartActivity extends AppCompatActivity {

    private static final int TAB_SYSTEM = 0;
    private static final int TAB_USER = 1;
    private static final int FILTER_ALL = 0;
    private static final int FILTER_SYSTEM = 1;
    private static final int FILTER_USER = 2;

    private int currentTab = TAB_SYSTEM;
    private int currentFilter = FILTER_ALL;
    private String searchQuery = "";

    private RecyclerView rvSystem, rvUser;
    private TextView tvEmpty, tvCount, tabSystem, tabUser;
    private View btnAdd, btnLaunchAll;
    private View systemToolbar, userToolbar;
    private EditText etSearch;
    private ImageView ivSearchClear;
    private ProgressBar pbLoading;

    private SwitchCompat swAutostartEnabled, swReturnHome;
    private EditText etFirstDelay, etInterval;
    private View btnSaveOptions;
    private View cardSelfAutostart;

    private final List<AutostartUtil.AutostartApp> systemAllApps = new ArrayList<>();
    private final List<AppUtil.AppInfo> userApps = new ArrayList<>();

    private SystemAutostartAdapter systemAdapter;
    private UserBootAdapter userAdapter;
    private ProgressDialog pickLoadingDialog;
    private ProgressDialog toggleLoadingDialog;
    private Boolean rootCached = null;
    private boolean returnedFromDetail = false;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_autostart);

        findViewById(R.id.btn_back).setOnClickListener(v -> finish());

        rvSystem = findViewById(R.id.rv_autostart);
        rvUser = findViewById(R.id.rv_user_autostart);
        tvEmpty = findViewById(R.id.tv_empty);
        tvCount = findViewById(R.id.tv_count);
        btnAdd = findViewById(R.id.btn_add);
        btnLaunchAll = findViewById(R.id.btn_launch_all);
        tabSystem = findViewById(R.id.tab_system);
        tabUser = findViewById(R.id.tab_user);
        systemToolbar = findViewById(R.id.system_toolbar);
        userToolbar = findViewById(R.id.user_toolbar);
        etSearch = findViewById(R.id.et_search);
        ivSearchClear = findViewById(R.id.iv_search_clear);
        pbLoading = findViewById(R.id.pb_loading);

        swAutostartEnabled = findViewById(R.id.sw_autostart_enabled);
        swReturnHome = findViewById(R.id.sw_return_home);
        etFirstDelay = findViewById(R.id.et_first_delay);
        etInterval = findViewById(R.id.et_interval);
        btnSaveOptions = findViewById(R.id.btn_save_options);
        cardSelfAutostart = findViewById(R.id.card_self_autostart);

        // 车机助手自身自启动引导卡片
        final SharedPreferences selfPrefs = getSharedPreferences("autostart_self_guide", MODE_PRIVATE);
        boolean hasOpenedSelfAutostart = selfPrefs.getBoolean("self_autostart_opened", false);
        Log.d("AutostartDebug", "onCreate: cardSelfAutostart=" + cardSelfAutostart + " hasOpened=" + hasOpenedSelfAutostart);
        if (!hasOpenedSelfAutostart) {
            cardSelfAutostart.setVisibility(View.VISIBLE);
        }
        findViewById(R.id.btn_self_autostart_go).setOnClickListener(v -> {
            try {
                Intent it = new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                it.setData(android.net.Uri.parse("package:" + getPackageName()));
                it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(it);
                selfPrefs.edit().putBoolean("self_autostart_opened", true).apply();
                cardSelfAutostart.setVisibility(View.GONE);
                Toast.makeText(this, R.string.autostart_self_toast, Toast.LENGTH_LONG).show();
            } catch (Exception e) {
                Toast.makeText(this, R.string.launch_fail, Toast.LENGTH_SHORT).show();
            }
        });

        rvSystem.setLayoutManager(new LinearLayoutManager(this));
        rvUser.setLayoutManager(new LinearLayoutManager(this));

        systemAdapter = new SystemAutostartAdapter();
        systemAdapter.setListener(new SystemAutostartAdapter.OnItemClickListener() {
            @Override public void onItemClick(AutostartUtil.AutostartApp app) { openAppDetail(app.packageName); }
            @Override public void onToggle(AutostartUtil.AutostartApp app) { toggleAppEnabled(app); }
        });
        rvSystem.setAdapter(systemAdapter);

        userAdapter = new UserBootAdapter();
        userAdapter.setListener(new UserBootAdapter.OnUserAppActionListener() {
            @Override public void onLaunch(AppUtil.AppInfo info) { launchApp(info.packageName, info.name); }
            @Override public void onRemove(AppUtil.AppInfo info) { confirmRemoveUserApp(info); }
        });
        rvUser.setAdapter(userAdapter);

        tabSystem.setOnClickListener(v -> switchTab(TAB_SYSTEM));
        tabUser.setOnClickListener(v -> switchTab(TAB_USER));
        btnAdd.setOnClickListener(v -> showPickDialog());
        btnLaunchAll.setOnClickListener(v -> launchAllUserApps());

        findViewById(R.id.btn_refresh).setOnClickListener(v -> {
            if (currentTab == TAB_SYSTEM) { startScanSystemApps(); }
            else { loadUserApps(); }
            Toast.makeText(this, R.string.autostart_refresh, Toast.LENGTH_SHORT).show();
        });

        etSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) {
                searchQuery = s.toString().trim().toLowerCase(Locale.getDefault());
                ivSearchClear.setVisibility(searchQuery.isEmpty() ? View.GONE : View.VISIBLE);
                applySystemFilter();
            }
        });
        ivSearchClear.setOnClickListener(v -> etSearch.setText(""));

        findViewById(R.id.chip_all).setOnClickListener(v -> setFilter(FILTER_ALL));
        findViewById(R.id.chip_system).setOnClickListener(v -> setFilter(FILTER_SYSTEM));
        findViewById(R.id.chip_user).setOnClickListener(v -> setFilter(FILTER_USER));

        btnSaveOptions.setOnClickListener(v -> saveStartupOptions());
        loadStartupOptions();
        switchTab(TAB_SYSTEM);
    }

    private void switchTab(int tab) {
        currentTab = tab;
        if (tab == TAB_SYSTEM) {
            tabSystem.setBackgroundResource(R.drawable.bg_segment_active);
            tabSystem.setTypeface(tabSystem.getTypeface(), android.graphics.Typeface.BOLD);
            tabSystem.setTextColor(ContextCompat.getColor(this, R.color.brand_primary));
            tabUser.setBackgroundResource(android.R.color.transparent);
            tabUser.setTypeface(tabUser.getTypeface(), android.graphics.Typeface.NORMAL);
            tabUser.setTextColor(ContextCompat.getColor(this, R.color.text_secondary));
            systemToolbar.setVisibility(View.VISIBLE);
            userToolbar.setVisibility(View.GONE);
            rvUser.setVisibility(View.GONE);
            rvSystem.setVisibility(View.VISIBLE);
            if (systemAllApps.isEmpty()) { startScanSystemApps(); }
            else { applySystemFilter(); }
        } else {
            tabUser.setBackgroundResource(R.drawable.bg_segment_active);
            tabUser.setTypeface(tabUser.getTypeface(), android.graphics.Typeface.BOLD);
            tabUser.setTextColor(ContextCompat.getColor(this, R.color.brand_primary));
            tabSystem.setBackgroundResource(android.R.color.transparent);
            tabSystem.setTypeface(tabSystem.getTypeface(), android.graphics.Typeface.NORMAL);
            tabSystem.setTextColor(ContextCompat.getColor(this, R.color.text_secondary));
            systemToolbar.setVisibility(View.GONE);
            userToolbar.setVisibility(View.VISIBLE);
            rvSystem.setVisibility(View.GONE);
            rvUser.setVisibility(View.VISIBLE);
            pbLoading.setVisibility(View.GONE);
            loadUserApps();
        }
    }

    private void setFilter(int filter) {
        currentFilter = filter;
        updateChipUi(R.id.chip_all, filter == FILTER_ALL);
        updateChipUi(R.id.chip_system, filter == FILTER_SYSTEM);
        updateChipUi(R.id.chip_user, filter == FILTER_USER);
        applySystemFilter();
    }

    private void updateChipUi(int chipId, boolean active) {
        TextView chip = findViewById(chipId);
        if (active) {
            chip.setBackgroundResource(R.drawable.bg_chip_filter_active);
            chip.setTextColor(ContextCompat.getColor(this, R.color.brand_primary));
            chip.setTypeface(chip.getTypeface(), android.graphics.Typeface.BOLD);
        } else {
            chip.setBackgroundResource(R.drawable.bg_chip_filter);
            chip.setTextColor(ContextCompat.getColor(this, R.color.text_secondary));
            chip.setTypeface(chip.getTypeface(), android.graphics.Typeface.NORMAL);
        }
    }

    private void startScanSystemApps() {
        pbLoading.setVisibility(View.VISIBLE);
        tvEmpty.setVisibility(View.GONE);
        rvSystem.setVisibility(View.GONE);
        new ScanTask(this).execute();
    }

    private void applySystemFilter() {
        List<AutostartUtil.AutostartApp> filtered = new ArrayList<>();
        for (AutostartUtil.AutostartApp app : systemAllApps) {
            if (currentFilter == FILTER_SYSTEM && !app.system) continue;
            if (currentFilter == FILTER_USER && app.system) continue;
            if (!searchQuery.isEmpty()) {
                String name = app.name == null ? "" : app.name.toLowerCase(Locale.getDefault());
                String pkg = app.packageName == null ? "" : app.packageName.toLowerCase(Locale.getDefault());
                if (!name.contains(searchQuery) && !pkg.contains(searchQuery)) continue;
            }
            filtered.add(app);
        }
        systemAdapter.setData(filtered);
        int count = filtered.size();
        tvCount.setText(getString(R.string.autostart_count, count));
        pbLoading.setVisibility(View.GONE);
        boolean empty = count == 0;
        if (empty) {
            if (!systemAllApps.isEmpty() && (!searchQuery.isEmpty() || currentFilter != FILTER_ALL)) {
                tvEmpty.setText(R.string.autostart_search_no_match);
            } else {
                tvEmpty.setText(R.string.autostart_empty);
            }
        }
        tvEmpty.setVisibility(empty ? View.VISIBLE : View.GONE);
        rvSystem.setVisibility(empty ? View.GONE : View.VISIBLE);
    }

    private void loadUserApps() {
        List<String> pkgList = PrefsUtil.getBootApps(this);
        if (pkgList.isEmpty()) {
            userApps.clear();
            userAdapter.setData(userApps);
            updateUserTabCount(0);
            return;
        }
        new LoadUserAppsTask(this).execute();
    }

    private void updateUserTabCount(int count) {
        tvCount.setText(getString(R.string.autostart_user_count, count));
        boolean empty = count == 0;
        if (empty) { tvEmpty.setText(R.string.autostart_user_empty); }
        tvEmpty.setVisibility(empty ? View.VISIBLE : View.GONE);
        rvUser.setVisibility(empty ? View.GONE : View.VISIBLE);
        btnLaunchAll.setVisibility(empty ? View.GONE : View.VISIBLE);
    }

    private void loadStartupOptions() {
        swAutostartEnabled.setChecked(AppAutoStartManager.isEnabled(this));
        swReturnHome.setChecked(AppAutoStartManager.isReturnHome(this));
        etFirstDelay.setText(String.valueOf(AppAutoStartManager.getDelay(this)));
        etInterval.setText(String.valueOf(AppAutoStartManager.getInterval(this)));
    }

    private void saveStartupOptions() {
        try {
            int delay = parseIntOr(etFirstDelay.getText().toString().trim(), AppAutoStartManager.getDelay(this));
            int interval = parseIntOr(etInterval.getText().toString().trim(), AppAutoStartManager.getInterval(this));
            if (delay < 0 || delay > 300 || interval < 0 || interval > 300) {
                Toast.makeText(this, R.string.autostart_invalid_number, Toast.LENGTH_SHORT).show();
                return;
            }
            AppAutoStartManager.setEnabled(this, swAutostartEnabled.isChecked());
            AppAutoStartManager.setReturnHome(this, swReturnHome.isChecked());
            AppAutoStartManager.setDelay(this, delay);
            AppAutoStartManager.setInterval(this, interval);
            Toast.makeText(this, R.string.autostart_options_saved, Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(this, R.string.autostart_invalid_number, Toast.LENGTH_SHORT).show();
        }
    }

    private static int parseIntOr(String s, int def) {
        if (s == null || s.isEmpty()) return def;
        try { return Integer.parseInt(s); } catch (NumberFormatException e) { return def; }
    }

    private void openAppDetail(String pkg) {
        try {
            Intent it = AutostartUtil.buildAppDetailIntent(pkg);
            startActivity(it);
            returnedFromDetail = true;
        } catch (Exception e) {
            Toast.makeText(this, R.string.launch_fail, Toast.LENGTH_SHORT).show();
        }
    }

    private void toggleAppEnabled(AutostartUtil.AutostartApp app) {
        if (!app.enabled && AutostartUtil.isDangerous(app.packageName)) {
            Toast.makeText(this, R.string.autostart_dangerous_protected, Toast.LENGTH_LONG).show();
            return;
        }
        if (rootCached == null) { rootCached = AutostartUtil.hasRoot(); }
        if (!rootCached) {
            Toast.makeText(this, R.string.autostart_no_root, Toast.LENGTH_LONG).show();
            return;
        }
        String msg = app.enabled
                ? getString(R.string.autostart_toggle_confirm_disable, app.name)
                : getString(R.string.autostart_toggle_confirm_enable, app.name);
        ConfirmDialog.show(this, R.drawable.ic_panel_close,
                app.enabled ? ConfirmDialog.TYPE_DANGER : ConfirmDialog.TYPE_SUCCESS,
                getString(R.string.autostart_title), msg,
                app.enabled ? getString(R.string.autostart_disable) : getString(R.string.autostart_enable),
                () -> executeToggle(app));
    }

    private void executeToggle(AutostartUtil.AutostartApp app) {
        toggleLoadingDialog = new ProgressDialog(this);
        toggleLoadingDialog.setMessage(getString(R.string.loading));
        toggleLoadingDialog.setCancelable(false);
        toggleLoadingDialog.show();
        new ToggleTask(this, app).execute();
    }

    private void launchApp(String pkg, String name) {
        try {
            Intent it = getPackageManager().getLaunchIntentForPackage(pkg);
            if (it != null) { it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); startActivity(it); }
            else { Toast.makeText(this, getString(R.string.autostart_launch_failed, name), Toast.LENGTH_SHORT).show(); }
        } catch (Exception e) {
            Toast.makeText(this, getString(R.string.autostart_launch_failed, name), Toast.LENGTH_SHORT).show();
        }
    }

    private void launchAllUserApps() {
        if (userApps.isEmpty()) return;
        AppAutoStartManager.startChainNow(this);
        Toast.makeText(this, R.string.autostart_refresh, Toast.LENGTH_SHORT).show();
    }

    private void confirmRemoveUserApp(AppUtil.AppInfo info) {
        ConfirmDialog.show(this, R.drawable.ic_panel_close, ConfirmDialog.TYPE_DANGER,
                getString(R.string.autostart_title),
                getString(R.string.autostart_remove_confirm, info.name),
                getString(R.string.remove),
                () -> { PrefsUtil.removeBootApp(this, info.packageName); loadUserApps(); });
    }

    private void showPickDialog() {
        if (pickLoadingDialog != null && pickLoadingDialog.isShowing()) return;
        pickLoadingDialog = new ProgressDialog(this);
        pickLoadingDialog.setMessage(getString(R.string.autostart_loading_apps));
        pickLoadingDialog.setCancelable(false);
        pickLoadingDialog.show();
        new LoadAllAppsTask(this).execute();
    }

    private void dismissPickLoading() {
        try { if (pickLoadingDialog != null && pickLoadingDialog.isShowing()) pickLoadingDialog.dismiss(); } catch (Exception ignored) {}
        pickLoadingDialog = null;
    }

    private void dismissToggleLoading() {
        try { if (toggleLoadingDialog != null && toggleLoadingDialog.isShowing()) toggleLoadingDialog.dismiss(); } catch (Exception ignored) {}
        toggleLoadingDialog = null;
    }

    private void showPickDialogInternal(List<AppUtil.AppInfo> allApps) {
        if (isFinishing()) return;
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_pick_app, null);
        RecyclerView rv = view.findViewById(R.id.rv_pick);
        rv.setLayoutManager(new LinearLayoutManager(this));
        TextView tvTitle = view.findViewById(R.id.tv_pick_title);
        TextView tvSubtitle = view.findViewById(R.id.tv_pick_subtitle);
        tvTitle.setText(R.string.autostart_pick_title);
        tvSubtitle.setText(R.string.autostart_pick_subtitle);
        final PickAppAdapter adapter = new PickAppAdapter();
        adapter.setData(allApps);
        adapter.setSelected(new HashSet<>(PrefsUtil.getBootApps(this)));
        rv.setAdapter(adapter);
        final EditText etPickSearch = view.findViewById(R.id.et_pick_search);
        final ImageView ivPickClear = view.findViewById(R.id.iv_pick_clear);
        etPickSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) {
                String q = s.toString().trim().toLowerCase(Locale.getDefault());
                ivPickClear.setVisibility(q.isEmpty() ? View.GONE : View.VISIBLE);
                List<AppUtil.AppInfo> filtered = new ArrayList<>();
                for (AppUtil.AppInfo info : allApps) {
                    if (info.name != null && info.name.toLowerCase(Locale.getDefault()).contains(q)) filtered.add(info);
                    else if (info.packageName != null && info.packageName.toLowerCase(Locale.getDefault()).contains(q)) filtered.add(info);
                }
                adapter.setData(filtered);
            }
        });
        ivPickClear.setOnClickListener(v -> etPickSearch.setText(""));
        androidx.appcompat.app.AlertDialog dialog = new androidx.appcompat.app.AlertDialog.Builder(this).setView(view).create();
        dialog.show();
        view.findViewById(R.id.btn_pick_cancel).setOnClickListener(v -> dialog.dismiss());
        view.findViewById(R.id.btn_pick_confirm).setOnClickListener(v -> {
            Set<String> selected = adapter.getSelected();
            PrefsUtil.setBootApps(this, new ArrayList<>(selected));
            loadUserApps();
            dialog.dismiss();
            Toast.makeText(this, R.string.ok, Toast.LENGTH_SHORT).show();
        });
    }

    @Override protected void onResume() {
        super.onResume();
        if (returnedFromDetail && currentTab == TAB_SYSTEM) { returnedFromDetail = false; startScanSystemApps(); }
    }

    @Override protected void onDestroy() {
        super.onDestroy();
        dismissPickLoading();
        dismissToggleLoading();
    }

    private static class ScanTask extends AsyncTask<Void, Void, List<AutostartUtil.AutostartApp>> {
        private final WeakReference<AutostartActivity> ref;
        ScanTask(AutostartActivity act) { this.ref = new WeakReference<>(act); }
        @Override protected List<AutostartUtil.AutostartApp> doInBackground(Void... voids) {
            AutostartActivity act = ref.get();
            if (act == null) return Collections.emptyList();
            try { return AutostartUtil.scanAutostartApps(act); }
            catch (Exception e) { return Collections.emptyList(); }
        }
        @Override protected void onPostExecute(List<AutostartUtil.AutostartApp> data) {
            AutostartActivity act = ref.get();
            if (act == null || act.isFinishing()) return;
            if (data == null) data = Collections.emptyList();
            act.systemAllApps.clear();
            act.systemAllApps.addAll(data);
            act.applySystemFilter();
        }
    }

    private static class ToggleTask extends AsyncTask<Void, Void, Boolean> {
        private final WeakReference<AutostartActivity> ref;
        private final AutostartUtil.AutostartApp app;
        private final boolean wasEnabled;
        ToggleTask(AutostartActivity act, AutostartUtil.AutostartApp app) { this.ref = new WeakReference<>(act); this.app = app; this.wasEnabled = app.enabled; }
        @Override protected Boolean doInBackground(Void... voids) {
            try { return wasEnabled ? AutostartUtil.disableApp(app.packageName) : AutostartUtil.enableApp(app.packageName); }
            catch (Exception e) { return false; }
        }
        @Override protected void onPostExecute(Boolean success) {
            AutostartActivity act = ref.get();
            if (act == null || act.isFinishing()) return;
            act.dismissToggleLoading();
            if (success != null && success) {
                app.enabled = !wasEnabled;
                Toast.makeText(act, act.getString(app.enabled ? R.string.autostart_enable_success : R.string.autostart_disable_success, app.name), Toast.LENGTH_SHORT).show();
                act.applySystemFilter();
            } else { Toast.makeText(act, R.string.autostart_toggle_failed, Toast.LENGTH_LONG).show(); }
        }
    }

    private static class LoadUserAppsTask extends AsyncTask<Void, Void, List<AppUtil.AppInfo>> {
        private final WeakReference<AutostartActivity> ref;
        LoadUserAppsTask(AutostartActivity act) { this.ref = new WeakReference<>(act); }
        @Override protected List<AppUtil.AppInfo> doInBackground(Void... voids) {
            AutostartActivity act = ref.get();
            if (act == null) return Collections.emptyList();
            try {
                List<String> pkgList = PrefsUtil.getBootApps(act);
                PackageManager pm = act.getPackageManager();
                List<AppUtil.AppInfo> result = new ArrayList<>();
                for (String pkg : pkgList) {
                    try {
                        ApplicationInfo ai = pm.getApplicationInfo(pkg, 0);
                        AppUtil.AppInfo info = new AppUtil.AppInfo();
                        info.packageName = pkg;
                        info.name = pm.getApplicationLabel(ai).toString();
                        info.icon = pm.getApplicationIcon(ai);
                        info.launchIntent = pm.getLaunchIntentForPackage(pkg);
                        result.add(info);
                    } catch (PackageManager.NameNotFoundException ignored) {}
                }
                return result;
            } catch (Exception e) { return Collections.emptyList(); }
        }
        @Override protected void onPostExecute(List<AppUtil.AppInfo> data) {
            AutostartActivity act = ref.get();
            if (act == null || act.isFinishing()) return;
            if (data == null) data = Collections.emptyList();
            act.userApps.clear();
            act.userApps.addAll(data);
            act.userAdapter.setData(data);
            act.updateUserTabCount(data.size());
        }
    }

    private static class LoadAllAppsTask extends AsyncTask<Void, Void, List<AppUtil.AppInfo>> {
        private final WeakReference<AutostartActivity> ref;
        LoadAllAppsTask(AutostartActivity act) { this.ref = new WeakReference<>(act); }
        @Override protected List<AppUtil.AppInfo> doInBackground(Void... voids) {
            AutostartActivity act = ref.get();
            if (act == null) return Collections.emptyList();
            try {
                PackageManager pm = act.getPackageManager();
                List<ApplicationInfo> ais = pm.getInstalledApplications(0);
                List<AppUtil.AppInfo> result = new ArrayList<>();
                for (ApplicationInfo ai : ais) {
                    if (pm.getLaunchIntentForPackage(ai.packageName) == null) continue;
                    AppUtil.AppInfo info = new AppUtil.AppInfo();
                    info.packageName = ai.packageName;
                    info.name = pm.getApplicationLabel(ai).toString();
                    info.icon = pm.getApplicationIcon(ai);
                    info.launchIntent = pm.getLaunchIntentForPackage(ai.packageName);
                    result.add(info);
                }
                Collections.sort(result, (a, b) -> a.name.compareToIgnoreCase(b.name));
                return result;
            } catch (Exception e) { return Collections.emptyList(); }
        }
        @Override protected void onPostExecute(List<AppUtil.AppInfo> data) {
            AutostartActivity act = ref.get();
            if (act == null || act.isFinishing()) return;
            act.dismissPickLoading();
            if (data == null) data = Collections.emptyList();
            if (data.isEmpty()) { Toast.makeText(act, R.string.empty_list, Toast.LENGTH_SHORT).show(); return; }
            act.showPickDialogInternal(data);
        }
    }

    private static class SystemAutostartAdapter extends RecyclerView.Adapter<SystemAutostartAdapter.VH> {
        private final List<AutostartUtil.AutostartApp> items = new ArrayList<>();
        private OnItemClickListener listener;
        interface OnItemClickListener { void onItemClick(AutostartUtil.AutostartApp app); void onToggle(AutostartUtil.AutostartApp app); }
        void setListener(OnItemClickListener l) { this.listener = l; }
        void setData(List<AutostartUtil.AutostartApp> data) { items.clear(); if (data != null) items.addAll(data); notifyDataSetChanged(); }
        @NonNull @Override public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) { return new VH(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_autostart, parent, false)); }
        @Override public void onBindViewHolder(@NonNull VH holder, int position) {
            AutostartUtil.AutostartApp app = items.get(position);
            holder.tvName.setText(app.name);
            if (app.icon != null) holder.ivIcon.setImageDrawable(app.icon); else holder.ivIcon.setImageResource(R.drawable.ic_app_default);
            holder.tvSystem.setVisibility(app.system ? View.VISIBLE : View.GONE);
            android.content.Context ctx = holder.itemView.getContext();
            if (app.enabled) {
                holder.tvStatus.setText(R.string.autostart_status_enabled);
                holder.tvStatus.setTextColor(ContextCompat.getColor(ctx, R.color.success));
                holder.tvStatus.setBackgroundResource(R.drawable.bg_status_enabled);
                holder.ivStatusDot.setColorFilter(ContextCompat.getColor(ctx, R.color.success));
                holder.btnToggle.setText(R.string.autostart_disable);
            } else {
                holder.tvStatus.setText(R.string.autostart_status_disabled);
                holder.tvStatus.setTextColor(ContextCompat.getColor(ctx, R.color.text_hint));
                holder.tvStatus.setBackgroundResource(R.drawable.bg_status_disabled);
                holder.ivStatusDot.setColorFilter(ContextCompat.getColor(ctx, R.color.text_hint));
                holder.btnToggle.setText(R.string.autostart_enable);
            }
            holder.tvPkg.setText(app.packageName);
            holder.btnDetail.setOnClickListener(v -> { if (listener != null) listener.onItemClick(app); });
            holder.btnToggle.setOnClickListener(v -> { if (listener != null) listener.onToggle(app); });
        }
        @Override public int getItemCount() { return items.size(); }
        static class VH extends RecyclerView.ViewHolder {
            ImageView ivIcon, ivStatusDot; TextView tvName, tvStatus, tvSystem, tvPkg; Button btnToggle, btnDetail;
            VH(@NonNull View v) { super(v); ivIcon = v.findViewById(R.id.iv_icon); ivStatusDot = v.findViewById(R.id.iv_status_dot); tvName = v.findViewById(R.id.tv_name); tvStatus = v.findViewById(R.id.tv_status); tvSystem = v.findViewById(R.id.tv_system); tvPkg = v.findViewById(R.id.tv_pkg); btnToggle = v.findViewById(R.id.btn_toggle); btnDetail = v.findViewById(R.id.btn_detail); }
        }
    }

    private static class UserBootAdapter extends RecyclerView.Adapter<UserBootAdapter.VH> {
        private final List<AppUtil.AppInfo> items = new ArrayList<>();
        private OnUserAppActionListener listener;
        interface OnUserAppActionListener { void onLaunch(AppUtil.AppInfo info); void onRemove(AppUtil.AppInfo info); }
        void setListener(OnUserAppActionListener l) { this.listener = l; }
        void setData(List<AppUtil.AppInfo> data) { items.clear(); if (data != null) items.addAll(data); notifyDataSetChanged(); }
        @NonNull @Override public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) { return new VH(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_autostart_user, parent, false)); }
        @Override public void onBindViewHolder(@NonNull VH holder, int position) {
            AppUtil.AppInfo info = items.get(position);
            holder.tvIndex.setText(String.valueOf(position + 1));
            holder.tvName.setText(info.name);
            holder.tvPkg.setText(info.packageName);
            if (info.icon != null) holder.ivIcon.setImageDrawable(info.icon); else holder.ivIcon.setImageResource(R.drawable.ic_app_default);
            holder.btnLaunch.setOnClickListener(v -> { if (listener != null) listener.onLaunch(info); });
            holder.btnRemove.setOnClickListener(v -> { if (listener != null) listener.onRemove(info); });
        }
        @Override public int getItemCount() { return items.size(); }
        static class VH extends RecyclerView.ViewHolder {
            ImageView ivIcon; TextView tvIndex, tvName, tvPkg; Button btnLaunch, btnRemove;
            VH(@NonNull View v) { super(v); ivIcon = v.findViewById(R.id.iv_icon); tvIndex = v.findViewById(R.id.tv_index); tvName = v.findViewById(R.id.tv_name); tvPkg = v.findViewById(R.id.tv_pkg); btnLaunch = v.findViewById(R.id.btn_launch); btnRemove = v.findViewById(R.id.btn_remove); }
        }
    }
}