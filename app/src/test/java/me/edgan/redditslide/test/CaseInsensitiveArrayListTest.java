package me.edgan.redditslide.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import me.edgan.redditslide.CaseInsensitiveArrayList;
import org.junit.Test;

/**
 * Characterization tests for {@link CaseInsensitiveArrayList}. Only {@code contains} is overridden
 * to be case-insensitive; {@code indexOf}/{@code remove} keep the case-sensitive {@link
 * java.util.ArrayList} behavior — these tests pin that (surprising) split so a future change is
 * visible.
 */
public class CaseInsensitiveArrayListTest {

    @Test
    public void containsIsCaseInsensitive() {
        CaseInsensitiveArrayList list = new CaseInsensitiveArrayList();
        list.add("pics");
        assertTrue(list.contains("pics"));
        assertTrue(list.contains("PICS"));
        assertTrue(list.contains("Pics"));
        assertFalse(list.contains("other"));
    }

    @Test
    public void emptyListContainsNothing() {
        assertFalse(new CaseInsensitiveArrayList().contains("anything"));
    }

    @Test
    public void containsAfterAddDifferentCase() {
        CaseInsensitiveArrayList list = new CaseInsensitiveArrayList();
        list.add("Test");
        assertTrue(list.contains("test"));
        assertTrue(list.contains("TEST"));
    }

    @Test
    public void listCopyConstructorPreservesCaseInsensitiveContains() {
        CaseInsensitiveArrayList list =
                new CaseInsensitiveArrayList(Arrays.asList("Alpha", "Beta"));
        assertTrue(list.contains("alpha"));
        assertTrue(list.contains("BETA"));
    }

    @Test
    public void copyConstructorFromCaseInsensitiveList() {
        CaseInsensitiveArrayList source = new CaseInsensitiveArrayList();
        source.add("Gamma");
        CaseInsensitiveArrayList copy = new CaseInsensitiveArrayList(source);
        assertTrue(copy.contains("gamma"));
    }

    @Test
    public void indexOfRemainsCaseSensitive() {
        CaseInsensitiveArrayList list = new CaseInsensitiveArrayList();
        list.add("pics");
        assertEquals(0, list.indexOf("pics"));
        assertEquals(-1, list.indexOf("PICS"));
    }

    @Test
    public void removeRemainsCaseSensitive() {
        CaseInsensitiveArrayList list = new CaseInsensitiveArrayList();
        list.add("pics");
        // remove(Object) uses case-sensitive equals, so a different case does NOT remove it.
        assertFalse(list.remove("PICS"));
        assertTrue(list.contains("pics"));
        // Exact case removes it.
        assertTrue(list.remove("pics"));
        assertFalse(list.contains("pics"));
    }
}
