package me.edgan.redditslide.Activities

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.util.Locale
import me.edgan.redditslide.Authentication
import me.edgan.redditslide.Megareddit
import me.edgan.redditslide.Megareddits
import me.edgan.redditslide.PostMatch
import me.edgan.redditslide.R
import me.edgan.redditslide.SettingValues
import me.edgan.redditslide.Visuals.Palette
import me.edgan.redditslide.util.BlendModeUtil
import me.edgan.redditslide.util.LogUtil
import me.edgan.redditslide.util.MiscUtil
import me.edgan.redditslide.util.SortingUtil
import net.dean.jraw.paginators.Paginator
import net.dean.jraw.paginators.SubredditPaginator

/**
 * How much of r/all a Megareddit actually keeps, subreddit by subreddit.
 *
 * A tag matching four posts in a thousand is a tag that will show an all but empty feed, and the
 * per-subreddit breakdown is where the false positives show up -- r/Catholicism under "cat", say --
 * which is why every row can be added to the Megareddit's positive or negative list without leaving
 * the screen. A row leaves the list once it has been added to one, so what is left is always the
 * subreddits still to judge.
 *
 * Hot and rising never run out, so the scan runs until it is stopped and reports as it goes.
 */
class MegaredditCount : BaseActivityAnim() {

    private lateinit var summary: TextView
    private lateinit var progress: ProgressBar
    private lateinit var list: RecyclerView

    /** The name is what survives a rename; the definition is re-read whenever it is changed. */
    private lateinit var megaredditName: String

    private var mega: Megareddit? = null

    /**
     * Everything the scan has counted so far, as of its last page, or null before its first page.
     * Main thread only; null is what keeps [render] off the screen until there is something on it.
     */
    private var counted: Map<String, Int>? = null

    /**
     * [counted] minus the subreddits already ruled on: in the order they were found while the
     * scan runs, ranked by count once it stops. See [render].
     */
    private var rows: List<Row> = emptyList()

    /** How many posts of r/all the scan has been through, as of its last page. */
    private var scannedTotal = 0

    /** The name of the sort, or sorts, being scanned. Set before the scan thread starts. */
    private var sortLabel = ""

    /** Read by the scan thread, written by the main thread when the scan is to stop. */
    @Volatile private var cancelled = false

    /** Whether the scan is still running, which is what the Stop action is offered for. */
    private var scanning = true

    /** Whether any sort ended on an error, which is said under the tally rather than over it. */
    private var scanFailed = false

    /**
     * Set while the list is blank, between the last page of the scan and the ranked list that
     * replaces it. Ranking moves every row at once, and a blank list is what stops a tap made
     * during that from landing on whichever row took its place.
     */
    private var settling = false

    private class Row(val subreddit: String, val count: Int)

    override fun onCreate(savedInstanceState: Bundle?) {
        overrideSwipeFromAnywhere()
        super.onCreate(savedInstanceState)

        megaredditName = MiscUtil.orEmpty(intent.getStringExtra(EXTRA_MEGAREDDIT))
        val megareddit = Megareddits.get(megaredditName)
        if (megareddit == null) {
            // Deleted while this was on its way open; there is nothing to count.
            finish()
            return
        }
        mega = megareddit

        applyColorTheme(megareddit.key())
        setContentView(R.layout.activity_megareddit_count)
        MiscUtil.setupOldSwipeModeBackground(this, window.decorView)
        setupAppBar(R.id.toolbar, megareddit.name, true, true)

        summary = requireViewById(R.id.summary)
        progress = requireViewById(R.id.progress)
        list = requireViewById(R.id.subslist)
        list.layoutManager = LinearLayoutManager(this)
        list.adapter = RowAdapter()

        summary.setText(R.string.megareddit_counting)
        scan(megareddit)
    }

    override fun onDestroy() {
        cancelled = true
        super.onDestroy()
    }

