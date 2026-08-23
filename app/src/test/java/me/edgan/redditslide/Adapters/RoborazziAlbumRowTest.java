package me.edgan.redditslide.Adapters;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.os.Looper;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import me.edgan.redditslide.Activities.GalleryImage;
import me.edgan.redditslide.R;
import me.edgan.redditslide.Views.ExoVideoView;
import me.edgan.redditslide.test.RoborazziCapture;
import me.edgan.redditslide.util.GifUtils;
import org.junit.After;
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
 * Screenshot test for the video row of the vertical album/gallery lists — the one layout
 * {@link me.edgan.redditslide.test.RoborazziLayoutTest} cannot cover, because inflating it says
 * nothing about how it appears.
 *
 * <p>submission_gifcard_album is {@code match_parent} tall, since it doubles as a full-screen pager
 * page; as a list row it gets an explicit height at bind time, from the media's aspect ratio via
 * {@link me.edgan.redditslide.util.LayoutUtils#applyAlbumRowSize}. A static inflate would therefore
 * pin a screen-tall row the list never shows. So the rows here are bound by a real
 * {@link RedditGalleryView} inside a real RecyclerView, and what is captured is the list.
 *
 * <p>Two entries: one plain, one with the per-image caption that sits above the video and is part of
 * the row's height budget. Together they pin the height derived from the aspect ratio, the outer gap
 * between rows, the button bar staying hidden, the play button centred on the video rather than on
 * the whole row, and the caption band.
 *
 * <p>The load is stubbed out at the executor seam, so the rows render in the state a user first sees
 * — spinner up, play button over black — with nothing going near the network.
 *
 * <pre>
 *   ./gradlew recordRoborazziWithGPlayDebug    # goldens are written into the source tree
 *   ./gradlew verifyRoborazziWithGPlayDebug
 * </pre>
 */
@RunWith(ParameterizedRobolectricTestRunner.class)
@Config(sdk = 33, application = Application.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class RoborazziAlbumRowTest {

    /** The same buckets and themes as the submission-card goldens, so the sets stay comparable. */
    private static final int[] SMALLEST_WIDTHS_DP = {411, 443, 448, 527, 600, 934};

    private static final Object[][] THEMES = {
        {"dark", R.style.Theme_DARK},
        {"light", R.style.Theme_LIGHT},
    };

    @ParameterizedRobolectricTestRunner.Parameters(name = "{1}_sw{0}dp")
    public static List<Object[]> cases() {
        final List<Object[]> cases = new ArrayList<>();
        for (Object[] theme : THEMES) {
            for (int dp : SMALLEST_WIDTHS_DP) {
                cases.add(new Object[] {dp, theme[0], theme[1]});
            }
        }
        return cases;
    }

    private final int swDp;
    private final String themeLabel;
    private final int themeRes;

    public RoborazziAlbumRowTest(int swDp, String themeLabel, int themeRes) {
        this.swDp = swDp;
        this.themeLabel = themeLabel;
        this.themeRes = themeRes;
    }

    /** The list under capture, held so it can be torn down when the case fails as well as passes. */
    @Nullable private RecyclerView list;

    @Before
    public void configureScreen() {
        RuntimeEnvironment.setQualifiers("+sw" + swDp + "dp-w" + swDp + "dp-h1600dp-port-xxhdpi");
    }

    /**
     * Detaches the rows so each ExoVideoView releases its player.
     *
     * <p>Every row builds a SimpleExoPlayer in its constructor and ExoVideoView releases one in
     * exactly one place — {@code stop()}, from {@code onDetachedFromWindow} — so a list left
     * standing in the activity leaks a player, and its playback thread, per row. Twelve
     * parameterised cases share one JVM and this suite has already exhausted the default test heap
     * once, so a case that fails has to clean up as thoroughly as one that passes.
     */
    @After
    public void releaseRowPlayers() {
        if (list == null) {
            return;
        }
        final List<ExoVideoView> players = new ArrayList<>();
        for (int i = 0; i < list.getChildCount(); i++) {
            players.add(list.getChildAt(i).findViewById(R.id.gif));
        }
        // Recycles every child through a real detach, unlike simply dropping the reference.
        list.setAdapter(null);
        list = null;
        for (ExoVideoView player : players) {
            assertFalse("a row kept its player after the list detached it", player.hasPlayer());
        }
    }

    @Test
    public void capture() {
        final ActivityController<TestActivity> controller =
                Robolectric.buildActivity(TestActivity.class);
        final TestActivity activity = controller.get();
        activity.setTheme(themeRes);
        controller.create();

        list = new RecyclerView(activity);
        list.setLayoutManager(new LinearLayoutManager(activity));
        activity.setContentView(
                list,
                new ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        controller.start().resume().visible();

        list.setAdapter(new StubbedGalleryView(activity, entries()));
        Shadows.shadowOf(Looper.getMainLooper()).idle();

        // Full display height, because that is the cap applyAlbumRowSize measures a row against; the
        // capture below then keeps only the part the rows actually occupy.
        final int widthPx = activity.getResources().getDisplayMetrics().widthPixels;
        final int heightPx = activity.getResources().getDisplayMetrics().heightPixels;
        list.measure(
                View.MeasureSpec.makeMeasureSpec(widthPx, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(heightPx, View.MeasureSpec.EXACTLY));
        list.layout(0, 0, widthPx, heightPx);

        final int contentHeight = contentHeight(list, heightPx);
        assertTrue("no rows were laid out", contentHeight > 0);

        final Bitmap bitmap =
                Bitmap.createBitmap(
                        Math.max(widthPx, 1), Math.max(contentHeight, 1), Bitmap.Config.ARGB_8888);
        bitmap.eraseColor(pageBackground(activity));
        list.draw(new Canvas(bitmap));
        RoborazziCapture.captureRoboImage(
                bitmap,
                "src/test/screenshots/album_video_row_" + themeLabel + "_sw" + swDp + "dp.png");
    }

    /**
     * How far down the list the bound rows reach, so the golden is the rows rather than the rest of
     * the screen below them — a full-height capture at the widest bucket is a ~50 MB bitmap.
     */
    private static int contentHeight(final RecyclerView list, final int limit) {
        int bottom = 0;
        for (int i = 0; i < list.getChildCount(); i++) {
            final View child = list.getChildAt(i);
            final ViewGroup.MarginLayoutParams lp =
                    (ViewGroup.MarginLayoutParams) child.getLayoutParams();
            bottom = Math.max(bottom, child.getBottom() + lp.bottomMargin);
        }
        return Math.min(bottom, limit);
    }

    private static int pageBackground(final AppCompatActivity activity) {
        final TypedValue tv = new TypedValue();
        if (activity.getTheme().resolveAttribute(android.R.attr.windowBackground, tv, true)
                && tv.type >= TypedValue.TYPE_FIRST_COLOR_INT
                && tv.type <= TypedValue.TYPE_LAST_COLOR_INT) {
            return tv.data;
        }
        return Color.WHITE;
    }

    /** A 16:9 video row and a 16:9 row carrying a caption. */
    private static List<GalleryImage> entries() {
        return Arrays.asList(
                animatedEntry("https://v.redd.it/abc123/DASHPlaylist.mpd", null),
                animatedEntry(
                        "https://v.redd.it/def456/DASHPlaylist.mpd",
                        "A per-image caption, long enough to wrap onto a second line on the"
                                + " narrower widths"));
    }

    private static GalleryImage animatedEntry(
            final String url, final @Nullable String caption) {
        final ObjectNode node = JsonNodeFactory.instance.objectNode();
        final GalleryImage image = new GalleryImage(node);
        image.url = url;
        image.width = 1920;
        image.height = 1080;
        image.caption = caption;
        // What makes isAnimated() true, and so what routes this to the video row rather than the
        // still one. metadata.source stays null, so getImageUrl() falls through to the url above.
        image.metadata.animated = true;
        return image;
    }

    /**
     * The real gallery adapter with the network taken out at the seam VerticalMediaAdapter already
     * exposes for that purpose. Everything the golden is about — the row's height, its margins, the
     * hidden button bar, the play button, the caption — is decided before a load would start.
     */
    private static class StubbedGalleryView extends RedditGalleryView {
        StubbedGalleryView(final AppCompatActivity activity, final List<GalleryImage> images) {
            super(activity, images, "pics", "A submission title");
        }

        @Override
        void execute(
                final GifUtils.AsyncLoadGif load,
                final java.util.concurrent.Executor executor,
                final String url) {}
    }

    public static class TestActivity extends AppCompatActivity {}
}
