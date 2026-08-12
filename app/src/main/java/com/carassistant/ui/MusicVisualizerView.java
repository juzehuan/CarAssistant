package com.carassistant.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.os.Handler;
import android.os.Looper;
import android.util.AttributeSet;
import android.view.View;

import java.util.Random;

/**
 * 无需录音权限的音乐律动视图。
 *
 * 五种模式只使用当前页面强调色及其柔和派生色，避免旧版彩虹色和高亮噪点抢走
 * 歌词、封面与播放控制的视觉焦点。模式编号保持 0..4 不变，兼容已有设置。
 */
public class MusicVisualizerView extends View {

    public static final int MODE_NEON_BARS = 0;       // 流光波纹
    public static final int MODE_CIRCLE_SPECTRUM = 1; // 环形律动
    public static final int MODE_PARTICLE_WAVE = 2;   // 极光流体
    public static final int MODE_COLUMN_EQ = 3;       // 节拍灯带
    public static final int MODE_DOT_PULSE = 4;       // 星轨粒子

    private static final int BAR_COUNT = 64;
    private static final long FRAME_MS = 33L;
    private static final float BASELINE = 0.035f;

    private final Paint mainPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint detailPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path mainPath = new Path();
    private final Path auxPath = new Path();
    private final float[] bars = new float[BAR_COUNT];
    private final float[] peaks = new float[BAR_COUNT];
    private final float[] peakVelocity = new float[BAR_COUNT];
    private final Random random = new Random();

