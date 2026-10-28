package com.example.hello;

import android.app.Activity;
import android.app.AlarmManager;
import android.app.AlertDialog;
import android.app.PendingIntent;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.telecom.Call;
import android.telecom.CallAudioState;
import android.telecom.VideoProfile;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.ScaleAnimation;
import android.widget.*;

public class InCallActivity extends Activity {
    static final int MATCH = -1, WRAP = -2;
    TextView avatar, name, numberTv, status, dtmfText;
    LinearLayout pad, grid, ringRow, activeRow, quickRow, waitRow;
    FrameLayout sliderBox;
    TextView waitLabel, swapBtn, mergeBtn;
    ImageView[] ctl = new ImageView[6];
    TextView[] lbl = new TextView[6];
    Boolean[] cur = new Boolean[6];
    Call shown;
    View ring, ring2, ring3;
    TextView topLabel, hint;
    FrameLayout avatarBox;
    boolean ending, pulsing, padOpen;
    String lastTime = "", number = "";
    Handler h = new Handler(Looper.getMainLooper());
    Runnable tick = new Runnable() {
        @Override public void run() { refresh(); h.postDelayed(this, 1000); }
    };

    int dp(float v) { return Ui.dp(this, v); }

    void setOn(int i, boolean on) {
        if (cur[i] != null && cur[i] == on) return;
        cur[i] = on;
        ctl[i].setBackground(on ? Ui.glow(this, Ui.ACCENT) : Ui.ovalStroke(this, Ui.SURFACE, Ui.SURFACE2));
        lbl[i].setTextColor(on ? Ui.TEXT : Ui.MUTED);
        ctl[i].setColorFilter(on ? Color.WHITE : Ui.TEXT);
    }

    LinearLayout action(String label, int color, float rot) {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setGravity(Gravity.CENTER_HORIZONTAL);
        c.addView(Ui.callIcon(this, 80, color, rot), new LinearLayout.LayoutParams(dp(80), dp(80)));
        TextView t = Ui.text(this, label, 13, Ui.MUTED, false);
        t.setGravity(Gravity.CENTER);
        t.setPadding(0, dp(8), 0, 0);
        c.addView(t);
        return c;
    }

    LinearLayout quick(int emoji, String label, View.OnClickListener l) {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setGravity(Gravity.CENTER_HORIZONTAL);
        ImageView ic = new ImageView(this);
        ic.setImageResource(emoji);
        ic.setColorFilter(Ui.TEXT);
        ic.setPadding(dp(15), dp(15), dp(15), dp(15));
        ic.setBackground(Ui.ovalStroke(this, Ui.SURFACE, Ui.SURFACE2));
        ic.setOnClickListener(l);
        c.addView(ic, new LinearLayout.LayoutParams(dp(54), dp(54)));
        TextView t = Ui.text(this, label, 12, Ui.MUTED, false);
        t.setGravity(Gravity.CENTER);
        t.setPadding(0, dp(6), 0, 0);
        c.addView(t);
        return c;
    }

