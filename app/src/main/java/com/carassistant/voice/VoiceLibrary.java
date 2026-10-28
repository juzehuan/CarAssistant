package com.carassistant.voice;

import android.content.Context;
import android.content.res.Resources;
import android.preference.PreferenceManager;
import android.util.Log;
import java.io.BufferedReader;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.json.JSONArray;
import org.json.JSONObject;

/* JADX INFO: loaded from: classes.dex */
public final class VoiceLibrary {
    private static final String CATALOG_ASSET = "voicepacks/catalog.json";
    private static final String FILES_PREFIX = "voicepacks/files/";
    private static final String TAG = "CarVoiceLib";
    public static final String VOICES_DIR = "voices";
    private static volatile boolean sCatalogLoaded;
    private static final List<CatalogEntry> sCatalog = new ArrayList();
    private static final List<String> sPackOrder = new ArrayList();
    private static final Map<String, String> sPackNames = new HashMap();
    private static final Map<String, CatalogEntry> sById = new HashMap();
    private static final Comparator<VoiceClip> BY_LABEL = new Comparator<VoiceClip>() { // from class: com.carassistant.voice.VoiceLibrary.1
        @Override // java.util.Comparator
        public int compare(VoiceClip voiceClip, VoiceClip voiceClip2) {
            int iCompareToIgnoreCase = (voiceClip.packName != null ? voiceClip.packName : "").compareToIgnoreCase(voiceClip2.packName != null ? voiceClip2.packName : "");
            if (iCompareToIgnoreCase != 0) {
                return iCompareToIgnoreCase;
            }
            return voiceClip.label.compareToIgnoreCase(voiceClip2.label);
        }
    };

    public static final class FolderInfo {
        public final int count;
        public final String name;
        public final String packId;

        public FolderInfo(String str, String str2, int i) {
            this.packId = str;
            this.name = str2;
            this.count = i;
        }
    }

    private static final class CatalogEntry {
        final String file;
        final String folder;
        final String id;
        final String label;
        final String pack;
        final Set<String> tags;

        CatalogEntry(String str, String str2, String str3, String str4, String str5, Set<String> set) {
            this.id = str;
            this.pack = str2;
            this.folder = str3;
            this.file = str4;
            this.label = str5;
            this.tags = set;
        }
    }

    private VoiceLibrary() {
    }

    public static File voicesDir(Context context) {
        File file = new File(context.getApplicationContext().getFilesDir(), VOICES_DIR);
        if (!file.exists()) {
            file.mkdirs();
        }
        return file;
    }

    public static List<FolderInfo> listFolders(Context context) {
        ensureCatalog(context);
        ArrayList arrayList = new ArrayList();
        int i = 0;
        while (true) {
            List<String> list = sPackOrder;
            if (i < list.size()) {
                String str = list.get(i);
                Map<String, String> map = sPackNames;
                String str2 = map.containsKey(str) ? map.get(str) : str;
                int i2 = 0;
                int i3 = 0;
                while (true) {
                    List<CatalogEntry> list2 = sCatalog;
                    if (i2 >= list2.size()) {
                        break;
                    }
                    if (str.equals(list2.get(i2).pack)) {
                        i3++;
                    }
                    i2++;
                }
                if (i3 > 0) {
                    arrayList.add(new FolderInfo(str, str2, i3));
                }
                i++;
            } else {
                return arrayList;
            }
        }
    }

    public static List<VoiceClip> listClipsInPack(Context context, String str) {
        ensureCatalog(context);
        ArrayList arrayList = new ArrayList();
        if (str == null) {
            return arrayList;
        }
        int i = 0;
        while (true) {
            List<CatalogEntry> list = sCatalog;
            if (i < list.size()) {
                CatalogEntry catalogEntry = list.get(i);
                if (str.equals(catalogEntry.pack)) {
                    arrayList.add(toClip(catalogEntry));
                }
                i++;
            } else {
                Collections.sort(arrayList, BY_LABEL);
                return arrayList;
            }
        }
    }

    public static List<VoiceClip> searchClips(Context context, String str) {
        ensureCatalog(context);
        ArrayList arrayList = new ArrayList();
        String strTrim = str != null ? str.trim() : "";
        if (strTrim.length() == 0) {
            return arrayList;
        }
        int i = 0;
        while (true) {
            List<CatalogEntry> list = sCatalog;
            if (i < list.size()) {
                CatalogEntry catalogEntry = list.get(i);
                VoiceClip clip = toClip(catalogEntry);
                if (containsIgnoreCase(displayLine(clip), strTrim) || containsIgnoreCase(catalogEntry.label, strTrim) || containsIgnoreCase(clip.packName, strTrim)) {
                    arrayList.add(clip);
                }
                i++;
            } else {
                Collections.sort(arrayList, BY_LABEL);
                return arrayList;
            }
        }
    }

    public static List<VoiceClip> listAll(Context context) {
        return listForEvent(context, null);
    }

