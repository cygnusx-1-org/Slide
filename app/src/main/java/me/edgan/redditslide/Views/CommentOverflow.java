package me.edgan.redditslide.Views;

import android.content.Context;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.Color;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;
import androidx.annotation.Nullable;
import com.devspark.robototextview.RobotoTypefaces;
import java.util.List;
import me.edgan.redditslide.R;
import me.edgan.redditslide.SpoilerRobotoTextView;
import me.edgan.redditslide.Visuals.ColorPreferences;
import me.edgan.redditslide.Visuals.FontPreferences;
import me.edgan.redditslide.util.CommentImageUtil;
import me.edgan.redditslide.util.CommentVideoPreview;
import me.edgan.redditslide.util.CommentVideoUtil;
import me.edgan.redditslide.util.DisplayUtil;
import me.edgan.redditslide.util.SubmissionParser;
import org.jspecify.annotations.NullMarked;

/** Class that provides methods to help bind submissions with multiple blocks of text. */
@NullMarked
public class CommentOverflow extends LinearLayout {
    // Assigned by init(context), which every constructor calls.
    @SuppressWarnings("NullAway.Init")
    private ColorPreferences colorPreferences;

    @Nullable private Typeface typeface = null;
    private int textColor;
    private int fontSize;
    private static final MarginLayoutParams COLUMN_PARAMS;
    private static final MarginLayoutParams MARGIN_PARAMS;
    private static final MarginLayoutParams HR_PARAMS;

    /** Metrics of the inline comment-video card. Fixed, so a card is the same size in every state. */
    private static final int VIDEO_CARD_HEIGHT_DP = 200;

    private static final int VIDEO_CARD_MARGIN_DP = 8;
    private static final int VIDEO_CARD_PLAY_DP = 48;

    /**
     * Padding of the arrow's circular scrim. The arrow itself stays {@link #VIDEO_CARD_PLAY_DP};
     * the scrim is what makes a white glyph readable over a bright preview frame.
     */
    private static final int VIDEO_CARD_PLAY_PADDING_DP = 12;

