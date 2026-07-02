package me.edgan.redditslide.Adapters;

/** Created by ccrama on 3/22/2015. */
import android.app.Activity;
import android.content.Intent;
import android.os.Handler;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import androidx.recyclerview.widget.RecyclerView;
import java.util.List;
import java.util.Locale;
import me.edgan.redditslide.Activities.SubredditView;
import me.edgan.redditslide.Fragments.SubredditListView;
import me.edgan.redditslide.R;
import me.edgan.redditslide.SpoilerRobotoTextView;
import me.edgan.redditslide.Views.CatchStaggeredGridLayoutManager;
import me.edgan.redditslide.Views.CommentOverflow;
import me.edgan.redditslide.Visuals.Palette;
import me.edgan.redditslide.util.BlendModeUtil;
import me.edgan.redditslide.util.OnSingleClickListener;
import me.edgan.redditslide.util.SubmissionParser;
import net.dean.jraw.models.Subreddit;

public class SubredditAdapter extends PaginatedListAdapter {

    public Activity context;
    public SubredditNames dataSet;
    SubredditListView displayer;

    public SubredditAdapter(
            Activity context,
            SubredditNames dataSet,
            RecyclerView listView,
            SubredditListView displayer) {
        super(listView);
        this.dataSet = dataSet;
        this.context = context;
        this.displayer = displayer;
    }






    @Override
    public void onBindViewHolder(final RecyclerView.ViewHolder holder2, final int pos) {

        int i = pos != 0 ? pos - 1 : pos;
        if (holder2 instanceof SubredditViewHolder) {
            final SubredditViewHolder holder = (SubredditViewHolder) holder2;
            final Subreddit sub = dataSet.posts.get(i);

            holder.name.setText(sub.getDisplayName());
            if (sub.getLocalizedSubscriberCount() != null) {
                holder.subscribers.setText(
                        context.getString(
                                R.string.subreddit_subscribers_string,
                                sub.getLocalizedSubscriberCount()));
            } else {
                holder.subscribers.setVisibility(View.GONE);
            }

            holder.color.setBackgroundResource(R.drawable.circle);
            BlendModeUtil.tintDrawableAsModulate(
                    holder.color.getBackground(),
                    Palette.getColor(sub.getDisplayName().toLowerCase(Locale.ENGLISH)));
            holder.itemView.setOnClickListener(
                    new OnSingleClickListener() {
                        @Override
                        public void onSingleClick(View view) {
                            Intent inte = new Intent(context, SubredditView.class);
                            inte.putExtra(SubredditView.EXTRA_SUBREDDIT, sub.getDisplayName());
                            context.startActivityForResult(inte, 4);
                        }
                    });
            holder.overflow.setOnClickListener(
                    new OnSingleClickListener() {
                        @Override
                        public void onSingleClick(View view) {
                            Intent inte = new Intent(context, SubredditView.class);
                            inte.putExtra(SubredditView.EXTRA_SUBREDDIT, sub.getDisplayName());
                            context.startActivityForResult(inte, 4);
                        }
                    });
            holder.body.setOnClickListener(
                    new OnSingleClickListener() {
                        @Override
                        public void onSingleClick(View view) {
                            Intent inte = new Intent(context, SubredditView.class);
                            inte.putExtra(SubredditView.EXTRA_SUBREDDIT, sub.getDisplayName());
                            context.startActivityForResult(inte, 4);
                        }
                    });
            if (sub.getDataNode().get("public_description_html").asText().equals("null")) {
                holder.body.setVisibility(View.GONE);
                holder.overflow.setVisibility(View.GONE);
            } else {
                holder.body.setVisibility(View.VISIBLE);
                holder.overflow.setVisibility(View.VISIBLE);
                setViews(
                        sub.getDataNode().get("public_description_html").asText().trim(),
                        sub.getDisplayName().toLowerCase(Locale.ENGLISH),
                        holder.body,
                        holder.overflow);
            }

            try {
                int state = sub.isUserSubscriber() ? View.VISIBLE : View.INVISIBLE;
                holder.subbed.setVisibility(state);
            } catch (Exception e) {
                holder.subbed.setVisibility(View.INVISIBLE);
            }

        } else if (holder2 instanceof SubmissionFooterViewHolder) {
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
                holder2.itemView.setVisibility(View.INVISIBLE);
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

    private void setViews(
            String rawHTML,
            String subredditName,
            SpoilerRobotoTextView firstTextView,
            CommentOverflow commentOverflow) {
        if (rawHTML.isEmpty()) {
            return;
        }

        List<String> blocks = SubmissionParser.getBlocks(rawHTML);

        // In the Discover list we only show a short preview: the first text block. Rendering the
        // remaining blocks (horizontal rules, link lists, tables and other sidebar content) caused
        // large amounts of empty/dead space for subreddits with rich descriptions such as
        // r/Deltarune, so the overflow is intentionally skipped here.
        // The <div class="md"> case is when the description leads with a table or code block.
        if (!blocks.isEmpty() && !blocks.get(0).equals("<div class=\"md\">")) {
            firstTextView.setVisibility(View.VISIBLE);
            firstTextView.setTextHtml(blocks.get(0), subredditName);
        } else {
            firstTextView.setText("");
            firstTextView.setVisibility(View.GONE);
        }

        commentOverflow.removeAllViews();
        commentOverflow.setVisibility(View.GONE);
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
        View v =
                LayoutInflater.from(viewGroup.getContext())
                        .inflate(R.layout.subfordiscover, viewGroup, false);
        return new SubredditViewHolder(v);
    }

}
