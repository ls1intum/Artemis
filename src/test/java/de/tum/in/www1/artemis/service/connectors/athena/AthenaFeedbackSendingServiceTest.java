package de.tum.in.www1.artemis.service.connectors.athena;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.util.ReflectionTestUtils;

import de.tum.in.www1.artemis.AbstractAthenaTest;
import de.tum.in.www1.artemis.domain.Feedback;
import de.tum.in.www1.artemis.domain.TextBlock;
import de.tum.in.www1.artemis.domain.TextExercise;
import de.tum.in.www1.artemis.domain.TextSubmission;
import de.tum.in.www1.artemis.domain.enumeration.AssessmentType;
import de.tum.in.www1.artemis.domain.enumeration.FeedbackType;
import de.tum.in.www1.artemis.exercise.textexercise.TextExerciseUtilService;
import de.tum.in.www1.artemis.repository.TextBlockRepository;

class AthenaFeedbackSendingServiceTest extends AbstractAthenaTest {

    @Mock
    private TextBlockRepository textBlockRepository;

    @Autowired
    private TextExerciseUtilService textExerciseUtilService;

    private AthenaFeedbackSendingService athenaFeedbackSendingService;

    private TextExercise textExercise;

    private TextSubmission textSubmission;

    private Feedback feedback;

    private TextBlock textBlock;

    @BeforeEach
    void setUp() {
        athenaFeedbackSendingService = new AthenaFeedbackSendingService(athenaRequestMockProvider.getRestTemplate(), textBlockRepository);
        ReflectionTestUtils.setField(athenaFeedbackSendingService, "athenaUrl", athenaUrl);

        athenaRequestMockProvider.enableMockingOfRequests();

        textExercise = textExerciseUtilService.createSampleTextExercise(null);

        textSubmission = new TextSubmission(2L).text("Test - This is what the feedback references - Submission");

        textBlock = new TextBlock().startIndex(7).endIndex(46).text("This is what the feedback references").submission(textSubmission);
        textBlock.computeId();

        feedback = new Feedback().type(FeedbackType.MANUAL).credits(5.0).reference(textBlock.getId());
        feedback.setId(3L);

        when(textBlockRepository.findById(textBlock.getId())).thenReturn(Optional.of(textBlock));
    }

    @Test
    void testFeedbackSending() {
        athenaRequestMockProvider.mockSendFeedbackAndExpect(jsonPath("$.exercise.id").value(textExercise.getId()), jsonPath("$.submission.id").value(textSubmission.getId()),
                jsonPath("$.submission.exerciseId").value(textExercise.getId()), jsonPath("$.feedbacks[0].id").value(feedback.getId()),
                jsonPath("$.feedbacks[0].exerciseId").value(textExercise.getId()), jsonPath("$.feedbacks[0].title").value(feedback.getText()),
                jsonPath("$.feedbacks[0].description").value(feedback.getDetailText()), jsonPath("$.feedbacks[0].credits").value(feedback.getCredits()),
                jsonPath("$.feedbacks[0].credits").value(feedback.getCredits()), jsonPath("$.feedbacks[0].indexStart").value(textBlock.getStartIndex()),
                jsonPath("$.feedbacks[0].indexEnd").value(textBlock.getEndIndex()));

        athenaFeedbackSendingService.sendFeedback(textExercise, textSubmission, List.of(feedback));
    }

    @Test
    void testEmptyFeedbackNotSending() {
        athenaRequestMockProvider.ensureNoRequest();
        athenaFeedbackSendingService.sendFeedback(textExercise, textSubmission, List.of());
    }

    @Test
    void testSendFeedbackWithFeedbackSuggestionsDisabled() {
        textExercise.setAssessmentType(AssessmentType.MANUAL); // disable feedback suggestions
        assertThatThrownBy(() -> athenaFeedbackSendingService.sendFeedback(textExercise, textSubmission, List.of(feedback))).isInstanceOf(IllegalArgumentException.class);
    }
}
