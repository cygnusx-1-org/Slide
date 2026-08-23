package me.edgan.redditslide.SubmissionViews;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.test.core.app.ApplicationProvider;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.InputStream;
import java.util.HashSet;
import me.edgan.redditslide.Activities.MediaView;
import me.edgan.redditslide.Adapters.CardSubmissionViewHolder;
import me.edgan.redditslide.Adapters.FullSubmissionViewHolder;
import me.edgan.redditslide.Adapters.SubmissionViewHolder;
import me.edgan.redditslide.ContentType;
import me.edgan.redditslide.R;
import me.edgan.redditslide.SettingValues;
import me.edgan.redditslide.util.OnSingleClickListener;
import net.dean.jraw.models.Submission;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.shadow.api.Shadow;
import org.robolectric.shadows.ShadowDrawable;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33, application = Application.class)
public class HeaderImageLinkViewTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Before
    public void enableImages() {
        SettingValues.noImages = false;
        SettingValues.lowResAlways = false;
        SettingValues.lowResMobile = false;
        SettingValues.gif = true;
        SettingValues.alwaysExternal = new HashSet<>();
        final Context context = ApplicationProvider.getApplicationContext();
        SettingValues.prefs =
                context.getSharedPreferences("header-image-test", Context.MODE_PRIVATE);
        ContentType.invalidateTypeCache();
    }

    @Test
    public void playableTypesUsePlayPlaceholder() {
        assertTrue(
                HeaderImageLinkView.isPlayablePlaceholderType(ContentType.Type.GIF));
        assertTrue(
                HeaderImageLinkView.isPlayablePlaceholderType(
                        ContentType.Type.VREDDIT_DIRECT));
        assertTrue(
                HeaderImageLinkView.isPlayablePlaceholderType(
                        ContentType.Type.VREDDIT_REDIRECT));
        assertTrue(
                HeaderImageLinkView.isPlayablePlaceholderType(ContentType.Type.STREAMABLE));
        assertTrue(
                HeaderImageLinkView.isPlayablePlaceholderType(ContentType.Type.VIDEO));
        assertTrue(
                HeaderImageLinkView.isPlayablePlaceholderType(ContentType.Type.EMBEDDED));
    }

    @Test
    public void nonPlayableTypesDoNotUsePlayPlaceholder() {
        assertFalse(
                HeaderImageLinkView.isPlayablePlaceholderType(ContentType.Type.LINK));
        assertFalse(
                HeaderImageLinkView.isPlayablePlaceholderType(ContentType.Type.IMAGE));
        assertFalse(
                HeaderImageLinkView.isPlayablePlaceholderType(ContentType.Type.TUMBLR));
        assertThat(
                HeaderImageLinkView.isPlayablePlaceholderType(ContentType.Type.SELF),
                is(false));
    }

    @Test
    public void compactTumblrMp4WithoutPreviewShowsPlayPlaceholderAndOpensMedia()
            throws Exception {
        final TestActivity activity = createActivity();
        final SubmissionViewHolder holder = inflateHolder(activity, R.layout.submission_list, false);
        final Submission submission = new Submission(readFixture("tumblrVideoNoPreview.json"));
        final ContentType.Type type = ContentType.getContentType(submission);

        assertThat(type, is(ContentType.Type.GIF));
        holder.leadImage.setSubmission(submission, false, submission.getSubredditName(), type);

        final ImageView thumbnail = (ImageView) holder.thumbimage;
        assertThat(holder.leadImage.getVisibility(), is(View.GONE));
        assertPlayPlaceholder(activity, thumbnail);
        assertThat(holder.leadImage.loadedUrl, is(submission.getUrl()));

        SubmissionClickActions.addClickFunctions(thumbnail, type, activity, submission, holder);
        OnSingleClickListener.override = true;
        assertTrue(thumbnail.performClick());
        final Intent startedIntent =
                org.robolectric.Shadows.shadowOf(activity).getNextStartedActivity();
        assertNotNull(startedIntent);
        final ComponentName component = startedIntent.getComponent();
        assertNotNull(component);
        assertThat(component.getClassName(), is(MediaView.class.getName()));
        assertThat(startedIntent.getStringExtra(MediaView.EXTRA_URL), is(submission.getUrl()));
    }

    @Test
    public void fullscreenTumblrMp4WithoutPreviewShowsPlayPlaceholder() throws Exception {
        final TestActivity activity = createActivity();
        final SubmissionViewHolder holder =
                inflateHolder(activity, R.layout.submission_fullscreen, true);
        final Submission playable = new Submission(readFixture("tumblrVideoNoPreview.json"));
        holder.leadImage.setSubmission(
                playable,
                true,
                playable.getSubredditName(),
                ContentType.getContentType(playable));

        assertThat(holder.leadImage.getVisibility(), is(View.GONE));
        assertThat(activity.findViewById(R.id.wraparea).getVisibility(), is(View.VISIBLE));
        assertPlayPlaceholder(activity, (ImageView) holder.thumbimage);
    }

    @Test
    public void malformedVRedditPreviewFallsBackToPlayPlaceholder() throws Exception {
        final TestActivity activity = createActivity();
        final SubmissionViewHolder holder = inflateHolder(activity, R.layout.submission_list, false);
        final ObjectNode node = (ObjectNode) readFixture("tumblrVideoNoPreview.json").deepCopy();
        node.put("name", "t3_malformed_vreddit");
        node.put("url", "https://v.redd.it/example");
        node.put("url_overridden_by_dest", "https://v.redd.it/example");
        node.put("domain", "v.redd.it");
        node.set(
                "preview",
                MAPPER.readTree("{\"images\":[{}]}"));
        final Submission submission = new Submission(node);
        final ContentType.Type type = ContentType.getContentType(submission);

        assertThat(type, is(ContentType.Type.VREDDIT_REDIRECT));
        holder.leadImage.setSubmission(
                submission, false, submission.getSubredditName(), type);

        assertThat(holder.leadImage.getVisibility(), is(View.GONE));
        assertPlayPlaceholder(activity, (ImageView) holder.thumbimage);
    }

    @Test
    public void recycledProductionRowClearsPlayDescription() throws Exception {
        final TestActivity activity = createActivity();
        final SubmissionViewHolder holder = inflateHolder(activity, R.layout.submission_list, false);
        final Submission playable = new Submission(readFixture("tumblrVideoNoPreview.json"));
        holder.leadImage.setSubmission(
                playable,
                false,
                playable.getSubredditName(),
                ContentType.getContentType(playable));
        assertNotNull(holder.thumbimage.getContentDescription());

        final ObjectNode linkNode =
                (ObjectNode) readFixture("tumblrVideoNoPreview.json").deepCopy();
        linkNode.put("name", "t3_plain_link");
        linkNode.put("url", "https://example.org/article");
        linkNode.put("url_overridden_by_dest", "https://example.org/article");
        linkNode.put("domain", "example.org");
        final Submission link = new Submission(linkNode);
        holder.leadImage.setSubmission(
                link, false, link.getSubredditName(), ContentType.getContentType(link));

        assertThat(holder.thumbimage.getContentDescription(), is((CharSequence) null));
        assertThat(holder.thumbimage.getVisibility(), is(View.GONE));
    }

    private static TestActivity createActivity() {
        final ActivityController<TestActivity> controller =
                Robolectric.buildActivity(TestActivity.class);
        final TestActivity activity = controller.get();
        activity.setTheme(R.style.Theme_LIGHT);
        activity.getTheme().applyStyle(R.style.FontStyle_MediumPost, true);
        activity.getTheme().applyStyle(R.style.FontStyle_MediumComment, true);
        controller.setup();
        return activity;
    }

    private static SubmissionViewHolder inflateHolder(
            TestActivity activity, int layout, boolean full) {
        final View root =
                LayoutInflater.from(activity).inflate(layout, new FrameLayout(activity), false);
        activity.setContentView(root);
        // `full` picks submission_fullscreen, which is the only layout the FullSubmissionViewHolder
        // fields exist in; everything else here is a feed card. See NULLAWAY.md phase 14.
        final SubmissionViewHolder holder =
                full ? new FullSubmissionViewHolder(root) : new CardSubmissionViewHolder(root);
        holder.leadImage.setThumbnail((ImageView) holder.thumbimage);
        if (full) {
            holder.leadImage.setWrapArea(root.findViewById(R.id.wraparea));
        }
        return holder;
    }

    private static void assertPlayPlaceholder(Context context, ImageView thumbnail) {
        assertThat(thumbnail.getVisibility(), is(View.VISIBLE));
        assertThat(
                thumbnail.getContentDescription().toString(),
                is(context.getString(R.string.btn_play)));
        final Drawable drawable = thumbnail.getDrawable();
        assertNotNull(drawable);
        final ShadowDrawable shadowDrawable = Shadow.extract(drawable);
        assertThat(
                shadowDrawable.getCreatedFromResId(),
                is(R.drawable.media_play_placeholder));
    }

    private static JsonNode readFixture(String name) throws Exception {
        try (InputStream input =
                HeaderImageLinkViewTest.class
                        .getClassLoader()
                        .getResourceAsStream("submissions/" + name)) {
            assertNotNull(input);
            return MAPPER.readTree(input);
        }
    }

    public static class TestActivity extends AppCompatActivity {}
}
