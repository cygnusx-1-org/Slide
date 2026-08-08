package me.edgan.redditslide.ui.settings;

import android.os.Bundle;
import android.view.ViewGroup;
import androidx.annotation.Nullable;
import me.edgan.redditslide.Activities.BaseActivityAnim;
import me.edgan.redditslide.R;
import me.edgan.redditslide.util.MiscUtil;
import org.jspecify.annotations.NullMarked;

/** Created by l3d00m on 11/13/2015. */
@NullMarked
public class ManageOfflineContent extends BaseActivityAnim {

    ManageOfflineContentFragment fragment = new ManageOfflineContentFragment(this);

    @Override public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        applyColorTheme();
        setContentView(R.layout.activity_manage_history);

        MiscUtil.setupOldSwipeModeBackground(this, getWindow().getDecorView());

        setupAppBar(R.id.toolbar, R.string.manage_offline_content, true, true);

        ((ViewGroup) requireViewById(R.id.manage_history))
                .addView(getLayoutInflater().inflate(R.layout.activity_manage_history_child, null));

        fragment.Bind();
    }
}
