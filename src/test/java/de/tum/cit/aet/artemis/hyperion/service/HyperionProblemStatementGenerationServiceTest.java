package de.tum.cit.aet.artemis.hyperion.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
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
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;

import com.fasterxml.jackson.databind.ObjectMapper;

import de.tum.cit.aet.artemis.account.test_repository.UserTestRepository;
import de.tum.cit.aet.artemis.admin.domain.LLMServiceType;
import de.tum.cit.aet.artemis.admin.service.LLMTokenUsageService;
import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;
import de.tum.cit.aet.artemis.core.exception.InternalServerErrorAlertException;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.exercise.domain.DifficultyLevel;
import de.tum.cit.aet.artemis.hyperion.dto.ProblemStatementGenerationResponseDTO;

class HyperionProblemStatementGenerationServiceTest {

    @Mock
    private ChatModel chatModel;

    @Mock
    private LLMTokenUsageService llmTokenUsageService;

    @Mock
    private UserTestRepository userRepository;

    private HyperionProblemStatementGenerationService hyperionProblemStatementGenerationService;

    private AutoCloseable mocks;

    @BeforeEach
    void setup() {
        mocks = MockitoAnnotations.openMocks(this);
        // Since Spring AI 2.0 the ChatClient merges request options into the model's options (getOptions since RC1, getDefaultOptions before), which must be non-null
        lenient().when(chatModel.getDefaultOptions()).thenReturn(ChatOptions.builder().build());
        lenient().when(chatModel.getOptions()).thenReturn(ChatOptions.builder().build());
        ChatClient chatClient = ChatClient.create(chatModel);
        var templateService = new HyperionPromptTemplateService();
        this.hyperionProblemStatementGenerationService = new HyperionProblemStatementGenerationService(chatClient, templateService, llmTokenUsageService, userRepository,
                new ObjectMapper());
    }

    private void stubModelResponse(String text) {
        when(chatModel.call(any(Prompt.class))).thenAnswer(_ -> new ChatResponse(List.of(new Generation(new AssistantMessage(text)))));
    }

    private static Course course() {
        var course = new Course();
        course.setId(123L);
        course.setTitle("Test Course");
        course.setDescription("Test Description");
        return course;
    }

    @Test
    void generateProblemStatement_returnsGeneratedDraft() {
        String generatedDraft = "Generated draft problem statement";
        ChatResponse chatResponse = org.mockito.Mockito.mock(ChatResponse.class);
        var generation = new Generation(new AssistantMessage(generatedDraft));
        when(chatResponse.getResult()).thenReturn(generation);

        var metadata = org.mockito.Mockito.mock(org.springframework.ai.chat.metadata.ChatResponseMetadata.class);
        var usage = org.mockito.Mockito.mock(org.springframework.ai.chat.metadata.Usage.class);
        when(usage.getPromptTokens()).thenReturn(10);
        when(usage.getCompletionTokens()).thenReturn(20);
        when(metadata.getUsage()).thenReturn(usage);
        when(chatResponse.getMetadata()).thenReturn(metadata);

        when(chatModel.call(any(Prompt.class))).thenAnswer(_ -> chatResponse);

        var course = new Course();
        course.setId(123L);
        course.setTitle("Test Course");
        course.setDescription("Test Description");
        ProblemStatementGenerationResponseDTO resp = hyperionProblemStatementGenerationService.generateProblemStatement(course, "Prompt");
        assertThat(resp).isNotNull();
        assertThat(resp.draftProblemStatement()).isEqualTo(generatedDraft);
        verify(llmTokenUsageService).trackChatResponseTokenUsage(eq(chatResponse), eq(LLMServiceType.HYPERION), eq("HYPERION_PROBLEM_GENERATION"), any());
    }

    @Test
    void generateProblemStatement_throwsExceptionOnAIFailure() {
        when(chatModel.call(any(Prompt.class))).thenThrow(new RuntimeException("AI service unavailable"));

        var course = new Course();
        course.setId(123L);
        course.setTitle("Test Course");
        course.setDescription("Test Description");

        assertThatThrownBy(() -> hyperionProblemStatementGenerationService.generateProblemStatement(course, "Prompt")).isInstanceOf(InternalServerErrorAlertException.class)
                .hasMessageContaining("Failed to generate problem statement");
    }

    @Test
    void generateProblemStatement_throwsExceptionOnExcessivelyLongResponse() {
        // Generate a string longer than MAX_PROBLEM_STATEMENT_LENGTH (50,000
        // characters)
        String excessivelyLongDraft = "a".repeat(50_001);
        when(chatModel.call(any(Prompt.class))).thenAnswer(_ -> new ChatResponse(List.of(new Generation(new AssistantMessage(excessivelyLongDraft)))));

        var course = new Course();
        course.setId(456L);
        course.setTitle("Test Course");
        course.setDescription("Test Description");

        assertThatThrownBy(() -> hyperionProblemStatementGenerationService.generateProblemStatement(course, "Prompt")).isInstanceOf(InternalServerErrorAlertException.class)
                .hasMessageContaining("exceeds the maximum allowed length");
    }

