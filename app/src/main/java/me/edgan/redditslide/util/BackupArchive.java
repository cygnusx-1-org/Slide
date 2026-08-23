package me.edgan.redditslide.util;

import android.content.Context;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;

import me.edgan.redditslide.BuildConfig;

import net.lingala.zip4j.exception.ZipException;
import net.lingala.zip4j.io.inputstream.ZipInputStream;
import net.lingala.zip4j.io.outputstream.ZipOutputStream;
import net.lingala.zip4j.model.LocalFileHeader;
import net.lingala.zip4j.model.ZipParameters;
import net.lingala.zip4j.model.enums.AesKeyStrength;
import net.lingala.zip4j.model.enums.EncryptionMethod;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * The one place that knows what a Slide settings backup looks like. Both the local (SAF) and the
 * Google Drive paths, in both product flavours, go through here, so the format is defined once.
 *
 * <p>A backup is a zip whose every entry is AES-256 encrypted with a password the user supplies:
 *
 * <pre>
 *   slide-backup/manifest.properties      format version and the app version that wrote it
 *   slide-backup/shared_prefs/&lt;name&gt;.xml  one entry per backed-up preferences file
 *   slide-backup/kvstore.tsv              {@link KVStoreBackup#export()}, omitted when empty
 * </pre>
 *
 * <p>Encryption is not cosmetic: {@code shared_prefs} contains the {@code AUTH} file, which holds
 * every signed-in account's Reddit refresh token and serialized OAuth credentials.
 *
 * <p>Backups written before this format was introduced are a single plain-text blob; {@link
 * #isZip(byte[])} tells the two apart and {@link #restoreLegacyText(Context, String)} still reads
 * the old one.
 */
@NullMarked
public final class BackupArchive {

    /** Marker written at the head of a legacy plain-text backup. */
    public static final String LEGACY_MARKER = "Slide_backupEND>";

    private static final String ROOT = "slide-backup/";
    private static final String MANIFEST_ENTRY = ROOT + "manifest.properties";
    private static final String PREFS_PREFIX = ROOT + "shared_prefs/";
    private static final String KVSTORE_ENTRY = ROOT + "kvstore.tsv";

    private static final String MANIFEST_FORMAT = "format";
    private static final int FORMAT_VERSION = 1;

    /**
     * Preference files a backup leaves out: caches that would only bloat it, the crash-report
     * scratch file, and preferences written by Google Play services rather than by Slide.
     */
    private static final String[] SKIPPED_PREFS = {
        "cache", "ion-cookies", "albums", "STACKTRACE", "com.google"
    };

    private static final int COPY_BUFFER = 8192;

    /**
     * A backup is preference files: tens of kilobytes, a few megabytes at the very worst. The
     * restore picker offers every file on the device, so what arrives here can just as easily be a
     * video the user tapped by mistake -- and reading that whole would exhaust the heap with an
     * OutOfMemoryError, which no catch block along this path handles, before anything could reject
     * it. Refusing past this size turns that crash into the ordinary "not a valid backup" dialog.
     */
    private static final int MAX_BYTES = 64 * 1024 * 1024;

    private BackupArchive() {}

    /** Thrown when an archive cannot be decrypted with the password it was given. */
    public static class WrongPasswordException extends IOException {
        WrongPasswordException(Throwable cause) {
            super(cause);
        }
    }

    /** The decrypted contents of an archive: preference files by name, plus the KVStore blob. */
    public static final class Archive {
        private final Map<String, byte[]> preferenceFiles;
        private final @Nullable String kvStoreData;

        Archive(Map<String, byte[]> preferenceFiles, @Nullable String kvStoreData) {
            this.preferenceFiles = preferenceFiles;
            this.kvStoreData = kvStoreData;
        }

        /** @return the {@code shared_prefs} entries, keyed by bare file name. */
        public Map<String, byte[]> getPreferenceFiles() {
            return preferenceFiles;
        }

        public @Nullable String getKvStoreData() {
            return kvStoreData;
        }
    }

    /* ---------------------------------------------------------------- writing */

    /**
     * Writes an encrypted backup of this install to {@code out}, which is closed when done. The
     * caller keeps ownership of {@code password} and should zero it afterwards.
     */
    public static void write(Context context, char[] password, OutputStream out)
            throws IOException {
        writeArchive(
                getPreferencesDir(context),
                KVStoreBackup.export(),
                buildManifest(context),
                password,
                out);
    }

    /** {@link #write(Context, char[], OutputStream)} into a byte array, for the Drive upload. */
    public static byte[] write(Context context, char[] password) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        write(context, password, out);
        return out.toByteArray();
    }

    /**
     * The Context-free half of {@link #write(Context, char[], OutputStream)}, so the format can be
     * exercised without an Android runtime.
     */
    public static void writeArchive(
            File preferencesDir,
            @Nullable String kvStoreData,
            Map<String, String> manifest,
            char[] password,
            OutputStream out)
            throws IOException {
        // ZipOutputStream.close() closes the stream underneath it. That matters for SAF and cloud
        // document providers, which only commit the bytes on close -- flushing alone leaves a
        // zero-byte file behind.
        try (ZipOutputStream zip = new ZipOutputStream(out, password)) {
            writeEntry(zip, MANIFEST_ENTRY, renderManifest(manifest));

            File[] files = preferencesDir.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (!file.isFile()) {
                        continue;
                    }
                    if (isSkipped(file.getName())) {
                        LogUtil.v("Skipping preference file: " + file.getName());
                        continue;
                    }
                    writeEntry(zip, PREFS_PREFIX + file.getName(), readFile(file));
                }
            }

            if (kvStoreData != null && !kvStoreData.isEmpty()) {
                writeEntry(zip, KVSTORE_ENTRY, kvStoreData.getBytes(StandardCharsets.UTF_8));
            }
        }
    }

    private static void writeEntry(ZipOutputStream zip, String name, byte[] content)
            throws IOException {
        ZipParameters parameters = new ZipParameters();
        parameters.setFileNameInZip(name);
        parameters.setEncryptFiles(true);
        parameters.setEncryptionMethod(EncryptionMethod.AES);
        parameters.setAesKeyStrength(AesKeyStrength.KEY_STRENGTH_256);
        // The compression method stays at the DEFLATE default; setEntrySize would only be needed
        // for STORE.
        zip.putNextEntry(parameters);
        zip.write(content);
        zip.closeEntry();
    }

    private static Map<String, String> buildManifest(Context context) {
        Map<String, String> manifest = new LinkedHashMap<>();
        manifest.put(MANIFEST_FORMAT, Integer.toString(FORMAT_VERSION));
        manifest.put("package", context.getPackageName());
        manifest.put("versionName", BuildConfig.VERSION_NAME);
        manifest.put("versionCode", Integer.toString(BuildConfig.VERSION_CODE));
        manifest.put(
                "created",
                new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ", Locale.US).format(new Date()));
        return manifest;
    }

    private static byte[] renderManifest(Map<String, String> manifest) {
        StringBuilder builder = new StringBuilder();
        for (Map.Entry<String, String> entry : manifest.entrySet()) {
            builder.append(entry.getKey()).append('=').append(entry.getValue()).append('\n');
        }
        return builder.toString().getBytes(StandardCharsets.UTF_8);
    }

    /* ---------------------------------------------------------------- reading */

    /** @return whether {@code data} starts with the zip local-file-header signature. */
    public static boolean isZip(byte[] data) {
        return data.length >= 4
                && data[0] == 'P'
                && data[1] == 'K'
                && data[2] == 0x03
                && data[3] == 0x04;
    }

    /**
     * Decrypts {@code data} and writes it over this install's preferences. Every entry is decrypted
     * before anything is committed, so a wrong password cannot leave the app half-restored.
     *
     * @throws WrongPasswordException if the archive will not decrypt with {@code password}
     */
    public static void restore(Context context, byte[] data, char[] password) throws IOException {
        Archive archive = readArchive(new ByteArrayInputStream(data), password);

        File preferencesDir = getPreferencesDir(context);
        for (Map.Entry<String, byte[]> entry : archive.getPreferenceFiles().entrySet()) {
            File target = new File(preferencesDir, entry.getKey());
            try (OutputStream out = new FileOutputStream(target)) {
                out.write(entry.getValue());
            }
            LogUtil.v("Restored preference file: " + entry.getKey());
        }

        String kvStoreData = archive.getKvStoreData();
        if (kvStoreData != null && !kvStoreData.isEmpty()) {
            KVStoreBackup.restore(kvStoreData);
            LogUtil.v("Restored KVStore collections.");
        }
    }

    /**
     * The Context-free half of {@link #restore(Context, byte[], char[])}. Reads every entry into
     * memory; backups are preference files, so they are tens of kilobytes.
     */
    public static Archive readArchive(InputStream in, char[] password) throws IOException {
        Map<String, byte[]> preferenceFiles = new LinkedHashMap<>();
        String kvStoreData = null;
        boolean recognized = false;

        try (ZipInputStream zip = new ZipInputStream(in, password)) {
            LocalFileHeader header;
            while ((header = zip.getNextEntry()) != null) {
                String name = header.getFileName();
                if (name == null || header.isDirectory()) {
                    continue;
                }

                // AES stores a two-byte password verifier, so getNextEntry() above rejects a wrong
                // password as ZipException(WRONG_PASSWORD). Roughly one password in 65536 clears
                // that verifier and only fails the entry MAC, which zip4j reports as a plain
                // IOException -- so that one lands on the "damaged backup" message rather than the
                // "wrong password" one. Retyping the password gets the right message; separating
                // the two would mean matching on an exception message, which would then report
                // genuine read errors as bad passwords.
                byte[] content = readFully(zip);

                if (MANIFEST_ENTRY.equals(name)) {
                    checkFormat(content);
                    recognized = true;
                } else if (KVSTORE_ENTRY.equals(name)) {
                    kvStoreData = new String(content, StandardCharsets.UTF_8);
                    recognized = true;
                } else if (name.startsWith(PREFS_PREFIX)) {
                    String fileName = name.substring(PREFS_PREFIX.length());
                    if (!isSafeEntryName(fileName)) {
                        throw new IOException("Unsafe backup entry name: " + name);
                    }
                    preferenceFiles.put(fileName, content);
                    recognized = true;
                }
            }
        } catch (ZipException e) {
            if (e.getType() == ZipException.Type.WRONG_PASSWORD) {
                throw new WrongPasswordException(e);
            }
            throw e;
        }

        if (!recognized) {
            throw new IOException("No Slide backup entries found in archive");
        }
        return new Archive(preferenceFiles, kvStoreData);
    }

    private static void checkFormat(byte[] manifest) throws IOException {
        Properties properties = new Properties();
        properties.load(new StringReader(new String(manifest, StandardCharsets.UTF_8)));
        String format = properties.getProperty(MANIFEST_FORMAT);
        int version;
        try {
            version = Integer.parseInt(format == null ? "" : format.trim());
        } catch (NumberFormatException e) {
            throw new IOException("Backup manifest has no usable format version", e);
        }
        if (version != FORMAT_VERSION) {
            throw new IOException(
                    "Backup format " + version + " is newer than this version of Slide can read");
        }
    }

    /**
     * Guards against zip slip: an entry under {@code shared_prefs/} names one file in that
     * directory and nothing else, so anything with a path separator or a traversal segment is
     * rejected rather than resolved.
     */
    private static boolean isSafeEntryName(String fileName) {
        return !fileName.isEmpty()
                && fileName.indexOf('/') < 0
                && fileName.indexOf('\\') < 0
                && !fileName.equals(".")
                && !fileName.equals("..");
    }

    /* ---------------------------------------------------------------- legacy format */

    /**
     * Restores a backup written before the encrypted-zip format: one blob of every preference file
     * concatenated behind {@code <STARTname>} / {@code END>} markers.
     *
     * @return whether the blob looked like a Slide backup and was applied
     */
    public static boolean restoreLegacyText(Context context, String data) {
        try {
            if (!data.contains(LEGACY_MARKER)) {
                LogUtil.v("Backup file did not contain the '" + LEGACY_MARKER + "' marker.");
                return false;
            }

            // Example data:
            // Slide_backupEND><STARTsomefile.xml>filecontentEND><STARTotherfile.xml>filecontentEND>
            File preferencesDir = getPreferencesDir(context);
            String[] files = data.split("END>");
            // files[0] holds "Slide_backupEND>", so skip it
            for (int i = 1; i < files.length; i++) {
                String innerFile = files[i];
                int startIndex = innerFile.indexOf("<START");
                if (startIndex == -1) {
                    LogUtil.v("Skipping malformed file block: " + innerFile);
                    continue;
                }

                String name =
                        innerFile.substring(startIndex + 6, innerFile.indexOf(">", startIndex));
                String fileContent = innerFile.substring(innerFile.indexOf(">", startIndex) + 1);

                if (KVStoreBackup.SENTINEL.equals(name)) {
                    KVStoreBackup.restore(fileContent);
                    LogUtil.v("Restored KVStore collections from legacy backup.");
                    continue;
                }
                if (!isSafeEntryName(name)) {
                    LogUtil.v("Skipping unsafe legacy entry name: " + name);
                    continue;
                }

                File target = new File(preferencesDir, name);
                LogUtil.v("Restoring legacy file: " + name + " (size=" + fileContent.length() + ")");
                try (OutputStream out = new FileOutputStream(target)) {
                    out.write(fileContent.getBytes(StandardCharsets.UTF_8));
                }
            }
        } catch (Exception e) {
            LogUtil.e(e, "Exception while parsing legacy backup data");
            return false;
        }
        return true;
    }

    /* ---------------------------------------------------------------- helpers */

    private static File getPreferencesDir(Context context) {
        return new File(context.getApplicationInfo().dataDir, "shared_prefs");
    }

    private static boolean isSkipped(String fileName) {
        for (String skipped : SKIPPED_PREFS) {
            if (fileName.contains(skipped)) {
                return true;
            }
        }
        return false;
    }

    private static byte[] readFile(File file) throws IOException {
        try (InputStream in = new FileInputStream(file)) {
            return readFully(in);
        }
    }

    /**
     * Drains {@code in} to a byte array. On a {@link ZipInputStream} that is the current entry.
     *
     * @throws IOException if the stream holds more than {@link #MAX_BYTES}, rather than reading on
     *     until the heap runs out
     */
    public static byte[] readFully(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[COPY_BUFFER];
        int read;
        while ((read = in.read(buffer)) != -1) {
            if (out.size() > MAX_BYTES - read) {
                throw new IOException("Backup is larger than " + MAX_BYTES + " bytes");
            }
            out.write(buffer, 0, read);
        }
        return out.toByteArray();
    }
}
