package me.edgan.redditslide.test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;

import me.edgan.redditslide.util.PostRecovery;
import org.junit.Test;

/**
 * Covers {@link PostRecovery#recoverField}, the pure gate shared by both the post and comment
 * recovery: reinstate the archive's value only where Reddit is actually hiding the live one, and
 * never reinstate a placeholder snapshot. Kept as a unit so the rule that decides whether an
 * intact-but-deleted-author item keeps its live body/author is testable without JRAW or a network.
 */
public class PostRecoveryTest {

    @Test
    public void recoversWhenLiveIsHiddenAndArchiveIsReal() {
        assertThat(PostRecovery.recoverField("[removed]", "the real body"), is("the real body"));
        assertThat(PostRecovery.recoverField("[deleted]", "coffca"), is("coffca"));
        assertThat(
                PostRecovery.recoverField("[ Removed by moderator ]", "original"), is("original"));
        assertThat(PostRecovery.recoverField("  [Removed]  ", "original"), is("original"));
    }

    @Test
    public void keepsNothingWhenLiveIsIntact() {
        // Reddit still serves the real value, so there is nothing to recover — even if the archive
        // has one. This is what stops an author-only recovery from reinstating an intact body.
        assertThat(PostRecovery.recoverField("a real intact body", "archived body"), is(nullValue()));
        assertThat(PostRecovery.recoverField("realuser", "realuser"), is(nullValue()));
    }

    @Test
    public void neverReinstatesAPlaceholderSnapshot() {
        // The archive only ever saw the removed copy.
        assertThat(PostRecovery.recoverField("[removed]", "[removed]"), is(nullValue()));
        assertThat(PostRecovery.recoverField("[deleted]", "[ Removed by Reddit ]"), is(nullValue()));
        assertThat(PostRecovery.recoverField("[removed]", null), is(nullValue()));
    }

    @Test
    public void nullLiveValueIsNotTreatedAsHidden() {
        // A null live value isn't a recognizable placeholder, so there's nothing to key a recovery
        // off — leave it alone rather than guess.
        assertThat(PostRecovery.recoverField(null, "something"), is(nullValue()));
    }
}
