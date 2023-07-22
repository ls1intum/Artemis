package de.tum.in.www1.artemis.service.connectors.athena;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.springframework.test.util.ReflectionTestUtils;

import de.tum.in.www1.artemis.domain.TextExercise;
import de.tum.in.www1.artemis.domain.TextSubmission;
import de.tum.in.www1.artemis.domain.enumeration.AssessmentType;
import de.tum.in.www1.artemis.repository.SubmissionRepository;

class AthenaSubmissionSelectionServiceTest extends AthenaTest {

    @Mock
    private SubmissionRepository submissionRepository;

    private AthenaSubmissionSelectionService athenaSubmissionSelectionService;

    private TextExercise textExercise;

    private TextSubmission textSubmission1;

    private TextSubmission textSubmission2;

    @BeforeEach
    void setUp() {
        athenaSubmissionSelectionService = new AthenaSubmissionSelectionService(athenaRequestMockProvider.getRestTemplate(), submissionRepository);
        ReflectionTestUtils.setField(athenaSubmissionSelectionService, "athenaUrl", athenaUrl);

        athenaRequestMockProvider.enableMockingOfRequests();

        textExercise = createTextExercise();
        textSubmission1 = new TextSubmission(1L);
        textSubmission2 = new TextSubmission(2L);

        when(submissionRepository.findById(textSubmission1.getId())).thenReturn(Optional.of(textSubmission1));
        when(submissionRepository.findById(textSubmission2.getId())).thenReturn(Optional.of(textSubmission2));
    }

    @Test
    void testSubmissionSelectionFromEmpty() {
        athenaRequestMockProvider.ensureNoRequest();
        var submission = athenaSubmissionSelectionService.getProposedSubmission(textExercise, List.of());
        assertThat(submission).isEmpty();
    }

    @Test
    void testSubmissionSelectionFromOne() {
        athenaRequestMockProvider.mockSelectSubmissionsAndExpect(1, jsonPath("$.exercise.id").value(textExercise.getId()), jsonPath("$.submissionIds").isArray());
        var submission = athenaSubmissionSelectionService.getProposedSubmission(textExercise, List.of(textSubmission1.getId()));
        assertThat(submission).contains(textSubmission1);
    }

    @Test
    void testNoSubmissionSelectionFromOne() {
        athenaRequestMockProvider.mockSelectSubmissionsAndExpect(-1, jsonPath("$.exercise.id").value(textExercise.getId()), jsonPath("$.submissionIds").isArray());
        var submission = athenaSubmissionSelectionService.getProposedSubmission(textExercise, List.of(textSubmission1.getId()));
        assertThat(submission).isEmpty();
    }

    @Test
    void testSubmissionSelectionFromTwo() {
        athenaRequestMockProvider.mockSelectSubmissionsAndExpect(1, jsonPath("$.exercise.id").value(textExercise.getId()), jsonPath("$.submissionIds").isArray());
        var submission = athenaSubmissionSelectionService.getProposedSubmission(textExercise, List.of(textSubmission1.getId(), textSubmission2.getId()));
        assertThat(submission).contains(textSubmission1);
    }

    @Test
    void testSubmissionSelectionWithFeedbackSuggestionsDisabled() {
        textExercise.setAssessmentType(AssessmentType.MANUAL); // disable feedback suggestions
        assertThatThrownBy(() -> athenaSubmissionSelectionService.getProposedSubmission(textExercise, List.of(textSubmission1.getId())))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
