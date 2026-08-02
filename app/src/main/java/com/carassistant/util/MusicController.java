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
import android.media.session.PlaybackState;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.carassistant.lyrics.LrcTimeline;
import com.carassistant.lyrics.LyricResult;
import com.carassistant.lyrics.MultiSourceLyricClient;
import com.carassistant.lyrics.MusicSnapshot;
import com.carassistant.music.MusicAppRegistry;
import com.carassistant.music.MusicSessionWatcher;
import com.carassistant.service.TargetMediaSessionService;

import java.io.InputStream;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * 音乐伴侣控制器（Java 重写自鸿启桌面 Kotlin 版 MusicController）
 *
 * 核心职责：
 * 1. 通过 {@link MusicSessionWatcher} 监听活跃媒体会话变化（1:1 对齐歌词伴侣监听逻辑）
 * 2. 在 watcher 回调中读取播放状态/元数据，触发歌词加载/封面获取
 * 3. 异步获取歌词（{@link MultiSourceLyricClient}）并按位置推送三行歌词
 * 4. 提供播放/暂停/上一首/下一首/拖动进度等控制接口
 * 5. 维护最近播放列表（最多 20 条）
 *
 * 与歌词伴侣监听架构的对齐：
 * - watcher 监听会话列表增减 + 全会话 callback + 600ms 轮询 + 通知触发
 * - TargetMediaSessionService.onNotificationPosted/Removed 转发给 watcher.refresh
 * - 健康监测：watcher 周期检测，不健康时调 TargetMediaSessionService.requestReconnect
 * - 打分选择：MusicAppRegistry.selectionScore 综合播放状态/元数据/控制能力
 */
public final class MusicController {

    private static final String TAG = "MusicController";

    /** 进度轮询间隔（ms） */
    private static final long PROGRESS_INTERVAL = 1000L;
    /** 最近歌曲上限 */
    private static final int MAX_RECENT_SONGS = 20;

    /** 单例 */
    private static volatile MusicController INSTANCE;

    private final Object lock = new Object();
    private final List<Callback> callbacks = new ArrayList<>();
    private final List<RecentSong> recentSongs = new ArrayList<>();

    private Context context;
    private Handler handler;
    private MediaController mediaController;
    /** watcher 监听者：接收会话选择/状态变化回调 */
    private final MusicSessionWatcher.Listener watcherListener = new MusicSessionWatcher.Listener() {
        @Override
        public void onSessionSelected(@NonNull MediaController controller,
                                      @Nullable MediaController oldController) {
            connectToController(controller);
        }

        @Override
        public void onSessionData(@NonNull MediaController controller,
                                  @Nullable MediaMetadata metadata,
                                  @Nullable PlaybackState playbackState) {
            // 同一会话内更新（切歌/暂停/播放等）
            onSessionDataUpdate(controller, metadata, playbackState);
        }

        @Override
        public void onSessionLost() {
            initLocalPlayer();
        }
    };

    private final LrcParser lrcParser = new LrcParser();

    // ============ 歌词时间线状态（供 LyricsPanelView 渲染使用） ============
    /** 当前歌词时间线（支持行级 + 逐字 + 翻译） */
    private volatile LrcTimeline currentTimeline = LrcTimeline.EMPTY;
    /** 当前歌词源名称（如 "网易云音乐"、"QQ音乐"） */
    private volatile String currentLyricSourceName = "";
    /** 歌词是否已加载完成（无论是否拿到内容） */
    private volatile boolean currentLyricLoaded = false;
    /** 当前曲目媒体 ID（用于 NetEase/Soda 直查） */
    private volatile String currentMediaId = "";
    /** 当前播放器友好名称（用于 UI 显示） */
    private volatile String currentSourceName = "音乐播放器";
    /** 当前播放器源 ID（netease/qqmusic/kugou/kuwo/soda），用于多源歌词优先级 */
    private volatile String currentSourceId = "";
    /** 曲目代际：每次元数据变更自增，用于丢弃过期歌词回调 */
    private volatile long trackGeneration = 0L;

    /** 实时位置推算基准（PlaybackState.getPosition() 的快照） */
    private volatile long basePositionMs = 0L;
    /** basePositionMs 快照时刻的 elapsedRealtime */
    private volatile long positionUpdatedAtElapsedMs = 0L;
    /** 播放速度（1.0 = 正常） */
    private volatile float playbackSpeed = 1.0f;

    /** 多源歌词客户端（懒初始化） */
    private volatile MultiSourceLyricClient lyricClient;
    /** 歌词加载线程池（缓存线程，避免并发限制） */
    private final ExecutorService lyricExecutor = Executors.newCachedThreadPool();
    /** 当前歌词加载任务（用于取消） */
    private volatile Future<?> lyricLoadTask;

