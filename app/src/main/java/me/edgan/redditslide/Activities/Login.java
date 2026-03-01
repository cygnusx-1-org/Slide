package me.edgan.redditslide.Activities;

import android.annotation.TargetApi;
import android.app.Dialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.webkit.CookieManager;
import android.webkit.CookieSyncManager;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.browser.customtabs.CustomTabColorSchemeParams;
import androidx.browser.customtabs.CustomTabsIntent;
import androidx.browser.customtabs.CustomTabsService;

import com.afollestad.materialdialogs.MaterialDialog;

import me.edgan.redditslide.Authentication;
import me.edgan.redditslide.CaseInsensitiveArrayList;
import me.edgan.redditslide.Constants;
import me.edgan.redditslide.R;
import me.edgan.redditslide.Reddit;
import me.edgan.redditslide.UserSubscriptions;
import me.edgan.redditslide.Visuals.GetClosestColor;
import me.edgan.redditslide.Visuals.Palette;
import me.edgan.redditslide.util.LogUtil;
import me.edgan.redditslide.util.MiscUtil;

import net.dean.jraw.http.NetworkException;
import net.dean.jraw.http.oauth.Credentials;
import net.dean.jraw.http.oauth.OAuthData;
import net.dean.jraw.http.oauth.OAuthException;
import net.dean.jraw.http.oauth.OAuthHelper;
import net.dean.jraw.models.LoggedInAccount;
import net.dean.jraw.models.Subreddit;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Created by ccrama on 5/27/2015. */
public class Login extends BaseActivityAnim {
    final Credentials credentials =
            Credentials.installedApp(Constants.getClientId(), Constants.REDDIT_REDIRECT_URL);

    Dialog d;
    CaseInsensitiveArrayList subNames;
    String authorizationUrl;
    OAuthHelper oAuthHelper;

    @Override
    public void onCreate(Bundle savedInstance) {
        overrideSwipeFromAnywhere();
        super.onCreate(savedInstance);
        applyColorTheme("");
        try {
            setContentView(R.layout.activity_login);
        } catch (Exception e) {
            finish();
            return;
        }

        MiscUtil.setupOldSwipeModeBackground(this, getWindow().getDecorView());

        setupAppBar(R.id.toolbar, R.string.title_login, true, true);

        String[] scopes = {
            "identity",
            "modcontributors",
            "modconfig",
            "modothers",
            "modwiki",
            "creddits",
            "livemanage",
            "account",
            "privatemessages",
            "modflair",
            "modlog",
            "report",
            "modposts",
            "modwiki",
            "read",
            "vote",
            "edit",
            "submit",
            "subscribe",
            "save",
            "wikiread",
            "flair",
            "history",
            "mysubreddits",
            "wikiedit"
        };
        if (Authentication.reddit == null) {
            new Authentication(getApplicationContext());
        }
        oAuthHelper = Authentication.reddit.getOAuthHelper();
        authorizationUrl =
                oAuthHelper.getAuthorizationUrl(credentials, true, scopes).toExternalForm();
        authorizationUrl = authorizationUrl.replace("www.", "i.");
        authorizationUrl = authorizationUrl.replace("%3A%2F%2Fi", "://www");
        Log.v(LogUtil.getTag(), "Auth URL: " + authorizationUrl);
        final WebView webView = (WebView) findViewById(R.id.web);
        webView.clearCache(true);
        webView.clearHistory();
        WebSettings webSettings = webView.getSettings();
        webSettings.setJavaScriptEnabled(true);
        webSettings.setDomStorageEnabled(true);
        webSettings.setDatabaseEnabled(true);
        webSettings.setMinimumFontSize(1);
        webSettings.setMinimumLogicalFontSize(1);

        final CookieManager cookieManager = CookieManager.getInstance();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            cookieManager.removeAllCookies(null);
            cookieManager.flush();
        } else {
            final CookieSyncManager cookieSyncMngr = CookieSyncManager.createInstance(this);
            cookieSyncMngr.startSync();
            cookieManager.removeAllCookie();
            cookieManager.removeSessionCookie();
            cookieSyncMngr.stopSync();
            cookieSyncMngr.sync();
        }

        webView.setWebViewClient(
                new WebViewClient() {
                    @Override
                    public void onPageStarted(WebView view, String url, Bitmap favicon) {
                        LogUtil.v(url);
                        if (url.contains("code=")) {
                            Log.v(LogUtil.getTag(), "WebView URL: " + url);
                            // Authentication code received, prevent HTTP call from being made.
                            webView.stopLoading();
                            new UserChallengeTask(oAuthHelper, credentials).execute(url);
                            webView.setVisibility(View.GONE);
                        }
                    }
                });

