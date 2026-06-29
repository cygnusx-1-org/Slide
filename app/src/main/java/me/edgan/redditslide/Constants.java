package me.edgan.redditslide;

import me.edgan.redditslide.util.DisplayUtil;

/** Constants used throughout the app */
public class Constants {
    public static final int DEFAULT_THEME_TYPE = 2;
    public static final String DEFAULT_THEME = "amoled_amber";

    /** Default paginator page size, matching JRAW's DEFAULT_LIMIT */
    public static final int DEFAULT_PAGINATOR_LIMIT = 25;

    /** Number of new subreddits resolved per scroll batch for the Discover "Trending" list */
    public static final int TRENDING_BATCH_SIZE = 10;

    /**
     * Thread pool size for the shared Universal Image Loader instances (used by feed, gallery,
     * album, and flair image loading). This is the maximum number of concurrent image loads.
     */
    public static final int IMAGE_LOADER_THREAD_POOL_SIZE = 8;

    /**
     * This is the estimated height of the Tabs view mode in dp. Use this for calculating the
     * SwipeToRefresh (PTR) progresses indicator offset when using "Tabs" view mode.
     */
    public static final int TAB_HEADER_VIEW_OFFSET = DisplayUtil.dpToPxVertical(108);

    /**
     * This is the estimated height of the toolbar height in dp. Use this for calculating the
     * SwipeToRefresh (PTR) progresses indicator offset when using "Single" view mode.
     */
    public static final int SINGLE_HEADER_VIEW_OFFSET = DisplayUtil.dpToPxVertical(56);

    /**
     * These offsets are used for the SwipeToRefresh (PTR) progress indicator. The TOP offset is
     * used for the starting point of the indicator (underneath the toolbar). The BOTTOM offset is
     * used for the end point of the indicator (below the toolbar). This is used whenever we call
     * mSwipeRefreshLayout.setProgressViewOffset().
     */
    public static final int PTR_OFFSET_TOP = DisplayUtil.dpToPxVertical(40);

    public static final int PTR_OFFSET_BOTTOM = DisplayUtil.dpToPxVertical(18);

    // 1000 * 60 * 50 = 50 minutes in milliseconds
    public static final int EXPIRES_VALUE = 3000000;

    /**
     * Drawer swipe edge (navdrawer). The higher the value, the more sensitive the navdrawer swipe
     * area becomes. This is a percentage of the screen width.
     */
    public static final float DRAWER_SWIPE_EDGE = 0.07f;

    public static final float DRAWER_SWIPE_EDGE_TABLET = 0.03f;

    /** The client ID to use when making requests to the Imgur API */
    public static final String IMGUR_CLIENT_ID = "098247aec5ce437";

    public static final String TUMBLR_API_KEY =
            "qr0mPKRNb46Q5HwjkQjALEsA7m4Ub5MKvwv2qXmGHQJjG2B3gl";

    public static final int SUBREDDIT_SEARCH_METHOD_DRAWER = 1;
    public static final int SUBREDDIT_SEARCH_METHOD_TOOLBAR = 2;
    public static final int SUBREDDIT_SEARCH_METHOD_BOTH = 3;

    public static final int FAB_DISMISS = 1;
    public static final int FAB_POST = 2;
    public static final int FAB_SEARCH = 3;

    /** Reddit OAuth credentials */
    private static final String REDDIT_CLIENT_ID_DEFAULT = "yH0aTnJEt6qUgGn835B4vg";

    /**
     * Gets the Reddit client ID to use for authentication. Returns the user-specified override if
     * set, otherwise returns the default.
     */
    public static String getClientId() {
        // Make sure settings are loaded
        if (SettingValues.prefs == null || !overridesEnabled()) {
            return REDDIT_CLIENT_ID_DEFAULT;
        }

        String override =
                SettingValues.prefs.getString(SettingValues.PREF_REDDIT_CLIENT_ID_OVERRIDE, "");
        return !override.isEmpty() ? override : REDDIT_CLIENT_ID_DEFAULT;
    }

    public static final String REDDIT_REDIRECT_URL = "redreader://rr_oauth_redir";

    /**
     * Gets the Reddit redirect URL to use for authentication. Returns the user-specified override
     * if set, otherwise returns the default.
     */
    public static String getRedirectUrl() {
        if (SettingValues.prefs == null || !overridesEnabled()) {
            return REDDIT_REDIRECT_URL;
        }

        String override =
                SettingValues.prefs.getString(
                        SettingValues.PREF_REDDIT_REDIRECT_URI_OVERRIDE, "");
        return !override.isEmpty() ? override : REDDIT_REDIRECT_URL;
    }

    /**
     * Gets the Reddit user agent string to use for API requests. Returns the user-specified
     * override if set, otherwise returns the default.
     */
    public static String getUserAgent() {
        if (SettingValues.prefs != null && overridesEnabled()) {
            String override =
                    SettingValues.prefs.getString(
                            SettingValues.PREF_REDDIT_USER_AGENT_OVERRIDE, "");
            if (!override.isEmpty()) {
                return override;
            }
        }

        return "org.quantumbadger.redreader/1.25.2";
    }

    /**
     * Whether the user has opted in to using their own Reddit client ID, redirect URI, and user
     * agent overrides. Defaults to false so the app uses its built-in defaults unless the user
     * explicitly enables overrides.
     */
    public static boolean overridesEnabled() {
        if (SettingValues.prefs == null) {
            return false;
        }
        return SettingValues.prefs.getBoolean(SettingValues.PREF_REDDIT_ENABLE_OVERRIDES, false);
    }

    public enum BackButtonBehaviorOptions {
        Default(0),
        ConfirmExit(1),
        OpenDrawer(2),
        GotoFirst(3);

        private final int mValue;

        BackButtonBehaviorOptions(int value) {
            mValue = value;
        }

        public int getValue() {
            return mValue;
        }
    }
}
