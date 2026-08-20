package me.edgan.redditslide.Modmail;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * A New Modmail conversation ({@code /api/mod/conversations}). Wraps the conversation JSON node from
 * the {@code conversations} map; messages are resolved separately from the {@code messages} map.
 */
public class ModmailConversation {
    private final JsonNode node;

    public ModmailConversation(JsonNode node) {
        this.node = node;
    }

    public JsonNode getDataNode() {
        return node;
    }

    public String getId() {
        return node.path("id").asText();
    }

    public String getSubject() {
        return node.path("subject").asText();
    }

    /** Subreddit the conversation belongs to (the {@code owner} of the modmail). */
    public String getSubreddit() {
        return node.path("owner").path("displayName").asText();
    }

    /** The non-mod participant of the conversation, or empty for internal discussions. */
    public String getParticipant() {
        return node.path("participant").path("name").asText();
    }

    public int getNumMessages() {
        return node.path("numMessages").asInt();
    }

    /**
     * New Modmail tracks read state per-viewer via {@code lastUnread}: a non-null timestamp means
     * this conversation has messages the current moderator has not read.
     */
    public boolean isUnread() {
        final JsonNode lastUnread = node.get("lastUnread");
        return lastUnread != null && !lastUnread.isNull();
    }

    public boolean isHighlighted() {
        return node.path("isHighlighted").asBoolean();
    }

    public long getLastUpdatedMillis() {
        return ModmailApi.parseModmailDate(node.path("lastUpdated").asText());
    }
}
