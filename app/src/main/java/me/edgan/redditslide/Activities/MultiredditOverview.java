package me.edgan.redditslide.Activities;

import android.app.Activity;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.text.Spannable;
import android.util.Log;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.animation.LinearInterpolator;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.PopupMenu;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentStatePagerAdapter;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.viewpager.widget.ViewPager;

import com.afollestad.materialdialogs.DialogAction;
import com.afollestad.materialdialogs.MaterialDialog;
import com.google.android.material.tabs.TabLayout;

import me.edgan.redditslide.Authentication;
import me.edgan.redditslide.CaseInsensitiveArrayList;
import me.edgan.redditslide.Fragments.MultiredditView;
import me.edgan.redditslide.R;
import me.edgan.redditslide.SettingValues;
import me.edgan.redditslide.UserSubscriptions;
import me.edgan.redditslide.Views.CatchStaggeredGridLayoutManager;
import me.edgan.redditslide.Views.PreCachingLayoutManager;
import me.edgan.redditslide.Visuals.ColorPreferences;
import me.edgan.redditslide.Visuals.Palette;
import me.edgan.redditslide.util.BlendModeUtil;
import me.edgan.redditslide.util.LogUtil;
import me.edgan.redditslide.util.MiscUtil;
import me.edgan.redditslide.util.SortingUtil;

import net.dean.jraw.models.MultiReddit;
import net.dean.jraw.models.MultiSubreddit;
import net.dean.jraw.models.Submission;
import net.dean.jraw.paginators.Sorting;
import net.dean.jraw.paginators.TimePeriod;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Created by ccrama on 9/17/2015. */
public class MultiredditOverview extends BaseActivityAnim {

    public static final String EXTRA_PROFILE = "profile";
    public static final String EXTRA_MULTI = "multi";

    public static Activity multiActivity;

