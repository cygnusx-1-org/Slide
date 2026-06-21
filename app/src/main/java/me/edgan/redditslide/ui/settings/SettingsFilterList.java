package me.edgan.redditslide.ui.settings;

import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextWatcher;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import me.edgan.redditslide.Activities.BaseActivityAnim;
import me.edgan.redditslide.R;
import me.edgan.redditslide.SettingValues;
import me.edgan.redditslide.Visuals.Palette;
import me.edgan.redditslide.util.MiscUtil;

/**
 * Displays a single type of post filter (domain, selftext, title, profile, subreddit or flair) as
 * its own searchable list, enforcing a maximum number of entries.
 */
public class SettingsFilterList extends BaseActivityAnim {

    /** Intent extra used to choose which {@link FilterType} this screen edits. */
    public static final String EXTRA_FILTER_TYPE = "filter_type";

    /** Maximum number of matching entries rendered in the list at once. */
    public static final int DISPLAY_LIMIT = 100;

    private FilterType type;
    private EditText input;
    private TextView count;
    private LinearLayout list;
    private String query = "";

    /** Describes a single filter type and where its data lives. */
    enum FilterType {
        DOMAIN(
                R.string.settings_filter_domain,
                R.string.enter_text_to_filter_click_enter_to_add,
                SettingValues.PREF_DOMAIN_FILTERS,
                false,
                true),
        SELFTEXT(
                R.string.settings_filter_selfttext,
                R.string.enter_text_to_filter_click_enter_to_add,
                SettingValues.PREF_TEXT_FILTERS,
                false,
                false),
        TITLE(
                R.string.settings_filter_title,
                R.string.enter_text_to_filter_click_enter_to_add,
                SettingValues.PREF_TITLE_FILTERS,
                false,
                false),
        PROFILE(
                R.string.settings_filter_profile,
                R.string.enter_text_to_filter_click_enter_to_add,
                SettingValues.PREF_USER_FILTERS,
                false,
                false),
        SUBREDDIT(
                R.string.setting_filter_subreddits,
                R.string.enter_text_to_filter_click_enter_to_add,
                SettingValues.PREF_SUBREDDIT_FILTERS,
                false,
                false),
        FLAIR(
                R.string.settings_filter_flair,
                R.string.enter_text_to_filter_click_enter_to_add_flair,
                SettingValues.PREF_FLAIR_FILTERS,
                true,
                false);

        final int titleRes;
        final int hintRes;
        final String prefKey;
        final boolean isFlair;
        final boolean isDomain;

        FilterType(int titleRes, int hintRes, String prefKey, boolean isFlair, boolean isDomain) {
            this.titleRes = titleRes;
            this.hintRes = hintRes;
            this.prefKey = prefKey;
            this.isFlair = isFlair;
            this.isDomain = isDomain;
        }

        Set<String> getFilters() {
            switch (this) {
                case DOMAIN:
                    return SettingValues.domainFilters;
                case SELFTEXT:
                    return SettingValues.textFilters;
                case TITLE:
                    return SettingValues.titleFilters;
                case PROFILE:
                    return SettingValues.userFilters;
                case SUBREDDIT:
                    return SettingValues.subredditFilters;
                case FLAIR:
                default:
                    return SettingValues.flairFilters;
            }
        }
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        applyColorTheme();
        setContentView(R.layout.activity_settings_filter_list);

        MiscUtil.setupOldSwipeModeBackground(this, getWindow().getDecorView());

        String typeName = getIntent().getStringExtra(EXTRA_FILTER_TYPE);
        type = typeName == null ? FilterType.DOMAIN : FilterType.valueOf(typeName);

        setupAppBar(R.id.toolbar, type.titleRes, true, true);

        input = (EditText) findViewById(R.id.filter_input);
        count = (TextView) findViewById(R.id.filter_count);
        list = (LinearLayout) findViewById(R.id.filterlist);

        input.setHint(type.hintRes);
        if (type.isDomain) {
            input.setInputType(InputType.TYPE_TEXT_VARIATION_URI);
        }

        input.addTextChangedListener(
                new TextWatcher() {
                    @Override
                    public void beforeTextChanged(
                            CharSequence s, int start, int before, int counter) {}

                    @Override
                    public void onTextChanged(
                            CharSequence s, int start, int before, int counter) {
                        query = s.toString().toLowerCase(Locale.ENGLISH).trim();
                        updateList();
                    }

                    @Override
                    public void afterTextChanged(Editable s) {}
                });

        input.setOnEditorActionListener(
                (v, actionId, event) -> {
                    if (actionId == EditorInfo.IME_ACTION_DONE) {
                        addFilter(v.getText().toString());
                    }
                    return false;
                });

        updateList();
    }

    /** Validates and adds the given text to the active filter set. */
    private void addFilter(String raw) {
        String text = raw.toLowerCase(Locale.ENGLISH).trim();

        if (text.isEmpty()) {
            return;
        }

        if (type.isFlair && !text.matches(".+:.+")) {
            return;
        }

        type.getFilters().add(text);
        query = "";
        input.setText("");
        updateList();
    }

    /**
     * Rebuilds the visible list, honoring the current search query and rendering at most {@link
     * #DISPLAY_LIMIT} matches.
     */
    private void updateList() {
        list.removeAllViews();

        Set<String> filters = type.getFilters();

        List<String> matches = new ArrayList<>();
        for (String s : filters) {
            if (query.isEmpty() || s.toLowerCase(Locale.ENGLISH).contains(query)) {
                matches.add(s);
            }
        }
        java.util.Collections.sort(matches);

        int shown = Math.min(matches.size(), DISPLAY_LIMIT);
        for (int i = 0; i < shown; i++) {
            final String s = matches.get(i);
            final View t =
                    getLayoutInflater().inflate(R.layout.account_textview, list, false);

            ((TextView) t.findViewById(R.id.name)).setText(type.isFlair ? formatFlair(s) : s);
            t.findViewById(R.id.remove)
                    .setOnClickListener(
                            v -> {
                                filters.remove(s);
                                updateList();
                            });
            list.addView(t);
        }

        if (matches.size() > DISPLAY_LIMIT) {
            count.setText(
                    getString(R.string.filter_limit_reached, DISPLAY_LIMIT, matches.size()));
        } else {
            count.setText(getResources()
                    .getQuantityString(
                            R.plurals.filter_count,
                            matches.size(),
                            matches.size(),
                            DISPLAY_LIMIT));
        }
    }

    /** Builds the colored "/r/subreddit flair" label used for flair filters. */
    private CharSequence formatFlair(String s) {
        SpannableStringBuilder b = new SpannableStringBuilder();
        String subname = s.split(":")[0];
        SpannableStringBuilder subreddit = new SpannableStringBuilder(" /r/" + subname + " ");

        if (SettingValues.colorSubName
                && Palette.getColor(subname) != Palette.getDefaultColor()) {
            subreddit.setSpan(
                    new ForegroundColorSpan(Palette.getColor(subname)),
                    0,
                    subreddit.length(),
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            subreddit.setSpan(
                    new StyleSpan(Typeface.BOLD),
                    0,
                    subreddit.length(),
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }

        b.append(subreddit).append(s.split(":")[1]);
        return b;
    }

    @Override
    public void onPause() {
        super.onPause();
        SharedPreferences.Editor e = SettingValues.prefs.edit();
        e.putStringSet(type.prefKey, type.getFilters());
        e.apply();
    }
}
