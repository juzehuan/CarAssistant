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
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.carassistant.R;
import com.carassistant.util.CleanUtil;

import java.util.ArrayList;
import java.util.List;

public class JunkAdapter extends RecyclerView.Adapter<JunkAdapter.VH> {

    private final List<CleanUtil.JunkGroup> items = new ArrayList<>();

    public void setData(List<CleanUtil.JunkGroup> data) {
        items.clear();
        if (data != null) items.addAll(data);
        notifyDataSetChanged();
    }

    public List<CleanUtil.JunkGroup> getData() {
        return items;
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_junk, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
        CleanUtil.JunkGroup g = items.get(position);
        holder.tvName.setText(g.name);
        holder.tvDesc.setText(g.desc);
        // root 专属项显示徽章
        if (g.rootOnly) {
            holder.tvRootTag.setVisibility(View.VISIBLE);
        } else {
            holder.tvRootTag.setVisibility(View.GONE);
        }
        holder.cb.setOnCheckedChangeListener(null);
        holder.cb.setChecked(g.selected);
        holder.cb.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                g.selected = isChecked;
            }
        });
        // 同时判断 files 和 rootPaths：rootOnly 项仅 rootPaths 有内容
        boolean hasContent = !g.files.isEmpty() || !g.rootPaths.isEmpty();
        if (!hasContent) {
            holder.itemView.setAlpha(0.4f);
            holder.cb.setEnabled(false);
        } else {
            holder.itemView.setAlpha(1f);
            holder.cb.setEnabled(true);
        }
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class VH extends RecyclerView.ViewHolder {
        ImageView ivIcon;
        TextView tvName;
        TextView tvDesc;
        TextView tvRootTag;
        CheckBox cb;

        VH(@NonNull View v) {
            super(v);
            ivIcon = v.findViewById(R.id.iv_junk_icon);
            tvName = v.findViewById(R.id.tv_junk_name);
            tvDesc = v.findViewById(R.id.tv_junk_desc);
            tvRootTag = v.findViewById(R.id.tv_junk_root_tag);
            cb = v.findViewById(R.id.cb_junk);
        }
    }
}
