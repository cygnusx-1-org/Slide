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
}
