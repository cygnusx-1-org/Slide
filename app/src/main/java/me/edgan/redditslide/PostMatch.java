package me.edgan.redditslide;

import android.content.SharedPreferences;

import net.dean.jraw.models.Submission;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.Locale;
import java.util.Set;
import java.util.HashSet;
import java.util.Map;
import java.util.HashMap;


/** Created by carlo_000 on 1/13/2016. */
public class PostMatch {
    // Add memory filters here
    public static Set<String> memorySubredditFilters = new HashSet<>();

    // Memory storage for content filters
    private static final Map<String, Boolean> memoryContentFilters = new HashMap<>();

    /**
     * Checks if a string is totally or partially contained in a set of strings
     *
     * @param target string to check
     * @param strings set of strings to check in
     * @param totalMatch only allow total match, no partial matches
     * @return if the string is contained in the set of strings
     */
    public static boolean contains(String target, Set<String> strings, boolean totalMatch) {
        // filters are always stored lowercase
        if (totalMatch) {
            return strings.contains(target.toLowerCase(Locale.ENGLISH).trim());
        } else if (strings.contains(target.toLowerCase(Locale.ENGLISH).trim())) {
            return true;
        } else {
            for (String s : strings) {
                if (target.toLowerCase(Locale.ENGLISH).trim().contains(s)) {
                    return true;
                }
            }
            return false;
        }
    }

    /**
     * Checks if a domain should be filtered or not: returns true if the target domain ends with the
     * comparison domain and if supplied, target path begins with the comparison path
     *
     * @param target URL to check
     * @param strings The URLs to check against
     * @return If the target is covered by any strings
     * @throws MalformedURLException
     */
    public static boolean isDomain(String target, Set<String> strings)
            throws MalformedURLException {
        URL domain = new URL(target);
        for (String s : strings) {
            if (!s.contains("/")) {
                if (ContentType.hostContains(domain.getHost(), s)) {
                    return true;
                } else {
                    continue;
                }
            }

            if (!s.contains("://")) {
                s = "http://" + s;
            }

            try {
                URL comparison = new URL(s.toLowerCase(Locale.ENGLISH));

                if (ContentType.hostContains(domain.getHost(), comparison.getHost())
                        && domain.getPath().startsWith(comparison.getPath())) {
                    return true;
                }
            } catch (MalformedURLException ignored) {
            }
        }
        return false;
    }

    public static boolean openExternal(String url) {
        try {
            return isDomain(url.toLowerCase(Locale.ENGLISH), SettingValues.alwaysExternal);
        } catch (MalformedURLException e) {
            return false;
        }
    }

    public static SharedPreferences filters;

