package me.edgan.redditslide.Views;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.net.Uri;
import android.text.Editable;
import android.util.Base64;
import android.view.ContextThemeWrapper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.ComponentActivity;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.PickVisualMediaRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import me.edgan.redditslide.Activities.Draw;
import me.edgan.redditslide.Authentication;
import me.edgan.redditslide.Drafts;
import me.edgan.redditslide.ImgurAlbum.UploadImgur;
import me.edgan.redditslide.ImgurAlbum.UploadImgurAlbum;
import me.edgan.redditslide.R;
import me.edgan.redditslide.SettingValues;
import me.edgan.redditslide.SpoilerRobotoTextView;
import me.edgan.redditslide.Visuals.ColorPreferences;
import me.edgan.redditslide.markdown.RedditMarkwon;
import me.edgan.redditslide.util.DialogUtil;
import me.edgan.redditslide.util.DisplayUtil;
import me.edgan.redditslide.util.KeyboardUtil;
import me.edgan.redditslide.util.LayoutUtils;
import me.edgan.redditslide.util.LogUtil;
import me.edgan.redditslide.util.MaterialProgressDialog;
import me.edgan.redditslide.util.SubmissionParser;
import me.edgan.redditslide.util.TranslateUtil;
import org.apache.commons.text.StringEscapeUtils;
import org.commonmark.Extension;
import org.commonmark.ext.gfm.strikethrough.StrikethroughExtension;
import org.commonmark.ext.gfm.tables.TablesExtension;
import org.commonmark.node.Node;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;
import org.json.JSONObject;

/** Created by carlo_000 on 10/18/2015. */
public class DoEditorActions {

    private static final AtomicInteger registryCounter = new AtomicInteger(0);

    public static void doActions(
            final EditText editText,
            final View baseView,
            final FragmentManager fm,
            final Activity a,
            final String oldComment,
            @Nullable final String[] authors) {
        doActions(editText, baseView, fm, a, oldComment, authors, null);
    }

