package me.edgan.redditslide.test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import me.edgan.redditslide.util.CommentRecovery;
import org.junit.Test;

/**
 * Covers {@link CommentRecovery#parse}, the pure half of the Arctic Shift comment recovery. The
 * payloads are the real shapes the archive returns — a recovered comment, a copy that was only ever
 * ingested after the removal, and the error envelope — because the parse's job is to survive every
 * one of them without throwing out of a background task.
 */
public class CommentRecoveryTest {

    private static CommentRecovery.Result parse(String json) {
        return CommentRecovery.parse(JsonParser.parseString(json).getAsJsonObject());
    }

    private static String envelope(String comment) {
        return "{\"data\": [" + comment + "]}";
    }

    @Test
    public void recoversBodyAndAuthor() {
        CommentRecovery.Result result =
                parse(
                        envelope(
                                "{\"id\": \"otuvyo7\", \"author\": \"coffca\","
                                        + " \"body\": \"Where does that lora come from? \","
                                        + " \"body_html\": null}"));

        assertThat(result.body, is("Where does that lora come from? "));
        assertThat(result.author, is("coffca"));
        assertFalse(result.isEmpty());
    }

    @Test
    public void rejectsArchivedPlaceholderBodies() {
        for (String placeholder :
                new String[] {
                    "[removed]",
                    "[deleted]",
                    "  [Removed]  ",
                    "[ Removed by Reddit ]",
                    "[ Removed by moderator ]"
                }) {
            CommentRecovery.Result result =
                    parse(envelope("{\"author\": \"someone\", \"body\": \"" + placeholder + "\"}"));

            assertThat(placeholder, result.body, is(nullValue()));
            // The author is still real, so the recovery is not empty.
            assertThat(result.author, is("someone"));
        }
    }

    @Test
    public void rejectsArchivedPlaceholderAuthor() {
        CommentRecovery.Result result =
                parse(envelope("{\"author\": \"[deleted]\", \"body\": \"still here\"}"));

        assertThat(result.author, is(nullValue()));
        assertThat(result.body, is("still here"));
    }

    @Test
    public void emptyWhenNothingUsableIsArchived() {
        assertTrue(parse(envelope("{\"author\": \"[deleted]\", \"body\": \"[removed]\"}")).isEmpty());
    }

    @Test
    public void survivesMalformedEnvelopes() {
        String[] envelopes = {
            "{}",
            "{\"data\": null}",
            "{\"data\": []}",
            "{\"data\": {}}",
            "{\"data\": \"nope\"}",
            "{\"data\": [null]}",
            "{\"data\": [\"nope\"]}",
            // The real shape Arctic Shift returns for a bad request.
            "{\"data\": null, \"error\": \"'permalink' is not a valid field\"}"
        };

        for (String json : envelopes) {
            assertTrue(json, parse(json).isEmpty());
        }
    }

    @Test
    public void ignoresMissingBlankAndNonPrimitiveValues() {
        assertTrue(parse(envelope("{\"id\": \"abc\"}")).isEmpty());
        assertTrue(parse(envelope("{\"body\": null, \"author\": null}")).isEmpty());
        assertTrue(parse(envelope("{\"body\": \"   \", \"author\": \"\"}")).isEmpty());
        assertTrue(parse(envelope("{\"body\": {}, \"author\": []}")).isEmpty());
    }

    @Test
    public void handlesANullResponse() {
        assertTrue(CommentRecovery.parse((JsonObject) null).isEmpty());
    }
}
