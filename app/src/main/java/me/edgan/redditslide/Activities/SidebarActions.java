package me.edgan.redditslide.Activities;

import java.util.Locale;
import me.edgan.redditslide.UserSubscriptions;
import net.dean.jraw.models.Subreddit;

public class SidebarActions {

    private final MainActivity mainActivity;
    public boolean currentlySubbed;

    public SidebarActions(MainActivity mainActivity) {
        this.mainActivity = mainActivity;
    }


    void changeSubscription(Subreddit subreddit, boolean isChecked) {
        currentlySubbed = isChecked;
        if (isChecked) {
            UserSubscriptions.addSubreddit(subreddit.getDisplayName().toLowerCase(Locale.ENGLISH), mainActivity);
        } else {
            UserSubscriptions.removeSubreddit(subreddit.getDisplayName().toLowerCase(Locale.ENGLISH), mainActivity);

            mainActivity.pager.setCurrentItem(mainActivity.pager.getCurrentItem() - 1);
            mainActivity.restartTheme();
        }
    }
}