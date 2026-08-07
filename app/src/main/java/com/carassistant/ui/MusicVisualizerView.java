package com.carassistant.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.os.Handler;
import android.os.Looper;
import android.util.AttributeSet;
import android.view.View;

import java.util.Random;

/**
 * 音乐律动可视化（无需录音权限），支持 5 种特效模式：
 * <ul>
 *   <li>0 — 霓虹声波：镜像霓虹条 + 辉光 + 星点（原有）</li>
 *   <li>1 — 圆形频谱：同心圆环随节奏呼吸缩放</li>
 *   <li>2 — 粒子波浪：多点正弦波浪线</li>
 *   <li>3 — 柱状均衡：经典上下跳动均衡器</li>
 *   <li>4 — 跳动圆点：一排大小随节奏变化的圆点</li>
 * </ul>
 */
public class MusicVisualizerView extends View {

    public static final int MODE_NEON_BARS = 0;
    public static final int MODE_CIRCLE_SPECTRUM = 1;
    public static final int MODE_PARTICLE_WAVE = 2;
    public static final int MODE_COLUMN_EQ = 3;
    public static final int MODE_DOT_PULSE = 4;

    private static final int BAR_COUNT = 56;
    private static final long FRAME_MS = 33;
    private static final float BASELINE = 0.025f;
    private static final float HUE_SPREAD = 55f;
    private static final float MID = (BAR_COUNT - 1) / 2f;

    private final Paint corePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint capPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint wavePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private int accentColor = 0xFFEE0A24;
    private float baseHue = 352f;
    private boolean active = false;
    private int mode = MODE_NEON_BARS;

    private final float[] bars = new float[BAR_COUNT];
    private final float[] peak = new float[BAR_COUNT];
    private final float[] peakVel = new float[BAR_COUNT];
    private float globalEnergy = 0f;
    private float externalLevel = 0.6f;
    private long lastUpdate = 0;

    private final Random random = new Random();
    private final float[] hsv = new float[3];
    private final Path wavePath = new Path();
    private final Path spectrumPath = new Path();
    private final Paint spectrumFill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint spectrumStroke = new Paint(Paint.ANTI_ALIAS_FLAG);

    // 圆形频谱专用
    private float circlePhase = 0f;
    // 粒子波浪专用
    private float wavePhase = 0f;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable tick = new Runnable() {
        @Override public void run() {
            update();
            invalidate();
            handler.postDelayed(this, FRAME_MS);
        }
    };

    public MusicVisualizerView(Context context) { super(context); init(); }
    public MusicVisualizerView(Context context, AttributeSet attrs) { super(context, attrs); init(); }
    public MusicVisualizerView(Context context, AttributeSet attrs, int defStyle) { super(context, attrs, defStyle); init(); }

    private void init() {
        for (int i = 0; i < BAR_COUNT; i++) {
            bars[i] = BASELINE; peak[i] = BASELINE; peakVel[i] = 0f;
        }
        corePaint.setStyle(Paint.Style.FILL);
        glowPaint.setStyle(Paint.Style.FILL);
        capPaint.setStyle(Paint.Style.FILL);
        capPaint.setColor(Color.WHITE);
        linePaint.setStyle(Paint.Style.FILL);
        wavePaint.setStyle(Paint.Style.STROKE);
        wavePaint.setStrokeWidth(3f);
        wavePaint.setStrokeCap(Paint.Cap.ROUND);
        spectrumFill.setStyle(Paint.Style.FILL);
        spectrumStroke.setStyle(Paint.Style.STROKE);
        spectrumStroke.setStrokeWidth(2f);
        spectrumStroke.setStrokeCap(Paint.Cap.ROUND);
        spectrumStroke.setStrokeJoin(Paint.Join.ROUND);

        dotPaint.setStyle(Paint.Style.FILL);
    }

    public void setAccentColor(int color) {
        this.accentColor = color;
        float[] tmp = new float[3];
        Color.colorToHSV(color, tmp);
        baseHue = tmp[0];
    }

    public void setActive(boolean active) { this.active = active; }

    public void setLevel(float level) {
        if (level >= 0f && level <= 1f) externalLevel = level;
    }

