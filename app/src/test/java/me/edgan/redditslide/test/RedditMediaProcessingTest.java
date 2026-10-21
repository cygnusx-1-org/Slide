package me.edgan.redditslide.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import me.edgan.redditslide.util.RedditMediaProcessing;
import org.junit.After;
import org.junit.Test;

/** Tests for the post-submit wait in {@link RedditMediaProcessing}. */
public class RedditMediaProcessingTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final List<Long> sleeps = new ArrayList<>();

    @After
    public void clearInterrupt() {
        // A test that interrupts must not leak the flag into the next one.
        Thread.interrupted();
    }

    private static JsonNode info(String... statuses) throws Exception {
        StringBuilder metadata = new StringBuilder();
        for (int i = 0; i < statuses.length; i++) {
            if (i > 0) metadata.append(',');
            metadata.append("\"id").append(i).append("\":{\"status\":\"");
            metadata.append(statuses[i]).append("\"}");
        }
        return MAPPER.readTree(
                "{\"kind\":\"Listing\",\"data\":{\"children\":[{\"kind\":\"t3\",\"data\":"
                        + "{\"id\":\"abc\",\"media_metadata\":{"
                        + metadata
                        + "}}}]}}");
    }

    private RedditMediaProcessing.Sleeper recordingSleeper() {
        return sleeps::add;
    }

    @Test
    public void hasUnprocessedMedia_onlyForUnprocessed() throws Exception {
        assertTrue(RedditMediaProcessing.hasUnprocessedMedia(info("valid", "unprocessed")));
        assertFalse(RedditMediaProcessing.hasUnprocessedMedia(info("valid", "valid")));
        assertFalse(RedditMediaProcessing.hasUnprocessedMedia(info("failed", "failed")));
        assertFalse(RedditMediaProcessing.hasUnprocessedMedia(null));
        assertFalse(RedditMediaProcessing.hasUnprocessedMedia(MAPPER.readTree("{}")));
        assertFalse(
                RedditMediaProcessing.hasUnprocessedMedia(
                        MAPPER.readTree("{\"data\":{\"children\":[]}}")));
        assertFalse(
                RedditMediaProcessing.hasUnprocessedMedia(
                        MAPPER.readTree(
                                "{\"data\":{\"children\":[{\"data\":{\"media_metadata\":null}}]}}")));
    }

    @Test
    public void stopsOnceAllValid() throws Exception {
        JsonNode[] replies = {
            info("unprocessed", "unprocessed"), info("valid", "unprocessed"), info("valid", "valid")
        };
        int[] calls = {0};
        int tries =
                RedditMediaProcessing.awaitProcessed(
                        () -> replies[calls[0]++], recordingSleeper());
        assertEquals(3, tries);
        assertEquals(3, calls[0]);
        assertEquals(2, sleeps.size());
        for (long sleep : sleeps) {
            assertEquals(RedditMediaProcessing.INTERVAL_MS, sleep);
        }
    }

    @Test
    public void stopsImmediatelyWhenAlreadyValid() throws Exception {
        JsonNode valid = info("valid");
        assertEquals(1, RedditMediaProcessing.awaitProcessed(() -> valid, recordingSleeper()));
        assertTrue(sleeps.isEmpty());
    }

    @Test
    public void stopsOnUnreadableReply() {
        assertEquals(1, RedditMediaProcessing.awaitProcessed(() -> null, recordingSleeper()));
        assertTrue(sleeps.isEmpty());
    }

    @Test
    public void stopsOnFailedRequest() {
        assertEquals(
                1,
                RedditMediaProcessing.awaitProcessed(
                        () -> {
                            throw new RuntimeException("HTTP 500");
                        },
                        recordingSleeper()));
        assertTrue(sleeps.isEmpty());
    }

    @Test
    public void doesNotWaitOnFailed() throws Exception {
        JsonNode failed = info("failed", "failed");
        assertEquals(1, RedditMediaProcessing.awaitProcessed(() -> failed, recordingSleeper()));
        assertTrue(sleeps.isEmpty());
    }

    @Test
    public void givesUpAfterMaxTries() throws Exception {
        JsonNode unprocessed = info("unprocessed");
        int[] calls = {0};
        int tries =
                RedditMediaProcessing.awaitProcessed(
                        () -> {
                            calls[0]++;
                            return unprocessed;
                        },
                        recordingSleeper());
        assertEquals(10, RedditMediaProcessing.MAX_TRIES);
        assertEquals(RedditMediaProcessing.MAX_TRIES, tries);
        assertEquals(RedditMediaProcessing.MAX_TRIES, calls[0]);
        assertEquals(RedditMediaProcessing.MAX_TRIES - 1, sleeps.size());
    }

    @Test
    public void interruptStopsAndRestoresFlag() throws Exception {
        JsonNode unprocessed = info("unprocessed");
        int[] calls = {0};
        int tries =
                RedditMediaProcessing.awaitProcessed(
                        () -> {
                            calls[0]++;
                            return unprocessed;
                        },
                        millis -> {
                            throw new InterruptedException();
                        });
        assertEquals(1, tries);
        assertEquals(1, calls[0]);
        assertTrue(Thread.currentThread().isInterrupted());
    }
}
