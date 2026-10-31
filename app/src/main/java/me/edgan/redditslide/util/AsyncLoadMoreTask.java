package me.edgan.redditslide.util;

import android.app.Activity;
import android.content.Context;
import android.os.AsyncTask;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.RecyclerView;
import com.fasterxml.jackson.databind.JsonNode;
import com.mikepenz.itemanimators.SlideRightAlphaAnimator;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import me.edgan.redditslide.Adapters.CommentAdapter;
import me.edgan.redditslide.Adapters.CommentItem;
import me.edgan.redditslide.Adapters.CommentObject;
import me.edgan.redditslide.Adapters.MoreChildItem;
import me.edgan.redditslide.Adapters.MoreCommentViewHolder;
import me.edgan.redditslide.Authentication;
import me.edgan.redditslide.Flair.RichFlairParser;
import me.edgan.redditslide.R;
import me.edgan.redditslide.Reddit;
import net.dean.jraw.models.CommentNode;
import net.dean.jraw.models.CommentSort;
import net.dean.jraw.models.MoreChildren;
import org.jspecify.annotations.NullMarked;

@NullMarked
public class AsyncLoadMoreTask extends AsyncTask<MoreChildItem, Void, Integer> {
    private final MoreCommentViewHolder holder;
    private final int dataPos;
    public final String fullname;

    private final Context mContext;
    private final CommentAdapter adapter;
    private final RecyclerView listView;
    private final ArrayList<CommentObject> currentComments;
    private final HashMap<String, Integer> keys;

    private ArrayList<CommentObject> finalData = new ArrayList<>();
    @Nullable private MoreChildItem targetItem;
    @Nullable private MoreCommentsResult focusedResult;
    @Nullable private MoreCommentsSession session;
    private List<CommentNode> focusedNodes = new ArrayList<>();

    public AsyncLoadMoreTask(
            int dataPos,
            MoreCommentViewHolder holder,
            String fullname,
            Context context,
            CommentAdapter adapter,
            RecyclerView listView,
            ArrayList<CommentObject> currentComments,
            HashMap<String, Integer> keys,
            MoreChildItem item) {
        this.holder = holder;
        this.dataPos = dataPos;
        this.fullname = fullname;
        this.mContext = context;
        this.adapter = adapter;
        this.listView = listView;
        this.currentComments = currentComments;
        this.keys = keys;
        this.targetItem = item;
    }

    /** The row's flag has to clear on every way out, or the placeholder ignores taps for good. */
    private void finishLoading() {
        adapter.currentLoading = null;
        if (targetItem != null) {
            targetItem.loading = false;
        }
    }

    @Override
    protected void onCancelled() {
        finishLoading();
        forgetUnappliedListing();
    }

    /**
     * A parent listing whose replies were dropped found nothing that's on screen, so the next load
     * has to be free to list the parent again instead of fetching every id on its own.
     */
    private void forgetUnappliedListing() {
        if (session != null
                && targetItem != null
                && focusedResult instanceof MoreCommentsResult.Loaded
                && ((MoreCommentsResult.Loaded) focusedResult).getListedParent()) {
            session.unmarkListed(MiscUtil.orEmpty(targetItem.comment.getComment().getId()));
        }
    }

    @Override
    public void onPostExecute(@Nullable Integer data) {
        finishLoading();
        if (isCancelled()) {
            return;
        }
        if (focusedResult != null) {
            applyFocusedResult(focusedResult);
            return;
        }
        if (data == null) {
            // Exception path; error dialog already shown from doInBackground. Keep the
            // placeholder so the user can retry.
            resetLoadingIndicator();
            return;
        }
        if (data <= 0) {
            Log.w(LogUtil.getTag(), "AsyncLoadMoreTask produced no visible rows for " + fullname);
            resetLoadingIndicator();
            return;
        }

        ((Activity) mContext)
                .runOnUiThread(
                        () -> {
                            // Re-resolve the placeholder using its stable key. The original
                            // captured index is only a hint because collapsed/hidden rows can
                            // leave us one slot off by the time this callback runs.
                            final MoreChildItem target = targetItem;
                            int currentDataPos = findPlaceholderPosition();
                            if (target == null || currentDataPos < 0) {
                                Log.w(LogUtil.getTag(), "Target MoreChildItem no longer in list; nothing to apply.");
                                resetLoadingIndicator();
                                return;
                            }

                            replacePlaceholder(currentDataPos, target, false);
                        });
    }

