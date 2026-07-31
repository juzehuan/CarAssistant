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

package com.carassistant.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.carassistant.util.AppAutoStartManager;

/**
 * 开机自启应用闹钟执行器
 *
 * 极简接收器：仅作为 AlarmManager 闹钟的触发入口，
 * 将实际启动逻辑全权委托给 {@link AppAutoStartManager#handleAlarm}。
 *
 * 每个 AppAutoStartReceiver 实例承担顺序启动链中的一环，
 * 通过 intent extra（index / return_home）标识当前环节。
 */
public class AppAutoStartReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        AppAutoStartManager.handleAlarm(context.getApplicationContext(), intent);
    }
}
