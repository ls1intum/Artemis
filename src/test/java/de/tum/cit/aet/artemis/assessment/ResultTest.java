package de.tum.cit.aet.artemis.assessment;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.assertj.core.data.Offset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.assessment.domain.AssessmentNote;
import de.tum.cit.aet.artemis.assessment.domain.Feedback;
import de.tum.cit.aet.artemis.assessment.domain.FeedbackType;
import de.tum.cit.aet.artemis.assessment.domain.Result;
import de.tum.cit.aet.artemis.assessment.domain.ScaFeedback;
import de.tum.cit.aet.artemis.assessment.domain.TestCaseFeedback;
import de.tum.cit.aet.artemis.assessment.domain.Visibility;
import de.tum.cit.aet.artemis.assessment.test_repository.ResultTestRepository;
import de.tum.cit.aet.artemis.core.util.CourseUtilService;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.exercise.participation.util.ParticipationFactory;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseStudentParticipation;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseTestCase;
import de.tum.cit.aet.artemis.programming.service.ProgrammingFeedbackSynthesizerService;
import de.tum.cit.aet.artemis.programming.util.ProgrammingExerciseUtilService;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationIndependentBatchTest;
import de.tum.cit.aet.artemis.text.domain.TextExercise;

class ResultTest extends AbstractSpringIntegrationIndependentBatchTest {

    Result result = new Result();

    List<Feedback> feedbackList;

    private Course course;

    Double offsetByTenThousandth = 0.0001;

    @Autowired
    private ResultTestRepository resultRepository;

    @Autowired
    private CourseUtilService courseUtilService;

    @Autowired
    private ProgrammingExerciseUtilService programmingExerciseUtilService;

    @BeforeEach
    void setUp() {
        Feedback feedback1 = new Feedback();
        feedback1.setCredits(2.5);
        Feedback feedback2 = new Feedback();
        feedback2.setCredits(-0.5);
        Feedback feedback3 = new Feedback();
        feedback3.setCredits(1.5);
        Feedback feedback4 = new Feedback();
        feedback4.setCredits(-1.5);
        Feedback feedback5 = new Feedback();
        feedback5.setCredits(3.0);
        feedbackList = List.of(feedback1, feedback2, feedback3, feedback4, feedback5);

        course = courseUtilService.addEmptyCourse();
        course.setAccuracyOfScores(1);
        var exercise = new TextExercise().course(course);
        ProgrammingExerciseStudentParticipation participation = new ProgrammingExerciseStudentParticipation();
        participation.setExercise(exercise);
        var submission = ParticipationFactory.generateProgrammingSubmission(true);
        submission.setParticipation(participation);
        result.setSubmission(submission);
    }

    @Test
    void calculateTotalPointsCountsASynthesizedViewOnlyOnce() {
        var programmingExercise = new ProgrammingExercise();
        programmingExercise.setMaxPoints(10.0);
        var participation = new ProgrammingExerciseStudentParticipation();
        participation.setExercise(programmingExercise);
        var submission = ParticipationFactory.generateProgrammingSubmission(true);
        submission.setParticipation(participation);
        var scoredResult = new Result();
        scoredResult.setSubmission(submission);

        var testCase = new ProgrammingExerciseTestCase();
        testCase.setId(42L);
        var passedTest = new TestCaseFeedback();
        passedTest.setTestCase(testCase);
        passedTest.setPositive(true);
        scoredResult.addTestCaseFeedback(passedTest);

        // the synthesized view of that very row, as every serialization path attaches it, carrying the derived credits
        var view = new Feedback();
        view.setId(ProgrammingFeedbackSynthesizerService.syntheticId(1L, 1));
        view.setType(FeedbackType.AUTOMATIC);
        view.setCredits(4.0);
        scoredResult.setFeedbacks(List.of(view));

        // 4 points, not 8: the view and the row it was derived from are the same feedback
        assertThat(scoredResult.calculateTotalPointsForProgrammingExercises(Map.of(42L, 4.0))).isEqualTo(4.0);
    }

