package com.carassistant.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.Handler;
import android.os.Looper;
import android.util.AttributeSet;
import android.view.View;

import java.util.Random;

/**
 * 音乐律动可视化（模拟，无需录音权限）。
 *
 * 以屏幕中线为基线，向上下两侧镜像展开的霓虹声波：
 * - 颜色跟随界面强调色（动态主题取色），并沿条带向两侧做对称色相延展；
 * - 播放时多层正弦叠加 + 节拍脉冲产生自然律动，叠加「辉光层 + 顶端星点 + 峰值缓降帽」；
 * - 暂停时平滑回落为微弱的呼吸涟漪。
 */
public class MusicVisualizerView extends View {

    private static final int BAR_COUNT = 56;
    private static final long FRAME_MS = 33;          // ~30fps，足够顺滑且省电
    private static final float BASELINE = 0.025f;     // 非播放时的基线/呼吸高度占比
    private static final float HUE_SPREAD = 55f;      // 以强调色为中心，向两侧色相延展幅度
    private static final float MID = (BAR_COUNT - 1) / 2f;

    private final Paint corePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint capPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private int accentColor = 0xFF2E6BFF;
    private float baseHue = 210f;
    private boolean active = false;

    private final float[] bars = new float[BAR_COUNT];
    private final float[] peak = new float[BAR_COUNT];
    private final float[] peakVel = new float[BAR_COUNT];
    private float globalEnergy = 0f;
    private float externalLevel = 0.6f;
    private long lastUpdate = 0;

