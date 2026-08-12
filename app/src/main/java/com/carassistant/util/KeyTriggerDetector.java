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
 * 一次物理按压的 DOWN / 重复 DOWN / UP 由同一个实例完整跟踪，避免只吞 DOWN、
 * 放行 UP 或长按重复事件而产生系统原动作。所有查询只使用当前已启用且前台约束
 * 满足的映射，禁用/不满足约束的映射不会拦截原按键。
 */
public class KeyTriggerDetector {

    private final Context ctx;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    /** 当前仍处于按下状态的物理键。 */
    private final Set<Integer> pressedKeys = new HashSet<>();
    /** 本次物理按压已决定消费，重复 DOWN 与 UP 必须保持相同决定。 */
    private final Set<Integer> consumedKeys = new HashSet<>();
    /** 已由组合键/序列处理，释放时不能再登记成普通点击。 */
    private final Set<Integer> suppressedKeys = new HashSet<>();
    /** 长按已经触发，释放时不能再触发单击。 */
    private final Set<Integer> longPressFired = new HashSet<>();
    /** 无竞争的单击已在 DOWN 时快速触发。 */
    private final Set<Integer> completedOnDown = new HashSet<>();
    /** 序列超时时首键仍未释放，等到 UP 后再按普通点击处理。 */
    private final Set<Integer> deferredSequenceTaps = new HashSet<>();

    private final Map<Integer, Runnable> longPressRunnables = new ConcurrentHashMap<>();
    private final Map<Integer, Runnable> pendingTapDecisions = new ConcurrentHashMap<>();
    private final Map<Integer, Integer> tapCounts = new ConcurrentHashMap<>();

    private final List<Integer> sequenceBuffer = new ArrayList<>();
    private boolean sequenceActive;
    private Runnable sequenceTimeoutRunnable;

    public KeyTriggerDetector(Context ctx) {
        this.ctx = ctx.getApplicationContext();
    }

    public boolean onKeyDown(int keyCode, KeyEvent event) {
        // Android 长按会持续产生 repeat DOWN；首个 DOWN 若被消费，后续必须全部消费。
        if (event.getRepeatCount() != 0 || pressedKeys.contains(keyCode)) {
            return consumedKeys.contains(keyCode);
        }

        pressedKeys.add(keyCode);
        // 第二/第三次点击从按下时起就延长判定窗口，避免旧定时器在本次抬起前抢先触发。
        if (tapCounts.containsKey(keyCode)) cancelTapDecision(keyCode);

        // 组合键优先。两边此前积累的单击/长按任务都必须取消，否则会先后执行组合与单键。
        for (int other : new HashSet<>(pressedKeys)) {
            if (other == keyCode) continue;
            // 其中一键的长按已经成立后，不再补触发组合键。
            if (longPressFired.contains(other)) continue;
            KeyMappingUtil.KeyMapping combo = getActiveCombo(keyCode, other);
            if (combo != null) {
                clearSequence();
                suppressGesture(keyCode);
                suppressGesture(other);
                suppressedKeys.add(keyCode);
                suppressedKeys.add(other);
                consumedKeys.add(keyCode);
                consumedKeys.add(other);
                fire(combo);
                return true;
            }
        }

        // 序列优先于普通单键；序列失败的当前键会回到普通按键流程，不再无条件吞掉。
        if (handleSequence(keyCode)) {
            suppressGesture(keyCode);
            suppressedKeys.add(keyCode);
            consumedKeys.add(keyCode);
            return true;
        }

        if (!hasAnyActiveMappingForKey(keyCode)) return false;
        consumedKeys.add(keyCode);

        KeyMappingUtil.KeyMapping longPress = getActiveMapping(
                keyCode, KeyMappingUtil.TRIGGER_LONG_PRESS);
        if (longPress != null) {
            Runnable task = () -> {
                longPressRunnables.remove(keyCode);
                if (!pressedKeys.contains(keyCode)) return;
                cancelTapDecision(keyCode);
                tapCounts.remove(keyCode);
                longPressFired.add(keyCode);
                fire(longPress);
            };
            longPressRunnables.put(keyCode, task);
            mainHandler.postDelayed(task, KeyMappingUtil.LONG_PRESS_TIMEOUT);
        }

        KeyMappingUtil.KeyMapping tap = getActiveMapping(keyCode, KeyMappingUtil.TRIGGER_TAP);
        boolean hasMultiTap = getActiveMapping(keyCode, KeyMappingUtil.TRIGGER_DOUBLE_TAP) != null
                || getActiveMapping(keyCode, KeyMappingUtil.TRIGGER_TRIPLE_TAP) != null;

        // 没有双/三击、长按、组合竞争的单击可立即执行，消除方向盘切歌/调音量的 500ms 延迟。
        if (tap != null && !hasMultiTap && longPress == null && !hasActiveComboForKey(keyCode)) {
            completedOnDown.add(keyCode);
            fire(tap);
        }
        return true;
    }

