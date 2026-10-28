package com.carassistant.voice;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

/* JADX INFO: loaded from: classes.dex */
public class MainActivity extends Activity {
    public static final String PREF_BOOT_HINT = "key_carvoice_boot_hint";
    private static final int REQ_PICK = 1001;
    private View mA11yBlock;
    private LinearLayout mDoorHost;
    private CheckBox mEnable;
    private LinearLayout mGearHost;
    private View mPageDoor;
    private View mPageGear;
    private View mPageMore;
    private SharedPreferences mSp;
    private TextView mStatus;
    private int mTab;
    private TextView mTabDoor;
    private TextView mTabGear;
    private TextView mTabMore;
    private LinearLayout mTurnHost;
    private CheckBox mVolBoost;
    private View mVolLevelRow;
    private TextView mVolMax;
    private TextView mVolMid;
    private TextView mVolMidHigh;

    @Override // android.app.Activity
    protected void onCreate(Bundle bundle) {
        super.onCreate(bundle);
        if (!DisclaimerActivity.hasAgreed(this)) {
            startActivity(new Intent(this, (Class<?>) DisclaimerActivity.class));
            finish();
            return;
        }
        setContentView(Res.layout(this, "cv_activity_main"));
        this.mSp = PreferenceManager.getDefaultSharedPreferences(this);
        this.mEnable = (CheckBox) findViewById(Res.id(this, "enableCB"));
        this.mStatus = (TextView) findViewById(Res.id(this, "statusText"));
        CheckBox checkBox = (CheckBox) findViewById(Res.id(this, "gearCB"));
        CheckBox checkBox2 = (CheckBox) findViewById(Res.id(this, "doorFlCB"));
        CheckBox checkBox3 = (CheckBox) findViewById(Res.id(this, "doorFrCB"));
        CheckBox checkBox4 = (CheckBox) findViewById(Res.id(this, "doorRlCB"));
        CheckBox checkBox5 = (CheckBox) findViewById(Res.id(this, "doorRrCB"));
        CheckBox checkBox6 = (CheckBox) findViewById(Res.id(this, "doorOpenOnlyCB"));
        CheckBox checkBox7 = (CheckBox) findViewById(Res.id(this, "turnCB"));
        final CheckBox checkBox8 = (CheckBox) findViewById(Res.id(this, "bootCB"));
        this.mGearHost = (LinearLayout) findViewById(Res.id(this, "gearEventHost"));
        this.mDoorHost = (LinearLayout) findViewById(Res.id(this, "doorEventHost"));
        this.mTurnHost = (LinearLayout) findViewById(Res.id(this, "turnEventHost"));
        this.mA11yBlock = findViewById(Res.id(this, "a11yBlock"));
        this.mTabGear = (TextView) findViewById(Res.id(this, "tabGear"));
        this.mTabDoor = (TextView) findViewById(Res.id(this, "tabDoor"));
        this.mTabMore = (TextView) findViewById(Res.id(this, "tabMore"));
        this.mPageGear = findViewById(Res.id(this, "pageGear"));
        this.mPageDoor = findViewById(Res.id(this, "pageDoor"));
        this.mPageMore = findViewById(Res.id(this, "pageMore"));
        this.mEnable.setChecked(this.mSp.getBoolean(VehicleVoiceEngine.PREF_ENABLED, false));
        checkBox.setChecked(this.mSp.getBoolean(VehicleVoiceEngine.PREF_GEAR, true));
        checkBox2.setChecked(this.mSp.getBoolean(VehicleVoiceEngine.PREF_DOOR_DRIVER, true));
        checkBox3.setChecked(this.mSp.getBoolean(VehicleVoiceEngine.PREF_DOOR_PASSENGER, true));
        checkBox4.setChecked(this.mSp.getBoolean(VehicleVoiceEngine.PREF_DOOR_RL, true));
        checkBox5.setChecked(this.mSp.getBoolean(VehicleVoiceEngine.PREF_DOOR_RR, true));
        checkBox6.setChecked(this.mSp.getBoolean(VehicleVoiceEngine.PREF_DOOR_OPEN_ONLY, false));
        checkBox7.setChecked(this.mSp.getBoolean(VehicleVoiceEngine.PREF_TURN, true));
        if (checkBox8 != null) {
            checkBox8.setChecked(this.mSp.getBoolean(PREF_BOOT_HINT, true));
            checkBox8.setOnClickListener(new View.OnClickListener() { // from class: com.carassistant.voice.MainActivity.1
                @Override // android.view.View.OnClickListener
                public void onClick(View view) {
                    MainActivity.this.mSp.edit().putBoolean(MainActivity.PREF_BOOT_HINT, checkBox8.isChecked()).apply();
                }
            });
        }
        wireToggle(this.mEnable, VehicleVoiceEngine.PREF_ENABLED);
        wireToggle(checkBox, VehicleVoiceEngine.PREF_GEAR);
        wireToggle(checkBox2, VehicleVoiceEngine.PREF_DOOR_DRIVER);
        wireToggle(checkBox3, VehicleVoiceEngine.PREF_DOOR_PASSENGER);
        wireToggle(checkBox4, VehicleVoiceEngine.PREF_DOOR_RL);
        wireToggle(checkBox5, VehicleVoiceEngine.PREF_DOOR_RR);
        wireToggle(checkBox6, VehicleVoiceEngine.PREF_DOOR_OPEN_ONLY);
        wireToggle(checkBox7, VehicleVoiceEngine.PREF_TURN);
        wireVolumeBoost();
        findViewById(Res.id(this, "diagBtn")).setOnClickListener(new View.OnClickListener() { // from class: com.carassistant.voice.MainActivity.2
            @Override // android.view.View.OnClickListener
            public void onClick(View view) {
                MainActivity.this.startActivity(new Intent(MainActivity.this, (Class<?>) DiagActivity.class));
            }
        });
        View viewFindViewById = findViewById(Res.id(this, "aboutBtn"));
        if (viewFindViewById != null) {
            viewFindViewById.setOnClickListener(new View.OnClickListener() { // from class: com.carassistant.voice.MainActivity.3
                @Override // android.view.View.OnClickListener
                public void onClick(View view) {
                    MainActivity.this.startActivity(new Intent(MainActivity.this, (Class<?>) AboutActivity.class));
                }
            });
        }
        View viewFindViewById2 = findViewById(Res.id(this, "openA11yBtn"));
        if (viewFindViewById2 != null) {
            viewFindViewById2.setOnClickListener(new View.OnClickListener() { // from class: com.carassistant.voice.MainActivity.4
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
        this.mTabGear.setOnClickListener(new View.OnClickListener() { // from class: com.carassistant.voice.MainActivity.5
            @Override // android.view.View.OnClickListener
            public void onClick(View view) {
                MainActivity.this.showTab(0);
            }
        });
        this.mTabDoor.setOnClickListener(new View.OnClickListener() { // from class: com.carassistant.voice.MainActivity.6
            @Override // android.view.View.OnClickListener
            public void onClick(View view) {
                MainActivity.this.showTab(1);
            }
        });
        this.mTabMore.setOnClickListener(new View.OnClickListener() { // from class: com.carassistant.voice.MainActivity.7
            @Override // android.view.View.OnClickListener
            public void onClick(View view) {
                MainActivity.this.showTab(2);
            }
        });
        rebuildEventRows();
        showTab(0);
        refreshStatus();
    }

    @Override // android.app.Activity
    protected void onResume() {
        super.onResume();
        refreshEventLabels();
        refreshStatus();
    }

    @Override // android.app.Activity
    protected void onActivityResult(int i, int i2, Intent intent) {
        super.onActivityResult(i, i2, intent);
        if (i == REQ_PICK) {
            refreshEventLabels();
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void showTab(int i) {
        this.mTab = i;
        this.mPageGear.setVisibility(i == 0 ? 0 : 8);
        this.mPageDoor.setVisibility(i == 1 ? 0 : 8);
        this.mPageMore.setVisibility(i == 2 ? 0 : 8);
        styleTab(this.mTabGear, i == 0);
        styleTab(this.mTabDoor, i == 1);
        styleTab(this.mTabMore, i == 2);
    }

    private void styleTab(TextView textView, boolean z) {
        if (textView == null) {
            return;
        }
        int identifier = getResources().getIdentifier(z ? "cv_tab_on" : "cv_tab_off", "drawable", getPackageName());
        if (identifier != 0) {
            textView.setBackgroundResource(identifier);
        }
        textView.setTextColor(getResources().getColor(getResources().getIdentifier(z ? "cv_text" : "cv_muted", "color", getPackageName())));
        textView.getPaint().setFakeBoldText(z);
    }

    private void rebuildEventRows() {
        this.mGearHost.removeAllViews();
        this.mDoorHost.removeAllViews();
        LinearLayout linearLayout = this.mTurnHost;
        if (linearLayout != null) {
            linearLayout.removeAllViews();
        }
        VoiceEvents.Event[] eventArrAll = VoiceEvents.all();
        LayoutInflater layoutInflaterFrom = LayoutInflater.from(this);
        for (VoiceEvents.Event event : eventArrAll) {
            LinearLayout linearLayoutHostFor = hostFor(event.key);
            if (linearLayoutHostFor != null) {
                View viewInflate = layoutInflaterFrom.inflate(Res.layout(this, "item_event"), (ViewGroup) linearLayoutHostFor, false);
                TextView textView = (TextView) viewInflate.findViewById(Res.id(this, "eventTitle"));
                TextView textView2 = (TextView) viewInflate.findViewById(Res.id(this, "eventClip"));
                Button button = (Button) viewInflate.findViewById(Res.id(this, "eventPreview"));
                Button button2 = (Button) viewInflate.findViewById(Res.id(this, "eventPick"));
                textView.setText(event.title);
                textView2.setTag(event.key);
                textView2.setText(VoiceLibrary.displayName(this, event.key));
                button.setOnClickListener(new View.OnClickListener() { // from class: com.carassistant.voice.MainActivity.8
                    @Override // android.view.View.OnClickListener
                    public void onClick(View view) {
                        Toast.makeText(MainActivity.this, VehicleVoiceEngine.playEventPreview(MainActivity.this, event.key), 0).show();
                    }
                });
                button2.setOnClickListener(new View.OnClickListener() { // from class: com.carassistant.voice.MainActivity.9
                    @Override // android.view.View.OnClickListener
                    public void onClick(View view) {
                        MainActivity mainActivity = MainActivity.this;
                        mainActivity.startActivityForResult(ClipPickerActivity.intent(mainActivity, event.key), MainActivity.REQ_PICK);
                    }
                });
                linearLayoutHostFor.addView(viewInflate);
            }
        }
    }

    private LinearLayout hostFor(String str) {
        if (str.startsWith("gear_")) {
            return this.mGearHost;
        }
        if (str.startsWith("door_")) {
            return this.mDoorHost;
        }
        if (VoiceEvents.TURN_LEFT.equals(str) || VoiceEvents.TURN_RIGHT.equals(str) || VoiceEvents.HAZARD.equals(str)) {
            return this.mTurnHost;
        }
        return null;
    }

    private void refreshEventLabels() {
        refreshHostLabels(this.mGearHost);
        refreshHostLabels(this.mDoorHost);
        refreshHostLabels(this.mTurnHost);
    }

    private void refreshHostLabels(LinearLayout linearLayout) {
        if (linearLayout == null) {
            return;
        }
        for (int i = 0; i < linearLayout.getChildCount(); i++) {
            TextView textView = (TextView) linearLayout.getChildAt(i).findViewById(Res.id(this, "eventClip"));
            if (textView != null && (textView.getTag() instanceof String)) {
                textView.setText(VoiceLibrary.displayName(this, (String) textView.getTag()));
            }
        }
    }

    private void wireToggle(final CheckBox checkBox, final String str) {
        checkBox.setOnClickListener(new View.OnClickListener() { // from class: com.carassistant.voice.MainActivity.10
            @Override // android.view.View.OnClickListener
            public void onClick(View view) {
                MainActivity.this.mSp.edit().putBoolean(str, checkBox.isChecked()).apply();
                MainActivity.this.refreshStatus();
            }
        });
    }

    private void wireVolumeBoost() {
        this.mVolBoost = (CheckBox) findViewById(Res.id(this, "volBoostCB"));
        this.mVolLevelRow = findViewById(Res.id(this, "volLevelRow"));
        this.mVolMid = (TextView) findViewById(Res.id(this, "volMid"));
        this.mVolMidHigh = (TextView) findViewById(Res.id(this, "volMidHigh"));
        this.mVolMax = (TextView) findViewById(Res.id(this, "volMax"));
        if (this.mVolBoost == null) {
            return;
        }
        boolean z = this.mSp.getBoolean(VehicleVoiceEngine.PREF_VOL_BOOST, false);
        this.mVolBoost.setChecked(z);
        View view = this.mVolLevelRow;
        if (view != null) {
            view.setVisibility(z ? 0 : 8);
        }
        refreshVolLevelUi();
        this.mVolBoost.setOnClickListener(new View.OnClickListener() { // from class: com.carassistant.voice.MainActivity.11
            @Override // android.view.View.OnClickListener
            public void onClick(View view2) {
                boolean zIsChecked = MainActivity.this.mVolBoost.isChecked();
                MainActivity.this.mSp.edit().putBoolean(VehicleVoiceEngine.PREF_VOL_BOOST, zIsChecked).apply();
                if (MainActivity.this.mVolLevelRow != null) {
                    MainActivity.this.mVolLevelRow.setVisibility(zIsChecked ? 0 : 8);
                }
            }
        });
        View.OnClickListener onClickListener = new View.OnClickListener() { // from class: com.carassistant.voice.MainActivity.12
            @Override // android.view.View.OnClickListener
            public void onClick(View view2) {
                int i;
                if (view2 != MainActivity.this.mVolMid) {
                    if (view2 != MainActivity.this.mVolMax) {
                        i = 1;
                    } else {
                        i = 2;
                    }
                } else {
                    i = 0;
                }
                MainActivity.this.mSp.edit().putInt(VehicleVoiceEngine.PREF_VOL_LEVEL, i).apply();
                MainActivity.this.refreshVolLevelUi();
            }
        };
        TextView textView = this.mVolMid;
        if (textView != null) {
            textView.setOnClickListener(onClickListener);
        }
        TextView textView2 = this.mVolMidHigh;
        if (textView2 != null) {
            textView2.setOnClickListener(onClickListener);
        }
        TextView textView3 = this.mVolMax;
        if (textView3 != null) {
            textView3.setOnClickListener(onClickListener);
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void refreshVolLevelUi() {
        int i = this.mSp.getInt(VehicleVoiceEngine.PREF_VOL_LEVEL, 1);
        styleVolChip(this.mVolMid, i == 0);
        styleVolChip(this.mVolMidHigh, i == 1);
        styleVolChip(this.mVolMax, i == 2);
    }

    private void styleVolChip(TextView textView, boolean z) {
        if (textView == null) {
            return;
        }
        int identifier = getResources().getIdentifier(z ? "cv_tab_on" : "cv_tab_off", "drawable", getPackageName());
        int identifier2 = getResources().getIdentifier(z ? "cv_text" : "cv_muted", "color", getPackageName());
        if (identifier != 0) {
            textView.setBackgroundResource(identifier);
        }
        if (identifier2 != 0) {
            textView.setTextColor(getResources().getColor(identifier2));
        }
        textView.setTypeface(null, z ? 1 : 0);
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void refreshStatus() {
        boolean z = this.mSp.getBoolean(VehicleVoiceEngine.PREF_ENABLED, false);
        boolean zIsAccessibilityEnabled = isAccessibilityEnabled();
        View view = this.mA11yBlock;
        if (view != null) {
            view.setVisibility(zIsAccessibilityEnabled ? 8 : 0);
        }
        if (!zIsAccessibilityEnabled) {
            this.mStatus.setText(Res.string(this, "status_a11y_off"));
        } else if (z) {
            this.mStatus.setText(Res.string(this, "status_on"));
        } else {
            this.mStatus.setText(Res.string(this, "status_off"));
        }
    }

    private boolean isAccessibilityEnabled() {
        String string = Settings.Secure.getString(getContentResolver(), "enabled_accessibility_services");
        if (TextUtils.isEmpty(string)) {
            return false;
        }
        String str = getPackageName() + "/" + VoiceAccessibilityService.class.getName();
        String str2 = getPackageName() + "/.VoiceAccessibilityService";
        TextUtils.SimpleStringSplitter simpleStringSplitter = new TextUtils.SimpleStringSplitter(':');
        simpleStringSplitter.setString(string);
        while (simpleStringSplitter.hasNext()) {
            String next = simpleStringSplitter.next();
            if (str.equalsIgnoreCase(next) || str2.equalsIgnoreCase(next) || next.endsWith("VoiceAccessibilityService")) {
                return true;
            }
        }
        return false;
    }
}
