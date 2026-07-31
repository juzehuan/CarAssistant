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
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.carassistant.R;
import com.carassistant.adapter.FileAdapter;
import com.carassistant.adapter.StorageAdapter;
import com.carassistant.util.FileUtil;
import com.carassistant.util.PermissionUtil;
import com.carassistant.util.StorageUtil;

import java.io.File;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 文件管理：内部存储 / SD 卡 / U 盘 三类入口
 * 点击存储卷进入对应路径，支持目录浏览与文件打开
 */
public class FileFragment extends Fragment {

    private TextView tvTitle, tvPath, tvEmpty, tvNavPath;
    private LinearLayout llNavBar;
    private ImageView ivNavBack, ivSearchClear;
    private EditText etSearch;
    private RecyclerView rvStorage, rvFiles;
    private StorageAdapter storageAdapter;
    private FileAdapter fileAdapter;

    private File currentDir = null;
    /** 进入存储卷时的根目录，用于"返回上一级"判定 */
    private File rootDir = null;
    /** 进入存储卷时的标签（如"内部存储"） */
    private String rootLabel = null;
    /** 最近一次加载到的全部文件（用于搜索过滤） */
    private List<FileUtil.FileItem> allItems = new ArrayList<>();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_file, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        tvTitle = view.findViewById(R.id.tv_file_title);
        tvPath = view.findViewById(R.id.tv_file_path);
        tvEmpty = view.findViewById(R.id.tv_file_empty);
        tvNavPath = view.findViewById(R.id.tv_nav_path);
        llNavBar = view.findViewById(R.id.ll_nav_bar);
        ivNavBack = view.findViewById(R.id.iv_nav_back);
        etSearch = view.findViewById(R.id.et_file_search);
        ivSearchClear = view.findViewById(R.id.iv_file_search_clear);
        rvStorage = view.findViewById(R.id.rv_storage);
        rvFiles = view.findViewById(R.id.rv_files);

        rvStorage.setLayoutManager(new LinearLayoutManager(requireContext()));
        storageAdapter = new StorageAdapter();
        rvStorage.setAdapter(storageAdapter);
        storageAdapter.setListener(info -> {
            rootLabel = info.label != null ? info.label : new File(info.path).getName();
            ensurePermissionAndOpen(new File(info.path));
        });

        rvFiles.setLayoutManager(new LinearLayoutManager(requireContext()));
        fileAdapter = new FileAdapter();
        rvFiles.setAdapter(fileAdapter);
        fileAdapter.setListener(new FileAdapter.OnFileClickListener() {
            @Override
            public void onFileClick(FileUtil.FileItem item) {
                if (item.directory) {
                    openDirectory(item.file);
                } else if (FileUtil.isApk(item.file)) {
                    confirmInstallApk(item.file);
                } else {
                    FileUtil.openFile(requireContext(), item.file);
                }
            }
            @Override
            public boolean onFileLongClick(FileUtil.FileItem item) {
                showFileOptions(item);
                return true;
            }
        });

        ivNavBack.setOnClickListener(v -> onBackPressed());

