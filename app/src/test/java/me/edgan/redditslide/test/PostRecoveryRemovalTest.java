package me.edgan.redditslide.test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.InputStream;
import me.edgan.redditslide.util.PostRecovery;
import net.dean.jraw.models.Submission;
import org.junit.Test;

/**
 * {@link PostRecovery#isRemovedOrDeleted}: whether the "Recover post" affordance is offered at all.
 *
 * <p>Three separate things make a post recoverable and they do not overlap. Two are placeholder
 * text in the body or the author, and {@link PostRecoveryTest} covers that taxonomy. The third is
 * {@code banned_by}, which reddit sets on a moderator or admin takedown — and on that kind of
 * takedown the body and author can both still read as ordinary text, so it is the only signal
 * there is. Nothing tested it: inverting the check left the whole suite green.
 */
public class PostRecoveryRemovalTest {

    private static Submission post(String selftext, String author, @org.jspecify.annotations.Nullable String bannedBy)
            throws Exception {
        final ObjectNode data;
        try (InputStream input =
                PostRecoveryRemovalTest.class
                        .getClassLoader()
                        .getResourceAsStream("submissions/galleryPost.json")) {
            assertNotNull(input);
            data = (ObjectNode) new ObjectMapper().readTree(input);
        }
        data.put("name", "t3_removal");
        data.put("is_gallery", false);
        data.put("selftext", selftext);
        data.put("author", author);
        if (bannedBy == null) {
            data.putNull("banned_by");
        } else {
            data.put("banned_by", bannedBy);
        }
        return new Submission(data);
    }

    @Test
    public void anOrdinaryPostIsNotRemoved() throws Exception {
        assertFalse(PostRecovery.isRemovedOrDeleted(post("a real body", "someone", null)));
    }

    /** The signal that has no placeholder text behind it. */
    @Test
    public void aPostTakenDownByAModeratorIsRemovedEvenWithItsTextIntact() throws Exception {
        assertTrue(
                "banned_by is the only sign of a takedown that leaves the text standing",
                PostRecovery.isRemovedOrDeleted(post("a real body", "someone", "somemod")));
    }

    @Test
    public void aPostWhoseBodyWasReplacedIsRemoved() throws Exception {
        assertTrue(PostRecovery.isRemovedOrDeleted(post("[removed]", "someone", null)));
    }

    @Test
    public void aPostWhoseAuthorDeletedTheirAccountIsRemoved() throws Exception {
        assertTrue(PostRecovery.isRemovedOrDeleted(post("a real body", "[deleted]", null)));
    }
}
