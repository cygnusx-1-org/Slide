package me.edgan.redditslide.Activities;

import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentStatePagerAdapter;
import androidx.viewpager.widget.ViewPager;
import java.util.ArrayList;
import java.util.Objects;
import me.edgan.redditslide.Adapters.CommentUrlObject;
import me.edgan.redditslide.ContentType;
import me.edgan.redditslide.Fragments.AlbumFullComments;
import me.edgan.redditslide.Fragments.MediaFragmentComment;
import me.edgan.redditslide.R;
import me.edgan.redditslide.util.MiscUtil;
import net.dean.jraw.models.Comment;
import org.jspecify.annotations.NullMarked;

/** Created by ccrama on 9/17/2015. */
@NullMarked
public class ShadowboxComments extends FullScreenActivity {
    /**
     * The album's comments, set by {@link me.edgan.redditslide.Fragments.CommentPage} before this
     * activity starts. Static, so it comes back null after process death while a recreated
     * fragment still carries its old page argument — every reader tests for that.
     */
    @Nullable public static ArrayList<CommentUrlObject> comments;

    @Override
    public void onCreate(@Nullable Bundle savedInstance) {
        overrideSwipeFromAnywhere();

        if (comments == null || comments.isEmpty()) {
            // finish() does not return, so without this the next line dereferenced the list that
            // was just found missing. Same shape as Gallery.onCreate.
            super.onCreate(savedInstance);
            finish();
            return;
        }
        applyDarkColorTheme(
                MiscUtil.orEmpty(comments.get(0).comment.getComment().getSubredditName()));
        super.onCreate(savedInstance);
        setContentView(R.layout.activity_slide);
        MiscUtil.setupOldSwipeModeBackground(this, getWindow().getDecorView());

        ViewPager pager = (ViewPager) requireViewById(R.id.content_view);
        pager.setAdapter(new ShadowboxCommentsPagerAdapter(getSupportFragmentManager()));
    }

    private static class ShadowboxCommentsPagerAdapter extends FragmentStatePagerAdapter {

        ShadowboxCommentsPagerAdapter(FragmentManager fm) {
            super(fm, BEHAVIOR_RESUME_ONLY_CURRENT_FRAGMENT);
        }

        @NonNull
        @Override
        public Fragment getItem(int i) {

            Fragment f = null;
            Bundle args = new Bundle();
            // getCount() reports 0 once the static list is gone, so the pager cannot get here.
            CommentUrlObject item = Objects.requireNonNull(comments).get(i);
            Comment comment = item.comment.getComment();

            String url = item.url;

            ContentType.Type t = ContentType.getContentType(url);

            switch (t) {
                case GIF:
                case IMAGE:
                case IMGUR:
                case REDDIT:
                case EXTERNAL:
                case XKCD:
                case SPOILER:
                case DEVIANTART:
                case REDDIT_GALLERY:
                case EMBEDDED:
                case LINK:
                case STREAMABLE:
                case VIDEO:
                    f = new MediaFragmentComment();
                    args.putString("contentUrl", url);
                    args.putString("firstUrl", url);
                    args.putInt("page", i);
                    args.putString("sub", comment.getSubredditName());
                    f.setArguments(args);
                    break;
                case ALBUM:
                    f = new AlbumFullComments();
                    args.putInt("page", i);
                    f.setArguments(args);
                    break;
            }
            return Objects.requireNonNull(f);
        }

        @Override
        public int getCount() {
            return comments == null ? 0 : comments.size();
        }
    }
}
