package me.edgan.redditslide;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.widget.Toast;
import androidx.annotation.Nullable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import me.edgan.redditslide.Activities.Login;
import me.edgan.redditslide.Activities.MainActivity;
import me.edgan.redditslide.Activities.MultiredditOverview;
import me.edgan.redditslide.Toolbox.Toolbox;
import me.edgan.redditslide.ui.settings.dragSort.ReorderSubreddits;
import me.edgan.redditslide.util.LogUtil;
import me.edgan.redditslide.util.MiscUtil;
import me.edgan.redditslide.util.NetworkUtil;
import me.edgan.redditslide.util.PrefUtil;
import me.edgan.redditslide.util.StringUtil;
import net.dean.jraw.ApiException;
import net.dean.jraw.RedditClient;
import net.dean.jraw.managers.AccountManager;
import net.dean.jraw.managers.MultiRedditManager;
import net.dean.jraw.models.MultiReddit;
import net.dean.jraw.models.Subreddit;
import net.dean.jraw.paginators.UserSubredditsPaginator;

/** Created by carlo_000 on 1/16/2016. */
public class UserSubscriptions {
    public static final String SUB_NAME_TO_PROPERTIES = "multiNameToSubs";
    public static final List<String> defaultSubs =
            Arrays.asList(
                    "frontpage",
                    "all",
                    "announcements",
                    "Art",
                    "AskReddit",
                    "askscience",
                    "aww",
                    "blog",
                    "books",
                    "creepy",
                    "dataisbeautiful",
                    "DIY",
                    "Documentaries",
                    "EarthPorn",
                    "explainlikeimfive",
                    "Fitness",
                    "food",
                    "funny",
                    "Futurology",
                    "gadgets",
                    "gaming",
                    "GetMotivated",
                    "gifs",
                    "history",
                    "IAmA",
                    "InternetIsBeautiful",
                    "Jokes",
                    "LifeProTips",
                    "listentothis",
                    "mildlyinteresting",
                    "movies",
                    "Music",
                    "news",
                    "nosleep",
                    "nottheonion",
                    "OldSchoolCool",
                    "personalfinance",
                    "philosophy",
                    "photoshopbattles",
                    "pics",
                    "science",
                    "Showerthoughts",
                    "space",
                    "sports",
                    "television",
                    "tifu",
                    "todayilearned",
                    "TwoXChromosomes",
                    "UpliftingNews",
                    "videos",
                    "worldnews",
                    "WritingPrompts");
    public static final List<String> specialSubreddits =
            Arrays.asList(
                    "frontpage",
                    "all",
                    "random",
                    "randnsfw",
                    "myrandom",
                    "friends",
                    "mod",
                    "popular");
    // All three are assigned by Reddit.doMainStuff, i.e. Application.onCreate, before anything
    // can read them.
    @SuppressWarnings("NullAway.Init")
    public static SharedPreferences subscriptions;

    @SuppressWarnings("NullAway.Init") // Reddit.onCreate assigns this
    public static SharedPreferences multiNameToSubs;

    @SuppressWarnings("NullAway.Init") // Reddit.onCreate assigns this
    public static SharedPreferences pinned;

    public static void setSubNameToProperties(String name, String descrption) {
        multiNameToSubs.edit().putString(name, descrption).apply();
    }

    public static Map<String, String> getMultiNameToSubs(boolean all) {
        return getNameToSubs(multiNameToSubs, all);
    }

    private static Map<String, String> getNameToSubs(SharedPreferences sP, boolean all) {
        Map<String, String> multiNameToSubsMapBase = new HashMap<>();

        Map<String, ?> multiNameToSubsObject = sP.getAll();

        for (Map.Entry<String, ?> entry : multiNameToSubsObject.entrySet()) {
            multiNameToSubsMapBase.put(entry.getKey(), entry.getValue().toString());
        }
        if (all) multiNameToSubsMapBase.putAll(getSubsNameToMulti());

        Map<String, String> multiNameToSubsMap = new HashMap<>();

        for (Map.Entry<String, String> entries : multiNameToSubsMapBase.entrySet()) {
            multiNameToSubsMap.put(
                    entries.getKey().toLowerCase(Locale.ENGLISH), entries.getValue());
        }

        return multiNameToSubsMap;
    }

