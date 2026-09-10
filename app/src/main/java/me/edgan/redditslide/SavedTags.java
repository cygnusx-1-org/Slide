package me.edgan.redditslide;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import net.dean.jraw.models.Contribution;
import net.dean.jraw.models.Submission;

import org.jspecify.annotations.Nullable;

/**
 * The logic behind saved tags, with no Android dependencies so it can be unit tested directly.
 *
 * <p>Reddit's own saved-categories feature is gone -- it was a Premium perk, Reddit removed it, and
 * neither {@code /api/saved_categories} nor the {@code category} parameter on {@code /api/save}
 * does anything useful now. Slide keeps the labels itself instead: saving still goes through the
 * normal save API, and which tags an item carries is stored locally by {@link SavedTagStore}.
 *
 * <p>Because Slide owns the model it is no longer limited to one label per item, which is why these
 * are tags rather than categories. An item can carry several.
 *
 * <p>Three tags are {@linkplain Builtin built in}. They are derived from the saved listing rather
 * than stored, so they cannot drift out of sync, cannot be renamed or deleted, and are present on a
 * brand-new install. Their display names are string resources, so this class works in terms of the
 * enum and takes the resolved labels from the caller wherever it needs them.
 */
public final class SavedTags {

    /** Shortest a user tag's name may be once trimmed. */
    public static final int MIN_NAME_LENGTH = 3;

    private SavedTags() {}

    /**
     * The tags Slide derives from the saved listing itself rather than storing.
     *
     * <p>Order matters: this is the order they are pinned in ahead of the user's own tags, in every
     * list that shows them. The three structural ones come first, then the content types in
     * alphabetical order of their labels.
     *
     * <p>The content-type entries each stand for one or more {@link ContentType.Type} values and
     * only ever match submissions -- a comment has no content type. Their display names come from
     * the {@code type_*} string resources {@code ContentType} already uses, so a saved item's tag
     * reads the same as the label on its card.
     *
     * <p>Three {@code ContentType.Type} values are deliberately left without a tag, because no
     * saved submission can carry them:
     *
     * <ul>
     *   <li>{@code SPOILER} is reddit's inline-spoiler markdown link ({@code [x](#s)}), so it only
     *       ever types a link inside a body. It is unrelated to reddit's {@code spoiler} flag on a
     *       submission, which coexists with a real content type rather than replacing it.
     *   <li>{@code EXTERNAL} depends on the user's always-open-externally domain list, so the same
     *       post moves in and out of it as that setting is edited; a tag on it would not be stable.
     *   <li>{@code NONE} needs a submission with no url, which reddit does not serve.
     * </ul>
     */
    public enum Builtin {
        /** Every saved item -- the unfiltered listing. */
        ALL,
        /** Saved submissions. */
        POSTS,
        /** Saved comments. */
        COMMENTS,
        ALBUM(ContentType.Type.ALBUM),
        DEVIANTART(ContentType.Type.DEVIANTART),
        GALLERY(ContentType.Type.REDDIT_GALLERY),
        GIF(ContentType.Type.GIF),
        IMAGE(ContentType.Type.IMAGE),
        IMGUR(ContentType.Type.IMGUR),
        LINK(ContentType.Type.LINK),
        REDDIT_LINK(ContentType.Type.REDDIT),
        /** Both v.redd.it shapes, which are one thing to the user. */
        REDDIT_VIDEO(ContentType.Type.VREDDIT_DIRECT, ContentType.Type.VREDDIT_REDIRECT),
        SELFTEXT(ContentType.Type.SELF),
        STREAMABLE(ContentType.Type.STREAMABLE),
        TUMBLR(ContentType.Type.TUMBLR),
        XKCD(ContentType.Type.XKCD),
        /** {@code ContentType.Type.VIDEO}, which the cards label YouTube. */
        YOUTUBE(ContentType.Type.VIDEO);

        private final Set<ContentType.Type> types;

        Builtin(ContentType.Type... types) {
            this.types =
                    types.length == 0
                            ? Collections.emptySet()
                            : Collections.unmodifiableSet(
                                    new HashSet<>(Arrays.asList(types)));
        }

        /** Whether this tag selects by content type rather than by item kind. */
        public boolean isContentType() {
            return !types.isEmpty();
        }

        /** Whether this tag covers {@code type}. */
        public boolean covers(ContentType.Type type) {
            return types.contains(type);
        }

        /**
         * Whether this tag selects by item kind -- a post or a comment -- rather than by content
         * type. {@link #ALL} is neither: it is the absence of a filter.
         */
        public boolean isStructural() {
            return this != ALL && !isContentType();
        }
    }

    /** Whether {@code contribution} belongs under {@code builtin}. */
    public static boolean matches(Builtin builtin, Contribution contribution) {
        if (builtin.isContentType()) {
            // Only a submission has a content type; a saved comment is never a GIF.
            return contribution instanceof Submission
                    && builtin.covers(ContentType.getContentType((Submission) contribution));
        }
        switch (builtin) {
            case POSTS:
                return contribution instanceof Submission;
            case COMMENTS:
                // Deliberately the negative case rather than a test for Comment: whatever else
                // Reddit ever puts in the saved listing still lands somewhere instead of vanishing
                // from both Posts and Comments.
                return !(contribution instanceof Submission);
            case ALL:
            default:
                return true;
        }
    }

