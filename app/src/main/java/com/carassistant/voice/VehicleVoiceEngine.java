package com.carassistant.voice;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.media.AudioManager;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Parcel;
import android.os.RemoteException;
import android.os.SystemClock;
import android.preference.PreferenceManager;
import android.util.Log;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Arrays;
import java.util.LinkedHashSet;

/* JADX INFO: loaded from: classes.dex */
public final class VehicleVoiceEngine {
    private static Throwable th;
    private static final String ADAPTER_CLS = "ltd.qisi.adapterservice.QISIAdapterService";
    private static final String ADAPTER_PKG = "ltd.qisi.adapterservice";
    private static final int BINDER_BCM = 25;
    private static final int BINDER_STATE = 15;
    private static final int CAN_BCM1 = 560;
    private static final String CAR_VEHICLE_STATE = "car_vehicle_state";
    private static final String CAR_VOICE = "car_voice_manager";
    private static final String DESC_BCM = "ltd.qisi.adapterproxy.ICarBCMManager";
    private static final String DESC_EXT = "android.car.IExternalCar";
    private static final String DESC_REMOTE = "ltd.qisi.adapterproxy.IVehicleRemoteClienter";
    private static final String DESC_STATE = "ltd.qisi.adapterproxy.ICarVehicleStateManager";
    private static final String DESC_VEH = "android.car.ICarVehicleState";
    private static final int DOOR_FL = 0;
    private static final int DOOR_FR = 1;
    private static final int DOOR_RL = 2;
    private static final int DOOR_RR = 3;
    private static final String EXT_ACTION = "android.car.IExternalCar";
    private static final String EXT_PKG = "com.android.externalcar";
    private static final int GEAR_D = 3;
    private static final long GEAR_DEBOUNCE_MS = 600;
    private static final int GEAR_N = 2;
    private static final int GEAR_P = 0;
    private static final int GEAR_R = 1;
    private static final int INVALID = Integer.MIN_VALUE;
    private static final long POLL_MS = 200;
    public static final String PREF_DOOR_DRIVER = "key_carvoice_door_driver";
    public static final String PREF_DOOR_OPEN_ONLY = "key_carvoice_door_open_only";
    public static final String PREF_DOOR_PASSENGER = "key_carvoice_door_passenger";
    public static final String PREF_DOOR_RL = "key_carvoice_door_rl";
    public static final String PREF_DOOR_RR = "key_carvoice_door_rr";
    public static final String PREF_ENABLED = "key_carvoice_enabled";
    public static final String PREF_GEAR = "key_carvoice_gear";
    public static final String PREF_TURN = "key_carvoice_turn";
    public static final String PREF_VOL_BOOST = "key_carvoice_vol_boost";
    public static final String PREF_VOL_LEVEL = "key_carvoice_vol_level";
    private static final int PROP_DOOR_POS = 373295872;
    private static final int PROP_FAW_DOOR_POS = 557859193;
    private static final int PROP_HAZARD_STATE = 289410563;
    private static final int PROP_TURN_SIGNAL = 289408008;
    public static final String TAG = "CarVoice";
    private static final long TURN_DEBOUNCE_MS = 700;
    private static final int TURN_HAZARD = 3;
    private static final long TURN_HOLD_MS = 1300;
    private static final int TURN_LEFT = 1;
    private static final int TURN_OFF = 0;
    private static final int TURN_RIGHT = 2;
    private static final long TURN_SETTLE_MS = 400;
    private static final int TX_EXT_GET_SERVICE = 2;
    private static final int TX_GET_FLAG_LIGHT = 88;
    private static final int TX_GET_GEAR = 6;
    private static final int TX_QUERY_BINDER = 1;
    private static final int TX_VEH_GET_INT = 2;
    private static final int VOICE_OP_GET = 2;
    public static final int VOL_LEVEL_MAX = 2;
    public static final int VOL_LEVEL_MID = 0;
    public static final int VOL_LEVEL_MID_HIGH = 1;
    private static boolean sPreviewBoostHeld;
    private static PlayHandle sPreviewPlayer;
    private static Runnable sPreviewStop;
    private static Context sVolApp;
    private static int sVolHold;
    private boolean mAlive;
    private IBinder mBcm;
    private boolean mBinding;
    private boolean mBound;
    private Object mCabinMgr;
    private boolean mCanListenersRegistered;
    private Object mCanMgr;
    private boolean mCarConnecting;
    private Object mCarObj;
    private final Context mCtx;
    private IBinder mExt;
    private boolean mExtBinding;
    private boolean mExtBound;
    private boolean mGearSeeded;
    private long mLastDoorDumpAt;
    private long mLastGearPlayAt;
    private long mLastTurnPlayAt;
    private long mLastVoiceRetryAt;
    private PlayHandle mOneShot;
    private boolean mOneShotBoostHeld;
    private Object mPropMgr;
    private IBinder mRemote;
    private PlayHandle mReverse;
    private boolean mReverseBoostHeld;
    private IBinder mState;
    private long mTurnCanHoldUntil;
    private boolean mTurnSeeded;
    private int mTurnSeenBits;
    private long mTurnSessionStart;
    private IBinder mVeh;
    private Object mVehMgr;
    private Object mVoiceMgr;
    private boolean mVoiceReadySeen;
    private static final int[] PROP_FAW_DOOR_ROW = {557859200, 557859201, 557859202, 557859203};
    private static final int DOOR_COUNT = 4;
    private static final int[] DOOR_AREA = {1, DOOR_COUNT, 16, 64};
    private static final int[] DOOR_CAN_SIG = {38404609, 38535681, 38666753, 38732289};
    private static final String[] DOOR_OPEN_EVENT = {VoiceEvents.DOOR_FL_OPEN, VoiceEvents.DOOR_FR_OPEN, VoiceEvents.DOOR_RL_OPEN, VoiceEvents.DOOR_RR_OPEN};
    private static final String[] DOOR_CLOSE_EVENT = {VoiceEvents.DOOR_FL_CLOSE, VoiceEvents.DOOR_FR_CLOSE, VoiceEvents.DOOR_RL_CLOSE, VoiceEvents.DOOR_RR_CLOSE};
    private static final Handler sPreviewHandler = new Handler(Looper.getMainLooper());
    private static final Object sVolLock = new Object();
    private static int sSavedMusicVol = -1;
    private final Handler mMain = new Handler(Looper.getMainLooper());
    private int mLastGear = INVALID;
    private final int[] mLastDoor = {INVALID, INVALID, INVALID, INVALID};
    private final boolean[] mDoorSeeded = new boolean[DOOR_COUNT];
    private final byte[][] mDoorFrames = new byte[DOOR_COUNT][];
    private int mDoorPolarity = 1;
    private String mDoorSrcName = "?";
    private int mLastTurn = INVALID;
    private String mTurnSrc = "?";
    private int mTurnCanHeld = 0;
    private final Object mLock = new Object();
    private final Object mAudioLock = new Object();
    private final ServiceConnection mConn = new ServiceConnection() { // from class: com.carassistant.voice.VehicleVoiceEngine.1
        @Override // android.content.ServiceConnection
        public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
            synchronized (VehicleVoiceEngine.this.mLock) {
                VehicleVoiceEngine.this.mRemote = iBinder;
                VehicleVoiceEngine.this.mState = null;
                VehicleVoiceEngine.this.mBound = true;
                VehicleVoiceEngine.this.mBinding = false;
            }
            VehicleVoiceEngine.this.resolveState();
            Log.i(VehicleVoiceEngine.TAG, "qisi connected");
        }

