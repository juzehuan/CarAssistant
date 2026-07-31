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

package com.carassistant.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.carassistant.R;
import com.carassistant.util.FileUtil;

import java.util.ArrayList;
import java.util.List;

public class FileAdapter extends RecyclerView.Adapter<FileAdapter.VH> {

    public interface OnFileClickListener {
        void onFileClick(FileUtil.FileItem item);
        boolean onFileLongClick(FileUtil.FileItem item);
    }

    private final List<FileUtil.FileItem> items = new ArrayList<>();
    private OnFileClickListener listener;

    public void setListener(OnFileClickListener l) { this.listener = l; }

    public void setData(List<FileUtil.FileItem> data) {
        items.clear();
        if (data != null) items.addAll(data);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_file, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
        FileUtil.FileItem item = items.get(position);
        holder.tvName.setText(item.name);
        String meta;
        if (item.directory) {
            meta = FileUtil.formatTime(item.lastModified);
        } else {
            meta = FileUtil.formatTime(item.lastModified) + "  "
                    + com.carassistant.util.FormatUtil.formatSize(item.size);
        }
        // APK 文件额外显示"安装包"标签
        if (!item.directory && FileUtil.isApk(item.file)) {
            meta = "安装包  ·  " + meta;
        }
        holder.tvMeta.setText(meta);
        if (item.directory) {
            holder.ivIcon.setImageResource(R.drawable.ic_folder);
            holder.ivIcon.setColorFilter(ContextCompat.getColor(holder.itemView.getContext(), R.color.brand_primary));
        } else if (FileUtil.isApk(item.file)) {
            holder.ivIcon.setImageResource(R.drawable.ic_apk);
            holder.ivIcon.setColorFilter(null);
        } else {
            holder.ivIcon.setImageResource(R.drawable.ic_file_generic);
            holder.ivIcon.setColorFilter(ContextCompat.getColor(holder.itemView.getContext(), R.color.text_hint));
        }

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onFileClick(item);
        });
        holder.itemView.setOnLongClickListener(v -> listener != null && listener.onFileLongClick(item));
    }

    @Override
    public int getItemCount() { return items.size(); }

    static class VH extends RecyclerView.ViewHolder {
        ImageView ivIcon;
        TextView tvName;
        TextView tvMeta;

        VH(@NonNull View v) {
            super(v);
            ivIcon = v.findViewById(R.id.iv_file_icon);
            tvName = v.findViewById(R.id.tv_file_name);
            tvMeta = v.findViewById(R.id.tv_file_meta);
        }
    }
}
