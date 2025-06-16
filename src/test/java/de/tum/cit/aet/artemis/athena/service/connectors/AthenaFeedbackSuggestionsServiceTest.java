package de.tum.cit.aet.artemis.athena.service.connectors;

import static de.tum.cit.aet.artemis.core.connector.AthenaRequestMockProvider.ATHENA_MODULE_MODELING_PRELIMINARY_TEST;
import static de.tum.cit.aet.artemis.core.connector.AthenaRequestMockProvider.ATHENA_MODULE_MODELING_SUGGESTIONS_TEST;
import static de.tum.cit.aet.artemis.core.connector.AthenaRequestMockProvider.ATHENA_MODULE_PROGRAMMING_PRELIMINARY_TEST;
import static de.tum.cit.aet.artemis.core.connector.AthenaRequestMockProvider.ATHENA_MODULE_PROGRAMMING_SUGGESTIONS_TEST;
import static de.tum.cit.aet.artemis.core.connector.AthenaRequestMockProvider.ATHENA_MODULE_TEXT_PRELIMINARY_TEST;
import static de.tum.cit.aet.artemis.core.connector.AthenaRequestMockProvider.ATHENA_MODULE_TEXT_SUGGESTIONS_TEST;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatExceptionOfType;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.cit.aet.artemis.athena.AbstractAthenaTest;
import de.tum.cit.aet.artemis.athena.dto.ProgrammingFeedbackDTO;
import de.tum.cit.aet.artemis.athena.dto.ResponseMetaDTO;
import de.tum.cit.aet.artemis.athena.dto.TextFeedbackDTO;
import de.tum.cit.aet.artemis.athena.service.AthenaFeedbackSuggestionsService;
import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.domain.LLMRequest;
import de.tum.cit.aet.artemis.core.domain.LLMServiceType;
import de.tum.cit.aet.artemis.core.domain.LLMTokenUsageRequest;
import de.tum.cit.aet.artemis.core.domain.LLMTokenUsageTrace;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.exception.ConflictException;
import de.tum.cit.aet.artemis.core.exception.NetworkingException;
import de.tum.cit.aet.artemis.core.repository.LLMTokenUsageRequestRepository;
import de.tum.cit.aet.artemis.core.repository.LLMTokenUsageTraceRepository;
import de.tum.cit.aet.artemis.core.user.util.UserUtilService;
import de.tum.cit.aet.artemis.exercise.domain.participation.StudentParticipation;
import de.tum.cit.aet.artemis.modeling.domain.ModelingExercise;
import de.tum.cit.aet.artemis.modeling.domain.ModelingSubmission;
import de.tum.cit.aet.artemis.modeling.util.ModelingExerciseUtilService;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingSubmission;
import de.tum.cit.aet.artemis.programming.util.ProgrammingExerciseUtilService;
import de.tum.cit.aet.artemis.text.domain.TextExercise;
import de.tum.cit.aet.artemis.text.domain.TextSubmission;
import de.tum.cit.aet.artemis.text.util.TextExerciseUtilService;

class AthenaFeedbackSuggestionsServiceTest extends AbstractAthenaTest {

    private static final String TEST_PREFIX = "athenafeedbacksuggestionsservicetest";

    @Autowired
    private AthenaFeedbackSuggestionsService athenaFeedbackSuggestionsService;

    @Autowired
    private TextExerciseUtilService textExerciseUtilService;

    @Autowired
    private ProgrammingExerciseUtilService programmingExerciseUtilService;

    @Autowired
    private ModelingExerciseUtilService modelingExerciseUtilService;

    @Autowired
    private LLMTokenUsageTraceRepository llmTokenUsageTraceRepository;

    @Autowired
    private LLMTokenUsageRequestRepository llmTokenUsageRequestRepository;

    @Autowired
    private UserUtilService userUtilService;

    private TextExercise textExercise;

    private TextSubmission textSubmission;