    public static void doActions(
            final EditText editText,
            final View baseView,
            final FragmentManager fm,
            final Activity a,
            final String oldComment,
            @Nullable final String[] authors,
            @Nullable final ActivityResultLauncher<PickVisualMediaRequest> imageLauncher) {
        baseView.findViewById(R.id.bold)
                .setOnClickListener(
                        new View.OnClickListener() {
                            @Override
                            public void onClick(View v) {
                                if (editText.hasSelection()) {
                                    wrapString(
                                            "**",
                                            editText); // If the user has text selected, wrap that
                                    // text in the symbols
                                } else {
                                    // If the user doesn't have text selected, put the symbols
                                    // around the cursor's position
                                    int pos = editText.getSelectionStart();
                                    editText.getText().insert(pos, "**");
                                    editText.getText().insert(pos + 1, "**");
                                    editText.setSelection(
                                            pos + 2); // put the cursor between the symbols
                                }
                            }
                        });

        if (baseView.findViewById(R.id.author) != null) {
            if (authors != null && authors.length > 0) {
                baseView.findViewById(R.id.author)
                        .setOnClickListener(
                                new View.OnClickListener() {
                                    @Override
                                    public void onClick(View v) {
                                        if (authors.length == 1) {
                                            String author = "/u/" + authors[0];
                                            insertBefore(author, editText);
                                        } else {
                                            DialogUtil.showWithCardBackground(new AlertDialog.Builder(a)
                                                    .setTitle(R.string.authors_above)
                                                    .setItems(
                                                            authors,
                                                            (dialog, which) -> {
                                                                String author =
                                                                        "/u/" + authors[which];
                                                                insertBefore(author, editText);
                                                            })
                                                    .setNeutralButton(R.string.btn_cancel, null)
                                                    );
                                        }
                                    }
                                });
            } else {
                baseView.findViewById(R.id.author).setVisibility(View.GONE);
            }
        }

        baseView.findViewById(R.id.italics)
                .setOnClickListener(
                        new View.OnClickListener() {
                            @Override
                            public void onClick(View v) {
                                if (editText.hasSelection()) {
                                    wrapString(
                                            "*",
                                            editText); // If the user has text selected, wrap that
                                    // text in the symbols
                                } else {
                                    // If the user doesn't have text selected, put the symbols
                                    // around the cursor's position
                                    int pos = editText.getSelectionStart();
                                    editText.getText().insert(pos, "*");
                                    editText.getText().insert(pos + 1, "*");
                                    editText.setSelection(
                                            pos + 1); // put the cursor between the symbols
                                }
                            }
                        });

        baseView.findViewById(R.id.strike)
                .setOnClickListener(
                        new View.OnClickListener() {
                            @Override
                            public void onClick(View v) {
                                if (editText.hasSelection()) {
                                    wrapString(
                                            "~~",
                                            editText); // If the user has text selected, wrap that
                                    // text in the symbols
                                } else {
                                    // If the user doesn't have text selected, put the symbols
                                    // around the cursor's position
                                    int pos = editText.getSelectionStart();
                                    editText.getText().insert(pos, "~~");
                                    editText.getText().insert(pos + 2, "~~");
                                    editText.setSelection(
                                            pos + 2); // put the cursor between the symbols
                                }
                            }
                        });

        baseView.findViewById(R.id.spoiler)
                .setOnClickListener(
                        new View.OnClickListener() {
                            @Override
                            public void onClick(View v) {
                                if (editText.hasSelection()) {
                                    wrapString(
                                            ">!", "!<",
                                            editText); // If the user has text selected, wrap that
                                    // text in the symbols
                                } else {
                                    // If the user doesn't have text selected, put the symbols
                                    // around the cursor's position
                                    int pos = editText.getSelectionStart();
                                    editText.getText().insert(pos, ">!");
                                    editText.getText().insert(pos + 2, "!<");
                                    editText.setSelection(
                                            pos + 2); // put the cursor between the symbols
                                }
                            }
                        });

        baseView.findViewById(R.id.savedraft)
                .setOnClickListener(
                        new View.OnClickListener() {
                            @Override
                            public void onClick(View v) {
                                Drafts.addDraft(editText.getText().toString());
                                Snackbar s =
                                        Snackbar.make(
                                                baseView.findViewById(R.id.savedraft),
                                                "Draft saved",
                                                Snackbar.LENGTH_SHORT);
                                View view = s.getView();
                                TextView tv =
                                        view.findViewById(
                                                com.google.android.material.R.id.snackbar_text);
                                tv.setTextColor(Color.WHITE);
                                s.setAction(
                                        R.string.btn_discard,
                                        new View.OnClickListener() {
                                            @Override
                                            public void onClick(View view) {
                                                Drafts.deleteDraft(Drafts.getDrafts().size() - 1);
                                            }
                                        });
                                LayoutUtils.showSnackbar(s);
                            }
                        });
        baseView.findViewById(R.id.draft)
                .setOnClickListener(
                        new View.OnClickListener() {
                            @Override
                            public void onClick(View v) {
                                final ArrayList<String> drafts = Drafts.getDrafts();
                                Collections.reverse(drafts);
                                final String[] draftText = new String[drafts.size()];
                                for (int i = 0; i < drafts.size(); i++) {
                                    draftText[i] = drafts.get(i);
                                }
                                if (drafts.isEmpty()) {
                                    final AlertDialog noDrafts =
                                            new AlertDialog.Builder(a)
                                                    .setTitle(R.string.dialog_no_drafts)
                                                    .setMessage(R.string.dialog_no_drafts_msg)
                                                    .setPositiveButton(R.string.btn_ok, null)
                                                    .create();
                                    DialogUtil.matchDialogToCardBackground(a, noDrafts);
                                    noDrafts.show();
                                } else {
                                    DialogUtil.showWithCardBackground(new AlertDialog.Builder(a)
                                            .setTitle(R.string.choose_draft)
                                            .setItems(
                                                    draftText,
                                                    (dialog, which) ->
                                                            editText.setText(
                                                                    editText.getText().toString()
                                                                            + draftText[which]))
                                            .setNeutralButton(R.string.btn_cancel, null)
                                            .setPositiveButton(
                                                    R.string.manage_drafts,
                                                    (dialog, which) -> {
                                                        final boolean[] selected =
                                                                new boolean[drafts.size()];
                                                        DialogUtil.showWithCardBackground(new AlertDialog.Builder(a)
                                                                .setTitle(R.string.choose_draft)
                                                                .setNeutralButton(
                                                                        R.string.btn_cancel, null)
                                                                .setNegativeButton(
                                                                        R.string.btn_delete,
                                                                        (dialog1, which1) ->
                                                                                new AlertDialog
                                                                                                .Builder(
                                                                                                a)
                                                                                        .setTitle(
                                                                                                R
                                                                                                        .string
                                                                                                        .really_delete_drafts)
                                                                                        .setCancelable(
                                                                                                false)
                                                                                        .setPositiveButton(
                                                                                                R
                                                                                                        .string
                                                                                                        .btn_yes,
                                                                                                (dialog11,
                                                                                                        which11) -> {
                                                                                                    ArrayList<
                                                                                                                    String>
                                                                                                            draf =
                                                                                                                    new ArrayList<>();
                                                                                                    for (int
                                                                                                                    i =
                                                                                                                            0;
                                                                                                            i
                                                                                                                    < draftText
                                                                                                                            .length;
                                                                                                            i++) {
                                                                                                        if (!selected[
                                                                                                                i]) {
                                                                                                            draf
                                                                                                                    .add(
                                                                                                                            draftText[
                                                                                                                                    i]);
                                                                                                        }
                                                                                                    }
                                                                                                    Drafts
                                                                                                            .save(
                                                                                                                    draf);
                                                                                                })
                                                                                        .setNegativeButton(
                                                                                                R
                                                                                                        .string
                                                                                                        .btn_no,
                                                                                                null)
                                                                                        .show())
                                                                .setMultiChoiceItems(
                                                                        draftText,
                                                                        selected,
                                                                        (dialog12,
                                                                                which12,
                                                                                isChecked) ->
                                                                                selected[which12] =
                                                                                        isChecked)
                                                                );
                                                    })
                                            );
                                }
                            }
                        });
        final View imageButton = baseView.findViewById(R.id.imagerep);
        imageButtons.put(editText, imageButton);
        // Reset the button state for reused editors: comments are limited to one inline Reddit
        // image, so the button is disabled once one has been added.
        setImageButtonEnabled(
                imageButton,
                editText.getId() == R.id.bodytext
                        || me.edgan.redditslide.util.RedditImageUploads.get(editText).isEmpty());
        imageButton.setOnClickListener(
                v -> {
                    e = editText.getText();
                    sStart = editText.getSelectionStart();
                    sEnd = editText.getSelectionEnd();
                    currentImageTarget = editText;

                    showImageUploadChoice(
                            a,
                            () -> {
                                uploadToReddit = false;
                                launchImagePicker(editText, a, imageLauncher);
                            },
                            () -> {
                                uploadToReddit = true;
                                launchImagePicker(editText, a, imageLauncher);
                            });
                });
        baseView.findViewById(R.id.draw)
                .setOnClickListener(
                        new View.OnClickListener() {
                            @Override
                            public void onClick(View v) {
                                doDraw(a, editText, fm);
                            }
                        });
        /*todo baseView.findViewById(R.id.superscript).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                insertBefore("^", editText);
            }
        });*/
        baseView.findViewById(R.id.size)
                .setOnClickListener(
                        new View.OnClickListener() {
                            @Override
                            public void onClick(View v) {
                                insertBefore("#", editText);
                            }
                        });

        baseView.findViewById(R.id.quote)
                .setOnClickListener(
                        new View.OnClickListener() {
                            @Override
                            public void onClick(View v) {

                                if (oldComment != null) {
                                    final TextView showText = new TextView(a);
                                    showText.setText(
                                            StringEscapeUtils.unescapeHtml4(
                                                    oldComment)); // text we get is escaped, we
                                    // don't want that
                                    showText.setTextIsSelectable(true);
                                    TranslateUtil.addToSelectionMenu(showText);
                                    int sixteen = DisplayUtil.dpToPxVertical(24);
                                    showText.setPadding(sixteen, 0, sixteen, 0);
                                    MaterialAlertDialogBuilder builder =
                                            new MaterialAlertDialogBuilder(
                                                    new ContextThemeWrapper(
                                                            a,
                                                            new ColorPreferences(a)
                                                                    .getFontStyle()
                                                                    .getBaseId()));
                                    builder.setView(showText)
                                            .setTitle(R.string.editor_actions_quote_comment)
                                            .setCancelable(true)
                                            .setPositiveButton(
                                                    a.getString(R.string.btn_select),
                                                    new DialogInterface.OnClickListener() {
                                                        @Override
                                                        public void onClick(
                                                                DialogInterface dialog,
                                                                int which) {
                                                            String selected =
                                                                    showText.getText()
                                                                            .toString()
                                                                            .substring(
                                                                                    showText
                                                                                            .getSelectionStart(),
                                                                                    showText
                                                                                            .getSelectionEnd());
                                                            if (selected.isEmpty()) {
                                                                selected =
                                                                        StringEscapeUtils
                                                                                .unescapeHtml4(
                                                                                        oldComment);
                                                            }
                                                            insertBefore(
                                                                    "> "
                                                                            + selected.replaceAll(
                                                                                    "\n", "\n> ")
                                                                            + "\n\n",
                                                                    editText);
                                                        }
                                                    })
                                            .setNegativeButton(a.getString(R.string.btn_cancel), null)
                                            .show();
                                    KeyboardUtil.hideKeyboard(
                                            editText.getContext(), editText.getWindowToken(), 0);
                                } else {
                                    insertBefore("> ", editText);
                                }
                            }
                        });

        baseView.findViewById(R.id.bulletlist)
                .setOnClickListener(
                        new View.OnClickListener() {
                            @Override
                            public void onClick(View v) {
                                int start = editText.getSelectionStart();
                                int end = editText.getSelectionEnd();
                                String selected =
                                        editText.getText()
                                                .toString()
                                                .substring(
                                                        Math.min(start, end), Math.max(start, end));
                                if (!selected.isEmpty()) {
                                    selected =
                                            selected.replaceFirst("^[^\n]", "* $0")
                                                    .replaceAll("\n", "\n* ");
                                    editText.getText()
                                            .replace(
                                                    Math.min(start, end),
                                                    Math.max(start, end),
                                                    selected);
                                } else {
                                    insertBefore("* ", editText);
                                }
                            }
                        });

        baseView.findViewById(R.id.numlist)
                .setOnClickListener(
                        new View.OnClickListener() {
                            @Override
                            public void onClick(View v) {
                                int start = editText.getSelectionStart();
                                int end = editText.getSelectionEnd();
                                String selected =
                                        editText.getText()
                                                .toString()
                                                .substring(
                                                        Math.min(start, end), Math.max(start, end));
                                if (!selected.isEmpty()) {
                                    selected =
                                            selected.replaceFirst("^[^\n]", "1. $0")
                                                    .replaceAll("\n", "\n1. ");
                                    editText.getText()
                                            .replace(
                                                    Math.min(start, end),
                                                    Math.max(start, end),
                                                    selected);
                                } else {
                                    insertBefore("1. ", editText);
                                }
                            }
                        });

        baseView.findViewById(R.id.preview)
                .setOnClickListener(
                        new View.OnClickListener() {
                            @Override
                            public void onClick(View v) {
                                LayoutInflater inflater = a.getLayoutInflater();
                                final View dialoglayout =
                                        inflater.inflate(R.layout.parent_comment_dialog, null);
                                final SpoilerRobotoTextView firstTextView =
                                        dialoglayout.findViewById(R.id.firstTextView);

                                // Preview through whichever renderer the post/comment will actually
                                // use, so the preview matches the result (issue #179). The Markwon
                                // path is the same one MarkdownImages.renderInto drives for live
                                // new-Reddit comments/self-text.
                                if (SettingValues.markdownNewReddit) {
                                    firstTextView.setVisibility(View.VISIBLE);
                                    RedditMarkwon.setMarkdown(
                                            firstTextView, "NO sub", editText.getText().toString());
                                } else {
                                    List<Extension> extensions =
                                            Arrays.asList(
                                                    TablesExtension.create(),
                                                    StrikethroughExtension.create());
                                    Parser parser =
                                            Parser.builder().extensions(extensions).build();
                                    HtmlRenderer renderer =
                                            HtmlRenderer.builder().extensions(extensions).build();
                                    Node document = parser.parse(editText.getText().toString());
                                    String html = renderer.render(document);
                                    // Process Reddit-style superscript (^text)
                                    html = processSuperscript(html);
                                    setViews(
                                            html,
                                            "NO sub",
                                            firstTextView,
                                            dialoglayout.findViewById(R.id.commentOverflow));
                                }

                                DialogUtil.showWithCardBackground(new AlertDialog.Builder(a).setView(dialoglayout));
                            }
                        });

        baseView.findViewById(R.id.link)
                .setOnClickListener(
                        new View.OnClickListener() {
                            @Override
                            public void onClick(View v) {
                                final LayoutInflater inflater = LayoutInflater.from(a);
                                final LinearLayout layout =
                                        (LinearLayout) inflater.inflate(R.layout.insert_link, null);

                                String selectedText = "";
                                // if the user highlighted text before inputting a URL, use that
                                // text for the descriptionBox
                                if (editText.hasSelection()) {
                                    final int startSelection = editText.getSelectionStart();
                                    final int endSelection = editText.getSelectionEnd();

                                    selectedText =
                                            editText.getText()
                                                    .toString()
                                                    .substring(startSelection, endSelection);
                                }

                                final boolean selectedTextNotEmpty = !selectedText.isEmpty();

                                final AlertDialog dialog =
                                        new MaterialAlertDialogBuilder(
                                                        new ContextThemeWrapper(
                                                                editText.getContext(),
                                                                new ColorPreferences(
                                                                                editText.getContext())
                                                                        .getFontStyle()
                                                                        .getBaseId()))
                                                .setTitle(R.string.editor_title_link)
                                                .setView(layout)
                                                .setPositiveButton(
                                                        R.string.editor_action_link,
                                                        new DialogInterface.OnClickListener() {
                                                            @Override
                                                            public void onClick(
                                                                    DialogInterface dialog,
                                                                    int which) {
                                                                final EditText urlBox =
                                                                        (EditText)
                                                                                layout.findViewById(
                                                                                        R.id
                                                                                                .url_box);
                                                                final EditText textBox =
                                                                        (EditText)
                                                                                layout.findViewById(
                                                                                        R.id
                                                                                                .text_box);
                                                                dialog.dismiss();

                                                                final String s =
                                                                        "["
                                                                                + textBox.getText()
                                                                                        .toString()
                                                                                + "]("
                                                                                + urlBox.getText()
                                                                                        .toString()
                                                                                + ")";

                                                                int start =
                                                                        Math.max(
                                                                                editText
                                                                                        .getSelectionStart(),
                                                                                0);
                                                                int end =
                                                                        Math.max(
                                                                                editText
                                                                                        .getSelectionEnd(),
                                                                                0);

                                                                editText.getText()
                                                                        .insert(
                                                                                Math.max(
                                                                                        start, end),
                                                                                s);

                                                                // delete the selected text to avoid
                                                                // duplication
                                                                if (selectedTextNotEmpty) {
                                                                    editText.getText()
                                                                            .delete(start, end);
                                                                }
                                                            }
                                                        })
                                                .create();

                                // Tint the hint text if the base theme is Sepia
                                if (SettingValues.currentTheme == 5) {
                                    ((EditText) layout.findViewById(R.id.url_box))
                                            .setHintTextColor(
                                                    ContextCompat.getColor(
                                                            layout.getContext(),
                                                            R.color.md_grey_600));
                                    ((EditText) layout.findViewById(R.id.text_box))
                                            .setHintTextColor(
                                                    ContextCompat.getColor(
                                                            layout.getContext(),
                                                            R.color.md_grey_600));
                                }

                                // use the selected text as the text for the link
                                if (!selectedText.isEmpty()) {
                                    ((EditText) layout.findViewById(R.id.text_box))
                                            .setText(selectedText);
                                }

                                dialog.show();
                            }
                        });

        try {
            ((ImageInsertEditText) editText)
                    .setImageSelectedCallback(
                            new ImageInsertEditText.ImageSelectedCallback() {
                                @Override
                                public void onImageSelected(final Uri content, String mimeType) {
                                    e = editText.getText();

                                    sStart = editText.getSelectionStart();
                                    sEnd = editText.getSelectionEnd();
                                    handleImageIntent(
                                            new ArrayList<Uri>() {
                                                {
                                                    add(content);
                                                }
                                            },
                                            editText,
                                            a);
                                }
                            });
        } catch (Exception e) {
            // if thrown, there is likely an issue implementing this on the user's version of
            // Android. There shouldn't be an issue, but just in case
        }
    }

