package me.edgan.redditslide;

import android.app.Activity;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Movie;
import android.graphics.Typeface;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.style.BackgroundColorSpan;
import android.text.style.CharacterStyle;
import android.text.style.DynamicDrawableSpan;
import android.text.style.ForegroundColorSpan;
import android.text.style.ImageSpan;
import android.text.style.QuoteSpan;
import android.text.style.RelativeSizeSpan;
import android.text.style.StrikethroughSpan;
import android.text.style.StyleSpan;
import android.text.style.TypefaceSpan;
import android.text.style.URLSpan;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.LruCache;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;

import androidx.appcompat.view.ContextThemeWrapper;
import androidx.core.content.ContextCompat;

import com.cocosw.bottomsheet.BottomSheet;
import com.devspark.robototextview.widget.RobotoTextView;

import me.edgan.redditslide.Activities.Album;
import me.edgan.redditslide.Activities.AlbumPager;
import me.edgan.redditslide.Activities.MediaView;
import me.edgan.redditslide.Activities.TumblrPager;
import me.edgan.redditslide.SubmissionViews.OpenVRedditTask;
import me.edgan.redditslide.Views.CustomQuoteSpan;
import me.edgan.redditslide.Visuals.Palette;
import me.edgan.redditslide.handler.TextViewLinkHandler;
import me.edgan.redditslide.util.AnimatedImageSpan;
import me.edgan.redditslide.util.BlendModeUtil;
import me.edgan.redditslide.util.CompatUtil;
import me.edgan.redditslide.util.GifDrawable;
import me.edgan.redditslide.util.GifUtils;
import me.edgan.redditslide.util.LinkUtil;
import me.edgan.redditslide.util.LogUtil;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.text.StringEscapeUtils;

import java.io.File;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;


/** Created by carlo_000 on 1/11/2016. */
public class SpoilerRobotoTextView extends RobotoTextView implements ClickableText {
    private List<CharacterStyle> storedSpoilerSpans = new ArrayList<>();
    private List<Integer> storedSpoilerStarts = new ArrayList<>();
    private List<Integer> storedSpoilerEnds = new ArrayList<>();
    public static final Pattern htmlSpoilerPattern =
            Pattern.compile("<a href=\"[#/](?:spoiler|sp|s)\">([^<]*)</a>");
    public static final Pattern nativeSpoilerPattern =
            Pattern.compile("<span class=\"[^\"]*md-spoiler-text+[^\"]*\">([^<]*)</span>");

    private static class MatchPair {
        final int start;
        final int end;

        MatchPair(int start, int end) {
            this.start = start;
            this.end = end;
        }
    }

    public SpoilerRobotoTextView(Context context) {
        super(context);
        setLineSpacing(0, 1.1f);
    }

    public SpoilerRobotoTextView(Context context, AttributeSet attrs) {
        super(context, attrs);
        setLineSpacing(0, 1.1f);
    }

