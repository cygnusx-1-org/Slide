package me.edgan.redditslide.Adapters;

import java.util.ArrayList;
import net.dean.jraw.models.Contribution;

/** Created by carlo_000 on 12/3/2015. */
public class GeneralPosts {
    @SuppressWarnings("NullAway.Init") // assigned by the loader before the adapter binds
    public ArrayList<Contribution> posts;
    public boolean nomore;

    /**
     * Whether a filter the loader applies is narrowing this listing, so {@link #posts} holds the
     * matches rather than everything there is.
     *
     * <p>The adapter cannot see such a filter -- the rows are already gone by the time they reach
     * it -- and without this it reads an empty list as one that has not loaded yet and draws
     * nothing at all. Saying so lets it answer "no results" instead of a blank screen.
     */
    public boolean isNarrowed() {
        return false;
    }
}
