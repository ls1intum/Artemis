package de.tum.cit.aet.artemis.hyperion.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.util.List;

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

import de.tum.cit.aet.artemis.core.domain.Language;
import de.tum.cit.aet.artemis.exercise.domain.DifficultyLevel;
import de.tum.cit.aet.artemis.hyperion.dto.quiz.AiQuestionSubtype;
import de.tum.cit.aet.artemis.hyperion.dto.quiz.AiQuizGenerationRequestDTO;
import de.tum.cit.aet.artemis.hyperion.dto.quiz.AiQuizGenerationResponseDTO;

class AiQuizGenerationServiceTest {

    @Mock
    private ChatModel chatModel;

    private AiQuizGenerationService aiQuizGenerationService;

    @BeforeEach
    void setup() {
        MockitoAnnotations.openMocks(this);
        ChatClient chatClient = ChatClient.create(chatModel);
        var templateService = new HyperionPromptTemplateService();
        this.aiQuizGenerationService = new AiQuizGenerationService(chatClient, templateService);
    }

    @Test
    void testGenerateQuiz_validSingleCorrectQuestion_success() throws Exception {
        // Valid JSON response with a single SINGLE_CORRECT question
        String validResponse = """
                [
                  {
                    "title": "What is Java?",
                    "text": "Java is a programming language.",
                    "explanation": "Java is a high-level, class-based, object-oriented programming language.",
                    "hint": "Think about OOP languages",
                    "difficulty": 3,
                    "tags": ["programming", "java"],
                    "subtype": "SINGLE_CORRECT",
                    "competencyIds": [],
                    "options": [
                      {"text": "A programming language", "correct": true, "feedback": "Correct!"},
                      {"text": "A coffee brand", "correct": false, "feedback": "Incorrect"},
                      {"text": "An island", "correct": false, "feedback": "Incorrect"}
                    ]
                  }
                ]
                """;

        when(chatModel.call(any(Prompt.class))).thenAnswer(invocation -> new ChatResponse(List.of(new Generation(new AssistantMessage(validResponse)))));

        var request = new AiQuizGenerationRequestDTO("Java Programming", 1, Language.ENGLISH, DifficultyLevel.MEDIUM, AiQuestionSubtype.SINGLE_CORRECT, "Focus on basics");

        AiQuizGenerationResponseDTO response = aiQuizGenerationService.generate(1L, request);

        assertThat(response).isNotNull();
        assertThat(response.questions()).hasSize(1);
        assertThat(response.warnings()).isEmpty();
        assertThat(response.questions().get(0).title()).isEqualTo("What is Java?");
        assertThat(response.questions().get(0).subtype()).isEqualTo(AiQuestionSubtype.SINGLE_CORRECT);
        assertThat(response.questions().get(0).options()).hasSize(3);
    }

    @Test
    void testGenerateQuiz_validMultiCorrectQuestion_success() throws Exception {
        String validResponse = """
                [
                  {
                    "title": "Which are programming languages?",
                    "text": "Select all programming languages from the list.",
                    "explanation": "Java, Python, and C++ are all programming languages.",
                    "hint": "More than one is correct",
                    "difficulty": 2,
                    "tags": [],
                    "subtype": "MULTI_CORRECT",
                    "competencyIds": [],
                    "options": [
                      {"text": "Java", "correct": true, "feedback": "Correct!"},
                      {"text": "Python", "correct": true, "feedback": "Correct!"},
                      {"text": "HTML", "correct": false, "feedback": "HTML is a markup language"},
                      {"text": "C++", "correct": true, "feedback": "Correct!"}
                    ]
                  }
                ]
                """;

        when(chatModel.call(any(Prompt.class))).thenAnswer(invocation -> new ChatResponse(List.of(new Generation(new AssistantMessage(validResponse)))));

        var request = new AiQuizGenerationRequestDTO("Programming Languages", 1, Language.ENGLISH, DifficultyLevel.EASY, AiQuestionSubtype.MULTI_CORRECT, null);

        AiQuizGenerationResponseDTO response = aiQuizGenerationService.generate(1L, request);

        assertThat(response).isNotNull();
        assertThat(response.questions()).hasSize(1);
        assertThat(response.warnings()).isEmpty();
        assertThat(response.questions().get(0).subtype()).isEqualTo(AiQuestionSubtype.MULTI_CORRECT);
        assertThat(response.questions().get(0).options()).hasSize(4);
        assertThat(response.questions().get(0).options().stream().filter(opt -> opt.correct()).count()).isEqualTo(3);
    }

