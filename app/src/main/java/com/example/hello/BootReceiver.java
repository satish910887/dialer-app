package com.example.hello;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

// Re-arms call-back reminders, which the system forgets on reboot and on app update.
public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context ctx, Intent i) {
        String a = i.getAction();
        if (Intent.ACTION_BOOT_COMPLETED.equals(a) || Intent.ACTION_MY_PACKAGE_REPLACED.equals(a)) {
            Reminders.rearmAll(ctx);
        }
    }
}