    // 当前状态
    private String currentTitle = "";
    private String currentArtist = "";
    private Bitmap currentAlbumArt;
    private long currentPosition = 0L;
    private long currentDuration = 0L;
    private boolean currentIsPlaying = false;
    private int currentRepeatMode = 0;
    private boolean isConnected = false;
    private long lyricOffsetMs = 0L;

    // 进度轮询任务
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
            if (lyricClient == null) {
                lyricClient = new MultiSourceLyricClient(context);
            }
            // 启动 watcher（替代原 registerActiveSessionsListener + startPolling）
            // watcher 内部完成：sessionsChanged + 600ms 轮询 + 全会话 callback + 健康监测
            MusicSessionWatcher.getInstance(context).start(watcherListener);
            // 立即触发一次刷新（watcher 内部 start 已会 refresh，这里冗余一次确保最快响应）
            try {
                MusicSessionWatcher.getInstance(context).refresh();
            } catch (Exception ignored) {}
            // 若 watcher 选中失败（无可用会话），走本地播放器兜底
            if (mediaController == null) {
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
        if (lyricLoadTask != null) {
            lyricLoadTask.cancel(true);
            lyricLoadTask = null;
        }
        try {
            lyricExecutor.shutdownNow();
        } catch (Exception ignored) {}
        // watcher 是应用级单例，release 时不 stop（避免影响其他调用方）
        // 仅解除当前 controller 的 listener 关联
        if (mediaController != null) {
            // watcher 会自动管理 callback 注销，这里只需清理本地引用
        }
        mediaController = null;
        isConnected = false;
        currentTimeline = LrcTimeline.EMPTY;
        currentLyricLoaded = false;
        currentLyricSourceName = "";
        synchronized (lock) {
            callbacks.clear();
        }
        context = null;
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

    /** 获取当前音乐来源应用包名 */
    public String getMusicPackageName() {
        if (mediaController != null) {
            try {
                return mediaController.getPackageName();
            } catch (Exception ignored) {}
        }
        return null;
    }

    // ============ LyricsPanelView 渲染所需的快照 API ============

    /**
     * 获取当前播放位置的歌词快照（供 LyricsPanelView.onDraw 调用）。
     *
     * @param offsetMs 用户偏移（毫秒），正值延后歌词、负值提前
     * @return 不可变快照，永远不会 null
     */
    public MusicSnapshot snapshot(int offsetMs) {
        long pos = currentPositionMs();
        return buildSnapshot(pos, Math.max(0L, offsetMs + pos));
    }

    /**
     * 获取拖动浏览位置的歌词快照（不依赖实时播放位置）。
     *
     * @param offsetMs         用户偏移（毫秒）
     * @param browsePositionMs 用户拖动到的目标播放位置
     * @return 不可变快照，永远不会 null
     */
    public MusicSnapshot snapshotForLyricBrowse(int offsetMs, long browsePositionMs) {
        return buildSnapshot(Math.max(0L, browsePositionMs - offsetMs),
                Math.max(0L, browsePositionMs));
    }

    /**
     * 行跳转：从 positionMs 偏移 delta 行（±1/±2...），返回目标行的起始时间。
     * 用于 LyricsPanelView 拖动浏览时计算目标定位。
     */
    public long shiftLyricPosition(long positionMs, int delta) {
        return currentTimeline.shiftedPosition(positionMs, delta);
    }

    /** 推算当前实时播放位置（基于基准位置 + 速度 * 经过时间） */
    private long currentPositionMs() {
        if (!currentIsPlaying) return basePositionMs;
        long elapsed = SystemClock.elapsedRealtime() - positionUpdatedAtElapsedMs;
        if (elapsed <= 0) return basePositionMs;
        long advanced = (long) (elapsed * playbackSpeed);
        long pos = basePositionMs + advanced;
        if (currentDuration > 0 && pos > currentDuration) pos = currentDuration;
        return Math.max(0L, pos);
    }

    /** 构建不可变快照 */
    private MusicSnapshot buildSnapshot(long rawPositionMs, long lyricPositionMs) {
        LrcTimeline timeline = currentTimeline;
        LrcTimeline.At at = timeline.isEmpty() ? LrcTimeline.At.EMPTY : timeline.at(lyricPositionMs);
        boolean lyricAvailable = !timeline.isEmpty();
        return new MusicSnapshot(
                isConnected,
                currentIsPlaying,
                currentSourceName,
                currentTitle,
                currentArtist,
                currentAlbumArt,
                currentDuration > 0 ? currentDuration : -1L,
                rawPositionMs,
                currentLyricLoaded,
                lyricAvailable,
                currentLyricSourceName,
                at
        );
    }

    public synchronized long getLyricOffsetMs() { return lyricOffsetMs; }

    public synchronized void setLyricOffsetMs(long ms) { lyricOffsetMs = ms; }

    public void adjustLyricOffset(long deltaMs) {
        lyricOffsetMs += deltaMs;
        updateLyrics(currentPosition);
    }

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
        long shifted = currentPosition - lyricOffsetMs;
        if (shifted < 0) shifted = 0;
        LrcParser.LyricsTriple l = lrcParser.getLyricsAtPosition(shifted);
        cb.onLyricsChanged(l.prev, l.current, l.next);
    }

