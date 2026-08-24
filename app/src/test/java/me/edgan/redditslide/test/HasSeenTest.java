package me.edgan.redditslide.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.content.Context;
import androidx.test.core.app.ApplicationProvider;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.lusfold.androidkeyvaluestore.KVStore;
import java.io.InputStream;
import me.edgan.redditslide.HasSeen;
import me.edgan.redditslide.Synccit.SynccitRead;
import net.dean.jraw.models.Submission;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/**
 * What counts as "seen", and the key everything is filed under.
 *
 * <p>Three separate rules live here and none was covered. The store is keyed on the id with the
 * kind prefix stripped, so the same post filed through two code paths has to land on one key.
 * Reddit's own {@code visited} flag and the user's vote both mean seen, so a post read on another
 * client or voted on here is not shown as new. And {@code addSeenScrolling} is called from
 * {@code onScrolled} -- many times a second during a fling -- so it has to stop after the first
 * time or the Synccit lists collect a duplicate per frame.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33, application = Application.class)
public class HasSeenTest {

    @Before
    public void setUp() {
        final Context context = ApplicationProvider.getApplicationContext();
        TestUtils.seedRedditApplication();
        KVStore.init(context, "SEEN");
        // hasSeen, seenTimes and the Synccit lists are process-wide statics.
        HasSeen.hasSeen.clear();
        HasSeen.seenTimes.clear();
        SynccitRead.newVisited.clear();
        SynccitRead.visitedIds.clear();
    }

    @After
    public void tearDown() {
        HasSeen.hasSeen.clear();
        HasSeen.seenTimes.clear();
        SynccitRead.newVisited.clear();
        SynccitRead.visitedIds.clear();
        TestUtils.clearRedditApplication();
    }

    private static Submission post(boolean visited, String vote) throws Exception {
        final ObjectNode data;
        try (InputStream input =
                HasSeenTest.class
                        .getClassLoader()
                        .getResourceAsStream("submissions/galleryPost.json")) {
            assertNotNull(input);
            data = (ObjectNode) new ObjectMapper().readTree(input);
        }
        data.put("name", "t3_seen1");
        data.put("visited", visited);
        if ("up".equals(vote)) {
            data.put("likes", true);
        } else if ("down".equals(vote)) {
            data.put("likes", false);
        } else {
            data.putNull("likes");
        }
        return new Submission(data);
    }

    /** The store is keyed on the bare id, so a raw fullname and the stripped id are one entry. */
    @Test
    public void thePrefixIsStrippedBeforeTheIdIsStored() {
        HasSeen.addSeen("t3_seen1");

        assertTrue("filed under the bare id", HasSeen.hasSeen.contains("seen1"));
        assertFalse("not under the raw fullname", HasSeen.hasSeen.contains("t3_seen1"));
    }

    /** Reddit already knows the user opened it, so it must not be shown as new. */
    @Test
    public void aPostRedditReportsAsVisitedIsSeen() throws Exception {
        assertTrue(HasSeen.getSeen(post(true, "none")));
    }

    /** Voting on a post is reading it. */
    @Test
    public void aPostTheUserVotedOnIsSeen() throws Exception {
        assertTrue(HasSeen.getSeen(post(false, "up")));
    }

    @Test
    public void anUntouchedPostIsNotSeen() throws Exception {
        assertFalse(HasSeen.getSeen(post(false, "none")));
    }

    /**
     * Comments are not posts; Synccit is a read-history service for links. Note the key rule is
     * asymmetric on purpose: only a {@code t3_} prefix is stripped, so a comment keeps its own.
     */
    @Test
    public void aCommentIsNotPushedToSynccit() {
        HasSeen.addSeen("t1_comment1");

        assertTrue(
                "it is still recorded locally, prefix and all",
                HasSeen.hasSeen.contains("t1_comment1"));
        assertTrue("but not queued for Synccit", SynccitRead.newVisited.isEmpty());
    }

    @Test
    public void aSubmissionIsPushedToSynccit() {
        HasSeen.addSeen("t3_seen1");

        assertEquals(1, SynccitRead.newVisited.size());
        assertEquals("seen1", SynccitRead.newVisited.get(0));
    }

    /**
     * The fling guard. Without it every frame of a scroll appends the same id to an ArrayList that
     * is never de-duplicated.
     */
    @Test
    public void scrollingPastTheSamePostRepeatedlyRecordsItOnce() {
        for (int i = 0; i < 25; i++) {
            HasSeen.addSeenScrolling("t3_seen1");
        }

        assertEquals("one entry, not one per frame", 1, SynccitRead.newVisited.size());
        assertEquals(1, SynccitRead.visitedIds.size());
    }

    @Test
    public void scrollingPastTwoPostsRecordsBoth() {
        HasSeen.addSeenScrolling("t3_seen1");
        HasSeen.addSeenScrolling("t3_seen2");

        assertEquals(2, SynccitRead.newVisited.size());
    }
}
