package me.edgan.redditslide.test;

import android.graphics.Bitmap;
import com.github.takahirom.roborazzi.RoborazziOptions;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

/**
 * Writes a bitmap to a Roborazzi golden.
 *
 * <p>Shared by the screenshot suites rather than copied into each: the call below cannot be written
 * as a normal method call, so every copy of it would be a copy of the MethodHandle plumbing too.
 */
public final class RoborazziCapture {

    private RoborazziCapture() {}

    private static final RoborazziOptions SCREENSHOT_OPTIONS =
            new RoborazziOptions(
                    // The Bitmap capture path only supports Screenshot capture (the Dump type is
                    // View-only), so pass it explicitly.
                    new RoborazziOptions.CaptureType.Screenshot(),
                    new RoborazziOptions.ReportOptions(),
                    new RoborazziOptions.CompareOptions(),
                    new RoborazziOptions.RecordOptions());

    /**
     * Calls {@code RoborazziKt.captureRoboImage(Bitmap, String, RoborazziOptions)} through a
     * MethodHandle. A direct Java call cannot compile: javac must load every overload of
     * {@code captureRoboImage} to pick one, and the sibling overloads reference Espresso/Compose
     * types that are not on this Java-only test classpath. findStatic resolves just the one
     * descriptor we need.
     */
    public static void captureRoboImage(final Bitmap bitmap, final String filePath) {
        try {
            MethodHandle handle =
                    MethodHandles.lookup()
                            .findStatic(
                                    Class.forName("com.github.takahirom.roborazzi.RoborazziKt"),
                                    "captureRoboImage",
                                    MethodType.methodType(
                                            void.class,
                                            Bitmap.class,
                                            String.class,
                                            RoborazziOptions.class));
            handle.invoke(bitmap, filePath, SCREENSHOT_OPTIONS);
        } catch (RuntimeException | Error e) {
            throw e;
        } catch (Throwable t) {
            throw new AssertionError("captureRoboImage failed for " + filePath, t);
        }
    }
}
