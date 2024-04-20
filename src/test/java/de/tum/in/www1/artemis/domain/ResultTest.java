package de.tum.in.www1.artemis.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;

import org.assertj.core.data.Offset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import de.tum.in.www1.artemis.AbstractSpringIntegrationIndependentTest;
import de.tum.in.www1.artemis.course.CourseUtilService;
import de.tum.in.www1.artemis.domain.enumeration.FeedbackType;
import de.tum.in.www1.artemis.domain.enumeration.Visibility;
import de.tum.in.www1.artemis.domain.participation.ProgrammingExerciseStudentParticipation;
import de.tum.in.www1.artemis.domain.participation.StudentParticipation;
import de.tum.in.www1.artemis.exercise.programmingexercise.ProgrammingExerciseUtilService;
import de.tum.in.www1.artemis.repository.ResultRepository;

class ResultTest extends AbstractSpringIntegrationIndependentTest {

    Result result = new Result();

    List<Feedback> feedbackList;

    private Course course;

    Double offsetByTenThousandth = 0.0001;

    @Autowired
    private ResultRepository resultRepository;

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
        result.setParticipation(new StudentParticipation().exercise(new TextExercise().course(course)));
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
    void filterSensitiveFeedbacksAfterDueDate() {
        Feedback feedback1 = new Feedback().visibility(Visibility.ALWAYS);
        Feedback feedback2 = new Feedback().visibility(Visibility.AFTER_DUE_DATE);
        Feedback feedback3 = new Feedback().visibility(Visibility.NEVER);
        result.setFeedbacks(new ArrayList<>(List.of(feedback1, feedback2, feedback3)));

        result.filterSensitiveFeedbacks(false);
        assertThat(result.getFeedbacks()).isEqualTo(List.of(feedback1, feedback2));
    }

    @Test
    void filterSensitiveFeedbacksBeforeDueDate() {
        Feedback feedback1 = new Feedback().visibility(Visibility.ALWAYS);
        Feedback feedback2 = new Feedback().visibility(Visibility.AFTER_DUE_DATE);
        Feedback feedback3 = new Feedback().visibility(Visibility.NEVER);
        result.setFeedbacks(new ArrayList<>(List.of(feedback1, feedback2, feedback3)));

        result.filterSensitiveFeedbacks(true);
        assertThat(result.getFeedbacks()).isEqualTo(List.of(feedback1));
    }

    @Test
    void testRemoveTestCaseNames() {
        ProgrammingExercise exercise = programmingExerciseUtilService.addProgrammingExerciseToCourse(course);
        var tests = programmingExerciseUtilService.addTestCasesToProgrammingExercise(exercise);
        Feedback tst1 = new Feedback().positive(true).type(FeedbackType.AUTOMATIC).testCase(tests.get(0));
        Feedback tst2 = new Feedback().positive(false).type(FeedbackType.AUTOMATIC).testCase(tests.get(2)).detailText("This is wrong.");

        ProgrammingExerciseStudentParticipation participation = new ProgrammingExerciseStudentParticipation();
        participation.setExercise(exercise);
        result.setParticipation(participation);
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
        result.setParticipation(participation);

        Feedback tst1 = new Feedback().positive(true).type(FeedbackType.AUTOMATIC).testCase(tests.get(0));
        Feedback tst2 = new Feedback().positive(false).type(FeedbackType.AUTOMATIC).testCase(tests.get(1)).detailText("This is wrong.");

        result.setFeedbacks(new ArrayList<>(List.of(tst1, tst2)));

        result.filterSensitiveFeedbacks(true);

        assertThat(result.getFeedbacks()).hasSize(2).allMatch(feedback -> feedback.getTestCase().getTestName() != null);
    }
}
