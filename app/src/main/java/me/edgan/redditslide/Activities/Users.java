package me.edgan.redditslide.Activities;

import android.os.AsyncTask;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.util.ArrayList;

import me.edgan.redditslide.Adapters.UsersAdapter;
import me.edgan.redditslide.Authentication;
import me.edgan.redditslide.R;
import me.edgan.redditslide.SavedUsers;
import me.edgan.redditslide.util.DialogUtil;
import me.edgan.redditslide.util.EditTextValidator;
import me.edgan.redditslide.util.MaterialInputDialog;
import me.edgan.redditslide.util.MaterialProgressDialog;
import me.edgan.redditslide.util.MiscUtil;
import me.edgan.redditslide.util.NetworkUtil;

import net.dean.jraw.http.NetworkException;

import org.jspecify.annotations.NullMarked;

/**
 * The locally saved list of redditors, opened from the "Users" drawer item.
 *
 * <p>A name is checked against reddit before it is saved, so a typo cannot be stored as a user that
 * will never load. The test is existence, not reachability: an account whose content this account
 * cannot see (blocked, suspended) still resolves, so it stays addable.
 */
@NullMarked
public class Users extends BaseActivityAnim {

    @SuppressWarnings("NullAway.Init") // built in onCreate
    private ArrayList<String> users;

    @SuppressWarnings("NullAway.Init") // built in onCreate
    private UsersAdapter adapter;

    @SuppressWarnings("NullAway.Init") // assigned in onCreate
    private RecyclerView recycler;

    @SuppressWarnings("NullAway.Init") // assigned in onCreate
    private TextView empty;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        applyColorTheme();
        setContentView(R.layout.activity_users);

        MiscUtil.setupOldSwipeModeBackground(this, getWindow().getDecorView());

        setupAppBar(R.id.toolbar, R.string.title_users, true, true);

        empty = (TextView) requireViewById(R.id.empty);
        recycler = (RecyclerView) requireViewById(R.id.userslist);
        recycler.setLayoutManager(new LinearLayoutManager(this));

        users = SavedUsers.getUsers();
        adapter = new UsersAdapter(this, users, username -> {
            Toast.makeText(
                            Users.this,
                            getString(R.string.users_removed, username),
                            Toast.LENGTH_SHORT)
                    .show();
            updateEmptyState();
        });
        recycler.setAdapter(adapter);
        updateEmptyState();

