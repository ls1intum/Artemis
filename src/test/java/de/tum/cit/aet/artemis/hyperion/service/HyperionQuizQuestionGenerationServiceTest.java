package de.tum.cit.aet.artemis.hyperion.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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
import de.tum.cit.aet.artemis.hyperion.dto.GeneratedQuizAnswerOptionDTO;
import de.tum.cit.aet.artemis.hyperion.dto.GeneratedQuizQuestionDTO;
import de.tum.cit.aet.artemis.hyperion.dto.QuizQuestionBulkRefinementRequestDTO;
import de.tum.cit.aet.artemis.hyperion.dto.QuizQuestionBulkRefinementResponseDTO;
import de.tum.cit.aet.artemis.hyperion.dto.QuizQuestionGenerationLanguage;
import de.tum.cit.aet.artemis.hyperion.dto.QuizQuestionGenerationRequestDTO;
import de.tum.cit.aet.artemis.hyperion.dto.QuizQuestionGenerationResponseDTO;
import de.tum.cit.aet.artemis.hyperion.dto.QuizQuestionGenerationType;
import de.tum.cit.aet.artemis.hyperion.dto.QuizQuestionRefinementRequestDTO;
import de.tum.cit.aet.artemis.hyperion.dto.QuizQuestionRefinementResponseDTO;

class HyperionQuizQuestionGenerationServiceTest {

    @Mock
    private ChatModel chatModel;

    @Mock
    private HyperionCompetencyContextService competencyContextService;

    @Mock
    private LLMTokenUsageService llmTokenUsageService;

    @Mock
    private UserTestRepository userRepository;

    private HyperionQuizQuestionGenerationService service;

    private AutoCloseable mocks;

    @BeforeEach
    void setup() {
        mocks = MockitoAnnotations.openMocks(this);
        // ChatClient merges request options into the model's options, which must be non-null
        lenient().when(chatModel.getOptions()).thenReturn(ChatOptions.builder().build());
        ChatClient chatClient = ChatClient.create(chatModel);
        service = new HyperionQuizQuestionGenerationService(chatClient, new HyperionPromptTemplateService(), competencyContextService, llmTokenUsageService, userRepository);
    }

    @AfterEach
    void tearDown() throws Exception {
        mocks.close();
    }

    @Test
    void generateQuizQuestions_returnsGeneratedQuestions() {
        String json = """
                {
                  "questions": [
                    {
                      "type": "single-choice",
                      "title": "HTTP Methods",
                      "questionText": "Which HTTP method is idempotent for updating an existing resource?",
                      "options": [
                        { "text": "PATCH", "correct": false },
                        { "text": "PUT", "correct": true },
                        { "text": "POST", "correct": false }
                      ]
                    }
                  ]
                }
                """;
        when(chatModel.call(any(Prompt.class))).thenAnswer(_ -> new ChatResponse(List.of(new Generation(new AssistantMessage(json)))));

        Course course = new Course();
        course.setTitle("Software Engineering");
        course.setDescription("SE basics");

        QuizQuestionGenerationRequestDTO request = new QuizQuestionGenerationRequestDTO("HTTP", null, "", QuizQuestionGenerationLanguage.EN,
                Set.of(QuizQuestionGenerationType.SINGLE_CHOICE), 1, 50);

        QuizQuestionGenerationResponseDTO response = service.generateQuizQuestions(course, request);

        assertThat(response.questions()).hasSize(1);
        assertThat(response.questions().getFirst().type()).isEqualTo(QuizQuestionGenerationType.SINGLE_CHOICE);
        assertThat(response.questions().getFirst().title()).isEqualTo("HTTP Methods");
        assertThat(response.questions().getFirst().options()).hasSize(3);
    }

