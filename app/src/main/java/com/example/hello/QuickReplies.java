package com.example.hello;

import android.content.Context;

public class QuickReplies {
    static final String[] DEF = {"Can't talk now. I'll call you back.",
            "I'm in a meeting. Call you later.", "Can you text me instead?"};

    // first 3 are editable, the 4th is always "Write my own message"
    public static String[] get(Context c) {
        android.content.SharedPreferences sp = c.getSharedPreferences("app", Context.MODE_PRIVATE);
        String[] out = new String[4];
        for (int i = 0; i < 3; i++) out[i] = sp.getString("qr_" + i, DEF[i]);
        out[3] = "Write my own message…";
        return out;
    }

    public static void set(Context c, int i, String t) {
        c.getSharedPreferences("app", Context.MODE_PRIVATE).edit().putString("qr_" + i, t).apply();
    }

    public static void reset(Context c) {
        android.content.SharedPreferences.Editor e = c.getSharedPreferences("app", Context.MODE_PRIVATE).edit();
        for (int i = 0; i < 3; i++) e.remove("qr_" + i);
        e.apply();
    }
}
