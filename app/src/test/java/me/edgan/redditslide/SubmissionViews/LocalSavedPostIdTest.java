package me.edgan.redditslide.SubmissionViews;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import android.app.Application;
import androidx.test.core.app.ApplicationProvider;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lusfold.androidkeyvaluestore.KVStore;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import me.edgan.redditslide.util.KVStoreBackup;
import me.edgan.redditslide.util.ThingRef;
import net.dean.jraw.models.Comment;
import net.dean.jraw.models.Submission;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/**
 * A Local Saved comment keeps its post id beside the save time, since Reddit cannot fetch a comment
 * from its id alone once {@code /api/info} is out of reach. Markers written before that have only
 * the save time, and still read.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33, application = Application.class)
public class LocalSavedPostIdTest {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Before
    public void setUp() {
        KVStore.init(ApplicationProvider.getApplicationContext(), "SEEN");
        KVStore.getInstance().clearTable();
    }

    @After
    public void tearDown() {
        KVStore.getInstance().clearTable();
    }

    private static Comment comment(String linkId) throws Exception {
        return new Comment(
                MAPPER.readTree("{\"name\":\"t1_c1\",\"id\":\"c1\",\"link_id\":" + linkId + "}"));
    }

    @Test
    public void aCommentsPostIdIsItsLinkIdWithoutThePrefix() throws Exception {
        assertEquals("post1", LocalSaved.postIdOf(comment("\"t3_post1\"")));
        assertNull(LocalSaved.postIdOf(comment("\"\"")));
        assertNull(LocalSaved.postIdOf(comment("\"t3_\"")));
        assertNull(LocalSaved.postIdOf(comment("null")));
        assertNull(
                LocalSaved.postIdOf(
                        new Submission(
                                MAPPER.readTree(
                                        "{\"name\":\"t3_p1\",\"id\":\"p1\",\"saved\":false}"))));
    }

    @Test
    public void promotedThingsReadWithTheirPostIdsNewestFirst() {
        KVStore.getInstance()
                .insert(LocalSaved.PROMOTED_PREFIX + "t1_c1", LocalSaved.markerValue(1000, "post1"));
        // Saved before the post id was kept.
        KVStore.getInstance().insert(LocalSaved.PROMOTED_PREFIX + "t1_c2", "2000");
        KVStore.getInstance()
                .insert(LocalSaved.PROMOTED_PREFIX + "t3_p1", LocalSaved.markerValue(3000, null));
        // Still pending, so not shown.
        KVStore.getInstance().insert(LocalSaved.PENDING_PREFIX + "t1_c3", "4000:post3");

        List<String> read = new ArrayList<>();
        for (ThingRef ref : LocalSaved.getPromoted()) {
            read.add(ref.fullname + "/" + ref.postId);
        }

        assertEquals(Arrays.asList("t3_p1/null", "t1_c2/null", "t1_c1/post1"), read);
    }

    @Test
    public void theBackupCarriesThePostId() {
        KVStore.getInstance()
                .insert(LocalSaved.PROMOTED_PREFIX + "t1_c1", LocalSaved.markerValue(1000, "post1"));
        String backup = KVStoreBackup.export();
        KVStore.getInstance().clearTable();

        KVStoreBackup.restore(backup);

        assertEquals("post1", LocalSaved.getPromoted().get(0).postId);
    }

    @Test
    public void anOlderBackupRestoresWithNoPostId() {
        KVStoreBackup.restore(LocalSaved.PROMOTED_PREFIX + "t1_c1\t1000\n");

        ThingRef ref = LocalSaved.getPromoted().get(0);
        assertEquals("t1_c1", ref.fullname);
        assertNull(ref.postId);
    }
}
