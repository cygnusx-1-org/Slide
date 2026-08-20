package me.edgan.redditslide.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;
import androidx.test.core.app.ApplicationProvider;
import me.edgan.redditslide.SpoilerRobotoTextView;
import me.edgan.redditslide.Views.CommentOverflow;
import me.edgan.redditslide.markdown.MarkdownImages;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/**
 * Videos uploaded through Reddit's comment composer, in the new-Reddit (Markwon) renderer used by
 * profile comments, the inbox, the moderator queue and the comment bottom sheet. The card is drawn
 * from body_html into the overflow, and the link it replaces is taken out of the text.
 */
@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE)
public class MarkdownImagesVideoTest {

    /** Comment osdgm90 of r/initiald/comments/1u95l4h: a pasted bare link, autolinked by snudown. */
    private static final String BARE_URL =
            "https://reddit.com/link/osdgm90/video/n9pjiglqh18h1/player";

    private static final String BARE_BODY_HTML =
            "<div class=\"md\"><p><a href=\"" + BARE_URL + "\">" + BARE_URL + "</a></p>\n</div>";

    /** The card's own container: a video block is the only child CommentOverflow adds for it. */
    private static ViewGroup card(CommentOverflow overflow) {
        assertEquals(1, overflow.getChildCount());
        return (ViewGroup) overflow.getChildAt(0);
    }

    /** A video block renders as a container whose first child is the tappable card. */
    private static boolean isCard(View child) {
        return child instanceof ViewGroup
                && ((ViewGroup) child).getChildCount() > 0
                && ((ViewGroup) child).getChildAt(0) instanceof FrameLayout;
    }

    private static String text(View child) {
        return ((TextView) child).getText().toString();
    }

    /**
     * The fast path in {@link MarkdownImages#render} must let a video-only body through:
     * "reddit.com" does not contain the substring "redd.it", so without its own test every video
     * comment would be dropped before parsing.
     */
    @Test
    public void bareVideoUrlRendersACardAndLeavesNoLinkText() {
        Context c = ApplicationProvider.getApplicationContext();
        SpoilerRobotoTextView tv = new SpoilerRobotoTextView(c);
        CommentOverflow overflow = new CommentOverflow(c);

        MarkdownImages.renderInto(tv, overflow, "initiald", BARE_URL + "\n\n", BARE_BODY_HTML, null);

        // One card, no caption line under it (the anchor text is just the href).
        assertEquals(1, card(overflow).getChildCount());
        // The url is drawn as the card, so it must not also be printed as a link above it.
        assertEquals("", tv.getText().toString());
        assertEquals(View.GONE, tv.getVisibility());
    }

    /** A captioned video (`![gif](url)`) keeps the caption on its own line under the card. */
    @Test
    public void captionedVideoRendersCaptionUnderTheCard() {
        Context c = ApplicationProvider.getApplicationContext();
        SpoilerRobotoTextView tv = new SpoilerRobotoTextView(c);
        CommentOverflow overflow = new CommentOverflow(c);

        MarkdownImages.renderInto(
                tv,
                overflow,
                "initiald",
                "![gif](https://reddit.com/link/abc/video/def/player)",
                "<div class=\"md\"><p><a href=\"https://reddit.com/link/abc/video/def/player\">gif"
                        + "</a></p>\n</div>",
                null);

        ViewGroup container = card(overflow);
        assertEquals(2, container.getChildCount());
        assertEquals("gif", ((TextView) container.getChildAt(1)).getText().toString());
    }

