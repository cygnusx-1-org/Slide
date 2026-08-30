package me.edgan.redditslide;

import android.content.Context;
import androidx.annotation.Nullable;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import me.edgan.redditslide.util.LogUtil;
import me.edgan.redditslide.util.MiscUtil;
import me.edgan.redditslide.util.PrefUtil;
import net.dean.jraw.models.CommentSort;
import net.dean.jraw.models.Submission;
import net.dean.jraw.models.meta.SubmissionSerializer;

/** Created by carlo_000 on 11/19/2015. */
public class OfflineSubreddit {

    public static Long currentid = 0L;
    private static String savedSubmissionsSubreddit = "";
    public long time;
    // Built by every factory method here, but the bare constructor those factories call leaves it
    // unset for a moment, and the callers below have always guarded it.
    public ArrayList<Submission> submissions = new ArrayList<>();

    /** Null on a bare OfflineSubreddit, before one of the factory methods has named it. */
    @Nullable public String subreddit;

    public boolean base;

    public static void writeSubmission(JsonNode node, Submission s, @Nullable Context c) {
        writeSubmissionToStorage(s, node, c);
    }

    /** Resolved on first use, so null until then. */
    @Nullable static File cacheDirectory;

    /**
     * Directory holding the cached submission blobs, or {@code null} if no Context is available.
     * Callers must null-check the result instead of concatenating it into a path — a null here
     * used to stringify into paths like "null/t3_abc" that resolve against the process working
     * directory, silently reading and writing the wrong place.
     */
    @Nullable
    public static File getCacheDirectory(@Nullable Context context) {
        if (cacheDirectory == null) {
            // Callers thread a Context down from activities and adapters, so it can arrive null;
            // the application context is an equivalent source for the cache dir.
            Context resolved = context != null ? context : Reddit.getAppContext();
            if (resolved != null) {
                // Internal cache only. An external-cache branch used to sit here, but it was
                // dead code (the internal assignment overwrote it unconditionally), so external
                // storage has never actually held this data. Keep it that way: these files are
                // cached post bodies, and internal cache keeps them out of other apps' reach and
                // lets the system reclaim them.
                cacheDirectory = resolved.getCacheDir();
            }
        }
        return cacheDirectory;
    }

    /**
     * Cache blob for {@code name}, or {@code null} when there is no cache directory to use or no
     * name to use. A null name has to be rejected here: {@code new File(dir, null)} throws, where
     * the string concatenation this replaced quietly cached every such submission on top of one
     * shared file literally called "null".
     */
    @Nullable
    private static File cacheFile(@Nullable String name, @Nullable Context c) {
        File dir = getCacheDirectory(c);
        return (dir == null || name == null) ? null : new File(dir, name);
    }

    public OfflineSubreddit overwriteSubmissions(List<Submission> data) {
        submissions = new ArrayList<>(data);
        return this;
    }

    public static void writeSubmissionToStorage(Submission s, JsonNode node, @Nullable Context c) {
        File toStore = cacheFile(s.getFullName(), c);
        if (toStore == null) {
            LogUtil.e("OfflineSubreddit.writeSubmissionToStorage skipped, no cache file");
            return;
        }
        try {
            FileWriter writer = new FileWriter(toStore);
            writer.append(node.toString());
            writer.flush();
            writer.close();
        } catch (IOException e) {
            LogUtil.e(e, "OfflineSubreddit.writeSubmissionToStorage failed");
        }
    }

    public boolean isStored(String name, @Nullable Context c) {
        File f = cacheFile(name, c);
        return f != null && f.exists();
    }

    public void writeToMemory(@Nullable Context c) {
        if (subreddit != null) {
            String title = subreddit.toLowerCase(Locale.ENGLISH) + "," + (base ? 0 : time);
            StringBuilder fullNames = new StringBuilder();
            cache.put(title, this);
            for (Submission sub : new ArrayList<>(submissions)) {
                fullNames.append(sub.getFullName()).append(",");
                if (!isStored(MiscUtil.orEmpty(sub.getFullName()), c)) {
                    writeSubmissionToStorage(sub, sub.getDataNode(), c);
                }
            }

            if (fullNames.length() > 0) {
                Reddit.cachedData
                        .edit()
                        .putString(title, fullNames.substring(0, fullNames.length() - 1))
                        .apply();
            }

            cache.put(title, this);
        }
    }

    public void writeToMemoryNoStorage() {
        if (subreddit != null) {
            String title = subreddit.toLowerCase(Locale.ENGLISH) + "," + (base ? 0 : time);
            StringBuilder fullNames = new StringBuilder();
            for (Submission sub : submissions) {
                fullNames.append(sub.getFullName()).append(",");
            }
            if (fullNames.length() > 0) {
                Reddit.cachedData
                        .edit()
                        .putString(title, fullNames.substring(0, fullNames.length() - 1))
                        .apply();
            }
            cache.put(title, this);
        }
    }

