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
 * distribution, sale or reverse engineering without written permission is
 * prohibited and subject to legal action.
 */

package com.carassistant.util;

import android.content.Context;
import android.os.Build;
import android.os.Environment;
import android.os.StatFs;
import android.os.storage.StorageManager;
import android.os.storage.StorageVolume;
import android.util.Base64;
import android.util.Log;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 存储工具：内部存储、SD 卡、U 盘容量与路径
 *
 * 识别策略（多层级兜底，任一层拿到就合并结果）：
 * 1. 公开 API StorageManager.getStorageVolumes()（API 24+）
 * 2. 隐藏 API StorageManager.getVolumeList() 反射（老系统兜底）
 * 3. 直接扫描常见挂载根目录（车机 ROM 的 StorageManager 实现残缺时兜底）
 *
 * 注意：不能只依赖反射隐藏 API —— Android 9 起对 targetSdk >= 28 的应用
 * 启用隐藏 API 限制，getVolumeList() 在部分版本会直接失效导致只能拿到内部存储。
 */
public final class StorageUtil {

    private static final String TAG = "StorageUtil";

    private StorageUtil() {}

    public static class StorageInfo {
        public String path;
        public String label;
        public long total;
        public long available;
        public boolean removable; // true 表示可拔插（SD卡/U盘）
        public boolean usb;       // true 表示 U 盘
    }

    public static long getTotalSize(File path) {
        if (path == null || !path.exists()) return 0;
        try {
            StatFs stat = new StatFs(path.getAbsolutePath());
            return stat.getTotalBytes();
        } catch (Exception e) {
            return 0;
        }
    }

    public static long getAvailableSize(File path) {
        if (path == null || !path.exists()) return 0;
        try {
            StatFs stat = new StatFs(path.getAbsolutePath());
            return stat.getAvailableBytes();
        } catch (Exception e) {
            return 0;
        }
    }

    public static File getInternalStorage() {
        return Environment.getExternalStorageDirectory();
    }

    /**
     * 获取所有已挂载的存储卷（内部存储 / SD 卡 / U 盘）
     */
    public static List<StorageInfo> getAllStorages(Context ctx) {
        List<StorageInfo> list = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        StorageManager sm = (StorageManager) ctx.getSystemService(Context.STORAGE_SERVICE);

        // ---- 1) 公开 API ----
        if (sm != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            try {
                List<StorageVolume> volumes = sm.getStorageVolumes();
                if (volumes != null) {
                    for (StorageVolume sv : volumes) {
                        StorageInfo info = fromStorageVolume(ctx, sv);
                        if (info != null && seen.add(info.path)) {
                            Log.d(TAG, "volume(api): " + info.path + " removable=" + info.removable
                                    + " usb=" + info.usb);
                            list.add(info);
                        }
                    }
                }
            } catch (Throwable t) {
                Log.w(TAG, "getStorageVolumes failed", t);
            }
        }

        // ---- 2) 隐藏 API 兜底（Android 8/9 部分 ROM） ----
        if (sm != null) {
            try {
                Method getVolumeList = StorageManager.class.getMethod("getVolumeList");
                Object[] volumes = (Object[]) getVolumeList.invoke(sm);
                if (volumes != null) {
                    for (Object vol : volumes) {
                        StorageInfo info = fromVolumeObject(ctx, vol);
                        if (info != null && seen.add(info.path)) {
                            Log.d(TAG, "volume(hidden): " + info.path + " removable=" + info.removable
                                    + " usb=" + info.usb);
                            list.add(info);
                        }
                    }
                }
            } catch (Throwable t) {
                Log.w(TAG, "getVolumeList reflection failed", t);
            }
        }

        // ---- 3) 文件系统扫描兜底 ----
        scanMountPoints(ctx, list, seen);

        // ---- 4) 保证内部存储一定在列表首位 ----
        ensureInternal(ctx, list, seen);

        return list;
    }

    // ==================== 解析 StorageVolume ====================

