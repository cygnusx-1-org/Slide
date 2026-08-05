package me.edgan.redditslide.Fragments;

import android.app.Activity;
import android.os.AsyncTask;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
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
    @Nullable private Submission s;

    @Override
    protected void bindActionbar() {
        if (s == null) {
            return;
        }
        PopulateShadowboxInfo.doActionbar(s, rootView, getActivity(), true);
    }

    @Override
    protected @Nullable String getAlbumUrl() {
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
        public boolean doWithData(final @Nullable List<Image> jsonElements) {
            // Nothing usable came back, so there is no album to build; super has already told
            // onError().
            if (!super.doWithData(jsonElements) || jsonElements == null) {
                return false;
            }
            AlbumView adapter =
                    new AlbumView(
                            baseActivity,
                            jsonElements,
                            s == null ? null : s.getSubredditName(),
                            s == null ? null : s.getTitle());
            ((RecyclerView) list).setAdapter(adapter);
            return true;
        }
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        i = this.getArguments().getInt("page", 0);
        s = submissionForShadowboxPage();
    }
}
