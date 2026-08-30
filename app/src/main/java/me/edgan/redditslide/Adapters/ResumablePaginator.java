package me.edgan.redditslide.Adapters;

import androidx.annotation.Nullable;

import org.jspecify.annotations.NullMarked;

/**
 * A paginator that can be started partway through a listing instead of at the top.
 *
 * <p>JRAW's {@code Paginator} keeps its cursor in a private {@code current} listing with no way to
 * seed it, so a paginator rebuilt after the process died starts from page one — which, for a feed
 * restored from cache, means re-fetching the hundred posts already on screen and appending whatever
 * has since drifted onto page one to the <em>bottom</em> of the list, out of order.
 *
 * <p>The way in is {@code getExtraQueryArgs()}: {@code Paginator.next} merges it into the query
 * with {@code putAll} <em>after</em> it has set {@code after} from the current listing, so an
 * {@code after} supplied here wins. No fork needed.
 */
@NullMarked
public interface ResumablePaginator {

    /**
     * Start the next request at {@code after} — a listing cursor from a previous session — instead
     * of at the top. Cleared once that request has been made, so every page after it comes from the
     * cursor Reddit returned, exactly as an uninterrupted session would.
     */
    void setResumeAfter(@Nullable String after);
}
