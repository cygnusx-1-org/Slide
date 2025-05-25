package me.edgan.redditslide.Activities;

import android.content.Intent;
import android.os.Handler;
import android.text.Editable;
import me.edgan.redditslide.util.stubs.SimpleTextWatcher;
import android.view.KeyEvent;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.AutoCompleteTextView;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

import androidx.cardview.widget.CardView;

import java.util.ArrayList;
import java.util.Locale;

import me.edgan.redditslide.Adapters.SideArrayAdapter;
import me.edgan.redditslide.Constants;
import me.edgan.redditslide.R;
import me.edgan.redditslide.SettingValues;
import me.edgan.redditslide.UserSubscriptions;
import me.edgan.redditslide.util.KeyboardUtil;
import me.edgan.redditslide.util.NetworkUtil;

public class ToolbarSearchController {

    private final MainActivity mainActivity;

    public ToolbarSearchController(MainActivity mainActivity) {
        this.mainActivity = mainActivity;
    }

    /**
     * If the user has the Subreddit Search method set to "long press on toolbar title", an
     * OnLongClickListener needs to be set for the toolbar as well as handling all of the relevant
     * onClicks for the views of the search bar.
     */
    public void setupSubredditSearchToolbar() {
        if (!NetworkUtil.isConnected(mainActivity)) {
            if (mainActivity.findViewById(R.id.drawer_divider) != null) {
                mainActivity.findViewById(R.id.drawer_divider).setVisibility(View.GONE);
            }
        } else {
            if ((SettingValues.subredditSearchMethod == Constants.SUBREDDIT_SEARCH_METHOD_TOOLBAR
                    || SettingValues.subredditSearchMethod == Constants.SUBREDDIT_SEARCH_METHOD_BOTH)
                    && mainActivity.usedArray != null && !mainActivity.usedArray.isEmpty()) {
                if (mainActivity.findViewById(R.id.drawer_divider) != null) {
                    if (SettingValues.subredditSearchMethod == Constants.SUBREDDIT_SEARCH_METHOD_BOTH) {
                        mainActivity.findViewById(R.id.drawer_divider).setVisibility(View.GONE);
                    } else {
                        mainActivity.findViewById(R.id.drawer_divider).setVisibility(View.VISIBLE);
                    }
                }

                final ListView TOOLBAR_SEARCH_SUGGEST_LIST = (ListView) mainActivity.findViewById(R.id.toolbar_search_suggestions_list);
                final ArrayList<String> subs_copy = new ArrayList<>(mainActivity.usedArray);
                final SideArrayAdapter TOOLBAR_SEARCH_SUGGEST_ADAPTER =
                        new SideArrayAdapter(mainActivity, subs_copy, UserSubscriptions.getAllSubreddits(mainActivity), TOOLBAR_SEARCH_SUGGEST_LIST);

                if (TOOLBAR_SEARCH_SUGGEST_LIST != null) {
                    TOOLBAR_SEARCH_SUGGEST_LIST.setAdapter(TOOLBAR_SEARCH_SUGGEST_ADAPTER);
                }

                if (mainActivity.mToolbar != null) {
                    mainActivity.mToolbar.setOnLongClickListener(
                            new View.OnLongClickListener() {
                                @Override
                                public boolean onLongClick(View v) {
                                    final AutoCompleteTextView GO_TO_SUB_FIELD = (AutoCompleteTextView) mainActivity.findViewById(R.id.toolbar_search);
                                    final ImageView CLOSE_BUTTON = (ImageView) mainActivity.findViewById(R.id.close_search_toolbar);
                                    final CardView SUGGESTIONS_BACKGROUND = (CardView) mainActivity.findViewById(R.id.toolbar_search_suggestions);

                                    // if the view mode is set to Subreddit Tabs, save the title ("Slide" or "Slide (debug)")
                                    mainActivity.tabViewModeTitle = (!SettingValues.single) ? mainActivity.getSupportActionBar().getTitle().toString() : null;

                                    mainActivity.getSupportActionBar().setTitle(""); // clear title to make room for search field

                                    if (GO_TO_SUB_FIELD != null && CLOSE_BUTTON != null && SUGGESTIONS_BACKGROUND != null) {
                                        GO_TO_SUB_FIELD.setVisibility(View.VISIBLE);
                                        CLOSE_BUTTON.setVisibility(View.VISIBLE);
                                        SUGGESTIONS_BACKGROUND.setVisibility(View.VISIBLE);

                                        // run enter animations
                                        enterAnimationsForToolbarSearch(mainActivity.ANIMATE_DURATION, SUGGESTIONS_BACKGROUND, GO_TO_SUB_FIELD, CLOSE_BUTTON);

                                        // Get focus of the search field and show the keyboard
                                        GO_TO_SUB_FIELD.requestFocus();
                                        KeyboardUtil.toggleKeyboard(mainActivity, InputMethodManager.SHOW_FORCED, InputMethodManager.HIDE_IMPLICIT_ONLY);

                                        // Close the search UI and keyboard when clicking the close button
                                        CLOSE_BUTTON.setOnClickListener(
                                                new View.OnClickListener() {
                                                    @Override
                                                    public void onClick(View v) {
                                                        final View view = mainActivity.getCurrentFocus();
                                                        if (view != null) {
                                                            // Hide the keyboard
                                                            KeyboardUtil.hideKeyboard(mainActivity, view.getWindowToken(), 0);
                                                        }

                                                        // run the exit animations
                                                        exitAnimationsForToolbarSearch(mainActivity.ANIMATE_DURATION, SUGGESTIONS_BACKGROUND, GO_TO_SUB_FIELD, CLOSE_BUTTON);

                                                        // clear sub text when close button is clicked
                                                        GO_TO_SUB_FIELD.setText("");
                                                    }
                                                });

                                        GO_TO_SUB_FIELD.setOnEditorActionListener(
                                                new TextView.OnEditorActionListener() {
                                                    @Override
                                                    public boolean onEditorAction(TextView arg0, int arg1, KeyEvent arg2) {
                                                        if (arg1 == EditorInfo.IME_ACTION_SEARCH) {
                                                            // If it the input text doesn't match a subreddit from the list exactly, openInSubView is true
                                                            if (TOOLBAR_SEARCH_SUGGEST_ADAPTER.fitems == null
                                                                    || TOOLBAR_SEARCH_SUGGEST_ADAPTER.openInSubView
                                                                    || !mainActivity.usedArray.contains(
                                                                            GO_TO_SUB_FIELD
                                                                                .getText()
                                                                                .toString()
                                                                                .toLowerCase(Locale.ENGLISH))) {
                                                                Intent intent = new Intent(mainActivity, SubredditView.class);
                                                                intent.putExtra(SubredditView.EXTRA_SUBREDDIT, GO_TO_SUB_FIELD.getText().toString());
                                                                mainActivity.startActivityForResult(intent, 2002);
                                                            } else {
                                                                if (mainActivity.commentPager && mainActivity.adapter instanceof MainPagerAdapterComment) {
                                                                    mainActivity.openingComments = null;
                                                                    mainActivity.toOpenComments = -1;
                                                                    ((MainPagerAdapterComment) mainActivity.adapter).size = (mainActivity.usedArray.size() + 1);
                                                                    mainActivity.adapter.notifyDataSetChanged();

                                                                    if (mainActivity.usedArray.contains(GO_TO_SUB_FIELD.getText().toString().toLowerCase(Locale.ENGLISH))) {
                                                                        mainActivity.doPageSelectedComments(
                                                                            mainActivity.usedArray.indexOf(GO_TO_SUB_FIELD.getText().toString().toLowerCase(Locale.ENGLISH))
                                                                        );
                                                                    } else {
                                                                        mainActivity.doPageSelectedComments(
                                                                                mainActivity.usedArray.indexOf(TOOLBAR_SEARCH_SUGGEST_ADAPTER.fitems.get(0))
                                                                        );
                                                                    }
                                                                }
                                                                if (mainActivity.usedArray.contains(GO_TO_SUB_FIELD.getText().toString().toLowerCase(Locale.ENGLISH))) {
                                                                    mainActivity.pager.setCurrentItem(
                                                                            mainActivity.usedArray.indexOf(
                                                                                GO_TO_SUB_FIELD
                                                                                    .getText()
                                                                                    .toString()
                                                                                    .toLowerCase(Locale.ENGLISH)
                                                                            )
                                                                    );
                                                                } else {
                                                                    mainActivity.pager.setCurrentItem(mainActivity.usedArray.indexOf(TOOLBAR_SEARCH_SUGGEST_ADAPTER.fitems.get(0)));
                                                                }
                                                            }

                                                            View view = mainActivity.getCurrentFocus();

                                                            if (view != null) {
                                                                // Hide the keyboard
                                                                KeyboardUtil.hideKeyboard(mainActivity, view.getWindowToken(), 0);
                                                            }

                                                            SUGGESTIONS_BACKGROUND.setVisibility(View.GONE);
                                                            GO_TO_SUB_FIELD.setVisibility(View.GONE);
                                                            CLOSE_BUTTON.setVisibility(View.GONE);

                                                            if (SettingValues.single) {
                                                                mainActivity.getSupportActionBar().setTitle(mainActivity.selectedSub);
                                                            } else {
                                                                // Set the title back to "Slide" or "Slide (debug)"
                                                                mainActivity.getSupportActionBar().setTitle(mainActivity.tabViewModeTitle);
                                                            }
                                                        }

                                                        return false;
                                                    }
                                                });

                                        GO_TO_SUB_FIELD.addTextChangedListener(
                                                new SimpleTextWatcher() {
                                                    @Override
                                                    public void afterTextChanged(Editable editable) {
                                                        final String RESULT = GO_TO_SUB_FIELD.getText().toString().replaceAll(" ", "");
                                                        TOOLBAR_SEARCH_SUGGEST_ADAPTER.getFilter().filter(RESULT);
                                                    }
                                                });
                                    }

                                    return true;
                                }
                            });
                }
            }
        }
    }