    @Override
    protected void onCreate(Bundle s) {
        super.onCreate(s);
        Ui.applyTheme(this);
        Ui.bars(this);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setBackground(Ui.bg(this, "incall"));
        root.setPadding(dp(20), dp(28), dp(20), dp(24));

        avatar = Ui.text(this, "", 42, Color.WHITE, true);
        LinearLayout.LayoutParams ap = new LinearLayout.LayoutParams(dp(120), dp(120));
        ap.topMargin = dp(20);
        ap.bottomMargin = dp(18);
        avatarBox = new FrameLayout(this);
        ring = new View(this);
        android.graphics.drawable.GradientDrawable rg = new android.graphics.drawable.GradientDrawable();
        rg.setShape(android.graphics.drawable.GradientDrawable.OVAL);
        rg.setColor(Color.TRANSPARENT);
        rg.setStroke(dp(3), Ui.ACCENT);
        ring.setBackground(rg);
        ring.setVisibility(View.GONE);
        avatarBox.addView(ring, new FrameLayout.LayoutParams(dp(120), dp(120), Gravity.CENTER));
        ring2 = makeRing();
        ring3 = makeRing();
        avatarBox.addView(ring2, new FrameLayout.LayoutParams(dp(120), dp(120), Gravity.CENTER));
        avatarBox.addView(ring3, new FrameLayout.LayoutParams(dp(120), dp(120), Gravity.CENTER));
        avatarBox.addView(avatar, new FrameLayout.LayoutParams(dp(120), dp(120), Gravity.CENTER));
        LinearLayout.LayoutParams abp = new LinearLayout.LayoutParams(dp(176), dp(176));
        abp.topMargin = dp(4);
        abp.bottomMargin = dp(4);
        topLabel = Ui.text(this, "INCOMING CALL", 12, Ui.MUTED, true);
        topLabel.setGravity(Gravity.CENTER);
        topLabel.setLetterSpacing(0.18f);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(MATCH, WRAP);
        tlp.topMargin = dp(6);
        root.addView(topLabel, tlp);
        root.addView(avatarBox, abp);

        name = Ui.text(this, "", 28, Ui.TEXT, true);
        name.setGravity(Gravity.CENTER);
        name.setSingleLine(true);
        name.setEllipsize(TextUtils.TruncateAt.END);
        numberTv = Ui.text(this, "", 14, Ui.MUTED, false);
        numberTv.setGravity(Gravity.CENTER);
        numberTv.setPadding(0, dp(4), 0, 0);
        status = Ui.text(this, "", 16, Ui.TEXT, false);
        status.setGravity(Gravity.CENTER);
        status.setPadding(0, dp(8), 0, 0);
        dtmfText = Ui.text(this, "", 22, Ui.TEXT, false);
        dtmfText.setGravity(Gravity.CENTER);
        dtmfText.setSingleLine(true);
        dtmfText.setPadding(0, dp(8), 0, dp(4));
        root.addView(name, new LinearLayout.LayoutParams(MATCH, WRAP));
        root.addView(numberTv, new LinearLayout.LayoutParams(MATCH, WRAP));
        root.addView(status, new LinearLayout.LayoutParams(MATCH, WRAP));
        hint = Ui.text(this, "", 12, Ui.MUTED, false);
        hint.setGravity(Gravity.CENTER);
        hint.setPadding(0, dp(4), 0, 0);
        hint.setVisibility(View.GONE);
        root.addView(hint, new LinearLayout.LayoutParams(MATCH, WRAP));
        root.addView(dtmfText, new LinearLayout.LayoutParams(MATCH, WRAP));

        waitRow = new LinearLayout(this);
        waitRow.setOrientation(LinearLayout.VERTICAL);
        waitRow.setGravity(Gravity.CENTER);
        waitRow.setPadding(dp(14), dp(10), dp(14), dp(12));
        waitRow.setBackground(Ui.card(this, Ui.SURFACE, 18, Ui.SURFACE2));
        waitRow.setVisibility(View.GONE);
        waitLabel = Ui.text(this, "", 13, Ui.MUTED, false);
        waitLabel.setGravity(Gravity.CENTER);
        waitRow.addView(waitLabel, new LinearLayout.LayoutParams(MATCH, WRAP));
        LinearLayout wb = new LinearLayout(this);
        wb.setGravity(Gravity.CENTER);
        swapBtn = Ui.text(this, "⇄  Swap", 13, Color.WHITE, true);
        swapBtn.setPadding(dp(18), dp(9), dp(18), dp(9));
        swapBtn.setBackground(Ui.ripple(this, Ui.ACCENT, false, 20));
        mergeBtn = Ui.text(this, "⑂  Merge", 13, Color.WHITE, true);
        mergeBtn.setPadding(dp(18), dp(9), dp(18), dp(9));
        mergeBtn.setBackground(Ui.ripple(this, Ui.GREEN, false, 20));
        LinearLayout.LayoutParams wl = new LinearLayout.LayoutParams(WRAP, WRAP);
        wl.setMargins(dp(5), dp(8), dp(5), 0);
        wb.addView(swapBtn, wl);
        wb.addView(mergeBtn, wl);
        waitRow.addView(wb);
        LinearLayout.LayoutParams wrp = new LinearLayout.LayoutParams(MATCH, WRAP);
        wrp.topMargin = dp(8);
        root.addView(waitRow, wrp);
        swapBtn.setOnClickListener(v -> {
            Call c = CallService.current;
            if (c == null) return;
            Call o = CallService.other(c);
            if (o == null) return;
            if (o.getState() == Call.STATE_HOLDING) o.unhold();
            else if (c.getState() == Call.STATE_HOLDING) c.unhold();
        });
        mergeBtn.setOnClickListener(v -> {
            Call c = CallService.current;
            if (c == null) return;
            Call o = CallService.other(c);
            if (o == null) return;
            if (c.getDetails().can(Call.Details.CAPABILITY_MERGE_CONFERENCE)) c.conference(o);
            else if (o.getDetails().can(Call.Details.CAPABILITY_MERGE_CONFERENCE)) o.conference(c);
            else Toast.makeText(this, "Your network doesn't allow merging these calls", Toast.LENGTH_SHORT).show();
        });

        pad = new LinearLayout(this);
        pad.setOrientation(LinearLayout.VERTICAL);
        pad.setVisibility(View.GONE);
        String[][] rows = {{"1", "2", "3"}, {"4", "5", "6"}, {"7", "8", "9"}, {"*", "0", "#"}};
        for (String[] r : rows) {
            LinearLayout row = new LinearLayout(this);
            row.setGravity(Gravity.CENTER);
            for (String k : r) {
                TextView b = Ui.text(this, k, 24, Ui.TEXT, false);
                b.setGravity(Gravity.CENTER);
                b.setBackground(Ui.ovalStroke(this, Ui.SURFACE, Ui.SURFACE2));
                final char ch = k.charAt(0);
                b.setOnTouchListener((v, e) -> {
                    Call c = CallService.current;
                    int act = e.getAction();
                    if (act == MotionEvent.ACTION_DOWN) {
                        v.setPressed(true);
                        if (c != null) { c.playDtmfTone(ch); dtmfText.append(String.valueOf(ch)); }
                    } else if (act == MotionEvent.ACTION_UP || act == MotionEvent.ACTION_CANCEL) {
                        v.setPressed(false);
                        if (c != null) c.stopDtmfTone();
                    }
                    return true;
                });
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(62), dp(62));
                lp.setMargins(dp(10), dp(4), dp(10), dp(4));
                row.addView(b, lp);
            }
            pad.addView(row);
        }
        root.addView(pad, new LinearLayout.LayoutParams(MATCH, WRAP));
        root.addView(new Space(this), new LinearLayout.LayoutParams(1, 0, 1));

