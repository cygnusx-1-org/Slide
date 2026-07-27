package me.edgan.redditslide.Fragments;

import android.app.Activity;
import android.os.AsyncTask;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

import me.edgan.redditslide.Adapters.TumblrView;
import me.edgan.redditslide.SubmissionViews.PopulateShadowboxInfo;
import me.edgan.redditslide.Tumblr.Photo;
import me.edgan.redditslide.Tumblr.TumblrUtils;

import net.dean.jraw.models.Submission;

/** Created by ccrama on 6/2/2015. */
public class TumblrFull extends BaseAlbumFull {

    private int i = 0;
    private Submission s;

    @Override
    protected void bindActionbar() {
        PopulateShadowboxInfo.doActionbar(s, rootView, getActivity(), true);
    }

    @Override
    protected String getAlbumUrl() {
        return s == null ? null : s.getUrl();
    }

    @Override
    protected void openComments() {
        openShadowboxComments(i);
    }

    @Override
    protected void loadAlbum(String url) {
        new LoadIntoRecycler(url, getActivity()).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }

    public class LoadIntoRecycler extends TumblrUtils.GetTumblrPostWithCallback {

        String url;

        public LoadIntoRecycler(@NonNull String url, @NonNull Activity baseActivity) {
            super(url, baseActivity);
            // todo htis dontClose = true;
            this.url = url;
        }

        @Override
        public boolean doWithData(final List<Photo> jsonElements) {
            // A post with no photos has no album to build; super has already told onError().
            if (!super.doWithData(jsonElements)) {
                return false;
            }
            // The submission's title, not the host activity's: this runs inside Shadowbox, which is
            // not the Tumblr activity, and the adapter used to cast its host to Tumblr to find one.
            TumblrView adapter =
                    new TumblrView(
                            baseActivity, jsonElements, s.getSubredditName(), s.getTitle());
            ((RecyclerView) list).setAdapter(adapter);
            return true;
        }
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        i = this.getArguments().getInt("page", 0);
        s = submissionForShadowboxPage();
    }
}
