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
    void prefersTheRunningCourseAmongEquallyRelevantCopiesInAnyOrder() {
        // Identical copies get the same relevance, so Weaviate may return the running course's copy last.
        var previous = row("exercise", 2L, "Bumpers");
        var old = row("exercise", 3L, "Bumpers");
        var current = row("exercise", 1L, "Bumpers");

        assertThat(GlobalSearchResultRanker.rank(List.of(previous, old, current), true, courses, NOW)).containsExactly(current);
    }

    @Test
    void keepsAMuchBetterMatchOfAnEndedCopyThatIsNotNextToTheRunningCopy() {
        var ended = row("exercise", 2L, "Bumpers");
        var filler = List.of(row("lecture", 4L, "a"), row("lecture", 4L, "b"), row("lecture", 4L, "c"), row("lecture", 4L, "d"), row("lecture", 4L, "e"), row("lecture", 4L, "f"));
        var current = row("exercise", 1L, "Bumpers");
        var rows = new java.util.ArrayList<Map<String, Object>>();
        rows.add(ended);
        rows.addAll(filler);
        rows.add(current);

        var ranked = GlobalSearchResultRanker.rank(rows, true, courses, NOW);

        assertThat(ranked).contains(ended).doesNotContain(current);
    }

    @Test
    void placesTheDefaultChannelOfAnExerciseDirectlyBehindTheExercise() {
        var channel = row("channel", 1L, "exercise-observer-pattern-imp");
        var lecture = row("lecture", 1L, "Design Patterns");
        var exercise = row("exercise", 1L, "Observer Pattern Implementation");

        assertThat(GlobalSearchResultRanker.rank(List.of(channel, lecture, exercise), true, courses, NOW)).containsExactly(lecture, exercise, channel);
    }

    @Test
    void matchesTheDefaultChannelOfAnItemWithAccentedTitle() {
        var channel = row("channel", 1L, "exercise-einf-hrung-in-java");
        var exercise = row("exercise", 1L, "Einführung in Java");

        assertThat(GlobalSearchResultRanker.rank(List.of(channel, exercise), true, courses, NOW)).containsExactly(exercise, channel);
    }

    @Test
    void leavesChannelsAloneThatBelongToNoItemInTheList() {
        var channel = row("channel", 1L, "tech-support");
        var otherCourseChannel = row("channel", 1L, "exercise-sorting");
        var exercise = row("exercise", 4L, "Sorting Algorithms");

        assertThat(GlobalSearchResultRanker.rank(List.of(channel, otherCourseChannel, exercise), true, courses, NOW)).containsExactly(channel, otherCourseChannel, exercise);
    }

    @Test
    void keepsItemsWithTheSameTitleWithinOneCourse() {
        var first = row("exercise", 1L, "Bonus");
        var second = new java.util.HashMap<>(row("exercise", 1L, "Bonus"));
        second.put(SearchableEntitySchema.Properties.ENTITY_ID, 2L);

        assertThat(GlobalSearchResultRanker.rank(List.<Map<String, Object>>of(first, second), true, courses, NOW)).hasSize(2);
    }

    @Test
    void neverMergesLectureUnitsWhoseNamesRepeatAcrossLectures() {
        var current = row("lecture_unit", 1L, "Slides");
        var previous = row("lecture_unit", 2L, "Slides");

        assertThat(GlobalSearchResultRanker.rank(List.of(current, previous), true, courses, NOW)).hasSize(2);
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
    void leavesABrowseWithoutAQueryUnchanged() {
        var oldRow = row("lecture", 3L, "Intro");
        var runningRow = row("lecture", 1L, "Testing");
        var copy = row("lecture", 2L, "Testing");

        assertThat(GlobalSearchResultRanker.rank(List.of(oldRow, runningRow, copy), false, courses, NOW)).containsExactly(oldRow, runningRow, copy);
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