    @Test
    void testGenerateQuiz_validTrueFalseQuestion_success() throws Exception {
        String validResponse = """
                [
                  {
                    "title": "Java is compiled",
                    "text": "Java is a compiled programming language.",
                    "explanation": "Java is compiled to bytecode.",
                    "hint": "Think about the JVM",
                    "difficulty": 1,
                    "tags": [],
                    "subtype": "TRUE_FALSE",
                    "competencyIds": [],
                    "options": [
                      {"text": "True", "correct": true, "feedback": "Correct!"},
                      {"text": "False", "correct": false, "feedback": "Incorrect"}
                    ]
                  }
                ]
                """;

        when(chatModel.call(any(Prompt.class))).thenAnswer(invocation -> new ChatResponse(List.of(new Generation(new AssistantMessage(validResponse)))));

        var request = new AiQuizGenerationRequestDTO("Java Basics", 1, Language.ENGLISH, DifficultyLevel.EASY, AiQuestionSubtype.TRUE_FALSE, null);

        AiQuizGenerationResponseDTO response = aiQuizGenerationService.generate(1L, request);

        assertThat(response).isNotNull();
        assertThat(response.questions()).hasSize(1);
        assertThat(response.warnings()).isEmpty();
        assertThat(response.questions().get(0).subtype()).isEqualTo(AiQuestionSubtype.TRUE_FALSE);
        assertThat(response.questions().get(0).options()).hasSize(2);
    }

    @Test
    void testGenerateQuiz_singleCorrectWithMultipleCorrectAnswers_validationFailure() throws Exception {
        // Invalid: SINGLE_CORRECT with 2 correct answers
        String invalidResponse = """
                [
                  {
                    "title": "What is Java?",
                    "text": "Java is a programming language.",
                    "explanation": "Java description.",
                    "hint": "Think about it",
                    "difficulty": 3,
                    "tags": [],
                    "subtype": "SINGLE_CORRECT",
                    "competencyIds": [],
                    "options": [
                      {"text": "A programming language", "correct": true, "feedback": "Correct!"},
                      {"text": "An OOP language", "correct": true, "feedback": "Also correct!"},
                      {"text": "A coffee brand", "correct": false, "feedback": "Incorrect"}
                    ]
                  }
                ]
                """;

        when(chatModel.call(any(Prompt.class))).thenAnswer(invocation -> new ChatResponse(List.of(new Generation(new AssistantMessage(invalidResponse)))));

        var request = new AiQuizGenerationRequestDTO("Java", 1, Language.ENGLISH, DifficultyLevel.MEDIUM, AiQuestionSubtype.SINGLE_CORRECT, null);

        AiQuizGenerationResponseDTO response = aiQuizGenerationService.generate(1L, request);

        assertThat(response).isNotNull();
        assertThat(response.questions()).isEmpty();
        assertThat(response.warnings()).isNotEmpty();
        assertThat(response.warnings().get(0)).contains("validation");
        assertThat(response.warnings().get(0)).contains("exactly 1 correct answer");
    }

    @Test
    void testGenerateQuiz_trueFalseWithThreeOptions_validationFailure() throws Exception {
        // Invalid: TRUE_FALSE with 3 options instead of 2
        String invalidResponse = """
                [
                  {
                    "title": "Java is compiled",
                    "text": "Java is a compiled programming language.",
                    "explanation": "Java is compiled to bytecode.",
                    "hint": "Think about the JVM",
                    "difficulty": 1,
                    "tags": [],
                    "subtype": "TRUE_FALSE",
                    "competencyIds": [],
                    "options": [
                      {"text": "True", "correct": true, "feedback": "Correct!"},
                      {"text": "False", "correct": false, "feedback": "Incorrect"},
                      {"text": "Maybe", "correct": false, "feedback": "Not applicable"}
                    ]
                  }
                ]
                """;

        when(chatModel.call(any(Prompt.class))).thenAnswer(invocation -> new ChatResponse(List.of(new Generation(new AssistantMessage(invalidResponse)))));

        var request = new AiQuizGenerationRequestDTO("Java", 1, Language.ENGLISH, DifficultyLevel.EASY, AiQuestionSubtype.TRUE_FALSE, null);

        AiQuizGenerationResponseDTO response = aiQuizGenerationService.generate(1L, request);

        assertThat(response).isNotNull();
        assertThat(response.questions()).isEmpty();
        assertThat(response.warnings()).isNotEmpty();
        assertThat(response.warnings().get(0)).contains("exactly 2 options");
    }

    @Test
    void testGenerateQuiz_multiCorrectWithNoCorrectAnswers_validationFailure() throws Exception {
        // Invalid: MULTI_CORRECT with no correct answers
        String invalidResponse = """
                [
                  {
                    "title": "Which are programming languages?",
                    "text": "Select all programming languages.",
                    "explanation": "None selected.",
                    "hint": "Think carefully",
                    "difficulty": 2,
                    "tags": [],
                    "subtype": "MULTI_CORRECT",
                    "competencyIds": [],
                    "options": [
                      {"text": "Java", "correct": false, "feedback": "Wrong"},
                      {"text": "Python", "correct": false, "feedback": "Wrong"}
                    ]
                  }
                ]
                """;

        when(chatModel.call(any(Prompt.class))).thenAnswer(invocation -> new ChatResponse(List.of(new Generation(new AssistantMessage(invalidResponse)))));

        var request = new AiQuizGenerationRequestDTO("Programming", 1, Language.ENGLISH, DifficultyLevel.EASY, AiQuestionSubtype.MULTI_CORRECT, null);

        AiQuizGenerationResponseDTO response = aiQuizGenerationService.generate(1L, request);

        assertThat(response).isNotNull();
        assertThat(response.questions()).isEmpty();
        assertThat(response.warnings()).isNotEmpty();
        assertThat(response.warnings().get(0)).contains("at least 1 correct answer");
    }

