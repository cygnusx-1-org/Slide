package me.edgan.redditslide.Flair;

import androidx.annotation.Nullable;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.apache.commons.text.StringEscapeUtils;

/**
 * Reads Reddit's modern flair shape — {@code link_flair_richtext} and {@code author_flair_richtext}
 * — off a submission's or comment's raw data node.
 *
 * <p>Each element is a {@link Richtext} segment: {@code e:"text"} carries a run of text in {@code t},
 * {@code e:"emoji"} carries the emoji's alias in {@code a} and its image URL in {@code u}. JRAW's
 * {@link net.dean.jraw.models.Flair} only models the legacy {@code *_flair_text} /
 * {@code *_flair_css_class} pair, so without this the emoji never reach the UI and an emoji-only
 * flair renders as nothing at all.
 *
 * <p>The node is walked directly rather than deserialized. {@link Richtext} carries Gson
 * {@code @SerializedName} annotations while the flair picker deserializes with Jackson, and that
 * mismatch already drops fields silently elsewhere; reading the node keeps the two independent.
 */
public final class RichFlairParser {

    /** {@code data.link_flair_richtext} — the post's own flair. */
    public static final String LINK_FLAIR_RICHTEXT = "link_flair_richtext";

    /** {@code data.author_flair_richtext} — the flair beside the author's name. */
    public static final String AUTHOR_FLAIR_RICHTEXT = "author_flair_richtext";

    private RichFlairParser() {}

    /**
     * The richtext segments under {@code key}, or an empty list when the node is absent, is not an
     * array, or holds nothing usable. Never null, so callers can parse unconditionally on the feed's
     * hot path.
     */
    public static List<Richtext> from(@Nullable JsonNode dataNode, String key) {
        if (dataNode == null) {
            return Collections.emptyList();
        }
        return parse(dataNode.get(key));
    }

    /** Parses the richtext array itself. See {@link #from} for the usual entry point. */
    public static List<Richtext> parse(@Nullable JsonNode arrayNode) {
        if (arrayNode == null || !arrayNode.isArray() || arrayNode.size() == 0) {
            return Collections.emptyList();
        }

        final List<Richtext> segments = new ArrayList<>(arrayNode.size());

        for (JsonNode element : arrayNode) {
            if (element == null || !element.isObject()) {
                continue;
            }

            final String type = string(element, "e");

            // Reddit has only ever sent "text" and "emoji", but a segment with no type at all is
            // not renderable either way, so drop it rather than guess.
            if (type == null) {
                continue;
            }

            final Richtext segment = new Richtext();
            segment.setE(type);
            // "t" is already plain text in richtext (unlike link_flair_text, which is escaped), so
            // it is stored as-is and must not be run through fromHtml.
            segment.setT(string(element, "t"));
            segment.setA(string(element, "a"));
            segment.setU(unescape(string(element, "u")));

            segments.add(segment);
        }

        return segments;
    }

    /** Whether any segment is an emoji with an image to draw. */
    public static boolean hasEmoji(List<Richtext> segments) {
        for (Richtext segment : segments) {
            if (isEmoji(segment)) {
                return true;
            }
        }
        return false;
    }

    /** True when this segment is an emoji that carries a usable image URL. */
    public static boolean isEmoji(Richtext segment) {
        final String url = segment.getU();
        return "emoji".equals(segment.getE()) && url != null && !url.isEmpty();
    }

    /** Every emoji image URL, in order, for warming the image cache ahead of a bind. */
    public static List<String> emojiUrls(List<Richtext> segments) {
        final List<String> urls = new ArrayList<>();
        for (Richtext segment : segments) {
            if (isEmoji(segment)) {
                urls.add(segment.getU());
            }
        }
        return urls;
    }

    /**
     * The flair flattened to text: the text runs verbatim, and each emoji as its {@code :alias:}.
     * Used as the span's underlying string, so the flair still reads sensibly when copied, searched
     * or read aloud, and as the drawn fallback for an emoji whose image has not arrived.
     */
    public static String plainText(List<Richtext> segments) {
        final StringBuilder text = new StringBuilder();
        for (Richtext segment : segments) {
            text.append(segmentText(segment));
        }
        return text.toString();
    }

    /**
     * What to draw for one segment when there is no bitmap for it: its text, or its alias.
     *
     * <p>Keyed on the declared type rather than on {@link #isEmoji}, which additionally requires a
     * usable URL — an emoji segment that arrives without one still has an alias worth showing, and
     * checking the wrong thing here left it drawing nothing.
     */
    public static String segmentText(Richtext segment) {
        final boolean emoji = "emoji".equals(segment.getE());
        final String primary = emoji ? segment.getA() : segment.getT();

        if (primary != null) {
            return primary;
        }

        final String fallback = emoji ? segment.getT() : segment.getA();

        return fallback == null ? "" : fallback;
    }

    private static @Nullable String string(JsonNode node, String key) {
        final JsonNode value = node.get(key);
        return (value != null && !value.isNull()) ? value.asText() : null;
    }

    /**
     * Emoji URLs arrive HTML-escaped ({@code &amp;} in the query string). They are unescaped once,
     * here, so that the URL used to warm the cache and the URL used to read it back are byte
     * identical — a mismatch is a silent cache miss on every bind.
     */
    private static @Nullable String unescape(@Nullable String url) {
        return url == null ? null : StringEscapeUtils.unescapeHtml4(url);
    }
}
