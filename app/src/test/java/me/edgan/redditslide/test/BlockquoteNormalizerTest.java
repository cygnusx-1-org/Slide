package me.edgan.redditslide.test;

import static org.junit.Assert.assertEquals;

import me.edgan.redditslide.markdown.BlockquoteNormalizer;
import org.junit.Test;

/**
 * {@link BlockquoteNormalizer} bridges the blank lines between adjacent blockquote paragraphs so
 * the Markwon renderer draws one continuous stripe like snudown, without merging quotes that are
 * actually separated by ordinary content. See issue #179.
 */
public class BlockquoteNormalizerTest {

    private static String merge(String in) {
        return BlockquoteNormalizer.mergeAdjacentBlockquotes(in);
    }

    @Test
    public void blankLineBetweenQuotesBecomesContinuation() {
        assertEquals(">a\n>\n>b", merge(">a\n\n>b"));
    }

    @Test
    public void multipleBlankLinesBetweenQuotesAllBridged() {
        assertEquals(">a\n>\n>\n>b", merge(">a\n\n\n>b"));
    }

    @Test
    public void quoteFollowedByNormalTextIsNotMerged() {
        // The quote correctly ends; the blank line stays blank.
        assertEquals(">a\n\nplain text", merge(">a\n\nplain text"));
    }

    @Test
    public void normalTextBeforeQuoteIsNotMerged() {
        assertEquals("plain\n\n>a", merge("plain\n\n>a"));
    }

    @Test
    public void threeAdjacentQuoteParagraphsBecomeOneBlock() {
        assertEquals(">a\n>\n>b\n>\n>c", merge(">a\n\n>b\n\n>c"));
    }

    @Test
    public void fencedCodeBlockIsLeftUntouched() {
        String code = ">a\n\n```\n>not a quote\n\n>still code\n```\n\n>b";
        // Inside the fence nothing is bridged; the two real quotes flanking the fence are separated
        // by the code block, so they are not merged either.
        assertEquals(code, merge(code));
    }

    @Test
    public void noBlockquoteIsUnchanged() {
        assertEquals("just\n\ntext", merge("just\n\ntext"));
    }

    @Test
    public void nullIsPassedThrough() {
        assertEquals(null, merge(null));
    }
}
