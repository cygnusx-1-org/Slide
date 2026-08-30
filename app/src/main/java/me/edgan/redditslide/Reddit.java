package me.edgan.redditslide;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.Application;
import android.app.UiModeManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.WindowManager;
import android.widget.Toast;
import androidx.annotation.Nullable;
import androidx.annotation.OptIn;
import androidx.appcompat.app.AlertDialog;
import androidx.core.app.NotificationChannelCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.res.ResourcesCompat;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.database.DatabaseProvider;
import androidx.media3.database.ExoDatabaseProvider;
import androidx.media3.datasource.cache.Cache;
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor;
import androidx.media3.datasource.cache.SimpleCache;
import com.jakewharton.processphoenix.ProcessPhoenix;
import com.lusfold.androidkeyvaluestore.KVStore;
import com.nostra13.universalimageloader.core.ImageLoader;
import java.io.File;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.Writer;
import java.lang.ref.WeakReference;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import me.edgan.redditslide.Activities.MainActivity;
import me.edgan.redditslide.Autocache.AutoCacheScheduler;
import me.edgan.redditslide.ImgurAlbum.AlbumUtils;
import me.edgan.redditslide.Notifications.NotificationJobScheduler;
import me.edgan.redditslide.Notifications.NotificationPiggyback;
import me.edgan.redditslide.Notifications.TokenRefreshReceiver;
import me.edgan.redditslide.Random.RandomSubreddits;
import me.edgan.redditslide.Tumblr.TumblrUtils;
import me.edgan.redditslide.Visuals.Palette;
import me.edgan.redditslide.util.CompatUtil;
import me.edgan.redditslide.util.DialogUtil;
import me.edgan.redditslide.util.GifCache;
import me.edgan.redditslide.util.ImageLoaderUtils;
import me.edgan.redditslide.util.LogUtil;
import me.edgan.redditslide.util.NetworkUtil;
import me.edgan.redditslide.util.PrefUtil;
import me.edgan.redditslide.util.ReauthNotifier;
import me.edgan.redditslide.util.SortingUtil;
import me.edgan.redditslide.util.UpgradeUtil;
import net.dean.jraw.http.NetworkException;
import okhttp3.Dns;
import okhttp3.OkHttpClient;
import org.apache.commons.lang3.tuple.Triple;
import org.apache.commons.text.StringEscapeUtils;

/** Created by ccrama on 9/17/2015. */
@OptIn(markerClass = UnstableApi.class)
public class Reddit extends Application implements Application.ActivityLifecycleCallbacks {
    // NullAway.Init here and on the SharedPreferences group below: onCreate assigns mApplication
    // as its first statement and doMainStuff assigns the rest, so all of them are set before any
    // activity, service or receiver in the app can run.
    @SuppressWarnings("NullAway.Init")
    private static Application mApplication;

    public static final String EMPTY_STRING = "NOTHING";

    public static final long enter_animation_time_original = 600;
    public static final String PREF_LAYOUT = "PRESET";
    public static final String SHARED_PREF_IS_MOD = "is_mod";

    /** Built on first use by getVideoCache, so null until something plays a video. */
    @Nullable private static Cache videoCache;
    private static final Object videoCacheLock = new Object();

    public static long enter_animation_time = enter_animation_time_original;
    public static final int enter_animation_time_multiplier = 1;

    @SuppressWarnings("NullAway.Init") // assigned in doMainStuff/onActivityResumed
    public static Authentication authentication;

    @SuppressWarnings("NullAway.Init") // assigned in doMainStuff
    public static SharedPreferences colors;

    @SuppressWarnings("NullAway.Init") // assigned in doMainStuff
    public static SharedPreferences appRestart;

    @SuppressWarnings("NullAway.Init") // assigned in doMainStuff
    public static SharedPreferences tags;

    public static int dpWidth;
    public static int notificationTime;
    public static boolean videoPlugin;
    // Started on demand by whichever screen first needs them, and SettingsGeneralFragment
    // already tests both for null before using them.
    @Nullable public static NotificationJobScheduler notifications;
    public static boolean isLoading = false;
    public static final long time = System.currentTimeMillis();
    public static boolean fabClear;
    public static int currentPosition;

