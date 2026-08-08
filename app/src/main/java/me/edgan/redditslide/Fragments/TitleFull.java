package me.edgan.redditslide.Fragments;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
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

        PopulateShadowboxInfo.doActionbar(s, rootView, requireActivity(), true);

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
}
