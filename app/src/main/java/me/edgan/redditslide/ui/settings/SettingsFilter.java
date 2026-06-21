package me.edgan.redditslide.ui.settings;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Switch;
import android.widget.TextView;
import java.util.HashSet;
import java.util.Set;
import me.edgan.redditslide.Activities.BaseActivityAnim;
import me.edgan.redditslide.PostMatch;
import me.edgan.redditslide.R;
import me.edgan.redditslide.SettingValues;
import me.edgan.redditslide.ui.settings.SettingsFilterList.FilterType;
import me.edgan.redditslide.util.MiscUtil;

/** Created by l3d00m on 11/13/2015. */
public class SettingsFilter extends BaseActivityAnim {

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        applyColorTheme();
        setContentView(R.layout.activity_settings_filters);

        MiscUtil.setupOldSwipeModeBackground(this, getWindow().getDecorView());

        setupAppBar(R.id.toolbar, R.string.settings_title_filter, true, true);

        // Initialize memory filters as empty at app start
        if (SettingValues.subredditFiltersTillRestart) {
            PostMatch.memorySubredditFilters = new HashSet<>();
        }

        // Each filter type opens its own searchable list screen
        setupRow(R.id.domain_row, FilterType.DOMAIN);
        setupRow(R.id.selftext_row, FilterType.SELFTEXT);
        setupRow(R.id.title_row, FilterType.TITLE);
        setupRow(R.id.profile_row, FilterType.PROFILE);
        setupRow(R.id.subreddit_row, FilterType.SUBREDDIT);
        setupRow(R.id.flair_row, FilterType.FLAIR);

        // Add switch for subreddit content filters till restart
        Switch filtersTillRestart = (Switch) findViewById(R.id.subreddit_filters_till_restart);
        filtersTillRestart.setChecked(SettingValues.subredditFiltersTillRestart);
        filtersTillRestart.setOnCheckedChangeListener((buttonView, isChecked) -> {
            SettingValues.subredditFiltersTillRestart = isChecked;
            SettingValues.prefs.edit()
                .putBoolean(SettingValues.PREF_SUBREDDIT_FILTERS_TILL_RESTART, isChecked)
                .apply();
        });

        // Add switch for subreddit filter prefix matching
        Switch filterPrefixMatching = (Switch) findViewById(R.id.subreddit_filter_prefix_matching);
        filterPrefixMatching.setChecked(SettingValues.subredditFilterPrefixMatching);
        filterPrefixMatching.setOnCheckedChangeListener((buttonView, isChecked) -> {
            SettingValues.subredditFilterPrefixMatching = isChecked;
            SettingValues.prefs.edit()
                .putBoolean(SettingValues.PREF_SUBREDDIT_FILTER_PREFIX_MATCHING, isChecked)
                .apply();
        });

        // Add switch for filtering old posts
        Switch filterOldPosts = (Switch) findViewById(R.id.filter_old_posts);
        filterOldPosts.setChecked(SettingValues.filterOldPosts);
        filterOldPosts.setOnCheckedChangeListener((buttonView, isChecked) -> {
            SettingValues.filterOldPosts = isChecked;
            SettingValues.prefs.edit()
                .putBoolean(SettingValues.PREF_FILTER_OLD_POSTS, isChecked)
                .apply();
        });
    }

    @Override
    public void onResume() {
        super.onResume();
        // Counts may change after editing a list, so refresh them every time
        updateCount(R.id.domain_count, FilterType.DOMAIN.getFilters());
        updateCount(R.id.selftext_count, FilterType.SELFTEXT.getFilters());
        updateCount(R.id.title_count, FilterType.TITLE.getFilters());
        updateCount(R.id.profile_count, FilterType.PROFILE.getFilters());
        updateCount(R.id.subreddit_count, FilterType.SUBREDDIT.getFilters());
        updateCount(R.id.flair_count, FilterType.FLAIR.getFilters());
    }

    private void setupRow(int rowId, FilterType type) {
        findViewById(rowId)
                .setOnClickListener(
                        v -> {
                            Intent i = new Intent(this, SettingsFilterList.class);
                            i.putExtra(SettingsFilterList.EXTRA_FILTER_TYPE, type.name());
                            startActivity(i);
                        });
    }

    private void updateCount(int countId, Set<String> filters) {
        ((TextView) findViewById(countId)).setText(String.valueOf(filters.size()));
    }
}
