/*
 * Copyright (C) 2026 CarAssistant Project. All rights reserved.
 *
 * 版权所有 (C) 2026 车机助手项目
 * 保留所有权利
 *
 * 本源代码受著作权法保护，未经著作权人书面许可，不得以任何形式复制、修改、
 * 分发、出售或逆向工程。违反者将承担法律责任。
 */

package com.carassistant.util;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.view.KeyEvent;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 按键触发检测器（全局）。
 *
 * 能力（参考开源 Key Mapper）：
 * - 单击 / 双击 / 长按 / 三连按（单键）
 * - 组合键（同时按住两键）
 * - 按键序列（依次按下若干键，如 音量+ → 音量- → 播放）
 * - 模拟轴事件（手柄/方向盘，经 IME 转换为合成键码后接入相同检测逻辑）
 * - 前台应用约束（映射可设置「仅某应用前台时生效」，在 fire 前校验）
 *
 * 本类实例由 KeyMappingAccessibilityService 持有，按键事件由此服务转发。
 */
public class KeyTriggerDetector {

    private final Context ctx;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    // 已按下的物理键集合（用于组合键判定）
    private final Set<Integer> pressedKeys = new HashSet<>();
    // 长按待执行任务
    private final Map<Integer, Runnable> longPressRunnables = new ConcurrentHashMap<>();
    // 单击/双击/三连按：待判定任务与计数
    private final Map<Integer, Runnable> pendingDecision = new ConcurrentHashMap<>();
    private final Map<Integer, Integer> tapCounts = new ConcurrentHashMap<>();
    // 序列检测
    private final List<Integer> sequenceBuffer = new ArrayList<>();
    private boolean sequenceActive = false;
    private Runnable sequenceTimeoutRunnable;

    public KeyTriggerDetector(Context ctx) {
        this.ctx = ctx.getApplicationContext();
    }

    public boolean onKeyDown(int keyCode, KeyEvent event) {
        int repCount = event.getRepeatCount();
        if (repCount != 0) return false; // 仅处理首次按下，长按由计时器判定

        pressedKeys.add(keyCode);

        // 组合键判定（序列进行中时跳过，避免与序列冲突）
        if (!sequenceActive) {
            for (int other : pressedKeys) {
                if (other != keyCode && other != 0) {
                    KeyMappingUtil.KeyMapping combo = KeyMappingUtil.getComboMapping(ctx, keyCode, other);
                    if (combo == null) combo = KeyMappingUtil.getComboMapping(ctx, other, keyCode);
                    if (combo != null && combo.enabled) {
                        cancelPending(keyCode);
                        fire(combo);
                        return true;
                    }
                }
            }
        }

        // 序列判定（可能消费事件）
        if (handleSequence(keyCode)) return true;

        // 长按判定：若为某序列的首键则不排程，避免与序列语义冲突
        if (!sequenceActive && !isSequenceStart(keyCode)) {
            KeyMappingUtil.KeyMapping longM = KeyMappingUtil.getMapping(ctx, keyCode, KeyMappingUtil.TRIGGER_LONG_PRESS);
            if (longM != null && longM.enabled) {
                Runnable r = () -> {
                    if (pressedKeys.contains(keyCode)) {
                        cancelTapDecision(keyCode);
                        tapCounts.remove(keyCode);
                        fire(longM);
                    }
                };
                longPressRunnables.put(keyCode, r);
                mainHandler.postDelayed(r, KeyMappingUtil.LONG_PRESS_TIMEOUT);
            }
        }

        // 单击/双击/三连按判定
        scheduleTapDecision(keyCode);
        // 只有当该键参与某个「已启用」映射时才消费 DOWN（以便后续判定组合键/序列/多击）；
        // 否则必须放行，让系统正常处理该按键，避免开启无障碍后所有按键失效。
        return KeyMappingUtil.hasAnyEnabledMappingForKey(ctx, keyCode);
    }

