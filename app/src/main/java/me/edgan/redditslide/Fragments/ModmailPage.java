package me.edgan.redditslide.Fragments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import me.edgan.redditslide.Activities.ModmailThread;
import me.edgan.redditslide.Adapters.ModmailAdapter;
import me.edgan.redditslide.Adapters.ModmailPosts;
import me.edgan.redditslide.Constants;
import me.edgan.redditslide.R;
import me.edgan.redditslide.Views.PreCachingLayoutManager;
import me.edgan.redditslide.Visuals.Palette;
import me.edgan.redditslide.handler.ToolbarScrollHideHandler;

/**
 * New Modmail conversation list used by the two moderation "Mod mail" tabs. Replaces {@link
 * InboxPage} for those tabs, which loaded the now-empty legacy {@code /message/moderator} endpoints.
 */
public class ModmailPage extends Fragment {

    private int totalItemCount;
    private int visibleItemCount;
    private int pastVisiblesItems;
    private ModmailAdapter adapter;
    private ModmailPosts posts;
    private SwipeRefreshLayout mSwipeRefreshLayout;
    private boolean onlyUnread;
    // -1 so the first resume always loads (readGeneration only ever counts up from 0).
    private int loadedGeneration = -1;

    @Override
    public View onCreateView(
            LayoutInflater inflater,
            @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_verticalcontent, container, false);

        final RecyclerView rv = v.requireViewById(R.id.vertical_content);
        final PreCachingLayoutManager mLayoutManager = new PreCachingLayoutManager(requireActivity());
        rv.setLayoutManager(mLayoutManager);

        mSwipeRefreshLayout = v.requireViewById(R.id.activity_main_swipe_refresh_layout);
        v.requireViewById(R.id.post_floating_action_button).setVisibility(View.GONE);

        mSwipeRefreshLayout.setColorSchemeColors(Palette.getColors("", requireActivity()));
        mSwipeRefreshLayout.setProgressViewOffset(
                false,
                Constants.TAB_HEADER_VIEW_OFFSET - Constants.PTR_OFFSET_TOP,
                Constants.TAB_HEADER_VIEW_OFFSET + Constants.PTR_OFFSET_BOTTOM);

        // The spinner is shown in onResume(), which also drives the actual load, so off-screen
        // tabs don't sit spinning before they are ever displayed.
        posts = new ModmailPosts(onlyUnread);
        adapter = new ModmailAdapter(requireContext(), posts, rv);
        rv.setAdapter(adapter);

        posts.bindAdapter(adapter, mSwipeRefreshLayout);

        mSwipeRefreshLayout.setOnRefreshListener(() -> posts.loadMore(adapter, true));

        rv.addOnScrollListener(
                new ToolbarScrollHideHandler(
                        requireActivity().requireViewById(R.id.toolbar),
                        requireActivity().requireViewById(R.id.header)) {
                    @Override
                    public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                        super.onScrolled(recyclerView, dx, dy);
                        // A failed load swaps in an ErrorAdapter, and RecyclerView dispatches
                        // onScrolled(0, 0) for the layout that swap triggers: its single row
                        // reads as "near the end", so paging off it retries the failed load
                        // forever. An empty list is not near its end either -- the first page
                        // comes from onResume().
                        if (rv.getAdapter() != adapter || mLayoutManager.getItemCount() == 0) {
                            return;
                        }
                        // After a failed page only a real scroll asks for it again: the failure
                        // itself relaid the footer out, and that dispatch is a (0, 0) one.
                        if (posts.loadFailed) {
                            if (dy == 0) {
                                return;
                            }
                            posts.loadFailed = false;
                        }
                        visibleItemCount = mLayoutManager.getChildCount();
                        totalItemCount = mLayoutManager.getItemCount();
                        pastVisiblesItems = mLayoutManager.findFirstVisibleItemPosition();

                        if (!posts.loading && !posts.nomore) {
                            if ((visibleItemCount + pastVisiblesItems) + 5 >= totalItemCount) {
                                posts.loading = true;
                                posts.loadMore(adapter, false);
                            }
                        }
                    }
                });
        return v;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Bundle bundle = this.getArguments();
        onlyUnread = bundle != null && bundle.getBoolean("unread", false);
    }

    /**
     * Both Mod mail tabs are separate fragments with their own conversation list, and the pager
     * keeps the off-screen one alive without recreating it. Opening a conversation marks it read,
     * which leaves whichever tab it was opened from showing it as unread. The pager uses {@code
     * BEHAVIOR_RESUME_ONLY_CURRENT_FRAGMENT}, so only the visible tab is resumed; we load here (the
     * first time the tab is shown) and re-fetch on a later resume only if {@link
     * ModmailThread#readGeneration} has advanced since this tab last loaded. That keeps the tabs in
     * step with reads made in a thread without discarding scroll position on every resume.
     */
    @Override
    public void onResume() {
        super.onResume();
        // posts/adapter/mSwipeRefreshLayout are all set together in onCreateView, which always runs
        // before onResume.
        if (posts == null || loadedGeneration == ModmailThread.readGeneration) {
            return;
        }
        loadedGeneration = ModmailThread.readGeneration;
        mSwipeRefreshLayout.setRefreshing(true);
        posts.loadMore(adapter, true);
    }
}
