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
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Environment;
import android.text.TextUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 垃圾清理工具：
 * - 缓存目录扫描与清理
 * - 残留 APK 安装包扫描与清理
 * - 日志、缩略图等临时文件清理
 * - Root 模式：扫描并清理其他应用的缓存（/data/data/<pkg>/cache、code_cache、外部缓存）
 *
 * 设计说明：
 * - 非 root 模式仅清理本应用可访问目录（Android 8+ Scoped Storage 限制）
 * - root 模式通过 du/rm 批量处理，单条 su 命令完成扫描，避免多次 fork su
 * - root 清理路径仅限第三方应用，跳过系统应用与本应用，避免误伤
 */
public final class CleanUtil {

    private CleanUtil() {}

    public static class JunkGroup {
        public String name;
        public String desc;
        /** 普通可删除文件（本应用可访问） */
        public List<File> files = new ArrayList<>();
        /** 需要 root 权限 rm -rf 的路径（其他应用缓存） */
        public List<String> rootPaths = new ArrayList<>();
        public long size;
        public boolean selected = true;
        /** 是否为 root 专属清理项（UI 据此标注） */
        public boolean rootOnly = false;
    }

    /** 扫描垃圾（兼容旧调用，默认非 root） */
    public static List<JunkGroup> scan(Context ctx) {
        return scan(ctx, false);
    }

    /** 扫描垃圾，hasRoot=true 时额外扫描其他应用缓存 */
    public static List<JunkGroup> scan(Context ctx, boolean hasRoot) {
        List<JunkGroup> groups = new ArrayList<>();

        // 1. 本应用缓存（诚实标注）
        groups.add(scanDir("本应用缓存", ctx.getCacheDir()));
        groups.add(scanDir("本应用外部缓存", ctx.getExternalCacheDir()));
        // code_cache：编译码缓存（可安全删除）
        groups.add(scanDir("编译码缓存", ctx.getCodeCacheDir()));

        // 2. 公共下载目录中的多余 APK
        groups.add(scanApk());

        // 3. 公共目录的缩略图
        File dcim = new File(Environment.getExternalStorageDirectory(), "DCIM/.thumbnails");
        groups.add(scanDir("缩略图缓存", dcim));
        File picturesThumb = new File(Environment.getExternalStorageDirectory(), "Pictures/.thumbnails");
        groups.add(scanDir("图片缩略图", picturesThumb));

        // 4. 临时文件 .tmp / .log
        groups.add(scanExt("临时文件", Environment.getExternalStorageDirectory(), ".tmp"));
        groups.add(scanExt("日志文件", Environment.getExternalStorageDirectory(), ".log"));

        // 5. 本应用数据库临时文件（WAL/SHM，可安全删除但会丢失未提交事务）
        groups.add(scanAppDbTemp(ctx));

        // 6. Root 模式：扫描其他应用缓存
        if (hasRoot) {
            JunkGroup otherCache = scanRootAppsCache(ctx);
            if (otherCache.size > 0) {
                groups.add(otherCache);
            }
            // 扫描系统崩溃日志
            JunkGroup sysLogs = scanRootSystemLogs();
            if (sysLogs.size > 0) {
                groups.add(sysLogs);
            }
        }

        return groups;
    }

    /** 扫描本应用数据库临时文件（.db-wal/.db-shm） */
    private static JunkGroup scanAppDbTemp(Context ctx) {
        JunkGroup g = new JunkGroup();
        g.name = "数据库临时文件";
        File dbDir = ctx.getDatabasePath("dummy").getParentFile();
        if (dbDir != null && dbDir.exists()) {
            File[] files = dbDir.listFiles();
            if (files != null) {
                for (File f : files) {
                    String name = f.getName().toLowerCase();
                    if (f.isFile() && (name.endsWith(".db-wal") || name.endsWith(".db-shm")
                            || name.endsWith(".db-journal"))) {
                        g.files.add(f);
                    }
                }
            }
        }
        g.size = sumSize(g.files);
        g.desc = g.files.size() + " 项 / " + FormatUtil.formatSize(g.size);
        return g;
    }

    /** Root 模式扫描系统崩溃日志（dropbox/anr/tombstones） */
    private static JunkGroup scanRootSystemLogs() {
        JunkGroup g = new JunkGroup();
        g.name = "系统崩溃日志";
        g.rootOnly = true;
        String[] logDirs = {"/data/system/dropbox/", "/data/anr/",
                            "/data/tombstones/", "/data/logs/"};
        long total = 0;
        int fileCount = 0;
        for (String dir : logDirs) {
            ShellUtil.Result r = ShellUtil.execRoot("du -sb '" + dir + "' 2>/dev/null");
            if (r.success() && !TextUtils.isEmpty(r.stdout)) {
                String[] parts = r.stdout.trim().split("\t");
                if (parts.length >= 1) {
                    try {
                        long size = Long.parseLong(parts[0].trim());
                        if (size > 0) {
                            g.rootPaths.add(dir);
                            total += size;
                            fileCount++;
                        }
                    } catch (Exception ignored) {}
                }
            }
        }
        g.size = total;
        g.desc = fileCount + " 类日志 / " + FormatUtil.formatSize(total);
        return g;
    }

