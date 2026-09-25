package me.edgan.redditslide.Activities;

import android.content.Intent;
import android.os.Bundle;
import android.text.InputType;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.animation.LinearInterpolator;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentStatePagerAdapter;
import androidx.viewpager.widget.PagerAdapter;
import androidx.viewpager.widget.ViewPager;
import com.google.android.material.tabs.TabLayout;
import me.edgan.redditslide.Fragments.SubredditListView;
import me.edgan.redditslide.HibernateState;
import me.edgan.redditslide.R;
import me.edgan.redditslide.Visuals.ColorPreferences;
import me.edgan.redditslide.Visuals.Palette;
import me.edgan.redditslide.util.MaterialInputDialog;
import me.edgan.redditslide.util.MiscUtil;
import org.jspecify.annotations.NullMarked;

/** Created by ccrama on 9/17/2015. */
@NullMarked
public class Discover extends BaseActivityAnim implements HibernateState.Restorable {

    @SuppressWarnings("NullAway.Init") // assigned in onCreate
    private ViewPager pager;

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.menu_discover, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int itemId = item.getItemId();
        if (itemId == android.R.id.home) {
            getOnBackPressedDispatcher().onBackPressed();
            return true;
        } else if (itemId == R.id.search) {
            new MaterialInputDialog.Builder(Discover.this)
                    .inputType(
                            InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS)
                    .inputRange(3, 100)
                    .input(getString(R.string.discover_search), null, null)
                    .positiveText(R.string.search_all)
                    .onPositive(
                            dialog -> {
                                Intent inte = new Intent(Discover.this, SubredditSearch.class);
                                inte.putExtra(
                                        "term",
                                        dialog.getInputEditText().getText().toString());
                                Discover.this.startActivity(inte);
                            })
                    .negativeText(R.string.btn_cancel)
                    .show();
            return true;
        } else {
            return false;
        }
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstance) {
        overrideSwipeFromAnywhere();

        super.onCreate(savedInstance);

        applyColorTheme("");
        setContentView(R.layout.activity_multireddits);
        MiscUtil.setupOldSwipeModeBackground(this, getWindow().getDecorView());

        ((DrawerLayout) requireViewById(R.id.drawer_layout))
                .setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED);
        setupAppBar(R.id.toolbar, R.string.discover_title, true, false);
        requireToolbar().setPopupTheme(new ColorPreferences(this).getFontStyle().getBaseId());

        requireViewById(R.id.header).setBackgroundColor(Palette.getDefaultColor());
        TabLayout tabs = (TabLayout) requireViewById(R.id.sliding_tabs);
        tabs.setTabMode(TabLayout.MODE_FIXED);
        tabs.setSelectedTabIndicatorColor(new ColorPreferences(Discover.this).getColor("no sub"));

        pager = (ViewPager) requireViewById(R.id.content_view);
        pager.setAdapter(new DiscoverPagerAdapter(getSupportFragmentManager()));
        tabs.setupWithViewPager(pager);
        pager.addOnPageChangeListener(
                new ViewPager.SimpleOnPageChangeListener() {
                    @Override
                    public void onPageSelected(int position) {
                        requireViewById(R.id.header)
                                .animate()
                                .translationY(0)
                                .setInterpolator(new LinearInterpolator())
                                .setDuration(180);
                    }
                });
    }

    @Override
    public void saveHibernateState(Bundle out) {
        super.saveHibernateState(out);
        out.putInt(HibernateState.STATE_PAGE, pager.getCurrentItem());
    }

    @Override
    public void restoreHibernateState(Bundle in) {
        super.restoreHibernateState(in);
        // Applied here rather than deferred to a field, because BaseActivity claims for this
        // screen from onPostCreate -- by which point onCreate has built the pager. Both tabs
        // are fixed and built by the adapter's getItem, so the index is the tab's identity and
        // cannot drift the way a list of the user's own multireddits can.
        final PagerAdapter built = pager.getAdapter();
        final int page = in.getInt(HibernateState.STATE_PAGE, -1);
        if (built != null && page >= 0 && page < built.getCount()) {
            pager.setCurrentItem(page, false);
        }
    }

    private class DiscoverPagerAdapter extends FragmentStatePagerAdapter {

        DiscoverPagerAdapter(FragmentManager fm) {
            super(fm, BEHAVIOR_RESUME_ONLY_CURRENT_FRAGMENT);
        }

        @NonNull
        @Override
        public Fragment getItem(int i) {
            Fragment f = new SubredditListView();
            Bundle args = new Bundle();
            args.putString("id", i == 1 ? "trending" : "popular");
            f.setArguments(args);

            return f;
        }

        @Override
        public int getCount() {
            return 2;
        }

        @Override
        public CharSequence getPageTitle(int position) {
            if (position == 0) {
                return getString(R.string.discover_popular);
            } else {
                return getString(R.string.discover_trending);
            }
        }
    }
}
