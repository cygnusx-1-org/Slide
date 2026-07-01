package me.edgan.redditslide.util;

import android.text.Spanned;

import androidx.annotation.NonNull;
import androidx.core.text.HtmlCompat;

import org.apache.commons.text.StringEscapeUtils;

/** Created by TacoTheDank on 04/22/2021. */
public class CompatUtil {
    public static Spanned fromHtml(@NonNull String source) {
        if (source == null) {
            return HtmlCompat.fromHtml("", HtmlCompat.FROM_HTML_MODE_LEGACY);
        }
        return HtmlCompat.fromHtml(source, HtmlCompat.FROM_HTML_MODE_LEGACY);
    }

    /**
     * Converts Reddit's entity-escaped HTML (e.g. {@code body_html} / {@code selftext_html}) into
     * readable plain text, resolving markdown formatting (bold, links, lists) to spoken/translatable
     * text instead of leaving raw markdown syntax. Returns an empty string for null/blank input.
     */
    public static String htmlToText(String escapedHtml) {
        if (escapedHtml == null || escapedHtml.isEmpty()) {
            return "";
        }
        // body_html arrives HTML-entity-escaped; unescape once so fromHtml parses the tags.
        return fromHtml(StringEscapeUtils.unescapeHtml4(escapedHtml)).toString().trim();
    }
}