        quickRow = new LinearLayout(this);
        quickRow.setGravity(Gravity.CENTER);
        quickRow.addView(quick(R.drawable.ic_chat, "Message", v -> replyDialog()), new LinearLayout.LayoutParams(dp(110), WRAP));
        quickRow.addView(quick(R.drawable.ic_alarm, "Remind me", v -> remindDialog()), new LinearLayout.LayoutParams(dp(110), WRAP));
        LinearLayout.LayoutParams qp = new LinearLayout.LayoutParams(MATCH, WRAP);
        qp.bottomMargin = dp(24);
        root.addView(quickRow, qp);

        grid = new LinearLayout(this);
        grid.setOrientation(LinearLayout.VERTICAL);
        int[] ic = {R.drawable.ic_mic, R.drawable.ic_dialpad, R.drawable.ic_volume,
                R.drawable.ic_add, R.drawable.ic_pause, R.drawable.ic_chat};
        String[] names = {"Mute", "Keypad", "Speaker", "Add call", "Hold", "Message"};
        LinearLayout gr = null;
        for (int i = 0; i < 6; i++) {
            if (i % 3 == 0) {
                gr = new LinearLayout(this);
                grid.addView(gr, new LinearLayout.LayoutParams(MATCH, WRAP));
            }
            LinearLayout cell = new LinearLayout(this);
            cell.setOrientation(LinearLayout.VERTICAL);
            cell.setGravity(Gravity.CENTER_HORIZONTAL);
            cell.setPadding(0, dp(6), 0, dp(6));
            ctl[i] = new ImageView(this);
            ctl[i].setImageResource(ic[i]);
            ctl[i].setPadding(dp(19), dp(19), dp(19), dp(19));
            lbl[i] = Ui.text(this, names[i], 12, Ui.MUTED, false);
            lbl[i].setGravity(Gravity.CENTER);
            lbl[i].setPadding(0, dp(6), 0, 0);
            cell.addView(ctl[i], new LinearLayout.LayoutParams(dp(64), dp(64)));
            cell.addView(lbl[i]);
            gr.addView(cell, new LinearLayout.LayoutParams(0, WRAP, 1));
            setOn(i, false);
        }
        LinearLayout.LayoutParams gp = new LinearLayout.LayoutParams(MATCH, WRAP);
        gp.bottomMargin = dp(18);
        root.addView(grid, gp);

