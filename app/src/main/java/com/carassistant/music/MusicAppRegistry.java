/*
 * Copyright (C) 2026 CarAssistant Project. All rights reserved.
 *
 * 版权所有 (C) 2026 车机助手项目
 * 保留所有权利
 *
 * 本源代码受著作权法保护，未经著作权人书面许可，不得以任何形式复制、修改、
 * 分发、出售或逆向工程。违反者将承担法律责任。
 *
 * Source code protected by copyright law. Unauthorized copying, modification,
 * distribution, sale, or reverse engineering without written permission is
 * prohibited and subject to legal action.
 */

package com.carassistant.music;

import java.util.Locale;

/**
 * 音乐应用识别注册表
 *
 * 移植自歌词伴侣（com.zuoqirun.lyricscompanion.MusicAppRegistry）的识别与打分逻辑。
 *
 * 核心职责：
 * 1. 已知音乐应用包名匹配（含车机专属变体：iot/car/auto/lite）
 * 2. 基于应用友好名称的中英文关键词模糊匹配（兜底识别未知包名）
 * 3. {@link #selectionScore} 综合打分：playbackRank + hasMetadata + supportsControls + sameSession
 *    用于在多个活跃会话中选择最可能正在被用户使用的会话
 */
public final class MusicAppRegistry {

    /** 已知音乐应用列表（包名前缀 + sourceId + 显示名） */
    private static final App[] KNOWN_APPS = {
            new App("netease", "网易云音乐", "com.netease.cloudmusic"),
            new App("netease", "网易云音乐", "com.netease.cloudmusic.iot"),
            new App("qqmusic", "QQ 音乐", "com.tencent.qqmusic"),
            new App("qqmusic", "QQ 音乐", "com.tencent.qqmusiccar"),
            new App("kugou", "酷狗音乐", "com.kugou.android"),
            new App("kugou", "酷狗音乐", "com.kugou.android.auto"),
            new App("kugou", "酷狗概念版", "com.kugou.android.lite"),
            new App("kugou", "酷狗音乐", "com.kugou.auto"),
            new App("kuwo", "酷我音乐", "cn.kuwo.player"),
            new App("kuwo", "酷我音乐", "cn.kuwo.kwmusiccar"),
            new App("kuwo", "酷我音乐", "cn.kuwo.kwmusic"),
            new App("kuwo", "酷我音乐", "cn.kuwo.car"),
            new App("kuwo", "酷我音乐", "com.shaiban.audioplayer.mplayer"),
            new App("spotify", "Spotify", "com.spotify.music"),
            new App("soda", "汽水音乐", "com.luna.music"),
            new App("soda", "汽水音乐", "com.luna.music.car"),
            new App("migu", "咪咕音乐", "cmccwm.mobilemusic"),
            new App("xiaomi", "小米音乐", "com.miui.player"),
            new App("huawei", "华为音乐", "com.android.mediacenter"),
            new App("apple_music", "Apple Music", "com.apple.android.music"),
            new App("youtube_music", "YouTube Music", "com.google.android.apps.youtube.music"),
            new App("amazon_music", "Amazon Music", "com.amazon.mp3"),
            new App("bilibili", "哔哩哔哩", "tv.danmaku.bili"),
            new App("bilibili", "哔哩哔哩", "com.bilibili.app"),
    };

    /** 元数据存在加分（200，移植自 ItemTouchHelper.DEFAULT_DRAG_ANIMATION_DURATION） */
    private static final int BONUS_HAS_METADATA = 200;
    /** 支持播放控制加分 */
    private static final int BONUS_SUPPORTS_CONTROLS = 40;
    /** 仍是当前选中会话加分（避免抖动） */
    private static final int BONUS_SAME_SESSION = 10;

    private MusicAppRegistry() {}

    /**
     * 会话选择打分（移植自歌词伴侣 selectionScore）。
     *
     * @param playbackRank    播放状态分（来自 {@link #playbackRank}）
     * @param hasMetadata     是否有可用元数据（标题非空）
     * @param supportsControls 是否支持播放/暂停/上下曲控制
     * @param sameSession     是否仍是上次选中的会话（避免抖动）
     * @return 综合分值，越大越优先选中
     */
    public static int selectionScore(int playbackRank, boolean hasMetadata,
                                     boolean supportsControls, boolean sameSession) {
        int score = playbackRank;
        if (hasMetadata) score += BONUS_HAS_METADATA;
        if (supportsControls) score += BONUS_SUPPORTS_CONTROLS;
        if (sameSession) score += BONUS_SAME_SESSION;
        return score;
    }

