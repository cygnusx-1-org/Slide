package me.edgan.redditslide.Activities

import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import android.graphics.Color
import android.os.Bundle
import android.text.Spannable
import android.view.Gravity
import android.view.Menu
import android.view.MenuItem
import android.view.ViewGroup
import android.view.animation.LinearInterpolator
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.PopupMenu
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentStatePagerAdapter
import androidx.viewpager.widget.ViewPager
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.tabs.TabLayout
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
private const val RC_EXPORT = 1801
private const val RC_IMPORT = 1802

/**
 * The account's Megareddits, one tab each. A page is the same [SubmissionsView] a main-screen tab
 * uses, loading the Megareddit's key, so its feed, sort and cache are shared with the tab.
 */
class MegaredditOverview : BaseActivityAnim() {

    private lateinit var pager: ViewPager
    private lateinit var tabs: TabLayout
    private var adapter: MegaredditPagerAdapter? = null

    /** What the tabs were built from, so a return to this screen only rebuilds them on a change. */
    private var shown: List<Megareddit> = emptyList()

    /** Set while the editor is open: whatever it saved has to reload the pages. */
    private var editing = false

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
        setContentView(R.layout.activity_multireddits)
        MiscUtil.setupOldSwipeModeBackground(this, window.decorView)

        setupAppBar(R.id.toolbar, R.string.title_megareddits, true, false)

        requireViewById<ViewGroup>(R.id.header).setBackgroundColor(Palette.getDefaultColor())
        tabs = requireViewById(R.id.sliding_tabs)
        tabs.tabMode = TabLayout.MODE_SCROLLABLE
        pager = requireViewById(R.id.content_view)
        requireToolbar().popupTheme = ColorPreferences(this).fontStyle.baseId

        // The shared layout carries the multireddit screen's subreddit drawer, which has nothing
        // to list here.
        requireViewById<DrawerLayout>(R.id.drawer_layout)
            .setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED, GravityCompat.END)

        pager.addOnPageChangeListener(
            object : ViewPager.SimpleOnPageChangeListener() {
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
        if (all.isEmpty()) {
            // Detach first: an attached adapter whose count drops without a notify throws.
            pager.adapter = null
            adapter = null
            shown = all
            editing = false
            showCreateDialog()
            return
        }
        if (editing || all.map { it.name } != shown.map { it.name }) {
            val current = pager.currentItem
            shown = all
            // The pages are about to be rebuilt, so nothing counted against the old ones holds.
            counts.clear()
            adapter = MegaredditPagerAdapter(supportFragmentManager)
            pager.adapter = adapter
            pager.offscreenPageLimit = 1
            tabs.setupWithViewPager(pager)
            pager.currentItem = current.coerceAtMost(all.size - 1)
            colorFor(pager.currentItem)
        } else {
            // Same tabs; keep the pages, and their scroll positions, as they are.
            shown = all
            recountCurrentPage()
            // The setting that decides whether the number shows at all lives a screen away, so
            // every title is rewritten on the way back rather than only the one that changed.
            refreshTabTitles()
        }
        editing = false
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

    /** Asks the page on screen what it is showing, for the changes no feed update reports. */
    private fun recountCurrentPage() {
        val page = adapter?.currentFragment as? SubmissionsView ?: return
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
        window.statusBarColor =
            if (SettingValues.alwaysBlackStatusbar) Color.BLACK else Palette.getDarkerColor(key)
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
        editing = true
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
        startActivityForResult(intent, RC_EXPORT)
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
        startActivityForResult(intent, RC_IMPORT)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        val uri = data?.data
        if (resultCode != RESULT_OK || uri == null) {
            pendingExport = null
            return
        }
        when (requestCode) {
            RC_EXPORT -> writeExport(uri)
            RC_IMPORT -> readImport(uri)
        }
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
        // Whatever it brought in, the tabs are rebuilt on the way back rather than left stale.
        editing = true
        snack(resources.getQuantityString(R.plurals.megareddits_imported, imported, imported))
    }

    private fun snack(text: String) {
        LayoutUtils.showSnackbar(Snackbar.make(pager, text, Snackbar.LENGTH_SHORT))
    }

    /**
     * Opens the count screen, which scans r/all and can add any subreddit it finds to this
     * Megareddit's lists -- so the pages reload on the way back, as they do after the editor.
     */
    private fun openCount(mega: Megareddit) {
        editing = true
        startActivity(
            Intent(this, MegaredditCount::class.java)
                .putExtra(MegaredditCount.EXTRA_MEGAREDDIT, mega.name)
        )
    }

    private fun refreshCurrent() {
        (adapter?.currentFragment as? SubmissionsView)?.forceRefresh()
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
            if (chosen == 0) {
                Megareddits.setSortAll(key, true)
                refreshCurrent()
                return@setOnMenuItemClickListener true
            }
            val sorting =
                sortings.getOrNull(chosen - 1) ?: return@setOnMenuItemClickListener true
            Megareddits.setSortAll(key, false)
            SortingUtil.setSorting(key, sorting)
            if (sorting == Sorting.TOP || sorting == Sorting.CONTROVERSIAL) {
                openTimePopup(key)
            } else {
                refreshCurrent()
            }
            true
        }
        popup.show()
    }

    private fun openTimePopup(key: String) {
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
                SortingUtil.setTime(key, time)
                refreshCurrent()
            }
            true
        }
        popup.show()
    }

    private inner class MegaredditPagerAdapter(fm: FragmentManager) :
        FragmentStatePagerAdapter(fm, FragmentStatePagerAdapter.BEHAVIOR_RESUME_ONLY_CURRENT_FRAGMENT) {

        var currentFragment: Fragment? = null
            private set

        override fun getItem(position: Int): Fragment {
            val f = SubmissionsView()
            f.arguments = Bundle().apply { putString("id", shown[position].key()) }
            return f
        }

        override fun setPrimaryItem(container: ViewGroup, position: Int, `object`: Any) {
            currentFragment = `object` as Fragment
            super.setPrimaryItem(container, position, `object`)
        }

        override fun getCount(): Int = shown.size

        override fun getPageTitle(position: Int): CharSequence = titleFor(position)
    }
}
