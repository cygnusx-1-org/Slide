package me.edgan.redditslide;

import java.util.Locale;
import me.edgan.redditslide.util.PrefUtil;

public class UserTags {

    /**
     * Gets the tag for a specific username.
     *
     * @param username The username to find the tag of
     * @return String for the tag
     */
    public static String getUserTag(String username) {
        return PrefUtil.getString(
                Reddit.tags, "user-tag" + username.toLowerCase(Locale.ENGLISH), "");
    }

    /**
     * Gets whether a username is tagged.
     *
     * @param username The username to find the tag of
     * @return Boolean if username is tagged
     */
    public static boolean isUserTagged(String username) {
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
