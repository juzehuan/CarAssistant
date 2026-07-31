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

import android.animation.ObjectAnimator;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.view.animation.LinearInterpolator;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.carassistant.R;
import com.carassistant.util.MusicController;

/**
 * 音乐伴侣主界面（鸿启桌面风格）
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

    private MusicController controller;

    // 视图
    private View cardPermission;
    private View cardAlbum;
    private ImageView ivVinyl;
    private ImageView ivAlbum;
    private TextView tvTitle, tvArtist, tvStatus;
    private TextView tvLyricPrev, tvLyricCurrent, tvLyricNext;
    private TextView tvCurrent, tvDuration;
    private SeekBar sbProgress;
    private ImageView btnPlay, btnPrev, btnNext, btnRepeat;
    private View btnBack, btnGrant;

    /** 黑胶唱片旋转动画 */
    private ObjectAnimator vinylAnimator;
    /** 用户是否正在拖动进度条 */
    private boolean userDragging = false;

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
        cardPermission = findViewById(R.id.card_permission);
        cardAlbum = findViewById(R.id.card_album);
        ivVinyl = findViewById(R.id.iv_vinyl);
        ivAlbum = findViewById(R.id.iv_album);
        tvTitle = findViewById(R.id.tv_title);
        tvArtist = findViewById(R.id.tv_artist);
        tvStatus = findViewById(R.id.tv_status);
        tvLyricPrev = findViewById(R.id.tv_lyric_prev);
        tvLyricCurrent = findViewById(R.id.tv_lyric_current);
        tvLyricNext = findViewById(R.id.tv_lyric_next);
        tvCurrent = findViewById(R.id.tv_current);
        tvDuration = findViewById(R.id.tv_duration);
        sbProgress = findViewById(R.id.sb_progress);
        btnPlay = findViewById(R.id.btn_play);
        btnPrev = findViewById(R.id.btn_prev);
        btnNext = findViewById(R.id.btn_next);
        btnRepeat = findViewById(R.id.btn_repeat);
        btnBack = findViewById(R.id.btn_back);
        btnGrant = findViewById(R.id.btn_grant);
    }

    /** 初始化黑胶唱片旋转动画：360 度循环，线性插值，每圈 20 秒 */
    private void setupVinylAnimation() {
        vinylAnimator = ObjectAnimator.ofFloat(ivVinyl, "rotation", 0f, 360f);
        vinylAnimator.setDuration(20000);
        vinylAnimator.setInterpolator(new LinearInterpolator());
        vinylAnimator.setRepeatCount(ObjectAnimator.INFINITE);
        vinylAnimator.setRepeatMode(ObjectAnimator.RESTART);
        // 默认暂停状态
        vinylAnimator.pause();
    }

    private void setupListeners() {
        btnBack.setOnClickListener(v -> finish());
        btnGrant.setOnClickListener(v -> controller.openNotificationListenerSettings(this));

        btnPlay.setOnClickListener(v -> {
            if (!ensurePermissionAndConnected()) return;
            if (controller.isPlaying()) controller.pause();
            else controller.play();
        });
        btnPrev.setOnClickListener(v -> {
            if (!ensurePermissionAndConnected()) return;
            controller.playPrevious();
        });
        btnNext.setOnClickListener(v -> {
            if (!ensurePermissionAndConnected()) return;
            controller.playNext();
        });
        btnRepeat.setOnClickListener(v -> {
            // 切换循环模式：0 关闭 → 1 单曲 → 2 列表 → 0
            int next = (controller.getRepeatMode() + 1) % 3;
            controller.setRepeatMode(next);
            Toast.makeText(this, repeatModeName(next), Toast.LENGTH_SHORT).show();
        });

        sbProgress.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                if (fromUser) {
                    long dur = controller.getDuration();
                    long target = dur > 0 ? progress * dur / 100 : 0;
                    tvCurrent.setText(formatTime(target));
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
        if (vinylAnimator != null) {
            vinylAnimator.cancel();
        }
    }

    // ============ MusicController.Callback ============

    @Override
    public void onConnected() {
        runOnUiThread(() -> {
            tvStatus.setText(R.string.music_connected);
            tvStatus.setBackgroundResource(R.drawable.bg_status_enabled);
            tvStatus.setTextColor(ContextCompat.getColor(this, R.color.success));
        });
    }

    @Override
    public void onDisconnected() {
        runOnUiThread(() -> {
            tvStatus.setText(R.string.music_disconnected);
            tvStatus.setBackgroundResource(R.drawable.bg_status_disabled);
            tvStatus.setTextColor(ContextCompat.getColor(this, R.color.music_text_hint));
            // 断开连接时停止黑胶旋转
            if (vinylAnimator != null && vinylAnimator.isStarted()) {
                vinylAnimator.pause();
            }
        });
    }

    @Override
    public void onLyricsChanged(String prev, String current, String next) {
        runOnUiThread(() -> {
            tvLyricPrev.setText(TextUtils.isEmpty(prev) ? "" : prev);
            tvLyricCurrent.setText(TextUtils.isEmpty(current) ? getString(R.string.music_no_lyrics) : current);
            tvLyricNext.setText(TextUtils.isEmpty(next) ? "" : next);
        });
    }

    @Override
    public void onMetadataChanged(String title, String artist, Bitmap albumArt) {
        runOnUiThread(() -> {
            tvTitle.setText(TextUtils.isEmpty(title) ? getString(R.string.music_no_song) : title);
            tvArtist.setText(TextUtils.isEmpty(artist) ? getString(R.string.music_unknown_artist) : artist);
            if (albumArt != null) {
                ivAlbum.setImageBitmap(albumArt);
                ivAlbum.setPadding(0, 0, 0, 0);
                ivAlbum.setBackgroundResource(R.drawable.bg_album_circle);
            } else {
                // 无封面时显示黑胶风格的占位图
                ivAlbum.setImageResource(R.drawable.ic_music_cover_placeholder);
                ivAlbum.setBackgroundResource(R.drawable.bg_album_circle);
                ivAlbum.setPadding(0, 0, 0, 0);
            }
        });
    }

    @Override
    public void onPlayStateChanged(boolean isPlaying) {
        runOnUiThread(() -> {
            btnPlay.setImageResource(
                    isPlaying ? R.drawable.ic_music_pause_hongqi : R.drawable.ic_music_play_hongqi);
            // 播放时启动黑胶旋转，暂停时停止
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
                    // 循环开启：金色高亮
                    color = R.color.music_seekbar_progress;
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
