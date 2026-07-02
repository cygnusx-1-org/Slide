package me.edgan.redditslide.Activities;

import android.app.Dialog;
import android.content.Intent;
import android.content.res.Resources;
import android.graphics.Color;
import android.net.Uri;
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
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.PickVisualMediaRequest;
import androidx.activity.result.contract.ActivityResultContracts;
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
import com.google.gson.JsonObject;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import me.edgan.redditslide.Authentication;
import me.edgan.redditslide.Drafts;
import me.edgan.redditslide.Flair.RichFlair;
import me.edgan.redditslide.OpenRedditLink;
import me.edgan.redditslide.R;
import me.edgan.redditslide.Reddit;
import me.edgan.redditslide.SpoilerRobotoTextView;
import me.edgan.redditslide.UserSubscriptions;
import me.edgan.redditslide.Views.CommentOverflow;
import me.edgan.redditslide.Views.DoEditorActions;
import me.edgan.redditslide.Views.ImageInsertEditText;
import me.edgan.redditslide.Visuals.ColorPreferences;
import me.edgan.redditslide.util.DialogUtil;
import me.edgan.redditslide.util.FlairUtil;
import me.edgan.redditslide.util.KeyboardUtil;
import me.edgan.redditslide.util.LogUtil;
import me.edgan.redditslide.util.MaterialProgressDialog;
import me.edgan.redditslide.util.MiscUtil;
import me.edgan.redditslide.util.SubmissionParser;
import me.edgan.redditslide.util.TitleExtractor;
import me.edgan.redditslide.util.stubs.SimpleTextWatcher;
import net.dean.jraw.ApiException;
import net.dean.jraw.http.HttpRequest;
import net.dean.jraw.http.RestResponse;
import net.dean.jraw.managers.AccountManager;
import net.dean.jraw.models.Submission;
import net.dean.jraw.models.Subreddit;
import okhttp3.OkHttpClient;

/** Created by ccrama on 3/5/2015. */
public class Submit extends BaseActivity {

    private boolean sent;
    private String trying;
    // The locally-picked image for an image post. The upload to Reddit/Imgur is deferred until
    // submit, so this holds the content Uri in the meantime.
    private Uri selectedImageUri;
    // The locally-picked images for a Reddit gallery post; uploaded at submit time.
    private final java.util.ArrayList<Uri> selectedGalleryUris = new java.util.ArrayList<>();
    // Caption EditTexts, one per gallery image, aligned with selectedGalleryUris.
    private final java.util.ArrayList<EditText> galleryCaptionFields = new java.util.ArrayList<>();
    private String selectedFlairID;
    private String selectedFlairText;
    private boolean isFlairRequired = false;
    private SwitchCompat inboxReplies;
    private View image;
    private View link;
    private View self;
    private View gallery;
    public static final String EXTRA_SUBREDDIT = "subreddit";
    public static final String EXTRA_BODY = "body";
    public static final String EXTRA_IS_SELF = "is_self";

    private static final int FLAIR_REQUIRED_COLOR = Color.parseColor("#FF9800");

    AsyncTask<Void, Void, Subreddit> tchange;
    AsyncTask<Void, Void, Boolean> tFlairRequired;
    private final Handler subredditDebounce = new Handler(Looper.getMainLooper());
    private Runnable subredditDebounceRunnable;
    private String lastCheckedSubreddit = "";
    private OkHttpClient client;
    private Gson gson;
    private ActivityResultLauncher<PickVisualMediaRequest> submitImageLauncher;
    private ActivityResultLauncher<PickVisualMediaRequest> editorImageLauncher;
    private ActivityResultLauncher<PickVisualMediaRequest> galleryImageLauncher;
    private MaterialProgressDialog galleryProgress;

    @Override
    public void onDestroy() {
        super.onDestroy();
        try {
            String text = ((EditText) findViewById(R.id.bodytext)).getText().toString();
            if (!text.isEmpty() && sent) {
                Drafts.addDraft(text);
                Toast.makeText(getApplicationContext(), R.string.msg_save_draft, Toast.LENGTH_LONG)
                        .show();
            }
        } catch (Exception e) {

        }
    }

