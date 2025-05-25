package me.edgan.redditslide.ui.settings.dragSort;

import static me.edgan.redditslide.UserSubscriptions.setPinned;

import android.app.Dialog;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.Color;
import android.os.AsyncTask;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.AppCompatCheckBox;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;
import com.nambimobile.widgets.efab.FabOption;

import me.edgan.redditslide.Activities.BaseActivityAnim;
import me.edgan.redditslide.Authentication;
import me.edgan.redditslide.CaseInsensitiveArrayList;
import me.edgan.redditslide.R;
import me.edgan.redditslide.SettingValues;
import me.edgan.redditslide.UserSubscriptions;
import me.edgan.redditslide.Visuals.ColorPreferences;
import me.edgan.redditslide.Visuals.Palette;
import me.edgan.redditslide.ui.settings.SettingsThemeFragment;
import me.edgan.redditslide.util.BlendModeUtil;
import me.edgan.redditslide.util.DialogUtil;
import me.edgan.redditslide.util.DisplayUtil;
import me.edgan.redditslide.util.MiscUtil;

import net.dean.jraw.http.MultiRedditUpdateRequest;
import net.dean.jraw.managers.MultiRedditManager;
import net.dean.jraw.models.MultiReddit;
import net.dean.jraw.models.Subreddit;
import net.dean.jraw.paginators.SubredditSearchPaginator;
import net.dean.jraw.paginators.UserSubredditsPaginator;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.lang.ref.WeakReference;
import java.util.stream.Collectors;

public class ReorderSubreddits extends BaseActivityAnim {

