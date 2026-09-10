package me.edgan.redditslide.Adapters;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.res.TypedArray;
import android.graphics.Typeface;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;
import com.devspark.robototextview.RobotoTypefaces;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import me.edgan.redditslide.ActionStates;
import me.edgan.redditslide.Authentication;
import me.edgan.redditslide.HasSeen;
import me.edgan.redditslide.Hidden;
import me.edgan.redditslide.OpenRedditLink;
import me.edgan.redditslide.R;
import me.edgan.redditslide.SettingValues;
import me.edgan.redditslide.SubmissionViews.PopulateSubmissionViewHolder;
import me.edgan.redditslide.Views.CatchStaggeredGridLayoutManager;
import me.edgan.redditslide.Views.CreateCardView;
import me.edgan.redditslide.Visuals.FontPreferences;
import me.edgan.redditslide.Visuals.Palette;
import me.edgan.redditslide.markdown.MarkdownImages;
import me.edgan.redditslide.util.CompatUtil;
import me.edgan.redditslide.util.MiscUtil;
import me.edgan.redditslide.util.SavedCommentActions;
import me.edgan.redditslide.util.SubmissionParser;
import me.edgan.redditslide.util.TimeUtils;
import net.dean.jraw.models.Comment;
import net.dean.jraw.models.Contribution;
import net.dean.jraw.models.Submission;
import net.dean.jraw.models.VoteDirection;

