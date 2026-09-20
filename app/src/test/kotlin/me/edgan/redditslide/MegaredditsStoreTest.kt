package me.edgan.redditslide

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import me.edgan.redditslide.test.TestUtils
import me.edgan.redditslide.ui.settings.SettingsThemeFragment
import net.dean.jraw.paginators.Sorting
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Where Megareddits live. Reddit stores none of this, so a bug here loses the user's Megareddits
 * outright, and a tab pointing at one that has been renamed or deleted is a tab that loads nothing.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class MegaredditsStoreTest {

    private lateinit var context: Context
    private var prefsWas: android.content.SharedPreferences? = null

    @Before
    fun setUp() {
        TestUtils.seedRedditApplication()
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("MEGAREDDITS", Context.MODE_PRIVATE).edit().clear().commit()

        UserSubscriptions.subscriptions =
            context.getSharedPreferences("megareddits-test-subs", Context.MODE_PRIVATE)
        UserSubscriptions.pinned =
            context.getSharedPreferences("megareddits-test-pinned", Context.MODE_PRIVATE)
        UserSubscriptions.subscriptions.edit().clear().commit()
        UserSubscriptions.pinned.edit().clear().commit()
        UserSubscriptions.pins = null

        prefsWas = SettingValues.prefs
        SettingValues.prefs =
            context.getSharedPreferences("megareddits-test-settings", Context.MODE_PRIVATE)
        SettingValues.prefs.edit().clear().commit()

        // Static, and shared with every other test in this Robolectric sandbox.
        SettingsThemeFragment.changed = false

        Authentication.name = "tester"
        forceReload()
    }

    @After
    fun tearDown() {
        prefsWas?.let { SettingValues.prefs = it }
        Authentication.name = null
        SettingsThemeFragment.changed = false
        TestUtils.clearRedditApplication()
    }

    /** Make the store forget its in-memory list by bouncing the account it was loaded for. */
    private fun forceReload() {
        val account = Authentication.name
        Authentication.name = "__reload__"
        Megareddits.getAll()
        Authentication.name = account
        Megareddits.getAll()
    }

    private fun cuteAnimals() =
        Megareddit("CuteAnimals", listOf("cat", "dog"), listOf("cathol"), listOf("aww"), listOf("catalog"))

    private fun tabs(): String =
        UserSubscriptions.subscriptions.getString(Authentication.nameOrEmpty(), "") ?: ""

    // --- the stored definition -----------------------------------------------------------------

    @Test
    fun aSavedMegaredditSurvivesAReload() {
        Megareddits.save(cuteAnimals(), null)
        forceReload()

        val stored = Megareddits.get("CuteAnimals")
        assertTrue(stored != null)
        assertEquals("CuteAnimals", stored!!.name)
        assertEquals(listOf("cat", "dog"), stored.positiveTags)
        assertEquals(listOf("cathol"), stored.negativeTags)
        assertEquals(listOf("aww"), stored.positiveSubreddits)
        assertEquals(listOf("catalog"), stored.negativeSubreddits)
    }

    @Test
    fun aMegaredditIsFoundByKeyOrByNameInAnyCase() {
        Megareddits.save(cuteAnimals(), null)

        assertTrue(Megareddits.get("cuteanimals") != null)
        assertTrue(Megareddits.forKey("/mega/cuteanimals") != null)
        // A subreddit called the same thing is not this Megareddit.
        assertNull(Megareddits.forKey("cuteanimals"))
        assertNull(Megareddits.forKey(null))
        assertNull(Megareddits.forKey("/mega/gone"))
    }

    @Test
    fun oneAccountCannotSeeAnothers() {
        Megareddits.save(cuteAnimals(), null)

        Authentication.name = "someone_else"
        assertTrue(Megareddits.getAll().isEmpty())
        Megareddits.save(Megareddit("Theirs", listOf("bird"), emptyList(), emptyList(), emptyList()), null)
        assertEquals(listOf("Theirs"), Megareddits.getAll().map { it.name })

        Authentication.name = "tester"
        assertEquals(listOf("CuteAnimals"), Megareddits.getAll().map { it.name })
    }

    @Test
    fun theListIsSortedByNameWhateverOrderItWasBuiltIn() {
        Megareddits.save(Megareddit("zebra", listOf("zebra"), emptyList(), emptyList(), emptyList()), null)
        Megareddits.save(cuteAnimals(), null)
        Megareddits.save(Megareddit("Birds", listOf("bird"), emptyList(), emptyList(), emptyList()), null)

        assertEquals(listOf("Birds", "CuteAnimals", "zebra"), Megareddits.getAll().map { it.name })
    }

    @Test
    fun editingReplacesRatherThanDuplicates() {
        Megareddits.save(cuteAnimals(), null)
        Megareddits.save(
            Megareddit("CuteAnimals", listOf("cat"), emptyList(), emptyList(), emptyList()),
            "CuteAnimals",
        )
        forceReload()

        assertEquals(1, Megareddits.getAll().size)
        assertEquals(listOf("cat"), Megareddits.get("CuteAnimals")!!.positiveTags)
    }

    @Test
    fun aCorruptBlobStartsTheAccountEmptyRatherThanThrowing() {
        context
            .getSharedPreferences("MEGAREDDITS", Context.MODE_PRIVATE)
            .edit()
            .putString("tester", "{not json")
            .commit()
        forceReload()

        assertTrue(Megareddits.getAll().isEmpty())
    }

    // --- export and import ----------------------------------------------------------------------

    @Test
    fun anExportImportsBackAsItself() {
        Megareddits.save(cuteAnimals(), null)
        val json = Megareddits.toJson(Megareddits.get("CuteAnimals")!!)

        // The account it lands in is a different one, which is what carrying a file across does.
        Authentication.name = "someone_else"
        assertEquals(1, Megareddits.importJson(json))

        val imported = Megareddits.get("CuteAnimals")!!
        assertEquals(listOf("cat", "dog"), imported.positiveTags)
        assertEquals(listOf("cathol"), imported.negativeTags)
        assertEquals(listOf("aww"), imported.positiveSubreddits)
        assertEquals(listOf("catalog"), imported.negativeSubreddits)
    }

    @Test
    fun exportAllCarriesEveryMegareddit() {
        Megareddits.save(cuteAnimals(), null)
        Megareddits.save(Megareddit("Birds", listOf("bird"), emptyList(), emptyList(), emptyList()), null)
        val json = Megareddits.toJsonAll()

        Authentication.name = "someone_else"
        assertEquals(2, Megareddits.importJson(json))
        assertEquals(listOf("Birds", "CuteAnimals"), Megareddits.getAll().map { it.name })
    }

    @Test
    fun anImportReplacesAMegaredditOfTheSameName() {
        Megareddits.save(cuteAnimals(), null)
        val json =
            Megareddits.toJson(
                Megareddit("CuteAnimals", listOf("puppy"), emptyList(), emptyList(), emptyList())
            )

        assertEquals(1, Megareddits.importJson(json))
        assertEquals(1, Megareddits.getAll().size)
        assertEquals(listOf("puppy"), Megareddits.get("CuteAnimals")!!.positiveTags)
    }

    @Test
    fun aFileThatIsNotAnExportIsReportedRatherThanStored() {
        assertEquals(-1, Megareddits.importJson("{not json"))
        assertEquals(-1, Megareddits.importJson("\"a string\""))
        assertTrue(Megareddits.getAll().isEmpty())

        // Valid JSON of the right shape but carrying nothing is not a failure, just nothing.
        assertEquals(0, Megareddits.importJson("[]"))
    }

    // --- the sort ------------------------------------------------------------------------------

    @Test
    fun aMegaredditDrawsFromEverySortUntilOneIsPicked() {
        val key = Megareddits.keyFor("CuteAnimals")
        // The default, and the reason a Megareddit feed is worth scrolling: one sort of r/all holds
        // too few of its posts.
        assertTrue(Megareddits.isSortAll(key))

        Megareddits.setSortAll(key, false)
        assertFalse(Megareddits.isSortAll(key))

        Megareddits.setSortAll(key, true)
        assertTrue(Megareddits.isSortAll(key))
    }

    @Test
    fun theSortIsPerMegareddit() {
        Megareddits.setSortAll(Megareddits.keyFor("CuteAnimals"), false)
        assertTrue(Megareddits.isSortAll(Megareddits.keyFor("Gaming")))
        // The key is stored lowercased, as the tab list is.
        assertFalse(Megareddits.isSortAll("/MEGA/CUTEANIMALS"))
    }

    @Test
    fun everySortIsWalkedInOrder() {
        assertEquals(
            listOf(
                Sorting.HOT,
                Sorting.NEW,
                Sorting.RISING,
                Sorting.TOP,
                Sorting.CONTROVERSIAL,
            ),
            Megareddits.ALL_SORTS,
        )
    }

    // --- the tab that points at it -------------------------------------------------------------

    @Test
    fun renamingMovesTheTab() {
        Megareddits.save(cuteAnimals(), null)
        UserSubscriptions.subscriptions
            .edit()
            .putString("tester", "frontpage,/mega/cuteanimals,pics")
            .commit()

        Megareddits.save(
            Megareddit("Adorable", listOf("cat", "dog"), emptyList(), listOf("aww"), listOf("catalog")),
            "CuteAnimals",
        )

        assertEquals("frontpage,/mega/adorable,pics", tabs())
        assertTrue(SettingsThemeFragment.changed)
    }

    @Test
    fun deletingTakesTheTabWithIt() {
        Megareddits.save(cuteAnimals(), null)
        UserSubscriptions.subscriptions
            .edit()
            .putString("tester", "frontpage,/mega/cuteanimals,pics")
            .commit()
        UserSubscriptions.pinned.edit().putString("tester", "/mega/cuteanimals").commit()

        Megareddits.delete("CuteAnimals")

        assertEquals("frontpage,pics", tabs())
        assertEquals("", UserSubscriptions.pinned.getString("tester", ""))
        assertTrue(Megareddits.getAll().isEmpty())
    }

    @Test
    fun aMegaredditWithNoTabLeavesTheTabListAlone() {
        Megareddits.save(cuteAnimals(), null)
        UserSubscriptions.subscriptions.edit().putString("tester", "frontpage,pics").commit()

        Megareddits.delete("CuteAnimals")

        assertEquals("frontpage,pics", tabs())
        assertFalse(SettingsThemeFragment.changed)
    }

    // --- the overflow menu's two actions -------------------------------------------------------

    @Test
    fun addingANegativeTakesItOffThePositiveList() {
        Megareddits.save(
            Megareddit("CuteAnimals", listOf("cat"), emptyList(), listOf("aww"), emptyList()),
            null,
        )

        assertTrue(Megareddits.addNegative("/mega/cuteanimals", "Aww"))
        forceReload()

        val stored = Megareddits.get("CuteAnimals")!!
        assertEquals(emptyList<String>(), stored.positiveSubreddits)
        assertEquals(listOf("aww"), stored.negativeSubreddits)
        assertFalse(stored.matches("aww"))
    }

    @Test
    fun addingAPositiveTakesItOffTheNegativeList() {
        Megareddits.save(
            Megareddit("CuteAnimals", listOf("cat"), emptyList(), emptyList(), listOf("catalog")),
            null,
        )

        assertTrue(Megareddits.addPositive("/mega/cuteanimals", "Catalog"))
        forceReload()

        val stored = Megareddits.get("CuteAnimals")!!
        assertEquals(listOf("catalog"), stored.positiveSubreddits)
        assertEquals(emptyList<String>(), stored.negativeSubreddits)
        assertTrue(stored.matches("catalog"))
    }

    @Test
    fun anActionOnAMegaredditThatIsGoneDoesNothing() {
        assertFalse(Megareddits.addNegative("/mega/gone", "cats"))
        assertFalse(Megareddits.addPositive("/mega/gone", "cats"))
    }

    // --- what the editor validates -------------------------------------------------------------

    @Test
    fun aNameIsTakenOnlyByADifferentMegareddit() {
        Megareddits.save(cuteAnimals(), null)

        assertTrue(Megareddits.isNameTaken("cuteanimals", null))
        // Editing this one and keeping its name, or only changing its case.
        assertFalse(Megareddits.isNameTaken("CuteAnimals", "CuteAnimals"))
        assertFalse(Megareddits.isNameTaken("CUTEANIMALS", "CuteAnimals"))
        assertFalse(Megareddits.isNameTaken("Birds", null))
    }

    @Test
    fun nameRulesMatchTheOnesRedditAppliesToAMultireddit() {
        assertTrue(Megareddits.isValidName("CuteAnimals"))
        assertTrue(Megareddits.isValidName("a_b"))
        assertFalse(Megareddits.isValidName("ab"))
        assertFalse(Megareddits.isValidName("_leading"))
        assertFalse(Megareddits.isValidName("has space"))
        // A name goes into the comma-separated tab list, so a comma would split the entry in two.
        assertFalse(Megareddits.isValidName("a,b"))
    }

    @Test
    fun termRulesCoverATagAndASubredditName() {
        assertTrue(Megareddits.isValidTerm("cat"))
        assertTrue(Megareddits.isValidTerm("ai"))
        assertFalse(Megareddits.isValidTerm("a"))
        assertFalse(Megareddits.isValidTerm("two words"))
        assertFalse(Megareddits.isValidTerm("/r/cats"))
    }

    @Test
    fun aTypedSubredditIsNormalizedBeforeItIsChecked() {
        assertEquals("aww", Megareddits.normalizeSubreddit(" /r/Aww "))
        assertEquals("aww", Megareddits.normalizeSubreddit("r/AWW"))
        assertEquals("aww", Megareddits.normalizeSubreddit("aww"))
    }
}
