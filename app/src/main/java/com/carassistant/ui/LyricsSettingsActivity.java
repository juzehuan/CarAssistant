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

package com.carassistant.ui;

import com.carassistant.util.Immersive;

import android.animation.ValueAnimator;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.carassistant.R;
import com.carassistant.lyrics.LyricsPanelView;
import com.carassistant.service.FloatingLyricsService;
import com.carassistant.util.PermissionUtil;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.shape.MaterialShapeDrawable;

/**
 * 桌面歌词设置页（1:1 复刻自歌词伴侣 DisplaySettingsActivity + RefinedSettingsActivity）。
 *
 * 功能：
 * - 实时预览（LyricsPanelView）
 * - 显示风格切换（经典/精致/紧凑/画中画）
 * - 悬浮窗尺寸与文字（宽度/高度/字号/歌词时间校正）
 * - 背景与封面（不透明度/封面大小/柔化/遮罩/背景类型）
 * - 精致风格特效（翻译/卡拉OK/发光/模糊/旋转/缩放/淡入淡出）
 * - 紧凑风格选项（显示封面/律动条）
 * - 播放控制按钮（上一首/播放暂停/下一首）
 *
 * 设置持久化到 SharedPreferences，FloatingLyricsService 在 showFloatWindow() 读取。
 * 修改后通过 {@link #changed()} 重启悬浮窗服务以应用新参数。
 */
public final class LyricsSettingsActivity extends AppCompatActivity {

    /** SharedPreferences 键名前缀（与 FloatingLyricsService 保持一致） */
    private static final String PREF_KEY_STYLE = "float_lyrics_style";
    private static final String PREF_KEY_OPACITY = "float_lyrics_opacity";
    private static final String PREF_KEY_WIDTH_DP = "float_lyrics_width_dp";
    private static final String PREF_KEY_HEIGHT_DP = "float_lyrics_height_dp";
    private static final String PREF_KEY_FONT_SCALE = "float_lyrics_font_scale";
    private static final String PREF_KEY_COVER_SCALE = "float_lyrics_cover_scale";
    private static final String PREF_KEY_BLUR = "float_lyrics_blur";
    private static final String PREF_KEY_DIM = "float_lyrics_dim";
    private static final String PREF_KEY_OFFSET = "float_lyrics_offset";
    private static final String PREF_KEY_BG_TYPE = "float_lyrics_bg_type";
    private static final String PREF_KEY_SHOW_TRANSLATION = "float_lyrics_show_translation";
    private static final String PREF_KEY_KARAOKE = "float_lyrics_karaoke";
    private static final String PREF_KEY_GLOW = "float_lyrics_glow";
    private static final String PREF_KEY_LYRIC_BLUR = "float_lyrics_lyric_blur";
    private static final String PREF_KEY_LYRIC_ROTATE = "float_lyrics_lyric_rotate";
    private static final String PREF_KEY_LYRIC_ZOOM = "float_lyrics_lyric_zoom";
    private static final String PREF_KEY_LYRIC_FADE = "float_lyrics_lyric_fade";
    private static final String PREF_KEY_COMPACT_COVER = "float_lyrics_compact_cover";
    private static final String PREF_KEY_COMPACT_BARS = "float_lyrics_compact_bars";
    /** 精致风格显示模式：both(封面+歌词) / lyrics(仅歌词) / cover(仅封面) */
    private static final String PREF_KEY_REFINED_DISPLAY = "float_lyrics_refined_display";
    private static final String PREF_KEY_SHOW_PREV = "float_lyrics_show_prev";
    private static final String PREF_KEY_SHOW_PLAY = "float_lyrics_show_play";
    private static final String PREF_KEY_SHOW_NEXT = "float_lyrics_show_next";
    /** 副屏模式：隐藏播放控制按钮 + 放大文字 */
    private static final String PREF_KEY_SECONDARY = "float_lyrics_secondary";

    // ===== 补齐原版 AppPreferences 缺失项（11 项） =====
    private static final String PREF_KEY_PANEL_SCALE = "float_lyrics_panel_scale";
    private static final String PREF_KEY_DISPLAY_ID = "float_lyrics_display_id";
    private static final String PREF_KEY_MAIN_OVERLAY = "float_lyrics_main_overlay";
    private static final String PREF_KEY_SECONDARY_ENABLE = "float_lyrics_secondary_enable";
    private static final String PREF_KEY_SECONDARY_STYLE = "float_lyrics_secondary_style";
    private static final String PREF_KEY_SECONDARY_POS = "float_lyrics_secondary_pos";
    private static final String PREF_KEY_SECONDARY_X = "float_lyrics_secondary_x";
    private static final String PREF_KEY_SECONDARY_Y = "float_lyrics_secondary_y";
    private static final String PREF_KEY_LYRIC_CATALOG = "float_lyrics_lyric_catalog";
    private static final String PREF_KEY_PLAYER_CATALOG_FALLBACK = "float_lyrics_player_catalog_fallback";
    private static final String PREF_KEY_CUSTOM_FONT = "float_lyrics_custom_font";
    private static final String PREF_KEY_DIAGNOSTIC_UPLOAD = "float_lyrics_diagnostic_upload";
    private static final String PREF_KEY_COMPACT_WIDTH = "float_lyrics_compact_width_dp";
    private static final String PREF_KEY_COMPACT_HEIGHT = "float_lyrics_compact_height_dp";

    private static final int REQUEST_CODE_FONT_FILE = 0x5F01;

