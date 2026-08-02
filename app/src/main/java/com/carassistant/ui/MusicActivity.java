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

import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Outline;
import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.LinearInterpolator;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import android.animation.ArgbEvaluator;
import android.graphics.drawable.GradientDrawable;
import android.view.ViewGroup;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.palette.graphics.Palette;
import com.carassistant.R;
import com.carassistant.service.FloatingLyricsService;
import com.carassistant.util.LrcParser;
import com.carassistant.util.MusicController;
import com.carassistant.util.PermissionUtil;

/**
 * 音乐伴侣主界面（网易云音乐风格）
 *
 * - 通过 {@link MusicController} 监听系统活跃媒体会话（依赖通知访问权限）
 * - 黑胶唱片样式的专辑封面，播放时旋转，暂停时停止
 * - 显示当前曲目信息（标题/歌手/专辑封面）
 * - 实时显示三行歌词（上一行/当前行/下一行）
 * - 提供播放/暂停/上一首/下一首/拖动进度/循环模式控制
 *
 * 与鸿启桌面版本的差异：
 * - 复用 {@link com.carassistant.service.TargetMediaSessionService}，避免重复注册 NotificationListener
 * - 不依赖 Kotlin 协程，使用 Java Handler 异步更新
 * - 独立 Activity 而非桌面 dock 卡片
 */
public class MusicActivity extends AppCompatActivity implements MusicController.Callback {

    private static final String TAG = "MusicActivity";
    /** 悬浮窗权限请求码 */
    private static final int REQ_OVERLAY_FOR_LYRICS = 0x10;

    private MusicController controller;

    // 视图
    private View cardPermission;
    private View cardAlbum;
    private ImageView ivVinyl;
    private ImageView ivAlbum;
    private ImageView ivTonearm;   // 唱臂（导轨），网易云风格：播放落下、暂停抬起
    private TextView tvTitle, tvArtist;
    private TextView tvLyricPrev2, tvLyricPrev, tvLyricCurrent, tvLyricTranslation, tvLyricNext, tvLyricNext2;
    private TextView tvCurrent, tvDuration;
    private SeekBar sbProgress;
    private ImageView btnPlay, btnPrev, btnNext, btnRepeat;
    private ImageView btnFloatingLyrics;
    private ImageView btnLyricsSettings;
    private ImageView btnMusicSource;
    private ImageView btnLyricsToggle, btnPlaylist;
    private View lyricsContainer;
    private View btnBack, btnGrant;
    /** 根布局（用于动态设置背景色） */
    private ViewGroup musicRoot;
    /** 当前背景色（用于平滑过渡） */
    private int currentBgColor = 0xFF0F1320;
    /** 当前主题强调色 */
    private int currentAccentColor = 0xFFEE0A24;
    /** Argb 颜色求值器（平滑过渡） */
    private final ArgbEvaluator argbEvaluator = new ArgbEvaluator();    // 特效视图
    private View vinylGlow;       // 唱片外圈呼吸光晕
    private View playBtnGlow;     // 播放按钮外圈光晕
    private View ambientGlow1;    // 背景光斑 1
    private View ambientGlow2;    // 背景光斑 2