    @Test
    void generateQuizQuestions_throwsExceptionWhenChatClientIsNull() {
        var serviceWithNullClient = new HyperionQuizQuestionGenerationService(null, new HyperionPromptTemplateService(), competencyContextService, llmTokenUsageService,
                userRepository);

        Course course = new Course();
        course.setTitle("Software Engineering");
        course.setDescription("SE basics");

        QuizQuestionGenerationRequestDTO request = new QuizQuestionGenerationRequestDTO("HTTP", null, "", QuizQuestionGenerationLanguage.EN,
                Set.of(QuizQuestionGenerationType.SINGLE_CHOICE), 1, 50);

        assertThatThrownBy(() -> serviceWithNullClient.generateQuizQuestions(course, request)).isInstanceOf(InternalServerErrorAlertException.class)
                .hasMessageContaining("AI chat client is not configured");
    }

    @Test
    void refineQuizQuestion_returnsRefinedQuestion() {
        String json = """
                {
                  "question": {
                    "type": "single-choice",
                    "title": "HTTP Idempotency",
                    "questionText": "Which HTTP method is idempotent?",
                    "options": [
                      { "text": "PUT", "correct": true },
                      { "text": "POST", "correct": false }
                    ]
                  },
                  "reasoning": "Changed the wording to be more precise."
                }
                """;
        when(chatModel.call(any(Prompt.class))).thenAnswer(_ -> new ChatResponse(List.of(new Generation(new AssistantMessage(json)))));

        Course course = new Course();
        course.setTitle("Software Engineering");

        GeneratedQuizQuestionDTO originalQuestion = new GeneratedQuizQuestionDTO(QuizQuestionGenerationType.SINGLE_CHOICE, "HTTP Methods", "Which HTTP method updates a resource?",
                List.of(new GeneratedQuizAnswerOptionDTO("PUT", true, null, null), new GeneratedQuizAnswerOptionDTO("POST", false, null, null)), null, null);
        QuizQuestionRefinementRequestDTO request = new QuizQuestionRefinementRequestDTO(originalQuestion, "Make the question more precise");

        QuizQuestionRefinementResponseDTO.QuizQuestionRefinementSuccessDTO response = (QuizQuestionRefinementResponseDTO.QuizQuestionRefinementSuccessDTO) service
                .refineQuizQuestion(course, request);

        assertThat(response.question().title()).isEqualTo("HTTP Idempotency");
        assertThat(response.question().questionText()).isEqualTo("Which HTTP method is idempotent?");
        assertThat(response.question().options()).hasSize(2);
        assertThat(response.reasoning()).isEqualTo("Changed the wording to be more precise.");
    }

    @Test
    void refineQuizQuestion_throwsExceptionWhenChatClientIsNull() {
        var serviceWithNullClient = new HyperionQuizQuestionGenerationService(null, new HyperionPromptTemplateService(), competencyContextService, llmTokenUsageService,
                userRepository);

        Course course = new Course();
        course.setTitle("Software Engineering");

        GeneratedQuizQuestionDTO originalQuestion = new GeneratedQuizQuestionDTO(QuizQuestionGenerationType.SINGLE_CHOICE, "HTTP Methods", "Which method?",
                List.of(new GeneratedQuizAnswerOptionDTO("PUT", true, null, null), new GeneratedQuizAnswerOptionDTO("POST", false, null, null)), null, null);
        QuizQuestionRefinementRequestDTO request = new QuizQuestionRefinementRequestDTO(originalQuestion, "Make it harder");

        assertThatThrownBy(() -> serviceWithNullClient.refineQuizQuestion(course, request)).isInstanceOf(InternalServerErrorAlertException.class)
                .hasMessageContaining("AI chat client is not configured");
    }

