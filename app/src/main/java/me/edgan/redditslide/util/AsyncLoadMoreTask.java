package me.edgan.redditslide.util;

import android.app.Activity;
import android.content.Context;
import android.os.AsyncTask;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.RecyclerView;
import com.mikepenz.itemanimators.SlideRightAlphaAnimator;
import me.edgan.redditslide.Adapters.CommentAdapter;
import me.edgan.redditslide.Adapters.CommentObject;
import me.edgan.redditslide.Adapters.CommentItem;
import me.edgan.redditslide.Adapters.MoreChildItem;
import me.edgan.redditslide.Adapters.MoreCommentViewHolder;
import me.edgan.redditslide.Authentication;
import me.edgan.redditslide.R;
import net.dean.jraw.models.CommentNode;
import net.dean.jraw.models.MoreChildren;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public class AsyncLoadMoreTask extends AsyncTask<MoreChildItem, Void, Integer> {
    private final MoreCommentViewHolder holder;
    private final int holderPos;
    private final int dataPos;
    public final String fullname;

    private final Context mContext;
    private final CommentAdapter adapter;
    private final RecyclerView listView;
    private final ArrayList<CommentObject> currentComments;
    private final HashMap<String, Integer> keys;

    private ArrayList<CommentObject> finalData;
    private MoreChildItem targetItem;

    public AsyncLoadMoreTask(
            int dataPos,
            int holderPos,
            MoreCommentViewHolder holder,
            String fullname,
            Context context,
            CommentAdapter adapter,
            RecyclerView listView,
            ArrayList<CommentObject> currentComments,
            HashMap<String, Integer> keys) {
        this.holderPos = holderPos;
        this.holder = holder;
        this.dataPos = dataPos;
        this.fullname = fullname;
        this.mContext = context;
        this.adapter = adapter;
        this.listView = listView;
        this.currentComments = currentComments;
        this.keys = keys;
    }

    @Override
    public void onPostExecute(Integer data) {
        adapter.currentLoading = null;
        if (isCancelled()) {
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

        final int itemsToAdd = data;
        ((Activity) mContext)
                .runOnUiThread(
                        () -> {
                            // Re-resolve the placeholder using its stable key. The original
                            // captured index is only a hint because collapsed/hidden rows can
                            // leave us one slot off by the time this callback runs.
                            int currentDataPos = findPlaceholderPosition();
                            if (currentDataPos < 0) {
                                Log.w(LogUtil.getTag(), "Target MoreChildItem no longer in list; nothing to apply.");
                                resetLoadingIndicator();
                                return;
                            }

                            String placeholderKey = targetItem.getName();
                            currentComments.remove(currentDataPos);
                            keys.remove(placeholderKey);

                            if (itemsToAdd > 0) {
                                currentComments.addAll(currentDataPos, finalData);
                                for (int i = 0; i < finalData.size(); i++) {
                                    keys.put(finalData.get(i).getName(), currentDataPos + i);
                                }
                            }
                            for (int i = currentDataPos + (itemsToAdd > 0 ? finalData.size() : 0);
                                 i < currentComments.size();
                                 i++) {
                                keys.put(currentComments.get(i).getName(), i);
                            }

                            if (itemsToAdd > 0) {
                                listView.setItemAnimator(new SlideRightAlphaAnimator());
                            }
                            adapter.notifyDataSetChanged();
                        });
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
        if (isMatchingPlaceholderPosition(keyedPosition, placeholderKey)) {
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

    private boolean isMatchingPlaceholderPosition(Integer position, String placeholderKey) {
        return position != null
                && position >= 0
                && position < currentComments.size()
                && placeholderKey.equals(currentComments.get(position).getName());
    }


    @Override
    protected Integer doInBackground(MoreChildItem... params) {
        finalData = new ArrayList<>();
        int itemsAddedCount = 0;
        if (params.length > 0) {
            MoreChildItem moreChildItem = params[0];
            targetItem = moreChildItem;
            CommentNode parentNode = moreChildItem.comment;
            MoreChildren moreChildren = moreChildItem.children;

            try {
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
                                Runnable positiveAction = null;
                                String positiveButtonText = mContext.getString(R.string.btn_ok);

                                // Specific error handling based on stacktrace
                                if (stacktrace.contains("UnknownHostException")
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
                                builder.show();
                            } catch (Exception ignored) {
                                Log.e(LogUtil.getTag(), "Exception showing error dialog", ignored);
                            }
                        });
                return null;
            }
        }
        return itemsAddedCount;
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

        return itemsAddedCount;
    }
}
