package me.edgan.redditslide.util;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/**
 * {@link MiscUtil#idFromFullname}, which every permalink and API id in the app is built out of.
 *
 * <p>Its comment records why the length test is there: coalescing a null fullname to {@code ""}
 * and then calling {@code substring(3)} trades an NPE for a StringIndexOutOfBoundsException. The
 * guard was uncovered, so relaxing the length test left the whole suite green.
 */
public class MiscUtilTest {

    @Test
    public void theKindPrefixIsStripped() {
        assertEquals("abc123", MiscUtil.idFromFullname("t3_abc123"));
        assertEquals("def456", MiscUtil.idFromFullname("t1_def456"));
    }

    @Test
    public void aNullFullnameIsEmptyRatherThanAThrow() {
        assertEquals("", MiscUtil.idFromFullname(null));
    }

    /** The boundary the guard exists for: too short to carry an id at all. */
    @Test
    public void aFullnameTooShortToCarryAnIdIsEmptyRatherThanAThrow() {
        assertEquals("", MiscUtil.idFromFullname(""));
        assertEquals("", MiscUtil.idFromFullname("t"));
        assertEquals("", MiscUtil.idFromFullname("t3"));
    }

    /** Exactly the prefix and nothing else: three characters, so the substring is empty. */
    @Test
    public void aBareKindPrefixYieldsAnEmptyId() {
        assertEquals("", MiscUtil.idFromFullname("t3_"));
    }

    @Test
    public void orEmptyReplacesOnlyNull() {
        assertEquals("", MiscUtil.orEmpty(null));
        assertEquals("kept", MiscUtil.orEmpty("kept"));
    }
}
