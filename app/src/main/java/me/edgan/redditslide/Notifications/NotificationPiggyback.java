package me.edgan.redditslide.Notifications;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.text.TextUtils;

import androidx.core.content.ContextCompat;

import me.edgan.redditslide.util.LogUtil;

/** Created by Carlos on 9/27/2017. */
public class NotificationPiggyback extends NotificationListenerService {

    @Override
    public void onCreate() {
        super.onCreate();
        LogUtil.v("NotificationPiggyback service created");
    }

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        final String packageName = sbn.getPackageName();
        LogUtil.v("Notification received from: " + packageName);

        if (!TextUtils.isEmpty(packageName) && packageName.equals("com.reddit.frontpage")) {
            LogUtil.v("Processing Reddit notification");
            cancelNotification(sbn.getKey());

            // Schedule check without accessing Authentication directly
            Intent alarmIntent = new Intent(getApplicationContext(), CheckForMailSingle.class);
            PendingIntent pendingIntent =
                    PendingIntent.getBroadcast(
                            getApplicationContext(), 0, alarmIntent, PendingIntent.FLAG_IMMUTABLE);

            AlarmManager manager = ContextCompat.getSystemService(getApplicationContext(), AlarmManager.class);
            if (manager != null) {
                LogUtil.v("Scheduling mail check");
                manager.set(
                        AlarmManager.RTC_WAKEUP,
                        System.currentTimeMillis() + 100,
                        pendingIntent);
            }
        }
    }

    @Override
    public void onNotificationRemoved(StatusBarNotification sbn) {
        // Nothing to do
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        LogUtil.v("NotificationPiggyback service destroyed");
    }
}
