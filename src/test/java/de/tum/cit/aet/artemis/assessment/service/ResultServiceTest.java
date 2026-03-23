package de.tum.cit.aet.artemis.assessment.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Set;

import org.hibernate.Hibernate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.cit.aet.artemis.assessment.domain.AssessmentType;
import de.tum.cit.aet.artemis.assessment.domain.ComplaintType;
import de.tum.cit.aet.artemis.assessment.domain.Feedback;
import de.tum.cit.aet.artemis.assessment.domain.FeedbackType;
import de.tum.cit.aet.artemis.assessment.domain.LongFeedbackText;
import de.tum.cit.aet.artemis.assessment.domain.Result;
import de.tum.cit.aet.artemis.assessment.repository.ComplaintRepository;
import de.tum.cit.aet.artemis.assessment.repository.FeedbackRepository;
import de.tum.cit.aet.artemis.assessment.repository.LongFeedbackTextRepository;
import de.tum.cit.aet.artemis.assessment.repository.RatingRepository;
import de.tum.cit.aet.artemis.assessment.util.ComplaintUtilService;
import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.user.util.UserUtilService;
import de.tum.cit.aet.artemis.exam.domain.Exam;
import de.tum.cit.aet.artemis.exam.test_repository.ExamTestRepository;
import de.tum.cit.aet.artemis.exercise.domain.Submission;
import de.tum.cit.aet.artemis.exercise.domain.participation.StudentParticipation;
import de.tum.cit.aet.artemis.exercise.participation.util.ParticipationFactory;
import de.tum.cit.aet.artemis.exercise.participation.util.ParticipationUtilService;
import de.tum.cit.aet.artemis.exercise.test_repository.SubmissionTestRepository;
import de.tum.cit.aet.artemis.exercise.util.ExerciseUtilService;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseStudentParticipation;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingSubmission;
import de.tum.cit.aet.artemis.programming.test_repository.ProgrammingExerciseStudentParticipationTestRepository;
import de.tum.cit.aet.artemis.programming.test_repository.ProgrammingExerciseTestRepository;
import de.tum.cit.aet.artemis.programming.util.ProgrammingExerciseUtilService;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationIndependentTest;

class ResultServiceTest extends AbstractSpringIntegrationIndependentTest {

    private static final String TEST_PREFIX = "resultservice";

    @Autowired
    private ProgrammingExerciseTestRepository programmingExerciseRepository;

    @Autowired
    private ExamTestRepository examRepository;

    @Autowired
    private ResultService resultService;

    @Autowired
    private FeedbackRepository feedbackRepository;

    @Autowired
    private LongFeedbackTextRepository longFeedbackTextRepository;

    @Autowired
    private RatingRepository ratingRepository;

    @Autowired
    private ComplaintRepository complaintRepository;

    @Autowired
    private ComplaintUtilService complaintUtilService;

    @Autowired
    private SubmissionTestRepository submissionTestRepository;

    @Autowired
    private ProgrammingExerciseStudentParticipationTestRepository participationRepository;

    @Autowired
    private UserUtilService userUtilService;

    @Autowired
    private ProgrammingExerciseUtilService programmingExerciseUtilService;

    @Autowired
    private ExerciseUtilService exerciseUtilService;

    @Autowired
    private ParticipationUtilService participationUtilService;

    private ProgrammingExercise programmingExercise;

    private ProgrammingExerciseStudentParticipation programmingExerciseStudentParticipation;

    private StudentParticipation examStudentParticipation;

