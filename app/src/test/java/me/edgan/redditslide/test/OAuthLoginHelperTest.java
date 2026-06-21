package me.edgan.redditslide.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import me.edgan.redditslide.util.OAuthLoginHelper;
import me.edgan.redditslide.util.OAuthLoginHelper.FailureType;
import me.edgan.redditslide.util.OAuthLoginHelper.RedirectAction;
import me.edgan.redditslide.util.OAuthLoginHelper.RedirectResult;
import me.edgan.redditslide.util.OAuthLoginHelper.TokenResult;

import org.junit.Test;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;

/**
 * Plain-JVM tests for the parts of {@link OAuthLoginHelper} that don't parse a JSON body: HTTP-status
 * classification, the redirect decision, the error-page heuristic, and throwable classification.
 *
 * <p>JSON-body classification lives in {@link OAuthLoginHelperJsonTest} and must run under
 * Robolectric for org.json production parity — see the note there.
 */
public class OAuthLoginHelperTest {

    private static final String EXPECTED_STATE = "expected_state_123";

    // ---------- HTTP status classification (short-circuits before any JSON parsing) ----------

    @Test
    public void http401_isUnauthorized() {
        TokenResult r = OAuthLoginHelper.classifyTokenResponse(401, "ignored");
        assertEquals(FailureType.UNAUTHORIZED, r.failureType);
        assertEquals(401, r.httpCode);
        assertFalse(r.isSuccess());
    }

    @Test
    public void http403_isUnauthorized() {
        assertEquals(
                FailureType.UNAUTHORIZED,
                OAuthLoginHelper.classifyTokenResponse(403, "ignored").failureType);
    }

    @Test
    public void http429_isRateLimited() {
        assertEquals(
                FailureType.RATE_LIMITED,
                OAuthLoginHelper.classifyTokenResponse(429, "ignored").failureType);
    }

    @Test
    public void http500_isServerError() {
        assertEquals(
                FailureType.SERVER_ERROR,
                OAuthLoginHelper.classifyTokenResponse(500, "ignored").failureType);
    }

    @Test
    public void http503_isServerError() {
        assertEquals(
                FailureType.SERVER_ERROR,
                OAuthLoginHelper.classifyTokenResponse(503, "ignored").failureType);
    }

    @Test
    public void http400_isGenericHttp() {
        assertEquals(
                FailureType.HTTP_OTHER,
                OAuthLoginHelper.classifyTokenResponse(400, "ignored").failureType);
    }

    @Test
    public void http404_isGenericHttp() {
        TokenResult r = OAuthLoginHelper.classifyTokenResponse(404, "ignored");
        assertEquals(FailureType.HTTP_OTHER, r.failureType);
        assertEquals(404, r.httpCode);
    }

    @Test
    public void status200_emptyBody_isEmptyResponse() {
        assertEquals(
                FailureType.EMPTY_RESPONSE,
                OAuthLoginHelper.classifyTokenResponse(200, "").failureType);
    }

    @Test
    public void status200_nullBody_isEmptyResponse() {
        assertEquals(
                FailureType.EMPTY_RESPONSE,
                OAuthLoginHelper.classifyTokenResponse(200, null).failureType);
    }

    // ---------- Redirect decision ----------

    @Test
    public void codeWithValidState_exchanges() {
        RedirectResult r =
                OAuthLoginHelper.classifyRedirect("abc123", EXPECTED_STATE, null, EXPECTED_STATE);
        assertEquals(RedirectAction.EXCHANGE_CODE, r.action);
        assertEquals("abc123", r.authCode);
    }

    @Test
    public void codeWithWrongState_isMismatch() {
        RedirectResult r =
                OAuthLoginHelper.classifyRedirect("abc123", "other", null, EXPECTED_STATE);
        assertEquals(RedirectAction.STATE_MISMATCH, r.action);
        assertNull(r.authCode);
    }

    @Test
    public void codeWithNullState_isMismatch() {
        assertEquals(
                RedirectAction.STATE_MISMATCH,
                OAuthLoginHelper.classifyRedirect("abc123", null, null, EXPECTED_STATE).action);
    }

    @Test
    public void emptyCodeNoError_isNone() {
        assertEquals(
                RedirectAction.NONE,
                OAuthLoginHelper.classifyRedirect("", EXPECTED_STATE, null, EXPECTED_STATE).action);
    }

