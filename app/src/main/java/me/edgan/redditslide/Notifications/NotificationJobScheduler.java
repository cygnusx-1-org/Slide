package me.edgan.redditslide.Notifications;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;

import androidx.annotation.Nullable;
import androidx.core.app.AlarmManagerCompat;
import androidx.core.content.ContextCompat;

import me.edgan.redditslide.Reddit;

/** Created by carlo_000 on 10/13/2015. */
public class NotificationJobScheduler {
    private final PendingIntent pendingIntent;
    // Null on a device with no AlarmManager; every use below is guarded.
    @Nullable private final AlarmManager manager;

    public NotificationJobScheduler(Context context) {
        final Intent alarmIntent = new Intent(context, CheckForMail.class);
        pendingIntent =
                PendingIntent.getBroadcast(context, 0, alarmIntent, PendingIntent.FLAG_IMMUTABLE);
        manager = ContextCompat.getSystemService(context, AlarmManager.class);
        start();
    }

    public void start() {
        final int interval = 1000 * 60 * Reddit.notificationTime;
        final long currentTime = System.currentTimeMillis();
        if (manager != null) {
            AlarmManagerCompat.setAndAllowWhileIdle(
                    manager, AlarmManager.RTC_WAKEUP, currentTime + interval, pendingIntent);
        }
    }

    public void cancel() {
        if (manager != null) {
            manager.cancel(pendingIntent);
        }
    }
}
