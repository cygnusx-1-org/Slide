package me.edgan.redditslide.Activities;

import android.app.Dialog;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.widget.EditText;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import com.fasterxml.jackson.databind.JsonNode;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.snackbar.Snackbar;
import java.util.ArrayList;
import java.util.List;
import me.edgan.redditslide.Adapters.ModmailThreadAdapter;
import me.edgan.redditslide.Drafts;
import me.edgan.redditslide.Modmail.ModmailApi;
import me.edgan.redditslide.Modmail.ModmailMessage;
import me.edgan.redditslide.R;
import me.edgan.redditslide.Views.DoEditorActions;
import me.edgan.redditslide.util.DialogUtil;
import me.edgan.redditslide.util.LayoutUtils;
import me.edgan.redditslide.util.MiscUtil;
import org.jspecify.annotations.NullMarked;

/**
 * Shows a single New Modmail conversation thread ({@code /api/mod/conversations/:id}) and lets a
 * moderator reply. Opened from {@link me.edgan.redditslide.Adapters.ModmailAdapter}.
 */
@NullMarked
public class ModmailThread extends BaseActivityAnim {

    public static final String EXTRA_ID = "id";
    public static final String EXTRA_SUBJECT = "subject";

    /**
     * Bumped whenever a conversation is loaded here, which marks it read for this moderator and
     * changes what the list it was opened from should show. {@link
     * me.edgan.redditslide.Fragments.ModmailPage} watches this the way {@link
     * me.edgan.redditslide.Fragments.InboxPage} watches {@link Inbox#readGeneration}. Written and
     * read on the main thread only.
     */
    public static int readGeneration;

    private String conversationId;
    private final List<ModmailMessage> messages = new ArrayList<>();
    private ModmailThreadAdapter adapter;
    private RecyclerView recyclerView;
    private SwipeRefreshLayout refreshLayout;

    @Override
    public void onCreate(@Nullable Bundle savedInstance) {
        overrideSwipeFromAnywhere();
        super.onCreate(savedInstance);

        conversationId = MiscUtil.orEmpty(getIntent().getStringExtra(EXTRA_ID));
        String subject = getIntent().getStringExtra(EXTRA_SUBJECT);

        applyColorTheme("");
        setContentView(R.layout.activity_modmail_thread);

        MiscUtil.setupOldSwipeModeBackground(this, getWindow().getDecorView());

        setupAppBar(
                R.id.toolbar,
                subject == null || subject.isEmpty()
                        ? getString(R.string.mod_mail)
                        : subject,
                true,
                true);

        recyclerView = (RecyclerView) requireViewById(R.id.vertical_content);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new ModmailThreadAdapter(this, messages);
        recyclerView.setAdapter(adapter);

        // The AppBarLayout floats over the RecyclerView (it is drawn on top via its elevation),
        // so without a top inset the first message renders partially behind the toolbar. Pad the
        // list down by the header's height once it has been measured; clipToPadding="false" keeps
        // content scrolling under the bar as intended.
        final View header = requireViewById(R.id.header);
        header.post(
                () ->
                        recyclerView.setPadding(
                                recyclerView.getPaddingLeft(),
                                header.getHeight(),
                                recyclerView.getPaddingRight(),
                                recyclerView.getPaddingBottom()));

        refreshLayout =
                (SwipeRefreshLayout) requireViewById(R.id.activity_main_swipe_refresh_layout);
        refreshLayout.setOnRefreshListener(this::load);

        FloatingActionButton reply = (FloatingActionButton) requireViewById(R.id.reply);
        reply.setOnClickListener(v -> showReplyDialog());

        load();
    }

    private void load() {
        refreshLayout.post(() -> refreshLayout.setRefreshing(true));
        new AsyncTask<Void, Void, List<ModmailMessage>>() {
            @Override
            protected @Nullable List<ModmailMessage> doInBackground(Void... voids) {
                JsonNode root = ModmailApi.getConversation(conversationId, true);
                // null, not an empty list: the caller has to tell a failed fetch from a
                // conversation that really has no messages.
                return root == null ? null : ModmailApi.parseMessages(root);
            }

            @Override
            protected void onPostExecute(@Nullable List<ModmailMessage> result) {
                refreshLayout.setRefreshing(false);
                if (result == null) {
                    // A failed refresh must not blank a conversation that is already on screen,
                    // and nothing was marked read, so the list stays valid too.
                    LayoutUtils.showSnackbar(
                            Snackbar.make(
                                    recyclerView,
                                    R.string.err_loading_content,
                                    Snackbar.LENGTH_LONG));
                    return;
                }
                messages.clear();
                messages.addAll(result);
                adapter.notifyDataSetChanged();
                // Reading the conversation marked it read, and a reply reloads through here too,
                // so the list this was opened from is now out of date.
                readGeneration++;
            }
        }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }

    private void showReplyDialog() {
        LayoutInflater inflater = getLayoutInflater();
        final View dialoglayout = inflater.inflate(R.layout.edit_comment, null);
        final EditText e = dialoglayout.requireViewById(R.id.entry);

        DoEditorActions.doActions(
                e, dialoglayout, getSupportFragmentManager(), this, null, null);

        final AlertDialog.Builder builder = new AlertDialog.Builder(this).setView(dialoglayout);
        final Dialog d = builder.create();
        if (d.getWindow() != null) {
            d.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        }

        DialogUtil.matchDialogToCardBackground(d);
        d.show();

        dialoglayout.requireViewById(R.id.cancel).setOnClickListener(v -> d.dismiss());
        dialoglayout
                .requireViewById(R.id.submit)
                .setOnClickListener(
                        v -> {
                            final String text = e.getText().toString();
                            if (!text.trim().isEmpty()) {
                                sendReply(text);
                            }
                            d.dismiss();
                        });
    }

    private void sendReply(final String text) {
        new AsyncTask<Void, Void, Boolean>() {
            @Override
            protected Boolean doInBackground(Void... voids) {
                return ModmailApi.reply(conversationId, text, false, false);
            }

            @Override
            protected void onPostExecute(Boolean sent) {
                if (sent != null && sent) {
                    LayoutUtils.showSnackbar(
                            Snackbar.make(
                                    recyclerView,
                                    R.string.modmail_reply_sent,
                                    Snackbar.LENGTH_LONG));
                    load();
                } else {
                    Drafts.addDraft(text);
                    LayoutUtils.showSnackbar(
                            Snackbar.make(
                                    recyclerView,
                                    R.string.modmail_reply_failed,
                                    Snackbar.LENGTH_LONG));
                }
            }
        }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }
}