        final FloatingActionButton fab =
                (FloatingActionButton) requireViewById(R.id.post_floating_action_button);
        fab.setContentDescription(getString(R.string.btn_fab_add_user));
        fab.setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        showAddUserDialog();
                    }
                });

        recycler.addOnScrollListener(
                new RecyclerView.OnScrollListener() {
                    @Override
                    public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
                        if (dy <= 0) {
                            fab.show();
                        } else {
                            fab.hide();
                        }
                    }
                });
        fab.show();
    }

    @Override
    public void onResume() {
        super.onResume();
        // A profile opened from here can add or remove itself through its info dialog.
        reloadUsers();
    }

    private void showAddUserDialog() {
        final MaterialInputDialog addDialog =
                new MaterialInputDialog.Builder(this)
                        .title(R.string.users_btn_add)
                        .inputRange(3, 20)
                        .input(getString(R.string.user_enter), null, null)
                        .positiveText(R.string.btn_add)
                        .onPositive(
                                dialog -> {
                                    final String username =
                                            dialog.getInputEditText().getText().toString().trim();

                                    if (username.isEmpty()) {
                                        return;
                                    }

                                    if (SavedUsers.contains(username)) {
                                        Toast.makeText(
                                                        Users.this,
                                                        getString(
                                                                R.string.users_already_added,
                                                                username),
                                                        Toast.LENGTH_SHORT)
                                                .show();
                                        return;
                                    }

                                    verifyThenAdd(username);
                                })
                        .negativeText(R.string.btn_cancel)
                        .build();

        // Restrict the field to the characters reddit allows in a username, once, before the
        // dialog is shown. Doing this from the input callback instead would re-run setFilters on
        // every keystroke, and replacing the filters mid-word resets the IME's composing region,
        // which is what stops Gboard suggestions from being applied to the field.
        EditTextValidator.validateUsername(addDialog.getInputEditText());
        addDialog.getDialog().show();
    }

    /**
     * Checks the name against reddit before saving it, so a typo like "SahaniSignh" cannot be
     * stored as a user that will never load.
     *
     * <p>The test is existence, not reachability: a real account whose content this account cannot
     * see (blocked, suspended) still resolves through {@code getUser}, so it stays addable — only
     * names reddit does not know are refused.
     */
    private void verifyThenAdd(final String username) {
        if (!EditTextValidator.isValidUsername(username)) {
            showAddError(getString(R.string.users_invalid, username));
            return;
        }

        // Cancelable so a slow lookup cannot trap the user behind a modal: reddit is reached
        // through JRAW's OkHttp client, which Slide never gives a timeout override, so a stalled
        // connection sits here for the OkHttp default before it gives up.
        final MaterialProgressDialog checking =
                new MaterialProgressDialog.Builder(this)
                        .title(R.string.users_checking)
                        .progress(true, 0)
                        .cancelable(true)
                        .canceledOnTouchOutside(false)
                        .show();

        new AsyncTask<Void, Void, Boolean>() {
            @Override
            protected @Nullable Boolean doInBackground(Void... params) {
                // null means "could not check", which is not the same as "does not exist" and
                // must not silently drop the name.
                if (Authentication.reddit == null || !NetworkUtil.isConnected(Users.this)) {
                    return null;
                }

                try {
                    return Authentication.reddit.getUser(username) != null;
                } catch (NetworkException e) {
                    // Only reddit answering "no such account" proves absence. Any other status
                    // (rate limit, 5xx) says nothing about the name.
                    return e.getResponse().getStatusCode() == 404 ? Boolean.FALSE : null;
                } catch (Exception e) {
                    // Socket timeout, DNS failure, parse error: cannot tell, so do not claim the
                    // name is invalid. NetworkUtil.isConnected above only reports forced-offline
                    // mode, so a genuinely dropped connection arrives here.
                    return null;
                }
            }

            @Override
            protected void onPostExecute(@Nullable Boolean exists) {
                // Read before dismissing: the dialog is gone only if the user backed out of the
                // check (or the window died), and that means abandon the add rather than report
                // a result nobody is waiting for.
                final boolean abandoned = !checking.isShowing();

                try {
                    checking.dismiss();
                } catch (IllegalArgumentException ignored) {
                    // Activity already gone.
                }

                if (abandoned) {
                    return;
                }

                if (exists == null) {
                    showAddError(getString(R.string.users_check_failed, username));
                    return;
                }

                if (!exists) {
                    showAddError(getString(R.string.users_invalid, username));
                    return;
                }

                if (SavedUsers.addUser(username)) {
                    reloadUsers();
                    Toast.makeText(
                                    Users.this,
                                    getString(R.string.users_added, username),
                                    Toast.LENGTH_SHORT)
                            .show();
                } else {
                    Toast.makeText(
                                    Users.this,
                                    getString(R.string.users_already_added, username),
                                    Toast.LENGTH_SHORT)
                            .show();
                }
            }
        }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }

    /** Reports why a name was refused, and offers to retype it. */
    private void showAddError(String message) {
        // The lookup finishes on its own schedule, so this can land after the user has left the
        // screen; showing a dialog on a dead window throws BadTokenException.
        if (isFinishing() || isDestroyed()) {
            return;
        }

        DialogUtil.showWithCardBackground(
                new AlertDialog.Builder(this)
                        .setTitle(R.string.users_invalid_title)
                        .setMessage(message)
                        .setPositiveButton(R.string.btn_ok, null)
                        .setNegativeButton(
                                R.string.users_btn_add, (d, which) -> showAddUserDialog()));
    }

    /** Re-reads the store and rebinds the list in place. */
    private void reloadUsers() {
        users.clear();
        users.addAll(SavedUsers.getUsers());
        adapter.notifyDataSetChanged();
        updateEmptyState();
    }

    private void updateEmptyState() {
        empty.setVisibility(users.isEmpty() ? View.VISIBLE : View.GONE);
    }
}
