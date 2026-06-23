package me.edgan.redditslide.markdown;

import android.view.View;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import me.edgan.redditslide.SpoilerRobotoTextView;
import me.edgan.redditslide.Views.CommentOverflow;
import me.edgan.redditslide.util.SubmissionParser;
import org.apache.commons.text.StringEscapeUtils;

/**
 * Renders a comment/self-text's images (emotes, giphy reactions, inline images) for the new
 * Reddit-style renderer.
 *
 * <p>The raw markdown only references media (a bare {@code preview.redd.it} URL, or {@code
 * ![img](id)} resolved via {@code media_metadata}); the usable URLs live in {@code body_html}. So
 * we reuse Slide's existing resolution ({@link SubmissionParser#replaceProcessingImgPlaceholders}
 * + image-block extraction) and draw the images through the same pre-sized {@link CommentOverflow}
 * path the snudown renderer uses, while stripping the media references out of the Markwon text.
 * See issue #179.
 */
public final class MarkdownImages {

    private MarkdownImages() {}

    private static final Pattern MEDIA_URL =
            Pattern.compile(
                    "https://(?:preview\\.redd\\.it|i\\.redd\\.it|external-preview\\.redd\\.it"
                            + "|i\\.giphy\\.com)/\\S+");

    /** A free emote reference: {@code ![alt](emote|free_emotes_pack|name)}. Group 1 = full id. */
    private static final Pattern EMOTE_REF =
            Pattern.compile("!\\[[^\\]]*\\]\\((emote\\|[^)\\s]+)\\)");

    private static final char PLACEHOLDER = '￼'; // object replacement character

    /** Result of {@link #resolveEmotes}: the rewritten markdown and the ordered emote URLs. */
    public static final class EmoteResolution {
        public final String markdown;
        public final List<String> urls;

        EmoteResolution(String markdown, List<String> urls) {
            this.markdown = markdown;
            this.urls = urls;
        }
    }

    /**
     * Render {@code rawMarkdown} as new Reddit-style text into {@code textView} (media references
     * removed, free emotes loaded inline) and draw its resolved images into {@code overflow}.
     */
    public static void renderInto(
            SpoilerRobotoTextView textView,
            CommentOverflow overflow,
            String subreddit,
            String rawMarkdown,
            String bodyHtml,
            JsonNode dataNode) {
        rawMarkdown = unescapeTransportEntities(rawMarkdown);
        EmoteResolution emotes = resolveEmotes(rawMarkdown, dataNode);
        String text = stripMediaUrls(emotes.markdown);
        boolean rendered = false;
        if (!text.trim().isEmpty()) {
            RedditMarkwon.setMarkdown(textView, subreddit, text);
            textView.loadFreeEmotes(emotes.urls);
            // The markdown may be image-only (e.g. a lone giphy reaction): its image nodes are
            // dropped and the gif is drawn from body_html, leaving the text empty. Hide the view
            // in that case so there's no blank gap above the image.
            rendered = textView.getText() != null && textView.getText().length() > 0;
        }
        if (rendered) {
            textView.setVisibility(View.VISIBLE);
        } else {
            textView.setText("");
            textView.setVisibility(View.GONE);
        }
        render(overflow, bodyHtml, dataNode, subreddit);
    }

