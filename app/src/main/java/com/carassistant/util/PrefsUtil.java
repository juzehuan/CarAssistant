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
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 偏好持久化工具：
 * - 悬浮球快捷应用列表
 * - 内存清理白名单
 * - 首次启动权限引导标记
 */
public final class PrefsUtil {

    private PrefsUtil() {}

    private static final String PREF_NAME = "car_assistant_prefs";
    private static final String KEY_FLOAT_APPS = "float_app_packages";
    private static final String KEY_WHITELIST = "clean_whitelist";
    private static final String KEY_BOOT_APPS = "boot_app_packages";

    // ---------------- 悬浮球快捷按钮开关 ----------------
    // 总开关 + 4 个子开关（返回/主页/最近任务/锁屏）
    private static final String KEY_FLOAT_QUICK_MASTER = "float_quick_master";
    private static final String KEY_FLOAT_QUICK_BACK = "float_quick_back";
    private static final String KEY_FLOAT_QUICK_HOME = "float_quick_home";
    private static final String KEY_FLOAT_QUICK_RECENTS = "float_quick_recents";
    private static final String KEY_FLOAT_QUICK_LOCK = "float_quick_lock";

    // ---------------- 首次启动权限引导 ----------------
    private static final String KEY_PERMISSION_GUIDE_DONE = "permission_guide_done";

