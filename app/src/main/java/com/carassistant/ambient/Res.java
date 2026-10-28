package com.carassistant.ambient;

import android.content.Context;

/* JADX INFO: loaded from: classes.dex */
public final class Res {
    private Res() {
    }

    public static int id(Context context, String str) {
        return context.getResources().getIdentifier(str, "id", context.getPackageName());
    }

    public static int layout(Context context, String str) {
        return context.getResources().getIdentifier(str, "layout", context.getPackageName());
    }

    public static int string(Context context, String str) {
        return context.getResources().getIdentifier(str, "string", context.getPackageName());
    }
}
