package me.edgan.redditslide.Adapters;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.mock;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import net.dean.jraw.models.Comment;
import net.dean.jraw.models.CommentNode;
import net.dean.jraw.models.CommentSort;
import org.junit.Before;
import org.junit.Test;

public class CommentAdapterHiddenTest {
    private CommentAdapter adapter;
    private List<CommentObject> rows;

    @Before
    public void setUp() {
        adapter = mock(CommentAdapter.class);
        adapter.hidden = new HashSet<>();
        doCallRealMethod().when(adapter).inheritHidden(anyString(), anyBoolean(), any());

        ObjectNode data = new ObjectMapper().createObjectNode();
        data.put("id", "new");
        data.put("name", "t1_new");
        data.put("replies", "");
        CommentNode root =
                new CommentNode(
                        "t3_post",
                        Collections.singletonList(new Comment(data)),
                        null,
                        CommentSort.CONFIDENCE);
        rows = new ArrayList<>(Arrays.asList(new CommentItem(root.get(0))));
    }

    @Test
    public void rowsLoadedUnderACollapsedParentStayHiddenAndTheFinishedPlaceholderGoes() {
        adapter.hidden.add("t1_parentmore");

        adapter.inheritHidden("t1_parentmore", false, rows);

        assertEquals(new HashSet<>(Collections.singletonList("t1_new")), adapter.hidden);
    }

    @Test
    public void aPlaceholderThatStaysStaysHiddenToo() {
        adapter.hidden.add("t1_parentmore");

        adapter.inheritHidden("t1_parentmore", true, rows);

        assertEquals(new HashSet<>(Arrays.asList("t1_new", "t1_parentmore")), adapter.hidden);
    }

    @Test
    public void rowsUnderAVisiblePlaceholderStayVisible() {
        adapter.inheritHidden("t1_parentmore", false, rows);

        assertFalse(adapter.hidden.contains("t1_new"));
    }
}
