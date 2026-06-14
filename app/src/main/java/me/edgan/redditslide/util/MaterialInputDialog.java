package me.edgan.redditslide.util;

import android.content.Context;
import android.content.DialogInterface;
import android.text.InputType;
import android.util.TypedValue;
import android.view.ContextThemeWrapper;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;

import androidx.appcompat.app.AlertDialog;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputLayout;

import me.edgan.redditslide.Visuals.ColorPreferences;
import me.edgan.redditslide.util.stubs.SimpleTextWatcher;

/**
 * Lightweight replacement for the deprecated afollestad {@code MaterialDialog} text-input dialogs.
 *
 * <p>The {@link Builder} mirrors the afollestad input-dialog builder ({@code .title().input()
 * .inputRange().positiveText().onPositive()...}) and the instance exposes {@link #getInputEditText()}
 * and {@link #getActionButton(int)} so call sites migrate with minimal changes. Backed by a {@link
 * com.google.android.material.dialog.MaterialAlertDialogBuilder} wrapping a {@link TextInputLayout}.
 */
public class MaterialInputDialog {

    /** Invoked (after show, then on every text change) with the current input. */
    public interface InputCallback {
        void onInput(MaterialInputDialog dialog, CharSequence input);
    }

    /** Invoked when a dialog button is clicked. */
    public interface ButtonCallback {
        void onClick(MaterialInputDialog dialog);
    }

    private final AlertDialog dialog;
    private final EditText editText;

    private MaterialInputDialog(AlertDialog dialog, EditText editText) {
        this.dialog = dialog;
        this.editText = editText;
    }

    public EditText getInputEditText() {
        return editText;
    }

    /** @param which one of {@link DialogInterface#BUTTON_POSITIVE} etc. */
    public Button getActionButton(int which) {
        return dialog.getButton(which);
    }

    public void dismiss() {
        dialog.dismiss();
    }

    public AlertDialog getDialog() {
        return dialog;
    }

    public static class Builder {
        private final Context context;
        private CharSequence title;
        private CharSequence content;
        private CharSequence hint;
        private CharSequence prefill;
        private int inputType = InputType.TYPE_CLASS_TEXT;
        private int minLength = 0;
        private int maxLength = -1;
        private InputCallback inputCallback;
        private CharSequence positiveText;
        private CharSequence negativeText;
        private CharSequence neutralText;
        private ButtonCallback onPositive;
        private ButtonCallback onNegative;
        private ButtonCallback onNeutral;
        private boolean cancelable = true;
        private boolean autoDismiss = true;

        public Builder(Context base) {
            this.context =
                    new ContextThemeWrapper(
                            base, new ColorPreferences(base).getFontStyle().getBaseId());
        }

        public Builder title(CharSequence t) {
            this.title = t;
            return this;
        }

        public Builder title(int res) {
            this.title = context.getString(res);
            return this;
        }

        public Builder content(CharSequence c) {
            this.content = c;
            return this;
        }

        public Builder content(int res) {
            this.content = context.getString(res);
            return this;
        }

        public Builder inputType(int inputType) {
            this.inputType = inputType;
            return this;
        }

        public Builder inputRange(int min, int max) {
            this.minLength = min;
            this.maxLength = max;
            return this;
        }

        public Builder input(CharSequence hint, CharSequence prefill, InputCallback callback) {
            this.hint = hint;
            this.prefill = prefill;
            this.inputCallback = callback;
            return this;
        }

        public Builder positiveText(CharSequence t) {
            this.positiveText = t;
            return this;
        }

        public Builder positiveText(int res) {
            this.positiveText = context.getString(res);
            return this;
        }

        public Builder onPositive(ButtonCallback cb) {
            this.onPositive = cb;
            return this;
        }

        public Builder negativeText(CharSequence t) {
            this.negativeText = t;
            return this;
        }

        public Builder negativeText(int res) {
            this.negativeText = context.getString(res);
            return this;
        }

        public Builder onNegative(ButtonCallback cb) {
            this.onNegative = cb;
            return this;
        }

        public Builder neutralText(CharSequence t) {
            this.neutralText = t;
            return this;
        }

        public Builder neutralText(int res) {
            this.neutralText = context.getString(res);
            return this;
        }

