package me.edgan.redditslide.test;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

import com.google.common.net.MediaType;
import net.dean.jraw.http.HttpRequest;
import net.dean.jraw.http.RequestBody;
import org.junit.Test;

/**
 * Guards against <em>runtime</em> linkage failures between dependencies — the kind that compile
 * cleanly but throw {@link NoClassDefFoundError}/{@link NoSuchMethodError} when the app runs.
 *
 * <p>The motivating case: the JRAW fork ({@code com.github.edgan:JRAW}) is built against an older
 * OkHttp and references OkHttp internals. Upgrading OkHttp (4 → 5) removed
 * {@code okhttp3.internal.Util} (and its {@code EMPTY_BYTE_ARRAY}, used by
 * {@code HttpRequest.Builder.method()} to give body-permitting verbs an empty body), which the
 * compiler can't catch because JRAW arrives pre-compiled. The app only crashed at runtime on the
 * first POST/PUT with a null body (e.g. fetching flair choices).
 *
 * <p>These tests exercise the real cross-library code paths on the unit-test classpath (which uses
 * the same OkHttp the app ships), so a future dependency mismatch fails here instead of in users'
 * hands. They need no network — only request <em>construction</em>, where the linkage lives.
 *
 * <p>This file is also the template for guarding other risky dependency seams: exercise the actual
 * cross-library entry points rather than asserting on versions.
 */
public class LibraryLinkageTest {

    private static final String LINKAGE_HINT =
            " — a dependency references a symbol missing from another dependency on the classpath."
                    + " If this is JRAW vs OkHttp, rebuild the JRAW fork against the OkHttp in"
                    + " app/build.gradle (okhttp-bom) and bump the com.github.edgan:JRAW ref.";

    /**
     * Building a request for every HTTP verb must not hit a missing OkHttp symbol. The important
     * cases are the body-permitting verbs with a null body (POST/PUT): that branch of
     * {@code HttpRequest.Builder.method()} historically used {@code okhttp3.internal.Util}.
     */
    @Test
    public void jrawBuildsRequestsForAllVerbsWithoutLinkageErrors() {
        try {
            // Builder() also constructs an okhttp3.Headers.Builder; each verb routes through
            // method(), which touches okhttp3.internal.http.HttpMethod.permitsRequestBody.
            new HttpRequest.Builder().get();
            new HttpRequest.Builder().delete();
            new HttpRequest.Builder().head();
            new HttpRequest.Builder().post(); // null body + body-permitting verb (the Util path)
            new HttpRequest.Builder().put(); // ditto

            HttpRequest request =
                    new HttpRequest.Builder()
                            .https(true)
                            .host("oauth.reddit.com")
                            .path("/api/v1/me")
                            .post()
                            .build();
            assertNotNull(request);
        } catch (NoClassDefFoundError | NoSuchMethodError e) {
            fail("JRAW HttpRequest.Builder failed to link against OkHttp" + LINKAGE_HINT + " " + e);
        }
    }

    /** The request-with-body path (JRAW's Guava-MediaType RequestBody) must also link cleanly. */
    @Test
    public void jrawBuildsPostWithBodyWithoutLinkageErrors() {
        try {
            RequestBody body = RequestBody.create((MediaType) null, "{}");
            HttpRequest request =
                    new HttpRequest.Builder()
                            .https(true)
                            .host("oauth.reddit.com")
                            .path("/api/save")
                            .post(body)
                            .build();
            assertNotNull(request);
        } catch (NoClassDefFoundError | NoSuchMethodError e) {
            fail("JRAW request-with-body failed to link" + LINKAGE_HINT + " " + e);
        }
    }

    /**
     * Explicit guard for the one OkHttp <em>internal</em> symbol JRAW still relies on, so a future
     * OkHttp upgrade that removes or renames it fails with a message naming the exact symbol rather
     * than a generic crash deep in a request.
     */
    @Test
    public void okHttpInternalApiJrawDependsOnIsPresent() {
        try {
            Class<?> httpMethod = Class.forName("okhttp3.internal.http.HttpMethod");
            // Used by HttpRequest.Builder.method(); reflection is lenient about static vs instance.
            assertNotNull(httpMethod.getMethod("permitsRequestBody", String.class));
        } catch (ClassNotFoundException | NoSuchMethodException e) {
            fail(
                    "okhttp3.internal.http.HttpMethod.permitsRequestBody(String) is gone; JRAW's"
                            + " HttpRequest.Builder.method() depends on it" + LINKAGE_HINT + " " + e);
        }
    }
}
