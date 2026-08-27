package me.edgan.redditslide.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import android.app.Application;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.InputStream;
import me.edgan.redditslide.ActionStates;
import me.edgan.redditslide.SubmissionViews.PopulateSubmissionViewHolder;
import net.dean.jraw.models.Submission;
import net.dean.jraw.models.VoteDirection;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/**
 * The score a feed row shows, when the vote it should show is not the vote its Submission carries.
 *
 * <p>A feed row keeps the Submission object the listing handed it, score and {@code likes} frozen
 * at load time. Voting anywhere else -- the comments view above all, which fetches a Submission of
 * its own -- records the new direction in {@link ActionStates} against the fullname and nothing
 * else. So the number on the row is only ever right because
 * {@link PopulateSubmissionViewHolder#getAdjustedScore} reconciles the two, and it is wrong by
 * exactly one vote whenever that reconciliation misses a case.
 *
 * <p>The four non-zero UPVOTE/DOWNVOTE deltas below are the behaviour that already shipped; they
 * are pinned here as regression guards. The two NO_VOTE deltas are the case that was missing: the
 * bind path applied no offset at all when a vote was withdrawn, and the click-time path applied one
 * only to your own posts, so removing a vote in the comments view un-tinted the arrow on the feed
 * row and left the count a point too high.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33, application = Application.class)
public class SubmissionScoreOffsetTest {

    private static final String FULLNAME = "t3_score1";
    private static final int SERVER_SCORE = 42;

    @After
    public void tearDown() {
        // ActionStates is process-wide static state and the test task forks one JVM for the run.
        ActionStates.upVotedFullnames.remove(FULLNAME);
        ActionStates.downVotedFullnames.remove(FULLNAME);
        ActionStates.unvotedFullnames.remove(FULLNAME);
    }

    /**
     * A submission whose only interesting properties are its score and the vote reddit baked into
     * it. Built from the shared scalar fixture because JRAW's Submission unboxes several other
     * fields, and its {@code getVote()} dereferences the {@code likes} node without a null check,
     * so the key has to be present even when it is JSON null.
     */
    private static Submission scoredAt(int score, VoteDirection baked) throws Exception {
        final ObjectNode data;
        try (InputStream input =
                SubmissionScoreOffsetTest.class
                        .getClassLoader()
                        .getResourceAsStream("submissions/galleryPost.json")) {
            assertNotNull(input);
            data = (ObjectNode) new ObjectMapper().readTree(input);
        }
        data.put("name", FULLNAME);
        data.put("score", score);
        switch (baked) {
            case UPVOTE:
                data.put("likes", true);
                break;
            case DOWNVOTE:
                data.put("likes", false);
                break;
            case NO_VOTE:
                data.putNull("likes");
                break;
        }
        return new Submission(data);
    }

    /** @return the score the row would render for a post reddit sent as {@code baked}. */
    private static int shownWhen(VoteDirection baked, VoteDirection shown) throws Exception {
        Submission submission = scoredAt(SERVER_SCORE, baked);
        ActionStates.setVoteDirection(submission, shown);
        return PopulateSubmissionViewHolder.getAdjustedScore(submission);
    }

    @Test
    public void anUnchangedVoteLeavesTheServerScoreAlone() throws Exception {
        assertEquals(SERVER_SCORE, shownWhen(VoteDirection.NO_VOTE, VoteDirection.NO_VOTE));
        assertEquals(SERVER_SCORE, shownWhen(VoteDirection.UPVOTE, VoteDirection.UPVOTE));
        assertEquals(SERVER_SCORE, shownWhen(VoteDirection.DOWNVOTE, VoteDirection.DOWNVOTE));
    }

    @Test
    public void castingAVoteMovesTheScoreByOne() throws Exception {
        assertEquals(
                "upvoting an unvoted post",
                SERVER_SCORE + 1,
                shownWhen(VoteDirection.NO_VOTE, VoteDirection.UPVOTE));
        assertEquals(
                "downvoting an unvoted post",
                SERVER_SCORE - 1,
                shownWhen(VoteDirection.NO_VOTE, VoteDirection.DOWNVOTE));
    }

    @Test
    public void flippingAVoteMovesTheScoreByTwo() throws Exception {
        assertEquals(
                "downvote flipped to upvote",
                SERVER_SCORE + 2,
                shownWhen(VoteDirection.DOWNVOTE, VoteDirection.UPVOTE));
        assertEquals(
                "upvote flipped to downvote",
                SERVER_SCORE - 2,
                shownWhen(VoteDirection.UPVOTE, VoteDirection.DOWNVOTE));
    }

    /**
     * The case the feed used to miss entirely. Withdrawing a vote has to take the score back with
     * it, whoever wrote the post -- the vote reddit baked into {@code score} is the user's own
     * either way.
     */
    @Test
    public void withdrawingAVoteTakesTheScoreBackWithIt() throws Exception {
        assertEquals(
                "an upvote removed",
                SERVER_SCORE - 1,
                shownWhen(VoteDirection.UPVOTE, VoteDirection.NO_VOTE));
        assertEquals(
                "a downvote removed",
                SERVER_SCORE + 1,
                shownWhen(VoteDirection.DOWNVOTE, VoteDirection.NO_VOTE));
    }

    /**
     * ActionStates is keyed by fullname, so it answers for a Submission object that never carried
     * the vote -- which is exactly what a feed row holds after the comments view, with its own
     * freshly fetched Submission, records one.
     */
    @Test
    public void theOffsetFollowsTheFullnameAcrossObjects() throws Exception {
        Submission votedOn = scoredAt(SERVER_SCORE, VoteDirection.NO_VOTE);
        ActionStates.setVoteDirection(votedOn, VoteDirection.UPVOTE);

        Submission feedRow = scoredAt(SERVER_SCORE, VoteDirection.NO_VOTE);
        assertEquals(
                "a different object with the same fullname shows the vote",
                SERVER_SCORE + 1,
                PopulateSubmissionViewHolder.getAdjustedScore(feedRow));
    }
}
