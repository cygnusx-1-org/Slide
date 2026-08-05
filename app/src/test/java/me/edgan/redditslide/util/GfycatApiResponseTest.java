package me.edgan.redditslide.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.net.Uri;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import me.edgan.redditslide.SettingValues;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.jspecify.annotations.NullMarked;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/**
 * What {@link GifUtils.AsyncLoadGif#getUrlFromApi} does with a response that is not the happy one.
 *
 * <p>Its caller reaches it straight after the gfycat lookup has already come back empty, so a second
 * lookup returning null — which is what {@code HttpUtil.getJsonObject} does for a non-2xx response, a
 * network failure or unparsable JSON — is the likely case rather than an exotic one. An unguarded
 * dereference there did not fail the load: it escaped {@code doInBackground} and {@code AsyncTask}
 * rethrew it as a RuntimeException on the executor thread, which nothing catches.
 */
@NullMarked
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33, application = Application.class)
public class GfycatApiResponseTest {

    /**
     * Captured and put back: {@code hqgif} is an app-wide static, and Robolectric caches one sandbox
     * per {@code @Config} — which every suite here shares — so a value left behind is visible to
     * whatever class runs next in the same JVM.
     */
    private boolean hqgifWas;

    /** Same reason as {@link #hqgifWas}; the client is a public static the lookups read. */
    private OkHttpClient clientWas;

    @Before
    public void setUp() {
        hqgifWas = SettingValues.hqgif;
        clientWas = GifUtils.AsyncLoadGif.client;
        SettingValues.hqgif = true; // Take the mp4Url branch unless a test says otherwise.
    }

    @After
    public void tearDown() {
        SettingValues.hqgif = hqgifWas;
        GifUtils.AsyncLoadGif.client = clientWas;
    }

    @Test
    public void aNullResponseHasNoUrl() {
        assertNull(GifUtils.AsyncLoadGif.getUrlFromApi(null));
    }

    @Test
    public void aResponseWithoutAGfyItemHasNoUrl() {
        assertNull(GifUtils.AsyncLoadGif.getUrlFromApi(json("{\"error\":\"not found\"}")));
    }

    @Test
    public void aGfyItemWithoutTheRequestedUrlHasNoUrl() {
        assertNull(GifUtils.AsyncLoadGif.getUrlFromApi(json("{\"gfyItem\":{}}")));
    }

    @Test
    public void aJsonNullGfyItemHasNoUrl() {
        // Gson's getAsJsonObject(name) is an unchecked cast, so a member that is present but
        // JSON-null yields JsonNull and the cast throws ClassCastException — not the Java null a
        // "did we get one?" check is looking for.
        assertNull(GifUtils.AsyncLoadGif.getUrlFromApi(json("{\"gfyItem\":null}")));
    }

    @Test
    public void aNullUrlInTheGfyItemHasNoUrl() {
        assertNull(GifUtils.AsyncLoadGif.getUrlFromApi(json("{\"gfyItem\":{\"mp4Url\":null}}")));
    }

    @Test
    public void aCompleteResponseYieldsTheMp4Url() {
        assertEquals(
                "https://giant.gfycat.com/x.mp4",
                GifUtils.AsyncLoadGif.getUrlFromApi(
                        json("{\"gfyItem\":{\"mp4Url\":\"https://giant.gfycat.com/x.mp4\"}}")));
    }

    @Test
    public void theLowQualitySettingPrefersMobileUrl() {
        SettingValues.hqgif = false;
        assertEquals(
                "https://thumbs.gfycat.com/x-mobile.mp4",
                GifUtils.AsyncLoadGif.getUrlFromApi(
                        json(
                                "{\"gfyItem\":{\"mp4Url\":\"https://giant.gfycat.com/x.mp4\","
                                        + "\"mobileUrl\":\"https://thumbs.gfycat.com/x-mobile.mp4\"}}")));
    }

