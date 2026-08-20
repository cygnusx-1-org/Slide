package me.edgan.redditslide.Adapters;

import android.app.Activity;
import android.os.AsyncTask;
import androidx.annotation.Nullable;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import com.fasterxml.jackson.databind.JsonNode;
import com.google.android.material.snackbar.Snackbar;
import java.util.ArrayList;
import java.util.List;
import me.edgan.redditslide.Modmail.ModmailApi;
import me.edgan.redditslide.Modmail.ModmailConversation;
import me.edgan.redditslide.R;
import me.edgan.redditslide.util.LayoutUtils;

/**
 * Loads New Modmail conversations for a {@link me.edgan.redditslide.Fragments.ModmailPage}. The New
 * Modmail equivalent of {@link InboxMessages}: instead of legacy {@code /message/moderator}
 * listings it pages through {@code /api/mod/conversations}.
 */
public class ModmailPosts {
    @SuppressWarnings("NullAway.Init") // assigned in onPostExecute
    public ArrayList<ModmailConversation> posts;

    public boolean loading;
    public boolean nomore;

    /**
     * Set when a page load failed underneath conversations that are already on screen. The list
     * keeps them (see {@link LoadData#onPostExecute}), so without this the footer would go on
     * showing a spinner for a load that is not happening. Cleared whenever a load starts.
     */
    public boolean loadFailed;

    private final boolean onlyUnread;

    @SuppressWarnings("NullAway.Init") // assigned in bindAdapter
    private SwipeRefreshLayout refreshLayout;

    @SuppressWarnings("NullAway.Init") // assigned in bindAdapter
    private ModmailAdapter adapter;

    /**
     * Cursor for paging: the id of the last conversation returned, regardless of read state. Tracked
     * separately from {@link #posts} because the unread tab filters out read conversations, so the
     * last *displayed* conversation is not necessarily the right {@code after} value.
     */
    @Nullable private String lastConversationId;

    private static final int PAGE_SIZE = 25;

    public ModmailPosts(boolean onlyUnread) {
        this.onlyUnread = onlyUnread;
    }

    public void bindAdapter(ModmailAdapter a, SwipeRefreshLayout layout) {
        this.adapter = a;
        this.refreshLayout = layout;
        // The initial load (and every refresh when the tab is shown again) is driven from
        // ModmailPage.onResume() so that coming back from a thread reflects the read it performed.
    }

    public void loadMore(ModmailAdapter adapter, boolean refresh) {
        loadFailed = false;
        new LoadData(refresh).execute();
    }

    public class LoadData extends AsyncTask<Void, Void, List<ModmailConversation>> {
        final boolean reset;
        boolean endReached;

        public LoadData(boolean reset) {
            this.reset = reset;
        }

        @Override
        protected @Nullable List<ModmailConversation> doInBackground(Void... voids) {
            try {
                if (reset) {
                    nomore = false;
                    lastConversationId = null;
                }
                JsonNode root =
                        ModmailApi.getConversations(
                                "all", "recent", reset ? null : lastConversationId, PAGE_SIZE);
                if (root == null) {
                    return null;
                }

                // Page using the full (unfiltered) result: a short raw page means the end, and the
                // cursor must advance past read conversations even on the unread tab.
                List<ModmailConversation> raw = ModmailApi.parseConversationList(root, false);
                endReached = raw.size() < PAGE_SIZE;
                if (!raw.isEmpty()) {
                    lastConversationId = raw.get(raw.size() - 1).getId();
                }

                if (!onlyUnread) {
                    return raw;
                }
                List<ModmailConversation> filtered = new ArrayList<>();
                for (ModmailConversation c : raw) {
                    if (c.isUnread()) {
                        filtered.add(c);
                    }
                }
                return filtered;
            } catch (Exception e) {
                return null;
            }
        }

        @Override
        public void onPostExecute(@Nullable List<ModmailConversation> subs) {
            if (subs == null && !nomore) {
                // The error screen replaces the whole list, so it is only right when there is
                // nothing else to show. A page that fails underneath conversations already on
                // screen must not throw them away; say so and leave them alone.
                if (posts == null || posts.isEmpty()) {
                    adapter.setError(true);
                } else {
                    LayoutUtils.showSnackbar(
                            Snackbar.make(
                                    refreshLayout,
                                    R.string.err_loading_content,
                                    Snackbar.LENGTH_LONG));
                    // Swap the footer's spinner for a plain spacer: nothing is loading any more.
                    loadFailed = true;
                    adapter.notifyDataSetChanged();
                }
                refreshLayout.setRefreshing(false);
                loading = false;
                return;
            }
            if (nomore) {
                return;
            }

            if (endReached) {
                nomore = true;
            }
            if (reset) {
                posts = subs == null ? new ArrayList<>() : new ArrayList<>(subs);
            } else {
                if (posts == null) {
                    posts = new ArrayList<>();
                }
                if (subs != null) {
                    posts.addAll(subs);
                }
            }

            ((Activity) adapter.mContext)
                    .runOnUiThread(
                            () -> {
                                loading = false;
                                // Recover the real adapter if an earlier load had swapped in the
                                // error view; otherwise the list stays stuck on the error screen
                                // even though this load succeeded.
                                adapter.undoSetError();
                                adapter.notifyDataSetChanged();

                                // The unread tab can return a full page with nothing to show; the
                                // scroll listener can't fire on an empty list, so keep paging here
                                // until we have something or reach the end.
                                if (!nomore && (posts == null || posts.isEmpty())) {
                                    loading = true;
                                    loadMore(adapter, false);
                                } else {
                                    refreshLayout.setRefreshing(false);
                                }
                            });
        }
    }
}
