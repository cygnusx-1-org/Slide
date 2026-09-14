package me.edgan.redditslide.test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertNotNull;

import android.app.Application;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.InputStream;
import me.edgan.redditslide.SettingValues;
import me.edgan.redditslide.SubmissionCache;
import net.dean.jraw.models.Submission;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/**
 * The card's selftext preview for a post that pastes a reddit image into its body.
 *
 * <p>The fixtures are six real r/test posts, one per body shape, and five real r/copypasta posts
 * whose picture is Reddit's preview of a link in the body rather than an image the body pasted
 * in. All are stored the way Slide receives them — Slide fetches listings without {@code
 * raw_json=1}, so {@code selftext_html} arrives entity-escaped and the single {@code fromHtml}
 * pass inside {@code getSelftextPreview} is what turns it back into the html the card's TextView
 * then parses. A preview here is therefore html, not display text.
 *
 * <p>Robolectric because that {@code fromHtml} is Android's.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33, application = Application.class)
public class SubmissionCacheTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** The first line of a body that opens with words, once unescaped. */
    private static final String TEXT_FIRST_LINE =
            "<!-- SC_OFF --><div class=\"md\"><p>This is text.</p>";

    /** A later paragraph, which is all that is left of a body that opens with pictures. */
    private static final String LATER_PARAGRAPH = "<p>This is text.</p>";

    private boolean cardTextEllipsizeWas;

    @Before
    public void pinTheEllipsizeSetting() {
        // Process-wide, and the unit-test task forks one JVM for the whole run.
        cardTextEllipsizeWas = SettingValues.cardTextEllipsize;
        SettingValues.cardTextEllipsize = false;
    }

    @After
    public void restoreTheEllipsizeSetting() {
        SettingValues.cardTextEllipsize = cardTextEllipsizeWas;
    }

    // ---------------------------------------------------------------------
    // The preview skips the lines whose picture the card is already drawing
    // ---------------------------------------------------------------------

    @Test
    public void aBodyThatOpensWithWordsPreviewsThoseWords() throws Exception {
        assertPreview("selftext_text_image", TEXT_FIRST_LINE);
        assertPreview("selftext_text_image_text", TEXT_FIRST_LINE);
        assertPreview("selftext_text_image_image", TEXT_FIRST_LINE);
    }

    @Test
    public void aBodyThatOpensWithAPicturePreviewsTheWordsAfterIt() throws Exception {
        // Including the post that opens with two pictures: both lines are skipped, not just one.
        assertPreview("selftext_image_text", LATER_PARAGRAPH);
        assertPreview("selftext_image_image_text", LATER_PARAGRAPH);
        assertPreview("selftext_image_text_image", LATER_PARAGRAPH);
    }

    @Test
    public void noPreviewEverShowsTheUrlOfThePictureBesideIt() throws Exception {
        for (String name : ALL_SHAPES) {
            final String preview = SubmissionCache.getSelftextPreview(submission(name), true);
            assertThat(name, preview.contains("preview.redd.it"), is(false));
        }
    }

    @Test
    public void aBodyThatIsNothingButPicturesHasNoPreview() throws Exception {
        // The card hides the body rather than leaving a blank line under the image.
        final Submission imagesOnly =
                submission(
                        "t3_images_only",
                        "&lt;!-- SC_OFF --&gt;&lt;div class=\"md\"&gt;&lt;p&gt;&lt;a"
                                + " href=\"https://preview.redd.it/a.png?s=1\"&gt;"
                                + "https://preview.redd.it/a.png?s=1&lt;/a&gt;&lt;/p&gt;\n"
                                + "&lt;/div&gt;&lt;!-- SC_ON --&gt;");
        assertThat(SubmissionCache.getSelftextPreview(imagesOnly, true).trim(), is(""));
    }

    // ---------------------------------------------------------------------
    // Without the flag, nothing about the old preview moves
    // ---------------------------------------------------------------------

    @Test
    public void theUnflaggedPreviewIsStillJustTheFirstLine() throws Exception {
        assertThat(
                SubmissionCache.getSelftextPreview(submission("selftext_text_image"), false).trim(),
                is(TEXT_FIRST_LINE));
        // A body that opens with a picture previews that picture's link, as it always has: the card
        // only asks for the other shape when it has drawn the picture itself.
        final String imageFirst =
                SubmissionCache.getSelftextPreview(submission("selftext_image_text"), false);
        assertThat(imageFirst.contains("preview.redd.it"), is(true));
    }

    // ---------------------------------------------------------------------
    // Which way round the card draws it
    // ---------------------------------------------------------------------

    @Test
    public void aBodyOpeningWithAPictureIsRecognised() throws Exception {
        assertStartsWithImage("selftext_text_image", false);
        assertStartsWithImage("selftext_image_text", true);
        assertStartsWithImage("selftext_text_image_text", false);
        assertStartsWithImage("selftext_text_image_image", false);
        assertStartsWithImage("selftext_image_image_text", true);
        assertStartsWithImage("selftext_image_text_image", true);
    }

    @Test
    public void aPictureOutOfTheBodyKeepsTheBodysOrder() throws Exception {
        // The lead image is the one the body pasted in (leadIsInlineImage): under the picture only
        // when the body opened with it.
        assertBelowLeadImage("selftext_image_text", true, true, true);
        assertBelowLeadImage("selftext_image_image_text", true, true, true);
        assertBelowLeadImage("selftext_image_text_image", true, true, true);
        assertBelowLeadImage("selftext_text_image", true, true, false);
        assertBelowLeadImage("selftext_text_image_text", true, true, false);
        assertBelowLeadImage("selftext_text_image_image", true, true, false);
    }

    @Test
    public void aPreviewRedditBuiltFromALinkInTheBodyLeadsTheCard() throws Exception {
        // The five r/copypasta posts whose feed card drew the words above the picture while the
        // comments screen drew the picture above the words. None of them opens with a reddit
        // image — the link reddit previewed is on the first line, midway, or at the very end —
        // and none of that matters: the picture is not a line of the body, so the words go under
        // it, as they do in comments.
        for (String name : LINK_PREVIEW_SHAPES) {
            final Submission submission = submission(name);
            assertThat(name, submission.getDataNode().has("media_metadata"), is(false));
            assertThat(
                    name,
                    submission.getDataNode().path("preview").path("images").path(0).isObject(),
                    is(true));
            assertThat(name, SubmissionCache.selftextStartsWithImage(submission), is(false));
            assertThat(
                    name,
                    SubmissionCache.selftextPreviewBelowLeadImage(submission, true, false),
                    is(true));
        }
    }

    @Test
    public void aLinkPreviewPostStillPreviewsItsFirstLine() throws Exception {
        // The preview is built the unflagged way — nothing in the body is the picture, so there is
        // no line to skip — and the words are the body's opening words.
        assertThat(
                SubmissionCache.getSelftextPreview(
                                submission("selftext_linkpreview_text_then_link"), false)
                        .contains("Ggmonnnn."),
                is(true));
        assertThat(
                SubmissionCache.getSelftextPreview(
                                submission("selftext_linkpreview_link_first"), false)
                        .contains("TLDR:"),
                is(true));
    }

    @Test
    public void withNoLeadImageThereIsNothingToBeUnder() throws Exception {
        // "Hide selftext lead image", "no images", pictures off for the subreddit, a preview that
        // failed to size: the card drew no picture, and the preview keeps its usual seat whatever
        // the body's shape.
        for (String name : ALL_SHAPES) {
            assertBelowLeadImage(name, false, true, false);
            assertBelowLeadImage(name, false, false, false);
        }
        for (String name : LINK_PREVIEW_SHAPES) {
            assertBelowLeadImage(name, false, false, false);
        }
    }

    // ---------------------------------------------------------------------
    // The cache answers the question it was asked
    // ---------------------------------------------------------------------

    @Test
    public void theTwoPreviewsOfOnePostDoNotShareACacheEntry() throws Exception {
        // One Submission instance, asked both ways: the cache is keyed on the mode as well as the
        // body, or the card would show whichever preview happened to be built first.
        final Submission submission = submission("selftext_image_text");
        final String withImages = SubmissionCache.getSelftextPreview(submission, false);
        final String withoutImages = SubmissionCache.getSelftextPreview(submission, true);
        assertThat(withoutImages.trim(), is(LATER_PARAGRAPH));
        assertThat(withImages.equals(withoutImages), is(false));
        // And back, to prove neither call poisoned the other's entry.
        assertThat(
                SubmissionCache.getSelftextPreview(submission, false).equals(withImages), is(true));
    }

    @Test
    public void flippingTheEllipsizeSettingRebuildsThePreview() throws Exception {
        final String longLine = repeat("word ", 60).trim();
        final Submission submission =
                submission(
                        "t3_long_body",
                        "&lt;!-- SC_OFF --&gt;&lt;div class=\"md\"&gt;&lt;p&gt;"
                                + longLine
                                + "&lt;/p&gt;\n&lt;/div&gt;&lt;!-- SC_ON --&gt;");

        SettingValues.cardTextEllipsize = false;
        final String whole = SubmissionCache.getSelftextPreview(submission, true);
        assertThat(whole.contains("…"), is(false));

        SettingValues.cardTextEllipsize = true;
        final String cut = SubmissionCache.getSelftextPreview(submission, true);
        assertThat(cut.contains("…"), is(true));
        assertThat(cut.length() < whole.length(), is(true));
    }

    // ---------------------------------------------------------------------

    private static final String[] ALL_SHAPES = {
        "selftext_text_image",
        "selftext_image_text",
        "selftext_text_image_text",
        "selftext_text_image_image",
        "selftext_image_image_text",
        "selftext_image_text_image",
    };

    /**
     * r/copypasta t3_1w9nv8u, t3_1w379qf, t3_1weyevh, t3_1wepegd and t3_1w0t14y: a self post with
     * a {@code preview} node Reddit built from a link in the body, and no {@code media_metadata}.
     */
    private static final String[] LINK_PREVIEW_SHAPES = {
        "selftext_linkpreview_text_then_link",
        "selftext_linkpreview_long_text_then_link",
        "selftext_linkpreview_link_midway",
        "selftext_linkpreview_link_midway_repost",
        "selftext_linkpreview_link_first",
    };

    private static void assertPreview(String name, String expected) throws Exception {
        assertThat(
                name,
                SubmissionCache.getSelftextPreview(submission(name), true).trim(),
                is(expected));
    }

    private static void assertStartsWithImage(String name, boolean expected) throws Exception {
        assertThat(name, SubmissionCache.selftextStartsWithImage(submission(name)), is(expected));
    }

    private static void assertBelowLeadImage(
            String name, boolean leadImageShown, boolean leadIsInlineImage, boolean expected)
            throws Exception {
        assertThat(
                name + " shown=" + leadImageShown + " inline=" + leadIsInlineImage,
                SubmissionCache.selftextPreviewBelowLeadImage(
                        submission(name), leadImageShown, leadIsInlineImage),
                is(expected));
    }

    private static String repeat(String s, int times) {
        final StringBuilder b = new StringBuilder();
        for (int i = 0; i < times; i++) {
            b.append(s);
        }
        return b.toString();
    }

    /** A minimal self post carrying {@code selftextHtml} exactly as Reddit serves it. */
    private static Submission submission(String fullName, String selftextHtml) throws Exception {
        final com.fasterxml.jackson.databind.node.ObjectNode node = MAPPER.createObjectNode();
        node.put("name", fullName);
        node.put("saved", false);
        node.put("is_self", true);
        node.put("selftext_html", selftextHtml);
        return new Submission(node);
    }

    private static Submission submission(String fixture) throws Exception {
        try (InputStream input =
                SubmissionCacheTest.class
                        .getClassLoader()
                        .getResourceAsStream("submissions/" + fixture + ".json")) {
            assertNotNull(fixture, input);
            final JsonNode node = MAPPER.readTree(input);
            return new Submission(node);
        }
    }
}
