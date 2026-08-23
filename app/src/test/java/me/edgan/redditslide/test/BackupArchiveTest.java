package me.edgan.redditslide.test;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import me.edgan.redditslide.util.BackupArchive;
import org.jspecify.annotations.Nullable;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/** Round-trips the encrypted backup format without an Android runtime. */
public class BackupArchiveTest {

    private static final char[] PASSWORD = "correct horse".toCharArray();

    @Rule public final TemporaryFolder temporaryFolder = new TemporaryFolder();

    private static final String SETTINGS_XML =
            "<?xml version='1.0' encoding='utf-8'?>\n<map><boolean name=\"night\" value=\"true\" /></map>";
    private static final String AUTH_XML =
            "<?xml version='1.0' encoding='utf-8'?>\n<map><string name=\"lasttoken\">tok</string></map>";

    private File writeSamplePrefs() throws IOException {
        File prefsDir = temporaryFolder.newFolder("shared_prefs");
        writeFile(new File(prefsDir, "SETTINGS.xml"), SETTINGS_XML);
        writeFile(new File(prefsDir, "AUTH.xml"), AUTH_XML);
        // Skipped: the name contains "cache".
        writeFile(new File(prefsDir, "savedcache.xml"), "<map />");
        return prefsDir;
    }

    private static void writeFile(File file, String content) throws IOException {
        try (OutputStream out = new FileOutputStream(file)) {
            out.write(content.getBytes(StandardCharsets.UTF_8));
        }
    }

    private static Map<String, String> manifest() {
        Map<String, String> manifest = new LinkedHashMap<>();
        manifest.put("format", "1");
        manifest.put("package", "me.edgan.redditslide");
        return manifest;
    }

    private byte[] writeArchive(@Nullable String kvStoreData) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        BackupArchive.writeArchive(writeSamplePrefs(), kvStoreData, manifest(), PASSWORD, out);
        return out.toByteArray();
    }

    @Test
    public void roundTripsPreferencesAndKvStoreData() throws IOException {
        byte[] archive = writeArchive("readlater_t3_abc\t123\n");

        BackupArchive.Archive restored =
                BackupArchive.readArchive(new ByteArrayInputStream(archive), PASSWORD);

        Map<String, byte[]> prefs = restored.getPreferenceFiles();
        assertEquals(2, prefs.size());
        assertArrayEquals(SETTINGS_XML.getBytes(StandardCharsets.UTF_8), prefs.get("SETTINGS.xml"));
        assertArrayEquals(AUTH_XML.getBytes(StandardCharsets.UTF_8), prefs.get("AUTH.xml"));
        assertEquals("readlater_t3_abc\t123\n", restored.getKvStoreData());
    }

    @Test
    public void omitsCachePreferencesAndAnEmptyKvStore() throws IOException {
        byte[] archive = writeArchive("");

        BackupArchive.Archive restored =
                BackupArchive.readArchive(new ByteArrayInputStream(archive), PASSWORD);

        assertFalse(restored.getPreferenceFiles().containsKey("savedcache.xml"));
        assertNull(restored.getKvStoreData());
    }

    @Test
    public void archiveLooksLikeAZipAndALegacyBackupDoesNot() throws IOException {
        assertTrue(BackupArchive.isZip(writeArchive(null)));
        assertFalse(
                BackupArchive.isZip(
                        (BackupArchive.LEGACY_MARKER + "<STARTSETTINGS.xml>x END>")
                                .getBytes(StandardCharsets.UTF_8)));
        assertFalse(BackupArchive.isZip(new byte[] {'P', 'K'}));
    }

    @Test
    public void theWrongPasswordIsReportedAsSuch() throws IOException {
        byte[] archive = writeArchive(null);

        try {
            BackupArchive.readArchive(
                    new ByteArrayInputStream(archive), "not the password".toCharArray());
            fail("expected a WrongPasswordException");
        } catch (BackupArchive.WrongPasswordException expected) {
            // The point of the test: it is distinguishable from a damaged file, so the caller can
            // re-prompt instead of telling the user their backup is broken.
        }
    }

    @Test
    public void anArchiveWithNoSlideEntriesIsRejected() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        File empty = temporaryFolder.newFolder("empty");
        // An archive whose only entry is one this version does not know about.
        BackupArchive.writeArchive(
                empty, null, Collections.singletonMap("format", "1"), PASSWORD, out);
        byte[] manifestOnly = out.toByteArray();

        // The manifest alone is enough to recognise it...
        assertTrue(
                BackupArchive.readArchive(new ByteArrayInputStream(manifestOnly), PASSWORD)
                        .getPreferenceFiles()
                        .isEmpty());

        // ...but an unrelated zip is not a Slide backup.
        try {
            BackupArchive.readArchive(
                    new ByteArrayInputStream(unrelatedZip()), "whatever".toCharArray());
            fail("expected an IOException");
        } catch (IOException expected) {
            assertFalse(expected instanceof BackupArchive.WrongPasswordException);
        }
    }

    @Test
    public void aFormatFromTheFutureIsRefused() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        BackupArchive.writeArchive(
                writeSamplePrefs(),
                null,
                Collections.singletonMap("format", "2"),
                PASSWORD,
                out);

        try {
            BackupArchive.readArchive(new ByteArrayInputStream(out.toByteArray()), PASSWORD);
            fail("expected an IOException");
        } catch (IOException expected) {
            assertTrue(String.valueOf(expected.getMessage()).contains("newer"));
        }
    }

    @Test
    public void anEntryThatEscapesTheSharedPrefsDirectoryIsRejected() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (net.lingala.zip4j.io.outputstream.ZipOutputStream zip =
                new net.lingala.zip4j.io.outputstream.ZipOutputStream(out, PASSWORD)) {
            net.lingala.zip4j.model.ZipParameters parameters =
                    new net.lingala.zip4j.model.ZipParameters();
            parameters.setFileNameInZip("slide-backup/shared_prefs/../../../evil.xml");
            parameters.setEncryptFiles(true);
            parameters.setEncryptionMethod(
                    net.lingala.zip4j.model.enums.EncryptionMethod.AES);
            zip.putNextEntry(parameters);
            zip.write("owned".getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }

        try {
            BackupArchive.readArchive(new ByteArrayInputStream(out.toByteArray()), PASSWORD);
            fail("expected an IOException");
        } catch (IOException expected) {
            assertTrue(String.valueOf(expected.getMessage()).contains("Unsafe"));
        }
    }

    /** A zip4j archive holding a single unencrypted entry with no Slide entry names. */
    private static byte[] unrelatedZip() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (java.util.zip.ZipOutputStream zip = new java.util.zip.ZipOutputStream(out)) {
            zip.putNextEntry(new java.util.zip.ZipEntry("notes.txt"));
            zip.write("hello".getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        return out.toByteArray();
    }
}
