package com.example.hello;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.speech.tts.TextToSpeech;
import android.telecom.Call;
import android.telecom.InCallService;
import java.util.ArrayList;
import java.util.List;

public class CallService extends InCallService {
    static final String CH = "calls_silent";
    static final int NID = 1;
    public static CallService instance;
    public static Call current;
    public static Runnable onChange;
    public static final List<Call> calls = new ArrayList<>();

    Ringtone ringtone;
    Vibrator vibrator;
    SensorManager sm;
    TextToSpeech tts;
    boolean ttsReady, flipOn;
    Handler hh = new Handler(Looper.getMainLooper());

    SensorEventListener flipL = new SensorEventListener() {
        @Override public void onSensorChanged(SensorEvent e) {
            if (e.values.length > 2 && e.values[2] < -7f) hh.post(() -> stopRinging());
        }
        @Override public void onAccuracyChanged(Sensor s, int a) { }
    };

    Runnable speakLoop = new Runnable() {
        @Override public void run() {
            if (!anyRinging()) return;
            if (ttsReady && tts != null && current != null) {
                tts.speak("Incoming call from " + who(current), TextToSpeech.QUEUE_FLUSH, null, "cid");
            }
            hh.postDelayed(this, 5000);
        }
    };

    Call.Callback cb = new Call.Callback() {
        @Override public void onStateChanged(Call c, int state) {
            if (!anyRinging()) stopRinging();
            recompute();
            if (current != null) showNotification(current);
            notifyUi();
        }
        @Override public void onDetailsChanged(Call c, Call.Details d) { notifyUi(); }
        @Override public void onChildrenChanged(Call c, List<Call> children) { notifyUi(); }
    };

    static boolean anyRinging() {
        for (Call c : calls) if (c.getState() == Call.STATE_RINGING) return true;
        return false;
    }

    static void recompute() {
        Call ring = null, act = null, hold = null;
        for (Call c : calls) {
            if (c.getParent() != null) continue;
            int s = c.getState();
            if (s == Call.STATE_DISCONNECTED) continue;
            if (s == Call.STATE_RINGING) { if (ring == null) ring = c; }
            else if (s == Call.STATE_HOLDING) hold = c;
            else act = c;
        }
        current = ring != null ? ring : act != null ? act : hold;
    }

    public static Call other(Call c) {
        for (Call x : calls) {
            if (x != c && x.getParent() == null && x.getState() != Call.STATE_DISCONNECTED) return x;
        }
        return null;
    }

    static String numberOf(Call c) {
        return c != null && c.getDetails().getHandle() != null
                ? c.getDetails().getHandle().getSchemeSpecificPart() : "";
    }

    static String who(Call c) {
        Call.Details d = c.getDetails();
        if (d.hasProperty(Call.Details.PROPERTY_CONFERENCE)) return "Conference call";
        if (d.getCallerDisplayName() != null && !d.getCallerDisplayName().isEmpty())
            return d.getCallerDisplayName();
        if (d.getHandle() != null) {
            String num = d.getHandle().getSchemeSpecificPart();
            if (instance != null) {
                String nm = Ui.lookupName(instance, num);
                if (!nm.isEmpty()) return nm;
            }
            return num;
        }
        return "Unknown";
    }

    boolean pref(String k) {
        return getSharedPreferences("app", Context.MODE_PRIVATE).getBoolean(k, true);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        NotificationChannel ch = new NotificationChannel(CH, "Calls", NotificationManager.IMPORTANCE_HIGH);
        ch.setSound(null, null);
        ch.enableVibration(false);
        getSystemService(NotificationManager.class).createNotificationChannel(ch);
    }

    @Override
    public void onDestroy() {
        stopRinging();
        try { if (tts != null) { tts.shutdown(); tts = null; } } catch (Throwable e) { }
        super.onDestroy();
    }

    void startRinging() {
        stopRinging();
        AudioManager am = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        int mode = am.getRingerMode();
        if (mode == AudioManager.RINGER_MODE_SILENT) return;
        boolean normal = mode == AudioManager.RINGER_MODE_NORMAL;
        String num = numberOf(current);

        try {
            if (normal && pref("ringtone")) {
                String tok = Ring.pick(this, num);
                if (!"silent".equals(tok)) {
                    Uri uri = tok.isEmpty()
                            ? RingtoneManager.getActualDefaultRingtoneUri(this, RingtoneManager.TYPE_RINGTONE)
                            : Uri.parse(tok);
                    ringtone = RingtoneManager.getRingtone(this, uri);
                    if (ringtone != null) {
                        ringtone.setAudioAttributes(new AudioAttributes.Builder()
                                .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build());
                        ringtone.setLooping(true);
                        ringtone.play();
                    }
                }
            }
        } catch (Throwable e) { }

        try {
            if (pref("ring_vibe")) {
                vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
                if (vibrator != null && vibrator.hasVibrator()) {
                    int p = getSharedPreferences("app", Context.MODE_PRIVATE).getInt("vibe_pattern", 0);
                    if (p < 0 || p >= Ring.VIBE.length) p = 0;
                    vibrator.vibrate(VibrationEffect.createWaveform(Ring.VIBE[p], 0),
                            new AudioAttributes.Builder()
                                    .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE).build());
                }
            }
        } catch (Throwable e) { }

