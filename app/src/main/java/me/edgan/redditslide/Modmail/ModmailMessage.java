package me.edgan.redditslide.Modmail;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * A single message inside a New Modmail conversation ({@code /api/mod/conversations}).
 *
 * <p>New Modmail replaced legacy modmail (the old {@code /message/moderator} endpoints, which now
 * return empty listings). Its data model is conversation-centric rather than a flat list of {@link
 * net.dean.jraw.models.Message}, so it cannot reuse JRAW's inbox models.
 */
public class ModmailMessage {
    private final JsonNode node;

    public ModmailMessage(JsonNode node) {
        this.node = node;
    }

    /** Raw JSON node, kept so the renderer can resolve image placeholders like the inbox does. */
    public JsonNode getDataNode() {
        return node;
    }

    public String getId() {
        return node.path("id").asText();
    }

    public String getAuthor() {
        return node.path("author").path("name").asText();
    }

    public boolean isAuthorMod() {
        return node.path("author").path("isMod").asBoolean();
    }

    public boolean isAuthorHidden() {
        return node.path("author").path("isHidden").asBoolean();
    }

    /** Internal (mod-only) note, not visible to the participant. */
    public boolean isInternal() {
        return node.path("isInternal").asBoolean();
    }

    /** HTML body, ready to feed through SubmissionParser like inbox bodies. */
    public String getBodyHtml() {
        return node.path("body").asText();
    }

    public long getCreatedMillis() {
        return ModmailApi.parseModmailDate(node.path("date").asText());
    }
}
