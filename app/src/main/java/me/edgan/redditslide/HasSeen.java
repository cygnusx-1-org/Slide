package me.edgan.redditslide;

import static me.edgan.redditslide.OpenRedditLink.formatRedditUrl;
import static me.edgan.redditslide.OpenRedditLink.getRedditLinkType;

import android.net.Uri;
import android.os.AsyncTask;
import androidx.annotation.Nullable;
import com.lusfold.androidkeyvaluestore.KVStore;
import com.lusfold.androidkeyvaluestore.core.KVManger;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import net.dean.jraw.models.Contribution;
import net.dean.jraw.models.Submission;
import net.dean.jraw.models.VoteDirection;

/** Created by ccrama on 7/19/2015. */
public class HasSeen {

    // Built here rather than lazily in each entry point: every public method used to open with the
    // same "if (hasSeen == null)" block, and CommentAdapter reaches straight for seenTimes on the
    // strength of having called getSeenTime() first.
    public static final HashSet<String> hasSeen = new HashSet<>();
    public static final HashMap<String, Long> seenTimes = new HashMap<>();

    // Submissions seen since the last time they were pushed to reddit's own visited-posts history
    // (AccountManager.storeVisits, called from MainActivity.onPause, which then drops the ids it
    // sent).
    // Private, and reached only through the synchronized accessors below: the adds happen on the
    // main thread while the push iterates the list on a background one, so handing that push the
    // list itself lets an add during the loop throw ConcurrentModificationException at it.
    private static final ArrayList<String> newVisited = new ArrayList<>();

    private static synchronized void addNewVisited(String fullname) {
        newVisited.add(fullname);
    }

    /** Whether a push has anything to send, without copying the list to answer. */
    public static synchronized boolean hasNewVisited() {
        return !newVisited.isEmpty();
    }

    /** A copy, safe to iterate off the main thread while more posts are being marked seen. */
    public static synchronized List<String> newVisitedSnapshot() {
        return new ArrayList<>(newVisited);
    }

    /**
     * Drops the ids reddit has accepted, and only those. A post marked seen while the push was in
     * flight is not in {@code pushed}, and clearing the whole queue instead would drop it without
     * ever sending it -- permanently for one seen by scrolling, since {@code hasSeen} already
     * holds it and the fling guard in {@link #addSeenScrolling} stops it being queued again.
     */
    public static synchronized void clearNewVisited(Collection<String> pushed) {
        final Set<String> accepted = new HashSet<>(pushed);
        newVisited.removeIf(accepted::contains);
    }

    /** Forgets the whole queue, pushed or not. */
    public static synchronized void clearNewVisited() {
        newVisited.clear();
    }

    public static void setHasSeenContrib(List<Contribution> submissions) {
        KVManger m = KVStore.getInstance();
        for (Contribution s : submissions) {
            if (s instanceof Submission) {
                historyContains(s, m);
            }
        }
    }

    public static void setHasSeenSubmission(List<Submission> submissions) {
        KVManger m = KVStore.getInstance();
        for (Contribution s : submissions) {
            historyContains(s, m);
        }
    }

    /**
     * Strips the {@code t3_} prefix a fullname carries, or returns null when JRAW had no {@code
     * name} in the JSON to give. Every store here is keyed on the result, so a null means there is
     * nothing to record or look up rather than a key spelled "null".
     */
    private static @Nullable String seenKey(@Nullable String fullname) {
        return fullname == null ? null : seenKeyOf(fullname);
    }

    /** {@link #seenKey} for a fullname already known to be present. */
    private static String seenKeyOf(String fullname) {
        return fullname.contains("t3_") ? fullname.substring(3) : fullname;
    }

    private static void historyContains(Contribution s, KVManger m) {
        // Both keyings are needed: this table is keyed on the stripped id, while LastComments
        // keys on the raw t3_ fullname, so the comments lookup below must not be given the
        // stripped one or it can never match what setComments wrote.
        final String rawFullname = s.getFullName();
        if (rawFullname == null) {
            return;
        }
        final String fullname = seenKeyOf(rawFullname);

        // Key is the KVStore table's primary key, so these exact-match lookups use its index. A
        // LIKE '%fullname%' scan cannot, and read the whole (unbounded) table for every submission.
        String value = m.get(fullname);
        if (value != null) {
            hasSeen.add(fullname);
            try {
                seenTimes.put(fullname, Long.valueOf(value));
            } catch (Exception ignored) {
                // A stored value that is not a timestamp leaves seenTimes without an
                // entry; the post still counts as seen.
            }
        } else if (m.keyExists(LastComments.commentsKey(rawFullname))) {
            // The post itself was never marked seen but its comments were visited (a NSFW post
            // while storeNSFWHistory is off); the old LIKE scan matched that key too.
            hasSeen.add(fullname);
        }
    }

