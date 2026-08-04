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

import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.util.Log;

import androidx.annotation.RequiresApi;

import com.carassistant.music.MusicSessionWatcher;
import com.carassistant.util.AppAutoStartManager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 媒体会话监听服务
 *
 * 移植自「侧边栏_方控_开启启动三方应用三合一」APK 的 TargetMediaSessionService，
 * 用于将方向盘媒体按键定向派发到指定应用的 MediaController。
 *
 * 为什么用 NotificationListenerService：
 * - MediaSessionManager.getActiveSessions() 需要通知监听权限或 STATUS_BAR_SERVICE 权限
 * - 普通应用通过 NotificationListenerService 绕过限制获取活跃媒体会话列表
 * - 必须由用户在系统设置中手动开启「通知访问权限」
 *
 * 提供两个核心静态方法：
 * - {@link #dispatchToPackage}: 向指定包名的 MediaController 派发媒体按键
 * - {@link #selectTargetPackage}: 智能选择目标应用（避免与原车播放器冲突）
 *
 * 同时在 onListenerConnected 中兜底调度开机自启（三重保险之一）。
 */
public class TargetMediaSessionService extends NotificationListenerService {

    private static final String TAG = "MediaSessionService";

    /** 健康监测阈值：超过此时间未成功读取会话认为监听器不健康（ms） */
    private static final long HEALTHY_THRESHOLD_MS = 3000L;
    /** 重连请求最小间隔（ms），防频繁 */
    private static final long RECONNECT_MIN_INTERVAL_MS = 2000L;
    /** 组件恢复冷却（ms），禁用再启用组件后的最短间隔 */
    private static final long COMPONENT_RECOVERY_COOLDOWN_MS = 15000L;
    /** 组件禁用后再启用的延迟（ms） */
    private static final long COMPONENT_RECOVERY_DELAY_MS = 6000L;
    /** 系统断线后自动重连的延迟（ms） */
    private static final long AUTO_RECONNECT_AFTER_DISCONNECT_MS = 500L;

    /** 当前活跃的媒体会话列表（static volatile 供静态方法访问） */
    private static volatile List<MediaController> sActiveSessions = Collections.emptyList();

    /** 单例引用：供静态方法调用 requestReconnect / 转发通知 */
    private static volatile TargetMediaSessionService sInstance;

    /** 主线程 Handler（用于重连任务调度） */
    private static final Handler RECONNECT_HANDLER = new Handler(Looper.getMainLooper());

    /** 上次成功派发会话的时刻（elapsedRealtime） */
    private static volatile long sLastActiveSessionsPushedElapsedMs = 0L;
    /** 上次请求重连的时刻（用于限频） */
    private static volatile long sLastReconnectRequestElapsedMs = 0L;
    /** 上次组件恢复的时刻（用于冷却） */
    private static volatile long sLastComponentRecoveryElapsedMs = 0L;
    /** 重连起始时刻（用于判断是否需要升级到组件恢复） */
    private static volatile long sReconnectStartedElapsedMs = 0L;
    /** legacy 重连进行中标志（防重入） */
    private static volatile boolean sLegacyRebindInProgress = false;

    private MediaSessionManager mManager;
    private final MediaSessionManager.OnActiveSessionsChangedListener mSessionsListener =
            controllers -> updateSessions(controllers);
    /** 断线后自动重连任务 */
    private final Runnable mReconnectAfterDisconnect = new Runnable() {
        @Override
        public void run() {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return;
            if (sInstance == null) return;
            if (!hasNotificationAccess(sInstance)) return;
            // 已恢复健康（最近成功派发过会话）则不再请求重连
            if (isHealthy()) return;
            ComponentName cn = new ComponentName(sInstance, TargetMediaSessionService.class);
            try {
                requestRebind(cn);
            } catch (Throwable t) {
                Log.w(TAG, "auto reconnect requestRebind failed", t);
            }
        }
    };

    @Override
    public void onListenerConnected() {
        super.onListenerConnected();
        sInstance = this;
        Log.i(TAG, "listener connected");
        // 兜底：开机自启调度（三重保险之一）
        AppAutoStartManager.scheduleFromBoot(this);
        RECONNECT_HANDLER.removeCallbacks(mReconnectAfterDisconnect);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            mManager = (MediaSessionManager) getSystemService(Context.MEDIA_SESSION_SERVICE);
            if (mManager != null) {
                ComponentName cn = new ComponentName(this, TargetMediaSessionService.class);
                try {
                    mManager.addOnActiveSessionsChangedListener(mSessionsListener, cn);
                    updateSessions(mManager.getActiveSessions(cn));
                } catch (SecurityException e) {
                    Log.w(TAG, "no permission to query media sessions", e);
                }
            }
        }
        // 通知 MusicSessionWatcher 重连恢复（如果已启动）
        try {
            MusicSessionWatcher.getInstance(this).onListenerReconnected();
        } catch (Throwable t) {
            Log.w(TAG, "notify watcher reconnected failed", t);
        }
    }

    private void updateSessions(List<MediaController> controllers) {
        if (controllers == null) controllers = Collections.emptyList();
        sActiveSessions = controllers;
        sLastActiveSessionsPushedElapsedMs = SystemClock.elapsedRealtime();
        Log.d(TAG, "active sessions: " + controllers.size());
    }

    @Override
    public void onListenerDisconnected() {
        if (mManager != null) {
            try {
                mManager.removeOnActiveSessionsChangedListener(mSessionsListener);
            } catch (Exception ignored) {}
        }
        super.onListenerDisconnected();
        // 系统断线后自动尝试重连（API 24+ 通过 requestRebind）
        RECONNECT_HANDLER.postDelayed(mReconnectAfterDisconnect, AUTO_RECONNECT_AFTER_DISCONNECT_MS);
        Log.w(TAG, "listener disconnected, scheduled auto-reconnect");
    }

    /**
     * 通知张贴：转发给 MusicSessionWatcher 触发 refresh。
     *
     * 这是音乐伴侣 1:1 对齐歌词伴侣的关键特性——利用通知变化作为媒体状态变更的即时触发源，
     * 比 OnActiveSessionsChangedListener（仅在会话列表增减时回调）更灵敏，能捕捉到：
     * - 同一会话内切歌（元数据变化触发的通知刷新）
     * - 进度条更新触发的通知刷新
     * - 播放/暂停状态切换的通知刷新
     */
    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        if (sbn == null) return;
        // 排除自身应用的通知，避免无谓刷新
        if (getPackageName().equals(sbn.getPackageName())) return;
        try {
            MusicSessionWatcher.getInstance(this).refresh();
        } catch (Throwable t) {
            Log.w(TAG, "forward onNotificationPosted to watcher failed", t);
        }
    }

    /** 通知移除：同样转发给 watcher */
    @Override
    public void onNotificationRemoved(StatusBarNotification sbn) {
        if (sbn == null) return;
        if (getPackageName().equals(sbn.getPackageName())) return;
        try {
            MusicSessionWatcher.getInstance(this).refresh();
        } catch (Throwable t) {
            Log.w(TAG, "forward onNotificationRemoved to watcher failed", t);
        }
    }

    // ============ 静态方法：监听器健康监测与重连（1:1 对齐歌词伴侣） ============

    /**
     * 判断监听器是否健康（最近 N 秒内成功派发过会话列表）。
     * 供外部组件（如 MusicSessionWatcher）周期性检测监听器状态。
     *
     * 注：不直接调用 isListenerConnected()（protected），用最近派发时间作为存活判据，
     * 若监听器已断开则 updateSessions 不再被调用，sLastActiveSessionsPushedElapsedMs 停滞 → 不健康。
     */
    public static boolean isHealthy() {
        if (sInstance == null) return false;
        long last = sLastActiveSessionsPushedElapsedMs;
        if (last <= 0) return false;
        long elapsed = SystemClock.elapsedRealtime() - last;
        return elapsed >= 0 && elapsed < HEALTHY_THRESHOLD_MS;
    }

    /**
     * 请求重连监听器（公开 API，供 MusicSessionWatcher 在健康监测失败时调用）。
     *
     * 策略（移植自歌词伴侣 MusicNotificationListener.requestReconnect）：
     * 1. 健康则直接返回
     * 2. API 24+：先 requestRebind；超过 COMPONENT_RECOVERY_DELAY_MS 仍未恢复则升级到组件禁用/启用
     * 3. API < 24：直接走组件禁用/启用兜底
     * 4. 限频：RECONNECT_MIN_INTERVAL_MS
     */
    public static void requestReconnect(Context ctx) {
        if (ctx == null) return;
        if (isHealthy()) {
            sReconnectStartedElapsedMs = 0L;
            return;
        }
        if (!hasNotificationAccess(ctx)) return;
        long now = SystemClock.elapsedRealtime();
        if (now - sLastReconnectRequestElapsedMs < RECONNECT_MIN_INTERVAL_MS) return;
        sLastReconnectRequestElapsedMs = now;

        if (sReconnectStartedElapsedMs <= 0) {
            sReconnectStartedElapsedMs = now;
        }
        Context appCtx = ctx.getApplicationContext();
        ComponentName cn = new ComponentName(appCtx, TargetMediaSessionService.class);
        try {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
                requestLegacyRebind(appCtx, cn);
                return;
            }
            // API 24+：超过冷却时间仍未恢复 → 升级到组件禁用/启用
            if (now - sReconnectStartedElapsedMs >= COMPONENT_RECOVERY_DELAY_MS
                    && (sLastComponentRecoveryElapsedMs <= 0
                        || now - sLastComponentRecoveryElapsedMs >= COMPONENT_RECOVERY_COOLDOWN_MS)) {
                sLastComponentRecoveryElapsedMs = now;
                Log.w(TAG, "listener unhealthy, performing component recovery");
                requestLegacyRebind(appCtx, cn);
                return;
            }
            // 否则仅请求 rebind
            requestRebind(cn);
        } catch (Throwable t) {
            Log.w(TAG, "requestReconnect failed", t);
        }
    }

    /** 通过禁用再启用组件强制系统重绑 NotificationListenerService（兜底方案） */
    private static void requestLegacyRebind(final Context ctx, final ComponentName cn) {
        if (sLegacyRebindInProgress) return;
        sLegacyRebindInProgress = true;
        final PackageManager pm = ctx.getPackageManager();
        try {
            pm.setComponentEnabledSetting(cn, PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                    PackageManager.DONT_KILL_APP);
            RECONNECT_HANDLER.postDelayed(() -> {
                try {
                    pm.setComponentEnabledSetting(cn, PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                            PackageManager.DONT_KILL_APP);
                } catch (Throwable ignored) {
                } finally {
                    sLegacyRebindInProgress = false;
                }
                // API 24+ 还可以显式 requestRebind
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && hasNotificationAccess(ctx)) {
                    try {
                        requestRebind(cn);
                    } catch (Throwable t) {
                        Log.w(TAG, "rebind after component recovery failed", t);
                    }
                }
            }, COMPONENT_RECOVERY_DELAY_MS);
        } catch (Throwable t) {
            sLegacyRebindInProgress = false;
            Log.w(TAG, "requestLegacyRebind failed", t);
        }
    }

    /** 判断通知访问权限是否已授予 */
    public static boolean hasNotificationAccess(Context ctx) {
        if (ctx == null) return false;
        String s = Settings.Secure.getString(ctx.getContentResolver(), "enabled_notification_listeners");
        if (s == null) return false;
        ComponentName cn = new ComponentName(ctx, TargetMediaSessionService.class);
        for (String token : s.split(":")) {
            ComponentName c = ComponentName.unflattenFromString(token);
            if (cn.equals(c)) return true;
        }
        return false;
    }

    // ============ 静态方法：供 KeyActionExecutor 调用 ============

    /**
     * 向指定包名的 MediaController 派发媒体按键。
     *
     * @param targetPackage 目标应用包名；为空或未找到活跃会话时返回 false
     * @param keyCode       KeyEvent 键码（如 KEYCODE_MEDIA_PLAY_PAUSE）
     * @return true 表示已成功派发
     */
    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    public static boolean dispatchToPackage(String targetPackage, int keyCode) {
        if (targetPackage == null || targetPackage.isEmpty()) return false;
        List<MediaController> sessions = sActiveSessions;
        for (MediaController mc : sessions) {
            if (targetPackage.equals(mc.getPackageName())) {
                try {
                    long now = System.currentTimeMillis();
                    mc.dispatchMediaButtonEvent(new android.view.KeyEvent(
                            now, now, android.view.KeyEvent.ACTION_DOWN, keyCode, 0));
                    mc.dispatchMediaButtonEvent(new android.view.KeyEvent(
                            now, now, android.view.KeyEvent.ACTION_UP, keyCode, 0));
                    return true;
                } catch (Exception e) {
                    Log.w(TAG, "dispatch to " + targetPackage + " failed", e);
                    return false;
                }
            }
        }
        return false;
    }

    /**
     * 智能选择目标应用包名（避免与原车播放器冲突）。
     *
     * 策略（移植自 APK）：
     * 1. 若一个【未在 activePackages 中】的应用正在播放 → 旁路，返回 null（让系统处理原按键）
     * 2. 优先返回正在播放的指定应用
     * 3. 系统音频正在播放 → 旁路
     * 4. 否则返回第一个指定的应用
     *
     * @param ctx              上下文
     * @param activePackages   候选目标包名列表（用户为该按键绑定的应用）
     * @return 选中的包名，或 null 表示旁路
     */
    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    public static String selectTargetPackage(Context ctx, List<String> activePackages) {
        if (activePackages == null || activePackages.isEmpty()) return null;
        List<MediaController> sessions = sActiveSessions;
        String playingBound = null;   // 正在播放的绑定应用
        String anyBound = null;       // 任意绑定应用（兜底）

        for (MediaController mc : sessions) {
            String pkg = mc.getPackageName();
            boolean isBound = activePackages.contains(pkg);
            boolean isPlaying = isActivelyPlaying(mc);

            // 规则1：未绑定的应用正在播放 → 旁路
            if (isPlaying && !isBound) return null;

            if (isBound) {
                if (anyBound == null) anyBound = pkg;
                if (isPlaying && playingBound == null) playingBound = pkg;
            }
        }
        if (playingBound != null) return playingBound;

        // 规则2：系统音频正在播放 → 旁路
        if (ctx != null) {
            try {
                android.media.AudioManager am =
                        (android.media.AudioManager) ctx.getSystemService(Context.AUDIO_SERVICE);
                if (am != null && am.isMusicActive()) return null;
            } catch (Exception ignored) {}
        }
        return anyBound;
    }

    /**
     * 判断除指定目标之外，是否还有其它应用正在播放。
     *
     * 用于按键定向派发失败时决定是否回退全局派发：
     * - 若存在其它【未绑定】应用正在播放，则不回退（避免误控其它音乐 App，
     *   即「只绑了 A 却也控了 B」的 bug）；
     * - 否则回退全局，保证对目标应用（或系统当前播放器）的按键有效。
     *
     * 注：当通知访问权限未授予时 sActiveSessions 为空，本方法恒返回 false，
     * 从而自然回退全局派发，恢复「媒体控制有效」的可用性。
     *
     * @param targets 用户绑定的目标包名列表
     * @return true 表示有其它未绑定应用正在播放
     */
    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    public static boolean isOtherAppPlaying(java.util.List<String> targets) {
        if (targets == null || targets.isEmpty()) return false;
        for (MediaController mc : sActiveSessions) {
            String pkg = mc.getPackageName();
            if (!targets.contains(pkg) && isActivelyPlaying(mc)) {
                return true;
            }
        }
        return false;
    }

    /** 判断 MediaController 是否处于「正在播放」状态 */
    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    private static boolean isActivelyPlaying(MediaController mc) {
        if (mc == null) return false;
        PlaybackState state = mc.getPlaybackState();
        if (state == null) return false;
        return isPlayingState(state.getState());
    }

    /**
     * 判定为「正在播放」的状态码（移植自 APK）：
     * 3 PLAYING / 6 BUFFERING / 8 FAST_FORWARDING / 4 PAUSED(含) / 5 / 9 / 10 / 11
     * 覆盖播放/缓冲/快进快退等非停止状态。
     */
    private static boolean isPlayingState(int state) {
        return state == PlaybackState.STATE_PLAYING
                || state == PlaybackState.STATE_BUFFERING
                || state == PlaybackState.STATE_FAST_FORWARDING
                || state == PlaybackState.STATE_PAUSED
                || state == PlaybackState.STATE_REWINDING
                || state == 9 || state == 10 || state == 11;
    }

    /**
     * 判断本服务是否已在系统设置中开启通知访问权限。
     * 供 KeyMappingActivity 权限状态检测使用。
     */
    public static boolean isEnabledInSettings(Context ctx) {
        if (ctx == null) return false;
        String s = Settings.Secure.getString(ctx.getContentResolver(),
                "enabled_notification_listeners");
        if (s == null) return false;
        ComponentName cn = new ComponentName(ctx, TargetMediaSessionService.class);
        return s.toLowerCase().contains(cn.flattenToString().toLowerCase());
    }

    /** 获取当前活跃媒体会话的包名列表（用于调试或状态展示） */
    public static List<String> getActivePackages() {
        List<MediaController> sessions = sActiveSessions;
        List<String> list = new ArrayList<>();
        for (MediaController mc : sessions) {
            list.add(mc.getPackageName());
        }
        return list;
    }

    /**
     * 判断指定包名是否有活跃的媒体会话（用于 UI 状态展示）。
     */
    public static boolean hasActiveSession(String pkg) {
        if (pkg == null) return false;
        for (MediaController mc : sActiveSessions) {
            if (pkg.equals(mc.getPackageName())) return true;
        }
        return false;
    }

    /**
     * 获取指定包名媒体会话正在播放的曲目名称（用于 UI 展示）。
     */
    public static String getNowPlaying(String pkg) {
        if (pkg == null) return null;
        for (MediaController mc : sActiveSessions) {
            if (pkg.equals(mc.getPackageName())) {
                MediaMetadata md = mc.getMetadata();
                if (md != null) {
                    String title = md.getString(MediaMetadata.METADATA_KEY_TITLE);
                    if (title != null && !title.isEmpty()) return title;
                }
            }
        }
        return null;
    }

    /**
     * 选择用于调整音量的目标会话：优先正在播放的会话，否则取第一个活跃会话。
     */
    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    private static MediaController pickVolumeTarget() {
        MediaController fallback = null;
        for (MediaController mc : sActiveSessions) {
            if (isActivelyPlaying(mc)) return mc;
            if (fallback == null) fallback = mc;
        }
        return fallback;
    }

    /**
     * 通过当前媒体会话调整音量（等同用户与播放器交互，不受后台应用音频限制）。
     *
     * <p>用于「音量+ / 音量- / 静音」动作。Android 12+ 会静默忽略后台第三方应用通过
     * {@code AudioManager.adjustStreamVolume(STREAM_MUSIC)} 发起的音量调整（物理音量键因为是
     * 输入事件才可例外），而通过活动媒体会话的 {@link MediaController#adjustVolume} 调整则不受此限，
     * 这也是车机「音量键」最贴合的语义（调节当前正在播放的应用音量）。
     *
     * @param direction 见 {@link android.media.AudioManager} 的 ADJUST_* 常量
     *                  （RAISE / LOWER / TOGGLE_MUTE / MUTE / UNMUTE）
     * @return true 表示已通过媒体会话发起调整
     */
    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    public static boolean adjustCurrentVolume(int direction) {
        MediaController target = pickVolumeTarget();
        if (target == null) return false;
        try {
            target.adjustVolume(direction, 0);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 通过当前媒体会话将音量设置为指定百分比。
     *
     * @param percent 0-100
     * @return true 表示已通过媒体会话设置
     */
    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    public static boolean setSessionVolume(int percent) {
        MediaController target = pickVolumeTarget();
        if (target == null) return false;
        try {
            android.media.session.MediaController.PlaybackInfo info = target.getPlaybackInfo();
            if (info == null) return false;
            int max = info.getMaxVolume();
            if (max <= 0) return false;
            int vol = Math.max(0, Math.min(max, Math.round(max * percent / 100f)));
            target.setVolumeTo(vol, 0);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
