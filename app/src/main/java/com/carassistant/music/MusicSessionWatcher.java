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

package com.carassistant.music;

import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSession;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.carassistant.service.TargetMediaSessionService;

import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * 音乐会话监听器
 *
 * 移植自歌词伴侣（com.zuoqirun.lyricscompanion.ModernMediaSessionReader + MusicNotificationListener）
 * 的监听核心逻辑，作为音乐伴侣的统一会话监听入口。
 *
 * 核心特性（与歌词伴侣 1:1 对齐）：
 * 1. {@link MediaSessionManager#addOnActiveSessionsChangedListener} 监听会话列表增减
 * 2. 对所有非自身会话注册 {@link MediaController.Callback}（不仅是选中会话），
 *    任何会话的元数据/播放状态变化都能立即触发重新选择
 * 3. 600ms 高频轮询 refresh()，覆盖 OnActiveSessionsChangedListener 漏掉的细微变化
 * 4. {@link #selectionScore} 打分选择最佳会话：playbackRank + hasMetadata + supportsControls + sameSession
 * 5. 健康监测：基于 lastSuccessfulReadElapsedMs 判断监听是否健康，
 *    不健康时通过 {@link TargetMediaSessionService#requestReconnect(Context)} 触发组件恢复
 *
 * 与 {@link com.carassistant.util.MusicController} 解耦：
 * - Watcher 只负责会话生命周期与选择，不处理歌词/封面/控制
 * - 通过 {@link Listener} 回调通知 controller，由 controller 自行决定如何处理
 */
public final class MusicSessionWatcher {

    private static final String TAG = "MusicSessionWatcher";

    /** 会话轮询间隔（ms），移植自歌词伴侣 SESSION_POLL_MS = 600 */
    private static final long SESSION_POLL_MS = 600L;
    /** 健康监测阈值：超过此时间未成功读取会话认为不健康（ms） */
    private static final long HEALTHY_THRESHOLD_MS = 3000L;
    /** 重连请求最小间隔（ms），防频繁 */
    private static final long RECONNECT_MIN_INTERVAL_MS = 2000L;

    /** 单例（应用级，整个进程共享一个监听器） */
    private static volatile MusicSessionWatcher sInstance;

    private final Context context;
    private final Handler handler;
    private final ComponentName listenerComponent;
    private MediaSessionManager sessionManager;

    /** 当前选中的会话（打分最高的） */
    private volatile MediaController selectedController;
    /** 所有正在监听 callback 的会话集合（与系统活跃列表保持同步） */
    private final Map<MediaSession.Token, MediaController> observedControllers = new HashMap<>();

    /** OnActiveSessionsChangedListener：会话列表增减时触发 */
    private final MediaSessionManager.OnActiveSessionsChangedListener sessionsChangedListener =
            controllers -> handleSessions(controllers);

    /** 所有会话共享的 Callback：任意会话元数据/状态变化触发 refresh */
    private final MediaController.Callback sessionCallback = new MediaController.Callback() {
        @Override
        public void onMetadataChanged(MediaMetadata md) {
            // 任意会话元数据变化都触发重新选择（可能切到新会话）
            refresh();
        }

        @Override
        public void onPlaybackStateChanged(PlaybackState state) {
            refresh();
        }

        @Override
        public void onSessionDestroyed() {
            refresh();
        }
    };

    /** 600ms 轮询任务 */
    private final Runnable sessionPoll = new Runnable() {
        @Override
        public void run() {
            refresh();
            handler.postDelayed(this, SESSION_POLL_MS);
        }
    };

    /** 监听状态 */
    private volatile boolean started = false;
    /** 上次成功读取会话的时刻（elapsedRealtime） */
    private volatile long lastSuccessfulReadElapsedMs = 0L;
    /** 上次请求重连的时刻（用于限频） */
    private volatile long lastReconnectRequestElapsedMs = 0L;
    /** 外部监听者 */
    private volatile Listener listener;

    private MusicSessionWatcher(Context ctx) {
        this.context = ctx.getApplicationContext();
        this.handler = new Handler(Looper.getMainLooper());
        this.listenerComponent = new ComponentName(this.context, TargetMediaSessionService.class);
    }

    /** 获取单例（首次调用时创建） */
    public static MusicSessionWatcher getInstance(@NonNull Context ctx) {
        if (sInstance == null) {
            synchronized (MusicSessionWatcher.class) {
                if (sInstance == null) {
                    sInstance = new MusicSessionWatcher(ctx);
                }
            }
        }
        return sInstance;
    }

    /** 启动监听（多次调用幂等） */
    public void start(@Nullable Listener listener) {
        this.listener = listener;
        if (started) {
            // 已启动：仅刷新一次并立即派发当前选中
            refresh();
            return;
        }
        started = true;
        try {
            sessionManager = (MediaSessionManager) context.getSystemService(Context.MEDIA_SESSION_SERVICE);
        } catch (Exception e) {
            Log.w(TAG, "get MediaSessionManager failed", e);
        }
        if (sessionManager == null) {
            Log.w(TAG, "MediaSessionManager unavailable, watcher disabled");
            return;
        }
        try {
            sessionManager.addOnActiveSessionsChangedListener(sessionsChangedListener, listenerComponent, handler);
        } catch (SecurityException e) {
            Log.w(TAG, "no permission to register sessions listener", e);
        } catch (Throwable t) {
            Log.w(TAG, "register sessions listener failed", t);
        }
        // 立即刷新一次 + 启动 600ms 轮询
        refresh();
        handler.removeCallbacks(sessionPoll);
        handler.postDelayed(sessionPoll, SESSION_POLL_MS);
        Log.i(TAG, "watcher started");
    }

    /** 停止监听并释放资源 */
    public void stop() {
        if (!started) return;
        started = false;
        handler.removeCallbacks(sessionPoll);
        if (sessionManager != null) {
            try {
                sessionManager.removeOnActiveSessionsChangedListener(sessionsChangedListener);
            } catch (Exception ignored) {}
        }
        synchronized (observedControllers) {
            Iterator<MediaController> it = observedControllers.values().iterator();
            while (it.hasNext()) {
                try {
                    it.next().unregisterCallback(sessionCallback);
                } catch (Throwable ignored) {}
            }
            observedControllers.clear();
        }
        selectedController = null;
        Log.i(TAG, "watcher stopped");
    }

    /** 设置外部监听者（可在 start 后更换） */
    public void setListener(@Nullable Listener listener) {
        this.listener = listener;
    }

    /** 获取当前选中的会话（可能为 null） */
    @Nullable
    public MediaController getSelectedController() {
        return selectedController;
    }

    /**
     * 立即刷新一次会话列表。
     *
     * 三个触发源都会调到这里：
     * 1. 600ms 轮询
     * 2. OnActiveSessionsChangedListener 回调（直接调 handleSessions）
     * 3. NotificationListener.onNotificationPosted/Removed
     */
    public void refresh() {
        if (!started || sessionManager == null) {
            return;
        }
        List<MediaController> controllers;
        try {
            controllers = sessionManager.getActiveSessions(listenerComponent);
        } catch (SecurityException e) {
            Log.w(TAG, "getActiveSessions denied", e);
            requestReconnectIfNeeded();
            return;
        } catch (Throwable t) {
            Log.w(TAG, "getActiveSessions failed", t);
            requestReconnectIfNeeded();
            return;
        }
        if (controllers == null) controllers = Collections.emptyList();
        lastSuccessfulReadElapsedMs = SystemClock.elapsedRealtime();
        handleSessions(controllers);
    }

    /** 通知监听器组件恢复时调用（强制重新派发当前选中） */
    public void onListenerReconnected() {
        refresh();
    }

    /**
     * 健康监测：当长时间未成功读取会话时，主动请求 NotificationListener 重绑。
     *
     * 触发场景：
     * - 系统因内存压力杀掉 NotificationListenerService 后未自动重启
     * - 用户在系统设置中误关闭通知访问后再次开启但未重启本服务
     */
    public void requestReconnectIfNeeded() {
        if (!started) return;
        long now = SystemClock.elapsedRealtime();
        if (lastSuccessfulReadElapsedMs <= 0) return;
        long elapsed = now - lastSuccessfulReadElapsedMs;
        if (elapsed < HEALTHY_THRESHOLD_MS) return;
        if (now - lastReconnectRequestElapsedMs < RECONNECT_MIN_INTERVAL_MS) return;
        lastReconnectRequestElapsedMs = now;
        Log.w(TAG, "watcher unhealthy (no read for " + elapsed + "ms), requesting reconnect");
        TargetMediaSessionService.requestReconnect(context);
    }

    // ============ 内部实现 ============

    /** 会话选择核心：打分选择最佳会话并通知 listener */
    private void handleSessions(List<MediaController> controllers) {
        // 1. 同步所有会话 callback（注册新的，注销消失的）
        syncObservedSessions(controllers);

        // 2. 打分选择最佳会话
        MediaController best = null;
        int bestScore = Integer.MIN_VALUE;
        String ownPkg = context.getPackageName();
        if (controllers != null) {
            for (MediaController mc : controllers) {
                if (mc == null) continue;
                String pkg = mc.getPackageName();
                if (ownPkg.equals(pkg)) continue;  // 跳过自身
                if (!isUsableSession(mc)) continue;

                PlaybackState st = mc.getPlaybackState();
                MediaMetadata md = mc.getMetadata();
                boolean sameSession = sameController(selectedController, mc);
                int score = MusicAppRegistry.selectionScore(
                        MusicAppRegistry.playbackRank(st == null ? 0 : st.getState()),
                        hasMetadata(md),
                        supportsControls(st),
                        sameSession);
                if (score > bestScore) {
                    best = mc;
                    bestScore = score;
                }
            }
        }

        // 3. 通知 listener
        MediaController old = selectedController;
        if (listener == null) return;

        if (best == null) {
            // 无可用会话
            if (old != null) {
                selectedController = null;
                Log.i(TAG, "session lost (no usable session)");
                listener.onSessionLost();
            }
            return;
        }

        // 选中变化：派发 onSessionSelected（controller 切换）
        boolean changed = !sameController(old, best);
        if (changed) {
            selectedController = best;
            Log.i(TAG, "session selected: " + best.getPackageName() + " score=" + bestScore
                    + " (old=" + (old != null ? old.getPackageName() : "null") + ")");
            listener.onSessionSelected(best, old);
        } else {
            // 同一会话：派发 onSessionData，让 listener 自行判断是否需要更新（如切歌、暂停）
            try {
                MediaMetadata md = best.getMetadata();
                PlaybackState st = best.getPlaybackState();
                listener.onSessionData(best, md, st);
            } catch (Throwable t) {
                Log.w(TAG, "dispatch onSessionData failed", t);
            }
        }
    }

    /** 同步观察会话集合：为新会话注册 callback，为消失的会话注销 callback */
    private void syncObservedSessions(List<MediaController> controllers) {
        Map<MediaSession.Token, MediaController> newSet = new HashMap<>();
        String ownPkg = context.getPackageName();
        if (controllers != null) {
            for (MediaController mc : controllers) {
                if (mc == null) continue;
                if (ownPkg.equals(mc.getPackageName())) continue;
                try {
                    MediaSession.Token token = mc.getSessionToken();
                    newSet.put(token, mc);
                } catch (Throwable ignored) {}
            }
        }
        synchronized (observedControllers) {
            // 注销消失的
            Iterator<Map.Entry<MediaSession.Token, MediaController>> it =
                    observedControllers.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<MediaSession.Token, MediaController> e = it.next();
                if (!newSet.containsKey(e.getKey())) {
                    try {
                        e.getValue().unregisterCallback(sessionCallback);
                    } catch (Throwable ignored) {}
                    it.remove();
                }
            }
            // 注册新的
            for (Map.Entry<MediaSession.Token, MediaController> e : newSet.entrySet()) {
                if (!observedControllers.containsKey(e.getKey())) {
                    try {
                        e.getValue().registerCallback(sessionCallback, handler);
                    } catch (Throwable ignored) {}
                    observedControllers.put(e.getKey(), e.getValue());
                } else {
                    // 已存在：更新引用（token 不变但 controller 实例可能换）
                    observedControllers.put(e.getKey(), e.getValue());
                }
            }
        }
    }

    /** 判断会话是否可用（移植自歌词伴侣 isUsableSession） */
    private boolean isUsableSession(MediaController mc) {
        PlaybackState st = mc.getPlaybackState();
        int state = st == null ? 0 : st.getState();
        if (MusicAppRegistry.playbackRank(state) > 0) return true;
        // 状态分低但仍有元数据且非 STOPPED/ERROR 时也允许（如未启动的应用残留会话）
        MediaMetadata md = mc.getMetadata();
        if (!hasMetadata(md)) return false;
        if (state == PlaybackState.STATE_STOPPED || state == PlaybackState.STATE_ERROR) return false;
        return true;
    }

    private boolean hasMetadata(MediaMetadata md) {
        if (md == null) return false;
        String title = md.getString(MediaMetadata.METADATA_KEY_TITLE);
        if (!empty(title)) return true;
        String display = md.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE);
        return !empty(display);
    }

    private boolean supportsControls(PlaybackState st) {
        if (st == null) return false;
        long actions = st.getActions();
        // 566 = ACTION_PLAY | ACTION_PAUSE | ACTION_SKIP_TO_NEXT | ACTION_SKIP_TO_PREVIOUS | ACTION_STOP
        return (566 & actions) != 0;
    }

    private boolean sameController(MediaController a, MediaController b) {
        if (a == b) return true;
        if (a == null || b == null) return false;
        try {
            return a.getSessionToken().equals(b.getSessionToken());
        } catch (Throwable t) {
            return false;
        }
    }

    private static boolean empty(String s) {
        return s == null || s.trim().isEmpty();
    }

    /** 外部监听者接口 */
    public interface Listener {
        /**
         * 选中的会话变化（首次连接或切换到新会话）。
         *
         * @param controller 新选中的会话
         * @param oldController 上次选中的会话（首次为 null）
         */
        void onSessionSelected(@NonNull MediaController controller,
                               @Nullable MediaController oldController);

        /**
         * 当前选中会话的元数据/播放状态刷新（每次 refresh 后调用）。
         *
         * 用于捕捉同一会话内的切歌、播放/暂停状态切换等变化——
         * 这些变化 OnActiveSessionsChangedListener 不会回调，仅由
         * 各会话的 MediaController.Callback 或 600ms 轮询触发 refresh 后派发到这里。
         *
         * @param controller 当前选中会话（与 onSessionSelected 中的相同）
         * @param metadata 最新元数据（可能为 null）
         * @param playbackState 最新播放状态（可能为 null）
         */
        void onSessionData(@NonNull MediaController controller,
                           @Nullable MediaMetadata metadata,
                           @Nullable PlaybackState playbackState);

        /** 选中的会话失效（无可用会话时） */
        void onSessionLost();
    }
}