    /** 设置特效模式（0=霓虹声波, 1=圆形频谱, 2=粒子波浪, 3=柱状均衡, 4=跳动圆点） */
    public void setMode(int mode) {
        if (mode >= 0 && mode <= 4) this.mode = mode;
    }

    public void start() { if (!handler.hasCallbacks(tick)) handler.post(tick); }
    public void stop() { handler.removeCallbacks(tick); }

    private void update() {
        long now = System.currentTimeMillis();
        float dt = lastUpdate == 0 ? FRAME_MS : Math.min(50, now - lastUpdate);
        lastUpdate = now;
        float t = now / 1000f;

        globalEnergy *= (float) Math.pow(0.93f, dt / FRAME_MS);
        if (active && random.nextDouble() < 0.025 * dt / FRAME_MS) {
            globalEnergy = Math.min(1f, globalEnergy + 0.45f + random.nextFloat() * 0.3f);
        }
        float ampScale = active ? (0.45f + 0.55f * externalLevel) : 1f;

        circlePhase += dt * 0.002f;
        wavePhase += dt * 0.003f;

        for (int i = 0; i < BAR_COUNT; i++) {
            float target;
            if (active) {
                float env = 0.5f + 0.5f * (float) Math.sin(Math.PI * i / (BAR_COUNT - 1));
                double f = 2.0 + i * 0.07;
                double w1 = Math.sin(t * f + i * 0.6) * 0.5 + 0.5;
                double w2 = Math.sin(t * f * 1.9 + i * 1.1) * 0.5 + 0.5;
                double w3 = Math.sin(t * f * 0.5 + i * 0.3) * 0.5 + 0.5;
                target = (float) ((w1 * 0.5 + w2 * 0.3 + w3 * 0.2) * env * ampScale * (1f + globalEnergy * 0.7f));
                target += random.nextFloat() * 0.04f;
                if (target > 1f) target = 1f;
                if (target < 0f) target = 0f;
            } else {
                target = BASELINE + 0.02f * (float) Math.sin(t * 1.1f + i * 0.5f);
            }
            bars[i] += (target - bars[i]) * (active ? 0.3f : 0.09f);
            if (bars[i] >= peak[i]) { peak[i] = bars[i]; peakVel[i] = 0f; }
            else {
                peakVel[i] += dt * 0.0009f;
                peak[i] -= peakVel[i] * dt;
                if (peak[i] < bars[i]) peak[i] = bars[i];
                if (peak[i] < 0f) peak[i] = 0f;
            }
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        switch (mode) {
            case MODE_NEON_BARS:   drawNeonBars(canvas); break;
            case MODE_CIRCLE_SPECTRUM: drawCircleSpectrum(canvas); break;
            case MODE_PARTICLE_WAVE: drawParticleWave(canvas); break;
            case MODE_COLUMN_EQ:   drawColumnEq(canvas); break;
            case MODE_DOT_PULSE:   drawDotPulse(canvas); break;
            default: drawNeonBars(canvas);
        }
    }

    // === 模式 0：霓虹声波（原创效果） ===
    private void drawNeonBars(Canvas canvas) {
        int w = getWidth(), h = getHeight();
        if (w <= 0 || h <= 0) return;
        float gap = Math.max(2f, w * 0.006f);
        float barW = (w - gap * (BAR_COUNT - 1)) / (float) BAR_COUNT;
        if (barW <= 0) return;
        float centerY = h * 0.5f, halfSpan = h * 0.40f;

        for (int i = 0; i < BAR_COUNT; i++) {
            float lv = bars[i];
            float bh = Math.max(2f, lv * halfSpan);
            float x = i * (barW + gap);
            float hue = baseHue + ((i - MID) / MID) * HUE_SPREAD;
            hsv[0] = hue; hsv[1] = 0.9f; hsv[2] = 1f;
            int col = Color.HSVToColor(hsv);

            glowPaint.setColor(col);
            glowPaint.setAlpha(active ? 70 : 40);
            float gw = barW * 2.1f;
            drawBar(canvas, glowPaint, x - (gw - barW) / 2f, gw, bh * 1.05f, centerY);

            corePaint.setColor(col);
            corePaint.setAlpha(active ? 255 : 150);
            drawBar(canvas, corePaint, x, barW, bh, centerY);

            capPaint.setAlpha(active ? 210 : 120);
            float cr = barW * 0.6f;
            canvas.drawCircle(x + barW / 2f, centerY - bh, cr, capPaint);
            canvas.drawCircle(x + barW / 2f, centerY + bh, cr, capPaint);

            if (peak[i] > lv + 0.01f) {
                capPaint.setAlpha(255);
                float pr = barW * 0.8f, ph = peak[i] * halfSpan;
                canvas.drawCircle(x + barW / 2f, centerY - ph, pr, capPaint);
                canvas.drawCircle(x + barW / 2f, centerY + ph, pr, capPaint);
            }
        }
        hsv[0] = baseHue; hsv[1] = 0.9f; hsv[2] = 1f;
        linePaint.setColor(Color.HSVToColor(hsv));
        linePaint.setAlpha(active ? 55 : 25);
        canvas.drawRect(0, centerY - 1.5f, w, centerY + 1.5f, linePaint);
    }

    // === 模式 1：圆形频谱 ===
    private void drawCircleSpectrum(Canvas canvas) {
        int w = getWidth(), h = getHeight();
        if (w <= 0 || h <= 0) return;
        float cx = w / 2f, cy = h / 2f;
        float maxR = Math.min(cx, cy) * 0.85f;
        int rings = 6;
        for (int r = 0; r < rings; r++) {
            int bi = (int) (r * BAR_COUNT / (float) rings);
            if (bi >= BAR_COUNT) bi = BAR_COUNT - 1;
            float level = bars[bi];
            float radius = maxR * (0.2f + 0.8f * level) * (r + 1f) / rings;
            float hue = baseHue + r * 12f;
            hsv[0] = hue; hsv[1] = 0.8f; hsv[2] = 1f;
            int col = Color.HSVToColor(hsv);

            glowPaint.setColor(col);
            glowPaint.setAlpha(active ? 60 : 30);
            wavePaint.setColor(col);
            wavePaint.setAlpha(active ? 200 : 100);
            wavePaint.setStrokeWidth(2.5f + level * 4f);
            canvas.drawCircle(cx, cy, radius + circlePhase % 1f * 3f, wavePaint);

            // 内圈辉光
            glowPaint.setAlpha(active ? 25 : 10);
            canvas.drawCircle(cx, cy, radius * 0.85f, glowPaint);
        }
        // 中心亮点
        hsv[0] = baseHue; hsv[1] = 0.9f; hsv[2] = 1f;
        dotPaint.setColor(Color.HSVToColor(hsv));
        dotPaint.setAlpha(active ? 220 : 100);
        canvas.drawCircle(cx, cy, 4f + globalEnergy * 6f, dotPaint);
    }

    // === 模式 2：粒子波浪 ===
    private void drawParticleWave(Canvas canvas) {
        int w = getWidth(), h = getHeight();
        if (w <= 0 || h <= 0) return;
        float cx = w / 2f, cy = h / 2f;
        // 3 条波浪线
        for (int wave = 0; wave < 3; wave++) {
            wavePath.reset();
            float yBase = cy + (wave - 1) * h * 0.22f;
            float amp = h * 0.18f * (0.5f + 0.5f * globalEnergy);
            float hue = baseHue + wave * 20f;
            for (int i = 0; i <= w; i += 8) {
                float t = i / (float) w;
                int bi = (int) (t * (BAR_COUNT - 1));
                float y = yBase + (float) Math.sin(t * Math.PI * 4 + wavePhase + wave * 2.1f) * amp * bars[bi];
                if (i == 0) wavePath.moveTo(i, y);
                else wavePath.lineTo(i, y);
            }
            hsv[0] = hue; hsv[1] = 0.85f; hsv[2] = 1f;
            wavePaint.setColor(Color.HSVToColor(hsv));
            wavePaint.setAlpha(active ? 200 : 100);
            wavePaint.setStrokeWidth(2f + globalEnergy * 2f);
            canvas.drawPath(wavePath, wavePaint);
        }
        // 散布粒子
        dotPaint.setColor(accentColor);
        for (int i = 0; i < 12; i++) {
            int bi = (i * BAR_COUNT / 12);
            float t = i / 11f;
            float x = (float) (t * w + Math.sin(wavePhase * 2 + i) * 8f);
            float yBase = cy + (i % 3 - 1) * h * 0.22f;
            float y = yBase + bars[bi] * h * 0.2f;
            float r = 1.5f + bars[bi] * 5f;
            dotPaint.setAlpha((int) (80 + bars[bi] * 175));
            canvas.drawCircle(x, y, r, dotPaint);
        }
    }

    // === 模式 3：平滑频谱曲线 ===
    private void drawColumnEq(Canvas canvas) {
        int w = getWidth(), h = getHeight();
        if (w <= 0 || h <= 0) return;
        float centerY = h * 0.5f;
        float maxH = h * 0.42f;
        int baseAlpha = active ? 240 : 120;

        // 取均匀采样点（上半部分）
        int samples = 40;
        float[] ptsX = new float[samples + 2];
        float[] ptsY = new float[samples + 2];
        ptsX[0] = 0; ptsY[0] = centerY;
        for (int i = 0; i < samples; i++) {
            int bi = (int) (i * BAR_COUNT / (float) samples);
            ptsX[i + 1] = (i + 1) * w / (float) (samples + 1);
            ptsY[i + 1] = centerY - bars[bi] * maxH;
        }
        ptsX[samples + 1] = w; ptsY[samples + 1] = centerY;

        // 下半部分（镜像，暗色）
        float[] ptsYDim = new float[samples + 2];
        ptsYDim[0] = centerY; ptsYDim[samples + 1] = centerY;
        for (int i = 0; i < samples; i++) {
            int bi = (int) (i * BAR_COUNT / (float) samples);
            ptsYDim[i + 1] = centerY + bars[bi] * maxH * 0.65f;
        }

        // === 上半部：渐变填充 ===
        spectrumPath.reset();
        spectrumPath.moveTo(0, centerY);
        for (int i = 0; i <= samples + 1; i++) {
            if (i <= 1 || i >= samples) {
                spectrumPath.lineTo(ptsX[i], ptsY[i]);
            } else {
                float px = ptsX[i - 1], py = ptsY[i - 1];
                float cx1 = (px + ptsX[i]) / 2f;
                spectrumPath.cubicTo(cx1, py, cx1, ptsY[i], ptsX[i], ptsY[i]);
            }
        }
        spectrumPath.close();

        // 渐变填充：顶部亮红 → 底部透明
        android.graphics.LinearGradient fillGrad = new android.graphics.LinearGradient(
                0, centerY - maxH, 0, centerY,
                new int[]{shiftAlpha(accentColor, 0.55f), shiftAlpha(accentColor, 0.05f)},
                null, android.graphics.Shader.TileMode.CLAMP);
        spectrumFill.setShader(fillGrad);
        canvas.drawPath(spectrumPath, spectrumFill);

        // 上半部顶部描边线
        spectrumStroke.setColor(accentColor);
        spectrumStroke.setAlpha((int)(baseAlpha * 0.9f));
        spectrumStroke.setStrokeWidth(active ? 2.5f : 1.5f);
        spectrumPath.reset();
        spectrumPath.moveTo(0, centerY);
        for (int i = 0; i <= samples + 1; i++) {
            if (i <= 1 || i >= samples) {
                spectrumPath.lineTo(ptsX[i], ptsY[i]);
            } else {
                float px = ptsX[i - 1], py = ptsY[i - 1];
                float cx1 = (px + ptsX[i]) / 2f;
                spectrumPath.cubicTo(cx1, py, cx1, ptsY[i], ptsX[i], ptsY[i]);
            }
        }
        canvas.drawPath(spectrumPath, spectrumStroke);

        // === 下半部：微光填充 ===
        spectrumPath.reset();
        spectrumPath.moveTo(0, centerY);
        for (int i = 0; i <= samples + 1; i++) {
            if (i <= 1 || i >= samples) {
                spectrumPath.lineTo(ptsX[i], ptsYDim[i]);
            } else {
                float px = ptsX[i - 1], py = ptsYDim[i - 1];
                float cx1 = (px + ptsX[i]) / 2f;
                spectrumPath.cubicTo(cx1, py, cx1, ptsYDim[i], ptsX[i], ptsYDim[i]);
            }
        }
        spectrumPath.close();

        android.graphics.LinearGradient fillGradDim = new android.graphics.LinearGradient(
                0, centerY, 0, centerY + maxH,
                new int[]{shiftAlpha(accentColor, 0.08f), shiftAlpha(accentColor, 0f)},
                null, android.graphics.Shader.TileMode.CLAMP);
        spectrumFill.setShader(fillGradDim);
        canvas.drawPath(spectrumPath, spectrumFill);

        // 下半部描边线
        spectrumStroke.setAlpha((int)(baseAlpha * 0.35f));
        spectrumStroke.setStrokeWidth(1.5f);
        spectrumPath.reset();
        spectrumPath.moveTo(0, centerY);
        for (int i = 0; i <= samples + 1; i++) {
            if (i <= 1 || i >= samples) {
                spectrumPath.lineTo(ptsX[i], ptsYDim[i]);
            } else {
                float px = ptsX[i - 1], py = ptsYDim[i - 1];
                float cx1 = (px + ptsX[i]) / 2f;
                spectrumPath.cubicTo(cx1, py, cx1, ptsYDim[i], ptsX[i], ptsYDim[i]);
            }
        }
        canvas.drawPath(spectrumPath, spectrumStroke);

        // === 中心线 ===
        linePaint.setColor(accentColor);
        linePaint.setAlpha(active ? 30 : 15);
        canvas.drawLine(0, centerY, w, centerY, linePaint);
    }

    /** 保留原色的 alpha 通道，整体乘系数 */
    private int shiftAlpha(int color, float factor) {
        int a = (int) ((color >>> 24) * factor);
        if (a < 0) a = 0; if (a > 255) a = 255;
        return (a << 24) | (color & 0xFFFFFF);
    }

    // === 模式 4：跳动圆点 ===
    private void drawDotPulse(Canvas canvas) {
        int w = getWidth(), h = getHeight();
        if (w <= 0 || h <= 0) return;
        int dots = 24;
        float cx = w / 2f, cy = h / 2f;
        float baseR = Math.min(cx, cy) * 0.75f;

        for (int i = 0; i < dots; i++) {
            int bi = (int) (i * BAR_COUNT / (float) dots);
            float lv = bars[bi];
            float angle = (float) (i * 2 * Math.PI / dots + circlePhase * 0.3f);
            float r = baseR * (0.55f + 0.45f * lv);
            float dx = cx + r * (float) Math.cos(angle);
            float dy = cy + r * (float) Math.sin(angle);
            float dotR = 3f + lv * 12f * (0.6f + 0.4f * globalEnergy);

            float hue = baseHue + i * 15f;
            hsv[0] = hue % 360f; hsv[1] = 0.9f; hsv[2] = 1f;
            int col = Color.HSVToColor(hsv);

            // 光晕
            dotPaint.setColor(col);
            dotPaint.setAlpha(active ? 40 : 20);
            canvas.drawCircle(dx, dy, dotR * 1.6f, dotPaint);

            // 核心
            dotPaint.setAlpha(active ? 230 : 120);
            canvas.drawCircle(dx, dy, dotR, dotPaint);
        }
        // 中心点
        hsv[0] = baseHue; hsv[1] = 0.9f; hsv[2] = 1f;
        dotPaint.setColor(Color.HSVToColor(hsv));
        dotPaint.setAlpha(active ? 255 : 140);
        canvas.drawCircle(cx, cy, 3f + globalEnergy * 5f, dotPaint);
    }

    private void drawBar(Canvas canvas, Paint paint, float x, float w, float bh, float centerY) {
        if (bh < 2f) bh = 2f;
        float r = Math.min(w / 2f, bh);
        canvas.drawRoundRect(x, centerY - bh, x + w, centerY + bh, r, r, paint);
    }

    private float dp2px(float dp) {
        return dp * getResources().getDisplayMetrics().density;
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        stop();
    }
}
