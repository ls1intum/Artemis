package de.tum.cit.aet.artemis.assessment;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZonedDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.cit.aet.artemis.assessment.domain.AssessmentType;
import de.tum.cit.aet.artemis.assessment.domain.Feedback;
import de.tum.cit.aet.artemis.assessment.domain.Result;
import de.tum.cit.aet.artemis.assessment.domain.Visibility;
import de.tum.cit.aet.artemis.core.config.Constants;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.exercise.participation.util.ParticipationUtilService;
import de.tum.cit.aet.artemis.exercise.test_repository.StudentParticipationTestRepository;
import de.tum.cit.aet.artemis.exercise.util.ExerciseUtilService;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseStudentParticipation;
import de.tum.cit.aet.artemis.programming.service.ProgrammingFeedbackSynthesizerService;
import de.tum.cit.aet.artemis.programming.test_repository.ProgrammingExerciseTestCaseTestRepository;
import de.tum.cit.aet.artemis.programming.test_repository.ProgrammingExerciseTestRepository;
import de.tum.cit.aet.artemis.programming.util.ProgrammingExerciseUtilService;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationIndependentBatchTest;

class LongFeedbackResourceIntegrationTest extends AbstractSpringIntegrationIndependentBatchTest {

    private static final String TEST_PREFIX = "longfeedbackintegration";

    private static final String LONG_FEEDBACK = "a".repeat(Constants.FEEDBACK_DETAIL_TEXT_DATABASE_MAX_LENGTH + 10);

    @Autowired
    private ProgrammingExerciseUtilService programmingExerciseUtilService;

    @Autowired
    private ParticipationUtilService participationUtilService;

    @Autowired
    private ProgrammingExerciseTestCaseTestRepository testCaseRepository;

    @Autowired
    private ProgrammingExerciseTestRepository exerciseRepository;

    @Autowired
    private StudentParticipationTestRepository studentParticipationRepository;

    private Result resultStudent1;

    private ProgrammingExercise exercise;

