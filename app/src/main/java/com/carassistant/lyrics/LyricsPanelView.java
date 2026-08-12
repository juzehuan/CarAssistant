package com.carassistant.lyrics;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BlurMaskFilter;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.MaskFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.os.Build;
import android.os.SystemClock;
import android.util.LruCache;
import android.view.MotionEvent;
import android.view.View;

import androidx.core.view.ViewCompat;

import com.carassistant.util.MusicController;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * 歌词面板自定义视图（1:1 复刻自歌词伴侣 LyricsPanelView）。
 *
 * 5 种显示风格：
 * 1. default  — 经典三行歌词（上一行/当前行/下一行 + 翻译 + 进度条）
 * 2. refined  — 精致风格（动态调色板背景 + 卡拉 OK + 歌词曲线滚动 + 上下文行）
 * 3. compact  — 紧凑风格（单行跑马灯 + 进度条小柱状图）
 * 4. pip      — 画中画风格（小封面 + 标题 + 单行歌词）
 * 5. custom   — 自定义布局（基于 {@link LyricsLayoutConfig}）
 *
 * 核心能力：
 * - 卡拉 OK 逐字高亮（支持 KRC/QRC/YRC 归一化时间线）
 * - 间奏三点呼吸动画
 * - 歌词曲线滚动（行距中心越远越淡、越小、越弯）
 * - 拖动浏览歌词（弹簧物理 + 投影预测行数）
 * - 调色板提取（从封面采样 6 色，按饱和度排序）
 * - 动态背景（高斯模糊封面 / 纯色 / 渐变 / 流体）
 * - 跑马灯（紧凑风格，文本超长时自动滚动）
 * - 播放控制按钮（上/播放暂停/下，可点击）
 *
 * 数据来源：{@link com.carassistant.util.MusicController} 单例。
 */
public final class LyricsPanelView extends View {

    // ============ 风格配置（替代原项目的 AppPreferences） ============

    public static final class StyleConfig {
        public String overlayStyle = "refined";        // default/refined/compact/pip/custom
        public float textScale = 1.0f;
        public float coverScale = 1.0f;
        public int opacity = 88;                        // 0~100
        public int lyricOffsetMs = 0;
        public int backgroundBlur = 24;
        public int backgroundDim = 38;
        public int lyricLineCount = 3;

        // refined 专属（默认值 1:1 对齐 AppPreferences）
        public String refinedDisplayMode = "all";      // all/lyrics/cover（all=封面+歌词）
        public String refinedColorScheme = "auto";     // auto/light/dark
        public String refinedAccentVariant = "primary";// primary/secondary/tertiary/off
        public String refinedTextEffect = "none";      // none/shadow/glow
        public boolean refinedProgressBottom = true;
        public String refinedCoverHorizontal = "left"; // left/center
        public String refinedCoverVertical = "bottom"; // top/middle/bottom
        public boolean refinedRectangleCover = true;
        public boolean refinedCoverShadow = false;
        public String refinedBackgroundType = "blur";  // none/solid/gradient/fluid/blur
        public boolean refinedStaticFluid = false;
        public boolean refinedDynamicGradient = true;
        public int refinedLyricFontSize = 16;
        public boolean refinedOriginalBold = true;
        public boolean refinedLyricFade = false;
        public boolean refinedLyricZoom = false;
        public boolean refinedLyricBlur = false;
        public boolean refinedLyricRotate = true;
        public int refinedRotateCurvature = 10;
        public String refinedKaraokeAnimation = "float";// float/step/none
        public int refinedCurrentAlign = 50;           // 30/50，当前行纵向锚点百分比
        public boolean refinedShowTranslation = true;
        public boolean refinedLyricGlow = true;

        // compact 专属
        public boolean compactShowCover = true;
        public boolean compactShowBars = true;

        // 播放控制按钮显隐
        public boolean showPreviousButton = true;
        public boolean showPlayPauseButton = true;
        public boolean showNextButton = true;

        // 是否副屏（副屏不显示播放控制按钮）
        public boolean secondary = false;

        // ===== 补齐原版 AppPreferences 缺失项 =====
        public int displayId = -1;                      // 多显示器选择（-1=默认）
        public float panelScale = 1.0f;                 // 面板整体缩放比例
        public String secondaryOverlayStyle = "compact"; // 副悬浮窗独立风格
        public String lyricCatalog = "auto";            // 歌词源（auto/netease/qqmusic/kugou/kuwo/soda）
        public boolean playerCatalogFallback = true;    // 播放器源回退开关
        public String customFontFile = "";              // 自定义字体文件路径
        public boolean diagnosticUploadEnabled = false;  // 诊断上传开关
        public boolean mainOverlayEnabled = false;      // 主悬浮窗开关
        public int compactPanelWidthDp = 320;           // 紧凑风格独立宽度
        public int compactPanelHeightDp = 80;           // 紧凑风格独立高度
        // 副悬浮窗独立位置（与主悬浮窗分开存储）
        public int secondaryX = Integer.MIN_VALUE;
        public int secondaryY = Integer.MIN_VALUE;
    }

    /** 播放控制按钮点击监听 */
    public interface OnControlClickListener {
        void onPrevious();
        void onTogglePlayPause();
        void onNext();
    }

    // ============ 内部状态 ============

    private final StyleConfig style;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Rect sourceRect = new Rect();
    private final RectF panelRect = new RectF();
    private final RectF workRect = new RectF();
    private final RectF progressRect = new RectF();
    private final RectF coverRect = new RectF();
    private final RectF shadowRect = new RectF();
    private final Path clipPath = new Path();

    private final LruCache<TextLayoutKey, List<WrappedChunk>> wrappedTextCache = new LruCache<>(96);
    private final LruCache<TextLayoutKey, String> ellipsizedTextCache = new LruCache<>(96);

    private float[] refinedLineHeights = new float[8];
    private float[] refinedLineTops = new float[8];

    private int[] palette = {-10323574, -13416100, -7705230, -14931661, -6574408, -11310473};
    private Bitmap paletteSource;
    private Bitmap blurSource;
    private Bitmap blurPreview;

    private long lastRenderedLineStartMs = Long.MIN_VALUE;
    private long lyricScrollAnimationStartedMs;
    private int lyricScrollDirection;

    // 拖动浏览歌词
    private boolean browsingLyrics;
    private boolean browseMoved;
    private float browseLastY;
    private long browseLastEventTimeMs;
    private float browseTravelPx;
    private float browseVelocityPxPerSecond;
    private float browseVisualOffsetPx;
    private long browsePositionMs;
    private long browseUntilElapsedMs;
    private boolean browseSettling;
    private long browseSettleLastFrameMs;
    private float lastRefinedBrowseStepPx;

    // compact 跑马灯
    private boolean compactMarqueeActive;
    private long compactMarqueeElapsedMs;
    private long compactMarqueeLastFrameMs;
    private String compactMarqueeText = "";

    private final LyricsLayoutConfig layoutConfig = new LyricsLayoutConfig();
    private OnControlClickListener controlListener;

    private static final Typeface SANS_NORMAL = Typeface.create("sans", Typeface.NORMAL);
    private static final Typeface SANS_BOLD = Typeface.create("sans", Typeface.BOLD);

    public LyricsPanelView(Context context) {
        this(context, new StyleConfig());
    }

    public LyricsPanelView(Context context, StyleConfig style) {
        super(context);
        this.style = style == null ? new StyleConfig() : style;
        setLayerType((usesRefinedVisualStyle() && this.style.refinedLyricBlur)
                ? LAYER_TYPE_SOFTWARE : LAYER_TYPE_HARDWARE, null);
    }

    public void setControlListener(OnControlClickListener listener) {
        this.controlListener = listener;
    }

    /** 将新配置的字段覆盖到当前 style（用于设置页实时预览） */
    public void updateStyle(StyleConfig newStyle) {
        if (newStyle == null) return;
        style.overlayStyle = newStyle.overlayStyle;
        style.textScale = newStyle.textScale;
        style.coverScale = newStyle.coverScale;
        style.opacity = newStyle.opacity;
        style.lyricOffsetMs = newStyle.lyricOffsetMs;
        style.backgroundBlur = newStyle.backgroundBlur;
        style.backgroundDim = newStyle.backgroundDim;
        style.lyricLineCount = newStyle.lyricLineCount;
        style.refinedDisplayMode = newStyle.refinedDisplayMode;
        style.refinedColorScheme = newStyle.refinedColorScheme;
        style.refinedAccentVariant = newStyle.refinedAccentVariant;
        style.refinedTextEffect = newStyle.refinedTextEffect;
        style.refinedProgressBottom = newStyle.refinedProgressBottom;
        style.refinedCoverHorizontal = newStyle.refinedCoverHorizontal;
        style.refinedCoverVertical = newStyle.refinedCoverVertical;
        style.refinedRectangleCover = newStyle.refinedRectangleCover;
        style.refinedCoverShadow = newStyle.refinedCoverShadow;
        style.refinedBackgroundType = newStyle.refinedBackgroundType;
        style.refinedStaticFluid = newStyle.refinedStaticFluid;
        style.refinedDynamicGradient = newStyle.refinedDynamicGradient;
        style.refinedLyricFontSize = newStyle.refinedLyricFontSize;
        style.refinedOriginalBold = newStyle.refinedOriginalBold;
        style.refinedLyricFade = newStyle.refinedLyricFade;
        style.refinedLyricZoom = newStyle.refinedLyricZoom;
        style.refinedLyricBlur = newStyle.refinedLyricBlur;
        style.refinedLyricRotate = newStyle.refinedLyricRotate;
        style.refinedRotateCurvature = newStyle.refinedRotateCurvature;
        style.refinedKaraokeAnimation = newStyle.refinedKaraokeAnimation;
        style.refinedCurrentAlign = newStyle.refinedCurrentAlign;
        style.refinedShowTranslation = newStyle.refinedShowTranslation;
        style.refinedLyricGlow = newStyle.refinedLyricGlow;
        style.compactShowCover = newStyle.compactShowCover;
        style.compactShowBars = newStyle.compactShowBars;
        style.showPreviousButton = newStyle.showPreviousButton;
        style.showPlayPauseButton = newStyle.showPlayPauseButton;
        style.showNextButton = newStyle.showNextButton;
        style.secondary = newStyle.secondary;
        // 同步补齐的原版缺失项
        style.displayId = newStyle.displayId;
        style.panelScale = newStyle.panelScale;
        style.secondaryOverlayStyle = newStyle.secondaryOverlayStyle;
        style.lyricCatalog = newStyle.lyricCatalog;
        style.playerCatalogFallback = newStyle.playerCatalogFallback;
        style.customFontFile = newStyle.customFontFile;
        style.diagnosticUploadEnabled = newStyle.diagnosticUploadEnabled;
        style.mainOverlayEnabled = newStyle.mainOverlayEnabled;
        style.compactPanelWidthDp = newStyle.compactPanelWidthDp;
        style.compactPanelHeightDp = newStyle.compactPanelHeightDp;
        style.secondaryX = newStyle.secondaryX;
        style.secondaryY = newStyle.secondaryY;
        reloadStyle();
    }

