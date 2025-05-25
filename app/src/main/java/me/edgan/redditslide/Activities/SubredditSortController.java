package me.edgan.redditslide.Activities;

import android.content.DialogInterface;
import android.text.Spannable;
import android.view.Gravity;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.PopupMenu;

import me.edgan.redditslide.Fragments.SubmissionsView;
import me.edgan.redditslide.R;
import me.edgan.redditslide.SettingValues;
import me.edgan.redditslide.util.LogUtil;
import me.edgan.redditslide.util.SortingUtil;
import net.dean.jraw.paginators.Sorting;
import net.dean.jraw.paginators.TimePeriod;

/**
 * Handles the sorting selection logic for subreddits within MainActivity.
 */
public class SubredditSortController {

    private final MainActivity activity;

    Sorting sorts;
    TimePeriod time = TimePeriod.DAY;


    public SubredditSortController(MainActivity activity) {
        this.activity = activity;
    }

    public void openPopup() {
        PopupMenu popup =
                new PopupMenu(activity, activity.findViewById(R.id.anchor), Gravity.RIGHT);
        String id =
                ((SubmissionsView) (((MainPagerAdapter) activity.pager.getAdapter()).getCurrentFragment()))
                        .id;

        final Spannable[] base = SortingUtil.getSortingSpannables(id);
        for (Spannable s : base) {
            // Do not add option for "Best" in any subreddit except for the frontpage.
            if (!id.equals("frontpage") && s.toString().equals(activity.getString(R.string.sorting_best))) {
                continue;
            }
            MenuItem m = popup.getMenu().add(s);
        }
        popup.setOnMenuItemClickListener(
                new PopupMenu.OnMenuItemClickListener() {
                    public boolean onMenuItemClick(MenuItem item) {
                        int i = 0;
                        for (Spannable s : base) {
                            if (s.equals(item.getTitle())) {
                                break;
                            }
                            i++;
                        }

                        LogUtil.v("Chosen is " + i);
                        switch (i) {
                            case 0:
                                if (id.equals("frontpage")) {
                                    SortingUtil.frontpageSorting = Sorting.HOT;
                                } else {
                                    SortingUtil.setSorting(id, Sorting.HOT);
                                }
                                activity.reloadSubs();
                                break;
                            case 1:
                                if (id.equals("frontpage")) {
                                    SortingUtil.frontpageSorting = Sorting.NEW;
                                } else {
                                    SortingUtil.setSorting(id, Sorting.NEW);
                                }
                                activity.reloadSubs();
                                break;
                            case 2:
                                if (id.equals("frontpage")) {
                                    SortingUtil.frontpageSorting = Sorting.RISING;
                                } else {
                                    SortingUtil.setSorting(id, Sorting.RISING);
                                }
                                activity.reloadSubs();
                                break;
                            case 3:
                                if (id.equals("frontpage")) {
                                    SortingUtil.frontpageSorting = Sorting.TOP;
                                } else {
                                    SortingUtil.setSorting(id, Sorting.TOP);
                                }
                                openPopupTime();
                                break;
                            case 4:
                                if (id.equals("frontpage")) {
                                    SortingUtil.frontpageSorting = Sorting.CONTROVERSIAL;
                                } else {
                                    SortingUtil.setSorting(id, Sorting.CONTROVERSIAL);
                                }
                                openPopupTime();
                                break;
                            case 5:
                                if (id.equals("frontpage")) {
                                    SortingUtil.frontpageSorting = Sorting.BEST;
                                } else {
                                    SortingUtil.setSorting(id, Sorting.BEST);
                                }
                                activity.reloadSubs();
                                break;
                        }
                        return true;
                    }
                });
        popup.show();
    }

