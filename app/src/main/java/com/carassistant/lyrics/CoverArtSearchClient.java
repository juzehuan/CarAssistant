package com.carassistant.lyrics;

import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;

/**
 * 封面搜索客户端（1:1 复刻自歌词伴侣 CoverArtSearchClient）。
 *
 * 回退顺序：QQ → 酷狗 → 空
 * 评分阈值：>= 100
 */
public final class CoverArtSearchClient {

    private static final String TAG = "CoverArtSearchClient";

    private CoverArtSearchClient() {}

    public static String find(String title, String artist, long durationMs) {
        try {
            String qq = findOnQq(title, artist, durationMs);
            if (!qq.isEmpty()) return qq;
        } catch (Throwable t) {
            Log.w(TAG, "QQ cover failed", t);
        }
        try {
            return findOnKugou(title, artist, durationMs);
        } catch (Throwable t) {
            Log.w(TAG, "Kugou cover failed", t);
            return "";
        }
    }

    /** QQ 封面：https://y.gtimg.cn/music/photo_new/T002R500x500M000<albummid>.jpg */
    private static String findOnQq(String title, String artist, long durationMs) throws Exception {
        String url = "https://c.y.qq.com/soso/fcgi-bin/client_search_cp?p=1&n=10&w="
                + HttpCompat.encode(title + " " + artist) + "&format=json";
        Map<String, String> headers = new HashMap<>();
        headers.put("Referer", "https://y.qq.com/");
        String resp = HttpCompat.get(url, headers);
        JSONObject root = new JSONObject(resp);
        JSONObject data = root.optJSONObject("data");
        if (data == null) return "";
        JSONObject song = data.optJSONObject("song");
        if (song == null) return "";
        JSONArray list = song.optJSONArray("list");
        if (list == null) return "";

        int best = -1;
        String bestMid = "";
        for (int i = 0; i < list.length(); i++) {
            JSONObject s = list.optJSONObject(i);
            if (s == null) continue;
            String name = s.optString("songname", "");
            long dur = s.optLong("interval", 0) * 1000L;
            String art = "";
            JSONArray singers = s.optJSONArray("singer");
            if (singers != null && singers.length() > 0) {
                art = singers.optJSONObject(0).optString("name", "");
            }
            int score = LyricMatchScore.matchScore(title, artist, durationMs, name, art, dur);
            if (score > best) {
                best = score;
                bestMid = s.optString("albummid", "");
            }
        }
        if (!LyricMatchScore.isHit(best) || bestMid.isEmpty()) return "";
        return "https://y.gtimg.cn/music/photo_new/T002R500x500M000" + bestMid + ".jpg";
    }

    /** 酷狗封面：Image 字段含 {size} 占位符，替换为 500 */
    private static String findOnKugou(String title, String artist, long durationMs) throws Exception {
        String url = "https://songsearch.kugou.com/song_search_v2?keyword="
                + HttpCompat.encode(title + " " + artist)
                + "&page=1&pagesize=10&userid=-1&clientver=&platform=WebFilter";
        Map<String, String> headers = new HashMap<>();
        headers.put("Referer", "https://www.kugou.com/");
        String resp = HttpCompat.get(url, headers);
        JSONObject root = new JSONObject(resp);
        JSONObject data = root.optJSONObject("data");
        if (data == null) return "";
        JSONArray lists = data.optJSONArray("lists");
        if (lists == null) return "";

        int best = -1;
        String bestImg = "";
        for (int i = 0; i < lists.length(); i++) {
            JSONObject item = lists.optJSONObject(i);
            if (item == null) continue;
            String name = item.optString("SongName", "");
            long dur = item.optLong("Duration", 0) * 1000L;
            String art = item.optString("SingerName", "");
            int score = LyricMatchScore.matchScore(title, artist, durationMs, name, art, dur);
            if (score > best) {
                best = score;
                bestImg = item.optString("Image", "");
            }
        }
        if (!LyricMatchScore.isHit(best) || bestImg.isEmpty()) return "";
        return bestImg.replace("{size}", "500");
    }
}
