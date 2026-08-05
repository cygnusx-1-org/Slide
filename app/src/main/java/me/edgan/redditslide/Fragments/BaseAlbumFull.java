package me.edgan.redditslide.Fragments;

import android.animation.ValueAnimator;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.sothree.slidinguppanel.SlidingUpPanelLayout;
import me.edgan.redditslide.Activities.CommentsScreen;
import me.edgan.redditslide.Activities.Shadowbox;
import me.edgan.redditslide.R;
import me.edgan.redditslide.util.LayoutUtils;
import net.dean.jraw.models.Submission;

/**
 * Shared scaffolding for the shadowbox album fragments (imgur/reddit albums and tumblr posts, for
 * both submissions and comment links): inflates the album card, binds the actionbar, wires the
 * vertical RecyclerView with the scroll-driven actionbar fade and the sliding comments panel, then
 * hands the album url to the subclass's loader.
 *
 * <p>A load that fails here leaves the card blank, and none of the subclasses overrides its loader's
 * {@code onError}. That is deliberate: the standalone Album, RedditGallery and Tumblr activities own
 * their whole screen and can reasonably replace it with the link in a web view, but each of these
 * fragments is one page of a Shadowbox pager and may not even be the page in front of the user, so
 * starting an activity from it would move them off the page they are on.
 */
public abstract class BaseAlbumFull extends Fragment {

    protected View list;
    boolean hidden;
    View rootView;

    /** Binds the shadowbox actionbar for this fragment's content type. */
    protected abstract void bindActionbar();

    /** The album/gallery url to load. */
    protected abstract @Nullable String getAlbumUrl();

    /** Opens the comments for the displayed content (tap while the panel is expanded). */
    protected abstract void openComments();

    /**
     * Hands the media to the list: the loading subclasses start a fetch and set their adapter from
     * its callback, while one whose media is already in hand sets the adapter here and now.
     *
     * @param url from {@link #getAlbumUrl()}, and unused by a subclass with nothing to fetch
     */
    protected abstract void loadAlbum(String url);

    /**
     * Whether there is anything for {@link #loadAlbum} to show. Defaults to having a url to fetch,
     * which is what the loading subclasses need; one whose media is already in the submission should
     * say so directly rather than let a url it never reads decide whether its images appear.
     */
    protected boolean hasAlbumToShow() {
        return getAlbumUrl() != null; // null when the hosting activity is finishing
    }

    @Override
    public View onCreateView(
            LayoutInflater inflater,
            @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        rootView = inflater.inflate(R.layout.submission_albumcard, container, false);
        bindActionbar();

        list = rootView.findViewById(R.id.images);

        // The pager destroys the view of a page it has scrolled away from and rebuilds it on return,
        // keeping the fragment. The new @id/base starts at full alpha, so a stale flag here would
        // leave the fade stuck. (The panel height it remembers is per-view; see below.)
        hidden = false;

        list.setVisibility(View.VISIBLE);
        final LinearLayoutManager layoutManager = new LinearLayoutManager(getActivity());
        layoutManager.setOrientation(LinearLayoutManager.VERTICAL);
        ((RecyclerView) list).setLayoutManager(layoutManager);

        ((RecyclerView) list)
                .addOnScrollListener(
                        new RecyclerView.OnScrollListener() {

                            /**
                             * The fade currently running, so a scroll that reverses direction can
                             * cancel it. This was a local re-initialised to null on every callback,
                             * which made both cancels below dead code and let a flick leave two
                             * animators driving the panel's alpha at once.
                             */
                            @Nullable private ValueAnimator va;

                            @Override
                            public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
                                super.onScrolled(recyclerView, dx, dy);

                                if (dy > 0 && !hidden) {
                                    hidden = true;

                                    if (va != null && va.isRunning()) va.cancel();

                                    final View base = rootView.findViewById(R.id.base);
                                    va = ValueAnimator.ofFloat(1.0f, 0.2f);
                                    int mDuration = 250; // in millis
                                    va.setDuration(mDuration);
                                    va.addUpdateListener(
                                            new ValueAnimator.AnimatorUpdateListener() {
                                                @Override public void onAnimationUpdate(
                                                        ValueAnimator animation) {
                                                    Float value =
                                                            (Float) animation.getAnimatedValue();
                                                    base.setAlpha(value);
                                                }
                                            });

                                    va.start();

                                } else if (hidden && dy <= 0) {
                                    final View base = rootView.findViewById(R.id.base);

                                    if (va != null && va.isRunning()) va.cancel();

                                    hidden = false;
                                    va = ValueAnimator.ofFloat(0.2f, 1.0f);
                                    int mDuration = 250; // in millis
                                    va.setDuration(mDuration);
                                    va.addUpdateListener(
                                            new ValueAnimator.AnimatorUpdateListener() {
                                                @Override public void onAnimationUpdate(
                                                        ValueAnimator animation) {
                                                    Float value =
                                                            (Float) animation.getAnimatedValue();
                                                    base.setAlpha(value);
                                                }
                                            });

                                    va.start();
                                }
                            }
                        });

