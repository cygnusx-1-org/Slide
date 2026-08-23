package me.edgan.redditslide.Adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.recyclerview.widget.RecyclerView;

import me.edgan.redditslide.R;

/**
 * Scaffolding shared by the paginated list adapters (multireddit feed, subreddit discovery): the
 * spacer header, loading-spinner and no-more footers, and the error-adapter swap. Subclasses
 * supply the dataset state and the real content rows.
 */
public abstract class PaginatedListAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder>
        implements BaseAdapter {

    protected static final int LOADING_SPINNER = 5;
    protected static final int NO_MORE = 3;
    protected static final int SPACER = 6;

    protected final RecyclerView listView;

    protected PaginatedListAdapter(RecyclerView listView) {
        this.listView = listView;
    }

    /** Whether the dataset has no posts yet. */
    protected abstract boolean isEmpty();

    /** Number of loaded posts. */
    protected abstract int postCount();

    /** Whether pagination has ended. */
    protected abstract boolean noMore();

    /** Creates the holder for a real content row. */
    protected abstract RecyclerView.ViewHolder createContentViewHolder(ViewGroup viewGroup);

    @Override
    public void setError(Boolean b) {
        if (Boolean.TRUE.equals(b)) {
            listView.setAdapter(new ErrorAdapter());
        } else {
            undoSetError();
        }
    }

    @Override
    public void undoSetError() {
        listView.setAdapter(this);
    }

    @Override
    public int getItemViewType(int position) {
        if (position <= 0 && !isEmpty()) {
            return SPACER;
        } else if (!isEmpty()) {
            position -= (1);
        }
        if (position == postCount() && !isEmpty() && !noMore()) {
            return LOADING_SPINNER;
        } else if (position == postCount() && noMore()) {
            return NO_MORE;
        }
        return 1;
    }

    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup viewGroup, int i) {
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
        }
        return createContentViewHolder(viewGroup);
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
}
