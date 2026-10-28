package com.carassistant.ambient;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ArgbEvaluator;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.Property;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import java.util.ArrayList;

/* JADX INFO: loaded from: classes.dex */
public class MainActivity extends Activity {
    private Button mA11yBtn;
    private TextView mAboutBtn;
    private ObjectAnimator mAboutPulse;
    private int mAccentDefault;
    private ObjectAnimator mBingxinPulse;
    private View mBrandLine;
    private Button mCustomAddBtn;
    private TextView mCustomColorLabel;
    private Button mCustomColorMinusBtn;
    private Button mCustomColorPlusBtn;
    private SeekBar mCustomColorSeek;
    private View mCustomColorSwatch;
    private View mCustomIntervalBlock;
    private TextView mCustomIntervalLabel;
    private SeekBar mCustomIntervalSeek;
    private LinearLayout mCustomPaletteRow;
    private View mCustomPanel;
    private Button mCustomPreviewBtn;
    private Button mCustomResetBtn;
    private boolean mCustomUiReady;
    private CheckBox mEnable;
    private ObjectAnimator mGlowBreath;
    private View mHeroGlow;
    private View mLiveDot;
    private TextView mLiveLabel;
    private ObjectAnimator mLivePulse;
    private View mMasterRow;
    private View mModeCard;
    private TextView mModeCode;
    private View mModeFlashBar;
    private View mModeFxGlow;
    private boolean mModeFxReady;
    private RadioGroup mModeGroup;
    private Button mNotifBtn;
    private View mPermBlock;
    private AnimatorSet mRunningFx;
    private int mSelectedBg;
    private SharedPreferences mSp;
    private View mSpeedFill;
    private View mSpeedTrack;
    private TextView mStatus;
    private int mStatusDefault;

