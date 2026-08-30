package me.edgan.redditslide.Adapters;

import androidx.annotation.Nullable;

import java.util.HashMap;
import java.util.Map;

import net.dean.jraw.RedditClient;
import net.dean.jraw.models.Contribution;
import net.dean.jraw.models.Listing;
import net.dean.jraw.paginators.UserProfilePaginator;

import org.jspecify.annotations.NullMarked;

/**
 * A {@link UserProfilePaginator} that can resume from a saved cursor. See {@link ResumablePaginator}.
 *
 * <p>It also carries the three query arguments the profile tabs have always sent. Reddit only
 * returns post previews and thumbnails when the request asks for them; otherwise it honors the
 * account's media preference, which is why thumbnails went missing here (issue #274). These used to
 * live in an anonymous subclass at the call site, and had to move up here because {@code
 * getExtraQueryArgs} is also the seam the resume cursor goes through — an anonymous override of it
 * around this class would drop one or the other.
 */
@NullMarked
public class ResumableUserProfilePaginator extends UserProfilePaginator
        implements ResumablePaginator {

    @Nullable private String resumeAfter;

    /**
     * {@code client} is {@code @Nullable} to match {@code Authentication.reddit}, which is null
     * until the first credential check completes — the same widening
     * {@link ResumableSubredditPaginator} makes, and for the same reason.
     */
    public ResumableUserProfilePaginator(
            @Nullable RedditClient client, String where, String username) {
        super(client, where, username);
    }

    @Override
    public void setResumeAfter(@Nullable String after) {
        this.resumeAfter = after;
    }

    @Override
    protected Map<String, String> getExtraQueryArgs() {
        final Map<String, String> args = new HashMap<>();
        final Map<String, String> inherited = super.getExtraQueryArgs();
        if (inherited != null) {
            args.putAll(inherited);
        }
        args.put("feature", "link_preview");
        args.put("always_show_media", "1");
        args.put("sr_detail", "true");
        if (resumeAfter != null && !resumeAfter.isEmpty()) {
            args.put("after", resumeAfter);
        }
        return args;
    }

    @Override
    public Listing<Contribution> next(boolean forceNetwork) {
        final Listing<Contribution> listing = super.next(forceNetwork);
        // One request only. From here the paginator's own cursor is the fresher one; leaving the
        // saved token in place would make every subsequent page re-fetch the same slice.
        resumeAfter = null;
        return listing;
    }
}
