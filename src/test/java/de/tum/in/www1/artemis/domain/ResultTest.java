package de.tum.in.www1.artemis.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.List;

import org.assertj.core.data.Offset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import de.tum.in.www1.artemis.AbstractSpringIntegrationBambooBitbucketJiraTest;
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
        feedbackList = Arrays.asList(feedback1, feedback2, feedback3, feedback4, feedback5);
    }

    @Test
    public void evaluateFeedback() {
        double maxPoints = 7.0;
        result.setFeedbacks(feedbackList);

        double calculatedPoints = resultRepository.calculateTotalPoints(feedbackList);
        double totalPoints = resultRepository.constrainToRange(calculatedPoints, maxPoints);
        result.setScore(totalPoints, maxPoints);
        result.setResultString(totalPoints, maxPoints);

        assertThat(result.getScore()).isEqualTo(5.0 / maxPoints * 100, Offset.offset(offsetByTenThousandth));
        assertThat(result.getResultString()).isEqualToIgnoringCase("5 of 7 points");
    }

    @Test
    public void evaluateFeedback_totalScoreGreaterMaxScore() {
        result.setFeedbacks(feedbackList);

        double calculatePoints = resultRepository.calculateTotalPoints(feedbackList);
        double totalPoints = resultRepository.constrainToRange(calculatePoints, 4.0);
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

        double calculatePoints = resultRepository.calculateTotalPoints(feedbackList);
        double totalPoints = resultRepository.constrainToRange(calculatePoints, 7.0);
        result.setScore(totalPoints, 7.0);
        result.setResultString(totalPoints, 7.0);

        assertThat(result.getScore()).isEqualTo(0);
        assertThat(result.getResultString()).isEqualToIgnoringCase("0 of 7 points");
    }
}