        try {
            if (pref("flip_silence")) {
                sm = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
                Sensor acc = sm == null ? null : sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
                if (acc != null) { sm.registerListener(flipL, acc, SensorManager.SENSOR_DELAY_NORMAL); flipOn = true; }
            }
        } catch (Throwable e) { }

        try {
            if (normal && getSharedPreferences("app", Context.MODE_PRIVATE).getBoolean("announce", false)) {
                if (tts == null) tts = new TextToSpeech(this, st -> ttsReady = st == TextToSpeech.SUCCESS);
                hh.removeCallbacks(speakLoop);
                hh.postDelayed(speakLoop, 1200);
            }
        } catch (Throwable e) { }
    }

    void stopRinging() {
        try { if (ringtone != null) { ringtone.stop(); ringtone = null; } } catch (Throwable e) { }
        try { if (vibrator != null) { vibrator.cancel(); vibrator = null; } } catch (Throwable e) { }
        try { if (flipOn && sm != null) { sm.unregisterListener(flipL); flipOn = false; } } catch (Throwable e) { }
        try { hh.removeCallbacks(speakLoop); if (tts != null) tts.stop(); } catch (Throwable e) { }
    }

    @Override
    public void onCallAdded(Call call) {
        if (call.getState() == Call.STATE_RINGING) {
            String num = numberOf(call);
            String reason = SpamGuard.reason(this, num, call.getDetails().getCallerDisplayName());
            if (reason != null) {
                SpamGuard.logBlocked(this, num, reason);
                call.reject(false, null);
                return;
            }
        }
        calls.add(call);
        call.registerCallback(cb);
        recompute();
        if (current != null) showNotification(current);
        if (call.getState() == Call.STATE_RINGING) {
            startRinging();
            boolean idle;
            try {
                android.app.KeyguardManager km = (android.app.KeyguardManager) getSystemService(KEYGUARD_SERVICE);
                android.os.PowerManager pm = (android.os.PowerManager) getSystemService(POWER_SERVICE);
                idle = km.isKeyguardLocked() || !pm.isInteractive();
            } catch (Throwable e) { idle = true; }
            if (idle) launchUi();
        } else if (call.getParent() == null) {
            Intent i = new Intent(this, InCallActivity.class);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(i);
        }
        notifyUi();
    }

    @Override
    public void onCallRemoved(Call call) {
        call.unregisterCallback(cb);
        calls.remove(call);
        if (!anyRinging()) stopRinging();
        recompute();
        if (current == null) {
            getSystemService(NotificationManager.class).cancel(NID);
        } else {
            showNotification(current);
        }
        notifyUi();
    }

    void launchUi() {
        try {
            Intent i = new Intent(this, InCallActivity.class);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(i);
        } catch (Throwable e) { }
    }

    PendingIntent pi(String action, int req) {
        Intent i = new Intent(this, CallReceiver.class).setAction(action);
        return PendingIntent.getBroadcast(this, req, i,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    void showNotification(Call c) {
        Intent ui = new Intent(this, InCallActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        PendingIntent open = PendingIntent.getActivity(this, 0, ui,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        boolean ringing = c.getState() == Call.STATE_RINGING;
        String name = who(c);
        Notification n = null;
        if (ringing && Build.VERSION.SDK_INT >= 31) {
            try {
                android.app.Person p = new android.app.Person.Builder().setName(name).setImportant(true).build();
                n = new Notification.Builder(this, CH)
                        .setSmallIcon(R.drawable.ic_call)
                        .setColor(Ui.ACCENT)
                        .setCategory(Notification.CATEGORY_CALL)
                        .setOngoing(true)
                        .setContentIntent(open)
                        .setFullScreenIntent(open, true)
                        .setStyle(Notification.CallStyle.forIncomingCall(p, pi("DECLINE", 2), pi("ANSWER", 1)))
                        .build();
            } catch (Throwable e) { n = null; }
        }
        if (n == null) {
            Notification.Builder b = new Notification.Builder(this, CH)
                    .setSmallIcon(R.drawable.ic_call)
                    .setColor(Ui.ACCENT)
                    .setContentTitle(name)
                    .setContentText(ringing ? "Incoming call" : "Ongoing call")
                    .setCategory(Notification.CATEGORY_CALL)
                    .setOngoing(true)
                    .setContentIntent(open);
            if (ringing) {
                b.setFullScreenIntent(open, true);
                b.addAction(new Notification.Action.Builder(0, "Answer", pi("ANSWER", 1)).build());
                b.addAction(new Notification.Action.Builder(0, "Decline", pi("DECLINE", 2)).build());
            } else {
                long start = c.getDetails().getConnectTimeMillis();
                if (start > 0) b.setWhen(start).setShowWhen(true).setUsesChronometer(true);
                b.addAction(new Notification.Action.Builder(0, "End call", pi("END", 3)).build());
            }
            n = b.build();
        }
        getSystemService(NotificationManager.class).notify(NID, n);
    }

    static void notifyUi() {
        if (onChange != null) onChange.run();
    }
}
