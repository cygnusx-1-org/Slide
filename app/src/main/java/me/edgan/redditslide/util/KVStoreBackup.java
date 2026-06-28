package me.edgan.redditslide.util;

import com.lusfold.androidkeyvaluestore.KVStore;

import java.util.Map;

import me.edgan.redditslide.SubmissionViews.LocalSaved;
import me.edgan.redditslide.SubmissionViews.ReadLater;

/**
 * Serializes the small, user-meaningful {@link KVStore} collections so settings backup/restore can
 * carry them. The settings backup only copies the {@code shared_prefs} directory, but Read Later
 * and Local Saved live in the KVStore SQLite database, so they are otherwise lost on restore.
 *
 * <p>Only the whitelisted prefixes below are included. HasSeen / LastComments are intentionally
 * excluded -- they are large and not worth syncing.
 *
 * <p>The serialized form is {@code key\tvalue} lines. It deliberately contains neither the
 * {@code <START} / {@code END>} markers the backup file uses as delimiters nor tabs/newlines in
 * any key or value (keys are fixed prefixes + Reddit fullnames, values are timestamps), so it can
 * be embedded as a single backup entry without escaping.
 */
public class KVStoreBackup {

    /** Backup-entry name used to tag the serialized KVStore blob inside the backup file. */
    public static final String SENTINEL = "__kvstore_backup__";

    private static final String[] PREFIXES = {
        ReadLater.PREFIX, LocalSaved.PROMOTED_PREFIX, LocalSaved.PENDING_PREFIX
    };

    /** @return the whitelisted KVStore entries as {@code key\tvalue} lines, or "" if there are none. */
    public static String export() {
        StringBuilder sb = new StringBuilder();
        try {
            for (String prefix : PREFIXES) {
                Map<String, String> entries = KVStore.getInstance().getByPrefix(prefix);
                if (entries == null) continue;
                for (Map.Entry<String, String> e : entries.entrySet()) {
                    String key = e.getKey();
                    String value = e.getValue();
                    if (key == null || value == null) continue;
                    // getByPrefix uses SQL LIKE ('_' is a wildcard), so e.g. "localsaved_%" also
                    // matches "localsavedpending_..."; keep only literal-prefix matches so entries
                    // aren't double-listed across prefixes.
                    if (!key.startsWith(prefix)) continue;
                    // Keep the line format unambiguous; skip anything with our separators.
                    if (key.indexOf('\t') >= 0
                            || key.indexOf('\n') >= 0
                            || value.indexOf('\t') >= 0
                            || value.indexOf('\n') >= 0) {
                        continue;
                    }
                    sb.append(key).append('\t').append(value).append('\n');
                }
            }
        } catch (Exception e) {
            LogUtil.e(e, "KVStoreBackup.export failed");
        }
        return sb.toString();
    }

    /** Re-inserts entries produced by {@link #export()} back into KVStore. */
    public static void restore(String data) {
        if (data == null || data.isEmpty()) return;
        try {
            for (String line : data.split("\n")) {
                int tab = line.indexOf('\t');
                if (tab <= 0) continue;
                String key = line.substring(0, tab);
                String value = line.substring(tab + 1);
                if (!key.isEmpty()) {
                    KVStore.getInstance().insertOrUpdate(key, value);
                }
            }
        } catch (Exception e) {
            LogUtil.e(e, "KVStoreBackup.restore failed");
        }
    }
}
