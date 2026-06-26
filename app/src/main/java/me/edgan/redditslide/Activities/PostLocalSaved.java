package me.edgan.redditslide.Activities;

import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentStatePagerAdapter;
import androidx.viewpager.widget.ViewPager;

import me.edgan.redditslide.Fragments.LocalSavedView;
import me.edgan.redditslide.R;
import me.edgan.redditslide.Visuals.ColorPreferences;
import me.edgan.redditslide.util.MiscUtil;

/** Viewer for the Local Saved collection (saves Reddit dropped from the /saved listing). */
public class PostLocalSaved extends BaseActivityAnim {

    @Override
    public void onCreate(Bundle savedInstance) {
        overrideSwipeFromAnywhere();

        super.onCreate(savedInstance);

        applyColorTheme();
        setContentView(R.layout.activity_read_later);

        MiscUtil.setupOldSwipeModeBackground(this, getWindow().getDecorView());

        setupAppBar(R.id.toolbar, this.getString(R.string.local_saved), true, true);
        mToolbar.setPopupTheme(new ColorPreferences(this).getFontStyle().getBaseId());

        ViewPager pager = (ViewPager) findViewById(R.id.content_view);
        pager.setAdapter(new LocalSavedPagerAdapter(getSupportFragmentManager()));
    }

    private static class LocalSavedPagerAdapter extends FragmentStatePagerAdapter {

        LocalSavedPagerAdapter(FragmentManager fm) {
            super(fm, BEHAVIOR_RESUME_ONLY_CURRENT_FRAGMENT);
        }

        @NonNull
        @Override
        public Fragment getItem(int i) {
            LocalSavedView fragment = new LocalSavedView();
            Bundle args = new Bundle();
            args.putBoolean("single", true);
            fragment.setArguments(args);
            return fragment;
        }

        @Override
        public int getCount() {
            return 1;
        }
    }
}