    private static Map<String, String> getSubsNameToMulti() {
        Map<String, String> multiNameToSubsMap = new HashMap<>();

        Map<String, ?> multiNameToSubsObject = multiNameToSubs.getAll();

        for (Map.Entry<String, ?> entry : multiNameToSubsObject.entrySet()) {
            multiNameToSubsMap.put(entry.getValue().toString(), entry.getKey());
        }

        return multiNameToSubsMap;
    }

    public static void doMainActivitySubs(MainActivity c) {
        if (NetworkUtil.isConnected(c)) {
            String s = PrefUtil.getString(subscriptions, Authentication.nameOrEmpty(), "");
            if (s.isEmpty()) {
                // get online subs
                c.updateSubs(syncSubscriptionsOverwrite(c));
            } else {
                CaseInsensitiveArrayList subredditsForHome = new CaseInsensitiveArrayList();
                for (String s2 : s.split(",")) {
                    subredditsForHome.add(s2.toLowerCase(Locale.ENGLISH));
                }
                c.updateSubs(subredditsForHome);
            }
            c.updateMultiNameToSubs(getMultiNameToSubs(false));

        } else {
            String s = PrefUtil.getString(subscriptions, Authentication.nameOrEmpty(), "");
            List<String> subredditsForHome = new CaseInsensitiveArrayList();
            if (!s.isEmpty()) {
                for (String s2 : s.split(",")) {
                    subredditsForHome.add(s2.toLowerCase(Locale.ENGLISH));
                }
            }
            CaseInsensitiveArrayList finals = new CaseInsensitiveArrayList();
            List<String> offline = OfflineSubreddit.getAllFormatted();
            for (String subs : subredditsForHome) {
                if (offline.contains(subs)) {
                    finals.add(subs);
                }
            }
            for (String subs : offline) {
                if (!finals.contains(subs)) {
                    finals.add(subs);
                }
            }
            c.updateSubs(finals);
            c.updateMultiNameToSubs(getMultiNameToSubs(false));
        }
    }

    public static void doCachedModSubs() {
        if (modOf == null || modOf.isEmpty()) {
            String s = PrefUtil.getString(subscriptions, Authentication.nameOrEmpty() + "mod", "");
            if (!s.isEmpty()) {
                modOf = new CaseInsensitiveArrayList();
                for (String s2 : s.split(",")) {
                    modOf.add(s2.toLowerCase(Locale.ENGLISH));
                }
            }
        }
    }

    public static void cacheModOf() {
        subscriptions
                .edit()
                .putString(Authentication.nameOrEmpty() + "mod", StringUtil.arrayToString(modOf))
                .apply();
    }

    public static class SyncMultireddits extends AsyncTask<Void, Void, Boolean> {

        Context c;

        public SyncMultireddits(Context c) {
            this.c = c;
        }

        @Override
        public void onPostExecute(Boolean b) {
            Intent i = new Intent(c, MultiredditOverview.class);
            c.startActivity(i);
            ((Activity) c).finish();
        }

        @Override
        @Nullable
        public Boolean doInBackground(Void... params) {
            syncMultiReddits(c);
            return null;
        }
    }

    public static CaseInsensitiveArrayList getSubscriptions(Context c) {
        String s = PrefUtil.getString(subscriptions, Authentication.nameOrEmpty(), "");
        if (s.isEmpty()) {
            // get online subs
            return syncSubscriptionsOverwrite(c);
        } else {
            CaseInsensitiveArrayList subredditsForHome = new CaseInsensitiveArrayList();
            for (String s2 : s.split(",")) {
                if (!subredditsForHome.contains(s2)) subredditsForHome.add(s2);
            }
            return subredditsForHome;
        }
    }

