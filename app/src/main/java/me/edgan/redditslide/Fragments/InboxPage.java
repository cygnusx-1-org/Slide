package me.edgan.redditslide.Fragments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.SimpleItemAnimator;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import me.edgan.redditslide.Activities.Inbox;
import me.edgan.redditslide.Adapters.InboxAdapter;
import me.edgan.redditslide.Adapters.InboxMessages;
import me.edgan.redditslide.Constants;
import me.edgan.redditslide.R;
import me.edgan.redditslide.Views.CatchStaggeredGridLayoutManager;
import me.edgan.redditslide.Views.PreCachingLayoutManager;
import me.edgan.redditslide.Visuals.Palette;
import me.edgan.redditslide.handler.ToolbarScrollHideHandler;

public class InboxPage extends Fragment {

    private int totalItemCount;
    private int visibleItemCount;
    private int pastVisiblesItems;
    private InboxAdapter adapter;
    private InboxMessages posts;
    private String id;
    private SwipeRefreshLayout mSwipeRefreshLayout;
    // -1 so the first resume always loads (readGeneration only ever counts up from 0).
    private int loadedGeneration = -1;

    @Override
    public View onCreateView(
            LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_verticalcontent, container, false);

        final RecyclerView rv = v.findViewById(R.id.vertical_content);
        final PreCachingLayoutManager mLayoutManager = new PreCachingLayoutManager(getActivity());
        rv.setLayoutManager(mLayoutManager);

        mSwipeRefreshLayout = v.findViewById(R.id.activity_main_swipe_refresh_layout);
        v.findViewById(R.id.post_floating_action_button).setVisibility(View.GONE);

        // Marking a message read rebinds its row via notifyItemChanged; suppress the cross-fade
        // change animation so the read-state toggle doesn't flicker.
        if (rv.getItemAnimator() instanceof SimpleItemAnimator) {
            ((SimpleItemAnimator) rv.getItemAnimator()).setSupportsChangeAnimations(false);
        }

        mSwipeRefreshLayout.setColorSchemeColors(Palette.getColors(id, getActivity()));

        // If we use 'findViewById(R.id.header).getMeasuredHeight()', 0 is always returned.
        // So, we estimate the height of the header in dp
        mSwipeRefreshLayout.setProgressViewOffset(
                false,
                Constants.TAB_HEADER_VIEW_OFFSET - Constants.PTR_OFFSET_TOP,
                Constants.TAB_HEADER_VIEW_OFFSET + Constants.PTR_OFFSET_BOTTOM);

        // The spinner is shown in onResume(), which also drives the actual load, so off-screen tabs
        // don't sit spinning before they are ever displayed.
        posts = new InboxMessages(id);
        adapter = new InboxAdapter(getContext(), posts, rv);
        rv.setAdapter(adapter);

        posts.bindAdapter(adapter, mSwipeRefreshLayout);

        // TODO catch errors
        mSwipeRefreshLayout.setOnRefreshListener(
                new SwipeRefreshLayout.OnRefreshListener() {
                    @Override
                    public void onRefresh() {
                        posts.loadMore(adapter, id, true);

                        // TODO catch errors
                    }
                });
        rv.addOnScrollListener(
                new ToolbarScrollHideHandler(
                        (getActivity()).findViewById(R.id.toolbar),
                        getActivity().findViewById(R.id.header)) {
                    @Override
                    public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                        super.onScrolled(recyclerView, dx, dy);
                        visibleItemCount = rv.getLayoutManager().getChildCount();
                        totalItemCount = rv.getLayoutManager().getItemCount();

                        if (rv.getLayoutManager() instanceof PreCachingLayoutManager) {
                            pastVisiblesItems =
                                    ((PreCachingLayoutManager) rv.getLayoutManager())
                                            .findFirstVisibleItemPosition();
                        } else {
                            int[] firstVisibleItems = null;
                            firstVisibleItems =
                                    ((CatchStaggeredGridLayoutManager) rv.getLayoutManager())
                                            .findFirstVisibleItemPositions(firstVisibleItems);

                            if (firstVisibleItems != null && firstVisibleItems.length > 0) {
                                pastVisiblesItems = firstVisibleItems[0];
                            }
                        }

                        if (!posts.loading && !posts.nomore) {
                            if ((visibleItemCount + pastVisiblesItems) + 5 >= totalItemCount) {
                                posts.loading = true;
                                posts.loadMore(adapter, id, false);
                            }
                        }
                    }
                });
        return v;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Bundle bundle = this.getArguments();
        id = bundle.getString("id", "");
    }

    /**
     * Each inbox tab is a separate fragment with its own message list, and the ViewPager keeps
     * off-screen tabs alive without recreating them. Marking a message read in one tab (or via the
     * "mark all read" action) therefore leaves sibling tabs (most notably "unread") showing stale
     * state. The pager uses {@code BEHAVIOR_RESUME_ONLY_CURRENT_FRAGMENT}, so only the visible tab
     * is resumed; we load here (the first time the tab is shown) and re-fetch on a later resume only
     * if {@link Inbox#readGeneration} has advanced since this tab last loaded. That keeps sibling
     * tabs in sync with reads made elsewhere without discarding scroll position on every resume.
     */
    @Override
    public void onResume() {
        super.onResume();
        // posts/adapter/mSwipeRefreshLayout are all set together in onCreateView, which always runs
        // before onResume.
        if (posts == null || loadedGeneration == Inbox.readGeneration) {
            return;
        }
        loadedGeneration = Inbox.readGeneration;
        mSwipeRefreshLayout.setRefreshing(true);
        posts.loadMore(adapter, id, true);
    }
}