        // 搜索框：实时过滤当前目录文件
        etSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) {
                applyFileSearch(s.toString().trim());
            }
        });
        ivSearchClear.setOnClickListener(v -> etSearch.setText(""));

        refreshStorages();
    }

    /**
     * 在当前目录文件列表上应用搜索过滤。
     * - 空关键字：还原原始列表
     * - 非空：按文件名模糊匹配
     */
    private void applyFileSearch(String query) {
        if (ivSearchClear != null) {
            ivSearchClear.setVisibility(query.isEmpty() ? View.GONE : View.VISIBLE);
        }
        if (allItems.isEmpty()) return;

        if (query.isEmpty()) {
            fileAdapter.setData(allItems);
            tvEmpty.setVisibility(allItems.isEmpty() ? View.VISIBLE : View.GONE);
            rvFiles.setVisibility(allItems.isEmpty() ? View.GONE : View.VISIBLE);
            return;
        }

        String q = query.toLowerCase(Locale.getDefault());
        List<FileUtil.FileItem> filtered = new ArrayList<>();
        for (FileUtil.FileItem item : allItems) {
            if (item.name != null && item.name.toLowerCase(Locale.getDefault()).contains(q)) {
                filtered.add(item);
            }
        }
        fileAdapter.setData(filtered);
        if (filtered.isEmpty()) {
            tvEmpty.setText(R.string.file_search_no_match);
            tvEmpty.setVisibility(View.VISIBLE);
            rvFiles.setVisibility(View.GONE);
        } else {
            tvEmpty.setText(R.string.file_empty);
            tvEmpty.setVisibility(View.GONE);
            rvFiles.setVisibility(View.VISIBLE);
        }
    }

    private void refreshStorages() {
        List<StorageUtil.StorageInfo> list = StorageUtil.getAllStorages(requireContext());
        // 仅显示存在且有容量的存储
        list.removeIf(s -> s.total <= 0);
        storageAdapter.setData(list);
        // 显示存储列表，隐藏文件列表与导航栏
        rvStorage.setVisibility(View.VISIBLE);
        rvFiles.setVisibility(View.GONE);
        tvEmpty.setVisibility(View.GONE);
        llNavBar.setVisibility(View.GONE);
        // 回到存储列表时清空搜索框与缓存
        if (etSearch != null && etSearch.getText().length() > 0) {
            etSearch.setText("");
        }
        allItems.clear();
        tvPath.setText("共 " + list.size() + " 个存储");
    }

    private void ensurePermissionAndOpen(File dir) {
        if (!PermissionUtil.hasStorageAccess(requireContext())) {
            // 引导授权
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                PermissionUtil.requestAllFilesAccess(requireContext());
            } else {
                PermissionUtil.requestLegacyStorage(requireActivity(), 0x102);
            }
            Toast.makeText(requireContext(), R.string.permission_storage_required, Toast.LENGTH_LONG).show();
            return;
        }
        if (!dir.exists() || !dir.canRead()) {
            Toast.makeText(requireContext(), "无权访问该目录", Toast.LENGTH_SHORT).show();
            return;
        }
        rootDir = dir;
        openDirectory(dir);
    }

    private void openDirectory(File dir) {
        currentDir = dir;
        // 进入新目录时清空搜索关键字，避免上次过滤残留
        if (etSearch != null && etSearch.getText().length() > 0) {
            etSearch.setText("");
        }
        new ListTask(this, dir).execute();
    }

    /** 计算并显示当前路径的友好形式：rootLabel / sub1 / sub2 ... */
    private void updateNavBar(File dir) {
        llNavBar.setVisibility(View.VISIBLE);
        if (rootDir == null || rootLabel == null) {
            tvNavPath.setText(dir.getAbsolutePath());
            return;
        }
        String root = rootDir.getAbsolutePath();
        String abs = dir.getAbsolutePath();
        if (abs.equals(root)) {
            tvNavPath.setText(rootLabel);
        } else if (abs.startsWith(root)) {
            String sub = abs.substring(root.length());
            if (sub.startsWith("/")) sub = sub.substring(1);
            tvNavPath.setText(rootLabel + " / " + sub.replace("/", " / "));
        } else {
            tvNavPath.setText(abs);
        }
    }

    private void showFileOptions(FileUtil.FileItem item) {
        boolean isApk = !item.directory && FileUtil.isApk(item.file);
        CharSequence[] labels = isApk ? new CharSequence[]{"安装", "详情"} : new CharSequence[]{"打开", "详情"};
        new AlertDialog.Builder(requireContext())
                .setTitle(item.name)
                .setItems(labels, (d, w) -> {
                    if (w == 0) {
                        if (item.directory) openDirectory(item.file);
                        else if (isApk) confirmInstallApk(item.file);
                        else FileUtil.openFile(requireContext(), item.file);
                    } else {
                        String msg = "路径：" + item.file.getAbsolutePath() +
                                "\n大小：" + com.carassistant.util.FormatUtil.formatSize(item.size) +
                                "\n修改时间：" + FileUtil.formatTime(item.lastModified);
                        new AlertDialog.Builder(requireContext())
                                .setTitle(item.name)
                                .setMessage(msg)
                                .setPositiveButton(R.string.ok, null)
                                .show();
                    }
                })
                .show();
    }

    /** APK 安装二次确认：使用自定义 ConfirmDialog */
    private void confirmInstallApk(File file) {
        ConfirmDialog.show(requireContext(),
                R.drawable.ic_apk, ConfirmDialog.TYPE_PRIMARY,
                getString(R.string.file_install_apk),
                getString(R.string.file_install_apk_confirm, file.getName()),
                getString(R.string.file_install_apk),
                () -> FileUtil.installApk(requireContext(), file));
    }

    private static class ListTask extends AsyncTask<Void, Void, List<FileUtil.FileItem>> {
        private final WeakReference<FileFragment> ref;
        private final File dir;

        ListTask(FileFragment f, File dir) {
            this.ref = new WeakReference<>(f);
            this.dir = dir;
        }

        @Override
        protected List<FileUtil.FileItem> doInBackground(Void... voids) {
            return FileUtil.listFiles(dir);
        }

        @Override
        protected void onPostExecute(List<FileUtil.FileItem> data) {
            FileFragment f = ref.get();
            if (f == null || !f.isAdded()) return;
            f.rvStorage.setVisibility(View.GONE);
            f.rvFiles.setVisibility(View.VISIBLE);
            f.allItems = data != null ? data : new ArrayList<>();
            // 若搜索框有内容，沿用过滤；否则直接展示
            String q = f.etSearch != null ? f.etSearch.getText().toString().trim() : "";
            if (!q.isEmpty()) {
                f.applyFileSearch(q);
            } else {
                f.fileAdapter.setData(f.allItems);
                f.tvEmpty.setVisibility(f.allItems.isEmpty() ? View.VISIBLE : View.GONE);
            }
            f.tvPath.setText(dir.getAbsolutePath());
            f.updateNavBar(dir);
        }
    }

    /**
     * 处理返回键：返回上一级目录或回到存储列表
     */
    public boolean onBackPressed() {
        if (currentDir == null) return false;
        if (rootDir != null && currentDir.equals(rootDir)) {
            // 回到存储列表
            currentDir = null;
            rootDir = null;
            rootLabel = null;
            refreshStorages();
            return true;
        }
        File parent = currentDir.getParentFile();
        if (parent != null && parent.canRead()) {
            openDirectory(parent);
            return true;
        } else {
            currentDir = null;
            rootDir = null;
            rootLabel = null;
            refreshStorages();
            return true;
        }
    }
}
