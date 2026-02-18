package de.tum.cit.aet.artemis.hyperion.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.util.List;

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
import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;
import de.tum.cit.aet.artemis.core.exception.InternalServerErrorAlertException;
import de.tum.cit.aet.artemis.hyperion.dto.ProblemStatementRefinementResponseDTO;

class HyperionProblemStatementRefinementServiceTest {

    @Mock
    private ChatModel chatModel;

    private HyperionProblemStatementRefinementService hyperionProblemStatementRefinementService;

    private AutoCloseable mocks;

    @BeforeEach
    void setup() {
        mocks = MockitoAnnotations.openMocks(this);
        ChatClient chatClient = ChatClient.create(chatModel);
        var templateService = new HyperionPromptTemplateService();
        this.hyperionProblemStatementRefinementService = new HyperionProblemStatementRefinementService(chatClient, templateService);
    }

    @AfterEach
    void tearDown() throws Exception {
        mocks.close();
    }

    @Test
    void refineProblemStatement_returnsRefinedStatement() throws Exception {
        String originalStatement = "Original problem statement";
        String refinedStatement = "Refined problem statement with improvements";
        when(chatModel.call(any(Prompt.class))).thenAnswer(invocation -> new ChatResponse(List.of(new Generation(new AssistantMessage(refinedStatement)))));

        var course = new Course();
        course.setTitle("Test Course");
        course.setDescription("Test Description");
        ProblemStatementRefinementResponseDTO resp = hyperionProblemStatementRefinementService.refineProblemStatement(course, originalStatement, "Make it better");
        assertThat(resp).isNotNull();
        assertThat(resp.refinedProblemStatement()).isEqualTo(refinedStatement);
    }

    @Test
    void refineProblemStatement_throwsExceptionOnAIFailure() throws Exception {
        String originalStatement = "Original problem statement";
        when(chatModel.call(any(Prompt.class))).thenThrow(new RuntimeException("AI service unavailable"));

        var course = new Course();
        course.setId(123L);
        course.setTitle("Test Course");
        course.setDescription("Test Description");

        assertThatThrownBy(() -> hyperionProblemStatementRefinementService.refineProblemStatement(course, originalStatement, "Refine this"))
                .isInstanceOf(InternalServerErrorAlertException.class).hasMessageContaining("Failed to refine problem statement");
    }

    @Test
    void refineProblemStatement_throwsExceptionOnExcessivelyLongResponse() throws Exception {
        String originalStatement = "Original problem statement";
        // Generate a string longer than MAX_PROBLEM_STATEMENT_LENGTH (50,000 characters)
        String excessivelyLongRefinement = "a".repeat(50_001);
        when(chatModel.call(any(Prompt.class))).thenAnswer(invocation -> new ChatResponse(List.of(new Generation(new AssistantMessage(excessivelyLongRefinement)))));

        var course = new Course();
        course.setId(456L);
        course.setTitle("Test Course");
        course.setDescription("Test Description");

        assertThatThrownBy(() -> hyperionProblemStatementRefinementService.refineProblemStatement(course, originalStatement, "Refine this"))
                .isInstanceOf(InternalServerErrorAlertException.class).hasMessageContaining("exceeds the maximum allowed length");
    }

    @Test
    void refineProblemStatement_throwsExceptionWhenUserPromptIsNull() {
        String originalStatement = "Original problem statement";
        var course = new Course();
        course.setTitle("Test Course");
        course.setDescription("Test Description");

        // Should throw exception when userPrompt is null
        assertThatThrownBy(() -> hyperionProblemStatementRefinementService.refineProblemStatement(course, originalStatement, null)).isInstanceOf(BadRequestAlertException.class)
                .hasMessageContaining("User prompt cannot be empty");
    }

    @Test
    void refineProblemStatement_handlesNullCourseFields() throws Exception {
        String originalStatement = "Original problem statement";
        String refinedStatement = "Refined with default course info";
        when(chatModel.call(any(Prompt.class))).thenAnswer(invocation -> new ChatResponse(List.of(new Generation(new AssistantMessage(refinedStatement)))));

        var course = new Course();
        // Leave title and description null

        // Should use default values when course fields are null
        ProblemStatementRefinementResponseDTO resp = hyperionProblemStatementRefinementService.refineProblemStatement(course, originalStatement, "Refine this");
        assertThat(resp).isNotNull();
        assertThat(resp.refinedProblemStatement()).isEqualTo(refinedStatement);
    }

