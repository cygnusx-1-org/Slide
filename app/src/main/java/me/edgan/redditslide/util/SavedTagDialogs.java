package me.edgan.redditslide.util;

import android.content.Context;
import android.content.DialogInterface;
import android.content.res.TypedArray;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.text.InputType;
import android.view.ContextThemeWrapper;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import me.edgan.redditslide.R;
import me.edgan.redditslide.SavedTagStore;
import me.edgan.redditslide.SavedTags;
import me.edgan.redditslide.Visuals.ColorPreferences;

import net.dean.jraw.models.Contribution;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * The dialogs shared by everything that touches saved tags: the picker on a saved item, and the
 * validated name prompt used to create and rename them.
 *
 * <p>Kept in one place because three callers need them -- the post overflow sheet, the saved-comment
 * sheet, and the manage screen -- and the validation rules have to agree in all three.
 */
@NullMarked
public final class SavedTagDialogs {

    private SavedTagDialogs() {}

    /** Told when a dialog changed something the caller should redraw for. */
    public interface OnChanged {
        void onChanged();
    }

    /** Handed the accepted, trimmed name. */
    public interface OnName {
        void onName(String name);
    }

    private static Context themed(Context base) {
        return new ContextThemeWrapper(
                base, new ColorPreferences(base).getFontStyle().getBaseId());
    }

    /**
     * The display name of a built-in tag.
     *
     * <p>The content-type ones reuse the {@code type_*} resources {@code ContentType} labels cards
     * with, so "GIF" in the tag list is the same word the card shows.
     */
    public static String builtinLabel(Context context, SavedTags.Builtin builtin) {
        switch (builtin) {
            case POSTS:
                return context.getString(R.string.tag_builtin_posts);
            case COMMENTS:
                return context.getString(R.string.tag_builtin_comments);
            case ALBUM:
                return context.getString(R.string.type_album);
            case DEVIANTART:
                return context.getString(R.string.type_deviantart);
            case GALLERY:
                return context.getString(R.string.type_gallery);
            case GIF:
                return context.getString(R.string.type_gif);
            case IMAGE:
                return context.getString(R.string.type_img);
            case IMGUR:
                return context.getString(R.string.type_imgur);
            case LINK:
                return context.getString(R.string.type_link);
            case REDDIT_LINK:
                return context.getString(R.string.type_reddit);
            case REDDIT_VIDEO:
                return context.getString(R.string.type_vreddit);
            case SELFTEXT:
                return context.getString(R.string.type_selftext);
            case STREAMABLE:
                return context.getString(R.string.type_streamable);
            case TUMBLR:
                return context.getString(R.string.type_tumblr);
            case XKCD:
                return context.getString(R.string.type_xkcd);
            case YOUTUBE:
                return context.getString(R.string.type_youtube);
            case ALL:
            default:
                return context.getString(R.string.tag_builtin_all);
        }
    }

    /** Every built-in label, in the order they are pinned ahead of the user's own tags. */
    public static List<String> builtinLabels(Context context) {
        final List<String> labels = new ArrayList<>();
        for (SavedTags.Builtin builtin : SavedTags.Builtin.values()) {
            labels.add(builtinLabel(context, builtin));
        }
        return labels;
    }

