package me.edgan.redditslide.Fragments;

import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.os.Bundle;
import android.os.Handler;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.GestureDetector;
import android.view.HapticFeedbackConstants;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.RelativeLayout;
import android.widget.LinearLayout;
import android.widget.EditText;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.view.ContextThemeWrapper;
import androidx.core.graphics.ColorUtils;
import androidx.core.view.MarginLayoutParamsCompat;
import androidx.fragment.app.Fragment;
import androidx.interpolator.view.animation.LinearOutSlowInInterpolator;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.snackbar.Snackbar;
import com.mikepenz.itemanimators.AlphaInAnimator;
import com.mikepenz.itemanimators.SlideUpAlphaAnimator;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputLayout;

import me.edgan.redditslide.Activities.BaseActivity;
import me.edgan.redditslide.Activities.MainActivity;
import me.edgan.redditslide.Activities.MultiredditOverview;
import me.edgan.redditslide.Activities.Search;
import me.edgan.redditslide.Activities.Submit;
import me.edgan.redditslide.Activities.SubredditView;
import me.edgan.redditslide.Adapters.SubmissionAdapter;
import me.edgan.redditslide.Adapters.SubmissionDisplay;
import me.edgan.redditslide.Adapters.SubredditPosts;
import me.edgan.redditslide.Constants;
import me.edgan.redditslide.HasSeen;
import me.edgan.redditslide.Hidden;
import me.edgan.redditslide.OfflineSubreddit;
import me.edgan.redditslide.R;
import me.edgan.redditslide.Reddit;
import me.edgan.redditslide.SettingValues;
import me.edgan.redditslide.UserSubscriptions;
import me.edgan.redditslide.Views.CatchStaggeredGridLayoutManager;
import me.edgan.redditslide.Views.CreateCardView;
import me.edgan.redditslide.Visuals.ColorPreferences;
import me.edgan.redditslide.Visuals.Palette;
import me.edgan.redditslide.handler.ToolbarScrollHideHandler;
import me.edgan.redditslide.util.LayoutUtils;

import net.dean.jraw.models.MultiReddit;
import net.dean.jraw.models.Submission;

import java.util.List;
import java.util.Locale;

