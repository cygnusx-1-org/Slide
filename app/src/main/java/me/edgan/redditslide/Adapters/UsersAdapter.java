package me.edgan.redditslide.Adapters;

import android.app.Activity;
import android.content.Intent;
import android.content.res.TypedArray;
import android.graphics.Color;
import android.os.AsyncTask;
import android.os.Build;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;

import me.edgan.redditslide.Activities.Profile;
import me.edgan.redditslide.Authentication;
import me.edgan.redditslide.R;
import me.edgan.redditslide.SavedUsers;
import me.edgan.redditslide.Visuals.Palette;
import me.edgan.redditslide.util.BlendModeUtil;
import me.edgan.redditslide.util.ClipboardUtil;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * The rows of the "Users" screen. Tapping a row opens that redditor's profile, long-pressing copies
 * the username, the star marks them as a reddit friend, and the trash icon drops them from {@link
 * SavedUsers}.
 */
@NullMarked
public class UsersAdapter extends RecyclerView.Adapter<UsersAdapter.ViewHolder> {

    private final ArrayList<String> objects;

    private final Activity context;

    private final UserRemovedListener onRemoved;

    private final int friendColor;

    private final int notFriendColor;

    /** Lets the hosting screen react to a row being deleted. */
    public interface UserRemovedListener {
        void onUserRemoved(String username);
    }

    public UsersAdapter(Activity context, ArrayList<String> objects, UserRemovedListener onRemoved) {
        this.context = context;
        this.objects = objects;
        this.onRemoved = onRemoved;

        // There is no outlined star drawable, so the friend state is carried by tint alone. Both
        // colors come from the theme, so the star matches whatever accent the user picked, and both
        // are resolved once here rather than per bind: the theme cannot change under a live adapter
        // without the activity being recreated. One attribute per call, because
        // obtainStyledAttributes indexes by position in a set the platform expects sorted by id.
        this.notFriendColor = resolveColor(context, R.attr.tintColor, Color.WHITE);
        this.friendColor = resolveColor(context, R.attr.colorAccent, Color.WHITE);
    }

    private static int resolveColor(Activity context, int attr, int fallback) {
        final TypedArray ta = context.obtainStyledAttributes(new int[] {attr});
        final int color = ta.getColor(0, fallback);
        ta.recycle();
        return color;
    }

    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View v =
                LayoutInflater.from(parent.getContext())
                        .inflate(R.layout.userforuserlist, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(ViewHolder holder, int position) {
        final View convertView = holder.itemView;
        final String username = objects.get(position);

        ((TextView) convertView.requireViewById(R.id.name)).setText(username);

        final View colorView = convertView.requireViewById(R.id.color);
        colorView.setBackgroundResource(R.drawable.circle);
        BlendModeUtil.tintDrawableAsModulate(
                colorView.getBackground(), Palette.getColorUser(username));

        convertView.setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        Intent intent = new Intent(context, Profile.class);
                        intent.putExtra(Profile.EXTRA_PROFILE, username);
                        context.startActivity(intent);
                    }
                });

        convertView.setOnLongClickListener(
                new View.OnLongClickListener() {
                    @Override
                    public boolean onLongClick(View v) {
                        ClipboardUtil.copyToClipboard(context, "Username", username);

                        // Android 13 shows its own clipboard preview for every copy. Adding a
                        // toast there stacks a second confirmation on top of that chip and covers
                        // its share buttons, so only confirm on the versions that show nothing.
                        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                            Toast.makeText(
                                            context,
                                            context.getString(R.string.users_copied, username),
                                            Toast.LENGTH_SHORT)
                                    .show();
                        }
                        return true;
                    }
                });

        final ImageView friendView = convertView.requireViewById(R.id.friend);
        bindFriend(friendView, username);

        // Friendship is an account-to-account relationship, so there is nothing to toggle while
        // signed out. INVISIBLE rather than GONE keeps the name the same width in both states, so
        // signing in or out does not shuffle the row.
        friendView.setVisibility(Authentication.isLoggedIn ? View.VISIBLE : View.INVISIBLE);

        friendView.setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        toggleFriend(friendView, username);
                    }
                });

        convertView
                .requireViewById(R.id.remove)
                .setOnClickListener(
                        new View.OnClickListener() {
                            @Override
                            public void onClick(View v) {
                                // Bind position, not the stale one captured above: a previous
                                // removal shifts every row after it down by one. Resolve it before
                                // touching the store, so a tap on a row that is already animating
                                // out cannot delete the name while leaving its row on screen.
                                final int index = holder.getBindingAdapterPosition();
                                if (index == RecyclerView.NO_POSITION) {
                                    return;
                                }

                                // removeUser ends the reddit friendship too, so the next sync
                                // cannot pull the name straight back into the list.
                                SavedUsers.removeUser(username);
                                objects.remove(index);
                                notifyItemRemoved(index);
                                onRemoved.onUserRemoved(username);
                            }
                        });
    }

    @Override
    public int getItemCount() {
        return objects.size();
    }

    /** Paints the star for the stored state of this name. */
    private void bindFriend(ImageView friendView, String username) {
        final boolean isFriend = SavedUsers.isFriend(username);

        BlendModeUtil.tintImageViewAsSrcAtop(
                friendView, isFriend ? friendColor : notFriendColor);
        friendView.setContentDescription(
                context.getString(
                        isFriend ? R.string.profile_remove_friend : R.string.profile_add_friend));
    }

    /** Flips the mark straight away, then tells reddit; {@link #pushFriend} undoes it if refused. */
    private void toggleFriend(ImageView friendView, String username) {
        final boolean add = !SavedUsers.isFriend(username);

        SavedUsers.setFriend(username, add);
        bindFriend(friendView, username);

        pushFriend(username, add);
    }

    /** Mirrors the mark onto reddit and reports the outcome, undoing it if reddit refused. */
    private void pushFriend(final String username, final boolean add) {
        new AsyncTask<Void, Void, Boolean>() {
            @Override
            protected Boolean doInBackground(Void... params) {
                return SavedUsers.pushFriendToReddit(username, add);
            }

            @Override
            protected void onPostExecute(@Nullable Boolean success) {
                if (context.isFinishing() || context.isDestroyed()) {
                    return;
                }

                // The row can be deleted while its push is still in flight, and deleting it
                // already queued an unfriend behind this one. There is no mark left to restore
                // and reporting "added as a friend" for a name the user just removed would be
                // plain wrong, so say nothing.
                if (!SavedUsers.contains(username)) {
                    return;
                }

                if (success != null && success) {
                    Toast.makeText(
                                    context,
                                    context.getString(
                                            add
                                                    ? R.string.users_friend_added
                                                    : R.string.users_friend_removed,
                                            username),
                                    Toast.LENGTH_SHORT)
                            .show();
                } else {
                    revertFriend(username, add);
                }
            }
        }.executeOnExecutor(SavedUsers.friendPusher());
    }

    /** Puts a refused mark back and repaints whichever row is showing that name now. */
    private void revertFriend(String username, boolean add) {
        SavedUsers.setFriend(username, !add);

        // The row that was tapped may have been recycled onto another name by now, so find the
        // name rather than trusting a captured position or view.
        for (int i = 0; i < objects.size(); i++) {
            if (objects.get(i).equalsIgnoreCase(username)) {
                notifyItemChanged(i);
                break;
            }
        }

        Toast.makeText(
                        context,
                        context.getString(R.string.users_friend_failed, username),
                        Toast.LENGTH_SHORT)
                .show();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        public ViewHolder(View itemView) {
            super(itemView);
        }
    }
}
