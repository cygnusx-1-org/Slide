package me.edgan.redditslide.Activities;

import android.app.Dialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.style.ForegroundColorSpan;
import android.text.style.RelativeSizeSpan;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.webkit.CookieManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.browser.customtabs.CustomTabColorSchemeParams;
import androidx.browser.customtabs.CustomTabsIntent;
import androidx.browser.customtabs.CustomTabsService;
import androidx.core.content.ContextCompat;
import androidx.webkit.WebViewCompat;
import androidx.webkit.WebViewFeature;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import me.edgan.redditslide.Authentication;
import me.edgan.redditslide.BuildConfig;
import me.edgan.redditslide.CaseInsensitiveArrayList;
import me.edgan.redditslide.Constants;
import me.edgan.redditslide.R;
import me.edgan.redditslide.Reddit;
import me.edgan.redditslide.UserSubscriptions;
import me.edgan.redditslide.Visuals.ColorPreferences;
import me.edgan.redditslide.Visuals.GetClosestColor;
import me.edgan.redditslide.Visuals.Palette;
import me.edgan.redditslide.util.DialogUtil;
import me.edgan.redditslide.util.MaterialProgressDialog;
import me.edgan.redditslide.util.MiscUtil;
import me.edgan.redditslide.util.OAuthLoginHelper;
import net.dean.jraw.http.NetworkException;
import net.dean.jraw.http.RestResponse;
import net.dean.jraw.http.oauth.Credentials;
import net.dean.jraw.http.oauth.OAuthData;
import net.dean.jraw.http.oauth.OAuthException;
import net.dean.jraw.http.oauth.OAuthHelper;
import net.dean.jraw.models.LoggedInAccount;
import net.dean.jraw.models.Subreddit;
import org.jspecify.annotations.NullMarked;

/** Created by ccrama on 5/27/2015. */
@NullMarked
public class Login extends BaseActivityAnim {
    @SuppressWarnings("NullAway.Init") // assigned in onCreate
    Credentials credentials;

    @SuppressWarnings("NullAway.Init") // assigned in onPostExecute
    Dialog d;
    @SuppressWarnings("NullAway.Init") // assigned in doSubStrings
    CaseInsensitiveArrayList subNames;
    @SuppressWarnings("NullAway.Init") // assigned in onCreate
    String authorizationUrl;
    @SuppressWarnings("NullAway.Init") // assigned in onCreate
    OAuthHelper oAuthHelper;
    /** The CSRF state JRAW embedded in the authorize URL, validated on the redirect. */
    @Nullable String expectedState;

    private static final String LOGIN_TAG = "Log into Reddit";

    /**
     * Host of the page currently loaded in the login WebView, written on the UI thread from
     * onPageStarted/onPageFinished and read from the JavascriptInterface, which Android calls on a
     * binder thread. The interface is injected into every page the WebView loads, so this is what
     * lets it ignore calls from anything that isn't Reddit. It tracks the main frame only, so it
     * does not isolate a cross-origin iframe embedded in a Reddit page.
     */
    private volatile String currentHost = "";

    /**
     * The login flow logs page URLs, cookies and request headers to help diagnose Reddit's WebView
     * login; none of that belongs in a release logcat. Release builds set minifyEnabled false, so
     * the -assumenosideeffects Log rule in proguard-rules.pro never runs and cannot strip these.
     * This constant is the single switch for everything this file logs — the login flow itself
     * never tests it, it only calls the helpers below.
     */
    private static final boolean LOG_ENABLED = BuildConfig.DEBUG;

    /** The one statement in this file that emits a log line. */
    private static void log(int priority, String message, @Nullable Throwable tr) {
        if (!LOG_ENABLED) return;
        Log.println(
                priority,
                LOGIN_TAG,
                tr == null ? message : message + '\n' + Log.getStackTraceString(tr));
    }

    private static void logV(String message) {
        log(Log.VERBOSE, message, null);
    }

    private static void logE(String message) {
        log(Log.ERROR, message, null);
    }

    private static void logE(String message, Throwable tr) {
        log(Log.ERROR, message, tr);
    }

