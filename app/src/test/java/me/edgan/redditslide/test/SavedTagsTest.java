package me.edgan.redditslide.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import me.edgan.redditslide.ContentType;
import me.edgan.redditslide.SavedTags;
import net.dean.jraw.models.Comment;
import net.dean.jraw.models.Contribution;
import net.dean.jraw.models.Submission;
import org.junit.Test;

/**
 * {@link SavedTags}: the rules behind saved tags, which is everything about them that does not
 * need a device.
 *
 * <p>Reddit's saved-categories feature is gone, so tags are Slide's own -- which is why these rules
 * are worth pinning. Nothing on the server will correct a bad sort or let two tags differing only
 * in case both exist.
 */
public class SavedTagsTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * JRAW's {@code Submission} constructor reads several fields eagerly, so a hand-built node is
     * not enough to construct one. Start from a real captured submission and override identity, the
     * way {@link ContributionFilterTest} does.
     */
    private static Submission post(String fullname) throws Exception {
        final ObjectNode data;
        try (InputStream input =
                SavedTagsTest.class
                        .getClassLoader()
                        .getResourceAsStream("submissions/galleryPost.json")) {
            assertNotNull(input);
            data = (ObjectNode) MAPPER.readTree(input);
        }
        data.put("name", fullname);
        data.put("id", fullname.length() > 3 ? fullname.substring(3) : fullname);
        data.put("is_gallery", false);
        return new Submission(data);
    }

    /** A post whose content type is decided by its url. */
    private static Submission post(String fullname, String url) throws Exception {
        final Submission submission = post(fullname);
        ((ObjectNode) submission.getDataNode()).put("url", url);
        ((ObjectNode) submission.getDataNode()).put("is_self", false);
        return submission;
    }

    /** The captured gallery post, left as a gallery. */
    private static Submission galleryPost(String fullname) throws Exception {
        final ObjectNode data;
        try (InputStream input =
                SavedTagsTest.class
                        .getClassLoader()
                        .getResourceAsStream("submissions/galleryPost.json")) {
            assertNotNull(input);
            data = (ObjectNode) MAPPER.readTree(input);
        }
        data.put("name", fullname);
        data.put("is_gallery", true);
        return new Submission(data);
    }

    private static Comment comment(String fullname) {
        final ObjectNode data = MAPPER.createObjectNode();
        data.put("name", fullname);
        data.put("id", fullname.length() > 3 ? fullname.substring(3) : fullname);
        return new Comment(data);
    }

    private static ArrayList<Contribution> listing(Contribution... items) {
        return new ArrayList<>(Arrays.asList(items));
    }

    /** The built-in labels, as {@code SavedTagDialogs.builtinLabels} resolves them on device. */
    private static List<String> reserved() {
        return Arrays.asList(
                "All", "Posts", "Comments", "Album", "DeviantArt", "Gallery", "GIF", "Image",
                "Imgur content", "Link", "Reddit link", "Reddit Video", "Selftext", "Streamable",
                "Tumblr", "XKCD", "YouTube");
    }

    private static List<String> fullnames(List<Contribution> items) {
        final List<String> names = new ArrayList<>();
        for (Contribution item : items) {
            names.add(item.getFullName());
        }
        return names;
    }

    // ---------------------------------------------------------------------
    // Built-in tags
    // ---------------------------------------------------------------------

    @Test
    public void allMatchesEverything() throws Exception {
        assertTrue(SavedTags.matches(SavedTags.Builtin.ALL, post("t3_a")));
        assertTrue(SavedTags.matches(SavedTags.Builtin.ALL, comment("t1_b")));
    }

    @Test
    public void postsMatchesOnlySubmissions() throws Exception {
        assertTrue(SavedTags.matches(SavedTags.Builtin.POSTS, post("t3_a")));
        assertFalse(SavedTags.matches(SavedTags.Builtin.POSTS, comment("t1_b")));
    }

    @Test
    public void commentsIsTheNegativeCase() throws Exception {
        // Defined as "not a submission" so nothing Reddit adds to the saved listing later falls
        // out of both Posts and Comments.
        assertTrue(SavedTags.matches(SavedTags.Builtin.COMMENTS, comment("t1_b")));
        assertFalse(SavedTags.matches(SavedTags.Builtin.COMMENTS, post("t3_a")));
    }

    @Test
    public void builtinOrderPutsTheStructuralOnesFirst() {
        // This is the order they are pinned in every list, so it is part of the UI, not an
        // implementation detail. Content types follow, alphabetically by label.
        assertEquals(
                Arrays.asList(
                        SavedTags.Builtin.ALL,
                        SavedTags.Builtin.POSTS,
                        SavedTags.Builtin.COMMENTS,
                        SavedTags.Builtin.ALBUM,
                        SavedTags.Builtin.DEVIANTART,
                        SavedTags.Builtin.GALLERY,
                        SavedTags.Builtin.GIF,
                        SavedTags.Builtin.IMAGE,
                        SavedTags.Builtin.IMGUR,
                        SavedTags.Builtin.LINK,
                        SavedTags.Builtin.REDDIT_LINK,
                        SavedTags.Builtin.REDDIT_VIDEO,
                        SavedTags.Builtin.SELFTEXT,
                        SavedTags.Builtin.STREAMABLE,
                        SavedTags.Builtin.TUMBLR,
                        SavedTags.Builtin.XKCD,
                        SavedTags.Builtin.YOUTUBE),
                Arrays.asList(SavedTags.Builtin.values()));
    }

    // ---------------------------------------------------------------------
    // Content-type built-ins
    // ---------------------------------------------------------------------

    /**
     * The content types no tag stands for, and why -- see {@link SavedTags.Builtin}. Listing them
     * here rather than skipping them keeps the next type someone adds to {@code ContentType} from
     * being silently unreachable in the Post types section.
     */
    private static final List<ContentType.Type> DELIBERATELY_UNTAGGED =
            Arrays.asList(
                    ContentType.Type.SPOILER, // markdown link inside a body, never a submission
                    ContentType.Type.EXTERNAL, // depends on the user's always-external domains
                    ContentType.Type.NONE); // needs a submission with no url

    @Test
    public void everyContentTypeIsCoveredExactlyOnceOrDeliberatelyNotAtAll() {
        // A saved post should always be findable under one Post types row. Covering a type twice
        // would be just as wrong: the post would show under two tags that read as alternatives.
        for (ContentType.Type type : ContentType.Type.values()) {
            int covering = 0;
            for (SavedTags.Builtin builtin : SavedTags.Builtin.values()) {
                if (builtin.covers(type)) {
                    covering++;
                }
            }
            assertEquals(type.name(), DELIBERATELY_UNTAGGED.contains(type) ? 0 : 1, covering);
        }
    }

    @Test
    public void onlyStructuralBuiltinsAreNotContentTypes() {
        assertFalse(SavedTags.Builtin.ALL.isContentType());
        assertFalse(SavedTags.Builtin.POSTS.isContentType());
        assertFalse(SavedTags.Builtin.COMMENTS.isContentType());
        assertTrue(SavedTags.Builtin.GIF.isContentType());
        assertTrue(SavedTags.Builtin.SELFTEXT.isContentType());
    }

    @Test
    public void aGifPostMatchesGifAndNothingElse() throws Exception {
        final Submission gif = post("t3_gif", "http://i.imgur.com/HGuXQlm.gif");
        assertTrue(SavedTags.matches(SavedTags.Builtin.GIF, gif));
        assertFalse(SavedTags.matches(SavedTags.Builtin.IMAGE, gif));
        assertFalse(SavedTags.matches(SavedTags.Builtin.LINK, gif));
        // Still a post, and still in All.
        assertTrue(SavedTags.matches(SavedTags.Builtin.POSTS, gif));
        assertTrue(SavedTags.matches(SavedTags.Builtin.ALL, gif));
    }

    @Test
    public void anImagePostMatchesImage() throws Exception {
        final Submission image = post("t3_img", "http://i.imgur.com/HGuXQlm.png");
        assertTrue(SavedTags.matches(SavedTags.Builtin.IMAGE, image));
        assertFalse(SavedTags.matches(SavedTags.Builtin.GIF, image));
    }

    @Test
    public void anAlbumPostMatchesAlbum() throws Exception {
        final Submission album = post("t3_album", "http://www.imgur.com/a/duARTe");
        assertTrue(SavedTags.matches(SavedTags.Builtin.ALBUM, album));
        assertFalse(SavedTags.matches(SavedTags.Builtin.IMAGE, album));
    }

    @Test
    public void aGalleryPostMatchesGalleryAndNotAlbum() throws Exception {
        final Submission gallery = galleryPost("t3_gal");
        assertTrue(SavedTags.matches(SavedTags.Builtin.GALLERY, gallery));
        // Reddit galleries and imgur albums are different tags even though both are many images.
        assertFalse(SavedTags.matches(SavedTags.Builtin.ALBUM, gallery));
    }

    @Test
    public void redditVideoCoversBothVredditShapes() {
        // Direct and redirect are one thing to the user, so one tag has to hold both.
        assertTrue(SavedTags.Builtin.REDDIT_VIDEO.covers(ContentType.Type.VREDDIT_DIRECT));
        assertTrue(SavedTags.Builtin.REDDIT_VIDEO.covers(ContentType.Type.VREDDIT_REDIRECT));
        assertFalse(SavedTags.Builtin.REDDIT_VIDEO.covers(ContentType.Type.VIDEO));
    }

    @Test
    public void contentTypeBuiltinsNeverMatchAComment() {
        // A saved comment has no content type; it must not silently land under Selftext.
        final Comment c = comment("t1_b");
        for (SavedTags.Builtin builtin : SavedTags.Builtin.values()) {
            if (builtin.isContentType()) {
                assertFalse(builtin.name(), SavedTags.matches(builtin, c));
            }
        }
    }

    @Test
    public void contentTypeNamesAreReserved() {
        for (String name : Arrays.asList("GIF", "gif", "Album", "reddit video", "Selftext")) {
            assertEquals(
                    name,
                    SavedTags.Validation.RESERVED,
                    SavedTags.validate(name, Collections.emptyList(), null, reserved()));
        }
    }

    // ---------------------------------------------------------------------
    // Sorting
    // ---------------------------------------------------------------------

    @Test
    public void sortIsCaseInsensitive() {
        assertEquals(
                Arrays.asList("apple", "Banana", "cherry"),
                SavedTags.sort(Arrays.asList("cherry", "apple", "Banana")));
    }

    @Test
    public void sortHandlesAccents() {
        // A collator, not a byte comparison: "eclair" sorts next to "éclair", not after "zebra".
        assertEquals(
                Arrays.asList("eclair", "éclair", "zebra"),
                SavedTags.sort(Arrays.asList("zebra", "éclair", "eclair")));
    }

    @Test
    public void sortDoesNotMutateItsInput() {
        final List<String> original = new ArrayList<>(Arrays.asList("b", "a"));
        SavedTags.sort(original);
        assertEquals(Arrays.asList("b", "a"), original);
    }

    // ---------------------------------------------------------------------
    // Validation
    // ---------------------------------------------------------------------

    @Test
    public void twoCharactersIsTooShortAndThreeIsNot() {
        assertEquals(
                SavedTags.Validation.TOO_SHORT,
                SavedTags.validate("ab", Collections.emptyList(), null, reserved()));
        assertEquals(
                SavedTags.Validation.OK,
                SavedTags.validate("abc", Collections.emptyList(), null, reserved()));
    }

    @Test
    public void lengthIsMeasuredAfterTrimming() {
        assertEquals(
                SavedTags.Validation.TOO_SHORT,
                SavedTags.validate("  ab  ", Collections.emptyList(), null, reserved()));
        assertEquals(
                SavedTags.Validation.OK,
                SavedTags.validate("  abc  ", Collections.emptyList(), null, reserved()));
    }

    @Test
    public void whitespaceOnlyIsTooShort() {
        assertEquals(
                SavedTags.Validation.TOO_SHORT,
                SavedTags.validate("     ", Collections.emptyList(), null, reserved()));
    }

    @Test
    public void duplicatesAreRejectedRegardlessOfCase() {
        final List<String> existing = Collections.singletonList("Recipes");
        assertEquals(
                SavedTags.Validation.DUPLICATE,
                SavedTags.validate("recipes", existing, null, reserved()));
        assertEquals(
                SavedTags.Validation.DUPLICATE,
                SavedTags.validate("RECIPES", existing, null, reserved()));
        assertEquals(
                SavedTags.Validation.DUPLICATE,
                SavedTags.validate("  Recipes  ", existing, null, reserved()));
    }

    @Test
    public void builtinNamesAreReserved() {
        // Otherwise a user tag would shadow a built-in and the filter would show two identical rows.
        for (String name : Arrays.asList("All", "all", "POSTS", "Comments", " comments ")) {
            assertEquals(
                    name,
                    SavedTags.Validation.RESERVED,
                    SavedTags.validate(name, Collections.emptyList(), null, reserved()));
        }
    }

    @Test
    public void reservedBeatsDuplicate() {
        // A store that somehow already holds "Posts" must still report why the name is refused.
        assertEquals(
                SavedTags.Validation.RESERVED,
                SavedTags.validate(
                        "Posts", Collections.singletonList("Posts"), null, reserved()));
    }

    @Test
    public void renamingATagDoesNotCollideWithItself() {
        final List<String> existing = Arrays.asList("Recipes", "Woodworking");
        assertEquals(
                SavedTags.Validation.OK,
                SavedTags.validate("recipes", existing, "Recipes", reserved()));
        assertEquals(
                SavedTags.Validation.OK,
                SavedTags.validate("Recipes and more", existing, "Recipes", reserved()));
    }

    @Test
    public void renamingOntoAnotherTagIsStillADuplicate() {
        final List<String> existing = Arrays.asList("Recipes", "Woodworking");
        assertEquals(
                SavedTags.Validation.DUPLICATE,
                SavedTags.validate("woodworking", existing, "Recipes", reserved()));
    }

    // ---------------------------------------------------------------------
    // findName
    // ---------------------------------------------------------------------

    @Test
    public void findNameReturnsTheStoredSpelling() {
        assertEquals(
                "Recipes",
                SavedTags.findName(Arrays.asList("Recipes", "Woodworking"), "  recipes "));
    }

    @Test
    public void findNameReturnsNullWhenAbsent() {
        assertNull(SavedTags.findName(Collections.singletonList("Recipes"), "Baking"));
    }

    // ---------------------------------------------------------------------
    // De-duplication
    // ---------------------------------------------------------------------

    @Test
    public void dedupeReturnsTheSameListWhenThereAreNoDuplicates() throws Exception {
        final ArrayList<Contribution> items = listing(post("t3_a"), comment("t1_b"));
        // Identity, not just equality: the common case must not allocate a copy.
        assertSame(items, SavedTags.dedupe(items));
    }

    @Test
    public void dedupeKeepsTheFirstOccurrenceAndTheOrder() throws Exception {
        final ArrayList<Contribution> items =
                listing(post("t3_a"), comment("t1_b"), post("t3_a"), post("t3_c"));
        assertEquals(
                Arrays.asList("t3_a", "t1_b", "t3_c"), fullnames(SavedTags.dedupe(items)));
    }

    @Test
    public void dedupeDoesNotConflateAPostAndACommentWithTheSameId() throws Exception {
        // Different kinds, so the fullnames differ even though the ids do not.
        final ArrayList<Contribution> items = listing(post("t3_abc"), comment("t1_abc"));
        assertSame(items, SavedTags.dedupe(items));
    }

    @Test
    public void dedupeHandlesAnEmptyListing() {
        final ArrayList<Contribution> items = listing();
        assertSame(items, SavedTags.dedupe(items));
    }
}
