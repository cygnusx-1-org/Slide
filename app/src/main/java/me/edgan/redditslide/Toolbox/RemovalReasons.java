package me.edgan.redditslide.Toolbox;

import androidx.annotation.Nullable;
import com.google.gson.annotations.SerializedName;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.List;
import org.apache.commons.text.StringEscapeUtils;

public class RemovalReasons {
    @SerializedName("pmsubject")
    private String pmSubject = "";

    private String header = "";
    private String footer = "";

    @SerializedName("logsub")
    private String logSub = "";

    @SerializedName("logtitle")
    private String logTitle = "";

    @SerializedName("logreason")
    private String logReason = "";

    @SerializedName("bantitle")
    private String banTitle =
            ""; // Is this even used by Toolbox? For mod button bans maybe (not a removal reason

    // thing...)?

    // Absent from a config that defines no reasons; ToolboxConfigTest pins the null.
    @Nullable private List<RemovalReason> reasons;

    public RemovalReasons() {}

    /**
     * The empty string for a field GSON left null.
     *
     * <p>Every string field above initializes to "", but that only covers a key the config omits:
     * GSON writes an explicit JSON null straight over the initializer, so a wiki page carrying
     * {@code "logsub": null} left the field null and the accessor dereferenced it.
     */
    static String orEmpty(@Nullable String s) {
        return s == null ? "" : s;
    }

    public String getPmSubject() {
        final String pmSubject = orEmpty(this.pmSubject);
        if (pmSubject.isEmpty()) {
            return "Your {kind} was removed from /r/{subreddit}";
        }
        return pmSubject;
    }

    /**
     * @return the decoded header, or null if this device has no UTF-8 charset.
     */
    @Nullable
    public String getHeader() {
        try {
            return URLDecoder.decode(
                    StringEscapeUtils.unescapeJava(orEmpty(header).replace("%u", "\\u")),
                    "UTF-8"); // header is url encoded
        } catch (UnsupportedEncodingException e) {
            return null;
        }
    }

    /**
     * @return the decoded footer, or null if this device has no UTF-8 charset.
     */
    @Nullable
    public String getFooter() {
        try {
            return URLDecoder.decode(
                    StringEscapeUtils.unescapeJava(orEmpty(footer).replace("%u", "\\u")),
                    "UTF-8"); // footer is url encoded
        } catch (UnsupportedEncodingException e) {
            return null;
        }
    }

    public String getLogSub() {
        return orEmpty(logSub);
    }

    public String getLogTitle() {
        final String logTitle = orEmpty(this.logTitle);
        if (logTitle.isEmpty()) {
            return "Removed: {kind} by /u/{author} to /r/{subreddit}";
        }
        return logTitle;
    }

    public String getLogReason() {
        return orEmpty(logReason);
    }

    @Nullable
    public List<RemovalReason> getReasons() {
        return reasons;
    }

    /** Class defining an individual removal reason */
    public static class RemovalReason {
        private String title = "";
        private String text = "";
        private String flairText = "";
        private String flairCSS = "";

        public RemovalReason() {}

        public String getTitle() {
            return orEmpty(title);
        }

        /**
         * @return the decoded text, or null if this device has no UTF-8 charset.
         */
        @Nullable
        public String getText() {
            try {
                // text is url encoded but uses non-standard %uXXXX char sequences
                return URLDecoder.decode(
                        StringEscapeUtils.unescapeJava(orEmpty(text).replace("%u", "\\u")),
                        "UTF-8");
            } catch (UnsupportedEncodingException e) {
                return null;
            }
        }

        public String getFlairText() {
            return orEmpty(flairText);
        }

        public String getFlairCSS() {
            return orEmpty(flairCSS);
        }
    }
}
