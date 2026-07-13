package me.edgan.redditslide.util;

import android.app.Activity;
import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.FrameLayout;
import android.widget.TextView;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import com.google.android.material.snackbar.Snackbar;
import java.lang.ref.WeakReference;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import me.edgan.redditslide.Activities.Login;
import me.edgan.redditslide.Activities.Reauthenticate;
import me.edgan.redditslide.Authentication;
import me.edgan.redditslide.R;
import me.edgan.redditslide.Reddit;

/**
 * Surfaces an app-wide "Reauthenticating…" snackbar while a network token refresh is in flight, and
 * notifies listeners once reauth completes so already-visible UI can rebind. The comment page's
 * reply/vote/save buttons are gated on {@link Authentication#isLoggedIn}, which is set false at
 * launch and only flipped true asynchronously after the OAuth refresh finishes; a page bound during
 * that window keeps the buttons hidden until it is told to rebind (see {@link Listener}).
 *
 * <p>A snackbar only appears if reauth is still running {@link #SHOW_DELAY_MS} after it started, so
 * the fast local-token path (which finishes in milliseconds) never flashes one. If it is still
 * running {@link #FAILED_DELAY_MS} after starting, the snackbar switches to a failure message with a
 * Retry action.
 */
public class ReauthNotifier {

    private ReauthNotifier() {}

    // Invariant: SHOW_DELAY_MS < FAILED_DELAY_MS. scheduleForCurrentActivity() relies on this — if
    // the show delay were >= the fail delay, the failure runnable would be posted with a negative
    // delay (firing immediately) and the "Reauthenticating…" state would be skipped entirely.

    /** Delay before the "Reauthenticating…" snackbar appears. */
    private static final long SHOW_DELAY_MS = 10000;

    /** Delay before the snackbar switches to the "Could not reauthenticate" state. */
    private static final long FAILED_DELAY_MS = 30000;

    /** Notified on the main thread when a reauth finishes successfully. */
    public interface Listener {
        void onReauthComplete();
    }

    private static final AtomicInteger inProgress = new AtomicInteger(0);
    private static final CopyOnWriteArrayList<Listener> listeners = new CopyOnWriteArrayList<>();
    private static final Handler handler = new Handler(Looper.getMainLooper());

    private static WeakReference<Activity> currentActivity = new WeakReference<>(null);
    private static Snackbar currentSnackbar;
    private static long startTime;

    // reauthFailed: a reauth finished/timed-out unsuccessfully, so the failure bar is the active
    // state (until Retry or a new reauth). failureDismissed: the user swiped that bar away for the
    // current failure episode, so keep it hidden. Both main-thread only.
    private static boolean reauthFailed;
    private static boolean failureDismissed;

    private static final Runnable showRunnable = () -> showSnackbar(false);
    private static final Runnable failedRunnable =
            () -> {
                reauthFailed = true;
                showSnackbar(true);
            };

    public static void addListener(final Listener l) {
        if (l != null && !listeners.contains(l)) listeners.add(l);
    }

    public static void removeListener(final Listener l) {
        listeners.remove(l);
    }

    /** Called when a reauth AsyncTask begins. */
    public static void onStarted() {
        runOnMain(
                () -> {
                    if (inProgress.getAndIncrement() == 0) {
                        reauthFailed = false;
                        failureDismissed = false;
                        startTime = System.currentTimeMillis();
                        scheduleForCurrentActivity();
                    }
                });
    }

    /** Called when a reauth AsyncTask finishes; {@code success} is whether we ended up authed. */
    public static void onFinished(final boolean success) {
        runOnMain(
                () -> {
                    if (inProgress.get() > 0 && inProgress.decrementAndGet() == 0) {
                        if (success) {
                            reauthFailed = false;
                            failureDismissed = false;
                            cancelAndDismiss();
                            for (Listener l : listeners) {
                                try {
                                    l.onReauthComplete();
                                } catch (Exception ignored) {
                                }
                            }
                        } else {
                            // Reauth finished but we are still not authed. Surface the failure now
                            // instead of waiting for the 30s timer — a fast failure never reaches
                            // it, which would leave the buttons silently hidden (issue #295).
                            reauthFailed = true;
                            handler.removeCallbacks(showRunnable);
                            handler.removeCallbacks(failedRunnable);
                            showSnackbar(true);
                        }
                    }
                });
    }

