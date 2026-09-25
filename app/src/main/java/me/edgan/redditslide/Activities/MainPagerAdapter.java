package me.edgan.redditslide.Activities;

import android.animation.ArgbEvaluator;
import android.animation.ValueAnimator;
import android.content.res.ColorStateList;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Parcelable;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.LinearInterpolator;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentStatePagerAdapter;
import androidx.viewpager.widget.ViewPager;
import com.google.android.material.shape.MaterialShapeDrawable;
import java.util.Locale;
import me.edgan.redditslide.Adapters.SubredditPosts;
import me.edgan.redditslide.Authentication;
import me.edgan.redditslide.Constants;
import me.edgan.redditslide.Fragments.SubmissionsView;
import me.edgan.redditslide.HibernateState;
import me.edgan.redditslide.Megareddits;
import me.edgan.redditslide.R;
import me.edgan.redditslide.Reddit;
import me.edgan.redditslide.SettingValues;
import me.edgan.redditslide.UserSubscriptions;
import me.edgan.redditslide.Visuals.ColorPreferences;
import me.edgan.redditslide.Visuals.Palette;
import me.edgan.redditslide.util.LogUtil;
import me.edgan.redditslide.util.StringUtil;
import org.jspecify.annotations.NullMarked;

@NullMarked
public class MainPagerAdapter extends FragmentStatePagerAdapter {
    @SuppressWarnings("NullAway.Init") // assigned in doSetPrimary
    protected SubmissionsView mCurrentFragment;
    private MainActivity mainActivity;

    // A snapshot of SettingValues.hideSubredditTabs, not a live read of it. The flag decides how
    // many tabs getCount() reports, and the settings screen flips it the instant the switch is
    // tapped -- while MainActivity is only PAUSED behind that screen's translucent swipeable
    // theme, so its ViewPager is still attached and still measuring. Reading the flag live let
    // the count change under a pager that had not been notified, and the next populate() (a
    // measure pass is enough) threw IllegalStateException before MainActivity.onResume could
    // call reloadSubs(). The new value is taken up in notifyDataSetChanged() below, which is
    // where ViewPager re-reads the count too, so the two can no longer disagree.
    protected boolean hideSubredditTabs = SettingValues.hideSubredditTabs;

    @Override
    public void notifyDataSetChanged() {
        hideSubredditTabs = SettingValues.hideSubredditTabs;
        super.notifyDataSetChanged();
    }

    static int resolveHeaderColor(Drawable bg, String fallbackSub) {
        if (bg instanceof ColorDrawable) {
            return ((ColorDrawable) bg).getColor();
        }
        if (bg instanceof MaterialShapeDrawable) {
            ColorStateList tint = ((MaterialShapeDrawable) bg).getFillColor();
            if (tint != null) return tint.getDefaultColor();
        }
        return Palette.getColor(fallbackSub);
    }

    // Helper method to check if a subreddit is special (frontpage, all) or a multi-reddit
    protected boolean isSpecialOrMulti(String subreddit) {
        String lowercase = subreddit.toLowerCase(Locale.ENGLISH);
        return UserSubscriptions.specialSubreddits.contains(lowercase)
                || lowercase.contains("/m/")
                || Megareddits.isKey(lowercase);
    }

