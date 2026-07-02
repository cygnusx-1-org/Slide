package me.edgan.redditslide.Fragments;

import android.app.Activity;
import android.os.AsyncTask;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

import me.edgan.redditslide.Adapters.AlbumView;
import me.edgan.redditslide.ImgurAlbum.AlbumUtils;
import me.edgan.redditslide.ImgurAlbum.Image;
import me.edgan.redditslide.SubmissionViews.PopulateShadowboxInfo;

import net.dean.jraw.models.Submission;

/** Created by ccrama on 6/2/2015. */
public class AlbumFull extends BaseAlbumFull {

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

    public class LoadIntoRecycler extends AlbumUtils.GetAlbumWithCallback {

        String url;

        public LoadIntoRecycler(@NonNull String url, @NonNull Activity baseActivity) {
            super(url, baseActivity);
            // todo htis dontClose = true;
            this.url = url;
        }

        @Override
        public void doWithData(final List<Image> jsonElements) {
            super.doWithData(jsonElements);
            AlbumView adapter =
                    new AlbumView(
                            baseActivity, jsonElements, 0, s.getSubredditName(), s.getTitle());
            ((RecyclerView) list).setAdapter(adapter);
        }
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        i = this.getArguments().getInt("page", 0);
        s = submissionForShadowboxPage();
    }
}
