package me.edgan.redditslide.Activities

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.util.Locale
import me.edgan.redditslide.Megareddits
import me.edgan.redditslide.R
import me.edgan.redditslide.Visuals.Palette
import me.edgan.redditslide.util.BlendModeUtil
import me.edgan.redditslide.util.DialogUtil
import me.edgan.redditslide.util.MaterialInputDialog
import me.edgan.redditslide.util.MiscUtil

/**
 * One of a Megareddit's four lists, on a screen of its own. A Megareddit that filters r/all can
 * hold hundreds of subreddits, which on one shared screen buries the other three lists below it.
 *
 * Nothing is stored here. The edited list goes back to [CreateMegareddit], which owns the decision
 * of whether it is ever saved, so backing out of the editor still discards everything done here.
 */
class MegaredditSection : BaseActivityAnim() {

    private lateinit var section: CreateMegareddit.Section
    private lateinit var list: RecyclerView
    private lateinit var empty: TextView

    private val values = mutableListOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        overrideSwipeFromAnywhere()
        super.onCreate(savedInstanceState)

        val ordinal = intent.getIntExtra(EXTRA_SECTION, -1)
        if (ordinal !in CreateMegareddit.Section.entries.indices) {
            // Nothing to edit; only the editor opens this, and it always names a section.
            finish()
            return
        }
        section = CreateMegareddit.Section.entries[ordinal]
        values.addAll(intent.getStringArrayListExtra(EXTRA_VALUES) ?: emptyList())

        applyColorTheme()
        setContentView(R.layout.activity_megareddit_section)
        MiscUtil.setupOldSwipeModeBackground(this, window.decorView)
        setupAppBar(R.id.toolbar, section.title, true, true)

        empty = requireViewById(R.id.empty)
        list = requireViewById(R.id.subslist)
        list.layoutManager = LinearLayoutManager(this)
        list.adapter = ValueAdapter()

        // The result is kept current rather than written on the way out, so the up arrow, the back
        // gesture and a swipe back all hand the same list over.
        publish()
        showEmpty()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_megareddit_section, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            android.R.id.home -> onBackPressedDispatcher.onBackPressed()
            R.id.add -> showAddDialog()
            else -> return super.onOptionsItemSelected(item)
        }
        return true
    }

    private fun showAddDialog() {
        MaterialInputDialog.Builder(this)
            .title(section.title)
            .input(getString(section.hint), "", null)
            .inputRange(2, 24)
            .autoDismiss(false)
            .positiveText(R.string.btn_add)
            .onPositive { dialog ->
                val input = dialog.inputEditText.text.toString()
                val value =
                    if (section.isSubreddit) Megareddits.normalizeSubreddit(input)
                    else input.trim().lowercase(Locale.ENGLISH)
                if (!Megareddits.isValidTerm(value)) {
                    dialog.inputEditText.error =
                        getString(
                            if (section.isSubreddit) R.string.megareddit_invalid_subreddit
                            else R.string.megareddit_invalid_tag
                        )
                    return@onPositive
                }
                add(value)
                dialog.dismiss()
            }
            .negativeText(R.string.btn_cancel)
            .onNegative { it.dismiss() }
            .show()
    }

    private fun add(value: String) {
        if (value in values) {
            return
        }
        values.add(value)
        // Kept in the order the list is stored and shown in, rather than appending to the end.
        values.sort()
        changed()
    }

    private fun confirmRemove(value: String) {
        DialogUtil.showWithCardBackground(
            AlertDialog.Builder(this)
                .setTitle(getString(R.string.megareddit_remove_title, value))
                .setPositiveButton(R.string.btn_yes) { _, _ ->
                    values.remove(value)
                    changed()
                }
                .setNegativeButton(R.string.btn_no, null)
        )
    }

    private fun changed() {
        publish()
        list.adapter?.notifyDataSetChanged()
        showEmpty()
    }

    private fun publish() {
        setResult(
            RESULT_OK,
            Intent()
                .putExtra(EXTRA_SECTION, section.ordinal)
                .putStringArrayListExtra(EXTRA_VALUES, ArrayList(values)),
        )
    }

    private fun showEmpty() {
        empty.visibility = if (values.isEmpty()) View.VISIBLE else View.GONE
    }

    private inner class ValueAdapter : RecyclerView.Adapter<ValueAdapter.ViewHolder>() {

        inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val name: TextView = itemView.requireViewById(R.id.name)
            val color: View = itemView.requireViewById(R.id.color)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder =
            ViewHolder(
                LayoutInflater.from(parent.context).inflate(R.layout.subforsublist, parent, false)
            )

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val value = values[position]
            holder.name.text = if (section.isSubreddit) "/r/" + value else value
            // A tag is not a subreddit, so it has no colour; the dot keeps its space so every
            // value lines up with the ones in the other sections.
            if (section.isSubreddit) {
                holder.color.visibility = View.VISIBLE
                holder.color.setBackgroundResource(R.drawable.circle)
                BlendModeUtil.tintDrawableAsModulate(
                    holder.color.background,
                    Palette.getColor(value),
                )
            } else {
                holder.color.visibility = View.INVISIBLE
            }
            holder.itemView.setOnClickListener { confirmRemove(value) }
        }

        override fun getItemCount(): Int = values.size
    }

    companion object {
        const val EXTRA_SECTION = "section"
        const val EXTRA_VALUES = "values"
    }
}
