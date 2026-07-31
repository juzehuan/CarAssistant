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

package com.carassistant;

import android.app.Application;
import android.os.Build;
import android.util.Log;

import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Application 入口
 *
 * 注册全局未捕获异常处理器，将崩溃堆栈写入日志文件，
 * 便于在无 ADB 环境下车机设备上定位闪退原因。
 */
public class App extends Application {

    private static final String TAG = "CarAssistant.App";
    private static final String CRASH_DIR = "crash_logs";
    private static App instance;

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        // 注册全局未捕获异常处理器
        Thread.UncaughtExceptionHandler defaultHandler = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> {
            // 写入崩溃日志到文件
            writeCrashLog(thread, throwable);
            // 交还默认处理器（让系统弹出 FC 对话框或重启进程）
            if (defaultHandler != null) {
                defaultHandler.uncaughtException(thread, throwable);
            }
        });
    }

    public static App get() {
        return instance;
    }

    /**
     * 将崩溃堆栈写入应用私有外部目录下的 crash_logs/ 文件夹
     * 路径示例：/Android/data/com.carassistant/files/crash_logs/crash_20260730_153000.txt
     */
    private void writeCrashLog(Thread thread, Throwable throwable) {
        try {
            File dir = new File(getExternalFilesDir(null), CRASH_DIR);
            if (!dir.exists() && !dir.mkdirs()) {
                dir = new File(getFilesDir(), CRASH_DIR);
                if (!dir.exists()) dir.mkdirs();
            }

            String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.CHINA)
                    .format(new Date());
            File file = new File(dir, "crash_" + timestamp + ".txt");

            StringWriter sw = new StringWriter();
            PrintWriter pw = new PrintWriter(sw);
            pw.println("====== CarAssistant Crash Report ======");
            pw.println("Time: " + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.CHINA)
                    .format(new Date()));
            pw.println("Device: " + Build.MANUFACTURER + " " + Build.MODEL);
            pw.println("Android API: " + Build.VERSION.SDK_INT + " (" + Build.VERSION.RELEASE + ")");
            pw.println("App Version: " + getPackageManager().getPackageInfo(getPackageName(), 0).versionName);
            pw.println("Thread: " + thread.getName() + " (id=" + thread.getId() + ")");
            pw.println();
            pw.println("------ Exception Stack ------");
            throwable.printStackTrace(pw);
            pw.println();
            pw.println("------ Thread State ------");
            pw.println("State: " + thread.getState());
            pw.println("Priority: " + thread.getPriority());
            pw.println("isDaemon: " + thread.isDaemon());
            pw.println();

            FileWriter fw = new FileWriter(file);
            fw.write(sw.toString());
            fw.close();

            Log.e(TAG, "Crash log saved to: " + file.getAbsolutePath());
            Log.e(TAG, sw.toString());
        } catch (Exception e) {
            Log.e(TAG, "Failed to write crash log", e);
        }
    }

    /** 获取崩溃日志目录（供设置页或其他入口读取展示） */
    public static File getCrashLogDir() {
        if (instance == null) return null;
        File dir = new File(instance.getExternalFilesDir(null), CRASH_DIR);
        if (!dir.exists()) dir = new File(instance.getFilesDir(), CRASH_DIR);
        return dir;
    }
}
