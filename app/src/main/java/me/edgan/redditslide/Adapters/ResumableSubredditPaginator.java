package me.edgan.redditslide.Adapters;

import androidx.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;
import net.dean.jraw.RedditClient;
import net.dean.jraw.http.NetworkException;
import net.dean.jraw.models.Listing;
import net.dean.jraw.models.Submission;
import net.dean.jraw.paginators.SubredditPaginator;
import org.jspecify.annotations.NullMarked;

/** A {@link SubredditPaginator} that can resume from a saved cursor. See {@link ResumablePaginator}. */
@NullMarked
public class ResumableSubredditPaginator extends SubredditPaginator implements ResumablePaginator {

    @Nullable private String resumeAfter;

    /**
     * {@code client} is {@code @Nullable} to match {@code Authentication.reddit}, which is null
     * until the first credential check completes. JRAW is unannotated and this class only widens
     * what {@code SubredditPosts} already passed to the paginators it replaces; guarding here would
     * change when a listing fails, which is not this class's business.
     */
    public ResumableSubredditPaginator(@Nullable RedditClient client) {
        super(client);
    }

    /** See {@link #ResumableSubredditPaginator(RedditClient)} for why {@code client} is nullable. */
    public ResumableSubredditPaginator(@Nullable RedditClient client, String subreddit) {
        super(client, subreddit);
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
        if (resumeAfter != null && !resumeAfter.isEmpty()) {
            args.put("after", resumeAfter);
        }
        return args;
    }

    @Override
    public Listing<Submission> next(boolean forceNetwork) throws NetworkException {
        final Listing<Submission> listing = super.next(forceNetwork);
        // One request only. From here the paginator's own cursor is the fresher one; leaving the
        // saved token in place would make every subsequent page re-fetch the same slice.
        resumeAfter = null;
        return listing;
    }
}
