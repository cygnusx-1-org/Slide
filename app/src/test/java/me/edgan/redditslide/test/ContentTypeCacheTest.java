package me.edgan.redditslide.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import android.app.Application;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.InputStream;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import me.edgan.redditslide.ContentType;
import me.edgan.redditslide.ContentType.Type;
import me.edgan.redditslide.SettingValues;
import net.dean.jraw.models.Submission;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/**
 * The resolved-content-type cache, and the two things that are allowed to get past it.
 *
 * <p>Resolving a type parses the url twice and scans the always-external domain set, so the answer
 * is cached per post. That makes two contracts, both written down in comments on
 * {@code ContentType} and enforced by nothing:
 *
 * <ul>
 *   <li>{@code invalidateTypeCache()} has to actually drop the entries, because the always-external
 *       list is what decides whether a url is EXTERNAL -- a user adding a domain there would
 *       otherwise keep seeing the old handling until the process restarted.
 *   <li>The entry is keyed by fullname but validated against the url, so a post whose link was
 *       rewritten afterwards (PostRecovery) is resolved again rather than served the old type.
 * </ul>
 *
 * <p>Neither was covered: emptying the body of {@code invalidateTypeCache} left the whole suite
 * green, even though {@link ContentTypeTest} calls it in its own teardown for exactly this reason.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33, application = Application.class)
public class ContentTypeCacheTest {

    private static final String LINK_URL = "https://example.com/story";

    private Set<String> alwaysExternalWas;

    @Before
    public void setUp() {
        alwaysExternalWas = SettingValues.alwaysExternal;
        SettingValues.alwaysExternal = new HashSet<>();
        ContentType.invalidateTypeCache();
    }

    @After
    public void tearDown() {
        SettingValues.alwaysExternal = alwaysExternalWas;
        // The cache is a process-wide static; an entry resolved under this test's domain set would
        // otherwise answer for a later test class.
        ContentType.invalidateTypeCache();
    }

    /** A link post with the given fullname and url, built from the shared scalar fixture. */
    private static Submission post(String fullName, String url) throws Exception {
        final ObjectNode data;
        try (InputStream input =
                ContentTypeCacheTest.class
                        .getClassLoader()
                        .getResourceAsStream("submissions/galleryPost.json")) {
            assertNotNull(input);
            data = (ObjectNode) new ObjectMapper().readTree(input);
        }
        data.put("name", fullName);
        data.put("is_gallery", false);
        data.put("is_self", false);
        data.put("url", url);
        data.put("domain", "example.com");
        return new Submission(data);
    }

    /** Without an invalidation the cached answer stands -- that is what makes it a cache. */
    @Test
    public void aResolvedTypeIsServedFromTheCache() throws Exception {
        Submission submission = post("t3_cache1", LINK_URL);
        assertEquals(Type.LINK, ContentType.getContentType(submission));

        SettingValues.alwaysExternal = new HashSet<>(Arrays.asList("example.com"));

        assertEquals(
                "no invalidation, so the first answer still stands",
                Type.LINK,
                ContentType.getContentType(submission));
    }

    /** The contract the comment states: after invalidation the new setting is visible. */
    @Test
    public void invalidatingTheCacheMakesAnAlwaysExternalChangeVisible() throws Exception {
        Submission submission = post("t3_cache2", LINK_URL);
        assertEquals(Type.LINK, ContentType.getContentType(submission));

        SettingValues.alwaysExternal = new HashSet<>(Arrays.asList("example.com"));
        ContentType.invalidateTypeCache();

        assertEquals(
                "adding the domain to the always-external list has to take effect",
                Type.EXTERNAL,
                ContentType.getContentType(submission));
    }

    /** Removing the domain again has to take effect the same way. */
    @Test
    public void invalidatingTheCacheMakesAnAlwaysExternalRemovalVisible() throws Exception {
        SettingValues.alwaysExternal = new HashSet<>(Arrays.asList("example.com"));
        Submission submission = post("t3_cache3", LINK_URL);
        assertEquals(Type.EXTERNAL, ContentType.getContentType(submission));

        SettingValues.alwaysExternal = new HashSet<>();
        ContentType.invalidateTypeCache();

        assertEquals(Type.LINK, ContentType.getContentType(submission));
    }

    /**
     * PostRecovery rewrites a removed post's link in place, keeping its fullname. The entry is
     * validated against the url for that case, so the new link is resolved rather than served the
     * type of the old one.
     */
    @Test
    public void aPostWhoseUrlWasRewrittenIsResolvedAgainWithoutAnInvalidation() throws Exception {
        assertEquals(Type.LINK, ContentType.getContentType(post("t3_cache4", LINK_URL)));

        assertEquals(
                "same post, new link: the cached type must not be reused",
                Type.IMAGE,
                ContentType.getContentType(post("t3_cache4", "https://example.com/photo.png")));
    }
}
