package me.edgan.redditslide;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.util.Log;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import java.util.Calendar;
import java.util.HashSet;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import me.edgan.redditslide.Notifications.TokenRefreshReceiver;
import me.edgan.redditslide.util.DialogUtil;
import me.edgan.redditslide.util.LogUtil;
import me.edgan.redditslide.util.NetworkUtil;
import me.edgan.redditslide.util.ReauthNotifier;
import net.dean.jraw.RedditClient;
import net.dean.jraw.http.LoggingMode;
import net.dean.jraw.http.NetworkException;
import net.dean.jraw.http.OkHttpAdapter;
import net.dean.jraw.http.UserAgent;
import net.dean.jraw.http.oauth.Credentials;
import net.dean.jraw.http.oauth.OAuthData;
import net.dean.jraw.http.oauth.OAuthHelper;
import net.dean.jraw.models.LoggedInAccount;
import okhttp3.Protocol;

/** Created by ccrama on 3/30/2015. */
public class Authentication {
    // volatile: written from the reauth background thread (see maybeBreakReauth) and read on the
    // main thread throughout the app.
    public static volatile boolean isLoggedIn;

    // Dedicated single-thread executor for reauth tasks: serializes them so two never race on the
    // shared RedditClient/token state, while keeping them off the global AsyncTask serial executor
    // (so a stalled reauth doesn't block the rest of the app's AsyncTasks).
    private static final Executor REAUTH_EXECUTOR = Executors.newSingleThreadExecutor();
    public static RedditClient reddit;
    public static LoggedInAccount me;
    public static boolean mod;
    public static String name;
    public static SharedPreferences authentication;
    public static String refresh;

    public boolean hasDone;
    public static boolean didOnline;
    private static OkHttpAdapter httpAdapter;

    public static void resetAdapter() {
        new AsyncTask<Void, Void, Void>() {
            @Override
            protected Void doInBackground(Void... params) {
                if (httpAdapter != null && httpAdapter.getNativeClient() != null) {
                    httpAdapter.getNativeClient().connectionPool().evictAll();
                }
                return null;
            }
        }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }

    public Authentication(Context context) {
        Reddit.setDefaultErrorHandler(context);

        if (NetworkUtil.isConnected(context)) {
            hasDone = true;
            httpAdapter = new OkHttpAdapter(Reddit.client, Protocol.HTTP_2);
            isLoggedIn = false;
            reddit =
                    new RedditClient(
                            UserAgent.of(Constants.getUserAgent()),
                            httpAdapter);
            reddit.setRetryLimit(2);
            if (BuildConfig.DEBUG) reddit.setLoggingMode(LoggingMode.ALWAYS);
            didOnline = true;
            new VerifyCredentials(context).executeOnExecutor(REAUTH_EXECUTOR);
        } else {
            isLoggedIn = Reddit.appRestart.getBoolean("loggedin", false);
            name = Reddit.appRestart.getString("name", "");
            refresh = authentication.getString("lasttoken", "");
            if ((name.isEmpty() || !isLoggedIn)
                    && !authentication.getString("lasttoken", "").isEmpty()) {
                for (String s :
                        Authentication.authentication.getStringSet(
                                "accounts", new HashSet<String>())) {
                    if (s.contains(authentication.getString("lasttoken", ""))) {
                        name = (s.split(":")[0]);
                        break;
                    }
                }
                isLoggedIn = true;
            }
        }
    }

    public void updateToken(Context c) {
        updateToken(c, true, null);
    }

    public void updateToken(Context c, boolean reportToSnackbar) {
        updateToken(c, reportToSnackbar, null);
    }

    /**
     * @param reportToSnackbar when {@code false} the refresh runs silently, without the {@link
     *     ReauthNotifier} "reauthenticating"/failure snackbar. Used by the background {@link
     *     TokenRefreshReceiver} keep-warm refresh, which has no foreground user waiting on it.
     * @param onComplete run on the main thread once the refresh finishes (success, failure, or
     *     cancellation); may be {@code null}. Lets {@link TokenRefreshReceiver} hold its {@code
     *     goAsync()} broadcast lease open until the network refresh has actually completed.
     */
    public void updateToken(Context c, boolean reportToSnackbar, Runnable onComplete) {
        if (BuildConfig.DEBUG) LogUtil.v("Executing update token");
        if (reddit == null) {
            hasDone = true;
            isLoggedIn = false;
            reddit =
                    new RedditClient(
                            UserAgent.of(Constants.getUserAgent()));
            reddit.setLoggingMode(LoggingMode.ALWAYS);
            didOnline = true;

            new VerifyCredentials(c, reportToSnackbar, onComplete)
                    .executeOnExecutor(REAUTH_EXECUTOR);
        } else {
            new UpdateToken(c, reportToSnackbar, onComplete).executeOnExecutor(REAUTH_EXECUTOR);
        }
    }

