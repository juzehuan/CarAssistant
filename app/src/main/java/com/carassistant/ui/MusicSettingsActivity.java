package com.carassistant.ui;

import android.animation.ValueAnimator;
import android.content.SharedPreferences;
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
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.carassistant.R;
import com.carassistant.util.Immersive;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.shape.MaterialShapeDrawable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntConsumer;

/**
 * 音乐伴侣设置页（UI 风格对齐桌面歌词设置 LyricsSettingsActivity）。
 *
 * 与歌词设置不同：本页不预览悬浮窗，而是保存"进入音乐伴侣时的偏好"，
 * 由 {@link MusicActivity} 在 onResume / 生命周期中读取生效（实时微调 UI）。
 */
public final class MusicSettingsActivity extends AppCompatActivity {

    private static final String PREF_DEFAULT_SHOW_LYRICS = "music_default_show_lyrics";
    private static final String PREF_LYRIC_OFFSET_MS = "music_lyric_offset_ms";
    private static final String PREF_LYRIC_FONT_SCALE = "music_lyric_font_scale";
    private static final String PREF_LYRIC_LINE_SPACING = "music_lyric_line_spacing";
    private static final String PREF_SHOW_TRANSLATION = "music_show_translation";
    private static final String PREF_DYNAMIC_THEME = "music_dynamic_theme";
    private static final String PREF_VINYL_ROTATE = "music_vinyl_rotate";
    private static final String PREF_SHOW_ARM = "music_show_arm";
    private static final String PREF_VISUALIZER = "music_visualizer";
    private static final String PREF_VISUALIZER_MODE = "music_visualizer_mode";
    private static final String PREF_LAYOUT_ORIENT = "music_layout_orientation";
    private static final String PREF_COLOR_THEME = "music_color_theme";
    private static final String PREF_DEFAULT_REPEAT = "music_default_repeat";
    private static final String PREF_SHOW_PREV = "music_show_prev";
    private static final String PREF_SHOW_NEXT = "music_show_next";
    private static final String PREF_AUTO_OPEN_APP = "music_auto_open_app";
    private static final String PREF_VINYL_SCALE = "music_vinyl_scale";   // 唱片大小：百分比整数（100=原始大小）


    private static final int COLOR_PAGE_BG = 0xFF0D0D12;
    private static final int COLOR_CARD_BG = 0xFF151A28;
    private static final int COLOR_TEXT_PRIMARY = 0xFFF0F0F5;
    private static final int COLOR_TEXT_SECONDARY = 0xFF999FAD;
    private static final int COLOR_ACCENT = 0xFFEE0A24;
    private static final int COLOR_ACCENT_DIM = 0xFF5A6072;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        ScrollView scrollView = new ScrollView(this);
        scrollView.setFillViewport(true);
        // 渐变背景（与音乐伴侣页面统一）
        android.graphics.drawable.GradientDrawable pageBg = new android.graphics.drawable.GradientDrawable(
                android.graphics.drawable.GradientDrawable.Orientation.TL_BR,
                new int[]{0xFF0D0D12, 0xFF1A1418, 0xFF1A1420});
        scrollView.setBackground(pageBg);

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
        toolbar.setTitle(R.string.music_settings);
        toolbar.setSubtitle(R.string.music_settings_subtitle);
        toolbar.setTitleTextColor(Color.WHITE);
        toolbar.setSubtitleTextColor(COLOR_TEXT_SECONDARY);
        toolbar.setBackgroundColor(Color.TRANSPARENT);
        toolbar.setNavigationIcon(android.R.drawable.ic_menu_close_clear_cancel);
        toolbar.setNavigationOnClickListener(v -> finish());
        toolbar.setMinimumHeight(dp(72));
        root.addView(toolbar, new LinearLayout.LayoutParams(-1, -2));
        Immersive.padTopForStatusBar(toolbar);

