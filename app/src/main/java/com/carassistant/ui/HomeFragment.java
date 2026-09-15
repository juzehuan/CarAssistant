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

package com.carassistant.ui;

import android.content.res.Configuration;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.carassistant.MainActivity;
import com.carassistant.R;
import com.carassistant.adapter.FeatureAdapter;
import com.carassistant.util.GridSpacingItemDecoration;
import com.carassistant.util.MemoryUtil;
import com.carassistant.util.StorageUtil;

import java.util.ArrayList;
import java.util.List;

public class HomeFragment extends Fragment {

    private TextView tvStorage, tvMemory;
    private ProgressBar pbStorage, pbMemory;
    private RecyclerView rvFeatures;
    private FeatureAdapter featureAdapter;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_home, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        tvStorage = view.findViewById(R.id.tv_storage_info);
        tvMemory = view.findViewById(R.id.tv_memory_info);
        pbStorage = view.findViewById(R.id.pb_storage);
        pbMemory = view.findViewById(R.id.pb_memory);
        rvFeatures = view.findViewById(R.id.rv_features);
        setupFeatureGrid(view);

        // 设置入口（内存清理白名单 / 退出应用）
        view.findViewById(R.id.btn_settings).setOnClickListener(v ->
                startActivity(new android.content.Intent(requireContext(),
                        com.carassistant.SettingsActivity.class)));

        refreshStats();
    }

    /** 设置功能宫格：RecyclerView + GridLayoutManager 动态列数 */
    private void setupFeatureGrid(View root) {
        featureAdapter = new FeatureAdapter(requireContext());

        // 动态计算列数：根据屏幕宽度 / 卡片最小宽度
        int spanCount = calculateSpanCount();
        GridLayoutManager lm = new GridLayoutManager(requireContext(), spanCount);
        rvFeatures.setLayoutManager(lm);

        // 间距装饰
        int spacing = getResources().getDimensionPixelSize(R.dimen.feature_card_spacing);
        rvFeatures.addItemDecoration(new GridSpacingItemDecoration(spanCount, spacing, false));
        rvFeatures.setAdapter(featureAdapter);

        // 构建功能项：核心六大功能 + 内嵌的方控映射入口
        List<FeatureAdapter.FeatureItem> items = new ArrayList<>();
        items.add(new FeatureAdapter.FeatureItem(
                R.drawable.ic_feature_clean, R.string.home_feature_clean, R.color.icon_bg_clean,
                v -> switchTab(R.id.nav_clean)));
        items.add(new FeatureAdapter.FeatureItem(
                R.drawable.ic_feature_app, R.string.home_feature_app, R.color.icon_bg_app,
                v -> switchTab(R.id.nav_app)));
        // 文件管理与 U 盘管理已合并：FileFragment 顶部展示所有存储卷（内部 / SD / U 盘）入口
        items.add(new FeatureAdapter.FeatureItem(
                R.drawable.ic_feature_file, R.string.home_feature_file, R.color.icon_bg_file,
                v -> switchTab(R.id.nav_file)));
        items.add(new FeatureAdapter.FeatureItem(
                R.drawable.ic_feature_monitor, R.string.home_feature_monitor, R.color.icon_bg_monitor,
                v -> startActivity(new android.content.Intent(requireContext(), MonitorActivity.class))));
        items.add(new FeatureAdapter.FeatureItem(
                R.drawable.ic_feature_autostart, R.string.home_feature_autostart, R.color.icon_bg_autostart,
                v -> startActivity(new android.content.Intent(requireContext(), AutostartActivity.class))));
        items.add(new FeatureAdapter.FeatureItem(
                R.drawable.ic_feature_device, R.string.home_feature_device, R.color.icon_bg_device,
                v -> startActivity(new android.content.Intent(requireContext(), DeviceInfoActivity.class))));
        // 方控映射：随包内嵌的第三方应用，未安装则拉起安装器，已安装则直接打开
        items.add(new FeatureAdapter.FeatureItem(
                R.drawable.ic_feature_keymap, R.string.home_feature_keymap, R.color.icon_bg_keymap,
                v -> com.carassistant.util.KeyMapLauncher.open(requireActivity())));
        featureAdapter.setItems(items);
    }

    /**
     * 根据屏幕宽度动态计算列数
     * 公式：列数 = (屏幕宽度 - 边距) / (卡片最小宽度 + 间距)
     */
    private int calculateSpanCount() {
        DisplayMetrics dm = getResources().getDisplayMetrics();
        int screenWidth = dm.widthPixels;
        int cardMinWidth = getResources().getDimensionPixelSize(R.dimen.feature_card_min_width);
        int spacing = getResources().getDimensionPixelSize(R.dimen.feature_card_spacing);
        int pagePadding = getResources().getDimensionPixelSize(R.dimen.home_card_margin) * 2;
        int availableWidth = screenWidth - pagePadding;
        int spanCount = availableWidth / (cardMinWidth + spacing);
        // 限制范围：手机 3 列，大屏最多 6 列
        if (spanCount < 3) spanCount = 3;
        if (spanCount > 6) spanCount = 6;
        return spanCount;
    }

    private void switchTab(int tabId) {
        if (getActivity() instanceof MainActivity) {
            com.google.android.material.bottomnavigation.BottomNavigationView nav =
                    getActivity().findViewById(R.id.bottom_nav);
            nav.setSelectedItemId(tabId);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (!isAdded() || getActivity() == null) return;
        refreshStats();
    }

    private void refreshStats() {
        if (!isAdded() || getActivity() == null) return;
        final android.content.Context ctx = requireContext().getApplicationContext();
        getActivity().runOnUiThread(() -> {
            if (!isAdded()) return;
            // 存储：取内部存储
            java.io.File internal = StorageUtil.getInternalStorage();
            if (internal != null && tvStorage != null) {
                long total = StorageUtil.getTotalSize(internal);
                long avail = StorageUtil.getAvailableSize(internal);
                long used = total - avail;
                tvStorage.setText(getString(R.string.home_storage_used,
                        com.carassistant.util.FormatUtil.formatSize(used),
                        com.carassistant.util.FormatUtil.formatSize(total)));
                pbStorage.setProgress(total > 0 ? (int) (used * 100 / total) : 0);
            }
            // 内存
            if (tvMemory != null) {
                long memTotal = MemoryUtil.getTotalMemory(ctx);
                long memAvail = MemoryUtil.getAvailableMemory(ctx);
                long memUsed = memTotal - memAvail;
                tvMemory.setText(getString(R.string.home_storage_used,
                            com.carassistant.util.FormatUtil.formatSize(memUsed),
                            com.carassistant.util.FormatUtil.formatSize(memTotal)));
                pbMemory.setProgress(memTotal > 0 ? (int) (memUsed * 100 / memTotal) : 0);
            }
        });
    }

    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        // 屏幕旋转时重新计算列数
        if (rvFeatures != null && rvFeatures.getLayoutManager() instanceof GridLayoutManager) {
            int spanCount = calculateSpanCount();
            ((GridLayoutManager) rvFeatures.getLayoutManager()).setSpanCount(spanCount);
        }
    }
}
