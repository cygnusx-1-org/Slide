package me.edgan.redditslide.Autocache;

/** Created by carlo_000 on 10/13/2015. */
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.AsyncTask;

import me.edgan.redditslide.CommentCacheAsync;
import me.edgan.redditslide.Reddit;
import me.edgan.redditslide.util.NetworkUtil;
import me.edgan.redditslide.util.PrefUtil;

public class CacheAll extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        if (NetworkUtil.isConnectedNoOverride(context)) {
            if (Reddit.cachedData.getBoolean("wifiOnly", false)
                    && !NetworkUtil.isConnectedWifi(context)) return;
            new CommentCacheAsync(
                            context,
                            PrefUtil.getString(Reddit.cachedData, "toCache", "").split(","))
                    .executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
        }
    }
}
