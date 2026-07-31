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
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.carassistant.R;
import com.carassistant.util.FormatUtil;
import com.carassistant.util.StorageUtil;

import java.util.ArrayList;
import java.util.List;

public class StorageAdapter extends RecyclerView.Adapter<StorageAdapter.VH> {

    public interface OnStorageClickListener {
        void onStorageClick(StorageUtil.StorageInfo info);
    }

    private final List<StorageUtil.StorageInfo> items = new ArrayList<>();
    private OnStorageClickListener listener;

    public void setListener(OnStorageClickListener l) { this.listener = l; }

    public void setData(List<StorageUtil.StorageInfo> data) {
        items.clear();
        if (data != null) items.addAll(data);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_storage, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
        StorageUtil.StorageInfo info = items.get(position);
        holder.tvName.setText(info.label != null ? info.label : info.path);
        long used = info.total - info.available;
        int percent = info.total > 0 ? (int) (used * 100 / info.total) : 0;
        holder.tvInfo.setText(FormatUtil.formatSize(used) + " / " + FormatUtil.formatSize(info.total) + "  ·  " + percent + "%");
        holder.pbProgress.setProgress(percent);

        if (info.usb) {
            holder.ivIcon.setImageResource(R.drawable.ic_feature_usb);
            holder.ivIcon.setColorFilter(ContextCompat.getColor(holder.itemView.getContext(), R.color.brand_primary));
        } else if (info.removable) {
            holder.ivIcon.setImageResource(R.drawable.ic_file);
            holder.ivIcon.setColorFilter(ContextCompat.getColor(holder.itemView.getContext(), R.color.warn));
        } else {
            holder.ivIcon.setImageResource(R.drawable.ic_folder);
            holder.ivIcon.setColorFilter(ContextCompat.getColor(holder.itemView.getContext(), R.color.brand_primary));
        }

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onStorageClick(info);
        });
    }

    @Override
    public int getItemCount() { return items.size(); }

    static class VH extends RecyclerView.ViewHolder {
        ImageView ivIcon;
        TextView tvName;
        TextView tvInfo;
        ProgressBar pbProgress;

        VH(@NonNull View v) {
            super(v);
            ivIcon = v.findViewById(R.id.iv_storage_icon);
            tvName = v.findViewById(R.id.tv_storage_name);
            tvInfo = v.findViewById(R.id.tv_storage_info);
            pbProgress = v.findViewById(R.id.pb_storage);
        }
    }
}
