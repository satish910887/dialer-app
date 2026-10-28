package com.example.hello;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.text.InputType;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Toast;
import java.util.ArrayList;
import java.util.List;

public class SpeedDial {
    // the digit that was pressed while the Phone permission was still missing; dialled once it is granted
    public static String pendingKey;

    static SharedPreferences sp(Activity a) { return a.getSharedPreferences("app", Activity.MODE_PRIVATE); }

    public static void press(Activity a, String d) {
        if (d.equals("1")) {
            if (a.checkSelfPermission(Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED) {
                pendingKey = "1";
                a.requestPermissions(new String[]{Manifest.permission.CALL_PHONE}, 2);
                return;
            }
            pendingKey = null;
            try { a.startActivity(new Intent(Intent.ACTION_CALL, Uri.fromParts("voicemail", "", null))); }
            catch (Throwable e) { Toast.makeText(a, "Voicemail is not available on this SIM", Toast.LENGTH_SHORT).show(); }
            return;
        }
        String v = sp(a).getString("sd_" + d, "");
        if (v.isEmpty()) { assign(a, d, null); return; }
        String[] p = v.split("\n", 2);
        Caller.dial(a, p.length > 1 ? p[1] : p[0]);
    }

    public static void assign(Activity a, String d, Runnable done) {
        final List<String[]> favs = Favs.list(a);
        final boolean has = !sp(a).getString("sd_" + d, "").isEmpty();
        final List<String> items = new ArrayList<>();
        for (String[] f : favs) items.add("⭐ " + f[1] + "  ·  " + f[2]);
        items.add("Type a number");
        if (has) items.add("Remove from speed dial");
        new AlertDialog.Builder(a).setTitle("Speed dial " + d)
                .setItems(items.toArray(new String[0]), (dg, i) -> {
                    if (i < favs.size()) {
                        String[] f = favs.get(i);
                        sp(a).edit().putString("sd_" + d, f[1] + "\n" + f[2]).apply();
                        if (done != null) done.run();
                    } else if (i == favs.size()) {
                        typeNumber(a, d, done);
                    } else {
                        sp(a).edit().remove("sd_" + d).apply();
                        if (done != null) done.run();
                    }
                }).setNegativeButton("Cancel", null).show();
    }

    static void typeNumber(Activity a, String d, Runnable done) {
        final EditText e = new EditText(a);
        e.setInputType(InputType.TYPE_CLASS_PHONE);
        e.setHint("Phone number");
        LinearLayout box = new LinearLayout(a);
        box.setPadding(Ui.dp(a, 20), Ui.dp(a, 8), Ui.dp(a, 20), 0);
        box.addView(e, new LinearLayout.LayoutParams(-1, -2));
        new AlertDialog.Builder(a).setTitle("Speed dial " + d).setView(box)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Save", (dg, w) -> {
                    String t = e.getText().toString().trim();
                    if (!t.isEmpty()) {
                        sp(a).edit().putString("sd_" + d, t + "\n" + t).apply();
                        if (done != null) done.run();
                    }
                }).show();
    }

    public static void manage(Activity a) {
        final String[] items = new String[8];
        for (int i = 0; i < 8; i++) {
            String d = String.valueOf(i + 2);
            String v = sp(a).getString("sd_" + d, "");
            items[i] = d + "   ·   " + (v.isEmpty() ? "Not set" : v.split("\n", 2)[0]);
        }
        new AlertDialog.Builder(a).setTitle("Speed dial (1 = voicemail)")
                .setItems(items, (dg, i) -> assign(a, String.valueOf(i + 2), () -> manage(a)))
                .setNegativeButton("Close", null).show();
    }
}
