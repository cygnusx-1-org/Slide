package me.edgan.redditslide.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;

import android.app.Application;
import android.content.Context;
import android.graphics.Bitmap;
import android.util.LruCache;
import android.view.View;
import android.widget.ImageView;
import androidx.test.core.app.ApplicationProvider;
import java.lang.reflect.Field;
import me.edgan.redditslide.SettingValues;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/**
 * Which kind of "not showing" a comment's video thumbnail uses while its frame is being read.
 *
 * <p>Comment media is laid out as pre-sized blocks so that a frame arriving late never reflows the
 * comment underneath it. That only holds if the placeholder keeps its slot: {@code INVISIBLE}
 * leaves the block measured and {@code GONE} collapses it, taking every line below it up the
 * screen the moment a read starts.
 *
 * <p>The distinction is invisible to the rest of the suite -- no test in the repository asserted
 * {@code View.INVISIBLE} anywhere, and swapping this one for {@code GONE} left every test green.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33, application = Application.class)
public class CommentVideoPreviewVisibilityTest {

    private static final String PLAYER_URL =
            "https://www.reddit.com/link/abc123/video/def456/player";

    private Context context;
    private boolean noImagesWas;
    private boolean lowResAlwaysWas;

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
        noImagesWas = SettingValues.noImages;
        lowResAlwaysWas = SettingValues.lowResAlways;
        // "Don't load any images" with data saving on: load stops right after it has decided what
        // the placeholder looks like, so this exercises that decision without a network read.
        SettingValues.noImages = true;
        SettingValues.lowResAlways = true;
        cache().evictAll();
    }

    @After
    public void tearDown() {
        SettingValues.noImages = noImagesWas;
        SettingValues.lowResAlways = lowResAlwaysWas;
        // The frame cache is a process-wide static shared with every later test in this JVM.
        cache().evictAll();
    }

    @SuppressWarnings("unchecked")
    private static LruCache<String, Bitmap> cache() {
        try {
            Field f = CommentVideoPreview.class.getDeclaredField("CACHE");
            f.setAccessible(true);
            return (LruCache<String, Bitmap>) f.get(null);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException("Failed to reach CommentVideoPreview.CACHE", e);
        }
    }

    private ImageView shownImageView() {
        ImageView view = new ImageView(context);
        view.setVisibility(View.VISIBLE);
        return view;
    }

    /** The whole point: the slot stays measured while the frame is on its way. */
    @Test
    public void aFrameThatIsNotInMemoryLeavesThePlaceholderInvisibleNotGone() {
        ImageView view = shownImageView();

        CommentVideoPreview.load(view, PLAYER_URL);

        assertEquals(
                "a pending frame keeps its block measured", View.INVISIBLE, view.getVisibility());
        assertNotEquals(
                "GONE would collapse the block and shift the comment below it",
                View.GONE,
                view.getVisibility());
    }

    /** Nothing stale is left on screen underneath the placeholder. */
    @Test
    public void aPendingFrameClearsWhateverTheRecycledViewWasShowing() {
        ImageView view = shownImageView();
        view.setImageBitmap(Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888));

        CommentVideoPreview.load(view, PLAYER_URL);

        assertNull("a recycled view must not keep the previous card's frame", view.getDrawable());
    }

    /** A frame already in memory is shown immediately, with no invisible flicker in between. */
    @Test
    public void aFrameAlreadyInMemoryIsShownStraightAway() {
        ImageView view = shownImageView();
        view.setVisibility(View.INVISIBLE);
        cache().put(CommentVideoUtil.normalizeUrl(PLAYER_URL),
                Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888));

        CommentVideoPreview.load(view, PLAYER_URL);

        assertEquals(View.VISIBLE, view.getVisibility());
    }

    /**
     * The view is tagged with the normalised url, which is how a late frame decides whether the
     * view it is holding is still showing the video it was read for.
     */
    @Test
    public void theViewIsTaggedWithTheNormalisedUrl() {
        ImageView view = shownImageView();

        CommentVideoPreview.load(view, PLAYER_URL);

        assertEquals(
                "the same video with and without www. is one asset",
                CommentVideoUtil.normalizeUrl(PLAYER_URL),
                view.getTag());
        assertNotEquals("the raw url is not the cache key", PLAYER_URL, view.getTag());
    }
}
