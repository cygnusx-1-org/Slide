package me.edgan.redditslide;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.RemoteViews;
import android.widget.RemoteViewsService;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;
import me.edgan.redditslide.Adapters.SubredditPosts;
import me.edgan.redditslide.util.CompatUtil;
import me.edgan.redditslide.util.LogUtil;
import me.edgan.redditslide.util.TimeUtils;
import net.dean.jraw.models.Submission;

public class StackRemoteViewsFactory implements RemoteViewsService.RemoteViewsFactory {
    private final Context mContext;
    private List<Submission> submissions = new ArrayList<>();
    // Never assigned. StackWidgetService, the only thing that builds this factory, is not declared
    // in the manifest, so nothing can bind to it and onDataSetChanged never runs — it would NPE
    // here on the first call if it did. The live widget is Widget/ListViewRemoteViewsFactory.
    @Nullable private SubredditPosts posts;

    public StackRemoteViewsFactory(Context context, Intent intent) {
        mContext = context;
    }

    @Override public void onCreate() {}

    @Override public void onDestroy() {
        submissions.clear();
    }

    @Override public int getCount() {
        return submissions.size();
    }

    @Override public RemoteViews getViewAt(int position) {
        final RemoteViews rv =
                new RemoteViews(mContext.getPackageName(), R.layout.submission_widget);

        if (position <= getCount()) {

            final Submission submission = submissions.get(position);

            // No thumbnail: the row picked a preview/thumbnail url here and never drew it — and
            // submission_widget.xml has no image view to draw it into — so the selection went with
            // the dead code.
            rv.setTextViewText(R.id.title, CompatUtil.fromHtml(submission.getTitle()));

            rv.setTextViewText(R.id.subreddit, submission.getSubredditName());
            rv.setTextViewText(
                    R.id.info,
                    submission.getAuthor()
                            + " "
                            + TimeUtils.getTimeAgo(submission.getCreated().getTime(), mContext));

            Bundle extras = new Bundle();
            extras.putString("url", submission.getUrl());
            Intent fillInIntent = new Intent();
            fillInIntent.putExtras(extras);
            rv.setOnClickFillInIntent(R.id.card, fillInIntent);
        }

        return rv;
    }

    /** Null tells the framework to use the widget's default loading view. */
    @Override @Nullable public RemoteViews getLoadingView() {
        return null;
    }

    @Override public int getViewTypeCount() {
        return 1;
    }

    @Override public long getItemId(int position) {
        return position;
    }

    @Override public boolean hasStableIds() {
        return true;
    }

    @Override public void onDataSetChanged() {
        Log.v(LogUtil.getTag(), "MAKING POSTS");
        if (posts == null) {
            return;
        }
        submissions = posts.posts;
        Log.v(LogUtil.getTag(), "POSTS IS SIZE " + submissions.size());
    }
}
