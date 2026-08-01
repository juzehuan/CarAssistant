package com.carassistant.lyrics;

/**
 * 歌词源接口（1:1 复刻自歌词伴侣各 LyricClient）。
 *
 * 所有源返回统一的 {@link LyricResult}，由 {@link MultiSourceLyricClient} 串行调度。
 */
public interface LyricSource {

    /** 源 ID（如 "netease"、"qqmusic"） */
    String id();

    /** 源中文名（如 "网易云音乐"） */
    String displayName();

    /**
     * 加载歌词。
     *
     * @param mediaId  媒体 ID（仅 NetEase/Soda 支持直查，其余传空串）
     * @param title    歌名
     * @param artist   歌手
     * @param durationMs 时长（毫秒）
     * @return 歌词结果，失败返回 {@link LyricResult#EMPTY}
     */
    LyricResult load(String mediaId, String title, String artist, long durationMs);
}