    public static List<VoiceClip> listForEvent(Context context, String str) {
        ensureCatalog(context);
        ArrayList arrayList = new ArrayList();
        ArrayList arrayList2 = new ArrayList();
        int i = 0;
        while (true) {
            List<CatalogEntry> list = sCatalog;
            if (i < list.size()) {
                CatalogEntry catalogEntry = list.get(i);
                VoiceClip clip = toClip(catalogEntry);
                if (str != null && catalogEntry.tags.contains(str)) {
                    arrayList.add(clip);
                } else {
                    arrayList2.add(clip);
                }
                i++;
            } else {
                Comparator<VoiceClip> comparator = BY_LABEL;
                Collections.sort(arrayList, comparator);
                Collections.sort(arrayList2, comparator);
                ArrayList arrayList3 = new ArrayList();
                arrayList3.addAll(arrayList);
                arrayList3.addAll(arrayList2);
                addBuiltInRaws(context, arrayList3);
                addUserFiles(context, arrayList3);
                return arrayList3;
            }
        }
    }

    private static VoiceClip toClip(CatalogEntry catalogEntry) {
        String str;
        Map<String, String> map = sPackNames;
        String str2 = map.containsKey(catalogEntry.pack) ? map.get(catalogEntry.pack) : catalogEntry.pack;
        if (catalogEntry.folder != null && catalogEntry.folder.length() > 0) {
            str = catalogEntry.folder;
        } else {
            str = str2;
        }
        return new VoiceClip(VoiceClip.KIND_ASSET, catalogEntry.id, VoiceLabels.cleanLabel(catalogEntry.label), str, FILES_PREFIX + catalogEntry.file);
    }

    public static String displayLine(VoiceClip voiceClip) {
        String strCleanLabel = VoiceLabels.cleanLabel(voiceClip.label);
        if (voiceClip.packName != null && voiceClip.packName.length() > 0 && !strCleanLabel.startsWith(voiceClip.packName)) {
            return voiceClip.packName + " · " + strCleanLabel;
        }
        return strCleanLabel;
    }

    private static boolean containsIgnoreCase(String str, String str2) {
        return (str == null || str2 == null || str.toLowerCase().indexOf(str2.toLowerCase()) < 0) ? false : true;
    }

    private static void addUserFiles(Context context, List<VoiceClip> list) {
        File[] fileArrListFiles = voicesDir(context).listFiles();
        if (fileArrListFiles == null) {
            return;
        }
        for (File file : fileArrListFiles) {
            if (file != null && file.isFile()) {
                String name = file.getName();
                String lowerCase = name.toLowerCase();
                if (lowerCase.endsWith(".wav") || lowerCase.endsWith(".pcm") || lowerCase.endsWith(".mp3") || lowerCase.endsWith(".ogg")) {
                    list.add(new VoiceClip(VoiceClip.KIND_FILE, name, VoiceLabels.clip(name)));
                }
            }
        }
    }

    /* JADX WARN: Code duplicated, block: B:33:0x00a5  */
    private static void ensureCatalog(Context context) {
        String str;
        if (sCatalogLoaded) {
            return;
        }
        synchronized (VoiceLibrary.class) {
            if (sCatalogLoaded) {
                return;
            }
            try {
                JSONObject jSONObject = new JSONObject(readAll(context.getAssets().open(CATALOG_ASSET)));
                JSONArray jSONArrayOptJSONArray = jSONObject.optJSONArray("packs");
                if (jSONArrayOptJSONArray != null) {
                    for (int i = 0; i < jSONArrayOptJSONArray.length(); i++) {
                        JSONObject jSONObject2 = jSONArrayOptJSONArray.getJSONObject(i);
                        String string = jSONObject2.getString("id");
                        sPackNames.put(string, jSONObject2.optString("name", string));
                        sPackOrder.add(string);
                    }
                }
                JSONArray jSONArrayOptJSONArray2 = jSONObject.optJSONArray("clips");
                if (jSONArrayOptJSONArray2 != null) {
                    for (int i2 = 0; i2 < jSONArrayOptJSONArray2.length(); i2++) {
                        JSONObject jSONObject3 = jSONArrayOptJSONArray2.getJSONObject(i2);
                        HashSet hashSet = new HashSet();
                        JSONArray jSONArrayOptJSONArray3 = jSONObject3.optJSONArray("tags");
                        if (jSONArrayOptJSONArray3 != null) {
                            for (int i3 = 0; i3 < jSONArrayOptJSONArray3.length(); i3++) {
                                hashSet.add(jSONArrayOptJSONArray3.getString(i3));
                            }
                        }
                        String strOptString = jSONObject3.optString("pack", "");
                        String strOptString2 = jSONObject3.optString("folder", "");
                        if (strOptString2.length() == 0) {
                            Map<String, String> map = sPackNames;
                            if (map.containsKey(strOptString)) {
                                str = map.get(strOptString);
                            } else {
                                str = strOptString2;
                            }
                        } else {
                            str = strOptString2;
                        }
                        CatalogEntry catalogEntry = new CatalogEntry(jSONObject3.getString("id"), strOptString, str, jSONObject3.getString(VoiceClip.KIND_FILE), jSONObject3.optString("label", jSONObject3.getString("id")), hashSet);
                        sCatalog.add(catalogEntry);
                        sById.put(catalogEntry.id, catalogEntry);
                    }
                }
                Log.i(TAG, "catalog clips=" + sCatalog.size() + " folders=" + sPackOrder.size());
            } catch (Throwable th) {
                Log.w(TAG, "catalog load failed", th);
            }
            sCatalogLoaded = true;
        }
    }

