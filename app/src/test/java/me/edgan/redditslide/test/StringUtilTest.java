package me.edgan.redditslide.test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

import java.util.ArrayList;
import java.util.Arrays;
import me.edgan.redditslide.util.StringUtil;
import org.junit.Test;

/** Pure-JVM tests for {@link StringUtil}. */
public class StringUtilTest {

    private static ArrayList<String> list(String... items) {
        return new ArrayList<>(Arrays.asList(items));
    }

    @Test
    public void arrayToString_defaultCommaSeparator() {
        assertThat(StringUtil.arrayToString(list("a", "b", "c")), is("a,b,c"));
    }

    @Test
    public void arrayToString_singleElementHasNoTrailingSeparator() {
        assertThat(StringUtil.arrayToString(list("only")), is("only"));
    }

    @Test
    public void arrayToString_customMultiCharSeparator() {
        assertThat(StringUtil.arrayToString(list("a", "b"), "--"), is("a--b"));
    }

    @Test
    public void arrayToString_emptyListIsEmptyString() {
        assertThat(StringUtil.arrayToString(list()), is(""));
    }

    @Test
    public void arrayToString_nullIsEmptyString() {
        assertThat(StringUtil.arrayToString(null), is(""));
    }

    @Test
    public void stringToArray_splitsOnComma() {
        assertThat(StringUtil.stringToArray("a,b,c"), is(list("a", "b", "c")));
    }

    @Test
    public void stringToArray_emptyStringYieldsSingleEmptyElement() {
        // "".split(",") -> [""], so the result is a one-element list containing "".
        assertThat(StringUtil.stringToArray(""), is(list("")));
    }

    @Test
    public void arrayToString_stringToArray_roundTrip() {
        ArrayList<String> original = list("x", "y", "z");
        assertThat(StringUtil.stringToArray(StringUtil.arrayToString(original)), is(original));
    }

    @Test
    public void abbreviate_shorterThanMaxUnchanged() {
        assertThat(StringUtil.abbreviate("hello", 10), is("hello"));
    }

    @Test
    public void abbreviate_exactlyMaxUnchanged() {
        assertThat(StringUtil.abbreviate("hello", 5), is("hello"));
    }

    @Test
    public void abbreviate_longerThanMaxTruncatedWithEllipsis() {
        String result = StringUtil.abbreviate("hello!", 5);
        assertThat(result, is("he..."));
        assertThat(result.length(), is(5));
    }

    @Test
    public void stripAllWhitespace_removesEveryWhitespaceChar() {
        assertThat(StringUtil.stripAllWhitespace("a b\tc\nd"), is("abcd"));
    }

    @Test
    public void stripAllWhitespace_nullIsEmptyString() {
        assertThat(StringUtil.stripAllWhitespace(null), is(""));
    }

    @Test
    public void stripLeadingTrailingWhitespace_trimsEnds() {
        assertThat(StringUtil.stripLeadingTrailingWhitespace("  a b  "), is("a b"));
    }

    @Test
    public void stripLeadingTrailingWhitespace_nullIsEmptyString() {
        assertThat(StringUtil.stripLeadingTrailingWhitespace(null), is(""));
    }

    @Test
    public void sanitizeString_keepsOnlyAllowedChars() {
        assertThat(StringUtil.sanitizeString("Hello World-1!@#_"), is("HelloWorld-1_"));
    }

    @Test
    public void sanitizeString_allDisallowedYieldsEmpty() {
        assertThat(StringUtil.sanitizeString("!@# $%^"), is(""));
    }
}
