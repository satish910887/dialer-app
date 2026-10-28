package com.example.hello;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.KeyguardManager;
import android.app.role.RoleManager;
import android.content.ClipboardManager;
import android.content.ContentValues;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.BlockedNumberContract;
import android.provider.CallLog;
import android.provider.ContactsContract;
import android.provider.Settings;
import android.telephony.PhoneNumberUtils;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.text.format.DateUtils;
import android.view.*;
import android.widget.*;
import java.util.*;

public class MainActivity extends Activity {
    static final int MATCH = ViewGroup.LayoutParams.MATCH_PARENT;
    static final int WRAP = ViewGroup.LayoutParams.WRAP_CONTENT;
    static final int HOME = 0, CONTACTS = 1, KEYPAD = 2, MORE = 3;
    static final String NOTIF = "android.permission.POST_NOTIFICATIONS";
    static final String[] PERMS = {Manifest.permission.CALL_PHONE, Manifest.permission.READ_CALL_LOG,
            Manifest.permission.WRITE_CALL_LOG, Manifest.permission.READ_CONTACTS,
            Manifest.permission.READ_PHONE_STATE, NOTIF};

    StringBuilder num = new StringBuilder();
    FrameLayout content;
    LinearLayout[] tabItems = new LinearLayout[4];
    int tab = HOME, filter = 0, themeSeen = -1;
    boolean favOnly = false;
    ToneGenerator tone;
    TextView numberView, nameView;
    ImageView backBtn, addBtn;
    List<Row> contactCache;
    int contactCacheVer = -1;
    boolean preloading;
    List<Row> recentsCache;
    int recentsFilter = -1, recentsGen = 0, restoreTab = -1;
    // call log and contacts are read here so scrolling and tab switches don't wait on the database
    final java.util.concurrent.ExecutorService bg = java.util.concurrent.Executors.newSingleThreadExecutor();
    // the contacts list only needs rebuilding when the contacts actually change
    final android.database.ContentObserver contactObs = new android.database.ContentObserver(
            new android.os.Handler(android.os.Looper.getMainLooper())) {
        @Override public void onChange(boolean selfChange) { contactCache = null; contactGen++; }
    };
    int contactGen = 0;
    static boolean unlocked = false;
    static long leftAt = 0;
    boolean locking = false;
    View lockCover;
    LinearLayout mainRoot;
    android.speech.tts.TextToSpeech tts;
    boolean ttsOk;

    int dp(float v) { return Ui.dp(this, v); }

    boolean has(String p) { return checkSelfPermission(p) == PackageManager.PERMISSION_GRANTED; }

    boolean permsOk() {
        for (String p : PERMS) {
            if (p.equals(NOTIF) && Build.VERSION.SDK_INT < 33) continue;
            if (!has(p)) return false;
        }
        return true;
    }

    boolean pref(String k) { return getSharedPreferences("app", MODE_PRIVATE).getBoolean(k, true); }

    void toast(String m) { Toast.makeText(this, m, Toast.LENGTH_SHORT).show(); }

    // ================= lifecycle =================

    @Override
    protected void onCreate(Bundle s) {
        super.onCreate(s);
        Ui.applyTheme(this);
        Ui.bars(this);
        getWindow().setBackgroundDrawable(new ColorDrawable(Ui.BG));
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN
                | WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        try {
            getContentResolver().registerContentObserver(ContactsContract.Contacts.CONTENT_URI, true, contactObs);
        } catch (Throwable e) { }
        if (s != null && s.containsKey("num")) {
            // recreated (rotation, theme change): keep what was typed and the tab that was open
            num.append(s.getString("num", ""));
            restoreTab = s.getInt("tab", -1);
        } else {
            Uri data = getIntent().getData();
            if (data != null && "tel".equals(data.getScheme())) num.append(data.getSchemeSpecificPart());
        }

        SharedPreferences sp = getSharedPreferences("app", MODE_PRIVATE);
        if (!sp.getBoolean("onboarded", false)) {
            setContentView(onboarding(0));
            return;
        }
        buildMain();
        if (!permsOk()) {
            if (!sp.getBoolean("permAsked", false)) {
                sp.edit().putBoolean("permAsked", true).apply();
                requestPermissions(PERMS, 1);
            }
        } else {
            askDefaultDialer();
        }
    }