    /** Track the foreground activity and (re)show the snackbar on it if one is pending. */
    public static void attach(final Activity activity) {
        if (isLoginView(activity)) {
            // The login/reauthenticate screens already handle authentication directly, so don't
            // surface the reauth snackbar over them. Untarget this screen and drop any bar/timers;
            // state (inProgress/reauthFailed) is preserved so the bar returns on the next normal
            // activity if reauth is still outstanding.
            currentActivity.clear();
            cancelAndDismiss();
            return;
        }
        currentActivity = new WeakReference<>(activity);
        if (inProgress.get() > 0 || reauthFailed) {
            scheduleForCurrentActivity();
        }
    }

    /** The login/reauthenticate screens, over which the reauth snackbar should stay hidden. */
    private static boolean isLoginView(final Activity activity) {
        return activity instanceof Login || activity instanceof Reauthenticate;
    }

    /** Stop targeting a paused activity; its window is going away with any snackbar on it. */
    public static void detach(final Activity activity) {
        if (currentActivity.get() == activity) {
            currentActivity.clear();
        }
        currentSnackbar = null;
        // Drop pending timers while backgrounded; attach() reschedules them from elapsed time.
        handler.removeCallbacks(showRunnable);
        handler.removeCallbacks(failedRunnable);
    }

    private static void scheduleForCurrentActivity() {
        handler.removeCallbacks(showRunnable);
        handler.removeCallbacks(failedRunnable);
        if (reauthFailed) {
            showSnackbar(true);
            return;
        }
        if (inProgress.get() == 0) {
            // Not in progress and not in the failed state → nothing to show.
            return;
        }
        final long elapsed = System.currentTimeMillis() - startTime;
        if (elapsed >= FAILED_DELAY_MS) {
            reauthFailed = true;
            showSnackbar(true);
        } else if (elapsed >= SHOW_DELAY_MS) {
            showSnackbar(false);
            handler.postDelayed(failedRunnable, FAILED_DELAY_MS - elapsed);
        } else {
            handler.postDelayed(showRunnable, SHOW_DELAY_MS - elapsed);
            handler.postDelayed(failedRunnable, FAILED_DELAY_MS - elapsed);
        }
    }

    private static void showSnackbar(final boolean failed) {
        if (failed) {
            if (!reauthFailed || failureDismissed) return;
        } else if (inProgress.get() == 0 || reauthFailed) {
            return;
        }
        final Activity activity = currentActivity.get();
        if (activity == null || activity.isFinishing()) return;
        final View root = findAnchor(activity);
        if (root == null) return;
        try {
            if (currentSnackbar != null) currentSnackbar.dismiss();
            final Snackbar s =
                    Snackbar.make(
                            root,
                            failed ? R.string.reauth_failed : R.string.reauth_in_progress,
                            Snackbar.LENGTH_INDEFINITE);
            if (failed) {
                s.setAction(
                        R.string.btn_retry,
                        v -> {
                            s.dismiss();
                            currentSnackbar = null;
                            // Re-fire reauth and reset the cycle; if it stalls again the normal
                            // 4s/30s timers bring the snackbar back naturally.
                            reauthFailed = false;
                            failureDismissed = false;
                            startTime = System.currentTimeMillis();
                            // Use the live foreground activity, not the one captured when the bar
                            // was created (which may be finishing by the time Retry is tapped).
                            final Activity current = currentActivity.get();
                            if (Reddit.authentication != null
                                    && current != null
                                    && !current.isFinishing()) {
                                Reddit.authentication.updateToken(current);
                            }
                            scheduleForCurrentActivity();
                        });
            }
            // If the user swipes the failure bar away, keep it dismissed for this episode.
            final boolean isFailureBar = failed;
            s.addCallback(
                    new Snackbar.Callback() {
                        @Override
                        public void onDismissed(Snackbar sb, int event) {
                            if (isFailureBar && event == DISMISS_EVENT_SWIPE) {
                                failureDismissed = true;
                            }
                        }
                    });
            // Fade in rather than slide up from the bottom, so re-showing the bar on each activity
            // reads as "it was already there" instead of animating in on every navigation.
            s.setAnimationMode(Snackbar.ANIMATION_MODE_FADE);
            currentSnackbar = s;
            final View sbView = s.getView();
            styleSnackbar(sbView);
            // Make the bar compact and position it on the first layout (before the enter animation
            // settles, so there is no post-show jump).
            final ViewTreeObserver.OnGlobalLayoutListener[] holder =
                    new ViewTreeObserver.OnGlobalLayoutListener[1];
            holder[0] = () -> normalizeBar(sbView, holder[0]);
            sbView.getViewTreeObserver().addOnGlobalLayoutListener(holder[0]);
            s.show();
        } catch (Exception ignored) {
        }
    }