    // Modified constructor to accept MainActivity
    public MainPagerAdapter(MainActivity mainActivity, FragmentManager fm) {
        super(fm, BEHAVIOR_RESUME_ONLY_CURRENT_FRAGMENT);
        this.mainActivity = mainActivity;

        mainActivity.pager.clearOnPageChangeListeners();
        mainActivity.pager.addOnPageChangeListener(
                new ViewPager.SimpleOnPageChangeListener() {
                    @Override
                    public void onPageScrolled(
                            int position, float positionOffset, int positionOffsetPixels) {
                        if (positionOffset == 0) {
                            mainActivity.header.animate()
                                    .translationY(0)
                                    .setInterpolator(new LinearInterpolator())
                                    .setDuration(180);
                            final String scrolledTo = subredditForPage(position);
                            if (!scrolledTo.isEmpty()) {
                                mainActivity.sidebarController.doSubSidebarNoLoad(scrolledTo);
                            }
                        }
                    }

                    @Override
                    public void onPageSelected(final int position) {
                        // By the adapter's numbering, not usedArray's: with hideSubredditTabs on
                        // an ordinary subscription has no page, so from the first one onwards a
                        // position names a different entry than the array does. Reading the array
                        // raw here pointed the whole screen -- title, colors, and the subreddit
                        // the page is told to load -- at a subreddit with no tab, which is why
                        // the last tab came up blank.
                        final String selected = subredditForPage(position);
                        if (selected.isEmpty()) return;

                        Reddit.currentPosition = position;
                        mainActivity.selectedSub = selected;
                        SubmissionsView page = (SubmissionsView) getCurrentFragment();

                        if (mainActivity.hea != null) {
                            mainActivity.hea.setBackgroundColor(Palette.getColor(mainActivity.selectedSub));
                            if (mainActivity.accountsArea != null) {
                                mainActivity.accountsArea.setBackgroundColor(
                                        Palette.getDarkerColor(mainActivity.selectedSub));
                            }
                        }

                        int colorFrom = resolveHeaderColor(
                                mainActivity.header.getBackground(), mainActivity.selectedSub);
                        int colorTo = Palette.getColor(mainActivity.selectedSub);

                        ValueAnimator colorAnimation =
                                ValueAnimator.ofObject(new ArgbEvaluator(), colorFrom, colorTo);

                        colorAnimation.addUpdateListener(
                                new ValueAnimator.AnimatorUpdateListener() {
                                    @Override
                                    public void onAnimationUpdate(ValueAnimator animator) {
                                        int color = (int) animator.getAnimatedValue();

                                        mainActivity.header.setBackgroundColor(color);

                                        if (Build.VERSION.SDK_INT
                                                >= Build.VERSION_CODES.LOLLIPOP) {
                                            // Route through themeSystemBars() so the system-bar
                                            // scrims update too. Under edge-to-edge enforcement
                                            // (API 35+) the visible status bar is the scrim and a
                                            // direct window.setStatusBarColor() is a no-op, which
                                            // left the bar stuck on the previous tab's color.
                                            // themeSystemBars() applies alwaysBlackStatusbar and
                                            // colorNavBar internally.
                                            mainActivity.themeSystemBars(
                                                    Palette.getDarkerColor(color));
                                        }
                                    }
                                });
                        colorAnimation.setInterpolator(new AccelerateDecelerateInterpolator());
                        colorAnimation.setDuration(200);
                        colorAnimation.start();

                        mainActivity.setRecentBar(mainActivity.selectedSub);

                        if (SettingValues.single || mainActivity.mTabLayout == null) {
                            // Smooth out the fading animation for the toolbar subreddit search UI
                            if ((SettingValues.subredditSearchMethod
                                                    == Constants.SUBREDDIT_SEARCH_METHOD_TOOLBAR
                                            || SettingValues.subredditSearchMethod
                                                    == Constants.SUBREDDIT_SEARCH_METHOD_BOTH)
                                    && mainActivity.requireViewById(R.id.toolbar_search).getVisibility()
                                            == View.VISIBLE) {
                                new Handler()
                                        .postDelayed(
                                                new Runnable() {
                                                    @Override
                                                    public void run() {
                                                        java.util.Objects.requireNonNull(
                                                                        mainActivity
                                                                                .getSupportActionBar())
                                                                .setTitle(mainActivity.selectedSub);
                                                    }
                                                },
                                                mainActivity.ANIMATE_DURATION + mainActivity.ANIMATE_DURATION_OFFSET);
                            } else {
                                java.util.Objects.requireNonNull(mainActivity.getSupportActionBar()).setTitle(mainActivity.selectedSub);
                            }
                        } else {
                            mainActivity.mTabLayout.setSelectedTabIndicatorColor(
                                    new ColorPreferences(mainActivity)
                                            .getColor(mainActivity.selectedSub));
                        }
                        if (page != null && page.adapter != null) {
                            SubredditPosts p = page.adapter.dataSet;
                            if (p.offline && !p.restoredFromCache) {
                                p.doMainActivityOffline(mainActivity, p.displayer);
                            }
                        }
                        // Moving to another tab is neither a resume nor a scroll, so nothing
                        // else records it: killed here with no pause, the app came back on
                        // the tab the user had left. Posted so the page has laid out -- a
                        // page that has not loaded yet has no position to give and keeps the
                        // last snapshot, and records itself once its listing arrives.
                        mainActivity.pager.post(
                                new Runnable() {
                                    @Override
                                    public void run() {
                                        HibernateState.onContentSettled(mainActivity);
                                    }
                                });
                    }
                });

        if (mainActivity.pager.getAdapter() != null) {
            mainActivity.pager.getAdapter().notifyDataSetChanged();
            mainActivity.pager.setCurrentItem(1);
            mainActivity.pager.setCurrentItem(0);
        }
    }

