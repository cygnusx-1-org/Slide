package me.edgan.redditslide.util;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

import android.app.Application;
import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import androidx.test.core.app.ApplicationProvider;
import me.edgan.redditslide.SettingValues;
import me.edgan.redditslide.Views.MaxHeightImageView;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/**
 * The two slots an inline image can get: a comment's, which is a constant on-screen area whatever
 * the picture's shape, and a post's own, which is the body's full width at the picture's own
 * aspect ratio.
 *
 * <p>Driven through {@code sizeSlot}/{@code reserveSlot} rather than {@code display}, which reaches
 * the image loader through {@code (Reddit) context.getApplicationContext()} — an application no
 * unit test has. All of the geometry lives in those two.
 *
 * <p>The comment sizing is pinned at the "small" setting with tall pictures on purpose: it caps a
 * slot at the screen width, and a wide picture at a larger setting would hit that cap and stop
 * being about the area rule under test.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33, application = Application.class, qualifiers = "w411dp-h891dp-xxhdpi")
public class CommentImageSlotTest {

    /** The width a body gives its blocks; any fixed number the two sizings can be compared at. */
    private static final int CONTAINER_WIDTH_PX = 1000;

    /** r/test t3_1wfupwu's picture, landscape. */
    private static final int WIDE_W = 2948;

    private static final int WIDE_H = 2020;

    /** r/test t3_1wfuohp's picture, portrait. */
    private static final int TALL_W = 1344;

    private static final int TALL_H = 2992;

    private int savedCommentImageSize;

    @Before
    public void pinTheCommentImageSize() {
        savedCommentImageSize = SettingValues.commentImageSize;
        SettingValues.commentImageSize = SettingValues.COMMENT_IMAGE_SIZE_SMALL;
    }

    @After
    public void restoreCommentImageSize() {
        SettingValues.commentImageSize = savedCommentImageSize;
    }

    // ---------------------------------------------------------------------
    // The post's own pictures: the body's width, the picture's shape
    // ---------------------------------------------------------------------

    @Test
    public void aLoadedImageTakesTheWholeWidthAtItsOwnRatio() {
        assertFullWidthSlot(WIDE_W, WIDE_H);
        assertFullWidthSlot(TALL_W, TALL_H);
    }

    @Test
    public void theSlotIsThatSizeBeforeTheImageArrives() {
        // The whole point of reserving it: the body does not resize when the bitmap lands.
        final MaxHeightImageView reserved = block();
        CommentImageUtil.reserveSlot(reserved, (double) WIDE_H / WIDE_W, true);
        measure(reserved);

        final MaxHeightImageView loaded = block();
        CommentImageUtil.sizeSlot(loaded, WIDE_W, WIDE_H, true);
        measure(loaded);

        assertThat(reserved.getMeasuredWidth(), is(loaded.getMeasuredWidth()));
        assertThat(reserved.getMeasuredHeight(), is(loaded.getMeasuredHeight()));
    }

    @Test
    public void anImageOfUnknownShapeReservesNoHeight() {
        // Today's behaviour, recorded rather than blessed: nothing warms a post's selftext images
        // before the comments screen binds (SubmissionComments warms its videos only) and
        // media_metadata's s.x/s.y never reach the ratio cache, so the block opens flat and grows
        // once, when the bitmap arrives.
        final MaxHeightImageView view = block();
        CommentImageUtil.reserveSlot(view, 0, true);
        measure(view);

        assertThat(view.getMeasuredHeight(), is(0));
    }

    // ---------------------------------------------------------------------
    // A comment's pictures: unchanged
    // ---------------------------------------------------------------------

    @Test
    public void aCommentImageKeepsItsConstantAreaWhateverItsShape() {
        final ViewGroup.LayoutParams twoToOne = commentSlot(400, 800);
        final ViewGroup.LayoutParams threeToOne = commentSlot(300, 900);

        final double area = (double) twoToOne.width * twoToOne.height;
        final double otherArea = (double) threeToOne.width * threeToOne.height;
        assertThat(Math.abs(area - otherArea) / area < 0.01, is(true));
        // The taller picture pays for its height in width, rather than growing the block.
        assertThat(threeToOne.width < twoToOne.width, is(true));
        assertRatio(twoToOne, 2d);
        assertRatio(threeToOne, 3d);
    }

    @Test
    public void aCommentImageIsNarrowerThanThePostsOwn() {
        final ViewGroup.LayoutParams comment = commentSlot(WIDE_W, WIDE_H);
        final MaxHeightImageView post = block();
        CommentImageUtil.sizeSlot(post, WIDE_W, WIDE_H, true);
        measure(post);

        assertThat(comment.width < post.getMeasuredWidth(), is(true));
    }

    @Test
    public void aCommentImageOfUnknownSizeFallsBackToTheReferenceBox() {
        assertThat(commentSlot(0, 0).width, is(500));
        assertThat(commentSlot(0, 0).height, is(300));

        SettingValues.commentImageSize = SettingValues.COMMENT_IMAGE_SIZE_MEDIUM;
        assertThat(commentSlot(0, 0).width, is(750));
        assertThat(commentSlot(0, 0).height, is(450));

        SettingValues.commentImageSize = SettingValues.COMMENT_IMAGE_SIZE_LARGE;
        assertThat(commentSlot(0, 0).width, is(1000));
        assertThat(commentSlot(0, 0).height, is(600));
    }

    // ---------------------------------------------------------------------

    private static void assertFullWidthSlot(int width, int height) {
        final MaxHeightImageView view = block();
        CommentImageUtil.sizeSlot(view, width, height, true);
        measure(view);

        assertThat(view.getMeasuredWidth(), is(CONTAINER_WIDTH_PX));
        assertThat(view.getMeasuredHeight(), is(expectedHeight(width, height)));
    }

    private static ViewGroup.LayoutParams commentSlot(int width, int height) {
        final MaxHeightImageView view = block();
        CommentImageUtil.sizeSlot(view, width, height, false);
        return view.getLayoutParams();
    }

    /** A block image view as CommentOverflow adds it: the container's width, height from content. */
    private static MaxHeightImageView block() {
        final Context context = ApplicationProvider.getApplicationContext();
        final MaxHeightImageView view = new MaxHeightImageView(context);
        view.setLayoutParams(
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT));
        return view;
    }

    private static void measure(View view) {
        view.measure(
                View.MeasureSpec.makeMeasureSpec(CONTAINER_WIDTH_PX, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
    }

    /** The height MaxHeightImageView derives from a ratio, including its tall-image cap. */
    private static int expectedHeight(int width, int height) {
        return (int)
                Math.min(
                        CONTAINER_WIDTH_PX * ((double) height / width),
                        MaxHeightImageView.maxHeight);
    }

    private static void assertRatio(ViewGroup.LayoutParams params, double expected) {
        final double actual = (double) params.height / params.width;
        assertThat(Math.abs(actual - expected) < 0.01, is(true));
    }
}
