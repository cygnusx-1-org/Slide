package me.edgan.redditslide.util;

import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.regex.Pattern;
import org.jspecify.annotations.NullMarked;

/** Created by TacoTheDank on 03/15/2021. */
@NullMarked
public class StringUtil {
    public static String arrayToString(@Nullable final ArrayList<String> array) {
        return arrayToStringInternal(array, ",", 1);
    }

    public static String arrayToString(
            @Nullable final ArrayList<String> array, final String separator) {
        return arrayToStringInternal(array, separator, separator.length());
    }

    private static String arrayToStringInternal(
            @Nullable final ArrayList<String> array,
            final String separator,
            final int separatorLength) {
        if (array != null) {
            final StringBuilder b = new StringBuilder();
            for (String s : array) {
                b.append(s).append(separator);
            }
            String f = b.toString();
            if (f.length() > 0) {
                f = f.substring(0, f.length() - separatorLength);
            }
            return f;
        }
        return "";
    }

    public static ArrayList<String> stringToArray(final String string) {
        final ArrayList<String> f = new ArrayList<>();
        Collections.addAll(f, string.split(","));
        return f;
    }

    public static String abbreviate(final String str, final int maxWidth) {
        if (str.length() <= maxWidth) {
            return str;
        }

        final String abrevMarker = "...";
        return str.substring(0, maxWidth - 3) + abrevMarker;
    }

    public static String stripAllWhitespace(@Nullable final String input) {
        if (input == null) {
            return "";
        }
        return input.replaceAll("\\s", "");
    }

    public static String stripLeadingTrailingWhitespace(@Nullable final String input) {
        if (input == null) {
            return "";
        }
        return input.trim();
    }

    /** Html tags and comments, for asking whether a fragment still has text in it. */
    private static final Pattern TAGS = Pattern.compile("<[^>]*>");

    /**
     * A reddit-hosted image linked from a post body. Snudown renders a pasted image as a link to
     * it, so it arrives as an anchor whose href names one of reddit's image hosts — matching
     * {@code preview.redd.it} also covers {@code external-preview.redd.it}.
     */
    private static final String IMAGE_HOST = "(?:preview\\.redd\\.it|i\\.redd\\.it)";

    private static final Pattern IMAGE_ANCHOR =
            Pattern.compile("<a\\b[^>]*" + IMAGE_HOST + "[^>]*>(.*?)</a>", Pattern.DOTALL);

    /**
     * The same anchor with no {@code </a>}: {@link #ellipsizeHtml} cuts at a character budget
     * without closing what it opened, so a truncated image link arrives as an opening tag and
     * whatever of its text fitted.
     */
    private static final Pattern IMAGE_ANCHOR_UNCLOSED =
            Pattern.compile("<a\\b[^>]*" + IMAGE_HOST + "[^>]*>.*", Pattern.DOTALL);

    /**
     * A bare image url. Bounded by the markup characters as well as by whitespace: a url inside an
     * attribute has no space before the quote that ends it, so a {@code \\S*} run would swallow the
     * rest of the tag and the elements after it.
     */
    private static final Pattern IMAGE_URL =
            Pattern.compile("https?://[^\\s<>\"']*" + IMAGE_HOST + "[^\\s<>\"']*");

    /**
     * {@code html} with every link to a reddit-hosted image removed. The card draws such an image
     * as the post's lead image, and the body would otherwise draw the same picture again right
     * underneath it.
     */
    public static String withoutRedditImageLinks(final String html) {
        // The anchor gives up its text, not its whole self: an image reddit pasted in is linked
        // under its own url and that text goes with it below, but an author who wrote
        // "[this](url)" put a word in the middle of a sentence, and deleting the word leaves the
        // sentence broken.
        String stripped = IMAGE_ANCHOR.matcher(html).replaceAll("$1");
        stripped = IMAGE_ANCHOR_UNCLOSED.matcher(stripped).replaceAll("");
        return IMAGE_URL.matcher(stripped).replaceAll("");
    }

    /**
     * Whether {@code html} carries nothing a reader would see except links to reddit-hosted images
     * — the shape of a body whose first line is an image the author pasted on its own. The ellipsis
     * counts as nothing: it only marks where a link was cut.
     */
    public static boolean isOnlyRedditImageLinks(final String html) {
        final String text =
                TAGS.matcher(withoutRedditImageLinks(html))
                        .replaceAll("")
                        .replace("&nbsp;", " ")
                        .replace("\u00a0", " ")
                        .replace("\u2026", "");
        return text.trim().isEmpty();
    }

    /**
     * Cuts an html fragment down to its first {@code maxChars} characters of text and appends an
     * ellipsis, or returns it whole when the text is no longer than that. Only text is counted:
     * tags contribute nothing, an entity such as {@code &#39;} is the one character it stands for
     * and a surrogate pair is one character, so the cut can never split any of them. Whitespace
     * left at the cut is dropped so the ellipsis sits against the last word.
     */
    public static String ellipsizeHtml(final String html, final int maxChars) {
        final int n = html.length();
        int chars = 0;
        int cutAt = 0; // just past the last counted character
        int i = 0;
        while (i < n) {
            final char c = html.charAt(i);
            if (c == '<') {
                final int end = html.indexOf('>', i);
                if (end < 0) break;
                i = end + 1;
            } else if (chars == maxChars) {
                // Text past the limit: the cut stands.
                while (cutAt > 0 && Character.isWhitespace(html.charAt(cutAt - 1))) cutAt--;
                return html.substring(0, cutAt) + "\u2026";
            } else {
                if (c == '&') {
                    i = skipEntity(html, i);
                } else if (Character.isHighSurrogate(c)
                        && i + 1 < n
                        && Character.isLowSurrogate(html.charAt(i + 1))) {
                    i += 2;
                } else {
                    i++;
                }
                chars++;
                cutAt = i;
            }
        }
        return html;
    }

    /** Index just past the entity starting at {@code i}, or {@code i + 1} if it isn't one. */
    private static int skipEntity(final String html, final int i) {
        final int end = html.indexOf(';', i);
        // Entities are short; a far-off ';' means this '&' is a bare ampersand in the text.
        return end > i && end - i <= 10 ? end + 1 : i + 1;
    }

    public static String sanitizeString(final String input) {
        final char[] allowed =
                "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ-_".toCharArray();
        final char[] charArray = input.toCharArray();
        StringBuilder result = new StringBuilder();
        for (final char c : charArray) {
            for (final char a : allowed) {
                if (c == a) result.append(a);
            }
        }
        return result.toString();
    }
}
