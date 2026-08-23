package me.edgan.redditslide.test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

import android.app.Application;
import me.edgan.redditslide.util.LinkUtil;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/**
 * Robolectric tests for the pure-ish URL helpers in {@link LinkUtil}. {@code formatURL} returns an
 * {@code android.net.Uri} (real under Robolectric); {@code removeUnusedParameters} is plain string
 * work but shares the class, which is Android-heavy, so it runs here too.
 */
@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class)
public class LinkUtilTest {

    // ---------------------------------------------------------------------
    // formatURL
    // ---------------------------------------------------------------------

    @Test
    public void formatURL_schemeRelativeGetsHttps() {
        assertThat(LinkUtil.formatURL("//host.com/x").toString(), is("https://host.com/x"));
    }

    @Test
    public void formatURL_rootRelativeGetsRedditHost() {
        assertThat(LinkUtil.formatURL("/r/pics").toString(), is("https://reddit.com/r/pics"));
    }

    @Test
    public void formatURL_bareHostGetsHttp() {
        assertThat(LinkUtil.formatURL("example.com").toString(), is("http://example.com"));
    }

    @Test
    public void formatURL_lowercasesSchemeButKeepsPathCase() {
        assertThat(LinkUtil.formatURL("HTTPS://HOST/Path").toString(), is("https://HOST/Path"));
    }

    @Test
    public void formatURL_mailtoUntouched() {
        assertThat(LinkUtil.formatURL("mailto:a@b.com").toString(), is("mailto:a@b.com"));
    }

    // ---------------------------------------------------------------------
    // removeUnusedParameters
    // ---------------------------------------------------------------------

    @Test
    public void removeParams_noParamsUnchanged() {
        assertThat(
                LinkUtil.removeUnusedParameters("https://x.com/path"), is("https://x.com/path"));
    }

    @Test
    public void removeParams_keepsValuedParams() {
        assertThat(
                LinkUtil.removeUnusedParameters("https://x.com?a=1&b=2"),
                is("https://x.com?a=1&b=2"));
    }

    @Test
    public void removeParams_dropsValuelessParamInMiddle() {
        assertThat(
                LinkUtil.removeUnusedParameters("https://x.com?a=1&flag&b=2"),
                is("https://x.com?a=1&b=2"));
    }

    @Test
    public void removeParams_dropsValuelessOnlyParam() {
        assertThat(
                LinkUtil.removeUnusedParameters("https://x.com?utm_source"), is("https://x.com"));
    }

    @Test
    public void removeParams_urlDecodesKeysAndValues() {
        assertThat(
                LinkUtil.removeUnusedParameters("https://x.com?q=hello%20world"),
                is("https://x.com?q=hello world"));
    }

    @Test
    public void removeParams_trailingQuestionMarkLeftAsIs() {
        // "url?".split("\\?") drops the trailing empty, so there are no params to process.
        assertThat(LinkUtil.removeUnusedParameters("https://x.com?"), is("https://x.com?"));
    }

    @Test
    public void removeParams_leadingValuelessParamUsesAmpersand() {
        // Characterization: only index 0 gets '?', so a valued param that lands at index>0 after a
        // dropped valueless one is joined with '&' even though it is the first kept param.
        assertThat(
                LinkUtil.removeUnusedParameters("https://x.com?flag&a=1"), is("https://x.com&a=1"));
    }
}
