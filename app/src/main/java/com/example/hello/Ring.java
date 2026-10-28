package com.example.hello;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;

public class Ring {
    public static final String[] VIBE_NAMES = {"Default", "Short", "Long", "Pulse"};
    public static final long[][] VIBE = {
        {0, 800, 800}, {0, 300, 500}, {0, 1500, 1000}, {0, 200, 150, 200, 150, 200, 900}};

    static SharedPreferences cp(Context c) { return c.getSharedPreferences("cring", Context.MODE_PRIVATE); }

    public static String contact(Context c, String n) {
        String k = Favs.key(n);
        return k.isEmpty() ? "" : cp(c).getString(k, "");
    }

    public static void setContact(Context c, String n, String tok) {
        String k = Favs.key(n);
        if (k.isEmpty()) return;
        if (tok == null || tok.isEmpty()) cp(c).edit().remove(k).apply();
        else cp(c).edit().putString(k, tok).apply();
    }

    // "" = phone default, "silent" = no sound, otherwise a uri string
    public static String pick(Context c, String n) {
        String v = contact(c, n);
        if (!v.isEmpty()) return v;
        return c.getSharedPreferences("app", Context.MODE_PRIVATE).getString("ringtone_uri", "");
    }

    public static String title(Context c, String tok) {
        if (tok == null || tok.isEmpty()) return "Default";
        if ("silent".equals(tok)) return "Silent";
        try {
            Ringtone r = RingtoneManager.getRingtone(c, Uri.parse(tok));
            if (r != null) return r.getTitle(c);
        } catch (Throwable e) { }
        return "Custom";
    }

    public static Intent pickerIntent(String existing) {
        Intent in = new Intent(RingtoneManager.ACTION_RINGTONE_PICKER);
        in.putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_RINGTONE);
        in.putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true);
        in.putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, true);
        if (existing != null && !existing.isEmpty() && !"silent".equals(existing)) {
            in.putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, Uri.parse(existing));
        }
        return in;
    }

    public static String fromResult(Intent d) {
        Uri u = d.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI);
        return u == null ? "silent" : u.toString();
    }
}
