package me.edgan.redditslide;

import android.content.Context;

import androidx.annotation.Nullable;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import me.edgan.redditslide.util.LogUtil;

import net.dean.jraw.models.Comment;
import net.dean.jraw.models.Contribution;
import net.dean.jraw.models.Submission;

import org.jspecify.annotations.NullMarked;

/**
 * The list behind one profile tab, kept on disk so a hibernate resume can put it back without
 * refetching it.
 *
 * <p>The serialization is {@link SavedPostCache}'s: one Jackson array of each item's data node,
 * written to a temp file and published by rename, rebuilt by fullname prefix. That handles a mixed
 * list — the overview and comments tabs interleave submissions and comments — which is why this is
 * not the feed's {@link OfflineSubreddit}, a submissions-only store.
 *
 * <p>It is a separate class from {@code SavedPostCache} rather than a generalization of it because
 * the two want opposite things. That cache exists to save a request within half an hour and is
 * dropped the moment anything is saved or unsaved; this one exists to reproduce a screen the user
 * left, has no time-box at all, and must survive exactly the events that invalidate the other.
 *
 * <p>Three differences from it are load-bearing:
 *
 * <ul>
 *   <li>The blobs live under {@code getFilesDir()}, not the cache dir. The system reclaims the
 *       cache dir, and this is what a resume pointer points at.
 *   <li>The listing cursor is stored beside the items. A restored list whose paginator starts back
 *       at page one re-fetches everything already on screen, and
 *       {@code ContributionPosts.onPostExecute} appends without deduplicating — so the whole list
 *       would appear twice.
 *   <li>The key names the tab as well as the account, so eight tabs do not overwrite each other.
 * </ul>
 */
@NullMarked
public final class ContributionCache {

    /** Directory under {@code getFilesDir()} holding the blobs. */
    private static final String DIR_NAME = "hibernate-contributions";

    /**
     * How many tab blobs to keep. A resume restores one tab of one profile, but the user moves
     * between tabs before leaving, and each visited tab writes its own. Six is more than a session
     * plausibly leaves behind and keeps the directory from growing without bound.
     */
    private static final int MAX_BLOBS = 6;

    private static final String KEY_AFTER = "after";
    private static final String KEY_ITEMS = "items";

    /** Shared across threads; {@link ObjectMapper} is thread-safe once configured. */
    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * Blob writes run here, one at a time, so two stores for the same tab cannot interleave.
     * Dedicated daemon thread rather than the shared AsyncTask executor, so it neither blocks nor
     * is blocked by unrelated background work.
     */
    private static final ExecutorService WRITER =
            Executors.newSingleThreadExecutor(
                    r -> {
                        final Thread t = new Thread(r, "ContributionCache-writer");
                        t.setDaemon(true);
                        return t;
                    });

    private ContributionCache() {}

    /**
     * Identifies one tab of one account's profile. The category is part of it because the Saved tab
     * can be filtered to one, and the two listings are different lists.
     */
    public static String key(String username, String where, @Nullable String category) {
        return username + "|" + where + "|" + (category == null ? "" : category);
    }

    /** Filename-safe blob name (hex of the key, so arbitrary category names cannot collide). */
    private static String blobName(String key) {
        final StringBuilder sb = new StringBuilder();
        for (byte b : key.getBytes(StandardCharsets.UTF_8)) {
            sb.append(Character.forDigit((b >> 4) & 0xF, 16));
            sb.append(Character.forDigit(b & 0xF, 16));
        }
        return sb.toString();
    }

    /** The blob directory, created if need be, or null when there is no context to resolve it. */
    @Nullable
    private static File directory() {
        final Context context = Reddit.getAppContext();
        if (context == null) {
            return null;
        }
        final File dir = new File(context.getFilesDir(), DIR_NAME);
        if (!dir.exists() && !dir.mkdirs()) {
            LogUtil.e("ContributionCache could not create " + dir);
            return null;
        }
        return dir;
    }

    @Nullable
    private static File blobFile(String key) {
        final File dir = directory();
        return dir == null ? null : new File(dir, blobName(key));
    }

    /** A tab's list as it was last written, with the cursor for the page after it. */
    public static final class Cached {
        public final ArrayList<Contribution> posts;
        @Nullable public final String afterToken;

        Cached(ArrayList<Contribution> posts, @Nullable String afterToken) {
            this.posts = posts;
            this.afterToken = afterToken;
        }
    }

