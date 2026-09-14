package me.edgan.redditslide.test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

import java.util.ArrayList;
import java.util.Arrays;
import me.edgan.redditslide.util.StringUtil;
import org.junit.Test;

/** Pure-JVM tests for {@link StringUtil}. */
public class StringUtilTest {

    private static ArrayList<String> list(String... items) {
        return new ArrayList<>(Arrays.asList(items));
    }

    @Test
    public void arrayToString_defaultCommaSeparator() {
        assertThat(StringUtil.arrayToString(list("a", "b", "c")), is("a,b,c"));
    }

    @Test
    public void arrayToString_singleElementHasNoTrailingSeparator() {
        assertThat(StringUtil.arrayToString(list("only")), is("only"));
    }

    @Test
    public void arrayToString_customMultiCharSeparator() {
        assertThat(StringUtil.arrayToString(list("a", "b"), "--"), is("a--b"));
    }

    @Test
    public void arrayToString_emptyListIsEmptyString() {
        assertThat(StringUtil.arrayToString(list()), is(""));
    }

    @Test
    public void arrayToString_nullIsEmptyString() {
        assertThat(StringUtil.arrayToString(null), is(""));
    }

    @Test
    public void stringToArray_splitsOnComma() {
        assertThat(StringUtil.stringToArray("a,b,c"), is(list("a", "b", "c")));
    }

    @Test
    public void stringToArray_emptyStringYieldsSingleEmptyElement() {
        // "".split(",") -> [""], so the result is a one-element list containing "".
        assertThat(StringUtil.stringToArray(""), is(list("")));
    }

    @Test
    public void arrayToString_stringToArray_roundTrip() {
        ArrayList<String> original = list("x", "y", "z");
        assertThat(StringUtil.stringToArray(StringUtil.arrayToString(original)), is(original));
    }

    @Test
    public void abbreviate_shorterThanMaxUnchanged() {
        assertThat(StringUtil.abbreviate("hello", 10), is("hello"));
    }

    @Test
    public void abbreviate_exactlyMaxUnchanged() {
        assertThat(StringUtil.abbreviate("hello", 5), is("hello"));
    }

    @Test
    public void abbreviate_longerThanMaxTruncatedWithEllipsis() {
        String result = StringUtil.abbreviate("hello!", 5);
        assertThat(result, is("he..."));
        assertThat(result.length(), is(5));
    }

    @Test
    public void stripAllWhitespace_removesEveryWhitespaceChar() {
        assertThat(StringUtil.stripAllWhitespace("a b\tc\nd"), is("abcd"));
    }

    @Test
    public void stripAllWhitespace_nullIsEmptyString() {
        assertThat(StringUtil.stripAllWhitespace(null), is(""));
    }

    @Test
    public void stripLeadingTrailingWhitespace_trimsEnds() {
        assertThat(StringUtil.stripLeadingTrailingWhitespace("  a b  "), is("a b"));
    }

    @Test
    public void stripLeadingTrailingWhitespace_nullIsEmptyString() {
        assertThat(StringUtil.stripLeadingTrailingWhitespace(null), is(""));
    }

    @Test
    public void sanitizeString_keepsOnlyAllowedChars() {
        assertThat(StringUtil.sanitizeString("Hello World-1!@#_"), is("HelloWorld-1_"));
    }

    @Test
    public void sanitizeString_allDisallowedYieldsEmpty() {
        assertThat(StringUtil.sanitizeString("!@# $%^"), is(""));
    }

    // ---------------------------------------------------------------------
    // ellipsizeHtml — the "Elliptize to the first 128 characters" card preview (issue #93)
    // ---------------------------------------------------------------------

    /** The shape SubmissionCache hands in: the first line of selftext_html, once unescaped. */
    private static final String PREFIX = "<!-- SC_OFF --><div class=\"md\"><p>";

    private static String repeat(String s, int times) {
        final StringBuilder b = new StringBuilder();
        for (int i = 0; i < times; i++) b.append(s);
        return b.toString();
    }

    @Test
    public void ellipsizeHtml_cutsAfterMaxCharsOfTextAndAppendsEllipsis() {
        assertThat(
                StringUtil.ellipsizeHtml(PREFIX + "abcdefghij</p>", 4), is(PREFIX + "abcd\u2026"));
    }