    public static boolean doesMatch(Submission s, String baseSubreddit, boolean ignore18) {
        if (Hidden.id.contains(s.getFullName()))
            return true; // if it's hidden we're not going to show it regardless

        String title = s.getTitle();
        String body = s.getSelftext();
        String domain = s.getUrl();
        String subreddit = s.getSubredditName();
        String flair =
                s.getSubmissionFlair().getText() != null ? s.getSubmissionFlair().getText() : "";

        if (contains(title, SettingValues.titleFilters, false)) return true;

        if (contains(body, SettingValues.textFilters, false)) return true;

        if (contains(s.getAuthor(), SettingValues.userFilters, false)) return true;

        try {
            if (isDomain(domain.toLowerCase(Locale.ENGLISH), SettingValues.domainFilters))
                return true;
        } catch (MalformedURLException ignored) {
        }

        if (!subreddit.equalsIgnoreCase(baseSubreddit)) {
            if (SettingValues.subredditFilterPrefixMatching && subreddit.length() >= 6) {
                String lowerSubreddit = subreddit.toLowerCase();
                if (SettingValues.subredditFilters.stream()
                        .anyMatch(filter -> lowerSubreddit.startsWith(filter))) {
                    return true;
                }
            } else {
                if (contains(subreddit, SettingValues.subredditFilters, true)) {
                    return true;
                }
            }
        }

        boolean contentMatch = false;

        if (baseSubreddit == null || baseSubreddit.isEmpty()) {
            baseSubreddit = "frontpage";
        }

        baseSubreddit = baseSubreddit.toLowerCase(Locale.ENGLISH);
        boolean albums = isAlbum(baseSubreddit);
        boolean galleries = isGallery(baseSubreddit);
        boolean gifs = isGif(baseSubreddit);
        boolean images = isImage(baseSubreddit);
        boolean links = isLink(baseSubreddit);
        boolean selftexts = isSelftext(baseSubreddit);
        boolean tumblrs = isTumblr(baseSubreddit);
        boolean videos = isVideo(baseSubreddit);
        boolean nsfwGalleries = isNsfwGallery(baseSubreddit);
        boolean nsfwGifs = isNsfwGif(baseSubreddit);
        boolean nsfwImages = isNsfwImage(baseSubreddit);
        boolean nsfwLinks = isNsfwLink(baseSubreddit);
        boolean nsfwSelftexts = isNsfwSelftext(baseSubreddit);
        boolean nsfwTumblrs = isNsfwTumblr(baseSubreddit);
        boolean nsfwVideos = isNsfwVideo(baseSubreddit);

        ContentType.Type contentType = ContentType.getContentType(s);

        // Handle NSFW content types
        if (s.isNsfw() && SettingValues.showNSFWContent) {
            if (ignore18) {
                contentMatch = false;
            } else {
                switch (contentType) {
                    case DEVIANTART:
                    case GIF:
                        if (nsfwGifs) {
                            contentMatch = true;
                        }
                        break;
                    case IMGUR:
                    case XKCD:
                    case IMAGE:
                        if (nsfwImages) {
                            contentMatch = true;
                        }
                        break;
                    case REDDIT:
                    case EMBEDDED:
                    case LINK:
                        if (nsfwLinks) {
                            contentMatch = true;
                        }
                        break;
                    case NONE:
                    case SELF:
                        if (nsfwSelftexts) {
                            contentMatch = true;
                        }
                        break;
                    case REDDIT_GALLERY:
                        if (nsfwGalleries) {
                            contentMatch = true;
                        }
                        break;
                    case TUMBLR:
                        if (nsfwTumblrs) {
                            contentMatch = true;
                        }
                        break;
                    case VREDDIT_REDIRECT:
                    case STREAMABLE:
                    case VIDEO:
                        if (nsfwVideos) {
                            contentMatch = true;
                        }
                        break;
                }
            }
        } else {
            if (s.isNsfw()) {
                contentMatch = true;
            } else {
                // Handle regular content types
                switch (contentType) {
                    case ALBUM:
                        if (albums) {
                            contentMatch = true;
                        }
                        break;
                    case DEVIANTART:
                    case GIF:
                        if (gifs) {
                            contentMatch = true;
                        }
                        break;
                    case IMGUR:
                    case XKCD:
                    case IMAGE:
                        if (images) {
                            contentMatch = true;
                        }
                        break;
                    case REDDIT:
                    case EMBEDDED:
                    case LINK:
                        if (links) {
                            contentMatch = true;
                        }
                        break;
                    case NONE:
                    case SELF:
                        if (selftexts) {
                            contentMatch = true;
                        }
                        break;
                    case REDDIT_GALLERY:
                        if (galleries) {
                            contentMatch = true;
                        }
                        break;
                    case TUMBLR:
                        if (tumblrs) {
                            contentMatch = true;
                        }
                        break;
                    case VREDDIT_REDIRECT:
                    case STREAMABLE:
                    case VIDEO:
                        if (videos) {
                            contentMatch = true;
                        }
                        break;
                }
            }
        }

        if (!flair.isEmpty())
            for (String flairText : SettingValues.flairFilters) {
                if (flairText.toLowerCase(Locale.ENGLISH).startsWith(baseSubreddit)) {
                    String[] split = flairText.split(":");
                    if (split[0].equalsIgnoreCase(baseSubreddit)) {
                        if (flair.equalsIgnoreCase(split[1].trim())) {
                            contentMatch = true;
                            break;
                        }
                    }
                }
            }

        return contentMatch;
    }

