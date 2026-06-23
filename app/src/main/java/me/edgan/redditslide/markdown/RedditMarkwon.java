/*
 * New Reddit-style markdown renderer (issue #179): renders a comment/self-text from its RAW
 * markdown `body` using Markwon (Apache-2.0), so fenced ``` code blocks become styled code
 * boxes like new Reddit — unlike Reddit's snudown `body_html`, which collapses ``` to inline
 * code.
 *
 * Links are emitted as {@link URLSpan} and routed through Slide's existing
 * {@link SpoilerRobotoTextView#onLinkClick} via {@link TextViewLinkHandler}, so link handling
 * and comment tap/long-press behave exactly as in the snudown pipeline.
 */
package me.edgan.redditslide.markdown;

import android.content.Context;
import android.text.Spannable;
import android.text.Spanned;
import android.text.style.RelativeSizeSpan;
import android.text.style.SuperscriptSpan;
import android.text.style.URLSpan;
import android.text.util.Linkify;
import androidx.core.content.ContextCompat;
import io.noties.markwon.AbstractMarkwonPlugin;
import io.noties.markwon.Markwon;
import io.noties.markwon.MarkwonSpansFactory;
import io.noties.markwon.MarkwonVisitor;
import io.noties.markwon.SpannableBuilder;
import io.noties.markwon.core.CoreProps;
import io.noties.markwon.core.MarkwonTheme;
import io.noties.markwon.ext.strikethrough.StrikethroughPlugin;
import io.noties.markwon.ext.tables.TablePlugin;
import io.noties.markwon.linkify.LinkifyPlugin;
import io.noties.markwon.movement.MovementMethodPlugin;
import java.util.regex.Pattern;
import me.edgan.redditslide.R;
import me.edgan.redditslide.SettingValues;
import me.edgan.redditslide.SpoilerRobotoTextView;
import me.edgan.redditslide.Visuals.Palette;
import me.edgan.redditslide.handler.TextViewLinkHandler;
import org.commonmark.node.Link;
import org.commonmark.parser.Parser;

/**
 * Factory + entry point for the Markwon-based ("new Reddit-style") renderer.
 *
 * <p>One {@link Markwon} instance is cached and reused. Markwon's core/table plugins bake
 * theme-derived colors (code-block background, blockquote stripe, table borders/headers) into a
 * {@code MarkwonTheme} at build time, so {@link #invalidate()} drops the cache on a base-theme
 * change to force a rebuild with the new colors.
 *
 * <p><b>Intentional differences from the snudown ({@code body_html}) path</b> — these are by design
 * (new Reddit style is meant to improve on old Reddit, not mirror it), so don't "fix" them:
 *
 * <ul>
 *   <li><b>Code:</b> fenced/indented blocks render as real code boxes; snudown collapses ``` to
 *       inline code. This is the whole point of the new path.
 *   <li><b>Superscript:</b> whole-token with nesting ({@link SuperscriptPostProcessor}), matching
 *       new Reddit rather than snudown's single-char behavior.
 *   <li><b>Ordered lists:</b> commonmark renders real lists and <em>respects the author's start
 *       number</em>; the snudown path flattens them to plain text and always renumbers from 1.
 * </ul>
 */
public final class RedditMarkwon {

    private RedditMarkwon() {}

    private static volatile Markwon instance;

    /**
     * Matches a zero-width-space html entity in the raw markdown: hex ({@code &#x200B;},
     * upper/lower, zero-padded) or decimal ({@code &#8203;}). Authors paste these (often from new
     * Reddit's fancy editor) as blank-line spacers.
     *
     * <p>Slide fetches the {@code selftext}/{@code body} fields <em>without</em> {@code raw_json=1},
     * so Reddit html-escapes them and the author's {@code &} arrives as {@code &amp;} — i.e. the
     * entity reaches us as {@code &amp;#x200B;}. commonmark then resolves {@code &amp;}→{@code &} in
     * a single pass, leaving the literal text {@code &#x200B;} on screen. Hence the optional
     * {@code amp;} group: we strip the entity in both its escaped and unescaped forms, rewriting it
     * to the actual U+200B char so the (otherwise empty) paragraph survives as an invisible spacer.
     */
    private static final Pattern ZERO_WIDTH_SPACE_ENTITY =
            Pattern.compile("&(?:amp;)?#(?:[xX]0*200[bB]|0*8203);");

    /**
     * Render {@code markdown} into {@code textView}, wiring link taps to Slide's routing.
     *
     * @param subreddit context used for link theming/routing, may be {@code null}
     */
    public static void setMarkdown(
            SpoilerRobotoTextView textView, String subreddit, String markdown) {
        Markwon markwon = get(textView.getContext());
        String prepared = RedditSpoilerPreprocessor.sentinelize(markdown == null ? "" : markdown);
        prepared = BlockquoteNormalizer.mergeAdjacentBlockquotes(prepared);
        prepared = ZERO_WIDTH_SPACE_ENTITY.matcher(prepared).replaceAll("\u200B");
        Spanned rendered = markwon.toMarkdown(prepared);
        markwon.setParsedMarkdown(textView, rendered);
        CharSequence text = textView.getText();
        if (text instanceof Spannable) {
            Spannable spannable = (Spannable) text;
            // Color the spoiler blocks with the comment's theme color (matching the snudown path).
            // Done here, not at build time, because the Markwon instance is cached across subreddits.
            int spoilerColor = Palette.getDarkerColor(Palette.getColor(subreddit));
            for (RedditSpoilerSpan span :
                    spannable.getSpans(0, spannable.length(), RedditSpoilerSpan.class)) {
                span.setMaskColor(spoilerColor);
            }
            textView.setMovementMethod(new TextViewLinkHandler(textView, subreddit, spannable));
        }
    }

