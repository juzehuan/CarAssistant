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
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Outline;
import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.view.animation.LinearInterpolator;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

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
    private TextView tvTitle, tvArtist;
    private TextView tvLyricPrev2, tvLyricPrev, tvLyricCurrent, tvLyricTranslation, tvLyricNext, tvLyricNext2;
    private TextView tvCurrent, tvDuration;
    private SeekBar sbProgress;
    private ImageView btnPlay, btnPrev, btnNext, btnRepeat;
    private ImageView btnFloatingLyrics;
    private ImageView btnLyricsSettings;
    private ImageView btnMusicSource;
    private View btnBack, btnGrant;
    // 特效视图
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
        cardPermission = findViewById(R.id.card_permission);
        cardAlbum = findViewById(R.id.card_album);
        ivVinyl = findViewById(R.id.iv_vinyl);
        ivAlbum = findViewById(R.id.iv_album);
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
    }

    private void setupListeners() {
        btnBack.setOnClickListener(v -> finish());
        btnGrant.setOnClickListener(v -> controller.openNotificationListenerSettings(this));
        btnFloatingLyrics.setOnClickListener(v -> toggleFloatingLyrics());
        btnLyricsSettings.setOnClickListener(v -> {
            Intent intent = new Intent(this, LyricsSettingsActivity.class);
            startActivity(intent);
        });

        btnPlay.setOnClickListener(v -> {
            playBtnPop(v);
            if (!ensurePermissionAndConnected()) return;
            if (controller.isPlaying()) controller.pause();
            else controller.play();
        });
        btnPrev.setOnClickListener(v -> {
            playBtnPop(v);
            if (!ensurePermissionAndConnected()) return;
            controller.playPrevious();
        });
        btnNext.setOnClickListener(v -> {
            playBtnPop(v);
            if (!ensurePermissionAndConnected()) return;
            controller.playNext();
        });
        btnRepeat.setOnClickListener(v -> {
            playBtnPop(v);
            // 切换循环模式：0 关闭 → 1 单曲 → 2 列表 → 0
            int next = (controller.getRepeatMode() + 1) % 3;
            controller.setRepeatMode(next);
            Toast.makeText(this, repeatModeName(next), Toast.LENGTH_SHORT).show();
        });

        // 音乐来源图标：点击跳转到对应音乐 app
        btnMusicSource.setOnClickListener(v -> {
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

        cardAlbum.setOnLongClickListener(v -> {
            showLyricOffsetDialog();
            return true;
        });
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
            // 断开连接时隐藏音乐来源图标
            btnMusicSource.setVisibility(View.GONE);
        });
    }

    /** 更新音乐来源图标：显示当前播放音乐 app 的图标 */
    private void updateMusicSourceIcon() {
        String pkg = controller.getMusicPackageName();
        if (pkg != null && !pkg.isEmpty()) {
            try {
                android.graphics.drawable.Drawable icon = getPackageManager().getApplicationIcon(pkg);
                btnMusicSource.setImageDrawable(icon);
                btnMusicSource.setVisibility(View.VISIBLE);
            } catch (android.content.pm.PackageManager.NameNotFoundException e) {
                btnMusicSource.setVisibility(View.GONE);
            }
        } else {
            btnMusicSource.setVisibility(View.GONE);
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
            } else {
                // 无封面时显示黑胶风格的占位图
                ivAlbum.setImageResource(R.drawable.ic_music_cover_placeholder);
            }
        });
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
