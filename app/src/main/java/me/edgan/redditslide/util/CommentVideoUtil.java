package me.edgan.redditslide.util;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import androidx.annotation.Nullable;
import me.edgan.redditslide.Activities.MediaView;
import me.edgan.redditslide.SettingValues;
import org.jspecify.annotations.NullMarked;

/**
 * Opens a video uploaded through Reddit's comment composer, which comments carry as a link to
 * {@code reddit.com/link/<commentId>/video/<assetId>/player}. This is where the inline video card
 * drawn by {@link me.edgan.redditslide.Views.CommentOverflow} sends a tap, and it lands in the same
 * player a tap on the bare link already reaches.
 */
@NullMarked
public final class CommentVideoUtil {

    private CommentVideoUtil() {}

    /**
     * Strips the "www." Reddit's composer never writes but users paste. {@link
     * me.edgan.redditslide.ContentType}'s comment-video branch matches the host exactly ("
     * reddit.com"), so the "www." form would be classified as an ordinary Reddit link and open the
     * comments screen instead of the player.
     */
    public static String normalizeUrl(String url) {
        return url.replace("https://www.reddit.com/link/", "https://reddit.com/link/");
    }

    /**
     * Opens {@code url} full-screen. The url is handed over as it stands (apart from the host
     * normalisation above): {@link GifUtils.AsyncLoadGif#formatUrl} rewrites it to the playable
     * {@code v.redd.it/link/<commentId>/asset/<assetId>/DASHPlaylist.mpd} itself, and pre-converting
     * it here would produce a url that 404s.
     */
    public static void open(Context context, String rawUrl, @Nullable String subreddit) {
        final String url = normalizeUrl(rawUrl);
        if (!SettingValues.gif) {
            LinkUtil.openExternally(url);
            return;
        }
        try {
            Intent intent = new Intent(context, MediaView.class);
            intent.putExtra(MediaView.EXTRA_URL, url);
            if (subreddit != null) {
                intent.putExtra(MediaView.SUBREDDIT, subreddit);
            }
            if (!(context instanceof Activity)) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            }
            context.startActivity(intent);
        } catch (Exception e) {
            try {
                Intent view = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                view.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(view);
            } catch (Exception ignored) {
                // Neither the in-app player nor an external one would open the url; there is
                // nothing left to try.
            }
        }
    }
}
