package com.carassistant.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Shader;
import android.os.Handler;
import android.os.Looper;
import android.util.AttributeSet;
import android.view.View;

import java.util.Random;

/**
 * 音乐律动频谱条（模拟）。
 *
 * 不依赖麦克风 / 音频捕获权限（避免车机上申请录音权限的弹窗与第三方 App 音频捕获的不确定性），
 * 使用多层正弦波叠加 + 随机相位在播放时产生自然律动，暂停时平滑回落到基线。
 * 律动条颜色跟随界面强调色（动态主题），并在播放状态改变时整体随节奏起伏。
 */
public class MusicVisualizerView extends View {

    private static final int BAR_COUNT = 40;
    private static final long FRAME_MS = 33;          // ~30fps，足够顺滑且省电
    private static final float BASELINE = 0.08f;      // 暂停时的基线高度占比

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private int accentColor = 0xFFEE0A24;             // 默认网易云红
    private boolean active = false;

    private final float[] bars = new float[BAR_COUNT];
    private final float[] phases = new float[BAR_COUNT];
    private final float[] freqs = new float[BAR_COUNT];
    private final Random random = new Random();

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
            phases[i] = random.nextFloat() * (float) Math.PI * 2f;
            // 每条使用不同频率，制造频谱般的高低错落
            freqs[i] = 0.004f + random.nextFloat() * 0.006f;
            bars[i] = BASELINE;
        }
    }

    /** 设置律动条颜色（建议传入界面强调色） */
    public void setAccentColor(int color) {
        this.accentColor = color;
    }

    /** 设置是否处于播放状态：播放时律动，暂停/停止时回落 */
    public void setActive(boolean active) {
        this.active = active;
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
        long t = System.currentTimeMillis();
        // 整体节奏包络（模拟鼓点 / 节拍起伏），周期约 1.5s
        double beat = 0.55 + 0.45 * Math.sin(t * 0.0042);
        for (int i = 0; i < BAR_COUNT; i++) {
            float target;
            if (active) {
                // 多层正弦叠加，制造频谱般的高低错落
                double w1 = Math.sin(t * freqs[i] + phases[i]) * 0.5 + 0.5;
                double w2 = Math.sin(t * (freqs[i] * 2.3f) + phases[i] * 1.7f) * 0.5 + 0.5;
                double energy = (w1 * 0.6 + w2 * 0.4) * beat;
                // 中间条略高、两侧略低，形成山形，更像唱片律动
                double centerFall = 1.0 - Math.abs(i - (BAR_COUNT - 1) / 2.0) / (BAR_COUNT / 2.0) * 0.35;
                target = (float) (BASELINE + energy * (1f - BASELINE) * centerFall);
            } else {
                target = BASELINE;
            }
            // 低通平滑过渡，避免条高跳变产生闪烁
            bars[i] += (target - bars[i]) * 0.3f;
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0) return;

        float gap = 3f;
        float barW = (w - gap * (BAR_COUNT - 1)) / (float) BAR_COUNT;
        if (barW <= 0) return;

        int r = Color.red(accentColor);
        int g = Color.green(accentColor);
        int b = Color.blue(accentColor);
        int alpha = active ? 255 : 120;
        int top = Color.argb((int) (alpha * 0.35), r, g, b);
        int bottom = Color.argb(alpha, r, g, b);
        // 底部亮、顶部渐隐，营造发光感
        LinearGradient grad = new LinearGradient(0, h, 0, 0, bottom, top, Shader.TileMode.CLAMP);
        paint.setShader(grad);

        for (int i = 0; i < BAR_COUNT; i++) {
            float bh = Math.max(2f, bars[i] * h);
            float x = i * (barW + gap);
            float y = h - bh;
            canvas.drawRoundRect(x, y, x + barW, h, barW / 2f, barW / 2f, paint);
        }
        paint.setShader(null);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        stop();
    }
}