        // ===== 卡片：播放与歌词 =====
        LinearLayout cardPlay = card(R.string.music_settings_play_lyric_card);
        addToggle(cardPlay, R.string.music_settings_default_show_lyrics,
                PREF_DEFAULT_SHOW_LYRICS, true);
        addSeek(cardPlay, R.string.music_settings_lyric_offset, -5000, 5000,
                sp().getInt(PREF_LYRIC_OFFSET_MS, 0), " ms",
                i -> sp().edit().putInt(PREF_LYRIC_OFFSET_MS, i).apply());
        addSeek(cardPlay, R.string.music_settings_lyric_font, 50, 400,
                sp().getInt(PREF_LYRIC_FONT_SCALE, 100), " %",
                i -> sp().edit().putInt(PREF_LYRIC_FONT_SCALE, i).apply());
        addSeek(cardPlay, R.string.music_settings_lyric_line_spacing, 0, 16,
                sp().getInt(PREF_LYRIC_LINE_SPACING, 4), " dp",
                i -> sp().edit().putInt(PREF_LYRIC_LINE_SPACING, i).apply());
        addToggle(cardPlay, R.string.music_settings_show_translation,
                PREF_SHOW_TRANSLATION, true);
        addCard(root, cardPlay);

        // ===== 卡片：外观 =====
        LinearLayout cardAppearance = card(R.string.music_settings_appearance_card);
        addChoice(cardAppearance, R.string.music_settings_color_theme, PREF_COLOR_THEME, "-1",
                new int[]{R.string.music_theme_auto,
                        R.string.music_theme_red,
                        R.string.music_theme_blue,
                        R.string.music_theme_purple,
                        R.string.music_theme_green,
                        R.string.music_theme_orange,
                        R.string.music_theme_silver},
                new String[]{"-1", "0", "1", "2", "3", "4", "5"});
        addToggle(cardAppearance, R.string.music_settings_dynamic_theme,
                PREF_DYNAMIC_THEME, true);
        addToggle(cardAppearance, R.string.music_settings_vinyl_rotate,
                PREF_VINYL_ROTATE, true);
        addToggle(cardAppearance, R.string.music_settings_show_arm,
                PREF_SHOW_ARM, true);
        addToggle(cardAppearance, R.string.music_settings_visualizer,
                PREF_VISUALIZER, true);
        addEffectChoice(cardAppearance, R.string.music_settings_visualizer_mode, PREF_VISUALIZER_MODE, "0",
                new int[]{R.string.music_vis_mode_neon,
                        R.string.music_vis_mode_circle,
                        R.string.music_vis_mode_wave,
                        R.string.music_vis_mode_column,
                        R.string.music_vis_mode_dot},
                new String[]{"0", "1", "2", "3", "4"});
        addChoice(cardAppearance, R.string.music_settings_layout_orientation, PREF_LAYOUT_ORIENT, "0",
                new int[]{R.string.music_layout_vertical,
                        R.string.music_layout_horizontal},
                new String[]{"0", "1"});
        // 唱片大小：以 100% 为基准的百分比（兼容旧版字符串值 "0.8/1.0/1.2" → 80/100/120）
        int vinylPct = readVinylScalePercent();
        addSeek(cardAppearance, R.string.music_settings_vinyl_size, 50, 200,
                vinylPct, " %",
                i -> sp().edit().putInt(PREF_VINYL_SCALE, i).apply());
        addSeek(cardAppearance, R.string.music_settings_icon_size, 70, 150,
                sp().getInt("music_icon_scale", 100), " %",
                i -> sp().edit().putInt("music_icon_scale", i).apply());
        addSeek(cardAppearance, R.string.music_settings_seekbar_thickness, 2, 16,
                sp().getInt("music_seekbar_thickness", 3), " dp",
                i -> sp().edit().putInt("music_seekbar_thickness", i).apply());
        addColorRow(cardAppearance, R.string.music_settings_seekbar_color, "music_seekbar_color", 0xFFE60026);
        addCard(root, cardAppearance);

