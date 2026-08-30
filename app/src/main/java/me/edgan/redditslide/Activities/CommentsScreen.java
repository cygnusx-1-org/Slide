package me.edgan.redditslide.Activities;

import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.ViewGroup;
import android.view.Window;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentStatePagerAdapter;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager.widget.ViewPager;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import me.edgan.redditslide.Adapters.MultiredditPosts;
import me.edgan.redditslide.Adapters.SubmissionDisplay;
import me.edgan.redditslide.Adapters.SubredditPosts;
import me.edgan.redditslide.Authentication;
import me.edgan.redditslide.CommentRestoreState;
import me.edgan.redditslide.Fragments.BlankFragment;
import me.edgan.redditslide.Fragments.CommentPage;
import me.edgan.redditslide.HibernateState;
import me.edgan.redditslide.LastComments;
import me.edgan.redditslide.OfflineSubreddit;
import me.edgan.redditslide.PostLoader;
import me.edgan.redditslide.R;
import me.edgan.redditslide.Reddit;
import me.edgan.redditslide.SettingValues;
import me.edgan.redditslide.util.CustomViewPager;
import me.edgan.redditslide.util.KeyboardUtil;
import me.edgan.redditslide.util.LogUtil;
import me.edgan.redditslide.util.MiscUtil;
import net.dean.jraw.models.Submission;
import org.jspecify.annotations.NullMarked;

/**
 * This activity is responsible for the view when clicking on a post, showing the post and its
 * comments underneath with the slide left/right for the next post.
 *
 * <p>When the end of the currently loaded posts is being reached, more posts are loaded
 * asynchronously in {@link CommentsScreenPagerAdapter}.
 *
 * <p>Comments are displayed in the {@link CommentPage} fragment.
 *
 * <p>Created by ccrama on 9/17/2015.
 */
