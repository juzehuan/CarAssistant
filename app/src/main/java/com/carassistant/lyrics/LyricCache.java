package com.carassistant.lyrics;

import android.content.Context;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;

/**
 * 歌词文件缓存（1:1 复刻自歌词伴侣 LyricCache）。
 *
 * 目录：<appCacheDir>/lyrics_<source>_v1/
 * 文件名：key 中所有非 [A-Za-z0-9_-] 字符替换为 _，后缀 .lrc
 * 有效期：30 天
 * 大小上限：2MB
 */
public final class LyricCache {

    private static final String TAG = "LyricCache";
    private static final long MAX_AGE_MS = 2_592_000_000L; // 30 天
    private static final long MAX_FILE_SIZE = 2_000_000L;  // 2MB

    private final File directory;

    public LyricCache(Context context, String source) {
        this.directory = new File(context.getCacheDir(), "lyrics_" + source + "_v1");
    }

    private File file(String key) {
        String name = key == null ? "unknown" : key.replaceAll("[^A-Za-z0-9_-]", "_");
        return new File(directory, name + ".lrc");
    }

    /** 读取缓存，失效或不存在返回 null */
    public String read(String key) {
        File f = file(key);
        if (!f.isFile()) return null;
        if (System.currentTimeMillis() - f.lastModified() > MAX_AGE_MS) return null;
        try {
            int len = (int) Math.min(f.length(), MAX_FILE_SIZE);
            if (len <= 0) return null;
            byte[] buf = new byte[len];
            RandomAccessFile raf = new RandomAccessFile(f, "r");
            try {
                int read = 0;
                while (read < len) {
                    int n = raf.read(buf, read, len - read);
                    if (n < 0) break;
                    read += n;
                }
                return new String(buf, 0, read, StandardCharsets.UTF_8);
            } finally {
                raf.close();
            }
        } catch (Exception e) {
            Log.w(TAG, "read failed: " + key, e);
            return null;
        }
    }

    /** 写入缓存（空内容跳过） */
    public void write(String key, String value) {
        if (value == null || value.isEmpty()) return;
        if (!directory.isDirectory() && !directory.mkdirs()) {
            Log.w(TAG, "mkdirs failed: " + directory);
            return;
        }
        FileOutputStream fos = null;
        try {
            fos = new FileOutputStream(file(key));
            fos.write(value.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            Log.w(TAG, "write failed: " + key, e);
        } finally {
            if (fos != null) try { fos.close(); } catch (Exception ignored) {}
        }
    }
}