        ringRow = new LinearLayout(this);
        ringRow.setGravity(Gravity.CENTER);
        LinearLayout decline = action("Decline", Ui.RED, 135f);
        LinearLayout answer = action("Accept", Ui.GREEN, 0f);
        android.view.animation.TranslateAnimation bounce = new android.view.animation.TranslateAnimation(0, 0, 0, -dp(9));
        bounce.setDuration(650);
        bounce.setRepeatMode(Animation.REVERSE);
        bounce.setRepeatCount(Animation.INFINITE);
        bounce.setInterpolator(new android.view.animation.AccelerateDecelerateInterpolator());
        answer.getChildAt(0).startAnimation(bounce);
        ringRow.addView(decline, new LinearLayout.LayoutParams(0, WRAP, 1));
        ringRow.addView(answer, new LinearLayout.LayoutParams(0, WRAP, 1));
        root.addView(ringRow, new LinearLayout.LayoutParams(MATCH, WRAP));

        sliderBox = new FrameLayout(this);
        sliderBox.setBackground(Ui.card(this, Ui.SURFACE, 40, Ui.SURFACE2));
        sliderBox.setVisibility(View.GONE);
        TextView sh = Ui.text(this, "◀  Decline            Answer  ▶", 13, Ui.MUTED, false);
        sh.setGravity(Gravity.CENTER);
        sliderBox.addView(sh, new FrameLayout.LayoutParams(MATCH, MATCH));
        android.view.animation.AlphaAnimation shim = new android.view.animation.AlphaAnimation(0.3f, 1f);
        shim.setDuration(900);
        shim.setRepeatMode(Animation.REVERSE);
        shim.setRepeatCount(Animation.INFINITE);
        sh.startAnimation(shim);
        final ImageView knob = Ui.callIcon(this, 64, Ui.GREEN, 0);
        FrameLayout.LayoutParams kp = new FrameLayout.LayoutParams(dp(64), dp(64), Gravity.CENTER);
        sliderBox.addView(knob, kp);
        final float[] st = new float[2];
        knob.setOnTouchListener((v, e) -> {
            int a = e.getActionMasked();
            float max = (sliderBox.getWidth() - dp(64)) / 2f - dp(6);
            if (a == MotionEvent.ACTION_DOWN) {
                st[0] = e.getRawX();
                v.getParent().requestDisallowInterceptTouchEvent(true);
                return true;
            }
            if (a == MotionEvent.ACTION_MOVE) {
                float dx = Math.max(-max, Math.min(max, e.getRawX() - st[0]));
                v.setTranslationX(dx);
                float fr = Math.abs(dx) / Math.max(1f, max);
                int tint = (int) new android.animation.ArgbEvaluator().evaluate(fr * 0.55f, Ui.SURFACE, dx < 0 ? Ui.RED : Ui.GREEN);
                sliderBox.setBackground(Ui.card(this, tint, 40, Ui.SURFACE2));
                float neg = dx < 0 ? 1f : 0f;
                if (neg != st[1]) {
                    st[1] = neg;
                    knob.setBackground(Ui.glow(this, neg == 1f ? Ui.RED : Ui.GREEN));
                    knob.setRotation(neg == 1f ? 135f : -45f);
                }
                return true;
            }
            if (a == MotionEvent.ACTION_UP || a == MotionEvent.ACTION_CANCEL) {
                float dx = v.getTranslationX();
                Call c = CallService.current;
                if (a == MotionEvent.ACTION_UP && c != null && dx > max * 0.8f) {
                    sliderBox.setVisibility(View.GONE);
                    c.answer(VideoProfile.STATE_AUDIO_ONLY);
                } else if (a == MotionEvent.ACTION_UP && c != null && dx < -max * 0.8f) {
                    sliderBox.setVisibility(View.GONE);
                    c.reject(false, null);
                }
                v.animate().translationX(0f).setDuration(200).start();
                sliderBox.setBackground(Ui.card(this, Ui.SURFACE, 40, Ui.SURFACE2));
                st[1] = 0f;
                knob.setBackground(Ui.glow(this, Ui.GREEN));
                knob.setRotation(-45f);
                return true;
            }
            return true;
        });
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(MATCH, dp(80));
        slp.topMargin = dp(6);
        root.addView(sliderBox, slp);