    /** 强制刷新样式（用户切换风格后调用） */
    public void reloadStyle() {
        setLayerType((usesRefinedVisualStyle() && style.refinedLyricBlur)
                ? LAYER_TYPE_SOFTWARE : LAYER_TYPE_HARDWARE, null);
        if (blurPreview != null && blurPreview != blurSource && !blurPreview.isRecycled()) {
            blurPreview.recycle();
        }
        blurPreview = null;
        blurSource = null;
        clearTextCaches();
        invalidate();
    }

    // ============ 绘制入口 ============

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float density = getResources().getDisplayMetrics().density;
        float width = getWidth();
        float height = getHeight();
        if (width <= 2.0f || height <= 2.0f) return;

        panelRect.set(1.0f, 1.0f, width - 1.0f, height - 1.0f);
        long now = SystemClock.elapsedRealtime();
        updateBrowseSpring(now);

        boolean browsing = browsingLyrics;
        if (!browsing && browseUntilElapsedMs > 0 && now >= browseUntilElapsedMs) {
            browseUntilElapsedMs = 0L;
            browseSettling = false;
            browseVisualOffsetPx = 0.0f;
            browseVelocityPxPerSecond = 0.0f;
        }

        MusicSnapshot snapshot;
        if (browsing || browseUntilElapsedMs > now) {
            snapshot = MusicController.getInstance().snapshotForLyricBrowse(
                    style.lyricOffsetMs, browsePositionMs);
        } else {
            snapshot = MusicController.getInstance().snapshot(style.lyricOffsetMs);
        }

        String s = style.overlayStyle;
        if ("refined".equals(s)) {
            drawRefined(canvas, snapshot, density);
        } else if ("compact".equals(s)) {
            drawCompact(canvas, snapshot, density);
        } else if ("pip".equals(s)) {
            drawPip(canvas, snapshot, density);
        } else if ("custom".equals(s)) {
            drawCustom(canvas, snapshot, density);
        } else {
            drawDefault(canvas, snapshot, density);
        }

