package me.edgan.redditslide.Adapters;

/** Created by ccrama on 10/30/2015. */
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.recyclerview.widget.RecyclerView;

import me.edgan.redditslide.R;

public class ErrorAdapter extends RecyclerView.Adapter<ErrorAdapter.ViewHolder> {

    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View v =
                LayoutInflater.from(parent.getContext())
                        .inflate(R.layout.nointernet, parent, false);

        // Every adapter this replaces reserves the height of the floating header with a spacer
        // row, because the list is drawn underneath it. This one has a single row and no spacer,
        // so without the same offset the message renders behind the toolbar and the screen reads
        // as blank. Screens with no header leave the offset at zero.
        final View header = parent.getRootView().findViewById(R.id.header);
        if (header != null) {
            v.setPadding(
                    v.getPaddingLeft(),
                    v.getPaddingTop() + header.getHeight(),
                    v.getPaddingRight(),
                    v.getPaddingBottom());
        }
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(ViewHolder holder, int position) {}

    @Override
    public int getItemCount() {
        return 1;
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {

        public ViewHolder(View itemView) {
            super(itemView);
        }
    }
}
