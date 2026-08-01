package com.carassistant.lyrics;

/**
 * 歌词获取结果（1:1 复刻自歌词伴侣 MultiSourceLyricClient.Result）。
 *
 * 携带构建好的 {@link LrcTimeline} 与源信息。
 */
public final class LyricResult {

    public static final LyricResult EMPTY = new LyricResult("", "", LrcTimeline.EMPTY);

    public final String providerId;
    public final String providerName;
    public final LrcTimeline timeline;

    public LyricResult(String providerId, String providerName, LrcTimeline timeline) {
        this.providerId = providerId == null ? "" : providerId;
        this.providerName = providerName == null ? "" : providerName;
        this.timeline = timeline == null ? LrcTimeline.EMPTY : timeline;
    }

    public boolean isEmpty() {
        return timeline == null || timeline.isEmpty();
    }
}
