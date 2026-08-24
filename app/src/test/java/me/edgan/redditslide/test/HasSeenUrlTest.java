package me.edgan.redditslide.test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.content.Context;
import androidx.test.core.app.ApplicationProvider;
import com.lusfold.androidkeyvaluestore.KVStore;
import me.edgan.redditslide.HasSeen;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/**
 * {@code HasSeen.getSeen(String)}: marking a post read from a link rather than from a Submission.
 *
 * <p>This is the path a tapped reddit link takes, so it has to pull the same post id out of every
 * shape reddit hands out -- a full permalink, a comment permalink, a {@code redd.it} short link,
 * a submission url with no subreddit in it -- and it has to see through the {@code np.} subdomain
 * that reddit puts on "no participation" links. Get the path segment wrong and the post is filed
 * under a subreddit name or a comment id, so it never lines up with the same post seen in the feed.
 *
 * <p>None of it was covered: taking the id from the wrong segment, and dropping the {@code np.}
 * handling, each left the whole suite green.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33, application = Application.class)
public class HasSeenUrlTest {

    private static final String ID = "abc123";

    @Before
    public void setUp() {
        final Context context = ApplicationProvider.getApplicationContext();
        TestUtils.seedRedditApplication();
        KVStore.init(context, "SEEN");
        HasSeen.hasSeen.clear();
        HasSeen.seenTimes.clear();
        HasSeen.clearNewVisited();
    }

    @After
    public void tearDown() {
        HasSeen.hasSeen.clear();
        HasSeen.seenTimes.clear();
        HasSeen.clearNewVisited();
        TestUtils.clearRedditApplication();
    }

    @Test
    public void aFullPermalinkIsFiledUnderThePostId() {
        HasSeen.getSeen("https://www.reddit.com/r/pics/comments/" + ID + "/some_title/");

        assertTrue("filed under the post id", HasSeen.hasSeen.contains(ID));
        assertFalse("not under the subreddit", HasSeen.hasSeen.contains("pics"));
    }

    @Test
    public void aCommentPermalinkIsFiledUnderThePostIdNotTheCommentId() {
        HasSeen.getSeen(
                "https://www.reddit.com/r/pics/comments/" + ID + "/some_title/cmt999/");

        assertTrue(HasSeen.hasSeen.contains(ID));
        assertFalse("the comment id is not the post", HasSeen.hasSeen.contains("cmt999"));
    }

    @Test
    public void aShortLinkIsFiledUnderThePostId() {
        HasSeen.getSeen("https://redd.it/" + ID);

        assertTrue(HasSeen.hasSeen.contains(ID));
    }

    /**
     * Reddit hands out no-participation links on the {@code np.} subdomain; they point at the same
     * post, so they have to be filed as the same post.
     *
     * <p>Note what this does <em>not</em> pin. {@code getSeen} rewrites the authority to strip the
     * {@code np} prefix before classifying the link, but for a permalink the id comes from a path
     * segment, which the host does not affect — disabling the strip leaves this green. The strip
     * is therefore unobservable from this entry point and its own gap stays open; see TEST-GAPS.md.
     */
    @Test
    public void aNoParticipationPermalinkIsFiledUnderThePostId() {
        HasSeen.getSeen("https://np.reddit.com/r/pics/comments/" + ID + "/some_title/");

        assertTrue(
                "np.reddit.com is reddit.com with a prefix, not a different site",
                HasSeen.hasSeen.contains(ID));
    }

    /** A string that is not a url at all is taken as the fullname itself. */
    @Test
    public void aBareFullnameIsFiledUnderItsStrippedId() {
        HasSeen.getSeen("t3_" + ID);

        assertTrue(HasSeen.hasSeen.contains(ID));
    }
}