    @Test
    public void ellipsizeHtml_textWithinLimitIsUnchanged() {
        final String shorter = PREFIX + "abc</p>";
        assertThat(StringUtil.ellipsizeHtml(shorter, 4), is(shorter));
        // Exactly at the limit, with only tags after it, is not "past" it.
        final String exact = PREFIX + "abcd</p>";
        assertThat(StringUtil.ellipsizeHtml(exact, 4), is(exact));
    }

    @Test
    public void ellipsizeHtml_tagsAreNotCountedOrSplit() {
        assertThat(
                StringUtil.ellipsizeHtml("<p>ab<a href=\"http://x.co/long/path\">cd</a>efg</p>", 4),
                is("<p>ab<a href=\"http://x.co/long/path\">cd\u2026"));
    }

    @Test
    public void ellipsizeHtml_entityIsOneCharacterAndNotSplit() {
        // &#39; is the 2nd character and 'm' the 3rd: the cut lands after one or the other,
        // never inside the entity.
        assertThat(StringUtil.ellipsizeHtml("I&#39;m here", 2), is("I&#39;\u2026"));
        assertThat(StringUtil.ellipsizeHtml("I&#39;m here", 3), is("I&#39;m\u2026"));
    }

    @Test
    public void ellipsizeHtml_surrogatePairIsOneCharacterAndNotSplit() {
        // U+1F600 is two UTF-16 units.
        assertThat(StringUtil.ellipsizeHtml("a\uD83D\uDE00bc", 2), is("a\uD83D\uDE00\u2026"));
    }

    @Test
    public void ellipsizeHtml_whitespaceAtTheCutIsDropped() {
        assertThat(StringUtil.ellipsizeHtml("one two three", 4), is("one\u2026"));
    }

    @Test
    public void ellipsizeHtml_realCopypastaParagraphAt128() {
        // r/copypasta t3_1wcqd6w, first paragraph of selftext_html after one unescape.
        final String html =
                PREFIX
                        + "You have to have a very high IQ to understand GTA IV. The nuance is"
                        + " extremely subtle, and without a solid grasp of Slavic literature, most"
                        + " of the story will go over a typical gamer&#39;s head.</p>";
        assertThat(
                StringUtil.ellipsizeHtml(html, 128),
                is(
                        PREFIX
                                + "You have to have a very high IQ to understand GTA IV. The nuance"
                                + " is extremely subtle, and without a solid grasp of Slavic"
                                + " litera\u2026"));
    }

    @Test
    public void ellipsizeHtml_limitCountsTextNotMarkup() {
        // 128 x's inside markup: the markup is free, the 129th x tips it over.
        final String text128 = repeat("x", 128);
        assertThat(
                StringUtil.ellipsizeHtml(PREFIX + text128 + "</p>", 128),
                is(PREFIX + text128 + "</p>"));
        assertThat(
                StringUtil.ellipsizeHtml(PREFIX + text128 + "x</p>", 128),
                is(PREFIX + text128 + "\u2026"));
    }

    // ---------------------------------------------------------------------
    // withoutRedditImageLinks / isOnlyRedditImageLinks — the lines whose picture the card is
    // already drawing as the post's lead image. The inputs are the shape SubmissionCache hands in:
    // one line of selftext_html after a single unescape, i.e. html.
    // ---------------------------------------------------------------------

    /** r/test t3_1wfupwu, the line its body opens with. */
    private static final String PASTED_IMAGE_LINE =
            PREFIX
                    + "<a href=\"https://preview.redd.it/n9adtvxlafph1.jpg?width=2948&amp;"
                    + "format=pjpg&amp;auto=webp&amp;s=b376b1052e028fe826a2273788fe051733efcf25\">"
                    + "https://preview.redd.it/n9adtvxlafph1.jpg?width=2948&amp;format=pjpg&amp;"
                    + "auto=webp&amp;s=b376b1052e028fe826a2273788fe051733efcf25</a></p>";