    @SuppressWarnings("NullAway.Init") // assigned in doMainStuff
    public static SharedPreferences cachedData;
    public static final boolean noGapps = true; // for testing
    public static boolean overrideLanguage;
    public static boolean isRestarting;
    @Nullable public static AutoCacheScheduler autoCache;
    public static boolean peek;
    public boolean active;
    /** Built on first use by getImageLoader, and rebuilt whenever it has been de-initialised. */
    @Nullable public ImageLoader defaultImageLoader;

    @SuppressWarnings("NullAway.Init") // assigned in doMainStuff/onActivityResumed
    public static OkHttpClient client;

    public static boolean canUseNightModeAuto = false;

    public static void forceRestart(Context context, boolean forceLoadScreen) {
        if (forceLoadScreen) {
            appRestart.edit().putBoolean("isRestarting", true).apply();
        }
        if (appRestart.contains("back")) {
            appRestart.edit().remove("back").apply();
        }

        appRestart.edit().putBoolean("isRestarting", true).apply();
        isRestarting = true;
        // Every caller of this is a "the app is not what it was" moment -- an account switch, an
        // offline-mode toggle, a settings restore, finishing the tutorial. A hibernate snapshot
        // taken before one of those describes a session that no longer exists, so drop it and let
        // the rebirth land on a normal first screen.
        HibernateState.clear(context);
        ProcessPhoenix.triggerRebirth(context, new Intent(context, MainActivity.class));
    }

    public static void defaultShareText(@Nullable String title, @Nullable String url, Context c) {
        url = StringEscapeUtils.unescapeHtml4(CompatUtil.fromHtml(url).toString());
        Intent sharingIntent = new Intent(Intent.ACTION_SEND);
        sharingIntent.setType("text/plain");
        /* Decode html entities */
        title = StringEscapeUtils.unescapeHtml4(title);
        sharingIntent.putExtra(Intent.EXTRA_SUBJECT, title);
        sharingIntent.putExtra(Intent.EXTRA_TEXT, url);
        c.startActivity(Intent.createChooser(sharingIntent, c.getString(R.string.title_share)));
    }

    public static boolean isPackageInstalled(String s) {
        try {
            final PackageInfo pi = getAppContext().getPackageManager().getPackageInfo(s, 0);
            if (pi != null && pi.applicationInfo.enabled) return true;
        } catch (final Throwable ignored) {
            // Not installed, which is what the false below reports.
        }
        return false;
    }

    private static boolean isVideoPluginInstalled() {
        return isPackageInstalled(getAppContext().getString(R.string.youtube_plugin_package));
    }

    public static HashMap<String, String> getInstalledBrowsers() {
        int packageMatcher = PackageManager.MATCH_ALL;

        HashMap<String, String> browserMap = new HashMap<>();

        final List<ResolveInfo> resolveInfoList =
                getAppContext()
                        .getPackageManager()
                        .queryIntentActivities(
                                new Intent(Intent.ACTION_VIEW, Uri.parse("http://ccrama.me")),
                                packageMatcher);

        for (ResolveInfo resolveInfo : resolveInfoList) {
            if (resolveInfo.activityInfo.enabled) {
                browserMap.put(
                        resolveInfo.activityInfo.applicationInfo.packageName,
                        Reddit.getAppContext()
                                .getPackageManager()
                                .getApplicationLabel(resolveInfo.activityInfo.applicationInfo)
                                .toString());
            }
        }

        return browserMap;
    }

    @Override
    public void onLowMemory() {
        super.onLowMemory();
        getImageLoader().clearMemoryCache();
    }

    public ImageLoader getImageLoader() {
        if (defaultImageLoader == null || !defaultImageLoader.isInited()) {
            ImageLoaderUtils.initImageLoader(getApplicationContext());
            defaultImageLoader = ImageLoaderUtils.imageLoader;
        }

        return defaultImageLoader;
    }

    public static boolean notFirst = false;

    @Override
    public void onActivityResumed(Activity activity) {
        ReauthNotifier.attach(activity);
        doLanguages();
        if (client == null) {
            OkHttpClient.Builder builder = new OkHttpClient.Builder();
            builder.dns(new GfycatIpv4Dns());
            client = builder.build();
        }
        if (authentication != null
                && Authentication.didOnline
                && Authentication.authentication.getLong("expires", 0)
                        <= Calendar.getInstance().getTimeInMillis()) {
            authentication.updateToken(activity);
        } else if (NetworkUtil.isConnected(activity) && authentication == null) {
            authentication = new Authentication(this);
        }
        // Keep the background keep-warm token refresh aligned with the current token expiry so a
        // later re-open usually finds a fresh token instead of relying on the resume-time refresh.
        TokenRefreshReceiver.schedule(this);
    }