    private CaseInsensitiveArrayList subs;
    private CustomAdapter adapter;
    private RecyclerView recyclerView;
    private String input;
    public static final String MULTI_REDDIT = "/m/";
    MenuItem subscribe;

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.reorder_subs, menu);

        subscribe = menu.findItem(R.id.alphabetize_subscribe);
        subscribe.setChecked(SettingValues.alphabetizeOnSubscribe);

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                finish();
                return true;
            case R.id.refresh:
                done = 0;
                // Inflate the custom progress layout
                View progressView = getLayoutInflater().inflate(R.layout.dialog_progress, null);
                TextView progressText = progressView.findViewById(R.id.progress_text);
                progressText.setText(R.string.misc_please_wait);

                // Create the dialog using MaterialAlertDialogBuilder
                final AlertDialog d = new MaterialAlertDialogBuilder(ReorderSubreddits.this)
                        .setTitle(R.string.general_sub_sync)
                        .setView(progressView)
                        .setCancelable(false)
                        .create();

                // Apply custom border
                DialogUtil.applyCustomBorderToAlertDialog(ReorderSubreddits.this, d);
                d.show();

                new AsyncTask<Void, Void, ArrayList<String>>() {
                    @Override
                    protected ArrayList<String> doInBackground(Void... params) {
                        ArrayList<String> newSubs = new ArrayList<>(UserSubscriptions.syncSubreddits(ReorderSubreddits.this));
                        UserSubscriptions.syncMultiReddits(ReorderSubreddits.this);
                        return newSubs;
                    }

                    @Override
                    protected void onPostExecute(ArrayList<String> newSubs) {
                        d.dismiss();
                        // Determine if we should insert subreddits at the end of the list or sorted
                        boolean sorted = (subs.equals(UserSubscriptions.sortNoExtras(subs)));
                        Resources res = getResources();

                        // Add null check before iterating over newSubs
                        if (newSubs != null) {
                            for (String s : newSubs) {
                                if (!subs.contains(s)) {
                                    done++;
                                    subs.add(s);
                                }
                            }
                            if (sorted && done > 0) {
                                subs = UserSubscriptions.sortNoExtras(subs);
                                adapter = new CustomAdapter(subs);
                                recyclerView.setAdapter(adapter);
                            } else if (done > 0) {
                                adapter.notifyDataSetChanged();
                                recyclerView.smoothScrollToPosition(subs.size());
                            }
                        } else {
                            // Handle null case - show an error message using Toast instead of Snackbar
                            Toast.makeText(ReorderSubreddits.this, R.string.misc_err_sync_failed, Toast.LENGTH_SHORT).show();
                        }

                        // Show completion dialog
                        AlertDialog dialog = new MaterialAlertDialogBuilder(ReorderSubreddits.this)
                                .setTitle(R.string.reorder_sync_complete)
                                .setMessage(res.getQuantityString(R.plurals.reorder_subs_added, done, done))
                                .setPositiveButton(R.string.btn_ok, null)
                                .create();

                        // Apply custom border
                        DialogUtil.applyCustomBorderToAlertDialog(ReorderSubreddits.this, dialog);
                        dialog.show();
                    }
                }.execute();
                return true;
            case R.id.alphabetize:
                subs = UserSubscriptions.sortNoExtras(subs);
                adapter = new CustomAdapter(subs);
                //  adapter.setHasStableIds(true);
                recyclerView.setAdapter(adapter);
                return true;
            case R.id.alphabetize_subscribe:
                SettingValues.prefs
                        .edit()
                    .putBoolean(SettingValues.PREF_ALPHABETIZE_SUBSCRIBE, !SettingValues.alphabetizeOnSubscribe)
                        .apply();
                SettingValues.alphabetizeOnSubscribe = !SettingValues.alphabetizeOnSubscribe;
                if (subscribe != null) subscribe.setChecked(SettingValues.alphabetizeOnSubscribe);
                return true;
            case R.id.info:
                AlertDialog faqDialog = new MaterialAlertDialogBuilder(ReorderSubreddits.this)
                        .setTitle(R.string.reorder_subs_FAQ)
                        .setMessage(R.string.sorting_faq)
                        .setPositiveButton(R.string.btn_ok, null)
                        .create();

                // Apply custom border
                DialogUtil.applyCustomBorderToAlertDialog(ReorderSubreddits.this, faqDialog);
                faqDialog.show();
                return true;
        }
        return false;
    }

    @Override
    public void onPause() {
        try {
            UserSubscriptions.setSubscriptions(new CaseInsensitiveArrayList(subs));
            SettingsThemeFragment.changed = true;
        } catch (Exception e) {

        }
        super.onPause();
    }

    @Override
    public void onBackPressed() {
        if (isMultiple) {
            chosen = new ArrayList<>();
            doOldToolbar();
            adapter.notifyDataSetChanged();
            isMultiple = false;
        } else {
            super.onBackPressed();
        }
    }

    private ArrayList<String> chosen = new ArrayList<>();
    HashMap<String, Boolean> isSubscribed;
    private boolean isMultiple;
    private int done = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        disableSwipeBackLayout();
        super.onCreate(savedInstanceState);
        applyColorTheme();
        setContentView(R.layout.activity_sort);

        MiscUtil.setupOldSwipeModeBackground(this, getWindow().getDecorView());

        setupAppBar(R.id.toolbar, R.string.settings_manage_subscriptions, false, true);
        mToolbar.setPopupTheme(new ColorPreferences(this).getFontStyle().getBaseId());
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        isSubscribed = new HashMap<>();
        if (Authentication.isLoggedIn) {
            new AsyncTask<Void, Void, Void>() {
                boolean success = true;

                @Override
                protected Void doInBackground(Void... params) {
                    ArrayList<Subreddit> subs = new ArrayList<>();
                    UserSubredditsPaginator p =
                            new UserSubredditsPaginator(Authentication.reddit, "subscriber");
                    try {
                        while (p.hasNext()) {
                            subs.addAll(p.next());
                        }
                    } catch (Exception e) {
                        success = false;
                        return null;
                    }

                    for (Subreddit s : subs) {
                        isSubscribed.put(s.getDisplayName().toLowerCase(Locale.ENGLISH), true);
                    }

                    if (UserSubscriptions.multireddits == null) {
                        UserSubscriptions.loadMultireddits();
                    }
                    return null;
                }

                @Override
                protected void onPostExecute(Void aVoid) {
                    if (success) {
                        d.dismiss();
                        doShowSubs();
                    } else {
                        AlertDialog errorDialog = new MaterialAlertDialogBuilder(ReorderSubreddits.this)
                                .setTitle(R.string.err_title)
                                .setMessage(R.string.misc_please_try_again_soon)
                                .setCancelable(false)
                                .setPositiveButton(R.string.btn_ok, (dialog, which) -> finish())
                            .create();

                        // Apply custom border using DialogUtil
                        DialogUtil.applyCustomBorderToAlertDialog(ReorderSubreddits.this, errorDialog);
                        errorDialog.show();
                    }
                }

                Dialog d;

                @Override
                protected void onPreExecute() {
                    // Inflate the custom progress layout
                    View progressView = getLayoutInflater().inflate(R.layout.dialog_progress, null);
                    TextView progressText = progressView.findViewById(R.id.progress_text);
                    progressText.setText(R.string.misc_please_wait);

                    d = new MaterialAlertDialogBuilder(ReorderSubreddits.this)
                        .setTitle(R.string.reorder_loading_title)
                        .setView(progressView)
                        .setCancelable(false)
                        .create();

                    // Apply custom border
                    DialogUtil.applyCustomBorderToAlertDialog(ReorderSubreddits.this, (AlertDialog)d);
                    d.show();
                }
            }.execute();
        } else {
            doShowSubs();
        }
    }

    public void doShowSubs() {
        subs = new CaseInsensitiveArrayList(UserSubscriptions.getSubscriptions(this));
        recyclerView = (RecyclerView) findViewById(R.id.subslist);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setItemAnimator(null);

        DragSortRecycler dragSortRecycler = new DragSortRecycler();
        dragSortRecycler.setViewHandleId();
        dragSortRecycler.setFloatingAlpha();
        dragSortRecycler.setAutoScrollSpeed();
        dragSortRecycler.setAutoScrollWindow();

        dragSortRecycler.setOnItemMovedListener(
                new DragSortRecycler.OnItemMovedListener() {
                    @Override
                    public void onItemMoved(int from, int to) {
                        if (to == subs.size()) {
                            to -= 1;
                        }
                        String item = subs.remove(from);
                        subs.add(to, item);
                        adapter.notifyDataSetChanged();

                        CaseInsensitiveArrayList pinned = UserSubscriptions.getPinned();
                        if (pinned.contains(item) && pinned.size() != 1) {
                            pinned.remove(item);
                            if (to > pinned.size()) {
                                to = pinned.size();
                            }
                            pinned.add(to, item);
                            setPinned(pinned);
                        }
                    }
                });

        dragSortRecycler.setOnDragStateChangedListener(
                new DragSortRecycler.OnDragStateChangedListener() {
                    @Override
                    public void onDragStart() {}

                    @Override
                    public void onDragStop() {}
                });

        {
            final FabOption collectionFab =
                    (FabOption) findViewById(R.id.sort_fabOption_collection);
            collectionFab.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            if (UserSubscriptions.multireddits != null && !UserSubscriptions.multireddits.isEmpty()) {
                                AlertDialog multiOptionsDialog = new MaterialAlertDialogBuilder(ReorderSubreddits.this)
                                        .setTitle(R.string.create_or_import_multi)
                                        .setPositiveButton(R.string.btn_new, (dialog, which) -> doMultiReddit())
                                        .setNegativeButton(R.string.btn_import_multi, (dialog, which) -> showImportMultiredditDialog())
                                        .create();

                                // Apply custom border
                                DialogUtil.applyCustomBorderToAlertDialog(ReorderSubreddits.this, multiOptionsDialog);
                                multiOptionsDialog.show();
                            } else {
                                doMultiReddit();
                            }
                        }
                    });
        }
        {
            final FabOption subFab = (FabOption) findViewById(R.id.sort_fabOption_sub);
            subFab.setOnClickListener(
                    new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            // Create a custom layout with EditText for input
                            View dialogView = getLayoutInflater().inflate(R.layout.dialog_edit_text, null);
                            EditText editText = dialogView.findViewById(R.id.dialog_edit_text);
                            editText.setHint(getString(R.string.reorder_subreddit_name));

                            // Create and show the dialog
                            AlertDialog dialog = new MaterialAlertDialogBuilder(ReorderSubreddits.this)
                                .setTitle(R.string.reorder_add_or_search_subreddit)
                                .setView(dialogView)
                                .setPositiveButton(R.string.btn_add, (dialogInterface, which) -> {
                                    // Get the input from the EditText and execute the AsyncTask
                                    input = editText.getText().toString();
                                    new AsyncGetSubreddit().execute(input);
                                })
                                .setNegativeButton(R.string.btn_cancel, null)
                                .create();

                            // Apply the custom border
                            DialogUtil.applyCustomBorderToAlertDialog(ReorderSubreddits.this, dialog);

                            dialog.show();
                            Button positiveButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE);

                            // Set initial state (disabled until valid input)
                            positiveButton.setEnabled(false);

                            // Create input filter for real-time validation
                            TextWatcher textWatcher = new TextWatcher() {
                                @Override
                                public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

                                @Override
                                public void onTextChanged(CharSequence s, int start, int before, int count) {}

                                @Override
                                public void afterTextChanged(Editable s) {
                                    input = s.toString().trim();
                                    // Only proceed if input is valid
                                    // 2 min, 21 max
                                    // de is an example of 2
                                    positiveButton.setEnabled(input.length() >= 2 && input.length() <= 21);
                                }
                            };

                            // Add the text watcher to the EditText
                            editText.addTextChangedListener(textWatcher);

                            // Override click listener to handle input
                            positiveButton.setOnClickListener(v2 -> {
                                // Get final text from EditText
                                input = editText.getText().toString().trim();

                                addDomainUrl(input);
                                dialog.dismiss();
                            });
                        }
                    });
        }
        {
            final FabOption domainFab = (FabOption) findViewById(R.id.sort_fabOption_domain);
            domainFab.setOnClickListener(
                    new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            int maxLength = 253;
                            // Create custom layout with EditText for domain input
                            View dialogView = getLayoutInflater().inflate(R.layout.dialog_edit_text, null);
                            EditText editText = dialogView.findViewById(R.id.dialog_edit_text);
                            editText.setHint(getString(R.string.reorder_domain_placeholder));

                            // Create and show the dialog
                            AlertDialog dialog = new MaterialAlertDialogBuilder(ReorderSubreddits.this)
                                    .setTitle(R.string.reorder_add_domain)
                                    .setView(dialogView)
                                    .setPositiveButton(R.string.btn_add, null) // We'll set this later
                                    .setNegativeButton(R.string.btn_cancel, null)
                                    .create();

                            // Set input filters for length limitation (1-35 characters)
                            editText.setFilters(new android.text.InputFilter[] {
                                new android.text.InputFilter.LengthFilter(maxLength)
                            });

                            // Add TextWatcher for real-time validation
                            editText.addTextChangedListener(new TextWatcher() {
                                @Override
                                public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

                                @Override
                                public void onTextChanged(CharSequence s, int start, int before, int count) {}

                                @Override
                                public void afterTextChanged(Editable s) {
                                    // Remove whitespace from input
                                    input = s.toString().replaceAll("\\s", "");

                                    // Enable/disable positive button based on validation
                                    Button positiveButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
                                    if (positiveButton != null) {
                                        boolean isValid = input.contains(".") && input.length() >= 3;

                                        // Check total length and segment lengths
                                        if (isValid) {
                                            isValid = input.length() <= maxLength;
                                            if (isValid) {
                                                String[] segments = input.split("\\.");
                                                for (String segment : segments) {
                                                    if (segment.length() > 63) {
                                                        isValid = false;
                                                        break;
                                                    }
                                                }
                                            }
                                        }
                                        positiveButton.setEnabled(isValid);
                                    }
                                }
                            });

                            // Apply the custom border
                            DialogUtil.applyCustomBorderToAlertDialog(ReorderSubreddits.this, dialog);

                            // Show the dialog first
                            dialog.show();

                            // Now we can get the positive button AFTER dialog is shown
                            Button positiveButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE);

                            // Set initial state (disabled until valid input)
                            positiveButton.setEnabled(false);

                            // Override click listener to handle domain URL addition
                            positiveButton.setOnClickListener(v2 -> {
                                // Get final text from EditText
                                input = editText.getText().toString().replaceAll("\\s", "");

                                addDomainUrl(input);
                                dialog.dismiss();
                            });
                        }
                    });
        }
        recyclerView.addItemDecoration(dragSortRecycler);
        recyclerView.addOnItemTouchListener(dragSortRecycler);
        recyclerView.addOnScrollListener(dragSortRecycler.getScrollListener());
        dragSortRecycler.setViewHandleId();

        if (subs != null && !subs.isEmpty()) {
            adapter = new CustomAdapter(subs);
            recyclerView.setAdapter(adapter);
        } else {
            subs = new CaseInsensitiveArrayList();
        }

        recyclerView.addOnScrollListener(
                new RecyclerView.OnScrollListener() {
                    @Override
                    public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                        super.onScrolled(recyclerView, dx, dy);
                        if (recyclerView.getScrollState() == RecyclerView.SCROLL_STATE_DRAGGING) {
                            diff += dy;
                        } else {
                            diff = 0;
                        }
                    }
                });
    }

    public int diff;

    public void doMultiReddit() {
        // Use a static AsyncTask with WeakReference to prevent memory leaks
        MultiRedditSyncTask task = new MultiRedditSyncTask(this);
        task.execute();
    }

    // Static inner class to prevent memory leaks
    private static class MultiRedditSyncTask extends AsyncTask<Void, Void, ArrayList<String>> {
        private final WeakReference<ReorderSubreddits> activityRef;

        public MultiRedditSyncTask(ReorderSubreddits activity) {
            this.activityRef = new WeakReference<>(activity);
        }

        @Override
        protected ArrayList<String> doInBackground(Void... params) {
            ReorderSubreddits activity = activityRef.get();
            if (activity == null) return null; // Activity has been destroyed

            ArrayList<String> newSubs = new ArrayList<>(UserSubscriptions.syncSubreddits(activity));
            UserSubscriptions.syncMultiReddits(activity);
            return newSubs;
        }

        @Override
        protected void onPostExecute(ArrayList<String> newSubs) {
            ReorderSubreddits activity = activityRef.get();
            if (activity == null || newSubs == null) return; // Activity has been destroyed or task failed

            // Update the local subs list with any new subscriptions
            for (String s : newSubs) {
                if (!activity.subs.contains(s)) {
                    activity.subs.add(s);
                }
            }

            // Continue by showing the subreddit selection dialog
            activity.multiRedditCreateDialog();
        }
    }

    // The original dialog code moved to a separate method
    private void multiRedditCreateDialog() {
        final String[] subreddits = new String[UserSubscriptions.getSubscriptions(this).size()];
            int i = 0;
        for (String s : UserSubscriptions.getSubscriptions(this)) {
            subreddits[i] = s;
                i++;
            }

        // Create a boolean array to track selections
        final boolean[] checkedItems = new boolean[subreddits.length];

        // Create and show the dialog
        AlertDialog multiSelectDialog = new MaterialAlertDialogBuilder(ReorderSubreddits.this)
                .setTitle(R.string.reorder_subreddits_title)
                .setMultiChoiceItems(subreddits, checkedItems, (dialog, which, isChecked) -> {
                    // Update selection state when user clicks items
                    checkedItems[which] = isChecked;
                })
                .setPositiveButton(R.string.btn_add, (dialog, which) -> {
                    // Convert checked items to Integer[] selections
                    ArrayList<Integer> selectedIndicesList = new ArrayList<>();
                    for (int index = 0; index < checkedItems.length; index++) {
                        if (checkedItems[index]) {
                            selectedIndicesList.add(index);
                        }
                    }

                    // Convert ArrayList to Integer[]
                    Integer[] selections = selectedIndicesList.toArray(new Integer[0]);

                    // Only proceed if at least one item was selected
                    if (selections.length > 0) {
                        showMultiredditNameDialog(selections, subreddits);
                    }
                })
                .setNegativeButton(R.string.btn_cancel, null)
                .create();

        // Apply custom border
        DialogUtil.applyCustomBorderToAlertDialog(ReorderSubreddits.this, multiSelectDialog);

        multiSelectDialog.show();
    }

    /**
     * Shows a dialog prompting the user to enter a name for the new multireddit
     *
     * @param selections Array of selected subreddit indices
     * @param subreddits Array of available subreddits
     */
    private void showMultiredditNameDialog(Integer[] selections, String[] subreddits) {
        // Create a custom layout with EditText
        LayoutInflater inflater = LayoutInflater.from(this);
        View dialogView = inflater.inflate(R.layout.dialog_edit_text, null);
        EditText editText = dialogView.findViewById(R.id.dialog_edit_text);
        editText.setHint(R.string.multireddit_name_hint);

        // Set max length to match Reddit's limitations (50 characters)
        editText.setFilters(new android.text.InputFilter[] {
            new android.text.InputFilter.LengthFilter(50)
        });

        AlertDialog dialog = new MaterialAlertDialogBuilder(ReorderSubreddits.this)
                .setTitle(R.string.multireddit_name_title)
                .setView(dialogView)
                .setPositiveButton(R.string.btn_create, null) // Set this to null, we'll override it
                .setNegativeButton(R.string.btn_cancel, null)
                .create();

        // Apply custom border
        DialogUtil.applyCustomBorderToAlertDialog(ReorderSubreddits.this, dialog);

        // Set OnShowListener for button customization only
        dialog.setOnShowListener(dialogInterface -> {
            // Get the positive button
            Button positiveButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE);

            // Set custom click listener to handle validation
            positiveButton.setOnClickListener(v -> {
                String displayName = editText.getText().toString().toLowerCase(Locale.ENGLISH).trim();

                // Validate input
                if (displayName.isEmpty()) {
                    editText.setError(getString(R.string.multireddit_err_empty_name));
                    return; // Don't dismiss if validation fails
                }

                // Valid input, dismiss dialog and create multireddit
                dialog.dismiss();
                createMultireddit(displayName, selections, subreddits);
            });

            // Request focus on the EditText
            editText.requestFocus();
        });

        dialog.show();
    }

    /**
     * Creates a multireddit with the given name and selected subreddits
     * @param displayName The name for the multireddit
     * @param selections The indices of the selected subreddits
     * @param subreddits The array of all available subreddits
     */
    private void createMultireddit(String displayName, Integer[] selections, String[] subreddits) {
        // Create a list of selected subreddit names for the API
        List<Map<String, String>> subList = new ArrayList<>();
        StringBuilder selectedSubredditsDisplay = new StringBuilder();

        for (Integer index : selections) {
            String subName = subreddits[index];

            // Skip if this is already a multireddit
            if (subName.contains(MULTI_REDDIT)) {
                continue;
            }

            // Add to the list for API
            Map<String, String> subMap = new HashMap<>();
            subMap.put("name", subName);
            subList.add(subMap);

            // Also build a display string for logging/debugging
            selectedSubredditsDisplay.append(subName).append("+");
        }

        // Remove trailing + if present
        if (selectedSubredditsDisplay.length() > 0) {
            selectedSubredditsDisplay.deleteCharAt(selectedSubredditsDisplay.length() - 1);
        }

        if (subList.isEmpty()) {
            AlertDialog noValidSubsDialog = new MaterialAlertDialogBuilder(ReorderSubreddits.this)
                .setTitle(R.string.err_title)
                .setMessage(R.string.multireddit_err_no_valid_subs)
                .setPositiveButton(R.string.btn_ok, null)
                .create();

            // Apply custom border
            DialogUtil.applyCustomBorderToAlertDialog(ReorderSubreddits.this, noValidSubsDialog);
            noValidSubsDialog.show();
            return;
        }

        // Build the JSON data for the API request
        try {
            JSONObject jsonData = new JSONObject();
            jsonData.put("description_md", "");
            jsonData.put("display_name", displayName);
            jsonData.put("visibility", "private");

            JSONArray subsArray = new JSONArray();
            for (Map<String, String> sub : subList) {
                JSONObject subObj = new JSONObject();
                subObj.put("name", sub.get("name"));
                subsArray.put(subObj);
            }
            jsonData.put("subreddits", subsArray);

            // Create the multireddit path
            String username = Authentication.name != null ? Authentication.name : "anonymous";
            final String multiPath = "user/" + username + "/m/" + displayName;

            // Inflate the custom progress layout
            View progressView = getLayoutInflater().inflate(R.layout.dialog_progress, null);
            TextView progressText = progressView.findViewById(R.id.progress_text);
            progressText.setText(R.string.multireddit_progress_message);

            // Create the dialog using MaterialAlertDialogBuilder
            final AlertDialog progressDialog = new MaterialAlertDialogBuilder(ReorderSubreddits.this)
                .setTitle(R.string.multireddit_progress_title)
                .setView(progressView)
                .setCancelable(false)
                .create();

            // Apply custom border and show dialog
            DialogUtil.applyCustomBorderToAlertDialog(ReorderSubreddits.this, progressDialog);
            progressDialog.show();

            // Create the multireddit via API
            new AsyncTask<Void, Void, Boolean>() {
                @Override
                protected Boolean doInBackground(Void... params) {
                    try {
                        // Create a MultiRedditManager instance
                        MultiRedditManager manager = new MultiRedditManager(Authentication.reddit);

                        // Use the proper API method from the JRAW library
                        MultiReddit created = manager.createOrUpdate(
                            new MultiRedditUpdateRequest.Builder(username, displayName)
                                .description("")
                                .visibility(MultiReddit.Visibility.PRIVATE)
                                .subreddits(subList.stream()
                                    .map(map -> map.get("name"))
                                    .collect(Collectors.toList()))
                                .build());

                        return true;
                    } catch (Exception e) {
                        e.printStackTrace();
                        return false;
                    }
                }

                @Override
                protected void onPostExecute(Boolean success) {
                    progressDialog.dismiss();

                    if (success) {
                        // Add the multireddit to the subscription list
                        int pos = addSubAlphabetically(MULTI_REDDIT + displayName);

                        // Set the correct URL format for accessing it in the app
                        String urlForApp = String.format("api/%s", multiPath).toLowerCase(Locale.ENGLISH);
                        UserSubscriptions.setSubNameToProperties(MULTI_REDDIT + displayName, urlForApp);

                        // Sync multireddits to ensure everything is up to date
                        new AsyncTask<Void, Void, Void>() {
                            @Override
                            protected Void doInBackground(Void... params) {
                                UserSubscriptions.syncMultiReddits(ReorderSubreddits.this);
                                return null;
                            }
                        }.execute();

                        // Update UI
                        adapter.notifyDataSetChanged();
                        recyclerView.smoothScrollToPosition(pos);

                        // Show success message
                        Toast.makeText(ReorderSubreddits.this, getString(R.string.multireddit_created_success), Toast.LENGTH_SHORT).show();
                    } else {
                        // Show error message
                        AlertDialog errorDialog = new MaterialAlertDialogBuilder(ReorderSubreddits.this)
                            .setTitle(R.string.err_title)
                            .setMessage(R.string.multireddit_created_error)
                            .setPositiveButton(R.string.btn_ok, null)
                            .create();

                        // Apply custom border
                        DialogUtil.applyCustomBorderToAlertDialog(ReorderSubreddits.this, errorDialog);
                        errorDialog.show();
                    }
                }
            }.execute();
        } catch (JSONException e) {
            e.printStackTrace();
            AlertDialog jsonErrorDialog = new MaterialAlertDialogBuilder(ReorderSubreddits.this)
                .setTitle(R.string.err_title)
                .setMessage(R.string.multireddit_json_error)
                .setPositiveButton(R.string.btn_ok, null)
                .create();

                // Apply custom border
                DialogUtil.applyCustomBorderToAlertDialog(ReorderSubreddits.this, jsonErrorDialog);
                jsonErrorDialog.show();
        }
    }

    public void doAddSub(String subreddit) {
        subreddit = subreddit.toLowerCase(Locale.ENGLISH);
        List<String> sortedSubs = UserSubscriptions.sortNoExtras(subs);

        if (sortedSubs.equals(subs)) {
            subs.add(subreddit);
            subs = UserSubscriptions.sortNoExtras(subs);
            adapter = new CustomAdapter(subs);
            recyclerView.setAdapter(adapter);
        } else {
            int pos = addSubAlphabetically(subreddit);
            adapter.notifyDataSetChanged();
            recyclerView.smoothScrollToPosition(pos);
        }
    }

    private int addSubAlphabetically(String finalS) {
        int i = subs.size() - 1;
        while (i >= 0 && finalS.compareTo(subs.get(i)) < 0) {
            i--;
        }
        i += 1;
        subs.add(i, finalS);
        return i;
    }

    private class AsyncGetSubreddit extends AsyncTask<String, Void, Subreddit> {
        @Override
        public void onPostExecute(Subreddit subreddit) {
            if (subreddit != null) {
                doAddSub(subreddit.getDisplayName());
            } else if (isSpecial(sub)) {
                doAddSub(sub);
            }
        }

        ArrayList<Subreddit> otherSubs;
        String sub;

        @Override
        protected Subreddit doInBackground(final String... params) {
            sub = params[0];
            if (isSpecial(sub)) return null;
            try {
                return (subs.contains(params[0])
                        ? null
                        : Authentication.reddit.getSubreddit(params[0]));
            } catch (Exception e) {
                otherSubs = new ArrayList<>();
                SubredditSearchPaginator p =
                        new SubredditSearchPaginator(Authentication.reddit, sub);
                while (p.hasNext()) {
                    otherSubs.addAll((p.next()));
                }
                if (otherSubs.isEmpty()) {
                    runOnUiThread(
                            new Runnable() {
                                @Override
                                public void run() {
                                    try {
                                        AlertDialog errorDialog = new MaterialAlertDialogBuilder(ReorderSubreddits.this)
                                                .setTitle(R.string.subreddit_err)
                                                .setMessage(getString(R.string.subreddit_err_msg, params[0]))
                                                .setPositiveButton(R.string.btn_ok, (dialog, which) -> dialog.dismiss())
                                                .setOnDismissListener(null)
                                                .create();

                                        // Apply custom border
                                        DialogUtil.applyCustomBorderToAlertDialog(ReorderSubreddits.this, errorDialog);
                                        errorDialog.show();
                                    } catch (Exception ignored) {
                                    }
                                }
                            });
                } else {
                    runOnUiThread(
                            new Runnable() {
                                @Override
                                public void run() {
                                    try {
                                        final ArrayList<String> subs = new ArrayList<>();
                                        for (Subreddit s : otherSubs) {
                                            subs.add(s.getDisplayName());
                                        }
                                        AlertDialog searchResultsDialog = new MaterialAlertDialogBuilder(ReorderSubreddits.this)
                                                .setTitle(R.string.reorder_not_found_err)
                                                .setItems(subs.toArray(new String[0]), (dialog, which) -> doAddSub(subs.get(which)))
                                                .setPositiveButton(R.string.btn_cancel, null)
                                                .create();

                                        // Apply custom border
                                        DialogUtil.applyCustomBorderToAlertDialog(ReorderSubreddits.this, searchResultsDialog);
                                        searchResultsDialog.show();
                                    } catch (Exception ignored) {
                                    }
                                }
                            });
                }
            }
            return null;
        }
    }

    public void doOldToolbar() {
        mToolbar = (Toolbar) findViewById(R.id.toolbar);
        if (mToolbar != null) {
            mToolbar.setVisibility(View.VISIBLE);
        } else {
            Log.e("ReorderSubreddits", "Could not find toolbar");
            Toast.makeText(ReorderSubreddits.this, "Failed to find toolbar", Toast.LENGTH_SHORT).show();
        }
    }

    public class CustomAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
        private final ArrayList<String> items;

        public CustomAdapter(ArrayList<String> items) {
            this.items = items;
        }

        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            if (viewType == 2) {
                View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.spacer, parent, false);

                return new SpacerViewHolder(v);
            }
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.subforsublistdrag, parent, false);

            return new ViewHolder(v);
        }

        @Override
        public int getItemViewType(int position) {
            if (position == items.size()) {
                return 2;
            }
            return 1;
        }

        public void doNewToolbar() {
            if (mToolbar != null) {
                mToolbar.setVisibility(View.GONE);
            }

            mToolbar = (Toolbar) findViewById(R.id.toolbar2);

            // Check if mToolbar is null after findViewById
            if (mToolbar == null) {
                // Handle the null case - show error message and return
                Toast.makeText(ReorderSubreddits.this, "Failed to find toolbar", Toast.LENGTH_SHORT).show();
                return;
            }

            // Now it's safe to use mToolbar
            mToolbar.setTitle(getResources().getQuantityString(R.plurals.reorder_selected, chosen.size(), chosen.size()));

            View deleteButton = mToolbar.findViewById(R.id.delete);
            if (deleteButton != null) {
                deleteButton.setOnClickListener(
                        new View.OnClickListener() {
                            @Override
                            public void onClick(View v) {
                                showRemoveSubredditsDialog();
                            }
                        });
            }

            View topButton = mToolbar.findViewById(R.id.top);
            if (topButton != null) {
                topButton.setOnClickListener(
                        new View.OnClickListener() {
                            @Override
                            public void onClick(View v) {
                                for (String s : chosen) {
                                    subs.remove(s);
                                    subs.add(0, s);
                                }

                                isMultiple = false;
                                doOldToolbar();
                                chosen = new ArrayList<>();
                                notifyDataSetChanged();
                                recyclerView.smoothScrollToPosition(0);
                            }
                        });
            }

            View pinButton = mToolbar.findViewById(R.id.pin);
            if (pinButton != null) {
                pinButton.setOnClickListener(
                        new View.OnClickListener() {
                            @Override
                            public void onClick(View v) {
                                List<String> pinned = UserSubscriptions.getPinned();
                                boolean contained = pinned.containsAll(chosen);

                                for (String s : chosen) {
                                    if (contained) {
                                        UserSubscriptions.removePinned(
                                                s, ReorderSubreddits.this);
                                    } else {
                                        UserSubscriptions.addPinned(s, ReorderSubreddits.this);
                                        subs.remove(s);
                                        subs.add(0, s);
                                    }
                                }

                                isMultiple = false;
                                doOldToolbar();
                                chosen = new ArrayList<>();
                                notifyDataSetChanged();
                                recyclerView.smoothScrollToPosition(0);
                            }
                        });
            }
        }

        int[] textColorAttr = new int[] {R.attr.fontColor};
        TypedArray ta = obtainStyledAttributes(textColorAttr);
        int textColor = ta.getColor(0, Color.BLACK);

        public void updateToolbar() {
            if (mToolbar != null) {
                mToolbar.setTitle(getResources().getQuantityString(R.plurals.reorder_selected, chosen.size(), chosen.size()));
            }
        }

        @Override
        public void onBindViewHolder(final RecyclerView.ViewHolder holderB, final int position) {
            if (holderB instanceof ViewHolder) {
                final ViewHolder holder = (ViewHolder) holderB;
                final String origPos = items.get(position);
                holder.text.setText(origPos);

                if (chosen.contains(origPos)) {
                    holder.itemView.setBackgroundColor(Palette.getDarkerColor(holder.text.getCurrentTextColor()));
                    holder.text.setTextColor(Color.WHITE);
                } else {
                    holder.itemView.setBackgroundColor(Color.TRANSPARENT);
                    holder.text.setTextColor(textColor);
                }
                if (!isSingle(origPos) || !Authentication.isLoggedIn) {
                    holder.check.setVisibility(View.GONE);
                } else {
                    holder.check.setVisibility(View.VISIBLE);
                }
                holder.check.setOnCheckedChangeListener(
                        new CompoundButton.OnCheckedChangeListener() {
                            @Override
                            public void onCheckedChanged(
                                    CompoundButton buttonView, boolean isChecked) {
                                // do nothing
                            }
                        });
                holder.check.setChecked(
                        isSubscribed.containsKey(origPos.toLowerCase(Locale.ENGLISH))
                                && isSubscribed.get(origPos.toLowerCase(Locale.ENGLISH)));
                holder.check.setOnCheckedChangeListener(
                        new CompoundButton.OnCheckedChangeListener() {
                            @Override
                            public void onCheckedChanged(
                                    CompoundButton buttonView, boolean isChecked) {
                                if (!isChecked) {
                                    new UserSubscriptions.UnsubscribeTask().execute(origPos);
                                    Snackbar.make(mToolbar, getString(R.string.reorder_unsubscribed_toast, origPos), Snackbar.LENGTH_SHORT).show();
                                } else {
                                    new UserSubscriptions.SubscribeTask(ReorderSubreddits.this).execute(origPos);
                                    Snackbar.make(mToolbar, getString(R.string.reorder_subscribed_toast, origPos), Snackbar.LENGTH_SHORT).show();
                                }
                                isSubscribed.put(origPos.toLowerCase(Locale.ENGLISH), isChecked);
                            }
                        });
                final View colorView = holder.itemView.findViewById(R.id.color);
                colorView.setBackgroundResource(R.drawable.circle);
                BlendModeUtil.tintDrawableAsModulate(colorView.getBackground(), Palette.getColor(origPos));
                if (UserSubscriptions.getPinned().contains(origPos)) {
                    holder.itemView.findViewById(R.id.pinned).setVisibility(View.VISIBLE);
                } else {
                    holder.itemView.findViewById(R.id.pinned).setVisibility(View.GONE);
                }
                holder.itemView.setOnLongClickListener(
                        new View.OnLongClickListener() {
                            @Override
                            public boolean onLongClick(View v) {
                                if (!isMultiple) {
                                    isMultiple = true;
                                    chosen = new ArrayList<>();
                                    chosen.add(origPos);

                                    doNewToolbar();
                                    holder.itemView.setBackgroundColor(Palette.getDarkerColor(Palette.getDefaultAccent()));
                                    holder.text.setTextColor(Color.WHITE);
                                } else if (chosen.contains(origPos)) {
                                    holder.itemView.setBackgroundColor(Color.TRANSPARENT);

                                    // set the color of the text back to what it should be
                                    holder.text.setTextColor(textColor);

                                    chosen.remove(origPos);

                                    if (chosen.isEmpty()) {
                                        isMultiple = false;
                                        doOldToolbar();
                                    }
                                } else {
                                    chosen.add(origPos);
                                    holder.itemView.setBackgroundColor(Palette.getDarkerColor(Palette.getDefaultAccent()));
                                    holder.text.setTextColor(textColor);
                                    updateToolbar();
                                }
                                return true;
                            }
                        });

                holder.itemView.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        if (!isMultiple) {
                            // Get the current position safely before showing dialog
                            final int currentPos = holder.getBindingAdapterPosition();
                            if (currentPos == RecyclerView.NO_POSITION || currentPos >= items.size()) {
                                return;
                            }

                            showSubredditActionsDialog(currentPos, origPos, items);
                        } else {
                            if (chosen.contains(origPos)) {
                                holder.itemView.setBackgroundColor(Color.TRANSPARENT);

                                // set the color of the text back to what it should be
                                int[] textColorAttr = new int[] {R.attr.fontColor};
                                TypedArray ta = obtainStyledAttributes(textColorAttr);
                                holder.text.setTextColor(ta.getColor(0, Color.BLACK));
                                ta.recycle();

                                chosen.remove(origPos);
                                updateToolbar();

                                if (chosen.isEmpty()) {
                                    isMultiple = false;
                                    doOldToolbar();
                                }
                            } else {
                                chosen.add(origPos);
                                holder.itemView.setBackgroundColor(Palette.getDarkerColor(Palette.getDefaultAccent()));
                                holder.text.setTextColor(Color.WHITE);
                                updateToolbar();
                            }
                        }
                    }
                });
            }
        }

        @Override
        public int getItemCount() {
            return items.size() + 1;
        }

        public class ViewHolder extends RecyclerView.ViewHolder {
            final TextView text;
            final AppCompatCheckBox check;

            public ViewHolder(View itemView) {
                super(itemView);
                text = itemView.findViewById(R.id.name);
                check = itemView.findViewById(R.id.isSubscribed);
            }
        }

        public class SpacerViewHolder extends RecyclerView.ViewHolder {
            public SpacerViewHolder(View itemView) {
                super(itemView);
                itemView.findViewById(R.id.height)
                    .setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, DisplayUtil.dpToPxVertical(88)));
            }
        }

        // New method to show the remove subreddits dialog
        private void showRemoveSubredditsDialog() {
            MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(ReorderSubreddits.this)
                    .setTitle(R.string.reorder_remove_title)
                    .setPositiveButton(
                            R.string.btn_remove,
                            (dialogInterface, which) -> {
                                for (String s : chosen) {
                                    int index = subs.indexOf(s);
                                    subs.remove(index);
                                    adapter.notifyItemRemoved(index);
                                }
                                isMultiple = false;
                                chosen = new ArrayList<>();
                                doOldToolbar();
                            })
                    .setNegativeButton(R.string.btn_cancel, null);

            // Only add the neutral button if the condition is true
            if (Authentication.isLoggedIn && Authentication.didOnline && isSingle(chosen)) {
                builder.setNeutralButton(
                        R.string.reorder_remove_unsubscribe,
                        (dialogInterface, which) -> {
                            for (String s : chosen) {
                                int index = subs.indexOf(s);
                                subs.remove(index);
                                adapter.notifyItemRemoved(index);
                            }

                            new UserSubscriptions.UnsubscribeTask()
                                    .execute(chosen.toArray(new String[0]));

                            for (String s : chosen) {
                                isSubscribed.put(s.toLowerCase(Locale.ENGLISH), false);
                            }

                            isMultiple = false;
                            chosen = new ArrayList<>();
                            doOldToolbar();
                        });
            }

            AlertDialog dialog = builder.create();

            // Apply custom border
            DialogUtil.applyCustomBorderToAlertDialog(ReorderSubreddits.this, dialog);
            dialog.show();
        }
    }

    /**
     * Check if all of the subreddits are single
     *
     * @param subreddits list of subreddits to check
     * @return if all of the subreddits are single
     * @see #isSingle(java.lang.String)
     */
    private boolean isSingle(List<String> subreddits) {
        for (String subreddit : subreddits) {
            if (!isSingle(subreddit)) return false;
        }
        return true;
    }

    /**
     * If the subreddit isn't special, combined, or a multireddit - can attempt to be subscribed to
     *
     * @param subreddit name of a subreddit
     * @return if the subreddit is single
     */
    private boolean isSingle(String subreddit) {
        return !(isSpecial(subreddit) || subreddit.contains("+") || subreddit.contains(".") || subreddit.contains(MULTI_REDDIT));
    }

    /**
     * Subreddits with important behaviour - frontpage, all, random, etc.
     *
     * @param subreddit name of a subreddit
     * @return if the subreddit is special
     */
    private boolean isSpecial(String subreddit) {
        for (String specialSubreddit : UserSubscriptions.specialSubreddits) {
            if (subreddit.equalsIgnoreCase(specialSubreddit)) return true;
        }
        return false;
    }

    /**
     * Shows a dialog for importing existing multireddits
     */
    private void showImportMultiredditDialog() {
        final String[] multis = new String[UserSubscriptions.multireddits.size()];
        int i = 0;
        for (MultiReddit m : UserSubscriptions.multireddits) {
            multis[i] = m.getDisplayName();
            i++;
        }

        // Create dialog with single choice items
        AlertDialog dialog = new MaterialAlertDialogBuilder(ReorderSubreddits.this)
                .setTitle(R.string.reorder_subreddits_title)
                .setSingleChoiceItems(multis, -1, (dialogInterface, which) -> {
                    // Handle selection
                    String name = multis[which];
                    MultiReddit r = UserSubscriptions.getMultiredditByDisplayName(name);

                    // Construct the new URL format for multireddits
                    String username = Authentication.name;
                    String multiName = r.getDisplayName();

                    // Create the new URL formats for multireddits
                    String url = String.format("api/user/%s/m/%s", username, multiName);

                    int pos = addSubAlphabetically(MULTI_REDDIT + r.getDisplayName());
                    UserSubscriptions.setSubNameToProperties(MULTI_REDDIT + r.getDisplayName(), url);
                    adapter.notifyDataSetChanged();
                    recyclerView.smoothScrollToPosition(pos);

                    // Dismiss dialog after selection
                    dialogInterface.dismiss();
                })
                .setPositiveButton(R.string.btn_cancel, null) // Acts as cancel button
                .create();

        // Apply custom border
        DialogUtil.applyCustomBorderToAlertDialog(ReorderSubreddits.this, dialog);

        dialog.show();
    }

    /**
     * Adds a domain URL to the subscription list
     * @param url The domain URL to add
     */
    private void addDomainUrl(String url) {
        try {
            List<String> sortedSubs = UserSubscriptions.sortNoExtras(subs);

            if (sortedSubs.equals(subs)) {
                subs.add(url);
                subs = UserSubscriptions.sortNoExtras(subs);
                adapter = new CustomAdapter(subs);
                recyclerView.setAdapter(adapter);
            } else {
                int pos = addSubAlphabetically(url);
                adapter.notifyDataSetChanged();
                recyclerView.smoothScrollToPosition(pos);
            }
        } catch (Exception e) {
            e.printStackTrace();
            AlertDialog urlErrorDialog = new MaterialAlertDialogBuilder(ReorderSubreddits.this)
                    .setTitle(R.string.reorder_url_err)
                    .setMessage(R.string.misc_please_try_again)
                    .setPositiveButton(R.string.btn_ok, null)
                    .create();

            // Apply custom border
            DialogUtil.applyCustomBorderToAlertDialog(ReorderSubreddits.this, urlErrorDialog);
            urlErrorDialog.show();
        }
    }

    /**
     * Shows a dialog with actions that can be performed on a subreddit
     * @param position The position of the subreddit in the adapter
     * @param subredditName The name of the subreddit
     */
    private void showSubredditActionsDialog(final int position, final String subredditName, final ArrayList<String> items) {
        // Create list of items
        CharSequence[] options = new CharSequence[] {
            getString(R.string.reorder_move),
            UserSubscriptions.getPinned().contains(subredditName) ? "Unpin" : "Pin",
            getString(R.string.btn_delete)
        };

        // Create dialog with MaterialAlertDialogBuilder
        AlertDialog dialog = new MaterialAlertDialogBuilder(ReorderSubreddits.this)
                .setItems(options, (dialogInterface, which) -> {
                    if (which == 2) {
                        // Delete action
                        MaterialAlertDialogBuilder confirmBuilder = new MaterialAlertDialogBuilder(ReorderSubreddits.this)
                            .setTitle(R.string.reorder_remove_title)
                            .setPositiveButton(R.string.btn_remove, (dialog1, which1) -> {
                                subs.remove(items.get(position));
                                adapter.notifyItemRemoved(position);
                            })
                            .setNegativeButton(R.string.btn_cancel, null);

                        // Only add the neutral button if the condition is true
                        if (Authentication.isLoggedIn && Authentication.didOnline && isSingle(subredditName)) {
                            confirmBuilder.setNeutralButton(
                                    R.string.reorder_remove_unsubscribe,
                                    (dialog12, which12) -> {
                                        final String sub = items.get(position);
                                        subs.remove(sub);
                                        adapter.notifyItemRemoved(position);
                                        new UserSubscriptions.UnsubscribeTask().execute(sub);
                                        isSubscribed.put(sub.toLowerCase(Locale.ENGLISH), false);
                                    });
                        }

                        AlertDialog confirmDialog = confirmBuilder.create();

                        // Apply custom border using the utility class
                        DialogUtil.applyCustomBorderToAlertDialog(ReorderSubreddits.this, confirmDialog);
                        confirmDialog.show();

                    } else if (which == 0) {
                        // Move to top
                        String s = items.get(position);
                        subs.remove(s);
                        subs.add(0, s);
                        adapter.notifyItemMoved(position, 0);
                        recyclerView.smoothScrollToPosition(0);
                    } else if (which == 1) {
                        // Pin/unpin
                        String s = items.get(position);
                        if (!UserSubscriptions.getPinned().contains(s)) {
                            UserSubscriptions.addPinned(s, ReorderSubreddits.this);
                            subs.remove(s);
                            subs.add(0, s);
                            adapter.notifyItemMoved(position, 0);
                            recyclerView.smoothScrollToPosition(0);
                        } else {
                            UserSubscriptions.removePinned(s, ReorderSubreddits.this);
                            adapter.notifyItemChanged(position);
                        }
                    }
                })
                .create();

        // Apply custom border using the utility class
        DialogUtil.applyCustomBorderToAlertDialog(ReorderSubreddits.this, dialog);
        dialog.show();
    }
}
