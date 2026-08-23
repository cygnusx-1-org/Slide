package me.edgan.redditslide.SubmissionViews;

import android.app.Activity;
import android.os.AsyncTask;
import java.lang.ref.WeakReference;
import java.net.HttpURLConnection;
import java.net.URL;
import me.edgan.redditslide.OpenRedditLink;
import me.edgan.redditslide.Visuals.Palette;
import me.edgan.redditslide.util.LinkUtil;
import me.edgan.redditslide.util.LogUtil;

public class OpenVRedditTask extends AsyncTask<String, Void, Void> {

    private WeakReference<Activity> contextActivity;
    private String subreddit;

    public OpenVRedditTask(Activity contextActivity, String subreddit) {
        this.contextActivity = new WeakReference<>(contextActivity);
        this.subreddit = subreddit;
    }

    protected Void doInBackground(String... urls) {
        String url = urls[0];
        if (url.endsWith("/")) {
            url = url.substring(0, url.length() - 1);
        }
        String hash = url.substring(url.lastIndexOf("/"));
        try {
            URL newUrl = new URL("https://www.reddit.com/video" + hash);
            HttpURLConnection ucon = (HttpURLConnection) newUrl.openConnection();
            ucon.setInstanceFollowRedirects(false);
            String secondURL = new URL(ucon.getHeaderField("location")).toString();

            LogUtil.v(secondURL);

            final Activity activity = contextActivity.get();
            if (activity == null) {
                return null;
            }

            OpenRedditLink.openUrl(activity, secondURL, true);

        } catch (Exception e) {
            LogUtil.e(e, "OpenVRedditTask.doInBackground failed");
            final Activity failureActivity = contextActivity.get();
            if (failureActivity != null) {
                LinkUtil.openUrl(url, Palette.getColor(subreddit), failureActivity);
            }
        }
        return null;
    }
}
