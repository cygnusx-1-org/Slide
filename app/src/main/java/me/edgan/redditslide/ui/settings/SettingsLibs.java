package me.edgan.redditslide.ui.settings;

import android.os.Bundle;
import androidx.annotation.Nullable;
import com.mikepenz.aboutlibraries.LibsBuilder;
import com.mikepenz.aboutlibraries.ui.LibsSupportFragment;
import me.edgan.redditslide.Activities.BaseActivityAnim;
import me.edgan.redditslide.R;
import me.edgan.redditslide.util.MiscUtil;
import org.jspecify.annotations.NullMarked;

@NullMarked
public class SettingsLibs extends BaseActivityAnim {

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        applyColorTheme();
        setContentView(R.layout.activity_settings_libs);

        MiscUtil.setupOldSwipeModeBackground(this, getWindow().getDecorView());

        setupAppBar(R.id.toolbar, R.string.settings_about_libs, true, true);

        LibsSupportFragment fragment = new LibsBuilder().supportFragment();
        // Asked of the container, not of savedInstanceState: a hibernate resume hands this screen
        // a saved state whose fragments BaseActivity deliberately did not restore, and skipping
        // the add on that basis left the page blank.
        if (getSupportFragmentManager().findFragmentById(R.id.root_fragment) == null) {
            getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.root_fragment, fragment)
                    .commit();
        }
    }
}