    /**
     * 触发会话刷新（外部 API，兼容旧调用方）。
     *
     * 1:1 对齐歌词伴侣：实际由 {@link MusicSessionWatcher#refresh()} 处理，
     * watcher 内部完成打分选择，结果通过 {@link #watcherListener} 回调。
     *
     * @return true 表示当前已连接到有效会话
     */
    public boolean findActiveMusicSession() {
        if (context == null) return false;
        try {
            MusicSessionWatcher.getInstance(context).refresh();
        } catch (Exception ignored) {}
        return isConnected && mediaController != null;
    }

    /**
     * 连接到指定 MediaController（由 watcher.onSessionSelected 触发）。
     *
     * 与旧实现的差异：
     * - 不再注册单个 MediaController.Callback（callback 由 watcher 统一管理所有会话）
     * - 元数据/状态更新改为通过 {@link #onSessionDataUpdate} 接收 watcher 派发
     * - 仍负责：读取初始元数据、派发连接/元数据/播放状态、启动进度轮询、加载歌词
     */
    private void connectToController(MediaController controller) {
        if (controller == null) {
            return;
        }
        // 同一会话：不重复初始化（watcher 已通过 onSessionData 派发增量更新）
        if (mediaController == controller) {
            dispatchCurrentStateToAll();
            return;
        }
        // 切换会话前停止旧会话的进度轮询
        stopProgressUpdate();

        mediaController = controller;
        isConnected = true;

        // 设置播放器源信息（基于包名识别，1:1 对齐歌词伴侣 MusicAppRegistry）
        String pkg = controller.getPackageName();
        MusicAppRegistry.App app = MusicAppRegistry.resolve(pkg, applicationLabel(pkg));
        currentSourceId = app.sourceId;
        currentSourceName = app.displayName;
        Log.i(TAG, "connectToController: pkg=" + pkg + " source=" + currentSourceName + " title=" + currentTitle);

        // 切歌：重置歌词时间线
        trackGeneration++;
        currentTimeline = LrcTimeline.EMPTY;
        currentLyricLoaded = false;
        currentLyricSourceName = "";
        lrcParser.clear();

        // 读取初始元数据
        MediaMetadata md = controller.getMetadata();
        if (md != null) {
            applyMetadata(md);
        }
        // 读取初始播放状态
        PlaybackState st = controller.getPlaybackState();
        if (st != null) {
            applyPlaybackState(st);
        }
        notifyConnected();
        startProgressUpdate();
        if (!TextUtils.isEmpty(currentTitle)) {
            fetchLyricsAsync(currentTitle, currentArtist);
        }
    }

    /** 判断两个 MediaController 是否对应同一会话（按 token 比较，而非对象引用） */
    private static boolean isSameToken(MediaController a, MediaController b) {
        if (a == b) return true;
        if (a == null || b == null) return false;
        try {
            return a.getSessionToken().equals(b.getSessionToken());
        } catch (Throwable t) {
            return false;
        }
    }

