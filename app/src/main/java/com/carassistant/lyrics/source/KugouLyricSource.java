package com.carassistant.lyrics.source;

import android.content.Context;
import android.util.Log;

import com.carassistant.lyrics.HttpCompat;
import com.carassistant.lyrics.KrcLyricCodec;
import com.carassistant.lyrics.LrcTimeline;
import com.carassistant.lyrics.LyricCache;
import com.carassistant.lyrics.LyricMatchScore;
import com.carassistant.lyrics.LyricResult;
import com.carassistant.lyrics.LyricSource;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

/**
 * 酷狗音乐歌词源（1:1 复刻自歌词伴侣 KugouLyricClient）。
 *
 * 搜索：GET https://songsearch.kugou.com/song_search_v2?keyword=<kw>&page=1&pagesize=10
 * 详情：GET https://lyrics.kugou.com/download?ver=1&client=pc&id=<hash>&fmt=krc&charset=utf8
 *
 * 支持：KRC 逐字（XOR 加密）+ 翻译 JSON
 */
public final class KugouLyricSource implements LyricSource {

    private static final String TAG = "KugouLyricSource";
    private static final String SEARCH_URL = "https://songsearch.kugou.com/song_search_v2";
    private static final String LYRIC_URL = "https://lyrics.kugou.com/download";
    private static final String REFERER = "https://www.kugou.com/";

    private final LyricCache cache;

    public KugouLyricSource(Context context) {
        this.cache = new LyricCache(context, "kugou");
    }

    @Override public String id() { return "kugou"; }
    @Override public String displayName() { return "酷狗音乐"; }

    @Override
    public LyricResult load(String mediaId, String title, String artist, long durationMs) {
        try {
            String hash = searchHash(title, artist, durationMs);
            if (hash.isEmpty()) return LyricResult.EMPTY;

            String cachedEnhanced = cache.read(hash + "_enhanced");
            String cachedPlain = cache.read(hash + "_original");
            String cachedTrans = cache.read(hash + "_translated");
            if (cachedEnhanced != null || cachedPlain != null) {
                LrcTimeline tl = LrcTimeline.parse(
                        cachedPlain == null ? "" : cachedPlain,
                        cachedTrans == null ? "" : cachedTrans,
                        cachedEnhanced == null ? "" : cachedEnhanced);
                if (!tl.isEmpty()) return new LyricResult(id(), displayName(), tl);
            }

            String krcContent = fetchKrc(hash);
            if (krcContent == null || krcContent.isEmpty()) return LyricResult.EMPTY;

            String enhanced = KrcLyricCodec.toEnhancedTimeline(krcContent);
            String plainLrc = krcToPlainLrc(krcContent);
            // 翻译需单独接口，此处省略（酷狗翻译接口需 hash+id，且不稳定）
            String trans = "";

            cache.write(hash + "_enhanced", enhanced);
            cache.write(hash + "_original", plainLrc);
            cache.write(hash + "_translated", trans);

            LrcTimeline tl = LrcTimeline.parse(plainLrc, trans, enhanced);
            if (tl.isEmpty()) return LyricResult.EMPTY;
            return new LyricResult(id(), displayName(), tl);
        } catch (Exception e) {
            Log.w(TAG, "load failed: " + title + " - " + artist, e);
            return LyricResult.EMPTY;
        }
    }

    private String searchHash(String title, String artist, long durationMs) {
        try {
            String url = SEARCH_URL + "?keyword=" + HttpCompat.encode(title + " " + artist) +
                    "&page=1&pagesize=10&userid=-1&clientver=&platform=WebFilter";
            Map<String, String> headers = new HashMap<>();
            headers.put("Referer", REFERER);
            String resp = HttpCompat.get(url, headers);
            JSONObject root = new JSONObject(resp);
            JSONObject data = root.optJSONObject("data");
            if (data == null) return "";
            JSONArray lists = data.optJSONArray("lists");
            if (lists == null) return "";

            int bestScore = -1;
            String bestHash = "";
            for (int i = 0; i < lists.length(); i++) {
                JSONObject item = lists.optJSONObject(i);
                if (item == null) continue;
                String name = item.optString("SongName", "");
                long dur = item.optLong("Duration", 0) * 1000L;
                String art = item.optString("SingerName", "");
                int score = LyricMatchScore.matchScore(title, artist, durationMs, name, art, dur);
                if (score > bestScore) {
                    bestScore = score;
                    bestHash = item.optString("FileHash", "");
                }
            }
            return LyricMatchScore.isHit(bestScore) ? bestHash : "";
        } catch (Exception e) {
            Log.w(TAG, "search failed", e);
            return "";
        }
    }

    private String fetchKrc(String hash) {
        try {
            String url = LYRIC_URL + "?ver=1&client=pc&id=" + hash + "&fmt=krc&charset=utf8";
            Map<String, String> headers = new HashMap<>();
            headers.put("Referer", REFERER);
            String resp = HttpCompat.get(url, headers);
            JSONObject root = new JSONObject(resp);
            String content = root.optString("content", "");
            if (content.isEmpty()) return "";
            byte[] krcBytes = Base64.getDecoder().decode(content);
            return KrcLyricCodec.decrypt(krcBytes);
        } catch (Exception e) {
            Log.w(TAG, "fetchKrc failed: " + hash, e);
            return null;
        }
    }

    /** KRC 明文转普通 LRC（去 word 标签，时间戳转 mm:ss.xxx） */
    private static String krcToPlainLrc(String krc) {
        if (krc == null || krc.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        java.util.regex.Pattern LINE = java.util.regex.Pattern.compile("^\\[(\\d+)\\s*,\\s*(\\d+)](.*)$");
        java.util.regex.Pattern WORD = java.util.regex.Pattern.compile("<\\d+\\s*,\\s*\\d+\\s*,\\s*\\d+>");
        for (String raw : krc.split("\\r?\\n")) {
            java.util.regex.Matcher lm = LINE.matcher(raw);
            if (!lm.matches()) continue;
            long start = Long.parseLong(lm.group(1));
            String text = WORD.matcher(lm.group(3)).replaceAll("");
            sb.append(String.format(java.util.Locale.ROOT, "[%02d:%02d.%03d]",
                    start / 60000, (start / 1000) % 60, start % 1000));
            sb.append(text.trim()).append('\n');
        }
        return sb.toString();
    }
}