    public void onCreate(Bundle savedInstanceState) {
        disableSwipeBackLayout();
        super.onCreate(savedInstanceState);

        submitImageLauncher =
                registerForActivityResult(
                        new ActivityResultContracts.PickVisualMedia(),
                        uri -> {
                            if (uri != null) {
                                handleImageIntent(Collections.singletonList(uri));
                                KeyboardUtil.hideKeyboard(
                                        Submit.this,
                                        findViewById(R.id.bodytext).getWindowToken(),
                                        0);
                            }
                        });
        editorImageLauncher =
                registerForActivityResult(
                        new ActivityResultContracts.PickVisualMedia(),
                        uri -> {
                            if (uri != null
                                    && DoEditorActions.currentImageTarget != null) {
                                ArrayList<Uri> uriList = new ArrayList<>();
                                uriList.add(uri);
                                DoEditorActions.handleImageIntent(
                                        uriList,
                                        DoEditorActions.currentImageTarget,
                                        Submit.this);
                                KeyboardUtil.hideKeyboard(
                                        Submit.this,
                                        DoEditorActions.currentImageTarget.getWindowToken(),
                                        0);
                                DoEditorActions.currentImageTarget = null;
                            }
                        });
        galleryImageLauncher =
                registerForActivityResult(
                        new ActivityResultContracts.PickMultipleVisualMedia(20),
                        uris -> {
                            if (uris != null && !uris.isEmpty()) {
                                onGalleryPicked(uris);
                            }
                        });

        applyColorTheme();
        setContentView(R.layout.activity_submit);
        MiscUtil.setupOldSwipeModeBackground(this, getWindow().getDecorView());

        Window window = this.getWindow();
        window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);
        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);

        setupAppBar(R.id.toolbar, R.string.title_submit_post, true, true);

        inboxReplies = (SwitchCompat) findViewById(R.id.replies);

        Intent intent = getIntent();
        final String subreddit = intent.getStringExtra(EXTRA_SUBREDDIT);
        final String initialBody = intent.getStringExtra(EXTRA_BODY);

        self = findViewById(R.id.selftext);
        final AutoCompleteTextView subredditText =
                ((AutoCompleteTextView) findViewById(R.id.subreddittext));
        image = findViewById(R.id.image);
        link = findViewById(R.id.url);
        gallery = findViewById(R.id.gallery);

        image.setVisibility(View.GONE);
        link.setVisibility(View.GONE);
        gallery.setVisibility(View.GONE);

        if (subreddit != null
                && !subreddit.equals("frontpage")
                && !subreddit.equals("all")
                && !subreddit.equals("friends")
                && !subreddit.equals("mod")
                && !subreddit.contains("/m/")
                && !subreddit.contains("+")) {
            subredditText.setText(subreddit);
        }
        if (initialBody != null) {
            ((ImageInsertEditText) self.findViewById(R.id.bodytext)).setText(initialBody);
        }
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
                        isFlairRequired = false;
                        selectedFlairID = null;
                        selectedFlairText = null;
                        lastCheckedSubreddit = "";
                        refreshFlairState();
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

        // Pre-filled subreddit (e.g. launched from a sub view) — setText() fires before the
        // watcher is attached, so trigger the check explicitly so flair requirement loads.
        String initialSub = subredditText.getText().toString();
        if (!initialSub.isEmpty()) {
            runSubredditCheck(initialSub);
        }

        findViewById(R.id.selftextradio)
                .setOnClickListener(
                        new View.OnClickListener() {
                            @Override
                            public void onClick(View view) {
                                self.setVisibility(View.VISIBLE);

                                image.setVisibility(View.GONE);
                                link.setVisibility(View.GONE);
                                gallery.setVisibility(View.GONE);
                                updateSubmitEnabled();
                            }
                        });
        findViewById(R.id.imageradio)
                .setOnClickListener(
                        new View.OnClickListener() {
                            @Override
                            public void onClick(View view) {
                                self.setVisibility(View.GONE);
                                image.setVisibility(View.VISIBLE);
                                link.setVisibility(View.GONE);
                                gallery.setVisibility(View.GONE);
                                updateSubmitEnabled();
                            }
                        });
        findViewById(R.id.galleryradio)
                .setOnClickListener(
                        new View.OnClickListener() {
                            @Override
                            public void onClick(View view) {
                                self.setVisibility(View.GONE);
                                image.setVisibility(View.GONE);
                                link.setVisibility(View.GONE);
                                gallery.setVisibility(View.VISIBLE);
                                updateSubmitEnabled();
                            }
                        });
        findViewById(R.id.linkradio)
                .setOnClickListener(
                        new View.OnClickListener() {
                            @Override
                            public void onClick(View view) {
                                self.setVisibility(View.GONE);
                                image.setVisibility(View.GONE);
                                link.setVisibility(View.VISIBLE);
                                gallery.setVisibility(View.GONE);
                                updateSubmitEnabled();
                            }
                        });
        findViewById(R.id.selGallery)
                .setOnClickListener(
                        v ->
                                galleryImageLauncher.launch(
                                        new PickVisualMediaRequest.Builder()
                                                .setMediaType(
                                                        ActivityResultContracts.PickVisualMedia
                                                                .ImageOnly.INSTANCE)
                                                .build()));
        findViewById(R.id.flair)
                .setOnClickListener(
                        new View.OnClickListener() {
                            @Override
                            public void onClick(View view) {
                                showFlairChooser();
                            }
                        });

        findViewById(R.id.suggest)
                .setOnClickListener(
                        new View.OnClickListener() {
                            @Override
                            public void onClick(View v) {
                                new AsyncTask<String, Void, String>() {
                                    Dialog d;

                                    @Override
                                    protected String doInBackground(String... params) {
                                        try {
                                            return TitleExtractor.getPageTitle(params[0]);
                                        } catch (Exception e) {
                                            LogUtil.e(e, "Submit.doInBackground failed");
                                        }
                                        return null;
                                    }

                                    @Override
                                    protected void onPreExecute() {
                                        d =
                                                new MaterialProgressDialog.Builder(Submit.this)
                                                        .progress(true, 100)
                                                        .title(R.string.editor_finding_title)
                                                        .content(R.string.misc_please_wait)
                                                        .show()
                                                        .getDialog();
                                    }

                                    @Override
                                    protected void onPostExecute(String s) {
                                        if (s != null) {
                                            ((EditText) findViewById(R.id.titletext)).setText(s);
                                            d.dismiss();
                                        } else {
                                            d.dismiss();
                                            DialogUtil.showWithCardBackground(new AlertDialog.Builder(Submit.this)
                                                    .setTitle(R.string.title_not_found)
                                                    .setPositiveButton(R.string.btn_ok, null)
                                                    );
                                        }
                                    }
                                }.execute(
                                        ((EditText) findViewById(R.id.urltext))
                                                .getText()
                                                .toString());
                            }
                        });
        findViewById(R.id.selImage)
                .setOnClickListener(
                        v -> {
                            submitImageLauncher.launch(
                                    new PickVisualMediaRequest.Builder()
                                            .setMediaType(
                                                    ActivityResultContracts.PickVisualMedia
                                                            .ImageOnly.INSTANCE)
                                            .build());
                        });
        DoEditorActions.doActions(
                ((EditText) findViewById(R.id.bodytext)),
                findViewById(R.id.selftext),
                getSupportFragmentManager(),
                Submit.this,
                null,
                null,
                editorImageLauncher);
        if (intent.hasExtra(Intent.EXTRA_TEXT)
                && !intent.getExtras().getString(Intent.EXTRA_TEXT, "").isEmpty()
                && !intent.getBooleanExtra(EXTRA_IS_SELF, false)) {
            String data = intent.getStringExtra(Intent.EXTRA_TEXT);
            if (data.contains("\n")) {
                ((EditText) findViewById(R.id.titletext))
                        .setText(data.substring(0, data.indexOf("\n")));
                ((EditText) findViewById(R.id.urltext)).setText(data.substring(data.indexOf("\n")));
            } else {
                ((EditText) findViewById(R.id.urltext)).setText(data);
            }
            self.setVisibility(View.GONE);
            image.setVisibility(View.GONE);
            link.setVisibility(View.VISIBLE);
            ((RadioButton) findViewById(R.id.linkradio)).setChecked(true);

        } else if (intent.hasExtra(Intent.EXTRA_STREAM)) {
            final Uri imageUri = intent.getParcelableExtra(Intent.EXTRA_STREAM);
            if (imageUri != null) {
                handleImageIntent(
                        new ArrayList<Uri>() {
                            {
                                add(imageUri);
                            }
                        });
                self.setVisibility(View.GONE);
                image.setVisibility(View.VISIBLE);
                link.setVisibility(View.GONE);
                ((RadioButton) findViewById(R.id.imageradio)).setChecked(true);
            }
        }
        if (intent.hasExtra(Intent.EXTRA_SUBJECT)
                && !intent.getExtras().getString(Intent.EXTRA_SUBJECT, "").isEmpty()) {
            String data = intent.getStringExtra(Intent.EXTRA_SUBJECT);
            ((EditText) findViewById(R.id.titletext)).setText(data);
        }
        updateSubmitEnabled();
        findViewById(R.id.send)
                .setOnClickListener(
                        new View.OnClickListener() {
                            @Override
                            public void onClick(View view) {
                                if (isFlairRequired && selectedFlairID == null) {
                                    Toast.makeText(
                                                    Submit.this,
                                                    R.string.crosspost_flair_required_short,
                                                    Toast.LENGTH_LONG)
                                            .show();
                                    return;
                                }
                                ((FloatingActionButton) findViewById(R.id.send)).hide();
                                new AsyncDo().execute();
                            }
                        });
    }

    private void showFlairChooser() {
        client = Reddit.client;
        gson = new Gson();

        String subreddit = ((EditText) findViewById(R.id.subreddittext)).getText().toString();

        final Dialog d =
                new MaterialProgressDialog.Builder(Submit.this)
                        .title(R.string.submit_findingflairs)
                        .cancelable(true)
                        .content(R.string.misc_please_wait)
                        .progress(true, 100)
                        .show()
                        .getDialog();
        new AsyncTask<Void, Void, JsonArray>() {
            ArrayList<JsonObject> flairs;

            @Override
            protected JsonArray doInBackground(Void... params) {
                flairs = new ArrayList<>();
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

                        new MaterialAlertDialogBuilder(
                                        new ContextThemeWrapper(
                                                Submit.this,
                                                new ColorPreferences(Submit.this)
                                                        .getFontStyle()
                                                        .getBaseId()))
                                .setTitle(getString(R.string.submit_flairchoices, subreddit))
                                .setItems(
                                        allKeys.toArray(new CharSequence[0]),
                                        (dialog, which) -> {
                                            RichFlair selected = flairs.get(allKeys.get(which));
                                            selectedFlairID = selected.getId();
                                            selectedFlairText = selected.getText();
                                            refreshFlairState();
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
                        } catch (Exception ignored) {
                        }
                        return null;
                    }

                    @Override
                    protected void onPostExecute(Subreddit s) {
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
                                DialogUtil.showWithCardBackground(new AlertDialog.Builder(Submit.this)
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
                                    "Submit: post_requirements /r/"
                                            + subreddit
                                            + " response: "
                                            + (root == null ? "null" : root.toString()));
                            if (root != null && root.has("is_flair_required")) {
                                return root.get("is_flair_required").asBoolean(false);
                            }
                        } catch (Exception e) {
                            LogUtil.v(
                                    "Submit: post_requirements lookup failed: "
                                            + e.getClass().getSimpleName()
                                            + ": "
                                            + e.getMessage());
                        }
                        return false;
                    }

                    @Override
                    protected void onPostExecute(Boolean required) {
                        isFlairRequired = required != null && required;
                        refreshFlairState();
                    }
                };
        tFlairRequired.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }

    private void refreshFlairState() {
        TextView flair = (TextView) findViewById(R.id.flair);
        if (flair == null) {
            return;
        }
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

        boolean canSend = !(isFlairRequired && selectedFlairID == null);
        FloatingActionButton send = (FloatingActionButton) findViewById(R.id.send);
        if (send != null) {
            send.setEnabled(canSend);
            send.setAlpha(canSend ? 1f : 0.4f);
        }
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

    public void handleImageIntent(List<Uri> uris) {
        if (uris.isEmpty()) {
            return;
        }
        // Just select the image; the actual upload (to Reddit or Imgur) is deferred until submit.
        selectedImageUri = uris.get(0);

        ImageView preview = (ImageView) findViewById(R.id.imagepost);
        preview.setVisibility(View.VISIBLE);
        preview.setImageURI(selectedImageUri);

        ((TextView) findViewById(R.id.selImage)).setText(R.string.submit_change_img);

        updateSubmitEnabled();
    }

    private static final int MAX_GALLERY_IMAGES = 20;

    /**
     * Appends the newly picked gallery images (one, a batch, or all at once) to the existing
     * selection, preserving captions already typed. Duplicates are ignored and the total is capped
     * at Reddit's gallery maximum.
     */
    private void onGalleryPicked(List<Uri> uris) {
        for (Uri uri : uris) {
            if (selectedGalleryUris.size() >= MAX_GALLERY_IMAGES) {
                break;
            }
            if (selectedGalleryUris.contains(uri)) {
                continue;
            }
            addGalleryRow(uri);
        }
        updateGalleryCount();
        updateSubmitEnabled();
    }

    /** Adds one thumbnail + caption + remove row for a gallery image. */
    private void addGalleryRow(Uri uri) {
        LinearLayout items = (LinearLayout) findViewById(R.id.galleryItems);
        float density = Resources.getSystem().getDisplayMetrics().density;
        int size = (int) (64 * density);
        int margin = (int) (8 * density);
        int fontColor = resolveColorAttr(R.attr.fontColor);

        final LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(android.view.Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams rowLp =
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT);
        rowLp.setMargins(0, margin / 2, 0, margin / 2);
        row.setLayoutParams(rowLp);

        ImageView iv = new ImageView(this);
        LinearLayout.LayoutParams ivLp = new LinearLayout.LayoutParams(size, size);
        ivLp.setMargins(0, 0, margin, 0);
        iv.setLayoutParams(ivLp);
        iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
        iv.setImageURI(uri);
        row.addView(iv);

        final EditText caption = new EditText(this);
        LinearLayout.LayoutParams capLp =
                new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        caption.setLayoutParams(capLp);
        caption.setHint(R.string.submit_gallery_caption_hint);
        caption.setMaxLines(2);
        caption.setFilters(
                new android.text.InputFilter[] {new android.text.InputFilter.LengthFilter(180)});
        caption.setTextColor(fontColor);
        row.addView(caption);

        TextView remove = new TextView(this);
        remove.setText("✕");
        remove.setTextColor(fontColor);
        remove.setTextSize(18);
        remove.setPadding(margin, margin, margin, margin);
        android.util.TypedValue bg = new android.util.TypedValue();
        getTheme().resolveAttribute(android.R.attr.selectableItemBackground, bg, true);
        remove.setBackgroundResource(bg.resourceId);
        row.addView(remove);

        selectedGalleryUris.add(uri);
        galleryCaptionFields.add(caption);
        items.addView(row);

        remove.setOnClickListener(
                v -> {
                    int idx = galleryCaptionFields.indexOf(caption);
                    if (idx >= 0) {
                        selectedGalleryUris.remove(idx);
                        galleryCaptionFields.remove(idx);
                    }
                    items.removeView(row);
                    updateGalleryCount();
                    updateSubmitEnabled();
                });
    }

    private void updateGalleryCount() {
        TextView count = (TextView) findViewById(R.id.galleryCount);
        if (selectedGalleryUris.isEmpty()) {
            count.setVisibility(View.GONE);
        } else {
            count.setVisibility(View.VISIBLE);
            count.setText(getString(R.string.submit_gallery_count, selectedGalleryUris.size()));
        }
    }

    private int resolveColorAttr(int attr) {
        android.util.TypedValue tv = new android.util.TypedValue();
        getTheme().resolveAttribute(attr, tv, true);
        return tv.data;
    }

    /**
     * For an image/gallery post the submit button stays disabled until image(s) are picked, so the
     * user can't submit an empty post.
     */
    private void updateSubmitEnabled() {
        FloatingActionButton send = (FloatingActionButton) findViewById(R.id.send);
        if (send == null) {
            return;
        }
        boolean enabled = true;
        if (image.getVisibility() == View.VISIBLE) {
            enabled = selectedImageUri != null;
        } else if (gallery.getVisibility() == View.VISIBLE) {
            // Reddit galleries require at least two images.
            enabled = selectedGalleryUris.size() >= 2;
        }
        send.setEnabled(enabled);
        send.setAlpha(enabled ? 1f : 0.5f);
    }

    private void showGalleryProgress(String message) {
        if (galleryProgress == null) {
            galleryProgress =
                    new MaterialProgressDialog.Builder(this)
                            .progress(true, 0)
                            .cancelable(false)
                            .build();
        }
        galleryProgress.setTitle(message);
        galleryProgress.show();
    }

    private void dismissGalleryProgress() {
        if (galleryProgress != null) {
            galleryProgress.dismiss();
        }
    }

    private class AsyncDo extends AsyncTask<Void, Void, Void> {

        // Snapshot of all View state, captured on the UI thread in onPreExecute().
        // doInBackground() runs on a worker thread and must not touch Views directly.
        private boolean selfVisible;
        private boolean linkVisible;
        private boolean imageVisible;
        private boolean galleryVisible;
        private String bodyText;
        private String subredditText;
        private String titleText;
        private String urlText;
        private boolean sendReplies;
        private boolean imageReddit;
        private Uri imageUri;
        private java.util.ArrayList<Uri> galleryUris;
        private java.util.ArrayList<String> galleryCaptions;
        private java.util.List<me.edgan.redditslide.markdown.UploadedImage> uploadedImages;

        @Override
        protected void onPreExecute() {
            selfVisible = self.getVisibility() == View.VISIBLE;
            linkVisible = link.getVisibility() == View.VISIBLE;
            imageVisible = image.getVisibility() == View.VISIBLE;
            galleryVisible = gallery.getVisibility() == View.VISIBLE;
            imageReddit = ((RadioButton) findViewById(R.id.imageHostReddit)).isChecked();
            imageUri = selectedImageUri;
            galleryUris = new java.util.ArrayList<>(selectedGalleryUris);
            galleryCaptions = new java.util.ArrayList<>();
            for (EditText field : galleryCaptionFields) {
                galleryCaptions.add(field.getText().toString().trim());
            }
            bodyText = ((EditText) findViewById(R.id.bodytext)).getText().toString();
            uploadedImages =
                    me.edgan.redditslide.util.RedditImageUploads.consume(
                            (EditText) findViewById(R.id.bodytext));
            subredditText =
                    ((AutoCompleteTextView) findViewById(R.id.subreddittext))
                            .getText()
                            .toString();
            titleText = ((EditText) findViewById(R.id.titletext)).getText().toString();
            urlText = ((EditText) findViewById(R.id.urltext)).getText().toString();
            sendReplies = inboxReplies.isChecked();
        }

        @Override
        protected Void doInBackground(Void... voids) {
            try {
                if (selfVisible) {
                    final String text = bodyText;

                    if (uploadedImages != null && !uploadedImages.isEmpty()) {
                        // Inline Reddit images require submitting selftext as richtext_json,
                        // which JRAW's submit() cannot do.
                        try {
                            String fullName =
                                    me.edgan.redditslide.util.RichtextSubmission.submitSelf(
                                            Authentication.reddit,
                                            subredditText,
                                            titleText,
                                            bodyText,
                                            uploadedImages,
                                            sendReplies,
                                            selectedFlairID);
                            OpenRedditLink.openUrl(
                                    Submit.this,
                                    "reddit.com/r/"
                                            + subredditText
                                            + "/comments/"
                                            + fullName.substring(3),
                                    true);
                            Submit.this.finish();
                        } catch (final Exception e) {
                            Drafts.addDraft(text);
                            LogUtil.e(e, "Submit richtext self failed");
                            runOnUiThread(
                                    () ->
                                            showErrorRetryDialog(
                                                    getString(R.string.misc_err)
                                                            + ": "
                                                            + e.getMessage()
                                                            + "\n"
                                                            + getString(
                                                                    R.string.misc_retry_draft)));
                        }
                        return null;
                    }

                    try {
                        AccountManager.SubmissionBuilder builder =
                                new AccountManager.SubmissionBuilder(
                                        bodyText, subredditText, titleText);

                        if (selectedFlairID != null) {
                            builder.flairID(selectedFlairID);
                        }

                        Submission s = new AccountManager(Authentication.reddit).submit(builder);
                        new AccountManager(Authentication.reddit)
                                .sendRepliesToInbox(s, sendReplies);
                        OpenRedditLink.openUrl(
                                Submit.this,
                                "reddit.com/r/"
                                        + subredditText
                                        + "/comments/"
                                        + s.getFullName().substring(3),
                                true);
                        Submit.this.finish();
                    } catch (final ApiException e) {
                        // Network failures (bare RuntimeException) propagate to the outer
                        // catch (Exception); this branch only handles Reddit API errors.
                        Drafts.addDraft(text);
                        LogUtil.e(e, "Submit.doInBackground failed");

                        runOnUiThread(
                                new Runnable() {
                                    @Override
                                    public void run() {
                                        showErrorRetryDialog(
                                                getString(R.string.misc_err)
                                                        + ": "
                                                        + e.getExplanation()
                                                        + "\n"
                                                        + getString(R.string.misc_retry_draft));
                                    }
                                });
                    }
                } else if (linkVisible) {
                    try {
                        Submission s =
                                new AccountManager(Authentication.reddit)
                                        .submit(
                                                new AccountManager.SubmissionBuilder(
                                                        new URL(urlText),
                                                        subredditText,
                                                        titleText));
                        new AccountManager(Authentication.reddit)
                                .sendRepliesToInbox(s, sendReplies);
                        OpenRedditLink.openUrl(
                                Submit.this,
                                "reddit.com/r/"
                                        + subredditText
                                        + "/comments/"
                                        + s.getFullName().substring(3),
                                true);

                        Submit.this.finish();
                    } catch (final ApiException e) {
                        // Network failures (bare RuntimeException) propagate to the outer
                        // catch (Exception); this branch only handles Reddit API errors.
                        LogUtil.e(e, "Submit.run failed");

                        runOnUiThread(
                                new Runnable() {
                                    @Override
                                    public void run() {

                                        if (e instanceof ApiException) {
                                            showErrorRetryDialog(
                                                    getString(R.string.misc_err)
                                                            + ": "
                                                            + e.getExplanation()
                                                            + "\n"
                                                            + getString(R.string.misc_retry));
                                        } else {
                                            showErrorRetryDialog(
                                                    getString(R.string.misc_err)
                                                            + ": "
                                                            + getString(R.string.err_invalid_url)
                                                            + "\n"
                                                            + getString(R.string.misc_retry));
                                        }
                                    }
                                });
                    }
                } else if (imageVisible && imageReddit) {
                    // Upload to Reddit's media bucket, then submit a native image post
                    // (kind=image) pointing at the uploaded media URL.
                    try {
                        String mediaUrl =
                                me.edgan.redditslide.util.RedditMediaUpload.uploadForPostUrl(
                                        Submit.this, imageUri);
                        String permalink =
                                me.edgan.redditslide.util.RichtextSubmission.submitImage(
                                        Authentication.reddit,
                                        subredditText,
                                        titleText,
                                        mediaUrl,
                                        sendReplies,
                                        selectedFlairID);
                        OpenRedditLink.openUrl(
                                Submit.this,
                                permalink != null
                                        ? permalink
                                        : "reddit.com/r/" + subredditText,
                                true);
                        Submit.this.finish();
                    } catch (final Exception e) {
                        LogUtil.e(e, "Submit reddit image failed");
                        runOnUiThread(
                                () ->
                                        showErrorRetryDialog(
                                                getString(R.string.misc_err)
                                                        + ": "
                                                        + e.getMessage()
                                                        + "\n"
                                                        + getString(R.string.misc_retry)));
                    }
                } else if (imageVisible) {
                    try {
                        // Upload to Imgur, then submit the resulting link as the post URL.
                        String imgurUrl =
                                me.edgan.redditslide.util.ImgurUtils.uploadSync(
                                        Submit.this, imageUri);
                        Submission s =
                                new AccountManager(Authentication.reddit)
                                        .submit(
                                                new AccountManager.SubmissionBuilder(
                                                        new URL(imgurUrl),
                                                        subredditText,
                                                        titleText));
                        new AccountManager(Authentication.reddit)
                                .sendRepliesToInbox(s, sendReplies);
                        OpenRedditLink.openUrl(
                                Submit.this,
                                "reddit.com/r/"
                                        + subredditText
                                        + "/comments/"
                                        + s.getFullName().substring(3),
                                true);

                        Submit.this.finish();
                    } catch (final Exception e) {
                        runOnUiThread(
                                new Runnable() {
                                    @Override
                                    public void run() {
                                        if (e instanceof ApiException) {
                                            showErrorRetryDialog(
                                                    getString(R.string.misc_err)
                                                            + ": "
                                                            + ((ApiException) e).getExplanation()
                                                            + "\n"
                                                            + getString(R.string.misc_retry));
                                        } else {
                                            showErrorRetryDialog(
                                                    getString(R.string.misc_err)
                                                            + ": "
                                                            + getString(R.string.err_invalid_url)
                                                            + "\n"
                                                            + getString(R.string.misc_retry));
                                        }
                                    }
                                });
                    }
                } else if (galleryVisible) {
                    // Upload every image to Reddit's media bucket, then submit a gallery post.
                    try {
                        java.util.ArrayList<String> assetIds = new java.util.ArrayList<>();
                        for (int i = 0; i < galleryUris.size(); i++) {
                            final int index = i + 1;
                            final int total = galleryUris.size();
                            runOnUiThread(
                                    () ->
                                            showGalleryProgress(
                                                    getString(
                                                            R.string.submit_uploading_gallery,
                                                            index,
                                                            total)));
                            assetIds.add(
                                    me.edgan.redditslide.util.RedditMediaUpload
                                            .uploadForGalleryAssetId(
                                                    Submit.this, galleryUris.get(i)));
                        }
                        String permalink =
                                me.edgan.redditslide.util.RichtextSubmission.submitGallery(
                                        Authentication.reddit,
                                        subredditText,
                                        titleText,
                                        assetIds,
                                        galleryCaptions,
                                        sendReplies,
                                        selectedFlairID);
                        runOnUiThread(Submit.this::dismissGalleryProgress);
                        OpenRedditLink.openUrl(
                                Submit.this,
                                permalink != null ? permalink : "reddit.com/r/" + subredditText,
                                true);
                        Submit.this.finish();
                    } catch (final Exception e) {
                        LogUtil.e(e, "Submit gallery failed");
                        runOnUiThread(
                                () -> {
                                    dismissGalleryProgress();
                                    showErrorRetryDialog(
                                            getString(R.string.misc_err)
                                                    + ": "
                                                    + e.getMessage()
                                                    + "\n"
                                                    + getString(R.string.misc_retry));
                                });
                    }
                }
            } catch (Exception e) {
                LogUtil.e(e, "Submit.run failed");

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
        DialogUtil.showWithCardBackground(new AlertDialog.Builder(Submit.this)
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
