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

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.widget.Toast;

import com.carassistant.R;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * 方控映射（第三方应用 com.hzsoft.keymapx）的集成入口。
 *
 * <p>该应用以【原样内嵌】的方式随车机助手一起分发：原始 APK 放在
 * {@code app/src/main/assets/keymap.apk}，一个字节都没改。所以这里只做两件事：
 * <ol>
 *   <li>已安装 —— 直接把它的主界面拉起来；</li>
 *   <li>未安装 —— 把 assets 里的包释放到私有缓存目录，交给系统安装器安装。</li>
 * </ol>
 *
 * <p>为什么不能直接读 assets：安装器是另一个进程，拿不到我们 APK 内部的文件描述符，
 * 必须先落成真实文件。落盘后的安装动作直接复用 {@link FileUtil#installApk}，
 * 与文件管理页安装 APK 走的是同一条已经验证过的路径（FileProvider + ACTION_VIEW）。
 */
public final class KeyMapLauncher {

    /** 方控映射的包名 */
    public static final String PKG = "com.hzsoft.keymapx";

    /** 方控映射的桌面入口 Activity */
    private static final String MAIN_ACTIVITY = "com.hzsoft.sidebar.KeyMapSettingsActivity";

    /** 内嵌包在 assets 中的文件名 */
    private static final String ASSET_NAME = "keymap.apk";

    /** 释放到缓存目录后使用的文件名 */
    private static final String APK_FILE_NAME = "keymap-1.1.apk";

    private KeyMapLauncher() {}

    /** 方控映射是否已安装 */
    public static boolean isInstalled(Context ctx) {
        try {
            ctx.getPackageManager().getPackageInfo(PKG, 0);
            return true;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    /**
     * 首页「方控映射」卡片的统一入口：已安装则打开，未安装则安装。
     */
    public static void open(Activity act) {
        if (isInstalled(act)) {
            launchInstalled(act);
        } else {
            installFromAssets(act);
        }
    }

    /** 拉起已安装的方控映射 */
    private static void launchInstalled(Activity act) {
        Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.setComponent(new ComponentName(PKG, MAIN_ACTIVITY));
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            act.startActivity(intent);
            return;
        } catch (Exception e) {
            // 上游换了入口类名时兜底：用系统记录的启动 Activity
            Intent fallback = act.getPackageManager().getLaunchIntentForPackage(PKG);
            if (fallback != null) {
                fallback.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                try {
                    act.startActivity(fallback);
                    return;
                } catch (Exception ignored) {
                    // 落到下面统一提示
                }
            }
            Toast.makeText(act, R.string.keymap_launch_failed, Toast.LENGTH_LONG).show();
        }
    }

    /** 释放内嵌包并交给系统安装器 */
    private static void installFromAssets(Activity act) {
        Toast.makeText(act, R.string.keymap_prepare_install, Toast.LENGTH_SHORT).show();
        try {
            File apk = extractApk(act);
            FileUtil.installApk(act, apk);
        } catch (Exception e) {
            String msg = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            Toast.makeText(act, act.getString(R.string.keymap_install_failed, msg),
                    Toast.LENGTH_LONG).show();
        }
    }

    /**
     * 把 assets 中的内嵌包整包写到私有缓存目录。
     * 每次都重写：文件只有 200 多 KB，重写比「判断上次是否写完整」更省心也更安全。
     */
    private static File extractApk(Activity act) throws Exception {
        File dir = new File(act.getCacheDir(), "bundled_apk");
        if (!dir.exists() && !dir.mkdirs()) {
            throw new Exception("无法创建缓存目录 " + dir.getAbsolutePath());
        }
        File out = new File(dir, APK_FILE_NAME);
        try (InputStream in = act.getAssets().open(ASSET_NAME);
             OutputStream os = new FileOutputStream(out)) {
            byte[] buf = new byte[16 * 1024];
            int n;
            while ((n = in.read(buf)) > 0) {
                os.write(buf, 0, n);
            }
            os.flush();
        }
        return out;
    }
}
