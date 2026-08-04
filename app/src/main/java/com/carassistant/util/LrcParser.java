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

import android.text.TextUtils;
import android.util.Log;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * LRC 歌词解析器（Java 重写自鸿启桌面 Kotlin 版）
 *
 * 功能：
 * - 解析标准 LRC 格式（含多时间标签行 [mm:ss.xxx]text）
 * - 按播放位置定位上一行/当前行/下一行歌词
 * - 线程安全（synchronized 保护 lrcLines）
 *
 * LRC 格式示例：
 * [ti:歌曲名]
 * [ar:歌手]
 * [00:01.23]第一行歌词
 * [00:05.67]第二行歌词
 * [01:10.00][01:30.00]重复行（多时间标签）
 */
public final class LrcParser {

    private static final String TAG = "LrcParser";
    /** 时间标签正则：[mm:ss.xx] 或 [mm:ss.xxx] */
    private static final Pattern TIME_TAG_REGEX = Pattern.compile("\\[(\\d{2}):(\\d{2})\\.(\\d{2,3})\\]");
    /** 通用标签正则：[任意内容] */
    private static final Pattern TAG_REGEX = Pattern.compile("\\[(.*?)\\]");

    private final Object lock = new Object();
    private List<LrcLine> lrcLines = new ArrayList<>();

    /** LRC 单行数据 */
    public static final class LrcLine {
        public final long time;   // 毫秒
        public final String text;
        public final String translation;

        public LrcLine(long time, String text) {
            this(time, text, "");
        }

        public LrcLine(long time, String text, String translation) {
            this.time = time;
            this.text = text == null ? "" : text;
            this.translation = translation == null ? "" : translation;
        }