    public boolean onKeyUp(int keyCode, KeyEvent event) {
        pressedKeys.remove(keyCode);
        Runnable longTask = longPressRunnables.remove(keyCode);
        if (longTask != null) mainHandler.removeCallbacks(longTask);

        boolean consumed = consumedKeys.remove(keyCode);
        boolean sequenceTap = deferredSequenceTaps.remove(keyCode);
        boolean suppressed = suppressedKeys.remove(keyCode);

        if (sequenceTap) {
            recordCompletedTap(keyCode);
            return consumed;
        }
        if (suppressed) return consumed;
        if (longPressFired.remove(keyCode)) return consumed;
        if (completedOnDown.remove(keyCode)) return consumed;
        if (consumed) recordCompletedTap(keyCode);
        return consumed;
    }

    /** 模拟轴是离散边缘事件，无 UP/长按语义，但复用多击和序列判定。 */
    public void onAxisTrigger(int axisKeyCode) {
        if (!KeyMappingUtil.isAxisKey(axisKeyCode)) return;
        if (handleSequence(axisKeyCode)) return;
        recordCompletedTap(axisKeyCode);
    }

    // ---------- 单击 / 双击 / 三连按 ----------

    private void recordCompletedTap(int keyCode) {
        KeyMappingUtil.KeyMapping tap = getActiveMapping(keyCode, KeyMappingUtil.TRIGGER_TAP);
        KeyMappingUtil.KeyMapping dbl = getActiveMapping(keyCode, KeyMappingUtil.TRIGGER_DOUBLE_TAP);
        KeyMappingUtil.KeyMapping triple = getActiveMapping(keyCode, KeyMappingUtil.TRIGGER_TRIPLE_TAP);
        if (tap == null && dbl == null && triple == null) {
            clearTapState(keyCode);
            return;
        }

        cancelTapDecision(keyCode);
        int count = tapCounts.getOrDefault(keyCode, 0) + 1;
        tapCounts.put(keyCode, count);

        if (count >= 3) {
            if (triple != null) fire(triple);
            else if (dbl != null) fire(dbl);
            else if (tap != null) fire(tap);
            clearTapState(keyCode);
            return;
        }
        if (count == 2 && triple == null) {
            if (dbl != null) fire(dbl);
            else if (tap != null) fire(tap);
            clearTapState(keyCode);
            return;
        }
        if (count == 1 && dbl == null && triple == null) {
            if (tap != null) fire(tap);
            clearTapState(keyCode);
            return;
        }

        Runnable decide = () -> decidePendingTaps(keyCode);
        pendingTapDecisions.put(keyCode, decide);
        mainHandler.postDelayed(decide, KeyMappingUtil.DOUBLE_TAP_TIMEOUT);
    }

    private void decidePendingTaps(int keyCode) {
        pendingTapDecisions.remove(keyCode);
        int count = tapCounts.getOrDefault(keyCode, 0);
        KeyMappingUtil.KeyMapping tap = getActiveMapping(keyCode, KeyMappingUtil.TRIGGER_TAP);
        KeyMappingUtil.KeyMapping dbl = getActiveMapping(keyCode, KeyMappingUtil.TRIGGER_DOUBLE_TAP);
        KeyMappingUtil.KeyMapping triple = getActiveMapping(keyCode, KeyMappingUtil.TRIGGER_TRIPLE_TAP);
        if (count >= 3 && triple != null) fire(triple);
        else if (count >= 2 && dbl != null) fire(dbl);
        else if (count >= 1 && tap != null) fire(tap);
        tapCounts.remove(keyCode);
    }