    /**
     * Starts the enter animations for various UI components of the toolbar subreddit search
     *
     * @param ANIMATION_DURATION duration of the animation in ms
     * @param SUGGESTIONS_BACKGROUND background of subreddit suggestions list
     * @param GO_TO_SUB_FIELD search field in toolbar
     * @param CLOSE_BUTTON button that clears the search and closes the search UI
     */
    public void enterAnimationsForToolbarSearch(
            final long ANIMATION_DURATION,
            final CardView SUGGESTIONS_BACKGROUND,
            final AutoCompleteTextView GO_TO_SUB_FIELD,
            final ImageView CLOSE_BUTTON) {
        SUGGESTIONS_BACKGROUND
                .animate()
                .translationY(mainActivity.headerHeight)
                .setInterpolator(new AccelerateDecelerateInterpolator())
                .setDuration(ANIMATION_DURATION + mainActivity.ANIMATE_DURATION_OFFSET)
                .start();

        GO_TO_SUB_FIELD
                .animate()
                .alpha(1f)
                .setInterpolator(new AccelerateDecelerateInterpolator())
                .setDuration(ANIMATION_DURATION)
                .start();

        CLOSE_BUTTON
                .animate()
                .alpha(1f)
                .setInterpolator(new AccelerateDecelerateInterpolator())
                .setDuration(ANIMATION_DURATION)
                .start();
    }

