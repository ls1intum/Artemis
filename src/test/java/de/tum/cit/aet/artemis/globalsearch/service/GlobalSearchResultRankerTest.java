package de.tum.cit.aet.artemis.globalsearch.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.globalsearch.config.schema.entityschemas.SearchableEntitySchema;

class GlobalSearchResultRankerTest {

    private static final ZonedDateTime NOW = ZonedDateTime.parse("2026-10-13T12:00:00Z");

    private final Course running = course(1L, "Software Engineering WS26/27", NOW.plusMonths(3));

    private final Course lastSemester = course(2L, "Software Engineering SS26", NOW.minusMonths(3));

    private final Course longAgo = course(3L, "Software Engineering WS22/23", NOW.minusYears(3));

    private final Course otherCourse = course(4L, "Databases", NOW.minusMonths(3));

    private final Map<Long, Course> courses = Map.of(1L, running, 2L, lastSemester, 3L, longAgo, 4L, otherCourse);

    @Test
    void ranksARowOfARunningCourseAboveASlightlyBetterMatchOfAnEndedCourse() {
        var ended = row("exercise", 4L, "Normalization");
        var current = row("exercise", 1L, "Requirements");

        assertThat(GlobalSearchResultRanker.rank(List.of(ended, current), true, courses, NOW)).containsExactly(current, ended);
    }

    @Test
    void keepsAMuchBetterMatchOfAnEndedCourseAboveAWeakMatchOfARunningCourse() {
        var ended = row("exercise", 3L, "Sorting");
        var filler = List.of(row("lecture", 4L, "a"), row("lecture", 4L, "b"), row("lecture", 4L, "c"), row("lecture", 4L, "d"), row("lecture", 4L, "e"), row("lecture", 4L, "f"));
        var weakCurrent = row("exercise", 1L, "Something else");
        var rows = new java.util.ArrayList<Map<String, Object>>();
        rows.add(ended);
        rows.addAll(filler);
        rows.add(weakCurrent);

        var ranked = GlobalSearchResultRanker.rank(rows, true, courses, NOW);

        assertThat(ranked.indexOf(ended)).isLessThan(ranked.indexOf(weakCurrent));
    }

    @Test
    void showsAnItemFromSeveralSemestersOnceFromTheNewestCourse() {
        var old = row("exercise", 3L, "Bumpers");
        var previous = row("exercise", 2L, "Bumpers");
        var current = row("exercise", 1L, "Bumpers");

        assertThat(GlobalSearchResultRanker.rank(List.of(old, previous, current), true, courses, NOW)).containsExactly(current);
    }

    @Test
    void keepsItemsWithTheSameTitleInDifferentCourses() {
        var softwareEngineering = row("channel", 1L, "general");
        var databases = row("channel", 4L, "general");

        assertThat(GlobalSearchResultRanker.rank(List.of(softwareEngineering, databases), true, courses, NOW)).containsExactlyInAnyOrder(softwareEngineering, databases);
    }

    @Test
    void neverCollapsesCourseRows() {
        var current = row("course", 1L, "Software Engineering WS26/27");
        var previous = row("course", 2L, "Software Engineering SS26");

        assertThat(GlobalSearchResultRanker.rank(List.of(previous, current), true, courses, NOW)).hasSize(2);
    }

    @Test
    void sortsABrowseByCourseNewestFirstAndThenByTitle() {
        var oldRow = row("lecture", 3L, "Intro");
        var databasesRow = row("lecture", 4L, "Joins");
        var runningB = row("lecture", 1L, "Testing");
        var runningA = row("lecture", 1L, "Architecture");

        assertThat(GlobalSearchResultRanker.rank(List.of(oldRow, databasesRow, runningB, runningA), false, courses, NOW)).containsExactly(runningA, runningB, databasesRow, oldRow);
    }

    @Test
    void keepsRowsOfUnknownCoursesAtFullScore() {
        var unknown = row("exercise", 99L, "Sorting");
        var ended = row("exercise", 4L, "Normalization");

        assertThat(GlobalSearchResultRanker.rank(List.of(unknown, ended), true, courses, NOW)).containsExactly(unknown, ended);
    }

    private static Course course(long id, String title, ZonedDateTime endDate) {
        var course = new Course();
        course.setId(id);
        course.setTitle(title);
        course.setStartDate(endDate.minusMonths(6));
        course.setEndDate(endDate);
        return course;
    }

    private static Map<String, Object> row(String type, long courseId, String title) {
        return Map.of(SearchableEntitySchema.Properties.TYPE, type, SearchableEntitySchema.Properties.COURSE_ID, courseId, SearchableEntitySchema.Properties.TITLE, title);
    }
}