    /**
     * Rebuild a tab's list from its blob. Reads a file, so it MUST be called off the main thread.
     * Returns null on any miss — no blob, or a parse failure — so a partial or corrupt list is
     * never served in place of a fetch.
     */
    @Nullable
    public static Cached load(String key) {
        final File blob = blobFile(key);
        if (blob == null || !blob.exists()) {
            return null;
        }
        try {
            final JsonNode root = MAPPER.readTree(blob);
            if (root == null) {
                return null;
            }
            final JsonNode items = root.get(KEY_ITEMS);
            if (items == null || !items.isArray()) {
                return null;
            }
            final ArrayList<Contribution> posts = new ArrayList<>(items.size());
            for (JsonNode node : items) {
                // Reconstruct by fullname kind: t1_ = comment, everything else = submission.
                final String name = node.path("name").asText("");
                posts.add(name.startsWith("t1_") ? new Comment(node) : new Submission(node));
            }
            if (posts.isEmpty()) {
                return null;
            }
            final JsonNode after = root.get(KEY_AFTER);
            final String afterToken =
                    after == null || after.isNull() || after.asText().isEmpty()
                            ? null
                            : after.asText();
            return new Cached(posts, afterToken);
        } catch (Exception e) {
            LogUtil.e(e, "ContributionCache.load failed");
            return null;
        }
    }

    /**
     * Persist {@code posts} (submissions and comments, in order) as this tab's cache. The write is
     * queued on {@link #WRITER}, so this is safe to call from the main thread — which is where the
     * loaders' {@code onPostExecute} accumulates the list.
     */
    public static void store(String key, List<Contribution> posts, @Nullable String afterToken) {
        if (posts.isEmpty()) {
            return;
        }
        final ArrayList<Contribution> snapshot = new ArrayList<>(posts);
        WRITER.execute(() -> writeNow(key, snapshot, afterToken));
    }

    private static void writeNow(
            String key, ArrayList<Contribution> posts, @Nullable String afterToken) {
        final ObjectNode root = MAPPER.createObjectNode();
        if (afterToken != null && !afterToken.isEmpty()) {
            root.put(KEY_AFTER, afterToken);
        }
        final ArrayNode items = root.putArray(KEY_ITEMS);
        for (Contribution contrib : posts) {
            if (contrib != null && contrib.getDataNode() != null) {
                items.add(contrib.getDataNode());
            }
        }

        final File blob = blobFile(key);
        if (blob == null) {
            return;
        }
        // Temp file then rename, so a reader — or a process killed mid-write — sees either the
        // previous complete blob or this one, never half of either.
        final File tmp = new File(blob.getParentFile(), blob.getName() + ".tmp");
        try (FileWriter writer = new FileWriter(tmp)) {
            writer.write(root.toString());
        } catch (IOException e) {
            LogUtil.e(e, "ContributionCache.writeNow failed");
            tmp.delete();
            return;
        }
        if (!tmp.renameTo(blob)) {
            tmp.delete();
            LogUtil.e(
                    new IOException("rename failed"),
                    "ContributionCache.writeNow could not publish blob");
            return;
        }
        prune();
    }

    /** Keep the {@link #MAX_BLOBS} most recently written blobs and drop the rest. */
    private static void prune() {
        final File dir = directory();
        if (dir == null) {
            return;
        }
        final File[] files = dir.listFiles();
        if (files == null || files.length <= MAX_BLOBS) {
            return;
        }
        final File[] sorted = Arrays.copyOf(files, files.length);
        Arrays.sort(sorted, Comparator.comparingLong(File::lastModified).reversed());
        for (int i = MAX_BLOBS; i < sorted.length; i++) {
            if (!sorted[i].delete()) {
                LogUtil.e("ContributionCache.prune could not delete " + sorted[i]);
            }
        }
    }

    /**
     * Drop every cached tab. Called with the snapshot itself: an account switch or a data restore
     * makes both meaningless, and a blob left behind would be another account's listing.
     */
    public static void clear() {
        // Queued onto the writer rather than run on the caller's thread, so that a store already
        // in flight when this is called -- the user unchecking the overflow toggle mid-page is
        // enough -- finishes before the delete instead of publishing its blob after it. The queue
        // is single-threaded and FIFO, so ordering is all it takes.
        WRITER.execute(ContributionCache::clearNow);
    }

    private static void clearNow() {
        final File dir = directory();
        if (dir == null) {
            return;
        }
        final File[] files = dir.listFiles();
        if (files == null) {
            return;
        }
        for (File file : files) {
            if (!file.delete()) {
                LogUtil.e("ContributionCache.clear could not delete " + file);
            }
        }
    }
}
