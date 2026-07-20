package de.tum.cit.aet.artemis.hyperion.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.stream.Collectors;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;

import de.tum.cit.aet.artemis.account.test_repository.UserTestRepository;
import de.tum.cit.aet.artemis.admin.domain.LLMServiceType;
import de.tum.cit.aet.artemis.admin.service.LLMTokenUsageService;
import de.tum.cit.aet.artemis.core.exception.InternalServerErrorAlertException;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.hyperion.dto.AssessmentCriteriaExerciseType;
import de.tum.cit.aet.artemis.hyperion.dto.AssessmentCriteriaGenerationRequestDTO;
import de.tum.cit.aet.artemis.hyperion.dto.AssessmentCriteriaModelingContextDTO;

class HyperionAssessmentCriteriaGenerationServiceTest {

    private static final String VALID_RESPONSE = """
            {
              "criteria": [
                {
                  "title": "Correctness",
                  "structuredGradingInstructions": [
                    {
                      "credits": 2.0,
                      "gradingScale": "Full credit",
                      "instructionDescription": "The answer is correct and complete.",
                      "feedback": "Your answer is correct and complete.",
                      "usageCount": 1
                    },
                    {
                      "credits": -1.0,
                      "gradingScale": "Major error",
                      "instructionDescription": "The central concept is incorrect.",
                      "feedback": "Review the central concept.",
                      "usageCount": 1
                    }
                  ]
                }
              ]
            }
            """;

    @Mock
    private ChatModel chatModel;

    @Mock
    private LLMTokenUsageService llmTokenUsageService;

    @Mock
    private UserTestRepository userRepository;

    private HyperionAssessmentCriteriaGenerationService service;

    private AutoCloseable mocks;

    @BeforeEach
    void setup() {
        mocks = MockitoAnnotations.openMocks(this);
        lenient().when(chatModel.getOptions()).thenReturn(ChatOptions.builder().build());
        service = new HyperionAssessmentCriteriaGenerationService(ChatClient.create(chatModel), new HyperionPromptTemplateService(), llmTokenUsageService, userRepository);
    }

    @AfterEach
    void tearDown() throws Exception {
        mocks.close();
    }

    @Test
    void generateAssessmentCriteriaMapsOrderedResponseAndTracksSingleCall() {
        mockResponse(VALID_RESPONSE);
        Course course = course(42L);

        var response = service.generateAssessmentCriteria(course, textRequest());

        assertThat(response.criteria()).hasSize(1);
        assertThat(response.criteria().getFirst().title()).isEqualTo("Correctness");
        assertThat(response.criteria().getFirst().structuredGradingInstructions()).extracting("credits").containsExactly(2.0, -1.0);
        verify(chatModel, times(1)).call(any(Prompt.class));
        verify(llmTokenUsageService).trackChatResponseTokenUsage(any(), eq(LLMServiceType.HYPERION), eq(HyperionAssessmentCriteriaGenerationService.GENERATION_PIPELINE_ID), any());
    }

    @Test
    void generateAssessmentCriteriaIncludesModelingContextAndScoringRulesButNoExistingCriteria() {
        mockResponse(VALID_RESPONSE);
        Course course = course(23L);
        var request = new AssessmentCriteriaGenerationRequestDTO(AssessmentCriteriaExerciseType.MODELING, "Draw a class diagram", 10.0, 2.0, "Reward clear names",
                new AssessmentCriteriaModelingContextDTO("ClassDiagram", "{\"nodes\":[{\"id\":\"unsaved-node\"}]}"));

        service.generateAssessmentCriteria(course, request);

        ArgumentCaptor<Prompt> promptCaptor = ArgumentCaptor.forClass(Prompt.class);
        verify(chatModel).call(promptCaptor.capture());
        String promptText = promptCaptor.getValue().getInstructions().stream().map(message -> message.getText()).collect(Collectors.joining("\n"));
        assertThat(promptText).contains("same language as the problem statement", "intended maximum normal score", "Bonus points: 2.0", "ClassDiagram", "unsaved-node",
                "Reward clear names");
        assertThat(promptText).doesNotContain("existing criteria", "Current structured criteria");
    }

    @Test
    void generateAssessmentCriteriaRejectsEmptyAndMalformedResponses() {
        Course course = course(1L);
        mockResponse("{\"criteria\":[]}");
        assertThatThrownBy(() -> service.generateAssessmentCriteria(course, textRequest())).isInstanceOf(InternalServerErrorAlertException.class).hasMessageContaining("empty");

        mockResponse("not-json");
        assertThatThrownBy(() -> service.generateAssessmentCriteria(course, textRequest())).isInstanceOf(InternalServerErrorAlertException.class).hasMessageContaining("malformed");
    }

    @Test
    void generateAssessmentCriteriaRejectsInvalidGeneratedFields() {
        String invalidResponse = """
                {
                  "criteria": [{
                    "title": " ",
                    "structuredGradingInstructions": [{
                      "credits": 1,
                      "gradingScale": "Full",
                      "instructionDescription": "Description",
                      "feedback": "Feedback",
                      "usageCount": 1
                    }]
                  }]
                }
                """;
        mockResponse(invalidResponse);

        assertThatThrownBy(() -> service.generateAssessmentCriteria(course(1L), textRequest())).isInstanceOf(InternalServerErrorAlertException.class)
                .hasMessageContaining("invalid");
    }

    @Test
    void generateAssessmentCriteriaRejectsGeneratedIds() {
        mockResponse(VALID_RESPONSE.replace("\"title\": \"Correctness\"", "\"id\": 123, \"title\": \"Correctness\""));

        assertThatThrownBy(() -> service.generateAssessmentCriteria(course(1L), textRequest())).isInstanceOf(InternalServerErrorAlertException.class);
    }

    @Test
    void generateAssessmentCriteriaRejectsMissingChatClient() {
        var unconfiguredService = new HyperionAssessmentCriteriaGenerationService(null, new HyperionPromptTemplateService(), llmTokenUsageService, userRepository);

        assertThatThrownBy(() -> unconfiguredService.generateAssessmentCriteria(course(1L), textRequest())).isInstanceOf(InternalServerErrorAlertException.class)
                .hasMessageContaining("not configured");
    }

    private void mockResponse(String json) {
        when(chatModel.call(any(Prompt.class))).thenAnswer(_ -> new ChatResponse(List.of(new Generation(new AssistantMessage(json)))));
    }

    private AssessmentCriteriaGenerationRequestDTO textRequest() {
        return new AssessmentCriteriaGenerationRequestDTO(AssessmentCriteriaExerciseType.TEXT, "Explain idempotency.", 5.0, 0.0, "Be precise", null);
    }

    private Course course(long id) {
        Course course = new Course();
        course.setId(id);
        return course;
    }
}
