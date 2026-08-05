package me.edgan.redditslide.util;

import android.app.Activity;
import android.content.Context;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Color;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.TextView;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.tabs.TabLayout;
import me.edgan.redditslide.R;
import me.edgan.redditslide.Reddit;
import me.edgan.redditslide.SettingValues;
import org.jspecify.annotations.NullMarked;

/** Created by TacoTheDank on 12/31/2020. */
@NullMarked
public class LayoutUtils {

    /** Fallback aspect ratio (height/width) for media whose intrinsic size is unknown. */
    private static final double FALLBACK_RATIO = 9d / 16d;

    private static final Handler MAIN_HANDLER = new Handler(Looper.getMainLooper());

    /**
     * Method to scroll the TabLayout to a specific index
     *
     * @param tabLayout the tab layout
     * @param tabPosition index to scroll to
     */
    public static void scrollToTabAfterLayout(final TabLayout tabLayout, final int tabPosition) {
        // from http://stackoverflow.com/a/34780589/3697225
        if (tabLayout != null) {
            final ViewTreeObserver observer = tabLayout.getViewTreeObserver();

            if (observer.isAlive()) {
                observer.dispatchOnGlobalLayout(); // In case a previous call is waiting when this
                // call is made
                observer.addOnGlobalLayoutListener(
                        new ViewTreeObserver.OnGlobalLayoutListener() {
                            @Override
                            public void onGlobalLayout() {
                                tabLayout.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                                final TabLayout.Tab tab = tabLayout.getTabAt(tabPosition);
                                if (tab != null) {
                                    tab.select();
                                }
                            }
                        });
            }
        }
    }

    public static void showSnackbar(final Snackbar s) {
        final View view = s.getView();
        // Theme the snackbar background to match the rest of the UI instead of the gray default.
        final TypedValue cardBg = new TypedValue();
        if (view.getContext().getTheme().resolveAttribute(R.attr.card_background, cardBg, true)) {
            view.setBackgroundColor(cardBg.data);
        }
        final TextView tv = view.findViewById(com.google.android.material.R.id.snackbar_text);
        tv.setTextColor(Color.WHITE);
        s.show();
    }

    /**
     * Height in pixels for a media row of the given intrinsic size laid out at {@code rowWidth}.
     *
     * @param mediaWidth intrinsic media width; 16:9 is assumed when this or the height is unknown
     * @param mediaHeight intrinsic media height
     * @param rowWidth width the row will be laid out at
     * @param maxHeight upper bound for the returned height, ignored when not positive
     */
    public static int getMediaRowHeight(
            final int mediaWidth, final int mediaHeight, final int rowWidth, final int maxHeight) {
        if (rowWidth <= 0) {
            return 0;
        }
        final double ratio =
                (mediaWidth > 0 && mediaHeight > 0)
                        ? (double) mediaHeight / (double) mediaWidth
                        : FALLBACK_RATIO;
        // Clamp before narrowing. A skewed ratio from a malformed entry (media_metadata copies s.x
        // and s.y in unchecked) overflows an int, and clamping afterwards would let a negative
        // through — where -1 and -2 are the MATCH_PARENT and WRAP_CONTENT sentinels, putting the
        // screen-tall row straight back.
        final long height = Math.round((double) rowWidth * ratio);
        final long bound = maxHeight > 0 ? maxHeight : Integer.MAX_VALUE;
        return (int) Math.max(0L, Math.min(height, bound));
    }

    /**
     * Width a vertical album/gallery row will be laid out at. The first bind can happen before the
     * list has been laid out, in which case the window width is the best available estimate. The
     * same estimate covers a content width of zero, so that padding wider than the list cannot
     * silently collapse rows to nothing.
     */
    public static int getAlbumRowWidth(
            final @Nullable RecyclerView recyclerView, final Context context) {
        if (recyclerView != null) {
            final int content =
                    recyclerView.getWidth()
                            - recyclerView.getPaddingLeft()
                            - recyclerView.getPaddingRight();
            if (content > 0) {
                return content;
            }
        }
        return context.getResources().getDisplayMetrics().widthPixels;
    }

