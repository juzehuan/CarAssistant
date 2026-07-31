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

import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.carassistant.R;
import com.carassistant.util.AppUtil;

import java.util.ArrayList;
import java.util.List;

public class AppAdapter extends RecyclerView.Adapter<AppAdapter.VH> {

    public interface OnAppActionListener {
        void onOpen(AppUtil.AppInfo info);
        void onUninstall(AppUtil.AppInfo info);
        void onDetail(AppUtil.AppInfo info);
    }

    private final List<AppUtil.AppInfo> items = new ArrayList<>();
    private OnAppActionListener listener;

    public void setListener(OnAppActionListener l) { this.listener = l; }

    public void setData(List<AppUtil.AppInfo> data) {
        items.clear();
        if (data != null) items.addAll(data);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_app, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
        AppUtil.AppInfo info = items.get(position);
        holder.tvName.setText(info.name);
        StringBuilder sb = new StringBuilder();
        sb.append("版本 ").append(info.versionName);
        if (info.codeSize > 0) sb.append("  ·  ").append(com.carassistant.util.FormatUtil.formatSize(info.codeSize));
        if (info.system) sb.append("  ·  系统应用");
        holder.tvSize.setText(sb.toString());
        if (info.icon != null) holder.ivIcon.setImageDrawable(info.icon);
        else holder.ivIcon.setImageResource(R.drawable.ic_app_default);

        // 系统应用不允许卸载
        holder.btnUninstall.setVisibility(info.system ? View.GONE : View.VISIBLE);

        holder.itemView.setOnLongClickListener(v -> {
            if (listener != null) listener.onDetail(info);
            return true;
        });

        holder.btnOpen.setOnClickListener(v -> {
            if (listener != null) listener.onOpen(info);
        });
        holder.btnUninstall.setOnClickListener(v -> {
            if (listener != null) listener.onUninstall(info);
        });
        holder.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onDetail(info);
        });
    }

    @Override
    public int getItemCount() { return items.size(); }

    static class VH extends RecyclerView.ViewHolder {
        ImageView ivIcon;
        TextView tvName;
        TextView tvSize;
        Button btnOpen;
        Button btnUninstall;

        VH(@NonNull View v) {
            super(v);
            ivIcon = v.findViewById(R.id.iv_app_icon);
            tvName = v.findViewById(R.id.tv_app_name);
            tvSize = v.findViewById(R.id.tv_app_size);
            btnOpen = v.findViewById(R.id.btn_app_open);
            btnUninstall = v.findViewById(R.id.btn_app_uninstall);
        }
    }
}
