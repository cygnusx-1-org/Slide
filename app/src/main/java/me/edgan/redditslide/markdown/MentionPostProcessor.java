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
 * routing opens them. A mention that continues into a path ({@code r/sub/comments/id/slug/}) is
 * linked whole, as on Reddit, so the link opens the submission and not just the subreddit.
 *
 * <p>It operates on the parsed tree and only on {@link Text} nodes that are not already inside a
 * {@link Link}, so it never rewrites text inside code spans/blocks (which carry no Text children)
 * or existing links. See issue #179.
 */
public final class MentionPostProcessor implements PostProcessor {

    /**
     * One subreddit name, as snudown's {@code sd_autolink__subreddit} reads it: the literal
     * {@code reddit.com} (the only subreddit whose name contains a dot) or 2-24 of
     * {@code [A-Za-z0-9_]} starting with an alphanumeric. Hyphens are not name characters.
     *
     * <p>Both lookarounds exist to make an over-long name link <em>nothing</em> rather than a
     * truncated prefix, which would point at a different, real subreddit: the trailing one anchors
     * the 24-char ceiling, and the leading one covers snudown's stricter 10-char ceiling for the
     * {@code reddit.com} special case (so {@code r/reddit.commission} is not linked at all).
     */
    private static final String SUBREDDIT_NAME =
            "(?!(?i:reddit\\.com)[A-Za-z0-9_])"
                    + "(?:(?i:reddit\\.com)|[A-Za-z0-9][A-Za-z0-9_]{1,23}(?![A-Za-z0-9_]))";

    /**
     * Bare Reddit mention, matching what snudown links (see issue #356). Leading boundary must not
     * be a word char, '/', or '.' so we don't match inside URLs/paths or mid-word — deliberately
     * stricter than snudown, which relies on its inline parser having already consumed bare URLs.
     *
     * <p>After the name, a '/' starts a trailing path of {@code [A-Za-z0-9_/-]} — a charset with no
     * '.', ':' or '?', so a path stops at a sentence-ending period, at a query string and at '://'.
     * Subreddit names join with '+' into a multireddit, and with '-' as well when the reference
     * starts with {@code all-} (the "/r/all minus these subs" syntax). Usernames have no length
     * ceiling, no '+' handling, and take their path characters straight after the first character —
     * but need at least two of them, since {@code sd_autolink__username} bails on {@code size < 3}
     * (snudown's own suite asserts {@code u/m} is not a link while {@code u/me} is).
     */
    private static final Pattern MENTION =
            Pattern.compile(
                    "(?<![A-Za-z0-9_/.])/?(?:r/(?:(?i:all-)"
                            + SUBREDDIT_NAME
                            + "(?:[-+]"
                            + SUBREDDIT_NAME
                            + ")*|"
                            + SUBREDDIT_NAME
                            + "(?:\\+"
                            + SUBREDDIT_NAME
                            + ")*)(?:/[A-Za-z0-9_/-]*)?"
                            + "|u/[A-Za-z0-9_-][A-Za-z0-9_/-]+)");

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
            final String matched = literal.substring(m.start(), m.end());
            final Link link = new Link(matched.charAt(0) == '/' ? matched : "/" + matched, null);
            link.appendChild(new Text(matched));
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
