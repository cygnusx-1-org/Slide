package me.edgan.redditslide.Activities;

import android.os.Bundle;
import android.os.Parcelable;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.viewpager.widget.ViewPager;

import java.util.Locale;

import me.edgan.redditslide.Authentication;
import me.edgan.redditslide.Adapters.SubredditPosts;
import me.edgan.redditslide.Fragments.CommentPage;
import me.edgan.redditslide.Fragments.SubmissionsView;
import me.edgan.redditslide.SettingValues;
import me.edgan.redditslide.Visuals.Palette;
import me.edgan.redditslide.util.StringUtil;


public class MainPagerAdapterComment extends MainPagerAdapter {
    public int size;
    public Fragment storedFragment;
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
                                mainActivity.header.setBackgroundColor(
                                        Palette.getColor(mainActivity.usedArray.get(position)));
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
                                mainActivity.themeSystemBars(mainActivity.openingComments.getSubredditName().toLowerCase(Locale.ENGLISH));
                                mainActivity.setRecentBar(mainActivity.openingComments.getSubredditName().toLowerCase(Locale.ENGLISH));
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
                                if (p.offline && !mainActivity.isRestart) {
                                    p.doMainActivityOffline(mainActivity, p.displayer);
                                }
                            }
                        } else {
                            SubmissionsView page =
                                    (SubmissionsView) mainActivity.adapter.getCurrentFragment();
                            if (page != null && page.adapter != null) {
                                SubredditPosts p = page.adapter.dataSet;
                                if (p.offline && !mainActivity.isRestart) {
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
            if (SettingValues.hideSubredditTabs) {
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

            if (SettingValues.hideSubredditTabs) {
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
            }
            f.setArguments(args);
            return f;
        } else {
            Fragment f = new CommentPage();
            Bundle args = new Bundle();
            String submissionFullName = mainActivity.openingComments.getFullName();
            args.putString("id", submissionFullName.substring(3));
            args.putBoolean("archived", mainActivity.openingComments.isArchived());
            args.putBoolean(
                    "contest", mainActivity.openingComments.getDataNode().get("contest_mode").asBoolean());
            args.putBoolean("locked", mainActivity.openingComments.isLocked());
            args.putInt("page", mainActivity.currentComment);
            args.putString("subreddit", mainActivity.openingComments.getSubredditName());
            args.putString("baseSubreddit", mainActivity.subToDo);
            f.setArguments(args);
            return f;
        }
    }

    @Override
    public Parcelable saveState() {
        return null;
    }

    @Override
    public void doSetPrimary(Object object, int position) {
        if (position != mainActivity.toOpenComments) {
            if (mainActivity.multiNameToSubsMap.containsKey(mainActivity.usedArray.get(position))) {
                mainActivity.shouldLoad = mainActivity.multiNameToSubsMap.get(mainActivity.usedArray.get(position));
            } else {
                mainActivity.shouldLoad = mainActivity.usedArray.get(position);
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

    public Fragment getCurrentFragment() {
        return mCurrentFragment;
    }

    @Override
    public int getItemPosition(@NonNull Object object) {
        if (object != storedFragment) return POSITION_NONE;
        return POSITION_UNCHANGED;
    }

    @Override
    public CharSequence getPageTitle(int position) {
        if (mainActivity.usedArray != null && position != mainActivity.toOpenComments) {
            if (SettingValues.hideSubredditTabs) {
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