    public static boolean authedOnce;

    public static class UpdateToken extends AsyncTask<Void, Void, Boolean> {

        Context context;
        final boolean reportToSnackbar;
        final Runnable onComplete;

        public UpdateToken(Context c) {
            this(c, true, null);
        }

        public UpdateToken(Context c, boolean reportToSnackbar) {
            this(c, reportToSnackbar, null);
        }

        public UpdateToken(Context c, boolean reportToSnackbar, Runnable onComplete) {
            this.context = c;
            this.reportToSnackbar = reportToSnackbar;
            this.onComplete = onComplete;
        }

        @Override
        protected void onPreExecute() {
            if (reportToSnackbar) ReauthNotifier.onStarted();
        }

        @Override
        protected void onPostExecute(Boolean success) {
            // A null result means no refresh was attempted (e.g. not yet authed); fall back to the
            // live auth state. A non-null result is the real outcome of the refresh, so a failed
            // refresh reports false even while the old (not-yet-expired) token still reads as
            // authenticated — that is what surfaces the reauth failure snackbar and its Retry.
            if (reportToSnackbar) {
                ReauthNotifier.onFinished(success != null ? success : isReauthed());
            } else {
                // Background keep-warm path: realign the next alarm now that the refresh has run
                // and (on success) bumped the stored expiry, instead of off the pre-refresh value
                // read synchronously in the receiver, which would fire a redundant early wake.
                TokenRefreshReceiver.schedule(context);
            }
            runOnComplete();
        }

        @Override
        protected void onCancelled() {
            // Keep the reauth "in progress" counter balanced if the task is cancelled.
            if (reportToSnackbar) ReauthNotifier.onFinished(isReauthed());
            runOnComplete();
        }

        private void runOnComplete() {
            if (onComplete != null) onComplete.run();
        }