    /** Built on the first getPinned() and dropped again by setPinned(). */
    @Nullable public static CaseInsensitiveArrayList pins;

    public static CaseInsensitiveArrayList getPinned() {
        String s = PrefUtil.getString(pinned, Authentication.nameOrEmpty(), "");
        if (s.isEmpty()) {
            // get online subs
            return new CaseInsensitiveArrayList();
        } else if (pins == null) {
            pins = new CaseInsensitiveArrayList();
            for (String s2 : s.split(",")) {
                if (!pins.contains(s2)) pins.add(s2);
            }
            return pins;
        } else {
            return pins;
        }
    }

    public static CaseInsensitiveArrayList getSubscriptionsForShortcut(Context c) {
        String s = PrefUtil.getString(subscriptions, Authentication.nameOrEmpty(), "");
        if (s.isEmpty()) {
            // get online subs
            return syncSubscriptionsOverwrite(c);
        } else {
            CaseInsensitiveArrayList subredditsForHome = new CaseInsensitiveArrayList();
            for (String s2 : s.split(",")) {
                if (!s2.contains("/m/")) subredditsForHome.add(s2.toLowerCase(Locale.ENGLISH));
            }
            return subredditsForHome;
        }
    }

    public static boolean hasSubs() {
        String s = PrefUtil.getString(subscriptions, Authentication.nameOrEmpty(), "");
        return s.isEmpty();
    }

    // Both are filled in by a network sync that can fail, and loadMultireddits clears
    // multireddits back to null on error. Callers here already test for it.
    @Nullable public static CaseInsensitiveArrayList modOf;

    @Nullable public static ArrayList<MultiReddit> multireddits;
    public static HashMap<String, List<MultiReddit>> public_multireddits =
            new HashMap<String, List<MultiReddit>>();

    public static void doOnlineSyncing() {
        if (Authentication.mod) {
            doModOf();
            if (modOf != null) {
                for (String sub : modOf) {
                    Toolbox.ensureConfigCachedLoaded(sub);
                    Toolbox.ensureUsernotesCachedLoaded(sub);
                }
            }
        }
        SavedUsers.syncFriendsFromReddit();
        loadMultireddits();
    }

    @Nullable public static CaseInsensitiveArrayList toreturn;

    public static CaseInsensitiveArrayList syncSubscriptionsOverwrite(final Context c) {
        toreturn = new CaseInsensitiveArrayList();
        new AsyncTask<Void, Void, Void>() {
            @Override
            protected Void doInBackground(Void... params) {
                toreturn = syncSubreddits(c);
                toreturn = sort(toreturn);
                setSubscriptions(toreturn);
                return null;
            }
        }.execute();

        if (toreturn.isEmpty()) {
            // failed, load defaults
            toreturn.addAll(defaultSubs);
        }

        return toreturn;
    }

    public static CaseInsensitiveArrayList syncSubreddits(Context c) {
        CaseInsensitiveArrayList toReturn = new CaseInsensitiveArrayList();
        if (Authentication.isLoggedIn && NetworkUtil.isConnected(c)) {
            UserSubredditsPaginator pag =
                    new UserSubredditsPaginator(Authentication.reddit, "subscriber");
            pag.setLimit(100);
            try {
                while (pag.hasNext()) {
                    for (Subreddit s : pag.next()) {
                        toReturn.add(MiscUtil.orEmpty(s.getDisplayName()).toLowerCase(Locale.ENGLISH));
                    }
                }
                if (toReturn.isEmpty()
                        && PrefUtil.getString(subscriptions, Authentication.nameOrEmpty(), "").isEmpty()
                        && toreturn != null) {
                    toreturn.addAll(defaultSubs);
                }
            } catch (Exception e) {
                // failed;
                LogUtil.e(e, "UserSubscriptions.syncSubreddits failed");
            }
            addSubsToHistory(toReturn);
        } else {
            toReturn.addAll(defaultSubs);
        }
        return toReturn;
    }