        @Override // android.content.ServiceConnection
        public void onServiceDisconnected(ComponentName componentName) {
            synchronized (VehicleVoiceEngine.this.mLock) {
                VehicleVoiceEngine.this.mRemote = null;
                VehicleVoiceEngine.this.mState = null;
                VehicleVoiceEngine.this.mBcm = null;
                VehicleVoiceEngine.this.mBound = false;
                VehicleVoiceEngine.this.mBinding = false;
            }
        }
    };
    private final ServiceConnection mExtConn = new ServiceConnection() { // from class: com.carassistant.voice.VehicleVoiceEngine.2
        @Override // android.content.ServiceConnection
        public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
            synchronized (VehicleVoiceEngine.this.mLock) {
                VehicleVoiceEngine.this.mExt = iBinder;
                VehicleVoiceEngine.this.mVeh = null;
                VehicleVoiceEngine.this.mExtBound = true;
                VehicleVoiceEngine.this.mExtBinding = false;
            }
            VehicleVoiceEngine.this.resolveVeh();
            Log.i(VehicleVoiceEngine.TAG, "externalcar binder connected");
        }

        @Override // android.content.ServiceConnection
        public void onServiceDisconnected(ComponentName componentName) {
            synchronized (VehicleVoiceEngine.this.mLock) {
                VehicleVoiceEngine.this.mExt = null;
                VehicleVoiceEngine.this.mVeh = null;
                VehicleVoiceEngine.this.mExtBound = false;
                VehicleVoiceEngine.this.mExtBinding = false;
            }
        }
    };
    private final ServiceConnection mCarConn = new ServiceConnection() { // from class: com.carassistant.voice.VehicleVoiceEngine.3
        @Override // android.content.ServiceConnection
        public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
            Log.i(VehicleVoiceEngine.TAG, "car service connected (client cb)");
            VehicleVoiceEngine.this.refreshCarManagers();
        }

        @Override // android.content.ServiceConnection
        public void onServiceDisconnected(ComponentName componentName) {
            synchronized (VehicleVoiceEngine.this.mLock) {
                VehicleVoiceEngine.this.mVehMgr = null;
                VehicleVoiceEngine.this.mPropMgr = null;
                VehicleVoiceEngine.this.mCabinMgr = null;
                VehicleVoiceEngine.this.mVoiceMgr = null;
            }
            Log.w(VehicleVoiceEngine.TAG, "car service disconnected");
        }
    };
    private final Runnable mPoll = new Runnable() { // from class: com.carassistant.voice.VehicleVoiceEngine.4
        @Override // java.lang.Runnable
        public void run() {
            if (VehicleVoiceEngine.this.mAlive) {
                try {
                    if (VehicleVoiceEngine.this.enabled()) {
                        VehicleVoiceEngine.this.pollOnce();
                    } else {
                        VehicleVoiceEngine.this.stopReverse();
                    }
                } catch (Throwable th) {
                    Log.w(VehicleVoiceEngine.TAG, "poll", th);
                }
                if (VehicleVoiceEngine.this.mAlive) {
                    VehicleVoiceEngine.this.mMain.postDelayed(this, VehicleVoiceEngine.POLL_MS);
                }
            }
        }
    };

    public VehicleVoiceEngine(Context context) {
        this.mCtx = context.getApplicationContext();
    }

    public void start() {
        this.mAlive = true;
        ensureBind();
        ensureExtBind();
        ensureCarReflect();
        this.mMain.removeCallbacks(this.mPoll);
        this.mMain.post(this.mPoll);
        Log.i(TAG, "helper start");
    }

    public void destroy() {
        this.mAlive = false;
        this.mMain.removeCallbacks(this.mPoll);
        stopReverse();
        stopOneShot();
        try {
            if (this.mBound || this.mBinding) {
                this.mCtx.unbindService(this.mConn);
            }
        } catch (Throwable th) {
        }
        try {
            if (this.mExtBound || this.mExtBinding) {
                this.mCtx.unbindService(this.mExtConn);
            }
        } catch (Throwable th2) {
        }
        try {
            Object obj = this.mCarObj;
            if (obj != null) {
                obj.getClass().getMethod("disconnect", new Class[0]).invoke(this.mCarObj, new Object[0]);
            }
        } catch (Throwable th3) {
        }
        this.mBound = false;
        this.mBinding = false;
        this.mExtBound = false;
        this.mExtBinding = false;
        this.mCarObj = null;
        Log.i(TAG, "helper destroy");
    }

    public String preview(String str) {
        return playPreviewStatic(this.mCtx, str);
    }

    public static String diagnoseDoors(Context context) {
        Object objInvoke;
        Object objInvoke2;
        if (context == null) {
            return "ctx空";
        }
        Context applicationContext = context.getApplicationContext();
        StringBuilder sb = new StringBuilder();
        final Object[] objArr = new Object[1];
        ServiceConnection serviceConnection = new ServiceConnection() { // from class: com.carassistant.voice.VehicleVoiceEngine.5
            @Override // android.content.ServiceConnection
            public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
                synchronized (objArr) {
                    objArr[0] = Boolean.TRUE;
                    objArr.notifyAll();
                }
            }

            @Override // android.content.ServiceConnection
            public void onServiceDisconnected(ComponentName componentName) {
            }
        };
        Object objInvoke3 = null;
        try {
            Class<?> cls = Class.forName("android.car.Car");
            Object objInvoke4 = cls.getMethod("createCar", Context.class, ServiceConnection.class).invoke(null, applicationContext, serviceConnection);
            if (objInvoke4 == null) {
                if (objInvoke4 != null) {
                    try {
                        objInvoke4.getClass().getMethod("disconnect", new Class[0]).invoke(objInvoke4, new Object[0]);
                    } catch (Throwable th) {
                    }
                }
                return "Car.createCar=null(无automotive?)";
            }
            try {
                cls.getMethod("connect", new Class[0]).invoke(objInvoke4, new Object[0]);
                synchronized (objArr) {
                    if (objArr[0] == null) {
                        objArr.wait(2500L);
                    }
                }
                Thread.sleep(800L);
                Method method = objInvoke4.getClass().getMethod("getCarManager", String.class);
                try {
                    objInvoke = method.invoke(objInvoke4, CAR_VEHICLE_STATE);
                } catch (Throwable th2) {
                    objInvoke = null;
                }
                try {
                    objInvoke2 = method.invoke(objInvoke4, "property");
                } catch (Throwable th3) {
                    objInvoke2 = null;
                }
                try {
                    method.invoke(objInvoke4, "cabin");
                } catch (Throwable th4) {
                }
                try {
                    objInvoke3 = method.invoke(objInvoke4, CAR_VOICE);
                } catch (Throwable th5) {
                }
                sb.append("carOK voice=").append(objInvoke3 != null).append(" veh=").append(objInvoke != null).append(" prop=").append(objInvoke2 != null).append('\n');
                String[] strArr = {"FL", "FR", "RL", "RR"};
                for (int i = 0; i < DOOR_COUNT; i++) {
                    sb.append(strArr[i]).append(":can=").append(diagCanDoor(objInvoke3, DOOR_CAN_SIG[i])).append(" faw=").append(diagGetInt(objInvoke, PROP_FAW_DOOR_POS, DOOR_AREA[i])).append('/').append(diagGetInt(objInvoke2, PROP_FAW_DOOR_ROW[i], 0)).append(' ');
                }
                sb.append('\n').append(diagCanDoorRaw(objInvoke3, DOOR_CAN_SIG[0]));
                Log.i(TAG, "diagnose " + ((Object) sb));
                String strTrim = sb.toString().trim();
                if (objInvoke4 != null) {
                    try {
                        objInvoke4.getClass().getMethod("disconnect", new Class[0]).invoke(objInvoke4, new Object[0]);
                    } catch (Throwable th6) {
                    }
                }
                return strTrim;
            } catch (Throwable th7) {
                th = th7;
                objInvoke3 = objInvoke4;
                try {
                    Log.e(TAG, "diagnose", th);
                    String str = "诊断失败: " + th.getClass().getSimpleName() + " " + th.getMessage();
                    if (objInvoke3 != null) {
                        try {
                            objInvoke3.getClass().getMethod("disconnect", new Class[0]).invoke(objInvoke3, new Object[0]);
                        } catch (Throwable th8) {
                        }
                    }
                    return str;
                } finally {
                    if (objInvoke3 != null) {
                        try {
                            objInvoke3.getClass().getMethod("disconnect", new Class[0]).invoke(objInvoke3, new Object[0]);
                        } catch (Throwable th9) {
                        }
                    }
                }
            }
        } catch (Throwable th10) {
            th = th10;
        }
        return null;
    }

    private static int diagGetInt(Object obj, int i, int i2) {
        if (obj == null) {
            return -999;
        }
        try {
            Object objInvoke = obj.getClass().getMethod("getIntProperty", Integer.TYPE, Integer.TYPE).invoke(obj, Integer.valueOf(i), Integer.valueOf(i2));
            if (objInvoke instanceof Integer) {
                return ((Integer) objInvoke).intValue();
            }
            return -997;
        } catch (Throwable th) {
            return -998;
        }
    }

    private static int canMsgId(int i) {
        return (i >>> 16) & 65535;
    }

    private static int canStartBit(int i) {
        return (i >>> 8) & 255;
    }

    private static int canLength(int i) {
        int i2 = i & 255;
        if (i2 <= 0) {
            return 1;
        }
        return i2;
    }

    private static int diagCanDoor(Object obj, int i) {
        if (obj == null) {
            return -999;
        }
        try {
            int iExtractCanSignal = extractCanSignal(fetchCanFrame(obj, canMsgId(i)), canStartBit(i), canLength(i));
            if (iExtractCanSignal == INVALID) {
                return -997;
            }
            return iExtractCanSignal;
        } catch (Throwable th) {
            Log.w(TAG, "diagCanDoor sig=" + Integer.toHexString(i), th);
            return -998;
        }
    }

    private static String diagCanDoorRaw(Object obj, int i) {
        if (obj == null) {
            return "raw=null";
        }
        try {
            int iCanMsgId = canMsgId(i);
            byte[] bArrFetchCanFrame = fetchCanFrame(obj, iCanMsgId);
            StringBuilder sb = new StringBuilder();
            sb.append("msg=0x").append(Integer.toHexString(iCanMsgId)).append(" bit=").append(canStartBit(i)).append(" raw=");
            if (bArrFetchCanFrame == null) {
                sb.append("null");
            } else {
                for (int i2 = 0; i2 < bArrFetchCanFrame.length && i2 < 8; i2++) {
                    if (i2 > 0) {
                        sb.append(',');
                    }
                    sb.append(String.format("%02X", Integer.valueOf(bArrFetchCanFrame[i2] & 255)));
                }
            }
            return sb.toString();
        } catch (Throwable th) {
            return "rawErr";
        }
    }

    private static byte[] fetchCanFrame(Object obj, int i) throws Exception {
        Object objInvoke = obj.getClass().getMethod("getMessage", Integer.TYPE, Integer.TYPE, byte[].class).invoke(obj, 2, Integer.valueOf(i), new byte[0]);
        if (objInvoke instanceof byte[]) {
            return (byte[]) objInvoke;
        }
        return null;
    }

    private static int extractCanSignal(byte[] bArr, int i, int i2) {
        if (bArr == null || bArr.length == 0 || i < 0 || i2 < 1 || i > (bArr.length * 8) - 1 || isAllFf(bArr)) {
            return INVALID;
        }
        return extractCanBits(bArr, i, i2);
    }

    private static int extractCanBits(byte[] bArr, int i, int i2) {
        int i3 = i % 8;
        if (i3 + i2 <= 8) {
            int i4 = i / 8;
            return (i4 < 0 || i4 >= bArr.length) ? INVALID : ((bArr[i4] & 255) >> i3) & ((1 << i2) - 1);
        }
        int i5 = 8 - i3;
        int iExtractCanBits = extractCanBits(bArr, i, i5);
        int iExtractCanBits2 = extractCanBits(bArr, i + i5, i2 - i5);
        return (iExtractCanBits == INVALID || iExtractCanBits2 == INVALID) ? INVALID : (iExtractCanBits2 << i5) | iExtractCanBits;
    }

    public static String playPreviewStatic(Context context, String str) {
        return playPreviewStatic(context, str, false);
    }

    public static String playPreviewStatic(Context context, String str, boolean z) {
        if (context == null || str == null) {
            return "参数空";
        }
        Context applicationContext = context.getApplicationContext();
        if (str.indexOf(58) < 0) {
            str = "raw:" + str;
        }
        stopPreviewStatic();
        sPreviewBoostHeld = beginVolumeBoost(applicationContext);
        try {
            AudioManager audioManager = (AudioManager) applicationContext.getSystemService("audio");
            if (audioManager != null) {
                try {
                    audioManager.requestAudioFocus(null, 3, 3);
                } catch (Throwable th) {
                }
            }
        } catch (Throwable th2) {
        }
        boolean z2 = z || str.endsWith(":gear_r") || str.equals("raw:gear_r");
        PlayHandle playHandlePlay = VoiceLibrary.play(applicationContext, str, z2);
        if (playHandlePlay == null) {
            if (sPreviewBoostHeld) {
                endVolumeBoost();
                sPreviewBoostHeld = false;
                return "播放失败";
            }
            return "播放失败";
        }
        sPreviewPlayer = playHandlePlay;
        long j = z2 ? 2600L : 3500L;
        Runnable runnable = new Runnable() { // from class: com.carassistant.voice.VehicleVoiceEngine.6
            @Override // java.lang.Runnable
            public void run() {
                VehicleVoiceEngine.stopPreviewStatic();
            }
        };
        sPreviewStop = runnable;
        sPreviewHandler.postDelayed(runnable, j);
        return "播放中 " + str;
    }

    public static String playEventPreview(Context context, String str) {
        VoiceEvents.Event eventFind = VoiceEvents.find(str);
        return playPreviewStatic(context, VoiceLibrary.getSelectedId(context, str), eventFind != null && eventFind.loopPreview);
    }

    public static void stopPreviewStaticPublic() {
        stopPreviewStatic();
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static void stopPreviewStatic() {
        Runnable runnable = sPreviewStop;
        if (runnable != null) {
            sPreviewHandler.removeCallbacks(runnable);
            sPreviewStop = null;
        }
        PlayHandle playHandle = sPreviewPlayer;
        sPreviewPlayer = null;
        if (playHandle != null) {
            playHandle.stop();
        }
        if (sPreviewBoostHeld) {
            endVolumeBoost();
            sPreviewBoostHeld = false;
        }
    }

    public static boolean beginVolumeBoost(Context context) {
        int iMax;
        if (context == null) {
            return false;
        }
        Context applicationContext = context.getApplicationContext();
        SharedPreferences defaultSharedPreferences = PreferenceManager.getDefaultSharedPreferences(applicationContext);
        if (!defaultSharedPreferences.getBoolean(PREF_VOL_BOOST, false)) {
            return false;
        }
        int i = defaultSharedPreferences.getInt(PREF_VOL_LEVEL, 1);
        synchronized (sVolLock) {
            try {
                AudioManager audioManager = (AudioManager) applicationContext.getSystemService("audio");
                if (audioManager == null) {
                    return false;
                }
                if (sVolHold == 0) {
                    int streamMaxVolume = audioManager.getStreamMaxVolume(3);
                    sSavedMusicVol = audioManager.getStreamVolume(3);
                    sVolApp = applicationContext;
                    if (i <= 0) {
                        iMax = Math.max(1, streamMaxVolume / 2);
                    } else if (i == 1) {
                        iMax = Math.max(1, (streamMaxVolume * 3) / DOOR_COUNT);
                    } else {
                        iMax = Math.max(1, streamMaxVolume);
                    }
                    audioManager.setStreamVolume(3, iMax, 0);
                }
                sVolHold++;
                return true;
            } catch (Throwable th) {
                Log.w(TAG, "beginVolumeBoost", th);
                return false;
            }
        }
    }

    public static void endVolumeBoost() {
        Context context;
        synchronized (sVolLock) {
            int i = sVolHold;
            if (i <= 0) {
                sVolHold = 0;
                return;
            }
            int i2 = i - 1;
            sVolHold = i2;
            if (i2 > 0) {
                return;
            }
            if (sSavedMusicVol < 0 || (context = sVolApp) == null) {
                sSavedMusicVol = -1;
                sVolApp = null;
                return;
            }
            try {
                AudioManager audioManager = (AudioManager) context.getSystemService("audio");
                if (audioManager != null) {
                    int streamMaxVolume = audioManager.getStreamMaxVolume(3);
                    int i3 = sSavedMusicVol;
                    if (i3 <= streamMaxVolume) {
                        streamMaxVolume = i3;
                    }
                    if (streamMaxVolume < 0) {
                        streamMaxVolume = 0;
                    }
                    audioManager.setStreamVolume(3, streamMaxVolume, 0);
                }
            } catch (Throwable th) {
            }
            sSavedMusicVol = -1;
            sVolApp = null;
        }
    }

    private SharedPreferences prefs() {
        return PreferenceManager.getDefaultSharedPreferences(this.mCtx);
    }

    /* JADX INFO: Access modifiers changed from: private */
    public boolean enabled() {
        return prefs().getBoolean(PREF_ENABLED, false);
    }

    private boolean gearOn() {
        return prefs().getBoolean(PREF_GEAR, true);
    }

    private boolean doorOn(int i) {
        switch (i) {
            case VOL_LEVEL_MID /* 0 */:
                return prefs().getBoolean(PREF_DOOR_DRIVER, true);
            case VOL_LEVEL_MID_HIGH /* 1 */:
                return prefs().getBoolean(PREF_DOOR_PASSENGER, true);
            case VOL_LEVEL_MAX /* 2 */:
                return prefs().getBoolean(PREF_DOOR_RL, true);
            case 3:
                return prefs().getBoolean(PREF_DOOR_RR, true);
            default:
                return false;
        }
    }

    private boolean anyDoorOn() {
        return doorOn(0) || doorOn(1) || doorOn(2) || doorOn(3);
    }

    private boolean doorOpenOnly() {
        return prefs().getBoolean(PREF_DOOR_OPEN_ONLY, false);
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void pollOnce() {
        ensureBind();
        ensureExtBind();
        ensureCarReflect();
        ensureVoicePaths();
        refreshCarManagers();
        if (gearOn()) {
            pollGear();
        } else {
            stopReverse();
        }
        if (anyDoorOn()) {
            pollDoors();
        }
        if (turnOn()) {
            pollTurn();
        }
    }

    private boolean turnOn() {
        return prefs().getBoolean(PREF_TURN, true);
    }

    private void pollTurn() {
        int turnMode = readTurnMode();
        if (turnMode == INVALID) {
            return;
        }
        if (!this.mTurnSeeded) {
            this.mLastTurn = turnMode;
            this.mTurnSeeded = true;
            Log.i(TAG, "turn seed mode=" + turnMode + " src=" + this.mTurnSrc);
            return;
        }
        int i = this.mLastTurn;
        if (turnMode == i) {
            return;
        }
        this.mLastTurn = turnMode;
        long jUptimeMillis = SystemClock.uptimeMillis();
        if (jUptimeMillis - this.mLastTurnPlayAt < TURN_DEBOUNCE_MS) {
            return;
        }
        if (turnMode == 0) {
            Log.i(TAG, "turn off (was " + i + ") src=" + this.mTurnSrc);
            return;
        }
        this.mLastTurnPlayAt = jUptimeMillis;
        String strTurnEvent = turnEvent(turnMode);
        Log.i(TAG, "turn " + i + "->" + turnMode + " event=" + strTurnEvent + " src=" + this.mTurnSrc);
        if (strTurnEvent != null) {
            playEvent(strTurnEvent, false);
        }
    }

    private static String turnEvent(int i) {
        switch (i) {
            case VOL_LEVEL_MID_HIGH /* 1 */:
                return VoiceEvents.TURN_LEFT;
            case VOL_LEVEL_MAX /* 2 */:
                return VoiceEvents.TURN_RIGHT;
            case 3:
                return VoiceEvents.HAZARD;
            default:
                return null;
        }
    }

    private int readTurnMode() {
        int iMapFlagLight;
        int turnFromCan = readTurnFromCan();
        if (turnFromCan != INVALID) {
            return turnFromCan;
        }
        int iTryPropMgrRaw = tryPropMgrRaw(PROP_HAZARD_STATE, 0);
        if (iTryPropMgrRaw == INVALID) {
            iTryPropMgrRaw = tryVehMgrRaw(PROP_HAZARD_STATE, 0);
        }
        if (iTryPropMgrRaw == INVALID) {
            iTryPropMgrRaw = tryBinderVehRaw(PROP_HAZARD_STATE, 0);
        }
        if (iTryPropMgrRaw == 1) {
            this.mTurnSrc = "hazardProp";
            return 3;
        }
        int flagLight = readFlagLight();
        if (flagLight != INVALID && (iMapFlagLight = mapFlagLight(flagLight)) != INVALID) {
            this.mTurnSrc = "flagLight=" + flagLight;
            return iMapFlagLight;
        }
        int iTryPropMgrRaw2 = tryPropMgrRaw(PROP_TURN_SIGNAL, 0);
        if (iTryPropMgrRaw2 == INVALID) {
            iTryPropMgrRaw2 = tryVehMgrRaw(PROP_TURN_SIGNAL, 0);
        }
        if (iTryPropMgrRaw2 == INVALID) {
            iTryPropMgrRaw2 = tryBinderVehRaw(PROP_TURN_SIGNAL, 0);
        }
        if (iTryPropMgrRaw2 != INVALID) {
            this.mTurnSrc = "turnProp=" + iTryPropMgrRaw2;
            if (iTryPropMgrRaw2 == 0) {
                return 0;
            }
            if (iTryPropMgrRaw2 == 1) {
                return 2;
            }
            if (iTryPropMgrRaw2 == 2) {
                return 1;
            }
            if (iTryPropMgrRaw2 == 3) {
                return 3;
            }
        }
        this.mTurnSrc = "allOff";
        return 0;
    }

    private int readTurnFromCan() {
        Object obj;
        Object obj2;
        byte[] bArrFetchCanFrame;
        synchronized (this.mLock) {
            obj = this.mVoiceMgr;
            obj2 = this.mCanMgr;
        }
        if (obj == null) {
            bArrFetchCanFrame = null;
        } else {
            try {
                bArrFetchCanFrame = fetchCanFrame(obj, CAN_BCM1);
            } catch (Throwable th) {
                return INVALID;
            }
        }
        if ((bArrFetchCanFrame == null || isAllFf(bArrFetchCanFrame)) && obj2 != null) {
            Object objInvoke = null;
            try {
                objInvoke = obj2.getClass().getMethod("getMessage", Integer.TYPE).invoke(obj2, Integer.valueOf(CAN_BCM1));
            } catch (Throwable th) {
                objInvoke = null;
            }
            if (objInvoke instanceof byte[]) {
                bArrFetchCanFrame = (byte[]) objInvoke;
            }
        }
        if (bArrFetchCanFrame == null || bArrFetchCanFrame.length < 3 || isAllFf(bArrFetchCanFrame)) {
            return INVALID;
        }
        int i = bArrFetchCanFrame[2] & 255;
        long jUptimeMillis = SystemClock.uptimeMillis();
        if (i != 0) {
            int i2 = this.mTurnSeenBits;
            if (i2 == 0 && this.mTurnCanHeld == 0) {
                this.mTurnSessionStart = jUptimeMillis;
                this.mTurnSeenBits = i;
            } else {
                this.mTurnSeenBits = i2 | i;
            }
            this.mTurnCanHoldUntil = TURN_HOLD_MS + jUptimeMillis;
            int iClassifyTurnBits = classifyTurnBits(this.mTurnSeenBits);
            int i3 = this.mTurnCanHeld;
            if (i3 == 0) {
                this.mTurnCanHeld = iClassifyTurnBits;
            } else {
                this.mTurnCanHeld = mergeTurnLatch(i3, iClassifyTurnBits);
            }
            if (jUptimeMillis - this.mTurnSessionStart < TURN_SETTLE_MS) {
                this.mTurnSrc = "can230.settle b2=" + String.format("%02X", Integer.valueOf(i));
                return 0;
            }
            this.mTurnSrc = "can230.b2=" + String.format("%02X", Integer.valueOf(i)) + " bits=" + String.format("%02X", Integer.valueOf(this.mTurnSeenBits)) + " mode=" + this.mTurnCanHeld;
            return this.mTurnCanHeld;
        }
        if (this.mTurnCanHeld != 0 && jUptimeMillis < this.mTurnCanHoldUntil) {
            this.mTurnSrc = "can230.hold mode=" + this.mTurnCanHeld;
            return this.mTurnCanHeld;
        }
        this.mTurnCanHeld = 0;
        this.mTurnSeenBits = 0;
        this.mTurnSessionStart = 0L;
        this.mTurnSrc = "can230.off";
        return 0;
    }

    private static int classifyTurnBits(int i) {
        if (i == 0) {
            return 0;
        }
        boolean z = (i & 1) != 0;
        boolean z2 = (i & DOOR_COUNT) != 0;
        if ((i & 16) != 0 && !z) {
            z2 = true;
        }
        if (z2 && z) {
            return 1;
        }
        if (z2) {
            return 3;
        }
        return 2;
    }

    private static int mergeTurnLatch(int i, int i2) {
        if (i == i2 || i2 == 0) {
            return i;
        }
        if (i == 3) {
            return 3;
        }
        if (i == 1) {
            return 1;
        }
        if (i == 2 && i2 != 1 && i2 != 3) {
            return 2;
        }
        return i2;
    }

    private static int mapFlagLight(int i) {
        if (i < 0) {
            return INVALID;
        }
        if (i == 0) {
            return 0;
        }
        if (i == 1) {
            return 1;
        }
        if (i == 2) {
            return 2;
        }
        if (i != 3) {
            return INVALID;
        }
        return 3;
    }

    private int readFlagLight() {
        IBinder iBinderBcmOrNull = bcmOrNull();
        if (iBinderBcmOrNull == null) {
            return INVALID;
        }
        Parcel parcelObtain = Parcel.obtain();
        Parcel parcelObtain2 = Parcel.obtain();
        try {
            parcelObtain.writeInterfaceToken(DESC_BCM);
            iBinderBcmOrNull.transact(TX_GET_FLAG_LIGHT, parcelObtain, parcelObtain2, 0);
            parcelObtain2.readException();
            int iNormalizeSignal = normalizeSignal(parcelObtain2.readInt());
            parcelObtain2.recycle();
            parcelObtain.recycle();
            return iNormalizeSignal;
        } catch (Throwable th) {
            try {
                synchronized (this.mLock) {
                    this.mBcm = null;
                    parcelObtain2.recycle();
                    parcelObtain.recycle();
                    return INVALID;
                }
            } catch (Throwable th2) {
                parcelObtain2.recycle();
                parcelObtain.recycle();
                throw th2;
            }
        }
    }

    private int tryPropMgrRaw(int i, int i2) {
        Object obj;
        synchronized (this.mLock) {
            obj = this.mPropMgr;
        }
        return invokeGetIntRaw(obj, i, i2);
    }

    private int tryVehMgrRaw(int i, int i2) {
        Object obj;
        synchronized (this.mLock) {
            obj = this.mVehMgr;
        }
        return invokeGetIntRaw(obj, i, i2);
    }

    private int invokeGetIntRaw(Object obj, int i, int i2) {
        if (obj == null) {
            return INVALID;
        }
        try {
            Object objInvoke = obj.getClass().getMethod("getIntProperty", Integer.TYPE, Integer.TYPE).invoke(obj, Integer.valueOf(i), Integer.valueOf(i2));
            if (objInvoke instanceof Integer) {
                return normalizeSignal(((Integer) objInvoke).intValue());
            }
        } catch (Throwable th) {
        }
        return INVALID;
    }

    private int tryBinderVehRaw(int i, int i2) {
        IBinder iBinderVehOrNull = vehOrNull();
        if (iBinderVehOrNull == null) {
            return INVALID;
        }
        Parcel parcelObtain = Parcel.obtain();
        Parcel parcelObtain2 = Parcel.obtain();
        try {
            parcelObtain.writeInterfaceToken(DESC_VEH);
            parcelObtain.writeInt(i);
            parcelObtain.writeInt(i2);
            iBinderVehOrNull.transact(2, parcelObtain, parcelObtain2, 0);
            parcelObtain2.readException();
            int iNormalizeSignal = normalizeSignal(parcelObtain2.readInt());
            parcelObtain2.recycle();
            parcelObtain.recycle();
            return iNormalizeSignal;
        } catch (Throwable th) {
            parcelObtain2.recycle();
            parcelObtain.recycle();
            return INVALID;
        }
    }

    private static int normalizeSignal(int i) {
        if (i == INVALID || i < 0 || i == -65532 || i == 65535 || i == -65535) {
            return INVALID;
        }
        return i;
    }

    private void pollGear() {
        int gear = readGear();
        if (gear == INVALID) {
            return;
        }
        if (!this.mGearSeeded) {
            this.mLastGear = gear;
            this.mGearSeeded = true;
            return;
        }
        int i = this.mLastGear;
        if (gear == i) {
            return;
        }
        this.mLastGear = gear;
        long jUptimeMillis = SystemClock.uptimeMillis();
        if (jUptimeMillis - this.mLastGearPlayAt < GEAR_DEBOUNCE_MS && gear != 1 && i != 1) {
            return;
        }
        this.mLastGearPlayAt = jUptimeMillis;
        if (i == 1 && gear != 1) {
            stopReverse();
        }
        if (gear == 1) {
            startReverse();
            return;
        }
        String strGearEvent = gearEvent(gear);
        if (strGearEvent != null) {
            playEvent(strGearEvent, false);
        }
    }

    private static String gearEvent(int i) {
        switch (i) {
            case VOL_LEVEL_MID /* 0 */:
                return VoiceEvents.GEAR_P;
            case VOL_LEVEL_MID_HIGH /* 1 */:
            default:
                return null;
            case VOL_LEVEL_MAX /* 2 */:
                return VoiceEvents.GEAR_N;
            case 3:
                return VoiceEvents.GEAR_D;
        }
    }

    private void pollDoors() {
        int i;
        boolean z;
        int door;
        synchronized (this.mLock) {
            z = (this.mVoiceMgr == null && this.mCanMgr == null) ? false : true;
        }
        if (z && !this.mVoiceReadySeen) {
            this.mVoiceReadySeen = true;
            Arrays.fill(this.mDoorSeeded, false);
            Arrays.fill(this.mLastDoor, INVALID);
            Log.i(TAG, "door re-seed: voice/can ready");
            ensureCanDoorListeners();
        }
        long jUptimeMillis = SystemClock.uptimeMillis();
        if (jUptimeMillis - this.mLastDoorDumpAt >= 2000) {
            this.mLastDoorDumpAt = jUptimeMillis;
            dumpDoors();
        }
        for (i = 0; i < DOOR_COUNT; i++) {
            if (doorOn(i) && (door = readDoor(i)) != INVALID) {
                boolean[] zArr = this.mDoorSeeded;
                if (!zArr[i]) {
                    this.mLastDoor[i] = door;
                    zArr[i] = true;
                    Log.i(TAG, "door seed role=" + i + " v=" + door + " src=" + this.mDoorSrcName);
                } else {
                    int[] iArr = this.mLastDoor;
                    int i2 = iArr[i];
                    if (door != i2) {
                        iArr[i] = door;
                        handleDoorEdge(i, i2, door);
                        Log.i(TAG, "door edge role=" + i + " " + i2 + "->" + door + " src=" + this.mDoorSrcName);
                    }
                }
            }
        }
    }

    private void dumpDoors() {
        StringBuilder sbAppend = new StringBuilder("door dump src=").append(this.mDoorSrcName).append(" car=").append(this.mCarObj != null).append(" voice=").append(this.mVoiceMgr != null).append(" canMgr=").append(this.mCanMgr != null);
        for (int i = 0; i < DOOR_COUNT; i++) {
            sbAppend.append(" D").append(i).append("={can=").append(tryCanDoor(i)).append('}');
        }
        Log.i(TAG, sbAppend.toString());
    }

    private void handleDoorEdge(int i, int i2, int i3) {
        boolean z = i2 > 0;
        boolean z2 = i3 > 0;
        if (z == z2) {
            return;
        }
        if (z2) {
            playEvent(DOOR_OPEN_EVENT[i], false);
        } else if (!doorOpenOnly()) {
            playEvent(DOOR_CLOSE_EVENT[i], false);
        }
    }

    private int readDoor(int i) {
        int iTryCanDoor = tryCanDoor(i);
        if (iTryCanDoor != INVALID) {
            this.mDoorSrcName = "canDoorSt";
            return iTryCanDoor;
        }
        this.mDoorSrcName = "none";
        return INVALID;
    }

    private int tryCanDoor(int i) {
        byte[] bArr;
        Object obj;
        Object obj2;
        byte[] bArrFetchCanFrame;
        int i2 = DOOR_CAN_SIG[i];
        int iCanStartBit = canStartBit(i2);
        int iCanLength = canLength(i2);
        synchronized (this.mLock) {
            bArr = this.mDoorFrames[i];
        }
        int iExtractCanSignal = extractCanSignal(bArr, iCanStartBit, iCanLength);
        synchronized (this.mLock) {
            obj = this.mVoiceMgr;
            obj2 = this.mCanMgr;
        }
        if (obj != null) {
            try {
                bArrFetchCanFrame = fetchCanFrame(obj, canMsgId(i2));
            } catch (Throwable th) {
                bArrFetchCanFrame = null;
            }
        } else {
            bArrFetchCanFrame = null;
        }
        if ((bArrFetchCanFrame == null || isAllFf(bArrFetchCanFrame)) && obj2 != null) {
            try {
                Object objInvoke = obj2.getClass().getMethod("getMessage", Integer.TYPE).invoke(obj2, Integer.valueOf(canMsgId(i2)));
                if (objInvoke instanceof byte[]) {
                    bArrFetchCanFrame = (byte[]) objInvoke;
                }
            } catch (Throwable th2) {
            }
        }
        int iExtractCanSignal2 = extractCanSignal(bArrFetchCanFrame, iCanStartBit, iCanLength);
        if (iExtractCanSignal2 != INVALID) {
            synchronized (this.mLock) {
                this.mDoorFrames[i] = bArrFetchCanFrame;
            }
            return iExtractCanSignal2;
        }
        return iExtractCanSignal;
    }

    private static boolean isAllFf(byte[] bArr) {
        if (bArr == null || bArr.length == 0) {
            return true;
        }
        for (byte b : bArr) {
            if (b != -1) {
                return false;
            }
        }
        return true;
    }

    private int tryVehMgr(int i, int i2) {
        Object obj;
        synchronized (this.mLock) {
            obj = this.mVehMgr;
        }
        return invokeGetInt(obj, i, i2);
    }

    private int tryPropMgr(int i, int i2) {
        Object obj;
        synchronized (this.mLock) {
            obj = this.mPropMgr;
        }
        return invokeGetInt(obj, i, i2);
    }

    private int tryCabin(int i, int i2) {
        Object obj;
        synchronized (this.mLock) {
            obj = this.mCabinMgr;
        }
        return invokeGetInt(obj, i, i2);
    }

    private int invokeGetInt(Object obj, int i, int i2) {
        if (obj == null) {
            return INVALID;
        }
        try {
            Object objInvoke = obj.getClass().getMethod("getIntProperty", Integer.TYPE, Integer.TYPE).invoke(obj, Integer.valueOf(i), Integer.valueOf(i2));
            if (objInvoke instanceof Integer) {
                return normalizeDoor(((Integer) objInvoke).intValue());
            }
        } catch (Throwable th) {
        }
        return INVALID;
    }

    private int tryBinderVeh(int i, int i2) {
        IBinder iBinderVehOrNull = vehOrNull();
        if (iBinderVehOrNull == null) {
            return INVALID;
        }
        Parcel parcelObtain = Parcel.obtain();
        Parcel parcelObtain2 = Parcel.obtain();
        try {
            parcelObtain.writeInterfaceToken(DESC_VEH);
            parcelObtain.writeInt(i);
            parcelObtain.writeInt(i2);
            iBinderVehOrNull.transact(2, parcelObtain, parcelObtain2, 0);
            parcelObtain2.readException();
            int iNormalizeDoor = normalizeDoor(parcelObtain2.readInt());
            parcelObtain2.recycle();
            parcelObtain.recycle();
            return iNormalizeDoor;
        } catch (Throwable th) {
            try {
                synchronized (this.mLock) {
                    this.mVeh = null;
                    parcelObtain2.recycle();
                    parcelObtain.recycle();
                    return INVALID;
                }
            } catch (Throwable th2) {
                parcelObtain2.recycle();
                parcelObtain.recycle();
                throw th2;
            }
        }
    }

    private static int normalizeDoor(int i) {
        if (i == INVALID || i == -65532 || i == -1 || i == 65535 || i == -65535) {
            return INVALID;
        }
        return i;
    }

    /* JADX INFO: renamed from: com.carassistant.voice.VehicleVoiceEngine$7, reason: invalid class name */
    class AnonymousClass7 implements Runnable {
        final /* synthetic */ String val$eventKey;
        final /* synthetic */ boolean val$loop;

        AnonymousClass7(boolean z, String str) {
            this.val$loop = z;
            this.val$eventKey = str;
        }

        @Override // java.lang.Runnable
        public void run() {
            synchronized (VehicleVoiceEngine.this.mAudioLock) {
                boolean z = true;
                if (this.val$loop) {
                    if (VehicleVoiceEngine.this.mReverse != null) {
                        return;
                    }
                    VehicleVoiceEngine vehicleVoiceEngine = VehicleVoiceEngine.this;
                    vehicleVoiceEngine.mReverseBoostHeld = VehicleVoiceEngine.beginVolumeBoost(vehicleVoiceEngine.mCtx);
                    VehicleVoiceEngine vehicleVoiceEngine2 = VehicleVoiceEngine.this;
                    vehicleVoiceEngine2.mReverse = VoiceLibrary.playEvent(vehicleVoiceEngine2.mCtx, this.val$eventKey, true);
                    if (VehicleVoiceEngine.this.mReverse == null && VehicleVoiceEngine.this.mReverseBoostHeld) {
                        VehicleVoiceEngine.endVolumeBoost();
                        VehicleVoiceEngine.this.mReverseBoostHeld = false;
                    }
                    StringBuilder sbAppend = new StringBuilder().append("loop ").append(this.val$eventKey).append(" ok=");
                    if (VehicleVoiceEngine.this.mReverse == null) {
                        z = false;
                    }
                    Log.i(VehicleVoiceEngine.TAG, sbAppend.append(z).toString());
                } else {
                    VehicleVoiceEngine.this.stopOneShotLocked();
                    boolean zBeginVolumeBoost = VehicleVoiceEngine.beginVolumeBoost(VehicleVoiceEngine.this.mCtx);
                    VehicleVoiceEngine.this.mOneShotBoostHeld = zBeginVolumeBoost;
                    VehicleVoiceEngine vehicleVoiceEngine3 = VehicleVoiceEngine.this;
                    vehicleVoiceEngine3.mOneShot = VoiceLibrary.playEvent(vehicleVoiceEngine3.mCtx, this.val$eventKey, false);
                    if (VehicleVoiceEngine.this.mOneShot != null) {
                        VehicleVoiceEngine.this.mOneShot.setOnEnded(new Runnable() { // from class: com.carassistant.voice.VehicleVoiceEngine.7.1
                            @Override // java.lang.Runnable
                            public void run() {
                                VehicleVoiceEngine.this.mMain.post(new Runnable() { // from class: com.carassistant.voice.VehicleVoiceEngine.7.1.1
                                    @Override // java.lang.Runnable
                                    public void run() {
                                        synchronized (VehicleVoiceEngine.this.mAudioLock) {
                                            if (VehicleVoiceEngine.this.mOneShotBoostHeld) {
                                                VehicleVoiceEngine.endVolumeBoost();
                                                VehicleVoiceEngine.this.mOneShotBoostHeld = false;
                                            }
                                            VehicleVoiceEngine.this.mOneShot = null;
                                        }
                                    }
                                });
                            }
                        });
                    } else if (zBeginVolumeBoost) {
                        VehicleVoiceEngine.endVolumeBoost();
                        VehicleVoiceEngine.this.mOneShotBoostHeld = false;
                    }
                    StringBuilder sbAppend2 = new StringBuilder().append("oneshot ").append(this.val$eventKey).append(" ok=");
                    if (VehicleVoiceEngine.this.mOneShot == null) {
                        z = false;
                    }
                    Log.i(VehicleVoiceEngine.TAG, sbAppend2.append(z).toString());
                }
            }
        }
    }

    private void playEvent(String str, boolean z) {
        this.mMain.post(new AnonymousClass7(z, str));
    }

    private void startReverse() {
        playEvent(VoiceEvents.GEAR_R, true);
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void stopReverse() {
        this.mMain.post(new Runnable() { // from class: com.carassistant.voice.VehicleVoiceEngine.8
            @Override // java.lang.Runnable
            public void run() {
                synchronized (VehicleVoiceEngine.this.mAudioLock) {
                    if (VehicleVoiceEngine.this.mReverse != null) {
                        VehicleVoiceEngine.this.mReverse.stop();
                        VehicleVoiceEngine.this.mReverse = null;
                        Log.i(VehicleVoiceEngine.TAG, "reverse off");
                    }
                    if (VehicleVoiceEngine.this.mReverseBoostHeld) {
                        VehicleVoiceEngine.endVolumeBoost();
                        VehicleVoiceEngine.this.mReverseBoostHeld = false;
                    }
                }
            }
        });
    }

    private void stopOneShot() {
        this.mMain.post(new Runnable() { // from class: com.carassistant.voice.VehicleVoiceEngine.9
            @Override // java.lang.Runnable
            public void run() {
                synchronized (VehicleVoiceEngine.this.mAudioLock) {
                    VehicleVoiceEngine.this.stopOneShotLocked();
                }
            }
        });
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void stopOneShotLocked() {
        PlayHandle playHandle = this.mOneShot;
        if (playHandle != null) {
            playHandle.setOnEnded(null);
            this.mOneShot.stop();
            this.mOneShot = null;
        }
        if (this.mOneShotBoostHeld) {
            endVolumeBoost();
            this.mOneShotBoostHeld = false;
        }
    }

    private int rawId(String str) {
        return this.mCtx.getResources().getIdentifier(str, VoiceClip.KIND_RAW, this.mCtx.getPackageName());
    }

    private void ensureBind() {
        synchronized (this.mLock) {
            if (!this.mBound && !this.mBinding) {
                this.mBinding = true;
                try {
                    Intent intent = new Intent(ADAPTER_CLS);
                    intent.setPackage(ADAPTER_PKG);
                    boolean zBindQuiet = bindQuiet(intent, this.mConn);
                    if (!zBindQuiet) {
                        Intent intent2 = new Intent();
                        intent2.setClassName(ADAPTER_PKG, ADAPTER_CLS);
                        zBindQuiet = bindQuiet(intent2, this.mConn);
                    }
                    if (!zBindQuiet) {
                        synchronized (this.mLock) {
                            try {
                                this.mBinding = false;
                            } catch (Throwable th) {
                                throw th;
                            }
                        }
                    }
                } catch (Throwable th2) {
                    synchronized (this.mLock) {
                        this.mBinding = false;
                        Log.e(TAG, "bind", th2);
                    }
                }
            }
        }
    }

    private void ensureExtBind() {
        synchronized (this.mLock) {
            if (!this.mExtBound && !this.mExtBinding) {
                this.mExtBinding = true;
                try {
                    Intent intent = new Intent("android.car.IExternalCar");
                    intent.setPackage(EXT_PKG);
                    boolean zBindQuiet = bindQuiet(intent, this.mExtConn);
                    if (!zBindQuiet) {
                        Intent intent2 = new Intent();
                        intent2.setClassName(EXT_PKG, "com.android.externalcar.ExternalCarService");
                        zBindQuiet = bindQuiet(intent2, this.mExtConn);
                    }
                    if (!zBindQuiet) {
                        synchronized (this.mLock) {
                            this.mExtBinding = false;
                        }
                        Log.w(TAG, "externalcar bind failed");
                    }
                } catch (Throwable th) {
                    synchronized (this.mLock) {
                        this.mExtBinding = false;
                        Log.e(TAG, "ext bind", th);
                    }
                }
            }
        }
    }

    private void ensureCarReflect() {
        if (this.mCarObj != null || this.mCarConnecting) {
            return;
        }
        this.mCarConnecting = true;
        try {
            Class<?> cls = Class.forName("android.car.Car");
            Object objInvoke = cls.getMethod("createCar", Context.class, ServiceConnection.class).invoke(null, this.mCtx, this.mCarConn);
            if (objInvoke == null) {
                Log.w(TAG, "Car.createCar returned null (no automotive feature?)");
                this.mCarConnecting = false;
                return;
            }
            this.mCarObj = objInvoke;
            cls.getMethod("connect", new Class[0]).invoke(objInvoke, new Object[0]);
            Log.i(TAG, "Car.createCar+connect ok");
            refreshCarManagers();
            ensureVoicePaths();
        } catch (Throwable th) {
            Log.e(TAG, "Car reflect failed", th);
            this.mCarConnecting = false;
        }
    }

    private void ensureVoicePaths() {
        Object objInvoke;
        long jUptimeMillis = SystemClock.uptimeMillis();
        synchronized (this.mLock) {
            if (this.mVoiceMgr == null || this.mCanMgr == null) {
                if (jUptimeMillis - this.mLastVoiceRetryAt < 1500) {
                    return;
                }
                this.mLastVoiceRetryAt = jUptimeMillis;
                try {
                    Object objInvoke2 = Class.forName("android.car.Car").getMethod("getExternalCar", new Class[0]).invoke(null, new Object[0]);
                    if (objInvoke2 != null && (objInvoke = objInvoke2.getClass().getMethod("getCarManager", String.class).invoke(objInvoke2, CAR_VOICE)) != null) {
                        synchronized (this.mLock) {
                            this.mVoiceMgr = objInvoke;
                        }
                        Log.i(TAG, "voice via ExternalCar ok");
                    }
                } catch (Throwable th) {
                    Log.w(TAG, "ExternalCar voice", th);
                }
                try {
                    Object objInvoke3 = Class.forName("faw.car.manager.CarCANManager").getMethod("getInstance", new Class[0]).invoke(null, new Object[0]);
                    if (objInvoke3 != null) {
                        synchronized (this.mLock) {
                            this.mCanMgr = objInvoke3;
                        }
                        Log.i(TAG, "CarCANManager ok");
                    }
                } catch (Throwable th2) {
                    Log.w(TAG, "CarCANManager", th2);
                }
                ensureCanDoorListeners();
            }
        }
    }

    private void ensureCanDoorListeners() {
        synchronized (this.mLock) {
            if (this.mCanListenersRegistered) {
                return;
            }
            Object obj = this.mCanMgr;
            Object obj2 = this.mVoiceMgr;
            if (obj == null && obj2 == null) {
                return;
            }
            try {
                Class<?> cls = Class.forName("faw.car.manager.CarCANManager$OnCanListener");
                Object objNewProxyInstance = Proxy.newProxyInstance(cls.getClassLoader(), new Class[]{cls}, new InvocationHandler() { // from class: com.carassistant.voice.VehicleVoiceEngine.10
                    @Override // java.lang.reflect.InvocationHandler
                    public Object invoke(Object obj3, Method method, Object[] objArr) {
                        String name = method.getName();
                        if ("onCallback".equals(name) && objArr != null && objArr.length >= 2) {
                            Object obj4 = objArr[0];
                            if ((obj4 instanceof Integer) && (objArr[1] instanceof byte[])) {
                                VehicleVoiceEngine.this.onCanDoorFrame(((Integer) obj4).intValue(), (byte[]) objArr[1]);
                                return null;
                            }
                        }
                        if ("hashCode".equals(name)) {
                            return Integer.valueOf(System.identityHashCode(obj3));
                        }
                        if ("equals".equals(name)) {
                            return Boolean.valueOf(obj3 == objArr[0]);
                        }
                        if ("toString".equals(name)) {
                            return "DoorCanListener";
                        }
                        return null;
                    }
                });
                if (obj != null) {
                    Method method = obj.getClass().getMethod("registerListener", Integer.TYPE, cls);
                    for (int i = 0; i < DOOR_COUNT; i++) {
                        method.invoke(obj, Integer.valueOf(canMsgId(DOOR_CAN_SIG[i])), objNewProxyInstance);
                    }
                } else if (obj2 != null) {
                    Method method2 = obj2.getClass().getMethod("registerListener", Integer.TYPE, Integer.TYPE, cls);
                    for (int i2 = 0; i2 < DOOR_COUNT; i2++) {
                        method2.invoke(obj2, 3, Integer.valueOf(canMsgId(DOOR_CAN_SIG[i2])), objNewProxyInstance);
                    }
                }
                synchronized (this.mLock) {
                    this.mCanListenersRegistered = true;
                }
                Log.i(TAG, "CAN door listeners registered");
            } catch (Throwable th) {
                Log.w(TAG, "register door listeners", th);
            }
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void onCanDoorFrame(int i, byte[] bArr) {
        final int iExtractCanSignal;
        if (bArr == null) {
            return;
        }
        for (int i2 = 0; i2 < DOOR_COUNT; i2++) {
            int[] iArr = DOOR_CAN_SIG;
            if (canMsgId(iArr[i2]) == i) {
                synchronized (this.mLock) {
                    this.mDoorFrames[i2] = bArr;
                }
                if (!this.mAlive || !enabled() || !doorOn(i2) || (iExtractCanSignal = extractCanSignal(bArr, canStartBit(iArr[i2]), canLength(iArr[i2]))) == INVALID) {
                    return;
                }
                final int iFinal = i2;
                this.mMain.post(new Runnable() { // from class: com.carassistant.voice.VehicleVoiceEngine.11
                    @Override // java.lang.Runnable
                    public void run() {
                        VehicleVoiceEngine.this.applyDoorSample(iFinal, iExtractCanSignal, "canPush");
                    }
                });
                return;
            }
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void applyDoorSample(int i, int i2, String str) {
        this.mDoorSrcName = str;
        boolean[] zArr = this.mDoorSeeded;
        if (!zArr[i]) {
            this.mLastDoor[i] = i2;
            zArr[i] = true;
            Log.i(TAG, "door seed role=" + i + " v=" + i2 + " src=" + str);
            return;
        }
        int[] iArr = this.mLastDoor;
        int i3 = iArr[i];
        if (i2 == i3) {
            return;
        }
        iArr[i] = i2;
        handleDoorEdge(i, i3, i2);
        Log.i(TAG, "door edge role=" + i + " " + i3 + "->" + i2 + " src=" + str);
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void refreshCarManagers() {
        Object obj = this.mCarObj;
        if (obj != null) {
            try {
                Method method = obj.getClass().getMethod("getCarManager", String.class);
                Object objSafeGetMgr = safeGetMgr(method, obj, CAR_VEHICLE_STATE);
                Object objSafeGetMgr2 = safeGetMgr(method, obj, "property");
                Object objSafeGetMgr3 = safeGetMgr(method, obj, "cabin");
                Object objSafeGetMgr4 = safeGetMgr(method, obj, CAR_VOICE);
                synchronized (this.mLock) {
                    if (objSafeGetMgr != null) {
                        try {
                            this.mVehMgr = objSafeGetMgr;
                        } catch (Throwable th) {
                            throw th;
                        }
                    }
                    if (objSafeGetMgr2 != null) {
                        this.mPropMgr = objSafeGetMgr2;
                    }
                    if (objSafeGetMgr3 != null) {
                        this.mCabinMgr = objSafeGetMgr3;
                    }
                    if (objSafeGetMgr4 != null) {
                        this.mVoiceMgr = objSafeGetMgr4;
                    }
                }
            } catch (Throwable th2) {
                Log.w(TAG, "refreshCarManagers", th2);
            }
        }
        ensureVoicePaths();
    }

    private static Object safeGetMgr(Method method, Object obj, String str) {
        try {
            return method.invoke(obj, str);
        } catch (Throwable th) {
            return null;
        }
    }

    private boolean bindQuiet(Intent intent, ServiceConnection serviceConnection) {
        try {
            return this.mCtx.bindService(intent, serviceConnection, 1);
        } catch (Throwable th) {
            return false;
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
        } catch (Throwable th) {
            Log.e(TAG, "resolve state", th);
        }
        resolveBcm();
    }

    private void resolveBcm() {
        synchronized (this.mLock) {
            IBinder iBinder = this.mRemote;
            if (this.mBcm != null) {
                return;
            }
            if (iBinder == null) {
                return;
            }
            try {
                IBinder iBinderQueryBinder = queryBinder(iBinder, BINDER_BCM);
                synchronized (this.mLock) {
                    this.mBcm = iBinderQueryBinder;
                }
                Log.i(TAG, "bcm binder=" + (iBinderQueryBinder != null));
            } catch (Throwable th) {
                Log.w(TAG, "resolve bcm", th);
            }
        }
    }

    private IBinder bcmOrNull() {
        IBinder iBinder;
        synchronized (this.mLock) {
            iBinder = this.mBcm;
        }
        if (iBinder == null) {
            resolveBcm();
            synchronized (this.mLock) {
                iBinder = this.mBcm;
            }
        }
        return iBinder;
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void resolveVeh() {
        IBinder iBinder;
        synchronized (this.mLock) {
            iBinder = this.mExt;
        }
        if (iBinder == null) {
            return;
        }
        try {
            IBinder extService = getExtService(iBinder, CAR_VEHICLE_STATE);
            synchronized (this.mLock) {
                this.mVeh = extService;
            }
            Log.i(TAG, "veh binder=" + (extService != null));
        } catch (Throwable th) {
            Log.e(TAG, "resolve veh", th);
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

    private IBinder getExtService(IBinder iBinder, String str) throws RemoteException {
        Parcel parcelObtain = Parcel.obtain();
        Parcel parcelObtain2 = Parcel.obtain();
        try {
            parcelObtain.writeInterfaceToken("android.car.IExternalCar");
            parcelObtain.writeString(str);
            iBinder.transact(2, parcelObtain, parcelObtain2, 0);
            parcelObtain2.readException();
            return parcelObtain2.readStrongBinder();
        } finally {
            parcelObtain2.recycle();
            parcelObtain.recycle();
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

    private IBinder vehOrNull() {
        IBinder iBinder;
        synchronized (this.mLock) {
            iBinder = this.mVeh;
        }
        if (iBinder == null) {
            resolveVeh();
            synchronized (this.mLock) {
                iBinder = this.mVeh;
            }
        }
        return iBinder;
    }

    private int readGear() {
        int iTxStateGet = txStateGet(TX_GET_GEAR);
        if (iTxStateGet == INVALID || iTxStateGet == -65532) {
            return INVALID;
        }
        return iTxStateGet;
    }

    public static String diagnoseTurn(Context context) {
        Object objInvoke;
        if (context == null) {
            return "ctx空";
        }
        VehicleVoiceEngine vehicleVoiceEngine = new VehicleVoiceEngine(context);
        StringBuilder sb = new StringBuilder();
        sb.append("转向灯诊断（拨杆/双闪时点，看哪路会变）\n");
        try {
            vehicleVoiceEngine.ensureBind();
            vehicleVoiceEngine.ensureExtBind();
            vehicleVoiceEngine.ensureCarReflect();
            vehicleVoiceEngine.ensureVoicePaths();
            Thread.sleep(1000L);
            vehicleVoiceEngine.resolveState();
            vehicleVoiceEngine.resolveBcm();
            vehicleVoiceEngine.refreshCarManagers();
            synchronized (vehicleVoiceEngine.mLock) {
                objInvoke = vehicleVoiceEngine.mVoiceMgr;
            }
            boolean z = true;
            if (objInvoke == null) {
                try {
                    Object objInvoke2 = Class.forName("android.car.Car").getMethod("getExternalCar", new Class[0]).invoke(null, new Object[0]);
                    if (objInvoke2 != null) {
                        objInvoke = objInvoke2.getClass().getMethod("getCarManager", String.class).invoke(objInvoke2, CAR_VOICE);
                    }
                } catch (Throwable th) {
                }
            }
            StringBuilder sbAppend = sb.append("BCM binder=").append(vehicleVoiceEngine.bcmOrNull() != null).append(" prop=").append(vehicleVoiceEngine.mPropMgr != null).append(" veh=");
            if (vehicleVoiceEngine.mVehMgr == null) {
                z = false;
            }
            sbAppend.append(z).append('\n');
            LinkedHashSet linkedHashSet = new LinkedHashSet();
            for (int i = 0; i < 10; i++) {
                int flagLight = vehicleVoiceEngine.readFlagLight();
                int iTryPropMgrRaw = vehicleVoiceEngine.tryPropMgrRaw(PROP_TURN_SIGNAL, 0);
                if (iTryPropMgrRaw == INVALID) {
                    iTryPropMgrRaw = vehicleVoiceEngine.tryVehMgrRaw(PROP_TURN_SIGNAL, 0);
                }
                if (iTryPropMgrRaw == INVALID) {
                    iTryPropMgrRaw = vehicleVoiceEngine.tryBinderVehRaw(PROP_TURN_SIGNAL, 0);
                }
                int iTryPropMgrRaw2 = vehicleVoiceEngine.tryPropMgrRaw(PROP_HAZARD_STATE, 0);
                if (iTryPropMgrRaw2 == INVALID) {
                    iTryPropMgrRaw2 = vehicleVoiceEngine.tryVehMgrRaw(PROP_HAZARD_STATE, 0);
                }
                if (iTryPropMgrRaw2 == INVALID) {
                    iTryPropMgrRaw2 = vehicleVoiceEngine.tryBinderVehRaw(PROP_HAZARD_STATE, 0);
                }
                int turnMode = vehicleVoiceEngine.readTurnMode();
                String strFrameToHex = "?";
                if (objInvoke != null) {
                    try {
                        strFrameToHex = frameToHex(fetchCanFrame(objInvoke, CAN_BCM1));
                    } catch (Throwable th2) {
                        strFrameToHex = "err";
                    }
                }
                String str = "fl=" + flagLight + " turnProp=" + iTryPropMgrRaw + " hazard=" + iTryPropMgrRaw2 + " mode=" + modeName(turnMode) + " can230=" + strFrameToHex + " src=" + vehicleVoiceEngine.mTurnSrc;
                if (linkedHashSet.add(str)) {
                    sb.append('[').append(i).append("] ").append(str).append('\n');
                }
                Thread.sleep(TURN_SETTLE_MS);
            }
            sb.append("\n说明: 实车 FlagLight=-2 无效；看 can230 第3字节(b2)闪不闪。");
            sb.append("\n请分别拍：关 / 左打灯 / 右打灯 / 双闪 各一张。");
            Log.i(TAG, "diagnoseTurn " + ((Object) sb));
            String strTrim = sb.toString().trim();
            vehicleVoiceEngine.destroy();
            return strTrim;
        } catch (Throwable th3) {
            try {
                Log.e(TAG, "diagnoseTurn", th3);
                return "转向诊断失败: " + th3.getClass().getSimpleName() + " " + th3.getMessage();
            } finally {
                vehicleVoiceEngine.destroy();
            }
        }
    }

    private static String modeName(int i) {
        switch (i) {
            case VOL_LEVEL_MID /* 0 */:
                return "关";
            case VOL_LEVEL_MID_HIGH /* 1 */:
                return "左";
            case VOL_LEVEL_MAX /* 2 */:
                return "右";
            case 3:
                return "双闪";
            default:
                return "无效";
        }
    }

    private static String frameToHex(byte[] bArr) {
        if (bArr == null) {
            return "null";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < bArr.length && i < 8; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(String.format("%02X", Integer.valueOf(bArr[i] & 255)));
        }
        return sb.toString();
    }

    public static String diagnoseGear(Context context) {
        String str;
        if (context == null) {
            return "ctx空";
        }
        VehicleVoiceEngine vehicleVoiceEngine = new VehicleVoiceEngine(context);
        try {
            vehicleVoiceEngine.ensureBind();
            Thread.sleep(900L);
            vehicleVoiceEngine.resolveState();
            int gear = vehicleVoiceEngine.readGear();
            if (gear == INVALID) {
                String str2 = "档位=无效（Qisi 未连上？） state=" + (vehicleVoiceEngine.stateOrNull() != null);
                vehicleVoiceEngine.destroy();
                return str2;
            }
            switch (gear) {
                case VOL_LEVEL_MID /* 0 */:
                    str = "P";
                    break;
                case VOL_LEVEL_MID_HIGH /* 1 */:
                    str = "R";
                    break;
                case VOL_LEVEL_MAX /* 2 */:
                    str = "N";
                    break;
                case 3:
                    str = "D";
                    break;
                default:
                    str = "?";
                    break;
            }
            String str3 = "档位=" + gear + " (" + str + ")  P=0 R=1 N=2 D=3";
            vehicleVoiceEngine.destroy();
            return str3;
        } catch (Throwable th) {
            try {
                return "档位诊断失败: " + th.getClass().getSimpleName() + " " + th.getMessage();
            } finally {
                vehicleVoiceEngine.destroy();
            }
        }
    }

    private int txStateGet(int i) {
        IBinder iBinderStateOrNull = stateOrNull();
        if (iBinderStateOrNull == null) {
            return INVALID;
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
                    return INVALID;
                }
            } catch (Throwable th2) {
                parcelObtain2.recycle();
                parcelObtain.recycle();
                throw th2;
            }
        }
    }
}
