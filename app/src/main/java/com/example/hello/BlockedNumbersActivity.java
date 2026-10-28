package com.example.hello;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ContentUris;
import android.content.ContentValues;
import android.database.Cursor;
import android.graphics.Color;
import android.os.Bundle;
import android.provider.BlockedNumberContract;
import android.text.InputType;
import android.view.Gravity;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.util.List;

public class BlockedNumbersActivity extends Activity {
    LinearLayout rows, chipsRow;
    TextView addBtn;
    int tab = 0;

    int dp(float n) { return Ui.dp(this, n); }

    void toast(String m) { Toast.makeText(this, m, Toast.LENGTH_SHORT).show(); }

    @Override
    public void onCreate(Bundle b) {
        super.onCreate(b);
        Ui.applyTheme(this);
        Ui.bars(this);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(18), dp(16), dp(12));
        root.setBackground(Ui.bg(this));

        TextView back = Ui.text(this, "‹  Blocklist", 24, Ui.TEXT, true);
        back.setPadding(dp(4), dp(8), 0, dp(12));
        back.setOnClickListener(v -> finish());
        root.addView(back);

        chipsRow = new LinearLayout(this);
        chipsRow.setPadding(0, 0, 0, dp(12));
        root.addView(chipsRow, new LinearLayout.LayoutParams(-1, -2));

        rows = new LinearLayout(this);
        rows.setOrientation(LinearLayout.VERTICAL);
        ScrollView scroll = new ScrollView(this);
        scroll.addView(rows);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));

        addBtn = Ui.text(this, "", 15, Color.WHITE, true);
        addBtn.setGravity(Gravity.CENTER);
        addBtn.setBackground(Ui.ripple(this, Ui.ACCENT, false, 27));
        addBtn.setOnClickListener(v -> onAdd());
        LinearLayout.LayoutParams ap = new LinearLayout.LayoutParams(-1, dp(54));
        ap.topMargin = dp(10);
        root.addView(addBtn, ap);

        setContentView(root);
        render();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (rows != null) render();
    }

    void ask(String title, String hint, boolean phone, java.util.function.Consumer<String> ok) {
        EditText e = new EditText(this);
        e.setHint(hint);
        e.setSingleLine(true);
        if (phone) e.setInputType(InputType.TYPE_CLASS_PHONE);
        FrameLayout f = new FrameLayout(this);
        f.setPadding(dp(20), dp(8), dp(20), 0);
        f.addView(e);
        new AlertDialog.Builder(this).setTitle(title).setView(f)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Add", (d, w) -> {
                    String t = e.getText().toString().trim();
                    if (!t.isEmpty()) ok.accept(t);
                }).show();
    }

    void onAdd() {
        if (tab == 0) {
            ask("Block a number", "Phone number", true, s -> {
                try {
                    ContentValues cv = new ContentValues();
                    cv.put(BlockedNumberContract.BlockedNumbers.COLUMN_ORIGINAL_NUMBER, s);
                    getContentResolver().insert(BlockedNumberContract.BlockedNumbers.CONTENT_URI, cv);
                    toast("Blocked " + s);
                } catch (Exception e) {
                    toast("Can't block: set Dialer as the default phone app");
                }
                render();
            });
        } else if (tab == 1) {
            ask("Add rule", "Prefix like 140, or a name word", false, s -> {
                SpamGuard.addKeyword(this, s);
                render();
            });
        } else {
            ask("Report spam number", "Phone number", true, s -> {
                SpamGuard.report(this, s);
                render();
            });
        }
    }

    void render() {
        chipsRow.removeAllViews();
        String[] names = {"Numbers", "Keywords", "Spam reported"};
        for (int i = 0; i < names.length; i++) {
            final int idx = i;
            TextView c = Ui.text(this, names[i], 13, i == tab ? Color.WHITE : Ui.MUTED, true);
            c.setPadding(dp(16), dp(8), dp(16), dp(8));
            c.setBackground(i == tab ? Ui.ripple(this, Ui.ACCENT, false, 20)
                    : Ui.card(this, Ui.SURFACE, 20, Ui.SURFACE2));
            c.setOnClickListener(v -> { tab = idx; render(); });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
            lp.rightMargin = dp(8);
            chipsRow.addView(c, lp);
        }
        addBtn.setText(tab == 0 ? "＋  Add number" : tab == 1 ? "＋  Add keyword or prefix" : "＋  Report a number");
        rows.removeAllViews();
        if (tab == 0) numbers();
        else if (tab == 1) keywords();
        else spam();
    }

    void numbers() {
        Cursor c = null;
        int count = 0;
        try {
            c = getContentResolver().query(BlockedNumberContract.BlockedNumbers.CONTENT_URI,
                    new String[]{"_id", BlockedNumberContract.BlockedNumbers.COLUMN_ORIGINAL_NUMBER},
                    null, null, null);
            while (c != null && c.moveToNext()) {
                final long id = c.getLong(0);
                String n = c.getString(1);
                if (n == null || n.trim().isEmpty()) continue;
                count++;
                addRow(n, "Blocked", "Unblock", Ui.ACCENT, () -> {
                    try {
                        getContentResolver().delete(ContentUris.withAppendedId(
                                BlockedNumberContract.BlockedNumbers.CONTENT_URI, id), null, null);
                        toast("Unblocked");
                    } catch (Exception e) { toast("Unable to unblock"); }
                    render();
                });
            }
            if (count == 0) empty("No blocked numbers");
        } catch (Exception e) {
            empty("Blocked list unavailable.\nSet Dialer as the default phone app.");
        } finally {
            if (c != null) c.close();
        }
    }

    void keywords() {
        List<String> kws = SpamGuard.keywords(this);
        if (kws.isEmpty()) {
            empty("No rules yet.\nAdd a number prefix (like 140) or a word in the caller name.");
            return;
        }
        for (String k : kws) {
            addRow(k, k.matches("[0-9+ ]+") ? "Number starts with" : "Caller name contains",
                    "Remove", Ui.RED, () -> { SpamGuard.removeKeyword(this, k); render(); });
        }
    }

    void spam() {
        List<String[]> list = SpamGuard.reported(this);
        if (list.isEmpty()) {
            empty("No reported numbers.\nUse Report spam from the call menu.");
            return;
        }
        for (String[] r : list) {
            addRow(r[1], "Reported as spam", "Remove", Ui.RED, () -> { SpamGuard.unreport(this, r[0]); render(); });
        }
    }

    void addRow(String title, String sub, String action, int color, Runnable r) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(16), dp(12), dp(8), dp(12));
        row.setBackground(Ui.card(this, Ui.SURFACE, 16, Ui.SURFACE2));
        LinearLayout t = new LinearLayout(this);
        t.setOrientation(LinearLayout.VERTICAL);
        t.addView(Ui.text(this, title, 16, Ui.TEXT, true));
        t.addView(Ui.text(this, sub, 12.5f, Ui.MUTED, false));
        row.addView(t, new LinearLayout.LayoutParams(0, -2, 1));
        TextView b = Ui.text(this, action, 13, color, true);
        b.setPadding(dp(12), dp(10), dp(8), dp(10));
        b.setOnClickListener(v -> r.run());
        row.addView(b);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.bottomMargin = dp(8);
        rows.addView(row, lp);
    }

    void empty(String msg) {
        TextView t = Ui.text(this, msg, 15, Ui.MUTED, false);
        t.setGravity(Gravity.CENTER);
        t.setPadding(dp(18), dp(42), dp(18), dp(42));
        rows.addView(t, new LinearLayout.LayoutParams(-1, -2));
    }
}