    /**
     * Puts {@link #finalData} where the placeholder is. Tree order is every existing reply, then
     * the new ones, then whatever's left to load, so new rows go directly before a placeholder that
     * stays.
     */
    private void replacePlaceholder(int position, MoreChildItem target, boolean placeholderStays) {
        String placeholderKey = target.getName();
        adapter.inheritHidden(placeholderKey, placeholderStays, finalData);
        if (!placeholderStays) {
            currentComments.remove(position);
            keys.remove(placeholderKey);
        }

        currentComments.addAll(position, finalData);
        for (int i = position; i < currentComments.size(); i++) {
            keys.put(currentComments.get(i).getName(), i);
        }

        if (!finalData.isEmpty()) {
            listView.setItemAnimator(new SlideRightAlphaAnimator());
        }
        adapter.invalidatePositionCache();
        adapter.notifyDataSetChanged();
    }

    /**
     * Applied here rather than in doInBackground so the tree only ever changes on the main thread,
     * where the rows and the "+N" counts read it, and a cancelled load leaves it untouched. The
     * rows hold the tree's own nodes, so a collapse and re-expand finds the same state.
     */
    private void applyFocusedResult(MoreCommentsResult result) {
        final MoreChildItem target = targetItem;
        int position = findPlaceholderPosition();
        // A refresh swapped the list while this was loading; these replies belong to the old tree.
        if (target == null || position < 0 || currentComments != adapter.currentComments) {
            Log.w(LogUtil.getTag(), "Target MoreChildItem no longer in list; nothing to apply.");
            forgetUnappliedListing();
            resetLoadingIndicator();
            return;
        }

        CommentNode parent = target.comment;
        int previous = CommentTreeEditor.pendingReplies(parent);
        List<String> remaining = result.getRemainingIds();
        String parentName = MiscUtil.orEmpty(parent.getComment().getFullName());

        if (result instanceof MoreCommentsResult.Failed) {
            // Ids found to be gone before the failure needn't be asked for again.
            if (!remaining.isEmpty() && remaining.size() < target.children.getChildrenIds().size()) {
                target.children = CommentTreeEditor.newMore(remaining, previous, parentName);
                CommentTreeEditor.setMore(parent, target.children);
            }
            resetLoadingIndicator();
            return;
        }

        MoreChildren more = null;
        if (!remaining.isEmpty()) {
            int loaded = 0;
            for (CommentObject row : finalData) {
                if (row.isComment()) {
                    loaded++;
                }
            }
            Integer redditCount = ((MoreCommentsResult.Loaded) result).getRemainingCount();
            more =
                    CommentTreeEditor.newMore(
                            remaining,
                            redditCount != null ? redditCount : Math.max(0, previous - loaded),
                            parentName);
            target.children = more;
        }
        // Into the existing list, which every row of this parent already shares.
        parent.getChildren().addAll(focusedNodes);
        CommentTreeEditor.setMore(parent, more);
        replacePlaceholder(position, target, more != null);
    }

    /** Separate so a test can load from fixtures instead of the network. */
    protected FocusedListingFetcher newFetcher(CommentNode parent, CommentSort sort) {
        return new RedditFocusedListingFetcher(
                MiscUtil.idFromFullname(parent.getSubmissionName()), sort);
    }