    @Test
    public void emptyCodeWithAccessDenied_isAccessDenied() {
        assertEquals(
                RedirectAction.ACCESS_DENIED,
                OAuthLoginHelper.classifyRedirect("", null, "access_denied", EXPECTED_STATE).action);
    }

    @Test
    public void accessDeniedError_isAccessDenied() {
        assertEquals(
                RedirectAction.ACCESS_DENIED,
                OAuthLoginHelper.classifyRedirect(null, null, "access_denied", EXPECTED_STATE)
                        .action);
    }

    @Test
    public void otherError_isOauthError() {
        RedirectResult r =
                OAuthLoginHelper.classifyRedirect(null, null, "invalid_request", EXPECTED_STATE);
        assertEquals(RedirectAction.OAUTH_ERROR, r.action);
        assertEquals("invalid_request", r.errorValue);
    }

    @Test
    public void noCodeNoError_isNone() {
        assertEquals(
                RedirectAction.NONE,
                OAuthLoginHelper.classifyRedirect(null, null, null, EXPECTED_STATE).action);
    }

    @Test
    public void codeAndError_codeWins() {
        // A valid code present alongside an error param should still exchange (code takes precedence).
        RedirectResult r =
                OAuthLoginHelper.classifyRedirect(
                        "abc123", EXPECTED_STATE, "access_denied", EXPECTED_STATE);
        assertEquals(RedirectAction.EXCHANGE_CODE, r.action);
        assertEquals("abc123", r.authCode);
    }

    // ---------- Error-page heuristic ----------

    @Test
    public void emptyJsonObject_looksLikeError() {
        assertTrue(OAuthLoginHelper.looksLikeJsonErrorPage("{}"));
    }

    @Test
    public void jsonErrorBody_looksLikeError() {
        assertTrue(OAuthLoginHelper.looksLikeJsonErrorPage("{\"error\":\"invalid_grant\"}"));
    }

    @Test
    public void surroundingWhitespace_stillLooksLikeError() {
        assertTrue(OAuthLoginHelper.looksLikeJsonErrorPage("  {}  \n"));
    }

    @Test
    public void htmlPage_doesNotLookLikeError() {
        assertFalse(OAuthLoginHelper.looksLikeJsonErrorPage("<html><body>...</body></html>"));
    }

    @Test
    public void consentText_doesNotLookLikeError() {
        assertFalse(
                OAuthLoginHelper.looksLikeJsonErrorPage("Slide is requesting permission to..."));
    }

    @Test
    public void emptyString_doesNotLookLikeError() {
        assertFalse(OAuthLoginHelper.looksLikeJsonErrorPage(""));
    }

    @Test
    public void nullText_doesNotLookLikeError() {
        assertFalse(OAuthLoginHelper.looksLikeJsonErrorPage(null));
    }

    @Test
    public void unbalancedBrace_doesNotLookLikeError() {
        assertFalse(OAuthLoginHelper.looksLikeJsonErrorPage("{"));
        assertFalse(OAuthLoginHelper.looksLikeJsonErrorPage("{not closed"));
    }

    // ---------- Throwable classification ----------

    @Test
    public void ioException_isNetwork() {
        assertEquals(
                FailureType.NETWORK,
                OAuthLoginHelper.classifyThrowable(new IOException("boom")).failureType);
    }

    @Test
    public void socketTimeout_isNetwork() {
        assertEquals(
                FailureType.NETWORK,
                OAuthLoginHelper.classifyThrowable(new SocketTimeoutException()).failureType);
    }

    @Test
    public void unknownHost_isNetwork() {
        assertEquals(
                FailureType.NETWORK,
                OAuthLoginHelper.classifyThrowable(new UnknownHostException()).failureType);
    }

    @Test
    public void runtimeException_isUnknown() {
        assertEquals(
                FailureType.UNKNOWN,
                OAuthLoginHelper.classifyThrowable(new RuntimeException("x")).failureType);
    }

    @Test
    public void nullThrowable_isUnknown() {
        assertEquals(
                FailureType.UNKNOWN, OAuthLoginHelper.classifyThrowable(null).failureType);
    }
}
