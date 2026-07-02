package me.edgan.redditslide.test;

import android.app.Application;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Shader;
import android.graphics.drawable.BitmapDrawable;
import android.os.Looper;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import com.github.takahirom.roborazzi.RoborazziOptions;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import me.edgan.redditslide.R;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.ParameterizedRobolectricTestRunner;
import org.robolectric.Robolectric;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

/**
 * Screenshot tests that render the main submission layouts at every smallest-width-dp
 * configuration we care about, in a dark and a light theme, without needing an emulator.
 * Robolectric reconfigures the in-process display per case and Roborazzi captures a PNG that is
 * diffed against a committed golden in src/test/screenshots/.
 *
 * <p>Workflow:
 *
 * <pre>
 *   ./gradlew recordRoborazziWithGPlayDebug    # write/update goldens (commit them)
 *   ./gradlew verifyRoborazziWithGPlayDebug    # fail on any pixel diff
 *   ./gradlew compareRoborazziWithGPlayDebug   # write *_compare.png diffs without failing
 * </pre>
 *
 * <p>Each case is parameterized as (layout x theme x smallest-width dp) so a single layout that
 * breaks at one width/theme fails in isolation with a self-describing name, e.g.
 * "largecard_dark_sw411dp". Slide themes colour views through XML theme attributes, so inflating
 * against a themed Activity is enough — placeholder text/images are injected only where adapters
 * would normally bind content.
 */
