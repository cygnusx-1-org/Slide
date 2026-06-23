package me.edgan.redditslide.markdown;

/**
 * Merges blockquote paragraphs that are separated only by blank lines into a single CommonMark
 * blockquote, so the Markwon renderer draws one continuous stripe — matching Reddit's snudown
 * {@code body_html} rendering.
 *
 * <p>Authors commonly write a multi-paragraph quote as:
 *
 * <pre>
 * &gt;para one
 *
 * &gt;para two
 * </pre>
 *
 * CommonMark ends a blockquote at the blank line, so this parses as two separate blockquotes and
 * Markwon draws a segmented stripe (one short bar per paragraph with gaps). snudown instead renders
 * the whole thing as one quote with a single unbroken bar. To reproduce that, a blank line sitting
 * <em>between</em> two blockquote lines is rewritten to a {@code >} continuation line, turning the
 * run into one blockquote with multiple paragraphs.
 *
 * <p>Only blank lines flanked by quote lines are touched: a blockquote followed by ordinary text is
 * left alone (its quote correctly ends), and fenced code blocks are skipped so their blank lines and
 * any {@code >}-looking content are never rewritten. Run this <em>after</em>
 * {@link RedditSpoilerPreprocessor#sentinelize} so spoiler {@code >!} lines (already replaced with a
 * sentinel) are not mistaken for blockquotes. See issue #179.
 */
public final class BlockquoteNormalizer {

    private BlockquoteNormalizer() {}

    public static String mergeAdjacentBlockquotes(String markdown) {
        if (markdown == null || markdown.indexOf('>') < 0) {
            return markdown; // no blockquote marker, nothing to merge
        }
        String[] lines = markdown.split("\n", -1);
        boolean inFence = false;
        boolean prevContentIsQuote = false;
        StringBuilder out = new StringBuilder(markdown.length());
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            String trimmed = line.trim();
            boolean isFenceMarker = trimmed.startsWith("```") || trimmed.startsWith("~~~");

            if (isFenceMarker) {
                inFence = !inFence;
                out.append(line);
                prevContentIsQuote = false;
            } else if (inFence) {
                out.append(line);
                // content inside a fence never counts as a quote boundary
            } else if (trimmed.isEmpty()) {
                if (prevContentIsQuote && nextContentIsQuote(lines, i + 1)) {
                    out.append('>'); // bridge the gap so the quote stays one block
                } else {
                    out.append(line);
                }
                // a blank/bridged line is not itself a content line; leave prevContentIsQuote as-is
            } else {
                out.append(line);
                prevContentIsQuote = isQuoteLine(trimmed);
            }
            if (i < lines.length - 1) {
                out.append('\n');
            }
        }
        return out.toString();
    }

    /** A blockquote line: {@code >...} possibly after the up-to-3 leading spaces CommonMark allows. */
    private static boolean isQuoteLine(String trimmed) {
        return trimmed.startsWith(">");
    }

    /**
     * Whether the next non-blank line (scanning forward from {@code from}) is a blockquote line,
     * stopping at a fence marker (a code block is not part of the quote).
     */
    private static boolean nextContentIsQuote(String[] lines, int from) {
        for (int i = from; i < lines.length; i++) {
            String trimmed = lines[i].trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            if (trimmed.startsWith("```") || trimmed.startsWith("~~~")) {
                return false;
            }
            return isQuoteLine(trimmed);
        }
        return false;
    }
}
