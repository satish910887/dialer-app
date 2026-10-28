package com.example.hello;

import android.app.Activity;
import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.net.Uri;
import android.provider.ContactsContract;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

public class Ui {
    public static int BG = Color.parseColor("#0A0F1E");
    public static int SURFACE = Color.parseColor("#131A2E");
    public static int SURFACE2 = Color.parseColor("#1F2845");
    public static int TEXT = Color.parseColor("#F1F4FF");
    public static int MUTED = Color.parseColor("#8D97B5");
    public static int GREEN = Color.parseColor("#19C37D");
    public static int RED = Color.parseColor("#EF4444");
    public static int ACCENT = Color.parseColor("#5B6CFF");
    public static float SCALE = 1f;
    public static boolean BOLD = false;

    public static final String[] THEME_NAMES = {
        "System default", "Dark", "Light", "Midnight AMOLED", "Ocean Blue", "Emerald", "Sunset"
    };
    public static final String[] ACCENT_NAMES = {"Indigo", "Purple", "Green", "Orange", "Pink", "Cyan"};
    public static final String[] FONT_NAMES = {"Small", "Medium", "Large"};
    static final int[] ACCENTS = {
        Color.parseColor("#5B6CFF"), Color.parseColor("#8B5CF6"), Color.parseColor("#22C55E"),
        Color.parseColor("#F59E0B"), Color.parseColor("#EC4899"), Color.parseColor("#06B6D4")};

    static final String[][] PAL = {
        {"#0A0F1E", "#131A2E", "#1F2845", "#F1F4FF", "#8D97B5", "#19C37D", "#EF4444"},
        {"#F4F6FB", "#FFFFFF", "#E7EBF3", "#192234", "#68758A", "#16A34A", "#DC2626"},
        {"#000000", "#0D0F14", "#1C2028", "#F4F6FA", "#9AA3B2", "#2ECC71", "#E5484D"},
        {"#081521", "#102638", "#1D3B50", "#F0F8FF", "#9BB9CA", "#36D6A0", "#FF667A"},
        {"#0B1713", "#14251D", "#263D31", "#F0FFF5", "#A0B9AA", "#55E6A5", "#FF737A"},
        {"#1B1214", "#2A1B20", "#453039", "#FFF5F0", "#C6A9A5", "#62D6A5", "#FF6B68"}
    };

    public static void applyTheme(Context c) {
        android.content.SharedPreferences sp = c.getSharedPreferences("app", Context.MODE_PRIVATE);
        int id = sp.getInt("theme", 0);
        int acc = sp.getInt("accent", 0);
        int font = sp.getInt("font", 1);
        id = Math.max(0, Math.min(id, THEME_NAMES.length - 1));
        if (id == 0) {
            boolean night = (c.getResources().getConfiguration().uiMode
                    & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
            id = night ? 1 : 2;
        }
        String[] p = PAL[id - 1];
        BG = Color.parseColor(p[0]);
        SURFACE = Color.parseColor(p[1]);
        SURFACE2 = Color.parseColor(p[2]);
        TEXT = Color.parseColor(p[3]);
        MUTED = Color.parseColor(p[4]);
        GREEN = Color.parseColor(p[5]);
        RED = Color.parseColor(p[6]);
        ACCENT = ACCENTS[Math.max(0, Math.min(acc, ACCENTS.length - 1))];
        SCALE = font == 0 ? 0.9f : font == 2 ? 1.12f : 1f;
        BOLD = sp.getBoolean("a11y_bold", false);
        if (sp.getBoolean("a11y_contrast", false)) {
            boolean light = isLight();
            TEXT = light ? Color.BLACK : Color.WHITE;
            MUTED = Color.parseColor(light ? "#2B3345" : "#DDE2F0");
        }
    }

    public static boolean isLight() {
        return (Color.red(BG) * 299 + Color.green(BG) * 587 + Color.blue(BG) * 114) / 1000 > 150;
    }

    public static void bars(Activity a) {
        a.getWindow().setStatusBarColor(BG);
        a.getWindow().setNavigationBarColor(SURFACE);
        View d = a.getWindow().getDecorView();
        int v = d.getSystemUiVisibility();
        d.setSystemUiVisibility(isLight() ? (v | 0x2000) : (v & ~0x2000));
    }

    static int mix(int a, int b, float t) {
        return Color.rgb((int) (Color.red(a) * (1 - t) + Color.red(b) * t),
                (int) (Color.green(a) * (1 - t) + Color.green(b) * t),
                (int) (Color.blue(a) * (1 - t) + Color.blue(b) * t));
    }

    public static Drawable bg(Context c) { return bg(c, "other"); }

    public static Drawable bg(Context c, String key) {
        Drawable w = Wallpaper.drawable(c, key);
        if (w != null) return w;
        return new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{mix(BG, ACCENT, 0.12f), BG, BG});
    }

