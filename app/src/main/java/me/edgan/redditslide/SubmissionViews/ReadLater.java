package me.edgan.redditslide.SubmissionViews;

import com.lusfold.androidkeyvaluestore.KVStore;

import net.dean.jraw.models.Submission;

/** Created by ccrama on 7/19/2015. */
public class ReadLater {

    /** KVStore key prefix for Read Later markers. */
    public static final String PREFIX = "readLater";

    public static void setReadLater(Submission s, boolean readLater) {
        if (readLater) {
            KVStore.getInstance()
                    .insert(PREFIX + s.getFullName(), String.valueOf(System.currentTimeMillis()));
        } else {
            if (isToBeReadLater(s)) {
                KVStore.getInstance().delete(PREFIX + s.getFullName());
            }
        }
    }

    public static boolean isToBeReadLater(Submission s) {
        return !KVStore.getInstance().getByContains(PREFIX + s.getFullName()).isEmpty();
    }
}
