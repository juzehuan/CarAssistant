package com.carassistant.lyrics;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 统一歌词时间线（1:1 复刻自歌词伴侣 LrcTimeline）。
 *
 * 支持三种输入：
 * 1. 标准 LRC（行级时间戳）：[mm:ss.xxx]text
 * 2. 翻译 LRC：[mm:ss.xxx]译文
 * 3. 增强逐字时间线（KRC/QRC/YRC 归一化后的统一格式）：
 *    [行起始ms,行时长ms](字起始ms,字时长ms,0)字(字起始ms,字时长ms,0)字...
 *
 * 通过 {@link #at(long)} 可获取任意播放位置的歌词快照 {@link At}，
 * 包含上下文行、当前字、逐字进度、间奏标记等。
 */
public final class LrcTimeline {

    /** 间奏阈值：行间间隔 ≥ 5 秒视为间奏段 */
    private static final long MIN_INTERLUDE_MS = 5000L;
    /** 普通行（无 durationMs）默认持续 5 秒 */
    private static final long PLAIN_LINE_HOLD_MS = 5000L;

    public static final LrcTimeline EMPTY = new LrcTimeline(Collections.<Line>emptyList());

    private final List<Line> lines;

    public LrcTimeline(List<Line> lines) {
        this.lines = lines;
    }

    public List<Line> getLines() { return lines; }

    public boolean isEmpty() { return lines.isEmpty(); }

    public int size() { return lines.size(); }

    // ----------------------------------------------------------------------
    // 数据结构
    // ----------------------------------------------------------------------

    /** 一行歌词 */
    public static final class Line {
        public final long timeMs;
        public final long durationMs;
        public final String text;
        public final String translated;
        public final List<Word> words;

        public Line(long timeMs, long durationMs, String text, String translated, List<Word> words) {
            this.timeMs = timeMs;
            this.durationMs = durationMs;
            this.text = text;
            this.translated = translated == null ? "" : translated;
            this.words = words == null ? Collections.<Word>emptyList() : words;
        }
    }

    /** 一个字/词 */
    public static final class Word {
        public final long startMs;
        public final long durationMs;
        public final String text;

        public Word(long startMs, long durationMs, String text) {
            this.startMs = startMs;
            this.durationMs = durationMs;
            this.text = text == null ? "" : text;
        }
    }

    /** 上下文行 */
    public static final class NearbyLine {
        public final String text;
        public final String translated;
        public final int offset;
        public final long timeMs;
        public final long durationMs;
        public final boolean interlude;

        public NearbyLine(String text, String translated, int offset, long timeMs, long durationMs, boolean interlude) {
            this.text = text == null ? "" : text;
            this.translated = translated == null ? "" : translated;
            this.offset = offset;
            this.timeMs = timeMs;
            this.durationMs = durationMs;
            this.interlude = interlude;
        }
    }

    /** 播放位置快照 */
    public static final class At {
        public static final At EMPTY = new At("", "", "", "", false, false, "", "", -1, 0, -1, 0, 0, Collections.<NearbyLine>emptyList());

        public final String previousLyric;
        public final String lyric;
        public final String translatedLyric;
        public final String nextLyric;
        public final boolean interlude;
        public final boolean wordTimed;
        public final String completedLyric;
        public final String currentWord;
        public final long lineStartMs;
        public final long lineDurationMs;
        public final long wordStartMs;
        public final long wordDurationMs;
        public final int wordProgressPermille;
        public final List<NearbyLine> nearbyLines;

        public At(String previousLyric, String lyric, String translatedLyric, String nextLyric,
                  boolean interlude, boolean wordTimed, String completedLyric, String currentWord,
                  long lineStartMs, long lineDurationMs, long wordStartMs, long wordDurationMs,
                  int wordProgressPermille, List<NearbyLine> nearbyLines) {
            this.previousLyric = previousLyric == null ? "" : previousLyric;
            this.lyric = lyric == null ? "" : lyric;
            this.translatedLyric = translatedLyric == null ? "" : translatedLyric;
            this.nextLyric = nextLyric == null ? "" : nextLyric;
            this.interlude = interlude;
            this.wordTimed = wordTimed;
            this.completedLyric = completedLyric == null ? "" : completedLyric;
            this.currentWord = currentWord == null ? "" : currentWord;
            this.lineStartMs = lineStartMs;
            this.lineDurationMs = lineDurationMs;
            this.wordStartMs = wordStartMs;
            this.wordDurationMs = wordDurationMs;
            this.wordProgressPermille = wordProgressPermille;
            this.nearbyLines = nearbyLines == null ? Collections.<NearbyLine>emptyList() : nearbyLines;
        }
    }

    // ----------------------------------------------------------------------
    // 解析
    // ----------------------------------------------------------------------

    private static final Pattern TIME_TAG =
            Pattern.compile("\\[(\\d{1,3}):(\\d{2})(?:[.:](\\d{1,3}))?]");

    private static final Pattern YRC_LINE =
            Pattern.compile("^\\[(\\d+),(\\d+)](.*)$");
    private static final Pattern YRC_WORD =
            Pattern.compile("\\((\\d+),(\\d+),\\d+\\)");

    /**
     * 构建时间线。
     *
     * @param mainLrc     主歌词（标准 LRC）
     * @param translation 翻译歌词（标准 LRC），可为空
     * @param enhanced    逐字增强时间线（KRC/QRC/YRC 归一化格式），可为空
     */
    public static LrcTimeline parse(String mainLrc, String translation, String enhanced) {
        TreeMap<Long, String> mainMap = parseTimedLines(mainLrc == null ? "" : mainLrc);
        TreeMap<Long, String> transMap = parseTimedLines(translation == null ? "" : translation);

        List<Line> yrcLines = parseYrcLines(enhanced == null ? "" : enhanced, mainMap, transMap);
        if (!yrcLines.isEmpty()) {
            return new LrcTimeline(Collections.unmodifiableList(yrcLines));
        }

        if (mainMap.isEmpty()) return EMPTY;

        ArrayList<Line> list = new ArrayList<>(mainMap.size());
        for (Map.Entry<Long, String> e : mainMap.entrySet()) {
            list.add(new Line(e.getKey(), 0L, e.getValue(),
                    closestTranslation(transMap, e.getKey(), 500L),
                    Collections.<Word>emptyList()));
        }
        return new LrcTimeline(Collections.unmodifiableList(list));
    }

    /** 双参数重载（无逐字） */
    public static LrcTimeline parse(String mainLrc, String translation) {
        return parse(mainLrc, translation, "");
    }

    /** 单参数重载（无翻译、无逐字） */
    public static LrcTimeline parse(String mainLrc) {
        return parse(mainLrc, "", "");
    }

    /** 解析标准 LRC 为时间戳→文本的有序映射 */
    private static TreeMap<Long, String> parseTimedLines(String lrc) {
        TreeMap<Long, String> map = new TreeMap<>();
        if (lrc == null || lrc.isEmpty()) return map;
        for (String rawLine : lrc.split("\\r?\\n")) {
            Matcher tm = TIME_TAG.matcher(rawLine);
            ArrayList<Long> times = new ArrayList<>();
            int lastEnd = 0;
            while (tm.find()) {
                long ms = toMilliseconds(tm.group(1), tm.group(2), tm.group(3));
                times.add(ms);
                lastEnd = tm.end();
            }
            if (times.isEmpty()) continue;
            String text = rawLine.substring(lastEnd).trim();
            for (Long t : times) {
                map.put(t, text);
            }
        }
        return map;
    }

    private static long toMilliseconds(String minStr, String secStr, String fracStr) {
        long min = Long.parseLong(minStr);
        long sec = Long.parseLong(secStr);
        long frac = 0;
        if (fracStr != null && !fracStr.isEmpty()) {
            int len = fracStr.length();
            if (len == 1) frac = Long.parseLong(fracStr) * 100L;
            else if (len == 2) frac = Long.parseLong(fracStr) * 10L;
            else if (len == 3) frac = Long.parseLong(fracStr);
            else {
                frac = Long.parseLong(fracStr.substring(0, 3));
            }
        }
        return min * 60_000L + sec * 1000L + frac;
    }

    /** 解析增强逐字格式 */
    private static List<Line> parseYrcLines(String enhanced,
                                            TreeMap<Long, String> mainMap,
                                            TreeMap<Long, String> transMap) {
        ArrayList<Line> out = new ArrayList<>();
        if (enhanced == null || enhanced.isEmpty()) return out;

        for (String raw : enhanced.split("\\r?\\n")) {
            Matcher lm = YRC_LINE.matcher(raw);
            if (!lm.matches()) continue;
            long lineStart = Long.parseLong(lm.group(1));
            long lineDur = Long.parseLong(lm.group(2));
            String content = lm.group(3);

            ArrayList<Word> words = new ArrayList<>();
            Matcher wm = YRC_WORD.matcher(content);
            long curStart = -1;
            long curDur = 0;
            int textStart = -1;
            while (wm.find()) {
                if (curStart >= 0 && textStart >= 0) {
                    String wText = content.substring(textStart, wm.start());
                    words.add(new Word(curStart, curDur, wText));
                }
                curStart = Long.parseLong(wm.group(1));
                curDur = Long.parseLong(wm.group(2));
                textStart = wm.end();
            }
            if (curStart >= 0 && textStart >= 0) {
                String wText = content.substring(textStart);
                words.add(new Word(curStart, curDur, wText));
            }

            StringBuilder sb = new StringBuilder();
            for (Word w : words) sb.append(w.text);
            String lineText = sb.toString().trim();

            String translated = enhancedTranslation(mainMap, transMap, lineStart, lineText);
            out.add(new Line(lineStart, lineDur, lineText, translated,
                    Collections.unmodifiableList(words)));
        }
        return out;
    }

    /** 逐字行翻译匹配：先按文本相同找主歌词时间，再查翻译；否则直接按时间最近查翻译 */
    private static String enhancedTranslation(TreeMap<Long, String> mainMap,
                                              TreeMap<Long, String> transMap,
                                              long lineStart, String lineText) {
        String normalized = normalizeLyricText(lineText);
        if (!normalized.isEmpty() && !mainMap.isEmpty()) {
            long bestTime = -1;
            long bestDiff = 5000L;
            for (Map.Entry<Long, String> e : mainMap.entrySet()) {
                if (normalizeLyricText(e.getValue()).equals(normalized)) {
                    long diff = Math.abs(e.getKey() - lineStart);
                    if (diff < bestDiff) {
                        bestDiff = diff;
                        bestTime = e.getKey();
                    }
                }
            }
            if (bestTime >= 0) {
                return closestTranslation(transMap, bestTime, 500L);
            }
        }
        return closestTranslation(transMap, lineStart, 2500L);
    }

    private static String closestTranslation(TreeMap<Long, String> map, long time, long tolerance) {
        if (map == null || map.isEmpty()) return "";
        Map.Entry<Long, String> floor = map.floorEntry(time);
        Map.Entry<Long, String> ceil = map.ceilingEntry(time);
        long floorDiff = floor == null ? Long.MAX_VALUE : time - floor.getKey();
        long ceilDiff = ceil == null ? Long.MAX_VALUE : ceil.getKey() - time;
        if (floorDiff <= ceilDiff && floorDiff <= tolerance) return floor.getValue();
        if (ceilDiff <= floorDiff && ceilDiff <= tolerance) return ceil.getValue();
        return "";
    }

    private static String normalizeLyricText(String s) {
        if (s == null) return "";
        return s.toLowerCase(Locale.ROOT).replaceAll("[\\p{P}\\s]+", "");
    }

    // ----------------------------------------------------------------------
    // 查询
    // ----------------------------------------------------------------------

    /** 获取指定播放位置（毫秒）的歌词快照 */
    public At at(long positionMs) {
        if (lines.isEmpty()) return At.EMPTY;

        // 二分查找：最后一个 timeMs <= positionMs 的行
        int lo = 0, hi = lines.size();
        while (lo < hi) {
            int mid = (lo + hi) >>> 1;
            if (lines.get(mid).timeMs <= positionMs) lo = mid + 1;
            else hi = mid;
        }
        int idx = lo - 1; // 当前行索引

        Line current = idx >= 0 ? lines.get(idx) : null;
        Line prev = idx > 0 ? lines.get(idx - 1) : null;
        Line next = lo < lines.size() ? lines.get(lo) : null;

        // 前奏间奏
        if (current == null && next != null && next.timeMs >= MIN_INTERLUDE_MS) {
            return buildInterludeAt(prev, next, positionMs, 0, next.timeMs, idx);
        }
        if (current == null) {
            return new At("", "", "", next == null ? "" : next.text, false, false, "", "",
                    -1, 0, -1, 0, 0, buildNearby(idx));
        }

        // 行间间奏
        long lineEnd = current.timeMs + (current.durationMs > 0 ? current.durationMs : PLAIN_LINE_HOLD_MS);
        if (next != null) {
            long gap = next.timeMs - lineEnd;
            if (gap >= MIN_INTERLUDE_MS && positionMs >= lineEnd) {
                return buildInterludeAt(current, next, positionMs, lineEnd, gap, idx);
            }
        }

        // 当前行
        long lineDuration = current.durationMs > 0 ? current.durationMs
                : (next != null ? Math.max(1000L, next.timeMs - current.timeMs)
                : Math.max(1000L, PLAIN_LINE_HOLD_MS));

        // 逐字进度
        String completedLyric = "";
        String currentWord = "";
        long wordStartMs = -1;
        long wordDurationMs = 0;
        int wordProgress = 0;
        boolean wordTimed = !current.words.isEmpty();

        if (wordTimed) {
            StringBuilder sb = new StringBuilder();
            for (Word w : current.words) {
                if (positionMs < w.startMs) break;
                if (positionMs < w.startMs + w.durationMs) {
                    currentWord = w.text;
                    wordStartMs = w.startMs;
                    wordDurationMs = w.durationMs;
                    long p = ((positionMs - w.startMs) * 1000L) / Math.max(1L, w.durationMs);
                    wordProgress = (int) Math.max(0, Math.min(1000, p));
                    break;
                }
                sb.append(w.text);
            }
            completedLyric = sb.toString();
        }

        return new At(
                prev == null ? "" : prev.text,
                current.text,
                current.translated,
                next == null ? "" : next.text,
                false,
                wordTimed,
                completedLyric,
                currentWord,
                current.timeMs,
                lineDuration,
                wordStartMs,
                wordDurationMs,
                wordProgress,
                buildNearby(idx)
        );
    }

    private At buildInterludeAt(Line prev, Line next, long positionMs,
                                long gapStart, long gapDur, int idx) {
        return new At(
                prev == null ? "" : prev.text,
                "",
                "",
                next == null ? "" : next.text,
                true,
                false,
                "",
                "",
                gapStart,
                gapDur,
                -1,
                0,
                0,
                buildInterludeNearby(idx, gapStart, gapDur)
        );
    }

    /** 构建上下文行（前后各 3 行） */
    private List<NearbyLine> buildNearby(int idx) {
        ArrayList<NearbyLine> list = new ArrayList<>();
        int from = Math.max(0, idx - 3);
        int to = Math.min(lines.size() - 1, idx + 3);
        for (int i = from; i <= to; i++) {
            Line l = lines.get(i);
            list.add(new NearbyLine(l.text, l.translated, i - idx, l.timeMs, l.durationMs, false));
        }
        return Collections.unmodifiableList(list);
    }

    private List<NearbyLine> buildInterludeNearby(int idx, long gapStart, long gapDur) {
        ArrayList<NearbyLine> list = new ArrayList<>();
        int from = Math.max(0, idx - 3);
        int to = Math.min(lines.size() - 1, idx + 3);
        for (int i = from; i <= to; i++) {
            if (i == idx) {
                list.add(new NearbyLine("", "", 0, gapStart, gapDur, true));
                continue;
            }
            Line l = lines.get(i);
            list.add(new NearbyLine(l.text, l.translated, i - idx, l.timeMs, l.durationMs, false));
        }
        return Collections.unmodifiableList(list);
    }

    /** 行跳转：返回从当前行偏移 delta 行的起始时间 */
    public long shiftedPosition(long positionMs, int delta) {
        if (lines.isEmpty()) return positionMs;
        int lo = 0, hi = lines.size();
        while (lo < hi) {
            int mid = (lo + hi) >>> 1;
            if (lines.get(mid).timeMs <= positionMs) lo = mid + 1;
            else hi = mid;
        }
        int idx = lo - 1;
        int target = Math.max(0, Math.min(lines.size() - 1, idx + delta));
        return lines.get(target).timeMs;
    }

    /** 根据逐字进度计算应显示的码点数（向上取整） */
    public static int revealedCodePointCount(String word, int permille) {
        if (word == null || word.isEmpty()) return 0;
        int total = word.codePointCount(0, word.length());
        return Math.min(total, (Math.min(1000, permille) * total + 999) / 1000);
    }

    /** 静态歌词（无时间信息） */
    public static At liveLine(String text) {
        return new At("", text == null ? "" : text, "", "", false, false, "", "", -1, 0, -1, 0, 0,
                Collections.<NearbyLine>emptyList());
    }
}