    static final int[] PALETTE = {
        Color.parseColor("#5B8DEF"), Color.parseColor("#E5736B"), Color.parseColor("#43B581"),
        Color.parseColor("#B07CE8"), Color.parseColor("#F0A04B"), Color.parseColor("#3FB8C9"),
        Color.parseColor("#D96AA7")};

    public static int dp(Context c, float v) {
        return (int) (v * c.getResources().getDisplayMetrics().density + 0.5f);
    }

    public static Drawable shape(Context c, int color, boolean oval, float radiusDp) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        if (oval) g.setShape(GradientDrawable.OVAL);
        else g.setCornerRadius(dp(c, radiusDp));
        return g;
    }

    public static Drawable ripple(Context c, int color, boolean oval, float radiusDp) {
        return new RippleDrawable(ColorStateList.valueOf(Color.parseColor("#33FFFFFF")),
                shape(c, color, oval, radiusDp), shape(c, Color.WHITE, oval, radiusDp));
    }

    public static Drawable glow(Context c, int color) {
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{mix(color, Color.WHITE, 0.25f), color});
        g.setShape(GradientDrawable.OVAL);
        return new RippleDrawable(ColorStateList.valueOf(Color.parseColor("#33FFFFFF")),
                g, shape(c, Color.WHITE, true, 0));
    }

    public static void press(final View v) {
        if (!v.getContext().getSharedPreferences("app", Context.MODE_PRIVATE).getBoolean("animations", true)) return;
        v.setOnTouchListener((x, e) -> {
            int a = e.getActionMasked();
            if (a == android.view.MotionEvent.ACTION_DOWN) {
                x.animate().scaleX(0.92f).scaleY(0.92f).setDuration(80).start();
            } else if (a == android.view.MotionEvent.ACTION_UP || a == android.view.MotionEvent.ACTION_CANCEL) {
                x.animate().scaleX(1f).scaleY(1f).setDuration(240)
                        .setInterpolator(new android.view.animation.OvershootInterpolator(3f)).start();
            }
            return false;
        });
    }

    public static Drawable card(Context c, int fill, float radiusDp, int stroke) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(fill);
        g.setCornerRadius(dp(c, radiusDp));
        g.setStroke(dp(c, 1), stroke);
        return g;
    }

    public static Drawable ovalStroke(Context c, int fill, int stroke) {
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.OVAL);
        g.setColor(fill);
        g.setStroke(dp(c, 1), stroke);
        return new RippleDrawable(ColorStateList.valueOf(Color.parseColor("#33FFFFFF")),
                g, shape(c, Color.WHITE, true, 0));
    }

    public static TextView text(Context c, String t, float sp, int color, boolean bold) {
        TextView v = new TextView(c);
        v.setText(t);
        v.setTextSize(sp * SCALE);
        v.setTextColor(color);
        if (bold || BOLD) v.setTypeface(Typeface.DEFAULT_BOLD);
        return v;
    }

    public static void bindAvatar(Context c, TextView v, String name) {
        String s = name == null ? "" : name.trim();
        boolean letter = !s.isEmpty() && Character.isLetter(s.codePointAt(0));
        if (letter) {
            v.setText(s.substring(0, s.offsetByCodePoints(0, 1)).toUpperCase());
            v.setTextSize(18);
        } else {
            v.setText("👤");
            v.setTextSize(16);
        }
        int color = letter ? PALETTE[(s.toLowerCase().hashCode() & 0x7fffffff) % PALETTE.length] : SURFACE2;
        v.setBackground(shape(c, color, true, 0));
        v.setGravity(Gravity.CENTER);
        v.setTextColor(Color.WHITE);
        v.setTypeface(Typeface.DEFAULT_BOLD);
    }

    public static ImageView callIcon(Context c, int sizeDp, int bg, float rotation) {
        ImageView v = new ImageView(c);
        v.setImageResource(R.drawable.ic_call);
        v.setColorFilter(Color.WHITE);
        v.setRotation(rotation == 0f ? -45f : rotation);
        int p = dp(c, sizeDp * 0.28f);
        v.setPadding(p, p, p, p);
        v.setScaleType(ImageView.ScaleType.FIT_CENTER);
        v.setBackground(glow(c, bg));
        v.setElevation(dp(c, 6));
        try { v.setOutlineSpotShadowColor(bg); v.setOutlineAmbientShadowColor(bg); } catch (Throwable e) { }
        press(v);
        return v;
    }

    public static String lookupName(Context c, String number) {
        if (number == null || number.length() < 3) return "";
        if (c.checkSelfPermission(android.Manifest.permission.READ_CONTACTS)
                != android.content.pm.PackageManager.PERMISSION_GRANTED) return "";
        try (Cursor cur = c.getContentResolver().query(
                Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(number)),
                new String[]{ContactsContract.PhoneLookup.DISPLAY_NAME}, null, null, null)) {
            if (cur != null && cur.moveToFirst()) {
                String n = cur.getString(0);
                return n == null ? "" : n;
            }
        } catch (Exception e) { }
        return "";
    }
}
