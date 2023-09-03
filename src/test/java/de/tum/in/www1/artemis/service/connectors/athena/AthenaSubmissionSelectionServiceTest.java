package de.tum.in.www1.artemis.service.connectors.athena;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import de.tum.in.www1.artemis.AbstractAthenaTest;
import de.tum.in.www1.artemis.domain.TextExercise;
import de.tum.in.www1.artemis.domain.TextSubmission;
import de.tum.in.www1.artemis.domain.enumeration.AssessmentType;
import de.tum.in.www1.artemis.exercise.textexercise.TextExerciseUtilService;

class AthenaSubmissionSelectionServiceTest extends AbstractAthenaTest {

    @Autowired
    private AthenaSubmissionSelectionService athenaSubmissionSelectionService;

    @Autowired
    private TextExerciseUtilService textExerciseUtilService;

    private TextExercise textExercise;

    private TextSubmission textSubmission1;

    private TextSubmission textSubmission2;

    @BeforeEach
    void setUp() {
        athenaRequestMockProvider.enableMockingOfRequests();

        textExercise = textExerciseUtilService.createSampleTextExercise(null);
        textSubmission1 = new TextSubmission(1L);
        textSubmission2 = new TextSubmission(2L);
    }

    @Test
    void testSubmissionSelectionFromEmpty() {
        athenaRequestMockProvider.ensureNoRequest();
        var submission = athenaSubmissionSelectionService.getProposedSubmissionId(textExercise, List.of());
        assertThat(submission).isEmpty();
    }

    @Test
    void testSubmissionSelectionFromOne() {
        athenaRequestMockProvider.mockSelectSubmissionsAndExpect(1, jsonPath("$.exercise.id").value(textExercise.getId()), jsonPath("$.submissionIds").isArray());
        var submission = athenaSubmissionSelectionService.getProposedSubmissionId(textExercise, List.of(textSubmission1.getId()));
        assertThat(submission).contains(textSubmission1.getId());
    }

    @Test
    void testNoSubmissionSelectionFromOne() {
        athenaRequestMockProvider.mockSelectSubmissionsAndExpect(-1, jsonPath("$.exercise.id").value(textExercise.getId()), jsonPath("$.submissionIds").isArray());
        var submission = athenaSubmissionSelectionService.getProposedSubmissionId(textExercise, List.of(textSubmission1.getId()));
        assertThat(submission).isEmpty();
    }

    @Test
    void testSubmissionSelectionFromTwo() {
        athenaRequestMockProvider.mockSelectSubmissionsAndExpect(1, jsonPath("$.exercise.id").value(textExercise.getId()), jsonPath("$.submissionIds").isArray());
        var submission = athenaSubmissionSelectionService.getProposedSubmissionId(textExercise, List.of(textSubmission1.getId(), textSubmission2.getId()));
        assertThat(submission).contains(textSubmission1.getId());
    }

    @Test
    void testSubmissionSelectionWithFeedbackSuggestionsDisabled() {
        textExercise.setAssessmentType(AssessmentType.MANUAL); // disable feedback suggestions
        assertThatThrownBy(() -> athenaSubmissionSelectionService.getProposedSubmissionId(textExercise, List.of(textSubmission1.getId())))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testSubmissionSelectionWithException() {
        athenaRequestMockProvider.mockGetSelectedSubmissionAndExpectNetworkingException();
        assertThatNoException().isThrownBy(() -> athenaSubmissionSelectionService.getProposedSubmissionId(textExercise, List.of(textSubmission1.getId())));
    }
}
