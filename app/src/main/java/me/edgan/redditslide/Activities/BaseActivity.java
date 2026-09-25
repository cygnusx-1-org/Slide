package me.edgan.redditslide.Activities;

import android.app.ActivityManager;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Rect;
import android.os.Build;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.FrameLayout;
import android.widget.ScrollView;
import androidx.annotation.IdRes;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.appcompat.widget.Toolbar;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.widget.NestedScrollView;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.WeakHashMap;
import me.edgan.redditslide.ForceTouch.PeekViewActivity;
import me.edgan.redditslide.HibernateState;
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
import me.edgan.redditslide.handler.ToolbarScrollHideHandler;
import me.edgan.redditslide.util.GifUtils;
import me.edgan.redditslide.util.LogUtil;
import me.edgan.redditslide.util.ScrollAnchor;
import org.jspecify.annotations.NullMarked;

/**
 * This is an activity which is the base for most of Slide's activities. It has support for handling
 * of swiping, setting up the AppBar (toolbar), and coloring of applicable views.
 */
@NullMarked
public class BaseActivity extends PeekViewActivity
        implements SwipeBackActivityBase, HibernateState.Restorable {
    @Nullable public Toolbar mToolbar;
    @SuppressWarnings("NullAway.Init") // assigned in onCreate
    protected SwipeBackActivityHelper mHelper;
    protected boolean overrideRedditSwipeAnywhere = false;

    /** A restored scroll offset waiting for {@link #applyPendingScroll()}; 0 when there is none. */
    private int pendingScrollY;

    /** A restored list anchor waiting for the rows to arrive; NO_POSITION when there is none. */
    private int pendingAnchorPosition = ScrollAnchor.NO_POSITION;

    private int pendingAnchorOffset;

    /**
     * Where the last attempt at {@link #pendingAnchorPosition} left the list when it came up short
     * for want of rows below the anchor, or null when no attempt has. The next attempt waits for
     * the adapter to grow past {@link #anchorShortRowCount}.
     */
    @Nullable private ScrollAnchor anchorLandedShort;

    private int anchorShortRowCount;

    /** The list a restore is holding hidden, and the backstop that shows it regardless. */
    @Nullable private RecyclerView heldList;

    @Nullable private Runnable heldListReveal;

    /**
     * Whether {@link #onCreate} threw the system's saved state away in favour of a snapshot
     * restore, so that {@link #onRestoreInstanceState} has to throw away the matching half.
     *
     * <p>The two are separate mechanisms and only make sense together. onCreate's bundle is what
     * rebuilds the FragmentManager; the view hierarchy is restored later, from the copy the system
     * kept, and ViewPager2's adapter looks its pages up in the FragmentManager while doing it.
     * Dropping only the first left it asking for fragments that were never restored --
     * {@code IllegalStateException: Fragment no longer exists for key f#...} out of
     * {@code FragmentStateAdapter.restoreState}, on every `am kill` resume of a Megareddit.
     */
    private boolean discardedSystemState;

    /**
     * Whether the app bar was scrolled away when this screen was recorded, and whether that is
     * still waiting to be put back.
     *
     * <p>A feed carries this on its own restore, through {@link
     * me.edgan.redditslide.FeedRestoreState}; every other scrolling screen had nothing to carry it
     * at all, so Discover, Inbox and the Multireddit and Megareddit overviews were left with the
     * bar scrolled off and came back with it fully out, covering the rows the position had just
     * been restored for.
     */
    private boolean pendingToolbarHidden;

    private boolean hasPendingToolbar;

    /**
     * The same for the post button, which rides the same scroll. Captured here rather than in the
     * feed's own restore because the screens that go through this one host a different fragment:
     * a Multireddit page is a {@code MultiredditView}, so {@code SubmissionsView}'s FAB handling
     * never applied to it and its button came back showing over the rows it had scrolled clear of.
     */
    private boolean pendingFabHidden;

    private boolean hasPendingFab;

    /**
     * The scroll handlers of every list built on this screen, so a restore can tell them where
     * the bar ended up.
     *
     * <p>All of them, not the visible page's alone: the pages of a tabbed screen share one app
     * bar, so a handler left believing the bar is out while it is hidden puts it back on the
     * user's first scroll of that page. Each holds only views owned by this activity and is
     * dropped with it.
     */
    private final List<ToolbarScrollHideHandler> scrollHideHandlers = new ArrayList<>();

    /**
     * Whether {@link #onPostCreate} has claimed this screen's snapshot yet, and so whether {@link
     * #hasPendingToolbar} can be believed. Before that it is false for every screen, including
     * one about to be told a bar is waiting.
     */
    private boolean pendingToolbarKnown;

    /** How often to look for the list, and for how long, before giving the restore up. */
    private static final long LIST_RESTORE_POLL_MS = 250;

    private static final int LIST_RESTORE_ATTEMPTS = 40;
    protected boolean enableSwipeBackLayout = true;
    protected boolean overrideSwipeFromAnywhere = false;
    protected boolean verticalExit = false;
    // Nothing assigns this -- no subclass sets it either, though it is protected. Both readers
    // (onResume, onPause) already null-check it, so @Nullable states what is actually true.
    @Nullable protected GifUtils.AsyncLoadGif currentGif;

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
            // Decor flags are cosmetic and the window is gone on an
            // activity that is already finishing.
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
            // As in hideDecor: no window left to restore on a
            // finishing activity.
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();

        if (id == android.R.id.home) {
            try {
                getOnBackPressedDispatcher().onBackPressed();
            } catch (IllegalStateException ignored) {
                // The activity is already on its way out, which is what
                // back would have done.
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
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        applyOverrideLanguage();

        // A snapshot restore and the system's own saved state describe the same screen, and the
        // system's is the worse of the two -- so it is dropped rather than merged.
        //
        // Returning to a task whose process was killed hands the activity a savedInstanceState,
        // and FragmentActivity rebuilds the pager's fragments straight out of it. Rebuilt that
        // way they never go through the adapter's getItem, which is the only place the restore
        // args are attached, so the page comes back as the empty shell the dead process saved:
        // right screen, right tab, no posts. Launching after a force-stop has no saved state, so
        // getItem runs and the same screen restores correctly -- which is exactly why `am kill`
        // failed where force-stop passed.
        //
        // Except when the dead process was waiting on a result. The request code a launcher
        // registered is kept only in this state, so dropping it delivered the file the user had
        // just picked to nothing: an import from the picker, with the process reclaimed while the
        // picker was open, silently did nothing.
        discardedSystemState =
                savedInstanceState != null
                        && !awaitsActivityResult(savedInstanceState)
                        && HibernateState.hasPendingRestore(this);
        super.onCreate(discardedSystemState ? null : savedInstanceState);
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

    /**
     * Whether {@code saved} holds a result launch that has not come back yet -- a file picker the
     * process was killed behind. Read from the keys ComponentActivity and its SavedStateRegistry
     * write; were they ever to move, this answers false and the restore behaves as it did.
     */
    private static boolean awaitsActivityResult(Bundle saved) {
        final Bundle registry = saved.getBundle("androidx.lifecycle.BundlableSavedStateRegistry.key");
        final Bundle results =
                registry == null ? null : registry.getBundle("android:support:activity-result");
        final List<String> launched =
                results == null
                        ? null
                        : results.getStringArrayList("KEY_COMPONENT_ACTIVITY_LAUNCHED_KEYS");
        return launched != null && !launched.isEmpty();
    }

    @Override
    protected void onRestoreInstanceState(Bundle savedInstanceState) {
        if (discardedSystemState) {
            // onCreate dropped the FragmentManager half of this; restoring the view half alone
            // would look up pages that no longer exist.
            return;
        }
        super.onRestoreInstanceState(savedInstanceState);
    }

    @Override
    protected void onPostCreate(@Nullable Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);
        if (enableSwipeBackLayout) mHelper.onPostCreate();
        setupEdgeToEdge();
        // Here rather than in onCreate, and that ordering is the point: a screen that needs its
        // state before it builds anything -- a feed choosing which tab to open on -- claims it
        // in its own onCreate, which has already run by now, and a snapshot entry is handed out
        // once. So this picks up exactly the screens that have not claimed one, which is every
        // plain scrolling screen that wants nothing more than to come back where it was.
        final Bundle hibernated = HibernateState.claim(this);
        if (hibernated != null) {
            restoreHibernateState(hibernated);
        }
        pendingToolbarKnown = true;
        if (!hasPendingToolbar) {
            // Collected while that was still unknown, by a screen that built its list in onCreate.
            scrollHideHandlers.clear();
        }
        applyPendingScroll();
        watchScrollingContent();
        watchForListRestore();
    }

    /**
     * Records the scroll position of a plain scrolling screen once it comes to rest.
     *
     * <p>The lists in this app settle through {@code ToolbarScrollHideHandler}, which captures on
     * {@code SCROLL_STATE_IDLE}; a {@code ScrollView} has no idle callback, so a settings screen
     * scrolled and then killed without a pause -- the process taken for memory -- came back at
     * the top. A scroll change that is not followed by another within {@link #SCROLL_SETTLE_MS}
     * is treated as the rest. Reposted rather than debounced with a timestamp so that a long
     * fling costs one capture at its end rather than one per frame.
     */
    private void watchScrollingContent() {
        final View scroller = findScrollingContent();
        if (scroller == null) {
            return;
        }
        final Runnable settled =
                new Runnable() {
                    @Override
                    public void run() {
                        HibernateState.onContentSettled(BaseActivity.this);
                    }
                };
        scroller.setOnScrollChangeListener(
                new View.OnScrollChangeListener() {
                    @Override
                    public void onScrollChange(View v, int x, int y, int oldX, int oldY) {
                        v.removeCallbacks(settled);
                        v.postDelayed(settled, SCROLL_SETTLE_MS);
                    }
                });
    }

    /** How long a ScrollView has to hold still before its position counts as where the user is. */
    private static final long SCROLL_SETTLE_MS = 250;

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
                    if (mStatusBarScrim != null) {
                        setScrimHeight(mStatusBarScrim, bars.top);
                    }
                    if (mNavBarScrim != null) {
                        setScrimHeight(mNavBarScrim, bars.bottom);
                    }
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
            LogUtil.v(
                    "StatusBarColor: applyScrimColors() skipped, scrims not created yet ("
                            + getClass().getSimpleName()
                            + ")");
            return;
        }
        int themeFallback = opaqueOrBlack(resolveThemeColor(android.R.attr.statusBarColor));
        int color = mSystemBarColorSet ? mSystemBarColor : themeFallback;
        // Intermittent grey is usually this fallback firing before themeSystemBars() has run, or
        // alwaysBlackStatusbar forcing black; log enough to tell which branch produced the color.
        LogUtil.v(
                "StatusBarColor: applyScrimColors() ["
                        + getClass().getSimpleName()
                        + "] source="
                        + (mSystemBarColorSet ? "themeSystemBars" : "themeFallback")
                        + " systemBarColorSet="
                        + mSystemBarColorSet
                        + " systemBarColor="
                        + colorHex(mSystemBarColor)
                        + " themeFallback="
                        + colorHex(themeFallback)
                        + " alwaysBlackStatusbar="
                        + SettingValues.alwaysBlackStatusbar
                        + " colorNavBar="
                        + SettingValues.colorNavBar
                        + " -> chosen="
                        + colorHex(color));
        if (SettingValues.alwaysBlackStatusbar) {
            color = Color.BLACK;
        }
        mStatusBarScrim.setBackgroundColor(color);
        mNavBarScrim.setBackgroundColor(
                SettingValues.colorNavBar
                        ? color
                        : opaqueOrBlack(resolveThemeColor(android.R.attr.navigationBarColor)));
    }

    /** Formats a color-int as #AARRGGBB for readable logging of bar colors. */
    private static String colorHex(int color) {
        return String.format(Locale.ENGLISH, "#%08X", color);
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
    @SuppressWarnings("unchecked") // The helper is pre-generics and hands back a bare View.
    public <T extends View> T findViewById(int id) {
        T v = super.findViewById(id);
        if (v == null && mHelper != null) {
            T fromHelper = (T) mHelper.findViewById(id);
            if (fromHelper != null) return fromHelper;
        }
        return v;
    }

    @Override
    public @Nullable SwipeBackLayout getSwipeBackLayout() {
        if (enableSwipeBackLayout) {
            return mHelper.getSwipeBackLayout();
        } else {
            return null;
        }
    }

    @Override
    public void setSwipeBackEnable(boolean enable) {
        if (enableSwipeBackLayout) java.util.Objects.requireNonNull(getSwipeBackLayout()).setEnableGesture(enable);
    }

    @Override
    public void scrollToFinishActivity() {
        if (enableSwipeBackLayout) {
            Utils.convertActivityToTranslucent(this);
            java.util.Objects.requireNonNull(getSwipeBackLayout()).scrollToFinishActivity();
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
    /**
     * The toolbar, asserted present. Callers of this have already run {@link #setupAppBar}, which
     * binds it from the activity's layout and dereferences it itself. A screen that never sets one
     * up must read {@link #mToolbar} and test it instead.
     */
    public Toolbar requireToolbar() {
        return java.util.Objects.requireNonNull(mToolbar, "setupAppBar has not run");
    }

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
            java.util.Objects.requireNonNull(getSupportActionBar()).setDisplayHomeAsUpEnabled(enableUpButton);
            java.util.Objects.requireNonNull(getSupportActionBar()).setTitle(title);
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
            java.util.Objects.requireNonNull(getSupportActionBar()).setDisplayHomeAsUpEnabled(enableUpButton);
            java.util.Objects.requireNonNull(getSupportActionBar()).setTitle(title);
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
            java.util.Objects.requireNonNull(getSupportActionBar()).setDisplayHomeAsUpEnabled(enableUpButton);
            if (title != null) {
                java.util.Objects.requireNonNull(getSupportActionBar()).setTitle(title);
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
            java.util.Objects.requireNonNull(getSupportActionBar()).setDisplayHomeAsUpEnabled(enableUpButton);
            java.util.Objects.requireNonNull(getSupportActionBar()).setTitle(title);
        }

        themeSystemBars(subreddit);
        setRecentBar(title, Palette.getSubredditStatusBarColor(subreddit));
    }

    /**
     * Sets the status bar and navigation bar color for the activity based on a specific subreddit.
     *
     * @param subreddit The subreddit to base the color on.
     */
    public void themeSystemBars(@Nullable String subreddit) {
        int color = Palette.getSubredditStatusBarColor(subreddit);
        LogUtil.v(
                "StatusBarColor: themeSystemBars(subreddit=\""
                        + subreddit
                        + "\") ["
                        + getClass().getSimpleName()
                        + "] subColor="
                        + colorHex(Palette.getColor(subreddit))
                        + " defaultColor="
                        + colorHex(Palette.getDefaultColor())
                        + " usingDefault="
                        + (Palette.getColor(subreddit) == Palette.getDefaultColor())
                        + " -> statusBar="
                        + colorHex(color));
        themeSystemBars(color);
    }

    /**
     * Sets the status bar and navigation bar color for the activity
     *
     * @param color The color to tint the bars with
     */
    protected void themeSystemBars(int color) {
        LogUtil.v(
                "StatusBarColor: themeSystemBars(color="
                        + colorHex(color)
                        + ") ["
                        + getClass().getSimpleName()
                        + "] alwaysBlackStatusbar="
                        + SettingValues.alwaysBlackStatusbar
                        + " colorNavBar="
                        + SettingValues.colorNavBar);

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
    public void setRecentBar(@Nullable String subreddit) {
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
                "androidcirclejerk".equalsIgnoreCase(title)
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
    /**
     * Remembers how far down a plain scrolling screen was left, so a hibernate resume brings it
     * back at the same place rather than at the top. This is the whole of what most screens need —
     * the settings tree in particular, which is fifteen-odd activities that are each one long
     * ScrollView — and putting it here is what saves implementing the same three lines on all of
     * them.
     *
     * <p>Screens with more to remember (a feed's listing and anchor post, a comment thread's
     * collapsed replies, a pager's page) override both of these — and must call through, or they
     * lose the list position below along with it.
     *
     * <p>A list is remembered as a {@link ScrollAnchor}: the adapter position of the row at the
     * top plus its pixel offset, never a raw scroll offset, because the contents are refetched on
     * the way back and a pixel count into a list of a different length means nothing. Position is
     * the weaker anchor a feed's fullname gives — a listing that came back reordered lands near
     * where it was rather than exactly — and it is what a screen whose rows carry no stable id can
     * offer.
     */
    @Override
    public void saveHibernateState(Bundle out) {
        final View scroller = findScrollingContent();
        if (scroller != null) {
            // Written even when it is zero, so that an empty bundle keeps its one meaning: this
            // screen was asked before it had a view to measure. A screen that says nothing has
            // its previously recorded state kept, which for a list scrolled back to the top would
            // otherwise restore it back down again.
            out.putInt(HibernateState.STATE_SCROLL_Y, scroller.getScrollY());
        }
        final RecyclerView list = findListContent();
        if (list != null) {
            final ScrollAnchor anchor = ScrollAnchor.capture(list);
            if (anchor.isValid()) {
                out.putInt(HibernateState.STATE_ANCHOR_POSITION, anchor.position);
                out.putInt(HibernateState.STATE_ANCHOR_OFFSET, anchor.offset);
            }
        }
        // Whether the bar is hidden is part of what the screen looked like, not decoration: coming
        // back with it out covers the top of the very row the position was restored for.
        //
        // Only alongside a position, never on its own. An empty bundle is how a screen says it was
        // asked before it had anything to measure, and that answer is the one thing that stops a
        // capture during startup replacing the position already recorded. A lone "the bar is out",
        // which is what every screen reports before its rows arrive, would be a non-empty bundle
        // saying less than nothing.
        final View header = findViewById(R.id.header);
        if (header != null && !out.isEmpty()) {
            out.putBoolean(HibernateState.STATE_TOOLBAR_HIDDEN, header.getTranslationY() != 0f);
        }
        final View fab = findVisibleFab();
        if (fab != null && !out.isEmpty()) {
            out.putBoolean(HibernateState.STATE_FAB_HIDDEN, fab.getVisibility() != View.VISIBLE);
        }
    }

    @Override
    public void restoreHibernateState(Bundle in) {
        pendingScrollY = in.getInt(HibernateState.STATE_SCROLL_Y, 0);
        pendingAnchorPosition =
                in.getInt(HibernateState.STATE_ANCHOR_POSITION, ScrollAnchor.NO_POSITION);
        pendingAnchorOffset = in.getInt(HibernateState.STATE_ANCHOR_OFFSET, 0);
        hasPendingToolbar = in.containsKey(HibernateState.STATE_TOOLBAR_HIDDEN);
        pendingToolbarHidden = in.getBoolean(HibernateState.STATE_TOOLBAR_HIDDEN, false);
        hasPendingFab = in.containsKey(HibernateState.STATE_FAB_HIDDEN);
        pendingFabHidden = in.getBoolean(HibernateState.STATE_FAB_HIDDEN, false);
    }

    /**
     * Waits for the rows to arrive, then puts the list back where it was.
     *
     * <p>Polled rather than applied in {@code onPostCreate}: these lists are filled from the
     * network, or from a pager page that is not built yet, so at the point the state is claimed
     * there is usually neither a RecyclerView nor an adapter to scroll. Polling stops at the
     * first success and gives up after {@link #LIST_RESTORE_ATTEMPTS}, so a list whose rows never
     * come is left alone rather than jumped minutes later.
     */
    private void watchForListRestore() {
        if (pendingAnchorPosition == ScrollAnchor.NO_POSITION
                && !hasPendingToolbar
                && !hasPendingFab) {
            return;
        }
        final View decor = getWindow().getDecorView();
        decor.postDelayed(
                new Runnable() {
                    private int attempts;

                    @Override
                    public void run() {
                        if (pendingAnchorPosition == ScrollAnchor.NO_POSITION
                                && !hasPendingToolbar
                                && !hasPendingFab) {
                            return; // already applied, or given up on
                        }
                        // Nothing is applied while the screen is still deciding which page it
                        // is on. The list would be the wrong page's, and the bar would be undone
                        // a moment later anyway -- landing on a page animates the header back
                        // out (see the onPageSelected handlers on these screens).
                        if (!hasPendingPageRestore()) {
                            final RecyclerView list = findListContent();
                            final RecyclerView.Adapter<?> adapter =
                                    list == null ? null : list.getAdapter();
                            if (pendingAnchorPosition != ScrollAnchor.NO_POSITION) {
                                if (list != null
                                        && adapter != null
                                        && adapter.getItemCount() > pendingAnchorPosition
                                        // After a short landing, only once more rows have come.
                                        && (anchorLandedShort == null
                                                || adapter.getItemCount() > anchorShortRowCount)) {
                                    applyPendingAnchor(list);
                                    return;
                                }
                            } else if (applyPendingChrome()) {
                                // Nothing to scroll back to, only the chrome to put back.
                                return;
                            }
                        }
                        if (++attempts >= LIST_RESTORE_ATTEMPTS) {
                            if (anchorLandedShort != null) {
                                // The rows it was waiting for never came. The list is as close
                                // as the rows it has allow, so it is shown there with the bar
                                // settled to match rather than left blank.
                                anchorLandedShort = null;
                                releaseHeldList();
                                applyPendingChrome();
                            }
                            pendingAnchorPosition = ScrollAnchor.NO_POSITION;
                            hasPendingToolbar = false;
                            hasPendingFab = false;
                            return;
                        }
                        decor.postDelayed(this, LIST_RESTORE_POLL_MS);
                    }
                },
                LIST_RESTORE_POLL_MS);
    }

    /**
     * Only onto a list the user has not already moved, and again until the list lands.
     *
     * <p>A list comes back holding its first page and no more. When the recorded row is near the
     * end of that page there is not a screen's worth of rows below it, so {@code
     * scrollToPositionWithOffset} stops at the end of what is there -- short by however far into
     * the next page the user had scrolled. Settling for that restored Discover several rows above
     * where it was left, and the next capture then recorded the short position over the real one.
     * So a landing that comes up short with the loading footer on screen is held hidden, and tried
     * again once more rows arrive; the footer being on screen is what asks for them.
     */
    private void applyPendingAnchor(final RecyclerView list) {
        final int position = pendingAnchorPosition;
        final int offset = pendingAnchorOffset;
        pendingAnchorPosition = ScrollAnchor.NO_POSITION;
        final ScrollAnchor shortOf = anchorLandedShort;
        anchorLandedShort = null;
        final ScrollAnchor now = ScrollAnchor.capture(list);
        // Against where the last attempt left it, when there was one: the list is still there,
        // not at the top, and has only moved if something other than this restore moved it.
        final boolean moved =
                shortOf == null
                        ? now.isValid() && now.position != 0
                        : now.position != shortOf.position || now.offset != shortOf.offset;
        if (moved) {
            // Scrolled while the rows were loading, which only the user can have done. Jumping
            // now would take the screen away from them, and the bar is already wherever that
            // scroll of theirs left it.
            releaseHeldList();
            hasPendingToolbar = false;
            hasPendingFab = false;
            return;
        }
        holdListHidden(list, ScrollAnchor.REVEAL_TIMEOUT_MS);
        ScrollAnchor.apply(
                list,
                position,
                offset,
                () ->
                        // The jump is laid out on the next pass, and pushes one large dy through
                        // the scroll handlers. Posted so where it landed can be read, and so the
                        // bar is settled after that dy rather than being overwritten by it.
                        list.post(
                                new Runnable() {
                                    @Override
                                    public void run() {
                                        final RecyclerView.Adapter<?> adapter = list.getAdapter();
                                        if (adapter != null
                                                && landedShortOfMoreRows(list, position, offset)) {
                                            anchorLandedShort = ScrollAnchor.capture(list);
                                            anchorShortRowCount = adapter.getItemCount();
                                            pendingAnchorPosition = position;
                                            holdListHidden(
                                                    list, ScrollAnchor.FETCH_REVEAL_TIMEOUT_MS);
                                            watchForListRestore();
                                            return;
                                        }
                                        releaseHeldList();
                                        applyPendingChrome();
                                    }
                                }));
    }

    /**
     * Whether a restore stopped above its row for want of rows below it, with more on the way:
     * the list sits higher than it was asked to, and its last row is the loading footer. A list
     * that has ended has no such footer, and is left where the rows it has allow.
     */
    private static boolean landedShortOfMoreRows(RecyclerView list, int position, int offset) {
        final RecyclerView.LayoutManager lm = list.getLayoutManager();
        final RecyclerView.Adapter<?> adapter = list.getAdapter();
        if (lm == null || adapter == null) {
            return false;
        }
        final ScrollAnchor landed = ScrollAnchor.capture(list);
        final boolean above =
                landed.isValid()
                        && (landed.position < position
                                || (landed.position == position && landed.offset > offset));
        final View last = lm.findViewByPosition(adapter.getItemCount() - 1);
        return above && last != null && last.getId() == R.id.loading_more;
    }

    /**
     * Hides {@code list} until {@link #releaseHeldList}, or until {@code timeoutMs} has passed.
     *
     * <p>Not {@link ScrollAnchor#applyHidden}: its backstop cannot be called off, so a restore
     * waiting on more rows would have the list shown at the short landing a second in, and then
     * jump when the rows came.
     */
    private void holdListHidden(RecyclerView list, long timeoutMs) {
        // Same tick as the hide below, so nothing is drawn in between.
        releaseHeldList();
        list.setVisibility(View.INVISIBLE);
        final Runnable reveal = this::releaseHeldList;
        heldList = list;
        heldListReveal = reveal;
        list.postDelayed(reveal, timeoutMs);
    }

    /** Shows the list {@link #holdListHidden} is holding, if any, and calls off its backstop. */
    private void releaseHeldList() {
        final RecyclerView list = heldList;
        final Runnable reveal = heldListReveal;
        heldList = null;
        heldListReveal = null;
        if (list != null) {
            if (reveal != null) {
                list.removeCallbacks(reveal);
            }
            list.setVisibility(View.VISIBLE);
        }
    }

    /** One-shot: puts the post button back the way the recorded scroll position left it. */
    private boolean applyPendingFab() {
        if (!hasPendingFab) {
            return true;
        }
        final View fab = findVisibleFab();
        if (fab == null) {
            // The page holding it has not been built yet. Nothing consumed, so the poll asks
            // again rather than dropping the restore on the floor.
            return false;
        }
        hasPendingFab = false;
        if (!(fab instanceof FloatingActionButton) || !SettingValues.fab) {
            return true;
        }
        // show()/hide() rather than setVisibility, because that is what the scroll listener on
        // these pages uses and the button keeps state of its own about which it last ran.
        if (pendingFabHidden && !SettingValues.alwaysShowFAB) {
            ((FloatingActionButton) fab).hide();
        } else {
            ((FloatingActionButton) fab).show();
        }
        return true;
    }

    /**
     * Applies everything that rides the scroll -- the app bar and the post button -- and says
     * whether all of it landed.
     *
     * <p>Both, not the bar alone: the two are recorded independently (a screen can have one and
     * not the other) and applying the button only from inside the bar's path meant a screen with
     * no {@code R.id.header} never restored its button at all.
     */
    private boolean applyPendingChrome() {
        final boolean toolbar = applyPendingToolbar();
        final boolean fab = applyPendingFab();
        return toolbar && fab;
    }

    /**
     * Whether this screen is still waiting to land on the page it was left on.
     *
     * <p>Overridden by the tabbed screens that choose their page asynchronously -- the
     * Multireddit and Megareddit overviews fetch the list the tabs are built from, so the page to
     * land on is only decidable once it arrives. Until then the page on screen is the first one,
     * and the restore below would scroll <em>that</em> list to a position recorded for a
     * different one. The poll keeps waiting instead, and gives up on the same attempt count as
     * any other list that never arrives.
     */
    protected boolean hasPendingPageRestore() {
        return false;
    }

    /**
     * Whether this screen is still putting itself back where a snapshot recorded it -- its page
     * or its list position not landed yet. What it would report meanwhile is the state it is
     * passing through on the way (the first tab, a list with no rows, the bar out), and recording
     * that replaced the position the restore was about to put back.
     */
    public boolean isRestorePending() {
        return pendingAnchorPosition != ScrollAnchor.NO_POSITION || hasPendingPageRestore();
    }

    /**
     * Registers a list's scroll handler, so a restore can tell it where the app bar ended up.
     * Called by {@link ToolbarScrollHideHandler} as it is built.
     */
    public void registerScrollHideHandler(ToolbarScrollHideHandler handler) {
        // Only while a bar is waiting to be put back. A pager builds a fresh handler every time it
        // builds a page, so collecting them unconditionally would grow this list for as long as
        // the screen lived, for the benefit of a restore that happens once at startup or not at
        // all.
        //
        // Or while that is not known yet. Search and Related build their list, handler and all, in
        // onCreate, before onPostCreate has claimed the snapshot that says whether a bar is
        // waiting; turned away then, they were never told, and came back with the bar out over
        // the rows the position had been restored for.
        if (pendingToolbarKnown && !hasPendingToolbar) {
            return;
        }
        if (!scrollHideHandlers.contains(handler)) {
            scrollHideHandlers.add(handler);
        }
    }

    /**
     * One-shot: puts the app bar back where the recorded scroll position left it.
     *
     * @return whether it was applied. False means nothing has been consumed and the caller may
     *     try again: hiding the bar is measured against the toolbar's own height, so asking
     *     before it has been laid out moves it by zero and reports success having done nothing.
     */
    private boolean applyPendingToolbar() {
        if (!hasPendingToolbar) {
            return true;
        }
        if (scrollHideHandlers.isEmpty()) {
            return false;
        }
        final Toolbar bar = mToolbar;
        if (pendingToolbarHidden && (bar == null || bar.getHeight() == 0)) {
            return false;
        }
        hasPendingToolbar = false;
        // settleAfterJump, not a bare setTranslationY: the handler's own offset has to agree with
        // the bar, or it animates it straight back out on the user's first scroll.
        for (ToolbarScrollHideHandler handler : scrollHideHandlers) {
            handler.settleAfterJump(pendingToolbarHidden);
        }
        scrollHideHandlers.clear();
        return true;
    }

    /**
     * The post button on the page the user is looking at, or null.
     *
     * <p>Not {@code findViewById} on the activity: every page inflates the same {@code fab.xml}
     * through {@code fragment_verticalcontent}, and a pager keeps its neighbours attached, so
     * asking the activity for the id returns whichever page comes first in the tree. Recording or
     * restoring that one leaves the button the user can actually see untouched. Found by walking
     * up from the visible list, since the button and the list belong to the same page.
     */
    @Nullable
    private View findVisibleFab() {
        final RecyclerView list = findListContent();
        if (list == null) {
            return findViewById(R.id.post_floating_action_button);
        }
        ViewParent parent = list.getParent();
        while (parent instanceof View) {
            final View found = ((View) parent).findViewById(R.id.post_floating_action_button);
            if (found != null) {
                return found;
            }
            parent = parent.getParent();
        }
        return findViewById(R.id.post_floating_action_button);
    }

    /**
     * The RecyclerView the user is looking at, or null.
     *
     * <p>A pager keeps its neighbouring pages attached, so several are in the tree at once and
     * the one under the middle of the content area is the page on screen. Tree order is not that
     * page: {@code ViewPager} does not reorder its children as it moves.
     */
    @Nullable
    private RecyclerView findListContent() {
        final View content = findViewById(android.R.id.content);
        if (content == null) {
            return null;
        }
        final List<RecyclerView> found = new ArrayList<>();
        collectLists(content, found);
        if (found.isEmpty()) {
            return null;
        }
        if (found.size() == 1) {
            return found.get(0);
        }
        final Rect contentRect = new Rect();
        if (!content.getGlobalVisibleRect(contentRect)) {
            return found.get(0);
        }
        final Rect bounds = new Rect();
        for (RecyclerView candidate : found) {
            if (candidate.getGlobalVisibleRect(bounds)
                    && bounds.contains(contentRect.centerX(), contentRect.centerY())) {
                return candidate;
            }
        }
        return found.get(0);
    }

    private static void collectLists(@Nullable View view, List<RecyclerView> out) {
        if (view instanceof RecyclerView) {
            out.add((RecyclerView) view);
            return; // a list inside a list row is part of that row, not the screen's list
        }
        if (!(view instanceof ViewGroup)) {
            return;
        }
        final ViewGroup group = (ViewGroup) view;
        for (int i = 0; i < group.getChildCount(); i++) {
            collectLists(group.getChildAt(i), out);
        }
    }

    /**
     * Applies a restored scroll offset, hiding the view until it is in position so the screen never
     * appears at the top and then jumps.
     */
    private void applyPendingScroll() {
        if (pendingScrollY > 0) {
            ScrollAnchor.applyScrollY(findScrollingContent(), pendingScrollY);
            pendingScrollY = 0;
        }
    }

    /** The first ScrollView or NestedScrollView in the content view, or null if there is none. */
    @Nullable
    private View findScrollingContent() {
        return firstScroller(findViewById(android.R.id.content));
    }

    @Nullable
    private static View firstScroller(@Nullable View view) {
        if (view instanceof ScrollView || view instanceof NestedScrollView) {
            return view;
        }
        if (!(view instanceof ViewGroup)) {
            return null;
        }
        final ViewGroup group = (ViewGroup) view;
        for (int i = 0; i < group.getChildCount(); i++) {
            final View found = firstScroller(group.getChildAt(i));
            if (found != null) {
                return found;
            }
        }
        return null;
    }
}