    @Test
    void generateProblemStatement_throwsExceptionWhenUserPromptIsNull() {
        var course = new Course();
        course.setTitle("Test Course");
        course.setDescription("Test Description");

        // Should throw exception when userPrompt is null (sanitized to empty string)
        assertThatThrownBy(() -> hyperionProblemStatementGenerationService.generateProblemStatement(course, null)).isInstanceOf(BadRequestAlertException.class)
                .hasMessageContaining("User prompt cannot be empty");
    }

    @Test
    void generateProblemStatement_throwsExceptionWhenUserPromptIsBlank() {
        var course = new Course();
        course.setTitle("Test Course");
        course.setDescription("Test Description");

        // Should throw exception when userPrompt is whitespace-only (sanitized to empty string)
        assertThatThrownBy(() -> hyperionProblemStatementGenerationService.generateProblemStatement(course, "   ")).isInstanceOf(BadRequestAlertException.class)
                .hasMessageContaining("User prompt cannot be empty");
    }

    @Test
    void generateProblemStatement_handlesNullCourseFields() {
        String generatedDraft = "Generated draft with default course info";
        when(chatModel.call(any(Prompt.class))).thenAnswer(_ -> new ChatResponse(List.of(new Generation(new AssistantMessage(generatedDraft)))));

        var course = new Course();
        // Leave title and description null

        // Should use default values when course fields are null
        ProblemStatementGenerationResponseDTO resp = hyperionProblemStatementGenerationService.generateProblemStatement(course, "Prompt");
        assertThat(resp).isNotNull();
        assertThat(resp.draftProblemStatement()).isEqualTo(generatedDraft);
    }

    @Test
    void generateProblemStatement_acceptsMaximumLengthResponse() {
        // Generate a string exactly at MAX_PROBLEM_STATEMENT_LENGTH (50,000 characters)
        String maxLengthDraft = "a".repeat(50_000);
        when(chatModel.call(any(Prompt.class))).thenAnswer(_ -> new ChatResponse(List.of(new Generation(new AssistantMessage(maxLengthDraft)))));

        var course = new Course();
        course.setTitle("Test Course");
        course.setDescription("Test Description");

        // Should succeed with exactly 50,000 characters
        ProblemStatementGenerationResponseDTO resp = hyperionProblemStatementGenerationService.generateProblemStatement(course, "Prompt");
        assertThat(resp).isNotNull();
        assertThat(resp.draftProblemStatement()).hasSize(50_000);
    }

    @Test
    void generateProblemStatement_throwsExceptionWhenChatClientIsNull() {
        var serviceWithNullClient = new HyperionProblemStatementGenerationService(null, new HyperionPromptTemplateService(), llmTokenUsageService, userRepository,
                new ObjectMapper());
        var course = new Course();
        course.setTitle("Test Course");
        course.setDescription("Test Description");

        assertThatThrownBy(() -> serviceWithNullClient.generateProblemStatement(course, "Prompt")).isInstanceOf(InternalServerErrorAlertException.class)
                .hasMessageContaining("AI chat client is not configured");
    }

    @Test
    void generateProblemStatement_throwsExceptionWhenResponseIsNull() {
        when(chatModel.call(any(Prompt.class))).thenAnswer(_ -> new ChatResponse(List.of(new Generation(new AssistantMessage(null)))));

        var course = new Course();
        course.setId(999L);
        course.setTitle("Test Course");
        course.setDescription("Test Description");

        assertThatThrownBy(() -> hyperionProblemStatementGenerationService.generateProblemStatement(course, "Prompt")).isInstanceOf(InternalServerErrorAlertException.class)
                .hasMessageContaining("Generated problem statement is null or empty");
    }

    @Test
    void generateProblemStatement_throwsExceptionWhenResponseIsBlank() {
        when(chatModel.call(any(Prompt.class))).thenAnswer(_ -> new ChatResponse(List.of(new Generation(new AssistantMessage("   ")))));

        var course = new Course();
        course.setId(999L);
        course.setTitle("Test Course");
        course.setDescription("Test Description");

        assertThatThrownBy(() -> hyperionProblemStatementGenerationService.generateProblemStatement(course, "Prompt")).isInstanceOf(InternalServerErrorAlertException.class)
                .hasMessageContaining("Generated problem statement is null or empty");
    }

