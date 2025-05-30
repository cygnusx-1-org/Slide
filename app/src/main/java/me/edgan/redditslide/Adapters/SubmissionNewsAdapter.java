package me.edgan.redditslide.Adapters;

/** Created by ccrama on 3/22/2015. */
import android.app.Activity;
import android.content.Intent;
import android.os.Handler;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;

import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.snackbar.Snackbar;

import me.edgan.redditslide.Activities.CommentsScreen;
import me.edgan.redditslide.Activities.MainActivity;
import me.edgan.redditslide.Activities.SubredditView;
import me.edgan.redditslide.Activities.MainPagerAdapterComment;
import me.edgan.redditslide.Authentication;
import me.edgan.redditslide.Fragments.SubmissionsView;
import me.edgan.redditslide.R;
import me.edgan.redditslide.Reddit;
import me.edgan.redditslide.SettingValues;
import me.edgan.redditslide.SubmissionViews.PopulateNewsViewHolder;
import me.edgan.redditslide.Views.CatchStaggeredGridLayoutManager;
import me.edgan.redditslide.Views.CreateCardView;
import me.edgan.redditslide.util.LayoutUtils;
import me.edgan.redditslide.util.OnSingleClickListener;

import net.dean.jraw.models.Submission;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class SubmissionNewsAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder>
        implements BaseAdapter {

    private final RecyclerView listView;
    public final String subreddit;
    public Activity context;
    private final boolean custom;
    public SubredditPostsRealm dataSet;
    public List<Submission> seen;
    private final int LOADING_SPINNER = 5;
    private final int NO_MORE = 3;
    private final int SPACER = 6;
    SubmissionDisplay displayer;

    public SubmissionNewsAdapter(
            Activity context,
            SubredditPostsRealm dataSet,
            RecyclerView listView,
            String subreddit,
            SubmissionDisplay displayer) {
        this.subreddit = subreddit.toLowerCase(Locale.ENGLISH);
        this.listView = listView;
        this.dataSet = dataSet;
        this.context = context;
        this.seen = new ArrayList<>();
        custom =
                SettingValues.prefs.contains(
                        Reddit.PREF_LAYOUT + subreddit.toLowerCase(Locale.ENGLISH));
        this.displayer = displayer;
        MainActivity.randomoverride = "";
    }

    @Override
    public void setError(Boolean b) {
        listView.setAdapter(new ErrorAdapter());
        isError = true;
        listView.setLayoutManager(
                SubmissionsView.createLayoutManager(
                        LayoutUtils.getNumColumns(
                                context.getResources().getConfiguration().orientation, context)));
    }

    public boolean isError;

    @Override
    public long getItemId(int position) {
        if (position <= 0 && !dataSet.posts.isEmpty()) {
            return SPACER;
        } else if (!dataSet.posts.isEmpty()) {
            position -= (1);
        }
        if (position == dataSet.posts.size()
                && !dataSet.posts.isEmpty()
                && !dataSet.offline
                && !dataSet.nomore) {
            return LOADING_SPINNER;
        } else if (position == dataSet.posts.size() && (dataSet.offline || dataSet.nomore)) {
            return NO_MORE;
        }
        return dataSet.posts.get(position).getCreated().getTime();
    }

    @Override
    public void undoSetError() {
        listView.setAdapter(this);
        isError = false;
        listView.setLayoutManager(
                SubmissionsView.createLayoutManager(
                        LayoutUtils.getNumColumns(
                                context.getResources().getConfiguration().orientation, context)));
    }

    @Override
    public int getItemViewType(int position) {
        if (position <= 0 && !dataSet.posts.isEmpty()) {
            return SPACER;
        } else if (!dataSet.posts.isEmpty()) {
            position -= (1);
        }
        if (position == dataSet.posts.size()
                && !dataSet.posts.isEmpty()
                && !dataSet.offline
                && !dataSet.nomore) {
            return LOADING_SPINNER;
        } else if (position == dataSet.posts.size() && (dataSet.offline || dataSet.nomore)) {
            return NO_MORE;
        }
        return 1;
    }

    int tag = 1;

    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup viewGroup, int i) {
        tag++;

        if (i == SPACER) {
            View v =
                    LayoutInflater.from(viewGroup.getContext())
                            .inflate(R.layout.spacer, viewGroup, false);
            return new SpacerViewHolder(v);

        } else if (i == LOADING_SPINNER) {
            View v =
                    LayoutInflater.from(viewGroup.getContext())
                            .inflate(R.layout.loadingmore, viewGroup, false);
            return new SubmissionFooterViewHolder(v);
        } else if (i == NO_MORE) {
            View v =
                    LayoutInflater.from(viewGroup.getContext())
                            .inflate(R.layout.nomoreposts, viewGroup, false);
            return new SubmissionFooterViewHolder(v);
        } else {
            View v = CreateCardView.CreateViewNews(viewGroup);
            return new NewsViewHolder(v);
        }
    }

    int clicked;

    @Override
    public void onBindViewHolder(final RecyclerView.ViewHolder holder2, final int pos) {

        int i = pos != 0 ? pos - 1 : pos;

        if (holder2 instanceof NewsViewHolder) {
            final NewsViewHolder holder = (NewsViewHolder) holder2;

            final Submission submission = dataSet.posts.get(i);
            CreateCardView.colorCard(
                    submission.getSubredditName().toLowerCase(Locale.ENGLISH),
                    holder.itemView,
                    subreddit,
                    (subreddit.equals("frontpage")
                            || subreddit.equals("mod")
                            || subreddit.equals("friends")
                            || (subreddit.equals("all"))
                            || subreddit.contains(".")
                            || subreddit.contains("+")));
            holder.itemView.setOnClickListener(
                    new OnSingleClickListener() {
                        @Override
                        public void onSingleClick(View v) {

                            if (Authentication.didOnline || submission.getComments() != null) {
                                holder.title.setAlpha(0.54f);

                                if (context instanceof MainActivity) {
                                    final MainActivity a = (MainActivity) context;
                                    if (a.singleMode
                                            && a.commentPager
                                            && a.adapter
                                                    instanceof
                                                    MainPagerAdapterComment) {

                                        if (a.openingComments != submission) {
                                            clicked = holder2.getBindingAdapterPosition();
                                            a.openingComments = submission;
                                            a.toOpenComments = a.pager.getCurrentItem() + 1;
                                            a.currentComment =
                                                    holder.getBindingAdapterPosition() - 1;
                                            ((MainPagerAdapterComment) (a).adapter)
                                                            .storedFragment =
                                                    (a).adapter.getCurrentFragment();
                                            ((MainPagerAdapterComment) (a).adapter)
                                                            .size =
                                                    a.toOpenComments + 1;
                                            try {
                                                a.adapter.notifyDataSetChanged();
                                            } catch (Exception ignored) {

                                            }
                                        }
                                        a.pager.postDelayed(
                                                new Runnable() {
                                                    @Override
                                                    public void run() {
                                                        a.pager.setCurrentItem(
                                                                a.pager.getCurrentItem() + 1, true);
                                                    }
                                                },
                                                400);

                                    } else {
                                        Intent i2 = new Intent(context, CommentsScreen.class);
                                        i2.putExtra(
                                                CommentsScreen.EXTRA_PAGE,
                                                holder2.getBindingAdapterPosition() - 1);
                                        i2.putExtra(CommentsScreen.EXTRA_SUBREDDIT, subreddit);
                                        i2.putExtra("fullname", submission.getFullName());
                                        context.startActivityForResult(i2, 940);
                                        clicked = holder2.getBindingAdapterPosition();
                                    }
                                } else if (context instanceof SubredditView) {
                                    final SubredditView a = (SubredditView) context;
                                    if (a.singleMode && a.commentPager) {

                                        if (a.openingComments != submission) {
                                            clicked = holder2.getBindingAdapterPosition();
                                            a.openingComments = submission;
                                            a.currentComment =
                                                    holder.getBindingAdapterPosition() - 1;
                                            ((SubredditView.SubredditPagerAdapterComment)
                                                                    (a).adapter)
                                                            .storedFragment =
                                                    (a).adapter.getCurrentFragment();
                                            ((SubredditView.SubredditPagerAdapterComment) a.adapter)
                                                            .size =
                                                    3;
                                            a.adapter.notifyDataSetChanged();
                                        }
                                        a.pager.postDelayed(
                                                new Runnable() {
                                                    @Override
                                                    public void run() {
                                                        a.pager.setCurrentItem(
                                                                a.pager.getCurrentItem() + 1, true);
                                                    }
                                                },
                                                400);

                                    } else {
                                        Intent i2 = new Intent(context, CommentsScreen.class);
                                        i2.putExtra(
                                                CommentsScreen.EXTRA_PAGE,
                                                holder2.getBindingAdapterPosition() - 1);
                                        i2.putExtra(CommentsScreen.EXTRA_SUBREDDIT, subreddit);
                                        i2.putExtra("fullname", submission.getFullName());
                                        context.startActivityForResult(i2, 940);
                                        clicked = holder2.getBindingAdapterPosition();
                                    }
                                }
                            } else {
                                if (!Reddit.appRestart.contains("offlinepopup")) {
                                    new AlertDialog.Builder(context)
                                            .setTitle(R.string.cache_no_comments_found)
                                            .setMessage(R.string.cache_no_comments_found_message)
                                            .setCancelable(false)
                                            .setPositiveButton(
                                                    R.string.btn_ok,
                                                    (dialog, which) ->
                                                            Reddit.appRestart
                                                                    .edit()
                                                                    .putString("offlinepopup", "")
                                                                    .apply())
                                            .show();
                                } else {
                                    Snackbar s =
                                            Snackbar.make(
                                                    holder.itemView,
                                                    R.string.cache_no_comments_found_snackbar,
                                                    Snackbar.LENGTH_SHORT);
                                    s.setAction(
                                            R.string.misc_more_info,
                                            new View.OnClickListener() {
                                                @Override
                                                public void onClick(View v) {
                                                    new AlertDialog.Builder(context)
                                                            .setTitle(
                                                                    R.string
                                                                            .cache_no_comments_found)
                                                            .setMessage(
                                                                    R.string
                                                                            .cache_no_comments_found_message)
                                                            .setCancelable(false)
                                                            .setPositiveButton(
                                                                    R.string.btn_ok,
                                                                    (dialog, which) ->
                                                                            Reddit.appRestart
                                                                                    .edit()
                                                                                    .putString(
                                                                                            "offlinepopup",
                                                                                            "")
                                                                                    .apply())
                                                            .show();
                                                }
                                            });
                                    LayoutUtils.showSnackbar(s);
                                }
                            }
                        }
                    });
            new PopulateNewsViewHolder()
                    .populateNewsViewHolder(
                            holder,
                            submission,
                            context,
                            false,
                            false,
                            dataSet.posts,
                            listView,
                            custom,
                            dataSet.offline,
                            dataSet.subreddit.toLowerCase(Locale.ENGLISH),
                            null);
        }
        if (holder2 instanceof SubmissionFooterViewHolder) {
            Handler handler = new Handler();

            final Runnable r =
                    new Runnable() {
                        public void run() {
                            notifyItemChanged(
                                    dataSet.posts.size() + 1); // the loading spinner to replaced by
                            // nomoreposts.xml
                        }
                    };

            handler.post(r);

            if (holder2.itemView.findViewById(R.id.reload) != null) {
                holder2.itemView
                        .findViewById(R.id.reload)
                        .setOnClickListener(
                                new View.OnClickListener() {
                                    @Override
                                    public void onClick(View v) {
                                        dataSet.loadMore(context, displayer, true);
                                    }
                                });
            }
        }
        if (holder2 instanceof SpacerViewHolder) {
            View header = (context).findViewById(R.id.header);

            int height = header.getHeight();

            if (height == 0) {
                header.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED);
                height = header.getMeasuredHeight();
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
            } else {
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
    }

    public static class SubmissionFooterViewHolder extends RecyclerView.ViewHolder {
        public SubmissionFooterViewHolder(View itemView) {
            super(itemView);
        }
    }

    public static class SpacerViewHolder extends RecyclerView.ViewHolder {
        public SpacerViewHolder(View itemView) {
            super(itemView);
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

    public void performClick(int adapterPosition) {
        if (listView != null) {
            RecyclerView.ViewHolder holder =
                    listView.findViewHolderForLayoutPosition(adapterPosition);
            if (holder != null) {
                View view = holder.itemView;
                if (view != null) {
                    view.performClick();
                }
            }
        }
    }
}
