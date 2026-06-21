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
import io.noties.markwon.AbstractMarkwonPlugin;
import io.noties.markwon.Markwon;
import io.noties.markwon.MarkwonSpansFactory;
import io.noties.markwon.MarkwonVisitor;
import io.noties.markwon.SpannableBuilder;
import io.noties.markwon.core.CorePlugin;
import io.noties.markwon.core.CoreProps;
import io.noties.markwon.ext.strikethrough.StrikethroughPlugin;
import io.noties.markwon.ext.tables.TablePlugin;
import io.noties.markwon.linkify.LinkifyPlugin;
import io.noties.markwon.movement.MovementMethodPlugin;
import me.edgan.redditslide.SpoilerRobotoTextView;
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
 */
public final class RedditMarkwon {

    private RedditMarkwon() {}

    private static volatile Markwon instance;

    /**
     * Render {@code markdown} into {@code textView}, wiring link taps to Slide's routing.
     *
     * @param subreddit context used for link theming/routing, may be {@code null}
     */
    public static void setMarkdown(
            SpoilerRobotoTextView textView, String subreddit, String markdown) {
        Markwon markwon = get(textView.getContext());
        String prepared = RedditSpoilerPreprocessor.sentinelize(markdown == null ? "" : markdown);
        Spanned rendered = markwon.toMarkdown(prepared);
        markwon.setParsedMarkdown(textView, rendered);
        CharSequence text = textView.getText();
        if (text instanceof Spannable) {
            textView.setMovementMethod(
                    new TextViewLinkHandler(textView, subreddit, (Spannable) text));
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
                // CorePlugin renders fenced/indented code blocks as styled code boxes
                // (the issue #179 fix).
                .usePlugin(CorePlugin.create())
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
                })
                .build();
    }
}
