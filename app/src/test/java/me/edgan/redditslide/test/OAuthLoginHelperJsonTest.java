package me.edgan.redditslide.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import me.edgan.redditslide.util.OAuthLoginHelper;
import me.edgan.redditslide.util.OAuthLoginHelper.FailureType;
import me.edgan.redditslide.util.OAuthLoginHelper.TokenResult;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/**
 * Token-body classification tests for {@link OAuthLoginHelper}, run under Robolectric so they parse
 * against Android's real {@code org.json}.
 *
 * <p>This is the critical-trap guard: Android's org.json (on-device and under Robolectric) coerces a
 * JSON null to the string {@code "null"}, whereas the unit-test stub {@code android.jar} (with
 * {@code returnDefaultValues = true}) returns {@code null}/defaults. So {@code
 * optString("access_token").isEmpty()} would wrongly accept {@code {"access_token":null}} as a valid
 * token on a real device — but a plain JVM test wouldn't catch it. Running here (real parser) plus the
 * {@code isNull(...)} guard in the helper keeps the code correct under both parsers.
 *
 * <p>Uses the stock {@link Application} (not Slide's heavy {@code Reddit} app class) — only the
 * Android org.json runtime is needed, not the app graph.
 */
@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class)
public class OAuthLoginHelperJsonTest {

    /** Proves we're on the real Android parser (stub returns ""/null instead of "null"). */
    @Test
    public void sanity_realAndroidJsonParserCoercesNullToString() throws Exception {
        assertEquals("null", new JSONObject("{\"x\":null}").optString("x"));
    }

    @Test
    public void emptyObject_isMissingTokens() {
        assertEquals(
                FailureType.MISSING_TOKENS,
                OAuthLoginHelper.classifyTokenResponse(200, "{}").failureType);
    }

    @Test
    public void tokenOnlyNoRefresh_isMissingTokens() {
        assertEquals(
                FailureType.MISSING_TOKENS,
                OAuthLoginHelper.classifyTokenResponse(200, "{\"access_token\":\"abc\"}")
                        .failureType);
    }

    @Test
    public void blankStringTokens_isMissingTokens() {
        assertEquals(
                FailureType.MISSING_TOKENS,
                OAuthLoginHelper.classifyTokenResponse(
                                200, "{\"access_token\":\"\",\"refresh_token\":\"\"}")
                        .failureType);
    }

    /**
     * The trap: {@code {"access_token":null,"refresh_token":null}}. Fails (wrongly returns success)
     * before the {@code isNull} guard; passes after it.
     */
    @Test
    public void nullTokenValues_isMissingTokens() {
        assertEquals(
                FailureType.MISSING_TOKENS,
                OAuthLoginHelper.classifyTokenResponse(
                                200, "{\"access_token\":null,\"refresh_token\":null}")
                        .failureType);
    }

    @Test
    public void fullValidResponse_isSuccess() {
        TokenResult r =
                OAuthLoginHelper.classifyTokenResponse(
                        200,
                        "{\"access_token\":\"AT\",\"refresh_token\":\"RT\","
                                + "\"token_type\":\"bearer\",\"expires_in\":3600,"
                                + "\"scope\":\"identity read\"}");
        assertTrue(r.isSuccess());
        assertEquals("AT", r.accessToken);
        assertEquals("RT", r.refreshToken);
    }

    @Test
    public void redditErrorString_isRedditError() {
        TokenResult r =
                OAuthLoginHelper.classifyTokenResponse(200, "{\"error\":\"invalid_grant\"}");
        assertEquals(FailureType.REDDIT_ERROR, r.failureType);
        assertEquals("invalid_grant", r.redditError);
    }

    @Test
    public void redditErrorNumeric_isRedditError() {
        TokenResult r = OAuthLoginHelper.classifyTokenResponse(200, "{\"error\":401}");
        assertEquals(FailureType.REDDIT_ERROR, r.failureType);
        assertEquals("401", r.redditError);
    }

    /** {@code {"error":null}} is not a real error — it falls through to the (here, missing) tokens. */
    @Test
    public void errorNull_isNotTreatedAsRedditError() {
        assertEquals(
                FailureType.MISSING_TOKENS,
                OAuthLoginHelper.classifyTokenResponse(200, "{\"error\":null}").failureType);
    }

    @Test
    public void nonJsonBody_isMalformed() {
        assertEquals(
                FailureType.MALFORMED_JSON,
                OAuthLoginHelper.classifyTokenResponse(200, "not json at all").failureType);
    }

    @Test
    public void whitespaceBody_isMalformed() {
        assertEquals(
                FailureType.MALFORMED_JSON,
                OAuthLoginHelper.classifyTokenResponse(200, "   ").failureType);
    }
}
