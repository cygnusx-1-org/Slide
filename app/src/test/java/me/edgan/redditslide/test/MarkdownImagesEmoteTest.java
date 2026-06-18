package me.edgan.redditslide.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import me.edgan.redditslide.markdown.MarkdownImages;

import org.junit.Test;

import java.util.Collections;

/** Tests free-emote URL resolution from media_metadata (issue #179). */
public class MarkdownImagesEmoteTest {

    private static JsonNode node(String json) throws Exception {
        return new ObjectMapper().readTree(json);
    }

    @Test
    public void resolvesUrlFromMediaMetadataNotName() throws Exception {
        // Raw name is "upvote" but the real gif is "dizzy_face" — the URL must come from
        // media_metadata, never be constructed from the name.
        JsonNode data =
                node(
                        "{\"media_metadata\":{\"emote|free_emotes_pack|upvote\":{\"e\":"
                                + "\"AnimatedImage\",\"s\":{\"gif\":\"https://www.redditstatic.com/"
                                + "x/dizzy_face.gif\"}}}}");
        MarkdownImages.EmoteResolution r =
                MarkdownImages.resolveEmotes("![gif](emote|free_emotes_pack|upvote)", data);
        assertEquals(
                Collections.singletonList("https://www.redditstatic.com/x/dizzy_face.gif"), r.urls);
        assertEquals("￼", r.markdown);
    }

    @Test
    public void leavesUnresolvableEmoteAlone() throws Exception {
        JsonNode data = node("{\"media_metadata\":{}}");
        MarkdownImages.EmoteResolution r =
                MarkdownImages.resolveEmotes("![gif](emote|free_emotes_pack|x)", data);
        assertTrue(r.urls.isEmpty());
        assertEquals("![gif](emote|free_emotes_pack|x)", r.markdown);
    }

    @Test
    public void noEmoteNoChange() throws Exception {
        MarkdownImages.EmoteResolution r = MarkdownImages.resolveEmotes("just text", node("{}"));
        assertTrue(r.urls.isEmpty());
        assertEquals("just text", r.markdown);
    }
}
