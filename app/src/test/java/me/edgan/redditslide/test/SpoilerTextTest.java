package me.edgan.redditslide.test;

import static org.junit.Assert.fail;

import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;
import me.edgan.redditslide.SpoilerRobotoTextView;
import org.junit.Before;
import org.junit.Test;

public class SpoilerTextTest {
    private Pattern htmlSpoilerPattern;
    private Pattern nativeSpoilerPattern;

    @Before
    public void setUp() {
        htmlSpoilerPattern = SpoilerRobotoTextView.htmlSpoilerPattern;
        nativeSpoilerPattern = SpoilerRobotoTextView.nativeSpoilerPattern;
    }

    private final List<Object[]> htmlSpoilerTests =
            Arrays.asList(
                    new Object[] {"<a href=\"#spoiler\">test</a>", true},
                    new Object[] {"<a href=\"#sp\">test</a>", true},
                    new Object[] {"<a href=\"#s\">test</a>", true},
                    new Object[] {"<a href=\"#not-a-spoiler\">test</a>", false});

    private final List<Object[]> nativeSpoilerTests =
            Arrays.asList(
                    new Object[] {"<span class=\"md-spoiler-text\">test</span>", true},
                    new Object[] {
                        "<span class=\"md-bold-text md-spoiler-text md-italic-text\">test</span>",
                        true
                    },
                    new Object[] {"<span class=\"not-a-spoiler\">test</span>", false});

    @Test
    public void htmlSpoilerTest() {
        spoilerTest(htmlSpoilerTests, htmlSpoilerPattern, "HTML spoiler test");
    }

    @Test
    public void nativeSpoilerTest() {
        spoilerTest(nativeSpoilerTests, nativeSpoilerPattern, "Native spoiler test");
    }

    private void spoilerTest(List<Object[]> tests, Pattern pattern, String name) {
        for (Object[] test : tests) {
            if (pattern.matcher((String) test[0]).matches() == (Boolean) test[1]) {
                System.out.println(name + ": " + test[0] + " PASSED");
            } else {
                System.out.println(name + ": " + test[0] + " FAILED");
                fail();
            }
        }
    }
}