public class SubmissionsView extends Fragment implements SubmissionDisplay {
    private static int adapterPosition;
    private static int currentPosition;
    public SubredditPosts posts;
    public RecyclerView rv;
    public SubmissionAdapter adapter;
    public String id;
    public boolean main;
    public boolean forced;
    int diff;
    boolean forceLoad;
    private FloatingActionButton fab;
    private int visibleItemCount;
    private int pastVisiblesItems;
    private int totalItemCount;
    private SwipeRefreshLayout mSwipeRefreshLayout;
    private static Submission currentSubmission;

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);

        final int currentOrientation = newConfig.orientation;

        final CatchStaggeredGridLayoutManager mLayoutManager =
                (CatchStaggeredGridLayoutManager) rv.getLayoutManager();

        mLayoutManager.setSpanCount(LayoutUtils.getNumColumns(currentOrientation, getActivity()));
    }

    Runnable mLongPressRunnable;
    GestureDetector detector =
            new GestureDetector(getActivity(), new GestureDetector.SimpleOnGestureListener());
    float origY;

    @Override
    public View onCreateView(
            LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {

        final Context contextThemeWrapper =
                new ContextThemeWrapper(
                        getActivity(),
                        new ColorPreferences(inflater.getContext()).getThemeSubreddit(id));
        final View v =
                LayoutInflater.from(contextThemeWrapper)
                        .inflate(R.layout.fragment_verticalcontent, container, false);

        if (getActivity() instanceof MainActivity) {
            v.findViewById(R.id.back).setBackgroundResource(0);
        }
        rv = v.findViewById(R.id.vertical_content);

        rv.setHasFixedSize(true);

        final RecyclerView.LayoutManager mLayoutManager =
                createLayoutManager(
                        LayoutUtils.getNumColumns(
                                getResources().getConfiguration().orientation, getActivity()));

        if (!(getActivity() instanceof SubredditView)) {
            v.findViewById(R.id.back).setBackground(null);
        }
        rv.setLayoutManager(mLayoutManager);
        rv.setItemAnimator(
                new SlideUpAlphaAnimator().withInterpolator(new LinearOutSlowInInterpolator()));
        rv.getLayoutManager().scrollToPosition(0);

        mSwipeRefreshLayout = v.findViewById(R.id.activity_main_swipe_refresh_layout);
        mSwipeRefreshLayout.setColorSchemeColors(Palette.getColors(id, getContext()));

        /**
         * If using List view mode, we need to remove the start margin from the SwipeRefreshLayout.
         * The scrollbar style of "outsideInset" creates a 4dp padding around it. To counter this,
         * change the scrollbar style to "insideOverlay" when list view is enabled. To recap: this
         * removes the margins from the start/end so list view is full-width.
         */
        if (SettingValues.defaultCardView == CreateCardView.CardEnum.LIST) {
            RelativeLayout.LayoutParams params =
                    new RelativeLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT);
            MarginLayoutParamsCompat.setMarginStart(params, 0);
            rv.setScrollBarStyle(View.SCROLLBARS_INSIDE_OVERLAY);
            mSwipeRefreshLayout.setLayoutParams(params);
        }

        /**
         * If we use 'findViewById(R.id.header).getMeasuredHeight()', 0 is always returned. So, we
         * estimate the height of the header in dp. If the view type is "single" (and therefore
         * "commentPager"), we need a different offset
         */
        final int HEADER_OFFSET =
                (SettingValues.single || getActivity() instanceof SubredditView)
                        ? Constants.SINGLE_HEADER_VIEW_OFFSET
                        : Constants.TAB_HEADER_VIEW_OFFSET;

        mSwipeRefreshLayout.setProgressViewOffset(
                false,
                HEADER_OFFSET - Constants.PTR_OFFSET_TOP,
                HEADER_OFFSET + Constants.PTR_OFFSET_BOTTOM);

        if (SettingValues.fab) {
            fab = v.findViewById(R.id.post_floating_action_button);

            if (SettingValues.fabType == Constants.FAB_POST) {
                fab.setImageResource(R.drawable.ic_add);
                fab.setContentDescription(getString(R.string.btn_fab_post));
                fab.setOnClickListener(
                        new View.OnClickListener() {
                            @Override
                            public void onClick(View v) {
                                Intent inte = new Intent(getActivity(), Submit.class);
                                inte.putExtra(Submit.EXTRA_SUBREDDIT, id);
                                getActivity().startActivity(inte);
                            }
                        });
            } else if (SettingValues.fabType == Constants.FAB_SEARCH) {
                fab.setImageResource(R.drawable.ic_search);
                fab.setContentDescription(getString(R.string.btn_fab_search));
                fab.setOnClickListener(
                        new View.OnClickListener() {
                            String term;

                            @Override
                            public void onClick(View v) {
                                FrameLayout frameLayout = new FrameLayout(getActivity());
                                int padding = getResources().getDimensionPixelSize(R.dimen.activity_vertical_margin);

                                // Calculate fixed dimensions for the dialog
                                int screenWidth = getResources().getDisplayMetrics().widthPixels;
                                // Increased max width by 50% (from 720 to 1080) and increased screen percentage
                                int dialogWidth = Math.min((int)(screenWidth * 0.95), 1080);

                                // Use fixed width container with WRAP_CONTENT height
                                FrameLayout fixedWidthContainer = new FrameLayout(getActivity());
                                fixedWidthContainer.setLayoutParams(new FrameLayout.LayoutParams(
                                    dialogWidth - (padding * 2),
                                    ViewGroup.LayoutParams.WRAP_CONTENT
                                ));

                                // Create and configure TextInputLayout
                                TextInputLayout inputLayout = new TextInputLayout(getActivity());
                                inputLayout.setLayoutParams(new FrameLayout.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    ViewGroup.LayoutParams.WRAP_CONTENT
                                ));

                                // Create and configure EditText
                                EditText editText = new EditText(inputLayout.getContext());
                                editText.setSingleLine(true);
                                editText.setInputType(InputType.TYPE_CLASS_TEXT);
                                editText.setLayoutParams(new LinearLayout.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    ViewGroup.LayoutParams.WRAP_CONTENT
                                ));
                                editText.setHint(R.string.search_msg);

                                // Set theme colors
                                TypedValue typedValue = new TypedValue();
                                getActivity().getTheme().resolveAttribute(R.attr.fontColor, typedValue, true);
                                int textColor = typedValue.data;
                                int hintColor = ColorUtils.setAlphaComponent(textColor, 138);

                                editText.setTextColor(textColor);
                                editText.setHintTextColor(hintColor);
                                inputLayout.setHintTextColor(ColorStateList.valueOf(hintColor));
                                inputLayout.setDefaultHintTextColor(ColorStateList.valueOf(hintColor));

                                // No focus listener here yet - will add after dialog creation

                                // Build layout hierarchy
                                inputLayout.addView(editText);
                                fixedWidthContainer.addView(inputLayout);
                                frameLayout.addView(fixedWidthContainer);
                                frameLayout.setPadding(padding, padding/2, padding, 0);

                                MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(getActivity(),
                                        new ColorPreferences(getActivity()).getThemeSubreddit(id))
                                    .setTitle(R.string.search_title)
                                    .setView(frameLayout)
                                    .setBackgroundInsetStart(padding)
                                    .setBackgroundInsetEnd(padding);

                                editText.addTextChangedListener(new TextWatcher() {
                                    @Override
                                    public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

                                    @Override
                                    public void onTextChanged(CharSequence s, int start, int before, int count) {
                                        term = s.toString();
                                    }

                                    @Override
                                    public void afterTextChanged(Editable s) {}
                                });

                                if (id.startsWith("api/user")) {
                                    id = id.replaceFirst(".*?/m/", "/m/");
                                }

                                // Add "search current sub" if it is not frontpage/all/random
                                if (!id.equalsIgnoreCase("frontpage")
                                        && !id.equalsIgnoreCase("all")
                                        && !id.contains(".")
                                        && !id.contains("/m/")
                                        && !id.equalsIgnoreCase("friends")
                                        && !id.equalsIgnoreCase("random")
                                        && !id.equalsIgnoreCase("popular")
                                        && !id.equalsIgnoreCase("myrandom")
                                        && !id.equalsIgnoreCase("randnsfw")) {
                                    builder.setPositiveButton(getString(R.string.search_subreddit, id),
                                            (dialog, which) -> {
                                                Intent i = new Intent(getActivity(), Search.class);
                                                i.putExtra(Search.EXTRA_TERM, term);
                                                i.putExtra(Search.EXTRA_SUBREDDIT, id);
                                                startActivity(i);
                                            })
                                            .setNeutralButton(R.string.search_all,
                                                    (dialog, which) -> {
                                                        Intent i = new Intent(getActivity(), Search.class);
                                                        i.putExtra(Search.EXTRA_TERM, term);
                                                        startActivity(i);
                                                    });
                                } else if (id.contains("/m/")) {
                                    String subreddit = id;
                                    // Set the searchMulti for multireddit search
                                    if (UserSubscriptions.multireddits != null) {
                                        for (MultiReddit r : UserSubscriptions.multireddits) {
                                            if (r.getDisplayName().equalsIgnoreCase(subreddit.substring(3))) {
                                                MultiredditOverview.searchMulti = r;
                                                break;
                                            }
                                        }
                                    }
                                    builder.setPositiveButton(getString(R.string.search_subreddit, id),
                                            (dialog, which) -> {
                                                Intent i = new Intent(getActivity(), Search.class);
                                                i.putExtra(Search.EXTRA_TERM, term);
                                                i.putExtra(Search.EXTRA_MULTIREDDIT, id.substring(3));
                                                startActivity(i);
                                            })
                                            .setNeutralButton(R.string.search_all,
                                                    (dialog, which) -> {
                                                        Intent i = new Intent(getActivity(), Search.class);
                                                        i.putExtra(Search.EXTRA_TERM, term);
                                                        startActivity(i);
                                                    });
                                } else {
                                    builder.setPositiveButton(R.string.search_all,
                                            (dialog, which) -> {
                                                Intent i = new Intent(getActivity(), Search.class);
                                                i.putExtra(Search.EXTRA_TERM, term);
                                                startActivity(i);
                                            });
                                }

                                AlertDialog dialog = builder.create();

                                // Now add focus listener after dialog is created
                                editText.setOnFocusChangeListener((view, hasFocus) -> {
                                    // Maintain dialog size during focus changes
                                    view.post(() -> {
                                        Window window = dialog.getWindow();
                                        if (window != null) {
                                            window.setLayout(dialogWidth, ViewGroup.LayoutParams.WRAP_CONTENT);
                                        }
                                    });
                                });

                                // Configure the dialog window before showing it
                                Window window = dialog.getWindow();
                                if (window != null) {
                                    // Prevent window insets from changing the dialog size
                                    WindowManager.LayoutParams params = window.getAttributes();
                                    params.width = dialogWidth;
                                    params.softInputMode =
                                        WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE |
                                        WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING;

                                    // Apply flags to prevent layout changes
                                    window.setFlags(
                                        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
                                        WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM,
                                        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
                                        WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM);

                                    window.setAttributes(params);
                                }

                                dialog.show();

                                // After showing, clear flags that might interfere with focus
                                if (window != null) {
                                    window.clearFlags(
                                        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
                                        WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM);

                                    // Re-apply fixed layout size
                                    window.setLayout(dialogWidth, ViewGroup.LayoutParams.WRAP_CONTENT);
                                }

                                // Set colors for buttons and text
                                int accentColor = new ColorPreferences(getActivity()).getColor(id);
                                dialog.getButton(DialogInterface.BUTTON_POSITIVE).setTextColor(accentColor);
                                dialog.getButton(DialogInterface.BUTTON_NEUTRAL).setTextColor(accentColor);
                            }
                        });
            } else {
                fab.setImageResource(R.drawable.ic_visibility_off);
                fab.setContentDescription(getString(R.string.btn_fab_hide));
                fab.setOnClickListener(
                        new View.OnClickListener() {
                            @Override
                            public void onClick(View v) {
                                if (!Reddit.fabClear) {
                                    new MaterialAlertDialogBuilder(getActivity(),
                                            new ColorPreferences(getActivity()).getDarkThemeSubreddit(id))
                                            .setTitle(R.string.settings_fabclear)
                                            .setMessage(R.string.settings_fabclear_msg)
                                            .setPositiveButton(R.string.btn_ok, (dialog, which) -> {
                                                Reddit.colors.edit()
                                                        .putBoolean(SettingValues.PREF_FAB_CLEAR, true)
                                                        .apply();
                                                Reddit.fabClear = true;
                                                clearSeenPosts(false);
                                            })
                                            .show();
                                } else {
                                    clearSeenPosts(false);
                                }
                            }
                        });
                final Handler handler = new Handler();
                fab.setOnTouchListener(
                        new View.OnTouchListener() {

                            @Override
                            public boolean onTouch(View v, MotionEvent event) {
                                detector.onTouchEvent(event);
                                if (event.getAction() == MotionEvent.ACTION_DOWN) {
                                    origY = event.getY();
                                    handler.postDelayed(
                                            mLongPressRunnable,
                                            android.view.ViewConfiguration.getLongPressTimeout());
                                }
                                if (((event.getAction() == MotionEvent.ACTION_MOVE)
                                                && Math.abs(event.getY() - origY)
                                                        > fab.getHeight() / 2.0f)
                                        || (event.getAction() == MotionEvent.ACTION_UP)) {
                                    handler.removeCallbacks(mLongPressRunnable);
                                }
                                return false;
                            }
                        });
                mLongPressRunnable =
                        new Runnable() {
                            public void run() {
                                fab.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                                if (!Reddit.fabClear) {
                                    new MaterialAlertDialogBuilder(getActivity(),
                                            new ColorPreferences(getActivity()).getDarkThemeSubreddit(id))
                                            .setTitle(R.string.settings_fabclear)
                                            .setMessage(R.string.settings_fabclear_msg)
                                            .setPositiveButton(R.string.btn_ok, (dialog, which) -> {
                                                Reddit.colors.edit()
                                                        .putBoolean(SettingValues.PREF_FAB_CLEAR, true)
                                                        .apply();
                                                Reddit.fabClear = true;
                                                clearSeenPosts(true);
                                            })
                                            .show();
                                } else {
                                    clearSeenPosts(true);
                                }
                                Snackbar s =
                                        Snackbar.make(
                                                rv,
                                                getResources()
                                                        .getString(R.string.posts_hidden_forever),
                                                Snackbar.LENGTH_LONG);
                                /*Todo a way to unhide
                                s.setAction(R.string.btn_undo, new View.OnClickListener() {

                                    @Override
                                    public void onClick(View v) {

                                    }
                                });*/
                                LayoutUtils.showSnackbar(s);
                            }
                        };
            }
        } else {
            v.findViewById(R.id.post_floating_action_button).setVisibility(View.GONE);
        }
        if (fab != null) fab.show();

        header = getActivity().findViewById(R.id.header);

        resetScroll();

        Reddit.isLoading = false;
        if (MainActivity.shouldLoad == null
                || id == null
                || (MainActivity.shouldLoad != null && MainActivity.shouldLoad.equals(id))
                || !(getActivity() instanceof MainActivity)) {
            doAdapter();
        }
        return v;
    }

    View header;

    ToolbarScrollHideHandler toolbarScroll;

    @NonNull
    public static RecyclerView.LayoutManager createLayoutManager(final int numColumns) {
        return new CatchStaggeredGridLayoutManager(
                numColumns, CatchStaggeredGridLayoutManager.VERTICAL);
    }

    public void doAdapter() {
        if (!MainActivity.isRestart) {
            mSwipeRefreshLayout.post(
                    () -> {
                        mSwipeRefreshLayout.setRefreshing(true);
                    });
        }

        posts = new SubredditPosts(id, getContext());
        adapter = new SubmissionAdapter(getActivity(), posts, rv, id, this);
        adapter.setHasStableIds(true);

        // Force layout manager to recalculate spans before setting adapter
        if (rv.getLayoutManager() instanceof CatchStaggeredGridLayoutManager) {
            ((CatchStaggeredGridLayoutManager) rv.getLayoutManager()).invalidateSpanAssignments();
        }

        rv.setAdapter(adapter);
        posts.loadMore(getActivity(), this, true);
        mSwipeRefreshLayout.setOnRefreshListener(this::refresh);
    }

    public void doAdapter(boolean force18) {
        mSwipeRefreshLayout.post(
                () -> {
                    mSwipeRefreshLayout.setRefreshing(true);
                });

        posts = new SubredditPosts(id, getContext(), force18);
        adapter = new SubmissionAdapter(getActivity(), posts, rv, id, this);
        adapter.setHasStableIds(true);

        // Force layout manager to recalculate spans before setting adapter
        if (rv.getLayoutManager() instanceof CatchStaggeredGridLayoutManager) {
            ((CatchStaggeredGridLayoutManager) rv.getLayoutManager()).invalidateSpanAssignments();
        }

        rv.setAdapter(adapter);
        posts.loadMore(getActivity(), this, true);
        mSwipeRefreshLayout.setOnRefreshListener(this::refresh);
    }

    public List<Submission> clearSeenPosts(boolean forever) {
        if (adapter.dataSet.posts != null) {

            List<Submission> originalDataSetPosts = adapter.dataSet.posts;
            OfflineSubreddit o =
                    OfflineSubreddit.getSubreddit(
                            id.toLowerCase(Locale.ENGLISH), false, getActivity());

            for (int i = adapter.dataSet.posts.size(); i > -1; i--) {
                try {
                    if (HasSeen.getSeen(adapter.dataSet.posts.get(i))) {
                        if (forever) {
                            Hidden.setHidden(adapter.dataSet.posts.get(i));
                        }
                        o.clearPost(adapter.dataSet.posts.get(i));
                        adapter.dataSet.posts.remove(i);
                        if (adapter.dataSet.posts.isEmpty()) {
                            adapter.notifyDataSetChanged();
                        } else {
                            rv.setItemAnimator(new AlphaInAnimator());
                            adapter.notifyItemRemoved(i + 1);
                        }
                    }
                } catch (IndexOutOfBoundsException e) {
                    // Let the loop reset itself
                }
            }
            adapter.notifyItemRangeChanged(0, adapter.dataSet.posts.size());
            o.writeToMemoryNoStorage();
            rv.setItemAnimator(
                    new SlideUpAlphaAnimator().withInterpolator(new LinearOutSlowInInterpolator()));
            return originalDataSetPosts;
        }

        return null;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Bundle bundle = this.getArguments();
        id = bundle.getString("id", "");
        main = bundle.getBoolean("main", false);
        forceLoad = bundle.getBoolean("load", false);
    }

    @Override
    public void onResume() {
        super.onResume();
        if (adapter != null && adapterPosition > 0 && currentPosition == adapterPosition) {
            List<Submission> postsList = adapter.dataSet.getPosts();
            if (postsList != null && !postsList.isEmpty() && (adapterPosition - 1) >= 0 && (adapterPosition - 1) < postsList.size()) {
                if (postsList.get(adapterPosition - 1) == currentSubmission) {
                    adapter.performClick(adapterPosition);
                    adapterPosition = -1;
                }
            }
        }
    }

    public static void datachanged(int adaptorPosition2) {
        adapterPosition = adaptorPosition2;
    }

    private void refresh() {
        posts.forced = true;
        forced = true;
        posts.loadMore(mSwipeRefreshLayout.getContext(), this, true, id);
    }

    public void forceRefresh() {
        toolbarScroll.toolbarShow();
        rv.getLayoutManager().scrollToPosition(0);
        mSwipeRefreshLayout.post(
                new Runnable() {
                    @Override
                    public void run() {
                        mSwipeRefreshLayout.setRefreshing(true);
                        refresh();
                    }
                });
        mSwipeRefreshLayout.setRefreshing(false);
    }

    @Override
    public void updateSuccess(final List<Submission> submissions, final int startIndex) {
        if (getActivity() != null) {
            if (getActivity() instanceof MainActivity
                    && ((MainActivity) getActivity()).runAfterLoad != null) {
                new Handler().post(((MainActivity) getActivity()).runAfterLoad);
            }

            getActivity()
                    .runOnUiThread(
                            () -> {
                                if (mSwipeRefreshLayout != null) {
                                    mSwipeRefreshLayout.setRefreshing(false);
                                }

                                // Ensure layout manager state is consistent
                                CatchStaggeredGridLayoutManager layoutManager =
                                        (CatchStaggeredGridLayoutManager) rv.getLayoutManager();
                                layoutManager.invalidateSpanAssignments();

                                if (startIndex != -1 && !forced) {
                                    adapter.notifyItemRangeInserted(
                                            startIndex + 1, posts.posts.size());
                                } else {
                                    forced = false;
                                    rv.scrollToPosition(0);
                                }

                                adapter.notifyDataSetChanged();
                            });

            if (MainActivity.isRestart) {
                MainActivity.isRestart = false;
                posts.offline = false;
                rv.getLayoutManager().scrollToPosition(MainActivity.restartPage + 1);
            }
            if (startIndex < 10) resetScroll();
        }
    }

    @Override
    public void updateOffline(List<Submission> submissions, final long cacheTime) {
        if (getActivity() instanceof MainActivity) {
            if (((MainActivity) getActivity()).runAfterLoad != null) {
                new Handler().post(((MainActivity) getActivity()).runAfterLoad);
            }
        }
        if (this.isAdded()) {
            if (mSwipeRefreshLayout != null) {
                mSwipeRefreshLayout.setRefreshing(false);
            }
            adapter.notifyDataSetChanged();
        }
    }

    @Override
    public void updateOfflineError() {
        if (getActivity() instanceof MainActivity) {
            if (((MainActivity) getActivity()).runAfterLoad != null) {
                new Handler().post(((MainActivity) getActivity()).runAfterLoad);
            }
        }
        mSwipeRefreshLayout.setRefreshing(false);
        adapter.setError(true);
    }

    @Override
    public void updateError() {
        if (getActivity() instanceof MainActivity) {
            if (((MainActivity) getActivity()).runAfterLoad != null) {
                new Handler().post(((MainActivity) getActivity()).runAfterLoad);
            }
        }
        mSwipeRefreshLayout.setRefreshing(false);
    }

    @Override
    public void updateViews() {
        if (adapter.dataSet.posts != null) {
            for (int i = adapter.dataSet.posts.size(); i > -1; i--) {
                try {
                    if (HasSeen.getSeen(adapter.dataSet.posts.get(i))) {
                        adapter.notifyItemChanged(i + 1);
                    }
                } catch (IndexOutOfBoundsException e) {
                    // Let the loop reset itself
                }
            }
        }
    }

    @Override
    public void onAdapterUpdated() {
        adapter.notifyDataSetChanged();
    }

    public void resetScroll() {
        if (toolbarScroll == null) {
            toolbarScroll =
                    new ToolbarScrollHideHandler(((BaseActivity) getActivity()).mToolbar, header) {
                        @Override
                        public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                            super.onScrolled(recyclerView, dx, dy);

                            // Stabilize layout during scrolling
                            if (Math.abs(dy) > 0
                                    && rv.getLayoutManager()
                                            instanceof CatchStaggeredGridLayoutManager) {
                                ((CatchStaggeredGridLayoutManager) rv.getLayoutManager())
                                        .invalidateSpanAssignments();
                            }

                            if (!posts.loading
                                    && !posts.nomore
                                    && !posts.offline
                                    && !adapter.isError) {
                                visibleItemCount = rv.getLayoutManager().getChildCount();
                                totalItemCount = rv.getLayoutManager().getItemCount();

                                int[] firstVisibleItems =
                                        ((CatchStaggeredGridLayoutManager) rv.getLayoutManager())
                                                .findFirstVisibleItemPositions(null);
                                if (firstVisibleItems != null && firstVisibleItems.length > 0) {
                                    for (int firstVisibleItem : firstVisibleItems) {
                                        pastVisiblesItems = firstVisibleItem;
                                        if (SettingValues.scrollSeen
                                                && pastVisiblesItems > 0
                                                && SettingValues.storeHistory) {
                                            HasSeen.addSeenScrolling(
                                                    posts.posts
                                                            .get(pastVisiblesItems - 1)
                                                            .getFullName());
                                        }
                                    }
                                }

                                if ((visibleItemCount + pastVisiblesItems) + 5 >= totalItemCount) {
                                    posts.loading = true;
                                    posts.loadMore(
                                            mSwipeRefreshLayout.getContext(),
                                            SubmissionsView.this,
                                            false,
                                            posts.subreddit);
                                }
                            }

                            /*
                            if(dy <= 0 && !down){
                                (getActivity()).findViewById(R.id.header).animate().translationY(((BaseActivity)getActivity()).mToolbar.getTop()).setInterpolator(new AccelerateInterpolator()).start();
                                down = true;
                            } else if(down){
                                (getActivity()).findViewById(R.id.header).animate().translationY(((BaseActivity)getActivity()).mToolbar.getTop()).setInterpolator(new AccelerateInterpolator()).start();
                                down = false;
                            }*/
                            // todo For future implementation instead of scrollFlags

                            if (recyclerView.getScrollState()
                                    == RecyclerView.SCROLL_STATE_DRAGGING) {
                                diff += dy;
                            } else {
                                diff = 0;
                            }
                            if (fab != null) {
                                if (dy <= 0 && fab.getId() != 0 && SettingValues.fab) {
                                    if (recyclerView.getScrollState()
                                                    != RecyclerView.SCROLL_STATE_DRAGGING
                                            || diff < -fab.getHeight() * 2) {
                                        fab.show();
                                    }
                                } else {
                                    if (!SettingValues.alwaysShowFAB) {
                                        fab.hide();
                                    }
                                }
                            }
                        }

                        @Override
                        public void onScrollStateChanged(
                                @NonNull RecyclerView recyclerView, int newState) {
                            //                switch (newState) {
                            //                    case RecyclerView.SCROLL_STATE_IDLE:
                            //
                            // ((Reddit)getActivity().getApplicationContext()).getImageLoader().resume();
                            //                        break;
                            //                    case RecyclerView.SCROLL_STATE_DRAGGING:
                            //
                            // ((Reddit)getActivity().getApplicationContext()).getImageLoader().resume();
                            //                        break;
                            //                    case RecyclerView.SCROLL_STATE_SETTLING:
                            //
                            // ((Reddit)getActivity().getApplicationContext()).getImageLoader().pause();
                            //                        break;
                            //                }
                            super.onScrollStateChanged(recyclerView, newState);
                            // If the toolbar search is open, and the user scrolls in the Main
                            // view--close the search UI
                            if (getActivity() instanceof MainActivity
                                    && (SettingValues.subredditSearchMethod
                                                    == Constants.SUBREDDIT_SEARCH_METHOD_TOOLBAR
                                            || SettingValues.subredditSearchMethod
                                                    == Constants.SUBREDDIT_SEARCH_METHOD_BOTH)
                                    && ((MainActivity) getContext())
                                                    .findViewById(R.id.toolbar_search)
                                                    .getVisibility()
                                            == View.VISIBLE) {
                                ((MainActivity) getContext())
                                        .findViewById(R.id.close_search_toolbar)
                                        .performClick();
                            }
                        }
                    };
            rv.addOnScrollListener(toolbarScroll);
        } else {
            toolbarScroll.reset = true;
        }
    }

    public static void currentPosition(int adapterPosition) {
        currentPosition = adapterPosition;
    }

    public static void currentSubmission(Submission current) {
        currentSubmission = current;
    }
}
