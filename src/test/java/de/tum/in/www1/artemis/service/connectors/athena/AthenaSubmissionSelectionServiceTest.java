package de.tum.in.www1.artemis.service.connectors.athena;

import static de.tum.in.www1.artemis.connector.AthenaRequestMockProvider.ATHENA_MODULE_PROGRAMMING_TEST;
import static de.tum.in.www1.artemis.connector.AthenaRequestMockProvider.ATHENA_MODULE_TEXT_TEST;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.in.www1.artemis.AbstractAthenaTest;
import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.exercise.programmingexercise.ProgrammingExerciseUtilService;
import de.tum.in.www1.artemis.exercise.textexercise.TextExerciseUtilService;
import de.tum.in.www1.artemis.repository.ProgrammingExerciseRepository;
import de.tum.in.www1.artemis.repository.TextExerciseRepository;

class AthenaSubmissionSelectionServiceTest extends AbstractAthenaTest {

    private static final String TEST_PREFIX = "athenasubmissionselectionservicetest";

    @Autowired
    private AthenaSubmissionSelectionService athenaSubmissionSelectionService;

    @Autowired
    private TextExerciseUtilService textExerciseUtilService;

    @Autowired
    private TextExerciseRepository textExerciseRepository;

    @Autowired
    private ProgrammingExerciseUtilService programmingExerciseUtilService;

    @Autowired
    private ProgrammingExerciseRepository programmingExerciseRepository;

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
        textExercise.setFeedbackSuggestionModule(ATHENA_MODULE_TEXT_TEST);
        textExercise.setGradingCriteria(Set.of(new GradingCriterion()));
        textExerciseRepository.save(textExercise);
        textSubmission1 = new TextSubmission(1L);
        textSubmission2 = new TextSubmission(2L);

        programmingExercise = programmingExerciseUtilService.createSampleProgrammingExercise();
        programmingExercise.setFeedbackSuggestionModule(ATHENA_MODULE_PROGRAMMING_TEST);
        programmingExercise.setGradingCriteria(Set.of(new GradingCriterion()));
        programmingExerciseRepository.save(programmingExercise);
        programmingSubmission1 = new ProgrammingSubmission();
        programmingSubmission1.setId(3L);
        programmingSubmission2 = new ProgrammingSubmission();
        programmingSubmission2.setId(4L);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void testTextSubmissionSelectionFromEmpty() {
        var submission = athenaSubmissionSelectionService.getProposedSubmissionId(textExercise, List.of());
        assertThat(submission).isEmpty();
        athenaRequestMockProvider.verify(); // Ensure that there was no request
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void testProgrammingSubmissionSelectionFromEmpty() {
        var submission = athenaSubmissionSelectionService.getProposedSubmissionId(programmingExercise, List.of());
        assertThat(submission).isEmpty();
        athenaRequestMockProvider.verify(); // Ensure that there was no request
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void testSubmissionSelectionFromOneText() {
        athenaRequestMockProvider.mockSelectSubmissionsAndExpect("text", 1, jsonPath("$.exercise.id").value(textExercise.getId()), jsonPath("$.submissionIds").isArray());
        var submission = athenaSubmissionSelectionService.getProposedSubmissionId(textExercise, List.of(textSubmission1.getId()));
        assertThat(submission).contains(textSubmission1.getId());
        athenaRequestMockProvider.verify();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void testSubmissionSelectionFromOneProgramming() {
        athenaRequestMockProvider.mockSelectSubmissionsAndExpect("programming", 3, jsonPath("$.exercise.id").value(programmingExercise.getId()),
                jsonPath("$.submissionIds").isArray());
        var submission = athenaSubmissionSelectionService.getProposedSubmissionId(programmingExercise, List.of(programmingSubmission1.getId()));
        assertThat(submission).contains(programmingSubmission1.getId());
        athenaRequestMockProvider.verify();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void testTextNoSubmissionSelectionFromOne() {
        athenaRequestMockProvider.mockSelectSubmissionsAndExpect("text", -1, jsonPath("$.exercise.id").value(textExercise.getId()), jsonPath("$.submissionIds").isArray());
        var submission = athenaSubmissionSelectionService.getProposedSubmissionId(textExercise, List.of(textSubmission1.getId()));
        assertThat(submission).isEmpty();
        athenaRequestMockProvider.verify();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void testProgrammingNoSubmissionSelectionFromOne() {
        athenaRequestMockProvider.mockSelectSubmissionsAndExpect("programming", -1, jsonPath("$.exercise.id").value(programmingExercise.getId()),
                jsonPath("$.submissionIds").isArray());
        var submission = athenaSubmissionSelectionService.getProposedSubmissionId(programmingExercise, List.of(programmingSubmission1.getId()));
        assertThat(submission).isEmpty();
        athenaRequestMockProvider.verify();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void testSubmissionSelectionFromTwoText() {
        athenaRequestMockProvider.mockSelectSubmissionsAndExpect("text", 1, jsonPath("$.exercise.id").value(textExercise.getId()), jsonPath("$.submissionIds").isArray());
        var submission = athenaSubmissionSelectionService.getProposedSubmissionId(textExercise, List.of(textSubmission1.getId(), textSubmission2.getId()));
        assertThat(submission).contains(textSubmission1.getId());
        athenaRequestMockProvider.verify();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void testSubmissionSelectionFromTwoProgramming() {
        athenaRequestMockProvider.mockSelectSubmissionsAndExpect("programming", 4, jsonPath("$.exercise.id").value(programmingExercise.getId()),
                jsonPath("$.submissionIds").isArray());
        var submission = athenaSubmissionSelectionService.getProposedSubmissionId(programmingExercise, List.of(programmingSubmission1.getId(), programmingSubmission2.getId()));
        assertThat(submission).contains(programmingSubmission2.getId());
        athenaRequestMockProvider.verify();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void testTextSubmissionSelectionWithFeedbackSuggestionsDisabled() {
        textExercise.setFeedbackSuggestionModule(null);
        assertThatThrownBy(() -> athenaSubmissionSelectionService.getProposedSubmissionId(textExercise, List.of(textSubmission1.getId())))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void testProgrammingSubmissionSelectionWithFeedbackSuggestionsDisabled() {
        programmingExercise.setFeedbackSuggestionModule(null);
        assertThatThrownBy(() -> athenaSubmissionSelectionService.getProposedSubmissionId(programmingExercise, List.of(programmingSubmission1.getId())))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void testTextSubmissionSelectionWithException() {
        athenaRequestMockProvider.mockGetSelectedSubmissionAndExpectNetworkingException("text");
        assertThatNoException().isThrownBy(() -> athenaSubmissionSelectionService.getProposedSubmissionId(textExercise, List.of(textSubmission1.getId())));
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void testProgrammingSubmissionSelectionWithException() {
        athenaRequestMockProvider.mockGetSelectedSubmissionAndExpectNetworkingException("programming");
        assertThatNoException().isThrownBy(() -> athenaSubmissionSelectionService.getProposedSubmissionId(programmingExercise, List.of(programmingSubmission1.getId())));
    }
}
