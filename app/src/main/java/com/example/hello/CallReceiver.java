package com.example.hello;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.telecom.Call;
import android.telecom.VideoProfile;

public class CallReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context ctx, Intent i) {
        String a = i.getAction();
        if ("REMIND".equals(a)) {
            String who = i.getStringExtra("who");
            String num = i.getStringExtra("number");
            NotificationManager nm = ctx.getSystemService(NotificationManager.class);
            nm.createNotificationChannel(new NotificationChannel("reminders", "Call reminders",
                    NotificationManager.IMPORTANCE_HIGH));
            Intent dial = new Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", num == null ? "" : num, null));
            PendingIntent pi = PendingIntent.getActivity(ctx, 0, dial,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            Notification n = new Notification.Builder(ctx, "reminders")
                    .setSmallIcon(android.R.drawable.sym_action_call)
                    .setContentTitle("Call back " + (who == null ? "" : who))
                    .setContentText("You asked to be reminded. Tap to call.")
                    .setAutoCancel(true).setContentIntent(pi).build();
            nm.notify((int) (System.currentTimeMillis() % 100000) + 100, n);
            Reminders.done(ctx, i.getIntExtra("id", -1));
            return;
        }
        Call call = CallService.current;
        if (call == null) return;
        if ("ANSWER".equals(a)) {
            call.answer(VideoProfile.STATE_AUDIO_ONLY);
            // opened by the in-call service: a plain receiver is not allowed to start an activity from the background
            CallService svc = CallService.instance;
            if (svc != null) svc.launchUi();
        } else if ("DECLINE".equals(a)) {
            call.reject(false, null);
        } else if ("END".equals(a)) {
            call.disconnect();
        }
    }
}