    @Test
    void refineQuizQuestion_throwsExceptionWhenChatClientFails() {
        when(chatModel.call(any(Prompt.class))).thenThrow(new RuntimeException("AI error"));

        Course course = new Course();
        course.setTitle("Software Engineering");

        GeneratedQuizQuestionDTO originalQuestion = new GeneratedQuizQuestionDTO(QuizQuestionGenerationType.SINGLE_CHOICE, "HTTP Methods", "Which method?",
                List.of(new GeneratedQuizAnswerOptionDTO("PUT", true, null, null), new GeneratedQuizAnswerOptionDTO("POST", false, null, null)), null, null);
        QuizQuestionRefinementRequestDTO request = new QuizQuestionRefinementRequestDTO(originalQuestion, "Make it harder");

        assertThatThrownBy(() -> service.refineQuizQuestion(course, request)).isInstanceOf(InternalServerErrorAlertException.class)
                .hasMessageContaining("Failed to refine quiz question");
    }

    @Test
    void generateQuizQuestions_tracksTokenUsageOnSuccess() {
        String json = """
                {
                  "questions": [
                    {
                      "type": "single-choice",
                      "title": "HTTP Methods",
                      "questionText": "Which HTTP method is idempotent?",
                      "options": [
                        { "text": "PUT", "correct": true },
                        { "text": "POST", "correct": false }
                      ]
                    }
                  ]
                }
                """;
        when(chatModel.call(any(Prompt.class))).thenAnswer(_ -> new ChatResponse(List.of(new Generation(new AssistantMessage(json)))));

        Course course = new Course();
        course.setTitle("Software Engineering");
        QuizQuestionGenerationRequestDTO request = new QuizQuestionGenerationRequestDTO("HTTP", null, "", QuizQuestionGenerationLanguage.EN,
                Set.of(QuizQuestionGenerationType.SINGLE_CHOICE), 1, 50);

        service.generateQuizQuestions(course, request);

        verify(llmTokenUsageService).trackChatResponseTokenUsage(any(), eq(LLMServiceType.HYPERION), any(), any());
    }

    @Test
    void generateQuizQuestions_tracksTokenUsageEvenWhenConversionFails() {
        when(chatModel.call(any(Prompt.class))).thenAnswer(_ -> new ChatResponse(List.of(new Generation(new AssistantMessage("not valid json")))));

        Course course = new Course();
        course.setTitle("Software Engineering");
        QuizQuestionGenerationRequestDTO request = new QuizQuestionGenerationRequestDTO("HTTP", null, "", QuizQuestionGenerationLanguage.EN,
                Set.of(QuizQuestionGenerationType.SINGLE_CHOICE), 1, 50);

        assertThatThrownBy(() -> service.generateQuizQuestions(course, request)).isInstanceOf(InternalServerErrorAlertException.class);

        verify(llmTokenUsageService).trackChatResponseTokenUsage(any(), eq(LLMServiceType.HYPERION), any(), any());
    }

    @Test
    void refineQuizQuestion_tracksTokenUsageOnSuccess() {
        String json = """
                {
                  "question": {
                    "type": "single-choice",
                    "title": "HTTP Idempotency",
                    "questionText": "Which HTTP method is idempotent?",
                    "options": [
                      { "text": "PUT", "correct": true },
                      { "text": "POST", "correct": false }
                    ]
                  },
                  "reasoning": "Improved wording."
                }
                """;
        when(chatModel.call(any(Prompt.class))).thenAnswer(_ -> new ChatResponse(List.of(new Generation(new AssistantMessage(json)))));

        Course course = new Course();
        course.setTitle("Software Engineering");
        GeneratedQuizQuestionDTO originalQuestion = new GeneratedQuizQuestionDTO(QuizQuestionGenerationType.SINGLE_CHOICE, "HTTP Methods", "Which method?",
                List.of(new GeneratedQuizAnswerOptionDTO("PUT", true, null, null), new GeneratedQuizAnswerOptionDTO("POST", false, null, null)), null, null);
        QuizQuestionRefinementRequestDTO request = new QuizQuestionRefinementRequestDTO(originalQuestion, "Make it clearer");

        service.refineQuizQuestion(course, request);

        verify(llmTokenUsageService).trackChatResponseTokenUsage(any(), eq(LLMServiceType.HYPERION), any(), any());
    }

