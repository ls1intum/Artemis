package de.tum.in.www1.artemis.service.connectors.athena;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertThrows;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import de.tum.in.www1.artemis.AbstractSpringIntegrationBambooBitbucketJiraTest;
import de.tum.in.www1.artemis.connector.AthenaRequestMockProvider;
import de.tum.in.www1.artemis.domain.TextExercise;
import de.tum.in.www1.artemis.domain.TextSubmission;
import de.tum.in.www1.artemis.domain.enumeration.AssessmentType;

class AthenaSubmissionSelectionServiceTest extends AbstractSpringIntegrationBambooBitbucketJiraTest {

    @Autowired
    private AthenaRequestMockProvider athenaRequestMockProvider;

    @Autowired
    private AthenaSubmissionSelectionService athenaSubmissionSelectionService;

    private TextExercise textExercise;

    private TextSubmission textSubmission1;

    private TextSubmission textSubmission2;

    @BeforeEach
    void setUp() {
        athenaRequestMockProvider.enableMockingOfRequests();

        textExercise = new TextExercise();
        textExercise.setAssessmentType(AssessmentType.SEMI_AUTOMATIC); // needed for feedback suggestions
        textExercise.setId(1L);
        textExercise.setTitle("Test Exercise");
        textExercise.setMaxPoints(10.0);

        textSubmission1 = new TextSubmission();
        textSubmission1.setId(1L);

        textSubmission2 = new TextSubmission();
        textSubmission2.setId(2L);
    }

    @AfterEach
    void tearDown() throws Exception {
        athenaRequestMockProvider.reset();
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
        var submission = athenaSubmissionSelectionService.getProposedSubmission(textExercise, List.of(textSubmission1));
        assertThat(submission).contains(textSubmission1);
    }

    @Test
    void testNoSubmissionSelectionFromOne() {
        athenaRequestMockProvider.mockSelectSubmissionsAndExpect(-1, jsonPath("$.exercise.id").value(textExercise.getId()), jsonPath("$.submissionIds").isArray());
        var submission = athenaSubmissionSelectionService.getProposedSubmission(textExercise, List.of(textSubmission1));
        assertThat(submission).isEmpty();
    }

    @Test
    void testSubmissionSelectionFromTwo() {
        athenaRequestMockProvider.mockSelectSubmissionsAndExpect(1, jsonPath("$.exercise.id").value(textExercise.getId()), jsonPath("$.submissionIds").isArray());
        var submission = athenaSubmissionSelectionService.getProposedSubmission(textExercise, List.of(textSubmission1, textSubmission2));
        assertThat(submission).contains(textSubmission1);
    }

    @Test
    void testSubmissionSelectionWithFeedbackSuggestionsDisabled() {
        textExercise.setAssessmentType(AssessmentType.MANUAL); // disable feedback suggestions
        assertThrows(IllegalArgumentException.class, () -> athenaSubmissionSelectionService.getProposedSubmission(textExercise, List.of(textSubmission1)));
    }
}
