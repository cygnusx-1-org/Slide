package me.edgan.redditslide.Toolbox;

import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Color;
import android.os.AsyncTask;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.BackgroundColorSpan;
import android.text.style.ForegroundColorSpan;
import android.text.style.RelativeSizeSpan;
import android.util.TypedValue;
import android.view.ContextThemeWrapper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import java.lang.ref.WeakReference;
import java.net.MalformedURLException;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import me.edgan.redditslide.Activities.Reauthenticate;
import me.edgan.redditslide.Authentication;
import me.edgan.redditslide.Modmail.ModmailApi;
import me.edgan.redditslide.OpenRedditLink;
import me.edgan.redditslide.R;
import me.edgan.redditslide.SettingValues;
import me.edgan.redditslide.Views.RoundedBackgroundSpan;
import me.edgan.redditslide.Visuals.ColorPreferences;
import me.edgan.redditslide.util.DialogUtil;
import me.edgan.redditslide.util.MiscUtil;
import net.dean.jraw.ApiException;
import net.dean.jraw.RedditClient;
import net.dean.jraw.http.NetworkException;
import net.dean.jraw.http.oauth.InvalidScopeException;
import net.dean.jraw.managers.AccountManager;
import net.dean.jraw.managers.InboxManager;
import net.dean.jraw.managers.ModerationManager;
import net.dean.jraw.models.Comment;
import net.dean.jraw.models.DistinguishedStatus;
import net.dean.jraw.models.PublicContribution;
import net.dean.jraw.models.Submission;

/** Misc UI stuff for toolbox - usernote display, removal display, etc. */
public class ToolboxUI {

