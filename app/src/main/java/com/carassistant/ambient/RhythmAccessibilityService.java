package com.carassistant.ambient;

import android.accessibilityservice.AccessibilityService;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;

/* JADX INFO: loaded from: classes.dex */
public class RhythmAccessibilityService extends AccessibilityService {
    private static final String TAG = "QiguangA11y";
    private AmbientRhythmHelper mHelper;

    @Override // android.app.Service
    public void onCreate() {
        super.onCreate();
        AmbientRhythmHelper ambientRhythmHelper = new AmbientRhythmHelper(this);
        this.mHelper = ambientRhythmHelper;
        ambientRhythmHelper.start();
        Log.i(TAG, "rhythm helper started");
    }

    @Override // android.accessibilityservice.AccessibilityService
    public void onAccessibilityEvent(AccessibilityEvent accessibilityEvent) {
    }

    @Override // android.accessibilityservice.AccessibilityService
    public void onInterrupt() {
    }

    @Override // android.app.Service
    public void onDestroy() {
        AmbientRhythmHelper ambientRhythmHelper = this.mHelper;
        if (ambientRhythmHelper != null) {
            ambientRhythmHelper.destroy();
            this.mHelper = null;
        }
        Log.i(TAG, "rhythm helper destroyed");
        super.onDestroy();
    }
}
