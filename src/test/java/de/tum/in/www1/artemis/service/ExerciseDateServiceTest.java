package de.tum.in.www1.artemis.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZonedDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;

import de.tum.in.www1.artemis.AbstractSpringIntegrationIndependentTest;
import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.enumeration.DiagramType;
import de.tum.in.www1.artemis.domain.exam.Exam;
import de.tum.in.www1.artemis.domain.exam.StudentExam;
import de.tum.in.www1.artemis.domain.modeling.ModelingExercise;
import de.tum.in.www1.artemis.domain.participation.Participation;
import de.tum.in.www1.artemis.exam.ExamUtilService;
import de.tum.in.www1.artemis.exercise.ExerciseUtilService;
import de.tum.in.www1.artemis.exercise.modelingexercise.ModelingExerciseFactory;
import de.tum.in.www1.artemis.exercise.modelingexercise.ModelingExerciseUtilService;
import de.tum.in.www1.artemis.participation.ParticipationFactory;
import de.tum.in.www1.artemis.participation.ParticipationUtilService;
import de.tum.in.www1.artemis.repository.ExamRepository;
import de.tum.in.www1.artemis.repository.ModelingExerciseRepository;
import de.tum.in.www1.artemis.repository.ParticipationRepository;
import de.tum.in.www1.artemis.repository.StudentExamRepository;
import de.tum.in.www1.artemis.security.SecurityUtils;
import de.tum.in.www1.artemis.user.UserUtilService;

class ExerciseDateServiceTest extends AbstractSpringIntegrationIndependentTest {

    private static final String TEST_PREFIX = "exercisedateservice";

    @Autowired
    private ExerciseDateService exerciseDateService;

    @Autowired
    private ModelingExerciseRepository exerciseRepository;

    @Autowired
    private ParticipationRepository participationRepository;

    @Autowired
    private ExamRepository examRepository;

    @Autowired
    private StudentExamRepository studentExamRepository;

    @Autowired
    private UserUtilService userUtilService;

    @Autowired
    private ModelingExerciseUtilService modelingExerciseUtilService;

    @Autowired
    private ExerciseUtilService exerciseUtilService;

    @Autowired
    private ExamUtilService examUtilService;

    @Autowired
    private ParticipationUtilService participationUtilService;

    private Course course;

    private ModelingExercise exercise;

    @BeforeEach
    void init() {
        SecurityUtils.setAuthorizationObject();

        userUtilService.addUsers(TEST_PREFIX, 3, 2, 0, 2);
        course = modelingExerciseUtilService.addCourseWithOneModelingExercise();
        exercise = exerciseUtilService.getFirstExerciseWithType(course, ModelingExercise.class);

        for (int i = 1; i <= 3; ++i) {
            var submission = ParticipationFactory.generateModelingSubmission(String.format("model%d", i), true);
            modelingExerciseUtilService.addModelingSubmission(exercise, submission, TEST_PREFIX + "student1");
        }

        exercise = exerciseRepository.findByIdWithStudentParticipationsSubmissionsResultsElseThrow(exercise.getId());
    }

    @Test
    void latestDueDateShouldNotExistIfNoExerciseDueDate() {
        exercise.setDueDate(null);
        exercise = exerciseRepository.save(exercise);

        // in a real scenario individual due dates should never exist if the exercise has no due date
        final var participation = exercise.getStudentParticipations().stream().findAny().orElseThrow();
        participation.setIndividualDueDate(ZonedDateTime.now().plusHours(2));
        participationRepository.save(participation);

        assertThat(exerciseDateService.getLatestIndividualDueDate(exercise)).isEmpty();
        assertThat(exerciseDateService.isBeforeLatestDueDate(exercise)).isTrue();
    }

    @Test
    void latestDueDateShouldBeExerciseDueDateIfNoIndividualDueDate() {
        final var dueDate = ZonedDateTime.now().plusHours(4);
        exercise.setDueDate(dueDate);
        exercise = exerciseRepository.save(exercise);

        assertThat(exerciseDateService.getLatestIndividualDueDate(exercise).orElseThrow()).isEqualToIgnoringNanos(dueDate);
        assertThat(exerciseDateService.isBeforeLatestDueDate(exercise)).isTrue();
        assertThat(exerciseDateService.isAfterLatestDueDate(exercise)).isFalse();
    }