    private final Random random = new Random();
    private final float[] hsv = new float[3];

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
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
        for (int i = 0; i < BAR_COUNT; i++) {
            bars[i] = BASELINE;
            peak[i] = BASELINE;
            peakVel[i] = 0f;
        }
        corePaint.setStyle(Paint.Style.FILL);
        glowPaint.setStyle(Paint.Style.FILL);
        capPaint.setStyle(Paint.Style.FILL);
        capPaint.setColor(Color.WHITE);
        linePaint.setStyle(Paint.Style.FILL);
    }

    /** 设置律动条颜色（建议传入界面强调色） */
    public void setAccentColor(int color) {
        this.accentColor = color;
        float[] tmp = new float[3];
        Color.colorToHSV(color, tmp);
        baseHue = tmp[0];
    }

    /** 设置是否处于播放状态：播放时律动，暂停/停止时回落 */
    public void setActive(boolean active) {
        this.active = active;
    }

    /** 可选：传入 0..1 的整体音量增益，影响律动幅度 */
    public void setLevel(float level) {
        if (level >= 0f && level <= 1f) externalLevel = level;
    }

    /** 开始律动刷新（幂等） */
    public void start() {
        if (!handler.hasCallbacks(tick)) {
            handler.post(tick);
        }
    }

    /** 停止律动刷新 */
    public void stop() {
        handler.removeCallbacks(tick);
    }

    private void update() {
        long now = System.currentTimeMillis();
        float dt = lastUpdate == 0 ? FRAME_MS : Math.min(50, now - lastUpdate);
        lastUpdate = now;
        float t = now / 1000f;

        // 全局节拍脉冲（模拟鼓点 / 能量起伏）
        globalEnergy *= (float) Math.pow(0.93f, dt / FRAME_MS);
        if (active && random.nextDouble() < 0.025 * dt / FRAME_MS) {
            globalEnergy = Math.min(1f, globalEnergy + 0.45f + random.nextFloat() * 0.3f);
        }
        float ampScale = active ? (0.45f + 0.55f * externalLevel) : 1f;

        for (int i = 0; i < BAR_COUNT; i++) {
            float target;
            if (active) {
                // 中间略高、两侧略低，形成山形，更像唱片律动
                float env = 0.5f + 0.5f * (float) Math.sin(Math.PI * i / (BAR_COUNT - 1));
                double f = 2.0 + i * 0.07;
                double w1 = Math.sin(t * f + i * 0.6) * 0.5 + 0.5;
                double w2 = Math.sin(t * f * 1.9 + i * 1.1) * 0.5 + 0.5;
                double w3 = Math.sin(t * f * 0.5 + i * 0.3) * 0.5 + 0.5;
                double wave = w1 * 0.5 + w2 * 0.3 + w3 * 0.2;
                target = (float) (wave * env * ampScale * (1f + globalEnergy * 0.7f));
                target += random.nextFloat() * 0.04f;
                if (target > 1f) target = 1f;
                if (target < 0f) target = 0f;
            } else {
                target = BASELINE + 0.02f * (float) Math.sin(t * 1.1f + i * 0.5f);
            }
            // 低通平滑过渡，避免条高跳变产生闪烁
            bars[i] += (target - bars[i]) * (active ? 0.3f : 0.09f);

            // 峰值缓降（重力下落），营造「卡点」回落的灵动感
            if (bars[i] >= peak[i]) {
                peak[i] = bars[i];
                peakVel[i] = 0f;
            } else {
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
        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0) return;

        float gap = Math.max(2f, w * 0.006f);
        float barW = (w - gap * (BAR_COUNT - 1)) / (float) BAR_COUNT;
        if (barW <= 0) return;

        float centerY = h * 0.5f;
        float halfSpan = h * 0.40f;

        for (int i = 0; i < BAR_COUNT; i++) {
            float lv = bars[i];
            float bh = Math.max(2f, lv * halfSpan);
            float x = i * (barW + gap);
            // 以强调色色相为中心，向两侧对称延展出色相，形成霓虹渐变
            float hue = baseHue + ((i - MID) / MID) * HUE_SPREAD;
            hsv[0] = hue;
            hsv[1] = 0.9f;
            hsv[2] = 1f;
            int col = Color.HSVToColor(hsv);

            // 辉光层（更宽、半透明，制造霓虹光晕）
            glowPaint.setColor(col);
            glowPaint.setAlpha(active ? 70 : 40);
            float gw = barW * 2.1f;
            drawBar(canvas, glowPaint, x - (gw - barW) / 2f, gw, bh * 1.05f, centerY);

            // 核心层
            corePaint.setColor(col);
            corePaint.setAlpha(active ? 255 : 150);
            drawBar(canvas, corePaint, x, barW, bh, centerY);

            // 顶端星点（上下对称）
            capPaint.setAlpha(active ? 210 : 120);
            float cr = barW * 0.6f;
            canvas.drawCircle(x + barW / 2f, centerY - bh, cr, capPaint);
            canvas.drawCircle(x + barW / 2f, centerY + bh, cr, capPaint);

            // 峰值缓降帽（追上峰值时的亮点）
            if (peak[i] > lv + 0.01f) {
                capPaint.setAlpha(255);
                float pr = barW * 0.8f;
                float ph = peak[i] * halfSpan;
                canvas.drawCircle(x + barW / 2f, centerY - ph, pr, capPaint);
                canvas.drawCircle(x + barW / 2f, centerY + ph, pr, capPaint);
            }
        }

        // 中心光带：将上下两片声波在屏幕中线连成一道霓虹地平线
        hsv[0] = baseHue;
        hsv[1] = 0.9f;
        hsv[2] = 1f;
        linePaint.setColor(Color.HSVToColor(hsv));
        linePaint.setAlpha(active ? 55 : 25);
        canvas.drawRect(0, centerY - 1.5f, w, centerY + 1.5f, linePaint);
    }

    private void drawBar(Canvas canvas, Paint paint, float x, float w, float bh, float centerY) {
        if (bh < 2f) bh = 2f;
        float r = Math.min(w / 2f, bh);
        canvas.drawRoundRect(x, centerY - bh, x + w, centerY + bh, r, r, paint);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        stop();
    }
}
