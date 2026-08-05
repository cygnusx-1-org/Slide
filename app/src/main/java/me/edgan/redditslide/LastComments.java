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
        if (commentsSince != null && commentsSince.containsKey(s.getFullName()))
            return s.getCommentCount() - commentsSince.get(s.getFullName());
        return 0;
    }

    public static void setComments(Submission s) {
        if (commentsSince == null) {
            commentsSince = new HashMap<>();
        }
        KVStore.getInstance()
                .insertOrUpdate(commentsKey(s.getFullName()), String.valueOf(s.getCommentCount()));
        commentsSince.put(s.getFullName(), s.getCommentCount());
    }
}
