package me.edgan.redditslide.ui.settings;

import android.os.Bundle;
import android.view.ViewGroup;
import androidx.annotation.Nullable;
import me.edgan.redditslide.Activities.BaseActivityAnim;
import me.edgan.redditslide.R;
import me.edgan.redditslide.util.MiscUtil;
import org.jspecify.annotations.NullMarked;

/** Appearance > Markdown settings (issue #179). */
@NullMarked
public class SettingsMarkdown extends BaseActivityAnim {

    private SettingsMarkdownFragment fragment = new SettingsMarkdownFragment(this);

    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        applyColorTheme();
        setContentView(R.layout.activity_settings_markdown);

        MiscUtil.setupOldSwipeModeBackground(this, getWindow().getDecorView());

        setupAppBar(R.id.toolbar, R.string.settings_title_markdown, true, true);

        ((ViewGroup) findViewById(R.id.settings_markdown))
                .addView(
                        getLayoutInflater()
                                .inflate(R.layout.activity_settings_markdown_child, null));

        fragment.Bind();
    }
}