    @Test
    void latestDueDateShouldBeLatestIndividualDueDate() {
        final var now = ZonedDateTime.now();
        exercise.setDueDate(now.plusHours(4));
        exercise = exerciseRepository.save(exercise);

        var participation = exercise.getStudentParticipations().stream().findAny().orElseThrow();
        participation.setIndividualDueDate(now.plusHours(20));
        participationRepository.save(participation);

        assertThat(exerciseDateService.getLatestIndividualDueDate(exercise).orElseThrow()).isEqualToIgnoringNanos(now.plusHours(20));
    }

    @Test
    void participationDueDateShouldBeExerciseDueDateIfNoIndividualDueDate() {
        final var now = ZonedDateTime.now();
        exercise.setDueDate(now.plusHours(4));
        exercise = exerciseRepository.save(exercise);

        final var participation = exercise.getStudentParticipations().stream().findAny().orElseThrow();
        assertThat(ExerciseDateService.getDueDate(participation).orElseThrow()).isEqualToIgnoringNanos(now.plusHours(4));
    }

    @Test
    void participationDueDateShouldBeIndividualDueDate() {
        final var now = ZonedDateTime.now();
        exercise.setDueDate(now.plusHours(4));
        exercise = exerciseRepository.save(exercise);

        var participation = exercise.getStudentParticipations().stream().findAny().orElseThrow();
        participation.setIndividualDueDate(now.plusHours(20));
        participation = participationRepository.save(participation);

        assertThat(ExerciseDateService.getDueDate(participation).orElseThrow()).isEqualToIgnoringNanos(now.plusHours(20));
    }

    @Test
    void nowShouldBeBeforeADueDateInTheFuture() {
        final var now = ZonedDateTime.now();
        var participation = exercise.getStudentParticipations().stream().findAny().orElseThrow();
        participation.setIndividualDueDate(now.plusHours(20));
        participation = participationRepository.save(participation);

        assertThat(exerciseDateService.isBeforeDueDate(participation)).isTrue();
        assertThat(exerciseDateService.isAfterDueDate(participation)).isFalse();
    }

    @Test
    void itShouldAlwaysBeBeforeANonExistingDueDate() {
        exercise.setDueDate(null);
        exercise = exerciseRepository.save(exercise);
        var participation = exercise.getStudentParticipations().stream().findAny().orElseThrow();

        assertThat(participation.getIndividualDueDate()).isNull();
        assertThat(exerciseDateService.isBeforeDueDate(participation)).isTrue();
        assertThat(exerciseDateService.isAfterDueDate(participation)).isFalse();
    }

    @Test
    void testAssessmentDueDate_notSet() {
        exercise.setAssessmentDueDate(null);
        exercise = exerciseRepository.save(exercise);

        assertThat(ExerciseDateService.isAfterAssessmentDueDate(exercise)).isTrue();
    }

    @Test
    void testAssessmentDueDate_inFuture() {
        exercise.setAssessmentDueDate(ZonedDateTime.now().plusHours(1));
        exercise = exerciseRepository.save(exercise);

        assertThat(ExerciseDateService.isAfterAssessmentDueDate(exercise)).isFalse();
    }

    @Test
    void testAssessmentDueDate_inPast() {
        exercise.setAssessmentDueDate(ZonedDateTime.now().minusHours(1));
        exercise = exerciseRepository.save(exercise);

        assertThat(ExerciseDateService.isAfterAssessmentDueDate(exercise)).isTrue();
    }

    @Nested
    class ExamTest {

        private Exam exam;

        private ModelingExercise exercise;

        @BeforeEach
        void init() {
            exam = examUtilService.addExamWithExerciseGroup(course, true);
            exercise = ModelingExerciseFactory.generateModelingExerciseForExam(DiagramType.ClassDiagram, exam.getExerciseGroups().get(0));
            exercise = exerciseRepository.save(exercise);
        }

        @Test
        void testExamExerciseDueDate_duringRegularWorkingTime() {
            var student = TEST_PREFIX + "student1";
            examUtilService.addStudentExamWithUser(exam, student);
            Participation participation = participationUtilService.createAndSaveParticipationForExercise(exercise, TEST_PREFIX + "student1");
            boolean result = exerciseDateService.isAfterDueDate(participation);
            assertThat(result).isFalse(); // Exam is not started yet
        }

