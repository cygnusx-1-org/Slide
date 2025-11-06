package me.edgan.redditslide.Activities;

import android.os.Bundle;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentStatePagerAdapter;
import androidx.viewpager.widget.ViewPager;

import me.edgan.redditslide.Adapters.MultiredditPosts;
import me.edgan.redditslide.Adapters.SubmissionDisplay;
import me.edgan.redditslide.Adapters.SubredditPosts;
import me.edgan.redditslide.Authentication;
import me.edgan.redditslide.ContentType;
import me.edgan.redditslide.Fragments.AlbumFull;
import me.edgan.redditslide.Fragments.MediaFragment;
import me.edgan.redditslide.Fragments.RedditGalleryFull;
import me.edgan.redditslide.Fragments.SelftextFull;
import me.edgan.redditslide.Fragments.TitleFull;
import me.edgan.redditslide.Fragments.TumblrFull;
import me.edgan.redditslide.HasSeen;
import me.edgan.redditslide.LastComments;
import me.edgan.redditslide.OfflineSubreddit;
import me.edgan.redditslide.PostLoader;
import me.edgan.redditslide.R;
import me.edgan.redditslide.SettingValues;
import me.edgan.redditslide.util.MiscUtil;

import net.dean.jraw.models.Submission;

import java.util.List;

/** Created by ccrama on 9/17/2015. */
public class Shadowbox extends FullScreenActivity implements SubmissionDisplay {
    public static final String EXTRA_PROFILE = "profile";
    public static final String EXTRA_PAGE = "page";
    public static final String EXTRA_SUBREDDIT = "subreddit";
    public static final String EXTRA_MULTIREDDIT = "multireddit";
    public PostLoader subredditPosts;
    public String subreddit;
    int firstPage;
    private int count;

    public ViewPager pager;

    @Override
    public void onCreate(Bundle savedInstance) {
        overrideSwipeFromAnywhere();

        firstPage = getIntent().getExtras().getInt(EXTRA_PAGE, 0);
        subreddit = getIntent().getExtras().getString(EXTRA_SUBREDDIT);
        String multireddit = getIntent().getExtras().getString(EXTRA_MULTIREDDIT);
        String profile = getIntent().getExtras().getString(EXTRA_PROFILE, "");

        if (multireddit != null) {
            subredditPosts = new MultiredditPosts(multireddit, profile);
        } else {
            subredditPosts = new SubredditPosts(subreddit, Shadowbox.this);
        }
        subreddit = multireddit == null ? subreddit : ("multi" + multireddit);

        applyDarkColorTheme(subreddit);
        super.onCreate(savedInstance);
        setContentView(R.layout.activity_slide);
        MiscUtil.setupOldSwipeModeBackground(this, getWindow().getDecorView());

        long offline = getIntent().getLongExtra("offline", 0L);

        OfflineSubreddit submissions =
                OfflineSubreddit.getSubreddit(subreddit, offline, !Authentication.didOnline, this);

        // Always pre-populate from offline cache, just like Gallery mode
        subredditPosts.getPosts().addAll(submissions.submissions);
        count = subredditPosts.getPosts().size();
        Log.d("Shadowbox", "Loaded " + count + " posts from cache, didOnline=" + Authentication.didOnline);

        pager = (ViewPager) findViewById(R.id.content_view);
        submissionsPager = new ShadowboxPagerAdapter(getSupportFragmentManager());
        pager.setAdapter(submissionsPager);
        pager.setCurrentItem(firstPage);
        pager.addOnPageChangeListener(
                new ViewPager.SimpleOnPageChangeListener() {
                    @Override
                    public void onPageSelected(int position) {
                        // Load more posts when approaching the end (do this first, before any early returns)
                        if (count - 5 <= position && subredditPosts.hasMore()) {
                            Log.d("Shadowbox", "Triggering loadMore: position=" + position + ", count=" + count + ", hasMore=" + subredditPosts.hasMore());
                            if (subredditPosts instanceof SubredditPosts) {
                                if (!((SubredditPosts) subredditPosts).loading) {
                                    ((SubredditPosts) subredditPosts).loading = true;
                                    ((SubredditPosts) subredditPosts)
                                            .loadMore(
                                                    Shadowbox.this,
                                                    Shadowbox.this,
                                                    false,
                                                    subreddit);
                                }
                            } else if (subredditPosts instanceof MultiredditPosts) {
                                if (!((MultiredditPosts) subredditPosts).loading) {
                                    ((MultiredditPosts) subredditPosts).loading = true;
                                    subredditPosts.loadMore(Shadowbox.this, Shadowbox.this, false);
                                }
                            }
                        } else if (count - 5 <= position) {
                            Log.d("Shadowbox", "NOT loading more: position=" + position + ", count=" + count + ", hasMore=" + subredditPosts.hasMore() + ", nomore=" + (subredditPosts instanceof SubredditPosts ? ((SubredditPosts)subredditPosts).nomore : "N/A"));
                        }

                        // Track history (do this after loading logic to avoid early returns)
                        if (SettingValues.storeHistory) {
                            if (subredditPosts.getPosts().get(position).isNsfw()
                                    && !SettingValues.storeNSFWHistory) {
                                return;
                            }
                            HasSeen.addSeen(subredditPosts.getPosts().get(position).getFullName());
                        }
                    }
                });
    }

