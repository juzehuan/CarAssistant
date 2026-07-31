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

import java.text.DecimalFormat;

/**
 * 格式化工具：文件大小、日期等
 */
public final class FormatUtil {

    private FormatUtil() {}

    private static final String[] UNITS = {"B", "KB", "MB", "GB", "TB"};

    public static String formatSize(long size) {
        if (size <= 0) return "0 B";
        int digitGroups = (int) (Math.log10((double) size) / Math.log10(1024));
        if (digitGroups >= UNITS.length) digitGroups = UNITS.length - 1;
        double value = size / Math.pow(1024, digitGroups);
        DecimalFormat df = new DecimalFormat("#0.##");
        return df.format(value) + " " + UNITS[digitGroups];
    }

    public static String formatSize(long size, String suffix) {
        return formatSize(size) + suffix;
    }
}
