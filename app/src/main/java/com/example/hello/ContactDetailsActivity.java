package com.example.hello;

import android.Manifest;
import android.app.Activity;
import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.BlockedNumberContract;
import android.provider.CallLog;
import android.provider.ContactsContract;
import android.text.format.DateUtils;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

public class ContactDetailsActivity extends Activity {
    String number, name, lookupKey;
    boolean editing = false, saved = false;
    TextView ringTv;

    int dp(float v) { return Ui.dp(this, v); }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        Ui.applyTheme(this);
        Ui.bars(this);
        number = getIntent().getStringExtra("number");
        name = getIntent().getStringExtra("name");
        if (number == null) { finish(); return; }
        String looked = Ui.lookupName(this, number);
        saved = !looked.isEmpty();
        if (saved) name = looked;
        if (name == null || name.isEmpty()) name = number;

        ScrollView sv = new ScrollView(this);
        sv.setBackground(Ui.bg(this));
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setPadding(dp(20), dp(16), dp(20), dp(24));
        sv.addView(root);

        TextView back = Ui.text(this, "‹  Back", 16, Ui.MUTED, true);
        back.setPadding(dp(4), dp(8), dp(12), dp(8));
        back.setOnClickListener(v -> finish());
        LinearLayout.LayoutParams bl = new LinearLayout.LayoutParams(-2, -2);
        bl.gravity = Gravity.START;
        root.addView(back, bl);

        TextView av = Ui.text(this, "", 40, android.graphics.Color.WHITE, true);
        Ui.bindAvatar(this, av, saved ? name : null);
        av.setTextSize(40);
        Photos.bind(this, av, number, null, true);
        av.setOnClickListener(v -> photoMenu());
        LinearLayout.LayoutParams ap = new LinearLayout.LayoutParams(dp(110), dp(110));
        ap.topMargin = dp(8);
        root.addView(av, ap);

