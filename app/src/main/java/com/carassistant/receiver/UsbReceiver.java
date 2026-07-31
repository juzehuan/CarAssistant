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

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.storage.StorageManager;
import android.os.storage.StorageVolume;

import androidx.core.app.NotificationCompat;

import com.carassistant.MainActivity;
import com.carassistant.R;
import com.carassistant.util.StorageUtil;

import java.lang.reflect.Method;
import java.util.List;

/**
 * U 盘插拔监听：
 * - 静态注册在 Manifest 中监听 MEDIA_MOUNTED / MEDIA_EJECTED
 * - 通过反射获取 StorageVolume 列表，区分 U 盘并发布通知
 */
public class UsbReceiver extends BroadcastReceiver {

    private static final String CHANNEL_ID = "car_assistant_usb";
    private static final int NOTIFICATION_ID = 0xA2;

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null) return;
        String action = intent.getAction();
        if (action == null) return;

        boolean mounted = Intent.ACTION_MEDIA_MOUNTED.equals(action);
        boolean removed = Intent.ACTION_MEDIA_UNMOUNTED.equals(action)
                || Intent.ACTION_MEDIA_REMOVED.equals(action)
                || Intent.ACTION_MEDIA_EJECT.equals(action);

        if (!mounted && !removed) return;

        String path = null;
        if (intent.getData() != null) path = intent.getData().getPath();

        if (mounted) {
            // 获取 U 盘标签/容量
            String label = path != null ? path : "U盘";
            long total = 0, avail = 0;
            List<StorageUtil.StorageInfo> all = StorageUtil.getAllStorages(context);
            for (StorageUtil.StorageInfo s : all) {
                if (s.usb && s.total > 0) {
                    label = s.label != null ? s.label : "U盘";
                    total = s.total;
                    avail = s.available;
                    path = s.path;
                    break;
                }
            }
            String sizeText = total > 0
                    ? com.carassistant.util.FormatUtil.formatSize(total) + " 可用 "
                        + com.carassistant.util.FormatUtil.formatSize(avail)
                    : "";
            notifyUsb(context, context.getString(R.string.usb_plugged, label + (sizeText.isEmpty() ? "" : ("  " + sizeText))));
        } else if (removed) {
            notifyUsb(context, context.getString(R.string.usb_removed));
        }
    }

    private void notifyUsb(Context ctx, String message) {
        ensureChannel(ctx);

        Intent main = new Intent(ctx, MainActivity.class);
        main.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        main.putExtra("tab", "file");
        PendingIntent pi = PendingIntent.getActivity(ctx, 1, main,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

        NotificationCompat.Builder b = new NotificationCompat.Builder(ctx, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_feature_usb)
                .setContentTitle(ctx.getString(R.string.usb_title))
                .setContentText(message)
                .setContentIntent(pi)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH);

        try {
            NotificationManager nm = (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm != null) nm.notify(NOTIFICATION_ID, b.build());
        } catch (Exception ignored) {}
    }

    private void ensureChannel(Context ctx) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager nm = (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm == null) return;
            if (nm.getNotificationChannel(CHANNEL_ID) == null) {
                NotificationChannel ch = new NotificationChannel(CHANNEL_ID,
                        ctx.getString(R.string.usb_title),
                        NotificationManager.IMPORTANCE_HIGH);
                nm.createNotificationChannel(ch);
            }
        }
    }
}
