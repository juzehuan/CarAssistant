package com.carassistant.voice;

import android.app.Activity;
import android.content.pm.PackageInfo;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/* JADX INFO: loaded from: classes.dex */
public class AboutActivity extends Activity {
    private static final RoleGroup[] TEAMS = {new RoleGroup("开发者与设计师", new Member[]{new Member("冰鑫", "avatar_bingxin")}), new RoleGroup("开发者", new Member[]{new Member("大阳", "avatar_dayang")}), new RoleGroup("协助开发", new Member[]{new Member("海洋", "avatar_haiyang")}), new RoleGroup("测试成员", new Member[]{new Member("海洋", "avatar_haiyang"), new Member("浪漫吉他", "avatar_langman"), new Member("春序", "avatar_chunxu")}), new RoleGroup("建议优化成员", new Member[]{new Member("三", "avatar_san"), new Member("Mr.Milk", "avatar_mrmilk"), new Member("滄桑隱身", "avatar_cangsang"), new Member("黑蜘蛛", "avatar_heizhizhu")})};

    private static final class Member {
        final String avatar;
        final String name;

        Member(String str, String str2) {
            this.name = str;
            this.avatar = str2;
        }
    }

    private static final class RoleGroup {
        final Member[] members;
        final String title;

        RoleGroup(String str, Member[] memberArr) {
            this.title = str;
            this.members = memberArr;
        }
    }

    @Override // android.app.Activity
    protected void onCreate(Bundle bundle) {
        int identifier;
        AboutActivity aboutActivity = this;
        super.onCreate(bundle);
        aboutActivity.setContentView(Res.layout(aboutActivity, "cv_activity_about"));
        TextView textView = (TextView) aboutActivity.findViewById(Res.id(aboutActivity, "aboutVersion"));
        boolean z = false;
        if (textView != null) {
            textView.setText(aboutActivity.getString(Res.string(aboutActivity, "cv_about_version_fmt"), new Object[]{readVersionName()}));
        }
        aboutActivity.findViewById(Res.id(aboutActivity, "backBtn")).setOnClickListener(new View.OnClickListener() { // from class: com.carassistant.voice.AboutActivity.1
            @Override // android.view.View.OnClickListener
            public void onClick(View view) {
                AboutActivity.this.finish();
            }
        });
        LinearLayout linearLayout = (LinearLayout) aboutActivity.findViewById(Res.id(aboutActivity, "aboutTeamHost"));
        if (linearLayout != null) {
            LayoutInflater layoutInflaterFrom = LayoutInflater.from(this);
            int iLayout = Res.layout(aboutActivity, "cv_item_about_member");
            int iId = Res.id(aboutActivity, "memberAvatar");
            int iId2 = Res.id(aboutActivity, "memberName");
            int i = 0;
            while (true) {
                RoleGroup[] roleGroupArr = TEAMS;
                if (i < roleGroupArr.length) {
                    RoleGroup roleGroup = roleGroupArr[i];
                    TextView textView2 = new TextView(aboutActivity);
                    textView2.setText(roleGroup.title);
                    textView2.setTextColor(getResources().getColor(getResources().getIdentifier("cv_accent", "color", getPackageName())));
                    textView2.setTextSize(14.0f);
                    LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(-1, -2);
                    if (i > 0) {
                        layoutParams.topMargin = aboutActivity.dp(14);
                    } else {
                        layoutParams.topMargin = aboutActivity.dp(4);
                    }
                    textView2.setLayoutParams(layoutParams);
                    linearLayout.addView(textView2);
                    int i2 = 0;
                    while (i2 < roleGroup.members.length) {
                        Member member = roleGroup.members[i2];
                        View viewInflate = layoutInflaterFrom.inflate(iLayout, linearLayout, z);
                        ImageView imageView = (ImageView) viewInflate.findViewById(iId);
                        TextView textView3 = (TextView) viewInflate.findViewById(iId2);
                        if (imageView != null && (identifier = getResources().getIdentifier(member.avatar, "drawable", getPackageName())) != 0) {
                            imageView.setImageResource(identifier);
                        }
                        if (textView3 != null) {
                            textView3.setText(member.name);
                        }
                        linearLayout.addView(viewInflate);
                        i2++;
                        z = false;
                    }
                    i++;
                    z = false;
                    aboutActivity = this;
                } else {
                    return;
                }
            }
        }
    }

    private String readVersionName() {
        try {
            PackageInfo packageInfo = getPackageManager().getPackageInfo(getPackageName(), 0);
            if (packageInfo != null && packageInfo.versionName != null && packageInfo.versionName.length() > 0) {
                return packageInfo.versionName;
            }
            return "1.0";
        } catch (Throwable th) {
            return "1.0";
        }
    }

    private int dp(int i) {
        return (int) ((i * getResources().getDisplayMetrics().density) + 0.5f);
    }
}