        TextView nm = Ui.text(this, name, 24, Ui.TEXT, true);
        nm.setGravity(Gravity.CENTER);
        nm.setPadding(0, dp(14), 0, 0);
        root.addView(nm, new LinearLayout.LayoutParams(-1, -2));
        TextView nu = Ui.text(this, number, 14, Ui.MUTED, false);
        nu.setGravity(Gravity.CENTER);
        nu.setPadding(0, dp(4), 0, dp(18));
        root.addView(nu, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout actions = new LinearLayout(this);
        actions.setGravity(Gravity.CENTER);
        actions.addView(action("📞", "Call", Ui.GREEN, v -> Caller.dial(this, number)), new LinearLayout.LayoutParams(0, -2, 1));
        actions.addView(action("💬", "Message", Ui.SURFACE2, v -> {
            try { startActivity(new Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:" + Uri.encode(number)))); }
            catch (Exception e) { toast("No messaging app found"); }
        }), new LinearLayout.LayoutParams(0, -2, 1));
        final android.widget.ImageView[] favIcon = new android.widget.ImageView[1];
        LinearLayout fav = action(Favs.has(this, number) ? "⭐" : "☆", "Favorite", Ui.SURFACE2, v -> {
            boolean on = Favs.toggle(this, number, name);
            favIcon[0].setImageResource(on ? R.drawable.ic_star : R.drawable.ic_star_border);
            toast(on ? "Added to favorites" : "Removed from favorites");
        });
        favIcon[0] = (android.widget.ImageView) fav.getChildAt(0);
        actions.addView(fav, new LinearLayout.LayoutParams(0, -2, 1));
        actions.addView(action("🚫", "Block", Ui.SURFACE2, v -> block()), new LinearLayout.LayoutParams(0, -2, 1));
        actions.addView(action("✏️", "Edit", Ui.SURFACE2, v -> editContact()), new LinearLayout.LayoutParams(0, -2, 1));
        root.addView(actions, new LinearLayout.LayoutParams(-1, -2));

        section(root, "CONTACT INFO");
        LinearLayout info = card(root);
        info.addView(Ui.text(this, "Mobile", 12, Ui.MUTED, false));
        info.addView(Ui.text(this, number, 16, Ui.TEXT, true));
        TextView chg = Ui.text(this, "📷  Change photo", 14, Ui.ACCENT, true);
        chg.setPadding(0, dp(12), 0, 0);
        chg.setOnClickListener(v -> photoMenu());
        info.addView(chg);
        ringTv = Ui.text(this, "", 14, Ui.ACCENT, true);
        ringTv.setPadding(0, dp(12), 0, 0);
        ringTv.setOnClickListener(v -> ringMenu());
        info.addView(ringTv);
        refreshRing();
        if (!saved) {
            TextView add = Ui.text(this, "＋  Add to contacts", 14, Ui.ACCENT, true);
            add.setPadding(0, dp(12), 0, 0);
            add.setOnClickListener(v -> {
                Intent in = new Intent(Intent.ACTION_INSERT, ContactsContract.Contacts.CONTENT_URI);
                in.putExtra(ContactsContract.Intents.Insert.PHONE, number);
                startActivity(in);
            });
            info.addView(add);
        }

        section(root, "NOTES");
        LinearLayout nc = card(root);
        String note = Notes.get(this, number);
        TextView nt = Ui.text(this, note.isEmpty() ? "📝  Tap to add a note" : note, 14,
                note.isEmpty() ? Ui.ACCENT : Ui.TEXT, note.isEmpty());
        nc.addView(nt);
        nc.setOnClickListener(v -> Notes.edit(this, number, name, this::recreate));

        section(root, "CALL HISTORY");
        LinearLayout hist = card(root);
        loadHistory(hist);
        setContentView(sv);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (editing) { editing = false; Photos.CACHE.evictAll(); recreate(); }
    }

    @Override
    public void onRequestPermissionsResult(int c, String[] p, int[] r) {
        boolean ok = r.length > 0 && r[0] == PackageManager.PERMISSION_GRANTED;
        if (c == 77 && ok && Caller.pending != null) Caller.dial(this, Caller.pending);
        if (c == 91) {
            if (ok) pickPhoto(); else toast("Allow Contacts permission to change photos");
        }
    }

    // returns {contactId, rawContactId} or {-1,-1}
    long[] ids() {
        long cid = -1, rid = -1;
        try (Cursor c = getContentResolver().query(
                Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(number)),
                new String[]{ContactsContract.PhoneLookup._ID, ContactsContract.PhoneLookup.LOOKUP_KEY},
                null, null, null)) {
            if (c != null && c.moveToFirst()) { cid = c.getLong(0); lookupKey = c.getString(1); }
        } catch (Exception e) { }
        if (cid >= 0) {
            try (Cursor c = getContentResolver().query(ContactsContract.RawContacts.CONTENT_URI,
                    new String[]{ContactsContract.RawContacts._ID},
                    ContactsContract.RawContacts.CONTACT_ID + "=? AND " + ContactsContract.RawContacts.DELETED + "=0",
                    new String[]{String.valueOf(cid)}, null)) {
                if (c != null && c.moveToFirst()) rid = c.getLong(0);
            } catch (Exception e) { }
        }
        return new long[]{cid, rid};
    }

    void editContact() {
        long[] id = ids();
        if (id[0] < 0) {
            toast("Save this number as a contact first");
            Intent in = new Intent(Intent.ACTION_INSERT, ContactsContract.Contacts.CONTENT_URI);
            in.putExtra(ContactsContract.Intents.Insert.PHONE, number);
            editing = true;
            startActivity(in);
            return;
        }
        try {
            Intent in = new Intent(Intent.ACTION_EDIT);
            in.setDataAndType(ContactsContract.Contacts.getLookupUri(id[0], lookupKey),
                    ContactsContract.Contacts.CONTENT_ITEM_TYPE);
            in.putExtra("finishActivityOnSaveCompleted", true);
            editing = true;
            startActivity(in);
        } catch (Exception e) {
            editing = false;
            toast("No contacts app found to edit this contact");
        }
    }

    void photoMenu() {
        if (!saved) { editContact(); return; }
        new android.app.AlertDialog.Builder(this).setTitle("Contact photo")
                .setItems(new String[]{"Choose from gallery", "Remove photo", "Edit contact"}, (d, i) -> {
                    if (i == 0) pickPhoto();
                    else if (i == 1) removePhoto();
                    else editContact();
                }).show();
    }

    void pickPhoto() {
        if (checkSelfPermission(Manifest.permission.WRITE_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.WRITE_CONTACTS}, 91);
            return;
        }
        Intent in = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        in.addCategory(Intent.CATEGORY_OPENABLE);
        in.setType("image/*");
        startActivityForResult(in, 92);
    }

