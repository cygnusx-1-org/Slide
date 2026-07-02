package me.edgan.redditslide.Fragments;

import me.edgan.redditslide.Adapters.HistoryPosts;
import me.edgan.redditslide.Constants;

/** The read-later list: HistoryView backed by the "readLater" store under a single header. */
public class ReadLaterView extends HistoryView {

    @Override
    protected HistoryPosts createPosts() {
        return new HistoryPosts("readLater");
    }

    @Override
    protected int getHeaderViewOffset() {
        return Constants.SINGLE_HEADER_VIEW_OFFSET;
    }
}
