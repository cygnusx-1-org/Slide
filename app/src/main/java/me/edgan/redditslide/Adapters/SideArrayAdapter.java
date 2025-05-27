package me.edgan.redditslide.Adapters;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.inputmethod.InputMethodManager;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Filter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

import androidx.cardview.widget.CardView;

import me.edgan.redditslide.Activities.MainActivity;
import me.edgan.redditslide.Activities.SubredditView;
import me.edgan.redditslide.Activities.MainPagerAdapterComment;
import me.edgan.redditslide.CaseInsensitiveArrayList;
import me.edgan.redditslide.Constants;
import me.edgan.redditslide.R;
import me.edgan.redditslide.SettingValues;
import me.edgan.redditslide.UserSubscriptions;
import me.edgan.redditslide.Visuals.Palette;
import me.edgan.redditslide.util.BlendModeUtil;
import me.edgan.redditslide.util.KeyboardUtil;
import me.edgan.redditslide.util.StringUtil;

import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Created by ccrama on 8/17/2015. */
public class SideArrayAdapter extends ArrayAdapter<String> {
    private final List<String> objects;
    private Filter filter;
    public CaseInsensitiveArrayList baseItems;
    public CaseInsensitiveArrayList fitems;
    public ListView parentL;
    public boolean openInSubView = true;
    private final MainActivity mainActivity;
    private final Map<String, String> subProps = new HashMap<>();

    public SideArrayAdapter(
            MainActivity activity,
            ArrayList<String> objects,
            ArrayList<String> allSubreddits,
            ListView view) {
        super(activity, 0, objects);
        this.objects = new ArrayList<>(allSubreddits);
        filter = new SubFilter();
        fitems = new CaseInsensitiveArrayList(objects);
        baseItems = new CaseInsensitiveArrayList(objects);
        parentL = view;
        multiToMatch = UserSubscriptions.getMultiNameToSubs(true);
        this.mainActivity = activity;
        this.subProps.putAll(UserSubscriptions.getMultiNameToSubs(false));
    }

    @Override
    public boolean isEnabled(int position) {
        return false;
    }

    @Override
    public boolean areAllItemsEnabled() {
        return false;
    }

    @Override
    public Filter getFilter() {
        if (filter == null) {
            filter = new SubFilter();
        }
        return filter;
    }

    int height;
    Map<String, String> multiToMatch;

    private void hideSearchbarUI() {
        try {
            AutoCompleteTextView toolbarSearchField = (AutoCompleteTextView) ((MainActivity) getContext()).findViewById(R.id.toolbar_search);
            if (toolbarSearchField != null) {
                InputMethodManager imm = (InputMethodManager) getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
                imm.hideSoftInputFromWindow(toolbarSearchField.getWindowToken(), 0);
            }

            // Hide the toolbar search UI without an animation because we're starting a new activity
            if ((SettingValues.subredditSearchMethod == Constants.SUBREDDIT_SEARCH_METHOD_TOOLBAR
                            || SettingValues.subredditSearchMethod
                                    == Constants.SUBREDDIT_SEARCH_METHOD_BOTH)
                    && ((MainActivity) getContext())
                                    .findViewById(R.id.toolbar_search)
                                    .getVisibility()
                            == View.VISIBLE) {
                ((MainActivity) getContext())
                        .findViewById(R.id.toolbar_search_suggestions)
                        .setVisibility(View.GONE);
                ((MainActivity) getContext())
                        .findViewById(R.id.toolbar_search)
                        .setVisibility(View.GONE);
                ((MainActivity) getContext())
                        .findViewById(R.id.close_search_toolbar)
                        .setVisibility(View.GONE);

                // Play the exit animations of the search toolbar UI to avoid the animations failing
                // to animate upon the next time
                // the search toolbar UI is called. Set animation to 0 because the UI is already
                // hidden.
                ((MainActivity) getContext()).toolbarSearchController
                        .exitAnimationsForToolbarSearch(
                                0,
                                ((CardView)
                                        ((MainActivity) getContext())
                                                .findViewById(R.id.toolbar_search_suggestions)),
                                ((AutoCompleteTextView)
                                        ((MainActivity) getContext())
                                                .findViewById(R.id.toolbar_search)),
                                ((ImageView)
                                        ((MainActivity) getContext())
                                                .findViewById(R.id.close_search_toolbar)));
                if (SettingValues.single) {
                    ((MainActivity) getContext())
                            .getSupportActionBar()
                            .setTitle(((MainActivity) getContext()).selectedSub);
                } else {
                    ((MainActivity) getContext())
                            .getSupportActionBar()
                            .setTitle(((MainActivity) getContext()).tabViewModeTitle);
                }
            }
        } catch (NullPointerException npe) {
            Log.e(getClass().getName(), npe.getMessage());
        }
    }

