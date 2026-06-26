package me.edgan.redditslide.Adapters;

import android.app.Activity;
import android.os.AsyncTask;
import android.view.View;

import com.google.android.material.snackbar.Snackbar;

import me.edgan.redditslide.ActionStates;
import me.edgan.redditslide.Authentication;
import me.edgan.redditslide.R;
import me.edgan.redditslide.SubmissionViews.LocalSaved;
import me.edgan.redditslide.util.LayoutUtils;

import net.dean.jraw.managers.AccountManager;
import net.dean.jraw.models.Submission;

public class AsyncSave extends AsyncTask<Submission, Void, Void> {
    final Activity mContext;
    View v;

    public AsyncSave(Activity mContext, View v) {
        this.mContext = mContext;
        this.v = v;
    }

    @Override
    protected Void doInBackground(Submission... submissions) {
        try {
            boolean subSaved = ActionStates.isSaved(submissions[0]);
            final Snackbar s;

            if (subSaved) {
                new AccountManager(Authentication.reddit).unsave(submissions[0]);
                LocalSaved.onUnsaved(submissions[0]);
                s = Snackbar.make(v, R.string.submission_info_unsaved, Snackbar.LENGTH_SHORT);
            } else {
                new AccountManager(Authentication.reddit).save(submissions[0]);
                LocalSaved.onSaved(submissions[0]);
                s = Snackbar.make(v, R.string.submission_info_saved, Snackbar.LENGTH_SHORT);
            }
            runUiThread(s);
            submissions[0].saved = !subSaved;
            v = null;

        } catch (Exception e) {
            return null;
        }
        return null;
    }

    private void runUiThread(final Snackbar s) {
        mContext.runOnUiThread(() -> LayoutUtils.showSnackbar(s));
    }
}
