package me.edgan.redditslide

import android.content.Context
import android.content.SharedPreferences
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import java.util.Locale
import net.dean.jraw.paginators.Sorting
import me.edgan.redditslide.ui.settings.SettingsThemeFragment
import me.edgan.redditslide.util.LogUtil
import me.edgan.redditslide.util.PrefUtil
import me.edgan.redditslide.util.SortingUtil
import me.edgan.redditslide.util.StringUtil

/**
 * The account's [Megareddit]s, and the `/mega/<name>` key that stands for one wherever a subreddit
 * name would otherwise go: a main-screen tab, a feed's sort setting, its offline cache.
 *
 * One `SharedPreferences` entry per account, keyed by [Authentication.nameOrEmpty] the same way
 * [SavedTagStore] keys its blob. The value is a JSON array:
 * ```
 * [{"name": "CuteAnimals", "tags": ["cat", "dog"], "negativeTags": ["cathol"],
 *   "positive": ["aww"], "negative": ["catalog", "hotdog"]}]
 * ```
 *
 * The feed loader reads a definition on its background thread while the overflow menu can be
 * writing one on the main thread, so everything that touches the list is synchronized.
 */
object Megareddits {

    /**
     * Prefix of a Megareddit's key. A slash cannot appear in a subreddit name, and unlike a
     * multireddit's `/m/` this is never mapped to an API path: the loader turns it into r/all plus a
     * filter.
     */
    const val KEY_PREFIX = "/mega/"

    private const val PREFS = "MEGAREDDITS"

    /** Prefix of the per-Megareddit "sorted by every sort at once" flag, in the settings store. */
    private const val SORT_ALL = "megaSortAll"

    /**
     * The sorts [isSortAll] walks, in order. r/all under one sort holds few posts of any one
     * Megareddit -- for tags "cat" and "dog", about one in 36 -- and each sort surfaces a different
     * slice of the listing, so walking all five is what makes the feed worth scrolling. Duplicates
     * across them are dropped where the pages are merged.
     */
    @JvmField
    val ALL_SORTS: List<Sorting> =
        listOf(Sorting.HOT, Sorting.NEW, Sorting.RISING, Sorting.TOP, Sorting.CONTROVERSIAL)

    /** Shared across threads; [ObjectMapper] is thread-safe once configured. */
    private val mapper = ObjectMapper()

    /** The same rule reddit applies to a multireddit's name. */
    private val NAME = Regex("^[A-Za-z0-9][A-Za-z0-9_]{2,20}$")

    /** A tag or a subreddit name: what can appear in a subreddit name, and at least two of it. */
    private val TERM = Regex("^[A-Za-z0-9_]{2,21}$")

    /** The account [megareddits] was loaded for, so an account switch reloads. */
    private var loadedFor: String? = null

    private var megareddits: List<Megareddit> = emptyList()

    /**
     * Whether this Megareddit draws from every sort at once, which is what a Megareddit does unless
     * the user has picked one sort for it. Stored per key beside the ordinary sort settings.
     */
    @JvmStatic
    fun isSortAll(key: String): Boolean {
        val prefs = SettingValues.prefs ?: return true
        return prefs.getBoolean(SORT_ALL + key.lowercase(Locale.ENGLISH), true)
    }

    /** Records the "All" choice, or its replacement by one of the ordinary sorts. */
    @JvmStatic
    fun setSortAll(key: String, all: Boolean) {
        val prefs = SettingValues.prefs ?: return
        prefs.edit().putBoolean(SORT_ALL + key.lowercase(Locale.ENGLISH), all).apply()
    }

    /** Whether [s] is a Megareddit's key rather than a subreddit, multireddit or domain. */
    @JvmStatic
    fun isKey(s: String?): Boolean = s != null && s.lowercase(Locale.ENGLISH).startsWith(KEY_PREFIX)

    /** The key for the Megareddit called [name]. Lowercased, as the main tab list is. */
    @JvmStatic fun keyFor(name: String): String = KEY_PREFIX + name.lowercase(Locale.ENGLISH)

    /** Whether [name] is usable as a Megareddit's title. */
    @JvmStatic fun isValidName(name: String): Boolean = NAME.matches(name)

    /** Whether [term] is usable as a tag or, once normalized, a subreddit name. */
    @JvmStatic fun isValidTerm(term: String): Boolean = TERM.matches(term)

    /** `"/r/Aww "` and `"r/aww"` both become `"aww"`. */
    @JvmStatic
    fun normalizeSubreddit(input: String): String {
        var s = input.trim().removePrefix("/")
        if (s.regionMatches(0, "r/", 0, 2, ignoreCase = true)) {
            s = s.substring(2)
        }
        return s.lowercase(Locale.ENGLISH)
    }