    public boolean onKeyUp(int keyCode, KeyEvent event) {
        pressedKeys.remove(keyCode);
        Runnable r = longPressRunnables.remove(keyCode);
        if (r != null) mainHandler.removeCallbacks(r);
        return false;
    }

    /** 模拟轴事件触发（由 IME 转发）。无 keyup 语义，按「按下」处理，但不参与组合键 */
    public void onAxisTrigger(int axisKeyCode) {
        if (!KeyMappingUtil.isAxisKey(axisKeyCode)) return;
        if (handleSequence(axisKeyCode)) return;
        scheduleTapDecision(axisKeyCode);
    }

    // ---------- 单击/双击/三连按 ----------

    private void scheduleTapDecision(int keyCode) {
        cancelTapDecision(keyCode);
        int count = tapCounts.getOrDefault(keyCode, 0) + 1;
        tapCounts.put(keyCode, count);
        Runnable decide = () -> {
            pendingDecision.remove(keyCode);
            int c = tapCounts.getOrDefault(keyCode, 0);
            if (c == 0) return;
            KeyMappingUtil.KeyMapping triple = KeyMappingUtil.getMapping(ctx, keyCode, KeyMappingUtil.TRIGGER_TRIPLE_TAP);
            KeyMappingUtil.KeyMapping dbl = KeyMappingUtil.getMapping(ctx, keyCode, KeyMappingUtil.TRIGGER_DOUBLE_TAP);
            KeyMappingUtil.KeyMapping tap = KeyMappingUtil.getMapping(ctx, keyCode, KeyMappingUtil.TRIGGER_TAP);
            if (c >= 3 && triple != null && triple.enabled) {
                fire(triple);
            } else if (c == 2 && dbl != null && dbl.enabled) {
                fire(dbl);
            } else if (c == 1 && tap != null && tap.enabled) {
                fire(tap);
            } else if (c >= 2 && tap != null && tap.enabled) {
                fire(tap); // 多击但仅配置单击：回落为单击
            } else if (c >= 3 && dbl != null && dbl.enabled) {
                fire(dbl); // 三击但仅配置双击：回落为双击
            }
            tapCounts.remove(keyCode);
        };
        pendingDecision.put(keyCode, decide);
        // 若该键同时配置了「长按」映射，需等长按窗口结束后再判定单击/双击/三连按。
        // 否则单击判定（DOUBLE_TAP_TIMEOUT=500ms）会早于长按（LONG_PRESS_TIMEOUT=600ms）触发，
        // 导致长按时也先误触发一次单击（单击与长按同时配置时的经典问题）。
        // 延迟到长按阈值之后，长按触发时会 cancelTapDecision 取消待定单击，单击不再误触发。
        KeyMappingUtil.KeyMapping lp = KeyMappingUtil.getMapping(ctx, keyCode, KeyMappingUtil.TRIGGER_LONG_PRESS);
        boolean hasLong = lp != null && lp.enabled;
        long decideDelay = hasLong
                ? (KeyMappingUtil.LONG_PRESS_TIMEOUT + 50)
                : KeyMappingUtil.DOUBLE_TAP_TIMEOUT;
        mainHandler.postDelayed(decide, decideDelay);
    }

    private void cancelTapDecision(int keyCode) {
        Runnable r = pendingDecision.remove(keyCode);
        if (r != null) mainHandler.removeCallbacks(r);
    }

    private void cancelPending(int keyCode) {
        Runnable lr = longPressRunnables.remove(keyCode);
        if (lr != null) mainHandler.removeCallbacks(lr);
        cancelTapDecision(keyCode);
    }

    // ---------- 序列 ----------

