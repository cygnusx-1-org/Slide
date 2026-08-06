package me.edgan.redditslide;

import androidx.annotation.Nullable;

import java.util.Locale;
import me.edgan.redditslide.util.PrefUtil;

public class UserTags {

    /**
     * Gets the tag for a specific username.
     *
     * @param username The username to find the tag of
     * @return String for the tag
     */
    public static String getUserTag(@Nullable String username) {
        // A contribution with no author cannot carry a tag; the key would read "user-tagnull".
        if (username == null) return "";
        return PrefUtil.getString(
                Reddit.tags, "user-tag" + username.toLowerCase(Locale.ENGLISH), "");
    }

    /**
     * Gets whether a username is tagged.
     *
     * @param username The username to find the tag of
     * @return Boolean if username is tagged
     */
    public static boolean isUserTagged(@Nullable String username) {
        if (username == null) return false;
        return Reddit.tags.contains("user-tag" + username.toLowerCase(Locale.ENGLISH));
    }

    public static void setUserTag(final String username, String tag) {
        Reddit.tags
                .edit()
                .putString("user-tag" + username.toLowerCase(Locale.ENGLISH), tag)
                .apply();
    }

    public static void removeUserTag(final String username) {
        Reddit.tags.edit().remove("user-tag" + username.toLowerCase(Locale.ENGLISH)).apply();
    }
}
