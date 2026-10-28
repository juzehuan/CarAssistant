package com.carassistant.ambient;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.AudioPlaybackConfiguration;
import android.media.audiofx.Visualizer;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Parcel;
import android.os.RemoteException;
import android.os.SystemClock;
import android.preference.PreferenceManager;
import android.util.Log;
import java.io.File;
import java.io.FileOutputStream;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/* JADX INFO: loaded from: classes.dex */
public final class AmbientRhythmHelper {
    private static Throwable th;
    private static final String ADAPTER_CLS = "ltd.qisi.adapterservice.QISIAdapterService";
    private static final String ADAPTER_PKG = "ltd.qisi.adapterservice";
    private static final int BINDER_AMBIENT = 3;
    private static final int BINDER_STATE = 15;
    private static final int COLOR_MAX = 253;
    private static final int COLOR_MIN = 1;
    public static final int CUSTOM_AUTO_SEC_MAX = 30;
    public static final int CUSTOM_AUTO_SEC_MIN = 2;
    private static final long CUSTOM_MUSIC_BEAT_COOLDOWN_MS = 150;
    private static final float CUSTOM_MUSIC_FLUX_MULT = 1.6f;
    public static final int CUSTOM_PALETTE_MAX = 30;
    public static final int CUSTOM_PALETTE_MIN = 2;
    public static final String DEFAULT_CUSTOM_PALETTE = "32,64,96,128,160,192";
    private static final String DESC_AMBIENT = "ltd.qisi.adapterproxy.ICarAmbientLampManager";
    private static final String DESC_REMOTE = "ltd.qisi.adapterproxy.IVehicleRemoteClienter";
    private static final String DESC_STATE = "ltd.qisi.adapterproxy.ICarVehicleStateManager";
    private static final int DRIVE_REV_A = 28;
    private static final int DRIVE_REV_B = 60;
    private static final long DRIVE_REV_FLASH_MS = 280;
    private static final float DRIVE_SPEED_MAX = 120.0f;
    private static final long EV_BREATH_PERIOD_MS = 3200;
    private static final long EV_COLOR_INTERVAL_MS = 4000;
    private static final int GEAR_D = 3;
    private static final int GEAR_N = 0;
    private static final int GEAR_P = 2;
    private static final int GEAR_R = 1;
    public static final int MODE_CUSTOM_AUTO = 7;
    public static final int MODE_CUSTOM_MUSIC = 6;
    public static final int MODE_DRIVE = 5;
    public static final int MODE_EV = 4;
    public static final int MODE_GRADIENT = 2;
    public static final int MODE_PULSE = 3;
    public static final int MODE_RAGE = 0;
    public static final int MODE_SOFT = 1;
    private static final long POLL_MS = 500;
    public static final String PREF_CUSTOM_AUTO_SEC = "key_custom_auto_sec";
    public static final String PREF_CUSTOM_PALETTE = "key_custom_palette";
    public static final String PREF_ENABLED = "key_ambient_rhythm_enabled";
    public static final String PREF_MODE = "key_ambient_rhythm_mode";
    private static final long PULSE_BEAT_COOLDOWN_MS = 160;
    private static final float PULSE_FLUX_MULT = 1.55f;
    private static final int RAGE_BASS_BINS = 10;
    private static final long RAGE_BEAT_COOLDOWN_MS = 140;
    private static final int RAGE_COLOR_STEP_STRONG = 18;
    private static final int RAGE_COLOR_STEP_WEAK = 3;
    private static final float RAGE_FLUX_MULT = 1.75f;
    private static final float RAGE_MIN_FLUX = 0.035f;
    public static final String TAG = "QiguangRhythm";
    private static final long TICK_MS = 100;
    private static final int TX_GET_BRIGHTNESS = 3;
    private static final int TX_GET_COLOR = 8;
    private static final int TX_GET_GEAR = 6;
    private static final int TX_GET_SPEED = 7;
    private static final int TX_QUERY_BINDER = 1;
    private static final int TX_SET_BRIGHTNESS = 4;
    private static final int TX_SET_COLOR = 5;
    private static final int TX_SET_LAMP = 2;
    private static final int TX_SET_MUSIC_PULSE = 7;
    private static volatile AmbientRhythmHelper sActive;
    private boolean mAlive;
    private IBinder mAmbient;
    private float mBassAvg;
    private volatile float mBassFlux;
    private float mBassPrev;
    private volatile float mBassRaw;
    private boolean mBinding;
    private boolean mBound;
    private final Context mCtx;
    private int[] mCustomPaletteCache;
    private int mCustomPaletteIdx;
    private boolean mDriving;
    private boolean mFake;
    private long mFakeStart;
    private long mLastBeatAt;
    private long mLastColorAt;
    private long mLastGradAt;
    private long mLastRevFlashAt;
    private float mPrev;
    private boolean mPulseHi;
    private float mRageEnv;
    private volatile float mRawEnergy;
    private IBinder mRemote;
    private boolean mRevFlashHi;
    private boolean mSaved;
    private float mSmooth;
    private float mSmoothSpeed;
    private IBinder mState;
    private Visualizer mViz;
    private static final String[] MODE_NAMES = {"狂暴模式", "柔和呼吸", "慢速渐变", "单色脉动", "电车呼吸", "驾驶联动", "自定义跟乐", "自定义换色"};
    private static final int[] DRIVE_WARM_TO_RED = {94, 62, 30, 63, 31, 32, 1};
    private static final String[] MUSIC_PKGS = {"cn.kuwo.autolite", "com.luna.music.car"};
    private final Handler mMain = new Handler(Looper.getMainLooper());
    private int mVizSessionId = -1;
    private int mColor = 32;
    private int mFixedColor = 32;
    private int mLastColor = -1;
    private int mLastBri = -1;
    private int mSavedColor = -1;
    private int mSavedBri = -1;
    private int mLastMode = -1;
    private float mLastRawSpeed = -1.0f;
    private int mLastLoggedGear = Integer.MIN_VALUE;
    private final Object mLock = new Object();
    private final ServiceConnection mConn = new ServiceConnection() { // from class: com.carassistant.ambient.AmbientRhythmHelper.1
        @Override // android.content.ServiceConnection
        public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
            synchronized (AmbientRhythmHelper.this.mLock) {
                AmbientRhythmHelper.this.mRemote = iBinder;
                AmbientRhythmHelper.this.mAmbient = null;
                AmbientRhythmHelper.this.mState = null;
                AmbientRhythmHelper.this.mBound = true;
                AmbientRhythmHelper.this.mBinding = false;
            }
            AmbientRhythmHelper.this.resolveAmbient();
            AmbientRhythmHelper.this.resolveState();
            AmbientRhythmHelper.this.note("qisi connected");
        }