    @Override
    public void onActivityPaused(Activity activity) {
        ReauthNotifier.detach(activity);
        HibernateState.onActivityPaused(this);
    }

    /** Pass null to clear the handler: the weak reference then resolves to nothing and the
     * previous android handler is all that runs. */
    public static void setDefaultErrorHandler(@Nullable Context base) {
        // START code adapted from https://github.com/QuantumBadger/RedReader/
        final Thread.UncaughtExceptionHandler androidHandler =
                Thread.getDefaultUncaughtExceptionHandler();
        final WeakReference<Context> cont = new WeakReference<>(base);

        Thread.setDefaultUncaughtExceptionHandler(
                new Thread.UncaughtExceptionHandler() {
                    @Override public void uncaughtException(Thread thread, Throwable t) {
                        if (cont.get() != null) {
                            final Context c = cont.get();
                            Writer writer = new StringWriter();
                            PrintWriter printWriter = new PrintWriter(writer);
                            t.printStackTrace(printWriter);
                            String stacktrace = writer.toString().replace(";", ",");
                            // Null for anything thrown without one, which includes the very
                            // NullPointerException the branch below is trying to recognise.
                            final String message = t.getMessage();
                            if (stacktrace.contains("UnknownHostException")
                                    || stacktrace.contains("SocketTimeoutException")
                                    || stacktrace.contains("ConnectException")) {
                                // is offline
                                final Handler mHandler = new Handler(Looper.getMainLooper());
                                mHandler.post(
                                        new Runnable() {
                                            @Override
                                            public void run() {
                                                try {
                                                    DialogUtil.showWithCardBackground(new AlertDialog.Builder(c)
                                                            .setTitle(R.string.err_title)
                                                            .setMessage(
                                                                    R.string
                                                                            .err_connection_failed_msg)
                                                            .setNegativeButton(
                                                                    R.string.btn_close,
                                                                    (dialog, which) -> {
                                                                        if (!(c
                                                                                instanceof
                                                                                MainActivity)) {
                                                                            ((Activity) c).finish();
                                                                        }
                                                                    })
                                                            .setPositiveButton(
                                                                    R.string.btn_offline,
                                                                    (dialog, which) -> {
                                                                        Reddit.appRestart
                                                                                .edit()
                                                                                .putBoolean(
                                                                                        "forceoffline",
                                                                                        true)
                                                                                .apply();
                                                                        Reddit.forceRestart(
                                                                                c, false);
                                                                    })
                                                            );
                                                } catch (Exception ignored) {
                                                    // Dialog on a context that is finishing.
                                                }
                                            }
                                        });
                            } else if (stacktrace.contains("403 Forbidden")
                                    || stacktrace.contains("401 Unauthorized")) {
                                // Un-authenticated
                                final Handler mHandler = new Handler(Looper.getMainLooper());
                                mHandler.post(
                                        new Runnable() {
                                            @Override
                                            public void run() {
                                                try {
                                                    DialogUtil.showWithCardBackground(new AlertDialog.Builder(c)
                                                            .setTitle(R.string.err_title)
                                                            .setMessage(
                                                                    R.string
                                                                            .err_refused_request_msg)
                                                            .setNegativeButton(
                                                                    "No",
                                                                    (dialog, which) -> {
                                                                        if (!(c
                                                                                instanceof
                                                                                MainActivity)) {
                                                                            ((Activity) c).finish();
                                                                        }
                                                                    })
                                                            .setPositiveButton(
                                                                    "Yes",
                                                                    (dialog, which) ->
                                                                            authentication
                                                                                    .updateToken(c))
                                                            );
                                                } catch (Exception ignored) {
                                                    // Dialog on a context that is finishing.
                                                }
                                            }
                                        });

                            } else if (stacktrace.contains("404 Not Found")
                                    || stacktrace.contains("400 Bad Request")) {
                                final Handler mHandler = new Handler(Looper.getMainLooper());
                                mHandler.post(
                                        new Runnable() {
                                            @Override
                                            public void run() {
                                                try {
                                                    DialogUtil.showWithCardBackground(new AlertDialog.Builder(c)
                                                            .setTitle(R.string.err_title)
                                                            .setMessage(
                                                                    R.string
                                                                            .err_could_not_find_content_msg)
                                                            .setNegativeButton(
                                                                    "Close",
                                                                    (dialog, which) -> {
                                                                        if (!(c
                                                                                instanceof
                                                                                MainActivity)) {
                                                                            ((Activity) c).finish();
                                                                        }
                                                                    })
                                                            );
                                                } catch (Exception ignored) {
                                                    // Dialog on a context that is finishing.
                                                }
                                            }
                                        });
                            } else if (t instanceof NetworkException) {
                                Toast.makeText(
                                                c,
                                                "Error "
                                                        + ((NetworkException) t)
                                                                .getResponse()
                                                                .getStatusMessage()
                                                        + ": "
                                                        + (t).getMessage(),
                                                Toast.LENGTH_LONG)
                                        .show();
                            } else if (t instanceof NullPointerException
                                    && message != null
                                    && message.contains(
                                            "Attempt to invoke virtual method"
                                                    + " 'android.content.Context"
                                                    + " android.view.ViewGroup.getContext()' on a"
                                                    + " null object reference")) {
                                LogUtil.e(t, "Reddit.run failed");
                            } else if (t instanceof WindowManager.BadTokenException) {
                                LogUtil.e(t, "Reddit.run failed");
                            } else if (t instanceof IllegalArgumentException
                                    && message != null
                                    && message.contains("pointerIndex out of range")) {
                                LogUtil.e(t, "Reddit.run failed");
                            } else {
                                appRestart
                                        .edit()
                                        .apply(); // Force reload of data after crash incase state
                                // was not saved

                                try {

                                    SharedPreferences prefs =
                                            c.getSharedPreferences(
                                                    "STACKTRACE", Context.MODE_PRIVATE);
                                    prefs.edit().putString("stacktrace", stacktrace).apply();

                                } catch (Throwable ignored) {
                                    // Inside the uncaught-exception handler: saving the stacktrace is
                                    // best-effort and must not throw on top of the crash it is reporting.
                                }

                                androidHandler.uncaughtException(thread, t);
                            }
                        } else {
                            androidHandler.uncaughtException(thread, t);
                        }
                    }
                });
        // END adaptation
    }