    private void cancelTapDecision(int keyCode) {
        Runnable task = pendingTapDecisions.remove(keyCode);
        if (task != null) mainHandler.removeCallbacks(task);
    }

    private void clearTapState(int keyCode) {
        cancelTapDecision(keyCode);
        tapCounts.remove(keyCode);
    }

    private void suppressGesture(int keyCode) {
        Runnable longTask = longPressRunnables.remove(keyCode);
        if (longTask != null) mainHandler.removeCallbacks(longTask);
        clearTapState(keyCode);
        completedOnDown.remove(keyCode);
        longPressFired.remove(keyCode);
        deferredSequenceTaps.remove(keyCode);
    }

    // ---------- 序列 ----------

    private boolean handleSequence(int keyCode) {
        List<KeyMappingUtil.KeyMapping> sequences = getActiveSequences();
        if (sequences.isEmpty()) {
            if (sequenceActive) clearSequence();
            return false;
        }

        KeyMappingUtil.KeyMapping previouslyCompleted = null;
        int previousFirst = -1;
        if (!sequenceActive) {
            boolean starts = false;
            for (KeyMappingUtil.KeyMapping sequence : sequences) {
                if (sequence.sequenceKeys != null && sequence.sequenceKeys.length >= 2
                        && sequence.sequenceKeys[0] == keyCode) {
                    starts = true;
                    break;
                }
            }
            if (!starts) return false;
            sequenceBuffer.clear();
            sequenceBuffer.add(keyCode);
            sequenceActive = true;
        } else {
            previouslyCompleted = findExactActiveSequence(sequenceBuffer);
            if (sequenceBuffer.size() == 1) previousFirst = sequenceBuffer.get(0);
            sequenceBuffer.add(keyCode);
        }

        KeyMappingUtil.KeyMapping exact = null;
        boolean longerPrefix = false;
        for (KeyMappingUtil.KeyMapping sequence : sequences) {
            if (sequence.sequenceKeys == null || !isPrefix(sequence.sequenceKeys, sequenceBuffer)) continue;
            if (sequence.sequenceKeys.length == sequenceBuffer.size()) exact = sequence;
            else if (sequence.sequenceKeys.length > sequenceBuffer.size()) longerPrefix = true;
        }

        if (exact != null && !longerPrefix) {
            clearSequence();
            fire(exact);
            return true;
        }
        if (exact != null || longerPrefix) {
            // 若短序列同时是长序列前缀，等待窗口结束；超时才执行短序列。
            rescheduleSequenceTimeout();
            return true;
        }

        // 当前键不再是任何前缀：把此前唯一的首键回落为普通点击，并让当前键重新参与映射。
        clearSequence();
        if (previouslyCompleted != null) {
            fire(previouslyCompleted);
        } else if (previousFirst != -1) {
            if (pressedKeys.contains(previousFirst)) deferredSequenceTaps.add(previousFirst);
            else recordCompletedTap(previousFirst);
        }
        return false;
    }

    private void rescheduleSequenceTimeout() {
        if (sequenceTimeoutRunnable != null) mainHandler.removeCallbacks(sequenceTimeoutRunnable);
        sequenceTimeoutRunnable = () -> {
            sequenceTimeoutRunnable = null;
            if (!sequenceActive) return;

            List<Integer> snapshot = new ArrayList<>(sequenceBuffer);
            KeyMappingUtil.KeyMapping exact = findExactActiveSequence(snapshot);
            clearSequence();
            if (exact != null) {
                fire(exact);
            } else if (snapshot.size() == 1) {
                int first = snapshot.get(0);
                if (pressedKeys.contains(first)) deferredSequenceTaps.add(first);
                else recordCompletedTap(first);
            }
        };
        mainHandler.postDelayed(sequenceTimeoutRunnable, KeyMappingUtil.SEQUENCE_TIMEOUT);
    }