    private int accentColor = 0xFFEE0A24;
    private boolean active;
    private int mode = MODE_NEON_BARS;
    private float globalEnergy;
    private float externalLevel = 0.6f;
    private float phase;
    private long lastUpdate;
    private boolean running;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            if (!running) return;
            update();
            invalidate();
            handler.postDelayed(this, FRAME_MS);
        }
    };

    public MusicVisualizerView(Context context) {
        super(context);
        init();
    }

    public MusicVisualizerView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public MusicVisualizerView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        init();
    }

    private void init() {
        setLayerType(LAYER_TYPE_SOFTWARE, null);
        for (int i = 0; i < BAR_COUNT; i++) {
            bars[i] = BASELINE;
            peaks[i] = BASELINE;
        }
        mainPaint.setStyle(Paint.Style.STROKE);
        mainPaint.setStrokeCap(Paint.Cap.ROUND);
        mainPaint.setStrokeJoin(Paint.Join.ROUND);
        glowPaint.setStyle(Paint.Style.STROKE);
        glowPaint.setStrokeCap(Paint.Cap.ROUND);
        glowPaint.setStrokeJoin(Paint.Join.ROUND);
        fillPaint.setStyle(Paint.Style.FILL);
        detailPaint.setStyle(Paint.Style.FILL);
    }

    public void setAccentColor(int color) {
        accentColor = Color.rgb(Color.red(color), Color.green(color), Color.blue(color));
        invalidate();
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public void setLevel(float level) {
        externalLevel = clamp(level, 0f, 1f);
    }

    /** 保持旧设置兼容；越界值回到默认流光波纹。 */
    public void setMode(int mode) {
        this.mode = mode >= 0 && mode <= 4 ? mode : MODE_NEON_BARS;
        invalidate();
    }

    public void start() {
        if (running) return;
        running = true;
        handler.post(tick);
    }

    public void stop() {
        running = false;
        handler.removeCallbacks(tick);
        lastUpdate = 0L;
    }

    private void update() {
        long now = System.currentTimeMillis();
        float dt = lastUpdate == 0L ? FRAME_MS : Math.min(50f, now - lastUpdate);
        lastUpdate = now;
        phase += dt * (active ? 0.0036f : 0.0012f);

        globalEnergy *= (float) Math.pow(active ? 0.91f : 0.84f, dt / FRAME_MS);
        if (active && random.nextFloat() < 0.026f * dt / FRAME_MS) {
            globalEnergy = Math.min(1f, globalEnergy + 0.32f + random.nextFloat() * 0.34f);
        }

        float seconds = now / 1000f;
        float scale = active ? 0.44f + externalLevel * 0.46f : 0.08f;
        for (int i = 0; i < BAR_COUNT; i++) {
            float normalized = i / (float) (BAR_COUNT - 1);
            float envelope = 0.42f + 0.58f * (float) Math.sin(Math.PI * normalized);
            float slow = 0.5f + 0.5f * (float) Math.sin(seconds * 2.1f + i * 0.29f);
            float fast = 0.5f + 0.5f * (float) Math.sin(seconds * 4.7f + i * 0.71f);
            float target = BASELINE + envelope * (slow * 0.62f + fast * 0.38f)
                    * scale * (1f + globalEnergy * 0.34f);
            if (active) target += random.nextFloat() * 0.025f;
            target = clamp(target, BASELINE, 1f);
            bars[i] += (target - bars[i]) * (active ? 0.24f : 0.08f);

            if (bars[i] >= peaks[i]) {
                peaks[i] = bars[i];
                peakVelocity[i] = 0f;
            } else {
                peakVelocity[i] += dt * 0.00055f;
                peaks[i] = Math.max(bars[i], peaks[i] - peakVelocity[i] * dt);
            }
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (getWidth() <= 0 || getHeight() <= 0) return;
        switch (mode) {
            case MODE_CIRCLE_SPECTRUM:
                drawOrbit(canvas);
                break;
            case MODE_PARTICLE_WAVE:
                drawAurora(canvas);
                break;
            case MODE_COLUMN_EQ:
                drawBeatRibbon(canvas);
                break;
            case MODE_DOT_PULSE:
                drawStarTrail(canvas);
                break;
            case MODE_NEON_BARS:
            default:
                drawLightRibbon(canvas);
                break;
        }
    }

    /** 模式 0：三层柔和流光，以一条明亮主波纹作为视觉中心。 */
    private void drawLightRibbon(Canvas canvas) {
        float w = getWidth();
        float h = getHeight();
        float cy = h * 0.53f;
        int warm = blend(accentColor, 0xFFFF8A72, 0.32f);

        for (int layer = 2; layer >= 0; layer--) {
            float amplitude = h * (0.14f + layer * 0.055f);
            buildWavePath(mainPath, w, cy, amplitude, layer * 1.45f, 28);
            int color = layer == 0 ? blend(accentColor, Color.WHITE, 0.34f)
                    : (layer == 1 ? accentColor : warm);

            glowPaint.setShader(null);
            glowPaint.setColor(withAlpha(color, active ? 34 : 15));
            glowPaint.setStrokeWidth(dp(7f - layer));
            canvas.drawPath(mainPath, glowPaint);

            mainPaint.setShader(new LinearGradient(0, 0, w, 0,
                    new int[]{withAlpha(color, 28), withAlpha(color, active ? 228 : 105),
                            withAlpha(blend(color, Color.WHITE, 0.22f), active ? 245 : 120),
                            withAlpha(color, 28)},
                    new float[]{0f, 0.26f, 0.68f, 1f}, Shader.TileMode.CLAMP));
            mainPaint.setStrokeWidth(dp(layer == 0 ? 2.2f : 1.35f));
            canvas.drawPath(mainPath, mainPaint);
        }
        mainPaint.setShader(null);

        fillPaint.setShader(new RadialGradient(w * 0.56f, cy, w * 0.32f,
                new int[]{withAlpha(accentColor, active ? 24 : 10), Color.TRANSPARENT},
                null, Shader.TileMode.CLAMP));
        canvas.drawOval(w * 0.18f, cy - h * 0.38f, w * 0.92f, cy + h * 0.38f, fillPaint);
        fillPaint.setShader(null);
    }

    /** 模式 1：细密环形频谱，适合黑胶主题但不与主唱片争抢。 */
    private void drawOrbit(Canvas canvas) {
        float w = getWidth();
        float h = getHeight();
        float cx = w * 0.5f;
        float cy = h * 0.52f;
        float radiusX = w * 0.25f;
        float radiusY = h * 0.20f;
        int soft = blend(accentColor, Color.WHITE, 0.42f);

        int rays = 56;
        for (int i = 0; i < rays; i++) {
            float angle = (float) (Math.PI * 2d * i / rays - Math.PI / 2d + phase * 0.12f);
            float level = bars[(i * BAR_COUNT / rays) % BAR_COUNT];
            float cos = (float) Math.cos(angle);
            float sin = (float) Math.sin(angle);
            float innerX = cx + cos * radiusX;
            float innerY = cy + sin * radiusY;
            float spike = h * (0.035f + level * 0.14f);
            float outerX = innerX + cos * spike * 1.2f;
            float outerY = innerY + sin * spike;
            mainPaint.setShader(null);
            mainPaint.setColor(withAlpha(i % 3 == 0 ? soft : accentColor,
                    active ? 110 + Math.round(level * 130f) : 70));
            mainPaint.setStrokeWidth(dp(i % 3 == 0 ? 2f : 1.15f));
            canvas.drawLine(innerX, innerY, outerX, outerY, mainPaint);
        }

        mainPaint.setColor(withAlpha(soft, active ? 190 : 92));
        mainPaint.setStrokeWidth(dp(1.4f));
        canvas.drawOval(cx - radiusX, cy - radiusY, cx + radiusX, cy + radiusY, mainPaint);
        mainPaint.setColor(withAlpha(accentColor, active ? 62 : 25));
        mainPaint.setStrokeWidth(dp(4f));
        float glowScale = 1.14f + globalEnergy * 0.025f;
        canvas.drawOval(cx - radiusX * glowScale, cy - radiusY * glowScale,
                cx + radiusX * glowScale, cy + radiusY * glowScale, mainPaint);

        float starAngle = phase * 0.48f;
        float starX = cx + (float) Math.cos(starAngle) * radiusX;
        float starY = cy + (float) Math.sin(starAngle) * radiusY;
        detailPaint.setShader(new RadialGradient(starX, starY, dp(9f),
                new int[]{withAlpha(soft, active ? 220 : 105), Color.TRANSPARENT},
                null, Shader.TileMode.CLAMP));
        canvas.drawCircle(starX, starY, dp(9f), detailPaint);
        detailPaint.setShader(null);
        detailPaint.setColor(withAlpha(soft, active ? 235 : 115));
        canvas.drawCircle(starX, starY, dp(1.8f + globalEnergy * 1.2f), detailPaint);
    }

    /** 模式 2：半透明极光色带，动画缓慢，适合长时间驻留。 */
    private void drawAurora(Canvas canvas) {
        float w = getWidth();
        float h = getHeight();
        float cy = h * 0.53f;
        int rose = blend(accentColor, 0xFFFF7995, 0.38f);

        for (int layer = 2; layer >= 0; layer--) {
            float amp = h * (0.12f + layer * 0.055f);
            float offset = (layer - 1) * h * 0.08f;
            buildWavePath(mainPath, w, cy + offset, amp, layer * 1.8f, 32);
            auxPath.reset();
            auxPath.addPath(mainPath);
            auxPath.lineTo(w, cy + offset + h * 0.25f);
            auxPath.lineTo(0, cy + offset + h * 0.25f);
            auxPath.close();

            int color = layer == 0 ? blend(accentColor, Color.WHITE, 0.22f)
                    : (layer == 1 ? accentColor : rose);
            fillPaint.setShader(new LinearGradient(0, cy - amp, 0, cy + h * 0.25f,
                    new int[]{withAlpha(color, active ? 58 - layer * 10 : 20),
                            withAlpha(color, active ? 12 : 5), Color.TRANSPARENT},
                    null, Shader.TileMode.CLAMP));
            canvas.drawPath(auxPath, fillPaint);

            mainPaint.setShader(null);
            mainPaint.setColor(withAlpha(color, active ? 185 - layer * 28 : 72));
            mainPaint.setStrokeWidth(dp(layer == 0 ? 2f : 1.2f));
            canvas.drawPath(mainPath, mainPaint);
        }
        fillPaint.setShader(null);

        for (int i = 0; i < 9; i++) {
            float x = w * (0.1f + i * 0.1f);
            int bi = i * (BAR_COUNT - 1) / 8;
            float y = cy + (float) Math.sin(i * 0.8f + phase) * h * 0.12f * (0.4f + bars[bi]);
            float r = dp(1.1f + bars[bi] * 1.9f);
            detailPaint.setColor(withAlpha(i % 2 == 0 ? rose : Color.WHITE,
                    active ? 90 + Math.round(bars[bi] * 110f) : 58));
            canvas.drawCircle(x, y, r, detailPaint);
        }
    }

    /** 模式 3：从下向上生长的节拍灯带；大留白、低密度，不再像一排对称栅栏。 */
    private void drawBeatRibbon(Canvas canvas) {
        float w = getWidth();
        float h = getHeight();
        float baseline = h * 0.76f;
        int count = Math.max(19, Math.min(31, Math.round(w / dp(19f))));
        if (count % 2 == 0) count--;
        float contentWidth = w * 0.78f;
        float barWidth = Math.min(dp(5.6f), contentWidth / count * 0.34f);
        float gap = (contentWidth - barWidth * count) / Math.max(1, count - 1);
        float startX = (w - contentWidth) * 0.5f;
        int soft = blend(accentColor, Color.WHITE, 0.42f);
        int deep = blend(accentColor, Color.BLACK, 0.28f);

        // 灯带下方只有一层很轻的环境光，避免出现厚重卡片感。
        fillPaint.setShader(new RadialGradient(w * 0.5f, baseline, w * 0.34f,
                new int[]{withAlpha(accentColor, active ? 25 : 10), Color.TRANSPARENT},
                null, Shader.TileMode.CLAMP));
        canvas.drawOval(w * 0.15f, baseline - h * 0.34f,
                w * 0.85f, baseline + h * 0.22f, fillPaint);
        fillPaint.setShader(null);

        for (int i = 0; i < count; i++) {
            int bi = i * (BAR_COUNT - 1) / Math.max(1, count - 1);
            int neighbor = Math.min(BAR_COUNT - 1, bi + 3);
            float level = bars[bi] * 0.74f + bars[neighbor] * 0.26f;
            float position = i / (float) Math.max(1, count - 1);
            float envelope = 0.48f + 0.52f * (float) Math.sin(Math.PI * position);
            float stagger = 0.88f + 0.12f * (float) Math.sin(i * 1.73f + phase * 0.42f);
            float height = dp(6f) + level * h * 0.54f * envelope * stagger;
            float left = startX + i * (barWidth + gap);
            float radius = barWidth * 0.5f;
            float top = baseline - height;

            glowPaint.setShader(null);
            glowPaint.setStyle(Paint.Style.FILL);
            glowPaint.setColor(withAlpha(accentColor, active ? 18 + Math.round(level * 32f) : 9));
            canvas.drawRoundRect(left - dp(2.2f), top - dp(2.2f),
                    left + barWidth + dp(2.2f), baseline + dp(2.2f),
                    radius + dp(2.2f), radius + dp(2.2f), glowPaint);

            fillPaint.setShader(new LinearGradient(0, top, 0, baseline,
                    new int[]{withAlpha(soft, active ? 245 : 118),
                            withAlpha(accentColor, active ? 224 : 92),
                            withAlpha(deep, active ? 142 : 60)},
                    new float[]{0f, 0.36f, 1f},
                    Shader.TileMode.CLAMP));
            canvas.drawRoundRect(left, top, left + barWidth, baseline,
                    radius, radius, fillPaint);

            // 极短的倒影把所有柱子串成一条灯带，但不再上下完全镜像。
            float reflection = Math.min(h - baseline - dp(2f), height * 0.13f);
            fillPaint.setShader(new LinearGradient(0, baseline, 0, baseline + reflection,
                    new int[]{withAlpha(accentColor, active ? 54 : 24), Color.TRANSPARENT},
                    null, Shader.TileMode.CLAMP));
            canvas.drawRoundRect(left, baseline + dp(2f), left + barWidth,
                    baseline + dp(2f) + reflection, radius, radius, fillPaint);
        }

        fillPaint.setShader(null);
        glowPaint.setStyle(Paint.Style.STROKE);

        mainPaint.setShader(new LinearGradient(startX, 0, startX + contentWidth, 0,
                new int[]{Color.TRANSPARENT, withAlpha(accentColor, active ? 68 : 28),
                        withAlpha(soft, active ? 86 : 34), Color.TRANSPARENT},
                new float[]{0f, 0.24f, 0.68f, 1f}, Shader.TileMode.CLAMP));
        mainPaint.setStrokeWidth(dp(0.8f));
        canvas.drawLine(startX, baseline + dp(1f), startX + contentWidth,
                baseline + dp(1f), mainPaint);
        mainPaint.setShader(null);
    }

    /** 模式 4：两条细线星轨与呼吸粒子，氛围感更强但保持留白。 */
    private void drawStarTrail(Canvas canvas) {
        float w = getWidth();
        float h = getHeight();
        float cy = h * 0.52f;
        int rose = blend(accentColor, 0xFFFFB0BE, 0.48f);

        for (int trail = 0; trail < 2; trail++) {
            buildWavePath(mainPath, w, cy + (trail == 0 ? -h * 0.08f : h * 0.09f),
                    h * (trail == 0 ? 0.18f : 0.13f), trail * 2.4f + 0.5f, 24);
            mainPaint.setShader(new LinearGradient(0, 0, w, 0,
                    new int[]{Color.TRANSPARENT, withAlpha(trail == 0 ? rose : accentColor,
                            active ? 125 : 52), Color.TRANSPARENT},
                    new float[]{0f, 0.55f, 1f}, Shader.TileMode.CLAMP));
            mainPaint.setStrokeWidth(dp(trail == 0 ? 1.35f : 0.9f));
            canvas.drawPath(mainPath, mainPaint);
        }
        mainPaint.setShader(null);

        int dots = 24;
        for (int i = 0; i < dots; i++) {
            float t = (i + 0.5f) / dots;
            int bi = i * (BAR_COUNT - 1) / (dots - 1);
            float drift = (float) Math.sin(t * Math.PI * 4f + phase + i * 0.17f);
            float y = cy + drift * h * 0.19f * (0.34f + bars[bi]);
            float x = t * w;
            float radius = dp(0.85f + bars[bi] * 2.1f);
            int dotColor = i % 4 == 0 ? Color.WHITE : (i % 2 == 0 ? rose : accentColor);

            detailPaint.setColor(withAlpha(dotColor, active ? 24 + Math.round(bars[bi] * 35f) : 12));
            canvas.drawCircle(x, y, radius * 3.2f, detailPaint);
            detailPaint.setColor(withAlpha(dotColor, active ? 125 + Math.round(bars[bi] * 120f) : 72));
            canvas.drawCircle(x, y, radius, detailPaint);
        }
    }

    private void buildWavePath(Path path, float width, float centerY, float amplitude,
                               float phaseOffset, int samples) {
        path.reset();
        float previousX = 0f;
        float previousY = waveY(0f, centerY, amplitude, phaseOffset, 0);
        path.moveTo(previousX, previousY);
        for (int i = 1; i <= samples; i++) {
            float t = i / (float) samples;
            float x = width * t;
            int bi = Math.min(BAR_COUNT - 1, Math.round(t * (BAR_COUNT - 1)));
            float y = waveY(t, centerY, amplitude, phaseOffset, bi);
            float midX = (previousX + x) * 0.5f;
            path.cubicTo(midX, previousY, midX, y, x, y);
            previousX = x;
            previousY = y;
        }
    }

    private float waveY(float t, float centerY, float amplitude, float phaseOffset, int barIndex) {
        float envelope = 0.35f + 0.65f * (float) Math.sin(Math.PI * t);
        float motion = (float) Math.sin(t * Math.PI * 3.6f + phase + phaseOffset);
        return centerY + motion * amplitude * envelope * (0.28f + bars[barIndex] * 0.72f);
    }

    private int withAlpha(int color, int alpha) {
        return (clamp(alpha, 0, 255) << 24) | (color & 0x00FFFFFF);
    }

    private int blend(int from, int to, float ratio) {
        ratio = clamp(ratio, 0f, 1f);
        return Color.rgb(
                Math.round(Color.red(from) + (Color.red(to) - Color.red(from)) * ratio),
                Math.round(Color.green(from) + (Color.green(to) - Color.green(from)) * ratio),
                Math.round(Color.blue(from) + (Color.blue(to) - Color.blue(from)) * ratio));
    }

    private float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private float dp(float value) {
        return value * getResources().getDisplayMetrics().density;
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        stop();
    }
}