    public static void syncMultiReddits(Context c) {
        try {
            multireddits = new ArrayList<>(new MultiRedditManager(Authentication.reddit).mine());
            for (MultiReddit multiReddit : multireddits) {
                if (MainActivity.multiNameToSubsMap.containsKey(
                        ReorderSubreddits.MULTI_REDDIT + multiReddit.getDisplayName())) {
                    // Use the full path that the Reddit API expects for a multi-reddit
                    // The correct format is "api/user/USERNAME/m/MULTINAME"
                    String multiPath = "api/user/" + Authentication.nameOrEmpty() + "/m/" + multiReddit.getDisplayName();

                    MainActivity.multiNameToSubsMap.put(
                            ReorderSubreddits.MULTI_REDDIT + multiReddit.getDisplayName(),
                            multiPath);
                    UserSubscriptions.setSubNameToProperties(
                            ReorderSubreddits.MULTI_REDDIT + multiReddit.getDisplayName(),
                            multiPath);
                }
            }
        } catch (ApiException | RuntimeException e) {
            LogUtil.e(e, "UserSubscriptions.syncMultiReddits failed");
        }
    }

    public static void setSubscriptions(CaseInsensitiveArrayList subs) {
        subscriptions.edit().putString(Authentication.nameOrEmpty(), StringUtil.arrayToString(subs)).apply();
    }

    public static void setPinned(CaseInsensitiveArrayList subs) {
        pinned.edit().putString(Authentication.nameOrEmpty(), StringUtil.arrayToString(subs)).apply();
        pins = null;
    }

    public static void switchAccounts() {
        // Different account -> different saved list; drop the previous account's Saved cache.
        SavedPostCache.invalidate();
        // ...and a different inbox, so the account being left must not lend its unread count to
        // the one being joined.
        InboxCount.clear(Reddit.appRestart);
        SharedPreferences.Editor editor = Reddit.appRestart.edit();
        editor.putBoolean("back", true);
        editor.putString("subs", "");
        Authentication.authentication.edit().remove("backedCreds").remove("expires").commit();
        editor.putBoolean("loggedin", Authentication.isLoggedIn);
        editor.putString("name", Authentication.name);
        editor.commit();
    }

    /**
     * @return list of multireddits if they are available, null if could not fetch multireddits
     */
    public static void getMultireddits(final MultiCallback callback) {
        new AsyncTask<Void, Void, List<MultiReddit>>() {

            @Override
            @Nullable
            protected List<MultiReddit> doInBackground(Void... params) {
                loadMultireddits();
                return multireddits;
            }

            @Override
            protected void onPostExecute(List<MultiReddit> multiReddits) {
                callback.onComplete(multiReddits);
            }
        }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }

    public interface MultiCallback {
        void onComplete(List<MultiReddit> multis);
    }

    public static void loadMultireddits() {
        if (Authentication.isLoggedIn
                && Authentication.didOnline
                && (multireddits == null || multireddits.isEmpty())) {
            try {
                multireddits =
                        new ArrayList<>(new MultiRedditManager(Authentication.reddit).mine());
            } catch (Exception e) {
                multireddits = null;
                LogUtil.e(e, "UserSubscriptions.loadMultireddits failed");
            }
        }
    }

    /**
     * @return list of multireddits if they are available, null if could not fetch multireddits
     */
    public static void getPublicMultireddits(MultiCallback callback, final String profile) {
        if (profile.isEmpty()) {
            getMultireddits(callback);
        }

        if (public_multireddits.get(profile) == null) {
            // It appears your own multis are pre-loaded at some point
            // but some other user's multis obviously can't be so
            // don't return until we've loaded them.
            loadPublicMultireddits(callback, profile);
        } else {
            callback.onComplete(public_multireddits.get(profile));
        }
    }

