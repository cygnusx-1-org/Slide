package me.edgan.redditslide;

import android.os.AsyncTask;
import java.util.HashSet;
import java.util.Set;
import me.edgan.redditslide.util.LogUtil;
import net.dean.jraw.managers.AccountManager;
import net.dean.jraw.models.Contribution;
import net.dean.jraw.models.Submission;

/** Created by carlo_000 on 10/16/2015. */
public class Hidden {
    public static final Set<String> id = new HashSet<>();

    public static void setHidden(final Contribution s) {
        new AsyncTask<Void, Void, Void>() {
            @Override
            protected Void doInBackground(Void[] params) {
                try {
                    id.add(s.getFullName());
                    new AccountManager(Authentication.reddit).hide(true, (Submission) s);
                } catch (Exception e) {
                    LogUtil.e(e, "Hidden.doInBackground failed");
                }
                return null;
            }
        }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }

    public static void undoHidden(final Contribution s) {
        new AsyncTask<Void, Void, Void>() {
            @Override
            protected Void doInBackground(Void[] params) {
                try {
                    id.remove(s.getFullName());
                    new AccountManager(Authentication.reddit).hide(false, (Submission) s);
                } catch (Exception e) {
                    LogUtil.e(e, "Hidden.doInBackground failed");
                }
                return null;
            }
        }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }
}