    private static JunkGroup scanDir(String name, File dir) {
        JunkGroup g = new JunkGroup();
        g.name = name;
        if (dir == null || !dir.exists()) {
            g.desc = "0 项 / 0 B";
            return g;
        }
        collectFiles(dir, g.files);
        g.size = sumSize(g.files);
        g.desc = g.files.size() + " 项 / " + FormatUtil.formatSize(g.size);
        return g;
    }

    private static JunkGroup scanApk() {
        JunkGroup g = new JunkGroup();
        g.name = "多余APK安装包";
        File downloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
        if (downloads != null && downloads.exists()) {
            File[] files = downloads.listFiles();
            if (files != null) {
                for (File f : files) {
                    if (f.isFile() && f.getName().toLowerCase().endsWith(".apk")) {
                        g.files.add(f);
                    }
                }
            }
        }
        g.size = sumSize(g.files);
        g.desc = g.files.size() + " 项 / " + FormatUtil.formatSize(g.size);
        return g;
    }

    private static JunkGroup scanExt(String name, File root, String suffix) {
        JunkGroup g = new JunkGroup();
        g.name = name;
        if (root != null && root.exists()) {
            collectBySuffix(root, suffix, g.files, 3);
        }
        g.size = sumSize(g.files);
        g.desc = g.files.size() + " 项 / " + FormatUtil.formatSize(g.size);
        return g;
    }

    /**
     * Root 模式扫描其他应用缓存
     * 单条 su 命令批量扫描所有第三方应用的 cache/code_cache/外部 cache，避免多次 fork
     */
    private static JunkGroup scanRootAppsCache(Context ctx) {
        JunkGroup g = new JunkGroup();
        g.name = "其他应用缓存";
        g.rootOnly = true;

        // 收集第三方应用包名集合（用于过滤 du 输出）
        Set<String> thirdPartyPkgs = new HashSet<>();
        PackageManager pm = ctx.getPackageManager();
        List<ApplicationInfo> apps = pm.getInstalledApplications(0);
        for (ApplicationInfo ai : apps) {
            if ((ai.flags & ApplicationInfo.FLAG_SYSTEM) == 0
                    && !ctx.getPackageName().equals(ai.packageName)) {
                thirdPartyPkgs.add(ai.packageName);
            }
        }
        if (thirdPartyPkgs.isEmpty()) {
            g.desc = "0 个应用 / 0 B";
            return g;
        }

        // 单条命令扫描所有 /data/data/<pkg>/cache 和 code_cache
        // 输出格式：<size>\t<path>
        String cmd = "du -sb /data/data/*/cache /data/data/*/code_cache 2>/dev/null";
        ShellUtil.Result r = ShellUtil.execRoot(cmd);
        long total = 0;
        int appCount = 0;
        Set<String> countedPkgs = new HashSet<>();
        if (r.success() && !TextUtils.isEmpty(r.stdout)) {
            String[] lines = r.stdout.split("\n");
            for (String line : lines) {
                // 格式：<size>\t<path>
                String[] parts = line.split("\t");
                if (parts.length < 2) continue;
                try {
                    long size = Long.parseLong(parts[0].trim());
                    String path = parts[1].trim();
                    // 从路径提取包名：/data/data/<pkg>/cache
                    String pkg = extractPkgFromPath(path);
                    if (pkg != null && thirdPartyPkgs.contains(pkg)) {
                        g.rootPaths.add(path);
                        total += size;
                        if (countedPkgs.add(pkg)) appCount++;
                    }
                } catch (Exception ignored) {}
            }
        }

        // 外部缓存 /sdcard/Android/data/<pkg>/cache（单独扫描，du 可能无权限）
        String extCmd = "du -sb /sdcard/Android/data/*/cache 2>/dev/null";
        ShellUtil.Result r2 = ShellUtil.execRoot(extCmd);
        if (r2.success() && !TextUtils.isEmpty(r2.stdout)) {
            String[] lines = r2.stdout.split("\n");
            for (String line : lines) {
                String[] parts = line.split("\t");
                if (parts.length < 2) continue;
                try {
                    long size = Long.parseLong(parts[0].trim());
                    String path = parts[1].trim();
                    // /sdcard/Android/data/<pkg>/cache
                    String pkg = extractExtPkgFromPath(path);
                    if (pkg != null && thirdPartyPkgs.contains(pkg)) {
                        g.rootPaths.add(path);
                        total += size;
                        if (countedPkgs.add(pkg)) appCount++;
                    }
                } catch (Exception ignored) {}
            }
        }

        g.size = total;
        g.desc = appCount + " 个应用 / " + FormatUtil.formatSize(total);
        return g;
    }

