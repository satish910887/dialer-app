package com.example.hello;

import android.app.Activity;
import android.content.Intent;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.animation.OvershootInterpolator;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

public class SplashActivity extends Activity {
    Handler h = new Handler(Looper.getMainLooper());
    boolean gone;

    int dp(float v) { return Ui.dp(this, v); }

    @Override
    protected void onCreate(Bundle s) {
        super.onCreate(s);
        Ui.applyTheme(this);
        Ui.bars(this);
        if (!getSharedPreferences("app", MODE_PRIVATE).getBoolean("splash", true)) { go(); return; }

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        root.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{Ui.mix(Ui.BG, Ui.ACCENT, 0.28f), Ui.BG}));
        root.setOnClickListener(v -> go());

        ImageView logo = new ImageView(this);
        try { logo.setImageDrawable(getPackageManager().getApplicationIcon(getPackageName())); }
        catch (Throwable e) { logo.setImageResource(android.R.drawable.sym_action_call); }
        root.addView(logo, new LinearLayout.LayoutParams(dp(112), dp(112)));

        TextView title = Ui.text(this, "Dialer", 34, Ui.TEXT, true);
        title.setGravity(Gravity.CENTER);
        title.setPadding(0, dp(20), 0, 0);
        root.addView(title, new LinearLayout.LayoutParams(-1, -2));
        TextView tag = Ui.text(this, "Smarter calls. Better connections.", 14, Ui.MUTED, false);
        tag.setGravity(Gravity.CENTER);
        tag.setPadding(0, dp(6), 0, dp(48));
        root.addView(tag, new LinearLayout.LayoutParams(-1, -2));

        FrameLayoutTrack(root);
        setContentView(root);

        logo.setScaleX(0.7f); logo.setScaleY(0.7f); logo.setAlpha(0f);
        logo.animate().scaleX(1f).scaleY(1f).alpha(1f).setDuration(550)
                .setInterpolator(new OvershootInterpolator(1.3f)).start();
        title.setAlpha(0f); tag.setAlpha(0f);
        title.animate().alpha(1f).setStartDelay(250).setDuration(450).start();
        tag.animate().alpha(1f).setStartDelay(400).setDuration(450).start();
        h.postDelayed(this::go, 1150);
    }

    void FrameLayoutTrack(LinearLayout root) {
        android.widget.FrameLayout track = new android.widget.FrameLayout(this);
        track.setBackground(Ui.shape(this, Ui.SURFACE2, false, 3));
        View fill = new View(this);
        fill.setBackground(Ui.shape(this, Ui.ACCENT, false, 3));
        track.addView(fill, new android.widget.FrameLayout.LayoutParams(-1, -1));
        fill.setPivotX(0f);
        fill.setScaleX(0f);
        fill.animate().scaleX(1f).setDuration(1000).start();
        root.addView(track, new LinearLayout.LayoutParams(dp(140), dp(4)));
    }

    void go() {
        if (gone) return;
        gone = true;
        h.removeCallbacksAndMessages(null);
        startActivity(new Intent(this, MainActivity.class));
        finish();
        if (getSharedPreferences("app", MODE_PRIVATE).getBoolean("splash", true))
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
        else overridePendingTransition(0, 0);
    }

    @Override
    public void onBackPressed() {
        gone = true;
        h.removeCallbacksAndMessages(null);
        super.onBackPressed();
    }

    @Override
    protected void onDestroy() {
        h.removeCallbacksAndMessages(null);
        super.onDestroy();
    }
}