    @Test
    public void isOnlyRedditImageLinks_aPastedImageIsItsWholeLine() {
        assertThat(StringUtil.isOnlyRedditImageLinks(PASTED_IMAGE_LINE), is(true));
    }

    @Test
    public void isOnlyRedditImageLinks_aLineWithWordsIsNot() {
        assertThat(StringUtil.isOnlyRedditImageLinks(PREFIX + "This is text.</p>"), is(false));
        // Words beside the picture still count as words.
        assertThat(
                StringUtil.isOnlyRedditImageLinks(
                        "<p>Look: <a href=\"https://i.redd.it/a.png\">a.png</a></p>"),
                is(false));
    }

    @Test
    public void isOnlyRedditImageLinks_everyRedditImageHostCounts() {
        // Snudown autolinks a pasted image, so the anchor's text is its own url — which is what
        // leaves nothing behind once both the anchor and the url come out.
        assertThat(StringUtil.isOnlyRedditImageLinks(pastedLine("https://i.redd.it/a.png")), is(true));
        assertThat(
                StringUtil.isOnlyRedditImageLinks(
                        pastedLine("https://external-preview.redd.it/a.png")),
                is(true));
        // Somewhere else's image is not a picture this card drew, so its line is still the preview.
        assertThat(StringUtil.isOnlyRedditImageLinks(pastedLine("https://i.imgur.com/a.png")), is(false));
    }

    /** A pasted image as snudown renders it: an anchor to the url, labelled with the url. */
    private static String pastedLine(final String url) {
        return "<p><a href=\"" + url + "\">" + url + "</a></p>";
    }

    @Test
    public void isOnlyRedditImageLinks_anImageLinkTheAuthorLabelledIsWords() {
        // "[the graph](https://i.redd.it/a.png)" is a word in a sentence, not a picture standing on
        // its own line: the label survives the strip, so the line is still a preview.
        assertThat(
                StringUtil.isOnlyRedditImageLinks(
                        "<p><a href=\"https://i.redd.it/a.png\">the graph</a></p>"),
                is(false));
    }

    @Test
    public void isOnlyRedditImageLinks_aCutLinkIsStillOnlyALink() {
        // ellipsizeHtml cuts at a character budget without closing what it opened, so a truncated
        // image link arrives as an opening tag, some of its text, and the ellipsis.
        final String cut = StringUtil.ellipsizeHtml(PASTED_IMAGE_LINE, 20);
        assertThat(cut.contains("</a>"), is(false));
        assertThat(StringUtil.isOnlyRedditImageLinks(cut), is(true));
    }

    @Test
    public void isOnlyRedditImageLinks_nonBreakingSpaceIsNotContent() {
        assertThat(
                StringUtil.isOnlyRedditImageLinks(
                        pastedLine("https://preview.redd.it/a.png") + "&nbsp;"),
                is(true));
    }

    @Test
    public void withoutRedditImageLinks_takesTheLinkAndItsOwnUrlAsText() {
        final String stripped = StringUtil.withoutRedditImageLinks(PASTED_IMAGE_LINE);
        assertThat(stripped.contains("preview.redd.it"), is(false));
        assertThat(stripped, is(PREFIX + "</p>"));
    }

    @Test
    public void withoutRedditImageLinks_keepsTheWordsOfALinkInASentence() {
        // An author who wrote "[this](url)" put a word in the middle of a sentence; deleting the
        // word would leave the sentence broken.
        assertThat(
                StringUtil.withoutRedditImageLinks(
                        "<p>See <a href=\"https://preview.redd.it/a.png\">this</a> please.</p>"),
                is("<p>See this please.</p>"));
    }

    @Test
    public void withoutRedditImageLinks_removesABareUrlWithoutEatingTheMarkupAfterIt() {
        assertThat(
                StringUtil.withoutRedditImageLinks(
                        "<p>https://preview.redd.it/a.png?s=1</p><p>after</p>"),
                is("<p></p><p>after</p>"));
    }

    @Test
    public void withoutRedditImageLinks_leavesABodyWithNoPicturesAlone() {
        final String plain = PREFIX + "This is text.</p>";
        assertThat(StringUtil.withoutRedditImageLinks(plain), is(plain));
    }
}
