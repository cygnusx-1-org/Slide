package me.edgan.redditslide;

import me.edgan.redditslide.util.DisplayUtil;

/** Constants used throughout the app */
public class Constants {
    public static final int DEFAULT_THEME_TYPE = 2;
    public static final String DEFAULT_THEME = "amoled_amber";

    /** Default paginator page size, matching JRAW's DEFAULT_LIMIT */
    public static final int DEFAULT_PAGINATOR_LIMIT = 25;

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
    private static final String REDDIT_CLIENT_ID_DEFAULT = "KI2Nl9A_ouG9Qw";

    /**
     * Gets the Reddit client ID to use for authentication. Returns the user-specified override if
     * set, otherwise returns the default.
     */
    public static String getClientId() {
        // Make sure settings are loaded
        if (SettingValues.prefs == null) {
            return REDDIT_CLIENT_ID_DEFAULT;
        }

        String override =
                SettingValues.prefs.getString(SettingValues.PREF_REDDIT_CLIENT_ID_OVERRIDE, "");
        return !override.isEmpty() ? override : REDDIT_CLIENT_ID_DEFAULT;
    }

    public static final String REDDIT_REDIRECT_URL = "http://www.ccrama.me";

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
