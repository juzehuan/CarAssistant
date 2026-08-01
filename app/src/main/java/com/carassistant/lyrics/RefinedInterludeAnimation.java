package com.carassistant.lyrics;

/**
 * 间奏三点动画（1:1 复刻自歌词伴侣 RefinedInterludeAnimation）。
 *
 * - breathScale：整组三点呼吸缩放（2 秒一周期，cos）
 * - dotState：单个点的透明度/缩放（错峰出现，CSS ease 缓动）
 */
final class RefinedInterludeAnimation {

    private static final long BREATH_DURATION_MS = 2000;
    private static final long SCALE_EXTRA_MS = 150;

    private RefinedInterludeAnimation() {}

    private static float cubic(float t, float p1, float p2, float p3, float p4) {
        float u = 1.0f - t;
        return (u * u * u * p1)
                + (3 * u * u * t * p2)
                + (3 * u * t * t * p3)
                + (t * t * t * p4);
    }

    /**
     * 单个点的状态。
     *
     * @param elapsedMs    间奏段已过去的毫秒
     * @param totalMs      间奏段总时长
     * @param dotIndex     0/1/2，依次错峰
     */
    static DotState dotState(long elapsedMs, long totalMs, int dotIndex) {
        long step = Math.max(1L, totalMs / 3);
        float t = (float) (elapsedMs - (Math.max(0, Math.min(2, dotIndex)) * step));
        float progress = clamp01(t / (float) step);
        float opacity = (progress * 0.7f) + 0.2f;
        float scaleProgress = clamp01(t / (float) (step + SCALE_EXTRA_MS));
        float scale = (cssEase(scaleProgress) * 0.1f) + 0.9f;
        return new DotState(opacity, scale);
    }

    /** 整组呼吸缩放：1.0 ~ 1.05 */
    static float breathScale(long elapsedMs) {
        float m = floorMod(elapsedMs, BREATH_DURATION_MS) / 2000.0f;
        return (float) (1.0 + ((1.0 - Math.cos(m * Math.PI * 2.0)) * 0.05));
    }

    /** CSS 风格 ease：先用 cubic(0,0.25,0.25,1) 反解 t，再用 cubic(0,0.1,1,1) 求值 */
    private static float cssEase(float x) {
        float lo = 0.0f;
        float hi = 1.0f;
        for (int i = 0; i < 10; i++) {
            float mid = (lo + hi) * 0.5f;
            if (cubic(mid, 0.0f, 0.25f, 0.25f, 1.0f) < x) lo = mid;
            else hi = mid;
        }
        return cubic((lo + hi) * 0.5f, 0.0f, 0.1f, 1.0f, 1.0f);
    }

    private static float clamp01(float v) {
        return Math.max(0.0f, Math.min(1.0f, v));
    }

    /** Java 8 的 Math.floorMod（long 版本） */
    private static long floorMod(long x, long y) {
        long r = x % y;
        if (r == 0L) return 0L;
        return (((x ^ y) >> 63) | 1) > 0 ? r : r + y;
    }

    static final class DotState {
        final float opacity;
        final float scale;

        DotState(float opacity, float scale) {
            this.opacity = opacity;
            this.scale = scale;
        }
    }
}