    override fun onResume() {
        super.onResume()
        // A post's overflow menu can put a subreddit on one of the lists while this screen is in
        // the back stack, and a row that has been ruled on is not one to keep offering.
        mega = Megareddits.get(megaredditName) ?: mega
        render()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_megareddit_count, menu)
        return true
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        menu.findItem(R.id.stop)?.isVisible = scanning
        return super.onPrepareOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            android.R.id.home -> onBackPressedDispatcher.onBackPressed()
            // Hot and rising never run out, so the scan runs until it is stopped. What has been
            // counted by then is what gets shown -- it is not thrown away.
            R.id.stop -> cancelled = true
            else -> return super.onOptionsItemSelected(item)
        }
        return true
    }

    /**
     * Pages through r/all under this Megareddit's sorts, counting what it keeps. Each sort is
     * walked until reddit stops handing over posts -- new, top and controversial end after 7 to 10
     * pages, hot and rising do not end at all -- so the only other stop is the Stop action.
     */
    private fun scan(megareddit: Megareddit) {
        val key = megareddit.key()
        val sortAll = Megareddits.isSortAll(key)
        val timePeriod = SettingValues.getSubmissionTimePeriod(key)
        // The feed walks every sort when it is on "All", so the count has to as well, or it would
        // report a fraction of what the tab actually shows.
        val sortings =
            if (sortAll) Megareddits.ALL_SORTS else listOf(SettingValues.getSubmissionSort(key))
        sortLabel =
            if (sortAll) getString(R.string.megareddit_sort_all)
            else SortingUtil.getSortingStrings()[SortingUtil.getSortingId(sortings[0])]

        Thread {
                var scanned = 0
                var failed = false
                // Insertion-ordered: while the scan runs the rows are kept in the order the
                // subreddits turned up, so nothing already on screen ever moves.
                val counts = LinkedHashMap<String, Int>()
                // A post that is in two sorts is one post; without this the busiest subreddits
                // would be counted several times over.
                val seen = HashSet<String>()
                for (sorting in sortings) {
                    if (cancelled) {
                        break
                    }
                    // Per sort, not around the whole walk: a listing that fails partway is one
                    // listing lost, and the sorts after it are still worth reading.
                    try {
                        val paginator = SubredditPaginator(Authentication.reddit, ALL)
                        paginator.setSorting(sorting)
                        paginator.setTimePeriod(timePeriod)
                        paginator.setLimit(Paginator.RECOMMENDED_MAX_LIMIT)
                        while (!cancelled && paginator.hasNext()) {
                            // Re-read per page: the + and - buttons change the definition while
                            // this is still running, and counting on against the definition the
                            // screen opened with would keep crediting a subreddit just excluded.
                            val definition = Megareddits.get(megaredditName) ?: megareddit
                            for (post in paginator.next()) {
                                if (!seen.add(post.fullName ?: continue)) {
                                    continue
                                }
                                scanned++
                                val sub = post.subredditName ?: continue
                                // The same two tests the feed applies, in the same order, so this
                                // counts the posts the feed would actually show and the two
                                // screens land on the same number.
                                if (!PostMatch.doesMatch(post, key, false) &&
                                    definition.matches(sub)
                                ) {
                                    counts[sub] = (counts[sub] ?: 0) + 1
                                }
                            }
                            // Published a page at a time rather than at the end: hot and rising
                            // never run out, so a scan that only reported on the way out would
                            // report nothing at all until it was stopped.
                            val soFar = scanned
                            val snapshot = LinkedHashMap(counts)
                            runOnUiThread {
                                if (!isFinishing && !isDestroyed) {
                                    scannedTotal = soFar
                                    // Read here, not carried over from the top of the page: a +
                                    // or - pressed while that page was in flight has already been
                                    // applied, and handing back the definition the page was
                                    // counted against would put the judged row back on screen.
                                    mega = Megareddits.get(megaredditName) ?: mega
                                    show(snapshot)
                                }
                            }
                        }
                    } catch (e: Exception) {
                        LogUtil.e(e, "MegaredditCount.scan failed")
                        failed = true
                    }
                }

                val total = scanned
                val gaveUp = failed
                val finalCounts = LinkedHashMap(counts)

                runOnUiThread {
                    if (isFinishing || isDestroyed) {
                        return@runOnUiThread
                    }
                    scanning = false
                    scanFailed = gaveUp
                    settling = true
                    invalidateOptionsMenu()
                    progress.visibility = View.GONE
                    scannedTotal = total
                    // Even a scan that ended badly counted something on its way there, and
                    // replacing the tally with an error throws away the part that worked.
                    show(finalCounts)
                    list.postDelayed(
                        {
                            if (!isFinishing && !isDestroyed) {
                                settling = false
                                render()
                            }
                        },
                        RANK_DELAY_MS,
                    )
                }
            }
            .start()
    }

    private fun show(counts: Map<String, Int>) {
        counted = counts
        render()
    }

    /**
     * Redraws the summary and the rows from what has been counted so far, against the definition
     * as it stands now. Both are re-read rather than kept, because the + and - buttons change the
     * definition underneath a scan that is still running.
     *
     * The summary counts every subreddit the Megareddit still matches, including the ones put on
     * the positive list and so no longer listed below, because those posts are in its feed. A
     * subreddit moved to the negative list is dropped from both: its posts are not.
     */
    private fun render() {
        val counts = counted ?: return
        val definition = mega
        val matched =
            if (definition == null) counts else counts.filterKeys { definition.matches(it) }
        summary.text =
            getString(R.string.megareddit_count_scanned, scannedTotal, sortLabel) +
                "\n" +
                resources.getQuantityString(
                    R.plurals.megareddit_count_matched,
                    matched.size,
                    matched.values.sum(),
                    matched.size,
                )
        if (scanFailed) {
            summary.append("\n\n" + getString(R.string.err_loading_content))
        } else if (!scanning && counts.isEmpty()) {
            // Against what was counted, not what is left of it: excluding every match by hand is
            // not the same as the Megareddit matching nothing, and only the second is worth saying.
            summary.append("\n\n" + getString(R.string.megareddit_count_none))
        }

        // Once a subreddit has been kept or dropped there is nothing left to decide about it, and
        // leaving it here only buries the ones still to judge.
        val kept = matched.entries.filter { definition == null || !definition.isListed(it.key) }
        rows =
            when {
                // Blank, so that the reordering below cannot happen under a finger already down.
                settling -> emptyList()
                // Found order while the scan runs: new subreddits are appended, and a row that
                // is already on screen keeps its place however its count grows.
                scanning -> kept.map { Row(it.key, it.value) }
                else ->
                    kept.sortedWith(
                            compareByDescending<Map.Entry<String, Int>> { it.value }
                                .thenBy { it.key.lowercase(Locale.ENGLISH) }
                        )
                        .map { Row(it.key, it.value) }
            }
        list.adapter?.notifyDataSetChanged()
    }

    /** Opens a counted subreddit, so a name in the breakdown can be looked at before judging it. */
    private fun openSubreddit(subreddit: String) {
        startActivity(
            Intent(this, SubredditView::class.java)
                .putExtra(SubredditView.EXTRA_SUBREDDIT, subreddit)
        )
    }

    /**
     * Adds a counted subreddit to one of the Megareddit's two lists. A subreddit belongs to at most
     * one of them, so this also takes it off the other; either way the row leaves the list, since
     * it has now been ruled on.
     */
    private fun keepOrDrop(subreddit: String, positive: Boolean) {
        val key = Megareddits.keyFor(megaredditName)
        val changed =
            if (positive) Megareddits.addPositive(key, subreddit)
            else Megareddits.addNegative(key, subreddit)
        if (!changed) {
            // Deleted from under this screen.
            finish()
            return
        }
        mega = Megareddits.get(megaredditName)
        render()
    }

    private inner class RowAdapter : RecyclerView.Adapter<RowAdapter.ViewHolder>() {

        inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val color: View = itemView.requireViewById(R.id.color)
            val name: TextView = itemView.requireViewById(R.id.name)
            val count: TextView = itemView.requireViewById(R.id.count)
            val addPositive: ImageView = itemView.requireViewById(R.id.add_positive)
            val addNegative: ImageView = itemView.requireViewById(R.id.add_negative)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder =
            ViewHolder(
                LayoutInflater.from(parent.context)
                    .inflate(R.layout.megareddit_count_row, parent, false)
            )

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val row = rows[position]
            holder.name.text = "/r/" + row.subreddit
            holder.count.text = row.count.toString()

            holder.color.setBackgroundResource(R.drawable.circle)
            BlendModeUtil.tintDrawableAsModulate(
                holder.color.background,
                Palette.getColor(row.subreddit),
            )

            holder.addPositive.contentDescription =
                getString(R.string.megareddit_add_positive, row.subreddit)
            holder.addNegative.contentDescription =
                getString(R.string.megareddit_add_negative, row.subreddit)

            holder.itemView.setOnClickListener { openSubreddit(row.subreddit) }
            holder.addPositive.setOnClickListener { keepOrDrop(row.subreddit, true) }
            holder.addNegative.setOnClickListener { keepOrDrop(row.subreddit, false) }
        }

        override fun getItemCount(): Int = rows.size
    }

    companion object {
        const val EXTRA_MEGAREDDIT = "megareddit"

        private const val ALL = "all"

        /** How long the list stays blank between the scan ending and the ranked list appearing. */
        private const val RANK_DELAY_MS = 300L
    }
}