    @Test
    void generateProblemStatement_throwsExceptionWhenUserPromptTooLong() {
        // 1001 characters exceeds MAX_USER_PROMPT_LENGTH (1000)
        String tooLongPrompt = "a".repeat(1001);
        var course = new Course();
        course.setTitle("Test Course");
        course.setDescription("Test Description");

        assertThatThrownBy(() -> hyperionProblemStatementGenerationService.generateProblemStatement(course, tooLongPrompt)).isInstanceOf(BadRequestAlertException.class)
                .hasMessageContaining("exceeds maximum length");
    }

    @Test
    void generateProblemStatement_parsesAndStripsTheProposedMetadataTrailer() {
        stubModelResponse("""
                # Ring Buffer

                Implement a fixed-capacity ring buffer.

                ```json
                {"title": "Ring Buffer", "difficulty": "MEDIUM", "categories": ["data-structures", "collections"]}
                ```
                """);

        var resp = hyperionProblemStatementGenerationService.generateProblemStatement(course(), "Prompt");

        // The trailer is removed from the student-facing statement and surfaced as suggestions.
        assertThat(resp.draftProblemStatement()).startsWith("# Ring Buffer").doesNotContain("```json").doesNotContain("difficulty");
        assertThat(resp.suggestedTitle()).isEqualTo("Ring Buffer");
        assertThat(resp.suggestedDifficulty()).isEqualTo(DifficultyLevel.MEDIUM);
        assertThat(resp.suggestedCategories()).containsExactly("data-structures", "collections");
    }

    @Test
    void generateProblemStatement_mapsDifficultyCaseInsensitively_andCapsCategoriesAtThree() {
        stubModelResponse("""
                # Cache

                Body.

                ```json
                {"difficulty": "hard", "categories": ["a", "a", "b", "c", "d"]}
                ```
                """);

        var resp = hyperionProblemStatementGenerationService.generateProblemStatement(course(), "Prompt");

        assertThat(resp.suggestedDifficulty()).isEqualTo(DifficultyLevel.HARD);
        // Deduped and capped to the first three distinct categories.
        assertThat(resp.suggestedCategories()).containsExactly("a", "b", "c");
        assertThat(resp.suggestedTitle()).isNull();
    }

    @Test
    void generateProblemStatement_withNoTrailer_returnsStatementUnchangedWithNoSuggestions() {
        stubModelResponse("# Plain\n\nNo metadata here.");

        var resp = hyperionProblemStatementGenerationService.generateProblemStatement(course(), "Prompt");

        assertThat(resp.draftProblemStatement()).isEqualTo("# Plain\n\nNo metadata here.");
        assertThat(resp.suggestedTitle()).isNull();
        assertThat(resp.suggestedDifficulty()).isNull();
        assertThat(resp.suggestedCategories()).isNull();
    }

    @Test
    void generateProblemStatement_ignoresJsonExampleInBody_andOnlyParsesTheMetadataTrailer() {
        // A json example in the statement body must NOT be mistaken for the metadata block; only the trailing block carrying metadata keys is consumed.
        stubModelResponse("""
                # Config

                Example input:

                ```json
                {"host": "localhost", "port": 8080}
                ```

                Implement the parser.

                ```json
                {"difficulty": "EASY", "categories": ["parsing"]}
                ```
                """);

        var resp = hyperionProblemStatementGenerationService.generateProblemStatement(course(), "Prompt");

        // The example block stays in the statement; only the metadata trailer is removed.
        assertThat(resp.draftProblemStatement()).contains("\"host\": \"localhost\"").doesNotContain("difficulty");
        assertThat(resp.suggestedDifficulty()).isEqualTo(DifficultyLevel.EASY);
        assertThat(resp.suggestedCategories()).containsExactly("parsing");
    }

    @Test
    void generateProblemStatement_failsOpenOnGarbageTrailer_keepingTheStatement() {
        stubModelResponse("""
                # Garbage

                Body.

                ```json
                {"difficulty": not-valid-json,,,}
                ```
                """);

        var resp = hyperionProblemStatementGenerationService.generateProblemStatement(course(), "Prompt");

        // Unparseable trailer => no suggestions, and the statement is preserved (never rejected just because the rider was malformed).
        assertThat(resp.draftProblemStatement()).contains("# Garbage");
        assertThat(resp.suggestedDifficulty()).isNull();
        assertThat(resp.suggestedTitle()).isNull();
        assertThat(resp.suggestedCategories()).isNull();
    }

    @Test
    void generateProblemStatement_invalidDifficultyValue_yieldsNullDifficultyButKeepsOtherSuggestions() {
        stubModelResponse("""
                # X

                Body.

                ```json
                {"title": "X", "difficulty": "TRIVIAL", "categories": ["t"]}
                ```
                """);

        var resp = hyperionProblemStatementGenerationService.generateProblemStatement(course(), "Prompt");

        assertThat(resp.suggestedDifficulty()).isNull();
        assertThat(resp.suggestedTitle()).isEqualTo("X");
        assertThat(resp.suggestedCategories()).containsExactly("t");
    }
}
