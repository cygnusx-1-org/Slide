package me.edgan.redditslide.util;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;

import androidx.annotation.Nullable;
import androidx.core.content.FileProvider;
import androidx.documentfile.provider.DocumentFile;

import net.dean.jraw.models.Comment;
import net.dean.jraw.models.Submission;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.UUID;

public class FileUtil {
    private FileUtil() {}

    /**
     * Modifies an {@code Intent} to open a file with the FileProvider
     *
     * @param file the {@code File} to open
     * @param intent the {@Intent} to modify
     * @param context Current context
     * @return a base {@code Intent} with read and write permissions granted to the receiving
     *     application
     */
    public static Intent getFileIntent(File file, Intent intent, Context context) {
        Uri selectedUri = getFileUri(file, context);

        intent.setDataAndType(selectedUri, context.getContentResolver().getType(selectedUri));
        intent.setFlags(
                Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);

        return intent;
    }

    /**
     * Gets a valid File Uri to a file in the system
     *
     * @param file the {@code File} to open
     * @param context Current context
     * @return a File Uri to the given file
     */
    public static Uri getFileUri(File file, Context context) {
        String packageName = context.getApplicationContext().getPackageName() + ".provider";
        Uri selectedUri = FileProvider.getUriForFile(context, packageName, file);
        context.grantUriPermission(
                packageName,
                selectedUri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        return selectedUri;
    }

    /**
     * Deletes all files in a folder
     *
     * @param dir to clear contents
     */
    public static void deleteFilesInDir(File dir) {
        for (File child : dir.listFiles()) {
            child.delete();
        }
    }

    /**
     * Convert a byte count into a human-readable size
     *
     * @param size Byte count
     * @return Human-readable size
     */
    public static String readableFileSize(final long size) {
        if (size <= 0) return "0";
        final String[] units = new String[] {"B", "KB", "MB", "GB", "TB"};
        final int digitGroups = (int) (Math.log10(size) / Math.log10(1024));
        return new DecimalFormat("#,##0.#").format(size / Math.pow(1024, digitGroups))
                + " "
                + units[digitGroups];
    }

    /**
     * Returns a unique file that can be used without any collision.
     *
     * @param folderPath Base directory path for saving
     * @param subfolderPath Sub directory path with File separator, such as "/subreddit"
     * @param title Unsafe file name with any length or any characters
     * @param fileIndex File index for the file, or empty string if it is not required
     * @param extension File extension with period, such has ".png", ".mp4"
     * @return A safe file for writing
     */
    public static File getValidFile(
            String folderPath,
            String subfolderPath,
            String title,
            String fileIndex,
            String extension) {
        validateDirectory(folderPath);
        validateDirectory(folderPath + subfolderPath);

        File file;

        int tries = 0;
        do {
            String extras =
                    tries == 0 ? fileIndex + extension : fileIndex + "_" + tries + extension;
            String sanitizedTitle = sanitizeFileName(title, extras);

            if (sanitizedTitle == null || sanitizedTitle.trim().isEmpty()) {
                sanitizedTitle = UUID.randomUUID().toString();
            }

            String fileName = sanitizedTitle + extras;
            file = new File(folderPath + subfolderPath + File.separator + fileName);

            tries++;
        } while (file.exists());

        return file;
    }

    /** Checks for directory existence, if it does not exist, creates one. */
    private static void validateDirectory(String path) {
        File directory = new File(path);

        if (directory.exists()) return;

        directory.mkdirs();
    }

    /**
     * Truncates a UTF-8 unicode string to be used within the 255 Bytes limit of file name length on
     * Linux, as well as replaces reserved file name characters with underscore.
     *
     * <p>Broken multi-byte unicode character will be ignored. File name will have "…" added at the
     * end if it gets truncated.
     *
     * @param fileName The string that will be saved as file name.
     * @param extras Additional string, such as index with file extension.
     * @return Sanitized file name (without the extras) that will be within the byte limit with the
     *     extras added later.
     */
    @Nullable
    private static String sanitizeFileName(String fileName, String extras) {
        if (fileName == null) return null;

        String sanitizedFileName = fileName.replaceAll("[/?<>\\\\:*|\"]", "_");

        Charset charset = Charset.defaultCharset();
        byte[] fileNameBytes = sanitizedFileName.getBytes(charset);

        int usableByteLimit = 255 - extras.getBytes().length;
        if (fileNameBytes.length <= usableByteLimit) return sanitizedFileName;

        String replacementChar = "…";
        int replacementCharLength = replacementChar.getBytes().length;

        ByteBuffer byteBuffer =
                ByteBuffer.wrap(fileNameBytes, 0, usableByteLimit - replacementCharLength);
        CharBuffer charBuffer = CharBuffer.allocate(usableByteLimit - replacementCharLength);

        // Replaces a broken / incomplete character because of truncation
        CharsetDecoder decoder = charset.newDecoder().onMalformedInput(CodingErrorAction.IGNORE);
        decoder.decode(byteBuffer, charBuffer, true);
        decoder.flush(charBuffer);

        return new String(charBuffer.array(), 0, charBuffer.position()) + replacementChar;
    }

    public static String getMimeType(String fileName) {
        if (fileName.toLowerCase().endsWith(".png")) {
            return "image/png";
        } else if (fileName.toLowerCase().endsWith(".gif")) {
            return "image/gif";
        } else {
            return "image/jpeg";
        }
    }

    public static void copyFile(File sourceFile, OutputStream out) throws IOException {
        byte[] buffer = new byte[8192];
        java.io.FileInputStream in = new java.io.FileInputStream(sourceFile);
        try {
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
        } finally {
            try {
                in.close();
            } catch (IOException ignored) {
            }
        }
    }

    public static String getValidFileName(String title, String prefix, String extension) {
        if (title == null) {
            title = "";
        }
        // Remove invalid characters
        String cleanTitle = title.replaceAll("[^a-zA-Z0-9.-]", "_");

        // Truncate if too long (max 100 chars)
        if (cleanTitle.length() > 100) {
            cleanTitle = cleanTitle.substring(0, 100);
        }

        // If title is empty or just special chars, use timestamp
        if (cleanTitle.trim().isEmpty()) {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US);
            cleanTitle = "image_" + sdf.format(new Date());
        }

        // Combine parts
        return prefix + cleanTitle + extension;
    }

