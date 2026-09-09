package me.edgan.redditslide.util;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import org.jspecify.annotations.NullMarked;

/**
 * Recognises the fixed "If you are looking for an image, it was probably deleted." graphic that
 * Reddit's preview CDN serves in place of a missing asset.
 *
 * <p>The response carries an HTTP 404 but a {@code Content-Type} of {@code image/png}, so a
 * downloader that only looks at the body happily decodes it and a feed row ends up showing Reddit's
 * error page stretched across the lead image. {@link OkHttpImageDownloader} rejects the 404 outright,
 * which stops new copies reaching the disk cache; this class is what recognises the copies already
 * cached from before that check existed.
 *
 * <p>The graphic is a single fixed asset — every 404 returns byte-identical content — so matching
 * its length together with its PNG dimensions is exact. All three have to agree; no real post image
 * is a 1048-byte PNG that is also exactly 130x60.
 */
@NullMarked
public final class RedditPlaceholderImage {

    /** Byte length of the placeholder PNG. */
    static final long LENGTH = 1048L;

    static final int WIDTH = 130;
    static final int HEIGHT = 60;

    /** Enough bytes for the PNG signature plus the IHDR width and height. */
    private static final int HEADER_BYTES = 24;

    private static final byte[] PNG_SIGNATURE = {
        (byte) 137, 'P', 'N', 'G', '\r', '\n', (byte) 26, '\n'
    };

    private RedditPlaceholderImage() {}

    /**
     * Whether {@code file} holds Reddit's placeholder graphic. Reads at most the first 24 bytes, and
     * only when the length already matches, so the common case costs a single {@code length()} call.
     */
    public static boolean isPlaceholder(File file) {
        if (file.length() != LENGTH) {
            return false;
        }
        final byte[] header = new byte[HEADER_BYTES];
        try (InputStream in = new FileInputStream(file)) {
            int read = 0;
            while (read < HEADER_BYTES) {
                final int count = in.read(header, read, HEADER_BYTES - read);
                if (count < 0) {
                    return false;
                }
                read += count;
            }
        } catch (IOException e) {
            // Unreadable file: let the normal decode path deal with it.
            return false;
        }
        return isPlaceholder(file.length(), header);
    }

    /**
     * Whether a response of {@code length} bytes starting with {@code header} is the placeholder.
     * Split out from {@link #isPlaceholder(File)} so the byte-level rule is testable on its own.
     */
    static boolean isPlaceholder(long length, byte[] header) {
        if (length != LENGTH || header.length < HEADER_BYTES) {
            return false;
        }
        for (int i = 0; i < PNG_SIGNATURE.length; i++) {
            if (header[i] != PNG_SIGNATURE[i]) {
                return false;
            }
        }
        return readInt(header, 16) == WIDTH && readInt(header, 20) == HEIGHT;
    }

    /** Whether decoded bounds match the placeholder's, for callers that already decoded them. */
    public static boolean isPlaceholderSize(int width, int height) {
        return width == WIDTH && height == HEIGHT;
    }

    private static int readInt(byte[] bytes, int offset) {
        return ((bytes[offset] & 0xff) << 24)
                | ((bytes[offset + 1] & 0xff) << 16)
                | ((bytes[offset + 2] & 0xff) << 8)
                | (bytes[offset + 3] & 0xff);
    }
}