    private ProgrammingExercise programmingExercise;

    private ProgrammingSubmission programmingSubmission;

    private ModelingExercise modelingExercise;

    private ModelingSubmission modelingSubmission;

    @BeforeEach
    void setUp() {
        athenaRequestMockProvider.enableMockingOfRequests();

        userUtilService.addUsers(TEST_PREFIX, 1, 1, 0, 0);

        Course course = courseUtilService.addEmptyCourse();
        textExercise = textExerciseUtilService.createSampleTextExercise(course);
        textExercise.setFeedbackSuggestionModule(ATHENA_MODULE_TEXT_SUGGESTIONS_TEST);
        textExercise.setPreliminaryFeedbackModule(ATHENA_MODULE_TEXT_PRELIMINARY_TEST);
        textSubmission = new TextSubmission(2L).text("This is a text submission");
        User student = userUtilService.getUserByLogin(TEST_PREFIX + "student1");
        StudentParticipation textParticipation = new StudentParticipation().exercise(textExercise);
        textParticipation.setParticipant(student);
        textSubmission.setParticipation(textParticipation);

        programmingExercise = programmingExerciseUtilService.createSampleProgrammingExercise();
        programmingExercise.setFeedbackSuggestionModule(ATHENA_MODULE_PROGRAMMING_SUGGESTIONS_TEST);
        programmingExercise.setPreliminaryFeedbackModule(ATHENA_MODULE_PROGRAMMING_PRELIMINARY_TEST);
        programmingSubmission = new ProgrammingSubmission();
        programmingSubmission.setId(3L);
        programmingSubmission.setParticipation(new StudentParticipation().exercise(programmingExercise));

        var modelingCourse = modelingExerciseUtilService.addCourseWithOneModelingExercise(null);
        modelingExercise = (ModelingExercise) modelingCourse.getExercises().stream().findFirst().orElseThrow();
        modelingExercise.setFeedbackSuggestionModule(ATHENA_MODULE_MODELING_SUGGESTIONS_TEST);
        modelingExercise.setPreliminaryFeedbackModule(ATHENA_MODULE_MODELING_PRELIMINARY_TEST);
        modelingSubmission = new ModelingSubmission();
        modelingSubmission.setId(4L);
        modelingSubmission.setParticipation(new StudentParticipation().exercise(modelingExercise));

        llmTokenUsageRequestRepository.deleteAll();
        llmTokenUsageTraceRepository.deleteAll();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void testFeedbackSuggestionsText() throws NetworkingException {

        var llmRequest = new LLMRequest("gpt-4o", 2000, 5, 1000, 10, "model-text");
        var totalUsage = new ResponseMetaDTO.TotalUsage(100, 200, 300, 0.0005f);
        var meta = new ResponseMetaDTO(totalUsage, List.of(llmRequest));

        athenaRequestMockProvider.mockGetFeedbackSuggestionsAndExpect("text", false, meta, jsonPath("$.exercise.id").value(textExercise.getId()),
                jsonPath("$.exercise.title").value(textExercise.getTitle()), jsonPath("$.submission.id").value(textSubmission.getId()),
                jsonPath("$.submission.text").value(textSubmission.getText()));
        List<TextFeedbackDTO> suggestions = athenaFeedbackSuggestionsService.getTextFeedbackSuggestions(textExercise, textSubmission, false);
        assertThat(suggestions.getFirst().title()).isEqualTo("Not so good");
        assertThat(suggestions.getFirst().indexStart()).isEqualTo(3);
        athenaRequestMockProvider.verify();

        List<LLMTokenUsageTrace> traces = llmTokenUsageTraceRepository.findAll();
        assertThat(traces).hasSize(1).first().satisfies(trace -> {
            assertThat(trace.getServiceType()).isEqualTo(LLMServiceType.ATHENA);
            assertThat(trace.getExerciseId()).isEqualTo(textExercise.getId());
            assertThat(trace.getUserId()).isEqualTo(userUtilService.getUserByLogin(TEST_PREFIX + "student1").getId());
        });

        List<LLMTokenUsageRequest> savedRequests = llmTokenUsageRequestRepository.findAll();
        assertThat(savedRequests).hasSize(1).first()
                .extracting(LLMTokenUsageRequest::getModel, LLMTokenUsageRequest::getNumInputTokens, LLMTokenUsageRequest::getNumOutputTokens,
                        LLMTokenUsageRequest::getServicePipelineId)
                .containsExactly(llmRequest.model(), llmRequest.numInputTokens(), llmRequest.numOutputTokens(), llmRequest.pipelineId());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void testFeedbackSuggestionsProgramming() throws NetworkingException {
        athenaRequestMockProvider.mockGetFeedbackSuggestionsAndExpect("programming", false, null, jsonPath("$.exercise.id").value(programmingExercise.getId()),
                jsonPath("$.exercise.title").value(programmingExercise.getTitle()), jsonPath("$.submission.id").value(programmingSubmission.getId()),
                jsonPath("$.submission.repositoryUri")
                        .value("http://localhost/api/athena/public/programming-exercises/" + programmingExercise.getId() + "/submissions/3/repository"));
        List<ProgrammingFeedbackDTO> suggestions = athenaFeedbackSuggestionsService.getProgrammingFeedbackSuggestions(programmingExercise, programmingSubmission, false);
        assertThat(suggestions.getFirst().title()).isEqualTo("Not so good");
        assertThat(suggestions.getFirst().lineStart()).isEqualTo(3);
        athenaRequestMockProvider.verify();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testPreliminaryFeedbackText() throws NetworkingException {
        athenaRequestMockProvider.mockGetFeedbackSuggestionsAndExpect("text", true, null, jsonPath("$.exercise.id").value(textExercise.getId()),
                jsonPath("$.exercise.title").value(textExercise.getTitle()), jsonPath("$.submission.id").value(textSubmission.getId()),
                jsonPath("$.submission.text").value(textSubmission.getText()));
        List<TextFeedbackDTO> suggestions = athenaFeedbackSuggestionsService.getTextFeedbackSuggestions(textExercise, textSubmission, true);
        assertThat(suggestions.getFirst().title()).isEqualTo("Not so good");
        assertThat(suggestions.getFirst().indexStart()).isEqualTo(3);
        athenaRequestMockProvider.verify();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testPreliminaryFeedbackProgramming() throws NetworkingException {
        athenaRequestMockProvider.mockGetFeedbackSuggestionsAndExpect("programming", true, null, jsonPath("$.exercise.id").value(programmingExercise.getId()),
                jsonPath("$.exercise.title").value(programmingExercise.getTitle()), jsonPath("$.submission.id").value(programmingSubmission.getId()),
                jsonPath("$.submission.repositoryUri")
                        .value("http://localhost/api/athena/public/programming-exercises/" + programmingExercise.getId() + "/submissions/3/repository"));
        List<ProgrammingFeedbackDTO> suggestions = athenaFeedbackSuggestionsService.getProgrammingFeedbackSuggestions(programmingExercise, programmingSubmission, true);
        assertThat(suggestions.getFirst().title()).isEqualTo("Not so good");
        assertThat(suggestions.getFirst().lineStart()).isEqualTo(3);
        athenaRequestMockProvider.verify();
    }

    @Test
    void testFeedbackSuggestionsIdConflict() {
        athenaRequestMockProvider.mockGetFeedbackSuggestionsAndExpect("text", true, null);
        var otherExercise = new TextExercise();
        textSubmission.setParticipation(new StudentParticipation().exercise(otherExercise)); // Add submission to wrong exercise
        assertThatExceptionOfType(ConflictException.class).isThrownBy(() -> athenaFeedbackSuggestionsService.getTextFeedbackSuggestions(textExercise, textSubmission, true));
    }
}
