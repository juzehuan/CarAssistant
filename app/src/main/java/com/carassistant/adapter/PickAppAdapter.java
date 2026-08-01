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
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.carassistant.R;
import com.carassistant.util.AppUtil;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 选择应用对话框列表 Adapter（多选）
 * 通过 selectedSet 记录已选中的包名
 */
public class PickAppAdapter extends RecyclerView.Adapter<PickAppAdapter.VH> {

    private final List<AppUtil.AppInfo> items = new ArrayList<>();
    private final Set<String> selected = new HashSet<>();
    private OnAppClickListener clickListener;
    private OnSelectedChangeListener selectedChangeListener;

    /** 单选点击回调（用于单选场景） */
    public interface OnAppClickListener {
        void onAppClick(int position, AppUtil.AppInfo info);
    }

    /** 多选模式下选中集合变化回调（用于刷新外部 UI，如按钮文字） */
    public interface OnSelectedChangeListener {
        void onSelectedChanged(int selectedCount);
    }

    public void setOnAppClickListener(OnAppClickListener l) {
        this.clickListener = l;
    }

    public void setOnSelectedChangeListener(OnSelectedChangeListener l) {
        this.selectedChangeListener = l;
    }

    public void setData(List<AppUtil.AppInfo> data) {
        items.clear();
        if (data != null) items.addAll(data);
        notifyDataSetChanged();
    }

    /** 预设已选中的包名集合 */
    public void setSelected(Set<String> preset) {
        selected.clear();
        if (preset != null) selected.addAll(preset);
        notifyDataSetChanged();
    }

    /** 获取已选中的包名集合 */
    public Set<String> getSelected() {
        return new HashSet<>(selected);
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_pick_app, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
        AppUtil.AppInfo info = items.get(position);
        if (info.icon != null) holder.ivIcon.setImageDrawable(info.icon);
        else holder.ivIcon.setImageResource(R.drawable.ic_app_default);
        holder.tvName.setText(info.name);
        boolean isSelected = selected.contains(info.packageName);
        holder.cb.setChecked(isSelected);
        holder.itemView.setOnClickListener(v -> {
            // 单选模式优先
            if (clickListener != null) {
                clickListener.onAppClick(position, info);
                return;
            }
            // 多选模式：切换选中状态
            boolean next = !holder.cb.isChecked();
            holder.cb.setChecked(next);
            if (next) selected.add(info.packageName);
            else selected.remove(info.packageName);
            // 通知外部 UI 刷新（如确定按钮文字）
            if (selectedChangeListener != null) {
                selectedChangeListener.onSelectedChanged(selected.size());
            }
        });
    }

    @Override
    public int getItemCount() { return items.size(); }

    static class VH extends RecyclerView.ViewHolder {
        ImageView ivIcon;
        TextView tvName;
        CheckBox cb;

        VH(@NonNull View v) {
            super(v);
            ivIcon = v.findViewById(R.id.iv_pick_icon);
            tvName = v.findViewById(R.id.tv_pick_name);
            cb = v.findViewById(R.id.cb_pick);
        }
    }
}