    ShadowboxPagerAdapter submissionsPager;

    @Override
    public void updateSuccess(final List<Submission> submissions, final int startIndex) {
        if (SettingValues.storeHistory) LastComments.setCommentsSince(submissions);
        runOnUiThread(
                new Runnable() {
                    @Override
                    public void run() {
                        int oldCount = count;
                        count = subredditPosts.getPosts().size();
                        Log.d("Shadowbox", "updateSuccess: oldCount=" + oldCount + ", newCount=" + count + ", submissions=" + submissions.size() + ", startIndex=" + startIndex);
                        if (startIndex != -1) {
                            // TODO determine correct behaviour
                            // comments.notifyItemRangeInserted(startIndex, posts.posts.size());
                            submissionsPager.notifyDataSetChanged();
                        } else {
                            submissionsPager.notifyDataSetChanged();
                        }
                    }
                });
    }

    @Override
    public void updateOffline(List<Submission> submissions, final long cacheTime) {
        runOnUiThread(
                new Runnable() {
                    @Override
                    public void run() {
                        count = subredditPosts.getPosts().size();
                        submissionsPager.notifyDataSetChanged();
                    }
                });
    }

    @Override
    public void updateOfflineError() {}

    @Override
    public void updateError() {}

    @Override
    public void updateViews() {}

    @Override
    public void onAdapterUpdated() {
        submissionsPager.notifyDataSetChanged();
    }

    private class ShadowboxPagerAdapter extends FragmentStatePagerAdapter {

        ShadowboxPagerAdapter(FragmentManager fm) {
            super(fm, BEHAVIOR_RESUME_ONLY_CURRENT_FRAGMENT);
        }

        @NonNull
        @Override
        public Fragment getItem(int i) {
            Fragment f = null;
            ContentType.Type t = ContentType.getContentType(subredditPosts.getPosts().get(i));
            Bundle args = new Bundle();

            switch (t) {
                case GIF:
                case IMAGE:
                case IMGUR:
                case REDDIT:
                case EXTERNAL:
                case SPOILER:
                case DEVIANTART:
                case EMBEDDED:
                case XKCD:
                case VREDDIT_DIRECT:
                case VREDDIT_REDIRECT:
                case LINK:
                case STREAMABLE:
                case VIDEO:
                        f = new MediaFragment();
                        Submission submission = subredditPosts.getPosts().get(i);
                        String previewUrl = "";
                        if (t != ContentType.Type.XKCD
                                && submission.getDataNode().has("preview")
                                && submission
                                        .getDataNode()
                                        .get("preview")
                                        .get("images")
                                        .get(0)
                                        .get("source")
                                        .has("height")) { // Load the preview image which has
                            // probably already been cached in
                            // memory instead of the direct link
                            previewUrl =
                                    submission
                                            .getDataNode()
                                            .get("preview")
                                            .get("images")
                                            .get(0)
                                            .get("source")
                                            .get("url")
                                            .asText();
                        }
                        args.putString("contentUrl", submission.getUrl());
                        args.putString("firstUrl", previewUrl);
                        break;
                case REDDIT_GALLERY:
                    f = new RedditGalleryFull();
                    break;
                case SELF:
                case NONE:
                    f = subredditPosts.getPosts().get(i).getSelftext().isEmpty()
                            ? new TitleFull()
                            : new SelftextFull();
                    break;
                case TUMBLR:
                    f = new TumblrFull();
                    break;
                case ALBUM:
                    f = new AlbumFull();
                    break;
                default:
                    throw new IllegalStateException("Unexpected content type: " + t);
            }

            args.putInt("page", i);
            args.putString("sub", subreddit);
            f.setArguments(args);

            return f;
        }

        @Override
        public int getCount() {
            return count;
        }
    }
}
