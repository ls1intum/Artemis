package de.tum.cit.aet.artemis.hyperion.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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

import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.exception.InternalServerErrorAlertException;
import de.tum.cit.aet.artemis.hyperion.dto.ProblemStatementRefinementResponseDTO;

class HyperionProblemStatementRefinementServiceTest {

    @Mock
    private ChatModel chatModel;

    private HyperionProblemStatementRefinementService hyperionProblemStatementRefinementService;

    @BeforeEach
    void setup() {
        MockitoAnnotations.openMocks(this);
        ChatClient chatClient = ChatClient.create(chatModel);
        var templateService = new HyperionPromptTemplateService();
        this.hyperionProblemStatementRefinementService = new HyperionProblemStatementRefinementService(chatClient, templateService);
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
        assertThat(resp.originalProblemStatement()).isNull();
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
                .isInstanceOf(InternalServerErrorAlertException.class).hasMessageContaining("Failed to refine problem statement").hasMessageContaining("AI service unavailable");
    }

    @Test
    void refineProblemStatement_throwsExceptionOnExcessivelyLongResponse() throws Exception {
        String originalStatement = "Original problem statement";
        // Generate a string longer than MAX_PROBLEM_STATEMENT_LENGTH (50,000
        // characters)
        String excessivelyLongRefinement = "a".repeat(50_001);
        when(chatModel.call(any(Prompt.class))).thenAnswer(invocation -> new ChatResponse(List.of(new Generation(new AssistantMessage(excessivelyLongRefinement)))));

        var course = new Course();
        course.setId(456L);
        course.setTitle("Test Course");
        course.setDescription("Test Description");

        assertThatThrownBy(() -> hyperionProblemStatementRefinementService.refineProblemStatement(course, originalStatement, "Refine this"))
                .isInstanceOf(InternalServerErrorAlertException.class).hasMessageContaining("too long").hasMessageContaining("50001 characters")
                .hasMessageContaining("Maximum allowed: 50000");
    }

    @Test
    void refineProblemStatement_handlesNullUserPrompt() throws Exception {
        String originalStatement = "Original problem statement";
        String refinedStatement = "Refined with default prompt";
        when(chatModel.call(any(Prompt.class))).thenAnswer(invocation -> new ChatResponse(List.of(new Generation(new AssistantMessage(refinedStatement)))));

        var course = new Course();
        course.setTitle("Test Course");
        course.setDescription("Test Description");

        // Should use default refinement prompt when userPrompt is null
        ProblemStatementRefinementResponseDTO resp = hyperionProblemStatementRefinementService.refineProblemStatement(course, originalStatement, null);
        assertThat(resp).isNotNull();
        assertThat(resp.refinedProblemStatement()).isEqualTo(refinedStatement);
        assertThat(resp.originalProblemStatement()).isNull();
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
        assertThat(resp.originalProblemStatement()).isNull();
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
        assertThat(resp.originalProblemStatement()).isNull();
    }

    @Test
    void refineProblemStatement_returnsOriginalWhenInputIsNull() throws Exception {
        var course = new Course();
        course.setTitle("Test Course");
        course.setDescription("Test Description");

        // Should return empty refinement and empty original when input is null
        ProblemStatementRefinementResponseDTO resp = hyperionProblemStatementRefinementService.refineProblemStatement(course, null, "Refine this");
        assertThat(resp).isNotNull();
        assertThat(resp.refinedProblemStatement()).isEmpty();
        assertThat(resp.originalProblemStatement()).isEmpty();
    }

    @Test
    void refineProblemStatement_returnsOriginalWhenInputIsBlank() throws Exception {
        var course = new Course();
        course.setTitle("Test Course");
        course.setDescription("Test Description");

        // Should return empty refinement and original when input is blank
        ProblemStatementRefinementResponseDTO resp = hyperionProblemStatementRefinementService.refineProblemStatement(course, "   ", "Refine this");
        assertThat(resp).isNotNull();
        assertThat(resp.refinedProblemStatement()).isEmpty();
        assertThat(resp.originalProblemStatement()).isEqualTo("   ");
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
                .isInstanceOf(InternalServerErrorAlertException.class).hasMessageContaining("same after refinement");
    }
}
