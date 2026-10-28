package com.carassistant.voice;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

/* JADX INFO: loaded from: classes.dex */
public class DiagActivity extends Activity {
    private TextView mResult;

    private interface DiagJob {
        String run();
    }

    @Override // android.app.Activity
    protected void onCreate(Bundle bundle) {
        super.onCreate(bundle);
        setContentView(Res.layout(this, "activity_diag"));
        this.mResult = (TextView) findViewById(Res.id(this, "diagResult"));
        findViewById(Res.id(this, "backBtn")).setOnClickListener(new View.OnClickListener() { // from class: com.carassistant.voice.DiagActivity.1
            @Override // android.view.View.OnClickListener
            public void onClick(View view) {
                DiagActivity.this.finish();
            }
        });
        findViewById(Res.id(this, "doorDiagBtn")).setOnClickListener(new View.OnClickListener() { // from class: com.carassistant.voice.DiagActivity.2
            @Override // android.view.View.OnClickListener
            public void onClick(View view) {
                DiagActivity.this.runDiag("诊断中，约3秒…", "door-diag", new DiagJob() { // from class: com.carassistant.voice.DiagActivity.2.1
                    @Override // com.carassistant.voice.DiagActivity.DiagJob
                    public String run() {
                        return VehicleVoiceEngine.diagnoseDoors(DiagActivity.this);
                    }
                });
            }
        });
        findViewById(Res.id(this, "gearDiagBtn")).setOnClickListener(new View.OnClickListener() { // from class: com.carassistant.voice.DiagActivity.3
            @Override // android.view.View.OnClickListener
            public void onClick(View view) {
                DiagActivity.this.runDiag("读取档位…", "gear-diag", new DiagJob() { // from class: com.carassistant.voice.DiagActivity.3.1
                    @Override // com.carassistant.voice.DiagActivity.DiagJob
                    public String run() {
                        return VehicleVoiceEngine.diagnoseGear(DiagActivity.this);
                    }
                });
            }
        });
        findViewById(Res.id(this, "turnDiagBtn")).setOnClickListener(new View.OnClickListener() { // from class: com.carassistant.voice.DiagActivity.4
            @Override // android.view.View.OnClickListener
            public void onClick(View view) {
                Toast.makeText(DiagActivity.this, "请拨左/右转向或开双闪，采样约4秒…", 1).show();
                DiagActivity.this.runDiag("转向采样中，请拨杆/双闪…", "turn-diag", new DiagJob() { // from class: com.carassistant.voice.DiagActivity.4.1
                    @Override // com.carassistant.voice.DiagActivity.DiagJob
                    public String run() {
                        return VehicleVoiceEngine.diagnoseTurn(DiagActivity.this);
                    }
                });
            }
        });
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void runDiag(String str, String str2, final DiagJob diagJob) {
        Toast.makeText(this, str, 0).show();
        this.mResult.setText(str);
        new Thread(new Runnable() { // from class: com.carassistant.voice.DiagActivity.5
            @Override // java.lang.Runnable
            public void run() {
                final String strRun = diagJob.run();
                DiagActivity.this.runOnUiThread(new Runnable() { // from class: com.carassistant.voice.DiagActivity.5.1
                    @Override // java.lang.Runnable
                    public void run() {
                        DiagActivity.this.mResult.setText(strRun);
                    }
                });
            }
        }, str2).start();
    }
}