        @Override
        protected Boolean doInBackground(Void... params) {
            Boolean result = null;
            maybeBreakReauth();
            if (authedOnce && NetworkUtil.isConnected(context)) {
                didOnline = true;
                if (name != null && !name.isEmpty()) {
                    Log.v(LogUtil.getTag(), "REAUTH");
                    // Ensure refresh token is available from SharedPreferences if null
                    if (refresh == null || refresh.isEmpty()) {
                        refresh = authentication.getString("lasttoken", "");
                    }
                    // Branch on the presence of a user refresh token, NOT the transient isLoggedIn
                    // flag: several paths clear isLoggedIn momentarily while the user is still
                    // logged in (constructor, a concurrent Authentication build, the debug break),
                    // and taking the userless branch then would poison backedCreds/name (sub:loid).
                    if (refresh != null && !refresh.isEmpty()) {
                        try {

                            final Credentials credentials =
                                    Credentials.installedApp(
                                            Constants.getClientId(), Constants.getRedirectUrl());
                            Log.v(LogUtil.getTag(), "REAUTH LOGGED IN");

                            OAuthHelper oAuthHelper = reddit.getOAuthHelper();

                            oAuthHelper.setRefreshToken(refresh);
                            OAuthData finalData;
                            if (authentication.contains("backedCreds")
                                    && authentication.getLong("expires", 0)
                                            > Calendar.getInstance().getTimeInMillis()) {
                                finalData =
                                        oAuthHelper.refreshToken(
                                                credentials,
                                                authentication.getString(
                                                        "backedCreds", "")); // does a request
                            } else {
                                finalData = oAuthHelper.refreshToken(credentials); // does a request
                                authentication
                                        .edit()
                                        .putLong(
                                                "expires",
                                                Calendar.getInstance().getTimeInMillis() + Constants.EXPIRES_VALUE)
                                        .commit();
                            }
                            authentication
                                    .edit()
                                    .putString("backedCreds", finalData.getDataNode().toString())
                                    .commit();
                            reddit.authenticate(finalData);
                            refresh = oAuthHelper.getRefreshToken();

                            if (reddit.isAuthenticated()) {
                                if (me == null) {
                                    // Don't let a me() blip skip isLoggedIn=true: the token is
                                    // already authenticated at this point.
                                    try {
                                        me = reddit.me();
                                    } catch (Exception meError) {
                                        LogUtil.e(meError, "reddit.me() failed after auth");
                                    }
                                }
                                Authentication.isLoggedIn = true;
                            }
                            Log.v(LogUtil.getTag(), "AUTHENTICATED");
                            result = reddit.isAuthenticated();
                        } catch (Exception e) {
                            LogUtil.e(e, "Authentication.doInBackground failed");
                            // Refresh failed; report failure so the reauth snackbar Retry appears.
                            result = false;
                        }

                    } else {
                        final Credentials fcreds =
                                Credentials.userlessApp(Constants.getClientId(), UUID.randomUUID());
                        OAuthData authData;
                        if (BuildConfig.DEBUG) LogUtil.v("Not logged in");
                        try {

                            authData = reddit.getOAuthHelper().easyAuth(fcreds);
                            authentication
                                    .edit()
                                    .putLong(
                                            "expires",
                                            Calendar.getInstance().getTimeInMillis() + Constants.EXPIRES_VALUE)
                                    .commit();
                            authentication
                                    .edit()
                                    .putString("backedCreds", authData.getDataNode().toString())
                                    .commit();
                            Authentication.name = "LOGGEDOUT";
                            mod = false;

                            reddit.authenticate(authData);
                            Log.v(LogUtil.getTag(), "REAUTH LOGGED IN");

                        } catch (Exception e) {
                            try {
                                ((Activity) context)
                                        .runOnUiThread(
                                                new Runnable() {
                                                    @Override
                                                    public void run() {
                                                        try {

                                                            DialogUtil.showWithCardBackground(new AlertDialog.Builder(context)
                                                                    .setTitle(R.string.err_general)
                                                                    .setMessage(
                                                                            R.string
                                                                                    .err_no_connection)
                                                                    .setPositiveButton(
                                                                            R.string.btn_yes,
                                                                            (dialog, which) ->
                                                                                    new UpdateToken(
                                                                                                    context)
                                                                                            .executeOnExecutor(
                                                                                                    REAUTH_EXECUTOR))
                                                                    .setNegativeButton(
                                                                            R.string.btn_no,
                                                                            (dialog, which) ->
                                                                                    Reddit
                                                                                            .forceRestart(
                                                                                                    context,
                                                                                                    false))
                                                                    );
                                                        } catch (Exception ignored) {

                                                        }
                                                    }
                                                });
                            } catch (Exception e2) {
                                Toast.makeText(
                                                context,
                                                "Reddit could not be reached. Try again soon",
                                                Toast.LENGTH_SHORT)
                                        .show();
                            }

                            // TODO fail
                        }
                    }
                }
            }
            if (BuildConfig.DEBUG) LogUtil.v("Done loading token");
            return result;
        }
    }

    public static class VerifyCredentials extends AsyncTask<String, Void, Void> {
        Context mContext;
        String lastToken;
        boolean single;
        final boolean reportToSnackbar;
        final Runnable onComplete;

        public VerifyCredentials(Context context) {
            this(context, true, null);
        }

        public VerifyCredentials(Context context, boolean reportToSnackbar, Runnable onComplete) {
            mContext = context;
            lastToken = authentication.getString("lasttoken", "");
            this.reportToSnackbar = reportToSnackbar;
            this.onComplete = onComplete;
        }

        @Override
        protected void onPreExecute() {
            if (reportToSnackbar) ReauthNotifier.onStarted();
        }

        @Override
        protected void onPostExecute(Void result) {
            if (reportToSnackbar) ReauthNotifier.onFinished(isReauthed());
            if (onComplete != null) onComplete.run();
        }

        @Override
        protected void onCancelled() {
            // Keep the reauth "in progress" counter balanced if the task is cancelled.
            if (reportToSnackbar) ReauthNotifier.onFinished(isReauthed());
            if (onComplete != null) onComplete.run();
        }

        @Override
        protected Void doInBackground(String... subs) {
            maybeBreakReauth();
            doVerify(lastToken, reddit, single, mContext);
            return null;
        }
    }

