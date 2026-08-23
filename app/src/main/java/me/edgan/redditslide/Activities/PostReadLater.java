package me.edgan.redditslide.Activities;

import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentStatePagerAdapter;
import androidx.viewpager.widget.ViewPager;
import me.edgan.redditslide.Fragments.ReadLaterView;
import me.edgan.redditslide.R;
import me.edgan.redditslide.Visuals.ColorPreferences;
import me.edgan.redditslide.util.MiscUtil;
import org.jspecify.annotations.NullMarked;

/** Created by ccrama on 9/17/2015. */
@NullMarked
public class PostReadLater extends BaseActivityAnim {

    @Override
    public void onCreate(@Nullable Bundle savedInstance) {
        overrideSwipeFromAnywhere();

        super.onCreate(savedInstance);

        applyColorTheme();
        setContentView(R.layout.activity_read_later);

        MiscUtil.setupOldSwipeModeBackground(this, getWindow().getDecorView());

        setupAppBar(R.id.toolbar, this.getString(R.string.read_later), true, true);
        requireToolbar().setPopupTheme(new ColorPreferences(this).getFontStyle().getBaseId());

        ViewPager pager = (ViewPager) requireViewById(R.id.content_view);
        pager.setAdapter(new ReadLaterPagerAdapter(getSupportFragmentManager()));
    }

    private static class ReadLaterPagerAdapter extends FragmentStatePagerAdapter {

        ReadLaterPagerAdapter(FragmentManager fm) {
            super(fm, BEHAVIOR_RESUME_ONLY_CURRENT_FRAGMENT);
        }

        @NonNull
        @Override
        public Fragment getItem(int i) {
            return new ReadLaterView();
        }

        @Override
        public int getCount() {
            return 1;
        }
    }
}
