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

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.NonNull;

import com.carassistant.service.TargetMediaSessionService;

import java.io.InputStream;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * 音乐伴侣控制器（Java 重写自鸿启桌面 Kotlin 版 MusicController）
 *
 * 核心职责：
 * 1. 监听活跃媒体会话变化（复用已有 {@link TargetMediaSessionService}，不重复创建 NotificationListener）
 * 2. 注册 MediaController.Callback 接收播放状态/元数据变更
 * 3. 异步获取歌词（{@link LyricsApi}）并按位置推送三行歌词（上一行/当前行/下一行）
 * 4. 提供播放/暂停/上一首/下一首/拖动进度等控制接口
 * 5. 维护最近播放列表（最多 20 条）
 *
 * 与鸿启桌面版本的差异：
 * - 用 {@link Handler} + {@link Runnable} 替代 Kotlin 协程
 * - 不再单独创建 NotificationListenerService，复用 {@link TargetMediaSessionService}
 *   的静态 sActiveSessions，避免双重声明服务与权限冲突
 * - 通过 {@link MediaSessionManager#addOnActiveSessionsChangedListener} 监听会话变化
 */
public final class MusicController {

    private static final String TAG = "MusicController";

    /** 进度轮询间隔（ms） */
    private static final long PROGRESS_INTERVAL = 1000L;
    /** 寻找活跃会话轮询间隔（ms） */
    private static final long POLL_INTERVAL = 5000L;
    /** 最近歌曲上限 */
    private static final int MAX_RECENT_SONGS = 20;

    /** 已知音乐应用包名（用于识别音乐媒体会话） */
    private static final List<String> KNOWN_MUSIC_APPS = Collections.unmodifiableList(Arrays.asList(
            "com.netease.cloudmusic", "com.netease.cloudmusic.car",
            "com.tencent.qqmusic", "com.tencent.qqmusiccar",
            "com.kugou.android", "com.kugou.android.car",
            "com.kuwo.kwmusic", "cn.kuwo.kwmusicandroid", "cn.kuwo.player",
            "cn.kuwo.kwmusiccar", "cn.kuwo.kwmusic_car",
            "com.android.music", "com.google.android.music",
            "com.sonyericsson.music", "com.gd.music",
            "com.rdio.android", "com.pandora.android",
            "com.spotify.music", "com.apple.android.music",
            "com.android.car.media", "com.android.bluetooth", "com.android.systemui"
    ));

    /** 排除非音乐应用（电话/相机/录音/输入法等） */
    private static final List<String> NON_MUSIC_APPS = Collections.unmodifiableList(Arrays.asList(
            "com.android.dialer", "com.android.phone", "com.google.android.dialer",
            "com.android.incallui", "com.android.camera", "com.android.camera2",
            "com.google.android.camera", "com.android.soundrecorder",
            "com.android.voicedialer", "com.android.voicerecorder",
            "com.iflytek.inputmethod", "com.baidu.input", "com.sohu.inputmethod"
    ));

    /** 单例 */
    private static volatile MusicController INSTANCE;

    private final Object lock = new Object();
    private final List<Callback> callbacks = new ArrayList<>();
    private final List<RecentSong> recentSongs = new ArrayList<>();

    private Context context;
    private Handler handler;
    private MediaSessionManager sessionManager;
    private MediaController mediaController;
    private MediaController.Callback controllerCallback;
    private MediaSessionManager.OnActiveSessionsChangedListener sessionsListener;

    private final LrcParser lrcParser = new LrcParser();

    // 当前状态
    private String currentTitle = "";
    private String currentArtist = "";
    private Bitmap currentAlbumArt;
    private long currentPosition = 0L;
    private long currentDuration = 0L;
    private boolean currentIsPlaying = false;
    private int currentRepeatMode = 0;
    private boolean isConnected = false;

    // 轮询任务
    private Runnable pollRunnable;
    private Runnable progressRunnable;

    private MusicController() {}

    public static MusicController getInstance() {
        if (INSTANCE == null) {
            synchronized (MusicController.class) {
                if (INSTANCE == null) INSTANCE = new MusicController();
            }
        }
        return INSTANCE;
    }

    // ============ 公共 API ============

    /** 初始化（多次调用幂等，仅首次执行实际初始化） */
    public void initialize(@NonNull Context ctx, @NonNull Callback cb) {
        if (context == null) {
            context = ctx.getApplicationContext();
            handler = new Handler(Looper.getMainLooper());
            try {
                sessionManager = (MediaSessionManager) context.getSystemService(Context.MEDIA_SESSION_SERVICE);
            } catch (Exception e) {
                Log.w(TAG, "get MediaSessionManager failed", e);
            }
            registerActiveSessionsListener();
            startPolling();
            try {
                if (!findActiveMusicSession()) {
                    initLocalPlayer();
                }
            } catch (SecurityException e) {
                Log.w(TAG, "SecurityException in findActiveMusicSession", e);
                initLocalPlayer();
            } catch (Exception e) {
                Log.w(TAG, "findActiveMusicSession failed", e);
                initLocalPlayer();
            }
        }
        synchronized (lock) {
            if (!callbacks.contains(cb)) callbacks.add(cb);
        }
        dispatchCurrentState(cb);
    }

    /** 注销回调 */
    public void removeCallback(@NonNull Callback cb) {
        synchronized (lock) {
            callbacks.remove(cb);
        }
    }

    /** 释放资源（应用退出时调用） */
    public void release() {
        stopProgressUpdate();
        stopPolling();
        if (sessionsListener != null && sessionManager != null) {
            try {
                sessionManager.removeOnActiveSessionsChangedListener(sessionsListener);
            } catch (Exception ignored) {}
        }
        sessionsListener = null;
        if (mediaController != null && controllerCallback != null) {
            try {
                mediaController.unregisterCallback(controllerCallback);
            } catch (Exception ignored) {}
        }
        mediaController = null;
        controllerCallback = null;
        isConnected = false;
        synchronized (lock) {
            callbacks.clear();
        }
        context = null;
        sessionManager = null;
    }

    // ============ 控制接口 ============

    public void play() {
        if (isConnected && mediaController != null) {
            try {
                MediaController.TransportControls tc = mediaController.getTransportControls();
                if (tc != null) tc.play();
            } catch (Exception e) {
                Log.w(TAG, "play failed", e);
            }
        }
    }

    public void pause() {
        if (isConnected && mediaController != null) {
            try {
                MediaController.TransportControls tc = mediaController.getTransportControls();
                if (tc != null) tc.pause();
            } catch (Exception e) {
                Log.w(TAG, "pause failed", e);
            }
        }
    }

    public void playNext() {
        if (isConnected && mediaController != null) {
            try {
                MediaController.TransportControls tc = mediaController.getTransportControls();
                if (tc != null) tc.skipToNext();
            } catch (Exception e) {
                Log.w(TAG, "skipToNext failed", e);
            }
        }
    }

    public void playPrevious() {
        if (isConnected && mediaController != null) {
            try {
                MediaController.TransportControls tc = mediaController.getTransportControls();
                if (tc != null) tc.skipToPrevious();
            } catch (Exception e) {
                Log.w(TAG, "skipToPrevious failed", e);
            }
        }
    }

    public void seekTo(long position) {
        if (isConnected && mediaController != null) {
            try {
                MediaController.TransportControls tc = mediaController.getTransportControls();
                if (tc != null) tc.seekTo(position);
            } catch (Exception e) {
                Log.w(TAG, "seekTo failed", e);
            }
        }
        currentPosition = position;
        notifyProgressChanged(currentPosition, currentDuration);
    }

    public void setRepeatMode(int repeatMode) {
        if (isConnected && mediaController != null) {
            try {
                MediaController.TransportControls tc = mediaController.getTransportControls();
                if (tc != null) {
                    Method m = tc.getClass().getMethod("setRepeatMode", int.class);
                    m.invoke(tc, repeatMode);
                }
            } catch (Exception e) {
                Log.w(TAG, "setRepeatMode failed", e);
            }
        }
        currentRepeatMode = repeatMode;
        notifyRepeatModeChanged(repeatMode);
    }

    // ============ 状态查询 ============

    public boolean isPlaying() {
        if (isConnected && mediaController != null) {
            try {
                PlaybackState st = mediaController.getPlaybackState();
                return st != null && st.getState() == PlaybackState.STATE_PLAYING;
            } catch (Exception e) {
                return currentIsPlaying;
            }
        }
        return currentIsPlaying;
    }

    public boolean isConnected() { return isConnected; }

    public long getCurrentPosition() {
        if (isConnected && mediaController != null) {
            try {
                PlaybackState st = mediaController.getPlaybackState();
                if (st != null) return st.getPosition();
            } catch (Exception ignored) {}
        }
        return currentPosition;
    }

    public long getDuration() {
        if (isConnected && mediaController != null) {
            try {
                MediaMetadata md = mediaController.getMetadata();
                if (md != null) return md.getLong(MediaMetadata.METADATA_KEY_DURATION);
            } catch (Exception ignored) {}
        }
        return currentDuration;
    }

    public String getTitle() { return currentTitle; }
    public String getArtist() { return currentArtist; }
    public Bitmap getAlbumArt() { return currentAlbumArt; }
    public int getRepeatMode() { return currentRepeatMode; }
    public LrcParser getLrcParser() { return lrcParser; }

    public List<RecentSong> getRecentSongs() {
        synchronized (recentSongs) {
            return new ArrayList<>(recentSongs);
        }
    }

    /** 判断通知监听权限是否已授予（用于 UI 引导） */
    public boolean isNotificationListenerEnabled(@NonNull Context ctx) {
        ComponentName cn = new ComponentName(ctx, TargetMediaSessionService.class);
        String flat = Settings.Secure.getString(ctx.getContentResolver(), "enabled_notification_listeners");
        return flat != null && flat.contains(cn.flattenToString());
    }

    /** 跳转到通知访问设置 */
    public void openNotificationListenerSettings(@NonNull Context ctx) {
        try {
            ctx.startActivity(new Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS")
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (Exception e) {
            Log.w(TAG, "openNotificationListenerSettings failed", e);
        }
    }

    /** 跳转到指定音乐应用（点击卡片时使用） */
    public boolean launchMusicApp(@NonNull Context ctx) {
        if (mediaController != null) {
            String pkg = mediaController.getPackageName();
            if (!TextUtils.isEmpty(pkg)) {
                android.content.pm.PackageManager pm = ctx.getPackageManager();
                android.content.Intent it = pm.getLaunchIntentForPackage(pkg);
                if (it != null) {
                    it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    ctx.startActivity(it);
                    return true;
                }
            }
        }
        return false;
    }

    // ============ 内部实现 ============

    private void dispatchCurrentState(Callback cb) {
        if (isConnected) {
            cb.onMetadataChanged(currentTitle, currentArtist, currentAlbumArt);
            cb.onProgressChanged(currentPosition, currentDuration);
            cb.onPlayStateChanged(currentIsPlaying);
            cb.onConnected();
            cb.onRepeatModeChanged(currentRepeatMode);
        } else {
            cb.onDisconnected();
            cb.onMetadataChanged(currentTitle, currentArtist, currentAlbumArt);
            cb.onProgressChanged(currentPosition, currentDuration);
            cb.onPlayStateChanged(currentIsPlaying);
            cb.onRepeatModeChanged(currentRepeatMode);
        }
        LrcParser.LyricsTriple l = lrcParser.getLyricsAtPosition(currentPosition);
        cb.onLyricsChanged(l.prev, l.current, l.next);
    }

    private void registerActiveSessionsListener() {
        if (sessionManager == null) return;
        sessionsListener = controllers -> {
            Log.d(TAG, "onActiveSessionsChanged: " + (controllers == null ? 0 : controllers.size()));
            findActiveMusicSession();
        };
        ComponentName cn = new ComponentName(context, TargetMediaSessionService.class);
        try {
            sessionManager.addOnActiveSessionsChangedListener(sessionsListener, cn);
        } catch (SecurityException e) {
            Log.w(TAG, "no permission to register sessions listener", e);
        }
    }

    private void startPolling() {
        stopPolling();
        pollRunnable = new Runnable() {
            @Override
            public void run() {
                if (!isConnected) {
                    try {
                        findActiveMusicSession();
                    } catch (SecurityException e) {
                        Log.w(TAG, "polling SecurityException", e);
                    }
                }
                if (handler != null) handler.postDelayed(this, POLL_INTERVAL);
            }
        };
        if (handler != null) handler.postDelayed(pollRunnable, POLL_INTERVAL);
    }

    private void stopPolling() {
        if (pollRunnable != null && handler != null) {
            handler.removeCallbacks(pollRunnable);
        }
        pollRunnable = null;
    }

    /**
     * 查找活跃的音乐媒体会话并连接
     * @return true 表示已连接到一个有效会话
     */
    public boolean findActiveMusicSession() {
        if (sessionManager == null || context == null) return false;
        ComponentName cn = new ComponentName(context, TargetMediaSessionService.class);
        List<MediaController> controllers;
        try {
            controllers = sessionManager.getActiveSessions(cn);
        } catch (SecurityException e) {
            Log.w(TAG, "getActiveSessions denied", e);
            return false;
        }
        if (controllers == null || controllers.isEmpty()) return false;

        // 优先：正在播放的音乐会话
        MediaController candidate = null;
        for (MediaController mc : controllers) {
            if (!isMusicApp(mc.getPackageName())) continue;
            PlaybackState st = mc.getPlaybackState();
            if (st != null && st.getState() == PlaybackState.STATE_PLAYING) {
                candidate = mc;
                break;
            }
            if (candidate == null && hasValidMusicMetadata(mc)) {
                candidate = mc;
            }
        }
        if (candidate != null) {
            connectToController(candidate);
            return true;
        }
        return false;
    }

    /** 连接到指定 MediaController */
    private void connectToController(MediaController controller) {
        if (mediaController == controller) {
            dispatchCurrentStateToAll();
            return;
        }
        Log.d(TAG, "connectToController: " + controller.getPackageName());
        if (mediaController != null && controllerCallback != null) {
            try {
                mediaController.unregisterCallback(controllerCallback);
            } catch (Exception ignored) {}
        }
        mediaController = controller;
        controllerCallback = new MediaController.Callback() {
            @Override
            public void onPlaybackStateChanged(PlaybackState state) {
                if (state == null) return;
                boolean playing = state.getState() == PlaybackState.STATE_PLAYING;
                int repeatMode = readRepeatMode(state);
                notifyPlayStateChanged(playing);
                notifyRepeatModeChanged(repeatMode);
                long duration = currentDuration;
                if (mediaController != null) {
                    MediaMetadata md = mediaController.getMetadata();
                    if (md != null) {
                        duration = md.getLong(MediaMetadata.METADATA_KEY_DURATION);
                    }
                }
                notifyProgressChanged(state.getPosition(), duration);
            }

            @Override
            public void onMetadataChanged(MediaMetadata metadata) {
                if (metadata == null) return;
                String title = metadata.getString(MediaMetadata.METADATA_KEY_TITLE);
                if (TextUtils.isEmpty(title)) title = "未知歌曲";
                String artist = metadata.getString(MediaMetadata.METADATA_KEY_ARTIST);
                if (TextUtils.isEmpty(artist)) {
                    artist = metadata.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST);
                }
                if (TextUtils.isEmpty(artist)) artist = "未知歌手";
                Bitmap art = metadata.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART);
                if (art == null) art = metadata.getBitmap(MediaMetadata.METADATA_KEY_ART);
                if (art == null) art = metadata.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON);
                if (art == null) {
                    String uri = metadata.getString(MediaMetadata.METADATA_KEY_ALBUM_ART_URI);
                    if (!TextUtils.isEmpty(uri)) {
                        art = loadBitmapFromUri(Uri.parse(uri));
                    }
                }
                long duration = metadata.getLong(MediaMetadata.METADATA_KEY_DURATION);
                notifyMetadataChanged(title, artist, art);
                addRecentSong(title, artist, art);
                if (duration > 0) currentDuration = duration;
                if (mediaController != null) {
                    PlaybackState st = mediaController.getPlaybackState();
                    if (st != null) {
                        notifyProgressChanged(st.getPosition(), currentDuration);
                    }
                }
                fetchLyricsAsync(title, artist);
            }

            @Override
            public void onSessionDestroyed() {
                isConnected = false;
                stopProgressUpdate();
                notifyDisconnected();
                initLocalPlayer();
            }
        };
        try {
            controller.registerCallback(controllerCallback);
        } catch (Exception e) {
            Log.w(TAG, "registerCallback failed", e);
        }
        isConnected = true;

        // 立即派发当前元数据
        MediaMetadata md = controller.getMetadata();
        if (md != null) {
            String title = md.getString(MediaMetadata.METADATA_KEY_TITLE);
            if (TextUtils.isEmpty(title)) title = "未知歌曲";
            String artist = md.getString(MediaMetadata.METADATA_KEY_ARTIST);
            if (TextUtils.isEmpty(artist)) {
                artist = md.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST);
            }
            if (TextUtils.isEmpty(artist)) artist = "未知歌手";
            Bitmap art = md.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART);
            if (art == null) art = md.getBitmap(MediaMetadata.METADATA_KEY_ART);
            if (art == null) art = md.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON);
            if (art == null) {
                String uri = md.getString(MediaMetadata.METADATA_KEY_ALBUM_ART_URI);
                if (!TextUtils.isEmpty(uri)) art = loadBitmapFromUri(Uri.parse(uri));
            }
            long duration = md.getLong(MediaMetadata.METADATA_KEY_DURATION);
            if (duration <= 0) duration = 240_000L;
            currentDuration = duration;
            notifyMetadataChanged(title, artist, art);
            addRecentSong(title, artist, art);
        }
        PlaybackState st = controller.getPlaybackState();
        if (st != null) {
            boolean playing = st.getState() == PlaybackState.STATE_PLAYING;
            currentIsPlaying = playing;
            currentPosition = st.getPosition();
            currentRepeatMode = readRepeatMode(st);
            notifyPlayStateChanged(playing);
            notifyProgressChanged(currentPosition, currentDuration);
            notifyRepeatModeChanged(currentRepeatMode);
        }
        notifyConnected();
        startProgressUpdate();
        if (!TextUtils.isEmpty(currentTitle)) {
            fetchLyricsAsync(currentTitle, currentArtist);
        }
    }

    /** 反射读取 PlaybackState.getRepeatMode（部分系统 API 隐藏） */
    private int readRepeatMode(PlaybackState state) {
        try {
            Method m = state.getClass().getMethod("getRepeatMode");
            Object v = m.invoke(state);
            if (v instanceof Integer) return (Integer) v;
        } catch (Exception ignored) {}
        return 0;
    }

    private void startProgressUpdate() {
        stopProgressUpdate();
        progressRunnable = new Runnable() {
            @Override
            public void run() {
                if (isConnected && mediaController != null) {
                    try {
                        PlaybackState st = mediaController.getPlaybackState();
                        if (st != null) {
                            long duration = currentDuration;
                            MediaMetadata md = mediaController.getMetadata();
                            if (md != null) {
                                long d = md.getLong(MediaMetadata.METADATA_KEY_DURATION);
                                if (d > 0) duration = d;
                            }
                            notifyProgressChanged(st.getPosition(), duration);
                            updateLyrics(st.getPosition());
                        }
                    } catch (Exception e) {
                        Log.w(TAG, "progress update error", e);
                    }
                }
                if (handler != null) handler.postDelayed(this, PROGRESS_INTERVAL);
            }
        };
        if (handler != null) handler.post(progressRunnable);
    }

    private void stopProgressUpdate() {
        if (progressRunnable != null && handler != null) {
            handler.removeCallbacks(progressRunnable);
        }
        progressRunnable = null;
    }

    private void updateLyrics(long position) {
        LrcParser.LyricsTriple l = lrcParser.getLyricsAtPosition(position);
        notifyLyricsChanged(l.prev, l.current, l.next);
    }

    private void fetchLyricsAsync(final String title, final String artist) {
        lrcParser.clear();
        notifyLyricsChanged("", "歌词加载中...", "");
        LyricsApi.getInstance().fetchLyrics(artist, title, context, new LyricsApi.Callback() {
            @Override
            public void onSuccess(String lrcContent) {
                if (handler != null) {
                    handler.post(() -> {
                        boolean ok = lrcParser.parse(lrcContent);
                        Log.d(TAG, "lyrics parsed: " + ok + ", lines=" + lrcParser.getTotalLines());
                        updateLyrics(currentPosition);
                    });
                }
            }

            @Override
            public void onFailure(String error) {
                if (handler != null) {
                    handler.post(() -> notifyLyricsChanged("", "暂无歌词", ""));
                }
            }
        });
    }

    /** 切换到本地播放器状态（无活跃会话时） */
    private void initLocalPlayer() {
        Log.d(TAG, "initLocalPlayer: no active music session");
        isConnected = false;
        currentTitle = "暂无音乐播放";
        currentArtist = "请打开音乐应用";
        currentDuration = 0L;
        currentPosition = 0L;
        currentIsPlaying = false;
        currentAlbumArt = null;
        currentRepeatMode = 0;
        notifyMetadataChanged(currentTitle, currentArtist, null);
        notifyProgressChanged(currentPosition, currentDuration);
        notifyPlayStateChanged(false);
        notifyLyricsChanged("", "点击卡片打开音乐应用", "");
        notifyDisconnected();
        notifyRepeatModeChanged(0);
    }

    /** 加载 album art URI 为 Bitmap（采样压缩） */
    private Bitmap loadBitmapFromUri(Uri uri) {
        if (context == null || uri == null) return null;
        InputStream is = null;
        try {
            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inJustDecodeBounds = true;
            is = context.getContentResolver().openInputStream(uri);
            if (is != null) BitmapFactory.decodeStream(is, null, opts);
            if (is != null) { is.close(); is = null; }
            int sample = 1;
            while (opts.outWidth / sample > 256 || opts.outHeight / sample > 256) {
                sample *= 2;
            }
            BitmapFactory.Options decode = new BitmapFactory.Options();
            decode.inSampleSize = sample;
            decode.inPreferredConfig = Bitmap.Config.RGB_565;
            is = context.getContentResolver().openInputStream(uri);
            return is != null ? BitmapFactory.decodeStream(is, null, decode) : null;
        } catch (Exception e) {
            Log.w(TAG, "loadBitmapFromUri failed: " + uri, e);
            return null;
        } finally {
            if (is != null) try { is.close(); } catch (Exception ignored) {}
        }
    }

    private boolean isMusicApp(String pkg) {
        if (TextUtils.isEmpty(pkg) || NON_MUSIC_APPS.contains(pkg)) return false;
        if (KNOWN_MUSIC_APPS.contains(pkg)) return true;
        String lower = pkg.toLowerCase(Locale.ROOT);
        return lower.contains("music") || lower.contains("kugou") || lower.contains("kuwo")
                || lower.contains("audio") || lower.contains("media") || lower.contains("player")
                || lower.contains("spotify") || lower.contains("qqmusic")
                || lower.contains("cloudmusic") || lower.contains("bilibili")
                || lower.contains("ytmusic") || lower.contains("youtube");
    }

    private boolean hasValidMusicMetadata(MediaController mc) {
        if (mc == null) return false;
        MediaMetadata md = mc.getMetadata();
        if (md == null) return false;
        String title = md.getString(MediaMetadata.METADATA_KEY_TITLE);
        return !TextUtils.isEmpty(title);
    }

    // ============ 状态派发 ============

    private void dispatchCurrentStateToAll() {
        List<Callback> snapshot;
        synchronized (lock) {
            snapshot = new ArrayList<>(callbacks);
        }
        for (Callback cb : snapshot) dispatchCurrentState(cb);
    }

    private void notifyConnected() {
        List<Callback> snapshot;
        synchronized (lock) {
            snapshot = new ArrayList<>(callbacks);
        }
        for (Callback cb : snapshot) cb.onConnected();
    }

    private void notifyDisconnected() {
        List<Callback> snapshot;
        synchronized (lock) {
            snapshot = new ArrayList<>(callbacks);
        }
        for (Callback cb : snapshot) cb.onDisconnected();
    }

    private void notifyMetadataChanged(String title, String artist, Bitmap art) {
        currentTitle = title;
        currentArtist = artist;
        currentAlbumArt = art;
        List<Callback> snapshot;
        synchronized (lock) {
            snapshot = new ArrayList<>(callbacks);
        }
        for (Callback cb : snapshot) cb.onMetadataChanged(title, artist, art);
    }

    private void notifyPlayStateChanged(boolean playing) {
        currentIsPlaying = playing;
        List<Callback> snapshot;
        synchronized (lock) {
            snapshot = new ArrayList<>(callbacks);
        }
        for (Callback cb : snapshot) cb.onPlayStateChanged(playing);
    }

    private void notifyProgressChanged(long current, long total) {
        currentPosition = current;
        currentDuration = total;
        List<Callback> snapshot;
        synchronized (lock) {
            snapshot = new ArrayList<>(callbacks);
        }
        for (Callback cb : snapshot) cb.onProgressChanged(current, total);
    }

    private void notifyLyricsChanged(String prev, String current, String next) {
        List<Callback> snapshot;
        synchronized (lock) {
            snapshot = new ArrayList<>(callbacks);
        }
        for (Callback cb : snapshot) cb.onLyricsChanged(prev, current, next);
    }

    private void notifyRepeatModeChanged(int mode) {
        currentRepeatMode = mode;
        List<Callback> snapshot;
        synchronized (lock) {
            snapshot = new ArrayList<>(callbacks);
        }
        for (Callback cb : snapshot) cb.onRepeatModeChanged(mode);
    }

    // ============ 最近播放 ============

    private void addRecentSong(String title, String artist, Bitmap art) {
        if (TextUtils.isEmpty(title) || "暂无音乐播放".equals(title)) return;
        synchronized (recentSongs) {
            // 去重：相同标题+歌手
            for (int i = recentSongs.size() - 1; i >= 0; i--) {
                RecentSong s = recentSongs.get(i);
                if (TextUtils.equals(s.title, title) && TextUtils.equals(s.artist, artist)) {
                    recentSongs.remove(i);
                }
            }
            recentSongs.add(0, new RecentSong(title, artist, art, System.currentTimeMillis()));
            while (recentSongs.size() > MAX_RECENT_SONGS) {
                recentSongs.remove(recentSongs.size() - 1);
            }
        }
    }

    // ============ 内部类型 ============

    public interface Callback {
        void onConnected();
        void onDisconnected();
        void onLyricsChanged(String prev, String current, String next);
        void onMetadataChanged(String title, String artist, Bitmap albumArt);
        void onPlayStateChanged(boolean isPlaying);
        void onProgressChanged(long current, long total);
        void onRepeatModeChanged(int repeatMode);
    }

    public static final class RecentSong {
        public final String title;
        public final String artist;
        public final Bitmap albumArt;
        public final long timestamp;

        public RecentSong(String title, String artist, Bitmap albumArt, long timestamp) {
            this.title = title == null ? "" : title;
            this.artist = artist == null ? "" : artist;
            this.albumArt = albumArt;
            this.timestamp = timestamp;
        }
    }
}