    /**
     * Sizes a video/GIF row in the vertical album/gallery list.
     *
     * <p>submission_gifcard_album.xml is {@code match_parent} because it doubles as a full-screen
     * pager page. As a RecyclerView row that MATCH_PARENT becomes EXACTLY(list height), which made
     * every video row exactly one screenful tall with a black void under the video. Give the row an
     * explicit height from the media's aspect ratio instead, capped so a row can never exceed one
     * screenful, plus the same outer gap the image rows in album_image.xml already have.
     *
     * @param rowWidth width from {@link #getAlbumRowWidth}. Passed in rather than resolved here so
     *     that a caller measuring other parts of the row sizes them against the same width.
     * @param extraHeight height of any content stacked above the video (a visible caption). It is
     *     included in the one-screenful cap, so a long caption shortens the video rather than
     *     growing the row past a screen; callers should bound the caption itself.
     */
    public static void applyAlbumRowSize(
            final View itemView,
            final @Nullable RecyclerView recyclerView,
            final int rowWidth,
            final int mediaWidth,
            final int mediaHeight,
            final int extraHeight) {
        // Nothing to size a view by if it has none. The row holders always do, since they inflate
        // against the RecyclerView; anything else has no parent yet, and inventing LayoutParams here
        // would have to pick a concrete subclass, which would then ClassCastException in whatever
        // parent the caller really adds the view to.
        final ViewGroup.LayoutParams lp = itemView.getLayoutParams();
        if (lp == null) {
            return;
        }

        final Resources res = itemView.getResources();
        final int maxHeight =
                (recyclerView != null && recyclerView.getHeight() > 0)
                        ? recyclerView.getHeight()
                        : res.getDisplayMetrics().heightPixels;

        final int height =
                getMediaRowHeight(mediaWidth, mediaHeight, rowWidth, maxHeight) + extraHeight;
        final int rowHeight = maxHeight > 0 ? Math.min(height, maxHeight) : height;

        lp.height = rowHeight;
        if (lp instanceof ViewGroup.MarginLayoutParams) {
            final ViewGroup.MarginLayoutParams mlp = (ViewGroup.MarginLayoutParams) lp;
            mlp.topMargin = res.getDimensionPixelSize(R.dimen.album_row_margin_top);
            mlp.bottomMargin = res.getDimensionPixelSize(R.dimen.album_row_margin_bottom);
        }
        itemView.setLayoutParams(lp);
    }

    /**
     * Pads the bottom of a list by an overlay that covers it, so its last row can scroll clear.
     *
     * <p>{@code clipToPadding} is off so rows scroll through the padded strip rather than stopping
     * short of it — the overlay is translucent and the content should pass under it.
     *
     * <p>Belongs to whoever owns the overlay, not to the adapter: the album card sets its panel's
     * height at runtime from a measured view, so no dimension an adapter could read is correct. The
     * adapters used to emit an end-of-list spacer row for this and had to guess the height, which is
     * how it ended up being 0 for years and then the wrong 96dp.
     */
    public static void insetForOverlay(final RecyclerView list, final int overlayHeight) {
        list.setPadding(
                list.getPaddingLeft(), list.getPaddingTop(), list.getPaddingRight(), overlayHeight);
        list.setClipToPadding(false);
    }

    /**
     * Height to leave clear when scrolling a vertical album/gallery to a row: that of the toolbar
     * overlaying the top of the list, or 0 for a host with no toolbar. Resolved through here rather
     * than by dereferencing {@code findViewById(R.id.toolbar)} at the call site, which is where the
     * grid dialogs used to assume a toolbar exists.
     */
    public static int getToolbarOffset(final Activity activity) {
        final View toolbar = activity.findViewById(R.id.toolbar);
        return toolbar == null ? 0 : toolbar.getHeight();
    }

    /**
     * Listener that runs {@code onWidthChanged} when a list's width changes, for adapters whose
     * rows carry an explicit pixel height. The album activities declare configChanges for
     * orientation, so they are not recreated on rotation and those heights would stay stale.
     *
     * <p>This fires on the list's first layout too (0 to its real width), so {@code onWidthChanged}
     * is expected to compare against the width its rows were actually sized for rather than
     * rebinding unconditionally.
     *
     * <p>The work is posted rather than run inline: notifying an adapter from inside a layout pass
     * throws.
     */
    public static View.OnLayoutChangeListener rebindOnWidthChange(final Runnable onWidthChanged) {
        return (v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> {
            if (right - left != oldRight - oldLeft) {
                MAIN_HANDLER.post(onWidthChanged);
            }
        };
    }

    // Should this go here in this class??? I don't think it should but idk where else to put it
    public static int getNumColumns(final int orientation, final Activity activity) {
        final int numColumns;
        boolean singleColumnMultiWindow = false;
        singleColumnMultiWindow =
                activity.isInMultiWindowMode() && SettingValues.singleColumnMultiWindow;
        if (orientation == Configuration.ORIENTATION_LANDSCAPE && !singleColumnMultiWindow) {
            numColumns = Reddit.dpWidth;
        } else if (orientation == Configuration.ORIENTATION_PORTRAIT) {
            numColumns = SettingValues.portraitColumns;
        } else {
            numColumns = 1;
        }
        return numColumns;
    }
}
