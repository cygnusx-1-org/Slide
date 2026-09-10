package me.edgan.redditslide.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.content.Context;
import androidx.test.core.app.ApplicationProvider;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import me.edgan.redditslide.Authentication;
import me.edgan.redditslide.SavedTagStore;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/**
 * {@link SavedTagStore}: where a saved item's tags actually live.
 *
 * <p>Reddit no longer stores any of this, so a bug here loses the user's tags outright -- there is
 * nothing to re-fetch them from. The three things worth pinning are that one account cannot see
 * another's tags, that an item can carry several at once, and that pruning drops dead membership
 * without dropping the tags themselves.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33, application = Application.class)
public class SavedTagStoreTest {

    @Before
    public void setUp() {
        TestUtils.seedRedditApplication();
        ((Context) ApplicationProvider.getApplicationContext())
                .getSharedPreferences("savedtags", Context.MODE_PRIVATE)
                .edit()
                .clear()
                .commit();
        // The store caches per account and reloads when the name changes, so every test starts by
        // naming its own account. The sandbox is shared with same-config classes.
        Authentication.name = "tester";
        forceReload();
    }

    @After
    public void tearDown() {
        Authentication.name = null;
        TestUtils.clearRedditApplication();
    }

    /** Make the store forget its in-memory map by bouncing the account it was loaded for. */
    private static void forceReload() {
        final String account = Authentication.name;
        Authentication.name = "__reload__";
        SavedTagStore.getTagNames();
        Authentication.name = account;
        SavedTagStore.getTagNames();
    }

    // ---------------------------------------------------------------------
    // Create, rename, delete
    // ---------------------------------------------------------------------

    @Test
    public void createdTagsAreListedSorted() {
        SavedTagStore.createTag("Woodworking");
        SavedTagStore.createTag("apples");
        assertEquals(Arrays.asList("apples", "Woodworking"), SavedTagStore.getTagNames());
    }

    @Test
    public void anEmptyTagIsStillATag() {
        // The whole point of creating one before you have anything to put in it.
        SavedTagStore.createTag("Recipes");
        assertFalse(SavedTagStore.isEmpty());
        assertTrue(SavedTagStore.fullnamesIn("Recipes").isEmpty());
    }

    @Test
    public void creatingAnExistingTagKeepsTheOriginalSpelling() {
        SavedTagStore.createTag("Recipes");
        assertEquals("Recipes", SavedTagStore.createTag("recipes"));
        assertEquals(Collections.singletonList("Recipes"), SavedTagStore.getTagNames());
    }

    @Test
    public void renameKeepsTheItems() {
        SavedTagStore.createTag("Recipes");
        SavedTagStore.setTagsFor("t3_a", Collections.singletonList("Recipes"));
        SavedTagStore.renameTag("Recipes", "Cooking");

        assertEquals(Collections.singletonList("Cooking"), SavedTagStore.getTagNames());
        assertEquals(
                Collections.singleton("t3_a"), SavedTagStore.fullnamesIn("Cooking"));
    }

    @Test
    public void deleteRemovesTheTagAndNothingElse() {
        SavedTagStore.createTag("Recipes");
        SavedTagStore.createTag("To try");
        SavedTagStore.setTagsFor("t3_a", Arrays.asList("Recipes", "To try"));

        SavedTagStore.deleteTag("Recipes");

        assertEquals(Collections.singletonList("To try"), SavedTagStore.getTagNames());
        // The item keeps its other tag; deleting a tag never touches what is saved.
        assertEquals(Collections.singletonList("To try"), SavedTagStore.tagsFor("t3_a"));
    }

    // ---------------------------------------------------------------------
    // Membership
    // ---------------------------------------------------------------------

    @Test
    public void anItemCanCarrySeveralTags() {
        SavedTagStore.createTag("Recipes");
        SavedTagStore.createTag("To try");
        SavedTagStore.setTagsFor("t3_a", Arrays.asList("Recipes", "To try"));

        assertEquals(Arrays.asList("Recipes", "To try"), SavedTagStore.tagsFor("t3_a"));
    }

    @Test
    public void postsAndCommentsShareOneStore() {
        SavedTagStore.createTag("Recipes");
        SavedTagStore.setTagsFor("t3_a", Collections.singletonList("Recipes"));
        SavedTagStore.setTagsFor("t1_b", Collections.singletonList("Recipes"));

        assertEquals(
                new HashSet<>(Arrays.asList("t3_a", "t1_b")),
                SavedTagStore.fullnamesIn("Recipes"));
    }

    @Test
    public void settingAnEmptySetUntagsTheItem() {
        SavedTagStore.createTag("Recipes");
        SavedTagStore.setTagsFor("t3_a", Collections.singletonList("Recipes"));

        assertTrue(SavedTagStore.setTagsFor("t3_a", Collections.emptyList()));

        assertTrue(SavedTagStore.tagsFor("t3_a").isEmpty());
        // The tag survives losing its last item.
        assertEquals(Collections.singletonList("Recipes"), SavedTagStore.getTagNames());
    }

    @Test
    public void settingTheSameTagsAgainReportsNoChange() {
        SavedTagStore.createTag("Recipes");
        SavedTagStore.setTagsFor("t3_a", Collections.singletonList("Recipes"));
        assertFalse(SavedTagStore.setTagsFor("t3_a", Collections.singletonList("Recipes")));
    }

    @Test
    public void membershipMatchesTagNamesCaseInsensitively() {
        SavedTagStore.createTag("Recipes");
        SavedTagStore.setTagsFor("t3_a", Collections.singletonList("RECIPES"));
        assertEquals(Collections.singleton("t3_a"), SavedTagStore.fullnamesIn("recipes"));
    }

    // ---------------------------------------------------------------------
    // Pruning
    // ---------------------------------------------------------------------

    @Test
    public void pruningDropsItemsNoLongerSaved() {
        SavedTagStore.createTag("Recipes");
        SavedTagStore.setTagsFor("t3_a", Collections.singletonList("Recipes"));
        SavedTagStore.setTagsFor("t3_gone", Collections.singletonList("Recipes"));

        assertTrue(SavedTagStore.pruneTo(new HashSet<>(Collections.singletonList("t3_a"))));

        assertEquals(Collections.singleton("t3_a"), SavedTagStore.fullnamesIn("Recipes"));
    }

    @Test
    public void pruningNeverDeletesTheTagItself() {
        SavedTagStore.createTag("Recipes");
        SavedTagStore.setTagsFor("t3_a", Collections.singletonList("Recipes"));

        SavedTagStore.pruneTo(Collections.emptySet());

        assertEquals(Collections.singletonList("Recipes"), SavedTagStore.getTagNames());
        assertTrue(SavedTagStore.fullnamesIn("Recipes").isEmpty());
    }

    @Test
    public void pruningNothingReportsNoChange() {
        SavedTagStore.createTag("Recipes");
        SavedTagStore.setTagsFor("t3_a", Collections.singletonList("Recipes"));
        assertFalse(SavedTagStore.pruneTo(new HashSet<>(Collections.singletonList("t3_a"))));
    }

    // ---------------------------------------------------------------------
    // Accounts and persistence
    // ---------------------------------------------------------------------

    @Test
    public void tagsSurviveAReload() {
        SavedTagStore.createTag("Recipes");
        SavedTagStore.setTagsFor("t3_a", Collections.singletonList("Recipes"));

        forceReload();

        assertEquals(Collections.singletonList("Recipes"), SavedTagStore.getTagNames());
        assertEquals(Collections.singleton("t3_a"), SavedTagStore.fullnamesIn("Recipes"));
    }

    @Test
    public void oneAccountNeverSeesAnothersTags() {
        SavedTagStore.createTag("Recipes");
        SavedTagStore.setTagsFor("t3_a", Collections.singletonList("Recipes"));

        Authentication.name = "someone_else";
        assertTrue(SavedTagStore.getTagNames().isEmpty());
        SavedTagStore.createTag("Woodworking");

        Authentication.name = "tester";
        assertEquals(Collections.singletonList("Recipes"), SavedTagStore.getTagNames());
        assertEquals(Collections.singleton("t3_a"), SavedTagStore.fullnamesIn("Recipes"));
    }
}
