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

import android.content.Context;
import android.os.Environment;
import android.os.StatFs;
import android.os.storage.StorageManager;
import android.os.storage.StorageVolume;

import android.util.Base64;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 存储工具：内部存储、SD 卡、U 盘容量与路径
 */
public final class StorageUtil {

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
     * 通过 StorageManager 获取所有挂载的存储卷
     */
    @SuppressWarnings({"unchecked", "Reflection"})
    public static List<StorageInfo> getAllStorages(Context ctx) {
        List<StorageInfo> list = new ArrayList<>();
        StorageManager sm = (StorageManager) ctx.getSystemService(Context.STORAGE_SERVICE);
        if (sm == null) return list;

        // 优先使用反射获取所有 Volume（兼容 Android 8）
        try {
            Method getVolumeList = StorageManager.class.getMethod("getVolumeList");
            Object[] volumes = (Object[]) getVolumeList.invoke(sm);
            if (volumes != null) {
                for (Object vol : volumes) {
                    StorageInfo info = parseStorageVolume(vol, ctx);
                    if (info != null) list.add(info);
                }
            }
        } catch (Exception e) {
            // ignore
        }

        // 兜底：至少保证内部存储
        if (list.isEmpty()) {
            File intern = getInternalStorage();
            if (intern != null && intern.exists()) {
                StorageInfo info = new StorageInfo();
                info.path = intern.getAbsolutePath();
                info.label = "内部存储";
                info.total = getTotalSize(intern);
                info.available = getAvailableSize(intern);
                info.removable = false;
                info.usb = false;
                list.add(info);
            }
        }
        return list;
    }

    @SuppressWarnings("Reflection")
    private static StorageInfo parseStorageVolume(Object vol, Context ctx) {
        StorageInfo info = new StorageInfo();
        try {
            Class<?> clz = vol.getClass();

            // path
            try {
                Method getPath = clz.getMethod("getPath");
                info.path = (String) getPath.invoke(vol);
            } catch (Exception e) {
                // Android Q+ 通过 getPathFile
                try {
                    Method getPathFile = clz.getMethod("getPathFile");
                    File f = (File) getPathFile.invoke(vol);
                    if (f != null) info.path = f.getAbsolutePath();
                } catch (Exception ignored) {}
            }
            if (info.path == null) return null;

            // removable
            try {
                Method isRemovable = clz.getMethod("isRemovable");
                info.removable = (boolean) isRemovable.invoke(vol);
            } catch (Exception ignored) {}

            // 框架卷标（部分机型对 FAT 卷标按错误编码解码，得到 ??????）
            String fwLabel = null;
            try {
                Method getDescription = clz.getMethod("getDescription", Context.class);
                Object r = getDescription.invoke(vol, ctx);
                if (r instanceof String) fwLabel = (String) r;
            } catch (Exception ignored) {}

            File f = new File(info.path);
            info.total = getTotalSize(f);
            info.available = getAvailableSize(f);

            // 判定是否为 U 盘（路径特征）
            String p = info.path.toLowerCase();
            info.usb = info.removable && (p.contains("usb") || p.contains("udisk")
                    || p.contains("/mnt/media_rw/"));

            // 解析最终显示名称（修复 FAT 卷标乱码 ?????）
            info.label = resolveLabel(fwLabel, info.path, info.removable, info.usb);
        } catch (Exception e) {
            return null;
        }
        return info;
    }

    private static Boolean sRootCache = null;
    private static boolean hasRoot() {
        if (sRootCache == null) sRootCache = ShellUtil.hasRoot();
        return sRootCache;
    }

    /**
     * 解析存储卷的显示名称。
     * 框架 getDescription() 在部分车机/国产 ROM 上会把 FAT 卷标按错误编码解码成 ?????，
     * 此时通过 root 直接读取引导扇区的 11 字节 OEM 卷标（中文为 GBK）还原真实名称。
     */
    private static String resolveLabel(String fwLabel, String path,
                                       boolean removable, boolean usb) {
        // 框架已给出正常名称（非乱码、非通用占位）则直接使用
        if (fwLabel != null && !fwLabel.contains("？")
                && !fwLabel.trim().isEmpty()
                && !"可移动存储".equals(fwLabel)
                && !"内部存储".equals(fwLabel)) {
            return fwLabel;
        }
        // 框架解码失败：尝试直接读取 FAT 引导扇区卷标（仅在确实需要时触发 root）
        if (hasRoot()) {
            String dev = findBlockDevice(path);
            String raw = readFatLabel(dev);
            if (raw != null) return raw;
        }
        // 框架名称虽不完整但不是乱码，保留
        if (fwLabel != null && !fwLabel.contains("？") && !fwLabel.trim().isEmpty()) {
            return fwLabel;
        }
        // 兜底：用路径中的卷 UUID 生成可读名称，避免出现 ??????
        if (usb || removable) {
            String seg = lastSegment(path);
            if (seg != null && !seg.isEmpty()) return "U盘(" + seg + ")";
            return "U盘";
        }
        return fwLabel != null ? fwLabel : (removable ? "可移动存储" : "内部存储");
    }

    /** 从 /proc/mounts 根据挂载路径（或其 UUID 片段）定位块设备 */
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
                        || mp.equals(mountPath)
                        || (seg != null && mp.endsWith("/" + seg))
                        || (seg != null && mp.contains(seg));
                if (match && mp.length() > bestLen) {
                    bestLen = mp.length();
                    bestDev = dev;
                }
            }
        } catch (Exception ignored) {}
        return bestDev;
    }

    /** 通过 root 读取块设备引导扇区，提取 FAT 卷标（11 字节，OEM 编码） */
    private static String readFatLabel(String dev) {
        if (dev == null) return null;
        // dd 读取首扇区并通过 base64 输出，避免二进制经 shell 字符串传递被篡改
        ShellUtil.Result r = ShellUtil.execRoot("dd if=" + dev + " bs=512 count=1 2>/dev/null | base64");
        if (!r.success() || r.stdout == null || r.stdout.isEmpty()) return null;
        byte[] sector;
        try {
            sector = Base64.decode(r.stdout.replaceAll("[\\r\\n]", ""), Base64.DEFAULT);
        } catch (Exception e) {
            return null;
        }
        if (sector == null || sector.length < 512) return null;
        // FAT12/16/32：卷标位于偏移 71，共 11 字节（OEM 代码页，中文设备为 GBK）
        String label = decodeOem(Arrays.copyOfRange(sector, 71, 82));
        if (isValidLabel(label)) return label;
        // exFAT：部分实现在引导扇区偏移 0x80 同样保留 11 字节卷标，作为兜底尝试
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
