package com.carassistant.lyrics;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.InflaterInputStream;

/**
 * 酷狗 KRC 歌词解密与解析（1:1 复刻自歌词伴侣 KrcLyricCodec）。
 *
 * KRC 文件结构：
 * 1. 前 4 字节魔数 "krc1"
 * 2. XOR 加密的数据（密钥 16 字节）
 * 3. zlib raw deflate 压缩
 *
 * 解密后文本格式：
 * [ti:歌名][ar:歌手][al:专辑][offset:0]
 * [行起始ms,行时长ms]<字偏移ms,字时长ms,0>字<字偏移ms,字时长ms,0>字...
 *
 * 注意：KRC 的字时间是相对行首的偏移，需转为绝对时间。
 */
public final class KrcLyricCodec {

    private static final byte[] KEY = {
            64, 71, 97, 119, 94, 50, 116, 71, 81, 54, 49, 45, -50, -46, 110, 105
    };

    private static final Pattern LINE =
            Pattern.compile("^\\[(\\d+)\\s*,\\s*(\\d+)](.*)$");
    private static final Pattern WORD =
            Pattern.compile("<(\\d+)\\s*,\\s*(\\d+)\\s*,\\s*\\d+>");
    private static final Pattern LANGUAGE =
            Pattern.compile("(?m)^\\[language:([^]]+)]\\s*$");

    private KrcLyricCodec() {}

    /** 解密 KRC 二进制数据，返回明文 KRC 文本 */
    public static String decrypt(byte[] data) throws Exception {
        if (data == null || data.length <= 4) return "";
        int len = data.length - 4;
        byte[] xored = new byte[len];
        for (int i = 0; i < len; i++) {
            xored[i] = (byte) (data[i + 4] ^ KEY[i % KEY.length]);
        }
        InflaterInputStream iis = new InflaterInputStream(new ByteArrayInputStream(xored));
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[4096];
        int n;
        while ((n = iis.read(buf)) >= 0) {
            if (n > 0) out.write(buf, 0, n);
        }
        return out.toString("UTF-8").replace("\ufeff", "");
    }

    /** 将明文 KRC 转为统一增强时间线格式 */
    public static String toEnhancedTimeline(String krc) {
        if (krc == null || krc.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (String raw : krc.split("\\r?\\n")) {
            Matcher lm = LINE.matcher(raw);
            if (!lm.matches()) continue;
            long lineStart = Long.parseLong(lm.group(1));
            String content = lm.group(3);
            sb.append('[').append(lineStart).append(',').append(lm.group(2)).append(']');

            Matcher wm = WORD.matcher(content);
            int lastEnd = 0;
            while (wm.find()) {
                long wordOffset = Long.parseLong(wm.group(1));
                long wordDur = Long.parseLong(wm.group(2));
                long absStart = lineStart + wordOffset;
                sb.append('(').append(absStart).append(',').append(wordDur).append(",0)");
                sb.append(content, lastEnd, wm.start());
                lastEnd = wm.end();
            }
            sb.append('\n');
        }
        return sb.toString();
    }

    /** 从 KRC 中提取翻译（酷狗翻译 JSON 格式） */
    public static String toTranslationLrc(String krcContent, String translationJson) {
        if (krcContent == null || translationJson == null || translationJson.isEmpty()) return "";
        // 提取每行的 lineStart
        ArrayList<Long> lineStarts = new ArrayList<>();
        for (String raw : krcContent.split("\\r?\\n")) {
            Matcher lm = LINE.matcher(raw);
            if (lm.matches()) {
                lineStarts.add(Long.parseLong(lm.group(1)));
            }
        }
        if (lineStarts.isEmpty()) return "";
        try {
            org.json.JSONObject root = new org.json.JSONObject(translationJson);
            org.json.JSONArray content = root.optJSONArray("content");
            if (content == null) return "";
            org.json.JSONArray transLines = null;
            for (int i = 0; i < content.length(); i++) {
                org.json.JSONObject item = content.optJSONObject(i);
                if (item != null && item.optInt("type") == 1) {
                    transLines = item.optJSONArray("lyricContent");
                    break;
                }
            }
            if (transLines == null || transLines.length() != lineStarts.size()) return "";
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < transLines.length(); i++) {
                org.json.JSONArray lineArr = transLines.optJSONArray(i);
                if (lineArr == null) continue;
                StringBuilder line = new StringBuilder();
                for (int j = 0; j < lineArr.length(); j++) {
                    String s = lineArr.optString(j, "");
                    if (!s.isEmpty() && !s.equals("//")) line.append(s);
                }
                long t = lineStarts.get(i);
                sb.append(String.format(java.util.Locale.ROOT, "[%02d:%02d.%03d]",
                        t / 60000, (t / 1000) % 60, t % 1000));
                sb.append(line).append('\n');
            }
            return sb.toString();
        } catch (Exception e) {
            return "";
        }
    }

    /** 提取语言标签 */
    public static String encodedLanguage(String krc) {
        if (krc == null) return "";
        Matcher m = LANGUAGE.matcher(krc);
        return m.find() ? m.group(1) : "";
    }
}
