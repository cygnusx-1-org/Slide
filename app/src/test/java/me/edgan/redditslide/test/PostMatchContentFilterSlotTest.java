package me.edgan.redditslide.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import androidx.test.core.app.ApplicationProvider;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import me.edgan.redditslide.PostMatch;
import me.edgan.redditslide.SettingValues;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/**
 * The sixteen "filter this content" checkboxes, each against the accessor that reads it back.
 *
 * <p>{@code setChosen} takes a {@code boolean[]} straight from a dialog's checkbox order and writes
 * it through two hand-written arrays of key strings; sixteen sibling accessors then read those keys
 * back, each restating its own key literal, on either of two branches. That is thirty-two literals
 * and one positional convention with nothing tying them together, so a key repeated, reordered or
 * misspelled in any one of them silently applies the wrong filter -- the user ticks "NSFW links"
 * and NSFW selftexts disappear instead.
 *
 * <p>The existing coverage spot-checks the eight ordinary slots and three of the eight NSFW ones,
 * on the in-memory branch only. Swapping two of the untested NSFW keys left the whole suite green.
 *
 * <p>These walk every slot on both branches: each index is turned on alone and has to light up its
 * own accessor and no other.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33, application = Application.class)
public class PostMatchContentFilterSlotTest {

    /** The accessors in the order {@code setChosen} writes its {@code boolean[]}. */
    private static final String[] SLOT_NAMES = {
        "albums", "galleries", "gifs", "images", "links", "selftexts", "tumblrs", "videos",
        "nsfwAlbums", "nsfwGalleries", "nsfwGifs", "nsfwImages", "nsfwLinks", "nsfwSelftexts",
        "nsfwTumblrs", "nsfwVideos"
    };

    private static final List<Predicate<String>> SLOT_READERS = readers();

    private static List<Predicate<String>> readers() {
        return Arrays.<Predicate<String>>asList(
            PostMatch::isAlbum,
            PostMatch::isGallery,
            PostMatch::isGif,
            PostMatch::isImage,
            PostMatch::isLink,
            PostMatch::isSelftext,
            PostMatch::isTumblr,
            PostMatch::isVideo,
            PostMatch::isNsfwAlbum,
            PostMatch::isNsfwGallery,
            PostMatch::isNsfwGif,
            PostMatch::isNsfwImage,
            PostMatch::isNsfwLink,
            PostMatch::isNsfwSelftext,
            PostMatch::isNsfwTumblr,
            PostMatch::isNsfwVideo);
    }

    private boolean tillRestartWas;
    private boolean showNsfwWas;
    private SharedPreferences filtersWas;

    @Before
    public void setUp() {
        tillRestartWas = SettingValues.subredditFiltersTillRestart;
        showNsfwWas = SettingValues.showNSFWContent;
        filtersWas = PostMatch.filters;

        // The NSFW half of the array is only written when NSFW content is on at all.
        SettingValues.showNSFWContent = true;
        clearMemoryContentFilters();
    }

    @After
    public void tearDown() {
        SettingValues.subredditFiltersTillRestart = tillRestartWas;
        SettingValues.showNSFWContent = showNsfwWas;
        PostMatch.filters = filtersWas;
        clearMemoryContentFilters();
    }

    /** The map is a private static with no reset hook, and this JVM is shared. */
    @SuppressWarnings("unchecked")
    private static void clearMemoryContentFilters() {
        try {
            Field f = PostMatch.class.getDeclaredField("memoryContentFilters");
            f.setAccessible(true);
            ((Map<String, Boolean>) f.get(null)).clear();
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException("Failed to clear PostMatch.memoryContentFilters", e);
        }
    }

    /** Turns on slot {@code on} alone and checks every accessor answers for its own slot. */
    private void assertSlotIsWiredToItsOwnAccessor(int on, String subreddit) {
        boolean[] values = new boolean[SLOT_NAMES.length];
        values[on] = true;
        PostMatch.setChosen(values, subreddit);

        for (int i = 0; i < SLOT_NAMES.length; i++) {
            assertEquals(
                    "checkbox \""
                            + SLOT_NAMES[on]
                            + "\" lit up the \""
                            + SLOT_NAMES[i]
                            + "\" filter",
                    i == on,
                    SLOT_READERS.get(i).test(subreddit));
        }
    }

    @Test
    public void everySlotIsWiredToItsOwnAccessorInMemory() {
        SettingValues.subredditFiltersTillRestart = true;

        for (int slot = 0; slot < SLOT_NAMES.length; slot++) {
            // The in-memory map has no per-subreddit reset, so give each slot its own subreddit.
            assertSlotIsWiredToItsOwnAccessor(slot, "memslot" + slot);
        }
    }

    /**
     * The other branch, which no test had reached because {@code PostMatch.filters} is only
     * assigned by the running app. It restates all sixteen keys a second time.
     */
    @Test
    public void everySlotIsWiredToItsOwnAccessorInPreferences() {
        SettingValues.subredditFiltersTillRestart = false;
        final Context context = ApplicationProvider.getApplicationContext();
        SharedPreferences prefs =
                context.getSharedPreferences("content-filter-slot-test", Context.MODE_PRIVATE);
        prefs.edit().clear().commit();
        PostMatch.filters = prefs;

        for (int slot = 0; slot < SLOT_NAMES.length; slot++) {
            assertSlotIsWiredToItsOwnAccessor(slot, "prefslot" + slot);
        }
    }

    /** With NSFW content off the NSFW half is not written at all, and reads as off. */
    @Test
    public void theNsfwSlotsAreNotWrittenWhenNsfwContentIsOff() {
        SettingValues.subredditFiltersTillRestart = true;
        SettingValues.showNSFWContent = false;

        boolean[] values = new boolean[SLOT_NAMES.length];
        for (int i = 8; i < values.length; i++) {
            values[i] = true;
        }
        PostMatch.setChosen(values, "nsfwoff");

        for (int i = 8; i < SLOT_NAMES.length; i++) {
            assertFalse(
                    SLOT_NAMES[i] + " must stay off when NSFW content is disabled",
                    SLOT_READERS.get(i).test("nsfwoff"));
        }
    }

    /** A short array (the dialog with no NSFW rows) must not reach the NSFW half. */
    @Test
    public void anEightSlotArrayLeavesTheNsfwSlotsAlone() {
        SettingValues.subredditFiltersTillRestart = true;

        PostMatch.setChosen(new boolean[] {true, true, true, true, true, true, true, true},
                "eightonly");

        assertTrue(PostMatch.isAlbum("eightonly"));
        for (int i = 8; i < SLOT_NAMES.length; i++) {
            assertFalse(
                    SLOT_NAMES[i] + " must be untouched by an eight-slot array",
                    SLOT_READERS.get(i).test("eightonly"));
        }
    }
}