    @BeforeEach
    void reset() {
        userUtilService.addUsers(TEST_PREFIX, 2, 1, 1, 1);
        Course course = programmingExerciseUtilService.addCourseWithOneProgrammingExercise();
        this.programmingExercise = ExerciseUtilService.getFirstExerciseWithType(course, ProgrammingExercise.class);
        // This is done to avoid proxy issues in the processNewResult method of the ResultService.
        this.programmingExerciseStudentParticipation = participationUtilService.addStudentParticipationForProgrammingExercise(this.programmingExercise, TEST_PREFIX + "student1");
        participationUtilService.addSubmission(this.programmingExerciseStudentParticipation, new ProgrammingSubmission());

        ProgrammingExercise examProgrammingExercise = programmingExerciseUtilService.addCourseExamExerciseGroupWithOneProgrammingExercise();
        this.examStudentParticipation = participationUtilService.addStudentParticipationForProgrammingExercise(examProgrammingExercise, TEST_PREFIX + "student1");
        participationUtilService.addSubmission(examStudentParticipation, new ProgrammingSubmission());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void testFilterFeedbacksForClientAsTA() {
        this.testFilterFeedbacksForClientAsCurrentUser();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "editor1", roles = "EDITOR")
    void testFilterFeedbacksForClientAsEditor() {
        this.testFilterFeedbacksForClientAsCurrentUser();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testFilterFeedbacksForClientAsInstructor() {
        this.testFilterFeedbacksForClientAsCurrentUser();
    }

    private void testFilterFeedbacksForClientAsCurrentUser() {
        Result result = participationUtilService.addResultToSubmission(null, null, programmingExerciseStudentParticipation.findLatestSubmission().orElseThrow());
        result = participationUtilService.addVariousVisibilityFeedbackToResult(result);

        assertThat(resultService.filterFeedbackForClient(result)).containsExactlyInAnyOrderElementsOf(result.getFeedbacks());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "STUDENT")
    void testFilterFeedbacksForClientAreSortedWithManualFirst() {
        Result result = participationUtilService.addResultToSubmission(null, null, programmingExerciseStudentParticipation.findLatestSubmission().orElseThrow());
        result = participationUtilService.addVariousFeedbackTypeFeedbacksToResult(result);

        // The ordering should be the same as is declared in addVariousFeedbackTypeFeedbacksToResult()
        assertThat(resultService.filterFeedbackForClient(result)).isEqualTo(result.getFeedbacks());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "STUDENT")
    void testFilterFeedbacksForClientAsStudent_shouldFilterInExamsBeforePublish() {
        Exam exam = examStudentParticipation.getExercise().getExam();
        exam.setPublishResultsDate(ZonedDateTime.now().plusDays(2));
        examRepository.save(exam);
        Result result = participationUtilService.addResultToSubmission(null, null, examStudentParticipation.findLatestSubmission().orElseThrow());
        result = participationUtilService.addVariousVisibilityFeedbackToResult(result);

        List<Feedback> expectedFeedbacks = result.getFeedbacks().stream().filter(feedback -> !feedback.isInvisible() && !feedback.isAfterDueDate()).toList();

        assertThat(resultService.filterFeedbackForClient(result)).containsExactlyInAnyOrderElementsOf(expectedFeedbacks);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "STUDENT")
    void testFilterFeedbacksForClientAsStudent_shouldFilterInExamsAfterPublish() {
        Exam exam = examStudentParticipation.getExercise().getExam();
        exam.setPublishResultsDate(ZonedDateTime.now().minusDays(2));
        examRepository.save(exam);
        Result result = participationUtilService.addResultToSubmission(null, null, examStudentParticipation.findLatestSubmission().orElseThrow());
        result = participationUtilService.addVariousVisibilityFeedbackToResult(result);

        List<Feedback> expectedFeedbacks = result.getFeedbacks().stream().filter(feedback -> !feedback.isInvisible()).toList();

        assertThat(resultService.filterFeedbackForClient(result)).containsExactlyInAnyOrderElementsOf(expectedFeedbacks);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "STUDENT")
    void testFilterFeedbacksForClientAsStudent_shouldFilterInCourseBeforeDueDate() {
        programmingExercise.setDueDate(ZonedDateTime.now().plusDays(2));
        programmingExerciseRepository.save(programmingExercise);
        Result result = participationUtilService.addResultToSubmission(null, null, programmingExerciseStudentParticipation.findLatestSubmission().orElseThrow());
        result = participationUtilService.addVariousVisibilityFeedbackToResult(result);

        List<Feedback> expectedFeedbacks = result.getFeedbacks().stream().filter(feedback -> !feedback.isInvisible() && !feedback.isAfterDueDate()).toList();

        assertThat(resultService.filterFeedbackForClient(result)).containsExactlyInAnyOrderElementsOf(expectedFeedbacks);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "STUDENT")
    void testFilterFeedbacksForClientAsStudent_shouldFilterInCourseAfterDueDate() {
        programmingExercise.setDueDate(ZonedDateTime.now().minusDays(2));
        programmingExerciseRepository.save(programmingExercise);
        Result result = participationUtilService.addResultToSubmission(null, null, programmingExerciseStudentParticipation.findLatestSubmission().orElseThrow());
        result = participationUtilService.addVariousVisibilityFeedbackToResult(result);
        result = resultRepository.save(result);
        List<Feedback> expectedFeedbacks = result.getFeedbacks().stream().filter(feedback -> !feedback.isInvisible()).toList();

        assertThat(resultService.filterFeedbackForClient(result)).containsExactlyInAnyOrderElementsOf(expectedFeedbacks);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "STUDENT")
    void testFilterFeedbacksForClientAsStudent_shouldFilterInCourseBeforeAssessmentDueDateWithNonAutomaticResult() {
        programmingExercise.setDueDate(ZonedDateTime.now().minusDays(4));
        programmingExercise.setAssessmentDueDate(ZonedDateTime.now().plusDays(2));
        programmingExerciseRepository.save(programmingExercise);
        Result result = participationUtilService.addResultToSubmission(AssessmentType.SEMI_AUTOMATIC, null,
                programmingExerciseStudentParticipation.findLatestSubmission().orElseThrow());
        result = participationUtilService.addVariousVisibilityFeedbackToResult(result);
        result = participationUtilService.addFeedbackToResult(ParticipationFactory.createPositiveFeedback(FeedbackType.MANUAL), result);

        List<Feedback> expectedFeedbacks = result.getFeedbacks().stream()
                .filter(feedback -> !feedback.isInvisible() && feedback.getType() != null && feedback.getType().equals(FeedbackType.AUTOMATIC)).toList();

        assertThat(resultService.filterFeedbackForClient(result)).containsExactlyInAnyOrderElementsOf(expectedFeedbacks);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "STUDENT")
    void testFilterFeedbacksForClientAsStudent_shouldFilterInCourseAfterAssessmentDueDateWithNonAutomaticResult() {
        programmingExercise.setDueDate(ZonedDateTime.now().minusDays(4));
        programmingExercise.setAssessmentDueDate(ZonedDateTime.now().minusDays(2));
        programmingExerciseRepository.save(programmingExercise);
        Result result = participationUtilService.addResultToSubmission(AssessmentType.SEMI_AUTOMATIC, null,
                programmingExerciseStudentParticipation.findLatestSubmission().orElseThrow());
        result = participationUtilService.addVariousVisibilityFeedbackToResult(result);
        result = participationUtilService.addFeedbackToResult(ParticipationFactory.createPositiveFeedback(FeedbackType.MANUAL), result);

        List<Feedback> expectedFeedbacks = result.getFeedbacks().stream().filter(feedback -> !feedback.isInvisible()).toList();

        assertThat(resultService.filterFeedbackForClient(result)).containsExactlyInAnyOrderElementsOf(expectedFeedbacks);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "STUDENT")
    void testFilterFeedbacksForClientAsStudent_shouldFilterInCourseAfterAssessmentDueDateWithAutomaticResult() {
        programmingExercise.setDueDate(ZonedDateTime.now().minusDays(4));
        programmingExercise.setAssessmentDueDate(ZonedDateTime.now().minusDays(2));
        programmingExerciseRepository.save(programmingExercise);
        Result result = participationUtilService.addResultToSubmission(AssessmentType.AUTOMATIC, null,
                programmingExerciseStudentParticipation.findLatestSubmission().orElseThrow());
        result = participationUtilService.addVariousVisibilityFeedbackToResult(result);
        result = participationUtilService.addFeedbackToResult(ParticipationFactory.createPositiveFeedback(FeedbackType.MANUAL), result);

        List<Feedback> expectedFeedbacks = result.getFeedbacks().stream().filter(feedback -> !feedback.isInvisible()).toList();

        assertThat(resultService.filterFeedbackForClient(result)).containsExactlyInAnyOrderElementsOf(expectedFeedbacks);
    }

    @ParameterizedTest(name = "{displayName} [{index}] {argumentsWithNames}")
    @EnumSource(AssessmentType.class)
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "STUDENT")
    void testFilterFeedbacksForClientAsStudentShouldOnlyFilterAutomaticResultBeforeLastDueDate(AssessmentType assessmentType) {
        programmingExercise.setDueDate(ZonedDateTime.now().minusHours(2));
        programmingExercise.setAssessmentDueDate(null);
        programmingExercise = programmingExerciseRepository.save(programmingExercise);

        final var participation2 = participationUtilService.addStudentParticipationForProgrammingExercise(programmingExercise, TEST_PREFIX + "student2");
        participation2.setIndividualDueDate(ZonedDateTime.now().plusDays(2));
        participationRepository.save(participation2);

        Result result = participationUtilService.addResultToSubmission(assessmentType, null, programmingExerciseStudentParticipation.findLatestSubmission().orElseThrow());
        result = participationUtilService.addVariousVisibilityFeedbackToResult(result);
        result = participationUtilService.addFeedbackToResult(ParticipationFactory.createPositiveFeedback(FeedbackType.MANUAL), result);

        List<Feedback> expectedFeedbacks;
        if (AssessmentType.AUTOMATIC == assessmentType) {
            expectedFeedbacks = result.getFeedbacks().stream().filter(feedback -> !feedback.isInvisible() && !feedback.isAfterDueDate()).toList();
            assertThat(expectedFeedbacks).hasSize(2);
        }
        else {
            expectedFeedbacks = result.getFeedbacks().stream().filter(feedback -> !feedback.isInvisible()).toList();
            assertThat(expectedFeedbacks).hasSize(3);
        }
        assertThat(resultService.filterFeedbackForClient(result)).containsExactlyInAnyOrderElementsOf(expectedFeedbacks);
    }

    @ParameterizedTest(name = "{displayName} [{index}] {argumentsWithNames}")
    @EnumSource(AssessmentType.class)
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "STUDENT")
    void testFilterFeedbacksForClientAsStudentShouldNotFilterAfterLatestDueDate(AssessmentType assessmentType) {
        programmingExercise.setDueDate(ZonedDateTime.now().minusHours(2));
        programmingExercise.setAssessmentDueDate(null);
        programmingExercise = programmingExerciseRepository.save(programmingExercise);

        final var participation2 = participationUtilService.addStudentParticipationForProgrammingExercise(programmingExercise, TEST_PREFIX + "student2");
        participation2.setIndividualDueDate(ZonedDateTime.now().minusHours(1));
        participationRepository.save(participation2);

        Result result = participationUtilService.addResultToSubmission(assessmentType, null, programmingExerciseStudentParticipation.findLatestSubmission().orElseThrow());
        result = participationUtilService.addVariousVisibilityFeedbackToResult(result);
        result = participationUtilService.addFeedbackToResult(ParticipationFactory.createPositiveFeedback(FeedbackType.MANUAL), result);

        List<Feedback> expectedFeedbacks;
        expectedFeedbacks = result.getFeedbacks().stream().filter(feedback -> !feedback.isInvisible()).toList();
        assertThat(expectedFeedbacks).hasSize(3);
        assertThat(resultService.filterFeedbackForClient(result)).containsExactlyInAnyOrderElementsOf(expectedFeedbacks);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testDeleteResultWithFeedback_deletesResultAndAllFeedbacks() {
        Result result = participationUtilService.addResultToSubmission(null, null, programmingExerciseStudentParticipation.findLatestSubmission().orElseThrow());
        result = participationUtilService.addVariousVisibilityFeedbackToResult(result);
        Long resultId = result.getId();
        List<Long> feedbackIds = result.getFeedbacks().stream().map(Feedback::getId).toList();

        assertThat(result.getFeedbacks()).isNotEmpty();
        assertThat(feedbackIds).allSatisfy(id -> assertThat(feedbackRepository.findById(id)).isPresent());

        resultService.deleteResult(result, true);

        assertThat(resultRepository.findById(resultId)).isEmpty();
        assertThat(feedbackIds).allSatisfy(id -> assertThat(feedbackRepository.findById(id)).isEmpty());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testDeleteResultWithFeedbackAndLongFeedbackText_deletesAllRelatedEntities() {
        Result result = participationUtilService.addResultToSubmission(null, null, programmingExerciseStudentParticipation.findLatestSubmission().orElseThrow());

        // Create feedback with associated long feedback text
        Feedback feedback = new Feedback();
        feedback.setDetailText("short text");
        feedback.setHasLongFeedbackText(true);
        feedback = feedbackRepository.save(feedback);

        LongFeedbackText longFeedbackText = new LongFeedbackText();
        longFeedbackText.setFeedback(feedback);
        longFeedbackText.setText("This is a very long feedback text that exceeds the normal limit");
        longFeedbackTextRepository.save(longFeedbackText);

        feedback.setLongFeedbackText(Set.of(longFeedbackText));
        result.addFeedback(feedback);
        result = resultRepository.save(result);

        Long resultId = result.getId();
        Long feedbackId = feedback.getId();
        Long longFeedbackTextId = longFeedbackText.getId();

        assertThat(result.getFeedbacks()).hasSize(1);
        assertThat(longFeedbackTextRepository.findByFeedbackId(feedbackId)).isPresent();

        resultService.deleteResult(result, true);

        assertThat(resultRepository.findById(resultId)).isEmpty();
        assertThat(feedbackRepository.findById(feedbackId)).isEmpty();
        assertThat(longFeedbackTextRepository.findById(longFeedbackTextId)).isEmpty();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testDeleteResultWithRating_deletesResultAndRating() {
        Result result = participationUtilService.addResultToSubmission(null, null, programmingExerciseStudentParticipation.findLatestSubmission().orElseThrow());
        result = participationUtilService.addVariousVisibilityFeedbackToResult(result);
        participationUtilService.addRatingToResult(result, 4);
        Long resultId = result.getId();

        assertThat(ratingRepository.findRatingByResultId(resultId)).isPresent();

        resultService.deleteResult(result, true);

        assertThat(resultRepository.findById(resultId)).isEmpty();
        assertThat(ratingRepository.findRatingByResultId(resultId)).isEmpty();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testDeleteResultWithComplaint_deletesResultAndComplaint() {
        Result result = participationUtilService.addResultToSubmission(AssessmentType.MANUAL, null, programmingExerciseStudentParticipation.findLatestSubmission().orElseThrow());
        result = participationUtilService.addVariousVisibilityFeedbackToResult(result);
        result.setCompletionDate(ZonedDateTime.now());
        result = resultRepository.save(result);
        Long resultId = result.getId();

        complaintUtilService.addComplaintToSubmission(result.getSubmission(), TEST_PREFIX + "student1", ComplaintType.COMPLAINT);

        assertThat(complaintRepository.findByResultId(resultId)).isPresent();

        resultService.deleteResult(result, true);

        assertThat(resultRepository.findById(resultId)).isEmpty();
        assertThat(complaintRepository.findByResultId(resultId)).isEmpty();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testDeleteResultWithMultipleFeedbacksAndRating_deletesEverything() {
        Result result = participationUtilService.addResultToSubmission(null, null, programmingExerciseStudentParticipation.findLatestSubmission().orElseThrow());
        result = participationUtilService.addVariousVisibilityFeedbackToResult(result);
        participationUtilService.addRatingToResult(result, 3);
        Long resultId = result.getId();
        List<Long> feedbackIds = result.getFeedbacks().stream().map(Feedback::getId).toList();

        assertThat(feedbackIds).hasSizeGreaterThan(1);
        assertThat(ratingRepository.findRatingByResultId(resultId)).isPresent();

        resultService.deleteResult(result, true);

        assertThat(resultRepository.findById(resultId)).isEmpty();
        assertThat(feedbackIds).allSatisfy(id -> assertThat(feedbackRepository.findById(id)).isEmpty());
        assertThat(ratingRepository.findRatingByResultId(resultId)).isEmpty();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testDeleteResultPreservesOtherResultsOnSameParticipation() {
        var submission1 = programmingExerciseStudentParticipation.findLatestSubmission().orElseThrow();
        Result result1 = participationUtilService.addResultToSubmission(null, null, submission1);
        result1 = participationUtilService.addVariousVisibilityFeedbackToResult(result1);

        // Add a second submission and result to the same participation
        ProgrammingSubmission submission2 = new ProgrammingSubmission();
        participationUtilService.addSubmission(programmingExerciseStudentParticipation, submission2);
        Result result2 = participationUtilService.addResultToSubmission(null, null, submission2);
        result2 = participationUtilService.addVariousVisibilityFeedbackToResult(result2);

        Long result1Id = result1.getId();
        Long result2Id = result2.getId();

        resultService.deleteResult(result1, true);

        assertThat(resultRepository.findById(result1Id)).isEmpty();
        assertThat(resultRepository.findById(result2Id)).isPresent();
        // Use a query that eagerly fetches feedbacks to avoid LazyInitializationException
        Result survivingResult = resultRepository.findResultWithFeedbacksAndTestCasesById(result2Id).orElseThrow();
        assertThat(survivingResult.getFeedbacks()).isNotEmpty();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testDeleteResultWithUninitializedFeedbacks_shouldNotThrowEntityNotFoundException() {
        Result result = participationUtilService.addResultToSubmission(null, null, programmingExerciseStudentParticipation.findLatestSubmission().orElseThrow());
        result = participationUtilService.addVariousVisibilityFeedbackToResult(result);

        long resultId = result.getId();
        long submissionId = result.getSubmission().getId();
        assertThat(result.getFeedbacks()).isNotEmpty();
        assertThat(feedbackRepository.findByResult(result)).isNotEmpty();

        // Reload the submission WITHOUT eager feedbacks (only results + assessor),
        // simulating the exact query used by cancelAssessment in AssessmentResource.
        Submission reloadedSubmission = submissionTestRepository.findByIdWithResultsElseThrow(submissionId);
        Result resultWithLazyFeedbacks = reloadedSubmission.getLatestResult();

        // Verify that feedbacks are NOT initialized (lazy proxy)
        assertThat(Hibernate.isInitialized(resultWithLazyFeedbacks.getFeedbacks())).isFalse();

        // This must not throw EntityNotFoundException for already bulk-deleted feedbacks
        resultService.deleteResult(resultWithLazyFeedbacks, true);

        assertThat(resultRepository.findById(resultId)).isEmpty();
        assertThat(feedbackRepository.findByResult(result)).isEmpty();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testDeleteResultWithUninitializedFeedbacksAndLongFeedbackText_shouldNotViolateForeignKeyConstraint() {
        Result result = participationUtilService.addResultToSubmission(null, null, programmingExerciseStudentParticipation.findLatestSubmission().orElseThrow());

        // Create feedback with long feedback text (FK: long_feedback_text -> feedback -> result)
        Feedback feedback = new Feedback();
        feedback.setDetailText("short text");
        feedback.setHasLongFeedbackText(true);
        feedback = feedbackRepository.save(feedback);

        LongFeedbackText longFeedbackText = new LongFeedbackText();
        longFeedbackText.setFeedback(feedback);
        longFeedbackText.setText("This is a very long feedback text that exceeds the normal limit");
        longFeedbackTextRepository.save(longFeedbackText);

        feedback.setLongFeedbackText(Set.of(longFeedbackText));
        result.addFeedback(feedback);
        result = resultRepository.save(result);

        long resultId = result.getId();
        long submissionId = result.getSubmission().getId();
        assertThat(result.getFeedbacks()).hasSize(1);
        assertThat(longFeedbackTextRepository.findByFeedbackId(feedback.getId())).isPresent();

        // Reload WITHOUT eager feedbacks to get uninitialized proxy (like cancelAssessment does)
        Submission reloadedSubmission = submissionTestRepository.findByIdWithResultsElseThrow(submissionId);
        Result resultWithLazyFeedbacks = reloadedSubmission.getLatestResult();
        assertThat(Hibernate.isInitialized(resultWithLazyFeedbacks.getFeedbacks())).isFalse();

        // This must delete long_feedback_text -> feedback -> result without FK violations
        // and without EntityNotFoundException for uninitialized feedbacks
        resultService.deleteResult(resultWithLazyFeedbacks, true);

        assertThat(resultRepository.findById(resultId)).isEmpty();
    }
}
