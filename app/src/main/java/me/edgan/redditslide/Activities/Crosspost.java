package me.edgan.redditslide.Activities;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.view.ContextThemeWrapper;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.SwitchCompat;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import me.edgan.redditslide.Authentication;
import me.edgan.redditslide.Flair.RichFlair;
import me.edgan.redditslide.OpenRedditLink;
import me.edgan.redditslide.R;
import me.edgan.redditslide.Reddit;
import me.edgan.redditslide.SpoilerRobotoTextView;
import me.edgan.redditslide.UserSubscriptions;
import me.edgan.redditslide.Views.CommentOverflow;
import me.edgan.redditslide.Visuals.ColorPreferences;
import me.edgan.redditslide.util.DialogUtil;
import me.edgan.redditslide.util.FlairUtil;
import me.edgan.redditslide.util.LogUtil;
import me.edgan.redditslide.util.MaterialProgressDialog;
import me.edgan.redditslide.util.MiscUtil;
import me.edgan.redditslide.util.SubmissionParser;
import me.edgan.redditslide.util.stubs.SimpleTextWatcher;
import net.dean.jraw.ApiException;
import net.dean.jraw.Endpoints;
import net.dean.jraw.http.HttpRequest;
import net.dean.jraw.http.RestResponse;
import net.dean.jraw.models.Submission;
import net.dean.jraw.models.Subreddit;
import okhttp3.OkHttpClient;

/** Created by ccrama on 3/5/2015. */
public class Crosspost extends BaseActivity {

    public static Submission toCrosspost;
    private SwitchCompat inboxReplies;

    private String selectedFlairID;
    private String selectedFlairText;
    private boolean allowsCrossposts = true;
    private boolean isFlairRequired = false;

    private enum CrosspostBlockReason {
        DISALLOWED,
        NOT_SUBSCRIBED,
        DOES_NOT_EXIST
    }

    private CrosspostBlockReason blockReason;
    private boolean lastSubredditExists = true;

    AsyncTask<Void, Void, Subreddit> tchange;
    AsyncTask<Void, Void, Boolean> tFlairRequired;
    AsyncTask<Void, Void, Set<String>> tCrosspostable;
    private final Handler subredditDebounce = new Handler(Looper.getMainLooper());
    private Runnable subredditDebounceRunnable;
    private String lastCheckedSubreddit = "";
    private Set<String> crosspostableSubs;

    public void onCreate(Bundle savedInstanceState) {
        disableSwipeBackLayout();
        super.onCreate(savedInstanceState);
        applyColorTheme();
        setContentView(R.layout.activity_crosspost);

        MiscUtil.setupOldSwipeModeBackground(this, getWindow().getDecorView());

        Window window = this.getWindow();
        window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);
        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
        setupAppBar(R.id.toolbar, R.string.title_crosspost, true, true);

        inboxReplies = (SwitchCompat) findViewById(R.id.replies);

        final AutoCompleteTextView subredditText =
                ((AutoCompleteTextView) findViewById(R.id.subreddittext));

        ((EditText) findViewById(R.id.crossposttext))
                .setText(
                        toCrosspost.getTitle()
                                + getString(R.string.submission_properties_seperator)
                                + "/u/"
                                + toCrosspost.getAuthor());
        findViewById(R.id.crossposttext).setEnabled(false);
        ArrayAdapter adapter =
                new ArrayAdapter(
                        this,
                        android.R.layout.simple_list_item_1,
                        UserSubscriptions.getAllSubreddits(this));

        subredditText.setAdapter(adapter);
        subredditText.setThreshold(2);

        subredditText.addTextChangedListener(
                new SimpleTextWatcher() {
                    @Override
                    public void onTextChanged(CharSequence s, int start, int before, int count) {
                        if (tchange != null) {
                            tchange.cancel(true);
                        }
                        if (tFlairRequired != null) {
                            tFlairRequired.cancel(true);
                        }
                        findViewById(R.id.submittext).setVisibility(View.GONE);
                        // Reset state because the subreddit changed
                        allowsCrossposts = true;
                        blockReason = null;
                        lastSubredditExists = true;
                        isFlairRequired = false;
                        selectedFlairID = null;
                        selectedFlairText = null;
                        lastCheckedSubreddit = "";
                        refreshInputState(s.toString());
                        scheduleSubredditCheck(s.toString());
                    }
                });

