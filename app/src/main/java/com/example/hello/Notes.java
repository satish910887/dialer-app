package com.example.hello;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.text.InputType;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Toast;

public class Notes {
    static android.content.SharedPreferences sp(Context c) {
        return c.getSharedPreferences("notes", Context.MODE_PRIVATE);
    }

    public static String get(Context c, String n) {
        String k = Favs.key(n);
        return k.isEmpty() ? "" : sp(c).getString(k, "");
    }

    public static void edit(Activity a, String number, String title, Runnable done) {
        final String k = Favs.key(number);
        if (k.isEmpty()) { Toast.makeText(a, "No number to attach a note to", Toast.LENGTH_SHORT).show(); return; }
        final EditText e = new EditText(a);
        e.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        e.setMinLines(3);
        e.setGravity(android.view.Gravity.TOP);
        e.setText(get(a, number));
        e.setSelection(e.getText().length());
        LinearLayout box = new LinearLayout(a);
        int p = Ui.dp(a, 20);
        box.setPadding(p, Ui.dp(a, 8), p, 0);
        box.addView(e, new LinearLayout.LayoutParams(-1, -2));
        AlertDialog.Builder b = new AlertDialog.Builder(a).setTitle("Note · " + title).setView(box)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Save", (d, w) -> {
                    String t = e.getText().toString().trim();
                    if (t.isEmpty()) sp(a).edit().remove(k).apply(); else sp(a).edit().putString(k, t).apply();
                    if (done != null) done.run();
                });
        if (!get(a, number).isEmpty()) {
            b.setNeutralButton("Delete", (d, w) -> { sp(a).edit().remove(k).apply(); if (done != null) done.run(); });
        }
        b.show();
    }
}