    private int loadFocused(MoreChildItem item) throws Exception {
        CommentNode parent = item.comment;
        CommentSort sort = adapter.dataSet.getSorting();
        session = adapter.dataSet.getMoreCommentsSession();
        MoreCommentsLoader loader = new MoreCommentsLoader(session, newFetcher(parent, sort));
        List<String> ids = item.children.getChildrenIds();
        MoreCommentsResult result =
                parent.getDepth() == 0
                        ? loader.loadTopLevel(ids)
                        : loader.loadReplies(MiscUtil.orEmpty(parent.getComment().getId()), ids);
        focusedResult = result;

        if (result instanceof MoreCommentsResult.Failed) {
            if (!isCancelled()) {
                showLoadFailedDialog(((MoreCommentsResult.Failed) result).getCause());
            }
            return 0;
        }
        focusedNodes =
                CommentTreeEditor.buildChildren(
                        parent, ((MoreCommentsResult.Loaded) result).getComments(), sort);
        return focusedNodes.isEmpty() ? 0 : appendExpandedNodes(parent, focusedNodes);
    }

    private void resetLoadingIndicator() {
        if (holder == null || holder.loading == null || holder.content == null || mContext == null) {
            Log.w(LogUtil.getTag(), "Could not reset loading indicator: holder or context was null.");
            return;
        }
        ((Activity) mContext).runOnUiThread(() -> {
            // Prefer the adapter's re-bind path; it correctly reproduces the placeholder text
            // regardless of how the list has shifted. Only fall back to direct holder mutation
            // when we can't find the item anymore.
            int currentDataPos = findPlaceholderPosition();
            if (currentDataPos >= 0) {
                adapter.notifyDataSetChanged();
                return;
            }

            // The item is gone. Make sure the captured holder at least isn't stuck on
            // "Loading more…" — clear its spinner. Text will be reset when/if it rebinds.
            holder.loading.setVisibility(View.GONE);
        });
    }

    private int findPlaceholderPosition() {
        if (targetItem == null) {
            return -1;
        }

        String placeholderKey = targetItem.getName();
        if (placeholderKey == null || placeholderKey.isEmpty()) {
            return -1;
        }

        Integer keyedPosition = keys.get(placeholderKey);
        if (keyedPosition != null && isMatchingPlaceholderPosition(keyedPosition, placeholderKey)) {
            return keyedPosition;
        }

        if (isMatchingPlaceholderPosition(dataPos, placeholderKey)) {
            return dataPos;
        }

        if (isMatchingPlaceholderPosition(dataPos - 1, placeholderKey)) {
            return dataPos - 1;
        }

        if (isMatchingPlaceholderPosition(dataPos + 1, placeholderKey)) {
            return dataPos + 1;
        }

        for (int i = 0; i < currentComments.size(); i++) {
            if (placeholderKey.equals(currentComments.get(i).getName())) {
                return i;
            }
        }

        return -1;
    }

    private boolean isMatchingPlaceholderPosition(
            @Nullable Integer position, String placeholderKey) {
        return position != null
                && position >= 0
                && position < currentComments.size()
                && placeholderKey.equals(currentComments.get(position).getName());
    }


    @Override
    protected @Nullable Integer doInBackground(MoreChildItem... params) {
        finalData = new ArrayList<>();
        int itemsAddedCount = 0;
        if (params.length > 0) {
            MoreChildItem moreChildItem = params[0];
            CommentNode parentNode = moreChildItem.comment;
            MoreChildren moreChildren = moreChildItem.children;

            try {
                if (!adapter.dataSet.getMoreCommentsSession().useOldMethod) {
                    return loadFocused(moreChildItem);
                }

                if (Authentication.reddit == null) {
                    Log.e(LogUtil.getTag(), "Authentication.reddit is null in AsyncLoadMoreTask");
                    return null;
                }

                List<CommentNode> newNodesListing = parentNode.loadMoreComments(Authentication.reddit);

                if (newNodesListing == null) {
                    Log.w(LogUtil.getTag(), "loadMoreComments returned null listing for node: " + parentNode.getComment().getId());
                    return 0;
                }

                itemsAddedCount += appendExpandedNodes(parentNode, newNodesListing);

                // JRAW occasionally resolves the request but returns no depth+1 nodes for a
                // single-child placeholder. In that case, fetch that one child directly.
                if (itemsAddedCount == 0
                        && moreChildren != null
                        && moreChildren.getChildrenIds() != null
                        && moreChildren.getChildrenIds().size() == 1) {
                    String childId = moreChildren.getChildrenIds().get(0);
                    Log.d(LogUtil.getTag(), "AsyncLoadMoreTask: falling back to insertComment for single child "
                            + childId + " under " + parentNode.getComment().getId());
                    List<CommentNode> insertedNodes = parentNode.insertComment(
                            Authentication.reddit,
                            "t1_" + childId);
                    itemsAddedCount += appendExpandedNodes(parentNode, insertedNodes);
                }

            } catch (Exception e) {
                Log.e(LogUtil.getTag(), "Cannot load more comments for node " + parentNode.getComment().getId(), e);
                showLoadFailedDialog(e);
                return null;
            }
        }
        return itemsAddedCount;
    }

