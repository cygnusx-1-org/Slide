package me.edgan.redditslide.Activities

import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import android.os.Bundle
import android.text.Spannable
import android.view.Gravity
import android.view.Menu
import android.view.MenuItem
import android.view.ViewGroup
import android.view.animation.LinearInterpolator
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.PopupMenu
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import me.edgan.redditslide.Fragments.SubmissionsView
import me.edgan.redditslide.Megareddit
import me.edgan.redditslide.Megareddits
import me.edgan.redditslide.R
import me.edgan.redditslide.SettingValues
import me.edgan.redditslide.Visuals.ColorPreferences
import me.edgan.redditslide.Visuals.Palette
import me.edgan.redditslide.util.DialogUtil
import me.edgan.redditslide.util.LayoutUtils
import me.edgan.redditslide.util.LogUtil
import me.edgan.redditslide.util.MiscUtil
import me.edgan.redditslide.util.SortingUtil
import me.edgan.redditslide.util.StorageUtil
import net.dean.jraw.paginators.Sorting
import net.dean.jraw.paginators.TimePeriod

private const val JSON_MIME = "application/json"

/**
 * The account's Megareddits, one tab each. A page is the same [SubmissionsView] a main-screen tab
 * uses, loading the Megareddit's key, so its feed, sort and cache are shared with the tab.
 */
class MegaredditOverview : BaseActivityAnim() {

    private lateinit var pager: ViewPager2
    private lateinit var tabs: TabLayout
    private lateinit var adapter: MegaredditPagerAdapter

    /**
     * Bumped every time the pages are rebuilt, and carried in the adapter's item ids: a page whose
     * id the adapter has not seen before is dropped and built again, which is how an edited
     * Megareddit is made to re-run its filter rather than show what the old one kept.
     */
    private var pageGeneration = 0

    /** What the tabs were built from, so a return to this screen only rebuilds them on a change. */
    private var shown: List<Megareddit> = emptyList()

    /**
     * What [shown] held, spelled out. The editor, the subreddit count screen and an import can all
     * come back having changed nothing, and rebuilding then would throw away loaded feeds and the
     * scroll positions they are sitting at for the very same posts -- so what the pages were built
     * from is compared against what the store now holds, rather than trusting that a screen which
     * *can* edit a Megareddit *did*.
     */
    private var shownSignature: String = ""

    /** The JSON an export is waiting to write, until the picker says where to put it. */
    private var pendingExport: String? = null

    /**
     * Posts on screen per Megareddit key, as each page reports them. A Megareddit is r/all filtered
     * down, so this is the number that says how much of it survived the filter, and it climbs as
     * the feed pages. Empty until a page has loaded; a tab with nothing counted yet shows no
     * number rather than a zero it has not measured.
     */
    private val counts = HashMap<String, Int>()

    override fun onCreate(savedInstanceState: Bundle?) {
        overrideSwipeFromAnywhere()
        super.onCreate(savedInstanceState)

        applyColorTheme("")
        setContentView(R.layout.activity_megareddits)
        MiscUtil.setupOldSwipeModeBackground(this, window.decorView)

        setupAppBar(R.id.toolbar, R.string.title_megareddits, true, false)

        requireViewById<ViewGroup>(R.id.header).setBackgroundColor(Palette.getDefaultColor())
        tabs = requireViewById(R.id.sliding_tabs)
        tabs.tabMode = TabLayout.MODE_SCROLLABLE
        pager = requireViewById(R.id.content_view)
        pager.offscreenPageLimit = 1
        adapter = MegaredditPagerAdapter()
        pager.adapter = adapter
        // Attached once, for the life of the screen: the pages are rebuilt through the adapter
        // rather than by handing the pager a new one, so the tabs never need re-attaching.
        TabLayoutMediator(tabs, pager) { tab, position -> tab.text = titleFor(position) }.attach()
        requireToolbar().popupTheme = ColorPreferences(this).fontStyle.baseId

        // This layout carries the multireddit screen's subreddit drawer, which has nothing to
        // list here.
        requireViewById<DrawerLayout>(R.id.drawer_layout)
            .setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED, GravityCompat.END)

