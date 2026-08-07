package com.carassistant.ui;

import android.graphics.Color;

/**
 * 音乐伴侣预设主题（6 套配色方案）。
 * 每个主题定义强调色、背景渐变起止色、辉光色。
 */
public enum MusicTheme {

    RED(0, "网易红",
            0xFFEE0A24,  // accent
            0xFF0D0D12,  // bgStart
            0xFF1A1418,  // bgMid
            0xFF251A1E,  // bgEnd
            0x44EE0A24,  // glow
            0xFFEE0A24), // seekbar

    BLUE(1, "深海蓝",
            0xFF2196F3,
            0xFF0A0D18,
            0xFF101424,
            0xFF161E32,
            0x442196F3,
            0xFF2196F3),

    PURPLE(2, "暗夜紫",
            0xFF9C27B0,
            0xFF0E0D15,
            0xFF14101C,
            0xFF1E1528,
            0x449C27B0,
            0xFF9C27B0),

    GREEN(3, "极光绿",
            0xFF4CAF50,
            0xFF0A100D,
            0xFF0E1612,
            0xFF121E16,
            0x444CAF50,
            0xFF4CAF50),

    ORANGE(4, "橙焰",
            0xFFFF9800,
            0xFF110E08,
            0xFF1A140C,
            0xFF281A0A,
            0x44FF9800,
            0xFFFF9800),

    SILVER(5, "银灰",
            0xFFB0BEC5,
            0xFF101214,
            0xFF181A1C,
            0xFF1E2022,
            0x44B0BEC5,
            0xFFB0BEC5);

    public final int id;
    public final String label;
    public final int accent;
    public final int bgStart;
    public final int bgMid;
    public final int bgEnd;
    public final int glow;
    public final int seekbar;

    MusicTheme(int id, String label, int accent, int bgStart, int bgMid,
               int bgEnd, int glow, int seekbar) {
        this.id = id;
        this.label = label;
        this.accent = accent;
        this.bgStart = bgStart;
        this.bgMid = bgMid;
        this.bgEnd = bgEnd;
        this.glow = glow;
        this.seekbar = seekbar;
    }

    public static MusicTheme fromId(int id) {
        for (MusicTheme t : values()) {
            if (t.id == id) return t;
        }
        return RED;
    }

    /** 根据强调色反查最接近的主题（用于专辑封面取色时匹配） */
    public static MusicTheme nearestOf(int color) {
        float[] hsv = new float[3];
        Color.colorToHSV(color, hsv);
        float h = hsv[0];
        // 红 0-20 或 340-360
        if (h < 20 || h > 340) return RED;
        // 蓝 190-260
        if (h >= 190 && h < 260) return BLUE;
        // 紫 260-310
        if (h >= 260 && h < 310) return PURPLE;
        // 绿 80-170
        if (h >= 80 && h < 170) return GREEN;
        // 橙 20-45
        if (h >= 20 && h < 45) return ORANGE;
        return SILVER;
    }
}
