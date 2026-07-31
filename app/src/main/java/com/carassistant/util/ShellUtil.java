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

import android.text.TextUtils;

import java.io.BufferedReader;
import java.io.InputStreamReader;

/**
 * Shell 命令执行工具
 *
 * 设计要点（遵循项目约束）：
 * - 不缓存 su 进程，每次操作完成后立即销毁
 * - 执行失败返回非零 exitCode，调用方根据结果判断
 * - 所有方法在调用方线程同步执行，建议在后台线程调用
 */
public final class ShellUtil {

    private ShellUtil() {}

    public static class Result {
        /** 进程退出码：0=成功，非0=失败 */
        public int exitCode = -1;
        /** 标准输出 */
        public String stdout = "";
        /** 错误输出 */
        public String stderr = "";
        public boolean success() { return exitCode == 0; }
    }

    /**
     * 以 root 权限执行命令（su -c）
     * 注意：每次调用都会启动一个新的 su 进程，执行完毕立即销毁，不缓存
     */
    public static Result execRoot(String command) {
        Result result = new Result();
        if (TextUtils.isEmpty(command)) return result;
        Process process = null;
        BufferedReader outReader = null;
        BufferedReader errReader = null;
        try {
            process = Runtime.getRuntime().exec(new String[]{"su", "-c", command});
            // 不需要输入，直接关闭 stdin
            process.getOutputStream().close();
            outReader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            errReader = new BufferedReader(new InputStreamReader(process.getErrorStream()));
            StringBuilder out = new StringBuilder();
            StringBuilder err = new StringBuilder();
            String line;
            while ((line = outReader.readLine()) != null) {
                if (out.length() > 0) out.append("\n");
                out.append(line);
            }
            while ((line = errReader.readLine()) != null) {
                if (err.length() > 0) err.append("\n");
                err.append(line);
            }
            result.stdout = out.toString();
            result.stderr = err.toString();
            result.exitCode = process.waitFor();
        } catch (Exception e) {
            result.exitCode = -1;
            result.stderr = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
        } finally {
            if (outReader != null) try { outReader.close(); } catch (Exception ignored) {}
            if (errReader != null) try { errReader.close(); } catch (Exception ignored) {}
            if (process != null) process.destroy();
        }
        return result;
    }

    /** 检查当前设备是否具有 root 权限 */
    public static boolean hasRoot() {
        Result r = execRoot("id");
        return r.success() && r.stdout != null && r.stdout.contains("uid=0");
    }
}