        if (browsing || browseUntilElapsedMs > now) {
            drawBrowseIndicator(canvas, snapshot, density);
        }
        if (!style.secondary) {
            drawPlaybackControls(canvas, snapshot, density);
        }
        postInvalidateDelayed(nextFrameDelay(snapshot, now));
    }

    private long nextFrameDelay(MusicSnapshot snapshot, long now) {
        if (browsingLyrics || browseSettling) return 16L;
        if (compactMarqueeActive && snapshot.playing) return 16L;
        if (lyricScrollAnimationStartedMs > 0 && now - lyricScrollAnimationStartedMs < 500) return 16L;
        if (browseUntilElapsedMs > now) return Math.max(16L, Math.min(250L, browseUntilElapsedMs - now));
        if (!snapshot.active) return 750L;
        if (!snapshot.playing) return 400L;
        if (snapshot.lyrics.interlude) return 33L;
        if (snapshot.lyrics.wordTimed && snapshot.lyrics.wordDurationMs > 0
                && !snapshot.lyrics.currentWord.isEmpty()) {
            int total = snapshot.lyrics.currentWord.codePointCount(0, snapshot.lyrics.currentWord.length());
            int revealed = LrcTimeline.revealedCodePointCount(snapshot.lyrics.currentWord, snapshot.lyrics.wordProgressPermille);
            if (total > 0 && revealed < total) {
                long nextBoundary = snapshot.lyrics.wordStartMs
                        + (((snapshot.lyrics.wordDurationMs * (revealed + 1)) + total - 1) / total);
                long delay = nextBoundary - (snapshot.positionMs + style.lyricOffsetMs);
                return Math.max(16L, Math.min(100L, delay));
            }
        }
        return 100L;
    }

    // ============ 触摸：拖动浏览 + 播放控制按钮点击 ============

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN) {
            // 优先判定播放控制按钮点击
            MediaControlAction ctrl = playbackControlAt(event.getX(), event.getY());
            if (ctrl != null) {
                dispatchControlClick(ctrl);
                performClick();
                return true;
            }
            MusicSnapshot snap = MusicController.getInstance().snapshot(style.lyricOffsetMs);
            if (!snap.lyricAvailable || !isLyricGestureRegion(event.getX(), event.getY())) {
                return false;
            }
            long now = SystemClock.elapsedRealtime();
            updateBrowseSpring(now);
            browseSettling = false;
            browsePositionMs = LyricsBrowseState.startingPosition(now, browseUntilElapsedMs,
                    browsePositionMs, snap.positionMs + style.lyricOffsetMs);
            browsingLyrics = true;
            browseMoved = false;
            browseTravelPx = 0.0f;
            browseLastY = event.getY();
            browseLastEventTimeMs = event.getEventTime();
            browseVelocityPxPerSecond = 0.0f;
            browseUntilElapsedMs = 0L;
            if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(true);
            invalidate();
            return true;
        }
        if (action != MotionEvent.ACTION_UP) {
            if (action == MotionEvent.ACTION_MOVE) {
                if (!browsingLyrics) return false;
                float dy = event.getY() - browseLastY;
                long t = event.getEventTime();
                browseVelocityPxPerSecond = (browseVelocityPxPerSecond * 0.28f)
                        + (((1000.0f * dy) / Math.max(1L, t - browseLastEventTimeMs)) * 0.72f);
                browseLastY = event.getY();
                browseLastEventTimeMs = t;
                browseTravelPx += Math.abs(dy);
                browseMoved = browseTravelPx >= getResources().getDisplayMetrics().density * 6.0f;
                browseVisualOffsetPx += dy;
                consumeBrowseSteps(browseStepPx());
                invalidate();
                return true;
            }
            if (action != MotionEvent.ACTION_CANCEL) return super.onTouchEvent(event);
        }
        if (!browsingLyrics) return false;
        browsingLyrics = false;
        long now = SystemClock.elapsedRealtime();
        if (event.getActionMasked() == MotionEvent.ACTION_UP) {
            projectBrowseRelease(browseStepPx());
        } else {
            browseVelocityPxPerSecond = 0.0f;
        }
        browseUntilElapsedMs = 2500L + now;
        if (animationsEnabled() && (Math.abs(browseVisualOffsetPx) > 0.35f
                || Math.abs(browseVelocityPxPerSecond) > 4.0f)) {
            browseSettling = true;
            browseSettleLastFrameMs = now;
        } else {
            browseSettling = false;
            browseVisualOffsetPx = 0.0f;
            browseVelocityPxPerSecond = 0.0f;
        }
        if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(false);
        invalidate();
        if (!browseMoved && event.getActionMasked() == MotionEvent.ACTION_UP) {
            performClick();
        }
        return true;
    }

    private void dispatchControlClick(MediaControlAction action) {
        if (controlListener == null) return;
        switch (action) {
            case PREVIOUS: controlListener.onPrevious(); break;
            case TOGGLE_PLAY_PAUSE: controlListener.onTogglePlayPause(); break;
            case NEXT: controlListener.onNext(); break;
        }
    }

    @Override
    public boolean performClick() {
        super.performClick();
        return true;
    }

    public boolean isLyricGestureRegion(float x, float y) {
        if (getWidth() <= 0 || getHeight() <= 0) return false;
        if (playbackControlAt(x, y) != null) return false;
        if (!MusicController.getInstance().snapshot(style.lyricOffsetMs).lyricAvailable) return false;
        if ("refined".equals(style.overlayStyle)) {
            String mode = style.refinedDisplayMode == null ? "all" : style.refinedDisplayMode;
            if ("cover".equals(mode)) return false;
            // 仅歌词模式或点击位置落在歌词区（封面区域右侧）才视为歌词手势
            if ("lyrics".equals(mode)) return true;
            float dd = getResources().getDisplayMetrics().density;
            float pad = Math.max(12.0f * dd, 0.035f * getWidth());
            float coverSize = Math.max(46.0f * dd,
                    Math.min(Math.min(0.30f * getWidth(), 0.46f * getHeight()) * style.coverScale, 0.54f * getHeight()));
            return x >= (pad + coverSize + pad);
        }
        if ("compact".equals(style.overlayStyle)) return false;
        if ("pip".equals(style.overlayStyle)) return y >= getHeight() * 0.34f;
        if ("custom".equals(style.overlayStyle)) return y >= getHeight() * 0.28f;
        return y >= getHeight() * 0.24f && y <= getHeight() * 0.86f;
    }

    public MediaControlAction playbackControlAt(float x, float y) {
        if (style.secondary || getWidth() <= 0 || getHeight() <= 0) return null;
        PlaybackControlLayout l = playbackControlLayout(getResources().getDisplayMetrics().density);
        if (style.showPreviousButton && insideCircle(x, y, l.centerX - l.spacing, l.centerY, l.radius)) {
            return MediaControlAction.PREVIOUS;
        }
        if (style.showPlayPauseButton && insideCircle(x, y, l.centerX, l.centerY, l.radius * 1.12f)) {
            return MediaControlAction.TOGGLE_PLAY_PAUSE;
        }
        if (style.showNextButton && insideCircle(x, y, l.centerX + l.spacing, l.centerY, l.radius)) {
            return MediaControlAction.NEXT;
        }
        return null;
    }

    // ============ 风格 1: default ============

    private void drawDefault(Canvas canvas, MusicSnapshot snap, float d) {
        String sourceSuffix;
        float padX = d * 18.0f;
        float width = getWidth();
        float height = getHeight();
        if (style.opacity > 0) {
            drawPanelShadow(canvas, d * 24.0f, Color.argb((int) (style.opacity * 2.55f), 6, 15, 27));
            paint.setColor(withAlpha(0xFF404654, Math.round((Color.alpha(0xFF404654) * style.opacity) / 100.0f)));
            workRect.set(padX, 10.0f * d, width - padX, 13.0f * d);
            float r = d * 2.0f;
            canvas.drawRoundRect(workRect, r, r, paint);
        }
        float maxW = Math.max(1.0f, width - padX * 2.0f);
        float offset = browseVisualOffsetPx;
        float y = d * 33.0f;
        float secondaryScale = style.secondary ? 1.12f : 1.0f;
        sourceSuffix = snap.lyricSourceName.isEmpty() ? "" : "  ·  歌词/" + snap.lyricSourceName;
        String info;
        if (snap.active) {
            info = snap.sourceName + (snap.playing ? "  ·  播放中" : "  ·  已暂停") + sourceSuffix;
        } else {
            info = "车机助手  ·  等待音乐";
        }
        drawCentered(canvas, info, y, 11.0f * d * style.textScale * secondaryScale,
                snap.playing ? 0xFF6F6F62 : 0xFF837F73, maxW, 1);
        float titleSize = d * 24.0f * secondaryScale;
        float y2 = y + titleSize;
        drawCentered(canvas, snap.active ? snap.title : "打开音乐播放器并开始播放",
                y2, 15.0f * d * style.textScale * secondaryScale, 0xFFF7071F, maxW, 1);
        float yPrev = y2 + (27.0f * d * secondaryScale);
        float transSize = d * 12.0f;
        drawCentered(canvas, snap.lyrics.previousLyric, yPrev + offset,
                style.textScale * transSize * secondaryScale, 0xFF676B74, maxW, 0);
        float yCur = yPrev + (32.0f * d * secondaryScale);
        if (snap.lyrics.interlude) {
            float dotR = d * 22.0f * style.textScale * secondaryScale * 0.35f;
            drawInterludeDots(canvas, snap, (width / 2.0f) - (interludeDotsWidth(dotR) / 2.0f),
                    (yCur + offset) - dotR, dotR, 0xFFFFCA96);
        } else {
            drawKaraoke(canvas, snap, currentText(snap), width / 2.0f, yCur + offset,
                    d * 22.0f * style.textScale * secondaryScale, maxW, Paint.Align.CENTER,
                    0xFFB1C1EB, 0xFFFFCA96);
        }
        if (!snap.lyrics.translatedLyric.isEmpty()) {
            drawCentered(canvas, snap.lyrics.translatedLyric, yCur + titleSize + offset,
                    style.textScale * transSize * secondaryScale, 0xFFB8B8B8, maxW, 0);
        }
        drawCentered(canvas, snap.lyrics.nextLyric,
                ((height - (37.0f * d)) - (style.secondary ? 0.0f : 31.0f * d)) + offset,
                transSize * style.textScale * secondaryScale, 0xFF676B74, maxW, 0);
        drawProgress(canvas, padX, height - (17.0f * d), width - padX, d * 3.0f,
                snap, 0xFF353B47, 0xFFFFCA96);
    }

    // ============ 风格 2: refined ============

    private void drawRefined(Canvas canvas, MusicSnapshot snap, float d) {
        float width = getWidth();
        float height = getHeight();
        updatePalette(snap.albumArt);
        boolean light = refinedUsesLightColors();
        int accent = refinedAccentColor();
        int primaryText = light ? mix(accent, ViewCompat.MEASURED_STATE_MASK, 0.72f)
                : mix(accent, 0xFFFFFFFF, 0.78f);
        int secondaryText = withAlpha(primaryText, 150);
        drawRefinedBackground(canvas, snap.albumArt, light, accent, snap.playing);
        int save = canvas.save();
        clipPath.reset();
        float radius = Math.min(width, height) * 0.075f;
        clipPath.addRoundRect(panelRect, radius, radius, Path.Direction.CW);
        canvas.clipPath(clipPath);

        float pad = Math.max(12.0f * d, 0.035f * width);
        // 显示模式：all/both=封面+歌词；lyrics=仅歌词；cover=仅封面（与 AppPreferences 一致）
        String mode = style.refinedDisplayMode == null ? "all" : style.refinedDisplayMode;
        boolean lyricsOnly = "lyrics".equals(mode);
        boolean coverOnly = "cover".equals(mode);

        // 封面区域宽度固定 45% 宽度（1:1 对齐原版 drawRefined）；
        // 歌词区从 max(50% 宽度, 封面区+pad) 开始，确保左半封面、右半歌词的稳定布局
        float coverAreaW = lyricsOnly ? 0.0f : 0.45f * width;
        float lyricsX = lyricsOnly ? pad : Math.max(0.5f * width, coverAreaW + pad);
        float lyricsW = Math.max(1.0f, (width - lyricsX) - pad);

        // 1:1 对齐原版顺序：先画封面信息，后画歌词
        if (!lyricsOnly) {
            drawRefinedSongInfo(canvas, snap, d, coverAreaW, pad, primaryText, secondaryText, accent);
        }
        if (!coverOnly) {
            drawRefinedLyrics(canvas, snap, d, lyricsX, lyricsW, primaryText, secondaryText);
        }
        drawProgress(canvas, pad, height - (style.refinedProgressBottom ? d * 2.0f : 10.0f * d),
                width - pad, d * 2.0f, snap, withAlpha(primaryText, 48), withAlpha(primaryText, 225));
        canvas.restoreToCount(save);
    }

    private void drawRefinedSongInfo(Canvas canvas, MusicSnapshot snap, float d,
                                      float coverAreaW, float pad, int primary, int secondary, int accent) {
        float height = getHeight();
        // 封面尺寸从 coverAreaW 派生
        // 原版系数 0.56/0.46/0.54 在大屏车机上导致封面远小于封面区域，间隔过大
        // 增大系数让封面更充分地填充封面区域
        float coverSize = Math.max(46.0f * d,
                Math.min(Math.min(0.80f * coverAreaW, 0.68f * height) * style.coverScale, 0.75f * height));
        float titleSize = Math.max(16.0f * d, Math.min(34.0f * d, 0.075f * height)) * style.textScale;
        float subSize = Math.max(9.0f * d, 0.42f * titleSize);
        float gap = d * 18.0f;
        float blockH = coverSize + gap + titleSize + (3.2f * subSize);
        float top;
        if ("middle".equals(style.refinedCoverVertical)) {
            top = Math.max(pad, (height - blockH) / 2.0f);
        } else {
            // bottom（默认）/ top：底部对齐，预留进度条空间
            top = Math.max(pad, ((height - pad) - (8.0f * d)) - blockH);
        }
        // 封面水平位置：居左从 pad 开始，居中在 coverAreaW 内居中
        float left = "center".equals(style.refinedCoverHorizontal)
                ? Math.max(pad, (coverAreaW - coverSize) / 2.0f) : pad;
        coverRect.set(left, top, left + coverSize, top + coverSize);
        float corner = style.refinedRectangleCover ? (16.0f * d) : (coverSize / 2.0f);

        if (style.refinedCoverShadow && snap.albumArt != null && !snap.albumArt.isRecycled()) {
            float shadowExtend = 0.06f * coverSize;
            shadowRect.set(coverRect.left - shadowExtend, coverRect.top + (0.02f * coverSize),
                    coverRect.right + shadowExtend, coverRect.bottom + (coverSize * 0.11f));
            int sv = canvas.save();
            clipPath.reset();
            clipPath.addRoundRect(shadowRect, corner, corner, Path.Direction.CW);
            canvas.clipPath(clipPath);
            drawBitmapCrop(canvas, blurredPreview(snap.albumArt), shadowRect, 145);
            canvas.restoreToCount(sv);
        }
        drawCover(canvas, snap.albumArt, coverRect, corner, mix(accent, 0xFF434343, 0.55f));

        // 文字宽度对齐封面区域（coverAreaW），使标题/歌手/来源能完整显示
        float textX = pad;
        float textW = Math.max(1.0f, coverAreaW - pad - pad);
        Paint.Align align = Paint.Align.LEFT;
        if ("center".equals(style.refinedCoverHorizontal)) {
            textX = coverAreaW / 2.0f;
            align = Paint.Align.CENTER;
        }
        float yTitle = coverRect.bottom + gap + titleSize;
        drawRefinedText(canvas, snap.active ? snap.title : "等待音乐", textX, yTitle,
                titleSize, primary, textW, align, 0, 255);
        float yArtist = yTitle + (1.55f * subSize);
        drawRefinedText(canvas, snap.artist, textX, yArtist, subSize, secondary, textW, align, 0, 205);
        drawRefinedText(canvas, snap.sourceName + sourceSuffix(snap), textX, yArtist + (1.38f * subSize),
                subSize * 0.88f, secondary, textW, align, 0, 145);
    }

    private void drawRefinedLyrics(Canvas canvas, MusicSnapshot snap, float d,
                                    float x, float w, int primary, int secondary) {
        float fontSize = style.refinedLyricFontSize * d * style.textScale * (style.secondary ? 1.03f : 1.0f);
        float transSize = fontSize * 0.62f;
        float centerY = getHeight() * (style.refinedCurrentAlign / 100.0f);

        if (snap.lyrics.nearbyLines.isEmpty()) {
            drawWrappedKaraoke(canvas, snap, currentText(snap), x, (centerY - fontSize) + browseVisualOffsetPx,
                    fontSize, w, primary, 3);
            return;
        }
        List<LrcTimeline.NearbyLine> lines = snap.lyrics.nearbyLines;
        ensureRefinedLineCapacity(lines.size());
        float[] heights = refinedLineHeights;
        int currentIdx = 0;
        for (int i = 0; i < lines.size(); i++) {
            LrcTimeline.NearbyLine line = lines.get(i);
            if (line.offset == 0) currentIdx = i;
            if (line.interlude) {
                heights[i] = 1.75f * fontSize;
            } else {
                heights[i] = wrappedTextHeight(line.text, fontSize, w, 3);
                if (style.refinedShowTranslation && !line.translated.isEmpty()) {
                    heights[i] += (0.18f * fontSize) + wrappedTextHeight(line.translated, transSize, w, 2);
                }
            }
        }
        float gap = 0.52f * fontSize;
        lastRefinedBrowseStepPx = Math.max(1.0f, heights[currentIdx] + gap);
        float[] tops = refinedLineTops;
        tops[currentIdx] = centerY - Math.min(fontSize, heights[currentIdx] * 0.45f);
        for (int i = currentIdx + 1; i < lines.size(); i++) {
            tops[i] = tops[i - 1] + heights[i - 1] + gap;
        }
        for (int i = currentIdx - 1; i >= 0; i--) {
            tops[i] = (tops[i + 1] - heights[i]) - gap;
        }
        float scrollShift = animatedLyricScrollShift(snap.lyrics.lineStartMs, heights[currentIdx] + gap)
                + browseVisualOffsetPx;

        for (int i = 0; i < lines.size(); i++) {
            LrcTimeline.NearbyLine line = lines.get(i);
            int offset = line.offset;
            if (Math.abs(offset) > 3) continue;
            float y = tops[i] + scrollShift;
            RefinedLyricCurve.Transform transform;
            if (style.refinedLyricRotate) {
                transform = RefinedLyricCurve.calculate(tops[currentIdx] - y, heights[i], getHeight(), d, style.refinedRotateCurvature);
            } else {
                transform = RefinedLyricCurve.Transform.IDENTITY;
            }
            // 歌词左对齐：所有行从歌词区域左边 x 开始，不应用曲线水平位移
            float cx = x;
            float cy = y + transform.translationY;
            float centerYLine = cy + (heights[i] / 2.0f);
            float scale = style.refinedLyricZoom ? refinedScaleForOffset(offset) : 1.0f;
            float alpha = (offset == 0) ? 1.0f : 0.4f;
            if (style.refinedLyricFade && Math.abs(offset) > 1) {
                alpha *= Math.max(0.0f, 1.0f - ((Math.abs(offset) - 1) * 0.4f));
            }
            alpha *= clamp(Math.min(centerYLine / Math.max(1.0f, getHeight()),
                    (getHeight() - centerYLine) / Math.max(1.0f, getHeight())) * 8.0f) * transform.opacity;
            if (alpha <= 0.01f) continue;
            int save = canvas.save();
            // 歌词左对齐：不应用旋转，保持文字水平
            canvas.scale(scale, scale, cx, centerYLine);
            if (style.refinedLyricBlur && offset != 0) {
                paint.setMaskFilter(new BlurMaskFilter(
                        Math.min(4.5f * d, (Math.abs(offset) + 0.5f) * d), BlurMaskFilter.Blur.NORMAL));
            }
            if (line.interlude) {
                drawInterludeDots(canvas, snap, cx, cy + (0.3f * fontSize),
                        fontSize * 0.35f, withAlpha(primary, Math.round(225.0f * alpha)));
            } else if (offset == 0) {
                drawWrappedKaraoke(canvas, snap, currentText(snap), cx, cy, fontSize, w, primary, 3);
            } else {
                drawWrappedText(canvas, line.text, cx, cy, fontSize,
                        withAlpha(secondary, Math.round(255.0f * alpha)), w,
                        style.refinedOriginalBold ? 1 : 0, 3);
            }
            if (!line.interlude && style.refinedShowTranslation && !line.translated.isEmpty()) {
                float yTrans = cy + wrappedTextHeight(line.text, fontSize, w, 3) + (fontSize * 0.18f);
                drawWrappedText(canvas, line.translated, cx, yTrans, transSize,
                        withAlpha(secondary, Math.round(alpha * (offset == 0 ? 205.0f : 180.0f))),
                        w, 0, 2);
            }
            paint.setMaskFilter(null);
            canvas.restoreToCount(save);
        }
    }

    private void ensureRefinedLineCapacity(int n) {
        if (refinedLineHeights.length >= n) return;
        int cap = Math.max(n, refinedLineHeights.length * 2);
        refinedLineHeights = new float[cap];
        refinedLineTops = new float[cap];
    }

    private float animatedLyricScrollShift(long lineStartMs, float stepPx) {
        if (lineStartMs < 0) return 0.0f;
        if (manualPreviewActive()) {
            lastRenderedLineStartMs = lineStartMs;
            lyricScrollAnimationStartedMs = 0L;
            lyricScrollDirection = 0;
            return 0.0f;
        }
        if (lastRenderedLineStartMs == Long.MIN_VALUE) {
            lastRenderedLineStartMs = lineStartMs;
            return 0.0f;
        }
        if (lineStartMs != lastRenderedLineStartMs) {
            lyricScrollDirection = lineStartMs > lastRenderedLineStartMs ? 1 : -1;
            lastRenderedLineStartMs = lineStartMs;
            lyricScrollAnimationStartedMs = SystemClock.elapsedRealtime();
        }
        float progress = clamp(((float) (SystemClock.elapsedRealtime() - lyricScrollAnimationStartedMs)) / 500.0f);
        return lyricScrollDirection * stepPx * (1.0f - (1.0f - (float) Math.pow(1.0f - progress, 3.0)));
    }

    private float refinedScaleForOffset(int offset) {
        float base = Math.max(1.0f - (Math.abs(offset) * 0.2f), 0.0f);
        return (base * base * base * 0.3f) + 0.7f;
    }

    private void drawRefinedText(Canvas canvas, String text, float x, float y, float size,
                                  int color, float maxW, Paint.Align align, int bold, int alpha) {
        if (text == null || text.isEmpty() || alpha <= 0) return;
        MaskFilter mf = paint.getMaskFilter();
        float fit = fitSize(text, size, maxW, bold);
        setTextPaint(fit, bold);
        paint.setMaskFilter(mf);
        paint.setTextAlign(align);
        paint.setColor(withAlpha(color, alpha));
        applyRefinedTextEffect(fit, color, alpha);
        canvas.drawText(ellipsize(text.replace('\n', ' '), maxW), x, y, paint);
        paint.clearShadowLayer();
    }

    private void applyRefinedTextEffect(float size, int color, int alpha) {
        if ("shadow".equals(style.refinedTextEffect)) {
            paint.setShadowLayer(Math.max(2.0f, 0.16f * size), 0.0f, size * 0.1f,
                    Color.argb(Math.min(115, alpha), 0, 0, 0));
        } else if ("glow".equals(style.refinedTextEffect)) {
            paint.setShadowLayer(Math.max(3.0f, size * 0.24f), 0.0f, 0.0f,
                    withAlpha(color, Math.min(95, alpha)));
        }
    }

    private void drawRefinedBackground(Canvas canvas, Bitmap bmp, boolean light, int accent, boolean playing) {
        String type = style.refinedBackgroundType == null ? "blur" : style.refinedBackgroundType;
        if ("none".equals(type)) return;
        int sl = saveLayerAlphaCompat(canvas, panelRect, Math.round(clamp(style.opacity / 100.0f) * 255.0f));
        int save = canvas.save();
        float radius = Math.min(getWidth(), getHeight()) * 0.075f;
        clipPath.reset();
        clipPath.addRoundRect(panelRect, radius, radius, Path.Direction.CW);
        canvas.clipPath(clipPath);
        paint.setStyle(Paint.Style.FILL);
        paint.setAlpha(255);
        paint.setShader(null);

        if ("solid".equals(type)) {
            paint.setColor(mix(accent, light ? 0xFFFFFFFF : ViewCompat.MEASURED_STATE_MASK, light ? 0.78f : 0.72f));
            canvas.drawRect(panelRect, paint);
        } else if ("gradient".equals(type)) {
            float t = (style.refinedDynamicGradient && playing)
                    ? ((float) (SystemClock.elapsedRealtime() % 120000)) / 120000.0f : 0.125f;
            paint.setShader(new LinearGradient(0.0f, getHeight(),
                    getWidth() * ((0.8f * t) + 0.2f), getHeight() * (1.0f - (t * 0.6f)),
                    palette, null, Shader.TileMode.CLAMP));
            canvas.drawRect(panelRect, paint);
        } else if ("fluid".equals(type)) {
            paint.setColor(mix(accent, light ? 0xFFFFFFFF : ViewCompat.MEASURED_STATE_MASK, 0.62f));
            canvas.drawRect(panelRect, paint);
            if (bmp != null && !bmp.isRecycled()) {
                drawBitmapCrop(canvas, blurredPreview(bmp), panelRect, 105);
            }
            float t = (style.refinedStaticFluid || !playing)
                    ? 0.23f : ((float) (SystemClock.elapsedRealtime() % 150000)) / 150000.0f;
            float maxR = Math.max(getWidth(), getHeight()) * 0.72f;
            for (int i = 0; i < 4; i++) {
                double angle = (t * Math.PI * 2.0) + ((i * Math.PI) / 2.0);
                paint.setShader(new RadialGradient(
                        (getWidth() * 0.5f) + ((float) Math.cos(angle) * getWidth() * 0.33f),
                        (getHeight() * 0.5f) + ((float) Math.sin(angle) * getHeight() * 0.28f),
                        maxR, withAlpha(palette[i], 205), withAlpha(palette[i], 0),
                        Shader.TileMode.CLAMP));
                canvas.drawRect(panelRect, paint);
            }
        } else if (bmp != null && !bmp.isRecycled()) {
            drawBitmapCrop(canvas, blurredPreview(bmp), panelRect, 255);
        } else {
            paint.setShader(new LinearGradient(0.0f, 0.0f, getWidth(), getHeight(),
                    palette[0], palette[3], Shader.TileMode.CLAMP));
            canvas.drawRect(panelRect, paint);
        }
        paint.setShader(null);
        int dim = Math.round(clamp(style.backgroundDim / 100.0f) * 255.0f);
        paint.setColor(light ? Color.argb(dim, 255, 255, 255) : Color.argb(dim, 0, 0, 0));
        canvas.drawRect(panelRect, paint);
        canvas.restoreToCount(save);
        canvas.restoreToCount(sl);
        paint.setShader(null);
        paint.setAlpha(255);
    }

    private boolean refinedUsesLightColors() {
        if ("light".equals(style.refinedColorScheme)) return true;
        if ("dark".equals(style.refinedColorScheme)) return false;
        return (getResources().getConfiguration().uiMode & 0x30) != 0x20;
    }

    private int refinedAccentColor() {
        if ("off".equals(style.refinedAccentVariant)) return 0xFF96A7C6;
        if ("secondary".equals(style.refinedAccentVariant)) return mix(palette[0], palette[1], 0.5f);
        if ("tertiary".equals(style.refinedAccentVariant)) {
            float[] hsv = new float[3];
            Color.colorToHSV(palette[0], hsv);
            hsv[0] = (hsv[0] + 58.0f) % 360.0f;
            hsv[1] = Math.max(0.3f, Math.min(0.78f, hsv[1]));
            hsv[2] = Math.max(0.55f, hsv[2]);
            return Color.HSVToColor(hsv);
        }
        return palette[0];
    }

    private void updatePalette(Bitmap bmp) {
        if (bmp == paletteSource) return;
        paletteSource = bmp;
        if (bmp == null || bmp.isRecycled()) return;
        int[] arr = new int[6];
        for (int i = 0; i < 6; i++) {
            arr[i] = averageRegion(bmp, (i % 3) / 3.0f, (i / 3) / 2.0f,
                    ((i % 3) + 1) / 3.0f, ((i / 3) + 1) / 2.0f);
        }
        for (int i = 0; i < 6; i++) {
            for (int j = i + 1; j < 6; j++) {
                if (saturation(arr[j]) > saturation(arr[i])) {
                    int tmp = arr[i];
                    arr[i] = arr[j];
                    arr[j] = tmp;
                }
            }
        }
        palette = arr;
    }

    private static int averageRegion(Bitmap bmp, float x0, float y0, float x1, float y1) {
        long r = 0, g = 0, b = 0, count = 0;
        for (int yi = 0; yi < 6; yi++) {
            int py = Math.min(bmp.getHeight() - 1,
                    Math.max(0, Math.round((y0 + (((y1 - y0) * (yi + 0.5f)) / 6.0f)) * (bmp.getHeight() - 1))));
            for (int xi = 0; xi < 6; xi++) {
                int px = Math.min(bmp.getWidth() - 1,
                        Math.max(0, Math.round((x0 + (((x1 - x0) * (xi + 0.5f)) / 6.0f)) * (bmp.getWidth() - 1))));
                int c = bmp.getPixel(px, py);
                if (Color.alpha(c) < 96) continue;
                r += Color.red(c);
                g += Color.green(c);
                b += Color.blue(c);
                count++;
            }
        }
        if (count == 0) return 0xFF6F6F6F;
        float[] hsv = new float[3];
        Color.colorToHSV(Color.rgb((int) (r / count), (int) (g / count), (int) (b / count)), hsv);
        hsv[1] = Math.max(0.3f, Math.min(0.8f, hsv[1]));
        hsv[2] = Math.max(0.42f, Math.min(0.84f, hsv[2]));
        return Color.HSVToColor(hsv);
    }

    private static float saturation(int color) {
        float[] hsv = new float[3];
        Color.colorToHSV(color, hsv);
        return hsv[1];
    }

    // ============ 风格 3: compact ============

    private void drawCompact(Canvas canvas, MusicSnapshot snap, float d) {
        float width = getWidth();
        float height = getHeight();
        updatePalette(snap.albumArt);
        boolean light = refinedUsesLightColors();
        int accent = refinedAccentColor();
        int primary = light ? mix(accent, ViewCompat.MEASURED_STATE_MASK, 0.72f)
                : mix(accent, 0xFFFFFFFF, 0.78f);
        drawRefinedBackground(canvas, snap.albumArt, light, accent, snap.playing);
        int save = canvas.save();
        float radius = Math.min(width, height) * 0.18f;
        clipPath.reset();
        clipPath.addRoundRect(panelRect, radius, radius, Path.Direction.CW);
        canvas.clipPath(clipPath);

        float pad = Math.max(7.0f * d, 0.028f * width);
        boolean showCover = style.compactShowCover;
        boolean showBars = style.compactShowBars;
        float barH = showBars ? Math.max(d * 4.0f, 0.052f * height) : 0.0f;
        float bottomAreaY = showBars ? (height - (0.42f * pad)) - barH : height - pad;
        float rightX = width - pad;
        float coverLeft = rightX;
        if (showCover) {
            float coverTop = 0.62f * pad;
            float coverSize = Math.max(30.0f * d, Math.min(bottomAreaY - coverTop,
                    Math.min(width * 0.23f, 58.0f * d)));
            coverLeft = rightX - coverSize;
            coverRect.set(coverLeft, coverTop, coverLeft + coverSize, coverSize + coverTop);
            drawCover(canvas, snap.albumArt, coverRect, d * 10.0f, mix(accent, 0xFF434343, 0.55f));
        }
        float textW = Math.max(1.0f, (coverLeft - pad) - (d * 4.0f));
        float topRef = showCover ? coverRect.top : pad;
        float gap = 3.0f * d;
        float textAreaH = Math.max(1.0f, (bottomAreaY - gap) - topRef);
        float fontSize = Math.max(22.0f * d, Math.min(style.refinedLyricFontSize * d * style.textScale * 1.5f, 0.56f * textAreaH));
        boolean hasTrans = style.refinedShowTranslation && !snap.lyrics.translatedLyric.isEmpty();
        float transSize = 0.48f * fontSize;
        setTextPaint(fontSize, 1);
        float asc1 = paint.ascent();
        float desc1 = paint.descent();
        setTextPaint(transSize, 0);
        float asc2 = paint.ascent();
        float desc2 = paint.descent();
        float lineGap = Math.max(gap, fontSize * 0.12f);
        float centerY = (topRef + Math.max(0.0f,
                (textAreaH - (hasTrans ? (((desc1 - asc1) + lineGap) + desc2 - asc2) : desc1 - asc1)) * 0.5f)) - asc1;
        float yTrans = ((centerY + desc1) + lineGap) - asc2;
        if (hasTrans) {
            drawRefinedText(canvas, snap.lyrics.translatedLyric, pad + (textW * 0.5f), yTrans,
                    transSize, primary, textW, Paint.Align.CENTER, 0, 165);
        }
        if (snap.lyrics.interlude) {
            compactMarqueeActive = false;
            compactMarqueeText = "";
            compactMarqueeElapsedMs = 0L;
            float dotR = fontSize * 0.2f;
            drawInterludeDots(canvas, snap, pad + ((textW - interludeDotsWidth(dotR)) * 0.5f),
                    centerY - (fontSize * 0.72f), dotR, primary);
        } else {
            drawCompactMarqueeKaraoke(canvas, snap, currentText(snap), pad, centerY,
                    fontSize, textW, d, withAlpha(primary, 120), primary);
        }
        if (showBars) {
            drawCompactPlaybackBars(canvas, snap, pad, bottomAreaY, rightX, barH, primary);
        }
        canvas.restoreToCount(save);
    }

    private void drawCompactPlaybackBars(Canvas canvas, MusicSnapshot snap, float left,
                                          float top, float right, float h, int color) {
        if (right <= left || h <= 0.0f) return;
        float span = right - left;
        int n = Math.max(16, Math.min(40, Math.round(span / Math.max(5.0f, 1.2f * h))));
        float colW = span / n;
        float barW = Math.max(1.0f, 0.48f * colW);
        float progress = snap.durationMs > 0 ? clamp((float) snap.positionMs / (float) snap.durationMs) : 0.0f;
        float t = snap.playing ? ((float) SystemClock.elapsedRealtime()) / 260.0f : 0.0f;
        paint.setStyle(Paint.Style.FILL);
        paint.setShader(null);
        for (int i = 0; i < n; i++) {
            float mid = (i + 0.5f) / n;
            float wave = ((float) Math.abs(Math.sin((0.73f * i) + t)) * 0.58f) + 0.42f;
            float bh = Math.max(1.0f, wave * h * ((((float) Math.sin(mid * Math.PI)) * 0.64f) + 0.36f));
            paint.setColor(withAlpha(color, mid <= progress ? 220 : 72));
            float bx = left + (i * colW) + ((colW - barW) * 0.5f);
            float by = top + h;
            progressRect.set(bx, by - bh, bx + barW, by);
            float r = 0.5f * barW;
            canvas.drawRoundRect(progressRect, r, r, paint);
        }
    }

    private void drawCompactMarqueeKaraoke(Canvas canvas, MusicSnapshot snap, String text,
                                            float x, float y, float size, float w, float d,
                                            int dimColor, int brightColor) {
        if (text == null || text.isEmpty()) {
            compactMarqueeActive = false;
            compactMarqueeText = "";
            compactMarqueeElapsedMs = 0L;
            return;
        }
        String clean = text.replace('\n', ' ');
        setTextPaint(size, 1);
        paint.setTextAlign(Paint.Align.LEFT);
        float textW = paint.measureText(clean);
        if (textW <= w) {
            compactMarqueeActive = false;
            compactMarqueeText = "";
            compactMarqueeElapsedMs = 0L;
            drawKaraoke(canvas, snap, clean, x + (0.5f * w), y, size, w, Paint.Align.CENTER, dimColor, brightColor);
            return;
        }
        long now = SystemClock.elapsedRealtime();
        if (!clean.equals(compactMarqueeText)) {
            compactMarqueeText = clean;
            compactMarqueeElapsedMs = 0L;
            compactMarqueeLastFrameMs = now;
        } else if (snap.playing) {
            compactMarqueeElapsedMs += Math.max(0L, now - compactMarqueeLastFrameMs);
            compactMarqueeLastFrameMs = now;
        } else {
            compactMarqueeLastFrameMs = now;
        }
        compactMarqueeActive = snap.playing;
        float scroll = textW - w;
        if (snap.lyrics.wordTimed) {
            LrcTimeline.At at = snap.lyrics;
            int revealed = LrcTimeline.revealedCodePointCount(at.currentWord, at.wordProgressPermille);
            String revealedWord = at.currentWord.substring(0, at.currentWord.offsetByCodePoints(0, revealed));
            scroll = Math.max(0.0f, Math.min(scroll,
                    (paint.measureText(at.completedLyric) + paint.measureText(revealedWord))
                            - Math.max(12.0f * d, 0.64f * w)));
        } else if (snap.lyrics.lineStartMs >= 0 && snap.lyrics.lineDurationMs > 0) {
            float p = clamp((float) ((snap.positionMs + style.lyricOffsetMs) - snap.lyrics.lineStartMs)
                    / (float) snap.lyrics.lineDurationMs);
            scroll *= clamp((p - 0.06f) / 0.82f);
        } else {
            long dur = Math.max(240L, Math.round((scroll / (72.0f * d)) * 1000.0f));
            long total = dur + 1100;
            long phase = compactMarqueeElapsedMs % total;
            if (phase <= 400) {
                scroll = 0.0f;
            } else if (phase < dur + 400) {
                scroll = (scroll * ((float) (phase - 400))) / ((float) dur);
            }
        }
        int save = canvas.save();
        float top = y - (1.25f * size);
        float bottom = (0.35f * size) + y;
        canvas.clipRect(x, top, x + w, bottom);
        float sx = x - scroll;
        paint.setColor(dimColor);
        canvas.drawText(clean, sx, y, paint);
        if (!snap.lyricAvailable || snap.lyrics.lyric.isEmpty()) {
            paint.setColor(brightColor);
            canvas.drawText(clean, sx, y, paint);
        } else if (!snap.lyrics.wordTimed) {
            paint.setColor(brightColor);
            applyRefinedTextEffect(size, brightColor, 255);
            canvas.drawText(clean, sx, y, paint);
            paint.clearShadowLayer();
        } else {
            LrcTimeline.At at = snap.lyrics;
            int revealed = LrcTimeline.revealedCodePointCount(at.currentWord, at.wordProgressPermille);
            String revealedWord = at.currentWord.substring(0, at.currentWord.offsetByCodePoints(0, revealed));
            float revealedW = paint.measureText(at.completedLyric) + paint.measureText(revealedWord);
            int save2 = canvas.save();
            canvas.clipRect(sx, top, Math.min(textW, revealedW) + sx, bottom);
            paint.setColor(brightColor);
            if (style.refinedLyricGlow) {
                paint.setShadowLayer(Math.max(3.0f, size * 0.24f), 0.0f, 0.0f, withAlpha(brightColor, 90));
            }
            canvas.drawText(clean, sx, y, paint);
            paint.clearShadowLayer();
            canvas.restoreToCount(save2);
        }
        canvas.restoreToCount(save);
    }

    // ============ 风格 4: pip ============

    private void drawPip(Canvas canvas, MusicSnapshot snap, float d) {
        float width = getWidth();
        float height = getHeight();
        drawArtworkBackground(canvas, snap.albumArt, 0xFFE9C9D8, 0xFFB6A1D2, false);
        float pad = d * 13.0f;
        float coverSize = Math.max(46.0f * d, Math.min(Math.min(0.32f * height, 0.18f * width) * style.coverScale, 0.42f * height)) + pad;
        coverRect.set(pad, pad, coverSize, coverSize);
        drawCover(canvas, snap.albumArt, coverRect, d * 9.0f, 0xFFD3D2CC);
        float textX = coverRect.right + pad;
        float textW = (width - textX) - pad;
        float titleSize = d * 20.0f;
        drawLeft(canvas, snap.active ? snap.title : "等待音乐", textX, coverRect.top + titleSize,
                16.0f * d * style.textScale, 0xFF28282C, textW, 1);
        float subSize = d * 11.0f;
        drawLeft(canvas, snap.artist, textX, coverRect.top + (40.0f * d),
                subSize * style.textScale, 0xFF7A7480, textW, 0);
        drawLeft(canvas, snap.sourceName + sourceSuffix(snap), textX, coverRect.top + (58.0f * d),
                9.5f * d * style.textScale, 0xFF989084, textW, 0);
        float progressY = Math.max(coverRect.bottom + subSize, height * 0.38f);
        drawProgress(canvas, pad, progressY, width - pad, d * 2.0f, snap, 0xFF404654, 0xFF111114);
        float lyricY = progressY + (34.0f * d) + browseVisualOffsetPx;
        float lyricW = width - (2.0f * pad);
        if (style.lyricLineCount >= 3) {
            drawLeft(canvas, snap.lyrics.previousLyric, pad, lyricY,
                    12.0f * d * style.textScale, 0xFF909098, lyricW, 1);
            lyricY += 25.0f * d;
        }
        float fontSize = titleSize * style.textScale * (style.secondary ? 1.06f : 1.0f);
        float wrappedH;
        if (snap.lyrics.interlude) {
            drawInterludeDots(canvas, snap, pad, lyricY - (0.55f * fontSize),
                    fontSize * 0.35f, 0xFF1A1A1A);
            wrappedH = 1.22f * fontSize;
        } else {
            wrappedH = drawWrappedKaraoke(canvas, snap, currentText(snap), pad,
                    lyricY - fontSize, fontSize, lyricW, 0xFF1A1A1A, 2);
        }
        if (!snap.lyrics.translatedLyric.isEmpty()) {
            drawLeft(canvas, snap.lyrics.translatedLyric, pad, (lyricY - fontSize) + wrappedH + (d * 14.0f),
                    subSize * style.textScale, 0xFF7A7480, lyricW, 0);
            lyricY += 18.0f * d;
        }
        if (style.lyricLineCount >= 2) {
            drawLeft(canvas, snap.lyrics.nextLyric, pad, (lyricY - fontSize) + wrappedH + (36.0f * d),
                    d * 14.0f * style.textScale, 0xFF989084, lyricW, 1);
        }
    }

    // ============ 风格 5: custom ============

    private void drawCustom(Canvas canvas, MusicSnapshot snap, float d) {
        drawArtworkBackground(canvas, snap.albumArt, 0xFF0F1A24, 0xFF04101C, true);
        float width = getWidth();
        float height = getHeight();
        float pad = d * 10.0f;
        for (LyricsLayoutConfig.Item item : layoutConfig.items()) {
            if (!item.enabled) continue;
            float x = clamp(item.x) * width;
            float y = clamp(item.y) * height;
            if (isCustomLyricItem(item.id)) y += browseVisualOffsetPx;
            float maxW = Math.max(40.0f * d, (width - x) - pad);
            switch (item.id) {
                case "translation":
                    drawLeft(canvas, snap.lyrics.translatedLyric, x, y, 11.0f * d * style.textScale, 0xFFB8B8C0, maxW, 0);
                    break;
                case "artist":
                    drawLeft(canvas, snap.artist, x, y, 11.0f * d * style.textScale, 0xFFB8B8C0, maxW, 0);
                    break;
                case "previous":
                    drawLeft(canvas, snap.lyrics.previousLyric, x, y, d * 12.0f * style.textScale, 0xFF7F7F87, maxW, 0);
                    break;
                case "progress":
                    drawProgress(canvas, x, y, Math.min(width - pad, (0.38f * width) + x), d * 3.0f,
                            snap, 0xFF494E58, 0xFFFFCA96);
                    break;
                case "source":
                    drawLeft(canvas, snap.sourceName + sourceSuffix(snap), x, y, pad * style.textScale, 0xFF9099A4, maxW, 1);
                    break;
                case "next":
                    drawLeft(canvas, snap.lyrics.nextLyric, x, y, d * 12.0f * style.textScale, 0xFF7F7F87, maxW, 0);
                    break;
                case "cover": {
                    float size = Math.min(0.3f * width, 0.42f * height) * style.coverScale;
                    coverRect.set(x, y, x + size, size + y);
                    drawCover(canvas, snap.albumArt, coverRect, d * 12.0f, 0xFF27323E);
                    break;
                }
                case "title":
                    drawLeft(canvas, snap.active ? snap.title : "等待音乐", x, y,
                            17.0f * d * style.textScale, 0xFFFFFFFF, maxW, 1);
                    break;
                case "current":
                    drawKaraoke(canvas, snap, currentText(snap), x, y,
                            22.0f * d * style.textScale, maxW, Paint.Align.LEFT, 0xFFB1C1EB, 0xFFFFCA96);
                    break;
                default:
                    break;
            }
        }
    }

    private static boolean isCustomLyricItem(String id) {
        return "previous".equals(id) || "current".equals(id)
                || "translation".equals(id) || "next".equals(id);
    }

    // ============ 公共绘制工具 ============

    private void drawPanelShadow(Canvas canvas, float radius, int color) {
        paint.setShader(null);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(color);
        paint.setShadowLayer(0.75f * radius, 0.0f, 0.25f * radius, 0x70000000);
        canvas.drawRoundRect(panelRect, radius, radius, paint);
        paint.clearShadowLayer();
    }

    private void drawArtworkBackground(Canvas canvas, Bitmap bmp, int c1, int c2, boolean dark) {
        paint.setStyle(Paint.Style.FILL);
        paint.setAlpha(255);
        float radius = Math.min(getWidth(), getHeight()) * 0.075f;
        int sl = saveLayerAlphaCompat(canvas, panelRect, Math.round(clamp(style.opacity / 100.0f) * 255.0f));
        int save = canvas.save();
        clipPath.reset();
        clipPath.addRoundRect(panelRect, radius, radius, Path.Direction.CW);
        canvas.clipPath(clipPath);
        if (bmp != null && !bmp.isRecycled()) {
            drawBitmapCrop(canvas, blurredPreview(bmp), panelRect);
        } else {
            paint.setShader(new LinearGradient(0.0f, 0.0f, getWidth(), getHeight(), c1, c2, Shader.TileMode.CLAMP));
            canvas.drawRect(panelRect, paint);
            paint.setShader(null);
        }
        int dim = Math.round(clamp(style.backgroundDim / 100.0f) * 220.0f);
        if (dark) dim = Math.max(dim, 70);
        paint.setColor(dark ? Color.argb(dim, 4, 7, 12)
                : Color.argb(Math.min(190, dim + 65), 238, 226, 208));
        canvas.drawRect(panelRect, paint);
        canvas.restoreToCount(save);
        canvas.restoreToCount(sl);
        paint.setShader(null);
    }

    private Bitmap blurredPreview(Bitmap bmp) {
        if (bmp == blurSource && blurPreview != null && !blurPreview.isRecycled()) {
            return blurPreview;
        }
        if (blurPreview != null && blurPreview != blurSource && !blurPreview.isRecycled()) {
            blurPreview.recycle();
        }
        blurSource = bmp;
        int factor = Math.round(style.backgroundBlur * 0.46f) + 6;
        // Android 8.0+ 媒体会话封面常为 HARDWARE 配置，createScaledBitmap 会抛异常，
        // 必须先复制为软件位图再缩放；复制失败时放弃模糊，直接返回原图交给绘制方。
        Bitmap toScale = bmp;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                && bmp.getConfig() == Bitmap.Config.HARDWARE) {
            try {
                Bitmap copy = bmp.copy(Bitmap.Config.ARGB_8888, false);
                if (copy != null) toScale = copy;
            } catch (Exception ignored) {
                toScale = bmp;
            }
        }
        if (toScale.getConfig() == Bitmap.Config.HARDWARE) {
            // 无法取得软件位图，放弃缩放模糊，直接绘制原图（HARDWARE 位图可安全绘制）
            blurPreview = bmp;
            return blurPreview;
        }
        try {
            blurPreview = Bitmap.createScaledBitmap(toScale,
                    Math.max(2, toScale.getWidth() / factor),
                    Math.max(2, toScale.getHeight() / factor), true);
        } catch (Exception e) {
            blurPreview = bmp;
        }
        if (toScale != bmp && !toScale.isRecycled()) {
            toScale.recycle();
        }
        return blurPreview;
    }

    @Override
    protected void onDetachedFromWindow() {
        if (blurPreview != null && blurPreview != blurSource && !blurPreview.isRecycled()) {
            blurPreview.recycle();
        }
        blurPreview = null;
        blurSource = null;
        paletteSource = null;
        clearTextCaches();
        super.onDetachedFromWindow();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldW, int oldH) {
        if (w != oldW || h != oldH) clearTextCaches();
        super.onSizeChanged(w, h, oldW, oldH);
    }

    private void drawCover(Canvas canvas, Bitmap bmp, RectF rect, float corner, int placeholderColor) {
        paint.setStyle(Paint.Style.FILL);
        paint.setAlpha(255);
        int save = canvas.save();
        clipPath.reset();
        clipPath.addRoundRect(rect, corner, corner, Path.Direction.CW);
        canvas.clipPath(clipPath);
        if (bmp == null || bmp.isRecycled()) {
            paint.setShader(new LinearGradient(rect.left, rect.top, rect.right, rect.bottom,
                    placeholderColor, lighten(placeholderColor, 34), Shader.TileMode.CLAMP));
            canvas.drawRect(rect, paint);
            paint.setShader(null);
            setTextPaint(Math.min(rect.width(), rect.height()) * 0.24f, 1);
            paint.setTextAlign(Paint.Align.CENTER);
            paint.setColor(0x76000000);
            canvas.drawText("♪", rect.centerX(), rect.centerY() - ((paint.ascent() + paint.descent()) / 2.0f), paint);
        } else {
            drawBitmapCrop(canvas, bmp, rect);
        }
        canvas.restoreToCount(save);
    }

    private void drawBitmapCrop(Canvas canvas, Bitmap bmp, RectF rect) {
        drawBitmapCrop(canvas, bmp, rect, 255);
    }

    private void drawBitmapCrop(Canvas canvas, Bitmap bmp, RectF rect, int alpha) {
        float srcRatio = bmp.getWidth() / (float) bmp.getHeight();
        float dstRatio = rect.width() / Math.max(1.0f, rect.height());
        if (srcRatio > dstRatio) {
            int w = Math.round(bmp.getHeight() * dstRatio);
            int left = (bmp.getWidth() - w) / 2;
            sourceRect.set(left, 0, w + left, bmp.getHeight());
        } else {
            int h = Math.round(bmp.getWidth() / dstRatio);
            int top = (bmp.getHeight() - h) / 2;
            sourceRect.set(0, top, bmp.getWidth(), h + top);
        }
        paint.setShader(null);
        paint.setAlpha(Math.max(0, Math.min(255, alpha)));
        canvas.drawBitmap(bmp, sourceRect, rect, paint);
        paint.setAlpha(255);
    }

    private void drawProgress(Canvas canvas, float x, float y, float xEnd, float h,
                               MusicSnapshot snap, int bgColor, int fgColor) {
        if (xEnd <= x) return;
        paint.setShader(null);
        paint.setStyle(Paint.Style.FILL);
        paint.setAlpha(255);
        paint.setColor(bgColor);
        progressRect.set(x, y, xEnd, y + h);
        canvas.drawRoundRect(progressRect, h, h, paint);
        float p = snap.durationMs > 0 ? clamp((float) snap.positionMs / (float) snap.durationMs) : 0.0f;
        paint.setColor(fgColor);
        progressRect.right = x + ((xEnd - x) * p);
        canvas.drawRoundRect(progressRect, h, h, paint);
    }

    private String currentText(MusicSnapshot snap) {
        if (!snap.active) return "等待播放";
        if (!snap.lyricLoaded && !snap.lyricAvailable) return "正在匹配歌词…";
        if (!snap.lyricAvailable) return "暂无匹配歌词";
        if (snap.lyrics.interlude) return "♪  ·  ·  ·";
        if (snap.lyrics.lyric.isEmpty()) return "即将开始";
        return snap.lyrics.lyric;
    }

    private String sourceSuffix(MusicSnapshot snap) {
        return snap.lyricSourceName.isEmpty() ? "" : "  ·  " + snap.lyricSourceName;
    }

    private void drawBrowseIndicator(Canvas canvas, MusicSnapshot snap, float d) {
        long sec = Math.max(0L, browsePositionMs) / 1000;
        String text = String.format(Locale.ROOT, "浏览歌词  %d:%02d  ·  松手后返回",
                sec / 60, sec % 60);
        setTextPaint(10.5f * d, 1);
        float padX = 9.0f * d;
        float h = 25.0f * d;
        float right = getWidth() - (d * 10.0f);
        workRect.set(right - (paint.measureText(text) + (padX * 2.0f)), padX, right, padX + h);
        paint.setColor(0xA8303030);
        float r = h / 2.0f;
        canvas.drawRoundRect(workRect, r, r, paint);
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setColor(0xFFE8E8E8);
        canvas.drawText(text, workRect.centerX(),
                workRect.centerY() - ((paint.ascent() + paint.descent()) / 2.0f), paint);
    }

    private void drawInterludeDots(Canvas canvas, MusicSnapshot snap, float x, float y,
                                    float r, int color) {
        LrcTimeline.At at = snap.lyrics;
        long elapsed = Math.max(0L, (snap.positionMs + style.lyricOffsetMs) - at.lineStartMs);
        paint.setStyle(Paint.Style.FILL);
        float gap = 3.4285715f * r;
        int save = canvas.save();
        float scale = RefinedInterludeAnimation.breathScale(elapsed);
        float cy = y + r;
        canvas.scale(scale, scale, x, cy);
        for (int i = 0; i < 3; i++) {
            RefinedInterludeAnimation.DotState ds = RefinedInterludeAnimation.dotState(elapsed, at.lineDurationMs, i);
            paint.setColor(withAlpha(color, Math.round(Color.alpha(color) * ds.opacity)));
            canvas.drawCircle(x + r + (i * gap), cy, ds.scale * r, paint);
        }
        canvas.restoreToCount(save);
    }

    private float drawWrappedKaraoke(Canvas canvas, MusicSnapshot snap, String text,
                                      float x, float y, float size, float maxW,
                                      int brightColor, int maxLines) {
        if (text == null || text.isEmpty()) return 0.0f;
        setTextPaint(size, 1);
        List<WrappedChunk> chunks = wrapText(text.replace('\n', ' '), maxW, maxLines);
        float lineH = 1.22f * size;
        LrcTimeline.At at = snap.lyrics;
        int revealed;
        if (!snap.lyricAvailable || at.lyric.isEmpty()) {
            revealed = 0;
        } else if (!at.wordTimed) {
            revealed = text.length();
        } else {
            int wordRevealed = LrcTimeline.revealedCodePointCount(at.currentWord, at.wordProgressPermille);
            String revealedWord = at.currentWord.substring(0, at.currentWord.offsetByCodePoints(0, wordRevealed));
            revealed = Math.min(text.length(), at.completedLyric.length() + revealedWord.length());
        }
        paint.setTextAlign(Paint.Align.LEFT);
        int start = 0;
        for (int i = 0; i < chunks.size(); i++) {
            WrappedChunk c = chunks.get(i);
            float cy = y + size + (i * lineH);
            paint.setColor(withAlpha(brightColor, 105));
            canvas.drawText(c.text, x, cy, paint);
            int end = Math.max(start, Math.min(c.end, revealed) - c.start);
            if (end > 0) {
                int len = Math.min(end, c.text.length());
                float revealedW = (len >= c.text.length())
                        ? paint.measureText(c.text)
                        : paint.measureText(c.text.substring(start, len));
                int save = canvas.save();
                canvas.clipRect(x, cy - (1.18f * size), revealedW + x, cy + (0.3f * size));
                paint.setColor(brightColor);
                if (!"refined".equals(style.overlayStyle) || style.refinedLyricGlow) {
                    paint.setShadowLayer(Math.max(3.0f, 0.24f * size), 0.0f, 0.0f, withAlpha(brightColor, 90));
                }
                canvas.drawText(c.text, x, cy, paint);
                paint.clearShadowLayer();
                canvas.restoreToCount(save);
            }
            start = 0;
        }
        return chunks.size() * lineH;
    }

    private float drawWrappedText(Canvas canvas, String text, float x, float y, float size,
                                   int color, float maxW, int bold, int maxLines) {
        if (text == null || text.isEmpty()) return 0.0f;
        setTextPaint(size, bold);
        paint.setTextAlign(Paint.Align.LEFT);
        paint.setColor(color);
        List<WrappedChunk> chunks = wrapText(text.replace('\n', ' '), maxW, maxLines);
        float lineH = 1.22f * size;
        for (int i = 0; i < chunks.size(); i++) {
            canvas.drawText(chunks.get(i).text, x, y + size + (i * lineH), paint);
        }
        return chunks.size() * lineH;
    }

    private float wrappedTextHeight(String text, float size, float maxW, int maxLines) {
        if (text == null || text.isEmpty()) return 0.0f;
        setTextPaint(size, 1);
        return wrapText(text.replace('\n', ' '), maxW, maxLines).size() * size * 1.22f;
    }

    private List<WrappedChunk> wrapText(String text, float maxW, int maxLines) {
        if (text == null || text.isEmpty() || maxW <= 0.0f || maxLines <= 0) {
            return Collections.emptyList();
        }
        TextLayoutKey key = TextLayoutKey.fromPaint(text, paint, maxW, maxLines);
        List<WrappedChunk> cached = wrappedTextCache.get(key);
        if (cached != null) return cached;
        ArrayList<WrappedChunk> out = new ArrayList<>();
        int i = 0;
        while (i < text.length() && out.size() < maxLines) {
            while (i < text.length() && text.charAt(i) == ' ') i++;
            if (i >= text.length()) break;
            int breakN = paint.breakText(text, i, text.length(), true, maxW, null);
            if (breakN <= 0) breakN = Character.charCount(text.codePointAt(i));
            int end = Math.min(text.length(), breakN + i);
            if (end < text.length() && end > i && Character.isHighSurrogate(text.charAt(end - 1))) end--;
            if (end < text.length()) {
                int lastSpace = text.lastIndexOf(' ', end - 1);
                if (lastSpace > Math.max(1, (end - i) / 2) + i) end = lastSpace;
            }
            if (end <= i) end = Math.min(text.length(), Character.charCount(text.codePointAt(i)) + i);
            String chunk = text.substring(i, end).trim();
            if (out.size() == maxLines - 1 && end < text.length()) {
                chunk = ellipsize(chunk + "…", maxW);
            }
            out.add(new WrappedChunk(chunk, i, end));
            i = end;
        }
        wrappedTextCache.put(key, out);
        return out;
    }

    private static final class WrappedChunk {
        final String text;
        final int start;
        final int end;

        WrappedChunk(String text, int start, int end) {
            this.text = text;
            this.start = start;
            this.end = end;
        }
    }

    private void drawKaraoke(Canvas canvas, MusicSnapshot snap, String text,
                              float x, float y, float size, float maxW,
                              Paint.Align align, int dimColor, int brightColor) {
        if (text == null || text.isEmpty()) return;
        float fit = fitSize(text, size, maxW, 1);
        setTextPaint(fit, 1);
        paint.setTextAlign(align);
        String clean = ellipsize(text.replace('\n', ' '), maxW);
        float textW = paint.measureText(clean);
        float startX = align == Paint.Align.CENTER ? x - (textW / 2.0f) : x;
        paint.setColor(dimColor);
        canvas.drawText(clean, x, y, paint);
        if (!snap.lyricAvailable || snap.lyrics.lyric.isEmpty()) return;
        LrcTimeline.At at = snap.lyrics;
        if (!at.wordTimed) {
            paint.setColor(brightColor);
            if (usesRefinedVisualStyle()) applyRefinedTextEffect(fit, brightColor, 255);
            canvas.drawText(clean, x, y, paint);
            paint.clearShadowLayer();
            return;
        }
        int revealed = LrcTimeline.revealedCodePointCount(at.currentWord, at.wordProgressPermille);
        String revealedWord = at.currentWord.substring(0, at.currentWord.offsetByCodePoints(0, revealed));
        float revealedW = paint.measureText(at.completedLyric) + paint.measureText(revealedWord);
        int save = canvas.save();
        float drawY = (usesRefinedVisualStyle() && "float".equals(style.refinedKaraokeAnimation))
                ? y - (0.06f * fit) : y;
        float bottom = fit * 0.35f;
        canvas.clipRect(startX, y - (1.25f * fit), Math.min(textW, revealedW) + startX, y + bottom);
        paint.setColor(brightColor);
        if (!usesRefinedVisualStyle() || style.refinedLyricGlow) {
            paint.setShadowLayer(Math.max(4.0f, bottom), 0.0f, 0.0f,
                    Color.argb(100, Color.red(brightColor), Color.green(brightColor), Color.blue(brightColor)));
        }
        canvas.drawText(clean, x, drawY, paint);
        paint.clearShadowLayer();
        canvas.restoreToCount(save);
    }

    private void drawCentered(Canvas canvas, String text, float y,
                               float size, int color, float maxW, int bold) {
        if (text == null || text.isEmpty()) return;
        setTextPaint(fitSize(text, size, maxW, bold), bold);
        paint.setTextAlign(Paint.Align.CENTER);
        String clean = ellipsize(text.replace('\n', ' '), maxW);
        paint.setColor(color);
        canvas.drawText(clean, getWidth() / 2.0f, y, paint);
    }

    private void drawLeft(Canvas canvas, String text, float x, float y,
                           float size, int color, float maxW, int bold) {
        if (text == null || text.isEmpty()) return;
        setTextPaint(fitSize(text, size, maxW, bold), bold);
        paint.setTextAlign(Paint.Align.LEFT);
        paint.setColor(color);
        canvas.drawText(ellipsize(text.replace('\n', ' '), maxW), x, y, paint);
    }

    private float fitSize(String text, float size, float maxW, int bold) {
        setTextPaint(size, bold);
        if (text == null) text = "";
        float w = paint.measureText(text);
        return (w <= maxW || w <= 0.0f) ? size : Math.max(0.62f * size, (size * maxW) / w);
    }

    private String ellipsize(String text, float maxW) {
        if (paint.measureText(text) <= maxW) return text;
        TextLayoutKey key = TextLayoutKey.fromPaint(text, paint, maxW, -1);
        String cached = ellipsizedTextCache.get(key);
        if (cached != null) return cached;
        int lo = 0;
        int hi = text.length();
        while (lo < hi) {
            int mid = ((lo + hi) + 1) >>> 1;
            if (paint.measureText(text.substring(0, mid) + "…") <= maxW) lo = mid;
            else hi = mid - 1;
        }
        if (lo > 0 && Character.isHighSurrogate(text.charAt(lo - 1))) lo--;
        String result = text.substring(0, lo) + "…";
        ellipsizedTextCache.put(key, result);
        return result;
    }

    private void setTextPaint(float size, int bold) {
        paint.setShader(null);
        paint.setAlpha(255);
        paint.setStyle(Paint.Style.FILL);
        paint.setMaskFilter(null);
        paint.clearShadowLayer();
        paint.setTextSize(size);
        paint.setTypeface(bold == 1 ? SANS_BOLD : SANS_NORMAL);
    }

    private void clearTextCaches() {
        wrappedTextCache.evictAll();
        ellipsizedTextCache.evictAll();
    }

    private static final class TextLayoutKey {
        final String value;
        final int textSizeBits;
        final int widthBits;
        final int maxLines;
        final int typefaceStyle;

        private TextLayoutKey(String value, int textSizeBits, int widthBits, int maxLines, int typefaceStyle) {
            this.value = value;
            this.textSizeBits = textSizeBits;
            this.widthBits = widthBits;
            this.maxLines = maxLines;
            this.typefaceStyle = typefaceStyle;
        }

        static TextLayoutKey fromPaint(String value, Paint paint, float width, int maxLines) {
            Typeface tf = paint.getTypeface();
            return new TextLayoutKey(value, Float.floatToIntBits(paint.getTextSize()),
                    Float.floatToIntBits(width), maxLines, tf == null ? 0 : tf.getStyle());
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof TextLayoutKey)) return false;
            TextLayoutKey k = (TextLayoutKey) o;
            return textSizeBits == k.textSizeBits && widthBits == k.widthBits
                    && maxLines == k.maxLines && typefaceStyle == k.typefaceStyle
                    && value.equals(k.value);
        }

        @Override
        public int hashCode() {
            return ((((((value.hashCode() * 31) + textSizeBits) * 31) + widthBits) * 31) + maxLines) * 31 + typefaceStyle;
        }
    }

    // ============ 播放控制按钮 ============

    private enum MediaControlAction { PREVIOUS, TOGGLE_PLAY_PAUSE, NEXT }

    private void drawPlaybackControls(Canvas canvas, MusicSnapshot snap, float d) {
        if (style.secondary || getWidth() <= 0 || getHeight() <= 0) return;
        PlaybackControlLayout l = playbackControlLayout(d);

        // 通用配色（1:1 对齐原版 drawPlaybackControls 字节码）
        int bgColor = snap.active ? 0xC92B405A : 0x8A26384E;
        int fgColor = snap.active ? 0xFFF5F9FF : 0xFF9AAABB;
        int playingColor = snap.playing ? 0xFFFFCA66 : 0xFF6EE7F2;
        int playPauseBg = snap.active ? 0xE0445D78 : bgColor;

        String s = style.overlayStyle;
        if ("refined".equals(s)) {
            // refined 覆盖：prev/next 用深蓝紫，play/pause 用粉红
            bgColor = snap.active ? 0x8C243B52 : 0x62243852;
            playPauseBg = snap.active ? 0xC13A5872 : bgColor;
        }

        if (style.showPreviousButton) {
            drawPlaybackButton(canvas, l.centerX - l.spacing, l.centerY, l.radius, bgColor, fgColor,
                    MediaControlAction.PREVIOUS, false);
        }
        if (style.showPlayPauseButton) {
            drawPlaybackButton(canvas, l.centerX, l.centerY, l.radius * 1.12f, playPauseBg, playingColor,
                    MediaControlAction.TOGGLE_PLAY_PAUSE, snap.playing);
        }
        if (style.showNextButton) {
            drawPlaybackButton(canvas, l.centerX + l.spacing, l.centerY, l.radius, bgColor, fgColor,
                    MediaControlAction.NEXT, false);
        }
    }

    private void drawPlaybackButton(Canvas canvas, float cx, float cy, float radius,
                                     int bgColor, int fgColor,
                                     MediaControlAction action, boolean playing) {
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(bgColor);
        canvas.drawCircle(cx, cy, radius, paint);
        paint.setStyle(Paint.Style.STROKE);
        float stroke = Math.max(1.0f, 0.075f * radius);
        paint.setStrokeWidth(stroke);
        paint.setColor(withAlpha(fgColor, 125));
        canvas.drawCircle(cx, cy, radius, paint);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(fgColor);
        float half = 0.34f * radius;
        if (action != MediaControlAction.TOGGLE_PLAY_PAUSE) {
            float dir = action == MediaControlAction.PREVIOUS ? -1.0f : 1.0f;
            float baseX = cx - (dir * half);
            float topY = cy - half;
            float botY = cy + half;
            Path p = new Path();
            p.moveTo(baseX, topY);
            p.lineTo(baseX, botY);
            p.lineTo(cx + (0.78f * dir), cy);
            p.close();
            canvas.drawPath(p, paint);
            float barX = cx + (dir * 1.05f);
            canvas.drawRect(barX - stroke, topY, barX + stroke, botY, paint);
            return;
        }
        if (playing) {
            float bw = Math.max(2.0f, 0.19f * radius);
            float gap = radius * 0.12f;
            float left = cx - gap;
            float right = cx + gap;
            float topY = cy - half;
            float botY = cy + half;
            workRect.set(left - bw, topY, left, botY);
            canvas.drawRoundRect(workRect, bw, bw, paint);
            workRect.set(right, topY, right + bw, botY);
            canvas.drawRoundRect(workRect, bw, bw, paint);
        } else {
            Path p = new Path();
            float baseX = cx - (0.52f * half);
            p.moveTo(baseX, cy - half);
            p.lineTo(baseX, cy + half);
            p.lineTo(cx + half, cy);
            p.close();
            canvas.drawPath(p, paint);
        }
    }

    private PlaybackControlLayout playbackControlLayout(float d) {
        float width = getWidth();
        float height = getHeight();
        float radius;
        float spacing;
        float centerX;
        float centerY;
        String s = style.overlayStyle;
        if ("refined".equals(s)) {
            radius = Math.max(11.0f * d, Math.min(16.0f * d, 0.07f * height));
            spacing = radius * 2.7f;
            centerX = width * 0.75f;
            centerY = radius + (18.0f * d);
        } else if ("compact".equals(s)) {
            radius = Math.max(7.0f * d, Math.min(10.0f * d, 0.085f * height));
            spacing = radius * 2.45f;
            centerX = width * 0.38f;
            centerY = (height - radius) - (11.0f * d);
        } else if ("pip".equals(s)) {
            radius = Math.max(9.0f * d, Math.min(13.0f * d, 0.075f * height));
            spacing = radius * 2.6f;
            centerX = width - ((spacing + radius) + (14.0f * d));
            centerY = (height - radius) - (12.0f * d);
        } else if ("custom".equals(s)) {
            radius = Math.max(9.0f * d, Math.min(14.0f * d, 0.085f * height));
            spacing = radius * 2.7f;
            centerX = width - ((spacing + radius) + (14.0f * d));
            centerY = (height - radius) - (12.0f * d);
        } else {
            radius = Math.max(10.0f * d, Math.min(16.0f * d, 0.095f * height));
            spacing = radius * 2.85f;
            centerX = width * 0.5f;
            centerY = (height - radius) - (7.0f * d);
        }
        float minCenter = (1.18f * radius) + spacing;
        float padEdge = 4.0f * d;
        centerX = Math.max(minCenter, Math.min(width - minCenter, centerX));
        centerY = Math.max(radius + padEdge, Math.min((height - radius) - padEdge, centerY));
        return new PlaybackControlLayout(centerX, centerY, radius, spacing);
    }

    private static final class PlaybackControlLayout {
        final float centerX;
        final float centerY;
        final float radius;
        final float spacing;

        PlaybackControlLayout(float centerX, float centerY, float radius, float spacing) {
            this.centerX = centerX;
            this.centerY = centerY;
            this.radius = radius;
            this.spacing = spacing;
        }
    }

    // ============ 浏览手势 ============

    private void consumeBrowseSteps(float step) {
        if (step <= 0.0f) return;
        while (true) {
            float negStep = -step;
            if (browseVisualOffsetPx > negStep) {
                while (browseVisualOffsetPx >= step) {
                    long shifted = MusicController.getInstance().shiftLyricPosition(browsePositionMs, -1);
                    if (shifted == browsePositionMs) {
                        browseVisualOffsetPx = step + LyricPreviewMotion.rubberBand(browseVisualOffsetPx - step, 2.0f * step);
                        return;
                    } else {
                        browsePositionMs = shifted;
                        browseVisualOffsetPx -= step;
                    }
                }
                return;
            }
            long shifted = MusicController.getInstance().shiftLyricPosition(browsePositionMs, 1);
            if (shifted == browsePositionMs) {
                browseVisualOffsetPx = negStep - LyricPreviewMotion.rubberBand(-(browseVisualOffsetPx + step), step * 2.0f);
                return;
            }
            browsePositionMs = shifted;
            browseVisualOffsetPx += step;
        }
    }

    private void projectBrowseRelease(float step) {
        int delta = LyricPreviewMotion.projectedLineDelta(browseVisualOffsetPx, browseVelocityPxPerSecond, step);
        int dir = Integer.compare(delta, 0);
        for (int i = 0; i < Math.abs(delta); i++) {
            long shifted = MusicController.getInstance().shiftLyricPosition(browsePositionMs, dir);
            if (shifted == browsePositionMs) return;
            browsePositionMs = shifted;
            browseVisualOffsetPx += dir > 0 ? step : -step;
        }
    }

    private void updateBrowseSpring(long now) {
        if (!browseSettling) return;
        float dt = ((float) (now - browseSettleLastFrameMs)) / 1000.0f;
        browseSettleLastFrameMs = now;
        LyricPreviewMotion.SpringState st = LyricPreviewMotion.stepCritical(
                browseVisualOffsetPx, browseVelocityPxPerSecond, dt, 0.38f);
        browseVisualOffsetPx = st.position;
        browseVelocityPxPerSecond = st.velocity;
        browseSettling = !st.settled;
    }

    private float browseStepPx() {
        if ("refined".equals(style.overlayStyle) && lastRefinedBrowseStepPx > 1.0f) {
            return lastRefinedBrowseStepPx;
        }
        return getResources().getDisplayMetrics().density * 34.0f;
    }

    private boolean manualPreviewActive() {
        return browsingLyrics || browseSettling || browseUntilElapsedMs > SystemClock.elapsedRealtime();
    }

    private static boolean animationsEnabled() {
        if (Build.VERSION.SDK_INT >= 26) {
            return ValueAnimator.areAnimatorsEnabled();
        }
        return true;
    }

    private boolean usesRefinedVisualStyle() {
        return "refined".equals(style.overlayStyle) || "compact".equals(style.overlayStyle);
    }

    // ============ 工具 ============

    private static boolean insideCircle(float x, float y, float cx, float cy, float r) {
        float dx = x - cx;
        float dy = y - cy;
        return (dx * dx) + (dy * dy) <= r * r;
    }

    private static float interludeDotsWidth(float r) {
        return (r * 2.0f) + (r * 3.4285715f * 2.0f);
    }

    private static int lighten(int color, int amount) {
        return Color.rgb(Math.min(255, Color.red(color) + amount),
                Math.min(255, Color.green(color) + amount),
                Math.min(255, Color.blue(color) + amount));
    }

    private static int mix(int c1, int c2, float t) {
        float k = clamp(t);
        float inv = 1.0f - k;
        return Color.rgb(
                Math.round((Color.red(c1) * inv) + (Color.red(c2) * k)),
                Math.round((Color.green(c1) * inv) + (Color.green(c2) * k)),
                Math.round((Color.blue(c1) * inv) + (Color.blue(c2) * k)));
    }

    private static int withAlpha(int color, int alpha) {
        return Color.argb(Math.max(0, Math.min(255, alpha)),
                Color.red(color), Color.green(color), Color.blue(color));
    }

    private static int saveLayerAlphaCompat(Canvas canvas, RectF rect, int alpha) {
        return canvas.saveLayerAlpha(rect, alpha);
    }

    private static float clamp(float v) {
        return Math.max(0.0f, Math.min(1.0f, v));
    }
}
