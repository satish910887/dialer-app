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
import android.os.PowerManager;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.speech.tts.TextToSpeech;
import android.telecom.Call;
import android.telecom.CallAudioState;
import android.telecom.InCallService;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CallService extends InCallService {
    static final String CH = "calls_silent";
    static final int NID = 1;
    public static CallService instance;
    public static Call current;
    public static Runnable onChange;
    public static final List<Call> calls = new ArrayList<>();
    // display names, so the contacts database is queried once per call instead of on every refresh
    static final Map<Call, String> names = new HashMap<>();

    PowerManager.WakeLock proxLock;
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
            updateProximity();
            notifyUi();
        }
        @Override public void onDetailsChanged(Call c, Call.Details d) { names.remove(c); notifyUi(); }
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
        String cached = names.get(c);
        if (cached != null) return cached;
        String out = resolveName(d);
        // don't remember a number-only answer given before the service was ready to look the name up
        if (instance != null) names.put(c, out);
        return out;
    }

    static String resolveName(Call.Details d) {
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

    // Keeps the screen off while the phone is held to the ear (earpiece only).
    // Lives in the service, not the activity, so it survives the activity pausing when the screen goes dark.
    void updateProximity() {
        boolean want = false;
        try {
            Call c = current;
            if (c != null) {
                int s = c.getState();
                boolean live = s == Call.STATE_ACTIVE || s == Call.STATE_DIALING || s == Call.STATE_CONNECTING;
                CallAudioState as = getCallAudioState();
                int route = as == null ? CallAudioState.ROUTE_EARPIECE : as.getRoute();
                want = live && route == CallAudioState.ROUTE_EARPIECE;
            }
        } catch (Throwable e) { want = false; }
        try {
            if (want) {
                if (proxLock == null) {
                    PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
                    if (pm == null || !pm.isWakeLockLevelSupported(PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK)) return;
                    proxLock = pm.newWakeLock(PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK, "dialer:proximity");
                    proxLock.setReferenceCounted(false);
                }
                if (!proxLock.isHeld()) proxLock.acquire(4 * 60 * 60 * 1000L);
            } else if (proxLock != null && proxLock.isHeld()) {
                proxLock.release(PowerManager.RELEASE_FLAG_WAIT_FOR_NO_PROXIMITY);
            }
        } catch (Throwable e) { }
    }

    @Override
    public void onCallAudioStateChanged(CallAudioState audioState) {
        updateProximity();
        notifyUi();
    }

    // Do Not Disturb: the system no longer rings for us once we play our own ringtone, so honour it here.
    boolean dndAllows(String number) {
        try {
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm == null) return true;
            int f = nm.getCurrentInterruptionFilter();
            if (f == NotificationManager.INTERRUPTION_FILTER_NONE
                    || f == NotificationManager.INTERRUPTION_FILTER_ALARMS) return false;
            if (f != NotificationManager.INTERRUPTION_FILTER_PRIORITY) return true;
            NotificationManager.Policy p = nm.getNotificationPolicy();
            if ((p.priorityCategories & NotificationManager.Policy.PRIORITY_CATEGORY_CALLS) == 0) return false;
            if (p.priorityCallSenders == NotificationManager.Policy.PRIORITY_SENDERS_ANY) return true;
            if (number == null || number.isEmpty()) return false;
            if (p.priorityCallSenders == NotificationManager.Policy.PRIORITY_SENDERS_STARRED) return Favs.has(this, number);
            return !Ui.lookupName(this, number).isEmpty();
        } catch (Throwable e) { return true; }
    }

    Ringtone tryPlay(Uri uri) {
        try {
            if (uri == null) return null;
            Ringtone r = RingtoneManager.getRingtone(this, uri);
            if (r == null) return null;
            r.setAudioAttributes(new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build());
            r.setLooping(true);
            r.play();
            if (r.isPlaying()) return r;
            r.stop();
        } catch (Throwable e) { }
        return null;
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
        for (Call c : new ArrayList<>(calls)) {
            try { c.unregisterCallback(cb); } catch (Throwable e) { }
        }
        calls.clear();
        names.clear();
        current = null;
        hh.removeCallbacksAndMessages(null);
        try { if (proxLock != null && proxLock.isHeld()) proxLock.release(); } catch (Throwable e) { }
        proxLock = null;
        if (instance == this) instance = null;
        super.onDestroy();
    }

    void startRinging() {
        stopRinging();
        AudioManager am = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        int mode = am.getRingerMode();
        if (mode == AudioManager.RINGER_MODE_SILENT) return;
        boolean normal = mode == AudioManager.RINGER_MODE_NORMAL;
        String num = numberOf(current);
        if (!dndAllows(num)) return;

        try {
            if (normal && pref("ringtone")) {
                String tok = Ring.pick(this, num);
                if (!"silent".equals(tok)) {
                    Uri def = RingtoneManager.getActualDefaultRingtoneUri(this, RingtoneManager.TYPE_RINGTONE);
                    Uri uri = tok.isEmpty() ? def : Uri.parse(tok);
                    ringtone = tryPlay(uri);
                    // a missing or unreadable custom tone must never mean a silent phone
                    if (ringtone == null && def != null && !def.equals(uri)) ringtone = tryPlay(def);
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
        updateProximity();
        notifyUi();
    }

    @Override
    public void onCallRemoved(Call call) {
        call.unregisterCallback(cb);
        calls.remove(call);
        names.remove(call);
        if (!anyRinging()) stopRinging();
        recompute();
        updateProximity();
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
