/*
 * Copyright (c) 2016. ccrama
 *
 * Slide is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package me.edgan.redditslide.util;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import me.edgan.redditslide.Fragments.DrawerItemsDialog;
import me.edgan.redditslide.Reddit;
import me.edgan.redditslide.SettingValues;
import org.jspecify.annotations.NullMarked;

@NullMarked
public class UpgradeUtil {
    // Increment for each needed change
    private static final int VERSION = 4;

    private UpgradeUtil() {}

    /** Runs any upgrade actions required between versions in an organised way */
    public static void upgrade(Context context) {
        SharedPreferences colors = context.getSharedPreferences("COLOR", 0);
        SharedPreferences upgradePrefs = context.getSharedPreferences("upgradeUtil", 0);

        // Exit if this is the first start
        if (colors != null && !colors.contains("Tutorial")) {
            upgradePrefs.edit().putInt("VERSION", VERSION).apply();
            return;
        }

        final int CURRENT = upgradePrefs.getInt("VERSION", 0);

        // Exit if we're up to date
        if (CURRENT == VERSION) return;

        if (CURRENT < 1) {
            SharedPreferences prefs = context.getSharedPreferences("SETTINGS", 0);
            String domains = PrefUtil.getString(prefs, SettingValues.PREF_ALWAYS_EXTERNAL, "");

            domains =
                    domains.replaceFirst("(?<=^|,)youtube.co(?=$|,)", "youtube.com")
                            .replaceFirst("(?<=^|,)play.google.co(?=$|,)", "play.google.com");

            prefs.edit().putString(SettingValues.PREF_ALWAYS_EXTERNAL, domains).apply();
        }

        // migrate old filters
        if (CURRENT < 2) {
            SharedPreferences prefs = context.getSharedPreferences("SETTINGS", 0);
            SharedPreferences.Editor prefsEditor = prefs.edit();
            String titleFilterStr = PrefUtil.getString(prefs, SettingValues.PREF_TITLE_FILTERS, "");
            String textFilterStr = PrefUtil.getString(prefs, SettingValues.PREF_TEXT_FILTERS, "");
            String flairFilterStr = PrefUtil.getString(prefs, SettingValues.PREF_FLAIR_FILTERS, "");
            String subredditFilterStr = PrefUtil.getString(prefs, SettingValues.PREF_SUBREDDIT_FILTERS, "");
            String domainFilterStr = PrefUtil.getString(prefs, SettingValues.PREF_DOMAIN_FILTERS, "");
            String usersFilterStr = PrefUtil.getString(prefs, SettingValues.PREF_USER_FILTERS, "");
            String alwaysExternalStr = PrefUtil.getString(prefs, SettingValues.PREF_ALWAYS_EXTERNAL, "");

            prefsEditor.remove(SettingValues.PREF_TITLE_FILTERS);
            prefsEditor.remove(SettingValues.PREF_TEXT_FILTERS);
            prefsEditor.remove(SettingValues.PREF_FLAIR_FILTERS);
            prefsEditor.remove(SettingValues.PREF_SUBREDDIT_FILTERS);
            prefsEditor.remove(SettingValues.PREF_DOMAIN_FILTERS);
            prefsEditor.remove(SettingValues.PREF_USER_FILTERS);
            prefsEditor.remove(SettingValues.PREF_ALWAYS_EXTERNAL);

            Set<String> titleFilters =
                    titleFilterStr.isEmpty()
                            ? new HashSet<>()
                            : new HashSet<>(
                                    Arrays.asList(
                                            titleFilterStr
                                                    .replaceAll("^[,\\s]+", "")
                                                    .toLowerCase(Locale.ENGLISH)
                                                    .split("[,\\s]+")));

            Set<String> textFilters =
                    textFilterStr.isEmpty()
                            ? new HashSet<>()
                            : new HashSet<>(
                                    Arrays.asList(
                                            textFilterStr
                                                    .replaceAll("^[,\\s]+", "")
                                                    .toLowerCase(Locale.ENGLISH)
                                                    .split("[,\\s]+")));

            Set<String> flairFilters =
                    flairFilterStr.isEmpty()
                            ? new HashSet<>()
                            : new HashSet<>(
                                    Arrays.asList(
                                            flairFilterStr
                                                    .replaceAll("^[,]+", "")
                                                    .toLowerCase(Locale.ENGLISH)
                                                    .split("[,]+")));
            // verify flairs filters are valid
            // A flair filter is "<subreddit>:<flair>", and both halves have to be there: an entry
            // with nothing after the colon matches no flair, and used to crash the feed on the
            // split. This is the same test SettingsFilterList.addFilter applies to typed entries;
            // merely containing a colon is not enough.
            HashSet<String> invalid = new HashSet<>();
            for (String s : flairFilters) {
                if (!s.matches(".+:.+")) {
                    invalid.add(s);
                }
            }
            flairFilters.removeAll(invalid);

            Set<String> subredditFilters =
                    subredditFilterStr.isEmpty()
                            ? new HashSet<>()
                            : new HashSet<>(
                                    Arrays.asList(
                                            subredditFilterStr
                                                    .replaceAll("^[,\\s]+", "")
                                                    .toLowerCase(Locale.ENGLISH)
                                                    .split("[,\\s]+")));

            Set<String> domainFilters =
                    domainFilterStr.isEmpty()
                            ? new HashSet<>()
                            : new HashSet<>(
                                    Arrays.asList(
                                            domainFilterStr
                                                    .replaceAll("^[,\\s]+", "")
                                                    .toLowerCase(Locale.ENGLISH)
                                                    .split("[,\\s]+")));

            Set<String> usersFilters =
                    usersFilterStr.isEmpty()
                            ? new HashSet<>()
                            : new HashSet<>(
                                    Arrays.asList(
                                            usersFilterStr
                                                    .replaceAll("^[,\\s]+", "")
                                                    .toLowerCase(Locale.ENGLISH)
                                                    .split("[,\\s]+")));

            Set<String> alwaysExternal =
                    alwaysExternalStr.isEmpty()
                            ? new HashSet<>()
                            : new HashSet<>(
                                    Arrays.asList(
                                            alwaysExternalStr
                                                    .replaceAll("^[,\\s]+", "")
                                                    .toLowerCase(Locale.ENGLISH)
                                                    .split("[,\\s]+")));

            prefsEditor.putStringSet(SettingValues.PREF_TITLE_FILTERS, titleFilters);
            prefsEditor.putStringSet(SettingValues.PREF_TEXT_FILTERS, textFilters);
            prefsEditor.putStringSet(SettingValues.PREF_FLAIR_FILTERS, flairFilters);
            prefsEditor.putStringSet(SettingValues.PREF_SUBREDDIT_FILTERS, subredditFilters);
            prefsEditor.putStringSet(SettingValues.PREF_DOMAIN_FILTERS, domainFilters);
            prefsEditor.putStringSet(SettingValues.PREF_USER_FILTERS, usersFilters);
            prefsEditor.putStringSet(SettingValues.PREF_ALWAYS_EXTERNAL, alwaysExternal);

            prefsEditor.apply();
        }

        // Show the new "Users" drawer item by default. selectedDrawerItems is a bitmask, and -1
        // means "everything", so only a stored value written before USERS existed needs the bit
        // adding; leaving it alone would hide the item for anyone who has opened the Drawer Items
        // dialog.
        if (CURRENT < 3) {
            SharedPreferences prefs = context.getSharedPreferences("SETTINGS", 0);
            long selected = prefs.getLong(SettingValues.PREF_SELECTED_DRAWER_ITEMS, -1);

            if (selected != -1) {
                prefs.edit()
                        .putLong(
                                SettingValues.PREF_SELECTED_DRAWER_ITEMS,
                                selected | DrawerItemsDialog.SettingsDrawerEnum.USERS.value)
                        .apply();
            }
        }

        // Per-subreddit layout presets were written under the subreddit as displayed, while the
        // feed has only ever read the lowercased key, so a preset set for r/AskReddit was stored
        // where nothing looked for it and the custom layout never applied. The key is built in one
        // place now (Reddit#getLayoutPrefKey); move what is already stored onto it, so the setting
        // starts working and the settings screen keeps listing the subreddit as modified.
        if (CURRENT < 4) {
            SharedPreferences prefs = context.getSharedPreferences("SETTINGS", 0);
            SharedPreferences.Editor prefsEditor = prefs.edit();

            for (Map.Entry<String, ?> entry : prefs.getAll().entrySet()) {
                final String key = entry.getKey();
                if (!key.startsWith(Reddit.PREF_LAYOUT)
                        || !(entry.getValue() instanceof Boolean)) {
                    continue;
                }

                final String migrated =
                        Reddit.getLayoutPrefKey(key.substring(Reddit.PREF_LAYOUT.length()));
                if (migrated.equals(key)) {
                    continue;
                }

                prefsEditor.remove(key);
                // A preset already saved under the lowercased key is the live one; the stale
                // mixed-case copy is dropped rather than allowed to overwrite it.
                if (!prefs.contains(migrated)) {
                    prefsEditor.putBoolean(migrated, (Boolean) entry.getValue());
                }
            }

            prefsEditor.apply();
        }

        upgradePrefs.edit().putInt("VERSION", VERSION).apply();
    }
}