    @Override
    public int getCount() {
        if (mainActivity.usedArray == null) {
            return 1;
        } else {
            if (hideSubredditTabs) {
                // Count special subreddits like frontpage, all, etc. and multi-reddits
                int count = 0;
                for (String sub : mainActivity.usedArray) {
                    if (isSpecialOrMulti(sub)) {
                        count++;
                    }
                }
                return count > 0 ? count : 1; // Always show at least one tab
            } else {
                return mainActivity.usedArray.size();
            }
        }
    }

    @NonNull
    @Override
    public Fragment getItem(int i) {
        SubmissionsView f = new SubmissionsView();
        Bundle args = new Bundle();
        String name = ""; // Initialize with default empty string

        if (hideSubredditTabs) {
            int specialIndex = 0;
            boolean found = false;

            for (String sub : mainActivity.usedArray) {
                if (isSpecialOrMulti(sub)) {
                    if (specialIndex == i) {
                        // Ensure full path for multi-reddits even when hidden
                        if (sub.startsWith("/m/")) {
                            if (mainActivity.multiNameToSubsMap.containsKey(sub)) {
                                name = mainActivity.multiNameToSubsMap.get(sub);
                            } else {
                                // Construct full path if map lookup fails
                                name = "api/user/" + Authentication.name + sub; // sub already starts with /m/
                            }
                        } else {
                            name = sub; // Standard special subreddits (frontpage, all)
                        }
                        found = true;
                        break;
                    }
                    specialIndex++;
                }
            }

            // Fallback to the first subreddit if no special subreddit or multi-reddit was found
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
        } else {
            if (mainActivity.usedArray.size() > i) {
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
        }

        args.putString("id", name);
        mainActivity.applyRestoreArgs(name, args);
        f.setArguments(args);

        return f;
    }

    @Override
    public void setPrimaryItem(
            @NonNull ViewGroup container, int position, @NonNull Object object) {
        // Ensure position is valid before accessing usedArray
        if (position >= 0 && position < mainActivity.usedArray.size()) {
            if (mainActivity.reloadItemNumber == position || mainActivity.reloadItemNumber < 0) {
                super.setPrimaryItem(container, position, object);
                // Check size again before calling doSetPrimary
                if (position < mainActivity.usedArray.size()) {
                    doSetPrimary(object, position);
                }
            } else {
                // Ensure reloadItemNumber is valid
                if (mainActivity.reloadItemNumber >= 0 && mainActivity.reloadItemNumber < mainActivity.usedArray.size()) {
                    if (mainActivity.multiNameToSubsMap.containsKey(mainActivity.usedArray.get(mainActivity.reloadItemNumber))) {
                        mainActivity.shouldLoad = mainActivity.multiNameToSubsMap.getOrDefault(mainActivity.usedArray.get(mainActivity.reloadItemNumber), "");
                    } else {
                        mainActivity.shouldLoad = mainActivity.usedArray.get(mainActivity.reloadItemNumber);
                    }
                } else {
                    mainActivity.shouldLoad = "frontpage";
                }
            }
        } else {
             // Handle invalid position, maybe log an error or do nothing
            Log.e(LogUtil.getTag(), "Invalid position in setPrimaryItem: " + position);
        }
    }


    @Override
    public @Nullable Parcelable saveState() {
        return null;
    }

    public void doSetPrimary(Object object, int position) {
         // Add null check for usedArray and bounds check for position
        if (mainActivity.usedArray == null || position < 0 || position >= mainActivity.usedArray.size()) {
            Log.e(LogUtil.getTag(), "Invalid state in doSetPrimary: usedArray=" + mainActivity.usedArray + ", position=" + position);
            return;
        }

        if (object != null && getCurrentFragment() != object && position != mainActivity.toOpenComments && object instanceof SubmissionsView) {
            final String primary = subredditForPage(position);

            if (mainActivity.multiNameToSubsMap.containsKey(primary)) {
                mainActivity.shouldLoad = mainActivity.multiNameToSubsMap.getOrDefault(primary, "");
            } else {
                mainActivity.shouldLoad = primary;
            }

            mCurrentFragment = ((SubmissionsView) object);

            if (mCurrentFragment.posts == null && mCurrentFragment.isAdded()) {
                mCurrentFragment.doAdapter();
            }
        }
    }

    // Public getter for mCurrentFragment
    public Fragment getCurrentFragment() {
        return mCurrentFragment;
    }

    /**
     * The subreddit a pager position is showing, or {@code ""} when there is none.
     *
     * <p>A pager position is not an index into {@code usedArray}. With {@code hideSubredditTabs}
     * on, only the special subreddits and multireddits get a page, so an ordinary subscription
     * sitting between two of them makes the two numbering schemes diverge from that point on --
     * with {@code [/mega/cuteanimals, randnsfw, frontpage, all, test, random]} the RANDOM tab is
     * position 4 while {@code usedArray.get(4)} is {@code test}. Reading the array by position
     * therefore names a subreddit that has no tab at all, which is how a resume came back on the
     * right tab with an empty listing.
     *
     * <p>Mirrors {@link #getItem} and {@link #getPageTitle}, which is what actually decides which
     * subreddit a page loads; this is the same walk without building anything.
     */
    public String subredditForPage(int position) {
        if (mainActivity.usedArray == null || position < 0) {
            return "";
        }
        if (!hideSubredditTabs) {
            return position < mainActivity.usedArray.size()
                    ? mainActivity.usedArray.get(position)
                    : "";
        }
        int specialIndex = 0;
        for (String sub : mainActivity.usedArray) {
            if (isSpecialOrMulti(sub)) {
                if (specialIndex == position) {
                    return sub;
                }
                specialIndex++;
            }
        }
        // getItem falls back to the first subscription when there is no special or multireddit to
        // show, and getCount still reports one page ("Always show at least one tab"), so position
        // 0 is a real page displaying a real subreddit. Answering "" for it told the page to load
        // nothing and left onPageSelected returning before it set the title, colors or sidebar.
        return position == 0 && !mainActivity.usedArray.isEmpty()
                ? mainActivity.usedArray.get(0)
                : "";
    }

    /** The pager position showing a subreddit, or -1. The inverse of {@link #subredditForPage}. */
    public int pageForSubreddit(String sub) {
        if (mainActivity.usedArray == null) {
            return -1;
        }
        if (!hideSubredditTabs) {
            for (int i = 0; i < mainActivity.usedArray.size(); i++) {
                if (sub.equalsIgnoreCase(mainActivity.usedArray.get(i))) {
                    return i;
                }
            }
            return -1;
        }
        int specialIndex = 0;
        for (String candidate : mainActivity.usedArray) {
            if (isSpecialOrMulti(candidate)) {
                if (sub.equalsIgnoreCase(candidate)) {
                    return specialIndex;
                }
                specialIndex++;
            }
        }
        // The same fallback page getItem builds when nothing qualifies for a tab.
        if (specialIndex == 0
                && !mainActivity.usedArray.isEmpty()
                && sub.equalsIgnoreCase(mainActivity.usedArray.get(0))) {
            return 0;
        }
        return -1;
    }

    @Override
    public CharSequence getPageTitle(int position) {
        if (mainActivity.usedArray != null) {
            if (hideSubredditTabs) {
                // Find the position-th special subreddit or multi-reddit
                int specialIndex = 0;
                for (String sub : mainActivity.usedArray) {
                    if (isSpecialOrMulti(sub)) {
                        if (specialIndex == position) {
                            // Display only the name part for tabs, e.g., "/m/tech" or "frontpage"
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
        }
        return "";
    }
}
