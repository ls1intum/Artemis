package de.tum.cit.aet.artemis.hyperion.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
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
import org.springframework.ai.chat.prompt.Prompt;

import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.exception.InternalServerErrorAlertException;
import de.tum.cit.aet.artemis.hyperion.dto.GeneratedQuizAnswerOptionDTO;
import de.tum.cit.aet.artemis.hyperion.dto.GeneratedQuizQuestionDTO;
import de.tum.cit.aet.artemis.hyperion.dto.QuizQuestionGenerationLanguage;
import de.tum.cit.aet.artemis.hyperion.dto.QuizQuestionGenerationRequestDTO;
import de.tum.cit.aet.artemis.hyperion.dto.QuizQuestionGenerationResponseDTO;
import de.tum.cit.aet.artemis.hyperion.dto.QuizQuestionGenerationType;
import de.tum.cit.aet.artemis.hyperion.dto.QuizQuestionRefinementRequestDTO;
import de.tum.cit.aet.artemis.hyperion.dto.QuizQuestionRefinementResponseDTO;

class HyperionQuizQuestionGenerationServiceTest {

    @Mock
    private ChatModel chatModel;

    private HyperionQuizQuestionGenerationService service;

    private AutoCloseable mocks;

    @BeforeEach
    void setup() {
        mocks = MockitoAnnotations.openMocks(this);
        ChatClient chatClient = ChatClient.create(chatModel);
        service = new HyperionQuizQuestionGenerationService(chatClient, new HyperionPromptTemplateService());
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

        QuizQuestionGenerationRequestDTO request = new QuizQuestionGenerationRequestDTO("HTTP", "", QuizQuestionGenerationLanguage.EN,
                Set.of(QuizQuestionGenerationType.SINGLE_CHOICE), 1, 50);

        QuizQuestionGenerationResponseDTO response = service.generateQuizQuestions(course, request);

        assertThat(response.questions()).hasSize(1);
        assertThat(response.questions().getFirst().type()).isEqualTo(QuizQuestionGenerationType.SINGLE_CHOICE);
        assertThat(response.questions().getFirst().title()).isEqualTo("HTTP Methods");
        assertThat(response.questions().getFirst().options()).hasSize(3);
    }

    @Test
    void generateQuizQuestions_throwsExceptionWhenChatClientIsNull() {
        var serviceWithNullClient = new HyperionQuizQuestionGenerationService(null, new HyperionPromptTemplateService());

        Course course = new Course();
        course.setTitle("Software Engineering");
        course.setDescription("SE basics");

        QuizQuestionGenerationRequestDTO request = new QuizQuestionGenerationRequestDTO("HTTP", "", QuizQuestionGenerationLanguage.EN,
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

        QuizQuestionRefinementResponseDTO.Success response = (QuizQuestionRefinementResponseDTO.Success) service.refineQuizQuestion(course, request);

        assertThat(response.question().title()).isEqualTo("HTTP Idempotency");
        assertThat(response.question().questionText()).isEqualTo("Which HTTP method is idempotent?");
        assertThat(response.question().options()).hasSize(2);
        assertThat(response.reasoning()).isEqualTo("Changed the wording to be more precise.");
    }

    @Test
    void refineQuizQuestion_throwsExceptionWhenChatClientIsNull() {
        var serviceWithNullClient = new HyperionQuizQuestionGenerationService(null, new HyperionPromptTemplateService());

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

        QuizQuestionGenerationRequestDTO request = new QuizQuestionGenerationRequestDTO("HTTP", "", QuizQuestionGenerationLanguage.EN,
                Set.of(QuizQuestionGenerationType.SINGLE_CHOICE), 1, 50);

        assertThatThrownBy(() -> service.generateQuizQuestions(course, request)).isInstanceOf(InternalServerErrorAlertException.class).hasMessageContaining("type is invalid");
    }
}
