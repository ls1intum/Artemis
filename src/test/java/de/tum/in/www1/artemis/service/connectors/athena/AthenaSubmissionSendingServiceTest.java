package de.tum.in.www1.artemis.service.connectors.athena;

import static org.junit.Assert.assertThrows;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;

import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.springframework.test.util.ReflectionTestUtils;

import de.tum.in.www1.artemis.domain.TextExercise;
import de.tum.in.www1.artemis.domain.TextSubmission;
import de.tum.in.www1.artemis.domain.enumeration.AssessmentType;
import de.tum.in.www1.artemis.repository.TextSubmissionRepository;

class AthenaSubmissionSendingServiceTest extends AthenaTest {

    @Mock
    private TextSubmissionRepository textSubmissionRepository;

    private AthenaSubmissionSendingService athenaSubmissionSendingService;

    private TextExercise textExercise;

    private TextSubmission textSubmission;

    @BeforeEach
    void setUp() {
        athenaSubmissionSendingService = new AthenaSubmissionSendingService(textSubmissionRepository, athenaRequestMockProvider.getRestTemplate());
        ReflectionTestUtils.setField(athenaSubmissionSendingService, "athenaUrl", athenaUrl);

        athenaRequestMockProvider.enableMockingOfRequests();

        textExercise = createTextExercise();
        textSubmission = new TextSubmission(2L);

        when(textSubmissionRepository.getTextSubmissionsWithTextBlocksByExerciseId(textExercise.getId())).thenReturn(Set.of(textSubmission));
    }

    @Test
    void testSendSubmissionsSuccess() {
        athenaRequestMockProvider.mockSendSubmissionsAndExpect(jsonPath("$.exercise.id").value(textExercise.getId()), jsonPath("$.exercise.title").value(textExercise.getTitle()),
                jsonPath("$.exercise.maxPoints").value(textExercise.getMaxPoints()), jsonPath("$.exercise.bonusPoints").value(textExercise.getBonusPoints()),
                jsonPath("$.exercise.gradingInstructions").value(textExercise.getGradingInstructions()),
                jsonPath("$.exercise.problemStatement").value(textExercise.getProblemStatement()), jsonPath("$.submissions[0].id").value(textSubmission.getId()),
                jsonPath("$.submissions[0].exerciseId").value(textExercise.getId()), jsonPath("$.submissions[0].text").value(textSubmission.getText()),
                jsonPath("$.submissions[0].language").value(textSubmission.getLanguage()));
        athenaSubmissionSendingService.sendSubmissions(textExercise);
    }

    @Test
    void testSendNoSubmissions() {
        athenaRequestMockProvider.ensureNoRequest();
        when(textSubmissionRepository.getTextSubmissionsWithTextBlocksByExerciseId(textExercise.getId())).thenReturn(Set.of());
        athenaSubmissionSendingService.sendSubmissions(textExercise);
    }

    @Test
    void testSendSubmissionsWithFeedbackSuggestionsDisabled() {
        textExercise.setAssessmentType(AssessmentType.MANUAL); // disable feedback suggestions
        assertThrows(IllegalArgumentException.class, () -> athenaSubmissionSendingService.sendSubmissions(textExercise));
    }
}