    private static void loadPublicMultireddits(final MultiCallback callback, final String profile) {
        new AsyncTask<Void, Void, List<MultiReddit>>() {

            @Override
            @Nullable
            protected List<MultiReddit> doInBackground(Void... params) {
                try {
                    public_multireddits.put(
                            profile,
                            new ArrayList(
                                    new MultiRedditManager(Authentication.reddit)
                                            .getPublicMultis(profile)));
                } catch (Exception e) {
                    public_multireddits.put(profile, null);
                    LogUtil.e(e, "UserSubscriptions.doInBackground failed");
                }
                return public_multireddits.get(profile);
            }

            @Override
            protected void onPostExecute(List<MultiReddit> multiReddits) {
                callback.onComplete(multiReddits);
            }
        }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }

    private static CaseInsensitiveArrayList doModOf() {
        CaseInsensitiveArrayList finished = new CaseInsensitiveArrayList();

        UserSubredditsPaginator pag =
                new UserSubredditsPaginator(Authentication.reddit, "moderator");
        pag.setLimit(100);
        try {
            while (pag.hasNext()) {
                for (Subreddit s : pag.next()) {
                    finished.add(MiscUtil.orEmpty(s.getDisplayName()).toLowerCase(Locale.ENGLISH));
                }
            }
            modOf = (finished);
            cacheModOf();
        } catch (Exception e) {
            // failed;
            LogUtil.e(e, "UserSubscriptions.doModOf failed");
        }

        return finished;
    }

    // Public method to safely get moderated subreddits
    public static CaseInsensitiveArrayList getModeratedSubs() {
        return doModOf();
    }

    /** Null when no loaded multireddit carries that display name. */
    @Nullable
    public static MultiReddit getMultiredditByDisplayName(String displayName) {
        if (multireddits != null) {
            for (MultiReddit multiReddit : multireddits) {
                if (MiscUtil.orEmpty(multiReddit.getDisplayName()).equals(displayName)) {
                    return multiReddit;
                }
            }
        }
        return null;
    }

    /** Null when no loaded public multireddit carries that display name. */
    @Nullable
    public static MultiReddit getPublicMultiredditByDisplayName(
            String profile, String displayName) {
        if (profile.isEmpty()) {
            return getMultiredditByDisplayName(displayName);
        }

        if (public_multireddits.get(profile) != null) {
            for (MultiReddit multiReddit : public_multireddits.get(profile)) {
                if (MiscUtil.orEmpty(multiReddit.getDisplayName()).equals(displayName)) {
                    return multiReddit;
                }
            }
        }
        return null;
    }

    // Gets user subscriptions + top 500 subs + subs in history
    public static CaseInsensitiveArrayList getAllSubreddits(Context c) {
        CaseInsensitiveArrayList finalReturn = new CaseInsensitiveArrayList();
        CaseInsensitiveArrayList history = getHistory();
        CaseInsensitiveArrayList defaults = getDefaults(c);
        finalReturn.addAll(getSubscriptions(c));
        for (String s : finalReturn) {
            history.remove(s);
            defaults.remove(s);
        }
        for (String s : history) {
            defaults.remove(s);
        }
        for (String s : history) {
            if (!finalReturn.contains(s)) {
                finalReturn.add(s);
            }
        }
        for (String s : defaults) {
            if (!finalReturn.contains(s)) {
                finalReturn.add(s);
            }
        }
        return finalReturn;
    }

    // Gets user subscriptions + top 500 subs + subs in history
    public static CaseInsensitiveArrayList getAllUserSubreddits(Context c) {
        CaseInsensitiveArrayList finalReturn = new CaseInsensitiveArrayList();
        finalReturn.addAll(getSubscriptions(c));
        finalReturn.removeAll(getHistory());
        finalReturn.addAll(getHistory());
        return finalReturn;
    }

    public static CaseInsensitiveArrayList getHistory() {
        String[] hist =
                PrefUtil.getString(subscriptions, "subhistory", "")
                        .toLowerCase(Locale.ENGLISH)
                        .split(",");
        CaseInsensitiveArrayList history = new CaseInsensitiveArrayList();
        Collections.addAll(history, hist);
        return history;
    }

