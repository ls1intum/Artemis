package de.tum.cit.aet.artemis.deimos;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZonedDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Pageable;

import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.deimos.repository.DeimosBatchParticipationRepository;
import de.tum.cit.aet.artemis.exercise.domain.participation.Participation;
import de.tum.cit.aet.artemis.exercise.util.ExerciseUtilService;
import de.tum.cit.aet.artemis.programming.AbstractProgrammingIntegrationIndependentTest;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseStudentParticipation;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingSubmission;

class DeimosBatchParticipationRepositoryIntegrationTest extends AbstractProgrammingIntegrationIndependentTest {

    private static final String TEST_PREFIX = "deimosbatchparticipation";

    private static final ZonedDateTime RANGE_FROM = ZonedDateTime.parse("2026-01-01T00:00:00Z");

    private static final ZonedDateTime RANGE_TO = ZonedDateTime.parse("2026-01-31T23:59:59Z");

    private static final ZonedDateTime SUBMISSION_DATE = ZonedDateTime.parse("2026-01-15T12:00:00Z");

    @Autowired
    private DeimosBatchParticipationRepository deimosBatchParticipationRepository;

    private Course course;

    private ProgrammingExercise exercise;

    private ProgrammingExerciseStudentParticipation studentParticipation;

    @BeforeEach
    void setUp() {
        userUtilService.addUsers(TEST_PREFIX, 1, 0, 0, 0);
        course = programmingExerciseUtilService.addCourseWithOneProgrammingExercise();
        exercise = ExerciseUtilService.getFirstExerciseWithType(course, ProgrammingExercise.class);
        exercise = programmingExerciseRepository.findWithTemplateAndSolutionParticipationById(exercise.getId()).orElseThrow();
        studentParticipation = participationUtilService.addStudentParticipationForProgrammingExercise(exercise, TEST_PREFIX + "student1");

        createSubmissionInRange(studentParticipation, SUBMISSION_DATE);
        createSubmissionInRange(exercise.getTemplateParticipation(), SUBMISSION_DATE);
        createSubmissionInRange(exercise.getSolutionParticipation(), SUBMISSION_DATE);
    }

    @Test
    void findParticipationIdsForExerciseInRange_shouldOnlyIncludeStudentParticipations() {
        var participationIds = deimosBatchParticipationRepository.findParticipationIdsForExerciseInRange(exercise.getId(), RANGE_FROM, RANGE_TO, Pageable.ofSize(20));

        assertThat(participationIds.getContent()).containsExactly(studentParticipation.getId());
        assertThat(deimosBatchParticipationRepository.countDistinctParticipationIdsForExerciseInRange(exercise.getId(), RANGE_FROM, RANGE_TO)).isOne();
    }

    @Test
    void findParticipationIdsForCourseInRange_shouldOnlyIncludeStudentParticipations() {
        var participationIds = deimosBatchParticipationRepository.findParticipationIdsForCourseInRange(course.getId(), RANGE_FROM, RANGE_TO, Pageable.ofSize(20));

        assertThat(participationIds.getContent()).containsExactly(studentParticipation.getId());
        assertThat(deimosBatchParticipationRepository.countDistinctParticipationIdsForCourseInRange(course.getId(), RANGE_FROM, RANGE_TO)).isOne();
    }

    private void createSubmissionInRange(Participation participation, ZonedDateTime submissionDate) {
        ProgrammingSubmission submission = new ProgrammingSubmission();
        submission.setSubmitted(true);
        submission.setSubmissionDate(submissionDate);
        submission.setCommitHash("deadbeef");
        submission.setParticipation(participation);
        programmingSubmissionRepository.saveAndFlush(submission);
    }
}
