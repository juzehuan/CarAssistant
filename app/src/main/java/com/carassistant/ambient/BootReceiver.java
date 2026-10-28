package com.carassistant.ambient;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.widget.Toast;

/* JADX INFO: loaded from: classes.dex */
public class BootReceiver extends BroadcastReceiver {
    public static final String PREF_BOOT_HINT = "key_qiguang_boot_hint";

    @Override // android.content.BroadcastReceiver
    public void onReceive(Context context, Intent intent) {
        if (intent == null || intent.getAction() == null || !"android.intent.action.BOOT_COMPLETED".equals(intent.getAction())) {
            return;
        }
        SharedPreferences defaultSharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
        if (!defaultSharedPreferences.getBoolean(PREF_BOOT_HINT, true) || !defaultSharedPreferences.getBoolean(AmbientRhythmHelper.PREF_ENABLED, false)) {
            return;
        }
        try {
            Toast.makeText(context, "旗光律动：请确认无障碍已开启", 1).show();
        } catch (Throwable th) {
        }
    }
}
