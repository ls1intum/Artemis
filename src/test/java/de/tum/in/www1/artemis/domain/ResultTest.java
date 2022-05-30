package de.tum.in.www1.artemis.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;

import org.assertj.core.data.Offset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import de.tum.in.www1.artemis.AbstractSpringIntegrationBambooBitbucketJiraTest;
import de.tum.in.www1.artemis.domain.enumeration.Visibility;
import de.tum.in.www1.artemis.domain.participation.StudentParticipation;
import de.tum.in.www1.artemis.repository.ResultRepository;
import de.tum.in.www1.artemis.service.AssessmentService;

public class ResultTest extends AbstractSpringIntegrationBambooBitbucketJiraTest {

    Result result = new Result();

    List<Feedback> feedbackList;

    Double offsetByTenThousandth = 0.0001;

    @Autowired
    AssessmentService assessmentService;

    @Autowired
    ResultRepository resultRepository;

    @BeforeEach
    public void setUp() throws Exception {
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

        Course course = new Course();
        course.setAccuracyOfScores(1);
        result.setParticipation(new StudentParticipation().exercise(new TextExercise().course(course)));
    }

    @Test
    public void evaluateFeedback() {
        double maxPoints = 7.0;
        result.setFeedbacks(feedbackList);

        double calculatedPoints = resultRepository.calculateTotalPoints(feedbackList);
        double totalPoints = resultRepository.constrainToRange(calculatedPoints, maxPoints);
        result.setScore(100.0 * totalPoints / maxPoints);

        assertThat(result.getScore()).isEqualTo(5.0 / maxPoints * 100, Offset.offset(offsetByTenThousandth));
    }

    @Test
    public void evaluateFeedback_totalScoreGreaterMaxScore() {
        result.setFeedbacks(feedbackList);

        double calculatePoints = resultRepository.calculateTotalPoints(feedbackList);
        double totalPoints = resultRepository.constrainToRange(calculatePoints, 4.0);
        result.setScore(100.0 * totalPoints / 4.0);

        assertThat(result.getScore()).isEqualTo(100);
    }

    @Test
    public void evaluateFeedback_negativeTotalScore() {
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
    public void filterSensitiveFeedbacksAfterDueDate() {
        Feedback feedback1 = new Feedback().visibility(Visibility.ALWAYS);
        Feedback feedback2 = new Feedback().visibility(Visibility.AFTER_DUE_DATE);
        Feedback feedback3 = new Feedback().visibility(Visibility.NEVER);
        result.setFeedbacks(new ArrayList<>(List.of(feedback1, feedback2, feedback3)));

        result.filterSensitiveFeedbacks(false);
        assertThat(result.getFeedbacks()).isEqualTo(List.of(feedback1, feedback2));
    }

    @Test
    public void filterSensitiveFeedbacksBeforeDueDate() {
        Feedback feedback1 = new Feedback().visibility(Visibility.ALWAYS);
        Feedback feedback2 = new Feedback().visibility(Visibility.AFTER_DUE_DATE);
        Feedback feedback3 = new Feedback().visibility(Visibility.NEVER);
        result.setFeedbacks(new ArrayList<>(List.of(feedback1, feedback2, feedback3)));

        result.filterSensitiveFeedbacks(true);
        assertThat(result.getFeedbacks()).isEqualTo(List.of(feedback1));
    }
}
