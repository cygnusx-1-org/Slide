package me.edgan.redditslide.Activities;

import android.os.Bundle;
import android.view.View;
import android.view.Window;
import android.widget.HorizontalScrollView;

import androidx.appcompat.widget.AppCompatButton;

import me.edgan.redditslide.OpenRedditLink;
import me.edgan.redditslide.R;
import me.edgan.redditslide.Reddit;
import me.edgan.redditslide.SpoilerRobotoTextView;
import me.edgan.redditslide.Views.CommentOverflow;
import me.edgan.redditslide.Views.SidebarLayout;
import me.edgan.redditslide.Views.TitleTextView;
import me.edgan.redditslide.Visuals.ColorPreferences;
import me.edgan.redditslide.util.MiscUtil;
import me.edgan.redditslide.util.SubmissionParser;

import java.util.List;

public class Announcement extends BaseActivity {

    @Override
    public void finish() {
        super.finish();
        overridePendingTransition(0, 0);
    }

    @Override
    public void onCreate(Bundle savedInstance) {
        overridePendingTransition(R.anim.fade_in_real, 0);
        disableSwipeBackLayout();
        applyColorTheme();
        setTheme(R.style.popup);
        supportRequestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        super.onCreate(savedInstance);
        setContentView(R.layout.submission_dialog);
        MiscUtil.setupOldSwipeModeBackground(this, getWindow().getDecorView());

        SpoilerRobotoTextView spoilerRobotoTextView = (SpoilerRobotoTextView) findViewById(R.id.submission_dialog_firstTextView);
        CommentOverflow commentOverflow = (CommentOverflow) findViewById(R.id.submission_dialog_commentOverflow);
        TitleTextView titleTextView = (TitleTextView) findViewById(R.id.submission_dialog_title);
        AppCompatButton okBtn = (AppCompatButton) findViewById(R.id.submission_dialog_ok);
        AppCompatButton commentsBtn = (AppCompatButton) findViewById(R.id.submission_dialog_comments);

        setViews(
                Reddit.appRestart.getString("page", ""),
                "NO SUB",
                spoilerRobotoTextView,
                commentOverflow);
        titleTextView.setText(Reddit.appRestart.getString("title", ""));

        okBtn.setOnClickListener(v -> finish());

        commentsBtn.setOnClickListener(
                v -> {
                    OpenRedditLink.openUrl(Announcement.this, Reddit.appRestart.getString("url", ""), true);
                    finish();
                });
    }

    private void setViews(String rawHTML, String subredditName, SpoilerRobotoTextView firstTextView, CommentOverflow commentOverflow) {
        if (rawHTML.isEmpty()) {
            return;
        }

        List<String> blocks = SubmissionParser.getBlocks(rawHTML);

        int startIndex = 0;
        // the <div class="md"> case is when the body contains a table or code block first
        if (!blocks.get(0).equals("<div class=\"md\">")) {
            firstTextView.setVisibility(View.VISIBLE);
            firstTextView.setTextHtml(blocks.get(0), subredditName);
            firstTextView.setLinkTextColor(new ColorPreferences(this).getColor(subredditName));
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
            SidebarLayout sidebar =
                    (SidebarLayout) findViewById(R.id.submission_dialog_drawerLayout);
            for (int i = 0; i < commentOverflow.getChildCount(); i++) {
                View maybeScrollable = commentOverflow.getChildAt(i);
                if (maybeScrollable instanceof HorizontalScrollView) {
                    sidebar.addScrollable(maybeScrollable);
                }
            }
        } else {
            commentOverflow.removeAllViews();
        }
    }
}