    /**
     * The sequence number is part of the primary key of {@code test_case_feedback}. Re-evaluating a result
     * removes the rows of test cases that are no longer active and adds rows for active test cases that the
     * build did not execute — in the same flush. Handing a removed row's number to a new row makes Hibernate
     * (which flushes inserts before deletes) fail the whole re-evaluation with a duplicate-key error.
     */
    @Test
    void doesNotReuseTheSequenceNumberOfARemovedTestCaseFeedback() {
        var reEvaluated = new Result();
        reEvaluated.setTestCaseFeedbacks(List.of(testCaseFeedbackWithSeq(1), testCaseFeedbackWithSeq(2), testCaseFeedbackWithSeq(3)));

        // the test case of the last row was deactivated, so re-evaluation drops it
        assertThat(reEvaluated.removeTestCaseFeedbackIf(feedback -> feedback.getSeq() == 3)).isTrue();

        // ... and another test case is active but was not executed, so a row is created for it
        var notExecuted = new TestCaseFeedback();
        reEvaluated.addTestCaseFeedback(notExecuted);

        assertThat(notExecuted.getSeq()).isEqualTo(4);
        assertThat(reEvaluated.getTestCaseFeedbacks()).extracting(TestCaseFeedback::getSeq).containsExactlyInAnyOrder(1, 2, 4);
    }

    @Test
    void doesNotReuseTheSequenceNumberOfARemovedScaFeedback() {
        var reEvaluated = new Result();
        var removed = scaFeedbackWithSeq(2);
        reEvaluated.setScaFeedbacks(List.of(scaFeedbackWithSeq(1), removed));

        // the category of the second issue was set to invisible, so it is dropped
        assertThat(reEvaluated.removeScaFeedback(removed)).isTrue();

        var added = new ScaFeedback();
        reEvaluated.addScaFeedback(added);

        assertThat(added.getSeq()).isEqualTo(3);
        assertThat(reEvaluated.getScaFeedbacks()).extracting(ScaFeedback::getSeq).containsExactlyInAnyOrder(1, 3);
    }

    private static TestCaseFeedback testCaseFeedbackWithSeq(int seq) {
        var feedback = new TestCaseFeedback();
        feedback.setSeq(seq);
        return feedback;
    }

    private static ScaFeedback scaFeedbackWithSeq(int seq) {
        var feedback = new ScaFeedback();
        feedback.setSeq(seq);
        return feedback;
    }

    @Test
    void evaluateFeedback() {
        double maxPoints = 7.0;
        result.setFeedbacks(feedbackList);

        double calculatedPoints = resultRepository.calculateTotalPoints(feedbackList);
        double totalPoints = resultRepository.constrainToRange(calculatedPoints, maxPoints);
        result.setScore(100.0 * totalPoints / maxPoints);

        assertThat(result.getScore()).isEqualTo(5.0 / maxPoints * 100, Offset.offset(offsetByTenThousandth));
    }

    @Test
    void evaluateFeedback_totalScoreGreaterMaxScore() {
        result.setFeedbacks(feedbackList);

        double calculatePoints = resultRepository.calculateTotalPoints(feedbackList);
        double totalPoints = resultRepository.constrainToRange(calculatePoints, 4.0);
        result.setScore(100.0 * totalPoints / 4.0);

        assertThat(result.getScore()).isEqualTo(100);
    }

    @Test
    void evaluateFeedback_negativeTotalScore() {
        Feedback feedback1 = new Feedback();
        feedback1.setCredits(-2.5);
        Feedback feedback2 = new Feedback();
        feedback2.setCredits(-0.5);
        Feedback feedback3 = new Feedback();
        feedback3.setCredits(1.567);
        feedbackList = List.of(feedback1, feedback2, feedback3);
        result.setFeedbacks(feedbackList);

        double calculatePoints = resultRepository.calculateTotalPoints(feedbackList);
        double totalPoints = resultRepository.constrainToRange(calculatePoints, 7.0);
        result.setScore(100.0 * totalPoints / 7.0);

        assertThat(result.getScore()).isZero();
    }

    @Test
    void setScoreShouldUseZeroIfMaxPointsAreZero() {
        result.setScore(5.0, 0.0, course);

        assertThat(result.getScore()).isZero();
        assertThat(result.isSuccessful()).isFalse();
    }

    @Test
    void filterSensitiveFeedbacksAfterDueDate() {
        Feedback feedback1 = new Feedback().visibility(Visibility.ALWAYS);
        Feedback feedback2 = new Feedback().visibility(Visibility.AFTER_DUE_DATE);
        Feedback feedback3 = new Feedback().visibility(Visibility.NEVER);
        result.setFeedbacks(new ArrayList<>(List.of(feedback1, feedback2, feedback3)));

        result.filterSensitiveFeedbacks(false);
        assertThat(result.getFeedbacks()).containsExactlyInAnyOrder(feedback1, feedback2);
    }