        final View.OnClickListener openClick =
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        ((SlidingUpPanelLayout) rootView.findViewById(R.id.sliding_layout))
                                .setPanelState(SlidingUpPanelLayout.PanelState.EXPANDED);
                    }
                };
        rootView.findViewById(R.id.base).setOnClickListener(openClick);
        // On every layout of the title, not once: these activities declare configChanges for
        // orientation, so rotating does not recreate them, and the title reflows to a different
        // height at the new width. A one-shot measurement left both the collapsed panel and the
        // list's inset holding the height from the orientation the page was opened in.
        //
        // All three captured, none read from a field: nothing removes this listener, so it outlives a
        // view the pager rebuilds. Its targets have to be its own view's, and so does the height it
        // remembers — a shared one would let a stale listener record its write against the new view
        // and make that view skip its own, which is the very thing this is guarding against.
        final SlidingUpPanelLayout panelLayout = rootView.findViewById(R.id.sliding_layout);
        final RecyclerView panelList = (RecyclerView) list;
        final int[] applied = {0};
        rootView.findViewById(R.id.title)
                .addOnLayoutChangeListener(
                        (v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) ->
                                applyPanelHeight(panelLayout, panelList, applied, bottom - top));
        ((SlidingUpPanelLayout) rootView.findViewById(R.id.sliding_layout))
                .addPanelSlideListener(
                        new SlidingUpPanelLayout.SimplePanelSlideListener() {
                            @Override
                            public void onPanelStateChanged(
                                    View panel,
                                    SlidingUpPanelLayout.PanelState previousState,
                                    SlidingUpPanelLayout.PanelState newState) {
                                if (newState == SlidingUpPanelLayout.PanelState.EXPANDED) {
                                    rootView.findViewById(R.id.base)
                                            .setOnClickListener(
                                                    new View.OnClickListener() {
                                                        @Override
                                                        public void onClick(View v) {
                                                            openComments();
                                                        }
                                                    });
                                } else {
                                    rootView.findViewById(R.id.base).setOnClickListener(openClick);
                                }
                            }
                        });

        if (hasAlbumToShow()) {
            final String albumUrl = getAlbumUrl();
            // RedditGalleryFull renders from its own image list and ignores this argument; every
            // other page only gets here because getAlbumUrl() was non-null.
            loadAlbum(albumUrl == null ? "" : albumUrl);
        }

        return rootView;
    }

    /**
     * Sizes the collapsed info panel to the title and pads the list by the same amount, since the
     * panel is {@code umanoOverlay} and covers that much of it. Both from one measurement so they
     * cannot disagree, and here rather than in the adapter because this height is measured at runtime
     * — no dimension an adapter could read is the right one.
     *
     * <p>Ignores a repeat of the height in {@code applied}: {@code setPanelHeight} re-lays out the
     * panel, which lays out the title again, which calls straight back in here. Static, and given
     * everything it touches, so it cannot reach a field that a rebuilt view has moved on from.
     */
    private static void applyPanelHeight(
            final SlidingUpPanelLayout panelLayout,
            final RecyclerView panelList,
            final int[] applied,
            final int panelHeight) {
        if (panelHeight <= 0 || panelHeight == applied[0]) {
            return;
        }
        applied[0] = panelHeight;
        panelLayout.setPanelHeight(panelHeight);
        LayoutUtils.insetForOverlay(panelList, panelHeight);
    }

    /** Shadowbox-hosted subclasses: resolve this page's submission, finishing if it is gone. */
    protected @Nullable Submission submissionForShadowboxPage() {
        Bundle bundle = this.getArguments();
        if (((Shadowbox) getActivity()).subredditPosts == null
                || ((Shadowbox) getActivity()).subredditPosts.getPosts().size()
                        <= bundle.getInt("page", 0)) {
            getActivity().finish();
            return null;
        } else {
            return ((Shadowbox) getActivity())
                    .subredditPosts
                    .getPosts()
                    .get(bundle.getInt("page", 0));
        }
    }

    /** Shadowbox-hosted subclasses: open the comments screen for the given shadowbox page. */
    protected void openShadowboxComments(int page) {
        Intent i2 = new Intent(getActivity(), CommentsScreen.class);
        i2.putExtra(CommentsScreen.EXTRA_PAGE, page);
        i2.putExtra(CommentsScreen.EXTRA_SUBREDDIT, ((Shadowbox) getActivity()).subreddit);
        (getActivity()).startActivity(i2);
    }
}
