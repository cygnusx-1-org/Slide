package me.edgan.redditslide.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import me.edgan.redditslide.Toolbox.Usernote;
import me.edgan.redditslide.Toolbox.Usernotes;
import me.edgan.redditslide.Toolbox.Usernotes.UsernotesConstants;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/**
 * Tests for {@link Usernotes}: the zlib+Base64 blob codec (needs Android {@code Base64} →
 * Robolectric), note create/remove/display logic, and the default color/text lookups (which
 * class-load {@code Toolbox}, so {@code Reddit.mApplication} is seeded first).
 */
@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class)
public class UsernotesTest {

    @Before
    public void setUp() {
        // Required before anything class-loads Toolbox (its static init reads a SharedPreferences).
        TestUtils.seedRedditApplication();
    }

    private static Map<String, List<Usernote>> emptyNotes() {
        return new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
    }

    private static Usernotes usernotes(String[] mods, String[] warnings) {
        return new Usernotes(6, new UsernotesConstants(mods, warnings), emptyNotes(), "testsub");
    }

    // ---------------------------------------------------------------------
    // Blob codec
    // ---------------------------------------------------------------------

    @Test
    public void blobRoundTrips() {
        String json = "{\"someuser\":{\"ns\":[{\"n\":\"note\",\"l\":\"\",\"t\":1,\"m\":0,\"w\":0}]}}";
        String blob = Usernotes.BlobSerializer.jsonToBlob(json);
        assertEquals(json, Usernotes.BlobDeserializer.blobToJson(blob));
    }

    @Test
    public void blobDecodesKnownGoodValue() {
        // Generated with:
        //   python3 -c "import zlib,base64; print(base64.b64encode(zlib.compress(b'{\"hello\":\"world\"}')).decode())"
        String known = "eJyrVspIzcnJV7JSKs8vyklRqgUANWsF9w==";
        assertEquals("{\"hello\":\"world\"}", Usernotes.BlobDeserializer.blobToJson(known));
    }

    @Test
    public void fullGsonWiringDecodesBlobAndLowercasesUsers() {
        String notesJson =
                "{\"SomeUser\":{\"ns\":[{\"n\":\"first note\",\"l\":\"\",\"t\":1600000000,\"m\":0,\"w\":0}]}}";
        String blob = Usernotes.BlobSerializer.jsonToBlob(notesJson);
        String wiki =
                "{\"ver\":6,\"constants\":{\"users\":[\"mod1\"],\"warnings\":[\"gooduser\"]},"
                        + "\"blob\":\""
                        + blob
                        + "\"}";
        Gson gson =
                new GsonBuilder()
                        .registerTypeAdapter(
                                new TypeToken<Map<String, List<Usernote>>>() {}.getType(),
                                new Usernotes.BlobDeserializer())
                        .create();
        Usernotes un = gson.fromJson(wiki, Usernotes.class);

        assertEquals(6, un.getSchema());
        // Stored lowercased; lookup is case-insensitive (TreeMap CASE_INSENSITIVE_ORDER).
        assertNotNull(un.getNotesForUser("someuser"));
        assertEquals("first note", un.getNotesForUser("SOMEUSER").get(0).getNoteText());
    }

    // ---------------------------------------------------------------------
    // createNote
    // ---------------------------------------------------------------------

    @Test
    public void createNote_addsFirstNoteWithScaledTime() {
        Usernotes un = usernotes(new String[] {"mod1"}, new String[] {null});
        un.createNote("user1", "hello", "", 5000L, "mod1", null);

        List<Usernote> notes = un.getNotesForUser("user1");
        assertEquals(1, notes.size());
        assertEquals("hello", notes.get(0).getNoteText());
        assertEquals(0, notes.get(0).getMod());
        assertEquals(0, notes.get(0).getWarning());
        // createNote stores time/1000; getTime() multiplies back by 1000.
        assertEquals(5000L, notes.get(0).getTime());
    }

    @Test
    public void createNote_prependsNewestFirst() {
        Usernotes un = usernotes(new String[] {"mod1"}, new String[] {null});
        un.createNote("user1", "old", "", 1000L, "mod1", null);
        un.createNote("user1", "new", "", 2000L, "mod1", null);

        List<Usernote> notes = un.getNotesForUser("user1");
        assertEquals(2, notes.size());
        assertEquals("new", notes.get(0).getNoteText());
        assertEquals("old", notes.get(1).getNoteText());
    }

