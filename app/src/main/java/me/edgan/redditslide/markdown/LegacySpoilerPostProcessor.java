package me.edgan.redditslide.markdown;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import org.commonmark.node.AbstractVisitor;
import org.commonmark.node.Link;
import org.commonmark.node.Node;
import org.commonmark.node.Text;
import org.commonmark.parser.PostProcessor;

/**
 * Clean-room commonmark post-processor for Reddit's <em>legacy</em> CSS spoiler links, the syntax
 * that predates {@code >!…!<} and is still used by older subreddits (e.g. r/anime).
 *
 * <p>The canonical spoiler CSS (r/csshelp wiki §36, copied by most subs) styles a link as a spoiler
 * when its href is one of {@code /s}, {@code #s}, {@code /spoiler}, {@code #spoiler}; Slide's
 * snudown path also accepts {@code /sp}/{@code #sp}. Two shapes occur in the wild:
 *
 * <ul>
 *   <li>No title: {@code [hidden text](/s)} — the link text <em>is</em> the spoiler.
 *   <li>Title: {@code [teaser](/s "hidden text")} — the link text is a visible teaser that reads in
 *       the sentence, and the spoiler content lives in the link <em>title</em> (the r/anime
 *       convention). This mirrors the old {@code SubmissionParser.parseSpoilerTags} behavior.
 * </ul>
 *
 * <p>A plain fragment link such as {@code [trial](#does it work)} is not in the spoiler set and is
 * left as an ordinary link. See issue #179.
 */
public final class LegacySpoilerPostProcessor implements PostProcessor {

    // Exact-match the relative spoiler destinations (anchored), matching snudown's htmlSpoilerPattern
    // rather than the CSS "ends-with", so a real link to e.g. https://x/s is not swallowed.
    private static final Pattern SPOILER_DEST = Pattern.compile("^[/#](?:spoiler|sp|s)$");

    @Override
    public Node process(Node document) {
        final List<Link> targets = new ArrayList<>();
        document.accept(
                new AbstractVisitor() {
                    @Override
                    public void visit(Link link) {
                        String dest = link.getDestination();
                        if (dest != null && SPOILER_DEST.matcher(dest).matches()) {
                            targets.add(link);
                        }
                        visitChildren(link);
                    }
                });
        for (Link link : targets) {
            convert(link);
        }
        return document;
    }

    private static void convert(Link link) {
        String title = link.getTitle();
        if (title != null && !title.isEmpty()) {
            // Keep the link's visible text as plain text (it reads in the sentence), then add a
            // spoiler holding the title's hidden content.
            Node child = link.getFirstChild();
            boolean hadTeaser = child != null;
            while (child != null) {
                Node next = child.getNext();
                link.insertBefore(child); // moves child out as plain text, preserving order
                child = next;
            }
            if (hadTeaser) {
                // Separate the teaser from the spoiler block so they don't run together
                // ("Kaguya manga" + "the culture…" -> "Kaguya manga the culture…").
                link.insertBefore(new Text(" "));
            }
            SpoilerNode spoiler = new SpoilerNode();
            spoiler.appendChild(new Text(title));
            link.insertBefore(spoiler);
        } else {
            // The link text itself is the spoiler.
            SpoilerNode spoiler = new SpoilerNode();
            Node child = link.getFirstChild();
            while (child != null) {
                Node next = child.getNext();
                spoiler.appendChild(child);
                child = next;
            }
            link.insertBefore(spoiler);
        }
        link.unlink();
    }
}