    @Override
    public void onActivityStopped(Activity activity) {
        HibernateState.onActivityStopped(this);
    }

    @Override
    public void onActivityCreated(Activity activity, @Nullable Bundle savedInstanceState) {
        doLanguages();
        HibernateState.recordCreated(activity);
    }

    @Override
    public void onActivityStarted(Activity activity) {
        HibernateState.onActivityStarted();
    }

    @Override
    public void onActivitySaveInstanceState(Activity activity, Bundle outState) {}

    @Override
    public void onActivityDestroyed(Activity activity) {
        HibernateState.recordDestroyed(activity);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        mApplication = this;
        //  LeakCanary.install(this);
        if (ProcessPhoenix.isPhoenixProcess(this)) {
            return;
        }

        UpgradeUtil.upgrade(getApplicationContext());
        doMainStuff();

        // Build the video cache off the launch thread; SimpleCache's constructor locks and scans
        // the cache folder, which cost hundreds of milliseconds of cold start for every user,
        // whether or not they went on to play a video.
        AsyncTask.THREAD_POOL_EXECUTOR.execute(Reddit::getVideoCache);
    }

    /**
     * The per-subreddit layout-preset key. The subreddit is lowercased here so that the writer,
     * the probe, the removes and the reader all land on the same entry.
     */
    public static String getLayoutPrefKey(String subreddit) {
        return PREF_LAYOUT + subreddit.toLowerCase(Locale.ENGLISH);
    }

