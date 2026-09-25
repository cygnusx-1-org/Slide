package me.edgan.redditslide.Activities;

import android.os.Bundle;
import android.os.Parcelable;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.viewpager.widget.ViewPager;
import java.util.Locale;
import me.edgan.redditslide.Adapters.SubredditPosts;
import me.edgan.redditslide.Authentication;
import me.edgan.redditslide.Fragments.CommentPage;
import me.edgan.redditslide.Fragments.SubmissionsView;
import me.edgan.redditslide.Visuals.Palette;
import me.edgan.redditslide.util.MiscUtil;
import me.edgan.redditslide.util.StringUtil;
import org.jspecify.annotations.NullMarked;


@NullMarked
public class MainPagerAdapterComment extends MainPagerAdapter {
    public int size;
    @SuppressWarnings("NullAway.Init") // assigned by SubmissionAdapter
    public Fragment storedFragment;
    @SuppressWarnings("NullAway.Init") // assigned in doSetPrimary
    CommentPage mCurrentComments;
    MainActivity mainActivity;

    public MainPagerAdapterComment(MainActivity mainActivity, FragmentManager fm) {
        super(mainActivity, fm);
        this.mainActivity = mainActivity;
        this.size = mainActivity.usedArray.size();
        mainActivity.pager.clearOnPageChangeListeners();
        mainActivity.pager.addOnPageChangeListener(
                new ViewPager.SimpleOnPageChangeListener() {
                    @Override
                    public void onPageScrolled(
                            int position, float positionOffset, int positionOffsetPixels) {
                        if (positionOffset == 0) {
                            if (position != mainActivity.toOpenComments) {
                                mainActivity.pager.setSwipeLeftOnly(true);
                                final String scrolledTo = subredditForPage(position);
                                if (!scrolledTo.isEmpty()) {
                                    mainActivity.header.setBackgroundColor(
                                            Palette.getColor(scrolledTo));
                                }
                                mainActivity.doPageSelectedComments(position);
                                if (position == mainActivity.toOpenComments - 1 && mainActivity.adapter != null && mainActivity.adapter.getCurrentFragment() != null) {
                                    SubmissionsView page = (SubmissionsView) mainActivity.adapter.getCurrentFragment();

                                    if (page != null && page.adapter != null) {
                                        page.adapter.refreshView();
                                    }
                                }
                            } else {
                                if (mainActivity.sidebarController != null) {
                                    mainActivity.sidebarController.cancelAsyncGetSubredditTask();
                                }

                                if (mainActivity.header.getTranslationY() == 0) {
                                    mainActivity.header.animate()
                                            .translationY(-mainActivity.header.getHeight() * 1.5f)
                                            .setInterpolator(new android.view.animation.LinearInterpolator())
                                            .setDuration(180);
                                }

                                mainActivity.pager.setSwipeLeftOnly(true);
                                if (mainActivity.openingComments != null) {
                                    final String sub =
                                            MiscUtil.orEmpty(
                                                            mainActivity
                                                                    .openingComments
                                                                    .getSubredditName())
                                                    .toLowerCase(Locale.ENGLISH);
                                    mainActivity.themeSystemBars(sub);
                                    mainActivity.setRecentBar(sub);
                                }
                            }
                        }
                    }

                    @Override
                    public void onPageSelected(final int position) {
                        if (position == mainActivity.toOpenComments - 1
                                && mainActivity.adapter != null
                                && mainActivity.adapter.getCurrentFragment() != null) {
                            SubmissionsView page =
                                    (SubmissionsView) mainActivity.adapter.getCurrentFragment();
                            if (page != null && page.adapter != null) {
                                page.adapter.refreshView();
                                SubredditPosts p = page.adapter.dataSet;
                                if (p.offline && !p.restoredFromCache) {
                                    p.doMainActivityOffline(mainActivity, p.displayer);
                                }
                            }
                        } else {
                            SubmissionsView page =
                                    (SubmissionsView) mainActivity.adapter.getCurrentFragment();
                            if (page != null && page.adapter != null) {
                                SubredditPosts p = page.adapter.dataSet;
                                if (p.offline && !p.restoredFromCache) {
                                    p.doMainActivityOffline(mainActivity, p.displayer);
                                }
                            }
                        }
                    }
                });
        notifyDataSetChanged();
    }

