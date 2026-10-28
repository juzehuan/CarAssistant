package com.carassistant.voice;

/* JADX INFO: loaded from: classes.dex */
public final class VoiceClip {
    public static final String KIND_ASSET = "asset";
    public static final String KIND_FILE = "file";
    public static final String KIND_RAW = "raw";
    public final String assetPath;
    public final String id;
    public final String kind;
    public final String label;
    public final String name;
    public final String packName;

    public VoiceClip(String str, String str2, String str3) {
        this(str, str2, str3, null, null);
    }

    public VoiceClip(String str, String str2, String str3, String str4, String str5) {
        this.kind = str;
        this.name = str2;
        this.label = str3 == null ? str2 : str3;
        this.packName = str4;
        this.assetPath = str5;
        this.id = str + ":" + str2;
    }

    public static VoiceClip parseId(String str) {
        if (str == null || str.length() == 0) {
            return null;
        }
        int iIndexOf = str.indexOf(58);
        if (iIndexOf <= 0 || iIndexOf >= str.length() - 1) {
            return new VoiceClip(KIND_RAW, str, str);
        }
        String strSubstring = str.substring(0, iIndexOf);
        String strSubstring2 = str.substring(iIndexOf + 1);
        return new VoiceClip(strSubstring, strSubstring2, strSubstring2);
    }
}
