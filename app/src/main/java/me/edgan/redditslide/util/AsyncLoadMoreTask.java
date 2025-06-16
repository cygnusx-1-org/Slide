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
        if (!isCancelled() && data != null && data > 0) {
            final int itemsToAdd = data;

            ((Activity) mContext)
                    .runOnUiThread(
                            new Runnable() {
                                @Override
                                public void run() {
                                    // Find the MoreChildItem by fullname instead of relying on dataPos
                                    // to avoid race conditions when the list is modified during async operation
                                    int currentDataPos = -1;
                                    int currentHolderPos = -1;

                                    for (int i = 0; i < currentComments.size(); i++) {
                                        CommentObject obj = currentComments.get(i);
                                        if (obj instanceof MoreChildItem) {
                                            MoreChildItem moreItem = (MoreChildItem) obj;
                                            if (moreItem.comment.getComment().getFullName().equals(fullname)) {
                                                currentDataPos = i;
                                                // Calculate holder position (account for header and spacer)
                                                currentHolderPos = i + 2;
                                                // Adjust for hidden items
                                                for (int j = 0; j < i; j++) {
                                                    if (adapter.hidden.contains(currentComments.get(j).getName())) {
                                                        currentHolderPos--;
                                                    }
                                                }
                                                break;
                                            }
                                        }
                                    }

                                    if (currentDataPos != -1) {
                                        // Remove the "Load More" item
                                        currentComments.remove(currentDataPos);
                                        adapter.notifyItemRemoved(currentHolderPos);

                                        // Add new items at the same position
                                        currentComments.addAll(currentDataPos, finalData);

                                        // Update keys for newly added items
                                        for (int i = 0; i < finalData.size(); i++) {
                                            keys.put(finalData.get(i).getName(), currentDataPos + i);
                                        }

                                        // Update keys for items that came after the insertion point
                                        for (int i = currentDataPos + finalData.size(); i < currentComments.size(); i++) {
                                            keys.put(currentComments.get(i).getName(), i);
                                        }

                                        listView.setItemAnimator(new SlideRightAlphaAnimator());
                                        adapter.notifyItemRangeInserted(currentHolderPos, itemsToAdd);

                                    } else {
                                        Log.w(LogUtil.getTag(), "Could not find MoreChildItem with fullname: " + fullname + " during load more completion. Item may have been removed.");
                                        resetLoadingIndicator();
                                    }
                                }
                            });

        } else if (data == null || data == 0) { // Handle null or zero case (error or no new comments)
            Log.w(LogUtil.getTag(), "AsyncLoadMoreTask finished with null or zero data. Error occurred or no more comments.");
            resetLoadingIndicator();
        }
    }

    private void resetLoadingIndicator() {
         // Ensure holder and its views are not null, and context is valid
        if (holder != null && holder.loading != null && holder.content != null && mContext != null) {
            ((Activity) mContext).runOnUiThread(() -> {
                // Find the MoreChildItem by fullname instead of using potentially stale dataPos
                MoreChildItem baseNode = null;
                for (CommentObject obj : currentComments) {
                    if (obj instanceof MoreChildItem) {
                        MoreChildItem moreItem = (MoreChildItem) obj;
                        if (moreItem.comment.getComment().getFullName().equals(fullname)) {
                            baseNode = moreItem;
                            break;
                        }
                    }
                }

                if (baseNode != null) {
                    try {
                         // Use getLocalizedCount for display
                        String countString = baseNode.children.getLocalizedCount();
                        if (baseNode.children.getCount() > 0) {
                            holder.content.setText(mContext.getString(R.string.comment_load_more_string_new, countString));
                        } else if (!baseNode.children.getChildrenIds().isEmpty()) {
                            // Even if count is 0, if IDs exist, show unknown
                            holder.content.setText(R.string.comment_load_more_number_unknown);
                        } else {
                             // No count and no IDs means it's likely a "continue thread" link
                            holder.content.setText(R.string.thread_continue);
                        }
                    } catch (Exception e) {
                        Log.e(LogUtil.getTag(), "Error resetting loading indicator text", e);
                        holder.content.setText(R.string.comment_load_more_number_unknown);
                    }
                } else {
                    // Item was removed or changed, just hide loading indicator
                    Log.w(LogUtil.getTag(), "Could not find MoreChildItem with fullname: " + fullname + " when resetting indicator. Item may have been removed.");
                }
                holder.loading.setVisibility(View.GONE);
            });
        } else {
            Log.w(LogUtil.getTag(), "Could not reset loading indicator: holder or context was null.");
        }
    }


    @Override
    protected Integer doInBackground(MoreChildItem... params) {
        finalData = new ArrayList<>();
        int itemsAddedCount = 0;
        if (params.length > 0) {
            MoreChildItem moreChildItem = params[0];
            CommentNode parentNode = moreChildItem.comment;
            MoreChildren moreChildren = moreChildItem.children;

            try {
                if (Authentication.reddit == null) {
                    Log.e(LogUtil.getTag(), "Authentication.reddit is null in AsyncLoadMoreTask");
                    return null;
                }

                // Fetch the next batch of comments
                List<CommentNode> newNodesListing = parentNode.loadMoreComments(Authentication.reddit);

                if (newNodesListing != null) {
                    // Use the same pattern as SubmissionComments to properly handle nested comments
                    Map<Integer, MoreChildItem> waiting = new HashMap<>();

                    for (CommentNode newNode : newNodesListing) {
                        // Check if the key already exists - prevents adding duplicates
                        if (!keys.containsKey(newNode.getComment().getFullName())) {
                            // Use walkTree to traverse the entire tree structure of each loaded comment
                            for (CommentNode n : newNode.walkTree()) {
                                // Skip if this comment is already loaded
                                if (!keys.containsKey(n.getComment().getFullName())) {
                                    // Process any waiting MoreChildItems before adding this comment
                                    List<Integer> removed = new ArrayList<>();
                                    Map<Integer, MoreChildItem> map = new TreeMap<>(Collections.reverseOrder());
                                    map.putAll(waiting);

                                    for (Integer i : map.keySet()) {
                                        if (i >= n.getDepth()) {
                                            finalData.add(waiting.get(i));
                                            itemsAddedCount++;
                                            removed.add(i);
                                            waiting.remove(i);
                                        }
                                    }

                                    // Add the comment itself
                                    finalData.add(new CommentItem(n));
                                    itemsAddedCount++;

                                    // If this node has more comments, add a MoreChildItem marker for it
                                    if (n.hasMoreComments()) {
                                        waiting.put(n.getDepth(), new MoreChildItem(n, n.getMoreChildren()));
                                    }
                                } else {
                                    Log.w(LogUtil.getTag(), "Skipping already existing comment key in walkTree: " + n.getComment().getFullName());
                                }
                            }
                        } else {
                            Log.w(LogUtil.getTag(), "Skipping already existing comment key: " + newNode.getComment().getFullName());
                        }
                    }

                    // Add any remaining waiting MoreChildItems
                    Map<Integer, MoreChildItem> map = new TreeMap<>(Collections.reverseOrder());
                    map.putAll(waiting);
                    for (Integer i : map.keySet()) {
                        finalData.add(waiting.get(i));
                        itemsAddedCount++;
                        waiting.remove(i);
                    }
                } else {
                    Log.w(LogUtil.getTag(), "loadMoreComments returned null listing for node: " + parentNode.getComment().getId());
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
}