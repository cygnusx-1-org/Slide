package me.edgan.redditslide.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.Test;

/**
 * The lookup by id behind History, Local Saved, the comment peek and the submit poll.
 *
 * <p>Reddit answers {@code /api/info} with a 403 block page for some client ids while each post's
 * own listing still answers. These pin when {@code /api/info} is asked at all, when the block is
 * remembered, and how single fetches are put back into an {@code /api/info}-shaped listing.
 */
public class ThingLookupTest {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final AtomicBoolean blocked = new AtomicBoolean();
    private final List<String> infoAsked = Collections.synchronizedList(new ArrayList<>());
    private final List<String> itemsAsked = Collections.synchronizedList(new ArrayList<>());

    /** Single answers by id; anything absent is a post that answers normally. */
    private final Map<String, LookupResponse> itemAnswers = new ConcurrentHashMap<>();

    private LookupResponse infoAnswer = listing("info");

    private static JsonNode json(String raw) {
        try {
            return MAPPER.readTree(raw);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static LookupResponse listing(String... ids) {
        List<JsonNode> children = new ArrayList<>();
        for (String id : ids) {
            children.add(json("{\"kind\":\"t3\",\"data\":{\"id\":\"" + id + "\"}}"));
        }
        return ThingLookup.listingOf(children);
    }

    private static LookupResponse status(int code) {
        return new LookupResponse(code, null);
    }

    private ThingLookup<String> lookup(InfoMode mode) {
        return new ThingLookup<>(
                mode,
                blocked,
                ids -> {
                    infoAsked.add(ids);
                    return infoAnswer;
                },
                id -> "t3_" + id,
                id -> {
                    if (id.startsWith("alone")) {
                        // A comment with no post id: it cannot be asked for on its own.
                        return null;
                    }
                    itemsAsked.add(id);
                    LookupResponse answer = itemAnswers.get(id);
                    return answer != null
                            ? answer
                            : new LookupResponse(
                                    200,
                                    json(
                                            "[{\"data\":{\"children\":[{\"kind\":\"t3\",\"data\":{\"id\":\""
                                                    + id
                                                    + "\"}}]}}]"));
                },
                (id, body) -> body.path(0).path("data").path("children").path(0));
    }

    private static List<String> ids(LookupResponse answer) {
        assertTrue(answer.isSuccessful());
        List<String> out = new ArrayList<>();
        for (JsonNode child : answer.json.path("data").path("children")) {
            out.add(child.path("data").path("id").asText());
        }
        return out;
    }

    private static List<String> many(int count) {
        List<String> out = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            out.add("p" + i);
        }
        return out;
    }

    // ~~~ Mode "first" (the anonymous account)

    @Test
    public void firstReturnsAWorkingInfoAnswerAsIsWithNoSingleFetches() {
        LookupResponse answer = lookup(InfoMode.FIRST).lookup(Arrays.asList("a", "b"));

        assertEquals(Collections.singletonList("t3_a,t3_b"), infoAsked);
        assertEquals(Collections.singletonList("info"), ids(answer));
        assertTrue(itemsAsked.isEmpty());
        assertFalse(blocked.get());
    }

    @Test
    public void firstFallsBackOnA403InInputOrderAndRemembersIt() {
        infoAnswer = status(403);
        List<String> wanted = many(30);

        LookupResponse answer = lookup(InfoMode.FIRST).lookup(wanted);

        assertEquals(wanted, ids(answer));
        assertEquals(30, itemsAsked.size());
        assertTrue(blocked.get());
    }

    @Test
    public void onceBlockedFirstNeverAsksInfoAgain() {
        blocked.set(true);

        LookupResponse answer = lookup(InfoMode.FIRST).lookup(Arrays.asList("a", "b"));

        assertTrue(infoAsked.isEmpty());
        assertEquals(Arrays.asList("a", "b"), ids(answer));
    }

    @Test
    public void aServerErrorFromInfoIsReturnedAndNotTakenForTheBlock() {
        infoAnswer = status(503);

        LookupResponse answer = lookup(InfoMode.FIRST).lookup(Arrays.asList("a", "b"));

        assertEquals(503, answer.code);
        assertTrue(itemsAsked.isEmpty());
        assertFalse(blocked.get());
    }

    @Test
    public void anEmptyInputAsksNothing() {
        for (InfoMode mode : InfoMode.values()) {
            LookupResponse answer = lookup(mode).lookup(Collections.emptyList());
            assertTrue(ids(answer).isEmpty());
        }
        assertTrue(infoAsked.isEmpty());
        assertTrue(itemsAsked.isEmpty());
    }

    @Test
    public void a404ItemIsLeftOut() {
        blocked.set(true);
        itemAnswers.put("b", status(404));

        LookupResponse answer = lookup(InfoMode.FIRST).lookup(Arrays.asList("a", "b", "c"));

        assertEquals(Arrays.asList("a", "c"), ids(answer));
    }

    @Test
    public void aPrivateItemIsLeftOut() {
        blocked.set(true);
        itemAnswers.put("b", status(403));

        LookupResponse answer = lookup(InfoMode.FIRST).lookup(Arrays.asList("a", "b", "c"));

        assertEquals(Arrays.asList("a", "c"), ids(answer));
    }

    @Test
    public void everyItem404IsAnEmptyListing() {
        blocked.set(true);
        itemAnswers.put("a", status(404));
        itemAnswers.put("b", status(404));

        assertTrue(ids(lookup(InfoMode.FIRST).lookup(Arrays.asList("a", "b"))).isEmpty());
    }

    @Test
    public void everyItemRefusedReturnsTheRefusal() {
        blocked.set(true);
        itemAnswers.put("a", status(403));
        itemAnswers.put("b", status(404));

        assertEquals(403, lookup(InfoMode.FIRST).lookup(Arrays.asList("a", "b")).code);
    }

    @Test
    public void aRateLimitedItemIsReturnedAndStopsTheRest() {
        blocked.set(true);
        itemAnswers.put("p0", status(429));

        LookupResponse answer = lookup(InfoMode.FIRST).lookup(many(40));

        assertEquals(429, answer.code);
        // At most the other workers' claims got out after it.
        assertTrue(itemsAsked.toString(), itemsAsked.size() < 40);
    }

    // ~~~ Modes "only" and "never" (an account, by the setting)

    @Test
    public void onlyReturnsInfosRefusalWithNoSingleFetchesAndNoFlag() {
        infoAnswer = status(403);

        LookupResponse answer = lookup(InfoMode.ONLY).lookup(Arrays.asList("a", "b"));

        assertEquals(403, answer.code);
        assertEquals(1, infoAsked.size());
        assertTrue(itemsAsked.isEmpty());
        assertFalse(blocked.get());
    }

    @Test
    public void neverNeverAsksInfo() {
        LookupResponse answer = lookup(InfoMode.NEVER).lookup(Arrays.asList("a", "b"));

        assertTrue(infoAsked.isEmpty());
        assertEquals(Arrays.asList("a", "b"), ids(answer));
        assertFalse(blocked.get());
    }

    // ~~~ Things that cannot be fetched on their own

    @Test
    public void aThingWithNoSingleFetchIsAskedOfInfoWhileItAnswers() {
        LookupResponse answer = lookup(InfoMode.FIRST).lookup(Arrays.asList("alone1", "a"));

        assertEquals(Collections.singletonList("t3_alone1,t3_a"), infoAsked);
        assertTrue(answer.isSuccessful());
    }

    @Test
    public void aThingWithNoSingleFetchIsLeftOutWithoutARequest() {
        for (InfoMode mode : new InfoMode[] {InfoMode.NEVER, InfoMode.FIRST}) {
            infoAnswer = status(403);
            blocked.set(false);
            infoAsked.clear();
            itemsAsked.clear();

            LookupResponse answer = lookup(mode).lookup(Arrays.asList("alone1", "a", "alone2"));

            assertEquals(mode.name(), Collections.singletonList("a"), ids(answer));
            assertEquals(mode.name(), Collections.singletonList("a"), itemsAsked);
        }
    }

    @Test
    public void onlyThingsWithNoSingleFetchIsAnEmptyListingNotAnError() {
        LookupResponse answer =
                lookup(InfoMode.NEVER).lookup(Arrays.asList("alone1", "alone2"));

        assertTrue(ids(answer).isEmpty());
        assertTrue(itemsAsked.isEmpty());
    }

    @Test
    public void onlyFindsAThingWithNoSingleFetchThroughInfo() {
        infoAnswer = listing("alone1");

        LookupResponse answer = lookup(InfoMode.ONLY).lookup(Collections.singletonList("alone1"));

        assertEquals(Collections.singletonList("alone1"), ids(answer));
        assertTrue(itemsAsked.isEmpty());
    }

    // ~~~ The Reddit parsers

    @Test
    public void aCommentLooksLikeInfosOwn() {
        JsonNode answer =
                json(
                        "[{\"kind\":\"Listing\",\"data\":{\"children\":[{\"kind\":\"t3\",\"data\":"
                                + "{\"id\":\"post1\",\"title\":\"The post &amp; title\"}}]}},"
                                + "{\"kind\":\"Listing\",\"data\":{\"children\":[{\"kind\":\"t1\",\"data\":"
                                + "{\"id\":\"c1\",\"link_id\":\"t3_post1\",\"body\":\"hi\",\"replies\":"
                                + "{\"kind\":\"Listing\",\"data\":{\"children\":[]}}}}]}}]");

        JsonNode child = RedditLookups.parse(new ThingRef("t1_c1", "post1"), answer);

        assertEquals("t1", child.path("kind").asText());
        assertEquals("", child.path("data").path("replies").asText("missing"));
        assertTrue(child.path("data").path("replies").isTextual());
        assertEquals("The post &amp; title", child.path("data").path("link_title").asText());
    }

    @Test
    public void aCommentKeepsALinkTitleItAlreadyHas() {
        JsonNode answer =
                json(
                        "[{\"data\":{\"children\":[{\"kind\":\"t3\",\"data\":{\"title\":\"post\"}}]}},"
                                + "{\"data\":{\"children\":[{\"kind\":\"t1\",\"data\":"
                                + "{\"id\":\"c1\",\"link_title\":\"own\"}}]}}]");

        JsonNode child = RedditLookups.parse(new ThingRef("t1_c1", "t3_post1"), answer);

        assertEquals("own", child.path("data").path("link_title").asText());
    }

    @Test
    public void aPostIsTheFirstListingsOnlyChild() {
        JsonNode answer =
                json(
                        "[{\"data\":{\"children\":[{\"kind\":\"t3\",\"data\":{\"id\":\"p1\"}}]}},"
                                + "{\"data\":{\"children\":[{\"kind\":\"t1\",\"data\":{\"id\":\"c\"}}]}}]");

        assertEquals("t3", RedditLookups.parse(new ThingRef("t3_p1", null), answer).path("kind").asText());
        // A mismatched or wrong-kind answer is left out rather than shown as something else.
        assertEquals(null, RedditLookups.parse(new ThingRef("t3_other", null), answer));
        assertEquals(null, RedditLookups.parse(new ThingRef("t1_p1", "x"), answer));
    }

    @Test
    public void onlyPostsAndCommentsWithAPostHaveASingleFetch() {
        assertEquals("/comments/abc", new ThingRef("t3_abc", null).path());
        assertEquals("/comments/post/_/abc", new ThingRef("t1_abc", "t3_post").path());
        assertEquals(null, new ThingRef("t1_abc", null).path());
        assertEquals(null, new ThingRef("t1_abc", "").path());
        assertEquals(null, new ThingRef("localsaved_t3_abc", null).path());
        assertEquals(null, new ThingRef("t3_../x", null).path());
    }
}
