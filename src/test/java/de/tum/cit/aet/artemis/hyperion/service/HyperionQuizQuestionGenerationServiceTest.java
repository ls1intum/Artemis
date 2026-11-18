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

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource({ "title,501,Generated quiz question title is invalid", "title,0,Generated quiz question title is invalid",
            "questionText,10001,Generated quiz question text is invalid", "questionText,0,Generated quiz question text is invalid",
            "optionText,2001,Generated quiz answer option text is invalid", "optionText,0,Generated quiz answer option text is invalid" })
    void rejectsGeneratedTextOutsideDocumentedLengthBoundaries(String field, int length, String message) {
        tools.jackson.databind.json.JsonMapper mapper = new tools.jackson.databind.json.JsonMapper();
        tools.jackson.databind.node.ObjectNode question = validGeneratedQuestion(mapper);
        if (field.equals("optionText")) {
            ((tools.jackson.databind.node.ObjectNode) question.path("options").get(0)).put("text", "x".repeat(length));
        }
        else {
            question.put(field, "x".repeat(length));
        }
        setGenerationResponse(mapper.writeValueAsString(mapper.createObjectNode().set("questions", mapper.createArrayNode().add(question))));
        assertThatThrownBy(() -> service.generateQuizQuestions(new Course(), generationRequest())).isInstanceOf(InternalServerErrorAlertException.class).hasMessage(message);
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = { "null", "{}", "{\"questions\":null}", "{\"questions\":[]}", "{\"questions\":[null]}",
            "{\"questions\":[{\"type\":\"single-choice\",\"title\":\"Title\",\"questionText\":\"Text\"}]}" })
    void rejectsEmptyQuestionCollectionsAndMissingOptions(String response) {
        setGenerationResponse(response);
        assertThatThrownBy(() -> service.generateQuizQuestions(new Course(), generationRequest())).isInstanceOf(InternalServerErrorAlertException.class).hasMessageContaining(
                response.contains("[null]") ? "Generated quiz question is null" : response.contains("Title") ? "options are invalid" : "Generated quiz questions are empty");
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource({ "single-choice,0,false", "single-choice,2,false", "multiple-choice,0,false", "multiple-choice,2,true", "true-false,0,false",
            "true-false,1,true", "true-false,2,false" })
    void generatedQuestionTypesRequireTheirOwnCorrectOptionCounts(String type, int correctCount, boolean valid) {
        tools.jackson.databind.json.JsonMapper mapper = new tools.jackson.databind.json.JsonMapper();
        tools.jackson.databind.node.ObjectNode question = validGeneratedQuestion(mapper);
        question.put("type", type);
        ((tools.jackson.databind.node.ObjectNode) question.path("options").get(0)).put("correct", correctCount > 0);
        ((tools.jackson.databind.node.ObjectNode) question.path("options").get(1)).put("correct", correctCount > 1);
        setGenerationResponse(mapper.writeValueAsString(mapper.createObjectNode().set("questions", mapper.createArrayNode().add(question))));
        if (valid) {
            assertThat(service.generateQuizQuestions(new Course(), generationRequest()).questions().getFirst().type().getValue()).isEqualTo(type);
        }
        else {
            assertThatThrownBy(() -> service.generateQuizQuestions(new Course(), generationRequest())).isInstanceOf(InternalServerErrorAlertException.class).hasMessageContaining(
                    type.equals("single-choice") ? "exactly one correct answer" : type.equals("multiple-choice") ? "at least one correct answer" : "exactly two options");
        }
    }

    @Test
    void generatedOptionsRejectNullsAndDefaultMissingCorrectnessToFalse() {
        tools.jackson.databind.json.JsonMapper mapper = new tools.jackson.databind.json.JsonMapper();
        tools.jackson.databind.node.ObjectNode question = validGeneratedQuestion(mapper);
        tools.jackson.databind.node.ArrayNode options = (tools.jackson.databind.node.ArrayNode) question.path("options");
        options.set(1, mapper.nullNode());
        setGenerationResponse(mapper.writeValueAsString(mapper.createObjectNode().set("questions", mapper.createArrayNode().add(question))));
        assertThatThrownBy(() -> service.generateQuizQuestions(new Course(), generationRequest())).isInstanceOf(InternalServerErrorAlertException.class)
                .hasMessage("Generated quiz answer option is null");
        options.set(1, mapper.createObjectNode().put("text", "wrong").put("hint", "hint").put("explanation", "explanation"));
        question.put("hint", "question hint");
        question.put("explanation", "question explanation");
        setGenerationResponse(mapper.writeValueAsString(mapper.createObjectNode().set("questions", mapper.createArrayNode().add(question))));
        var result = service.generateQuizQuestions(new Course(), generationRequest()).questions().getFirst();
        assertThat(result.hint()).isEqualTo("question hint");
        assertThat(result.explanation()).isEqualTo("question explanation");
        assertThat(result.options().get(1).correct()).isFalse();
        assertThat(result.options().get(1).hint()).isEqualTo("hint");
        assertThat(result.options().get(1).explanation()).isEqualTo("explanation");
    }

    @Test
    void refinementIncludesHintsAndExplanationsInModelRequest() {
        setGenerationResponse(
                "{\"question\":{\"type\":\"single-choice\",\"title\":\"Improved\",\"questionText\":\"Text\",\"options\":[{\"text\":\"A\",\"correct\":true},{\"text\":\"B\",\"correct\":false}]},\"reasoning\":\"Precise\"}");
        GeneratedQuizQuestionDTO original = new GeneratedQuizQuestionDTO(QuizQuestionGenerationType.SINGLE_CHOICE, "Title", "Text",
                List.of(new GeneratedQuizAnswerOptionDTO("A", true, "option hint", "option explanation"), new GeneratedQuizAnswerOptionDTO("B", false, " ", " ")), "question hint",
                "question explanation");
        var result = service.refineQuizQuestion(new Course(), new QuizQuestionRefinementRequestDTO(original, "Improve"));
        assertThat(result).isInstanceOf(QuizQuestionRefinementResponseDTO.QuizQuestionRefinementSuccessDTO.class);
        org.mockito.ArgumentCaptor<Prompt> captor = org.mockito.ArgumentCaptor.forClass(Prompt.class);
        verify(chatModel).call(captor.capture());
        assertThat(captor.getValue().getUserMessage().getText()).contains("question hint", "question explanation", "option hint", "option explanation", "[correct]", "[wrong]");
    }

    @Test
    void competencyModeRendersTaxonomiesRelationsAndLectureContext() {
        de.tum.cit.aet.artemis.atlas.domain.competency.Competency first = new de.tum.cit.aet.artemis.atlas.domain.competency.Competency();
        first.setId(1L);
        first.setTitle("Loops");
        first.setTaxonomy(de.tum.cit.aet.artemis.atlas.domain.competency.CompetencyTaxonomy.APPLY);
        first.setDescription("Iterate elements");
        de.tum.cit.aet.artemis.atlas.domain.competency.Competency second = new de.tum.cit.aet.artemis.atlas.domain.competency.Competency();
        second.setId(2L);
        second.setTitle("Variables");
        second.setDescription(" ");
        java.util.Set<de.tum.cit.aet.artemis.atlas.domain.competency.CompetencyRelation> relations = new java.util.HashSet<>();
        long relationId = 1;
        for (var type : de.tum.cit.aet.artemis.atlas.domain.competency.RelationType.values()) {
            var relation = new de.tum.cit.aet.artemis.atlas.domain.competency.CompetencyRelation();
            relation.setId(relationId++);
            relation.setTailCompetency(first);
            relation.setHeadCompetency(second);
            relation.setType(type);
            relations.add(relation);
        }
        when(competencyContextService.computeContext(7L, List.of(1L, 2L)))
                .thenReturn(new HyperionCompetencyContextService.CompetencyContext(List.of(first, second), relations, List.of("Lecture excerpt")));
        Course course = new Course();
        course.setId(7L);
        setGenerationResponse(
                "{\"questions\":[{\"type\":\"single-choice\",\"title\":\"Title\",\"questionText\":\"Text\",\"options\":[{\"text\":\"A\",\"correct\":true},{\"text\":\"B\",\"correct\":false}]}]}");
        var request = new QuizQuestionGenerationRequestDTO(null, List.of(1L, 2L), "", QuizQuestionGenerationLanguage.EN, Set.of(QuizQuestionGenerationType.SINGLE_CHOICE), 1, 50);
        assertThat(service.generateQuizQuestions(course, request).questions()).hasSize(1);
        org.mockito.ArgumentCaptor<Prompt> captor = org.mockito.ArgumentCaptor.forClass(Prompt.class);
        verify(chatModel).call(captor.capture());
        assertThat(captor.getValue().getUserMessage().getText()).contains("Loops (APPLY): Iterate elements", "Variables (UNDERSTAND)", "ASSUMES", "EXTENDS", "MATCHES",
                "Lecture excerpt");
    }

    private QuizQuestionGenerationRequestDTO generationRequest() {
        return new QuizQuestionGenerationRequestDTO("Topic", List.of(), "", QuizQuestionGenerationLanguage.EN, Set.of(QuizQuestionGenerationType.SINGLE_CHOICE), 1, 50);
    }

    private tools.jackson.databind.node.ObjectNode validGeneratedQuestion(tools.jackson.databind.json.JsonMapper mapper) {
        tools.jackson.databind.node.ObjectNode question = mapper.createObjectNode().put("type", "single-choice").put("title", "Title").put("questionText", "Text");
        question.putArray("options").addObject().put("text", "A").put("correct", true);
        ((tools.jackson.databind.node.ArrayNode) question.path("options")).addObject().put("text", "B").put("correct", false);
        return question;
    }

    private void setGenerationResponse(String response) {
        when(chatModel.call(any(Prompt.class))).thenReturn(new ChatResponse(List.of(new Generation(new AssistantMessage(response)))));
    }

}
