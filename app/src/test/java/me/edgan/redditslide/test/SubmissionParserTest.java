package me.edgan.redditslide.test;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.hasItem;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import me.edgan.redditslide.util.SubmissionParser;
import org.junit.Test;

/**
 * Pure-JVM tests for {@link SubmissionParser}. The class has no Android/JRAW dependencies (only
 * Jackson + commons-text), so no Robolectric is needed. Most cases are driven by inline HTML strings
 * so the exact raw input is known; the multi-line fixtures under {@code resources/submissions/} are
 * used only for structural regression checks (the parser's trailing-newline trimming makes exact
 * whole-string assertions on multi-line input brittle).
 */
public class SubmissionParserTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static String joined(List<String> blocks) {
        return String.join("", blocks);
    }

    // ---------------------------------------------------------------------
    // getBlocks: basic text handling
    // ---------------------------------------------------------------------

    @Test
    public void plainParagraphBecomesSingleDivBlock() {
        List<String> blocks = SubmissionParser.getBlocks("<div class=\"md\"><p>Hello world</p></div>");
        assertThat(blocks.size(), is(1));
        assertThat(blocks.get(0), is("<div class=\"md\"><div>Hello world</div></div>"));
    }

    @Test
    public void unescapesHtmlEntities() {
        List<String> blocks = SubmissionParser.getBlocks("<div><p>Tom &amp; Jerry &lt;3</p></div>");
        assertThat(blocks.get(0), containsString("Tom & Jerry <3"));
    }

    @Test
    public void trimsContentAfterLastNewline() {
        // getBlocks drops everything after the last '\n'.
        List<String> blocks = SubmissionParser.getBlocks("<div>keep</div>\ndropped");
        assertThat(joined(blocks), containsString("keep"));
        assertThat(joined(blocks), not(containsString("dropped")));
    }

    // Deliberately violates getBlocks' declared @NonNull contract, which is the whole point of
    // the characterization — so NullAway has to be told to allow this one call.
    @SuppressWarnings("NullAway")
    @Test(expected = NullPointerException.class)
    public void nullInputThrowsNpe() {
        // unescapeHtml4(null) returns null, so the first .replace(...) dereferences null.
        // Pinned as a characterization: getBlocks assumes a non-null body.
        SubmissionParser.getBlocks(null);
    }

    // ---------------------------------------------------------------------
    // getBlocks: code blocks
    // ---------------------------------------------------------------------

    @Test
    public void inlineCodeGetsMarkersWithoutSplitting() {
        List<String> blocks = SubmissionParser.getBlocks("<div>use <code>foo()</code> here</div>");
        assertThat(blocks.size(), is(1));
        assertThat(blocks.get(0), containsString("<code>[[&lt;[foo()]&gt;]]</code>"));
    }

    @Test
    public void preCodeBlockBecomesOwnBlockWithConvertedWhitespace() {
        // Trailing '\n' ensures the trimming step drops nothing meaningful.
        List<String> blocks =
                SubmissionParser.getBlocks("<pre><code>line1\n  line2\n</code></pre>\n");
        assertThat(blocks.size(), is(2));
        assertThat(blocks.get(0), is("")); // text before the code block (empty here)
        assertThat(
                blocks.get(1),
                is("<pre><code>[[&lt;[line1<br/>&nbsp;&nbsp;line2<br/>]&gt;]]</code></pre>"));
    }

    @Test
    public void multipleCodeBlocksEachOwnBlock() throws Exception {
        String html = TestUtils.getResource("submissions/multipleCodeBlocks.html");
        List<String> blocks = SubmissionParser.getBlocks(html);
        long codeBlocks =
                blocks.stream().filter(b -> b.startsWith("<pre><code>[[&lt;[")).count();
        assertThat(codeBlocks, is(2L));
        // The inline <code>iter_mut</code> in prose gets markers but stays in its text block.
        assertTrue(
                "inline code marker expected",
                joined(blocks).contains("<code>[[&lt;[iter_mut]&gt;]]</code>"));
    }

    // ---------------------------------------------------------------------
    // getBlocks: tables
    // ---------------------------------------------------------------------

    @Test
    public void tableBecomesOwnBlock() {
        List<String> blocks =
                SubmissionParser.getBlocks("<div>before</div><table><tr><td>x</td></tr></table>");
        assertThat(blocks, hasItem("<table><tr><td>x</td></tr></table>"));
    }

    @Test
    public void tableFixtureProducesTableBlock() throws Exception {
        String html = TestUtils.getResource("submissions/table.html");
        List<String> blocks = SubmissionParser.getBlocks(html);
        long tables = blocks.stream().filter(b -> b.startsWith("<table>")).count();
        assertTrue("expected at least one table block", tables >= 1);
    }

    // ---------------------------------------------------------------------
    // getBlocks: horizontal rules
    // ---------------------------------------------------------------------

    @Test
    public void horizontalRuleSplitsBlocks() {
        List<String> blocks = SubmissionParser.getBlocks("<div>a</div><hr/><div>b</div>");
        assertThat(blocks.size(), is(3));
        assertThat(blocks.get(0), is("<div>a</div>"));
        assertThat(blocks.get(1), is("<hr/>"));
        assertThat(blocks.get(2), is("<div>b</div>"));
    }

    @Test
    public void trailingHorizontalRuleIsRemoved() {
        List<String> blocks = SubmissionParser.getBlocks("<div>a</div><hr/>");
        // parseHR appends then removes the last trailing "<hr/>".
        assertThat(blocks, not(hasItem("")));
        assertThat(blocks.get(0), is("<div>a</div>"));
        assertThat(blocks, not(hasItem("<hr/>")));
    }

    @Test
    public void aBodyThatIsNothingButARuleIsARuleBlock() {
        // A comment or Tumblr caption whose whole body is "---". split() drops both parts as
        // trailing empties, so there is nothing to interleave the rule between; parseHR used to
        // trim a tag it had never added and throw IndexOutOfBounds on the way out of getBlocks.
        List<String> blocks = SubmissionParser.getBlocks("<hr/>");
        assertThat(blocks, is(Collections.singletonList("<hr/>")));
    }

    @Test
    public void consecutiveRulesWithNoTextCollapseToOneBlock() {
        List<String> blocks = SubmissionParser.getBlocks("<hr/><hr/>");
        assertThat(blocks, is(Collections.singletonList("<hr/>")));
    }

    @Test
    public void aRuleOnlyBlockDoesNotEatTheBlockBeforeIt() {
        // The trim reached backwards into blocks an earlier iteration had added, so the text before
        // a code block silently lost its rule separator rather than throwing.
        List<String> blocks = SubmissionParser.getBlocks("<div>a</div><pre><code>c</code></pre><hr/>");
        assertThat(blocks.get(0), is("<div>a</div>"));
        assertThat(blocks.get(1), containsString("c"));
        assertThat(blocks.get(1), containsString("<pre><code>"));
    }

    // ---------------------------------------------------------------------
    // getBlocks: never returns an empty list
    // ---------------------------------------------------------------------

    @Test
    public void everyDegenerateInputStillYieldsAtLeastOneBlock() {
        // Callers index block 0 straight away — TumblrPager, TumblrView and AlbumView all read the
        // first block as a caption — so an empty list would be an IndexOutOfBounds at each of them.
        // These are the inputs where a bare String.split() returns an empty array, which is the only
        // way the helpers could have produced one.
        for (String html :
                Arrays.asList(
                        "",
                        "<hr/>",
                        "<hr/><hr/>",
                        "<pre><code>",
                        "<pre><code><pre><code>",
                        "<table>",
                        "<table><table>")) {
            List<String> blocks = SubmissionParser.getBlocks(html);
            assertTrue("no blocks for [" + html + "]", !blocks.isEmpty());
        }
    }

    // ---------------------------------------------------------------------
    // getBlocks: spoilers
    // ---------------------------------------------------------------------

    @Test
    public void spoilerTitleMovedIntoMarkers() {
        List<String> blocks =
                SubmissionParser.getBlocks("<div>Hey <a href=\"/s\" title=\"secret\">teaser</a></div>");
        assertThat(joined(blocks), containsString("teaser&lt; [[s[ secret]s]]</a>"));
    }

    @Test
    public void emptySpoilerTeaserGetsLiteralSpoiler() {
        List<String> blocks =
                SubmissionParser.getBlocks("<div><a href=\"/s\" title=\"hidden\"></a></div>");
        assertThat(joined(blocks), containsString("spoiler&lt; [[s[ hidden]s]]</a>"));
    }

    @Test
    public void httpAnchorIsNotTreatedAsSpoiler() {
        List<String> blocks =
                SubmissionParser.getBlocks(
                        "<div><a href=\"http://x.com\" title=\"t\">link</a></div>");
        assertThat(joined(blocks), not(containsString("[[s[")));
    }

    // ---------------------------------------------------------------------
    // getBlocks: del / sup
    // ---------------------------------------------------------------------

    @Test
    public void strikethroughGetsMarkers() {
        List<String> blocks = SubmissionParser.getBlocks("<div><del>gone</del></div>");
        assertThat(blocks.get(0), containsString("[[d[gone]d]]"));
    }

    @Test
    public void superscriptGetsSmallWrapper() {
        List<String> blocks = SubmissionParser.getBlocks("<div><sup>note</sup></div>");
        assertThat(blocks.get(0), containsString("<sup><small>note</small></sup>"));
    }

    // ---------------------------------------------------------------------
    // getBlocks: lists
    // ---------------------------------------------------------------------

    @Test
    public void orderedListIsNumbered() {
        List<String> blocks = SubmissionParser.getBlocks("<ol><li>one</li><li>two</li></ol>");
        String out = joined(blocks);
        assertThat(out, containsString("1. one<br/>"));
        assertThat(out, containsString("2. two<br/>"));
    }

    @Test
    public void unorderedListUsesBullets() {
        List<String> blocks = SubmissionParser.getBlocks("<ul><li>a</li><li>b</li></ul>");
        String out = joined(blocks);
        assertThat(out, containsString("• a<br/>"));
        assertThat(out, containsString("• b<br/>"));
    }

    @Test
    public void nestedOrderedListIndents() {
        List<String> blocks =
                SubmissionParser.getBlocks("<ol><li>outer<ol><li>inner</li></ol></li></ol>");
        String out = joined(blocks);
        assertThat(out, containsString("1. outer"));
        // Nested level gets the &nbsp; indent prefix.
        assertThat(out, containsString("&nbsp;&nbsp;&nbsp;&nbsp;1. inner"));
    }

    // ---------------------------------------------------------------------
    // getBlocks: kitchen sink regression
    // ---------------------------------------------------------------------

    @Test
    public void everythingFixtureParsesIntoManyBlocks() throws Exception {
        String html = TestUtils.getResource("submissions/everything.html");
        List<String> blocks = SubmissionParser.getBlocks(html);
        assertTrue("expected several blocks, got " + blocks.size(), blocks.size() > 3);
        // The fixture contains two markdown tables, each split into its own block.
        long tables = blocks.stream().filter(b -> b.startsWith("<table>")).count();
        assertThat(tables, is(2L));
    }

    // ---------------------------------------------------------------------
    // replaceProcessingImgPlaceholders
    // ---------------------------------------------------------------------

    @Test
    public void processingImg_nullDataNodeReturnsUnchanged() {
        String body = "Processing img abc123...";
        assertThat(SubmissionParser.replaceProcessingImgPlaceholders(body, null), is(body));
    }

    @Test
    public void processingImg_missingMediaMetadataReturnsUnchanged() throws Exception {
        JsonNode data = MAPPER.readTree("{}");
        String body = "Processing img abc123...";
        assertThat(SubmissionParser.replaceProcessingImgPlaceholders(body, data), is(body));
    }

    @Test
    public void processingImg_nullMediaMetadataReturnsUnchanged() throws Exception {
        JsonNode data = MAPPER.readTree("{\"media_metadata\": null}");
        String body = "Processing img abc123...";
        assertThat(SubmissionParser.replaceProcessingImgPlaceholders(body, data), is(body));
    }

    @Test
    public void processingImg_replacesWithUrl() throws Exception {
        JsonNode data =
                MAPPER.readTree(
                        "{\"media_metadata\":{\"abc123\":{\"s\":{\"u\":\"https://i.redd.it/x.png\"}}}}");
        String result =
                SubmissionParser.replaceProcessingImgPlaceholders("Processing img abc123...", data);
        assertThat(result, is("https://i.redd.it/x.png"));
    }

    @Test
    public void processingImg_asteriskWrappedVariantReplaced() throws Exception {
        JsonNode data =
                MAPPER.readTree(
                        "{\"media_metadata\":{\"abc123\":{\"s\":{\"u\":\"https://i.redd.it/x.png\"}}}}");
        String result =
                SubmissionParser.replaceProcessingImgPlaceholders(
                        "*Processing img abc123...*", data);
        assertThat(result, is("https://i.redd.it/x.png"));
    }

    @Test
    public void processingImg_prefersGifOverMp4OverU() throws Exception {
        JsonNode data =
                MAPPER.readTree(
                        "{\"media_metadata\":{\"g\":{\"s\":{\"gif\":\"https://g.gif\",\"mp4\":\"https://g.mp4\",\"u\":\"https://g.png\"}}}}");
        String result =
                SubmissionParser.replaceProcessingImgPlaceholders("Processing img g...", data);
        assertThat(result, is("https://g.gif"));
    }

    @Test
    public void processingImg_unescapesUrl() throws Exception {
        JsonNode data =
                MAPPER.readTree(
                        "{\"media_metadata\":{\"a\":{\"s\":{\"u\":\"https://i.redd.it/x.png?a=1&amp;b=2\"}}}}");
        String result =
                SubmissionParser.replaceProcessingImgPlaceholders("Processing img a...", data);
        assertThat(result, is("https://i.redd.it/x.png?a=1&b=2"));
    }

    @Test
    public void processingImg_unknownIdLeftInPlace() throws Exception {
        JsonNode data =
                MAPPER.readTree(
                        "{\"media_metadata\":{\"abc\":{\"s\":{\"u\":\"https://i.redd.it/x.png\"}}}}");
        String body = "Processing img zzz...";
        assertThat(SubmissionParser.replaceProcessingImgPlaceholders(body, data), is(body));
    }

    @Test
    public void processingImg_multiplePlaceholders() throws Exception {
        JsonNode data =
                MAPPER.readTree(
                        "{\"media_metadata\":{\"a\":{\"s\":{\"u\":\"https://a.png\"}},\"b\":{\"s\":{\"u\":\"https://b.png\"}}}}");
        String result =
                SubmissionParser.replaceProcessingImgPlaceholders(
                        "Processing img a... and Processing img b...", data);
        assertThat(result, is("https://a.png and https://b.png"));
    }

    // ---------------------------------------------------------------------
    // imageUrlsFor / extractImageBlocks
    // ---------------------------------------------------------------------

    @Test
    public void imageUrlsFor_nullOrEmptyReturnsEmpty() {
        assertTrue(SubmissionParser.imageUrlsFor(null).isEmpty());
        assertTrue(SubmissionParser.imageUrlsFor("").isEmpty());
    }

    @Test
    public void imageUrlsFor_singleReddImageAnchor() {
        List<String> urls =
                SubmissionParser.imageUrlsFor(
                        "<div><a href=\"https://i.redd.it/abc.png\">https://i.redd.it/abc.png</a></div>");
        assertThat(urls, is(Collections.singletonList("https://i.redd.it/abc.png")));
    }

    @Test
    public void imageUrlsFor_giphyPlainLinkRewritten() {
        List<String> urls =
                SubmissionParser.imageUrlsFor(
                        "<a href=\"https://giphy.com/gifs/xyz123\">via giphy</a>");
        assertThat(urls, is(Collections.singletonList("https://i.giphy.com/media/xyz123/giphy.gif")));
    }

    @Test
    public void imageUrlsFor_imgurHostNotExtracted() {
        List<String> urls =
                SubmissionParser.imageUrlsFor(
                        "<div><a href=\"https://i.imgur.com/x.png\">img</a></div>");
        assertTrue(urls.isEmpty());
    }

    @Test
    public void imageUrlsFor_reddItWithoutImageExtensionNotExtracted() {
        List<String> urls =
                SubmissionParser.imageUrlsFor(
                        "<div><a href=\"https://i.redd.it/xyz\">https://i.redd.it/xyz</a></div>");
        assertTrue(urls.isEmpty());
    }

    @Test
    public void extractImageBlocks_passesThroughTableCodeHr() {
        List<String> in = Arrays.asList("<table>x</table>", "<pre>y</pre>", "<hr/>");
        assertThat(SubmissionParser.extractImageBlocks(in), is(in));
    }

    @Test
    public void extractImageBlocks_noImageBlockUnmodified() {
        List<String> in = Collections.singletonList("just plain text");
        assertThat(SubmissionParser.extractImageBlocks(in), is(in));
    }

    @Test
    public void extractImageBlocks_midParagraphImageSplitsText() {
        List<String> out =
                SubmissionParser.extractImageBlocks(
                        Collections.singletonList(
                                "Look <a href=\"https://i.redd.it/pic.jpg\">here</a> now"));
        assertThat(out.size(), is(3));
        assertThat(out.get(0), containsString("Look"));
        assertThat(out.get(1), is(SubmissionParser.IMAGE_BLOCK_PREFIX + "https://i.redd.it/pic.jpg"));
        assertThat(out.get(2), containsString("now"));
    }

    @Test
    public void imageBlockPrefixConstant() {
        // The prefix is wrapped in SOH (U+0001) control chars so it never collides with real text.
        assertEquals("\u0001img\u0001", SubmissionParser.IMAGE_BLOCK_PREFIX);
    }

    // ---------------------------------------------------------------------
    // comment videos (a video uploaded through Reddit's comment composer)
    // ---------------------------------------------------------------------

    /** The verbatim payload of comment osdgm90 of r/initiald/comments/1u95l4h, trailing \n and all. */
    private static final String BARE_VIDEO_BODY_HTML =
            "<div class=\"md\"><p><a href=\"https://reddit.com/link/osdgm90/video/n9pjiglqh18h1/player\">"
                    + "https://reddit.com/link/osdgm90/video/n9pjiglqh18h1/player</a></p>\n</div>";

    private static List<String> videoBlocks(String html) {
        return SubmissionParser.extractImageBlocks(SubmissionParser.getBlocks(html));
    }

    private static String onlyVideoBlock(List<String> blocks) {
        List<String> found = new ArrayList<>();
        for (String block : blocks) {
            if (block.startsWith(SubmissionParser.VIDEO_BLOCK_PREFIX)) {
                found.add(block);
            }
        }
        assertThat("expected exactly one video block in " + blocks, found.size(), is(1));
        return found.get(0);
    }

    @Test
    public void videoBlockPrefixConstant() {
        // Same SOH wrapping as the image prefix, for the same reason.
        assertEquals("\u0001vid\u0001", SubmissionParser.VIDEO_BLOCK_PREFIX);
    }

    @Test
    public void videoAnchorWithCaptionKeepsCaption() {
        String block =
                onlyVideoBlock(
                        videoBlocks(
                                "<div class=\"md\"><p><a href=\"https://reddit.com/link/abc/video/def/player\">"
                                        + "gif</a></p></div>"));
        assertThat(
                SubmissionParser.videoUrlOf(block),
                is("https://reddit.com/link/abc/video/def/player"));
        assertThat(SubmissionParser.videoCaptionOf(block), is("gif"));
    }

    @Test
    public void autolinkedBareVideoUrlHasNoCaption() {
        List<String> blocks = videoBlocks(BARE_VIDEO_BODY_HTML);
        String block = onlyVideoBlock(blocks);
        assertThat(
                SubmissionParser.videoUrlOf(block),
                is("https://reddit.com/link/osdgm90/video/n9pjiglqh18h1/player"));
        // The anchor text is the href, so there is nothing worth printing under the card.
        assertThat(SubmissionParser.videoCaptionOf(block), is(""));
        // Nothing but the card: the surrounding markup is not renderable text.
        assertThat(blocks.size(), is(1));
    }

    @Test
    public void wwwVideoHostStillRecognised() {
        String block =
                onlyVideoBlock(
                        videoBlocks(
                                "<div><a href=\"https://www.reddit.com/link/abc/video/def/player\">"
                                        + "video</a></div>"));
        assertThat(
                SubmissionParser.videoUrlOf(block),
                is("https://www.reddit.com/link/abc/video/def/player"));
    }

    @Test
    public void videoQueryStringStillRecognised() {
        String block =
                onlyVideoBlock(
                        videoBlocks(
                                "<div><a href=\"https://reddit.com/link/abc/video/def/player?source=share\">"
                                        + "gif</a></div>"));
        assertThat(
                SubmissionParser.videoUrlOf(block),
                is("https://reddit.com/link/abc/video/def/player?source=share"));
    }

    @Test
    public void textAroundVideoBecomesItsOwnBlocks() {
        List<String> blocks =
                SubmissionParser.extractImageBlocks(
                        Collections.singletonList(
                                "Look at this <a href=\"https://reddit.com/link/abc/video/def/player\">"
                                        + "gif</a> it's great"));
        assertThat(blocks.size(), is(3));
        assertThat(blocks.get(0), containsString("Look at this"));
        assertThat(blocks.get(1).startsWith(SubmissionParser.VIDEO_BLOCK_PREFIX), is(true));
        assertThat(blocks.get(2), containsString("it's great"));
    }

    @Test
    public void twoVideosInOneCommentBecomeTwoBlocks() {
        List<String> blocks =
                SubmissionParser.extractImageBlocks(
                        Collections.singletonList(
                                "<a href=\"https://reddit.com/link/a1/video/b1/player\">one</a>"
                                        + "<a href=\"https://reddit.com/link/a2/video/b2/player\">two</a>"));
        assertThat(blocks.size(), is(2));
        assertThat(
                SubmissionParser.videoUrlOf(blocks.get(0)),
                is("https://reddit.com/link/a1/video/b1/player"));
        assertThat(SubmissionParser.videoCaptionOf(blocks.get(0)), is("one"));
        assertThat(
                SubmissionParser.videoUrlOf(blocks.get(1)),
                is("https://reddit.com/link/a2/video/b2/player"));
        assertThat(SubmissionParser.videoCaptionOf(blocks.get(1)), is("two"));
    }

    @Test
    public void videoUrlInCodeOrTableIsNotAVideoBlock() {
        List<String> in =
                Arrays.asList(
                        "<pre><code>&lt;a href=\"https://reddit.com/link/abc/video/def/player\"&gt;"
                                + "x&lt;/a&gt;</code></pre>",
                        "<table><tr><td><a href=\"https://reddit.com/link/abc/video/def/player\">"
                                + "x</a></td></tr></table>");
        assertThat(SubmissionParser.extractImageBlocks(in), is(in));
    }

    @Test
    public void imageUrlsFor_videoOnlyBodyReturnsEmpty() {
        // A playlist url in the bitmap preloader would be a pointless download.
        assertTrue(SubmissionParser.imageUrlsFor(BARE_VIDEO_BODY_HTML).isEmpty());
    }

    @Test
    public void getBlocksAloneLeavesTheVideoAsText() {
        // What the renderers do under data saving: CommentAdapter.computeBlocks skips
        // extractImageBlocks entirely, so no card is produced and the plain link still works.
        List<String> blocks = SubmissionParser.getBlocks(BARE_VIDEO_BODY_HTML);
        assertThat(blocks.size(), is(1));
        assertThat(blocks.get(0), containsString("<a href=\"https://reddit.com/link/osdgm90/video/"));
        assertThat(
                blocks.get(0).contains(SubmissionParser.VIDEO_BLOCK_PREFIX), is(false));
    }
}
