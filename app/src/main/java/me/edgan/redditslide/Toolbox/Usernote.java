package me.edgan.redditslide.Toolbox;

import androidx.annotation.Nullable;
import com.google.gson.annotations.SerializedName;

import java.util.Objects;

/** Defines a Usernote so GSON can deserialize it */
public class Usernote {
    // Both keys are written by Toolbox for every note, but a hand-edited wiki page can omit
    // either, so nothing here dereferences them without a guard.
    @SerializedName("n")
    @Nullable private String noteText;

    @SerializedName("l")
    @Nullable private String link;

    @SerializedName("t")
    private long time;

    @SerializedName("m")
    private int mod;

    @SerializedName("w")
    private int warning;

    public Usernote() { // for GSON
    }

    public Usernote(
            @Nullable String noteText, @Nullable String link, long time, int mod, int warning) {
        this.noteText = noteText;
        this.link = link;
        this.time = time;
        this.mod = mod;
        this.warning = warning;
    }

    /**
     * @return the note's text, empty when the note carries none. Never null: both readers put the
     *     result straight into a display string, and neither {@code SpannableStringBuilder.append}
     *     nor {@code StringUtils.abbreviate} would survive one.
     */
    public String getNoteText() {
        return noteText == null ? "" : noteText;
    }

    public long getTime() {
        return time * 1000; // * 1000 so it makes sense as a long
    }

    public int getMod() {
        return mod;
    }

    @Nullable
    public String getLink() {
        return link;
    }

    public int getWarning() {
        return warning;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof Usernote) {
            return ((Usernote) obj).warning == warning
                    && ((Usernote) obj).mod == mod
                    && ((Usernote) obj).time == time
                    && Objects.equals(((Usernote) obj).noteText, noteText)
                    && Objects.equals(((Usernote) obj).link, link);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return Objects.hash(noteText, link, time, mod, warning);
    }

    /**
     * Identify what type of link a usernote points to, if any
     *
     * @return Type of link
     */
    @Nullable
    public UsernoteLinkType getLinkType() {
        final String link = this.link;
        if (link == null || link.isEmpty()) {
            return null;
        }
        if (link.startsWith("m,")) {
            return UsernoteLinkType.MODMAIL;
        } else if (link.startsWith("l,") && link.split(",").length == 3) {
            return UsernoteLinkType.COMMENT;
        } else if (link.startsWith("l,")) {
            return UsernoteLinkType.POST;
        } else {
            return null;
        }
    }

    /**
     * Gets the Usernote's link as a URL
     *
     * @return String of usernote's URL.
     */
    @Nullable
    public String getLinkAsURL(String subreddit) {
        final String link = this.link;
        if (link == null || link.isEmpty()) {
            return null;
        }

        if (getLinkType() == UsernoteLinkType.MODMAIL) {
            return "https://www.reddit.com/message/messages/" + link.substring(3);
        } else {
            String[] split = link.split(",");
            return "https://www.reddit.com/r/"
                    + subreddit
                    + "/comments/"
                    + split[1]
                    + (getLinkType() == UsernoteLinkType.COMMENT ? "/_/" + split[2] : "");
        }
    }

    public enum UsernoteLinkType {
        POST,
        COMMENT,
        MODMAIL
    }
}
