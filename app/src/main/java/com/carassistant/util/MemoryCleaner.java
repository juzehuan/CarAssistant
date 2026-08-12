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

import android.content.Context;
import android.os.Build;
import android.util.Log;

import com.carassistant.service.KeyMappingAccessibilityService;

import java.util.List;

/**
 * 内存清理器：综合调度多种清理策略
 *
 * 策略优先级（自动选择最佳方案）：
 * 1. Root + drop_caches：清理系统页缓存/dentry/inode（最有效，秒级释放数百 MB）
 * 2. Root + am force-stop：强制停止正在运行的第三方应用
 * 3. 无障碍服务 + 模拟清除最近任务：非 Root 下最有效（释放 100-500 MB）
 * 4. 普通模式 + killBackgroundProcesses：系统 API（受限，效果有限）
 *
 * 所有策略均诚实标注实际效果，避免"伪清理"。
 */
public final class MemoryCleaner {

    private static final String TAG = "MemoryCleaner";

    private MemoryCleaner() {}

    public static class Result {
        /** 释放的内存字节数 */
        public long releasedBytes;
        /** 清理方式描述（用于 Toast） */
        public String methodDesc;
        /** 是否成功执行（不代表有效果，仅表示动作已执行） */
        public boolean success;
        /** 停止的应用数（Root 模式） */
        public int stoppedApps;
        /** 当前清理模式：root / accessibility / normal */
        public String mode = "";
        /** 是否建议用户开启更强清理方式 */
        public boolean suggestEnableMore = false;
    }

    /** 清理回调 */
    public interface Callback {
        void onDone(Result result);
    }

    /**
     * 执行内存清理（自动选择最佳策略）
     *
     * @param ctx 上下文
     * @param whitelist 内存清理白名单（包名集合）
     * @param callback 完成回调（在调用线程同步回调）
     */
    public static void clean(final Context ctx, final java.util.Set<String> whitelist,
                              final Callback callback) {
        new Thread(() -> {
            Result result = new Result();
            long before = MemoryUtil.getAvailableMemory(ctx);

            // 策略 1：Root + drop_caches + force-stop
            if (ShellUtil.hasRoot()) {
                result = cleanWithRoot(ctx, whitelist);
                result.mode = "root";
            }
            // 策略 2：无障碍服务模拟清除最近任务
            else if (KeyMappingAccessibilityService.isConnected()) {
                result = cleanWithAccessibility(ctx);
                result.mode = "accessibility";
            }
            // 策略 3：普通模式（受限）
            else {
                result = cleanNormal(ctx, whitelist);
                result.mode = "normal";
                result.suggestEnableMore = true;
            }

            // 通用辅助手段：触发本应用 trim-memory + GC，让系统层面回收一些内存
            try {
                android.app.ActivityManager am = (android.app.ActivityManager)
                        ctx.getSystemService(Context.ACTIVITY_SERVICE);
                if (am != null) {
                    // 通知所有应用释放内存（系统广播，部分 ROM 会响应）
                    // 注意：此 API 仅影响自身应用进程，不强制其他应用
                }
            } catch (Exception ignored) {}
            // 显式触发 GC，清理本应用 Dalvik/ART 堆中的不可达对象
            System.gc();
            try { Thread.sleep(300); } catch (InterruptedException ignored) {}

            // 等待系统回收内存
            try { Thread.sleep(1200); } catch (InterruptedException ignored) {}

            long after = MemoryUtil.getAvailableMemory(ctx);
            result.releasedBytes = Math.max(0, after - before);

            if (callback != null) {
                callback.onDone(result);
            }
        }).start();
    }

    /** Root 模式：drop_caches + force-stop 正在运行的第三方应用 */
    private static Result cleanWithRoot(Context ctx, java.util.Set<String> whitelist) {
        Result r = new Result();
        r.success = true;
        r.methodDesc = "Root 模式";

        // 1. 强制停止正在运行的第三方应用
        List<String> running = MemoryUtil.getRunningKillablePackages(ctx, whitelist);
        if (!running.isEmpty()) {
            r.stoppedApps = MemoryUtil.forceStopPackages(running);
        }

        // 2. 清理系统页缓存/dentry/inode（最有效的释放方式）
        ShellUtil.Result drop = ShellUtil.execRoot("echo 3 > /proc/sys/vm/drop_caches");
        if (!drop.success()) {
            Log.w(TAG, "drop_caches failed: " + drop.stderr);
        }

        // 3. 触发 trim-memory，让应用主动释放
        ShellUtil.execRoot("am send-trim-memory all RUNNING_CRITICAL 2>/dev/null");

        return r;
    }

    /** 无障碍服务模式：模拟清除最近任务（非 Root 最有效） */
    private static Result cleanWithAccessibility(Context ctx) {
        Result r = new Result();
        r.methodDesc = "无障碍模式";

        KeyMappingAccessibilityService svc = KeyMappingAccessibilityService.getInstance();
        if (svc == null) {
            r.success = false;
            return r;
        }

        // 同步等待清理完成（cleanRecentTasks 内部已是异步线程）
        final boolean[] done = {false};
        final boolean[] actionSucceeded = {false};
        svc.cleanRecentTasks(success -> {
            synchronized (done) {
                actionSucceeded[0] = success;
                done[0] = true;
                done.notifyAll();
            }
        });

        // 等待清理完成（最多 15 秒）
        synchronized (done) {
            long deadline = System.currentTimeMillis() + 15000;
            while (!done[0] && System.currentTimeMillis() < deadline) {
                try { done.wait(500); } catch (InterruptedException ignored) {}
            }
        }

        r.success = done[0] && actionSucceeded[0];
        return r;
    }

    /** 普通模式：killBackgroundProcesses（系统 API，效果有限） */
    private static Result cleanNormal(Context ctx, java.util.Set<String> whitelist) {
        Result r = new Result();
        r.methodDesc = "普通模式";
        // Android 14 起 killBackgroundProcesses 只能结束调用应用自身，不能清理其他应用。
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            r.success = false;
            return r;
        }
        List<String> pkgs = MemoryUtil.getKillablePackages(ctx, whitelist);
        int requested = MemoryUtil.killBackgroundProcesses(ctx, pkgs);
        r.success = requested > 0;
        return r;
    }
}
