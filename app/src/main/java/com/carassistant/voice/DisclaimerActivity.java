package com.carassistant.voice;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.preference.PreferenceManager;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

/* JADX INFO: loaded from: classes.dex */
public class DisclaimerActivity extends Activity {
    private static final int AD_SECONDS = 60;
    private static final String AGREE_WORD = "同意，后果自行承担";
    public static final String PREF_DISCLAIMER_OK = "key_carvoice_disclaimer_ok_v2";
    private boolean mAdDone;
    private Button mAgreeBtn;
    private TextView mCountdown;
    private String mCountdownDone;
    private String mCountdownFmt;
    private EditText mInput;
    private ProgressBar mProgress;
    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private int mLeft = AD_SECONDS;
    private final Runnable mTick = new Runnable() { // from class: com.carassistant.voice.DisclaimerActivity.1
        @Override // java.lang.Runnable
        public void run() {
            if (DisclaimerActivity.this.isFinishing()) {
                return;
            }
            DisclaimerActivity.access$010(DisclaimerActivity.this);
            if (DisclaimerActivity.this.mLeft > 0) {
                DisclaimerActivity.this.updateWaitUi();
                DisclaimerActivity.this.mHandler.postDelayed(this, 1000L);
            } else {
                DisclaimerActivity.this.mAdDone = true;
                DisclaimerActivity.this.updateWaitUi();
                DisclaimerActivity.this.refreshAgreeEnabled();
            }
        }
    };

    static /* synthetic */ int access$010(DisclaimerActivity disclaimerActivity) {
        int i = disclaimerActivity.mLeft;
        disclaimerActivity.mLeft = i - 1;
        return i;
    }

    public static boolean hasAgreed(Context context) {
        return PreferenceManager.getDefaultSharedPreferences(context).getBoolean(PREF_DISCLAIMER_OK, false);
    }

    @Override // android.app.Activity
    protected void onCreate(Bundle bundle) {
        super.onCreate(bundle);
        if (hasAgreed(this)) {
            goMain();
            return;
        }
        setContentView(Res.layout(this, "activity_disclaimer"));
        this.mCountdown = (TextView) findViewById(Res.id(this, "adCountdown"));
        this.mProgress = (ProgressBar) findViewById(Res.id(this, "adProgress"));
        this.mInput = (EditText) findViewById(Res.id(this, "agreeInput"));
        this.mAgreeBtn = (Button) findViewById(Res.id(this, "agreeBtn"));
        this.mCountdownFmt = getString(Res.string(this, "disclaimer_countdown_fmt"));
        this.mCountdownDone = getString(Res.string(this, "disclaimer_countdown_done"));
        ProgressBar progressBar = this.mProgress;
        if (progressBar != null) {
            progressBar.setMax(AD_SECONDS);
        }
        updateWaitUi();
        EditText editText = this.mInput;
        if (editText != null) {
            editText.addTextChangedListener(new TextWatcher() { // from class: com.carassistant.voice.DisclaimerActivity.2
                @Override // android.text.TextWatcher
                public void beforeTextChanged(CharSequence charSequence, int i, int i2, int i3) {
                }

                @Override // android.text.TextWatcher
                public void onTextChanged(CharSequence charSequence, int i, int i2, int i3) {
                    DisclaimerActivity.this.refreshAgreeEnabled();
                }

                @Override // android.text.TextWatcher
                public void afterTextChanged(Editable editable) {
                }
            });
        }
        Button button = this.mAgreeBtn;
        if (button != null) {
            button.setOnClickListener(new View.OnClickListener() { // from class: com.carassistant.voice.DisclaimerActivity.3
                @Override // android.view.View.OnClickListener
                public void onClick(View view) {
                    DisclaimerActivity.this.tryAgree();
                }
            });
        }
        View viewFindViewById = findViewById(Res.id(this, "exitBtn"));
        if (viewFindViewById != null) {
            viewFindViewById.setOnClickListener(new View.OnClickListener() { // from class: com.carassistant.voice.DisclaimerActivity.4
                @Override // android.view.View.OnClickListener
                public void onClick(View view) {
                    DisclaimerActivity.this.finish();
                }
            });
        }
        refreshAgreeEnabled();
        this.mHandler.postDelayed(this.mTick, 1000L);
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void updateWaitUi() {
        int i;
        TextView textView = this.mCountdown;
        if (textView != null) {
            if (this.mAdDone || (i = this.mLeft) <= 0) {
                textView.setText(this.mCountdownDone);
                this.mCountdown.setTextColor(getResources().getColor(getResources().getIdentifier("cv_accent", "color", getPackageName())));
            } else {
                textView.setText(String.format(this.mCountdownFmt, Integer.valueOf(i)));
                this.mCountdown.setTextColor(getResources().getColor(getResources().getIdentifier("cv_text", "color", getPackageName())));
            }
        }
        if (this.mProgress != null) {
            int iMax = Math.max(this.mLeft, 0);
            int i2 = AD_SECONDS;
            int i3 = 60 - iMax;
            if (!this.mAdDone) {
                i2 = i3;
            }
            this.mProgress.setProgress(i2);
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void refreshAgreeEnabled() {
        if (this.mAgreeBtn == null) {
            return;
        }
        EditText editText = this.mInput;
        boolean z = this.mAdDone && (editText != null && AGREE_WORD.equals(editText.getText().toString().trim()));
        this.mAgreeBtn.setEnabled(z);
        this.mAgreeBtn.setTextColor(getResources().getColor(getResources().getIdentifier(z ? "cv_accent" : "cv_muted", "color", getPackageName())));
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void tryAgree() {
        if (!this.mAdDone) {
            Toast.makeText(this, Res.string(this, "disclaimer_wait"), 0).show();
            return;
        }
        EditText editText = this.mInput;
        if (!AGREE_WORD.equals(editText != null ? editText.getText().toString().trim() : "")) {
            Toast.makeText(this, Res.string(this, "disclaimer_type_agree"), 0).show();
        } else {
            PreferenceManager.getDefaultSharedPreferences(this).edit().putBoolean(PREF_DISCLAIMER_OK, true).apply();
            goMain();
        }
    }

    private void goMain() {
        startActivity(new Intent(this, (Class<?>) MainActivity.class));
        finish();
    }

    @Override // android.app.Activity
    protected void onDestroy() {
        this.mHandler.removeCallbacks(this.mTick);
        super.onDestroy();
    }

    @Override // android.app.Activity
    public void onBackPressed() {
        finish();
    }
}