        subredditText.setOnFocusChangeListener(
                new View.OnFocusChangeListener() {
                    @Override
                    public void onFocusChange(View v, boolean hasFocus) {
                        findViewById(R.id.submittext).setVisibility(View.GONE);
                        if (!hasFocus) {
                            runSubredditCheck(subredditText.getText().toString());
                        }
                    }
                });

        subredditText.setOnItemClickListener(
                (parent, view, position, id) ->
                        runSubredditCheck(subredditText.getText().toString()));

        ((EditText) findViewById(R.id.titletext)).setText(toCrosspost.getTitle());

        findViewById(R.id.suggest)
                .setOnClickListener(
                        new View.OnClickListener() {
                            @Override
                            public void onClick(View v) {
                                ((EditText) findViewById(R.id.titletext))
                                        .setText(toCrosspost.getTitle());
                            }
                        });

        findViewById(R.id.flair)
                .setOnClickListener(
                        new View.OnClickListener() {
                            @Override
                            public void onClick(View v) {
                                String subreddit =
                                        ((EditText) findViewById(R.id.subreddittext))
                                                .getText()
                                                .toString();
                                if (subreddit.isEmpty()) {
                                    Toast.makeText(
                                                    Crosspost.this,
                                                    R.string.editor_hint_subreddit,
                                                    Toast.LENGTH_SHORT)
                                            .show();
                                    return;
                                }
                                showFlairChooser(subreddit);
                            }
                        });

        findViewById(R.id.send)
                .setOnClickListener(
                        new View.OnClickListener() {
                            @Override
                            public void onClick(View view) {
                                // Defense in depth — the FAB should be disabled in these states,
                                // but guard anyway.
                                if (!allowsCrossposts) {
                                    return;
                                }
                                if (isFlairRequired && selectedFlairID == null) {
                                    Toast.makeText(
                                                    Crosspost.this,
                                                    R.string.crosspost_flair_required_short,
                                                    Toast.LENGTH_LONG)
                                            .show();
                                    return;
                                }
                                ((FloatingActionButton) findViewById(R.id.send)).hide();
                                new AsyncDo().execute();
                            }
                        });

