package de.tum.in.www1.artemis.domain;

import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;


public class ResultTest {

    Result result = new Result();
    List<Feedback> feedbackList;

    @Before
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

        result.evaluateFeedback(maxScore);

        assertThat(result.getScore()).isEqualTo(Math.round(5.0 / maxScore * 100));
        assertThat(result.getResultString()).isEqualToIgnoringCase("5 of 7 points");
    }

    @Test
    public void evaluateFeedback_totalScoreGreaterMaxScore() {
        result.setFeedbacks(feedbackList);

        result.evaluateFeedback(4);

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
        feedback3.setCredits(1.5);
        feedbackList = Arrays.asList(feedback1, feedback2, feedback3);
        result.setFeedbacks(feedbackList);

        result.evaluateFeedback(7);

        assertThat(result.getScore()).isEqualTo(0);
        assertThat(result.getResultString()).isEqualToIgnoringCase("0 of 7 points");
    }
}