@RunWith(ParameterizedRobolectricTestRunner.class)
// Stock Application: we only need a themed Activity to inflate against, not the real app's
// singletons. SDK 33 matches the continuum snapshot suite so goldens stay comparable.
@Config(sdk = 33, application = Application.class)
// Native graphics so view.draw() actually rasterises text.
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class RoborazziLayoutTest {

    /** Long enough to wrap onto multiple lines on narrow widths. */
    private static final String SAMPLE_TEXT =
            "Sample content long enough to wrap across multiple lines on narrower screens";

    /** Smallest-width dp buckets: common phones (411/443/448), this dev's phone (527), tablets. */
    private static final int[] SMALLEST_WIDTHS_DP = {411, 443, 448, 527, 600, 934};

    private static final Object[][] THEMES = {
        {"dark", R.style.Theme_DARK},
        {"light", R.style.Theme_LIGHT},
    };

    /** Submission layouts under test — the big card variants plus the compact list row. */
    private static Map<String, Integer> layouts() {
        Map<String, Integer> m = new LinkedHashMap<>();
        m.put("largecard", R.layout.submission_largecard);
        m.put("mediacard", R.layout.submission_mediacard);
        m.put("textcard", R.layout.submission_textcard);
        m.put("titlecard", R.layout.submission_titlecard);
        m.put("list", R.layout.submission_list);
        return m;
    }

    @ParameterizedRobolectricTestRunner.Parameters(name = "{0}_{3}_sw{2}dp")
    public static List<Object[]> cases() {
        List<Object[]> cases = new ArrayList<>();
        for (Map.Entry<String, Integer> layout : layouts().entrySet()) {
            for (Object[] theme : THEMES) {
                for (int dp : SMALLEST_WIDTHS_DP) {
                    cases.add(new Object[] {layout.getKey(), layout.getValue(), dp, theme[0], theme[1]});
                }
            }
        }
        return cases;
    }

    private final String name;
    private final int layoutRes;
    private final int swDp;
    private final String themeLabel;
    private final int themeRes;

    public RoborazziLayoutTest(
            String name, int layoutRes, int swDp, String themeLabel, int themeRes) {
        this.name = name;
        this.layoutRes = layoutRes;
        this.swDp = swDp;
        this.themeLabel = themeLabel;
        this.themeRes = themeRes;
    }

    /** Minimal AppCompat activity to host the themed inflation. */
    public static class TestActivity extends AppCompatActivity {}

    @Before
    public void configureScreen() {
        // Portrait at the target smallest-width dp, xxhdpi so text renders at realistic pixel
        // sizes. Qualifiers are in Android's canonical order; h stays larger than every swDp so
        // portrait never clamps the width down.
        RuntimeEnvironment.setQualifiers("+sw" + swDp + "dp-w" + swDp + "dp-h1600dp-port-xxhdpi");
    }

    @Test
    public void capture() {
        View view = render(layoutRes);
        // Draw the laid-out view onto a bitmap ourselves (the View overloads render transparent
        // under Robolectric) and erase with the theme's window background first so the golden has
        // the real page background behind the card.
        Bitmap bitmap =
                Bitmap.createBitmap(
                        Math.max(view.getWidth(), 1),
                        Math.max(view.getHeight(), 1),
                        Bitmap.Config.ARGB_8888);
        bitmap.eraseColor(pageBackground);
        view.draw(new Canvas(bitmap));
        captureRoboImage(
                bitmap,
                "src/test/screenshots/" + name + "_" + themeLabel + "_sw" + swDp + "dp.png");
    }

    /**
     * Calls {@code RoborazziKt.captureRoboImage(Bitmap, String, RoborazziOptions)} through a
     * MethodHandle. A direct Java call cannot compile: javac must load every overload of
     * {@code captureRoboImage} to pick one, and the sibling overloads reference Espresso/Compose
     * types that are not on this Java-only test classpath. findStatic resolves just the one
     * descriptor we need.
     */
    private static void captureRoboImage(Bitmap bitmap, String filePath) {
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

    private int pageBackground = Color.WHITE;

    /**
     * Inflate against a real, themed Activity and run a genuine layout/draw traversal so the item
     * is realised for native-graphics rendering, then inject placeholder content and reflow at the
     * configured display width.
     */
    private View render(int layoutRes) {
        ActivityController<TestActivity> controller = Robolectric.buildActivity(TestActivity.class);
        TestActivity activity = controller.get();
        activity.setTheme(themeRes);
        // BaseActivity layers the font-size overlays onto the theme at runtime; the submission
        // layouts reference their attrs (?attr/font_cardtitle etc.), so mirror it with the
        // default (Medium) styles before inflation.
        activity.getTheme().applyStyle(R.style.FontStyle_MediumPost, true);
        activity.getTheme().applyStyle(R.style.FontStyle_MediumComment, true);
        controller.create();

        TypedValue tv = new TypedValue();
        if (activity.getTheme().resolveAttribute(android.R.attr.windowBackground, tv, true)
                && tv.type >= TypedValue.TYPE_FIRST_COLOR_INT
                && tv.type <= TypedValue.TYPE_LAST_COLOR_INT) {
            pageBackground = tv.data;
        }

        View view =
                LayoutInflater.from(activity).inflate(layoutRes, new FrameLayout(activity), false);
        activity.setContentView(
                view,
                new ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        // start→resume→visible attaches the decor and runs a real measure/layout/draw pass; drain
        // the looper so it completes and TextViews get their real widths before content injection.
        controller.start().resume().visible();
        Shadows.shadowOf(Looper.getMainLooper()).idle();

        fillPlaceholders(view);

        int widthPx = activity.getResources().getDisplayMetrics().widthPixels;
        view.measure(
                View.MeasureSpec.makeMeasureSpec(widthPx, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        view.layout(0, 0, view.getMeasuredWidth(), view.getMeasuredHeight());
        return view;
    }

    /**
     * Inject generic placeholder content where the adapters would bind: wide empty TextViews get
     * sample copy long enough to wrap differently per width (the dp-sensitive behaviour under
     * test); empty ImageViews get a generated sample image. Narrow labels/counters stay empty so
     * they don't balloon vertically. No per-layout view-id coupling, so layout renames survive.
     */
    private void fillPlaceholders(View view) {
        if (view instanceof TextView) {
            TextView text = (TextView) view;
            double minFillWidth = view.getResources().getDisplayMetrics().widthPixels * 0.45;
            if ((text.getText() == null || text.getText().length() == 0)
                    && text.getWidth() >= minFillWidth) {
                text.setText(SAMPLE_TEXT);
            }
        } else if (view instanceof ImageView) {
            ImageView image = (ImageView) view;
            if (image.getDrawable() == null) {
                image.setImageDrawable(
                        new BitmapDrawable(view.getResources(), makeSampleBitmap()));
            }
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                fillPlaceholders(group.getChildAt(i));
            }
        }
    }

    /** A gradient with a couple of shapes so image slots read as a photo, not a flat fill. */
    private static Bitmap makeSampleBitmap() {
        int size = 240;
        Bitmap bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bmp);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setShader(
                new LinearGradient(
                        0f,
                        0f,
                        size,
                        size,
                        new int[] {
                            Color.rgb(0x3F, 0x51, 0xB5),
                            Color.rgb(0x00, 0xBC, 0xD4),
                            Color.rgb(0xFF, 0x98, 0x00)
                        },
                        null,
                        Shader.TileMode.CLAMP));
        canvas.drawPaint(paint);
        paint.setShader(null);
        paint.setColor(Color.argb(0x88, 0xFF, 0xFF, 0xFF));
        canvas.drawCircle(size * 0.30f, size * 0.30f, size * 0.15f, paint);
        paint.setColor(Color.argb(0x66, 0x00, 0x00, 0x00));
        canvas.drawRect(0f, size * 0.72f, size, size, paint);
        return bmp;
    }

    private static final RoborazziOptions SCREENSHOT_OPTIONS =
            new RoborazziOptions(
                    // The Bitmap capture path only supports Screenshot capture (the Dump type is
                    // View-only), so pass it explicitly.
                    new RoborazziOptions.CaptureType.Screenshot(),
                    new RoborazziOptions.ReportOptions(),
                    new RoborazziOptions.CompareOptions(),
                    new RoborazziOptions.RecordOptions());
}
