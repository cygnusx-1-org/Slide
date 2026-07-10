package me.edgan.redditslide;

import static me.edgan.redditslide.OpenRedditLink.formatRedditUrl;
import static me.edgan.redditslide.OpenRedditLink.getRedditLinkType;

import android.net.Uri;
import android.os.AsyncTask;
import com.lusfold.androidkeyvaluestore.KVStore;
import com.lusfold.androidkeyvaluestore.core.KVManger;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import me.edgan.redditslide.Synccit.SynccitRead;
import net.dean.jraw.models.Contribution;
import net.dean.jraw.models.Submission;
import net.dean.jraw.models.VoteDirection;

/** Created by ccrama on 7/19/2015. */
public class HasSeen {

    public static HashSet<String> hasSeen;
    public static HashMap<String, Long> seenTimes;

    public static void setHasSeenContrib(List<Contribution> submissions) {
        if (hasSeen == null) {
            hasSeen = new HashSet<>();
            seenTimes = new HashMap<>();
        }
        KVManger m = KVStore.getInstance();
        for (Contribution s : submissions) {
            if (s instanceof Submission) {
                historyContains(s, m);
            }
        }
    }

    public static void setHasSeenSubmission(List<Submission> submissions) {
        if (hasSeen == null) {
            hasSeen = new HashSet<>();
            seenTimes = new HashMap<>();
        }
        KVManger m = KVStore.getInstance();
        for (Contribution s : submissions) {
            historyContains(s, m);
        }
    }

    private static void historyContains(Contribution s, KVManger m) {
        String fullname = s.getFullName();
        if (fullname.contains("t3_")) {
            fullname = fullname.substring(3);
        }

        // Key is the KVStore table's primary key, so these exact-match lookups use its index. A
        // LIKE '%fullname%' scan cannot, and read the whole (unbounded) table for every submission.
        String value = m.get(fullname);
        if (value != null) {
            hasSeen.add(fullname);
            try {
                seenTimes.put(fullname, Long.valueOf(value));
            } catch (Exception ignored) {
            }
        } else if (m.keyExists(LastComments.commentsKey(s.getFullName()))) {
            // The post itself was never marked seen but its comments were visited (a NSFW post
            // while storeNSFWHistory is off); the old LIKE scan matched that key too.
            hasSeen.add(fullname);
        }
    }

    public static boolean getSeen(Submission s) {
        if (hasSeen == null) {
            hasSeen = new HashSet<>();
            seenTimes = new HashMap<>();
        }

        String fullname = s.getFullName();
        if (fullname.contains("t3_")) {
            fullname = fullname.substring(3);
        }
        return (hasSeen.contains(fullname)
                || SynccitRead.visitedIds.contains(fullname)
                || s.getDataNode().has("visited") && s.getDataNode().get("visited").asBoolean()
                || s.getVote() != VoteDirection.NO_VOTE);
    }

    public static boolean getSeen(String s) {
        if (hasSeen == null) {
            hasSeen = new HashSet<>();
            seenTimes = new HashMap<>();
        }

        Uri uri = formatRedditUrl(s);
        String fullname = s;
        if (uri != null) {
            String host = uri.getHost();

            if (host.startsWith("np")) {
                uri = uri.buildUpon().authority(host.substring(2)).build();
            }

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
        hasSeen.add(fullname);
        return (hasSeen.contains(fullname) || SynccitRead.visitedIds.contains(fullname));
    }

    public static long getSeenTime(Submission s) {
        if (hasSeen == null) {
            hasSeen = new HashSet<>();
            seenTimes = new HashMap<>();
        }
        String fullname = s.getFullName();
        if (fullname.contains("t3_")) {
            fullname = fullname.substring(3);
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

    public static void addSeen(String fullname) {
        if (hasSeen == null) {
            hasSeen = new HashSet<>();
        }
        if (seenTimes == null) {
            seenTimes = new HashMap<>();
        }

        if (fullname.contains("t3_")) {
            fullname = fullname.substring(3);
        }

        hasSeen.add(fullname);
        seenTimes.put(fullname, System.currentTimeMillis());

        long result =
                KVStore.getInstance().insert(fullname, String.valueOf(System.currentTimeMillis()));
        if (result == -1) {
            KVStore.getInstance().update(fullname, String.valueOf(System.currentTimeMillis()));
        }

        if (!fullname.contains("t1_")) {
            SynccitRead.newVisited.add(fullname);
            SynccitRead.visitedIds.add(fullname);
        }
    }

    public static void addSeenScrolling(String fullname) {
        if (hasSeen == null) {
            hasSeen = new HashSet<>();
        }
        if (seenTimes == null) {
            seenTimes = new HashMap<>();
        }

        if (fullname.contains("t3_")) {
            fullname = fullname.substring(3);
        }

        // Called from onScrolled, i.e. many times a second while flinging. Everything below only
        // has to happen the first time a post is seen: insert() is a no-op once the key exists,
        // and the Synccit lists are ArrayLists that would otherwise collect a duplicate per frame.
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
            SynccitRead.newVisited.add(fullname);
            SynccitRead.visitedIds.add(fullname);
        }
    }
}
