/*
 * Copyright (C) 2013 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package me.edgan.redditslide.Activities;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import me.edgan.redditslide.Authentication;
import me.edgan.redditslide.Megareddits;
import me.edgan.redditslide.R;
import me.edgan.redditslide.UserSubscriptions;
import me.edgan.redditslide.Visuals.Palette;
import me.edgan.redditslide.util.BlendModeUtil;
import me.edgan.redditslide.util.DialogUtil;
import me.edgan.redditslide.util.LogUtil;
import me.edgan.redditslide.util.MaterialInputDialog;
import me.edgan.redditslide.util.MaterialProgressDialog;
import me.edgan.redditslide.util.MiscUtil;
import net.dean.jraw.ApiException;
import net.dean.jraw.http.MultiRedditUpdateRequest;
import net.dean.jraw.http.NetworkException;
import net.dean.jraw.managers.MultiRedditManager;
import net.dean.jraw.models.MultiReddit;
import net.dean.jraw.models.MultiSubreddit;
import net.dean.jraw.models.Subreddit;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/** This class handles creation of Multireddits. */
@NullMarked
public class CreateMulti extends BaseActivityAnim {

    private ArrayList<String> subs;
    private CustomAdapter adapter;
    private EditText title;
    private RecyclerView recyclerView;
    private String input = "";
    // The name (the path Reddit edits by) of the multi being edited; null when creating one.
    @Nullable private String old;
    // Its display name, which is what the main subreddit list and MultiredditOverview key it by.
    @Nullable private String oldDisplayName;
    // What the name field and the list started as: a rename is a change to the first, and Back
    // only offers to save when either has changed.
    private String originalTitle = "";
    private ArrayList<String> originalSubs = new ArrayList<>();
    public static final String EXTRA_MULTI = "multi";

    // Shows a dialog with all Subscribed subreddits and allows the user to select which ones to
    // include in the Multireddit
    // Holes: showSelectDialog's first loop skips special subreddits, so the entries it does not
    // write stay null until the toArray call further down that method compacts them out.
    private @Nullable String[] all = new String[0];

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        overrideSwipeFromAnywhere();

        super.onCreate(savedInstanceState);
        getOnBackPressedDispatcher().addCallback(this, mBackCallback);
        applyColorTheme();
        setContentView(R.layout.activity_createmulti);

        MiscUtil.setupOldSwipeModeBackground(this, getWindow().getDecorView());

        setupAppBar(R.id.toolbar, "", true, true);

        findViewById(R.id.add)
                .setOnClickListener(
                        new View.OnClickListener() {
                            @Override
                            public void onClick(View v) {
                                showSelectDialog();
                            }
                        });
        title = (EditText) findViewById(R.id.name);

        subs = new ArrayList<>();
        final String multi = getIntent().getStringExtra(EXTRA_MULTI);
        if (multi != null) {
            old = multi;
            title.setText(multi);
            originalTitle = title.getText().toString();
            UserSubscriptions.getMultireddits(
                    new UserSubscriptions.MultiCallback() {
                        @Override
                        public void onComplete(@Nullable List<MultiReddit> multis) {
                            // Null when the fetch failed.
                            if (multis == null) {
                                return;
                            }
                            for (MultiReddit multiReddit : multis) {
                                if (multi.equalsIgnoreCase(multiReddit.getFullName())) {
                                    oldDisplayName = multiReddit.getDisplayName();
                                    for (MultiSubreddit sub : multiReddit.getSubreddits()) {
                                        final String name = sub.getDisplayName();
                                        if (name != null) {
                                            subs.add(name.toLowerCase(Locale.ENGLISH));
                                        }
                                    }
                                }
                            }
                            sortSubs();
                            originalSubs = new ArrayList<>(subs);
                            // The adapter was handed this list while it was still empty.
                            adapter.notifyDataSetChanged();
                        }
                    });
        }
        recyclerView = (RecyclerView) findViewById(R.id.subslist);

        adapter = new CustomAdapter(subs);
        //  adapter.setHasStableIds(true);

