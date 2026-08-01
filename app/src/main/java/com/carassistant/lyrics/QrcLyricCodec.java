package com.carassistant.lyrics;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.InflaterInputStream;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;

/**
 * QQ 音乐 QRC 歌词解密与解析（1:1 复刻自歌词伴侣 QrcLyricCodec）。
 *
 * QRC 数据可能是：
 * 1. 明文 XML（直接处理）
 * 2. Hex 编码的加密数据：Hex→3DES EDE ECB 解密→zlib raw inflate→提取 LyricContent
 *
 * 3DES 密钥：!@#)(*$%123ZXC!@!@#)(NHL（24 字节 ASCII）
 *
 * 解密后正文格式（与 KRC/YRC 不同，文本在标签前）：
 * [行起始ms,行时长ms]字(字起始ms,字时长ms)字(字起始ms,字时长ms)...
 *
 * 转换为统一格式时把文本移到标签后并补 ",0"。
 */
public final class QrcLyricCodec {

    private static final byte[] KEY = "!@#)(*$%123ZXC!@!@#)(NHL".getBytes(java.nio.charset.StandardCharsets.US_ASCII);

    private static final Pattern LYRIC_CONTENT =
            Pattern.compile("LyricContent\\s*=\\s*\"([\\s\\S]*?)\"(?=\\s*/?>)");

    private static final Pattern LINE =
            Pattern.compile("^\\[(\\d+)\\s*,\\s*(\\d+)](.*)$");
    private static final Pattern WORD =
            Pattern.compile("([^()\\r\\n]*)\\((\\d+)\\s*,\\s*(\\d+)\\)");

    private QrcLyricCodec() {}

    /** 判断字符串是否为纯十六进制 */
    private static boolean isHex(String s) {
        if (s == null || s.isEmpty() || s.length() % 2 != 0) return false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (!((c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F'))) return false;
        }
        return true;
    }

    private static byte[] fromHex(String s) {
        int len = s.length() / 2;
        byte[] out = new byte[len];
        for (int i = 0; i < len; i++) {
            out[i] = (byte) Integer.parseInt(s.substring(i * 2, i * 2 + 2), 16);
        }
        return out;
    }

    /** 3DES EDE ECB 解密（零填充，输出按原长截断） */
    private static byte[] decrypt3Des(byte[] data, byte[] key) throws Exception {
        Cipher cipher = Cipher.getInstance("DESede/ECB/NoPadding");
        SecretKeySpec keySpec = new SecretKeySpec(key, "DESede");
        cipher.init(Cipher.DECRYPT_MODE, keySpec);
        // 零填充到 8 字节倍数
        int padded = ((data.length + 7) / 8) * 8;
        byte[] paddedData = new byte[padded];
        System.arraycopy(data, 0, paddedData, 0, data.length);
        byte[] decrypted = cipher.doFinal(paddedData);
        // 截断到原长度
        byte[] result = new byte[data.length];
        System.arraycopy(decrypted, 0, result, 0, data.length);
        return result;
    }

    private static byte[] inflate(byte[] data) throws Exception {
        InflaterInputStream iis = new InflaterInputStream(new ByteArrayInputStream(data));
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[4096];
        int n;
        while ((n = iis.read(buf)) >= 0) {
            if (n > 0) out.write(buf, 0, n);
        }
        return out.toByteArray();
    }

    /** 解码 XML 实体 */
    public static String decodeXmlEntities(String s) {
        if (s == null) return "";
        return s.replace("&#58;", ":")
                .replace("&#46;", ".")
                .replace("&apos;", "'")
                .replace("&quot;", "\"")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&amp;", "&");
    }

    /** 解密 QRC 加密内容，返回明文歌词文本 */
    public static String decryptTimedPayload(String str) throws Exception {
        if (str == null || str.isEmpty()) return "";
        if (!isHex(str)) {
            return decodeXmlEntities(str).trim();
        }
        byte[] encrypted = fromHex(str);
        byte[] decrypted = decrypt3Des(encrypted, KEY);
        byte[] inflated = inflate(decrypted);
        String xml = new String(inflated, java.nio.charset.StandardCharsets.UTF_8);
        Matcher m = LYRIC_CONTENT.matcher(xml);
        if (m.find()) {
            xml = m.group(1);
        }
        return decodeXmlEntities(xml).trim();
    }

    /** 将 QRC 明文转为统一增强时间线格式 */
    public static String toEnhancedTimeline(String qrc) {
        if (qrc == null || qrc.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (String raw : qrc.split("\\r?\\n")) {
            Matcher lm = LINE.matcher(raw);
            if (!lm.matches()) continue;
            sb.append('[').append(lm.group(1)).append(',').append(lm.group(2)).append(']');
            String content = lm.group(3);
            Matcher wm = WORD.matcher(content);
            while (wm.find()) {
                String text = wm.group(1);
                if (!text.isEmpty()) {
                    sb.append('(').append(wm.group(2)).append(',').append(wm.group(3)).append(",0)");
                    sb.append(text);
                }
            }
            sb.append('\n');
        }
        return sb.toString();
    }

    /** 转为普通 LRC */
    public static String toPlainLrc(String qrc) {
        if (qrc == null || qrc.isEmpty()) return "";
        // 已是标准 LRC
        if (qrc.contains("[") && qrc.contains(":") && qrc.contains("]") && !qrc.contains(",")) {
            return qrc;
        }
        StringBuilder sb = new StringBuilder();
        for (String raw : qrc.split("\\r?\\n")) {
            Matcher lm = LINE.matcher(raw);
            if (!lm.matches()) continue;
            long start = Long.parseLong(lm.group(1));
            String content = lm.group(3);
            // 去除 word 标签
            String text = content.replaceAll("\\(\\d+\\s*,\\s*\\d+\\)", "");
            sb.append(String.format(java.util.Locale.ROOT, "[%02d:%02d.%03d]",
                    start / 60000, (start / 1000) % 60, start % 1000));
            sb.append(text.trim()).append('\n');
        }
        return sb.toString();
    }

    /** 提取 QRC XML 中指定标签的 CDATA 内容（翻译/罗马音） */
    public static String encryptedContent(String xml, String tagName) {
        if (xml == null || tagName == null) return "";
        Pattern p = Pattern.compile(
                "<" + Pattern.quote(tagName) + "[^>]*>.*?<!\\[CDATA\\[(.*?)]]>",
                Pattern.DOTALL);
        Matcher m = p.matcher(xml);
        return m.find() ? m.group(1).trim() : "";
    }
}