    @Test
    void refineProblemStatement_acceptsMaximumLengthResponse() throws Exception {
        String originalStatement = "Original problem statement";
        // Generate a string exactly at MAX_PROBLEM_STATEMENT_LENGTH (50,000 characters)
        String maxLengthRefinement = "a".repeat(50_000);
        when(chatModel.call(any(Prompt.class))).thenAnswer(invocation -> new ChatResponse(List.of(new Generation(new AssistantMessage(maxLengthRefinement)))));

        var course = new Course();
        course.setTitle("Test Course");
        course.setDescription("Test Description");

        // Should succeed with exactly 50,000 characters
        ProblemStatementRefinementResponseDTO resp = hyperionProblemStatementRefinementService.refineProblemStatement(course, originalStatement, "Refine this");
        assertThat(resp).isNotNull();
        assertThat(resp.refinedProblemStatement()).hasSize(50_000);
    }

    @Test
    void refineProblemStatement_throwsExceptionWhenInputIsNull() {
        var course = new Course();
        course.setTitle("Test Course");
        course.setDescription("Test Description");

        // Should throw exception when input is null
        assertThatThrownBy(() -> hyperionProblemStatementRefinementService.refineProblemStatement(course, null, "Refine this")).isInstanceOf(BadRequestAlertException.class)
                .hasMessageContaining("Cannot refine empty problem statement");
    }

    @Test
    void refineProblemStatement_throwsExceptionWhenInputIsBlank() {
        var course = new Course();
        course.setTitle("Test Course");
        course.setDescription("Test Description");

        // Should throw exception when input is blank
        assertThatThrownBy(() -> hyperionProblemStatementRefinementService.refineProblemStatement(course, "   ", "Refine this")).isInstanceOf(BadRequestAlertException.class)
                .hasMessageContaining("Cannot refine empty problem statement");
    }

    @Test
    void refineProblemStatement_throwsExceptionWhenChatClientIsNull() {
        var serviceWithNullClient = new HyperionProblemStatementRefinementService(null, new HyperionPromptTemplateService());
        var course = new Course();
        course.setTitle("Test Course");
        course.setDescription("Test Description");

        assertThatThrownBy(() -> serviceWithNullClient.refineProblemStatement(course, "Original problem statement", "Refine this"))
                .isInstanceOf(InternalServerErrorAlertException.class).hasMessageContaining("AI chat client is not configured");
    }

    @Test
    void refineProblemStatement_throwsExceptionWhenResponseIsNull() throws Exception {
        String originalStatement = "Original problem statement";
        // AI returns null content
        when(chatModel.call(any(Prompt.class))).thenAnswer(invocation -> new ChatResponse(List.of(new Generation(new AssistantMessage(null)))));

        var course = new Course();
        course.setId(999L);
        course.setTitle("Test Course");
        course.setDescription("Test Description");

        assertThatThrownBy(() -> hyperionProblemStatementRefinementService.refineProblemStatement(course, originalStatement, "Refine this"))
                .isInstanceOf(InternalServerErrorAlertException.class).hasMessageContaining("Refined problem statement is null");
    }

    @Test
    void refineProblemStatement_throwsExceptionWhenRefinementUnchanged() throws Exception {
        String originalStatement = "Original problem statement";
        // AI returns the exact same content
        when(chatModel.call(any(Prompt.class))).thenAnswer(invocation -> new ChatResponse(List.of(new Generation(new AssistantMessage(originalStatement)))));

        var course = new Course();
        course.setId(789L);
        course.setTitle("Test Course");
        course.setDescription("Test Description");

        assertThatThrownBy(() -> hyperionProblemStatementRefinementService.refineProblemStatement(course, originalStatement, "Refine this"))
                .isInstanceOf(BadRequestAlertException.class).hasMessageContaining("same after refinement");
    }

    @Test
    void refineProblemStatement_throwsExceptionWhenUserPromptTooLong() {
        String originalStatement = "Original problem statement";
        // 1001 characters exceeds MAX_USER_PROMPT_LENGTH (1000)
        String tooLongPrompt = "a".repeat(1001);
        var course = new Course();
        course.setTitle("Test Course");
        course.setDescription("Test Description");

        assertThatThrownBy(() -> hyperionProblemStatementRefinementService.refineProblemStatement(course, originalStatement, tooLongPrompt))
                .isInstanceOf(BadRequestAlertException.class).hasMessageContaining("exceeds maximum length");
    }

    @Test
    void refineProblemStatement_throwsExceptionWhenProblemStatementTooLong() {
        // 50001 characters exceeds MAX_PROBLEM_STATEMENT_LENGTH (50000)
        String tooLongProblemStatement = "a".repeat(50_001);
        var course = new Course();
        course.setTitle("Test Course");
        course.setDescription("Test Description");

        assertThatThrownBy(() -> hyperionProblemStatementRefinementService.refineProblemStatement(course, tooLongProblemStatement, "Refine this"))
                .isInstanceOf(BadRequestAlertException.class).hasMessageContaining("exceeds maximum length");
    }
}