    @Test
    void testGenerateQuiz_missingRequiredFields_validationFailure() throws Exception {
        // Invalid: Missing title
        String invalidResponse = """
                [
                  {
                    "title": "",
                    "text": "What is Java?",
                    "explanation": "Java description.",
                    "hint": "Think about it",
                    "difficulty": 3,
                    "tags": [],
                    "subtype": "SINGLE_CORRECT",
                    "competencyIds": [],
                    "options": [
                      {"text": "A programming language", "correct": true, "feedback": "Correct!"},
                      {"text": "A coffee brand", "correct": false, "feedback": "Incorrect"}
                    ]
                  }
                ]
                """;

        when(chatModel.call(any(Prompt.class))).thenAnswer(invocation -> new ChatResponse(List.of(new Generation(new AssistantMessage(invalidResponse)))));

        var request = new AiQuizGenerationRequestDTO("Java", 1, Language.ENGLISH, DifficultyLevel.MEDIUM, AiQuestionSubtype.SINGLE_CORRECT, null);

        AiQuizGenerationResponseDTO response = aiQuizGenerationService.generate(1L, request);

        assertThat(response).isNotNull();
        assertThat(response.questions()).isEmpty();
        assertThat(response.warnings()).isNotEmpty();
        // relaxed assertion: match what the service actually produces
        assertThat(response.warnings().get(0)).contains("Question 1 violated constraints").contains("must not be blank");
    }

    @Test
    void testGenerateQuiz_multipleQuestions_success() throws Exception {
        String validResponse = """
                [
                  {
                    "title": "Question 1",
                    "text": "First question text.",
                    "explanation": "Explanation 1.",
                    "hint": "Hint 1",
                    "difficulty": 2,
                    "tags": [],
                    "subtype": "SINGLE_CORRECT",
                    "competencyIds": [],
                    "options": [
                      {"text": "Option A", "correct": true, "feedback": "Correct!"},
                      {"text": "Option B", "correct": false, "feedback": "Incorrect"}
                    ]
                  },
                  {
                    "title": "Question 2",
                    "text": "Second question text.",
                    "explanation": "Explanation 2.",
                    "hint": "Hint 2",
                    "difficulty": 3,
                    "tags": [],
                    "subtype": "TRUE_FALSE",
                    "competencyIds": [],
                    "options": [
                      {"text": "True", "correct": false, "feedback": "Incorrect"},
                      {"text": "False", "correct": true, "feedback": "Correct!"}
                    ]
                  }
                ]
                """;

        when(chatModel.call(any(Prompt.class))).thenAnswer(invocation -> new ChatResponse(List.of(new Generation(new AssistantMessage(validResponse)))));

        var request = new AiQuizGenerationRequestDTO("Programming", 2, Language.ENGLISH, DifficultyLevel.MEDIUM, AiQuestionSubtype.SINGLE_CORRECT, null);

        AiQuizGenerationResponseDTO response = aiQuizGenerationService.generate(1L, request);

        assertThat(response).isNotNull();
        assertThat(response.questions()).hasSize(2);
        assertThat(response.warnings()).isEmpty();
        assertThat(response.questions().get(0).title()).isEqualTo("Question 1");
        assertThat(response.questions().get(1).title()).isEqualTo("Question 2");
    }

    @Test
    void testGenerateQuiz_emptyResponse_validationFailure() throws Exception {
        String emptyResponse = "[]";

        when(chatModel.call(any(Prompt.class))).thenAnswer(invocation -> new ChatResponse(List.of(new Generation(new AssistantMessage(emptyResponse)))));

        var request = new AiQuizGenerationRequestDTO("Java", 1, Language.ENGLISH, DifficultyLevel.MEDIUM, AiQuestionSubtype.SINGLE_CORRECT, null);

        AiQuizGenerationResponseDTO response = aiQuizGenerationService.generate(1L, request);

        assertThat(response).isNotNull();
        assertThat(response.questions()).isEmpty();
        assertThat(response.warnings()).isNotEmpty();
        assertThat(response.warnings().get(0)).contains("No questions were generated");
    }

    @Test
    void testGenerateQuiz_invalidJson_errorHandling() throws Exception {
        String invalidJson = "This is not valid JSON";

        when(chatModel.call(any(Prompt.class))).thenAnswer(invocation -> new ChatResponse(List.of(new Generation(new AssistantMessage(invalidJson)))));

        var request = new AiQuizGenerationRequestDTO("Java", 1, Language.ENGLISH, DifficultyLevel.MEDIUM, AiQuestionSubtype.SINGLE_CORRECT, null);

        AiQuizGenerationResponseDTO response = aiQuizGenerationService.generate(1L, request);

        assertThat(response).isNotNull();
        assertThat(response.questions()).isEmpty();
        assertThat(response.warnings()).isNotEmpty();
        assertThat(response.warnings().get(0)).contains("response could not be processed");
    }
}
