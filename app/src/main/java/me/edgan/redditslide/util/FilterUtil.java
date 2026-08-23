package me.edgan.redditslide.util;

import android.content.Context;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.ListView;

import java.util.ArrayList;
import java.util.Locale;

import me.edgan.redditslide.PostMatch;
import me.edgan.redditslide.R;
import me.edgan.redditslide.SettingValues;
import org.jspecify.annotations.NullMarked;

@NullMarked
public class FilterUtil {
    public static class FilterLists {
        public final ArrayList<Boolean> regularList = new ArrayList<>();
        public final ArrayList<String> regularLabels = new ArrayList<>();
        public final ArrayList<Boolean> nsfwList = new ArrayList<>();
        public final ArrayList<String> nsfwLabels = new ArrayList<>();
    }

    public static FilterLists setupFilterLists(Context context, String subreddit, boolean reset) {
        FilterLists lists = new FilterLists();
        String sub = subreddit.toLowerCase(Locale.ENGLISH);

        // Add regular content types
        if (reset) {
            // When reset is true, set all filters to true (nothing filtered)
            lists.regularList.add(true);
            lists.regularList.add(true);
            lists.regularList.add(true);
            lists.regularList.add(true);
            lists.regularList.add(true);
            lists.regularList.add(true);
            lists.regularList.add(true);
            lists.regularList.add(true);
        } else {
            // Use saved filter settings
            lists.regularList.add(!PostMatch.isAlbum(sub));
            lists.regularList.add(!PostMatch.isGallery(sub));
            lists.regularList.add(!PostMatch.isGif(sub));
            lists.regularList.add(!PostMatch.isImage(sub));
            lists.regularList.add(!PostMatch.isLink(sub));
            lists.regularList.add(!PostMatch.isSelftext(sub));
            lists.regularList.add(!PostMatch.isTumblr(sub));
            lists.regularList.add(!PostMatch.isVideo(sub));
        }

        // Labels are always the same
        lists.regularLabels.add(context.getString(R.string.type_albums));
        lists.regularLabels.add(context.getString(R.string.type_galleries));
        lists.regularLabels.add(context.getString(R.string.type_gifs));
        lists.regularLabels.add(context.getString(R.string.images));
        lists.regularLabels.add(context.getString(R.string.type_links));
        lists.regularLabels.add(context.getString(R.string.type_selftexts));
        lists.regularLabels.add(context.getString(R.string.type_tumblrs));
        lists.regularLabels.add(context.getString(R.string.type_videos));

        // Add NSFW content types if enabled
        if (SettingValues.showNSFWContent) {
            if (reset) {
                // When reset is true, set all NSFW filters to true (nothing filtered)
                lists.nsfwList.add(true);
                lists.nsfwList.add(true);
                lists.nsfwList.add(true);
                lists.nsfwList.add(true);
                lists.nsfwList.add(true);
                lists.nsfwList.add(true);
                lists.nsfwList.add(true);
                lists.nsfwList.add(true);
            } else {
                // Use saved NSFW filter settings
                lists.nsfwList.add(!PostMatch.isNsfwAlbum(sub));
                lists.nsfwList.add(!PostMatch.isNsfwGallery(sub));
                lists.nsfwList.add(!PostMatch.isNsfwGif(sub));
                lists.nsfwList.add(!PostMatch.isNsfwImage(sub));
                lists.nsfwList.add(!PostMatch.isNsfwLink(sub));
                lists.nsfwList.add(!PostMatch.isNsfwSelftext(sub));
                lists.nsfwList.add(!PostMatch.isNsfwTumblr(sub));
                lists.nsfwList.add(!PostMatch.isNsfwVideo(sub));
            }

            // NSFW labels are always the same
            lists.nsfwLabels.add(context.getString(R.string.type_nsfw_albums));
            lists.nsfwLabels.add(context.getString(R.string.type_nsfw_galleries));
            lists.nsfwLabels.add(context.getString(R.string.type_nsfw_gifs));
            lists.nsfwLabels.add(context.getString(R.string.type_nsfw_images));
            lists.nsfwLabels.add(context.getString(R.string.type_nsfw_links));
            lists.nsfwLabels.add(context.getString(R.string.type_nsfw_selftexts));
            lists.nsfwLabels.add(context.getString(R.string.type_nsfw_tumblrs));
            lists.nsfwLabels.add(context.getString(R.string.type_nsfw_videos));
        }

        return lists;
    }

