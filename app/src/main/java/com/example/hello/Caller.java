package com.example.hello;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.telecom.PhoneAccount;
import android.telecom.PhoneAccountHandle;
import android.telecom.TelecomManager;
import android.widget.Toast;
import java.util.ArrayList;
import java.util.List;

public class Caller {
    public static String pending;

    static List<PhoneAccountHandle> accounts(Activity a) {
        List<PhoneAccountHandle> out = new ArrayList<>();
        try {
            TelecomManager tm = (TelecomManager) a.getSystemService(Activity.TELECOM_SERVICE);
            if (tm != null && a.checkSelfPermission(Manifest.permission.READ_PHONE_STATE)
                    == PackageManager.PERMISSION_GRANTED) {
                out.addAll(tm.getCallCapablePhoneAccounts());
            }
        } catch (Throwable e) { out.clear(); }
        return out;
    }

    public static int simCount(Activity a) { return accounts(a).size(); }

    static String assisted(Activity a, String n) {
        SharedPreferences sp = a.getSharedPreferences("app", Activity.MODE_PRIVATE);
        if (!sp.getBoolean("assist_on", false)) return n;
        String t = n.replaceAll("[\\s\\-()]", "");
        if (!t.matches("[0-9]{10}") || t.startsWith("0")) return n;
        return sp.getString("assist_cc", "+91") + t;
    }

    static String card(Activity a, String n) {
        SharedPreferences sp = a.getSharedPreferences("app", Activity.MODE_PRIVATE);
        String c = sp.getString("card_num", "");
        if (!sp.getBoolean("card_on", false) || c.isEmpty()) return n;
        if (n.startsWith("*") || n.startsWith("#") || n.replaceAll("[^0-9]", "").length() < 6) return n;
        return c + n;
    }

    public static void dial(Activity a, String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            Toast.makeText(a, "Enter a number first", Toast.LENGTH_SHORT).show();
            return;
        }
        if (a.checkSelfPermission(Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED) {
            pending = raw;
            Toast.makeText(a, "Allow the Phone permission to place calls", Toast.LENGTH_LONG).show();
            a.requestPermissions(new String[]{Manifest.permission.CALL_PHONE}, 77);
            return;
        }
        pending = null;
        final String number = card(a, assisted(a, raw));
        final List<PhoneAccountHandle> accts = accounts(a);
        if (accts.size() > 1) {
            int i = a.getSharedPreferences("app", Activity.MODE_PRIVATE).getInt("sim", 0);
            if (i == -1) {
                String[] labels = new String[accts.size()];
                try {
                    TelecomManager tm = (TelecomManager) a.getSystemService(Activity.TELECOM_SERVICE);
                    for (int k = 0; k < labels.length; k++) {
                        PhoneAccount pa = tm.getPhoneAccount(accts.get(k));
                        CharSequence l = pa != null ? pa.getLabel() : null;
                        labels[k] = "SIM " + (k + 1) + (l != null && l.length() > 0 ? "  ·  " + l : "");
                    }
                } catch (Throwable e) {
                    for (int k = 0; k < labels.length; k++) labels[k] = "SIM " + (k + 1);
                }
                new AlertDialog.Builder(a).setTitle("Call with")
                        .setItems(labels, (d, w) -> start(a, number, accts.get(w))).show();
            } else {
                if (i >= accts.size()) i = 0;
                start(a, number, accts.get(i));
            }
        } else {
            start(a, number, null);
        }
    }

    static void start(Activity a, String number, PhoneAccountHandle h) {
        try {
            Intent i = new Intent(Intent.ACTION_CALL, Uri.fromParts("tel", number, null));
            if (h != null) i.putExtra(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE, h);
            a.startActivity(i);
        } catch (Throwable e) {
            Toast.makeText(a, "Couldn't place the call", Toast.LENGTH_LONG).show();
        }
    }
}
