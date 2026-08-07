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

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.carassistant.R;

import java.util.ArrayList;
import java.util.List;

/**
 * 首页功能宫格适配器
 * 卡片数据：图标 + 标题 + 背景色 + 点击回调
 */
public class FeatureAdapter extends RecyclerView.Adapter<FeatureAdapter.VH> {

    public static class FeatureItem {
        public final int iconRes;
        public final int titleRes;
        public final int bgColorRes;
        public final View.OnClickListener clickListener;

        public FeatureItem(int iconRes, int titleRes, int bgColorRes, View.OnClickListener clickListener) {
            this.iconRes = iconRes;
            this.titleRes = titleRes;
            this.bgColorRes = bgColorRes;
            this.clickListener = clickListener;
        }
    }

    private final List<FeatureItem> items = new ArrayList<>();
    private final Context context;

    public FeatureAdapter(Context context) {
        this.context = context;
    }

    public void setItems(List<FeatureItem> items) {
        this.items.clear();
        if (items != null) this.items.addAll(items);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_feature_card, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
        FeatureItem item = items.get(position);
        holder.ivIcon.setImageResource(item.iconRes);
        // 圆形图标背景（支持渐变）
        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.OVAL);
        int bgColor = ContextCompat.getColor(context, item.bgColorRes);
        int[] colors = iconGradientOf(bgColor);
        if (colors != null && colors.length >= 2) {
            bg.setColors(colors);
            bg.setGradientType(GradientDrawable.LINEAR_GRADIENT);
            bg.setOrientation(GradientDrawable.Orientation.TL_BR);
        } else {
            bg.setColor(bgColor);
        }
        holder.ivIcon.setBackground(bg);
        holder.tvTitle.setText(item.titleRes);
        holder.itemView.setOnClickListener(item.clickListener);
    }

    /**
     * 根据基底颜色生成轻微渐变配色（增强图标背景视觉层次）
     */
    private int[] iconGradientOf(int baseColor) {
        float[] hsv = new float[3];
        android.graphics.Color.colorToHSV(baseColor, hsv);
        float s = Math.min(1.0f, hsv[1] * 1.15f);
        float b = Math.min(1.0f, hsv[2] * 1.08f);
        return new int[]{
            baseColor,
            android.graphics.Color.HSVToColor(new float[]{hsv[0], s, b})
        };
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class VH extends RecyclerView.ViewHolder {
        ImageView ivIcon;
        TextView tvTitle;

        VH(View v) {
            super(v);
            ivIcon = v.findViewById(R.id.iv_icon);
            tvTitle = v.findViewById(R.id.tv_title);
        }
    }
}