    /**
     * Fraction of the navigation-bar inset used as the bar's bottom margin. The full inset (1.0)
     * leaves a gap above the visible gesture handle, which sits lower than the inset's top edge;
     * half (0.5) overlaps the handle. ~0.7 lands the bar's bottom just on top of the handle.
     */
    private static final float BOTTOM_CLEARANCE_FRACTION = 0.97f;

    /**
     * Make the bar compact and rest it on top of the visible gesture handle. On the content
     * FrameLayout (which spans edge-to-edge) Material pads the bar with the full window insets,
     * wrapping the text in dead space. Strip that, wrap the text, and set a bottom margin of
     * {@link #BOTTOM_CLEARANCE_FRACTION} of the nav-bar inset so the bar sits just above the handle
     * (no gap, no overlap). Done at layout time (before the enter animation settles) so there is no
     * post-show jump, and because the content frame is edge-to-edge nothing clips the bar's bottom.
     */
    private static void normalizeBar(
            final View sbView, final ViewTreeObserver.OnGlobalLayoutListener self) {
        try {
            int navInset = 0;
            final WindowInsetsCompat wi = ViewCompat.getRootWindowInsets(sbView);
            if (wi != null) {
                navInset = wi.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom;
            }
            ViewCompat.setOnApplyWindowInsetsListener(sbView, null);
            sbView.setPadding(0, 0, 0, 0);
            final ViewGroup.LayoutParams lp = sbView.getLayoutParams();
            if (lp != null) {
                lp.height = ViewGroup.LayoutParams.WRAP_CONTENT;
                if (lp instanceof ViewGroup.MarginLayoutParams) {
                    ((ViewGroup.MarginLayoutParams) lp).bottomMargin =
                            Math.round(navInset * BOTTOM_CLEARANCE_FRACTION);
                }
                if (lp instanceof FrameLayout.LayoutParams) {
                    ((FrameLayout.LayoutParams) lp).gravity = Gravity.BOTTOM;
                }
                sbView.setLayoutParams(lp);
            }
        } catch (Exception ignored) {
        } finally {
            sbView.getViewTreeObserver().removeOnGlobalLayoutListener(self);
        }
    }

    /**
     * Anchor the snackbar to the content {@link FrameLayout} — it spans edge-to-edge on every
     * screen, so the bar can be positioned anywhere above the nav bar without being clipped. (A
     * CoordinatorLayout child would be clipped at the nav-bar inset.)
     */
    private static View findAnchor(final Activity activity) {
        return activity.findViewById(android.R.id.content);
    }

    /**
     * Theme the snackbar to match the app's cards: {@code card_background} fill, {@code fontColor}
     * text (so it stays readable in light and dark themes, unlike a hard-coded white), and a thin
     * {@code tintColor} outline so its bounds are visible against an AMOLED-black background.
     */
    private static void styleSnackbar(final View sbView) {
        try {
            final Context ctx = sbView.getContext();
            final TypedValue cardBg = new TypedValue();
            final TypedValue tint = new TypedValue();
            final TypedValue font = new TypedValue();
            final boolean hasCard =
                    ctx.getTheme().resolveAttribute(R.attr.card_background, cardBg, true);
            final boolean hasTint = ctx.getTheme().resolveAttribute(R.attr.tintColor, tint, true);
            final boolean hasFont = ctx.getTheme().resolveAttribute(R.attr.fontColor, font, true);
            final float density = ctx.getResources().getDisplayMetrics().density;
            if (hasCard) {
                final int strokeColor =
                        hasTint ? ((tint.data & 0x00FFFFFF) | 0x66000000) : 0x66888888;
                final GradientDrawable bg = new GradientDrawable();
                bg.setColor(cardBg.data);
                bg.setCornerRadius(2 * density);
                bg.setStroke(Math.max(1, Math.round(1.5f * density)), strokeColor);
                sbView.setBackground(bg);
            }
            if (hasFont) {
                final TextView tv =
                        sbView.findViewById(com.google.android.material.R.id.snackbar_text);
                if (tv != null) tv.setTextColor(font.data);
            }
        } catch (Exception ignored) {
        }
    }

    private static void cancelAndDismiss() {
        handler.removeCallbacks(showRunnable);
        handler.removeCallbacks(failedRunnable);
        if (currentSnackbar != null) {
            try {
                currentSnackbar.dismiss();
            } catch (Exception ignored) {
            }
            currentSnackbar = null;
        }
    }

    private static void runOnMain(final Runnable r) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            r.run();
        } else {
            handler.post(r);
        }
    }
}
