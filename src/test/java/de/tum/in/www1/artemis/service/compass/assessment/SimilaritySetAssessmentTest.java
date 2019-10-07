package de.tum.in.www1.artemis.service.compass.assessment;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import de.tum.in.www1.artemis.domain.Feedback;

class SimilaritySetAssessmentTest {

    private SimilaritySetAssessment assessment;

    private Feedback initialFeedback;

    @BeforeEach
    void setUp() {
        initialFeedback = new Feedback();
        initialFeedback.setId(1L);
        initialFeedback.setCredits(1.5);
        initialFeedback.setText("very long feedback text");
        assessment = new SimilaritySetAssessment(initialFeedback);
    }

    @Test
    void addFeedback() {
        Feedback feedbackToAdd = new Feedback();
        feedbackToAdd.setId(2L);
        feedbackToAdd.setCredits(0.5);
        feedbackToAdd.setText("long feedback text");
        Feedback feedbackToAdd2 = new Feedback();
        feedbackToAdd2.setId(3L);
        feedbackToAdd2.setCredits(0.5);
        feedbackToAdd2.setText("feedback text");

        assessment.addFeedback(feedbackToAdd);
        assessment.addFeedback(feedbackToAdd2);

        List<Feedback> feedbackList = assessment.getFeedbackList();
        assertThat(feedbackList).containsExactlyInAnyOrder(feedbackToAdd, feedbackToAdd2, initialFeedback);
        Score score = assessment.getScore();
        assertThat(score.getPoints()).isEqualTo(0.5);
        assertThat(score.getComments()).containsExactlyInAnyOrder("long feedback text", "feedback text");
        assertThat(score.getConfidence()).isEqualTo(2 / 3.0);
    }
}