    /**
     * 播放状态分级（移植自歌词伴侣 playbackRank）。
     *
     * - PLAYING / PAUSED / BUFFERING / FAST_FORWARDING / REWINDING / SKIPPING：高/中分
     * - STOPPED / ERROR / CONNECTING / NONE：0 分（不应被选中）
     */
    public static int playbackRank(int state) {
        switch (state) {
            case android.media.session.PlaybackState.STATE_PAUSED:
                return 5000;
            case android.media.session.PlaybackState.STATE_PLAYING:
            case android.media.session.PlaybackState.STATE_BUFFERING:
            case android.media.session.PlaybackState.STATE_CONNECTING:
                return 10000;
            case android.media.session.PlaybackState.STATE_FAST_FORWARDING:
                return 9000;
            case android.media.session.PlaybackState.STATE_REWINDING:
                return 8000;
            case android.media.session.PlaybackState.STATE_SKIPPING_TO_PREVIOUS:
            case android.media.session.PlaybackState.STATE_SKIPPING_TO_NEXT:
            case android.media.session.PlaybackState.STATE_SKIPPING_TO_QUEUE_ITEM:
                return 7000;
            case android.media.session.PlaybackState.STATE_STOPPED:
            case android.media.session.PlaybackState.STATE_ERROR:
            case android.media.session.PlaybackState.STATE_NONE:
            default:
                return 0;
        }
    }

    /** 解析包名/label 为 App（含已知/未知标识） */
    public static App resolve(String packageName, String appLabel) {
        String pkg = safe(packageName).toLowerCase(Locale.ROOT);
        // 1. 精确匹配已知应用包名前缀
        for (App app : KNOWN_APPS) {
            if (pkg.equals(app.packagePrefix) || pkg.startsWith(app.packagePrefix + ".")) {
                return app;
            }
        }
        // 2. 基于包名/label 做模糊关键词匹配
        App resolved = resolveFeatures(safe(appLabel), pkg);
        if (resolved != null) return resolved;

        // 3. 兜底：用 label 或包名末段作为显示名
        String label = safe(appLabel).trim();
        if (label.isEmpty()) {
            int lastDot = pkg.lastIndexOf('.');
            label = lastDot >= 0 ? pkg.substring(lastDot + 1) : pkg;
        }
        if (label.isEmpty() || "player".equalsIgnoreCase(label) || "music".equalsIgnoreCase(label)) {
            label = "音乐播放器";
        }
        return new App("media", label, pkg, false);
    }

    /** 关键词模糊匹配（移植自歌词伴侣 resolveFeatures） */
    private static App resolveFeatures(String label, String pkg) {
        String s = safe(label).toLowerCase(Locale.ROOT).replaceAll("[\\s\\p{Punct}·•]+", "");
        String p = safe(pkg).toLowerCase(Locale.ROOT);
        if (containsAny(s, "网易", "netease", "cloudmusic", "163音乐")
                || containsAny(p, "netease", "cloudmusic")) {
            return new App("netease", "网易云音乐", pkg);
        }
        if (containsAny(s, "qq", "腾讯音乐", "qqmusic")
                || containsAny(p, "qqmusic")) {
            return new App("qqmusic", "QQ 音乐", pkg);
        }
        if (containsAny(s, "酷狗", "kugou", "kgmusic")
                || containsAny(p, "kugou")) {
            return new App("kugou", "酷狗音乐", pkg);
        }
        if (containsAny(s, "酷我", "kuwo", "kwmusic")
                || containsAny(p, "kuwo")) {
            return new App("kuwo", "酷我音乐", pkg);
        }
        if (containsAny(s, "汽水", "lunamusic", "sodamusic")
                || containsAny(p, "luna.music", "soda")) {
            return new App("soda", "汽水音乐", pkg);
        }
        if (containsAny(s, "哔哩", "bilibili", "b 站")
                || containsAny(p, "bilibili", "danmaku.bili")) {
            return new App("bilibili", "哔哩哔哩", pkg);
        }
        return null;
    }

    private static boolean containsAny(String str, String... arr) {
        for (String s : arr) {
            if (str.contains(s)) return true;
        }
        return false;
    }

    private static String safe(String s) {
        return s == null ? "" : s;
    }

    /** 应用识别结果 */
    public static final class App {
        /** 源 ID（netease/qqmusic/kugou/kuwo/soda/bilibili/media 等），用于多源歌词优先级 */
        public final String sourceId;
        /** 显示名（用于 UI） */
        public final String displayName;
        /** 包名前缀（用于精确匹配） */
        public final String packagePrefix;
        /** 是否为已知音乐应用 */
        public final boolean known;

        public App(String sourceId, String displayName, String packagePrefix) {
            this(sourceId, displayName, packagePrefix, true);
        }

        private App(String sourceId, String displayName, String packagePrefix, boolean known) {
            this.sourceId = sourceId;
            this.displayName = displayName;
            this.packagePrefix = packagePrefix;
            this.known = known;
        }
    }
}
