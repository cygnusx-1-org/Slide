package me.edgan.redditslide.Activities;

import android.app.ActivityManager;
import android.content.res.Configuration;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.annotation.IdRes;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.appcompat.widget.Toolbar;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.drawerlayout.widget.DrawerLayout;

import me.edgan.redditslide.ForceTouch.PeekViewActivity;
import me.edgan.redditslide.R;
import me.edgan.redditslide.Reddit;
import me.edgan.redditslide.SettingValues;
import me.edgan.redditslide.SwipeLayout.SwipeBackLayout;
import me.edgan.redditslide.SwipeLayout.Utils;
import me.edgan.redditslide.SwipeLayout.app.SwipeBackActivityBase;
import me.edgan.redditslide.SwipeLayout.app.SwipeBackActivityHelper;
import me.edgan.redditslide.Visuals.ColorPreferences;
import me.edgan.redditslide.Visuals.FontPreferences;
import me.edgan.redditslide.Visuals.Palette;
import me.edgan.redditslide.util.GifUtils;

import java.util.Locale;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * This is an activity which is the base for most of Slide's activities. It has support for handling
 * of swiping, setting up the AppBar (toolbar), and coloring of applicable views.
 */
public class BaseActivity extends PeekViewActivity implements SwipeBackActivityBase {
    @Nullable public Toolbar mToolbar;
    protected SwipeBackActivityHelper mHelper;
    protected boolean overrideRedditSwipeAnywhere = false;
    protected boolean enableSwipeBackLayout = true;
    protected boolean overrideSwipeFromAnywhere = false;
    protected boolean verticalExit = false;
    protected GifUtils.AsyncLoadGif currentGif;

    /**
     * Subclasses that want their content to draw behind the system bars (full-bleed media
     * viewers) can set this to true before onPostCreate() runs.
     */
    protected boolean disableEdgeToEdgePadding = false;

    @Nullable private View mStatusBarScrim;
    @Nullable private View mNavBarScrim;
    private int mSystemBarColor;
    private boolean mSystemBarColorSet = false;
    private final Map<View, int[]> mInitialPadding = new WeakHashMap<>();

    /** Enable fullscreen immersive mode if setting is checked */
    @Override
    public void onWindowFocusChanged(final boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (SettingValues.immersiveMode) {
            if (hasFocus) {
                hideDecor();
            }
        }
        if (enableSwipeBackLayout) {
            Utils.convertActivityToTranslucent(this);
        }
    }

