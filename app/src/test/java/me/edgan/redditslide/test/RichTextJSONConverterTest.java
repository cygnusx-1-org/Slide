package me.edgan.redditslide.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.Collections;
import me.edgan.redditslide.markdown.RichTextJSONConverter;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

/**
 * Regression guard for the #176 richtext_json submit path after the commonmark 0.13.0 migration
 * (which is active regardless of the new Reddit-style toggle). Issue #179.
 */
@RunWith(RobolectricTestRunner.class)
public class RichTextJSONConverterTest {

    private static JSONObject convert(String md) throws Exception {
        return new JSONObject(
                new RichTextJSONConverter().constructRichTextJSON(md, Collections.emptyList()));
    }

    @Test
    public void plainParagraph() throws Exception {
        JSONArray doc = convert("hello world").getJSONArray("document");
        assertEquals(1, doc.length());
        JSONObject par = doc.getJSONObject(0);
        assertEquals("par", par.getString("e"));
        JSONObject text = par.getJSONArray("c").getJSONObject(0);
        assertEquals("text", text.getString("e"));
        assertEquals("hello world", text.getString("t"));
    }

    @Test
    public void boldFormat() throws Exception {
        JSONObject text =
                convert("**bold**")
                        .getJSONArray("document")
                        .getJSONObject(0)
                        .getJSONArray("c")
                        .getJSONObject(0);
        assertEquals("bold", text.getString("t"));
        JSONArray fmt = text.getJSONArray("f").getJSONArray(0);
        assertEquals(1, fmt.getInt(0)); // BOLD = 1
        assertEquals(0, fmt.getInt(1)); // start
        assertEquals(4, fmt.getInt(2)); // length
    }

    @Test
    public void fencedCodeBlock() throws Exception {
        JSONObject block = convert("```\ncode\n```").getJSONArray("document").getJSONObject(0);
        assertEquals("code", block.getString("e"));
    }

    @Test
    public void link() throws Exception {
        // org.json escapes '/' as '\/', so normalize before checking the URL.
        String json = convert("see [text](https://example.com)").toString().replace("\\/", "/");
        assertTrue(json.contains("\"e\":\"link\""));
        assertTrue(json.contains("https://example.com"));
    }
}