    public static boolean doesMatch(Submission s) {
        String title = s.getTitle();
        String body = s.getSelftext();
        String domain = s.getUrl();
        String subreddit = s.getSubredditName();

        boolean domainc = false;

        boolean titlec = contains(title, SettingValues.titleFilters, false);

        boolean bodyc = contains(body, SettingValues.textFilters, false);

        try {
            domainc = isDomain(domain.toLowerCase(Locale.ENGLISH), SettingValues.domainFilters);
        } catch (MalformedURLException ignored) {
        }

        boolean subredditc =
                subreddit != null
                        && !subreddit.isEmpty()
                        && contains(subreddit, SettingValues.subredditFilters, true);

        return (titlec || bodyc || domainc || subredditc);
    }

    public static void setChosen(boolean[] values, String subreddit) {
        subreddit = subreddit.toLowerCase(Locale.ENGLISH);

        // Create a list of filter keys
        String[] filterKeys = {
            subreddit + "_albumsFilter",
            subreddit + "_galleriesFilter",
            subreddit + "_gifsFilter",
            subreddit + "_imagesFilter",
            subreddit + "_linksFilter",
            subreddit + "_selftextsFilter",
            subreddit + "_tumblrsFilter",
            subreddit + "_videosFilter"
        };

        // Add NSFW filter keys if needed
        String[] nsfwFilterKeys = null;
        if (values.length > 8 && SettingValues.showNSFWContent) {
            nsfwFilterKeys = new String[] {
                subreddit + "_nsfwAlbumsFilter",
                subreddit + "_nsfwGalleriesFilter",
                subreddit + "_nsfwGifsFilter",
                subreddit + "_nsfwImagesFilter",
                subreddit + "_nsfwLinksFilter",
                subreddit + "_nsfwSelftextsFilter",
                subreddit + "_nsfwTumblrsFilter",
                subreddit + "_nsfwVideosFilter"
            };
        }

        if (SettingValues.subredditFiltersTillRestart) {
            // Store in memory
            for (int i = 0; i < filterKeys.length; i++) {
                memoryContentFilters.put(filterKeys[i], values[i]);
            }

            if (nsfwFilterKeys != null) {
                for (int i = 0; i < nsfwFilterKeys.length; i++) {
                    memoryContentFilters.put(nsfwFilterKeys[i], values[i + 8]);
                }
            }
        } else {
            // Store in SharedPreferences
            SharedPreferences.Editor e = filters.edit();
            for (int i = 0; i < filterKeys.length; i++) {
                e.putBoolean(filterKeys[i], values[i]);
            }

            if (nsfwFilterKeys != null) {
                for (int i = 0; i < nsfwFilterKeys.length; i++) {
                    e.putBoolean(nsfwFilterKeys[i], values[i + 8]);
                }
            }
            e.apply();
        }
    }

    public static boolean isAlbum(String baseSubreddit) {
        if (SettingValues.subredditFiltersTillRestart) {
            return memoryContentFilters.getOrDefault(baseSubreddit + "_albumsFilter", false);
        } else {
            return filters.getBoolean(baseSubreddit + "_albumsFilter", false);
        }
    }

    public static boolean isGallery(String baseSubreddit) {
        if (SettingValues.subredditFiltersTillRestart) {
            return memoryContentFilters.getOrDefault(baseSubreddit + "_galleriesFilter", false);
        } else {
            return filters.getBoolean(baseSubreddit + "_galleriesFilter", false);
        }
    }

    public static boolean isGif(String baseSubreddit) {
        if (SettingValues.subredditFiltersTillRestart) {
            return memoryContentFilters.getOrDefault(baseSubreddit + "_gifsFilter", false);
        } else {
            return filters.getBoolean(baseSubreddit + "_gifsFilter", false);
        }
    }

    public static boolean isImage(String baseSubreddit) {
        if (SettingValues.subredditFiltersTillRestart) {
            return memoryContentFilters.getOrDefault(baseSubreddit + "_imagesFilter", false);
        } else {
            return filters.getBoolean(baseSubreddit + "_imagesFilter", false);
        }
    }