    public static Editable e;
    public static int sStart, sEnd;
    public static EditText currentImageTarget;
    // When true, the next picked image is uploaded to Reddit (inline) instead of Imgur (link).
    public static boolean uploadToReddit;
    // The image-toolbar button per editor, so it can be greyed out after a comment's one allowed
    // Reddit image.
    private static final java.util.WeakHashMap<EditText, View> imageButtons =
            new java.util.WeakHashMap<>();

    public static void doDraw(final Activity a, final EditText editText, final FragmentManager fm) {
        final Intent intent = new Intent(a, Draw.class);
        KeyboardUtil.hideKeyboard(editText.getContext(), editText.getWindowToken(), 0);
        e = editText.getText();

        if (a instanceof ComponentActivity) {
            ComponentActivity componentActivity = (ComponentActivity) a;
            String key = "doEditorDraw_" + registryCounter.getAndIncrement();
            ActivityResultLauncher<PickVisualMediaRequest> drawLauncher =
                    componentActivity
                            .getActivityResultRegistry()
                            .register(
                                    key,
                                    new ActivityResultContracts.PickVisualMedia(),
                                    uri -> {
                                        if (uri != null) {
                                            Draw.uri = uri;
                                            Fragment auxiliary = new AuxiliaryFragment();

                                            sStart = editText.getSelectionStart();
                                            sEnd = editText.getSelectionEnd();

                                            fm.beginTransaction()
                                                    .add(auxiliary, "IMAGE_UPLOAD")
                                                    .commit();
                                            fm.executePendingTransactions();

                                            auxiliary.startActivityForResult(intent, 3333);
                                        }
                                    });
            drawLauncher.launch(
                    new PickVisualMediaRequest.Builder()
                            .setMediaType(
                                    ActivityResultContracts.PickVisualMedia.ImageOnly.INSTANCE)
                            .build());
        }
    }