    @Test
    void refineAllQuizQuestions_returnsOneResultPerQuestion() {
        String json = """
                {
                  "question": {
                    "type": "single-choice",
                    "title": "HTTP Idempotency",
                    "questionText": "Which HTTP method is idempotent?",
                    "options": [
                      { "text": "PUT", "correct": true },
                      { "text": "POST", "correct": false }
                    ]
                  },
                  "reasoning": "Improved."
                }
                """;
        when(chatModel.call(any(Prompt.class))).thenAnswer(_ -> new ChatResponse(List.of(new Generation(new AssistantMessage(json)))));

        Course course = new Course();
        course.setTitle("Software Engineering");
        GeneratedQuizQuestionDTO q1 = new GeneratedQuizQuestionDTO(QuizQuestionGenerationType.SINGLE_CHOICE, "Q1", "Question 1?",
                List.of(new GeneratedQuizAnswerOptionDTO("A", true, null, null), new GeneratedQuizAnswerOptionDTO("B", false, null, null)), null, null);
        GeneratedQuizQuestionDTO q2 = new GeneratedQuizQuestionDTO(QuizQuestionGenerationType.SINGLE_CHOICE, "Q2", "Question 2?",
                List.of(new GeneratedQuizAnswerOptionDTO("C", true, null, null), new GeneratedQuizAnswerOptionDTO("D", false, null, null)), null, null);
        QuizQuestionBulkRefinementRequestDTO request = new QuizQuestionBulkRefinementRequestDTO(List.of(q1, q2), "Make it harder");

        QuizQuestionBulkRefinementResponseDTO response = service.refineAllQuizQuestions(course, request);

        assertThat(response.refinements()).hasSize(2);
        assertThat(response.refinements()).allMatch(r -> r instanceof QuizQuestionRefinementResponseDTO.QuizQuestionRefinementSuccessDTO);
    }

    @Test
    void refineAllQuizQuestions_returnsFailureDTOWithoutThrowingWhenQuestionFails() {
        when(chatModel.call(any(Prompt.class))).thenThrow(new RuntimeException("AI unavailable"));

        Course course = new Course();
        course.setTitle("Software Engineering");
        GeneratedQuizQuestionDTO q = new GeneratedQuizQuestionDTO(QuizQuestionGenerationType.SINGLE_CHOICE, "Q1", "Question?",
                List.of(new GeneratedQuizAnswerOptionDTO("A", true, null, null), new GeneratedQuizAnswerOptionDTO("B", false, null, null)), null, null);
        QuizQuestionBulkRefinementRequestDTO request = new QuizQuestionBulkRefinementRequestDTO(List.of(q), "Refine it");

        QuizQuestionBulkRefinementResponseDTO response = service.refineAllQuizQuestions(course, request);

        assertThat(response.refinements()).hasSize(1);
        assertThat(response.refinements().getFirst()).isInstanceOf(QuizQuestionRefinementResponseDTO.QuizQuestionRefinementFailureDTO.class);
    }

    @Test
    void generateQuizQuestions_throwsExceptionWhenTypeIsInvalid() {
        String json = """
                {
                  "questions": [
                    {
                      "type": "essay",
                      "title": "Why",
                      "questionText": "Why?",
                      "options": [
                        { "text": "A", "correct": true },
                        { "text": "B", "correct": false }
                      ]
                    }
                  ]
                }
                """;
        when(chatModel.call(any(Prompt.class))).thenAnswer(_ -> new ChatResponse(List.of(new Generation(new AssistantMessage(json)))));

        Course course = new Course();
        course.setTitle("Software Engineering");

        QuizQuestionGenerationRequestDTO request = new QuizQuestionGenerationRequestDTO("HTTP", null, "", QuizQuestionGenerationLanguage.EN,
                Set.of(QuizQuestionGenerationType.SINGLE_CHOICE), 1, 50);

        assertThatThrownBy(() -> service.generateQuizQuestions(course, request)).isInstanceOf(InternalServerErrorAlertException.class).hasMessageContaining("type is invalid");
    }
}