    public static String getValidFileName(String title) {
        return getValidFileName(title, "", "");
    }

    /**
     * Builds the base name used for a downloaded file by joining the post title with the Reddit
     * post id and (for media saved from a comment) the comment id, separated by underscores.
     *
     * <p>Post content yields {@code title_postId}; comment content yields {@code
     * title_postId_commentId}. Empty/null parts are skipped, so when nothing is available the
     * result is an empty string and callers fall back to a timestamp, matching the previous
     * behavior. A file index and extension are appended later by the save code.
     *
     * @param title Post title (may be null/empty)
     * @param postId Reddit post (submission) id, such as "1abc2de" (may be null/empty)
     * @param commentId Reddit comment id when saving comment media, otherwise null/empty
     * @return Underscore-joined base name without extension
     */
    public static String buildDownloadName(String title, String postId, String commentId) {
        StringBuilder sb = new StringBuilder();

        if (title != null && !title.trim().isEmpty()) {
            sb.append(title);
        }
        if (postId != null && !postId.isEmpty()) {
            if (sb.length() > 0) sb.append("_");
            sb.append(postId);
        }
        if (commentId != null && !commentId.isEmpty()) {
            if (sb.length() > 0) sb.append("_");
            sb.append(commentId);
        }

        return sb.toString();
    }

    /**
     * Builds the download base name for media that belongs to a post, yielding {@code
     * title_postId}.
     *
     * @param submission The post the media belongs to
     * @return Underscore-joined base name without extension
     */
    public static String buildDownloadName(Submission submission) {
        return buildDownloadName(submission.getTitle(), submission.getId(), null);
    }

    /**
     * Builds the download base name for media saved from a comment, yielding {@code
     * title_postId_commentId}.
     *
     * @param comment The comment the media was saved from
     * @return Underscore-joined base name without extension
     */
    public static String buildDownloadName(Comment comment) {
        return buildDownloadName(
                comment.getSubmissionTitle(), comment.getSubmissionId(), comment.getId());
    }

    /**
     * Looks up a child directory by name and creates it if it does not exist.
     *
     * @param parent Directory to look in / create within
     * @param name Name of the child directory
     * @return The existing or newly created directory, or null if creation failed
     */
    @Nullable
    public static DocumentFile getOrCreateDirectory(DocumentFile parent, String name) {
        DocumentFile existing = parent.findFile(name);
        if (existing != null && existing.isDirectory()) {
            return existing;
        }
        return parent.createDirectory(name);
    }
}
