package me.edgan.redditslide.test;

import static org.junit.Assert.assertEquals;

import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import me.edgan.redditslide.SpoilerRobotoTextView;
import org.junit.Test;

public class SpoilerTextTest {

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
        spoilerTest(
                htmlSpoilerTests, SpoilerRobotoTextView.htmlSpoilerPattern, "HTML spoiler test");
    }

    @Test
    public void nativeSpoilerTest() {
        spoilerTest(
                nativeSpoilerTests,
                SpoilerRobotoTextView.nativeSpoilerPattern,
                "Native spoiler test");
    }

    /**
     * The emote rescue marks each zero-length anchor so {@code Html.fromHtml} keeps it, and skips
     * the ones that are spoilers. With a greedy quantifier a comment holding both matched them as
     * one span, which contains {@code href="/sp"}, so the emote beside the spoiler was skipped
     * along with it and vanished from the comment.
     */
    @Test
    public void eachZeroLengthAnchorIsMatchedOnItsOwn() {
        Matcher matcher =
                SpoilerRobotoTextView.htmlEmotePattern.matcher(
                        "<a href=\"/sp\"></a> and <a href=\"/emote\"></a>");

        assertEquals(true, matcher.find());
        assertEquals("<a href=\"/sp\"></a>", matcher.group());
        assertEquals(true, matcher.find());
        assertEquals("<a href=\"/emote\"></a>", matcher.group());
        assertEquals(false, matcher.find());
    }

    /** Asserted case by case, so a failure names the input rather than only the suite. */
    private void spoilerTest(List<Object[]> tests, Pattern pattern, String name) {
        for (Object[] test : tests) {
            assertEquals(
                    name + ": " + test[0], test[1], pattern.matcher((String) test[0]).matches());
        }
    }
}
