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
 * 汽水音乐歌词源（1:1 复刻自歌词伴侣 SodaLyricClient）。
 *
 * 搜索：GET https://api.qishui.com/luna/pc/search/track?q=<kw>&cursor=0&search_method=input&aid=386088&device_platform=web&channel=pc_web
 * 详情：GET https://api.qishui.com/luna/pc/track_v2?track_id=<id>&media_type=track&aid=386088&device_platform=web&channel=pc_web
 *
 * 支持：逐字（自有格式）+ 翻译（cn/zh-Hans/zh_CN/zh）
 */
public final class SodaLyricSource implements LyricSource {

    private static final String TAG = "SodaLyricSource";
    private static final String SEARCH_URL = "https://api.qishui.com/luna/pc/search/track";
    private static final String LYRIC_URL = "https://api.qishui.com/luna/pc/track_v2";
    private static final String REFERER = "https://www.qishui.com/";
    private static final String AID = "386088";
    private static final Pattern TRACK_ID = Pattern.compile("(?:^|\\D)(\\d{6,})(?:$|\\D)");
    private static final Pattern TIMED_LINE = Pattern.compile("^\\[(\\d+),(\\d+)](.*)$");
    private static final Pattern TIMED_WORD = Pattern.compile("<(\\d+),(\\d+),(\\d+)>");

    private final LyricCache cache;

    public SodaLyricSource(Context context) {
        this.cache = new LyricCache(context, "soda");
    }

    @Override public String id() { return "soda"; }
    @Override public String displayName() { return "汽水音乐"; }

    @Override
    public LyricResult load(String mediaId, String title, String artist, long durationMs) {
        try {
            String trackId = resolveTrackId(mediaId, title, artist, durationMs);
            if (trackId.isEmpty()) return LyricResult.EMPTY;

            String cachedEnhanced = cache.read(trackId + "_enhanced");
            String cachedPlain = cache.read(trackId + "_original");
            String cachedTrans = cache.read(trackId + "_translated");
            String checked = cache.read(trackId + "_checked");
            if (checked != null && (cachedEnhanced != null || cachedPlain != null)) {
                LrcTimeline tl = LrcTimeline.parse(
                        cachedPlain == null ? "" : cachedPlain,
                        cachedTrans == null ? "" : cachedTrans,
                        cachedEnhanced == null ? "" : cachedEnhanced);
                if (!tl.isEmpty()) return new LyricResult(id(), displayName(), tl);
            }

            JSONObject lyricObj = fetchLyric(trackId);
            if (lyricObj == null) return LyricResult.EMPTY;

            String content = lyricObj.optString("content", "");
            String enhanced = toEnhancedTiming(content);
            String plainLrc = toPlainLrc(content);
            String trans = translation(lyricObj.optJSONObject("translations"));

            cache.write(trackId + "_enhanced", enhanced);
            cache.write(trackId + "_original", plainLrc);
            cache.write(trackId + "_translated", trans);
            cache.write(trackId + "_checked", "1");

            LrcTimeline tl = LrcTimeline.parse(plainLrc, trans, enhanced);
            if (tl.isEmpty()) return LyricResult.EMPTY;
            return new LyricResult(id(), displayName(), tl);
        } catch (Exception e) {
            Log.w(TAG, "load failed: " + title + " - " + artist, e);
            return LyricResult.EMPTY;
        }
    }

    private String resolveTrackId(String mediaId, String title, String artist, long durationMs) {
        if (mediaId != null && !mediaId.isEmpty()) {
            Matcher m = TRACK_ID.matcher(mediaId);
            if (m.find()) return m.group(1);
        }
        return searchTrackId(title, artist, durationMs);
    }