    private void showLoadFailedDialog(Exception e) {
        Writer writer = new StringWriter();
        PrintWriter printWriter = new PrintWriter(writer);
        e.printStackTrace(printWriter);
        String stacktrace = writer.toString().replace(";", ",");

        final Handler mHandler = new Handler(Looper.getMainLooper());
        mHandler.post(
                () -> {
                    try {
                        // Default error messages
                        String message = mContext.getString(R.string.err_connection_failed_msg);
                        String title = mContext.getString(R.string.err_title);
                        String positiveButtonText = mContext.getString(R.string.btn_ok);

                        // Specific error handling based on stacktrace
                        if (e instanceof RateLimitPausedException) {
                            message = rateLimitMessage((RateLimitPausedException) e);
                        } else if (stacktrace.contains("UnknownHostException")
                                || stacktrace.contains("SocketTimeoutException")
                                || stacktrace.contains("ConnectException")) {
                            message = mContext.getString(R.string.err_connection_failed_msg);
                        } else if (stacktrace.contains("403 Forbidden")
                                || stacktrace.contains("401 Unauthorized")) {
                            message = mContext.getString(R.string.err_refused_request_msg);
                            // Removed re-auth attempt for simplicity, just inform user
                        } else if (stacktrace.contains("404 Not Found")
                                || stacktrace.contains("400 Bad Request")) {
                            message = mContext.getString(R.string.err_could_not_find_content_msg);
                        } else {
                            message = mContext.getString(R.string.err_general) + "\n" + e.getMessage();
                        }

                        AlertDialog.Builder builder = new AlertDialog.Builder(mContext)
                                .setTitle(title)
                                .setMessage(message)
                                .setNegativeButton(R.string.btn_close, null);

                        builder.setPositiveButton(positiveButtonText, null);
                        DialogUtil.showWithCardBackground(builder);
                    } catch (Exception ignored) {
                        Log.e(LogUtil.getTag(), "Exception showing error dialog", ignored);
                    }
                });
    }

    private String rateLimitMessage(RateLimitPausedException e) {
        int seconds = (int) Math.max(1, (e.getWaitMs() + 999) / 1000);
        if (seconds < 60) {
            return mContext.getResources()
                    .getQuantityString(R.plurals.err_rate_limited_seconds, seconds, seconds);
        }
        int minutes = (seconds + 59) / 60;
        return mContext.getResources()
                .getQuantityString(R.plurals.err_rate_limited_minutes, minutes, minutes);
    }

