package me.edgan.redditslide.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import androidx.test.core.app.ApplicationProvider;
import me.edgan.redditslide.Reddit;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/**
 * The per-subreddit layout preset ("PRESET" + subreddit), which the subreddit settings screen
 * writes and the feed reads.
 *
 * <p>The two disagreed about capitalisation: the write, the probe and the removes built the key
 * from the subreddit exactly as displayed while {@code SubmissionAdapter} lowercased it, so for any
 * subreddit whose name is not already lowercase the custom layout was stored under a key nothing
 * ever read and turning it on did nothing. All five sites now go through
 * {@link Reddit#getLayoutPrefKey}, so the lowercasing is the thing to pin.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33, application = Application.class)
public class RedditLayoutPrefKeyTest {

    @Test
    public void theKeyIsTheSameWhateverCapitalisationTheSubredditArrivesIn() {
        assertEquals(
                Reddit.getLayoutPrefKey("askreddit"), Reddit.getLayoutPrefKey("AskReddit"));
    }

    /** The write happens in settings, under the name as displayed; the read happens in the feed. */
    @Test
    public void aPresetSavedFromSettingsIsFoundByTheFeed() {
        Context context = ApplicationProvider.getApplicationContext();
        SharedPreferences prefs =
                context.getSharedPreferences("layout-preset-test", Context.MODE_PRIVATE);
        prefs.edit().clear().commit();

        prefs.edit().putBoolean(Reddit.getLayoutPrefKey("AskReddit"), true).commit();

        assertTrue(
                "the feed asks for the subreddit it is showing",
                prefs.contains(Reddit.getLayoutPrefKey("askreddit")));
    }
}
