package me.edgan.redditslide.Fragments;

import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.os.Bundle;
import android.view.ContextThemeWrapper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.RelativeLayout;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.view.MarginLayoutParamsCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.interpolator.view.animation.LinearOutSlowInInterpolator;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.snackbar.Snackbar;
import com.mikepenz.itemanimators.AlphaInAnimator;
import com.mikepenz.itemanimators.SlideUpAlphaAnimator;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import me.edgan.redditslide.Activities.MultiredditOverview;
import me.edgan.redditslide.Activities.Search;
import me.edgan.redditslide.Activities.Submit;
import me.edgan.redditslide.Adapters.MultiredditAdapter;
import me.edgan.redditslide.Adapters.MultiredditPosts;
import me.edgan.redditslide.Adapters.SubmissionDisplay;
import me.edgan.redditslide.Constants;
import me.edgan.redditslide.HasSeen;
import me.edgan.redditslide.Hidden;
import me.edgan.redditslide.OfflineSubreddit;
import me.edgan.redditslide.R;
import me.edgan.redditslide.Reddit;
import me.edgan.redditslide.SettingValues;
import me.edgan.redditslide.UserSubscriptions;
import me.edgan.redditslide.Views.CatchStaggeredGridLayoutManager;
import me.edgan.redditslide.Views.CreateCardView;
import me.edgan.redditslide.Visuals.ColorPreferences;
import me.edgan.redditslide.Visuals.Palette;
import me.edgan.redditslide.handler.ToolbarScrollHideHandler;
import me.edgan.redditslide.util.DialogUtil;
import me.edgan.redditslide.util.LayoutUtils;
import me.edgan.redditslide.util.MaterialInputDialog;
import me.edgan.redditslide.util.MiscUtil;
import net.dean.jraw.models.MultiReddit;
import net.dean.jraw.models.MultiSubreddit;
import net.dean.jraw.models.Submission;

public class MultiredditView extends Fragment implements SubmissionDisplay {

    private static final String EXTRA_PROFILE = "profile";

    // rv is bound unconditionally in onCreateView; adapter, posts and fab are bound only inside
    // its `multireddits != null && !multireddits.isEmpty()` branch, so an account with no
    // multireddits leaves those three null and every callback below has to tolerate it.
    @Nullable public MultiredditAdapter adapter;

    @Nullable public MultiredditPosts posts;

    @SuppressWarnings("NullAway.Init") // assigned in createLayoutManager
    public RecyclerView rv;

    @Nullable public FloatingActionButton fab;
    public int diff;
    private SwipeRefreshLayout refreshLayout;
    private int id;
    private int totalItemCount;
    private int visibleItemCount;
    private int pastVisiblesItems;
    private String profile;

    @NonNull
    private RecyclerView.LayoutManager createLayoutManager(final int numColumns) {
        return new CatchStaggeredGridLayoutManager(
                numColumns, CatchStaggeredGridLayoutManager.VERTICAL);
    }

