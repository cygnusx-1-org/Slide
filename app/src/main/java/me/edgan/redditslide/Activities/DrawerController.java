package me.edgan.redditslide.Activities;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.AsyncTask;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.view.ContextThemeWrapper;
import androidx.appcompat.widget.SwitchCompat;
import androidx.appcompat.widget.Toolbar;
import androidx.core.view.GravityCompat;

import com.afollestad.materialdialogs.DialogAction;
import com.afollestad.materialdialogs.MaterialDialog;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputLayout;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import me.edgan.redditslide.Adapters.SideArrayAdapter;
import me.edgan.redditslide.Authentication;
import me.edgan.redditslide.Constants;
import me.edgan.redditslide.Fragments.DrawerItemsDialog;
import me.edgan.redditslide.Fragments.SubmissionsView;
import me.edgan.redditslide.R;
import me.edgan.redditslide.Reddit;
import me.edgan.redditslide.SettingValues;
import me.edgan.redditslide.UserSubscriptions;
import me.edgan.redditslide.Visuals.ColorPreferences;
import me.edgan.redditslide.Visuals.Palette;
import me.edgan.redditslide.ui.settings.ManageOfflineContent;
import me.edgan.redditslide.ui.settings.SettingsActivity;
import me.edgan.redditslide.util.AnimatorUtil;
import me.edgan.redditslide.util.EditTextValidator;
import me.edgan.redditslide.util.KeyboardUtil;
import me.edgan.redditslide.util.LogUtil;
import me.edgan.redditslide.util.NetworkUtil;
import me.edgan.redditslide.util.OnSingleClickListener;
import me.edgan.redditslide.util.stubs.SimpleTextWatcher;

public class DrawerController {

    private final MainActivity mainActivity;
    ListView drawerSubList;
    EditText drawerSearch;
    View hea;
    View accountsArea;
    SideArrayAdapter sideArrayAdapter;
    HashMap<String, String> accounts = new HashMap<>();


    public DrawerController(MainActivity mainActivity) {
        this.mainActivity = mainActivity;
    }

