package me.edgan.redditslide;

import android.content.Context;
import android.content.SharedPreferences;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import me.edgan.redditslide.util.LogUtil;

import org.jspecify.annotations.Nullable;

/**
 * Where a saved item's tags live. See {@link SavedTags} for why they are Slide's to keep.
 *
 * <p>One {@code SharedPreferences} entry per account, keyed by {@link Authentication#nameOrEmpty()}
 * the same way {@link SavedPostCache} keys its blobs, so one account never sees another's tags. The
 * value is a JSON object mapping each tag name to the fullnames carrying it:
 *
 * <pre>{"Recipes": ["t3_abc123", "t1_def456"], "To try": ["t3_abc123"], "Woodworking": []}</pre>
 *
 * <p>A tag with an empty array is a real tag -- that is what creating one before you have anything
 * to put in it means. A fullname under two tags is the point: membership is many-to-many. Fullnames
 * keep their kind prefix, so submissions and comments share one set unambiguously.
 *
 * <p>The parsed map is held in memory and every mutation is a read-modify-write of it, so all
 * public methods synchronize on this class -- two rapid writes, such as the picker committing
 * several memberships at once, cannot interleave and lose one. The blob itself goes out through
 * {@code apply()}, which is already off-thread, so no caller waits on disk.
 */
public final class SavedTagStore {

    private static final String PREFS = "savedtags";