    @Override
    public View onCreateView(
            LayoutInflater inflater,
            @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {

        View v = inflater.inflate(R.layout.fragment_verticalcontent, container, false);

        rv = v.requireViewById(R.id.vertical_content);
        final RecyclerView.LayoutManager mLayoutManager =
                createLayoutManager(
                        LayoutUtils.getNumColumns(
                                getResources().getConfiguration().orientation, requireActivity()));

        rv.setLayoutManager(mLayoutManager);
        if (SettingValues.fab) {
            fab = v.findViewById(R.id.post_floating_action_button);

            if (SettingValues.fabType == Constants.FAB_POST) {
                fab.setOnClickListener(
                        new View.OnClickListener() {
                            @Override
                            public void onClick(View v) {
                                // A detached fragment has no host here; there is nothing to act on.
                                final FragmentActivity activity = getActivity();
                                if (activity == null) {
                                    return;
                                }
                                if (posts == null) return;
                                final ArrayList<String> subs = new ArrayList<>();
                                if (posts.multiReddit != null) {
                                    for (MultiSubreddit s : posts.multiReddit.getSubreddits()) {
                                        subs.add(s.getDisplayName());
                                    }
                                }
                                final Context contextThemeWrapper =
                                        new ContextThemeWrapper(
                                                getActivity(),
                                                new ColorPreferences(activity)
                                                        .getFontStyle()
                                                        .getBaseId());
                                new MaterialAlertDialogBuilder(contextThemeWrapper)
                                        .setTitle(R.string.multi_submit_which_sub)
                                        .setItems(
                                                subs.toArray(new CharSequence[0]),
                                                (dialog, which) -> {
                                                    Intent i =
                                                            new Intent(getActivity(), Submit.class);
                                                    i.putExtra(
                                                            Submit.EXTRA_SUBREDDIT,
                                                            subs.get(which));
                                                    startActivity(i);
                                                })
                                        .show();
                            }
                        });
            } else if (SettingValues.fabType == Constants.FAB_SEARCH) {
                fab.setImageResource(R.drawable.ic_search);
                fab.setOnClickListener(
                        new View.OnClickListener() {
                            String term = "";

                            @Override
                            public void onClick(View v) {
                                // A detached fragment has no host here; there is nothing to act on.
                                final FragmentActivity activity = getActivity();
                                if (activity == null) {
                                    return;
                                }
                                if (posts == null) return;
                                final MultiredditPosts searchPosts = posts;
                                // Set the searchMulti for multireddit search
                                MultiredditOverview.searchMulti = searchPosts.multiReddit;

                                MaterialInputDialog.Builder builder =
                                        new MaterialInputDialog.Builder(activity)
                                                .title(R.string.search_title)
                                                .input(
                                                        getString(R.string.search_msg),
                                                        "",
                                                        (dialog, charSequence) ->
                                                                term = charSequence.toString());

                                // Only set search option for multireddit
                                builder.positiveText(getString(R.string.search_subreddit, "/m/" + posts.displayName()))
                                        .onPositive(
                                                dialog -> {
                                                    Intent i = new Intent(getActivity(), Search.class);
                                                    i.putExtra(Search.EXTRA_TERM, term);
                                                    i.putExtra(
                                                            Search.EXTRA_MULTIREDDIT,
                                                            searchPosts.displayName());
                                                    startActivity(i);
                                                });

                                builder.show();
                            }
                        });
            } else {
                fab.setImageResource(R.drawable.ic_visibility_off);
                fab.setOnClickListener(
                        new View.OnClickListener() {
                            @Override
                            public void onClick(View v) {
                                // A detached fragment has no host here; there is nothing to act on.
                                final FragmentActivity activity = getActivity();
                                if (activity == null) {
                                    return;
                                }
                                if (!Reddit.fabClear) {
                                    DialogUtil.showWithCardBackground(new AlertDialog.Builder(activity)
                                            .setTitle(R.string.settings_fabclear)
                                            .setMessage(R.string.settings_fabclear_msg)
                                            .setPositiveButton(
                                                    R.string.btn_ok,
                                                    (dialog, which) -> {
                                                        Reddit.colors
                                                                .edit()
                                                                .putBoolean(
                                                                        SettingValues
                                                                                .PREF_FAB_CLEAR,
                                                                        true)
                                                                .apply();
                                                        Reddit.fabClear = true;
                                                        clearSeenPosts(false);
                                                    })
                                            );
                                } else {
                                    clearSeenPosts(false);
                                }
                            }
                        });
                fab.setOnLongClickListener(
                        new View.OnLongClickListener() {
                            @Override
                            public boolean onLongClick(View v) {
                                // A detached fragment has no host here; there is nothing to act on.
                                final FragmentActivity activity = getActivity();
                                if (activity == null) {
                                    return false;
                                }
                                if (!Reddit.fabClear) {
                                    DialogUtil.showWithCardBackground(new AlertDialog.Builder(activity)
                                            .setTitle(R.string.settings_fabclear)
                                            .setMessage(R.string.settings_fabclear_msg)
                                            .setPositiveButton(
                                                    R.string.btn_ok,
                                                    (dialog, which) -> {
                                                        Reddit.colors
                                                                .edit()
                                                                .putBoolean(
                                                                        SettingValues
                                                                                .PREF_FAB_CLEAR,
                                                                        true)
                                                                .apply();
                                                        Reddit.fabClear = true;
                                                        clearSeenPosts(true);
                                                    })
                                            );
                                } else {
                                    clearSeenPosts(true);
                                }
                                /*
                                ToDo Make a sncakbar with an undo option of the clear all
                                View.OnClickListener undoAction = new View.OnClickListener() {
                                    @Override
                                    public void onClick(View v) {
                                        adapter.dataSet.posts = original;
                                        for(Submission post : adapter.dataSet.posts){
                                            if(HasSeen.getSeen(post.getFullName()))
                                                Hidden.undoHidden(post);
                                        }
                                    }
                                };*/
                                Snackbar s =
                                        Snackbar.make(
                                                rv,
                                                getResources()
                                                        .getString(R.string.posts_hidden_forever),
                                                Snackbar.LENGTH_LONG);
                                LayoutUtils.showSnackbar(s);

                                return false;
                            }
                        });
            }
        } else {
            v.findViewById(R.id.post_floating_action_button).setVisibility(View.GONE);
        }
        refreshLayout = v.requireViewById(R.id.activity_main_swipe_refresh_layout);

        /**
         * If using List view mode, we need to remove the start margin from the SwipeRefreshLayout.
         * The scrollbar style of "outsideInset" creates a 4dp padding around it. To counter this,
         * change the scrollbar style to "insideOverlay" when list view is enabled. To recap: this
         * removes the margins from the start/end so list view is full-width.
         */
        if (SettingValues.defaultCardView == CreateCardView.CardEnum.LIST) {
            RelativeLayout.LayoutParams params =
                    new RelativeLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT);
            MarginLayoutParamsCompat.setMarginStart(params, 0);
            rv.setScrollBarStyle(View.SCROLLBARS_INSIDE_OVERLAY);
            refreshLayout.setLayoutParams(params);
        }

        List<MultiReddit> multireddits;
        if (profile.isEmpty()) {
            multireddits = UserSubscriptions.multireddits;
        } else {
            multireddits = UserSubscriptions.public_multireddits.get(profile);
        }

        if ((multireddits != null) && !multireddits.isEmpty()) {
            refreshLayout.setColorSchemeColors(
                    Palette.getColors(MiscUtil.orEmpty(multireddits.get(id).getDisplayName()), requireActivity()));
        }

        // If we use 'findViewById(R.id.header).getMeasuredHeight()', 0 is always returned.
        // So, we estimate the height of the header in dp
        refreshLayout.setProgressViewOffset(
                false,
                Constants.TAB_HEADER_VIEW_OFFSET - Constants.PTR_OFFSET_TOP,
                Constants.TAB_HEADER_VIEW_OFFSET + Constants.PTR_OFFSET_BOTTOM);

        refreshLayout.post(
                new Runnable() {
                    @Override
                    public void run() {
                        refreshLayout.setRefreshing(true);
                    }
                });

        if ((multireddits != null) && !multireddits.isEmpty()) {
            posts = new MultiredditPosts(MiscUtil.orEmpty(multireddits.get(id).getDisplayName()), profile);

            adapter = new MultiredditAdapter(requireActivity(), posts, rv, refreshLayout, this);
            rv.setAdapter(adapter);
            rv.setItemAnimator(
                    new SlideUpAlphaAnimator().withInterpolator(new LinearOutSlowInInterpolator()));
            posts.loadMore(requireActivity(), this, true, adapter);

            refreshLayout.setOnRefreshListener(
                    new SwipeRefreshLayout.OnRefreshListener() {
                        @Override
                        public void onRefresh() {
                            // Folded into the existing branch rather than returning above it: that
                            // branch stops the spinner, and a detached page that skipped it left
                            // pull-to-refresh spinning forever.
                            final FragmentActivity activity = getActivity();
                            if (activity == null || posts == null || adapter == null) {
                                refreshLayout.setRefreshing(false);
                                return;
                            }
                            posts.loadMore(activity, MultiredditView.this, true, adapter);

                            // TODO catch errors
                        }
                    });

            if (fab != null) {
                fab.show();
            }

            rv.addOnScrollListener(
                    new ToolbarScrollHideHandler(
                            (requireActivity()).requireViewById(R.id.toolbar),
                            requireActivity().requireViewById(R.id.header)) {
                        @Override
                        public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                            super.onScrolled(recyclerView, dx, dy);
                            // Below super, not above it: the toolbar hide/show it drives needs no
                            // host, and only the loadMore at the bottom of this method does.
                            final FragmentActivity activity = getActivity();
                            if (activity == null) return;
                            if (posts == null || adapter == null) return;

                            final RecyclerView.LayoutManager lm = rv.getLayoutManager();
                            if (lm == null) return;

                            visibleItemCount = lm.getChildCount();
                            totalItemCount = lm.getItemCount();

                            int[] firstVisibleItems =
                                    ((CatchStaggeredGridLayoutManager) lm)
                                            .findFirstVisibleItemPositions(null);
                            if (firstVisibleItems != null && firstVisibleItems.length > 0) {
                                for (int firstVisibleItem : firstVisibleItems) {
                                    pastVisiblesItems = firstVisibleItem;
                                    if (SettingValues.scrollSeen
                                            && pastVisiblesItems > 0
                                            && SettingValues.storeHistory) {
                                        HasSeen.addSeenScrolling(
                                                posts.posts
                                                        .get(pastVisiblesItems - 1)
                                                        .getFullName());
                                    }
                                }
                            }

                            if (!posts.loading) {
                                if ((visibleItemCount + pastVisiblesItems) + 5 >= totalItemCount
                                        && !posts.nomore) {
                                    posts.loading = true;
                                    posts.loadMore(
                                            activity, MultiredditView.this, false, adapter);
                                }
                            }
                            if (recyclerView.getScrollState()
                                    == RecyclerView.SCROLL_STATE_DRAGGING) {
                                diff += dy;
                            } else {
                                diff = 0;
                            }
                            if (fab != null) {
                                if (dy <= 0 && fab.getId() != 0 && SettingValues.fab) {
                                    if (recyclerView.getScrollState()
                                                    != RecyclerView.SCROLL_STATE_DRAGGING
                                            || diff < -fab.getHeight() * 2) fab.show();
                                } else {
                                    fab.hide();
                                }
                            }
                        }
                    });
        }
        return v;
    }

    private @Nullable List<Submission> clearSeenPosts(boolean forever) {
        if (posts == null || adapter == null) return null;
        final MultiredditPosts loadedPosts = posts;
        final MultiredditAdapter loadedAdapter = adapter;
        if (loadedPosts.posts != null) {

            List<Submission> originalDataSetPosts = loadedPosts.posts;

            OfflineSubreddit o =
                    OfflineSubreddit.getSubreddit(
                            "multi_" + loadedPosts.displayName().toLowerCase(Locale.ENGLISH),
                            false,
                            getActivity());
            for (int i = loadedPosts.posts.size(); i > -1; i--) {
                try {
                    if (HasSeen.getSeen(loadedPosts.posts.get(i))) {
                        if (forever) {
                            Hidden.setHidden(loadedPosts.posts.get(i));
                        }
                        o.clearPost(loadedPosts.posts.get(i));
                        loadedPosts.posts.remove(i);
                        if (loadedPosts.posts.isEmpty()) {
                            loadedAdapter.notifyDataSetChanged();
                        } else {
                            rv.setItemAnimator(new AlphaInAnimator());
                            loadedAdapter.notifyItemRemoved(i + 1);
                        }
                    }
                } catch (IndexOutOfBoundsException e) {
                    // Let the loop reset itself
                }
            }
            o.writeToMemoryNoStorage();
            rv.setItemAnimator(
                    new SlideUpAlphaAnimator().withInterpolator(new LinearOutSlowInInterpolator()));
            return originalDataSetPosts;
        }

        return null;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Bundle bundle = requireArguments();
        id = bundle.getInt("id", 0);
        profile = bundle.getString(EXTRA_PROFILE, "");
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);

        final int currentOrientation = newConfig.orientation;

        final CatchStaggeredGridLayoutManager mLayoutManager =
                (CatchStaggeredGridLayoutManager) rv.getLayoutManager();
        if (mLayoutManager == null) {
            return;
        }

        mLayoutManager.setSpanCount(LayoutUtils.getNumColumns(currentOrientation, requireActivity()));
    }

    @Override
    public void updateSuccess(List<Submission> submissions, final int startIndex) {
        // posts and adapter are only built when the account has at least one multireddit
        // (onCreateView guards on multireddits being non-empty), so every entry point has to
        // tolerate their absence rather than assume the feed exists.
        if (adapter == null || posts == null) {
            // Posted, not called directly, for the reason updateError() below records: onCreateView
            // queues a setRefreshing(true) on this same view, so a direct stop would run first and
            // then be undone by that queued runnable.
            refreshLayout.post(() -> refreshLayout.setRefreshing(false));
            return;
        }
        final MultiredditAdapter loadedAdapter = adapter;
        final MultiredditPosts loadedPosts = posts;
        loadedAdapter.context.runOnUiThread(
                new Runnable() {
                    @Override
                    public void run() {
                        refreshLayout.setRefreshing(false);

                        if (startIndex != -1) {
                            loadedAdapter.notifyItemRangeInserted(
                                    startIndex + 1, loadedPosts.posts.size() - startIndex);
                        } else {
                            loadedAdapter.notifyDataSetChanged();
                        }
                    }
                });
    }

    @Override
    public void updateOffline(List<Submission> submissions, long cacheTime) {
        if (adapter == null) {
            refreshLayout.setRefreshing(false);
            return;
        }
        adapter.setError(true);
        refreshLayout.setRefreshing(false);
    }

    @Override
    public void updateOfflineError() {}

    @Override
    public void updateError() {
        // Was empty, which left the spinner running forever on a failed load — including the
        // multiReddit == null path MultiredditPosts.loadMore reports here rather than crashing in.
        //
        // Posted rather than called directly: onCreateView queues a setRefreshing(true) on this
        // same view, and that loadMore path reports synchronously from inside onCreateView, so a
        // direct setRefreshing(false) would run first and then be undone by the queued runnable.
        //
        // Stopping the spinner is all this does, matching SubmissionsView.updateError. Not
        // adapter.setError(true): that swaps in an ErrorAdapter, and nothing calls undoSetError
        // for MultiredditPosts the way SubredditPosts does for SubmissionsView, so a later
        // successful refresh would notify a detached adapter and leave the error screen up.
        refreshLayout.post(() -> refreshLayout.setRefreshing(false));
    }

    @Override
    public void updateViews() {
        if (adapter == null) return;
        try {
            adapter.notifyItemRangeChanged(0, adapter.dataSet.getPosts().size());
        } catch (Exception e) {
            // Range refresh against a list that changed size; the
            // next bind uses the current data.
        }
    }

    @Override
    public void onAdapterUpdated() {
        if (adapter == null) return;
        adapter.notifyDataSetChanged();
    }
}