    /**
     * Shows a removal reason dialog
     *
     * @param context Context
     * @param thing Submission or Comment being removed
     */
    public static void showRemoval(
            final Context context,
            final PublicContribution thing,
            final CompletedRemovalCallback callback) {
        final String removalSubreddit;
        if (thing instanceof Comment) {
            removalSubreddit = ((Comment) thing).getSubredditName();
        } else if (thing instanceof Submission) {
            removalSubreddit = ((Submission) thing).getSubredditName();
        } else {
            return;
        }
        // canShowRemoval() is the gate every caller goes through, but re-check here rather than
        // trusting it: the config is loaded asynchronously and can be evicted between the two.
        final ToolboxConfig removalConfig = Toolbox.getConfig(removalSubreddit);
        final RemovalReasons removalReasons =
                removalConfig == null ? null : removalConfig.getRemovalReasons();
        if (removalReasons == null) {
            return;
        }

        final MaterialAlertDialogBuilder builder =
                new MaterialAlertDialogBuilder(
                        new ContextThemeWrapper(
                                context,
                                new ColorPreferences(context).getFontStyle().getBaseId()));

        // Set the dialog title
        builder.setTitle(
                context.getResources()
                        .getString(R.string.toolbox_removal_title, removalSubreddit));

        final View dialogContent =
                LayoutInflater.from(context).inflate(R.layout.toolbox_removal_dialog, null);

        final CheckBox headerToggle = dialogContent.requireViewById(R.id.toolbox_header_toggle);
        final TextView headerText = dialogContent.requireViewById(R.id.toolbox_header_text);
        final LinearLayout reasonsList = dialogContent.requireViewById(R.id.toolbox_reasons_list);
        final CheckBox footerToggle = dialogContent.requireViewById(R.id.toolbox_footer_toggle);
        final TextView footerText = dialogContent.requireViewById(R.id.toolbox_footer_text);
        final RadioGroup actions = dialogContent.requireViewById(R.id.toolbox_action);
        final CheckBox actionSticky = dialogContent.requireViewById(R.id.sticky_comment);
        final CheckBox actionModmail = dialogContent.requireViewById(R.id.pm_modmail);
        final CheckBox actionLock = dialogContent.requireViewById(R.id.lock);
        final EditText logReason = dialogContent.requireViewById(R.id.toolbox_log_reason);

        // Check if removal should be logged and set related views
        final boolean log = !removalReasons.getLogSub().isEmpty();
        if (log) {
            dialogContent.requireViewById(R.id.none).setVisibility(View.VISIBLE);
            if (removalReasons.getLogTitle().contains("{reason}")) {
                logReason.setVisibility(View.VISIBLE);
                logReason.setText(removalReasons.getLogReason());
            }
        }

        // Hide lock option if removing a comment
        if (thing instanceof Comment) {
            actionLock.setVisibility(View.GONE);
        }

        // Set up the header and footer options
        // A null decode means this device has no UTF-8 charset; treat it as no header/footer
        // rather than passing it on to replaceTokens.
        final String header = removalReasons.getHeader();
        headerText.setText(header == null ? "" : replaceTokens(header, thing));
        if (header == null || header.isEmpty()) {
            ((View) headerToggle.getParent()).setVisibility(View.GONE);
        }
        final String footer = removalReasons.getFooter();
        footerText.setText(footer == null ? "" : replaceTokens(footer, thing));
        if (footer == null || footer.isEmpty()) {
            ((View) footerToggle.getParent()).setVisibility(View.GONE);
        }

        // Set up the removal reason list
        final List<RemovalReasons.RemovalReason> reasons =
                removalReasons.getReasons() == null
                        ? new ArrayList<RemovalReasons.RemovalReason>()
                        : removalReasons.getReasons();
        for (RemovalReasons.RemovalReason reason : reasons) {
            CheckBox checkBox = new CheckBox(context);
            checkBox.setMaxLines(2);
            checkBox.setEllipsize(TextUtils.TruncateAt.END);
            final TypedValue tv = new TypedValue();
            final boolean found = context.getTheme().resolveAttribute(R.attr.fontColor, tv, true);
            checkBox.setTextColor(found ? tv.data : Color.WHITE);
            checkBox.setText(reason.getTitle().isEmpty() ? reason.getText() : reason.getTitle());
            reasonsList.addView(checkBox);
        }

        // Set default states of checkboxes/radiobuttons
        if (SettingValues.toolboxMessageType
                == SettingValues.ToolboxRemovalMessageType.COMMENT.ordinal()) {
            ((RadioButton) actions.requireViewById(R.id.comment)).setChecked(true);
        } else if (SettingValues.toolboxMessageType
                == SettingValues.ToolboxRemovalMessageType.PM.ordinal()) {
            ((RadioButton) actions.requireViewById(R.id.pm)).setChecked(true);
        } else if (SettingValues.toolboxMessageType
                == SettingValues.ToolboxRemovalMessageType.BOTH.ordinal()) {
            ((RadioButton) actions.requireViewById(R.id.both)).setChecked(true);
        } else {
            ((RadioButton) actions.requireViewById(R.id.none)).setChecked(true);
        }
        actionSticky.setChecked(SettingValues.toolboxSticky);
        actionModmail.setChecked(SettingValues.toolboxModmail);
        actionLock.setChecked(SettingValues.toolboxLock);

        // Set up dialog buttons
        builder.setView(dialogContent);
        builder.setNegativeButton(R.string.btn_cancel, null);
        builder.setPositiveButton(
                R.string.mod_btn_remove,
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        StringBuilder removalString = new StringBuilder();
                        StringBuilder flairText = new StringBuilder();
                        StringBuilder flairCSS = new StringBuilder();

                        // Add the header to the removal message
                        if (headerToggle.isChecked()) {
                            removalString.append(removalReasons.getHeader());
                            removalString.append("\n\n");
                        }
                        // Add the removal reasons
                        for (int i = 0; i < reasonsList.getChildCount(); i++) {
                            if (((CheckBox) reasonsList.getChildAt(i)).isChecked()) {
                                removalString.append(reasons.get(i).getText());
                                removalString.append("\n\n");

                                flairText.append(flairText.length() > 0 ? " " : "");
                                flairText.append(reasons.get(i).getFlairText());

                                flairCSS.append(flairCSS.length() > 0 ? " " : "");
                                flairCSS.append(reasons.get(i).getFlairCSS());
                            }
                        }
                        // Add the footer
                        if (footerToggle.isChecked()) {
                            removalString.append(removalReasons.getFooter());
                        }
                        // Add PM footer
                        if (actions.getCheckedRadioButtonId() == R.id.pm
                                || actions.getCheckedRadioButtonId() == R.id.both) {
                            removalString.append("\n\n---\n[[Link to your {kind}]({url})]");
                        }
                        // A PM "from the subreddit" is New Modmail, which needs a scope that
                        // tokens authorized before it was added don't carry. Warn here: the send
                        // fails inside the background task, which has no context to explain why.
                        if (actionModmail.isChecked()
                                && (actions.getCheckedRadioButtonId() == R.id.pm
                                        || actions.getCheckedRadioButtonId() == R.id.both)
                                && !Authentication.hasScope(ModmailApi.SCOPE)) {
                            Toast.makeText(
                                            context,
                                            R.string.modmail_scope_missing,
                                            Toast.LENGTH_LONG)
                                    .show();
                        }

                        // Remove the item and send the message if desired
                        new AsyncRemoveTask(callback)
                                .execute(
                                        thing, // thing
                                        actions.getCheckedRadioButtonId(), // action ID
                                        replaceTokens(
                                                removalString.toString(), thing), // removal reason
                                        replaceTokens(
                                                removalReasons.getPmSubject(),
                                                thing), // removal PM subject
                                        actionModmail.isChecked(), // modmail?
                                        actionSticky.isChecked(), // sticky?
                                        actionLock.isChecked(), // lock?
                                        log, // log the removal?
                                        replaceTokens(
                                                        removalReasons.getLogTitle(),
                                                        thing) // log post title
                                                .replace("{reason}", logReason.getText()),
                                        removalReasons.getLogSub(), // log sub
                                        new String[] {
                                            flairText.toString(), flairCSS.toString()
                                        } // flair text and css
                                        );
                    }
                });

        builder.create().show();
    }

    /**
     * Checks if a Toolbox removal dialog can be shown for a subreddit
     *
     * @param subreddit Subreddit
     * @return whether a toolbox removal dialog can be shown
     */
    public static boolean canShowRemoval(String subreddit) {
        final ToolboxConfig config = Toolbox.getConfig(subreddit);
        return SettingValues.toolboxEnabled
                && config != null
                && config.getRemovalReasons() != null;
    }

    /**
     * Replace toolbox tokens with the appropriate replacements Does NOT include log-related tokens,
     * those must be handled after logging.
     *
     * <p>Every JRAW getter below is {@code @Nullable} for an absent JSON member, and {@code
     * String.replace} throws on a null replacement. NullAway cannot see it — {@code String} is
     * unannotated, so a null argument passes without a word, which is NULLAWAY.md phase 4's second
     * lesson. The values are substituted into removal-message text and nothing else, so an absent
     * member degrades to an empty token rather than crashing the removal dialog. That is also why
     * {@code getUrl()} is coalesced here despite phase 10 excluding it from the mechanical pass:
     * there it fed a loader, here it is only ever text.
     *
     * @param reason String to be parsed
     * @param parameter Item being acted upon
     * @return String with replacements made
     */
    public static String replaceTokens(String reason, PublicContribution parameter) {
        if (parameter instanceof Comment) {
            Comment thing = (Comment) parameter;
            return reason.replace("{subreddit}", MiscUtil.orEmpty(thing.getSubredditName()))
                    .replace("{author}", MiscUtil.orEmpty(thing.getAuthor()))
                    .replace("{kind}", "comment")
                    .replace("{mod}", Authentication.nameOrEmpty())
                    .replace("{title}", "")
                    .replace(
                            "{url}",
                            "https://www.reddit.com"
                                    + thing.getDataNode().path("permalink").asText())
                    .replace("{domain}", "")
                    .replace("{link}", "undefined");
        } else if (parameter instanceof Submission) {
            Submission thing = (Submission) parameter;
            return reason.replace("{subreddit}", MiscUtil.orEmpty(thing.getSubredditName()))
                    .replace("{author}", MiscUtil.orEmpty(thing.getAuthor()))
                    .replace("{kind}", "submission")
                    .replace("{mod}", Authentication.nameOrEmpty())
                    .replace("{title}", MiscUtil.orEmpty(thing.getTitle()))
                    .replace(
                            "{url}",
                            "https://www.reddit.com"
                                    + thing.getDataNode().path("permalink").asText())
                    .replace("{domain}", MiscUtil.orEmpty(thing.getDomain()))
                    .replace("{link}", MiscUtil.orEmpty(thing.getUrl()));
        } else {
            throw new IllegalArgumentException("Must be passed a submission or comment!");
        }
    }

    /**
     * Shows a user's usernotes in a dialog
     *
     * @param context context
     * @param author user to show usernotes for
     * @param subreddit subreddit to get usernotes from
     * @param currentLink Link, in Toolbox format, for the current item - used for adding usernotes
     */
    public static void showUsernotes(
            final Context context, String author, String subreddit, String currentLink) {
        final UsernoteListAdapter adapter = new UsernoteListAdapter(context, subreddit, author);
        DialogUtil.showWithCardBackground(new AlertDialog.Builder(context)
                .setTitle(context.getResources().getString(R.string.mod_usernotes_title, author))
                .setAdapter(adapter, null)
                .setNeutralButton(
                        R.string.mod_usernotes_add,
                        (dialog, which) -> {
                            // set up layout for add note dialog
                            final LinearLayout layout = new LinearLayout(context);
                            final Spinner spinner = new Spinner(context);
                            final EditText noteText = new EditText(context);

                            layout.addView(spinner);
                            layout.addView(noteText);

                            noteText.setHint(R.string.toolbox_note_text_placeholder);

                            layout.setOrientation(LinearLayout.VERTICAL);
                            LinearLayout.LayoutParams params =
                                    new LinearLayout.LayoutParams(
                                            LinearLayout.LayoutParams.MATCH_PARENT,
                                            LinearLayout.LayoutParams.WRAP_CONTENT);
                            spinner.setLayoutParams(params);
                            noteText.setLayoutParams(params);

                            // create list of types, add default "no type" type
                            List<CharSequence> types = new ArrayList<>();
                            SpannableStringBuilder defaultType =
                                    new SpannableStringBuilder(
                                            " "
                                                    + context.getString(
                                                            R.string.toolbox_note_default)
                                                    + " ");
                            defaultType.setSpan(
                                    new BackgroundColorSpan(Color.parseColor("#808080")),
                                    0,
                                    defaultType.length(),
                                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                            defaultType.setSpan(
                                    new ForegroundColorSpan(Color.WHITE),
                                    0,
                                    defaultType.length(),
                                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                            types.add(defaultType);

                            // add additional types
                            ToolboxConfig config = Toolbox.getConfig(subreddit);

                            final Map<String, Map<String, String>> configured =
                                    config == null ? null : config.getUsernoteTypes();
                            final Map<String, Map<String, String>> typeMap =
                                    configured != null && !configured.isEmpty()
                                            ? configured
                                            : Toolbox.DEFAULT_USERNOTE_TYPES;

                            for (Map<String, String> stringStringMap : typeMap.values()) {
                                SpannableStringBuilder typeString =
                                        new SpannableStringBuilder(
                                                " [" + stringStringMap.get("text") + "] ");
                                typeString.setSpan(
                                        new BackgroundColorSpan(
                                                Color.parseColor(stringStringMap.get("color"))),
                                        0,
                                        typeString.length(),
                                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                                typeString.setSpan(
                                        new ForegroundColorSpan(Color.WHITE),
                                        0,
                                        typeString.length(),
                                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                                types.add(typeString);
                            }

                            spinner.setAdapter(
                                    new ArrayAdapter<>(
                                            context,
                                            android.R.layout.simple_spinner_dropdown_item,
                                            types));

                            // show add note dialog
                            final AlertDialog noteDialog =
                                    new MaterialAlertDialogBuilder(
                                                    new ContextThemeWrapper(
                                                            context,
                                                            new ColorPreferences(context)
                                                                    .getFontStyle()
                                                                    .getBaseId()))
                                            .setView(layout)
                                            .setPositiveButton(R.string.btn_add, null)
                                            .setNegativeButton(R.string.btn_cancel, null)
                                            .create();
                            noteDialog.setOnShowListener(
                                    d ->
                                            noteDialog
                                                    .getButton(DialogInterface.BUTTON_POSITIVE)
                                                    .setOnClickListener(
                                                            v -> {
                                                                if (noteText.getText().length()
                                                                        == 0) {
                                                                    noteText.setError(
                                                                            context.getString(
                                                                                    R.string
                                                                                            .toolbox_note_text_required));
                                                                    return;
                                                                }
                                                                int selected =
                                                                        spinner
                                                                                .getSelectedItemPosition();
                                                                new AsyncAddUsernoteTask(context)
                                                                        .execute(
                                                                                subreddit,
                                                                                author,
                                                                                noteText.getText()
                                                                                        .toString(),
                                                                                currentLink,
                                                                                selected - 1 >= 0
                                                                                        ? typeMap
                                                                                                .keySet()
                                                                                                .toArray()
                                                                                                [
                                                                                                selected
                                                                                                        - 1]
                                                                                                .toString()
                                                                                        : null);
                                                                noteDialog.dismiss();
                                                            }));
                            noteDialog.show();
                        })
                .setPositiveButton(R.string.btn_close, null)
                );
    }

    /**
     * Appends a usernote to builder if a usernote in the subreddit is available, and the current
     * user has it enabled.
     *
     * @param context Android context
     * @param builder The builder to append the usernote to
     * @param subreddit The subreddit to look for notes in
     * @param user The user to look for
     */
    public static void appendToolboxNote(
            Context context,
            SpannableStringBuilder builder,
            @Nullable String subreddit,
            @Nullable String user) {
        if (!SettingValues.toolboxEnabled || !Authentication.mod) {
            return;
        }

        Usernotes notes = Toolbox.getUsernotes(subreddit);
        if (notes == null) {
            return;
        }

        List<Usernote> notesForUser = notes.getNotesForUser(user);
        if (notesForUser == null || notesForUser.isEmpty()) {
            return;
        }

        SpannableStringBuilder noteBuilder =
                new SpannableStringBuilder("\u00A0" + notes.getDisplayNoteForUser(user) + "\u00A0");

        noteBuilder.setSpan(
                new RoundedBackgroundSpan(
                        ContextCompat.getColor(context, android.R.color.white),
                        notes.getDisplayColorForUser(user),
                        false,
                        context),
                0,
                noteBuilder.length(),
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

        builder.append(" ");
        builder.append(noteBuilder);
    }

    public static class UsernoteListAdapter extends ArrayAdapter<UsernoteListItem> {
        public UsernoteListAdapter(@NonNull Context context, String subreddit, String user) {
            super(context, R.layout.usernote_list_item, R.id.usernote_note_text);

            final Usernotes usernotes = Toolbox.getUsernotes(subreddit);

            final List<Usernote> notesForUser =
                    usernotes == null ? null : usernotes.getNotesForUser(user);
            if (usernotes != null && notesForUser != null) {
                for (Usernote note : notesForUser) {
                    String dateString =
                            SimpleDateFormat.getDateTimeInstance(
                                            SimpleDateFormat.SHORT, SimpleDateFormat.SHORT)
                                    .format(new Date(note.getTime()));

                    SpannableStringBuilder authorDateText =
                            new SpannableStringBuilder(
                                    usernotes.getModNameFromModIndex(note.getMod())
                                            + "\n"
                                            + dateString);
                    authorDateText.setSpan(
                            new RelativeSizeSpan(.92f),
                            authorDateText.length() - dateString.length(),
                            authorDateText.length(),
                            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

                    SpannableStringBuilder noteText =
                            new SpannableStringBuilder(
                                    usernotes.getWarningTextFromWarningIndex(
                                            note.getWarning(), true));
                    noteText.setSpan(
                            new ForegroundColorSpan(
                                    usernotes.getColorFromWarningIndex(note.getWarning())),
                            0,
                            noteText.length(),
                            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                    if (noteText.length() > 0) {
                        noteText.append(" ");
                    }
                    noteText.append(note.getNoteText());

                    String link = note.getLinkAsURL(subreddit);

                    this.add(
                            new UsernoteListItem(
                                    authorDateText, noteText, link, note, subreddit, user));
                }
            }
        }

        @NonNull
        @Override
        public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
            final View view = super.getView(position, convertView, parent);
            // getItem is @Nullable only because ArrayAdapter is generic; position comes from the
            // adapter's own getCount, so the backing list always has an entry here.
            final UsernoteListItem item = Objects.requireNonNull(getItem(position));

            TextView authorDatetime = view.requireViewById(R.id.usernote_author_datetime);
            authorDatetime.setText(item.getAuthorDatetime());

            TextView noteText = view.requireViewById(R.id.usernote_note_text);
            noteText.setText(item.getNoteText());

            view.setOnClickListener(
                    new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            if (item.getLink() != null) {
                                OpenRedditLink.openUrl(view.getContext(), item.getLink(), true);
                            }
                        }
                    });

            view.requireViewById(R.id.delete)
                    .setOnClickListener(
                            new View.OnClickListener() {
                                @Override
                                public void onClick(View v) {
                                    new AsyncRemoveUsernoteTask(item.getNote(), getContext())
                                            .execute(item.getSubreddit(), item.getUser());
                                    remove(item);
                                }
                            });

            return view;
        }
    }

    public static class UsernoteListItem {
        private CharSequence authorDatetime;
        private CharSequence noteText;
        // Null for a note that points at nothing; getView() checks before opening it.
        @Nullable private String link;
        private Usernote note;
        private String subreddit;
        private String user;

        public UsernoteListItem(
                CharSequence authorDatetime,
                CharSequence noteText,
                @Nullable String link,
                Usernote note,
                String subreddit,
                String user) {
            this.authorDatetime = authorDatetime;
            this.noteText = noteText;
            this.link = link;
            this.note = note;
            this.subreddit = subreddit;
            this.user = user;
        }

        public CharSequence getAuthorDatetime() {
            return authorDatetime;
        }

        public CharSequence getNoteText() {
            return noteText;
        }

        @Nullable
        public String getLink() {
            return link;
        }

        public Usernote getNote() {
            return note;
        }

        public String getSubreddit() {
            return subreddit;
        }

        public String getUser() {
            return user;
        }
    }

    /**
     * Removes a post/comment, optionally locking first if a post. Parameters are: thing (extends
     * PublicContribution), action ID (int), removal reason (String), removal subject (String),
     * modmail (boolean), sticky (boolean), lock (boolean), log (boolean), logtitle (String), logsub
     * (String) flair (String[] - [text, css])
     */
    public static class AsyncRemoveTask extends AsyncTask<Object, Void, Boolean> {
        CompletedRemovalCallback callback;

        public AsyncRemoveTask(CompletedRemovalCallback callback) {
            this.callback = callback;
        }

        /**
         * Runs the removal and necessary action(s)
         *
         * @param objects ...
         * @return Success
         */
        @Override
        protected Boolean doInBackground(Object... objects) {
            PublicContribution thing = (PublicContribution) objects[0];
            int action = (int) objects[1];
            String removalString = (String) objects[2];
            String pmSubject = (String) objects[3];
            boolean modmail = (boolean) objects[4];
            boolean sticky = (boolean) objects[5];
            boolean lock = (boolean) objects[6];
            boolean log = (boolean) objects[7];
            String logTitle = (String) objects[8];
            String logSub = (String) objects[9];
            String[] flair = (String[]) objects[10];

            boolean success = true;

            String logResult = "";
            if (log) {
                // Log the removal
                Submission s =
                        logRemoval(
                                logSub,
                                logTitle,
                                "https://www.reddit.com"
                                        + thing.getDataNode().path("permalink").asText());
                if (s != null) {
                    logResult =
                            "https://www.reddit.com" + s.getDataNode().path("permalink").asText();
                } else {
                    success = false;
                }
            }

            // Check what the desired action is and perform it
            if (action == R.id.comment) {
                success &=
                        postRemovalComment(
                                thing, removalString.replace("{loglink}", logResult), sticky);
            } else if (action == R.id.pm) {
                if (thing instanceof Comment) {
                    success &=
                            sendRemovalPM(
                                    modmail ? MiscUtil.orEmpty(((Comment) thing).getSubredditName()) : "",
                                    MiscUtil.orEmpty(((Comment) thing).getAuthor()),
                                    pmSubject.replace("{loglink}", logResult),
                                    removalString);
                } else {
                    success &=
                            sendRemovalPM(
                                    modmail ? MiscUtil.orEmpty(((Submission) thing).getSubredditName()) : "",
                                    MiscUtil.orEmpty(((Submission) thing).getAuthor()),
                                    pmSubject.replace("{loglink}", logResult),
                                    removalString);
                }
            } else if (action == R.id.both) {
                success &=
                        postRemovalComment(
                                thing, removalString.replace("{loglink}", logResult), sticky);
                if (thing instanceof Comment) {
                    success &=
                            sendRemovalPM(
                                    modmail ? MiscUtil.orEmpty(((Comment) thing).getSubredditName()) : "",
                                    MiscUtil.orEmpty(((Comment) thing).getAuthor()),
                                    pmSubject.replace("{loglink}", logResult),
                                    removalString);
                } else {
                    success &=
                            sendRemovalPM(
                                    modmail ? MiscUtil.orEmpty(((Submission) thing).getSubredditName()) : "",
                                    MiscUtil.orEmpty(((Submission) thing).getAuthor()),
                                    pmSubject.replace("{loglink}", logResult),
                                    removalString);
                }
            }
            // R.id.none needs no handling as we don't do anything on none.

            // Remove the item and lock/apply necessary flair
            try {
                new ModerationManager(Authentication.reddit)
                        .remove((PublicContribution) objects[0], false);
                if (lock && thing instanceof Submission) {
                    new ModerationManager(Authentication.reddit).setLocked(thing);
                }
                if ((flair[0].length() > 0 || flair[1].length() > 0)
                        && thing instanceof Submission) {
                    new ModerationManager(Authentication.reddit)
                            .setFlair(
                                    ((Submission) thing).getSubredditName(),
                                    (Submission) thing,
                                    flair[0],
                                    flair[1]);
                }
            } catch (ApiException | RuntimeException e) {
                success = false;
            }

            return success;
        }

        /**
         * Run the callback
         *
         * @param success Whether doInBackground was a complete success
         */
        @Override
        protected void onPostExecute(Boolean success) {
            // Run the callback on the UI thread
            callback.onComplete(success);
        }

        /**
         * Send a removal PM
         *
         * @param from empty string if from user, sub name if from sub
         * @param to recipient
         * @param subject subject
         * @param body body
         * @return success
         */
        private boolean sendRemovalPM(String from, String to, String subject, String body) {
            // A non-empty "from" means send as the subreddit — that is modmail, so route it through
            // New Modmail. The legacy /api/compose from_sr path rides the retired modmail system.
            // A plain user PM (empty "from") still goes through the regular compose endpoint.
            if (!from.isEmpty()) {
                return ModmailApi.createConversation(from, to, subject, body, true);
            }
            try {
                new InboxManager(Authentication.reddit).compose(from, to, subject, body);
                return true;
            } catch (ApiException | RuntimeException e) {
                return false;
            }
        }

        /**
         * Post a removal comment
         *
         * @param thing thing to reply to
         * @param comment comment text
         * @param sticky whether to sticky the comment
         * @return success
         */
        private boolean postRemovalComment(
                PublicContribution thing, String comment, boolean sticky) {
            final RedditClient client = Authentication.reddit;
            if (client == null) {
                return false;
            }
            try {
                // Reply with a comment and get that comment's ID
                String id = new AccountManager(client).reply(thing, comment);

                // Sticky or distinguish the posted comment
                if (sticky) {
                    new ModerationManager(client)
                            .setSticky((Comment) client.get("t1_" + id).get(0), true);
                } else {
                    new ModerationManager(client)
                            .setDistinguishedStatus(
                                    client.get("t1_" + id).get(0), DistinguishedStatus.MODERATOR);
                }
                return true;
            } catch (ApiException | RuntimeException e) {
                return false;
            }
        }

        /**
         * Log a removal to a logsub
         *
         * @param logSub name of log sub
         * @param title title of post
         * @return resulting submission, or null if the log post could not be made
         */
        @Nullable
        private Submission logRemoval(String logSub, String title, String link) {
            try {
                return new AccountManager(Authentication.reddit)
                        .submit(new AccountManager.SubmissionBuilder(new URL(link), logSub, title));
            } catch (MalformedURLException | ApiException | NetworkException e) {
                return null;
            }
        }

        /**
         * Convenience method to execute the task with the correct parameters
         *
         * @param thing Thing being removed
         * @param action Action to take
         * @param removalReason Removal reason
         * @param pmSubject Removal PM subject
         * @param modmail Whether to send PM as modmail
         * @param sticky Whether to sticky removal comment
         * @param lock Whether to lock removed thread
         * @param log Whether to log the removal
         * @param logTitle Log post title
         * @param logSub Log subreddit
         * @param flair Flair [text, CSS]
         */
        public void execute(
                PublicContribution thing,
                int action,
                String removalReason,
                String pmSubject,
                boolean modmail,
                boolean sticky,
                boolean lock,
                boolean log,
                String logTitle,
                String logSub,
                String[] flair) {
            super.execute(
                    thing,
                    action,
                    removalReason,
                    pmSubject,
                    modmail,
                    sticky,
                    lock,
                    log,
                    logTitle,
                    logSub,
                    flair);
        }
    }

    /** Add a usernote for a subreddit Parameters are: subreddit user note text link type */
    public static class AsyncAddUsernoteTask extends AsyncTask<String, Void, Boolean> {
        private WeakReference<Context> contextRef;

        AsyncAddUsernoteTask(Context context) {
            this.contextRef = new WeakReference<>(context);
        }

        @Override
        protected Boolean doInBackground(String... strings) {
            String reason;

            try {
                Toolbox.downloadUsernotes(strings[0]);
            } catch (RuntimeException e) {
                // Connection failures surface as a bare RuntimeException (not NetworkException)
                return false;
            }
            if (Toolbox.getUsernotes(strings[0]) == null) {
                if (Toolbox.usernotesUnreadable(strings[0])) {
                    // The sub has a usernotes page, we just could not read it. Creating a fresh
                    // config here and uploading it would replace that page with an empty one;
                    // report the failure instead.
                    return false;
                }
                Toolbox.createUsernotes(strings[0]);
                reason = "create usernotes config";
            } else {
                reason = "create new note on user " + strings[1];
            }
            final Usernotes usernotes = Toolbox.getUsernotes(strings[0]);
            if (usernotes == null) {
                return false;
            }
            usernotes.createNote(
                            strings[1], // user
                            strings[2], // note text
                            strings[3], // link
                            System.currentTimeMillis(), // time
                            Authentication.nameOrEmpty(), // mod
                            strings[4] // type
                            );
            try {
                Toolbox.uploadUsernotes(strings[0], reason);
            } catch (
                    InvalidScopeException
                            e) { // we don't have wikiedit scope, need to reauth to get it
                return false;
            }
            return true;
        }

        @Override
        protected void onPostExecute(Boolean success) {
            if (!success) {
                final Context context = contextRef.get();
                if (context == null) {
                    return;
                }
                new MaterialAlertDialogBuilder(
                                new ContextThemeWrapper(
                                        context,
                                        new ColorPreferences(context).getFontStyle().getBaseId()))
                        .setTitle(R.string.toolbox_wiki_edit_reauth)
                        .setMessage(R.string.toolbox_wiki_edit_reauth_question)
                        .setNegativeButton(R.string.misc_maybe_later, null)
                        .setPositiveButton(
                                R.string.btn_yes,
                                (dialog1, which1) ->
                                        context.startActivity(
                                                new Intent(context, Reauthenticate.class)))
                        .show();
            }
        }
    }

    /** Remove a usernote from a subreddit Parameters are: subreddit user */
    public static class AsyncRemoveUsernoteTask extends AsyncTask<String, Void, Boolean> {
        private Usernote note;
        private WeakReference<Context> contextRef;

        AsyncRemoveUsernoteTask(Usernote note, Context context) {
            this.note = note;
            this.contextRef = new WeakReference<>(context);
        }

        @Override
        protected Boolean doInBackground(String... strings) {
            try {
                Toolbox.downloadUsernotes(strings[0]);
            } catch (RuntimeException e) {
                // Connection failures surface as a bare RuntimeException (not NetworkException)
                return false;
            }
            final Usernotes usernotes = Toolbox.getUsernotes(strings[0]);
            if (usernotes == null) {
                return false;
            }
            usernotes.removeNote(strings[1], note);
            try {
                Toolbox.uploadUsernotes(
                        strings[0], "delete note " + note.getTime() + " on user " + strings[1]);
            } catch (
                    InvalidScopeException
                            e) { // we don't have wikiedit scope, need to reauth to get it
                return false;
            }
            return true;
        }

        @Override
        protected void onPostExecute(Boolean success) {
            if (!success) {
                final Context context = contextRef.get();
                if (context == null) {
                    return;
                }
                DialogUtil.showWithCardBackground(new AlertDialog.Builder(context)
                        .setTitle(R.string.toolbox_wiki_edit_reauth)
                        .setMessage(R.string.toolbox_wiki_edit_reauth_question)
                        .setNegativeButton(R.string.misc_maybe_later, null)
                        .setPositiveButton(
                                R.string.btn_yes,
                                (dialog1, which1) ->
                                        context.startActivity(
                                                new Intent(context, Reauthenticate.class)))
                        );
            }
        }
    }

    /** A callback for code to be run on the UI thread after removal. */
    public interface CompletedRemovalCallback {
        /**
         * Called when the removal is completed
         *
         * @param success Whether the removal and reason-sending process was 100% successful or not
         */
        void onComplete(boolean success);
    }
}