    @Test
    void filterSensitiveFeedbacksBeforeDueDate() {
        Feedback feedback1 = new Feedback().visibility(Visibility.ALWAYS);
        Feedback feedback2 = new Feedback().visibility(Visibility.AFTER_DUE_DATE);
        Feedback feedback3 = new Feedback().visibility(Visibility.NEVER);
        result.setFeedbacks(new ArrayList<>(List.of(feedback1, feedback2, feedback3)));

        result.filterSensitiveFeedbacks(true);
        assertThat(result.getFeedbacks()).containsExactly(feedback1);
    }

    @Test
    void testRemoveTestCaseNames() {
        ProgrammingExercise exercise = programmingExerciseUtilService.addProgrammingExerciseToCourse(course);
        var tests = programmingExerciseUtilService.addTestCasesToProgrammingExercise(exercise);
        Feedback tst1 = new Feedback().positive(true).type(FeedbackType.AUTOMATIC).testCase(tests.getFirst());
        Feedback tst2 = new Feedback().positive(false).type(FeedbackType.AUTOMATIC).testCase(tests.get(2)).detailText("This is wrong.");

        ProgrammingExerciseStudentParticipation participation = new ProgrammingExerciseStudentParticipation();
        participation.setExercise(exercise);
        var submission = ParticipationFactory.generateProgrammingSubmission(true);
        submission.setParticipation(participation);
        result.setSubmission(submission);
        result.setFeedbacks(new ArrayList<>(List.of(tst1, tst2)));

        result.filterSensitiveFeedbacks(true);
        assertThat(result.getFeedbacks()).hasSize(2).allMatch(feedback -> feedback.getTestCase().getTestName() == null);
    }

    @Test
    void keepTestNamesWhenExerciseSettingActive() {
        ProgrammingExercise exercise = programmingExerciseUtilService.addProgrammingExerciseToCourse(course);
        exercise.setShowTestNamesToStudents(true);
        var tests = programmingExerciseUtilService.addTestCasesToProgrammingExercise(exercise);

        ProgrammingExerciseStudentParticipation participation = new ProgrammingExerciseStudentParticipation();
        participation.setExercise(exercise);

        Feedback tst1 = new Feedback().positive(true).type(FeedbackType.AUTOMATIC).testCase(tests.getFirst());
        Feedback tst2 = new Feedback().positive(false).type(FeedbackType.AUTOMATIC).testCase(tests.get(1)).detailText("This is wrong.");

        result.setFeedbacks(new ArrayList<>(List.of(tst1, tst2)));

        result.filterSensitiveFeedbacks(true);

        assertThat(result.getFeedbacks()).hasSize(2).allMatch(feedback -> feedback.getTestCase().getTestName() != null);
    }

    @Test
    void createFilteredFeedbacks_shouldHandleNullFeedbackEntries() {
        Feedback feedback1 = new Feedback().visibility(Visibility.ALWAYS);
        feedback1.setCredits(1.0);
        Feedback feedback2 = new Feedback().visibility(Visibility.ALWAYS);
        feedback2.setCredits(2.0);
        // Simulate null gaps from Hibernate @OrderColumn when feedback entries are deleted
        List<Feedback> feedbacksWithNulls = new ArrayList<>();
        feedbacksWithNulls.add(feedback1);
        feedbacksWithNulls.add(null);
        feedbacksWithNulls.add(feedback2);
        feedbacksWithNulls.add(null);
        result.setFeedbacks(feedbacksWithNulls);

        var filtered = result.createFilteredFeedbacks(false, new TextExercise().course(course));
        assertThat(filtered).containsExactly(feedback1, feedback2);
    }

    @Test
    void filterSensitiveFeedbacks_shouldHandleNullFeedbackEntries() {
        Feedback feedback1 = new Feedback().visibility(Visibility.ALWAYS);
        feedback1.setCredits(1.0);
        Feedback feedback2 = new Feedback().visibility(Visibility.AFTER_DUE_DATE);
        feedback2.setCredits(2.0);
        List<Feedback> feedbacksWithNulls = new ArrayList<>();
        feedbacksWithNulls.add(feedback1);
        feedbacksWithNulls.add(null);
        feedbacksWithNulls.add(feedback2);
        result.setFeedbacks(feedbacksWithNulls);

        result.filterSensitiveFeedbacks(true);
        assertThat(result.getFeedbacks()).containsExactly(feedback1);
    }

    @Test
    void filterSensitiveInformation() {
        Result result = new Result();
        result.setAssessor(new User());
        result.setAssessmentNote(new AssessmentNote());

        assertThat(result.getAssessor()).isNotNull();
        assertThat(result.getAssessmentNote()).isNotNull();

        result.filterSensitiveInformation();

        assertThat(result.getAssessor()).isNull();
        assertThat(result.getAssessmentNote()).isNull();
    }
}
