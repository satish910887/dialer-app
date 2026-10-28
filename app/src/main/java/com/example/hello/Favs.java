package com.example.hello;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Favs {
    static SharedPreferences p(Context c) {
        return c.getSharedPreferences("favs", Context.MODE_PRIVATE);
    }

    static String key(String n) {
        String d = n == null ? "" : n.replaceAll("[^0-9]", "");
        return d.length() > 10 ? d.substring(d.length() - 10) : d;
    }

    public static boolean has(Context c, String n) {
        String k = key(n);
        return !k.isEmpty() && p(c).contains(k);
    }

    public static List<String> order(Context c) {
        List<String> out = new ArrayList<>();
        for (String s : p(c).getString("_order", "").split(",")) if (!s.isEmpty()) out.add(s);
        return out;
    }

    public static void setOrder(Context c, List<String> keys) {
        p(c).edit().putString("_order", TextUtils.join(",", keys)).apply();
    }

    // returns true if added, false if removed
    public static boolean toggle(Context c, String n, String name) {
        String k = key(n);
        if (k.isEmpty()) return false;
        List<String> o = order(c);
        if (p(c).contains(k)) {
            o.remove(k);
            p(c).edit().remove(k).putString("_order", TextUtils.join(",", o)).apply();
            return false;
        }
        String nm = (name == null || name.isEmpty()) ? n : name;
        o.remove(k);
        o.add(k);
        p(c).edit().putString(k, nm + "\n" + n).putString("_order", TextUtils.join(",", o)).apply();
        return true;
    }

    public static void remove(Context c, String k) {
        List<String> o = order(c);
        o.remove(k);
        p(c).edit().remove(k).putString("_order", TextUtils.join(",", o)).apply();
    }

    // each item: {key, name, number}, in the saved order
    public static List<String[]> list(Context c) {
        Map<String, String[]> map = new HashMap<>();
        for (Map.Entry<String, ?> e : p(c).getAll().entrySet()) {
            if (e.getKey().startsWith("_") || !(e.getValue() instanceof String)) continue;
            String v = (String) e.getValue();
            int i = v.indexOf('\n');
            map.put(e.getKey(), new String[]{e.getKey(), i >= 0 ? v.substring(0, i) : v, i >= 0 ? v.substring(i + 1) : v});
        }
        List<String[]> out = new ArrayList<>();
        for (String k : order(c)) {
            String[] it = map.remove(k);
            if (it != null) out.add(it);
        }
        List<String[]> rest = new ArrayList<>(map.values());
        Collections.sort(rest, (a, b) -> a[1].compareToIgnoreCase(b[1]));
        out.addAll(rest);
        return out;
    }
}