    static {
        COLUMN_PARAMS =
                new TableRow.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        COLUMN_PARAMS.setMargins(0, 0, 32, 0);

        MARGIN_PARAMS =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        MARGIN_PARAMS.setMargins(0, 16, 0, 16);

        HR_PARAMS =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, DisplayUtil.dpToPxVertical(2));
        HR_PARAMS.setMargins(0, 16, 0, 16);
    }

    public CommentOverflow(Context context) {
        super(context);
        init(context);
    }

    public CommentOverflow(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public CommentOverflow(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    private void init(Context context) {
        colorPreferences = new ColorPreferences(context);
    }

    /**
     * Set the text for the corresponding views.
     *
     * @param blocks list of all blocks to be set
     * @param subreddit
     */
    public void setViews(List<String> blocks, String subreddit) {
        setViews(blocks, subreddit, null, null);
    }

    /**
     * Propagates the download base name (title_postId_commentId) to every text block so media
     * links inside comment overflow blocks save with the same name as the rest of the comment.
     * Call after {@link #setViews} has populated the children.
     */
    public void setDownloadName(String downloadName) {
        for (int i = 0; i < getChildCount(); i++) {
            View child = getChildAt(i);
            if (child instanceof SpoilerRobotoTextView) {
                ((SpoilerRobotoTextView) child).setDownloadName(downloadName);
            } else if (child instanceof HorizontalScrollView
                    && ((HorizontalScrollView) child).getChildCount() > 0) {
                View inner = ((HorizontalScrollView) child).getChildAt(0);
                if (inner instanceof SpoilerRobotoTextView) {
                    ((SpoilerRobotoTextView) inner).setDownloadName(downloadName);
                }
            }
        }
    }

    /**
     * Set the text for the corresponding views.
     *
     * @param blocks list of all blocks to be set
     * @param subreddit
     */
    public void setViews(
            List<String> blocks,
            String subreddit,
            @Nullable OnClickListener click,
            @Nullable OnLongClickListener longClick) {
        beginBlocks();

        if (!blocks.isEmpty()) {
            setVisibility(View.VISIBLE);
        }

        for (String block : blocks) {
            addBlock(block, subreddit, click, longClick);
        }
    }

    /**
     * Resolve the comment font settings and drop the previous bind's views, so the {@code add*Block}
     * methods below can be called one at a time. {@link #setViews} does this for a whole block list;
     * a caller that interleaves parsed text with blocks (the new-Reddit renderer) drives it itself.
     */
    public void beginBlocks() {
        Context context = getContext();
        int type = new FontPreferences(context).getFontTypeComment().getTypeface();
        if (type >= 0) {
            typeface = RobotoTypefaces.obtainTypeface(context, type);
        } else {
            typeface = Typeface.DEFAULT;
        }
        TypedValue typedValue = new TypedValue();
        Resources.Theme theme = context.getTheme();
        theme.resolveAttribute(R.attr.fontColor, typedValue, true);
        textColor = typedValue.data;
        TypedValue fontSizeTypedValue = new TypedValue();
        theme.resolveAttribute(R.attr.font_commentbody, fontSizeTypedValue, true);
        TypedArray a =
                context.obtainStyledAttributes(
                        null,
                        new int[] {R.attr.font_commentbody},
                        R.attr.font_commentbody,
                        new FontPreferences(context).getCommentFontStyle().getResId());
        fontSize = a.getDimensionPixelSize(0, -1);
        a.recycle();
        removeAllViews();
    }

    /**
     * Adds an empty comment-styled text view as the next block and returns it, for a caller that
     * supplies an already-parsed {@link android.text.Spanned} instead of html. {@link #beginBlocks}
     * must have run first — that is what resolves the font, size and colour applied here.
     */
    public SpoilerRobotoTextView addTextBlock(
            String subreddit,
            @Nullable OnClickListener click,
            @Nullable OnLongClickListener longClick) {
        SpoilerRobotoTextView textView = new SpoilerRobotoTextView(getContext());
        setStyle(textView, subreddit);
        textView.setLayoutParams(MARGIN_PARAMS);
        if (click != null) textView.setOnClickListener(click);
        if (longClick != null) textView.setOnLongClickListener(longClick);
        addView(textView);
        return textView;
    }

    /** Adds one parsed block — table, image, video, rule, code or text. See {@link #beginBlocks}. */
    public void addBlock(
            String block,
            String subreddit,
            @Nullable OnClickListener click,
            @Nullable OnLongClickListener longClick) {
        Context context = getContext();
        if (block.startsWith("<table>")) {
            HorizontalScrollView scrollView = new HorizontalScrollView(context);
            scrollView.setScrollbarFadingEnabled(false);
            TableLayout table = formatTable(block, subreddit, click, longClick);
            scrollView.setLayoutParams(MARGIN_PARAMS);
            table.setPaddingRelative(0, 0, 0, DisplayUtil.dpToPxVertical(10));
            scrollView.addView(table);
            addView(scrollView);
        } else if (block.startsWith(SubmissionParser.IMAGE_BLOCK_PREFIX)) {
            // A standalone inline comment image: render a real, pre-sized ImageView (loaded
            // from the shared cache) so the image is in place with no placeholder and no reflow.
            String url = block.substring(SubmissionParser.IMAGE_BLOCK_PREFIX.length());
            MaxHeightImageView imageView = new MaxHeightImageView(context);
            LinearLayout.LayoutParams imageParams =
                    new LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT);
            imageParams.setMargins(0, 16, 0, 16);
            imageView.setLayoutParams(imageParams);
            CommentImageUtil.display(imageView, url, subreddit);
            if (longClick != null) imageView.setOnLongClickListener(longClick);
            addView(imageView);
        } else if (block.startsWith(SubmissionParser.VIDEO_BLOCK_PREFIX)) {
            addVideoCard(context, block, subreddit, longClick);
        } else if (block.equals("<hr/>")) {
            View line = new View(context);
            line.setLayoutParams(HR_PARAMS);
            line.setBackgroundColor(textColor);
            line.setAlpha(0.6f);
            addView(line);
        } else if (block.startsWith("<pre>")) {
            HorizontalScrollView scrollView = new HorizontalScrollView(context);
            scrollView.setScrollbarFadingEnabled(false);
            SpoilerRobotoTextView newTextView = new SpoilerRobotoTextView(context);
            newTextView.setTextHtml(block, subreddit);
            setStyle(newTextView, subreddit);
            scrollView.setLayoutParams(MARGIN_PARAMS);
            newTextView.setPaddingRelative(0, 0, 0, DisplayUtil.dpToPxVertical(10));
            scrollView.addView(newTextView);
            if (click != null) newTextView.setOnClickListener(click);
            if (longClick != null) newTextView.setOnLongClickListener(longClick);
            addView(scrollView);

        } else {
            SpoilerRobotoTextView newTextView = new SpoilerRobotoTextView(context);
            newTextView.setTextHtml(block, subreddit);
            setStyle(newTextView, subreddit);
            newTextView.setLayoutParams(MARGIN_PARAMS);
            if (click != null) newTextView.setOnClickListener(click);
            if (longClick != null) newTextView.setOnLongClickListener(longClick);
            addView(newTextView);
        }
    }

    /**
     * dp to px against the display's density. {@link DisplayUtil} converts against the physical
     * xdpi/ydpi instead, which are not the same number on every device — the card would be inset
     * further on one axis than the other.
     */
    private static int dp(Context context, int dp) {
        return Math.round(
                TypedValue.applyDimension(
                        TypedValue.COMPLEX_UNIT_DIP,
                        dp,
                        context.getResources().getDisplayMetrics()));
    }

    /**
     * Draws a video uploaded through Reddit's comment composer as a static card: a full-width black
     * rounded rectangle with a centered play arrow, and — only when Reddit gave a caption that is
     * something other than the link itself — that caption on its own line underneath. Tapping the
     * card opens the same full-screen player a tap on the link would.
     *
     * <p>The card's size and margins never depend on the caption, so it lands on identical pixels
     * whether or not one is shown, and whether it is a comment's first block or a later one.
     */
    private void addVideoCard(
            Context context,
            String block,
            String subreddit,
            @Nullable OnLongClickListener longClick) {
        final String url = SubmissionParser.videoUrlOf(block);
        final String caption = SubmissionParser.videoCaptionOf(block);

        LinearLayout container = new LinearLayout(context);
        container.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams containerParams =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        containerParams.setMargins(0, 16, 0, 16);
        container.setLayoutParams(containerParams);

        FrameLayout card = new FrameLayout(context);
        LinearLayout.LayoutParams cardParams =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, dp(context, VIDEO_CARD_HEIGHT_DP));
        int margin = dp(context, VIDEO_CARD_MARGIN_DP);
        cardParams.setMargins(margin, margin, margin, margin);
        card.setLayoutParams(cardParams);
        card.setBackgroundResource(R.drawable.comment_video_card);
        // The rounded background supplies the outline, so this clips the preview frame to the
        // card's corners instead of letting it square them off.
        card.setClipToOutline(true);

        // Added before the arrow so it draws underneath. It stays invisible until a frame is read;
        // a video that yields none just leaves the card its flat fill.
        ImageView preview = new ImageView(context);
        preview.setLayoutParams(
                new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        preview.setScaleType(ImageView.ScaleType.CENTER_CROP);
        card.addView(preview);
        CommentVideoPreview.load(preview, url);

        ImageView play = new ImageView(context);
        int playPadding = dp(context, VIDEO_CARD_PLAY_PADDING_DP);
        int playSize = dp(context, VIDEO_CARD_PLAY_DP) + playPadding * 2;
        play.setLayoutParams(new FrameLayout.LayoutParams(playSize, playSize, Gravity.CENTER));
        play.setPadding(playPadding, playPadding, playPadding, playPadding);
        play.setBackgroundResource(R.drawable.comment_video_play_scrim);
        play.setImageResource(R.drawable.ic_play_arrow);
        play.setColorFilter(Color.WHITE);
        play.setContentDescription(context.getString(R.string.comment_video_play));
        card.addView(play);

        card.setOnClickListener(v -> CommentVideoUtil.open(context, url, subreddit));
        if (longClick != null) card.setOnLongClickListener(longClick);
        container.addView(card);

        if (!caption.isEmpty()) {
            TextView captionView = new TextView(context);
            captionView.setLayoutParams(
                    new LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT));
            captionView.setText(caption);
            captionView.setTextColor(textColor);
            captionView.setTextSize(TypedValue.COMPLEX_UNIT_PX, fontSize);
            if (typeface != null) captionView.setTypeface(typeface);
            container.addView(captionView);
        }

        addView(container);
    }

    /*todo: possibly fix tapping issues, better method required (this disables scrolling the HorizontalScrollView)
    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        super.dispatchTouchEvent(event);
        return false;
    }*/
    private TableLayout formatTable(String text, String subreddit) {
        return formatTable(text, subreddit, null, null);
    }

    private TableLayout formatTable(
            String text,
            String subreddit,
            @Nullable OnClickListener click,
            @Nullable OnLongClickListener longClick) {
        TableRow.LayoutParams rowParams =
                new TableRow.LayoutParams(
                        TableRow.LayoutParams.WRAP_CONTENT, TableRow.LayoutParams.WRAP_CONTENT);

        Context context = getContext();
        TableLayout table = new TableLayout(context);
        TableLayout.LayoutParams params =
                new TableLayout.LayoutParams(
                        TableLayout.LayoutParams.WRAP_CONTENT,
                        TableLayout.LayoutParams.WRAP_CONTENT);
        table.setLayoutParams(params);

        final String tableStart = "<table>";
        final String tableEnd = "</table>";
        final String tableHeadStart = "<thead>";
        final String tableHeadEnd = "</thead>";
        final String tableRowStart = "<tr>";
        final String tableRowEnd = "</tr>";
        final String tableColumnStart = "<td>";
        final String tableColumnEnd = "</td>";
        final String tableColumnStartLeft = "<td align=\"left\">";
        final String tableColumnStartRight = "<td align=\"right\">";
        final String tableColumnStartCenter = "<td align=\"center\">";
        final String tableHeaderStart = "<th>";
        final String tableHeaderStartLeft = "<th align=\"left\">";
        final String tableHeaderStartRight = "<th align=\"right\">";
        final String tableHeaderStartCenter = "<th align=\"center\">";
        final String tableHeaderEnd = "</th>";

        int i = 0;
        int columnStart = 0;
        int columnEnd;
        int gravity = Gravity.START;
        boolean columnStarted = false;

        TableRow row = null;

        while (i < text.length()) {
            if (text.charAt(i) != '<') { // quick check otherwise it falls through to else
                i += 1;
            } else if (text.subSequence(i, i + tableStart.length()).toString().equals(tableStart)) {
                i += tableStart.length();
            } else if (text.subSequence(i, i + tableHeadStart.length())
                    .toString()
                    .equals(tableHeadStart)) {
                i += tableHeadStart.length();
            } else if (text.subSequence(i, i + tableRowStart.length())
                    .toString()
                    .equals(tableRowStart)) {
                row = new TableRow(context);
                row.setLayoutParams(rowParams);
                i += tableRowStart.length();
            } else if (text.subSequence(i, i + tableRowEnd.length())
                    .toString()
                    .equals(tableRowEnd)) {
                table.addView(row);
                i += tableRowEnd.length();
            } else if (text.subSequence(i, i + tableEnd.length()).toString().equals(tableEnd)) {
                i += tableEnd.length();
            } else if (text.subSequence(i, i + tableHeadEnd.length())
                    .toString()
                    .equals(tableHeadEnd)) {
                i += tableHeadEnd.length();
            } else if (!columnStarted
                    && i + tableColumnStart.length() < text.length()
                    && (text.subSequence(i, i + tableColumnStart.length())
                                    .toString()
                                    .equals(tableColumnStart)
                            || text.subSequence(i, i + tableHeaderStart.length())
                                    .toString()
                                    .equals(tableHeaderStart))) {
                columnStarted = true;
                gravity = Gravity.START;
                i += tableColumnStart.length();
                columnStart = i;
            } else if (!columnStarted
                    && i + tableColumnStartRight.length() < text.length()
                    && (text.subSequence(i, i + tableColumnStartRight.length())
                                    .toString()
                                    .equals(tableColumnStartRight)
                            || text.subSequence(i, i + tableHeaderStartRight.length())
                                    .toString()
                                    .equals(tableHeaderStartRight))) {
                columnStarted = true;
                gravity = Gravity.END;
                i += tableColumnStartRight.length();
                columnStart = i;
            } else if (!columnStarted
                    && i + tableColumnStartCenter.length() < text.length()
                    && (text.subSequence(i, i + tableColumnStartCenter.length())
                                    .toString()
                                    .equals(tableColumnStartCenter)
                            || text.subSequence(i, i + tableHeaderStartCenter.length())
                                    .toString()
                                    .equals(tableHeaderStartCenter))) {
                columnStarted = true;
                gravity = Gravity.CENTER;
                i += tableColumnStartCenter.length();
                columnStart = i;
            } else if (!columnStarted
                    && i + tableColumnStartLeft.length() < text.length()
                    && (text.subSequence(i, i + tableColumnStartLeft.length())
                                    .toString()
                                    .equals(tableColumnStartLeft)
                            || text.subSequence(i, i + tableHeaderStartLeft.length())
                                    .toString()
                                    .equals(tableHeaderStartLeft))) {
                columnStarted = true;
                gravity = Gravity.START;
                i += tableColumnStartLeft.length();
                columnStart = i;
            } else if (text.substring(i).startsWith("<td")) {
                // case for <td colspan="2"  align="left">
                // See last table in
                // https://www.reddit.com/r/GlobalOffensive/comments/51s3r8/virtuspro_vs_vgcyberzen_sl_ileague_s2_finals/
                columnStarted = true;
                i += text.substring(i).indexOf(">") + 1;
                columnStart = i;
            } else if (text.subSequence(i, i + tableColumnEnd.length())
                            .toString()
                            .equals(tableColumnEnd)
                    || text.subSequence(i, i + tableHeaderEnd.length())
                            .toString()
                            .equals(tableHeaderEnd)) {
                columnEnd = i;

                SpoilerRobotoTextView textView = new SpoilerRobotoTextView(context);
                textView.setTextHtml(text.subSequence(columnStart, columnEnd), subreddit);
                setStyle(textView, subreddit);
                textView.setLayoutParams(COLUMN_PARAMS);
                textView.setGravity(gravity);
                if (click != null) textView.setOnClickListener(click);
                if (longClick != null) textView.setOnLongClickListener(longClick);
                if (text.subSequence(i, i + tableHeaderEnd.length())
                        .toString()
                        .equals(tableHeaderEnd)) {
                    textView.setTypeface(null, Typeface.BOLD);
                }
                if (row != null) {
                    row.addView(textView);
                }

                columnStart = 0;
                columnStarted = false;
                i += tableColumnEnd.length();
            } else {
                i += 1;
            }
        }

        return table;
    }

    private void setStyle(SpoilerRobotoTextView textView, String subreddit) {
        textView.setTextColor(textColor);
        textView.setTextSize(TypedValue.COMPLEX_UNIT_PX, fontSize);
        if (typeface != null) textView.setTypeface(typeface);
        textView.setLinkTextColor(colorPreferences.getColor(subreddit));
    }
}