    @Override
    public int getCount() {
        if (mainActivity.usedArray == null) {
            return 1;
        } else {
            if (hideSubredditTabs) {
                // Count special subreddits and multi-reddits
                int count = 0;
                for (String sub : mainActivity.usedArray) {
                    if (isSpecialOrMulti(sub)) {
                        count++;
                    }
                }

                // Always include the comment page
                return count + 1;
            } else {
                return size;
            }
        }
    }

    @NonNull
    @Override
    public Fragment getItem(int i) {
        if (mainActivity.openingComments == null || i != mainActivity.toOpenComments) {
            SubmissionsView f = new SubmissionsView();
            Bundle args = new Bundle();
            String name = ""; // Initialize name

            if (hideSubredditTabs) {
                // Find the i-th special subreddit or multi-reddit
                int specialIndex = 0;
                boolean found = false;

                for (String s : mainActivity.usedArray) {
                    if (isSpecialOrMulti(s)) {
                        if (specialIndex == i) {
                            // Ensure full path for multi-reddits even when hidden
                            if (s.startsWith("/m/")) {
                                 if (mainActivity.multiNameToSubsMap.containsKey(s)) {
                                    name = mainActivity.multiNameToSubsMap.get(s);
                                } else {
                                    // Construct full path if map lookup fails
                                    name = "api/user/" + Authentication.name + s; // s already starts with /m/
                                }
                            } else {
                                name = s; // Standard special subreddits (frontpage, all)
                            }
                            found = true;
                            break;
                        }
                        specialIndex++;
                    }
                }

                // Fallback to the first subreddit if no special subreddit or multi-reddit was found at index i
                if (!found && !mainActivity.usedArray.isEmpty()) {
                     name = mainActivity.usedArray.get(0);
                     // Handle potential multi-reddit fallback case
                     if (name.startsWith("/m/")) {
                         if (mainActivity.multiNameToSubsMap.containsKey(name)) {
                            name = mainActivity.multiNameToSubsMap.get(name);
                        } else {
                            // Construct full path if map lookup fails
                            name = "api/user/" + Authentication.name + name; // name already starts with /m/
                        }
                     }
                }

            } else if (mainActivity.usedArray.size() > i) {
                 String potentialMulti = mainActivity.usedArray.get(i);
                 if (mainActivity.multiNameToSubsMap.containsKey(potentialMulti)) {
                    name = mainActivity.multiNameToSubsMap.get(potentialMulti); // Use the full path from the map
                } else if (potentialMulti.startsWith("/m/")) {
                    // If map lookup fails BUT it looks like a multi-reddit, construct the path
                    name = "api/user/" + Authentication.name + potentialMulti; // potentialMulti starts with /m/
                } else {
                    // Regular subreddit or other special case
                    name = potentialMulti;
                }
            }

            if (!name.isEmpty()) { // Ensure name is not empty before putting in args
                args.putString("id", name);
                mainActivity.applyRestoreArgs(name, args);
            }
            f.setArguments(args);
            return f;
        } else {
            Fragment f = new CommentPage();
            Bundle args = new Bundle();
            args.putString(
                    "id",
                    MiscUtil.idFromFullname(mainActivity.openingComments.getFullName()));
            args.putBoolean("archived", mainActivity.openingComments.isArchived());
            args.putBoolean(
                    "contest", mainActivity.openingComments.getDataNode().path("contest_mode").asBoolean());
            args.putBoolean("locked", mainActivity.openingComments.isLocked());
            args.putInt("page", mainActivity.currentComment);
            args.putString("subreddit", mainActivity.openingComments.getSubredditName());
            args.putString("baseSubreddit", mainActivity.subToDo);
            mainActivity.commentRestore.applyTo(
                    mainActivity.openingComments.getFullName(), args);
            f.setArguments(args);
            return f;
        }
    }

    /**
     * Whether the comment page is occupying a page of its own right now.
     *
     * <p>{@code toOpenComments} keeps the position it was last opened at after the page is gone,
     * so the index alone does not say whether that slot is a thread or a feed; {@code
     * openingComments} is what {@link #getItem} actually branches on.
     */
    private boolean commentPageShowing() {
        return mainActivity.openingComments != null && mainActivity.toOpenComments >= 0;
    }

