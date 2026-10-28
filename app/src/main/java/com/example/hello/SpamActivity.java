package com.example.hello;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;

public class SpamActivity extends Activity {
    LinearLayout list;
    SharedPreferences sp;

    int dp(float n) { return Ui.dp(this, n); }

    @Override
    public void onCreate(Bundle b) {
        super.onCreate(b);
        Ui.applyTheme(this);
        Ui.bars(this);
        sp = getSharedPreferences("app", MODE_PRIVATE);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(18), dp(18), dp(12));
        root.setBackground(Ui.bg(this));
        TextView title = Ui.text(this, "‹  Spam protection", 24, Ui.TEXT, true);
        title.setPadding(dp(2), dp(8), 0, dp(12));
        title.setOnClickListener(v -> finish());
        root.addView(title);

        ScrollView sv = new ScrollView(this);
        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        sv.addView(list);
        root.addView(sv, new LinearLayout.LayoutParams(-1, 0, 1));
        setContentView(root);
    }

    @Override
    protected void onResume() {
        super.onResume();
        build();
    }

    void build() {
        list.removeAllViews();
        TextView shield = Ui.text(this, "🛡️", 44, Ui.TEXT, false);
        shield.setGravity(Gravity.CENTER);
        shield.setBackground(Ui.ovalStroke(this, Ui.SURFACE, Ui.ACCENT));
        LinearLayout.LayoutParams sp1 = new LinearLayout.LayoutParams(dp(96), dp(96));
        sp1.gravity = Gravity.CENTER_HORIZONTAL;
        list.addView(shield, sp1);
        TextView d = Ui.text(this, "Calls that match your rules are rejected automatically.", 14, Ui.MUTED, false);
        d.setGravity(Gravity.CENTER);
        d.setPadding(dp(12), dp(12), dp(12), dp(12));
        list.addView(d);

        toggle("Enable spam protection", "Reject calls that match your blocklist rules", "spam_on", true);
        toggle("Block unknown callers", "Reject numbers that are not in your contacts", "block_unknown", false);
        toggle("Block hidden numbers", "Reject calls with no caller number", "block_hidden", false);

        int n = sp.getInt("blocked_count", 0);
        String last = sp.getString("blocked_last", "");
        LinearLayout stats = card();
        stats.setOrientation(LinearLayout.VERTICAL);
        stats.addView(Ui.text(this, "Calls blocked: " + n, 16, Ui.TEXT, true));
        if (!last.isEmpty()) {
            TextView l = Ui.text(this, "Last: " + last, 13, Ui.MUTED, false);
            l.setPadding(0, dp(4), 0, 0);
            stats.addView(l);
        }
        if (n > 0) {
            TextView reset = Ui.text(this, "Reset counter", 13, Ui.ACCENT, true);
            reset.setPadding(0, dp(10), 0, 0);
            reset.setOnClickListener(v -> {
                sp.edit().putInt("blocked_count", 0).remove("blocked_last").apply();
                build();
            });
            stats.addView(reset);
        }

        row("Blocklist", "Numbers, keywords and reported spam",
                () -> startActivity(new Intent(this, BlockedNumbersActivity.class)));
    }

    LinearLayout card() {
        LinearLayout c = new LinearLayout(this);
        c.setGravity(Gravity.CENTER_VERTICAL);
        c.setPadding(dp(16), dp(14), dp(16), dp(14));
        c.setBackground(Ui.card(this, Ui.SURFACE, 18, Ui.SURFACE2));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.bottomMargin = dp(8);
        list.addView(c, lp);
        return c;
    }

    LinearLayout texts(String title, String sub) {
        LinearLayout t = new LinearLayout(this);
        t.setOrientation(LinearLayout.VERTICAL);
        t.addView(Ui.text(this, title, 16, Ui.TEXT, true));
        TextView s = Ui.text(this, sub, 13, Ui.MUTED, false);
        s.setPadding(0, dp(4), 0, 0);
        t.addView(s);
        return t;
    }

    void row(String title, String sub, Runnable r) {
        LinearLayout c = card();
        c.addView(texts(title, sub), new LinearLayout.LayoutParams(0, -2, 1));
        c.addView(Ui.text(this, "›", 22, Ui.MUTED, false));
        c.setOnClickListener(v -> r.run());
    }

    void toggle(String title, String sub, String key, boolean def) {
        LinearLayout c = card();
        c.addView(texts(title, sub), new LinearLayout.LayoutParams(0, -2, 1));
        Switch sw = new Switch(this);
        sw.setChecked(sp.getBoolean(key, def));
        sw.setOnCheckedChangeListener((v, on) -> sp.edit().putBoolean(key, on).apply());
        c.addView(sw);
        c.setOnClickListener(v -> sw.toggle());
    }
}