    public void writeToMemory(ArrayList<String> names) {
        if (subreddit != null && !names.isEmpty()) {
            String title = subreddit.toLowerCase(Locale.ENGLISH) + "," + (time);
            StringBuilder fullNamesBuilder = new StringBuilder();
            for (String sub : names) {
                fullNamesBuilder.append(sub).append(",");
            }
            String fullNames = fullNamesBuilder.toString();
            if (subreddit.equals(CommentCacheAsync.SAVED_SUBMISSIONS)) {
                Map<String, ?> offlineSubs = Reddit.cachedData.getAll();
                for (String offlineSub : offlineSubs.keySet()) {
                    if (offlineSub.contains(CommentCacheAsync.SAVED_SUBMISSIONS)) {
                        savedSubmissionsSubreddit = offlineSub;
                        break;
                    }
                }
                String savedSubmissions =
                        PrefUtil.getString(
                                Reddit.cachedData,
                                OfflineSubreddit.savedSubmissionsSubreddit,
                                fullNames);
                Reddit.cachedData.edit().remove(savedSubmissionsSubreddit).apply();
                if (!savedSubmissions.equals(fullNames)) {
                    savedSubmissions = fullNames + savedSubmissions;
                }
                saveToCache(title, savedSubmissions);
            } else {
                saveToCache(title, fullNames);
            }
        }
    }

    public void deleteFromMemory(String name) {
        if (subreddit != null) {
            String title = subreddit.toLowerCase(Locale.ENGLISH) + "," + (time);
            if (subreddit.equals(CommentCacheAsync.SAVED_SUBMISSIONS)) {
                Map<String, ?> offlineSubs = Reddit.cachedData.getAll();
                for (String offlineSub : offlineSubs.keySet()) {
                    if (offlineSub.contains(CommentCacheAsync.SAVED_SUBMISSIONS)) {
                        savedSubmissionsSubreddit = offlineSub;
                        break;
                    }
                }
                String savedSubmissions =
                        PrefUtil.getString(
                                Reddit.cachedData, OfflineSubreddit.savedSubmissionsSubreddit, name);
                if (!savedSubmissions.equals(name)) {
                    Reddit.cachedData.edit().remove(savedSubmissionsSubreddit).apply();
                    String modifiedSavedSubmissions = savedSubmissions.replace(name + ",", "");
                    saveToCache(title, modifiedSavedSubmissions);
                }
            }
        }
    }

    private void saveToCache(String title, String submissions) {
        Reddit.cachedData.edit().putString(title, submissions).apply();
    }

    public static OfflineSubreddit getSubreddit(
            @Nullable String subreddit, boolean offline, @Nullable Context c) {
        return getSubreddit(subreddit, 0L, offline, c);
    }

    public static OfflineSubreddit getSubNoLoad(String s) {
        s = s.toLowerCase(Locale.ENGLISH);

        OfflineSubreddit o = new OfflineSubreddit();
        o.subreddit = s.toLowerCase(Locale.ENGLISH);
        o.base = true;
        o.time = 0;
        o.submissions = new ArrayList<>();
        return o;
    }

    private static final HashMap<String, OfflineSubreddit> cache = new HashMap<>();

    public static OfflineSubreddit getSubreddit(
            @Nullable String subreddit, Long time, boolean offline, @Nullable Context c) {
        String title;
        if (subreddit != null) {
            title = subreddit.toLowerCase(Locale.ENGLISH) + "," + time;
        } else {
            title = "";
            subreddit = "";
        }
        if (cache.containsKey(title)) {
            return cache.get(title);
        } else {
            subreddit = subreddit.toLowerCase(Locale.ENGLISH);

            OfflineSubreddit o = new OfflineSubreddit();
            o.subreddit = subreddit.toLowerCase(Locale.ENGLISH);

            if (time == 0) {
                o.base = true;
            }

            o.time = time;

            String[] split =
                    PrefUtil.getString(
                                    Reddit.cachedData,
                                    subreddit.toLowerCase(Locale.ENGLISH) + "," + time,
                                    "")
                            .split(",");
            if (split.length > 0) {
                o.time = time;
                o.submissions = new ArrayList<>();
                ObjectMapper mapperBase = new ObjectMapper();
                ObjectReader reader = mapperBase.reader();
                // Resolved once, not per post: it is the same listing throughout, and the loop
                // runs a hundred times on a feed rebuild -- each iteration would repeat a
                // preference lookup and an enum parse that cannot have changed since the last.
                final CommentSort sort = SettingValues.getCommentSorting(subreddit);

                for (String s : split) {
                    if (!s.contains("_")) s = "t3_" + s;
                    if (!s.isEmpty()) {
                        try {
                            Submission sub = getSubmissionFromStorage(s, c, offline, reader, sort);
                            if (sub != null) {
                                o.submissions.add(sub);
                            }
                        } catch (IOException e) {
                            LogUtil.e(e, "OfflineSubreddit.getSubreddit failed");
                        }
                    }
                }

            } else {
                o.submissions = new ArrayList<>();
            }
            cache.put(title, o);
            return o;
        }
    }

