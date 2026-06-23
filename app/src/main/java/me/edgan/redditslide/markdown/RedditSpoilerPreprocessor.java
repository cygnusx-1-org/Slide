package me.edgan.redditslide.markdown;

/**
 * Replaces Reddit spoiler delimiters {@code >!} and {@code !<} with private-use sentinel chars
 * <em>before</em> commonmark parsing, so a leading {@code >!} is not mistaken for a blockquote.
 * {@link SpoilerPostProcessor} later turns matched sentinel pairs into {@link SpoilerNode}s.
 *
 * <p>Fenced code blocks and inline code spans are skipped so spoiler-looking text inside code is
 * left untouched. See issue #179.
 */
public final class RedditSpoilerPreprocessor {

    static final char OPEN = '\uE000';
    static final char CLOSE = '\uE001';

    private RedditSpoilerPreprocessor() {}

    public static String sentinelize(String markdown) {
        if (markdown == null || markdown.indexOf('>') < 0) {
            return markdown; // spoilers require ">!"; nothing to do
        }
        String[] lines = markdown.split("\n", -1);
        boolean inFence = false;
        StringBuilder out = new StringBuilder(markdown.length());
        for (int li = 0; li < lines.length; li++) {
            String line = lines[li];
            String trimmed = line.trim();
            if (trimmed.startsWith("```") || trimmed.startsWith("~~~")) {
                inFence = !inFence;
                out.append(line);
            } else if (inFence) {
                out.append(line);
            } else {
                appendOutsideCode(line, out);
            }
            if (li < lines.length - 1) {
                out.append('\n');
            }
        }
        return out.toString();
    }

    private static void appendOutsideCode(String line, StringBuilder out) {
        int i = 0;
        final int n = line.length();
        while (i < n) {
            char c = line.charAt(i);
            if (c == '\\') {
                // A backslash escapes the next char, so \>! is a literal ">!" (not a spoiler open)
                // and \!< a literal "!<" — matching commonmark, which strips the backslash when it
                // renders the escaped punctuation. Emit both verbatim and skip past the escaped
                // char so it can't start/close a sentinel. (A lone trailing "\" is emitted as-is.)
                out.append(c);
                if (i + 1 < n) {
                    out.append(line.charAt(i + 1));
                    i += 2;
                } else {
                    i++;
                }
            } else if (c == '`') {
                int tickStart = i;
                int ticks = 0;
                while (i < n && line.charAt(i) == '`') {
                    ticks++;
                    i++;
                }
                int close = findClosingTicks(line, i, ticks);
                if (close >= 0) {
                    out.append(line, tickStart, close); // emit code span verbatim
                    i = close;
                } else {
                    out.append(line, tickStart, i); // unmatched backticks, emit as-is
                }
            } else if (c == '>' && i + 1 < n && line.charAt(i + 1) == '!') {
                out.append(OPEN);
                i += 2;
            } else if (c == '!' && i + 1 < n && line.charAt(i + 1) == '<') {
                out.append(CLOSE);
                i += 2;
            } else {
                out.append(c);
                i++;
            }
        }
    }

    /** Index just past a closing run of exactly {@code ticks} backticks, or -1 if none. */
    private static int findClosingTicks(String line, int from, int ticks) {
        int i = from;
        final int n = line.length();
        while (i < n) {
            if (line.charAt(i) == '`') {
                int run = 0;
                while (i < n && line.charAt(i) == '`') {
                    run++;
                    i++;
                }
                if (run == ticks) {
                    return i;
                }
            } else {
                i++;
            }
        }
        return -1;
    }
}
