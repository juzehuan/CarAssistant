package com.carassistant.ambient;

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

    /* JADX WARN: Multi-variable type inference failed */
    /* JADX WARN: Type inference failed for: r16v0 */
    /* JADX WARN: Type inference failed for: r16v1 */
    /* JADX WARN: Type inference failed for: r16v2 */
    /* JADX WARN: Type inference failed for: r16v3 */
    /* JADX WARN: Type inference failed for: r16v4 */
    /* JADX WARN: Type inference failed for: r16v5 */
    /* JADX WARN: Type inference failed for: r1v12, types: [android.view.ViewGroup, android.widget.LinearLayout] */
    /* JADX WARN: Type inference failed for: r3v1 */
    /* JADX WARN: Type inference failed for: r3v13 */
    /* JADX WARN: Type inference failed for: r3v14 */
    /* JADX WARN: Type inference failed for: r3v15 */
    /* JADX WARN: Type inference failed for: r3v2, types: [android.view.LayoutInflater] */
    /* JADX WARN: Type inference failed for: r3v3 */
    @Override // android.app.Activity
    protected void onCreate(Bundle bundle) {
        LayoutInflater r16 = null;
        LayoutInflater r17 = null;
        AboutActivity aboutActivity = this;
        super.onCreate(bundle);
        aboutActivity.setContentView(Res.layout(aboutActivity, "qg_activity_about"));
        TextView textView = (TextView) aboutActivity.findViewById(Res.id(aboutActivity, "aboutVersion"));
        boolean z = false;
        if (textView != null) {
            int iString = Res.string(aboutActivity, "qg_about_version_fmt");
            if (iString != 0) {
                textView.setText(aboutActivity.getString(iString, new Object[]{readVersionName()}));
            } else {
                textView.setText("版本 " + readVersionName());
            }
        }
        View viewFindViewById = aboutActivity.findViewById(Res.id(aboutActivity, "backBtn"));
        if (viewFindViewById != null) {
            viewFindViewById.setOnClickListener(new View.OnClickListener() { // from class: com.carassistant.ambient.AboutActivity.1
                @Override // android.view.View.OnClickListener
                public void onClick(View view) {
                    AboutActivity.this.finish();
                }
            });
        }
        LinearLayout r1 = (LinearLayout) aboutActivity.findViewById(Res.id(aboutActivity, "aboutTeamHost"));
        if (r1 != null) {
            LayoutInflater layoutInflaterFrom = LayoutInflater.from(this);
            int iLayout = Res.layout(aboutActivity, "qg_item_about_member");
            int iId = Res.id(aboutActivity, "memberAvatar");
            int iId2 = Res.id(aboutActivity, "memberName");
            int identifier = getResources().getIdentifier("qg_warm", "color", getPackageName());
            int color = identifier != 0 ? getResources().getColor(identifier) : -30147;
            int i = 0;
            LayoutInflater r3 = layoutInflaterFrom;
            while (true) {
                RoleGroup[] roleGroupArr = TEAMS;
                if (i < roleGroupArr.length) {
                    RoleGroup roleGroup = roleGroupArr[i];
                    TextView textView2 = new TextView(aboutActivity);
                    textView2.setText(roleGroup.title);
                    textView2.setTextColor(color);
                    textView2.setTextSize(14.0f);
                    LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(-1, -2);
                    layoutParams.topMargin = aboutActivity.dp(i > 0 ? 14 : 4);
                    textView2.setLayoutParams(layoutParams);
                    r1.addView(textView2);
                    int i2 = 0;
                    LayoutInflater r4 = r3;
                    while (i2 < roleGroup.members.length) {
                        Member member = roleGroup.members[i2];
                        View viewInflate = r4.inflate(iLayout, r1, z);
                        ImageView imageView = (ImageView) viewInflate.findViewById(iId);
                        TextView textView3 = (TextView) viewInflate.findViewById(iId2);
                        if (imageView == null) {
                            r16 = r4;
                        } else {
                            r17 = r4;
                            int identifier2 = getResources().getIdentifier(member.avatar, "drawable", getPackageName());
                            if (identifier2 != 0) {
                                r16 = r17;
                                imageView.setImageResource(identifier2);
                                r16 = r17;
                            }
                        }
                        if (textView3 != null) {
                            textView3.setText(member.name);
                        }
                        r1.addView(viewInflate);
                        i2++;
                        z = false;
                        r4 = r16;
                    }
                    i++;
                    z = false;
                    aboutActivity = this;
                    r3 = r4;
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