    /**
     * Starts the exit animations for various UI components of the toolbar subreddit search
     *
     * @param ANIMATION_DURATION duration of the animation in ms
     * @param SUGGESTIONS_BACKGROUND background of subreddit suggestions list
     * @param GO_TO_SUB_FIELD search field in toolbar
     * @param CLOSE_BUTTON button that clears the search and closes the search UI
     */
    public void exitAnimationsForToolbarSearch(
            final long ANIMATION_DURATION,
            final CardView SUGGESTIONS_BACKGROUND,
            final AutoCompleteTextView GO_TO_SUB_FIELD,
            final ImageView CLOSE_BUTTON) {
        SUGGESTIONS_BACKGROUND
                .animate()
                .translationY(-SUGGESTIONS_BACKGROUND.getHeight())
                .setInterpolator(new AccelerateDecelerateInterpolator())
                .setDuration(ANIMATION_DURATION + mainActivity.ANIMATE_DURATION_OFFSET)
                .start();

        GO_TO_SUB_FIELD
                .animate()
                .alpha(0f)
                .setInterpolator(new AccelerateDecelerateInterpolator())
                .setDuration(ANIMATION_DURATION)
                .start();

        CLOSE_BUTTON
                .animate()
                .alpha(0f)
                .setInterpolator(new AccelerateDecelerateInterpolator())
                .setDuration(ANIMATION_DURATION)
                .start();

        // Helps smooth the transition between the toolbar title being reset and the search elements
        // fading out.
        final long OFFSET_ANIM = (ANIMATION_DURATION == 0) ? 0 : mainActivity.ANIMATE_DURATION_OFFSET;

        // Hide the various UI components after the animations are complete and reset the toolbar title
        new Handler().postDelayed(
                        new Runnable() {
                            @Override
                            public void run() {
                                SUGGESTIONS_BACKGROUND.setVisibility(View.GONE);
                                GO_TO_SUB_FIELD.setVisibility(View.GONE);
                                CLOSE_BUTTON.setVisibility(View.GONE);

                                if (SettingValues.single) {
                                    mainActivity.getSupportActionBar().setTitle(mainActivity.selectedSub);
                                } else {
                                    mainActivity.getSupportActionBar().setTitle(mainActivity.tabViewModeTitle);
                                }
                            }
                        },
                        ANIMATION_DURATION + mainActivity.ANIMATE_DURATION_OFFSET);
    }
}