    public static void setupListViews(Context context, ListView regularListView, ListView nsfwListView,
                                    FilterLists lists) {
        ArrayAdapter<String> regularAdapter = new ArrayAdapter<>(context,
                android.R.layout.simple_list_item_multiple_choice, lists.regularLabels);
        regularListView.setAdapter(regularAdapter);

        // Set initial regular selections
        for (int i = 0; i < lists.regularList.size(); i++) {
            regularListView.setItemChecked(i, lists.regularList.get(i));
        }

        // Always set NSFW list visibility based on settings
        nsfwListView.setVisibility(SettingValues.showNSFWContent ? View.VISIBLE : View.GONE);

        // Set up NSFW list if enabled
        if (SettingValues.showNSFWContent) {
            ArrayAdapter<String> nsfwAdapter = new ArrayAdapter<>(context,
                    android.R.layout.simple_list_item_multiple_choice, lists.nsfwLabels);
            nsfwListView.setAdapter(nsfwAdapter);

            // Set initial NSFW selections
            for (int i = 0; i < lists.nsfwList.size(); i++) {
                nsfwListView.setItemChecked(i, lists.nsfwList.get(i));
            }
        } else {
            // Clear adapter when NSFW is disabled
            nsfwListView.setAdapter(null);
        }
    }

    public static void saveFilters(ListView regularListView, ListView nsfwListView, FilterLists lists, String subreddit) {
        int totalLength = 8 + (SettingValues.showNSFWContent ? 8 : 0); // 8 regular types + 8 NSFW types
        boolean[] chosen = new boolean[totalLength];

        // Regular content types in specific order - invert the checked state
        chosen[0] = !regularListView.isItemChecked(0); // albums
        chosen[1] = !regularListView.isItemChecked(1); // galleries
        chosen[2] = !regularListView.isItemChecked(2); // gifs
        chosen[3] = !regularListView.isItemChecked(3); // images
        chosen[4] = !regularListView.isItemChecked(4); // links
        chosen[5] = !regularListView.isItemChecked(5); // selftexts
        chosen[6] = !regularListView.isItemChecked(6); // tumblrs
        chosen[7] = !regularListView.isItemChecked(7); // videos

        // NSFW content types in specific order - invert the checked state
        if (SettingValues.showNSFWContent) {
            chosen[8] = !nsfwListView.isItemChecked(0);  // nsfwAlbums
            chosen[9] = !nsfwListView.isItemChecked(1);  // nsfwGalleries
            chosen[10] = !nsfwListView.isItemChecked(2); // nsfwGifs
            chosen[11] = !nsfwListView.isItemChecked(3); // nsfwImages
            chosen[12] = !nsfwListView.isItemChecked(4); // nsfwLinks
            chosen[13] = !nsfwListView.isItemChecked(5); // nsfwSelftexts
            chosen[14] = !nsfwListView.isItemChecked(6); // nsfwTumblrs
            chosen[15] = !nsfwListView.isItemChecked(7); // nsfwVideos
        }

        PostMatch.setChosen(chosen, subreddit);
    }

    public static boolean[] getCombinedChoices(ListView regularListView, ListView nsfwListView, FilterLists lists) {
        int totalLength = lists.regularList.size() + (SettingValues.showNSFWContent ? lists.nsfwList.size() : 0);
        boolean[] chosen = new boolean[totalLength];

        for (int i = 0; i < lists.regularList.size(); i++) {
            chosen[i] = regularListView.isItemChecked(i);
        }

        if (SettingValues.showNSFWContent) {
            for (int i = 0; i < lists.nsfwList.size(); i++) {
                chosen[i + lists.regularList.size()] = nsfwListView.isItemChecked(i);
            }
        }

        return chosen;
    }
}
