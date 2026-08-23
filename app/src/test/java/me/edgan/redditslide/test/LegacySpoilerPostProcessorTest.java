package me.edgan.redditslide.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import me.edgan.redditslide.markdown.LegacySpoilerPostProcessor;
import me.edgan.redditslide.markdown.SpoilerNode;
import org.commonmark.node.AbstractVisitor;
import org.commonmark.node.Link;
import org.commonmark.node.Node;
import org.commonmark.node.Text;
import org.commonmark.parser.Parser;
import org.junit.Test;

/**
 * Pure-commonmark tests (no Android) for the legacy CSS spoiler links handled by
 * {@link LegacySpoilerPostProcessor}: {@code [x](/s|#s|/sp|#sp|/spoiler|#spoiler)} and the r/anime
 * title-attribute form {@code [teaser](/s "hidden")}. Issue #179.
 */
public class LegacySpoilerPostProcessorTest {

    private static Node parse(String markdown) {
        return Parser.builder().postProcessor(new LegacySpoilerPostProcessor()).build().parse(markdown);
    }

    /** Text held inside each {@link SpoilerNode}, in document order. */
    private static List<String> spoilers(String markdown) {
        final List<String> out = new ArrayList<>();
        parse(markdown)
                .accept(
                        new AbstractVisitor() {
                            @Override
                            public void visit(org.commonmark.node.CustomNode node) {
                                if (node instanceof SpoilerNode) {
                                    StringBuilder sb = new StringBuilder();
                                    for (Node c = node.getFirstChild(); c != null; c = c.getNext()) {
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

    private static int linkCount(String markdown) {
        final int[] n = {0};
        parse(markdown)
                .accept(
                        new AbstractVisitor() {
                            @Override
                            public void visit(Link link) {
                                n[0]++;
                                visitChildren(link);
                            }
                        });
        return n[0];
    }

    /** All Text literals in the tree (includes spoiler children). */
    private static String allText(String markdown) {
        final StringBuilder sb = new StringBuilder();
        parse(markdown)
                .accept(
                        new AbstractVisitor() {
                            @Override
                            public void visit(Text text) {
                                sb.append(text.getLiteral());
                            }
                        });
        return sb.toString();
    }

    @Test
    public void linkStyleSpoilerHidesLinkText() {
        assertEquals(Collections.singletonList("secret"), spoilers("[secret](/s)"));
        assertEquals("link should be consumed", 0, linkCount("[secret](/s)"));
    }

    @Test
    public void allCanonicalDestinations() {
        for (String dest : new String[] {"/spoiler", "#spoiler", "/s", "#s", "/sp", "#sp"}) {
            String md = "[x](" + dest + ")";
            assertEquals("dest " + dest, Collections.singletonList("x"), spoilers(md));
        }
    }

    @Test
    public void titleAttrHidesTitleAndKeepsTeaser() {
        // r/anime convention: the link text is a visible teaser; the spoiler is the title.
        String md = "[Kaguya manga](/s \"the culture festival confession\")";
        assertEquals(
                Collections.singletonList("the culture festival confession"), spoilers(md));
        assertTrue(
                "teaser and spoiler are separated by a space",
                allText(md).contains("Kaguya manga the culture festival confession"));
        assertEquals("link should be consumed", 0, linkCount(md));
    }

    @Test
    public void nonSpoilerFragmentLinkStaysALink() {
        // A normal fragment link whose destination isn't in the spoiler set must stay a link.
        assertEquals(Collections.emptyList(), spoilers("[trial](#section)"));
        assertEquals(1, linkCount("[trial](#section)"));
    }

    @Test
    public void csshelpDoesItWorkIsNotASpoiler() {
        // From r/csshelp #582cr7: "[trial](#does it work)" — the spaces make it an invalid
        // commonmark link destination (rendered as literal text), and it is not a spoiler either.
        assertEquals(Collections.emptyList(), spoilers("[trial](#does it work)"));
    }

    @Test
    public void fullUrlEndingInSlashSIsNotSpoiler() {
        // Exact-match (anchored) destinations only, so a real URL that happens to end in /s stays.
        assertEquals(Collections.emptyList(), spoilers("[real](https://example.com/s)"));
        assertEquals(1, linkCount("[real](https://example.com/s)"));
    }

    @Test
    public void mixedSpoilerAndNormalLink() {
        String md = "see [a](/s) and [b](https://example.com)";
        assertEquals(Collections.singletonList("a"), spoilers(md));
        assertEquals("only the real link remains", 1, linkCount(md));
    }
}