    @Override
    public View getView(final int position, View convertView, ViewGroup parent) {
        if (position < fitems.size()) {
            convertView =
                    LayoutInflater.from(getContext())
                            .inflate(R.layout.subforsublist, parent, false);

            final String sub;
            final String base = fitems.get(position);
            if (multiToMatch.containsKey(fitems.get(position))
                    && !fitems.get(position).contains("/m/")) {
                sub = multiToMatch.get(fitems.get(position));
            } else {
                sub = fitems.get(position);
            }
            final TextView t = convertView.findViewById(R.id.name);
            t.setText(sub);

            if (height == 0) {
                final View finalConvertView = convertView;
                convertView
                        .getViewTreeObserver()
                        .addOnGlobalLayoutListener(
                                new ViewTreeObserver.OnGlobalLayoutListener() {
                                    @Override
                                    public void onGlobalLayout() {
                                        height = finalConvertView.getHeight();
                                        finalConvertView
                                                .getViewTreeObserver()
                                                .removeOnGlobalLayoutListener(this);
                                    }
                                });
            }

            final String subreddit =
                    (sub.contains("+") || sub.contains("/m/"))
                            ? sub
                            : StringUtil.sanitizeString(
                                    sub.replace(
                                            getContext().getString(R.string.search_goto) + " ",
                                            ""));

            final View colorView = convertView.findViewById(R.id.color);
            colorView.setBackgroundResource(R.drawable.circle);
            BlendModeUtil.tintDrawableAsModulate(
                    colorView.getBackground(), Palette.getColor(subreddit));
            convertView.setOnClickListener(
                    new View.OnClickListener() {
                        @Override
                        public void onClick(View view) {
                            boolean isSpecialOrMulti = UserSubscriptions.specialSubreddits.contains(subreddit.toLowerCase(Locale.ENGLISH)) || subreddit.startsWith("/m/");
                            if (SettingValues.hideSubredditTabs) {
                                // WHEN TABS ARE HIDDEN:
                                if (isSpecialOrMulti) {
                                    // Special/Multi selected: Switch to the (still visible) tab
                                    if (mainActivity.usedArray.contains(subreddit)) {
                                        int pos = mainActivity.usedArray.indexOf(subreddit);
                                        mainActivity.pager.setCurrentItem(pos);
                                        mainActivity.drawerLayout.closeDrawers();
                                        if (((MainActivity) getContext()).drawerSearch != null) {
                                            ((MainActivity) getContext()).drawerSearch.setText("");
                                        }
                                    } else if (subreddit.equalsIgnoreCase("random")
                                                || subreddit.equalsIgnoreCase("randnsfw")
                                                || subreddit.equalsIgnoreCase("myrandom")) {
                                        // Handle random subreddits even if not directly in usedArray
                                        mainActivity.drawerLayout.closeDrawers();
                                        // Find the correct index for random if it exists
                                        int randomIndex = -1;
                                        if (mainActivity.usedArray.contains("random")) randomIndex = mainActivity.usedArray.indexOf("random");
                                        else if (mainActivity.usedArray.contains("randnsfw")) randomIndex = mainActivity.usedArray.indexOf("randnsfw");
                                        else if (mainActivity.usedArray.contains("myrandom")) randomIndex = mainActivity.usedArray.indexOf("myrandom");

                                        if (randomIndex != -1) {
                                            mainActivity.pager.setCurrentItem(randomIndex);
                                        } else {
                                            // Fallback: Open in SubredditView if random tab isn't present for some reason
                                            Intent intent = new Intent(mainActivity, SubredditView.class);
                                            intent.putExtra(SubredditView.EXTRA_SUBREDDIT, subreddit);
                                            mainActivity.startActivityForResult(intent, 2001);
                                        }
                                    } else {
                                        // Should not happen for special/multis if usedArray is correct, but fallback
                                        Intent intent = new Intent(mainActivity, SubredditView.class);
                                        intent.putExtra(SubredditView.EXTRA_SUBREDDIT, subreddit);
                                        mainActivity.startActivityForResult(intent, 2001);
                                    }
                                } else {
                                    // Regular subreddit selected: Open in SubredditView because its tab is hidden
                                    Intent intent = new Intent(mainActivity, SubredditView.class);
                                    intent.putExtra(SubredditView.EXTRA_SUBREDDIT, subreddit);
                                    mainActivity.startActivityForResult(intent, 2001);
                                }
                            } else {
                                // WHEN TABS ARE SHOWN (Original Logic):
                                if (mainActivity.usedArray.contains(subreddit)) {
                                    // Subscribed: Switch to the tab
                                    int pos = mainActivity.usedArray.indexOf(subreddit);
                                    if (mainActivity.commentPager
                                            && mainActivity.adapter instanceof MainPagerAdapterComment) {
                                        mainActivity.openingComments = null;
                                        mainActivity.toOpenComments = -1;
                                        ((MainPagerAdapterComment) mainActivity.adapter).size =
                                                (mainActivity.usedArray.size() + 1);
                                        mainActivity.adapter.notifyDataSetChanged();
                                        mainActivity.doPageSelectedComments(pos);
                                    }
                                    mainActivity.pager.setCurrentItem(pos);
                                    mainActivity.drawerLayout.closeDrawers();
                                    ((MainActivity) getContext()).getDrawerController().clearDrawerSearch();
                                } else if (subreddit.equalsIgnoreCase("random")
                                            || subreddit.equalsIgnoreCase("randnsfw")
                                            || subreddit.equalsIgnoreCase("myrandom")) {
                                     // Handle random even if not technically "subscribed"
                                    mainActivity.drawerLayout.closeDrawers();
                                    // Find the correct index for random
                                    int randomIndex = -1;
                                    if (mainActivity.usedArray.contains("random")) randomIndex = mainActivity.usedArray.indexOf("random");
                                    else if (mainActivity.usedArray.contains("randnsfw")) randomIndex = mainActivity.usedArray.indexOf("randnsfw");
                                    else if (mainActivity.usedArray.contains("myrandom")) randomIndex = mainActivity.usedArray.indexOf("myrandom");

                                    if (randomIndex != -1) {
                                        mainActivity.pager.setCurrentItem(randomIndex);
                                    } else {
                                         // Fallback: Open in SubredditView
                                        Intent intent = new Intent(mainActivity, SubredditView.class);
                                        intent.putExtra(SubredditView.EXTRA_SUBREDDIT, subreddit);
                                        mainActivity.startActivityForResult(intent, 2001);
                                    }
                                } else {
                                    // Unsubscribed or other case: Open in SubredditView
                                    Intent intent = new Intent(mainActivity, SubredditView.class);
                                    intent.putExtra(SubredditView.EXTRA_SUBREDDIT, subreddit);
                                    mainActivity.startActivityForResult(intent, 2001);
                                }
                            }

                            // Hide the toolbar search UI
                            hideSearchbarUI();

                            // Hide keyboard regardless of which branch was taken
                            View currentFocusView = mainActivity.getCurrentFocus();
                            if (currentFocusView != null) {
                                InputMethodManager imm =
                                        (InputMethodManager)
                                                mainActivity.getSystemService(Context.INPUT_METHOD_SERVICE);
                                imm.hideSoftInputFromWindow(currentFocusView.getWindowToken(), 0);
                            }
                        }
                    });
            convertView.setOnLongClickListener(
                    new View.OnLongClickListener() {
                        @Override
                        public boolean onLongClick(View view) {
                            hideSearchbarUI();
                            Intent inte = new Intent(getContext(), SubredditView.class);
                            inte.putExtra(SubredditView.EXTRA_SUBREDDIT, subreddit);
                            ((Activity) getContext()).startActivityForResult(inte, 2001);

                            KeyboardUtil.hideKeyboard(getContext(), view.getWindowToken(), 0);
                            return true;
                        }
                    });
        } else {
            convertView = LayoutInflater.from(getContext()).inflate(R.layout.spacer, parent, false);
            ViewGroup.LayoutParams params = convertView.findViewById(R.id.height).getLayoutParams();
            if ((fitems.size() * height) < parentL.getHeight()
                    && (SettingValues.subredditSearchMethod
                                    == Constants.SUBREDDIT_SEARCH_METHOD_DRAWER
                            || SettingValues.subredditSearchMethod
                                    == Constants.SUBREDDIT_SEARCH_METHOD_BOTH)) {
                params.height = (parentL.getHeight() - (getCount() - 1) * height);
            } else {
                params.height = 0;
            }
            convertView.setLayoutParams(params);
        }
        return convertView;
    }

