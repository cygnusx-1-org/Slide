package me.edgan.redditslide.test;

import static org.junit.Assert.assertEquals;

import android.content.Context;
import android.view.View;

import androidx.test.core.app.ApplicationProvider;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import me.edgan.redditslide.SpoilerRobotoTextView;
import me.edgan.redditslide.Views.CommentOverflow;
import me.edgan.redditslide.markdown.MarkdownImages;
import me.edgan.redditslide.markdown.RedditMarkwon;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/**
 * Giphy reactions in the new-Reddit (Markwon) renderer. The image node is dropped from the text
 * (the gif is drawn separately from body_html); the leftover text must not leak as a blank, visible
 * line. The {@code RedditMarkwon}-level "image-only renders empty, not raw source" contract is
 * guarded by the giphy_only entry in MarkdownEquivalenceTest's corpus; these cover the
 * {@link MarkdownImages#renderInto} wrapper, which that suite does not exercise.
 */
@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE)
public class MarkdownImagesGiphyTest {

    private static JsonNode node(String json) throws Exception {
        return new ObjectMapper().readTree(json);
    }

    /** A lone giphy reaction: text view is emptied and hidden, so there's no gap above the gif. */
    @Test
    public void renderIntoHidesTextViewForGiphyOnly() throws Exception {
        Context c = ApplicationProvider.getApplicationContext();
        SpoilerRobotoTextView tv = new SpoilerRobotoTextView(c);
        CommentOverflow overflow = new CommentOverflow(c);
        JsonNode data =
                node("{\"media_metadata\":{\"giphy|RXKCMLmch5W2Q\":{\"status\":\"invalid\"}}}");
        MarkdownImages.renderInto(
                tv,
                overflow,
                "all",
                "![gif](giphy|RXKCMLmch5W2Q)",
                "<div class=\"md\"><p><a href=\"https://giphy.com/gifs/RXKCMLmch5W2Q\""
                        + " target=\"_blank\">https://giphy.com/gifs/RXKCMLmch5W2Q</a></p>\n</div>",
                data);
        assertEquals("", tv.getText().toString());
        assertEquals(View.GONE, tv.getVisibility());
    }

    /** Text followed by a giphy keeps the text and drops the image markdown. */
    @Test
    public void textPlusGiphyKeepsOnlyText() {
        Context c = ApplicationProvider.getApplicationContext();
        SpoilerRobotoTextView tv = new SpoilerRobotoTextView(c);
        RedditMarkwon.setMarkdown(
                tv, "all", "*Say hi to your wife for me*\n\n![gif](giphy|ASd0Ukj0y3qMM)");
        assertEquals("Say hi to your wife for me", tv.getText().toString().trim());
    }
}
