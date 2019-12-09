package de.tum.in.www1.artemis.service;

import static org.assertj.core.api.Assertions.*;

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

    private static Map<String, Feedback> feedback;

    @BeforeAll
    public static void init() {
        Map<String, Feedback> mockFeedback = new HashMap<>();
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
        feedback = mockFeedback;
    }

    @Test
    public void getCreditsOfTextBlock() {
        TextBlock textBlock = new TextBlock();
        textBlock.setCluster(textCluster);
        textBlock.setId("1");
        Mockito.when(feedbackService.getFeedbackForTextExerciseInCluster(textCluster)).thenReturn(feedback);
        assertThat(textAssessmentUtilityService.getCreditsOfTextBlock(textBlock).equals(OptionalDouble.of(feedback.get("1").getCredits())));
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
