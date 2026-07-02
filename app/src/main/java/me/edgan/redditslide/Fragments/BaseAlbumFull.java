package me.edgan.redditslide.Fragments;

import android.animation.ValueAnimator;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;

import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.sothree.slidinguppanel.SlidingUpPanelLayout;

import me.edgan.redditslide.Activities.CommentsScreen;
import me.edgan.redditslide.Activities.Shadowbox;
import me.edgan.redditslide.R;

import net.dean.jraw.models.Submission;

/**
 * Shared scaffolding for the shadowbox album fragments (imgur/reddit albums and tumblr posts, for
 * both submissions and comment links): inflates the album card, binds the actionbar, wires the
 * vertical RecyclerView with the scroll-driven actionbar fade and the sliding comments panel, then
 * hands the album url to the subclass's loader.
 */
public abstract class BaseAlbumFull extends Fragment {

    protected View list;
    boolean hidden;
    View rootView;

    /** Binds the shadowbox actionbar for this fragment's content type. */
    protected abstract void bindActionbar();

    /** The album/gallery url to load. */
    protected abstract String getAlbumUrl();

    /** Opens the comments for the displayed content (tap while the panel is expanded). */
    protected abstract void openComments();

    /** Kicks off the subclass's album loader; the loader sets the adapter in doWithData. */
    protected abstract void loadAlbum(String url);

    @Override
    public View onCreateView(
            LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        rootView = inflater.inflate(R.layout.submission_albumcard, container, false);
        bindActionbar();

        list = rootView.findViewById(R.id.images);

        list.setVisibility(View.VISIBLE);
        final LinearLayoutManager layoutManager = new LinearLayoutManager(getActivity());
        layoutManager.setOrientation(LinearLayoutManager.VERTICAL);
        ((RecyclerView) list).setLayoutManager(layoutManager);

        ((RecyclerView) list)
                .addOnScrollListener(
                        new RecyclerView.OnScrollListener() {

                            @Override
                            public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
                                super.onScrolled(recyclerView, dx, dy);
                                ValueAnimator va = null;

                                if (dy > 0 && !hidden) {
                                    hidden = true;

                                    if (va != null && va.isRunning()) va.cancel();

                                    final View base = rootView.findViewById(R.id.base);
                                    va = ValueAnimator.ofFloat(1.0f, 0.2f);
                                    int mDuration = 250; // in millis
                                    va.setDuration(mDuration);
                                    va.addUpdateListener(
                                            new ValueAnimator.AnimatorUpdateListener() {
                                                public void onAnimationUpdate(
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
                                                public void onAnimationUpdate(
                                                        ValueAnimator animation) {
                                                    Float value =
                                                            (Float) animation.getAnimatedValue();
                                                    base.setAlpha(value);
                                                }
                                            });

                                    va.start();
                                }
                            }

                            @Override
                            public void onScrollStateChanged(
                                    RecyclerView recyclerView, int newState) {
                                super.onScrollStateChanged(recyclerView, newState);
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
        final View title = rootView.findViewById(R.id.title);
        title.getViewTreeObserver()
                .addOnGlobalLayoutListener(
                        new ViewTreeObserver.OnGlobalLayoutListener() {
                            @Override
                            public void onGlobalLayout() {
                                ((SlidingUpPanelLayout) rootView.findViewById(R.id.sliding_layout))
                                        .setPanelHeight(title.getMeasuredHeight());
                                title.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                            }
                        });
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

        String albumUrl = getAlbumUrl();
        if (albumUrl != null) { // null when the hosting activity is finishing
            loadAlbum(albumUrl);
        }

        return rootView;
    }

    /** Shadowbox-hosted subclasses: resolve this page's submission, finishing if it is gone. */
    protected Submission submissionForShadowboxPage() {
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