    @Test
    public void createNote_appendsUnknownMod() {
        Usernotes un = usernotes(new String[] {"mod1"}, new String[] {null});
        un.createNote("user1", "note", "", 1000L, "mod2", null);

        assertEquals(2, un.getConstants().getMods().length);
        assertEquals("mod2", un.getConstants().getModName(1));
        assertEquals(1, un.getNotesForUser("user1").get(0).getMod());
    }

    @Test
    public void createNote_appendsUnknownType() {
        Usernotes un = usernotes(new String[] {"mod1"}, new String[] {null});
        un.createNote("user1", "note", "", 1000L, "mod1", "gooduser");

        assertEquals(2, un.getConstants().getTypes().length);
        assertEquals("gooduser", un.getConstants().getTypeName(1));
        assertEquals(1, un.getNotesForUser("user1").get(0).getWarning());
    }

    // ---------------------------------------------------------------------
    // removeNote
    // ---------------------------------------------------------------------

    @Test
    public void removeNote_removesUserWhenLastNoteGone() {
        Usernotes un = usernotes(new String[] {"mod1"}, new String[] {null});
        un.createNote("user1", "only", "", 1000L, "mod1", null);
        Usernote note = un.getNotesForUser("user1").get(0);

        un.removeNote("user1", note);
        assertNull(un.getNotesForUser("user1"));
    }

    @Test
    public void removeNote_unknownUserIsNoOp() {
        Usernotes un = usernotes(new String[] {"mod1"}, new String[] {null});
        // Should not throw.
        un.removeNote("ghost", new Usernote("x", "", 1, 0, 0));
        assertNull(un.getNotesForUser("ghost"));
    }

    // ---------------------------------------------------------------------
    // getDisplayNoteForUser
    // ---------------------------------------------------------------------

    @Test
    public void getDisplayNote_shortSingleNoteUnchanged() {
        Usernotes un = usernotes(new String[] {"mod1"}, new String[] {null});
        un.createNote("user1", "short note", "", 1000L, "mod1", null);
        assertEquals("short note", un.getDisplayNoteForUser("user1"));
    }

    @Test
    public void getDisplayNote_longNoteAbbreviatedTo20() {
        Usernotes un = usernotes(new String[] {"mod1"}, new String[] {null});
        un.createNote("user1", "this note is way too long to fit", "", 1000L, "mod1", null);
        String display = un.getDisplayNoteForUser("user1");
        assertEquals(20, display.length());
        assertTrue(display.endsWith("…"));
    }

    @Test
    public void getDisplayNote_countSuffixForMultipleNotes() {
        Usernotes un = usernotes(new String[] {"mod1"}, new String[] {null});
        un.createNote("user1", "old", "", 1000L, "mod1", null);
        un.createNote("user1", "newest", "", 2000L, "mod1", null);
        assertEquals("newest (+1)", un.getDisplayNoteForUser("user1"));
    }

    // ---------------------------------------------------------------------
    // Default color / text lookups (class-load Toolbox; Reddit app seeded in setUp)
    // ---------------------------------------------------------------------

    @Test
    public void getColorFromWarningIndex_usesDefaultTypeColor() {
        Usernotes un = usernotes(new String[] {"mod1"}, new String[] {"gooduser"});
        // No toolbox config loaded for "testsub" -> falls back to DEFAULT_USERNOTE_TYPES.
        assertEquals(0xFF008000, un.getColorFromWarningIndex(0));
    }

    @Test
    public void getColorFromWarningIndex_unknownTypeIsGray() {
        Usernotes un = usernotes(new String[] {"mod1"}, new String[] {"notarealtype"});
        assertEquals(0xFF808080, un.getColorFromWarningIndex(0));
    }

    @Test
    public void getWarningText_wrapsDefaultTextInBrackets() {
        Usernotes un = usernotes(new String[] {"mod1"}, new String[] {"gooduser"});
        String text = un.getWarningTextFromWarningIndex(0, true);
        assertTrue(text.startsWith("[Good"));
        assertTrue(text.endsWith("]"));
        assertTrue(text.contains("Contributor"));
    }

    @Test
    public void getDisplayColorForUser_usesFirstNoteWarning() {
        Usernotes un = usernotes(new String[] {"mod1"}, new String[] {"gooduser"});
        un.createNote("user1", "note", "", 1000L, "mod1", "gooduser");
        // The type "gooduser" already exists at index 0, so the note's warning is 0.
        assertEquals(0xFF008000, un.getDisplayColorForUser("user1"));
    }
}
