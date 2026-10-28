package com.example.hello;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.KeyguardManager;
import android.content.ContentValues;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.provider.Settings;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.json.JSONObject;

public class SettingsActivity extends Activity {
    static final String[] STORES = {"app", "favs", "spam", "cring", "notes"};
    // The settings the "app" store may hold, by type. A restored file is only trusted for these keys and types:
    // a value of the wrong type would crash the first screen that reads it.
    static final Set<String> INT_KEYS = new HashSet<>(Arrays.asList(
            "theme", "accent", "font", "vibe_pattern", "gesture", "sim", "wall_v", "wall_dim", "blocked_count"));
    static final Set<String> BOOL_KEYS = new HashSet<>(Arrays.asList(
            "animations", "splash", "vibration", "tones", "ringtone", "ring_vibe", "flip_silence", "announce",
            "assist_on", "card_on", "applock", "a11y_contrast", "a11y_bold", "a11y_speak",
            "spam_on", "block_unknown", "block_hidden", "onboarded"));
    static final Set<String> STR_KEYS = new HashSet<>(Arrays.asList("ringtone_uri", "assist_cc"));
    static final String[] GESTURES = {"Buttons only", "Swipe up to answer, down to decline", "Swipe right to answer, left to decline", "Slide the knob to answer or decline"};
    LinearLayout list;
    String secIcon = "⚙️";
    SharedPreferences sp;

    int dp(float n) { return Ui.dp(this, n); }

    void toast(String m) { Toast.makeText(this, m, Toast.LENGTH_LONG).show(); }

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

        TextView title = Ui.text(this, "‹  Settings", 26, Ui.TEXT, true);
        title.setPadding(dp(2), dp(8), 0, dp(12));
        title.setOnClickListener(v -> finish());
        root.addView(title);

