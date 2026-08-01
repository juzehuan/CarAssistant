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

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 网易云音乐歌词源（1:1 复刻自歌词伴侣 NetEaseLyricClient）。
 *
 * 搜索：POST https://music.163.com/api/search/get/web
 * 详情：GET  https://music.163.com/api/song/lyric?id=<songId>&lv=-1&kv=-1&tv=-1&yv=-1&rv=-1
 *
 * 支持：主歌词 LRC + 翻译 LRC（tlyric）+ 逐字 YRC（yrc）
 */
public final class NeteaseLyricSource implements LyricSource {

    private static final String TAG = "NeteaseLyricSource";
    private static final String SEARCH_URL = "https://music.163.com/api/search/get/web";
    private static final String LYRIC_URL = "https://music.163.com/api/song/lyric";
    private static final Pattern SONG_ID_PATTERN = Pattern.compile("(\\d{4,})");

    private final LyricCache cache;
    /** NetEase 使用独立 JSON 缓存目录 */
    private final File neteaseCacheDir;

    public NeteaseLyricSource(Context context) {
        this.cache = new LyricCache(context, "netease");
        this.neteaseCacheDir = new File(context.getCacheDir(), "netease_lyrics_v2");
    }

    @Override public String id() { return "netease"; }
    @Override public String displayName() { return "网易云音乐"; }

    @Override
    public LyricResult load(String mediaId, String title, String artist, long durationMs) {
        try {
            String songId = resolveSongId(mediaId, title, artist, durationMs);
            if (songId.isEmpty()) return LyricResult.EMPTY;

            String json = readJsonCache(songId);
            if (json == null) {
                json = fetchLyricJson(songId);
                if (json == null || json.isEmpty()) return LyricResult.EMPTY;
                writeJsonCache(songId, json);
            }

            JSONObject root = new JSONObject(json);
            String main = lyricValue(root.optJSONObject("lrc"));
            String trans = lyricValue(root.optJSONObject("tlyric"));
            String yrc = lyricValue(root.optJSONObject("yrc"));
            LrcTimeline timeline = LrcTimeline.parse(main, trans, yrc);
            if (timeline.isEmpty()) return LyricResult.EMPTY;
            return new LyricResult(id(), displayName(), timeline);
        } catch (Exception e) {
            Log.w(TAG, "load failed: " + title + " - " + artist, e);
            return LyricResult.EMPTY;
        }
    }

    private String resolveSongId(String mediaId, String title, String artist, long durationMs) {
        // 直查 ID
        if (mediaId != null && !mediaId.isEmpty()) {
            Matcher m = SONG_ID_PATTERN.matcher(mediaId);
            if (m.find()) {
                String sid = m.group(1);
                // 验证 ID 有效性（拉取一次）
                String json = fetchLyricJson(sid);
                if (json != null && !json.isEmpty()) return sid;
            }
        }
        // 搜索
        return searchSongId(title, artist, durationMs);
    }

    private String searchSongId(String title, String artist, long durationMs) {
        try {
            String body = "s=" + HttpCompat.encode(title + " " + artist) +
                    "&type=1&limit=10&offset=0";
            Map<String, String> headers = new HashMap<>();
            headers.put("Referer", "https://music.163.com/");
            headers.put("Accept", "application/json");
            String resp = HttpCompat.post(SEARCH_URL, body,
                    "application/x-www-form-urlencoded; charset=UTF-8", headers);
            JSONObject root = new JSONObject(resp);
            JSONObject result = root.optJSONObject("result");
            if (result == null) return "";
            JSONArray songs = result.optJSONArray("songs");
            if (songs == null) return "";

            int bestScore = -1;
            String bestId = "";
            for (int i = 0; i < songs.length(); i++) {
                JSONObject song = songs.optJSONObject(i);
                if (song == null) continue;
                String name = song.optString("name", "");
                long dur = song.optLong("duration", 0);
                String art = "";
                JSONArray artists = song.optJSONArray("artists");
                if (artists != null && artists.length() > 0) {
                    art = artists.optJSONObject(0).optString("name", "");
                }
                int score = LyricMatchScore.matchScore(title, artist, durationMs, name, art, dur);
                if (score > bestScore) {
                    bestScore = score;
                    bestId = String.valueOf(song.optLong("id", -1));
                }
            }
            return LyricMatchScore.isHit(bestScore) ? bestId : "";
        } catch (Exception e) {
            Log.w(TAG, "search failed", e);
            return "";
        }
    }

    private String fetchLyricJson(String songId) {
        try {
            String url = LYRIC_URL + "?id=" + songId +
                    "&lv=-1&kv=-1&tv=-1&yv=-1&rv=-1";
            Map<String, String> headers = new HashMap<>();
            headers.put("Referer", "https://music.163.com/");
            headers.put("Accept", "application/json");
            return HttpCompat.get(url, headers);
        } catch (Exception e) {
            Log.w(TAG, "fetchLyricJson failed: " + songId, e);
            return null;
        }
    }

    private static String lyricValue(JSONObject obj) {
        return obj == null ? "" : obj.optString("lyric", "");
    }

    private String readJsonCache(String songId) {
        if (!neteaseCacheDir.isDirectory()) return null;
        File f = new File(neteaseCacheDir, songId + ".json");
        if (!f.isFile()) return null;
        if (System.currentTimeMillis() - f.lastModified() > 2_592_000_000L) return null;
        try {
            java.io.RandomAccessFile raf = new java.io.RandomAccessFile(f, "r");
            try {
                int len = (int) Math.min(f.length(), 2_000_000L);
                byte[] buf = new byte[len];
                int read = 0;
                while (read < len) {
                    int n = raf.read(buf, read, len - read);
                    if (n < 0) break;
                    read += n;
                }
                return new String(buf, 0, read, java.nio.charset.StandardCharsets.UTF_8);
            } finally {
                raf.close();
            }
        } catch (Exception e) {
            return null;
        }
    }

    private void writeJsonCache(String songId, String json) {
        if (json == null || json.isEmpty()) return;
        if (!neteaseCacheDir.isDirectory() && !neteaseCacheDir.mkdirs()) return;
        try {
            java.io.FileOutputStream fos = new java.io.FileOutputStream(new File(neteaseCacheDir, songId + ".json"));
            try {
                fos.write(json.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            } finally {
                fos.close();
            }
        } catch (Exception ignored) {}
    }
}