    /**
     * Prompt for a tag name, keeping the positive button disabled until it is valid.
     *
     * <p>{@link MaterialInputDialog.Builder#inputRange} already gates on length and its input
     * callback runs after that check, so this narrows the button further without fighting it.
     *
     * @param renamingFrom the tag being renamed, excluded from the duplicate check; {@code null}
     *     when creating a new one
     */
    public static void promptForName(
            final Context context, final @Nullable String renamingFrom, final OnName onName) {
        final List<String> existing = SavedTagStore.getTagNames();
        final List<String> reserved = builtinLabels(context);

        new MaterialInputDialog.Builder(context)
                .title(renamingFrom == null ? R.string.tag_add : R.string.tag_rename)
                .input(
                        context.getString(R.string.tag_set_name_hint),
                        renamingFrom,
                        (dialog, input) -> {
                            final Button positive =
                                    dialog.getActionButton(DialogInterface.BUTTON_POSITIVE);
                            positive.setEnabled(
                                    SavedTags.validate(
                                                    input.toString(),
                                                    existing,
                                                    renamingFrom,
                                                    reserved)
                                            == SavedTags.Validation.OK);
                        })
                .inputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS)
                .inputRange(SavedTags.MIN_NAME_LENGTH, -1)
                .positiveText(R.string.btn_set)
                .negativeText(R.string.btn_cancel)
                .onPositive(
                        dialog -> {
                            final String name =
                                    dialog.getInputEditText().getText().toString().trim();
                            final SavedTags.Validation result =
                                    SavedTags.validate(name, existing, renamingFrom, reserved);
                            if (result == SavedTags.Validation.OK) {
                                onName.onName(name);
                            } else {
                                showNameError(context, result);
                            }
                        })
                .show();
    }

    /**
     * Why a name was refused. The button gating means this is only reachable if the text changed
     * between the last validation and the tap, but silently doing nothing there would be worse.
     */
    private static void showNameError(Context context, SavedTags.Validation result) {
        if (result == SavedTags.Validation.OK) {
            return;
        }
        new MaterialAlertDialogBuilder(themed(context))
                .setTitle(R.string.tag_name_used_title)
                .setMessage(
                        result == SavedTags.Validation.RESERVED
                                ? R.string.tag_name_reserved
                                : R.string.tag_name_used)
                .setPositiveButton(R.string.btn_ok, null)
                .show();
    }

    /** Told which tags the Saved tab should now be filtered by. */
    public interface OnFilterChosen {
        void onFilter(Set<String> tags, Set<SavedTags.Builtin> builtins);
    }

    /**
     * The filter the tag list will hand back, and whether it differs from the one it opened with.
     *
     * <p>Held across the whole dialog rather than reported as it changes, because reporting each
     * tick immediately reloads the feed underneath a dialog the user is still working in. It is
     * also what a rename and a delete are applied to: the tag that is selected can itself be
     * renamed twice in one visit, and the second rename has to match the name the first one gave
     * it.
     */
    private static final class PendingFilter {
        final Set<String> tags = new LinkedHashSet<>();
        final EnumSet<SavedTags.Builtin> builtins = EnumSet.noneOf(SavedTags.Builtin.class);
        boolean changed;

        PendingFilter(Collection<String> tags, Collection<SavedTags.Builtin> builtins) {
            this.tags.addAll(tags);
            this.builtins.addAll(builtins);
            normalize();
        }

        /**
         * {@link SavedTags.Builtin#ALL} is the absence of a filter, so it cannot sit beside
         * anything -- and an empty selection is the same statement, so it becomes All rather than
         * a filter that matches nothing.
         */
        private void normalize() {
            if (builtins.contains(SavedTags.Builtin.ALL)
                    || (tags.isEmpty() && builtins.isEmpty())) {
                tags.clear();
                builtins.clear();
                builtins.add(SavedTags.Builtin.ALL);
            }
        }

        /**
         * Run {@code mutation}, re-normalize, and record whether the selection actually moved.
         * Re-ticking All when All is already the whole selection is not a change, and marking it
         * one would reload the feed for nothing.
         */
        private void mutate(Runnable mutation) {
            final Set<String> beforeTags = new LinkedHashSet<>(tags);
            final EnumSet<SavedTags.Builtin> beforeBuiltins = EnumSet.copyOf(builtins);
            mutation.run();
            normalize();
            if (!beforeTags.equals(tags) || !beforeBuiltins.equals(builtins)) {
                changed = true;
            }
        }

        boolean isSelected(SavedTags.Builtin builtin) {
            return builtins.contains(builtin);
        }

        boolean isSelected(String tag) {
            return tags.contains(tag);
        }

        void toggle(final SavedTags.Builtin builtin) {
            mutate(
                    () -> {
                        if (builtin == SavedTags.Builtin.ALL) {
                            tags.clear();
                            builtins.clear();
                            return;
                        }
                        builtins.remove(SavedTags.Builtin.ALL);
                        if (!builtins.remove(builtin)) {
                            builtins.add(builtin);
                        }
                    });
        }

        void toggle(final String tag) {
            mutate(
                    () -> {
                        builtins.remove(SavedTags.Builtin.ALL);
                        if (!tags.remove(tag)) {
                            tags.add(tag);
                        }
                    });
        }

        /** Follow a rename, so a selected tag stays selected under its new name. */
        void renamed(final String from, final String to) {
            mutate(
                    () -> {
                        if (tags.remove(from)) {
                            tags.add(to);
                        }
                    });
        }

        /** Drop a deleted tag; if it was the whole selection, normalize puts All back. */
        void deleted(final String tag) {
            mutate(() -> tags.remove(tag));
        }
    }

    /**
     * The tag list: one dialog that both picks the Saved tab's filter and edits the tags.
     *
     * <p>These were two separate things -- a filter dialog and a manage screen -- which meant two
     * toolbar buttons showing the same list for different reasons. Tapping a row ticks it; tapping
     * a custom tag's pencil renames or deletes it; the title's + adds one. Built-in tags are
     * selectable but not editable, so their pencil space is reserved rather than removed and every
     * row's name still lands on the same pixels.
     *
     * @param activeTags the custom tags currently filtered by
     * @param activeBuiltins the built-in tags currently filtered by
     */
    public static void showTagList(
            final Context context,
            final Collection<String> activeTags,
            final Collection<SavedTags.Builtin> activeBuiltins,
            final OnFilterChosen onFilterChosen) {

        final Context themed = themed(context);
        final LayoutInflater inflater = LayoutInflater.from(themed);
        final View title = inflater.inflate(R.layout.dialog_tag_list_title, null);
        final View body = inflater.inflate(R.layout.dialog_tag_list, null);
        final LinearLayout list = (LinearLayout) body.requireViewById(R.id.taglist);

        final PendingFilter pending = new PendingFilter(activeTags, activeBuiltins);

        final AlertDialog dialog =
                new MaterialAlertDialogBuilder(themed)
                        .setCustomTitle(title)
                        .setView(body)
                        // Not "Cancel": the selection is applied on the way out however the dialog
                        // closes, so a button offering to discard it would be lying.
                        .setPositiveButton(R.string.btn_ok, null)
                        .create();

        // The feed reloads once, on the way out. Ticking several tags is a run of small changes and
        // reloading after each one would pull the list out from under the dialog still on screen.
        dialog.setOnDismissListener(
                d -> {
                    if (pending.changed) {
                        onFilterChosen.onFilter(
                                new LinkedHashSet<>(pending.tags),
                                EnumSet.copyOf(pending.builtins));
                    }
                });

        // The tag set changes under this dialog as rows are ticked, added, renamed and deleted, so
        // the list is rebuilt rather than patched.
        final Runnable[] rebuild = new Runnable[1];
        rebuild[0] = () -> populateTagList(themed, inflater, list, pending, rebuild);
        rebuild[0].run();

        title.requireViewById(R.id.add)
                .setOnClickListener(
                        v ->
                                promptForName(
                                        context,
                                        null,
                                        name -> {
                                            SavedTagStore.createTag(name);
                                            rebuild[0].run();
                                        }));

        dialog.show();
    }

    /** Fill {@code list} with All, then Types, the user's own tags, and the content types. */
    private static void populateTagList(
            final Context themed,
            final LayoutInflater inflater,
            final LinearLayout list,
            final PendingFilter pending,
            final Runnable[] rebuild) {

        list.removeAllViews();

        // All sits above the sections rather than inside one: it is the absence of a filter, not
        // one of the kinds a filter can be.
        addBuiltinRow(themed, inflater, list, pending, rebuild, SavedTags.Builtin.ALL);

        addHeader(inflater, list, themed.getString(R.string.tag_types_header));
        for (SavedTags.Builtin builtin : SavedTags.Builtin.values()) {
            if (builtin.isStructural()) {
                addBuiltinRow(themed, inflater, list, pending, rebuild, builtin);
            }
        }

        // The user's own tags sit above the content types: they are the short, hand-made list
        // someone opens this dialog to reach, and burying them under the whole of Post types meant
        // scrolling past every derived row to get to them.
        addCustomTagRows(themed, inflater, list, pending, rebuild);

        addHeader(inflater, list, themed.getString(R.string.tag_post_types_header));
        for (SavedTags.Builtin builtin : SavedTags.Builtin.values()) {
            if (builtin.isContentType()) {
                addBuiltinRow(themed, inflater, list, pending, rebuild, builtin);
            }
        }
    }

    /** The Custom tags section: selectable like the rest, and the only rows that can be edited. */
    private static void addCustomTagRows(
            final Context themed,
            final LayoutInflater inflater,
            final LinearLayout list,
            final PendingFilter pending,
            final Runnable[] rebuild) {

        addHeader(inflater, list, themed.getString(R.string.tag_custom_header));
        final List<String> names = SavedTagStore.getTagNames();
        if (names.isEmpty()) {
            final View empty =
                    addRow(themed, inflater, list, themed.getString(R.string.tag_none_yet), false);
            ((TextView) empty.requireViewById(R.id.name)).setAlpha(0.6f);
            empty.requireViewById(R.id.remove).setVisibility(View.INVISIBLE);
            return;
        }

        for (final String tag : names) {
            final View row = addRow(themed, inflater, list, tag, pending.isSelected(tag));
            row.setOnClickListener(
                    v -> {
                        pending.toggle(tag);
                        rebuild[0].run();
                    });

            final ImageView edit = (ImageView) row.requireViewById(R.id.remove);
            edit.setImageResource(R.drawable.ic_edit);
            edit.setContentDescription(themed.getString(R.string.tag_manage));
            edit.setOnClickListener(v -> showEditDialog(themed, tag, pending, rebuild));
        }
    }

    /** One selectable built-in row. Built-ins are derived, so there is nothing to edit on them. */
    private static void addBuiltinRow(
            final Context themed,
            final LayoutInflater inflater,
            final LinearLayout list,
            final PendingFilter pending,
            final Runnable[] rebuild,
            final SavedTags.Builtin builtin) {

        final View row =
                addRow(
                        themed,
                        inflater,
                        list,
                        builtinLabel(themed, builtin),
                        pending.isSelected(builtin));
        // The space stays reserved so built-in names line up with the editable rows below.
        row.requireViewById(R.id.remove).setVisibility(View.INVISIBLE);
        row.setOnClickListener(
                v -> {
                    pending.toggle(builtin);
                    rebuild[0].run();
                });
    }

    private static void addHeader(LayoutInflater inflater, LinearLayout list, String text) {
        final TextView header =
                (TextView) inflater.inflate(R.layout.saved_tag_header, list, false);
        header.setText(text);
        list.addView(header);
    }

    /**
     * One row, ticked or not.
     *
     * <p>The tick is always attached and hidden by alpha rather than by being left off, so a row's
     * label sits on exactly the same pixels whether or not it is selected -- otherwise every name
     * in the list would shift sideways as tags were ticked.
     */
    private static View addRow(
            final Context themed,
            final LayoutInflater inflater,
            final LinearLayout list,
            final String text,
            final boolean checked) {

        final View row = inflater.inflate(R.layout.account_textview, list, false);
        final TextView name = (TextView) row.requireViewById(R.id.name);
        name.setText(text);

        // mutate() before setAlpha: a VectorDrawable straight out of Resources shares its
        // ConstantState with every other instance of the same drawable, and its alpha lives in
        // that shared state. Without this, one blanked tick blanks the rest of the list -- and
        // ic_done elsewhere in the app with it.
        final Drawable tick =
                BlendModeUtil.getTintedDrawable(themed, R.drawable.ic_done, fontColor(themed))
                        .mutate();
        tick.setAlpha(checked ? 255 : 0);
        name.setCompoundDrawablesRelativeWithIntrinsicBounds(tick, null, null, null);
        name.setCompoundDrawablePadding(
                Math.round(8 * themed.getResources().getDisplayMetrics().density));

        list.addView(row);
        return row;
    }

    private static int fontColor(Context themed) {
        final TypedArray ta = themed.obtainStyledAttributes(new int[] {R.attr.fontColor});
        final int color = ta.getColor(0, Color.WHITE);
        ta.recycle();
        return color;
    }

    /** What a user tag's pencil offers. */
    private static void showEditDialog(
            final Context context,
            final String tag,
            final PendingFilter pending,
            final Runnable[] rebuild) {

        new MaterialAlertDialogBuilder(themed(context))
                .setTitle(tag)
                .setItems(
                        new CharSequence[] {
                            context.getString(R.string.tag_rename),
                            context.getString(R.string.btn_delete)
                        },
                        (d, which) -> {
                            if (which == 0) {
                                promptForName(
                                        context,
                                        tag,
                                        name -> {
                                            // The tab may be filtered by the name that just
                                            // changed, so the filter follows it -- but only on the
                                            // way out, so the feed does not reload underneath.
                                            pending.renamed(tag, name);
                                            SavedTagStore.renameTag(tag, name);
                                            rebuild[0].run();
                                        });
                            } else {
                                confirmDelete(context, tag, pending, rebuild);
                            }
                        })
                .show();
    }

    private static void confirmDelete(
            final Context context,
            final String tag,
            final PendingFilter pending,
            final Runnable[] rebuild) {

        new MaterialAlertDialogBuilder(themed(context))
                .setTitle(R.string.btn_delete)
                .setMessage(context.getString(R.string.tag_delete_confirm, tag))
                .setPositiveButton(
                        R.string.btn_delete,
                        (d, which) -> {
                            // Filtering by a tag that no longer exists would show an empty tab, so
                            // it leaves the selection -- again, only once the dialog closes.
                            pending.deleted(tag);
                            SavedTagStore.deleteTag(tag);
                            rebuild[0].run();
                        })
                .setNegativeButton(R.string.btn_cancel, null)
                .show();
    }

    /**
     * The tag picker for one saved item. Multi-select, because an item can carry several tags;
     * unticking everything is how it becomes untagged.
     *
     * <p>Built-in tags are not offered -- they are derived from what the item is, not something to
     * assign. With no tags yet there is nothing to tick, so this goes straight to creating one.
     */
    public static void showTagPicker(
            final Context context, final Contribution item, final @Nullable OnChanged onChanged) {
        final String fullname = item.getFullName();
        if (fullname == null || fullname.isEmpty()) {
            return;
        }
        showTagPicker(context, item, fullname, null, onChanged);
    }

    /**
     * @param preselected ticks to restore, carried across a "New tag" round trip so the choices
     *     made before creating one are not lost; {@code null} to read them from the store
     */
    private static void showTagPicker(
            final Context context,
            final Contribution item,
            final String fullname,
            final @Nullable Set<String> preselected,
            final @Nullable OnChanged onChanged) {

        final List<String> names = SavedTagStore.getTagNames();
        final Set<String> ticked =
                preselected != null
                        ? preselected
                        : new LinkedHashSet<>(SavedTagStore.tagsFor(fullname));

        if (names.isEmpty()) {
            // Nothing to choose from yet. Creating the first tag applies it immediately, which is
            // the only thing the user could have wanted from an empty picker.
            promptForName(
                    context,
                    null,
                    name -> {
                        final Set<String> wanted = new LinkedHashSet<>(ticked);
                        wanted.add(SavedTagStore.createTag(name));
                        SavedTagStore.setTagsFor(fullname, wanted);
                        if (onChanged != null) {
                            onChanged.onChanged();
                        }
                    });
            return;
        }

        final CharSequence[] rows = names.toArray(new CharSequence[0]);
        final boolean[] chosen = new boolean[names.size()];
        for (int i = 0; i < names.size(); i++) {
            chosen[i] = ticked.contains(names.get(i));
        }

        new MaterialAlertDialogBuilder(themed(context))
                .setTitle(R.string.profile_tag_select)
                .setMultiChoiceItems(
                        rows, chosen, (dialog, which, isChecked) -> chosen[which] = isChecked)
                .setPositiveButton(
                        R.string.btn_ok,
                        (dialog, which) -> {
                            if (SavedTagStore.setTagsFor(fullname, checkedNames(names, chosen))
                                    && onChanged != null) {
                                onChanged.onChanged();
                            }
                        })
                .setNeutralButton(
                        R.string.tag_new,
                        (dialog, which) ->
                                promptForName(
                                        context,
                                        null,
                                        name -> {
                                            // Keep what was already ticked, add the new tag, and
                                            // come back to the picker rather than committing here.
                                            final Set<String> carried =
                                                    checkedNames(names, chosen);
                                            carried.add(SavedTagStore.createTag(name));
                                            showTagPicker(
                                                    context, item, fullname, carried, onChanged);
                                        }))
                .setNegativeButton(R.string.btn_cancel, null)
                .show();
    }

    /** The tag names ticked in the picker. */
    private static Set<String> checkedNames(List<String> names, boolean[] chosen) {
        final Set<String> checked = new LinkedHashSet<>();
        for (int i = 0; i < names.size(); i++) {
            if (chosen[i]) {
                checked.add(names.get(i));
            }
        }
        return checked;
    }
}
