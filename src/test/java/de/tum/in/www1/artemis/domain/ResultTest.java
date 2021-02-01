package de.tum.in.www1.artemis.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import de.tum.in.www1.artemis.AbstractSpringIntegrationBambooBitbucketJiraTest;
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
        double maxScore = 7.0;
        result.setFeedbacks(feedbackList);

        Double calculatedScore = assessmentService.calculateTotalPoints(feedbackList);
        double totalScore = assessmentService.calculateTotalPoints(calculatedScore, maxScore);
        result.setScore(totalScore, maxScore);
        result.setResultString(totalScore, maxScore);

        assertThat(result.getScore()).isEqualTo(Math.round(5.0 / maxScore * 100));
        assertThat(result.getResultString()).isEqualToIgnoringCase("5 of 7 points");
    }

    @Test
    public void evaluateFeedback_totalScoreGreaterMaxScore() {
        result.setFeedbacks(feedbackList);

        Double calculatedScore = assessmentService.calculateTotalPoints(feedbackList);
        double totalScore = assessmentService.calculateTotalPoints(calculatedScore, 4.0);
        result.setScore(totalScore, 4.0);
        result.setResultString(totalScore, 4.0);

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

        Double calculatedScore = assessmentService.calculateTotalPoints(feedbackList);
        double totalScore = assessmentService.calculateTotalPoints(calculatedScore, 7.0);
        result.setScore(totalScore, 7.0);
        result.setResultString(totalScore, 7.0);

        assertThat(result.getScore()).isEqualTo(0);
        assertThat(result.getResultString()).isEqualToIgnoringCase("0 of 7 points");
    }
}
