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

import java.io.File;
import java.lang.reflect.Method;
import java.util.ArrayList;
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
                    StorageInfo info = parseStorageVolume(vol);
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
    private static StorageInfo parseStorageVolume(Object vol) {
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

            // description
            try {
                Method getDescription = clz.getMethod("getDescription", Context.class);
                // 暂不传 context
                info.label = (String) getDescription.invoke(vol, (Object) null);
            } catch (Exception ignored) {}
            if (info.label == null) info.label = info.removable ? "可移动存储" : "内部存储";

            File f = new File(info.path);
            info.total = getTotalSize(f);
            info.available = getAvailableSize(f);

            // 判定是否为 U 盘（路径特征）
            String p = info.path.toLowerCase();
            info.usb = info.removable && (p.contains("usb") || p.contains("udisk")
                    || p.contains("/mnt/media_rw/"));
        } catch (Exception e) {
            return null;
        }
        return info;
    }
}
