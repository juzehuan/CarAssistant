package com.carassistant.lyrics;

/**
 * 歌词浏览状态工具（1:1 复刻自歌词伴侣 LyricsBrowseState）。
 *
 * 决定拖动浏览的起始位置：
 * - 若仍在浏览保持期内（browseUntilElapsedMs > now）：沿用上次的 browsePositionMs
 * - 否则：从当前实时播放位置开始浏览
 */
final class LyricsBrowseState {

    private LyricsBrowseState() {}

    /**
     * @param now                 当前 elapsedRealtime
     * @param browseUntilElapsedMs 浏览保持截止时刻（0 表示未在浏览）
     * @param browsePositionMs    上次的浏览位置
     * @param currentPositionMs   当前实时播放位置
     * @return 本次拖动浏览的起始位置
     */
    static long startingPosition(long now, long browseUntilElapsedMs,
                                  long browsePositionMs, long currentPositionMs) {
        return browseUntilElapsedMs > now
                ? Math.max(0L, browsePositionMs)
                : Math.max(0L, currentPositionMs);
    }
}