    public static CaseInsensitiveArrayList getDefaults(Context c) {
        CaseInsensitiveArrayList history = new CaseInsensitiveArrayList();
        Collections.addAll(history, c.getString(R.string.top_500_csv).split(","));
        return history;
    }

    public static void addSubreddit(String s, Context c) {
        CaseInsensitiveArrayList subs = getSubscriptions(c);
        subs.add(s);
        if (SettingValues.alphabetizeOnSubscribe) {
            setSubscriptions(sortNoExtras(subs));
        } else {
            setSubscriptions(subs);
        }
    }

    public static void removeSubreddit(String s, Context c) {
        CaseInsensitiveArrayList subs = getSubscriptions(c);
        subs.remove(s);
        setSubscriptions(subs);
    }

    public static void addPinned(String s, Context c) {
        CaseInsensitiveArrayList subs = getPinned();
        subs.add(s);
        setPinned(subs);
    }

    public static void removePinned(String s, Context c) {
        CaseInsensitiveArrayList subs = getPinned();
        subs.remove(s);
        setPinned(subs);
    }

    // Sets sub as "searched for", will apply to all accounts
    public static void addSubToHistory(String s) {
        String history = PrefUtil.getString(subscriptions, "subhistory", "");
        if (!history.contains(s.toLowerCase(Locale.ENGLISH))) {
            history += "," + s.toLowerCase(Locale.ENGLISH);
            subscriptions.edit().putString("subhistory", history).apply();
        }
    }

    // Sets a list of subreddits as "searched for", will apply to all accounts
    public static void addSubsToHistory(ArrayList<Subreddit> s2) {
        StringBuilder history =
                new StringBuilder(
                        PrefUtil.getString(subscriptions, "subhistory", "")
                                .toLowerCase(Locale.ENGLISH));
        for (Subreddit s : s2) {
            if (!history.toString().contains(MiscUtil.orEmpty(s.getDisplayName()).toLowerCase(Locale.ENGLISH))) {
                history.append(",").append(MiscUtil.orEmpty(s.getDisplayName()).toLowerCase(Locale.ENGLISH));
            }
        }
        subscriptions.edit().putString("subhistory", history.toString()).apply();
    }

    public static void addSubsToHistory(CaseInsensitiveArrayList s2) {
        StringBuilder history =
                new StringBuilder(
                        PrefUtil.getString(subscriptions, "subhistory", "")
                                .toLowerCase(Locale.ENGLISH));
        for (String s : s2) {
            if (!history.toString().contains(s.toLowerCase(Locale.ENGLISH))) {
                history.append(",").append(s.toLowerCase(Locale.ENGLISH));
            }
        }
        subscriptions.edit().putString("subhistory", history.toString()).apply();
    }

    public static ArrayList<Subreddit> syncSubredditsGetObject() {
        ArrayList<Subreddit> toReturn = new ArrayList<>();
        if (Authentication.isLoggedIn) {
            UserSubredditsPaginator pag =
                    new UserSubredditsPaginator(Authentication.reddit, "subscriber");
            pag.setLimit(100);

            try {
                while (pag.hasNext()) {
                    toReturn.addAll(pag.next());
                }

            } catch (Exception e) {
                // failed;
                LogUtil.e(e, "UserSubscriptions.syncSubredditsGetObject failed");
            }

            addSubsToHistory(toReturn);
            return toReturn;
        }
        return toReturn;
    }