    private int appendExpandedNodes(CommentNode parentNode, List<CommentNode> newNodesListing) {
        int itemsAddedCount = 0;

        // JRAW's loadMoreComments returns only nodes at depth == parent.depth + 1, but the
        // network call attaches their descendants (and any nested MoreChildren) into the
        // returned nodes' subtrees. Walk each subtree so descendants reach the UI too.
        List<CommentNode> walked = new ArrayList<>();
        boolean dedupe;
        if (newNodesListing != null && !newNodesListing.isEmpty()) {
            // Normal path: these nodes are all freshly constructed by JRAW, so no dedupe.
            for (CommentNode top : newNodesListing) {
                for (CommentNode n : top.walkTree()) {
                    walked.add(n);
                }
            }
            dedupe = false;
        } else {
            // Fallback: the depth+1 filter matched nothing but the parent tree may still
            // have been mutated. Walk it and dedupe against what's already visible.
            for (CommentNode n : parentNode.walkTree()) {
                if (n == parentNode) continue;
                walked.add(n);
            }
            dedupe = true;
        }

        Log.d(LogUtil.getTag(), "AsyncLoadMoreTask: parent=" + parentNode.getComment().getId()
                + " newRootNodes=" + (newNodesListing == null ? -1 : newNodesListing.size())
                + " walked=" + walked.size() + " dedupe=" + dedupe);

        // Mirror SubmissionComments.LoadData.doInBackground: emit nodes in preorder and
        // place MoreChildItem placeholders at the correct depth via a reverse-order flush.
        Map<Integer, MoreChildItem> waiting = new HashMap<>();
        for (CommentNode n : walked) {
            Map<Integer, MoreChildItem> flush = new TreeMap<>(Collections.reverseOrder());
            flush.putAll(waiting);
            for (Integer i : flush.keySet()) {
                if (i >= n.getDepth()) {
                    finalData.add(waiting.get(i));
                    itemsAddedCount++;
                    waiting.remove(i);
                }
            }

            if (dedupe && keys.containsKey(n.getComment().getFullName())) {
                continue;
            }

            finalData.add(new CommentItem(n));
            itemsAddedCount++;

            if (n.hasMoreComments()) {
                waiting.put(n.getDepth(), new MoreChildItem(n, n.getMoreChildren()));
            }
        }

        Map<Integer, MoreChildItem> flush = new TreeMap<>(Collections.reverseOrder());
        flush.putAll(waiting);
        for (Integer i : flush.keySet()) {
            finalData.add(waiting.get(i));
            itemsAddedCount++;
        }

        // Download the newly fetched child comments' images, and read their videos' still frames,
        // before they're inserted/shown (we're on a background thread), so they render in place with
        // their comment instead of popping in, matching SubmissionComments.LoadData.
        preloadCommentMedia(finalData);

        return itemsAddedCount;
    }

    private void preloadCommentMedia(List<CommentObject> built) {
        try {
            LinkedHashSet<String> urls = new LinkedHashSet<>();
            LinkedHashSet<String> videos = new LinkedHashSet<>();
            // The emoji of any richtext author flair, warmed alongside the media so the
            // byline chip paints complete on the first bind.
            LinkedHashSet<String> flairEmoji = new LinkedHashSet<>();
            for (CommentObject o : built) {
                if (o == null || !o.isComment() || o.comment == null) {
                    continue;
                }
                try {
                    JsonNode dataNode = o.comment.getComment().getDataNode();
                    flairEmoji.addAll(
                            RichFlairParser.emojiUrls(
                                    RichFlairParser.from(
                                            dataNode, RichFlairParser.AUTHOR_FLAIR_RICHTEXT)));
                    String html =
                            SubmissionParser.replaceProcessingImgPlaceholders(
                                    dataNode.path("body_html").asText(""), dataNode);
                    urls.addAll(SubmissionParser.imageUrlsFor(html));
                    videos.addAll(SubmissionParser.videoUrlsFor(html));
                } catch (Exception ignored) {
                    // Skip comments we can't parse; they'll still load on bind.
                }
            }
            CommentImageUtil.preloadBlocking(Reddit.getAppContext(), urls);
            FlairEmojiUtil.preloadBlocking(Reddit.getAppContext(), flairEmoji);
            CommentVideoPreview.preloadBlocking(Reddit.getAppContext(), videos);
        } catch (Exception ignored) {
            // Preloading images is best-effort: on any failure the
            // images still load when a comment binds.
        }
    }
}