    private static SharedPreferences sp(Context ctx) {
        return ctx.getApplicationContext().getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    // ---------------- 悬浮球应用 ----------------

    /** 获取悬浮球快捷应用包名列表（按用户排序） */
    public static List<String> getFloatApps(Context ctx) {
        String json = sp(ctx).getString(KEY_FLOAT_APPS, null);
        if (json == null) return new ArrayList<>();
        try {
            JSONArray arr = new JSONArray(json);
            List<String> result = new ArrayList<>();
            for (int i = 0; i < arr.length(); i++) {
                result.add(arr.getString(i));
            }
            return result;
        } catch (JSONException e) {
            return new ArrayList<>();
        }
    }

    public static void setFloatApps(Context ctx, List<String> packages) {
        JSONArray arr = new JSONArray();
        if (packages != null) {
            // 去重保序
            Set<String> seen = new LinkedHashSet<>(packages);
            for (String pkg : seen) {
                if (pkg != null && !pkg.isEmpty()) arr.put(pkg);
            }
        }
        sp(ctx).edit().putString(KEY_FLOAT_APPS, arr.toString()).apply();
    }

    public static boolean isFloatApp(Context ctx, String pkg) {
        if (pkg == null) return false;
        return getFloatApps(ctx).contains(pkg);
    }

    public static void addFloatApp(Context ctx, String pkg) {
        if (pkg == null) return;
        List<String> list = getFloatApps(ctx);
        if (!list.contains(pkg)) {
            list.add(pkg);
            setFloatApps(ctx, list);
        }
    }

    public static void removeFloatApp(Context ctx, String pkg) {
        if (pkg == null) return;
        List<String> list = getFloatApps(ctx);
        if (list.remove(pkg)) {
            setFloatApps(ctx, list);
        }
    }

    // ---------------- 悬浮球快捷按钮开关 ----------------

    /** 快捷按钮总开关（控制整个快捷按钮区是否显示），默认开启 */
    public static boolean isFloatQuickMasterOn(Context ctx) {
        return sp(ctx).getBoolean(KEY_FLOAT_QUICK_MASTER, true);
    }

    public static void setFloatQuickMaster(Context ctx, boolean on) {
        sp(ctx).edit().putBoolean(KEY_FLOAT_QUICK_MASTER, on).apply();
    }

    public static boolean isFloatQuickBackOn(Context ctx) {
        return sp(ctx).getBoolean(KEY_FLOAT_QUICK_BACK, true);
    }

    public static void setFloatQuickBack(Context ctx, boolean on) {
        sp(ctx).edit().putBoolean(KEY_FLOAT_QUICK_BACK, on).apply();
    }

    public static boolean isFloatQuickHomeOn(Context ctx) {
        return sp(ctx).getBoolean(KEY_FLOAT_QUICK_HOME, true);
    }

    public static void setFloatQuickHome(Context ctx, boolean on) {
        sp(ctx).edit().putBoolean(KEY_FLOAT_QUICK_HOME, on).apply();
    }

    public static boolean isFloatQuickRecentsOn(Context ctx) {
        return sp(ctx).getBoolean(KEY_FLOAT_QUICK_RECENTS, true);
    }

    public static void setFloatQuickRecents(Context ctx, boolean on) {
        sp(ctx).edit().putBoolean(KEY_FLOAT_QUICK_RECENTS, on).apply();
    }

    /** 锁屏按钮默认关闭（需 Android 9+ 和无障碍服务） */
    public static boolean isFloatQuickLockOn(Context ctx) {
        return sp(ctx).getBoolean(KEY_FLOAT_QUICK_LOCK, false);
    }

    public static void setFloatQuickLock(Context ctx, boolean on) {
        sp(ctx).edit().putBoolean(KEY_FLOAT_QUICK_LOCK, on).apply();
    }

    // ---------------- 清理白名单 ----------------

    /** 内存清理白名单（包名集合），白名单中的应用不会被 kill */
    public static Set<String> getWhitelist(Context ctx) {
        String json = sp(ctx).getString(KEY_WHITELIST, null);
        if (json == null) return Collections.emptySet();
        try {
            JSONArray arr = new JSONArray(json);
            Set<String> result = new LinkedHashSet<>();
            for (int i = 0; i < arr.length(); i++) {
                result.add(arr.getString(i));
            }
            return result;
        } catch (JSONException e) {
            return Collections.emptySet();
        }
    }

    public static void setWhitelist(Context ctx, Set<String> packages) {
        JSONArray arr = new JSONArray();
        if (packages != null) {
            for (String pkg : packages) {
                if (pkg != null && !pkg.isEmpty()) arr.put(pkg);
            }
        }
        sp(ctx).edit().putString(KEY_WHITELIST, arr.toString()).apply();
    }

    public static boolean isWhitelisted(Context ctx, String pkg) {
        if (pkg == null) return false;
        return getWhitelist(ctx).contains(pkg);
    }

    public static void addWhitelist(Context ctx, String pkg) {
        if (pkg == null) return;
        Set<String> set = new LinkedHashSet<>(getWhitelist(ctx));
        if (set.add(pkg)) {
            setWhitelist(ctx, set);
        }
    }

    public static void removeWhitelist(Context ctx, String pkg) {
        if (pkg == null) return;
        Set<String> set = new LinkedHashSet<>(getWhitelist(ctx));
        if (set.remove(pkg)) {
            setWhitelist(ctx, set);
        }
    }

    // ---------------- 开机自启应用 ----------------

    /** 获取开机自启应用包名列表（按用户排序） */
    public static List<String> getBootApps(Context ctx) {
        String json = sp(ctx).getString(KEY_BOOT_APPS, null);
        if (json == null) return new ArrayList<>();
        try {
            JSONArray arr = new JSONArray(json);
            List<String> result = new ArrayList<>();
            for (int i = 0; i < arr.length(); i++) {
                result.add(arr.getString(i));
            }
            return result;
        } catch (JSONException e) {
            return new ArrayList<>();
        }
    }

    public static void setBootApps(Context ctx, List<String> packages) {
        JSONArray arr = new JSONArray();
        if (packages != null) {
            Set<String> seen = new LinkedHashSet<>(packages);
            for (String pkg : seen) {
                if (pkg != null && !pkg.isEmpty()) arr.put(pkg);
            }
        }
        sp(ctx).edit().putString(KEY_BOOT_APPS, arr.toString()).apply();
    }

    public static boolean isBootApp(Context ctx, String pkg) {
        if (pkg == null) return false;
        return getBootApps(ctx).contains(pkg);
    }

    public static void addBootApp(Context ctx, String pkg) {
        if (pkg == null) return;
        List<String> list = getBootApps(ctx);
        if (!list.contains(pkg)) {
            list.add(pkg);
            setBootApps(ctx, list);
        }
    }

    public static void removeBootApp(Context ctx, String pkg) {
        if (pkg == null) return;
        List<String> list = getBootApps(ctx);
        if (list.remove(pkg)) {
            setBootApps(ctx, list);
        }
    }

    // ---------------- 首次启动权限引导 ----------------

    /** 是否已完成首次权限引导（完成后不再弹窗） */
    public static boolean isPermissionGuideDone(Context ctx) {
        return sp(ctx).getBoolean(KEY_PERMISSION_GUIDE_DONE, false);
    }

    public static void setPermissionGuideDone(Context ctx, boolean done) {
        sp(ctx).edit().putBoolean(KEY_PERMISSION_GUIDE_DONE, done).apply();
    }
}