    private String searchTrackId(String title, String artist, long durationMs) {
        try {
            String url = SEARCH_URL + "?q=" + HttpCompat.encode(title + " " + artist) +
                    "&cursor=0&search_method=input&aid=" + AID +
                    "&device_platform=web&channel=pc_web";
            Map<String, String> headers = new HashMap<>();
            headers.put("Referer", REFERER);
            String resp = HttpCompat.get(url, headers);
            JSONObject root = new JSONObject(resp);
            JSONArray groups = root.optJSONArray("result_groups");
            if (groups == null) return "";

            int bestScore = -1;
            String bestId = "";
            for (int i = 0; i < groups.length(); i++) {
                JSONObject group = groups.optJSONObject(i);
                if (group == null) continue;
                JSONArray data = group.optJSONArray("data");
                if (data == null) continue;
                for (int j = 0; j < data.length(); j++) {
                    JSONObject item = data.optJSONObject(j);
                    if (item == null) continue;
                    JSONObject track = item.optJSONObject("entity");
                    if (track == null) continue;
                    track = track.optJSONObject("track");
                    if (track == null) continue;
                    String name = track.optString("name", "");
                    long dur = track.optLong("duration", 0);
                    String art = artists(track.optJSONArray("artists"));
                    int score = LyricMatchScore.matchScore(title, artist, durationMs, name, art, dur);
                    if (score > bestScore) {
                        bestScore = score;
                        bestId = track.optString("id", "");
                    }
                }
            }
            return LyricMatchScore.isHit(bestScore) ? bestId : "";
        } catch (Exception e) {
            Log.w(TAG, "search failed", e);
            return "";
        }
    }

    private JSONObject fetchLyric(String trackId) {
        try {
            String url = LYRIC_URL + "?track_id=" + HttpCompat.encode(trackId) +
                    "&media_type=track&aid=" + AID +
                    "&device_platform=web&channel=pc_web";
            Map<String, String> headers = new HashMap<>();
            headers.put("Referer", REFERER);
            String resp = HttpCompat.get(url, headers);
            JSONObject root = new JSONObject(resp);
            return root.optJSONObject("lyric");
        } catch (Exception e) {
            Log.w(TAG, "fetchLyric failed: " + trackId, e);
            return null;
        }
    }

    private static String artists(JSONArray arr) {
        if (arr == null) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < arr.length(); i++) {
            JSONObject a = arr.optJSONObject(i);
            if (a != null) {
                if (sb.length() > 0) sb.append(", ");
                sb.append(a.optString("name", ""));
            }
        }
        return sb.toString();
    }

    /** 汽水自有格式转统一逐字格式：<offset,dur,idx> → (绝对起始,dur,idx) */
    static String toEnhancedTiming(String content) {
        if (content == null || content.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (String raw : content.split("\\r?\\n")) {
            Matcher lm = TIMED_LINE.matcher(raw);
            if (!lm.matches()) continue;
            long lineStart = Long.parseLong(lm.group(1));
            String body = lm.group(3);
            sb.append('[').append(lineStart).append(',').append(lm.group(2)).append(']');
            Matcher wm = TIMED_WORD.matcher(body);
            int lastEnd = 0;
            while (wm.find()) {
                long offset = Long.parseLong(wm.group(1));
                long dur = Long.parseLong(wm.group(2));
                long absStart = lineStart + offset;
                sb.append('(').append(absStart).append(',').append(dur).append(",0)");
                sb.append(body, lastEnd, wm.start());
                lastEnd = wm.end();
            }
            sb.append('\n');
        }
        return sb.toString();
    }

    static String toPlainLrc(String content) {
        if (content == null || content.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (String raw : content.split("\\r?\\n")) {
            Matcher lm = TIMED_LINE.matcher(raw);
            if (!lm.matches()) continue;
            long start = Long.parseLong(lm.group(1));
            String text = TIMED_WORD.matcher(lm.group(3)).replaceAll("");
            sb.append(String.format(Locale.ROOT, "[%02d:%02d.%03d]",
                    start / 60000, (start / 1000) % 60, start % 1000));
            sb.append(text.trim()).append('\n');
        }
        return sb.toString();
    }

    private static String translation(JSONObject translations) {
        if (translations == null) return "";
        String[] keys = {"cn", "zh-Hans", "zh_CN", "zh"};
        for (String k : keys) {
            String v = translations.optString(k, "");
            if (!v.isEmpty()) return v;
        }
        return "";
    }
}
