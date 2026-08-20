package me.edgan.redditslide.Activities;

import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.LinearInterpolator;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentStatePagerAdapter;
import androidx.viewpager.widget.ViewPager;
import com.google.android.material.tabs.TabLayout;
import me.edgan.redditslide.Authentication;
import me.edgan.redditslide.Fragments.ModLog;
import me.edgan.redditslide.Fragments.ModPage;
import me.edgan.redditslide.Fragments.ModmailPage;
import me.edgan.redditslide.Modmail.ModmailApi;
import me.edgan.redditslide.R;
import me.edgan.redditslide.UserSubscriptions;
import me.edgan.redditslide.Visuals.ColorPreferences;
import me.edgan.redditslide.Visuals.Palette;
import me.edgan.redditslide.util.MiscUtil;
import org.jspecify.annotations.NullMarked;

/** Created by ccrama on 9/17/2015. */
@NullMarked
public class ModQueue extends BaseActivityAnim {


    @Override
    public void onCreate(@Nullable Bundle savedInstance) {
        overrideSwipeFromAnywhere();

        super.onCreate(savedInstance);

        applyColorTheme("");
        setContentView(R.layout.activity_inbox);

        MiscUtil.setupOldSwipeModeBackground(this, getWindow().getDecorView());

        setupAppBar(R.id.toolbar, R.string.drawer_moderation, true, true);

        TabLayout tabs = (TabLayout) requireViewById(R.id.sliding_tabs);
        tabs.setTabMode(TabLayout.MODE_SCROLLABLE);
        tabs.setSelectedTabIndicatorColor(new ColorPreferences(ModQueue.this).getColor("no sub"));
        final View header = requireViewById(R.id.header);
        ViewPager pager = (ViewPager) requireViewById(R.id.content_view);
        pager.addOnPageChangeListener(
                new ViewPager.SimpleOnPageChangeListener() {
                    @Override
                    public void onPageSelected(int position) {
                        header.animate()
                                .translationY(0)
                                .setInterpolator(new LinearInterpolator())
                                .setDuration(180);
                    }
                });
        requireViewById(R.id.header).setBackgroundColor(Palette.getDefaultColor());
        pager.setAdapter(new ModQueuePagerAdapter(getSupportFragmentManager()));
        tabs.setupWithViewPager(pager);

        // New Modmail needs the "modmail" OAuth scope, which tokens authorized before it was added
        // don't carry. Warn so the empty Mod mail tabs aren't mistaken for a bug (issue #219).
        if (!Authentication.hasScope(ModmailApi.SCOPE)) {
            Toast.makeText(this, R.string.modmail_scope_missing, Toast.LENGTH_LONG).show();
        }
    }

    private class ModQueuePagerAdapter extends FragmentStatePagerAdapter {

        @SuppressWarnings("NullAway.Init") // assigned in setPrimaryItem as the pager swaps pages
        private Fragment mCurrentFragment;

        ModQueuePagerAdapter(FragmentManager fm) {
            super(fm, BEHAVIOR_RESUME_ONLY_CURRENT_FRAGMENT);
        }

        @Override
        public void setPrimaryItem(
                @NonNull ViewGroup container, int position, @NonNull Object object) {
            if (mCurrentFragment != object) {
                mCurrentFragment = (Fragment) object;
            }
            super.setPrimaryItem(container, position, object);
        }

        @NonNull
        @Override
        public Fragment getItem(int i) {
            Fragment f;
            Bundle args = new Bundle();
            switch (i) {
                case 0:
                    f = new ModmailPage();
                    args.putBoolean("unread", true);
                    f.setArguments(args);
                    return f;
                case 1:
                    f = new ModmailPage();
                    args.putBoolean("unread", false);
                    f.setArguments(args);
                    return f;
                case 2:
                    f = new ModPage();
                    args.putString("id", "modqueue");
                    args.putString("subreddit", "mod");
                    f.setArguments(args);
                    return f;
                case 3:
                    f = new ModPage();
                    args.putString("id", "unmoderated");
                    args.putString("subreddit", "mod");
                    f.setArguments(args);
                    return f;
                case 4:
                    f = new ModLog();
                    f.setArguments(args);
                    return f;
                default:
                    f = new ModPage();
                    args.putString("id", "modqueue");
                    args.putString(
                            "subreddit",
                            UserSubscriptions.modOf == null
                                    ? ""
                                    : UserSubscriptions.modOf.get(i - 5));
                    f.setArguments(args);
                    return f;
            }
        }

        @Override
        public int getCount() {
            return UserSubscriptions.modOf == null ? 2 : UserSubscriptions.modOf.size() + 5;
        }

        @Override
        public CharSequence getPageTitle(int position) {
            switch (position) {
                case 0:
                    return getString(R.string.mod_mail_unread);
                case 1:
                    return getString(R.string.mod_mail);
                case 2:
                    return getString(R.string.mod_modqueue);
                case 3:
                    return getString(R.string.mod_unmoderated);
                case 4:
                    return getString(R.string.mod_log);
                default:
                    return UserSubscriptions.modOf == null
                            ? ""
                            : UserSubscriptions.modOf.get(position - 5);
            }
        }
    }
}
