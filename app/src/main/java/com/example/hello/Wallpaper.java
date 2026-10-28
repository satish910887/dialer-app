package com.example.hello;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.media.ExifInterface;
import android.net.Uri;
import android.util.LruCache;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

public class Wallpaper {
    public static final String[] KEYS = {"home", "contacts", "keypad", "more", "incall", "other"};
    public static final String[] NAMES = {"Recents (Home)", "Contacts", "Keypad", "More",
            "Call screen", "Settings & other screens"};
    static final LruCache<String, Bitmap> CACHE = new LruCache<>(4);

    static SharedPreferences sp(Context c) { return c.getSharedPreferences("app", Context.MODE_PRIVATE); }

    static File file(Context c, String key) {
        return new File(c.getFilesDir(), key == null ? "wallpaper.jpg" : "wall_" + key + ".jpg");
    }

    public static boolean has(Context c) { return file(c, null).exists(); }

    public static int mode(Context c, String key) {
        return key == null ? 0 : sp(c).getInt("wall_mode_" + key, 0);
    }

    public static void setMode(Context c, String key, int m) {
        sp(c).edit().putInt("wall_mode_" + key, m).apply();
    }

    public static void bump(Context c) {
        sp(c).edit().putInt("wall_v", sp(c).getInt("wall_v", 0) + 1).apply();
    }

    public static void remove(Context c) {
        file(c, null).delete();
        bump(c);
    }

    public static void removeCustom(Context c, String key) {
        file(c, key).delete();
        setMode(c, key, 0);
        bump(c);
    }

    public static void resetAll(Context c) {
        for (String k : KEYS) { file(c, k).delete(); setMode(c, k, 0); }
        file(c, "voicemail").delete();
        bump(c);
    }

    public static boolean save(Context c, Uri uri) { return save(c, uri, null); }

    public static boolean save(Context c, Uri uri, String key) {
        try {
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            try (InputStream is = c.getContentResolver().openInputStream(uri)) {
                BitmapFactory.decodeStream(is, null, o);
            }
            if (o.outWidth <= 0 || o.outHeight <= 0) return false;
            int s = 1;
            while (Math.max(o.outWidth, o.outHeight) / (s * 2) >= 1600) s *= 2;
            o.inJustDecodeBounds = false;
            o.inSampleSize = s;
            Bitmap b;
            try (InputStream is = c.getContentResolver().openInputStream(uri)) {
                b = BitmapFactory.decodeStream(is, null, o);
            }
            if (b == null) return false;
            int rot = 0;
            try (InputStream is = c.getContentResolver().openInputStream(uri)) {
                int ori = new ExifInterface(is).getAttributeInt(
                        ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
                if (ori == ExifInterface.ORIENTATION_ROTATE_90) rot = 90;
                else if (ori == ExifInterface.ORIENTATION_ROTATE_180) rot = 180;
                else if (ori == ExifInterface.ORIENTATION_ROTATE_270) rot = 270;
            } catch (Throwable e) { }
            if (rot != 0) {
                Matrix m = new Matrix();
                m.postRotate(rot);
                b = Bitmap.createBitmap(b, 0, 0, b.getWidth(), b.getHeight(), m, true);
            }
            try (FileOutputStream fo = new FileOutputStream(file(c, key))) {
                b.compress(Bitmap.CompressFormat.JPEG, 90, fo);
            }
            if (key != null) setMode(c, key, 1);
            bump(c);
            return true;
        } catch (Throwable e) {
            return false;
        }
    }

    static Bitmap load(File f) {
        String k = f.getName() + "|" + f.lastModified();
        Bitmap b = CACHE.get(k);
        if (b == null) {
            try { b = BitmapFactory.decodeFile(f.getPath()); } catch (Throwable e) { b = null; }
            if (b != null) CACHE.put(k, b);
        }
        return b;
    }

    public static Drawable drawable(Context c, String key) {
        int m = mode(c, key);
        if (m == 2) return null;
        File f = m == 1 ? file(c, key) : file(c, null);
        if (m == 1 && !f.exists()) f = file(c, null);
        if (!f.exists()) return null;
        Bitmap b = load(f);
        if (b == null) return null;
        int dim = sp(c).getInt("wall_dim", 55);
        int ov = Color.argb(dim * 255 / 100, Color.red(Ui.BG), Color.green(Ui.BG), Color.blue(Ui.BG));
        return new Wall(b, ov);
    }

    static class Wall extends Drawable {
        final Bitmap b;
        final Paint p = new Paint(Paint.FILTER_BITMAP_FLAG);
        final Paint ov = new Paint();

        Wall(Bitmap b, int overlay) {
            this.b = b;
            ov.setColor(overlay);
        }

        @Override public void draw(Canvas c) {
            Rect r = getBounds();
            if (r.isEmpty()) return;
            float s = Math.max((float) r.width() / b.getWidth(), (float) r.height() / b.getHeight());
            Matrix m = new Matrix();
            m.setScale(s, s);
            m.postTranslate(r.left + (r.width() - b.getWidth() * s) / 2f,
                    r.top + (r.height() - b.getHeight() * s) / 2f);
            c.save();
            c.clipRect(r);
            c.drawBitmap(b, m, p);
            c.drawRect(r, ov);
            c.restore();
        }

        @Override public void setAlpha(int a) { }
        @Override public void setColorFilter(ColorFilter f) { }
        @Override public int getOpacity() { return PixelFormat.OPAQUE; }
    }
}