    private KeyMappingUtil.KeyMapping findExactActiveSequence(List<Integer> keys) {
        for (KeyMappingUtil.KeyMapping sequence : getActiveSequences()) {
            if (sequence.sequenceKeys != null && sequence.sequenceKeys.length == keys.size()
                    && isPrefix(sequence.sequenceKeys, keys)) {
                return sequence;
            }
        }
        return null;
    }

    private void clearSequence() {
        sequenceActive = false;
        sequenceBuffer.clear();
        if (sequenceTimeoutRunnable != null) {
            mainHandler.removeCallbacks(sequenceTimeoutRunnable);
            sequenceTimeoutRunnable = null;
        }
    }

    private List<KeyMappingUtil.KeyMapping> getActiveSequences() {
        List<KeyMappingUtil.KeyMapping> active = new ArrayList<>();
        for (KeyMappingUtil.KeyMapping sequence : KeyMappingUtil.getSequenceMappings(ctx)) {
            if (isActive(sequence)) active.add(sequence);
        }
        return active;
    }

    private static boolean isPrefix(int[] full, List<Integer> prefix) {
        if (prefix.size() > full.length) return false;
        for (int i = 0; i < prefix.size(); i++) {
            if (prefix.get(i) != full[i]) return false;
        }
        return true;
    }

    // ---------- 映射查询与执行 ----------

    private KeyMappingUtil.KeyMapping getActiveMapping(int keyCode, int trigger) {
        KeyMappingUtil.KeyMapping mapping = KeyMappingUtil.getMapping(ctx, keyCode, trigger);
        return isActive(mapping) ? mapping : null;
    }

    private KeyMappingUtil.KeyMapping getActiveCombo(int key1, int key2) {
        KeyMappingUtil.KeyMapping mapping = KeyMappingUtil.getComboMapping(ctx, key1, key2);
        return isActive(mapping) ? mapping : null;
    }

    private boolean hasActiveComboForKey(int keyCode) {
        for (KeyMappingUtil.KeyMapping mapping : KeyMappingUtil.getAllMappings(ctx)) {
            if (mapping.comboKeyCode != 0
                    && (mapping.keyCode == keyCode || mapping.comboKeyCode == keyCode)
                    && isActive(mapping)) {
                return true;
            }
        }
        return false;
    }

    private boolean hasAnyActiveMappingForKey(int keyCode) {
        for (KeyMappingUtil.KeyMapping mapping : KeyMappingUtil.getAllMappings(ctx)) {
            if (!isActive(mapping)) continue;
            if (mapping.trigger == KeyMappingUtil.TRIGGER_SEQUENCE) {
                // 空闲时只有首键会启动序列，后续成员不能无故吞掉系统原按键。
                if (mapping.sequenceKeys != null && mapping.sequenceKeys.length >= 2
                        && mapping.sequenceKeys[0] == keyCode) return true;
            } else if (mapping.comboKeyCode != 0) {
                if (mapping.keyCode == keyCode || mapping.comboKeyCode == keyCode) return true;
            } else if (mapping.keyCode == keyCode) {
                return true;
            }
        }
        return false;
    }

    private boolean isActive(KeyMappingUtil.KeyMapping mapping) {
        return mapping != null && mapping.enabled
                && KeyMappingUtil.isConstraintSatisfied(ctx, mapping);
    }

    private void fire(KeyMappingUtil.KeyMapping mapping) {
        if (!isActive(mapping)) return;
        KeyActionExecutor.execute(ctx, mapping);
    }

    /** 服务解绑/Activity 销毁时清理所有待执行任务和按压状态。 */
    public void cleanup() {
        for (Runnable task : longPressRunnables.values()) mainHandler.removeCallbacks(task);
        for (Runnable task : pendingTapDecisions.values()) mainHandler.removeCallbacks(task);
        longPressRunnables.clear();
        pendingTapDecisions.clear();
        tapCounts.clear();
        pressedKeys.clear();
        consumedKeys.clear();
        suppressedKeys.clear();
        longPressFired.clear();
        completedOnDown.clear();
        deferredSequenceTaps.clear();
        clearSequence();
    }
}