    @Override
    public int getCount() {
        return fitems.size() + 1;
    }

    public void updateHistory(ArrayList<String> history) {
        for (String s : history) {
            if (!objects.contains(s)) {
                objects.add(s);
            }
        }
        notifyDataSetChanged();
    }

    private class SubFilter extends Filter {
        @Override
        protected FilterResults performFiltering(CharSequence constraint) {
            FilterResults results = new FilterResults();
            String prefix = constraint.toString().toLowerCase(Locale.ENGLISH);

            if (prefix == null || prefix.isEmpty()) {
                CaseInsensitiveArrayList list = new CaseInsensitiveArrayList(baseItems);
                results.values = list;
                results.count = list.size();
            } else {
                openInSubView = true;
                final CaseInsensitiveArrayList list = new CaseInsensitiveArrayList(objects);
                final CaseInsensitiveArrayList nlist = new CaseInsensitiveArrayList();

                for (String sub : list) {
                    if (StringUtils.containsIgnoreCase(sub, prefix)) nlist.add(sub);
                    if (sub.equals(prefix)) openInSubView = false;
                }
                if (openInSubView) {
                    nlist.add(getContext().getString(R.string.search_goto) + " " + prefix);
                }

                results.values = nlist;
                results.count = nlist.size();
            }
            return results;
        }

        @SuppressWarnings("unchecked")
        @Override
        protected void publishResults(CharSequence constraint, FilterResults results) {
            fitems = (CaseInsensitiveArrayList) results.values;
            clear();
            if (fitems != null) {
                addAll(fitems);
                notifyDataSetChanged();
            }
        }
    }
}