        activeRow = new LinearLayout(this);
        activeRow.setGravity(Gravity.CENTER);
        LinearLayout end = new LinearLayout(this);
        end.setOrientation(LinearLayout.HORIZONTAL);
        end.setGravity(Gravity.CENTER);
        android.graphics.drawable.GradientDrawable egd = new android.graphics.drawable.GradientDrawable(
                android.graphics.drawable.GradientDrawable.Orientation.TL_BR,
                new int[]{0xFFFF5A5A, 0xFFD62828});
        egd.setCornerRadius(dp(34));
        end.setBackground(egd);
        end.setElevation(dp(8));
        try { end.setOutlineSpotShadowColor(Ui.RED); end.setOutlineAmbientShadowColor(Ui.RED); } catch (Throwable e) { }
        ImageView eic = new ImageView(this);
        eic.setImageResource(R.drawable.ic_call);
        eic.setColorFilter(Color.WHITE);
        eic.setRotation(135f);
        end.addView(eic, new LinearLayout.LayoutParams(dp(28), dp(28)));
        TextView etx = Ui.text(this, "End call", 16, Color.WHITE, true);
        etx.setPadding(dp(12), 0, 0, 0);
        end.addView(etx);
        Ui.press(end);
        activeRow.addView(end, new LinearLayout.LayoutParams(dp(230), dp(68)));
        root.addView(activeRow, new LinearLayout.LayoutParams(MATCH, WRAP));
        setContentView(root);

        View.OnClickListener hang = v -> {
            Call c = CallService.current;
            if (c == null) { finish(); return; }
            if (c.getState() == Call.STATE_RINGING) c.reject(false, null);
            else c.disconnect();
        };
        decline.getChildAt(0).setOnClickListener(hang);
        end.setOnClickListener(hang);
        answer.getChildAt(0).setOnClickListener(v -> {
            Call c = CallService.current;
            if (c != null) c.answer(VideoProfile.STATE_AUDIO_ONLY);
        });