    public static MultiReddit searchMulti;
    public MultiredditOverviewPagerAdapter adapter;
    private ViewPager pager;
    private String profile;
    private TabLayout tabs;
    private List<MultiReddit> usedArray;
    private String initialMulti;

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.menu_multireddits, menu);

        if (!profile.isEmpty()) {
            menu.findItem(R.id.action_edit).setVisible(false);
            menu.findItem(R.id.create).setVisible(false);
        }

        //   if (mShowInfoButton) menu.findItem(R.id.action_info).setVisible(true);
        //   else menu.findItem(R.id.action_info).setVisible(false);

        return true;
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        /* removed for now
        int keyCode = event.getKeyCode();
        switch (keyCode) {
            case KeyEvent.KEYCODE_VOLUME_UP:
                return ((MultiredditView) adapter.getCurrentFragment()).onKeyDown(keyCode);
            case KeyEvent.KEYCODE_VOLUME_DOWN:
                return ((MultiredditView) adapter.getCurrentFragment()).onKeyDown(keyCode);
            default:
                return super.dispatchKeyEvent(event);
        }*/
        return super.dispatchKeyEvent(event);
    }

    public int getCurrentPage() {
        int position = 0;
        int currentOrientation = getResources().getConfiguration().orientation;
        if (((MultiredditView) adapter.getCurrentFragment()).rv.getLayoutManager()
                        instanceof LinearLayoutManager
                && currentOrientation == Configuration.ORIENTATION_LANDSCAPE) {
            position =
                    ((LinearLayoutManager)
                                            ((MultiredditView) adapter.getCurrentFragment())
                                                    .rv.getLayoutManager())
                                    .findFirstVisibleItemPosition()
                            - 1;
        } else if (((MultiredditView) adapter.getCurrentFragment()).rv.getLayoutManager()
                instanceof CatchStaggeredGridLayoutManager) {
            int[] firstVisibleItems = null;
            firstVisibleItems =
                    ((CatchStaggeredGridLayoutManager)
                                    ((MultiredditView) adapter.getCurrentFragment())
                                            .rv.getLayoutManager())
                            .findFirstVisibleItemPositions(firstVisibleItems);
            if (firstVisibleItems != null && firstVisibleItems.length > 0) {
                position = firstVisibleItems[0] - 1;
            }
        } else {
            position =
                    ((PreCachingLayoutManager)
                                            ((MultiredditView) adapter.getCurrentFragment())
                                                    .rv.getLayoutManager())
                                    .findFirstVisibleItemPosition()
                            - 1;
        }
        return position;
    }

    String term;

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item == null) return false;
        MultiredditView currentFragment = null;
        List<Submission> posts = null;

        // Safely get current fragment and its posts
        if (adapter != null && adapter.getCurrentFragment() instanceof MultiredditView) {
            currentFragment = (MultiredditView) adapter.getCurrentFragment();
            if (currentFragment != null && currentFragment.posts != null) {
                posts = currentFragment.posts.posts;
            }
        }

        switch (item.getItemId()) {
            case android.R.id.home:
                try {
                    onBackPressed();
                } catch (Exception ignored) {

                }
                return true;
            case R.id.action_edit:
                {
                    if (profile.isEmpty()
                            && (UserSubscriptions.multireddits != null)
                            && !UserSubscriptions.multireddits.isEmpty()) {
                        Intent i = new Intent(MultiredditOverview.this, CreateMulti.class);
                        i.putExtra(
                                CreateMulti.EXTRA_MULTI,
                                UserSubscriptions.multireddits
                                        .get(pager.getCurrentItem())
                                        .getDisplayName());
                        startActivity(i);
                    }
                }
                return true;
            case R.id.search:
                {
                    UserSubscriptions.MultiCallback m =
                            new UserSubscriptions.MultiCallback() {
                                @Override
                                public void onComplete(List<MultiReddit> multireddits) {
                                    if ((multireddits != null) && !multireddits.isEmpty()) {
                                        searchMulti = multireddits.get(pager.getCurrentItem());
                                        MaterialDialog.Builder builder =
                                                new MaterialDialog.Builder(MultiredditOverview.this)
                                                        .title(R.string.search_title)
                                                        .alwaysCallInputCallback()
                                                        .input(
                                                                getString(R.string.search_msg),
                                                                "",
                                                                new MaterialDialog.InputCallback() {
                                                                    @Override
                                                                    public void onInput(
                                                                            MaterialDialog
                                                                                    materialDialog,
                                                                            CharSequence
                                                                                    charSequence) {
                                                                        term =
                                                                                charSequence
                                                                                        .toString();
                                                                    }
                                                                });

                                        // Add "search current sub" if it is not
                                        // frontpage/all/random
                                        builder.positiveText(
                                                        getString(
                                                                R.string.search_subreddit,
                                                                "/m/"
                                                                        + searchMulti
                                                                                .getDisplayName()))
                                                .onPositive(
                                                        new MaterialDialog.SingleButtonCallback() {
                                                            @Override
                                                            public void onClick(
                                                                    @NonNull
                                                                            MaterialDialog
                                                                                    materialDialog,
                                                                    @NonNull
                                                                            DialogAction
                                                                                    dialogAction) {
                                                                Intent i =
                                                                        new Intent(
                                                                                MultiredditOverview
                                                                                        .this,
                                                                                Search.class);
                                                                i.putExtra(Search.EXTRA_TERM, term);
                                                                i.putExtra(
                                                                        Search.EXTRA_MULTIREDDIT,
                                                                        searchMulti
                                                                                .getDisplayName());
                                                                startActivity(i);
                                                            }
                                                        });

                                        builder.show();
                                    }
                                }
                            };

                    if (profile.isEmpty()) {
                        UserSubscriptions.getMultireddits(m);
                    } else {
                        UserSubscriptions.getPublicMultireddits(m, profile);
                    }
                }
                return true;
            case R.id.create:
                if (profile.isEmpty()) {
                    Intent i2 = new Intent(MultiredditOverview.this, CreateMulti.class);
                    startActivity(i2);
                }
                return true;
            case R.id.action_sort:
                openPopup();
                return true;

            case R.id.subs:
                ((DrawerLayout) findViewById(R.id.drawer_layout)).openDrawer(Gravity.RIGHT);
                return true;
            case R.id.gallery:
                if (currentFragment != null && posts != null && !posts.isEmpty()) {
                    Intent i2 = new Intent(this, Gallery.class);
                    i2.putExtra(Gallery.EXTRA_PROFILE, profile);
                    i2.putExtra(
                            Gallery.EXTRA_MULTIREDDIT,
                            currentFragment.posts.multiReddit.getDisplayName());
                    startActivity(i2);
                }
                return true;
            case R.id.action_shadowbox:
                if (currentFragment != null && posts != null && !posts.isEmpty()) {
                    Intent i = new Intent(this, Shadowbox.class);
                    i.putExtra(Shadowbox.EXTRA_PAGE, getCurrentPage());
                    i.putExtra(Shadowbox.EXTRA_PROFILE, profile);
                    i.putExtra(
                            Shadowbox.EXTRA_MULTIREDDIT,
                            currentFragment.posts.multiReddit.getDisplayName());
                    startActivity(i);
                }
                return true;
            default:
                return false;
        }
    }

    private void buildDialog() {
        buildDialog(false);
    }

    private void buildDialog(boolean wasException) {
        try {
            final AlertDialog.Builder b =
                    new AlertDialog.Builder(MultiredditOverview.this)
                            .setCancelable(false)
                            .setOnDismissListener(dialog -> finish());
            if (wasException) {
                b.setTitle(R.string.err_title)
                        .setMessage(R.string.err_loading_content)
                        .setPositiveButton(R.string.btn_ok, (dialog, which) -> finish());
            } else if (profile.isEmpty()) {
                b.setTitle(R.string.multireddit_err_title)
                        .setMessage(R.string.multireddit_err_msg)
                        .setPositiveButton(
                                R.string.btn_yes,
                                (dialog, which) -> {
                                    Intent i =
                                            new Intent(MultiredditOverview.this, CreateMulti.class);
                                    startActivity(i);
                                })
                        .setNegativeButton(R.string.btn_no, (dialog, which) -> finish());
            } else {
                b.setTitle(R.string.public_multireddit_err_title)
                        .setMessage(R.string.public_multireddit_err_msg)
                        .setNegativeButton(R.string.btn_go_back, (dialog, which) -> finish());
            }
            b.show();
        } catch (Exception e) {

        }
    }

    @Override
    public void onCreate(Bundle savedInstance) {
        overrideSwipeFromAnywhere();
        multiActivity = this;
        super.onCreate(savedInstance);

        applyColorTheme("");
        setContentView(R.layout.activity_multireddits);
        MiscUtil.setupOldSwipeModeBackground(this, getWindow().getDecorView());

        setupAppBar(R.id.toolbar, R.string.title_multireddits, true, false);

        findViewById(R.id.header).setBackgroundColor(Palette.getDefaultColor());
        tabs = (TabLayout) findViewById(R.id.sliding_tabs);
        tabs.setTabMode(TabLayout.MODE_SCROLLABLE);

        pager = (ViewPager) findViewById(R.id.content_view);
        mToolbar.setPopupTheme(new ColorPreferences(this).getFontStyle().getBaseId());

        profile = "";
        initialMulti = "";
        if (getIntent().getExtras() != null) {
            profile = getIntent().getExtras().getString(EXTRA_PROFILE, "");
            initialMulti = getIntent().getExtras().getString(EXTRA_MULTI, "");
        }
        if (profile.equalsIgnoreCase(Authentication.name)) {
            profile = "";
        }

        UserSubscriptions.MultiCallback callback =
                new UserSubscriptions.MultiCallback() {
                    @Override
                    public void onComplete(List<MultiReddit> multiReddits) {
                        if (multiReddits != null && !multiReddits.isEmpty()) {
                            setDataSet(multiReddits);
                        } else {
                            buildDialog();
                        }
                    }
                };

        if (profile.isEmpty()) {
            UserSubscriptions.getMultireddits(callback);
        } else {
            UserSubscriptions.getPublicMultireddits(callback, profile);
        }
    }

    public void openPopup() {
        PopupMenu popup =
                new PopupMenu(MultiredditOverview.this, findViewById(R.id.anchor), Gravity.RIGHT);
        String id =
                ((MultiredditView)
                                (((MultiredditOverviewPagerAdapter) pager.getAdapter())
                                        .getCurrentFragment()))
                        .posts
                        .multiReddit
                        .getDisplayName()
                        .toLowerCase(Locale.ENGLISH);
        final Spannable[] base = SortingUtil.getSortingSpannables("multi" + id);
        for (Spannable s : base) {
            // Do not add option for "Best" in any subreddit except for the frontpage.
            if (s.toString().equals(getString(R.string.sorting_best))) {
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
                        if (pager.getAdapter() != null) {
                            switch (i) {
                                case 0:
                                    SortingUtil.setSorting(
                                            "multi"
                                                    + ((MultiredditView)
                                                                    (((MultiredditOverviewPagerAdapter)
                                                                                    pager
                                                                                            .getAdapter())
                                                                            .getCurrentFragment()))
                                                            .posts
                                                            .multiReddit
                                                            .getDisplayName()
                                                            .toLowerCase(Locale.ENGLISH),
                                            Sorting.HOT);
                                    reloadSubs();
                                    break;
                                case 1:
                                    SortingUtil.setSorting(
                                            "multi"
                                                    + ((MultiredditView)
                                                                    (((MultiredditOverviewPagerAdapter)
                                                                                    pager
                                                                                            .getAdapter())
                                                                            .getCurrentFragment()))
                                                            .posts
                                                            .multiReddit
                                                            .getDisplayName()
                                                            .toLowerCase(Locale.ENGLISH),
                                            Sorting.NEW);
                                    reloadSubs();
                                    break;
                                case 2:
                                    SortingUtil.setSorting(
                                            "multi"
                                                    + ((MultiredditView)
                                                                    (((MultiredditOverviewPagerAdapter)
                                                                                    pager
                                                                                            .getAdapter())
                                                                            .getCurrentFragment()))
                                                            .posts
                                                            .multiReddit
                                                            .getDisplayName()
                                                            .toLowerCase(Locale.ENGLISH),
                                            Sorting.RISING);
                                    reloadSubs();
                                    break;
                                case 3:
                                    SortingUtil.setSorting(
                                            "multi"
                                                    + ((MultiredditView)
                                                                    (((MultiredditOverviewPagerAdapter)
                                                                                    pager
                                                                                            .getAdapter())
                                                                            .getCurrentFragment()))
                                                            .posts
                                                            .multiReddit
                                                            .getDisplayName()
                                                            .toLowerCase(Locale.ENGLISH),
                                            Sorting.TOP);
                                    openPopupTime();
                                    break;
                                case 4:
                                    SortingUtil.setSorting(
                                            "multi"
                                                    + ((MultiredditView)
                                                                    (((MultiredditOverviewPagerAdapter)
                                                                                    pager
                                                                                            .getAdapter())
                                                                            .getCurrentFragment()))
                                                            .posts
                                                            .multiReddit
                                                            .getDisplayName()
                                                            .toLowerCase(Locale.ENGLISH),
                                            Sorting.CONTROVERSIAL);
                                    openPopupTime();
                                    break;
                            }
                        }
                        return true;
                    }
                });
        popup.show();
    }

    public void openPopupTime() {
        PopupMenu popup =
                new PopupMenu(MultiredditOverview.this, findViewById(R.id.anchor), Gravity.RIGHT);
        String id =
                ((MultiredditView)
                                (((MultiredditOverviewPagerAdapter) pager.getAdapter())
                                        .getCurrentFragment()))
                        .posts
                        .multiReddit
                        .getDisplayName()
                        .toLowerCase(Locale.ENGLISH);
        final Spannable[] base = SortingUtil.getSortingTimesSpannables("multi" + id);
        for (Spannable s : base) {
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
                        if (pager.getAdapter() != null) {
                            switch (i) {
                                case 0:
                                    SortingUtil.setTime(
                                            "multi"
                                                    + ((MultiredditView)
                                                                    (((MultiredditOverviewPagerAdapter)
                                                                                    pager
                                                                                            .getAdapter())
                                                                            .getCurrentFragment()))
                                                            .posts
                                                            .multiReddit
                                                            .getDisplayName()
                                                            .toLowerCase(Locale.ENGLISH),
                                            TimePeriod.HOUR);
                                    reloadSubs();
                                    break;
                                case 1:
                                    SortingUtil.setTime(
                                            "multi"
                                                    + ((MultiredditView)
                                                                    (((MultiredditOverviewPagerAdapter)
                                                                                    pager
                                                                                            .getAdapter())
                                                                            .getCurrentFragment()))
                                                            .posts
                                                            .multiReddit
                                                            .getDisplayName()
                                                            .toLowerCase(Locale.ENGLISH),
                                            TimePeriod.DAY);
                                    reloadSubs();
                                    break;
                                case 2:
                                    SortingUtil.setTime(
                                            "multi"
                                                    + ((MultiredditView)
                                                                    (((MultiredditOverviewPagerAdapter)
                                                                                    pager
                                                                                            .getAdapter())
                                                                            .getCurrentFragment()))
                                                            .posts
                                                            .multiReddit
                                                            .getDisplayName()
                                                            .toLowerCase(Locale.ENGLISH),
                                            TimePeriod.WEEK);
                                    reloadSubs();
                                    break;
                                case 3:
                                    SortingUtil.setTime(
                                            "multi"
                                                    + ((MultiredditView)
                                                                    (((MultiredditOverviewPagerAdapter)
                                                                                    pager
                                                                                            .getAdapter())
                                                                            .getCurrentFragment()))
                                                            .posts
                                                            .multiReddit
                                                            .getDisplayName()
                                                            .toLowerCase(Locale.ENGLISH),
                                            TimePeriod.MONTH);
                                    reloadSubs();
                                    break;
                                case 4:
                                    SortingUtil.setTime(
                                            "multi"
                                                    + ((MultiredditView)
                                                                    (((MultiredditOverviewPagerAdapter)
                                                                                    pager
                                                                                            .getAdapter())
                                                                            .getCurrentFragment()))
                                                            .posts
                                                            .multiReddit
                                                            .getDisplayName()
                                                            .toLowerCase(Locale.ENGLISH),
                                            TimePeriod.YEAR);
                                    reloadSubs();
                                    break;
                                case 5:
                                    SortingUtil.setTime(
                                            "multi"
                                                    + ((MultiredditView)
                                                                    (((MultiredditOverviewPagerAdapter)
                                                                                    pager
                                                                                            .getAdapter())
                                                                            .getCurrentFragment()))
                                                            .posts
                                                            .multiReddit
                                                            .getDisplayName()
                                                            .toLowerCase(Locale.ENGLISH),
                                            TimePeriod.ALL);
                                    reloadSubs();
                                    break;
                            }
                        }
                        return true;
                    }
                });
        popup.show();
    }

    private void reloadSubs() {
        int current = pager.getCurrentItem();
        adapter = new MultiredditOverviewPagerAdapter(getSupportFragmentManager());
        pager.setAdapter(adapter);
        pager.setCurrentItem(current);
    }

    private void setDataSet(List<MultiReddit> data) {
        try {
            usedArray = data;

            if (usedArray.isEmpty()) {
                buildDialog();
            } else {

                if (adapter == null) {
                    adapter = new MultiredditOverviewPagerAdapter(getSupportFragmentManager());
                } else {
                    adapter.notifyDataSetChanged();
                }
                pager.setAdapter(adapter);
                pager.setOffscreenPageLimit(1);
                tabs.setupWithViewPager(pager);
                if (!initialMulti.isEmpty()) {
                    for (int i = 0; i < usedArray.size(); i++) {
                        if (usedArray.get(i).getDisplayName().equalsIgnoreCase(initialMulti)) {
                            pager.setCurrentItem(i);
                            break;
                        }
                    }
                }
                tabs.setSelectedTabIndicatorColor(
                        new ColorPreferences(MultiredditOverview.this)
                                .getColor(usedArray.get(0).getDisplayName()));
                doDrawerSubs(0);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    Window window = this.getWindow();
                    int color = Palette.getDarkerColor(usedArray.get(0).getDisplayName());

                    if (SettingValues.alwaysBlackStatusbar) {
                        color = Color.BLACK;
                    }

                    window.setStatusBarColor(color);
                }
                final View header = findViewById(R.id.header);
                tabs.addOnTabSelectedListener(
                        new TabLayout.ViewPagerOnTabSelectedListener(pager) {
                            @Override
                            public void onTabReselected(TabLayout.Tab tab) {
                                super.onTabReselected(tab);
                                int pastVisiblesItems = 0;
                                int[] firstVisibleItems =
                                        ((CatchStaggeredGridLayoutManager)
                                                        (((MultiredditView)
                                                                        adapter
                                                                                .getCurrentFragment())
                                                                .rv.getLayoutManager()))
                                                .findFirstVisibleItemPositions(null);
                                if (firstVisibleItems != null && firstVisibleItems.length > 0) {
                                    for (int firstVisibleItem : firstVisibleItems) {
                                        pastVisiblesItems = firstVisibleItem;
                                    }
                                }
                                if (pastVisiblesItems > 8) {
                                    ((MultiredditView) adapter.getCurrentFragment())
                                            .rv.scrollToPosition(0);
                                    if (header != null) {
                                        header.animate()
                                                .translationY(header.getHeight())
                                                .setInterpolator(new LinearInterpolator())
                                                .setDuration(0);
                                    }
                                } else {
                                    ((MultiredditView) adapter.getCurrentFragment())
                                            .rv.smoothScrollToPosition(0);
                                }
                            }
                        });
                findViewById(R.id.header)
                        .setBackgroundColor(Palette.getColor(usedArray.get(0).getDisplayName()));
            }
        } catch (NullPointerException e) {
            buildDialog(true);
            Log.e(LogUtil.getTag(), "Cannot load multis:\n" + e);
        }
    }

    public void doDrawerSubs(int position) {
        MultiReddit current = usedArray.get(position);
        LinearLayout l = (LinearLayout) findViewById(R.id.sidebar_scroll);
        l.removeAllViews();

        CaseInsensitiveArrayList toSort = new CaseInsensitiveArrayList();

        for (MultiSubreddit s : current.getSubreddits()) {
            toSort.add(s.getDisplayName().toLowerCase(Locale.ENGLISH));
        }

        for (String sub : UserSubscriptions.sortNoExtras(toSort)) {
            final View convertView = getLayoutInflater().inflate(R.layout.subforsublist, l, false);

            final String subreddit = sub;
            final TextView t = convertView.findViewById(R.id.name);
            t.setText(subreddit);

            final View colorView = convertView.findViewById(R.id.color);
            colorView.setBackgroundResource(R.drawable.circle);
            BlendModeUtil.tintDrawableAsModulate(
                    colorView.getBackground(), Palette.getColor(subreddit));
            convertView.setOnClickListener(
                    new View.OnClickListener() {
                        @Override
                        public void onClick(View view) {
                            Intent inte = new Intent(MultiredditOverview.this, SubredditView.class);
                            inte.putExtra(SubredditView.EXTRA_SUBREDDIT, subreddit);
                            MultiredditOverview.this.startActivityForResult(inte, 4);
                        }
                    });
            l.addView(convertView);
        }
    }

    private class MultiredditOverviewPagerAdapter extends FragmentStatePagerAdapter {

        MultiredditOverviewPagerAdapter(FragmentManager fm) {
            super(fm, BEHAVIOR_RESUME_ONLY_CURRENT_FRAGMENT);
            pager.addOnPageChangeListener(
                    new ViewPager.SimpleOnPageChangeListener() {
                        @Override
                        public void onPageSelected(int position) {
                            findViewById(R.id.header)
                                    .animate()
                                    .translationY(0)
                                    .setInterpolator(new LinearInterpolator())
                                    .setDuration(180);
                            findViewById(R.id.header)
                                    .setBackgroundColor(
                                            Palette.getColor(
                                                    usedArray.get(position).getDisplayName()));
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                                Window window = getWindow();
                                int color =
                                        Palette.getDarkerColor(
                                                usedArray.get(position).getDisplayName());

                                if (SettingValues.alwaysBlackStatusbar) {
                                    color = Color.BLACK;
                                }

                                window.setStatusBarColor(color);
                            }
                            tabs.setSelectedTabIndicatorColor(
                                    new ColorPreferences(MultiredditOverview.this)
                                            .getColor(usedArray.get(position).getDisplayName()));
                            doDrawerSubs(position);
                        }
                    });
        }

        @NonNull
        @Override
        public Fragment getItem(int i) {
            Fragment f = new MultiredditView();
            Bundle args = new Bundle();

            args.putInt("id", i);
            args.putString(EXTRA_PROFILE, profile);

            f.setArguments(args);

            return f;
        }

        private Fragment mCurrentFragment;

        Fragment getCurrentFragment() {
            return mCurrentFragment;
        }

        @Override
        public void setPrimaryItem(
                @NonNull ViewGroup container, int position, @NonNull Object object) {
            if (mCurrentFragment != object) {
                mCurrentFragment = (Fragment) object;
            }
            super.setPrimaryItem(container, position, object);
        }

        @Override
        public int getCount() {
            if (usedArray == null) {
                return 1;
            } else {
                return usedArray.size();
            }
        }

        @Override
        public CharSequence getPageTitle(int position) {
            return usedArray.get(position).getFullName();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        // Check if adapter exists and has a current fragment
        if (requestCode == 940 && adapter != null) {
            Fragment currentFragment = adapter.getCurrentFragment();
            if (currentFragment instanceof MultiredditView) {
                MultiredditView multiredditView = (MultiredditView) currentFragment;

                if (resultCode == RESULT_OK && data != null) {
                    LogUtil.v("Doing hide posts");
                    ArrayList<Integer> posts = data.getIntegerArrayListExtra("seen");
                    if (posts != null && multiredditView.adapter != null) {
                        multiredditView.adapter.refreshView(posts);

                        // Check for lastPage extra and scroll if needed
                        if (data.hasExtra("lastPage")
                                && data.getIntExtra("lastPage", 0) != 0
                                && multiredditView.rv != null
                                && multiredditView.rv.getLayoutManager() instanceof LinearLayoutManager) {
                            ((LinearLayoutManager) multiredditView.rv.getLayoutManager())
                                .scrollToPositionWithOffset(
                                    data.getIntExtra("lastPage", 0) + 1,
                                    mToolbar != null ? mToolbar.getHeight() : 0);
                        }
                    }
                } else if (multiredditView.adapter != null) {
                    multiredditView.adapter.refreshView();
                }
            }
        }
    }
}