    private static String readAll(InputStream inputStream) throws Exception {
        BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(inputStream, Charset.forName("UTF-8")));
        StringBuilder sb = new StringBuilder();
        while (true) {
            String line = bufferedReader.readLine();
            if (line != null) {
                sb.append(line).append('\n');
            } else {
                bufferedReader.close();
                return sb.toString();
            }
        }
    }

    private static void addBuiltInRaws(Context context, List<VoiceClip> list) {
        try {
            boolean z = false;
            for (Field field : Class.forName(context.getPackageName() + ".R$raw").getFields()) {
                String name = field.getName();
                if (name != null && !name.startsWith("aapt_")) {
                    list.add(new VoiceClip(VoiceClip.KIND_RAW, name, "内置 · " + VoiceLabels.clip(name)));
                    z = true;
                }
            }
            if (z) {
                return;
            }
        } catch (Throwable th) {
        }
        String[] strArr = {VoiceEvents.GEAR_P, VoiceEvents.GEAR_R, VoiceEvents.GEAR_N, VoiceEvents.GEAR_D, VoiceEvents.DOOR_FL_OPEN, VoiceEvents.DOOR_FL_CLOSE, VoiceEvents.DOOR_FR_OPEN, VoiceEvents.DOOR_FR_CLOSE, VoiceEvents.DOOR_RL_OPEN, VoiceEvents.DOOR_RL_CLOSE, VoiceEvents.DOOR_RR_OPEN, VoiceEvents.DOOR_RR_CLOSE};
        Resources resources = context.getResources();
        String packageName = context.getPackageName();
        for (int i = 0; i < 12; i++) {
            if (resources.getIdentifier(strArr[i], VoiceClip.KIND_RAW, packageName) != 0) {
                list.add(new VoiceClip(VoiceClip.KIND_RAW, strArr[i], "内置 · " + VoiceLabels.clip(strArr[i])));
            }
        }
    }

    public static String getSelectedId(Context context, String str) {
        return PreferenceManager.getDefaultSharedPreferences(context).getString(VoiceEvents.prefKey(str), VoiceEvents.defaultClipId(VoiceEvents.find(str)));
    }

    public static void setSelectedId(Context context, String str, String str2) {
        PreferenceManager.getDefaultSharedPreferences(context).edit().putString(VoiceEvents.prefKey(str), str2).apply();
    }

    public static String displayName(Context context, String str) {
        String selectedId = getSelectedId(context, str);
        VoiceClip id = VoiceClip.parseId(selectedId);
        if (id != null && VoiceClip.KIND_ASSET.equals(id.kind)) {
            ensureCatalog(context);
            CatalogEntry catalogEntry = sById.get(id.name);
            if (catalogEntry != null) {
                return VoiceLabels.cleanLabel(catalogEntry.label);
            }
        }
        return VoiceLabels.clipFromId(selectedId);
    }

    public static PlayHandle play(Context context, String str, boolean z) {
        VoiceClip id = VoiceClip.parseId(str);
        if (id == null) {
            return null;
        }
        if (VoiceClip.KIND_ASSET.equals(id.kind)) {
            ensureCatalog(context);
            CatalogEntry catalogEntry = sById.get(id.name);
            if (catalogEntry == null) {
                Log.w(TAG, "missing asset id " + id.name);
                return null;
            }
            return PlayHandle.of(MediaClipPlayer.startAsset(context, FILES_PREFIX + catalogEntry.file, z));
        }
        if (VoiceClip.KIND_FILE.equals(id.kind)) {
            File file = new File(voicesDir(context), id.name);
            if (!file.isFile()) {
                Log.w(TAG, "missing file " + file);
                return null;
            }
            String lowerCase = id.name.toLowerCase();
            if (lowerCase.endsWith(".mp3") || lowerCase.endsWith(".ogg") || lowerCase.endsWith(".m4a")) {
                return PlayHandle.of(MediaClipPlayer.startFile(context, file.getAbsolutePath(), z));
            }
            return PlayHandle.of(WavAudioPlayer.startFile(context, file.getAbsolutePath(), z));
        }
        int identifier = context.getResources().getIdentifier(id.name, VoiceClip.KIND_RAW, context.getPackageName());
        if (identifier == 0) {
            Log.w(TAG, "missing raw " + id.name);
            return null;
        }
        return PlayHandle.of(WavAudioPlayer.start(context, identifier, z));
    }

    public static PlayHandle playEvent(Context context, String str, boolean z) {
        return play(context, getSelectedId(context, str), z);
    }
}
