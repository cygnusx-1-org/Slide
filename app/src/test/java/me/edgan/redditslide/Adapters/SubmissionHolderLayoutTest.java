package me.edgan.redditslide.Adapters;

import android.app.Application;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.FrameLayout;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import me.edgan.redditslide.R;
import me.edgan.redditslide.SettingValues;
import me.edgan.redditslide.Views.CreateCardView;
import me.edgan.redditslide.Views.CreateCardView.CardEnum;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;

/**
 * The contract between the submission holders and the layouts they are handed.
 *
 * <p>{@link SubmissionViewHolder} resolves eleven ids with {@code requireViewById}, which throws
 * rather than returning null, and {@link CardSubmissionViewHolder} adds two more. Which layout the
 * holder gets is decided at runtime by the user's card style, so an id dropped or renamed in one
 * of the four feed layouts crashes the feed in that card style alone and leaves the other three
 * working -- the shape of bug that reaches a release because whoever changed the layout was
 * looking at a different card style.
 *
 * <p>The split between the base holder and its two subclasses is documented in a comment on
 * {@link SubmissionViewHolder} and enforced by nothing: renaming {@code @+id/hide} in
 * {@code submission_list_desktop.xml} left the entire suite green.
 *
 * <p>These build a holder over every layout the app can actually reach, through
 * {@link CreateCardView#CreateView} rather than a hand-written list of layout ids, so a new card
 * style is covered the day its case is added to that switch.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33, application = Application.class)
public class SubmissionHolderLayoutTest {

    public static class TestActivity extends AppCompatActivity {}

    @Nullable private ActivityController<TestActivity> controller;

    @SuppressWarnings("NullAway.Init")
    private TestActivity activity;

    private CardEnum cardViewWas;
    private boolean middleImageWas;
    private boolean bigThumbnailsWas;
    private boolean noThumbnailsWas;

    @Before
    public void setUp() {
        cardViewWas = SettingValues.defaultCardView;
        middleImageWas = SettingValues.middleImage;
        bigThumbnailsWas = SettingValues.bigThumbnails;
        noThumbnailsWas = SettingValues.noThumbnails;

        controller = Robolectric.buildActivity(TestActivity.class);
        activity = controller.get();
        activity.setTheme(R.style.Theme_DARK);
        // BaseActivity layers the font-size overlays on at runtime and the submission layouts
        // reference their attrs, so apply the defaults before inflating.
        activity.getTheme().applyStyle(R.style.FontStyle_MediumPost, true);
        activity.getTheme().applyStyle(R.style.FontStyle_MediumComment, true);
        controller.create();
    }

    @After
    public void tearDown() {
        // SettingValues is app-wide static state shared with every later test in this JVM.
        SettingValues.defaultCardView = cardViewWas;
        SettingValues.middleImage = middleImageWas;
        SettingValues.bigThumbnails = bigThumbnailsWas;
        SettingValues.noThumbnails = noThumbnailsWas;
        // Null when setUp threw before the controller was built; closing it then would replace
        // the real failure with an NPE from @After.
        if (controller != null) {
            controller.close();
        }
    }

    /** Builds the card the given settings produce, and the holder that will be bound to it. */
    private CardSubmissionViewHolder holderFor(CardEnum style, boolean middleImage, boolean bigThumbnails) {
        SettingValues.defaultCardView = style;
        SettingValues.middleImage = middleImage;
        SettingValues.bigThumbnails = bigThumbnails;
        SettingValues.noThumbnails = false;

        View card = CreateCardView.CreateView(new FrameLayout(activity));
        return new CardSubmissionViewHolder(card);
    }

    @Test
    public void theBigCardStyleSuppliesEveryIdItsHolderRequires() {
        holderFor(CardEnum.LARGE, false, true);
    }

    @Test
    public void theBigCardWithAMiddleImageSuppliesEveryIdItsHolderRequires() {
        holderFor(CardEnum.LARGE, true, true);
    }

    @Test
    public void theListStyleSuppliesEveryIdItsHolderRequires() {
        holderFor(CardEnum.LIST, false, true);
    }

    @Test
    public void theDesktopStyleSuppliesEveryIdItsHolderRequires() {
        holderFor(CardEnum.DESKTOP, false, true);
    }

    /**
     * Small thumbnails take a second path through {@code CreateView} that resolves
     * {@code innerrelative} on everything but the desktop style, so it is its own contract.
     */
    @Test
    public void everyCardStyleSuppliesTheSmallThumbnailIdsToo() {
        holderFor(CardEnum.LARGE, false, false);
        holderFor(CardEnum.LARGE, true, false);
        holderFor(CardEnum.LIST, false, false);
        holderFor(CardEnum.DESKTOP, false, false);
    }

    /**
     * The comments-screen header is the one layout with the whole selftext and no hide button, so
     * it is bound to the other subclass and has to satisfy that one instead.
     */
    @Test
    public void theCommentsHeaderSuppliesEveryIdItsHolderRequires() {
        View header =
                LayoutInflater.from(activity)
                        .inflate(R.layout.submission_fullscreen, new FrameLayout(activity), false);

        new FullSubmissionViewHolder(header);
    }
}