        public Builder onNeutral(ButtonCallback cb) {
            this.onNeutral = cb;
            return this;
        }

        public Builder cancelable(boolean cancelable) {
            this.cancelable = cancelable;
            return this;
        }

        public Builder autoDismiss(boolean autoDismiss) {
            this.autoDismiss = autoDismiss;
            return this;
        }

        public MaterialInputDialog build() {
            final EditText editText = new EditText(context);
            editText.setInputType(inputType);
            editText.setSingleLine(true);
            if (hint != null) {
                editText.setHint(hint);
            }
            if (prefill != null) {
                editText.setText(prefill);
                editText.setSelection(prefill.length());
            }

            final TextInputLayout inputLayout = new TextInputLayout(context);
            inputLayout.setErrorIconDrawable(null);
            inputLayout.addView(editText);

            final FrameLayout container = new FrameLayout(context);
            final int padding =
                    (int)
                            TypedValue.applyDimension(
                                    TypedValue.COMPLEX_UNIT_DIP,
                                    24,
                                    context.getResources().getDisplayMetrics());
            container.setPadding(padding, 0, padding, 0);
            container.addView(
                    inputLayout,
                    new FrameLayout.LayoutParams(
                            FrameLayout.LayoutParams.MATCH_PARENT,
                            FrameLayout.LayoutParams.WRAP_CONTENT));

            final MaterialAlertDialogBuilder builder =
                    new MaterialAlertDialogBuilder(context)
                            .setView(container)
                            .setCancelable(cancelable);
            if (title != null) {
                builder.setTitle(title);
            }
            if (content != null) {
                builder.setMessage(content);
            }
            if (positiveText != null) {
                builder.setPositiveButton(positiveText, null);
            }
            if (negativeText != null) {
                builder.setNegativeButton(negativeText, null);
            }
            if (neutralText != null) {
                builder.setNeutralButton(neutralText, null);
            }

            final AlertDialog alertDialog = builder.create();
            final MaterialInputDialog inputDialog = new MaterialInputDialog(alertDialog, editText);

            alertDialog.setOnShowListener(
                    d -> {
                        final Button positive =
                                alertDialog.getButton(DialogInterface.BUTTON_POSITIVE);
                        final Button negative =
                                alertDialog.getButton(DialogInterface.BUTTON_NEGATIVE);
                        final Button neutral =
                                alertDialog.getButton(DialogInterface.BUTTON_NEUTRAL);

                        final Runnable validate =
                                () -> {
                                    if (positive != null) {
                                        final int len = editText.getText().length();
                                        final boolean ok =
                                                len >= minLength
                                                        && (maxLength < 0 || len <= maxLength);
                                        positive.setEnabled(ok);
                                    }
                                };
                        validate.run();
                        if (inputCallback != null) {
                            inputCallback.onInput(inputDialog, editText.getText());
                        }

                        editText.addTextChangedListener(
                                new SimpleTextWatcher() {
                                    @Override
                                    public void afterTextChanged(android.text.Editable s) {
                                        validate.run();
                                        if (inputCallback != null) {
                                            inputCallback.onInput(inputDialog, s);
                                        }
                                    }
                                });

                        if (positive != null) {
                            positive.setOnClickListener(
                                    v -> {
                                        if (onPositive != null) {
                                            onPositive.onClick(inputDialog);
                                        }
                                        if (autoDismiss) {
                                            alertDialog.dismiss();
                                        }
                                    });
                        }
                        if (negative != null) {
                            negative.setOnClickListener(
                                    v -> {
                                        if (onNegative != null) {
                                            onNegative.onClick(inputDialog);
                                        }
                                        if (autoDismiss) {
                                            alertDialog.dismiss();
                                        }
                                    });
                        }
                        if (neutral != null) {
                            neutral.setOnClickListener(
                                    v -> {
                                        if (onNeutral != null) {
                                            onNeutral.onClick(inputDialog);
                                        }
                                        if (autoDismiss) {
                                            alertDialog.dismiss();
                                        }
                                    });
                        }
                    });

            return inputDialog;
        }

        public MaterialInputDialog show() {
            final MaterialInputDialog inputDialog = build();
            inputDialog.dialog.show();
            return inputDialog;
        }
    }
}
