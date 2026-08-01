package com.carassistant.lyrics;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * 歌词匹配评分算法（1:1 复刻自歌词伴侣 NetEaseLyricClient.matchScore）。
 *
 * 所有歌词源共用此评分函数，命中阈值统一为 >= 100。
 *
 * 评分维度：
 * 1. 歌名（基准分，最重要）：完全相等 +100 / 包含 +55 / 不匹配 -80
 * 2. 歌手（累加）：完全相等 +70 / 包含 +45 / 不匹配 -45
 * 3. 时长（累加）：≤2s +35 / ≤5s +25 / ≤15s +5 / >15s -20
 * 4. 版本噪声惩罚：查询不带噪声但候选带噪声 -60
 */
public final class LyricMatchScore {

    /** 命中阈值：评分 >= 100 才视为匹配 */
    public static final int HIT_THRESHOLD = 100;

    private static final Pattern VERSION_NOISE = Pattern.compile(
            ".*(伴奏|翻唱|现场|升调|降调|live|remix|dj|sped|slowed|ktv).*",
            Pattern.CASE_INSENSITIVE);

    private LyricMatchScore() {}

    public static int matchScore(String queryTitle, String queryArtist, long queryDurationMs,
                                 String candTitle, String candArtist, long candDurationMs) {
        String qTitle = normalize(queryTitle);
        String qArtist = normalize(queryArtist);
        String cTitle = normalize(candTitle);
        String cArtist = normalize(candArtist);

        int score = 0;

        // 1. 歌名分
        if (qTitle.equals(cTitle)) {
            score += 100;
        } else if (!qTitle.isEmpty() && !cTitle.isEmpty()
                && (qTitle.contains(cTitle) || cTitle.contains(qTitle))) {
            score += 55;
        } else {
            score -= 80;
        }

        // 2. 歌手分
        if (!qArtist.isEmpty() && !cArtist.isEmpty()) {
            if (qArtist.equals(cArtist)) {
                score += 70;
            } else if (qArtist.contains(cArtist) || cArtist.contains(qArtist)) {
                score += 45;
            } else {
                score -= 45;
            }
        }

        // 3. 时长分
        if (queryDurationMs > 0 && candDurationMs > 0) {
            long diff = Math.abs(queryDurationMs - candDurationMs);
            if (diff <= 2000) score += 35;
            else if (diff <= 5000) score += 25;
            else if (diff <= 15000) score += 5;
            else score -= 20;
        }

        // 4. 版本噪声惩罚
        if (hasVersionNoise(queryTitle) || !hasVersionNoise(candTitle)) {
            return score;
        }
        return score - 60;
    }

    public static boolean isHit(int score) {
        return score >= HIT_THRESHOLD;
    }

    private static String normalize(String s) {
        if (s == null) return "";
        return s.toLowerCase(Locale.ROOT).replaceAll("[\\p{P}\\s]+", "");
    }

    private static boolean hasVersionNoise(String s) {
        if (s == null || s.isEmpty()) return false;
        return VERSION_NOISE.matcher(s.toLowerCase(Locale.ROOT)).matches();
    }
}
