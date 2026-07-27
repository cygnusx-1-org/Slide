package me.edgan.redditslide.Tumblr;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import androidx.appcompat.app.AppCompatActivity;
import androidx.test.core.app.ApplicationProvider;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import me.edgan.redditslide.Constants;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/**
 * Covers the contract every Tumblr album screen binds against: what {@code doWithData} reports for a
 * response that carried no photos, and the two steps that decide what it is handed — {@code
 * hasPhotos}, which screens a fetched response, and {@code parseJson}, which digs the photo list out
 * of one.
 *
 * <p>This used to be a void method that called {@link
 * TumblrUtils.GetTumblrPostWithCallback#onError()} and then returned normally, which left every
 * override free to call {@code super} and index the empty list anyway — the peek overlay did exactly
 * that and threw IndexOutOfBoundsException. The boolean return is now the guard, so it is worth
 * pinning down.
 *
 * <p>Robolectric only because the constructor parses the post url; the method under test touches no
 * Android at all.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33, application = Application.class)
public class TumblrCallbackContractTest {

    private static class RecordingCallback extends TumblrUtils.GetTumblrPostWithCallback {
        int errors;
        int delivered;

        /**
         * How many responses were handed to the parser. Counted because the two ways a cached entry
         * can end at onError() are otherwise indistinguishable: parsed and found wanting, or
         * screened out and re-fetched. Only the second is what the cache screen is for.
         */
        int parsed;

        RecordingCallback(final Activity host) {
            super("https://example.tumblr.com/post/1", host);
        }

        @Override
        public void onError() {
            errors++;
        }

        @Override
        public void parseJson(final JsonElement baseData) {
            parsed++;
            super.parseJson(baseData);
        }

        @Override
        public boolean doWithData(final List<Photo> data) {
            if (!super.doWithData(data)) {
                return false;
            }
            delivered = data.size();
            return true;
        }
    }

    /** A real host, since the constructor's {@code baseActivity} is declared {@code @NonNull}. */
    private RecordingCallback newCallback() {
        return new RecordingCallback(Robolectric.buildActivity(TestActivity.class).setup().get());
    }

    @Test
    public void aNullPhotoListIsReportedAsNothingToBind() {
        final RecordingCallback callback = newCallback();

        assertFalse(callback.doWithData(null));
        assertEquals(1, callback.errors);
    }

    @Test
    public void anEmptyPhotoListIsReportedAsNothingToBind() {
        final RecordingCallback callback = newCallback();

        assertFalse(callback.doWithData(new ArrayList<>()));
        assertEquals(1, callback.errors);
    }

    @Test
    public void aPopulatedPhotoListIsBindable() {
        final RecordingCallback callback = newCallback();
        final List<Photo> photos = Collections.singletonList(new Photo());

        assertTrue(callback.doWithData(photos));
        assertEquals(0, callback.errors);
    }

    /** {@code {"response":{"posts":[…]}}} as gson hands it to the code under test. */
    private static JsonObject json(final String raw) {
        return JsonParser.parseString(raw).getAsJsonObject();
    }

    @Test
    public void aPostArrayHoldingANullEntryIsReportedRatherThanThrowing() {
        // Jackson keeps a null in the list for a null element in the JSON array, so the walk down to
        // the photos has an element to null-check and not only a size to test. It runs on the
        // loader's worker thread, where an NPE is not caught by anything and takes the process down.
        final RecordingCallback callback = newCallback();

        callback.parseJson(json("{\"response\":{\"posts\":[null]}}"));

        assertEquals(1, callback.errors);
    }

    @Test
    public void aResponseThatIsAbsentAltogetherIsReported() {
        final RecordingCallback callback = newCallback();

        callback.parseJson(json("{}"));

        assertEquals(1, callback.errors);
    }

    @Test
    public void anEmptyPostArrayIsReported() {
        final RecordingCallback callback = newCallback();

        callback.parseJson(json("{\"response\":{\"posts\":[]}}"));

        assertEquals(1, callback.errors);
    }

    @Test
    public void aPostCarryingPhotosIsHandedOn() {
        final RecordingCallback callback = newCallback();

        callback.parseJson(
                json("{\"response\":{\"posts\":[{\"photos\":[{\"original_size\":{}}]}]}}"));

        assertEquals(0, callback.errors);
    }

    @Test
    public void onlyAResponseWithAPhotoBearingPostIsWorthParsing() {
        assertTrue(
                TumblrUtils.GetTumblrPostWithCallback.hasPhotos(
                        json("{\"response\":{\"posts\":[{\"photos\":[]}]}}")));
    }

    @Test
    public void aMalformedResponseIsScreenedOutRatherThanThrowing() {
        // Every one of these used to throw where it was tested — ClassCastException for a JSON-null
        // or non-array where an object or array was assumed, IndexOutOfBoundsException for the empty
        // array — on the loader's worker thread, so none of them reached onError().
        assertFalse(TumblrUtils.GetTumblrPostWithCallback.hasPhotos(null));
        assertFalse(TumblrUtils.GetTumblrPostWithCallback.hasPhotos(json("{}")));
        assertFalse(TumblrUtils.GetTumblrPostWithCallback.hasPhotos(json("{\"response\":null}")));
        assertFalse(TumblrUtils.GetTumblrPostWithCallback.hasPhotos(json("{\"response\":[]}")));
        assertFalse(
                TumblrUtils.GetTumblrPostWithCallback.hasPhotos(json("{\"response\":{}}")));
        assertFalse(
                TumblrUtils.GetTumblrPostWithCallback.hasPhotos(
                        json("{\"response\":{\"posts\":{}}}")));
        assertFalse(
                TumblrUtils.GetTumblrPostWithCallback.hasPhotos(
                        json("{\"response\":{\"posts\":[]}}")));
        assertFalse(
                TumblrUtils.GetTumblrPostWithCallback.hasPhotos(
                        json("{\"response\":{\"posts\":[null]}}")));
        assertFalse(
                TumblrUtils.GetTumblrPostWithCallback.hasPhotos(
                        json("{\"response\":{\"posts\":[{}]}}")));
    }

    @Test
    public void aCachedResponseThatIsNotAnObjectIsNotParsedAsOne() {
        // parseString("") is JsonNull and parseString("[]") is an array; getAsJsonObject() on either
        // throws IllegalStateException. That used to run inline in doInBackground, on the worker
        // thread, with nothing to catch it.
        assertNull(TumblrUtils.GetTumblrPostWithCallback.asObject(null));
        assertNull(TumblrUtils.GetTumblrPostWithCallback.asObject(""));
        assertNull(TumblrUtils.GetTumblrPostWithCallback.asObject("null"));
        assertNull(TumblrUtils.GetTumblrPostWithCallback.asObject("[]"));
        assertNull(TumblrUtils.GetTumblrPostWithCallback.asObject("7"));
        // Not merely a wrong type — malformed, which parseString throws on rather than returning.
        assertNull(TumblrUtils.GetTumblrPostWithCallback.asObject("{oops"));
    }

    @Test
    public void aCachedObjectIsParsedBackIntoOne() {
        assertNotNull(
                TumblrUtils.GetTumblrPostWithCallback.asObject("{\"response\":{\"posts\":[]}}"));
    }

    @Test
    public void aUsableCachedResponseIsServedWithoutAFetch() {
        // The whole cached branch of doInBackground, end to end. Reddit.client is null here, so if
        // this fell through to the network branch HttpUtil would hand back null and this would
        // report an error instead of the photos.
        final RecordingCallback callback = newCallback();
        cache(callback, "{\"response\":{\"posts\":[{\"photos\":[{\"original_size\":{}}]}]}}");

        callback.doInBackground();

        assertEquals(0, callback.errors);
        assertEquals(1, callback.delivered);
        assertEquals("the cached body should have gone straight to the parser", 1, callback.parsed);
    }

    @Test
    public void aCorruptCacheEntryIsReportedRatherThanThrowing() {
        // The same branch with a value that is not a JSON object at all. It has to fall through to
        // the fetch, which returns nothing here, and end at onError() — not at an exception thrown
        // off the worker thread.
        final RecordingCallback callback = newCallback();
        cache(callback, "not json at all");

        callback.doInBackground();

        assertEquals(1, callback.errors);
        assertEquals(0, callback.delivered);
        assertEquals("nothing parseable to hand the parser", 0, callback.parsed);
    }

    @Test
    public void aCachedResponseWithNoPhotosIsNotServedFromTheCache() {
        // Only a photo-bearing response is ever written to the cache, so anything else in there did
        // not come from us; screening it the same way the fetch is screened sends it back for a
        // fresh one rather than into parseJson.
        final RecordingCallback callback = newCallback();
        cache(callback, "{\"response\":{\"posts\":[]}}");

        callback.doInBackground();

        assertEquals(1, callback.errors);
        assertEquals(0, callback.delivered);
        // The point of screening the cache: this entry is discarded and a fresh fetch attempted,
        // rather than parsed for good. Both routes end at onError() here, so counting parses is the
        // only thing that tells them apart.
        assertEquals("a photo-less cache entry must not be parsed", 0, callback.parsed);
    }

    /** Puts {@code body} in the request cache under the exact key doInBackground will look up. */
    private static void cache(final RecordingCallback callback, final String body) {
        TumblrUtils.tumblrRequests =
                ApplicationProvider.getApplicationContext()
                        .getSharedPreferences("tumblrRequests", Context.MODE_PRIVATE);
        TumblrUtils.tumblrRequests.edit().clear().apply();
        TumblrUtils.tumblrRequests
                .edit()
                .putString(
                        "https://api.tumblr.com/v2/blog/"
                                + callback.blog
                                + "/posts?api_key="
                                + Constants.TUMBLR_API_KEY
                                + "&id="
                                + callback.id,
                        body)
                .apply();
    }

    public static class TestActivity extends AppCompatActivity {}
}