        pager.registerOnPageChangeCallback(
            object : ViewPager2.OnPageChangeCallback() {
                override fun onPageSelected(position: Int) {
                    requireViewById<ViewGroup>(R.id.header)
                        .animate()
                        .translationY(0f)
                        .setInterpolator(LinearInterpolator())
                        .setDuration(180)
                    colorFor(position)
                    // Rows the overflow menu dropped never went through a feed update, so the page
                    // being settled on is re-counted here rather than trusted to be current.
                    recountCurrentPage()
                }
            }
        )
    }

    override fun onResume() {
        super.onResume()
        val all = Megareddits.getAll()
        val signature = signatureOf(all)
        if (all.isEmpty()) {
            shown = all
            shownSignature = signature
            // Notified rather than detached: the adapter stays on the pager for the life of the
            // screen, and a count that drops without a notify throws.
            rebuildPages()
            showCreateDialog()
            return
        }
        if (signature != shownSignature) {
            val current = pager.currentItem
            shown = all
            shownSignature = signature
            // The pages are about to be rebuilt, so nothing counted against the old ones holds.
            counts.clear()
            rebuildPages()
            // Not animated: the page being landed on is a new one, not one scrolled to.
            pager.setCurrentItem(current.coerceAtMost(all.size - 1), false)
            colorFor(pager.currentItem)
        } else {
            // Same Megareddits; keep the pages, their feeds and their scroll positions as they are.
            shown = all
            recountCurrentPage()
            // The setting that decides whether the number shows at all lives a screen away, so
            // every title is rewritten on the way back rather than only the one that changed.
            refreshTabTitles()
        }
    }

    /**
     * The store as it stands when this screen leaves the foreground, which is what its pages are
     * already showing. The long-press sheet can drop a subreddit from a Megareddit while the
     * screen is up and then fix its own page in place, reporting the new count rather than going
     * through a rebuild; without re-taking the signature here, the next return from anywhere at
     * all would read that edit as news and rebuild every page for something already applied.
     */
    override fun onPause() {
        super.onPause()
        shownSignature = signatureOf(Megareddits.getAll())
    }

    /**
     * Everything a page is built from, as one string: the names in tab order, and every term each
     * Megareddit filters on. A Megareddit carries no value equality of its own, and the name alone
     * is not enough -- an edit that only adds a tag leaves the names identical while changing what
     * the feed should hold.
     */
    private fun signatureOf(list: List<Megareddit>): String =
        list.joinToString("\n") {
            it.name +
                "\u0000" +
                it.positiveTags +
                "\u0000" +
                it.negativeTags +
                "\u0000" +
                it.positiveSubreddits +
                "\u0000" +
                it.negativeSubreddits
        }

    /**
     * The posts a page is showing, reported by [SubmissionsView] as its feed loads, pages and
     * refreshes. Only the tab's own text is rewritten: rebuilding the whole TabLayout for a number
     * would drop the scroll position it is sitting at.
     */
    fun onFeedCountChanged(id: String, count: Int) {
        if (counts.put(id, count) == count || !SettingValues.megaredditPostCount) {
            return
        }
        val position = shown.indexOfFirst { it.key().equals(id, ignoreCase = true) }
        if (position >= 0) {
            tabs.getTabAt(position)?.text = titleFor(position)
        }
    }

    private fun refreshTabTitles() {
        for (position in shown.indices) {
            tabs.getTabAt(position)?.text = titleFor(position)
        }
    }

    /** Drops every page and builds it again, so an edited Megareddit re-runs its filter. */
    private fun rebuildPages() {
        pageGeneration++
        adapter.notifyDataSetChanged()
    }

    /**
     * The page on screen. The adapter owns its fragments and hands none of them back, so the one
     * being shown is looked up by the key it was built with rather than held onto.
     */
    private fun currentPage(): SubmissionsView? {
        val key = currentKey() ?: return null
        return supportFragmentManager.fragments.filterIsInstance<SubmissionsView>().firstOrNull {
            // A fragment that has not reached onCreate yet has no key, hence the null-safe side.
            key.equals(it.id, ignoreCase = true)
        }
    }

    /** Asks the page on screen what it is showing, for the changes no feed update reports. */
    private fun recountCurrentPage() {
        val page = currentPage() ?: return
        val loaded = page.posts?.posts ?: return
        onFeedCountChanged(page.id, loaded.size)
    }

    private fun titleFor(position: Int): CharSequence {
        val megareddit = shown[position]
        val count = counts[megareddit.key()]
        // Off by default, and a tab whose feed has not loaded yet has nothing to show.
        return if (SettingValues.megaredditPostCount && count != null) {
            megareddit.name + "(" + count + ")"
        } else {
            megareddit.name
        }
    }

    private fun colorFor(position: Int) {
        val key = shown.getOrNull(position)?.key() ?: return
        requireViewById<ViewGroup>(R.id.header).setBackgroundColor(Palette.getColor(key))
        // Routed through BaseActivity so the system-bar scrims are colored too; a direct
        // Window.setStatusBarColor() no-ops under edge-to-edge enforcement, and this applies
        // alwaysBlackStatusbar internally.
        themeSystemBars(Palette.getDarkerColor(key))
        tabs.setSelectedTabIndicatorColor(ColorPreferences(this).getColor(key))
    }

    private fun showCreateDialog() {
        val dialog =
            AlertDialog.Builder(this)
                .setCancelable(false)
                .setTitle(R.string.megareddit_none_title)
                .setMessage(R.string.megareddit_none_msg)
                .setPositiveButton(R.string.btn_yes) { _, _ -> openEditor(null) }
                // The only way in on a fresh account: with nothing to show, this dialog is what
                // the screen is, and the overflow menu behind it cannot be reached.
                .setNeutralButton(R.string.megareddit_import) { _, _ -> importMegareddits() }
                .setNegativeButton(R.string.btn_no) { _, _ -> finish() }
                .create()
        DialogUtil.matchDialogToCardBackground(this, dialog)
        dialog.show()
    }

    private fun openEditor(name: String?) {
        val i = Intent(this, CreateMegareddit::class.java)
        if (name != null) {
            i.putExtra(CreateMegareddit.EXTRA_MEGAREDDIT, name)
        }
        startActivity(i)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_megareddits, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            android.R.id.home -> onBackPressedDispatcher.onBackPressed()
            R.id.action_edit -> shown.getOrNull(pager.currentItem)?.let { openEditor(it.name) }
            R.id.create -> openEditor(null)
            R.id.action_sort -> currentKey()?.let { openSortPopup(it) }
            R.id.count -> shown.getOrNull(pager.currentItem)?.let { openCount(it) }
            R.id.import_megareddits -> importMegareddits()
            R.id.export ->
                shown.getOrNull(pager.currentItem)?.let {
                    export(Megareddits.toJson(it), "Megareddit-" + it.name + ".json")
                }
            R.id.export_all -> export(Megareddits.toJsonAll(), "Megareddits.json")
            else -> return super.onOptionsItemSelected(item)
        }
        return true
    }

    private fun currentKey(): String? = shown.getOrNull(pager.currentItem)?.key()

    private val exportLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val uri = result.data?.data
            if (result.resultCode != RESULT_OK || uri == null) {
                // Nothing was written, so the JSON waiting for a destination is dropped.
                pendingExport = null
                return@registerForActivityResult
            }
            writeExport(uri)
        }

    private val importLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val uri = result.data?.data
            if (result.resultCode == RESULT_OK && uri != null) {
                readImport(uri)
            }
        }

    /**
     * Writes [json] wherever the picker is pointed. The file is the store's own shape, so an export
     * is readable by hand and an import of it needs no translation.
     */
    private fun export(json: String, fileName: String) {
        pendingExport = json
        val intent =
            Intent(Intent.ACTION_CREATE_DOCUMENT)
                .addCategory(Intent.CATEGORY_OPENABLE)
                .setType(JSON_MIME)
                .putExtra(Intent.EXTRA_TITLE, fileName)
        StorageUtil.getStorageUri(this)?.let {
            intent.putExtra(DocumentsContract.EXTRA_INITIAL_URI, it)
        }
        exportLauncher.launch(intent)
    }

    private fun importMegareddits() {
        val intent =
            Intent(Intent.ACTION_OPEN_DOCUMENT)
                .addCategory(Intent.CATEGORY_OPENABLE)
                // Not the JSON type: plenty of providers hand a .json file over as
                // application/octet-stream, and filtering on it hides the file being looked for.
                .setType("*/*")
        StorageUtil.getStorageUri(this)?.let {
            intent.putExtra(DocumentsContract.EXTRA_INITIAL_URI, it)
        }
        importLauncher.launch(intent)
    }

    private fun writeExport(uri: Uri) {
        val json = pendingExport ?: return
        pendingExport = null
        try {
            // "wt", not the default "w": picking a file that already exists overwrites it in
            // place, and without truncation the tail of the longer old file is left behind and
            // the export is no longer valid JSON.
            val written =
                contentResolver.openOutputStream(uri, "wt").use {
                    it?.write(json.toByteArray())
                    it != null
                }
            snack(
                getString(
                    if (written) R.string.megareddit_exported
                    else R.string.megareddit_export_failed
                )
            )
        } catch (e: Exception) {
            LogUtil.e(e, "MegaredditOverview.writeExport failed")
            snack(getString(R.string.megareddit_export_failed))
        }
    }

    private fun readImport(uri: Uri) {
        val json =
            try {
                contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
            } catch (e: Exception) {
                LogUtil.e(e, "MegaredditOverview.readImport failed")
                null
            }
        val imported = if (json == null) -1 else Megareddits.importJson(json)
        if (imported < 0) {
            snack(getString(R.string.megareddit_import_failed))
            return
        }
        snack(resources.getQuantityString(R.plurals.megareddits_imported, imported, imported))
    }

    private fun snack(text: String) {
        LayoutUtils.showSnackbar(Snackbar.make(pager, text, Snackbar.LENGTH_SHORT))
    }

    /**
     * Opens the count screen, which scans r/all and can add any subreddit it finds to this
     * Megareddit's lists -- so the pages reload on the way back, as they do after the editor, but
     * only if it added one.
     */
    private fun openCount(mega: Megareddit) {
        startActivity(
            Intent(this, MegaredditCount::class.java)
                .putExtra(MegaredditCount.EXTRA_MEGAREDDIT, mega.name)
        )
    }

    private fun refreshCurrent() {
        currentPage()?.forceRefresh()
    }

    /**
     * "All" first, then the ordinary sorts. The entries come from `SortingUtil` so that this menu
     * and the one on a Megareddit's main-screen tab stay the same menu.
     */
    private fun openSortPopup(key: String) {
        val popup = PopupMenu(this, requireViewById(R.id.anchor), Gravity.END)
        val sortings = Megareddits.ALL_SORTS
        val entries: Array<Spannable> = SortingUtil.getMegaredditSortingSpannables(key)
        entries.forEach { popup.menu.add(it) }

        popup.setOnMenuItemClickListener { item ->
            val chosen = entries.indexOfFirst { it == item.title }
            // The sort the menu marks is the one already on; picking it again asks for the posts
            // that are already on screen, and refreshing would fetch them afresh and throw the
            // scroll position away for nothing. Read the current sort before it is overwritten.
            if (chosen == 0) {
                val changed = !Megareddits.isSortAll(key)
                Megareddits.setSortAll(key, true)
                if (changed) {
                    refreshCurrent()
                }
                return@setOnMenuItemClickListener true
            }
            val sorting =
                sortings.getOrNull(chosen - 1) ?: return@setOnMenuItemClickListener true
            val changed =
                Megareddits.isSortAll(key) || SettingValues.getSubmissionSort(key) != sorting
            Megareddits.setSortAll(key, false)
            SortingUtil.setSorting(key, sorting)
            if (sorting == Sorting.TOP || sorting == Sorting.CONTROVERSIAL) {
                openTimePopup(key, changed)
            } else if (changed) {
                refreshCurrent()
            }
            true
        }
        popup.show()
    }

    /**
     * The period the TOP and CONTROVERSIAL sorts run over. [sortChanged] carries whether the sort
     * it belongs to just moved, so that keeping the period still refreshes when the sort did not.
     */
    private fun openTimePopup(key: String, sortChanged: Boolean) {
        val popup = PopupMenu(this, requireViewById(R.id.anchor), Gravity.END)
        val base: Array<Spannable> = SortingUtil.getSortingTimesSpannables(key)
        val times =
            arrayOf(
                TimePeriod.HOUR,
                TimePeriod.DAY,
                TimePeriod.WEEK,
                TimePeriod.MONTH,
                TimePeriod.YEAR,
                TimePeriod.ALL,
            )
        base.forEach { popup.menu.add(it) }
        popup.setOnMenuItemClickListener { item ->
            val time = times.getOrNull(base.indexOfFirst { it == item.title })
            if (time != null) {
                val changed = sortChanged || SettingValues.getSubmissionTimePeriod(key) != time
                SortingUtil.setTime(key, time)
                if (changed) {
                    refreshCurrent()
                }
            }
            true
        }
        popup.show()
    }

    /** One page per Megareddit, in [shown]'s order, with [pageGeneration] baked into the ids. */
    private inner class MegaredditPagerAdapter : FragmentStateAdapter(this@MegaredditOverview) {

        override fun getItemCount(): Int = shown.size

        override fun getItemId(position: Int): Long = pageId(position)

        override fun containsItem(itemId: Long): Boolean =
            shown.indices.any { pageId(it) == itemId }

        override fun createFragment(position: Int): Fragment {
            val f = SubmissionsView()
            f.arguments = Bundle().apply { putString("id", shown[position].key()) }
            return f
        }
    }

    private fun pageId(position: Int): Long = (pageGeneration.toLong() shl 32) or position.toLong()
}
