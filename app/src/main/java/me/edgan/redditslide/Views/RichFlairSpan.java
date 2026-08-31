package me.edgan.redditslide.Views;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ReplacementSpan;
import android.widget.TextView;
import androidx.annotation.ColorInt;
import androidx.annotation.Nullable;
import com.devspark.robototextview.RobotoTypefaces;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import me.edgan.redditslide.Flair.RichFlairParser;
import me.edgan.redditslide.Flair.Richtext;
import me.edgan.redditslide.util.FlairEmojiUtil;
import org.jspecify.annotations.NullMarked;

/**
 * Draws a flair pill whose contents mix text and emoji images — Reddit's richtext flair.
 *
 * <p>This exists because {@link RoundedBackgroundSpan} cannot hold one. It is a
 * {@link ReplacementSpan}, and a replacement span owns its whole range: it draws the text itself, so
 * character-level spans set underneath it (an {@code ImageSpan}, say) are never drawn. Since
 * {@code ImageSpan} is itself a replacement span the two also overlap illegally. One span therefore
 * has to own the pill and everything in it.
 *
 * <p>The geometry deliberately mirrors {@link RoundedBackgroundSpan} — same inset, same corner
 * radius, same baseline — so an emoji flair sits exactly where a text flair would. Flairs with no
 * emoji keep using {@link RoundedBackgroundSpan} untouched, so no existing pill moves.
 *
 * <p>Resolved bitmaps are frozen between {@link #resolve} calls. {@link #getSize} and
 * {@link #draw} must agree on the width, and they cannot if an image is allowed to arrive between
 * the two; the refill path calls {@link #resolve} and then asks the view to lay out again.
 */
@NullMarked
public class RichFlairSpan extends ReplacementSpan {

    /** Matches RoundedBackgroundSpan, so the two kinds of pill have identical corners. */
    private static final int CORNER_RADIUS = 8;

    /** The pill's inset from the line box when drawn at half size, as RoundedBackgroundSpan does. */
    private static final int HALF_INSET_DIVISOR = 6;

    /** Padding either side of the flair's contents, matching the NBSPs a text pill is built with. */
    private static final String PADDING = "\u00A0";

    private final List<Richtext> segments;
    private final int textColor;
    private final int backgroundColor;
    private final boolean half;
    private final Context context;

    /** url -> bitmap, written only by {@link #resolve} so a measure and its draw always agree. */
    private final Map<String, Bitmap> resolved = new ConcurrentHashMap<>();

    private final Rect source = new Rect();
    private final RectF pill = new RectF();
    private final RectF emoji = new RectF();

    public RichFlairSpan(
            List<Richtext> segments,
            @ColorInt int textColor,
            @ColorInt int backgroundColor,
            boolean half,
            Context context) {
        this.segments = segments;
        this.textColor = textColor;
        this.backgroundColor = backgroundColor;
        this.half = half;
        // The application context, not the one passed in. Colours are already resolved to ints
        // above, and the only later uses are the shared image cache and the typeface — neither
        // needs an Activity. It matters because SubmissionCache.titles is never cleared, so a span
        // holding an Activity would pin it for the life of the process.
        this.context = context.getApplicationContext();

        resolve();
    }

    /**
     * Picks up every emoji bitmap that is currently in the memory cache. Called once when the span
     * is built — on the background thread that warmed the cache, in the feed's case — and again by
     * the refill path once a late image has arrived.
     *
     * @return whether anything changed, i.e. whether the span needs to be measured again.
     */
    public boolean resolve() {
        boolean changed = false;

        for (Richtext segment : segments) {
            if (!RichFlairParser.isEmoji(segment)) {
                continue;
            }

            final String url = segment.getU();

            if (url == null) {
                continue;
            }

            final Bitmap existing = resolved.get(url);

            if (existing != null && !existing.isRecycled()) {
                continue;
            }

            final Bitmap bitmap = lookup(url);

            if (bitmap != null && !bitmap.isRecycled()) {
                resolved.put(url, bitmap);
                changed = true;
            } else if (existing != null) {
                // The cache dropped it and recycled the bitmap; stop drawing it.
                resolved.remove(url);
                changed = true;
            }
        }

        return changed;
    }

