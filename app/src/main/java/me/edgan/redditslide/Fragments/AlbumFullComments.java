package me.edgan.redditslide.Fragments;

import android.app.Activity;
import android.os.AsyncTask;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
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

    @Nullable private CommentUrlObject s;

    @Override
    protected void bindActionbar() {
        if (s != null) {
            PopulateShadowboxInfo.doActionbar(s.comment, rootView, getActivity(), true);
        }
    }

    @Override
    protected @Nullable String getAlbumUrl() {
        return s == null ? null : s.url;
    }

    @Override
    protected void openComments() {
        if (s == null) {
            return;
        }
        final Comment c = s.comment.getComment();
        // link_id is "t3_" + the submission id; without it there is no submission to open.
        final String linkId = c.getDataNode().path("link_id").asText();
        if (linkId.length() <= 3) {
            return;
        }
        String url =
                "https://reddit.com"
                        + "/r/"
                        + c.getSubredditName()
                        + "/comments/"
                        + linkId.substring(3)
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
        public boolean doWithData(final @Nullable List<Image> jsonElements) {
            // Nothing usable came back, so there is no album to build; super has already told
            // onError().
            if (!super.doWithData(jsonElements) || jsonElements == null) {
                return false;
            }
            // May be a bug with downloading multiple comment albums off the same submission
            AlbumView adapter =
                    new AlbumView(
                            baseActivity,
                            jsonElements,
                            s == null ? null : s.getSubredditName(),
                            s == null
                                    ? null
                                    : FileUtil.buildDownloadName(s.comment.getComment()));
            ((RecyclerView) list).setAdapter(adapter);
            return true;
        }
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
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
