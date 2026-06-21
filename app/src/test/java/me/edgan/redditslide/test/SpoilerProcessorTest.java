package me.edgan.redditslide.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import me.edgan.redditslide.markdown.RedditSpoilerPreprocessor;
import me.edgan.redditslide.markdown.SpoilerNode;
import me.edgan.redditslide.markdown.SpoilerPostProcessor;
import org.commonmark.node.AbstractVisitor;
import org.commonmark.node.BlockQuote;
import org.commonmark.node.CustomNode;
import org.commonmark.node.Node;
import org.commonmark.node.Text;
import org.commonmark.parser.Parser;
import org.junit.Test;

/** Pure-commonmark tests (no Android) for the clean-room spoiler preprocess + post-process. */
public class SpoilerProcessorTest {

    private static Node parse(String markdown) {
        Parser parser = Parser.builder().postProcessor(new SpoilerPostProcessor()).build();
        return parser.parse(RedditSpoilerPreprocessor.sentinelize(markdown));
    }

    private static List<String> spoilers(String markdown) {
        final List<String> out = new ArrayList<>();
        parse(markdown)
                .accept(
                        new AbstractVisitor() {
                            @Override
                            public void visit(CustomNode node) {
                                if (node instanceof SpoilerNode) {
                                    StringBuilder sb = new StringBuilder();
                                    for (Node c = node.getFirstChild();
                                            c != null;
                                            c = c.getNext()) {
                                        if (c instanceof Text) {
                                            sb.append(((Text) c).getLiteral());
                                        }
                                    }
                                    out.add(sb.toString());
                                }
                                visitChildren(node);
                            }
                        });
        return out;
    }

    private static boolean hasBlockQuote(String markdown) {
        final boolean[] found = {false};
        parse(markdown)
                .accept(
                        new AbstractVisitor() {
                            @Override
                            public void visit(BlockQuote blockQuote) {
                                found[0] = true;
                            }
                        });
        return found[0];
    }

    @Test
    public void leadingSpoilerIsNotABlockquote() {
        assertEquals(Collections.singletonList("secret"), spoilers(">!secret!<"));
        assertFalse(hasBlockQuote(">!secret!<"));
    }

    @Test
    public void inlineSpoiler() {
        assertEquals(Collections.singletonList("a"), spoilers("text >!a!< more"));
    }

    @Test
    public void multipleSpoilers() {
        assertEquals(Arrays.asList("one", "two"), spoilers(">!one!< and >!two!<"));
    }

    @Test
    public void spoilerInsideInlineCodeIsLiteral() {
        assertEquals(Collections.emptyList(), spoilers("`>!secret!<`"));
    }

    @Test
    public void realBlockquoteStillWorks() {
        // "> text" (with a space) is a genuine blockquote, not a spoiler.
        assertTrue(hasBlockQuote("> just a quote"));
        assertEquals(Collections.emptyList(), spoilers("> just a quote"));
    }

    @Test
    public void unmatchedOpenIsLiteral() {
        assertEquals(Collections.emptyList(), spoilers(">!oops with no close"));
    }
}