    public SpoilerRobotoTextView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        setLineSpacing(0, 1.1f);
    }

    public boolean isSpoilerClicked() {
        return spoilerClicked;
    }

    public void resetSpoilerClicked() {
        spoilerClicked = false;
    }

    public boolean spoilerClicked = false;

    private static SpannableStringBuilder removeNewlines(SpannableStringBuilder s) {
        int start = 0;
        int end = s.length();
        while (start < end && Character.isWhitespace(s.charAt(start))) {
            start++;
        }

        while (end > start && Character.isWhitespace(s.charAt(end - 1))) {
            end--;
        }

        return (SpannableStringBuilder) s.subSequence(start, end);
    }

    /**
     * Set the text from html. Handles formatting spoilers, links etc.
     *
     * <p>The text must be valid html.
     *
     * @param text html text
     */
    public void setTextHtml(CharSequence text) {
        setTextHtml(text, "");
    }

    /**
     * Set the text from html. Handles formatting spoilers, links etc.
     *
     * <p>The text must be valid html.
     *
     * @param baseText html text
     * @param subreddit the subreddit to theme
     */
    public void setTextHtml(CharSequence baseText, String subreddit) {
        setTextHtml(baseText, subreddit, null);
    }

    /**
     * Set the text from html. Handles formatting spoilers, links etc.
     *
     * <p>The text must be valid html.
     *
     * @param baseText html text
     * @param subreddit the subreddit to theme
     * @param submission the submission object for accessing media metadata
     */
    public void setTextHtml(CharSequence baseText, String subreddit, net.dean.jraw.models.Submission submission) {
        String text = wrapAlternateSpoilers(saveEmotesFromDestruction(baseText.toString().trim()));
        text = replaceCodeBlocks(text);

        // Process Reddit video player URLs before HTML parsing if submission is provided
        if (submission != null && text.contains("reddit.com/link/") && text.contains("/video/")) {
            text = preprocessRedditVideoPlayerUrls(text, submission, subreddit);
        }

        SpannableStringBuilder builder = (SpannableStringBuilder) CompatUtil.fromHtml(text);

        // replace the <blockquote> blue line with something more colorful
        replaceQuoteSpans(builder);

        if (text.contains("free_emotes_pack") || text.contains("giphy")) {
            setEmoteText(text, this);
        }
        if (text.contains("<a")) {
            setEmoteSpans(builder); // for emote enabled subreddits
        }
        if (text.contains("[")) {
            setCodeFont(builder);
            setSpoilerStyle(builder, subreddit);
        }
        if (text.contains("[[d[")) {
            setStrikethrough(builder);
        }
        if (text.contains("[[h[")) {
            setHighlight(builder, subreddit);
        }

        processRedditPreviewImages(builder);

        // Process video thumbnail placeholders if submission is provided
        if (submission != null && builder.toString().contains("VIDEO_THUMBNAIL_")) {
            processVideoThumbnailPlaceholders(builder, submission, subreddit);
        }

        if (subreddit != null && !subreddit.isEmpty()) {
            // Check if we have video thumbnails
            boolean hasVideoThumbnails = submission != null && builder.toString().contains("\uFFFC");

            if (hasVideoThumbnails) {
                // Use standard LinkMovementMethod for video thumbnails to ensure ClickableSpans work
                setMovementMethod(android.text.method.LinkMovementMethod.getInstance());
                android.util.Log.d("SpoilerRobotoTextView", "Using LinkMovementMethod for video thumbnails");
            } else {
                // Use custom TextViewLinkHandler for regular content
            setMovementMethod(new TextViewLinkHandler(this, subreddit, builder));
            }

            setFocusable(false);
            // Don't set clickable to false if we have video thumbnails
            if (!hasVideoThumbnails) {
            setClickable(false);
            }
            if (subreddit.equals("FORCE_LINK_CLICK")) {
                setLongClickable(false);
            }
        }

        // If we have video thumbnails, make sure the TextView can handle clicks
        if (submission != null && builder.toString().contains("\uFFFC")) {
            setClickable(true);
            setFocusable(true);
            android.util.Log.d("SpoilerRobotoTextView", "TextView set to clickable for video thumbnails");
        }

        builder = removeNewlines(builder);
        builder.append(" ");

        super.setText(builder, BufferType.SPANNABLE);
    }

    /**
     * Replaces the blue line produced by
     *
     * <blockquote>
     *
     * s with something more visible
     *
     * @param spannable parsed comment text #fromHtml
     */
    private void replaceQuoteSpans(Spannable spannable) {
        QuoteSpan[] quoteSpans = spannable.getSpans(0, spannable.length(), QuoteSpan.class);

        for (QuoteSpan quoteSpan : quoteSpans) {
            final int start = spannable.getSpanStart(quoteSpan);
            final int end = spannable.getSpanEnd(quoteSpan);
            final int flags = spannable.getSpanFlags(quoteSpan);

            spannable.removeSpan(quoteSpan);

            // If the theme is Light or Sepia, use a darker blue; otherwise, use a lighter blue
            final int barColor =
                    ContextCompat.getColor(
                            getContext(),
                            SettingValues.currentTheme == 1 || SettingValues.currentTheme == 5
                                    ? R.color.md_blue_600
                                    : R.color.md_blue_400);

            final int BAR_WIDTH = 4;
            final int GAP = 5;

            spannable.setSpan(
                    new CustomQuoteSpan(
                            barColor, // bar color
                            BAR_WIDTH, // bar width
                            GAP), // bar + text gap
                    start,
                    end,
                    flags);
        }
    }

    private String wrapAlternateSpoilers(String html) {
        String replacement = "<a href=\"/spoiler\">spoiler&lt; [[s[$1]s]]</a>";

        html = htmlSpoilerPattern.matcher(html).replaceAll(replacement);
        html = nativeSpoilerPattern.matcher(html).replaceAll(replacement);
        return html;
    }

    private String saveEmotesFromDestruction(String html) {
        // Emotes often have no spoiler caption, and therefore are converted to empty anchors.
        // Html.fromHtml removes anchors with zero length node text. Find zero length anchors that
        // start
        // with "/" and add "." to them.
        Pattern htmlEmotePattern = Pattern.compile("<a href=\"/.*\"></a>");
        Matcher htmlEmoteMatcher = htmlEmotePattern.matcher(html);
        while (htmlEmoteMatcher.find()) {
            String newPiece = htmlEmoteMatcher.group();
            // Ignore empty tags marked with sp.
            if (!htmlEmoteMatcher.group().contains("href=\"/sp\"")) {
                newPiece = newPiece.replace("></a", ">.</a");
                html = html.replace(htmlEmoteMatcher.group(), newPiece);
            }
        }
        return html;
    }

    // List to keep track of active GifDrawables to manage their lifecycle
    private List<GifDrawable> activeGifDrawables = new ArrayList<>();

    private SpannableStringBuilder currentBuilder;
    private final Object spanLock = new Object();

    private static class PendingEmoteSpan {
        DynamicDrawableSpan span;
        String emoteName;
        int index;

        PendingEmoteSpan(DynamicDrawableSpan span, String emoteName, int index) {
            this.span = span;
            this.emoteName = emoteName;
            this.index = index;
        }
    }

    private final List<EmoteDrawInfo> emoteDrawables = new ArrayList<>();

    private static class EmoteDrawInfo {
        GifDrawable drawable;
        int position; // Character position in text
        float x; // Cached x position
        float y; // Cached y position
        boolean isValid;

        EmoteDrawInfo(GifDrawable drawable, int position) {
            this.drawable = drawable;
            this.position = position;
        }
    }

    private final List<PendingEmoteSpan> pendingSpans = new ArrayList<>();

    public static boolean findObjectReplacementChar(CharSequence text) {
        boolean atStart = text.charAt(0) == '\uFFFC';

        if (atStart) return true;

        return false;
    }

    private void setEmoteSpan(DynamicDrawableSpan span, String emoteName, int position) {
        if (span == null || emoteName == null) {
            Log.e("EmoteDebug", "Null span or emote name in setEmoteSpan");
            return;
        }

        synchronized (spanLock) {
            try {
                if (!isAttachedToWindow()) {
                    Log.d("EmoteDebug", "View not attached, queueing update for " + emoteName);
                    pendingSpans.add(
                            new PendingEmoteSpan((AnimatedImageSpan) span, emoteName, position));
                    return;
                }

                // Get current text and ensure it's a SpannableStringBuilder
                CharSequence current = getText();
                SpannableStringBuilder text;
                if (current instanceof SpannableStringBuilder) {
                    text = (SpannableStringBuilder) current;
                } else {
                    text = new SpannableStringBuilder(current != null ? current : "");
                    setText(text);
                }

                // Find position for emote
                String content = text.toString();
                int pos = -1; // Initialize pos
                int count = 0;

                while (count <= position) {
                    pos =
                            content.indexOf(
                                    '\uFFFC',
                                    pos + 1); // '\uFFFC' is the object replacement character
                    if (pos == -1) {
                        Log.e("EmoteDebug", "Could not find position for emote " + emoteName);
                        return;
                    }
                    count++;
                }

                // Remove any existing spans at this position
                ImageSpan[] existingSpans = text.getSpans(pos, pos + 1, ImageSpan.class);
                for (ImageSpan existingSpan : existingSpans) {
                    text.removeSpan(existingSpan);
                }

                // Set the new AnimatedImageSpan
                text.setSpan(span, pos, pos + 1, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);

                if (SettingValues.commentEmoteAnimation) {
                    // Start the GIF animation and add to active list if it's an AnimatedImageSpan
                    if (span instanceof AnimatedImageSpan) {
                        AnimatedImageSpan animatedSpan = (AnimatedImageSpan) span;
                        animatedSpan.start();
                        emoteDrawables.add(new EmoteDrawInfo(animatedSpan.getGifDrawable(), pos));
                    }
                }

                // Force layout update
                requestLayout();

            } catch (Exception e) {
                Log.e("EmoteDebug", "Error setting emote drawable for " + emoteName, e);
            }
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        synchronized (spanLock) {
            // First apply any pending spans
            if (!pendingSpans.isEmpty()) {
                Log.d("EmoteDebug", "Applying " + pendingSpans.size() + " pending spans");
                for (PendingEmoteSpan pendingSpan : pendingSpans) {
                    setEmoteSpan(pendingSpan.span, pendingSpan.emoteName, pendingSpan.index);
                }
                pendingSpans.clear();
            }

            // Start all spans
            CharSequence text = getText();
            if (text instanceof Spannable) {
                Spannable spannable = (Spannable) text;
                DynamicDrawableSpan[] spans =
                        spannable.getSpans(0, spannable.length(), DynamicDrawableSpan.class);
                for (DynamicDrawableSpan span : spans) {
                    if (span instanceof AnimatedImageSpan) {
                        ((AnimatedImageSpan) span).onAttached();
                    }
                }
            }
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        synchronized (spanLock) {
            // Stop all spans
            CharSequence text = getText();
            if (text instanceof Spannable) {
                Spannable spannable = (Spannable) text;
                DynamicDrawableSpan[] spans =
                        spannable.getSpans(0, spannable.length(), DynamicDrawableSpan.class);
                for (DynamicDrawableSpan span : spans) {
                    if (span instanceof AnimatedImageSpan) {
                        ((AnimatedImageSpan) span).onDetached();
                    }
                }
            }
        }
        super.onDetachedFromWindow();
    }

    private void cleanupGifs() {
        synchronized (spanLock) {
            // Stop all active GIF animations
            for (EmoteDrawInfo info : emoteDrawables) {
                info.drawable.stop();
            }
            emoteDrawables.clear();

            // Remove all AnimatedImageSpan instances from the text
            CharSequence currentText = getText();
            if (currentText instanceof Spannable) {
                Spannable spannable = (Spannable) currentText;
                AnimatedImageSpan[] spans =
                        spannable.getSpans(0, spannable.length(), AnimatedImageSpan.class);
                for (AnimatedImageSpan span : spans) {
                    spannable.removeSpan(span);
                }
            }

            // Clear pending spans
            pendingSpans.clear();
        }
    }

// In setEmoteText(...), after processing the raw HTML:
public void setEmoteText(String text, TextView textView) {
    if (text == null || textView == null) {
        Log.e("EmoteDebug", "Null text or textview in setEmoteText");
        return;
    }

    try {
        // Clear existing state
        cleanupGifs();

        Pattern redditPattern =
                Pattern.compile(
                    "<img\\s+src=\\\"(https://www\\.redditstatic\\.com/marketplace-assets/v1/core/emotes/snoomoji_emotes/free_emotes_pack/([^/\\\"]+)\\.gif)\\\"[^>]*>");
        Pattern giphyPattern =
                Pattern.compile(
                    "<img\\s+src=\\\"(https://external-preview\\.redd\\.it/([^?]+)\\?width=([0-9]+)&height=([0-9]+)&s=([^\\\"]+))\\\"[^>]*>");

        List<EmoteSpanRequest> spanRequests = new ArrayList<>();
        StringBuilder processedText = new StringBuilder();

        // Strip unwanted divs first
        text = text.replaceAll("<div class=\\\"md\\\"><div>", "").replaceAll("</div>", "");

        // Process the text for both patterns (note: free_emote_pack is handled by redditPattern)
        processPattern(text, redditPattern, processedText, spanRequests);
        processPattern(text, giphyPattern, processedText, spanRequests);

        // Create builder and ensure it's a SpannableStringBuilder
        SpannableStringBuilder builder = new SpannableStringBuilder(processedText);

        // Set the initial text
        setText(builder);

        // For each emote request, decide how to load it
        for (EmoteSpanRequest request : spanRequests) {
            // If this URL comes from external-preview (i.e. giphy emote), use the new inline image loader…
            if (request.gifUrl.contains("external-preview.redd.it")) {
                loadGiphyEmote(request, textView, request.start);
            } else {
                // …otherwise (e.g. free_emote_pack/snoomoji) leave it as before.
                loadGifEmote(request, textView, request.start);
            }
        }

    } catch (Exception e) {
        Log.e("EmoteDebug", "Error in setEmoteText", e);
    }
}

/**
 * Loads a giphy emote image asynchronously using loadThumbnailFromUrl
 * and replaces the placeholder ImageSpan (inserted as the object replacement character)
 * with one that uses the downloaded image.
 */
private void loadGiphyEmote(EmoteSpanRequest request, TextView textView, int posCount) {
    Log.d("EmoteDebug", "Starting image download for giphy emote: " + request.gifUrl);
    loadThumbnailFromUrl(request.gifUrl, new ImageCallback() {
        @Override
        public void onImageLoaded(Bitmap bitmap) {
            post(() -> {
                if (bitmap != null) {
                    try {
                        // Compute scaled dimensions (using similar limits as inline preview images)
                        int maxWidth = Math.min(getWidth() - getPaddingLeft() - getPaddingRight(), 500);
                        int maxHeight = 300;  // maximum height (you can adjust as needed)
                        float widthScale = (float) maxWidth / bitmap.getWidth();
                        float heightScale = (float) maxHeight / bitmap.getHeight();
                        float scale = Math.min(widthScale, heightScale);
                        int scaledWidth = (int) (bitmap.getWidth() * scale);
                        int scaledHeight = (int) (bitmap.getHeight() * scale);

                        BitmapDrawable drawable = new BitmapDrawable(getResources(), bitmap);
                        drawable.setBounds(0, 0, scaledWidth, scaledHeight);
                        ImageSpan newSpan = new ImageSpan(drawable);

                        // Find the appropriate occurrence of the object replacement character in our text.
                        CharSequence currentText = getText();
                        if (currentText instanceof Spannable) {
                            Spannable spannable = (Spannable) currentText;
                            String content = spannable.toString();
                            int occurrence = 0;
                            int index = -1;
                            int searchFrom = 0;
                            while (occurrence <= posCount) {
                                index = content.indexOf('\uFFFC', searchFrom);
                                if (index == -1) break;
                                if (occurrence == posCount) break;
                                occurrence++;
                                searchFrom = index + 1;
                            }
                            if (index != -1) {
                                // Remove any existing ImageSpan at the placeholder position.
                                ImageSpan[] spans = spannable.getSpans(index, index + 1, ImageSpan.class);
                                for (ImageSpan span : spans) {
                                    spannable.removeSpan(span);
                                }
                                // Replace the placeholder with the new ImageSpan.
                                spannable.setSpan(newSpan, index, index + 1, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                                Log.d("GiphyEmote", "Replaced placeholder with image: " + scaledWidth + "x" + scaledHeight);
                                invalidate();
                                requestLayout();
                            } else {
                                Log.e("GiphyEmote", "Could not find placeholder for giphy emote: " + request.emoteName);
                            }
                        }
                    } catch (Exception e) {
                        Log.e("GiphyEmote", "Error updating image span for giphy emote", e);
                    }
                }
            });
        }
    });
}

    private void processPattern(
            String text,
            Pattern pattern,
            StringBuilder processedText,
            List<EmoteSpanRequest> spanRequests) {
        Matcher matcher = pattern.matcher(text);
        int lastEnd = 0;
        int emoteCount = spanRequests.size();

        while (matcher.find()) {
            int matchStart = matcher.start();
            int matchEnd = matcher.end();

            // Append text before the match
            if (matchStart > lastEnd) {
                processedText.append(text.substring(lastEnd, matchStart));
            }

            try {
                String gifUrl = matcher.group(1);
                String emoteName = matcher.group(2);

                if (gifUrl != null && emoteName != null) {
                    processedText.append("\uFFFC"); // Object replacement character

                    spanRequests.add(
                            new EmoteSpanRequest(gifUrl, emoteCount, emoteCount + 1, emoteName));

                    emoteCount++;
                }
            } catch (IllegalStateException | IndexOutOfBoundsException e) {
                Log.e("EmoteDebug", "Error processing match groups", e);
            }

            lastEnd = matchEnd;
        }

        // Safely append remaining text after last match
        if (lastEnd < text.length()) {
            processedText.append(text.substring(lastEnd));
        }
    }

    private void loadGifEmote(EmoteSpanRequest request, TextView textView, int pos) {
        Log.d("EmoteDebug", "Starting GIF download for: " + request.gifUrl);

        GifUtils.downloadGif(request.gifUrl, new GifUtils.GifDownloadCallback() {
            @Override
            public void onGifDownloaded(File gifFile) {
                float scale = 0.5f;
                try {
                    Movie movie = Movie.decodeFile(gifFile.getAbsolutePath());
                    if (movie != null) {
                        int intrinsicWidth = movie.width();
                        int intrinsicHeight = movie.height();

                        int width = (int) (intrinsicWidth * scale);
                        int height = (int) (intrinsicHeight * scale);

                        // Create the GifDrawable and set its bounds correctly.
                        GifDrawable gifDrawable = new GifDrawable(movie, null);
                        gifDrawable.setBounds(0, 0, width, height);

                        Log.d("EmoteDebug", "Created drawable for " + request.emoteName +
                                " with bounds " + width + "x" + height);

                        // Wrap the drawable in an AnimatedImageSpan.
                        AnimatedImageSpan animatedSpan = new AnimatedImageSpan(gifDrawable, SpoilerRobotoTextView.this);

                        // Post UI updates.
                        textView.post(() -> {
                            synchronized (spanLock) {
                                try {
                                    if (!isAttachedToWindow()) {
                                        Log.d("EmoteDebug", "View not attached, queueing update for " + request.emoteName);
                                        pendingSpans.add(new PendingEmoteSpan(animatedSpan, request.emoteName, pos));
                                        return;
                                    }

                                    CharSequence currentText = getText();
                                    if (currentText instanceof Spannable) {
                                        Spannable spannable = (Spannable) currentText;
                                        String content = spannable.toString();
                                        int occurrence = 0;
                                        int index = -1;
                                        int searchFrom = 0;

                                        // Locate the nth occurrence of the object replacement character.
                                        while (occurrence <= pos) {
                                            index = content.indexOf('\uFFFC', searchFrom);
                                            if (index == -1) break;
                                            if (occurrence == pos) break;
                                            occurrence++;
                                            searchFrom = index + 1;
                                        }

                                        if (index != -1) {
                                            // Remove any existing ImageSpan at this placeholder.
                                            ImageSpan[] spans = spannable.getSpans(index, index + 1, ImageSpan.class);
                                            for (ImageSpan span : spans) {
                                                spannable.removeSpan(span);
                                            }
                                            // Replace the placeholder with the new AnimatedImageSpan.
                                            spannable.setSpan(animatedSpan, index, index + 1, Spannable.SPAN_EXCLUSIVE_INCLUSIVE);
                                        } else {
                                            Log.e("EmoteDebug", "Could not find placeholder for emote: " + request.emoteName);
                                        }
                                    }

                                    // Attach and start the animation if enabled.
                                    animatedSpan.onAttached();
                                    if (SettingValues.commentEmoteAnimation) {
                                        animatedSpan.start();
                                    }
                                    emoteDrawables.add(new EmoteDrawInfo(animatedSpan.getGifDrawable(), pos));
                                    requestLayout();

                                } catch (Exception e) {
                                    Log.e("EmoteDebug", "Error setting emote drawable for " + request.emoteName, e);
                                }
                            }
                        });
                    } else {
                        Log.e("EmoteDebug", "Failed to decode movie for: " + request.emoteName);
                    }
                } catch (Exception e) {
                    Log.e("EmoteDebug", "Error processing GIF: " + request.emoteName, e);
                }
            }

            @Override
            public void onGifDownloadFailed(Exception e) {
                Log.e("EmoteDebug", "Failed to download GIF: " + request.gifUrl, e);
            }
        }, textView.getContext());
    }

    // Helper class to keep track of span requests
    private static class EmoteSpanRequest {
        String gifUrl;
        int start;
        int end;
        String emoteName;

        EmoteSpanRequest(String gifUrl, int start, int end, String emoteName) {
            this.gifUrl = gifUrl;
            this.start = start;
            this.end = end;
            this.emoteName = emoteName;
        }
    }

    private void setEmoteSpans(SpannableStringBuilder builder) {
        for (URLSpan span : builder.getSpans(0, builder.length(), URLSpan.class)) {
            if (SettingValues.typeInText) {
                setLinkTypes(builder, span);
            }
            if (SettingValues.largeLinks) {
                setLargeLinks(builder, span);
            }
            File emoteDir = new File(Environment.getExternalStorageDirectory(), "RedditEmotes");
            File emoteFile =
                    new File(
                            emoteDir,
                            span.getURL().replace("/", "").replaceAll("-.*", "")
                                    + ".png"); // BPM uses "-" to add dynamics for emotes in
            // browser. Fall back to
            // original here if exists.
            boolean startsWithSlash = span.getURL().startsWith("/");
            boolean hasOnlyOneSlash = StringUtils.countMatches(span.getURL(), "/") == 1;

            if (emoteDir.exists() && startsWithSlash && hasOnlyOneSlash && emoteFile.exists()) {
                // We've got an emote match
                int start = builder.getSpanStart(span);
                int end = builder.getSpanEnd(span);
                CharSequence textCovers = builder.subSequence(start, end);

                // Make sure bitmap loaded works well with screen density.
                BitmapFactory.Options options = new BitmapFactory.Options();
                DisplayMetrics metrics = new DisplayMetrics();
                ContextCompat.getSystemService(getContext(), WindowManager.class)
                        .getDefaultDisplay()
                        .getMetrics(metrics);
                options.inDensity = 240;
                options.inScreenDensity = metrics.densityDpi;
                options.inScaled = true;

                // Since emotes are not directly attached to included text, add extra character to
                // attach
                // image to.
                builder.removeSpan(span);
                if (builder.subSequence(start, end).charAt(0) != '.') {
                    builder.insert(start, ".");
                }
                Bitmap emoteBitmap = BitmapFactory.decodeFile(emoteFile.getAbsolutePath(), options);
                builder.setSpan(
                        new ImageSpan(getContext(), emoteBitmap),
                        start,
                        start + 1,
                        Spanned.SPAN_INCLUSIVE_INCLUSIVE);
                // Check if url span has length. If it does, it's a spoiler/caption
                if (textCovers.length() > 1) {
                    builder.setSpan(
                            new URLSpan("/sp"),
                            start + 1,
                            end + 1,
                            Spanned.SPAN_INCLUSIVE_INCLUSIVE);
                    builder.setSpan(
                            new StyleSpan(Typeface.ITALIC),
                            start + 1,
                            end + 1,
                            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                }
                builder.append("\n"); // Newline to fix text wrapping issues
            }
        }
    }

    private void setLinkTypes(SpannableStringBuilder builder, URLSpan span) {
        String url = span.getURL();
        if (url.endsWith("/")) {
            url = url.substring(0, url.length() - 1);
        }
        String text =
                builder.subSequence(builder.getSpanStart(span), builder.getSpanEnd(span))
                        .toString();
        if (!text.equalsIgnoreCase(url)) {
            ContentType.Type contentType = ContentType.getContentType(url);
            String bod;
            try {
                bod =
                        " ("
                                + ((url.contains("/")
                                                && url.startsWith("/")
                                                && !(url.split("/").length > 2))
                                        ? url
                                        : (getContext()
                                                        .getString(
                                                                ContentType.getContentID(
                                                                        contentType, false))
                                                + (contentType == ContentType.Type.LINK
                                                        ? " " + Uri.parse(url).getHost()
                                                        : "")))
                                + ")";
            } catch (Exception e) {
                bod =
                        " ("
                                + getContext()
                                        .getString(ContentType.getContentID(contentType, false))
                                + ")";
            }
            SpannableStringBuilder b = new SpannableStringBuilder(bod);
            b.setSpan(
                    new StyleSpan(Typeface.BOLD), 0, b.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            b.setSpan(new RelativeSizeSpan(0.8f), 0, b.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            builder.insert(builder.getSpanEnd(span), b);
        }
    }

    private void setLargeLinks(SpannableStringBuilder builder, URLSpan span) {
        builder.setSpan(
                new RelativeSizeSpan(1.3f),
                builder.getSpanStart(span),
                builder.getSpanEnd(span),
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    }

    private void setStrikethrough(SpannableStringBuilder builder) {
        final int offset = "[[d[".length(); // == "]d]]".length()

        int start = -1;
        int end;

        for (int i = 0; i < builder.length() - 3; i++) {
            if (builder.charAt(i) == '['
                    && builder.charAt(i + 1) == '['
                    && builder.charAt(i + 2) == 'd'
                    && builder.charAt(i + 3) == '[') {
                start = i + offset;
            } else if (builder.charAt(i) == ']'
                    && builder.charAt(i + 1) == 'd'
                    && builder.charAt(i + 2) == ']'
                    && builder.charAt(i + 3) == ']') {
                end = i;
                builder.setSpan(
                        new StrikethroughSpan(), start, end, Spanned.SPAN_INCLUSIVE_INCLUSIVE);
                builder.delete(end, end + offset);
                builder.delete(start - offset, start);
                i -= offset + (end - start); // length of text
            }
        }
    }

    private void setHighlight(SpannableStringBuilder builder, String subreddit) {
        final int offset = "[[h[".length(); // == "]h]]".length()

        int start = -1;
        int end;
        for (int i = 0; i < builder.length() - 4; i++) {
            if (builder.charAt(i) == '['
                    && builder.charAt(i + 1) == '['
                    && builder.charAt(i + 2) == 'h'
                    && builder.charAt(i + 3) == '[') {
                start = i + offset;
            } else if (builder.charAt(i) == ']'
                    && builder.charAt(i + 1) == 'h'
                    && builder.charAt(i + 2) == ']'
                    && builder.charAt(i + 3) == ']') {
                end = i;
                builder.setSpan(
                        new BackgroundColorSpan(Palette.getColor(subreddit)),
                        start,
                        end,
                        Spanned.SPAN_INCLUSIVE_INCLUSIVE);
                builder.delete(end, end + offset);
                builder.delete(start - offset, start);
                i -= offset + (end - start); // length of text
            }
        }
    }

    @Override
    public void onLinkClick(String url, int xOffset, String subreddit, URLSpan span) {
        if (url == null) {
            ((View) getParent()).callOnClick();
            return;
        }

        ContentType.Type type = ContentType.getContentType(url);
        Context context = getContext();
        Activity activity = null;
        if (context instanceof Activity) {
            activity = (Activity) context;
        } else if (context instanceof ContextThemeWrapper) {
            activity = (Activity) ((ContextThemeWrapper) context).getBaseContext();
        } else if (context instanceof ContextWrapper) {
            Context context1 = ((ContextWrapper) context).getBaseContext();
            if (context1 instanceof Activity) {
                activity = (Activity) context1;
            } else if (context1 instanceof ContextWrapper) {
                Context context2 = ((ContextWrapper) context1).getBaseContext();
                if (context2 instanceof Activity) {
                    activity = (Activity) context2;
                } else if (context2 instanceof ContextWrapper) {
                    activity = (Activity) ((ContextThemeWrapper) context2).getBaseContext();
                }
            }
        } else {
            throw new RuntimeException("Could not find activity from context:" + context);
        }

        if (!PostMatch.openExternal(url) || type == ContentType.Type.VIDEO) {
            switch (type) {
                case DEVIANTART:
                case IMGUR:
                case XKCD:
                    if (SettingValues.image) {
                        Intent intent2 = new Intent(activity, MediaView.class);
                        intent2.putExtra(MediaView.EXTRA_URL, url);
                        intent2.putExtra(MediaView.SUBREDDIT, subreddit);
                        activity.startActivity(intent2);
                    } else {
                        LinkUtil.openExternally(url);
                    }
                    break;
                case REDDIT:
                    OpenRedditLink.openUrl(activity, url, true);
                    break;
                case LINK:
                    if (url.startsWith("https://giphy.com/")) {
                        openGif(url, subreddit, activity);
                    } else {
                        LogUtil.v("Opening link");
                        LinkUtil.openUrl(url, Palette.getColor(subreddit), activity);
                    }
                    break;
                case SELF:
                case NONE:
                    break;
                case STREAMABLE:
                    openStreamable(url, subreddit);
                    break;
                case ALBUM:
                    if (SettingValues.album) {
                        Intent i;
                        if (SettingValues.albumSwipe) {
                            i = new Intent(activity, AlbumPager.class);
                            i.putExtra(Album.EXTRA_URL, url);
                            i.putExtra(AlbumPager.SUBREDDIT, subreddit);
                        } else {
                            i = new Intent(activity, Album.class);
                            i.putExtra(Album.SUBREDDIT, subreddit);
                            i.putExtra(Album.EXTRA_URL, url);
                        }
                        activity.startActivity(i);
                    } else {
                        LinkUtil.openExternally(url);
                    }
                    break;
                case TUMBLR:
                    if (SettingValues.image) {
                        Intent i = new Intent(activity, TumblrPager.class);
                        i.putExtra(Album.EXTRA_URL, url);
                        activity.startActivity(i);
                    } else {
                        LinkUtil.openExternally(url);
                    }
                    break;
                case IMAGE:
                    openImage(url, subreddit);
                    break;
                case VREDDIT_REDIRECT:
                    openVReddit(url, subreddit, activity);
                    break;
                case GIF:
                case VREDDIT_DIRECT:
                    openGif(url, subreddit, activity);
                    break;
                case VIDEO:
                    if (!LinkUtil.tryOpenWithVideoPlugin(url)) {
                        LinkUtil.openUrl(url, Palette.getStatusBarColor(), activity);
                    }
                case SPOILER:
                    spoilerClicked = true;
                    setOrRemoveSpoilerSpans(xOffset, span);
                    break;
                case EXTERNAL:
                    LinkUtil.openExternally(url);
                    break;
            }
        } else {
            LinkUtil.openExternally(url);
        }
    }

    @Override
    public void onLinkLongClick(final String baseUrl, MotionEvent event) {
        if (baseUrl == null || SettingValues.noPreviewImageLongClick) {
            return;
        }
        final String url = StringEscapeUtils.unescapeHtml4(baseUrl);

        performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
        Activity activity = null;
        final Context context = getContext();
        if (context instanceof Activity) {
            activity = (Activity) context;
        } else if (context instanceof ContextThemeWrapper) {
            activity = (Activity) ((ContextThemeWrapper) context).getBaseContext();
        } else if (context instanceof ContextWrapper) {
            Context context1 = ((ContextWrapper) context).getBaseContext();
            if (context1 instanceof Activity) {
                activity = (Activity) context1;
            } else if (context1 instanceof ContextWrapper) {
                Context context2 = ((ContextWrapper) context1).getBaseContext();
                if (context2 instanceof Activity) {
                    activity = (Activity) context2;
                } else if (context2 instanceof ContextWrapper) {
                    activity = (Activity) ((ContextThemeWrapper) context2).getBaseContext();
                }
            }
        } else {
            throw new RuntimeException("Could not find activity from context:" + context);
        }

        if (activity != null && !activity.isFinishing()) {
            BottomSheet.Builder b = new BottomSheet.Builder(activity).title(url).grid();
            int[] attrs = new int[] {R.attr.tintColor};
            TypedArray ta = getContext().obtainStyledAttributes(attrs);

            int color = ta.getColor(0, Color.WHITE);
            Drawable open = getResources().getDrawable(R.drawable.ic_open_in_new);
            Drawable share = getResources().getDrawable(R.drawable.ic_share);
            Drawable copy = getResources().getDrawable(R.drawable.ic_content_copy);
            final List<Drawable> drawableSet = Arrays.asList(open, share, copy);
            BlendModeUtil.tintDrawablesAsSrcAtop(drawableSet, color);

            ta.recycle();

            b.sheet(R.id.open_link, open, getResources().getString(R.string.open_externally));
            b.sheet(R.id.share_link, share, getResources().getString(R.string.share_link));
            b.sheet(
                    R.id.copy_link,
                    copy,
                    getResources().getString(R.string.submission_link_copy));
            final Activity finalActivity = activity;
            b.listener(
                            new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    switch (which) {
                                        case R.id.open_link:
                                            LinkUtil.openExternally(url);
                                            break;
                                        case R.id.share_link:
                                            Reddit.defaultShareText("", url, finalActivity);
                                            break;
                                        case R.id.copy_link:
                                            LinkUtil.copyUrl(url, finalActivity);
                                            break;
                                    }
                                }
                            })
                    .show();
//            }
        }
    }

    private void openVReddit(String url, String subreddit, Activity activity) {
        new OpenVRedditTask(activity, subreddit)
                .executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, url);
    }

    private void openGif(String url, String subreddit, Activity activity) {
        if (SettingValues.gif) {
            Intent myIntent = new Intent(getContext(), MediaView.class);
            myIntent.putExtra(MediaView.EXTRA_URL, url);
            myIntent.putExtra(MediaView.SUBREDDIT, subreddit);
            getContext().startActivity(myIntent);
            //}
        } else {
            LinkUtil.openExternally(url);
        }
    }

    private void openStreamable(String url, String subreddit) {
        if (SettingValues.video) { // todo maybe streamable here?
            Intent myIntent = new Intent(getContext(), MediaView.class);

            myIntent.putExtra(MediaView.EXTRA_URL, url);
            myIntent.putExtra(MediaView.SUBREDDIT, subreddit);
            getContext().startActivity(myIntent);

        } else {
            LinkUtil.openExternally(url);
        }
    }

    private void openImage(String submission, String subreddit) {
        if (SettingValues.image) {
            Intent myIntent = new Intent(getContext(), MediaView.class);
            myIntent.putExtra(MediaView.EXTRA_URL, submission);
            myIntent.putExtra(MediaView.SUBREDDIT, subreddit);
            getContext().startActivity(myIntent);
        } else {
            LinkUtil.openExternally(submission);
        }
    }

    public void setOrRemoveSpoilerSpans(int endOfLink, URLSpan span) {
        if (span != null) {
            int offset = (span.getURL().contains("hidden")) ? -1 : 2;
            Spannable text = (Spannable) getText();
            // add 2 to end of link since there is a white space between the link text and the
            // spoiler
            ForegroundColorSpan[] foregroundColors =
                    text.getSpans(
                            endOfLink + offset, endOfLink + offset, ForegroundColorSpan.class);

            if (foregroundColors.length > 1) {
                text.removeSpan(foregroundColors[1]);
            } else {
                for (int i = 1; i < storedSpoilerStarts.size(); i++) {
                    if (storedSpoilerStarts.get(i) < endOfLink + offset
                            && storedSpoilerEnds.get(i) > endOfLink + offset) {
                        try {
                            text.setSpan(
                                    storedSpoilerSpans.get(i),
                                    storedSpoilerStarts.get(i),
                                    storedSpoilerEnds.get(i) > text.toString().length()
                                            ? storedSpoilerEnds.get(i) + offset
                                            : storedSpoilerEnds.get(i),
                                    Spanned.SPAN_INCLUSIVE_INCLUSIVE);
                        } catch (Exception ignored) {
                            // catch out of bounds
                            ignored.printStackTrace();
                        }
                    }
                }
            }
            setText(text);
        }
    }

    /**
     * Set the necessary spans for each spoiler.
     *
     * <p>The algorithm works in the same way as <code>setCodeFont</code>.
     *
     * @param sequence
     * @return
     */
    private CharSequence setSpoilerStyle(SpannableStringBuilder sequence, String subreddit) {
        int start = 0;
        int end = 0;
        for (int i = 0; i < sequence.length(); i++) {
            if (sequence.charAt(i) == '[' && i < sequence.length() - 3) {
                if (sequence.charAt(i + 1) == '['
                        && sequence.charAt(i + 2) == 's'
                        && sequence.charAt(i + 3) == '[') {
                    start = i;
                }
            } else if (sequence.charAt(i) == ']' && i < sequence.length() - 3) {
                if (sequence.charAt(i + 1) == 's'
                        && sequence.charAt(i + 2) == ']'
                        && sequence.charAt(i + 3) == ']') {
                    end = i;
                }
            }

            if (end > start) {
                sequence.delete(end, end + 4);
                sequence.delete(start, start + 4);

                BackgroundColorSpan backgroundColorSpan =
                        new BackgroundColorSpan(
                                Palette.getDarkerColor(Palette.getColor(subreddit)));
                ForegroundColorSpan foregroundColorSpan =
                        new ForegroundColorSpan(
                                Palette.getDarkerColor(Palette.getColor(subreddit)));
                ForegroundColorSpan underneathColorSpan = new ForegroundColorSpan(Color.WHITE);

                URLSpan urlSpan = sequence.getSpans(start, start, URLSpan.class)[0];
                sequence.setSpan(
                        urlSpan,
                        sequence.getSpanStart(urlSpan),
                        start - 1,
                        Spanned.SPAN_INCLUSIVE_INCLUSIVE);

                sequence.setSpan(
                        new URLSpanNoUnderline("#spoilerhidden"),
                        start,
                        end - 4,
                        Spannable.SPAN_INCLUSIVE_INCLUSIVE);
                // spoiler text has a space at the front
                sequence.setSpan(
                        backgroundColorSpan, start, end - 4, Spannable.SPAN_INCLUSIVE_INCLUSIVE);
                sequence.setSpan(
                        underneathColorSpan, start, end - 4, Spannable.SPAN_INCLUSIVE_INCLUSIVE);
                sequence.setSpan(
                        foregroundColorSpan, start, end - 4, Spannable.SPAN_INCLUSIVE_INCLUSIVE);

                storedSpoilerSpans.add(underneathColorSpan);
                storedSpoilerSpans.add(foregroundColorSpan);
                storedSpoilerSpans.add(backgroundColorSpan);
                // Shift 1 to account for remove of beginning "<"

                storedSpoilerStarts.add(start - 1);
                storedSpoilerStarts.add(start - 1);
                storedSpoilerStarts.add(start - 1);
                storedSpoilerEnds.add(end - 5);
                storedSpoilerEnds.add(end - 5);
                storedSpoilerEnds.add(end - 5);

                sequence.delete(start - 2, start - 1); // remove the trailing <
                start = 0;
                end = 0;
                i = i - 5; // move back to compensate for removal of [[s[
            }
        }

        return sequence;
    }

    private static class URLSpanNoUnderline extends URLSpan {
        public URLSpanNoUnderline(String url) {
            super(url);
        }

        @Override
        public void updateDrawState(TextPaint ds) {
            super.updateDrawState(ds);
            ds.setUnderlineText(false);
        }
    }

    /**
     * Sets the styling for string with code segments.
     *
     * <p>The general process is to search for <code>[[&lt;[</code> and <code>]&gt;]]</code> tokens
     * to find the code fragments within the escaped text. A <code>Spannable</code> is created which
     * which breaks up the origin sequence into non-code and code fragments, and applies a monospace
     * font to the code fragments.
     *
     * @param sequence the Spannable generated from Html.fromHtml
     * @return the message with monospace font applied to code fragments
     */
    private SpannableStringBuilder setCodeFont(SpannableStringBuilder sequence) {
        int start = 0;
        int end = 0;
        for (int i = 0; i < sequence.length(); i++) {
            if (sequence.charAt(i) == '[' && i < sequence.length() - 3) {
                if (sequence.charAt(i + 1) == '['
                        && sequence.charAt(i + 2) == '<'
                        && sequence.charAt(i + 3) == '[') {
                    start = i;
                }
            } else if (sequence.charAt(i) == ']' && i < sequence.length() - 3) {
                if (sequence.charAt(i + 1) == '>'
                        && sequence.charAt(i + 2) == ']'
                        && sequence.charAt(i + 3) == ']') {
                    end = i;
                }
            }

            if (end > start) {
                sequence.delete(end, end + 4);
                sequence.delete(start, start + 4);
                sequence.setSpan(
                        new TypefaceSpan("monospace"),
                        start,
                        end - 4,
                        Spannable.SPAN_INCLUSIVE_INCLUSIVE);
                start = 0;
                end = 0;
                i = i - 4; // move back to compensate for removal of [[<[
            }
        }

        return sequence;
    }

    private boolean isRedditPreviewImage(String url) {
        return (url.startsWith("https://preview.redd.it/") || url.startsWith("https://i.redd.it/")) && (url.endsWith(".jpeg") || url.endsWith(".jpg") || url.endsWith(".png") ||
                url.contains(".gif") || url.contains("format=pjpg") || url.contains("format=png"));
    }

    private void processRedditPreviewImages(SpannableStringBuilder builder) {
        Pattern previewPattern = Pattern.compile("https://preview\\.redd\\.it/[^\\s]+");
        Matcher previewMatcher = previewPattern.matcher(builder);

        Pattern iPattern = Pattern.compile("https://i\\.redd\\.it/[^\\s]+");
        Matcher iMatcher = iPattern.matcher(builder);

        List<MatchPair> matches = new ArrayList<>();

        // preview.redd.it
        while (previewMatcher.find()) {
            matches.add(new MatchPair(previewMatcher.start(), previewMatcher.end()));
        }

        // i.redd.it
        while (iMatcher.find()) {
            matches.add(new MatchPair(iMatcher.start(), iMatcher.end()));
        }

        // Process matches from last to first to avoid invalidating indices
        for (int i = matches.size() - 1; i >= 0; i--) {
            MatchPair match = matches.get(i);
            String url = builder.subSequence(match.start, match.end).toString();

            if (isRedditPreviewImage(url)) {
                // Set initial placeholder
                ColorDrawable placeholder = new ColorDrawable(Color.LTGRAY);
                placeholder.setBounds(0, 0, 300, 300);
                builder.setSpan(
                    new ImageSpan(placeholder),
                    match.start,
                    match.end,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                );

                // Load the actual image
                loadThumbnailFromUrl(url, bitmap -> {
                    post(() -> {
                        if (bitmap != null) {
                            try {
                                // Calculate dimensions maintaining aspect ratio
                                int maxWidth = Math.min(getWidth() - getPaddingLeft() - getPaddingRight(), 500);
                                int maxHeight = 300;  // Max height of 300dp

                                float widthScale = (float) maxWidth / bitmap.getWidth();
                                float heightScale = (float) maxHeight / bitmap.getHeight();
                                float scale = Math.min(widthScale, heightScale);

                                int scaledWidth = (int) (bitmap.getWidth() * scale);
                                int scaledHeight = (int) (bitmap.getHeight() * scale);

                                BitmapDrawable drawable = new BitmapDrawable(getResources(), bitmap);
                                drawable.setBounds(0, 0, scaledWidth, scaledHeight);

                                // Create new span with the loaded image
                                ImageSpan newSpan = new ImageSpan(drawable);

                                // Update the text
                                if (getText() instanceof Spannable) {
                                    Spannable spannable = (Spannable) getText();
                                    ImageSpan[] oldSpans = spannable.getSpans(match.start, match.end, ImageSpan.class);
                                    for (ImageSpan span : oldSpans) {
                                        spannable.removeSpan(span);
                                    }
                                    spannable.setSpan(
                                        newSpan,
                                        match.start,
                                        match.end,
                                        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                                    );

                                    Log.d("SpoilerRobotoTextView", "Replaced placeholder with image: " + scaledWidth + "x" + scaledHeight);
                                }

                                invalidate();
                                requestLayout();
                            } catch (Exception e) {
                                Log.e("SpoilerRobotoTextView", "Error updating image span", e);
                            }
                        }
                    });
                });
            }
        }
    }

    private String preprocessRedditVideoPlayerUrls(String html, net.dean.jraw.models.Submission submission, String subreddit) {
        android.util.Log.d("SpoilerRobotoTextView", "preprocessRedditVideoPlayerUrls called with HTML: " + html);

        // Pattern to match Reddit video player URLs in HTML links
        Pattern videoLinkPattern = Pattern.compile("<a href=\"(https://reddit\\.com/link/[^/]+/video/([^/]+)/player)\"[^>]*>([^<]*)</a>");
        Matcher matcher = videoLinkPattern.matcher(html);

        // Get media metadata from submission
        com.fasterxml.jackson.databind.JsonNode mediaMetadata = null;
        if (submission.getDataNode().has("media_metadata")) {
            mediaMetadata = submission.getDataNode().get("media_metadata");
            android.util.Log.d("SpoilerRobotoTextView", "Found media_metadata with keys: " + mediaMetadata.fieldNames());
        }

        if (mediaMetadata == null) {
            android.util.Log.d("SpoilerRobotoTextView", "No media_metadata found");
            return html; // No media metadata available
        }

        StringBuffer result = new StringBuffer();
        int matchCount = 0;

        while (matcher.find()) {
            matchCount++;
            String fullUrl = matcher.group(1);
            String videoId = matcher.group(2);
            String linkText = matcher.group(3);

            android.util.Log.d("SpoilerRobotoTextView", "Found video link " + matchCount + ": URL=" + fullUrl + ", videoId=" + videoId);

            if (mediaMetadata.has(videoId)) {
                com.fasterxml.jackson.databind.JsonNode videoData = mediaMetadata.get(videoId);

                // Check if this is a valid Reddit video
                if (videoData.has("e") && "RedditVideo".equals(videoData.get("e").asText())) {
                    // Replace the link with a special placeholder that includes video metadata
                    String replacement = String.format(
                        "<span class=\"reddit-video\" data-video-id=\"%s\" data-url=\"%s\">VIDEO_THUMBNAIL_%s</span>",
                        videoId, fullUrl, videoId
                    );
                    android.util.Log.d("SpoilerRobotoTextView", "Replacing with: " + replacement);
                    matcher.appendReplacement(result, replacement);
                } else {
                    android.util.Log.d("SpoilerRobotoTextView", "Not a valid Reddit video, keeping original link");
                    // Keep the original link if it's not a valid Reddit video
                    matcher.appendReplacement(result, matcher.group(0));
                }
            } else {
                android.util.Log.d("SpoilerRobotoTextView", "No metadata found for videoId: " + videoId);
                // Keep the original link if no metadata found
                matcher.appendReplacement(result, matcher.group(0));
            }
        }
        matcher.appendTail(result);

        String finalResult = result.toString();
        android.util.Log.d("SpoilerRobotoTextView", "Final preprocessed HTML: " + finalResult);
        return finalResult;
    }

    private void processVideoThumbnailPlaceholders(SpannableStringBuilder builder, net.dean.jraw.models.Submission submission, String subreddit) {
        android.util.Log.d("SpoilerRobotoTextView", "processVideoThumbnailPlaceholders called with text: " + builder.toString());

        // Get media metadata from submission
        com.fasterxml.jackson.databind.JsonNode mediaMetadata = null;
        if (submission.getDataNode().has("media_metadata")) {
            mediaMetadata = submission.getDataNode().get("media_metadata");
        }

        if (mediaMetadata == null) {
            android.util.Log.d("SpoilerRobotoTextView", "No media_metadata found in processVideoThumbnailPlaceholders");
            return; // No media metadata available
        }

        // Process placeholders one by one, recalculating positions each time
        Pattern placeholderPattern = Pattern.compile("VIDEO_THUMBNAIL_([^\\s]+)");

        while (true) {
            Matcher matcher = placeholderPattern.matcher(builder);
            if (!matcher.find()) {
                break; // No more placeholders found
            }

            int start = matcher.start();
            int end = matcher.end();
            String placeholderText = matcher.group(0);
            String videoId = matcher.group(1);

            android.util.Log.d("SpoilerRobotoTextView", "Processing placeholder: " + placeholderText + " at position " + start + "-" + end);
            android.util.Log.d("SpoilerRobotoTextView", "Extracted videoId: " + videoId);

            if (mediaMetadata.has(videoId)) {
                com.fasterxml.jackson.databind.JsonNode videoData = mediaMetadata.get(videoId);

                // Check if this is a valid Reddit video
                if (videoData.has("e") && "RedditVideo".equals(videoData.get("e").asText())) {
                    android.util.Log.d("SpoilerRobotoTextView", "Creating video thumbnail for: " + videoId);

                    // Get video dimensions
                    int videoWidth = videoData.has("x") ? videoData.get("x").asInt() : 300;
                    int videoHeight = videoData.has("y") ? videoData.get("y").asInt() : 300;

                    // Calculate thumbnail dimensions maintaining aspect ratio
                    // Use a default max width if view width is not available yet
                    int viewWidth = getWidth();
                    int maxWidth = viewWidth > 0 ?
                        Math.min(viewWidth - getPaddingLeft() - getPaddingRight(), 500) : 400;
                    int maxHeight = 300;

                    float widthScale = (float) maxWidth / videoWidth;
                    float heightScale = (float) maxHeight / videoHeight;
                    float scale = Math.min(widthScale, heightScale);

                    int thumbnailWidth = Math.max((int) (videoWidth * scale), 100); // Minimum 100px width
                    int thumbnailHeight = Math.max((int) (videoHeight * scale), 100); // Minimum 100px height

                    android.util.Log.d("SpoilerRobotoTextView", "Thumbnail dimensions: " + thumbnailWidth + "x" + thumbnailHeight);

                    // Replace the placeholder text with a single character
                    builder.replace(start, end, "\uFFFC"); // Object replacement character

                    // The position is now start (where we just inserted the replacement character)
                    int finalPosition = start;

                    // Create initial placeholder while loading actual thumbnail
                    android.graphics.drawable.ColorDrawable initialPlaceholder = new android.graphics.drawable.ColorDrawable(android.graphics.Color.LTGRAY);
                    initialPlaceholder.setBounds(0, 0, thumbnailWidth, thumbnailHeight);

                    // Create an ImageSpan with initial placeholder
                    android.text.style.ImageSpan imageSpan = new android.text.style.ImageSpan(initialPlaceholder, android.text.style.DynamicDrawableSpan.ALIGN_BOTTOM);

                    // Create a ClickableSpan for handling clicks
                    VideoPlayerClickSpan clickSpan = new VideoPlayerClickSpan(videoData, subreddit, submission);

                    // Apply both spans to the placeholder character
                    builder.setSpan(
                        imageSpan,
                        finalPosition,
                        finalPosition + 1, // Only span the single replacement character
                        android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                    );

                    builder.setSpan(
                        clickSpan,
                        finalPosition,
                        finalPosition + 1, // Only span the single replacement character
                        android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                    );

                    // Load actual video thumbnail asynchronously
                    loadVideoThumbnail(videoData, finalPosition, thumbnailWidth, thumbnailHeight);
                } else {
                    android.util.Log.d("SpoilerRobotoTextView", "Not a valid Reddit video");
                    break; // Skip this placeholder and continue
                }
            } else {
                android.util.Log.d("SpoilerRobotoTextView", "No metadata found for videoId: " + videoId);
                break; // Skip this placeholder and continue
            }
        }
    }

    private android.graphics.drawable.LayerDrawable createVideoThumbnailPlaceholder(int width, int height) {
        // Create a gray background
        android.graphics.drawable.ColorDrawable background = new android.graphics.drawable.ColorDrawable(android.graphics.Color.LTGRAY);
        background.setBounds(0, 0, width, height);

        // Create a play button overlay
        android.graphics.drawable.Drawable playIcon = androidx.core.content.ContextCompat.getDrawable(getContext(), R.drawable.ic_play_arrow);
        if (playIcon != null) {
            // Center the play button
            int iconSize = Math.min(width, height) / 4;
            int iconLeft = (width - iconSize) / 2;
            int iconTop = (height - iconSize) / 2;
            playIcon.setBounds(iconLeft, iconTop, iconLeft + iconSize, iconTop + iconSize);
            playIcon.setTint(android.graphics.Color.WHITE);
        }

        android.graphics.drawable.Drawable[] layers = playIcon != null ?
            new android.graphics.drawable.Drawable[]{background, playIcon} :
            new android.graphics.drawable.Drawable[]{background};

        android.graphics.drawable.LayerDrawable layerDrawable = new android.graphics.drawable.LayerDrawable(layers);
        layerDrawable.setBounds(0, 0, width, height);

        return layerDrawable;
    }

    // Custom span class for video thumbnails that handles click functionality
    private class VideoPlayerClickSpan extends android.text.style.ClickableSpan {
        private final com.fasterxml.jackson.databind.JsonNode videoData;
        private final String subreddit;
        private final net.dean.jraw.models.Submission submission;

        public VideoPlayerClickSpan(com.fasterxml.jackson.databind.JsonNode videoData, String subreddit, net.dean.jraw.models.Submission submission) {
            this.videoData = videoData;
            this.subreddit = subreddit;
            this.submission = submission;
        }

        @Override
        public void onClick(View widget) {
            android.util.Log.d("SpoilerRobotoTextView", "VideoPlayerClickSpan onClick called!");

            // Extract video URLs from the metadata
            String dashUrl = null;
            String hlsUrl = null;

            if (videoData.has("dashUrl")) {
                dashUrl = videoData.get("dashUrl").asText();
            }
            if (videoData.has("hlsUrl")) {
                hlsUrl = videoData.get("hlsUrl").asText();
            }

            // Use DASH URL if available, otherwise HLS
            String videoUrl = dashUrl != null ? dashUrl : hlsUrl;

            android.util.Log.d("SpoilerRobotoTextView", "Video URL: " + videoUrl);

            if (videoUrl != null) {
                // Open the video in MediaView
                android.content.Context context = getContext();
                android.app.Activity activity = null;

                if (context instanceof android.app.Activity) {
                    activity = (android.app.Activity) context;
                } else if (context instanceof androidx.appcompat.view.ContextThemeWrapper) {
                    activity = (android.app.Activity) ((androidx.appcompat.view.ContextThemeWrapper) context).getBaseContext();
                } else if (context instanceof android.content.ContextWrapper) {
                    android.content.Context context1 = ((android.content.ContextWrapper) context).getBaseContext();
                    if (context1 instanceof android.app.Activity) {
                        activity = (android.app.Activity) context1;
                    }
                }

                if (activity != null && me.edgan.redditslide.SettingValues.video) {
                    android.util.Log.d("SpoilerRobotoTextView", "Opening video in MediaView");
                    android.content.Intent intent = new android.content.Intent(activity, me.edgan.redditslide.Activities.MediaView.class);
                    intent.putExtra(me.edgan.redditslide.Activities.MediaView.EXTRA_URL, videoUrl);
                    intent.putExtra(me.edgan.redditslide.Activities.MediaView.SUBREDDIT, subreddit);
                    if (submission != null) {
                        intent.putExtra("EXTRA_SUBMISSION_TITLE", submission.getTitle());
                    }
                    activity.startActivity(intent);
                } else if (activity != null) {
                    android.util.Log.d("SpoilerRobotoTextView", "Opening video externally");
                    me.edgan.redditslide.util.LinkUtil.openExternally(videoUrl);
                }
            } else {
                android.util.Log.e("SpoilerRobotoTextView", "No video URL found");
            }
        }

        @Override
        public void updateDrawState(android.text.TextPaint ds) {
            // Don't underline the video thumbnail
        }
    }

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private static final ExecutorService THUMBNAIL_LOAD_EXECUTOR =
            Executors.newFixedThreadPool(Math.max(2, Math.min(Runtime.getRuntime().availableProcessors() * 2, 8)));

    private static final LruCache<String, Bitmap> THUMBNAIL_CACHE;

    static {
        THUMBNAIL_CACHE = new LruCache<>(calculateThumbnailCacheMemSize());
    }

    private static int calculateThumbnailCacheMemSize() {
        // Using a fixed proportion of max memory for the cache.
        // Consider making this configurable or more dynamic if needed.
        final int maxMemory = (int) (Runtime.getRuntime().maxMemory() / 1024); // Get memory in KB
        return maxMemory / 8; // Use 1/8th of available memory for the cache
    }

    private void loadThumbnailFromUrl(final String url, final ImageCallback callback) {
        Bitmap cachedThumbnail = THUMBNAIL_CACHE.get(url);
        if (cachedThumbnail != null) {
            callback.onImageLoaded(cachedThumbnail);
            return;
        }
        THUMBNAIL_LOAD_EXECUTOR.execute(() -> {
            HttpURLConnection connection = null;
            InputStream input = null;
            try {
                Log.d("SpoilerRobotoTextView", "Loading thumbnail from: " + url);
                connection = (HttpURLConnection) new URL(url).openConnection();
                connection.setDoInput(true);
                connection.connect();
                input = connection.getInputStream();
                BitmapFactory.Options options = new BitmapFactory.Options();
                options.inJustDecodeBounds = true;
                BitmapFactory.decodeStream(input, null, options);
                input.close();
                connection.disconnect();

                int reqWidth = 300;
                int reqHeight = 300;

                options.inSampleSize = calculateInSampleSize(options, reqWidth, reqHeight);
                options.inJustDecodeBounds = false;

                connection = (HttpURLConnection) new URL(url).openConnection();
                connection.setDoInput(true);
                connection.connect();
                input = connection.getInputStream();
                final Bitmap thumbnail = BitmapFactory.decodeStream(input, null, options);
                input.close();

                if (thumbnail != null) {
                    THUMBNAIL_CACHE.put(url, thumbnail);
                    Log.d("SpoilerRobotoTextView", "Thumbnail loaded successfully, size: "
                            + thumbnail.getWidth() + "x" + thumbnail.getHeight());
                    callback.onImageLoaded(thumbnail);
                } else {
                    Log.e("SpoilerRobotoTextView", "Failed to decode thumbnail from: " + url);
                    callback.onImageLoaded(null);
                }
            } catch (Exception e) {
                Log.e("SpoilerRobotoTextView", "Error loading thumbnail: " + url, e);
                callback.onImageLoaded(null);
            } finally {
                if (input != null) {
                    try {
                        input.close();
                    } catch (Exception e) {
                        Log.e("SpoilerRobotoTextView", "Error closing input stream: " + url, e);
                    }
                }
                if (connection != null) {
                    connection.disconnect();
                }
            }
        });
    }

    public static int calculateInSampleSize(BitmapFactory.Options options, int reqWidth, int reqHeight) {
        final int height = options.outHeight;
        final int width = options.outWidth;
        int inSampleSize = 1;
        if (height > reqHeight || width > reqWidth) {
            int heightRatio = (int) Math.floor((float) height / reqHeight);
            int widthRatio = (int) Math.floor((float) width / reqWidth);
            int ratio = Math.min(heightRatio, widthRatio);
            while (inSampleSize < ratio) {
                inSampleSize *= 2;
            }
        }
        return inSampleSize;
    }

    // Simple callback interface
    private interface ImageCallback {
        void onImageLoaded(Bitmap bitmap);
    }

    /**
     * Replaces Markdown code block delimiters wrapped in paragraph tags
     * with standard HTML code tags.
     *
     * @param html The input HTML string.
     * @return The HTML string with code blocks replaced.
     */
    private String replaceCodeBlocks(String html) {
        Log.d("SpoilerRobotoTextView", "Initial HTML for code block replacement: " + html);
        StringBuffer sb = new StringBuffer();

        // Refined Pattern:
        // 1. `<div>\s*```(.*?)```\s*</div>`: Matches standard ```content``` within a div, capturing content.
        // 2. `<div>\s*([^`\s][^<]*)```\s*</div>`: Matches content``` within a div, where content doesn't start with ` or whitespace, and doesn't contain '<'.
        Pattern combinedPattern = Pattern.compile("<div>\\s*(?:```(.*?)```|([^`\\s][^<]*)```)\\s*</div>", Pattern.DOTALL);
        Matcher matcher = combinedPattern.matcher(html);

        while (matcher.find()) {
            String content;
            if (matcher.group(1) != null) {
                // Matched ```content```
                content = matcher.group(1);
            } else {
                // Matched content```
                content = matcher.group(2);
            }
            content = content.trim(); // Trim whitespace

            // Replace the entire matched <div>...</div> block
            String replacement = "<div><code>[[&lt;[" + Matcher.quoteReplacement(content) + "]&gt;]]</code></div>";
            matcher.appendReplacement(sb, replacement);
        }
        matcher.appendTail(sb);
        String result = sb.toString();
        Log.d("SpoilerRobotoTextView", "Final HTML after code block replacement: " + result);

        return result.toString();
    }

    private void loadVideoThumbnail(com.fasterxml.jackson.databind.JsonNode videoData, int position, int targetWidth, int targetHeight) {
        android.util.Log.d("SpoilerRobotoTextView", "loadVideoThumbnail called for position " + position + " with dimensions " + targetWidth + "x" + targetHeight);
        android.util.Log.d("SpoilerRobotoTextView", "Video data: " + videoData.toString());

        // Extract video URLs from the metadata
        String dashUrl = null;
        String hlsUrl = null;
        String fallbackUrl = null;

        if (videoData.has("dashUrl")) {
            dashUrl = videoData.get("dashUrl").asText();
        }
        if (videoData.has("hlsUrl")) {
            hlsUrl = videoData.get("hlsUrl").asText();
        }
        if (videoData.has("fallbackUrl")) {
            fallbackUrl = videoData.get("fallbackUrl").asText();
        }

        android.util.Log.d("SpoilerRobotoTextView", "Extracted URLs - dashUrl: " + dashUrl + ", hlsUrl: " + hlsUrl + ", fallbackUrl: " + fallbackUrl);

        // Try to extract thumbnail from video file
        // First try fallback URL if available, then construct direct URLs
        java.util.List<String> videoUrls = new java.util.ArrayList<>();

        // Add fallback URL first if available
        if (fallbackUrl != null && !fallbackUrl.isEmpty()) {
            videoUrls.add(fallbackUrl);
            android.util.Log.d("SpoilerRobotoTextView", "Added fallback URL: " + fallbackUrl);
        }

        // Try to construct direct video URLs from DASH URL
        if (dashUrl != null && dashUrl.contains("v.redd.it")) {
            String baseUrl = dashUrl;
            if (baseUrl.contains("?")) {
                baseUrl = baseUrl.substring(0, baseUrl.indexOf("?"));
            }

            // Extract video ID from the DASH URL
            java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("v\\.redd\\.it/(?:link/[^/]+/asset/)?([^/]+)");
            java.util.regex.Matcher matcher = pattern.matcher(baseUrl);
            if (matcher.find()) {
                String videoId = matcher.group(1);
                // Try multiple video quality options
                videoUrls.add("https://v.redd.it/" + videoId + "/DASH_480.mp4");
                videoUrls.add("https://v.redd.it/" + videoId + "/DASH_360.mp4");
                videoUrls.add("https://v.redd.it/" + videoId + "/DASH_720.mp4");
                videoUrls.add("https://v.redd.it/" + videoId + "/DASH_240.mp4");
                android.util.Log.d("SpoilerRobotoTextView", "Added constructed video URLs for ID: " + videoId);
            }
        }

        if (!videoUrls.isEmpty()) {
            tryVideoFrameExtraction(videoUrls, 0, position, targetWidth, targetHeight);
        } else {
            android.util.Log.d("SpoilerRobotoTextView", "No video URLs available, using fallback thumbnail");
            createFallbackVideoThumbnail(position, targetWidth, targetHeight);
        }
    }

    private void extractVideoFrame(String videoUrl, int position, int targetWidth, int targetHeight) {
        android.util.Log.d("SpoilerRobotoTextView", "Extracting video frame from: " + videoUrl);

        executor.execute(() -> {
            android.media.MediaMetadataRetriever retriever = null;
            try {
                retriever = new android.media.MediaMetadataRetriever();

                // Set headers for Reddit video requests
                java.util.Map<String, String> headers = new java.util.HashMap<>();
                headers.put("User-Agent", "android:me.edgan.redditslide:v6.0.0 (by /u/edgan)");

                // Set connection timeout
                retriever.setDataSource(videoUrl, headers);

                // Get frame at 1 second (1,000,000 microseconds) or beginning if video is shorter
                android.graphics.Bitmap frame = retriever.getFrameAtTime(1000000, android.media.MediaMetadataRetriever.OPTION_CLOSEST_SYNC);

                if (frame != null) {
                    android.util.Log.d("SpoilerRobotoTextView", "Successfully extracted video frame: " + frame.getWidth() + "x" + frame.getHeight());

                    post(() -> {
                        createVideoThumbnailWithImage(frame, position, targetWidth, targetHeight);
                    });
                } else {
                    android.util.Log.e("SpoilerRobotoTextView", "Failed to extract frame from video");
                    post(() -> {
                        createFallbackVideoThumbnail(position, targetWidth, targetHeight);
                    });
                }

            } catch (Exception e) {
                android.util.Log.e("SpoilerRobotoTextView", "Error extracting video frame from: " + videoUrl, e);
                post(() -> {
                    createFallbackVideoThumbnail(position, targetWidth, targetHeight);
                });
            } finally {
                if (retriever != null) {
                    try {
                        retriever.release();
                    } catch (Exception e) {
                        android.util.Log.e("SpoilerRobotoTextView", "Error releasing MediaMetadataRetriever", e);
                    }
                }
            }
        });
    }

    private void tryAlternativeVideoUrls(String originalUrl, int position, int targetWidth, int targetHeight) {
        android.util.Log.d("SpoilerRobotoTextView", "Trying alternative video URLs for frame extraction");

        // Extract video ID from original URL
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("v\\.redd\\.it/([^/]+)");
        java.util.regex.Matcher matcher = pattern.matcher(originalUrl);

        if (matcher.find()) {
            String videoId = matcher.group(1);

            // Try different video quality/format URLs
            java.util.List<String> alternativeUrls = new java.util.ArrayList<>();
            alternativeUrls.add("https://v.redd.it/" + videoId + "/DASH_480.mp4");
            alternativeUrls.add("https://v.redd.it/" + videoId + "/DASH_360.mp4");
            alternativeUrls.add("https://v.redd.it/" + videoId + "/DASH_240.mp4");
            alternativeUrls.add("https://v.redd.it/" + videoId + "/DASH_1080.mp4");
            alternativeUrls.add("https://v.redd.it/" + videoId + "/DASH_96.mp4");

            tryVideoFrameExtraction(alternativeUrls, 0, position, targetWidth, targetHeight);
        } else {
            android.util.Log.d("SpoilerRobotoTextView", "Could not extract video ID, using fallback thumbnail");
            createFallbackVideoThumbnail(position, targetWidth, targetHeight);
        }
    }

    private void tryVideoFrameExtraction(java.util.List<String> urls, int index, int position, int targetWidth, int targetHeight) {
        if (index >= urls.size()) {
            android.util.Log.d("SpoilerRobotoTextView", "All video URLs failed, using fallback thumbnail");
            createFallbackVideoThumbnail(position, targetWidth, targetHeight);
            return;
        }

        String currentUrl = urls.get(index);
        android.util.Log.d("SpoilerRobotoTextView", "Trying video URL " + (index + 1) + "/" + urls.size() + ": " + currentUrl);

        // Use a separate executor with timeout for video frame extraction
        // java.util.concurrent.ExecutorService timeoutExecutor = java.util.concurrent.Executors.newSingleThreadExecutor();
        java.util.concurrent.Future<android.graphics.Bitmap> future = THUMBNAIL_LOAD_EXECUTOR.submit(() -> {
            android.media.MediaMetadataRetriever retriever = null;
            try {
                retriever = new android.media.MediaMetadataRetriever();

                java.util.Map<String, String> headers = new java.util.HashMap<>();
                headers.put("User-Agent", "android:me.edgan.redditslide:v6.0.0 (by /u/edgan)");

                android.util.Log.d("SpoilerRobotoTextView", "Setting data source for: " + currentUrl);
                retriever.setDataSource(currentUrl, headers);

                android.util.Log.d("SpoilerRobotoTextView", "Extracting frame at 0.5 seconds");
                android.graphics.Bitmap frame = retriever.getFrameAtTime(500000, android.media.MediaMetadataRetriever.OPTION_CLOSEST_SYNC);

                if (frame != null) {
                    android.util.Log.d("SpoilerRobotoTextView", "Successfully extracted frame: " + frame.getWidth() + "x" + frame.getHeight());
                } else {
                    android.util.Log.e("SpoilerRobotoTextView", "Frame extraction returned null");
                }

                return frame;

            } catch (Exception e) {
                if (e instanceof RuntimeException && e.getMessage() != null && e.getMessage().startsWith("setDataSource failed: status = 0x80000000")) {
                    Log.w("SpoilerRobotoTextView", "Known MediaMetadataRetriever failure (likely from a timed-out and cancelled attempt) for " + currentUrl + ": " + e.toString());
                } else if (Thread.currentThread().isInterrupted()) {
                    Log.w("SpoilerRobotoTextView", "Interrupted task for frame extraction from " + currentUrl + " ended with exception: " + e.toString());
                } else {
                    Log.e("SpoilerRobotoTextView", "Exception during frame extraction from: " + currentUrl, e);
                }
                return null;
            } finally {
                if (retriever != null) {
                    try {
                        retriever.release();
                    } catch (Exception e) {
                        android.util.Log.e("SpoilerRobotoTextView", "Error releasing MediaMetadataRetriever", e);
                    }
                }
            }
        });

        // Handle the result with timeout
        THUMBNAIL_LOAD_EXECUTOR.execute(() -> {
            try {
                // Wait for result with 8 second timeout
                android.graphics.Bitmap frame = future.get(3, java.util.concurrent.TimeUnit.SECONDS);

                if (frame != null) {
                    android.util.Log.d("SpoilerRobotoTextView", "Successfully extracted frame from URL: " + currentUrl);
                    post(() -> {
                        createVideoThumbnailWithImage(frame, position, targetWidth, targetHeight);
                    });
                } else {
                    android.util.Log.e("SpoilerRobotoTextView", "Frame extraction failed for: " + currentUrl);
                    // Try next URL
                    // post(() -> {
                    //    tryVideoFrameExtraction(urls, index + 1, position, targetWidth, targetHeight);
                    // });
                    THUMBNAIL_LOAD_EXECUTOR.execute(() -> tryVideoFrameExtraction(urls, index + 1, position, targetWidth, targetHeight));
                }

            } catch (java.util.concurrent.TimeoutException e) {
                android.util.Log.e("SpoilerRobotoTextView", "Timeout extracting frame from: " + currentUrl);
                future.cancel(true);
                // Try next URL
                // post(() -> {
                //    tryVideoFrameExtraction(urls, index + 1, position, targetWidth, targetHeight);
                // });
                THUMBNAIL_LOAD_EXECUTOR.execute(() -> tryVideoFrameExtraction(urls, index + 1, position, targetWidth, targetHeight));
            } catch (Exception e) {
                android.util.Log.e("SpoilerRobotoTextView", "Error waiting for frame extraction: " + currentUrl, e);
                // Try next URL
                // post(() -> {
                //    tryVideoFrameExtraction(urls, index + 1, position, targetWidth, targetHeight);
                // });
                THUMBNAIL_LOAD_EXECUTOR.execute(() -> tryVideoFrameExtraction(urls, index + 1, position, targetWidth, targetHeight));
            } finally {
                // timeoutExecutor.shutdown();
            }
        });
    }

    private void createVideoThumbnailWithImage(android.graphics.Bitmap originalBitmap, int position, int targetWidth, int targetHeight) {
        try {
            // Scale the bitmap to target dimensions while maintaining aspect ratio
            float bitmapAspect = (float) originalBitmap.getWidth() / originalBitmap.getHeight();
            float targetAspect = (float) targetWidth / targetHeight;

            int scaledWidth, scaledHeight;
            if (bitmapAspect > targetAspect) {
                // Bitmap is wider than target, fit to width
                scaledWidth = targetWidth;
                scaledHeight = (int) (targetWidth / bitmapAspect);
            } else {
                // Bitmap is taller than target, fit to height
                scaledHeight = targetHeight;
                scaledWidth = (int) (targetHeight * bitmapAspect);
            }

            // Create scaled bitmap
            android.graphics.Bitmap scaledBitmap = android.graphics.Bitmap.createScaledBitmap(
                originalBitmap, scaledWidth, scaledHeight, true);

            // Create final bitmap with target dimensions (may have letterboxing)
            android.graphics.Bitmap finalBitmap = android.graphics.Bitmap.createBitmap(
                targetWidth, targetHeight, android.graphics.Bitmap.Config.ARGB_8888);
            android.graphics.Canvas canvas = new android.graphics.Canvas(finalBitmap);

            // Fill with black background
            canvas.drawColor(android.graphics.Color.BLACK);

            // Center the scaled image
            int offsetX = (targetWidth - scaledWidth) / 2;
            int offsetY = (targetHeight - scaledHeight) / 2;
            canvas.drawBitmap(scaledBitmap, offsetX, offsetY, null);

            // Add play button overlay
            addPlayButtonOverlay(canvas, targetWidth, targetHeight);

            // Replace the ImageSpan
            replaceImageSpan(finalBitmap, position, targetWidth, targetHeight);

        } catch (Exception e) {
            android.util.Log.e("SpoilerRobotoTextView", "Error creating video thumbnail with image", e);
            createFallbackVideoThumbnail(position, targetWidth, targetHeight);
        }
    }

    private void createFallbackVideoThumbnail(int position, int targetWidth, int targetHeight) {
        post(() -> {
            android.util.Log.d("SpoilerRobotoTextView", "Creating fallback video thumbnail for position " + position);
            try {
                // Create a dark background
                android.graphics.Bitmap bitmap = android.graphics.Bitmap.createBitmap(targetWidth, targetHeight, android.graphics.Bitmap.Config.ARGB_8888);
                android.graphics.Canvas canvas = new android.graphics.Canvas(bitmap);

                // Fill with dark background
                android.graphics.Paint backgroundPaint = new android.graphics.Paint();
                backgroundPaint.setColor(android.graphics.Color.parseColor("#2C2C2C")); // Dark gray
                canvas.drawRect(0, 0, targetWidth, targetHeight, backgroundPaint);

                // Add play button overlay
                addPlayButtonOverlay(canvas, targetWidth, targetHeight);

                // Add "VIDEO" text at the bottom
                android.graphics.Paint textPaint = new android.graphics.Paint();
                textPaint.setColor(android.graphics.Color.WHITE);
                textPaint.setTextSize(targetHeight * 0.08f); // 8% of height
                textPaint.setAntiAlias(true);
                textPaint.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);

                String videoText = "REDDIT VIDEO";
                float textWidth = textPaint.measureText(videoText);
                float textX = (targetWidth - textWidth) / 2;
                float textY = targetHeight - (targetHeight * 0.1f); // 10% from bottom

                // Draw text background
                android.graphics.Paint textBgPaint = new android.graphics.Paint();
                textBgPaint.setColor(android.graphics.Color.BLACK);
                textBgPaint.setAlpha(150);
                canvas.drawRect(textX - 10, textY - textPaint.getTextSize(), textX + textWidth + 10, textY + 5, textBgPaint);

                canvas.drawText(videoText, textX, textY, textPaint);

                // Replace the ImageSpan
                replaceImageSpan(bitmap, position, targetWidth, targetHeight);

            } catch (Exception e) {
                android.util.Log.e("SpoilerRobotoTextView", "Error creating fallback video thumbnail", e);
            }
        });
    }

    private void addPlayButtonOverlay(android.graphics.Canvas canvas, int width, int height) {
        // Draw play button
        android.graphics.drawable.Drawable playIcon = androidx.core.content.ContextCompat.getDrawable(getContext(), R.drawable.ic_play_arrow);
        if (playIcon != null) {
            int iconSize = Math.min(width, height) / 3;
            int iconLeft = (width - iconSize) / 2;
            int iconTop = (height - iconSize) / 2;

            // Add semi-transparent circle background for play button
            android.graphics.Paint circlePaint = new android.graphics.Paint();
            circlePaint.setColor(android.graphics.Color.BLACK);
            circlePaint.setAlpha(180); // More opaque
            circlePaint.setAntiAlias(true);
            canvas.drawCircle(iconLeft + iconSize/2, iconTop + iconSize/2, iconSize/2 + 15, circlePaint);

            playIcon.setBounds(iconLeft, iconTop, iconLeft + iconSize, iconTop + iconSize);
            playIcon.setTint(android.graphics.Color.WHITE);
            playIcon.draw(canvas);
        }
    }

    private void replaceImageSpan(android.graphics.Bitmap bitmap, int position, int targetWidth, int targetHeight) {
        // Create drawable with the video thumbnail
        android.graphics.drawable.BitmapDrawable drawable = new android.graphics.drawable.BitmapDrawable(getResources(), bitmap);
        drawable.setBounds(0, 0, targetWidth, targetHeight);

        android.util.Log.d("SpoilerRobotoTextView", "Looking for ImageSpan at position " + position);

        // Find and replace the placeholder ImageSpan
        android.text.Spannable spannable = (android.text.Spannable) getText();
        if (spannable != null && position < spannable.length()) {
            android.text.style.ImageSpan[] imageSpans = spannable.getSpans(position, position + 1, android.text.style.ImageSpan.class);
            android.util.Log.d("SpoilerRobotoTextView", "Found " + imageSpans.length + " ImageSpans at position " + position);

            if (imageSpans.length > 0) {
                // Remove old span
                spannable.removeSpan(imageSpans[0]);

                // Add new span with video thumbnail
                android.text.style.ImageSpan newSpan = new android.text.style.ImageSpan(drawable, android.text.style.DynamicDrawableSpan.ALIGN_BOTTOM);
                spannable.setSpan(newSpan, position, position + 1, android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);

                android.util.Log.d("SpoilerRobotoTextView", "Successfully replaced ImageSpan at position " + position);

                invalidate();
                requestLayout();
            } else {
                android.util.Log.e("SpoilerRobotoTextView", "No ImageSpan found at position " + position);
            }
        } else {
            android.util.Log.e("SpoilerRobotoTextView", "Invalid spannable or position: spannable=" + (spannable != null) + ", position=" + position + ", length=" + (spannable != null ? spannable.length() : "null"));
        }
    }
}

