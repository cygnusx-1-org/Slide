package me.edgan.redditslide.test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.text.Spanned;
import android.widget.TextView;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Scanner;
import java.util.Set;
import me.edgan.redditslide.markdown.RedditMarkwon;
import me.edgan.redditslide.markdown.RedditSpoilerPreprocessor;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

/**
 * Permanent equivalence suite (Slide issue #179): renders a shared markdown corpus through Slide's
 * new Reddit-style renderer and asserts it matches golden fixtures generated from Continuum's real
 * renderer (see {@code SlideGoldenGeneratorTest} in the Continuum repo). The comparison is on
 * normalized text + semantic spans (see {@link MarkdownNormalizer}).
 */
@RunWith(RobolectricTestRunner.class)
public class MarkdownEquivalenceTest {

    /**
     * Corpus ids where Slide intentionally diverges from Continuum (Slide matches new Reddit
     * better): Continuum superscripts only one char after {@code ^} and collapses {@code ^^},
     * whereas Slide superscripts the whole token and supports nesting.
     */
    private static final Set<String> EXPECTED_DIFFERENT =
            new HashSet<>(Arrays.asList("superscript_word", "superscript_nested"));

    /**
     * Corpus ids skipped because they depend on the test environment, not the renderer: bare-URL
     * autolinking goes through Android's {@code Linkify}, whose {@code WEB_URL} pattern yields
     * nothing under Slide's Robolectric (4.16.x) but works on-device and under the generator's
     * Robolectric. Slide uses the same {@code LinkifyPlugin.WEB_URLS} as Continuum, so this is a
     * harness artifact, not a rendering difference.
     */
    private static final Set<String> SKIP_ENV = new HashSet<>(Arrays.asList("autolink"));

    @Test
    public void matchesContinuumGoldens() throws Exception {
        Application ctx = RuntimeEnvironment.getApplication();
        JSONArray goldens = new JSONArray(readResource("/markdown/golden.json"));

        StringBuilder failures = new StringBuilder();
        for (int i = 0; i < goldens.length(); i++) {
            JSONObject golden = goldens.getJSONObject(i);
            String id = golden.getString("id");
            String md = golden.getString("md");
            if (SKIP_ENV.contains(id)) {
                continue;
            }

            // Render through a TextView so Markwon's afterSetText hooks (e.g. linkify) run, as in
            // the real app — toMarkdown() alone skips them.
            TextView tv = new TextView(ctx);
            RedditMarkwon.get(ctx).setMarkdown(tv, RedditSpoilerPreprocessor.sentinelize(md));
            JSONObject slide = MarkdownNormalizer.normalize(id, md, (Spanned) tv.getText());

            boolean same =
                    slide.getString("text").equals(golden.getString("text"))
                            && slide.getJSONArray("spans")
                                    .toString()
                                    .equals(golden.getJSONArray("spans").toString());

            if (EXPECTED_DIFFERENT.contains(id)) {
                assertFalse(
                        "Expected '" + id + "' to differ from Continuum, but it matched", same);
            } else if (!same) {
                failures.append("\n  ")
                        .append(id)
                        .append("\n    slide  = ")
                        .append(slide)
                        .append("\n    golden = ")
                        .append(golden);
            }
        }
        assertTrue("Slide diverged from Continuum goldens:" + failures, failures.length() == 0);
    }

    private static String readResource(String path) {
        try (InputStream in = MarkdownEquivalenceTest.class.getResourceAsStream(path)) {
            if (in == null) {
                throw new IllegalStateException("missing test resource: " + path);
            }
            return new Scanner(in, StandardCharsets.UTF_8.name()).useDelimiter("\\A").next();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
