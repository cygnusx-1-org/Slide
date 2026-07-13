package me.edgan.redditslide.Notifications;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;

import androidx.core.app.AlarmManagerCompat;
import androidx.core.content.ContextCompat;

import java.util.Calendar;
import java.util.concurrent.atomic.AtomicBoolean;

import me.edgan.redditslide.Authentication;
import me.edgan.redditslide.Reddit;

/**
 * Keeps the logged-in OAuth token warm in the background. An {@link AlarmManager} alarm fires around
 * the stored token expiry, does a real (network) token refresh off the main thread — silently,
 * without the reauth snackbar — via {@link Authentication#updateToken(Context, boolean, Runnable)},
 * then reschedules itself for the next cycle. This means re-opening the app usually finds a fresh
 * token, instead of relying on the resume-time refresh, which occasionally fails and otherwise only
 * retries on the next manual re-open.
 */
public class TokenRefreshReceiver extends BroadcastReceiver {

    // Never schedule the next refresh sooner than this. It only applies when the token is already
    // expired at scheduling time (e.g. a refresh just failed): the alarm then retries on this
    // cadence instead of firing in a tight loop. The normal case fires at the future expiry.
    private static final long MIN_DELAY_MS = 10L * 60 * 1000;

    // Upper bound on how long the goAsync() lease is held. The lease is normally released the moment
    // the refresh completes; this cap guarantees we release within the broadcast window even if the
    // refresh queues behind another reauth on the shared executor or never reports back, so a
    // backed-up executor can't ANR/kill the process. Kept comfortably under the ~60s background
    // broadcast window so a slow-but-succeeding refresh (OkHttp timeouts, queueing) still has room.
    private static final long FINISH_TIMEOUT_MS = 45_000;

    @Override
    public void onReceive(Context context, Intent intent) {
        final Context app = context.getApplicationContext();
        // Keep the process alive until the async refresh finishes; without this the process can be
        // killed once onReceive returns, dropping the in-flight network refresh.
        final PendingResult pending = goAsync();
        final AtomicBoolean finished = new AtomicBoolean();
        final Handler handler = new Handler(Looper.getMainLooper());
        final Runnable done =
                new Runnable() {
                    @Override
                    public void run() {
                        // Release exactly once, whether that is the refresh completing or the cap.
                        if (finished.compareAndSet(false, true)) {
                            // Drop the pending cap message if we finished early so it isn't retained.
                            handler.removeCallbacks(this);
                            try {
                                pending.finish();
                            } catch (Exception ignored) {
                            }
                        }
                    }
                };
        // Safety cap so a stalled/queued refresh can never hold the lease past the broadcast window.
        handler.postDelayed(done, FINISH_TIMEOUT_MS);

        if (Reddit.authentication != null) {
            Reddit.authentication.updateToken(app, false, done);
        } else {
            // Auth isn't built yet — should not happen, since Application.onCreate sets it before any
            // receiver runs. Nothing to refresh silently here, so just release the lease; app init or
            // the next resume will establish auth.
            done.run();
        }
        // Reschedule for the next cycle; a successful refresh bumps the stored expiry, and
        // UpdateToken.onPostExecute realigns this alarm once the refresh has actually run.
        schedule(app);
    }

    /**
     * (Re)schedule the next background token refresh at the stored token expiry. No-op unless a user
     * refresh token is stored, so a logged-out/guest session is left alone.
     */
    public static void schedule(final Context context) {
        final AlarmManager manager = ContextCompat.getSystemService(context, AlarmManager.class);
        if (manager == null
                || Authentication.authentication == null
                || Authentication.authentication.getString("lasttoken", "").isEmpty()) {
            return;
        }

        final long now = Calendar.getInstance().getTimeInMillis();
        final long expires = Authentication.authentication.getLong("expires", 0);
        // Fire at expiry, not before it: the pre-expiry refresh path reuses cached credentials and
        // would not extend the token, whereas an expired token forces a real network refresh.
        final long triggerAt = Math.max(expires, now + MIN_DELAY_MS);

        final Intent intent = new Intent(context, TokenRefreshReceiver.class);
        final PendingIntent pendingIntent =
                PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_IMMUTABLE);
        AlarmManagerCompat.setAndAllowWhileIdle(
                manager, AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent);
    }
}