    void removePhoto() {
        if (checkSelfPermission(Manifest.permission.WRITE_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.WRITE_CONTACTS}, 91);
            return;
        }
        long[] id = ids();
        if (id[1] < 0) { toast("Contact not found"); return; }
        try {
            getContentResolver().delete(ContactsContract.Data.CONTENT_URI,
                    ContactsContract.Data.RAW_CONTACT_ID + "=? AND " + ContactsContract.Data.MIMETYPE + "=?",
                    new String[]{String.valueOf(id[1]),
                            ContactsContract.CommonDataKinds.Photo.CONTENT_ITEM_TYPE});
            Photos.CACHE.evictAll();
            toast("Photo removed");
            recreate();
        } catch (Exception e) {
            toast("Couldn't remove the photo");
        }
    }

    @Override
    protected void onActivityResult(int req, int res, Intent d) {
        super.onActivityResult(req, res, d);
        if (req == 93) {
            if (res == RESULT_OK && d != null) { Ring.setContact(this, number, Ring.fromResult(d)); refreshRing(); }
            return;
        }
        if (req != 92 || res != RESULT_OK || d == null || d.getData() == null) return;
        final Uri u = d.getData();
        toast("Saving photo…");
        new Thread(() -> {
            final String err = savePhoto(u);
            runOnUiThread(() -> {
                if (err == null) { Photos.CACHE.evictAll(); toast("Photo updated"); recreate(); }
                else toast("Couldn't save photo: " + err);
            });
        }).start();
    }

    String savePhoto(Uri u) {
        try {
            long[] id = ids();
            if (id[1] < 0) return "contact not found";
            android.graphics.BitmapFactory.Options o = new android.graphics.BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            try (java.io.InputStream is = getContentResolver().openInputStream(u)) {
                android.graphics.BitmapFactory.decodeStream(is, null, o);
            }
            if (o.outWidth <= 0) return "unreadable image";
            int sm = 1;
            while (Math.min(o.outWidth, o.outHeight) / (sm * 2) >= 720) sm *= 2;
            o.inJustDecodeBounds = false;
            o.inSampleSize = sm;
            android.graphics.Bitmap b;
            try (java.io.InputStream is = getContentResolver().openInputStream(u)) {
                b = android.graphics.BitmapFactory.decodeStream(is, null, o);
            }
            if (b == null) return "unreadable image";
            int rot = 0;
            try (java.io.InputStream is = getContentResolver().openInputStream(u)) {
                int ori = new android.media.ExifInterface(is).getAttributeInt(
                        android.media.ExifInterface.TAG_ORIENTATION, 1);
                rot = ori == 6 ? 90 : ori == 3 ? 180 : ori == 8 ? 270 : 0;
            } catch (Throwable e) { }
            int side = Math.min(b.getWidth(), b.getHeight());
            android.graphics.Matrix m = new android.graphics.Matrix();
            if (rot != 0) m.postRotate(rot);
            android.graphics.Bitmap sq = android.graphics.Bitmap.createBitmap(
                    b, (b.getWidth() - side) / 2, (b.getHeight() - side) / 2, side, side, m, true);
            if (sq.getWidth() > 720) sq = android.graphics.Bitmap.createScaledBitmap(sq, 720, 720, true);
            java.io.ByteArrayOutputStream bo = new java.io.ByteArrayOutputStream();
            sq.compress(android.graphics.Bitmap.CompressFormat.JPEG, 92, bo);
            Uri raw = android.content.ContentUris.withAppendedId(ContactsContract.RawContacts.CONTENT_URI, id[1]);
            Uri dp = Uri.withAppendedPath(raw, ContactsContract.RawContacts.DisplayPhoto.CONTENT_DIRECTORY);
            try (android.content.res.AssetFileDescriptor fd = getContentResolver().openAssetFileDescriptor(dp, "w");
                 java.io.OutputStream os = fd.createOutputStream()) {
                os.write(bo.toByteArray());
            }
            return null;
        } catch (Throwable e) {
            return "unexpected error";
        }
    }

    void refreshRing() {
        if (ringTv != null) ringTv.setText("🔔  Ringtone: " + Ring.title(this, Ring.contact(this, number)));
    }

    void ringMenu() {
        new android.app.AlertDialog.Builder(this).setTitle("Ringtone for " + name)
                .setItems(new String[]{"Choose ringtone", "Use default ringtone"}, (dg, i) -> {
                    if (i == 0) startActivityForResult(Ring.pickerIntent(Ring.contact(this, number)), 93);
                    else { Ring.setContact(this, number, ""); refreshRing(); }
                }).show();
    }

    void toast(String m) { Toast.makeText(this, m, Toast.LENGTH_SHORT).show(); }

    int iconFor(String e) {
        switch (e) {
            case "📞": return R.drawable.ic_call;
            case "💬": return R.drawable.ic_chat;
            case "⭐": return R.drawable.ic_star;
            case "☆": return R.drawable.ic_star_border;
            case "🚫": return R.drawable.ic_block;
            default: return R.drawable.ic_edit;
        }
    }

    LinearLayout action(String emoji, String label, int color, android.view.View.OnClickListener l) {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setGravity(Gravity.CENTER_HORIZONTAL);
        android.widget.ImageView ic = new android.widget.ImageView(this);
        ic.setImageResource(iconFor(emoji));
        ic.setColorFilter(android.graphics.Color.WHITE);
        ic.setPadding(dp(16), dp(16), dp(16), dp(16));
        Ui.press(ic);
        ic.setBackground(Ui.ovalStroke(this, color, Ui.SURFACE2));
        ic.setOnClickListener(l);
        c.addView(ic, new LinearLayout.LayoutParams(dp(56), dp(56)));
        TextView t = Ui.text(this, label, 12, Ui.MUTED, false);
        t.setGravity(Gravity.CENTER);
        t.setPadding(0, dp(6), 0, 0);
        c.addView(t);
        return c;
    }

    void section(LinearLayout root, String t) {
        TextView s = Ui.text(this, t, 12, Ui.ACCENT, true);
        s.setPadding(dp(4), dp(22), 0, dp(8));
        root.addView(s, new LinearLayout.LayoutParams(-1, -2));
    }

    LinearLayout card(LinearLayout root) {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setPadding(dp(16), dp(14), dp(16), dp(14));
        c.setBackground(Ui.card(this, Ui.SURFACE, 18, Ui.SURFACE2));
        root.addView(c, new LinearLayout.LayoutParams(-1, -2));
        return c;
    }

    void block() {
        try {
            ContentValues cv = new ContentValues();
            cv.put(BlockedNumberContract.BlockedNumbers.COLUMN_ORIGINAL_NUMBER, number);
            getContentResolver().insert(BlockedNumberContract.BlockedNumbers.CONTENT_URI, cv);
            toast("Blocked " + number);
        } catch (Exception e) {
            toast("Can't block: set Dialer as the default phone app");
        }
    }

    void loadHistory(LinearLayout hist) {
        String digits = number.replaceAll("[^0-9]", "");
        int count = 0;
        if (!digits.isEmpty() && checkSelfPermission(Manifest.permission.READ_CALL_LOG)
                == PackageManager.PERMISSION_GRANTED) {
            try (Cursor c = getContentResolver().query(CallLog.Calls.CONTENT_URI,
                    new String[]{CallLog.Calls.TYPE, CallLog.Calls.DATE, CallLog.Calls.DURATION},
                    Favs.logSel(number), Favs.logArgs(number),
                    CallLog.Calls.DATE + " DESC")) {
                while (c != null && c.moveToNext() && count < 30) {
                    int type = c.getInt(0);
                    long date = c.getLong(1);
                    long secs = c.getLong(2);
                    String label = type == CallLog.Calls.OUTGOING_TYPE ? "↗ Outgoing"
                            : type == CallLog.Calls.MISSED_TYPE ? "✖ Missed"
                            : type == CallLog.Calls.REJECTED_TYPE ? "⊘ Declined"
                            : type == CallLog.Calls.BLOCKED_TYPE ? "⊘ Blocked" : "↙ Incoming";
                    String dur = secs > 0 ? "  ·  " + (secs < 60 ? secs + "s" : (secs / 60) + "m " + (secs % 60) + "s") : "";
                    TextView t = Ui.text(this, label + "\n" + DateUtils.formatDateTime(this, date,
                            DateUtils.FORMAT_SHOW_DATE | DateUtils.FORMAT_ABBREV_MONTH | DateUtils.FORMAT_SHOW_TIME) + dur,
                            14, type == CallLog.Calls.MISSED_TYPE ? Ui.RED : Ui.TEXT, false);
                    t.setPadding(0, dp(6), 0, dp(6));
                    hist.addView(t);
                    count++;
                }
            } catch (Exception e) { }
        }
        if (count == 0) hist.addView(Ui.text(this, "No calls with this number", 14, Ui.MUTED, false));
    }
}
