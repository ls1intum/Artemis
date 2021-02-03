package de.tum.in.www1.artemis.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import de.tum.in.www1.artemis.AbstractSpringIntegrationBambooBitbucketJiraTest;
import de.tum.in.www1.artemis.domain.enumeration.TestCaseVisibility;
import de.tum.in.www1.artemis.service.AssessmentService;

public class ResultTest extends AbstractSpringIntegrationBambooBitbucketJiraTest {

    Result result = new Result();

    List<Feedback> feedbackList;

    @Autowired
    AssessmentService assessmentService;

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
        feedbackList = Arrays.asList(feedback1, feedback2, feedback3, feedback4, feedback5);
    }

    @Test
    public void evaluateFeedback() {
        double maxPoints = 7.0;
        result.setFeedbacks(feedbackList);

        Double calculatedPoints = assessmentService.calculateTotalPoints(feedbackList);
        double totalPoints = assessmentService.calculateTotalPoints(calculatedPoints, maxPoints);
        result.setScore(totalPoints, maxPoints);
        result.setResultString(totalPoints, maxPoints);

        assertThat(result.getScore()).isEqualTo(Math.round(5.0 / maxPoints * 100));
        assertThat(result.getResultString()).isEqualToIgnoringCase("5 of 7 points");
    }

    @Test
    public void evaluateFeedback_totalScoreGreaterMaxScore() {
        result.setFeedbacks(feedbackList);

        Double calculatePoints = assessmentService.calculateTotalPoints(feedbackList);
        double totalPoints = assessmentService.calculateTotalPoints(calculatePoints, 4.0);
        result.setScore(totalPoints, 4.0);
        result.setResultString(totalPoints, 4.0);

        assertThat(result.getScore()).isEqualTo(100);
        assertThat(result.getResultString()).isEqualToIgnoringCase("4 of 4 points");
    }

    @Test
    public void evaluateFeedback_negativeTotalScore() {
        Feedback feedback1 = new Feedback();
        feedback1.setCredits(-2.5);
        Feedback feedback2 = new Feedback();
        feedback2.setCredits(-0.5);
        Feedback feedback3 = new Feedback();
        feedback3.setCredits(1.567);
        feedbackList = Arrays.asList(feedback1, feedback2, feedback3);
        result.setFeedbacks(feedbackList);

        Double calculatePoints = assessmentService.calculateTotalPoints(feedbackList);
        double totalPoints = assessmentService.calculateTotalPoints(calculatePoints, 7.0);
        result.setScore(totalPoints, 7.0);
        result.setResultString(totalPoints, 7.0);

        assertThat(result.getScore()).isEqualTo(0);
        assertThat(result.getResultString()).isEqualToIgnoringCase("0 of 7 points");
    }

    @Test
    public void filterSensitiveFeedbacksAfterDueDate() {
        Feedback feedback1 = new Feedback().visibility(TestCaseVisibility.ALWAYS);
        Feedback feedback2 = new Feedback().visibility(TestCaseVisibility.AFTER_DUE_DATE);
        Feedback feedback3 = new Feedback().visibility(TestCaseVisibility.NEVER);
        result.setFeedbacks(new ArrayList<>(List.of(feedback1, feedback2, feedback3)));

        result.filterSensitiveFeedbacks(false);
        assertThat(result.getFeedbacks()).isEqualTo(List.of(feedback1, feedback2));
    }

    @Test
    public void filterSensitiveFeedbacksBeforeDueDate() {
        Feedback feedback1 = new Feedback().visibility(TestCaseVisibility.ALWAYS);
        Feedback feedback2 = new Feedback().visibility(TestCaseVisibility.AFTER_DUE_DATE);
        Feedback feedback3 = new Feedback().visibility(TestCaseVisibility.NEVER);
        result.setFeedbacks(new ArrayList<>(List.of(feedback1, feedback2, feedback3)));

        result.filterSensitiveFeedbacks(true);
        assertThat(result.getFeedbacks()).isEqualTo(List.of(feedback1));
    }
}