    /**
     * Text around the video keeps its place: the leading run goes in the dedicated first TextView,
     * and the card and the trailing run follow it in the overflow, in that order.
     */
    @Test
    public void textAroundTheVideoSurvives() {
        Context c = ApplicationProvider.getApplicationContext();
        SpoilerRobotoTextView tv = new SpoilerRobotoTextView(c);
        CommentOverflow overflow = new CommentOverflow(c);

        MarkdownImages.renderInto(
                tv,
                overflow,
                "initiald",
                "Look at this " + BARE_URL + " it's great",
                "<div class=\"md\"><p>Look at this <a href=\"" + BARE_URL + "\">" + BARE_URL
                        + "</a> it's great</p>\n</div>",
                null);

        assertTrue(tv.getText().toString().contains("Look at this"));
        assertTrue(!tv.getText().toString().contains("/video/"));
        assertEquals(View.VISIBLE, tv.getVisibility());

        assertEquals(2, overflow.getChildCount());
        assertTrue(isCard(overflow.getChildAt(0)));
        assertTrue(text(overflow.getChildAt(1)).contains("it's great"));
    }

    /**
     * The shape Reddit's composer produces for a self post: the video first, then the body text.
     * The card has to come out above the text, the way the author wrote it — regression test for
     * r/wallpaperengine/comments/1ktbgap, whose selftext is exactly this.
     */
    @Test
    public void videoBeforeTextPutsTheCardFirst() {
        Context c = ApplicationProvider.getApplicationContext();
        SpoilerRobotoTextView tv = new SpoilerRobotoTextView(c);
        CommentOverflow overflow = new CommentOverflow(c);
        String url = "https://reddit.com/link/1ktbgap/video/q8rpr9z0sg2f1/player";

        MarkdownImages.renderInto(
                tv,
                overflow,
                "wallpaperengine",
                url + "\n\nIt's on the wallpaper engine android version. Looking good ?",
                "<!-- SC_OFF --><div class=\"md\"><p><a href=\"" + url + "\">" + url + "</a></p>\n\n"
                        + "<p>It&#39;s on the wallpaper engine android version. Looking good ?</p>\n"
                        + "</div><!-- SC_ON -->",
                null);

        // Nothing leads the body, so the dedicated first TextView is hidden and the overflow
        // carries the whole thing in order.
        assertEquals("", tv.getText().toString());
        assertEquals(View.GONE, tv.getVisibility());

        assertEquals(2, overflow.getChildCount());
        assertTrue(isCard(overflow.getChildAt(0)));
        assertTrue(text(overflow.getChildAt(1)).contains("wallpaper engine android version"));
    }

    /** Two videos with text between them keep all three in source order. */
    @Test
    public void videoTextVideoKeepsSourceOrder() {
        Context c = ApplicationProvider.getApplicationContext();
        SpoilerRobotoTextView tv = new SpoilerRobotoTextView(c);
        CommentOverflow overflow = new CommentOverflow(c);
        String one = "https://reddit.com/link/a1/video/b1/player";
        String two = "https://reddit.com/link/a2/video/b2/player";

        MarkdownImages.renderInto(
                tv,
                overflow,
                "initiald",
                one + "\n\nand then\n\n" + two,
                "<div class=\"md\"><p><a href=\"" + one + "\">" + one + "</a></p>\n\n"
                        + "<p>and then</p>\n\n<p><a href=\"" + two + "\">" + two + "</a></p>\n</div>",
                null);

        assertEquals(View.GONE, tv.getVisibility());
        assertEquals(3, overflow.getChildCount());
        assertTrue(isCard(overflow.getChildAt(0)));
        assertTrue(text(overflow.getChildAt(1)).contains("and then"));
        assertTrue(isCard(overflow.getChildAt(2)));
    }

    /** A body with no media at all still short-circuits to an empty overflow. */
    @Test
    public void plainTextClearsTheOverflow() {
        Context c = ApplicationProvider.getApplicationContext();
        SpoilerRobotoTextView tv = new SpoilerRobotoTextView(c);
        CommentOverflow overflow = new CommentOverflow(c);

        MarkdownImages.renderInto(
                tv, overflow, "initiald", "hello", "<div class=\"md\"><p>hello</p>\n</div>", null);

        assertEquals(0, overflow.getChildCount());
    }
}