    public static void syncSubredditsGetObjectAsync(final Login mainActivity) {
        final ArrayList<Subreddit> toReturn = new ArrayList<>();
        new AsyncTask<Void, Void, Void>() {
            @Override
            protected Void doInBackground(Void... params) {
                if (Authentication.isLoggedIn) {
                    UserSubredditsPaginator pag =
                            new UserSubredditsPaginator(Authentication.reddit, "subscriber");
                    pag.setLimit(100);

                    try {
                        while (pag.hasNext()) {
                            toReturn.addAll(pag.next());
                        }

                    } catch (Exception e) {
                        // failed;
                        LogUtil.e(e, "UserSubscriptions.doInBackground failed");
                    }
                }
                return null;
            }

            @Override
            protected void onPostExecute(Void aVoid) {
                mainActivity.doLastStuff(toReturn);
            }
        }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }

    /**
     * Sorts the subreddit ArrayList, keeping special subreddits at the top of the list (e.g.
     * frontpage, all, the random subreddits). Always adds frontpage and all
     *
     * @param unsorted the ArrayList to sort
     * @return the sorted ArrayList
     * @see #sortNoExtras(CaseInsensitiveArrayList)
     */
    public static CaseInsensitiveArrayList sort(CaseInsensitiveArrayList unsorted) {
        CaseInsensitiveArrayList subs = new CaseInsensitiveArrayList(unsorted);

        if (!subs.contains("frontpage")) {
            subs.add("frontpage");
        }

        if (!subs.contains("all")) {
            subs.add("all");
        }

        return sortNoExtras(subs);
    }

    /**
     * Sorts the subreddit ArrayList, keeping special subreddits at the top of the list (e.g.
     * frontpage, all, the random subreddits)
     *
     * @param unsorted the ArrayList to sort
     * @return the sorted ArrayList
     * @see #sort(CaseInsensitiveArrayList)
     */
    public static CaseInsensitiveArrayList sortNoExtras(CaseInsensitiveArrayList unsorted) {
        List<String> subs = new CaseInsensitiveArrayList(unsorted);
        CaseInsensitiveArrayList finals = new CaseInsensitiveArrayList();

        for (String subreddit : getPinned()) {
            if (subs.contains(subreddit)) {
                subs.remove(subreddit);
                finals.add(subreddit);
            }
        }

        for (String subreddit : specialSubreddits) {
            if (subs.contains(subreddit)) {
                subs.remove(subreddit);
                finals.add(subreddit);
            }
        }

        java.util.Collections.sort(subs, String.CASE_INSENSITIVE_ORDER);
        finals.addAll(subs);
        return finals;
    }

    public static class SubscribeTask extends AsyncTask<String, Void, Boolean> {
        private Context context;

        /** Set only on the failure path, which is the only path that reads it. */
        @Nullable private String errorMessage;

        public SubscribeTask(Context context) {
            this.context = context;
        }

        @Override
        protected Boolean doInBackground(String... subreddits) {
            final RedditClient client = Authentication.reddit;
            if (client == null) {
                errorMessage = context.getString(R.string.subscribe_err_login);
                return false;
            }
            final AccountManager m = new AccountManager(client);
            try {
                for (String subreddit : subreddits) {
                    m.subscribe(client.getSubreddit(subreddit));
                }
                return true;
            } catch (Exception e) {
                errorMessage = "Couldn't subscribe, subreddit is private, quarantined, or invite only";
                return false;
            }
        }

        @Override
        protected void onPostExecute(Boolean success) {
            if (!success && context != null) {
                Toast.makeText(context, errorMessage, Toast.LENGTH_SHORT).show();
            }
        }
    }

    public static class UnsubscribeTask extends AsyncTask<String, Void, Void> {
        @Override
        protected Void doInBackground(String... subreddits) {
            final RedditClient client = Authentication.reddit;
            if (client == null) {
                return null;
            }
            final AccountManager m = new AccountManager(client);
            try {
                for (String subreddit : subreddits) {
                    m.unsubscribe(client.getSubreddit(subreddit));
                }
            } catch (Exception e) {
                // The sub stays subscribed on the server. Every caller (ReorderSubreddits) has
                // already dropped it locally and said so, so the two diverge until the next
                // syncSubscriptions; failing loudly here would not undo that.
            }
            return null;
        }
    }
}