    /**
     * Null when the blob is missing or holds no submission data.
     *
     * @param sort how the comments in the blob are ordered, for a blob that holds a whole thread.
     *     It is not used to sort anything -- the tree keeps the order it was written in -- but it
     *     is the sort {@code CommentNode} sends to the {@code morechildren} endpoint when the user
     *     expands a collapsed branch, so a wrong one here means the extra replies come back
     *     ordered differently from the ones around them. There is no sensible default: it belongs
     *     to whoever is about to display the thread, which is why it is a parameter rather than a
     *     constant.
     */
    @Nullable
    public static Submission getSubmissionFromStorage(
            String fullName,
            @Nullable Context c,
            boolean offline,
            ObjectReader reader,
            CommentSort sort)
            throws IOException {
        String gotten = getStringFromFile(fullName, c);
        if (!gotten.isEmpty()) {
            if (gotten.startsWith("[") && offline) {
                return (SubmissionSerializer.withComments(reader.readTree(gotten), sort));
            } else if (gotten.startsWith("[")) {
                JsonNode elem = reader.readTree(gotten);
                JsonNode first = elem != null ? elem.get(0) : null;
                JsonNode data = first != null ? first.get("data") : null;
                JsonNode children = data != null ? data.get("children") : null;
                JsonNode firstChild = children != null ? children.get(0) : null;
                JsonNode submissionData = firstChild != null ? firstChild.get("data") : null;
                if (submissionData != null) {
                    return (new Submission(submissionData));
                }
                return null;
            } else {
                return (new Submission(reader.readTree(gotten)));
            }
        }
        return null;
    }

    public static String getStringFromFile(String name, @Nullable Context c) {
        File f = cacheFile(name, c);
        if (f != null && f.exists()) {
            try {
                BufferedReader reader = new BufferedReader(new FileReader(f));
                char[] chars = new char[(int) f.length()];
                reader.read(chars);
                reader.close();
                return new String(chars);
            } catch (IOException e) {
                LogUtil.e(e, "OfflineSubreddit.getStringFromFile failed");
            }
        } else {
            return "";
        }
        return "";
    }

    public static OfflineSubreddit newSubreddit(String subreddit) {
        subreddit = subreddit.toLowerCase(Locale.ENGLISH);

        OfflineSubreddit o = new OfflineSubreddit();
        o.subreddit = subreddit.toLowerCase(Locale.ENGLISH);
        o.base = false;
        o.time = System.currentTimeMillis();
        o.submissions = new ArrayList<>();

        return o;
    }

    public void clearPost(Submission s) {
        Submission toRemove = null;
        for (Submission s2 : submissions) {
            if (MiscUtil.orEmpty(s.getFullName()).equals(MiscUtil.orEmpty(s2.getFullName()))) {
                toRemove = s2;
            }
        }
        if (toRemove != null) {
            submissions.remove(toRemove);
        }
    }

    int savedIndex;

    /** The post hide() last removed, so unhideLast() can put it back. Null until one is hidden. */
    @Nullable Submission savedSubmission;

    public void hide(int index) {
        hide(index, true);
    }

    public void hideMulti(int index) {
        if (index >= 0 && index < submissions.size()) {
            submissions.remove(index);
        }
    }

    public void hide(int index, boolean save) {
        savedSubmission = submissions.get(index);
        submissions.remove(index);
        savedIndex = index;
        writeToMemoryNoStorage();
    }

    public void unhideLast() {
        if (savedSubmission != null) {
            submissions.add(savedIndex, savedSubmission);
            writeToMemoryNoStorage();
        }
    }

    public static ArrayList<String> getAll(@Nullable String subreddit) {
        // No subreddit name means no cached keys are prefixed with one, so the scan below would
        // match nothing anyway.
        if (subreddit == null) {
            return new ArrayList<>();
        }
        subreddit = subreddit.toLowerCase(Locale.ENGLISH);
        ArrayList<String> base = new ArrayList<>();
        for (String s : Reddit.cachedData.getAll().keySet()) {
            if (s.startsWith(subreddit) && s.contains(",")) {
                base.add(s);
            }
        }

        Collections.sort(base, new MultiComparator());
        return base;
    }

    public static class MultiComparator implements Comparator<String> {
        @Override
        public int compare(String o1, String o2) {
            double first = Double.parseDouble(o1.split(",")[1]);
            double second = Double.parseDouble(o2.split(",")[1]);
            return Double.compare(second, first); // Simplified comparison
        }
    }

    public static ArrayList<String> getAll() {
        ArrayList<String> keys = new ArrayList<>();
        for (String s : Reddit.cachedData.getAll().keySet()) {
            if (s.contains(",") && !s.startsWith("multi_")) {
                keys.add(s);
            }
        }
        return keys;
    }

    public static ArrayList<String> getAllFormatted() {
        ArrayList<String> keys = new ArrayList<>();
        for (String s : Reddit.cachedData.getAll().keySet()) {
            if (s.contains(",")
                    && !keys.contains(s.substring(0, s.indexOf(",")))
                    && !s.startsWith("multi_")) {
                keys.add(s.substring(0, s.indexOf(",")));
            }
        }
        return keys;
    }
}
