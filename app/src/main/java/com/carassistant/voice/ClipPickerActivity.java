package com.carassistant.voice;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;
import java.util.ArrayList;
import java.util.List;

/* JADX INFO: loaded from: classes.dex */
public class ClipPickerActivity extends Activity {
    public static final String EXTRA_EVENT = "event_key";
    private static final int MODE_CLIPS = 1;
    private static final int MODE_FOLDERS = 0;
    private static final int MODE_SEARCH = 2;
    private PickerAdapter mAdapter;
    private String mEventKey;
    private TextView mHint;
    private String mOpenPackId;
    private String mOpenPackName;
    private EditText mSearch;
    private TextView mTitle;
    private int mMode = 0;
    private final List<VoiceLibrary.FolderInfo> mFolders = new ArrayList();
    private final List<VoiceClip> mClips = new ArrayList();

    public static Intent intent(Context context, String str) {
        Intent intent = new Intent(context, (Class<?>) ClipPickerActivity.class);
        intent.putExtra(EXTRA_EVENT, str);
        return intent;
    }

    @Override // android.app.Activity
    protected void onCreate(Bundle bundle) {
        super.onCreate(bundle);
        setContentView(Res.layout(this, "activity_clip_picker"));
        String stringExtra = getIntent() != null ? getIntent().getStringExtra(EXTRA_EVENT) : null;
        this.mEventKey = stringExtra;
        VoiceEvents.Event eventFind = VoiceEvents.find(stringExtra);
        TextView textView = (TextView) findViewById(Res.id(this, "pickerTitle"));
        this.mTitle = textView;
        if (textView != null) {
            textView.setText(eventFind != null ? "选择音频 · " + eventFind.title : "选择音频");
        }
        findViewById(Res.id(this, "pickerBack")).setOnClickListener(new View.OnClickListener() { // from class: com.carassistant.voice.ClipPickerActivity.1
            @Override // android.view.View.OnClickListener
            public void onClick(View view) {
                ClipPickerActivity.this.onBackNav();
            }
        });
        this.mFolders.clear();
        this.mFolders.addAll(VoiceLibrary.listFolders(this));
        this.mHint = (TextView) findViewById(Res.id(this, "clipSearchHint"));
        ListView listView = (ListView) findViewById(Res.id(this, "clipList"));
        PickerAdapter pickerAdapter = new PickerAdapter();
        this.mAdapter = pickerAdapter;
        listView.setAdapter((ListAdapter) pickerAdapter);
        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() { // from class: com.carassistant.voice.ClipPickerActivity.2
            @Override // android.widget.AdapterView.OnItemClickListener
            public void onItemClick(AdapterView<?> adapterView, View view, int i, long j) {
                if (ClipPickerActivity.this.mMode == 0 && i >= 0 && i < ClipPickerActivity.this.mFolders.size()) {
                    VoiceLibrary.FolderInfo folderInfo = (VoiceLibrary.FolderInfo) ClipPickerActivity.this.mFolders.get(i);
                    ClipPickerActivity.this.openFolder(folderInfo.packId, folderInfo.name);
                }
            }
        });
        EditText editText = (EditText) findViewById(Res.id(this, "clipSearch"));
        this.mSearch = editText;
        if (editText != null) {
            editText.addTextChangedListener(new TextWatcher() { // from class: com.carassistant.voice.ClipPickerActivity.3
                @Override // android.text.TextWatcher
                public void beforeTextChanged(CharSequence charSequence, int i, int i2, int i3) {
                }

                @Override // android.text.TextWatcher
                public void onTextChanged(CharSequence charSequence, int i, int i2, int i3) {
                    ClipPickerActivity.this.applySearch(charSequence != null ? charSequence.toString() : "");
                }

                @Override // android.text.TextWatcher
                public void afterTextChanged(Editable editable) {
                }
            });
        }
        showFolders();
        if (this.mFolders.isEmpty()) {
            Toast.makeText(this, "音频库为空", 0).show();
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void onBackNav() {
        int i = this.mMode;
        if (i == 1 || i == 2) {
            EditText editText = this.mSearch;
            if (editText != null && editText.getText() != null && this.mSearch.getText().length() > 0) {
                this.mSearch.setText("");
            }
            showFolders();
            return;
        }
        finish();
    }

    @Override // android.app.Activity
    public void onBackPressed() {
        onBackNav();
    }

    private void showFolders() {
        this.mMode = 0;
        this.mOpenPackId = null;
        this.mOpenPackName = null;
        this.mClips.clear();
        VoiceEvents.Event eventFind = VoiceEvents.find(this.mEventKey);
        TextView textView = this.mTitle;
        if (textView != null) {
            textView.setText(eventFind != null ? "选择音频 · " + eventFind.title : "选择音频");
        }
        TextView textView2 = this.mHint;
        if (textView2 != null) {
            textView2.setText(this.mFolders.size() + " 个分类 · 点分类进入，点返回退出");
        }
        this.mAdapter.notifyDataSetChanged();
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void openFolder(String str, String str2) {
        this.mMode = 1;
        this.mOpenPackId = str;
        this.mOpenPackName = str2;
        this.mClips.clear();
        this.mClips.addAll(VoiceLibrary.listClipsInPack(this, str));
        TextView textView = this.mTitle;
        if (textView != null) {
            textView.setText(str2);
        }
        TextView textView2 = this.mHint;
        if (textView2 != null) {
            textView2.setText(this.mClips.size() + " 条 · 点「试听」预览，「选用」绑定 · 返回上一级");
        }
        this.mAdapter.notifyDataSetChanged();
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void applySearch(String str) {
        String strTrim = str != null ? str.trim() : "";
        if (strTrim.length() == 0) {
            String str2 = this.mOpenPackId;
            if (str2 != null) {
                openFolder(str2, this.mOpenPackName);
                return;
            } else {
                showFolders();
                return;
            }
        }
        this.mMode = 2;
        this.mClips.clear();
        this.mClips.addAll(VoiceLibrary.searchClips(this, strTrim));
        TextView textView = this.mTitle;
        if (textView != null) {
            textView.setText("搜索结果");
        }
        TextView textView2 = this.mHint;
        if (textView2 != null) {
            textView2.setText("找到 " + this.mClips.size() + " 条 · 清空搜索返回分类");
        }
        this.mAdapter.notifyDataSetChanged();
    }

    @Override // android.app.Activity
    protected void onDestroy() {
        VehicleVoiceEngine.stopPreviewStaticPublic();
        super.onDestroy();
    }

    private final class PickerAdapter extends BaseAdapter {
        private PickerAdapter() {
        }

        @Override // android.widget.Adapter
        public int getCount() {
            return ClipPickerActivity.this.mMode == 0 ? ClipPickerActivity.this.mFolders.size() : ClipPickerActivity.this.mClips.size();
        }

        @Override // android.widget.Adapter
        public Object getItem(int i) {
            return ClipPickerActivity.this.mMode == 0 ? ClipPickerActivity.this.mFolders.get(i) : ClipPickerActivity.this.mClips.get(i);
        }

        @Override // android.widget.Adapter
        public long getItemId(int i) {
            return i;
        }

        @Override // android.widget.BaseAdapter, android.widget.Adapter
        public int getViewTypeCount() {
            return 2;
        }

        @Override // android.widget.BaseAdapter, android.widget.Adapter
        public int getItemViewType(int i) {
            return ClipPickerActivity.this.mMode == 0 ? 0 : 1;
        }

        @Override // android.widget.Adapter
        public View getView(int i, View view, ViewGroup viewGroup) {
            if (ClipPickerActivity.this.mMode == 0) {
                return bindFolder(i, view, viewGroup);
            }
            return bindClip(i, view, viewGroup);
        }

        private View bindFolder(int i, View view, ViewGroup viewGroup) {
            if (view == null || view.findViewById(Res.id(ClipPickerActivity.this, "folderName")) == null) {
                view = LayoutInflater.from(ClipPickerActivity.this).inflate(Res.layout(ClipPickerActivity.this, "item_folder"), viewGroup, false);
            }
            VoiceLibrary.FolderInfo folderInfo = (VoiceLibrary.FolderInfo) ClipPickerActivity.this.mFolders.get(i);
            TextView textView = (TextView) view.findViewById(Res.id(ClipPickerActivity.this, "folderName"));
            TextView textView2 = (TextView) view.findViewById(Res.id(ClipPickerActivity.this, "folderCount"));
            if (textView != null) {
                textView.setText(folderInfo.name);
            }
            if (textView2 != null) {
                textView2.setText(String.valueOf(folderInfo.count));
            }
            return view;
        }

        private View bindClip(int i, View view, ViewGroup viewGroup) {
            boolean z = false;
            if (view == null || view.findViewById(Res.id(ClipPickerActivity.this, "clipName")) == null) {
                view = LayoutInflater.from(ClipPickerActivity.this).inflate(Res.layout(ClipPickerActivity.this, "item_clip"), viewGroup, false);
            }
            final VoiceClip voiceClip = (VoiceClip) ClipPickerActivity.this.mClips.get(i);
            TextView textView = (TextView) view.findViewById(Res.id(ClipPickerActivity.this, "clipName"));
            TextView textView2 = (TextView) view.findViewById(Res.id(ClipPickerActivity.this, "clipMark"));
            Button button = (Button) view.findViewById(Res.id(ClipPickerActivity.this, "clipPreview"));
            Button button2 = (Button) view.findViewById(Res.id(ClipPickerActivity.this, "clipChoose"));
            if (ClipPickerActivity.this.mMode == 2) {
                textView.setText(VoiceLibrary.displayLine(voiceClip));
            } else {
                textView.setText(VoiceLabels.cleanLabel(voiceClip.label));
            }
            ClipPickerActivity clipPickerActivity = ClipPickerActivity.this;
            boolean zEquals = voiceClip.id.equals(VoiceLibrary.getSelectedId(clipPickerActivity, clipPickerActivity.mEventKey));
            textView2.setText(zEquals ? "当前" : "");
            textView2.setVisibility(zEquals ? 0 : 8);
            VoiceEvents.Event eventFind = VoiceEvents.find(ClipPickerActivity.this.mEventKey);
            if (eventFind != null && eventFind.loopPreview) {
                z = true;
            }
            final boolean zFinal = z;
            button.setOnClickListener(new View.OnClickListener() { // from class: com.carassistant.voice.ClipPickerActivity.PickerAdapter.1
                @Override // android.view.View.OnClickListener
                public void onClick(View view2) {
                    Toast.makeText(ClipPickerActivity.this, VehicleVoiceEngine.playPreviewStatic(ClipPickerActivity.this, voiceClip.id, zFinal), 0).show();
                }
            });
            button2.setOnClickListener(new View.OnClickListener() { // from class: com.carassistant.voice.ClipPickerActivity.PickerAdapter.2
                @Override // android.view.View.OnClickListener
                public void onClick(View view2) {
                    VoiceLibrary.setSelectedId(ClipPickerActivity.this, ClipPickerActivity.this.mEventKey, voiceClip.id);
                    Toast.makeText(ClipPickerActivity.this, "已选用 " + VoiceLabels.cleanLabel(voiceClip.label), 0).show();
                    ClipPickerActivity.this.setResult(-1);
                    ClipPickerActivity.this.finish();
                }
            });
            return view;
        }
    }
}
