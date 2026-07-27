package me.edgan.redditslide.Activities;

import static org.junit.Assert.assertEquals;

import android.app.Application;
import me.edgan.redditslide.SettingValues;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/**
 * Covers the Imgur low-quality url, which the pager builds by inserting a size suffix before the
 * extension ({@code <hash>m|l|h.<ext>}, the variants Imgur serves beside the original).
 *
 * <p>The url with no extension is the case worth pinning: {@code substring(0, lastIndexOf("."))} on
 * one returns the whole string minus nothing and {@code substring(lastIndexOf("."))} throws, so the
 * old inline copies of this either built a prefix like "https://i" or crashed the page.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33, application = Application.class)
public class AlbumPagerLowQualityUrlTest {

    private static final String URL = "https://i.imgur.com/abc123.png";

    private boolean lqLow;
    private boolean lqMid;

    @Before
    public void setUp() {
        // Static and shared with every other test in this Robolectric sandbox.
        lqLow = SettingValues.lqLow;
        lqMid = SettingValues.lqMid;
    }

    @After
    public void tearDown() {
        SettingValues.lqLow = lqLow;
        SettingValues.lqMid = lqMid;
    }

    @Test
    public void theLowSettingAsksForTheSmallVariant() {
        SettingValues.lqLow = true;
        SettingValues.lqMid = false;

        assertEquals("https://i.imgur.com/abc123m.png", AlbumPager.lowQualityUrl(URL));
    }

    @Test
    public void theMidSettingAsksForTheLargeVariant() {
        SettingValues.lqLow = false;
        SettingValues.lqMid = true;

        assertEquals("https://i.imgur.com/abc123l.png", AlbumPager.lowQualityUrl(URL));
    }

    @Test
    public void neitherSettingAsksForTheHugeVariant() {
        SettingValues.lqLow = false;
        SettingValues.lqMid = false;

        assertEquals("https://i.imgur.com/abc123h.png", AlbumPager.lowQualityUrl(URL));
    }

    @Test
    public void aUrlWithNoExtensionComesBackUnchanged() {
        SettingValues.lqLow = true;
        SettingValues.lqMid = false;

        // No dot to insert the suffix before. Truncating there would hand the image loader a prefix
        // of the url, which fetches nothing.
        assertEquals("https://i/abc123", AlbumPager.lowQualityUrl("https://i/abc123"));
    }

    @Test
    public void aNullUrlComesBackNull() {
        assertEquals(null, AlbumPager.lowQualityUrl(null));
    }
}
