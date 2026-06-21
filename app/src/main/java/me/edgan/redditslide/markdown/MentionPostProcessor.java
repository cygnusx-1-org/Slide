package me.edgan.redditslide.markdown;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.commonmark.node.AbstractVisitor;
import org.commonmark.node.Link;
import org.commonmark.node.Node;
import org.commonmark.node.Text;
import org.commonmark.parser.PostProcessor;

/**
 * Clean-room commonmark post-processor that turns bare Reddit mentions ({@code u/name},
 * {@code r/name}, and the {@code /u/}, {@code /r/} forms) into relative links
 * ({@code /u/name}, {@code /r/name}) — the same hrefs snudown emits — so Slide's existing link
 * routing opens them.
 *
 * <p>It operates on the parsed tree and only on {@link Text} nodes that are not already inside a
 * {@link Link}, so it never rewrites text inside code spans/blocks (which carry no Text children)
 * or existing links. See issue #179.
 */
public final class MentionPostProcessor implements PostProcessor {

    // Leading boundary must not be a word char, '/', or '.' so we don't match inside URLs/paths
    // or mid-word. Group 1 = kind (u|r); group 2 = name (Reddit names are 1-21 chars here).
    private static final Pattern MENTION =
            Pattern.compile("(?<![A-Za-z0-9_/.])/?([ur])/([A-Za-z0-9][A-Za-z0-9_-]{1,20})");

    @Override
    public Node process(Node document) {
        final List<Text> targets = new ArrayList<>();
        document.accept(
                new AbstractVisitor() {
                    @Override
                    public void visit(Text text) {
                        if (!hasLinkAncestor(text)) {
                            targets.add(text);
                        }
                    }
                });
        for (Text text : targets) {
            splitMentions(text);
        }
        return document;
    }

    private static boolean hasLinkAncestor(Node node) {
        for (Node p = node.getParent(); p != null; p = p.getParent()) {
            if (p instanceof Link) {
                return true;
            }
        }
        return false;
    }

    private static void splitMentions(Text textNode) {
        final String literal = textNode.getLiteral();
        final Matcher m = MENTION.matcher(literal);
        if (!m.find()) {
            return;
        }

        final List<Node> replacement = new ArrayList<>();
        int last = 0;
        m.reset();
        while (m.find()) {
            if (m.start() > last) {
                replacement.add(new Text(literal.substring(last, m.start())));
            }
            // Preserve the text as typed (with or without the leading slash); normalize the href.
            final Link link = new Link("/" + m.group(1) + "/" + m.group(2), null);
            link.appendChild(new Text(literal.substring(m.start(), m.end())));
            replacement.add(link);
            last = m.end();
        }
        if (last < literal.length()) {
            replacement.add(new Text(literal.substring(last)));
        }

        for (Node n : replacement) {
            textNode.insertBefore(n);
        }
        textNode.unlink();
    }
}
