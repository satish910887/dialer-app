package com.example.hello;

import android.Manifest;
import android.app.Activity;
import android.app.NotificationManager;
import android.app.role.RoleManager;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.OvershootInterpolator;
import android.view.animation.ScaleAnimation;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

public class AboutActivity extends Activity {
    LinearLayout root, checks;
    TextView summary;
    int bad;

    int dp(float v) { return Ui.dp(this, v); }

    boolean has(String p) { return checkSelfPermission(p) == PackageManager.PERMISSION_GRANTED; }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        Ui.applyTheme(this);
        Ui.bars(this);

        ScrollView sv = new ScrollView(this);
        sv.setBackground(Ui.bg(this));
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setPadding(dp(20), dp(18), dp(20), dp(28));
        sv.addView(root);

        TextView back = Ui.text(this, "‹  About", 24, Ui.TEXT, true);
        back.setPadding(dp(4), dp(8), dp(12), dp(8));
        back.setOnClickListener(v -> finish());
        LinearLayout.LayoutParams bl = new LinearLayout.LayoutParams(-2, -2);
        bl.gravity = Gravity.START;
        root.addView(back, bl);

        FrameLayout logoBox = new FrameLayout(this);
        View halo = new View(this);
        GradientDrawable hg = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{Ui.mix(Ui.BG, Ui.ACCENT, 0.55f), Ui.BG});
        hg.setShape(GradientDrawable.OVAL);
        halo.setBackground(hg);
        logoBox.addView(halo, new FrameLayout.LayoutParams(dp(150), dp(150), Gravity.CENTER));
        final ImageView logo = new ImageView(this);
        try { logo.setImageDrawable(getPackageManager().getApplicationIcon(getPackageName())); }
        catch (Throwable e) { logo.setImageResource(android.R.drawable.sym_action_call); }
        logoBox.addView(logo, new FrameLayout.LayoutParams(dp(96), dp(96), Gravity.CENTER));
        root.addView(logoBox, new LinearLayout.LayoutParams(dp(150), dp(150)));

        TextView title = Ui.text(this, "Dialer", 32, Ui.TEXT, true);
        title.setPadding(0, dp(8), 0, 0);
        root.addView(title);
        TextView tag = Ui.text(this, "Smarter calls. Better connections.", 14, Ui.MUTED, false);
        tag.setPadding(0, dp(4), 0, dp(12));
        root.addView(tag);

        String ver = "", code = "";
        try {
            PackageInfo pi = getPackageManager().getPackageInfo(getPackageName(), 0);
            ver = pi.versionName;
            code = String.valueOf(pi.getLongVersionCode());
        } catch (Exception e) { }
        TextView vc = Ui.text(this, "Version " + ver + "  ·  build " + code, 12.5f, Ui.ACCENT, true);
        vc.setPadding(dp(14), dp(6), dp(14), dp(6));
        vc.setBackground(Ui.card(this, Ui.SURFACE, 16, Ui.SURFACE2));
        root.addView(vc);

        // ---- Made in India card ----
        LinearLayout india = new LinearLayout(this);
        india.setOrientation(LinearLayout.VERTICAL);
        india.setGravity(Gravity.CENTER_HORIZONTAL);
        india.setPadding(dp(20), dp(22), dp(20), dp(22));
        india.setBackground(Ui.card(this, Ui.SURFACE, 24, Ui.SURFACE2));

        LinearLayout flag = new LinearLayout(this);
        flag.setBackground(Ui.card(this, Color.WHITE, 4, Ui.SURFACE2));
        flag.setClipToOutline(true);
        String[] fc = {"#FF9933", "#FFFFFF", "#138808"};
        for (String c : fc) {
            View s = new View(this);
            s.setBackgroundColor(Color.parseColor(c));
            flag.addView(s, new LinearLayout.LayoutParams(0, -1, 1));
        }
        india.addView(flag, new LinearLayout.LayoutParams(dp(110), dp(9)));

        TextView mi = Ui.text(this, "MADE IN INDIA", 22, Ui.TEXT, true);
        mi.setLetterSpacing(0.16f);
        mi.setPadding(0, dp(14), 0, 0);
        india.addView(mi);
        TextView mis = Ui.text(this, "Built in India, for everyone who loves a great dialer.", 13, Ui.MUTED, false);
        mis.setGravity(Gravity.CENTER);
        mis.setPadding(0, dp(6), 0, dp(16));
        india.addView(mis);

        View line = new View(this);
        line.setBackgroundColor(Ui.SURFACE2);
        india.addView(line, new LinearLayout.LayoutParams(dp(180), dp(1)));

        TextView mb = Ui.text(this, "MADE BY", 11, Ui.MUTED, true);
        mb.setLetterSpacing(0.25f);
        mb.setPadding(0, dp(16), 0, dp(4));
        india.addView(mb);
        TextView nm = Ui.text(this, "Satish Kushwaha", 28, Ui.ACCENT, true);
        nm.setGravity(Gravity.CENTER);
        india.addView(nm);

        TextView heart = Ui.text(this, "❤️", 24, Ui.TEXT, false);
        heart.setPadding(0, dp(12), 0, 0);
        india.addView(heart);
        ScaleAnimation pulse = new ScaleAnimation(1f, 1.3f, 1f, 1.3f,
                Animation.RELATIVE_TO_SELF, .5f, Animation.RELATIVE_TO_SELF, .5f);
        pulse.setDuration(700);
        pulse.setRepeatMode(Animation.REVERSE);
        pulse.setRepeatCount(Animation.INFINITE);
        heart.startAnimation(pulse);

        LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(-1, -2);
        ip.topMargin = dp(20);
        root.addView(india, ip);

        // ---- feature chips ----
        String[] feats = {"🛡️ Spam protection", "🎨 Themes & wallpapers", "📞 Dual SIM",
                "⭐ Favorites", "📝 Call notes", "🔢 Speed dial"};
        for (int i = 0; i < feats.length; i += 2) {
            LinearLayout row = new LinearLayout(this);
            row.setGravity(Gravity.CENTER);
            for (int j = i; j < i + 2; j++) {
                TextView c = Ui.text(this, feats[j], 12.5f, Ui.TEXT, false);
                c.setGravity(Gravity.CENTER);
                c.setPadding(dp(8), dp(10), dp(8), dp(10));
                c.setBackground(Ui.card(this, Ui.SURFACE, 16, Ui.SURFACE2));
                LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(0, -2, 1);
                cp.setMargins(dp(4), dp(4), dp(4), dp(4));
                row.addView(c, cp);
            }
            LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(-1, -2);
            if (i == 0) rp.topMargin = dp(14);
            root.addView(row, rp);
        }

        // ---- system check ----
        TextView sec = Ui.text(this, "SYSTEM CHECK", 12, Ui.ACCENT, true);
        sec.setPadding(dp(6), dp(22), 0, dp(8));
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(-1, -2);
        root.addView(sec, sp);
        summary = Ui.text(this, "", 15, Ui.TEXT, true);
        summary.setPadding(dp(6), 0, 0, dp(8));
        root.addView(summary, new LinearLayout.LayoutParams(-1, -2));
        checks = new LinearLayout(this);
        checks.setOrientation(LinearLayout.VERTICAL);
        checks.setPadding(dp(16), dp(8), dp(16), dp(8));
        checks.setBackground(Ui.card(this, Ui.SURFACE, 20, Ui.SURFACE2));
        root.addView(checks, new LinearLayout.LayoutParams(-1, -2));

        TextView foot = Ui.text(this, "Thank you for using Dialer 🙏", 13, Ui.MUTED, false);
        foot.setGravity(Gravity.CENTER);
        foot.setPadding(0, dp(22), 0, 0);
        root.addView(foot, new LinearLayout.LayoutParams(-1, -2));

        setContentView(sv);

        if (getSharedPreferences("app", MODE_PRIVATE).getBoolean("animations", true)) {
            logo.setScaleX(0.5f);
            logo.setScaleY(0.5f);
            logo.animate().scaleX(1f).scaleY(1f).setDuration(600)
                    .setInterpolator(new OvershootInterpolator(1.6f)).start();
            for (int i = 2; i < root.getChildCount(); i++) {
                View v = root.getChildAt(i);
                v.setAlpha(0f);
                v.setTranslationY(dp(26));
                v.animate().alpha(1f).translationY(0f).setStartDelay(120L + i * 60L).setDuration(380).start();
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
    }

    void refresh() {
        checks.removeAllViews();
        bad = 0;
        final Runnable app = () -> startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:" + getPackageName())));
        check("Phone calls", "Place and manage calls", has(Manifest.permission.CALL_PHONE), app);
        check("Phone state", "Detect SIM cards", has(Manifest.permission.READ_PHONE_STATE), app);
        check("Call history", "Read recent calls", has(Manifest.permission.READ_CALL_LOG), app);
        check("Edit call history", "Delete and clear calls", has(Manifest.permission.WRITE_CALL_LOG), app);
        check("Contacts", "Names, photos and search", has(Manifest.permission.READ_CONTACTS), app);
        check("Edit contacts", "Change contact photos", has(Manifest.permission.WRITE_CONTACTS), app);
        if (Build.VERSION.SDK_INT >= 33) {
            check("Notifications", "Incoming and ongoing call alerts",
                    has("android.permission.POST_NOTIFICATIONS"), app);
        }
        boolean role = false;
        try {
            RoleManager rm = (RoleManager) getSystemService(ROLE_SERVICE);
            role = rm != null && rm.isRoleHeld(RoleManager.ROLE_DIALER);
        } catch (Throwable e) { }
        check("Default phone app", role ? "Dialer handles your calls" : "Set Dialer as default to get the call screen",
                role, () -> startActivity(new Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)));
        if (Build.VERSION.SDK_INT >= 34) {
            boolean fs = true;
            try { fs = getSystemService(NotificationManager.class).canUseFullScreenIntent(); }
            catch (Throwable e) { }
            check("Full-screen incoming call", "Show the call screen over the lock screen", fs,
                    () -> startActivity(new Intent("android.settings.MANAGE_APP_USE_FULL_SCREEN_INTENT",
                            Uri.parse("package:" + getPackageName()))));
        }
        int sims = Caller.simCount(this);
        check("SIM cards", sims > 0 ? sims + " detected" : "None detected (allow Phone state)", sims > 0, app);

        summary.setText(bad == 0 ? "✅  Everything is working"
                : "⚠️  " + bad + (bad == 1 ? " item needs attention" : " items need attention"));
    }

    void check(String label, String sub, boolean ok, final Runnable fix) {
        if (!ok) bad++;
        LinearLayout r = new LinearLayout(this);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setPadding(0, dp(9), 0, dp(9));
        TextView ic = Ui.text(this, ok ? "✅" : "⚠️", 16, Ui.TEXT, false);
        r.addView(ic, new LinearLayout.LayoutParams(dp(30), -2));
        LinearLayout t = new LinearLayout(this);
        t.setOrientation(LinearLayout.VERTICAL);
        t.addView(Ui.text(this, label, 15, Ui.TEXT, true));
        t.addView(Ui.text(this, sub, 12, Ui.MUTED, false));
        r.addView(t, new LinearLayout.LayoutParams(0, -2, 1));
        if (!ok) {
            TextView f = Ui.text(this, "Fix", 13, Ui.ACCENT, true);
            f.setPadding(dp(12), dp(8), dp(4), dp(8));
            r.addView(f);
            r.setOnClickListener(v -> fix.run());
        }
        checks.addView(r, new LinearLayout.LayoutParams(-1, -2));
    }
}
