package me.edgan.redditslide.Adapters;

import android.app.Activity;
import android.os.AsyncTask;
import androidx.annotation.Nullable;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import me.edgan.redditslide.Authentication;
import me.edgan.redditslide.util.LogUtil;
import net.dean.jraw.models.Message;
import net.dean.jraw.models.PrivateMessage;
import net.dean.jraw.paginators.InboxPaginator;
import net.dean.jraw.paginators.Paginator;

/** Created by ccrama on 9/17/2015. */
public class InboxMessages extends GeneralPosts {
    @SuppressWarnings("NullAway.Init") // assigned in onPostExecute
    public ArrayList<Message> posts;
    public boolean loading;
    @SuppressWarnings("NullAway.Init") // assigned in run
    private Paginator<Message> paginator;
    @SuppressWarnings("NullAway.Init") // assigned in bindAdapter
    private SwipeRefreshLayout refreshLayout;
    public String where;
    @SuppressWarnings("NullAway.Init") // assigned in bindAdapter
    private InboxAdapter adapter;

    public InboxMessages(String where) {
        this.where = where;
    }

    public void bindAdapter(InboxAdapter a, SwipeRefreshLayout layout) {
        this.adapter = a;
        this.refreshLayout = layout;
        // The initial load (and every refresh when the tab is shown again) is driven from
        // InboxPage.onResume() so that switching back to a tab reflects reads made elsewhere.
    }

    public void loadMore(InboxAdapter adapter, String where, boolean refresh) {

        new LoadData(refresh).execute(where);
    }

    public class LoadData extends AsyncTask<String, Void, ArrayList<Message>> {
        final boolean reset;

        public LoadData(boolean reset) {
            this.reset = reset;
        }

        @Override
        public void onPostExecute(ArrayList<Message> subs) {
            if (subs == null && !nomore) {
                adapter.setError(true);
                refreshLayout.setRefreshing(false);
            } else if (!nomore) {

                if (subs.size() < 25) {
                    nomore = true;
                }
                if (reset) {
                    posts = subs;

                } else {
                    if (posts == null) {
                        posts = new ArrayList<>();
                    }
                    posts.addAll(subs);
                }
                ((Activity) adapter.mContext)
                        .runOnUiThread(
                                new Runnable() {
                                    @Override
                                    public void run() {
                                        refreshLayout.setRefreshing(false);
                                        loading = false;
                                        // Recover the real adapter if a previous load had swapped
                                        // in the error view; otherwise the list stays stuck on the
                                        // error screen even though this load succeeded.
                                        adapter.undoSetError();
                                        adapter.notifyDataSetChanged();
                                    }
                                });
            }
        }

        @Override
        protected @Nullable ArrayList<Message> doInBackground(String... subredditPaginators) {
            try {
                if (reset || paginator == null) {
                    paginator = new InboxPaginator(Authentication.reddit, where);
                    paginator.setLimit(25);
                    nomore = false;
                }
                if (paginator.hasNext()) {
                    ArrayList<Message> done = new ArrayList<>();
                    for (Message m : paginator.next()) {
                        done.add(m);
                        if (m.getDataNode().has("replies")
                                && !m.getDataNode().path("replies").toString().isEmpty()
                                && m.getDataNode().path("replies").has("data")
                                && m.getDataNode().path("replies").path("data").has("children")) {
                            JsonNode n = m.getDataNode().path("replies").path("data").path("children");

                            for (JsonNode o : n) {
                                // A child with no "data" object builds a Message whose data node
                                // carries nothing, and the first JRAW accessor the inbox row
                                // reaches — getCreated(), which is created_utc.longValue() with no
                                // null test — throws while the row is being bound.
                                final JsonNode messageData = o.get("data");
                                if (messageData != null && messageData.isObject()) {
                                    done.add(new PrivateMessage(messageData));
                                }
                            }
                        }
                    }
                    return done;

                } else {
                    nomore = true;
                }
                return null;
            } catch (Exception e) {
                LogUtil.e(e, "InboxMessages.doInBackground failed");
                return null;
            }
        }
    }
}
