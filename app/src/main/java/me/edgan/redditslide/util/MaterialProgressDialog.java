package me.edgan.redditslide.util;

import android.content.Context;
import android.content.DialogInterface;
import android.view.ContextThemeWrapper;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import me.edgan.redditslide.R;
import me.edgan.redditslide.Visuals.ColorPreferences;

/**
 * Lightweight replacement for the deprecated afollestad {@code MaterialDialog} progress dialogs.
 *
 * <p>The {@link Builder} mirrors the afollestad builder chain ({@code .title().content()
 * .progress().cancelable().show()}) so progress call sites migrate with minimal changes, and the
 * instance exposes the {@code setProgress}/{@code getCurrentProgress}/{@code setContent}/
 * {@code setMaxProgress} methods those sites relied on. Backed by a {@link
 * com.google.android.material.dialog.MaterialAlertDialogBuilder} and {@code R.layout.dialog_progress}.
 */
public class MaterialProgressDialog {
    private final AlertDialog dialog;
    private final ProgressBar horizontal;
    private final TextView text;

    private MaterialProgressDialog(AlertDialog dialog, ProgressBar horizontal, TextView text) {
        this.dialog = dialog;
        this.horizontal = horizontal;
        this.text = text;
    }

    public static class Builder {
        private final Context context;
        private CharSequence title;
        private CharSequence content;
        private boolean indeterminate = true;
        private int max = 0;
        private boolean cancelable = true;
        private boolean canceledOnTouchOutside = true;
        private DialogInterface.OnShowListener showListener;

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

        public Builder progress(boolean indeterminate, int max) {
            this.indeterminate = indeterminate;
            this.max = max;
            return this;
        }

        public Builder cancelable(boolean cancelable) {
            this.cancelable = cancelable;
            return this;
        }

        public Builder canceledOnTouchOutside(boolean canceledOnTouchOutside) {
            this.canceledOnTouchOutside = canceledOnTouchOutside;
            return this;
        }

        public Builder showListener(DialogInterface.OnShowListener listener) {
            this.showListener = listener;
            return this;
        }

        public MaterialProgressDialog build() {
            final View view =
                    LayoutInflater.from(context).inflate(R.layout.dialog_progress, null);
            final ProgressBar circular = view.findViewById(R.id.progress_bar);
            final ProgressBar horizontal = view.findViewById(R.id.progress_bar_horizontal);
            final TextView text = view.findViewById(R.id.progress_text);

            if (indeterminate) {
                horizontal.setVisibility(View.GONE);
            } else {
                circular.setVisibility(View.GONE);
                horizontal.setVisibility(View.VISIBLE);
                horizontal.setMax(max);
            }

            if (content != null) {
                text.setText(content);
            } else {
                text.setVisibility(View.GONE);
            }

            final MaterialAlertDialogBuilder builder =
                    new MaterialAlertDialogBuilder(context)
                            .setView(view)
                            .setCancelable(cancelable);
            if (title != null) {
                builder.setTitle(title);
            }

            final AlertDialog dialog = builder.create();
            dialog.setCanceledOnTouchOutside(canceledOnTouchOutside);
            if (showListener != null) {
                dialog.setOnShowListener(showListener);
            }
            return new MaterialProgressDialog(dialog, horizontal, text);
        }

        public MaterialProgressDialog show() {
            final MaterialProgressDialog d = build();
            d.show();
            return d;
        }
    }

    public void show() {
        dialog.show();
    }

    public void dismiss() {
        dialog.dismiss();
    }

    public boolean isShowing() {
        return dialog.isShowing();
    }

    public void setProgress(int progress) {
        horizontal.setProgress(progress);
    }

    public void incrementProgress(int by) {
        horizontal.setProgress(horizontal.getProgress() + by);
    }

    public int getCurrentProgress() {
        return horizontal.getProgress();
    }

    public void setMaxProgress(int max) {
        horizontal.setMax(max);
    }

    public int getMaxProgress() {
        return horizontal.getMax();
    }

    public void setContent(CharSequence content) {
        text.setVisibility(View.VISIBLE);
        text.setText(content);
    }

    public void setTitle(CharSequence title) {
        dialog.setTitle(title);
    }

    public AlertDialog getDialog() {
        return dialog;
    }
}
