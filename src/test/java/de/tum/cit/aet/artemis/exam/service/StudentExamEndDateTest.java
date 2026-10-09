package de.tum.cit.aet.artemis.exam.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZonedDateTime;

import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.exam.domain.StudentExam;

class StudentExamEndDateTest {

    private static final ZonedDateTime EXAM_START = ZonedDateTime.parse("2030-01-01T10:00:00Z");

    private static final ZonedDateTime STARTED = ZonedDateTime.parse("2030-02-01T08:00:00Z");

    @Test
    void testExamUsesStartedDateWithGracePeriod() {
        var end = StudentExam.individualEndDateWithGracePeriod(true, EXAM_START, 30, STARTED, 3600);

        assertThat(end.toInstant()).isEqualTo(STARTED.plusSeconds(3630).toInstant());
    }

    @Test
    void testExamWithoutStartedDateHasNoEnd() {
        assertThat(StudentExam.individualEndDateWithGracePeriod(true, EXAM_START, 30, null, 3600)).isNull();
    }

    @Test
    void realExamUsesExamStartDateAndTreatsMissingGracePeriodAsZero() {
        var end = StudentExam.individualEndDateWithGracePeriod(false, EXAM_START, null, null, 7200);

        assertThat(end.toInstant()).isEqualTo(EXAM_START.plusSeconds(7200).toInstant());
    }

    @Test
    void workingTimeAndGracePeriodAreAddedWithoutIntOverflow() {
        var end = StudentExam.individualEndDateWithGracePeriod(false, EXAM_START, Integer.MAX_VALUE, null, Integer.MAX_VALUE);

        assertThat(end.toInstant()).isEqualTo(EXAM_START.plusSeconds(2L * Integer.MAX_VALUE).toInstant());
    }
}
