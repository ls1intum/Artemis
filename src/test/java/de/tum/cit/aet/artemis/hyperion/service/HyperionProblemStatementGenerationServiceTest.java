package de.tum.cit.aet.artemis.hyperion.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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
import org.springframework.ai.chat.prompt.Prompt;

import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.domain.LLMServiceType;
import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;
import de.tum.cit.aet.artemis.core.exception.InternalServerErrorAlertException;
import de.tum.cit.aet.artemis.core.service.LLMTokenUsageService;
import de.tum.cit.aet.artemis.core.test_repository.UserTestRepository;
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
        ChatClient chatClient = ChatClient.create(chatModel);
        var templateService = new HyperionPromptTemplateService();
        this.hyperionProblemStatementGenerationService = new HyperionProblemStatementGenerationService(chatClient, templateService, llmTokenUsageService, userRepository);
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
        var serviceWithNullClient = new HyperionProblemStatementGenerationService(null, new HyperionPromptTemplateService(), llmTokenUsageService, userRepository);
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
}
