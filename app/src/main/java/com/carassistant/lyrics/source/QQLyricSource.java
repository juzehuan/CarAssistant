package com.carassistant.lyrics.source;

import android.content.Context;
import android.util.Log;

import com.carassistant.lyrics.HttpCompat;
import com.carassistant.lyrics.LrcTimeline;
import com.carassistant.lyrics.LyricCache;
import com.carassistant.lyrics.LyricMatchScore;
import com.carassistant.lyrics.LyricResult;
import com.carassistant.lyrics.LyricSource;
import com.carassistant.lyrics.QrcLyricCodec;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;

/**
 * QQ 音乐歌词源（1:1 复刻自歌词伴侣 QQMusicLyricClient）。
 *
 * 搜索：GET https://c.y.qq.com/soso/fcgi-bin/client_search_cp?p=1&n=10&w=<kw>&format=json
 * 详情：GET https://c.y.qq.com/qqmusic/fcgi-bin/lyric_download.fcg?songmid=<mid>&qrc_t=1&g_tk=5381&format=json
 *
 * 支持：QRC 逐字（3DES 加密）+ 翻译（trans 标签）
 */
public final class QQLyricSource implements LyricSource {

    private static final String TAG = "QQLyricSource";
    private static final String SEARCH_URL = "https://c.y.qq.com/soso/fcgi-bin/client_search_cp";
    private static final String LYRIC_URL = "https://c.y.qq.com/qqmusic/fcgi-bin/lyric_download.fcg";
    private static final String REFERER = "https://y.qq.com/";

    private final LyricCache cache;

    public QQLyricSource(Context context) {
        this.cache = new LyricCache(context, "qq");
    }

    @Override public String id() { return "qqmusic"; }
    @Override public String displayName() { return "QQ 音乐"; }

    @Override
    public LyricResult load(String mediaId, String title, String artist, long durationMs) {
        try {
            String songMid = searchSongMid(title, artist, durationMs);
            if (songMid.isEmpty()) return LyricResult.EMPTY;

            // 缓存
            String cachedEnhanced = cache.read(songMid + "_enhanced");
            String cachedPlain = cache.read(songMid + "_original");
            String cachedTrans = cache.read(songMid + "_translated");
            if (cachedEnhanced != null || cachedPlain != null) {
                LrcTimeline tl = LrcTimeline.parse(
                        cachedPlain == null ? "" : cachedPlain,
                        cachedTrans == null ? "" : cachedTrans,
                        cachedEnhanced == null ? "" : cachedEnhanced);
                if (!tl.isEmpty()) return new LyricResult(id(), displayName(), tl);
            }

            String xml = fetchLyricXml(songMid);
            if (xml == null || xml.isEmpty()) return LyricResult.EMPTY;

            // 提取原文 QRC
            String originalQrc = QrcLyricCodec.encryptedContent(xml, "lyric");
            if (originalQrc.isEmpty()) {
                // 尝试直接解密
                originalQrc = xml;
            }
            String plainLrc;
            String enhanced;
            try {
                String decrypted = QrcLyricCodec.decryptTimedPayload(originalQrc);
                plainLrc = QrcLyricCodec.toPlainLrc(decrypted);
                enhanced = QrcLyricCodec.toEnhancedTimeline(decrypted);
            } catch (Exception e) {
                Log.w(TAG, "QRC decrypt failed", e);
                plainLrc = "";
                enhanced = "";
            }

            // 翻译
            String transQrc = QrcLyricCodec.encryptedContent(xml, "trans");
            String transLrc = "";
            if (!transQrc.isEmpty()) {
                try {
                    transLrc = QrcLyricCodec.decryptTimedPayload(transQrc);
                } catch (Exception e) {
                    transLrc = "";
                }
            }

            cache.write(songMid + "_enhanced", enhanced);
            cache.write(songMid + "_original", plainLrc);
            cache.write(songMid + "_translated", transLrc);

            LrcTimeline tl = LrcTimeline.parse(plainLrc, transLrc, enhanced);
            if (tl.isEmpty()) return LyricResult.EMPTY;
            return new LyricResult(id(), displayName(), tl);
        } catch (Exception e) {
            Log.w(TAG, "load failed: " + title + " - " + artist, e);
            return LyricResult.EMPTY;
        }
    }

    private String searchSongMid(String title, String artist, long durationMs) {
        try {
            String url = SEARCH_URL + "?p=1&n=10&w=" + HttpCompat.encode(title + " " + artist) + "&format=json";
            Map<String, String> headers = new HashMap<>();
            headers.put("Referer", REFERER);
            String resp = HttpCompat.get(url, headers);
            JSONObject root = new JSONObject(resp);
            JSONObject data = root.optJSONObject("data");
            if (data == null) return "";
            JSONObject songObj = data.optJSONObject("song");
            if (songObj == null) return "";
            JSONArray songs = songObj.optJSONArray("list");
            if (songs == null) return "";

            int bestScore = -1;
            String bestMid = "";
            for (int i = 0; i < songs.length(); i++) {
                JSONObject song = songs.optJSONObject(i);
                if (song == null) continue;
                String name = song.optString("songname", "");
                long dur = song.optLong("interval", 0) * 1000L;
                String art = "";
                JSONArray singers = song.optJSONArray("singer");
                if (singers != null && singers.length() > 0) {
                    art = singers.optJSONObject(0).optString("name", "");
                }
                int score = LyricMatchScore.matchScore(title, artist, durationMs, name, art, dur);
                if (score > bestScore) {
                    bestScore = score;
                    bestMid = song.optString("songmid", "");
                }
            }
            return LyricMatchScore.isHit(bestScore) ? bestMid : "";
        } catch (Exception e) {
            Log.w(TAG, "search failed", e);
            return "";
        }
    }

    private String fetchLyricXml(String songMid) {
        try {
            String url = LYRIC_URL + "?songmid=" + songMid +
                    "&qrc_t=1&g_tk=5381&format=json&inCharset=utf-8&outCharset=utf-8";
            Map<String, String> headers = new HashMap<>();
            headers.put("Referer", REFERER);
            return HttpCompat.get(url, headers);
        } catch (Exception e) {
            Log.w(TAG, "fetchLyricXml failed: " + songMid, e);
            return null;
        }
    }
}
