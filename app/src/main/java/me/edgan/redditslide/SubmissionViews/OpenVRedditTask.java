package me.edgan.redditslide.SubmissionViews;

import android.app.Activity;
import android.os.AsyncTask;

import me.edgan.redditslide.OpenRedditLink;
import me.edgan.redditslide.Visuals.Palette;
import me.edgan.redditslide.util.LinkUtil;
import me.edgan.redditslide.util.LogUtil;

import java.lang.ref.WeakReference;
import java.net.HttpURLConnection;
import java.net.URL;

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

            OpenRedditLink.openUrl(contextActivity.get(), secondURL, true);

        } catch (Exception e) {
            e.printStackTrace();
            LinkUtil.openUrl(url, Palette.getColor(subreddit), contextActivity.get());
        }
        return null;
    }
}