    /**
     * 同一会话内的元数据/状态刷新（由 watcher.onSessionData 触发）。
     *
     * 判断 title 是否变化来决定行为：
     * - title 变化（切歌）：重置歌词时间线 + 重新加载歌词 + 重新获取封面
     * - title 不变：仅更新播放状态/进度
     */
    private void onSessionDataUpdate(MediaController controller, MediaMetadata md, PlaybackState st) {
        if (controller == null) {
            return;
        }
        // 关键修复：getActiveSessions() 每次返回新的 MediaController 实例，同一会话的 token
        // 不变但对象引用不同。若用引用比较（mediaController != controller）会误判为"会话已切换"
        // 从而丢弃同一会话内的切歌更新，导致歌词/封面不刷新。必须用 token 比较。
        if (mediaController == null || !isSameToken(mediaController, controller)) {
            return;
        }
        // 用最新实例替换本地引用，保证后续 isPlaying()/getMetadata() 等读取到最新数据
        mediaController = controller;
        // 1. 元数据变化（切歌检测）
        if (md != null) {
            String newTitle = md.getString(MediaMetadata.METADATA_KEY_TITLE);
            if (TextUtils.isEmpty(newTitle)) {
                newTitle = md.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE);
            }
            if (TextUtils.isEmpty(newTitle)) newTitle = "未知歌曲";
            // 切歌判断：title 变化时重置歌词
            boolean songChanged = !TextUtils.equals(newTitle, currentTitle);
            if (songChanged) {
                // 切歌：重置歌词时间线
                trackGeneration++;
                currentTimeline = LrcTimeline.EMPTY;
                currentLyricLoaded = false;
                currentLyricSourceName = "";
                lrcParser.clear();
            }
            // 应用元数据（无论是否切歌，封面/歌手等都可能更新）
            applyMetadata(md);
            if (songChanged) {
                fetchLyricsAsync(currentTitle, currentArtist);
            }
        }
        // 2. 播放状态变化
        if (st != null) {
            applyPlaybackState(st);
        }
    }

    /**
     * 应用元数据（提取标题/歌手/封面/时长，派发 UI 更新，触发封面兜底）。
     * connectToController 和 onSessionDataUpdate 共用。
     */
    private void applyMetadata(MediaMetadata md) {
        if (md == null) return;
        // 标题（1:1 对齐歌词伴侣：TITLE → DISPLAY_TITLE 回退）
        String title = md.getString(MediaMetadata.METADATA_KEY_TITLE);
        if (TextUtils.isEmpty(title)) title = md.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE);
        if (TextUtils.isEmpty(title)) title = "未知歌曲";
        // 歌手（ARTIST → ALBUM_ARTIST → DISPLAY_SUBTITLE）
        String artist = md.getString(MediaMetadata.METADATA_KEY_ARTIST);
        if (TextUtils.isEmpty(artist)) artist = md.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST);
        if (TextUtils.isEmpty(artist)) artist = md.getString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE);
        if (TextUtils.isEmpty(artist)) artist = "未知歌手";
        // 封面（ALBUM_ART → ART → DISPLAY_ICON → URI 回退）
        Bitmap art = md.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART);
        if (art == null) art = md.getBitmap(MediaMetadata.METADATA_KEY_ART);
        if (art == null) art = md.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON);
        if (art == null) {
            String uri = md.getString(MediaMetadata.METADATA_KEY_ALBUM_ART_URI);
            if (TextUtils.isEmpty(uri)) uri = md.getString(MediaMetadata.METADATA_KEY_ART_URI);
            if (!TextUtils.isEmpty(uri)) art = loadBitmapFromUri(Uri.parse(uri));
        }
        long duration = md.getLong(MediaMetadata.METADATA_KEY_DURATION);
        String mediaId = md.getString(MediaMetadata.METADATA_KEY_MEDIA_ID);
        currentMediaId = mediaId == null ? "" : mediaId;
        if (duration > 0) currentDuration = duration;
        notifyMetadataChanged(title, artist, art);
        addRecentSong(title, artist, art);
        // 封面回退：MediaMetadata 未提供封面时，异步从 QQ/酷狗搜索
        if (art == null) {
            fetchCoverFallback(title, artist, duration);
        }
    }

    /**
     * 应用播放状态（更新播放/暂停/位置/速度/循环模式，派发 UI）。
     * connectToController 和 onSessionDataUpdate 共用。
     */
    private void applyPlaybackState(PlaybackState state) {
        if (state == null) return;
        boolean playing = state.getState() == PlaybackState.STATE_PLAYING;
        int repeatMode = readRepeatMode(state);
        float speed = state.getPlaybackSpeed();
        if (speed <= 0f) speed = 1.0f;
        long duration = currentDuration;
        // 更新实时位置推算基准
        basePositionMs = Math.max(0L, state.getPosition());
        positionUpdatedAtElapsedMs = SystemClock.elapsedRealtime();
        playbackSpeed = playing ? speed : 0.0f;
        notifyPlayStateChanged(playing);
        notifyRepeatModeChanged(repeatMode);
        notifyProgressChanged(state.getPosition(), duration);
    }

    /** 异步获取封面兜底（QQ/酷狗搜索） */
    private void fetchCoverFallback(String title, String artist, long duration) {
        final String fTitle = title;
        final String fArtist = artist;
        final long fDuration = duration;
        final long gen = trackGeneration;
        lyricExecutor.submit(() -> {
            try {
                String coverUrl = com.carassistant.lyrics.CoverArtSearchClient.find(
                        fTitle, fArtist, fDuration);
                if (coverUrl.isEmpty()) return;
                Bitmap cover = com.carassistant.lyrics.HttpCompat.downloadBitmap(coverUrl);
                if (cover == null) return;
                // 防止切歌后回灌旧封面
                if (gen != trackGeneration) return;
                if (handler != null) {
                    handler.post(() -> {
                        if (gen != trackGeneration) return;
                        if (fTitle.equals(currentTitle) && fArtist.equals(currentArtist)) {
                            notifyMetadataChanged(currentTitle, currentArtist, cover);
                        }
                    });
                }
            } catch (Throwable t) {
                Log.w(TAG, "cover fallback failed", t);
            }
        });
    }

    /** 通过 PackageManager 获取应用友好名称（1:1 对齐歌词伴侣 applicationLabel） */
    private String applicationLabel(String pkg) {
        if (context == null || TextUtils.isEmpty(pkg)) return "";
        try {
            android.content.pm.PackageManager pm = context.getPackageManager();
            CharSequence label = pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0));
            return label == null ? "" : label.toString().trim();
        } catch (Exception e) {
            return "";
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
        long shifted = position - lyricOffsetMs;
        if (shifted < 0) shifted = 0;
        LrcParser.LyricsTriple l = lrcParser.getLyricsAtPosition(shifted);
        notifyLyricsChanged(l.prev, l.current, l.next);
    }

    private void fetchLyricsAsync(final String title, final String artist) {
        if (lyricLoadTask != null) {
            lyricLoadTask.cancel(true);
            lyricLoadTask = null;
        }
        notifyLyricsChanged("", "歌词加载中...", "");
        final MultiSourceLyricClient client = lyricClient;
        if (client == null) {
            currentLyricLoaded = true;
            notifyLyricsChanged("", "暂无歌词", "");
            return;
        }
        final long generation = trackGeneration;
        final String mediaId = currentMediaId;
        final String sourceId = currentSourceId;
        final long duration = currentDuration;
        lyricLoadTask = lyricExecutor.submit(() -> {
            try {
                LyricResult result = client.load(sourceId, mediaId, title, artist, duration);
                if (Thread.currentThread().isInterrupted()) return;
                if (generation != trackGeneration) return; // 已切歌，丢弃过期结果
                if (handler != null) {
                    handler.post(() -> {
                        if (generation != trackGeneration) return;
                        applyLyricResult(result, generation);
                    });
                }
            } catch (Exception e) {
                Log.w(TAG, "fetchLyrics failed", e);
                if (handler != null && generation == trackGeneration) {
                    handler.post(() -> {
                        if (generation != trackGeneration) return;
                        currentLyricLoaded = true;
                        currentTimeline = LrcTimeline.EMPTY;
                        currentLyricSourceName = "";
                        notifyLyricsChanged("", "暂无歌词", "");
                    });
                }
            }
        });
    }

    /** 应用歌词加载结果到 UI（主线程） */
    private void applyLyricResult(LyricResult result, long generation) {
        if (generation != trackGeneration) return;
        currentLyricLoaded = true;
        if (result == null || result.isEmpty()) {
            currentTimeline = LrcTimeline.EMPTY;
            currentLyricSourceName = "";
            notifyLyricsChanged("", "暂无歌词", "");
            return;
        }
        currentTimeline = result.timeline;
        currentLyricSourceName = result.providerName;
        // 同步到 LrcParser（兼容 MusicActivity 三行歌词显示）
        List<LrcParser.LrcLine> parserLines = new ArrayList<>();
        for (LrcTimeline.Line line : result.timeline.getLines()) {
            parserLines.add(new LrcParser.LrcLine(line.timeMs, line.text, line.translated));
        }
        lrcParser.setLines(parserLines);
        // 立即刷新当前歌词行
        long pos = currentPositionMs();
        updateLyrics(pos);
        Log.d(TAG, "lyrics loaded from " + result.providerName + ", lines=" + result.timeline.size());
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
        trackGeneration++;
        currentTimeline = LrcTimeline.EMPTY;
        currentLyricLoaded = false;
        currentLyricSourceName = "";
        currentMediaId = "";
        currentSourceId = "";
        currentSourceName = "音乐播放器";
        basePositionMs = 0L;
        positionUpdatedAtElapsedMs = SystemClock.elapsedRealtime();
        playbackSpeed = 0.0f;
        lrcParser.clear();
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
