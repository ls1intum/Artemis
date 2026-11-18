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
import de.tum.cit.aet.artemis.assessment.domain.FeedbackType;
import de.tum.cit.aet.artemis.assessment.domain.Result;
import de.tum.cit.aet.artemis.assessment.domain.Visibility;
import de.tum.cit.aet.artemis.core.config.Constants;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.exam.domain.Exam;
import de.tum.cit.aet.artemis.exam.domain.StudentExam;
import de.tum.cit.aet.artemis.exam.test_repository.ExamTestRepository;
import de.tum.cit.aet.artemis.exam.util.ExamUtilService;
import de.tum.cit.aet.artemis.exercise.domain.Submission;
import de.tum.cit.aet.artemis.exercise.domain.participation.StudentParticipation;
import de.tum.cit.aet.artemis.exercise.participation.util.ParticipationUtilService;
import de.tum.cit.aet.artemis.exercise.test_repository.StudentParticipationTestRepository;
import de.tum.cit.aet.artemis.exercise.util.ExerciseUtilService;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseStudentParticipation;
import de.tum.cit.aet.artemis.programming.service.ProgrammingFeedbackSynthesizerService;
import de.tum.cit.aet.artemis.programming.test_repository.ProgrammingExerciseTestCaseTestRepository;
import de.tum.cit.aet.artemis.programming.test_repository.ProgrammingExerciseTestRepository;
import de.tum.cit.aet.artemis.programming.util.ProgrammingExerciseParticipationUtilService;
import de.tum.cit.aet.artemis.programming.util.ProgrammingExerciseUtilService;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationIndependentBatchTest;
import de.tum.cit.aet.artemis.text.domain.TextExercise;
import de.tum.cit.aet.artemis.text.domain.TextSubmission;

class LongFeedbackResourceIntegrationTest extends AbstractSpringIntegrationIndependentBatchTest {

    private static final String TEST_PREFIX = "longfeedbackintegration";

    private static final String LONG_FEEDBACK = "a".repeat(Constants.FEEDBACK_DETAIL_TEXT_DATABASE_MAX_LENGTH + 10);

    @Autowired
    private ProgrammingExerciseUtilService programmingExerciseUtilService;

    @Autowired
    private ProgrammingExerciseParticipationUtilService programmingExerciseParticipationUtilService;

    @Autowired
    private ParticipationUtilService participationUtilService;

    @Autowired
    private ProgrammingExerciseTestCaseTestRepository testCaseRepository;

    @Autowired
    private ProgrammingExerciseTestRepository exerciseRepository;

    @Autowired
    private StudentParticipationTestRepository studentParticipationRepository;

    @Autowired
    private ExamUtilService examUtilService;

    @Autowired
    private ExamTestRepository examRepository;

    private Result resultStudent1;

    private ProgrammingExercise exercise;

