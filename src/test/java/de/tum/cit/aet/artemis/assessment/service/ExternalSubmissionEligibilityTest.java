package de.tum.cit.aet.artemis.assessment.service;

import static de.tum.cit.aet.artemis.assessment.domain.ExternalSubmissionIneligibility.EXERCISE_NOT_IN_STUDENT_EXAM;
import static de.tum.cit.aet.artemis.assessment.domain.ExternalSubmissionIneligibility.NOT_IN_COURSE;
import static de.tum.cit.aet.artemis.assessment.domain.ExternalSubmissionIneligibility.NO_STUDENT_EXAM;
import static de.tum.cit.aet.artemis.assessment.domain.ExternalSubmissionIneligibility.RESULT_ALREADY_EXISTS;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.EnumSet;
import java.util.Optional;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import de.tum.cit.aet.artemis.assessment.domain.ExternalSubmissionIneligibility;

/**
 * Pins the order in which the reasons of {@link ExternalSubmissionIneligibility} are reported. The search and the endpoint that creates the result both reduce the facts
 * about a student through {@link ExternalSubmissionEligibilityService#ineligibilityFrom}, so they cannot report different reasons for the same student.
 */
class ExternalSubmissionEligibilityTest {

    // U1
    @Test
    void reportsEachReasonOnItsOwnAndNothingForAnEligibleStudent() {
        assertThat(ExternalSubmissionEligibilityService.ineligibilityFrom(false, true, true, false, true)).contains(NOT_IN_COURSE);
        assertThat(ExternalSubmissionEligibilityService.ineligibilityFrom(true, false, false, false, true)).contains(NO_STUDENT_EXAM);
        assertThat(ExternalSubmissionEligibilityService.ineligibilityFrom(true, true, false, false, true)).contains(EXERCISE_NOT_IN_STUDENT_EXAM);
        assertThat(ExternalSubmissionEligibilityService.ineligibilityFrom(true, true, true, true, true)).contains(RESULT_ALREADY_EXISTS);
        assertThat(ExternalSubmissionEligibilityService.ineligibilityFrom(true, true, true, false, true)).isEmpty();
        assertThat(ExternalSubmissionEligibilityService.ineligibilityFrom(true, false, false, false, false)).isEmpty();
    }

    // U2
    @ParameterizedTest(name = "{displayName} [{index}] inCourse={0}, hasStudentExam={1}, exerciseInStudentExam={2}, hasResult={3}, examExercise={4}")
    @MethodSource("allFlagCombinations")
    void reportsOnlyTheHighestPriorityReason(boolean inCourse, boolean hasStudentExam, boolean exerciseInStudentExam, boolean hasResult, boolean examExercise) {
        // every reason that applies on its own; the exam facts only count for exam exercises
        EnumSet<ExternalSubmissionIneligibility> applicable = EnumSet.noneOf(ExternalSubmissionIneligibility.class);
        if (!inCourse) {
            applicable.add(NOT_IN_COURSE);
        }
        if (examExercise && !hasStudentExam) {
            applicable.add(NO_STUDENT_EXAM);
        }
        if (examExercise && !exerciseInStudentExam) {
            applicable.add(EXERCISE_NOT_IN_STUDENT_EXAM);
        }
        if (hasResult) {
            applicable.add(RESULT_ALREADY_EXISTS);
        }
        // the reported reason is the first applicable one in declaration order, never a later one and never none while one applies
        Optional<ExternalSubmissionIneligibility> expected = applicable.stream().findFirst();

        assertThat(ExternalSubmissionEligibilityService.ineligibilityFrom(inCourse, hasStudentExam, exerciseInStudentExam, hasResult, examExercise)).isEqualTo(expected);
    }

    private static Stream<Arguments> allFlagCombinations() {
        return IntStream.range(0, 32).mapToObj(bits -> Arguments.of((bits & 1) != 0, (bits & 2) != 0, (bits & 4) != 0, (bits & 8) != 0, (bits & 16) != 0));
    }
}
