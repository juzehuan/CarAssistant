package com.carassistant.util;

import android.app.Activity;
import android.graphics.Color;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;

import androidx.annotation.NonNull;

/**
 * 沉浸式状态栏工具：把状态栏设为透明，让页面背景铺满至屏幕顶部。
 * 浅色背景页面传 lightStatusBarText=true（深色状态栏文字），深色页面传 false（浅色文字）。
 */
public final class Immersive {

    private Immersive() {
    }

    public static void apply(@NonNull Activity activity, boolean lightStatusBarText) {
        Window window = activity.getWindow();
        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
        window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);
        window.setStatusBarColor(Color.TRANSPARENT);
        int ui = View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_STABLE;
        if (lightStatusBarText) ui |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
        window.getDecorView().setSystemUiVisibility(ui);
    }

    /**
     * 给根布局首个子 View（通常为顶部工具栏）增加状态栏高度的内边距，避免内容被状态栏图标遮挡。
     */
    public static void padTopForStatusBar(@NonNull Activity activity, ViewGroup root) {
        if (root == null) return;
        View header = root.getChildAt(0);
        if (header == null) return;
        padTopForStatusBar(header);
    }

    /**
     * 使用系统实际 WindowInsets 给指定顶部控件补齐安全区。
     * 保存原始 padding，重复分发 Insets 时不会不断累加。
     */
    public static void padTopForStatusBar(@NonNull View header) {
        final int left = header.getPaddingLeft();
        final int top = header.getPaddingTop();
        final int right = header.getPaddingRight();
        final int bottom = header.getPaddingBottom();
        header.setOnApplyWindowInsetsListener((view, insets) -> {
            view.setPadding(left, top + insets.getSystemWindowInsetTop(), right, bottom);
            return insets;
        });
        header.requestApplyInsets();
    }
}
