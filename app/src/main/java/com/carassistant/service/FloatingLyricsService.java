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

package com.carassistant.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.preference.PreferenceManager;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import com.carassistant.R;
import com.carassistant.lyrics.LyricsPanelView;
import com.carassistant.ui.MusicActivity;
import com.carassistant.util.MusicController;

/**
 * 桌面歌词悬浮窗服务（1:1 复刻自歌词伴侣 LyricsDisplayService 的渲染能力）。
 *
 * 架构：
 * - {@link LyricsPanelView}：核心自定义视图，承担 5 种显示风格、卡拉 OK、调色板、
 *   动态背景、弹簧拖动浏览、播放控制按钮绘制与点击。其内部 onDraw 通过
 *   postInvalidateDelayed 自驱动持续刷新，无需外部轮询。
 * - 顶部拖动手柄条：半透明条带，处理悬浮窗整体位置移动（位置持久化）。
 * - 右上角关闭按钮：停止服务。
 *
 * 数据来源：{@link MusicController} 单例。通过 Callback 在元数据/播放状态/歌词变更时
 * 触发 panelView.invalidate() 启动一次刷新循环。
 *
 * 手势分工（避免与 LyricsPanelView 内部歌词浏览冲突）：
 * 1. 顶部手柄条拖动 → 移动整个悬浮窗位置
 * 2. 右上角 ×按钮 → 关闭悬浮窗
 * 3. LyricsPanelView 歌词区域上下滑动 → 浏览歌词（内部弹簧物理）
 * 4. LyricsPanelView 底部三按钮 → 上一首/播放暂停/下一首
 */
public class FloatingLyricsService extends Service implements MusicController.Callback {

    private static final String TAG = "FloatingLyricsSvc";
    private static final String CHANNEL_ID = "floating_lyrics_channel";
    private static final int NOTI_ID = 0x300;
    private static final String PREF_KEY_X = "float_lyrics_x";
    private static final String PREF_KEY_Y = "float_lyrics_y";
    private static final String PREF_KEY_WIDTH_DP = "float_lyrics_width_dp";
    private static final String PREF_KEY_HEIGHT_DP = "float_lyrics_height_dp";
    private static final String PREF_KEY_STYLE = "float_lyrics_style";
    private static final String PREF_KEY_OPACITY = "float_lyrics_opacity";

    /**
     * 默认面板尺寸（dp）—— 1:1 对齐原版 AppPreferences.defaultPanelWidthDp / defaultPanelHeightDp。
     * 不同风格使用不同默认尺寸，确保布局比例正确。
     */
    private static int defaultWidthDp(String overlayStyle) {
        switch (overlayStyle) {
            case "refined": return 560;
            case "compact": return 320;
            case "pip":     return 440;
            case "custom":  return 460;
            default:        return 390;
        }
    }
    private static int defaultHeightDp(String overlayStyle) {
        switch (overlayStyle) {
            case "refined": return 300;
            case "compact": return 80;
            case "pip":     return 220;
            case "custom":  return 260;
            default:        return 226;
        }
    }
    /** 最小面板尺寸（dp）—— 1:1 对齐原版 minimumPanelWidthDp / minimumPanelHeightDp */
    private static int minimumWidthDp(String overlayStyle) {
        return "compact".equals(overlayStyle) ? 220 : 240;
    }
    private static int minimumHeightDp(String overlayStyle) {
        return "compact".equals(overlayStyle) ? 72 : 176;
    }
    private WindowManager windowManager;
    private FrameLayout rootView;
    private LyricsPanelView panelView;
    private WindowManager.LayoutParams params;
    private LyricsPanelView.StyleConfig styleConfig;
    private Handler uiHandler;

    private static boolean running = false;

    public static boolean isRunning() { return running; }

