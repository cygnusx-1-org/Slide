package me.edgan.redditslide.test;

import static org.junit.Assert.assertEquals;

import me.edgan.redditslide.util.FileUtil;
import org.junit.Test;

public class FileUtilTest {

    @Test
    public void postContentAppendsPostId() {
        assertEquals(
                "Cool cat_1abc2de", FileUtil.buildDownloadName("Cool cat", "1abc2de", null));
    }

    @Test
    public void commentContentAppendsPostAndCommentId() {
        assertEquals(
                "Cool cat_1abc2de_def456",
                FileUtil.buildDownloadName("Cool cat", "1abc2de", "def456"));
    }

    @Test
    public void emptyTitleFallsBackToIds() {
        assertEquals("1abc2de", FileUtil.buildDownloadName("", "1abc2de", null));
        assertEquals("1abc2de_def456", FileUtil.buildDownloadName("  ", "1abc2de", "def456"));
    }

    @Test
    public void missingPartsAreSkipped() {
        assertEquals("Cool cat", FileUtil.buildDownloadName("Cool cat", null, null));
        assertEquals("Cool cat", FileUtil.buildDownloadName("Cool cat", "", ""));
    }

    @Test
    public void everythingMissingYieldsEmpty() {
        // Empty result lets the save code fall back to a timestamp, matching prior behavior.
        assertEquals("", FileUtil.buildDownloadName(null, null, null));
    }
}
