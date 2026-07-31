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
import android.content.Intent;
import android.net.Uri;
import android.webkit.MimeTypeMap;
import android.widget.Toast;

import androidx.core.content.FileProvider;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * 文件浏览工具
 */
public final class FileUtil {

    private FileUtil() {}

    public static class FileItem {
        public File file;
        public String name;
        public boolean directory;
        public long size;
        public long lastModified;
        public String mimeType;
    }

    public static List<FileItem> listFiles(File dir) {
        List<FileItem> result = new ArrayList<>();
        if (dir == null || !dir.exists() || !dir.isDirectory()) return result;
        File[] files = dir.listFiles();
        if (files == null) return result;
        for (File f : files) {
            FileItem item = new FileItem();
            item.file = f;
            item.name = f.getName();
            item.directory = f.isDirectory();
            item.size = f.length();
            item.lastModified = f.lastModified();
            item.mimeType = getMimeType(f);
            result.add(item);
        }
        // 文件夹在前，按名称排序
        Collections.sort(result, new Comparator<FileItem>() {
            @Override
            public int compare(FileItem o1, FileItem o2) {
                if (o1.directory != o2.directory) return o1.directory ? -1 : 1;
                return o1.name.compareToIgnoreCase(o2.name);
            }
        });
        return result;
    }

    public static String getMimeType(File file) {
        String name = file.getName();
        String ext = name.substring(name.lastIndexOf('.') + 1).toLowerCase();
        String mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext);
        return mime == null ? "*/*" : mime;
    }

    public static String formatTime(long millis) {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
                .format(new Date(millis));
    }

    /** 判断是否为 APK 安装包 */
    public static boolean isApk(File file) {
        if (file == null || file.isDirectory()) return false;
        String name = file.getName().toLowerCase();
        return name.endsWith(".apk");
    }

    /** 获取 FileProvider Uri */
    private static Uri getUriForFile(Context ctx, File file) {
        return FileProvider.getUriForFile(ctx,
                ctx.getPackageName() + ".fileprovider", file);
    }

    /** 打开文件（使用系统默认应用） */
    public static void openFile(Context ctx, File file) {
        if (file == null || !file.exists()) return;
        Intent intent = new Intent(Intent.ACTION_VIEW);
        String mime = getMimeType(file);
        Uri uri = getUriForFile(ctx, file);
        intent.setDataAndType(uri, mime);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            ctx.startActivity(intent);
        } catch (Exception e) {
            // 没有匹配应用
            intent.setDataAndType(uri, "*/*");
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            try {
                ctx.startActivity(Intent.createChooser(intent, "打开方式"));
            } catch (Exception ignored) {}
        }
    }

    /**
     * 安装 APK
     * Android 7+ 必须使用 FileProvider（file:// 会触发 FileUriExposedException）
     * Android 8+ 需要 REQUEST_INSTALL_PACKAGES 权限
     */
    public static void installApk(Context ctx, File file) {
        if (file == null || !file.exists()) return;
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setDataAndType(getUriForFile(ctx, file), "application/vnd.android.package-archive");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            ctx.startActivity(intent);
        } catch (Exception e) {
            // 兜底：使用 chooser，避免 file:// 触发 FileUriExposedException
            try {
                Intent chooser = Intent.createChooser(intent, "选择安装程序");
                chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                ctx.startActivity(chooser);
            } catch (Exception ignored) {
                Toast.makeText(ctx, "无法启动安装器，请检查权限", Toast.LENGTH_SHORT).show();
            }
        }
    }
}
