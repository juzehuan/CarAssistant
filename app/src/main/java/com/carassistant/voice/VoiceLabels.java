package com.carassistant.voice;

/* JADX INFO: loaded from: classes.dex */
public final class VoiceLabels {
    private VoiceLabels() {
    }

    public static String cleanLabel(String str) {
        char cCharAt;
        if (str == null || str.length() == 0) {
            return "";
        }
        if (str.startsWith("喂饭合集")) {
            str = str.substring("喂饭合集".length());
            while (str.length() > 0 && ((cCharAt = str.charAt(0)) == ' ' || cCharAt == 183 || cCharAt == 8226 || cCharAt == '-' || cCharAt == '_' || cCharAt == '/' || cCharAt == '|' || cCharAt == 65295)) {
                str = str.substring(1);
            }
        }
        return str.trim();
    }

    public static String clip(String str) {
        if (str == null) {
            return "未知";
        }
        int iMax = Math.max(str.lastIndexOf(47), str.lastIndexOf(92));
        if (iMax >= 0) {
            str = str.substring(iMax + 1);
        }
        int iLastIndexOf = str.lastIndexOf(46);
        if (iLastIndexOf > 0) {
            str = str.substring(0, iLastIndexOf);
        }
        if (VoiceEvents.GEAR_P.equals(str)) {
            return "P档提示音";
        }
        if (VoiceEvents.GEAR_R.equals(str)) {
            return "倒车循环音";
        }
        if (VoiceEvents.GEAR_N.equals(str)) {
            return "N档提示音";
        }
        if (VoiceEvents.GEAR_D.equals(str)) {
            return "D档提示音";
        }
        if (VoiceEvents.DOOR_FL_OPEN.equals(str)) {
            return "主驾开门";
        }
        if (VoiceEvents.DOOR_FL_CLOSE.equals(str)) {
            return "主驾关门";
        }
        if (VoiceEvents.DOOR_FR_OPEN.equals(str)) {
            return "副驾开门";
        }
        if (VoiceEvents.DOOR_FR_CLOSE.equals(str)) {
            return "副驾关门";
        }
        if (VoiceEvents.DOOR_RL_OPEN.equals(str)) {
            return "左后门开门";
        }
        if (VoiceEvents.DOOR_RL_CLOSE.equals(str)) {
            return "左后门关门";
        }
        if (VoiceEvents.DOOR_RR_OPEN.equals(str)) {
            return "右后门开门";
        }
        if (VoiceEvents.DOOR_RR_CLOSE.equals(str)) {
            return "右后门关门";
        }
        return str;
    }

    public static String clipFromId(String str) {
        VoiceClip id = VoiceClip.parseId(str);
        if (id == null) {
            return "未选择";
        }
        String strClip = clip(id.name);
        VoiceClip.KIND_FILE.equals(id.kind);
        return strClip;
    }
}
