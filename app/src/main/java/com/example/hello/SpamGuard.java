package com.example.hello;

import android.Manifest;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class SpamGuard {
    static SharedPreferences sp(Context c) { return c.getSharedPreferences("spam", Context.MODE_PRIVATE); }
    static SharedPreferences ap(Context c) { return c.getSharedPreferences("app", Context.MODE_PRIVATE); }

    static String digits(String n) { return n == null ? "" : n.replaceAll("[^0-9]", ""); }

    static String key(String n) {
        String d = digits(n);
        return d.length() > 10 ? d.substring(d.length() - 10) : d;
    }

    public static boolean enabled(Context c) { return ap(c).getBoolean("spam_on", true); }

    public static boolean isReported(Context c, String n) {
        String k = key(n);
        return !k.isEmpty() && sp(c).contains("n:" + k);
    }

    public static void report(Context c, String n) {
        String k = key(n);
        if (!k.isEmpty()) sp(c).edit().putString("n:" + k, n).apply();
    }

    public static void unreport(Context c, String fullKey) {
        sp(c).edit().remove(fullKey).apply();
    }

    public static List<String[]> reported(Context c) {
        List<String[]> out = new ArrayList<>();
        for (Map.Entry<String, ?> e : sp(c).getAll().entrySet()) {
            if (e.getKey().startsWith("n:")) out.add(new String[]{e.getKey(), String.valueOf(e.getValue())});
        }
        Collections.sort(out, (a, b) -> a[1].compareTo(b[1]));
        return out;
    }

    public static List<String> keywords(Context c) {
        List<String> out = new ArrayList<>();
        for (String k : sp(c).getAll().keySet()) if (k.startsWith("k:")) out.add(k.substring(2));
        Collections.sort(out);
        return out;
    }

    public static void addKeyword(Context c, String kw) {
        if (kw != null && !kw.trim().isEmpty()) sp(c).edit().putString("k:" + kw.trim(), "1").apply();
    }

    public static void removeKeyword(Context c, String kw) {
        sp(c).edit().remove("k:" + kw).apply();
    }

    static boolean isSaved(Context c, String number) {
        return c.checkSelfPermission(Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED
                && !Ui.lookupName(c, number).isEmpty();
    }

    // returns null if the call is allowed, otherwise the reason it is blocked
    public static String reason(Context c, String number, String name) {
        if (!enabled(c)) return null;
        if (number == null || number.trim().isEmpty()) {
            return ap(c).getBoolean("block_hidden", false) ? "Hidden number" : null;
        }
        if (isReported(c, number)) return "Reported as spam";
        String nd = digits(number);
        String local = (nd.startsWith("91") && nd.length() > 10) ? nd.substring(2) : nd;
        String nm = name == null ? "" : name.toLowerCase(Locale.ROOT);
        for (String kw : keywords(c)) {
            String kd = digits(kw);
            boolean hit = false;
            if (!kd.isEmpty() && kw.matches("[0-9+ ]+")) {
                hit = nd.startsWith(kd) || local.startsWith(kd);
            } else if (!nm.isEmpty() && nm.contains(kw.toLowerCase(Locale.ROOT))) {
                hit = true;
            }
            // Prefix and word rules are for strangers. Someone saved in Contacts is never silenced by them
            // (block that person directly if you really want to).
            if (hit && !isSaved(c, number)) return "Matches rule " + kw;
        }
        if (ap(c).getBoolean("block_unknown", false) && nd.length() >= 6
                && c.checkSelfPermission(Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED
                && Ui.lookupName(c, number).isEmpty()) {
            return "Unknown caller";
        }
        return null;
    }

    public static void logBlocked(Context c, String n, String why) {
        SharedPreferences p = ap(c);
        p.edit().putInt("blocked_count", p.getInt("blocked_count", 0) + 1)
                .putString("blocked_last", ((n == null || n.isEmpty()) ? "Hidden" : n) + "  ·  " + why)
                .apply();
    }
}