    public static class AuxiliaryFragment extends Fragment {
        @Override
        public void onActivityResult(int requestCode, int resultCode, final Intent data) {
            super.onActivityResult(requestCode, resultCode, data);
            if (data != null && data.getData() != null) {
                handleImageIntent(
                        new ArrayList<Uri>() {
                            {
                                add(data.getData());
                            }
                        },
                        e,
                        getContext());

                getActivity().getSupportFragmentManager().beginTransaction().remove(this).commit();
            }
        }
    }

    public static String getImageLink(Bitmap b) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        b.compress(
                Bitmap.CompressFormat.JPEG,
                100,
                baos); // Not sure whether this should be jpeg or png, try both and see which works
        // best
        return Base64.encodeToString(baos.toByteArray(), Base64.DEFAULT);
    }

    public static void insertBefore(String wrapText, EditText editText) {
        int start = Math.max(editText.getSelectionStart(), 0);
        int end = Math.max(editText.getSelectionEnd(), 0);
        editText.getText().insert(Math.min(start, end), wrapText);
    }

    /**
     * Wrap selected text in one or multiple characters, handling newlines and spaces properly for
     * markdown
     *
     * @param wrapText Character(s) to wrap the selected text in
     * @param editText EditText
     */
    public static void wrapString(String wrapText, EditText editText) {
        wrapString(wrapText, wrapText, editText);
    }

    /**
     * Wrap selected text in one or multiple characters, handling newlines, spaces, >s properly for
     * markdown, with different start and end text.
     *
     * @param startWrap Character(s) to start wrapping with
     * @param endWrap Character(s) to close wrapping with
     * @param editText EditText
     */
    public static void wrapString(String startWrap, String endWrap, EditText editText) {
        int start = Math.max(editText.getSelectionStart(), 0);
        int end = Math.max(editText.getSelectionEnd(), 0);
        String selected =
                editText.getText().toString().substring(Math.min(start, end), Math.max(start, end));
        // insert the wrapping character inside any selected spaces and >s because they stop
        // markdown formatting
        // we use replaceFirst because anchors (\A, \Z) aren't consumed
        selected =
                selected.replaceFirst("\\A[\\n> ]*", "$0" + startWrap)
                        .replaceFirst("[\\n> ]*\\Z", endWrap + "$0");
        // 2+ newlines stop formatting, so we do the formatting on each instance of text surrounded
        // by 2+ newlines
        /* in case anyone needs to understand this in the future:
         * ([^\n> ]) captures any character that isn't a newline, >, or space
         * (\n[> ]*){2,} captures any number of two or more newlines with any combination of spaces or >s since markdown ignores those by themselves
         * (?=[^\n> ]) performs a lookahead and ensures there's a character that isn't a newline, >, or space
         */
        selected =
                selected.replaceAll(
                        "([^\\n> ])(\\n[> ]*){2,}(?=[^\\n> ])", "$1" + endWrap + "$2" + startWrap);
        editText.getText().replace(start, end, selected);
    }

    private static void setViews(
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
            firstTextView.setTextHtml(blocks.get(0), subredditName);
            firstTextView.setLinkTextColor(
                    new ColorPreferences(firstTextView.getContext()).getColor(subredditName));
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

    private static class UploadImgurDEA extends UploadImgur {

        public UploadImgurDEA(Context c) {
            this.c = c;
            dialog =
                    new MaterialProgressDialog.Builder(c)
                            .title(c.getString(R.string.editor_uploading_image))
                            .progress(false, 100)
                            .cancelable(false)
                            .show();
        }

        @Override
        // Reading a single themed attribute (R.attr.fontColor) from a specific style via a
        // manual int[]; lint's @StyleableRes typedef is too strict for this idiom.
        @SuppressLint("ResourceType")
        protected void onPostExecute(final JSONObject result) {
            dialog.dismiss();
            try {
                int[] attrs = {R.attr.fontColor};
                TypedArray ta =
                        c.obtainStyledAttributes(
                                new ColorPreferences(c).getFontStyle().getBaseId(), attrs);
                final String url = result.getJSONObject("data").getString("link");
                LinearLayout layout = new LinearLayout(c);
                layout.setOrientation(LinearLayout.VERTICAL);

                final TextView titleBox = new TextView(c);
                titleBox.setText(url);
                layout.addView(titleBox);
                titleBox.setEnabled(false);
                titleBox.setTextColor(ta.getColor(0, Color.WHITE));

                final EditText descriptionBox = new EditText(c);
                descriptionBox.setHint(R.string.editor_title);
                descriptionBox.setEnabled(true);
                descriptionBox.setTextColor(ta.getColor(0, Color.WHITE));
                KeyboardUtil.toggleKeyboard(
                        c, InputMethodManager.SHOW_FORCED, InputMethodManager.HIDE_IMPLICIT_ONLY);

                if (DoEditorActions.e != null) {
                    descriptionBox.setText(DoEditorActions.e.toString().substring(sStart, sEnd));
                }

                ta.recycle();
                int sixteen = DisplayUtil.dpToPxVertical(16);
                layout.setPadding(sixteen, sixteen, sixteen, sixteen);
                layout.addView(descriptionBox);
                final AlertDialog linkDialog =
                        new MaterialAlertDialogBuilder(
                                        new ContextThemeWrapper(
                                                c,
                                                new ColorPreferences(c).getFontStyle().getBaseId()))
                                .setTitle(R.string.editor_title_link)
                                .setView(layout)
                                .setPositiveButton(
                                        R.string.editor_action_link,
                                        new DialogInterface.OnClickListener() {
                                            @Override
                                            public void onClick(
                                                    DialogInterface dialog, int which) {
                                                dialog.dismiss();
                                                String s =
                                                        "["
                                                                + descriptionBox
                                                                        .getText()
                                                                        .toString()
                                                                + "]("
                                                                + url
                                                                + ")";
                                                if (descriptionBox
                                                        .getText()
                                                        .toString()
                                                        .trim()
                                                        .isEmpty()) {
                                                    s = url + " ";
                                                }
                                                int start = Math.max(sStart, 0);
                                                int end = Math.max(sEnd, 0);
                                                if (DoEditorActions.e != null) {
                                                    DoEditorActions.e.insert(
                                                            Math.max(start, end), s);
                                                    DoEditorActions.e.delete(start, end);
                                                    DoEditorActions.e = null;
                                                }
                                                sStart = 0;
                                                sEnd = 0;
                                            }
                                        })
                                .create();
                linkDialog.setCanceledOnTouchOutside(false);
                linkDialog.show();

            } catch (Exception e) {
                DialogUtil.showWithCardBackground(new AlertDialog.Builder(c)
                        .setTitle(R.string.err_title)
                        .setMessage(R.string.editor_err_msg)
                        .setPositiveButton(R.string.btn_ok, null)
                        );
                LogUtil.e(e, "DoEditorActions.onClick failed");
            }
        }
    }

    private static class UploadImgurAlbumDEA extends UploadImgurAlbum {

        public UploadImgurAlbumDEA(Context c) {
            this.c = c;
            dialog =
                    new MaterialProgressDialog.Builder(c)
                            .title(c.getString(R.string.editor_uploading_image))
                            .progress(false, 100)
                            .cancelable(false)
                            .show();
        }

        @Override
        // Reading a single themed attribute (R.attr.fontColor) from a specific style via a
        // manual int[]; lint's @StyleableRes typedef is too strict for this idiom.
        @SuppressLint("ResourceType")
        protected void onPostExecute(final String result) {
            dialog.dismiss();
            try {
                int[] attrs = {R.attr.fontColor};
                TypedArray ta =
                        c.obtainStyledAttributes(
                                new ColorPreferences(c).getFontStyle().getBaseId(), attrs);
                LinearLayout layout = new LinearLayout(c);
                layout.setOrientation(LinearLayout.VERTICAL);

                final TextView titleBox = new TextView(c);
                titleBox.setText(finalUrl);
                layout.addView(titleBox);
                titleBox.setEnabled(false);
                titleBox.setTextColor(ta.getColor(0, Color.WHITE));

                final EditText descriptionBox = new EditText(c);
                descriptionBox.setHint(R.string.editor_title);
                descriptionBox.setEnabled(true);
                descriptionBox.setTextColor(ta.getColor(0, Color.WHITE));

                if (DoEditorActions.e != null) {
                    descriptionBox.setText(DoEditorActions.e.toString().substring(sStart, sEnd));
                }

                ta.recycle();
                int sixteen = DisplayUtil.dpToPxVertical(16);
                layout.setPadding(sixteen, sixteen, sixteen, sixteen);
                layout.addView(descriptionBox);
                final AlertDialog linkDialog =
                        new MaterialAlertDialogBuilder(
                                        new ContextThemeWrapper(
                                                c,
                                                new ColorPreferences(c).getFontStyle().getBaseId()))
                                .setTitle(R.string.editor_title_link)
                                .setView(layout)
                                .setPositiveButton(
                                        R.string.editor_action_link,
                                        new DialogInterface.OnClickListener() {
                                            @Override
                                            public void onClick(
                                                    DialogInterface dialog, int which) {
                                                dialog.dismiss();
                                                String s =
                                                        "["
                                                                + descriptionBox
                                                                        .getText()
                                                                        .toString()
                                                                + "]("
                                                                + finalUrl
                                                                + ")";
                                                int start = Math.max(sStart, 0);
                                                int end = Math.max(sEnd, 0);
                                                DoEditorActions.e.insert(Math.max(start, end), s);
                                                DoEditorActions.e.delete(start, end);
                                                DoEditorActions.e = null;
                                                sStart = 0;
                                                sEnd = 0;
                                            }
                                        })
                                .create();
                linkDialog.setCanceledOnTouchOutside(false);
                linkDialog.show();

            } catch (Exception e) {
                DialogUtil.showWithCardBackground(new AlertDialog.Builder(c)
                        .setTitle(R.string.err_title)
                        .setMessage(R.string.editor_err_msg)
                        .setPositiveButton(R.string.btn_ok, null)
                        );
                LogUtil.e(e, "DoEditorActions.onClick failed");
            }
        }
    }

    public static void handleImageIntent(List<Uri> uris, EditText ed, Context c) {
        if (uploadToReddit) {
            uploadToReddit = false;
            uploadImageToReddit(uris, ed, c);
        } else {
            handleImageIntent(uris, ed.getText(), c);
        }
    }

    /**
     * Ask the user whether the selected image should be uploaded to Reddit (rendered inline) or to
     * Imgur (inserted as a link). When the user is logged out, Reddit upload is not possible so we
     * go straight to Imgur.
     */
    private static void showImageUploadChoice(
            final Activity a, final Runnable onImgur, final Runnable onReddit) {
        if (!Authentication.isLoggedIn
                || Authentication.name == null
                || Authentication.name.equalsIgnoreCase("LOGGEDOUT")) {
            onImgur.run();
            return;
        }

        new MaterialAlertDialogBuilder(
                        new ContextThemeWrapper(
                                a, new ColorPreferences(a).getFontStyle().getBaseId()))
                .setTitle(R.string.editor_upload_image_title)
                .setItems(
                        new CharSequence[] {
                            a.getString(R.string.editor_upload_reddit),
                            a.getString(R.string.editor_upload_imgur)
                        },
                        (dialog, which) -> {
                            if (which == 0) {
                                onReddit.run();
                            } else {
                                onImgur.run();
                            }
                        })
                .setNegativeButton(R.string.btn_cancel, null)
                .show();
    }

    private static void launchImagePicker(
            final EditText editText,
            final Activity a,
            @Nullable final ActivityResultLauncher<PickVisualMediaRequest> imageLauncher) {
        if (imageLauncher != null) {
            imageLauncher.launch(
                    new PickVisualMediaRequest.Builder()
                            .setMediaType(
                                    ActivityResultContracts.PickVisualMedia.ImageOnly.INSTANCE)
                            .build());
        } else if (a instanceof ComponentActivity) {
            ComponentActivity componentActivity = (ComponentActivity) a;
            String key = "doEditorImage_" + registryCounter.getAndIncrement();
            ActivityResultLauncher<PickVisualMediaRequest> launcher =
                    componentActivity
                            .getActivityResultRegistry()
                            .register(
                                    key,
                                    new ActivityResultContracts.PickVisualMedia(),
                                    uri -> {
                                        if (uri != null) {
                                            ArrayList<Uri> uriList = new ArrayList<>();
                                            uriList.add(uri);
                                            handleImageIntent(
                                                    uriList, editText, editText.getContext());
                                            KeyboardUtil.hideKeyboard(
                                                    editText.getContext(),
                                                    editText.getWindowToken(),
                                                    0);
                                        }
                                    });
            launcher.launch(
                    new PickVisualMediaRequest.Builder()
                            .setMediaType(
                                    ActivityResultContracts.PickVisualMedia.ImageOnly.INSTANCE)
                            .build());
        }
    }

    public static void uploadImageToReddit(List<Uri> uris, EditText editText, Context c) {
        if (uris.isEmpty()) {
            return;
        }
        try {
            new UploadRedditImageTask(c, editText).execute(uris.get(0));
        } catch (Exception e) {
            LogUtil.e(e, "DoEditorActions.uploadImageToReddit failed");
        }
    }

    private static void setImageButtonEnabled(View v, boolean enabled) {
        if (v == null) {
            return;
        }
        v.setEnabled(enabled);
        v.setAlpha(enabled ? 1f : 0.4f);
    }

    /** Inserts a Reddit-uploaded image reference at the editor's cursor, newline-aware. */
    private static void insertRedditImage(EditText editText, String key) {
        int start = Math.max(editText.getSelectionStart(), 0);
        int end = Math.max(editText.getSelectionEnd(), 0);
        int realStart = Math.min(start, end);

        String insert;
        if (realStart > 0
                && editText.getText().toString().charAt(realStart - 1) != '\n') {
            insert = "\n![](" + key + ")\n";
        } else {
            insert = "![](" + key + ")\n";
        }
        editText.getText().replace(realStart, Math.max(start, end), insert);
    }

    private static class UploadRedditImageTask extends android.os.AsyncTask<Uri, Void, Object> {
        private final Context c;
        private final EditText editText;
        private MaterialProgressDialog dialog;

        UploadRedditImageTask(Context c, EditText editText) {
            this.c = c;
            this.editText = editText;
        }

        @Override
        protected void onPreExecute() {
            dialog =
                    new MaterialProgressDialog.Builder(c)
                            .title(c.getString(R.string.editor_uploading_reddit))
                            .progress(true, 0)
                            .cancelable(false)
                            .show();
        }

        @Override
        protected Object doInBackground(Uri... uris) {
            try {
                return me.edgan.redditslide.util.RedditMediaUpload.upload(c, uris[0]);
            } catch (Exception e) {
                LogUtil.e(e, "RedditMediaUpload failed");
                return e;
            }
        }

        @Override
        protected void onPostExecute(Object result) {
            if (dialog != null) {
                dialog.dismiss();
            }
            if (result instanceof me.edgan.redditslide.markdown.UploadedImage) {
                me.edgan.redditslide.markdown.UploadedImage image =
                        (me.edgan.redditslide.markdown.UploadedImage) result;
                insertRedditImage(editText, image.imageUrlOrKey);
                me.edgan.redditslide.util.RedditImageUploads.add(editText, image);
                // Comments allow only one inline Reddit image; grey out the button after the first.
                if (editText.getId() != R.id.bodytext) {
                    setImageButtonEnabled(imageButtons.get(editText), false);
                }
                Toast.makeText(c, R.string.editor_reddit_upload_success, Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(c, R.string.editor_reddit_upload_failed, Toast.LENGTH_LONG).show();
            }
        }
    }

    public static void handleImageIntent(List<Uri> uris, Editable ed, Context c) {
        if (uris.size() == 1) {
            // Get the Image from data (single image)
            try {
                new UploadImgurDEA(c).execute(uris.get(0));
            } catch (Exception e) {
                LogUtil.e(e, "DoEditorActions.handleImageIntent failed");
            }
        } else {
            // Multiple images
            try {
                new UploadImgurAlbumDEA(c).execute(uris.toArray(new Uri[0]));
            } catch (Exception e) {
                LogUtil.e(e, "DoEditorActions.handleImageIntent failed");
            }
        }
    }

    /**
     * Processes Reddit-style superscript formatting (^text) and converts it to HTML.
     * Matches text that starts with ^ and continues until whitespace or end of line.
     */
    private static String processSuperscript(String html) {
        // Pattern to match ^text (caret followed by text until whitespace or end)
        // This matches Reddit's superscript behavior
        return html.replaceAll("\\^([^\\s]+)", "<sup><small>$1</small></sup>");
    }
}
