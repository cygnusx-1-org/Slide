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
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import me.edgan.redditslide.Activities.ShadowboxComments;
import me.edgan.redditslide.Adapters.CommentUrlObject;
import me.edgan.redditslide.R;
import me.edgan.redditslide.Reddit;
import me.edgan.redditslide.test.TestUtils;
import net.dean.jraw.models.Comment;
import net.dean.jraw.models.CommentNode;
import net.dean.jraw.models.CommentSort;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;

/**
 * Covers the album fragment behind a comment link — the one whose media comes from a static list
 * filled by CommentPage rather than from a submission, and the only one whose bindActionbar guards
 * against a missing item.
 *
 * <p>Reaching it by hand needs a comment thread that happens to contain a media link, so its two
 * states are pinned here: an entry present, and the static list gone after process death.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33, application = Application.class)
public class AlbumFullCommentsTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private TestActivity activity;

    @Before
    public void setUp() {
        // The stock Application never runs Reddit.onCreate, and the actionbar this fragment binds
        // colours the comment author through Palette, which reads Reddit.colors. An empty store is
        // enough — every subreddit then resolves to the default colour.
        TestUtils.seedRedditApplication();
        Reddit.colors =
                ((Context) ApplicationProvider.getApplicationContext())
                        .getSharedPreferences("COLOR", Context.MODE_PRIVATE);

        final ActivityController<TestActivity> controller =
                Robolectric.buildActivity(TestActivity.class);
        activity = controller.get();
        activity.setTheme(R.style.Theme_LIGHT);
        // @id/title sizes itself from ?attr/font_cardtitle, from the font-style overlay.
        activity.getTheme().applyStyle(R.style.FontStyle_MediumPost, true);
        activity.getTheme().applyStyle(R.style.FontStyle_MediumComment, true);
        controller.setup();
    }

    @After
    public void tearDown() {
        // Leave the globals as they were for any same-config test sharing this Robolectric sandbox.
        ShadowboxComments.comments = null;
        Reddit.colors = null;
        TestUtils.clearRedditApplication();
    }

    @Test
    public void theFragmentTakesItsAlbumFromTheStaticListByPage() throws Exception {
        ShadowboxComments.comments = new ArrayList<>();
        ShadowboxComments.comments.add(commentLink("https://imgur.com/a/first"));
        ShadowboxComments.comments.add(commentLink("https://imgur.com/a/second"));

        final AlbumFullComments fragment = host(1);

        // Page 1, so the second link — an off-by-one here would load the wrong comment's album.
        assertEquals("https://imgur.com/a/second", fragment.getAlbumUrl());
        assertTrue(fragment.hasAlbumToShow());
        assertNotNull(fragment.list);
    }

    @Test
    public void aMissingStaticListLeavesNothingToShowRatherThanThrowing() {
        // What the fragment is recreated into after process death: the list is static, so it comes
        // back null while the fragment still carries its old page argument.
        ShadowboxComments.comments = null;

        final AlbumFullComments fragment = host(0);

        assertNull(fragment.getAlbumUrl());
        assertFalse(fragment.hasAlbumToShow());
        assertNull(((RecyclerView) fragment.list).getAdapter());
        assertTrue(activity.isFinishing());
    }

    @Test
    public void aPageBeyondTheStaticListLeavesNothingToShow() {
        ShadowboxComments.comments = new ArrayList<>();
        ShadowboxComments.comments.add(commentLink("https://imgur.com/a/only"));

        final AlbumFullComments fragment = host(3);

        assertNull(fragment.getAlbumUrl());
        assertFalse(fragment.hasAlbumToShow());
        assertTrue(activity.isFinishing());
    }

    /** Runs the fragment through onCreate/onCreateView attached to the activity. */
    private AlbumFullComments host(final int page) {
        final AlbumFullComments fragment = new AlbumFullComments();
        final Bundle args = new Bundle();
        args.putInt("page", page);
        fragment.setArguments(args);
        activity.getSupportFragmentManager()
                .beginTransaction()
                .add(android.R.id.content, fragment)
                .commitNow();
        return fragment;
    }

    /** A comment carrying a media link, as CommentPage builds when it scans a thread. */
    private static CommentUrlObject commentLink(final String url) {
        return new CommentUrlObject(commentNode(), url, "pics");
    }

    private static CommentNode commentNode() {
        try (InputStream input =
                AlbumFullCommentsTest.class
                        .getClassLoader()
                        .getResourceAsStream("submissions/commentWithLink.json")) {
            assertNotNull(input);
            final JsonNode data = MAPPER.readTree(input);
            final CommentNode root =
                    new CommentNode(
                            "t3_gal123",
                            Collections.singletonList(new Comment(data)),
                            null,
                            CommentSort.TOP);
            // The root wraps the submission; the comment itself is its only child.
            return root.iterator().next();
        } catch (Exception e) {
            throw new AssertionError("could not build a CommentNode", e);
        }
    }

    public static class TestActivity extends AppCompatActivity {}
}
