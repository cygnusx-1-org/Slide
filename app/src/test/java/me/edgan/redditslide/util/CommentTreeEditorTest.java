package me.edgan.redditslide.util;

import static me.edgan.redditslide.util.MoreCommentsFixtures.comment;
import static me.edgan.redditslide.util.MoreCommentsFixtures.more;
import static me.edgan.redditslide.util.MoreCommentsFixtures.moreChildren;
import static me.edgan.redditslide.util.MoreCommentsFixtures.thread;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import net.dean.jraw.models.CommentNode;
import net.dean.jraw.models.CommentSort;
import net.dean.jraw.models.MoreChildren;
import org.junit.Test;

public class CommentTreeEditorTest {

    private static List<JsonNode> data(JsonNode... things) {
        List<JsonNode> out = new ArrayList<>();
        for (JsonNode thing : things) {
            out.add(thing.get("data"));
        }
        return out;
    }

    private static List<CommentNode> build(CommentNode parent, JsonNode... things) {
        return CommentTreeEditor.buildChildren(parent, data(things), CommentSort.CONFIDENCE);
    }

    @Test
    public void depthFollowsTheParentThroughNestedCommentsAndMores() {
        CommentNode root = thread(null, comment("top", comment("mid")));
        CommentNode mid = root.get(0).get(0);
        assertEquals(2, mid.getDepth());

        // A focused listing reports the comment at depth 0 wherever it sits.
        CommentNode reply =
                build(mid, comment("r", comment("rr", comment("rrr"), more(3, "m1", "m2")))).get(0);

        assertEquals(3, reply.getDepth());
        assertSame(mid, reply.getParent());
        CommentNode rr = reply.get(0);
        assertEquals(4, rr.getDepth());
        assertEquals(5, rr.get(0).getDepth());
        assertSame(reply, rr.getParent());
        assertEquals(Arrays.asList("m1", "m2"), rr.getMoreChildren().getChildrenIds());
    }

    @Test
    public void topLevelRepliesAreDepthOneUnderTheRoot() {
        CommentNode root = thread(moreChildren(2, "a", "b"));
        assertEquals(1, build(root, comment("a")).get(0).getDepth());
        assertEquals("t3_post", build(root, comment("a")).get(0).getSubmissionName());
    }

    @Test
    public void aLoadedCommentsOwnMoreIsKept() {
        CommentNode root = thread(null);
        CommentNode loaded = build(root, comment("a", more(4, "x"))).get(0);
        assertEquals(Collections.singletonList("x"), loaded.getMoreChildren().getChildrenIds());
    }

    @Test
    public void buildingDoesNotTouchTheParent() {
        CommentNode root = thread(null, comment("top"));
        build(root.get(0), comment("r"));
        assertTrue(root.get(0).getChildren().isEmpty());
    }

    @Test
    public void unparseableCommentDoesNotBlockTheRest() {
        JsonNode noReplies = comment("broken");
        ((com.fasterxml.jackson.databind.node.ObjectNode) noReplies.get("data")).remove("replies");

        List<CommentNode> built = build(thread(null), noReplies, comment("fine"));

        assertEquals(1, built.size());
        assertEquals("fine", built.get(0).getComment().getId());
    }

    @Test
    public void setMoreUpdatesAndClearsTheTreesOwnNode() {
        CommentNode node = thread(null, comment("top", more(3, "a", "b"))).get(0);
        MoreChildren next = CommentTreeEditor.newMore(Collections.singletonList("b"), 1, "t1_top");

        CommentTreeEditor.setMore(node, next);
        assertSame(next, node.getMoreChildren());
        assertEquals(Integer.valueOf(1), next.getCount());
        assertFalse(node.isThreadContinuation());

        CommentTreeEditor.setMore(node, null);
        assertFalse(node.hasMoreComments());
        assertNull(node.getMoreChildren());
    }

    @Test
    public void pendingUsesRedditsCountFlooredAtTheIdCount() {
        assertEquals(0, CommentTreeEditor.pendingReplies(thread(null, comment("a")).get(0)));
        assertEquals(
                10,
                CommentTreeEditor.pendingReplies(thread(null, comment("a", more(10, "x"))).get(0)));
        assertEquals(
                3,
                CommentTreeEditor.pendingReplies(
                        thread(null, comment("a", more(1, "x", "y", "z"))).get(0)));
    }

    @Test
    public void continueThisThreadIsNotPending() {
        assertEquals(0, CommentTreeEditor.pendingReplies(thread(null, comment("a", more(0))).get(0)));
    }

    @Test
    public void totalIncludesNestedPendingAndExcludesThePlaceholder() {
        CommentNode top =
                thread(
                                null,
                                comment(
                                        "top",
                                        comment("c1"),
                                        comment("c2", comment("g1"), more(4, "x")),
                                        more(10, "y", "z")))
                        .get(0);

        // c1, c2, g1, c2's 4 pending, top's 10 pending, and nothing for either "load more" row.
        assertEquals(17, CommentTreeEditor.totalReplies(top, true));
        // Offline a more has no row and can't load, so only what's in hand counts.
        assertEquals(3, CommentTreeEditor.totalReplies(top, false));
    }

    @Test
    public void aCommentWhoseOnlyRepliesAreBehindAMoreStillCounts() {
        assertEquals(
                11,
                CommentTreeEditor.totalReplies(
                        thread(null, comment("top", more(11, "a", "b"))).get(0), true));
    }
}
