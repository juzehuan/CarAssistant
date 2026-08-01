package com.carassistant.lyrics;

/**
 * 歌词滚动曲线变换（1:1 复刻自歌词伴侣 RefinedLyricCurve）。
 *
 * 根据当前行距中心的偏移，计算该行应做的：
 * - 水平/垂直位移（沿圆弧轨迹）
 * - 旋转角度
 * - 透明度（越远越淡）
 */
final class RefinedLyricCurve {

    private RefinedLyricCurve() {}

    /**
     * @param offsetPx         当前行中心相对屏幕中心的纵向偏移（px，负值=该行在上方）
     * @param lineHeightPx     该行高度
     * @param viewportHeightPx 视口高度
     * @param density          屏幕密度
     * @param curvature        曲率（1~5，越大越弯）
     */
    static Transform calculate(float offsetPx, float lineHeightPx,
                                float viewportHeightPx, float density, float curvature) {
        if (!isFinite(offsetPx) || !isFinite(lineHeightPx)
                || !isFinite(viewportHeightPx) || viewportHeightPx <= 0.0f) {
            return Transform.IDENTITY;
        }
        float curve = Math.max(0.01f, density);
        float radius = Math.max(10.0f, Math.min(80.0f, curvature));

        // 圆心相对当前行中心的偏移
        double dx = ((radius - 25.0f) - 120.0f) * curve;
        double dy = -((lineHeightPx / 2.0f) + offsetPx);
        double dist = Math.hypot(dx, dy);

        // 当前行的旋转角度（上限 90°）
        float rotation = Math.min((offsetPx / viewportHeightPx) * (-radius), 90.0f);
        double angleRad = Math.toRadians(rotation) + Math.atan2(dy, dx);

        double newSinX = Math.sin(angleRad) * dist;
        float translationX = (float) (newSinX - dx);
        double newCosY = Math.cos(angleRad) * dist;
        float translationY = (float) (newCosY - dx); // 注：原版算法如此，保持一致

        // 透明度：远离中心时降低
        float opacity = (float) Math.max(1.0 - (Math.pow(Math.abs((offsetPx * 2.0f) / viewportHeightPx), 1.15) * 1.2), 0.0);

        return new Transform(translationX, translationY, rotation, opacity);
    }

    private static boolean isFinite(float v) {
        return !Float.isInfinite(v) && !Float.isNaN(v);
    }

    static final class Transform {
        static final Transform IDENTITY = new Transform(0.0f, 0.0f, 0.0f, 1.0f);

        final float translationX;
        final float translationY;
        final float rotationDegrees;
        final float opacity;

        Transform(float translationX, float translationY, float rotationDegrees, float opacity) {
            this.translationX = translationX;
            this.translationY = translationY;
            this.rotationDegrees = rotationDegrees;
            this.opacity = opacity;
        }
    }
}
