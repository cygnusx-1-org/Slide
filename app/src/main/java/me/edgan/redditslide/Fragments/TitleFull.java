package me.edgan.redditslide.Fragments;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import me.edgan.redditslide.Activities.CommentsScreen;
import me.edgan.redditslide.Activities.Shadowbox;
import me.edgan.redditslide.R;
import me.edgan.redditslide.SubmissionViews.PopulateShadowboxInfo;
import net.dean.jraw.models.Submission;

/** Created by ccrama on 6/2/2015. */
public class TitleFull extends Fragment {

    private int i = 0;
    @Nullable private Submission s;

    @Override
    public View onCreateView(
            LayoutInflater inflater,
            @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        ViewGroup rootView =
                (ViewGroup) inflater.inflate(R.layout.submission_titlecard, container, false);

        // onCreate finishes the activity when the (static) backing list no longer holds this
        // page — which happens on a process-death restore — but the view is still created.
        if (s == null) {
            return rootView;
        }

        PopulateShadowboxInfo.doActionbar(s, rootView, getActivity(), true);

        rootView.findViewById(R.id.desc)
                .setOnClickListener(
                        new View.OnClickListener() {
                            @Override
                            public void onClick(View v) {

                                Intent i2 = new Intent(getActivity(), CommentsScreen.class);
                                i2.putExtra(CommentsScreen.EXTRA_PAGE, i);
                                i2.putExtra(CommentsScreen.EXTRA_SUBREDDIT, sub);
                                (getActivity()).startActivity(i2);
                            }
                        });
        return rootView;
    }

    @Nullable public String sub;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Bundle bundle = this.getArguments();
        i = bundle.getInt("page", 0);
        sub = bundle.getString("sub");
        if (((Shadowbox) getActivity()).subredditPosts == null
                || ((Shadowbox) getActivity()).subredditPosts.getPosts().size()
                        < bundle.getInt("page", 0)) {
            getActivity().finish();
        } else {
            s = ((Shadowbox) getActivity()).subredditPosts.getPosts().get(bundle.getInt("page", 0));
        }
    }
}