    public static boolean isLink(String baseSubreddit) {
        if (SettingValues.subredditFiltersTillRestart) {
            return memoryContentFilters.getOrDefault(baseSubreddit + "_linksFilter", false);
        } else {
            return filters.getBoolean(baseSubreddit + "_linksFilter", false);
        }
    }

    public static boolean isSelftext(String baseSubreddit) {
        if (SettingValues.subredditFiltersTillRestart) {
            return memoryContentFilters.getOrDefault(baseSubreddit + "_selftextsFilter", false);
        } else {
            return filters.getBoolean(baseSubreddit + "_selftextsFilter", false);
        }
    }

    public static boolean isTumblr(String baseSubreddit) {
        if (SettingValues.subredditFiltersTillRestart) {
            return memoryContentFilters.getOrDefault(baseSubreddit + "_tumblrsFilter", false);
        } else {
            return filters.getBoolean(baseSubreddit + "_tumblrsFilter", false);
        }
    }

    public static boolean isVideo(String baseSubreddit) {
        if (SettingValues.subredditFiltersTillRestart) {
            return memoryContentFilters.getOrDefault(baseSubreddit + "_videosFilter", false);
        } else {
            return filters.getBoolean(baseSubreddit + "_videosFilter", false);
        }
    }

    public static boolean isNsfwAlbum(String baseSubreddit) {
        if (SettingValues.subredditFiltersTillRestart) {
            return memoryContentFilters.getOrDefault(baseSubreddit + "_nsfwAlbumsFilter", false);
        } else {
            return filters.getBoolean(baseSubreddit + "_nsfwAlbumsFilter", false);
        }
    }

    public static boolean isNsfwGallery(String baseSubreddit) {
        if (SettingValues.subredditFiltersTillRestart) {
            return memoryContentFilters.getOrDefault(baseSubreddit + "_nsfwGalleriesFilter", false);
        } else {
            return filters.getBoolean(baseSubreddit + "_nsfwGalleriesFilter", false);
        }
    }

    public static boolean isNsfwGif(String baseSubreddit) {
        if (SettingValues.subredditFiltersTillRestart) {
            return memoryContentFilters.getOrDefault(baseSubreddit + "_nsfwGifsFilter", false);
        } else {
            return filters.getBoolean(baseSubreddit + "_nsfwGifsFilter", false);
        }
    }

    public static boolean isNsfwImage(String baseSubreddit) {
        if (SettingValues.subredditFiltersTillRestart) {
            return memoryContentFilters.getOrDefault(baseSubreddit + "_nsfwImagesFilter", false);
        } else {
            return filters.getBoolean(baseSubreddit + "_nsfwImagesFilter", false);
        }
    }

    public static boolean isNsfwLink(String baseSubreddit) {
        if (SettingValues.subredditFiltersTillRestart) {
            return memoryContentFilters.getOrDefault(baseSubreddit + "_nsfwLinksFilter", false);
        } else {
            return filters.getBoolean(baseSubreddit + "_nsfwLinksFilter", false);
        }
    }

    public static boolean isNsfwSelftext(String baseSubreddit) {
        if (SettingValues.subredditFiltersTillRestart) {
            return memoryContentFilters.getOrDefault(baseSubreddit + "_nsfwSelftextsFilter", false);
        } else {
            return filters.getBoolean(baseSubreddit + "_nsfwSelftextsFilter", false);
        }
    }

    public static boolean isNsfwTumblr(String baseSubreddit) {
        if (SettingValues.subredditFiltersTillRestart) {
            return memoryContentFilters.getOrDefault(baseSubreddit + "_nsfwTumblrsFilter", false);
        } else {
            return filters.getBoolean(baseSubreddit + "_nsfwTumblrsFilter", false);
        }
    }

    public static boolean isNsfwVideo(String baseSubreddit) {
        if (SettingValues.subredditFiltersTillRestart) {
            return memoryContentFilters.getOrDefault(baseSubreddit + "_nsfwVideosFilter", false);
        } else {
            return filters.getBoolean(baseSubreddit + "_nsfwVideosFilter", false);
        }
    }
}
