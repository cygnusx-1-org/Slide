package me.edgan.redditslide.ui.settings;

import android.os.Bundle;
import android.view.ViewGroup;

import me.edgan.redditslide.Activities.BaseActivityAnim;
import me.edgan.redditslide.R;
import me.edgan.redditslide.util.MiscUtil;

/** Appearance > Markdown settings (issue #179). */
public class SettingsMarkdown extends BaseActivityAnim {

    private SettingsMarkdownFragment fragment = new SettingsMarkdownFragment(this);

    public void onCreate(Bundle savedInstanceState) {
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
