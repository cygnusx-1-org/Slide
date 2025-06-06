package me.edgan.redditslide.Activities;

import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.EditText;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.SwitchCompat;

import com.google.android.material.floatingactionbutton.FloatingActionButton;

import me.edgan.redditslide.Authentication;
import me.edgan.redditslide.OpenRedditLink;
import me.edgan.redditslide.R;
import me.edgan.redditslide.SpoilerRobotoTextView;
import me.edgan.redditslide.UserSubscriptions;
import me.edgan.redditslide.Views.CommentOverflow;
import me.edgan.redditslide.util.SubmissionParser;
import me.edgan.redditslide.util.stubs.SimpleTextWatcher;
import me.edgan.redditslide.util.MiscUtil;

import net.dean.jraw.ApiException;
import net.dean.jraw.managers.AccountManager;
import net.dean.jraw.models.Submission;
import net.dean.jraw.models.Subreddit;

import java.util.List;

/** Created by ccrama on 3/5/2015. */
public class Crosspost extends BaseActivity {

    public static Submission toCrosspost;
    private SwitchCompat inboxReplies;

    AsyncTask<Void, Void, Subreddit> tchange;

    public void onCreate(Bundle savedInstanceState) {
        disableSwipeBackLayout();
        super.onCreate(savedInstanceState);
        applyColorTheme();
        setContentView(R.layout.activity_crosspost);

        MiscUtil.setupOldSwipeModeBackground(this, getWindow().getDecorView());

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            Window window = this.getWindow();
            window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);
            window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
        }
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
                        findViewById(R.id.submittext).setVisibility(View.GONE);
                    }
                });

        subredditText.setOnFocusChangeListener(
                new View.OnFocusChangeListener() {
                    @Override
                    public void onFocusChange(View v, boolean hasFocus) {
                        findViewById(R.id.submittext).setVisibility(View.GONE);
                        if (!hasFocus) {
                            tchange =
                                    new AsyncTask<Void, Void, Subreddit>() {
                                        @Override
                                        protected Subreddit doInBackground(Void... params) {
                                            try {
                                                return Authentication.reddit.getSubreddit(
                                                        subredditText.getText().toString());
                                            } catch (Exception ignored) {

                                            }
                                            return null;
                                        }

                                        @Override
                                        protected void onPostExecute(Subreddit s) {

                                            if (s != null) {
                                                String text =
                                                        s.getDataNode()
                                                                .get("submit_text_html")
                                                                .asText();
                                                if (text != null
                                                        && !text.isEmpty()
                                                        && !text.equals("null")) {
                                                    findViewById(R.id.submittext)
                                                            .setVisibility(View.VISIBLE);
                                                    setViews(
                                                            text,
                                                            subredditText.getText().toString(),
                                                            (SpoilerRobotoTextView)
                                                                    findViewById(R.id.submittext),
                                                            (CommentOverflow)
                                                                    findViewById(
                                                                            R.id.commentOverflow));
                                                }
                                                if (s.getSubredditType().equals("RESTRICTED")) {
                                                    subredditText.setText("");
                                                    new AlertDialog.Builder(Crosspost.this)
                                                            .setTitle(
                                                                    R.string.err_submit_restricted)
                                                            .setMessage(
                                                                    R.string
                                                                            .err_submit_restricted_text)
                                                            .setPositiveButton(
                                                                    R.string.btn_ok, null)
                                                            .show();
                                                }
                                            } else {
                                                findViewById(R.id.submittext)
                                                        .setVisibility(View.GONE);
                                            }
                                        }
                                    };
                            tchange.execute();
                        }
                    }
                });

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

        findViewById(R.id.send)
                .setOnClickListener(
                        new View.OnClickListener() {
                            @Override
                            public void onClick(View view) {
                                ((FloatingActionButton) findViewById(R.id.send)).hide();
                                new AsyncDo().execute();
                            }
                        });
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

        @Override
        protected Void doInBackground(Void... voids) {
            try {
                try {
                    Submission s =
                            new AccountManager(Authentication.reddit)
                                    .crosspost(
                                            toCrosspost,
                                            ((AutoCompleteTextView)
                                                            findViewById(R.id.subreddittext))
                                                    .getText()
                                                    .toString(),
                                            ((EditText) findViewById(R.id.titletext))
                                                    .getText()
                                                    .toString(),
                                            null,
                                            "");
                    new AccountManager(Authentication.reddit)
                            .sendRepliesToInbox(s, inboxReplies.isChecked());
                    OpenRedditLink.openUrl(
                            Crosspost.this,
                            "reddit.com/r/"
                                    + ((AutoCompleteTextView) findViewById(R.id.subreddittext))
                                            .getText()
                                            .toString()
                                    + "/comments/"
                                    + s.getFullName().substring(3),
                            true);
                    Crosspost.this.finish();
                } catch (final ApiException e) {
                    e.printStackTrace();

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
                e.printStackTrace();

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
        new AlertDialog.Builder(Crosspost.this)
                .setTitle(R.string.err_title)
                .setMessage(message)
                .setNegativeButton(R.string.btn_no, (dialogInterface, i) -> finish())
                .setPositiveButton(
                        R.string.btn_yes,
                        (dialogInterface, i) ->
                                ((FloatingActionButton) findViewById(R.id.send)).show())
                .setOnDismissListener(
                        dialog -> ((FloatingActionButton) findViewById(R.id.send)).show())
                .create()
                .show();
    }
}
