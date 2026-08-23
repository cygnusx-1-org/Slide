package me.edgan.redditslide.Fragments;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import java.util.List;
import me.edgan.redditslide.Activities.CommentsScreen;
import me.edgan.redditslide.Activities.Shadowbox;
import me.edgan.redditslide.R;
import me.edgan.redditslide.SettingValues;
import me.edgan.redditslide.SpoilerRobotoTextView;
import me.edgan.redditslide.SubmissionViews.PopulateShadowboxInfo;
import me.edgan.redditslide.Views.CommentOverflow;
import me.edgan.redditslide.markdown.MarkdownImages;
import me.edgan.redditslide.util.MiscUtil;
import me.edgan.redditslide.util.SubmissionParser;
import net.dean.jraw.models.Submission;

/** Created by ccrama on 6/2/2015. */
public class SelftextFull extends Fragment {

    private int i = 0;
    private Submission s;

    @Override
    public View onCreateView(
            LayoutInflater inflater,
            @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        ViewGroup rootView =
                (ViewGroup) inflater.inflate(R.layout.submission_textcard, container, false);

        PopulateShadowboxInfo.doActionbar(s, rootView, requireActivity(), true);

        if (!MiscUtil.orEmpty(s.getSelftext()).isEmpty()) {
            if (SettingValues.markdownNewReddit) {
                MarkdownImages.renderInto(
                        rootView.requireViewById(R.id.firstTextView),
                        rootView.requireViewById(R.id.commentOverflow),
                        MiscUtil.orEmpty(s.getSubredditName()),
                        s.getSelftext(),
                        s.getDataNode().path("selftext_html").asText(""),
                        s.getDataNode());
            } else {
                setViews(
                        s.getDataNode().path("selftext_html").asText(""),
                        MiscUtil.orEmpty(s.getSubredditName()),
                        rootView);
            }
        }
        rootView.requireViewById(R.id.desc)
                .setOnClickListener(
                        new View.OnClickListener() {
                            @Override
                            public void onClick(View v) {
                                // A click can arrive after the shadowbox page detaches; with no
                                // host there is nothing to start the comments screen from.
                                final FragmentActivity activity = getActivity();
                                if (activity == null) {
                                    return;
                                }
                                Intent i2 = new Intent(activity, CommentsScreen.class);
                                i2.putExtra(CommentsScreen.EXTRA_PAGE, i);
                                i2.putExtra(CommentsScreen.EXTRA_SUBREDDIT, sub);
                                activity.startActivity(i2);
                            }
                        });
        return rootView;
    }

    @Nullable public String sub;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Bundle bundle = requireArguments();
        i = bundle.getInt("page", 0);
        sub = bundle.getString("sub");
        if (((Shadowbox) requireActivity()).subredditPosts == null
                || ((Shadowbox) requireActivity()).subredditPosts.getPosts().size()
                        < bundle.getInt("page", 0)) {
            requireActivity().finish();
        } else {
            s = ((Shadowbox) requireActivity()).subredditPosts.getPosts().get(bundle.getInt("page", 0));
        }
    }

    private void setViews(String rawHTML, String subredditName, View base) {
        if (rawHTML.isEmpty()) {
            return;
        }

        List<String> blocks = SubmissionParser.getBlocks(rawHTML);

        int startIndex = 0;
        if (!blocks.get(0).startsWith("<table>") && !blocks.get(0).startsWith("<pre>")) {
            ((SpoilerRobotoTextView) base.requireViewById(R.id.firstTextView))
                    .setTextHtml(blocks.get(0), subredditName);
            startIndex = 1;
        }

        CommentOverflow overflow = base.requireViewById(R.id.commentOverflow);
        if (blocks.size() > 1) {
            if (startIndex == 0) {
                overflow.setViews(blocks, subredditName);
            } else {
                overflow.setViews(blocks.subList(startIndex, blocks.size()), subredditName);
            }
        }
    }
}
