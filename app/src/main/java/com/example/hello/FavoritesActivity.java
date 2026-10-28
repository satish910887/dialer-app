package com.example.hello;

import android.Manifest;
import android.app.Activity;
import android.content.ClipData;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.DragEvent;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.List;

public class FavoritesActivity extends Activity {
    LinearLayout rows;
    ScrollView scroll;

    int dp(float n) { return Ui.dp(this, n); }

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        Ui.applyTheme(this);
        Ui.bars(this);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(18), dp(16), dp(8));
        root.setBackground(Ui.bg(this));

        TextView title = Ui.text(this, "‹  Favorites ⭐", 24, Ui.TEXT, true);
        title.setPadding(dp(4), dp(8), 0, dp(10));
        title.setOnClickListener(v -> finish());
        root.addView(title);

        TextView hint = Ui.text(this,
                "Long-press a favorite and drag it to reorder. Add favorites from a contact or recent call menu.",
                13.5f, Ui.MUTED, false);
        hint.setPadding(dp(4), 0, dp(4), dp(12));
        root.addView(hint);

        rows = new LinearLayout(this);
        rows.setOrientation(LinearLayout.VERTICAL);
        scroll = new ScrollView(this);
        scroll.addView(rows);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        setContentView(root);

        rows.setOnDragListener((v, e) -> {
            Object ls = e.getLocalState();
            if (!(ls instanceof View) || ((View) ls).getParent() != rows) return false;
            View dragged = (View) ls;
            switch (e.getAction()) {
                case DragEvent.ACTION_DRAG_STARTED:
                    return true;
                case DragEvent.ACTION_DRAG_LOCATION: {
                    float y = e.getY();
                    float vy = y - scroll.getScrollY();
                    if (vy < dp(90)) scroll.scrollBy(0, -dp(14));
                    else if (vy > scroll.getHeight() - dp(90)) scroll.scrollBy(0, dp(14));
                    for (int i = 0; i < rows.getChildCount(); i++) {
                        View ch = rows.getChildAt(i);
                        if (y >= ch.getTop() && y <= ch.getBottom()) {
                            if (ch != dragged) { rows.removeView(dragged); rows.addView(dragged, i); }
                            break;
                        }
                    }
                    return true;
                }
                case DragEvent.ACTION_DROP:
                    return true;
                case DragEvent.ACTION_DRAG_ENDED:
                    dragged.setAlpha(1f);
                    saveOrder();
                    return true;
            }
            return true;
        });
    }

    void saveOrder() {
        List<String> keys = new ArrayList<>();
        for (int i = 0; i < rows.getChildCount(); i++) {
            Object t = rows.getChildAt(i).getTag();
            if (t instanceof String) keys.add((String) t);
        }
        if (!keys.isEmpty()) Favs.setOrder(this, keys);
    }

    @Override protected void onResume() {
        super.onResume();
        Ui.applyTheme(this);
        if (rows != null) load();
    }

    @Override
    public void onRequestPermissionsResult(int c, String[] p, int[] r) {
        if (c == 77 && Caller.pending != null
                && checkSelfPermission(Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED) {
            Caller.dial(this, Caller.pending);
        }
    }

    void load() {
        rows.removeAllViews();
        List<String[]> items = Favs.list(this);
        for (String[] it : items) {
            final String key = it[0], name = it[1], num = it[2];
            LinearLayout row = new LinearLayout(this);
            row.setTag(key);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(12), dp(10), dp(8), dp(10));
            row.setBackground(Ui.ripple(this, Ui.SURFACE, false, 16));

            TextView av = Ui.text(this, "", 18, Ui.TEXT, true);
            Ui.bindAvatar(this, av, name);
            Photos.bind(this, av, num, null, false);
            row.addView(av, new LinearLayout.LayoutParams(dp(44), dp(44)));

            LinearLayout txt = new LinearLayout(this);
            txt.setOrientation(LinearLayout.VERTICAL);
            txt.addView(Ui.text(this, name, 16, Ui.TEXT, true));
            txt.addView(Ui.text(this, num, 13, Ui.MUTED, false));
            LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(0, -2, 1);
            tp.leftMargin = dp(14);
            row.addView(txt, tp);

            TextView handle = Ui.text(this, "≡", 24, Ui.MUTED, false);
            handle.setPadding(dp(8), 0, dp(8), 0);
            row.addView(handle);

            TextView rm = Ui.text(this, "Remove", 13, Ui.RED, true);
            rm.setPadding(dp(10), dp(10), dp(8), dp(10));
            rm.setOnClickListener(v -> { Favs.remove(this, key); load(); });
            row.addView(rm);

            row.setOnClickListener(v -> Caller.dial(this, num));
            row.setOnLongClickListener(v -> {
                v.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                v.setAlpha(0.4f);
                v.startDragAndDrop(ClipData.newPlainText("fav", key), new View.DragShadowBuilder(v), v, 0);
                return true;
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
            lp.bottomMargin = dp(8);
            rows.addView(row, lp);
        }

        if (items.isEmpty()) {
            TextView empty = Ui.text(this, "No favorites yet", 16, Ui.MUTED, false);
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(dp(20), dp(48), dp(20), dp(48));
            rows.addView(empty);
        }
    }
}