    @BeforeEach
    void setUp() {
        userUtilService.addUsers(TEST_PREFIX, 2, 1, 0, 0);

        final Course course = programmingExerciseUtilService.addEnrolledCourseWithOneProgrammingExercise(TEST_PREFIX);
        exercise = ExerciseUtilService.getFirstExerciseWithType(course, ProgrammingExercise.class);

        resultStudent1 = participationUtilService.addProgrammingParticipationWithResultForExercise(exercise, TEST_PREFIX + "student1");
        ProgrammingExerciseStudentParticipation programmingExerciseStudentParticipation = programmingExerciseParticipationUtilService
                .findStudentParticipationByExerciseAndStudentLogin(exercise, TEST_PREFIX + "student1");
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
    void longFeedbackOfManualAssessmentNotServedBeforeAssessmentDueDate() throws Exception {
        final Feedback feedback = addManualLongFeedbackToManuallyAssessedResult(ZonedDateTime.now().plusHours(2));

        request.get(getUrl(feedback.getId()), HttpStatus.NOT_FOUND, String.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1")
    void longFeedbackOfManualAssessmentServedAfterAssessmentDueDate() throws Exception {
        final Feedback feedback = addManualLongFeedbackToManuallyAssessedResult(ZonedDateTime.now().minusHours(2));

        final String longFeedbackText = request.get(getUrl(feedback.getId()), HttpStatus.OK, String.class);
        assertThat(longFeedbackText).isEqualTo(LONG_FEEDBACK);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1")
    void longFeedbackOfManualAssessmentServedToTutorBeforeAssessmentDueDate() throws Exception {
        final Feedback feedback = addManualLongFeedbackToManuallyAssessedResult(ZonedDateTime.now().plusHours(2));

        final String longFeedbackText = request.get(getUrl(feedback.getId()), HttpStatus.OK, String.class);
        assertThat(longFeedbackText).isEqualTo(LONG_FEEDBACK);
    }

    /**
     * Feedback that was accepted from an automatic suggestion belongs to the manual assessment: it is released with the assessment, not at once.
     */
    @Test
    @WithMockUser(username = TEST_PREFIX + "student1")
    void longFeedbackOfSuggestionAcceptedIntoManualAssessmentNotServedBeforeAssessmentDueDate() throws Exception {
        final Feedback feedback = addLongFeedbackToAssessedResult(ZonedDateTime.now().plusHours(2), AssessmentType.SEMI_AUTOMATIC, FeedbackType.AUTOMATIC, ZonedDateTime.now());

        request.get(getUrl(feedback.getId()), HttpStatus.NOT_FOUND, String.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1")
    void longFeedbackOfAutomaticAssistantResultServedBeforeAssessmentDueDate() throws Exception {
        final Feedback feedback = addLongFeedbackToAssessedResult(ZonedDateTime.now().plusHours(2), AssessmentType.AUTOMATIC_ATHENA, FeedbackType.AUTOMATIC, null);

        final String longFeedbackText = request.get(getUrl(feedback.getId()), HttpStatus.OK, String.class);
        assertThat(longFeedbackText).isEqualTo(LONG_FEEDBACK);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1")
    void longFeedbackOfAssessmentThatWasNotSubmittedNotServedToStudent() throws Exception {
        final Feedback feedback = addLongFeedbackToAssessedResult(ZonedDateTime.now().minusHours(2), AssessmentType.SEMI_AUTOMATIC, FeedbackType.MANUAL, null);

        request.get(getUrl(feedback.getId()), HttpStatus.NOT_FOUND, String.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1")
    void longFeedbackOfAssessmentThatWasNotSubmittedServedToTutor() throws Exception {
        final Feedback feedback = addLongFeedbackToAssessedResult(ZonedDateTime.now().minusHours(2), AssessmentType.SEMI_AUTOMATIC, FeedbackType.MANUAL, null);

        final String longFeedbackText = request.get(getUrl(feedback.getId()), HttpStatus.OK, String.class);
        assertThat(longFeedbackText).isEqualTo(LONG_FEEDBACK);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1")
    void examLongFeedbackOfAssessmentNotServedBeforeTheResultsArePublished() throws Exception {
        final Feedback feedback = addLongFeedbackToExamResult(AssessmentType.MANUAL, ZonedDateTime.now().plusDays(1));

        request.get(getUrl(feedback.getId()), HttpStatus.NOT_FOUND, String.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1")
    void examLongFeedbackOfAutomaticResultNotServedBeforeTheResultsArePublished() throws Exception {
        final Feedback feedback = addLongFeedbackToExamResult(AssessmentType.AUTOMATIC, ZonedDateTime.now().plusDays(1));

        request.get(getUrl(feedback.getId()), HttpStatus.NOT_FOUND, String.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1")
    void examLongFeedbackOfAssessmentServedOnceTheResultsArePublished() throws Exception {
        final Feedback feedback = addLongFeedbackToExamResult(AssessmentType.MANUAL, ZonedDateTime.now().minusMinutes(1));

        final String longFeedbackText = request.get(getUrl(feedback.getId()), HttpStatus.OK, String.class);
        assertThat(longFeedbackText).isEqualTo(LONG_FEEDBACK);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1")
    void examLongFeedbackOfAssessmentServedToTutorBeforeTheResultsArePublished() throws Exception {
        final Feedback feedback = addLongFeedbackToExamResult(AssessmentType.MANUAL, ZonedDateTime.now().plusDays(1));

        final String longFeedbackText = request.get(getUrl(feedback.getId()), HttpStatus.OK, String.class);
        assertThat(longFeedbackText).isEqualTo(LONG_FEEDBACK);
    }

    /**
     * The result of a programming exercise in an exam is hidden until the results are published, also when the build finished after the working time, and so are the messages of
     * its test cases.
     */
    @Test
    @WithMockUser(username = TEST_PREFIX + "student1")
    void examTestCaseMessageNotServedBeforeTheResultsArePublished() throws Exception {
        long syntheticId = addTestCaseMessageToExamResult(ZonedDateTime.now().plusDays(1));

        request.get(getUrl(syntheticId), HttpStatus.NOT_FOUND, String.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1")
    void examTestCaseMessageServedOnceTheResultsArePublished() throws Exception {
        long syntheticId = addTestCaseMessageToExamResult(ZonedDateTime.now().minusMinutes(1));

        final String longFeedbackText = request.get(getUrl(syntheticId), HttpStatus.OK, String.class);
        assertThat(longFeedbackText).isEqualTo(LONG_FEEDBACK);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1")
    void examTestCaseMessageServedToTutorBeforeTheResultsArePublished() throws Exception {
        long syntheticId = addTestCaseMessageToExamResult(ZonedDateTime.now().plusDays(1));

        final String longFeedbackText = request.get(getUrl(syntheticId), HttpStatus.OK, String.class);
        assertThat(longFeedbackText).isEqualTo(LONG_FEEDBACK);
    }

    /**
     * Feedback of a result is served under the visibility it has in the full result: hidden feedback is never served to the student, and feedback that is visible after the due
     * date only once the due date has passed.
     */
    @Test
    @WithMockUser(username = TEST_PREFIX + "student1")
    void longFeedbackWithVisibilityNeverNotServedToStudent() throws Exception {
        final Feedback feedback = addLongFeedbackWithVisibility(Visibility.NEVER);

        request.get(getUrl(feedback.getId()), HttpStatus.NOT_FOUND, String.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1")
    void longFeedbackWithVisibilityNeverServedToTutor() throws Exception {
        final Feedback feedback = addLongFeedbackWithVisibility(Visibility.NEVER);

        final String longFeedbackText = request.get(getUrl(feedback.getId()), HttpStatus.OK, String.class);
        assertThat(longFeedbackText).isEqualTo(LONG_FEEDBACK);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1")
    void longFeedbackAfterDueDateNotServedToStudentBeforeTheDueDate() throws Exception {
        exercise.setDueDate(ZonedDateTime.now().plusHours(2));
        exerciseRepository.save(exercise);
        final Feedback feedback = addLongFeedbackWithVisibility(Visibility.AFTER_DUE_DATE);

        request.get(getUrl(feedback.getId()), HttpStatus.NOT_FOUND, String.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1")
    void longFeedbackAfterDueDateServedToStudentAfterTheDueDate() throws Exception {
        exercise.setDueDate(ZonedDateTime.now().minusHours(2));
        exerciseRepository.save(exercise);
        final Feedback feedback = addLongFeedbackWithVisibility(Visibility.AFTER_DUE_DATE);

        final String longFeedbackText = request.get(getUrl(feedback.getId()), HttpStatus.OK, String.class);
        assertThat(longFeedbackText).isEqualTo(LONG_FEEDBACK);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1")
    void longFeedbackAfterDueDateServedToTutorBeforeTheDueDate() throws Exception {
        exercise.setDueDate(ZonedDateTime.now().plusHours(2));
        exerciseRepository.save(exercise);
        final Feedback feedback = addLongFeedbackWithVisibility(Visibility.AFTER_DUE_DATE);

        final String longFeedbackText = request.get(getUrl(feedback.getId()), HttpStatus.OK, String.class);
        assertThat(longFeedbackText).isEqualTo(LONG_FEEDBACK);
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
        return addTestCaseMessageToResult(exercise, result, visibility);
    }

    private long addTestCaseMessageToResult(final ProgrammingExercise programmingExercise, final Result result, Visibility visibility) {
        var testCase = programmingExerciseUtilService.addTestCaseToProgrammingExercise(programmingExercise, "hiddenTest" + visibility);
        testCase.setVisibility(visibility);
        testCase = testCaseRepository.save(testCase);
        var row = participationUtilService.addTestCaseFeedbackToResult(result, testCase, false, LONG_FEEDBACK);
        return ProgrammingFeedbackSynthesizerService.syntheticTestCaseId(row.getId());
    }

    private String getUrl(final long feedbackId) {
        return "/api/assessment/feedbacks/%d/long-feedback".formatted(feedbackId);
    }

    private Feedback addManualLongFeedbackToManuallyAssessedResult(final ZonedDateTime assessmentDueDate) {
        return addLongFeedbackToAssessedResult(assessmentDueDate, AssessmentType.SEMI_AUTOMATIC, FeedbackType.MANUAL, ZonedDateTime.now().minusMinutes(5));
    }

    private Feedback addLongFeedbackToAssessedResult(final ZonedDateTime assessmentDueDate, final AssessmentType assessmentType, final FeedbackType feedbackType,
            final ZonedDateTime completionDate) {
        exercise.setAssessmentDueDate(assessmentDueDate);
        exerciseRepository.save(exercise);
        resultStudent1.setAssessmentType(assessmentType);
        resultStudent1.setCompletionDate(completionDate);
        resultRepository.save(resultStudent1);

        final Feedback feedback = new Feedback();
        feedback.setType(feedbackType);
        feedback.setDetailText(LONG_FEEDBACK);
        participationUtilService.addFeedbackToResult(feedback, resultStudent1);
        return feedback;
    }

    /**
     * Creates a result of the first student in an exam exercise, with a long feedback, in an exam whose results are published at the given date.
     */
    private Feedback addLongFeedbackToExamResult(final AssessmentType assessmentType, final ZonedDateTime publishResultsDate) {
        TextExercise examTextExercise = examUtilService.addEnrolledCourseExamExerciseGroupWithOneTextExercise(TEST_PREFIX);
        Exam exam = examTextExercise.getExam();
        exam.setPublishResultsDate(publishResultsDate);
        examRepository.save(exam);
        StudentExam studentExam = examUtilService.addStudentExamWithUser(exam, TEST_PREFIX + "student1");
        examUtilService.addExerciseToStudentExam(studentExam, examTextExercise);

        StudentParticipation participation = participationUtilService.createAndSaveParticipationForExercise(examTextExercise, TEST_PREFIX + "student1");
        Submission submission = participationUtilService.addSubmission(participation, new TextSubmission());
        Result result = participationUtilService.addResultToSubmission(assessmentType, ZonedDateTime.now(), submission);

        final Feedback feedback = new Feedback();
        feedback.setType(assessmentType == AssessmentType.AUTOMATIC ? FeedbackType.AUTOMATIC : FeedbackType.MANUAL);
        feedback.setDetailText(LONG_FEEDBACK);
        participationUtilService.addFeedbackToResult(feedback, result);
        return feedback;
    }

    private Feedback addLongFeedbackWithVisibility(final Visibility visibility) {
        final Feedback feedback = new Feedback();
        feedback.setDetailText(LONG_FEEDBACK);
        feedback.setVisibility(visibility);
        participationUtilService.addFeedbackToResult(feedback, resultStudent1);
        return feedback;
    }

    /**
     * Creates a result of the first student in a programming exercise of an exam that has ended, with the message of a test case, in an exam whose results are published at
     * the given date.
     */
    private long addTestCaseMessageToExamResult(final ZonedDateTime publishResultsDate) {
        ProgrammingExercise examExercise = programmingExerciseUtilService.addEnrolledCourseExamExerciseGroupWithOneProgrammingExercise(TEST_PREFIX);
        Exam exam = examExercise.getExam();
        ZonedDateTime now = ZonedDateTime.now();
        examUtilService.setVisibleStartAndEndDateOfExam(exam, now.minusHours(4), now.minusHours(3), now.minusHours(2));
        exam.setPublishResultsDate(publishResultsDate);
        examRepository.save(exam);
        StudentExam studentExam = examUtilService.addStudentExamWithUser(exam, TEST_PREFIX + "student1");
        examUtilService.addExerciseToStudentExam(studentExam, examExercise);

        Result result = participationUtilService.addProgrammingParticipationWithResultForExercise(examExercise, TEST_PREFIX + "student1");
        ProgrammingExerciseStudentParticipation participation = programmingExerciseParticipationUtilService.findStudentParticipationByExerciseAndStudentLogin(examExercise,
                TEST_PREFIX + "student1");
        programmingExerciseUtilService.addProgrammingSubmissionToResultAndParticipation(result, participation, "test");
        return addTestCaseMessageToResult(examExercise, result, Visibility.ALWAYS);
    }

    private Feedback addLongFeedbackToResult(final Result result) {
        final Feedback feedback = new Feedback();
        feedback.setDetailText(LONG_FEEDBACK);

        participationUtilService.addFeedbackToResult(feedback, result);

        return feedback;
    }
}
