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
import de.tum.cit.aet.artemis.hyperion.dto.QuizQuestionGenerationLanguage;
import de.tum.cit.aet.artemis.hyperion.dto.QuizQuestionGenerationRequestDTO;
import de.tum.cit.aet.artemis.hyperion.dto.QuizQuestionGenerationResponseDTO;
import de.tum.cit.aet.artemis.hyperion.dto.QuizQuestionGenerationType;

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