    private fun prefs(): SharedPreferences =
        Reddit.getAppContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** Parse this account's blob into [megareddits], unless it is already in memory. */
    private fun ensureLoaded() {
        val account = Authentication.nameOrEmpty()
        if (account == loadedFor) {
            return
        }
        megareddits =
            try {
                parse(prefs().getString(account, null))
            } catch (e: Exception) {
                // A corrupt blob is not worth losing the session over; start this account empty.
                LogUtil.e(e, "Megareddits.ensureLoaded failed")
                emptyList()
            }
        loadedFor = account
    }

    private fun parse(blob: String?): List<Megareddit> {
        if (blob.isNullOrEmpty()) {
            return emptyList()
        }
        val root = mapper.readTree(blob)
        if (root == null || !root.isArray) {
            return emptyList()
        }
        return parse(root)
    }

    /** The Megareddits in an array node, skipping anything without a name. */
    private fun parse(root: JsonNode): List<Megareddit> {
        return root
            .filter { it.path("name").asText("").isNotEmpty() }
            .map {
                Megareddit(
                    it.path("name").asText(""),
                    strings(it.path("tags")),
                    strings(it.path("negativeTags")),
                    strings(it.path("positive")),
                    strings(it.path("negative")),
                )
            }
    }

    private fun strings(array: JsonNode): List<String> =
        array.map { it.asText("") }.filter { it.isNotEmpty() }

    /** Queue a write of the in-memory list for the account it belongs to. */
    private fun persist() {
        val account = loadedFor ?: Authentication.nameOrEmpty()
        val root = mapper.createArrayNode()
        megareddits.forEach { write(it, root.addObject()) }
        prefs().edit().putString(account, root.toString()).apply()
    }

    private fun write(m: Megareddit, node: ObjectNode): ObjectNode {
        node.put("name", m.name)
        node.putArray("tags").also { array -> m.positiveTags.forEach { array.add(it) } }
        node.putArray("negativeTags").also { array -> m.negativeTags.forEach { array.add(it) } }
        node.putArray("positive").also { array -> m.positiveSubreddits.forEach { array.add(it) } }
        node.putArray("negative").also { array -> m.negativeSubreddits.forEach { array.add(it) } }
        return node
    }

    /**
     * One Megareddit as the JSON an export writes: the same shape the store keeps, so an export is
     * readable by hand and an import needs no translation.
     */
    @JvmStatic
    @Synchronized
    fun toJson(megareddit: Megareddit): String =
        mapper.writerWithDefaultPrettyPrinter().writeValueAsString(write(megareddit, mapper.createObjectNode()))

    /** Every Megareddit this account has, as a JSON array of the same objects. */
    @JvmStatic
    @Synchronized
    fun toJsonAll(): String {
        ensureLoaded()
        val root = mapper.createArrayNode()
        megareddits.forEach { write(it, root.addObject()) }
        return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(root)
    }

    /**
     * Stores what an export wrote, taking either one object or an array of them, and returns how
     * many were read. A Megareddit whose name is already in use is replaced by the imported one:
     * an import is a deliberate act, and the usual source is an export of the same list.
     *
     * Returns -1 when the file is not a Megareddit export at all.
     */
    @JvmStatic
    @Synchronized
    fun importJson(blob: String): Int {
        val parsed =
            try {
                val root = mapper.readTree(blob)
                when {
                    root == null -> null
                    root.isArray -> parse(root)
                    root.isObject -> parse(mapper.createArrayNode().add(root))
                    else -> null
                }
            } catch (e: Exception) {
                LogUtil.e(e, "Megareddits.importJson failed")
                null
            } ?: return -1

        parsed.forEach { save(it, null) }
        return parsed.size
    }

    /** Every Megareddit this account has, sorted by name. */
    @JvmStatic
    @Synchronized
    fun getAll(): List<Megareddit> {
        ensureLoaded()
        return megareddits
    }

    /** The Megareddit a key or a bare name refers to, or null when there is none. */
    @JvmStatic
    @Synchronized
    fun get(keyOrName: String): Megareddit? {
        ensureLoaded()
        return find(if (isKey(keyOrName)) keyOrName.substring(KEY_PREFIX.length) else keyOrName)
    }

    /**
     * The Megareddit [listing] is the key of, or null when [listing] is a subreddit, multireddit or
     * domain, or names a Megareddit that no longer exists.
     */
    @JvmStatic
    @Synchronized
    fun forKey(listing: String?): Megareddit? =
        if (listing != null && isKey(listing)) get(listing) else null

    private fun find(name: String): Megareddit? =
        megareddits.firstOrNull { it.name.equals(name, ignoreCase = true) }

