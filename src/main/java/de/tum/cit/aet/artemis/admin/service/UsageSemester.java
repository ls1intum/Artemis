package de.tum.cit.aet.artemis.admin.service;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * A university semester, derived from a date instead of the free-text course semester field.
 * Winter semester: October to March. Summer semester: April to September. The boundaries are midnight in a zone given by the caller.
 *
 * @param year   the calendar year in which the semester starts
 * @param winter true for the winter semester starting in October of {@code year}, false for the summer semester starting in April
 */
record UsageSemester(int year, boolean winter) {

    private static final int WINTER_START_MONTH = 10;

    private static final int SUMMER_START_MONTH = 4;

    /**
     * @param date a date
     * @return the semester that contains the date
     */
    static UsageSemester containing(LocalDate date) {
        int month = date.getMonthValue();
        if (month >= WINTER_START_MONTH) {
            return new UsageSemester(date.getYear(), true);
        }
        if (month >= SUMMER_START_MONTH) {
            return new UsageSemester(date.getYear(), false);
        }
        return new UsageSemester(date.getYear() - 1, true);
    }

    /**
     * @param today the current date
     * @param count how many semesters to return
     * @return the semester containing today and its predecessors, newest first
     */
    static List<UsageSemester> lastSemesters(LocalDate today, int count) {
        var result = new ArrayList<UsageSemester>(count);
        var semester = containing(today);
        for (int i = 0; i < count; i++) {
            result.add(semester);
            semester = semester.previous();
        }
        return result;
    }

    UsageSemester previous() {
        return winter ? new UsageSemester(year, false) : new UsageSemester(year - 1, true);
    }

    /**
     * @param zone the zone in which instructors enter course dates, so that a course starting at midnight on the first day belongs to the semester
     * @return the inclusive start of the semester
     */
    ZonedDateTime start(ZoneId zone) {
        return ZonedDateTime.of(year, winter ? WINTER_START_MONTH : SUMMER_START_MONTH, 1, 0, 0, 0, 0, zone);
    }

    /**
     * @param zone the zone in which instructors enter course dates
     * @return the exclusive end of the semester, which is the start of the next one
     */
    ZonedDateTime end(ZoneId zone) {
        return winter ? ZonedDateTime.of(year + 1, SUMMER_START_MONTH, 1, 0, 0, 0, 0, zone) : ZonedDateTime.of(year, WINTER_START_MONTH, 1, 0, 0, 0, 0, zone);
    }

    /**
     * @return "WS 2025/26" for a winter semester, "SS 2026" for a summer semester
     */
    String label() {
        return winter ? "WS %d/%02d".formatted(year, (year + 1) % 100) : "SS %d".formatted(year);
    }
}