    /**
     * The shared 256MB media cache, built on first use. SimpleCache throws if a second instance is
     * created for the same directory, so this must be the only construction site.
     */
    public static Cache getVideoCache() {
        synchronized (videoCacheLock) {
            if (videoCache == null) {
                final File dir;
                if (Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED)
                        && getAppContext().getExternalCacheDir() != null) {
                    dir =
                            new File(
                                    getAppContext().getExternalCacheDir()
                                            + File.separator
                                            + "video-cache");
                } else {
                    dir = new File(getAppContext().getCacheDir() + File.separator + "video-cache");
                }
                LeastRecentlyUsedCacheEvictor evictor =
                        new LeastRecentlyUsedCacheEvictor(256 * 1024 * 1024);
                DatabaseProvider databaseProvider = new ExoDatabaseProvider(getAppContext());
                videoCache = new SimpleCache(dir, evictor, databaseProvider); // 256MB
            }
            return videoCache;
        }
    }

    public void doMainStuff() {
        Log.v(LogUtil.getTag(), "ON CREATED AGAIN");
        if (client == null) {
            client = new OkHttpClient();
        }

        setCanUseNightModeAuto();

        overrideLanguage =
                getSharedPreferences("SETTINGS", 0)
                        .getBoolean(SettingValues.PREF_OVERRIDE_LANGUAGE, false);
        appRestart = getSharedPreferences("appRestart", 0);
        AlbumUtils.albumRequests = getSharedPreferences("albums", 0);
        TumblrUtils.tumblrRequests = getSharedPreferences("tumblr", 0);

        cachedData = getSharedPreferences("cache", 0);

        // Off the launch thread: "cache" holds one entry per cached subreddit page and is never
        // pruned, and contains() blocks until the whole file is parsed. Nothing needed for the
        // first frame reads it.
        AsyncTask.THREAD_POOL_EXECUTOR.execute(
                () -> {
                    if (!cachedData.contains("hasReset")) {
                        cachedData.edit().clear().putBoolean("hasReset", true).apply();
                    }
                });

        registerActivityLifecycleCallbacks(this);
        Authentication.authentication = getSharedPreferences("AUTH", 0);
        UserSubscriptions.subscriptions = getSharedPreferences("SUBSNEW", 0);
        UserSubscriptions.multiNameToSubs = getSharedPreferences("MULTITONAME", 0);

        UserSubscriptions.pinned = getSharedPreferences("PINNED", 0);
        SavedUsers.users = getSharedPreferences("USERS", 0);
        SavedUsers.friends = getSharedPreferences("USERFRIENDS", 0);
        PostMatch.filters = getSharedPreferences("FILTERS", 0);
        ImageFlairs.flairs = getSharedPreferences("FLAIRS", 0);
        SettingValues.setAllValues(getSharedPreferences("SETTINGS", 0));
        SortingUtil.defaultSorting = SettingValues.defaultSorting;
        SortingUtil.frontpageSorting = SettingValues.frontpageSorting;
        SortingUtil.timePeriod = SettingValues.timePeriod;
        // Restore the persisted frontpage time period so it survives across sessions and
        // is reflected in the settings sort dialog and the frontpage sort header.
        SortingUtil.setTime("frontpage", SettingValues.getSubmissionTimePeriod("frontpage"));
        colors = getSharedPreferences("COLOR", 0);
        tags = getSharedPreferences("TAGS", 0);
        KVStore.init(this, "SEEN");
        doLanguages();

        Authentication.isLoggedIn = appRestart.getBoolean("loggedin", false);
        Authentication.name = PrefUtil.getString(appRestart, "name", "LOGGEDOUT");
        active = true;

        authentication = new Authentication(this);

        Authentication.mod = Authentication.authentication.getBoolean(SHARED_PREF_IS_MOD, false);

        enter_animation_time = enter_animation_time_original * enter_animation_time_multiplier;

        fabClear = colors.getBoolean(SettingValues.PREF_FAB_CLEAR, false);

        if (colors.contains("tabletOVERRIDE")) {
            dpWidth = colors.getInt("tabletOVERRIDE", 1);
        } else {
            dpWidth = 1;
        }

        if (colors.contains("notificationOverride")) {
            notificationTime = colors.getInt("notificationOverride", 60);
        } else {
            notificationTime = 60;
        }

        videoPlugin = isVideoPluginInstalled();

        GifCache.init(this);

        // Opportunistic, on app open: re-check the hosted random-subreddit lists when their
        // cadence says one is due. Only lists already cached are touched, so this is two
        // preference reads for anyone who has never opened a random subreddit.
        RandomSubreddits.refreshCachedLists(this);

        setupNotificationChannels();
    }

    public void doLanguages() {
        if (SettingValues.overrideLanguage) {
            Locale locale = new Locale("en_US");
            Locale.setDefault(locale);
            Configuration config = getResources().getConfiguration();
            config.locale = locale;
            getResources().updateConfiguration(config, null);
        }
    }

    public boolean isNotificationAccessEnabled() {
        ActivityManager manager = ContextCompat.getSystemService(this, ActivityManager.class);
        if (manager != null) {
            for (ActivityManager.RunningServiceInfo service :
                    manager.getRunningServices(Integer.MAX_VALUE)) {
                if (NotificationPiggyback.class.getName().equals(service.service.getClassName())) {
                    return true;
                }
            }
        }
        return false;
    }

    public static final String CHANNEL_IMG = "IMG_DOWNLOADS";
    public static final String CHANNEL_COMMENT_CACHE = "POST_SYNC";
    public static final String CHANNEL_MAIL = "MAIL_NOTIFY";
    public static final String CHANNEL_MODMAIL = "MODMAIL_NOTIFY";
    public static final String CHANNEL_SUBCHECKING = "SUB_CHECK_NOTIFY";

    public void setupNotificationChannels() {
        // Each triple contains the channel ID, name, and importance level
        List<Triple<String, String, Integer>> notificationTripleList =
                Arrays.asList(
                        Triple.of(
                                CHANNEL_IMG,
                                "Image downloads",
                                NotificationManagerCompat.IMPORTANCE_LOW),
                        Triple.of(
                                CHANNEL_COMMENT_CACHE,
                                "Comment caching",
                                NotificationManagerCompat.IMPORTANCE_LOW),
                        Triple.of(
                                CHANNEL_MAIL,
                                "Reddit mail",
                                NotificationManagerCompat.IMPORTANCE_HIGH),
                        Triple.of(
                                CHANNEL_MODMAIL,
                                "Reddit modmail",
                                NotificationManagerCompat.IMPORTANCE_HIGH),
                        Triple.of(
                                CHANNEL_SUBCHECKING,
                                "Submission post checking",
                                NotificationManagerCompat.IMPORTANCE_LOW));

        final NotificationManagerCompat notificationManager = NotificationManagerCompat.from(this);

        for (Triple<String, String, Integer> notificationTriple : notificationTripleList) {
            final NotificationChannelCompat notificationChannel =
                    new NotificationChannelCompat.Builder(
                                    notificationTriple.getLeft(), notificationTriple.getRight())
                            .setName(notificationTriple.getMiddle())
                            .setLightsEnabled(true)
                            .setShowBadge(
                                    notificationTriple.getRight()
                                            == NotificationManagerCompat.IMPORTANCE_HIGH)
                            .setLightColor(
                                    notificationTriple.getLeft().contains("MODMAIL")
                                            ? ResourcesCompat.getColor(
                                                    this.getResources(), R.color.md_red_500, null)
                                            : Palette.getColor(""))
                            .build();
            notificationManager.createNotificationChannel(notificationChannel);
        }
    }

    // IPV6 workaround by /u/talklittle
    public static class GfycatIpv4Dns implements Dns {
        @Override
        public List<InetAddress> lookup(String hostname) throws UnknownHostException {
            if (ContentType.hostContains(hostname, "gfycat.com", "redgifs.com")) {
                InetAddress[] addresses = InetAddress.getAllByName(hostname);
                if (addresses == null || addresses.length == 0) {
                    throw new UnknownHostException("Bad host: " + hostname);
                }

                // prefer IPv4; list IPv4 first
                ArrayList<InetAddress> result = new ArrayList<>();
                for (InetAddress address : addresses) {
                    if (address instanceof Inet4Address) {
                        result.add(address);
                    }
                }
                for (InetAddress address : addresses) {
                    if (!(address instanceof Inet4Address)) {
                        result.add(address);
                    }
                }

                return result;
            } else {
                return Dns.SYSTEM.lookup(hostname);
            }
        }
    }

    public static Context getAppContext() {
        return mApplication.getApplicationContext();
    }

    private static void setCanUseNightModeAuto() {
        UiModeManager uiModeManager = getAppContext().getSystemService(UiModeManager.class);
        canUseNightModeAuto = uiModeManager != null;
    }
}
