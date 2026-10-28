package com.example.hello;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

// "Call back later" reminders. They are stored so they can be re-armed after a reboot or an app update,
// and so the person can see and cancel them.
public class Reminders {
    static SharedPreferences sp(Context c) {
        return c.getSharedPreferences("reminders", Context.MODE_PRIVATE);
    }

    static PendingIntent pending(Context c, int id, String who, String number) {
        Intent in = new Intent(c, CallReceiver.class).setAction("REMIND")
                .putExtra("who", who).putExtra("number", number).putExtra("id", id);
        return PendingIntent.getBroadcast(c, id, in,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    static void arm(Context c, int id, long at, String who, String number) {
        AlarmManager am = c.getSystemService(AlarmManager.class);
        if (am == null) return;
        // works in Doze; may fire a little late, which is fine for a call-back nudge
        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending(c, id, who, number));
    }

    public static int add(Context c, long at, String who, String number) {
        SharedPreferences p = sp(c);
        int id = (int) ((at / 1000) % 1000000);
        while (p.contains(String.valueOf(id))) id++;
        p.edit().putString(String.valueOf(id),
                at + "\n" + (who == null ? "" : who.replace('\n', ' ')) + "\n" + (number == null ? "" : number)).apply();
        arm(c, id, at, who, number);
        return id;
    }

    // called once a reminder has fired
    public static void done(Context c, int id) {
        sp(c).edit().remove(String.valueOf(id)).apply();
    }

    public static void cancel(Context c, int id) {
        String v = sp(c).getString(String.valueOf(id), null);
        done(c, id);
        AlarmManager am = c.getSystemService(AlarmManager.class);
        if (am == null) return;
        String[] f = v == null ? new String[]{"0", "", ""} : v.split("\n", -1);
        am.cancel(pending(c, id, f.length > 1 ? f[1] : "", f.length > 2 ? f[2] : ""));
    }

    // each item: {id, at, who, number}, soonest first
    public static List<String[]> list(Context c) {
        List<String[]> out = new ArrayList<>();
        for (Map.Entry<String, ?> e : sp(c).getAll().entrySet()) {
            if (!(e.getValue() instanceof String)) continue;
            String[] f = ((String) e.getValue()).split("\n", -1);
            if (f.length < 3) continue;
            out.add(new String[]{e.getKey(), f[0], f[1], f[2]});
        }
        java.util.Collections.sort(out, (a, b) -> {
            try { return Long.compare(Long.parseLong(a[1]), Long.parseLong(b[1])); }
            catch (NumberFormatException x) { return 0; }
        });
        return out;
    }

    // after a reboot or app update: anything still pending is armed again; overdue ones fire shortly
    public static void rearmAll(Context c) {
        long now = System.currentTimeMillis();
        for (String[] r : list(c)) {
            try {
                arm(c, Integer.parseInt(r[0]), Math.max(Long.parseLong(r[1]), now + 3000), r[2], r[3]);
            } catch (NumberFormatException ignored) { }
        }
    }
}