        ScrollView scroll = new ScrollView(this);
        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(list);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));

        section("APPEARANCE");
        row("Theme", Ui.THEME_NAMES[Math.max(0, Math.min(sp.getInt("theme", 0), Ui.THEME_NAMES.length - 1))], this::themes);
        row("Accent colour", Ui.ACCENT_NAMES[Math.max(0, Math.min(sp.getInt("accent", 0), Ui.ACCENT_NAMES.length - 1))], this::accents);
        row("Text size", Ui.FONT_NAMES[Math.max(0, Math.min(sp.getInt("font", 1), Ui.FONT_NAMES.length - 1))], this::fonts);
        row("Wallpaper", "Default and per-screen wallpapers",
                () -> startActivity(new Intent(this, WallpaperActivity.class)));
        toggle("Animations", "Press and screen transition effects", "animations", true);
        toggle("Splash screen", "Show the Dialer logo when the app opens", "splash", true);

        section("FEEDBACK");
        toggle("Touch feedback", "Vibrate when keypad keys are pressed", "vibration", true);
        toggle("Keypad tones", "Play a tone when keypad keys are pressed", "tones", true);

        section("INCOMING CALLS");
        toggle("Ringtone", "Play your phone ringtone for incoming calls", "ringtone", true);
        row("Ringtone sound", Ring.title(this, sp.getString("ringtone_uri", "")), this::ringtonePick);
        toggle("Ring vibration", "Vibrate for incoming calls", "ring_vibe", true);
        row("Vibration pattern", Ring.VIBE_NAMES[Math.max(0, Math.min(sp.getInt("vibe_pattern", 0), Ring.VIBE_NAMES.length - 1))], this::vibeDialog);
        toggle("Flip to silence", "Turn the phone face down to stop the ringing", "flip_silence", true);
        toggle("Caller ID announcement", "Speak the caller's name while ringing", "announce", false);
        row("Incoming call gesture", GESTURES[Math.max(0, Math.min(sp.getInt("gesture", 0), 3))], this::gestureDialog);

        section("CALLS");
        row("Calling accounts", sp.getInt("sim", 0) == -1 ? "Ask every time" : "SIM " + (sp.getInt("sim", 0) + 1) + " by default", this::simDialog);
        row("Quick responses", "Edit the messages used to decline calls", this::quickDialog);
        toggle("Assisted dialling", "Add the country code to 10-digit numbers", "assist_on", false);
        row("Default country code", sp.getString("assist_cc", "+91"), this::ccDialog);
        toggle("Calling card", "Add your card access number before every call", "card_on", false);
        row("Calling card number", sp.getString("card_num", "").isEmpty() ? "Not set" : sp.getString("card_num", ""), this::cardDialog);
        row("Speed dial", "Long-press keypad digits 2-9 to call", () -> SpeedDial.manage(this));
        int pendingReminders = Reminders.list(this).size();
        row("Call-back reminders", pendingReminders == 0 ? "None set" : pendingReminders + " pending", this::remindersDialog);
        row("Favorites", "Your starred contacts",
                () -> startActivity(new Intent(this, FavoritesActivity.class)));
        row("Spam protection", "Block spam and unwanted calls",
                () -> startActivity(new Intent(this, SpamActivity.class)));
        row("Blocklist", "Numbers, keywords and reported spam",
                () -> startActivity(new Intent(this, BlockedNumbersActivity.class)));
        row("Default phone app", "Choose which app handles calls",
                () -> startActivity(new Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)));

        section("PRIVACY & SECURITY");
        lockToggle();
        row("App permissions", "Phone, Call logs, Contacts, Notifications",
                () -> startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.parse("package:" + getPackageName()))));

        section("ACCESSIBILITY");
        toggleRe("High contrast text", "Stronger text colours for easier reading", "a11y_contrast");
        toggleRe("Bold text", "Show all text in bold", "a11y_bold");
        toggle("Speak keypad digits", "Say each digit aloud as you press it", "a11y_speak", false);

        section("STORAGE & BACKUP");
        row("Back up settings", "Save favorites, rules and settings to Downloads", this::backup);
        row("Restore from backup", "Pick a dialer-backup.json file", this::restore);

        section("ABOUT");
        String ver = "";
        try { ver = getPackageManager().getPackageInfo(getPackageName(), 0).versionName; }
        catch (Exception e) { }
        final String v2 = ver;
        row("About Dialer", "Made in India · Version " + ver, () -> startActivity(new Intent(this, AboutActivity.class)));

        setContentView(root);
    }

    void section(String text) {
        switch (text) {
            case "APPEARANCE": secIcon = "🎨"; break;
            case "FEEDBACK": secIcon = "📳"; break;
            case "INCOMING CALLS": secIcon = "🔔"; break;
            case "CALLS": secIcon = "📞"; break;
            case "ACCESSIBILITY": secIcon = "♿"; break;
            case "PRIVACY & SECURITY": secIcon = "🔒"; break;
            case "STORAGE & BACKUP": secIcon = "💾"; break;
            case "ABOUT": secIcon = "ℹ️"; break;
            default: secIcon = "⚙️";
        }
        TextView t = Ui.text(this, text, 12, Ui.ACCENT, true);
        t.setPadding(dp(8), dp(18), dp(4), dp(8));
        list.addView(t);
    }

    LinearLayout card() {
        LinearLayout c = new LinearLayout(this);
        c.setGravity(Gravity.CENTER_VERTICAL);
        c.setPadding(dp(16), dp(14), dp(16), dp(14));
        c.setBackground(Ui.ripple(this, Ui.SURFACE, false, 18));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.bottomMargin = dp(8);
        list.addView(c, lp);
        TextView ic = Ui.text(this, secIcon, 17, Ui.TEXT, false);
        ic.setGravity(Gravity.CENTER);
        ic.setBackground(Ui.shape(this, Ui.mix(Ui.SURFACE, Ui.ACCENT, 0.22f), true, 0));
        LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(dp(40), dp(40));
        ip.rightMargin = dp(14);
        c.addView(ic, ip);
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

    void row(String title, String sub, Runnable action) {
        LinearLayout c = card();
        c.addView(texts(title, sub), new LinearLayout.LayoutParams(0, -2, 1));
        c.addView(Ui.text(this, "›", 22, Ui.MUTED, false));
        c.setOnClickListener(v -> action.run());
        Ui.press(c);
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

    void lockToggle() {
        LinearLayout c = card();
        c.addView(texts("App lock", "Ask for your screen lock when opening Dialer"),
                new LinearLayout.LayoutParams(0, -2, 1));
        Switch sw = new Switch(this);
        sw.setChecked(sp.getBoolean("applock", false));
        sw.setOnCheckedChangeListener((v, on) -> {
            if (on) {
                KeyguardManager km = (KeyguardManager) getSystemService(KEYGUARD_SERVICE);
                if (km == null || !km.isDeviceSecure()) {
                    toast("Set a screen lock (PIN, pattern or fingerprint) in phone Settings first");
                    v.setChecked(false);
                    return;
                }
                MainActivity.unlocked = true;
                MainActivity.leftAt = System.currentTimeMillis();
            }
            sp.edit().putBoolean("applock", on).apply();
        });
        c.addView(sw);
        c.setOnClickListener(v -> sw.toggle());
    }

    void wallpaper() {
        boolean has = Wallpaper.has(this);
        final java.util.List<String> items = new java.util.ArrayList<>();
        items.add("Choose from gallery");
        if (has) { items.add("Adjust dimming"); items.add("Remove wallpaper"); }
        new AlertDialog.Builder(this).setTitle("Wallpaper")
                .setItems(items.toArray(new String[0]), (d, i) -> {
                    if (i == 0) {
                        Intent in = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                        in.addCategory(Intent.CATEGORY_OPENABLE);
                        in.setType("image/*");
                        startActivityForResult(in, 82);
                    } else if (i == 1) dimDialog();
                    else { Wallpaper.remove(this); recreate(); }
                }).show();
    }

    void dimDialog() {
        final android.widget.SeekBar sb = new android.widget.SeekBar(this);
        sb.setMax(90);
        sb.setProgress(sp.getInt("wall_dim", 55));
        final TextView lab = new TextView(this);
        lab.setText("Dimming: " + sb.getProgress() + "%");
        lab.setTextSize(15);
        sb.setOnSeekBarChangeListener(new android.widget.SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(android.widget.SeekBar s, int p, boolean u) { lab.setText("Dimming: " + p + "%"); }
            public void onStartTrackingTouch(android.widget.SeekBar s) { }
            public void onStopTrackingTouch(android.widget.SeekBar s) { }
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

    void toggleRe(String title, String sub, final String key) {
        LinearLayout c = card();
        c.addView(texts(title, sub), new LinearLayout.LayoutParams(0, -2, 1));
        Switch sw = new Switch(this);
        sw.setChecked(sp.getBoolean(key, false));
        sw.setOnCheckedChangeListener((v, on) -> { sp.edit().putBoolean(key, on).apply(); recreate(); });
        c.addView(sw);
        c.setOnClickListener(v -> sw.toggle());
    }

    void cardDialog() {
        final android.widget.EditText e = new android.widget.EditText(this);
        e.setInputType(android.text.InputType.TYPE_CLASS_PHONE);
        e.setHint("Access number, e.g. 1800123456,");
        e.setText(sp.getString("card_num", ""));
        LinearLayout box = new LinearLayout(this);
        box.setPadding(dp(20), dp(8), dp(20), 0);
        box.addView(e, new LinearLayout.LayoutParams(-1, -2));
        new AlertDialog.Builder(this).setTitle("Calling card number").setView(box)
                .setMessage("A comma adds a short pause before the number.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Save", (d, w) -> {
                    sp.edit().putString("card_num", e.getText().toString().replaceAll("[^0-9,+]", "")).apply();
                    recreate();
                }).show();
    }

    void ringtonePick() {
        new AlertDialog.Builder(this).setTitle("Ringtone")
                .setItems(new String[]{"Choose ringtone", "Use phone default"}, (d, i) -> {
                    if (i == 0) startActivityForResult(Ring.pickerIntent(sp.getString("ringtone_uri", "")), 85);
                    else { sp.edit().remove("ringtone_uri").apply(); recreate(); }
                }).show();
    }

    void vibeDialog() {
        new AlertDialog.Builder(this).setTitle("Vibration pattern")
                .setSingleChoiceItems(Ring.VIBE_NAMES, sp.getInt("vibe_pattern", 0), (d, w) -> {
                    sp.edit().putInt("vibe_pattern", w).apply();
                    try {
                        android.os.Vibrator v = (android.os.Vibrator) getSystemService(VIBRATOR_SERVICE);
                        long[] p = Ring.VIBE[w];
                        v.vibrate(android.os.VibrationEffect.createWaveform(p, -1));
                    } catch (Throwable e) { }
                    d.dismiss();
                    recreate();
                }).setNegativeButton("Cancel", null).show();
    }

    void gestureDialog() {
        new AlertDialog.Builder(this).setTitle("Incoming call gesture")
                .setSingleChoiceItems(GESTURES, Math.max(0, Math.min(sp.getInt("gesture", 0), 3)), (d, w) -> {
                    sp.edit().putInt("gesture", w).apply(); d.dismiss(); recreate(); })
                .setNegativeButton("Cancel", null).show();
    }

    void simDialog() {
        int n = Caller.simCount(this);
        if (n < 2) { toast("Two SIMs not detected. Allow the Phone permission and insert 2 SIMs."); return; }
        final String[] items = new String[n + 1];
        items[0] = "Ask every time";
        for (int i = 1; i <= n; i++) items[i] = "SIM " + i;
        int cur = sp.getInt("sim", 0);
        int sel = cur == -1 ? 0 : Math.min(cur + 1, n);
        new AlertDialog.Builder(this).setTitle("Calling accounts")
                .setSingleChoiceItems(items, sel, (d, w) -> {
                    sp.edit().putInt("sim", w == 0 ? -1 : w - 1).apply(); d.dismiss(); recreate(); })
                .setNegativeButton("Cancel", null).show();
    }

    void quickDialog() {
        final String[] cur = QuickReplies.get(this);
        new AlertDialog.Builder(this).setTitle("Quick responses (tap to edit)")
                .setItems(new String[]{cur[0], cur[1], cur[2]}, (d, i) -> editQuick(i))
                .setNegativeButton("Close", null)
                .setNeutralButton("Reset", (d, w) -> { QuickReplies.reset(this); toast("Reset to defaults"); })
                .show();
    }

    void editQuick(final int i) {
        final android.widget.EditText e = new android.widget.EditText(this);
        e.setText(QuickReplies.get(this)[i]);
        LinearLayout box = new LinearLayout(this);
        box.setPadding(dp(20), dp(8), dp(20), 0);
        box.addView(e, new LinearLayout.LayoutParams(-1, -2));
        new AlertDialog.Builder(this).setTitle("Message " + (i + 1)).setView(box)
                .setNegativeButton("Cancel", (d, w) -> quickDialog())
                .setPositiveButton("Save", (d, w) -> {
                    String t = e.getText().toString().trim();
                    if (!t.isEmpty()) QuickReplies.set(this, i, t);
                    quickDialog();
                }).show();
    }

    void ccDialog() {
        final android.widget.EditText e = new android.widget.EditText(this);
        e.setInputType(android.text.InputType.TYPE_CLASS_PHONE);
        e.setText(sp.getString("assist_cc", "+91"));
        LinearLayout box = new LinearLayout(this);
        box.setPadding(dp(20), dp(8), dp(20), 0);
        box.addView(e, new LinearLayout.LayoutParams(-1, -2));
        new AlertDialog.Builder(this).setTitle("Default country code").setView(box)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Save", (d, w) -> {
                    String t = e.getText().toString().replaceAll("[^0-9]", "");
                    if (!t.isEmpty()) { sp.edit().putString("assist_cc", "+" + t).apply(); recreate(); }
                }).show();
    }

    void themes() {
        new AlertDialog.Builder(this).setTitle("Choose theme")
                .setSingleChoiceItems(Ui.THEME_NAMES, sp.getInt("theme", 0), (d, w) -> {
                    sp.edit().putInt("theme", w).apply(); d.dismiss(); recreate(); })
                .setNegativeButton("Cancel", null).show();
    }

    void accents() {
        new AlertDialog.Builder(this).setTitle("Accent colour")
                .setSingleChoiceItems(Ui.ACCENT_NAMES, sp.getInt("accent", 0), (d, w) -> {
                    sp.edit().putInt("accent", w).apply(); d.dismiss(); recreate(); })
                .setNegativeButton("Cancel", null).show();
    }

    void fonts() {
        new AlertDialog.Builder(this).setTitle("Text size")
                .setSingleChoiceItems(Ui.FONT_NAMES, sp.getInt("font", 1), (d, w) -> {
                    sp.edit().putInt("font", w).apply(); d.dismiss(); recreate(); })
                .setNegativeButton("Cancel", null).show();
    }

    // ---------- backup / restore ----------

    void backup() {
        try {
            JSONObject root = new JSONObject();
            for (String f : STORES) {
                JSONObject o = new JSONObject();
                for (Map.Entry<String, ?> e : getSharedPreferences(f, MODE_PRIVATE).getAll().entrySet()) {
                    // the calling card number can work like a PIN, so it stays out of a plain-text file in Downloads
                    if (f.equals("app") && e.getKey().equals("card_num")) continue;
                    o.put(e.getKey(), e.getValue());
                }
                root.put(f, o);
            }
            ContentValues cv = new ContentValues();
            cv.put(MediaStore.MediaColumns.DISPLAY_NAME, "dialer-backup.json");
            cv.put(MediaStore.MediaColumns.MIME_TYPE, "application/json");
            cv.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);
            Uri u = getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, cv);
            if (u == null) throw new Exception("could not create file");
            try (OutputStream os = getContentResolver().openOutputStream(u)) {
                os.write(root.toString(2).getBytes("UTF-8"));
            }
            toast("Saved to Downloads/dialer-backup.json");
        } catch (Exception e) {
            toast("Couldn't save the backup");
        }
    }

    // Returns the value to store (already the right type), or null if this key/value must not be restored.
    static Object coerce(String store, String key, Object v) {
        if (!store.equals("app")) return v instanceof String ? v : null;
        if (key.equals("card_num")) return null; // never taken from a file
        if (INT_KEYS.contains(key) || key.startsWith("wall_mode_")) {
            return v instanceof Number ? (Object) ((Number) v).intValue() : null;
        }
        if (BOOL_KEYS.contains(key)) return v instanceof Boolean ? v : null;
        if (STR_KEYS.contains(key) || key.startsWith("qr_") || key.startsWith("sd_")) {
            return v instanceof String ? v : null;
        }
        return null;
    }

    void remindersDialog() {
        final List<String[]> items = Reminders.list(this);
        if (items.isEmpty()) {
            new AlertDialog.Builder(this).setTitle("Call-back reminders")
                    .setMessage("No reminders are set. Use \"Remind me\" on the incoming call screen.")
                    .setPositiveButton("OK", null).show();
            return;
        }
        String[] labels = new String[items.size()];
        for (int i = 0; i < labels.length; i++) {
            String[] r = items.get(i);
            String who = r[2].isEmpty() ? r[3] : r[2];
            String when = "";
            try {
                when = android.text.format.DateUtils.formatDateTime(this, Long.parseLong(r[1]),
                        android.text.format.DateUtils.FORMAT_SHOW_DATE | android.text.format.DateUtils.FORMAT_ABBREV_MONTH
                                | android.text.format.DateUtils.FORMAT_SHOW_TIME);
            } catch (NumberFormatException ignored) { }
            labels[i] = who + "  ·  " + when;
        }
        new AlertDialog.Builder(this).setTitle("Tap a reminder to cancel it")
                .setItems(labels, (d, i) -> {
                    try { Reminders.cancel(this, Integer.parseInt(items.get(i)[0])); }
                    catch (NumberFormatException ignored) { }
                    toast("Reminder cancelled");
                    recreate();
                }).setNegativeButton("Close", null).show();
    }

    void restore() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("*/*");
        startActivityForResult(i, 81);
    }

    @Override
    protected void onActivityResult(int req, int res, Intent d) {
        super.onActivityResult(req, res, d);
        if (req == 85 && res == RESULT_OK && d != null) {
            sp.edit().putString("ringtone_uri", Ring.fromResult(d)).apply();
            recreate();
        }
        if (req == 81 && res == RESULT_OK && d != null && d.getData() != null) doRestore(d.getData());
        if (req == 82 && res == RESULT_OK && d != null && d.getData() != null) {
            final Uri u = d.getData();
            toast("Setting wallpaper…");
            new Thread(() -> {
                final boolean ok = Wallpaper.save(this, u);
                runOnUiThread(() -> {
                    if (ok) { toast("Wallpaper applied"); recreate(); }
                    else toast("Couldn't use that image");
                });
            }).start();
        }
    }

    void doRestore(Uri u) {
        try (InputStream is = getContentResolver().openInputStream(u)) {
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = is.read(buf)) > 0) {
                bo.write(buf, 0, n);
                if (bo.size() > 2 * 1024 * 1024) throw new IllegalArgumentException("not a dialer backup");
            }
            JSONObject root = new JSONObject(bo.toString("UTF-8"));
            for (String f : STORES) {
                JSONObject o = root.optJSONObject(f);
                if (o == null) continue;
                SharedPreferences.Editor ed = getSharedPreferences(f, MODE_PRIVATE).edit();
                if (!f.equals("app")) ed.clear();
                Iterator<String> it = o.keys();
                while (it.hasNext()) {
                    String k = it.next();
                    Object v = coerce(f, k, o.get(k));
                    if (v instanceof Boolean) ed.putBoolean(k, (Boolean) v);
                    else if (v instanceof Integer) ed.putInt(k, (Integer) v);
                    else if (v instanceof String) ed.putString(k, (String) v);
                }
                ed.apply();
            }
            Favs.version++; // favourites may have changed, so cached lists must be rebuilt
            toast("Backup restored");
            recreate();
        } catch (Exception e) {
            toast("Couldn't restore: that doesn't look like a Dialer backup");
        }
    }
}
