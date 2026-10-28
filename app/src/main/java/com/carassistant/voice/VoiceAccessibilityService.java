package com.carassistant.voice;

import android.accessibilityservice.AccessibilityService;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;

/* JADX INFO: loaded from: classes.dex */
public class VoiceAccessibilityService extends AccessibilityService {
    private static final String TAG = "CarVoiceA11y";
    private VehicleVoiceEngine mEngine;

    @Override // android.app.Service
    public void onCreate() {
        super.onCreate();
        VehicleVoiceEngine vehicleVoiceEngine = new VehicleVoiceEngine(this);
        this.mEngine = vehicleVoiceEngine;
        vehicleVoiceEngine.start();
        Log.i(TAG, "engine started");
    }

    @Override // android.accessibilityservice.AccessibilityService
    public void onAccessibilityEvent(AccessibilityEvent accessibilityEvent) {
    }

    @Override // android.accessibilityservice.AccessibilityService
    public void onInterrupt() {
    }

    @Override // android.app.Service
    public void onDestroy() {
        VehicleVoiceEngine vehicleVoiceEngine = this.mEngine;
        if (vehicleVoiceEngine != null) {
            vehicleVoiceEngine.destroy();
            this.mEngine = null;
        }
        Log.i(TAG, "engine destroyed");
        super.onDestroy();
    }
}
