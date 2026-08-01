package com.carassistant.lyrics;

import android.graphics.Bitmap;

/**
 * 音乐快照（1:1 复刻自歌词伴侣 MusicSnapshot）。
 *
 * 一次性的不可变快照：包含会话状态、元数据、播放位置、歌词状态。
 * 由 {@link com.carassistant.util.MusicController} 构造，供 {@link LyricsPanelView} 绘制使用。
 */
public final class MusicSnapshot {

    public static final MusicSnapshot EMPTY = new MusicSnapshot(
            false, false, "音乐播放器", "", "", null,
            -1L, 0L, false, false, "", LrcTimeline.At.EMPTY);

    public final boolean active;
    public final boolean playing;
    public final String sourceName;
    public final String title;
    public final String artist;
    public final Bitmap albumArt;
    public final long durationMs;
    public final long positionMs;
    public final boolean lyricLoaded;
    public final boolean lyricAvailable;
    public final String lyricSourceName;
    public final LrcTimeline.At lyrics;

    public MusicSnapshot(boolean active, boolean playing, String sourceName,
                          String title, String artist, Bitmap albumArt,
                          long durationMs, long positionMs,
                          boolean lyricLoaded, boolean lyricAvailable,
                          String lyricSourceName, LrcTimeline.At lyrics) {
        this.active = active;
        this.playing = playing;
        this.sourceName = sourceName == null ? "" : sourceName;
        this.title = title == null ? "" : title;
        this.artist = artist == null ? "" : artist;
        this.albumArt = albumArt;
        this.durationMs = durationMs;
        this.positionMs = positionMs;
        this.lyricLoaded = lyricLoaded;
        this.lyricAvailable = lyricAvailable;
        this.lyricSourceName = lyricSourceName == null ? "" : lyricSourceName;
        this.lyrics = lyrics == null ? LrcTimeline.At.EMPTY : lyrics;
    }
}
