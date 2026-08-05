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
    private static final String PREF_SHOW_TRANSLATION = "music_show_translation";
    private static final String PREF_DYNAMIC_THEME = "music_dynamic_theme";
    private static final String PREF_VINYL_ROTATE = "music_vinyl_rotate";
    private static final String PREF_SHOW_ARM = "music_show_arm";
    private static final String PREF_VISUALIZER = "music_visualizer";
    private static final String PREF_DEFAULT_REPEAT = "music_default_repeat";
    private static final String PREF_SHOW_PREV = "music_show_prev";
    private static final String PREF_SHOW_NEXT = "music_show_next";
    private static final String PREF_AUTO_OPEN_APP = "music_auto_open_app";
    private static final String PREF_VINYL_SCALE = "music_vinyl_scale";   // 唱片大小缩放：0.8/1.0/1.2


    private static final int COLOR_PAGE_BG = 0xFF0F1320;
    private static final int COLOR_CARD_BG = 0xFF1A1F2E;
    private static final int COLOR_TEXT_PRIMARY = 0xFFE8EAF0;
    private static final int COLOR_TEXT_SECONDARY = 0xFF8B92A5;
    private static final int COLOR_ACCENT = 0xFFEE0A24;   // 网易云红
    private static final int COLOR_ACCENT_DIM = 0xFF6B7280;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        ScrollView scrollView = new ScrollView(this);
        scrollView.setFillViewport(true);
        scrollView.setBackgroundColor(COLOR_PAGE_BG);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(12), dp(18), dp(32));
        scrollView.addView(root, new FrameLayout.LayoutParams(-1, -2));

        // ===== 顶部工具栏 =====
        MaterialToolbar toolbar = new MaterialToolbar(this);
        toolbar.setTitle(R.string.music_settings);
        toolbar.setSubtitle(R.string.music_settings_subtitle);
        toolbar.setTitleTextColor(Color.WHITE);
        toolbar.setSubtitleTextColor(COLOR_TEXT_SECONDARY);
        toolbar.setBackgroundColor(Color.TRANSPARENT);
        toolbar.setNavigationIcon(android.R.drawable.ic_menu_close_clear_cancel);
        toolbar.setNavigationOnClickListener(v -> finish());
        root.addView(toolbar, new LinearLayout.LayoutParams(-1, dp(70)));

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
        addToggle(cardPlay, R.string.music_settings_show_translation,
                PREF_SHOW_TRANSLATION, true);
        addCard(root, cardPlay);

        // ===== 卡片：外观 =====
        LinearLayout cardAppearance = card(R.string.music_settings_appearance_card);
        addToggle(cardAppearance, R.string.music_settings_dynamic_theme,
                PREF_DYNAMIC_THEME, true);
        addToggle(cardAppearance, R.string.music_settings_vinyl_rotate,
                PREF_VINYL_ROTATE, true);
        addToggle(cardAppearance, R.string.music_settings_show_arm,
                PREF_SHOW_ARM, true);
        addChoice(cardAppearance, R.string.music_settings_vinyl_size, PREF_VINYL_SCALE, "1.0",
                new int[]{R.string.music_settings_vinyl_size_small,
                        R.string.music_settings_vinyl_size_medium,
                        R.string.music_settings_vinyl_size_large},
                new String[]{"0.8", "1.0", "1.2"});
        addSeek(cardAppearance, R.string.music_settings_icon_size, 70, 150,
                sp().getInt("music_icon_scale", 100), " %",
                i -> sp().edit().putInt("music_icon_scale", i).apply());
        addSeek(cardAppearance, R.string.music_settings_seekbar_thickness, 2, 16,
                sp().getInt("music_seekbar_thickness", 6), " dp",
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

    private LinearLayout card(int titleRes) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(15), dp(14), dp(15), dp(15));
        MaterialShapeDrawable bg = new MaterialShapeDrawable();
        bg.setFillColor(ColorStateList.valueOf(COLOR_CARD_BG));
        bg.setCornerSize((float) dp(20));
        card.setBackground(bg);
        card.addView(cardTitle(titleRes));
        return card;
    }

    private void addCard(LinearLayout parent, LinearLayout card) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = dp(14);
        parent.addView(card, lp);
    }

    private TextView cardTitle(int resId) {
        return labelText(resId, true);
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
        row.setPadding(0, dp(12), 0, dp(12));
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

        String current = sp().getString(key, defaultVal);
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
            btn.setPadding(dp(10), dp(7), dp(10), dp(7));
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
