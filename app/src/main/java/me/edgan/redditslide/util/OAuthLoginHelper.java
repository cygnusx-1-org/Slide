package me.edgan.redditslide.util;

import android.util.Log;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import java.io.IOException;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Context-free classification helpers for the Reddit OAuth login flow. Pulling these pure decisions
 * out of {@link me.edgan.redditslide.Activities.Login} (which is tangled up with the WebView,
 * theming, and app singletons) lets them be unit-tested directly — including the
 * production-parity org.json edge cases that only show up on a real Android parser.
 */
public final class OAuthLoginHelper {

    public static final String TAG = "OAuthLogin";

    static final String ACCESS_TOKEN_KEY = "access_token";
    static final String REFRESH_TOKEN_KEY = "refresh_token";
    static final String ERROR_KEY = "error";

    private OAuthLoginHelper() {}

    /** Why a token exchange did not yield a usable pair of tokens. */
    public enum FailureType {
        /** HTTP 401/403 — bad credentials (wrong Client ID / Redirect URI). */
        UNAUTHORIZED,
        /** HTTP 429 — too many requests. */
        RATE_LIMITED,
        /** HTTP 5xx — Reddit-side error. */
        SERVER_ERROR,
        /** Any other non-2xx status. */
        HTTP_OTHER,
        /** 2xx but the body was null/empty. */
        EMPTY_RESPONSE,
        /** A Reddit {@code {"error":...}} body. */
        REDDIT_ERROR,
        /** Parsed fine but one or both tokens were absent/blank/JSON-null. */
        MISSING_TOKENS,
        /** Body could not be parsed as JSON. */
        MALFORMED_JSON,
        /** An {@link IOException} (network) failure on the call itself. */
        NETWORK,
        /** Anything else. */
        UNKNOWN
    }

    /** Outcome of {@link #classifyTokenResponse}: either both tokens, or a {@link FailureType}. */
    public static final class TokenResult {
        public final String accessToken;
        public final String refreshToken;
        public final FailureType failureType;
        /** The HTTP status for an HTTP-class failure, otherwise -1. */
        public final int httpCode;
        /** The raw Reddit error value for {@link FailureType#REDDIT_ERROR}, otherwise null. */
        public final String redditError;

        private TokenResult(String accessToken, String refreshToken, FailureType failureType,
                            int httpCode, String redditError) {
            this.accessToken = accessToken;
            this.refreshToken = refreshToken;
            this.failureType = failureType;
            this.httpCode = httpCode;
            this.redditError = redditError;
        }

        static TokenResult success(String accessToken, String refreshToken) {
            return new TokenResult(accessToken, refreshToken, null, -1, null);
        }

        static TokenResult failure(FailureType type) {
            return new TokenResult(null, null, type, -1, null);
        }

        static TokenResult httpFailure(FailureType type, int httpCode) {
            return new TokenResult(null, null, type, httpCode, null);
        }

        static TokenResult redditError(String redditError) {
            return new TokenResult(null, null, FailureType.REDDIT_ERROR, -1, redditError);
        }

        public boolean isSuccess() {
            return failureType == null;
        }
    }

    /**
     * Classifies a token-endpoint response from its HTTP status and body string into either both
     * tokens or a {@link FailureType}. Covers non-2xx responses (with HTTP code), empty bodies,
     * Reddit {@code {"error":...}} bodies, missing tokens, and malformed JSON. Context-free so it can
     * be unit-tested directly.
     */
    @NonNull
    public static TokenResult classifyTokenResponse(int httpStatus, @Nullable String body) {
        if (httpStatus < 200 || httpStatus >= 300) {
            Log.e(TAG, "Token exchange failed: HTTP " + httpStatus + " — " + body);
            if (httpStatus == 401 || httpStatus == 403) {
                return TokenResult.httpFailure(FailureType.UNAUTHORIZED, httpStatus);
            } else if (httpStatus == 429) {
                return TokenResult.httpFailure(FailureType.RATE_LIMITED, httpStatus);
            } else if (httpStatus >= 500) {
                return TokenResult.httpFailure(FailureType.SERVER_ERROR, httpStatus);
            } else {
                return TokenResult.httpFailure(FailureType.HTTP_OTHER, httpStatus);
            }
        }

        if (body == null || body.isEmpty()) {
            Log.e(TAG, "Token exchange returned an empty body");
            return TokenResult.failure(FailureType.EMPTY_RESPONSE);
        }

        try {
            JSONObject responseJSON = new JSONObject(body);

            // !isNull guards against an explicit JSON null error (e.g. {"error":null}), which is not
            // a real failure and should fall through to the token check below.
            if (responseJSON.has(ERROR_KEY) && !responseJSON.isNull(ERROR_KEY)) {
                String redditError = responseJSON.optString(ERROR_KEY);
                Log.e(TAG, "Token exchange returned an error field: " + body);
                return TokenResult.redditError(redditError);
            }

            String accessToken = responseJSON.optString(ACCESS_TOKEN_KEY);
            String refreshToken = responseJSON.optString(REFRESH_TOKEN_KEY);
            // isNull is essential: Android's org.json coerces a JSON null to the string "null"
            // (non-empty), so optString alone would accept {"access_token":null} as a valid token.
            if (responseJSON.isNull(ACCESS_TOKEN_KEY) || responseJSON.isNull(REFRESH_TOKEN_KEY)
                    || accessToken.isEmpty() || refreshToken.isEmpty()) {
                Log.e(TAG, "Token exchange response was missing tokens: " + body);
                return TokenResult.failure(FailureType.MISSING_TOKENS);
            }

            return TokenResult.success(accessToken, refreshToken);
        } catch (JSONException e) {
            Log.e(TAG, "Failed to parse token exchange response: " + body, e);
            return TokenResult.failure(FailureType.MALFORMED_JSON);
        }
    }

