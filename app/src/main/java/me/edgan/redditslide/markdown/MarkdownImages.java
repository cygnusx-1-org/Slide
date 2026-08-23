package me.edgan.redditslide.markdown;

import android.content.Context;
import android.text.Spanned;
import android.view.View;
import androidx.annotation.Nullable;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import me.edgan.redditslide.SettingValues;
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

    /**
     * A video uploaded through Reddit's comment composer. Slide draws it as a card from {@code
     * body_html}, so the reference is taken out of the Markwon text: an {@code ![caption](url)}
     * form is already dropped as a markdown image node, but a pasted bare url would otherwise be
     * linkified and printed above the card.
     */
    private static final Pattern COMMENT_VIDEO_URL =
            Pattern.compile(
                    "https://(?:www\\.)?reddit\\.com/link/[^/\\s]+/video/[^/\\s]+/player"
                            + "(?:\\?\\S*)?");

    /** A giphy reaction link, which {@link SubmissionParser} rewrites into an image block. */
    private static final Pattern GIPHY_LINK = Pattern.compile("https://giphy\\.com/gifs/\\S+");

    /**
     * Anything in the raw markdown that may be a media reference: a markdown image or link node
     * (whose target still has to be classified by {@link #isMediaTarget}, group 1), an unprocessed
     * inline-image placeholder, or a bare media / giphy / comment-video url. Ordered with the node
     * form first so a url inside {@code ![alt](url)} is consumed as part of the node.
     */
    private static final Pattern MARKDOWN_MEDIA_REF =
            Pattern.compile(
                    "!?\\[[^\\]]*\\]\\(([^)\\s]*)\\)"
                            + "|\\*?Processing img \\w+\\.{3}\\*?"
                            + "|https://(?:preview\\.redd\\.it|i\\.redd\\.it"
                            + "|external-preview\\.redd\\.it|i\\.giphy\\.com)/\\S+"
                            + "|https://giphy\\.com/gifs/\\S+"
                            + "|(https://(?:www\\.)?reddit\\.com/link/[^/\\s]+/video/[^/\\s]+"
                            + "/player(?:\\?\\S*)?)");

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
            @Nullable CommentOverflow overflow,
            String subreddit,
            @Nullable String rawMarkdown,
            @Nullable String bodyHtml,
            @Nullable JsonNode dataNode) {
        renderInto(textView, overflow, subreddit, rawMarkdown, bodyHtml, dataNode, null);
    }

    /**
     * As {@link #renderInto}, additionally highlighting every occurrence of {@code searchTerm} in
     * the rendered text (the new Reddit-style equivalent of the snudown {@code [[h[…]h]]} search
     * highlight, which can't run here because the text comes from raw markdown). Pass {@code null}
     * or empty for no highlighting.
     */
    public static void renderInto(
            SpoilerRobotoTextView textView,
            @Nullable CommentOverflow overflow,
            String subreddit,
            @Nullable String rawMarkdown,
            @Nullable String bodyHtml,
            @Nullable JsonNode dataNode,
            @Nullable String searchTerm) {
        renderPrepared(
                textView,
                overflow,
                subreddit,
                prepare(textView.getContext(), rawMarkdown, bodyHtml, dataNode),
                searchTerm);
    }

    /**
     * One piece of a prepared body: either a run of parsed text or a single media block, in the
     * order the author wrote them.
     */
    public static final class Segment {
        /** The parsed text of this segment, or {@code null} when this is a media block. */
        @Nullable public final Spanned text;

        /**
         * An {@link SubmissionParser#IMAGE_BLOCK_PREFIX} / {@link SubmissionParser#VIDEO_BLOCK_PREFIX}
         * block, or {@code null} when this is a text segment.
         */
        @Nullable public final String mediaBlock;

        /** The free-emote urls whose {@code \uFFFC} placeholders fall inside {@link #text}. */
        public final List<String> emoteUrls;

        private Segment(
                @Nullable Spanned text, @Nullable String mediaBlock, List<String> emoteUrls) {
            this.text = text;
            this.mediaBlock = mediaBlock;
            this.emoteUrls = emoteUrls;
        }

        static Segment ofText(Spanned text, List<String> emoteUrls) {
            return new Segment(text, null, emoteUrls);
        }

        static Segment ofMedia(String mediaBlock) {
            return new Segment(null, mediaBlock, Collections.emptyList());
        }
    }

    /**
     * The view-independent half of {@link #renderInto}: a body split into its text runs and media
     * blocks, in source order. All of it is a pure function of the raw markdown, the body html and
     * the data node, so a caller rendering the same comment repeatedly can cache this rather than
     * re-parsing on every bind.
     *
     * <p>{@link #text} is the leading text run, held apart from the rest because the comment layouts
     * have a dedicated first TextView above the {@link CommentOverflow}; it is {@code null} when the
     * body opens with media (or is entirely media/whitespace), in which case that view is hidden and
     * everything is drawn by the overflow. {@link #overflow} is whatever follows, still in order.
     */
    public static final class Prepared {
        /** The rendered leading text, or {@code null} when the body does not open with text. */
        @Nullable public final Spanned text;

        /** The free-emote urls belonging to {@link #text}. */
        public final List<String> emoteUrls;

        /** Everything after the leading text run, in source order. */
        public final List<Segment> overflow;

        Prepared(@Nullable Spanned text, List<String> emoteUrls, List<Segment> overflow) {
            this.text = text;
            this.emoteUrls = emoteUrls;
            this.overflow = overflow;
        }
    }

    /**
     * Resolve and parse {@code rawMarkdown}, interleaving it with the media blocks resolved from
     * {@code bodyHtml}; see {@link Prepared}.
     *
     * <p>The media has to come from the html — the raw markdown only references it, and an
     * {@code ![img](id)} reference is only resolvable through {@code media_metadata} — but the
     * <em>order</em> is the author's, so the two are zipped: the media references found in the
     * markdown are matched one-for-one, in order, with the blocks {@link SubmissionParser} lifted
     * out of the html. If the two disagree on how many there are, the body is laid out the old way
     * (all text, then all media) rather than risk pairing the wrong things.
     */
    public static Prepared prepare(
            Context context,
            @Nullable String rawMarkdown,
            @Nullable String bodyHtml,
            @Nullable JsonNode dataNode) {
        final boolean skipImages = SettingValues.shouldSkipImages(context);
        rawMarkdown = unescapeTransportEntities(rawMarkdown);
        EmoteResolution emotes = resolveEmotes(rawMarkdown, dataNode);
        List<String> media = mediaBlocksFor(bodyHtml, dataNode, skipImages);

        List<int[]> refs = mediaRefsIn(emotes.markdown, dataNode, skipImages);
        if (!media.isEmpty() && refs.size() == media.size()) {
            return interleave(context, emotes, media, refs);
        }

        // No media, or the markdown and the html disagree: fall back to the original layout.
        String text = stripMediaUrls(emotes.markdown);
        // Under data saving no card is drawn, so the link has to stay as the only way in.
        if (!skipImages) {
            text = stripCommentVideoUrls(text);
        }
        Spanned rendered = text.trim().isEmpty() ? null : RedditMarkwon.toMarkdown(context, text);
        List<Segment> overflow = new ArrayList<>(media.size());
        for (String block : media) {
            overflow.add(Segment.ofMedia(block));
        }
        return new Prepared(rendered, rendered == null ? Collections.emptyList() : emotes.urls, overflow);
    }

    /**
     * Builds the ordered segments for a body whose markdown references line up with {@code media}.
     * The text between two references becomes its own segment (dropped when it is only whitespace),
     * and each reference is replaced by the block it resolved to.
     */
    private static Prepared interleave(
            Context context, EmoteResolution emotes, List<String> media, List<int[]> refs) {
        final String markdown = emotes.markdown;
        List<Segment> segments = new ArrayList<>(refs.size() * 2 + 1);
        int last = 0;
        int emoteFrom = 0;
        for (int i = 0; i < refs.size(); i++) {
            int[] ref = refs.get(i);
            String between = markdown.substring(last, ref[0]);
            emoteFrom = addTextSegment(context, segments, between, emotes.urls, emoteFrom);
            segments.add(Segment.ofMedia(media.get(i)));
            last = ref[1];
        }
        addTextSegment(context, segments, markdown.substring(last), emotes.urls, emoteFrom);

        // The leading run, if the body opens with text, goes in the dedicated first TextView.
        if (!segments.isEmpty() && segments.get(0).text != null) {
            Segment first = segments.remove(0);
            return new Prepared(first.text, first.emoteUrls, segments);
        }
        return new Prepared(null, Collections.emptyList(), segments);
    }

    /**
     * Parses {@code source} and appends it as a text segment, unless it is blank. Returns the index
     * of the next unconsumed emote url: the emotes belonging to a segment are the ones whose
     * placeholders it contains, and the placeholders are in the same order as {@code emoteUrls}.
     */
    private static int addTextSegment(
            Context context,
            List<Segment> segments,
            String source,
            List<String> emoteUrls,
            int emoteFrom) {
        int placeholders = 0;
        for (int i = 0; i < source.length(); i++) {
            if (source.charAt(i) == PLACEHOLDER) {
                placeholders++;
            }
        }
        int emoteTo = Math.min(emoteUrls.size(), emoteFrom + placeholders);
        if (!source.trim().isEmpty()) {
            Spanned parsed = RedditMarkwon.toMarkdown(context, source);
            if (parsed.length() > 0) {
                segments.add(
                        Segment.ofText(
                                parsed,
                                emoteFrom >= emoteTo
                                        ? Collections.emptyList()
                                        : new ArrayList<>(emoteUrls.subList(emoteFrom, emoteTo))));
            }
        }
        return emoteTo;
    }

    /** Draw an already-{@link #prepare}d comment into {@code textView} and {@code overflow}. */
    public static void renderPrepared(
            SpoilerRobotoTextView textView,
            @Nullable CommentOverflow overflow,
            String subreddit,
            Prepared prepared,
            @Nullable String searchTerm) {
        boolean rendered = false;
        if (prepared.text != null) {
            RedditMarkwon.setParsedMarkdown(textView, subreddit, prepared.text);
            textView.loadFreeEmotes(prepared.emoteUrls);
            if (searchTerm != null && !searchTerm.isEmpty()) {
                textView.highlightOccurrences(searchTerm, subreddit);
            }
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
        renderSegments(overflow, subreddit, prepared.overflow, searchTerm);
    }

    /**
     * Draws {@code segments} into {@code overflow} in order, so media sits where the author put it
     * rather than always below the text.
     */
    private static void renderSegments(
            @Nullable CommentOverflow overflow,
            String subreddit,
            List<Segment> segments,
            @Nullable String searchTerm) {
        if (overflow == null) {
            return;
        }
        if (segments.isEmpty()) {
            // Nothing to draw: clear the previous bind without paying for beginBlocks()' font and
            // theme lookups. Most comments have no media, and this runs on every one of them.
            overflow.removeAllViews();
            return;
        }
        overflow.beginBlocks();
        overflow.setVisibility(View.VISIBLE);
        for (Segment segment : segments) {
            if (segment.mediaBlock != null) {
                overflow.addBlock(segment.mediaBlock, subreddit, null, null);
            } else if (segment.text != null) {
                SpoilerRobotoTextView view = overflow.addTextBlock(subreddit, null, null);
                RedditMarkwon.setParsedMarkdown(view, subreddit, segment.text);
                view.loadFreeEmotes(segment.emoteUrls);
                if (searchTerm != null && !searchTerm.isEmpty()) {
                    view.highlightOccurrences(searchTerm, subreddit);
                }
            }
        }
    }

    /**
     * Replace each resolvable free-emote reference with a {@code ￼} placeholder and collect the
     * emote image URLs (in order) from {@code media_metadata}. Reddit keys emotes by an id like
     * {@code emote|free_emotes_pack|upvote} whose real gif filename differs, so the URL must come
     * from {@code media_metadata} — never constructed from the name.
     */
    public static EmoteResolution resolveEmotes(
            @Nullable String rawMarkdown, @Nullable JsonNode dataNode) {
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

    @Nullable
    private static String emoteUrl(@Nullable JsonNode entry) {
        if (entry == null) {
            return null;
        }
        JsonNode s = entry.get("s");
        if (s == null) {
            return null;
        }
        if (s.hasNonNull("gif")) {
            return s.path("gif").asText();
        }
        if (s.hasNonNull("u")) {
            return s.path("u").asText();
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
    @Nullable
    public static String unescapeTransportEntities(@Nullable String rawMarkdown) {
        if (rawMarkdown == null || rawMarkdown.indexOf('&') < 0) {
            return rawMarkdown; // no entity to decode
        }
        return StringEscapeUtils.unescapeHtml4(rawMarkdown);
    }

    /** Remove standalone Reddit media URLs (they are rendered as image blocks instead). */
    public static String stripMediaUrls(@Nullable String markdown) {
        if (markdown == null) {
            return "";
        }
        if (!markdown.contains("redd.it") && !markdown.contains("giphy")) {
            return markdown; // fast path: no media URL to strip
        }
        return MEDIA_URL.matcher(markdown).replaceAll("");
    }

    /** Remove comment-video links (they are rendered as video cards instead). */
    public static String stripCommentVideoUrls(@Nullable String markdown) {
        if (markdown == null) {
            return "";
        }
        if (!markdown.contains("/video/")) {
            return markdown; // fast path: no comment video to strip
        }
        return COMMENT_VIDEO_URL.matcher(markdown).replaceAll("");
    }

    /**
     * The media blocks {@code bodyHtml} resolves to, in order. Data saving drops the video blocks so
     * the plain link survives, matching the snudown renderer's gate in
     * {@code CommentAdapter.computeBlocks}.
     */
    private static List<String> mediaBlocksFor(
            @Nullable String bodyHtml, @Nullable JsonNode dataNode, boolean skipImages) {
        List<String> media = new ArrayList<>();
        if (bodyHtml == null || bodyHtml.isEmpty()) {
            return media;
        }
        // Fast path: no media host URL and no media_metadata to resolve a placeholder from, so
        // there are no image blocks — skip the (regex-heavy) getBlocks parse entirely. A comment
        // video links to "reddit.com/link/...", which does not contain the substring "redd.it", so
        // it needs its own test here or every video-only comment is dropped before parsing.
        if (!bodyHtml.contains("redd.it")
                && !bodyHtml.contains("giphy")
                && !bodyHtml.contains("reddit.com/link/")
                && (dataNode == null || !dataNode.has("media_metadata"))) {
            return media;
        }
        String resolved = SubmissionParser.replaceProcessingImgPlaceholders(bodyHtml, dataNode);
        for (String block :
                SubmissionParser.extractImageBlocks(SubmissionParser.getBlocks(resolved))) {
            if (block.startsWith(SubmissionParser.IMAGE_BLOCK_PREFIX)
                    || (!skipImages && block.startsWith(SubmissionParser.VIDEO_BLOCK_PREFIX))) {
                media.add(block);
            }
        }
        return media;
    }

    /**
     * Every reference in {@code markdown} that {@link #mediaBlocksFor} would have turned into a
     * block, as {start, end} offsets in source order. Free emotes are deliberately not among them:
     * they stay inline in the text as {@code \uFFFC} placeholders.
     */
    private static List<int[]> mediaRefsIn(
            String markdown, @Nullable JsonNode dataNode, boolean skipImages) {
        List<int[]> refs = new ArrayList<>();
        Matcher m = MARKDOWN_MEDIA_REF.matcher(markdown);
        while (m.find()) {
            String node = m.group(1);
            // A markdown node is only a media reference if its target resolves to media; an
            // ordinary link matches the same shape and must be left in the text.
            if (node != null && !isMediaTarget(node, dataNode, skipImages)) {
                continue;
            }
            // A bare comment-video url is left in the text under data saving, where no card is
            // drawn and mediaBlocksFor() likewise skipped it.
            if (skipImages && m.group(2) != null) {
                continue;
            }
            refs.add(new int[] {m.start(), m.end()});
        }
        return refs;
    }

    /** Whether a markdown link/image target is something {@link SubmissionParser} renders as media. */
    private static boolean isMediaTarget(
            String target, @Nullable JsonNode dataNode, boolean skipImages) {
        if (target.isEmpty() || target.startsWith("emote|")) {
            return false;
        }
        if (target.startsWith("giphy|")) {
            return true;
        }
        if (MEDIA_URL.matcher(target).matches() || GIPHY_LINK.matcher(target).matches()) {
            return true;
        }
        if (COMMENT_VIDEO_URL.matcher(target).matches()) {
            return !skipImages;
        }
        // A bare media_metadata key, as in ![img](abc123).
        JsonNode mediaMetadata = dataNode == null ? null : dataNode.get("media_metadata");
        return mediaMetadata != null && mediaMetadata.has(target);
    }
}