    public void doDrawer() {
        drawerSubList = (ListView) mainActivity.findViewById(R.id.drawerlistview);
        drawerSubList.setDividerHeight(0);
        drawerSubList.setDescendantFocusability(ListView.FOCUS_BEFORE_DESCENDANTS);
        final LayoutInflater inflater = mainActivity.getLayoutInflater();
        final View header;

        if (Authentication.isLoggedIn && Authentication.didOnline) {

            header = inflater.inflate(R.layout.drawer_loggedin, drawerSubList, false);
            mainActivity.headerMain = header;
            hea = header.findViewById(R.id.back);

            drawerSubList.addHeaderView(header, null, false);
            ((TextView) header.findViewById(R.id.name)).setText(Authentication.name);
            header.findViewById(R.id.multi)
                    .setOnClickListener(
                            new OnSingleClickListener() {
                                @Override
                                public void onSingleClick(View view) {
                                    if (mainActivity.runAfterLoad == null) {
                                        Intent inte = new Intent(mainActivity, MultiredditOverview.class);
                                        mainActivity.startActivity(inte);
                                    }
                                }
                            });
            header.findViewById(R.id.multi).setOnLongClickListener(new View.OnLongClickListener() {
                @Override
                public boolean onLongClick(View view) {
                    showUsernameDialog(true);
                    return true;
                }
            });

            header.findViewById(R.id.discover)
                    .setOnClickListener(
                            new OnSingleClickListener() {
                                @Override
                                public void onSingleClick(View view) {
                                    Intent inte = new Intent(mainActivity, Discover.class);
                                    mainActivity.startActivity(inte);
                                }
                            });

            header.findViewById(R.id.prof_click)
                    .setOnClickListener(
                            new OnSingleClickListener() {
                                @Override
                                public void onSingleClick(View view) {
                                    Intent inte = new Intent(mainActivity, Profile.class);
                                    inte.putExtra(Profile.EXTRA_PROFILE, Authentication.name);
                                    mainActivity.startActivity(inte);
                                }
                            });
            header.findViewById(R.id.saved)
                    .setOnClickListener(
                            new OnSingleClickListener() {
                                @Override
                                public void onSingleClick(View view) {
                                    Intent inte = new Intent(mainActivity, Profile.class);
                                    inte.putExtra(Profile.EXTRA_PROFILE, Authentication.name);
                                    inte.putExtra(Profile.EXTRA_SAVED, true);
                                    mainActivity.startActivity(inte);
                                }
                            });
            header.findViewById(R.id.later)
                    .setOnClickListener(
                            new OnSingleClickListener() {
                                @Override
                                public void onSingleClick(View view) {
                                    Intent inte =
                                            new Intent(mainActivity, PostReadLater.class);
                                    mainActivity.startActivity(inte);
                                }
                            });
            header.findViewById(R.id.history)
                    .setOnClickListener(
                            new OnSingleClickListener() {
                                @Override
                                public void onSingleClick(View view) {
                                    Intent inte = new Intent(mainActivity, Profile.class);
                                    inte.putExtra(Profile.EXTRA_PROFILE, Authentication.name);
                                    inte.putExtra(Profile.EXTRA_HISTORY, true);
                                    mainActivity.startActivity(inte);
                                }
                            });
            header.findViewById(R.id.commented)
                    .setOnClickListener(
                            new OnSingleClickListener() {
                                @Override
                                public void onSingleClick(View view) {
                                    Intent inte = new Intent(mainActivity, Profile.class);
                                    inte.putExtra(Profile.EXTRA_PROFILE, Authentication.name);
                                    inte.putExtra(Profile.EXTRA_COMMENT, true);
                                    mainActivity.startActivity(inte);
                                }
                            });
            header.findViewById(R.id.submitted)
                    .setOnClickListener(
                            new OnSingleClickListener() {
                                @Override
                                public void onSingleClick(View view) {
                                    Intent inte = new Intent(mainActivity, Profile.class);
                                    inte.putExtra(Profile.EXTRA_PROFILE, Authentication.name);
                                    inte.putExtra(Profile.EXTRA_SUBMIT, true);
                                    mainActivity.startActivity(inte);
                                }
                            });
            header.findViewById(R.id.upvoted)
                    .setOnClickListener(
                            new OnSingleClickListener() {
                                @Override
                                public void onSingleClick(View view) {
                                    Intent inte = new Intent(mainActivity, Profile.class);
                                    inte.putExtra(Profile.EXTRA_PROFILE, Authentication.name);
                                    inte.putExtra(Profile.EXTRA_UPVOTE, true);
                                    mainActivity.startActivity(inte);
                                }
                            });

            /**
             * If the user is a known mod, show the "Moderation" drawer item quickly to stop the UI
             * from jumping
             */
            header.findViewById(R.id.mod).setVisibility(View.GONE);
            if (Authentication.mod && UserSubscriptions.modOf != null && !UserSubscriptions.modOf.isEmpty()) {
                header.findViewById(R.id.mod).setVisibility(View.VISIBLE);
            }

            // update notification badge
            final LinearLayout profStuff = header.findViewById(R.id.accountsarea);
            profStuff.setVisibility(View.GONE);
            mainActivity.findViewById(R.id.back)
                    .setOnClickListener(
                            new View.OnClickListener() {
                                @Override
                                public void onClick(View v) {
                                    if (profStuff.getVisibility() == View.GONE) {
                                        expand(profStuff);
                                        header.setContentDescription(mainActivity.getResources().getString(R.string.btn_collapse));
                                        AnimatorUtil.flipAnimator(false, header.findViewById(R.id.headerflip)).start();
                                    } else {
                                        collapse(profStuff);
                                        header.setContentDescription(mainActivity.getResources().getString(R.string.btn_expand));
                                        AnimatorUtil.flipAnimator(true, header.findViewById(R.id.headerflip)).start();
                                    }
                                }
                            });

            for (String s : Authentication.authentication.getStringSet("accounts", new HashSet<String>())) {
                if (s.contains(":")) {
                    accounts.put(s.split(":")[0], s.split(":")[1]);
                } else {
                    accounts.put(s, "");
                }
            }
            final ArrayList<String> keys = new ArrayList<>(accounts.keySet());

            final LinearLayout accountList = header.findViewById(R.id.accountsarea);
            for (final String accName : keys) {
                LogUtil.v(accName);
                final View t = mainActivity.getLayoutInflater().inflate(R.layout.account_textview_white, accountList, false);
                ((TextView) t.findViewById(R.id.name)).setText(accName);
                LogUtil.v("Adding click to " + ((TextView) t.findViewById(R.id.name)).getText());
                t.findViewById(R.id.remove)
                        .setOnClickListener(
                                new View.OnClickListener() {
                                    @Override
                                    public void onClick(View v) {
                                        final Context contextThemeWrapper = new ContextThemeWrapper(mainActivity,
                                                new ColorPreferences(mainActivity).getFontStyle().getBaseId());

                                        new MaterialAlertDialogBuilder(contextThemeWrapper)
                                                .setTitle(R.string.profile_remove)
                                                .setMessage(R.string.profile_remove_account)
                                                .setNegativeButton(
                                                        R.string.btn_delete,
                                                        (dialog2, which2) -> {
                                                            Set<String> accounts2 = Authentication.authentication.getStringSet("accounts", new HashSet<>());
                                                            Set<String> done = new HashSet<>();

                                                            for (String s : accounts2) {
                                                                if (!s.contains(accName)) {
                                                                    done.add(s);
                                                                }
                                                            }

                                                            Authentication.authentication
                                                                    .edit()
                                                                    .putStringSet("accounts", done)
                                                                    .commit();
                                                            dialog2.dismiss();
                                                            accountList.removeView(t);
                                                            if (accName.equalsIgnoreCase(Authentication.name)) {
                                                                boolean d = false;

                                                                for (String s : keys) {
                                                                    if (!s.equalsIgnoreCase(
                                                                            accName)) {
                                                                        d = true;
                                                                        LogUtil.v("Switching to "+ s);

                                                                        for (Map.Entry<String, String> e : accounts.entrySet()) {
                                                                            LogUtil.v(e.getKey() + ":" + e.getValue());
                                                                        }

                                                                        if (accounts.containsKey(s) && !accounts.get(s).isEmpty()) {
                                                                            Authentication.authentication
                                                                                .edit()
                                                                                .putString("lasttoken", accounts.get(s))
                                                                                .remove("backedCreds")
                                                                                .commit();
                                                                        } else {
                                                                            ArrayList<String>tokens = new ArrayList<>(Authentication.authentication
                                                                                .getStringSet("tokens", new HashSet<>()));

                                                                            int index = keys.indexOf(s);

                                                                            if (keys.indexOf(s) > tokens.size()) {
                                                                                index -= 1;
                                                                            }

                                                                            Authentication.authentication
                                                                                .edit()
                                                                                .putString("lasttoken", tokens.get(index))
                                                                                .remove("backedCreds")
                                                                                .commit();
                                                                        }

                                                                        Authentication.name = s;
                                                                        UserSubscriptions.switchAccounts();
                                                                        Reddit.forceRestart(mainActivity, true);
                                                                        break;
                                                                    }
                                                                }

                                                                if (!d) {
                                                                    Authentication.name = "LOGGEDOUT";
                                                                    Authentication.isLoggedIn = false;
                                                                    Authentication.authentication
                                                                        .edit()
                                                                        .remove("lasttoken")
                                                                        .remove("backedCreds")
                                                                        .commit();
                                                                    UserSubscriptions.switchAccounts();
                                                                    Reddit.forceRestart(mainActivity, true);
                                                                }

                                                            } else {
                                                                accounts.remove(accName);
                                                                keys.remove(accName);
                                                            }
                                                        })
                                                .setPositiveButton(R.string.btn_cancel, null)
                                                .show();
                                    }
                                });
                t.setOnClickListener(
                        new View.OnClickListener() {
                            @Override
                            public void onClick(View v) {
                                String accName = ((TextView) t.findViewById(R.id.name)).getText().toString();
                                LogUtil.v("Found name is " + accName);

                                if (!accName.equalsIgnoreCase(Authentication.name)) {
                                    LogUtil.v("Switching to " + accName);
                                    if (!accounts.get(accName).isEmpty()) {
                                        LogUtil.v("Using token " + accounts.get(accName));
                                        Authentication.authentication
                                            .edit()
                                            .putString("lasttoken", accounts.get(accName))
                                            .remove("backedCreds")
                                            .apply();
                                    } else {
                                        ArrayList<String> tokens = new ArrayList<>(Authentication.authentication.getStringSet("tokens", new HashSet<String>()));
                                        Authentication.authentication
                                            .edit()
                                            .putString("lasttoken", tokens.get(keys.indexOf(accName)))
                                            .remove("backedCreds")
                                            .apply();
                                    }

                                    Authentication.name = accName;
                                    // Reset moderator status for new account
                                    Authentication.mod = false;
                                    UserSubscriptions.modOf = null;
                                    UserSubscriptions.switchAccounts();
                                    Reddit.forceRestart(mainActivity, true);
                                }
                            }
                        });
                accountList.addView(t);
            }

            header.findViewById(R.id.godown)
                    .setOnClickListener(
                            new View.OnClickListener() {
                                @Override
                                public void onClick(View view) {
                                    LinearLayout body = header.findViewById(R.id.expand_profile);
                                    if (body.getVisibility() == View.GONE) {
                                        expand(body);
                                        AnimatorUtil.flipAnimator(false, view).start();
                                        view.findViewById(R.id.godown).setContentDescription(mainActivity.getResources().getString(R.string.btn_collapse));
                                    } else {
                                        collapse(body);
                                        AnimatorUtil.flipAnimator(true, view).start();
                                        view.findViewById(R.id.godown).setContentDescription(mainActivity.getResources().getString(R.string.btn_expand));
                                    }
                                }
                            });

            header.findViewById(R.id.guest_mode)
                    .setOnClickListener(
                            new OnSingleClickListener() {
                                @Override
                                public void onSingleClick(View v) {
                                    Authentication.name = "LOGGEDOUT";
                                    Authentication.isLoggedIn = false;
                                    Authentication.authentication
                                        .edit()
                                        .remove("lasttoken")
                                        .remove("backedCreds")
                                        .apply();
                                    UserSubscriptions.switchAccounts();
                                    Reddit.forceRestart(mainActivity, true);
                                }
                            });

            header.findViewById(R.id.add)
                    .setOnClickListener(
                            new OnSingleClickListener() {
                                @Override
                                public void onSingleClick(View view) {
                                    Intent inte = new Intent(mainActivity, Login.class);
                                    mainActivity.startActivity(inte);
                                }
                            });
            header.findViewById(R.id.offline)
                    .setOnClickListener(
                            new OnSingleClickListener() {
                                @Override
                                public void onSingleClick(View view) {
                                    Reddit.appRestart
                                        .edit()
                                        .putBoolean("forceoffline", true)
                                        .commit();
                                    Reddit.forceRestart(mainActivity, false);
                                }
                            });
            header.findViewById(R.id.inbox)
                    .setOnClickListener(
                            new OnSingleClickListener() {
                                @Override
                                public void onSingleClick(View view) {
                                    Intent inte = new Intent(mainActivity, Inbox.class);
                                    mainActivity.startActivityForResult(inte, MainActivity.INBOX_RESULT);
                                }
                            });

            mainActivity.headerMain = header;

            if (mainActivity.runAfterLoad == null) {
                new AsyncNotificationBadge(mainActivity).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
            }

        } else if (Authentication.didOnline) {
            header = inflater.inflate(R.layout.drawer_loggedout, drawerSubList, false);
            drawerSubList.addHeaderView(header, null, false);
            mainActivity.headerMain = header;
            hea = header.findViewById(R.id.back);

            final LinearLayout profStuff = header.findViewById(R.id.accountsarea);
            profStuff.setVisibility(View.GONE);
            mainActivity.findViewById(R.id.back)
                    .setOnClickListener(
                            new View.OnClickListener() {
                                @Override
                                public void onClick(View v) {
                                    if (profStuff.getVisibility() == View.GONE) {
                                        expand(profStuff);
                                        AnimatorUtil.flipAnimator(false, header.findViewById(R.id.headerflip)).start();
                                        header.findViewById(R.id.headerflip).setContentDescription(mainActivity.getResources().getString(R.string.btn_collapse));
                                    } else {
                                        collapse(profStuff);
                                        AnimatorUtil.flipAnimator(true, header.findViewById(R.id.headerflip)).start();
                                        header.findViewById(R.id.headerflip).setContentDescription(mainActivity.getResources().getString(R.string.btn_expand));
                                    }
                                }
                            });
            final HashMap<String, String> accounts = new HashMap<>();

            for (String s : Authentication.authentication.getStringSet("accounts", new HashSet<String>())) {
                if (s.contains(":")) {
                    accounts.put(s.split(":")[0], s.split(":")[1]);
                } else {
                    accounts.put(s, "");
                }
            }

            final ArrayList<String> keys = new ArrayList<>(accounts.keySet());
            final LinearLayout accountList = header.findViewById(R.id.accountsarea);

            for (final String accName : keys) {
                LogUtil.v(accName);
                final View t = mainActivity.getLayoutInflater().inflate(R.layout.account_textview_white, accountList, false);
                ((TextView) t.findViewById(R.id.name)).setText(accName);
                t.findViewById(R.id.remove)
                        .setOnClickListener(
                                new View.OnClickListener() {
                                    @Override
                                    public void onClick(View v) {
                                        new AlertDialog.Builder(mainActivity)
                                            .setTitle(R.string.profile_remove)
                                            .setMessage(R.string.profile_remove_account)
                                            .setNegativeButton(
                                                    R.string.btn_delete,
                                                    (dialog2, which2) -> {
                                                        Set<String> accounts2 = Authentication.authentication.getStringSet("accounts", new HashSet<>());
                                                        Set<String> done = new HashSet<>();

                                                        for (String s : accounts2) {
                                                            if (!s.contains(accName)) {
                                                                done.add(s);
                                                            }
                                                        }

                                                        Authentication.authentication.edit().putStringSet("accounts", done).commit();
                                                        dialog2.dismiss();
                                                        accountList.removeView(t);

                                                        if (accName.equalsIgnoreCase(Authentication.name)) {
                                                            boolean d = false;

                                                            for (String s : keys) {
                                                                if (!s.equalsIgnoreCase(accName)) {
                                                                    d = true;
                                                                    LogUtil.v("Switching to " + s);
                                                                    if (!accounts.get(s).isEmpty()) {
                                                                        Authentication.authentication
                                                                                .edit()
                                                                                .putString("lasttoken", accounts.get(s))
                                                                                .remove("backedCreds")
                                                                                .commit();
                                                                    } else {
                                                                        ArrayList<String>tokens = new ArrayList<>(Authentication.authentication
                                                                            .getStringSet("tokens", new HashSet<>()));
                                                                        Authentication.authentication
                                                                            .edit()
                                                                            .putString("lasttoken", tokens.get(keys.indexOf(s)))
                                                                            .remove("backedCreds")
                                                                            .commit();
                                                                    }

                                                                    Authentication.name = s;
                                                                    UserSubscriptions.switchAccounts();
                                                                    Reddit.forceRestart(mainActivity, true);
                                                                }
                                                            }

                                                            if (!d) {
                                                                Authentication.name = "LOGGEDOUT";
                                                                Authentication.isLoggedIn = false;
                                                                Authentication.authentication
                                                                    .edit()
                                                                    .remove("lasttoken")
                                                                    .remove("backedCreds")
                                                                    .commit();
                                                                UserSubscriptions
                                                                    .switchAccounts();
                                                                Reddit.forceRestart(
                                                                    mainActivity,
                                                                    true);
                                                            }
                                                        } else {
                                                            accounts.remove(accName);
                                                            keys.remove(accName);
                                                        }
                                                    })
                                            .setPositiveButton(R.string.btn_cancel, null)
                                            .show();
                                    }
                                });
                t.setOnClickListener(
                        new OnSingleClickListener() {
                            @Override
                            public void onSingleClick(View v) {
                                if (!accName.equalsIgnoreCase(Authentication.name)) {
                                    if (!accounts.get(accName).isEmpty()) {
                                        Authentication.authentication
                                            .edit()
                                            .putString("lasttoken", accounts.get(accName))
                                            .remove("backedCreds")
                                            .commit();
                                    } else {
                                        ArrayList<String> tokens = new ArrayList<>(Authentication.authentication.getStringSet("tokens", new HashSet<String>()));
                                        Authentication.authentication
                                            .edit()
                                            .putString("lasttoken", tokens.get(keys.indexOf(accName)))
                                            .remove("backedCreds")
                                            .commit();
                                    }

                                    // Removing this will break Guest mode
                                    Authentication.isLoggedIn = true;
                                    Authentication.name = accName;
                                    UserSubscriptions.switchAccounts();
                                    Reddit.forceRestart(mainActivity, true);
                                }
                            }
                        });
                accountList.addView(t);
            }

            header.findViewById(R.id.add)
                    .setOnClickListener(
                            new OnSingleClickListener() {
                                @Override
                                public void onSingleClick(View view) {
                                    Intent inte = new Intent(mainActivity, Login.class);
                                    mainActivity.startActivity(inte);
                                }
                            });
            header.findViewById(R.id.offline)
                    .setOnClickListener(
                            new OnSingleClickListener() {
                                @Override
                                public void onSingleClick(View view) {
                                    Reddit.appRestart
                                        .edit()
                                        .putBoolean("forceoffline", true)
                                        .commit();
                                    Reddit.forceRestart(mainActivity, false);
                                }
                            });
            mainActivity.headerMain = header;

            header.findViewById(R.id.multi)
                    .setOnClickListener(
                            new View.OnClickListener() {
                                @Override
                                public void onClick(View view) {
                                    new MaterialDialog.Builder(mainActivity)
                                        .inputRange(3, 20)
                                        .alwaysCallInputCallback()
                                        .input(
                                                mainActivity.getString(R.string.user_enter),
                                                null,
                                                new MaterialDialog.InputCallback() {
                                                    @Override
                                                    public void onInput(
                                                            @NonNull MaterialDialog dialog,
                                                            CharSequence input) {
                                                        final EditText editText = dialog.getInputEditText();
                                                        EditTextValidator.validateUsername(editText);
                                                        if (input.length() >= 3 && input.length() <= 20) {
                                                            dialog.getActionButton(DialogAction.POSITIVE).setEnabled(true);
                                                        }
                                                    }
                                                })
                                        .positiveText(R.string.user_btn_gotomultis)
                                        .onPositive(
                                                new MaterialDialog.SingleButtonCallback() {
                                                    @Override
                                                    public void onClick(
                                                            @NonNull MaterialDialog dialog,
                                                            @NonNull DialogAction which) {

                                                        if (mainActivity.runAfterLoad == null) {
                                                            Intent inte = new Intent(mainActivity, MultiredditOverview.class);
                                                            inte.putExtra(Profile.EXTRA_PROFILE, dialog.getInputEditText().getText().toString());
                                                            mainActivity.startActivity(inte);
                                                        }
                                                    }
                                                })
                                        .negativeText(R.string.btn_cancel)
                                        .show();
                                }
                            });

        } else {
            header = inflater.inflate(R.layout.drawer_offline, drawerSubList, false);
            mainActivity.headerMain = header;
            drawerSubList.addHeaderView(header, null, false);
            hea = header.findViewById(R.id.back);

            header.findViewById(R.id.online)
                    .setOnClickListener(
                            new OnSingleClickListener() {
                                @Override
                                public void onSingleClick(View view) {
                                    Reddit.appRestart.edit().remove("forceoffline").commit();
                                    Reddit.forceRestart(mainActivity, false);
                                }
                            });
        }

        final LinearLayout expandSettings = header.findViewById(R.id.expand_settings);
        header.findViewById(R.id.godown_settings)
                .setOnClickListener(
                        new View.OnClickListener() {
                            @Override
                            public void onClick(View v) {
                                if (expandSettings.getVisibility() == View.GONE) {
                                    expand(expandSettings);
                                    header.findViewById(R.id.godown_settings).setContentDescription(mainActivity.getResources().getString(R.string.btn_collapse));
                                    AnimatorUtil.flipAnimator(false, v).start();
                                } else {
                                    collapse(expandSettings);
                                    header.findViewById(R.id.godown_settings).setContentDescription(mainActivity.getResources().getString(R.string.btn_expand));
                                    AnimatorUtil.flipAnimator(true, v).start();
                                }
                            }
                        });

        { // Set up quick setting toggles
            final SwitchCompat toggleNightMode = expandSettings.findViewById(R.id.toggle_night_mode);
            toggleNightMode.setVisibility(View.VISIBLE);
            toggleNightMode.setChecked(mainActivity.inNightMode);
            toggleNightMode.setOnCheckedChangeListener(
                    new CompoundButton.OnCheckedChangeListener() {
                        @Override
                        public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                            SettingValues.forcedNightModeState = isChecked ? SettingValues.ForcedState.FORCED_ON : SettingValues.ForcedState.FORCED_OFF;
                            mainActivity.restartTheme();
                        }
                    });

            final SwitchCompat toggleImmersiveMode = expandSettings.findViewById(R.id.toggle_immersive_mode);
            toggleImmersiveMode.setChecked(SettingValues.immersiveMode);
            toggleImmersiveMode.setOnCheckedChangeListener(
                    new CompoundButton.OnCheckedChangeListener() {
                        @Override
                        public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                            SettingValues.immersiveMode = isChecked;
                            SettingValues.prefs
                                .edit()
                                .putBoolean(SettingValues.PREF_IMMERSIVE_MODE, isChecked)
                                .apply();

                            if (isChecked) {
                                mainActivity.hideDecor();
                            } else {
                                mainActivity.showDecor();
                            }
                        }
                    });