    /**
     * Classifies a failure thrown by the token-exchange call itself. Network failures
     * ({@link IOException}) map to {@link FailureType#NETWORK}; anything else to
     * {@link FailureType#UNKNOWN}.
     */
    @NonNull
    public static TokenResult classifyThrowable(@Nullable Throwable t) {
        Log.e(TAG, "Token exchange call failed", t);
        if (t instanceof IOException) {
            return TokenResult.failure(FailureType.NETWORK);
        }
        return TokenResult.failure(FailureType.UNKNOWN);
    }

    /** What the OAuth redirect URI tells us to do. */
    public enum RedirectAction {
        /** A valid {@code code} with a matching {@code state} — proceed to the token exchange. */
        EXCHANGE_CODE,
        /** A {@code code} arrived but {@code state} was missing or did not match — possible CSRF. */
        STATE_MISMATCH,
        /** {@code error=access_denied} — the user declined, or Reddit auto-denied. */
        ACCESS_DENIED,
        /** Some other {@code error=...} value. */
        OAUTH_ERROR,
        /** No {@code code} and no {@code error} — not a redirect we act on (keep loading). */
        NONE
    }

    /** Result of {@link #classifyRedirect}. */
    public static final class RedirectResult {
        public final RedirectAction action;
        /** The auth code for {@link RedirectAction#EXCHANGE_CODE}, otherwise null. */
        public final String authCode;
        /** The raw error value for {@link RedirectAction#OAUTH_ERROR}, otherwise null. */
        public final String errorValue;

        private RedirectResult(RedirectAction action, String authCode, String errorValue) {
            this.action = action;
            this.authCode = authCode;
            this.errorValue = errorValue;
        }
    }

    /**
     * Decides what to do with an OAuth redirect from its {@code code}, {@code state}, and
     * {@code error} query parameters, comparing {@code state} against {@code expectedState}
     * null-safely (a missing or mismatched state is a {@link RedirectAction#STATE_MISMATCH}, not a
     * crash). A present {@code code} takes precedence over any {@code error}. Context-/Uri-free so it
     * can be unit-tested directly.
     */
    @NonNull
    public static RedirectResult classifyRedirect(@Nullable String code, @Nullable String state,
                                                  @Nullable String error,
                                                  @Nullable String expectedState) {
        if (code != null && !code.isEmpty()) {
            if (expectedState != null && expectedState.equals(state)) {
                return new RedirectResult(RedirectAction.EXCHANGE_CODE, code, null);
            }
            return new RedirectResult(RedirectAction.STATE_MISMATCH, null, null);
        }
        if ("access_denied".equals(error)) {
            return new RedirectResult(RedirectAction.ACCESS_DENIED, null, null);
        }
        if (error != null && !error.isEmpty()) {
            return new RedirectResult(RedirectAction.OAUTH_ERROR, null, error);
        }
        return new RedirectResult(RedirectAction.NONE, null, null);
    }

    /**
     * Heuristic: does this WebView page text look like a bare JSON body (e.g. {@code {}} or {@code
     * {"error":...}}) rather than an HTML page? Reddit renders such a body in the WebView when the
     * OAuth flow dead-ends instead of redirecting to our scheme — typically a misconfigured API
     * Client ID or Redirect URI. A normal consent/login page's text never starts with '{'.
     */
    public static boolean looksLikeJsonErrorPage(@Nullable String pageText) {
        if (pageText == null) {
            return false;
        }
        String t = pageText.trim();
        return t.length() >= 2
                && t.length() < 2000
                && t.charAt(0) == '{'
                && t.charAt(t.length() - 1) == '}';
    }
}