    /** 从 /data/data/<pkg>/cache 提取 <pkg> */
    private static String extractPkgFromPath(String path) {
        if (path == null) return null;
        String prefix = "/data/data/";
        if (path.startsWith(prefix)) {
            String rest = path.substring(prefix.length());
            int slash = rest.indexOf('/');
            return slash > 0 ? rest.substring(0, slash) : rest;
        }
        return null;
    }

    /** 从 /sdcard/Android/data/<pkg>/cache 提取 <pkg> */
    private static String extractExtPkgFromPath(String path) {
        if (path == null) return null;
        String prefix = "/sdcard/Android/data/";
        if (path.startsWith(prefix)) {
            String rest = path.substring(prefix.length());
            int slash = rest.indexOf('/');
            return slash > 0 ? rest.substring(0, slash) : rest;
        }
        return null;
    }

    private static void collectFiles(File dir, List<File> out) {
        if (dir == null || !dir.exists()) return;
        File[] files = dir.listFiles();
        if (files == null) return;
        for (File f : files) {
            if (f.isDirectory()) {
                collectFiles(f, out);
            } else if (f.isFile()) {
                out.add(f);
            }
        }
    }

    private static void collectBySuffix(File dir, String suffix, List<File> out, int depth) {
        if (depth <= 0 || dir == null || !dir.exists()) return;
        File[] files = dir.listFiles();
        if (files == null) return;
        for (File f : files) {
            if (f.isDirectory()) {
                // 跳过 Android/ 数据目录避免误伤
                if (!"Android".equals(f.getName())) {
                    collectBySuffix(f, suffix, out, depth - 1);
                }
            } else if (f.isFile() && f.getName().toLowerCase().endsWith(suffix)) {
                out.add(f);
            }
        }
    }

    private static long sumSize(List<File> files) {
        long s = 0;
        for (File f : files) s += f.length();
        return s;
    }

    /**
     * 清理指定分组中的文件
     * - 普通文件：直接 delete()
     * - root 路径：用 rm -rf 清理（清理前先用 du 统计大小用于汇报）
     */
    public static long clean(List<JunkGroup> groups) {
        long cleaned = 0;
        for (JunkGroup g : groups) {
            if (!g.selected) continue;
            // 普通文件
            for (File f : g.files) {
                if (f.exists()) {
                    long size = f.length();
                    if (f.delete()) {
                        cleaned += size;
                    }
                }
            }
            // root 路径
            if (!g.rootPaths.isEmpty()) {
                cleaned += cleanRootPaths(g.rootPaths);
            }
        }
        return cleaned;
    }

    /** 批量清理 root 路径，单条 su 命令完成，返回清理字节数 */
    private static long cleanRootPaths(List<String> paths) {
        long cleaned = 0;
        // 先统计总大小
        for (String path : paths) {
            cleaned += rootDirSize(path);
        }
        // 拼接 rm -rf 命令（一次执行）
        StringBuilder cmd = new StringBuilder("rm -rf");
        for (String path : paths) {
            cmd.append(" '").append(path).append("'");
        }
        ShellUtil.Result r = ShellUtil.execRoot(cmd.toString());
        if (!r.success()) {
            // 失败则不计入清理量
            return 0;
        }
        return cleaned;
    }

    /** root 模式获取目录大小 */
    private static long rootDirSize(String path) {
        ShellUtil.Result r = ShellUtil.execRoot("du -sb '" + path + "' 2>/dev/null | cut -f1");
        if (r.success() && !TextUtils.isEmpty(r.stdout)) {
            try { return Long.parseLong(r.stdout.trim()); } catch (Exception ignored) {}
        }
        return 0;
    }

    /** 获取所有应用缓存总大小（粗略，root 下更准确） */
    public static long getAppsCacheSize(Context ctx) {
        PackageManager pm = ctx.getPackageManager();
        List<ApplicationInfo> apps = pm.getInstalledApplications(0);
        long total = 0;
        for (ApplicationInfo ai : apps) {
            try {
                File cacheDir = new File(ai.dataDir, "cache");
                if (cacheDir.exists()) {
                    total += dirSize(cacheDir);
                }
            } catch (Exception ignored) {}
        }
        return total;
    }

    private static long dirSize(File dir) {
        if (dir == null || !dir.exists()) return 0;
        long size = 0;
        File[] files = dir.listFiles();
        if (files == null) return 0;
        for (File f : files) {
            if (f.isDirectory()) size += dirSize(f);
            else size += f.length();
        }
        return size;
    }
}
