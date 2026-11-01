package de.tum.cit.aet.artemis.admin.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;

import org.junit.jupiter.api.Test;

class UsageSemesterTest {

    private static final ZoneId BERLIN = ZoneId.of("Europe/Berlin");

    @Test
    void containing_octoberToMarchIsWinterOfTheStartYear() {
        assertThat(UsageSemester.containing(LocalDate.of(2025, 10, 1))).isEqualTo(new UsageSemester(2025, true));
        assertThat(UsageSemester.containing(LocalDate.of(2025, 12, 31))).isEqualTo(new UsageSemester(2025, true));
        assertThat(UsageSemester.containing(LocalDate.of(2026, 1, 1))).isEqualTo(new UsageSemester(2025, true));
        assertThat(UsageSemester.containing(LocalDate.of(2026, 3, 31))).isEqualTo(new UsageSemester(2025, true));
    }

    @Test
    void containing_aprilToSeptemberIsSummer() {
        assertThat(UsageSemester.containing(LocalDate.of(2026, 4, 1))).isEqualTo(new UsageSemester(2026, false));
        assertThat(UsageSemester.containing(LocalDate.of(2026, 9, 30))).isEqualTo(new UsageSemester(2026, false));
    }

    @Test
    void lastSemesters_returnsNewestFirstAcrossYears() {
        var semesters = UsageSemester.lastSemesters(LocalDate.of(2026, 11, 1), 4);

        assertThat(semesters).extracting(UsageSemester::label).containsExactly("WS 2026/27", "SS 2026", "WS 2025/26", "SS 2025");
    }

    @Test
    void boundaries_areHalfOpenAndContiguous() {
        var winter = new UsageSemester(2025, true);
        var summer = new UsageSemester(2026, false);

        assertThat(winter.start(ZoneOffset.UTC)).isEqualTo(ZonedDateTime.of(2025, 10, 1, 0, 0, 0, 0, ZoneOffset.UTC));
        assertThat(winter.end(ZoneOffset.UTC)).isEqualTo(summer.start(ZoneOffset.UTC));
        assertThat(summer.end(ZoneOffset.UTC)).isEqualTo(ZonedDateTime.of(2026, 10, 1, 0, 0, 0, 0, ZoneOffset.UTC));
    }

    @Test
    void boundaries_followTheGivenZone_soAMidnightStartInBerlinBelongsToTheNewSemester() {
        var winter = new UsageSemester(2026, true);
        var courseStart = ZonedDateTime.of(2026, 10, 1, 0, 0, 0, 0, BERLIN);

        assertThat(courseStart).isAfterOrEqualTo(winter.start(BERLIN));
        assertThat(courseStart).isBefore(winter.start(ZoneOffset.UTC));
    }

    @Test
    void label_padsTheTwoDigitEndYear() {
        assertThat(new UsageSemester(2008, true).label()).isEqualTo("WS 2008/09");
        assertThat(new UsageSemester(2099, true).label()).isEqualTo("WS 2099/00");
        assertThat(new UsageSemester(2026, false).label()).isEqualTo("SS 2026");
    }
}
