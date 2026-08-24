package me.edgan.redditslide.Modmail;

import com.fasterxml.jackson.databind.JsonNode;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;
import me.edgan.redditslide.Authentication;
import me.edgan.redditslide.util.LogUtil;
import net.dean.jraw.RedditClient;
import net.dean.jraw.http.HttpRequest;
import net.dean.jraw.http.RestResponse;
import org.jspecify.annotations.Nullable;

/**
 * Thin client for Reddit's New Modmail API ({@code /api/mod/conversations}), which replaced the
 * legacy {@code /message/moderator} endpoints used by JRAW's {@link
 * net.dean.jraw.paginators.InboxPaginator}. JRAW has no New Modmail support, so — like {@code
 * ImageFlairs} and {@code Crosspost} — this issues raw authenticated requests and parses the JSON
 * directly. Requires the {@code modmail} OAuth scope (added in Login/Reauthenticate).
 */
public class ModmailApi {

    /** OAuth scope these endpoints require; absent from grants authorized before it was added. */
    public static final String SCOPE = "modmail";

    private ModmailApi() {}

    /**
     * GET {@code /api/mod/conversations}. Returns the root JSON node ({@code conversations},
     * {@code messages}, {@code conversationIds}) or null on failure.
     *
     * @param state one of all, new, inprogress, archived, etc.
     * @param sort recent, mod, user, or unread
     * @param after a ModmailConversation id to page from, or null for the first page
     * @param limit page size (1-100)
     */
    public static @Nullable JsonNode getConversations(
            String state, String sort, @Nullable String after, int limit) {
        final RedditClient client = Authentication.reddit;
        if (client == null || !Authentication.hasScope(SCOPE)) {
            return null;
        }
        try {
            Map<String, String> query = new HashMap<>();
            query.put("state", state);
            query.put("sort", sort);
            query.put("limit", String.valueOf(limit));
            if (after != null && !after.isEmpty()) {
                query.put("after", after);
            }

            HttpRequest r =
                    client.request()
                            .path("/api/mod/conversations")
                            .query(query)
                            .get()
                            .build();
            RestResponse response = client.execute(r);
            return response.getJson();
        } catch (Exception e) {
            LogUtil.e(e, "ModmailApi.getConversations failed");
            return null;
        }
    }

    /**
     * GET {@code /api/mod/conversations/:id}. Returns the root node ({@code conversation},
     * {@code messages}, {@code modActions}) or null on failure.
     *
     * @param markRead whether reading marks the conversation read for the current moderator
     */
    public static @Nullable JsonNode getConversation(String id, boolean markRead) {
        final RedditClient client = Authentication.reddit;
        if (client == null || !Authentication.hasScope(SCOPE)) {
            return null;
        }
        try {
            Map<String, String> query = new HashMap<>();
            query.put("markRead", String.valueOf(markRead));

            HttpRequest r =
                    client.request()
                            .path("/api/mod/conversations/" + id)
                            .query(query)
                            .get()
                            .build();
            RestResponse response = client.execute(r);
            return response.getJson();
        } catch (Exception e) {
            LogUtil.e(e, "ModmailApi.getConversation failed");
            return null;
        }
    }

    /**
     * POST {@code /api/mod/conversations/:id} — append a reply to a conversation.
     *
     * @param body raw markdown
     * @param isAuthorHidden hide the replying mod, posting as the subreddit instead
     * @param isInternal post an internal (mod-only) note rather than a participant-visible reply
     * @return true on success
     */
    public static boolean reply(
            String id, String body, boolean isAuthorHidden, boolean isInternal) {
        final RedditClient client = Authentication.reddit;
        if (client == null || !Authentication.hasScope(SCOPE)) {
            return false;
        }
        try {
            Map<String, String> args = new HashMap<>();
            args.put("body", body);
            args.put("isAuthorHidden", String.valueOf(isAuthorHidden));
            args.put("isInternal", String.valueOf(isInternal));

            HttpRequest r =
                    client.request()
                            .path("/api/mod/conversations/" + id)
                            .post(args)
                            .build();
            RestResponse response = client.execute(r);
            return !response.hasErrors();
        } catch (Exception e) {
            LogUtil.e(e, "ModmailApi.reply failed");
            return false;
        }
    }

    /**
     * Parse a {@code /api/mod/conversations} list response into ordered conversations. Order follows
     * {@code conversationIds}.
     *
     * @param onlyUnread when true, conversations with no unread messages are skipped
     */
    public static List<ModmailConversation> parseConversationList(
            @Nullable JsonNode root, boolean onlyUnread) {
        List<ModmailConversation> out = new ArrayList<>();
        if (root == null || !root.has("conversations") || !root.has("conversationIds")) {
            return out;
        }
        JsonNode conversations = root.path("conversations");
        for (JsonNode idNode : root.path("conversationIds")) {
            final JsonNode conversation = conversations.get(idNode.asText());
            if (conversation == null) {
                continue;
            }
            ModmailConversation c = new ModmailConversation(conversation);
            if (onlyUnread && !c.isUnread()) {
                continue;
            }
            out.add(c);
        }
        return out;
    }

    /**
     * Parse a single-conversation response ({@code /api/mod/conversations/:id}) into its messages,
     * ordered chronologically via the conversation's {@code objIds}.
     */
    public static List<ModmailMessage> parseMessages(@Nullable JsonNode root) {
        List<ModmailMessage> out = new ArrayList<>();
        if (root == null || !root.has("messages")) {
            return out;
        }
        JsonNode messages = root.path("messages");

        JsonNode conversation = root.path("conversation");
        if (conversation.has("objIds")) {
            for (JsonNode obj : conversation.path("objIds")) {
                if (!"messages".equals(obj.path("key").asText())) {
                    continue;
                }
                final JsonNode message = messages.get(obj.path("id").asText());
                if (message != null) {
                    out.add(new ModmailMessage(message));
                }
            }
        } else {
            // Fallback: no ordering metadata, just take every message we were given.
            for (JsonNode m : messages) {
                out.add(new ModmailMessage(m));
            }
        }
        return out;
    }

    /** Subject for a single-conversation response, used to title the thread screen. */
    public static String parseSubject(@Nullable JsonNode root) {
        if (root == null) {
            return "";
        }
        return root.path("conversation").path("subject").asText();
    }

    /**
     * Parse a New Modmail timestamp into epoch millis. Reddit emits ISO-8601 with fractional seconds
     * and an offset, e.g. {@code 2025-05-01T12:34:56.789012+00:00} or {@code ...Z}; we read the
     * seconds component as UTC, which is accurate enough for relative "time ago" display.
     */
    public static long parseModmailDate(@Nullable String date) {
        if (date == null || date.isEmpty()) {
            return 0L;
        }
        try {
            String trimmed = date;
            int dot = trimmed.indexOf('.');
            if (dot > 0) {
                trimmed = trimmed.substring(0, dot);
            } else {
                // Strip a trailing offset/Z when there are no fractional seconds.
                int t = trimmed.indexOf('T');
                int off = Math.max(trimmed.indexOf('+', t), trimmed.indexOf('Z', t));
                if (off > 0) {
                    trimmed = trimmed.substring(0, off);
                }
            }
            SimpleDateFormat fmt = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.ENGLISH);
            fmt.setTimeZone(TimeZone.getTimeZone("UTC"));
            Date parsed = fmt.parse(trimmed);
            return parsed == null ? 0L : parsed.getTime();
        } catch (Exception e) {
            return 0L;
        }
    }
}