    @Test
    public void aJsonNullMobileUrlFallsBackToTheMp4Url() {
        // has("mobileUrl") is true for a member that is present but JSON-null, so keying off the
        // key rather than the value failed a response that had a usable mp4Url beside it.
        SettingValues.hqgif = false;
        assertEquals(
                "https://giant.gfycat.com/x.mp4",
                GifUtils.AsyncLoadGif.getUrlFromApi(
                        json(
                                "{\"gfyItem\":{\"mobileUrl\":null,"
                                        + "\"mp4Url\":\"https://giant.gfycat.com/x.mp4\"}}")));
    }

    @Test
    public void onlyTheHandoffMarkerIsAHandoff() {
        // The caller used to spot the browser hand-off by looking for "gifdeliverynetwork" in the
        // returned uri, which no return of loadGfycat has ever contained. The marker is a scheme
        // that cannot collide with a media url.
        assertTrue(
                GifUtils.AsyncLoadGif.isHandoff(
                        Uri.parse(GifUtils.AsyncLoadGif.HANDOFF_SCHEME + "://gifdeliverynetwork")));
        assertFalse(GifUtils.AsyncLoadGif.isHandoff(null));
        assertFalse(GifUtils.AsyncLoadGif.isHandoff(Uri.parse("https://giant.gfycat.com/x.mp4")));
        assertFalse(
                GifUtils.AsyncLoadGif.isHandoff(
                        Uri.parse("https://gifdeliverynetwork.com/somegif")));
    }

    // ---------------------------------------------------------------- through loadGfycat

    @Test
    public void loadGfycatSurvivesAGfyItemWithNoMp4Url() {
        // loadGfycat used to repeat the traversal itself, ahead of and without getUrlFromApi's
        // checks, so these responses threw before that reader was ever consulted. Nothing catches
        // it on the way out: the try inside loadGfycat only catches IOException and
        // doInBackground's GFYCAT case has no catch at all, so it escaped as an AsyncTask-rethrown
        // RuntimeException instead of failing the load.
        serve("{\"gfyItem\":{}}");

        assertNull(GifUtils.AsyncLoadGif.loadGfycat("/somename", GFYCAT_URL, null, null, false));
    }

    @Test
    public void loadGfycatSurvivesAJsonNullGfyItem() {
        serve("{\"gfyItem\":null}");

        assertNull(GifUtils.AsyncLoadGif.loadGfycat("/somename", GFYCAT_URL, null, null, false));
    }

    @Test
    public void loadGfycatSurvivesAResponseWithNoGfyItem() {
        // An object, deliberately. A body that does not parse at all leaves getApiResponse returning
        // null, which sends loadGfycat into its redirect-following recovery — a real
        // HttpURLConnection to gfycat.com that the interceptor above cannot intercept.
        serve("{}");

        assertNull(GifUtils.AsyncLoadGif.loadGfycat("/somename", GFYCAT_URL, null, null, false));
    }

    @Test
    public void loadGfycatReturnsTheMp4UrlItWasGiven() {
        serve("{\"gfyItem\":{\"mp4Url\":\"https://giant.gfycat.com/x.mp4\"}}");

        assertEquals(
                Uri.parse("https://giant.gfycat.com/x.mp4"),
                GifUtils.AsyncLoadGif.loadGfycat("/somename", GFYCAT_URL, null, null, false));
    }

    private static final String GFYCAT_URL = "https://gfycat.com/somename";

    /**
     * Answers every request with {@code body}, so the lookups run for real with no network. An
     * interceptor rather than a MockWebServer because the api host is baked into getApiResponse.
     */
    private static void serve(final String body) {
        GifUtils.AsyncLoadGif.client =
                new OkHttpClient.Builder()
                        .addInterceptor(
                                chain ->
                                        new Response.Builder()
                                                .request(chain.request())
                                                .protocol(Protocol.HTTP_1_1)
                                                .code(200)
                                                .message("OK")
                                                .body(
                                                        ResponseBody.create(
                                                                body,
                                                                MediaType.get("application/json")))
                                                .build())
                        .build();
    }

    private static JsonObject json(final String raw) {
        return JsonParser.parseString(raw).getAsJsonObject();
    }
}