    public static Markwon get(Context context) {
        Markwon local = instance;
        if (local == null) {
            synchronized (RedditMarkwon.class) {
                local = instance;
                if (local == null) {
                    local = build(context.getApplicationContext());
                    instance = local;
                }
            }
        }
        return local;
    }

    /**
     * Drop the cached instance so the next render rebuilds Markwon with current theme colors. Call
     * on a base-theme change (see {@code MainActivity.restartTheme}); rebuild is lazy.
     */
    public static void invalidate() {
        instance = null;
    }

    private static Markwon build(Context context) {
        return Markwon.builder(context)
                // When a comment's whole body is media (e.g. a lone giphy reaction
                // ![gif](giphy|ID)), ImageDropPostProcessor drops the image node and the
                // rendered text is empty — the image is drawn separately from body_html. Markwon
                // defaults to resurrecting the RAW markdown source in that case, which leaked
                // "![gif](giphy|ID)" as literal text above the gif; disable that fallback.
                .fallbackToRawInputWhenEmpty(false)
                // NB: do NOT add CorePlugin.create() explicitly here. Markwon already adds
                // CorePlugin automatically (it renders fenced/indented code blocks as styled
                // code boxes — the issue #179 fix). Adding a second instance leaves two
                // CorePlugins in the registry: LinkifyPlugin attaches its OnTextAddedListener
                // to one while the Text visitor that actually renders belongs to the other, so
                // bare URLs in comments silently stop being linkified.
                .usePlugin(StrikethroughPlugin.create())
                .usePlugin(TablePlugin.create(context))
                .usePlugin(LinkifyPlugin.create(Linkify.WEB_URLS))
                // Don't let Markwon install LinkMovementMethod; Slide sets its own handler so
                // single-tap (collapse) and long-press (menu) keep working on comments.
                .usePlugin(MovementMethodPlugin.none())
                // Emit links as URLSpan so Slide's TextViewLinkHandler routes them through
                // the existing onLinkClick logic.
                .usePlugin(new AbstractMarkwonPlugin() {
                    @Override
                    public void configureParser(Parser.Builder builder) {
                        // Clean-room Reddit syntax: u/·r/ mentions, ^superscript and >!spoilers!<.
                        builder.postProcessor(new MentionPostProcessor());
                        builder.postProcessor(new SuperscriptPostProcessor());
                        builder.postProcessor(new SpoilerPostProcessor());
                        // Legacy CSS spoiler links ([x](/s), [teaser](/s "hidden")) → SpoilerNode.
                        builder.postProcessor(new LegacySpoilerPostProcessor());
                        // Images (giphy/inline) are drawn as resolved blocks from body_html
                        // (MarkdownImages) and free emotes as ￼ placeholders, so drop the
                        // markdown image nodes from the text.
                        builder.postProcessor(new ImageDropPostProcessor());
                    }

                    @Override
                    public void configureVisitor(MarkwonVisitor.Builder builder) {
                        builder.on(
                                SuperscriptNode.class,
                                (visitor, node) -> {
                                    int start = visitor.length();
                                    visitor.visitChildren(node);
                                    SpannableBuilder.setSpans(
                                            visitor.builder(),
                                            new Object[] {
                                                new SuperscriptSpan(), new RelativeSizeSpan(0.75f)
                                            },
                                            start,
                                            visitor.length());
                                });
                        builder.on(
                                SpoilerNode.class,
                                (visitor, node) -> {
                                    int start = visitor.length();
                                    visitor.visitChildren(node);
                                    SpannableBuilder.setSpans(
                                            visitor.builder(),
                                            new RedditSpoilerSpan(),
                                            start,
                                            visitor.length());
                                });
                    }

                    @Override
                    public void configureSpansFactory(MarkwonSpansFactory.Builder builder) {
                        builder.setFactory(
                                Link.class,
                                (configuration, props) ->
                                        new URLSpan(CoreProps.LINK_DESTINATION.require(props)));
                    }

                    @Override
                    public void configureTheme(MarkwonTheme.Builder builder) {
                        // Match the snudown path's blockquote stripe (SpoilerRobotoTextView /
                        // CustomQuoteSpan): theme-aware blue, 4px stripe, 5px gap — instead of
                        // Markwon's default faded-grey bar. Colors are baked at build time, so
                        // invalidate() rebuilds on a theme change.
                        int barColor =
                                ContextCompat.getColor(
                                        context,
                                        SettingValues.currentTheme == 1
                                                        || SettingValues.currentTheme == 5
                                                ? R.color.md_blue_600
                                                : R.color.md_blue_400);
                        builder.blockQuoteColor(barColor)
                                .blockQuoteWidth(4) // px stripe, matches CustomQuoteSpan
                                .blockMargin(9); // px leading margin: stripe (4) + gap (5)
                    }
                })
                .build();
    }
}