        refreshInputState("");
        fetchCrosspostableSubs();
    }

    private void fetchCrosspostableSubs() {
        if (tCrosspostable != null) {
            tCrosspostable.cancel(true);
        }
        tCrosspostable =
                new AsyncTask<Void, Void, Set<String>>() {
                    @Override
                    protected Set<String> doInBackground(Void... voids) {
                        try {
                            HttpRequest r =
                                    Authentication.reddit
                                            .request()
                                            .path("/api/crosspostable_subreddits")
                                            .get()
                                            .build();
                            RestResponse response = Authentication.reddit.execute(r);
                            JsonNode root = response.getJson();
                            LogUtil.v(
                                    "Crosspost: crosspostable_subreddits response: "
                                            + (root == null ? "null" : root.toString()));
                            if (root != null && root.isArray()) {
                                Set<String> set = new HashSet<>();
                                for (JsonNode node : root) {
                                    String name = node.asText();
                                    if (name != null && !name.isEmpty()) {
                                        set.add(name.toLowerCase(Locale.ROOT));
                                    }
                                }
                                return set;
                            }
                        } catch (Exception e) {
                            LogUtil.v(
                                    "Crosspost: crosspostable_subreddits failed: "
                                            + e.getClass().getSimpleName()
                                            + ": "
                                            + e.getMessage());
                        }
                        return null;
                    }

                    @Override
                    protected void onPostExecute(Set<String> result) {
                        crosspostableSubs = result;
                        AutoCompleteTextView subredditText =
                                (AutoCompleteTextView) findViewById(R.id.subreddittext);
                        if (subredditText != null) {
                            String current = subredditText.getText().toString().trim();
                            if (!current.isEmpty()) {
                                applyCrosspostableState(current);
                                refreshInputState(current);
                            }
                        }
                    }
                };
        tCrosspostable.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }

    private void applyCrosspostableState(String subreddit) {
        if (crosspostableSubs == null || subreddit == null || subreddit.isEmpty()) {
            // Crosspostable list not yet loaded — don't block; will be re-checked when
            // the list arrives (and at submit time as a backstop).
            allowsCrossposts = true;
            blockReason = null;
            return;
        }
        String lower = subreddit.toLowerCase(Locale.ROOT);
        if (crosspostableSubs.contains(lower)) {
            allowsCrossposts = true;
            blockReason = null;
            return;
        }
        // Not in the crosspostable list. Pick the most specific reason:
        //   - the /r/{sub}/about fetch returned null → DOES_NOT_EXIST
        //   - exists but user isn't in their local subscription list → NOT_SUBSCRIBED
        //   - exists, user is subscribed → DISALLOWED (the sub blocks crossposts)
        allowsCrossposts = false;
        if (!lastSubredditExists) {
            blockReason = CrosspostBlockReason.DOES_NOT_EXIST;
        } else {
            List<String> subs = UserSubscriptions.getAllSubreddits(Crosspost.this);
            boolean subscribed = subs != null && subs.contains(subreddit);
            blockReason =
                    subscribed
                            ? CrosspostBlockReason.DISALLOWED
                            : CrosspostBlockReason.NOT_SUBSCRIBED;
        }
        LogUtil.v(
                "Crosspost: /r/"
                        + subreddit
                        + " not in crosspostable list, exists="
                        + lastSubredditExists
                        + " -> blockReason="
                        + blockReason);
    }

    private static final int FLAIR_REQUIRED_COLOR = Color.parseColor("#FF9800");
    private static final int WARNING_TEXT_COLOR = Color.parseColor("#F44336");

    private void refreshInputState(String subreddit) {
        TextView status = (TextView) findViewById(R.id.crosspost_status);
        if (!allowsCrossposts) {
            int msg;
            if (blockReason == CrosspostBlockReason.DOES_NOT_EXIST) {
                msg = R.string.err_crosspost_sub_not_found;
            } else if (blockReason == CrosspostBlockReason.NOT_SUBSCRIBED) {
                msg = R.string.err_crosspost_not_subscribed;
            } else {
                msg = R.string.err_crosspost_disallowed_text;
            }
            status.setText(getString(msg, subreddit));
            status.setTextColor(WARNING_TEXT_COLOR);
            status.setVisibility(View.VISIBLE);
        } else {
            status.setVisibility(View.GONE);
            status.setText("");
        }

        TextView flair = (TextView) findViewById(R.id.flair);
        String base =
                selectedFlairID != null
                        ? getString(R.string.submit_selected_flair, selectedFlairText)
                        : getString(R.string.editor_btn_select_flair);
        if (isFlairRequired && selectedFlairID == null) {
            SpannableString span = new SpannableString(base + " *");
            span.setSpan(
                    new ForegroundColorSpan(FLAIR_REQUIRED_COLOR),
                    span.length() - 1,
                    span.length(),
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            flair.setText(span);
        } else {
            flair.setText(base);
        }

        boolean canSend =
                allowsCrossposts && !(isFlairRequired && selectedFlairID == null);
        FloatingActionButton send = (FloatingActionButton) findViewById(R.id.send);
        send.setEnabled(canSend);
        send.setAlpha(canSend ? 1f : 0.4f);
    }

    private void scheduleSubredditCheck(final String subreddit) {
        if (subredditDebounceRunnable != null) {
            subredditDebounce.removeCallbacks(subredditDebounceRunnable);
        }
        if (subreddit == null || subreddit.trim().length() < 2) {
            return;
        }
        subredditDebounceRunnable = () -> runSubredditCheck(subreddit);
        subredditDebounce.postDelayed(subredditDebounceRunnable, 700);
    }

    private void runSubredditCheck(final String subredditRaw) {
        final String subreddit = subredditRaw == null ? "" : subredditRaw.trim();
        if (subreddit.isEmpty() || subreddit.equals(lastCheckedSubreddit)) {
            return;
        }
        lastCheckedSubreddit = subreddit;
        if (subredditDebounceRunnable != null) {
            subredditDebounce.removeCallbacks(subredditDebounceRunnable);
            subredditDebounceRunnable = null;
        }
        if (tchange != null) {
            tchange.cancel(true);
        }
        final AutoCompleteTextView subredditText =
                (AutoCompleteTextView) findViewById(R.id.subreddittext);
        tchange =
                new AsyncTask<Void, Void, Subreddit>() {
                    @Override
                    protected Subreddit doInBackground(Void... params) {
                        try {
                            return Authentication.reddit.getSubreddit(subreddit);
                        } catch (Exception e) {
                            LogUtil.v(
                                    "Crosspost: getSubreddit failed for /r/"
                                            + subreddit
                                            + ": "
                                            + e.getClass().getSimpleName()
                                            + ": "
                                            + e.getMessage());
                        }
                        return null;
                    }

                    @Override
                    protected void onPostExecute(Subreddit s) {
                        lastSubredditExists = (s != null);
                        if (s != null) {
                            String text = s.getDataNode().get("submit_text_html").asText();
                            if (text != null && !text.isEmpty() && !text.equals("null")) {
                                findViewById(R.id.submittext).setVisibility(View.VISIBLE);
                                setViews(
                                        text,
                                        subreddit,
                                        (SpoilerRobotoTextView)
                                                findViewById(R.id.submittext),
                                        (CommentOverflow) findViewById(R.id.commentOverflow));
                            }
                            if (s.getSubredditType().equals("RESTRICTED")) {
                                subredditText.setText("");
                                lastCheckedSubreddit = "";
                                DialogUtil.showWithCardBackground(new AlertDialog.Builder(Crosspost.this)
                                        .setTitle(R.string.err_submit_restricted)
                                        .setMessage(R.string.err_submit_restricted_text)
                                        .setPositiveButton(R.string.btn_ok, null)
                                        );
                                return;
                            }

                            fetchFlairRequirement(subreddit);
                        } else {
                            findViewById(R.id.submittext).setVisibility(View.GONE);
                        }
                        applyCrosspostableState(subreddit);
                        refreshInputState(subreddit);
                    }
                };
        tchange.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }

    private void fetchFlairRequirement(final String subreddit) {
        if (tFlairRequired != null) {
            tFlairRequired.cancel(true);
        }
        tFlairRequired =
                new AsyncTask<Void, Void, Boolean>() {
                    @Override
                    protected Boolean doInBackground(Void... voids) {
                        try {
                            HttpRequest r =
                                    Authentication.reddit
                                            .request()
                                            .path(
                                                    "/api/v1/"
                                                            + subreddit
                                                            + "/post_requirements")
                                            .get()
                                            .build();
                            RestResponse response = Authentication.reddit.execute(r);
                            JsonNode root = response.getJson();
                            LogUtil.v(
                                    "Crosspost: post_requirements /r/"
                                            + subreddit
                                            + " response: "
                                            + (root == null ? "null" : root.toString()));
                            if (root != null && root.has("is_flair_required")) {
                                return root.get("is_flair_required").asBoolean(false);
                            }
                        } catch (Exception e) {
                            LogUtil.v(
                                    "Crosspost: post_requirements lookup failed: "
                                            + e.getClass().getSimpleName()
                                            + ": "
                                            + e.getMessage());
                        }
                        return false;
                    }

                    @Override
                    protected void onPostExecute(Boolean required) {
                        isFlairRequired = required != null && required;
                        refreshInputState(subreddit);
                    }
                };
        tFlairRequired.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }

    private void showFlairChooser(final String subreddit) {
        final OkHttpClient client = Reddit.client;
        final Gson gson = new Gson();

        final Dialog d =
                new MaterialProgressDialog.Builder(Crosspost.this)
                        .title(R.string.submit_findingflairs)
                        .cancelable(true)
                        .content(R.string.misc_please_wait)
                        .progress(true, 100)
                        .show()
                        .getDialog();
        new AsyncTask<Void, Void, JsonArray>() {
            @Override
            protected JsonArray doInBackground(Void... params) {
                return FlairUtil.fetchLinkFlairs(client, gson, subreddit);
            }

            @Override
            protected void onPostExecute(final JsonArray result) {
                if (result == null) {
                    LogUtil.v("Error loading content");
                    d.dismiss();
                } else {
                    try {
                        final HashMap<String, RichFlair> flairs = new HashMap<>();
                        for (JsonElement object : result) {
                            RichFlair choice =
                                    new ObjectMapper()
                                            .disable(
                                                    DeserializationFeature
                                                            .FAIL_ON_UNKNOWN_PROPERTIES)
                                            .readValue(object.toString(), RichFlair.class);
                            String title = choice.getText();
                            flairs.put(title, choice);
                        }
                        d.dismiss();

                        ArrayList<String> allKeys = new ArrayList<>(flairs.keySet());

                        final Context contextThemeWrapper =
                                new ContextThemeWrapper(
                                        Crosspost.this,
                                        new ColorPreferences(Crosspost.this)
                                                .getFontStyle()
                                                .getBaseId());
                        new MaterialAlertDialogBuilder(contextThemeWrapper)
                                .setTitle(getString(R.string.submit_flairchoices, subreddit))
                                .setItems(
                                        allKeys.toArray(new CharSequence[0]),
                                        (dialog, which) -> {
                                            RichFlair selected = flairs.get(allKeys.get(which));
                                            selectedFlairID = selected.getId();
                                            selectedFlairText = selected.getText();
                                            refreshInputState(subreddit);
                                        })
                                .show();
                    } catch (Exception e) {
                        LogUtil.v(e.toString());
                        d.dismiss();
                        LogUtil.v("Error parsing flairs");
                    }
                }
            }
        }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }

    public void setViews(
            String rawHTML,
            String subredditName,
            SpoilerRobotoTextView firstTextView,
            CommentOverflow commentOverflow) {
        if (rawHTML.isEmpty()) {
            return;
        }

        List<String> blocks = SubmissionParser.getBlocks(rawHTML);

        int startIndex = 0;
        // the <div class="md"> case is when the body contains a table or code block first
        if (!blocks.get(0).equals("<div class=\"md\">")) {
            firstTextView.setVisibility(View.VISIBLE);
            firstTextView.setTextHtml(blocks.get(0) + " ", subredditName);
            startIndex = 1;
        } else {
            firstTextView.setText("");
            firstTextView.setVisibility(View.GONE);
        }

        if (blocks.size() > 1) {
            if (startIndex == 0) {
                commentOverflow.setViews(blocks, subredditName);
            } else {
                commentOverflow.setViews(blocks.subList(startIndex, blocks.size()), subredditName);
            }
        } else {
            commentOverflow.removeAllViews();
        }
    }

    private class AsyncDo extends AsyncTask<Void, Void, Void> {

        // Snapshot of View state, captured on the UI thread in onPreExecute().
        // doInBackground() runs on a worker thread and must not touch Views directly.
        private String subreddit;
        private String title;
        private boolean sendReplies;

        @Override
        protected void onPreExecute() {
            subreddit =
                    ((AutoCompleteTextView) findViewById(R.id.subreddittext))
                            .getText()
                            .toString();
            title = ((EditText) findViewById(R.id.titletext)).getText().toString();
            sendReplies = inboxReplies.isChecked();
        }

        @Override
        protected Void doInBackground(Void... voids) {
            try {
                try {
                    Map<String, String> args = new HashMap<>();
                    args.put("api_type", "json");
                    args.put("extension", "json");
                    args.put("kind", "crosspost");
                    args.put("sr", subreddit);
                    args.put("crosspost_fullname", toCrosspost.getFullName());
                    args.put("title", title);
                    args.put("sendreplies", String.valueOf(sendReplies));
                    if (selectedFlairID != null) {
                        args.put("flair_id", selectedFlairID);
                    }
                    if (selectedFlairText != null) {
                        args.put("flair_text", selectedFlairText);
                    }

                    RestResponse response =
                            Authentication.reddit.execute(
                                    Authentication.reddit
                                            .request()
                                            .endpoint(Endpoints.SUBMIT)
                                            .post(args)
                                            .build());
                    if (response.hasErrors()) {
                        throw response.getError();
                    }
                    String newId =
                            response.getJson()
                                    .get("json")
                                    .get("data")
                                    .get("id")
                                    .asText();
                    Submission s = Authentication.reddit.getSubmission(newId);
                    OpenRedditLink.openUrl(
                            Crosspost.this,
                            "reddit.com/r/"
                                    + subreddit
                                    + "/comments/"
                                    + s.getFullName().substring(3),
                            true);
                    Crosspost.this.finish();
                } catch (final ApiException e) {
                    // Network/connection failures (bare RuntimeException) propagate to the
                    // outer catch (Exception) below; this branch is just for API errors.
                    LogUtil.e(e, "Crosspost.doInBackground failed");

                    runOnUiThread(
                            new Runnable() {
                                @Override
                                public void run() {
                                    showErrorRetryDialog(
                                            getString(R.string.misc_err)
                                                    + ": "
                                                    + e.getExplanation()
                                                    + "\n"
                                                    + getString(R.string.misc_retry));
                                }
                            });
                }
            } catch (Exception e) {
                LogUtil.e(e, "Crosspost.run failed");

                runOnUiThread(
                        new Runnable() {
                            @Override
                            public void run() {
                                showErrorRetryDialog(getString(R.string.misc_retry));
                            }
                        });
            }
            return null;
        }
    }

    private void showErrorRetryDialog(String message) {
        DialogUtil.showWithCardBackground(new AlertDialog.Builder(Crosspost.this)
                .setTitle(R.string.err_title)
                .setMessage(message)
                .setNegativeButton(R.string.btn_no, (dialogInterface, i) -> finish())
                .setPositiveButton(
                        R.string.btn_yes,
                        (dialogInterface, i) ->
                                ((FloatingActionButton) findViewById(R.id.send)).show())
                .setOnDismissListener(
                        dialog -> ((FloatingActionButton) findViewById(R.id.send)).show())
                );
    }
}
