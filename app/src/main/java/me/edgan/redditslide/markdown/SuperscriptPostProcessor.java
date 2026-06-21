package me.edgan.redditslide.markdown;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.commonmark.node.AbstractVisitor;
import org.commonmark.node.Node;
import org.commonmark.node.Text;
import org.commonmark.parser.PostProcessor;

/**
 * Clean-room commonmark post-processor for Reddit superscript: {@code ^(phrase with spaces)} or
 * {@code ^token} (up to the next whitespace). Matched text is wrapped in a {@link SuperscriptNode}.
 *
 * <p>Nesting is supported: each {@code ^} adds a level (e.g. {@code ^^word} is a superscript of a
 * superscript), achieved by parsing each match's inner content recursively. Operates only on
 * {@link Text} nodes, so it never rewrites text inside code spans/blocks. See issue #179.
 */
public final class SuperscriptPostProcessor implements PostProcessor {

    // Parenthesized form (may contain spaces) OR a single non-whitespace token.
    private static final Pattern SUPERSCRIPT = Pattern.compile("\\^\\(([^)]*)\\)|\\^(\\S+)");

    @Override
    public Node process(Node document) {
        final List<Text> targets = new ArrayList<>();
        document.accept(
                new AbstractVisitor() {
                    @Override
                    public void visit(Text text) {
                        targets.add(text);
                    }
                });
        for (Text text : targets) {
            if (text.getLiteral().indexOf('^') >= 0) {
                List<Node> parsed = parse(text.getLiteral());
                for (Node n : parsed) {
                    text.insertBefore(n);
                }
                text.unlink();
            }
        }
        return document;
    }

    /** Split {@code s} into Text and (recursively nested) {@link SuperscriptNode}s. */
    private static List<Node> parse(String s) {
        final List<Node> out = new ArrayList<>();
        final Matcher m = SUPERSCRIPT.matcher(s);
        int last = 0;
        while (m.find()) {
            if (m.start() > last) {
                out.add(new Text(s.substring(last, m.start())));
            }
            String inner = m.group(1) != null ? m.group(1) : m.group(2);
            SuperscriptNode sup = new SuperscriptNode();
            for (Node child : parse(inner)) { // recurse: each ^ adds a level
                sup.appendChild(child);
            }
            out.add(sup);
            last = m.end();
        }
        if (last < s.length()) {
            out.add(new Text(s.substring(last)));
        }
        return out;
    }
}
