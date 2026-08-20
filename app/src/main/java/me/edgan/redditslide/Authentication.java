package me.edgan.redditslide;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.util.Log;
import android.webkit.CookieManager;
import android.widget.Toast;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import me.edgan.redditslide.Notifications.TokenRefreshReceiver;
import me.edgan.redditslide.util.DialogUtil;
import me.edgan.redditslide.util.LogUtil;
import me.edgan.redditslide.util.NetworkUtil;
import me.edgan.redditslide.util.PrefUtil;
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
    // The client, the account behind it and the refresh token all come from the network, and
    // every path that builds them can fail — the constructor's offline branch never builds a client
    // at all. The code here already branches on each of them being null.
    @Nullable public static RedditClient reddit;

    @Nullable public static LoggedInAccount me;

    public static boolean mod;

    // Assigned unconditionally by Reddit.doMainStuff, i.e. Application.onCreate — but several
    // sites then overwrite it with LoggedInAccount.getFullName(), which is JRAW's data("name") and
    // returns null when the account JSON carries no "name". SubmissionCache, LocalSaved and
    // DoEditorActions have always tested it for null.
    @Nullable public static String name;

    @SuppressWarnings("NullAway.Init") // Reddit.onCreate assigns this (Reddit.java:236)
    public static SharedPreferences authentication;

    @Nullable public static String refresh;

    /**
     * Scopes granted to the current token, as reported by Reddit on the most recent (re)auth. Null
     * until a token has been obtained this session. OAuth scopes are frozen at consent time and the
     * refresh-token flow can't widen them, so this is how we tell whether a user authorized before a
     * newly-added scope (e.g. {@code modmail}) existed.
     *
     * <p>volatile: written from the authentication background threads and read on the main thread,
     * like {@link #isLoggedIn}. Without it a screen can read a stale null and skip the warning it
     * exists to show.
     */
    @Nullable public static volatile Set<String> grantedScopes;

    /**
     * Records the scopes Reddit reported for a freshly authenticated token. Reads the data node
     * rather than calling {@link OAuthData#getScopes()}, which splits {@code data("scope")} with no
     * null test: a token response carrying no scope field would throw from inside the authentication
     * it is called from, turning a good refresh into a failed one.
     */
    private static void recordGrantedScopes(OAuthData data) {
        final JsonNode scope = data.getDataNode().get("scope");
        if (scope != null && scope.isTextual()) {
            grantedScopes = new HashSet<>(Arrays.asList(scope.asText().split(" ")));
        }
    }

    /**
     * @return true if the current token is known to include {@code scope}. Also returns true when
     *     scopes are unknown (e.g. offline before the first refresh) so callers don't warn on
     *     incomplete information. A {@code *} grant counts as all scopes.
     */
    public static boolean hasScope(String scope) {
        // Read once into a local: the field is written from the reauth background thread, so a
        // null check on the field itself would not hold for the reads that follow it.
        final Set<String> scopes = grantedScopes;
        if (scopes == null) {
            return true;
        }
        return scopes.contains("*") || scopes.contains(scope);
    }

    public boolean hasDone;
    public static boolean didOnline;

    @Nullable private static OkHttpAdapter httpAdapter;

    /**
     * {@link #name} for use as a preferences key or a message substitution, falling back to the
     * empty string. Reads and writes must both go through this, or a null name would read one key
     * and write another.
     */
    public static String nameOrEmpty() {
        final String current = name;
        return current == null ? "" : current;
    }

    /**
     * Rewrites a bare account name in the stored "accounts" set to the newer "name:token" form.
     *
     * <p>No-op unless the bare name is actually present, so it is safe to call on every
     * authentication. This block was previously copy-pasted verbatim into five call sites, where it
     * had drifted: four used {@code apply()} and one {@code commit()}, while all five carried a
     * "force commit" comment. It uses {@code apply()} — the write still lands, and it never blocks
     * on disk, which matters because some callers run on the main thread.
     */
    public static void migrateAccountToTokenForm(@Nullable String accountName) {
        // Read the token once: it is the refresh token JRAW handed back, which can be null, and
        // concatenating it regardless wrote "name:null" — an entry that names a token nothing can
        // spend, which no later sign-in matches and no drawer row can switch to. The bare name is
        // still usable through the index lookup, and the next authentication holding a real token
        // migrates it properly.
        final String token = refresh;
        if (token == null || token.isEmpty()) {
            return;
        }

        final Set<String> accounts =
                PrefUtil.getMutableStringSet(authentication, "accounts", new HashSet<String>());
        if (accounts.contains(accountName)) {
            accounts.remove(accountName);
            accounts.add(accountName + ":" + token);
            authentication.edit().putStringSet("accounts", accounts).apply();
        }
    }

    /**
     * The refresh token carried by an entry of the "accounts" set, or null for an entry stored in
     * the older bare-name form, which has none.
     */
    @Nullable
    public static String tokenOf(String accountEntry) {
        final int split = accountEntry.indexOf(':');
        return split < 0 ? null : accountEntry.substring(split + 1);
    }

    /**
     * Whether an entry of the "accounts" set belongs to {@code accountName}. An entry is
     * "name:token", or a bare name on older installs.
     *
     * <p>Every site used to test this with {@code contains}, which answers true for any substring:
     * a stored "bobby:token" matched the name "bob", so signing in or removing an account could
     * replace or delete a different one. Only the name half of the entry is compared, and case is
     * ignored — Reddit names are unique regardless of case, and the drawer already decides which
     * row is the current account with {@code equalsIgnoreCase}, so matching exactly here would let
     * a name that came back in different case than it was stored in miss its own entry and be
     * stored a second time.
     */
    public static boolean isEntryFor(String accountEntry, String accountName) {
        final int split = accountEntry.indexOf(':');
        final String storedName = split < 0 ? accountEntry : accountEntry.substring(0, split);
        return storedName.equalsIgnoreCase(accountName);
    }

    /**
     * Drops every token from {@code tokens} that has no matching entry in {@code accounts}.
     *
     * <p>"tokens" is read in exactly one way — the index-based lookup in {@code DrawerController}
     * and {@code CommentAdapter} — and that lookup only runs for an entry stored in the older
     * bare-name form, which carries no token of its own. Once every entry carries its token,
     * nothing can reach the rest: they are stored credentials no code path can spend, and one more
     * copy for the settings backup to export. With no accounts left at all, that is all of them.
     *
     * <p>While even one bare-name entry survives, those unnamed tokens are exactly the pool its
     * lookup draws from, so nothing is pruned then — deleting one would lock that account out for
     * good. {@code lasttoken} is kept regardless, being the session in use. Note this only ever
     * removes: a token the caller has already dropped cannot come back through here.
     */
    private static boolean pruneOrphanTokens(Set<String> accounts, Set<String> tokens) {
        final Set<String> named = new HashSet<>();

        for (String entry : accounts) {
            final String token = tokenOf(entry);
            if (token == null || token.isEmpty()) {
                return false;
            }
            named.add(token);
        }

        named.add(PrefUtil.getString(authentication, "lasttoken", ""));
        return tokens.retainAll(named);
    }

    /**
     * The refresh token an entry in the older bare-name form has to be matched to by position: it
     * names no token of its own, so the drawer and {@code CommentAdapter} index "tokens" by the
     * account's place in the name list.
     *
     * <p>Both sets are unordered, so that correspondence has always been a guess — but it used to
     * be an unchecked one. Removing an account now takes its token out of "tokens" with it, which
     * can leave the list shorter than the position asked for, and {@code get} on a list that short
     * throws. The bounds test the call sites carried ({@code index > tokens.size()}, then
     * {@code index -= 1}) never prevented that: it does not fire at {@code index == size}, and
     * where it does fire the decremented index is still past the end.
     */
    @Nullable
    public static String legacyTokenAt(int index) {
        if (index < 0) {
            return null;
        }

        final List<String> tokens =
                new ArrayList<>(
                        PrefUtil.getStringSet(authentication, "tokens", new HashSet<String>()));
        return index < tokens.size() ? tokens.get(index) : null;
    }

    /**
     * Prunes the stored sets where they sit, writing only when something actually goes — so the
     * reauth paths that call this on every refresh cost a read and nothing else the rest of the
     * time.
     *
     * <p>{@link #forgetAccount} and {@link #storeAccountToken} prune as they go, which cleans a
     * profile the moment an account is added or removed. This is what reaches the profile that
     * does neither, and in particular one restored from a backup: restore rewrites the preference
     * file wholesale rather than going through either path, so a backup taken before any of this
     * existed hands its orphans straight back.
     */
    public static synchronized void pruneOrphanTokens() {
        final Set<String> accounts =
                PrefUtil.getStringSet(authentication, "accounts", new HashSet<String>());
        final Set<String> tokens =
                PrefUtil.getMutableStringSet(authentication, "tokens", new HashSet<String>());

        if (pruneOrphanTokens(accounts, tokens)) {
            authentication.edit().putStringSet("tokens", tokens).apply();
        }
    }

    /**
     * Records {@code accountName}'s refresh token, replacing anything already stored for that
     * account: its "accounts" entry, and the token that entry carried.
     *
     * <p>Login used to only add, so signing in again to an account already stored appended a second
     * "name:token" entry and left both tokens in "tokens" — a duplicate the drawer's name-keyed map
     * then collapsed to whichever entry it saw last. Reauthenticate replaced the entry but likewise
     * kept the superseded token. Both are stored credentials for an account that has moved on.
     *
     * <p>Every matching entry goes, not just one, so a duplicate an earlier login left behind is
     * cleaned up on the next sign-in. Both sets are put into the caller's {@code editor} rather
     * than written here, so they land in the caller's single commit alongside "lasttoken".
     *
     * <p>Both values come straight off JRAW — the account name from {@link
     * LoggedInAccount#getFullName()}, the token from the exchange — and either can be null when
     * that response was not what it should have been. Neither set is touched then: the callers
     * used to store the concatenation regardless, which put a "null:token" entry in the store that
     * no later sign-in could match and no drawer row could switch to.
     */
    public static synchronized void storeAccountToken(
            SharedPreferences.Editor editor,
            @Nullable String accountName,
            @Nullable String refreshToken) {
        if (accountName == null || refreshToken == null) {
            LogUtil.e("Not storing account credentials: name " + accountName
                    + ", refresh token " + (refreshToken == null ? "missing" : "present"));
            return;
        }

        final Set<String> accounts =
                PrefUtil.getMutableStringSet(authentication, "accounts", new HashSet<String>());
        final Set<String> tokens =
                PrefUtil.getMutableStringSet(authentication, "tokens", new HashSet<String>());

        for (Iterator<String> it = accounts.iterator(); it.hasNext(); ) {
            final String entry = it.next();
            if (isEntryFor(entry, accountName)) {
                it.remove();
                final String superseded = tokenOf(entry);
                if (superseded != null) {
                    tokens.remove(superseded);
                }
            }
        }

        accounts.add(accountName + ":" + refreshToken);
        tokens.add(refreshToken);
        pruneOrphanTokens(accounts, tokens);

        editor.putStringSet("accounts", accounts).putStringSet("tokens", tokens);
    }

    /**
     * Forgets a stored account: drops its entry from the "accounts" set and, with it, its refresh
     * token from the parallel "tokens" set.
     *
     * <p>Account removal used to prune "accounts" alone, which left the removed account's refresh
     * token sitting in "tokens" — still a live credential, and still reachable through the
     * index-based fallback the drawer uses for entries stored in the older bare-name form. Both
     * writes land in one {@code commit()}, because every caller restarts the process immediately
     * afterwards.
     *
     * <p>Entries are matched with {@link #isEntryFor}, so removing "bob" no longer takes "bobby"
     * with it. Tokens left over from before this pruning existed go too, under the conditions
     * {@link #pruneOrphanTokens} describes.
     */
    public static synchronized void forgetAccount(String accountName) {
        final Set<String> tokens =
                PrefUtil.getMutableStringSet(authentication, "tokens", new HashSet<String>());
        final Set<String> kept = new HashSet<>();

        for (String s : PrefUtil.getStringSet(authentication, "accounts", new HashSet<String>())) {
            if (isEntryFor(s, accountName)) {
                final String token = tokenOf(s);
                if (token != null) {
                    tokens.remove(token);
                }
            } else {
                kept.add(s);
            }
        }

        pruneOrphanTokens(kept, tokens);

        authentication
                .edit()
                .putStringSet("accounts", kept)
                .putStringSet("tokens", tokens)
                .commit();
    }

    /**
     * Empties the WebView cookie jar, which is shared app-wide (the login flow, the in-app browser
     * and the wiki viewer all use the one {@link CookieManager}).
     *
     * <p>Signing out only ever dropped the stored tokens, so the jar kept the reddit.com session
     * cookies of the account that had just been signed out of — enough for any of those WebViews to
     * still be logged in as them. Login and Reauthenticate already clear the jar on the way in;
     * this is the matching clear on the way out.
     */
    public static void clearWebViewCookies() {
        // getInstance() loads the WebView provider, and throws when there is not a usable one —
        // the package being updated out from under the process, or disabled entirely. The screens
        // that already clear the jar are WebView screens, so a failure there is fatal anyway; this
        // runs on sign-out, which otherwise never touches a WebView, and must not take the account
        // switch down with it. A jar that could not be opened has no cookies to leak.
        try {
            final CookieManager cookieManager = CookieManager.getInstance();
            cookieManager.removeAllCookies(null);
            cookieManager.flush();
        } catch (Exception e) {
            LogUtil.e(e, "Could not clear the WebView cookie jar");
        }
    }

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
            name = PrefUtil.getString(Reddit.appRestart, "name", "");
            final String lastToken = PrefUtil.getString(authentication, "lasttoken", "");
            refresh = lastToken;
            if ((name.isEmpty() || !isLoggedIn) && !lastToken.isEmpty()) {
                for (String s :
                        PrefUtil.getStringSet(
                                Authentication.authentication,
                                "accounts",
                                new HashSet<String>())) {
                    if (s.contains(lastToken)) {
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
    public void updateToken(Context c, boolean reportToSnackbar, @Nullable Runnable onComplete) {
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
        @Nullable final Runnable onComplete;

        public UpdateToken(Context c) {
            this(c, true, null);
        }

        public UpdateToken(Context c, boolean reportToSnackbar) {
            this(c, reportToSnackbar, null);
        }

        public UpdateToken(Context c, boolean reportToSnackbar, @Nullable Runnable onComplete) {
            this.context = c;
            this.reportToSnackbar = reportToSnackbar;
            this.onComplete = onComplete;
        }

        @Override
        protected void onPreExecute() {
            if (reportToSnackbar) ReauthNotifier.onStarted();
        }

        @Override
        protected void onPostExecute(@Nullable Boolean success) {
            // A null result means no refresh was attempted (e.g. not yet authed); fall back to the
            // live auth state. A non-null result is the real outcome of the refresh, so a failed
            // refresh reports false even while the old (not-yet-expired) token still reads as
            // authenticated — that is what surfaces the reauth failure snackbar and its Retry.
            if (reportToSnackbar) {
                ReauthNotifier.onFinished(success != null ? success : isReauthed());
            } else {
                // A silent refresh still clears a stale failure state: it is the path that keeps
                // the token fresh, which in turn stops the resume-time refresh (gated on the token
                // having expired) from ever running to clear it. Note that success here does not
                // always mean a request was made — if "expires" moved into the future while this
                // task was queued, the branch above reports success straight off the cached
                // backedCreds. That is still safe to clear on, because only a *successful* refresh
                // ever writes "expires", and that success has already cleared the failure state.
                if (success != null && success) ReauthNotifier.onSucceededSilently();
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
        @Nullable
        protected Boolean doInBackground(Void... params) {
            Boolean result = null;
            maybeBreakReauth();
            // updateToken only builds an UpdateToken on the branch where the client already
            // exists, and nothing ever sets it back to null, so this is defensive. Returning null
            // is the documented "no refresh was attempted" result onPostExecute already handles.
            final RedditClient client = reddit;
            if (client == null) {
                return result;
            }
            if (authedOnce && NetworkUtil.isConnected(context)) {
                didOnline = true;
                if (name != null && !name.isEmpty()) {
                    Log.v(LogUtil.getTag(), "REAUTH");
                    // Ensure refresh token is available from SharedPreferences if null
                    if (refresh == null || refresh.isEmpty()) {
                        refresh = PrefUtil.getString(authentication, "lasttoken", "");
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

                            OAuthHelper oAuthHelper = client.getOAuthHelper();

                            oAuthHelper.setRefreshToken(refresh);
                            OAuthData finalData;
                            if (authentication.contains("backedCreds")
                                    && authentication.getLong("expires", 0)
                                            > Calendar.getInstance().getTimeInMillis()) {
                                finalData =
                                        oAuthHelper.refreshToken(
                                                credentials,
                                                PrefUtil.getString(
                                                        authentication,
                                                        "backedCreds",
                                                        "")); // does a request
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
                            client.authenticate(finalData);
                            recordGrantedScopes(finalData);
                            refresh = oAuthHelper.getRefreshToken();

                            if (client.isAuthenticated()) {
                                if (me == null) {
                                    // Don't let a me() blip skip isLoggedIn=true: the token is
                                    // already authenticated at this point.
                                    try {
                                        me = client.me();
                                    } catch (Exception meError) {
                                        LogUtil.e(meError, "reddit.me() failed after auth");
                                    }
                                }
                                Authentication.isLoggedIn = true;
                            }
                            // A refresh just proved which token this account really uses; drop any
                            // the account list no longer claims.
                            pruneOrphanTokens();
                            Log.v(LogUtil.getTag(), "AUTHENTICATED");
                            result = client.isAuthenticated();
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

                            authData = client.getOAuthHelper().easyAuth(fcreds);
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

                            client.authenticate(authData);
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
                                                            // Re-auth prompt on a context that is finishing; the next call
                                                            // prompts again.
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
        @Nullable final Runnable onComplete;

        public VerifyCredentials(Context context) {
            this(context, true, null);
        }

        public VerifyCredentials(
                Context context, boolean reportToSnackbar, @Nullable Runnable onComplete) {
            mContext = context;
            lastToken = PrefUtil.getString(authentication, "lasttoken", "");
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
            // Symmetric with UpdateToken: a silent success still has to reach the notifier, both to
            // clear a stale failure and to mark any outstanding reported reauth moot. This path runs
            // instead of UpdateToken whenever the client had not been built yet (see updateToken).
            //
            // isReauthed() is only a valid success test *because* that client is brand new: on an
            // already-authenticated client it stays true even after a failed refresh (it checks for
            // any authData, not expiry, and doVerify swallows its exception rather than rethrowing),
            // so it would report a failure as success. Nothing can have authenticated this one
            // first — reddit == null means the constructor took the offline branch, which queues no
            // tasks, and reddit is never reset to null. Revisit this if that ever changes.
            else if (isReauthed()) ReauthNotifier.onSucceededSilently();
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
                // Debug-only spin loop: an interrupt just re-checks the toggle.
            }
        }
    }

    private static boolean isBreakReauthEnabled() {
        return SettingValues.prefs != null
                && SettingValues.prefs.getBoolean(SettingValues.PREF_DEBUG_BREAK_REAUTH, false);
    }

    public static void doVerify(
            String lastToken,
            @Nullable RedditClient baseReddit,
            boolean single,
            Context mContext) {
        try {

            if (BuildConfig.DEBUG) LogUtil.v("TOKEN IS " + lastToken);
            if (baseReddit == null) {
                return;
            }
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
                                        credentials,
                                        PrefUtil.getString(authentication, "backedCreds", ""));
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
                        // Cold start authenticates here rather than through UpdateToken, so the
                        // scopes have to be recorded here too, or hasScope() has nothing to answer
                        // from for the rest of the session.
                        recordGrantedScopes(finalData);
                        authentication
                                .edit()
                                .putString("backedCreds", finalData.getDataNode().toString())
                                .apply();
                        refresh = oAuthHelper.getRefreshToken();
                        if (BuildConfig.DEBUG) {
                            LogUtil.v("ACCESS TOKEN IS " + finalData.getAccessToken());
                        }

                        Authentication.isLoggedIn = true;

                        // Same reconcile as the resume-time refresh, on the cold-start path.
                        // Gated on !single because the per-account client CommentAdapter builds
                        // refreshes somebody else's token, not the session's.
                        pruneOrphanTokens();

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

                    authData = baseReddit.getOAuthHelper().easyAuth(fcreds);
                    authentication
                            .edit()
                            .putLong("expires", Calendar.getInstance().getTimeInMillis() + Constants.EXPIRES_VALUE)
                            .apply();
                    authentication
                            .edit()
                            .putString("backedCreds", authData.getDataNode().toString())
                            .apply();
                    baseReddit.authenticate(authData);

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