        webView.loadUrl(authorizationUrl);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.menu_login, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.open_in_browser) {
            if (authorizationUrl != null) {
                openLoginInCustomTab();
            }
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);

        Uri uri = intent.getData();
        if (uri != null && uri.toString().contains("code=")) {
            String url = uri.toString();
            Log.v(LogUtil.getTag(), "Custom Tab redirect URL: " + url);
            new UserChallengeTask(oAuthHelper, credentials).execute(url);
        }
    }

    private void openLoginInCustomTab() {
        List<ResolveInfo> resolveInfos = getCustomTabsPackages(getPackageManager());

        if (!resolveInfos.isEmpty()) {
            CustomTabsIntent.Builder builder = new CustomTabsIntent.Builder();
            builder.setShareState(CustomTabsIntent.SHARE_STATE_ON);
            builder.setDefaultColorSchemeParams(
                    new CustomTabColorSchemeParams.Builder()
                            .setToolbarColor(getResources().getColor(R.color.md_blue_500))
                            .build());
            CustomTabsIntent customTabsIntent = builder.build();
            customTabsIntent.intent.setPackage(
                    resolveInfos.get(0).activityInfo.packageName);

            try {
                customTabsIntent.launchUrl(this, Uri.parse(authorizationUrl));
            } catch (ActivityNotFoundException e) {
                Toast.makeText(this, R.string.website_external, Toast.LENGTH_SHORT).show();
            }
        } else {
            Toast.makeText(this, R.string.website_external, Toast.LENGTH_SHORT).show();
        }
    }

    private List<ResolveInfo> getCustomTabsPackages(PackageManager pm) {
        Intent activityIntent = new Intent()
                .setAction(Intent.ACTION_VIEW)
                .addCategory(Intent.CATEGORY_BROWSABLE)
                .setData(Uri.fromParts("http", "", null));

        List<ResolveInfo> resolvedActivityList = pm.queryIntentActivities(activityIntent, 0);
        List<ResolveInfo> packagesSupportingCustomTabs = new ArrayList<>();
        for (ResolveInfo info : resolvedActivityList) {
            Intent serviceIntent = new Intent();
            serviceIntent.setAction(CustomTabsService.ACTION_CUSTOM_TABS_CONNECTION);
            serviceIntent.setPackage(info.activityInfo.packageName);
            if (pm.resolveService(serviceIntent, 0) != null) {
                packagesSupportingCustomTabs.add(info);
            }
        }

        return packagesSupportingCustomTabs;
    }

    @Override
    @TargetApi(Build.VERSION_CODES.O)
    protected void setAutofill() {
        getWindow().getDecorView().setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_AUTO);
    }

    private void doSubStrings(ArrayList<Subreddit> subs) {
        subNames = new CaseInsensitiveArrayList();
        for (Subreddit s : subs) {
            subNames.add(s.getDisplayName().toLowerCase(Locale.ENGLISH));
        }
        subNames = UserSubscriptions.sort(subNames);
        if (!subNames.contains("slideforreddit")) {
            new AlertDialog.Builder(Login.this)
                    .setTitle(R.string.login_subscribe_rslideforreddit)
                    .setMessage(R.string.login_subscribe_rslideforreddit_desc)
                    .setPositiveButton(
                            R.string.btn_yes,
                            (dialog, which) -> {
                                subNames.add(2, "slideforreddit");
                                UserSubscriptions.setSubscriptions(subNames);
                                Reddit.forceRestart(Login.this, true);
                            })
                    .setNegativeButton(
                            R.string.btn_no,
                            (dialog, which) -> {
                                UserSubscriptions.setSubscriptions(subNames);
                                Reddit.forceRestart(Login.this, true);
                            })
                    .setCancelable(false)
                    .show();
        } else {
            UserSubscriptions.setSubscriptions(subNames);
            Reddit.forceRestart(Login.this, true);
        }
    }

    public void doLastStuff(final ArrayList<Subreddit> subs) {

        d.dismiss();
        new AlertDialog.Builder(Login.this)
                .setTitle(R.string.login_sync_colors)
                .setMessage(R.string.login_sync_colors_desc)
                .setPositiveButton(
                        R.string.btn_yes,
                        (dialog, which) -> {
                            for (Subreddit s : subs) {
                                if (s.getDataNode().has("key_color")
                                        && !s.getDataNode().get("key_color").asText().isEmpty()
                                        && Palette.getColor(
                                                        s.getDisplayName()
                                                                .toLowerCase(Locale.ENGLISH))
                                                == Palette.getDefaultColor()) {
                                    Palette.setColor(
                                            s.getDisplayName().toLowerCase(Locale.ENGLISH),
                                            GetClosestColor.getClosestColor(
                                                    s.getDataNode().get("key_color").asText(),
                                                    Login.this));
                                }
                            }
                            doSubStrings(subs);
                        })
                .setNegativeButton(R.string.btn_no, (dialog, which) -> doSubStrings(subs))
                .setOnDismissListener(dialog -> doSubStrings(subs))
                .create()
                .show();
    }

    private final class UserChallengeTask extends AsyncTask<String, Void, OAuthData> {
        private final OAuthHelper mOAuthHelper;
        private final Credentials mCredentials;
        private MaterialDialog mMaterialDialog;

        public UserChallengeTask(OAuthHelper oAuthHelper, Credentials credentials) {
            Log.v(LogUtil.getTag(), "UserChallengeTask()");
            mOAuthHelper = oAuthHelper;
            mCredentials = credentials;
        }

        @Override
        protected void onPreExecute() {
            // Show a dialog to indicate progress
            MaterialDialog.Builder builder =
                    new MaterialDialog.Builder(Login.this)
                            .title(R.string.login_authenticating)
                            .progress(true, 0)
                            .content(R.string.misc_please_wait)
                            .cancelable(false);
            mMaterialDialog = builder.build();
            mMaterialDialog.show();
        }

        @Override
        protected OAuthData doInBackground(String... params) {
            try {
                OAuthData oAuthData = mOAuthHelper.onUserChallenge(params[0], mCredentials);
                if (oAuthData != null) {
                    Authentication.reddit.authenticate(oAuthData);
                    Authentication.isLoggedIn = true;
                    String refreshToken = Authentication.reddit.getOAuthData().getRefreshToken();
                    SharedPreferences.Editor editor = Authentication.authentication.edit();
                    Set<String> accounts =
                            Authentication.authentication.getStringSet(
                                    "accounts", new HashSet<String>());
                    LoggedInAccount me = Authentication.reddit.me();
                    accounts.add(me.getFullName() + ":" + refreshToken);
                    Authentication.name = me.getFullName();
                    editor.putStringSet("accounts", accounts);
                    Set<String> tokens =
                            Authentication.authentication.getStringSet(
                                    "tokens", new HashSet<String>());
                    tokens.add(refreshToken);
                    editor.putStringSet("tokens", tokens);
                    editor.putString("lasttoken", refreshToken);
                    editor.remove("backedCreds");
                    Reddit.appRestart.edit().remove("back").commit();
                    editor.commit();
                } else {
                    Log.e(LogUtil.getTag(), "Passed in OAuthData was null");
                }
                return oAuthData;
            } catch (IllegalStateException | NetworkException | OAuthException e) {
                // Handle me gracefully
                Log.e(LogUtil.getTag(), "OAuth failed");
                Log.e(LogUtil.getTag(), e.getMessage());
            } catch (RuntimeException e) {
                // Catch runtime exceptions, which include Protocol exceptions from OkHttp
                if (e.getCause() instanceof java.net.ProtocolException &&
                    e.getCause().getMessage().contains("Too many follow-up requests")) {
                    // This is the specific redirect loop issue
                    Log.e(LogUtil.getTag(), "OAuth redirect loop detected: " + e.getCause().getMessage());
                } else {
                    // Other runtime exceptions
                    Log.e(LogUtil.getTag(), "OAuth runtime error: " + e.getMessage());
                    e.printStackTrace();
                }
            } catch (Exception e) {
                // Catch any other unexpected exceptions
                Log.e(LogUtil.getTag(), "Unexpected error during OAuth: " + e.getMessage());
                e.printStackTrace();
            }
            return null;
        }

        @Override
        protected void onPostExecute(OAuthData oAuthData) {
            // Dismiss old progress dialog
            mMaterialDialog.dismiss();

            if (oAuthData != null) {
                Reddit.appRestart.edit().putBoolean("firststarting", true).apply();

                UserSubscriptions.switchAccounts();
                d =
                        new MaterialDialog.Builder(Login.this)
                                .cancelable(false)
                                .title(R.string.login_starting)
                                .progress(true, 0)
                                .content(R.string.login_starting_desc)
                                .build();
                d.show();

                UserSubscriptions.syncSubredditsGetObjectAsync(Login.this);
            } else {
                // Show a dialog if data is null
                new AlertDialog.Builder(Login.this)
                        .setTitle(R.string.err_authentication)
                        .setMessage(R.string.login_failed_err_decline)
                        .setNeutralButton(
                                android.R.string.ok,
                                (dialog, which) -> {
                                    Reddit.forceRestart(Login.this, true);
                                    finish();
                                })
                        .show();
            }
        }
    }
}