    public void hideDecor() {
        try {
            if (SettingValues.immersiveMode) {
                final View decorView = getWindow().getDecorView();
                decorView.setSystemUiVisibility(
                        View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                                | View.SYSTEM_UI_FLAG_FULLSCREEN
                                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
                decorView.setOnSystemUiVisibilityChangeListener(
                        new View.OnSystemUiVisibilityChangeListener() {
                            @Override
                            public void onSystemUiVisibilityChange(int visibility) {
                                if ((visibility) == 0) {
                                    decorView.setSystemUiVisibility(
                                            View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                                                    | View.SYSTEM_UI_FLAG_FULLSCREEN);
                                } else {
                                    decorView.setSystemUiVisibility(
                                            View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                                                    | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                                                    | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                                                    | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                                                    | View.SYSTEM_UI_FLAG_FULLSCREEN
                                                    | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
                                }
                            }
                        });
            }
        } catch (Exception ignored) {

        }
    }

    public void showDecor() {
        try {
            if (!SettingValues.immersiveMode) {
                final View decorView = getWindow().getDecorView();
                decorView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_VISIBLE);
                decorView.setOnSystemUiVisibilityChangeListener(null);
            }
        } catch (Exception ignored) {
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();

        if (id == android.R.id.home) {
            try {
                getOnBackPressedDispatcher().onBackPressed();
            } catch (IllegalStateException ignored) {

            }
        }

        return super.onOptionsItemSelected(item);
    }

    public boolean shouldInterceptAlways = false;

    /** Force English locale if setting is checked */
    public void applyOverrideLanguage() {
        if (SettingValues.overrideLanguage) {
            Locale locale = new Locale("en", "US");
            Locale.setDefault(locale);
            Configuration config = new Configuration();
            config.locale = locale;
            getBaseContext()
                    .getResources()
                    .updateConfiguration(
                            config, getBaseContext().getResources().getDisplayMetrics());
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        applyOverrideLanguage();

        super.onCreate(savedInstanceState);
        setAutofill();

        /**
         * Enable fullscreen immersive mode if setting is checked
         *
         * <p>Adding this check in the onCreate method prevents the status/nav bars from appearing
         * briefly when changing from one activity to another
         */
        hideDecor();

        if (enableSwipeBackLayout) {
            mHelper = new SwipeBackActivityHelper(this);
            mHelper.onActivityCreate();

            if (SettingValues.swipeAnywhere || overrideRedditSwipeAnywhere) {
                if (overrideSwipeFromAnywhere) {
                    shouldInterceptAlways = true;
                } else {
                    if (verticalExit) {
                        mHelper.getSwipeBackLayout()
                                .setEdgeTrackingEnabled(
                                        SwipeBackLayout.EDGE_LEFT
                                                | SwipeBackLayout.EDGE_BOTTOM
                                                | SwipeBackLayout.EDGE_TOP);
                    } else {
                        mHelper.getSwipeBackLayout()
                                .setEdgeTrackingEnabled(
                                        SwipeBackLayout.EDGE_LEFT | SwipeBackLayout.EDGE_TOP);
                    }
                    mHelper.getSwipeBackLayout().setFullScreenSwipeEnabled(true);
                }
            } else {
                shouldInterceptAlways = true;
            }
        }
    }

    protected void setAutofill() {
        getWindow()
                .getDecorView()
                .setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS);
    }

    @Override
    protected void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);
        if (enableSwipeBackLayout) mHelper.onPostCreate();
        setupEdgeToEdge();
    }

