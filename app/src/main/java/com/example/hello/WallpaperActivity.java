package com.example.hello;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

public class WallpaperActivity extends Activity {
    LinearLayout list;
    SharedPreferences sp;

    int dp(float n) { return Ui.dp(this, n); }

    void toast(String m) { Toast.makeText(this, m, Toast.LENGTH_SHORT).show(); }

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
        TextView title = Ui.text(this, "‹  Wallpaper", 26, Ui.TEXT, true);
        title.setPadding(dp(2), dp(8), 0, dp(8));
        title.setOnClickListener(v -> finish());
        root.addView(title);

        ScrollView sv = new ScrollView(this);
        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        sv.addView(list);
        root.addView(sv, new LinearLayout.LayoutParams(-1, 0, 1));
        setContentView(root);
        build();
    }

    String status(String key) {
        int m = Wallpaper.mode(this, key);
        if (m == 1) return "Custom image";
        if (m == 2) return "No wallpaper";
        return Wallpaper.has(this) ? "Default wallpaper" : "No wallpaper";
    }

    void build() {
        list.removeAllViews();
        TextView hint = Ui.text(this, "Pick a default wallpaper, then give any screen its own image or turn it off.",
                13.5f, Ui.MUTED, false);
        hint.setPadding(dp(4), 0, dp(4), dp(6));
        list.addView(hint);

        section("DEFAULT");
        row("Default wallpaper", Wallpaper.has(this) ? "Image set. Tap to change or remove" : "None. Tap to choose from gallery",
                this::defaultDialog);
        row("Dimming", sp.getInt("wall_dim", 55) + "%  ·  keeps text readable", this::dimDialog);

        section("SCREENS");
        for (int i = 0; i < Wallpaper.KEYS.length; i++) {
            final int idx = i;
            row(Wallpaper.NAMES[i], status(Wallpaper.KEYS[i]), () -> screenDialog(idx));
        }
        section("RESET");
        row("Reset all screens", "Every screen uses the default wallpaper again", () -> {
            Wallpaper.resetAll(this);
            toast("All screens reset");
            recreate();
        });
    }

    void section(String t) {
        TextView s = Ui.text(this, t, 12, Ui.ACCENT, true);
        s.setPadding(dp(8), dp(18), dp(4), dp(8));
        list.addView(s);
    }

    void row(String title, String sub, Runnable r) {
        LinearLayout c = new LinearLayout(this);
        c.setGravity(Gravity.CENTER_VERTICAL);
        c.setPadding(dp(16), dp(14), dp(16), dp(14));
        c.setBackground(Ui.ripple(this, Ui.SURFACE, false, 18));
        LinearLayout t = new LinearLayout(this);
        t.setOrientation(LinearLayout.VERTICAL);
        t.addView(Ui.text(this, title, 16, Ui.TEXT, true));
        TextView s = Ui.text(this, sub, 13, Ui.MUTED, false);
        s.setPadding(0, dp(4), 0, 0);
        t.addView(s);
        c.addView(t, new LinearLayout.LayoutParams(0, -2, 1));
        c.addView(Ui.text(this, "›", 22, Ui.MUTED, false));
        c.setOnClickListener(v -> r.run());
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.bottomMargin = dp(8);
        list.addView(c, lp);
    }

    void pick(String key) {
        sp.edit().putString("wall_pending", key == null ? "" : key).apply();
        Intent in = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        in.addCategory(Intent.CATEGORY_OPENABLE);
        in.setType("image/*");
        startActivityForResult(in, 84);
    }

    void defaultDialog() {
        final boolean has = Wallpaper.has(this);
        final String[] items = has ? new String[]{"Choose from gallery", "Remove default wallpaper"}
                : new String[]{"Choose from gallery"};
        new AlertDialog.Builder(this).setTitle("Default wallpaper")
                .setItems(items, (d, i) -> {
                    if (i == 0) pick(null);
                    else { Wallpaper.remove(this); recreate(); }
                }).show();
    }

    void screenDialog(final int idx) {
        final String key = Wallpaper.KEYS[idx];
        final String[] items = {"Use default wallpaper", "Choose image for this screen", "No wallpaper on this screen"};
        new AlertDialog.Builder(this).setTitle(Wallpaper.NAMES[idx])
                .setSingleChoiceItems(items, Wallpaper.mode(this, key) == 1 ? 1 : Wallpaper.mode(this, key) == 2 ? 2 : 0,
                        (d, i) -> {
                    d.dismiss();
                    if (i == 0) { Wallpaper.removeCustom(this, key); recreate(); }
                    else if (i == 1) pick(key);
                    else { Wallpaper.setMode(this, key, 2); Wallpaper.bump(this); recreate(); }
                }).setNegativeButton("Cancel", null).show();
    }

    void dimDialog() {
        final SeekBar sb = new SeekBar(this);
        sb.setMax(90);
        sb.setProgress(sp.getInt("wall_dim", 55));
        final TextView lab = new TextView(this);
        lab.setText("Dimming: " + sb.getProgress() + "%");
        lab.setTextSize(15);
        sb.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar s, int p, boolean u) { lab.setText("Dimming: " + p + "%"); }
            public void onStartTrackingTouch(SeekBar s) { }
            public void onStopTrackingTouch(SeekBar s) { }
        });
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(24), dp(16), dp(24), dp(4));
        box.addView(lab);
        box.addView(sb);
        new AlertDialog.Builder(this).setTitle("Wallpaper dimming").setView(box)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Save", (d, w) -> {
                    sp.edit().putInt("wall_dim", sb.getProgress()).apply();
                    Wallpaper.bump(this);
                    recreate();
                }).show();
    }

    @Override
    protected void onActivityResult(int req, int res, Intent d) {
        super.onActivityResult(req, res, d);
        if (req != 84 || res != RESULT_OK || d == null || d.getData() == null) return;
        final Uri u = d.getData();
        final String k = sp.getString("wall_pending", "");
        toast("Setting wallpaper…");
        new Thread(() -> {
            final boolean ok = Wallpaper.save(this, u, k.isEmpty() ? null : k);
            runOnUiThread(() -> {
                if (ok) { toast("Wallpaper applied"); recreate(); }
                else toast("Couldn't use that image");
            });
        }).start();
    }
}