public class ContributionAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder>
        implements BaseAdapter {

    private final int SPACER = 6;
    private static final int COMMENT = 1;
    public final Activity mContext;
    private final RecyclerView listView;
    private final Boolean isHiddenPost;
    public GeneralPosts dataSet;

    // Search/filter state
    /**
     * The rows a search narrowed the listing down to, or {@code null} when no search is running.
     *
     * <p>The listing itself stays in {@code dataSet.posts} throughout. It used to be overwritten
     * with this subset, which is why a search had to load the whole history before it could filter
     * anything: the first filtered page became the list the next page appended to, and the rest of
     * the listing was gone.
     */
    @Nullable private ArrayList<Contribution> filteredData = null;
    @Nullable private String currentQuery = null;
    @Nullable private String currentWhere = null;

    public ContributionAdapter(Activity mContext, GeneralPosts dataSet, RecyclerView listView) {
        this.mContext = mContext;
        this.listView = listView;
        this.dataSet = dataSet;

        this.isHiddenPost = false;
    }

    public ContributionAdapter(
            Activity mContext, GeneralPosts dataSet, RecyclerView listView, Boolean isHiddenPost) {
        this.mContext = mContext;
        this.listView = listView;
        this.dataSet = dataSet;

        this.isHiddenPost = isHiddenPost;
    }

    private final int LOADING_SPINNER = 5;
    /** Shown in place of the footer when a search is active and has matched nothing. */
    private static final int NO_RESULTS = 7;
    private final int NO_MORE = 3;

    @Override
    public int getItemViewType(int position) {
        final ArrayList<Contribution> rows = visible();
        if (position == 0) {
            return SPACER;
        }
        position -= 1;
        if (position == rows.size()) {
            // "No results" is a conclusion, so it waits until there is nothing left to page. A
            // search whose first hit is deep in the history would otherwise open by announcing it
            // had found nothing, while the strip beside it was still counting posts.
            if (isNarrowed() && rows.isEmpty() && dataSet.nomore) {
                return NO_RESULTS;
            }
            return dataSet.nomore ? NO_MORE : LOADING_SPINNER;
        }
        if (rows.get(position) instanceof Comment) return COMMENT;

        return 2;
    }

    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup viewGroup, int i) {

        if (i == SPACER) {
            View v =
                    LayoutInflater.from(viewGroup.getContext())
                            .inflate(R.layout.spacer, viewGroup, false);
            return new SpacerViewHolder(v);

        } else if (i == COMMENT) {
            View v =
                    LayoutInflater.from(viewGroup.getContext())
                            .inflate(R.layout.profile_comment, viewGroup, false);
            return new ProfileCommentViewHolder(v);
        } else if (i == LOADING_SPINNER) {
            View v =
                    LayoutInflater.from(viewGroup.getContext())
                            .inflate(R.layout.loadingmore, viewGroup, false);
            return new SubmissionFooterViewHolder(v);
        } else if (i == NO_MORE) {
            View v =
                    LayoutInflater.from(viewGroup.getContext())
                            .inflate(R.layout.nomoreposts, viewGroup, false);
            return new SubmissionFooterViewHolder(v);
        } else if (i == NO_RESULTS) {
            View v =
                    LayoutInflater.from(viewGroup.getContext())
                            .inflate(R.layout.nosearchresults, viewGroup, false);
            return new SubmissionFooterViewHolder(v);
        } else {
            View v = CreateCardView.CreateView(viewGroup);
            return new CardSubmissionViewHolder(v);
        }
    }

    public static class SubmissionFooterViewHolder extends RecyclerView.ViewHolder {
        public SubmissionFooterViewHolder(View itemView) {
            super(itemView);
        }
    }

    @Override
    // Listeners capture the bound Submission and recompute the live position via
    // dataSet.posts.indexOf(submission) when needed, so the bind-time position is never
    // used for stale lookups.
    @SuppressLint("RecyclerView")
    public void onBindViewHolder(final RecyclerView.ViewHolder firstHolder, final int pos) {
        int i = pos != 0 ? pos - 1 : pos;

        if (firstHolder instanceof CardSubmissionViewHolder holder) {
            final Submission submission = (Submission) visible().get(i);
            CreateCardView.resetColorCard(holder.itemView);
            if (submission.getSubredditName() != null)
                CreateCardView.colorCard(
                        submission.getSubredditName().toLowerCase(Locale.ENGLISH),
                        holder.itemView,
                        "no_subreddit",
                        false);
            holder.itemView.setOnLongClickListener(
                    new View.OnLongClickListener() {
                        @Override
                        public boolean onLongClick(View v) {
                            CharSequence titleText = CompatUtil.fromHtml(submission.getTitle());
                            if (hasActiveFilter() && currentQuery != null) {
                                titleText = highlightSearchTerms(titleText, currentQuery);
                            }
                            SubmissionAdapterHelper.showSubmissionLongPressDialog(
                                    mContext,
                                    submission,
                                    titleText,
                                    firstHolder.itemView,
                                    ContributionAdapter.this,
                                    visible(),
                                    listView);
                            return true;
                        }
                    });
            new PopulateSubmissionViewHolder()
                    .populateSubmissionViewHolder(
                            holder,
                            submission,
                            mContext,
                            false,
                            false,
                            visible(),
                            listView,
                            false,
                            false,
                            null,
                            null);

            // Apply search highlighting to submission title if filter is active
            if (hasActiveFilter() && currentQuery != null && holder.title != null) {
                CharSequence titleText = holder.title.getText();
                if (titleText != null && titleText.length() > 0) {
                    CharSequence highlightedTitle = highlightSearchTerms(titleText, currentQuery);
                    holder.title.setText(highlightedTitle);
                }
            }

            final ImageView hideButton = holder.itemView.findViewById(R.id.hide);
            if (hideButton != null && isHiddenPost) {
                hideButton.setOnClickListener(
                        new View.OnClickListener() {
                            @Override
                            public void onClick(View v) {
                                final int pos = visible().indexOf(submission);
                                if (pos < 0) {
                                    // Already gone -- a page that landed between this bind and
                                    // this tap re-filtered the list out from under the row.
                                    return;
                                }
                                final Contribution old = visible().get(pos);
                                visible().remove(submission);
                                // Unhiding has to drop the row from the listing too, or a search
                                // that is cleared afterwards puts it straight back on screen.
                                if (filteredData != null) {
                                    dataSet.posts.remove(submission);
                                }
                                notifyItemRemoved(pos + 1);

                                Hidden.undoHidden(old);
                            }
                        });
            }
            holder.itemView.setOnClickListener(
                    new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            String url = "www.reddit.com" + submission.getPermalink();
                            url = url.replace("?ref=search_posts", "");
                            OpenRedditLink.openUrl(mContext, url, true);
                            if (SettingValues.storeHistory) {
                                if ((SettingValues.storeNSFWHistory && submission.isNsfw())
                                        || !submission.isNsfw())
                                    HasSeen.addSeen(submission.getFullName());
                            }

                            notifyItemChanged(pos);
                        }
                    });

        } else if (firstHolder instanceof ProfileCommentViewHolder) {
            // IS COMMENT
            ProfileCommentViewHolder holder = (ProfileCommentViewHolder) firstHolder;
            final Comment comment = (Comment) visible().get(i);

            String scoreText;
            if (comment.isScoreHidden()) {
                scoreText =
                        "[" + mContext.getString(R.string.misc_score_hidden).toUpperCase(Locale.getDefault()) + "]";
            } else {
                scoreText = String.format(Locale.getDefault(), "%d", comment.getScore());
            }

            SpannableStringBuilder score = new SpannableStringBuilder(scoreText);

            if (score == null || score.toString().isEmpty()) {
                score = new SpannableStringBuilder("0");
            }
            if (!scoreText.contains("[")) {
                score.append(
                        String.format(
                                Locale.getDefault(),
                                " %s",
                                mContext.getResources()
                                        .getQuantityString(R.plurals.points, comment.getScore())));
            }
            holder.score.setText(score);

            if (Authentication.isLoggedIn) {
                if (ActionStates.getVoteDirection(comment) == VoteDirection.UPVOTE) {
                    holder.score.setTextColor(
                            ContextCompat.getColor(mContext, R.color.md_orange_500));
                } else if (ActionStates.getVoteDirection(comment) == VoteDirection.DOWNVOTE) {
                    holder.score.setTextColor(
                            ContextCompat.getColor(mContext, R.color.md_blue_500));
                } else {
                    holder.score.setTextColor(holder.time.getCurrentTextColor());
                }
            }
            String spacer = mContext.getString(R.string.submission_properties_seperator);
            SpannableStringBuilder titleString = new SpannableStringBuilder();

            String timeAgo = TimeUtils.getTimeAgo(comment.getCreated().getTime(), mContext);
            String time =
                    ((timeAgo == null || timeAgo.isEmpty())
                            ? "just now"
                            : timeAgo); // some users were crashing here
            time =
                    time
                            + (((comment.getEditDate() != null)
                                    ? " (edit "
                                            + TimeUtils.getTimeAgo(
                                                    comment.getEditDate().getTime(), mContext)
                                            + ")"
                                    : ""));
            titleString.append(time);
            titleString.append(spacer);

            if (comment.getSubredditName() != null) {
                String subname = comment.getSubredditName();
                SpannableStringBuilder subreddit = new SpannableStringBuilder("/r/" + subname);
                if ((SettingValues.colorSubName
                        && Palette.getColor(subname) != Palette.getDefaultColor())) {
                    subreddit.setSpan(
                            new ForegroundColorSpan(Palette.getColor(subname)),
                            0,
                            subreddit.length(),
                            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    subreddit.setSpan(
                            new StyleSpan(Typeface.BOLD),
                            0,
                            subreddit.length(),
                            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                }

                // Highlight search terms in subreddit name
                if (hasActiveFilter() && currentQuery != null) {
                    String[] searchTerms = ContributionFilter.parseTerms(currentQuery);
                    for (String term : searchTerms) {
                        if (term.isEmpty()) continue;
                        Pattern pattern = Pattern.compile(Pattern.quote(term), Pattern.CASE_INSENSITIVE);
                        Matcher matcher = pattern.matcher(subreddit.toString());
                        while (matcher.find()) {
                            subreddit.setSpan(
                                new android.text.style.BackgroundColorSpan(0x80FFFF00),
                                matcher.start(),
                                matcher.end(),
                                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                            );
                        }
                    }
                }

                titleString.append(subreddit);
            }

            holder.time.setText(titleString);
            if (SettingValues.markdownNewReddit) {
                // New Reddit-style: render the raw markdown body via Markwon (issue #179).
                setViewsMarkdown(
                        MiscUtil.orEmpty(comment.getBody()),
                        comment.getDataNode().path("body_html").asText(""),
                        comment.getDataNode(),
                        MiscUtil.orEmpty(comment.getSubredditName()),
                        holder,
                        hasActiveFilter() ? currentQuery : null);
            } else {
                // Pass search query for highlighting comment body
                setViews(
                        SubmissionParser.replaceProcessingImgPlaceholders(
                                comment.getDataNode().path("body_html").asText(""),
                                comment.getDataNode()),
                        MiscUtil.orEmpty(comment.getSubredditName()),
                        holder,
                        hasActiveFilter() ? currentQuery : null);
            }

            int type = new FontPreferences(mContext).getFontTypeComment().getTypeface();
            Typeface typeface;
            if (type >= 0) {
                typeface = RobotoTypefaces.obtainTypeface(mContext, type);
            } else {
                typeface = Typeface.DEFAULT;
            }
            holder.content.setTypeface(typeface);

            ((TextView) holder.gild).setText("");
            if (!SettingValues.hideCommentAwards
                    && (comment.getTimesSilvered() > 0
                            || comment.getTimesGilded() > 0
                            || comment.getTimesPlatinized() > 0)) {
                TypedArray a =
                        mContext.obtainStyledAttributes(
                                new FontPreferences(mContext).getPostFontStyle().getResId(),
                                R.styleable.FontStyle);
                int fontsize =
                        (int)
                                (a.getDimensionPixelSize(R.styleable.FontStyle_font_cardtitle, -1)
                                        * .75);
                a.recycle();
                holder.gild.setVisibility(View.VISIBLE);
                // Add silver, gold, platinum icons and counts in that order
                MiscUtil.addAwards(
                        mContext, fontsize, holder, comment.getTimesSilvered(), R.drawable.silver);
                MiscUtil.addAwards(
                        mContext, fontsize, holder, comment.getTimesGilded(), R.drawable.gold);
                MiscUtil.addAwards(
                        mContext,
                        fontsize,
                        holder,
                        comment.getTimesPlatinized(),
                        R.drawable.platinum);
            } else if (holder.gild.getVisibility() == View.VISIBLE)
                holder.gild.setVisibility(View.GONE);

            if (comment.getSubmissionTitle() != null) {
                CharSequence titleText = CompatUtil.fromHtml(comment.getSubmissionTitle());
                if (hasActiveFilter() && currentQuery != null) {
                    titleText = highlightSearchTerms(titleText, currentQuery);
                }
                holder.title.setText(titleText);
            } else {
                holder.title.setText(CompatUtil.fromHtml(comment.getAuthor()));
            }

            // Comment rows had no menu at all before this, so a saved comment could never be
            // tagged after the fact. The button is the same one the submission cards carry, in the
            // same place; long-press opens it too, matching how a submission row behaves.
            // An unsave only takes the row out of a listing that is the saved listing; on the
            // profile's other comment tabs the row belongs there either way.
            final SavedCommentActions.OnUnsaved onUnsaved =
                    dataSet instanceof ContributionPostsSaved
                            ? ContributionAdapter.this::removeContribution
                            : null;
            final View.OnClickListener openSheet =
                    v ->
                            SavedCommentActions.showBottomSheet(
                                    mContext, comment, firstHolder.itemView, onUnsaved);
            holder.menu.setVisibility(View.VISIBLE);
            holder.menu.setOnClickListener(openSheet);
            holder.itemView.setOnLongClickListener(
                    new View.OnLongClickListener() {
                        @Override
                        public boolean onLongClick(View v) {
                            openSheet.onClick(v);
                            return true;
                        }
                    });
            holder.itemView.setOnClickListener(
                    new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            OpenRedditLink.openUrl(
                                    mContext,
                                    comment.getSubmissionId(),
                                    comment.getSubredditName(),
                                    comment.getId());
                        }
                    });
            holder.content.setOnClickListener(
                    new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            OpenRedditLink.openUrl(
                                    mContext,
                                    comment.getSubmissionId(),
                                    comment.getSubredditName(),
                                    comment.getId());
                        }
                    });

        } else if (firstHolder instanceof SpacerViewHolder) {
            firstHolder.itemView.setLayoutParams(
                    new LinearLayout.LayoutParams(
                            firstHolder.itemView.getWidth(),
                            mContext.requireViewById(R.id.header).getHeight()));
            if (listView.getLayoutManager() instanceof CatchStaggeredGridLayoutManager) {
                CatchStaggeredGridLayoutManager.LayoutParams layoutParams =
                        new CatchStaggeredGridLayoutManager.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                mContext.requireViewById(R.id.header).getHeight());
                layoutParams.setFullSpan(true);
                firstHolder.itemView.setLayoutParams(layoutParams);
            }
        }
    }

    public static class SpacerViewHolder extends RecyclerView.ViewHolder {
        public SpacerViewHolder(View itemView) {
            super(itemView);
        }
    }

    /**
     * New Reddit-style rendering of a profile/saved comment: render the raw markdown via Markwon
     * into the single content TextView and clear the overflow block list. See issue #179.
     */
    private void setViewsMarkdown(
            String rawMarkdown,
            String bodyHtml,
            JsonNode dataNode,
            String subredditName,
            ProfileCommentViewHolder holder,
            @Nullable String searchQuery) {
        // Render the body without renderInto's own search highlight: that one uses the
        // subreddit color and is reserved for the in-thread comment search. Apply the
        // same highlight the post title uses instead, so title and body colors match.
        MarkdownImages.renderInto(
                holder.content, holder.overflow, subredditName, rawMarkdown, bodyHtml, dataNode);

        if (searchQuery != null && !searchQuery.trim().isEmpty()) {
            CharSequence body = holder.content.getText();
            if (body != null && body.length() > 0) {
                holder.content.setText(highlightSearchTerms(body, searchQuery));
            }
        }
    }

    private void setViews(String rawHTML, String subredditName, ProfileCommentViewHolder holder, @Nullable String searchQuery) {
        if (rawHTML.isEmpty()) {
            return;
        }

        List<String> blocks = SubmissionParser.getBlocks(rawHTML);

        int startIndex = 0;
        // the <div class="md"> case is when the body contains a table or code block first
        if (!blocks.get(0).equals("<div class=\"md\">")) {
            holder.content.setVisibility(View.VISIBLE);
            holder.content.setTextHtml(blocks.get(0), subredditName);

            // Apply search highlighting to comment body after HTML is processed
            if (searchQuery != null && holder.content.getText() != null) {
                CharSequence currentText = holder.content.getText();
                CharSequence highlightedText = highlightSearchTerms(currentText, searchQuery);
                holder.content.setText(highlightedText);
            }

            startIndex = 1;
        } else {
            holder.content.setText("");
            holder.content.setVisibility(View.GONE);
        }

        if (blocks.size() > 1) {
            if (startIndex == 0) {
                holder.overflow.setViews(blocks, subredditName);
            } else {
                holder.overflow.setViews(blocks.subList(startIndex, blocks.size()), subredditName);
            }
        } else {
            holder.overflow.removeAllViews();
        }
    }

    /**
     * The rows on screen: the search's hits while one is active, otherwise the whole listing.
     *
     * <p>Every render path reads through here so that narrowing the view never narrows the listing
     * the loader is still paging into.
     */
    private ArrayList<Contribution> visible() {
        final ArrayList<Contribution> filtered = filteredData;
        if (filtered != null) {
            return filtered;
        }
        // The loader only assigns dataSet.posts on a page that brought rows back, so a filter that
        // has matched nothing leaves it null indefinitely -- not just before the first load.
        // Answering with an empty list rather than null keeps that state readable as "nothing to
        // show", which is what it is, instead of an NPE waiting at every caller.
        final ArrayList<Contribution> all = dataSet.posts;
        return all != null ? all : new ArrayList<>();
    }

    /**
     * Drop a row the user acted itself out of -- unsaving a comment from the Saved tab. Mirrors the
     * unhide path: when a search filter is active the item has to leave the backing listing too, or
     * clearing the search puts it straight back on screen.
     */
    private void removeContribution(Contribution contribution) {
        final int pos = visible().indexOf(contribution);
        if (pos < 0) {
            // Already gone -- a reload landed between the sheet opening and the tap.
            return;
        }
        visible().remove(contribution);
        if (filteredData != null) {
            dataSet.posts.remove(contribution);
        }
        if (visible().isEmpty()) {
            // Emptying the list changes more than one row. Unnarrowed it reports zero rows, so the
            // spacer and footer go with the last item and a lone notifyItemRemoved would leave
            // RecyclerView expecting two rows the adapter no longer has -- which it reports as
            // corruption. Narrowed the count holds at two, but the footer becomes the "no results"
            // row, which is a different view type at the same position. A full rebind is the one
            // answer that is right for both.
            notifyDataSetChanged();
        } else {
            notifyItemRemoved(pos + 1);
        }
    }

    @Override
    public int getItemCount() {
        if (isNarrowed()) {
            // This sits above the null check below on purpose: a filter that matched nothing never
            // got dataSet.posts assigned, so that check would answer "no rows at all" and swallow
            // the "no results" row.
            if (visible().isEmpty()) {
                // A search that has matched nothing so far still needs a row to say so; without
                // one the screen is blank, which reads exactly like a search that never finished.
                //
                // A loader-side filter is the other way round. It narrows on a fresh load, where
                // the refresh layout is already spinning, so a footer spinner here would put a
                // second one on screen beside it -- that case waits until there is a conclusion
                // to state, and draws only the "no results" row.
                return hasActiveFilter() || dataSet.nomore ? 2 : 0;
            }
            return visible().size() + 2;
        }
        if (dataSet.posts == null || dataSet.posts.isEmpty()) {
            return 0;
        }
        return dataSet.posts.size() + 2; // Include spacer and footer
    }

    @Override
    public void setError(Boolean b) {
        listView.setAdapter(new ErrorAdapter());
    }

    @Override
    public void undoSetError() {
        listView.setAdapter(this);
    }

    /**
     * Called by GeneralPosts after data has been updated.
     * Re-applies filter if one is active.
     */
    public void onDataUpdated() {
        if (hasActiveFilter() && currentQuery != null && dataSet.posts != null) {
            // Re-filter the listing as it now stands, so each page the loader brings in adds its
            // hits to what is already on screen.
            filteredData =
                    ContributionFilter.filterContributions(
                            dataSet.posts, currentQuery, currentWhere);
        }
    }

    /**
     * As {@link #onDataUpdated()}, for a loader that knows which rows it just appended.
     *
     * <p>Filtering runs on the main thread and reads every field of every row, body text included.
     * Re-filtering the whole listing after each page makes that quadratic in the length of the
     * history -- a thousand-post tab paged in tens re-walks a hundred thousand rows -- so a load
     * that only added to the list only filters what it added.
     *
     * @param added the rows this load appended, or {@code null} if it replaced the list instead
     */
    public void onDataUpdated(@Nullable List<Contribution> added) {
        if (added == null || filteredData == null || !hasActiveFilter() || currentQuery == null) {
            onDataUpdated();
            return;
        }
        filteredData.addAll(
                ContributionFilter.filterContributions(
                        new ArrayList<>(added), currentQuery, currentWhere));
    }

    /**
     * Applies a search filter to the contributions list.
     *
     * @param query The search query string
     * @param where The tab/section name (e.g., "Overview", "Comments", etc.)
     */
    public void applyFilter(String query, String where) {
        if (query == null || query.trim().isEmpty()) {
            clearFilter();
            return;
        }

        // Always store the search query and location, even if no data yet
        currentQuery = query;
        currentWhere = where;

        filteredData =
                dataSet.posts == null
                        ? new ArrayList<>()
                        : ContributionFilter.filterContributions(dataSet.posts, query, where);
        notifyDataSetChanged();
    }

    /**
     * Clears the active search filter and restores original data.
     */
    public void clearFilter() {
        // Always clear the query state, so the filter is not re-applied when new data loads
        currentQuery = null;
        currentWhere = null;

        if (filteredData != null) {
            filteredData = null;
            notifyDataSetChanged();
        }
    }

    /**
     * Checks if a search filter is currently active.
     *
     * @return true if filter is active, false otherwise
     */
    public boolean hasActiveFilter() {
        return currentQuery != null && !currentQuery.trim().isEmpty();
    }

    /**
     * Whether the rows on screen are a narrowed view of the listing -- by the search box here, or
     * by a filter the loader applied before the rows ever arrived. Either way an empty result is an
     * answer rather than a list that has not loaded, so it gets the "no results" row.
     *
     * <p>Only the row count and the footer's type read this. Search highlighting still keys off
     * {@link #hasActiveFilter()}, because there is no query to highlight in the loader's case.
     */
    private boolean isNarrowed() {
        return hasActiveFilter() || dataSet.isNarrowed();
    }

    /**
     * Gets the number of results after filtering.
     *
     * @return Number of filtered results, or -1 if no filter is active
     */
    public int getResultCount() {
        if (filteredData != null) {
            return filteredData.size();
        }
        return -1;
    }

    /**
     * Gets the current search query.
     *
     * @return The current query string, or null if no filter is active
     */
    public @Nullable String getCurrentQuery() {
        return currentQuery;
    }

    /**
     * Highlights search terms in the given text.
     *
     * @param text The original text (can be String or CharSequence with existing spans)
     * @param query The search query with terms to highlight
     * @return SpannableStringBuilder with highlighted terms and preserved original spans
     */
    private CharSequence highlightSearchTerms(CharSequence text, String query) {
        if (query == null || query.trim().isEmpty() || text == null || text.length() == 0) {
            return text;
        }

        // Create a SpannableStringBuilder preserving existing spans
        SpannableStringBuilder spannable;
        if (text instanceof SpannableStringBuilder) {
            spannable = (SpannableStringBuilder) text;
        } else {
            spannable = new SpannableStringBuilder(text);
        }

        String textString = text.toString();

        // Split the query the same way the matcher does, so a quoted phrase highlights as one run
        String[] searchTerms = ContributionFilter.parseTerms(query);

        // Highlight each term
        for (String term : searchTerms) {
            if (term.isEmpty()) continue;

            // Create case-insensitive pattern
            Pattern pattern = Pattern.compile(Pattern.quote(term), Pattern.CASE_INSENSITIVE);
            Matcher matcher = pattern.matcher(textString);

            // Find and highlight all occurrences
            while (matcher.find()) {
                int start = matcher.start();
                int end = matcher.end();

                // Apply yellow background highlight
                spannable.setSpan(
                    new android.text.style.BackgroundColorSpan(0x80FFFF00), // Semi-transparent yellow
                    start,
                    end,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                );
            }
        }

        return spannable;
    }

    public static class EmptyViewHolder extends RecyclerView.ViewHolder {
        public EmptyViewHolder(View itemView) {
            super(itemView);
        }
    }
}