        @Override // android.content.ServiceConnection
        public void onServiceDisconnected(ComponentName componentName) {
            synchronized (AmbientRhythmHelper.this.mLock) {
                AmbientRhythmHelper.this.mRemote = null;
                AmbientRhythmHelper.this.mAmbient = null;
                AmbientRhythmHelper.this.mState = null;
                AmbientRhythmHelper.this.mBound = false;
                AmbientRhythmHelper.this.mBinding = false;
            }
        }
    };
    private final Runnable mPoll = new Runnable() { // from class: com.carassistant.ambient.AmbientRhythmHelper.2
        @Override // java.lang.Runnable
        public void run() {
            if (AmbientRhythmHelper.this.mAlive) {
                try {
                    AmbientRhythmHelper.this.pollOnce();
                } catch (Throwable th) {
                    Log.w(AmbientRhythmHelper.TAG, "poll", th);
                }
                if (AmbientRhythmHelper.this.mAlive) {
                    AmbientRhythmHelper.this.mMain.postDelayed(this, AmbientRhythmHelper.POLL_MS);
                }
            }
        }
    };
    private final Runnable mTick = new Runnable() { // from class: com.carassistant.ambient.AmbientRhythmHelper.3
        @Override // java.lang.Runnable
        public void run() {
            if (AmbientRhythmHelper.this.mAlive && AmbientRhythmHelper.this.mDriving) {
                try {
                    AmbientRhythmHelper.this.tickOnce();
                } catch (Throwable th) {
                    Log.w(AmbientRhythmHelper.TAG, "tick", th);
                }
                if (AmbientRhythmHelper.this.mAlive && AmbientRhythmHelper.this.mDriving) {
                    AmbientRhythmHelper.this.mMain.postDelayed(this, AmbientRhythmHelper.TICK_MS);
                }
            }
        }
    };

    public AmbientRhythmHelper(Context context) {
        this.mCtx = context.getApplicationContext();
    }

    public static String modeName(int i) {
        if (i >= 0) {
            String[] strArr = MODE_NAMES;
            if (i < strArr.length) {
                return strArr[i];
            }
        }
        return MODE_NAMES[0];
    }

    public void start() {
        this.mAlive = true;
        sActive = this;
        ensureBind();
        this.mMain.removeCallbacks(this.mPoll);
        this.mMain.post(this.mPoll);
        note("helper start");
    }

    public void destroy() {
        if (sActive == this) {
            sActive = null;
        }
        this.mAlive = false;
        this.mMain.removeCallbacks(this.mPoll);
        this.mMain.removeCallbacks(this.mTick);
        stopDriving(true);
        try {
            if (this.mBound || this.mBinding) {
                this.mCtx.unbindService(this.mConn);
            }
        } catch (Throwable th) {
        }
        this.mBound = false;
        this.mBinding = false;
        note("helper destroy");
    }

    private SharedPreferences prefs() {
        return PreferenceManager.getDefaultSharedPreferences(this.mCtx);
    }

    private boolean enabled() {
        return prefs().getBoolean(PREF_ENABLED, false);
    }

    private int mode() {
        int i = prefs().getInt(PREF_MODE, 0);
        if (i < 0 || i > 7) {
            return 0;
        }
        return i;
    }

    private boolean shouldDrive() {
        if (!enabled()) {
            return false;
        }
        int iMode = mode();
        if (iMode == 4 || iMode == 5 || iMode == 7) {
            return true;
        }
        return isMusicPlaying();
    }

    private static boolean isNonMusicMode(int i) {
        return i == 4 || i == 5 || i == 7;
    }

    public static int[] getCustomPalette(Context context) {
        return parsePalette(PreferenceManager.getDefaultSharedPreferences(context.getApplicationContext()).getString(PREF_CUSTOM_PALETTE, DEFAULT_CUSTOM_PALETTE));
    }

    public static void setCustomPalette(Context context, int[] iArr) {
        int[] iArrSanitizePalette = sanitizePalette(iArr);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < iArrSanitizePalette.length; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(iArrSanitizePalette[i]);
        }
        PreferenceManager.getDefaultSharedPreferences(context.getApplicationContext()).edit().putString(PREF_CUSTOM_PALETTE, sb.toString()).apply();
        AmbientRhythmHelper ambientRhythmHelper = sActive;
        if (ambientRhythmHelper != null) {
            ambientRhythmHelper.mCustomPaletteCache = iArrSanitizePalette;
            if (ambientRhythmHelper.mCustomPaletteIdx >= iArrSanitizePalette.length) {
                ambientRhythmHelper.mCustomPaletteIdx = 0;
            }
        }
    }

    public static void resetCustomPalette(Context context) {
        setCustomPalette(context, parsePalette(DEFAULT_CUSTOM_PALETTE));
    }

    public static int getCustomAutoSec(Context context) {
        int i = PreferenceManager.getDefaultSharedPreferences(context.getApplicationContext()).getInt(PREF_CUSTOM_AUTO_SEC, TX_GET_COLOR);
        if (i < 2) {
            return 2;
        }
        if (i > 30) {
            return 30;
        }
        return i;
    }

    public static void setCustomAutoSec(Context context, int i) {
        if (i < 2) {
            i = 2;
        }
        if (i > 30) {
            i = 30;
        }
        PreferenceManager.getDefaultSharedPreferences(context.getApplicationContext()).edit().putInt(PREF_CUSTOM_AUTO_SEC, i).apply();
    }

    public static int approxRgbForIndex(int i) {
        if (i < 1) {
            i = 1;
        }
        if (i > COLOR_MAX) {
            i = COLOR_MAX;
        }
        return Color.HSVToColor(new float[]{((i - 1) / 252.0f) * 360.0f, 0.72f, 0.95f});
    }

    public static boolean previewLampColor(Context context, int i) {
        if (i < 1) {
            i = 1;
        }
        if (i > COLOR_MAX) {
            i = COLOR_MAX;
        }
        AmbientRhythmHelper ambientRhythmHelper = sActive;
        if (ambientRhythmHelper == null) {
            return false;
        }
        ambientRhythmHelper.ensureBind();
        ambientRhythmHelper.setLamp(1);
        int color = ambientRhythmHelper.setColor(i);
        ambientRhythmHelper.mLastColor = i;
        ambientRhythmHelper.mColor = i;
        return color >= 0;
    }

    public static int[] parsePalette(String str) {
        if (str == null || str.length() == 0) {
            return sanitizePalette(null);
        }
        String[] strArrSplit = str.split(",");
        int[] iArr = new int[strArrSplit.length];
        int i = 0;
        for (String str2 : strArrSplit) {
            try {
                int i2 = Integer.parseInt(str2.trim());
                if (i2 >= 1 && i2 <= COLOR_MAX) {
                    int i3 = i + 1;
                    try {
                        iArr[i] = i2;
                        i = i3;
                    } catch (Throwable th) {
                        i = i3;
                    }
                }
            } catch (Throwable th2) {
            }
        }
        int[] iArr2 = new int[i];
        System.arraycopy(iArr, 0, iArr2, 0, i);
        return sanitizePalette(iArr2);
    }

    public static int[] sanitizePalette(int[] iArr) {
        boolean z;
        int[] paletteRaw = parsePaletteRaw(DEFAULT_CUSTOM_PALETTE);
        if (iArr == null || iArr.length == 0) {
            return paletteRaw;
        }
        ArrayList arrayList = new ArrayList();
        int i = 0;
        while (true) {
            boolean z2 = true;
            if (i >= iArr.length || arrayList.size() >= 30) {
                break;
            }
            int i2 = iArr[i];
            if (i2 >= 1 && i2 <= COLOR_MAX) {
                int i3 = 0;
                while (true) {
                    if (i3 >= arrayList.size()) {
                        z2 = false;
                        break;
                    }
                    if (((Integer) arrayList.get(i3)).intValue() == i2) {
                        break;
                    }
                    i3++;
                }
                if (!z2) {
                    arrayList.add(Integer.valueOf(i2));
                }
            }
            i++;
        }
        while (arrayList.size() < 2) {
            for (int i4 = 0; i4 < paletteRaw.length && arrayList.size() < 2; i4++) {
                int i5 = paletteRaw[i4];
                int i6 = 0;
                while (true) {
                    if (i6 >= arrayList.size()) {
                        z = false;
                        break;
                    }
                    if (((Integer) arrayList.get(i6)).intValue() != i5) {
                        i6++;
                    } else {
                        z = true;
                        break;
                    }
                }
                if (!z) {
                    arrayList.add(Integer.valueOf(i5));
                }
            }
            if (arrayList.size() < 2) {
                arrayList.add(32);
            }
        }
        int[] iArr2 = new int[arrayList.size()];
        for (int i7 = 0; i7 < arrayList.size(); i7++) {
            iArr2[i7] = ((Integer) arrayList.get(i7)).intValue();
        }
        return iArr2;
    }

    private static int[] parsePaletteRaw(String str) {
        String[] strArrSplit = str.split(",");
        int[] iArr = new int[strArrSplit.length];
        for (int i = 0; i < strArrSplit.length; i++) {
            iArr[i] = Integer.parseInt(strArrSplit[i].trim());
        }
        return iArr;
    }

    private int[] customPalette() {
        int[] customPalette = getCustomPalette(this.mCtx);
        this.mCustomPaletteCache = customPalette;
        int i = this.mCustomPaletteIdx;
        if (i < 0 || i >= customPalette.length) {
            this.mCustomPaletteIdx = 0;
        }
        return customPalette;
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void pollOnce() {
        boolean zShouldDrive = shouldDrive();
        if (zShouldDrive && !this.mDriving) {
            startDriving();
            return;
        }
        if (!zShouldDrive && this.mDriving) {
            stopDriving(true);
        } else if (this.mDriving && zShouldDrive && !isNonMusicMode(mode())) {
            maybeRefreshViz();
        }
    }

    private boolean isMusicPlaying() {
        boolean z;
        List<MediaController> activeSessions;
        PlaybackState playbackState;
        String packageName;
        try {
            MediaSessionManager mediaSessionManager = (MediaSessionManager) this.mCtx.getSystemService("media_session");
            if (mediaSessionManager != null && (activeSessions = mediaSessionManager.getActiveSessions(new ComponentName(this.mCtx.getPackageName(), "com.carassistant.ambient.MediaNotificationListenerService"))) != null) {
                z = false;
                for (MediaController mediaController : activeSessions) {
                    try {
                        if (mediaController != null && (playbackState = mediaController.getPlaybackState()) != null && playbackState.getState() == 3) {
                            try {
                                packageName = mediaController.getPackageName();
                            } catch (Throwable th) {
                                packageName = null;
                            }
                            if (isPreferredMusicPkg(packageName)) {
                                return true;
                            }
                            z = true;
                        }
                    } catch (Throwable th2) {
                        th = th2;
                        Log.w(TAG, "session check", th);
                    }
                }
            } else {
                z = false;
            }
        } catch (Throwable th3) {
            th = th3;
            z = false;
        }
        if (z) {
            return true;
        }
        try {
            AudioManager audioManager = (AudioManager) this.mCtx.getSystemService("audio");
            return audioManager != null && audioManager.isMusicActive();
        } catch (Throwable th4) {
        }
        return false;
    }

    private static boolean isPreferredMusicPkg(String str) {
        if (str == null) {
            return false;
        }
        int i = 0;
        while (true) {
            String[] strArr = MUSIC_PKGS;
            if (i >= strArr.length) {
                return false;
            }
            if (!str.equals(strArr[i])) {
                i++;
            } else {
                return true;
            }
        }
    }

    private void startDriving() {
        ensureBind();
        saveLampOnce();
        setLamp(1);
        setMusicPulse(false);
        this.mSmooth = 0.0f;
        this.mPrev = 0.0f;
        this.mRawEnergy = 0.0f;
        resetBassState();
        this.mLastColorAt = 0L;
        this.mLastGradAt = SystemClock.uptimeMillis();
        this.mFakeStart = SystemClock.uptimeMillis();
        int i = this.mSavedColor;
        if (i >= 1 && i <= COLOR_MAX) {
            this.mFixedColor = i;
            this.mColor = i;
        }
        int iMode = mode();
        this.mCustomPaletteCache = null;
        this.mCustomPaletteIdx = 0;
        if (!isNonMusicMode(iMode)) {
            this.mFake = !attachVizBest();
        } else {
            releaseViz();
            this.mFake = true;
            if (iMode == 5) {
                resolveState();
                this.mSmoothSpeed = 0.0f;
                this.mLastRawSpeed = -1.0f;
                this.mRevFlashHi = false;
                this.mLastRevFlashAt = 0L;
            }
        }
        this.mDriving = true;
        this.mMain.removeCallbacks(this.mTick);
        this.mMain.post(this.mTick);
        note("drive on mode=" + mode() + " fake=" + this.mFake);
        Log.i(TAG, "drive on mode=" + modeName(mode()) + " fake=" + this.mFake);
    }

    private void stopDriving(boolean z) {
        this.mDriving = false;
        this.mMain.removeCallbacks(this.mTick);
        releaseViz();
        if (z) {
            restoreLamp();
        }
        note("drive off restore=" + z);
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void tickOnce() {
        float fSin;
        int iMode = mode();
        if (iMode != this.mLastMode) {
            this.mLastMode = iMode;
            this.mLastColor = -1;
            this.mLastBri = -1;
            this.mLastColorAt = 0L;
            this.mLastGradAt = 0L;
            this.mPulseHi = false;
            this.mCustomPaletteCache = null;
            this.mCustomPaletteIdx = 0;
            ensureFixedColor();
            if (isNonMusicMode(iMode)) {
                releaseViz();
                this.mFake = true;
                if (iMode == 5) {
                    resolveState();
                }
            } else if (this.mViz == null) {
                resetBassState();
                this.mFake = !attachVizBest();
            }
            if (iMode == 1 || iMode == 3) {
                sendColor(this.mFixedColor);
            } else if (iMode == 5) {
                sendColor(DRIVE_WARM_TO_RED[0]);
            } else if (iMode == 6 || iMode == 7) {
                int[] iArrCustomPalette = customPalette();
                sendColor(iArrCustomPalette[0]);
                this.mColor = iArrCustomPalette[0];
            } else {
                int i = this.mColor;
                if (i < 1) {
                    i = this.mFixedColor;
                }
                sendColor(i);
            }
            note("mode switch -> " + modeName(iMode) + " fake=" + this.mFake + " vizSid=" + this.mVizSessionId);
        }
        long jUptimeMillis = SystemClock.uptimeMillis();
        if (iMode == 5) {
            tickDriveLink(jUptimeMillis);
            return;
        }
        if (this.mFake || this.mViz == null) {
            fSin = (float) ((((Math.sin((((jUptimeMillis - this.mFakeStart) % POLL_MS) / 500.0d) * 3.141592653589793d * 2.0d) * 0.5d) + 0.5d) * 0.45d) + 0.35d);
            this.mRawEnergy = fSin;
            this.mBassFlux = Math.max(0.0f, fSin - this.mBassPrev);
            this.mBassPrev = fSin;
            this.mBassRaw = fSin;
        } else {
            fSin = this.mRawEnergy;
        }
        float fClamp01 = this.mFake ? fSin : clamp01((this.mBassRaw * 0.72f) + (0.28f * fSin));
        if (iMode == 0 || iMode == 3 || iMode == 6) {
            float f = this.mRageEnv;
            if (fClamp01 > f) {
                this.mRageEnv = (f * 0.22f) + (fClamp01 * 0.78f);
            } else {
                this.mRageEnv = (f * 0.84f) + (fClamp01 * 0.16f);
            }
            this.mSmooth = this.mRageEnv;
        } else if (iMode == 1 || iMode == 2) {
            float f2 = this.mRageEnv;
            if (fClamp01 > f2) {
                this.mRageEnv = (f2 * 0.45f) + (fClamp01 * 0.55f);
            } else {
                this.mRageEnv = (f2 * 0.88f) + (0.12f * fClamp01);
            }
            float f3 = (this.mRageEnv * 0.55f) + (fSin * 0.45f);
            this.mSmooth = f3;
            this.mSmooth = (f3 * 0.55f) + (fClamp01 * 0.45f);
        } else {
            this.mSmooth = (this.mSmooth * 0.65f) + (fSin * 0.35f);
        }
        switch (iMode) {
            case MODE_SOFT /* 1 */:
                tickSoft(jUptimeMillis);
                break;
            case 2:
                tickGradient(jUptimeMillis);
                break;
            case MODE_PULSE /* 3 */:
                tickPulse(jUptimeMillis);
                break;
            case 4:
                tickEvBreath(jUptimeMillis);
                break;
            case 5:
            default:
                tickRage(jUptimeMillis);
                break;
            case 6:
                tickCustomMusic(jUptimeMillis);
                break;
            case MODE_CUSTOM_AUTO /* 7 */:
                tickCustomAuto(jUptimeMillis);
                break;
        }
        this.mPrev = this.mSmooth;
    }

    private void tickCustomMusic(long j) {
        int[] iArrCustomPalette = customPalette();
        int i = this.mCustomPaletteIdx;
        if (i < 0 || i >= iArrCustomPalette.length) {
            this.mCustomPaletteIdx = 0;
        }
        float fClamp01 = clamp01(this.mBassRaw);
        float fMax = Math.max(0.0f, this.mBassFlux);
        float fMax2 = (this.mBassAvg * 0.9f) + (Math.max(fMax, 0.001f) * 0.1f);
        this.mBassAvg = fMax2;
        boolean z = (((j - this.mLastBeatAt) > CUSTOM_MUSIC_BEAT_COOLDOWN_MS ? 1 : ((j - this.mLastBeatAt) == CUSTOM_MUSIC_BEAT_COOLDOWN_MS ? 0 : -1)) >= 0) && fMax >= Math.max(0.03f, fMax2 * CUSTOM_MUSIC_FLUX_MULT) && fClamp01 >= 0.07f;
        int iRound = Math.round(clamp01(this.mRageEnv) * 62.0f) + DRIVE_REV_A;
        if (z) {
            iRound = Math.min(100, iRound + RAGE_COLOR_STEP_STRONG);
        }
        sendBri(clampBri(iRound));
        if (z) {
            this.mLastBeatAt = j;
            int length = (this.mCustomPaletteIdx + 1) % iArrCustomPalette.length;
            this.mCustomPaletteIdx = length;
            int i2 = iArrCustomPalette[length];
            this.mColor = i2;
            sendColor(i2);
            this.mLastColorAt = j;
            return;
        }
        if (fClamp01 < 0.1f && this.mRageEnv < 0.16f && j - this.mLastColorAt >= 400) {
            if (this.mCustomPaletteIdx != 0 || this.mLastColor != iArrCustomPalette[0]) {
                this.mCustomPaletteIdx = 0;
                int i3 = iArrCustomPalette[0];
                this.mColor = i3;
                sendColor(i3);
                this.mLastColorAt = j;
            }
        }
    }

    private void tickCustomAuto(long j) {
        int[] iArrCustomPalette = customPalette();
        int i = this.mCustomPaletteIdx;
        if (i < 0 || i >= iArrCustomPalette.length) {
            this.mCustomPaletteIdx = 0;
        }
        long customAutoSec = ((long) getCustomAutoSec(this.mCtx)) * 1000;
        sendBri(clampBri(Math.round(((float) ((Math.sin(((j % EV_BREATH_PERIOD_MS) / 3200.0d) * 3.141592653589793d * 2.0d) * 0.5d) + 0.5d)) * 36.0f) + 30));
        long j2 = this.mLastColorAt;
        if (j2 == 0 || j - j2 >= customAutoSec) {
            if (j2 != 0) {
                this.mCustomPaletteIdx = (this.mCustomPaletteIdx + 1) % iArrCustomPalette.length;
            }
            int i2 = iArrCustomPalette[this.mCustomPaletteIdx];
            this.mColor = i2;
            sendColor(i2);
            this.mLastColorAt = j;
        }
    }

    private void tickDriveLink(long j) {
        int gear = readGear();
        float speedKmh = readSpeedKmh();
        if (speedKmh < 0.0f) {
            speedKmh = 0.0f;
        }
        if (this.mLastRawSpeed < 0.0f) {
            this.mLastRawSpeed = speedKmh;
        }
        float f = speedKmh - this.mLastRawSpeed;
        this.mLastRawSpeed = speedKmh;
        this.mSmoothSpeed = (this.mSmoothSpeed * 0.72f) + (0.28f * speedKmh);
        if (gear != this.mLastLoggedGear) {
            this.mLastLoggedGear = gear;
            note("gear=" + gear + " spd=" + ((int) speedKmh));
        }
        if (gear == 1) {
            if (j - this.mLastRevFlashAt >= DRIVE_REV_FLASH_MS) {
                this.mRevFlashHi = !this.mRevFlashHi;
                this.mLastRevFlashAt = j;
            }
            int i = this.mRevFlashHi ? DRIVE_REV_A : DRIVE_REV_B;
            this.mColor = i;
            sendColor(i);
            sendBri(this.mRevFlashHi ? 95 : 45);
            return;
        }
        if (gear != 2 && gear != 0) {
            float f2 = this.mSmoothSpeed;
            if (f2 >= 2.5f) {
                float fClamp01 = clamp01(f2 / DRIVE_SPEED_MAX);
                if (f > 4.0f) {
                    fClamp01 = clamp01(fClamp01 + 0.18f);
                } else if (f > 2.0f) {
                    fClamp01 = clamp01(fClamp01 + 0.08f);
                }
                int[] iArr = DRIVE_WARM_TO_RED;
                int length = iArr.length - 1;
                int i2 = (int) (length * fClamp01);
                if (i2 >= length) {
                    i2 = length;
                }
                int i3 = iArr[i2];
                if (f > 3.0f && (j / 120) % 2 == 0 && i2 < length) {
                    i3 = iArr[i2 + 1];
                }
                this.mColor = i3;
                if (i3 != this.mLastColor || j - this.mLastColorAt >= 180) {
                    sendColor(i3);
                    this.mLastColorAt = j;
                }
                int iRound = Math.round(fClamp01 * 55.0f) + 40;
                if (f > 3.0f) {
                    iRound = Math.min(100, iRound + BINDER_STATE);
                }
                sendBri(clampBri(iRound));
                return;
            }
        }
        float fSin = (float) ((Math.sin(((j % 3600) / 3600.0d) * 3.141592653589793d * 2.0d) * 0.5d) + 0.5d);
        int[] iArr2 = DRIVE_WARM_TO_RED;
        int i4 = iArr2[0];
        int i5 = iArr2[1];
        if (fSin > 0.55f) {
            i4 = i5;
        }
        this.mColor = i4;
        if (j - this.mLastColorAt >= 450) {
            sendColor(i4);
            this.mLastColorAt = j;
        }
        sendBri(Math.round(fSin * 28.0f) + 32);
    }

    private void tickEvBreath(long j) {
        sendBri(clampBri(Math.round(((float) ((Math.sin(((j % EV_BREATH_PERIOD_MS) / 3200.0d) * 3.141592653589793d * 2.0d) * 0.5d) + 0.5d)) * 42.0f) + DRIVE_REV_A));
        if (j - this.mLastColorAt >= EV_COLOR_INTERVAL_MS) {
            stepColor(1);
            sendColor(this.mColor);
            this.mLastColorAt = j;
        }
    }

    private void tickRage(long j) {
        int i;
        float fClamp01 = clamp01(this.mBassRaw);
        float fMax = Math.max(0.0f, this.mBassFlux);
        float fMax2 = (this.mBassAvg * 0.9f) + (Math.max(fMax, 0.001f) * 0.1f);
        this.mBassAvg = fMax2;
        float fMax3 = Math.max(RAGE_MIN_FLUX, fMax2 * RAGE_FLUX_MULT);
        boolean z = false;
        if ((j - this.mLastBeatAt >= RAGE_BEAT_COOLDOWN_MS) && fMax >= fMax3 && fClamp01 >= 0.08f) {
            z = true;
        }
        if (z) {
            this.mLastBeatAt = j;
            sendBri(clampBri(Math.round(fClamp01 * 10.0f) + 90));
            stepColor(RAGE_COLOR_STEP_STRONG);
            sendColor(this.mColor);
            this.mLastColorAt = j;
            return;
        }
        if (fClamp01 < 0.1f) {
            float f = this.mRageEnv;
            if (f < 0.14f) {
                sendBri(clampBri(Math.round(clamp01(f) * 18.0f) + 20));
                if (j - this.mLastColorAt >= 700 && (i = this.mFixedColor) >= 1) {
                    int i2 = this.mColor - i;
                    if (Math.abs(i2) > 2) {
                        int iWrapColor = wrapColor(this.mColor - (i2 <= 0 ? -2 : 2));
                        this.mColor = iWrapColor;
                        sendColor(iWrapColor);
                        this.mLastColorAt = j;
                        return;
                    }
                    return;
                }
                return;
            }
        }
        sendBri(clampBri(Math.round(clamp01(this.mRageEnv) * 52.0f) + 32));
        if (fMax > fMax3 * 0.45f && j - this.mLastColorAt >= 380) {
            stepColor(3);
            sendColor(this.mColor);
            this.mLastColorAt = j;
        }
    }

    private void resetBassState() {
        this.mBassRaw = 0.0f;
        this.mBassFlux = 0.0f;
        this.mBassPrev = 0.0f;
        this.mBassAvg = 0.02f;
        this.mRageEnv = 0.0f;
        this.mLastBeatAt = 0L;
    }

    private void tickSoft(long j) {
        ensureFixedColor();
        float fClamp01 = clamp01(this.mSmooth);
        long jRound = 8200 - ((long) Math.round(2800.0f * fClamp01));
        if (jRound < 4800) {
            jRound = 4800;
        }
        double d = ((j % jRound) / jRound) * 3.141592653589793d * 2.0d;
        float fSin = (float) ((Math.sin(d) * 0.5d) + 0.5d);
        int iRound = (int) Math.round(((double) (Math.round(10.0f * fClamp01) + TX_GET_COLOR)) * Math.sin(d));
        sendBri(clampBri(Math.round(fSin * 22.0f) + 34 + Math.round(16.0f * fClamp01)));
        long jRound2 = 520 - ((long) Math.round(fClamp01 * 180.0f));
        if (jRound2 < 320) {
            jRound2 = 320;
        }
        if (j - this.mLastColorAt >= jRound2) {
            int iWrapColor = wrapColor(this.mFixedColor + iRound);
            this.mColor = iWrapColor;
            sendColor(iWrapColor);
            this.mLastColorAt = j;
        }
    }

    private void tickGradient(long j) {
        long j2;
        float fClamp01 = clamp01(this.mSmooth);
        float fMax = Math.max(0.0f, this.mBassFlux);
        float fMax2 = (this.mBassAvg * 0.92f) + (Math.max(fMax, 0.001f) * 0.08f);
        this.mBassAvg = fMax2;
        int i = 1;
        boolean z = fMax >= Math.max(0.028f, fMax2 * 1.5f) && fClamp01 >= 0.12f;
        sendBri(clampBri(Math.round(38.0f * fClamp01) + 32 + (z ? 12 : 0)));
        if (z) {
            j2 = 320;
        } else {
            j2 = fClamp01 < 0.12f ? 900L : 580L;
        }
        if (z) {
            i = 3;
        } else if (fClamp01 > 0.45f) {
            i = 2;
        }
        if (j - this.mLastGradAt >= j2) {
            stepColor(i);
            sendColor(this.mColor);
            this.mLastGradAt = j;
            this.mLastColorAt = j;
        }
    }

    private void tickPulse(long j) {
        ensureFixedColor();
        float fClamp01 = clamp01(this.mBassRaw);
        float fMax = Math.max(0.0f, this.mBassFlux);
        float fMax2 = (this.mBassAvg * 0.9f) + (Math.max(fMax, 0.001f) * 0.1f);
        this.mBassAvg = fMax2;
        boolean z = (((j - this.mLastBeatAt) > PULSE_BEAT_COOLDOWN_MS ? 1 : ((j - this.mLastBeatAt) == PULSE_BEAT_COOLDOWN_MS ? 0 : -1)) >= 0) && fMax >= Math.max(0.03f, fMax2 * PULSE_FLUX_MULT) && fClamp01 >= 0.07f;
        int iRound = Math.round(clamp01(this.mRageEnv) * 62.0f) + DRIVE_REV_A;
        if (z) {
            iRound = Math.min(100, iRound + RAGE_COLOR_STEP_STRONG);
        }
        sendBri(clampBri(iRound));
        if (z) {
            this.mLastBeatAt = j;
            this.mPulseHi = !this.mPulseHi;
            int iWrapColor = this.mPulseHi ? wrapColor(this.mFixedColor + Math.round(fClamp01 * 8.0f) + 12) : this.mFixedColor;
            this.mColor = iWrapColor;
            sendColor(iWrapColor);
            this.mLastColorAt = j;
            return;
        }
        if (fClamp01 < 0.1f && this.mRageEnv < 0.16f && j - this.mLastColorAt >= 360) {
            int i = this.mLastColor;
            int i2 = this.mFixedColor;
            if (i != i2) {
                this.mColor = i2;
                sendColor(i2);
                this.mPulseHi = false;
                this.mLastColorAt = j;
            }
        }
    }

    private int wrapColor(int i) {
        while (i > COLOR_MAX) {
            i -= 253;
        }
        while (i < 1) {
            i += COLOR_MAX;
        }
        return i;
    }

    private void ensureFixedColor() {
        int i = this.mFixedColor;
        if (i < 1 || i > COLOR_MAX) {
            this.mFixedColor = this.mColor;
        }
        if (this.mFixedColor < 1) {
            this.mFixedColor = 32;
        }
    }

    private void stepColor(int i) {
        int i2 = this.mColor + i;
        this.mColor = i2;
        if (i2 > COLOR_MAX) {
            this.mColor = (((i2 - COLOR_MAX) - 1) % COLOR_MAX) + 1;
        }
        if (this.mColor < 1) {
            this.mColor = COLOR_MAX;
        }
    }

    private void sendBri(int i) {
        if (i != this.mLastBri) {
            setBrightness(i);
            this.mLastBri = i;
        }
    }

    private void sendColor(int i) {
        if (i != this.mLastColor) {
            setColor(i);
            this.mLastColor = i;
            note("color=" + i + " mode=" + mode());
        }
    }

    private boolean attachVizBest() {
        int iFindPlayingAudioSessionId = findPlayingAudioSessionId();
        if (iFindPlayingAudioSessionId > 0 && attachViz(iFindPlayingAudioSessionId)) {
            note("viz ok sid=" + iFindPlayingAudioSessionId);
            return true;
        }
        if (attachViz(0)) {
            note("viz ok sid=0 (mix)");
            return true;
        }
        note("viz FAIL fake");
        return false;
    }

    private void maybeRefreshViz() {
        try {
            if (!this.mFake && this.mViz != null) {
                int iFindPlayingAudioSessionId = findPlayingAudioSessionId();
                if (iFindPlayingAudioSessionId > 0 && iFindPlayingAudioSessionId != this.mVizSessionId && attachViz(iFindPlayingAudioSessionId)) {
                    resetBassState();
                    note("viz switch sid=" + iFindPlayingAudioSessionId);
                }
                return;
            }
            resetBassState();
            if (attachVizBest()) {
                this.mFake = false;
                note("viz recovered sid=" + this.mVizSessionId);
            }
        } catch (Throwable th) {
            Log.w(TAG, "refresh viz", th);
        }
    }

    private int findPlayingAudioSessionId() {
        AudioManager audioManager;
        List<AudioPlaybackConfiguration> activePlaybackConfigurations;
        Method method;
        boolean zIsPreferredMusicUid;
        int usage;
        try {
            if (Build.VERSION.SDK_INT >= 26 && (audioManager = (AudioManager) this.mCtx.getSystemService("audio")) != null && audioManager.isMusicActive() && (activePlaybackConfigurations = audioManager.getActivePlaybackConfigurations()) != null && !activePlaybackConfigurations.isEmpty()) {
                Class<?> cls = Class.forName("android.media.AudioPlaybackConfiguration");
                try {
                    Method method2 = cls.getMethod("getAudioSessionId", new Class[0]);
                    try {
                        method = cls.getMethod("getClientUid", new Class[0]);
                    } catch (Throwable th) {
                        method = null;
                    }
                    int i = 0;
                    int i2 = 0;
                    int i3 = 0;
                    for (int i4 = 0; i4 < activePlaybackConfigurations.size(); i4++) {
                        AudioPlaybackConfiguration audioPlaybackConfiguration = activePlaybackConfigurations.get(i4);
                        if (audioPlaybackConfiguration != null) {
                            try {
                                int iIntValue = ((Integer) method2.invoke(audioPlaybackConfiguration, new Object[0])).intValue();
                                if (iIntValue > 0) {
                                    if (i3 == 0) {
                                        i3 = iIntValue;
                                    }
                                    if (method != null) {
                                        try {
                                            zIsPreferredMusicUid = isPreferredMusicUid(((Integer) method.invoke(audioPlaybackConfiguration, new Object[0])).intValue());
                                        } catch (Throwable th2) {
                                            zIsPreferredMusicUid = false;
                                        }
                                    } else {
                                        zIsPreferredMusicUid = false;
                                    }
                                    try {
                                        AudioAttributes audioAttributes = (AudioAttributes) audioPlaybackConfiguration.getClass().getMethod("getAudioAttributes", new Class[0]).invoke(audioPlaybackConfiguration, new Object[0]);
                                        if (audioAttributes == null) {
                                            usage = 0;
                                        } else {
                                            usage = audioAttributes.getUsage();
                                        }
                                    } catch (Throwable th3) {
                                        usage = 0;
                                    }
                                    boolean z = true;
                                    if (usage != 1 && usage != 14 && usage != 0) {
                                        z = false;
                                    }
                                    if (zIsPreferredMusicUid && z) {
                                        return iIntValue;
                                    }
                                    if (zIsPreferredMusicUid && i == 0) {
                                        i = iIntValue;
                                    }
                                    if (z && i2 == 0) {
                                        i2 = iIntValue;
                                    }
                                } else {
                                    continue;
                                }
                            } catch (Throwable th4) {
                            }
                        }
                    }
                    if (i > 0) {
                        return i;
                    }
                    if (i2 > 0) {
                        return i2;
                    }
                    return i3;
                } catch (Throwable th5) {
                    return 0;
                }
            }
            return 0;
        } catch (Throwable th6) {
            Log.w(TAG, "audio session", th6);
            return 0;
        }
    }

    private boolean isPreferredMusicUid(int i) {
        if (i <= 0) {
            return false;
        }
        try {
            String[] packagesForUid = this.mCtx.getPackageManager().getPackagesForUid(i);
            if (packagesForUid == null) {
                return false;
            }
            for (String str : packagesForUid) {
                if (isPreferredMusicPkg(str)) {
                    return true;
                }
            }
        } catch (Throwable th) {
        }
        return false;
    }

    private boolean attachViz(int i) {
        releaseViz();
        try {
            Visualizer visualizer = new Visualizer(i);
            int[] captureSizeRange = Visualizer.getCaptureSizeRange();
            try {
                visualizer.setCaptureSize((captureSizeRange == null || captureSizeRange.length < 2) ? 512 : captureSizeRange[1]);
            } catch (Throwable th) {
            }
            visualizer.setDataCaptureListener(new Visualizer.OnDataCaptureListener() { // from class: com.carassistant.ambient.AmbientRhythmHelper.4
                @Override // android.media.audiofx.Visualizer.OnDataCaptureListener
                public void onWaveFormDataCapture(Visualizer visualizer2, byte[] bArr, int i2) {
                    if (bArr == null || bArr.length == 0) {
                        return;
                    }
                    long j = 0;
                    for (byte b : bArr) {
                        long j2 = (b & 255) - 128;
                        j += j2 * j2;
                    }
                    AmbientRhythmHelper.this.mRawEnergy = AmbientRhythmHelper.clamp01((((float) Math.sqrt(j / ((double) bArr.length))) / 128.0f) * 1.8f);
                }

                @Override // android.media.audiofx.Visualizer.OnDataCaptureListener
                public void onFftDataCapture(Visualizer visualizer2, byte[] bArr, int i2) {
                    if (bArr == null || bArr.length < AmbientRhythmHelper.TX_GET_COLOR) {
                        return;
                    }
                    int iMin = Math.min(bArr.length, 64);
                    long j = 0;
                    for (int i3 = 2; i3 < iMin; i3++) {
                        long j2 = bArr[i3];
                        j += j2 * j2;
                    }
                    float fSqrt = ((float) Math.sqrt(j / ((double) Math.max(1, iMin - 2)))) / 128.0f;
                    AmbientRhythmHelper ambientRhythmHelper = AmbientRhythmHelper.this;
                    ambientRhythmHelper.mRawEnergy = AmbientRhythmHelper.clamp01(Math.max(ambientRhythmHelper.mRawEnergy, fSqrt * 2.2f));
                    int iMin2 = Math.min(bArr.length - 1, 22);
                    int i4 = 0;
                    float f = 0.0f;
                    for (int i5 = 2; i5 < iMin2; i5 += 2) {
                        float f2 = bArr[i5];
                        float f3 = bArr[i5 + 1];
                        f += (f2 * f2) + (f3 * f3);
                        i4++;
                    }
                    float fClamp01 = AmbientRhythmHelper.clamp01((i4 > 0 ? ((float) Math.sqrt(((double) f) / ((double) i4))) / 128.0f : 0.0f) * 2.6f);
                    float fMax = Math.max(0.0f, fClamp01 - AmbientRhythmHelper.this.mBassPrev);
                    AmbientRhythmHelper.this.mBassPrev = fClamp01;
                    AmbientRhythmHelper.this.mBassRaw = fClamp01;
                    AmbientRhythmHelper.this.mBassFlux = fMax;
                }
            }, Visualizer.getMaxCaptureRate() / 2, true, true);
            visualizer.setEnabled(true);
            this.mViz = visualizer;
            this.mVizSessionId = i;
            return true;
        } catch (Throwable th2) {
            Log.w(TAG, "viz fail sid=" + i, th2);
            note("viz err sid=" + i + " " + th2.getMessage());
            this.mViz = null;
            this.mVizSessionId = -1;
            return false;
        }
    }

    private void releaseViz() {
        Visualizer visualizer = this.mViz;
        this.mViz = null;
        this.mVizSessionId = -1;
        if (visualizer != null) {
            try {
                visualizer.setEnabled(false);
            } catch (Throwable th) {
            }
            try {
                visualizer.release();
            } catch (Throwable th2) {
            }
        }
    }

    private void saveLampOnce() {
        if (this.mSaved) {
            return;
        }
        int color = getColor();
        int brightness = getBrightness();
        if (color >= 1 && color <= COLOR_MAX) {
            this.mSavedColor = color;
            this.mColor = color;
            this.mFixedColor = color;
        } else {
            this.mSavedColor = this.mColor;
        }
        if (brightness < 0 || brightness > 100) {
            brightness = 50;
        }
        this.mSavedBri = brightness;
        this.mSaved = true;
        note("saved c=" + this.mSavedColor + " b=" + this.mSavedBri);
    }

    private void restoreLamp() {
        if (!this.mSaved) {
            return;
        }
        int i = this.mSavedColor;
        if (i >= 1) {
            setColor(i);
            this.mLastColor = this.mSavedColor;
        }
        int i2 = this.mSavedBri;
        if (i2 >= 0) {
            setBrightness(i2);
            this.mLastBri = this.mSavedBri;
        }
        this.mSaved = false;
        note("restored");
    }

    private void ensureBind() {
        synchronized (this.mLock) {
            if (!this.mBound && !this.mBinding) {
                this.mBinding = true;
                try {
                    Intent intent = new Intent(ADAPTER_CLS);
                    intent.setPackage(ADAPTER_PKG);
                    boolean zBindQuiet = bindQuiet(intent);
                    if (!zBindQuiet) {
                        Intent intent2 = new Intent();
                        intent2.setClassName(ADAPTER_PKG, ADAPTER_CLS);
                        zBindQuiet = bindQuiet(intent2);
                    }
                    if (!zBindQuiet) {
                        Intent intent3 = new Intent();
                        intent3.setPackage(ADAPTER_PKG);
                        zBindQuiet = bindQuiet(intent3);
                    }
                    if (!zBindQuiet) {
                        synchronized (this.mLock) {
                            this.mBinding = false;
                        }
                        note("FAIL no adapter");
                    }
                } catch (Throwable th) {
                    synchronized (this.mLock) {
                        this.mBinding = false;
                        Log.e(TAG, "bind", th);
                    }
                }
            }
        }
    }

    private boolean bindQuiet(Intent intent) {
        try {
            return this.mCtx.bindService(intent, this.mConn, 1);
        } catch (Throwable th) {
            return false;
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void resolveAmbient() {
        IBinder iBinder;
        synchronized (this.mLock) {
            iBinder = this.mRemote;
        }
        if (iBinder == null) {
            return;
        }
        try {
            IBinder iBinderQueryBinder = queryBinder(iBinder, 3);
            synchronized (this.mLock) {
                this.mAmbient = iBinderQueryBinder;
            }
            note("ambient=" + (iBinderQueryBinder != null));
        } catch (Throwable th) {
            Log.e(TAG, "resolve ambient", th);
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void resolveState() {
        IBinder iBinder;
        synchronized (this.mLock) {
            iBinder = this.mRemote;
        }
        if (iBinder == null) {
            return;
        }
        try {
            IBinder iBinderQueryBinder = queryBinder(iBinder, BINDER_STATE);
            synchronized (this.mLock) {
                this.mState = iBinderQueryBinder;
            }
            note("state=" + (iBinderQueryBinder != null));
        } catch (Throwable th) {
            Log.e(TAG, "resolve state", th);
        }
    }

    private IBinder stateOrNull() {
        IBinder iBinder;
        synchronized (this.mLock) {
            iBinder = this.mState;
        }
        if (iBinder == null) {
            resolveState();
            synchronized (this.mLock) {
                iBinder = this.mState;
            }
        }
        return iBinder;
    }

    private int readGear() {
        int iTxStateGet = txStateGet(6);
        if (iTxStateGet == Integer.MIN_VALUE || iTxStateGet == -65532) {
            return 3;
        }
        return iTxStateGet;
    }

    private float readSpeedKmh() {
        int iTxStateGet = txStateGet(7);
        if (iTxStateGet == Integer.MIN_VALUE || iTxStateGet == -65532 || iTxStateGet < 0) {
            return 0.0f;
        }
        if (iTxStateGet > 300) {
            return iTxStateGet / 100.0f;
        }
        return iTxStateGet;
    }

    private int txStateGet(int i) {
        IBinder iBinderStateOrNull = stateOrNull();
        if (iBinderStateOrNull == null) {
            return Integer.MIN_VALUE;
        }
        Parcel parcelObtain = Parcel.obtain();
        Parcel parcelObtain2 = Parcel.obtain();
        try {
            parcelObtain.writeInterfaceToken(DESC_STATE);
            iBinderStateOrNull.transact(i, parcelObtain, parcelObtain2, 0);
            parcelObtain2.readException();
            int i2 = parcelObtain2.readInt();
            parcelObtain2.recycle();
            parcelObtain.recycle();
            return i2;
        } catch (Throwable th) {
            try {
                synchronized (this.mLock) {
                    this.mState = null;
                    parcelObtain2.recycle();
                    parcelObtain.recycle();
                    return Integer.MIN_VALUE;
                }
            } catch (Throwable th2) {
                parcelObtain2.recycle();
                parcelObtain.recycle();
                throw th2;
            }
        }
    }

    private IBinder queryBinder(IBinder iBinder, int i) throws RemoteException {
        Parcel parcelObtain = Parcel.obtain();
        Parcel parcelObtain2 = Parcel.obtain();
        try {
            parcelObtain.writeInterfaceToken(DESC_REMOTE);
            parcelObtain.writeInt(i);
            iBinder.transact(1, parcelObtain, parcelObtain2, 0);
            parcelObtain2.readException();
            return parcelObtain2.readStrongBinder();
        } finally {
            parcelObtain2.recycle();
            parcelObtain.recycle();
        }
    }

    private IBinder ambientOrNull() {
        IBinder iBinder;
        synchronized (this.mLock) {
            iBinder = this.mAmbient;
        }
        if (iBinder == null) {
            resolveAmbient();
            synchronized (this.mLock) {
                iBinder = this.mAmbient;
            }
        }
        return iBinder;
    }

    private int setLamp(int i) {
        return txInt(2, i);
    }

    private int setBrightness(int i) {
        return txInt(4, i);
    }

    private int setColor(int i) {
        return txInt(5, i);
    }

    private int getBrightness() {
        return txGet(3);
    }

    private int getColor() {
        return txGet(TX_GET_COLOR);
    }

    private int setMusicPulse(boolean z) {
        IBinder iBinderAmbientOrNull = ambientOrNull();
        if (iBinderAmbientOrNull == null) {
            return -1;
        }
        Parcel parcelObtain = Parcel.obtain();
        Parcel parcelObtain2 = Parcel.obtain();
        try {
            parcelObtain.writeInterfaceToken(DESC_AMBIENT);
            parcelObtain.writeInt(z ? 1 : 0);
            iBinderAmbientOrNull.transact(7, parcelObtain, parcelObtain2, 0);
            parcelObtain2.readException();
            int i = parcelObtain2.readInt();
            parcelObtain2.recycle();
            parcelObtain.recycle();
            return i;
        } catch (Throwable th) {
            try {
                invalidate();
                return -2;
            } finally {
                parcelObtain2.recycle();
                parcelObtain.recycle();
            }
        }
    }

    private int txInt(int i, int i2) {
        IBinder iBinderAmbientOrNull = ambientOrNull();
        if (iBinderAmbientOrNull == null) {
            return -1;
        }
        Parcel parcelObtain = Parcel.obtain();
        Parcel parcelObtain2 = Parcel.obtain();
        try {
            parcelObtain.writeInterfaceToken(DESC_AMBIENT);
            parcelObtain.writeInt(i2);
            iBinderAmbientOrNull.transact(i, parcelObtain, parcelObtain2, 0);
            parcelObtain2.readException();
            int i3 = parcelObtain2.readInt();
            parcelObtain2.recycle();
            parcelObtain.recycle();
            return i3;
        } catch (Throwable th) {
            try {
                invalidate();
                return -2;
            } finally {
                parcelObtain2.recycle();
                parcelObtain.recycle();
            }
        }
    }

    private int txGet(int i) {
        IBinder iBinderAmbientOrNull = ambientOrNull();
        if (iBinderAmbientOrNull == null) {
            return -1;
        }
        Parcel parcelObtain = Parcel.obtain();
        Parcel parcelObtain2 = Parcel.obtain();
        try {
            parcelObtain.writeInterfaceToken(DESC_AMBIENT);
            iBinderAmbientOrNull.transact(i, parcelObtain, parcelObtain2, 0);
            parcelObtain2.readException();
            int i2 = parcelObtain2.readInt();
            parcelObtain2.recycle();
            parcelObtain.recycle();
            return i2;
        } catch (Throwable th) {
            try {
                invalidate();
                return -2;
            } finally {
                parcelObtain2.recycle();
                parcelObtain.recycle();
            }
        }
    }

    private void invalidate() {
        synchronized (this.mLock) {
            this.mAmbient = null;
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static float clamp01(float f) {
        if (f < 0.0f) {
            return 0.0f;
        }
        if (f > 1.0f) {
            return 1.0f;
        }
        return f;
    }

    private static int clampBri(int i) {
        if (i < RAGE_BASS_BINS) {
            return RAGE_BASS_BINS;
        }
        if (i > 100) {
            return 100;
        }
        return i;
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void note(String str) {
        try {
            FileOutputStream fileOutputStream = new FileOutputStream(new File(this.mCtx.getExternalFilesDir(null), "ambient_rhythm_qg.txt"), false);
            fileOutputStream.write((System.currentTimeMillis() + " " + str + "\n").getBytes("UTF-8"));
            fileOutputStream.close();
        } catch (Throwable th) {
        }
    }
}
