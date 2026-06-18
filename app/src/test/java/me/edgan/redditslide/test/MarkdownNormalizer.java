package me.edgan.redditslide.test;

import android.text.Spanned;
import android.text.style.URLSpan;

import io.noties.markwon.core.spans.LinkSpan;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Normalizes a rendered markdown {@link Spanned} to a renderer-independent shape: the display text
 * plus an ordered list of semantic spans ({@code {type,start,end,url?}}). Identical logic to the
 * generator in the Continuum repo, so the two renderers can be compared structurally. Issue #179.
 */
final class MarkdownNormalizer {

    private MarkdownNormalizer() {}

    static JSONObject normalize(String id, String md, Spanned s) throws Exception {
        JSONObject o = new JSONObject();
        o.put("id", id);
        o.put("md", md);
        o.put("text", s.toString());

        List<JSONObject> spans = new ArrayList<>();
        for (Object span : s.getSpans(0, s.length(), Object.class)) {
            String type = mapType(span);
            if (type == null) {
                continue;
            }
            JSONObject js = new JSONObject();
            js.put("type", type);
            js.put("start", s.getSpanStart(span));
            js.put("end", s.getSpanEnd(span));
            if ("LINK".equals(type)) {
                js.put("url", normUrl(linkUrl(span)));
            }
            spans.add(js);
        }
        spans.sort(
                Comparator.comparingInt((JSONObject j) -> j.optInt("start"))
                        .thenComparingInt(j -> j.optInt("end"))
                        .thenComparing(j -> j.optString("type")));
        o.put("spans", new JSONArray(spans));
        return o;
    }

    /** Map a span instance to a renderer-independent semantic type, or null to ignore it. */
    static String mapType(Object span) {
        // Name checks come first: Slide's spoiler span subclasses URLSpan, so it must not be
        // mis-classified as a LINK.
        String n = span.getClass().getSimpleName();
        if (n.contains("Spoiler")) return "SPOILER";
        if (n.contains("Superscript")) return "SUPERSCRIPT";
        if (span instanceof URLSpan || span instanceof LinkSpan) {
            return "LINK";
        }
        switch (n) {
            case "StrongEmphasisSpan":
                return "BOLD";
            case "EmphasisSpan":
                return "ITALIC";
            case "StrikethroughSpan":
                return "STRIKETHROUGH";
            case "CodeSpan":
                return "CODE";
            case "CodeBlockSpan":
                return "CODE_BLOCK";
            case "HeadingSpan":
                return "HEADING";
            case "BlockQuoteSpan":
                return "BLOCKQUOTE";
            case "OrderedListItemSpan":
            case "BulletListItemSpan":
                return "LIST";
            default:
                return null; // colors, sizes, leading margins, etc. are rendering noise
        }
    }

    static String linkUrl(Object span) {
        if (span instanceof URLSpan) {
            return ((URLSpan) span).getURL();
        }
        if (span instanceof LinkSpan) {
            return ((LinkSpan) span).getLink();
        }
        return null;
    }

    /** Collapse reddit host variants so {@code /u/x} and {@code https://www.reddit.com/u/x} match. */
    static String normUrl(String u) {
        if (u == null) {
            return null;
        }
        for (String p :
                new String[] {
                    "https://www.reddit.com",
                    "https://reddit.com",
                    "https://old.reddit.com",
                    "https://oauth.reddit.com"
                }) {
            if (u.startsWith(p)) {
                return u.substring(p.length());
            }
        }
        return u;
    }
}