    /** 黑胶唱片旋转动画（同时旋转碟片+专辑封面，网易云风格） */
    private AnimatorSet vinylAnimator;
    /** 唱片光晕呼吸动画 */
    private ObjectAnimator vinylGlowPulse;
    /** 播放按钮光晕呼吸动画 */
    private ObjectAnimator playBtnGlowPulse;
    /** 背景光斑 1 飘动动画 */
    private ObjectAnimator ambientGlow1Anim;
    /** 背景光斑 2 飘动动画 */
    private ObjectAnimator ambientGlow2Anim;
    /** 用户是否正在拖动进度条 */
    private boolean userDragging = false;
    /** 上一次歌词文本，用于判断是否需要播放切换动画 */
    private String lastLyric = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_music);

        bindViews();
        setupListeners();
        setupVinylAnimation();

        controller = MusicController.getInstance();
    }

    private void bindViews() {
        musicRoot = findViewById(R.id.music_root);
        cardPermission = findViewById(R.id.card_permission);
        cardAlbum = findViewById(R.id.card_album);
        ivVinyl = findViewById(R.id.iv_vinyl);
        ivAlbum = findViewById(R.id.iv_album);
        ivTonearm = findViewById(R.id.iv_tonearm);
        tvTitle = findViewById(R.id.tv_title);
        tvArtist = findViewById(R.id.tv_artist);
        tvLyricPrev2 = findViewById(R.id.tv_lyric_prev2);
        tvLyricPrev = findViewById(R.id.tv_lyric_prev);
        tvLyricCurrent = findViewById(R.id.tv_lyric_current);
        tvLyricTranslation = findViewById(R.id.tv_lyric_translation);
        tvLyricNext = findViewById(R.id.tv_lyric_next);
        tvLyricNext2 = findViewById(R.id.tv_lyric_next2);
        tvCurrent = findViewById(R.id.tv_current);
        tvDuration = findViewById(R.id.tv_duration);
        sbProgress = findViewById(R.id.sb_progress);
        btnPlay = findViewById(R.id.btn_play);
        btnPrev = findViewById(R.id.btn_prev);
        btnNext = findViewById(R.id.btn_next);
        btnRepeat = findViewById(R.id.btn_repeat);
        btnFloatingLyrics = findViewById(R.id.btn_floating_lyrics);
        btnLyricsSettings = findViewById(R.id.btn_lyrics_settings);
        btnBack = findViewById(R.id.btn_back);
        btnGrant = findViewById(R.id.btn_grant);
        btnMusicSource = findViewById(R.id.btn_music_source);
        btnLyricsToggle = findViewById(R.id.btn_lyrics_toggle);
        btnPlaylist = findViewById(R.id.btn_playlist);
        lyricsContainer = findViewById(R.id.lyrics_container);
        // 特效视图
        vinylGlow = findViewById(R.id.vinyl_glow);
        playBtnGlow = findViewById(R.id.play_btn_glow);
        ambientGlow1 = findViewById(R.id.ambient_glow_1);
        ambientGlow2 = findViewById(R.id.ambient_glow_2);

        // 专辑封面圆形裁剪（网易云经典圆形封面）
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            ivAlbum.setOutlineProvider(new ViewOutlineProvider() {
                @Override
                public void getOutline(View view, Outline outline) {
                    outline.setOval(0, 0, view.getWidth(), view.getHeight());
                }
            });
            ivAlbum.setClipToOutline(true);
        }
    }

    /** 初始化黑胶唱片旋转动画 + 光晕呼吸 + 背景光斑飘动 */
    private void setupVinylAnimation() {
        // 唱片旋转：碟片 + 专辑封面同步旋转（网易云经典效果）
        ObjectAnimator discRot = ObjectAnimator.ofFloat(ivVinyl, "rotation", 0f, 360f);
        discRot.setDuration(20000);
        discRot.setInterpolator(new LinearInterpolator());
        discRot.setRepeatCount(ObjectAnimator.INFINITE);
        discRot.setRepeatMode(ObjectAnimator.RESTART);
        ObjectAnimator albumRot = ObjectAnimator.ofFloat(ivAlbum, "rotation", 0f, 360f);
        albumRot.setDuration(20000);
        albumRot.setInterpolator(new LinearInterpolator());
        albumRot.setRepeatCount(ObjectAnimator.INFINITE);
        albumRot.setRepeatMode(ObjectAnimator.RESTART);
        vinylAnimator = new AnimatorSet();
        vinylAnimator.playTogether(discRot, albumRot);
        vinylAnimator.pause();

        // 唱片光晕呼吸：alpha 0.3 ↔ 0.8，3 秒一个周期
        vinylGlowPulse = ObjectAnimator.ofFloat(vinylGlow, "alpha", 0.3f, 0.8f);
        vinylGlowPulse.setDuration(3000);
        vinylGlowPulse.setInterpolator(new LinearInterpolator());
        vinylGlowPulse.setRepeatCount(ObjectAnimator.INFINITE);
        vinylGlowPulse.setRepeatMode(ObjectAnimator.REVERSE);
        vinylGlowPulse.pause();

        // 播放按钮光晕呼吸：alpha 0.2 ↔ 0.6，2 秒一个周期
        playBtnGlowPulse = ObjectAnimator.ofFloat(playBtnGlow, "alpha", 0.2f, 0.6f);
        playBtnGlowPulse.setDuration(2000);
        playBtnGlowPulse.setInterpolator(new LinearInterpolator());
        playBtnGlowPulse.setRepeatCount(ObjectAnimator.INFINITE);
        playBtnGlowPulse.setRepeatMode(ObjectAnimator.REVERSE);
        playBtnGlowPulse.pause();

        // 背景光斑 1 飘动：平移 + 缩放循环（缓慢飘动）
        ambientGlow1Anim = ObjectAnimator.ofFloat(ambientGlow1, "translationY", 0f, 40f);
        ambientGlow1Anim.setDuration(8000);
        ambientGlow1Anim.setInterpolator(new LinearInterpolator());
        ambientGlow1Anim.setRepeatCount(ObjectAnimator.INFINITE);
        ambientGlow1Anim.setRepeatMode(ObjectAnimator.REVERSE);
        ambientGlow1Anim.start();

        // 背景光斑 2 飘动
        ambientGlow2Anim = ObjectAnimator.ofFloat(ambientGlow2, "translationX", 0f, -50f);
        ambientGlow2Anim.setDuration(10000);
        ambientGlow2Anim.setInterpolator(new LinearInterpolator());
        ambientGlow2Anim.setRepeatCount(ObjectAnimator.INFINITE);
        ambientGlow2Anim.setRepeatMode(ObjectAnimator.REVERSE);
        ambientGlow2Anim.start();

        // 唱臂（导轨）：初始为“抬起”状态。枢轴位于矢量图右上角 (170,46)/viewBox(200,250)，
        // 映射到 ImageView 即 pivotX≈85%、pivotY≈18.4%。抬起时绕枢轴逆时针旋转离开唱片。
        if (ivTonearm != null) {
            ivTonearm.post(() -> {
                ivTonearm.setPivotX(ivTonearm.getWidth() * 0.85f);
                ivTonearm.setPivotY(ivTonearm.getHeight() * 0.184f);
                ivTonearm.setRotation(TONEARM_LIFT_ANGLE);
            });
        }
    }

    /** 唱臂抬起角度（暂停/未播放时绕枢轴逆时针抬起，离开唱片） */
    private static final float TONEARM_LIFT_ANGLE = -25f;

    /** 网易云风格唱臂动画：播放时落下压片、暂停时抬起 */
    private void animateTonearm(boolean playing) {
        if (ivTonearm == null || ivTonearm.getWidth() == 0) return;
        // 确保枢轴正确（布局尺寸已确定）
        ivTonearm.setPivotX(ivTonearm.getWidth() * 0.85f);
        ivTonearm.setPivotY(ivTonearm.getHeight() * 0.184f);
        float target = playing ? 0f : TONEARM_LIFT_ANGLE;
        ivTonearm.animate()
                .rotation(target)
                .setDuration(450)
                .setInterpolator(new AccelerateDecelerateInterpolator())
                .start();
    }

    private void setupListeners() {
        // 所有视图绑定都做了空值保护：横屏布局（layout-land）与竖屏布局的控件集合不同，
        // 缺失的控件 findViewById 会返回 null，直接调用 setOnClickListener 会 NPE 闪退。
        // 因此这里对每个可能为 null 的视图都先判空，保证任意布局下打开界面都不会崩溃。
        if (btnBack != null) btnBack.setOnClickListener(v -> finish());
        if (btnGrant != null) btnGrant.setOnClickListener(v -> controller.openNotificationListenerSettings(this));

        if (btnPlay != null) btnPlay.setOnClickListener(v -> {
            playBtnPop(v);
            if (!ensurePermissionAndConnected()) return;
            if (controller.isPlaying()) controller.pause();
            else controller.play();
        });
        if (btnPrev != null) btnPrev.setOnClickListener(v -> {
            playBtnPop(v);
            if (!ensurePermissionAndConnected()) return;
            controller.playPrevious();
        });
        if (btnNext != null) btnNext.setOnClickListener(v -> {
            playBtnPop(v);
            if (!ensurePermissionAndConnected()) return;
            controller.playNext();
        });
        if (btnRepeat != null) btnRepeat.setOnClickListener(v -> {
            playBtnPop(v);
            // 切换循环模式：0 关闭 → 1 单曲 → 2 列表 → 0
            int next = (controller.getRepeatMode() + 1) % 3;
            controller.setRepeatMode(next);
            Toast.makeText(this, repeatModeName(next), Toast.LENGTH_SHORT).show();
        });

        // “词”按钮：展开 / 收起歌词区（横屏布局可能无此按钮 / 无歌词容器）
        if (btnLyricsToggle != null) btnLyricsToggle.setOnClickListener(v -> {
            playBtnPop(v);
            if (lyricsContainer != null) {
                if (lyricsContainer.getVisibility() == View.VISIBLE) {
                    lyricsContainer.setVisibility(View.GONE);
                    updateLyricsToggleButton(false);
                } else {
                    lyricsContainer.setVisibility(View.VISIBLE);
                    updateLyricsToggleButton(true);
                    ensurePermissionAndConnected();
                }
            }
        });

        // 列表按钮：打开当前音乐源 app
        if (btnPlaylist != null) btnPlaylist.setOnClickListener(v -> {
            playBtnPop(v);
            String pkg = controller.getMusicPackageName();
            if (pkg != null && !pkg.isEmpty()) {
                Intent launchIntent = getPackageManager().getLaunchIntentForPackage(pkg);
                if (launchIntent != null) {
                    launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(launchIntent);
                } else {
                    Toast.makeText(this, R.string.music_open_app, Toast.LENGTH_SHORT).show();
                }
            }
        });

        // 旧按钮保留监听（已隐藏，无影响）
        if (btnFloatingLyrics != null) btnFloatingLyrics.setOnClickListener(v -> toggleFloatingLyrics());
        if (btnLyricsSettings != null) btnLyricsSettings.setOnClickListener(v -> {
            Intent intent = new Intent(this, LyricsSettingsActivity.class);
            startActivity(intent);
        });
        if (btnMusicSource != null) btnMusicSource.setOnClickListener(v -> {
            // 已迁移到 btnPlaylist
        });

        if (sbProgress != null) sbProgress.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                if (fromUser) {
                    long dur = controller.getDuration();
                    long target = dur > 0 ? progress * dur / 100 : 0;
                    if (tvCurrent != null) tvCurrent.setText(formatTime(target));
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar bar) {
                userDragging = true;
            }

            @Override
            public void onStopTrackingTouch(SeekBar bar) {
                userDragging = false;
                long dur = controller.getDuration();
                long target = dur > 0 ? bar.getProgress() * dur / 100 : 0;
                controller.seekTo(target);
            }
        });

        // 点击专辑卡片：未连接时尝试打开音乐应用
        if (cardAlbum != null) {
            cardAlbum.setOnClickListener(v -> {
                if (controller.isConnected()) {
                    if (!controller.launchMusicApp(this)) {
                        Toast.makeText(this, R.string.music_no_active_session, Toast.LENGTH_SHORT).show();
                    }
                } else {
                    controller.findActiveMusicSession();
                    if (!controller.isConnected()) {
                        Toast.makeText(this, R.string.music_open_app_first, Toast.LENGTH_SHORT).show();
                    }
                }
            });

            cardAlbum.setOnLongClickListener(v -> {
                showLyricOffsetDialog();
                return true;
            });
        }
    }

    /** 切换桌面歌词悬浮窗开关 */
    private void toggleFloatingLyrics() {
        if (FloatingLyricsService.isRunning()) {
            stopService(new Intent(this, FloatingLyricsService.class));
            updateFloatingLyricsButton(false);
            Toast.makeText(this, R.string.floating_lyrics_disabled, Toast.LENGTH_SHORT).show();
            return;
        }
        // 开启前必须先有通知监听权限（拿到歌词数据源）和悬浮窗权限
        if (!controller.isNotificationListenerEnabled(this)) {
            Toast.makeText(this, R.string.music_permission_required, Toast.LENGTH_LONG).show();
            showPermissionCard(true);
            return;
        }
        if (!PermissionUtil.canDrawOverlays(this)) {
            Toast.makeText(this, R.string.floating_lyrics_no_overlay_perm, Toast.LENGTH_LONG).show();
            PermissionUtil.requestOverlayPermission(this, REQ_OVERLAY_FOR_LYRICS);
            return;
        }
        ContextCompat.startForegroundService(this, new Intent(this, FloatingLyricsService.class));
        updateFloatingLyricsButton(true);
        Toast.makeText(this, R.string.floating_lyrics_enabled, Toast.LENGTH_SHORT).show();
        Toast.makeText(this, R.string.floating_lyrics_drag_hint, Toast.LENGTH_LONG).show();
    }

    /** 同步悬浮窗按钮的视觉状态（激活=网易云红，未激活=灰色） */
    private void updateFloatingLyricsButton(boolean running) {
        if (btnFloatingLyrics == null) return;
        int colorRes = running ? R.color.music_accent : R.color.music_text_hint;
        btnFloatingLyrics.setColorFilter(ContextCompat.getColor(this, colorRes));
    }

    /** 同步“词”按钮视觉状态（歌词显示=网易云红高亮，隐藏=灰色） */
    private void updateLyricsToggleButton(boolean on) {
        if (btnLyricsToggle == null) return;
        int colorRes = on ? R.color.music_accent : R.color.music_text_hint;
        btnLyricsToggle.setColorFilter(ContextCompat.getColor(this, colorRes));
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_OVERLAY_FOR_LYRICS) {
            // 用户从悬浮窗权限设置页返回，若已授权则继续启动
            if (PermissionUtil.canDrawOverlays(this) && !FloatingLyricsService.isRunning()) {
                ContextCompat.startForegroundService(this, new Intent(this, FloatingLyricsService.class));
                updateFloatingLyricsButton(true);
                Toast.makeText(this, R.string.floating_lyrics_enabled, Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void showLyricOffsetDialog() {
        final long[] offset = {controller.getLyricOffsetMs()};
        androidx.appcompat.app.AlertDialog dialog = new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("歌词偏移调整")
                .setMessage(buildOffsetMessage(offset[0]))
                .setPositiveButton("提前 0.5s", (d, w) -> {
                    controller.adjustLyricOffset(-500);
                    Toast.makeText(this, "歌词提前 0.5s", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("延后 0.5s", (d, w) -> {
                    controller.adjustLyricOffset(500);
                    Toast.makeText(this, "歌词延后 0.5s", Toast.LENGTH_SHORT).show();
                })
                .setNeutralButton("重置", (d, w) -> {
                    controller.setLyricOffsetMs(0);
                    controller.adjustLyricOffset(0);
                    Toast.makeText(this, "歌词偏移已重置", Toast.LENGTH_SHORT).show();
                })
                .create();
        dialog.show();
    }

    private String buildOffsetMessage(long offsetMs) {
        if (offsetMs == 0) return "当前偏移：0ms（无偏移）\n长按专辑封面可再次调整";
        long abs = Math.abs(offsetMs);
        String dir = offsetMs > 0 ? "延后" : "提前";
        return "当前偏移：" + dir + " " + (abs / 1000.0) + "s\n长按专辑封面可再次调整";
    }

    /** 按钮按下缩放反馈动画 */
    private void playBtnPop(View v) {
        Animation pop = AnimationUtils.loadAnimation(this, R.anim.btn_pop);
        v.startAnimation(pop);
    }

    /** 校验权限；未授权时显示引导卡片并返回 false */
    private boolean ensurePermissionAndConnected() {
        if (!controller.isNotificationListenerEnabled(this)) {
            showPermissionCard(true);
            Toast.makeText(this, R.string.music_permission_required, Toast.LENGTH_LONG).show();
            return false;
        }
        if (!controller.isConnected()) {
            boolean found = controller.findActiveMusicSession();
            if (!found) {
                Toast.makeText(this, R.string.music_no_active_session, Toast.LENGTH_SHORT).show();
                return false;
            }
        }
        return true;
    }

    private void showPermissionCard(boolean show) {
        cardPermission.setVisibility(show ? View.VISIBLE : View.GONE);
    }

    private String repeatModeName(int mode) {
        switch (mode) {
            case 1: return getString(R.string.music_repeat_one);
            case 2: return getString(R.string.music_repeat_all);
            default: return getString(R.string.music_repeat_off);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        // 权限状态检查
        boolean permGranted = controller.isNotificationListenerEnabled(this);
        showPermissionCard(!permGranted);
        // 同步桌面歌词按钮状态（服务可能已被用户在悬浮窗长按关闭）
        updateFloatingLyricsButton(FloatingLyricsService.isRunning());
        // 同步“词”按钮与音乐来源图标状态
        updateLyricsToggleButton(lyricsContainer != null && lyricsContainer.getVisibility() == View.VISIBLE);
        updateMusicSourceIcon();
        // 注册回调并初始化（幂等）
        controller.initialize(this, this);
    }

    @Override
    protected void onPause() {
        super.onPause();
        controller.removeCallback(this);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (vinylAnimator != null) vinylAnimator.cancel();
        if (vinylGlowPulse != null) vinylGlowPulse.cancel();
        if (playBtnGlowPulse != null) playBtnGlowPulse.cancel();
        if (ambientGlow1Anim != null) ambientGlow1Anim.cancel();
        if (ambientGlow2Anim != null) ambientGlow2Anim.cancel();
    }

    // ============ MusicController.Callback ============

    @Override
    public void onConnected() {
        runOnUiThread(() -> {
            updateMusicSourceIcon();
        });
    }

    @Override
    public void onDisconnected() {
        runOnUiThread(() -> {
            // 断开连接时停止黑胶旋转
            if (vinylAnimator != null && vinylAnimator.isStarted()) {
                vinylAnimator.pause();
            }
            // 断开连接时恢复右侧按钮为默认列表图标
            if (btnPlaylist != null) btnPlaylist.setImageResource(R.drawable.ic_music_list);
        });
    }

    /** 更新最右侧按钮图标：显示当前播放音乐 app 的图标（无则为列表图标） */
    private void updateMusicSourceIcon() {
        if (btnPlaylist == null) return;
        String pkg = controller.getMusicPackageName();
        if (pkg != null && !pkg.isEmpty()) {
            try {
                android.graphics.drawable.Drawable icon = getPackageManager().getApplicationIcon(pkg);
                btnPlaylist.setImageDrawable(icon);
            } catch (android.content.pm.PackageManager.NameNotFoundException e) {
                btnPlaylist.setImageResource(R.drawable.ic_music_list);
            }
        } else {
            btnPlaylist.setImageResource(R.drawable.ic_music_list);
        }
    }

    @Override
    public void onLyricsChanged(String prev, String current, String next) {
        runOnUiThread(() -> {
            // 通过 LrcParser 获取当前行索引，再取前后各 2 行（共 5 行）
            int idx = controller.getLrcParser().getCurrentLineIndex(controller.getCurrentPosition());
            LrcParser.LrcLine prev2Line = idx >= 2 ? controller.getLrcParser().getLyricLine(idx - 2) : null;
            LrcParser.LrcLine prev1Line = idx >= 1 ? controller.getLrcParser().getLyricLine(idx - 1) : null;
            LrcParser.LrcLine currLine = idx >= 0 ? controller.getLrcParser().getLyricLine(idx) : null;
            LrcParser.LrcLine next1Line = controller.getLrcParser().getLyricLine(idx + 1);
            LrcParser.LrcLine next2Line = controller.getLrcParser().getLyricLine(idx + 2);

            tvLyricPrev2.setText(prev2Line != null ? prev2Line.text : "");
            tvLyricPrev.setText(prev1Line != null ? prev1Line.text : "");
            tvLyricNext.setText(next1Line != null ? next1Line.text : "");
            tvLyricNext2.setText(next2Line != null ? next2Line.text : "");

            // 当前行变化时播放淡入动画
            String newLyric = TextUtils.isEmpty(current) ? getString(R.string.music_no_lyrics) : current;
            if (!newLyric.equals(lastLyric)) {
                lastLyric = newLyric;
                tvLyricCurrent.setText(newLyric);
                Animation fadeIn = AnimationUtils.loadAnimation(this, R.anim.lyric_fade_in);
                tvLyricCurrent.startAnimation(fadeIn);
            } else {
                tvLyricCurrent.setText(newLyric);
            }
            // 显示翻译（如果有，紧跟当前行）
            if (tvLyricTranslation != null) {
                String trans = (currLine != null) ? currLine.translation : "";
                tvLyricTranslation.setText(trans);
                tvLyricTranslation.setVisibility(trans.isEmpty() ? View.GONE : View.VISIBLE);
            }
        });
    }

    @Override
    public void onMetadataChanged(String title, String artist, Bitmap albumArt) {
        runOnUiThread(() -> {
            tvTitle.setText(TextUtils.isEmpty(title) ? getString(R.string.music_no_song) : title);
            tvArtist.setText(TextUtils.isEmpty(artist) ? getString(R.string.music_unknown_artist) : artist);
            if (albumArt != null) {
                ivAlbum.setImageBitmap(albumArt);
                // 从封面提取主色调，动态更新背景色
                applyDynamicTheme(albumArt);
            } else {
                // 无封面时显示黑胶风格的占位图
                ivAlbum.setImageResource(R.drawable.ic_music_cover_placeholder);
                // 恢复默认深色背景
                applyBackgroundColor(0xFF0F1320, 0xFFEE0A24);
            }
        });
    }

    /**
     * 从专辑封面提取主色调，动态更新界面背景色（网易云/Spotify 风格）。
     * 在后台线程执行 Palette 提取，避免阻塞 UI。
     */
    private void applyDynamicTheme(Bitmap bitmap) {
        if (bitmap == null || bitmap.isRecycled()) return;
        // 注意：必须在后台线程执行，且对 Android 8.0+ 的 HARDWARE 位图（媒体会话封面常见）
        // 做兼容处理——createScaledBitmap/getPixels 在硬件位图上会抛 IllegalArgumentException。
        // 原先 scaleForPalette 在主线程、且在 try 之外调用，封面为硬件位图时会直接闪退本界面。
        new Thread(() -> {
            Bitmap scaled = null;
            try {
                scaled = scaleForPalette(bitmap);
                if (scaled == null) return;
                Palette palette = Palette.from(scaled).maximumColorCount(16).generate();
                // 优先使用 Vibrant，其次 DarkVibrant，再次 Muted，最后 DarkMuted
                int dominant = palette.getDominantColor(0xFF1A1F2E);
                int vibrant = palette.getVibrantColor(dominant);
                int darkVibrant = palette.getDarkVibrantColor(vibrant);
                int muted = palette.getMutedColor(darkVibrant);
                // 选择饱和度较高的颜色作为背景（偏深，保证文字可读性）
                int bgColor = darken(muted, 0.75f);
                int accentColor = palette.getLightVibrantColor(0xFFEE0A24);
                if (accentColor == 0xFFEE0A24) {
                    accentColor = palette.getVibrantColor(0xFFEE0A24);
                }
                final int finalBg = bgColor;
                final int finalAccent = accentColor;
                runOnUiThread(() -> applyBackgroundColor(finalBg, finalAccent));
            } catch (Exception e) {
                Log.w(TAG, "Palette extraction failed", e);
            } finally {
                // 仅回收我们新创建的缩放副本，绝不回收原始 albumArt（仍在 ImageView 使用）
                if (scaled != null && scaled != bitmap && !scaled.isRecycled()) {
                    scaled.recycle();
                }
            }
        }).start();
    }

    /**
     * 缩放 bitmap 到约 100px 以加速 Palette 提取。
     * 兼容 Android 8.0+ 的 HARDWARE 位图：媒体会话返回的专辑封面常为 HARDWARE 配置，
     * 必须先复制为 ARGB_8888 软件位图才能缩放/取像素，否则 createScaledBitmap 会抛异常。
     */
    private Bitmap scaleForPalette(Bitmap src) {
        if (src == null) return null;
        Bitmap working = src;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                && src.getConfig() == Bitmap.Config.HARDWARE) {
            try {
                Bitmap copy = src.copy(Bitmap.Config.ARGB_8888, false);
                if (copy != null) working = copy;
            } catch (Exception ignored) {
                // 复制失败则退回原始位图，交给上层 catch 处理
            }
        }
        int w = working.getWidth();
        int h = working.getHeight();
        if (w <= 0 || h <= 0) return working;
        if (w <= 100 && h <= 100) return working;
        float scale = Math.min(100f / w, 100f / h);
        int nw = Math.max(1, (int) (w * scale));
        int nh = Math.max(1, (int) (h * scale));
        try {
            return Bitmap.createScaledBitmap(working, nw, nh, true);
        } catch (Exception e) {
            // 极端情况下缩放失败，退回原始位图（已在 try 内，不会闪退）
            return working;
        }
    }

    /** 加深颜色（factor < 1 变深，> 1 变亮） */
    private int darken(int color, float factor) {
        int r = (int) (Color.red(color) * factor);
        int g = (int) (Color.green(color) * factor);
        int b = (int) (Color.blue(color) * factor);
        return Color.rgb(Math.min(255, Math.max(0, r)),
                         Math.min(255, Math.max(0, g)),
                         Math.min(255, Math.max(0, b)));
    }

    /**
     * 用 ValueAnimator 平滑过渡背景色（从 currentBgColor 到 targetColor）。
     * 使用垂直渐变（顶部深、底部稍浅），营造氛围感。
     */
    private void applyBackgroundColor(int targetColor, int accentColor) {
        if (musicRoot == null) return;
        int startColor = currentBgColor;
        // 创建渐变背景：顶部更深，底部为目标色
        int topColor = darken(targetColor, 0.55f);
        ValueAnimator animator = ValueAnimator.ofFloat(0f, 1f);
        animator.setDuration(800);
        animator.setInterpolator(new android.view.animation.DecelerateInterpolator());
        animator.addUpdateListener(animation -> {
            float fraction = animation.getAnimatedFraction();
            int curTop = (int) argbEvaluator.evaluate(fraction, darken(startColor, 0.55f), topColor);
            int curBot = (int) argbEvaluator.evaluate(fraction, startColor, targetColor);
            GradientDrawable gd = new GradientDrawable(
                    GradientDrawable.Orientation.TOP_BOTTOM,
                    new int[]{curTop, curBot});
            musicRoot.setBackground(gd);
        });
        animator.start();
        currentBgColor = targetColor;
        currentAccentColor = accentColor;
    }

    @Override
    public void onPlayStateChanged(boolean isPlaying) {
        runOnUiThread(() -> {
            btnPlay.setImageResource(
                    isPlaying ? R.drawable.ic_music_pause_hongqi : R.drawable.ic_music_play_hongqi);
            // 播放时启动黑胶旋转 + 光晕呼吸，暂停时停止
            if (vinylAnimator != null) {
                if (isPlaying) {
                    if (vinylAnimator.isPaused()) {
                        vinylAnimator.resume();
                    } else if (!vinylAnimator.isStarted()) {
                        vinylAnimator.start();
                    }
                } else {
                    if (vinylAnimator.isRunning()) {
                        vinylAnimator.pause();
                    }
                }
            }
            // 唱片外圈光晕：播放时显示 + 呼吸，暂停时隐藏
            if (vinylGlow != null) {
                if (isPlaying) {
                    vinylGlow.setVisibility(View.VISIBLE);
                    if (vinylGlowPulse != null) {
                        if (vinylGlowPulse.isPaused()) {
                            vinylGlowPulse.resume();
                        } else if (!vinylGlowPulse.isStarted()) {
                            vinylGlowPulse.start();
                        }
                    }
                } else {
                    vinylGlow.setVisibility(View.GONE);
                    if (vinylGlowPulse != null && vinylGlowPulse.isRunning()) {
                        vinylGlowPulse.pause();
                    }
                }
            }
            // 播放按钮外圈光晕：播放时显示 + 呼吸，暂停时隐藏
            if (playBtnGlow != null) {
                if (isPlaying) {
                    playBtnGlow.setVisibility(View.VISIBLE);
                    if (playBtnGlowPulse != null) {
                        if (playBtnGlowPulse.isPaused()) {
                            playBtnGlowPulse.resume();
                        } else if (!playBtnGlowPulse.isStarted()) {
                            playBtnGlowPulse.start();
                        }
                    }
                } else {
                    playBtnGlow.setVisibility(View.GONE);
                    if (playBtnGlowPulse != null && playBtnGlowPulse.isRunning()) {
                        playBtnGlowPulse.pause();
                    }
                }
            }
            // 唱臂：播放时落下压片，暂停/未播放时抬起（网易云经典效果）
            animateTonearm(isPlaying);
        });
    }

    @Override
    public void onProgressChanged(long current, long total) {
        runOnUiThread(() -> {
            tvCurrent.setText(formatTime(current));
            tvDuration.setText(formatTime(total));
            if (!userDragging && total > 0) {
                sbProgress.setProgress((int) (current * 100 / total));
            } else if (!userDragging) {
                sbProgress.setProgress(0);
            }
        });
    }

    @Override
    public void onRepeatModeChanged(int repeatMode) {
        runOnUiThread(() -> {
            int color;
            switch (repeatMode) {
                case 1: case 2:
                    // 循环开启：网易云红高亮
                    color = R.color.music_accent;
                    break;
                default:
                    // 循环关闭：浅色
                    color = R.color.music_lyric_other;
                    break;
            }
            btnRepeat.setColorFilter(ContextCompat.getColor(this, color));
        });
    }

    // ============ 工具 ============

    /** 时间格式化 mm:ss */
    private static String formatTime(long ms) {
        if (ms < 0) ms = 0;
        long totalSec = ms / 1000;
        long mm = totalSec / 60;
        long ss = totalSec % 60;
        return String.format("%02d:%02d", mm, ss);
    }
}
