package me.edgan.redditslide.Notifications;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import androidx.core.content.ContextCompat;

import me.edgan.redditslide.util.LogUtil;

/** Created by carlo_000 on 10/13/2015. */
public class StartOnBoot extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        LogUtil.v("StartOnBoot received intent: " + intent.getAction());
        if (intent.getAction() != null && intent.getAction().equals("android.intent.action.BOOT_COMPLETED")) {
            LogUtil.v("Boot completed, setting up notification alarm");
            /* Setting the alarm here */
            Intent alarmIntent = new Intent(context, NotificationJobScheduler.class);
            PendingIntent pendingIntent =
                    PendingIntent.getBroadcast(
                            context, 0, alarmIntent, PendingIntent.FLAG_IMMUTABLE);

            AlarmManager manager = ContextCompat.getSystemService(context, AlarmManager.class);
            int interval = 8000;
            if (manager != null) {
                LogUtil.v("Setting up alarm with interval: " + interval);
                manager.setInexactRepeating(
                        AlarmManager.RTC_WAKEUP,
                        System.currentTimeMillis(),
                        interval,
                        pendingIntent);
                LogUtil.v("Alarm set successfully");
            } else {
                LogUtil.v("Failed to get AlarmManager");
            }

            // Start the background token keep-warm refresh at boot too, independent of the
            // notification checker above, so a logged-in user's token stays fresh even with
            // notifications disabled. No-op when logged out (see TokenRefreshReceiver.schedule).
            TokenRefreshReceiver.schedule(context);
        } else {
            LogUtil.v("Received non-boot intent");
        }
    }
}
