package com.carassistant.lyrics.source;

import android.content.Context;
import android.util.Log;

import com.carassistant.lyrics.HttpCompat;
import com.carassistant.lyrics.LrcTimeline;
import com.carassistant.lyrics.LyricCache;
import com.carassistant.lyrics.LyricMatchScore;
import com.carassistant.lyrics.LyricResult;
import com.carassistant.lyrics.LyricSource;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 酷我音乐歌词源（1:1 复刻自歌词伴侣 KuwoLyricClient）。
 *
 * 搜索：GET https://search.kuwo.cn/r.s?all=<kw>&ft=music&itemset=web_2013&client=kt&pn=0&rn=10&rformat=json&encoding=utf8
 * 详情：GET https://m.kuwo.cn/newh5/singles/songinfoandlrc?musicId=<id>
 *
 * 仅支持行级 LRC（无逐字、无翻译）。
 */
public final class KuwoLyricSource implements LyricSource {

    private static final String TAG = "KuwoLyricSource";
    private static final String SEARCH_URL = "https://search.kuwo.cn/r.s";
    private static final String LYRIC_URL = "https://m.kuwo.cn/newh5/singles/songinfoandlrc";
    private static final String REFERER = "https://www.kuwo.cn/";

    private static final Pattern SEARCH_ITEM = Pattern.compile(
            "'ARTIST':'([^']*)'.*?'DURATION':'(\\d+)'.*?'MUSICRID':'MUSIC_(\\d+)'.*?'NAME':'([^']*)'",
            Pattern.DOTALL);

    private final LyricCache cache;

    public KuwoLyricSource(Context context) {
        this.cache = new LyricCache(context, "kuwo");
    }

    @Override public String id() { return "kuwo"; }
    @Override public String displayName() { return "酷我音乐"; }

    @Override
    public LyricResult load(String mediaId, String title, String artist, long durationMs) {
        try {
            String songId = searchSongId(title, artist, durationMs);
            if (songId.isEmpty()) return LyricResult.EMPTY;

            String cached = cache.read(songId);
            if (cached != null) {
                LrcTimeline tl = LrcTimeline.parse(cached);
                if (!tl.isEmpty()) return new LyricResult(id(), displayName(), tl);
            }

            String lrc = fetchLrc(songId);
            if (lrc.isEmpty()) return LyricResult.EMPTY;
            cache.write(songId, lrc);
            LrcTimeline tl = LrcTimeline.parse(lrc);
            if (tl.isEmpty()) return LyricResult.EMPTY;
            return new LyricResult(id(), displayName(), tl);
        } catch (Exception e) {
            Log.w(TAG, "load failed: " + title + " - " + artist, e);
            return LyricResult.EMPTY;
        }
    }

    private String searchSongId(String title, String artist, long durationMs) {
        try {
            String url = SEARCH_URL + "?all=" + HttpCompat.encode(title + " " + artist) +
                    "&ft=music&itemset=web_2013&client=kt&pn=0&rn=10&rformat=json&encoding=utf8";
            Map<String, String> headers = new HashMap<>();
            headers.put("Referer", REFERER);
            String resp = HttpCompat.get(url, headers);

            int bestScore = -1;
            String bestId = "";
            Matcher m = SEARCH_ITEM.matcher(resp);
            while (m.find()) {
                String art = decodeLegacy(m.group(1));
                long dur = 0;
                try { dur = Long.parseLong(m.group(2)) * 1000L; } catch (Exception ignored) {}
                String id = m.group(3);
                String name = decodeLegacy(m.group(4));
                int score = LyricMatchScore.matchScore(title, artist, durationMs, name, art, dur);
                if (score > bestScore) {
                    bestScore = score;
                    bestId = id;
                }
            }
            return LyricMatchScore.isHit(bestScore) ? bestId : "";
        } catch (Exception e) {
            Log.w(TAG, "search failed", e);
            return "";
        }
    }

    private String fetchLrc(String songId) {
        try {
            String url = LYRIC_URL + "?musicId=" + HttpCompat.encode(songId);
            Map<String, String> headers = new HashMap<>();
            headers.put("Referer", REFERER);
            String resp = HttpCompat.get(url, headers);
            JSONObject root = new JSONObject(resp);
            JSONObject data = root.optJSONObject("data");
            if (data == null) return "";
            JSONArray lrclist = data.optJSONArray("lrclist");
            if (lrclist == null) return "";
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < lrclist.length(); i++) {
                JSONObject item = lrclist.optJSONObject(i);
                if (item == null) continue;
                double t = item.optDouble("time", -1);
                String line = item.optString("lineLyric", "").trim();
                if (t >= 0 && !line.isEmpty()) {
                    long round = Math.round(t * 100.0);
                    sb.append(String.format(Locale.ROOT, "[%02d:%02d.%02d]",
                            round / 6000, (round / 100) % 60, round % 60));
                    sb.append(line).append('\n');
                }
            }
            return sb.toString();
        } catch (Exception e) {
            Log.w(TAG, "fetchLrc failed: " + songId, e);
            return "";
        }
    }

    private static String decodeLegacy(String s) {
        if (s == null) return "";
        return s.replace("&nbsp;", " ")
                .replace("\\\\u0026", "&")
                .replace("\\u0026", "&")
                .trim();
    }
}