    public static boolean getSeen(Submission s) {
        String fullname = seenKey(s.getFullName());
        // Without a fullname the set lookup cannot match, but the node and the vote still
        // answer the question, so they are left to decide it rather than returning false outright.
        return ((fullname != null && hasSeen.contains(fullname))
                || (s.getDataNode().has("visited") && s.getDataNode().path("visited").asBoolean())
                || s.getVote() != VoteDirection.NO_VOTE);
    }

    /**
     * Puts a "no participation" link back on the host it is a copy of. Reddit hands those out on
     * the {@code np.} subdomain, which {@link OpenRedditLink#formatRedditUrl} normalises to a
     * {@code npreddit.com} host; dropping the prefix leaves {@code reddit.com}, so the link is
     * classified as the ordinary link it is.
     *
     * <p>Extracted from {@link #getSeen(String)} so it can be asserted directly, because it cannot
     * be asserted through that method's result: every link shape the switch there handles takes
     * the post id from a path segment, which the host does not affect, and {@link
     * OpenRedditLink#getRedditLinkType} reads the host only to spot {@code redd.it}. Stripped or
     * not, those links classify and file identically — so with no seam, deleting the strip left
     * the whole suite green. See TEST-GAPS.md.
     */
    public static Uri stripNoParticipation(Uri uri) {
        // formatRedditUrl only returns a Uri it could read a host from.
        String host = Objects.requireNonNull(uri.getHost());

        if (host.startsWith("np")) {
            return uri.buildUpon().authority(host.substring(2)).build();
        }

        return uri;
    }

    public static boolean getSeen(String s) {
        Uri uri = formatRedditUrl(s);
        String fullname = s;
        if (uri != null) {
            uri = stripNoParticipation(uri);

            OpenRedditLink.RedditLinkType type = getRedditLinkType(uri);
            List<String> parts = uri.getPathSegments();

            switch (type) {
                case SHORTENED:
                    {
                        fullname = parts.get(0);
                        break;
                    }
                case COMMENT_PERMALINK:
                case SUBMISSION:
                    {
                        fullname = parts.get(3);
                        break;
                    }
                case SUBMISSION_WITHOUT_SUB:
                    {
                        fullname = parts.get(1);
                        break;
                    }
            }
        }

        if (fullname.contains("t3_")) {
            fullname = fullname.substring(3);
        }
        // Answered before the add, not after: this entry point marks the post seen as well as
        // reporting on it, so asking the set afterwards can only ever say yes.
        final boolean alreadySeen = hasSeen.contains(fullname);
        hasSeen.add(fullname);
        return alreadySeen;
    }

    public static long getSeenTime(Submission s) {
        String fullname = seenKey(s.getFullName());
        if (fullname == null) {
            return 0;
        }
        if (seenTimes.containsKey(fullname)) {
            return seenTimes.get(fullname);
        } else {
            try {
                return Long.parseLong(KVStore.getInstance().get(fullname));
            } catch (NumberFormatException e) {
                return 0;
            }
        }
    }

    public static void addSeen(@Nullable String rawFullname) {
        String fullname = seenKey(rawFullname);
        if (fullname == null) {
            return;
        }

        hasSeen.add(fullname);
        seenTimes.put(fullname, System.currentTimeMillis());

        long result =
                KVStore.getInstance().insert(fullname, String.valueOf(System.currentTimeMillis()));
        if (result == -1) {
            KVStore.getInstance().update(fullname, String.valueOf(System.currentTimeMillis()));
        }

        if (!fullname.contains("t1_")) {
            addNewVisited(fullname);
        }
    }

    public static void addSeenScrolling(@Nullable String rawFullname) {
        String fullname = seenKey(rawFullname);
        if (fullname == null) {
            return;
        }

        // Called from onScrolled, i.e. many times a second while flinging. Everything below only
        // has to happen the first time a post is seen: seenTimes must keep the first-seen
        // timestamp, insert() is a no-op once the key exists, and newVisited is an ArrayList that
        // would otherwise collect a duplicate per frame.
        if (hasSeen.contains(fullname) && seenTimes.containsKey(fullname)) {
            return;
        }

        hasSeen.add(fullname);
        seenTimes.put(fullname, System.currentTimeMillis());

        final String key = fullname;
        final String value = String.valueOf(System.currentTimeMillis());
        // Off the UI thread: insert() runs a SELECT plus an INSERT against the seen database.
        AsyncTask.THREAD_POOL_EXECUTOR.execute(() -> KVStore.getInstance().insert(key, value));

        if (!fullname.contains("t1_")) {
            addNewVisited(fullname);
        }
    }
}
