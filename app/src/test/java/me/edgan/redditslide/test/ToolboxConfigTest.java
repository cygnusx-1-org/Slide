package me.edgan.redditslide.test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;

import com.google.gson.Gson;
import me.edgan.redditslide.Toolbox.RemovalReasons;
import me.edgan.redditslide.Toolbox.ToolboxConfig;
import org.junit.Test;

/**
 * Gson-deserialization tests for {@link ToolboxConfig} and {@link RemovalReasons}. Only the parsing
 * surface is exercised — {@code getUsernoteColor}/{@code getUsernoteText} are intentionally NOT
 * called because they reference {@code Toolbox.DEFAULT_USERNOTE_TYPES}, whose static initializer
 * needs the Android application context (covered under Robolectric elsewhere).
 */
public class ToolboxConfigTest {

    private static final Gson GSON = new Gson();

    private static ToolboxConfig config(String fixture) throws Exception {
        return GSON.fromJson(TestUtils.getResource(fixture), ToolboxConfig.class);
    }

    // ---------------------------------------------------------------------
    // Full config fixture
    // ---------------------------------------------------------------------

    @Test
    public void parsesSchemaVersion() throws Exception {
        assertThat(config("toolbox/toolbox_config.json").getSchema(), is(1));
    }

    @Test
    public void parsesDomainTags() throws Exception {
        ToolboxConfig c = config("toolbox/toolbox_config.json");
        assertThat(c.getDomainTags().size(), is(1));
        assertThat(c.getDomainTags().get(0).get("name"), is("imgur.com"));
    }

    @Test
    public void parsesUsernoteColorsIntoMapKeyedByKey() throws Exception {
        ToolboxConfig c = config("toolbox/toolbox_config.json");
        assertThat(c.getUsernoteTypes().size(), is(2));
        assertThat(c.getUsernoteTypes().get("spamwarn").get("color"), is("#ff0000"));
        assertThat(c.getUsernoteTypes().get("spamwarn").get("text"), is("Spam Warning"));
        assertThat(c.getUsernoteTypes().get("gooduser").get("color"), is("#008000"));
    }

    @Test
    public void parsesRemovalReasonsObject() throws Exception {
        RemovalReasons r = config("toolbox/toolbox_config.json").getRemovalReasons();
        assertThat(r, is(notNullValue()));
        assertThat(r.getPmSubject(), is("Your post was removed"));
        assertThat(r.getLogSub(), is("modlog"));
        assertThat(r.getLogTitle(), is("Removed {title}"));
        assertThat(r.getLogReason(), is("rule violation"));
        assertThat(r.getReasons().size(), is(2));
    }

    @Test
    public void parsesIndividualRemovalReasonFields() throws Exception {
        RemovalReasons.RemovalReason reason =
                config("toolbox/toolbox_config.json").getRemovalReasons().getReasons().get(0);
        assertThat(reason.getTitle(), is("Rule 1"));
        assertThat(reason.getFlairText(), is("spam"));
        assertThat(reason.getFlairCSS(), is("spam-css"));
        // text is URL-encoded in the toolbox schema; getText decodes it.
        assertThat(reason.getText(), is("No spam"));
    }

    // ---------------------------------------------------------------------
    // Empty-string-means-null adapter
    // ---------------------------------------------------------------------

    @Test
    public void emptyStringFieldsBecomeNull() throws Exception {
        ToolboxConfig c = config("toolbox/toolbox_config_empty_strings.json");
        assertThat(c.getSchema(), is(2));
        assertThat(c.getDomainTags(), is(nullValue()));
        assertThat(c.getRemovalReasons(), is(nullValue()));
        assertThat(c.getUsernoteTypes(), is(nullValue()));
    }

    // ---------------------------------------------------------------------
    // RemovalReasons default fallbacks (deserialized directly)
    // ---------------------------------------------------------------------

    @Test
    public void removalReasons_defaultPmSubjectWhenEmpty() {
        RemovalReasons r = GSON.fromJson("{}", RemovalReasons.class);
        assertThat(r.getPmSubject(), is("Your {kind} was removed from /r/{subreddit}"));
    }

    @Test
    public void removalReasons_defaultLogTitleWhenEmpty() {
        RemovalReasons r = GSON.fromJson("{}", RemovalReasons.class);
        assertThat(r.getLogTitle(), is("Removed: {kind} by /u/{author} to /r/{subreddit}"));
    }

    @Test
    public void removalReasons_customPmSubjectOverridesDefault() {
        RemovalReasons r = GSON.fromJson("{\"pmsubject\":\"Custom subject\"}", RemovalReasons.class);
        assertThat(r.getPmSubject(), is("Custom subject"));
    }

    @Test
    public void removalReasons_reasonsNullWhenAbsent() {
        RemovalReasons r = GSON.fromJson("{}", RemovalReasons.class);
        assertThat(r.getReasons(), is(nullValue()));
    }
}