    /**
     * Handles window insets manually now that edge-to-edge is enforced (targetSdk 36 ignores
     * windowOptOutEdgeToEdgeEnforcement on Android 16+). Pads the activity content by the system
     * bar insets and draws colored scrims behind the status and navigation bars so activities
     * keep the same look they had before enforcement. Below API 35 the decor still fits system
     * windows, so this is skipped and the legacy setStatusBarColor() path applies.
     */
    private void setupEdgeToEdge() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            return;
        }
        if (disableEdgeToEdgePadding) {
            return;
        }
        final FrameLayout contentFrame =
                (FrameLayout) getWindow().getDecorView().findViewById(android.R.id.content);
        if (contentFrame == null) {
            return;
        }

        mStatusBarScrim = new View(this);
        contentFrame.addView(
                mStatusBarScrim,
                new FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT, 0, Gravity.TOP));
        mNavBarScrim = new View(this);
        contentFrame.addView(
                mNavBarScrim,
                new FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT, 0, Gravity.BOTTOM));
        applyScrimColors();

        ViewCompat.setOnApplyWindowInsetsListener(
                contentFrame,
                (v, windowInsets) -> {
                    Insets bars =
                            windowInsets.getInsets(
                                    WindowInsetsCompat.Type.systemBars()
                                            | WindowInsetsCompat.Type.displayCutout());
                    Insets ime = windowInsets.getInsets(WindowInsetsCompat.Type.ime());
                    int bottom = Math.max(bars.bottom, ime.bottom);
                    for (int i = 0; i < contentFrame.getChildCount(); i++) {
                        View child = contentFrame.getChildAt(i);
                        if (child == mStatusBarScrim || child == mNavBarScrim) {
                            continue;
                        }
                        if (child instanceof DrawerLayout) {
                            // DrawerLayout ignores padding in its measure/layout pass,
                            // so inset it with margins instead
                            int[] base = mInitialPadding.get(child);
                            if (base == null) {
                                ViewGroup.MarginLayoutParams params =
                                        (ViewGroup.MarginLayoutParams) child.getLayoutParams();
                                base =
                                        new int[] {
                                            params.leftMargin, params.topMargin,
                                            params.rightMargin, params.bottomMargin
                                        };
                                mInitialPadding.put(child, base);
                            }
                            ViewGroup.MarginLayoutParams params =
                                    (ViewGroup.MarginLayoutParams) child.getLayoutParams();
                            params.leftMargin = base[0] + bars.left;
                            params.topMargin = base[1] + bars.top;
                            params.rightMargin = base[2] + bars.right;
                            params.bottomMargin = base[3] + bottom;
                            child.setLayoutParams(params);
                        } else {
                            int[] base = mInitialPadding.get(child);
                            if (base == null) {
                                base =
                                        new int[] {
                                            child.getPaddingLeft(), child.getPaddingTop(),
                                            child.getPaddingRight(), child.getPaddingBottom()
                                        };
                                mInitialPadding.put(child, base);
                            }
                            child.setPadding(
                                    base[0] + bars.left,
                                    base[1] + bars.top,
                                    base[2] + bars.right,
                                    base[3] + bottom);
                        }
                    }
                    setScrimHeight(mStatusBarScrim, bars.top);
                    setScrimHeight(mNavBarScrim, bars.bottom);
                    return WindowInsetsCompat.CONSUMED;
                });
    }

    private static void setScrimHeight(View scrim, int height) {
        ViewGroup.LayoutParams params = scrim.getLayoutParams();
        if (params.height != height) {
            params.height = height;
            scrim.setLayoutParams(params);
        }
    }

    /**
     * Colors the system bar scrims with the color last passed to themeSystemBars(), falling back
     * to the theme's bar colors for activities that never set one.
     */
    private void applyScrimColors() {
        if (mStatusBarScrim == null || mNavBarScrim == null) {
            return;
        }
        int color =
                mSystemBarColorSet
                        ? mSystemBarColor
                        : opaqueOrBlack(resolveThemeColor(android.R.attr.statusBarColor));
        if (SettingValues.alwaysBlackStatusbar) {
            color = Color.BLACK;
        }
        mStatusBarScrim.setBackgroundColor(color);
        mNavBarScrim.setBackgroundColor(
                SettingValues.colorNavBar
                        ? color
                        : opaqueOrBlack(resolveThemeColor(android.R.attr.navigationBarColor)));
    }

    /**
     * The system bar scrims must be opaque so they hide the content behind them. Under edge-to-edge
     * enforcement (API 35+) the framework default for android:navigationBarColor/statusBarColor is
     * transparent, and our themes never override it, so resolveThemeColor() returns a fully
     * transparent color. A transparent scrim paints nothing, which let the post list and FAB bleed
     * through the navigation bar area and flicker. Fall back to black in that case.
     */
    private static int opaqueOrBlack(int color) {
        return Color.alpha(color) == 0 ? Color.BLACK : color;
    }

    private int resolveThemeColor(int attr) {
        TypedValue typedValue = new TypedValue();
        if (getTheme().resolveAttribute(attr, typedValue, true)) {
            return typedValue.data;
        }
        return Color.BLACK;
    }

    @Override
    public View findViewById(int id) {
        View v = super.findViewById(id);
        if (v == null && mHelper != null) return mHelper.findViewById(id);
        return v;
    }

    @Override
    public SwipeBackLayout getSwipeBackLayout() {
        if (enableSwipeBackLayout) {
            return mHelper.getSwipeBackLayout();
        } else {
            return null;
        }
    }

    @Override
    public void setSwipeBackEnable(boolean enable) {
        if (enableSwipeBackLayout) getSwipeBackLayout().setEnableGesture(enable);
    }

    @Override
    public void scrollToFinishActivity() {
        if (enableSwipeBackLayout) {
            Utils.convertActivityToTranslucent(this);
            getSwipeBackLayout().scrollToFinishActivity();
        }
    }

    /** Disables the Swipe-Back-Layout. Should be called before calling super.onCreate() */
    protected void disableSwipeBackLayout() {
        enableSwipeBackLayout = false;
    }

    protected void overrideSwipeFromAnywhere() {
        overrideSwipeFromAnywhere = true;
    }

    protected void overrideRedditSwipeAnywhere() {
        overrideRedditSwipeAnywhere = true;
    }

    /** Applies the activity's base color theme. Should be called before inflating any layouts. */
    protected void applyColorTheme() {
        getTheme().applyStyle(new FontPreferences(this).getCommentFontStyle().getResId(), true);
        getTheme().applyStyle(new FontPreferences(this).getPostFontStyle().getResId(), true);
        getTheme().applyStyle(new ColorPreferences(this).getFontStyle().getBaseId(), true);
    }

    /**
     * Applies the activity's base color theme based on the theme of a specific subreddit. Should be
     * called before inflating any layouts.
     *
     * @param subreddit The subreddit to base the theme on
     */
    protected void applyColorTheme(String subreddit) {
        getTheme().applyStyle(new FontPreferences(this).getPostFontStyle().getResId(), true);
        getTheme().applyStyle(new ColorPreferences(this).getThemeSubreddit(subreddit), true);
        getTheme().applyStyle(new FontPreferences(this).getCommentFontStyle().getResId(), true);
    }

    /**
     * Applies the activity's base color theme based on the theme of a specific subreddit. Should be
     * called before inflating any layouts.
     *
     * <p>This will take the accent colors from the sub theme but return the AMOLED with contrast
     * base theme.
     *
     * @param subreddit The subreddit to base the theme on
     */
    protected void applyDarkColorTheme(String subreddit) {
        getTheme().applyStyle(new FontPreferences(this).getPostFontStyle().getResId(), true);
        getTheme().applyStyle(new ColorPreferences(this).getDarkThemeSubreddit(subreddit), true);
        getTheme().applyStyle(new FontPreferences(this).getCommentFontStyle().getResId(), true);
    }

    @Override
    public void onResume() {
        super.onResume();
        Reddit.setDefaultErrorHandler(this); // set defualt reddit api issue handler
        hideDecor();
        if (currentGif != null) {
            currentGif.onResume();
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Reddit.setDefaultErrorHandler(null); // remove defualt reddit api issue handler (mem leaks)
    }

    /**
     * Sets up the activity's support toolbar and colorizes the status bar.
     *
     * @param toolbar The toolbar's id
     * @param title String resource for the toolbar's title
     * @param enableUpButton Whether or not the toolbar should have up navigation
     */
    protected void setupAppBar(
            @IdRes int toolbar,
            @StringRes int title,
            boolean enableUpButton,
            boolean colorToolbar) {
        setupAppBar(toolbar, getString(title), enableUpButton, colorToolbar);
    }

    /**
     * Sets up the activity's support toolbar and colorizes the status bar.
     *
     * @param toolbar The toolbar's id
     * @param title String to be set as the toolbar title
     * @param enableUpButton Whether or not the toolbar should have up navigation
     */
    protected void setupAppBar(
            @IdRes int toolbar, String title, boolean enableUpButton, boolean colorToolbar) {
        int systemBarColor = Palette.getStatusBarColor();
        mToolbar = (Toolbar) findViewById(toolbar);

        if (colorToolbar) {
            mToolbar.setBackgroundColor(Palette.getDefaultColor());
        }
        setSupportActionBar(mToolbar);

        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(enableUpButton);
            getSupportActionBar().setTitle(title);
        }

        themeSystemBars(systemBarColor);
        setRecentBar(title, systemBarColor);
    }

    /**
     * Sets up the activity's support toolbar and colorizes the status bar to a specific color
     *
     * @param toolbar The toolbar's id
     * @param title String to be set as the toolbar title
     * @param enableUpButton Whether or not the toolbar should have up navigation
     * @param color Color to color the tab bar
     */
    protected void setupAppBar(
            @IdRes int toolbar,
            String title,
            boolean enableUpButton,
            int color,
            @IdRes int appbar) {
        int systemBarColor = Palette.getDarkerColor(color);
        mToolbar = (Toolbar) findViewById(toolbar);
        findViewById(appbar).setBackgroundColor(color);

        setSupportActionBar(mToolbar);

        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(enableUpButton);
            getSupportActionBar().setTitle(title);
        }

        themeSystemBars(systemBarColor);
        setRecentBar(title, systemBarColor);
    }

    /**
     * Sets up the activity's support toolbar and colorizes the status bar. Applies color theming
     * based on the theme for the username specified.
     *
     * @param toolbar The toolbar's id
     * @param title String to be set as the toolbar title
     * @param enableUpButton Whether or not the toolbar should have up navigation
     * @param username The username to base the theme on
     */
    protected void setupUserAppBar(
            @IdRes int toolbar, @Nullable String title, boolean enableUpButton, String username) {
        int systemBarColor = Palette.getUserStatusBarColor(username);
        mToolbar = (Toolbar) findViewById(toolbar);
        mToolbar.setBackgroundColor(Palette.getColorUser(username));
        setSupportActionBar(mToolbar);

        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(enableUpButton);
            if (title != null) {
                getSupportActionBar().setTitle(title);
            }
        }

        themeSystemBars(systemBarColor);
        setRecentBar(title, systemBarColor);
    }

    /**
     * Sets up the activity's support toolbar and colorizes the status bar. Applies color theming
     * based on the theme for the subreddit specified.
     *
     * @param toolbar The toolbar's id
     * @param title String to be set as the toolbar title
     * @param enableUpButton Whether or not the toolbar should have up navigation
     * @param subreddit The subreddit to base the theme on
     */
    protected void setupSubredditAppBar(
            @IdRes int toolbar, String title, boolean enableUpButton, String subreddit) {
        mToolbar = (Toolbar) findViewById(toolbar);
        mToolbar.setBackgroundColor(Palette.getColor(subreddit));
        setSupportActionBar(mToolbar);

        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(enableUpButton);
            getSupportActionBar().setTitle(title);
        }

        themeSystemBars(subreddit);
        setRecentBar(title, Palette.getSubredditStatusBarColor(subreddit));
    }

    /**
     * Sets the status bar and navigation bar color for the activity based on a specific subreddit.
     *
     * @param subreddit The subreddit to base the color on.
     */
    public void themeSystemBars(String subreddit) {
        themeSystemBars(Palette.getSubredditStatusBarColor(subreddit));
    }

    /**
     * Sets the status bar and navigation bar color for the activity
     *
     * @param color The color to tint the bars with
     */
    protected void themeSystemBars(int color) {
        if (SettingValues.alwaysBlackStatusbar) {
            color = Color.BLACK;
        }

        mSystemBarColor = color;
        mSystemBarColorSet = true;

        // No-ops under edge-to-edge enforcement (API 35+); the scrims take over there
        getWindow().setStatusBarColor(color);
        if (SettingValues.colorNavBar) {
            getWindow().setNavigationBarColor(color);
        }

        applyScrimColors();
    }

    /**
     * Sets the title and color of the recent bar based on the subreddit
     *
     * @param subreddit Name of the subreddit
     */
    public void setRecentBar(String subreddit) {
        setRecentBar(subreddit, Palette.getColor(subreddit));
    }

    /**
     * Sets the title in the recent overview with the given title and the default color
     *
     * @param title Title as string for the recent app bar
     * @param color Color for the recent app bar
     */
    public void setRecentBar(@Nullable String title, int color) {
        if (title == null || title.isEmpty()) {
            title = getString(R.string.app_name);
        }
        setRecentBarTaskDescription(title, color);
    }

    private void setRecentBarTaskDescription(@Nullable String title, int color) {
        int icon =
                title.equalsIgnoreCase("androidcirclejerk")
                        ? R.drawable.matiasduarte
                        : R.drawable.ic_launcher;

        setTaskDescription(new ActivityManager.TaskDescription(title, icon, color));
    }

    @Override
    public void onPause() {
        super.onPause();
        if (currentGif != null) {
            currentGif.onPause();
        }
    }
}