    public void openPopupTime() {
        PopupMenu popup =
                new PopupMenu(activity, activity.findViewById(R.id.anchor), Gravity.RIGHT);
        String id =
                ((SubmissionsView) (((MainPagerAdapter) activity.pager.getAdapter()).getCurrentFragment()))
                        .id;
        final Spannable[] base = SortingUtil.getSortingTimesSpannables(id);
        for (Spannable s : base) {
            MenuItem m = popup.getMenu().add(s);
        }
        popup.setOnMenuItemClickListener(
                new PopupMenu.OnMenuItemClickListener() {
                    public boolean onMenuItemClick(MenuItem item) {
                        LogUtil.v("Chosen is " + item.getOrder());
                        int i = 0;
                        for (Spannable s : base) {
                            if (s.equals(item.getTitle())) {
                                break;
                            }
                            i++;
                        }
                        switch (i) {
                            case 0:
                                SortingUtil.setTime(
                                        ((SubmissionsView)
                                                        (((MainPagerAdapter) activity.pager.getAdapter())
                                                                .getCurrentFragment()))
                                                .id,
                                        TimePeriod.HOUR);
                                activity.reloadSubs();
                                break;
                            case 1:
                                SortingUtil.setTime(
                                        ((SubmissionsView)
                                                        (((MainPagerAdapter) activity.pager.getAdapter())
                                                                .getCurrentFragment()))
                                                .id,
                                        TimePeriod.DAY);
                                activity.reloadSubs();
                                break;
                            case 2:
                                SortingUtil.setTime(
                                        ((SubmissionsView)
                                                        (((MainPagerAdapter) activity.pager.getAdapter())
                                                                .getCurrentFragment()))
                                                .id,
                                        TimePeriod.WEEK);
                                activity.reloadSubs();
                                break;
                            case 3:
                                SortingUtil.setTime(
                                        ((SubmissionsView)
                                                        (((MainPagerAdapter) activity.pager.getAdapter())
                                                                .getCurrentFragment()))
                                                .id,
                                        TimePeriod.MONTH);
                                activity.reloadSubs();
                                break;
                            case 4:
                                SortingUtil.setTime(
                                        ((SubmissionsView)
                                                        (((MainPagerAdapter) activity.pager.getAdapter())
                                                                .getCurrentFragment()))
                                                .id,
                                        TimePeriod.YEAR);
                                activity.reloadSubs();
                                break;
                            case 5:
                                SortingUtil.setTime(
                                        ((SubmissionsView)
                                                        (((MainPagerAdapter) activity.pager.getAdapter())
                                                                .getCurrentFragment()))
                                                .id,
                                        TimePeriod.ALL);
                                activity.reloadSubs();
                                break;
                        }
                        return true;
                    }
                });
        popup.show();
    }

    public void askTimePeriod(final Sorting sort, final String sub, final View dialoglayout) {
        final DialogInterface.OnClickListener l2 =
                new DialogInterface.OnClickListener() {

                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        switch (i) {
                            case 0:
                                time = TimePeriod.HOUR;
                                break;
                            case 1:
                                time = TimePeriod.DAY;
                                break;
                            case 2:
                                time = TimePeriod.WEEK;
                                break;
                            case 3:
                                time = TimePeriod.MONTH;
                                break;
                            case 4:
                                time = TimePeriod.YEAR;
                                break;
                            case 5:
                                time = TimePeriod.ALL;
                                break;
                        }
                        SettingValues.setSubSorting(sort, time, sub);
                        SortingUtil.setSorting(sub, sort);
                        SortingUtil.setTime(sub, time);
                        final TextView sortTextView = dialoglayout.findViewById(R.id.sort);
                        if (SettingValues.hasSort(sub)) {
                            Sorting sortingis = SettingValues.getBaseSubmissionSort(sub);
                            sortTextView.setText(
                                    sortingis.name()
                                            + ((sortingis == Sorting.CONTROVERSIAL
                                                            || sortingis == Sorting.TOP)
                                                    ? " of "
                                                            + SettingValues.getBaseTimePeriod(sub)
                                                                    .name()
                                                    : ""));
                        } else {
                            sortTextView.setText("Set default sorting");
                        }
                        activity.reloadSubs();
                    }
                };
        new AlertDialog.Builder(activity) // Use activity context
                .setTitle(R.string.sorting_choose)
                .setSingleChoiceItems(
                        SortingUtil.getSortingTimesStrings(), SortingUtil.getSortingTimeId(""), l2)
                .show();
    }
}