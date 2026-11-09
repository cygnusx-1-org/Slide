package me.edgan.redditslide.Adapters;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.Collections;
import me.edgan.redditslide.util.FakeReddit;
import net.dean.jraw.ApiException;
import net.dean.jraw.managers.AccountManager;
import net.dean.jraw.models.Comment;
import net.dean.jraw.models.CommentNode;
import net.dean.jraw.models.CommentSort;
import net.dean.jraw.models.Thing;
import net.dean.jraw.util.JrawUtils;
import org.junit.Test;

/**
 * Editing a comment, issue #318. The edit is one POST to {@code /api/editusertext}, whose answer
 * already carries the edited comment, so no {@code /api/info} refresh follows it. Reddit blocks
 * {@code /api/info} for some client ids, and that blocked refresh used to be reported as a failed
 * edit.
 */
public class CommentEditResponseTest {
    private static final String POST = "t3_abc";

    private static String commentData(String id, String body) {
        return "{\"id\":\""
                + id
                + "\",\"name\":\"t1_"
                + id
                + "\",\"body\":\""
                + body
                + "\",\"link_id\":\""
                + POST
                + "\",\"replies\":\"\"}";
    }

    /** What {@code /api/editusertext} answers with {@code api_type=json}. */
    private static String editAnswer(String id, String body) {
        return "{\"json\":{\"errors\":[],\"data\":{\"things\":[{\"kind\":\"t1\",\"data\":"
                + commentData(id, body)
                + "}]}}}";
    }

    private static Comment comment(String id, String body) {
        return new Comment(JrawUtils.fromString(commentData(id, body)));
    }

    @Test
    public void editReturnsTheEditedCommentFromItsOwnAnswer() throws Exception {
        FakeReddit reddit =
                new FakeReddit(request -> FakeReddit.Answer.json(200, editAnswer("c1", "after")));

        Thing edited =
                new AccountManager(reddit.client).updateContribution(comment("c1", "before"), "after");

        if (!(edited instanceof Comment comment)) {
            fail("expected the edited Comment, got " + edited);
            return;
        }
        assertEquals("t1_c1", comment.getFullName());
        assertEquals("after", comment.getBody());
        assertEquals(1, reddit.requests.size());
        assertTrue(reddit.requests.get(0), reddit.requests.get(0).startsWith("/api/editusertext"));
    }

    @Test
    public void editAnswerWithoutTheCommentIsStillASuccess() throws Exception {
        FakeReddit reddit =
                new FakeReddit(request -> FakeReddit.Answer.json(200, "{\"json\":{\"errors\":[]}}"));

        assertNull(
                new AccountManager(reddit.client).updateContribution(comment("c1", "before"), "after"));
        assertEquals(1, reddit.requests.size());
    }

    @Test
    public void rejectedEditThrows() throws Exception {
        FakeReddit reddit =
                new FakeReddit(
                        request ->
                                FakeReddit.Answer.json(
                                        200,
                                        "{\"json\":{\"errors\":[[\"TOO_LONG\",\"this is too long\",\"text\"]]}}"));

        try {
            new AccountManager(reddit.client).updateContribution(comment("c1", "before"), "after");
            fail("expected an ApiException");
        } catch (ApiException e) {
            assertEquals("TOO_LONG", e.getReason());
        }
    }

    @Test
    public void notifyCommentChangedSwapsTheCommentInPlace() {
        CommentNode root =
                new CommentNode(
                        POST,
                        Collections.singletonList(comment("c1", "before")),
                        null,
                        CommentSort.CONFIDENCE);
        CommentNode node = root.getChildren().get(0);

        assertSame(node, node.notifyCommentChanged(comment("c1", "after")));

        assertEquals("after", node.getComment().getBody());
        assertSame(root, node.getParent());
        assertEquals(1, node.getDepth());
    }

    @Test
    public void notifyCommentChangedRejectsAnotherComment() {
        CommentNode root =
                new CommentNode(
                        POST,
                        Collections.singletonList(comment("c1", "before")),
                        null,
                        CommentSort.CONFIDENCE);
        CommentNode node = root.getChildren().get(0);

        try {
            node.notifyCommentChanged(comment("c2", "other"));
            fail("expected an IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            assertEquals("before", node.getComment().getBody());
        }
    }
}