        @Test
        void testExamExerciseDueDate_workingTimeEnded() {
            updateExamDatesToPast();
            var student = TEST_PREFIX + "student1";
            examUtilService.addStudentExamWithUser(exam, student);
            Participation participation = participationUtilService.createAndSaveParticipationForExercise(exercise, student);

            boolean result = exerciseDateService.isAfterDueDate(participation);
            assertThat(result).isTrue(); // Exam working period is over
        }

        @ParameterizedTest
        @ValueSource(strings = { TEST_PREFIX + "student1", TEST_PREFIX + "student2" })
        void testExamExerciseDueDate_individualTimeExtension(String ownerOfStudentExam) {
            updateExamDatesToPast();

            StudentExam studentExam = examUtilService.addStudentExamWithUser(exam, ownerOfStudentExam);
            studentExam.setWorkingTime(exam.getWorkingTime() + 20 * 60);

            studentExamRepository.save(studentExam);
            Participation participation = participationUtilService.createAndSaveParticipationForExercise(exercise, ownerOfStudentExam);

            boolean result = exerciseDateService.isAfterDueDate(participation);
            assertThat(result).isFalse(); // Exam working period is over but the time extension is still active
        }

        private void updateExamDatesToPast() {
            exam.setStartDate(ZonedDateTime.now().minusMinutes(20));
            exam.setEndDate(ZonedDateTime.now().minusMinutes(10));
            exam = examRepository.save(exam);
        }

        @Test
        void testExamAssessmentDueDate_reviewPeriodUnset() {
            exam.setExamStudentReviewStart(null);
            exam.setExamStudentReviewEnd(null);
            exam = examRepository.save(exam);

            assertThat(ExerciseDateService.isAfterAssessmentDueDate(exercise)).isFalse();
        }

        @Test
        void testExamAssessmentDueDate_resultsNotPublished() {
            exam.setPublishResultsDate(ZonedDateTime.now().plusHours(1));
            exam = examRepository.save(exam);

            assertThat(ExerciseDateService.isAfterAssessmentDueDate(exercise)).isFalse();
        }

        @Test
        void testExamAssessmentDueDate_resultsPublished() {
            exam.setPublishResultsDate(ZonedDateTime.now().minusHours(1));
            exam = examRepository.save(exam);

            assertThat(ExerciseDateService.isAfterAssessmentDueDate(exercise)).isTrue();
        }

        @Test
        void testExamAssessmentDueDate_afterReviewPeriod() {
            exam.setPublishResultsDate(ZonedDateTime.now().minusHours(2));
            exam.setExamStudentReviewStart(ZonedDateTime.now().minusHours(2));
            exam.setExamStudentReviewEnd(ZonedDateTime.now().minusHours(1));
            exam = examRepository.save(exam);

            assertThat(ExerciseDateService.isAfterAssessmentDueDate(exercise)).isTrue();
        }
    }

    @Nested
    class TestExamTest {

        private Exam testExam;

        private ModelingExercise exercise;

        @BeforeEach
        void init() {
            testExam = examUtilService.addTestExamWithExerciseGroup(course, true);
            exercise = ModelingExerciseFactory.generateModelingExerciseForExam(DiagramType.ClassDiagram, testExam.getExerciseGroups().get(0));
            exercise = exerciseRepository.save(exercise);
        }

        @Test
        void testTestExamExerciseDueDate_duringOwnWorkingTime() {
            Participation participation = participationUtilService.createAndSaveParticipationForExercise(exercise, TEST_PREFIX + "student1");
            examUtilService.addStudentExamWithUser(testExam, TEST_PREFIX + "student1");

            boolean result = exerciseDateService.isAfterDueDate(participation);
            assertThat(result).isFalse();
        }

        @Test
        void testTestExamExerciseDueDate_afterSubmittingOwnExam() {
            StudentExam studentExam = examUtilService.addStudentExamWithUser(testExam, TEST_PREFIX + "student1");
            studentExam.setSubmitted(true);
            studentExamRepository.save(studentExam);
            Participation participation = participationUtilService.createAndSaveParticipationForExercise(exercise, TEST_PREFIX + "student1");

            boolean result = exerciseDateService.isAfterDueDate(participation);
            assertThat(result).isTrue();

            // Should also be true if other students are stil working - only the own test exam matters
            examUtilService.addStudentExamWithUser(testExam, TEST_PREFIX + "student2");
            result = exerciseDateService.isAfterDueDate(participation);
            assertThat(result).isTrue();
        }

    }

}
