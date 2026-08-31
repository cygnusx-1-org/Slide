package me.edgan.redditslide.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.InputStream;
import me.edgan.redditslide.Flair.RichFlairParser;
import net.dean.jraw.models.Submission;
import org.junit.Test;

/**
 * A recovered flair has to beat the removed post's richtext copy.
 *
 * <p>Recovery rewrites {@code link_flair_text} on the live JSON node, but the renderer prefers
 * {@code link_flair_richtext} whenever it holds an emoji. Left alone, that array still describes the
 * pre-removal flair, so the recovery would be applied and then silently ignored on screen.
 */
public class PostRecoveryFlairRichtextTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static ArrayNode richtext(String alias, String url) {
        final ObjectNode segment = MAPPER.createObjectNode();
        segment.put("e", "emoji");
        segment.put("a", alias);
        segment.put("u", url);

        final ArrayNode array = MAPPER.createArrayNode();
        array.add(segment);
        return array;
    }

    private static Submission removedPost(String fullName) throws Exception {
        // Built from a real post fixture: JRAW's Submission constructor reads several unrelated
        // fields eagerly, so a hand-rolled node is not enough to construct one.
        final ObjectNode data;
        try (InputStream input =
                PostRecoveryFlairRichtextTest.class
                        .getClassLoader()
                        .getResourceAsStream("submissions/galleryPost.json")) {
            assertNotNull(input);
            data = (ObjectNode) MAPPER.readTree(input);
        }
        data.put("name", fullName);
        data.put("link_flair_text", "stale flair");
        data.putNull("link_flair_css_class");
        data.set("link_flair_richtext", richtext(":stale:", "https://emoji.example/stale.png"));
        data.put("author_flair_text", "stale author flair");
        data.putNull("author_flair_css_class");
        data.set("author_flair_richtext", richtext(":old:", "https://emoji.example/old.png"));
        return new Submission(data);
    }

    @Test
    public void recoveredFlairClearsTheStaleRichtext() throws Exception {
        final Submission submission = removedPost("t3_richtextrecovery");

        PostRecovery.store(
                submission,
                new PostRecovery.Result(
                        null,
                        null,
                        null,
                        null,
                        new PostRecovery.Flairs("recovered flair", null),
                        new PostRecovery.Flairs("recovered author flair", null)));

        final ObjectNode data = (ObjectNode) submission.getDataNode();

        assertEquals("recovered flair", data.get("link_flair_text").asText());
        assertEquals("recovered author flair", data.get("author_flair_text").asText());

        // Emptied, not removed: the node's key set stays as it was, because the feed's background
        // caching pass writes to this same node and restructuring it could race a rehash.
        assertTrue(data.has("link_flair_richtext"));
        assertTrue(data.has("author_flair_richtext"));

        assertTrue(
                RichFlairParser.from(data, RichFlairParser.LINK_FLAIR_RICHTEXT).isEmpty());
        assertTrue(
                RichFlairParser.from(data, RichFlairParser.AUTHOR_FLAIR_RICHTEXT).isEmpty());
    }

    @Test
    public void anUnrecoveredPostKeepsItsRichtext() throws Exception {
        final Submission submission = removedPost("t3_untouched");

        PostRecovery.reapplyRecoveredLink(submission);

        final ObjectNode data = (ObjectNode) submission.getDataNode();

        assertEquals(
                1, RichFlairParser.from(data, RichFlairParser.LINK_FLAIR_RICHTEXT).size());
        assertEquals(
                1, RichFlairParser.from(data, RichFlairParser.AUTHOR_FLAIR_RICHTEXT).size());
    }
}
