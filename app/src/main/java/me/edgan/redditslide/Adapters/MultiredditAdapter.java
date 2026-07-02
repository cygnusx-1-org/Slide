package me.edgan.redditslide.Adapters;

/** Created by ccrama on 3/22/2015. */
import android.app.Activity;
import android.content.Intent;
import android.os.Handler;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import com.google.android.material.snackbar.Snackbar;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import me.edgan.redditslide.Activities.CommentsScreen;
import me.edgan.redditslide.Authentication;
import me.edgan.redditslide.Fragments.MultiredditView;
import me.edgan.redditslide.R;
import me.edgan.redditslide.SubmissionViews.PopulateSubmissionViewHolder;
import me.edgan.redditslide.Views.CatchStaggeredGridLayoutManager;
import me.edgan.redditslide.Views.CreateCardView;
import me.edgan.redditslide.util.LayoutUtils;
import net.dean.jraw.models.Submission;

public class MultiredditAdapter extends PaginatedListAdapter {

    public Activity context;
    public MultiredditPosts dataSet;
    public List<Submission> seen;
    SwipeRefreshLayout refreshLayout;
    MultiredditView baseView;

    public MultiredditAdapter(
            Activity context,
            MultiredditPosts dataSet,
            RecyclerView listView,
            SwipeRefreshLayout refreshLayout,
            MultiredditView baseView) {
        super(listView);
        this.dataSet = dataSet;
        this.context = context;
        this.seen = new ArrayList<>();
        this.refreshLayout = refreshLayout;
        this.baseView = baseView;
    }






    int clicked;

    public void refreshView() {
        final RecyclerView.ItemAnimator a = listView.getItemAnimator();
        listView.setItemAnimator(null);
        notifyItemChanged(clicked);
        listView.postDelayed(
                new Runnable() {
                    @Override
                    public void run() {
                        listView.setItemAnimator(a);
                    }
                },
                500);
    }

    public void refreshView(ArrayList<Integer> seen) {
        listView.setItemAnimator(null);
        final RecyclerView.ItemAnimator a = listView.getItemAnimator();

        for (int i : seen) {
            notifyItemChanged(i + 1);
        }
        listView.postDelayed(
                new Runnable() {
                    @Override
                    public void run() {
                        listView.setItemAnimator(a);
                    }
                },
                500);
    }

    @Override
    public void onBindViewHolder(final RecyclerView.ViewHolder holder2, final int pos) {
        int i = (pos != 0) ? (pos - 1) : pos;

        if (holder2 instanceof SubmissionViewHolder) {
            final SubmissionViewHolder holder = (SubmissionViewHolder) holder2;
            final Submission submission = dataSet.posts.get(i);

            CreateCardView.colorCard(
                    submission.getSubredditName().toLowerCase(Locale.ENGLISH),
                    holder.itemView,
                    "multi_" + dataSet.multiReddit.getDisplayName(),
                    true);
            holder.itemView.setOnClickListener(
                    new View.OnClickListener() {
                        @Override
                        public void onClick(View arg0) {

                            if (Authentication.didOnline || submission.getComments() != null) {
                                holder.title.setAlpha(0.65f);
                                holder.leadImage.setAlpha(0.65f);
                                holder.thumbimage.setAlpha(0.65f);

                                Intent i2 = new Intent(context, CommentsScreen.class);
                                i2.putExtra(
                                        CommentsScreen.EXTRA_PAGE,
                                        holder2.getBindingAdapterPosition() - 1);
                                i2.putExtra(
                                        CommentsScreen.EXTRA_MULTIREDDIT,
                                        dataSet.multiReddit.getDisplayName());
                                context.startActivityForResult(i2, 940);
                                i2.putExtra("fullname", submission.getFullName());
                                clicked = holder2.getBindingAdapterPosition();

                            } else {
                                Snackbar s =
                                        Snackbar.make(
                                                holder.itemView,
                                                R.string.offline_comments_not_loaded,
                                                Snackbar.LENGTH_SHORT);
                                LayoutUtils.showSnackbar(s);
                            }
                        }
                    });

            new PopulateSubmissionViewHolder()
                    .populateSubmissionViewHolder(
                            holder,
                            submission,
                            context,
                            false,
                            false,
                            dataSet.posts,
                            listView,
                            true,
                            false,
                            "multi_"
                                    + dataSet.multiReddit
                                            .getDisplayName()
                                            .toLowerCase(Locale.ENGLISH),
                            null);
        }
        if (holder2 instanceof SubmissionFooterViewHolder) {
            // Only refresh when the footer actually needs to change type (e.g. the
            // loading spinner replaced by nomoreposts.xml). Posting unconditionally made
            // the footer re-bind itself on every bind, an endless redraw loop. Compute the
            // position inside the post so it reflects the list size when it actually runs.
            if (holder2.getItemViewType() != getItemViewType(dataSet.posts.size() + 1)) {
                new Handler().post(() -> notifyItemChanged(dataSet.posts.size() + 1));
            }
            if (holder2.itemView.findViewById(R.id.reload) != null) {
                holder2.itemView
                        .findViewById(R.id.reload)
                        .setOnClickListener(
                                new View.OnClickListener() {
                                    @Override
                                    public void onClick(View v) {
                                        dataSet.loadMore(
                                                context, baseView, true, MultiredditAdapter.this);
                                    }
                                });
            }
        }
        if (holder2 instanceof SpacerViewHolder) {
            final int height = (context).findViewById(R.id.header).getHeight();

            holder2.itemView
                    .findViewById(R.id.height)
                    .setLayoutParams(
                            new LinearLayout.LayoutParams(holder2.itemView.getWidth(), height));
            if (listView.getLayoutManager() instanceof CatchStaggeredGridLayoutManager) {
                CatchStaggeredGridLayoutManager.LayoutParams layoutParams =
                        new CatchStaggeredGridLayoutManager.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT, height);
                layoutParams.setFullSpan(true);
                holder2.itemView.setLayoutParams(layoutParams);
            }
        }
    }



    @Override
    public int getItemCount() {
        if (dataSet.posts == null || dataSet.posts.isEmpty()) {
            return 0;
        } else {
            return dataSet.posts.size() + 2; // Always account for footer
        }
    }
    @Override
    protected boolean isEmpty() {
        return dataSet.posts.isEmpty();
    }

    @Override
    protected int postCount() {
        return dataSet.posts.size();
    }

    @Override
    protected boolean noMore() {
        return dataSet.nomore;
    }

    @Override
    protected RecyclerView.ViewHolder createContentViewHolder(ViewGroup viewGroup) {
        View v = CreateCardView.CreateView(viewGroup);
        return new SubmissionViewHolder(v);
    }

}
