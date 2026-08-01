package com.carassistant.lyrics;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * HTTP 工具（1:1 复刻自歌词伴侣 LyricHttp/HttpCompat）。
 *
 * 提供 GET/POST 请求，统一 UA、Referer、超时配置。
 */
public final class HttpCompat {

    private static final String TAG = "HttpCompat";
    private static final String DEFAULT_UA = "Mozilla/5.0 Lyrics-Companion/1.0";
    private static final int CONNECT_TIMEOUT = 8000;
    private static final int READ_TIMEOUT = 10000;

    private HttpCompat() {}

    public static String get(String url, Map<String, String> headers) throws IOException {
        return execute("GET", url, null, headers);
    }

    public static String post(String url, String body, String contentType, Map<String, String> headers) throws IOException {
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setRequestMethod("POST");
            conn.setConnectTimeout(CONNECT_TIMEOUT);
            conn.setReadTimeout(READ_TIMEOUT);
            conn.setRequestProperty("User-Agent", DEFAULT_UA);
            if (contentType != null) {
                conn.setRequestProperty("Content-Type", contentType);
            }
            if (headers != null) {
                for (Map.Entry<String, String> e : headers.entrySet()) {
                    conn.setRequestProperty(e.getKey(), e.getValue());
                }
            }
            conn.setDoOutput(true);
            if (body != null) {
                OutputStream os = conn.getOutputStream();
                try {
                    os.write(body.getBytes(StandardCharsets.UTF_8));
                } finally {
                    os.close();
                }
            }
            return readResponse(conn);
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    private static String execute(String method, String url, String body, Map<String, String> headers) throws IOException {
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setRequestMethod(method);
            conn.setConnectTimeout(CONNECT_TIMEOUT);
            conn.setReadTimeout(READ_TIMEOUT);
            conn.setRequestProperty("User-Agent", DEFAULT_UA);
            if (headers != null) {
                for (Map.Entry<String, String> e : headers.entrySet()) {
                    conn.setRequestProperty(e.getKey(), e.getValue());
                }
            }
            return readResponse(conn);
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    private static String readResponse(HttpURLConnection conn) throws IOException {
        int code = conn.getResponseCode();
        InputStream is = (code >= 200 && code < 300) ? conn.getInputStream() : conn.getErrorStream();
        if (is == null) {
            Log.w(TAG, "HTTP " + code + " no body");
            return "";
        }
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int n;
            while ((n = is.read(buf)) >= 0) {
                if (n > 0) out.write(buf, 0, n);
            }
            return out.toString(StandardCharsets.UTF_8.name());
        } finally {
            is.close();
        }
    }

    public static String encode(String s) {
        if (s == null) return "";
        try {
            return URLEncoder.encode(s, "UTF-8");
        } catch (Exception e) {
            return s;
        }
    }

    /** 下载图片为 Bitmap，失败返回 null */
    public static Bitmap downloadBitmap(String url) {
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(CONNECT_TIMEOUT);
            conn.setReadTimeout(READ_TIMEOUT);
            conn.setRequestProperty("User-Agent", DEFAULT_UA);
            int code = conn.getResponseCode();
            if (code < 200 || code >= 300) {
                Log.w(TAG, "downloadBitmap HTTP " + code);
                return null;
            }
            InputStream is = conn.getInputStream();
            try {
                return BitmapFactory.decodeStream(is);
            } finally {
                is.close();
            }
        } catch (Exception e) {
            Log.w(TAG, "downloadBitmap failed: " + url, e);
            return null;
        } finally {
            if (conn != null) conn.disconnect();
        }
    }
}
