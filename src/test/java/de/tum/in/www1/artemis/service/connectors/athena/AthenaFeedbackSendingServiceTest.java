package de.tum.in.www1.artemis.service.connectors.athena;

import static org.junit.Assert.assertThrows;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.test.util.ReflectionTestUtils;

import de.tum.in.www1.artemis.AbstractSpringIntegrationBambooBitbucketJiraTest;
import de.tum.in.www1.artemis.connector.AthenaRequestMockProvider;
import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.enumeration.AssessmentType;
import de.tum.in.www1.artemis.domain.enumeration.FeedbackType;
import de.tum.in.www1.artemis.repository.TextBlockRepository;

class AthenaFeedbackSendingServiceTest extends AbstractSpringIntegrationBambooBitbucketJiraTest {

    @Value("${artemis.athena.url}")
    private String athenaUrl;

    @Autowired
    private AthenaRequestMockProvider athenaRequestMockProvider;

    @Mock
    private TextBlockRepository textBlockRepository;

    private AthenaFeedbackSendingService athenaFeedbackSendingService;

    private TextExercise textExercise;

    private TextSubmission textSubmission;

    private Feedback feedback;

    private TextBlock textBlock;

    @BeforeEach
    void setUp() {
        athenaFeedbackSendingService = new AthenaFeedbackSendingService(textBlockRepository, athenaRequestMockProvider.getRestTemplate());
        ReflectionTestUtils.setField(athenaFeedbackSendingService, "athenaUrl", athenaUrl);

        athenaRequestMockProvider.enableMockingOfRequests();

        textExercise = new TextExercise();
        textExercise.setAssessmentType(AssessmentType.SEMI_AUTOMATIC); // needed for feedback suggestions
        textExercise.setId(1L);
        textExercise.setTitle("Test Exercise");
        textExercise.setMaxPoints(10.0);

        textSubmission = new TextSubmission();
        textSubmission.setId(2L);
        textSubmission.setText("Test - This is what the feedback references - Submission");

        textBlock = new TextBlock();
        textBlock.setId("4");
        textBlock.setStartIndex(7);
        textBlock.setEndIndex(46);
        textBlock.setText("This is what the feedback references");
        textBlock.setSubmission(textSubmission);

        feedback = new Feedback();
        feedback.setId(3L);
        feedback.setType(FeedbackType.MANUAL);
        feedback.setCredits(5.0);
        feedback.setReference(textBlock.getId());

        when(textBlockRepository.findById(textBlock.getId())).thenReturn(Optional.of(textBlock));
    }

    @AfterEach
    void tearDown() throws Exception {
        athenaRequestMockProvider.reset();
    }

    @Test
    void testFeedbackSending() {
        athenaRequestMockProvider.mockSendFeedbackAndExpect(jsonPath("$.exercise.id").value(textExercise.getId()), jsonPath("$.submission.id").value(textSubmission.getId()),
                jsonPath("$.submission.exerciseId").value(textExercise.getId()), jsonPath("$.feedbacks[0].id").value(feedback.getId()),
                jsonPath("$.feedbacks[0].exerciseId").value(textExercise.getId()), jsonPath("$.feedbacks[0].text").value(feedback.getText()),
                jsonPath("$.feedbacks[0].detailText").value(feedback.getDetailText()), jsonPath("$.feedbacks[0].credits").value(feedback.getCredits()),
                jsonPath("$.feedbacks[0].credits").value(feedback.getCredits()), jsonPath("$.feedbacks[0].indexStart").value(textBlock.getStartIndex()),
                jsonPath("$.feedbacks[0].indexEnd").value(textBlock.getEndIndex()));

        athenaFeedbackSendingService.sendFeedback(textExercise, textSubmission, List.of(feedback));
    }

    @Test
    void testSendFeedbackWithFeedbackSuggestionsDisabled() {
        textExercise.setAssessmentType(AssessmentType.MANUAL); // disable feedback suggestions
        assertThrows(IllegalArgumentException.class, () -> athenaFeedbackSendingService.sendFeedback(textExercise, textSubmission, List.of(feedback)));
    }
}
