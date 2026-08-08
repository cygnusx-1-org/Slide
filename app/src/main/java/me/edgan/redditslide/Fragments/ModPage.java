package me.edgan.redditslide.Fragments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import me.edgan.redditslide.Activities.ModQueue;
import me.edgan.redditslide.Adapters.ModeratorAdapter;
import me.edgan.redditslide.Adapters.ModeratorPosts;
import me.edgan.redditslide.Constants;
import me.edgan.redditslide.R;
import me.edgan.redditslide.Views.PreCachingLayoutManager;
import me.edgan.redditslide.Visuals.Palette;
import me.edgan.redditslide.handler.ToolbarScrollHideHandler;

public class ModPage extends Fragment {

    public ModeratorAdapter adapter;
    private ModeratorPosts posts;
    private String id;
    private String sub;

    @Override
    public View onCreateView(
            LayoutInflater inflater,
            @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {

        View v = inflater.inflate(R.layout.fragment_verticalcontent, container, false);

        RecyclerView rv = v.requireViewById(R.id.vertical_content);
        final PreCachingLayoutManager mLayoutManager = new PreCachingLayoutManager(requireActivity());
        rv.setLayoutManager(mLayoutManager);

        v.requireViewById(R.id.post_floating_action_button).setVisibility(View.GONE);

        final SwipeRefreshLayout mSwipeRefreshLayout =
                v.requireViewById(R.id.activity_main_swipe_refresh_layout);

        mSwipeRefreshLayout.setColorSchemeColors(Palette.getColors(id, requireActivity()));

        // If we use 'findViewById(R.id.header).getMeasuredHeight()', 0 is always returned.
        // So, we estimate the height of the header in dp
        mSwipeRefreshLayout.setProgressViewOffset(
                false,
                Constants.TAB_HEADER_VIEW_OFFSET - Constants.PTR_OFFSET_TOP,
                Constants.TAB_HEADER_VIEW_OFFSET + Constants.PTR_OFFSET_BOTTOM);

        mSwipeRefreshLayout.post(
                new Runnable() {
                    @Override
                    public void run() {
                        mSwipeRefreshLayout.setRefreshing(true);
                    }
                });
        posts = new ModeratorPosts(id, sub);
        adapter = new ModeratorAdapter(requireActivity(), posts, rv);
        rv.setAdapter(adapter);

        rv.addOnScrollListener(
                new ToolbarScrollHideHandler(
                        ((ModQueue) requireActivity()).requireToolbar(),
                        (requireActivity()).requireViewById(R.id.header)));

        posts.bindAdapter(adapter, mSwipeRefreshLayout);
        mSwipeRefreshLayout.setOnRefreshListener(
                new SwipeRefreshLayout.OnRefreshListener() {
                    @Override
                    public void onRefresh() {
                        posts.loadMore(adapter, id, sub);
                    }
                });
        return v;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Bundle bundle = requireArguments();
        id = bundle.getString("id", "");
        sub = bundle.getString("subreddit", "");
    }
}
