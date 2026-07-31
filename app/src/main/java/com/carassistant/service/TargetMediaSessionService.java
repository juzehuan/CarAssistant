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
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.os.Build;
import android.provider.Settings;
import android.service.notification.NotificationListenerService;
import android.util.Log;

import androidx.annotation.RequiresApi;

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

    /** 当前活跃的媒体会话列表（static volatile 供静态方法访问） */
    private static volatile List<MediaController> sActiveSessions = Collections.emptyList();

    private MediaSessionManager mManager;
    private final MediaSessionManager.OnActiveSessionsChangedListener mSessionsListener =
            controllers -> updateSessions(controllers);

    @Override
    public void onListenerConnected() {
        super.onListenerConnected();
        Log.i(TAG, "listener connected");
        // 兜底：开机自启调度（三重保险之一）
        AppAutoStartManager.scheduleFromBoot(this);
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
    }

    private void updateSessions(List<MediaController> controllers) {
        if (controllers == null) controllers = Collections.emptyList();
        sActiveSessions = controllers;
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
}
