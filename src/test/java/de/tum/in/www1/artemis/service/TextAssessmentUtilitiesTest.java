package de.tum.in.www1.artemis.service;

import static org.assertj.core.api.Assertions.*;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

import java.util.*;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import de.tum.in.www1.artemis.domain.*;

@ExtendWith(SpringExtension.class)
@SpringBootTest
@AutoConfigureMockMvc
@AutoConfigureTestDatabase
@ActiveProfiles("artemis,automaticText")
public class TextAssessmentUtilitiesTest {

    @Autowired
    TextAssessmentUtilityService textAssessmentUtilityService;

    @MockBean
    private static FeedbackService feedbackService;

    private static TextCluster textCluster;

    private static Map<String, Feedback> mockFeedback = new HashMap<>();

    @BeforeAll
    public static void init() {
        initializeTextClusters(10);
        // Generate 5 feedback items with a score of 2.0
        for (int i = 0; i < 5; i++) {
            Feedback feedback = new Feedback();
            feedback.credits(2.0);
            feedback.setId((long) i);
            mockFeedback.put("" + i, feedback);
        }
        // Generate 3 feedback items with a score of 1.0
        for (int i = 5; i < 8; i++) {
            Feedback feedback = new Feedback();
            feedback.credits(1.0);
            feedback.setId((long) i);
            mockFeedback.put("" + i, feedback);
        }
        // Generate 2 feedback items with a score of 0.0
        for (int i = 8; i < 10; i++) {
            Feedback feedback = new Feedback();
            feedback.credits(0.0);
            feedback.setId((long) i);
            mockFeedback.put("" + i, feedback);
        }
    }

    @Test
    public void getCreditsOfTextBlock() {
        TextBlock textBlock = new TextBlock();
        textBlock.setCluster(textCluster);
        textBlock.setId("1");
        Mockito.when(feedbackService.getFeedbackForTextExerciseInCluster(textCluster)).thenReturn(mockFeedback);
        assertThat(textAssessmentUtilityService.getCreditsOfTextBlock(textBlock), is(OptionalDouble.of(mockFeedback.get("1").getCredits())));
    }

    @Test
    public void validateClusterScores() {
        Mockito.when(feedbackService.getFeedbackForTextExerciseInCluster(textCluster)).thenReturn(mockFeedback);
        assertThat(textAssessmentUtilityService.getMaxScore(textCluster), is(OptionalDouble.of(2.0)));
        assertThat(textAssessmentUtilityService.getMinimumScore(textCluster), is(OptionalDouble.of(0.0)));
        assertThat(textAssessmentUtilityService.getMedianScore(textCluster), is(OptionalDouble.of(1.0)));
        assertThat(textAssessmentUtilityService.calculateAverage(textCluster), is(OptionalDouble.of(1.3)));
    }

    @Test
    public void validateCoveragePercentage() {
        Mockito.when(feedbackService.getFeedbackForTextExerciseInCluster(textCluster)).thenReturn(mockFeedback);
        TextBlock textBlock = new TextBlock();
        textBlock.setCluster(textCluster);
        textBlock.setId("1");
        assertThat(textAssessmentUtilityService.calculateScoreCoveragePercentage(textBlock), is(OptionalDouble.of(0.5)));
        assertThat(textAssessmentUtilityService.calculateCoveragePercentage(textCluster), is(OptionalDouble.of(1.0)));
    }

    private static ArrayList<TextBlock> generateTextBlocks(int count) {
        ArrayList<TextBlock> textBlocks = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            TextBlock textBlock = new TextBlock();
            textBlock.setText("TextBlock" + i);
            textBlock.setId("" + i);
            textBlocks.add(textBlock);
        }
        return textBlocks;
    }

    public static void initializeTextClusters(int count) {
        ArrayList<TextBlock> textBlocks = generateTextBlocks(count);
        textCluster = new TextCluster();

        // Set the relation between a textcluster and its text blocks bidirectional
        textCluster.setBlocks(textBlocks);
        textCluster.getBlocks().stream().forEach(block -> block.setCluster(textCluster));
    }

}