    /**
     * The bitmap for one emoji URL, or null if it is not cached yet.
     *
     * <p>Overridable so the screenshot tests can supply a stub instead of standing up the whole
     * image loader. Called from the constructor, so an override must not depend on its own
     * subclass's fields being initialised.
     */
    protected @Nullable Bitmap lookup(String url) {
        return FlairEmojiUtil.cachedBitmap(context, url);
    }

    /** The emoji URLs still without a bitmap, for the caller to fetch. Empty once fully drawn. */
    public List<String> unresolvedUrls() {
        final List<String> urls = new ArrayList<>();

        for (Richtext segment : segments) {
            final String url = segment.getU();

            if (RichFlairParser.isEmoji(segment) && url != null && !resolved.containsKey(url)) {
                urls.add(url);
            }
        }

        return urls;
    }

    /**
     * The ready-to-append chip: the flair flattened to text, with this span drawn over all of it.
     * The text underneath matters even though the span paints over it — it is what gets copied,
     * searched and read aloud, and what shows if the span is ever dropped.
     */
    public static SpannableStringBuilder chip(
            List<Richtext> segments,
            @ColorInt int textColor,
            @ColorInt int backgroundColor,
            boolean half,
            Context context) {
        final SpannableStringBuilder chip =
                new SpannableStringBuilder(
                        "\u00A0" + RichFlairParser.plainText(segments) + "\u00A0");

        chip.setSpan(
                new RichFlairSpan(segments, textColor, backgroundColor, half, context),
                0,
                chip.length(),
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

        return chip;
    }

    /**
     * Fetches any emoji this view's flairs are still missing and redraws once they arrive.
     *
     * <p>Needed by the screens that never run a preload — profile, saved, moderator, search — where
     * the flair first paints with its {@code :alias:} fallback. The feed and comments screen warm
     * the cache ahead of the bind, so this normally finds nothing to do.
     *
     * <p>Recycling is handled by identity rather than a generation counter: the view is only asked
     * to lay out again if it still holds the very span that was being filled. Span instances
     * survive the copy {@code SubmissionModActions.doText} makes of the cached title, so the check
     * holds there too.
     */
    public static void refill(TextView view) {
        final CharSequence text = view.getText();

        if (!(text instanceof Spanned)) {
            return;
        }

        final RichFlairSpan[] spans =
                ((Spanned) text).getSpans(0, text.length(), RichFlairSpan.class);

        if (spans.length == 0) {
            return;
        }

        for (RichFlairSpan span : spans) {
            for (String url : span.unresolvedUrls()) {
                FlairEmojiUtil.loadAsync(
                        view.getContext(),
                        url,
                        () -> {
                            boolean changed = false;

                            for (RichFlairSpan pending : spans) {
                                changed |= pending.resolve();
                            }

                            if (changed && stillShowing(view, spans)) {
                                // The pill is wider now that an alias has become an image, so this
                                // has to be a fresh measure, not just a repaint.
                                view.requestLayout();
                                view.invalidate();
                            }
                        });
            }
        }
    }

    /** Whether {@code view} still holds these span instances, i.e. was not recycled meanwhile. */
    private static boolean stillShowing(TextView view, RichFlairSpan[] spans) {
        final CharSequence text = view.getText();

        if (!(text instanceof Spanned)) {
            return false;
        }

        final List<RichFlairSpan> current =
                Arrays.asList(((Spanned) text).getSpans(0, text.length(), RichFlairSpan.class));

        for (RichFlairSpan span : spans) {
            if (current.contains(span)) {
                return true;
            }
        }

        return false;
    }

    @Override
    public int getSize(
            Paint paint,
            CharSequence text,
            int start,
            int end,
            @Nullable Paint.FontMetricsInt fm) {
        final Typeface typeface = paint.getTypeface();
        final float textSize = paint.getTextSize();

        try {
            apply(paint);
            return Math.round(measure(paint));
        } finally {
            paint.setTypeface(typeface);
            paint.setTextSize(textSize);
        }
    }

    @Override
    public void draw(
            Canvas canvas,
            CharSequence text,
            int start,
            int end,
            float x,
            int top,
            int y,
            int bottom,
            Paint paint) {
        final Typeface typeface = paint.getTypeface();
        final float textSize = paint.getTextSize();
        final int color = paint.getColor();

        try {
            apply(paint);

            final int inset = half ? (bottom - top) / HALF_INSET_DIVISOR : 0;

            pill.set(x, top + inset, x + measure(paint), bottom - inset);
            paint.setColor(backgroundColor);
            canvas.drawRoundRect(pill, CORNER_RADIUS, CORNER_RADIUS, paint);
            paint.setColor(textColor);

            // The same baseline RoundedBackgroundSpan centres its text on.
            final float baseline =
                    pill.bottom - ((pill.bottom - pill.top) / 2) + (paint.descent() * 1.5f);

            drawContents(canvas, paint, x + padding(paint), baseline);
        } finally {
            paint.setTypeface(typeface);
            paint.setTextSize(textSize);
            paint.setColor(color);
        }
    }

    private void drawContents(Canvas canvas, Paint paint, float startX, float baseline) {
        float cursor = startX;

        for (Richtext segment : segments) {
            final Bitmap bitmap = bitmapFor(segment);

            if (bitmap != null) {
                final float height = paint.getTextSize();
                final float width = emojiWidth(bitmap, height);

                // Centre the emoji on the text's optical centre rather than its baseline, so a
                // square emoji and the text beside it look level.
                final float centre = baseline + ((paint.ascent() + paint.descent()) / 2f);

                source.set(0, 0, bitmap.getWidth(), bitmap.getHeight());
                emoji.set(cursor, centre - (height / 2f), cursor + width, centre + (height / 2f));
                canvas.drawBitmap(bitmap, source, emoji, paint);

                cursor += width;
            } else {
                final String value = RichFlairParser.segmentText(segment);

                if (!value.isEmpty()) {
                    canvas.drawText(value, cursor, baseline, paint);
                    cursor += paint.measureText(value);
                }
            }
        }
    }

    /** Total pill width: the padding either side plus every segment, measured as it will be drawn. */
    private float measure(Paint paint) {
        float width = padding(paint) * 2;

        for (Richtext segment : segments) {
            final Bitmap bitmap = bitmapFor(segment);

            if (bitmap != null) {
                width += emojiWidth(bitmap, paint.getTextSize());
            } else {
                width += paint.measureText(RichFlairParser.segmentText(segment));
            }
        }

        return width;
    }

    /** The bitmap to draw for this segment, or null to fall back to drawing its text. */
    private @Nullable Bitmap bitmapFor(Richtext segment) {
        final String url = segment.getU();

        if (!RichFlairParser.isEmoji(segment) || url == null) {
            return null;
        }

        final Bitmap bitmap = resolved.get(url);

        return (bitmap != null && !bitmap.isRecycled()) ? bitmap : null;
    }

    private static float emojiWidth(Bitmap bitmap, float height) {
        if (bitmap.getHeight() <= 0) {
            return height;
        }

        return height * ((float) bitmap.getWidth() / bitmap.getHeight());
    }

    private static float padding(Paint paint) {
        return paint.measureText(PADDING);
    }

    /**
     * The typeface and size the pill is measured and drawn at. Unlike RoundedBackgroundSpan, the
     * halving happens once, here, so the width reported by {@link #getSize} is the width
     * {@link #draw} actually paints.
     */
    private void apply(Paint paint) {
        paint.setTypeface(
                RobotoTypefaces.obtainTypeface(
                        context, RobotoTypefaces.TYPEFACE_ROBOTO_CONDENSED_BOLD));

        if (half) {
            paint.setTextSize(paint.getTextSize() / 2);
        }
    }
}
