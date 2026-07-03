package me.edgan.redditslide.test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;

import me.edgan.redditslide.util.TimeUtils;
import net.dean.jraw.paginators.TimePeriod;
import org.junit.Test;

/**
 * Pure-JVM tests for the string→enum mapping in {@link TimeUtils}. The {@code Context}-dependent
 * formatting methods (getTimeAgo/getTimeSince) are covered separately under Robolectric if needed.
 */
public class TimeUtilsTest {

    @Test
    public void mapsLowercaseNames() {
        assertThat(TimeUtils.stringToTimePeriod("hour"), is(TimePeriod.HOUR));
        assertThat(TimeUtils.stringToTimePeriod("week"), is(TimePeriod.WEEK));
        assertThat(TimeUtils.stringToTimePeriod("all"), is(TimePeriod.ALL));
    }

    @Test
    public void isCaseInsensitive() {
        assertThat(TimeUtils.stringToTimePeriod("HOUR"), is(TimePeriod.HOUR));
        assertThat(TimeUtils.stringToTimePeriod("Month"), is(TimePeriod.MONTH));
    }

    @Test
    public void unknownStringReturnsNull() {
        assertThat(TimeUtils.stringToTimePeriod("not-a-period"), is(nullValue()));
    }

    @Test
    public void emptyStringReturnsNull() {
        assertThat(TimeUtils.stringToTimePeriod(""), is(nullValue()));
    }
}
