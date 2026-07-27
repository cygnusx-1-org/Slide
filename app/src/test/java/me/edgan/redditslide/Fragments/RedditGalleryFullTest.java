package me.edgan.redditslide.Fragments;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.content.Context;
import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.RecyclerView;
import androidx.test.core.app.ApplicationProvider;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.InputStream;
import java.util.List;
import me.edgan.redditslide.Activities.GalleryImage;
import me.edgan.redditslide.Adapters.RedditGalleryView;
import me.edgan.redditslide.R;
import me.edgan.redditslide.Reddit;
import me.edgan.redditslide.test.TestUtils;
import net.dean.jraw.models.Submission;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;

/**
 * Covers the one album fragment that loads nothing: a Reddit gallery is already inside its submission,
 * so this sets its adapter during onCreateView rather than from a network callback, and gates on
 * having images rather than on having a url to fetch.
 *
 * <p>It is also the fragment least reachable by hand — it needs an i.redd.it gallery post sitting in
 * a live feed, which a Shadowbox pass over a feed of Imgur albums never produces.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33, application = Application.class)
public class RedditGalleryFullTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private TestActivity activity;

    @Before
    public void setUp() {
        // The actionbar this fragment binds runs Palette.getColor over the subreddit name, which
        // dereferences Reddit.colors. It is short-circuited today only because SettingValues
        // .colorSubName defaults false and no test flips it — seed the store rather than depend on
        // that. An empty one resolves every subreddit to the default colour.
        TestUtils.seedRedditApplication();
        Reddit.colors =
                ((Context) ApplicationProvider.getApplicationContext())
                        .getSharedPreferences("COLOR", Context.MODE_PRIVATE);

        final ActivityController<TestActivity> controller =
                Robolectric.buildActivity(TestActivity.class);
        activity = controller.get();
        activity.setTheme(R.style.Theme_LIGHT);
        // @id/title sizes itself from ?attr/font_cardtitle, supplied by the font-style overlay the
        // activities apply over their theme rather than by the theme itself.
        activity.getTheme().applyStyle(R.style.FontStyle_MediumPost, true);
        activity.getTheme().applyStyle(R.style.FontStyle_MediumComment, true);
        controller.setup();
    }

    @After
    public void tearDown() {
        // Leave the globals as they were for any same-config test sharing this Robolectric sandbox.
        Reddit.colors = null;
        TestUtils.clearRedditApplication();
    }

    @Test
    public void onlyGalleryItemsWithASourceBecomeRows() throws Exception {
        final List<GalleryImage> images =
                new TestGallery().extractGalleryImages(gallerySubmission());

        // The fixture has four items: two with an "s" node, one whose metadata has none, and one with
        // no metadata entry at all. Only the first two can be shown.
        assertEquals(2, images.size());
        assertEquals("First image", images.get(0).caption);
    }

    @Test
    public void aGalleryBindsItsAdapterDuringOnCreateViewWithoutFetching() throws Exception {
        final TestGallery fragment = host(gallerySubmission());

        assertTrue(fragment.hasAlbumToShow());
        // Set by the time the view exists, not from a later callback: that is what separates this
        // subclass from the three that fetch.
        final RecyclerView list = (RecyclerView) fragment.list;
        assertNotNull(list.getAdapter());
        assertTrue(list.getAdapter() instanceof RedditGalleryView);
        // One row per image and no leading spacer, since this host has no toolbar over the list.
        assertEquals(fragment.images.size(), list.getAdapter().getItemCount());
    }

    @Test
    public void aGalleryWithNoUrlStillBinds() throws Exception {
        // The whole point of gating on images rather than on getAlbumUrl(): the url is never fetched
        // here, so a submission that reports none must not stop the images it does have from showing.
        final TestGallery fragment = host(gallerySubmissionWithoutUrl());

        assertNull(fragment.getAlbumUrl());
        assertTrue(fragment.hasAlbumToShow());
        assertNotNull(((RecyclerView) fragment.list).getAdapter());
    }

    @Test
    public void aSubmissionWithAUrlButNoUsableImagesBindsNothing() throws Exception {
        // The other half: a gallery whose metadata yielded nothing has a perfectly good url and still
        // has nothing to show.
        final TestGallery fragment = host(gallerySubmissionWithNoMedia());

        assertNotNull(fragment.getAlbumUrl());
        assertTrue(fragment.images.isEmpty());
        assertFalse(fragment.hasAlbumToShow());
        assertNull(((RecyclerView) fragment.list).getAdapter());
    }

    @Test
    public void aPageWhoseSubmissionIsGoneBindsNothingRatherThanThrowing() {
        // Process death or a finishing activity: submissionForShadowboxPage returns null, so onCreate
        // leaves both fields unset and there is nothing to gate on.
        final TestGallery fragment = host(null);

        assertNull(fragment.s);
        assertNull(fragment.images);
        assertNull(fragment.getAlbumUrl());
        assertFalse(fragment.hasAlbumToShow());
        assertNull(((RecyclerView) fragment.list).getAdapter());
    }

    /** Runs the fragment through onCreate/onCreateView attached to the activity. */
    private TestGallery host(final Submission submission) {
        final TestGallery fragment = new TestGallery();
        fragment.submission = submission;
        final Bundle args = new Bundle();
        args.putInt("page", 0);
        fragment.setArguments(args);
        activity.getSupportFragmentManager()
                .beginTransaction()
                .add(android.R.id.content, fragment)
                .commitNow();
        return fragment;
    }

    /**
     * The real fragment with only its Shadowbox lookup replaced. Public and static with a no-arg
     * constructor because FragmentTransaction.add insists on being able to recreate it.
     */
    public static class TestGallery extends RedditGalleryFull {
        Submission submission;

        @Override
        Submission resolveSubmission() {
            return submission;
        }
    }

    private static Submission gallerySubmission() throws Exception {
        return new Submission(galleryData());
    }

    /** The same post with its url field absent, so getUrl() reports none. */
    private static Submission gallerySubmissionWithoutUrl() throws Exception {
        final ObjectNode data = galleryData();
        data.remove("url");
        return new Submission(data);
    }

    /**
     * The post's scalar fields with no gallery attached, so extractGalleryImages finds nothing. This
     * is {@code galleryPost.json} unmerged — which is why that file holds no gallery nodes of its own.
     */
    private static Submission gallerySubmissionWithNoMedia() throws Exception {
        return new Submission(submissionScalars());
    }

    /**
     * A full gallery post: the scalar fields JRAW's Submission constructor and PopulateShadowboxInfo
     * read (several are unboxed, so a bare data node throws), with the gallery nodes merged in from
     * the fixture {@code JsonUtilTest} also uses. Merged rather than copied so there is one
     * description of what a Reddit gallery looks like.
     */
    private static ObjectNode galleryData() throws Exception {
        final ObjectNode data = submissionScalars();
        final JsonNode gallery = readFixture("gallery/gallery_submission_data.json");
        data.set("gallery_data", gallery.get("gallery_data"));
        data.set("media_metadata", gallery.get("media_metadata"));
        return data;
    }

    private static ObjectNode submissionScalars() throws Exception {
        return (ObjectNode) readFixture("submissions/galleryPost.json");
    }

    private static JsonNode readFixture(final String name) throws Exception {
        try (InputStream input =
                RedditGalleryFullTest.class.getClassLoader().getResourceAsStream(name)) {
            assertNotNull(input);
            return MAPPER.readTree(input);
        }
    }

    public static class TestActivity extends AppCompatActivity {}
}
