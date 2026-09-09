package me.edgan.redditslide.util;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.Arrays;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/**
 * {@link RedditPlaceholderImage} against the real asset Reddit serves.
 *
 * <p>The fixture is the exact body of a 404 from preview.redd.it: a 1048-byte, 130x60 grayscale PNG
 * reading "If you are looking for an image, it was probably deleted.". Reddit returns it with a
 * Content-Type of image/png, which is why it ever got decoded as a post's picture.
 *
 * <p>The rule has to be tight in both directions. Too loose and it throws away real post images;
 * too tight and the placeholder starts rendering again, which is the bug this exists to close.
 */
public class RedditPlaceholderImageTest {

    @Rule public final TemporaryFolder folder = new TemporaryFolder();

    private static byte[] fixture() throws IOException {
        try (InputStream in =
                RedditPlaceholderImageTest.class
                        .getClassLoader()
                        .getResourceAsStream("images/reddit_deleted_placeholder.png")) {
            assertNotNull("placeholder fixture missing from test resources", in);
            final ByteArrayOutputStream out = new ByteArrayOutputStream();
            final byte[] buffer = new byte[512];
            int read;
            while ((read = in.read(buffer)) > 0) {
                out.write(buffer, 0, read);
            }
            return out.toByteArray();
        }
    }

    private File fileOf(byte[] bytes) throws IOException {
        final File file = folder.newFile();
        Files.write(file.toPath(), bytes);
        return file;
    }

    @Test
    public void theRealPlaceholderIsRecognised() throws IOException {
        final byte[] bytes = fixture();
        assertTrue(RedditPlaceholderImage.isPlaceholder(fileOf(bytes)));
        assertTrue(RedditPlaceholderImage.isPlaceholder(bytes.length, bytes));
    }

    @Test
    public void theFixtureIsWhatTheRuleDescribes() throws IOException {
        // Guards the constants against a fixture swap: 1048 bytes, PNG, 130x60.
        final byte[] bytes = fixture();
        assertTrue(bytes.length == RedditPlaceholderImage.LENGTH);
        assertTrue(RedditPlaceholderImage.isPlaceholderSize(130, 60));
    }

    @Test
    public void aDifferentLengthIsNotThePlaceholder() throws IOException {
        // Same image content, one byte longer: Reddit's asset is a fixed, byte-identical response.
        final byte[] bytes = Arrays.copyOf(fixture(), (int) RedditPlaceholderImage.LENGTH + 1);
        assertFalse(RedditPlaceholderImage.isPlaceholder(fileOf(bytes)));
    }

    @Test
    public void theRightLengthWithoutThePngSignatureIsNotThePlaceholder() throws IOException {
        final byte[] bytes = fixture();
        bytes[1] = 'X';
        assertFalse(RedditPlaceholderImage.isPlaceholder(fileOf(bytes)));
    }

    @Test
    public void aPngOfTheRightLengthButWrongSizeIsNotThePlaceholder() throws IOException {
        final byte[] bytes = fixture();
        // IHDR width lives at offset 16, big-endian: make it 131 instead of 130.
        bytes[19] = (byte) 131;
        assertFalse(RedditPlaceholderImage.isPlaceholder(fileOf(bytes)));

        final byte[] tall = fixture();
        // IHDR height lives at offset 20.
        tall[23] = (byte) 61;
        assertFalse(RedditPlaceholderImage.isPlaceholder(fileOf(tall)));
    }

    @Test
    public void anOrdinaryImageIsNotThePlaceholder() throws IOException {
        final byte[] jpeg = new byte[(int) RedditPlaceholderImage.LENGTH];
        jpeg[0] = (byte) 0xFF;
        jpeg[1] = (byte) 0xD8;
        jpeg[2] = (byte) 0xFF;
        assertFalse(RedditPlaceholderImage.isPlaceholder(fileOf(jpeg)));
    }

    @Test
    public void aMissingFileIsNotThePlaceholder() {
        assertFalse(RedditPlaceholderImage.isPlaceholder(new File(folder.getRoot(), "absent.png")));
    }

    @Test
    public void aTruncatedFileIsNotThePlaceholder() throws IOException {
        // Length matches but the file is too short to hold a header — never happens in practice,
        // but the header read must not throw its way out of a bind.
        assertFalse(RedditPlaceholderImage.isPlaceholder(RedditPlaceholderImage.LENGTH, new byte[8]));
    }
}