            final SwitchCompat toggleNSFW = expandSettings.findViewById(R.id.toggle_nsfw);
            toggleNSFW.setChecked(SettingValues.showNSFWContent);
            toggleNSFW.setOnCheckedChangeListener(
                    new CompoundButton.OnCheckedChangeListener() {
                        @Override
                        public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                            SettingValues.showNSFWContent = isChecked;
                            SettingValues.prefs
                                .edit()
                                .putBoolean(SettingValues.PREF_SHOW_NSFW_CONTENT, isChecked)
                                .apply();
                            mainActivity.reloadSubs();
                        }
                    });

            final SwitchCompat toggleRightThumbnails = expandSettings.findViewById(R.id.toggle_right_thumbnails);
            toggleRightThumbnails.setChecked(SettingValues.switchThumb);
            toggleRightThumbnails.setOnCheckedChangeListener(
                    new CompoundButton.OnCheckedChangeListener() {
                        @Override
                        public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                            SettingValues.switchThumb = isChecked;
                            SettingValues.prefs
                                .edit()
                                .putBoolean(SettingValues.PREF_SWITCH_THUMB, isChecked)
                                .apply();
                            mainActivity.reloadSubs();
                        }
                    });

            final SwitchCompat toggleReaderMode = expandSettings.findViewById(R.id.toggle_reader_mode);
            toggleReaderMode.setChecked(SettingValues.readerMode);
            toggleReaderMode.setOnCheckedChangeListener(
                    new CompoundButton.OnCheckedChangeListener() {
                        @Override
                        public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                            SettingValues.readerMode = isChecked;
                            SettingValues.prefs
                                .edit()
                                .putBoolean(SettingValues.PREF_READER_MODE, isChecked)
                                .apply();
                        }
                    });
        }

        header.findViewById(R.id.manage)
                .setOnClickListener(
                        new OnSingleClickListener() {
                            @Override
                            public void onSingleClick(View view) {
                                Intent i = new Intent(mainActivity, ManageOfflineContent.class);
                                mainActivity.startActivity(i);
                            }
                        });
        if (Authentication.didOnline) {
            View support = header.findViewById(R.id.support);

            support.setVisibility(View.GONE);
            header.findViewById(R.id.prof)
                .setOnClickListener(new OnSingleClickListener() {
                    @Override
                    public void onSingleClick(View view) {
                        showUsernameDialog(false);  // false for profile view
                    }
                });

        }

        header.findViewById(R.id.settings)
                .setOnClickListener(
                        new OnSingleClickListener() {
                            @Override
                            public void onSingleClick(View v) {
                                Intent i = new Intent(mainActivity, SettingsActivity.class);
                                mainActivity.startActivity(i);
                                // Cancel sub loading because exiting the settings will reload it anyway
                                if (mainActivity.sidebarController != null) mainActivity.sidebarController.cancelAsyncGetSubredditTask();
                                mainActivity.drawerLayout.closeDrawers();
                            }
                        });

        final Toolbar toolbar = (Toolbar) mainActivity.findViewById(R.id.toolbar);

        final androidx.appcompat.app.ActionBarDrawerToggle actionBarDrawerToggle =
                new androidx.appcompat.app.ActionBarDrawerToggle(mainActivity, mainActivity.drawerLayout, toolbar, R.string.btn_open, R.string.btn_close) {
                    @Override
                    public void onDrawerSlide(View drawerView, float slideOffset) {
                        super.onDrawerSlide(drawerView, 0); // this disables the animation
                    }

                    @Override
                    public void onDrawerOpened(View drawerView) {
                        super.onDrawerOpened(drawerView);
                        if (mainActivity.drawerLayout.isDrawerOpen(GravityCompat.END)) {
                            int current = mainActivity.pager.getCurrentItem();

                            if (current == mainActivity.toOpenComments && mainActivity.toOpenComments != 0) {
                                current -= 1;
                            }

                            String compare = mainActivity.usedArray.get(current);

                            if (compare.equals("random") || compare.equals("myrandom") || compare.equals("randnsfw")) {
                                if (mainActivity.adapter != null
                                        && mainActivity.adapter.getCurrentFragment() != null
                                        && ((SubmissionsView) mainActivity.adapter.getCurrentFragment())
                                                        .adapter
                                                        .dataSet
                                                        .subredditRandom
                                                != null) {
                                    String sub =
                                            ((SubmissionsView) mainActivity.adapter.getCurrentFragment())
                                                    .adapter
                                                    .dataSet
                                                    .subredditRandom;
                                    mainActivity.sidebarController.doSubSidebarNoLoad(sub);
                                    mainActivity.sidebarController.doSubSidebar(sub);
                                }
                            } else {
                                mainActivity.sidebarController.doSubSidebar(mainActivity.usedArray.get(current));
                            }
                        }
                    }

                    @Override
                    public void onDrawerClosed(View view) {
                        super.onDrawerClosed(view);
                        KeyboardUtil.hideKeyboard(mainActivity, mainActivity.drawerLayout.getWindowToken(), 0);
                    }
                };

        mainActivity.drawerLayout.addDrawerListener(actionBarDrawerToggle);

        actionBarDrawerToggle.syncState();
        header.findViewById(R.id.back).setBackgroundColor(Palette.getColor("alsdkfjasld"));
        accountsArea = header.findViewById(R.id.accountsarea);

        if (accountsArea != null) {
            accountsArea.setBackgroundColor(Palette.getDarkerColor("alsdkfjasld"));
        }

        setDrawerSubList();
        hideDrawerItems();
    }

    public void hideDrawerItems() {
        for (DrawerItemsDialog.SettingsDrawerEnum settingDrawerItem : DrawerItemsDialog.SettingsDrawerEnum.values()) {
            View drawerItem = drawerSubList.findViewById(settingDrawerItem.drawerId);

            if (drawerItem != null && drawerItem.getVisibility() == View.VISIBLE && (SettingValues.selectedDrawerItems & settingDrawerItem.value) == 0) {
                drawerItem.setVisibility(View.GONE);
            }
        }
    }

    public void setDrawerSubList() {
        ArrayList<String> copy;

        if (NetworkUtil.isConnected(mainActivity)) {
            copy = new ArrayList<>(mainActivity.usedArray);
        } else {
            copy = UserSubscriptions.getAllUserSubreddits(mainActivity);
        }

        copy.removeAll(Arrays.asList("", null));

        sideArrayAdapter = new SideArrayAdapter(mainActivity, copy, UserSubscriptions.getAllSubreddits(mainActivity), drawerSubList);
        drawerSubList.setAdapter(sideArrayAdapter);

        if ((SettingValues.subredditSearchMethod != Constants.SUBREDDIT_SEARCH_METHOD_TOOLBAR)) {
            drawerSearch = mainActivity.headerMain.findViewById(R.id.sort);
            drawerSearch.setVisibility(View.VISIBLE);
            drawerSubList.setFocusable(false);

            mainActivity.headerMain
                .findViewById(R.id.close_search_drawer)
                .setOnClickListener(
                        new View.OnClickListener() {
                            @Override
                            public void onClick(View v) {
                                drawerSearch.setText("");
                            }
                        });

            drawerSearch.setOnFocusChangeListener(
                    new View.OnFocusChangeListener() {
                        @Override
                        public void onFocusChange(View v, boolean hasFocus) {
                            if (hasFocus) {
                                mainActivity.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN);
                                drawerSubList.smoothScrollToPositionFromTop(1, drawerSearch.getHeight(), 100);
                            } else {
                                mainActivity.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
                            }
                        }
                    });
            drawerSearch.setOnEditorActionListener(
                    new TextView.OnEditorActionListener() {
                        @Override
                        public boolean onEditorAction(TextView arg0, int arg1, KeyEvent arg2) {
                            if (arg2 != null) {
                                return true;
                            }

                            String searchText = drawerSearch.getText().toString().toLowerCase(Locale.ENGLISH);
                            boolean searchSubFound = mainActivity.usedArray.contains(searchText);
                            int searchSubIndex = mainActivity.usedArray.indexOf(searchText);
                            int sideArrayAdapterIndex = mainActivity.usedArray.indexOf(sideArrayAdapter.fitems.get(0));

                            if (arg1 == EditorInfo.IME_ACTION_SEARCH) {
                                // If it the input text doesn't match a subreddit from the list exactly, openInSubView is true
                                if (sideArrayAdapter.fitems == null || sideArrayAdapter.openInSubView || !searchSubFound) {
                                    Intent inte = new Intent(mainActivity, SubredditView.class);
                                    inte.putExtra(SubredditView.EXTRA_SUBREDDIT, searchText);
                                    mainActivity.startActivityForResult(inte, 2001);
                                } else {
                                    if (mainActivity.commentPager && mainActivity.adapter instanceof MainPagerAdapterComment) {
                                        mainActivity.openingComments = null;
                                        mainActivity.toOpenComments = -1;
                                        ((MainPagerAdapterComment) mainActivity.adapter).size = (mainActivity.usedArray.size() + 1);
                                        mainActivity.adapter.notifyDataSetChanged();

                                        if (!searchSubFound) {
                                            mainActivity.doPageSelectedComments(sideArrayAdapterIndex);
                                        } else {
                                            mainActivity.doPageSelectedComments(searchSubIndex);
                                        }
                                    }

                                    if (!searchSubFound) {
                                        mainActivity.pager.setCurrentItem(sideArrayAdapterIndex);
                                    } else {
                                        mainActivity.pager.setCurrentItem(searchSubIndex);
                                    }

                                    mainActivity.drawerLayout.closeDrawers();
                                    drawerSearch.setText("");
                                    View view = mainActivity.getCurrentFocus();

                                    if (view != null) {
                                        KeyboardUtil.hideKeyboard(
                                                mainActivity, view.getWindowToken(), 0);
                                    }
                                }
                            }
                            return false;
                        }
                    });

            final View close = mainActivity.findViewById(R.id.close_search_drawer);
            close.setVisibility(View.GONE);

            drawerSearch.addTextChangedListener(
                new SimpleTextWatcher() {
                    @Override
                    public void afterTextChanged(Editable editable) {
                        final String result = editable.toString();
                        if (result.isEmpty()) {
                            close.setVisibility(View.GONE);
                        } else {
                            close.setVisibility(View.VISIBLE);
                        }
                        sideArrayAdapter.getFilter().filter(result);
                    }
                });
        } else {
            if (drawerSearch != null) {
                drawerSearch.setOnClickListener(null);
                drawerSearch.setVisibility(View.GONE);
            }
        }
    }

    private void collapse(final LinearLayout v) {
        int finalHeight = v.getHeight();
        ValueAnimator mAnimator = AnimatorUtil.slideAnimator(finalHeight, 0, v);

        mAnimator.addListener(
                new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animator) {
                        v.setVisibility(View.GONE);
                    }
                });
        mAnimator.start();
    }

    private void expand(LinearLayout v) {
        v.setVisibility(View.VISIBLE);

        final int widthSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED);
        final int heightSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED);

        v.measure(widthSpec, heightSpec);

        ValueAnimator mAnimator = AnimatorUtil.slideAnimator(0, v.getMeasuredHeight(), v);
        mAnimator.start();
    }

    private void showUsernameDialog(boolean isMultireddit) {
        final Context contextThemeWrapper = new ContextThemeWrapper(mainActivity, new ColorPreferences(mainActivity).getFontStyle().getBaseId());

        // Create TextInputLayout for proper error handling
        TextInputLayout inputLayout = new TextInputLayout(contextThemeWrapper);
        inputLayout.setErrorIconDrawable(null);
        inputLayout.setErrorEnabled(true);

        final EditText input = new EditText(contextThemeWrapper);
        input.setHint(mainActivity.getString(R.string.user_enter));
        input.setHintTextColor(mainActivity.getResources().getColor(R.color.md_grey_700));
        input.setSingleLine(true);
        input.setInputType(InputType.TYPE_CLASS_TEXT);

        // Match search box styling
        int underlineColor = new ColorPreferences(contextThemeWrapper).getColor(mainActivity.selectedSub);
        input.getBackground().setColorFilter(underlineColor, android.graphics.PorterDuff.Mode.SRC_ATOP);

        // Add EditText to TextInputLayout
        inputLayout.addView(input);

        FrameLayout frameLayout = new FrameLayout(contextThemeWrapper);
        int padding = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 24, mainActivity.getResources().getDisplayMetrics());
        frameLayout.setPadding(padding, 0, padding, 0);
        frameLayout.addView(inputLayout, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT));

        int positiveButtonText = R.string.user_btn_goto;

        if (isMultireddit) {
            positiveButtonText = R.string.user_btn_gotomultis;
        }

        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(contextThemeWrapper)
            .setTitle(R.string.user_enter)
            .setView(frameLayout)
            .setPositiveButton(positiveButtonText, null)
            .setNegativeButton(R.string.btn_cancel, null);

        AlertDialog dialog = builder.create();
        dialog.show();

        // Set accent color for buttons
        int accentColor = new ColorPreferences(contextThemeWrapper).getColor(mainActivity.selectedSub);
        dialog.getButton(DialogInterface.BUTTON_POSITIVE).setTextColor(accentColor);
        dialog.getButton(DialogInterface.BUTTON_NEGATIVE).setTextColor(accentColor);

        // Set up the positive button click listener after dialog is shown
        Button positiveButton = dialog.getButton(DialogInterface.BUTTON_POSITIVE);
        positiveButton.setEnabled(true);
        positiveButton.setOnClickListener(view1 -> {
            String username = input.getText().toString().trim();
            if (username.length() >= 3 && username.length() <= 20) {
                Intent inte;

                if (isMultireddit) {
                    inte = new Intent(mainActivity, MultiredditOverview.class);
                } else {
                    inte = new Intent(mainActivity, Profile.class);
                }

                inte.putExtra(Profile.EXTRA_PROFILE, username);
                mainActivity.startActivity(inte);
                dialog.dismiss();
            } else {
                inputLayout.setError("Username must be between 3 and 20 characters");
            }
        });

        // Clear error when text changes
        input.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                inputLayout.setError(null);
            }
            @Override public void afterTextChanged(Editable s) {}
        });
    }
}
