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
import de.tum.cit.aet.artemis.hyperion.dto.ProblemStatementGenerationResponseDTO;

class HyperionProblemStatementGenerationServiceTest {

    @Mock
    private ChatModel chatModel;

    private HyperionProblemStatementGenerationService hyperionProblemStatementGenerationService;

    @BeforeEach
    void setup() {
        MockitoAnnotations.openMocks(this);
        ChatClient chatClient = ChatClient.create(chatModel);
        var templateService = new HyperionPromptTemplateService();
        this.hyperionProblemStatementGenerationService = new HyperionProblemStatementGenerationService(chatClient, templateService);
    }

    @Test
    void generateProblemStatement_returnsGeneratedDraft() throws Exception {
        String generatedDraft = "Generated draft problem statement";
        when(chatModel.call(any(Prompt.class))).thenAnswer(invocation -> new ChatResponse(List.of(new Generation(new AssistantMessage(generatedDraft)))));

        var course = new Course();
        course.setTitle("Test Course");
        course.setDescription("Test Description");
        ProblemStatementGenerationResponseDTO resp = hyperionProblemStatementGenerationService.generateProblemStatement(course, "Prompt");
        assertThat(resp).isNotNull();
        assertThat(resp.draftProblemStatement()).isEqualTo(generatedDraft);
        assertThat(resp.error()).isNull();
    }

    @Test
    void generateProblemStatement_throwsExceptionOnAIFailure() throws Exception {
        when(chatModel.call(any(Prompt.class))).thenThrow(new RuntimeException("AI service unavailable"));

        var course = new Course();
        course.setId(123L);
        course.setTitle("Test Course");
        course.setDescription("Test Description");

        assertThatThrownBy(() -> hyperionProblemStatementGenerationService.generateProblemStatement(course, "Prompt")).isInstanceOf(InternalServerErrorAlertException.class)
                .hasMessageContaining("Failed to generate problem statement").hasMessageContaining("AI service unavailable");
    }

    @Test
    void generateProblemStatement_throwsExceptionOnExcessivelyLongResponse() throws Exception {
        // Generate a string longer than MAX_PROBLEM_STATEMENT_LENGTH (50,000 characters)
        String excessivelyLongDraft = "a".repeat(50_001);
        when(chatModel.call(any(Prompt.class))).thenAnswer(invocation -> new ChatResponse(List.of(new Generation(new AssistantMessage(excessivelyLongDraft)))));

        var course = new Course();
        course.setId(456L);
        course.setTitle("Test Course");
        course.setDescription("Test Description");

        assertThatThrownBy(() -> hyperionProblemStatementGenerationService.generateProblemStatement(course, "Prompt")).isInstanceOf(InternalServerErrorAlertException.class)
                .hasMessageContaining("too long").hasMessageContaining("50001 characters").hasMessageContaining("Maximum allowed: 50000");
    }

    @Test
    void generateProblemStatement_handlesNullUserPrompt() throws Exception {
        String generatedDraft = "Generated draft with default prompt";
        when(chatModel.call(any(Prompt.class))).thenAnswer(invocation -> new ChatResponse(List.of(new Generation(new AssistantMessage(generatedDraft)))));

        var course = new Course();
        course.setTitle("Test Course");
        course.setDescription("Test Description");

        // Should use default prompt when userPrompt is null
        ProblemStatementGenerationResponseDTO resp = hyperionProblemStatementGenerationService.generateProblemStatement(course, null);
        assertThat(resp).isNotNull();
        assertThat(resp.draftProblemStatement()).isEqualTo(generatedDraft);
        assertThat(resp.error()).isNull();
    }

    @Test
    void generateProblemStatement_handlesNullCourseFields() throws Exception {
        String generatedDraft = "Generated draft with default course info";
        when(chatModel.call(any(Prompt.class))).thenAnswer(invocation -> new ChatResponse(List.of(new Generation(new AssistantMessage(generatedDraft)))));

        var course = new Course();
        // Leave title and description null

        // Should use default values when course fields are null
        ProblemStatementGenerationResponseDTO resp = hyperionProblemStatementGenerationService.generateProblemStatement(course, "Prompt");
        assertThat(resp).isNotNull();
        assertThat(resp.draftProblemStatement()).isEqualTo(generatedDraft);
        assertThat(resp.error()).isNull();
    }

    @Test
    void generateProblemStatement_acceptsMaximumLengthResponse() throws Exception {
        // Generate a string exactly at MAX_PROBLEM_STATEMENT_LENGTH (50,000 characters)
        String maxLengthDraft = "a".repeat(50_000);
        when(chatModel.call(any(Prompt.class))).thenAnswer(invocation -> new ChatResponse(List.of(new Generation(new AssistantMessage(maxLengthDraft)))));

        var course = new Course();
        course.setTitle("Test Course");
        course.setDescription("Test Description");

        // Should succeed with exactly 50,000 characters
        ProblemStatementGenerationResponseDTO resp = hyperionProblemStatementGenerationService.generateProblemStatement(course, "Prompt");
        assertThat(resp).isNotNull();
        assertThat(resp.draftProblemStatement()).hasSize(50_000);
        assertThat(resp.error()).isNull();
    }
}
