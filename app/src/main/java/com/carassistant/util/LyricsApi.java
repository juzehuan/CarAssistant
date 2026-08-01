/*
 * Copyright (C) 2026 CarAssistant Project. All rights reserved.
 *
 * 版权所有 (C) 2026 车机助手项目
 * 保留所有权利
 *
 * 本源代码受著作权法保护，未经著作权人书面许可，不得以任何形式复制、修改、
 * 分发、出售或逆向工程。违反者将承担法律责任。
 *
 * Source code protected by copyright law. Unauthorized copying, modification,
 * distribution, sale, or reverse engineering without written permission is
 * prohibited and subject to legal action.
 */

package com.carassistant.util;

import android.content.Context;
import android.os.Environment;
import android.text.TextUtils;
import android.util.Log;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 在线歌词 API（Java 重写自鸿启桌面 Kotlin 版）
 *
 * 支持歌词源：
 * - AUTO（自动顺序尝试 QQ → 网易 → GeciMe → Lyrics.ovh）
 * - QQ 音乐（base64 解码）
 * - 网易云音乐（lrc.lyric 字段）
 * - GeciMe（result[0].lrc URL）
 * - Lyrics.ovh（lyrics 字段，纯文本转 LRC）
 * - 自定义 URL（{title}/{artist} 占位符）
 *
 * 调用方式：
 *   LyricsApi.getInstance().fetchLyrics(artist, title, context, new LyricsApi.Callback() {...});
 *
 * 设计要点：
 * - 用 ExecutorService 替代 Kotlin 协程，单线程池避免并发请求
 * - 缓存（LinkedHashMap，最多 50 项，LRU 策略）
 * - 失败回退：本地搜索 → 在线 API（按 apiSource 顺序）
 */
public final class LyricsApi {

    private static final String TAG = "LyricsApi";

    /** API 源常量 */
    public static final int API_SOURCE_AUTO = 0;
    public static final int API_SOURCE_QQ = 1;
    public static final int API_SOURCE_NETEASE = 2;
    public static final int API_SOURCE_GECIME = 3;
    public static final int API_SOURCE_OVH = 4;
    public static final int API_SOURCE_CUSTOM = 5;

    /** 缓存最大容量 */
    private static final int MAX_CACHE_SIZE = 50;

    private static volatile LyricsApi INSTANCE;

