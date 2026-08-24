package me.edgan.redditslide.Adapters;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Typeface;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;
import me.edgan.redditslide.Activities.ModmailThread;
import me.edgan.redditslide.Modmail.ModmailConversation;
import me.edgan.redditslide.R;
import me.edgan.redditslide.SettingValues;
import me.edgan.redditslide.Visuals.Palette;
import me.edgan.redditslide.util.TimeUtils;

/**
 * Lists New Modmail conversations (the New Modmail counterpart of {@link InboxAdapter}). Each row is
 * a conversation; tapping it opens the full thread in {@link ModmailThread}.
 */
public class ModmailAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder>
        implements BaseAdapter {

    private static final int SPACER = 6;
    private static final int LOADING = 5;
    private static final int CONVERSATION = 1;

    public final Context mContext;
    private final RecyclerView listView;
    public ModmailPosts dataSet;

    public ModmailAdapter(Context mContext, ModmailPosts dataSet, RecyclerView listView) {
        this.mContext = mContext;
        this.listView = listView;
        this.dataSet = dataSet;
    }

    @Override
    public void setError(Boolean b) {
        listView.setAdapter(new ErrorAdapter());
    }

    @Override
    public void undoSetError() {
        // Only swap back when the error view is actually showing: setAdapter() rebuilds the list
        // from scratch, so calling it after every successful page load would throw away the
        // scroll position mid-scroll.
        if (listView.getAdapter() != this) {
            listView.setAdapter(this);
        }
    }

    @Override
    public int getItemViewType(int position) {
        // getItemCount() reserves a row at each end. The trailing one is the loading footer while
        // more pages are coming, and a spacer once they are not — without that second case it would
        // bind as a conversation and index one past the end of the list.
        if (position == 0 && !dataSet.posts.isEmpty()) {
            return SPACER;
        }
        int i = position - 1;
        if (i == dataSet.posts.size() && !dataSet.posts.isEmpty()) {
            return dataSet.nomore || dataSet.loadFailed ? SPACER : LOADING;
        }
        return CONVERSATION;
    }

    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup viewGroup, int i) {
        if (i == SPACER) {
            View v =
                    LayoutInflater.from(viewGroup.getContext())
                            .inflate(R.layout.spacer, viewGroup, false);
            return new InboxAdapter.SpacerViewHolder(v);
        } else if (i == LOADING) {
            View v =
                    LayoutInflater.from(viewGroup.getContext())
                            .inflate(R.layout.loadingmore, viewGroup, false);
            return new ContributionAdapter.EmptyViewHolder(v);
        } else {
            View v =
                    LayoutInflater.from(viewGroup.getContext())
                            .inflate(R.layout.top_level_message, viewGroup, false);
            return new MessageViewHolder(v);
        }
    }

    @Override
    public void onBindViewHolder(final RecyclerView.ViewHolder viewHolder, int pos) {
        if (viewHolder instanceof InboxAdapter.SpacerViewHolder) {
            viewHolder
                    .itemView
                    .requireViewById(R.id.height)
                    .setLayoutParams(
                            new LinearLayout.LayoutParams(
                                    viewHolder.itemView.getWidth(),
                                    ((Activity) mContext).requireViewById(R.id.header).getHeight()));
            return;
        }
        if (viewHolder instanceof ContributionAdapter.EmptyViewHolder) {
            return;
        }

        final MessageViewHolder holder = (MessageViewHolder) viewHolder;
        final ModmailConversation conversation = dataSet.posts.get(pos - 1);

        holder.time.setText(
                TimeUtils.getTimeAgo(conversation.getLastUpdatedMillis(), mContext));

        // Title: subject, coloured red when the moderator has unread messages (matches inbox).
        holder.title.setText(conversation.getSubject());
        if (conversation.isUnread()) {
            holder.title.setTextColor(ContextCompat.getColor(mContext, R.color.md_red_400));
        } else {
            holder.title.setTextColor(holder.content.getCurrentTextColor());
        }

        // Byline: participant + subreddit + message count.
        SpannableStringBuilder byline = new SpannableStringBuilder();
        String participant = conversation.getParticipant();
        if (!participant.isEmpty()) {
            byline.append("/u/").append(participant).append(" ");
        }
        String subname = conversation.getSubreddit();
        if (!subname.isEmpty()) {
            SpannableStringBuilder subreddit = new SpannableStringBuilder("/r/" + subname);
            if (SettingValues.colorSubName
                    && Palette.getColor(subname) != Palette.getDefaultColor()) {
                subreddit.setSpan(
                        new ForegroundColorSpan(Palette.getColor(subname)),
                        0,
                        subreddit.length(),
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                subreddit.setSpan(
                        new StyleSpan(Typeface.BOLD),
                        0,
                        subreddit.length(),
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            byline.append(subreddit);
        }
        holder.user.setText(byline);

        // List rows have no body; show the message count in the content slot instead.
        holder.commentOverflow.removeAllViews();
        int count = conversation.getNumMessages();
        holder.content.setVisibility(View.VISIBLE);
        holder.content.setText(
                mContext.getResources()
                        .getQuantityString(R.plurals.modmail_message_count, count, count));

        holder.itemView.setOnClickListener(
                v -> {
                    Intent i = new Intent(mContext, ModmailThread.class);
                    i.putExtra(ModmailThread.EXTRA_ID, conversation.getId());
                    i.putExtra(ModmailThread.EXTRA_SUBJECT, conversation.getSubject());
                    mContext.startActivity(i);
                });
    }

    @Override
    public int getItemCount() {
        if (dataSet.posts == null || dataSet.posts.isEmpty()) {
            return 0;
        }
        // +1 spacer header, +1 loading/terminal footer.
        return dataSet.posts.size() + 2;
    }
}