    /**
     * {@inheritDoc}
     *
     * <p>The comment page takes a slot rather than shifting the ones above it: {@link #getItem}
     * branches on {@code i != toOpenComments} and otherwise walks the specials by the raw
     * position, exactly as the plain adapter does. So the inherited mapping is right for every
     * page but the thread's own, and the subreddit that would have sat in that slot has no page
     * at all while the thread is open.
     */
    @Override
    public String subredditForPage(int position) {
        if (commentPageShowing() && position == mainActivity.toOpenComments) {
            return ""; // a thread, not a listing
        }
        final String named = super.subredditForPage(position);
        if (!named.isEmpty() || position < 0 || position >= getCount()) {
            return named;
        }
        // getItem falls back to the first subscription for any page its walk cannot name. On the
        // plain adapter that is only ever page 0, because getCount() stops at the number of
        // specials; here there is one page more than that, so the page past the last special is
        // reachable and really is showing usedArray.get(0).
        return mainActivity.usedArray == null || mainActivity.usedArray.isEmpty()
                ? ""
                : mainActivity.usedArray.get(0);
    }

    /** The inverse, refusing the slot the thread has taken. */
    @Override
    public int pageForSubreddit(String sub) {
        final int position = super.pageForSubreddit(sub);
        if (commentPageShowing() && position == mainActivity.toOpenComments) {
            return -1; // that listing is the one the thread displaced
        }
        return position;
    }

    @Override
    public @Nullable Parcelable saveState() {
        return null;
    }

    @Override
    public void doSetPrimary(Object object, int position) {
        if (position != mainActivity.toOpenComments) {
            // By the adapter's numbering, not usedArray's. With hideSubredditTabs on an ordinary
            // subscription gets no page, so from the first one onwards the two disagree and
            // reading the array raw told the page to load a subreddit that has no tab -- the
            // same mismatch that made a tab come up blank on the plain adapter.
            final String primary = subredditForPage(position);
            if (mainActivity.multiNameToSubsMap.containsKey(primary)) {
                mainActivity.shouldLoad = mainActivity.multiNameToSubsMap.getOrDefault(primary, "");
            } else {
                mainActivity.shouldLoad = primary;
            }
            if (getCurrentFragment() != object) {
                mCurrentFragment = ((SubmissionsView) object);
                if (mCurrentFragment != null && mCurrentFragment.posts == null && mCurrentFragment.isAdded()) {
                    mCurrentFragment.doAdapter();
                }
            }
        } else if (object instanceof CommentPage) {
            mCurrentComments = (CommentPage) object;
        }
    }

    @Override public Fragment getCurrentFragment() {
        return mCurrentFragment;
    }

    /** The comment page, when one has been opened over the feed; null before that. */
    @Nullable
    public CommentPage currentComments() {
        return mCurrentComments;
    }

    @Override
    public int getItemPosition(@NonNull Object object) {
        if (object != storedFragment) return POSITION_NONE;
        return POSITION_UNCHANGED;
    }

    @Override
    public CharSequence getPageTitle(int position) {
        if (mainActivity.usedArray != null && position != mainActivity.toOpenComments) {
            if (hideSubredditTabs) {
                // Find the position-th special subreddit or multi-reddit
                int specialIndex = 0;
                for (String sub : mainActivity.usedArray) {
                    if (isSpecialOrMulti(sub)) {
                        if (specialIndex == position) {
                            // Display only the name part for tabs
                            return StringUtil.abbreviate(sub, 25);
                        }
                        specialIndex++;
                    }
                }
                // Fallback to the first subreddit if no special subreddit or multi-reddit was found at index position
                if (!mainActivity.usedArray.isEmpty()) {
                    // Display only the name part for tabs
                    return StringUtil.abbreviate(mainActivity.usedArray.get(0), 25);
                }
            } else {
                 // Display only the name part for tabs
                return StringUtil.abbreviate(mainActivity.usedArray.get(position), 25);
            }
        } else if (position == mainActivity.toOpenComments) {
            return "Comments";
        }
        return "";
    }
}