    @Override
    public void onCreate() {
        super.onCreate();
        running = true;
        windowManager = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
        uiHandler = new Handler(Looper.getMainLooper());
        createNotificationChannel();
        boolean foregroundOk = false;
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(NOTI_ID, buildNotification(),
                        android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
            } else {
                startForeground(NOTI_ID, buildNotification());
            }
            foregroundOk = true;
        } catch (Exception e) {
            Log.e(TAG, "startForeground with type failed, retry without type", e);
            try {
                startForeground(NOTI_ID, buildNotification());
                foregroundOk = true;
            } catch (Exception e2) {
                Log.e(TAG, "startForeground fallback failed", e2);
            }
        }
        if (!foregroundOk) {
            running = false;
            stopSelf();
            return;
        }
        showFloatWindow();
        MusicController.getInstance().initialize(this, this);
    }

    private void createNotificationChannel() {
        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel ch = new NotificationChannel(CHANNEL_ID,
                    getString(R.string.floating_lyrics_title), NotificationManager.IMPORTANCE_LOW);
            ch.setDescription(getString(R.string.floating_lyrics_noti_desc));
            nm.createNotificationChannel(ch);
        }
    }

    private Notification buildNotification() {
        Intent contentIntent = new Intent(this, MusicActivity.class);
        contentIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent contentPi = PendingIntent.getActivity(this, 0, contentIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Intent stopIntent = new Intent(this, FloatingLyricsService.class);
        stopIntent.setAction("ACTION_STOP_SELF");
        PendingIntent stopPi = PendingIntent.getService(this, 1, stopIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Action stopAction = new NotificationCompat.Action.Builder(
                android.R.drawable.ic_menu_close_clear_cancel,
                getString(R.string.floating_lyrics_disabled), stopPi).build();

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle(getString(R.string.floating_lyrics_title))
                .setContentText(getString(R.string.floating_lyrics_drag_hint))
                .setSmallIcon(R.drawable.ic_feature_music)
                .setContentIntent(contentPi)
                .addAction(stopAction)
                .setOngoing(true)
                .build();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && "ACTION_STOP_SELF".equals(intent.getAction())) {
            stopSelf();
            return START_NOT_STICKY;
        }
        return START_STICKY;
    }

    private int overlayType() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    private void showFloatWindow() {
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(this);

        // ===== 风格配置（全部从 SharedPreferences 读取，1:1 复刻歌词伴侣 AppPreferences） =====
        styleConfig = new LyricsPanelView.StyleConfig();
        styleConfig.overlayStyle = sp.getString(PREF_KEY_STYLE, "refined");
        // 显示模式：all=封面+歌词（与原版默认一致，避免 "lyrics" 导致封面区域为 0）
        styleConfig.refinedDisplayMode = sp.getString("float_lyrics_refined_display", "all");
        styleConfig.opacity = sp.getInt(PREF_KEY_OPACITY, 88);
        // 歌词偏移：优先取设置页的值，其次取 MusicController 的运行时值
        int offsetPref = sp.getInt("float_lyrics_offset", Integer.MIN_VALUE);
        styleConfig.lyricOffsetMs = (offsetPref != Integer.MIN_VALUE)
                ? offsetPref
                : (int) MusicController.getInstance().getLyricOffsetMs();
        styleConfig.textScale = sp.getInt("float_lyrics_font_scale", 100) / 100f;
        styleConfig.coverScale = sp.getInt("float_lyrics_cover_scale", 100) / 100f;
        styleConfig.backgroundBlur = sp.getInt("float_lyrics_blur", 128);
        styleConfig.backgroundDim = sp.getInt("float_lyrics_dim", 38);
        styleConfig.lyricLineCount = Math.max(1, Math.min(3, sp.getInt("float_lyrics_lyric_lines", 3)));
        styleConfig.refinedBackgroundType = sp.getString("float_lyrics_bg_type", "blur");
        styleConfig.refinedColorScheme = sp.getString("float_lyrics_color_scheme", "auto");
        styleConfig.refinedAccentVariant = sp.getString("float_lyrics_accent_variant", "primary");
        styleConfig.refinedTextEffect = sp.getString("float_lyrics_text_effect", "none");
        styleConfig.refinedProgressBottom = sp.getBoolean("float_lyrics_progress_bottom", true);
        styleConfig.refinedCoverHorizontal = sp.getString("float_lyrics_cover_horizontal", "left");
        styleConfig.refinedCoverVertical = sp.getString("float_lyrics_cover_vertical", "bottom");
        styleConfig.refinedRectangleCover = sp.getBoolean("float_lyrics_rectangle_cover", true);
        styleConfig.refinedCoverShadow = sp.getBoolean("float_lyrics_cover_shadow", false);
        styleConfig.refinedStaticFluid = sp.getBoolean("float_lyrics_static_fluid", false);
        styleConfig.refinedDynamicGradient = sp.getBoolean("float_lyrics_dynamic_gradient", true);
        styleConfig.refinedLyricFontSize = Math.max(16, Math.min(64, sp.getInt("float_lyrics_lyric_font_size", 16)));
        styleConfig.refinedOriginalBold = sp.getBoolean("float_lyrics_original_bold", true);
        styleConfig.refinedShowTranslation = sp.getBoolean("float_lyrics_show_translation", true);
        styleConfig.refinedKaraokeAnimation = sp.getString("float_lyrics_karaoke_anim", "float");
        styleConfig.refinedLyricGlow = sp.getBoolean("float_lyrics_glow", true);
        styleConfig.refinedLyricBlur = sp.getBoolean("float_lyrics_lyric_blur", false);
        styleConfig.refinedLyricRotate = sp.getBoolean("float_lyrics_lyric_rotate", true);
        styleConfig.refinedLyricZoom = sp.getBoolean("float_lyrics_lyric_zoom", false);
        styleConfig.refinedLyricFade = sp.getBoolean("float_lyrics_lyric_fade", false);
        styleConfig.refinedRotateCurvature = Math.max(10, Math.min(80, sp.getInt("float_lyrics_rotate_curvature", 10)));
        try {
            styleConfig.refinedCurrentAlign = Integer.parseInt(sp.getString("float_lyrics_current_align", "50"));
        } catch (Exception e) {
            styleConfig.refinedCurrentAlign = 50;
        }
        styleConfig.compactShowCover = sp.getBoolean("float_lyrics_compact_cover", true);
        styleConfig.compactShowBars = sp.getBoolean("float_lyrics_compact_bars", true);
        styleConfig.showPreviousButton = sp.getBoolean("float_lyrics_show_prev", true);
        styleConfig.showPlayPauseButton = sp.getBoolean("float_lyrics_show_play", true);
        styleConfig.showNextButton = sp.getBoolean("float_lyrics_show_next", true);
        styleConfig.secondary = sp.getBoolean("float_lyrics_secondary", false);

        // ===== LyricsPanelView =====
        panelView = new LyricsPanelView(this, styleConfig);
        panelView.setControlListener(new LyricsPanelView.OnControlClickListener() {
            @Override
            public void onPrevious() { MusicController.getInstance().playPrevious(); }
            @Override
            public void onTogglePlayPause() {
                if (MusicController.getInstance().isPlaying()) {
                    MusicController.getInstance().pause();
                } else {
                    MusicController.getInstance().play();
                }
            }
            @Override
            public void onNext() { MusicController.getInstance().playNext(); }
        });
        // 拖动任意位置移动悬浮窗（歌词区域和按钮区域由 panelView 自行处理）
        panelView.setOnTouchListener(dragListener);

        // ===== 根布局 =====
        rootView = new FrameLayout(this);
        // 1:1 对齐原版：尺寸随 overlayStyle 变化，限制在 [minimum, displaySize-24dp] 范围内
        String overlayStyle = sp.getString(PREF_KEY_STYLE, "refined");
        DisplayMetrics dm = getResources().getDisplayMetrics();
        int displayW = dm.widthPixels;
        int displayH = dm.heightPixels;
        int widthDp = Math.min(
                sp.getInt(PREF_KEY_WIDTH_DP, defaultWidthDp(overlayStyle)),
                Math.max(minimumWidthDp(overlayStyle), Math.round((displayW / dm.density)) - 24));
        int heightDp = Math.min(
                sp.getInt(PREF_KEY_HEIGHT_DP, defaultHeightDp(overlayStyle)),
                Math.max(minimumHeightDp(overlayStyle), Math.round((displayH / dm.density)) - 24));
        int widthPx = dp(widthDp);
        int heightPx = dp(heightDp);

        // 1:1 对齐原版：LyricsPanelView 占满整个窗口
        FrameLayout.LayoutParams panelLp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT);
        rootView.addView(panelView, panelLp);

        // ===== WindowManager 参数 =====
        params = new WindowManager.LayoutParams(
                widthPx, heightPx,
                overlayType(),
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.TOP | Gravity.START;
        params.x = sp.getInt(PREF_KEY_X, 40);
        params.y = sp.getInt(PREF_KEY_Y, 160);

        try {
            windowManager.addView(rootView, params);
        } catch (Exception e) {
            Log.e(TAG, "addView failed", e);
            Toast.makeText(this, R.string.floating_lyrics_no_overlay_perm, Toast.LENGTH_LONG).show();
            stopSelf();
        }
    }

    /** 拖动监听：拖动任意位置移动悬浮窗，歌词区域和按钮区域交给 panelView 处理 */
    private final View.OnTouchListener dragListener = new View.OnTouchListener() {
        float downRawX, downRawY;
        int downX, downY;
        boolean lyricGesture;
        boolean moved;

        @Override
        public boolean onTouch(View v, MotionEvent event) {
            int action = event.getActionMasked();
            if (action == MotionEvent.ACTION_DOWN) {
                // 检查是否在歌词手势区域（交给 panelView 处理歌词浏览）
                lyricGesture = panelView.isLyricGestureRegion(event.getX(), event.getY());
                if (lyricGesture) return false;
                // 检查是否点击了播放控制按钮（交给 panelView 处理按钮点击）
                if (panelView.playbackControlAt(event.getX(), event.getY()) != null) return false;
                downRawX = event.getRawX();
                downRawY = event.getRawY();
                downX = params.x;
                downY = params.y;
                moved = false;
                return true;
            }
            if (lyricGesture) return false;
            if (action == MotionEvent.ACTION_MOVE) {
                float dx = event.getRawX() - downRawX;
                float dy = event.getRawY() - downRawY;
                if (Math.abs(dx) > dp(4) || Math.abs(dy) > dp(4)) moved = true;
                params.x = Math.max(0, downX + Math.round(dx));
                params.y = Math.max(0, downY + Math.round(dy));
                try {
                    windowManager.updateViewLayout(rootView, params);
                } catch (Exception ignored) {}
                return true;
            }
            if (action == MotionEvent.ACTION_UP) {
                savePosition();
                if (!moved) {
                    // 未移动则视为点击，打开主活动
                    v.performClick();
                    Intent intent = new Intent(FloatingLyricsService.this, MusicActivity.class);
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                    startActivity(intent);
                }
                return true;
            }
            if (action == MotionEvent.ACTION_CANCEL) {
                savePosition();
                return true;
            }
            return false;
        }
    };

    /** 保存当前位置 */
    private void savePosition() {
        if (params == null) return;
        PreferenceManager.getDefaultSharedPreferences(this)
                .edit()
                .putInt(PREF_KEY_X, params.x)
                .putInt(PREF_KEY_Y, params.y)
                .apply();
    }

    // ============ MusicController.Callback ============
    // 每次回调触发 panelView.invalidate()，启动一次 onDraw 循环（内部会自驱动持续刷新）

    @Override
    public void onConnected() {
        uiHandler.post(() -> { if (panelView != null) panelView.invalidate(); });
    }

    @Override
    public void onDisconnected() {
        uiHandler.post(() -> { if (panelView != null) panelView.invalidate(); });
    }

    @Override
    public void onLyricsChanged(String prev, String current, String next) {
        uiHandler.post(() -> { if (panelView != null) panelView.invalidate(); });
    }

    @Override
    public void onMetadataChanged(String title, String artist, Bitmap albumArt) {
        uiHandler.post(() -> { if (panelView != null) panelView.invalidate(); });
    }

    @Override
    public void onPlayStateChanged(boolean isPlaying) {
        uiHandler.post(() -> { if (panelView != null) panelView.invalidate(); });
    }

    @Override
    public void onProgressChanged(long current, long total) {
        // 同步用户调整的歌词偏移（用户可能在 MusicActivity 中调整了 offset）
        if (styleConfig != null) {
            long offset = MusicController.getInstance().getLyricOffsetMs();
            if (styleConfig.lyricOffsetMs != (int) offset) {
                styleConfig.lyricOffsetMs = (int) offset;
            }
        }
        // 进度变化由 LyricsPanelView 内部 onDraw 自驱动刷新，无需高频 invalidate
    }

    @Override
    public void onRepeatModeChanged(int repeatMode) {
        uiHandler.post(() -> { if (panelView != null) panelView.invalidate(); });
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        running = false;
        savePosition();
        MusicController.getInstance().removeCallback(this);
        if (rootView != null) {
            try { windowManager.removeView(rootView); } catch (Exception ignored) {}
            rootView = null;
        }
        panelView = null;
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) { return null; }
}