    void buildMain() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        mainRoot = root;
        root.setBackground(Ui.bg(this, "home"));
        content = new FrameLayout(this);
        root.addView(content, new LinearLayout.LayoutParams(MATCH, 0, 1));
        root.addView(tabBar(), new LinearLayout.LayoutParams(MATCH, WRAP));
        setContentView(root);
        show(num.length() > 0 ? KEYPAD : restoreTab >= 0 && restoreTab <= MORE ? restoreTab : HOME);
        lockCover = new View(this);
        lockCover.setBackgroundColor(Ui.BG);
        lockCover.setClickable(true);
        lockCover.setVisibility(View.GONE);
        addContentView(lockCover, new ViewGroup.LayoutParams(MATCH, MATCH));
    }

    void finishOnboarding() {
        getSharedPreferences("app", MODE_PRIVATE).edit()
                .putBoolean("onboarded", true).putBoolean("permAsked", true).apply();
        buildMain();
        askDefaultDialer();
    }

    @Override
    public void onRequestPermissionsResult(int c, String[] p, int[] r) {
        contactCache = null;
        if (content == null) { finishOnboarding(); return; }
        show(tab);
        if (c == 77) {
            if (has(Manifest.permission.CALL_PHONE) && Caller.pending != null) {
                Caller.dial(this, Caller.pending);
            }
            return;
        }
        if (c == 2) {
            // a speed-dial / voicemail press that was waiting for the Phone permission
            String k = SpeedDial.pendingKey;
            SpeedDial.pendingKey = null;
            if (k != null && has(Manifest.permission.CALL_PHONE)) { SpeedDial.press(this, k); return; }
        }
        askDefaultDialer();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (content == null) return;
        Ui.applyTheme(this);
        SharedPreferences sp = getSharedPreferences("app", MODE_PRIVATE);
        int key = sp.getInt("theme", 0) * 100 + sp.getInt("accent", 0) * 10 + sp.getInt("font", 1)
                + sp.getInt("wall_v", 0) * 1000
                + (sp.getBoolean("a11y_contrast", false) ? 100000 : 0)
                + (sp.getBoolean("a11y_bold", false) ? 200000 : 0);
        if (themeSeen == -1) themeSeen = key;
        else if (key != themeSeen) { themeSeen = key; recreate(); return; }
        preloadContacts();
        if (tab != KEYPAD) refreshKeep();
        checkLock();
    }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        out.putString("num", num.toString());
        out.putInt("tab", tab);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        try { getContentResolver().unregisterContentObserver(contactObs); } catch (Throwable e) { }
        bg.shutdownNow();
        if (tone != null) { tone.release(); tone = null; }
        try { if (tts != null) { tts.shutdown(); tts = null; } } catch (Throwable e) { }
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (!locking) leftAt = System.currentTimeMillis();
    }

    void checkLock() {
        if (lockCover == null) return;
        boolean on = getSharedPreferences("app", MODE_PRIVATE).getBoolean("applock", false);
        KeyguardManager km = (KeyguardManager) getSystemService(KEYGUARD_SERVICE);
        if (!on || km == null || !km.isDeviceSecure()) { lockCover.setVisibility(View.GONE); return; }
        if (locking) return;
        if (unlocked && System.currentTimeMillis() - leftAt < 60000) { lockCover.setVisibility(View.GONE); return; }
        Intent i = km.createConfirmDeviceCredentialIntent("Unlock Dialer", "Confirm your screen lock to continue");
        if (i == null) { lockCover.setVisibility(View.GONE); return; }
        locking = true;
        unlocked = false;
        lockCover.setVisibility(View.VISIBLE);
        startActivityForResult(i, 55);
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req == 55) {
            locking = false;
            if (res == RESULT_OK) {
                unlocked = true;
                leftAt = System.currentTimeMillis();
                if (lockCover != null) lockCover.setVisibility(View.GONE);
            } else {
                finish();
            }
        }
    }

    ListView findList(View v) {
        if (v instanceof ListView) return (ListView) v;
        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) {
                ListView r = findList(g.getChildAt(i));
                if (r != null) return r;
            }
        }
        return null;
    }

    void refreshKeep() {
        ListView old = findList(content);
        int pos = 0, top = 0;
        if (old != null) {
            pos = old.getFirstVisiblePosition();
            View c0 = old.getChildAt(0);
            top = c0 == null ? 0 : c0.getTop();
        }
        show(tab);
        final ListView nl = findList(content);
        if (nl != null && pos > 0) {
            final int p = pos, t = top;
            nl.post(() -> nl.setSelectionFromTop(p, t));
        }
    }

    @Override
    public void onBackPressed() {
        if (content != null && tab != HOME) show(HOME);
        else super.onBackPressed();
    }

    void askDefaultDialer() {
        SharedPreferences sp = getSharedPreferences("app", MODE_PRIVATE);
        if (sp.getBoolean("roleAsked", false)) return;
        RoleManager rm = (RoleManager) getSystemService(ROLE_SERVICE);
        if (rm != null && rm.isRoleAvailable(RoleManager.ROLE_DIALER)
                && !rm.isRoleHeld(RoleManager.ROLE_DIALER)) {
            sp.edit().putBoolean("roleAsked", true).apply();
            startActivityForResult(rm.createRequestRoleIntent(RoleManager.ROLE_DIALER), 9);
        }
    }

    // ================= onboarding =================

    View onboarding(int i) {
        String[][] pg = {
            {"📇", "All your contacts\nin one place", "Find, call and manage your contacts, recents and favorites quickly."},
            {"🛡️", "Stay safe from spam", "Block unwanted numbers and keep your call list clean."},
            {"🎨", "Make it yours", "Dark, light and colourful themes with your favourite accent colour."},
            {"🔐", "Allow access", "Dialer needs these permissions to work as your phone app:\n\n"
                    + "📞  Phone: make and manage calls\n👥  Contacts: names and photos\n"
                    + "🕘  Call logs: recent calls\n🔔  Notifications: incoming calls"}};
        boolean last = i == pg.length - 1;
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setBackground(Ui.bg(this));
        root.setPadding(dp(28), dp(28), dp(28), dp(28));

        TextView skip = Ui.text(this, "Skip", 14, Ui.MUTED, true);
        skip.setPadding(dp(12), dp(10), dp(12), dp(10));
        skip.setVisibility(last ? View.INVISIBLE : View.VISIBLE);
        skip.setOnClickListener(v -> setContentView(onboarding(pg.length - 1)));
        LinearLayout.LayoutParams sk = new LinearLayout.LayoutParams(WRAP, WRAP);
        sk.gravity = Gravity.END;
        root.addView(skip, sk);
        root.addView(new Space(this), new LinearLayout.LayoutParams(1, 0, 1));

        TextView art = Ui.text(this, pg[i][0], 64, Ui.TEXT, false);
        art.setGravity(Gravity.CENTER);
        art.setBackground(Ui.ovalStroke(this, Ui.SURFACE, Ui.ACCENT));
        root.addView(art, new LinearLayout.LayoutParams(dp(150), dp(150)));

        TextView t = Ui.text(this, pg[i][1], 26, Ui.TEXT, true);
        t.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(MATCH, WRAP);
        tp.topMargin = dp(32);
        root.addView(t, tp);
        TextView sub = Ui.text(this, pg[i][2], 15, Ui.MUTED, false);
        sub.setGravity(last ? Gravity.START : Gravity.CENTER);
        sub.setLineSpacing(0, 1.15f);
        LinearLayout.LayoutParams sp2 = new LinearLayout.LayoutParams(MATCH, WRAP);
        sp2.topMargin = dp(12);
        root.addView(sub, sp2);
        root.addView(new Space(this), new LinearLayout.LayoutParams(1, 0, 1));

        LinearLayout dots = new LinearLayout(this);
        dots.setGravity(Gravity.CENTER);
        for (int d = 0; d < pg.length; d++) {
            View dot = new View(this);
            dot.setBackground(Ui.shape(this, d == i ? Ui.ACCENT : Ui.SURFACE2, false, 4));
            LinearLayout.LayoutParams dl = new LinearLayout.LayoutParams(dp(d == i ? 22 : 8), dp(8));
            dl.setMargins(dp(4), 0, dp(4), 0);
            dots.addView(dot, dl);
        }
        root.addView(dots, new LinearLayout.LayoutParams(MATCH, WRAP));

        TextView btn = Ui.text(this, last ? "Allow & continue" : "Next", 16, Color.WHITE, true);
        btn.setGravity(Gravity.CENTER);
        btn.setBackground(Ui.ripple(this, Ui.ACCENT, false, 28));
        btn.setOnClickListener(v -> {
            if (!last) setContentView(onboarding(i + 1));
            else requestPermissions(PERMS, 1);
        });
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(MATCH, dp(54));
        bp.topMargin = dp(24);
        root.addView(btn, bp);

        if (last) {
            TextView later = Ui.text(this, "Not now", 14, Ui.MUTED, true);
            later.setGravity(Gravity.CENTER);
            later.setPadding(0, dp(14), 0, dp(4));
            later.setOnClickListener(v -> finishOnboarding());
            root.addView(later, new LinearLayout.LayoutParams(MATCH, WRAP));
        }
        return root;
    }

    // ================= navigation =================

    ImageView[] tabIcons = new ImageView[4];
    TextView badge;
    View indicator;
    LinearLayout tabBarRow;
    int lastStyled = -1, lastAnim = -1;
    static final int[] TAB_ICONS = {R.drawable.ic_history, R.drawable.ic_people,
            R.drawable.ic_dialpad, R.drawable.ic_menu};

    View tabBar() {
        FrameLayout wrap = new FrameLayout(this);
        wrap.setBackgroundColor(Ui.SURFACE);
        LinearLayout bar = new LinearLayout(this);
        tabBarRow = bar;
        String[] names = {"Recents", "Contacts", "Keypad", "More"};
        for (int i = 0; i < 4; i++) {
            final int idx = i;
            LinearLayout item = new LinearLayout(this);
            item.setOrientation(LinearLayout.VERTICAL);
            item.setGravity(Gravity.CENTER);
            item.setPadding(0, dp(10), 0, dp(8));
            item.setBackground(Ui.ripple(this, Color.TRANSPARENT, false, 0));
            FrameLayout box = new FrameLayout(this);
            ImageView ic = new ImageView(this);
            ic.setImageResource(TAB_ICONS[i]);
            tabIcons[i] = ic;
            box.addView(ic, new FrameLayout.LayoutParams(dp(24), dp(24), Gravity.CENTER));
            if (i == 0) {
                badge = Ui.text(this, "", 10, Color.WHITE, true);
                badge.setGravity(Gravity.CENTER);
                badge.setPadding(dp(5), 0, dp(5), 0);
                badge.setBackground(Ui.shape(this, Ui.RED, false, 8));
                badge.setVisibility(View.GONE);
                FrameLayout.LayoutParams bl = new FrameLayout.LayoutParams(-2, dp(16), Gravity.TOP | Gravity.END);
                bl.setMargins(0, 0, dp(2), 0);
                box.addView(badge, bl);
            }
            item.addView(box, new LinearLayout.LayoutParams(dp(44), dp(28)));
            TextView lb = Ui.text(this, names[i], 10.5f, Ui.MUTED, false);
            lb.setGravity(Gravity.CENTER);
            lb.setPadding(0, dp(2), 0, 0);
            item.addView(lb);
            item.setOnClickListener(v -> {
                if (pref("vibration") && idx != tab) v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                show(idx);
            });
            bar.addView(item, new LinearLayout.LayoutParams(0, WRAP, 1));
            tabItems[i] = item;
        }
        wrap.addView(bar, new FrameLayout.LayoutParams(MATCH, WRAP));
        indicator = new View(this);
        indicator.setBackground(Ui.shape(this, Ui.ACCENT, false, 2));
        wrap.addView(indicator, new FrameLayout.LayoutParams(dp(28), dp(3), Gravity.TOP | Gravity.START));
        bar.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, orr, ob) -> moveIndicator(false));
        return wrap;
    }

    void moveIndicator(boolean animate) {
        if (tabBarRow == null || indicator == null) return;
        float w = tabBarRow.getWidth() / 4f;
        if (w <= 0) return;
        float x = tab * w + (w - dp(28)) / 2f;
        if (animate && pref("animations")) indicator.animate().translationX(x).setDuration(240)
                .setInterpolator(new android.view.animation.DecelerateInterpolator()).start();
        else { indicator.animate().cancel(); indicator.setTranslationX(x); }
    }

    void updateBadge() {
        if (badge == null) return;
        int n = 0;
        if (has(Manifest.permission.READ_CALL_LOG)) {
            try (Cursor c = getContentResolver().query(CallLog.Calls.CONTENT_URI,
                    new String[]{CallLog.Calls._ID},
                    CallLog.Calls.TYPE + "=" + CallLog.Calls.MISSED_TYPE + " AND " + CallLog.Calls.NEW + "=1",
                    null, null)) {
                if (c != null) n = c.getCount();
            } catch (Exception e) { }
        }
        badge.setText(n > 99 ? "99+" : String.valueOf(n));
        badge.setVisibility(n > 0 ? View.VISIBLE : View.GONE);
    }

    void markMissedRead() {
        try {
            ContentValues cv = new ContentValues();
            cv.put(CallLog.Calls.NEW, 0);
            getContentResolver().update(CallLog.Calls.CONTENT_URI, cv, CallLog.Calls.NEW + "=1", null);
        } catch (Exception e) { }
        updateBadge();
    }

    void addFab() {
        ImageView f = new ImageView(this);
        f.setImageResource(R.drawable.ic_dialpad);
        f.setColorFilter(Color.WHITE);
        f.setPadding(dp(16), dp(16), dp(16), dp(16));
        f.setBackground(Ui.glow(this, Ui.ACCENT));
        f.setElevation(dp(8));
        f.setOnClickListener(v -> show(KEYPAD));
        Ui.press(f);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(dp(60), dp(60), Gravity.BOTTOM | Gravity.END);
        lp.setMargins(0, 0, dp(20), dp(20));
        content.addView(f, lp);
        if (pref("animations")) {
            f.setScaleX(0f);
            f.setScaleY(0f);
            f.animate().scaleX(1f).scaleY(1f).setStartDelay(150).setDuration(320)
                    .setInterpolator(new android.view.animation.OvershootInterpolator(2f)).start();
        }
    }

    void styleTabs() {
        for (int i = 0; i < 4; i++) {
            final int ii = i;
            boolean on = i == tab;
            tabIcons[i].setColorFilter(on ? Ui.ACCENT : Ui.MUTED);
            tabIcons[i].setAlpha(on ? 1f : 0.85f);
            ((TextView) tabItems[i].getChildAt(1)).setTextColor(on ? Ui.ACCENT : Ui.MUTED);
            if (on && lastStyled != -1 && lastStyled != tab && pref("animations")) {
                tabIcons[i].animate().scaleX(1.3f).scaleY(1.3f).setDuration(120)
                        .withEndAction(() -> tabIcons[ii].animate().scaleX(1f).scaleY(1f).setDuration(160).start())
                        .start();
            }
        }
        lastStyled = tab;
        moveIndicator(true);
        updateBadge();
    }

    void show(int t) {
        tab = t;
        content.removeAllViews();
        numberView = null;
        if (t == HOME) home();
        else if (t == CONTACTS) contactsTab();
        else if (t == KEYPAD) keypad();
        else moreTab();
        styleTabs();
        if (mainRoot != null) mainRoot.setBackground(Ui.bg(this, Wallpaper.KEYS[t]));
        if (pref("animations")) {
            content.setAlpha(0f);
            content.setTranslationY(dp(8));
            content.animate().alpha(1f).translationY(0f).setDuration(220)
                    .setInterpolator(new android.view.animation.DecelerateInterpolator()).start();
        } else {
            content.setAlpha(1f);
            content.setTranslationY(0f);
        }
    }

    LinearLayout screen(String title, String action, Runnable r) {
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        LinearLayout head = new LinearLayout(this);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.setPadding(dp(20), dp(20), dp(12), dp(8));
        head.addView(Ui.text(this, title, 28, Ui.TEXT, true), new LinearLayout.LayoutParams(0, WRAP, 1));
        if (action != null) {
            TextView act = Ui.text(this, action, 22, Ui.ACCENT, true);
            act.setGravity(Gravity.CENTER);
            act.setBackground(Ui.ovalStroke(this, Ui.SURFACE, Ui.SURFACE2));
            act.setOnClickListener(v -> r.run());
            head.addView(act, new LinearLayout.LayoutParams(dp(44), dp(44)));
        }
        col.addView(head, new LinearLayout.LayoutParams(MATCH, WRAP));
        content.addView(col, new FrameLayout.LayoutParams(MATCH, MATCH));
        return col;
    }

    LinearLayout emptyView(String emoji, String msg) {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setGravity(Gravity.CENTER);
        c.setPadding(dp(32), dp(32), dp(32), dp(32));
        TextView e = Ui.text(this, emoji, 40, Ui.TEXT, false);
        e.setGravity(Gravity.CENTER);
        TextView t = Ui.text(this, msg, 15, Ui.MUTED, false);
        t.setGravity(Gravity.CENTER);
        t.setPadding(0, dp(8), 0, 0);
        c.addView(e);
        c.addView(t);
        return c;
    }

    void message(String emoji, String title, String sub, String action, Runnable r) {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setGravity(Gravity.CENTER);
        c.setPadding(dp(32), 0, dp(32), 0);
        TextView e = Ui.text(this, emoji, 48, Ui.TEXT, false);
        e.setGravity(Gravity.CENTER);
        TextView t = Ui.text(this, title, 18, Ui.TEXT, true);
        t.setGravity(Gravity.CENTER);
        TextView s = Ui.text(this, sub, 14, Ui.MUTED, false);
        s.setGravity(Gravity.CENTER);
        s.setPadding(0, dp(6), 0, 0);
        c.addView(e);
        c.addView(t);
        c.addView(s);
        if (action != null) {
            TextView b = Ui.text(this, action, 15, Color.WHITE, true);
            b.setGravity(Gravity.CENTER);
            b.setPadding(dp(28), dp(13), dp(28), dp(13));
            b.setBackground(Ui.ripple(this, Ui.ACCENT, false, 26));
            b.setOnClickListener(v -> r.run());
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(WRAP, WRAP);
            lp.topMargin = dp(20);
            c.addView(b, lp);
        }
        content.addView(c, new FrameLayout.LayoutParams(MATCH, MATCH));
    }

    void openSettings() {
        startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:" + getPackageName())));
    }

    TextView chip(String t, boolean on) {
        TextView c = Ui.text(this, t, 13, on ? Color.WHITE : Ui.MUTED, true);
        c.setPadding(dp(16), dp(8), dp(16), dp(8));
        c.setBackground(on ? Ui.ripple(this, Ui.ACCENT, false, 20)
                : Ui.card(this, Ui.SURFACE, 20, Ui.SURFACE2));
        return c;
    }

    View chipBar(String[] names, int sel, java.util.function.IntConsumer pick) {
        HorizontalScrollView hs = new HorizontalScrollView(this);
        hs.setHorizontalScrollBarEnabled(false);
        LinearLayout row = new LinearLayout(this);
        row.setPadding(dp(20), dp(6), dp(20), dp(8));
        for (int i = 0; i < names.length; i++) {
            final int idx = i;
            TextView c = chip(names[i], i == sel);
            c.setOnClickListener(v -> pick.accept(idx));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(WRAP, WRAP);
            lp.rightMargin = dp(8);
            row.addView(c, lp);
        }
        hs.addView(row);
        return hs;
    }

    EditText searchBox(String hint) {
        EditText e = new EditText(this);
        e.setHint("🔍  " + hint);
        e.setHintTextColor(Ui.MUTED);
        e.setTextColor(Ui.TEXT);
        e.setTextSize(15);
        e.setSingleLine(true);
        e.setPadding(dp(18), dp(12), dp(18), dp(12));
        e.setBackground(Ui.card(this, Ui.SURFACE, 26, Ui.SURFACE2));
        return e;
    }

    // ================= actions =================

    void details(String number, String name) {
        Intent i = new Intent(this, ContactDetailsActivity.class);
        i.putExtra("number", number).putExtra("name", name);
        startActivity(i);
    }

    void call() { Caller.dial(this, num.toString()); }

    void callNumber(String n) {
        num = new StringBuilder(n);
        Caller.dial(this, n);
    }

    void redial() {
        String last = "";
        if (has(Manifest.permission.READ_CALL_LOG)) {
            try { last = CallLog.Calls.getLastOutgoingCall(this); } catch (Exception e) { }
        }
        if (last == null || last.isEmpty()) {
            toast("Enter a number to call");
        } else {
            num = new StringBuilder(last);
            updateDisplay();
            toast("Last dialed number. Press call to confirm");
        }
    }

    void addContact(String n) {
        if (n == null || n.isEmpty()) return;
        Intent in = new Intent(Intent.ACTION_INSERT, ContactsContract.Contacts.CONTENT_URI);
        in.putExtra(ContactsContract.Intents.Insert.PHONE, n);
        startActivity(in);
    }

    void newContact() {
        startActivity(new Intent(Intent.ACTION_INSERT, ContactsContract.Contacts.CONTENT_URI));
    }

    void block(String n) {
        try {
            ContentValues cv = new ContentValues();
            cv.put(BlockedNumberContract.BlockedNumbers.COLUMN_ORIGINAL_NUMBER, n);
            getContentResolver().insert(BlockedNumberContract.BlockedNumbers.CONTENT_URI, cv);
            toast("Blocked " + n);
        } catch (Exception e) {
            toast("Can't block: set Dialer as the default phone app");
        }
    }

    void deleteLog(String n) {
        try {
            int rows = getContentResolver().delete(CallLog.Calls.CONTENT_URI,
                    Favs.logSel(n), Favs.logArgs(n));
            toast(rows > 0 ? "Deleted" : "Nothing to delete");
            recentsCache = null; // don't flash the deleted entry from the cached list
            show(tab);
        } catch (Exception e) {
            toast("Can't delete: set Dialer as the default phone app");
        }
    }

    void clearAll() {
        new AlertDialog.Builder(this).setTitle("Clear call history?")
                .setMessage("All recent calls will be deleted.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Clear", (d, i) -> {
                    try {
                        getContentResolver().delete(CallLog.Calls.CONTENT_URI, null, null);
                        recentsCache = null;
                        show(tab);
                    } catch (Exception e) {
                        toast("Can't clear: set Dialer as the default phone app");
                    }
                }).show();
    }

    void menu(String number, String title, boolean recent) {
        final List<String> items = new ArrayList<>(Arrays.asList(
                "Call", "View details", "Copy to keypad", "Send message", "Add to contacts", "Block number", "Report spam", "Add note",
                Favs.has(this, number) ? "Remove favorite" : "Add favorite"));
        if (recent) items.add("Delete from history");
        new AlertDialog.Builder(this).setTitle(title)
                .setItems(items.toArray(new String[0]), (d, i) -> {
                    String it = items.get(i);
                    if (it.equals("Call")) callNumber(number);
                    else if (it.equals("View details")) details(number, title);
                    else if (it.equals("Copy to keypad")) { num = new StringBuilder(number); show(KEYPAD); }
                    else if (it.equals("Send message")) {
                        try {
                            startActivity(new Intent(Intent.ACTION_SENDTO,
                                    Uri.parse("smsto:" + Uri.encode(number))));
                        } catch (Exception e) { toast("No messaging app found"); }
                    }
                    else if (it.equals("Add to contacts")) addContact(number);
                    else if (it.equals("Block number")) block(number);
                    else if (it.equals("Report spam")) { SpamGuard.report(this, number); block(number); }
                    else if (it.equals("Add note")) Notes.edit(this, number, title, null);
                    else if (it.equals("Add favorite") || it.equals("Remove favorite")) {
                        boolean on = Favs.toggle(this, number, title);
                        toast(on ? "Added to favorites" : "Removed from favorites");
                        contactCache = null;
                        show(tab);
                    }
                    else deleteLog(number);
                }).show();
    }

    // ================= keypad =================

    static String sub(String k) {
        switch (k) {
            case "2": return "ABC"; case "3": return "DEF"; case "4": return "GHI";
            case "5": return "JKL"; case "6": return "MNO"; case "7": return "PQRS";
            case "8": return "TUV"; case "9": return "WXYZ"; case "0": return "+";
            default: return "";
        }
    }

    void beep(String k) {
        if (!pref("tones")) return;
        try {
            if (tone == null) tone = new ToneGenerator(AudioManager.STREAM_DTMF, 70);
            int t;
            if (k.equals("*")) t = ToneGenerator.TONE_DTMF_S;
            else if (k.equals("#")) t = ToneGenerator.TONE_DTMF_P;
            else t = ToneGenerator.TONE_DTMF_0 + (k.charAt(0) - '0');
            tone.startTone(t, 90);
        } catch (Exception e) { }
    }

    void speak(String k) {
        if (!getSharedPreferences("app", MODE_PRIVATE).getBoolean("a11y_speak", false)) return;
        if (tts == null) {
            tts = new android.speech.tts.TextToSpeech(this, st -> ttsOk = st == android.speech.tts.TextToSpeech.SUCCESS);
            return;
        }
        if (ttsOk) tts.speak(k.equals("*") ? "star" : k.equals("#") ? "hash" : k,
                android.speech.tts.TextToSpeech.QUEUE_FLUSH, null, "k");
    }

    void append(String k) {
        num.append(k);
        updateDisplay();
    }

    String pretty(String raw) {
        if (raw.isEmpty() || raw.contains("*") || raw.contains("#")) return raw;
        try {
            String cc = Locale.getDefault().getCountry();
            String f = PhoneNumberUtils.formatNumber(raw, cc.isEmpty() ? "IN" : cc);
            return f != null ? f : raw;
        } catch (Exception e) { return raw; }
    }

    void updateDisplay() {
        if (numberView == null) return;
        boolean any = num.length() > 0;
        String shown = pretty(num.toString());
        numberView.setText(shown);
        int len = shown.length();
        numberView.setTextSize(len > 16 ? 22 : len > 12 ? 28 : 36);
        backBtn.setVisibility(View.VISIBLE);
        backBtn.setAlpha(any ? 1f : 0.3f);
        addBtn.setVisibility(any ? View.VISIBLE : View.INVISIBLE);
        nameView.setText(any ? Ui.lookupName(this, num.toString()) : "Enter a number · v32");
    }

    void pressAnim(View v) {
        if (!pref("animations")) return;
        v.setOnTouchListener((view, e) -> {
            int a = e.getActionMasked();
            if (a == MotionEvent.ACTION_DOWN) {
                view.animate().scaleX(0.9f).scaleY(0.9f).setDuration(60).start();
            } else if (a == MotionEvent.ACTION_UP || a == MotionEvent.ACTION_CANCEL) {
                view.animate().scaleX(1f).scaleY(1f).setDuration(90).start();
            }
            return false;
        });
    }

    View key(String k) {
        LinearLayout key = new LinearLayout(this);
        key.setOrientation(LinearLayout.VERTICAL);
        key.setGravity(Gravity.CENTER);
        key.setBackground(Ui.ovalStroke(this, Ui.SURFACE, Ui.SURFACE2));
        TextView a = Ui.text(this, k, 28, Ui.TEXT, false);
        a.setGravity(Gravity.CENTER);
        key.addView(a);
        String sb = sub(k);
        if (!sb.isEmpty()) {
            TextView b = Ui.text(this, sb, 9, Ui.MUTED, true);
            b.setLetterSpacing(0.12f);
            b.setGravity(Gravity.CENTER);
            key.addView(b);
        }
        pressAnim(key);
        key.setOnClickListener(v -> {
            if (pref("vibration")) v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
            beep(k);
            speak(k);
            append(k);
        });
        if (k.equals("0")) {
            key.setOnLongClickListener(v -> {
                v.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                append("+");
                return true;
            });
        }
        if (k.matches("[1-9]")) {
            key.setOnLongClickListener(v -> {
                v.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                SpeedDial.press(this, k);
                return true;
            });
        }
        return key;
    }

    LinearLayout cell(View v, int sizeDp) {
        LinearLayout c = new LinearLayout(this);
        c.setGravity(Gravity.CENTER);
        c.addView(v, new LinearLayout.LayoutParams(dp(sizeDp), dp(sizeDp)));
        return c;
    }

    void keypad() {
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(24), dp(14), dp(24), dp(12));

        int sims = Caller.simCount(this);
        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER);
        if (sims > 1) {
            int sel = getSharedPreferences("app", MODE_PRIVATE).getInt("sim", 0);
            for (int i = 0; i < sims; i++) {
                final int idx = i;
                TextView c = chip("SIM " + (i + 1), i == sel);
                c.setOnClickListener(v -> {
                    getSharedPreferences("app", MODE_PRIVATE).edit().putInt("sim", idx).apply();
                    show(KEYPAD);
                });
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(WRAP, WRAP);
                lp.setMargins(dp(4), 0, dp(4), 0);
                top.addView(c, lp);
            }
        }
        col.addView(top, new LinearLayout.LayoutParams(MATCH, WRAP));

        LinearLayout display = new LinearLayout(this);
        display.setOrientation(LinearLayout.VERTICAL);
        display.setGravity(Gravity.CENTER);
        numberView = Ui.text(this, "", 36, Ui.TEXT, false);
        numberView.setGravity(Gravity.CENTER);
        numberView.setSingleLine(true);
        numberView.setOnLongClickListener(v -> {
            ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
            if (cm != null && cm.hasPrimaryClip() && cm.getPrimaryClip().getItemCount() > 0) {
                CharSequence t = cm.getPrimaryClip().getItemAt(0).coerceToText(this);
                if (t != null) {
                    String clean = t.toString().replaceAll("[^0-9+*#]", "");
                    if (!clean.isEmpty()) {
                        num = new StringBuilder(clean);
                        updateDisplay();
                        toast("Pasted");
                    }
                }
            }
            return true;
        });
        nameView = Ui.text(this, "", 15, Ui.MUTED, false);
        nameView.setGravity(Gravity.CENTER);
        nameView.setPadding(0, dp(6), 0, 0);
        display.addView(numberView, new LinearLayout.LayoutParams(MATCH, WRAP));
        display.addView(nameView, new LinearLayout.LayoutParams(MATCH, WRAP));
        col.addView(display, new LinearLayout.LayoutParams(MATCH, 0, 1));

        String[][] rows = {{"1", "2", "3"}, {"4", "5", "6"}, {"7", "8", "9"}, {"*", "0", "#"}};
        for (String[] r : rows) {
            LinearLayout row = new LinearLayout(this);
            for (String k : r) row.addView(cell(key(k), 72), new LinearLayout.LayoutParams(0, dp(82), 1));
            col.addView(row, new LinearLayout.LayoutParams(MATCH, WRAP));
        }

        addBtn = new ImageView(this);
        addBtn.setImageResource(R.drawable.ic_person_add);
        addBtn.setColorFilter(Ui.MUTED);
        addBtn.setPadding(dp(22), dp(22), dp(22), dp(22));
        addBtn.setBackground(Ui.ripple(this, Color.TRANSPARENT, true, 0));
        addBtn.setOnClickListener(v -> addContact(num.toString()));

        ImageView callB = Ui.callIcon(this, 72, Ui.GREEN, 0);
        pressAnim(callB);
        callB.setOnClickListener(v -> {
            if (pref("vibration")) v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
            if (num.length() == 0) redial(); else call();
        });

        backBtn = new ImageView(this);
        backBtn.setImageResource(R.drawable.ic_backspace);
        backBtn.setColorFilter(Ui.TEXT);
        backBtn.setPadding(dp(22), dp(22), dp(22), dp(22));
        backBtn.setBackground(Ui.ripple(this, Color.TRANSPARENT, true, 0));
        backBtn.setOnClickListener(v -> {
            if (num.length() > 0) { num.deleteCharAt(num.length() - 1); updateDisplay(); }
        });
        backBtn.setOnLongClickListener(v -> { num.setLength(0); updateDisplay(); return true; });

        LinearLayout last = new LinearLayout(this);
        FrameLayout addW = new FrameLayout(this);
        addW.addView(addBtn, new FrameLayout.LayoutParams(dp(64), dp(64), Gravity.CENTER));
        last.addView(addW, new LinearLayout.LayoutParams(0, dp(82), 1));
        last.addView(cell(callB, 72), new LinearLayout.LayoutParams(0, dp(82), 1));
        FrameLayout backW = new FrameLayout(this);
        backW.addView(backBtn, new FrameLayout.LayoutParams(dp(64), dp(64), Gravity.CENTER));
        last.addView(backW, new LinearLayout.LayoutParams(0, dp(82), 1));
        col.addView(last, new LinearLayout.LayoutParams(MATCH, WRAP));

        content.addView(col, new FrameLayout.LayoutParams(MATCH, MATCH));
        updateDisplay();
    }

    // ================= list rows =================

    static class Row {
        boolean header, missed, recent;
        String title, base, sub, number, avatarName, photo;
        int count = 1;
    }

    static class Holder { TextView av, title, sub; ImageView call; }

    class RowAdapter extends BaseAdapter {
        final List<Row> rows;
        RowAdapter(List<Row> r) { rows = r; }
        public int getCount() { return rows.size(); }
        public Object getItem(int i) { return rows.get(i); }
        public long getItemId(int i) { return i; }
        public int getViewTypeCount() { return 2; }
        public int getItemViewType(int i) { return rows.get(i).header ? 1 : 0; }
        public boolean isEnabled(int i) { return !rows.get(i).header; }

        public View getView(int pos, View v, ViewGroup parent) {
            Row r = rows.get(pos);
            if (r.header) {
                TextView t = (TextView) v;
                if (t == null) {
                    t = Ui.text(MainActivity.this, "", 13, Ui.ACCENT, true);
                    t.setPadding(dp(20), dp(14), dp(20), dp(4));
                }
                t.setText(r.title);
                return t;
            }
            Holder h;
            if (v == null) {
                LinearLayout item = new LinearLayout(MainActivity.this);
                item.setGravity(Gravity.CENTER_VERTICAL);
                item.setPadding(dp(20), dp(10), dp(20), dp(10));
                item.setBackground(Ui.ripple(MainActivity.this, Color.TRANSPARENT, false, 0));
                h = new Holder();
                h.av = Ui.text(MainActivity.this, "", 18, Color.WHITE, true);
                item.addView(h.av, new LinearLayout.LayoutParams(dp(46), dp(46)));
                LinearLayout txt = new LinearLayout(MainActivity.this);
                txt.setOrientation(LinearLayout.VERTICAL);
                h.title = Ui.text(MainActivity.this, "", 16, Ui.TEXT, false);
                h.title.setSingleLine(true);
                h.title.setEllipsize(TextUtils.TruncateAt.END);
                h.sub = Ui.text(MainActivity.this, "", 12.5f, Ui.MUTED, false);
                h.sub.setSingleLine(true);
                h.sub.setEllipsize(TextUtils.TruncateAt.END);
                txt.addView(h.title);
                txt.addView(h.sub);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, WRAP, 1);
                lp.leftMargin = dp(14);
                item.addView(txt, lp);
                h.call = new ImageView(MainActivity.this);
                h.call.setImageResource(R.drawable.ic_call);
                h.call.setColorFilter(Ui.GREEN);
                h.call.setPadding(dp(10), dp(10), dp(10), dp(10));
                h.call.setBackground(Ui.ovalStroke(MainActivity.this, Ui.mix(Ui.SURFACE, Ui.GREEN, 0.16f), Ui.mix(Ui.SURFACE2, Ui.GREEN, 0.45f)));
                Ui.press(h.call);
                item.addView(h.call, new LinearLayout.LayoutParams(dp(40), dp(40)));
                item.setTag(h);
                v = item;
            } else {
                h = (Holder) v.getTag();
            }
            h.title.setText(r.title);
            h.title.setTextColor(r.missed ? Ui.RED : Ui.TEXT);
            h.sub.setText(r.sub);
            final String callNum = r.number;
            h.call.setVisibility(r.recent && callNum != null ? View.VISIBLE : View.GONE);
            h.call.setOnClickListener(x -> { if (callNum != null) callNumber(callNum); });
            Ui.bindAvatar(MainActivity.this, h.av, r.avatarName);
            Photos.bind(MainActivity.this, h.av, r.number, r.photo, false);
            v.animate().cancel();
            if (pref("animations") && pos > lastAnim && pos < 12) {
                lastAnim = pos;
                v.setAlpha(0f);
                v.setTranslationY(dp(22));
                v.animate().alpha(1f).translationY(0f).setStartDelay(pos * 40L).setDuration(280).start();
            } else {
                v.setAlpha(1f);
                v.setTranslationY(0f);
            }
            return v;
        }
    }

    ListView list(List<Row> rows, boolean recent) {
        lastAnim = -1;
        ListView lv = new ListView(this);
        lv.setDivider(null);
        lv.setDividerHeight(0);
        lv.setSelector(new ColorDrawable(Color.TRANSPARENT));
        lv.setVerticalScrollBarEnabled(false);
        lv.setAdapter(new RowAdapter(rows));
        lv.setOnItemClickListener((p, v, pos, id) -> {
            Row r = (Row) p.getItemAtPosition(pos);
            if (r.number == null) return;
            if ("Dial this number".equals(r.sub)) callNumber(r.number);
            else details(r.number, r.base != null ? r.base : r.title);
        });
        lv.setOnItemLongClickListener((p, v, pos, id) -> {
            Row r = (Row) p.getItemAtPosition(pos);
            if (r.number != null) {
                v.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                menu(r.number, r.base != null ? r.base : r.title, recent);
            }
            return true;
        });
        return lv;
    }

    // ================= home / recents =================

    String greeting() {
        int h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        return h < 12 ? "Good morning" : h < 17 ? "Good afternoon" : "Good evening";
    }

    List<String[]> favList() {
        List<String[]> out = new ArrayList<>();
        for (String[] f : Favs.list(this)) out.add(new String[]{f[1], f[2]});
        return out;
    }

    String when(long d) {
        String time = DateUtils.formatDateTime(this, d, DateUtils.FORMAT_SHOW_TIME);
        if (DateUtils.isToday(d)) return "Today, " + time;
        if (DateUtils.isToday(d + DateUtils.DAY_IN_MILLIS)) return "Yesterday, " + time;
        return DateUtils.formatDateTime(this, d, DateUtils.FORMAT_SHOW_DATE
                | DateUtils.FORMAT_ABBREV_MONTH | DateUtils.FORMAT_SHOW_TIME);
    }

    String fmtDur(long s) {
        return s < 60 ? s + "s" : (s / 60) + "m " + (s % 60) + "s";
    }

    List<Row> loadRecents() {
        List<Row> rows = new ArrayList<>();
        java.util.Map<String, Row> seen = new java.util.HashMap<>();
        String sel = null;
        if (filter == 1) sel = CallLog.Calls.TYPE + "=" + CallLog.Calls.MISSED_TYPE;
        else if (filter == 2) sel = CallLog.Calls.TYPE + "=" + CallLog.Calls.INCOMING_TYPE;
        else if (filter == 3) sel = CallLog.Calls.TYPE + "=" + CallLog.Calls.OUTGOING_TYPE;
        int scanned = 0;
        try (Cursor c = getContentResolver().query(CallLog.Calls.CONTENT_URI,
                new String[]{CallLog.Calls.NUMBER, CallLog.Calls.CACHED_NAME,
                        CallLog.Calls.TYPE, CallLog.Calls.DATE, CallLog.Calls.DURATION},
                sel, null, CallLog.Calls.DATE + " DESC")) {
            while (c != null && c.moveToNext() && rows.size() < 200 && scanned++ < 5000) {
                String n = c.getString(0);
                String name = c.getString(1);
                int type = c.getInt(2);
                long date = c.getLong(3);
                long secs = c.getLong(4);
                boolean hasNum = n != null && !n.isEmpty() && !n.startsWith("-");
                boolean hasName = name != null && !name.isEmpty();
                String k = "unknown";
                if (hasNum) {
                    k = Favs.key(n);
                    if (k.isEmpty()) k = n;
                }
                Row ex = seen.get(k);
                if (ex != null) {
                    ex.count++;
                    ex.title = ex.base + " (" + ex.count + ")";
                    continue;
                }
                String label;
                if (type == CallLog.Calls.OUTGOING_TYPE) label = "↗ Outgoing";
                else if (type == CallLog.Calls.MISSED_TYPE) label = "✖ Missed";
                else if (type == CallLog.Calls.REJECTED_TYPE) label = "⊘ Declined";
                else if (type == CallLog.Calls.BLOCKED_TYPE) label = "⊘ Blocked";
                else label = "↙ Incoming";
                boolean answered = type == CallLog.Calls.OUTGOING_TYPE || type == CallLog.Calls.INCOMING_TYPE;
                Row r = new Row();
                r.recent = true;
                r.number = hasNum ? n : null;
                r.title = hasName ? name : (hasNum ? n : "Unknown");
                r.base = r.title;
                r.avatarName = hasName ? name : null;
                r.missed = type == CallLog.Calls.MISSED_TYPE;
                r.sub = label + "  ·  " + when(date) + (answered && secs > 0 ? "  ·  " + fmtDur(secs) : "");
                rows.add(r);
                seen.put(k, r);
            }
        } catch (Exception e) { }
        return rows;
    }

    void home() {
        if (!has(Manifest.permission.READ_CALL_LOG)) {
            message("📵", "Call history unavailable",
                    "Allow the Call logs permission to see your recent calls.",
                    "Open settings", this::openSettings);
            return;
        }
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        content.addView(col, new FrameLayout.LayoutParams(MATCH, MATCH));

        LinearLayout head = new LinearLayout(this);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.setPadding(dp(20), dp(18), dp(16), dp(4));
        LinearLayout titles = new LinearLayout(this);
        titles.setOrientation(LinearLayout.VERTICAL);
        titles.addView(Ui.text(this, greeting(), 13, Ui.MUTED, false));
        titles.addView(Ui.text(this, "Dialer", 26, Ui.TEXT, true));
        head.addView(titles, new LinearLayout.LayoutParams(0, WRAP, 1));
        ImageView gear = new ImageView(this);
        gear.setImageResource(R.drawable.ic_settings);
        gear.setColorFilter(Ui.TEXT);
        gear.setPadding(dp(11), dp(11), dp(11), dp(11));
        Ui.press(gear);
        gear.setBackground(Ui.ovalStroke(this, Ui.SURFACE, Ui.SURFACE2));
        gear.setOnClickListener(v -> startActivity(new Intent(this, SettingsActivity.class)));
        head.addView(gear, new LinearLayout.LayoutParams(dp(44), dp(44)));
        col.addView(head, new LinearLayout.LayoutParams(MATCH, WRAP));

        EditText search = searchBox("Search contacts or numbers");
        LinearLayout.LayoutParams sl = new LinearLayout.LayoutParams(MATCH, WRAP);
        sl.setMargins(dp(20), dp(10), dp(20), dp(6));
        col.addView(search, sl);

        FrameLayout body = new FrameLayout(this);
        col.addView(body, new LinearLayout.LayoutParams(MATCH, 0, 1));
        renderHome(body);
        addFab();
        search.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
            public void onTextChanged(CharSequence s, int a, int b, int c) { }
            public void afterTextChanged(Editable e) {
                String q = e.toString().trim();
                if (q.isEmpty()) renderHome(body); else renderSearch(body, q);
            }
        });
    }

    void renderHome(FrameLayout body) {
        body.removeAllViews();
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        body.addView(col, new FrameLayout.LayoutParams(MATCH, MATCH));

        List<String[]> favs = favList();
        if (!favs.isEmpty()) {
            HorizontalScrollView hs = new HorizontalScrollView(this);
            hs.setHorizontalScrollBarEnabled(false);
            LinearLayout strip = new LinearLayout(this);
            strip.setPadding(dp(14), dp(6), dp(14), dp(4));
            for (String[] f : favs) {
                final String name = f[0], number = f[1];
                LinearLayout item = new LinearLayout(this);
                item.setOrientation(LinearLayout.VERTICAL);
                item.setGravity(Gravity.CENTER_HORIZONTAL);
                item.setPadding(0, dp(4), 0, dp(4));
                TextView av = Ui.text(this, "", 20, Color.WHITE, true);
                Ui.bindAvatar(this, av, name);
                Photos.bind(this, av, number, null, false);
                item.addView(av, new LinearLayout.LayoutParams(dp(56), dp(56)));
                TextView nm = Ui.text(this, name, 11.5f, Ui.TEXT, false);
                nm.setSingleLine(true);
                nm.setEllipsize(TextUtils.TruncateAt.END);
                nm.setGravity(Gravity.CENTER);
                nm.setPadding(0, dp(6), 0, 0);
                item.addView(nm, new LinearLayout.LayoutParams(MATCH, WRAP));
                item.setOnClickListener(v -> Caller.dial(this, number));
                item.setOnLongClickListener(v -> { menu(number, name, false); return true; });
                strip.addView(item, new LinearLayout.LayoutParams(dp(74), WRAP));
            }
            hs.addView(strip);
            col.addView(hs, new LinearLayout.LayoutParams(MATCH, WRAP));
        }

        LinearLayout rh = new LinearLayout(this);
        rh.setGravity(Gravity.CENTER_VERTICAL);
        rh.setPadding(dp(20), dp(10), dp(12), dp(2));
        rh.addView(Ui.text(this, "Recent calls", 16, Ui.TEXT, true), new LinearLayout.LayoutParams(0, WRAP, 1));
        TextView clr = Ui.text(this, "Clear", 13, Ui.ACCENT, true);
        clr.setPadding(dp(12), dp(8), dp(12), dp(8));
        clr.setOnClickListener(v -> clearAll());
        rh.addView(clr);
        col.addView(rh, new LinearLayout.LayoutParams(MATCH, WRAP));

        col.addView(chipBar(new String[]{"All", "Missed", "Incoming", "Outgoing"}, filter,
                i -> { filter = i; if (i == 1) markMissedRead(); renderHome(body); }), new LinearLayout.LayoutParams(MATCH, WRAP));

        // show what was loaded last time straight away, then refresh from the call log in the background
        final FrameLayout slot = new FrameLayout(this);
        col.addView(slot, new LinearLayout.LayoutParams(MATCH, 0, 1));
        if (recentsCache != null && recentsFilter == filter) applyRecents(slot, recentsCache, false);
        loadRecentsAsync(slot);
    }

    void applyRecents(FrameLayout slot, List<Row> rows, boolean keepScroll) {
        int pos = 0, top = 0;
        if (keepScroll) {
            ListView old = findList(slot);
            if (old != null) {
                pos = old.getFirstVisiblePosition();
                View c0 = old.getChildAt(0);
                top = c0 == null ? 0 : c0.getTop();
            }
        }
        slot.removeAllViews();
        if (rows.isEmpty()) {
            slot.addView(emptyView("📞", filter == 1 ? "No missed calls" : "No calls to show"),
                    new FrameLayout.LayoutParams(MATCH, MATCH));
            return;
        }
        final ListView lv = list(rows, true);
        slot.addView(lv, new FrameLayout.LayoutParams(MATCH, MATCH));
        if (pos > 0) {
            final int p = pos, t = top;
            lv.post(() -> lv.setSelectionFromTop(p, t));
        }
    }

    void loadRecentsAsync(final FrameLayout slot) {
        final int gen = ++recentsGen;
        final int f = filter;
        try {
            bg.execute(() -> {
                final List<Row> rows = loadRecents();
                runOnUiThread(() -> {
                    // ignore the result if the screen moved on while the call log was being read
                    if (isDestroyed() || gen != recentsGen || tab != HOME || !slot.isAttachedToWindow()) return;
                    boolean shown = recentsCache != null && recentsFilter == f;
                    boolean same = shown && sameRows(recentsCache, rows);
                    recentsCache = rows;
                    recentsFilter = f;
                    if (!same) applyRecents(slot, rows, shown);
                });
            });
        } catch (java.util.concurrent.RejectedExecutionException e) { }
    }

    static boolean sameRows(List<Row> a, List<Row> b) {
        if (a.size() != b.size()) return false;
        for (int i = 0; i < a.size(); i++) {
            Row x = a.get(i), y = b.get(i);
            if (!Objects.equals(x.title, y.title) || !Objects.equals(x.sub, y.sub)
                    || !Objects.equals(x.number, y.number)) return false;
        }
        return true;
    }

    void renderSearch(FrameLayout body, String q) {
        body.removeAllViews();
        List<Row> rows = new ArrayList<>();
        for (Row r : allContacts()) if (matches(r, q)) rows.add(r);
        boolean digits = q.matches("[0-9+ ]+");
        String qd = q.replaceAll("[^0-9+]", "");
        if (digits && qd.length() >= 3) {
            Row r = new Row();
            r.title = "Call " + q;
            r.sub = "Dial this number";
            r.number = qd;
            rows.add(0, r);
        }
        if (rows.isEmpty()) { body.addView(emptyView("🔍", "No matches")); return; }
        body.addView(list(rows, false), new FrameLayout.LayoutParams(MATCH, MATCH));
    }

    // ================= contacts =================

    static String t9(String s) {
        StringBuilder b = new StringBuilder();
        for (char ch : s.toLowerCase(Locale.ROOT).toCharArray()) {
            if (ch >= '0' && ch <= '9') b.append(ch);
            else if ("abc".indexOf(ch) >= 0) b.append('2');
            else if ("def".indexOf(ch) >= 0) b.append('3');
            else if ("ghi".indexOf(ch) >= 0) b.append('4');
            else if ("jkl".indexOf(ch) >= 0) b.append('5');
            else if ("mno".indexOf(ch) >= 0) b.append('6');
            else if ("pqrs".indexOf(ch) >= 0) b.append('7');
            else if ("tuv".indexOf(ch) >= 0) b.append('8');
            else if ("wxyz".indexOf(ch) >= 0) b.append('9');
        }
        return b.toString();
    }

    boolean matches(Row r, String q) {
        String nm = r.base == null ? "" : r.base.toLowerCase(Locale.ROOT);
        if (nm.contains(q.toLowerCase(Locale.ROOT))) return true;
        String qd = q.replaceAll("[^0-9]", "");
        if (qd.isEmpty()) return false;
        String nd = r.number == null ? "" : r.number.replaceAll("[^0-9]", "");
        if (nd.contains(qd)) return true;
        return q.matches("[0-9 ]+") && t9(nm).contains(qd);
    }

    List<Row> allContacts() {
        // the cached list is also dropped when favourites change, because it carries the star next to a name
        if (contactCache != null && contactCacheVer == Favs.version) return contactCache;
        int ver = Favs.version;
        List<Row> rows = buildContacts();
        contactCache = rows;
        contactCacheVer = ver;
        return rows;
    }

    // warms the cache in the background so the Contacts tab and search open instantly
    void preloadContacts() {
        if (preloading || !has(Manifest.permission.READ_CONTACTS)
                || (contactCache != null && contactCacheVer == Favs.version)) return;
        preloading = true;
        final int ver = Favs.version, gen = contactGen;
        try {
            bg.execute(() -> {
                final List<Row> rows = buildContacts();
                runOnUiThread(() -> {
                    preloading = false;
                    // drop the result if the contacts changed while it was being read
                    if (isDestroyed() || gen != contactGen) return;
                    if (contactCache == null || contactCacheVer != Favs.version) {
                        contactCache = rows;
                        contactCacheVer = ver;
                    }
                });
            });
        } catch (java.util.concurrent.RejectedExecutionException e) { preloading = false; }
    }

    List<Row> buildContacts() {
        List<Row> rows = new ArrayList<>();
        if (!has(Manifest.permission.READ_CONTACTS)) return rows;
        Set<String> seen = new HashSet<>();
        String NAME = ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME;
        String NUM = ContactsContract.CommonDataKinds.Phone.NUMBER;
        try (Cursor c = getContentResolver().query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                new String[]{NAME, NUM, ContactsContract.CommonDataKinds.Phone.PHOTO_THUMBNAIL_URI},
                null, null, NAME + " ASC")) {
            while (c != null && c.moveToNext()) {
                String name = c.getString(0);
                String n = c.getString(1);
                if (name == null || n == null || name.isEmpty()) continue;
                if (!seen.add(name + "|" + n.replaceAll("[^0-9+]", ""))) continue;
                Row r = new Row();
                r.base = name;
                r.title = (Favs.has(this, n) ? "⭐ " : "") + name;
                r.sub = n;
                r.number = n;
                r.photo = c.getString(2);
                r.avatarName = name;
                rows.add(r);
            }
        } catch (Exception e) { }
        return rows;
    }

    List<Row> contactRows(String q) {
        List<Row> out = new ArrayList<>();
        String last = "";
        for (Row r : allContacts()) {
            if (favOnly && !Favs.has(this, r.number)) continue;
            if (!q.isEmpty() && !matches(r, q)) continue;
            String letter = r.base.substring(0, 1).toUpperCase();
            if (!Character.isLetter(letter.charAt(0))) letter = "#";
            if (!letter.equals(last)) {
                Row h = new Row();
                h.header = true;
                h.title = letter;
                out.add(h);
                last = letter;
            }
            out.add(r);
        }
        return out;
    }

    void contactsTab() {
        if (!has(Manifest.permission.READ_CONTACTS)) {
            message("👥", "Contacts unavailable",
                    "Allow the Contacts permission to browse and search your contacts.",
                    "Open settings", this::openSettings);
            return;
        }
        LinearLayout col = screen("Contacts", "＋", this::newContact);
        EditText search = searchBox("Search contacts");
        LinearLayout.LayoutParams sl = new LinearLayout.LayoutParams(MATCH, WRAP);
        sl.setMargins(dp(20), dp(4), dp(20), dp(4));
        col.addView(search, sl);
        col.addView(chipBar(new String[]{"All", "Favorites"}, favOnly ? 1 : 0,
                i -> { favOnly = i == 1; show(CONTACTS); }), new LinearLayout.LayoutParams(MATCH, WRAP));

        FrameLayout holder = new FrameLayout(this);
        ListView lv = list(contactRows(""), false);
        lv.setFastScrollEnabled(true);
        LinearLayout empty = emptyView("👥", favOnly ? "No favorites yet" : "No contacts found");
        holder.addView(empty, new FrameLayout.LayoutParams(MATCH, MATCH));
        holder.addView(lv, new FrameLayout.LayoutParams(MATCH, MATCH));
        lv.setEmptyView(empty);
        col.addView(holder, new LinearLayout.LayoutParams(MATCH, 0, 1));

        search.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
            public void onTextChanged(CharSequence s, int a, int b, int c) { }
            public void afterTextChanged(Editable e) {
                lv.setAdapter(new RowAdapter(contactRows(e.toString().trim())));
            }
        });
    }

    // ================= voicemail =================

    void voicemail() {
        message("📨", "Voicemail", "Call your voicemail to listen to your messages.",
                "Call voicemail", () -> {
                    if (!has(Manifest.permission.CALL_PHONE)) { SpeedDial.pendingKey = "1"; requestPermissions(PERMS, 2); return; }
                    try {
                        startActivity(new Intent(Intent.ACTION_CALL, Uri.fromParts("voicemail", "", null)));
                    } catch (Throwable e) { toast("Voicemail is not available on this SIM"); }
                });
    }

    // ================= more =================

    void callVoicemail() {
        if (!has(Manifest.permission.CALL_PHONE)) { SpeedDial.pendingKey = "1"; requestPermissions(PERMS, 2); return; }
        try {
            startActivity(new Intent(Intent.ACTION_CALL, Uri.fromParts("voicemail", "", null)));
        } catch (Throwable e) { toast("Voicemail is not available on this SIM"); }
    }

    void moreItem(LinearLayout list, String emoji, String title, String sub, Runnable r) {
        LinearLayout card = new LinearLayout(this);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(14), dp(12), dp(14), dp(12));
        card.setBackground(Ui.ripple(this, Ui.SURFACE, false, 18));
        TextView ic = Ui.text(this, emoji, 18, Ui.TEXT, false);
        ic.setGravity(Gravity.CENTER);
        ic.setBackground(Ui.shape(this, Ui.mix(Ui.SURFACE, Ui.ACCENT, 0.22f), true, 0));
        card.addView(ic, new LinearLayout.LayoutParams(dp(42), dp(42)));
        LinearLayout t = new LinearLayout(this);
        t.setOrientation(LinearLayout.VERTICAL);
        t.addView(Ui.text(this, title, 16, Ui.TEXT, true));
        t.addView(Ui.text(this, sub, 12.5f, Ui.MUTED, false));
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(0, WRAP, 1);
        tp.leftMargin = dp(14);
        card.addView(t, tp);
        card.addView(Ui.text(this, "›", 22, Ui.MUTED, false));
        card.setOnClickListener(v -> r.run());
        Ui.press(card);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(MATCH, WRAP);
        lp.bottomMargin = dp(8);
        list.addView(card, lp);
    }

    void moreTab() {
        LinearLayout col = screen("More", null, null);
        ScrollView sv = new ScrollView(this);
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(dp(20), dp(8), dp(20), dp(16));
        sv.addView(list);
        col.addView(sv, new LinearLayout.LayoutParams(MATCH, 0, 1));

        LinearLayout hero = new LinearLayout(this);
        hero.setGravity(Gravity.CENTER_VERTICAL);
        hero.setPadding(dp(16), dp(16), dp(16), dp(16));
        android.graphics.drawable.GradientDrawable hg = new android.graphics.drawable.GradientDrawable(
                android.graphics.drawable.GradientDrawable.Orientation.TL_BR,
                new int[]{Ui.mix(Ui.SURFACE, Ui.ACCENT, 0.7f), Ui.mix(Ui.SURFACE, Ui.ACCENT, 0.25f)});
        hg.setCornerRadius(dp(22));
        hero.setBackground(hg);
        ImageView heroLogo = new ImageView(this);
        try { heroLogo.setImageDrawable(getPackageManager().getApplicationIcon(getPackageName())); } catch (Throwable e) { }
        hero.addView(heroLogo, new LinearLayout.LayoutParams(dp(54), dp(54)));
        LinearLayout ht = new LinearLayout(this);
        ht.setOrientation(LinearLayout.VERTICAL);
        ht.addView(Ui.text(this, "Dialer", 20, Color.WHITE, true));
        ht.addView(Ui.text(this, "Made in India  ·  by Satish Kushwaha", 12.5f, Color.parseColor("#E6FFFFFF"), false));
        LinearLayout.LayoutParams htp = new LinearLayout.LayoutParams(0, WRAP, 1);
        htp.leftMargin = dp(14);
        hero.addView(ht, htp);
        hero.addView(Ui.text(this, "›", 24, Color.WHITE, false));
        hero.setOnClickListener(v -> startActivity(new Intent(this, AboutActivity.class)));
        LinearLayout.LayoutParams hlp = new LinearLayout.LayoutParams(MATCH, WRAP);
        hlp.bottomMargin = dp(14);
        list.addView(hero, hlp);
        moreItem(list, "📨", "Voicemail", "Call your voicemail", this::callVoicemail);
        moreItem(list, "⭐", "Favorites", "Your starred contacts",
                () -> startActivity(new Intent(this, FavoritesActivity.class)));
        moreItem(list, "🛡️", "Spam protection", "Block spam and unwanted calls",
                () -> startActivity(new Intent(this, SpamActivity.class)));
        moreItem(list, "🚫", "Blocked numbers", "View and unblock numbers",
                () -> startActivity(new Intent(this, BlockedNumbersActivity.class)));
        moreItem(list, "⚙️", "Settings", "Theme, accent colour, feedback",
                () -> startActivity(new Intent(this, SettingsActivity.class)));
        moreItem(list, "📞", "Default phone app", "Choose which app handles calls",
                () -> startActivity(new Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)));
        moreItem(list, "🔑", "App permissions", "Phone, Call logs, Contacts, Notifications",
                this::openSettings);
        String ver = "";
        try { ver = getPackageManager().getPackageInfo(getPackageName(), 0).versionName; }
        catch (Exception e) { }
        final String v2 = ver;
        moreItem(list, "ℹ️", "About", "Version " + ver, () -> startActivity(new Intent(this, AboutActivity.class)));
    }
}
