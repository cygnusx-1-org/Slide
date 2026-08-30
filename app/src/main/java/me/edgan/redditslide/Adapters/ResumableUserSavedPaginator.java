package me.edgan.redditslide.Adapters;

import androidx.annotation.Nullable;

import java.util.HashMap;
import java.util.Map;

import net.dean.jraw.RedditClient;
import net.dean.jraw.models.Contribution;
import net.dean.jraw.models.Listing;
import net.dean.jraw.paginators.UserSavedPaginator;

import org.jspecify.annotations.NullMarked;

/**
 * A {@link UserSavedPaginator} that can resume from a saved cursor. See {@link ResumablePaginator},
 * and {@link ResumableUserProfilePaginator} for why the preview query arguments live here.
 */
@NullMarked
public class ResumableUserSavedPaginator extends UserSavedPaginator implements ResumablePaginator {

    @Nullable private String resumeAfter;

    /** See {@link ResumableUserProfilePaginator} for why {@code client} is nullable. */
    public ResumableUserSavedPaginator(
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
        // One request only; from here the paginator's own cursor is the fresher one.
        resumeAfter = null;
        return listing;
    }
}
