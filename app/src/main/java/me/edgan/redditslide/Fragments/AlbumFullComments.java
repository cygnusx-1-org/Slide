package me.edgan.redditslide.Fragments;

import android.app.Activity;
import android.os.AsyncTask;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

import me.edgan.redditslide.Activities.ShadowboxComments;
import me.edgan.redditslide.Adapters.AlbumView;
import me.edgan.redditslide.Adapters.CommentUrlObject;
import me.edgan.redditslide.ImgurAlbum.AlbumUtils;
import me.edgan.redditslide.ImgurAlbum.Image;
import me.edgan.redditslide.OpenRedditLink;
import me.edgan.redditslide.SubmissionViews.PopulateShadowboxInfo;
import me.edgan.redditslide.util.FileUtil;

import net.dean.jraw.models.Comment;

/** Created by ccrama on 6/2/2015. */
public class AlbumFullComments extends BaseAlbumFull {

    private CommentUrlObject s;

    @Override
    protected void bindActionbar() {
        if (s != null) {
            PopulateShadowboxInfo.doActionbar(s.comment, rootView, getActivity(), true);
        }
    }

    @Override
    protected String getAlbumUrl() {
        return s == null ? null : s.url;
    }

    @Override
    protected void openComments() {
        if (s == null) {
            return;
        }
        final Comment c = s.comment.getComment();
        String url =
                "https://reddit.com"
                        + "/r/"
                        + c.getSubredditName()
                        + "/comments/"
                        + c.getDataNode().get("link_id").asText().substring(3)
                        + "/nothing/"
                        + c.getId()
                        + "?context=3";
        OpenRedditLink.openUrl(getActivity(), url, true);
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
            // May be a bug with downloading multiple comment albums off the same submission
            AlbumView adapter =
                    new AlbumView(
                            baseActivity,
                            jsonElements,
                            0,
                            s.getSubredditName(),
                            FileUtil.buildDownloadName(s.comment.getComment()));
            ((RecyclerView) list).setAdapter(adapter);
        }
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        int page = this.getArguments().getInt("page", 0);
        // The backing list is static; after process death it comes back null/empty while
        // the fragment is recreated with its old page argument.
        if (ShadowboxComments.comments == null || ShadowboxComments.comments.size() <= page) {
            getActivity().finish();
        } else {
            s = ShadowboxComments.comments.get(page);
        }
    }
}
