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

package com.carassistant.util;

import android.graphics.Rect;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

/**
 * RecyclerView Grid 间距装饰
 *
 * 等间距分布：水平方向 item 之间和左右边距一致
 */
public class GridSpacingItemDecoration extends RecyclerView.ItemDecoration {

    private final int spanCount;
    private final int spacing;
    private final boolean includeEdge;

    public GridSpacingItemDecoration(int spanCount, int spacing, boolean includeEdge) {
        this.spanCount = spanCount;
        this.spacing = spacing;
        this.includeEdge = includeEdge;
    }

    @Override
    public void getItemOffsets(@NonNull Rect outRect, @NonNull View view,
                               @NonNull RecyclerView parent, @NonNull RecyclerView.State state) {
        int position = parent.getChildAdapterPosition(view);
        RecyclerView.LayoutManager lm = parent.getLayoutManager();
        int spanSize = 1;
        int spanIndex = position % spanCount;
        if (lm instanceof GridLayoutManager) {
            GridLayoutManager.SpanSizeLookup lookup = ((GridLayoutManager) lm).getSpanSizeLookup();
            spanSize = lookup.getSpanSize(position);
            spanIndex = lookup.getSpanIndex(position, spanCount);
        }

        if (spanSize == spanCount) {
            // 整行 item：不加水平间距
            outRect.left = 0;
            outRect.right = 0;
        } else {
            // 普通多列 item：等间距分布
            int totalSpacing = spacing * (spanCount - 1);
            int perItem = totalSpacing / spanCount;
            outRect.left = spacing - spanIndex * perItem;
            outRect.right = (spanIndex + spanSize) * perItem;
        }

        if (includeEdge) {
            outRect.top = spacing;
        } else {
            if (position >= spanCount) {
                outRect.top = spacing;
            }
        }
    }
}
