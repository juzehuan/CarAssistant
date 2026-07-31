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

import android.os.AsyncTask;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.carassistant.R;
import com.carassistant.adapter.JunkAdapter;
import com.carassistant.util.CleanUtil;
import com.carassistant.util.FormatUtil;
import com.carassistant.util.MemoryUtil;
import com.carassistant.util.PrefsUtil;
import com.carassistant.util.ShellUtil;

import java.lang.ref.WeakReference;
import java.util.List;
import java.util.Set;

/**
 * 清理优化：内存加速 + 垃圾清理
 *
 * Root 感知策略：
 * - 进入页面时后台检测 Root，结果显示在头部徽章
 * - 垃圾扫描：Root 模式额外扫描其他应用缓存
 * - 内存加速：Root 模式用 am force-stop 强制结束正在运行的第三方应用；非 Root 回退到 killBackgroundProcesses
 * - UI 文案诚实标注当前模式与实际效果
 */
public class CleanFragment extends Fragment {

    private TextView tvSummary, tvMemTotal, tvMemAvail, tvMemRunning, tvMemPercent, tvMemUsed, tvJunkTotal, tvJunkCount;
    private ProgressBar pbMem;
    private Button btnBoost, btnClean;
    private RecyclerView rvJunk;
    private JunkAdapter junkAdapter;
    private View badgeRoot;

    /** Root 检测结果，由 RootCheckTask 写入，UI 线程读取 */
    private volatile boolean hasRoot = false;
    /** Root 检测是否已完成 */
    private volatile boolean rootChecked = false;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_clean, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        tvSummary = view.findViewById(R.id.tv_clean_summary);
        tvMemTotal = view.findViewById(R.id.tv_memory_total);
        tvMemAvail = view.findViewById(R.id.tv_memory_avail);
        tvMemRunning = view.findViewById(R.id.tv_memory_running);
        tvMemPercent = view.findViewById(R.id.tv_memory_percent);
        tvMemUsed = view.findViewById(R.id.tv_memory_used);
        tvJunkTotal = view.findViewById(R.id.tv_junk_total);
        tvJunkCount = view.findViewById(R.id.tv_junk_count);
        pbMem = view.findViewById(R.id.pb_memory);
        btnBoost = view.findViewById(R.id.btn_boost);
        btnClean = view.findViewById(R.id.btn_clean);
        rvJunk = view.findViewById(R.id.rv_junk);
        badgeRoot = view.findViewById(R.id.badge_root);

        rvJunk.setLayoutManager(new LinearLayoutManager(requireContext()));
        rvJunk.setNestedScrollingEnabled(false);
        junkAdapter = new JunkAdapter();
        rvJunk.setAdapter(junkAdapter);

        btnBoost.setOnClickListener(v -> doBoost());
        btnClean.setOnClickListener(v -> doClean());

