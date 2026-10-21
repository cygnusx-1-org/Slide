package me.edgan.redditslide.util;

import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import com.fasterxml.jackson.databind.JsonNode;
import net.dean.jraw.RedditClient;
import net.dean.jraw.http.HttpRequest;
import org.jspecify.annotations.NullMarked;

/**
 * Waits for Reddit to finish processing the images of a freshly submitted post.
 *
 * <p>For about a second after a gallery or an inline-image self post is submitted, its {@code
 * media_metadata} reports each image as {@code "status": "unprocessed"}. Opening the post in that
 * window renders a gallery as a text post and inline images as "Processing img" placeholders, so
 * the submit paths poll {@code /api/info} until no entry is unprocessed before opening it.
 */
@NullMarked
public final class RedditMediaProcessing {

    @VisibleForTesting public static final int MAX_TRIES = 10;
    @VisibleForTesting public static final long INTERVAL_MS = 500;

    /** Fetches the {@code /api/info} reply for the post; {@code null} when there is none. */
    @VisibleForTesting
    public interface InfoFetcher {
        @Nullable JsonNode fetch() throws Exception;
    }

    @VisibleForTesting
    public interface Sleeper {
        void sleep(long millis) throws InterruptedException;
    }

    private RedditMediaProcessing() {}

    /**
     * Blocks until no image of {@code fullname} is {@code "unprocessed"}, the reply cannot be
     * read, or {@link #MAX_TRIES} tries have passed. Never throws: the post exists either way and
     * the wait is only cosmetic. Must be called off the main thread.
     *
     * @param fullname the post's fullname ({@code t3_...})
     */
    public static void awaitProcessed(RedditClient client, String fullname) {
        awaitProcessed(
                () -> {
                    HttpRequest request =
                            client.request()
                                    .path("/api/info")
                                    .query("id", fullname, "raw_json", "1")
                                    .get()
                                    .build();
                    return client.execute(request).getJson();
                },
                Thread::sleep);
    }

    /** @return the number of tries made */
    @VisibleForTesting
    public static int awaitProcessed(InfoFetcher fetcher, Sleeper sleeper) {
        int tries = 0;
        while (tries < MAX_TRIES) {
            if (tries > 0) {
                try {
                    sleeper.sleep(INTERVAL_MS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return tries;
                }
            }
            tries++;
            JsonNode root;
            try {
                root = fetcher.fetch();
            } catch (Exception e) {
                LogUtil.w("RedditMediaProcessing: info request failed: " + e);
                return tries;
            }
            if (!hasUnprocessedMedia(root)) {
                return tries;
            }
        }
        return tries;
    }

    /**
     * Whether any {@code media_metadata} entry of the first post in an {@code /api/info} reply is
     * still {@code "unprocessed"}. A missing or unreadable reply counts as not unprocessed, and so
     * does {@code "failed"}, which never changes.
     */
    @VisibleForTesting
    public static boolean hasUnprocessedMedia(@Nullable JsonNode root) {
        if (root == null) {
            return false;
        }
        JsonNode metadata =
                root.path("data").path("children").path(0).path("data").path("media_metadata");
        for (JsonNode entry : metadata) {
            if ("unprocessed".equals(entry.path("status").asText())) {
                return true;
            }
        }
        return false;
    }
}