        public long getTime() { return time; }
        public String getText() { return text; }
        public String getTranslation() { return translation; }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof LrcLine)) return false;
            LrcLine l = (LrcLine) o;
            return time == l.time && TextUtils.equals(text, l.text)
                    && TextUtils.equals(translation, l.translation);
        }

        @Override
        public int hashCode() {
            int h = Long.hashCode(time) * 31;
            h += (text != null ? text.hashCode() : 0);
            return h * 31 + (translation != null ? translation.hashCode() : 0);
        }

        @Override
        public String toString() {
            return "LrcLine(time=" + time + ", text=" + text + ", translation=" + translation + ")";
        }
    }

    /** 三元组：上一行/当前行/下一行 */
    public static final class LyricsTriple {
        public final String prev;
        public final String current;
        public final String next;
        public final String currentTranslation;

        public LyricsTriple(String prev, String current, String next) {
            this(prev, current, next, "");
        }

        public LyricsTriple(String prev, String current, String next, String currentTranslation) {
            this.prev = prev == null ? "" : prev;
            this.current = current == null ? "" : current;
            this.next = next == null ? "" : next;
            this.currentTranslation = currentTranslation == null ? "" : currentTranslation;
        }
    }

    /**
     * 解析 LRC 文本
     * @return true 表示解析到至少一行歌词
     */
    public boolean parse(String lrcContent) {
        if (TextUtils.isEmpty(lrcContent)) {
            synchronized (lock) {
                lrcLines.clear();
            }
            return false;
        }
        List<LrcLine> parsed = new ArrayList<>();
        String[] lines = lrcContent.split("\n");
        for (String rawLine : lines) {
            if (rawLine == null) continue;
            String line = rawLine.trim();
            if (line.isEmpty()) continue;

            // 跳过元数据标签（ti/ar/al/by/offset/length 等）：整行只含 [非数字标签]
            Matcher tagMatcher = TAG_REGEX.matcher(line);
            // 查找所有时间标签
            List<Long> times = new ArrayList<>();
            int lastTagEnd = 0;
            while (tagMatcher.find()) {
                String tagContent = tagMatcher.group(1);
                if (tagContent == null) continue;
                Matcher tm = TIME_TAG_REGEX.matcher("[" + tagContent + "]");
                if (tm.matches()) {
                    try {
                        long ms = parseTimeTag(tm.group(1), tm.group(2), tm.group(3));
                        times.add(ms);
                        lastTagEnd = tagMatcher.end();
                    } catch (Exception ignored) {}
                } else {
                    // 非 time 标签（ti/ar/al 等）：记录结束位置，避免计入歌词文本
                    lastTagEnd = tagMatcher.end();
                }
            }
            if (times.isEmpty()) {
                // 整行无时间标签：跳过（避免把元数据当歌词）
                continue;
            }
            // 歌词文本 = 最后一个标签之后的部分
            String text = lastTagEnd > 0 && lastTagEnd <= line.length()
                    ? line.substring(lastTagEnd).trim() : "";
            // 解析翻译（双语歌词以 " || " 分隔）
            String mainText = text;
            String translation = "";
            if (text.contains(" || ")) {
                int idx = text.indexOf(" || ");
                mainText = text.substring(0, idx).trim();
                translation = text.substring(idx + 4).trim();
            }
            for (Long t : times) {
                parsed.add(new LrcLine(t, mainText, translation));
            }
        }
        // 按时间升序排序
        Collections.sort(parsed, (a, b) -> Long.compare(a.time, b.time));
        synchronized (lock) {
            lrcLines = parsed;
        }
        Log.d(TAG, "parsed " + parsed.size() + " lines");
        return !parsed.isEmpty();
    }

    /** 解析 [mm:ss.xxx] 为毫秒 */
    private static long parseTimeTag(String mm, String ss, String xxx) {
        if (mm == null || ss == null || xxx == null) return 0;
        try {
            int minutes = Integer.parseInt(mm);
            int seconds = Integer.parseInt(ss);
            int millis;
            if (xxx.length() == 2) {
                millis = Integer.parseInt(xxx) * 10;  // [mm:ss.xx] → 两位补零到毫秒
            } else {
                millis = Integer.parseInt(xxx);
            }
            return minutes * 60_000L + seconds * 1000L + millis;
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /** 清空歌词 */
    public void clear() {
        synchronized (lock) {
            lrcLines.clear();
        }
    }

    /**
     * 直接设置歌词行（用于从 {@link com.carassistant.lyrics.LrcTimeline} 同步行数据）。
     * 调用后 lrcLines 会被替换为传入列表的副本，并按时间升序排序。
     */
    public void setLines(List<LrcLine> lines) {
        synchronized (lock) {
            if (lines == null || lines.isEmpty()) {
                lrcLines = new ArrayList<>();
                return;
            }
            List<LrcLine> copy = new ArrayList<>(lines);
            Collections.sort(copy, (a, b) -> Long.compare(a.time, b.time));
            lrcLines = copy;
        }
    }

    /** 总行数 */
    public int getTotalLines() {
        synchronized (lock) {
            return lrcLines.size();
        }
    }

    /** 全部行（副本） */
    public List<LrcLine> getAllLines() {
        synchronized (lock) {
            return new ArrayList<>(lrcLines);
        }
    }

    /** 按索引获取行 */
    public LrcLine getLyricLine(int index) {
        synchronized (lock) {
            if (index < 0 || index >= lrcLines.size()) return null;
            return lrcLines.get(index);
        }
    }

    /** 当前位置对应的行索引（-1 表示无匹配） */
    public int getCurrentLineIndex(long position) {
        synchronized (lock) {
            if (lrcLines.isEmpty()) return -1;
            // 歌曲开头（在第一行歌词时间点之前）显示第一句，避免空白期误显示“暂无歌词”
            if (lrcLines.get(0).time > position) return 0;
            int currentIndex = -1;
            for (int i = 0; i < lrcLines.size(); i++) {
                if (lrcLines.get(i).time > position) break;
                currentIndex = i;
            }
            return currentIndex;
        }
    }

    /**
     * 按位置返回三行歌词（上一行/当前行/下一行）
     * 无歌词时 current="暂无歌词"
     */
    public LyricsTriple getLyricsAtPosition(long position) {
        synchronized (lock) {
            if (lrcLines.isEmpty()) {
                return new LyricsTriple("", "暂无歌词", "", "");
            }
            LrcLine prevLine = null;
            LrcLine currentLine = null;
            LrcLine nextLine = null;
            List<LrcLine> snapshot = new ArrayList<>(lrcLines);
            for (LrcLine line : snapshot) {
                if (line.time <= position) {
                    prevLine = currentLine;
                    currentLine = line;
                } else {
                    nextLine = line;
                    break;
                }
            }
            // 歌曲开头（在第一行歌词时间点之前）：当前行显示第一句歌词，
            // 避免切歌后空白期把第一句误显示成“暂无歌词”。
            if (currentLine == null) {
                currentLine = snapshot.get(0);
                nextLine = snapshot.size() > 1 ? snapshot.get(1) : null;
            }
            String prev = prevLine != null ? prevLine.text : "";
            String current = currentLine != null ? currentLine.text : "";
            String next = nextLine != null ? nextLine.text : "";
            String currentTrans = currentLine != null ? currentLine.translation : "";
            return new LyricsTriple(prev, current, next, currentTrans);
        }
    }
}
