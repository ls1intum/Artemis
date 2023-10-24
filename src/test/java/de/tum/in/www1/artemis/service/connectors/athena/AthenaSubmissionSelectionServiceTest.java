package de.tum.in.www1.artemis.service.connectors.athena;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.in.www1.artemis.AbstractAthenaTest;
import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.exercise.programmingexercise.ProgrammingExerciseUtilService;
import de.tum.in.www1.artemis.exercise.textexercise.TextExerciseUtilService;

class AthenaSubmissionSelectionServiceTest extends AbstractAthenaTest {

    private static final String TEST_PREFIX = "athenasubmissionselectionservicetest";

    @Autowired
    private AthenaSubmissionSelectionService athenaSubmissionSelectionService;

    @Autowired
    private TextExerciseUtilService textExerciseUtilService;

    @Autowired
    private ProgrammingExerciseUtilService programmingExerciseUtilService;

    private TextExercise textExercise;

    private TextSubmission textSubmission1;

    private TextSubmission textSubmission2;

    private ProgrammingExercise programmingExercise;

    private ProgrammingSubmission programmingSubmission1;

    private ProgrammingSubmission programmingSubmission2;

    @BeforeEach
    void setUp() {
        athenaRequestMockProvider.enableMockingOfRequests();

        textExercise = textExerciseUtilService.createSampleTextExercise(null);
        textExercise.setFeedbackSuggestionsEnabled(true);
        textSubmission1 = new TextSubmission(1L);
        textSubmission2 = new TextSubmission(2L);

        programmingExercise = programmingExerciseUtilService.createSampleProgrammingExercise();
        programmingExercise.setFeedbackSuggestionsEnabled(true);
        programmingSubmission1 = new ProgrammingSubmission();
        programmingSubmission1.setId(3L);
        programmingSubmission2 = new ProgrammingSubmission();
        programmingSubmission2.setId(4L);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void testSubmissionSelectionFromEmpty() {
        athenaRequestMockProvider.ensureNoRequest();
        var submission = athenaSubmissionSelectionService.getProposedSubmissionId(textExercise, List.of());
        assertThat(submission).isEmpty();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void testSubmissionSelectionFromOneText() {
        athenaRequestMockProvider.mockSelectSubmissionsAndExpect("text", 1, jsonPath("$.exercise.id").value(textExercise.getId()), jsonPath("$.submissionIds").isArray());
        var submission = athenaSubmissionSelectionService.getProposedSubmissionId(textExercise, List.of(textSubmission1.getId()));
        assertThat(submission).contains(textSubmission1.getId());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void testSubmissionSelectionFromOneProgramming() {
        athenaRequestMockProvider.mockSelectSubmissionsAndExpect("programming", 3, jsonPath("$.exercise.id").value(programmingExercise.getId()),
                jsonPath("$.submissionIds").isArray());
        var submission = athenaSubmissionSelectionService.getProposedSubmissionId(programmingExercise, List.of(programmingSubmission1.getId()));
        assertThat(submission).contains(programmingSubmission1.getId());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void testNoSubmissionSelectionFromOne() {
        athenaRequestMockProvider.mockSelectSubmissionsAndExpect("text", -1, jsonPath("$.exercise.id").value(textExercise.getId()), jsonPath("$.submissionIds").isArray());
        var submission = athenaSubmissionSelectionService.getProposedSubmissionId(textExercise, List.of(textSubmission1.getId()));
        assertThat(submission).isEmpty();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void testSubmissionSelectionFromTwoText() {
        athenaRequestMockProvider.mockSelectSubmissionsAndExpect("text", 1, jsonPath("$.exercise.id").value(textExercise.getId()), jsonPath("$.submissionIds").isArray());
        var submission = athenaSubmissionSelectionService.getProposedSubmissionId(textExercise, List.of(textSubmission1.getId(), textSubmission2.getId()));
        assertThat(submission).contains(textSubmission1.getId());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void testSubmissionSelectionFromTwoProgramming() {
        athenaRequestMockProvider.mockSelectSubmissionsAndExpect("programming", 4, jsonPath("$.exercise.id").value(programmingExercise.getId()),
                jsonPath("$.submissionIds").isArray());
        var submission = athenaSubmissionSelectionService.getProposedSubmissionId(programmingExercise, List.of(programmingSubmission1.getId(), programmingSubmission2.getId()));
        assertThat(submission).contains(programmingSubmission2.getId());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void testSubmissionSelectionWithFeedbackSuggestionsDisabled() {
        textExercise.setFeedbackSuggestionsEnabled(false);
        assertThatThrownBy(() -> athenaSubmissionSelectionService.getProposedSubmissionId(textExercise, List.of(textSubmission1.getId())))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void testSubmissionSelectionWithException() {
        athenaRequestMockProvider.mockGetSelectedSubmissionAndExpectNetworkingException();
        assertThatNoException().isThrownBy(() -> athenaSubmissionSelectionService.getProposedSubmissionId(textExercise, List.of(textSubmission1.getId())));
    }
}
