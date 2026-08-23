package me.edgan.redditslide.Activities;

import java.util.Locale;
import me.edgan.redditslide.UserSubscriptions;
import net.dean.jraw.models.Subreddit;
import org.jspecify.annotations.NullMarked;

@NullMarked
public class SidebarActions {

    private final MainActivity mainActivity;
    public boolean currentlySubbed;

    public SidebarActions(MainActivity mainActivity) {
        this.mainActivity = mainActivity;
    }


    void changeSubscription(Subreddit subreddit, boolean isChecked) {
        final String displayName = subreddit.getDisplayName();
        if (displayName == null) {
            // Nothing to subscribe to or unsubscribe from; an empty name would be stored as a
            // subscription in its own right.
            return;
        }
        currentlySubbed = isChecked;
        if (isChecked) {
            UserSubscriptions.addSubreddit(displayName.toLowerCase(Locale.ENGLISH), mainActivity);
        } else {
            UserSubscriptions.removeSubreddit(displayName.toLowerCase(Locale.ENGLISH), mainActivity);

            mainActivity.pager.setCurrentItem(mainActivity.pager.getCurrentItem() - 1);
            mainActivity.restartTheme();
        }
    }
}