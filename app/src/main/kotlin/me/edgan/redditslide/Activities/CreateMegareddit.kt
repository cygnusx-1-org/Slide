package me.edgan.redditslide.Activities

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.annotation.StringRes
import androidx.appcompat.app.AlertDialog
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import me.edgan.redditslide.Megareddit
import me.edgan.redditslide.Megareddits
import me.edgan.redditslide.R
import me.edgan.redditslide.util.DialogUtil
import me.edgan.redditslide.util.MiscUtil

/**
 * Creates or edits a [Megareddit]: a title, and four lists, each of which is counted here and
 * edited on its own screen. Nothing here touches the network; a Megareddit is only ever a filter
 * over r/all.
 */
class CreateMegareddit : BaseActivityAnim() {

    /** One of the four lists a Megareddit is made of, as [MegaredditSection] edits it. */
    enum class Section(
        @StringRes val title: Int,
        @StringRes val hint: Int,
        val isSubreddit: Boolean,
    ) {
        POSITIVE_TAGS(R.string.megareddit_positive_tags, R.string.megareddit_tag_hint, false),
        NEGATIVE_TAGS(R.string.megareddit_negative_tags, R.string.megareddit_tag_hint, false),
        POSITIVE(
            R.string.megareddit_positive_subreddits,
            R.string.megareddit_subreddit_hint,
            true,
        ),
        NEGATIVE(
            R.string.megareddit_negative_subreddits,
            R.string.megareddit_subreddit_hint,
            true,
        );

        /** The list a value leaves when it joins this one: it is kept or dropped, never both. */
        val opposite: Section
            get() =
                when (this) {
                    POSITIVE_TAGS -> NEGATIVE_TAGS
                    NEGATIVE_TAGS -> POSITIVE_TAGS
                    POSITIVE -> NEGATIVE
                    NEGATIVE -> POSITIVE
                }
    }

    private lateinit var title: EditText
    private lateinit var list: RecyclerView

    private val values: Map<Section, MutableList<String>> =
        Section.entries.associateWith { mutableListOf() }

    /** The name of the Megareddit being edited, or null when creating one. */
    private var old: String? = null

    /** Whether anything differs from what was loaded, so back only asks when there is. */
    private var dirty = false

    override fun onCreate(savedInstanceState: Bundle?) {
        overrideSwipeFromAnywhere()
        super.onCreate(savedInstanceState)
        onBackPressedDispatcher.addCallback(this, backCallback)
        applyColorTheme()
        setContentView(R.layout.activity_createmegareddit)
        MiscUtil.setupOldSwipeModeBackground(this, window.decorView)
        setupAppBar(R.id.toolbar, "", true, true)

        title = requireViewById(R.id.name)
        list = requireViewById(R.id.subslist)
        list.layoutManager = LinearLayoutManager(this)

        val existing = intent.getStringExtra(EXTRA_MEGAREDDIT)?.let { Megareddits.get(it) }
        if (existing != null) {
            old = existing.name
            title.setText(existing.name)
            values.getValue(Section.POSITIVE_TAGS).addAll(existing.positiveTags)
            values.getValue(Section.NEGATIVE_TAGS).addAll(existing.negativeTags)
            values.getValue(Section.POSITIVE).addAll(existing.positiveSubreddits)
            values.getValue(Section.NEGATIVE).addAll(existing.negativeSubreddits)
        }
        list.adapter = SectionAdapter()
    }