        ctl[0].setOnClickListener(v -> {
            CallService svc = CallService.instance;
            if (svc == null) return;
            CallAudioState as = svc.getCallAudioState();
            boolean m = !(as != null && as.isMuted());
            svc.setMuted(m);
            setOn(0, m);
        });
        ctl[1].setOnClickListener(v -> {
            padOpen = !padOpen;
            pad.setVisibility(padOpen ? View.VISIBLE : View.GONE);
            avatarBox.setVisibility(padOpen ? View.GONE : View.VISIBLE);
            setOn(1, padOpen);
        });
        ctl[2].setOnClickListener(v -> {
            CallService svc = CallService.instance;
            if (svc == null) return;
            CallAudioState as = svc.getCallAudioState();
            boolean spk = as != null && as.getRoute() == CallAudioState.ROUTE_SPEAKER;
            svc.setAudioRoute(spk ? CallAudioState.ROUTE_WIRED_OR_EARPIECE : CallAudioState.ROUTE_SPEAKER);
            setOn(2, !spk);
        });
        ctl[3].setOnClickListener(v -> {
            Intent in = new Intent(this, MainActivity.class);
            in.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            startActivity(in);
        });
        ctl[4].setOnClickListener(v -> {
            Call c = CallService.current;
            if (c == null) return;
            if (c.getState() == Call.STATE_HOLDING) { c.unhold(); setOn(4, false); }
            else { c.hold(); setOn(4, true); }
        });
        ctl[5].setOnClickListener(v -> sms());
    }

    void updateWaiting(Call c) {
        Call o = CallService.other(c);
        if (o == null || c.getState() == Call.STATE_RINGING && o.getState() == Call.STATE_DISCONNECTED) {
            waitRow.setVisibility(View.GONE);
            return;
        }
        boolean ringing = c.getState() == Call.STATE_RINGING;
        waitRow.setVisibility(View.VISIBLE);
        waitLabel.setText(ringing ? "Call waiting · you are on " + CallService.who(o)
                : "Other call: " + CallService.who(o)
                + (o.getState() == Call.STATE_HOLDING ? " (on hold)" : ""));
        swapBtn.setVisibility(ringing ? View.GONE : View.VISIBLE);
        boolean canMerge = c.getDetails().can(Call.Details.CAPABILITY_MERGE_CONFERENCE)
                || o.getDetails().can(Call.Details.CAPABILITY_MERGE_CONFERENCE);
        mergeBtn.setVisibility(!ringing && canMerge ? View.VISIBLE : View.GONE);
    }

    android.view.GestureDetector gd;

    String gestureHint() {
        int m = getSharedPreferences("app", MODE_PRIVATE).getInt("gesture", 0);
        if (m == 1) return "\nSwipe up to answer, down to decline";
        if (m == 2) return "\nSwipe right to answer, left to decline";
        return "";
    }

    float[] swp = new float[2];

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        int a = ev.getActionMasked();
        if (a == MotionEvent.ACTION_DOWN) { swp[0] = ev.getRawX(); swp[1] = ev.getRawY(); }
        if (a == MotionEvent.ACTION_UP) {
            Call c = CallService.current;
            int m = getSharedPreferences("app", MODE_PRIVATE).getInt("gesture", 0);
            if (c != null && c.getState() == Call.STATE_RINGING && (m == 1 || m == 2)) {
                float dx = ev.getRawX() - swp[0], dy = ev.getRawY() - swp[1];
                float min = dp(80);
                if (m == 1 && Math.abs(dy) > min && Math.abs(dy) > Math.abs(dx) * 1.2f) {
                    if (dy < 0) c.answer(VideoProfile.STATE_AUDIO_ONLY); else c.reject(false, null);
                    return true;
                }
                if (m == 2 && Math.abs(dx) > min && Math.abs(dx) > Math.abs(dy) * 1.2f) {
                    if (dx > 0) c.answer(VideoProfile.STATE_AUDIO_ONLY); else c.reject(false, null);
                    return true;
                }
            }
        }
        return super.dispatchTouchEvent(ev);
    }

    void sms() {
        try {
            startActivity(new Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:" + Uri.encode(number))));
        } catch (Exception e) {
            Toast.makeText(this, "No messaging app found", Toast.LENGTH_SHORT).show();
        }
    }

    void replyDialog() {
        final String[] msgs = QuickReplies.get(this);
        new AlertDialog.Builder(this).setTitle("Decline with message")
                .setItems(msgs, (d, i) -> {
                    Call c = CallService.current;
                    if (c == null) return;
                    if (i == 3) { c.reject(false, null); sms(); return; }
                    try { c.reject(true, msgs[i]); }
                    catch (Throwable e) {
                        c.reject(false, null);
                        Toast.makeText(this, "Couldn't send the message", Toast.LENGTH_SHORT).show();
                    }
                }).show();
    }

    void remindDialog() {
        final String[] opts = {"In 5 minutes", "In 30 minutes", "In 1 hour"};
        final long[] mins = {5, 30, 60};
        new AlertDialog.Builder(this).setTitle("Remind me to call back")
                .setItems(opts, (d, i) -> {
                    long at = System.currentTimeMillis() + mins[i] * 60000L;
                    Intent in = new Intent(this, CallReceiver.class).setAction("REMIND")
                            .putExtra("who", name.getText().toString()).putExtra("number", number);
                    PendingIntent pi = PendingIntent.getBroadcast(this, (int) (at % 1000000), in,
                            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
                    ((AlarmManager) getSystemService(ALARM_SERVICE)).set(AlarmManager.RTC_WAKEUP, at, pi);
                    Toast.makeText(this, "Reminder set", Toast.LENGTH_SHORT).show();
                    Call c = CallService.current;
                    if (c != null && c.getState() == Call.STATE_RINGING) c.reject(false, null);
                }).show();
    }

    @Override
    protected void onResume() {
        super.onResume();
        CallService.onChange = () -> runOnUiThread(this::refresh);
        h.post(tick);
    }

    @Override
    protected void onPause() {
        super.onPause();
        CallService.onChange = null;
        h.removeCallbacks(tick);
        pulse(false);
    }

    String duration(long ms) {
        long s = Math.max(0, ms / 1000);
        if (s >= 3600) return String.format("%d:%02d:%02d", s / 3600, (s % 3600) / 60, s % 60);
        return String.format("%02d:%02d", s / 60, s % 60);
    }

    View makeRing() {
        View v = new View(this);
        android.graphics.drawable.GradientDrawable g = new android.graphics.drawable.GradientDrawable();
        g.setShape(android.graphics.drawable.GradientDrawable.OVAL);
        g.setColor(Color.TRANSPARENT);
        g.setStroke(dp(2), Ui.ACCENT);
        v.setBackground(g);
        v.setVisibility(View.GONE);
        return v;
    }

    android.view.animation.AnimationSet ringAnim(long off) {
        android.view.animation.AnimationSet set = new android.view.animation.AnimationSet(false);
        ScaleAnimation sc = new ScaleAnimation(1f, 1.45f, 1f, 1.45f,
                Animation.RELATIVE_TO_SELF, .5f, Animation.RELATIVE_TO_SELF, .5f);
        android.view.animation.AlphaAnimation al = new android.view.animation.AlphaAnimation(0.7f, 0f);
        for (Animation a : new Animation[]{sc, al}) {
            a.setDuration(1800);
            a.setRepeatCount(Animation.INFINITE);
            a.setStartOffset(off);
            set.addAnimation(a);
        }
        return set;
    }

    void pulse(boolean on) {
        if (on == pulsing) return;
        pulsing = on;
        if (on) {
            ScaleAnimation a = new ScaleAnimation(1f, 1.08f, 1f, 1.08f,
                    Animation.RELATIVE_TO_SELF, .5f, Animation.RELATIVE_TO_SELF, .5f);
            a.setDuration(800);
            a.setRepeatMode(Animation.REVERSE);
            a.setRepeatCount(Animation.INFINITE);
            avatar.startAnimation(a);
            ring.setVisibility(View.VISIBLE);
            android.view.animation.AnimationSet set = new android.view.animation.AnimationSet(true);
            ScaleAnimation sc = new ScaleAnimation(1f, 1.45f, 1f, 1.45f,
                    Animation.RELATIVE_TO_SELF, .5f, Animation.RELATIVE_TO_SELF, .5f);
            android.view.animation.AlphaAnimation al = new android.view.animation.AlphaAnimation(0.8f, 0f);
            set.addAnimation(sc);
            set.addAnimation(al);
            set.setDuration(1400);
            set.setRepeatCount(Animation.INFINITE);
            ring.startAnimation(ringAnim(0));
            ring2.setVisibility(View.VISIBLE);
            ring2.startAnimation(ringAnim(600));
            ring3.setVisibility(View.VISIBLE);
            ring3.startAnimation(ringAnim(1200));
        } else {
            avatar.clearAnimation();
            ring.clearAnimation();
            ring.setVisibility(View.GONE);
            ring2.clearAnimation();
            ring2.setVisibility(View.GONE);
            ring3.clearAnimation();
            ring3.setVisibility(View.GONE);
        }
    }

    void refresh() {
        Call c = CallService.current;
        if (c == null) {
            if (shown == null) { finish(); return; }
            if (ending) return;
            ending = true;
            status.setText(lastTime.isEmpty() ? "Call ended" : "Call ended  ·  " + lastTime);
            pulse(false);
            grid.setAlpha(0.4f);
            ringRow.setVisibility(View.GONE);
            quickRow.setVisibility(View.GONE);
            activeRow.setVisibility(View.INVISIBLE);
            h.postDelayed(this::finish, 1500);
            return;
        }
        if (c != shown) {
            ending = false;
            grid.setAlpha(1f);
            shown = c;
            String n = CallService.who(c);
            number = c.getDetails().getHandle() != null
                    ? c.getDetails().getHandle().getSchemeSpecificPart() : "";
            name.setText(n);
            numberTv.setText(n.equals(number) ? "" : number);
            Ui.bindAvatar(this, avatar, n.equals(number) ? null : n);
            avatar.setTextSize(42);
            Photos.bind(this, avatar, number, null, true);
        }
        int st = c.getState();
        boolean ringing = st == Call.STATE_RINGING;
        String t;
        if (ringing) t = "Incoming call…";
        else if (st == Call.STATE_DIALING || st == Call.STATE_CONNECTING) t = "Calling…";
        else if (st == Call.STATE_ACTIVE || st == Call.STATE_HOLDING) {
            long start = c.getDetails().getConnectTimeMillis();
            String time = start > 0 ? duration(System.currentTimeMillis() - start) : "";
            lastTime = time;
            t = (st == Call.STATE_HOLDING ? "On hold  ·  " : "") + time;
        }
        else if (st == Call.STATE_DISCONNECTED) t = "Call ended";
        else t = "";
        status.setText(t);
        boolean act = st == Call.STATE_ACTIVE || st == Call.STATE_HOLDING;
        status.setTextSize(act ? 26 : 16);
        try { status.setFontFeatureSettings("tnum"); } catch (Throwable e) { }
        String gh = ringing ? gestureHint().trim() : "";
        hint.setText(gh);
        hint.setVisibility(gh.isEmpty() ? View.GONE : View.VISIBLE);
        topLabel.setText(ringing ? "INCOMING CALL" : st == Call.STATE_HOLDING ? "ON HOLD"
                : (st == Call.STATE_DIALING || st == Call.STATE_CONNECTING) ? "CALLING" : "ON CALL");

        boolean slide = getSharedPreferences("app", MODE_PRIVATE).getInt("gesture", 0) == 3;
        ringRow.setVisibility(ringing && !slide ? View.VISIBLE : View.GONE);
        sliderBox.setVisibility(ringing && slide ? View.VISIBLE : View.GONE);
        quickRow.setVisibility(ringing ? View.VISIBLE : View.GONE);
        if (!ringing && activeRow.getVisibility() != View.VISIBLE) {
            activeRow.setAlpha(0f);
            activeRow.setTranslationY(dp(40));
            activeRow.animate().alpha(1f).translationY(0f).setDuration(350).start();
        }
        activeRow.setVisibility(ringing ? View.GONE : View.VISIBLE);
        grid.setVisibility(ringing ? View.GONE : View.VISIBLE);
        updateWaiting(c);
        pulse(ringing || st == Call.STATE_DIALING || st == Call.STATE_CONNECTING);

        if (!ringing) {
            CallAudioState as = CallService.instance != null
                    ? CallService.instance.getCallAudioState() : null;
            setOn(0, as != null && as.isMuted());
            setOn(2, as != null && as.getRoute() == CallAudioState.ROUTE_SPEAKER);
            setOn(4, st == Call.STATE_HOLDING);
            setOn(1, padOpen);
        }
    }
}
