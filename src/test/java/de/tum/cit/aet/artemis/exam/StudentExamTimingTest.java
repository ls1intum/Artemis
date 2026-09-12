package de.tum.cit.aet.artemis.exam;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZonedDateTime;

import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.exam.domain.Exam;
import de.tum.cit.aet.artemis.exam.domain.ExamMode;
import de.tum.cit.aet.artemis.exam.domain.StudentExam;

class StudentExamTimingTest {

    private final ZonedDateTime start = ZonedDateTime.parse("2026-09-10T10:00:00Z");

    @Test
    void simulationAndPracticeUseTheSameTimingInEntityAndProjection() {
        Exam exam = new Exam();
        exam.setExamMode(ExamMode.TEST_WITH_SIMULATION);
        exam.setStartDate(start);
        exam.setWorkingTime(3600);
        exam.setGracePeriod(60);
        for (ZonedDateTime attemptStart : new ZonedDateTime[] { start.plusMinutes(10), start.plusHours(1), start.plusHours(2) }) {
            ZonedDateTime expectedEnd = attemptStart.isBefore(exam.getSimulationEndDate()) ? start.plusSeconds(4200) : attemptStart.plusSeconds(4200);
            assertThat(StudentExam.individualEndDate(exam, attemptStart, 4200)).isEqualTo(expectedEnd);
            assertThat(StudentExam.individualEndDate(exam.getExamMode(), exam.getSimulationEndDate(), start, attemptStart, 4200)).isEqualTo(expectedEnd);
            assertThat(StudentExam.individualEndDateWithGracePeriod(exam, attemptStart, 4200)).isEqualTo(expectedEnd.plusSeconds(60));
            assertThat(StudentExam.individualEndDateWithGracePeriod(exam.getExamMode(), exam.getSimulationEndDate(), start, 60, attemptStart, 4200))
                    .isEqualTo(expectedEnd.plusSeconds(60));
        }
    }

    @Test
    void unstartedTestExamHasNoIndividualEndDate() {
        assertThat(StudentExam.individualEndDate(ExamMode.TEST, null, start, null, 3600)).isNull();
        assertThat(StudentExam.individualEndDateWithGracePeriod(ExamMode.TEST, null, start, 60, null, 3600)).isNull();
    }

    @Test
    void realExamUsesScheduledStartEvenWhenStudentStartsLate() {
        assertThat(StudentExam.individualEndDate(ExamMode.REAL, null, start, start.plusMinutes(10), 3600)).isEqualTo(start.plusHours(1));
        assertThat(StudentExam.individualEndDateWithGracePeriod(ExamMode.REAL, null, start, null, start.plusMinutes(10), 3600)).isEqualTo(start.plusHours(1));
    }
}