    /** Logs the cookies the WebView holds for {@code url}, including the reddit_session cookie. */
    private static void logCookies(String url) {
        if (!LOG_ENABLED) return;
        logV("Cookies for URL: " + CookieManager.getInstance().getCookie(url));
    }

    /**
     * Dumps a header map one entry per line. Only reached from the gated helpers below, so it needs
     * no check of its own.
     */
    private static void logHeaders(
            int priority, String prefix, @Nullable Map<String, String> headers) {
        if (headers == null) return;
        for (Map.Entry<String, String> entry : headers.entrySet()) {
            log(priority, prefix + entry.getKey() + ": " + entry.getValue(), null);
        }
    }

    /** Logs an outgoing Reddit request and its headers from shouldInterceptRequest. */
    private static void logRequest(WebResourceRequest request) {
        if (!LOG_ENABLED) return;
        String url = request.getUrl().toString();
        if (!url.contains("reddit.com")) return;
        logV("shouldInterceptRequest: " + request.getMethod() + " " + redactCode(url));
        logHeaders(Log.VERBOSE, "  Request header: ", request.getRequestHeaders());
    }

    /**
     * Logs an HTTP error response in full. Note that reading getData() draws from the same stream
     * the WebView renders, so the body the JSON-error-page detector in onPageFinished looks for may
     * come up empty while this is running — one more reason it stays out of release builds.
     */
    private static void logHttpError(
            WebResourceRequest request, WebResourceResponse errorResponse) {
        if (!LOG_ENABLED) return;
        logE(
                "onReceivedHttpError: "
                        + errorResponse.getStatusCode()
                        + " "
                        + errorResponse.getReasonPhrase()
                        + " Method: "
                        + request.getMethod()
                        + " URL: "
                        + redactCode(request.getUrl().toString()));
        logHeaders(Log.ERROR, "  Error request header: ", request.getRequestHeaders());
        logHeaders(Log.ERROR, "  Error response header: ", errorResponse.getResponseHeaders());
        try {
            InputStream is = errorResponse.getData();
            if (is != null) {
                BufferedReader reader = new BufferedReader(new InputStreamReader(is));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line).append("\n");
                }
                logE("  Error response body: " + sb.toString());
            }
        } catch (Exception e) {
            logE("  Failed to read error response body: " + e.getMessage());
        }
    }

    /**
     * Blanks the {@code code} parameter of an OAuth redirect URL for logging. That value exchanges
     * for a refresh token, so it stays out of logcat even in a debug build.
     */
    private static String redactCode(String url) {
        return url.replaceAll("([?&]code=)[^&]*", "$1<redacted>");
    }

    /** True when the WebView's main frame is on Reddit, so a JavascriptInterface call is trusted. */
    private boolean isOnRedditPage() {
        String host = currentHost;
        return host.equals("reddit.com") || host.endsWith(".reddit.com");
    }

    /** Records the main-frame host backing {@link #isOnRedditPage()}. */
    private void trackHost(String url) {
        String host = Uri.parse(url).getHost();
        currentHost = host != null ? host.toLowerCase(Locale.ENGLISH) : "";
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstance) {
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
            "modmail",
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
        try {
            credentials = Credentials.installedApp(Constants.getClientId(), Constants.getRedirectUrl());
        } catch (IllegalArgumentException e) {
            Toast.makeText(this, R.string.settings_reddit_redirect_uri_invalid, Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        if (Authentication.reddit == null) {
            // Offline at startup: Authentication's offline branch never built a client, and
            // there is no way to run an OAuth flow without one.
            Toast.makeText(this, R.string.err_general, Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        oAuthHelper = Authentication.reddit.getOAuthHelper();
        java.net.URL authUrl = oAuthHelper.getAuthorizationUrl(credentials, true, scopes);
        // Capture the CSRF state JRAW embedded in the authorize URL so the redirect can be validated
        // (via OAuthLoginHelper.classifyRedirect) before handing off to the token exchange.
        expectedState = Uri.parse(authUrl.toExternalForm()).getQueryParameter("state");
        authorizationUrl = authUrl.toExternalForm();
        authorizationUrl = authorizationUrl.replace("www.", "i.");
        authorizationUrl = authorizationUrl.replace("%3A%2F%2Fi", "://www");
        logV("Auth URL: " + authorizationUrl);
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
        cookieManager.removeAllCookies(null);
        cookieManager.flush();

        String userAgent = webSettings.getUserAgentString();
        logV("WebView original User-Agent: " + userAgent);

        // Remove WebView identifier that Reddit uses to block login
        // "wv" in the UA and "Android WebView" in sec-ch-ua cause Reddit
        // to reject credentials with "Invalid username or password"
        String chromeUserAgent = userAgent
                .replace("; wv)", ")")
                .replace("Version/4.0 ", "");
        webSettings.setUserAgentString(chromeUserAgent);
        logV("WebView modified User-Agent: " + chromeUserAgent);

        // This interface is exposed to every page the WebView loads, not just Reddit's, and
        // navigation is not restricted (shouldOverrideUrlLoading returns false). Both methods check
        // the current host so an unrelated page cannot spam the log or trigger the failure dialog.
        webView.addJavascriptInterface(new Object() {
            @JavascriptInterface
            public void logFetch(String message) {
                if (!isOnRedditPage()) {
                    return;
                }
                logE("Fetch intercept: " + message);
            }

            // Called from the WebView when a loaded reddit page's text starts with '{'. Confirm it's a
            // bare-JSON error page (the OAuth flow dead-ended instead of redirecting to our scheme) and,
            // if so, surface a clear message instead of leaving "{}" on screen. Runs on a binder thread.
            @JavascriptInterface
            public void onPossibleErrorPage(String body) {
                if (!isOnRedditPage() || !OAuthLoginHelper.looksLikeJsonErrorPage(body)) {
                    return;
                }
                logE("OAuth flow dead-ended on an error page: " + body);
                runOnUiThread(() -> showLoginFailedDialog());
            }
        }, "LoginDebug");

        webView.setWebViewClient(
                new WebViewClient() {
                    @Override
                    public void onPageStarted(WebView view, String url, Bitmap favicon) {
                        // Track the host before the page can run any script, so the
                        // JavascriptInterface never sees a stale origin.
                        trackHost(url);
                        logV("onPageStarted: " + redactCode(url));
                        logCookies(url);
                        handleOAuthRedirect(url, webView);
                    }

                    @Override
                    public void onPageFinished(WebView view, String url) {
                        trackHost(url);
                        logV("onPageFinished: " + redactCode(url));
                        String title = view.getTitle();
                        if (title != null) {
                            logV("Page title: " + title);
                        }
                        // Inject the fetch/XHR interceptor that feeds logFetch. It is pure
                        // diagnostics: it monkey-patches the page's fetch and XMLHttpRequest and
                        // clones every fetch response, so it stays out of release builds where
                        // logFetch discards everything it is handed.
                        if (LOG_ENABLED) {
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
                                    value -> logV("JS inject result: " + value));
                        }

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
                                value -> logV("authorize rewrite inject: " + value));

                        // Detect the OAuth flow dead-ending on a bare-JSON error page (e.g. "{}")
                        // rendered in the WebView when the redirect never fires — otherwise the user is
                        // stuck with no feedback. The reddit.com host check plus the bare-{…} shape keep
                        // this from firing on normal consent/login pages.
                        view.evaluateJavascript(
                                "(function() {"
                                        + "  try {"
                                        + "    if (location.host.indexOf('reddit.com') === -1) return;"
                                        + "    var t = (document.body ? document.body.innerText : '')"
                                        + "        .trim();"
                                        + "    if (t.length > 0 && t.charAt(0) === '{') {"
                                        + "      LoginDebug.onPossibleErrorPage(t);"
                                        + "    }"
                                        + "  } catch (e) {}"
                                        + "})()",
                                null);
                    }

                    @Override
                    public boolean shouldOverrideUrlLoading(WebView view, String url) {
                        logV("shouldOverrideUrlLoading: " + redactCode(url));
                        return false;
                    }

                    @Override
                    public @Nullable WebResourceResponse shouldInterceptRequest(
                            WebView view, WebResourceRequest request) {
                        // Diagnostics only — always returns null so the WebView handles every
                        // request itself.
                        logRequest(request);
                        return null;
                    }

                    @Override
                    public void onReceivedError(
                            WebView view,
                            WebResourceRequest request,
                            WebResourceError error) {
                        logE(
                                "onReceivedError: "
                                        + error.getErrorCode()
                                        + " "
                                        + error.getDescription()
                                        + " URL: "
                                        + redactCode(request.getUrl().toString()));
                    }

                    @Override
                    public void onReceivedHttpError(
                            WebView view,
                            WebResourceRequest request,
                            WebResourceResponse errorResponse) {
                        logHttpError(request, errorResponse);
                    }
                });

        // Hide Reddit's cookie consent wrapper before any page script runs. It can appear
        // behind the login form and steal focus from inputs. Inject at document-start so the
        // CSS rule + MutationObserver land before Reddit's own scripts mount the element.
        if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
            WebViewCompat.addDocumentStartJavaScript(
                    webView,
                    "(function(){"
                            + "var ID='data-protection-consent-wrapper';"
                            + "var addStyle=function(){"
                            + "if(document.getElementById('_dpcStyle'))return;"
                            + "var s=document.createElement('style');"
                            + "s.id='_dpcStyle';"
                            + "s.textContent='#'+ID+'{display:none!important}';"
                            + "(document.head||document.documentElement||document)"
                            + ".appendChild(s);"
                            + "};"
                            + "var kill=function(){"
                            + "var el=document.getElementById(ID);"
                            + "if(el)el.remove();"
                            + "};"
                            + "addStyle();kill();"
                            + "new MutationObserver(function(){addStyle();kill();})"
                            + ".observe(document.documentElement||document,"
                            + "{childList:true,subtree:true});"
                            + "})()",
                    Collections.singleton("https://*.reddit.com"));
        }

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
        if (uri != null) {
            logV("Custom Tab redirect URL: " + redactCode(uri.toString()));
            handleOAuthRedirect(uri.toString(), null);
        }
    }

    /**
     * Decides what to do with an OAuth redirect URL via {@link OAuthLoginHelper#classifyRedirect}:
     * exchange a valid code (after stopping/hiding the WebView, when present), or surface a "Login
     * Failed" dialog for an access-denied, state-mismatch, or other error. A non-redirect URL (a
     * normal consent/login page) is left to keep loading. Shared by the in-app WebView
     * ({@code webView} non-null) and the Custom Tab ({@code webView} null) paths.
     */
    private void handleOAuthRedirect(String url, @Nullable WebView webView) {
        Uri uri = Uri.parse(url);
        String code;
        String state;
        String error;
        try {
            code = uri.getQueryParameter("code");
            state = uri.getQueryParameter("state");
            error = uri.getQueryParameter("error");
        } catch (UnsupportedOperationException e) {
            // Opaque URI (no query component) — nothing to act on.
            return;
        }

        OAuthLoginHelper.RedirectResult result =
                OAuthLoginHelper.classifyRedirect(code, state, error, expectedState);
        switch (result.action) {
            case EXCHANGE_CODE:
                logV("Auth code received, exchanging for token");
                if (webView != null) {
                    // Prevent the WebView from making the HTTP call to the redirect URI itself.
                    webView.stopLoading();
                    webView.setVisibility(View.GONE);
                }
                new UserChallengeTask(oAuthHelper, credentials).execute(url);
                break;
            case ACCESS_DENIED:
                logE("OAuth redirect: access denied");
                if (webView != null) {
                    webView.stopLoading();
                }
                showLoginFailedDialog(R.string.login_failed_err_decline);
                break;
            case STATE_MISMATCH:
            case OAUTH_ERROR:
                logE(
                        "OAuth redirect error: action="
                                + result.action
                                + " error="
                                + result.errorValue);
                if (webView != null) {
                    webView.stopLoading();
                }
                showLoginFailedDialog(R.string.login_failed_oauth_error_page);
                break;
            case NONE:
            default:
                // Not an OAuth redirect (a normal page) — keep loading.
                break;
        }
    }

    private void openLoginInCustomTab() {
        // The external browser / Custom Tab relies on a static manifest <intent-filter> to
        // relaunch the app when Reddit redirects to the configured Redirect URI. If that URI
        // isn't handled by one of this app's own filters, the redirect silently strands the
        // browser on Reddit's "Redirecting to…" page, so warn instead of launching.
        String redirectUri = Constants.getRedirectUrl();
        if (!isRedirectUriRegistered(redirectUri)) {
            showUnsupportedRedirectUriDialog(redirectUri);
            return;
        }

        List<ResolveInfo> resolveInfos = getCustomTabsPackages(getPackageManager());

        if (!resolveInfos.isEmpty()) {
            CustomTabsIntent.Builder builder = new CustomTabsIntent.Builder();
            builder.setShareState(CustomTabsIntent.SHARE_STATE_ON);
            builder.setDefaultColorSchemeParams(
                    new CustomTabColorSchemeParams.Builder()
                            .setToolbarColor(ContextCompat.getColor(Login.this, R.color.md_blue_500))
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

    /**
     * Resolves the configured Redirect URI against this app's own intent filters via the package
     * manager. This respects Android's exact scheme/host/port/path matching and stays in sync with
     * the manifest automatically (no hardcoded list of supported URIs).
     */
    private boolean isRedirectUriRegistered(String redirectUri) {
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(redirectUri));
        intent.addCategory(Intent.CATEGORY_BROWSABLE);
        for (ResolveInfo info : getPackageManager().queryIntentActivities(intent, 0)) {
            if (info.activityInfo != null
                    && getPackageName().equals(info.activityInfo.packageName)) {
                return true;
            }
        }
        return false;
    }

    private void showUnsupportedRedirectUriDialog(String redirectUri) {
        int accentColor = new ColorPreferences(Login.this).getColor("");
        CharSequence styled =
                warningText(
                        accentColor,
                        getString(R.string.login_unsupported_redirect_uri, redirectUri));
        DialogUtil.showWithCardBackground(new AlertDialog.Builder(Login.this)
                .setTitle(R.string.login_unsupported_redirect_uri_title)
                .setMessage(styled)
                .setNeutralButton(android.R.string.ok, (dialog, which) -> dialog.dismiss()));
    }

    /**
     * Shows the "Login Failed" dialog when the in-app WebView login dead-ends on a bare-JSON Reddit
     * error page (typically a misconfigured API Key/Client ID or Redirect URI), then closes the login
     * screen on dismiss so the user isn't left staring at "{}".
     */
    private void showLoginFailedDialog() {
        showLoginFailedDialog(R.string.login_failed_oauth_error_page);
    }

    private void showLoginFailedDialog(int messageResId) {
        if (isFinishing() || isDestroyed()) {
            return;
        }
        int accentColor = new ColorPreferences(Login.this).getColor("");
        CharSequence styled = warningText(accentColor, getString(messageResId));
        DialogUtil.showWithCardBackground(new AlertDialog.Builder(Login.this)
                .setTitle(R.string.login_unsupported_redirect_uri_title)
                .setMessage(styled)
                .setCancelable(false)
                .setNeutralButton(android.R.string.ok, (dialog, which) -> dialog.dismiss())
                .setOnDismissListener(dialog -> finish()));
    }

    /**
     * Prefixes {@code text} with a 2×, accent-tinted ⚠ glyph so the message reads as an alert at a
     * glance, matching how the rest of Slide styles attention text.
     */
    private CharSequence warningText(int accentColor, CharSequence text) {
        String symbol = "⚠";
        SpannableString styled = new SpannableString(symbol + "  " + text);
        styled.setSpan(
                new RelativeSizeSpan(2f), 0, symbol.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        styled.setSpan(
                new ForegroundColorSpan(accentColor),
                0,
                symbol.length(),
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        return styled;
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
    protected void setAutofill() {
        getWindow().getDecorView().setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_AUTO);
    }

    private void doSubStrings(ArrayList<Subreddit> subs) {
        subNames = new CaseInsensitiveArrayList();
        for (Subreddit s : subs) {
            subNames.add(MiscUtil.orEmpty(s.getDisplayName()).toLowerCase(Locale.ENGLISH));
        }
        subNames = UserSubscriptions.sort(subNames);
        if (!subNames.contains("slideforreddit")) {
            DialogUtil.showWithCardBackground(new AlertDialog.Builder(Login.this)
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
                    );
        } else {
            UserSubscriptions.setSubscriptions(subNames);
            Reddit.forceRestart(Login.this, true);
        }
    }

    public void doLastStuff(final ArrayList<Subreddit> subs) {

        d.dismiss();
        DialogUtil.showWithCardBackground(new AlertDialog.Builder(Login.this)
                .setTitle(R.string.login_sync_colors)
                .setMessage(R.string.login_sync_colors_desc)
                .setPositiveButton(
                        R.string.btn_yes,
                        (dialog, which) -> {
                            for (Subreddit s : subs) {
                                if (s.getDataNode().has("key_color")
                                        && !s.getDataNode().path("key_color").asText().isEmpty()
                                        && Palette.getColor(
                                                        MiscUtil.orEmpty(s.getDisplayName())
                                                                .toLowerCase(Locale.ENGLISH))
                                                == Palette.getDefaultColor()) {
                                    Palette.setColor(
                                            MiscUtil.orEmpty(s.getDisplayName()).toLowerCase(Locale.ENGLISH),
                                            GetClosestColor.getClosestColor(
                                                    s.getDataNode().path("key_color").asText(),
                                                    Login.this));
                                }
                            }
                            doSubStrings(subs);
                        })
                .setNegativeButton(R.string.btn_no, (dialog, which) -> doSubStrings(subs))
                .setOnDismissListener(dialog -> doSubStrings(subs))
                );
    }

    private final class UserChallengeTask extends AsyncTask<String, Void, OAuthData> {
        private final OAuthHelper mOAuthHelper;
        private final Credentials mCredentials;
        @SuppressWarnings("NullAway.Init") // assigned in onPreExecute
        private MaterialProgressDialog mMaterialDialog;
        /** Classified reason the exchange failed, used to pick the dialog message; null on success. */
        @Nullable private OAuthLoginHelper.FailureType failureType;

        public UserChallengeTask(OAuthHelper oAuthHelper, Credentials credentials) {
            logV("UserChallengeTask created");
            mOAuthHelper = oAuthHelper;
            mCredentials = credentials;
        }

        @Override
        protected void onPreExecute() {
            logV("UserChallengeTask starting OAuth exchange");
            // Show a dialog to indicate progress
            MaterialProgressDialog.Builder builder =
                    new MaterialProgressDialog.Builder(Login.this)
                            .title(R.string.login_authenticating)
                            .progress(true, 0)
                            .content(R.string.misc_please_wait)
                            .cancelable(false);
            mMaterialDialog = builder.build();
            mMaterialDialog.show();
        }

        @Override
        protected @Nullable OAuthData doInBackground(String... params) {
            logV("doInBackground: processing challenge URL: " + redactCode(params[0]));
            try {
                logV("Calling onUserChallenge...");
                OAuthData oAuthData = mOAuthHelper.onUserChallenge(params[0], mCredentials);
                if (oAuthData != null) {
                    logV("OAuthData received successfully");
                    logV("Authenticating with Reddit...");
                    if (Authentication.reddit == null) {
                        return null;
                    }

                    Authentication.reddit.authenticate(oAuthData);
                    Authentication.isLoggedIn = true;
                    String refreshToken = Authentication.reddit.getOAuthData().getRefreshToken();
                    logV("Refresh token obtained: " + (refreshToken != null ? "yes" : "null"));
                    SharedPreferences.Editor editor = Authentication.authentication.edit();
                    logV("Fetching logged-in account info...");
                    LoggedInAccount me = Authentication.reddit.me();
                    logV("Logged in as: " + me.getFullName());
                    Authentication.name = me.getFullName();
                    // Signing in again to an account already stored replaces its entry, rather
                    // than adding a second one alongside the token it still held.
                    Authentication.storeAccountToken(editor, me.getFullName(), refreshToken);
                    editor.putString("lasttoken", refreshToken);
                    editor.remove("backedCreds");
                    Reddit.appRestart.edit().remove("back").commit();
                    editor.commit();
                    logV("Login credentials saved successfully");
                } else {
                    logE("onUserChallenge returned null OAuthData");
                }
                return oAuthData;
            } catch (NetworkException e) {
                // JRAW wraps the token-endpoint HTTP response here; classify it (HTTP status + body)
                // so onPostExecute can show a precise reason instead of a generic decline message.
                RestResponse response = e.getResponse();
                failureType =
                        response != null
                                ? OAuthLoginHelper.classifyTokenResponse(
                                                response.getStatusCode(), response.getRaw())
                                        .failureType
                                : OAuthLoginHelper.FailureType.NETWORK;
                logE("OAuth network failure (" + failureType + "): " + e.getMessage());
                logE("Login.doInBackground failed", e);
            } catch (OAuthException e) {
                failureType = OAuthLoginHelper.FailureType.REDDIT_ERROR;
                logE("OAuth error: " + e.getMessage());
                logE("Login.doInBackground failed", e);
            } catch (IllegalStateException e) {
                // JRAW throws this on a state (CSRF) mismatch.
                failureType = OAuthLoginHelper.FailureType.UNKNOWN;
                logE("OAuth state mismatch: " + e.getMessage());
                logE("Login.doInBackground failed", e);
            } catch (RuntimeException e) {
                // Catch runtime exceptions, which include Protocol exceptions from OkHttp
                failureType = OAuthLoginHelper.classifyThrowable(e).failureType;
                if (e.getCause() instanceof java.net.ProtocolException &&
                    String.valueOf(e.getCause() == null ? null : e.getCause().getMessage()).contains("Too many follow-up requests")) {
                    logE("OAuth redirect loop detected: " + e.getCause().getMessage());
                } else {
                    logE("OAuth runtime error: " + e.getClass().getSimpleName() + ": " + e.getMessage());
                    if (e.getCause() != null) {
                        logE("Caused by: " + e.getCause().getClass().getSimpleName() + ": " + e.getCause().getMessage());
                    }
                    logE("Login.doInBackground failed", e);
                }
            } catch (Exception e) {
                failureType = OAuthLoginHelper.classifyThrowable(e).failureType;
                logE("Unexpected error during OAuth: " + e.getClass().getSimpleName() + ": " + e.getMessage());
                logE("Login.doInBackground failed", e);
            }
            return null;
        }

        @Override
        protected void onPostExecute(OAuthData oAuthData) {
            // Dismiss old progress dialog
            mMaterialDialog.dismiss();

            if (oAuthData != null) {
                logV("Login successful, starting subscription sync");
                Reddit.appRestart.edit().putBoolean("firststarting", true).apply();

                UserSubscriptions.switchAccounts();
                d =
                        new MaterialProgressDialog.Builder(Login.this)
                                .cancelable(false)
                                .title(R.string.login_starting)
                                .progress(true, 0)
                                .content(R.string.login_starting_desc)
                                .build()
                                .getDialog();
                d.show();

                UserSubscriptions.syncSubredditsGetObjectAsync(Login.this);
            } else {
                logE(
                        "Login failed: OAuthData was null in onPostExecute (failureType="
                                + failureType
                                + ")");
                // Pick a message from the classified failure: a network problem, a credential/config
                // problem (bad Client ID / Redirect URI / Reddit error), or — when nothing was thrown
                // — the user likely declined on the consent screen.
                int messageRes;
                if (failureType == OAuthLoginHelper.FailureType.NETWORK) {
                    messageRes = R.string.err_connection_failed_msg;
                } else if (failureType != null) {
                    messageRes = R.string.login_failed_oauth_error_page;
                } else {
                    messageRes = R.string.login_failed_err_decline;
                }
                DialogUtil.showWithCardBackground(new AlertDialog.Builder(Login.this)
                        .setTitle(R.string.err_authentication)
                        .setMessage(messageRes)
                        .setNeutralButton(
                                android.R.string.ok,
                                (dialog, which) -> {
                                    Reddit.forceRestart(Login.this, true);
                                    finish();
                                }));
            }
        }
    }
}