    private static StorageInfo fromStorageVolume(Context ctx, StorageVolume sv) {
        if (sv == null) return null;

        File dir = null;
        // API 30+ 有公开 API
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                dir = sv.getDirectory();
            } catch (Throwable ignored) {}
        }
        // 反射隐藏方法 getPathFile()（API 24+）
        if (dir == null) {
            try {
                Method m = sv.getClass().getMethod("getPathFile");
                Object r = m.invoke(sv);
                if (r instanceof File) dir = (File) r;
            } catch (Throwable ignored) {}
        }
        // 反射隐藏方法 getPath()（老版本）
        if (dir == null) {
            try {
                Method m = sv.getClass().getMethod("getPath");
                Object r = m.invoke(sv);
                if (r instanceof String) dir = new File((String) r);
            } catch (Throwable ignored) {}
        }
        if (dir == null) return null;

        boolean removable = false;
        try {
            removable = sv.isRemovable();
        } catch (Throwable ignored) {}

        String fwLabel = null;
        try {
            fwLabel = sv.getDescription(ctx);
        } catch (Throwable ignored) {}

        return buildInfo(ctx, dir, removable, fwLabel);
    }

    /** 解析反射得到的 volume 对象（可能是 StorageVolume 或老版本的内部类） */
    @SuppressWarnings("Reflection")
    private static StorageInfo fromVolumeObject(Context ctx, Object vol) {
        if (vol == null) return null;
        if (vol instanceof StorageVolume) return fromStorageVolume(ctx, (StorageVolume) vol);

        File dir = null;
        try {
            Method m = vol.getClass().getMethod("getPathFile");
            Object r = m.invoke(vol);
            if (r instanceof File) dir = (File) r;
        } catch (Throwable ignored) {}
        if (dir == null) {
            try {
                Method m = vol.getClass().getMethod("getPath");
                Object r = m.invoke(vol);
                if (r instanceof String) dir = new File((String) r);
            } catch (Throwable ignored) {}
        }
        if (dir == null) return null;

        boolean removable = false;
        try {
            Method m = vol.getClass().getMethod("isRemovable");
            Object r = m.invoke(vol);
            if (r instanceof Boolean) removable = (Boolean) r;
        } catch (Throwable ignored) {}

        String fwLabel = null;
        try {
            Method m = vol.getClass().getMethod("getDescription", Context.class);
            Object r = m.invoke(vol, ctx);
            if (r instanceof String) fwLabel = (String) r;
        } catch (Throwable ignored) {}

        return buildInfo(ctx, dir, removable, fwLabel);
    }

    private static StorageInfo buildInfo(Context ctx, File rawDir, boolean removable, String fwLabel) {
        File dir = normalizeDir(rawDir);
        if (dir == null) return null;

        StorageInfo info = new StorageInfo();
        info.path = dir.getAbsolutePath();
        info.removable = removable || !isInternalPath(info.path);
        info.total = getTotalSize(dir);
        info.available = getAvailableSize(dir);
        info.usb = looksLikeUsb(info.path, info.removable);
        info.label = resolveLabel(fwLabel, info.path, info.removable, info.usb);
        return info;
    }

    // ==================== 挂载点扫描兜底 ====================

    /** 常见挂载根目录：StorageManager 拿不到时直接扫文件系统 */
    private static final String[] SCAN_ROOTS = {
            "/storage",
            "/mnt/media_rw",
            "/mnt/usb_storage",
            "/mnt/usbotg",
            "/mnt/usbdisk",
            "/mnt/udisk",
            "/mnt/ext_sd",
            "/mnt/external_sd",
            "/mnt/sdcard2"
    };

    private static void scanMountPoints(Context ctx, List<StorageInfo> list, Set<String> seen) {
        for (String root : SCAN_ROOTS) {
            File rf = new File(root);
            File[] subs;
            try {
                subs = rf.listFiles();
            } catch (Exception e) {
                continue;
            }
            if (subs == null) continue;
            for (File f : subs) {
                if (!f.isDirectory()) continue;
                String p = f.getAbsolutePath();
                if (isInternalPath(p)) continue;
                if (isNoiseMountPoint(p)) continue;
                if (!isMounted(f)) continue;
                if (!seen.add(p)) continue;

                StorageInfo info = new StorageInfo();
                info.path = p;
                info.removable = true;
                info.total = getTotalSize(f);
                info.available = getAvailableSize(f);
                info.usb = looksLikeUsb(p, true);
                info.label = resolveLabel(null, p, true, info.usb);
                Log.d(TAG, "volume(scan): " + p + " usb=" + info.usb);
                list.add(info);
            }
        }
    }

    private static void ensureInternal(Context ctx, List<StorageInfo> list, Set<String> seen) {
        for (StorageInfo s : list) {
            if (!s.removable) return; // 已有内部存储
        }
        File intern = getInternalStorage();
        if (intern == null) return;
        String p = intern.getAbsolutePath();
        if (seen.contains(p)) return;
        StorageInfo info = new StorageInfo();
        info.path = p;
        info.removable = false;
        info.usb = false;
        info.total = getTotalSize(intern);
        info.available = getAvailableSize(intern);
        info.label = "内部存储";
        list.add(0, info);
    }

    // ==================== 路径与状态判定 ====================

    /**
     * 把框架给出的挂载点换成真正可读的那一个。
     * Android 8/9 上反射常拿到 /mnt/media_rw/XXXX-XXXX（普通应用无权访问），
     * 对应的可读路径是 /storage/XXXX-XXXX。
     */
    private static File normalizeDir(File dir) {
        if (dir == null) return null;
        if (isUsable(dir)) return dir;

        String p = dir.getAbsolutePath();
        String seg = lastSegment(p);
        if (seg == null) return dir;

        if (p.startsWith("/mnt/media_rw/")) {
            File alt = new File("/storage/" + seg);
            if (isUsable(alt)) return alt;
        } else if (p.startsWith("/storage/")) {
            File alt = new File("/mnt/media_rw/" + seg);
            if (isUsable(alt)) return alt;
        }
        return dir;
    }

    private static boolean isUsable(File f) {
        return f != null && f.exists() && f.canRead() && getTotalSize(f) > 0;
    }

    /** 是否为内部存储路径（/storage/emulated/...、/storage/self 等） */
    private static boolean isInternalPath(String p) {
        if (p == null) return false;
        File intern = getInternalStorage();
        if (intern != null && p.equals(intern.getAbsolutePath())) return true;
        String lp = p.toLowerCase(Locale.US);
        return lp.startsWith("/storage/emulated")
                || lp.equals("/storage/self")
                || lp.startsWith("/storage/self/")
                || lp.equals("/mnt/sdcard")
                || lp.equals("/sdcard");
    }

    /** 明显不是存储卷的目录，扫描时排除 */
    private static boolean isNoiseMountPoint(String p) {
        String lp = p.toLowerCase(Locale.US);
        return lp.endsWith("/emulated")
                || lp.endsWith("/self")
                || lp.endsWith("/obb")
                || lp.endsWith("/asec")
                || lp.endsWith("/secure")
                || lp.endsWith("/runtime")
                || lp.endsWith("/enc_emulated");
    }

    /** 卷是否已挂载且可用 */
    private static boolean isMounted(File dir) {
        if (dir == null || !dir.exists()) return false;
        String state = null;
        try {
            state = Environment.getExternalStorageState(dir);
        } catch (Exception ignored) {}
        if (Environment.MEDIA_MOUNTED.equals(state)) return true;
        // 明确已卸载 / 已拔出：直接排除（避免把拔掉后残留的空壳目录当成卷）
        if (state != null && (Environment.MEDIA_UNMOUNTED.equals(state)
                || Environment.MEDIA_REMOVED.equals(state)
                || Environment.MEDIA_BAD_REMOVAL.equals(state))) {
            return false;
        }
        // 部分 ROM 对 U 盘返回 unknown/checking：以 /proc/mounts 是否为独立挂载点为准
        if (isRealMountPoint(dir.getAbsolutePath())) return true;
        return dir.canRead() && getTotalSize(dir) > 0;
    }

    /** 该路径是否为 /proc/mounts 中的独立挂载点 */
    private static boolean isRealMountPoint(String path) {
        if (path == null) return false;
        try (BufferedReader br = new BufferedReader(new FileReader("/proc/mounts"))) {
            String line;
            while ((line = br.readLine()) != null) {
                String[] parts = line.split("\\s+");
                if (parts.length >= 2 && parts[1].equals(path)) return true;
            }
        } catch (Exception ignored) {}
        return false;
    }

    /**
     * 判定是否为 U 盘。
     *
     * 不能只看路径关键字：Android 8/9 上 U 盘通常挂载为 /storage/XXXX-XXXX（UUID），
     * 路径里既没有 usb 也没有 udisk。因此再用块设备号/设备名做二次判定：
     * - /dev/block/vold/8:1  major=8 属于 SCSI 磁盘（U 盘走 USB-SCSI）
     * - /dev/block/vold/179:x major=179 属于 MMC（SD 卡）
     */
    private static boolean looksLikeUsb(String path, boolean removable) {
        if (path == null) return false;
        String lp = path.toLowerCase(Locale.US);
        if (lp.contains("usb") || lp.contains("udisk") || lp.contains("usbdisk")
                || lp.contains("usbotg") || lp.contains("otg")) {
            return true;
        }
        if (!removable) return false;

        String dev = findBlockDevice(path);
        if (dev == null) return false;
        String d = dev.toLowerCase(Locale.US);
        if (d.contains("mmcblk")) return false;

        // /dev/block/vold/<major>:<minor>
        Matcher m = Pattern.compile("/dev/block/vold/(\\d+):").matcher(d);
        if (m.find()) {
            try {
                int major = Integer.parseInt(m.group(1));
                // 8, 65-71, 128-135：SCSI 磁盘（sdX，通常即 U 盘）
                if (major == 8 || (major >= 65 && major <= 71) || (major >= 128 && major <= 135)) {
                    return true;
                }
                // 179, 259：MMC（SD 卡）
                return false;
            } catch (Exception ignored) {}
        }

        // /dev/sda1 /dev/sdb1 ...
        Matcher m2 = Pattern.compile("/dev/(sd[a-z])(\\d*)").matcher(d);
        if (m2.find()) return true;

        return false;
    }

    /** 从 /proc/mounts 根据挂载路径定位块设备 */
    private static String findBlockDevice(String mountPath) {
        if (mountPath == null) return null;
        String seg = lastSegment(mountPath);
        String bestDev = null;
        int bestLen = -1;
        try (BufferedReader br = new BufferedReader(new FileReader("/proc/mounts"))) {
            String line;
            while ((line = br.readLine()) != null) {
                String[] parts = line.split("\\s+");
                if (parts.length < 2) continue;
                String dev = parts[0];
                String mp = parts[1];
                boolean match = mountPath.equals(mp)
                        || (seg != null && !seg.isEmpty() && mp.endsWith("/" + seg));
                if (match && mp.length() > bestLen) {
                    bestLen = mp.length();
                    bestDev = dev;
                }
            }
        } catch (Exception ignored) {}
        return bestDev;
    }

    // ==================== 卷标解析 ====================

    private static Boolean sRootCache = null;
    private static boolean hasRoot() {
        if (sRootCache == null) sRootCache = ShellUtil.hasRoot();
        return sRootCache;
    }

    /**
     * 解析存储卷的显示名称。
     * 框架 getDescription() 在部分车机/国产 ROM 上会把 FAT 卷标按错误编码解码成 ???
     * 此时通过 root 直接读取引导扇区的 11 字节 OEM 卷标（中文为 GBK）还原真实名称。
     */
    private static String resolveLabel(String fwLabel, String path,
                                       boolean removable, boolean usb) {
        if (isGoodLabel(fwLabel)) return fwLabel;
        if (hasRoot()) {
            String dev = findBlockDevice(path);
            String raw = readFatLabel(dev);
            if (raw != null) return raw;
        }
        if (isGoodLabel(fwLabel)) return fwLabel;

        if (usb) {
            String seg = lastSegment(path);
            if (seg != null && !seg.isEmpty()) return "U盘(" + seg + ")";
            return "U盘";
        }
        if (removable) {
            String seg = lastSegment(path);
            if (seg != null && !seg.isEmpty()) return "SD卡(" + seg + ")";
            return "SD卡";
        }
        return fwLabel != null && !fwLabel.trim().isEmpty() ? fwLabel : "内部存储";
    }

    private static boolean isGoodLabel(String label) {
        return label != null
                && !label.contains("？")
                && !label.trim().isEmpty()
                && !"可移动存储".equals(label)
                && !"内部存储".equals(label)
                && !"SD卡".equals(label);
    }

    /** 通过 root 读取块设备引导扇区，提取 FAT 卷标（11 字节，OEM 编码） */
    private static String readFatLabel(String dev) {
        if (dev == null) return null;
        ShellUtil.Result r = ShellUtil.execRoot("dd if=" + dev + " bs=512 count=1 2>/dev/null | base64");
        if (!r.success() || r.stdout == null || r.stdout.isEmpty()) return null;
        byte[] sector;
        try {
            sector = Base64.decode(r.stdout.replaceAll("[\\r\\n]", ""), Base64.DEFAULT);
        } catch (Exception e) {
            return null;
        }
        if (sector == null || sector.length < 512) return null;
        String label = decodeOem(Arrays.copyOfRange(sector, 71, 82));
        if (isValidLabel(label)) return label;
        String label2 = decodeOem(Arrays.copyOfRange(sector, 128, 139));
        if (isValidLabel(label2)) return label2;
        return null;
    }

    /** 将 OEM 字节解码为中文（GBK 优先，失败后尝试 UTF-8） */
    private static String decodeOem(byte[] bytes) {
        if (bytes == null) return null;
        String s;
        try {
            s = new String(bytes, "GBK");
        } catch (Exception e) {
            s = new String(bytes);
        }
        if (s.contains("？")) {
            try {
                String u = new String(bytes, "UTF-8");
                if (!u.contains("？")) s = u;
            } catch (Exception ignored) {}
        }
        return s;
    }

    private static boolean isValidLabel(String label) {
        if (label == null) return false;
        String t = label.trim();
        if (t.isEmpty() || "NO NAME".equalsIgnoreCase(t)) return false;
        for (int i = 0; i < t.length(); i++) {
            char c = t.charAt(i);
            if (c != ' ' && c != 0) return true;
        }
        return false;
    }

    private static String lastSegment(String path) {
        if (path == null) return null;
        String p = path.replace('\\', '/');
        if (p.endsWith("/")) p = p.substring(0, p.length() - 1);
        int i = p.lastIndexOf('/');
        return i >= 0 ? p.substring(i + 1) : p;
    }
}