    /**
     * Whether another Megareddit already has [name]. [oldName] is the one being edited, which may keep
     * its own name or change only its case.
     */
    @JvmStatic
    @Synchronized
    fun isNameTaken(name: String, oldName: String?): Boolean {
        ensureLoaded()
        return find(name) != null && (oldName == null || !oldName.equals(name, ignoreCase = true))
    }

    /**
     * Stores [megareddit], replacing the one called [oldName] when editing. A rename moves its
     * main-screen tab along with it.
     */
    @JvmStatic
    @Synchronized
    fun save(megareddit: Megareddit, oldName: String?) {
        ensureLoaded()
        megareddits =
            (megareddits.filterNot {
                    it.name.equals(megareddit.name, ignoreCase = true) ||
                        (oldName != null && it.name.equals(oldName, ignoreCase = true))
                } + megareddit)
                .sortedWith { a, b -> a.name.compareTo(b.name, ignoreCase = true) }
        persist()
        if (oldName != null && keyFor(oldName) != megareddit.key()) {
            rewriteTabs(keyFor(oldName), megareddit.key())
            moveSettings(keyFor(oldName), megareddit.key())
        }
    }

    /** Removes the Megareddit called [name], and its main-screen tab if it has one. */
    @JvmStatic
    @Synchronized
    fun delete(name: String) {
        ensureLoaded()
        val existing = find(name) ?: return
        megareddits = megareddits - existing
        persist()
        rewriteTabs(existing.key(), null)
        moveSettings(existing.key(), null)
    }

    /**
     * Adds [subreddit] to the positive subreddits of the Megareddit [key] refers to, taking it off
     * the negative list. Returns false when there is no such Megareddit.
     */
    @JvmStatic
    @Synchronized
    fun addPositive(key: String, subreddit: String): Boolean {
        val existing = get(key) ?: return false
        save(existing.withPositive(subreddit), existing.name)
        return true
    }

    /**
     * Adds [subreddit] to the negative subreddits of the Megareddit [key] refers to, taking it off
     * the positive list. Returns false when there is no such Megareddit.
     */
    @JvmStatic
    @Synchronized
    fun addNegative(key: String, subreddit: String): Boolean {
        val existing = get(key) ?: return false
        save(existing.withNegative(subreddit), existing.name)
        return true
    }

    /**
     * Carries a Megareddit's sort across a rename, and clears it on a delete. These are stored per
     * key, so without this a rename silently drops back to the default sort, and a new Megareddit
     * that happens to reuse a deleted one's name inherits its settings.
     */
    private fun moveSettings(oldKey: String, newKey: String?) {
        SortingUtil.sorting.remove(oldKey)?.let { if (newKey != null) SortingUtil.sorting[newKey] = it }
        SortingUtil.times.remove(oldKey)?.let { if (newKey != null) SortingUtil.times[newKey] = it }

        val prefs = SettingValues.prefs ?: return
        val editor = prefs.edit()
        for (prefix in listOf(SORT_ALL, "defaultSort", "defaultTime")) {
            val from = prefix + oldKey
            if (!prefs.contains(from)) {
                continue
            }
            if (newKey != null) {
                when (val value = prefs.all[from]) {
                    is Boolean -> editor.putBoolean(prefix + newKey, value)
                    is String -> editor.putString(prefix + newKey, value)
                    else -> Unit
                }
            }
            editor.remove(from)
        }
        editor.apply()
    }

    /**
     * Points the account's main-screen tab list, and its pinned list, at [newKey] instead of
     * [oldKey], or drops [oldKey] when [newKey] is null. Otherwise a renamed or deleted Megareddit
     * leaves a tab behind that loads nothing.
     */
    private fun rewriteTabs(oldKey: String, newKey: String?) {
        val subs = rewriteList(UserSubscriptions.subscriptions, oldKey, newKey)
        val pins = rewriteList(UserSubscriptions.pinned, oldKey, newKey)
        if (pins) {
            UserSubscriptions.pins = null
        }
        if (subs || pins) {
            // MainActivity rebuilds its tabs from the stored list when it next resumes.
            SettingsThemeFragment.changed = true
        }
    }

    private fun rewriteList(prefs: SharedPreferences?, oldKey: String, newKey: String?): Boolean {
        if (prefs == null) {
            return false
        }
        val account = Authentication.nameOrEmpty()
        val stored = PrefUtil.getString(prefs, account, "")
        if (stored.isEmpty()) {
            return false
        }
        val entries = ArrayList<String>()
        var changed = false
        for (entry in stored.split(",")) {
            if (entry.equals(oldKey, ignoreCase = true)) {
                changed = true
                if (newKey != null && newKey !in entries) {
                    entries.add(newKey)
                }
            } else {
                entries.add(entry)
            }
        }
        if (changed) {
            prefs.edit().putString(account, StringUtil.arrayToString(entries)).apply()
        }
        return changed
    }
}