    private final ExecutorService io = Executors.newSingleThreadExecutor();
    /** LRU 缓存：key = "artist|title" */
    private final Map<String, String> lyricsCache = new LinkedHashMap<String, String>(16, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, String> eldest) {
            return size() > MAX_CACHE_SIZE;
        }
    };
    private final Object cacheLock = new Object();

    /** 配置项（由 MusicSettingsActivity 写入） */
    private volatile int apiSource = API_SOURCE_AUTO;
    private volatile boolean onlineEnabled = true;
    private volatile String customApiUrl = "";
    private volatile String lyricsDir = "";

    private LyricsApi() {}

    public static LyricsApi getInstance() {
        if (INSTANCE == null) {
            synchronized (LyricsApi.class) {
                if (INSTANCE == null) INSTANCE = new LyricsApi();
            }
        }
        return INSTANCE;
    }

    // ============ 配置 ============
    public void setApiSource(int source) { this.apiSource = source; }
    public int getApiSource() { return apiSource; }

    public void setOnlineEnabled(boolean enabled) { this.onlineEnabled = enabled; }
    public boolean isOnlineEnabled() { return onlineEnabled; }

    public void setCustomApiUrl(String url) { this.customApiUrl = url == null ? "" : url; }
    public String getCustomApiUrl() { return customApiUrl; }

    public void setLyricsDir(String dir) { this.lyricsDir = dir == null ? "" : dir; }
    public String getLyricsDir() { return lyricsDir; }

    public void clearCache() {
        synchronized (cacheLock) { lyricsCache.clear(); }
    }

    public int getCacheSize() {
        synchronized (cacheLock) { return lyricsCache.size(); }
    }

    // ============ 异步获取 ============

    /** 异步获取回调 */
    public interface Callback {
        /** 成功返回 LRC 文本（可能为空字符串表示无歌词） */
        void onSuccess(String lrcContent);
        /** 失败 */
        void onFailure(String error);
    }

    /**
     * 异步获取歌词
     * @param artist 歌手（可空）
     * @param title 歌曲名（必需）
     * @param ctx 上下文（用于本地搜索，可空）
     * @param callback 主线程回调（内部已切换到主线程）
     */
    public void fetchLyrics(final String artist, final String title, final Context ctx, final Callback callback) {
        if (TextUtils.isEmpty(title)) {
            callback.onFailure("title is empty");
            return;
        }
        io.execute(() -> {
            final String result = fetchLyricsSync(artist, title, ctx);
            if (result != null) {
                callback.onSuccess(result);
            } else {
                callback.onFailure("no lyrics found");
            }
        });
    }

    /** 同步获取（在 IO 线程调用） */
    private String fetchLyricsSync(String artist, String title, Context ctx) {
        String cacheKey = (artist == null ? "" : artist) + "|" + title;
        // 1. 缓存
        synchronized (cacheLock) {
            String cached = lyricsCache.get(cacheKey);
            if (cached != null) {
                Log.d(TAG, "lyrics cache hit: " + cacheKey);
                return cached;
            }
        }
        // 2. 本地搜索
        String local = findLocalLyrics(artist, title, ctx);
        if (!TextUtils.isEmpty(local)) {
            putCache(cacheKey, local);
            return local;
        }
        // 3. 在线
        if (!onlineEnabled) {
            Log.d(TAG, "online lyrics disabled");
            return null;
        }
        String online = null;
        try {
            switch (apiSource) {
                case API_SOURCE_QQ:
                    online = fetchFromQQMusic(title, artist);
                    break;
                case API_SOURCE_NETEASE:
                    online = fetchFromNetEase(title, artist);
                    break;
                case API_SOURCE_GECIME:
                    online = fetchFromGeciMe(title, artist);
                    break;
                case API_SOURCE_OVH:
                    online = fetchFromLyricsOvh(artist, title);
                    break;
                case API_SOURCE_CUSTOM:
                    online = fetchFromCustomApi(title, artist);
                    break;
                case API_SOURCE_AUTO:
                default:
                    // 顺序回退：QQ → 网易 → GeciMe → Lyrics.ovh
                    online = fetchFromQQMusic(title, artist);
                    if (TextUtils.isEmpty(online)) online = fetchFromNetEase(title, artist);
                    if (TextUtils.isEmpty(online)) online = fetchFromGeciMe(title, artist);
                    if (TextUtils.isEmpty(online)) online = fetchFromLyricsOvh(artist, title);
                    break;
            }
        } catch (Exception e) {
            Log.w(TAG, "fetchLyrics online failed", e);
        }
        if (!TextUtils.isEmpty(online)) {
            putCache(cacheKey, online);
        }
        return online;
    }

    private void putCache(String key, String value) {
        synchronized (cacheLock) { lyricsCache.put(key, value); }
    }

    // ============ 本地搜索 ============

    /** 在多个常见目录中查找本地 .lrc/.txt 文件 */
    private String findLocalLyrics(String artist, String title, Context ctx) {
        if (TextUtils.isEmpty(title)) return null;
        List<File> searchDirs = new ArrayList<>();
        if (!TextUtils.isEmpty(lyricsDir)) searchDirs.add(new File(lyricsDir));
        File ext = Environment.getExternalStorageDirectory();
        if (ext != null) {
            searchDirs.add(new File(ext, "Lyrics"));
            searchDirs.add(new File(ext, "Music/Lyrics"));
            searchDirs.add(new File(ext, "Download/Lyrics"));
            searchDirs.add(new File(ext, "Download"));
            searchDirs.add(new File(ext, "Music"));
            searchDirs.add(new File(ext, "歌曲"));
            searchDirs.add(new File(ext, "音乐"));
        }
        if (ctx != null) {
            searchDirs.add(new File(ctx.getFilesDir(), "lyrics"));
            searchDirs.add(new File(ctx.getCacheDir(), "lyrics"));
        }
        String[] exts = {".lrc", ".LRC", ".txt", ".TXT"};
        String normTitle = normalize(title);
        String normArtist = artist == null ? "" : normalize(artist);
        // 文件名候选
        List<String> nameCandidates = new ArrayList<>();
        if (!TextUtils.isEmpty(normArtist)) {
            nameCandidates.add(normTitle + "-" + normArtist);
            nameCandidates.add(normArtist + "-" + normTitle);
            nameCandidates.add(normTitle + "_" + normArtist);
            nameCandidates.add(normArtist + "_" + normTitle);
            nameCandidates.add(normTitle + " " + normArtist);
            nameCandidates.add(normArtist + " " + normTitle);
        }
        nameCandidates.add(normTitle);

        for (File dir : searchDirs) {
            if (dir == null || !dir.exists() || !dir.isDirectory()) continue;
            // 精确匹配
            for (String name : nameCandidates) {
                for (String e : exts) {
                    File f = new File(dir, name + e);
                    if (f.exists() && f.isFile() && f.canRead()) {
                        String content = readFileText(f);
                        if (!TextUtils.isEmpty(content)) return content;
                    }
                }
            }
            // 模糊匹配：列目录
            File[] files = dir.listFiles();
            if (files == null) continue;
            for (File f : files) {
                if (!f.isFile()) continue;
                String fn = f.getName().toLowerCase();
                if (!fn.endsWith(".lrc") && !fn.endsWith(".txt")) continue;
                String nameNoExt = normalize(f.getName().substring(0, f.getName().lastIndexOf('.')));
                if (nameNoExt.contains(normTitle) || (normTitle.contains(nameNoExt) && nameNoExt.length() > 2)) {
                    String content = readFileText(f);
                    if (!TextUtils.isEmpty(content)) return content;
                }
            }
        }
        return null;
    }

    /** 文件名规范化：去除空格/分隔符/标点 */
    private static String normalize(String s) {
        if (s == null) return "";
        return s.replaceAll("[\\s\\-_·.。，,、]", "")
                .replaceAll("[\\[\\]()（）【】]", "")
                .replaceAll("[\\\\/:*?\"<>|]", "")
                .trim()
                .toLowerCase();
    }

    /** 读取文件文本（UTF-8） */
    private static String readFileText(File f) {
        try (BufferedReader r = new BufferedReader(new InputStreamReader(new FileInputStream(f), "UTF-8"))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = r.readLine()) != null) {
                if (sb.length() > 0) sb.append("\n");
                sb.append(line);
            }
            return sb.toString();
        } catch (Exception e) {
            Log.w(TAG, "readFileText failed: " + f, e);
            return null;
        }
    }

    /** 扫描本地歌词文件列表（用于设置页展示） */
    public List<String> scanLocalLyrics(Context ctx) {
        List<String> result = new ArrayList<>();
        List<File> dirs = new ArrayList<>();
        if (!TextUtils.isEmpty(lyricsDir)) dirs.add(new File(lyricsDir));
        File ext = Environment.getExternalStorageDirectory();
        if (ext != null) {
            dirs.add(new File(ext, "Lyrics"));
            dirs.add(new File(ext, "Music/Lyrics"));
            dirs.add(new File(ext, "Download/Lyrics"));
            dirs.add(new File(ext, "Download"));
            dirs.add(new File(ext, "Music"));
        }
        if (ctx != null) {
            dirs.add(new File(ctx.getFilesDir(), "lyrics"));
            dirs.add(new File(ctx.getCacheDir(), "lyrics"));
        }
        for (File dir : dirs) {
            if (dir == null || !dir.exists() || !dir.isDirectory()) continue;
            File[] files = dir.listFiles();
            if (files == null) continue;
            for (File f : files) {
                if (!f.isFile()) continue;
                String fn = f.getName().toLowerCase();
                if (fn.endsWith(".lrc") || fn.endsWith(".txt")) {
                    result.add(f.getAbsolutePath());
                }
            }
        }
        return result;
    }

    // ============ 在线 API ============

    /** QQ 音乐：搜索 → 获取 songmid → 请求歌词（base64 解码） */
    private String fetchFromQQMusic(String title, String artist) {
        try {
            String searchUrl = "https://c.y.qq.com/soso/fcgi-bin/client_search_cp?w="
                    + URLEncoder.encode(title + " " + (artist == null ? "" : artist), "UTF-8")
                    + "&format=json&n=5&p=1";
            String resp = httpGet(searchUrl);
            if (TextUtils.isEmpty(resp)) return null;
            JSONObject json = new JSONObject(resp);
            JSONObject data = json.optJSONObject("data");
            if (data == null) return null;
            JSONObject song = data.optJSONObject("song");
            if (song == null) return null;
            org.json.JSONArray list = song.optJSONArray("list");
            if (list == null || list.length() == 0) return null;
            JSONObject first = list.optJSONObject(0);
            String songmid = first.optString("songmid", "");
            if (TextUtils.isEmpty(songmid)) return null;
            // 歌词接口
            String lrcUrl = "https://c.y.qq.com/lyric/fcgi-bin/fcg_query_lyric_new.fcg?songmid=" + songmid
                    + "&format=json&nobase64=0";
            String lrcResp = httpGetWithHeader(lrcUrl, "Referer", "https://y.qq.com");
            if (TextUtils.isEmpty(lrcResp)) return null;
            JSONObject lrcJson = new JSONObject(lrcResp);
            String base64 = lrcJson.optString("lyric", "");
            if (TextUtils.isEmpty(base64)) return null;
            byte[] decoded = android.util.Base64.decode(base64, android.util.Base64.DEFAULT);
            String lrc = new String(decoded, "UTF-8");
            return lrc.contains("[") ? lrc : null;
        } catch (Exception e) {
            Log.w(TAG, "QQ music lyrics failed", e);
            return null;
        }
    }

    /** 网易云音乐：搜索 → 获取歌曲 id → 请求 lyric 接口 */
    private String fetchFromNetEase(String title, String artist) {
        try {
            String searchUrl = "https://music.163.com/api/search/get?s="
                    + URLEncoder.encode(title + " " + (artist == null ? "" : artist), "UTF-8")
                    + "&type=1&offset=0&limit=5";
            String resp = httpGetWithHeader(searchUrl, "Referer", "https://music.163.com");
            if (TextUtils.isEmpty(resp)) return null;
            JSONObject json = new JSONObject(resp);
            JSONObject result = json.optJSONObject("result");
            if (result == null) return null;
            org.json.JSONArray songs = result.optJSONArray("songs");
            if (songs == null || songs.length() == 0) return null;
            long songId = songs.optJSONObject(0).optLong("id", -1);
            if (songId < 0) return null;
            String lrcUrl = "https://music.163.com/api/song/lyric?id=" + songId + "&lv=1&kv=1&tv=-1";
            String lrcResp = httpGetWithHeader(lrcUrl, "Referer", "https://music.163.com");
            if (TextUtils.isEmpty(lrcResp)) return null;
            JSONObject lrcJson = new JSONObject(lrcResp);
            JSONObject lrc = lrcJson.optJSONObject("lrc");
            if (lrc == null) return null;
            String content = lrc.optString("lyric", "");
            if (!content.contains("[")) return null;
            // 获取翻译歌词
            JSONObject tlyric = lrcJson.optJSONObject("tlyric");
            if (tlyric != null) {
                String trans = tlyric.optString("lyric", "");
                if (!TextUtils.isEmpty(trans)) {
                    content = mergeTranslation(content, trans);
                }
            }
            return content;
        } catch (Exception e) {
            Log.w(TAG, "NetEase lyrics failed", e);
            return null;
        }
    }

    /** 合并翻译歌词：相同时间标签的行用 " || " 连接 */
    private static String mergeTranslation(String mainLrc, String translationLrc) {
        if (TextUtils.isEmpty(mainLrc)) return mainLrc;
        if (TextUtils.isEmpty(translationLrc)) return mainLrc;
        java.util.Map<Long, String> transMap = new java.util.HashMap<>();
        java.util.regex.Pattern p = java.util.regex.Pattern.compile("\\[(\\d{2}):(\\d{2})\\.(\\d{2,3})\\](.*)");
        for (String line : translationLrc.split("\n")) {
            java.util.regex.Matcher m = p.matcher(line.trim());
            if (m.matches()) {
                try {
                    long ms = Long.parseLong(m.group(1)) * 60000
                            + Long.parseLong(m.group(2)) * 1000
                            + (m.group(3).length() == 2 ? Long.parseLong(m.group(3)) * 10 : Long.parseLong(m.group(3)));
                    String text = m.group(4) == null ? "" : m.group(4).trim();
                    if (!text.isEmpty()) transMap.put(ms, text);
                } catch (Exception ignored) {}
            }
        }
        if (transMap.isEmpty()) return mainLrc;
        StringBuilder sb = new StringBuilder();
        for (String line : mainLrc.split("\n")) {
            java.util.regex.Matcher m = p.matcher(line.trim());
            if (m.matches()) {
                try {
                    long ms = Long.parseLong(m.group(1)) * 60000
                            + Long.parseLong(m.group(2)) * 1000
                            + (m.group(3).length() == 2 ? Long.parseLong(m.group(3)) * 10 : Long.parseLong(m.group(3)));
                    String trans = transMap.get(ms);
                    if (trans != null && !trans.isEmpty()) {
                        sb.append(line.trim()).append(" || ").append(trans).append("\n");
                    } else {
                        sb.append(line.trim()).append("\n");
                    }
                } catch (Exception e) {
                    sb.append(line.trim()).append("\n");
                }
            } else {
                sb.append(line.trim()).append("\n");
            }
        }
        return sb.toString();
    }

    /** GeciMe：result[0].lrc URL → 下载 LRC */
    private String fetchFromGeciMe(String title, String artist) {
        try {
            String url;
            if (TextUtils.isEmpty(artist)) {
                url = "http://geci.me/api/lyric/" + URLEncoder.encode(title, "UTF-8");
            } else {
                url = "http://geci.me/api/lyric/" + URLEncoder.encode(title, "UTF-8")
                        + "/" + URLEncoder.encode(artist, "UTF-8");
            }
            String resp = httpGet(url);
            if (TextUtils.isEmpty(resp)) return null;
            JSONObject json = new JSONObject(resp);
            org.json.JSONArray result = json.optJSONArray("result");
            if (result == null || result.length() == 0) return null;
            String lrcUrl = result.optJSONObject(0).optString("lrc", "");
            if (TextUtils.isEmpty(lrcUrl)) return null;
            String lrc = httpGet(lrcUrl);
            return (lrc != null && lrc.contains("[")) ? lrc : null;
        } catch (Exception e) {
            Log.w(TAG, "GeciMe lyrics failed", e);
            return null;
        }
    }

    /** Lyrics.ovh：返回纯文本，需转换为 LRC */
    private String fetchFromLyricsOvh(String artist, String title) {
        try {
            if (TextUtils.isEmpty(artist) || TextUtils.isEmpty(title)) return null;
            String url = "https://api.lyrics.ovh/v1/"
                    + URLEncoder.encode(artist, "UTF-8") + "/"
                    + URLEncoder.encode(title, "UTF-8");
            String resp = httpGet(url);
            if (TextUtils.isEmpty(resp)) return null;
            JSONObject json = new JSONObject(resp);
            String plain = json.optString("lyrics", "");
            if (TextUtils.isEmpty(plain)) return null;
            return convertPlainTextToLrc(plain, title, artist);
        } catch (Exception e) {
            Log.w(TAG, "Lyrics.ovh failed", e);
            return null;
        }
    }

    /** 自定义 API：支持 {title}/{artist} 占位符 */
    private String fetchFromCustomApi(String title, String artist) {
        if (TextUtils.isEmpty(customApiUrl)) return null;
        try {
            String url = customApiUrl
                    .replace("{title}", URLEncoder.encode(title, "UTF-8"))
                    .replace("{artist}", URLEncoder.encode(artist == null ? "" : artist, "UTF-8"));
            String resp = httpGetWithHeader(url, "User-Agent", "Mozilla/5.0");
            if (TextUtils.isEmpty(resp)) return null;
            String trimmed = resp.trim();
            if (trimmed.startsWith("{")) {
                try {
                    JSONObject json = new JSONObject(trimmed);
                    String[] fields = {"lyric", "lrc", "content", "lyrics"};
                    for (String f : fields) {
                        String v = json.optString(f, "");
                        if (!TextUtils.isEmpty(v) && v.contains("[")) return v;
                    }
                } catch (Exception ignored) {}
            }
            // 纯文本 LRC
            if (trimmed.contains("[") && trimmed.contains("]")) return trimmed;
            return null;
        } catch (Exception e) {
            Log.w(TAG, "Custom API lyrics failed", e);
            return null;
        }
    }

    /** 纯文本歌词转 LRC（每行 4 秒） */
    private static String convertPlainTextToLrc(String plain, String title, String artist) {
        try {
            StringBuilder sb = new StringBuilder();
            sb.append("[ti:").append(title == null ? "" : title).append("]\n");
            sb.append("[ar:").append(artist == null ? "" : artist).append("]\n");
            sb.append("[al:]\n");
            sb.append("[by:车机助手]\n");
            String[] lines = plain.split("\n");
            long offset = 0;
            for (String raw : lines) {
                String line = raw.trim();
                if (line.isEmpty()) continue;
                long mm = offset / 60_000;
                long ss = (offset % 60_000) / 1000;
                long ms = offset % 1000;
                sb.append(String.format("[%02d:%02d.%03d]", mm, ss, ms)).append(line).append("\n");
                offset += 4000;
            }
            return sb.toString();
        } catch (Exception e) {
            return "";
        }
    }

    // ============ HTTP 工具 ============

    private static String httpGet(String urlStr) {
        return httpGetWithHeader(urlStr, null, null);
    }

    private static String httpGetWithHeader(String urlStr, String headerKey, String headerValue) {
        HttpURLConnection conn = null;
        try {
            URL url = new URL(urlStr);
            conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(8000);
            conn.setReadTimeout(8000);
            conn.setRequestMethod("GET");
            if (headerKey != null && headerValue != null) {
                conn.setRequestProperty(headerKey, headerValue);
            }
            int code = conn.getResponseCode();
            if (code != 200) return null;
            java.io.InputStream is = conn.getInputStream();
            BufferedReader r = new BufferedReader(new InputStreamReader(is, "UTF-8"));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = r.readLine()) != null) {
                if (sb.length() > 0) sb.append("\n");
                sb.append(line);
            }
            r.close();
            return sb.toString();
        } catch (Exception e) {
            Log.w(TAG, "httpGet failed: " + urlStr, e);
            return null;
        } finally {
            if (conn != null) conn.disconnect();
        }
    }
}