    /**
     * Whether {@code contribution} passes a selection of built-in tags.
     *
     * <p>The two groups compose differently, which is the whole point of splitting them into
     * Types and Post types in the picker:
     *
     * <ul>
     *   <li>Post types <b>narrow</b> Posts rather than adding to it. Selecting Posts together with
     *       GIF and Image shows GIFs and images, not every post -- picking a type is a statement
     *       about which posts are wanted, so honouring Posts as well would make the types
     *       pointless. Picking a type without Posts means the same thing.
     *   <li>Comments is independent of both, because a comment has no content type. Comments with
     *       GIF is every comment plus every GIF post.
     * </ul>
     *
     * <p>An empty selection matches nothing; the caller decides that no filter at all means
     * everything. {@link Builtin#ALL} anywhere in the selection matches everything.
     */
    public static boolean matchesAny(Collection<Builtin> builtins, Contribution contribution) {
        if (builtins.isEmpty()) {
            return false;
        }
        if (builtins.contains(Builtin.ALL)) {
            return true;
        }
        if (!(contribution instanceof Submission)) {
            return builtins.contains(Builtin.COMMENTS);
        }
        boolean anyContentType = false;
        for (Builtin builtin : builtins) {
            if (builtin.isContentType()) {
                anyContentType = true;
                break;
            }
        }
        if (!anyContentType) {
            return builtins.contains(Builtin.POSTS);
        }
        final ContentType.Type type = ContentType.getContentType((Submission) contribution);
        for (Builtin builtin : builtins) {
            if (builtin.isContentType() && builtin.covers(type)) {
                return true;
            }
        }
        return false;
    }

    /** Why {@link #validate} rejected a name, or {@link #OK} if it did not. */
    public enum Validation {
        OK,
        /** Fewer than {@link #MIN_NAME_LENGTH} characters once trimmed. */
        TOO_SHORT,
        /** Case-insensitively equal to a user tag that already exists. */
        DUPLICATE,
        /** Case-insensitively equal to a built-in tag's name. */
        RESERVED
    }

    /**
     * Check a proposed tag name.
     *
     * @param candidate what the user typed; trimmed here, so callers need not
     * @param existing the user's current tag names
     * @param renamingFrom the name being renamed, excluded from the duplicate check so that
     *     re-confirming a tag's own name is not a collision; {@code null} when creating
     * @param reserved the built-in display names, which a user tag may not shadow
     */
    public static Validation validate(
            String candidate,
            Collection<String> existing,
            @Nullable String renamingFrom,
            Collection<String> reserved) {
        final String name = candidate.trim();
        if (name.length() < MIN_NAME_LENGTH) {
            return Validation.TOO_SHORT;
        }
        final Collator collator = collator();
        for (String builtin : reserved) {
            if (equal(collator, name, builtin)) {
                return Validation.RESERVED;
            }
        }
        for (String other : existing) {
            if (renamingFrom != null && equal(collator, other, renamingFrom)) {
                continue;
            }
            if (equal(collator, name, other)) {
                return Validation.DUPLICATE;
            }
        }
        return Validation.OK;
    }

    /**
     * The spelling of {@code candidate} as it is actually stored, or {@code null} when no tag
     * matches it. Lets a lookup be case-insensitive without losing the casing the user chose.
     */
    public static @Nullable String findName(Collection<String> names, String candidate) {
        final String name = candidate.trim();
        final Collator collator = collator();
        for (String stored : names) {
            if (equal(collator, name, stored)) {
                return stored;
            }
        }
        return null;
    }

    /** {@code names} sorted for display: locale-aware and case-insensitive. */
    public static List<String> sort(Collection<String> names) {
        final List<String> sorted = new ArrayList<>(names);
        final Collator collator = collator();
        Collections.sort(sorted, collator::compare);
        return sorted;
    }

    /**
     * Drop repeated items from a saved listing, keeping the first occurrence and the original
     * order. Reddit can return the same item twice inside a single response.
     *
     * <p>Returns {@code items} itself when there was nothing to drop, so the overwhelmingly common
     * case costs one pass and no allocation.
     */
    public static ArrayList<Contribution> dedupe(ArrayList<Contribution> items) {
        final Set<String> seen = new HashSet<>();
        boolean duplicated = false;
        for (Contribution item : items) {
            if (!seen.add(keyOf(item))) {
                duplicated = true;
                break;
            }
        }
        if (!duplicated) {
            return items;
        }

        seen.clear();
        final ArrayList<Contribution> deduped = new ArrayList<>(items.size());
        for (Contribution item : items) {
            if (seen.add(keyOf(item))) {
                deduped.add(item);
            }
        }
        return deduped;
    }

    /**
     * What identifies a saved item. The fullname when there is one; otherwise the kind and id
     * together, because an id alone is only unique within a kind.
     */
    private static String keyOf(Contribution item) {
        final String fullname = item.getFullName();
        if (fullname != null && !fullname.isEmpty()) {
            return fullname;
        }
        return item.getClass().getSimpleName() + "_" + item.getId();
    }

    /**
     * A collator at {@code SECONDARY} strength: accents distinguish, case does not.
     *
     * <p>Not {@code String.CASE_INSENSITIVE_ORDER} and not {@code toLowerCase()} -- both get
     * Turkish dotless i wrong, in the sort and in the duplicate comparison alike. Built per call
     * because {@link Collator} is not thread-safe.
     */
    private static Collator collator() {
        final Collator collator = Collator.getInstance();
        collator.setStrength(Collator.SECONDARY);
        return collator;
    }

    private static boolean equal(Collator collator, String a, String b) {
        return collator.compare(a.trim(), b.trim()) == 0;
    }
}
