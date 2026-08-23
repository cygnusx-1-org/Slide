package me.edgan.redditslide;

import androidx.annotation.Nullable;

import com.lusfold.androidkeyvaluestore.KVStore;
import com.lusfold.androidkeyvaluestore.core.KVManger;
import java.util.HashMap;
import java.util.List;
import net.dean.jraw.models.Submission;

/** Created by ccrama on 7/19/2015. */
public class LastComments {

    // Null until the first setCommentsSince/setComments call builds it, which every reader here
    // already checks for.
    @Nullable public static HashMap<String, Integer> commentsSince;

    /** The KVStore key holding the comment count last seen for {@code fullname} (a t3_ fullname). */
    public static String commentsKey(String fullname) {
        return "comments" + fullname;
    }

    public static void setCommentsSince(List<Submission> submissions) {
        if (commentsSince == null) {
            commentsSince = new HashMap<>();
        }
        KVManger m = KVStore.getInstance();
        try {
            for (Submission s : submissions) {
                String fullname = s.getFullName();
                if (fullname == null) {
                    // No "name" in the JSON, so there is no key to look up. commentsKey would
                    // concatenate one reading "commentsnull" and store a count under it.
                    continue;
                }

                // Key is the KVStore table's primary key, so this exact-match lookup uses its
                // index. A LIKE '%comments<fullname>%' scan cannot, and read the whole (unbounded)
                // table for every submission.
                String value = m.get(commentsKey(fullname));
                if (value != null) {
                    commentsSince.put(fullname, Integer.valueOf(value));
                }
            }
        } catch (Exception ignored) {
            // A cache row that will not parse abandons this pass — the entries already read stay
            // in commentsSince, and every submission it did not reach reads as 'no new comments'.
        }
    }

    public static int commentsSince(Submission s) {
        final Integer count = s.getCommentCount();
        final Integer since =
                commentsSince == null ? null : commentsSince.get(s.getFullName());
        if (count == null || since == null) {
            // No comment count, or none recorded from a previous visit: nothing new to report.
            return 0;
        }
        return count - since;
    }

    public static void setComments(Submission s) {
        if (commentsSince == null) {
            commentsSince = new HashMap<>();
        }
        String fullname = s.getFullName();
        if (fullname == null) {
            // See setCommentsSince: without a fullname there is no key worth writing.
            return;
        }
        KVStore.getInstance()
                .insertOrUpdate(commentsKey(fullname), String.valueOf(s.getCommentCount()));
        commentsSince.put(fullname, s.getCommentCount());
    }
}
