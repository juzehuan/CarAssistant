package com.carassistant.lyrics;

/**
 * 歌词浏览手势的弹簧物理投影（1:1 复刻自歌词伴侣 LyricPreviewMotion）。
 *
 * 职责：
 * - project：根据当前位移与速度，预测释放后还会"飘"多远
 * - projectedLineDelta：把预测位移换算成歌词行数
 * - rubberBand：到达边界后的橡皮筋回弹
 * - stepCritical：临界阻尼弹簧，用于释放后的归位动画
 */
final class LyricPreviewMotion {

    private static final float DECELERATION_RATE = 0.99f;

    private LyricPreviewMotion() {}

    /** 预测释放后位移增量（px） */
    static float project(float position, float velocityPxPerSecond) {
        return (((velocityPxPerSecond / 1000.0f) * DECELERATION_RATE) / 0.00999999f) + position;
    }

    /** 把预测位移换算为歌词行偏移（clamp 到 ±3 行） */
    static int projectedLineDelta(float position, float velocityPxPerSecond, float lineStepPx) {
        if (lineStepPx <= 0.0f) return 0;
        return Math.max(-3, Math.min(3, Math.round((-project(position, velocityPxPerSecond)) / lineStepPx)));
    }

    /** 橡皮筋回弹：在 lengthPx 容器内压缩位移 */
    static float rubberBand(float offset, float lengthPx) {
        if (lengthPx <= 0.0f || offset == 0.0f) return 0.0f;
        return ((offset * lengthPx) * 0.55f) / (lengthPx + (Math.abs(offset) * 0.55f));
    }

    /**
     * 临界阻尼弹簧积分（半隐式欧拉）。
     *
     * @param position     当前位移
     * @param velocity     当前速度
     * @param dtSec        时间步长（秒）
     * @param dampingRatio 阻尼比，越大越快稳定（0.12 ~ 1.0）
     */
    static SpringState stepCritical(float position, float velocity, float dtSec, float dampingRatio) {
        float dt = Math.max(0.0f, Math.min(0.05f, dtSec));
        double damping = Math.max(0.12f, dampingRatio);
        double omega0 = 6.283185307179586d / damping; // 2π / 周期
        double v0 = (omega0 * position) + velocity;
        double exp = Math.exp(-omega0 * dt);
        float newPosition = (float) ((position + (v0 * dt)) * exp);
        float newVelocity = (float) ((velocity - ((omega0 * v0) * dt)) * exp);
        if (Math.abs(newPosition) < 0.35f && Math.abs(newVelocity) < 4.0f) {
            return new SpringState(0.0f, 0.0f, true);
        }
        return new SpringState(newPosition, newVelocity, false);
    }

    /** 弹簧状态 */
    static final class SpringState {
        final float position;
        final float velocity;
        final boolean settled;

        SpringState(float position, float velocity, boolean settled) {
            this.position = position;
            this.velocity = velocity;
            this.settled = settled;
        }
    }
}
