package me.edgan.redditslide.util;

import android.widget.ListView;
import org.jspecify.annotations.NullMarked;

@NullMarked
public class FilterToggleUtil {
    public static final int STATE_ALL_NON_NSFW = 0;
    public static final int STATE_ALL_NSFW = 1;
    public static final int STATE_NONE = 2;
    public static final int STATE_ALL = 3;

    public static int determineCurrentState(ListView regularListView, ListView nsfwListView,
            FilterUtil.FilterLists lists, boolean showNSFW) {
        boolean allRegularChecked = true;
        boolean allNsfwChecked = true;
        boolean anyChecked = false;

        // Check regular items
        for (int i = 0; i < lists.regularList.size(); i++) {
            if (regularListView.isItemChecked(i)) {
                anyChecked = true;
            } else {
                allRegularChecked = false;
            }
        }

        // Check NSFW items
        if (showNSFW) {
            for (int i = 0; i < lists.nsfwList.size(); i++) {
                if (nsfwListView.isItemChecked(i)) {
                    anyChecked = true;
                } else {
                    allNsfwChecked = false;
                }
            }
        }

        // Determine current state
        if (!anyChecked) {
            return STATE_NONE;
        } else if (allRegularChecked && allNsfwChecked) {
            return STATE_ALL;
        } else if (allRegularChecked && !allNsfwChecked) {
            return STATE_ALL_NON_NSFW;
        } else if (!allRegularChecked && allNsfwChecked) {
            return STATE_ALL_NSFW;
        } else {
            return STATE_NONE; // If in mixed state, next click will clear all
        }
    }

    public static void handleFilterToggle(ListView regularListView, ListView nsfwListView,
            FilterUtil.FilterLists lists, boolean showNSFW, int toggleState) {
        // Determine current state and calculate next state
        int currentState = determineCurrentState(regularListView, nsfwListView, lists, showNSFW);
        int nextState = (currentState + 1) % 4;

        switch (nextState) {
            case STATE_ALL_NON_NSFW: // Select all non-NSFW
                for (int i = 0; i < lists.regularList.size(); i++) {
                    regularListView.setItemChecked(i, true);
                }
                if (showNSFW) {
                    for (int i = 0; i < lists.nsfwList.size(); i++) {
                        nsfwListView.setItemChecked(i, false);
                    }
                }
                break;

            case STATE_ALL_NSFW: // Select all NSFW
                for (int i = 0; i < lists.regularList.size(); i++) {
                    regularListView.setItemChecked(i, false);
                }
                if (showNSFW) {
                    for (int i = 0; i < lists.nsfwList.size(); i++) {
                        nsfwListView.setItemChecked(i, true);
                    }
                }
                break;

            case STATE_NONE: // Unselect all
                for (int i = 0; i < lists.regularList.size(); i++) {
                    regularListView.setItemChecked(i, false);
                }
                if (showNSFW) {
                    for (int i = 0; i < lists.nsfwList.size(); i++) {
                        nsfwListView.setItemChecked(i, false);
                    }
                }
                break;

            case STATE_ALL: // Select all
                for (int i = 0; i < lists.regularList.size(); i++) {
                    regularListView.setItemChecked(i, true);
                }
                if (showNSFW) {
                    for (int i = 0; i < lists.nsfwList.size(); i++) {
                        nsfwListView.setItemChecked(i, true);
                    }
                }
                break;
        }
    }
}