        refreshMemory();
        // 先用非 Root 模式扫描，避免 Root 检测期间页面空白
        new ScanTask(this).execute();
        // 后台检测 Root，完成后重新扫描
        new RootCheckTask(this).execute();
    }

    /** 后台检测 Root 权限，避免阻塞 UI */
    private static class RootCheckTask extends AsyncTask<Void, Void, Boolean> {
        private final WeakReference<CleanFragment> ref;

        RootCheckTask(CleanFragment f) { this.ref = new WeakReference<>(f); }

        @Override
        protected Boolean doInBackground(Void... voids) {
            return ShellUtil.hasRoot();
        }

        @Override
        protected void onPostExecute(Boolean result) {
            CleanFragment f = ref.get();
            if (f == null || !f.isAdded()) return;
            f.hasRoot = Boolean.TRUE.equals(result);
            f.rootChecked = true;
            // 显示 Root 徽章
            f.badgeRoot.setVisibility(f.hasRoot ? View.VISIBLE : View.GONE);
            // Root 检测完成后重新扫描，纳入其他应用缓存
            new ScanTask(f).execute();
        }
    }

    private void refreshMemory() {
        long total = MemoryUtil.getTotalMemory(requireContext());
        long avail = MemoryUtil.getAvailableMemory(requireContext());
        long used = total - avail;
        int percent = MemoryUtil.getUsedPercent(requireContext());
        int running = MemoryUtil.getRunningProcessCount(requireContext());

        tvMemTotal.setText(getString(R.string.memory_total, FormatUtil.formatSize(total)));
        tvMemAvail.setText(FormatUtil.formatSize(avail));
        tvMemUsed.setText(FormatUtil.formatSize(used));
        tvMemPercent.setText(percent + "%");
        tvMemRunning.setText(getString(R.string.memory_running, running));
        pbMem.setProgress(percent);
    }

    private void doBoost() {
        btnBoost.setText(R.string.memory_boosting);
        btnBoost.setEnabled(false);
        new Thread(() -> {
            long before = MemoryUtil.getAvailableMemory(requireContext());
            java.util.Set<String> whitelist = PrefsUtil.getWhitelist(requireContext());

            int stoppedCount = 0;
            if (hasRoot) {
                // Root 模式：仅强制结束正在运行的第三方后台应用（避免无差别 force-stop 全部已安装应用）
                List<String> runningPkgs = MemoryUtil.getRunningKillablePackages(requireContext(), whitelist);
                if (!runningPkgs.isEmpty()) {
                    stoppedCount = MemoryUtil.forceStopPackages(runningPkgs);
                }
            } else {
                // 非 Root：系统 API 受限，仅作尽力而为的清理
                List<String> pkgs = MemoryUtil.getKillablePackages(requireContext(), whitelist);
                MemoryUtil.killBackgroundProcesses(requireContext(), pkgs);
            }
            // 等待系统回收内存
            try { Thread.sleep(800); } catch (InterruptedException ignored) {}
            long after = MemoryUtil.getAvailableMemory(requireContext());
            long released = Math.max(0, after - before);
            final int finalStopped = stoppedCount;
            requireActivity().runOnUiThread(() -> {
                btnBoost.setText(R.string.memory_boost);
                btnBoost.setEnabled(true);
                refreshMemory();
                String msg;
                if (hasRoot) {
                    if (finalStopped > 0) {
                        msg = getString(R.string.memory_root_done, finalStopped, FormatUtil.formatSize(released));
                    } else {
                        msg = getString(R.string.memory_no_apps_to_kill);
                    }
                } else {
                    msg = getString(R.string.memory_no_root_done, FormatUtil.formatSize(released));
                }
                Toast.makeText(requireContext(), msg, Toast.LENGTH_LONG).show();
            });
        }).start();
    }

    private void doClean() {
        // 根据是否 Root 显示不同确认文案
        String msg = hasRoot ? getString(R.string.clean_confirm_root)
                             : getString(R.string.clean_confirm_no_root);
        ConfirmDialog.show(requireContext(),
                R.drawable.ic_clean, ConfirmDialog.TYPE_WARN,
                getString(R.string.clean_clean),
                msg,
                getString(R.string.clean_clean),
                () -> doCleanInternal());
    }

    private void doCleanInternal() {
        // 复制一份待清理数据，避免子线程访问适配器数据时发生并发修改
        final List<CleanUtil.JunkGroup> groups = new java.util.ArrayList<>(junkAdapter.getData());
        btnClean.setText(R.string.clean_scaning);
        btnClean.setEnabled(false);
        new Thread(() -> {
            // 检测是否含 root 专属项
            boolean hasRootItems = false;
            for (CleanUtil.JunkGroup g : groups) {
                if (g.selected && g.rootOnly && !g.rootPaths.isEmpty()) {
                    hasRootItems = true;
                    break;
                }
            }
            final long cleaned = CleanUtil.clean(groups);
            // 如果有 root 项但当前无 Root 权限，clean() 内部 rm 会失败，cleaned 为 0
            // 通过对比选中总大小判断是否部分失败
            long selectedSize = 0;
            for (CleanUtil.JunkGroup g : groups) {
                if (g.selected) selectedSize += g.size;
            }
            final boolean rootFailed = hasRootItems && !hasRoot && selectedSize > 0 && cleaned == 0;
            requireActivity().runOnUiThread(() -> {
                btnClean.setText(R.string.clean_clean);
                btnClean.setEnabled(true);
                new ScanTask(this).execute();
                if (rootFailed) {
                    Toast.makeText(requireContext(),
                            R.string.clean_root_failed,
                            Toast.LENGTH_LONG).show();
                } else {
                    Toast.makeText(requireContext(),
                            getString(R.string.clean_done, FormatUtil.formatSize(cleaned)),
                            Toast.LENGTH_LONG).show();
                }
            });
        }).start();
    }

    private static class ScanTask extends AsyncTask<Void, Void, List<CleanUtil.JunkGroup>> {
        private final WeakReference<CleanFragment> ref;

        ScanTask(CleanFragment f) { this.ref = new WeakReference<>(f); }

        @Override
        protected void onPreExecute() {
            CleanFragment f = ref.get();
            if (f != null && f.isAdded()) {
                f.tvSummary.setText(R.string.clean_scaning);
            }
        }

        @Override
        protected List<CleanUtil.JunkGroup> doInBackground(Void... voids) {
            CleanFragment f = ref.get();
            if (f == null || f.getContext() == null) return null;
            // 使用当前 Root 状态进行扫描
            return CleanUtil.scan(f.requireContext(), f.hasRoot);
        }

        @Override
        protected void onPostExecute(List<CleanUtil.JunkGroup> groups) {
            CleanFragment f = ref.get();
            if (f == null || !f.isAdded() || groups == null) return;
            long total = 0;
            for (CleanUtil.JunkGroup g : groups) total += g.size;
            f.junkAdapter.setData(groups);
            f.tvJunkTotal.setText(FormatUtil.formatSize(total));
            f.tvJunkCount.setText("共 " + groups.size() + " 项");
            if (total > 0) {
                // 诚实标注当前模式
                if (f.hasRoot) {
                    f.tvSummary.setText(f.getString(R.string.clean_root_summary_done, FormatUtil.formatSize(total)));
                } else {
                    f.tvSummary.setText(f.getString(R.string.clean_summary_done, FormatUtil.formatSize(total)));
                }
            } else {
                // 无垃圾时，若已检测过 Root 且无 Root，提示用户
                if (f.rootChecked && !f.hasRoot) {
                    f.tvSummary.setText(R.string.clean_no_root_summary);
                } else {
                    f.tvSummary.setText(R.string.clean_empty);
                }
            }
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        refreshMemory();
    }
}