        // ===== 卡片：播放控制 =====
        LinearLayout cardCtrl = card(R.string.music_settings_pref_card);
        addChoice(cardCtrl, R.string.music_settings_default_repeat, PREF_DEFAULT_REPEAT, "0",
                new int[]{R.string.music_settings_repeat_order,
                        R.string.music_settings_repeat_one,
                        R.string.music_settings_repeat_all},
                new String[]{"0", "1", "2"});
        addToggle(cardCtrl, R.string.music_settings_show_prev, PREF_SHOW_PREV, true);
        addToggle(cardCtrl, R.string.music_settings_show_next, PREF_SHOW_NEXT, true);
        addToggle(cardCtrl, R.string.music_settings_auto_open_app, PREF_AUTO_OPEN_APP, false);
        addSeek(cardCtrl, R.string.music_settings_control_margin, 0, 64,
                sp().getInt("music_ctrl_margin", 24), " dp",
                i -> sp().edit().putInt("music_ctrl_margin", i).apply());
        addSeek(cardCtrl, R.string.music_settings_header_lift, 0, 40,
                sp().getInt("music_header_lift", 0), " dp",
                i -> sp().edit().putInt("music_header_lift", i).apply());
        addCard(root, cardCtrl);

        setContentView(scrollView);
    }

    // ============ 设置变更后钩子（UI 类设置由 MusicActivity 在 resume 时读取，无需重启） ============

    private void changed() {
        // 留空：所有 UI 微调项在返回音乐伴侣时由 applyMusicUiSettings() 实时生效
    }

    // ============ UI 构建辅助（与桌面歌词设置一致） ============

    private SharedPreferences sp() {
        return getSharedPreferences("music_settings", MODE_PRIVATE);
    }

    /** 读取唱片大小百分比，默认 100；兼容旧版以字符串存储的 "0.8/1.0/1.2"。 */
    private int readVinylScalePercent() {
        try {
            return sp().getInt(PREF_VINYL_SCALE, 100);
        } catch (ClassCastException e) {
            try {
                return (int) (Float.parseFloat(sp().getString(PREF_VINYL_SCALE, "1.0")) * 100);
            } catch (Exception ignore) {
                return 100;
            }
        }
    }

    private LinearLayout card(int titleRes) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(20), dp(18), dp(20), dp(18));
        // 双层叠加背景（深色底 + 顶部红色微光描边）
        android.graphics.drawable.LayerDrawable layers = new android.graphics.drawable.LayerDrawable(
                new android.graphics.drawable.Drawable[]{
                        createCardBg(), createCardTopGlow()
                });
        card.setBackground(layers);
        card.setElevation(dp(2));
        card.addView(cardTitle(titleRes));
        return card;
    }

    private android.graphics.drawable.GradientDrawable createCardBg() {
        android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
        bg.setShape(android.graphics.drawable.GradientDrawable.RECTANGLE);
        bg.setCornerRadius(dp(22));
        bg.setColor(0xF2151A28);
        bg.setStroke(dp(1), 0x16FFFFFF);
        return bg;
    }

    private android.graphics.drawable.GradientDrawable createCardTopGlow() {
        android.graphics.drawable.GradientDrawable glow = new android.graphics.drawable.GradientDrawable();
        glow.setShape(android.graphics.drawable.GradientDrawable.RECTANGLE);
        float r = dp(22);
        glow.setCornerRadii(new float[]{r, r, r, r, 0, 0, 0, 0});
        glow.setGradientType(android.graphics.drawable.GradientDrawable.LINEAR_GRADIENT);
        glow.setOrientation(android.graphics.drawable.GradientDrawable.Orientation.TOP_BOTTOM);
        glow.setColors(new int[]{0x16EE0A24, 0x00000000});
        glow.setSize(-1, dp(72));
        return glow;
    }

    private void addCard(LinearLayout parent, LinearLayout card) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = dp(16);
        parent.addView(card, lp);
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
            super(MusicSettingsActivity.this);
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

        String current = readChoiceValue(key, defaultVal);
        final List<TextView> buttons = new ArrayList<>();
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

    /** 五种律动使用 3+2 两行选择器，避免竖屏下文字被挤成难看的窄列。 */
    private void addEffectChoice(LinearLayout parent, int labelRes, final String key, String defaultVal,
                                 int[] labelResArr, final String[] valueArr) {
        TextView label = labelText(labelRes, false);
        label.setPadding(0, dp(12), 0, dp(6));
        label.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        label.setTextColor(COLOR_TEXT_PRIMARY);
        label.setTextSize(14f);
        parent.addView(label);

        String current = readChoiceValue(key, defaultVal);
        final List<TextView> buttons = new ArrayList<>();
        final List<String> values = new ArrayList<>();
        LinearLayout row = null;
        for (int i = 0; i < valueArr.length; i++) {
            if (i == 0 || i == 3) {
                row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setGravity(Gravity.CENTER_VERTICAL);
                if (i == 3) row.setPadding(0, dp(6), 0, dp(4));
                parent.addView(row, new LinearLayout.LayoutParams(-1, -2));
            }

            final String val = valueArr[i];
            TextView btn = new TextView(this);
            btn.setText(labelResArr[i]);
            btn.setTextSize(13f);
            btn.setGravity(Gravity.CENTER);
            btn.setPadding(dp(12), dp(11), dp(12), dp(11));
            btn.setMinHeight(dp(46));
            applySegmentButtonLook(btn, val.equals(current));

            LinearLayout.LayoutParams itemLp = new LinearLayout.LayoutParams(0, -2, 1f);
            if ((i % 3) > 0) itemLp.leftMargin = dp(6);
            btn.setLayoutParams(itemLp);
            btn.setOnClickListener(v -> {
                sp().edit().putString(key, val).apply();
                for (int j = 0; j < buttons.size(); j++) {
                    applySegmentButtonLook(buttons.get(j), values.get(j).equals(val));
                }
                changed();
            });
            buttons.add(btn);
            values.add(val);
            row.addView(btn);
        }
    }

    private void applySegmentButtonLook(TextView btn, boolean selected) {
        btn.setTextColor(selected ? Color.WHITE : COLOR_TEXT_SECONDARY);
        btn.setTypeface(Typeface.DEFAULT, selected ? Typeface.BOLD : Typeface.NORMAL);
        MaterialShapeDrawable bg = new MaterialShapeDrawable();
        bg.setFillColor(ColorStateList.valueOf(selected ? COLOR_ACCENT : 0xFF252B3D));
        bg.setCornerSize((float) dp(8));
        btn.setBackground(bg);
    }

    private String readChoiceValue(String key, String defaultVal) {
        try {
            return sp().getString(key, defaultVal);
        } catch (ClassCastException e) {
            try {
                return String.valueOf(sp().getInt(key, Integer.parseInt(defaultVal)));
            } catch (ClassCastException | NumberFormatException ignore) {
                return defaultVal;
            }
        }
    }

    private void addColorRow(LinearLayout parent, int labelRes, final String key, int defaultColor) {
        TextView label = labelText(labelRes, false);
        label.setPadding(0, dp(12), 0, dp(4));
        label.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        label.setTextColor(COLOR_TEXT_PRIMARY);
        label.setTextSize(14f);
        parent.addView(label);

        final int[] colors = new int[]{0xFFFFFFFF, 0xFFEE0A24, 0xFFFF6B00, 0xFFFFD000,
                0xFF00E5A0, 0xFF00C2FF, 0xFF4D8BFF, 0xFFB14DFF, 0xFFFF4D8B};
        int current = sp().getInt(key, defaultColor);

        final List<View> swatches = new ArrayList<>();
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, 0, 0, dp(6));
        for (final int col : colors) {
            View sw = new View(this);
            int size = dp(32);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(size, size);
            lp.leftMargin = dp(5);
            lp.rightMargin = dp(5);
            sw.setLayoutParams(lp);
            MaterialShapeDrawable bg = new MaterialShapeDrawable();
            bg.setFillColor(ColorStateList.valueOf(col));
            bg.setCornerSize((float) dp(16));
            bg.setStroke(dp(3), ColorStateList.valueOf(col == current ? Color.WHITE : Color.TRANSPARENT));
            sw.setBackground(bg);
            sw.setTag(col);
            sw.setOnClickListener(v -> {
                sp().edit().putInt(key, col).apply();
                for (View s : swatches) {
                    MaterialShapeDrawable d = (MaterialShapeDrawable) s.getBackground();
                    boolean sel = (int) s.getTag() == col;
                    d.setStroke(dp(3), ColorStateList.valueOf(sel ? Color.WHITE : Color.TRANSPARENT));
                    s.invalidate();
                }
                changed();
            });
            swatches.add(sw);
            row.addView(sw);
        }
        parent.addView(row);
    }

    // ============ 小工具 ============

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }

    private int clamp(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }

    private String formatValue(int val, String unit) {
        return val + unit;
    }
}