        recyclerView.setAdapter(adapter);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
    }

    // Intentionally intercepts Back to show a save/discard prompt instead of finishing
    private final OnBackPressedCallback mBackCallback =
            new OnBackPressedCallback(true) {
                @Override
                public void handleOnBackPressed() {
                    if (title.getText().toString().equals(originalTitle)
                            && subs.equals(originalSubs)) {
                        finish();
                        return;
                    }
                    // Yes leaves the overview alone: SaveMulti replaces it once the save has
                    // gone through, and finishing it here lost it to a save that then failed.
                    AlertDialog dialog =
                            new AlertDialog.Builder(CreateMulti.this)
                                    .setTitle(R.string.general_confirm_exit)
                                    .setMessage(R.string.multi_save_option)
                                    .setPositiveButton(R.string.btn_yes, (d, i) -> save())
                                    .setNegativeButton(R.string.btn_no, (d, i) -> finish())
                                    .create();
                    DialogUtil.matchDialogToCardBackground(CreateMulti.this, dialog);
                    dialog.show();
                }
            };

    public void showSelectDialog() {
        // List of all subreddits of the multi
        List<String> multiSubs = new ArrayList<>(subs);
        List<String> sorted = new ArrayList<>(subs);

        // Add all user subs that aren't already on the list
        for (String s : UserSubscriptions.sort(UserSubscriptions.getSubscriptions(this))) {
            if (!sorted.contains(s)) sorted.add(s);
        }

        // Array of all subs
        all = new String[sorted.size()];
        // Contains which subreddits are checked
        boolean[] checked = new boolean[all.length];

        // Remove special subreddits from list and store it in "all"
        int i = 0;
        for (String s : sorted) {
            if (!s.equals("all")
                    && !s.equals("frontpage")
                    && !s.contains("+")
                    && !s.contains(".")
                    && !s.contains("/m/")
                    && !Megareddits.isKey(s)) {
                all[i] = s;
                i++;
            }
        }

        // Remove empty entries & store which subreddits are checked
        List<String> list = new ArrayList<>();
        i = 0;
        for (String s : all) {
            if (s != null && !s.isEmpty()) {
                list.add(s);
                if (multiSubs.contains(s)) {
                    checked[i] = true;
                }
                i++;
            }
        }

        // Convert List back to Array
        all = list.toArray(new String[0]);

        final ArrayList<String> toCheck = new ArrayList<>(subs);
        DialogUtil.showWithCardBackground(new AlertDialog.Builder(this)
                .setMultiChoiceItems(
                        all,
                        checked,
                        (dialog, which, isChecked) -> {
                            if (!isChecked) {
                                toCheck.remove(all[which]);
                            } else {
                                toCheck.add(all[which]);
                            }
                            Log.v(LogUtil.getTag(), "Done with " + all[which]);
                        })
                .setTitle(R.string.multireddit_selector)
                .setPositiveButton(
                        getString(R.string.btn_add).toUpperCase(Locale.getDefault()),
                        (dialog, which) -> {
                            subs = toCheck;
                            sortSubs();
                            adapter = new CustomAdapter(subs);
                            recyclerView.setAdapter(adapter);
                        })
                .setNegativeButton(
                        R.string.reorder_add_subreddit,
                        (dialog, which) ->
                                new MaterialInputDialog.Builder(CreateMulti.this)
                                        .title(R.string.reorder_add_subreddit)
                                        .inputRange(2, 21)
                                        .input(
                                                getString(R.string.reorder_subreddit_name),
                                                null,
                                                (inputDialog, raw) ->
                                                        input =
                                                                raw.toString()
                                                                        .replaceAll("\\s", ""))
                                        .positiveText(R.string.btn_add)
                                        .onPositive(
                                                inputDialog ->
                                                        new AsyncGetSubreddit().execute(input))
                                        .negativeText(R.string.btn_cancel)
                                        .show())
                );
    }

    private class AsyncGetSubreddit extends AsyncTask<String, Void, Subreddit> {
        @Override
        public void onPostExecute(@Nullable Subreddit subreddit) {
            if (subreddit != null
                    || input.equalsIgnoreCase("friends")
                    || input.equalsIgnoreCase("mod")) {
                subs.add(input);
                sortSubs();
                adapter.notifyDataSetChanged();
                recyclerView.smoothScrollToPosition(subs.indexOf(input));
            }
        }

        @Override
        protected @Nullable Subreddit doInBackground(final String... params) {
            try {
                if (subs.contains(params[0])) return null;
                return Objects.requireNonNull(Authentication.reddit).getSubreddit(params[0]);
            } catch (Exception e) {
                runOnUiThread(
                        new Runnable() {
                            @Override
                            public void run() {
                                try {
                                    DialogUtil.showWithCardBackground(new AlertDialog.Builder(CreateMulti.this)
                                            .setTitle(R.string.subreddit_err)
                                            .setMessage(
                                                    getString(
                                                            R.string.subreddit_err_msg, params[0]))
                                            .setPositiveButton(
                                                    R.string.btn_ok,
                                                    (dialog, which) -> dialog.dismiss())
                                            .setOnDismissListener(null)
                                            );
                                } catch (Exception ignored) {
                                    // Error dialog on a host the user has already left.
                                }
                            }
                        });

                return null;
            }
        }
    }

    /** Responsible for showing a list of subreddits which are added to this Multireddit */
    public class CustomAdapter extends RecyclerView.Adapter<CustomAdapter.ViewHolder> {
        private final ArrayList<String> items;

        public CustomAdapter(ArrayList<String> items) {
            this.items = items;
        }

        @Override
        public CustomAdapter.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View v =
                    LayoutInflater.from(parent.getContext())
                            .inflate(R.layout.subforsublistremove, parent, false);
            return new ViewHolder(v);
        }

        @Override
        public void onBindViewHolder(final ViewHolder holder, int position) {

            final String origPos = items.get(position);
            holder.text.setText(origPos);

            final View colorView = holder.itemView.findViewById(R.id.color);
            colorView.setBackgroundResource(R.drawable.circle);
            BlendModeUtil.tintDrawableAsModulate(
                    colorView.getBackground(), Palette.getColor(origPos));

            holder.itemView.setOnClickListener(
                    new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            Intent inte = new Intent(CreateMulti.this, SubredditView.class);
                            inte.putExtra(SubredditView.EXTRA_SUBREDDIT, origPos);
                            startActivity(inte);
                        }
                    });
            holder.remove.setOnClickListener(
                    new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            DialogUtil.showWithCardBackground(new AlertDialog.Builder(CreateMulti.this)
                                    .setTitle(R.string.really_remove_subreddit_title)
                                    .setPositiveButton(
                                            R.string.btn_yes,
                                            (dialog, which) -> {
                                                subs.remove(origPos);
                                                adapter = new CustomAdapter(subs);
                                                recyclerView.setAdapter(adapter);
                                            })
                                    .setNegativeButton(R.string.btn_no, null)
                                    );
                        }
                    });
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        public class ViewHolder extends RecyclerView.ViewHolder {
            final TextView text;
            final View remove;

            public ViewHolder(View itemView) {
                super(itemView);

                text = itemView.findViewById(R.id.name);
                remove = itemView.findViewById(R.id.remove);
            }
        }
    }

    /** Saves a Multireddit with applicable data in an async task */
    public class SaveMulti extends AsyncTask<Void, Void, Void> {
        // Snapshot of the title field, read on the UI thread; doInBackground()
        // runs on a worker thread and must not touch Views directly.
        @SuppressWarnings("NullAway.Init") // assigned in onPreExecute, before doInBackground runs
        private String titleText;

        @Override
        protected void onPreExecute() {
            titleText = title.getText().toString();
        }

        @Override
        protected Void doInBackground(Void... params) {
            try {
                String multiName = titleText.replace(" ", "").replace("-", "_");
                final String editing = old;
                final boolean renamed = editing != null && !titleText.equals(originalTitle);

                // Only a new name has to pass: an edit that keeps the name saves under the one
                // the multi already has, which can predate these rules.
                if (editing == null || renamed) {
                    Pattern validName = Pattern.compile("^[A-Za-z0-9][A-Za-z0-9_]{2,20}$");
                    Matcher m = validName.matcher(multiName);

                    if (!m.matches()) {
                        Log.v(LogUtil.getTag(), "Invalid multi name");
                        throw new IllegalArgumentException(multiName);
                    }
                }
                // Reddit's paths are case-insensitive, so a rename that only changes case keeps
                // the path and changes the display name alone.
                final boolean moved =
                        editing != null && renamed && !editing.equalsIgnoreCase(multiName);
                // An edit saves under the multi's own name; a rename moves it afterwards. In that
                // order a failed save leaves the multi where the app still looks for it, where a
                // rename that went through ahead of a rejected save left the main subreddit list
                // and the overview pointing at a path that no longer existed.
                final String saveAs = editing != null ? editing : multiName;
                Log.v(LogUtil.getTag(), "Create or Update, Name: " + saveAs);
                final MultiRedditManager manager = new MultiRedditManager(Authentication.reddit);
                MultiRedditUpdateRequest.Builder request =
                        new MultiRedditUpdateRequest.Builder(Authentication.name, saveAs)
                                .subreddits(subs);
                // Setting the display name to the name, as a multi created here gets, keeps the
                // two in step: the main subreddit list builds a multi's path from its display
                // name.
                if (renamed && !moved) {
                    request.displayName(multiName);
                }
                final MultiReddit saved = manager.createOrUpdate(request.build());
                if (moved) {
                    // Reddit has dropped /api/multi/rename (it now reads "rename" as the name of
                    // a multi and answers 400), so a rename is the copy and delete that endpoint
                    // used to do. The copy is refused with a 409 when the new name is taken,
                    // before anything else changes. It appends "copied from" to the description
                    // and can reset the visibility, so both are put back from the save above.
                    Log.v(LogUtil.getTag(), "Renaming");
                    manager.copy(editing, multiName);
                    manager.createOrUpdate(
                            new MultiRedditUpdateRequest.Builder(Authentication.name, multiName)
                                    .displayName(multiName)
                                    .description(MiscUtil.orEmpty(saved.getDescription()))
                                    .visibility(saved.getVisibility())
                                    .build());
                    manager.delete(editing);
                }
                // The display name MultiredditOverview reopens on.
                final String reopen = editing != null && !renamed ? oldDisplayName : multiName;
                runOnUiThread(
                        new Runnable() {
                            @Override
                            public void run() {
                                Log.v(LogUtil.getTag(), "Update Subreddits");
                                if (renamed && oldDisplayName != null) {
                                    UserSubscriptions.renameMultiInSubscriptions(
                                            oldDisplayName, multiName);
                                }
                                finishOverview();
                                new UserSubscriptions.SyncMultireddits(CreateMulti.this, reopen)
                                        .execute();
                            }
                        });
                runOnUiThread(
                        new Runnable() {
                            @Override
                            public void run() {
                                Context context = getApplicationContext();
                                CharSequence text = getString(R.string.multi_saved_successfully);
                                int duration = Toast.LENGTH_SHORT;
                                Toast toast = Toast.makeText(context, text, duration);
                                toast.show();
                            }
                        });
            } catch (final NetworkException | ApiException e) {
                runOnUiThread(
                        new Runnable() {
                            @Override
                            public void run() {
                                String errorMsg = getString(R.string.misc_err);
                                // Creating correct error message if the multireddit has more than
                                // 100 subs or its name already exists
                                if (e instanceof ApiException) {
                                    errorMsg =
                                            getString(R.string.misc_err)
                                                    + ": "
                                                    + ((ApiException) e).getExplanation()
                                                    + "\n"
                                                    + getString(R.string.misc_retry);

                                } else if (((NetworkException) e).getResponse().getStatusCode()
                                        == 409) {
                                    // The HTTP status code returned when the name of the
                                    // multireddit already exists or
                                    // has more than 100 subs is 409
                                    errorMsg = getString(R.string.multireddit_save_err);
                                }

                                DialogUtil.showWithCardBackground(new AlertDialog.Builder(CreateMulti.this)
                                        .setTitle(R.string.err_title)
                                        .setMessage(errorMsg)
                                        .setNeutralButton(
                                                R.string.btn_ok, (dialogInterface, i) -> finish())
                                        );
                            }
                        });
                LogUtil.e(e, "CreateMulti.run failed");
            } catch (IllegalArgumentException e) {
                runOnUiThread(
                        new Runnable() {
                            @Override
                            public void run() {
                                DialogUtil.showWithCardBackground(new AlertDialog.Builder(CreateMulti.this)
                                        .setTitle(R.string.multireddit_invalid_name)
                                        .setMessage(R.string.multireddit_invalid_name_msg)
                                        .setNeutralButton(
                                                R.string.btn_ok, (dialogInterface, i) -> finish())
                                        );
                            }
                        });
            } catch (RuntimeException e) {
                // Connection failures surface as a bare RuntimeException, not
                // NetworkException/ApiException, so handle them here to avoid crashing.
                runOnUiThread(
                        new Runnable() {
                            @Override
                            public void run() {
                                DialogUtil.showWithCardBackground(new AlertDialog.Builder(CreateMulti.this)
                                        .setTitle(R.string.err_title)
                                        .setMessage(R.string.misc_err)
                                        .setNeutralButton(
                                                R.string.btn_ok, (dialogInterface, i) -> finish())
                                        );
                            }
                        });
                LogUtil.e(e, "CreateMulti.run failed");
            }
            return null;
        }
    }

    /** Keeps the list in alphabetical order, whichever way its entries arrived. */
    private void sortSubs() {
        Collections.sort(subs, String.CASE_INSENSITIVE_ORDER);
    }

    /** Saves the multi, unless its name or subreddit list is empty. Save and Back both use it. */
    private void save() {
        if (title.getText().toString().isEmpty()) {
            DialogUtil.showWithCardBackground(new AlertDialog.Builder(CreateMulti.this)
                    .setTitle(R.string.multireddit_title_empty)
                    .setMessage(R.string.multireddit_title_empty_msg)
                    .setPositiveButton(
                            R.string.btn_ok,
                            (dialog, which) -> {
                                dialog.dismiss();
                                title.requestFocus();
                            })
                    );
        } else if (subs.isEmpty()) {
            DialogUtil.showWithCardBackground(new AlertDialog.Builder(CreateMulti.this)
                    .setTitle(R.string.multireddit_no_subs)
                    .setMessage(R.string.multireddit_no_subs_msg)
                    .setPositiveButton(R.string.btn_ok, (dialog, which) -> dialog.dismiss())
                    );
        } else {
            new SaveMulti().execute();
        }
    }

    /** SyncMultireddits opens a fresh overview, so the one this screen was opened from goes. */
    private static void finishOverview() {
        final Activity overview = MultiredditOverview.multiActivity;
        if (overview != null) {
            overview.finish();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.menu_create_multi, menu);

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int itemId = item.getItemId();
        if (itemId == R.id.delete) {
            DialogUtil.showWithCardBackground(new AlertDialog.Builder(CreateMulti.this)
                        .setTitle(
                                getString(
                                        R.string.delete_multireddit_title,
                                        title.getText().toString()))
                        .setMessage(R.string.cannot_be_undone)
                        .setPositiveButton(
                                R.string.btn_yes,
                                (dialog, which) -> {
                                    finishOverview();
                                    new MaterialProgressDialog.Builder(CreateMulti.this)
                                            .title(R.string.deleting)
                                            .progress(true, 100)
                                            .content(R.string.misc_please_wait)
                                            .cancelable(false)
                                            .show();

                                    new AsyncTask<Void, Void, Void>() {
                                        @Override
                                        protected Void doInBackground(Void... params) {
                                            try {
                                                new MultiRedditManager(Authentication.reddit)
                                                        .delete(old);
                                                runOnUiThread(
                                                        new Runnable() {
                                                            @Override
                                                            public void run() {
                                                                new UserSubscriptions
                                                                                .SyncMultireddits(
                                                                                CreateMulti.this)
                                                                        .execute();
                                                            }
                                                        });

                                            } catch (final Exception e) {
                                                runOnUiThread(
                                                        new Runnable() {
                                                            @Override
                                                            public void run() {
                                                                DialogUtil.showWithCardBackground(new AlertDialog.Builder(
                                                                                CreateMulti.this)
                                                                        .setTitle(
                                                                                R.string.err_title)
                                                                        .setMessage(
                                                                                R.string.misc_err)
                                                                        .setNeutralButton(
                                                                                R.string.btn_ok,
                                                                                (dialogInterface,
                                                                                        i) ->
                                                                                        finish())
                                                                        );
                                                            }
                                                        });
                                                LogUtil.e(e, "CreateMulti.run failed");
                                            }
                                            return null;
                                        }
                                    }.execute();
                                })
                        .setNegativeButton(R.string.btn_cancel, null));
            return true;
        } else if (itemId == R.id.save) {
            save();
            return true;
        } else if (itemId == android.R.id.home) {
            getOnBackPressedDispatcher().onBackPressed();
            return true;
        } else {
            return false;
        }
    }
}
