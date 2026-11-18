package de.tum.cit.aet.artemis.core.service.util;

import static de.tum.cit.aet.artemis.core.util.TimeUtil.toRelativeTime;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZonedDateTime;

import org.junit.jupiter.api.Test;

class TimeUtilTest {

    @Test
    void testToRelativeTimeAtStartTime() {
        ZonedDateTime originTime = ZonedDateTime.parse("2020-01-01T00:00:00Z");
        ZonedDateTime unitTime = ZonedDateTime.parse("2020-01-01T01:00:00Z");
        assertThat(toRelativeTime(originTime, unitTime, originTime)).isEqualTo(0);
    }

    @Test
    void testToRelativeTimeAtEndTime() {
        ZonedDateTime originTime = ZonedDateTime.parse("2020-01-01T00:00:00Z");
        ZonedDateTime unitTime = ZonedDateTime.parse("2020-01-01T01:00:00Z");
        assertThat(toRelativeTime(originTime, unitTime, unitTime)).isEqualTo(100);
    }

    @Test
    void testToRelativeTimeInBetween() {
        ZonedDateTime originTime = ZonedDateTime.parse("2020-01-01T00:00:00Z");
        ZonedDateTime unitTime = ZonedDateTime.parse("2020-01-01T10:00:00Z");
        ZonedDateTime targetTime = ZonedDateTime.parse("2020-01-01T02:30:00Z");
        assertThat(toRelativeTime(originTime, unitTime, targetTime)).isEqualTo(25);
    }

}