    /**
     * Replace each resolvable free-emote reference with a {@code ￼} placeholder and collect the
     * emote image URLs (in order) from {@code media_metadata}. Reddit keys emotes by an id like
     * {@code emote|free_emotes_pack|upvote} whose real gif filename differs, so the URL must come
     * from {@code media_metadata} — never constructed from the name.
     */
    public static EmoteResolution resolveEmotes(String rawMarkdown, JsonNode dataNode) {
        List<String> urls = new ArrayList<>();
        if (rawMarkdown == null || rawMarkdown.isEmpty() || rawMarkdown.indexOf("emote|") < 0) {
            return new EmoteResolution(rawMarkdown == null ? "" : rawMarkdown, urls);
        }
        JsonNode mediaMetadata =
                dataNode != null && dataNode.has("media_metadata")
                        ? dataNode.get("media_metadata")
                        : null;
        Matcher m = EMOTE_REF.matcher(rawMarkdown);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            String url = mediaMetadata == null ? null : emoteUrl(mediaMetadata.get(m.group(1)));
            if (url != null) {
                urls.add(url);
                m.appendReplacement(sb, String.valueOf(PLACEHOLDER));
            } else {
                m.appendReplacement(sb, Matcher.quoteReplacement(m.group(0)));
            }
        }
        m.appendTail(sb);
        return new EmoteResolution(sb.toString(), urls);
    }

    private static String emoteUrl(JsonNode entry) {
        if (entry == null) {
            return null;
        }
        JsonNode s = entry.get("s");
        if (s == null) {
            return null;
        }
        if (s.hasNonNull("gif")) {
            return s.get("gif").asText();
        }
        if (s.hasNonNull("u")) {
            return s.get("u").asText();
        }
        return null;
    }

    /**
     * Undo Reddit's transport-level HTML escaping of the raw {@code body}/{@code selftext}.
     *
     * <p>Slide fetches these fields <em>without</em> {@code raw_json=1}, so Reddit html-escapes them
     * and an author's {@code >} (blockquote marker) arrives as {@code &gt;}, {@code <} as {@code
     * &lt;}, {@code &} as {@code &amp;}, etc. commonmark decides block structure (blockquotes,
     * spoilers) <em>before</em> it decodes inline entities, so {@code &gt;text} is never recognized
     * as a blockquote — the inline pass later decodes it to a literal leading {@code >}. Decoding
     * here, before parsing, lets the block parser (and {@link RedditSpoilerPreprocessor}, which
     * matches a literal {@code >!}) see the real markers.
     *
     * <p>This is a single pass: {@code &amp;gt;} (an author who typed the literal text {@code &gt;})
     * decodes to {@code &gt;}, not {@code >}. Markdown-level entities the author actually intended
     * are still handled afterwards by commonmark's own inline entity decoding, so the result matches
     * the snudown {@code body_html} pipeline. See issue #179.
     */
    public static String unescapeTransportEntities(String rawMarkdown) {
        if (rawMarkdown == null || rawMarkdown.indexOf('&') < 0) {
            return rawMarkdown; // no entity to decode
        }
        return StringEscapeUtils.unescapeHtml4(rawMarkdown);
    }

    /** Remove standalone Reddit media URLs (they are rendered as image blocks instead). */
    public static String stripMediaUrls(String markdown) {
        if (markdown == null) {
            return "";
        }
        if (!markdown.contains("redd.it") && !markdown.contains("giphy")) {
            return markdown; // fast path: no media URL to strip
        }
        return MEDIA_URL.matcher(markdown).replaceAll("");
    }

    /**
     * Populate {@code overflow} with the image blocks resolved from {@code bodyHtml}; clears it if
     * there are none.
     */
    public static void render(
            CommentOverflow overflow, String bodyHtml, JsonNode dataNode, String subreddit) {
        if (overflow == null) {
            return;
        }
        if (bodyHtml == null || bodyHtml.isEmpty()) {
            overflow.removeAllViews();
            return;
        }
        // Fast path: no media host URL and no media_metadata to resolve a placeholder from, so
        // there are no image blocks — skip the (regex-heavy) getBlocks parse entirely.
        if (!bodyHtml.contains("redd.it")
                && !bodyHtml.contains("giphy")
                && (dataNode == null || !dataNode.has("media_metadata"))) {
            overflow.removeAllViews();
            return;
        }
        String resolved = SubmissionParser.replaceProcessingImgPlaceholders(bodyHtml, dataNode);
        List<String> blocks =
                SubmissionParser.extractImageBlocks(SubmissionParser.getBlocks(resolved));
        List<String> images = new ArrayList<>();
        for (String block : blocks) {
            if (block.startsWith(SubmissionParser.IMAGE_BLOCK_PREFIX)) {
                images.add(block);
            }
        }
        if (images.isEmpty()) {
            overflow.removeAllViews();
        } else {
            overflow.setViews(images, subreddit);
        }
    }
}