    /** Hands one list to [MegaredditSection] to be edited, and waits for it to come back. */
    private fun openSection(section: Section) {
        startActivityForResult(
            Intent(this, MegaredditSection::class.java)
                .putExtra(MegaredditSection.EXTRA_SECTION, section.ordinal)
                .putStringArrayListExtra(
                    MegaredditSection.EXTRA_VALUES,
                    ArrayList(values.getValue(section)),
                ),
            RC_SECTION,
        )
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != RC_SECTION || resultCode != RESULT_OK || data == null) {
            return
        }
        val ordinal = data.getIntExtra(MegaredditSection.EXTRA_SECTION, -1)
        if (ordinal !in Section.entries.indices) {
            return
        }
        applyEdited(
            Section.entries[ordinal],
            data.getStringArrayListExtra(MegaredditSection.EXTRA_VALUES) ?: return,
        )
    }

    /**
     * Takes an edited list back. A value that has just joined this list leaves the opposing one,
     * so nothing is ever both kept and dropped.
     */
    private fun applyEdited(section: Section, edited: List<String>) {
        val target = values.getValue(section)
        val moved = values.getValue(section.opposite).removeAll(edited.toSet())
        if (target == edited && !moved) {
            return
        }
        target.clear()
        target.addAll(edited)
        dirty = true
        (list.adapter as SectionAdapter).notifyDataSetChanged()
    }

    /** Validates and stores the Megareddit, then closes. Returns false when it cannot be saved. */
    private fun save(): Boolean {
        val name = title.text.toString().trim()
        val error =
            when {
                name.isEmpty() -> R.string.megareddit_title_empty
                !Megareddits.isValidName(name) -> R.string.megareddit_title_invalid
                Megareddits.isNameTaken(name, old) -> R.string.megareddit_title_taken
                values.getValue(Section.POSITIVE_TAGS).isEmpty() &&
                    values.getValue(Section.POSITIVE).isEmpty() -> R.string.megareddit_nothing_to_match
                else -> null
            }
        if (error != null) {
            DialogUtil.showWithCardBackground(
                AlertDialog.Builder(this)
                    .setTitle(R.string.err_title)
                    .setMessage(error)
                    .setPositiveButton(R.string.btn_ok, null)
            )
            return false
        }
        Megareddits.save(
            Megareddit(
                name,
                values.getValue(Section.POSITIVE_TAGS),
                values.getValue(Section.NEGATIVE_TAGS),
                values.getValue(Section.POSITIVE),
                values.getValue(Section.NEGATIVE),
            ),
            old,
        )
        // The reorder screen opens this to put a Megareddit in the tab list; without the name it
        // has nothing to add and the trip through here achieves nothing.
        setResult(RESULT_OK, Intent().putExtra(EXTRA_MEGAREDDIT, name))
        finish()
        return true
    }

    private val backCallback =
        object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (!dirty && title.text.toString().trim() == (old ?: "")) {
                    finish()
                    return
                }
                DialogUtil.showWithCardBackground(
                    AlertDialog.Builder(this@CreateMegareddit)
                        .setTitle(R.string.general_confirm_exit)
                        .setMessage(R.string.megareddit_save_option)
                        .setPositiveButton(R.string.btn_yes) { _, _ -> save() }
                        .setNegativeButton(R.string.btn_no) { _, _ -> finish() }
                )
            }
        }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_create_multi, menu)
        menu.findItem(R.id.delete).isVisible = old != null
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            android.R.id.home -> onBackPressedDispatcher.onBackPressed()
            R.id.save -> save()
            R.id.delete -> {
                val name = old ?: return true
                DialogUtil.showWithCardBackground(
                    AlertDialog.Builder(this)
                        .setTitle(getString(R.string.megareddit_delete_title, name))
                        .setMessage(R.string.cannot_be_undone)
                        .setPositiveButton(R.string.btn_yes) { _, _ ->
                            Megareddits.delete(name)
                            finish()
                        }
                        .setNegativeButton(R.string.btn_cancel, null)
                )
            }
            else -> return super.onOptionsItemSelected(item)
        }
        return true
    }

    private inner class SectionAdapter : RecyclerView.Adapter<SectionAdapter.ViewHolder>() {

        inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val name: TextView = itemView.requireViewById(R.id.name)
            val count: TextView = itemView.requireViewById(R.id.count)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder =
            ViewHolder(
                LayoutInflater.from(parent.context)
                    .inflate(R.layout.megareddit_section_row, parent, false)
            )

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val section = Section.entries[position]
            holder.name.setText(section.title)
            holder.count.text = values.getValue(section).size.toString()
            holder.itemView.setOnClickListener { openSection(section) }
        }

        override fun getItemCount(): Int = Section.entries.size
    }

    companion object {
        const val EXTRA_MEGAREDDIT = "megareddit"

        private const val RC_SECTION = 1803
    }
}
