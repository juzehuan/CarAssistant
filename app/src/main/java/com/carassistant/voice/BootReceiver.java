package com.carassistant.voice;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.widget.Toast;

/* JADX INFO: loaded from: classes.dex */
public class BootReceiver extends BroadcastReceiver {
    @Override // android.content.BroadcastReceiver
    public void onReceive(Context context, Intent intent) {
        if (intent == null || intent.getAction() == null || !"android.intent.action.BOOT_COMPLETED".equals(intent.getAction())) {
            return;
        }
        SharedPreferences defaultSharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
        if (!defaultSharedPreferences.getBoolean(MainActivity.PREF_BOOT_HINT, true) || !defaultSharedPreferences.getBoolean(VehicleVoiceEngine.PREF_ENABLED, false)) {
            return;
        }
        try {
            Toast.makeText(context.getApplicationContext(), "红旗智声：请确认无障碍已开启", 1).show();
        } catch (Throwable th) {
        }
    }
}