@NullMarked
public class CommentsScreen extends BaseActivityAnim
        implements SubmissionDisplay, HibernateState.Restorable {

    /** Where in the comments the user was when the app was last closed; empty on an ordinary open. */
    public final CommentRestoreState restore = new CommentRestoreState();

    public static final String EXTRA_PROFILE = "profile";
    public static final String EXTRA_PAGE = "page";
    public static final String EXTRA_SUBREDDIT = "subreddit";
    public static final String EXTRA_MULTIREDDIT = "multireddit";

    public ArrayList<Submission> currentPosts;

    public PostLoader subredditPosts;
    int firstPage;

    @SuppressWarnings("NullAway.Init") // assigned in onCreate
    CommentsScreenPagerAdapter comments;
    private String subreddit;
    private String baseSubreddit = "";

    String multireddit;
    String profile;

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        int keyCode = event.getKeyCode();
        if (SettingValues.commentVolumeNav) {
            switch (keyCode) {
                case KeyEvent.KEYCODE_VOLUME_UP:
                case KeyEvent.KEYCODE_VOLUME_DOWN:
                case KeyEvent.KEYCODE_SEARCH:
                    return ((CommentPage) comments.getCurrentFragment()).onKeyDown(keyCode, event);
                default:
                    return super.dispatchKeyEvent(event);
            }
        } else {
            return super.dispatchKeyEvent(event);
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        KeyboardUtil.hideKeyboard(this, findViewById(android.R.id.content).getWindowToken(), 0);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 14) {
            comments.notifyDataSetChanged();
        }
    }

    public int currentPage;
    public ArrayList<Integer> seen;

    public boolean popup;

    /**
     * The submission a pager position is showing, or -1 for a position showing no submission.
     *
     * This is {@link CommentsScreenPagerAdapter#getItem} read backwards, and it has to stay that
     * way: in {@code oldSwipeMode} everything up to and including {@code firstPage} is a
     * {@link BlankFragment}, so those positions have no submission at all rather than one an
     * offset away.
     */
    private int submissionIndexFor(int position) {
        if (SettingValues.oldSwipeMode) {
            if (position <= firstPage) {
                return -1;
            }
            position = position - 1;
        }
        return (position < 0 || position >= currentPosts.size()) ? -1 : position;
    }

    /**
     * Publishes the rows the feed should rebind on the way back. A vote cast on this screen only
     * reaches the feed row through that rebind, so this has to be reported even when the user never
     * changes pages. {@code lastPage} is left out unless the user actually moved, because the hosts
     * turn it into a {@code scrollToPositionWithOffset} and backing straight out must not scroll.
     */
    private void publishSeenResult(int index, boolean includeLastPage) {
        if (!seen.contains(index)) {
            seen.add(index);
        }

        Bundle conData = new Bundle();
        conData.putIntegerArrayList("seen", seen);
        if (includeLastPage) {
            conData.putInt("lastPage", index);
        }
        Intent intent = new Intent();
        intent.putExtras(conData);
        setResult(RESULT_OK, intent);
    }

    private class CommonPageChangeListener extends ViewPager.SimpleOnPageChangeListener {
        @Override
        public void onPageSelected(int position) {
            final int index = submissionIndexFor(position);
            if (index < 0) {
                return;
            }

            updateSubredditAndSubmission(currentPosts.get(index));

            if (currentPosts.size() - 2 <= index && subredditPosts.hasMore()) {
                subredditPosts.loadMore(
                        CommentsScreen.this.getApplicationContext(),
                        CommentsScreen.this,
                        false);
            }

            currentPage = index;
            publishSeenResult(index, true);
        }
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstance) {
        popup =
                getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE
                        && !SettingValues.fullCommentOverride;
        seen = new ArrayList<>();
        if (popup) {
            disableSwipeBackLayout();
            applyColorTheme();
            setTheme(R.style.popup);
            supportRequestWindowFeature(Window.FEATURE_NO_TITLE);
            getWindow().setBackgroundDrawableResource(android.R.color.transparent);
            super.onCreate(savedInstance);
            setContentView(R.layout.activity_slide_popup);
        } else {
            overrideSwipeFromAnywhere();
            applyColorTheme();
            getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            getWindow().getDecorView().setBackground(null);
            super.onCreate(savedInstance);
            setContentView(R.layout.activity_slide);
        }

        Reddit.setDefaultErrorHandler(this);

        firstPage = getIntent().getIntExtra(EXTRA_PAGE, -1);
        baseSubreddit = MiscUtil.orEmpty(getIntent().getStringExtra(EXTRA_SUBREDDIT));
        subreddit = baseSubreddit;
        multireddit = MiscUtil.orEmpty(getIntent().getStringExtra(EXTRA_MULTIREDDIT));
        profile = MiscUtil.orEmpty(getIntent().getStringExtra(EXTRA_PROFILE));
        currentPosts = new ArrayList<>();
        if (!multireddit.isEmpty()) {
            subredditPosts = new MultiredditPosts(multireddit, profile);
        } else {
            baseSubreddit = subreddit.toLowerCase(Locale.ENGLISH);
            subredditPosts = new SubredditPosts(baseSubreddit, CommentsScreen.this);
        }

        if (firstPage == RecyclerView.NO_POSITION || firstPage < 0) {
            firstPage = 0;
            // IS SINGLE POST
        } else {
            OfflineSubreddit o =
                    OfflineSubreddit.getSubreddit(
                            multireddit.isEmpty() ? baseSubreddit : "multi_" + multireddit,
                            OfflineSubreddit.currentid,
                            !Authentication.didOnline,
                            CommentsScreen.this);
            subredditPosts.getPosts().addAll(o.submissions);
            currentPosts.addAll(subredditPosts.getPosts());
        }

        if (getIntent().hasExtra("fullname")) {
            final String fullname = MiscUtil.orEmpty(getIntent().getStringExtra("fullname"));
            int found = RecyclerView.NO_POSITION;
            for (int i = 0; i < currentPosts.size(); i++) {
                if (MiscUtil.orEmpty(currentPosts.get(i).getFullName()).equals(fullname)) {
                    found = i;
                    break;
                }
            }
            if (found == RecyclerView.NO_POSITION) {
                // The intent names a post the cached listing no longer holds. A resume replays
                // the intent this screen was opened with, against a listing that has since been
                // refreshed -- and for a random feed, refreshed into a different subreddit
                // entirely. firstPage indexes the listing as it was, so leaving it alone opens
                // whichever post now sits at that row: silently, a thread the user never asked
                // for. The post itself is still in the cache directory from when it was read.
                final Submission stored = storedPost(fullname);
                if (stored != null) {
                    subredditPosts.getPosts().add(stored);
                    currentPosts.add(stored);
                    found = currentPosts.size() - 1;
                }
            }
            if (found != RecyclerView.NO_POSITION) {
                firstPage = found;
            }
            // Not found and not on disk either: firstPage is left as it came in. For an ordinary
            // tap that is the live index and still correct; only a resume can reach here with a
            // stale one, and there is nothing better left to open.
        }

        if (firstPage < 0
                || currentPosts.isEmpty()
                || currentPosts.size() <= firstPage
                || currentPosts.get(firstPage) == null) {
            // firstPage first: it is the index the two tests after it read with, and a negative
            // one reaches the get() as an out-of-bounds throw rather than as the finish() this
            // branch exists to reach.
            finish();
        } else {
            updateSubredditAndSubmission(currentPosts.get(firstPage));

            final CustomViewPager pager = (CustomViewPager) requireViewById(R.id.content_view);
            // final ViewPager pager = (ViewPager) findViewById(R.id.content_view);

            final Bundle hibernated = HibernateState.claim(this);
            if (hibernated != null) {
                restoreHibernateState(hibernated);
            }

            comments = new CommentsScreenPagerAdapter(getSupportFragmentManager());
            pager.setAdapter(comments);

            currentPage = firstPage;
            publishSeenResult(firstPage, false);

            if (SettingValues.oldSwipeMode) {
                pager.setCurrentItem(firstPage + 1);
            } else {
                pager.setCurrentItem(firstPage);
            }

            pager.setEntryPageIndex(firstPage);

            if (SettingValues.oldSwipeMode) {
                MiscUtil.setupOldSwipeModeBackground(this, pager);

                pager.addOnPageChangeListener(new CommonPageChangeListener() {
                    @Override
                    public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {
                        if (position <= firstPage && positionOffsetPixels == 0) {
                            finish();
                        }
                        if (position == firstPage && !popup) {
                            CommentsScreenPagerAdapter adapter =
                                    (CommentsScreenPagerAdapter) pager.getAdapter();
                            if (adapter != null && adapter.blankPage != null) {
                                adapter.blankPage.doOffset(positionOffset);
                            }
                        }
                    }
                });
            } else {
                pager.addOnPageChangeListener(new CommonPageChangeListener());
            }
        }
    }

    /**
     * The copy of {@code fullname} written to the cache directory when it was last read, or null
     * when there is none.
     */
    @Nullable
    private Submission storedPost(String fullname) {
        try {
            return OfflineSubreddit.getSubmissionFromStorage(
                    fullname,
                    this,
                    true,
                    new ObjectMapper().reader(),
                    // baseSubreddit is the listing's name -- "frontpage", or a multireddit --
                    // not the post's own subreddit, so its comment-sort preference would be the
                    // wrong answer if anything read it. Nothing does: this copy supplies the
                    // pager's post, and the thread on screen is the one CommentPage builds with
                    // the sort it resolved from the post's real subreddit.
                    SettingValues.defaultCommentSorting);
        } catch (IOException e) {
            LogUtil.e(e, "CommentsScreen could not read " + fullname);
            return null;
        }
    }

    private void updateSubredditAndSubmission(Submission post) {
        // A submission with no subreddit is a promoted post; that fallback was already here, just
        // written after the assignment it was meant to cover rather than as part of it.
        final String postSubreddit = post.getSubredditName();
        subreddit = postSubreddit == null ? "Promoted" : postSubreddit;
        themeSystemBars(subreddit);
        setRecentBar(subreddit);
    }

    @Override
    public void updateSuccess(final List<Submission> submissions, final int startIndex) {
        if (SettingValues.storeHistory) LastComments.setCommentsSince(submissions);
        currentPosts.clear();
        currentPosts.addAll(submissions);
        runOnUiThread(
                new Runnable() {
                    @Override
                    public void run() {
                        comments.notifyDataSetChanged();
                    }
                });
    }

    @Override
    public void updateOffline(List<Submission> submissions, final long cacheTime) {
        runOnUiThread(
                new Runnable() {
                    @Override
                    public void run() {
                        comments.notifyDataSetChanged();
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
        comments.notifyDataSetChanged();
    }

    @Override
    public void saveHibernateState(Bundle out) {
        if (currentPosts == null || currentPage < 0 || currentPage >= currentPosts.size()) {
            return;
        }
        final Fragment current = comments == null ? null : comments.getCurrentFragment();
        CommentRestoreState.capture(
                out,
                currentPosts.get(currentPage).getFullName(),
                current instanceof CommentPage ? (CommentPage) current : null);
    }

    @Override
    public void restoreHibernateState(Bundle in) {
        restore.read(in);
    }

    private class CommentsScreenPagerAdapter extends FragmentStatePagerAdapter {
        @SuppressWarnings("NullAway.Init") // assigned in setPrimaryItem as the pager swaps pages
        private CommentPage mCurrentFragment;
        @SuppressWarnings("NullAway.Init") // assigned in getItem
        public BlankFragment blankPage;

        CommentsScreenPagerAdapter(FragmentManager fm) {
            super(fm, BEHAVIOR_RESUME_ONLY_CURRENT_FRAGMENT);
        }

        Fragment getCurrentFragment() {
            return mCurrentFragment;
        }

        @Override
        public void setPrimaryItem(
                @NonNull ViewGroup container, int position, @NonNull Object object) {
            super.setPrimaryItem(container, position, object);
            if (getCurrentFragment() != object && object instanceof CommentPage) {
                mCurrentFragment = (CommentPage) object;
                if (!mCurrentFragment.loaded && mCurrentFragment.isAdded()) {
                    mCurrentFragment.doAdapter(true);
                }
            }
        }

        private Fragment createCommentPageFragment(int i) {
            Fragment f = new CommentPage();
            Bundle args = new Bundle();
            args.putString("id", MiscUtil.idFromFullname(currentPosts.get(i).getFullName()));
            args.putBoolean("archived", currentPosts.get(i).isArchived());
            args.putBoolean(
                    "contest",
                    currentPosts.get(i).getDataNode().path("contest_mode").asBoolean());
            args.putBoolean("locked", currentPosts.get(i).isLocked());
            args.putInt("page", i);
            args.putString("subreddit", currentPosts.get(i).getSubredditName());
            args.putString(
                    "baseSubreddit",
                    multireddit.isEmpty() ? baseSubreddit : "multi_" + multireddit);
            restore.applyTo(currentPosts.get(i).getFullName(), args);
            f.setArguments(args);
            return f;
        }

        @NonNull
        @Override
        public Fragment getItem(int i) {
            if (SettingValues.oldSwipeMode) {
                if (i <= firstPage || i == 0) {
                    blankPage = new BlankFragment();
                    return blankPage;
                } else {
                    return createCommentPageFragment(i - 1);
                }
            } else {
                return createCommentPageFragment(i);
            }
        }

        @Override
        public int getCount() {
            if (SettingValues.oldSwipeMode) {
                return currentPosts.size() + 1;
            } else {
                return currentPosts.size();
            }
        }
    }
}