    private boolean handleSequence(int keyCode) {
        List<KeyMappingUtil.KeyMapping> seqs = KeyMappingUtil.getSequenceMappings(ctx);
        if (seqs == null || seqs.isEmpty()) return false;

        if (!sequenceActive) {
            boolean starts = false;
            for (KeyMappingUtil.KeyMapping s : seqs) {
                if (s.sequenceKeys != null && s.sequenceKeys.length >= 2 && s.sequenceKeys[0] == keyCode) {
                    starts = true;
                    break;
                }
            }
            if (!starts) return false;
            sequenceBuffer.clear();
            sequenceBuffer.add(keyCode);
            sequenceActive = true;
        } else {
            sequenceBuffer.add(keyCode);
        }

        // 完全匹配？
        for (KeyMappingUtil.KeyMapping s : seqs) {
            if (s.sequenceKeys != null && s.sequenceKeys.length == sequenceBuffer.size()
                    && isPrefix(s.sequenceKeys, sequenceBuffer)) {
                clearSequence();
                fire(s);
                return true;
            }
        }
        // 作为前缀继续？
        boolean isPrefix = false;
        for (KeyMappingUtil.KeyMapping s : seqs) {
            if (s.sequenceKeys != null && s.sequenceKeys.length > sequenceBuffer.size()
                    && isPrefix(s.sequenceKeys, sequenceBuffer)) {
                isPrefix = true;
                break;
            }
        }
        if (!isPrefix) {
            // 序列中断：放弃当前缓冲，错按的键不再触发其它映射
            clearSequence();
            return true;
        }
        // 续标定，重置超时（超时未补齐则触发首键的普通映射）
        rescheduleSequenceTimeout();
        return true;
    }

    private void rescheduleSequenceTimeout() {
        if (sequenceTimeoutRunnable != null) mainHandler.removeCallbacks(sequenceTimeoutRunnable);
        sequenceTimeoutRunnable = () -> {
            sequenceTimeoutRunnable = null;
            if (sequenceActive) {
                int lone = sequenceBuffer.isEmpty() ? -1 : sequenceBuffer.get(0);
                clearSequence();
                if (lone != -1) {
                    // 序列未完成：将首键作为普通按键重新走单击/双击/三连按判定
                    scheduleTapDecision(lone);
                }
            }
        };
        mainHandler.postDelayed(sequenceTimeoutRunnable, KeyMappingUtil.SEQUENCE_TIMEOUT);
    }

    private void clearSequence() {
        sequenceActive = false;
        sequenceBuffer.clear();
        if (sequenceTimeoutRunnable != null) {
            mainHandler.removeCallbacks(sequenceTimeoutRunnable);
            sequenceTimeoutRunnable = null;
        }
    }

    private boolean isSequenceStart(int keyCode) {
        List<KeyMappingUtil.KeyMapping> seqs = KeyMappingUtil.getSequenceMappings(ctx);
        if (seqs == null) return false;
        for (KeyMappingUtil.KeyMapping s : seqs) {
            if (s.sequenceKeys != null && s.sequenceKeys.length >= 2 && s.sequenceKeys[0] == keyCode) {
                return true;
            }
        }
        return false;
    }

    private static boolean isPrefix(int[] full, List<Integer> buf) {
        if (buf.size() > full.length) return false;
        for (int i = 0; i < buf.size(); i++) {
            if (buf.get(i) != full[i]) return false;
        }
        return true;
    }

    // ---------- 执行 ----------

    private void fire(KeyMappingUtil.KeyMapping m) {
        if (m == null || !m.enabled) return;
        if (!KeyMappingUtil.isConstraintSatisfied(ctx, m)) return;
        KeyActionExecutor.execute(ctx, m);
    }

    /** 服务解绑/销毁时清理所有待执行任务 */
    public void cleanup() {
        for (Runnable r : longPressRunnables.values()) mainHandler.removeCallbacks(r);
        for (Runnable r : pendingDecision.values()) mainHandler.removeCallbacks(r);
        longPressRunnables.clear();
        pendingDecision.clear();
        tapCounts.clear();
        pressedKeys.clear();
        clearSequence();
    }
}
