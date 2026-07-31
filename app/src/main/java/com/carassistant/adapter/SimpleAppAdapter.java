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
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.carassistant.R;
import com.carassistant.util.AppUtil;

import java.util.ArrayList;
import java.util.List;

/**
 * 简单的应用列表 Adapter（图标 + 名称 + 移除按钮）
 * 用于悬浮球应用列表与白名单列表
 */
public class SimpleAppAdapter extends RecyclerView.Adapter<SimpleAppAdapter.VH> {

    public interface OnRemoveListener {
        void onRemove(int position, AppUtil.AppInfo info);
    }

    private final List<AppUtil.AppInfo> items = new ArrayList<>();
    private OnRemoveListener listener;

    public void setListener(OnRemoveListener l) { this.listener = l; }

    public void setData(List<AppUtil.AppInfo> data) {
        items.clear();
        if (data != null) items.addAll(data);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_simple_app, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
        AppUtil.AppInfo info = items.get(position);
        if (info.icon != null) holder.ivIcon.setImageDrawable(info.icon);
        else holder.ivIcon.setImageResource(R.drawable.ic_app_default);
        holder.tvName.setText(info.name);
        holder.btnRemove.setOnClickListener(v -> {
            if (listener != null) listener.onRemove(holder.getBindingAdapterPosition(), info);
        });
    }

    @Override
    public int getItemCount() { return items.size(); }

    static class VH extends RecyclerView.ViewHolder {
        ImageView ivIcon;
        TextView tvName;
        Button btnRemove;

        VH(@NonNull View v) {
            super(v);
            ivIcon = v.findViewById(R.id.iv_icon);
            tvName = v.findViewById(R.id.tv_name);
            btnRemove = v.findViewById(R.id.btn_remove);
        }
    }
}
