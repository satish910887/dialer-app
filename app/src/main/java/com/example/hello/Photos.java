package com.example.hello;

import android.content.ContentResolver;
import android.content.Context;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.*;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.provider.ContactsContract;
import android.util.LruCache;
import android.widget.TextView;
import java.io.InputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Photos {
    static final LruCache<String, Bitmap> CACHE = new LruCache<>(80);
    static final Bitmap NONE = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888);
    static final ExecutorService POOL = Executors.newFixedThreadPool(3);

    static class Circle extends Drawable {
        final Bitmap b;
        final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        final BitmapShader sh;

        Circle(Bitmap b) {
            this.b = b;
            sh = new BitmapShader(b, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
            p.setShader(sh);
        }

        @Override public void draw(Canvas c) {
            Rect r = getBounds();
            if (r.isEmpty()) return;
            float s = Math.max((float) r.width() / b.getWidth(), (float) r.height() / b.getHeight());
            Matrix m = new Matrix();
            m.setScale(s, s);
            m.postTranslate(r.left + (r.width() - b.getWidth() * s) / 2f,
                    r.top + (r.height() - b.getHeight() * s) / 2f);
            sh.setLocalMatrix(m);
            c.drawCircle(r.exactCenterX(), r.exactCenterY(),
                    Math.min(r.width(), r.height()) / 2f, p);
        }

        @Override public void setAlpha(int a) { p.setAlpha(a); }
        @Override public void setColorFilter(ColorFilter f) { p.setColorFilter(f); }
        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    }

    static void apply(TextView v, Bitmap b) {
        v.setText("");
        v.setBackground(new Circle(b));
    }

    public static void bind(Context c, TextView v, String number, String photoUri, boolean full) {
        String id = (photoUri != null && !photoUri.isEmpty()) ? photoUri : number;
        if (id == null || id.length() < 3) { v.setTag(null); return; }
        final String key = (full ? "f|" : "t|") + id;
        v.setTag(key);
        Bitmap hit = CACHE.get(key);
        if (hit != null) {
            if (hit != NONE) apply(v, hit);
            return;
        }
        final Context app = c.getApplicationContext();
        POOL.execute(() -> {
            Bitmap b = load(app, number, photoUri, full);
            CACHE.put(key, b == null ? NONE : b);
            if (b != null) v.post(() -> { if (key.equals(v.getTag())) apply(v, b); });
        });
    }

    static Bitmap load(Context c, String number, String photoUri, boolean full) {
        ContentResolver cr = c.getContentResolver();
        String u = photoUri;
        if (u == null || u.isEmpty()) {
            if (number == null || c.checkSelfPermission(android.Manifest.permission.READ_CONTACTS)
                    != PackageManager.PERMISSION_GRANTED) return null;
            String col = full ? ContactsContract.PhoneLookup.PHOTO_URI
                    : ContactsContract.PhoneLookup.PHOTO_THUMBNAIL_URI;
            try (Cursor cur = cr.query(Uri.withAppendedPath(
                    ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(number)),
                    new String[]{col}, null, null, null)) {
                if (cur != null && cur.moveToFirst()) u = cur.getString(0);
            } catch (Exception e) { return null; }
        }
        if (u == null || u.isEmpty()) return null;
        try {
            Uri uri = Uri.parse(u);
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            try (InputStream is = cr.openInputStream(uri)) { BitmapFactory.decodeStream(is, null, o); }
            int target = full ? 512 : 160;
            int s = 1;
            while (o.outWidth / (s * 2) >= target && o.outHeight / (s * 2) >= target) s *= 2;
            o.inJustDecodeBounds = false;
            o.inSampleSize = s;
            try (InputStream is = cr.openInputStream(uri)) { return BitmapFactory.decodeStream(is, null, o); }
        } catch (Throwable e) { return null; }
    }
}
