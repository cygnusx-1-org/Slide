package me.edgan.redditslide.util;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.widget.Toast;
import java.util.Locale;
import me.edgan.redditslide.R;

/**
 * Reads post and comment text out loud using the device's text-to-speech engine. A single engine
 * instance is kept alive for the app's lifetime and reused across calls; tapping "Read aloud" while
 * something is already being spoken stops the current playback (acting as a toggle).
 *
 * <p>Playback is stopped automatically when the user leaves the screen that started it: an
 * {@link Application.ActivityLifecycleCallbacks} hook calls {@link #stop()} in {@code
 * onActivityStopped}, except on configuration changes (rotation), so navigating away (back press,
 * opening the post, switching apps) halts the speech while rotating the device does not. The bottom
 * sheet and copy dialogs are plain dialogs that don't stop their host activity, so opening them
 * does not interrupt playback.
 */
public class ReadAloudUtil {

    private static final String UTTERANCE_ID = "slide_read_aloud";

    private static TextToSpeech tts;
    private static boolean ready;
    private static String pending;
    private static boolean lifecycleRegistered;

    /**
     * Speaks {@code text} aloud, lazily initializing the TTS engine on first use. If the engine is
     * already speaking, this call stops it instead (so the menu item toggles playback).
     */
    public static void readAloud(final Context context, final String text) {
        if (text == null || text.trim().isEmpty()) {
            return;
        }

        // Use the application context so the engine outlives the Activity that opened the menu.
        final Context app = context.getApplicationContext();

        // Make sure playback halts when the user navigates away from the current screen.
        registerLifecycleStop(app);

        if (tts != null && ready) {
            if (tts.isSpeaking()) {
                // Second tap while speaking: stop.
                tts.stop();
            } else {
                speak(text);
            }
            return;
        }

        // Engine not ready yet; remember the text and speak once initialization completes.
        pending = text;
        if (tts == null) {
            tts =
                    new TextToSpeech(
                            app,
                            status -> {
                                if (status == TextToSpeech.SUCCESS) {
                                    ready = true;
                                    tts.setLanguage(Locale.getDefault());
                                    if (pending != null) {
                                        speak(pending);
                                        pending = null;
                                    }
                                } else {
                                    ready = false;
                                    Toast.makeText(
                                                    app,
                                                    R.string.read_aloud_unavailable,
                                                    Toast.LENGTH_SHORT)
                                            .show();
                                }
                            });
        }
    }

    private static void speak(final String text) {
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, UTTERANCE_ID);
    }

    /** Stops any in-progress playback. Safe to call when nothing is speaking. */
    public static void stop() {
        // Also drop any text queued before the engine finished initializing, so a start that is
        // still pending when the user leaves doesn't begin speaking after the fact.
        pending = null;
        if (tts != null && ready) {
            tts.stop();
        }
    }

    /**
     * Registers a one-time hook (per process) that stops playback when the user leaves the screen
     * that started reading. We stop on {@code onActivityStopped} (not pause) and skip
     * configuration changes, so rotating the device does not interrupt playback; dialogs don't stop
     * their host activity either, so opening the overflow sheet or copy dialog won't trigger this.
     */
    private static void registerLifecycleStop(final Context app) {
        if (lifecycleRegistered || !(app instanceof Application)) {
            return;
        }
        lifecycleRegistered = true;
        ((Application) app)
                .registerActivityLifecycleCallbacks(
                        new Application.ActivityLifecycleCallbacks() {
                            @Override
                            public void onActivityStopped(Activity activity) {
                                // Don't stop for a rotation/config-change teardown-recreate.
                                if (!activity.isChangingConfigurations()) {
                                    stop();
                                }
                            }

                            @Override
                            public void onActivityCreated(
                                    Activity activity, Bundle savedInstanceState) {}

                            @Override
                            public void onActivityStarted(Activity activity) {}

                            @Override
                            public void onActivityResumed(Activity activity) {}

                            @Override
                            public void onActivityPaused(Activity activity) {}

                            @Override
                            public void onActivitySaveInstanceState(
                                    Activity activity, Bundle outState) {}

                            @Override
                            public void onActivityDestroyed(Activity activity) {}
                        });
    }
}