    /** Whether the Reddit client currently holds a valid auth token (logged-in or userless). */
    private static boolean isReauthed() {
        try {
            return reddit != null && reddit.isAuthenticated();
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Debug aid (see the Debug settings screen): when {@link SettingValues#debugBreakReauth} is on,
     * simulate a stalled re-authentication so the reauth snackbar (which appears after 10s and shows
     * failure after 30s) can be tested. Runs on a background thread; it holds the reauth "in
     * progress" and temporarily marks us logged-out (so the gated UI hides its buttons), and only
     * returns once the toggle is switched off — the recovery reauth then sets isLoggedIn back true.
     * The flag is read back through SharedPreferences each pass so this background thread reliably
     * observes the toggle change and exits. (No save/restore of isLoggedIn is needed: {@link
     * UpdateToken} picks the auth path from the refresh token, not isLoggedIn.)
     */
    private static void maybeBreakReauth() {
        if (!isBreakReauthEnabled()) return;
        isLoggedIn = false;
        while (isBreakReauthEnabled()) {
            try {
                Thread.sleep(500);
            } catch (InterruptedException ignored) {
            }
        }
    }

    private static boolean isBreakReauthEnabled() {
        return SettingValues.prefs != null
                && SettingValues.prefs.getBoolean(SettingValues.PREF_DEBUG_BREAK_REAUTH, false);
    }

    public static void doVerify(
            String lastToken, RedditClient baseReddit, boolean single, Context mContext) {
        try {

            if (BuildConfig.DEBUG) LogUtil.v("TOKEN IS " + lastToken);
            if (!lastToken.isEmpty()) {

                final Credentials credentials =
                        Credentials.installedApp(
                                Constants.getClientId(), Constants.getRedirectUrl());

                OAuthHelper oAuthHelper = baseReddit.getOAuthHelper();
                oAuthHelper.setRefreshToken(lastToken);

                try {
                    OAuthData finalData;
                    if (!single
                            && authentication.contains("backedCreds")
                            && authentication.getLong("expires", 0)
                                    > Calendar.getInstance().getTimeInMillis()) {
                        finalData =
                                oAuthHelper.refreshToken(
                                        credentials, authentication.getString("backedCreds", ""));
                    } else {
                        finalData = oAuthHelper.refreshToken(credentials); // does a request
                        if (!single) {
                            authentication
                                    .edit()
                                    .putLong(
                                            "expires",
                                            Calendar.getInstance().getTimeInMillis() + Constants.EXPIRES_VALUE)
                                    .apply();
                        }
                    }
                    baseReddit.authenticate(finalData);

                    if (!single) {
                        authentication
                                .edit()
                                .putString("backedCreds", finalData.getDataNode().toString())
                                .apply();
                        refresh = oAuthHelper.getRefreshToken();
                        if (BuildConfig.DEBUG) {
                            LogUtil.v("ACCESS TOKEN IS " + finalData.getAccessToken());
                        }

                        Authentication.isLoggedIn = true;

                        UserSubscriptions.doCachedModSubs();
                    }

                } catch (Exception e) {
                    LogUtil.e(e, "Authentication.doVerify failed");
                    if (e instanceof NetworkException) {
                        Toast.makeText(
                                        mContext,
                                        "Error "
                                                + ((NetworkException) e)
                                                        .getResponse()
                                                        .getStatusMessage()
                                                + ": "
                                                + (e).getMessage(),
                                        Toast.LENGTH_LONG)
                                .show();
                    }
                }
                didOnline = true;

            } else if (!single) {
                if (BuildConfig.DEBUG) LogUtil.v("NOT LOGGED IN");

                final Credentials fcreds =
                        Credentials.userlessApp(Constants.getClientId(), UUID.randomUUID());
                OAuthData authData;
                try {

                    authData = reddit.getOAuthHelper().easyAuth(fcreds);
                    authentication
                            .edit()
                            .putLong("expires", Calendar.getInstance().getTimeInMillis() + Constants.EXPIRES_VALUE)
                            .apply();
                    authentication
                            .edit()
                            .putString("backedCreds", authData.getDataNode().toString())
                            .apply();
                    reddit.authenticate(authData);

                    Authentication.name = "LOGGEDOUT";
                    Reddit.notFirst = true;
                    didOnline = true;

                } catch (Exception e) {
                    LogUtil.e(e, "Authentication.doVerify failed");
                    if (e instanceof NetworkException) {
                        Toast.makeText(
                                        mContext,
                                        "Error "
                                                + ((NetworkException) e)
                                                        .getResponse()
                                                        .getStatusMessage()
                                                + ": "
                                                + (e).getMessage(),
                                        Toast.LENGTH_LONG)
                                .show();
                    }
                }
            }
            if (!single) authedOnce = true;

        } catch (Exception e) {
            // TODO fail

        }
    }
}