    @BeforeEach
    void setUp() {
        userUtilService.addUsers(TEST_PREFIX, 2, 1, 0, 0);

        final Course course = programmingExerciseUtilService.addEnrolledCourseWithOneProgrammingExercise(TEST_PREFIX);
        exercise = ExerciseUtilService.getFirstExerciseWithType(course, ProgrammingExercise.class);

        resultStudent1 = participationUtilService.addProgrammingParticipationWithResultForExercise(exercise, TEST_PREFIX + "student1");
        ProgrammingExerciseStudentParticipation programmingExerciseStudentParticipation = programmingExerciseParticipationService
                .findStudentParticipationByExerciseAndStudentId(exercise, TEST_PREFIX + "student1");
        programmingExerciseUtilService.addProgrammingSubmissionToResultAndParticipation(resultStudent1, programmingExerciseStudentParticipation, "test");
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1")
    void getLongFeedbackAsStudent() throws Exception {
        final Feedback feedback = addLongFeedbackToResult(resultStudent1);

        final String longFeedbackText = request.get(getUrl(feedback.getId()), HttpStatus.OK, String.class);
        assertThat(longFeedbackText).isEqualTo(LONG_FEEDBACK);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1")
    void getLongFeedbackAsTutor() throws Exception {
        final Feedback feedback = addLongFeedbackToResult(resultStudent1);

        final String longFeedbackText = request.get(getUrl(feedback.getId()), HttpStatus.OK, String.class);
        assertThat(longFeedbackText).isEqualTo(LONG_FEEDBACK);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1")
    void notFoundIfNotExists() throws Exception {
        final Feedback feedback = addLongFeedbackToResult(resultStudent1);

        final String longFeedbackText = request.get(getUrl(feedback.getId() + 1), HttpStatus.NOT_FOUND, String.class);
        assertThat(longFeedbackText).isNull();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1")
    void notFoundIfOnlyShortFeedback() throws Exception {
        final Feedback feedback = new Feedback();
        feedback.setDetailText("short text");
        participationUtilService.addFeedbackToResult(feedback, resultStudent1);

        final String longFeedbackText = request.get(getUrl(feedback.getId()), HttpStatus.NOT_FOUND, String.class);
        assertThat(longFeedbackText).isNull();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student2")
    void accessForbiddenIfNotOwnParticipation() throws Exception {
        final Feedback feedback = addLongFeedbackToResult(resultStudent1);

        final String longFeedbackText = request.get(getUrl(feedback.getId()), HttpStatus.FORBIDDEN, String.class);
        assertThat(longFeedbackText).isNull();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1")
    void getTestCaseMessageAsStudent() throws Exception {
        long syntheticId = addTestCaseMessageToResult(resultStudent1, Visibility.ALWAYS);

        final String longFeedbackText = request.get(getUrl(syntheticId), HttpStatus.OK, String.class);
        assertThat(longFeedbackText).isEqualTo(LONG_FEEDBACK);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student2")
    void testCaseMessageForbiddenIfNotOwnParticipation() throws Exception {
        long syntheticId = addTestCaseMessageToResult(resultStudent1, Visibility.ALWAYS);

        request.get(getUrl(syntheticId), HttpStatus.FORBIDDEN, String.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1")
    void testCaseMessageOfInvisibleTestHiddenFromStudent() throws Exception {
        // the synthetic ids encode typed row ids and are enumerable, so the endpoint must not leak
        // messages of hidden test cases even for the student's own submission
        long syntheticId = addTestCaseMessageToResult(resultStudent1, Visibility.NEVER);

        request.get(getUrl(syntheticId), HttpStatus.NOT_FOUND, String.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1")
    void testCaseMessageOfAfterDueDateTestHiddenFromStudentBeforeDueDate() throws Exception {
        exercise.setDueDate(ZonedDateTime.now().plusHours(2));
        exerciseRepository.save(exercise);
        long syntheticId = addTestCaseMessageToResult(resultStudent1, Visibility.AFTER_DUE_DATE);

        request.get(getUrl(syntheticId), HttpStatus.NOT_FOUND, String.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1")
    void testCaseMessageOfAfterDueDateTestVisibleToStudentAfterDueDate() throws Exception {
        exercise.setDueDate(ZonedDateTime.now().minusHours(2));
        exerciseRepository.save(exercise);
        long syntheticId = addTestCaseMessageToResult(resultStudent1, Visibility.AFTER_DUE_DATE);

        final String longFeedbackText = request.get(getUrl(syntheticId), HttpStatus.OK, String.class);
        assertThat(longFeedbackText).isEqualTo(LONG_FEEDBACK);
    }

    /**
     * The synthetic ids are enumerable, so the guard has to hide exactly what the serialization filters hide.
     * For automatic results those keep 'after due date' feedback hidden until the LAST individual due date has
     * passed — a student whose own due date is over must not be able to read it through this endpoint while a
     * classmate with an extension can still submit.
     */
    @Test
    @WithMockUser(username = TEST_PREFIX + "student1")
    void testCaseMessageOfAfterDueDateTestHiddenWhileAnotherStudentStillHasTimeToSubmit() throws Exception {
        exercise.setDueDate(ZonedDateTime.now().minusHours(2));
        exerciseRepository.save(exercise);
        // a classmate got an extension, so the exercise's latest individual due date is still in the future
        var participationWithExtension = participationUtilService.createAndSaveParticipationForExercise(exercise, TEST_PREFIX + "student2");
        participationWithExtension.setIndividualDueDate(ZonedDateTime.now().plusHours(2));
        studentParticipationRepository.save(participationWithExtension);

        resultStudent1.setAssessmentType(AssessmentType.AUTOMATIC);
        resultRepository.save(resultStudent1);
        long syntheticId = addTestCaseMessageToResult(resultStudent1, Visibility.AFTER_DUE_DATE);

        request.get(getUrl(syntheticId), HttpStatus.NOT_FOUND, String.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1")
    void testCaseMessageOfInvisibleTestVisibleToTutor() throws Exception {
        long syntheticId = addTestCaseMessageToResult(resultStudent1, Visibility.NEVER);

        final String longFeedbackText = request.get(getUrl(syntheticId), HttpStatus.OK, String.class);
        assertThat(longFeedbackText).isEqualTo(LONG_FEEDBACK);
    }

    private long addTestCaseMessageToResult(final Result result, Visibility visibility) {
        var testCase = programmingExerciseUtilService.addTestCaseToProgrammingExercise(exercise, "hiddenTest" + visibility);
        testCase.setVisibility(visibility);
        testCase = testCaseRepository.save(testCase);
        var row = participationUtilService.addTestCaseFeedbackToResult(result, testCase, false, LONG_FEEDBACK);
        return ProgrammingFeedbackSynthesizerService.syntheticTestCaseId(row.getId());
    }

    private String getUrl(final long feedbackId) {
        return "/api/assessment/feedbacks/%d/long-feedback".formatted(feedbackId);
    }

    private Feedback addLongFeedbackToResult(final Result result) {
        final Feedback feedback = new Feedback();
        feedback.setDetailText(LONG_FEEDBACK);

        participationUtilService.addFeedbackToResult(feedback, result);

        return feedback;
    }
}