    /** Shared across threads; {@link ObjectMapper} is thread-safe once configured. */
    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** The account {@link #tags} was loaded for, so an account switch reloads rather than leaks. */
    private static @Nullable String loadedFor;

    /** Tag name to the fullnames carrying it. Insertion-ordered; callers sort for display. */
    private static Map<String, Set<String>> tags = new LinkedHashMap<>();

    private SavedTagStore() {}

    private static SharedPreferences prefs() {
        return Reddit.getAppContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    /** Parse this account's blob into {@link #tags}, unless it is already the one in memory. */
    private static void ensureLoaded() {
        final String account = Authentication.nameOrEmpty();
        if (account.equals(loadedFor)) {
            return;
        }
        final Map<String, Set<String>> parsed = new LinkedHashMap<>();
        try {
            final String blob = prefs().getString(account, null);
            if (blob != null && !blob.isEmpty()) {
                final JsonNode root = MAPPER.readTree(blob);
                if (root != null && root.isObject()) {
                    final Iterator<String> names = root.fieldNames();
                    while (names.hasNext()) {
                        final String name = names.next();
                        final Set<String> fullnames = new LinkedHashSet<>();
                        for (JsonNode fullname : root.path(name)) {
                            final String text = fullname.asText("");
                            if (!text.isEmpty()) {
                                fullnames.add(text);
                            }
                        }
                        parsed.put(name, fullnames);
                    }
                }
            }
        } catch (Exception e) {
            // A corrupt blob is not worth losing the session over; start this account empty.
            LogUtil.e(e, "SavedTagStore.ensureLoaded failed");
            parsed.clear();
        }
        tags = parsed;
        loadedFor = account;
    }

    /** Queue a write of the in-memory map for the account it belongs to. */
    private static void persist() {
        final String account = loadedFor == null ? Authentication.nameOrEmpty() : loadedFor;
        final ObjectNode root = MAPPER.createObjectNode();
        for (Map.Entry<String, Set<String>> tag : tags.entrySet()) {
            final ArrayNode fullnames = root.putArray(tag.getKey());
            for (String fullname : tag.getValue()) {
                fullnames.add(fullname);
            }
        }
        prefs().edit().putString(account, root.toString()).apply();
    }

    /** Every user tag name this account has, in the order to display them. */
    public static synchronized List<String> getTagNames() {
        ensureLoaded();
        return SavedTags.sort(tags.keySet());
    }

    /** Whether this account has any user tags at all. */
    public static synchronized boolean isEmpty() {
        ensureLoaded();
        return tags.isEmpty();
    }

    /** The fullnames carrying {@code tag}, or an empty set when there is no such tag. */
    public static synchronized Set<String> fullnamesIn(String tag) {
        ensureLoaded();
        final String stored = SavedTags.findName(tags.keySet(), tag);
        if (stored == null) {
            return Collections.emptySet();
        }
        final Set<String> fullnames = tags.get(stored);
        return fullnames == null ? Collections.emptySet() : new LinkedHashSet<>(fullnames);
    }

    /**
     * The fullnames carrying any of {@code names}, as one set. Several tags can be filtered by at
     * once, and an item under two of them belongs in the result once.
     */
    public static synchronized Set<String> fullnamesInAny(Collection<String> names) {
        ensureLoaded();
        final Set<String> union = new LinkedHashSet<>();
        for (String name : names) {
            final String stored = SavedTags.findName(tags.keySet(), name);
            if (stored == null) {
                continue;
            }
            final Set<String> fullnames = tags.get(stored);
            if (fullnames != null) {
                union.addAll(fullnames);
            }
        }
        return union;
    }

    /** The tags {@code fullname} carries, sorted for display. */
    public static synchronized List<String> tagsFor(String fullname) {
        ensureLoaded();
        final List<String> carrying = new ArrayList<>();
        for (Map.Entry<String, Set<String>> tag : tags.entrySet()) {
            if (tag.getValue().contains(fullname)) {
                carrying.add(tag.getKey());
            }
        }
        return SavedTags.sort(carrying);
    }

    /**
     * Replace the set of tags {@code fullname} carries. Anything it was tagged with and is not in
     * {@code wanted} is removed; an empty {@code wanted} leaves it untagged.
     *
     * @return whether anything actually changed
     */
    public static synchronized boolean setTagsFor(String fullname, Collection<String> wanted) {
        ensureLoaded();
        final Set<String> resolved = new LinkedHashSet<>();
        for (String name : wanted) {
            final String stored = SavedTags.findName(tags.keySet(), name);
            if (stored != null) {
                resolved.add(stored);
            }
        }

        boolean changed = false;
        for (Map.Entry<String, Set<String>> tag : tags.entrySet()) {
            if (resolved.contains(tag.getKey())) {
                changed |= tag.getValue().add(fullname);
            } else {
                changed |= tag.getValue().remove(fullname);
            }
        }
        if (changed) {
            persist();
        }
        return changed;
    }

    /**
     * Create an empty tag.
     *
     * @return the stored name -- the existing spelling when a tag already matches it, so a caller
     *     that goes on to tag an item does not create a near-duplicate
     */
    public static synchronized String createTag(String name) {
        ensureLoaded();
        final String trimmed = name.trim();
        final String stored = SavedTags.findName(tags.keySet(), trimmed);
        if (stored != null) {
            return stored;
        }
        tags.put(trimmed, new LinkedHashSet<>());
        persist();
        return trimmed;
    }

    /**
     * Rename a tag, keeping the items it holds. A no-op when {@code from} does not exist. Renaming
     * onto a name that only differs in case just restyles the existing tag.
     */
    public static synchronized void renameTag(String from, String to) {
        ensureLoaded();
        final String stored = SavedTags.findName(tags.keySet(), from);
        if (stored == null) {
            return;
        }
        final String trimmed = to.trim();
        if (stored.equals(trimmed)) {
            return;
        }
        // Rebuild rather than remove-then-put, so the tag keeps its position in the map and a
        // rename does not silently reorder anything that iterates insertion order.
        final Map<String, Set<String>> rebuilt = new LinkedHashMap<>();
        for (Map.Entry<String, Set<String>> tag : tags.entrySet()) {
            if (tag.getKey().equals(stored)) {
                rebuilt.put(trimmed, tag.getValue());
            } else {
                rebuilt.put(tag.getKey(), tag.getValue());
            }
        }
        tags = rebuilt;
        persist();
    }

    /**
     * Delete a tag. The items that carried it stay saved and simply lose the tag -- deleting a tag
     * never unsaves anything.
     */
    public static synchronized void deleteTag(String name) {
        ensureLoaded();
        final String stored = SavedTags.findName(tags.keySet(), name);
        if (stored == null) {
            return;
        }
        tags.remove(stored);
        persist();
    }

    /**
     * Drop membership of anything not in {@code live}, which must be a complete saved listing.
     * Items unsaved on another client, or dropped by Reddit, would otherwise sit in the store
     * forever.
     *
     * <p>Tags themselves are never removed by this -- a tag emptied by pruning stays until the user
     * deletes it.
     *
     * @return whether anything was pruned
     */
    public static synchronized boolean pruneTo(Set<String> live) {
        ensureLoaded();
        boolean changed = false;
        for (Map.Entry<String, Set<String>> tag : tags.entrySet()) {
            changed |= tag.getValue().retainAll(live);
        }
        if (changed) {
            persist();
        }
        return changed;
    }
}
