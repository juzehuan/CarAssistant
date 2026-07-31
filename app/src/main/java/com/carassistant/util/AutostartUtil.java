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
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Build;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 自启管理工具
 *
 * 功能：
 * 1. 扫描监听 BOOT_COMPLETED 的应用
 * 2. 通过 root（pm disable-user/enable）启用或禁用应用自启
 *
 * 设计约束：
 * - 应用冻结使用 `pm disable-user --user 0 <pkg>` 避免全局副作用
 * - 危险系统应用（系统UI、设置、电话、Launcher、输入法、本应用）不允许禁用
 * - Root 操作不缓存 su 进程，每次执行完即销毁（由 ShellUtil 保证）
 */
public final class AutostartUtil {

    private AutostartUtil() {}

    public static class AutostartApp {
        public String packageName;
        public String name;
        public boolean system;
        public android.graphics.drawable.Drawable icon;
        public boolean enabled;  // 应用是否启用（pm enable/disable 状态）
    }

    /** 危险系统应用包名/前缀：禁用会导致系统不可用，不允许冻结 */
    private static final String[] DANGEROUS_PKGS = {
            "com.android.systemui",
            "com.android.settings",
            "com.android.phone",
            "com.android.dialer",
            "com.android.contacts",
            "com.android.launcher",
            "com.android.launcher3",
            "com.google.android.googlequicksearchbox",
            "com.android.inputmethod",
            "com.android.inputmethod.latin",
            "com.iflytek.inputmethod",
            "com.baidu.input",
            "com.sohu.inputmethod"
    };

    /** 判断是否为危险系统应用（不可禁用） */
    public static boolean isDangerous(String pkg) {
        if (pkg == null) return true;
        for (String danger : DANGEROUS_PKGS) {
            if (danger.equals(pkg) || pkg.startsWith(danger + ".")) return true;
        }
        return false;
    }

    /** 扫描所有监听 BOOT_COMPLETED 的应用 */
    public static List<AutostartApp> scanAutostartApps(Context ctx) {
        List<AutostartApp> result = new ArrayList<>();
        Intent intent = new Intent(Intent.ACTION_BOOT_COMPLETED);
        PackageManager pm = ctx.getPackageManager();
        List<ResolveInfo> ris = pm.queryBroadcastReceivers(intent, PackageManager.GET_RESOLVED_FILTER);

        if (ris == null) return result;

        for (ResolveInfo ri : ris) {
            try {
                String pkg = ri.activityInfo.packageName;
                if (pkg == null || ctx.getPackageName().equals(pkg)) continue;  // 排除自身
                AutostartApp app = new AutostartApp();
                app.packageName = pkg;
                PackageInfo pi = pm.getPackageInfo(pkg, 0);
                app.name = pm.getApplicationLabel(pi.applicationInfo).toString();
                app.system = (pi.applicationInfo.flags & android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0;
                app.icon = pm.getApplicationIcon(pi.applicationInfo);
                app.enabled = pi.applicationInfo.enabled;
                // 去重
                boolean dup = false;
                for (AutostartApp a : result) {
                    if (a.packageName.equals(pkg)) { dup = true; break; }
                }
                if (!dup) result.add(app);
            } catch (Exception ignored) {}
        }
        return result;
    }

    /**
     * 禁用应用（冻结）：使用 pm disable-user --user 0
     * 需要 root 权限
     * @return 成功返回 true
     */
    public static boolean disableApp(String pkg) {
        if (isDangerous(pkg)) return false;
        ShellUtil.Result r = ShellUtil.execRoot("pm disable-user --user 0 " + pkg);
        return r.success();
    }

    /**
     * 启用应用（解冻）：使用 pm enable
     * 需要 root 权限
     * @return 成功返回 true
     */
    public static boolean enableApp(String pkg) {
        ShellUtil.Result r = ShellUtil.execRoot("pm enable " + pkg);
        return r.success();
    }

    /** 检查当前设备是否具有 root 权限 */
    public static boolean hasRoot() {
        return ShellUtil.hasRoot();
    }

    /** 跳转到应用详情页让用户手动禁用 */
    public static Intent buildAppDetailIntent(String pkg) {
        Intent it = new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
        it.setData(android.net.Uri.parse("package:" + pkg));
        it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        return it;
    }
}
