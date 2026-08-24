package me.edgan.redditslide;

import android.content.SharedPreferences;
import java.util.ArrayList;
import me.edgan.redditslide.util.PrefUtil;
import me.edgan.redditslide.util.StringUtil;

/** Created by l3d00m on 11/13/2015. */
public class Drafts {

    public static ArrayList<String> getDrafts() {
        ArrayList<String> drafts = new ArrayList<>();
        for (String s :
                PrefUtil.getString(Authentication.authentication, SettingValues.PREF_DRAFTS, "")
                        .split("</newdraft>")) {
            if (!s.trim().isEmpty()) {
                drafts.add(s);
            }
        }
        return drafts;
    }

    public static void addDraft(String s) {
        ArrayList<String> drafts = getDrafts();
        drafts.add(s);
        save(drafts);
    }

    public static void deleteDraft(int position) {
        ArrayList<String> drafts = getDrafts();
        drafts.remove(position);
        save(drafts);
    }

    /**
     * Every caller runs on the main thread — the editor's save-draft button, and the reply paths
     * that stash text when a send fails — so this used to block them on a disk write. {@code
     * apply()} updates the in-memory map before it returns, which is what {@link #getDrafts} reads,
     * and Android flushes the write itself; the same trade made in {@code
     * Authentication.migrateAccountToTokenForm}.
     */
    public static void save(ArrayList<String> drafts) {
        SharedPreferences.Editor e = Authentication.authentication.edit();
        e.putString(SettingValues.PREF_DRAFTS, StringUtil.arrayToString(drafts, "</newdraft>"));
        e.apply();
    }
}
