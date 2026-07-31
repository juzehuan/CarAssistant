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
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.net.Uri;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * 已安装应用工具
 */
public final class AppUtil {

    private AppUtil() {}

    public static class AppInfo {
        public String packageName;
        public String name;
        public String versionName;
        public long versionCode;
        public long cacheSize;
        public long codeSize;
        public long dataSize;
        public boolean system;
        public Drawable icon;
        public Intent launchIntent;
    }

    public static List<AppInfo> getInstalledApps(Context ctx, int filter) {
        // filter: 0=all, 1=user only, 2=system only
        PackageManager pm = ctx.getPackageManager();
        List<PackageInfo> packages = pm.getInstalledPackages(0);
        List<AppInfo> result = new ArrayList<>();
        for (PackageInfo pi : packages) {
            ApplicationInfo ai = pi.applicationInfo;
            boolean isSystem = (ai.flags & ApplicationInfo.FLAG_SYSTEM) != 0;
            if (filter == 1 && isSystem) continue;
            if (filter == 2 && !isSystem) continue;

            AppInfo info = new AppInfo();
            info.packageName = pi.packageName;
            info.name = pm.getApplicationLabel(ai).toString();
            info.versionName = pi.versionName;
            info.versionCode = android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P
                    ? pi.getLongVersionCode() : pi.versionCode;
            info.system = isSystem;
            info.icon = pm.getApplicationIcon(ai);
            info.launchIntent = pm.getLaunchIntentForPackage(pi.packageName);
            info.cacheSize = 0;
            info.codeSize = 0;
            info.dataSize = 0;

            // 尝试获取大小（Android 8+ 普通应用无法获取其他应用缓存大小，使用 sourceDir 估算）
            try {
                File source = new File(ai.sourceDir);
                if (source.exists()) info.codeSize = source.length();
            } catch (Exception ignored) {}

            result.add(info);
        }
        Collections.sort(result, new Comparator<AppInfo>() {
            @Override
            public int compare(AppInfo o1, AppInfo o2) {
                return o1.name.compareToIgnoreCase(o2.name);
            }
        });
        return result;
    }

    /** 卸载应用 */
    public static Intent buildUninstallIntent(String pkg) {
        Intent intent = new Intent(Intent.ACTION_DELETE);
        intent.setData(Uri.parse("package:" + pkg));
        return intent;
    }

    /** 应用详情页 */
    public static Intent buildAppDetailIntent(String pkg) {
        Intent intent = new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
        intent.setData(Uri.parse("package:" + pkg));
        return intent;
    }
}
