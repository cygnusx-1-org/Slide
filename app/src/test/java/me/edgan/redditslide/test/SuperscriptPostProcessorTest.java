package me.edgan.redditslide.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import me.edgan.redditslide.markdown.SuperscriptNode;
import me.edgan.redditslide.markdown.SuperscriptPostProcessor;

import org.commonmark.node.AbstractVisitor;
import org.commonmark.node.CustomNode;
import org.commonmark.node.Node;
import org.commonmark.node.Text;
import org.commonmark.parser.Parser;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** Pure-commonmark tests (no Android) for the clean-room {@link SuperscriptPostProcessor}. */
public class SuperscriptPostProcessorTest {

    private static List<String> superscripts(String markdown) {
        Parser parser = Parser.builder().postProcessor(new SuperscriptPostProcessor()).build();
        Node document = parser.parse(markdown);
        final List<String> out = new ArrayList<>();
        document.accept(
                new AbstractVisitor() {
                    @Override
                    public void visit(CustomNode node) {
                        if (node instanceof SuperscriptNode) {
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

    @Test
    public void singleToken() {
        assertEquals(Collections.singletonList("super"), superscripts("a^super b"));
    }

    @Test
    public void parenthesizedPhrase() {
        assertEquals(Collections.singletonList("two words"), superscripts("x ^(two words) y"));
    }

    @Test
    public void multiplePerText() {
        assertEquals(Arrays.asList("a", "b"), superscripts("^a and ^b"));
    }

    @Test
    public void notInsideInlineCode() {
        assertEquals(Collections.emptyList(), superscripts("`a^b`"));
    }

    @Test
    public void nestedSuperscriptAddsLevels() {
        // ^^word -> a superscript of a superscript (two nodes); innermost text is "word".
        List<String> nodes = superscripts("a ^^word b");
        assertEquals(2, nodes.size());
        assertTrue(nodes.contains("word"));
    }

    @Test
    public void parenthesizedWithNestedToken() {
        // ^(a ^b) -> outer holds "a " + an inner superscript around "b".
        assertEquals(Arrays.asList("a ", "b"), superscripts("^(a ^b)"));
    }
}
