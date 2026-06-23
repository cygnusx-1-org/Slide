package me.edgan.redditslide.markdown;

import java.util.ArrayList;
import java.util.List;
import org.commonmark.node.AbstractVisitor;
import org.commonmark.node.Code;
import org.commonmark.node.FencedCodeBlock;
import org.commonmark.node.IndentedCodeBlock;
import org.commonmark.node.Node;
import org.commonmark.node.Text;
import org.commonmark.parser.PostProcessor;

/**
 * Turns matched spoiler sentinel pairs (inserted by {@link RedditSpoilerPreprocessor}) inside
 * {@link Text} nodes into {@link SpoilerNode}s. Unmatched sentinels are restored to their literal
 * {@code >!} / {@code !<} text. See issue #179.
 *
 * <p>{@link RedditSpoilerPreprocessor} skips fenced code and inline code spans, but it cannot tell
 * that a 4-space-indented line will become an <em>indented</em> code block, so a {@code >!} there is
 * still sentinelized. The block's content is a literal string, not a {@link Text} node, so those
 * sentinels would otherwise render as raw private-use characters; we restore them to literal text in
 * every code node (covering indented blocks, and harmlessly idempotent for the already-clean fenced
 * and inline code).
 */
public final class SpoilerPostProcessor implements PostProcessor {

    private static final char OPEN = RedditSpoilerPreprocessor.OPEN;
    private static final char CLOSE = RedditSpoilerPreprocessor.CLOSE;

    @Override
    public Node process(Node document) {
        final List<Text> targets = new ArrayList<>();
        document.accept(
                new AbstractVisitor() {
                    @Override
                    public void visit(Text text) {
                        String literal = text.getLiteral();
                        if (literal.indexOf(OPEN) >= 0 || literal.indexOf(CLOSE) >= 0) {
                            targets.add(text);
                        }
                    }

                    @Override
                    public void visit(Code code) {
                        code.setLiteral(restore(code.getLiteral()));
                    }

                    @Override
                    public void visit(FencedCodeBlock block) {
                        block.setLiteral(restore(block.getLiteral()));
                    }

                    @Override
                    public void visit(IndentedCodeBlock block) {
                        block.setLiteral(restore(block.getLiteral()));
                    }
                });
        for (Text text : targets) {
            splitSpoilers(text);
        }
        return document;
    }

    private static void splitSpoilers(Text textNode) {
        final String literal = textNode.getLiteral();
        final List<Node> replacement = new ArrayList<>();
        final StringBuilder buf = new StringBuilder();
        int i = 0;
        final int n = literal.length();
        while (i < n) {
            char c = literal.charAt(i);
            if (c == OPEN) {
                int close = literal.indexOf(CLOSE, i + 1);
                if (close >= 0) {
                    if (buf.length() > 0) {
                        replacement.add(new Text(buf.toString()));
                        buf.setLength(0);
                    }
                    SpoilerNode spoiler = new SpoilerNode();
                    spoiler.appendChild(new Text(restore(literal.substring(i + 1, close))));
                    replacement.add(spoiler);
                    i = close + 1;
                } else {
                    buf.append(">!"); // unmatched open -> literal
                    i++;
                }
            } else if (c == CLOSE) {
                buf.append("!<"); // stray close -> literal
                i++;
            } else {
                buf.append(c);
                i++;
            }
        }
        if (buf.length() > 0) {
            replacement.add(new Text(buf.toString()));
        }

        for (Node node : replacement) {
            textNode.insertBefore(node);
        }
        textNode.unlink();
    }

    /** Restore any stray sentinels (e.g. an inner unmatched open from non-nesting) to literals. */
    private static String restore(String s) {
        if (s == null || (s.indexOf(OPEN) < 0 && s.indexOf(CLOSE) < 0)) {
            return s;
        }
        return s.replace(String.valueOf(OPEN), ">!").replace(String.valueOf(CLOSE), "!<");
    }
}
