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
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebViewClient;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Map;

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

        String userAgent = webSettings.getUserAgentString();
        Log.v("Log into Reddit", "WebView original User-Agent: " + userAgent);

        // Remove WebView identifier that Reddit uses to block login
        // "wv" in the UA and "Android WebView" in sec-ch-ua cause Reddit
        // to reject credentials with "Invalid username or password"
        String chromeUserAgent = userAgent
                .replace("; wv)", ")")
                .replace("Version/4.0 ", "");
        webSettings.setUserAgentString(chromeUserAgent);
        Log.v("Log into Reddit", "WebView modified User-Agent: " + chromeUserAgent);

        webView.addJavascriptInterface(new Object() {
            @JavascriptInterface
            public void logFetch(String message) {
                Log.e("Log into Reddit", "Fetch intercept: " + message);
            }
        }, "LoginDebug");

        webView.setWebViewClient(
                new WebViewClient() {
                    private static final String LOGIN_TAG = "Log into Reddit";

                    @Override
                    public void onPageStarted(WebView view, String url, Bitmap favicon) {
                        Log.v(LOGIN_TAG, "onPageStarted: " + url);
                        String cookies =
                                CookieManager.getInstance().getCookie(url);
                        Log.v(LOGIN_TAG, "Cookies for URL: " + cookies);
                        LogUtil.v(url);
                        if (url.contains("code=")) {
                            Log.v(LOGIN_TAG, "Auth code received in URL: " + url);
                            // Authentication code received, prevent HTTP call from being made.
                            webView.stopLoading();
                            new UserChallengeTask(oAuthHelper, credentials).execute(url);
                            webView.setVisibility(View.GONE);
                        } else if (url.contains("error=")) {
                            Log.e(LOGIN_TAG, "Error in URL: " + url);
                        }
                    }

                    @Override
                    public void onPageFinished(WebView view, String url) {
                        Log.v(LOGIN_TAG, "onPageFinished: " + url);
                        String title = view.getTitle();
                        if (title != null) {
                            Log.v(LOGIN_TAG, "Page title: " + title);
                        }
                        // Inject fetch interceptor to capture login response body
                        view.evaluateJavascript(
                                "(function() {"
                                        + "  if (window._fetchIntercepted) return;"
                                        + "  window._fetchIntercepted = true;"
                                        + "  var origFetch = window.fetch;"
                                        + "  window.fetch = function() {"
                                        + "    var url = arguments[0];"
                                        + "    if (typeof url === 'object') url = url.url;"
                                        + "    LoginDebug.logFetch('fetch called: ' + url);"
                                        + "    var opts = arguments[1] || {};"
                                        + "    if (opts.body) {"
                                        + "      LoginDebug.logFetch('fetch body: '"
                                        + "        + opts.body.toString()"
                                        + "            .substring(0, 2000));"
                                        + "    }"
                                        + "    return origFetch.apply(this, arguments)"
                                        + "      .then(function(resp) {"
                                        + "        var cloned = resp.clone();"
                                        + "        if (url && url.toString()"
                                        + "            .indexOf('account/login') !== -1) {"
                                        + "          LoginDebug.logFetch("
                                        + "            'login response status: '"
                                        + "            + resp.status);"
                                        + "          cloned.text().then(function(body) {"
                                        + "            LoginDebug.logFetch("
                                        + "              'login response body: '"
                                        + "              + body.substring(0, 4000));"
                                        + "          });"
                                        + "        }"
                                        + "        return resp;"
                                        + "      });"
                                        + "  };"
                                        + "  var origXHR = XMLHttpRequest.prototype.open;"
                                        + "  XMLHttpRequest.prototype.open ="
                                        + "    function(method, xurl) {"
                                        + "      this._debugUrl = xurl;"
                                        + "      this._debugMethod = method;"
                                        + "      return origXHR.apply(this, arguments);"
                                        + "    };"
                                        + "  var origSend = XMLHttpRequest.prototype.send;"
                                        + "  XMLHttpRequest.prototype.send ="
                                        + "    function(body) {"
                                        + "      if (this._debugUrl && this._debugUrl"
                                        + "          .toString()"
                                        + "          .indexOf('account/login') !== -1) {"
                                        + "        LoginDebug.logFetch("
                                        + "          'XHR ' + this._debugMethod"
                                        + "          + ' ' + this._debugUrl);"
                                        + "        if (body) {"
                                        + "          LoginDebug.logFetch("
                                        + "            'XHR body: '"
                                        + "            + body.toString()"
                                        + "                .substring(0, 2000));"
                                        + "        }"
                                        + "        var xhr = this;"
                                        + "        this.addEventListener('load',"
                                        + "          function() {"
                                        + "            LoginDebug.logFetch("
                                        + "              'XHR response status: '"
                                        + "              + xhr.status);"
                                        + "            LoginDebug.logFetch("
                                        + "              'XHR response body: '"
                                        + "              + xhr.responseText"
                                        + "                  .substring(0, 4000));"
                                        + "          });"
                                        + "      }"
                                        + "      return origSend.apply(this, arguments);"
                                        + "    };"
                                        + "  LoginDebug.logFetch("
                                        + "    'fetch/XHR interceptors installed');"
                                        + "})()",
                                value ->
                                        Log.v(
                                                LOGIN_TAG,
                                                "JS inject result: " + value));

                        // Rewrite the authorize button value to 'Allow' (English) before
                        // form submission. Reddit's localized OAuth consent page submits
                        // a native form POST to /svc/shreddit/oauth-grant with the button's
                        // localized value (e.g. '허용' in Korean). Non-English values cause
                        // access_denied on Reddit's backend. We intercept in capture phase
                        // so the rewrite happens before FormData is read.
                        view.evaluateJavascript(
                                "(function() {"
                                        + "  if (window._authorizeRewriteInstalled) return;"
                                        + "  window._authorizeRewriteInstalled = true;"
                                        + "  document.addEventListener('submit', function(e) {"
                                        + "    var s = e.submitter;"
                                        + "    if (s && s.name === 'authorize'"
                                        + "        && s.value !== 'Allow') {"
                                        + "      LoginDebug.logFetch("
                                        + "        'authorize rewrite: '"
                                        + "        + s.value + ' -> Allow');"
                                        + "      s.value = 'Allow';"
                                        + "    }"
                                        + "  }, true);"
                                        + "  LoginDebug.logFetch("
                                        + "    'authorize rewrite listener installed');"
                                        + "})()",
                                value -> Log.v(LOGIN_TAG, "authorize rewrite inject: " + value));
                    }

                    @Override
                    public boolean shouldOverrideUrlLoading(WebView view, String url) {
                        Log.v(LOGIN_TAG, "shouldOverrideUrlLoading: " + url);
                        return false;
                    }

                    @Override
                    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
                    public WebResourceResponse shouldInterceptRequest(
                            WebView view, WebResourceRequest request) {
                        String url = request.getUrl().toString();
                        if (url.contains("reddit.com")) {
                            String method = request.getMethod();
                            Log.v(
                                    LOGIN_TAG,
                                    "shouldInterceptRequest: "
                                            + method
                                            + " "
                                            + url);
                            Map<String, String> headers = request.getRequestHeaders();
                            if (headers != null) {
                                for (Map.Entry<String, String> entry :
                                        headers.entrySet()) {
                                    Log.v(
                                            LOGIN_TAG,
                                            "  Request header: "
                                                    + entry.getKey()
                                                    + ": "
                                                    + entry.getValue());
                                }
                            }
                            // Intercept the login POST to capture the response body
                            if (url.contains("/svc/shreddit/account/login")
                                    && "POST".equals(method)) {
                                return proxyLoginRequest(request);
                            }
                        }
                        return null;
                    }

                    @Override
                    @TargetApi(Build.VERSION_CODES.M)
                    public void onReceivedError(
                            WebView view,
                            WebResourceRequest request,
                            WebResourceError error) {
                        Log.e(
                                LOGIN_TAG,
                                "onReceivedError: "
                                        + error.getErrorCode()
                                        + " "
                                        + error.getDescription()
                                        + " URL: "
                                        + request.getUrl());
                    }

                    @Override
                    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
                    public void onReceivedHttpError(
                            WebView view,
                            WebResourceRequest request,
                            WebResourceResponse errorResponse) {
                        String url = request.getUrl().toString();
                        String method = request.getMethod();
                        Log.e(
                                LOGIN_TAG,
                                "onReceivedHttpError: "
                                        + errorResponse.getStatusCode()
                                        + " "
                                        + errorResponse.getReasonPhrase()
                                        + " Method: "
                                        + method
                                        + " URL: "
                                        + url);
                        Map<String, String> reqHeaders = request.getRequestHeaders();
                        if (reqHeaders != null) {
                            for (Map.Entry<String, String> entry :
                                    reqHeaders.entrySet()) {
                                Log.e(
                                        LOGIN_TAG,
                                        "  Error request header: "
                                                + entry.getKey()
                                                + ": "
                                                + entry.getValue());
                            }
                        }
                        Map<String, String> respHeaders =
                                errorResponse.getResponseHeaders();
                        if (respHeaders != null) {
                            for (Map.Entry<String, String> entry :
                                    respHeaders.entrySet()) {
                                Log.e(
                                        LOGIN_TAG,
                                        "  Error response header: "
                                                + entry.getKey()
                                                + ": "
                                                + entry.getValue());
                            }
                        }
                        try {
                            InputStream is = errorResponse.getData();
                            if (is != null) {
                                BufferedReader reader =
                                        new BufferedReader(
                                                new InputStreamReader(is));
                                StringBuilder sb = new StringBuilder();
                                String line;
                                while ((line = reader.readLine()) != null) {
                                    sb.append(line).append("\n");
                                }
                                Log.e(
                                        LOGIN_TAG,
                                        "  Error response body: "
                                                + sb.toString());
                            }
                        } catch (Exception e) {
                            Log.e(
                                    LOGIN_TAG,
                                    "  Failed to read error response body: "
                                            + e.getMessage());
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

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    private WebResourceResponse proxyLoginRequest(WebResourceRequest request) {
        final String LOGIN_TAG = "Log into Reddit";
        try {
            URL url = new URL(request.getUrl().toString());
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setInstanceFollowRedirects(false);

            // Copy all headers from the WebView request
            Map<String, String> headers = request.getRequestHeaders();
            if (headers != null) {
                for (Map.Entry<String, String> entry : headers.entrySet()) {
                    conn.setRequestProperty(entry.getKey(), entry.getValue());
                }
            }

            // Copy cookies
            String cookies =
                    CookieManager.getInstance()
                            .getCookie(request.getUrl().toString());
            if (cookies != null) {
                conn.setRequestProperty("Cookie", cookies);
                Log.v(LOGIN_TAG, "Proxy login cookies: " + cookies);
            }

            // Note: We cannot read the POST body from WebResourceRequest,
            // so this proxy sends an empty body. The response will still
            // reveal what Reddit expects (e.g., CSRF token errors).
            conn.setDoOutput(true);
            OutputStream os = conn.getOutputStream();
            os.close();

            int responseCode = conn.getResponseCode();
            String responseMessage = conn.getResponseMessage();
            Log.e(
                    LOGIN_TAG,
                    "Proxy login response: "
                            + responseCode
                            + " "
                            + responseMessage);

            // Log response headers
            Map<String, java.util.List<String>> respHeaders =
                    conn.getHeaderFields();
            if (respHeaders != null) {
                for (Map.Entry<String, java.util.List<String>> entry :
                        respHeaders.entrySet()) {
                    Log.e(
                            LOGIN_TAG,
                            "  Proxy response header: "
                                    + entry.getKey()
                                    + ": "
                                    + entry.getValue());
                }
            }

            // Read the response body
            InputStream errorStream = conn.getErrorStream();
            InputStream inputStream =
                    errorStream != null ? errorStream : conn.getInputStream();
            BufferedReader reader =
                    new BufferedReader(new InputStreamReader(inputStream));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append("\n");
            }
            reader.close();
            String body = sb.toString();
            Log.e(LOGIN_TAG, "Proxy login response body: " + body);

            conn.disconnect();
        } catch (Exception e) {
            Log.e(
                    LOGIN_TAG,
                    "Proxy login request failed: " + e.getMessage());
            e.printStackTrace();
        }

        // Return null to let the WebView handle the original request normally
        return null;
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
        private static final String LOGIN_TAG = "Log into Reddit";
        private final OAuthHelper mOAuthHelper;
        private final Credentials mCredentials;
        private MaterialDialog mMaterialDialog;

        public UserChallengeTask(OAuthHelper oAuthHelper, Credentials credentials) {
            Log.v(LOGIN_TAG, "UserChallengeTask created");
            mOAuthHelper = oAuthHelper;
            mCredentials = credentials;
        }

        @Override
        protected void onPreExecute() {
            Log.v(LOGIN_TAG, "UserChallengeTask starting OAuth exchange");
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
            Log.v(LOGIN_TAG, "doInBackground: processing challenge URL: " + params[0]);
            try {
                Log.v(LOGIN_TAG, "Calling onUserChallenge...");
                OAuthData oAuthData = mOAuthHelper.onUserChallenge(params[0], mCredentials);
                if (oAuthData != null) {
                    Log.v(LOGIN_TAG, "OAuthData received successfully");
                    Log.v(LOGIN_TAG, "Authenticating with Reddit...");
                    Authentication.reddit.authenticate(oAuthData);
                    Authentication.isLoggedIn = true;
                    String refreshToken = Authentication.reddit.getOAuthData().getRefreshToken();
                    Log.v(
                            LOGIN_TAG,
                            "Refresh token obtained: "
                                    + (refreshToken != null ? "yes" : "null"));
                    SharedPreferences.Editor editor = Authentication.authentication.edit();
                    Set<String> accounts =
                            Authentication.authentication.getStringSet(
                                    "accounts", new HashSet<String>());
                    Log.v(LOGIN_TAG, "Fetching logged-in account info...");
                    LoggedInAccount me = Authentication.reddit.me();
                    Log.v(LOGIN_TAG, "Logged in as: " + me.getFullName());
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
                    Log.v(LOGIN_TAG, "Login credentials saved successfully");
                } else {
                    Log.e(LOGIN_TAG, "onUserChallenge returned null OAuthData");
                }
                return oAuthData;
            } catch (IllegalStateException | NetworkException | OAuthException e) {
                Log.e(LOGIN_TAG, "OAuth failed: " + e.getClass().getSimpleName());
                Log.e(LOGIN_TAG, "OAuth error message: " + e.getMessage());
                e.printStackTrace();
            } catch (RuntimeException e) {
                // Catch runtime exceptions, which include Protocol exceptions from OkHttp
                if (e.getCause() instanceof java.net.ProtocolException &&
                    e.getCause().getMessage().contains("Too many follow-up requests")) {
                    Log.e(LOGIN_TAG, "OAuth redirect loop detected: " + e.getCause().getMessage());
                } else {
                    Log.e(LOGIN_TAG, "OAuth runtime error: " + e.getClass().getSimpleName() + ": " + e.getMessage());
                    if (e.getCause() != null) {
                        Log.e(LOGIN_TAG, "Caused by: " + e.getCause().getClass().getSimpleName() + ": " + e.getCause().getMessage());
                    }
                    e.printStackTrace();
                }
            } catch (Exception e) {
                Log.e(LOGIN_TAG, "Unexpected error during OAuth: " + e.getClass().getSimpleName() + ": " + e.getMessage());
                e.printStackTrace();
            }
            return null;
        }

        @Override
        protected void onPostExecute(OAuthData oAuthData) {
            // Dismiss old progress dialog
            mMaterialDialog.dismiss();

            if (oAuthData != null) {
                Log.v(LOGIN_TAG, "Login successful, starting subscription sync");
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
                Log.e(LOGIN_TAG, "Login failed: OAuthData was null in onPostExecute");
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
