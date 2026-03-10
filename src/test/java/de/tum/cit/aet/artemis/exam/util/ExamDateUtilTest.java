package de.tum.cit.aet.artemis.exam.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Comparator;

import org.junit.jupiter.api.Test;

class ExamDateUtilTest {

    @Test
    void testTruncatedComparatorWithIdenticalDates() {
        Comparator<ZonedDateTime> comparator = ExamDateUtil.truncatedComparator();
        ZonedDateTime date1 = ZonedDateTime.of(2025, 2, 27, 12, 30, 45, 0, ZoneId.of("UTC"));
        ZonedDateTime date2 = ZonedDateTime.of(2025, 2, 27, 12, 30, 45, 0, ZoneId.of("UTC"));

        assertThat(comparator.compare(date1, date2)).isZero();
    }

    @Test
    void testTruncatedComparatorWithDifferentMilliseconds() {
        Comparator<ZonedDateTime> comparator = ExamDateUtil.truncatedComparator();
        // Same date but different milliseconds - should be equal after truncation
        ZonedDateTime date1 = ZonedDateTime.of(2025, 2, 27, 12, 30, 45, 123_000_000, ZoneId.of("UTC"));
        ZonedDateTime date2 = ZonedDateTime.of(2025, 2, 27, 12, 30, 45, 987_000_000, ZoneId.of("UTC"));

        assertThat(comparator.compare(date1, date2)).isZero();
    }

    @Test
    void testTruncatedComparatorWithDifferentNanoseconds() {
        Comparator<ZonedDateTime> comparator = ExamDateUtil.truncatedComparator();
        // Same date but different nanoseconds - should be equal after truncation to seconds
        ZonedDateTime date1 = ZonedDateTime.of(2025, 2, 27, 12, 30, 45, 100, ZoneId.of("UTC"));
        ZonedDateTime date2 = ZonedDateTime.of(2025, 2, 27, 12, 30, 45, 999_999_999, ZoneId.of("UTC"));

        assertThat(comparator.compare(date1, date2)).isZero();
    }

    @Test
    void testTruncatedComparatorWithDifferentSeconds() {
        Comparator<ZonedDateTime> comparator = ExamDateUtil.truncatedComparator();
        ZonedDateTime date1 = ZonedDateTime.of(2025, 2, 27, 12, 30, 45, 0, ZoneId.of("UTC"));
        ZonedDateTime date2 = ZonedDateTime.of(2025, 2, 27, 12, 30, 46, 0, ZoneId.of("UTC"));

        assertThat(comparator.compare(date1, date2)).isNegative();
        assertThat(comparator.compare(date2, date1)).isPositive();
    }

    @Test
    void testTruncatedComparatorWithDifferentTimezonesSameInstant() {
        Comparator<ZonedDateTime> comparator = ExamDateUtil.truncatedComparator();
        // Same instant in time but different timezones
        ZonedDateTime dateUtc = ZonedDateTime.of(2025, 2, 27, 12, 30, 45, 0, ZoneId.of("UTC"));
        ZonedDateTime dateNewYork = ZonedDateTime.of(2025, 2, 27, 7, 30, 45, 0, ZoneId.of("America/New_York"));

        // Both represent the same instant in time, so should be equal
        assertThat(comparator.compare(dateUtc, dateNewYork)).isZero();
    }

    @Test
    void testTruncatedComparatorWithDifferentTimezonesDifferentInstant() {
        Comparator<ZonedDateTime> comparator = ExamDateUtil.truncatedComparator();
        // Different instants in time with different timezones
        ZonedDateTime dateUtc = ZonedDateTime.of(2025, 2, 27, 12, 30, 45, 0, ZoneId.of("UTC"));
        ZonedDateTime dateNewYork = ZonedDateTime.of(2025, 2, 27, 12, 30, 45, 0, ZoneId.of("America/New_York"));

        // These represent different instants (5 hours apart), so should not be equal
        assertThat(comparator.compare(dateUtc, dateNewYork)).isNegative();
        assertThat(comparator.compare(dateNewYork, dateUtc)).isPositive();
    }

    @Test
    void testTruncatedComparatorWithEarlierDate() {
        Comparator<ZonedDateTime> comparator = ExamDateUtil.truncatedComparator();
        ZonedDateTime earlierDate = ZonedDateTime.of(2025, 2, 26, 12, 30, 45, 0, ZoneId.of("UTC"));
        ZonedDateTime laterDate = ZonedDateTime.of(2025, 2, 27, 12, 30, 45, 0, ZoneId.of("UTC"));

        assertThat(comparator.compare(earlierDate, laterDate)).isNegative();
        assertThat(comparator.compare(laterDate, earlierDate)).isPositive();
    }

    @Test
    void testTruncatedComparatorSimulatesDatabasePrecisionLoss() {
        Comparator<ZonedDateTime> comparator = ExamDateUtil.truncatedComparator();
        // Simulate a date as stored in the application (with full precision)
        ZonedDateTime originalDate = ZonedDateTime.of(2025, 2, 27, 12, 30, 45, 123_456_789, ZoneId.of("UTC"));
        // Simulate the same date as retrieved from the database (precision lost)
        ZonedDateTime databaseDate = ZonedDateTime.of(2025, 2, 27, 12, 30, 45, 0, ZoneId.of("UTC"));

        // These should be considered equal after truncation
        assertThat(comparator.compare(originalDate, databaseDate)).isZero();
    }
}