    @Override // android.app.Activity
    protected void onCreate(Bundle bundle) {
        super.onCreate(bundle);
        setContentView(Res.layout(this, "qg_activity_main"));
        this.mSp = PreferenceManager.getDefaultSharedPreferences(this);
        this.mSelectedBg = colorRes("qg_brand_soft", 1155603758);
        this.mAccentDefault = colorRes("qg_brand", -2024146);
        this.mStatusDefault = colorRes("qg_accent", -20336);
        this.mEnable = (CheckBox) findViewById(Res.id(this, "enableCB"));
        this.mStatus = (TextView) findViewById(Res.id(this, "statusText"));
        this.mLiveLabel = (TextView) findViewById(Res.id(this, "liveLabel"));
        this.mAboutBtn = (TextView) findViewById(Res.id(this, "aboutBtn"));
        this.mModeCode = (TextView) findViewById(Res.id(this, "modeCodeText"));
        this.mLiveDot = findViewById(Res.id(this, "liveDot"));
        this.mSpeedFill = findViewById(Res.id(this, "speedFill"));
        this.mSpeedTrack = findViewById(Res.id(this, "speedTrack"));
        this.mHeroGlow = findViewById(Res.id(this, "heroGlow"));
        this.mMasterRow = findViewById(Res.id(this, "masterRow"));
        this.mModeGroup = (RadioGroup) findViewById(Res.id(this, "modeGroup"));
        this.mPermBlock = findViewById(Res.id(this, "a11yBlock"));
        this.mA11yBtn = (Button) findViewById(Res.id(this, "a11yBtn"));
        this.mNotifBtn = (Button) findViewById(Res.id(this, "notifBtn"));
        this.mModeFlashBar = findViewById(Res.id(this, "modeFlashBar"));
        this.mModeFxGlow = findViewById(Res.id(this, "modeFxGlow"));
        this.mBrandLine = findViewById(Res.id(this, "brandLine"));
        this.mModeCard = findViewById(Res.id(this, "modeCard"));
        this.mCustomPanel = findViewById(Res.id(this, "customPanel"));
        this.mCustomPaletteRow = (LinearLayout) findViewById(Res.id(this, "customPaletteRow"));
        this.mCustomColorSeek = (SeekBar) findViewById(Res.id(this, "customColorSeek"));
        this.mCustomColorLabel = (TextView) findViewById(Res.id(this, "customColorLabel"));
        this.mCustomColorSwatch = findViewById(Res.id(this, "customColorSwatch"));
        this.mCustomColorMinusBtn = (Button) findViewById(Res.id(this, "customColorMinusBtn"));
        this.mCustomColorPlusBtn = (Button) findViewById(Res.id(this, "customColorPlusBtn"));
        this.mCustomAddBtn = (Button) findViewById(Res.id(this, "customAddBtn"));
        this.mCustomIntervalBlock = findViewById(Res.id(this, "customIntervalBlock"));
        this.mCustomIntervalSeek = (SeekBar) findViewById(Res.id(this, "customIntervalSeek"));
        this.mCustomIntervalLabel = (TextView) findViewById(Res.id(this, "customIntervalLabel"));
        this.mCustomPreviewBtn = (Button) findViewById(Res.id(this, "customPreviewBtn"));
        this.mCustomResetBtn = (Button) findViewById(Res.id(this, "customResetBtn"));
        int i = this.mSp.getInt(AmbientRhythmHelper.PREF_MODE, 0);
        this.mEnable.setChecked(this.mSp.getBoolean(AmbientRhythmHelper.PREF_ENABLED, false));
        applyModeToRadios(i);
        refreshModeHighlights(i, false);
        updateModeCode(i, false);
        updateSpeedBar(i, false);
        wireCustomPanel();
        refreshCustomPanel(i);
        this.mEnable.setOnClickListener(new View.OnClickListener() { // from class: com.carassistant.ambient.MainActivity.1
            @Override // android.view.View.OnClickListener
            public void onClick(View view) {
                boolean zIsChecked = MainActivity.this.mEnable.isChecked();
                MainActivity.this.mSp.edit().putBoolean(AmbientRhythmHelper.PREF_ENABLED, zIsChecked).apply();
                Toast.makeText(MainActivity.this, zIsChecked ? "氛围灯律动已开" : "氛围灯律动已关", 0).show();
                MainActivity.this.refreshStatus();
                MainActivity.this.playIgnitionFx(zIsChecked);
            }
        });
        TextView textView = this.mAboutBtn;
        if (textView != null) {
            textView.setOnClickListener(new View.OnClickListener() { // from class: com.carassistant.ambient.MainActivity.2
                @Override // android.view.View.OnClickListener
                public void onClick(View view) {
                    MainActivity.this.startActivity(new Intent(MainActivity.this, (Class<?>) AboutActivity.class));
                }
            });
        }
        wireModeListener();
        this.mModeFxReady = true;
        startAmbientLoops();
        Button button = this.mA11yBtn;
        if (button != null) {
            button.setOnClickListener(new View.OnClickListener() { // from class: com.carassistant.ambient.MainActivity.3
                @Override // android.view.View.OnClickListener
                public void onClick(View view) {
                    try {
                        MainActivity.this.startActivity(new Intent("android.settings.ACCESSIBILITY_SETTINGS"));
                    } catch (Throwable th) {
                        Toast.makeText(MainActivity.this, "无法打开无障碍设置", 0).show();
                    }
                }
            });
        }
        Button button2 = this.mNotifBtn;
        if (button2 != null) {
            button2.setOnClickListener(new View.OnClickListener() { // from class: com.carassistant.ambient.MainActivity.4
                @Override // android.view.View.OnClickListener
                public void onClick(View view) {
                    try {
                        MainActivity.this.startActivity(new Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"));
                    } catch (Throwable th) {
                        try {
                            MainActivity.this.startActivity(new Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"));
                        } catch (Throwable th2) {
                            Toast.makeText(MainActivity.this, "无法打开通知使用权", 0).show();
                        }
                    }
                }
            });
        }
    }

    @Override // android.app.Activity
    protected void onResume() {
        super.onResume();
        refreshStatus();
        startAmbientLoops();
    }

    @Override // android.app.Activity
    protected void onPause() {
        stopAmbientLoops();
        super.onPause();
    }

    @Override // android.app.Activity
    protected void onDestroy() {
        cancelFx();
        stopAmbientLoops();
        super.onDestroy();
    }

    private void startAmbientLoops() {
        stopAmbientLoops();
        boolean z = this.mSp.getBoolean(AmbientRhythmHelper.PREF_ENABLED, false);
        View view = this.mHeroGlow;
        if (view != null) {
            Property property = View.ALPHA;
            float[] fArr = new float[2];
            fArr[0] = z ? 0.35f : 0.22f;
            fArr[1] = z ? 0.75f : 0.42f;
            ObjectAnimator objectAnimatorOfFloat = ObjectAnimator.ofFloat(view, (Property<View, Float>) property, fArr);
            this.mGlowBreath = objectAnimatorOfFloat;
            objectAnimatorOfFloat.setDuration(z ? 1400L : 2400L);
            this.mGlowBreath.setRepeatCount(-1);
            this.mGlowBreath.setRepeatMode(2);
            this.mGlowBreath.setInterpolator(new AccelerateDecelerateInterpolator());
            this.mGlowBreath.start();
        }
        View view2 = this.mLiveDot;
        if (view2 != null) {
            Property property2 = View.ALPHA;
            float[] fArr2 = new float[2];
            fArr2[0] = 1.0f;
            fArr2[1] = z ? 0.25f : 0.55f;
            ObjectAnimator objectAnimatorOfFloat2 = ObjectAnimator.ofFloat(view2, (Property<View, Float>) property2, fArr2);
            this.mLivePulse = objectAnimatorOfFloat2;
            objectAnimatorOfFloat2.setDuration(z ? 520L : 1400L);
            this.mLivePulse.setRepeatCount(-1);
            this.mLivePulse.setRepeatMode(2);
            this.mLivePulse.start();
        }
        TextView textView = this.mLiveLabel;
        if (textView != null) {
            Property property3 = View.ALPHA;
            float[] fArr3 = new float[2];
            fArr3[0] = 1.0f;
            fArr3[1] = z ? 0.35f : 0.55f;
            ObjectAnimator objectAnimatorOfFloat3 = ObjectAnimator.ofFloat(textView, (Property<TextView, Float>) property3, fArr3);
            this.mBingxinPulse = objectAnimatorOfFloat3;
            objectAnimatorOfFloat3.setDuration(z ? 520L : 1400L);
            this.mBingxinPulse.setRepeatCount(-1);
            this.mBingxinPulse.setRepeatMode(2);
            this.mBingxinPulse.start();
        }
        TextView textView2 = this.mAboutBtn;
        if (textView2 != null) {
            Property property4 = View.ALPHA;
            float[] fArr4 = new float[2];
            fArr4[0] = 1.0f;
            fArr4[1] = z ? 0.35f : 0.55f;
            ObjectAnimator objectAnimatorOfFloat4 = ObjectAnimator.ofFloat(textView2, (Property<TextView, Float>) property4, fArr4);
            this.mAboutPulse = objectAnimatorOfFloat4;
            objectAnimatorOfFloat4.setDuration(z ? 520L : 1400L);
            this.mAboutPulse.setRepeatCount(-1);
            this.mAboutPulse.setRepeatMode(2);
            this.mAboutPulse.start();
        }
    }

    private void stopAmbientLoops() {
        ObjectAnimator objectAnimator = this.mGlowBreath;
        if (objectAnimator != null) {
            objectAnimator.cancel();
            this.mGlowBreath = null;
        }
        ObjectAnimator objectAnimator2 = this.mLivePulse;
        if (objectAnimator2 != null) {
            objectAnimator2.cancel();
            this.mLivePulse = null;
        }
        ObjectAnimator objectAnimator3 = this.mBingxinPulse;
        if (objectAnimator3 != null) {
            objectAnimator3.cancel();
            this.mBingxinPulse = null;
        }
        ObjectAnimator objectAnimator4 = this.mAboutPulse;
        if (objectAnimator4 != null) {
            objectAnimator4.cancel();
            this.mAboutPulse = null;
        }
        TextView textView = this.mLiveLabel;
        if (textView != null) {
            textView.setAlpha(1.0f);
        }
        TextView textView2 = this.mAboutBtn;
        if (textView2 != null) {
            textView2.setAlpha(1.0f);
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void refreshStatus() {
        int iColorRes;
        boolean z = this.mSp.getBoolean(AmbientRhythmHelper.PREF_ENABLED, false);
        int i = this.mSp.getInt(AmbientRhythmHelper.PREF_MODE, 0);
        boolean zIsAccessibilityEnabled = isAccessibilityEnabled();
        boolean zIsNotificationListenerEnabled = isNotificationListenerEnabled();
        refreshPermissionBlock(zIsAccessibilityEnabled, zIsNotificationListenerEnabled);
        updateModeCode(i, false);
        updateSpeedBar(i, false);
        TextView textView = this.mLiveLabel;
        if (textView != null) {
            textView.setText("Bingxin");
            TextView textView2 = this.mLiveLabel;
            if (z) {
                iColorRes = colorRes("qg_ok", -10498161);
            } else {
                iColorRes = colorRes("qg_muted", -6648184);
            }
            textView2.setTextColor(iColorRes);
        }
        if (this.mLiveDot != null) {
            int identifier = getResources().getIdentifier("qg_live_dot", "drawable", getPackageName());
            if (identifier != 0) {
                this.mLiveDot.setBackgroundResource(identifier);
            }
            this.mLiveDot.setAlpha(z ? 1.0f : 0.45f);
        }
        StringBuilder sb = new StringBuilder();
        sb.append(z ? "律动已开" : "律动已关");
        sb.append("  ·  ").append(AmbientRhythmHelper.modeName(i));
        if (zIsAccessibilityEnabled && zIsNotificationListenerEnabled) {
            sb.append("\n权限就绪，后台可常驻运行");
        } else {
            sb.append("\n");
            sb.append(zIsAccessibilityEnabled ? "无障碍已开" : "无障碍未开");
            sb.append("  ·  ");
            sb.append(zIsNotificationListenerEnabled ? "通知已开" : "通知未开");
        }
        TextView textView3 = this.mStatus;
        if (textView3 != null) {
            textView3.setText(sb.toString());
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void updateModeCode(int i, boolean z) {
        if (this.mModeCode == null) {
            return;
        }
        int iAccentForMode = accentForMode(i);
        this.mModeCode.setText(AmbientRhythmHelper.modeName(i));
        this.mModeCode.setTextColor(iAccentForMode);
        if (z) {
            ObjectAnimator objectAnimatorOfFloat = ObjectAnimator.ofFloat(this.mModeCode, View.SCALE_X, 1.0f, 1.18f, 1.0f);
            ObjectAnimator objectAnimatorOfFloat2 = ObjectAnimator.ofFloat(this.mModeCode, View.SCALE_Y, 1.0f, 1.18f, 1.0f);
            objectAnimatorOfFloat.setDuration(360L);
            objectAnimatorOfFloat2.setDuration(360L);
            objectAnimatorOfFloat.setInterpolator(new OvershootInterpolator(2.0f));
            objectAnimatorOfFloat2.setInterpolator(new OvershootInterpolator(2.0f));
            objectAnimatorOfFloat.start();
            objectAnimatorOfFloat2.start();
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void updateSpeedBar(int i, boolean z) {
        View view;
        float f;
        int iDp;
        if (this.mSpeedFill == null || (view = this.mSpeedTrack) == null) {
            return;
        }
        switch (i) {
            case AmbientRhythmHelper.MODE_RAGE /* 0 */:
                f = 0.92f;
                break;
            case AmbientRhythmHelper.MODE_SOFT /* 1 */:
                f = 0.42f;
                break;
            case 2:
                f = 0.58f;
                break;
            case AmbientRhythmHelper.MODE_PULSE /* 3 */:
                f = 0.72f;
                break;
            case AmbientRhythmHelper.MODE_EV /* 4 */:
            default:
                f = 0.35f;
                break;
            case AmbientRhythmHelper.MODE_DRIVE /* 5 */:
                f = 0.84f;
                break;
            case AmbientRhythmHelper.MODE_CUSTOM_MUSIC /* 6 */:
                f = 0.8f;
                break;
            case AmbientRhythmHelper.MODE_CUSTOM_AUTO /* 7 */:
                f = 0.48f;
                break;
        }
        if (view.getWidth() > 0) {
            iDp = this.mSpeedTrack.getWidth();
        } else {
            iDp = getResources().getDisplayMetrics().widthPixels - dp(60);
        }
        int iMax = Math.max(dp(18), Math.round(iDp * f));
        ViewGroup.LayoutParams layoutParams = this.mSpeedFill.getLayoutParams();
        if (!z || layoutParams == null) {
            if (layoutParams != null) {
                layoutParams.width = iMax;
                this.mSpeedFill.setLayoutParams(layoutParams);
                return;
            }
            return;
        }
        ValueAnimator valueAnimatorOfInt = ValueAnimator.ofInt(layoutParams.width > 0 ? layoutParams.width : dp(40), iMax);
        valueAnimatorOfInt.setDuration(420L);
        valueAnimatorOfInt.setInterpolator(new DecelerateInterpolator());
        valueAnimatorOfInt.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() { // from class: com.carassistant.ambient.MainActivity.5
            @Override // android.animation.ValueAnimator.AnimatorUpdateListener
            public void onAnimationUpdate(ValueAnimator valueAnimator) {
                ViewGroup.LayoutParams layoutParams2 = MainActivity.this.mSpeedFill.getLayoutParams();
                layoutParams2.width = ((Integer) valueAnimator.getAnimatedValue()).intValue();
                MainActivity.this.mSpeedFill.setLayoutParams(layoutParams2);
            }
        });
        valueAnimatorOfInt.start();
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void playIgnitionFx(boolean z) {
        View view;
        View view2 = this.mMasterRow;
        if (view2 != null) {
            ObjectAnimator objectAnimatorOfFloat = ObjectAnimator.ofFloat(view2, (Property<View, Float>) View.SCALE_X, 1.0f, 1.02f, 1.0f);
            ObjectAnimator objectAnimatorOfFloat2 = ObjectAnimator.ofFloat(this.mMasterRow, (Property<View, Float>) View.SCALE_Y, 1.0f, 1.02f, 1.0f);
            objectAnimatorOfFloat.setDuration(320L);
            objectAnimatorOfFloat2.setDuration(320L);
            objectAnimatorOfFloat.start();
            objectAnimatorOfFloat2.start();
        }
        if (z && (view = this.mModeFxGlow) != null) {
            view.setBackgroundColor(this.mAccentDefault);
            ObjectAnimator objectAnimatorOfFloat3 = ObjectAnimator.ofFloat(this.mModeFxGlow, (Property<View, Float>) View.ALPHA, 0.0f, 0.28f, 0.0f);
            objectAnimatorOfFloat3.setDuration(560L);
            objectAnimatorOfFloat3.start();
        }
        startAmbientLoops();
    }

    private void refreshPermissionBlock(boolean z, boolean z2) {
        View view = this.mPermBlock;
        if (view == null) {
            return;
        }
        boolean z3 = !z;
        boolean z4 = !z2;
        if (!z3 && !z4) {
            view.setVisibility(8);
            return;
        }
        view.setVisibility(0);
        setBtnVisible(this.mA11yBtn, z3, z4);
        setBtnVisible(this.mNotifBtn, z4, z3);
    }

    private void setBtnVisible(Button button, boolean z, boolean z2) {
        if (button == null) {
            return;
        }
        if (!z) {
            button.setVisibility(8);
            return;
        }
        int iDp = 0;
        button.setVisibility(0);
        ViewGroup.LayoutParams layoutParams = button.getLayoutParams();
        if (layoutParams instanceof LinearLayout.LayoutParams) {
            LinearLayout.LayoutParams layoutParams2 = (LinearLayout.LayoutParams) layoutParams;
            layoutParams2.width = 0;
            layoutParams2.weight = 1.0f;
            if (button == this.mNotifBtn) {
                if (z2) {
                    iDp = dp(8);
                }
                layoutParams2.leftMargin = iDp;
            }
            button.setLayoutParams(layoutParams2);
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public int dp(int i) {
        return Math.round(i * getResources().getDisplayMetrics().density);
    }

    private void wireModeListener() {
        this.mModeGroup.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() { // from class: com.carassistant.ambient.MainActivity.6
            @Override // android.widget.RadioGroup.OnCheckedChangeListener
            public void onCheckedChanged(RadioGroup radioGroup, int i) {
                if (i > 0) {
                    int iModeFromRadioId = MainActivity.this.modeFromRadioId(i);
                    MainActivity.this.mSp.edit().putInt(AmbientRhythmHelper.PREF_MODE, iModeFromRadioId).apply();
                    MainActivity.this.refreshStatus();
                    MainActivity mainActivity = MainActivity.this;
                    mainActivity.refreshModeHighlights(iModeFromRadioId, mainActivity.mModeFxReady);
                    MainActivity mainActivity2 = MainActivity.this;
                    mainActivity2.updateModeCode(iModeFromRadioId, mainActivity2.mModeFxReady);
                    MainActivity mainActivity3 = MainActivity.this;
                    mainActivity3.updateSpeedBar(iModeFromRadioId, mainActivity3.mModeFxReady);
                    MainActivity.this.refreshCustomPanel(iModeFromRadioId);
                    if (MainActivity.this.mModeFxReady) {
                        MainActivity mainActivity4 = MainActivity.this;
                        mainActivity4.playModeSwitchFx(iModeFromRadioId, mainActivity4.findViewById(i));
                        Toast.makeText(MainActivity.this, "已选：" + AmbientRhythmHelper.modeName(iModeFromRadioId), 0).show();
                    }
                }
            }
        });
    }

    private void wireCustomPanel() {
        if (this.mCustomColorSeek == null) {
            return;
        }
        int[] customPalette = AmbientRhythmHelper.getCustomPalette(this);
        int i = customPalette.length > 0 ? customPalette[0] : 32;
        this.mCustomColorSeek.setProgress(i - 1);
        updateCustomColorLabels(i);
        this.mCustomColorSeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() { // from class: com.carassistant.ambient.MainActivity.7
            @Override // android.widget.SeekBar.OnSeekBarChangeListener
            public void onProgressChanged(SeekBar seekBar, int i2, boolean z) {
                MainActivity.this.updateCustomColorLabels(i2 + 1);
            }

            @Override // android.widget.SeekBar.OnSeekBarChangeListener
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override // android.widget.SeekBar.OnSeekBarChangeListener
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });
        Button button = this.mCustomColorMinusBtn;
        if (button != null) {
            button.setOnClickListener(new View.OnClickListener() { // from class: com.carassistant.ambient.MainActivity.8
                @Override // android.view.View.OnClickListener
                public void onClick(View view) {
                    MainActivity.this.nudgeCustomColor(-1);
                }
            });
        }
        Button button2 = this.mCustomColorPlusBtn;
        if (button2 != null) {
            button2.setOnClickListener(new View.OnClickListener() { // from class: com.carassistant.ambient.MainActivity.9
                @Override // android.view.View.OnClickListener
                public void onClick(View view) {
                    MainActivity.this.nudgeCustomColor(1);
                }
            });
        }
        int customAutoSec = AmbientRhythmHelper.getCustomAutoSec(this);
        SeekBar seekBar = this.mCustomIntervalSeek;
        if (seekBar != null) {
            seekBar.setProgress(customAutoSec - 2);
            updateCustomIntervalLabel(customAutoSec);
            this.mCustomIntervalSeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() { // from class: com.carassistant.ambient.MainActivity.10
                @Override // android.widget.SeekBar.OnSeekBarChangeListener
                public void onProgressChanged(SeekBar seekBar2, int i2, boolean z) {
                    int i3 = i2 + 2;
                    MainActivity.this.updateCustomIntervalLabel(i3);
                    if (z) {
                        AmbientRhythmHelper.setCustomAutoSec(MainActivity.this, i3);
                    }
                }

                @Override // android.widget.SeekBar.OnSeekBarChangeListener
                public void onStartTrackingTouch(SeekBar seekBar2) {
                }

                @Override // android.widget.SeekBar.OnSeekBarChangeListener
                public void onStopTrackingTouch(SeekBar seekBar2) {
                }
            });
        }
        Button button3 = this.mCustomAddBtn;
        if (button3 != null) {
            button3.setOnClickListener(new View.OnClickListener() { // from class: com.carassistant.ambient.MainActivity.11
                @Override // android.view.View.OnClickListener
                public void onClick(View view) {
                    int progress = MainActivity.this.mCustomColorSeek.getProgress() + 1;
                    int[] customPalette2 = AmbientRhythmHelper.getCustomPalette(MainActivity.this);
                    if (customPalette2.length >= 30) {
                        Toast.makeText(MainActivity.this, "色板最多 30 色", 0).show();
                        return;
                    }
                    for (int i2 : customPalette2) {
                        if (i2 == progress) {
                            Toast.makeText(MainActivity.this, "色号已在色板中", 0).show();
                            return;
                        }
                    }
                    int[] iArr = new int[customPalette2.length + 1];
                    System.arraycopy(customPalette2, 0, iArr, 0, customPalette2.length);
                    iArr[customPalette2.length] = progress;
                    AmbientRhythmHelper.setCustomPalette(MainActivity.this, iArr);
                    MainActivity.this.rebuildPaletteChips();
                    Toast.makeText(MainActivity.this, "已加入色号 " + progress, 0).show();
                }
            });
        }
        Button button4 = this.mCustomPreviewBtn;
        if (button4 != null) {
            button4.setOnClickListener(new View.OnClickListener() { // from class: com.carassistant.ambient.MainActivity.12
                @Override // android.view.View.OnClickListener
                public void onClick(View view) {
                    int progress = MainActivity.this.mCustomColorSeek.getProgress() + 1;
                    Toast.makeText(MainActivity.this, AmbientRhythmHelper.previewLampColor(MainActivity.this, progress) ? "已试色 " + progress : "试色失败：请先开启律动并确保无障碍已开", 0).show();
                }
            });
        }
        Button button5 = this.mCustomResetBtn;
        if (button5 != null) {
            button5.setOnClickListener(new View.OnClickListener() { // from class: com.carassistant.ambient.MainActivity.13
                @Override // android.view.View.OnClickListener
                public void onClick(View view) {
                    AmbientRhythmHelper.resetCustomPalette(MainActivity.this);
                    int[] customPalette2 = AmbientRhythmHelper.getCustomPalette(MainActivity.this);
                    if (customPalette2.length > 0 && MainActivity.this.mCustomColorSeek != null) {
                        MainActivity.this.mCustomColorSeek.setProgress(customPalette2[0] - 1);
                        MainActivity.this.updateCustomColorLabels(customPalette2[0]);
                    }
                    MainActivity.this.rebuildPaletteChips();
                    Toast.makeText(MainActivity.this, "已恢复默认色板", 0).show();
                }
            });
        }
        this.mCustomUiReady = true;
        rebuildPaletteChips();
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void refreshCustomPanel(int i) {
        View view = this.mCustomPanel;
        if (view == null) {
            return;
        }
        boolean z = i == 6 || i == 7;
        view.setVisibility(z ? 0 : 8);
        View view2 = this.mCustomIntervalBlock;
        if (view2 != null) {
            view2.setVisibility(i != 7 ? 8 : 0);
        }
        if (z && this.mCustomUiReady) {
            rebuildPaletteChips();
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void nudgeCustomColor(int i) {
        SeekBar seekBar = this.mCustomColorSeek;
        if (seekBar == null) {
            return;
        }
        int progress = seekBar.getProgress() + 1 + i;
        int i2 = progress >= 1 ? progress : 1;
        if (i2 > 253) {
            i2 = 253;
        }
        this.mCustomColorSeek.setProgress(i2 - 1);
        updateCustomColorLabels(i2);
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void updateCustomColorLabels(int i) {
        TextView textView = this.mCustomColorLabel;
        if (textView != null) {
            textView.setText("色号 " + i);
        }
        if (this.mCustomColorSwatch != null) {
            GradientDrawable gradientDrawable = new GradientDrawable();
            gradientDrawable.setShape(1);
            gradientDrawable.setColor(AmbientRhythmHelper.approxRgbForIndex(i));
            gradientDrawable.setStroke(dp(1), colorRes("qg_accent", -20336));
            this.mCustomColorSwatch.setBackground(gradientDrawable);
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void updateCustomIntervalLabel(int i) {
        TextView textView = this.mCustomIntervalLabel;
        if (textView == null) {
            return;
        }
        textView.setText("当前 " + i + " 秒");
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void rebuildPaletteChips() {
        LinearLayout linearLayout = this.mCustomPaletteRow;
        if (linearLayout == null) {
            return;
        }
        linearLayout.removeAllViews();
        final int[] customPalette = AmbientRhythmHelper.getCustomPalette(this);
        int iDp = dp(40);
        int iDp2 = dp(8);
        for (int i = 0; i < customPalette.length; i++) {
            final int i2 = customPalette[i];
            final int iLoop = i;
            View view = new View(this);
            LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(iDp, iDp);
            if (i > 0) {
                layoutParams.leftMargin = iDp2;
            }
            view.setLayoutParams(layoutParams);
            GradientDrawable gradientDrawable = new GradientDrawable();
            gradientDrawable.setCornerRadius(dp(10));
            gradientDrawable.setColor(AmbientRhythmHelper.approxRgbForIndex(i2));
            gradientDrawable.setStroke(dp(2), colorRes("qg_text", -2320));
            view.setBackground(gradientDrawable);
            view.setOnClickListener(new View.OnClickListener() { // from class: com.carassistant.ambient.MainActivity.14
                @Override // android.view.View.OnClickListener
                public void onClick(View view2) {
                    if (MainActivity.this.mCustomColorSeek != null) {
                        MainActivity.this.mCustomColorSeek.setProgress(i2 - 1);
                        MainActivity.this.updateCustomColorLabels(i2);
                    }
                }
            });
            view.setOnLongClickListener(new View.OnLongClickListener() { // from class: com.carassistant.ambient.MainActivity.15
                @Override // android.view.View.OnLongClickListener
                public boolean onLongClick(View view2) {
                    int[] iArr = customPalette;
                    if (iArr.length <= 2) {
                        Toast.makeText(MainActivity.this, "至少保留 2 色", 0).show();
                        return true;
                    }
                    int[] iArr2 = new int[iArr.length - 1];
                    int i3 = 0;
                    int i4 = 0;
                    while (true) {
                        int[] iArr3 = customPalette;
                        if (i3 < iArr3.length) {
                            if (i3 != iLoop) {
                                iArr2[i4] = iArr3[i3];
                                i4++;
                            }
                            i3++;
                        } else {
                            AmbientRhythmHelper.setCustomPalette(MainActivity.this, iArr2);
                            MainActivity.this.rebuildPaletteChips();
                            Toast.makeText(MainActivity.this, "已删除色号 " + i2, 0).show();
                            return true;
                        }
                    }
                }
            });
            this.mCustomPaletteRow.addView(view);
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void refreshModeHighlights(int i, boolean z) {
        int iRadioIdForMode = radioIdForMode(i);
        int childCount = this.mModeGroup.getChildCount();
        int identifier = getResources().getIdentifier("qg_mode_idle", "drawable", getPackageName());
        int identifier2 = getResources().getIdentifier("qg_mode_selected", "drawable", getPackageName());
        for (int i2 = 0; i2 < childCount; i2++) {
            View childAt = this.mModeGroup.getChildAt(i2);
            if (childAt instanceof RadioButton) {
                boolean z2 = childAt.getId() == iRadioIdForMode;
                childAt.setBackgroundResource(z2 ? identifier2 : identifier);
                if (!z) {
                    childAt.setScaleX(1.0f);
                    childAt.setScaleY(1.0f);
                    childAt.setAlpha(z2 ? 1.0f : 0.78f);
                } else if (!z2) {
                    childAt.setAlpha(0.78f);
                    childAt.setScaleX(1.0f);
                    childAt.setScaleY(1.0f);
                }
            }
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void playModeSwitchFx(int i, final View view) {
        cancelFx();
        final int iAccentForMode = accentForMode(i);
        View view2 = this.mModeFlashBar;
        if (view2 != null) {
            view2.setBackgroundColor(iAccentForMode);
        }
        View view3 = this.mBrandLine;
        if (view3 != null) {
            view3.setBackgroundColor(iAccentForMode);
        }
        View view4 = this.mModeFxGlow;
        if (view4 != null) {
            view4.setBackgroundColor(iAccentForMode);
        }
        View view5 = this.mHeroGlow;
        if (view5 != null) {
            view5.setAlpha(0.9f);
        }
        AnimatorSet animatorSet = new AnimatorSet();
        ArrayList arrayList = new ArrayList();
        View view6 = this.mModeFxGlow;
        if (view6 != null) {
            ObjectAnimator objectAnimatorOfFloat = ObjectAnimator.ofFloat(view6, (Property<View, Float>) View.ALPHA, 0.0f, 0.32f, 0.08f, 0.26f, 0.0f);
            objectAnimatorOfFloat.setDuration(620L);
            arrayList.add(objectAnimatorOfFloat);
        }
        View view7 = this.mModeFlashBar;
        if (view7 != null) {
            view7.setPivotX(0.0f);
            ObjectAnimator objectAnimatorOfFloat2 = ObjectAnimator.ofFloat(this.mModeFlashBar, (Property<View, Float>) View.SCALE_X, 0.08f, 1.0f);
            objectAnimatorOfFloat2.setDuration(420L);
            objectAnimatorOfFloat2.setInterpolator(new DecelerateInterpolator());
            ObjectAnimator objectAnimatorOfFloat3 = ObjectAnimator.ofFloat(this.mModeFlashBar, (Property<View, Float>) View.ALPHA, 1.0f, 0.55f);
            objectAnimatorOfFloat3.setDuration(500L);
            arrayList.add(objectAnimatorOfFloat2);
            arrayList.add(objectAnimatorOfFloat3);
        }
        View view8 = this.mBrandLine;
        if (view8 != null) {
            view8.setPivotX(0.0f);
            ObjectAnimator objectAnimatorOfFloat4 = ObjectAnimator.ofFloat(this.mBrandLine, (Property<View, Float>) View.SCALE_X, 1.0f, 3.2f, 1.0f);
            objectAnimatorOfFloat4.setDuration(540L);
            arrayList.add(objectAnimatorOfFloat4);
        }
        if (view != null) {
            view.setAlpha(1.0f);
            ObjectAnimator objectAnimatorOfFloat5 = ObjectAnimator.ofFloat(view, (Property<View, Float>) View.SCALE_X, 1.0f, 1.055f, 1.0f);
            ObjectAnimator objectAnimatorOfFloat6 = ObjectAnimator.ofFloat(view, (Property<View, Float>) View.SCALE_Y, 1.0f, 1.055f, 1.0f);
            objectAnimatorOfFloat5.setDuration(480L);
            objectAnimatorOfFloat6.setDuration(480L);
            objectAnimatorOfFloat5.setInterpolator(new OvershootInterpolator(1.8f));
            objectAnimatorOfFloat6.setInterpolator(new OvershootInterpolator(1.8f));
            arrayList.add(objectAnimatorOfFloat5);
            arrayList.add(objectAnimatorOfFloat6);
            final GradientDrawable gradientDrawable = new GradientDrawable();
            gradientDrawable.setCornerRadius(dp(12));
            gradientDrawable.setOrientation(GradientDrawable.Orientation.LEFT_RIGHT);
            gradientDrawable.setColors(new int[]{Color.argb(160, Color.red(iAccentForMode), Color.green(iAccentForMode), Color.blue(iAccentForMode)), Color.argb(40, Color.red(iAccentForMode), Color.green(iAccentForMode), Color.blue(iAccentForMode))});
            gradientDrawable.setStroke(dp(2), iAccentForMode);
            view.setBackground(gradientDrawable);
            ValueAnimator valueAnimatorOfFloat = ValueAnimator.ofFloat(0.0f, 1.0f);
            valueAnimatorOfFloat.setDuration(560L);
            valueAnimatorOfFloat.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() { // from class: com.carassistant.ambient.MainActivity.16
                @Override // android.animation.ValueAnimator.AnimatorUpdateListener
                public void onAnimationUpdate(ValueAnimator valueAnimator) {
                    float fFloatValue = ((Float) valueAnimator.getAnimatedValue()).floatValue();
                    gradientDrawable.setColors(new int[]{((Integer) new ArgbEvaluator().evaluate(fFloatValue, Integer.valueOf(Color.argb(160, Color.red(iAccentForMode), Color.green(iAccentForMode), Color.blue(iAccentForMode))), Integer.valueOf(MainActivity.this.mSelectedBg))).intValue(), ((Integer) new ArgbEvaluator().evaluate(fFloatValue, Integer.valueOf(Color.argb(50, Color.red(iAccentForMode), Color.green(iAccentForMode), Color.blue(iAccentForMode))), Integer.valueOf(Color.argb(20, 255, 138, 61)))).intValue()});
                    gradientDrawable.setStroke(MainActivity.this.dp(2), ((Integer) new ArgbEvaluator().evaluate(fFloatValue, Integer.valueOf(iAccentForMode), Integer.valueOf(MainActivity.this.mAccentDefault))).intValue());
                }
            });
            valueAnimatorOfFloat.addListener(new AnimatorListenerAdapter() { // from class: com.carassistant.ambient.MainActivity.17
                @Override // android.animation.AnimatorListenerAdapter, android.animation.Animator.AnimatorListener
                public void onAnimationEnd(Animator animator) {
                    int identifier = MainActivity.this.getResources().getIdentifier("qg_mode_selected", "drawable", MainActivity.this.getPackageName());
                    View view9 = view;
                    if (view9 != null && identifier != 0) {
                        view9.setBackgroundResource(identifier);
                    }
                }
            });
            arrayList.add(valueAnimatorOfFloat);
        }
        if (this.mStatus != null) {
            ValueAnimator valueAnimatorOfObject = ValueAnimator.ofObject(new ArgbEvaluator(), Integer.valueOf(iAccentForMode), Integer.valueOf(this.mStatusDefault));
            valueAnimatorOfObject.setDuration(620L);
            valueAnimatorOfObject.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() { // from class: com.carassistant.ambient.MainActivity.18
                @Override // android.animation.ValueAnimator.AnimatorUpdateListener
                public void onAnimationUpdate(ValueAnimator valueAnimator) {
                    MainActivity.this.mStatus.setTextColor(((Integer) valueAnimator.getAnimatedValue()).intValue());
                }
            });
            ObjectAnimator objectAnimatorOfFloat7 = ObjectAnimator.ofFloat(this.mStatus, View.ALPHA, 1.0f, 0.4f, 1.0f);
            objectAnimatorOfFloat7.setDuration(420L);
            arrayList.add(valueAnimatorOfObject);
            arrayList.add(objectAnimatorOfFloat7);
        }
        View view9 = this.mModeCard;
        if (view9 != null) {
            ObjectAnimator objectAnimatorOfFloat8 = ObjectAnimator.ofFloat(view9, (Property<View, Float>) View.TRANSLATION_Y, 10.0f, 0.0f);
            objectAnimatorOfFloat8.setDuration(300L);
            objectAnimatorOfFloat8.setInterpolator(new DecelerateInterpolator());
            arrayList.add(objectAnimatorOfFloat8);
        }
        if (arrayList.isEmpty()) {
            return;
        }
        animatorSet.playTogether(arrayList);
        animatorSet.addListener(new AnimatorListenerAdapter() { // from class: com.carassistant.ambient.MainActivity.19
            @Override // android.animation.AnimatorListenerAdapter, android.animation.Animator.AnimatorListener
            public void onAnimationEnd(Animator animator) {
                MainActivity.this.mRunningFx = null;
                if (MainActivity.this.mModeFlashBar != null) {
                    MainActivity.this.mModeFlashBar.setScaleX(1.0f);
                    MainActivity.this.mModeFlashBar.setAlpha(0.55f);
                }
                if (MainActivity.this.mBrandLine != null) {
                    MainActivity.this.mBrandLine.setScaleX(1.0f);
                    MainActivity.this.mBrandLine.setBackgroundColor(MainActivity.this.mAccentDefault);
                }
                if (MainActivity.this.mModeFxGlow != null) {
                    MainActivity.this.mModeFxGlow.setAlpha(0.0f);
                }
                if (MainActivity.this.mStatus != null) {
                    MainActivity.this.mStatus.setTextColor(MainActivity.this.mStatusDefault);
                    MainActivity.this.mStatus.setAlpha(1.0f);
                }
            }
        });
        this.mRunningFx = animatorSet;
        animatorSet.start();
    }

    private void cancelFx() {
        AnimatorSet animatorSet = this.mRunningFx;
        if (animatorSet != null) {
            animatorSet.cancel();
            this.mRunningFx = null;
        }
    }

    private int accentForMode(int i) {
        switch (i) {
            case AmbientRhythmHelper.MODE_SOFT /* 1 */:
                return colorRes("qg_mode_soft", -9516801);
            case 2:
                return colorRes("qg_mode_grad", -3892225);
            case AmbientRhythmHelper.MODE_PULSE /* 3 */:
                return colorRes("qg_mode_pulse", -18611);
            case AmbientRhythmHelper.MODE_EV /* 4 */:
                return colorRes("qg_mode_ev", -11870592);
            case AmbientRhythmHelper.MODE_DRIVE /* 5 */:
                return colorRes("qg_mode_drive", -34235);
            case AmbientRhythmHelper.MODE_CUSTOM_MUSIC /* 6 */:
                return colorRes("qg_mode_custom", -10764066);
            case AmbientRhythmHelper.MODE_CUSTOM_AUTO /* 7 */:
                return colorRes("qg_mode_custom_auto", -8531008);
            default:
                return colorRes("qg_mode_rage", -53955);
        }
    }

    private int colorRes(String str, int i) {
        int identifier = getResources().getIdentifier(str, "color", getPackageName());
        if (identifier == 0) {
            return i;
        }
        try {
            return getResources().getColor(identifier);
        } catch (Throwable th) {
            return i;
        }
    }

    private void applyModeToRadios(int i) {
        int iRadioIdForMode = radioIdForMode(i);
        this.mModeGroup.setOnCheckedChangeListener(null);
        if (iRadioIdForMode != 0) {
            this.mModeGroup.check(iRadioIdForMode);
        }
    }

    private int radioIdForMode(int i) {
        switch (i) {
            case AmbientRhythmHelper.MODE_SOFT /* 1 */:
                return Res.id(this, "modeSoftRB");
            case 2:
                return Res.id(this, "modeGradientRB");
            case AmbientRhythmHelper.MODE_PULSE /* 3 */:
                return Res.id(this, "modePulseRB");
            case AmbientRhythmHelper.MODE_EV /* 4 */:
                return Res.id(this, "modeEvRB");
            case AmbientRhythmHelper.MODE_DRIVE /* 5 */:
                return Res.id(this, "modeDriveRB");
            case AmbientRhythmHelper.MODE_CUSTOM_MUSIC /* 6 */:
                return Res.id(this, "modeCustomMusicRB");
            case AmbientRhythmHelper.MODE_CUSTOM_AUTO /* 7 */:
                return Res.id(this, "modeCustomAutoRB");
            default:
                return Res.id(this, "modeRageRB");
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public int modeFromRadioId(int i) {
        if (i == Res.id(this, "modeSoftRB")) {
            return 1;
        }
        if (i == Res.id(this, "modeGradientRB")) {
            return 2;
        }
        if (i == Res.id(this, "modePulseRB")) {
            return 3;
        }
        if (i == Res.id(this, "modeEvRB")) {
            return 4;
        }
        if (i == Res.id(this, "modeDriveRB")) {
            return 5;
        }
        if (i == Res.id(this, "modeCustomMusicRB")) {
            return 6;
        }
        if (i == Res.id(this, "modeCustomAutoRB")) {
            return 7;
        }
        return 0;
    }

    private boolean isAccessibilityEnabled() {
        try {
            String string = Settings.Secure.getString(getContentResolver(), "enabled_accessibility_services");
            if (TextUtils.isEmpty(string)) {
                return false;
            }
            ComponentName componentName = new ComponentName(this, (Class<?>) RhythmAccessibilityService.class);
            String strFlattenToString = componentName.flattenToString();
            String strFlattenToShortString = componentName.flattenToShortString();
            TextUtils.SimpleStringSplitter simpleStringSplitter = new TextUtils.SimpleStringSplitter(':');
            simpleStringSplitter.setString(string);
            while (simpleStringSplitter.hasNext()) {
                String next = simpleStringSplitter.next();
                if (strFlattenToString.equalsIgnoreCase(next) || strFlattenToShortString.equalsIgnoreCase(next)) {
                    return true;
                }
            }
        } catch (Throwable th) {
        }
        return false;
    }

    private boolean isNotificationListenerEnabled() {
        try {
            String string = Settings.Secure.getString(getContentResolver(), "enabled_notification_listeners");
            if (TextUtils.isEmpty(string)) {
                return false;
            }
            ComponentName componentName = new ComponentName(this, (Class<?>) MediaNotificationListenerService.class);
            String strFlattenToString = componentName.flattenToString();
            String strFlattenToShortString = componentName.flattenToShortString();
            for (String str : string.split(":")) {
                if (strFlattenToString.equalsIgnoreCase(str) || strFlattenToShortString.equalsIgnoreCase(str)) {
                    return true;
                }
            }
        } catch (Throwable th) {
        }
        return false;
    }
}
