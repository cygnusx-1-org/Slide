package me.edgan.redditslide.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.List;
import me.edgan.redditslide.Flair.RichFlairParser;
import me.edgan.redditslide.Flair.Richtext;
import org.junit.Test;

/**
 * Reddit's modern flair shape, as read off a submission's or comment's raw data node.
 *
 * <p>JRAW models only the legacy {@code *_flair_text} / {@code *_flair_css_class} pair, so this
 * parser is the only thing standing between the API and an emoji flair rendering as nothing at all.
 * It runs on the feed's hot path for every post, which is why the malformed cases below matter as
 * much as the well-formed ones: a throw here takes the whole feed down.
 */
public class RichFlairParserTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static ObjectNode nodeWith(String key, ArrayNode value) {
        final ObjectNode data = MAPPER.createObjectNode();
        data.set(key, value);
        return data;
    }

    private static ArrayNode array(ObjectNode... elements) {
        final ArrayNode array = MAPPER.createArrayNode();
        for (ObjectNode element : elements) {
            array.add(element);
        }
        return array;
    }

    private static ObjectNode text(String t) {
        final ObjectNode segment = MAPPER.createObjectNode();
        segment.put("e", "text");
        segment.put("t", t);
        return segment;
    }

    private static ObjectNode emoji(String alias, String url) {
        final ObjectNode segment = MAPPER.createObjectNode();
        segment.put("e", "emoji");
        segment.put("a", alias);
        segment.put("u", url);
        return segment;
    }

    @Test
    public void textOnlyFlairHasNoEmoji() {
        final List<Richtext> segments = RichFlairParser.parse(array(text("Discussion")));

        assertEquals(1, segments.size());
        assertFalse(RichFlairParser.hasEmoji(segments));
        assertTrue(RichFlairParser.emojiUrls(segments).isEmpty());
        assertEquals("Discussion", RichFlairParser.plainText(segments));
    }

    @Test
    public void emojiOnlyFlairIsStillRenderable() {
        // The case the legacy path cannot show at all: link_flair_text is empty, so before this
        // parser the pill either vanished or fell back to the internal CSS class.
        final List<Richtext> segments =
                RichFlairParser.parse(array(emoji(":pikachu:", "https://emoji.example/p.png")));

        assertTrue(RichFlairParser.hasEmoji(segments));
        assertEquals(1, RichFlairParser.emojiUrls(segments).size());
        assertEquals("https://emoji.example/p.png", RichFlairParser.emojiUrls(segments).get(0));
        assertEquals(":pikachu:", RichFlairParser.plainText(segments));
    }

    @Test
    public void mixedFlairKeepsSegmentOrder() {
        final List<Richtext> segments =
                RichFlairParser.parse(
                        array(
                                emoji(":tag:", "https://emoji.example/a.png"),
                                text(" Meta "),
                                emoji(":star:", "https://emoji.example/b.png")));

        assertEquals(3, segments.size());
        assertEquals(":tag: Meta :star:", RichFlairParser.plainText(segments));
        assertEquals(
                java.util.Arrays.asList(
                        "https://emoji.example/a.png", "https://emoji.example/b.png"),
                RichFlairParser.emojiUrls(segments));
    }

    @Test
    public void emojiUrlIsUnescapedOnce() {
        // Warming the cache and reading it back must use byte-identical URLs, so the &amp; that
        // Reddit escapes into the query string is resolved here, once, rather than at each caller.
        final List<Richtext> segments =
                RichFlairParser.parse(
                        array(emoji(":x:", "https://emoji.example/p.png?a=1&amp;b=2")));

        assertEquals("https://emoji.example/p.png?a=1&b=2", segments.get(0).getU());
    }

    @Test
    public void emojiWithoutUrlIsNotAnEmoji() {
        // Nothing to draw, so it must not switch the renderer onto the emoji span; it falls back to
        // its alias as a plain text run instead.
        final ObjectNode segment = MAPPER.createObjectNode();
        segment.put("e", "emoji");
        segment.put("a", ":ghost:");

        final List<Richtext> segments = RichFlairParser.parse(array(segment));

        assertEquals(1, segments.size());
        assertFalse(RichFlairParser.hasEmoji(segments));
        assertEquals(":ghost:", RichFlairParser.plainText(segments));
    }

    @Test
    public void segmentWithoutTypeIsDropped() {
        final ObjectNode segment = MAPPER.createObjectNode();
        segment.put("t", "orphan");

        assertTrue(RichFlairParser.parse(array(segment)).isEmpty());
    }

    @Test
    public void nullValuesAreTolerated() {
        final ObjectNode segment = MAPPER.createObjectNode();
        segment.put("e", "text");
        segment.putNull("t");

        final List<Richtext> segments = RichFlairParser.parse(array(segment));

        assertEquals(1, segments.size());
        assertEquals("", RichFlairParser.plainText(segments));
    }

    @Test
    public void textIsNotHtmlDecoded() {
        // Unlike link_flair_text, richtext "t" arrives as plain text. Running it through fromHtml
        // would eat a literal ampersand a user actually typed.
        final List<Richtext> segments = RichFlairParser.parse(array(text("Q&A")));

        assertEquals("Q&A", RichFlairParser.plainText(segments));
    }

    @Test
    public void malformedNodesYieldNothing() {
        assertTrue(RichFlairParser.parse(null).isEmpty());
        assertTrue(RichFlairParser.parse(MAPPER.createArrayNode()).isEmpty());
        assertTrue(RichFlairParser.parse(MAPPER.createObjectNode()).isEmpty());
        assertTrue(RichFlairParser.parse(MAPPER.getNodeFactory().textNode("nope")).isEmpty());
        assertTrue(RichFlairParser.parse(MAPPER.getNodeFactory().nullNode()).isEmpty());

        // An array holding something that is not an object.
        final ArrayNode mixed = MAPPER.createArrayNode();
        mixed.add("loose string");
        mixed.add(text("kept"));
        assertEquals(1, RichFlairParser.parse(mixed).size());
    }

    @Test
    public void fromReadsTheNamedKeyOffTheDataNode() {
        final ObjectNode data =
                nodeWith(RichFlairParser.LINK_FLAIR_RICHTEXT, array(text("Discussion")));

        assertEquals(
                1, RichFlairParser.from(data, RichFlairParser.LINK_FLAIR_RICHTEXT).size());
        // Author flair lives under its own key; reading the wrong one must not cross the wires.
        assertTrue(
                RichFlairParser.from(data, RichFlairParser.AUTHOR_FLAIR_RICHTEXT).isEmpty());
        assertTrue(RichFlairParser.from(null, RichFlairParser.LINK_FLAIR_RICHTEXT).isEmpty());
    }
}