    private LyricsPanelView preview;
    private String currentStyle = "refined";
    /** 风格选择器按钮列表（用于切换高亮，避免 recreate 闪屏） */
    private final java.util.List<TextView> styleButtons = new java.util.ArrayList<>();
    /** 背景类型选择器按钮列表 */
    private final java.util.List<TextView> bgTypeButtons = new java.util.ArrayList<>();
    private String currentBgType = "blur";
    /** 自定义字体路径显示 TextView（用于选择文件后刷新显示） */
    private TextView fontPathLabel;

    /** 卡片中卡片背景色 */
    private static final int COLOR_CARD_BG = 0xFF1A1F2E;
    private static final int COLOR_PAGE_BG = 0xFF0F1320;
    private static final int COLOR_TEXT_PRIMARY = 0xFFE8EAF0;
    private static final int COLOR_TEXT_SECONDARY = 0xFF8B92A5;
    private static final int COLOR_ACCENT = 0xFFEE0A24;   // 网易云红
    private static final int COLOR_ACCENT_DIM = 0xFF6B7280;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        currentStyle = sp().getString(PREF_KEY_STYLE, "refined");
        currentBgType = sp().getString(PREF_KEY_BG_TYPE, "blur");

        ScrollView scrollView = new ScrollView(this);
        scrollView.setFillViewport(true);
        scrollView.setBackgroundColor(COLOR_PAGE_BG);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(22), dp(8), dp(22), dp(40));
        int availableWidth = getResources().getDisplayMetrics().widthPixels - dp(24);
        FrameLayout.LayoutParams rootLp = new FrameLayout.LayoutParams(
                Math.min(availableWidth, dp(900)), -2, Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        scrollView.addView(root, rootLp);

        // 沉浸式状态栏：透明 + 内容铺满顶部，工具栏下压避免被遮挡
        Immersive.apply(this, false);

        // ===== 顶部工具栏 =====
        MaterialToolbar toolbar = new MaterialToolbar(this);
        toolbar.setTitle(R.string.floating_lyrics_settings);
        toolbar.setSubtitle(R.string.floating_lyrics_settings_subtitle);
        toolbar.setTitleTextColor(Color.WHITE);
        toolbar.setSubtitleTextColor(COLOR_TEXT_SECONDARY);
        toolbar.setBackgroundColor(Color.TRANSPARENT);
        toolbar.setNavigationIcon(android.R.drawable.ic_menu_close_clear_cancel);
        toolbar.setNavigationOnClickListener(v -> finish());
        toolbar.setMinimumHeight(dp(72));
        root.addView(toolbar, new LinearLayout.LayoutParams(-1, -2));
        Immersive.padTopForStatusBar(toolbar);

        // ===== 桌面歌词开关 =====
        setupLyricsEnable(root);

        // ===== 实时预览 =====
        preview = new LyricsPanelView(this, buildStyleFromPrefs());
        LinearLayout.LayoutParams previewLp = new LinearLayout.LayoutParams(-1, dp(190));
        previewLp.topMargin = dp(8);
        root.addView(preview, previewLp);

        // ===== 风格切换（分段按钮） =====
        root.addView(buildStyleSelector());

        // ===== 卡片：悬浮窗尺寸与文字 =====
        LinearLayout cardSize = card(R.string.floating_lyrics_card_size);
        addSeek(cardSize, R.string.floating_lyrics_width, 280, 900,
                sp().getInt(PREF_KEY_WIDTH_DP, 420), " dp", i -> {
                    sp().edit().putInt(PREF_KEY_WIDTH_DP, i).apply();
                    changed();
                });
        addSeek(cardSize, R.string.floating_lyrics_height, 120, 600,
                sp().getInt(PREF_KEY_HEIGHT_DP, 200), " dp", i -> {
                    sp().edit().putInt(PREF_KEY_HEIGHT_DP, i).apply();
                    changed();
                });
        addSeek(cardSize, R.string.floating_lyrics_font_scale, 75, 150,
                sp().getInt(PREF_KEY_FONT_SCALE, 100), " %", i -> {
                    sp().edit().putInt(PREF_KEY_FONT_SCALE, i).apply();
                    changed();
                });
        addSeek(cardSize, R.string.floating_lyrics_offset, -5000, 5000,
                sp().getInt(PREF_KEY_OFFSET, 0), " ms", i -> {
                    sp().edit().putInt(PREF_KEY_OFFSET, i).apply();
                    changed();
                });
        addCard(root, cardSize);

        // ===== 卡片：背景与封面 =====
        LinearLayout cardBg = card(R.string.floating_lyrics_card_bg);
        addSeek(cardBg, R.string.floating_lyrics_opacity, 0, 100,
                sp().getInt(PREF_KEY_OPACITY, 88), " %", i -> {
                    sp().edit().putInt(PREF_KEY_OPACITY, i).apply();
                    changed();
                });
        addSeek(cardBg, R.string.floating_lyrics_cover_scale, 60, 150,
                sp().getInt(PREF_KEY_COVER_SCALE, 100), " %", i -> {
                    sp().edit().putInt(PREF_KEY_COVER_SCALE, i).apply();
                    changed();
                });
        addSeek(cardBg, R.string.floating_lyrics_blur, 0, 128,
                sp().getInt(PREF_KEY_BLUR, 128), " %", i -> {
                    sp().edit().putInt(PREF_KEY_BLUR, i).apply();
                    changed();
                });
        addSeek(cardBg, R.string.floating_lyrics_dim, 0, 80,
                sp().getInt(PREF_KEY_DIM, 38), " %", i -> {
                    sp().edit().putInt(PREF_KEY_DIM, i).apply();
                    changed();
                });
        // 背景类型选择（精致风格生效）
        addBgTypeSelector(cardBg);
        addCard(root, cardBg);

        // ===== 卡片：精致风格特效 =====
        LinearLayout cardRefined = card(R.string.floating_lyrics_card_refined);
        addToggle(cardRefined, R.string.floating_lyrics_show_translation, PREF_KEY_SHOW_TRANSLATION, true);
        addToggle(cardRefined, R.string.floating_lyrics_show_karaoke, PREF_KEY_KARAOKE, true);
        addToggle(cardRefined, R.string.floating_lyrics_show_glow, PREF_KEY_GLOW, true);
        addToggle(cardRefined, R.string.floating_lyrics_show_blur, PREF_KEY_LYRIC_BLUR, true);
        addToggle(cardRefined, R.string.floating_lyrics_show_rotate, PREF_KEY_LYRIC_ROTATE, true);
        addToggle(cardRefined, R.string.floating_lyrics_show_zoom, PREF_KEY_LYRIC_ZOOM, true);
        addToggle(cardRefined, R.string.floating_lyrics_show_fade, PREF_KEY_LYRIC_FADE, true);
        TextView tipRefined = tipText("仅在「精致」风格下生效。");
        cardRefined.addView(tipRefined);
        addCard(root, cardRefined);

        // ===== 卡片：紧凑风格选项 =====
        LinearLayout cardCompact = card(R.string.floating_lyrics_card_compact);
        addToggle(cardCompact, R.string.floating_lyrics_compact_cover, PREF_KEY_COMPACT_COVER, true);
        addToggle(cardCompact, R.string.floating_lyrics_compact_bars, PREF_KEY_COMPACT_BARS, true);
        TextView tipCompact = tipText("仅在「紧凑」风格下生效。");
        cardCompact.addView(tipCompact);
        addCard(root, cardCompact);

        // ===== 卡片：播放控制按钮 =====
        LinearLayout cardCtrl = card(R.string.floating_lyrics_card_controls);
        addToggle(cardCtrl, R.string.floating_lyrics_show_prev, PREF_KEY_SHOW_PREV, true);
        addToggle(cardCtrl, R.string.floating_lyrics_show_play, PREF_KEY_SHOW_PLAY, true);
        addToggle(cardCtrl, R.string.floating_lyrics_show_next, PREF_KEY_SHOW_NEXT, true);
        addToggle(cardCtrl, R.string.floating_lyrics_secondary, PREF_KEY_SECONDARY, false);
        TextView tipSecondary = tipText(getString(R.string.floating_lyrics_secondary_tip));
        cardCtrl.addView(tipSecondary);
        addCard(root, cardCtrl);

        // ===== 卡片：高级与扩展（面板缩放 + 目标显示器） =====
        LinearLayout cardAdvanced = card(R.string.floating_lyrics_card_advanced);
        addSeek(cardAdvanced, R.string.floating_lyrics_panel_scale, 50, 150,
                sp().getInt(PREF_KEY_PANEL_SCALE, 100), " %", i -> {
                    sp().edit().putInt(PREF_KEY_PANEL_SCALE, i).apply();
                    changed();
                });
        addChoice(cardAdvanced, R.string.floating_lyrics_display_id, PREF_KEY_DISPLAY_ID, "-1",
                new int[]{R.string.floating_lyrics_display_default,
                        R.string.floating_lyrics_display_main,
                        R.string.floating_lyrics_display_secondary},
                new String[]{"-1", "0", "1"});
        cardAdvanced.addView(tipText(getString(R.string.floating_lyrics_advanced_tip)));
        addCard(root, cardAdvanced);

        // ===== 卡片：副悬浮窗（主悬浮窗总开关 + 启用副悬浮窗 + 风格 + 位置） =====
        LinearLayout cardSecondary = card(R.string.floating_lyrics_card_secondary_panel);
        addToggle(cardSecondary, R.string.floating_lyrics_main_overlay, PREF_KEY_MAIN_OVERLAY, false);
        cardSecondary.addView(tipText(getString(R.string.floating_lyrics_main_overlay_tip)));
        addToggle(cardSecondary, R.string.floating_lyrics_secondary_enable, PREF_KEY_SECONDARY_ENABLE, false);
        addChoice(cardSecondary, R.string.floating_lyrics_secondary_style, PREF_KEY_SECONDARY_STYLE, "default",
                new int[]{R.string.floating_lyrics_secondary_style_default,
                        R.string.floating_lyrics_style_default,
                        R.string.floating_lyrics_style_refined,
                        R.string.floating_lyrics_style_compact,
                        R.string.floating_lyrics_style_pip},
                new String[]{"default", "default", "refined", "compact", "pip"});
        addChoice(cardSecondary, R.string.floating_lyrics_secondary_position, PREF_KEY_SECONDARY_POS, "default",
                new int[]{R.string.floating_lyrics_secondary_position_default,
                        R.string.floating_lyrics_secondary_position_custom},
                new String[]{"default", "custom"});
        addCard(root, cardSecondary);

        // ===== 卡片：歌词与诊断（歌词源 + 回退 + 字体 + 诊断上传） =====
        LinearLayout cardData = card(R.string.floating_lyrics_card_data_source);
        addChoice(cardData, R.string.floating_lyrics_lyric_catalog, PREF_KEY_LYRIC_CATALOG, "auto",
                new int[]{R.string.floating_lyrics_lyric_catalog_auto,
                        R.string.floating_lyrics_lyric_catalog_netease,
                        R.string.floating_lyrics_lyric_catalog_qqmusic,
                        R.string.floating_lyrics_lyric_catalog_kugou,
                        R.string.floating_lyrics_lyric_catalog_kuwo,
                        R.string.floating_lyrics_lyric_catalog_soda},
                new String[]{"auto", "netease", "qqmusic", "kugou", "kuwo", "soda"});
        addToggle(cardData, R.string.floating_lyrics_player_catalog_fallback, PREF_KEY_PLAYER_CATALOG_FALLBACK, true);
        cardData.addView(tipText(getString(R.string.floating_lyrics_player_catalog_fallback_tip)));
        addFontPicker(cardData);
        addToggle(cardData, R.string.floating_lyrics_diagnostic_upload, PREF_KEY_DIAGNOSTIC_UPLOAD, false);
        cardData.addView(tipText(getString(R.string.floating_lyrics_diagnostic_upload_tip)));
        addCard(root, cardData);

        // ===== 卡片：紧凑风格尺寸（独立宽度 + 独立高度） =====
        LinearLayout cardCompactSize = card(R.string.floating_lyrics_card_compact_size);
        addSeek(cardCompactSize, R.string.floating_lyrics_compact_width, 240, 480,
                sp().getInt(PREF_KEY_COMPACT_WIDTH, 320), " dp", i -> {
                    sp().edit().putInt(PREF_KEY_COMPACT_WIDTH, i).apply();
                    changed();
                });
        addSeek(cardCompactSize, R.string.floating_lyrics_compact_height, 60, 200,
                sp().getInt(PREF_KEY_COMPACT_HEIGHT, 80), " dp", i -> {
                    sp().edit().putInt(PREF_KEY_COMPACT_HEIGHT, i).apply();
                    changed();
                });
        cardCompactSize.addView(tipText("仅在「紧凑」风格下生效，用于副悬浮窗或紧凑模式独立尺寸。"));
        addCard(root, cardCompactSize);

        setContentView(scrollView);
    }

    // ============ 风格选择器（分段按钮组） ============

    private View buildStyleSelector() {
        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.HORIZONTAL);
        container.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = dp(14);
        container.setLayoutParams(lp);

        String[] styles = {"default", "refined", "compact", "pip"};
        int[] labels = {R.string.floating_lyrics_style_default,
                R.string.floating_lyrics_style_refined,
                R.string.floating_lyrics_style_compact,
                R.string.floating_lyrics_style_pip};
        styleButtons.clear();

        for (int i = 0; i < styles.length; i++) {
            final String style = styles[i];
            TextView btn = new TextView(this);
            btn.setText(labels[i]);
            btn.setTextSize(13f);
            btn.setGravity(Gravity.CENTER);
            btn.setPadding(dp(16), dp(10), dp(16), dp(10));
            applyStyleButtonLook(btn, style.equals(currentStyle));

            LinearLayout.LayoutParams btnLp = new LinearLayout.LayoutParams(0, -2, 1f);
            if (i > 0) btnLp.leftMargin = dp(6);
            btn.setLayoutParams(btnLp);
            btn.setOnClickListener(v -> {
                currentStyle = style;
                sp().edit().putString(PREF_KEY_STYLE, style).apply();
                // 刷新所有按钮高亮（不重建 Activity，避免闪屏）
                for (int j = 0; j < styles.length; j++) {
                    applyStyleButtonLook(styleButtons.get(j), styles[j].equals(style));
                }
                changed();
            });
            styleButtons.add(btn);
            container.addView(btn);
        }
        return container;
    }

    /** 应用风格按钮的选中/未选中外观 */
    private void applyStyleButtonLook(TextView btn, boolean selected) {
        btn.setTextColor(selected ? Color.WHITE : COLOR_TEXT_SECONDARY);
        btn.setTypeface(Typeface.DEFAULT, selected ? Typeface.BOLD : Typeface.NORMAL);
        MaterialShapeDrawable bg = new MaterialShapeDrawable();
        bg.setFillColor(ColorStateList.valueOf(selected ? COLOR_ACCENT : 0xFF252B3D));
        bg.setCornerSize((float) dp(10));
        btn.setBackground(bg);
    }

    // ============ 背景类型选择器 ============

    private void addBgTypeSelector(LinearLayout parent) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(12), 0, 0);
        row.addView(labelText(R.string.floating_lyrics_bg_type, false), new LinearLayout.LayoutParams(0, -2, 1f));

        final String[] types = {"blur", "solid", "gradient", "fluid", "none"};
        int[] labels = {R.string.floating_lyrics_bg_blur, R.string.floating_lyrics_bg_solid,
                R.string.floating_lyrics_bg_gradient, R.string.floating_lyrics_bg_fluid,
                R.string.floating_lyrics_bg_none};
        bgTypeButtons.clear();

        RadioGroup rg = new RadioGroup(this);
        rg.setOrientation(LinearLayout.HORIZONTAL);
        for (int i = 0; i < types.length; i++) {
            final String type = types[i];
            TextView item = new TextView(this);
            item.setText(labels[i]);
            item.setTextSize(12f);
            item.setGravity(Gravity.CENTER);
            item.setPadding(dp(10), dp(6), dp(10), dp(6));
            applyBgTypeButtonLook(item, type.equals(currentBgType));

            LinearLayout.LayoutParams itemLp = new LinearLayout.LayoutParams(-2, -2);
            if (i > 0) itemLp.leftMargin = dp(4);
            item.setLayoutParams(itemLp);
            item.setOnClickListener(v -> {
                currentBgType = type;
                sp().edit().putString(PREF_KEY_BG_TYPE, type).apply();
                // 刷新所有按钮高亮（不重建 Activity）
                for (int j = 0; j < types.length; j++) {
                    applyBgTypeButtonLook(bgTypeButtons.get(j), types[j].equals(type));
                }
                changed();
            });
            bgTypeButtons.add(item);
            rg.addView(item);
        }
        row.addView(rg);
        parent.addView(row);
    }

    /** 应用背景类型按钮的选中/未选中外观 */
    private void applyBgTypeButtonLook(TextView btn, boolean selected) {
        btn.setTextColor(selected ? Color.WHITE : COLOR_TEXT_SECONDARY);
        MaterialShapeDrawable bg = new MaterialShapeDrawable();
        bg.setFillColor(ColorStateList.valueOf(selected ? COLOR_ACCENT : 0x00000000));
        bg.setCornerSize((float) dp(8));
        bg.setStroke((float) dp(1), selected ? COLOR_ACCENT : COLOR_ACCENT_DIM);
        btn.setBackground(bg);
    }

    // ============ 从偏好构建 StyleConfig ============

    private LyricsPanelView.StyleConfig buildStyleFromPrefs() {
        LyricsPanelView.StyleConfig s = new LyricsPanelView.StyleConfig();
        SharedPreferences sp = sp();
        s.overlayStyle = sp.getString(PREF_KEY_STYLE, "refined");
        // 精致风格默认封面+歌词都显示（原默认 "lyrics" 会导致封面区域宽度为 0，封面不绘制）
        s.refinedDisplayMode = sp.getString(PREF_KEY_REFINED_DISPLAY, "both");
        s.opacity = sp.getInt(PREF_KEY_OPACITY, 88);
        s.lyricOffsetMs = sp.getInt(PREF_KEY_OFFSET, 0);
        s.textScale = sp.getInt(PREF_KEY_FONT_SCALE, 100) / 100f;
        s.coverScale = sp.getInt(PREF_KEY_COVER_SCALE, 100) / 100f;
        s.backgroundBlur = sp.getInt(PREF_KEY_BLUR, 128);
        s.backgroundDim = sp.getInt(PREF_KEY_DIM, 38);
        s.refinedBackgroundType = sp.getString(PREF_KEY_BG_TYPE, "blur");
        s.refinedShowTranslation = sp.getBoolean(PREF_KEY_SHOW_TRANSLATION, true);
        s.refinedKaraokeAnimation = sp.getBoolean(PREF_KEY_KARAOKE, true) ? "float" : "none";
        s.refinedLyricGlow = sp.getBoolean(PREF_KEY_GLOW, true);
        s.refinedLyricBlur = sp.getBoolean(PREF_KEY_LYRIC_BLUR, true);
        s.refinedLyricRotate = sp.getBoolean(PREF_KEY_LYRIC_ROTATE, true);
        s.refinedLyricZoom = sp.getBoolean(PREF_KEY_LYRIC_ZOOM, true);
        s.refinedLyricFade = sp.getBoolean(PREF_KEY_LYRIC_FADE, true);
        s.compactShowCover = sp.getBoolean(PREF_KEY_COMPACT_COVER, true);
        s.compactShowBars = sp.getBoolean(PREF_KEY_COMPACT_BARS, true);
        s.showPreviousButton = sp.getBoolean(PREF_KEY_SHOW_PREV, true);
        s.showPlayPauseButton = sp.getBoolean(PREF_KEY_SHOW_PLAY, true);
        s.showNextButton = sp.getBoolean(PREF_KEY_SHOW_NEXT, true);
        s.secondary = sp.getBoolean(PREF_KEY_SECONDARY, false);
        // ===== 补齐原版 AppPreferences 缺失项 =====
        s.panelScale = sp.getInt(PREF_KEY_PANEL_SCALE, 100) / 100f;
        s.displayId = sp.getInt(PREF_KEY_DISPLAY_ID, -1);
        s.mainOverlayEnabled = sp.getBoolean(PREF_KEY_MAIN_OVERLAY, false);
        boolean secondaryEnable = sp.getBoolean(PREF_KEY_SECONDARY_ENABLE, false);
        if (secondaryEnable) {
            s.secondaryOverlayStyle = sp.getString(PREF_KEY_SECONDARY_STYLE, "compact");
            String posMode = sp.getString(PREF_KEY_SECONDARY_POS, "default");
            if ("custom".equals(posMode)) {
                s.secondaryX = sp.getInt(PREF_KEY_SECONDARY_X, Integer.MIN_VALUE);
                s.secondaryY = sp.getInt(PREF_KEY_SECONDARY_Y, Integer.MIN_VALUE);
            } else {
                s.secondaryX = Integer.MIN_VALUE;
                s.secondaryY = Integer.MIN_VALUE;
            }
        } else {
            s.secondaryOverlayStyle = "compact";
            s.secondaryX = Integer.MIN_VALUE;
            s.secondaryY = Integer.MIN_VALUE;
        }
        s.lyricCatalog = sp.getString(PREF_KEY_LYRIC_CATALOG, "auto");
        s.playerCatalogFallback = sp.getBoolean(PREF_KEY_PLAYER_CATALOG_FALLBACK, true);
        s.customFontFile = sp.getString(PREF_KEY_CUSTOM_FONT, "");
        s.diagnosticUploadEnabled = sp.getBoolean(PREF_KEY_DIAGNOSTIC_UPLOAD, false);
        s.compactPanelWidthDp = sp.getInt(PREF_KEY_COMPACT_WIDTH, 320);
        s.compactPanelHeightDp = sp.getInt(PREF_KEY_COMPACT_HEIGHT, 80);
        return s;
    }

    // ============ 设置变更后刷新预览 + 重启悬浮窗 ============

    private void changed() {
        if (preview != null) {
            // 将最新偏好同步到预览面板，触发实时重绘
            preview.updateStyle(buildStyleFromPrefs());
        }
        // 如果悬浮窗正在运行，重启以应用新尺寸/风格
        if (FloatingLyricsService.isRunning()) {
            Intent intent = new Intent(this, FloatingLyricsService.class);
            stopService(intent);
            // 延迟一点再启动，确保旧服务完全停止
            if (preview != null) {
                preview.postDelayed(() -> {
                    Intent restart = new Intent(this, FloatingLyricsService.class);
                    ContextCompat.startForegroundService(this, restart);
                }, 200);
            }
        }
    }

    // ============ UI 构建辅助 ============

    private LinearLayout card(int titleRes) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(20), dp(18), dp(20), dp(18));
        MaterialShapeDrawable bg = new MaterialShapeDrawable();
        bg.setFillColor(ColorStateList.valueOf(0xF21A1F2E));
        bg.setCornerSize((float) dp(22));
        bg.setStroke(dp(1), ColorStateList.valueOf(0x16FFFFFF));
        card.setBackground(bg);
        card.setElevation(dp(2));
        card.addView(cardTitle(titleRes));
        return card;
    }

    private void addCard(LinearLayout parent, LinearLayout card) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = dp(16);
        parent.addView(card, lp);
    }

    /** 桌面歌词总开关：开启启动 FloatingLyricsService，关闭停止服务（含权限校验） */
    private void setupLyricsEnable(LinearLayout parent) {
        LinearLayout enableCard = card(R.string.floating_lyrics_enable_card);
        enableCard.addView(tipText(getString(R.string.floating_lyrics_enable_tip)));

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(14), 0, dp(4));

        TextView label = new TextView(this);
        label.setText(R.string.floating_lyrics_enable_switch);
        label.setTextSize(15f);
        label.setTextColor(COLOR_TEXT_PRIMARY);
        label.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        row.addView(label, new LinearLayout.LayoutParams(0, -2, 1f));

        final ToggleIndicator toggle = new ToggleIndicator(FloatingLyricsService.isRunning());
        row.addView(toggle);

        row.setOnClickListener(v -> {
            boolean want = !toggle.isChecked();
            if (want) {
                if (!PermissionUtil.isNotificationListenerEnabled(this)) {
                    toggle.setChecked(false);
                    android.widget.Toast.makeText(this, R.string.music_permission_required, android.widget.Toast.LENGTH_SHORT).show();
                    PermissionUtil.requestNotificationListenerAccess(this);
                    return;
                }
                if (!PermissionUtil.canDrawOverlays(this)) {
                    toggle.setChecked(false);
                    android.widget.Toast.makeText(this, R.string.floating_lyrics_no_overlay_perm, android.widget.Toast.LENGTH_SHORT).show();
                    PermissionUtil.requestOverlayPermission(this, 0x2102);
                    return;
                }
                ContextCompat.startForegroundService(this, new Intent(this, FloatingLyricsService.class));
                toggle.setChecked(true);
                android.widget.Toast.makeText(this, R.string.floating_lyrics_enabled, android.widget.Toast.LENGTH_SHORT).show();
            } else {
                stopService(new Intent(this, FloatingLyricsService.class));
                toggle.setChecked(false);
                android.widget.Toast.makeText(this, R.string.floating_lyrics_disabled, android.widget.Toast.LENGTH_SHORT).show();
            }
        });
        enableCard.addView(row);
        addCard(parent, enableCard);
    }

    private TextView cardTitle(int resId) {
        TextView title = labelText(resId, true);
        title.setTextSize(15f);
        title.setTextColor(COLOR_TEXT_PRIMARY);
        title.setPadding(0, 0, 0, dp(4));
        return title;
    }

    private TextView labelText(int resId, boolean bold) {
        TextView tv = new TextView(this);
        tv.setText(resId);
        tv.setTextSize(13f);
        tv.setTextColor(COLOR_TEXT_SECONDARY);
        if (bold) tv.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return tv;
    }

    private TextView tipText(String text) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextSize(12f);
        tv.setTextColor(COLOR_ACCENT_DIM);
        tv.setPadding(0, dp(8), 0, 0);
        return tv;
    }

    private void addSeek(LinearLayout parent, int labelRes, int min, int max,
                         int current, String unit, IntConsumer consumer) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(12), 0, 0);
        TextView label = new TextView(this);
        label.setText(labelRes);
        label.setTextSize(14f);
        label.setTextColor(COLOR_TEXT_PRIMARY);
        label.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        row.addView(label, new LinearLayout.LayoutParams(0, -2, 1f));

        final TextView value = new TextView(this);
        value.setText(formatValue(current, unit));
        value.setTextSize(13f);
        value.setTextColor(COLOR_TEXT_SECONDARY);
        row.addView(value);
        parent.addView(row);

        SeekBar seekBar = new SeekBar(this);
        seekBar.setMax(max - min);
        seekBar.setProgress(clamp(current, min, max) - min);
        seekBar.setProgressTintList(ColorStateList.valueOf(COLOR_ACCENT));
        seekBar.setThumbTintList(ColorStateList.valueOf(COLOR_ACCENT));
        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onStartTrackingTouch(SeekBar s) {}
            @Override public void onStopTrackingTouch(SeekBar s) {}
            @Override
            public void onProgressChanged(SeekBar s, int progress, boolean fromUser) {
                int val = min + progress;
                value.setText(formatValue(val, unit));
                if (fromUser) {
                    consumer.accept(val);
                }
            }
        });
        parent.addView(seekBar, new LinearLayout.LayoutParams(-1, dp(38)));
    }

    private void addToggle(LinearLayout parent, int labelRes,
                           String key, boolean defaultVal) {
        // 注意：原先使用 MaterialSwitch，但在 Android 14 (API 34) 上其父类 SwitchCompat.onMeasure
        // 会调用 makeLayout 构建 StaticLayout，传入 null CharSequence 导致 NPE 崩溃
        // （AppCompat 1.6.1 对 API 34 兼容缺陷）。改为自绘 ToggleIndicator 规避。
        boolean checked = sp().getBoolean(key, defaultVal);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(2), dp(12), dp(2), dp(12));
        row.setMinimumHeight(dp(56));
        row.setClickable(true);
        row.setFocusable(true);
        row.setBackground(getDrawable(android.R.drawable.list_selector_background));

        TextView label = new TextView(this);
        label.setText(labelRes);
        label.setTextSize(14f);
        label.setTextColor(COLOR_TEXT_PRIMARY);
        label.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        row.addView(label, new LinearLayout.LayoutParams(0, -2, 1f));

        ToggleIndicator toggle = new ToggleIndicator(checked);
        row.addView(toggle);

        row.setOnClickListener(v -> {
            boolean newState = !toggle.isChecked();
            toggle.setChecked(newState);
            sp().edit().putBoolean(key, newState).apply();
            changed();
        });
        parent.addView(row);
    }

    /**
     * 自绘开关指示器（网易云红色主题）。
     * 替代 MaterialSwitch，规避 Android 14 上 SwitchCompat.onMeasure 的 StaticLayout NPE。
     */
    private final class ToggleIndicator extends View {
        private boolean checked;
        private float thumbX;
        private final int trackW;
        private final int trackH;
        private final int thumbPad;
        private final int thumbSize;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF trackRect = new RectF();

        ToggleIndicator(boolean initial) {
            super(LyricsSettingsActivity.this);
            checked = initial;
            trackW = dp(52);
            trackH = dp(30);
            thumbPad = dp(3);
            thumbSize = trackH - 2 * thumbPad;
            thumbX = checked ? (trackW - thumbPad - thumbSize) : thumbPad;
            setClickable(false);
        }

        boolean isChecked() {
            return checked;
        }

        void setChecked(boolean c) {
            if (checked == c) return;
            checked = c;
            float target = checked ? (trackW - thumbPad - thumbSize) : thumbPad;
            ValueAnimator a = ValueAnimator.ofFloat(thumbX, target);
            a.setDuration(180);
            a.setInterpolator(new DecelerateInterpolator());
            a.addUpdateListener(an -> {
                thumbX = (float) an.getAnimatedValue();
                invalidate();
            });
            a.start();
        }

        @Override
        protected void onMeasure(int widthSpec, int heightSpec) {
            setMeasuredDimension(trackW, trackH);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            trackRect.set(0, 0, trackW, trackH);
            float r = trackH / 2f;
            paint.setColor(checked ? COLOR_ACCENT : 0xFF3A3F4E);
            canvas.drawRoundRect(trackRect, r, r, paint);
            paint.setColor(Color.WHITE);
            canvas.drawCircle(thumbX + thumbSize / 2f, trackH / 2f, thumbSize / 2f, paint);
        }
    }

    // ============ 通用分段选择器（替代 Spinner） ============

    private void addChoice(LinearLayout parent, int labelRes, final String key, String defaultVal,
                           int[] labelResArr, final String[] valueArr) {
        TextView label = labelText(labelRes, false);
        label.setPadding(0, dp(12), 0, dp(4));
        label.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        label.setTextColor(COLOR_TEXT_PRIMARY);
        label.setTextSize(14f);
        parent.addView(label);

        String current = sp().getString(key, defaultVal);
        final java.util.List<TextView> buttons = new java.util.ArrayList<>();
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, 0, 0, dp(4));

        for (int i = 0; i < valueArr.length; i++) {
            final String val = valueArr[i];
            TextView btn = new TextView(this);
            btn.setText(labelResArr[i]);
            btn.setTextSize(12f);
            btn.setGravity(Gravity.CENTER);
            btn.setPadding(dp(10), dp(10), dp(10), dp(10));
            btn.setMinHeight(dp(44));
            applySegmentButtonLook(btn, val.equals(current));

            LinearLayout.LayoutParams itemLp = new LinearLayout.LayoutParams(0, -2, 1f);
            if (i > 0) itemLp.leftMargin = dp(4);
            btn.setLayoutParams(itemLp);
            btn.setOnClickListener(v -> {
                sp().edit().putString(key, val).apply();
                for (int j = 0; j < valueArr.length; j++) {
                    applySegmentButtonLook(buttons.get(j), valueArr[j].equals(val));
                }
                changed();
            });
            buttons.add(btn);
            row.addView(btn);
        }
        parent.addView(row);
    }

    private void applySegmentButtonLook(TextView btn, boolean selected) {
        btn.setTextColor(selected ? Color.WHITE : COLOR_TEXT_SECONDARY);
        btn.setTypeface(Typeface.DEFAULT, selected ? Typeface.BOLD : Typeface.NORMAL);
        MaterialShapeDrawable bg = new MaterialShapeDrawable();
        bg.setFillColor(ColorStateList.valueOf(selected ? COLOR_ACCENT : 0xFF252B3D));
        bg.setCornerSize((float) dp(8));
        btn.setBackground(bg);
    }

    // ============ 自定义字体文件选择器 ============

    private void addFontPicker(LinearLayout parent) {
        TextView label = labelText(R.string.floating_lyrics_custom_font, false);
        label.setPadding(0, dp(12), 0, dp(4));
        label.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        label.setTextColor(COLOR_TEXT_PRIMARY);
        label.setTextSize(14f);
        parent.addView(label);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, 0, 0, dp(4));

        fontPathLabel = new TextView(this);
        fontPathLabel.setTextSize(12f);
        fontPathLabel.setTextColor(COLOR_TEXT_SECONDARY);
        fontPathLabel.setMaxLines(1);
        fontPathLabel.setEllipsize(android.text.TextUtils.TruncateAt.MIDDLE);
        String fontPath = sp().getString(PREF_KEY_CUSTOM_FONT, "");
        if (fontPath.isEmpty()) {
            fontPathLabel.setText(R.string.floating_lyrics_custom_font_none);
        } else {
            fontPathLabel.setText(fontPath);
        }
        LinearLayout.LayoutParams labelLp = new LinearLayout.LayoutParams(0, -2, 1f);
        row.addView(fontPathLabel, labelLp);

        TextView pickBtn = new TextView(this);
        pickBtn.setText(R.string.floating_lyrics_custom_font_pick);
        pickBtn.setTextSize(12f);
        pickBtn.setTextColor(Color.WHITE);
        pickBtn.setPadding(dp(12), dp(6), dp(12), dp(6));
        MaterialShapeDrawable pickBg = new MaterialShapeDrawable();
        pickBg.setFillColor(ColorStateList.valueOf(COLOR_ACCENT));
        pickBg.setCornerSize((float) dp(8));
        pickBtn.setBackground(pickBg);
        pickBtn.setOnClickListener(v -> openFontFilePicker());
        row.addView(pickBtn);

        TextView clearBtn = new TextView(this);
        clearBtn.setText(R.string.floating_lyrics_custom_font_clear);
        clearBtn.setTextSize(12f);
        clearBtn.setTextColor(COLOR_TEXT_SECONDARY);
        clearBtn.setPadding(dp(12), dp(6), dp(0), dp(6));
        LinearLayout.LayoutParams clearLp = new LinearLayout.LayoutParams(-2, -2);
        clearLp.leftMargin = dp(8);
        clearBtn.setLayoutParams(clearLp);
        clearBtn.setOnClickListener(v -> {
            sp().edit().remove(PREF_KEY_CUSTOM_FONT).apply();
            if (fontPathLabel != null) {
                fontPathLabel.setText(R.string.floating_lyrics_custom_font_none);
            }
            changed();
        });
        row.addView(clearBtn);

        parent.addView(row);
    }

    private void openFontFilePicker() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        String[] mimeTypes = {"font/ttf", "font/otf", "application/x-font-ttf", "application/x-font-otf", "application/octet-stream"};
        intent.putExtra(Intent.EXTRA_MIME_TYPES, mimeTypes);
        try {
            startActivityForResult(intent, REQUEST_CODE_FONT_FILE);
        } catch (Exception e) {
            // 部分车机没有 SAF 文件选择器，退回 ACTION_GET_CONTENT
            Intent fallback = new Intent(Intent.ACTION_GET_CONTENT);
            fallback.setType("*/*");
            try {
                startActivityForResult(fallback, REQUEST_CODE_FONT_FILE);
            } catch (Exception ex) {
                android.widget.Toast.makeText(this, "未找到文件选择器", android.widget.Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_CODE_FONT_FILE) {
            if (resultCode == RESULT_OK && data != null && data.getData() != null) {
                Uri uri = data.getData();
                String path = uri.toString();
                sp().edit().putString(PREF_KEY_CUSTOM_FONT, path).apply();
                if (fontPathLabel != null) {
                    fontPathLabel.setText(path);
                }
                changed();
            }
        }
    }

    // ============ 工具方法 ============

    private SharedPreferences sp() {
        return android.preference.PreferenceManager.getDefaultSharedPreferences(this);
    }

    private int dp(float v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    private static int clamp(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }

    private static String formatValue(int v, String unit) {
        StringBuilder sb = new StringBuilder();
        if (v > 0 && " ms".equals(unit)) sb.append("+");
        sb.append(v).append(unit);
        return sb.toString();
    }

    private interface IntConsumer {
        void accept(int value);
    }
}
