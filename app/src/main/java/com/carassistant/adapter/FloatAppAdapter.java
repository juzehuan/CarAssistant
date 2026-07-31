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
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.carassistant.R;

import java.util.ArrayList;
import java.util.List;

/**
 * 悬浮球快捷面板的应用适配器
 * 数据源使用 ResolveInfo（带 launcher intent 的应用）
 */
public class FloatAppAdapter extends RecyclerView.Adapter<FloatAppAdapter.VH> {

    public static class AppEntry {
        public String label;
        public String packageName;
        public Drawable icon;
        public Intent launchIntent;
    }

    public interface OnAppClickListener {
        void onAppClick(AppEntry entry);
    }

    private final List<AppEntry> items = new ArrayList<>();
    private OnAppClickListener listener;
    private final int maxCount;
    private final PackageManager pm;

    public FloatAppAdapter(PackageManager pm, int maxCount) {
        this.pm = pm;
        this.maxCount = maxCount;
    }

    public void setListener(OnAppClickListener l) { this.listener = l; }

    public void setFromResolveInfo(List<ResolveInfo> ris) {
        items.clear();
        if (ris != null && pm != null) {
            int limit = Math.min(maxCount, ris.size());
            for (int i = 0; i < limit; i++) {
                ResolveInfo ri = ris.get(i);
                AppEntry e = new AppEntry();
                e.label = ri.loadLabel(pm).toString();
                e.packageName = ri.activityInfo.packageName;
                e.icon = ri.loadIcon(pm);
                Intent base = new Intent(Intent.ACTION_MAIN);
                base.addCategory(Intent.CATEGORY_LAUNCHER);
                base.setClassName(ri.activityInfo.packageName, ri.activityInfo.name);
                base.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                e.launchIntent = base;
                items.add(e);
            }
        }
        notifyDataSetChanged();
    }

    public List<AppEntry> getItems() { return items; }

    /** 直接设置已构建好的应用条目列表（用于侧边栏等服务自行加载应用） */
    public void setData(List<AppEntry> data) {
        items.clear();
        if (data != null) items.addAll(data);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_float_app, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
        AppEntry e = items.get(position);
        if (e.icon != null) holder.ivIcon.setImageDrawable(e.icon);
        else holder.ivIcon.setImageResource(R.drawable.ic_app_default);
        holder.tvName.setText(e.label);
        holder.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onAppClick(e);
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class VH extends RecyclerView.ViewHolder {
        ImageView ivIcon;
        TextView tvName;

        VH(@NonNull View v) {
            super(v);
            ivIcon = v.findViewById(R.id.iv_float_app_icon);
            tvName = v.findViewById(R.id.tv_float_app_name);
        }
    }
}
