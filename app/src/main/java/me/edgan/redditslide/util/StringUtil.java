package me.edgan.redditslide.util;

import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collections;
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
